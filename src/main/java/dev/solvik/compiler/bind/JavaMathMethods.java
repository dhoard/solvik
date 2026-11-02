package dev.solvik.compiler.bind;

import dev.solvik.compiler.types.SolvikType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Compile-time exposure of the Java API of the built-in {@code BigInteger} and
 * {@code BigDecimal} values.
 *
 * <p>Those values are stored as the Java classes themselves, so a call needs no adapter: the
 * compiler selects a public instance method and emits a direct Java call. Only signatures whose
 * Java types map to Solvik types are exposed; unsupported shapes are reported rather than guessed.
 */
public final class JavaMathMethods {
    /** Two public overloads matched equally well. */
    public static final String AMBIGUOUS = "SOL-T014";

    private JavaMathMethods() {
    }

    /** The Java class behind a built-in numeric type, or {@code null} for every other type. */
    public static Class<?> javaClassOf(SolvikType type) {
        if (type == SolvikType.BIG_INTEGER) {
            return BigInteger.class;
        }
        if (type == SolvikType.BIG_DECIMAL) {
            return BigDecimal.class;
        }
        return null;
    }

    /** The Solvik type of a Java type, or {@code null} when it has no Solvik representation. */
    public static SolvikType map(Type type) {
        return type instanceof Class<?> clazz ? JavaTypes.of(clazz) : null;
    }

    /** The outcome of resolving one call: a method, or why none is usable. */
    public record Lookup(Method method, String problem) {
        public boolean found() {
            return method != null;
        }
    }

    /**
     * Selects the public instance method {@code name} that accepts exactly {@code arguments}.
     *
     * <p>Resolution is type-directed: every parameter has one Solvik type and every argument has
     * one, and the two must be equal. A lone match wins; several matches are reported as ambiguous
     * rather than silently picking the first.
     */
    public static Lookup resolve(SolvikType receiver, String name, List<SolvikType> arguments) {
        Class<?> type = javaClassOf(receiver);
        if (type == null) {
            return new Lookup(null, "not a Java-backed number");
        }
        List<Method> matches = new ArrayList<>();
        String unsupported = null;
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getDeclaringClass() == Object.class
                    || Modifier.isStatic(method.getModifiers()) || method.isBridge()
                    || method.isSynthetic() || method.isVarArgs()
                    || method.getTypeParameters().length != 0) {
                continue;
            }
            SolvikType result = map(method.getReturnType());
            if (result == null) {
                unsupported = method.getReturnType().getTypeName();
                continue;
            }
            if (method.getParameterCount() != arguments.size()) {
                continue;
            }
            boolean fits = true;
            for (int i = 0; i < method.getParameterCount(); i++) {
                SolvikType parameter = map(method.getParameterTypes()[i]);
                if (parameter == null || !parameter.equals(arguments.get(i))) {
                    fits = false;
                    break;
                }
            }
            if (fits) {
                matches.add(method);
            }
        }
        if (matches.size() == 1) {
            return new Lookup(matches.getFirst(), null);
        }
        if (matches.size() > 1) {
            return new Lookup(null, AMBIGUOUS + ": '" + name + "' matches "
                    + matches.size() + " public overloads of " + type.getSimpleName()
                    + " with these argument types");
        }
        if (unsupported != null) {
            return new Lookup(null, "the Java return type '" + unsupported
                    + "' of '" + name + "' has no Solvik representation");
        }
        return new Lookup(null, null);
    }
}
