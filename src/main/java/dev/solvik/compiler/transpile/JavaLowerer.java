package dev.solvik.compiler.transpile;

import dev.solvik.compiler.ast.Ast;
import dev.solvik.compiler.bind.BuiltinBindings;
import dev.solvik.compiler.bind.JavaBinding;
import dev.solvik.compiler.check.TypeChecker;
import dev.solvik.compiler.jir.JDecl;
import dev.solvik.compiler.jir.JExpr;
import dev.solvik.compiler.jir.JStmt;
import dev.solvik.compiler.jir.JType;
import dev.solvik.compiler.jir.JTypes;
import dev.solvik.compiler.types.SolvikType;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lowers a checked Solvik program into typed Java IR.
 *
 * <p>The checker remains authoritative for Solvik semantics: this class consumes its recorded
 * types, bindings, resolved overloads, and selected targets, and it never re-runs overload
 * resolution or mutability rules. It maps those decisions to Java representations, preserves
 * evaluation order, and names the generated support members the program reaches. It never builds
 * Java source text; {@link dev.solvik.compiler.jir.JavaSourceRenderer} owns that.
 */
public final class JavaLowerer {

    /** The factory and nested type that turn a Java interface into a Solvik native-trait value. */
    private static final String TRAIT_FACTORY = "$of";
    private static final String TRAIT_ADAPTER = "$Adapter";
    private static final String TRAIT_DELEGATE = "delegate";

    private final TypeChecker.Result types;
    private final Set<String> typeNames;
    /** Generated support members reached by every compilation unit lowered so far. */
    private final Set<String> intrinsicUses = new LinkedHashSet<>();
    /** Whether a bundled native binding was lowered, so the adapter file must be emitted. */
    private boolean usesIo;

    public JavaLowerer(TypeChecker.Result types, Set<String> typeNames) {
        this.types = types;
        this.typeNames = Set.copyOf(typeNames);
    }

    /** Every support member the lowered program reaches, in first-use order. */
    public Set<String> usedIntrinsics() {
        return Set.copyOf(intrinsicUses);
    }

    /** Whether the program reaches a bundled native binding and needs the generated adapter. */
    public boolean usesIo() {
        return usesIo;
    }

    /** Lowers the program class that holds top-level statements, functions, and support code. */
    public JDecl.CompilationUnit lowerProgram(Ast.Program program) {
        Unit unit = new Unit(null, null, List.of(), true);
        List<JDecl.Member> members = new ArrayList<>();
        members.add(new JDecl.Constructor(Set.of(JDecl.Modifier.PRIVATE), List.of(), List.of(),
                List.of()));
        members.add(new JDecl.Method(Set.of(JDecl.Modifier.PUBLIC, JDecl.Modifier.STATIC),
                List.of(), JTypes.VOID, "main",
                List.of(new JDecl.Parameter(new JType.Array(JTypes.STRING), "args")),
                unit.statements(program.statements()), List.of()));
        for (Ast.FuncDecl function : program.functions()) {
            members.add(unit.function(function,
                    Set.of(JDecl.Modifier.PUBLIC, JDecl.Modifier.STATIC)));
        }
        unit.addPatternFields(members, false);
        JDecl.ClassDecl type = new JDecl.ClassDecl(JDecl.Kind.CLASS,
                Set.of(JDecl.Modifier.PUBLIC, JDecl.Modifier.FINAL), GeneratedNames.PROGRAM_CLASS,
                List.of(), null, List.of(), members, List.of());
        return new JDecl.CompilationUnit(GeneratedNames.GLOBAL_PACKAGE, List.of(type));
    }

    /** Lowers one module's functions into its generated static holder class. */
    public JDecl.CompilationUnit lowerModule(String module, List<Ast.FuncDecl> functions) {
        Unit unit = new Unit(module, null, List.of(), false);
        List<JDecl.Member> members = new ArrayList<>();
        members.add(new JDecl.Constructor(Set.of(JDecl.Modifier.PRIVATE), List.of(), List.of(),
                List.of()));
        for (Ast.FuncDecl function : functions) {
            members.add(unit.function(function,
                    Set.of(JDecl.Modifier.PUBLIC, JDecl.Modifier.STATIC)));
        }
        unit.addPatternFields(members, false);
        JDecl.ClassDecl type = new JDecl.ClassDecl(JDecl.Kind.CLASS,
                Set.of(JDecl.Modifier.PUBLIC, JDecl.Modifier.FINAL), GeneratedNames.FUNCTIONS_CLASS,
                List.of(), null, List.of(), members, List.of());
        return new JDecl.CompilationUnit(GeneratedNames.javaPackage(module), List.of(type));
    }

    /** Lowers one declared type, choosing the class, trait, or native adapter representation. */
    public JDecl.CompilationUnit lowerType(Ast.TypeDecl declaration, String qualifiedName) {
        if (declaration.nativeClass() != null) {
            return nativeUnit(declaration, qualifiedName);
        }
        if (declaration.isTrait()) {
            return traitUnit(declaration, qualifiedName);
        }
        return classUnit(declaration, qualifiedName);
    }

    // ------------------------------------------------------------------ declarations

    private JDecl.CompilationUnit classUnit(Ast.TypeDecl declaration, String qualifiedName) {
        Unit unit = new Unit(moduleOf(qualifiedName), qualifiedName, declaration.typeParameters(),
                false);
        List<JDecl.Member> members = new ArrayList<>();
        Set<JDecl.Modifier> classModifiers = Set.of(JDecl.Modifier.PUBLIC, JDecl.Modifier.FINAL);
        List<JDecl.Member> staticInitializerMethods = new ArrayList<>();
        Map<Integer, List<Ast.StaticInitializer>> initializersByField = new LinkedHashMap<>();
        for (Ast.StaticInitializer initializer : declaration.staticInitializers()) {
            initializersByField.computeIfAbsent(initializer.fieldIndex(),
                    ignored -> new ArrayList<>()).add(initializer);
        }
        // Static fields and static blocks share one <clinit>, so keep their source order. Instance
        // fields may sit between them syntactically; they do not participate in class init.
        for (int index = 0; index <= declaration.fields().size(); index++) {
            for (Ast.StaticInitializer initializer
                    : initializersByField.getOrDefault(index, List.of())) {
                // A static block body may always throw, which javac rejects for an initializer
                // that cannot complete normally. A private static method may throw freely, and
                // calling it at this source position preserves initialization order.
                String methodName = "$solvikStaticInit" + staticInitializerMethods.size();
                members.add(unit.staticInitializerCall(methodName));
                staticInitializerMethods.add(
                        unit.staticInitializerMethod(initializer, methodName));
            }
            if (index == declaration.fields().size()) {
                continue;
            }
            Ast.FieldDecl field = declaration.fields().get(index);
            Set<JDecl.Modifier> modifiers = new LinkedHashSet<>();
            modifiers.add(field.isPublic() ? JDecl.Modifier.PUBLIC : JDecl.Modifier.PRIVATE);
            if (field.isStatic()) {
                modifiers.add(JDecl.Modifier.STATIC);
            }
            JExpr initializer = field.initializer() == null ? null : unit.expr(field.initializer());
            members.add(new JDecl.Field(modifiers, JTypes.of(types.typeOf(field)),
                    GeneratedNames.javaIdentifier(field.name()), initializer, List.of()));
        }
        if (!declaration.typeParameters().isEmpty()) {
            members.add(JDecl.Field.of(
                    Set.of(JDecl.Modifier.PRIVATE, JDecl.Modifier.FINAL), JTypes.STRING,
                    "$solvikType"));
        }
        // Only an instance native member needs the opaque payload: a native constructor computes
        // it, and a native instance method passes it. A type whose native members are all static
        // (File) keeps an ordinary Solvik constructor and carries no payload.
        boolean hasPayload = declaration.methods().stream()
                .anyMatch(method -> method.isNative() && !method.isStatic());
        if (hasPayload) {
            members.add(JDecl.Field.of(Set.of(JDecl.Modifier.PRIVATE, JDecl.Modifier.FINAL),
                    JTypes.OBJECT, "$payload"));
            members.add(new JDecl.Constructor(Set.of(JDecl.Modifier.PRIVATE),
                    List.of(new JDecl.Parameter(JTypes.OBJECT, "$payload")),
                    List.of(new JStmt.ExpressionStatement(assign(
                            field("this", "$payload", JTypes.OBJECT),
                            name("$payload", JTypes.OBJECT), JTypes.OBJECT))), List.of()));
            JType.Declared self = unit.classType();
            members.add(new JDecl.Method(
                    Set.of(JDecl.Modifier.PUBLIC, JDecl.Modifier.STATIC), List.of(), self,
                    "$of", List.of(new JDecl.Parameter(JTypes.OBJECT, "$payload")),
                    List.of(new JStmt.Return(new JExpr.New(self,
                            List.of(name("$payload", JTypes.OBJECT)), self))), List.of()));
            members.add(new JDecl.Method(Set.of(JDecl.Modifier.PUBLIC), List.of(),
                    JTypes.OBJECT, "$payload", List.of(),
                    List.of(new JStmt.Return(field("this", "$payload", JTypes.OBJECT))), List.of()));
        }
        List<Ast.FuncDecl> constructors = declaration.methods().stream()
                .filter(method -> method.isConstructor(declaration.name())).toList();
        for (Ast.FuncDecl constructor : constructors) {
            members.add(unit.constructor(constructor, constructor.isPublic(),
                    !declaration.typeParameters().isEmpty()));
        }
        for (Ast.FuncDecl method : declaration.methods()) {
            if (constructors.contains(method)) {
                continue;
            }
            Set<JDecl.Modifier> modifiers = new LinkedHashSet<>();
            modifiers.add(method.isPublic() ? JDecl.Modifier.PUBLIC : JDecl.Modifier.PRIVATE);
            if (method.isStatic()) {
                modifiers.add(JDecl.Modifier.STATIC);
            }
            members.add(unit.function(method, modifiers));
        }
        members.addAll(staticInitializerMethods);
        for (TypeChecker.DefaultBridge bridge : types.defaultBridgesFor(qualifiedName)) {
            members.add(unit.defaultBridge(bridge));
        }
        unit.addPatternFields(members, false);
        members.add(unit.typeNameMethod(!declaration.typeParameters().isEmpty(),
                declaration.name()));
        List<JDecl.Annotation> annotations = new ArrayList<>();
        if (suppressesEqualsHashWarning(declaration)) {
            annotations.add(suppression("overrides"));
        }
        List<JType> interfaces = declaration.implementedTraits().stream()
                .map(unit::resolve).map(JTypes::of).toList();
        JDecl.ClassDecl type = new JDecl.ClassDecl(JDecl.Kind.CLASS, classModifiers,
                GeneratedNames.javaIdentifier(declaration.name()), declaration.typeParameters(),
                null, interfaces, members, annotations);
        return new JDecl.CompilationUnit(packageOf(qualifiedName), List.of(type));
    }

    private JDecl.CompilationUnit traitUnit(Ast.TypeDecl declaration, String qualifiedName) {
        Unit unit = new Unit(moduleOf(qualifiedName), qualifiedName, List.of(), false);
        return new JDecl.CompilationUnit(packageOf(qualifiedName),
                List.of(trait(unit, declaration, null, null, false)));
    }

    /**
     * A trait interface, with its Java interface as parent when it can be inherited.
     *
     * <p>A native trait's type parameters name the Java interface's own parameters, so the emitted
     * interface declares them. A plain Solvik trait cannot declare any (the checker rejects them),
     * so this adds nothing for it.
     *
     * @param javaInterface        the Java interface a native trait exposes, or {@code null} for a
     *                             Solvik-bodied trait
     * @param adapter              the binding of a generic native trait, which is represented by its
     *                             own Solvik interface and converts with {@code $of}; or {@code null}
     * @param inheritJavaInterface whether the emitted interface extends {@code javaInterface}
     */
    private JDecl.ClassDecl trait(Unit unit, Ast.TypeDecl declaration, JType.Declared javaInterface,
            JavaBinding adapter, boolean inheritJavaInterface) {
        List<JDecl.Member> members = new ArrayList<>();
        for (Ast.FuncDecl method : declaration.methods()) {
            if (method.hasBody()) {
                members.addAll(unit.traitDefault(method));
            } else {
                members.add(unit.abstractMethod(method));
            }
        }
        unit.addPatternFields(members, true);
        if (adapter != null) {
            members.addAll(unit.traitAdapter(declaration, adapter, javaInterface));
        }
        List<JType> interfaces = inheritJavaInterface ? List.of(javaInterface) : List.of();
        return new JDecl.ClassDecl(JDecl.Kind.INTERFACE, Set.of(JDecl.Modifier.PUBLIC),
                GeneratedNames.javaIdentifier(declaration.name()), declaration.typeParameters(),
                null, interfaces, members, List.of());
    }

