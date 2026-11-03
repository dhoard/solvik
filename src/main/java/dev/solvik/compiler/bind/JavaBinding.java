package dev.solvik.compiler.bind;

import dev.solvik.compiler.ast.Ast;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.types.SolvikType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Compiler-only ABI metadata. No application method is invoked while resolving a binding. */
public record JavaBinding(Class<?> javaClass, Map<Ast.FuncDecl, Executable> members) {
    public static final String INVALID_BINDING = "SOL-T013";

    public static JavaBinding resolve(Ast.TypeDecl declaration,
            Function<Ast.TypeRef, SolvikType> resolve,
            Function<Class<?>, SolvikType> resolveJavaClass,
            ClassLoader loader, Diagnostics diagnostics) {
        try {
            Class<?> target = Class.forName(declaration.nativeClass(), false, loader);
            if (!Modifier.isPublic(target.getModifiers()) || target.isArray()
                    || target.getCanonicalName() == null
                    || declaration.isTrait() != target.isInterface()) {
                throw new IllegalArgumentException(declaration.isTrait()
                        ? "native trait target must be a public Java interface"
                        : "native type target must be a public Java class");
            }
            if (!declaration.fields().isEmpty()) {
                throw new IllegalArgumentException("native types cannot declare fields");
            }
            TypeVariable<?>[] parameters = target.getTypeParameters();
            if (!declaration.typeParameters().isEmpty()
                    && parameters.length != declaration.typeParameters().size()) {
                throw new IllegalArgumentException("native class type parameter count mismatch");
            }
            Map<TypeVariable<?>, SolvikType> substitutions = new HashMap<>();
            for (int i = 0; i < parameters.length; i++) {
                if (!Arrays.equals(parameters[i].getBounds(), new Type[]{Object.class})) {
                    throw new IllegalArgumentException(
                            "bounded Java type parameters require an explicit supported binding");
                }
                substitutions.put(parameters[i], declaration.typeParameters().isEmpty() ? SolvikType.ANY
                        : SolvikType.variable(declaration.typeParameters().get(i)));
            }
            collectParents(target, substitutions);
            Map<Ast.FuncDecl, Executable> members = new IdentityHashMap<>();
            for (Ast.FuncDecl exported : declaration.methods()) {
                boolean constructor = exported.isConstructor(declaration.name());
                if (constructor && exported.isStatic()) {
                    diagnostics.error(INVALID_BINDING, exported.pos(),
                            "a native constructor cannot be 'static'");
                    continue;
                }
                List<Executable> candidates = new ArrayList<>();
                if (constructor) {
                    if (declaration.isTrait()) {
                        throw new IllegalArgumentException("a native trait cannot declare a constructor");
                    }
                    if (Modifier.isAbstract(target.getModifiers())) {
                        throw new IllegalArgumentException("cannot construct an abstract native class");
                    }
                    candidates.addAll(Arrays.asList(target.getConstructors()));
                } else {
                    String name = exported.nativeName() == null ? exported.name() : exported.nativeName();
                    for (Method method : target.getMethods()) {
                        if (method.getName().equals(name) && !method.isBridge() && !method.isSynthetic())
                            candidates.add(method);
                    }
                }
                if (exported.hasBody()) {
                    List<Executable> inherited = candidates.stream()
                            .filter(candidate -> matchesParameters(candidate, exported, resolve,
                                    resolveJavaClass, substitutions))
                            .toList();
                    if (!inherited.isEmpty() && inherited.stream().noneMatch(candidate ->
                            matches(candidate, exported, resolve, resolveJavaClass, substitutions))) {
                        diagnostics.error(INVALID_BINDING, exported.pos(), "default function '"
                                + exported.name() + "' is incompatible with the inherited Java signature in "
                                + target.getName());
                    }
                    continue; // source defaults are emitted, never invoked reflectively
                }
                List<Executable> matching = candidates.stream()
                        .filter(candidate -> matches(candidate, exported, resolve,
                                resolveJavaClass, substitutions))
                        .toList();
                if (matching.size() != 1) {
                    diagnostics.error(INVALID_BINDING, exported.pos(), "native member '" + exported.name()
                            + "' has " + matching.size() + " matching public Java signatures in " + target.getName()
                            + "; check its parameters, return type, and static/instance declaration,"
                            + " or rename the selected overload");
                } else {
                    members.put(exported, matching.getFirst());
                }
            }
            return new JavaBinding(target, members);
        } catch (ClassNotFoundException | LinkageError | IllegalArgumentException | SecurityException failure) {
            diagnostics.error(INVALID_BINDING, declaration.pos(), "cannot bind '" + declaration.nativeClass()
                    + "': " + failure.getMessage());
            return null;
        }
    }

