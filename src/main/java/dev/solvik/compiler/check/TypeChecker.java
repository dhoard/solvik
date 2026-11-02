package dev.solvik.compiler.check;

import dev.solvik.compiler.ast.Ast;
import dev.solvik.compiler.bind.BuiltinBindings;
import dev.solvik.compiler.bind.JavaBinding;
import dev.solvik.compiler.bind.JavaMathMethods;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.diagnostic.SourcePos;
import dev.solvik.compiler.lexer.NumericSuffix;
import dev.solvik.compiler.types.ConversionText;
import dev.solvik.compiler.types.SolvikType;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * Name resolution and type checking for the implemented subset of Solvik.
 *
 * <p>Rules enforced here come from {@code LANGUAGE.md}: the three declaration forms and their
 * initializers (§3), nullability and narrowing (§2, §7.1), numeric widening and the operator set
 * (§4), assignment compatibility
 * (§2), array indexing and the {@code size} member (§5), and statement-only calls with no return
 * value (§3).
 *
 * <p>Types are recorded per expression node in an identity map, because structurally equal literals
 * are distinct nodes.
 */
public final class TypeChecker {

    /** Source-inexpressible Java local holding the checked peer of a user equality override. */
    public static final String EQUALS_PEER_JAVA_NAME = "$solvikOther";

    /** Source-inexpressible Java parameter binding {@code self} inside a static trait-default bridge. */
    public static final String TRAIT_SELF_JAVA_NAME = "$solvikSelf";

    /** The language's built-in functions; a top-level declaration cannot take one of these names. */
    private static final Set<String> BUILT_IN_FUNCTIONS = Set.of("exit");

    public static final String TYPE_ERROR = "SOL-T001";
    public static final String UNSUPPORTED = "SOL-T002";
    public static final String INVALID_TYPE_NAME = "SOL-T003";
    /** A reference to a declaration that is private to a module the referrer is outside of (§2). */
    public static final String PRIVATE_DECLARATION = "SOL-T011";
    /** A {@code pub} declaration whose visible signature names a type private to its module (§2). */
    public static final String PRIVATE_EXPOSURE = "SOL-T012";

    /**
     * The one member of every array type: its length (§5).
     *
     * <p>The length of a fixed {@code T[N]} is part of its type and the length of a size-erased
     * {@code T[]} is only known at run time, so the member is a read of the carrier's length, not a
     * stored Java field. It is immutable: an array never changes length after construction.
     */
    public static final String ARRAY_SIZE_MEMBER = "size";
    public static final String UNREACHABLE = "SOL-T004";
    public static final String RETURN_OUTSIDE_FUNCTION = "SOL-T005";
    /** A {@code case regex} label whose pattern does not compile (§3, "switch"). */
    public static final String INVALID_REGEX = "SOL-T006";
    /** {@code break} or {@code continue} outside a loop (§3, "Control flow"). */
    public static final String LOOP_CONTROL_OUTSIDE_LOOP = "SOL-T007";
    /**
     * An operation that provably fails for the literal values given (§10).
     *
     * <p>A failure the compiler can see is a compile error, not a run-time abort, so dividing by a
     * literal zero, a Decimal division that cannot terminate, and a conversion of a literal that
     * cannot succeed are all reported here instead of at run time.
     */
    public static final String STATIC_FAILURE = "SOL-T008";
    /**
     * A generic type named where a type is written — with or without arguments (§2, "Generics").
     *
     * <p>Type arguments are inferred at construction and never written, so {@code Box<Integer>} is
     * not a written type, and bare {@code Box} is a type no value can inhabit. Both are reported
     * rather than silently ignored.
     */
    public static final String WRITTEN_TYPE_ARGUMENT = "SOL-T009";
    /**
     * A write to a member that is immutable after construction (§2, "Mutability").
     *
     * <p>A member is private and immutable unless {@code pub} or {@code mut} says otherwise, so
     * this is the diagnostic for writing one without {@code mut}.
     */
    public static final String IMMUTABLE_MEMBER = "SOL-T010";
    /**
     * A value that may be {@code Null} where a non-null value is required ({@code LANGUAGE.md} §2.2).
     *
     * <p>Member access, indexing, arithmetic, conditions, and other operations that need a value are
     * rejected until a null check proves the value is present; printing, equality, and
     * {@code String(value)} accept a nullable value.
     */
    public static final String NULL_UNSAFE = "SOL-T015";
    /**
     * A {@code catch} clause whose type is not {@code Exception} ({@code LANGUAGE.md} §7.4).
     *
     * <p>{@code Exception} is the only catchable type: with no inheritance there is nothing else a
     * clause could match, and a nullable or unrelated type would accept a clause that can never
     * run or a value that is never {@code Null}.
     */
    public static final String CATCH_TYPE = "SOL-T016";
    /**
     * A {@code return} inside a {@code finally} block ({@code LANGUAGE.md} §7.4).
     *
     * <p>Java lets it discard a pending return value or exception; Solvik rejects the spelling
     * instead of silently changing what the {@code return} was for.
     */
    public static final String FINALLY_RETURN = "SOL-T017";

    private final Diagnostics diagnostics;
    private final ClassLoader nativeLoader;
    /** Approved bundled root modules, compared by identity rather than names or record equality. */
    private final Set<Ast.ModuleDecl> standardLibraryModules =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final IdentityHashMap<Ast.TypeDecl, JavaBinding> nativeBindings = new IdentityHashMap<>();
    /** The adapter entrypoint resolved for every bundled {@code native} declaration. */
    private final IdentityHashMap<Ast.FuncDecl, BuiltinBindings.Entry> builtinBindings =
            new IdentityHashMap<>();
    /** Type declarations loaded from packaged resources, compared by identity. */
    private final Set<Ast.TypeDecl> trustedTypes =
            Collections.newSetFromMap(new IdentityHashMap<>());
    /** Member calls on built-in big numbers, resolved to the Java method they invoke. */
    private final IdentityHashMap<Ast.CallExpr, Method> javaMathCalls =
            new IdentityHashMap<>();
    private final IdentityHashMap<Ast.Expr, SolvikType> expressionTypes = new IdentityHashMap<>();
    private final IdentityHashMap<Ast.VarStmt, SolvikType> variableTypes = new IdentityHashMap<>();
    /** Arguments already checked while fixing a pending construction, so they are not reported twice. */
    private final IdentityHashMap<Ast.Expr, SolvikType> prechecked = new IdentityHashMap<>();
    private final IdentityHashMap<Ast.Node, Binding> bindings = new IdentityHashMap<>();
    /** Name references and calls that mean {@code self.<name>} (§3, "Member names"). */
    private final IdentityHashMap<Ast.Node, String> implicitMembers = new IdentityHashMap<>();
    /** Full module paths and their first source positions; repeated blocks reopen the namespace. */
    private final Map<String, SourcePos> declaredModules = new LinkedHashMap<>();
    /** The module whose body is being checked, or {@code null} at the top level. */
    private String currentModule;
    /** The qualified name of the type body being checked, or {@code null} outside a type. */
    private String currentTypeName;
    /** Whether the member being checked is static, where no {@code self} exists. */
    private boolean staticContext;
    /** Type parameters of the enclosing generic type that a static member may not use. */
    private List<String> unavailableTypeParameters = List.of();
    /** Static member references and the qualified type that owns them, keyed by node identity. */
    private final IdentityHashMap<Ast.Node, String> staticOwners = new IdentityHashMap<>();
    /** Call nodes naming a function, with the qualified name the emitter must call (§2). */
    private final IdentityHashMap<Ast.Node, String> functionCalls = new IdentityHashMap<>();
    /** The overload selected for each resolved call, retained so Java emission cannot reselect it. */
    private final IdentityHashMap<Ast.CallExpr, Ast.FuncDecl> selectedOverloads =
            new IdentityHashMap<>();
    /** Selected parameter types after receiver/type-argument substitution. */
    private final IdentityHashMap<Ast.CallExpr, List<SolvikType>> selectedOverloadParameters =
            new IdentityHashMap<>();
    /** Explicit {@code Trait.method(self, ...)} calls and the selected default declaration. */
    private final IdentityHashMap<Ast.CallExpr, TraitDefaultCall> traitDefaultCalls =
            new IdentityHashMap<>();
    /** Static bridge holding each trait default's single body, keyed by declaration identity. */
    private final IdentityHashMap<Ast.FuncDecl, String> traitDefaultBridges =
            new IdentityHashMap<>();
    /** Call nodes that construct a user type, so the emitter knows to emit {@code new} (§11). */
    private final Set<Ast.Node> constructions =
            Collections.newSetFromMap(new IdentityHashMap<>());
    /** Per-body counter behind {@link Binding#javaName()}; reset for every emitted method. */
    private final Map<String, Integer> ordinals = new HashMap<>();
    /** How many loops enclose the statement being checked; {@code break} and {@code continue} need one. */
    private int loopDepth;
    /** How many {@code finally} blocks enclose the statement being checked (§7.4). */
    private int finallyDepth;
    /** Whether the body being checked is a constructor, where immutable members may be set. */
    private boolean inConstructor;
    private final IdentityHashMap<Ast.FuncDecl, SolvikType> returnTypes = new IdentityHashMap<>();
    private final IdentityHashMap<Ast.FieldDecl, SolvikType> fieldTypes = new IdentityHashMap<>();
    private final Map<String, TypeInfo> userTypes = new LinkedHashMap<>();
    private final Map<String, List<Ast.FuncDecl>> functions = new LinkedHashMap<>();
    /** Declaring type for every method, retained for inherited signature resolution. */
    private final IdentityHashMap<Ast.FuncDecl, String> methodOwners = new IdentityHashMap<>();
    /** Direct nominal trait contracts, keyed by the implementing type's qualified name. */
    private final Map<String, List<String>> implementedTraits = new LinkedHashMap<>();
    /** Concrete forwarders needed when one default also satisfies unrelated abstract traits. */
    private final Map<String, List<DefaultBridge>> defaultBridges = new LinkedHashMap<>();
    private Scope scope;
    /**
     * Non-null facts proven by an enclosing check, keyed by binding identity (§7.1).
     *
     * <p>A fact is dropped as soon as the binding is assigned again, so a mutable value is never
     * assumed to stay non-null across a write.
     */
    private final IdentityHashMap<Binding, SolvikType> narrowed = new IdentityHashMap<>();
    private SolvikType currentReturnType = SolvikType.VOID;
    private boolean inFunction;
    /** Whether the body being checked is a {@code static { ... }} initializer. */
    private boolean inStaticInitializer;
    private List<String> typeParameters = List.of();

    private TypeChecker(Diagnostics diagnostics, ClassLoader nativeLoader,
            Set<Ast.ModuleDecl> standardLibraryModules) {
        this(diagnostics, nativeLoader, standardLibraryModules, Set.of());
    }

    private TypeChecker(Diagnostics diagnostics, ClassLoader nativeLoader,
            Set<Ast.ModuleDecl> standardLibraryModules, Set<Ast.TypeDecl> trustedTypes) {
        this.diagnostics = diagnostics;
        this.nativeLoader = nativeLoader;
        this.standardLibraryModules.addAll(standardLibraryModules);
        this.trustedTypes.addAll(trustedTypes);
    }

    /** Checks a program and returns the recorded types. Errors are added to {@code diagnostics}. */
    public static Result check(Ast.Program program, Diagnostics diagnostics) {
        return check(program, diagnostics, ClassLoader.getPlatformClassLoader());
    }

    public static Result check(Ast.Program program, Diagnostics diagnostics, ClassLoader loader) {
        return check(program, diagnostics, loader, Set.of());
    }

    /** Checks a loaded program with explicit loader-owned standard-library module provenance. */
    public static Result check(Ast.Program program, Diagnostics diagnostics, ClassLoader loader,
            Set<Ast.ModuleDecl> standardLibraryModules) {
        return check(program, diagnostics, loader, standardLibraryModules, Set.of());
    }