    /**
     * Whether a native trait is represented by a Solvik-owned interface instead of its Java one.
     *
     * <p>A type parameter is what decides it: the trait's members are then emitted with Solvik
     * carriers inside that interface, and a Solvik {@code Boolean} cannot override Java's primitive
     * {@code boolean}. So the Java interface cannot be the supertype, and the generated
     * {@code $of} adapter is the only Java-to-Solvik conversion. A non-generic native trait keeps
     * extending its Java interface, which is what lets a {@code solvik::concurrency::Task} be passed
     * to a Java API expecting {@code Runnable}.
     */
    private static boolean adaptsNativeTrait(Ast.TypeDecl declaration) {
        return declaration.isTrait() && declaration.nativeClass() != null
                && !declaration.typeParameters().isEmpty();
    }

    /** Java warns on an intentional equals-only Solvik override unless the class owns the policy. */
    static boolean suppressesEqualsHashWarning(Ast.TypeDecl type) {
        boolean equals = type.methods().stream().anyMatch(method -> !method.isStatic()
                && "equals".equals(method.name()) && method.params().size() == 1);
        boolean hashCode = type.methods().stream().anyMatch(method -> !method.isStatic()
                && "hashCode".equals(method.name()) && method.params().isEmpty());
        return equals && !hashCode;
    }

    private static JDecl.Annotation suppression(String... values) {
        List<JExpr> arguments = new ArrayList<>();
        for (String value : values) {
            arguments.add(new JExpr.StringLiteral(value));
        }
        return new JDecl.Annotation(JType.Declared.of("java.lang", "SuppressWarnings"), arguments);
    }

    // ----------------------------------------------------------------- native adapters

    private JDecl.CompilationUnit nativeUnit(Ast.TypeDecl declaration, String qualifiedName) {
        JavaBinding binding = types.nativeBindings().get(declaration);
        if (binding == null) {
            throw new IllegalStateException("native binding for " + qualifiedName
                    + " was not resolved");
        }
        if (declaration.isTrait()) {
            JType.Declared javaInterface = JTypes.declaredForClass(binding.javaClass());
            // A generic native trait is a Solvik interface, not the Java one: a Solvik Boolean result
            // cannot override a Java `boolean`, and a Java iterator object never implements the
            // Solvik interface. The compiler-emitted `$of` adapter is the one conversion point.
            Unit unit = new Unit(moduleOf(qualifiedName), qualifiedName,
                    declaration.typeParameters(), false);
            boolean adapted = adaptsNativeTrait(declaration);
            return new JDecl.CompilationUnit(packageOf(qualifiedName),
                    List.of(trait(unit, declaration, javaInterface, adapted ? binding : null,
                            !adapted)));
        }
        Unit unit = new Unit(moduleOf(qualifiedName), qualifiedName,
                declaration.typeParameters(), false);
        boolean generic = !declaration.typeParameters().isEmpty();
        List<JType> javaArguments = generic
                ? declaration.typeParameters().stream()
                        .map(name -> (JType) new JType.TypeVariable(name)).toList()
                : javaObjectArguments(binding.javaClass());
        JType.Declared javaClass = JTypes.declaredForClass(binding.javaClass())
                .withArguments(javaArguments);
        boolean hasInstance = declaration.methods().stream()
                .anyMatch(method -> method.isConstructor(declaration.name()) || !method.isStatic());
        boolean hasConstructor = declaration.methods().stream()
                .anyMatch(method -> method.isConstructor(declaration.name()));
        List<JDecl.Member> members = new ArrayList<>();
        if (hasInstance) {
            members.add(JDecl.Field.of(
                    Set.of(JDecl.Modifier.PRIVATE, JDecl.Modifier.FINAL), javaClass, "$delegate"));
        }
        if (generic && hasInstance) {
            members.add(JDecl.Field.of(
                    Set.of(JDecl.Modifier.PRIVATE, JDecl.Modifier.FINAL), JTypes.STRING,
                    "$solvikType"));
        }
        if (hasInstance && !hasConstructor) {
            members.add(new JDecl.Constructor(Set.of(JDecl.Modifier.PRIVATE),
                    List.of(new JDecl.Parameter(javaClass, "delegate")),
                    List.of(new JStmt.ExpressionStatement(assign(
                            field("this", "$delegate", javaClass),
                            name("delegate", javaClass), javaClass))), List.of()));
        }
        if (!hasInstance) {
            members.add(new JDecl.Constructor(Set.of(JDecl.Modifier.PRIVATE), List.of(),
                    List.of(), List.of()));
        }
        for (Ast.FuncDecl exported : declaration.methods()) {
            members.add(unit.nativeMember(declaration, exported, binding, javaClass, hasInstance,
                    generic));
        }
        if (hasInstance && declaration.methods().stream().noneMatch(method -> !method.isStatic()
                && "toString".equals(method.name()))) {
            members.add(new JDecl.Method(Set.of(JDecl.Modifier.PUBLIC), List.of(), JTypes.STRING,
                    "toString", List.of(),
                    List.of(new JStmt.Return(new JExpr.Call(field("this", "$delegate", javaClass),
                            javaClass, "toString", List.of(), List.of(), JTypes.STRING))),
                    List.of(new JDecl.Annotation(JType.Declared.of("java.lang", "Override"),
                            List.of()))));
        }
        if (hasInstance) {
            members.add(unit.typeNameMethod(generic, declaration.name()));
        } else {
            members.add(new JDecl.Method(Set.of(JDecl.Modifier.PUBLIC), List.of(), JTypes.STRING,
                    "solvikTypeName", List.of(), List.of(new JStmt.Return(
                            new JExpr.StringLiteral(declaration.name()))), List.of()));
        }
        List<JDecl.Annotation> annotations = new ArrayList<>();
        if (suppressesEqualsHashWarning(declaration)) {
            annotations.add(suppression("overrides"));
        }
        JDecl.ClassDecl type = new JDecl.ClassDecl(JDecl.Kind.CLASS,
                Set.of(JDecl.Modifier.PUBLIC, JDecl.Modifier.FINAL),
                GeneratedNames.javaIdentifier(declaration.name()), declaration.typeParameters(),
                null, List.of(), members, annotations);
        return new JDecl.CompilationUnit(packageOf(qualifiedName), List.of(type));
    }

    // ------------------------------------------------------------------------- context

    /**
     * One compilation unit's lowering state.
     *
     * <p>It carries the lexical context a method body needs (declared type, type parameters, loop
     * labels, temporaries, compiled-once regex fields) but not the Solvik AST: each node is lowered
     * immediately, exactly once.
     */
    private record PendingPattern(String text, String field) {
    }

    private final class Unit {
        private final String module;
        private final String currentTypeName;
        private final List<String> typeParameters;
        private final boolean insideProgram;
        private final Map<String, String> patternFields = new LinkedHashMap<>();
        private final Deque<String> loopLabels = new ArrayDeque<>();
        private final List<PendingPattern> pendingPatterns = new ArrayList<>();
        private int patternCount;
        private int tempCount;
        private SolvikType currentReturnType = SolvikType.VOID;
        private String selfReference = "this";

        Unit(String module, String currentTypeName, List<String> typeParameters,
                boolean insideProgram) {
            this.module = module;
            this.currentTypeName = currentTypeName;
            this.typeParameters = List.copyOf(typeParameters);
            this.insideProgram = insideProgram;
        }

        // ----------------------------------------------------------- declarations

        JDecl.StaticInitializer staticInitializerCall(String methodName) {
            JExpr invocation = new JExpr.Call(null, classType(), methodName, List.of(),
                    List.of(), JTypes.VOID);
            return new JDecl.StaticInitializer(
                    List.of(new JStmt.ExpressionStatement(invocation)));
        }

        JDecl.Method staticInitializerMethod(Ast.StaticInitializer initializer,
                String methodName) {
            SolvikType previous = currentReturnType;
            currentReturnType = SolvikType.VOID;
            List<JStmt> body = statements(initializer.body());
            currentReturnType = previous;
            return new JDecl.Method(
                    Set.of(JDecl.Modifier.PRIVATE, JDecl.Modifier.STATIC), List.of(),
                    JTypes.VOID, methodName, List.of(), body, List.of());
        }

        JDecl.Method function(Ast.FuncDecl function, Set<JDecl.Modifier> modifiers) {
            if (function.isNative()) {
                return nativeFunction(function, modifiers);
            }
            SolvikType returnType = types.functionReturnTypes().get(function);
            JType javaReturn = returnType == null || returnType == SolvikType.VOID
                    ? JTypes.VOID : JTypes.of(returnType);
            boolean isStatic = modifiers.contains(JDecl.Modifier.STATIC);
            if (!isStatic && "equals".equals(function.name())) {
                javaReturn = JType.Primitive.BOOLEAN;
            }
            if (!isStatic && "hashCode".equals(function.name())) {
                javaReturn = JType.Primitive.INT;
            }
            String name = isStatic ? GeneratedNames.functionName(function.name())
                    : GeneratedNames.javaIdentifier(function.name());
            List<JStmt> body = new ArrayList<>();
            SolvikType previous = currentReturnType;
            currentReturnType = returnType;
            if (currentTypeName != null && !isStatic) {
                body.addAll(checkedEqualsPrologue(function));
            }
            body.addAll(statements(function.body()));
            currentReturnType = previous;
            return new JDecl.Method(modifiers, List.of(), javaReturn, name, parameters(function),
                    body, List.of());
        }

        /**
         * A native member's generated body: one call to the adapter resolved by the checker.
         *
         * <p>The receiver's opaque payload is the first adapter argument of an instance member; a
         * {@code Channel} argument passes its payload, and a {@code Channel} result is wrapped with
         * the type's {@code $of} factory. Values of every other type cross in their language
         * carrier. No reflection and no dynamic dispatch is involved.
         */
        JDecl.Method nativeFunction(Ast.FuncDecl function, Set<JDecl.Modifier> modifiers) {
            BuiltinBindings.Entry entry = bindingOf(function);
            SolvikType returnType = types.functionReturnTypes().get(function);
            JType javaReturn = returnType == null || returnType == SolvikType.VOID
                    ? JTypes.VOID : JTypes.of(returnType);
            List<JExpr> arguments = new ArrayList<>();
            if (!function.isStatic()) {
                arguments.add(payloadField());
            }
            arguments.addAll(boundArguments(function, entry));
            JExpr invocation = adapterCall(entry.adapter(), arguments, javaReturn);
            if (entry.result() == BuiltinBindings.Conversion.PAYLOAD_RESULT) {
                invocation = new JExpr.Call(null, (JType.Declared) JTypes.of(returnType),
                        "$of", List.of(), List.of(invocation), JTypes.of(returnType));
            }
            String name = function.isStatic()
                    ? GeneratedNames.functionName(function.name())
                    : GeneratedNames.javaIdentifier(function.name());
            List<JStmt> body = List.of(javaReturn == JTypes.VOID
                    ? new JStmt.ExpressionStatement(invocation)
                    : new JStmt.Return(invocation));
            return new JDecl.Method(modifiers, List.of(), javaReturn, name, parameters(function),
                    body, List.of());
        }

        /** A native constructor body: compute the opaque payload from the converted arguments. */
        JDecl.Constructor nativeConstructor(Ast.FuncDecl constructor, boolean isPublic) {
            BuiltinBindings.Entry entry = bindingOf(constructor);
            JExpr invocation = adapterCall(entry.adapter(), boundArguments(constructor, entry),
                    JTypes.OBJECT);
            List<JStmt> body = List.of(new JStmt.ExpressionStatement(assign(
                    field("this", "$payload", JTypes.OBJECT), invocation, JTypes.OBJECT)));
            return new JDecl.Constructor(
                    Set.of(isPublic ? JDecl.Modifier.PUBLIC : JDecl.Modifier.PRIVATE),
                    parameters(constructor), body, List.of());
        }