    private static boolean matches(Executable executable, Ast.FuncDecl exported,
            Function<Ast.TypeRef, SolvikType> resolve,
            Function<Class<?>, SolvikType> resolveJavaClass,
            Map<TypeVariable<?>, SolvikType> mapping) {
        if (!matchesParameters(executable, exported, resolve, resolveJavaClass, mapping)) {
            return false;
        }
        if (executable instanceof Constructor<?>) {
            return exported.returnType() == null;
        }
        if (exported.returnType() == null) {
            return true; // explicitly discard a Java return value
        }
        SolvikType returned = mapped(((Method) executable).getGenericReturnType(), mapping,
                resolveJavaClass);
        // Nullability is a Solvik contract, not a Java representation: `String?` and `String` share
        // one Java carrier, so a declaration may add `?` to a Java return that can be null (§11.1).
        return returned != null
                && returned.asNonNull().equals(resolve.apply(exported.returnType()).asNonNull());
    }

    private static boolean matchesParameters(Executable executable, Ast.FuncDecl exported,
            Function<Ast.TypeRef, SolvikType> resolve,
            Function<Class<?>, SolvikType> resolveJavaClass,
            Map<TypeVariable<?>, SolvikType> mapping) {
        if (executable.isVarArgs() || executable.getTypeParameters().length != 0
                || executable.getParameterCount() != exported.params().size()) return false;
        if (executable instanceof Constructor<?>) {
            if (exported.isStatic()) return false;
        } else if (exported.isStatic()
                != Modifier.isStatic(executable.getModifiers())) {
            // The declaration states whether the member belongs to the type or to an instance.
            return false;
        }
        Type[] parameters = executable.getGenericParameterTypes();
        for (int i = 0; i < parameters.length; i++) {
            SolvikType javaType = mapped(parameters[i], mapping, resolveJavaClass);
            SolvikType declared = resolve.apply(exported.params().get(i).type());
            if (javaType == null || !javaType.asNonNull().equals(declared.asNonNull())) {
                return false;
            }
            // A Java primitive parameter cannot receive Null, so its declaration stays non-null.
            if (declared.isNullable() && parameters[i] instanceof Class<?> clazz
                    && clazz.isPrimitive()) {
                return false;
            }
        }
        return true;
    }

    private static SolvikType mapped(Type type, Map<TypeVariable<?>, SolvikType> mapping,
            Function<Class<?>, SolvikType> resolveJavaClass) {
        if (type instanceof TypeVariable<?> variable) {
            return mapping.get(variable);
        }
        if (type instanceof ParameterizedType generic) {
            return mappedParameterized(generic, mapping, resolveJavaClass);
        }
        if (!(type instanceof Class<?> clazz)) return null;
        SolvikType builtin = JavaTypes.of(clazz);
        return builtin == null ? resolveJavaClass.apply(clazz) : builtin;
    }

    /**
     * A parameterized Java type named by a declared binding, with its arguments mapped.
     *
     * <p>Only a class or interface a packaged declaration already names has a Solvik
     * representation: {@code java.util.Iterator<E>} maps to the declaring native trait with each
     * argument mapped by the same rule, so {@code E} substituted with {@code Integer} yields
     * {@code solvik::collection::Iterator<Integer>}. A raw class with no declared name, a wildcard,
     * or an argument with no representation maps to {@code null}, which the caller reports as an
     * unmatched signature rather than a guess.
     */
    private static SolvikType mappedParameterized(ParameterizedType generic,
            Map<TypeVariable<?>, SolvikType> mapping,
            Function<Class<?>, SolvikType> resolveJavaClass) {
        if (!(generic.getRawType() instanceof Class<?> raw)) {
            return null;
        }
        SolvikType base = mapped(raw, mapping, resolveJavaClass);
        if (base == null || base.kind() != SolvikType.Kind.USER) {
            return null;
        }
        List<SolvikType> arguments = new ArrayList<>();
        for (Type argument : generic.getActualTypeArguments()) {
            SolvikType mappedArgument = mapped(argument, mapping, resolveJavaClass);
            if (mappedArgument == null) {
                return null;
            }
            arguments.add(mappedArgument);
        }
        return SolvikType.user(base.userName(), arguments);
    }

    /** Substitute inherited Java class variables, not merely variables with coincident names. */
    private static void collectParents(Class<?> owner, Map<TypeVariable<?>, SolvikType> mapping) {
        List<Type> parents = new ArrayList<>(Arrays.asList(owner.getGenericInterfaces()));
        if (owner.getGenericSuperclass() != null) {
            parents.add(owner.getGenericSuperclass());
        }
        for (Type parent : parents) {
            Class<?> raw;
            if (parent instanceof ParameterizedType generic) {
                raw = (Class<?>) generic.getRawType();
                Type[] actuals = generic.getActualTypeArguments();
                TypeVariable<?>[] names = raw.getTypeParameters();
                for (int i = 0; i < names.length; i++) {
                    SolvikType actual = mapped(actuals[i], mapping, ignored -> null);
                    if (actual != null) {
                        mapping.put(names[i], actual);
                    }
                }
            } else if (parent instanceof Class<?> clazz) {
                raw = clazz;
            } else {
                continue;
            }
            collectParents(raw, mapping);
        }
    }
}