    /**
     * Checks a loaded program with packaged-module and packaged-type provenance.
     *
     * <p>Only a type parsed from a packaged resource may declare a {@code native} member: trust is
     * where the declaration was loaded from, not how it is spelled.
     */
    public static Result check(Ast.Program program, Diagnostics diagnostics, ClassLoader loader,
            Set<Ast.ModuleDecl> standardLibraryModules, Set<Ast.TypeDecl> trustedTypes) {
        TypeChecker checker = new TypeChecker(diagnostics, loader, standardLibraryModules,
                trustedTypes);
        checker.run(program);
        return new Result(checker.expressionTypes, checker.variableTypes, checker.returnTypes,
                checker.fieldTypes, checker.bindings, checker.implicitMembers,
                checker.functionCalls, checker.constructions, checker.nativeBindings,
                checker.userTypes.entrySet().stream().filter(e -> e.getValue() != null
                        && e.getValue().declaration().nativeClass() != null)
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().declaration())),
                checker.javaMathCalls, checker.staticOwners, checker.selectedOverloads,
                checker.selectedOverloadParameters, checker.defaultBridges,
                checker.traitDefaultCalls, checker.traitDefaultBridges, checker.builtinBindings);
    }

    /** Types recorded per node, keyed by identity. */
    public record Result(IdentityHashMap<Ast.Expr, SolvikType> expressionTypes,
                         IdentityHashMap<Ast.VarStmt, SolvikType> variableTypes,
                         IdentityHashMap<Ast.FuncDecl, SolvikType> functionReturnTypes,
                         IdentityHashMap<Ast.FieldDecl, SolvikType> fieldTypes,
                         IdentityHashMap<Ast.Node, Binding> bindings,
                         IdentityHashMap<Ast.Node, String> implicitMembers,
                         IdentityHashMap<Ast.Node, String> functionCalls,
                         Set<Ast.Node> constructions, IdentityHashMap<Ast.TypeDecl, JavaBinding> nativeBindings,
                         Map<String, Ast.TypeDecl> nativeDeclarations,
                         IdentityHashMap<Ast.CallExpr, Method> javaMathCalls,
                         IdentityHashMap<Ast.Node, String> staticOwners,
                         IdentityHashMap<Ast.CallExpr, Ast.FuncDecl> selectedOverloads,
                         IdentityHashMap<Ast.CallExpr, List<SolvikType>> selectedOverloadParameters,
                         Map<String, List<DefaultBridge>> defaultBridges,
                         IdentityHashMap<Ast.CallExpr, TraitDefaultCall> traitDefaultCalls,
                         IdentityHashMap<Ast.FuncDecl, String> traitDefaultBridges,
                         IdentityHashMap<Ast.FuncDecl, BuiltinBindings.Entry> builtinBindings) {

        /** The qualified type owning a static member reference, or {@code null} for an instance one. */
        public String staticOwnerOf(Ast.Node node) {
            return staticOwners.get(node);
        }

        /** Whether this node refers to a static member that code generation reaches through its type. */
        public boolean isStaticMember(Ast.Node node) {
            return staticOwners.containsKey(node);
        }

        /** The Java method a big-number member call invokes, or {@code null}. */
        public Method javaMathMethodOf(Ast.Node node) {
            return node instanceof Ast.CallExpr call ? javaMathCalls.get(call) : null;
        }

        /** The declaration selected by Solvik overload resolution, or {@code null}. */
        public Ast.FuncDecl selectedOverloadOf(Ast.CallExpr call) {
            return selectedOverloads.get(call);
        }

        /** The selected overload's resolved parameter types, or an empty list for other calls. */
        public List<SolvikType> selectedParameterTypesOf(Ast.CallExpr call) {
            return selectedOverloadParameters.getOrDefault(call, List.of());
        }

        /** Java forwarders required on this concrete type for unambiguous Solvik defaults. */
        public List<DefaultBridge> defaultBridgesFor(String typeName) {
            return defaultBridges.getOrDefault(typeName, List.of());
        }

        /**
         * The explicitly selected trait default for this call, or {@code null} for an ordinary call.
         *
         * <p>The selected declaration, not merely the trait, is retained so emission can call the
         * exact bridge the checker resolved and never let javac reselect an overload.
         */
        public TraitDefaultCall traitDefaultOf(Ast.CallExpr call) {
            return traitDefaultCalls.get(call);
        }

        /** The Java name of the static bridge holding one trait default's body. */
        public String traitDefaultBridgeName(Ast.FuncDecl method) {
            return traitDefaultBridges.get(method);
        }

        public Ast.TypeDecl nativeDeclaration(String name) { return nativeDeclarations.get(name); }

        /** The adapter entrypoint for a bundled {@code native} declaration, or {@code null}. */
        public BuiltinBindings.Entry builtinBindingOf(Ast.FuncDecl declaration) {
            return builtinBindings.get(declaration);
        }
        public boolean isNative(SolvikType type) {
            return type != null && type.kind() == SolvikType.Kind.USER
                    && nativeDeclarations.containsKey(type.userName());
        }

        public SolvikType typeOf(Ast.Expr expression) {
            return expressionTypes.get(expression);
        }

        public SolvikType typeOf(Ast.VarStmt declaration) {
            return variableTypes.get(declaration);
        }

        public SolvikType typeOf(Ast.FieldDecl field) {
            return fieldTypes.get(field);
        }

        /**
         * The binding a name resolves to, which tells code generation which Java local to use
         * (§3, "Scope and shadowing"). {@code null} for a name the checker did not bind.
         */
        public Binding bindingOf(Ast.Node node) {
            return bindings.get(node);
        }

        /**
         * The member a bare name or bare call means (§3, "Member names"), or {@code null} when the
         * reference names a local, a parameter, or a function.
         */
        public String implicitMemberOf(Ast.Node node) {
            return implicitMembers.get(node);
        }

        /**
         * The function a call names, as its qualified name (§2, "Modules").
         *
         * <p>{@code area} is a top-level function and {@code geometry::area} is that module's;
         * returns {@code null} for a construction or a method call.
         */
        public String functionTargetOf(Ast.Node node) {
            return functionCalls.get(node);
        }

        /** Whether this call node constructs a user type, so the emitter writes {@code new} (§11). */
        public boolean isConstruction(Ast.Node node) {
            return constructions.contains(node);
        }
    }

    /** A concrete method that forwards to the sole trait default for a shared signature. */
    public record DefaultBridge(String traitName, Ast.FuncDecl method,
                                List<SolvikType> parameterTypes, SolvikType returnType) { }

    /**
     * One explicitly selected trait default: the owning trait and the resolved overload.
     *
     * <p>Recording the declaration rather than only the trait is what keeps emission faithful:
     * the call site names the bridge that holds this overload's body, so javac cannot apply its own
     * overload resolution to boxed numerics, {@code Any} parameters, or {@code null}.
     */
    public record TraitDefaultCall(String traitName, Ast.FuncDecl method) { }

    /**
     * A user-defined type: its fields, its methods, and its constructor if declared (§2).
     *
     * @param qualifiedName the identity used everywhere else: {@code Point} outside a module and
     *                      {@code geometry::Point} inside one (§2, "Modules")
     */
    private record TypeInfo(String qualifiedName, Ast.TypeDecl declaration,
                            Map<String, SolvikType> fields, Map<String, List<Ast.FuncDecl>> methods,
                            List<String> parameters, Map<String, Ast.FieldDecl> declared) {

        /** Whether the field was published with {@code pub} (§2, "Visibility"). */
        boolean isPublic(String field) {
            Ast.FieldDecl declaration = declared.get(field);
            return declaration != null && declaration.isPublic();
        }

        /** Whether the field was opened with {@code mut} (§2, "Mutability"). */
        boolean isMutable(String field) {
            Ast.FieldDecl declaration = declared.get(field);
            return declaration != null && declaration.isMutable();
        }

        /** Whether the field belongs to the type rather than to one instance (§3). */
        boolean isStaticField(String field) {
            Ast.FieldDecl declaration = declared.get(field);
            return declaration != null && declaration.isStatic();
        }

        /** Whether the method belongs to the type rather than to one instance (§3). */
        boolean isStaticMethod(String method) {
            List<Ast.FuncDecl> declarations = methods.get(method);
            return declarations != null && declarations.stream().allMatch(Ast.FuncDecl::isStatic);
        }

        /** The type as written at a use site, with arguments substituted for its parameters. */
        SolvikType typeFor(List<SolvikType> arguments) {
            return arguments.isEmpty()
                    ? SolvikType.user(qualifiedName)
                    : SolvikType.user(qualifiedName, arguments);
        }
    }

    // ------------------------------------------------------------------ driver

    private void run(Ast.Program program) {
        scope = new Scope(null, ordinals);
        ordinals.clear(); // top-level statements share one Java method
        List<DeclaredType> declared = declare(program);
        List<DeclaredFunction> declaredFunctions = declareFunctions(program);
        if (!checkNamespace(declared, declaredFunctions)) return;
        nameTraitDefaultBridges(declared);
        for (DeclaredFunction entry : declaredFunctions) {
            functions.computeIfAbsent(entry.qualifiedName(), ignored -> new ArrayList<>())
                    .add(entry.declaration());
        }
        for (DeclaredType entry : declared) {
            Ast.TypeDecl type = entry.declaration();
            // A type name must begin with an uppercase letter (§2, "User-defined types").
            if (type.name().isEmpty() || !Character.isUpperCase(type.name().charAt(0))) {
                error(INVALID_TYPE_NAME, type.pos(),
                        "a type name must start with an uppercase letter, found '" + type.name() + "'");
            }
            userTypes.put(entry.qualifiedName(), null); // reserve before fields are resolved
        }
        for (DeclaredType entry : declared) {
            currentModule = entry.module();
            userTypes.put(entry.qualifiedName(), buildTypeInfo(entry.declaration(), entry.qualifiedName()));
        }
        resolveImplementedTraits(declared);
        validateFunctionOverloads(declaredFunctions);
        validateTraitContracts(declared);
        for (DeclaredType entry : declared) {
            TypeInfo info = userTypes.get(entry.qualifiedName());
            if (info != null) {
                currentModule = entry.module();
                checkMethods(info);
            }
        }
        currentModule = null;
        for (DeclaredFunction entry : declaredFunctions) {
            currentModule = entry.module();
            checkFunction(entry.declaration(), false);
        }
        currentModule = null;
        checkPublicSurface(declared, declaredFunctions);
        currentModule = null;
        checkStatements(program.statements());
    }

    /** A type declaration together with the module that holds it ({@code null} at top level). */
    private record DeclaredType(Ast.TypeDecl declaration, String module) {

        String qualifiedName() {
            return TypeChecker.qualifiedName(module, declaration.name());
        }
    }

    /** {@code Point} outside a module, {@code geometry::Point} inside one (§2, "Modules"). */
    private static String qualifiedName(String module, String name) {
        return module == null ? name : module + "::" + name;
    }

    /** A function declaration together with the module that holds it ({@code null} at top level). */
    private record DeclaredFunction(Ast.FuncDecl declaration, String module) {

        String qualifiedName() {
            return TypeChecker.qualifiedName(module, declaration.name());
        }
    }

    private record NamespaceDeclaration(String kind, SourcePos pos) { }

    /** Validate the logical union before any definition can overwrite metadata or generate Java. */
    private boolean checkNamespace(List<DeclaredType> types, List<DeclaredFunction> functions) {
        Map<String, NamespaceDeclaration> names = new LinkedHashMap<>();
        // Repeated module/module blocks have already coalesced by full path. Every other repeat
        // is a conflict, including a type or function with the name of a nested module.
        declaredModules.forEach((path, pos) -> names.put(path, new NamespaceDeclaration("module", pos)));
        boolean valid = true;
        for (DeclaredType type : types) {
            valid &= checkBuiltInTypeName(type.declaration());
            valid &= declareName(names, type.qualifiedName(), "type", type.declaration().pos());
        }
        for (DeclaredFunction function : functions) {
            valid &= checkTopLevelBuiltInName(function);
            NamespaceDeclaration first = names.get(function.qualifiedName());
            if (first == null) {
                names.put(function.qualifiedName(),
                        new NamespaceDeclaration("function", function.declaration().pos()));
            } else if (!"function".equals(first.kind())) {
                valid &= declareName(names, function.qualifiedName(), "function",
                        function.declaration().pos());
            }
        }
        return valid;
    }

    /**
     * Rejects a declared type that takes a built-in type's name (§2.3).
     *
     * <p>{@code Byte}, {@code Short}, {@code Integer}, {@code Long}, {@code Float}, {@code Double},
     * {@code BigInteger}, {@code BigDecimal}, {@code String}, {@code Boolean}, {@code Any}, and
     * {@code Number} name fixed language types with fixed Java representations. A declaration with
     * one of those names could never be referred to, because a written type of that name always
     * means the built-in one, so it is a conflict in the shared namespace (§10) rather than an
     * alternative definition.
     */
    private boolean checkBuiltInTypeName(Ast.TypeDecl type) {
        if (SolvikType.named(type.name()) == null) {
            return true;
        }
        error(TYPE_ERROR, type.pos(), "'" + type.name() + "' is a built-in type; a declaration cannot"
                + " take its name (§2.3)");
        return false;
    }

    /**
     * Rejects a top-level function that takes a built-in function's or conversion's name (§4.4,
     * §4.6, §7.3).
     *
     * <p>A top-level function shares its Java holder with the language's built-in calls, and every
     * call written with such a name resolves to the built-in one, so the declaration could never be
     * called. A module function or a method is qualified and keeps its name.
     */
    private boolean checkTopLevelBuiltInName(DeclaredFunction function) {
        if (function.module() != null) {
            return true;
        }
        String name = function.declaration().name();
        if (SolvikType.named(name) != null) {
            error(TYPE_ERROR, function.declaration().pos(), "'" + name
                    + "' is a built-in type and conversion; a top-level function cannot take its"
                    + " name (§4.4)");
            return false;
        }
        if (BUILT_IN_FUNCTIONS.contains(name)) {
            error(TYPE_ERROR, function.declaration().pos(), "'" + name
                    + "' is a built-in function; a top-level function cannot take its name"
                    + " (§4.6, §7.3)");
            return false;
        }
        return true;
    }

    private boolean declareName(Map<String, NamespaceDeclaration> names, String name,
            String kind, SourcePos pos) {
        NamespaceDeclaration first = names.putIfAbsent(name, new NamespaceDeclaration(kind, pos));
        if (first == null) return true;
        SourcePos original = first.pos();
        String file = original.file() == null ? "<source>" : original.file();
        error(TYPE_ERROR, pos, kind + " '" + name + "' conflicts with " + first.kind()
                + " declared at " + file + ":" + original.line() + ":" + original.column());
        return false;
    }

    /**
     * Flattens the program's type declarations, validating each module as it is met.
     *
     * <p>Blocks with the same name contribute their types to the same module, so the qualified name
     * is the only key (§2, "Modules").
     */
    private List<DeclaredType> declare(Ast.Program program) {
        List<DeclaredType> declared = new ArrayList<>();
        for (Ast.TypeDecl type : program.types()) {
            declared.add(new DeclaredType(type, null));
        }
        for (Ast.ModuleDecl module : program.modules()) {
            declareModule(module, "", declared);
        }
        return declared;
    }

    /** Walks a module tree, flattening types under their full {@code a::b} path. */
    private void declareModule(Ast.ModuleDecl module, String parent, List<DeclaredType> declared) {
        String path = parent.isEmpty() ? module.name() : parent + "::" + module.name();
        if ("solvik".equals(path) && !standardLibraryModules.contains(module)) {
            error(TYPE_ERROR, module.pos(),
                    "module name 'solvik' is reserved for the standard library and generated Java code");
        }
        declaredModules.putIfAbsent(path, module.pos());
        for (Ast.TypeDecl type : module.types()) {
            declared.add(new DeclaredType(type, path));
        }
        for (Ast.ModuleDecl nested : module.modules()) {
            declareModule(nested, path, declared);
        }
    }

    /**
     * Flattens the program's function declarations, global ones first (§2, "Modules").
     *
     * <p>Blocks with the same module name contribute their functions to that module, so the
     * qualified name is the key, exactly as it is for types.
     */
    private List<DeclaredFunction> declareFunctions(Ast.Program program) {
        List<DeclaredFunction> declared = new ArrayList<>();
        for (Ast.FuncDecl function : program.functions()) {
            declared.add(new DeclaredFunction(function, null));
        }
        for (Ast.ModuleDecl module : program.modules()) {
            declareModuleFunctions(module, "", declared);
        }
        return declared;
    }

    /** Walks a module tree, flattening functions under their full {@code a::b} path. */
    private static void declareModuleFunctions(Ast.ModuleDecl module, String parent,
            List<DeclaredFunction> declared) {
        String path = parent.isEmpty() ? module.name() : parent + "::" + module.name();
        for (Ast.FuncDecl function : module.functions()) {
            declared.add(new DeclaredFunction(function, path));
        }
        for (Ast.ModuleDecl nested : module.modules()) {
            declareModuleFunctions(nested, path, declared);
        }
    }

    /**
     * Checks that every {@code pub} declaration exposes only types visible outside its module
     * (§2, "Modules").
     *
     * <p>Runs after all bodies are checked, because it reads the resolved field and return types.
     * A private type may be used freely *inside* a {@code pub} declaration's body; what it may not
     * do is appear in the signature an outside caller sees.
     */
    private void checkPublicSurface(List<DeclaredType> types, List<DeclaredFunction> functions) {
        for (DeclaredType entry : types) {
            TypeInfo info = userTypes.get(entry.qualifiedName());
            if (info == null || info.declaration() == null || !info.declaration().isPublic()) {
                continue;
            }
            currentModule = entry.module();
            for (Ast.FieldDecl field : info.declaration().fields()) {
                if (!field.isPublic()) {
                    continue;
                }
                requireExposedTypes(fieldTypes.get(field), field.pos(),
                        "pub field '" + entry.qualifiedName() + "." + field.name() + "'");
            }
            List<String> outerParameters = typeParameters;
            typeParameters = info.parameters();   // a generic type's signature may name its T
            for (Ast.FuncDecl method : info.declaration().methods()) {
                if (!method.isPublic()) {
                    continue;
                }
                String what = "pub function '" + entry.qualifiedName() + "." + method.name() + "'";
                for (Ast.Param param : method.params()) {
                    requireExposedTypes(resolveType(param.type()), param.pos(), what);
                }
                requireExposedTypes(returnTypes.get(method), method.pos(), what);
            }
            typeParameters = outerParameters;
        }
        for (DeclaredFunction entry : functions) {
            if (!entry.declaration().isPublic()) {
                continue;
            }
            currentModule = entry.module();
            String what = "pub function '" + entry.qualifiedName() + "'";
            for (Ast.Param param : entry.declaration().params()) {
                requireExposedTypes(resolveType(param.type()), param.pos(), what);
            }
            requireExposedTypes(returnTypes.get(entry.declaration()), entry.declaration().pos(), what);
        }
    }

    /** Collects a type's fields and methods. Fields need an initializer so their type is known. */
    private TypeInfo buildTypeInfo(Ast.TypeDecl type, String qualifiedName) {
        Map<String, SolvikType> fields = new LinkedHashMap<>();
        Scope outer = scope;
        List<String> outerParameters = typeParameters;
        String outerTypeName = currentTypeName;
        currentTypeName = qualifiedName;
        typeParameters = type.typeParameters();
        scope = new Scope(outer, ordinals);
        Map<String, Ast.FieldDecl> declared = new LinkedHashMap<>();
        for (Ast.FieldDecl field : type.fields()) {
            declared.put(field.name(), field);
        }
        Map<String, List<Ast.FuncDecl>> provisionalMethods = new LinkedHashMap<>();
        for (Ast.FuncDecl method : type.methods()) {
            methodOwners.put(method, qualifiedName);
            provisionalMethods.computeIfAbsent(method.name(), ignored -> new ArrayList<>()).add(method);
        }
        // A static field initializer may read an earlier static field or call a static function of
        // the same type. The provisional info exposes the declarations collected so far without
        // pretending the completed field list or checked method bodies are already known.
        userTypes.put(qualifiedName, new TypeInfo(qualifiedName, type, fields, provisionalMethods,
                type.typeParameters(), declared));
        for (Ast.FieldDecl field : type.fields()) {
            if (fields.containsKey(field.name())) {
                error(TYPE_ERROR, field.pos(), "field '" + field.name() + "' is already declared");
                continue;
            }
            boolean previousStatic = staticContext;
            List<String> previousUnavailable = unavailableTypeParameters;
            if (field.isStatic()) {
                // A Java static initializer has no instance and cannot name the class type
                // parameters, so a static field initializer is checked in that same context.
                staticContext = true;
                typeParameters = List.of();
                unavailableTypeParameters = type.typeParameters();
            }
            // A field whose initializer the parser could not find is already reported; its
            // declared type still names the field for the rest of the body.
            SolvikType fieldType = field.initializer() == null
                    ? (field.type() == null ? SolvikType.ANY : resolveType(field.type()))
                    : bindingType(field.type(), field.nullableInferred(),
                            checkInitializer(field.initializer()), field.pos(),
                            "field '" + field.name() + "'");
            staticContext = previousStatic;
            unavailableTypeParameters = previousUnavailable;
            typeParameters = type.typeParameters();
            fields.put(field.name(), fieldType);
            fieldTypes.put(field, fieldType);
        }
        scope = outer;
        currentTypeName = outerTypeName;

        Map<String, List<Ast.FuncDecl>> methods = new LinkedHashMap<>();
        for (Ast.FuncDecl method : type.methods()) {
            if (method.isConstructor(type.name())) {
                if (method.returnType() != null) {
                    error(TYPE_ERROR, method.pos(), "a constructor declares no return type");
                }
                if (method.isStatic()) {
                    error(TYPE_ERROR, method.pos(),
                            "a constructor cannot be 'static'; it initializes one instance");
                }
            } else if (resolveUserName(method.name()) != null) {
                error(TYPE_ERROR, method.pos(),
                        "a function's name may not match a type name (§3)");
            }
            checkContractOverride(type, method);
            methods.computeIfAbsent(method.name(), ignored -> new ArrayList<>()).add(method);
        }
        validateMethodOverloads(type, methods);
        typeParameters = outerParameters;
        TypeInfo info = new TypeInfo(qualifiedName, type, fields, methods, type.typeParameters(),
                declared);
        userTypes.put(qualifiedName, info);
        return info;
    }

    /**
     * A method that overrides the {@code Any} contract must be public (§8).
     *
     * <p>{@code toString}, {@code equals}, and {@code hashCode} are part of every value's
     * interface, so a private override could not be called; {@code type()} is final and may not be
     * overridden at all.
     */
    private void checkContractOverride(Ast.TypeDecl type, Ast.FuncDecl method) {
        if (method.isConstructor(type.name())) {
            return;
        }
        if (method.isStatic()) {
            return; // a static function belongs to the type, not to its Any instance contract
        }
        if ("type".equals(method.name())) {
            error(TYPE_ERROR, method.pos(),
                    "'type()' is final and cannot be declared (§8)");
            return;
        }
        boolean contract = "toString".equals(method.name()) || "equals".equals(method.name())
                || "hashCode".equals(method.name());
        if (contract && !method.isPublic()) {
            error(TYPE_ERROR, method.pos(), "'" + method.name()
                    + "' overrides the Any contract; declare it 'pub func' (§8)");
        }
        if (contract) {
            SolvikType expected = switch (method.name()) {
                case "equals" -> SolvikType.BOOLEAN;
                case "hashCode" -> SolvikType.INTEGER;
                default -> SolvikType.STRING;
            };
            boolean parametersMatch = "equals".equals(method.name())
                    ? method.params().size() == 1
                            && resolveType(method.params().getFirst().type())
                                    .equals(SolvikType.ANY.asNullable())
                    : method.params().isEmpty();
            if (!parametersMatch || method.returnType() == null
                    || !expected.equals(resolveType(method.returnType()))) {
                error(TYPE_ERROR, method.pos(), "'" + method.name() + "' must have signature "
                        + method.name() + ("equals".equals(method.name()) ? "(other: Any?)" : "()")
                        + " -> " + expected);
            }
        }
    }

    /** Checks every method body with {@code self} bound to the declaring type. */
    private void checkMethods(TypeInfo info) {
        String outerTypeName = currentTypeName;
        currentTypeName = info.qualifiedName();
        if (info.declaration().staticInitializers().size() > 1) {
            for (int index = 1; index < info.declaration().staticInitializers().size(); index++) {
                error(TYPE_ERROR, info.declaration().staticInitializers().get(index).pos(),
                        "a type declares at most one static initializer block");
            }
        }
        if (info.declaration().nativeClass() != null) {
            for (Ast.StaticInitializer initializer : info.declaration().staticInitializers()) {
                error(TYPE_ERROR, initializer.pos(),
                        "a native type binding cannot declare a static initializer");
            }
            List<String> previous = typeParameters;
            List<String> previousUnavailable = unavailableTypeParameters;
            boolean previousStatic = staticContext;
            for (Ast.FuncDecl method : info.methods().values().stream().flatMap(List::stream).toList()) {
                staticContext = method.isStatic();
                typeParameters = method.isStatic() ? List.of() : info.parameters();
                unavailableTypeParameters = method.isStatic() ? info.parameters() : List.of();
                returnTypes.put(method, method.returnType() == null ? SolvikType.VOID
                        : resolveType(method.returnType()));
                if (method.isNative()) {
                    error(TYPE_ERROR, method.pos(), "a native class binding declares its members by"
                            + " name; the 'native' modifier is for bodyless built-in declarations");
                }
            }
            staticContext = previousStatic;
            typeParameters = info.parameters();
            unavailableTypeParameters = List.of();
            JavaBinding binding = JavaBinding.resolve(info.declaration(), this::resolveType,
                    this::resolveNativeJavaClass, nativeLoader, diagnostics);
            if (binding != null) nativeBindings.put(info.declaration(), binding);
            if (info.declaration().isTrait()) {
                for (Ast.FuncDecl method : info.declaration().methods()) {
                    if (method.hasBody()) checkTraitDefaultBody(info, method);
                }
            }
            typeParameters = previous;
            unavailableTypeParameters = previousUnavailable;
            currentTypeName = outerTypeName;
            return;
        }
        List<Ast.FuncDecl> ordered = info.methods().values().stream()
                .flatMap(List::stream).collect(Collectors.toCollection(ArrayList::new));
        // The constructor runs first, so construction-time writes are checked before any method
        // reads a field.
        ordered.sort(Comparator.comparing(
                method -> method.isConstructor(info.declaration().name()) ? 0 : 1));
        for (Ast.FuncDecl method : ordered) {
            List<String> outerParameters = typeParameters;
            List<String> outerUnavailable = unavailableTypeParameters;
            boolean isStatic = method.isStatic();
            staticContext = isStatic;
            typeParameters = isStatic ? List.of() : info.parameters();
            unavailableTypeParameters = isStatic ? info.parameters() : List.of();
            SolvikType returnType = method.returnType() == null
                    ? SolvikType.VOID
                    : resolveType(method.returnType());
            returnTypes.put(method, returnType);
            if (method.isNative()) {
                checkNativeDeclaration(info, method);
                staticContext = false;
                typeParameters = outerParameters;
                unavailableTypeParameters = outerUnavailable;
                continue;
            }
            Scope outer = scope;
            currentReturnType = returnType;
            inFunction = true;
            inConstructor = method.isConstructor(info.declaration().name());
            ordinals.clear(); // every method becomes its own Java method
            scope = new Scope(outer, ordinals);
            SolvikType selfType = info.typeFor(info.parameters().stream()
                    .map(SolvikType::variable).toList());
            if (!isStatic) {
                scope.declareSelf(selfType);
            }
            for (Ast.Param param : method.params()) {
                if (isCheckedEqualityOverride(method) && param == method.params().getFirst()) {
                    scope.declare(param.name(), selfType, EQUALS_PEER_JAVA_NAME);
                } else {
                    scope.declare(param.name(), resolveType(param.type()));
                }
            }
            checkStatements(method.body());
            scope = outer;
            currentReturnType = SolvikType.VOID;
            inFunction = false;
            inConstructor = false;
            staticContext = false;
            typeParameters = outerParameters;
            unavailableTypeParameters = outerUnavailable;
        }
        for (int index = 0; index < info.declaration().staticInitializers().size(); index++) {
            Ast.StaticInitializer initializer = info.declaration().staticInitializers().get(index);
            if (info.declaration().isTrait()) {
                error(TYPE_ERROR, initializer.pos(),
                        "a trait cannot declare a static initializer");
                continue;
            }
            if (index == 0) {
                checkStaticInitializer(info, initializer);
            }
        }
        currentTypeName = outerTypeName;
    }

    /**
     * Checks a concrete type's {@code static { ... }} block in static context (§3.4).
     *
     * <p>The block has no {@code self} and no enclosing type parameters. It is checked after the
     * type's methods so calls resolve against every declared static signature; runtime evaluation
     * still follows the source order preserved by lowering.
     */
    private void checkStaticInitializer(TypeInfo info, Ast.StaticInitializer initializer) {
        Scope outerScope = scope;
        SolvikType outerReturnType = currentReturnType;
        boolean outerInFunction = inFunction;
        boolean outerInConstructor = inConstructor;
        boolean outerStatic = staticContext;
        boolean outerInStaticInitializer = inStaticInitializer;
        List<String> outerParameters = typeParameters;
        List<String> outerUnavailable = unavailableTypeParameters;
        int outerLoopDepth = loopDepth;
        int outerFinallyDepth = finallyDepth;
        staticContext = true;
        typeParameters = List.of();
        unavailableTypeParameters = info.parameters();
        currentReturnType = SolvikType.VOID;
        inFunction = true;
        inConstructor = false;
        inStaticInitializer = true;
        loopDepth = 0;
        finallyDepth = 0;
        ordinals.clear();
        scope = new Scope(outerScope, ordinals);
        checkStatements(initializer.body());
        scope = outerScope;
        currentReturnType = outerReturnType;
        inFunction = outerInFunction;
        inConstructor = outerInConstructor;
        staticContext = outerStatic;
        inStaticInitializer = outerInStaticInitializer;
        typeParameters = outerParameters;
        unavailableTypeParameters = outerUnavailable;
        loopDepth = outerLoopDepth;
        finallyDepth = outerFinallyDepth;
    }

    /**
     * Resolves a bodyless {@code native} member against the compiler's binding registry.
     *
     * <p>Trust is provenance: only a type parsed from a compiler-packaged resource may declare one.
     * A declaration whose signature has no exact registry entry is a build error, never a stub or a
     * reflective guess ({@code plans/SOLVIK_IO_DESIGN.md} §6.4).
     */
    private void checkNativeDeclaration(TypeInfo info, Ast.FuncDecl method) {
        if (info.declaration().isTrait()) {
            error(TYPE_ERROR, method.pos(), "a trait member is already bodyless; a 'native'"
                    + " declaration belongs to a native-backed type");
            return;
        }
        if (!trustedTypes.contains(info.declaration())) {
            error(TYPE_ERROR, method.pos(), "'native' declares a compiler-bundled binding; only a"
                    + " packaged standard-library type may use it");
            return;
        }
        List<SolvikType> parameters = new ArrayList<>();
        for (Ast.Param parameter : method.params()) {
            parameters.add(resolveType(parameter.type()));
        }
        BuiltinBindings.Entry entry = BuiltinBindings.resolve(info.declaration().name(),
                method.name(), method.isStatic(), parameters);
        if (entry == null) {
            error(JavaBinding.INVALID_BINDING, method.pos(), "native member '"
                    + info.declaration().name() + (method.isStatic() ? "::" : "#") + method.name()
                    + "(" + parameters.stream().map(SolvikType::toString)
                            .collect(Collectors.joining(", "))
                    + ")' has no entry in the compiler's binding registry");
            return;
        }
        SolvikType declaredResult = returnTypes.get(method);
        String writtenResult = declaredResult == null || declaredResult == SolvikType.VOID
                ? "Void" : declaredResult.toString();
        if (!entry.resultType().equals(writtenResult)) {
            error(JavaBinding.INVALID_BINDING, method.pos(), "native member '" + method.name()
                    + "' declares '" + writtenResult + "' but its binding returns '"
                    + entry.resultType() + "'");
            return;
        }
        builtinBindings.put(method, entry);
    }

    /** Checks a source-provided default on a native trait; bodyless members remain Java bindings. */
    private void checkTraitDefaultBody(TypeInfo info, Ast.FuncDecl method) {
        Scope outerScope = scope;
        SolvikType outerReturnType = currentReturnType;
        boolean outerInFunction = inFunction;
        boolean outerStatic = staticContext;
        List<String> outerParameters = typeParameters;
        List<String> outerUnavailable = unavailableTypeParameters;
        staticContext = false;
        typeParameters = info.parameters();
        unavailableTypeParameters = List.of();
        currentReturnType = method.returnType() == null
                ? SolvikType.VOID : resolveType(method.returnType());
        inFunction = true;
        ordinals.clear();
        scope = new Scope(outerScope, ordinals);
        scope.declareSelf(info.typeFor(info.parameters().stream()
                .map(SolvikType::variable).toList()));
        for (Ast.Param param : method.params()) {
            scope.declare(param.name(), resolveType(param.type()));
        }
        checkStatements(method.body());
        scope = outerScope;
        currentReturnType = outerReturnType;
        inFunction = outerInFunction;
        staticContext = outerStatic;
        typeParameters = outerParameters;
        unavailableTypeParameters = outerUnavailable;
    }

    /** Whether a user method is the valid Object equality contract that receives a checked peer. */
    private boolean isCheckedEqualityOverride(Ast.FuncDecl method) {
        return !method.isStatic() && "equals".equals(method.name())
                && method.params().size() == 1
                && resolveType(method.params().getFirst().type())
                        .equals(SolvikType.ANY.asNullable())
                && method.returnType() != null
                && resolveType(method.returnType()) == SolvikType.BOOLEAN;
    }

    private void checkFunction(Ast.FuncDecl function, boolean method) {
        if (method) {
            error(UNSUPPORTED, function.pos(), "methods are not yet emitted by this compiler");
            return;
        }
        if (function.isNative()) {
            error(TYPE_ERROR, function.pos(), "a 'native' declaration belongs to a bundled type;"
                    + " a top-level function has no binding surface");
            return;
        }
        SolvikType returnType = function.returnType() == null
                ? SolvikType.VOID
                : resolveType(function.returnType());
        returnTypes.put(function, returnType);
        Scope outer = scope;
        currentReturnType = returnType;
        inFunction = true;
        ordinals.clear(); // every function becomes its own Java method
        scope = new Scope(outer, ordinals);
        for (Ast.Param param : function.params()) {
            scope.declare(param.name(), resolveType(param.type()));
        }
        checkStatements(function.body());
        scope = outer;
        currentReturnType = SolvikType.VOID;
        inFunction = false;
    }

    /** A declared native Solvik name for an exact Java class/interface used in another binding. */
    private SolvikType resolveNativeJavaClass(Class<?> javaClass) {
        String target = javaClass.getCanonicalName();
        for (Map.Entry<String, TypeInfo> entry : userTypes.entrySet()) {
            Ast.TypeDecl declaration = entry.getValue() == null ? null : entry.getValue().declaration();
            if (declaration != null && target.equals(declaration.nativeClass())) {
                return SolvikType.user(entry.getKey());
            }
        }
        return null;
    }

    /** Resolves and validates every implemented-trait reference after all declarations exist. */
    private void resolveImplementedTraits(List<DeclaredType> declared) {
        for (DeclaredType entry : declared) {
            Ast.TypeDecl declaration = entry.declaration();
            currentModule = entry.module();
            if (declaration.isTrait()) {
                if (!declaration.typeParameters().isEmpty()) {
                    error(TYPE_ERROR, declaration.pos(), "traits cannot declare type parameters");
                }
                if (!declaration.implementedTraits().isEmpty()) {
                    error(TYPE_ERROR, declaration.pos(), "trait inheritance is not supported");
                }
                if (!declaration.fields().isEmpty()) {
                    error(TYPE_ERROR, declaration.pos(), "traits cannot declare fields");
                }
                for (Ast.FuncDecl method : declaration.methods()) {
                    if (!method.isPublic() || method.isStatic()
                            || method.isConstructor(declaration.name())) {
                        error(TYPE_ERROR, method.pos(),
                                "a trait declares only public instance functions");
                    }
                    if (method.hasBody() && method.nativeName() != null) {
                        error(TYPE_ERROR, method.pos(),
                                "a default trait function has a Solvik body and cannot be native");
                    }
                    if (method.nativeName() != null && declaration.nativeClass() == null) {
                        error(TYPE_ERROR, method.pos(),
                                "a native function name is valid only in a native trait");
                    }
                    if (method.hasBody() && ("type".equals(method.name())
                            || "toString".equals(method.name())
                            || "equals".equals(method.name())
                            || "hashCode".equals(method.name()))) {
                        error(TYPE_ERROR, method.pos(), "trait function '" + method.name()
                                + "' is part of the Any/Object contract and cannot have a default body");
                    }
                }
            } else if (declaration.nativeClass() != null
                    && !declaration.implementedTraits().isEmpty()) {
                error(TYPE_ERROR, declaration.pos(),
                        "native class bindings cannot implement Solvik traits");
            }
            List<String> contracts = new ArrayList<>();
            for (Ast.TypeRef written : declaration.implementedTraits()) {
                SolvikType resolved = resolveType(written);
                TypeInfo trait = resolved.kind() == SolvikType.Kind.USER
                        ? userTypes.get(resolved.userName()) : null;
                if (trait == null || !trait.declaration().isTrait()) {
                    error(TYPE_ERROR, written.pos(), "'" + written + "' is not a trait");
                } else if (!contracts.contains(resolved.userName())) {
                    contracts.add(resolved.userName());
                }
            }
            implementedTraits.put(entry.qualifiedName(), List.copyOf(contracts));
        }
        currentModule = null;
    }

    private record MethodSignature(String name, List<SolvikType> parameters) { }

    /**
     * Names the static bridge that holds each trait default's one body.
     *
     * <p>A bridge is named after its function and its declaration-order ordinal among same-named
     * defaults, so overloads get distinct Java methods and the checked call site can name exactly
     * the overload it selected. The name is compiler-owned: {@code $} cannot appear in a Solvik
     * identifier, so it can never collide with a trait member.
     */
    private void nameTraitDefaultBridges(List<DeclaredType> declared) {
        for (DeclaredType entry : declared) {
            Ast.TypeDecl declaration = entry.declaration();
            if (!declaration.isTrait()) continue;
            Map<String, Integer> ordinals = new LinkedHashMap<>();
            for (Ast.FuncDecl method : declaration.methods()) {
                if (!method.hasBody()) continue;
                int ordinal = ordinals.merge(method.name(), 1, Integer::sum) - 1;
                traitDefaultBridges.put(method,
                        "$default$" + method.name() + "$" + ordinal);
            }
        }
    }

    private record TraitContribution(String traitName, Ast.FuncDecl method,
                                     List<SolvikType> parameters, SolvikType returnType) { }

    /**
     * Validates concrete contracts as one resolved set, recording Java bridges where a sole
     * default also satisfies abstract declarations from unrelated traits.
     */
    private void validateTraitContracts(List<DeclaredType> declared) {
        for (DeclaredType entry : declared) {
            Ast.TypeDecl declaration = entry.declaration();
            if (declaration.isTrait()) continue;
            TypeInfo implementation = userTypes.get(entry.qualifiedName());
            Map<MethodSignature, List<TraitContribution>> contracts = new LinkedHashMap<>();
            for (String traitName : implementedTraits.getOrDefault(entry.qualifiedName(), List.of())) {
                TypeInfo trait = userTypes.get(traitName);
                if (trait == null) continue;
                for (Ast.FuncDecl method : trait.declaration().methods()) {
                    List<SolvikType> parameters = parameterTypes(method, moduleOf(traitName),
                            trait.parameters());
                    SolvikType returned = declaredReturnType(method, moduleOf(traitName),
                            trait.parameters());
                    MethodSignature signature = new MethodSignature(method.name(), parameters);
                    contracts.computeIfAbsent(signature, ignored -> new ArrayList<>())
                            .add(new TraitContribution(traitName, method, parameters, returned));
                }
            }
            List<DefaultBridge> bridges = new ArrayList<>();
            for (Map.Entry<MethodSignature, List<TraitContribution>> contract : contracts.entrySet()) {
                MethodSignature signature = contract.getKey();
                List<TraitContribution> contributions = contract.getValue();
                List<Ast.FuncDecl> concrete = implementation.methods()
                        .getOrDefault(signature.name(), List.of()).stream()
                        .filter(candidate -> methodSignature(candidate).equals(signature)).toList();
                if (!concrete.isEmpty()) {
                    Ast.FuncDecl match = concrete.getFirst();
                    if (match.isStatic() || !match.isPublic()) {
                        error(TYPE_ERROR, match.pos(), "method '" + signature(match)
                                + "' implementing a trait must be a public instance function");
                        continue;
                    }
                    SolvikType returned = methodReturnType(match);
                    for (TraitContribution contribution : contributions) {
                        if (!returned.equals(contribution.returnType())) {
                            error(TYPE_ERROR, match.pos(), "method '" + signature(match)
                                    + "' returns " + returned + " but trait "
                                    + contribution.traitName() + " requires "
                                    + contribution.returnType());
                        }
                    }
                    continue;
                }
                SolvikType returned = contributions.getFirst().returnType();
                boolean compatibleReturns = contributions.stream()
                        .allMatch(contribution -> returned.equals(contribution.returnType()));
                if (!compatibleReturns) {
                    error(TYPE_ERROR, declaration.pos(), "type '" + entry.qualifiedName()
                            + "' implements traits with incompatible returns for "
                            + displaySignature(signature));
                    continue;
                }
                List<TraitContribution> defaults = contributions.stream()
                        .filter(contribution -> contribution.method().hasBody()).toList();
                if (defaults.isEmpty()) {
                    TraitContribution required = contributions.getFirst();
                    error(TYPE_ERROR, declaration.pos(), "type '" + entry.qualifiedName()
                            + "' does not implement " + required.traitName() + "."
                            + signature(required.method()));
                } else if (defaults.size() > 1) {
                    error(TYPE_ERROR, declaration.pos(), "type '" + entry.qualifiedName()
                            + "' inherits conflicting defaults for " + displaySignature(signature)
                            + "; declare a public override");
                } else if (contributions.size() > 1) {
                    TraitContribution selected = defaults.getFirst();
                    bridges.add(new DefaultBridge(selected.traitName(), selected.method(),
                            List.copyOf(selected.parameters()), selected.returnType()));
                }
            }
            defaultBridges.put(entry.qualifiedName(), List.copyOf(bridges));
        }
    }

    private MethodSignature methodSignature(Ast.FuncDecl method) {
        String owner = methodOwners.get(method);
        TypeInfo info = owner == null ? null : userTypes.get(owner);
        return new MethodSignature(method.name(), parameterTypes(method, moduleOf(owner),
                info == null || method.isStatic() ? List.of() : info.parameters()));
    }

    private SolvikType methodReturnType(Ast.FuncDecl method) {
        String owner = methodOwners.get(method);
        TypeInfo info = owner == null ? null : userTypes.get(owner);
        return declaredReturnType(method, moduleOf(owner),
                info == null || method.isStatic() ? List.of() : info.parameters());
    }

    private static String displaySignature(MethodSignature signature) {
        return signature.name() + "(" + signature.parameters().stream()
                .map(Object::toString).collect(Collectors.joining(", ")) + ")";
    }

    private SolvikType declaredReturnType(Ast.FuncDecl declaration, String module,
            List<String> parameters) {
        if (declaration.returnType() == null) return SolvikType.VOID;
        return resolveSignatureType(declaration.returnType(), module, parameters);
    }

    private List<SolvikType> parameterTypes(Ast.FuncDecl declaration, String module,
            List<String> parameters) {
        return declaration.params().stream()
                .map(param -> resolveSignatureType(param.type(), module, parameters)).toList();
    }

    private SolvikType resolveSignatureType(Ast.TypeRef written, String module,
            List<String> parameters) {
        String previousModule = currentModule;
        List<String> previousParameters = typeParameters;
        currentModule = module;
        typeParameters = parameters;
        try {
            return resolveType(written);
        } finally {
            currentModule = previousModule;
            typeParameters = previousParameters;
        }
    }

    private void validateFunctionOverloads(List<DeclaredFunction> declared) {
        Map<String, List<DeclaredFunction>> grouped = declared.stream()
                .collect(Collectors.groupingBy(DeclaredFunction::qualifiedName,
                        LinkedHashMap::new, Collectors.toList()));
        for (List<DeclaredFunction> overloads : grouped.values()) {
            validateOverloads(overloads.stream().map(DeclaredFunction::declaration).toList(),
                    overloads.getFirst().module(), List.of(),
                    overloads.getFirst().qualifiedName());
        }
    }

    private void validateMethodOverloads(Ast.TypeDecl owner,
            Map<String, List<Ast.FuncDecl>> methods) {
        for (List<Ast.FuncDecl> overloads : methods.values()) {
            validateOverloads(overloads, currentModule, owner.typeParameters(),
                    qualifiedName(currentModule, owner.name()) + "." + overloads.getFirst().name());
        }
    }

    private void validateOverloads(List<Ast.FuncDecl> overloads, String module,
            List<String> parameters, String displayName) {
        Map<List<SolvikType>, Ast.FuncDecl> signatures = new LinkedHashMap<>();
        Map<List<String>, Ast.FuncDecl> erasures = new LinkedHashMap<>();
        for (Ast.FuncDecl declaration : overloads) {
            List<SolvikType> signature = parameterTypes(declaration, module,
                    declaration.isStatic() ? List.of() : parameters);
            Ast.FuncDecl duplicate = signatures.putIfAbsent(signature, declaration);
            if (duplicate != null) {
                SourcePos original = duplicate.pos();
                String file = original.file() == null ? "<source>" : original.file();
                error(TYPE_ERROR, declaration.pos(), "function '" + displayName
                        + "' conflicts with function declared at " + file + ":"
                        + original.line() + ":" + original.column()
                        + "; duplicate parameter signature " + signature);
                continue;
            }
            List<String> erasure = signature.stream().map(TypeChecker::javaErasure).toList();
            Ast.FuncDecl erased = erasures.putIfAbsent(erasure, declaration);
            if (erased != null) {
                error(TYPE_ERROR, declaration.pos(), "function '" + displayName
                        + "' has the same generated Java erasure " + erasure);
            }
        }
    }

    private static String javaErasure(SolvikType type) {
        // `T` and `T?` share one Java carrier, so they cannot be distinct overloads (§8).
        SolvikType carrier = type.asNonNull();
        return switch (carrier.kind()) {
            case ARRAY -> "java.util.List";
            case VARIABLE, ANY, NULL -> "java.lang.Object";
            case USER -> carrier.userName();
            default -> carrier.toString();
        };
    }

    private static String signature(Ast.FuncDecl declaration) {
        return declaration.name() + "(" + declaration.params().stream()
                .map(param -> param.type().toString()).collect(Collectors.joining(", ")) + ")";
    }

    /** Assignment plus nominal trait implementation. */
    private boolean isAssignable(SolvikType value, SolvikType target) {
        if (value.isAssignableTo(target)) return true;
        if (value.kind() != SolvikType.Kind.USER || target.kind() != SolvikType.Kind.USER) {
            return false;
        }
        return implementsTrait(value.userName(), target.userName(), new HashSet<>());
    }

    private boolean implementsTrait(String type, String target, Set<String> seen) {
        if (!seen.add(type)) return false;
        for (String trait : implementedTraits.getOrDefault(type, List.of())) {
            if (trait.equals(target) || implementsTrait(trait, target, seen)) return true;
        }
        return false;
    }

    /**
     * The qualified spelling of a type declared in a module under this simple name, or
     * {@code null} when no module declares one (§2, "Modules").
     *
     * <p>Used to turn "unknown function" into an actionable message when a module type was named
     * without its module.
     */
    private String moduleOfDeclaredType(String simpleName) {
        for (String module : declaredModules.keySet()) {
            String qualified = qualifiedName(module, simpleName);
            TypeInfo info = userTypes.get(qualified);
            if (info != null && info.declaration() != null && info.declaration().isPublic()) {
                return qualified;
            }
        }
        return null;
    }

    /**
     * The declared function a written name refers to, or {@code null} when nothing matches.
     *
     * <p>The enclosing module is searched first, then the top level, so a module function
     * shadows a global one of the same name inside its own module (§2, "Modules").
     */
    private String resolveFunctionName(String written) {
        if (written.contains("::")) {
            return functions.containsKey(written) ? written : null;
        }
        if (currentModule != null) {
            String scoped = qualifiedName(currentModule, written);
            if (functions.containsKey(scoped)) {
                return scoped;
            }
        }
        return functions.containsKey(written) ? written : null;
    }

    /**
     * Whether a declaration is reachable from the code being checked (§2, "Modules").
     *
     * <p>A top-level declaration has no module and is program-visible. A module declaration is
     * visible everywhere inside its own module and nowhere else unless marked {@code pub}.
     */
    private boolean isVisibleFromHere(String module, boolean isPublic) {
        if (module == null) {
            return true;
        }
        return isPublic || module.equals(currentModule);
    }

    /** Reports a type private to a module the referring code is outside of (§2, "Modules"). */
    private void requireVisibleType(String qualifiedName, SourcePos pos) {
        TypeInfo info = userTypes.get(qualifiedName);
        if (info == null || info.declaration() == null) {
            return;
        }
        String module = moduleOf(qualifiedName);
        if (!isVisibleFromHere(module, info.declaration().isPublic())) {
            error(PRIVATE_DECLARATION, pos, "type '" + qualifiedName + "' is private to module '"
                    + module + "'; declare it 'pub type' to use it outside");
        }
    }

    /** Reports a function private to a module the referring code is outside of (§2, "Modules"). */
    private void requireVisibleFunction(String qualifiedName, SourcePos pos) {
        List<Ast.FuncDecl> overloads = functions.get(qualifiedName);
        if (overloads == null) {
            return;
        }
        String module = moduleOf(qualifiedName);
        if (overloads.stream().noneMatch(function -> isVisibleFromHere(module, function.isPublic()))) {
            error(PRIVATE_DECLARATION, pos, "function '" + qualifiedName + "' is private to module '"
                    + module + "'; declare it 'pub func' to use it outside");
        }
    }

    /**
     * Reports the types of a {@code pub} declaration's visible signature that are not themselves
     * visible outside the module (§2, "Modules").
     *
     * <p>Exposure is what makes an outside caller receive a value it cannot name, so the declaration
     * must expose only {@code pub} types; the walk follows array elements and type arguments.
     */
    private void requireExposedTypes(SolvikType type, SourcePos pos, String exposedBy) {
        if (type == null) {
            return;
        }
        if (type.kind() == SolvikType.Kind.ARRAY) {
            requireExposedTypes(type.element(), pos, exposedBy);
            return;
        }
        if (type.kind() != SolvikType.Kind.USER) {
            return;
        }
        String module = moduleOf(type.userName());
        TypeInfo info = userTypes.get(type.userName());
        if (module != null && info != null && info.declaration() != null
                && !info.declaration().isPublic() && module.equals(currentModule)) {
            error(PRIVATE_EXPOSURE, pos, exposedBy + " exposes '" + type.userName()
                    + "', which is private to module '" + module + "'; declare it 'pub type' "
                    + "or make the member private");
        }
        for (SolvikType argument : type.arguments()) {
            requireExposedTypes(argument, pos, exposedBy);
        }
    }

    /** The module part of a qualified name, or {@code null} for a top-level type. */
    private static String moduleOf(String qualifiedName) {
        if (qualifiedName == null) return null;
        int separator = qualifiedName.lastIndexOf("::");
        return separator < 0 ? null : qualifiedName.substring(0, separator);
    }

    /** The last component of a qualified name: {@code Point} for {@code geometry::Point}. */
    private static String simpleName(String qualifiedName) {
        int separator = qualifiedName.lastIndexOf("::");
        return separator < 0 ? qualifiedName : qualifiedName.substring(separator + 2);
    }

    /**
     * The declared type a written name refers to, or {@code null} when nothing matches.
     *
     * <p>Inside a module the enclosing module is searched first, then the top level; a written
     * {@code geometry::Point} is looked up as-is (§2, "Modules").
     */
    private String resolveUserName(String written) {
        if (written.contains("::")) {
            return userTypes.containsKey(written) ? written : null;
        }
        if (currentModule != null) {
            String scoped = qualifiedName(currentModule, written);
            if (userTypes.containsKey(scoped)) {
                return scoped;
            }
        }
        return userTypes.containsKey(written) ? written : null;
    }

    private SolvikType resolveType(Ast.TypeRef type) {
        return switch (type) {
            case Ast.TypeRef.Array array -> resolveArrayType(array);
            case Ast.TypeRef.Named named -> resolveNamedType(named);
        };
    }

    /**
     * A written array type: the element type carries its own nullability, and {@code ?} after the
     * brackets makes the array itself nullable (§5).
     */
    private SolvikType resolveArrayType(Ast.TypeRef.Array array) {
        SolvikType element = resolveType(array.element());
        SolvikType resolved = SolvikType.array(element, arrayLength(array));
        return array.nullable() ? resolved.asNullable() : resolved;
    }

    /** The fixed length of a written {@code T[N]}, or {@code null} for the size-erased {@code T[]}. */
    private Integer arrayLength(Ast.TypeRef.Array array) {
        if (array.size() == null) {
            return null;
        }
        if (!(array.size() instanceof Ast.IntLit literal)) {
            error(TYPE_ERROR, array.size().pos(),
                    "an array length must be an integer literal (§5.1)");
            return null;
        }
        if (literal.suffix().isExplicit() && literal.suffix() != NumericSuffix.INTEGER) {
            error(TYPE_ERROR, literal.pos(), "an array length must be an Integer literal, found "
                    + literal.suffix().text() + " (§5.1)");
            return null;
        }
        BigInteger size = new BigInteger(literal.text());
        if (size.signum() < 1) {
            error(TYPE_ERROR, literal.pos(), "an array length must be at least 1 (§5.1): Solvik has"
                    + " no empty array, because a fixed length is at least one element");
            return null;
        }
        if (size.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
            error(TYPE_ERROR, literal.pos(),
                    "an array length must be at most 2147483647, found " + size);
            return null;
        }
        return size.intValue();
    }

    /** A named type, with {@code ?} applied when it was written (§2.1). */
    private SolvikType resolveNamedType(Ast.TypeRef.Named type) {
        SolvikType resolved = resolveNamed(type);
        if (!type.nullable()) {
            return resolved;
        }
        if (resolved.kind() == SolvikType.Kind.NULL) {
            error(TYPE_ERROR, type.pos(),
                    "'Null' is already the type of the null value; 'Null?' is redundant (§2.1)");
            return resolved;
        }
        return resolved.asNullable();
    }

    private SolvikType resolveNamed(Ast.TypeRef.Named type) {
        if (staticContext && unavailableTypeParameters.contains(type.name())) {
            error(TYPE_ERROR, type.pos(), "a static member cannot use type parameter '"
                    + type.name() + "' of its generic type");
            return SolvikType.ANY;
        }
        String nativeKey = resolveUserName(type.name());
        TypeInfo nativeInfo = nativeKey == null ? null : userTypes.get(nativeKey);
        if (nativeInfo != null && nativeInfo.declaration().nativeClass() != null) {
            requireVisibleType(nativeKey, type.pos());
            List<SolvikType> arguments = type.arguments().isEmpty()
                    ? nativeInfo.parameters().stream().map(ignored -> SolvikType.ANY).toList()
                    : type.arguments().stream().map(this::resolveType).toList();
            if (arguments.size() != nativeInfo.parameters().size()) {
                error(TYPE_ERROR, type.pos(), "wrong native type argument count for " + type.name());
            }
            return nativeInfo.typeFor(arguments);
        }
        if (!type.arguments().isEmpty()) {
            error(WRITTEN_TYPE_ARGUMENT, type.pos(), "type arguments are not written: '"
                    + type + "' should be '" + type.name()
                    + "'; a construction such as " + type.name()
                    + "(value) infers them");
            return SolvikType.ANY;
        }
        SolvikType builtin = SolvikType.named(type.name());
        if (builtin != null) return builtin;
        if (typeParameters.contains(type.name())) {
            return SolvikType.variable(type.name());
        }
        String key = resolveUserName(type.name());
        if (key != null) {
            requireVisibleType(key, type.pos());
            List<String> declared = userTypes.get(key) == null
                    ? List.of()
                    : userTypes.get(key).declaration().typeParameters();
            if (!declared.isEmpty()) {
                error(WRITTEN_TYPE_ARGUMENT, type.pos(), "type '" + type.name()
                        + "' is generic, and its arguments are inferred at construction:"
                        + " it cannot be named here without them");
                return SolvikType.user(key,
                        declared.stream().map(ignored -> SolvikType.ANY).toList());
            }
            return SolvikType.user(key);
        }
        reportUnknownType(type);
        return SolvikType.ANY;
    }

    /** Reports why a written type name did not resolve (§2, "Modules"). */
    private void reportUnknownType(Ast.TypeRef.Named type) {
        String written = type.name();
        int separator = written.indexOf("::");
        if (separator < 0) {
            error(TYPE_ERROR, type.pos(), "unknown type '" + written + "'");
            return;
        }
        String module = written.substring(0, separator);
        if (!declaredModules.containsKey(module)) {
            error(TYPE_ERROR, type.pos(), "unknown module '" + module + "'");
        } else {
            error(TYPE_ERROR, type.pos(), "module '" + module + "' declares no type '"
                    + simpleName(written) + "'");
        }
    }

    // -------------------------------------------------------------- statements

    private void checkStatement(Ast.Stmt statement) {
        switch (statement) {
            case Ast.VarStmt var -> checkVar(var);
            case Ast.AssignStmt assign -> checkAssign(assign);
            case Ast.ExprStmt exprStmt -> {
                SolvikType type = checkExpression(exprStmt.expression(), true);
                if (!(exprStmt.expression() instanceof Ast.CallExpr)) {
                    error(TYPE_ERROR, exprStmt.pos(),
                            "only an assignment or a call may be used as a statement");
                } else if (type != SolvikType.VOID) {
                    error(TYPE_ERROR, exprStmt.pos(),
                            "the result of this call is unused; it has a value");
                }
            }
            case Ast.IfStmt ifStmt -> {
                for (Ast.Branch branch : ifStmt.branches()) {
                    requireBoolean(branch.condition(), "an if condition");
                    checkBlock(branch.body(), nullFacts(branch.condition(), true));
                }
                if (ifStmt.elseBody() != null) {
                    // Only the last condition's negation is known in the else body; an else-if
                    // chain would need the whole chain, which is more than this rule promises.
                    checkBlock(ifStmt.elseBody(), nullFacts(
                            ifStmt.branches().getLast().condition(), false));
                }
            }
            case Ast.WhileStmt whileStmt -> {
                requireBoolean(whileStmt.condition(), "a while condition");
                loopDepth++;
                checkBlock(whileStmt.body(), nullFacts(whileStmt.condition(), true));
                loopDepth--;
            }
            case Ast.BreakStmt breakStmt -> {
                if (loopDepth == 0) {
                    error(LOOP_CONTROL_OUTSIDE_LOOP, breakStmt.pos(),
                            "'break' exits a loop, and no loop encloses it");
                }
            }
            case Ast.ContinueStmt continueStmt -> {
                if (loopDepth == 0) {
                    error(LOOP_CONTROL_OUTSIDE_LOOP, continueStmt.pos(),
                            "'continue' starts the next loop iteration, and no loop encloses it");
                }
            }
            case Ast.ForStmt forStmt -> {
                SolvikType iterable = requireNonNull(checkExpression(forStmt.iterable()),
                        forStmt.iterable().pos(), "for...in iteration");
                if (!iterable.isArray()) {
                    error(TYPE_ERROR, forStmt.pos(),
                            "for...in iterates an array, found " + iterable);
                }
                Scope outer = scope;
                scope = new Scope(outer, ordinals);
                bindings.put(forStmt, scope.declare(forStmt.name(), iterable.element()));
                loopDepth++;
                checkBlock(forStmt.body()); // the body is a block, and therefore a scope (§3)
                loopDepth--;
                scope = outer;
            }
            case Ast.SwitchStmt switchStmt -> checkSwitch(switchStmt);
            case Ast.TryStmt tryStmt -> checkTry(tryStmt);
            case Ast.RaiseStmt raise -> checkRaise(raise);
            case Ast.ReturnStmt returnStmt -> {
                if (inStaticInitializer) {
                    error(RETURN_OUTSIDE_FUNCTION, returnStmt.pos(),
                            "'return' is not allowed in a static initializer");
                    return;
                }
                if (!inFunction) {
                    error(RETURN_OUTSIDE_FUNCTION, returnStmt.pos(),
                            "'return' is only valid inside a function");
                    return;
                }
                if (finallyDepth > 0) {
                    error(FINALLY_RETURN, returnStmt.pos(), "'return' is not allowed in a 'finally'"
                            + " block; it would discard a pending exception or return value"
                            + " (§7.4)");
                }
                if (currentReturnType == SolvikType.VOID) {
                    if (returnStmt.value() != null) {
                        error(TYPE_ERROR, returnStmt.pos(),
                                "this function has no return type, so it cannot return a value");
                    }
                } else if (returnStmt.value() == null) {
                    error(TYPE_ERROR, returnStmt.pos(),
                            "this function returns " + currentReturnType + ", so return needs a value");
                } else {
                    SolvikType value = checkExpression(returnStmt.value());
                    if (!isAssignable(value, currentReturnType)) {
                        error(TYPE_ERROR, returnStmt.pos(),
                                "cannot return " + value + " from a function returning "
                                        + currentReturnType);
                    }
                }
            }
            case Ast.BlockStmt block -> checkBlock(block.body());
        }
    }

    /**
     * Checks {@code try}/{@code catch}/{@code finally} (§7.4).
     *
     * <p>Every clause is its own block, so the non-null facts proven inside one clause are unknown in
     * the next: a {@code try} body that may leave early, and a {@code catch} body that runs after an
     * arbitrary failure, prove nothing to the code that follows. The catch variable is an immutable,
     * non-null {@code Exception} bound in the clause only.
     */
    private void checkTry(Ast.TryStmt tryStmt) {
        checkBlock(tryStmt.body());
        if (tryStmt.catcher() != null) {
            Ast.CatchClause catcher = tryStmt.catcher();
            SolvikType caught = resolveType(catcher.type());
            if (!SolvikType.EXCEPTION.equals(caught)) {
                error(CATCH_TYPE, catcher.type().pos(), "a catch clause handles Exception, found "
                        + caught + "; Exception is the only catchable type (§7.4)");
            }
            Scope outer = scope;
            scope = new Scope(outer, ordinals);
            bindings.put(catcher, scope.declare(catcher.name(), SolvikType.EXCEPTION));
            checkBlock(catcher.body());
            scope = outer;
        }
        if (tryStmt.finallyBody() != null) {
            finallyDepth++;
            checkBlock(tryStmt.finallyBody());
            finallyDepth--;
        }
    }

    /** Checks {@code raise exception} (§7.4): the raised value is a non-null {@code Exception}. */
    private void checkRaise(Ast.RaiseStmt raise) {
        SolvikType raised = requireNonNull(checkExpression(raise.exception()),
                raise.exception().pos(), "a raised exception");
        if (raised != null && !SolvikType.EXCEPTION.equals(raised)) {
            error(TYPE_ERROR, raise.exception().pos(),
                    "only an Exception can be raised, found " + raised);
        }
    }

    /**
     * Checks {@code Exception(code, message)} (§7.4).
     *
     * <p>The code is an {@code Integer} and the message a non-null {@code String}. A negative code is
     * not a compile-time error here, exactly as a negative exit status is not: the generated
     * constructor aborts with {@code SOL-R012} whenever it sees one, including for a code computed at
     * run time.
     */
    private SolvikType checkExceptionConstruction(Ast.CallExpr call) {
        List<Ast.Expr> arguments = call.arguments();
        if (arguments.size() != 2) {
            error(TYPE_ERROR, call.pos(), "'Exception' takes a code and a message, found "
                    + arguments.size() + " argument(s)");
            for (Ast.Expr argument : arguments) {
                checkExpression(argument);
            }
            return SolvikType.EXCEPTION;
        }
        SolvikType code = requireNonNull(checkExpression(arguments.get(0)),
                arguments.get(0).pos(), "an exception code");
        if (code != SolvikType.INTEGER) {
            error(TYPE_ERROR, arguments.get(0).pos(),
                    "an exception code must be an Integer, found " + code);
        }
        SolvikType message = requireNonNull(checkExpression(arguments.get(1)),
                arguments.get(1).pos(), "an exception message");
        if (message != SolvikType.STRING) {
            error(TYPE_ERROR, arguments.get(1).pos(),
                    "an exception message must be a String, found " + message);
        }
        return SolvikType.EXCEPTION;
    }

    private void checkVar(Ast.VarStmt var) {
        if (var.initializer() == null) {
            // The parser already reported the missing initializer; keep the binding known so the
            // rest of the body does not produce a cascade of unknown-name diagnostics.
            SolvikType declared = var.type() == null ? SolvikType.ANY : resolveType(var.type());
            if (scope.local(var.name()) == null) {
                bindings.put(var, scope.declare(var.name(), declared, var, var.isMutable()));
            }
            variableTypes.put(var, declared);
            return;
        }
        SolvikType value = checkInitializer(var.initializer());
        SolvikType type = bindingType(var.type(), var.nullableInferred(), value,
                var.pos(), "'" + var.name() + "'");
        if (scope.local(var.name()) != null) {
            error(TYPE_ERROR, var.pos(), "'" + var.name()
                    + "' is already declared in this scope; a nested scope may shadow it");
        } else {
            Binding binding = scope.declare(var.name(), type, var, var.isMutable());
            bindings.put(var, binding);
            if (type.isNullable() && !value.isNullable() && !value.isNull()) {
                // The initializer is a present value, so the binding starts out non-null (§7.1).
                narrowed.put(binding, type.asNonNull());
            }
        }
        variableTypes.put(var, type);
        reportUninferrableTypeArguments(type, var.pos());
    }

    /** Checks a declaration's initializer, which every declaration requires (§3.1). */
    private SolvikType checkInitializer(Ast.Expr initializer) {
        SolvikType value = checkExpression(initializer);
        if (value == SolvikType.VOID) {
            error(TYPE_ERROR, initializer.pos(),
                    "a declaration needs a value, but this call returns none");
            return SolvikType.ANY;
        }
        return value;
    }

    /**
     * The type a declaration binds: its written type, or the type inferred from its initializer
     * (§3.1, §3.2).
     *
     * <p>{@code name := value} keeps the exact static type of the value, so a {@code Null}
     * initializer gives the {@code Null} type. {@code name? := value} asks for that type made
     * nullable, and {@code name? := Null} is {@code Any?} because {@code Null} alone names no
     * narrower non-null type.
     */
    private SolvikType bindingType(Ast.TypeRef written, boolean nullableInferred,
            SolvikType value, SourcePos pos, String what) {
        if (written != null) {
            SolvikType declared = resolveType(written);
            if (!isAssignable(value, declared)) {
                error(TYPE_ERROR, pos, "cannot initialize " + what + " of type " + declared
                        + " with " + value);
            }
            return declared;
        }
        if (nullableInferred) {
            return value.isNull() ? SolvikType.ANY.asNullable() : value.asNullable();
        }
        return value;
    }

    /**
     * A generic construction must decide its type arguments where it is written (§8.2).
     *
     * <p>There is no late fixation: a binding's type is known at its declaration, so an argument
     * the constructor call cannot infer is reported there instead of being settled by a later use.
     */
    private void reportUninferrableTypeArguments(SolvikType type, SourcePos pos) {
        if (type.kind() != SolvikType.Kind.USER
                || type.arguments().stream().noneMatch(SolvikType::isVariable)) {
            return;
        }
        String arguments = type.arguments().stream().filter(SolvikType::isVariable)
                .map(SolvikType::userName).collect(Collectors.joining(", "));
        error(TYPE_ERROR, pos, "cannot infer type argument '" + arguments
                + "' from this construction; call a constructor that takes a value of that type,"
                + " or name the arguments at the construction");
    }

    private void checkAssign(Ast.AssignStmt assign) {
        if (assign.target() instanceof Ast.MemberExpr member) {
            String staticType = namedType(member.target());
            if (staticType != null) {
                checkStaticFieldAssignment(assign, member, staticType);
                return;
            }
            if (!(member.target() instanceof Ast.NameExpr name && "self".equals(name.name()))) {
                SolvikType target = checkExpression(member.target());
                SolvikType value = checkExpression(assign.value());
                if (target != null && target.isArray()) {
                    if (ARRAY_SIZE_MEMBER.equals(member.name())) {
                        error(IMMUTABLE_MEMBER, assign.pos(), "an array's length is its immutable"
                                + " member '" + ARRAY_SIZE_MEMBER + "'; it is fixed when the array"
                                + " is constructed");
                    } else {
                        error(TYPE_ERROR, assign.pos(), "type " + target + " has no member '"
                                + member.name() + "'; an array has the member '"
                                + ARRAY_SIZE_MEMBER + "' and index access");
                    }
                    return;
                }
                TypeInfo info = userTypes.get(target.userName());
                if (info == null || !info.fields().containsKey(member.name())) {
                    error(TYPE_ERROR, assign.pos(),
                            "type " + target + " has no field '" + member.name() + "'");
                } else if (info.isStaticField(member.name())) {
                    error(TYPE_ERROR, assign.pos(), "field '" + member.name() + "' of " + target
                            + " is static; write it through " + target
                            + "." + member.name());
                } else if (!info.isPublic(member.name())
                        && !inDeclaringType(target.userName())) {
                    error(TYPE_ERROR, assign.pos(),
                            "field '" + member.name() + "' of " + target
                                    + " is private; it cannot be assigned from outside");
                } else if (!info.isMutable(member.name())) {
                    error(IMMUTABLE_MEMBER, assign.pos(), "field '" + member.name() + "' of "
                            + target + " is immutable; declare it 'mut' to write it");
                } else {
                    SolvikType fieldType = info.fields().get(member.name())
                            .substitute(typeArgumentMapping(target));
                    if ("..=".equals(assign.operator())) {
                        requireAppendable(fieldType, assign.pos(), "field");
                    } else if (!isAssignable(value, fieldType)) {
                        error(TYPE_ERROR, assign.pos(),
                                "cannot assign " + value + " to a field of type " + fieldType);
                    }
                }
                return;
            }
            checkSelfFieldAssignment(assign, member);
            return;
        }
        if (assign.target() instanceof Ast.IndexExpr index) {
            checkIndexAssignment(assign, index);
            return;
        }
        Binding binding;
        String spelling;
        SourcePos pos = assign.target().pos();
        if (assign.target() instanceof Ast.NameExpr name) {
            binding = scope.lookup(name.name());
            spelling = name.name();
            if (binding != null) {
                bindings.put(name, binding);
            } else if (bareMemberField(name.name()) != null) {
                implicitMembers.put(name, name.name());
                checkBareMemberWrite(assign, name);
                return;
            }
        } else if (assign.target() instanceof Ast.OuterExpr outer) {
            binding = scope.lookupOuter(outer.name());
            spelling = "outer " + outer.name();
            if (binding != null) {
                bindings.put(outer, binding);
            }
        } else {
            error(UNSUPPORTED, assign.pos(),
                    "only variables, outer variables, self.<field>, and array elements can be assigned");
            return;
        }
        SolvikType value = checkExpression(assign.value());
        if (binding == null) {
            error(TYPE_ERROR, pos, "unknown variable '" + spelling + "'");
            return;
        }
        if (!binding.isMutable()) {
            error(IMMUTABLE_MEMBER, assign.pos(), "'" + spelling
                    + "' is immutable; declare it 'mut' to assign it again");
            return;
        }
        if ("..=".equals(assign.operator())) {
            requireAppendable(binding.type(), assign.pos(), "variable");
            return;
        }
        if (!isAssignable(value, binding.type())) {
            error(TYPE_ERROR, assign.pos(),
                    "cannot assign " + value + " to a variable of type " + binding.type());
        }
        if (!value.isNullable() && !value.isNull() && binding.type().isNullable()) {
            // Writing a non-null value proves the binding is present until it is written again.
            narrowed.put(binding, binding.type().asNonNull());
        } else {
            // A write may store Null, so a previous proof no longer holds (§7.1).
            narrowed.remove(binding);
        }
    }

    /** Checks {@code x = value} inside a type body, where {@code x} is that type's member. */
    private void checkBareMemberWrite(Ast.AssignStmt assign, Ast.NameExpr name) {
        SolvikType value = checkExpression(assign.value());
        String selfType = selfTypeName();
        String typeName = selfType != null ? selfType : currentTypeName;
        TypeInfo info = userTypes.get(typeName);
        if (info == null) {
            return;
        }
        Ast.FieldDecl declared = info.declared.get(name.name());
        if (declared != null && declared.isStatic()) {
            staticOwners.put(name, typeName);
        } else if (staticContext) {
            error(TYPE_ERROR, name.pos(), "field '" + name.name()
                    + "' is an instance member; a static function has no 'self'");
            return;
        }
        SolvikType fieldType = info.fields().get(name.name());
        if (declared != null && declared.isStatic() && !declared.isMutable()) {
            error(IMMUTABLE_MEMBER, assign.pos(), "static field '" + name.name()
                    + "' is immutable; declare it 'static mut' to write it");
            return;
        }
        if (!inConstructor && !info.isMutable(name.name())) {
            error(IMMUTABLE_MEMBER, assign.pos(), "field '" + name.name()
                    + "' is immutable after construction; declare it 'mut' to change it");
            return;
        }
        if ("..=".equals(assign.operator())) {
            requireAppendable(fieldType, assign.pos(), "field");
            return;
        }
        if (!isAssignable(value, fieldType)) {
            error(TYPE_ERROR, assign.pos(),
                    "cannot assign " + value + " to a field of type " + fieldType);
        }
    }

    private void checkSelfFieldAssignment(Ast.AssignStmt assign, Ast.MemberExpr member) {
        SolvikType value = checkExpression(assign.value());
        TypeInfo selfInfo = userTypes.get(selfTypeName());
        if (selfInfo != null && selfInfo.isStaticField(member.name())) {
            error(TYPE_ERROR, assign.pos(), "static field '" + member.name()
                    + "' belongs to the type; write it through " + selfTypeName() + "."
                    + member.name());
            return;
        }
        SolvikType fieldType = checkMember(member);
        TypeInfo info = userTypes.get(selfTypeName());
        if (!inConstructor && info != null && !info.isMutable(member.name())) {
            error(IMMUTABLE_MEMBER, assign.pos(), "field '" + member.name()
                    + "' is immutable after construction; declare it 'mut' to change it");
        }
        if ("..=".equals(assign.operator())) {
            requireAppendable(fieldType, assign.pos(), "field");
            return;
        }
        if (!isAssignable(value, fieldType)) {
            error(TYPE_ERROR, assign.pos(),
                    "cannot assign " + value + " to a field of type " + fieldType);
        }
    }

    /**
     * Checks {@code array[index] = value} (§6, "Indexing"): the element takes an assignment of the
     * value's type, so the widening rule of expressions does not apply here either.
     */
    private void checkIndexAssignment(Ast.AssignStmt assign, Ast.IndexExpr index) {
        SolvikType element = checkExpression(index);
        SolvikType value = checkExpression(assign.value());
        if ("..=".equals(assign.operator())) {
            requireAppendable(element, assign.pos(), "element");
            return;
        }
        if (!isAssignable(value, element)) {
            error(TYPE_ERROR, assign.pos(),
                    "cannot assign " + value + " to an element of type " + element);
        }
    }

    /** The type of the enclosing type body, taken from {@code self}. */
    private String selfTypeName() {
        Binding self = scope.lookup("self");
        return self == null ? null : self.type().userName();
    }

    /**
     * Checks a block, forgetting the non-null facts proven inside it (§7.1).
     *
     * <p>Facts flow inwards from an enclosing condition and never outwards: a fact established
     * inside a block is not knowledge the code after the block has.
     */
    private void checkBlock(List<Ast.Stmt> body) {
        IdentityHashMap<Binding, SolvikType> saved = new IdentityHashMap<>(narrowed);
        Scope outer = scope;
        scope = new Scope(outer, ordinals);
        checkStatements(body);
        scope = outer;
        narrowed.clear();
        narrowed.putAll(saved);
    }

    /** Checks a block with the non-null facts an enclosing condition proves (§7.1). */
    private void checkBlock(List<Ast.Stmt> body, Map<Binding, SolvikType> facts) {
        withFacts(facts, () -> {
            checkBlock(body);
            return null;
        });
    }

    /** Runs {@code body} with additional non-null facts, restoring the previous set afterwards. */
    private <T> T withFacts(Map<Binding, SolvikType> facts, java.util.function.Supplier<T> body) {
        if (facts.isEmpty()) {
            return body.get();
        }
        IdentityHashMap<Binding, SolvikType> saved = new IdentityHashMap<>(narrowed);
        narrowed.putAll(facts);
        try {
            return body.get();
        } finally {
            narrowed.clear();
            narrowed.putAll(saved);
        }
    }

    /**
     * The non-null facts a condition proves for a branch (§7.1).
     *
     * <p>Only a direct comparison of a binding with {@code Null} establishes a fact, together with
     * {@code &&}, {@code ||}, and {@code !} combinations of such comparisons. Fixing a fact to the
     * binding, rather than to a name, is what keeps the rule sound under shadowing.
     */
    private Map<Binding, SolvikType> nullFacts(Ast.Expr condition, boolean whenTrue) {
        return switch (condition) {
            case Ast.UnaryExpr unary when "!".equals(unary.operator()) ->
                    nullFacts(unary.operand(), !whenTrue);
            case Ast.BinaryExpr binary when "&&".equals(binary.operator()) && whenTrue ->
                    mergeFacts(nullFacts(binary.left(), true), nullFacts(binary.right(), true));
            case Ast.BinaryExpr binary when "||".equals(binary.operator()) && !whenTrue ->
                    mergeFacts(nullFacts(binary.left(), false), nullFacts(binary.right(), false));
            case Ast.BinaryExpr binary when "!=".equals(binary.operator()) && whenTrue ->
                    nullCheckFacts(binary.left(), binary.right());
            case Ast.BinaryExpr binary when "==".equals(binary.operator()) && !whenTrue ->
                    nullCheckFacts(binary.left(), binary.right());
            default -> Map.of();
        };
    }

    private static Map<Binding, SolvikType> mergeFacts(Map<Binding, SolvikType> left,
            Map<Binding, SolvikType> right) {
        if (left.isEmpty()) return right;
        if (right.isEmpty()) return left;
        IdentityHashMap<Binding, SolvikType> merged = new IdentityHashMap<>(left);
        merged.putAll(right);
        return merged;
    }

    /** The fact proved by {@code value != Null}: that binding holds a non-null value. */
    private Map<Binding, SolvikType> nullCheckFacts(Ast.Expr left, Ast.Expr right) {
        Ast.Expr value = left instanceof Ast.NullLit ? right : left;
        Ast.Expr other = left instanceof Ast.NullLit ? left : right;
        if (!(other instanceof Ast.NullLit) || !(value instanceof Ast.NameExpr name)) {
            return Map.of();
        }
        Binding binding = bindings.get(name);
        if (binding == null || !binding.type().isNullable()) {
            return Map.of();
        }
        return Map.of(binding, binding.type().asNonNull());
    }

    /** Requires a non-null String target for {@code ..=} (§3.3). */
    private void requireAppendable(SolvikType type, SourcePos pos, String what) {
        if (type != null && type.kind() == SolvikType.Kind.STRING) {
            if (type.isNullable()) {
                error(NULL_UNSAFE, pos, what + " may be Null; check it against Null before"
                        + " appending to it (§7.1)");
            }
            return;
        }
        error(TYPE_ERROR, pos, "'..=' appends to a String " + what + ", found " + type);
    }

    /** Requires a non-null value where an operation dereferences it (§2.2, §7.1). */
    private SolvikType requireNonNull(SolvikType type, SourcePos pos, String what) {
        if (type == null || !type.isNullable()) {
            return type;
        }
        error(NULL_UNSAFE, pos, what + " needs a non-null value, found " + type
                + "; compare it with Null and use it where the value is present (§7.1)");
        return type.asNonNull();
    }

    /**
     * Checks a statement list and rejects code that cannot run.
     *
     * <p>{@code return} exits immediately (§3), so a statement after it in the same block is
     * unreachable. This is a language rule rather than a code-generation accident: without it the
     * generated Java would contain an unreachable statement, which {@code javac} refuses.
     */
    private void checkStatements(List<Ast.Stmt> body) {
        Ast.Stmt terminator = null;
        for (Ast.Stmt statement : body) {
            if (terminator != null) {
                error(UNREACHABLE, statement.pos(),
                        "unreachable statement after " + terminatorName(terminator));
                terminator = null;
            }
            checkStatement(statement);
            if (terminates(statement)) {
                terminator = statement;
            }
            if (statement instanceof Ast.IfStmt guard && guard.elseBody() == null
                    && guard.branches().stream().allMatch(branch -> terminates(branch.body()))) {
                // A guard whose branch always leaves knowledgeably rules out its condition for the
                // statements that follow (§7.1).
                narrowed.putAll(nullFacts(guard.branches().getLast().condition(), false));
            }
        }
    }

    /** How a diagnostic names the statement that left before the next one could run. */
    private static String terminatorName(Ast.Stmt statement) {
        return switch (statement) {
            case Ast.ReturnStmt ignored -> "'return'";
            case Ast.BreakStmt ignored -> "'break'";
            case Ast.ContinueStmt ignored -> "'continue'";
            case Ast.RaiseStmt ignored -> "'raise'";
            case Ast.TryStmt ignored -> "a 'try' statement that always leaves";
            default -> "a statement that always leaves";
        };
    }

    /**
     * Whether a statement always leaves before reaching the end of its statement list.
     *
     * <p>{@code return}, {@code break}, {@code continue}, and {@code raise} never complete
     * normally. A {@code try} statement completes normally only if a clause it can fall out of does:
     * a {@code finally} that always leaves runs after every path, and a {@code catch} that always
     * leaves handles the one way its {@code try} body can still fail. The generated Java inherits the
     * same rule from {@code javac}, so this is a diagnostic improvement, not a lowering decision.
     */
    private static boolean terminates(Ast.Stmt statement) {
        return switch (statement) {
            case Ast.ReturnStmt ignored -> true;
            case Ast.BreakStmt ignored -> true;
            case Ast.ContinueStmt ignored -> true;
            case Ast.RaiseStmt ignored -> true;
            case Ast.TryStmt attempt -> terminates(attempt.body())
                    && (attempt.catcher() == null || terminates(attempt.catcher().body()))
                    && (attempt.finallyBody() == null || terminates(attempt.finallyBody()));
            default -> false;
        };
    }

    /** Whether a statement list always leaves before reaching its end. */
    private static boolean terminates(List<Ast.Stmt> body) {
        return !body.isEmpty() && terminates(body.getLast());
    }

    /**
     * Checks a switch (§3, "switch"): a switchable subject, literal labels of the subject's own
     * type, no duplicate labels, and regex cases after every literal case.
     *
     * <p>A regex pattern is compiled here, so a malformed pattern is a compile-time error rather
     * than a run-time one on the input path that happens to reach it.
     */
    private void checkSwitch(Ast.SwitchStmt switchStmt) {
        SolvikType subject = requireNonNull(checkExpression(switchStmt.subject()),
                switchStmt.subject().pos(), "a switch subject");
        boolean switchable = subject == SolvikType.STRING || subject == SolvikType.INTEGER
                || subject == SolvikType.BYTE || subject == SolvikType.BOOLEAN;
        if (!switchable) {
            error(TYPE_ERROR, switchStmt.subject().pos(), "cannot switch on " + subject
                    + "; a switch subject must be String, Integer, Byte, or Boolean");
        }
        Set<String> labels = new HashSet<>();
        boolean seenRegex = false;
        for (Ast.CaseClause clause : switchStmt.cases()) {
            if (clause.regex()) {
                seenRegex = true;
                if (subject != SolvikType.STRING) {
                    error(TYPE_ERROR, clause.pos(),
                            "a 'case regex' needs a String switch, found " + subject);
                }
                checkRegexLabel(clause.label());
            } else {
                if (seenRegex) {
                    error(TYPE_ERROR, clause.pos(),
                            "a literal case cannot follow a 'case regex'; regex cases come last");
                }
                checkCaseLabel(subject, clause.label());
                String key = labelKey(clause.label());
                if (key != null && !labels.add(key)) {
                    error(TYPE_ERROR, clause.label().pos(), "duplicate case label " + key);
                }
            }
            checkBlock(clause.body());
        }
        if (switchStmt.defaultBody() != null) {
            checkBlock(switchStmt.defaultBody());
        }
    }

    private void checkCaseLabel(SolvikType subject, Ast.Expr label) {
        SolvikType labelType = checkExpression(label);
        boolean numeric = subject == SolvikType.INTEGER || subject == SolvikType.BYTE;
        boolean compatible = subject == SolvikType.STRING ? labelType == SolvikType.STRING
                : subject == SolvikType.BOOLEAN ? labelType == SolvikType.BOOLEAN
                : numeric ? labelType == SolvikType.INTEGER || labelType == SolvikType.BYTE
                : false;
        if (!compatible) {
            error(TYPE_ERROR, label.pos(),
                    "a case label of type " + labelType + " cannot match a " + subject + " subject");
        }
        if (subject == SolvikType.BYTE && labelType == SolvikType.INTEGER) {
            BigInteger value = labelValue(label);
            if (value != null && (value.compareTo(BigInteger.valueOf(-128)) < 0
                    || value.compareTo(BigInteger.valueOf(127)) > 0)) {
                error(TYPE_ERROR, label.pos(),
                        "case value " + value + " is outside Byte's -128..127 range, so it cannot match");
            }
        }
    }

    private void checkRegexLabel(Ast.Expr label) {
        if (!(label instanceof Ast.StringLit text)) {
            return; // the parser already reported a non-literal regex label
        }
        try {
            Pattern.compile(text.value());
        } catch (PatternSyntaxException e) {
            error(INVALID_REGEX, text.pos(),
                    "invalid regular expression: " + e.getDescription());
        }
    }

    /** A stable key for duplicate detection: labels that would match the same subject. */
    private String labelKey(Ast.Expr label) {
        if (label instanceof Ast.StringLit text) {
            return quote(text.value());
        }
        if (label instanceof Ast.BoolLit bool) {
            return Boolean.toString(bool.value());
        }
        BigInteger value = labelValue(label);
        return value == null ? null : value.toString();
    }

    /** The integer value of a numeric label, or {@code null} for a non-integer label. */
    private static BigInteger labelValue(Ast.Expr label) {
        if (label instanceof Ast.IntLit literal) {
            return new BigInteger(literal.text());
        }
        if (label instanceof Ast.UnaryExpr unary && "-".equals(unary.operator())
                && unary.operand() instanceof Ast.IntLit literal) {
            return new BigInteger(literal.text()).negate();
        }
        return null;
    }

    private static String quote(String value) {
        return "\"" + value + "\"";
    }

    private void requireBoolean(Ast.Expr condition, String what) {
        SolvikType type = checkExpression(condition);
        if (type == SolvikType.BOOLEAN) {
            return;
        }
        if (type.kind() == SolvikType.Kind.BOOLEAN) {
            error(NULL_UNSAFE, condition.pos(), what + " must be a non-null Boolean, found " + type
                    + "; compare it with Null and use it where a value is present (§7.1)");
        } else {
            error(TYPE_ERROR, condition.pos(), what + " must be Boolean, found " + type);
        }
    }

    // ------------------------------------------------------------- expressions

    private SolvikType checkExpression(Ast.Expr expression) {
        return checkExpression(expression, false);
    }

    /** Only the outer call of an expression statement may return no value. */
    private SolvikType checkExpression(Ast.Expr expression, boolean allowVoid) {
        SolvikType type = prechecked.remove(expression);
        if (type == null) type = infer(expression);
        if (type == SolvikType.VOID && !allowVoid) {
            error(TYPE_ERROR, expression.pos(), "a call returning no value is only valid as a statement");
            type = SolvikType.ANY; // recover without cascading incompatible-type errors
        }
        expressionTypes.put(expression, type);
        return type;
    }

    private SolvikType infer(Ast.Expr expression) {
        return switch (expression) {
            case Ast.IntLit literal -> checkIntegerLiteral(literal);
            case Ast.DecimalLit literal -> checkDecimalLiteral(literal);
            case Ast.StringLit ignored -> SolvikType.STRING;
            case Ast.BoolLit ignored -> SolvikType.BOOLEAN;
            case Ast.NullLit ignored -> SolvikType.NULL;
            case Ast.NameExpr name -> checkName(name);
            case Ast.QualifiedNameExpr qualified -> checkQualifiedName(qualified);
            case Ast.TypeApplyExpr application -> {
                error(TYPE_ERROR, application.pos(), "type arguments require a native constructor call");
                yield SolvikType.ANY;
            }
            case Ast.OuterExpr outer -> checkOuterName(outer);
            case Ast.UnaryExpr unary -> checkUnary(unary);
            case Ast.BinaryExpr binary -> checkBinary(binary);
            case Ast.CallExpr call -> checkCall(call);
            case Ast.IndexExpr index -> checkIndex(index);
            case Ast.MemberExpr member -> checkMember(member);
            case Ast.ArrayLit literal -> checkArrayLiteral(literal);
        };
    }

    /** Resolves a field and applies declaring-type privacy to {@code self} and same-type peers. */
    private SolvikType checkMember(Ast.MemberExpr member) {
        String staticType = namedType(member.target());
        if (staticType != null) {
            return checkStaticFieldRead(member, staticType);
        }
        SolvikType receiver = checkExpression(member.target());
        if (receiver != null && receiver.isArray()) {
            return arrayMember(member, requireNonNull(receiver, member.target().pos(),
                    "the member '" + ARRAY_SIZE_MEMBER + "'"));
        }
        SolvikType target = requireNonNull(receiver, member.target().pos(), "field access");
        boolean insideOwnBody = target.kind() == SolvikType.Kind.USER
                && inDeclaringType(target.userName());
        TypeInfo info = userTypes.get(target.userName());
        if (info == null) {
            if (target == SolvikType.STRING && ARRAY_SIZE_MEMBER.equals(member.name())) {
                // Arrays expose the member and String keeps the function, so name the right form.
                error(TYPE_ERROR, member.pos(), "'" + ARRAY_SIZE_MEMBER
                        + "' is a function of String, not a member; write value."
                        + ARRAY_SIZE_MEMBER + "()");
                return SolvikType.ANY;
            }
            error(TYPE_ERROR, member.pos(),
                    target == SolvikType.ANY
                            ? "field access needs a type; convert the value first (§4)"
                            : SolvikType.EXCEPTION.equals(target)
                                    ? exceptionMemberHint(member)
                                    : "type " + target + " has no fields");
            return SolvikType.ANY;
        }
        if (!info.fields().containsKey(member.name())) {
            error(TYPE_ERROR, member.pos(),
                    "type " + target + " has no field '" + member.name() + "'");
            return SolvikType.ANY;
        }
        if (info.isStaticField(member.name())) {
            error(TYPE_ERROR, member.pos(), "field '" + member.name() + "' of " + target
                    + " is static; read it through " + target + "." + member.name());
            return info.fields().get(member.name());
        }
        if (!insideOwnBody && !info.isPublic(member.name())) {
            error(TYPE_ERROR, member.pos(),
                    "field '" + member.name() + "' of " + target
                            + " is private; declare it 'pub' to read it from outside");
            return SolvikType.ANY;
        }
        SolvikType fieldType = info.fields().get(member.name());
        // A field declared with a type parameter takes the argument of the value it is read from.
        return fieldType.substitute(typeArgumentMapping(target));
    }

    /**
     * Resolves a member read on an array type (§5).
     *
     * <p>{@code size} is the array's only member, so any other name is reported with the members an
     * array actually offers rather than as a missing Java field.
     */
    private SolvikType arrayMember(Ast.MemberExpr member, SolvikType array) {
        if (ARRAY_SIZE_MEMBER.equals(member.name())) {
            return SolvikType.INTEGER;
        }
        error(TYPE_ERROR, member.pos(), "type " + array + " has no member '" + member.name()
                + "'; an array has the member '" + ARRAY_SIZE_MEMBER + "' and index access");
        return SolvikType.ANY;
    }

    /** Names the members an {@code Exception} does offer when a field read misses them (§7.4). */
    private static String exceptionMemberHint(Ast.MemberExpr member) {
        return "type Exception has no field '" + member.name()
                + "'; an Exception exposes the functions getCode() and getMessage()";
    }

    /**
     * The declared type a name target refers to, or {@code null} when it is an ordinary value.
     *
     * <p>{@code Type.member} and {@code module::Type.member} reuse the normal member syntax; the
     * checker recognizes the left side as a type name before evaluating it as a value.
     */
    private String namedType(Ast.Expr expression) {
        if (expression instanceof Ast.NameExpr name && scope.lookup(name.name()) != null) {
            return null; // a local or parameter wins over a type of the same spelling
        }
        String written = switch (expression) {
            case Ast.NameExpr name -> name.name();
            case Ast.QualifiedNameExpr qualified -> qualified.qualifiedName();
            default -> null;
        };
        return written == null ? null : resolveUserName(written);
    }

    /** Whether the current body belongs to the type named by {@code qualifiedName}. */
    private boolean inDeclaringType(String qualifiedName) {
        return qualifiedName.equals(currentTypeName);
    }

    /** Checks {@code Type.field}, a static field read (§3, static members). */
    private SolvikType checkStaticFieldRead(Ast.MemberExpr member, String typeName) {
        requireVisibleType(typeName, member.pos());
        staticOwners.put(member, typeName);
        TypeInfo info = userTypes.get(typeName);
        Ast.FieldDecl declared = info == null ? null : info.declared.get(member.name());
        if (declared == null) {
            error(TYPE_ERROR, member.pos(),
                    "type " + typeName + " has no field '" + member.name() + "'");
            return SolvikType.ANY;
        }
        if (!declared.isStatic()) {
            error(TYPE_ERROR, member.pos(), "field '" + member.name() + "' of " + typeName
                    + " is an instance member; read it from a value of type " + typeName);
            return info.fields().get(member.name());
        }
        if (!declared.isPublic() && !inDeclaringType(typeName)) {
            error(TYPE_ERROR, member.pos(), "static field '" + member.name() + "' of " + typeName
                    + " is private; declare it 'static pub' to read it from outside");
            return SolvikType.ANY;
        }
        return info.fields().get(member.name());
    }

    /** Checks {@code Type.field = value}, a static field write (§3, static members). */
    private void checkStaticFieldAssignment(Ast.AssignStmt assign, Ast.MemberExpr member,
            String typeName) {
        requireVisibleType(typeName, member.pos());
        staticOwners.put(member, typeName);
        TypeInfo info = userTypes.get(typeName);
        Ast.FieldDecl declared = info == null ? null : info.declared.get(member.name());
        if (declared == null) {
            error(TYPE_ERROR, assign.pos(),
                    "type " + typeName + " has no field '" + member.name() + "'");
            checkExpression(assign.value());
            return;
        }
        if (!declared.isStatic()) {
            error(TYPE_ERROR, assign.pos(), "field '" + member.name() + "' of " + typeName
                    + " is an instance member; write it through a value of type " + typeName);
            checkExpression(assign.value());
            return;
        }
        if (!declared.isPublic() && !inDeclaringType(typeName)) {
            error(TYPE_ERROR, assign.pos(), "static field '" + member.name() + "' of " + typeName
                    + " is private; declare it 'static pub mut' to write it from outside");
        }
        SolvikType fieldType = info.fields().get(member.name());
        if ("..=".equals(assign.operator())) {
            checkExpression(assign.value());
            requireAppendable(fieldType, assign.pos(), "field");
        } else {
            SolvikType value = checkExpression(assign.value());
            if (!isAssignable(value, fieldType)) {
                error(TYPE_ERROR, assign.pos(),
                        "cannot assign " + value + " to a field of type " + fieldType);
            }
        }
        if (!declared.isMutable()) {
            error(IMMUTABLE_MEMBER, assign.pos(), "static field '" + member.name() + "' of "
                    + typeName + " is immutable; declare it 'static mut' to write it");
        }
    }

    /** Checks {@code Type.f(...)}, a static function call (§3, static members). */
    private SolvikType checkStaticMethodCall(Ast.CallExpr call, Ast.MemberExpr member,
            String typeName) {
        requireVisibleType(typeName, member.pos());
        TypeInfo info = userTypes.get(typeName);
        List<Ast.FuncDecl> overloads = info == null ? List.of()
                : info.methods().getOrDefault(member.name(), List.of()).stream()
                        .filter(Ast.FuncDecl::isStatic).toList();
        Ast.FuncDecl declared = selectMethodOverload(call, SolvikType.user(typeName), overloads,
                member.name());
        if (declared == null) {
            error(TYPE_ERROR, call.pos(),
                    "type " + typeName + " has no function '" + member.name() + "'");
            for (Ast.Expr argument : call.arguments()) {
                checkExpression(argument);
            }
            return SolvikType.ANY;
        }
        if (declared.isConstructor(simpleName(typeName))) {
            error(TYPE_ERROR, call.pos(), "a constructor is called as " + typeName
                    + "(...) without a member name");
            for (Ast.Expr argument : call.arguments()) {
                checkExpression(argument);
            }
            return SolvikType.ANY;
        }
        if (!declared.isStatic()) {
            error(TYPE_ERROR, call.pos(), "function '" + member.name() + "' of " + typeName
                    + " is an instance member; call it on a value of type " + typeName);
        }
        staticOwners.put(call, typeName);
        staticOwners.put(member, typeName);
        return checkUserMethod(call, SolvikType.user(typeName), declared, member.name(),
                inDeclaringType(typeName));
    }

    /** Collects the type arguments a declared parameter type forces on {@code actual} (§2). */
    private static void matchTypeArgument(SolvikType declared, SolvikType actual,
                                          List<String> parameters, Map<String, SolvikType> into) {
        if (declared.isVariable() && parameters.contains(declared.userName())) {
            into.putIfAbsent(declared.userName(), actual);
            return;
        }
        if (declared.isArray() && actual.isArray()) {
            matchTypeArgument(declared.element(), actual.element(), parameters, into);
            return;
        }
        if (declared.kind() == SolvikType.Kind.USER && actual.kind() == SolvikType.Kind.USER
                && declared.userName().equals(actual.userName())
                && declared.arguments().size() == actual.arguments().size()) {
            for (int i = 0; i < declared.arguments().size(); i++) {
                matchTypeArgument(declared.arguments().get(i), actual.arguments().get(i),
                        parameters, into);
            }
        }
    }

    /**
     * A module-qualified name used as a value (§2, "Modules").
     *
     * <p>A module holds no values, so the only meaningful use of {@code geometry::Point} is as the
     * target of a construction call, which {@link #checkCall} handles before reaching this point.
     */
    private SolvikType checkQualifiedName(Ast.QualifiedNameExpr qualified) {
        String key = qualified.qualifiedName();
        if (userTypes.containsKey(key)) {
            error(TYPE_ERROR, qualified.pos(), "'" + key
                    + "' is a type or function name; call it with " + key + "(...)");
        } else if (!declaredModules.containsKey(qualified.module())) {
            error(TYPE_ERROR, qualified.pos(), "unknown module '" + qualified.module() + "'");
        } else {
            error(TYPE_ERROR, qualified.pos(), "module '" + qualified.module()
                    + "' declares no type or function '" + qualified.name() + "'");
        }
        return SolvikType.ANY;
    }

    private SolvikType checkOuterName(Ast.OuterExpr outer) {
        Binding binding = scope.lookupOuter(outer.name());
        if (binding == null) {
            error(TYPE_ERROR, outer.pos(),
                    "no outer '" + outer.name() + "' is in scope; 'outer' reaches one scope out");
            return SolvikType.ANY;
        }
        bindings.put(outer, binding);
        SolvikType proven = narrowed.get(binding);
        return proven != null ? proven : binding.type();
    }

    /**
     * Resolves a bare name inside a type body to that type's member (§3, "Member names").
     *
     * <p>A local or parameter always wins, so {@code self.} is needed only to reach a member whose
     * name is taken by a local. Outside a type body a bare name is still unknown.
     */
    private SolvikType checkBareMember(Ast.NameExpr name) {
        String field = bareMemberField(name.name());
        if (field == null) {
            return null;
        }
        String selfType = selfTypeName();
        String typeName = selfType != null ? selfType : currentTypeName;
        TypeInfo info = userTypes.get(typeName);
        Ast.FieldDecl declared = info == null ? null : info.declared.get(field);
        if (declared != null && declared.isStatic()) {
            staticOwners.put(name, typeName);
        } else if (selfType != null) {
            implicitMembers.put(name, field);
        } else {
            error(TYPE_ERROR, name.pos(), "field '" + field
                    + "' is an instance member; a static function has no 'self'");
            return SolvikType.ANY;
        }
        return info.fields().get(field);
    }

    /** The enclosing type's field with this name, or {@code null} when there is none. */
    private String bareMemberField(String name) {
        String selfType = selfTypeName();
        String typeName = selfType != null ? selfType
                : (staticContext ? currentTypeName : null);
        if (typeName == null) {
            return null;
        }
        TypeInfo info = userTypes.get(typeName);
        return info != null && info.fields().containsKey(name) ? name : null;
    }

    private SolvikType checkName(Ast.NameExpr name) {
        Binding binding = scope.lookup(name.name());
        if (binding == null) {
            SolvikType member = checkBareMember(name);
            if (member != null) {
                return member;
            }
            if (staticContext && "self".equals(name.name())) {
                error(TYPE_ERROR, name.pos(),
                        "a static function has no 'self'; declare the value as a parameter");
            } else {
                error(TYPE_ERROR, name.pos(), "unknown name '" + name.name() + "'");
            }
            return SolvikType.ANY;
        }
        bindings.put(name, binding);
        SolvikType proven = narrowed.get(binding);
        return proven != null ? proven : binding.type();
    }

    private SolvikType checkUnary(Ast.UnaryExpr unary) {
        if ("-".equals(unary.operator()) && unary.operand() instanceof Ast.IntLit literal
                && literal.suffix().isExplicit()) {
            // The sign belongs to a suffixed literal, so -128B is a Byte exactly as Byte(-128) is.
            SolvikType type = checkIntegerLiteral(literal, true);
            expressionTypes.put(literal, type);
            return type;
        }
        if ("-".equals(unary.operator()) && unary.operand() instanceof Ast.IntLit literal
                && "2147483648".equals(literal.text())) {
            expressionTypes.put(literal, SolvikType.INTEGER);
            return SolvikType.INTEGER;
        }
        SolvikType operand = requireNonNull(checkExpression(unary.operand()),
                unary.operand().pos(), "unary '" + unary.operator() + "'");
        if ("-".equals(unary.operator())) {
            if (!operand.isNumeric()) {
                error(TYPE_ERROR, unary.pos(), "unary '-' needs a numeric operand, found " + operand);
                return SolvikType.INTEGER;
            }
            return operand == SolvikType.BYTE || operand == SolvikType.SHORT ? SolvikType.INTEGER : operand;
        }
        if (operand != SolvikType.BOOLEAN) {
            error(TYPE_ERROR, unary.pos(), "'!' needs a Boolean operand, found " + operand);
        }
        return SolvikType.BOOLEAN;
    }

    private SolvikType checkBinary(Ast.BinaryExpr binary) {
        String op = binary.operator();
        if ("&&".equals(op) || "||".equals(op)) {
            requireBooleanOperand(binary.left(), op);
            withFacts(nullFacts(binary.left(), "&&".equals(op)),
                    () -> requireBooleanOperand(binary.right(), op));
            return SolvikType.BOOLEAN;
        }
        SolvikType left = checkExpression(binary.left());
        SolvikType right = checkExpression(binary.right());
        switch (op) {
            case "+", "-", "*", "/", "%", "<", "<=", ">", ">=" -> {
                SolvikType checkedLeft = requireNonNull(left, binary.left().pos(),
                        "operator '" + op + "'");
                SolvikType checkedRight = requireNonNull(right, binary.right().pos(),
                        "operator '" + op + "'");
                SolvikType widest = SolvikType.widest(checkedLeft, checkedRight);
                if (widest == null) {
                    error(TYPE_ERROR, binary.pos(),
                            "operator '" + op + "' needs numeric operands, found "
                                    + left + " and " + right);
                    return "+".equals(op) ? SolvikType.INTEGER : SolvikType.BOOLEAN;
                }
                if ("<".equals(op) || "<=".equals(op) || ">".equals(op) || ">=".equals(op)) {
                    return SolvikType.BOOLEAN;
                }
                if ("%".equals(op) && widest == SolvikType.BIG_DECIMAL) {
                    error(TYPE_ERROR, binary.pos(), "'%' is not defined for BigDecimal (§4.4)");
                } else {
                    checkLiteralArithmetic(binary, op, widest);
                }
                return widest;
            }
            case "==", "!=", "===" -> {
                // Equality is null-safe (§4.5): a Null operand is comparable with anything, and a
                // nullable operand is compared by value with Java null standing for Null.
                boolean comparable = left.isNumeric() && right.isNumeric()
                        || left.isNull() || right.isNull()
                        || left.kind() == SolvikType.Kind.USER
                                && right.kind() == SolvikType.Kind.USER
                        || isAssignable(left, right) || isAssignable(right, left);
                if (!comparable) {
                    error(TYPE_ERROR, binary.pos(),
                            "cannot compare " + left + " with " + right);
                }
                return SolvikType.BOOLEAN;
            }
            case ".." -> {
                return SolvikType.STRING;
            }
            default -> {
                error(TYPE_ERROR, binary.pos(), "unknown operator '" + op + "'");
                return SolvikType.ANY;
            }
        }
    }

    /** Checks one operand of {@code &&} or {@code ||}: a non-null Boolean (§4.3). */
    private SolvikType requireBooleanOperand(Ast.Expr operand, String operator) {
        SolvikType type = checkExpression(operand);
        if (type == SolvikType.BOOLEAN) {
            return type;
        }
        if (type.kind() == SolvikType.Kind.BOOLEAN) {
            error(NULL_UNSAFE, operand.pos(), "'" + operator + "' needs a non-null Boolean, found "
                    + type + "; compare it with Null and use it where a value is present (§7.1)");
        } else {
            error(TYPE_ERROR, operand.pos(),
                    "'" + operator + "' needs a Boolean operand, found " + type);
        }
        return null;
    }

    /**
     * Rejects arithmetic that cannot succeed for the literal operands given (§10).
     *
     * <p>Only the cases the compiler can prove are errors: a zero divisor, and a Decimal division
     * whose result has no terminating decimal expansion.
     */
    private void checkLiteralArithmetic(Ast.BinaryExpr binary, String op, SolvikType widest) {
        if ((!"/".equals(op) && !"%".equals(op)) || widest == SolvikType.FLOAT
                || widest == SolvikType.DOUBLE) return;
        BigDecimal divisor = numericValue(binary.right());
        if (divisor == null) {
            return;
        }
        if (divisor.signum() == 0) {
            error(STATIC_FAILURE, binary.pos(), "'" + op + "' by zero always fails");
            return;
        }
        if (!"/".equals(op) || widest != SolvikType.BIG_DECIMAL) {
            return;
        }
        BigDecimal dividend = numericValue(binary.left());
        if (dividend == null) {
            return;
        }
        try {
            dividend.divide(divisor); // exact only, matching the run-time rule (§7)
        } catch (ArithmeticException notTerminating) {
            error(STATIC_FAILURE, binary.pos(),
                    dividend + " / " + divisor + " has no terminating decimal expansion");
        }
    }

    /** A literal's exact numeric value, or {@code null} when the expression is not a literal. */
    private static BigDecimal numericValue(Ast.Expr expression) {
        if (expression instanceof Ast.IntLit literal) {
            return new BigDecimal(new BigInteger(literal.text()));
        }
        if (expression instanceof Ast.DecimalLit literal) {
            return new BigDecimal(literal.text());
        }
        if (expression instanceof Ast.UnaryExpr unary && "-".equals(unary.operator())) {
            BigDecimal operand = numericValue(unary.operand());
            return operand == null ? null : operand.negate();
        }
        // A conversion of a literal is itself a literal: `Decimal(1) / 3` cannot terminate either.
        if (expression instanceof Ast.CallExpr call
                && call.callee() instanceof Ast.NameExpr name
                && call.arguments().size() == 1) {
            Ast.Expr argument = call.arguments().getFirst();
            SolvikType converted = SolvikType.named(name.name());
            boolean integral = converted != null && converted.isIntegral();
            boolean foldable = argument instanceof Ast.IntLit
                    || (!integral && argument instanceof Ast.DecimalLit);
            if (foldable && ("BigDecimal".equals(name.name()) || integral)) {
                return numericValue(argument);
            }
        }
        return null;
    }

    /**
     * Checks an integer-spelled literal and returns the type it has (§4).
     *
     * <p>An uppercase suffix names the type outright; without one the literal is an
     * {@code Integer}. A carrier that cannot hold the written value is rejected here, because the
     * conversion could never succeed (§10).
     */
    private SolvikType checkIntegerLiteral(Ast.IntLit literal) {
        return checkIntegerLiteral(literal, false);
    }

    /**
     * @param negated whether the literal is the operand of a unary {@code -}, which folds into a
     *                suffixed literal so {@code -128B} and {@code -9223372036854775808L} exist
     */
    private SolvikType checkIntegerLiteral(Ast.IntLit literal, boolean negated) {
        SolvikType explicit = suffixedType(literal.suffix());
        SolvikType type = explicit == null ? SolvikType.INTEGER : explicit;
        BigInteger value = new BigInteger(literal.text());
        if (negated) {
            value = value.negate();
        }
        if (type == SolvikType.BIG_INTEGER) {
            return type; // arbitrary precision: every digit string is a value
        }
        if (!type.isIntegral()) {
            checkFloatingLiteralRange(type, new BigDecimal(value), literal.pos());
            return type;
        }
        checkLiteralRange(type, value, literal.pos());
        return type;
    }

    /**
     * Checks a fraction-spelled literal and returns the type its suffix names, or {@code Double}.
     *
     * <p>A fractional literal can never become an integral carrier, so that combination is a
     * compile error rather than a truncation (§10).
     */
    private SolvikType checkDecimalLiteral(Ast.DecimalLit literal) {
        SolvikType explicit = suffixedType(literal.suffix());
        SolvikType type = explicit == null ? SolvikType.DOUBLE : explicit;
        if (type.isIntegral()) {
            error(TYPE_ERROR, literal.pos(), "a fractional literal cannot be a " + type
                    + " literal; write a whole number or convert explicitly");
            return SolvikType.DOUBLE;
        }
        if (type != SolvikType.BIG_DECIMAL) {
            checkFloatingLiteralRange(type, new BigDecimal(literal.text()), literal.pos());
        }
        return type;
    }

    /** The numeric carrier a suffix names, or {@code null} when the literal has no suffix (§4). */
    private static SolvikType suffixedType(NumericSuffix suffix) {
        return switch (suffix) {
            case NONE -> null;
            case BYTE -> SolvikType.BYTE;
            case SHORT -> SolvikType.SHORT;
            case INTEGER -> SolvikType.INTEGER;
            case LONG -> SolvikType.LONG;
            case FLOAT -> SolvikType.FLOAT;
            case DOUBLE -> SolvikType.DOUBLE;
            case BIG_INTEGER -> SolvikType.BIG_INTEGER;
            case BIG_DECIMAL -> SolvikType.BIG_DECIMAL;
        };
    }

    /** Rejects a literal that its integral carrier cannot hold (§4, §10). */
    private void checkLiteralRange(SolvikType target, BigInteger value, SourcePos pos) {
        boolean fits = switch (target.kind()) {
            case BYTE -> within(value, Byte.MIN_VALUE, Byte.MAX_VALUE);
            case SHORT -> within(value, Short.MIN_VALUE, Short.MAX_VALUE);
            case INTEGER -> within(value, Integer.MIN_VALUE, Integer.MAX_VALUE);
            case LONG -> within(value, Long.MIN_VALUE, Long.MAX_VALUE);
            default -> true;
        };
        if (!fits) {
            error(STATIC_FAILURE, pos, target
                    + " literal out of range; use an explicit numeric constructor with text");
        }
    }

    private static boolean within(BigInteger value, long minimum, long maximum) {
        return value.compareTo(BigInteger.valueOf(minimum)) >= 0
                && value.compareTo(BigInteger.valueOf(maximum)) <= 0;
    }

    private void checkLiteralConversion(String target, Ast.Expr argument) {
        if ("String".equals(target)) return;
        if (argument instanceof Ast.NullLit) {
            error(STATIC_FAILURE, argument.pos(), "cannot convert null to " + target);
            return;
        }
        if (argument instanceof Ast.BoolLit) {
            if ("Boolean".equals(target)) return;
            error(STATIC_FAILURE, argument.pos(), "cannot convert Boolean to " + target);
            return;
        }
        if (argument instanceof Ast.StringLit text) {
            checkLiteralTextConversion(target, text);
            return;
        }
        SolvikType numeric = SolvikType.named(target);
        if (numeric != null && numeric.isNumeric()) {
            BigDecimal value = numericValue(argument);
            if (value != null) checkNumericRange(numeric, value, argument.pos());
        }
    }

    private void checkConversionSource(String target, SolvikType source, Ast.Expr argument) {
        if (source.isNullable() && !"String".equals(target)) {
            // `T?(value)` never asserts presence: the conversion would have to unbox Null (§4.4).
            error(TYPE_ERROR, argument.pos(), "cannot convert " + source + " to " + target
                    + "; check the value against Null first");
            return;
        }
        if (source == SolvikType.ANY || source == SolvikType.NULL || source == SolvikType.NUMBER) return;
        SolvikType numeric = SolvikType.named(target);
        boolean accepted = "String".equals(target)
                || "Boolean".equals(target) && (source == SolvikType.BOOLEAN || source == SolvikType.STRING)
                || numeric != null && numeric.isNumeric() && (source.isNumeric() || source == SolvikType.STRING);
        if (!accepted) error(TYPE_ERROR, argument.pos(), "cannot convert " + source + " to " + target);
    }

    private void checkLiteralTextConversion(String target, Ast.StringLit text) {
        String value = text.value();
        if ("Boolean".equals(target)) {
            if (!ConversionText.isBoolean(value)) {
                error(STATIC_FAILURE, text.pos(), "cannot convert '" + value + "' to Boolean");
            }
            return;
        }
        SolvikType numeric = SolvikType.named(target);
        if (numeric == null || !numeric.isNumeric()) return;
        boolean floatingSpecial = (numeric == SolvikType.FLOAT || numeric == SolvikType.DOUBLE)
                && Set.of("NaN", "Infinity", "+Infinity", "-Infinity").contains(value);
        if (floatingSpecial) return;
        if (!(numeric.isIntegral() ? ConversionText.isInteger(value) : ConversionText.isDecimal(value))) {
            error(STATIC_FAILURE, text.pos(), "cannot convert '" + value + "' to " + target);
            return;
        }
        try { checkNumericRange(numeric, new BigDecimal(value), text.pos()); }
        catch (NumberFormatException invalid) { error(STATIC_FAILURE, text.pos(), "cannot convert to " + target); }
    }

    private void checkNumericRange(SolvikType target, BigDecimal value, SourcePos pos) {
        if (!target.isIntegral()) return;
        try {
            BigInteger integer = value.toBigIntegerExact();
            switch (target.kind()) {
                case BYTE -> integer.byteValueExact(); case SHORT -> integer.shortValueExact();
                case INTEGER -> integer.intValueExact(); case LONG -> integer.longValueExact();
                default -> { }
            }
        } catch (ArithmeticException invalid) {
            error(STATIC_FAILURE, pos, "cannot convert " + value + " to " + target + " without loss or overflow");
        }
    }

    private SolvikType checkIndex(Ast.IndexExpr index) {
        SolvikType target = requireNonNull(checkExpression(index.target()),
                index.target().pos(), "array indexing");
        if (!target.isArray()) {
            error(TYPE_ERROR, index.pos(), "indexing needs an array, found " + target);
            return SolvikType.ANY;
        }
        SolvikType indexType = requireNonNull(checkExpression(index.index()),
                index.index().pos(), "an array index");
        if (indexType != SolvikType.INTEGER && indexType != SolvikType.BYTE
                && indexType != SolvikType.SHORT) {
            error(TYPE_ERROR, index.index().pos(),
                    "an index must be an Integer, Byte, or Short, found " + indexType);
        } else {
            BigDecimal literal = numericValue(index.index());
            if (literal != null && literal.signum() < 0) {
                error(STATIC_FAILURE, index.index().pos(),
                        "an index cannot be negative, found " + literal);
            }
        }
        return target.element();
    }

    /**
     * Checks an array literal {@code T[N] { ... } } (§5.1).
     *
     * <p>A non-nullable element type needs exactly {@code N} elements, so no uninitialized element
     * can be read as a value. A nullable element type may list fewer than {@code N}: the elements the
     * literal omits are {@code Null} by the construction rule, which is a property of the array
     * expression itself and not an implicit initializer for a declaration.
     */
    private SolvikType checkArrayLiteral(Ast.ArrayLit literal) {
        SolvikType arrayType = resolveType(literal.arrayType());
        SolvikType elementType = arrayType.element();
        if (literal.arrayType().nullable()) {
            error(TYPE_ERROR, literal.pos(),
                    "an array literal constructs a non-null array; write '" + arrayType.asNonNull()
                            + " {...}' and declare the nullable array with ': " + arrayType
                            + " = Null'");
        }
        if (arrayType.length() != null) {
            int listed = literal.elements().size();
            if (!elementType.isNullable() && listed != arrayType.length()) {
                error(TYPE_ERROR, literal.pos(), "an array of " + arrayType.length()
                        + " non-null " + elementType + " needs exactly " + arrayType.length()
                        + " element(s), found " + listed + " (§5.1)");
            } else if (listed > arrayType.length()) {
                error(TYPE_ERROR, literal.pos(), "an array of length " + arrayType.length()
                        + " cannot list " + listed + " elements (§5.1)");
            }
        }
        for (Ast.Expr element : literal.elements()) {
            // Context comes before default Integer validation, including every node in a signed
            // numeric literal tree. Nonliteral expressions still obey type-stable assignment.
            if (elementType.isNumeric() && adoptsElementType(element, elementType)) {
                BigDecimal value = numericValue(element);
                checkNumericRange(elementType, value, element.pos());
                checkFloatingLiteralRange(elementType, value, element.pos());
                recordLiteralType(element, elementType);
                continue;
            }
            SolvikType type = checkExpression(element);
            if (!isAssignable(type, elementType)) {
                error(TYPE_ERROR, element.pos(),
                        "cannot place " + type + " in an array of " + elementType);
            }
        }
        return arrayType.asNonNull();
    }

    /** Java floating literals must be finite and must not underflow to zero. Conversions may do so. */
    private void checkFloatingLiteralRange(SolvikType type, BigDecimal value, SourcePos pos) {
        if (type != SolvikType.FLOAT && type != SolvikType.DOUBLE) return;
        double rounded = type == SolvikType.FLOAT ? value.floatValue() : value.doubleValue();
        if (Double.isInfinite(rounded) || rounded == 0.0 && value.signum() != 0) {
            error(STATIC_FAILURE, pos, "numeric literal is outside " + type
                    + "'s literal range; use an explicit conversion with text");
        }
    }

    private void recordLiteralType(Ast.Expr literal, SolvikType type) {
        expressionTypes.put(literal, type);
        if (literal instanceof Ast.UnaryExpr unary) recordLiteralType(unary.operand(), type);
    }

    /**
     * True when a literal can adopt {@code target} as its own type (§3).
     *
     * <p>A suffixed literal never adopts the element type: the suffix already named its type, so an
     * array of a different carrier reports the ordinary "cannot place" error instead of silently
     * retyping the literal.
     */
    private static boolean adoptsElementType(Ast.Expr element, SolvikType target) {
        return switch (element) {
            case Ast.IntLit literal -> !literal.suffix().isExplicit() && target.isNumeric();
            case Ast.DecimalLit literal -> !literal.suffix().isExplicit() && target.isNumeric()
                    && !target.isIntegral();
            case Ast.UnaryExpr unary -> "-".equals(unary.operator()) && adoptsElementType(unary.operand(), target);
            case Ast.BoolLit ignored -> target == SolvikType.BOOLEAN;
            case Ast.StringLit ignored -> target == SolvikType.STRING;
            default -> false;
        };
    }

    /** Reject a numeric literal that cannot fit its conversion target. */
    private void checkByteRange(Ast.Expr element, SolvikType target) {
        BigDecimal value = numericValue(element);
        if (value != null && target.isNumeric()) checkNumericRange(target, value, element.pos());
    }

    private SolvikType checkCall(Ast.CallExpr call) {
        if (call.callee() instanceof Ast.NullLit) {
            error(TYPE_ERROR, call.pos(),
                    "'Null' is a value, not a constructor; write 'Null' without parentheses (§2.1)");
            call.arguments().forEach(this::checkExpression);
            return SolvikType.NULL;
        }
        if (call.callee() instanceof Ast.TypeApplyExpr application) {
            String written = application.target() instanceof Ast.NameExpr name ? name.name()
                    : ((Ast.QualifiedNameExpr) application.target()).qualifiedName();
            String key = resolveUserName(written);
            TypeInfo info = key == null ? null : userTypes.get(key);
            if (info == null || info.declaration().nativeClass() == null) {
                error(WRITTEN_TYPE_ARGUMENT, application.pos(),
                        "explicit constructor type arguments require a native type");
                return SolvikType.ANY;
            }
            return checkConstruction(call, key);
        }
        if (call.callee() instanceof Ast.NameExpr name) {
            return checkNamedCall(call, name);
        }
        if (call.callee() instanceof Ast.QualifiedNameExpr qualified) {
            String key = qualified.qualifiedName();
            if (userTypes.containsKey(key)) {
                return checkConstruction(call, key);
            }
            if (functions.containsKey(key)) {
                return checkFunctionCall(call, key);
            }
            checkQualifiedName(qualified);
            return SolvikType.ANY;
        }
        if (call.callee() instanceof Ast.MemberExpr member) {
            String traitName = namedType(member.target());
            TypeInfo trait = traitName == null ? null : userTypes.get(traitName);
            if (trait != null && trait.declaration().isTrait()) {
                return checkTraitDefaultCall(call, member, traitName, trait);
            }
            return checkMemberCall(call, member);
        }
        error(TYPE_ERROR, call.pos(), "this expression cannot be called");
        return SolvikType.ANY;
    }

    private record Overload(Ast.FuncDecl declaration, List<SolvikType> parameters) { }

    /** Selects one applicable declaration independent of source order. */
    private Ast.FuncDecl selectOverload(Ast.CallExpr call, List<Overload> overloads,
            String displayName) {
        return selectOverload(call, call.arguments(), overloads, displayName);
    }

    /** Selects an overload using an explicit argument slice, such as after a default receiver. */
    private Ast.FuncDecl selectOverload(Ast.CallExpr call, List<Ast.Expr> writtenArguments,
            List<Overload> overloads, String displayName) {
        List<SolvikType> arguments = new ArrayList<>();
        for (Ast.Expr argument : writtenArguments) {
            SolvikType type = checkExpression(argument);
            arguments.add(type);
            prechecked.put(argument, type);
        }
        List<Overload> applicable = overloads.stream()
                .filter(candidate -> candidate.parameters().size() == arguments.size())
                .filter(candidate -> {
                    for (int i = 0; i < arguments.size(); i++) {
                        SolvikType parameter = candidate.parameters().get(i);
                        if (!parameter.isVariable()
                                && !isAssignable(arguments.get(i), parameter)) return false;
                    }
                    return true;
                }).toList();
        if (applicable.isEmpty()) {
            error(TYPE_ERROR, call.pos(), "no overload of '" + displayName
                    + "' accepts (" + arguments.stream().map(Object::toString)
                            .collect(Collectors.joining(", ")) + ")");
            return overloads.isEmpty() ? null : overloads.getFirst().declaration();
        }
        List<Overload> best = applicable.stream().filter(candidate -> applicable.stream()
                .noneMatch(other -> other != candidate && moreSpecific(other, candidate))).toList();
        if (best.size() != 1) {
            error(JavaMathMethods.AMBIGUOUS, call.pos(), "call to '" + displayName
                    + "' is ambiguous between " + best.stream()
                            .map(item -> signature(item.declaration()))
                            .collect(Collectors.joining(" and ")));
            return best.getFirst().declaration();
        }
        Overload selected = best.getFirst();
        selectedOverloads.put(call, selected.declaration());
        selectedOverloadParameters.put(call, List.copyOf(selected.parameters()));
        return selected.declaration();
    }

    private boolean moreSpecific(Overload left, Overload right) {
        boolean strict = false;
        for (int i = 0; i < left.parameters().size(); i++) {
            SolvikType specific = left.parameters().get(i);
            SolvikType general = right.parameters().get(i);
            if (general.isVariable()) {
                strict |= !specific.equals(general);
                continue;
            }
            if (specific.isVariable() || !isAssignable(specific, general)) return false;
            strict |= !specific.equals(general);
        }
        return strict;
    }

    private Ast.FuncDecl selectMethodOverload(Ast.CallExpr call, SolvikType target,
            List<Ast.FuncDecl> declarations, String name) {
        return selectMethodOverload(call, call.arguments(), target, declarations, name);
    }

    private Ast.FuncDecl selectMethodOverload(Ast.CallExpr call, List<Ast.Expr> arguments,
            SolvikType target, List<Ast.FuncDecl> declarations, String name) {
        if (declarations.isEmpty()) return null;
        List<Overload> overloads = new ArrayList<>();
        for (Ast.FuncDecl declaration : declarations) {
            String owner = methodOwners.getOrDefault(declaration, target.userName());
            TypeInfo ownerInfo = userTypes.get(owner);
            Map<String, SolvikType> mapping = owner.equals(target.userName())
                    ? typeArgumentMapping(target) : Map.of();
            List<SolvikType> parameters = parameterTypes(declaration, moduleOf(owner),
                    ownerInfo == null || declaration.isStatic()
                            ? List.of() : ownerInfo.parameters()).stream()
                    .map(type -> type.substitute(mapping)).toList();
            overloads.add(new Overload(declaration, parameters));
        }
        return selectOverload(call, arguments, overloads, name);
    }

    /** Checks an explicit call to a directly implemented trait's default body. */
    private SolvikType checkTraitDefaultCall(Ast.CallExpr call,
            Ast.MemberExpr member, String traitName, TypeInfo trait) {
        String displayName = traitName + "." + member.name();
        List<Ast.Expr> arguments = call.arguments();
        if (arguments.isEmpty()) {
            error(TYPE_ERROR, call.pos(), "explicit trait default call '" + displayName
                    + "' needs self as its first argument");
        } else {
            Ast.Expr receiver = arguments.getFirst();
            SolvikType receiverType = checkExpression(receiver);
            Binding binding = bindings.get(receiver);
            boolean self = receiver instanceof Ast.NameExpr name && "self".equals(name.name())
                    && binding != null && "this".equals(binding.javaName())
                    && receiverType.kind() == SolvikType.Kind.USER
                    && receiverType.userName().equals(currentTypeName);
            if (!self) {
                error(TYPE_ERROR, receiver.pos(), "explicit trait default call '"
                        + displayName + "' requires self as its receiver");
            }
        }
        TypeInfo current = currentTypeName == null ? null : userTypes.get(currentTypeName);
        boolean instanceBody = inFunction && !staticContext && current != null
                && !current.declaration().isTrait();
        if (!instanceBody) {
            error(TYPE_ERROR, call.pos(), "explicit trait default call '"
                    + displayName
                    + "' is valid only in an instance function of a concrete type");
        } else if (!implementedTraits.getOrDefault(currentTypeName, List.of()).contains(traitName)) {
            error(TYPE_ERROR, call.pos(), "type '" + currentTypeName
                    + "' does not directly implement trait '" + traitName + "'");
        }
        List<Ast.FuncDecl> defaults = trait.methods()
                .getOrDefault(member.name(), List.of()).stream()
                .filter(Ast.FuncDecl::hasBody).toList();
        List<Ast.Expr> methodArguments = arguments.isEmpty()
                ? List.of() : arguments.subList(1, arguments.size());
        if (defaults.isEmpty()) {
            methodArguments.forEach(this::checkExpression);
            boolean declared = trait.methods().containsKey(member.name());
            error(TYPE_ERROR, call.pos(), declared
                    ? "trait function '" + traitName + "." + member.name()
                            + "' has no default body"
                    : "trait '" + traitName + "' has no function '" + member.name() + "'");
            return SolvikType.ANY;
        }
        Ast.FuncDecl selected = selectMethodOverload(call, methodArguments,
                SolvikType.user(traitName), defaults, displayName);
        if (selected != null) {
            traitDefaultCalls.put(call, new TraitDefaultCall(traitName, selected));
        }
        return selected == null ? SolvikType.ANY : methodReturnType(selected);
    }

    /** Direct methods plus inherited trait overloads, with exact concrete declarations winning. */
    private List<Ast.FuncDecl> effectiveMethods(SolvikType target, String name) {
        TypeInfo info = userTypes.get(target.userName());
        if (info == null) return List.of();
        Map<MethodSignature, Ast.FuncDecl> methods = new LinkedHashMap<>();
        Set<MethodSignature> concrete = new HashSet<>();
        for (Ast.FuncDecl method : info.methods().getOrDefault(name, List.of())) {
            MethodSignature signature = methodSignature(method);
            methods.put(signature, method);
            concrete.add(signature);
        }
        if (info.declaration().isTrait()) return List.copyOf(methods.values());
        for (String traitName : implementedTraits.getOrDefault(target.userName(), List.of())) {
            TypeInfo trait = userTypes.get(traitName);
            if (trait == null) continue;
            for (Ast.FuncDecl method : trait.methods().getOrDefault(name, List.of())) {
                MethodSignature signature = methodSignature(method);
                Ast.FuncDecl inherited = methods.get(signature);
                if (!concrete.contains(signature)
                        && (inherited == null || (!inherited.hasBody() && method.hasBody()))) {
                    methods.put(signature, method);
                }
            }
        }
        return List.copyOf(methods.values());
    }

    private Ast.FuncDecl selectFunctionOverload(Ast.CallExpr call, String functionName) {
        List<Ast.FuncDecl> declarations = functions.getOrDefault(functionName, List.of());
        List<Ast.FuncDecl> visible = declarations.stream()
                .filter(function -> isVisibleFromHere(moduleOf(functionName), function.isPublic()))
                .toList();
        List<Overload> overloads = (visible.isEmpty() ? declarations : visible).stream()
                .map(function -> new Overload(function,
                        parameterTypes(function, moduleOf(functionName), List.of())))
                .toList();
        return selectOverload(call, overloads, functionName);
    }

    /**
     * Checks a call written without {@code self.} inside a type body (§3, "Member names").
     *
     * <p>Returns {@code null} when the enclosing type has no such method, so the caller can report
     * an unknown function instead.
     */
    private SolvikType checkBareMethod(Ast.CallExpr call) {
        if (!(call.callee() instanceof Ast.NameExpr callee)) {
            return null;
        }
        String selfType = selfTypeName();
        String typeName = selfType != null ? selfType
                : (staticContext ? currentTypeName : null);
        TypeInfo info = typeName == null ? null : userTypes.get(typeName);
        List<Ast.FuncDecl> methods = info == null ? List.of()
                : effectiveMethods(info.typeFor(info.parameters().stream()
                        .map(SolvikType::variable).toList()), callee.name()).stream()
                        .filter(method -> !method.isConstructor(simpleName(typeName))).toList();
        Ast.FuncDecl method = selectMethodOverload(call,
                info == null ? SolvikType.ANY : info.typeFor(info.parameters().stream()
                        .map(SolvikType::variable).toList()), methods, callee.name());
        if (method == null) {
            return null;
        }
        if (method.isStatic()) {
            staticOwners.put(call, typeName);
        } else if (selfType != null) {
            implicitMembers.put(call, callee.name());
        } else {
            error(TYPE_ERROR, callee.pos(), "function '" + callee.name()
                    + "' is an instance member; a static function has no 'self'");
            return SolvikType.ANY;
        }
        return checkUserMethod(call, info.typeFor(info.parameters().stream()
                .map(SolvikType::variable).toList()), method, callee.name(), true);
    }

    private SolvikType checkNamedCall(Ast.CallExpr call, Ast.NameExpr callee) {
        String name = callee.name();
        List<Ast.Expr> arguments = call.arguments();
        String constructed = resolveUserName(name);
        if (constructed != null) {
            return checkConstruction(call, constructed);
        }
        if ("exit".equals(name)) {
            return checkExit(call);
        }
        if ("Exception".equals(name)) {
            return checkExceptionConstruction(call);
        }
        switch (name) {
            case "Byte", "Short", "Integer", "Long", "Float", "Double",
                    "BigInteger", "BigDecimal", "String", "Boolean" -> {
                if (arguments.size() != 1) {
                    error(TYPE_ERROR, call.pos(), "'" + name + "' converts exactly one value");
                } else {
                    SolvikType source = checkExpression(arguments.getFirst());
                    if ("Byte".equals(name)) {
                        checkByteRange(arguments.getFirst(), SolvikType.BYTE);
                    }
                    checkConversionSource(name, source, arguments.getFirst());
                    checkLiteralConversion(name, arguments.getFirst());
                }
                return switch (name) {
                    case "String" -> SolvikType.STRING;
                    case "Boolean" -> SolvikType.BOOLEAN;
                    default -> SolvikType.named(name);
                };
            }
            default -> {
                String functionName = resolveFunctionName(name);
                List<Ast.FuncDecl> overloads = functionName == null
                        ? List.of() : functions.getOrDefault(functionName, List.of());
                if (overloads.isEmpty()) {
                    SolvikType memberCall = checkBareMethod(call);
                    if (memberCall != null) {
                        return memberCall;
                    }
                    String qualified = moduleOfDeclaredType(name);
                    if (qualified != null) {
                        error(TYPE_ERROR, callee.pos(), "type '" + name + "' is declared in a "
                                + "module; write " + qualified + " instead");
                    } else {
                        error(TYPE_ERROR, callee.pos(), "unknown function '" + name + "'");
                    }
                    for (Ast.Expr argument : arguments) {
                        checkExpression(argument);
                    }
                    return SolvikType.ANY;
                }
                return checkFunctionCall(call, functionName);
            }
        }
    }

    /**
     * {@code exit()} terminates with status 0 and {@code exit(n)} with {@code n} (§3).
     *
     * <p>It has no return type, so it is only usable as a statement.
     */
    private SolvikType checkExit(Ast.CallExpr call) {
        List<Ast.Expr> arguments = call.arguments();
        if (arguments.size() > 1) {
            error(TYPE_ERROR, call.pos(), "'exit' takes at most one argument");
        }
        if (arguments.size() == 1) {
            SolvikType status = checkExpression(arguments.getFirst());
            if (status != SolvikType.INTEGER && status != SolvikType.BYTE) {
                error(TYPE_ERROR, arguments.getFirst().pos(),
                        "an exit code must be an Integer, found " + status);
            }
            if (arguments.getFirst() instanceof Ast.IntLit literal) {
                long code = Long.parseLong(literal.text());
                if (code < 0 || code > 255) {
                    error(TYPE_ERROR, literal.pos(), "exit code must be 0 to 255, found "
                            + literal.text());
                }
            }
        }
        return SolvikType.VOID;
    }

    /** Checks a construction call, resolving the constructor's signature in its own module. */
    private SolvikType checkConstruction(Ast.CallExpr call, String qualifiedName) {
        requireVisibleType(qualifiedName, call.pos());
        String callerModule = currentModule;
        currentModule = moduleOf(qualifiedName);
        try {
            return checkConstructionIn(call, qualifiedName, callerModule);
        } finally {
            currentModule = callerModule;
        }
    }

    private SolvikType checkConstructionIn(Ast.CallExpr call, String qualifiedName,
            String callerModule) {
        constructions.add(call);
        String typeName = simpleName(qualifiedName);
        TypeInfo info = userTypes.get(qualifiedName);
        if (info != null && info.declaration().isTrait()) {
            error(TYPE_ERROR, call.pos(), "trait '" + qualifiedName + "' cannot be constructed");
            currentModule = callerModule;
            call.arguments().forEach(this::checkExpression);
            return SolvikType.user(qualifiedName);
        }
        List<Ast.FuncDecl> constructors = info == null ? List.of()
                : info.methods().getOrDefault(typeName, List.of());
        Ast.FuncDecl constructor = info == null ? null
                : selectMethodOverload(call, info.typeFor(info.parameters().stream()
                        .map(SolvikType::variable).toList()), constructors, qualifiedName);
        // A constructor is a method, so it is private unless declared 'pub func' (§2, "Members").
        boolean insideOwnBody = inFunction && qualifiedName.equals(currentTypeName);
        if (constructor != null && !constructor.isPublic() && !insideOwnBody) {
            error(TYPE_ERROR, call.pos(), "the constructor of '" + qualifiedName
                    + "' is private; declare it 'pub func " + typeName
                    + "(...)' to construct it from outside");
        }
        List<Ast.Expr> arguments = call.arguments();
        if (info != null && info.declaration().nativeClass() != null) {
            List<SolvikType> actuals = call.callee() instanceof Ast.TypeApplyExpr application
                    ? application.arguments().stream().map(this::resolveType).toList()
                    : info.parameters().stream().map(ignored -> SolvikType.ANY).toList();
            if (actuals.size() != info.parameters().size()) {
                error(TYPE_ERROR, call.pos(), "wrong native type argument count for " + qualifiedName);
                return SolvikType.ANY;
            }
            Map<String, SolvikType> mapping = new LinkedHashMap<>();
            for (int i = 0; i < actuals.size(); i++) mapping.put(info.parameters().get(i), actuals.get(i));
            List<String> previous = typeParameters;
            typeParameters = info.parameters();
            List<SolvikType> expected = constructor == null ? List.of() : constructor.params().stream()
                    .map(p -> resolveType(p.type()).substitute(mapping)).toList();
            typeParameters = previous;
            currentModule = callerModule;
            if (constructor == null || arguments.size() != expected.size())
                error(TYPE_ERROR, call.pos(), "native constructor argument count mismatch for " + qualifiedName);
            for (int i = 0; i < arguments.size(); i++) {
                SolvikType actual = checkExpression(arguments.get(i));
                if (i < expected.size() && !isAssignable(actual, expected.get(i)))
                    error(TYPE_ERROR, arguments.get(i).pos(), "cannot pass " + actual + " as " + expected.get(i));
            }
            return info.typeFor(actuals);
        }
        if (info != null && !info.parameters().isEmpty()) {
            return checkGenericConstruction(call, info, constructor);
        }
        if (constructor == null) {
            error(TYPE_ERROR, call.pos(), "type " + qualifiedName
                    + " declares no constructor, so it cannot be constructed");
            currentModule = callerModule;
            for (Ast.Expr argument : arguments) {
                checkExpression(argument);
            }
            return SolvikType.user(qualifiedName);
        }
        if (constructor.params().size() != arguments.size()) {
            error(TYPE_ERROR, call.pos(),
                    qualifiedName + " takes " + constructor.params().size()
                            + " argument(s), found " + arguments.size());
        }
        List<SolvikType> paramTypes = new ArrayList<>();
        for (Ast.Param parameter : constructor.params()) {
            paramTypes.add(resolveType(parameter.type()));
        }
        currentModule = callerModule; // the arguments belong to the caller
        for (int i = 0; i < arguments.size(); i++) {
            SolvikType argumentType = checkExpression(arguments.get(i));
            if (i < paramTypes.size() && !isAssignable(argumentType, paramTypes.get(i))) {
                error(TYPE_ERROR, arguments.get(i).pos(),
                        "cannot pass " + argumentType + " as " + paramTypes.get(i));
            }
        }
        return SolvikType.user(qualifiedName);
    }

    /** Maps a used type's parameters to the arguments written at that use site. */
    private Map<String, SolvikType> typeArgumentMapping(SolvikType target) {
        TypeInfo info = userTypes.get(target.userName());
        Map<String, SolvikType> mapping = new LinkedHashMap<>();
        if (info == null) {
            return mapping;
        }
        List<SolvikType> arguments = target.arguments();
        for (int i = 0; i < info.parameters().size() && i < arguments.size(); i++) {
            mapping.put(info.parameters().get(i), arguments.get(i));
        }
        return mapping;
    }

    /**
     * Constructs a generic type, inferring its arguments from the constructor call.
     *
     * <p>Inference is deliberately narrow: an argument whose parameter is a bare type variable
     * provides that variable's argument, so {@code Box(1)} is a {@code Box<Integer>}. Anything
     * needing more than that is reported rather than guessed.
     */
    private SolvikType checkGenericConstruction(Ast.CallExpr call, TypeInfo info,
                                                Ast.FuncDecl constructor) {
        List<Ast.Expr> arguments = call.arguments();
        if (constructor == null) {
            error(TYPE_ERROR, call.pos(),
                    "type " + info.declaration().name() + " declares no constructor");
            arguments.forEach(this::checkExpression);
            typeParameters = List.of();
            return info.typeFor(info.parameters().stream().map(name -> SolvikType.ANY).toList());
        }
        List<SolvikType> argumentTypes = arguments.stream().map(this::checkExpression).toList();
        Map<String, SolvikType> inferred = new LinkedHashMap<>();
        List<String> outerParameters = typeParameters;
        typeParameters = info.parameters(); // resolve the constructor's T parameters in its own scope
        List<Ast.Param> parameters = constructor.params();
        if (parameters.size() != arguments.size()) {
            error(TYPE_ERROR, call.pos(), info.declaration().name() + " takes "
                    + parameters.size() + " argument(s), found " + arguments.size());
        }
        for (int i = 0; i < Math.min(parameters.size(), argumentTypes.size()); i++) {
            SolvikType declared = resolveType(parameters.get(i).type());
            if (declared.isVariable() && info.parameters().contains(declared.userName())) {
                SolvikType previous = inferred.putIfAbsent(declared.userName(), argumentTypes.get(i));
                if (previous != null && !previous.equals(argumentTypes.get(i))) {
                    error(TYPE_ERROR, arguments.get(i).pos(),
                            "cannot infer " + declared.userName() + ": saw both " + previous
                                    + " and " + argumentTypes.get(i));
                }
            }
        }
        List<SolvikType> argumentTypesForType = new ArrayList<>();
        for (String parameter : info.parameters()) {
            SolvikType inferredType = inferred.get(parameter);
            // An argument the call does not determine stays a variable; a declaration reports an
            // argument that no constructor argument decides (§8.2).
            argumentTypesForType.add(inferredType == null ? SolvikType.variable(parameter)
                    : inferredType);
        }
        Map<String, SolvikType> mapping = new LinkedHashMap<>();
        for (int i = 0; i < info.parameters().size(); i++) {
            mapping.put(info.parameters().get(i), argumentTypesForType.get(i));
        }
        for (int i = 0; i < Math.min(parameters.size(), argumentTypes.size()); i++) {
            SolvikType expected = resolveType(parameters.get(i).type()).substitute(mapping);
            if (!isAssignable(argumentTypes.get(i), expected)) {
                error(TYPE_ERROR, arguments.get(i).pos(),
                        "cannot pass " + argumentTypes.get(i) + " as " + expected);
            }
        }
        typeParameters = outerParameters;
        return info.typeFor(argumentTypesForType);
    }

    /**
     * Checks a String method, or returns {@code null} when {@code method} is not one (§5).
     *
     * <p>The bounds of a literal receiver with literal indices are checked here, so an impossible
     * {@code substring} is a compile error rather than an abort on the input that reaches it.
     */
    private SolvikType checkStringMethod(Ast.CallExpr call, String method) {
        List<Ast.Expr> arguments = call.arguments();
        switch (method) {
            case "size" -> {
                requireNoArguments(call, "size");
                return SolvikType.INTEGER;
            }
            case "substring" -> {
                if (arguments.size() != 1 && arguments.size() != 2) {
                    error(TYPE_ERROR, call.pos(),
                            "'substring' takes a start index, and optionally an end index");
                    arguments.forEach(this::checkExpression);
                    return SolvikType.STRING;
                }
                for (Ast.Expr argument : arguments) {
                    requireTextIndex(argument, "substring");
                }
                checkLiteralSubstring(call);
                return SolvikType.STRING;
            }
            case "indexOf" -> {
                requireTextArguments(call, 1);
                return SolvikType.INTEGER;
            }
            case "contains", "startsWith", "endsWith" -> {
                requireTextArguments(call, 1);
                return SolvikType.BOOLEAN;
            }
            case "strip", "stripLeading", "stripTrailing", "toUpperCase", "toLowerCase" -> {
                requireNoArguments(call, method);
                return SolvikType.STRING;
            }
            case "replace" -> {
                requireTextArguments(call, 2);
                return SolvikType.STRING;
            }
            case "split" -> {
                requireTextArguments(call, 1);
                checkLiteralSplit(call);
                return SolvikType.array(SolvikType.STRING);   // size-erased String[] (§6)
            }
            default -> {
                return null;
            }
        }
    }

    private void requireTextArguments(Ast.CallExpr call, int count) {
        if (call.arguments().size() != count) {
            error(TYPE_ERROR, call.pos(),
                    "'" + ((Ast.MemberExpr) call.callee()).name() + "' takes " + count
                            + " String argument(s), found " + call.arguments().size());
        }
        for (Ast.Expr argument : call.arguments()) {
            SolvikType type = checkExpression(argument);
            if (type != SolvikType.STRING) {
                error(TYPE_ERROR, argument.pos(), "expected a String, found " + type);
            }
        }
    }

    private void requireTextIndex(Ast.Expr argument, String method) {
        SolvikType type = checkExpression(argument);
        if (type != SolvikType.INTEGER && type != SolvikType.BYTE) {
            error(TYPE_ERROR, argument.pos(),
                    "a " + method + " index must be an Integer, found " + type);
        }
    }

    /** Rejects a literal substring whose indices are provably outside the literal text (§5). */
    private void checkLiteralSubstring(Ast.CallExpr call) {
        Ast.Expr receiver = ((Ast.MemberExpr) call.callee()).target();
        if (!(receiver instanceof Ast.StringLit text)) {
            return;
        }
        int length = text.value().length();
        List<Ast.Expr> arguments = call.arguments();
        BigDecimal start = numericValue(arguments.getFirst());
        BigDecimal end = arguments.size() == 2 ? numericValue(arguments.get(1)) : null;
        if (start == null || (arguments.size() == 2 && end == null)) {
            return;
        }
        if (start.signum() < 0 || start.compareTo(BigDecimal.valueOf(length)) > 0
                || (end != null && (end.signum() < 0
                        || end.compareTo(BigDecimal.valueOf(length)) > 0))) {
            error(STATIC_FAILURE, call.pos(),
                    "substring indices are outside 0.." + length);
            return;
        }
        if (end != null && start.compareTo(end) > 0) {
            error(STATIC_FAILURE, call.pos(),
                    "substring ends before it starts: " + start + " to " + end);
        }
    }

    /** Rejects a literal empty separator, which could never split anything (§5). */
    private void checkLiteralSplit(Ast.CallExpr call) {
        if (call.arguments().getFirst() instanceof Ast.StringLit separator
                && separator.value().isEmpty()) {
            error(STATIC_FAILURE, separator.pos(), "split needs a non-empty separator");
        }
    }

    private SolvikType checkMemberCall(Ast.CallExpr call, Ast.MemberExpr member) {
        String staticType = namedType(member.target());
        if (staticType != null) {
            return checkStaticMethodCall(call, member, staticType);
        }
        SolvikType target = requireNonNull(checkExpression(member.target()),
                member.target().pos(), "a member call");
        String method = member.name();
        if (target.isArray() && ARRAY_SIZE_MEMBER.equals(method)) {
            call.arguments().forEach(this::checkExpression);
            error(TYPE_ERROR, member.pos(), "an array's length is the immutable member '"
                    + ARRAY_SIZE_MEMBER + "', so write value." + ARRAY_SIZE_MEMBER
                    + " instead of value." + ARRAY_SIZE_MEMBER + "()");
            return SolvikType.INTEGER;
        }
        if (target == SolvikType.STRING) {
            SolvikType text = checkStringMethod(call, method);
            if (text != null) {
                return text;
            }
        }
        if (SolvikType.EXCEPTION.equals(target)
                && ("getCode".equals(method) || "getMessage".equals(method))) {
            requireNoArguments(call, method);
            return "getCode".equals(method) ? SolvikType.INTEGER : SolvikType.STRING;
        }
        TypeInfo nativeInfo = userTypes.get(target.userName());
        if (nativeInfo != null && nativeInfo.isStaticMethod(method)) {
            error(TYPE_ERROR, call.pos(), "function '" + method + "' of " + target
                    + " is static; call it through the type, as " + target + "." + method
                    + "(...)");
            Ast.FuncDecl selected = selectMethodOverload(call, target,
                    nativeInfo.methods().getOrDefault(method, List.of()), method);
            return checkUserMethod(call, target, selected, method, false);
        }
        if (nativeInfo != null && nativeInfo.declaration().nativeClass() != null
                && nativeInfo.methods().containsKey(method)) {
            Ast.FuncDecl selected = selectMethodOverload(call, target,
                    nativeInfo.methods().getOrDefault(method, List.of()), method);
            if (selected != null && selected.isStatic()) {
                error(TYPE_ERROR, call.pos(), "function '" + method + "' of " + target
                        + " is static; call it through the type, as " + target + "." + method
                        + "(...)");
            }
            return checkUserMethod(call, target, selected, method, false);
        }
        switch (method) {
            case "type", "toString" -> {
                if ("toString".equals(method) && !call.arguments().isEmpty()
                        && (target == SolvikType.BIG_INTEGER
                                || target == SolvikType.BIG_DECIMAL)) {
                    return checkJavaMathCall(call, target, method); // e.g. toString(radix)
                }
                requireNoArguments(call, method);
                return SolvikType.STRING;
            }
            case "hashCode" -> {
                requireNoArguments(call, method);
                return SolvikType.INTEGER;
            }
            case "equals" -> {
                if (call.arguments().size() != 1) {
                    error(TYPE_ERROR, call.pos(), "'equals' takes exactly one argument");
                } else {
                    checkExpression(call.arguments().getFirst());
                }
                return SolvikType.BOOLEAN;
            }
            default -> {
                if (target == SolvikType.BIG_INTEGER || target == SolvikType.BIG_DECIMAL) {
                    return checkJavaMathCall(call, target, method);
                }
                TypeInfo info = userTypes.get(target.userName());
                List<Ast.FuncDecl> overloads = info == null ? List.of()
                        : effectiveMethods(target, method);
                Ast.FuncDecl declared = selectMethodOverload(call, target, overloads, method);
                if (declared == null) {
                    error(TYPE_ERROR, call.pos(),
                            "type " + target + " has no method '" + method + "'");
                    for (Ast.Expr argument : call.arguments()) {
                        checkExpression(argument);
                    }
                    return SolvikType.ANY;
                }
                if (declared.isStatic()) {
                    error(TYPE_ERROR, call.pos(), "function '" + method + "' of " + target
                            + " is static; call it through the type, as " + target + "." + method
                            + "(...)");
                    return checkUserMethod(call, target, declared, method, false);
                }
                boolean insideOwnBody = target.kind() == SolvikType.Kind.USER
                        && inDeclaringType(target.userName());
                return checkUserMethod(call, target, declared, method, insideOwnBody);
            }
        }
    }

    /**
     * A member call on a built-in big number that is not part of the {@code Any} contract.
     *
     * <p>The value already is a {@code BigInteger} or {@code BigDecimal}, so the call is resolved to
     * a public Java method at compile time and emitted directly; no adapter object is created.
     */
    private SolvikType checkJavaMathCall(Ast.CallExpr call, SolvikType receiver, String method) {
        List<SolvikType> arguments = new ArrayList<>();
        for (Ast.Expr argument : call.arguments()) arguments.add(checkExpression(argument));
        JavaMathMethods.Lookup lookup = JavaMathMethods.resolve(receiver, method, arguments);
        if (lookup.found()) {
            javaMathCalls.put(call, lookup.method());
            SolvikType returned = JavaMathMethods.map(lookup.method().getReturnType());
            return returned == null ? SolvikType.VOID : returned;
        }
        SolvikType userMethod = checkUserMethodIfAny(call, receiver, method);
        if (userMethod != null) return userMethod;
        if (lookup.problem() != null) {
            error(lookup.problem().startsWith(JavaMathMethods.AMBIGUOUS)
                    ? JavaMathMethods.AMBIGUOUS : TYPE_ERROR, call.pos(), lookup.problem());
        } else {
            error(TYPE_ERROR, call.pos(), "type " + receiver + " has no method '" + method + "'");
        }
        return SolvikType.ANY;
    }

    /** A big-number receiver has no user methods; kept hook so the fallback reads in one place. */
    private SolvikType checkUserMethodIfAny(Ast.CallExpr call, SolvikType receiver, String method) {
        return null;
    }

    /**
     * Checks a call to a user type's method, shared by {@code self.m()}, {@code value.m()} and m().
     *
     * <p>A signature is written inside its declaring type, so its parameter and return types resolve
     * in that type's module; the arguments belong to the caller and resolve in the caller's
     * module (§2, "Modules").
     */
    private SolvikType checkUserMethod(Ast.CallExpr call, SolvikType target, Ast.FuncDecl declared,
            String method, boolean insideOwnBody) {
        String owner = methodOwners.getOrDefault(declared, target.userName());
        TypeInfo info = userTypes.get(owner);
        if (!insideOwnBody && !declared.isPublic()
                && !declared.isConstructor(simpleName(target.userName()))) {
            error(TYPE_ERROR, call.pos(), "method '" + method + "' of " + target
                    + " is private; declare it 'pub func' to call it from outside");
        }
        if (declared.params().size() != call.arguments().size()) {
            error(TYPE_ERROR, call.pos(), "'" + method + "' takes " + declared.params().size()
                    + " argument(s), found " + call.arguments().size());
        }
        Map<String, SolvikType> mapping = owner.equals(target.userName())
                ? typeArgumentMapping(target) : Map.of();
        List<String> outerParameters = typeParameters;
        String callerModule = currentModule;
        List<SolvikType> paramTypes = new ArrayList<>();
        SolvikType result;
        currentModule = moduleOf(owner);
        try {
            typeParameters = info == null ? List.of() : info.parameters();
            for (Ast.Param parameter : declared.params()) {
                paramTypes.add(resolveType(parameter.type()).substitute(mapping));
            }
            result = declared.returnType() == null
                    ? SolvikType.VOID
                    : resolveType(declared.returnType()).substitute(mapping);
        } finally {
            typeParameters = outerParameters;
            currentModule = callerModule;
        }
        for (int i = 0; i < call.arguments().size(); i++) {
            SolvikType argumentType = checkExpression(call.arguments().get(i));
            if (i < paramTypes.size() && !isAssignable(argumentType, paramTypes.get(i))) {
                error(TYPE_ERROR, call.arguments().get(i).pos(),
                        "cannot pass " + argumentType + " as " + paramTypes.get(i));
            }
        }
        return result;
    }

    /**
     * Checks a call to a top-level or module function (§2, "Modules").
     *
     * <p>The signature belongs to the declaring module, so it resolves there; the arguments belong
     * to the caller and resolve in the caller's module.
     */
    private SolvikType checkFunctionCall(Ast.CallExpr call, String functionName) {
        requireVisibleFunction(functionName, call.pos());
        Ast.FuncDecl function = selectFunctionOverload(call, functionName);
        List<Ast.Expr> arguments = call.arguments();
        functionCalls.put(call, functionName);
        if (function.params().size() != arguments.size()) {
            error(TYPE_ERROR, call.pos(), "'" + functionName + "' takes "
                    + function.params().size() + " argument(s), found " + arguments.size());
        }
        List<String> outerParameters = typeParameters;
        String callerModule = currentModule;
        List<SolvikType> paramTypes = new ArrayList<>();
        SolvikType result;
        currentModule = moduleOf(functionName);
        try {
            for (Ast.Param parameter : function.params()) {
                paramTypes.add(resolveType(parameter.type()));
            }
            result = returnTypes.getOrDefault(function, function.returnType() == null
                    ? SolvikType.VOID
                    : resolveType(function.returnType()));
        } finally {
            currentModule = callerModule;
            typeParameters = outerParameters;
        }
        for (int i = 0; i < arguments.size(); i++) {
            SolvikType argumentType = checkExpression(arguments.get(i));
            if (i < paramTypes.size() && !isAssignable(argumentType, paramTypes.get(i))) {
                error(TYPE_ERROR, arguments.get(i).pos(),
                        "cannot pass " + argumentType + " as " + paramTypes.get(i));
            }
        }
        return result;
    }

    private void requireNoArguments(Ast.CallExpr call, String method) {
        if (!call.arguments().isEmpty()) {
            error(TYPE_ERROR, call.pos(), "'" + method + "' takes no arguments");
        }
    }

    private void error(String code, SourcePos pos, String message) {
        diagnostics.error(code, pos, message);
    }

    // ------------------------------------------------------------------- scopes

    /**
     * One declaration of a variable (§3, "Scope and shadowing").
     *
     * <p>A binding is an identity, not a spelling: when a name is declared again in a nested
     * scope, the inner declaration is a different binding. Generated Java forbids shadowing a
     * local variable, so a binding also carries the name the emitter must use.
     */
    public static final class Binding {
        private final String name;
        private final int ordinal;
        private final boolean instance;
        private final boolean mutable;
        private final String generatedName;
        private final SolvikType type;

        private Binding(String name, int ordinal, boolean instance, SolvikType type) {
            this(name, ordinal, instance, type, true, null);
        }

        private Binding(String name, int ordinal, boolean instance, SolvikType type,
                boolean mutable) {
            this(name, ordinal, instance, type, mutable, null);
        }

        private Binding(String name, int ordinal, boolean instance, SolvikType type,
                boolean mutable, String generatedName) {
            this.name = name;
            this.ordinal = ordinal;
            this.instance = instance;
            this.type = type;
            this.mutable = mutable;
            this.generatedName = generatedName;
        }

        /** Whether the binding may be assigned again after it holds a value. */
        public boolean isMutable() {
            return mutable;
        }

        public String name() {
            return name;
        }

        public SolvikType type() {
            return type;
        }

        /** The name to use in generated Java: {@code self} is {@code this}, shadows get a suffix. */
        public String javaName() {
            if (instance) {
                return "this";
            }
            if (generatedName != null) {
                return generatedName;
            }
            return ordinal == 0 ? name : name + "$" + ordinal;
        }

    }

    private static final class Scope {
        private final Scope parent;
        private final Map<String, Binding> bindings = new LinkedHashMap<>();
        private final Map<String, Integer> ordinals;

        Scope(Scope parent, Map<String, Integer> ordinals) {
            this.parent = parent;
            this.ordinals = ordinals;
        }

        /** Declares a name in this scope, shadowing an enclosing binding of the same name. */
        Binding declare(String name, SolvikType type) {
            return declare(name, type, null, false);
        }

        /** Declares a source name whose checked value is stored in a distinct generated local. */
        Binding declare(String name, SolvikType type, String javaName) {
            int ordinal = ordinals.merge(name, 1, Integer::sum) - 1;
            Binding binding = new Binding(name, ordinal, false, type, true, javaName);
            bindings.put(name, binding);
            return binding;
        }

        /** Declares {@code self}, which names the instance and is emitted as {@code this}. */
        Binding declareSelf(SolvikType type) {
            Binding binding = new Binding("self", 0, true, type);
            bindings.put("self", binding);
            return binding;
        }

        Binding declare(String name, SolvikType type, Ast.VarStmt statement) {
            return declare(name, type, statement, false);
        }

        /** Declares a local with the mutability its declaration states. */
        Binding declare(String name, SolvikType type, Ast.VarStmt statement, boolean mutable) {
            int ordinal = ordinals.merge(name, 1, Integer::sum) - 1;
            Binding binding = new Binding(name, ordinal, false, type, mutable);
            bindings.put(name, binding);
            return binding;
        }

        /** The binding this scope declares, or {@code null}: used to reject redeclaration. */
        Binding local(String name) {
            return bindings.get(name);
        }

        Binding lookup(String name) {
            for (Scope scope = this; scope != null; scope = scope.parent) {
                Binding binding = scope.bindings.get(name);
                if (binding != null) {
                    return binding;
                }
            }
            return null;
        }

        /** The nearest binding of {@code name} outside this scope, which is what {@code outer} means. */
        Binding lookupOuter(String name) {
            for (Scope scope = parent; scope != null; scope = scope.parent) {
                Binding binding = scope.bindings.get(name);
                if (binding != null) {
                    return binding;
                }
            }
            return null;
        }

    }
}