        private BuiltinBindings.Entry bindingOf(Ast.FuncDecl declaration) {
            BuiltinBindings.Entry entry = types.builtinBindingOf(declaration);
            if (entry == null) {
                throw new IllegalStateException("native member " + declaration.name()
                        + " has no resolved adapter binding");
            }
            return entry;
        }

        private List<JExpr> boundArguments(Ast.FuncDecl declaration, BuiltinBindings.Entry entry) {
            List<JExpr> arguments = new ArrayList<>();
            for (int index = 0; index < declaration.params().size(); index++) {
                Ast.Param parameter = declaration.params().get(index);
                SolvikType type = resolve(parameter.type());
                JType carrier = JTypes.of(type);
                JExpr argument = name(parameter.name(), carrier);
                if (entry.parameters().get(index) == BuiltinBindings.Conversion.PAYLOAD_ARGUMENT) {
                    argument = new JExpr.Call(argument, (JType.Declared) carrier, "$payload",
                            List.of(), List.of(), JTypes.OBJECT);
                }
                arguments.add(argument);
            }
            return arguments;
        }

        private JExpr adapterCall(String adapter, List<JExpr> arguments, JType returnType) {
            usesIo = true;
            noteIntrinsic(GeneratedNames.EXCEPTION_CLASS);
            return new JExpr.Call(null, ioType(), adapter, List.of(), arguments, returnType);
        }

        private JExpr payloadField() {
            return field("this", "$payload", JTypes.OBJECT);
        }

        private JType.Declared ioType() {
            return JType.Declared.of(GeneratedNames.GLOBAL_PACKAGE, GeneratedNames.IO_CLASS);
        }

        JDecl.Method abstractMethod(Ast.FuncDecl method) {
            SolvikType returned = types.functionReturnTypes().get(method);
            JType javaReturn = returned == null || returned == SolvikType.VOID
                    ? JTypes.VOID : JTypes.of(returned);
            return new JDecl.Method(Set.of(), List.of(), javaReturn,
                    GeneratedNames.javaIdentifier(method.name()), parameters(method), null,
                    List.of());
        }

        /**
         * The {@code $of} factory and the adapter type for a generic native trait.
         *
         * <p>A Java interface object is not a Solvik value: the Solvik interface is its own type, so
         * exactly one compiler-generated type implements it by delegation. Its name contains
         * {@code $}, which no Solvik identifier can spell, so it can never collide with a
         * declaration; like the {@code $of} factory of a native-backed type it is emitted with the
         * type that declares it.
         */
        List<JDecl.Member> traitAdapter(Ast.TypeDecl declaration, JavaBinding binding,
                JType.Declared javaInterface) {
            List<JType> variables = declaration.typeParameters().stream()
                    .map(name -> (JType) new JType.TypeVariable(
                            GeneratedNames.javaIdentifier(name)))
                    .toList();
            JType.Declared owner = classType();
            JType.Declared traitType = owner.withArguments(variables);
            JType.Declared delegateType = javaInterface.withArguments(variables);
            JType.Declared adapterType = new JType.Declared(owner.packageName(),
                    List.of(owner.name()), TRAIT_ADAPTER, variables);
            JDecl.Method factory = new JDecl.Method(
                    Set.of(JDecl.Modifier.PUBLIC, JDecl.Modifier.STATIC),
                    declaration.typeParameters(), traitType, TRAIT_FACTORY,
                    List.of(new JDecl.Parameter(delegateType, TRAIT_DELEGATE)),
                    List.of(new JStmt.Return(new JExpr.New(adapterType,
                            List.of(name(TRAIT_DELEGATE, delegateType)), traitType))),
                    List.of());

            List<JDecl.Member> members = new ArrayList<>();
            members.add(JDecl.Field.of(Set.of(JDecl.Modifier.PRIVATE, JDecl.Modifier.FINAL),
                    delegateType, TRAIT_DELEGATE));
            members.add(new JDecl.Constructor(Set.of(),
                    List.of(new JDecl.Parameter(delegateType, TRAIT_DELEGATE)),
                    List.of(new JStmt.ExpressionStatement(assign(
                            field("this", TRAIT_DELEGATE, delegateType),
                            name(TRAIT_DELEGATE, delegateType), delegateType))),
                    List.of()));
            for (Ast.FuncDecl method : declaration.methods()) {
                if (method.hasBody()) {
                    continue; // a source default is inherited from the interface
                }
                if (!(binding.members().get(method) instanceof Method javaMethod)) {
                    continue; // an unresolved binding is reported by the checker, never emitted
                }
                List<JDecl.Parameter> parameters = parameters(method);
                List<JExpr> arguments = new ArrayList<>();
                for (JDecl.Parameter parameter : parameters) {
                    arguments.add(name(parameter.name(), parameter.type()));
                }
                SolvikType returned = types.functionReturnTypes().get(method);
                JType javaReturn = returned == null || returned == SolvikType.VOID
                        ? JTypes.VOID : JTypes.of(returned);
                JExpr invocation = new JExpr.Call(
                        field("this", TRAIT_DELEGATE, delegateType), delegateType,
                        javaMethod.getName(), List.of(), arguments, javaReturn);
                members.add(new JDecl.Method(Set.of(JDecl.Modifier.PUBLIC), List.of(), javaReturn,
                        GeneratedNames.javaIdentifier(method.name()), parameters,
                        List.of(returned == SolvikType.VOID
                                ? new JStmt.ExpressionStatement(invocation)
                                : new JStmt.Return(invocation)),
                        List.of(new JDecl.Annotation(
                                JType.Declared.of("java.lang", "Override"), List.of()))));
            }
            JDecl.ClassDecl adapter = new JDecl.ClassDecl(JDecl.Kind.CLASS,
                    Set.of(JDecl.Modifier.PUBLIC, JDecl.Modifier.FINAL), TRAIT_ADAPTER,
                    declaration.typeParameters(), null, List.of(traitType), members, List.of());
            return List.of(factory, adapter);
        }

        JDecl.Constructor constructor(Ast.FuncDecl constructor, boolean isPublic, boolean generic) {
            if (constructor.isNative()) {
                return nativeConstructor(constructor, isPublic);
            }
            List<JDecl.Parameter> parameters = new ArrayList<>(parameters(constructor));
            List<JStmt> body = new ArrayList<>();
            if (generic) {
                parameters.add(new JDecl.Parameter(JTypes.STRING, "$solvikType"));
                body.add(new JStmt.ExpressionStatement(assign(
                        new JExpr.FieldAccess(name("this", selfType()), "$solvikType",
                                JTypes.STRING),
                        name("$solvikType", JTypes.STRING), JTypes.STRING)));
            }
            SolvikType previous = currentReturnType;
            currentReturnType = SolvikType.VOID;
            body.addAll(statements(constructor.body()));
            currentReturnType = previous;
            return new JDecl.Constructor(
                    Set.of(isPublic ? JDecl.Modifier.PUBLIC : JDecl.Modifier.PRIVATE), parameters,
                    body, List.of());
        }

        /** The delegating {@code default} method and the static bridge that holds its body. */
        List<JDecl.Member> traitDefault(Ast.FuncDecl method) {
            SolvikType returned = types.functionReturnTypes().get(method);
            boolean returnsValue = returned != null && returned != SolvikType.VOID;
            JType javaReturn = returnsValue ? JTypes.of(returned) : JTypes.VOID;
            List<JDecl.Parameter> parameters = parameters(method);
            List<JExpr> arguments = new ArrayList<>();
            arguments.add(name("this", selfType()));
            for (Ast.Param parameter : method.params()) {
                arguments.add(name(parameter.name(), JTypes.of(resolve(parameter.type()))));
            }
            String bridge = types.traitDefaultBridgeName(method);
            JExpr invocation = new JExpr.Call(null, classType(), bridge, List.of(), arguments,
                    javaReturn);
            List<JStmt> delegating = List.of(returnsValue
                    ? new JStmt.Return(invocation) : new JStmt.ExpressionStatement(invocation));
            JDecl.Method defaultMethod = new JDecl.Method(
                    Set.of(JDecl.Modifier.DEFAULT), List.of(), javaReturn,
                    GeneratedNames.javaIdentifier(method.name()), parameters, delegating,
                    List.of());

            List<JDecl.Parameter> bridgeParameters = new ArrayList<>();
            bridgeParameters.add(new JDecl.Parameter(classType(),
                    TypeChecker.TRAIT_SELF_JAVA_NAME));
            bridgeParameters.addAll(parameters);
            String outerSelf = selfReference;
            selfReference = TypeChecker.TRAIT_SELF_JAVA_NAME;
            SolvikType previous = currentReturnType;
            currentReturnType = returned;
            List<JStmt> body = statements(method.body());
            currentReturnType = previous;
            selfReference = outerSelf;
            JDecl.Method staticBridge = new JDecl.Method(
                    Set.of(JDecl.Modifier.PUBLIC, JDecl.Modifier.STATIC), List.of(), javaReturn,
                    bridge, bridgeParameters, body, List.of());
            return List.of(defaultMethod, staticBridge);
        }

        /** A concrete selector for the sole default shared with unrelated abstract traits. */
        JDecl.Method defaultBridge(TypeChecker.DefaultBridge bridge) {
            Ast.FuncDecl source = bridge.method();
            List<JDecl.Parameter> parameters = new ArrayList<>();
            List<JExpr> arguments = new ArrayList<>();
            arguments.add(name("this", selfType()));
            for (int i = 0; i < source.params().size(); i++) {
                Ast.Param parameter = source.params().get(i);
                JType type = JTypes.of(bridge.parameterTypes().get(i));
                parameters.add(new JDecl.Parameter(type, parameter.name()));
                arguments.add(name(parameter.name(), type));
            }
            JType javaReturn = bridge.returnType() == SolvikType.VOID
                    ? JTypes.VOID : JTypes.of(bridge.returnType());
            JExpr invocation = new JExpr.Call(null,
                    JTypes.generated(bridge.traitName()),
                    types.traitDefaultBridgeName(source), List.of(), arguments, javaReturn);
            List<JStmt> body = List.of(bridge.returnType() == SolvikType.VOID
                    ? new JStmt.ExpressionStatement(invocation)
                    : new JStmt.Return(invocation));
            return new JDecl.Method(Set.of(JDecl.Modifier.PUBLIC), List.of(), javaReturn,
                    GeneratedNames.javaIdentifier(source.name()), parameters, body, List.of());
        }

        JDecl.Method typeNameMethod(boolean generic, String writtenName) {
            JExpr value = generic ? name("$solvikType", JTypes.STRING)
                    : new JExpr.StringLiteral(writtenName);
            return new JDecl.Method(Set.of(JDecl.Modifier.PUBLIC), List.of(), JTypes.STRING,
                    "solvikTypeName", List.of(), List.of(new JStmt.Return(value)), List.of());
        }

        JDecl.Member nativeMember(Ast.TypeDecl declaration, Ast.FuncDecl exported,
                JavaBinding binding, JType.Declared javaClass, boolean hasInstance,
                boolean generic) {
            Executable executable = binding.members().get(exported);
            if (executable == null) {
                throw new IllegalStateException("native member " + exported.name()
                        + " of " + declaration.name() + " was not resolved");
            }
            List<JDecl.Parameter> parameters = new ArrayList<>();
            List<JExpr> arguments = new ArrayList<>();
            for (int i = 0; i < exported.params().size(); i++) {
                Ast.Param parameter = exported.params().get(i);
                JType type = JTypes.of(resolve(parameter.type()));
                parameters.add(new JDecl.Parameter(type, parameter.name()));
                Class<?> actual = executable.getParameterTypes()[i];
                JExpr argument = name(parameter.name(), type);
                if (actual.isPrimitive()) {
                    argument = new JExpr.Cast(JTypes.forClass(actual), argument);
                }
                if (isEqualsBridge(exported, executable, declaration)) {
                    JType.Declared declared = classType();
                    JType.Declared checked = generic ? declared.withArguments(
                            List.of(JType.Wildcard.any())) : declared;
                    JExpr other = new JExpr.InstanceOf(argument, checked,
                            "$solvikOther");
                    JExpr delegate = new JExpr.FieldAccess(
                            name("$solvikOther", checked), "$delegate", javaClass);
                    argument = new JExpr.Conditional(other, delegate, argument, JTypes.OBJECT);
                }
                arguments.add(argument);
            }
            boolean constructor = exported.isConstructor(declaration.name());
            if (constructor && generic) {
                parameters.add(new JDecl.Parameter(JTypes.STRING, "$solvikType"));
            }
            SolvikType returned = types.functionReturnTypes().get(exported);
            JType javaReturn = returned == SolvikType.VOID ? JTypes.VOID : JTypes.of(returned);
            if (!exported.isStatic() && "hashCode".equals(exported.name())
                    && exported.params().isEmpty()) {
                javaReturn = JType.Primitive.INT;
            }
            if (!exported.isStatic() && "equals".equals(exported.name())
                    && exported.params().size() == 1) {
                javaReturn = JType.Primitive.BOOLEAN;
            }
            String javaName = exported.isStatic()
                    ? GeneratedNames.functionName(exported.name())
                    : GeneratedNames.javaIdentifier(exported.name());
            List<JStmt> generalBody = new ArrayList<>();
            List<JStmt> tryBody = new ArrayList<>();
            boolean selfReturn = false;
            JExpr invocation;
            if (constructor) {
                if (generic) {
                    generalBody.add(new JStmt.ExpressionStatement(assign(
                            field("this", "$solvikType", JTypes.STRING),
                            name("$solvikType", JTypes.STRING), JTypes.STRING)));
                }
                invocation = new JExpr.New(javaClass, arguments, javaClass);
                tryBody.add(new JStmt.ExpressionStatement(
                        assign(field("this", "$delegate", javaClass), invocation, javaClass)));
            } else {
                Method method = (Method) executable;
                selfReturn = returned != null
                        && returned.kind() == SolvikType.Kind.USER
                        && currentTypeName != null
                        && currentTypeName.equals(returned.userName())
                        && method.getReturnType().equals(binding.javaClass());
                JExpr receiver = Modifier.isStatic(method.getModifiers())
                        ? null : field("this", "$delegate", javaClass);
                invocation = new JExpr.Call(receiver, javaClass, method.getName(), List.of(),
                        arguments, javaReturn);
                if (returned == SolvikType.VOID) {
                    tryBody.add(new JStmt.ExpressionStatement(invocation));
                } else if (selfReturn) {
                    JType.Declared owner = classType();
                    tryBody.add(new JStmt.Return(new JExpr.New(
                            generic ? owner.withArguments(List.of(JType.Wildcard.any())) : owner,
                            List.of(invocation), owner)));
                } else if (adaptsNativeTrait(returned)) {
                    // The Java interface object must become a Solvik value; the trait's generated
                    // factory is the one conversion, so no cast or unchecked call is emitted.
                    tryBody.add(new JStmt.Return(new JExpr.Call(null,
                            JTypes.generated(returned.userName()), TRAIT_FACTORY, List.of(),
                            List.of(invocation), javaReturn)));
                } else {
                    tryBody.add(new JStmt.Return(invocation));
                }
            }
            JType exceptionType = JTypes.EXCEPTION;
            JType runtimeType = JTypes.RUNTIME_EXCEPTION;
            JExpr nativeFailure = intrinsic("fail", JTypes.RUNTIME_EXCEPTION,
                    new JExpr.StringLiteral("SOL-R011"),
                    new JExpr.Binary(JExpr.BinaryOp.PLUS,
                            new JExpr.StringLiteral("native call failed: "),
                            name("failure", exceptionType), JTypes.STRING));
            generalBody.add(new JStmt.Try(new JStmt.Block(tryBody), List.of(
                    new JStmt.Catch(runtimeType, "failure",
                            new JStmt.Block(List.of(new JStmt.Throw(
                                    name("failure", runtimeType))))),
                    new JStmt.Catch(exceptionType, "failure",
                            new JStmt.Block(List.of(new JStmt.Throw(nativeFailure)))))));
            Set<JDecl.Modifier> modifiers = new LinkedHashSet<>();
            modifiers.add(exported.isPublic() ? JDecl.Modifier.PUBLIC : JDecl.Modifier.PRIVATE);
            if (exported.isStatic()) {
                modifiers.add(JDecl.Modifier.STATIC);
            }
            if (constructor) {
                return new JDecl.Constructor(modifiers, parameters, generalBody, List.of());
            }
            return new JDecl.Method(modifiers, List.of(), javaReturn, javaName, parameters,
                    generalBody, List.of());
        }

        // ------------------------------------------------------------- statements

        List<JStmt> statements(List<Ast.Stmt> body) {
            List<JStmt> statements = new ArrayList<>();
            for (Ast.Stmt statement : body) {
                JStmt lowered = statement(statement);
                if (lowered != null) {
                    statements.add(lowered);
                }
            }
            return statements;
        }

        private JStmt statement(Ast.Stmt statement) {
            return switch (statement) {
                case Ast.VarStmt var -> variable(var);
                case Ast.AssignStmt assign -> assignment(assign);
                case Ast.ExprStmt exprStmt -> expressionStatement(exprStmt);
                case Ast.IfStmt ifStmt -> ifStatement(ifStmt);
                case Ast.SwitchStmt switchStmt -> switchStatement(switchStmt);
                case Ast.WhileStmt whileStmt -> {
                    String label = "$loop" + tempCount++;
                    loopLabels.addLast(label);
                    JStmt body = block(whileStmt.body());
                    loopLabels.removeLast();
                    yield new JStmt.While(label, expr(whileStmt.condition()), body);
                }
                case Ast.BreakStmt ignored -> new JStmt.Break(loopLabels.peekLast());
                case Ast.ContinueStmt ignored -> new JStmt.Continue(loopLabels.peekLast());
                case Ast.ForStmt forStmt -> {
                    SolvikType iterable = types.typeOf(forStmt.iterable());
                    String label = "$loop" + tempCount++;
                    loopLabels.addLast(label);
                    if (iterable.isArray()) {
                        JStmt body = block(forStmt.body());
                        loopLabels.removeLast();
                        yield new JStmt.ForEach(label, JTypes.of(iterable.element()),
                                localName(forStmt, forStmt.name()), expr(forStmt.iterable()), body);
                    }
                    // A declared iteration protocol: hold the iterator the collection's own member
                    // returns, ask it for each element, and give the loop name a fresh declaration
                    // per iteration. The Java source is the same direct calls a hand-written
                    // `while` would make; the only allocation is the one iterator per loop.
                    SolvikType protocol = types.iterationProtocolOf(forStmt);
                    if (protocol == null) {
                        loopLabels.removeLast();
                        throw new IllegalStateException("for ... in over " + iterable
                                + " has no recorded iteration protocol");
                    }
                    JType.Declared iterator = declaredType(protocol);
                    String iteratorName = "$it" + tempCount++;
                    SolvikType element = types.bindingOf(forStmt).type();
                    List<JStmt> body = new ArrayList<>();
                    body.add(new JStmt.VarDecl(JTypes.of(element),
                            localName(forStmt, forStmt.name()), false,
                            new JExpr.Call(name(iteratorName, iterator), iterator, "next",
                                    List.of(), List.of(), JTypes.of(element)), List.of()));
                    body.addAll(statements(forStmt.body()));
                    loopLabels.removeLast();
                    yield new JStmt.For(label,
                            List.of(new JStmt.VarDecl(iterator, iteratorName, false,
                                    new JExpr.Call(expr(forStmt.iterable()),
                                            declaredType(iterable), "iterator", List.of(),
                                            List.of(), iterator), List.of())),
                            new JExpr.Call(name(iteratorName, iterator), iterator, "hasNext",
                                    List.of(), List.of(), JTypes.of(SolvikType.BOOLEAN)),
                            List.of(), new JStmt.Block(body));
                }
                case Ast.ReturnStmt returnStmt -> returned(returnStmt);
                case Ast.TryStmt tryStmt -> tryStatement(tryStmt);
                case Ast.SyncStmt syncStmt ->
                        new JStmt.Synchronized(expr(syncStmt.monitor()), block(syncStmt.body()));
                case Ast.RaiseStmt raise -> raised(raise);
                case Ast.BlockStmt block -> new JStmt.Block(statements(block.body()));
            };
        }

        /**
         * Lowers {@code try}/{@code catch}/{@code finally} (§7.4).
         *
         * <p>Java's own statement has exactly the required semantics, so the catch clause names the
         * generated exception class: every other throwable — the abort class, the exit class, and a
         * Java exception from a native call — passes the {@code catch} by, because it is not that
         * type.
         */
        private JStmt tryStatement(Ast.TryStmt tryStmt) {
            List<JStmt.Catch> catchers = new ArrayList<>();
            if (tryStmt.catcher() != null) {
                Ast.CatchClause catcher = tryStmt.catcher();
                noteIntrinsic(GeneratedNames.EXCEPTION_CLASS);
                catchers.add(new JStmt.Catch(exceptionType(),
                        localName(catcher, catcher.name()), block(catcher.body())));
            }
            return new JStmt.Try(block(tryStmt.body()), catchers,
                    tryStmt.finallyBody() == null ? null : block(tryStmt.finallyBody()));
        }

        /** Lowers {@code raise exception} to a Java {@code throw} (§7.4). */
        private JStmt raised(Ast.RaiseStmt raise) {
            noteIntrinsic(GeneratedNames.EXCEPTION_CLASS);
            return new JStmt.Throw(expr(raise.exception()));
        }

        private JType.Declared exceptionType() {
            JType carrier = JTypes.of(SolvikType.EXCEPTION);
            return carrier instanceof JType.Declared declared ? declared
                    : JTypes.programNested(GeneratedNames.EXCEPTION_CLASS);
        }

        private JStmt returned(Ast.ReturnStmt statement) {
            if (statement.value() == null) {
                return new JStmt.Return(null);
            }
            JExpr value = expr(statement.value());
            if (statement.value() instanceof Ast.NullLit
                    && (currentReturnType == SolvikType.INTEGER
                            || currentReturnType == SolvikType.BOOLEAN)) {
                value = new JExpr.Cast(JTypes.of(currentReturnType), value);
            }
            return new JStmt.Return(value);
        }

        private JStmt block(List<Ast.Stmt> body) {
            return new JStmt.Block(statements(body));
        }

        private JStmt variable(Ast.VarStmt var) {
            String name = localName(var, var.name());
            SolvikType type = types.typeOf(var);
            if (type == null || type == SolvikType.NULL) {
                return new JStmt.VarDecl(JTypes.OBJECT, name, false, new JExpr.NullLiteral(),
                        List.of());
            }
            JExpr initializer = var.initializer() == null ? new JExpr.NullLiteral()
                    : expr(var.initializer());
            return new JStmt.VarDecl(JTypes.of(type), name, false, initializer, List.of());
        }

        private JStmt assignment(Ast.AssignStmt assign) {
            if (assign.target() instanceof Ast.IndexExpr index) {
                return indexAssignment(assign, index);
            }
            JExpr target = switch (assign.target()) {
                case Ast.NameExpr name -> emitName(name);
                case Ast.OuterExpr outer -> reference(outer, outer.name());
                default -> expr(assign.target());
            };
            JType type = target.type();
            JExpr value = expr(assign.value());
            if ("..=".equals(assign.operator())) {
                value = intrinsic("concat", JTypes.STRING, target, value);
            }
            return new JStmt.ExpressionStatement(assign(target, value, type));
        }

        private JStmt indexAssignment(Ast.AssignStmt assign, Ast.IndexExpr index) {
            JExpr array = expr(index.target());
            JExpr offset = expr(index.index());
            SolvikType arrayType = types.typeOf(index.target());
            JType.Declared owner = JTypes.of(arrayType) instanceof JType.Declared declared
                    ? declared : JTypes.listOfAny();
            if (!"..=".equals(assign.operator())) {
                return new JStmt.ExpressionStatement(new JExpr.Call(array, owner, "set",
                        List.of(), List.of(offset, expr(assign.value())), JTypes.VOID));
            }
            String arrayVariable = "$solvikTarget" + tempCount++;
            String offsetVariable = "$solvikOffset" + tempCount++;
            List<JStmt> statements = new ArrayList<>();
            statements.add(new JStmt.VarDecl(JTypes.of(arrayType), arrayVariable, false, array,
                    List.of()));
            statements.add(new JStmt.VarDecl(JType.Primitive.INT, offsetVariable, false, offset,
                    List.of()));
            JExpr element = new JExpr.Call(name(arrayVariable, JTypes.of(arrayType)), owner, "get",
                    List.of(), List.of(name(offsetVariable, JType.Primitive.INT)),
                    JTypes.of(arrayType.element()));
            JExpr appended = intrinsic("concat", JTypes.STRING, element, expr(assign.value()));
            statements.add(new JStmt.ExpressionStatement(new JExpr.Call(
                    name(arrayVariable, JTypes.of(arrayType)), owner, "set", List.of(),
                    List.of(name(offsetVariable, JType.Primitive.INT), appended), JTypes.VOID)));
            return new JStmt.Block(statements);
        }

        /** Lowers a call used as a statement; its value, when any, is discarded. */
        private JStmt expressionStatement(Ast.ExprStmt statement) {
            return new JStmt.ExpressionStatement(expr(statement.expression()));
        }

        private JStmt ifStatement(Ast.IfStmt ifStmt) {
            List<Ast.Branch> branches = ifStmt.branches();
            JStmt chained = ifStmt.elseBody() == null ? null : block(ifStmt.elseBody());
            for (int i = branches.size() - 1; i >= 0; i--) {
                Ast.Branch branch = branches.get(i);
                chained = new JStmt.If(expr(branch.condition()), block(branch.body()), chained);
            }
            return chained;
        }

        private JStmt switchStatement(Ast.SwitchStmt switchStmt) {
            SolvikType subjectType = types.typeOf(switchStmt.subject());
            boolean hasRegex = switchStmt.cases().stream().anyMatch(Ast.CaseClause::regex);
            boolean nativeSwitch = subjectType == SolvikType.STRING
                    || subjectType == SolvikType.BYTE;
            JExpr subject = expr(switchStmt.subject());
            if (hasRegex || !nativeSwitch) {
                String variable = "$solvikSubject" + tempCount++;
                JStmt declaration = new JStmt.VarDecl(JTypes.of(subjectType), variable, false,
                        subject, List.of());
                subject = name(variable, JTypes.of(subjectType));
                JStmt selection = nativeSwitch
                        ? nativeSwitch(switchStmt, subject, hasRegex)
                        : equalitySwitch(switchStmt, subject, subjectType);
                return new JStmt.Block(List.of(declaration, selection));
            }
            return nativeSwitch(switchStmt, subject, false);
        }

        private JStmt nativeSwitch(Ast.SwitchStmt switchStmt, JExpr subject, boolean hasRegex) {
            List<JStmt.SwitchArm> arms = new ArrayList<>();
            List<Ast.CaseClause> regexes = new ArrayList<>();
            for (Ast.CaseClause clause : switchStmt.cases()) {
                if (clause.regex()) {
                    regexes.add(clause);
                } else {
                    arms.add(JStmt.SwitchArm.of(literalLabel(clause.label()),
                            statements(clause.body())));
                }
            }
            JStmt defaultBody;
            if (!regexes.isEmpty()) {
                defaultBody = new JStmt.Block(List.<JStmt>of(
                        regexChain(regexes, switchStmt.defaultBody(), subject)));
            } else {
                defaultBody = switchStmt.defaultBody() == null ? null
                        : new JStmt.Block(statements(switchStmt.defaultBody()));
            }
            return new JStmt.Switch(subject, arms, defaultBody);
        }

        private JStmt regexChain(List<Ast.CaseClause> regexes, List<Ast.Stmt> defaultBody,
                JExpr subject) {
            JStmt chained = defaultBody == null ? new JStmt.Block(List.of())
                    : new JStmt.Block(statements(defaultBody));
            for (int i = regexes.size() - 1; i >= 0; i--) {
                Ast.CaseClause clause = regexes.get(i);
                JExpr condition = new JExpr.Call(
                        name(patternField(clause.label()), JTypes.PATTERN), JTypes.PATTERN,
                        "matcher", List.of(), List.of(subject), JTypes.declaredForClass(
                                java.util.regex.Matcher.class));
                JExpr matches = new JExpr.Call(condition,
                        JTypes.declaredForClass(java.util.regex.Matcher.class), "matches", List.of(),
                        List.of(), JType.Primitive.BOOLEAN);
                chained = new JStmt.If(matches, new JStmt.Block(statements(clause.body())),
                        chained);
            }
            return chained;
        }

        private JStmt equalitySwitch(Ast.SwitchStmt switchStmt, JExpr subject,
                SolvikType subjectType) {
            List<Ast.CaseClause> cases = switchStmt.cases();
            JStmt chained = switchStmt.defaultBody() == null ? null
                    : new JStmt.Block(statements(switchStmt.defaultBody()));
            for (int i = cases.size() - 1; i >= 0; i--) {
                Ast.CaseClause clause = cases.get(i);
                JExpr label = literalLabel(clause.label());
                JExpr condition = subjectType == SolvikType.BOOLEAN
                        ? new JExpr.Binary(JExpr.BinaryOp.EQUAL, subject, label,
                                JType.Primitive.BOOLEAN)
                        : intrinsic("equal", JType.Primitive.BOOLEAN, subject, label);
                chained = new JStmt.If(condition, new JStmt.Block(statements(clause.body())),
                        chained);
            }
            return chained == null ? new JStmt.Block(List.of()) : chained;
        }

        private JExpr literalLabel(Ast.Expr label) {
            return switch (label) {
                case Ast.IntLit literal -> new JExpr.IntLiteral(literal.text(),
                        JType.Primitive.INT);
                case Ast.DecimalLit literal -> new JExpr.FloatingLiteral(literal.text(),
                        JType.Primitive.DOUBLE);
                case Ast.StringLit literal -> new JExpr.StringLiteral(literal.value());
                case Ast.BoolLit literal -> new JExpr.BooleanLiteral(literal.value());
                case Ast.UnaryExpr unary when "-".equals(unary.operator())
                        && unary.operand() instanceof Ast.IntLit literal ->
                        new JExpr.Unary(JExpr.UnaryOp.NEGATE,
                                new JExpr.IntLiteral(literal.text(), JType.Primitive.INT),
                                JType.Primitive.INT);
                default -> new JExpr.NullLiteral();
            };
        }

        private String patternField(Ast.Expr label) {
            String text = ((Ast.StringLit) label).value();
            return patternFields.computeIfAbsent(text, pattern -> {
                String field = "$solvikCase" + patternCount++;
                pendingPatterns.add(new PendingPattern(text, field));
                return field;
            });
        }

        void addPatternFields(List<JDecl.Member> members, boolean interfaceUnit) {
            List<JDecl.Member> patterns = new ArrayList<>();
            for (PendingPattern pattern : pendingPatterns) {
                JExpr compilation = new JExpr.Call(null, JTypes.PATTERN, "compile", List.of(),
                        List.of(new JExpr.StringLiteral(pattern.text())), JTypes.PATTERN);
                // Interface fields are implicitly public static final; a class keeps them private.
                Set<JDecl.Modifier> modifiers = new LinkedHashSet<>();
                modifiers.add(interfaceUnit ? JDecl.Modifier.PUBLIC : JDecl.Modifier.PRIVATE);
                modifiers.add(JDecl.Modifier.STATIC);
                modifiers.add(JDecl.Modifier.FINAL);
                patterns.add(JDecl.Field.of(modifiers, JTypes.PATTERN, pattern.field(),
                        compilation));
            }
            // A static field initializer or static block may call a method that uses a compiled
            // regex, so pattern fields must initialize before every user static initializer.
            members.addAll(0, patterns);
        }

        // ------------------------------------------------------------ expressions

        JExpr expr(Ast.Expr expression) {
            return switch (expression) {
                case Ast.IntLit literal -> numericLiteral(literal.text(),
                        types.typeOf(expression));
                case Ast.DecimalLit literal -> numericLiteral(literal.text(),
                        types.typeOf(expression));
                case Ast.StringLit literal -> new JExpr.StringLiteral(literal.value());
                case Ast.BoolLit literal -> new JExpr.BooleanLiteral(literal.value());
                case Ast.NullLit ignored -> new JExpr.NullLiteral();
                case Ast.NameExpr name -> emitName(name);
                case Ast.OuterExpr outer -> reference(outer, outer.name());
                case Ast.UnaryExpr unary -> unary(unary);
                case Ast.BinaryExpr binary -> binary(binary);
                case Ast.CallExpr call -> call(call);
                case Ast.IndexExpr index -> {
                    SolvikType target = types.typeOf(index.target());
                    JType.Declared owner = JTypes.of(target) instanceof JType.Declared declared
                            ? declared : JTypes.listOfAny();
                    yield new JExpr.Call(expr(index.target()), owner, "get", List.of(),
                            List.of(expr(index.index())), JTypes.of(types.typeOf(expression)));
                }
                case Ast.ArrayLit literal -> arrayLiteral(literal);
                case Ast.MemberExpr member -> member(member);
                case Ast.TypeApplyExpr application -> expr(application.target());
                case Ast.QualifiedNameExpr qualified ->
                        throw new IllegalStateException("unresolved type name "
                                + qualified.qualifiedName());
            };
        }

        private JExpr member(Ast.MemberExpr member) {
            if (types.isStaticMember(member)) {
                return staticField(member, member.name(), JTypes.of(types.typeOf(member)));
            }
            JType type = JTypes.of(types.typeOf(member));
            if (isArraySize(member)) {
                // The member reads the carrier's length; the receiver expression is evaluated once.
                return new JExpr.Call(expr(member.target()), JTypes.listOfAny(),
                        TypeChecker.ARRAY_SIZE_MEMBER, List.of(), List.of(), JType.Primitive.INT);
            }
            return new JExpr.FieldAccess(expr(member.target()),
                    GeneratedNames.javaIdentifier(member.name()), type);
        }

        /** Whether this is the immutable `size` member read on an array value (§5). */
        private boolean isArraySize(Ast.MemberExpr member) {
            SolvikType target = types.typeOf(member.target());
            return target != null && target.isArray()
                    && TypeChecker.ARRAY_SIZE_MEMBER.equals(member.name());
        }

        private JExpr emitName(Ast.NameExpr name) {
            if ("self".equals(name.name()) && !"this".equals(selfReference)) {
                return name(selfReference, selfType());
            }
            return reference(name, "self".equals(name.name()) ? "this" : name.name());
        }

        private JExpr reference(Ast.Expr node, String fallback) {
            JType type = JTypes.of(types.typeOf(node));
            if (types.isStaticMember(node)) {
                return staticField(node, fallback, type);
            }
            String member = types.implicitMemberOf(node);
            if (member != null) {
                return new JExpr.FieldAccess(name(selfReference, selfType()),
                        GeneratedNames.javaIdentifier(member), JTypes.of(types.typeOf(node)));
            }
            TypeChecker.Binding binding = types.bindingOf(node);
            if (binding == null) {
                return name(fallback, type);
            }
            String javaName = binding.javaName();
            return name("this".equals(javaName) ? javaName
                    : GeneratedNames.javaIdentifier(javaName), type);
        }

        private JExpr staticField(Ast.Node node, String name, JType type) {
            String owner = types.staticOwnerOf(node);
            return new JExpr.StaticField(owner == null ? classType() : JTypes.generated(owner),
                    GeneratedNames.javaIdentifier(name), type);
        }

        private JExpr unary(Ast.UnaryExpr unary) {
            if ("!".equals(unary.operator())) {
                return new JExpr.Unary(JExpr.UnaryOp.NOT, expr(unary.operand()),
                        JType.Primitive.BOOLEAN);
            }
            SolvikType result = types.typeOf(unary);
            if (result == null) {
                result = types.typeOf(unary.operand());
            }
            if (unary.operand() instanceof Ast.IntLit literal) {
                if (result == SolvikType.INTEGER && "2147483648".equals(literal.text())) {
                    return new JExpr.StaticField(JTypes.INTEGER, "MIN_VALUE",
                            JType.Primitive.INT);
                }
                return negateLiteral(literal.text(), result);
            }
            if (unary.operand() instanceof Ast.DecimalLit literal) {
                return negateLiteral(literal.text(), result);
            }
            JExpr operand = numericOperand(unary.operand(), result);
            if (!result.isPrimitiveNumeric()) {
                return new JExpr.Call(operand, declared(JTypes.of(result)), "negate",
                        List.of(), List.of(), JTypes.of(result));
            }
            JExpr negated = new JExpr.Unary(JExpr.UnaryOp.NEGATE, operand,
                    JTypes.primitive(result));
            if (result == SolvikType.BYTE || result == SolvikType.SHORT) {
                return new JExpr.Cast(JTypes.primitive(result), negated);
            }
            return negated;
        }

        private JExpr negateLiteral(String text, SolvikType result) {
            if (result == SolvikType.BIG_INTEGER || result == SolvikType.BIG_DECIMAL) {
                return new JExpr.New(declared(JTypes.of(result)),
                        List.of(new JExpr.StringLiteral("-" + text)), JTypes.of(result));
            }
            if (result == SolvikType.BYTE || result == SolvikType.SHORT) {
                return new JExpr.Cast(JTypes.primitive(result), new JExpr.Unary(
                        JExpr.UnaryOp.NEGATE,
                        new JExpr.IntLiteral(text, JType.Primitive.INT), JType.Primitive.INT));
            }
            JExpr literal = numericLiteral(text, result);
            return new JExpr.Unary(JExpr.UnaryOp.NEGATE, literal, JTypes.primitive(result));
        }

        private JExpr numericLiteral(String text, SolvikType type) {
            if (type == SolvikType.BIG_INTEGER) {
                return new JExpr.New(JTypes.BIG_INTEGER, List.of(new JExpr.StringLiteral(text)),
                        JTypes.BIG_INTEGER);
            }
            if (type == SolvikType.BIG_DECIMAL) {
                return new JExpr.New(JTypes.BIG_DECIMAL, List.of(new JExpr.StringLiteral(text)),
                        JTypes.BIG_DECIMAL);
            }
            if (type == SolvikType.BYTE) {
                return new JExpr.Cast(JType.Primitive.BYTE,
                        new JExpr.IntLiteral(text, JType.Primitive.INT));
            }
            if (type == SolvikType.SHORT) {
                return new JExpr.Cast(JType.Primitive.SHORT,
                        new JExpr.IntLiteral(text, JType.Primitive.INT));
            }
            if (type == SolvikType.LONG) {
                return new JExpr.IntLiteral(text, JType.Primitive.LONG);
            }
            if (type == SolvikType.FLOAT) {
                return new JExpr.FloatingLiteral(text, JType.Primitive.FLOAT);
            }
            if (type == SolvikType.DOUBLE) {
                return new JExpr.FloatingLiteral(text, JType.Primitive.DOUBLE);
            }
            return new JExpr.IntLiteral(text, JType.Primitive.INT);
        }

        private JExpr binary(Ast.BinaryExpr binary) {
            String operator = binary.operator();
            SolvikType leftType = types.typeOf(binary.left());
            SolvikType rightType = types.typeOf(binary.right());
            if (List.of("+", "-", "*", "/", "%", "<", "<=", ">", ">=").contains(operator)) {
                return arithmetic(binary, operator, leftType, rightType);
            }
            JExpr left = expr(binary.left());
            JExpr right = expr(binary.right());
            return switch (operator) {
                case "==", "!=" -> {
                    JExpr equality = equality(left, leftType, right, rightType, false);
                    if ("!=".equals(operator)) {
                        yield new JExpr.Unary(JExpr.UnaryOp.NOT, equality,
                                JType.Primitive.BOOLEAN);
                    }
                    yield equality;
                }
                case "===" -> equality(left, leftType, right, rightType, true);
                case "&&", "||" -> new JExpr.Binary(
                        "&&".equals(operator) ? JExpr.BinaryOp.AND : JExpr.BinaryOp.OR,
                        left, right, JType.Primitive.BOOLEAN);
                case ".." -> canRenderDirectly(leftType) && canRenderDirectly(rightType)
                        ? new JExpr.Binary(JExpr.BinaryOp.PLUS,
                                renderInConcat(left, leftType,
                                        leftType != SolvikType.STRING),
                                renderInConcat(right, rightType, false), JTypes.STRING)
                        : intrinsic("concat", JTypes.STRING, left, right);
                default -> throw new IllegalStateException("unknown operator " + operator);
            };
        }

        private JExpr arithmetic(Ast.BinaryExpr binary, String operator, SolvikType leftType,
                SolvikType rightType) {
            SolvikType widened = SolvikType.widest(leftType, rightType);
            JExpr left = numericOperand(binary.left(), widened);
            JExpr right = numericOperand(binary.right(), widened);
            boolean arithmeticOperator = List.of("+", "-", "*", "/", "%").contains(operator);
            if (widened.isPrimitiveNumeric()) {
                JType primitive = JTypes.primitive(widened);
                if (("/".equals(operator) || "%".equals(operator)) && widened.isIntegral()) {
                    return intrinsic("/".equals(operator) ? "divide" : "remainder", primitive,
                            left, right);
                }
                return new JExpr.Binary(operatorOf(operator), left, right,
                        arithmeticOperator ? primitive : JType.Primitive.BOOLEAN);
            }
            if (!arithmeticOperator) {
                JExpr compared = new JExpr.Call(left, declared(JTypes.of(widened)),
                        "compareTo", List.of(), List.of(right), JType.Primitive.INT);
                return new JExpr.Binary(operatorOf(operator), compared,
                        new JExpr.IntLiteral("0", JType.Primitive.INT), JType.Primitive.BOOLEAN);
            }
            if ("/".equals(operator) || "%".equals(operator)) {
                return intrinsic("/".equals(operator) ? "divide" : "remainder",
                        JTypes.of(widened), left, right);
            }
            String method = switch (operator) {
                case "+" -> "add";
                case "-" -> "subtract";
                case "*" -> "multiply";
                default -> throw new IllegalStateException("unknown operator " + operator);
            };
            return new JExpr.Call(left, declared(JTypes.of(widened)), method, List.of(),
                    List.of(right), JTypes.of(widened));
        }

        private JExpr.BinaryOp operatorOf(String operator) {
            return switch (operator) {
                case "+" -> JExpr.BinaryOp.PLUS;
                case "-" -> JExpr.BinaryOp.MINUS;
                case "*" -> JExpr.BinaryOp.MULTIPLY;
                case "/" -> JExpr.BinaryOp.DIVIDE;
                case "%" -> JExpr.BinaryOp.REMAINDER;
                case "<" -> JExpr.BinaryOp.LESS;
                case "<=" -> JExpr.BinaryOp.LESS_EQUAL;
                case ">" -> JExpr.BinaryOp.GREATER;
                case ">=" -> JExpr.BinaryOp.GREATER_EQUAL;
                default -> throw new IllegalStateException("unknown operator " + operator);
            };
        }

        private JExpr numericOperand(Ast.Expr operand, SolvikType promoted) {
            if (promoted.isPrimitiveNumeric() && isLiteralValue(operand)) {
                String text = operand instanceof Ast.IntLit integer ? integer.text()
                        : ((Ast.DecimalLit) operand).text();
                return numericLiteral(text, promoted);
            }
            return numericOperand(operand, types.typeOf(operand), promoted);
        }

        private JExpr numericOperand(Ast.Expr operand, SolvikType type, SolvikType promoted) {
            JExpr expression = expr(operand);
            JExpr checked = optionalRequireNonNull(expression, JTypes.of(type));
            if (!promoted.isPrimitiveNumeric()) {
                return checked;
            }
            String method = promoted == SolvikType.DOUBLE ? "doubleValue"
                    : promoted == SolvikType.FLOAT ? "floatValue"
                    : promoted == SolvikType.LONG ? "longValue" : "intValue";
            JType primitive = JTypes.primitive(promoted);
            return new JExpr.Call(checked, declared(JTypes.of(type)), method, List.of(),
                    List.of(), primitive);
        }

        private JExpr optionalRequireNonNull(JExpr expression, JType type) {
            return new JExpr.Call(null, JTypes.OBJECTS, "requireNonNull", List.of(),
                    List.of(expression), type);
        }

        private JExpr equality(JExpr left, SolvikType leftType, JExpr right, SolvikType rightType,
                boolean identity) {
            if (!identity && leftType != null && rightType != null
                    && leftType.isPrimitiveNumeric() && rightType.isPrimitiveNumeric()) {
                SolvikType promoted = SolvikType.widest(leftType, rightType);
                String helper = promoted == SolvikType.FLOAT ? "equalFloat"
                        : promoted == SolvikType.DOUBLE ? "equalFloating" : "equalIntegral";
                return intrinsic(helper, JType.Primitive.BOOLEAN, left, right);
            }
            if (leftType == SolvikType.NULL || rightType == SolvikType.NULL
                    || leftType != null && leftType.isArray()
                    || rightType != null && rightType.isArray()) {
                return referenceEquality(left, right);
            }
            if (leftType != null && leftType.equals(rightType) && switch (leftType.kind()) {
                case BYTE, SHORT, INTEGER, LONG, FLOAT, DOUBLE, BIG_INTEGER, BIG_DECIMAL, STRING,
                        BOOLEAN -> true;
                default -> false;
            }) {
                return new JExpr.Call(null, JTypes.OBJECTS, "equals", List.of(),
                        List.of(left, right), JType.Primitive.BOOLEAN);
            }
            return intrinsic("identity", JType.Primitive.BOOLEAN, left, right);
        }

        private JExpr referenceEquality(JExpr left, JExpr right) {
            // Java compares a reference with `null` directly; only two values need the Object
            // cast that makes the comparison a reference comparison rather than numeric.
            boolean plainNull = left instanceof JExpr.NullLiteral
                    && !right.type().isPrimitive()
                    || right instanceof JExpr.NullLiteral && !left.type().isPrimitive();
            return new JExpr.Binary(JExpr.BinaryOp.EQUAL,
                    plainNull ? left : asObject(left), plainNull ? right : asObject(right),
                    JType.Primitive.BOOLEAN);
        }

        /** A value as {@code Object}, without the redundant cast a value already typed Object has. */
        private JExpr asObject(JExpr expression) {
            return JTypes.OBJECT.equals(expression.type()) ? expression
                    : new JExpr.Cast(JTypes.OBJECT, expression);
        }

        private JExpr boxedEquality(JExpr left, SolvikType leftType, JExpr right,
                SolvikType rightType) {
            if (leftType != null && leftType.isArray() || rightType != null && rightType.isArray()) {
                return referenceEquality(left, right);
            }
            if (canRenderDirectly(leftType) && canRenderDirectly(rightType)) {
                return new JExpr.Call(null, JTypes.OBJECTS, "equals", List.of(),
                        List.of(left, right), JType.Primitive.BOOLEAN);
            }
            return intrinsic("equal", JType.Primitive.BOOLEAN, left, right);
        }

        private JExpr arrayLiteral(Ast.ArrayLit literal) {
            SolvikType elementType = resolve(literal.arrayType()).element();
            JType javaElement = JTypes.of(elementType);
            List<JExpr> elements = literal.elements().stream().map(this::expr).toList();
            long size = types.typeOf(literal) instanceof SolvikType arrayType
                    && arrayType.length() != null
                    ? arrayType.length() : literal.elements().size();
            JType.Declared result = JTypes.list(javaElement);
            if (size == literal.elements().size()) {
                return new JExpr.Call(null, JTypes.ARRAYS, "asList", List.of(javaElement),
                        elements, result);
            }
            noteIntrinsic("array");
            JExpr empty = new JExpr.NewArray(JTypes.OBJECT, elements);
            return new JExpr.Call(null, JTypes.programClass(), "array", List.of(javaElement),
                    List.of(new JExpr.IntLiteral(Long.toString(size), JType.Primitive.INT), empty),
                    result);
        }

        // -------------------------------------------------------------- calls

        private JExpr call(Ast.CallExpr call) {
            TypeChecker.TraitDefaultCall traitDefault = types.traitDefaultOf(call);
            if (traitDefault != null) {
                List<JExpr> arguments = new ArrayList<>();
                arguments.add(expr(call.arguments().getFirst()));
                arguments.addAll(argumentsOf(call, 1));
                return new JExpr.Call(null, JTypes.generated(traitDefault.traitName()),
                        types.traitDefaultBridgeName(traitDefault.method()), List.of(), arguments,
                        JTypes.of(types.typeOf(call)));
            }
            if (types.isStaticMember(call)) {
                String name = call.callee() instanceof Ast.MemberExpr member ? member.name()
                        : call.callee() instanceof Ast.NameExpr bare ? bare.name() : "<error>";
                String owner = types.staticOwnerOf(call);
                JExpr invoked = new JExpr.Call(null,
                        owner == null ? JTypes.programClass() : JTypes.generated(owner),
                        GeneratedNames.functionName(name), List.of(), argumentsOf(call),
                        JTypes.of(types.typeOf(call)));
                return owner != null && types.nativeDeclaration(owner) != null
                        ? checkedNativeResult(invoked, types.typeOf(call)) : invoked;
            }
            if (call.callee() instanceof Ast.MemberExpr member) {
                return memberCall(call, member);
            }
            if (call.callee() instanceof Ast.TypeApplyExpr application) {
                String written = application.target() instanceof Ast.NameExpr name ? name.name()
                        : ((Ast.QualifiedNameExpr) application.target()).qualifiedName();
                return construction(call, written);
            }
            if (call.callee() instanceof Ast.QualifiedNameExpr qualified) {
                return types.isConstruction(call)
                        ? construction(call, qualified.qualifiedName())
                        : functionCall(call, qualified.qualifiedName(), argumentsOf(call));
            }
            Ast.NameExpr callee = (Ast.NameExpr) call.callee();
            String name = callee.name();
            String member = types.implicitMemberOf(call);
            if (member != null) {
                JExpr receiver = name(selfReference, selfType());
                return new JExpr.Call(receiver, classType(),
                        GeneratedNames.javaIdentifier(member), List.of(),
                        argumentsOf(call), JTypes.of(types.typeOf(call)));
            }
            List<JExpr> arguments = argumentsOf(call);
            return switch (name) {
                case "exit" -> intrinsic("exit", JTypes.VOID, arguments);
                case "Exception" -> {
                    noteIntrinsic(GeneratedNames.EXCEPTION_CLASS);
                    JType.Declared created = exceptionType();
                    yield new JExpr.New(created, arguments, created);
                }
                case "Byte", "Short", "Integer", "Long", "Float", "Double", "BigInteger",
                        "BigDecimal" -> conversion(name, call, arguments);
                case "String" -> renderValue(arguments.getFirst(),
                        types.typeOf(call.arguments().getFirst()));
                case "Boolean" -> intrinsic("toBoolean", JTypes.BOOLEAN, arguments);
                default -> types.isConstruction(call)
                        ? construction(call, name)
                        : functionCall(call, name, arguments);
            };
        }

        private JExpr conversion(String name, Ast.CallExpr call, List<JExpr> arguments) {
            SolvikType target = SolvikType.named(name);
            Ast.Expr argument = call.arguments().getFirst();
            SolvikType source = types.typeOf(argument);
            if (source == target && isLiteralValue(argument)) {
                return arguments.getFirst();
            }
            if (source == target) {
                return optionalRequireNonNull(arguments.getFirst(), JTypes.of(target));
            }
            return intrinsic("to" + name, JTypes.of(target), arguments);
        }

        private JExpr memberCall(Ast.CallExpr call, Ast.MemberExpr member) {
            JExpr target = expr(member.target());
            SolvikType receiver = types.typeOf(member.target());
            JType resultType = JTypes.of(types.typeOf(call));
            if (receiver == SolvikType.STRING) {
                JExpr text = stringMethod(member.name(), target, call);
                if (text != null) {
                    return text;
                }
            }
            if (types.isNative(receiver) && declaresMember(receiver, member.name())) {
                return checkedNativeResult(new JExpr.Call(target, receiverType(target),
                        GeneratedNames.javaIdentifier(member.name()), List.of(),
                        argumentsOf(call), resultType), types.typeOf(call));
            }
            Method javaMath = types.javaMathMethodOf(call);
            if (javaMath != null) {
                return new JExpr.Call(target, receiverType(target), javaMath.getName(), List.of(),
                        javaMathArguments(call, javaMath), resultType);
            }
            return switch (member.name()) {
                case "type" -> intrinsic("type", JTypes.STRING, target);
                case "toString" -> call.arguments().isEmpty()
                        ? renderValue(target, receiver)
                        : new JExpr.Call(target, receiverType(target), member.name(), List.of(),
                                javaMathArguments(call, types.javaMathMethodOf(call)), resultType);
                case "hashCode" -> intrinsic("hash", JTypes.INTEGER, target);
                case "equals" -> boxedEquality(target, receiver,
                        expr(call.arguments().getFirst()),
                        types.typeOf(call.arguments().getFirst()));
                default -> new JExpr.Call(target, receiverType(target),
                        GeneratedNames.javaIdentifier(member.name()), List.of(),
                        argumentsOf(call), resultType);
            };
        }

        /**
         * Guards a declared non-null native result against a Java {@code null} (§11.1).
         *
         * <p>A nullable result is returned as it is, and a carrier that cannot be {@code null}
         * (a Java primitive) or cannot be tested (a type variable) is left alone.
         */
        private JExpr checkedNativeResult(JExpr invoked, SolvikType result) {
            if (result == null || result.kind() == SolvikType.Kind.VOID || result.isNullable()
                    || result.isVariable()) {
                return invoked;
            }
            JType carrier = JTypes.of(result);
            if (!(carrier instanceof JType.Declared)) {
                return invoked;
            }
            if (!result.arguments().isEmpty()) {
                // Recovering a parameterized type from an Object result would be an unchecked cast,
                // so the guard returns its own argument type for this case.
                noteIntrinsic("checkedResult");
                return new JExpr.Call(null, JTypes.programClass(), "checkedResult", List.of(),
                        List.of(invoked), carrier);
            }
            noteIntrinsic("checked");
            return new JExpr.Cast(carrier, new JExpr.Call(null, JTypes.programClass(), "checked",
                    List.of(), List.of(invoked), JTypes.OBJECT));
        }

        private static JType.Declared declared(JType type) {
            return type instanceof JType.Declared declared ? declared : JTypes.OBJECT;
        }

        private JType.Declared receiverType(JExpr receiver) {
            return receiver.type() instanceof JType.Declared declared ? declared
                    : JTypes.OBJECT;
        }

        private boolean declaresMember(SolvikType type, String name) {
            Ast.TypeDecl declaration = types.nativeDeclaration(type.userName());
            return declaration != null && declaration.methods().stream()
                    .anyMatch(method -> !method.isStatic() && method.name().equals(name));
        }

        private JExpr stringMethod(String method, JExpr target, Ast.CallExpr call) {
            List<JExpr> arguments = argumentsOf(call);
            return switch (method) {
                case "size" -> new JExpr.Call(target, JTypes.STRING, "length", List.of(),
                        List.of(), JType.Primitive.INT);
                case "substring" -> intrinsic("textSubstring", JTypes.STRING,
                        prepend(target, arguments));
                case "indexOf" -> new JExpr.Call(target, JTypes.STRING, "indexOf", List.of(),
                        arguments, JType.Primitive.INT);
                case "contains" -> new JExpr.Call(target, JTypes.STRING, "contains", List.of(),
                        arguments, JType.Primitive.BOOLEAN);
                case "startsWith" -> new JExpr.Call(target, JTypes.STRING, "startsWith", List.of(),
                        arguments, JType.Primitive.BOOLEAN);
                case "endsWith" -> new JExpr.Call(target, JTypes.STRING, "endsWith", List.of(),
                        arguments, JType.Primitive.BOOLEAN);
                case "strip" -> new JExpr.Call(target, JTypes.STRING, "strip", List.of(), List.of(),
                        JTypes.STRING);
                case "stripLeading" -> new JExpr.Call(target, JTypes.STRING, "stripLeading",
                        List.of(), List.of(), JTypes.STRING);
                case "stripTrailing" -> new JExpr.Call(target, JTypes.STRING, "stripTrailing",
                        List.of(), List.of(), JTypes.STRING);
                case "toUpperCase" -> new JExpr.Call(target, JTypes.STRING, "toUpperCase",
                        List.of(), List.of(new JExpr.StaticField(JTypes.LOCALE, "ROOT",
                                JTypes.LOCALE)), JTypes.STRING);
                case "toLowerCase" -> new JExpr.Call(target, JTypes.STRING, "toLowerCase",
                        List.of(), List.of(new JExpr.StaticField(JTypes.LOCALE, "ROOT",
                                JTypes.LOCALE)), JTypes.STRING);
                case "replace" -> new JExpr.Call(target, JTypes.STRING, "replace", List.of(),
                        arguments, JTypes.STRING);
                case "split" -> intrinsic("textSplit", JTypes.list(JTypes.STRING),
                        prepend(target, arguments));
                default -> null;
            };
        }

        private List<JExpr> prepend(JExpr first, List<JExpr> rest) {
            List<JExpr> all = new ArrayList<>();
            all.add(first);
            all.addAll(rest);
            return all;
        }

        private List<JExpr> javaMathArguments(Ast.CallExpr call, Method method) {
            List<JExpr> arguments = new ArrayList<>();
            for (int i = 0; i < call.arguments().size(); i++) {
                Ast.Expr argument = call.arguments().get(i);
                JExpr expression = expr(argument);
                Class<?> parameter = method.getParameterTypes()[i];
                if (parameter.isPrimitive()) {
                    String unbox = switch (parameter.getName()) {
                        case "int" -> "intValue";
                        case "long" -> "longValue";
                        case "double" -> "doubleValue";
                        case "float" -> "floatValue";
                        case "short" -> "shortValue";
                        case "byte" -> "byteValue";
                        default -> null;
                    };
                    boolean alreadyPrimitive = isLiteralValue(argument)
                            && types.typeOf(argument) == SolvikType.named(boxedName(parameter));
                    if (unbox != null && !alreadyPrimitive) {
                        expression = new JExpr.Call(
                                optionalRequireNonNull(expression,
                                        JTypes.of(types.typeOf(argument))),
                                declared(JTypes.of(types.typeOf(argument))), unbox, List.of(),
                                List.of(), JTypes.primitive(types.typeOf(argument)));
                    }
                }
                arguments.add(expression);
            }
            return arguments;
        }

        private static String boxedName(Class<?> parameter) {
            return switch (parameter.getName()) {
                case "int" -> "Integer";
                case "long" -> "Long";
                case "double" -> "Double";
                case "float" -> "Float";
                case "short" -> "Short";
                case "byte" -> "Byte";
                default -> parameter.getSimpleName();
            };
        }

        private List<JExpr> argumentsOf(Ast.CallExpr call) {
            return argumentsOf(call, 0);
        }

        private List<JExpr> argumentsOf(Ast.CallExpr call, int offset) {
            List<SolvikType> parameters = types.selectedParameterTypesOf(call);
            List<JExpr> arguments = new ArrayList<>();
            for (int i = offset; i < call.arguments().size(); i++) {
                Ast.Expr argument = call.arguments().get(i);
                JExpr emitted = expr(argument);
                int parameterIndex = i - offset;
                if (argument instanceof Ast.NullLit && parameterIndex < parameters.size()) {
                    SolvikType parameter = parameters.get(parameterIndex);
                    if (parameter != SolvikType.ANY && parameter != SolvikType.NULL
                            && !parameter.isVariable()) {
                        emitted = new JExpr.Cast(JTypes.of(parameter), emitted);
                    }
                }
                arguments.add(emitted);
            }
            return arguments;
        }

        private JExpr functionCall(Ast.CallExpr call, String written, List<JExpr> arguments) {
            String target = types.functionTargetOf(call);
            JType resultType = JTypes.of(types.typeOf(call));
            if (target == null) {
                JType.Declared owner = written.contains("::")
                        ? JTypes.generated(written) : JTypes.programClass();
                return new JExpr.Call(null, owner, GeneratedNames.functionName(written), List.of(),
                        arguments, resultType);
            }
            int separator = target.lastIndexOf("::");
            if (separator >= 0) {
                String modulePath = target.substring(0, separator);
                JType.Declared owner = JType.Declared.of(GeneratedNames.javaPackage(modulePath),
                        GeneratedNames.FUNCTIONS_CLASS);
                return new JExpr.Call(null, owner,
                        GeneratedNames.functionName(target.substring(separator + 2)), List.of(),
                        arguments, resultType);
            }
            return new JExpr.Call(null, JTypes.programClass(),
                    GeneratedNames.functionName(target), List.of(), arguments, resultType);
        }

        private JExpr construction(Ast.CallExpr call, String written) {
            List<JExpr> arguments = argumentsOf(call);
            SolvikType constructed = types.typeOf(call);
            JType.Declared created = JTypes.generated(
                    constructed == null ? written : constructed.userName());
            if (constructed == null || constructed.arguments().isEmpty()) {
                return new JExpr.New(created, arguments, created);
            }
            List<JType> typeArguments = constructed.arguments().stream().map(JTypes::of).toList();
            JType.Declared genericCreated = created.withArguments(typeArguments);
            List<JExpr> all = new ArrayList<>(arguments);
            all.add(new JExpr.StringLiteral(runtimeTypeName(constructed)));
            return new JExpr.New(genericCreated, all, genericCreated);
        }

        private String runtimeTypeName(SolvikType type) {
            if (type.kind() != SolvikType.Kind.USER) {
                return type.toString();
            }
            String simple = type.userName().contains("::")
                    ? type.userName().substring(type.userName().lastIndexOf("::") + 2)
                    : type.userName();
            if (type.arguments().isEmpty()) {
                return simple;
            }
            return simple + "<" + type.arguments().stream().map(this::runtimeTypeName)
                    .reduce((left, right) -> left + ", " + right).orElse("") + ">";
        }

        private JExpr renderInConcat(JExpr expression, SolvikType type, boolean mustBeText) {
            if (expression instanceof JExpr.NullLiteral) {
                return new JExpr.StringLiteral("null");
            }
            if (type == SolvikType.BIG_DECIMAL) {
                return renderValue(expression, type);
            }
            if (type == null || !rendersLikeJavaConcat(type)) {
                return intrinsic("render", JTypes.STRING, expression);
            }
            if (type == SolvikType.NULL || mustBeText && type != SolvikType.STRING) {
                return new JExpr.Call(null, JTypes.STRING, "valueOf", List.of(),
                        List.of(asObject(expression)), JTypes.STRING);
            }
            return expression;
        }

        private JExpr renderValue(JExpr expression, SolvikType type) {
            if (expression instanceof JExpr.NullLiteral) {
                return new JExpr.StringLiteral("null");
            }
            if (type == SolvikType.BIG_DECIMAL) {
                return intrinsic("plainText", JTypes.STRING, expression);
            }
            if (canRenderDirectly(type)
                    || type != null && type.kind() == SolvikType.Kind.USER) {
                return new JExpr.Call(null, JTypes.STRING, "valueOf", List.of(),
                        List.of(asObject(expression)), JTypes.STRING);
            }
            return intrinsic("render", JTypes.STRING, expression);
        }

        // --------------------------------------------------------------- helpers

        private List<JDecl.Parameter> parameters(Ast.FuncDecl function) {
            List<JDecl.Parameter> parameters = new ArrayList<>();
            for (Ast.Param parameter : function.params()) {
                parameters.add(new JDecl.Parameter(JTypes.of(resolve(parameter.type())),
                        GeneratedNames.javaIdentifier(parameter.name())));
            }
            return parameters;
        }

        private List<JStmt> checkedEqualsPrologue(Ast.FuncDecl function) {
            if (function.isStatic()
                    || !"equals".equals(function.name()) || function.params().size() != 1
                    || !isNullableAny(function.params().getFirst().type())
                    || function.returnType() == null
                    || !isNamed(function.returnType(), "Boolean")) {
                return List.of();
            }
            String parameter = function.params().getFirst().name();
            JType.Declared raw = classType();
            JType.Declared peerType = typeParameters.isEmpty() ? raw
                    : raw.withArguments(typeParameters.stream()
                            .map(name -> (JType) new JType.TypeVariable(name)).toList());
            JType.Declared checkedType = typeParameters.isEmpty() ? raw
                    : raw.withArguments(List.of(JType.Wildcard.any()));
            JExpr test = new JExpr.InstanceOf(name(parameter, JTypes.OBJECT), checkedType, null);
            List<JStmt> statements = new ArrayList<>();
            statements.add(new JStmt.If(new JExpr.Unary(JExpr.UnaryOp.NOT, test,
                    JType.Primitive.BOOLEAN),
                    new JStmt.Block(List.of(new JStmt.Return(
                            new JExpr.BooleanLiteral(false)))), null));
            List<JDecl.Annotation> annotations = typeParameters.isEmpty() ? List.of()
                    : List.of(suppression("unchecked"));
            statements.add(new JStmt.VarDecl(peerType, TypeChecker.EQUALS_PEER_JAVA_NAME, false,
                    new JExpr.Cast(peerType, name(parameter, JTypes.OBJECT)), annotations));
            return statements;
        }

        private JType selfType() {
            return currentTypeName == null ? JTypes.programClass()
                    : JTypes.generated(currentTypeName);
        }

        private JType.Declared classType() {
            return currentTypeName == null ? JTypes.programClass()
                    : JTypes.generated(currentTypeName);
        }

        /** Whether a returned type is a generic native trait, so the Java result must be adapted. */
        private boolean adaptsNativeTrait(SolvikType type) {
            if (type == null || type.kind() != SolvikType.Kind.USER) {
                return false;
            }
            Ast.TypeDecl declaration = types.nativeDeclaration(type.userName());
            return declaration != null && JavaLowerer.adaptsNativeTrait(declaration);
        }

        /** A Java declared type named by a Solvik user type, which is how every member call names it. */
        private JType.Declared declaredType(SolvikType type) {
            if (JTypes.of(type) instanceof JType.Declared declared) {
                return declared;
            }
            throw new IllegalStateException("a member call needs a declared Java type, found " + type);
        }

        private String localName(Ast.Node node, String fallback) {
            TypeChecker.Binding binding = types.bindingOf(node);
            if (binding == null) {
                return GeneratedNames.javaIdentifier(fallback);
            }
            String javaName = binding.javaName();
            return "this".equals(javaName) ? javaName
                    : GeneratedNames.javaIdentifier(javaName);
        }

        private JExpr intrinsic(String name, JType returnType, JExpr... arguments) {
            return intrinsic(name, returnType, List.of(arguments));
        }

        private JExpr intrinsic(String name, JType returnType, List<JExpr> arguments) {
            noteIntrinsic(name);
            return new JExpr.Call(null, JTypes.programClass(), name, List.of(), arguments,
                    returnType);
        }

        /** The Solvik type a write position names, for the Java carrier this lowering needs. */
        SolvikType resolve(Ast.TypeRef type) {
            SolvikType recorded = types.typeOf(type);
            if (recorded != null) {
                return recorded;
            }
            return switch (type) {
                case Ast.TypeRef.Array array -> {
                    Integer length = array.size() instanceof Ast.IntLit literal
                            ? Integer.valueOf(literal.text()) : null;
                    SolvikType resolved = SolvikType.array(resolve(array.element()), length);
                    yield array.nullable() ? resolved.asNullable() : resolved;
                }
                case Ast.TypeRef.Named named -> {
                    SolvikType resolved = resolveNamed(named);
                    yield named.nullable() ? resolved.asNullable() : resolved;
                }
            };
        }

        private SolvikType resolveNamed(Ast.TypeRef.Named type) {
            if (typeParameters.contains(type.name())) {
                return SolvikType.variable(type.name());
            }
            SolvikType builtin = SolvikType.named(type.name());
            if (builtin != null) {
                return builtin;
            }
            String written = resolveWrittenName(type.name());
            Ast.TypeDecl nativeDeclaration = types.nativeDeclaration(written);
            if (nativeDeclaration != null) {
                return SolvikType.user(written, type.arguments().isEmpty()
                        ? nativeDeclaration.typeParameters().stream()
                                .map(ignored -> SolvikType.ANY).toList()
                        : type.arguments().stream().map(this::resolve).toList());
            }
            if (!type.arguments().isEmpty()) {
                return SolvikType.user(written,
                        type.arguments().stream().map(this::resolve).toList());
            }
            return SolvikType.user(written);
        }

        /** Whether a written type is exactly the named, non-null type {@code name}. */
        private static boolean isNamed(Ast.TypeRef type, String name) {
            return type instanceof Ast.TypeRef.Named named && !named.nullable()
                    && name.equals(named.name()) && named.arguments().isEmpty();
        }

        /** Whether a written type is {@code Any?}, the parameter type of the equality contract. */
        private static boolean isNullableAny(Ast.TypeRef type) {
            return type instanceof Ast.TypeRef.Named named && named.nullable()
                    && "Any".equals(named.name()) && named.arguments().isEmpty();
        }

        private String resolveWrittenName(String written) {
            if (written.contains("::") || module == null) {
                return written;
            }
            String scoped = module + "::" + written;
            return typeNames.contains(scoped) ? scoped : written;
        }
    }

    // ------------------------------------------------------------------ static helpers

    /**
     * A binding that names no type parameters must still not emit a raw Java type: a generic Java
     * class is used with {@code Object} arguments, exactly as the binding's untyped members mean.
     */
    private static List<JType> javaObjectArguments(Class<?> javaClass) {
        int count = javaClass.getTypeParameters().length;
        List<JType> arguments = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            arguments.add(JTypes.OBJECT);
        }
        return arguments;
    }

    private static boolean isEqualsBridge(Ast.FuncDecl exported, Executable executable,
            Ast.TypeDecl declaration) {
        return !exported.isStatic() && !exported.isConstructor(declaration.name())
                && "equals".equals(exported.name()) && exported.params().size() == 1
                && executable.getParameterTypes()[0] == Object.class;
    }

    private static boolean isLiteralValue(Ast.Expr expression) {
        return switch (expression) {
            case Ast.IntLit ignored -> true;
            case Ast.DecimalLit ignored -> true;
            case Ast.StringLit ignored -> true;
            case Ast.BoolLit ignored -> true;
            default -> false;
        };
    }

    private static boolean canRenderDirectly(SolvikType type) {
        return type != null && switch (type.kind()) {
            case BYTE, SHORT, INTEGER, LONG, FLOAT, DOUBLE, BIG_INTEGER, BIG_DECIMAL, STRING,
                    BOOLEAN, NULL -> true;
            default -> false;
        };
    }

    private static boolean rendersLikeJavaConcat(SolvikType type) {
        return switch (type.kind()) {
            case BYTE, SHORT, INTEGER, LONG, FLOAT, DOUBLE, BIG_INTEGER, STRING, BOOLEAN, USER,
                    NULL -> true;
            default -> false;
        };
    }

    private static String moduleOf(String qualifiedName) {
        int separator = qualifiedName.lastIndexOf("::");
        return separator < 0 ? null : qualifiedName.substring(0, separator);
    }

    /** The Java package of a qualified Solvik type name; top-level types live in {@code solvik}. */
    private static String packageOf(String qualifiedName) {
        String module = moduleOf(qualifiedName);
        return module == null ? GeneratedNames.GLOBAL_PACKAGE
                : GeneratedNames.javaPackage(module);
    }

    private void noteIntrinsic(String name) {
        intrinsicUses.add(name);
    }

    private static JExpr name(String name, JType type) {
        return new JExpr.Name(name, type);
    }

    private static JExpr field(String target, String name, JType targetType) {
        return new JExpr.FieldAccess(name(target, targetType), name, targetType);
    }

    private static JExpr assign(JExpr target, JExpr value, JType type) {
        return new JExpr.Assignment(target, value, type);
    }
}
