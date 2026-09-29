/*
 * Copyright (c) 2026-present Douglas Hoard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.solvik.semantic;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.solvik.ast.AstNode;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.ClassDeclNode;
import org.solvik.ast.declaration.DeclarationNode;
import org.solvik.ast.declaration.DelegateDeclNode;
import org.solvik.ast.declaration.EnumDeclNode;
import org.solvik.ast.declaration.EnumVariantNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.declaration.ConstructorDeclNode;
import org.solvik.ast.declaration.InterfaceDeclNode;
import org.solvik.ast.declaration.ParameterNode;
import org.solvik.ast.declaration.PropertyDeclNode;
import org.solvik.ast.declaration.SignatureDeclNode;
import org.solvik.ast.declaration.StaticBlockNode;
import org.solvik.ast.declaration.TypeParameterNode;
import org.solvik.ast.declaration.FunctionTypeRefNode;
import org.solvik.ast.declaration.TypeRef;
import org.solvik.ast.declaration.TypeRefNode;
import org.solvik.ast.expression.BinaryExprNode;
import org.solvik.ast.expression.BinaryOperator;
import org.solvik.ast.expression.BlockExprNode;
import org.solvik.ast.expression.BoolLiteralNode;
import org.solvik.ast.expression.CallExprNode;
import org.solvik.ast.expression.CastExprNode;
import org.solvik.ast.expression.PropagationExprNode;
import org.solvik.ast.expression.CharacterLiteralNode;
import org.solvik.ast.expression.ExpressionNode;
import org.solvik.ast.expression.FloatingLiteralNode;
import org.solvik.ast.expression.IfExprNode;
import org.solvik.ast.expression.IntegerLiteralNode;
import org.solvik.ast.expression.LiteralNode;
import org.solvik.ast.expression.LongLiteralNode;
import org.solvik.ast.expression.MapEntryExprNode;
import org.solvik.ast.expression.MatchBranchNode;
import org.solvik.ast.expression.AnonymousFunctionExprNode;
import org.solvik.ast.expression.CaptureItem;
import org.solvik.ast.expression.MatchExprNode;
import org.solvik.ast.expression.MemberAccessExprNode;
import org.solvik.ast.expression.NameRefExprNode;
import org.solvik.ast.expression.NamespaceAccessExprNode;
import org.solvik.ast.expression.NullLiteralNode;
import org.solvik.ast.expression.ParenExprNode;
import org.solvik.ast.expression.RawStringLiteralNode;
import org.solvik.ast.expression.StringLiteralNode;
import org.solvik.ast.expression.SuperExprNode;
import org.solvik.ast.expression.SwitchExprNode;
import org.solvik.ast.expression.ThisExprNode;
import org.solvik.ast.expression.TypeTestExprNode;
import org.solvik.ast.expression.UnaryExprNode;
import org.solvik.ast.expression.UnaryOperator;
import org.solvik.ast.pattern.BindingPatternNode;
import org.solvik.ast.pattern.EnumPatternNode;
import org.solvik.ast.pattern.PatternNode;
import org.solvik.ast.pattern.WildcardPatternNode;
import org.solvik.ast.statement.AssignStmtNode;
import org.solvik.ast.statement.BindingKind;
import org.solvik.ast.statement.BlockNode;
import org.solvik.ast.statement.BreakStmtNode;
import org.solvik.ast.statement.CaseLabelNode;
import org.solvik.ast.statement.ConstantCaseLabelNode;
import org.solvik.ast.statement.ContinueStmtNode;
import org.solvik.ast.statement.ElseBranchNode;
import org.solvik.ast.statement.ExprStmtNode;
import org.solvik.ast.statement.ForInStmtNode;
import org.solvik.ast.statement.ForStmtNode;
import org.solvik.ast.statement.IfStmtNode;
import org.solvik.ast.statement.LocalDeclNode;
import org.solvik.ast.statement.RegexCaseLabelNode;
import org.solvik.ast.statement.ReturnStmtNode;
import org.solvik.ast.statement.ThrowStmtNode;
import org.solvik.ast.statement.TryStmtNode;
import org.solvik.ast.statement.StatementNode;
import org.solvik.ast.statement.SwitchCaseNode;
import org.solvik.ast.statement.SwitchStmtNode;
import org.solvik.ast.statement.WhileStmtNode;
import org.solvik.diagnostic.Diagnostic;
import org.solvik.diagnostic.DiagnosticBag;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.parser.FileScope;
import org.solvik.regex.RegexPattern;
import org.solvik.regex.RegexSyntax;
import org.solvik.source.SourceSpan;
import org.solvik.source.StringEscapes;
import org.solvik.type.AnyType;
import org.solvik.type.FunctionType;
import org.solvik.type.BooleanType;
import org.solvik.type.ByteType;
import org.solvik.type.CharacterType;
import org.solvik.type.ClassType;
import org.solvik.type.InterfaceType;
import org.solvik.type.DoubleType;
import org.solvik.type.EnumType;
import org.solvik.type.ExceptionBases;
import org.solvik.type.FloatType;
import org.solvik.type.IdentityDomain;
import org.solvik.type.IntegerType;
import org.solvik.type.BuiltinCollectionMember;
import org.solvik.type.BuiltinCollectionType;
import org.solvik.type.BuiltinCollectionTypes;
import org.solvik.type.LongType;
import org.solvik.type.NothingType;
import org.solvik.type.NullType;
import org.solvik.type.NullableType;
import org.solvik.type.NumericTypes;
import org.solvik.type.ParameterizedType;
import org.solvik.type.RegexMatchType;
import org.solvik.type.RegexType;
import org.solvik.type.ShortType;
import org.solvik.type.StringType;
import org.solvik.type.Type;
import org.solvik.type.TypeEnvironment;
import org.solvik.type.TypeJoin;
import org.solvik.type.TypeParameterType;
import org.solvik.type.UnitType;

/**
 * Static semantic analysis for the Solvik core and object language (docs/ARCHITECTURE.md pipeline
 * stages "symbol collection", "name resolution", "type resolution", and "static type checking").
 *
 * <p>The pass is intentionally backend-independent and Truffle-free: it produces a
 * {@link CheckedProgram} of compiler facts, never executable nodes or call targets. Declaration
 * collection runs first so a function body may call any declared function or construct any
 * declared class; body checking then runs per callable with the top-level declarations visible
 * through the outermost scope.
 *
 * <p>Name scopes are lexical: source-file declarations live in the outermost scope, parameters in
 * the callable scope, and every block and {@code for} statement pushes a nested scope. A nested
 * declaration may shadow an outer one, but a duplicate in the same scope is an error. Class
 * properties and methods are resolved through the receiver type, never through lexical scope.
 *
 * <p>A program with any error diagnostic yields no {@link CheckedProgram}; the caller must never
 * lower or execute it.
 */
public final class SolvikSemanticAnalyzer {

    private final TypeEnvironment typeEnvironment;
    /** The static identity domain for {@code ===}/{@code !==}, built once after declaration collection. */
    private IdentityDomain identityDomain;
    private final DiagnosticBag.Builder diagnostics = DiagnosticBag.builder();
    private final SymbolTable symbols = new SymbolTable();
    private final Map<ExpressionNode, Type> expressionTypes = new IdentityHashMap<>();
    private final Map<LocalDeclNode, VariableSymbol> localSymbols = new IdentityHashMap<>();
    private final Map<NameRefExprNode, Symbol> nameSymbols = new IdentityHashMap<>();
    private final Map<TryStmtNode, List<VariableSymbol>> catchBindings = new IdentityHashMap<>();
    private final Map<MemberAccessExprNode, PropertySymbol> propertyAccesses = new IdentityHashMap<>();
    private final Map<CallExprNode, ClassSymbol> constructorCalls = new IdentityHashMap<>();
    private final Map<CallExprNode, ResolvedMethod> methodCalls = new IdentityHashMap<>();
    /** The {@code toString()} calls that resolve to the built-in root member rather than a class method. */
    private final Set<CallExprNode> builtinToStringCalls = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<CallExprNode> builtinHashCodeCalls = Collections.newSetFromMap(new IdentityHashMap<>());
    /** The {@code equals(...)} calls that resolve to the built-in root member rather than a class method. */
    private final Set<CallExprNode> builtinEqualsCalls = Collections.newSetFromMap(new IdentityHashMap<>());
    /** The implicit immutable loop variable each range for-in declaration introduces. */
    private final Map<ForInStmtNode, VariableSymbol> forInBindings = new IdentityHashMap<>();
    /**
     * Declarations grouped by module (docs/LANGUAGE_SPEC.md section 20). A named module key holds
     * only that module's declarations; the implicit default module uses the flat {@link #functions},
     * {@link #classes}, {@link #interfaces}, {@link #enums}, and {@link #typeEnvironment} maps.
     */
    private final Map<String, ModuleContents> modules = new LinkedHashMap<>();
    /** A qualified function call resolved through a module prefix, for lowering. */
    private final Map<ExpressionNode, FunctionSymbol> qualifiedFunctionCalls = new IdentityHashMap<>();
    private final Map<String, FunctionSymbol> functions = new LinkedHashMap<>();
    private final Map<String, ClassSymbol> classes = new LinkedHashMap<>();
    private final Map<String, InterfaceSymbol> interfaces = new LinkedHashMap<>();
    private final Map<String, EnumSymbol> enums = new LinkedHashMap<>();
    private final Map<FunctionDeclNode, FunctionSymbol> declaredFunctions = new IdentityHashMap<>();
    private final Map<ClassDeclNode, ClassSymbol> declaredClasses = new IdentityHashMap<>();
    private final Map<ClassDeclNode, ClassType> classTypes = new IdentityHashMap<>();
    private final Map<EnumDeclNode, EnumSymbol> declaredEnums = new IdentityHashMap<>();
    private final Map<EnumDeclNode, EnumType> enumTypes = new IdentityHashMap<>();
    private final Map<EnumType, EnumSymbol> symbolsByEnumType = new IdentityHashMap<>();
    private final Map<InterfaceDeclNode, InterfaceSymbol> declaredInterfaces = new IdentityHashMap<>();
    private final Map<InterfaceDeclNode, InterfaceType> interfaceTypes = new IdentityHashMap<>();
    private final Map<InterfaceType, InterfaceSymbol> symbolsByInterfaceType = new IdentityHashMap<>();
    private final Map<InterfaceType, InterfaceDeclNode> interfaceDeclarationsByType = new IdentityHashMap<>();
    private final Map<InterfaceDeclNode, List<InterfaceDeclNode>> superInterfaceDeclarations = new IdentityHashMap<>();
    /** The resolved (possibly generic) {@code extends} types of each interface, for subtype edges. */
    private final Map<InterfaceDeclNode, List<Type>> resolvedSuperInterfaceTypes = new IdentityHashMap<>();
    private final Map<ClassType, ClassSymbol> symbolsByType = new IdentityHashMap<>();
    private final Map<ClassType, ClassDeclNode> declarationsByType = new IdentityHashMap<>();
    private final Map<ClassDeclNode, ClassDeclNode> superDeclarations = new IdentityHashMap<>();
    /** The resolved (possibly generic) {@code extends} type of each class. */
    private final Map<ClassDeclNode, Type> resolvedSuperTypes = new IdentityHashMap<>();
    private final Map<CallExprNode, Type> conversions = new IdentityHashMap<>();
    /** Source expressions whose value is implicitly widened to a wider numeric type (section 4). */
    private final Map<ExpressionNode, Type> coercions = new IdentityHashMap<>();
    private final Map<ExpressionNode, Type> testedTypes = new IdentityHashMap<>();
    private final Map<CallExprNode, ClassSymbol> superConstructorCalls = new IdentityHashMap<>();
    /** The enum variant constructed by a call or a value-less variant read, for lowering. */
    private final Map<ExpressionNode, EnumVariantSymbol> variantConstructions = new IdentityHashMap<>();
    /** The compiled constant of each {@code Regex} construction whose pattern is a source constant. */
    private final Map<CallExprNode, RegexPattern> regexConstants = new IdentityHashMap<>();
    /**
     * The declared function each function-reference expression names: a bare name or a module-qualified
     * name used in a value position (docs/LANGUAGE_SPEC.md section 6). Kept separate from
     * {@code nameSymbols} so an immediate call is never mistaken for a reference to a function value,
     * which is what preserves the statically resolved direct-call path.
     */
    private final Map<ExpressionNode, FunctionSymbol> functionReferences = new IdentityHashMap<>();
    /**
     * The call expressions that invoke a function value, with the function type of their callee. Lowering
     * emits an indirect invocation for each; a call absent from this map is a statically resolved direct
     * call and keeps its existing path.
     */
    private final Map<CallExprNode, FunctionType> indirectCalls = new IdentityHashMap<>();
    /**
     * The callable each anonymous function expression denotes (docs/LANGUAGE_SPEC.md section 6). The
     * expression is an expression and not a declaration, so it enters no scope and no name can reach
     * its body; this map is the only record of that body, and lowering builds one call target from it.
     */
    private final Map<AnonymousFunctionExprNode, FunctionSymbol> anonymousFunctions = new IdentityHashMap<>();
    /** The compiled pattern of each {@code switch} regex case whose label is a string literal. */
    private final Map<RegexCaseLabelNode, RegexPattern> regexCasePatterns = new IdentityHashMap<>();
    private final Map<EnumPatternNode, EnumVariantSymbol> enumPatterns = new IdentityHashMap<>();
    private final Map<BindingPatternNode, VariableSymbol> patternBindings = new IdentityHashMap<>();
    private final Map<BindingPatternNode, Type> patternBindingTypes = new IdentityHashMap<>();
    /** Types already resolved for a written type reference, so an error is reported only once. */
    private final Map<TypeRef, Type> resolvedTypes = new IdentityHashMap<>();
    /** Declared type parameters of the declaration whose member types are currently resolving. */
    private Map<String, TypeParameterType> typeParameterScope = new HashMap<>();
    /** The declared type parameters of each class and interface, in source order. */
    private final Map<ClassDeclNode, List<TypeParameterType>> classTypeParameters = new IdentityHashMap<>();
    private final Map<InterfaceDeclNode, List<TypeParameterType>> interfaceTypeParameters = new IdentityHashMap<>();
    private final Map<EnumDeclNode, List<TypeParameterType>> enumTypeParameters = new IdentityHashMap<>();
    /** Flow-sensitive non-null refinements active at the current program point, keyed by binding. */
    private Map<VariableSymbol, Type> narrowedTypes = new IdentityHashMap<>();
    /** Bindings written so far in the current callable, used to invalidate narrowing across loops. */
    private Set<VariableSymbol> writtenVariables = Collections.newSetFromMap(new IdentityHashMap<>());
    // Error-handling type graph: each class's directly declared superclass name, and the fixed-point
    // set of every class that is (transitively) a guest exception. Built once before bodies are checked.
    private Map<String, String> exceptionParents = Map.of();
    private Set<String> exceptionClassNames = Set.of();

    private FunctionSymbol currentFunction;
    private ClassSymbol currentClass;
    private InterfaceSymbol currentInterface;
    /** Expected types of the enclosing value bindings, most specific first, for type inference. */
    private final Deque<Type> expectedTypes = new ArrayDeque<>();
    private boolean checkingConstructor;
    /**
     * Whether the analyzer is currently inside a {@code static} member or a class initializer block
     * (docs/LANGUAGE_SPEC.md section 7). The owning class stays recorded for type-parameter resolution,
     * so this flag is what makes {@code this} and {@code super} resolve to nothing and be rejected.
     */
    private boolean checkingStaticMember;
    /**
     * Whether an instance receiver exists in some enclosing callable, across any anonymous-function
     * boundary. It distinguishes {@code this} inside a closure written in an instance method — a
     * receiver that a capture list would have to bind, so the honest report is an unlisted capture —
     * from {@code this} with no enclosing receiver anywhere, which is RESOL-005. It is carried through
     * a boundary and restored exactly as the receiver-tracking fields around it are.
     */
    private boolean enclosingReceiverAvailable;
    /**
     * The names of the local declarations whose initializer is currently being checked, innermost first.
     * A binding is created and marked initialized only after its initializer is checked, so a name here is
     * one whose value does not exist yet. It is consulted by exactly one rule — a capture item naming the
     * binding its own expression initializes, which the specification reports as read-before-initialization
     * rather than as an unknown name (docs/LANGUAGE_SPEC.md section 6: "listing that binding in the capture
     * list is an ordinary read-before-initialization error ({@code SOLV-TYPE-008}), because the value does
     * not exist when its initializer is evaluated").
     *
     * <p>It is deliberately not used for an ordinary read of a name under declaration. Resolution cannot
     * see such a name at all — the binding is not in scope yet — and the resulting
     * {@code SOLV-RESOL-001} is not wrong: a shadowed outer binding of the same name really is the binding
     * the read sees, and turning every shadowing declaration into a read-before-initialization error would
     * break ordinary programs to serve a rule that is only ever stated about capture items.
     */
    private final Deque<String> pendingDeclarations = new ArrayDeque<>();

    /**
     * Whether {@code name} names the innermost local binding currently being initialized. Innermost rather
     * than any match, because a nested declaration shadows an outer one whose initializer is also in
     * flight, and the nested one is the binding a capture item at this position could name.
     */
    private boolean declaringBindingNamed(String name) {
        return name.equals(pendingDeclarations.peekFirst());
    }

    /**
     * The receiver the anonymous function whose body is being checked captured through a written
     * {@code [this]} item, or {@code null} when it wrote none. Inside such a body that receiver is what
     * {@code this} means and what a nested {@code [this]} item captures, which is how one receiver reaches
     * an arbitrarily deep closure: "every intervening closure must list and forward that value
     * explicitly", and writing {@code [this]} on each of them is what threads the same value inward.
     * Null for every body that is not a closure body and for a closure that captured no receiver.
     */
    private CapturedValue currentBoundaryReceiver;
    /**
     * The capture item names that the anonymous function whose body is being checked rejected as
     * {@code var}s, so a body read or write of one reports the mutable-capture code the specification
     * assigns it. Empty outside an anonymous function body, and read only by
     * {@link #reportCapturedMutableUse}.
     */
    private Set<String> currentRejectedCaptures = Set.of();
    /**
     * Type parameters a member reference must not resolve to, because the member being analyzed is a
     * {@code static} member of the class that declares them
     * (docs/LANGUAGE_SPEC.md section 7). A static member belongs to the class itself, so it cannot see
     * the class's type parameters, which exist only per instance. A static method's own type parameters
     * are removed from this set and stay legal, exactly as in Java. Emptied everywhere else.
     */
    private Set<TypeParameterType> forbiddenTypeParameters = Set.of();
    private CallExprNode sanctionedSuperCall;
    private Set<PropertySymbol> definitelyInitialized = Collections.emptySet();
    private int loopDepth;
    /**
     * The number of enclosing loops that a {@code break} may exit. It is reset to zero on entry to
     * every {@code switch} case body, because a {@code break} may not exit a switch
     * (docs/LANGUAGE_SPEC.md section 13); a loop nested inside the case restores a breakable level.
     * {@link #loopDepth} still counts every enclosing loop so {@code continue} can target one.
     */
    private int breakDepth;
    private FunctionSymbol entryPoint;
    /** The compiler-synthesized entry point built from executable top-level statements, if any. */
    private FunctionSymbol implicitMain;
    /** The module of the file whose item is currently being collected or checked; null = default. */
    private String currentModule;
    /** The file-local prefix-to-module bindings of the current file. */
    private Map<String, String> currentPrefixes = Map.of();
    /** The module/namespace context of every resolved top-level item, keyed by node identity. */
    private Map<AstNode, FileScope> itemScopes = Map.of();

    /** The declarations of one named module. */
    private static final class ModuleContents {
        final Map<String, Symbol> symbols = new LinkedHashMap<>();
        final Map<String, Type> types = new LinkedHashMap<>();

        /** Declares a top-level symbol; false means the name is already declared in this module. */
        boolean declare(Symbol symbol) {
            return symbols.putIfAbsent(symbol.name(), symbol) == null;
        }
    }

    private SolvikSemanticAnalyzer(TypeEnvironment typeEnvironment) {
        this.typeEnvironment = Objects.requireNonNull(typeEnvironment);
    }

    /** Runs declaration collection and static checking over a parsed Solvik source file. */
    public static SemanticResult analyze(CompilationUnitNode unit) {
        return analyze(unit, Map.of());
    }

    /**
     * Runs declaration collection and static checking, using {@code itemScopes} to resolve each
     * top-level item against the module and prefixes of the physical file that declared it.
     */
    public static SemanticResult analyze(CompilationUnitNode unit, Map<AstNode, FileScope> itemScopes) {
        Objects.requireNonNull(unit, "unit");
        if (unit.hasUnresolvedIncludes()) {
            throw new IllegalArgumentException("include directives must be resolved before semantic analysis");
        }
        SolvikSemanticAnalyzer analyzer = new SolvikSemanticAnalyzer(new TypeEnvironment());
        analyzer.itemScopes = Objects.requireNonNull(itemScopes, "itemScopes");
        analyzer.collectDeclarations(unit);
        analyzer.checkBodies(unit);
        DiagnosticBag bag = analyzer.diagnostics.build();
        if (bag.hasErrors()) {
            return SemanticResult.failure(bag);
        }
        return SemanticResult.success(new CheckedProgram(unit, analyzer.functions, analyzer.classes, analyzer.interfaces, analyzer.enums, analyzer.declaredClasses, analyzer.declaredInterfaces, analyzer.declaredEnums, analyzer.expressionTypes, analyzer.localSymbols, analyzer.nameSymbols, analyzer.propertyAccesses, analyzer.constructorCalls, analyzer.methodCalls, analyzer.builtinToStringCalls, analyzer.builtinEqualsCalls, analyzer.builtinHashCodeCalls, analyzer.forInBindings, analyzer.conversions, analyzer.coercions, analyzer.testedTypes, analyzer.superConstructorCalls, analyzer.variantConstructions, analyzer.regexConstants, analyzer.functionReferences, analyzer.indirectCalls, analyzer.anonymousFunctions, analyzer.enumPatterns, analyzer.patternBindings, analyzer.patternBindingTypes, analyzer.regexCasePatterns, analyzer.qualifiedFunctionCalls, analyzer.entryPoint, analyzer.catchBindings, analyzer.exceptionClassNames, analyzer.exceptionParents));
    }

    /**
     * Sets the current module and prefix context from the given node when that node is a resolved
     * top-level item. Nested nodes leave the enclosing item's context in place, so a whole function
     * or class body is checked in the module of the file that declared it.
     */
    private void useScope(AstNode node) {
        FileScope scope = itemScopes.get(node);
        if (scope != null) {
            currentModule = scope.moduleNameOrNull();
            currentPrefixes = scope.prefixes();
        }
    }

    private ModuleContents module(String name) {
        return modules.computeIfAbsent(name, key -> new ModuleContents());
    }

    private ModuleContents currentModuleContents() {
        return currentModule == null ? null : modules.get(currentModule);
    }

    /**
     * Resolves an unqualified name: lexical locals first, then the current module's declarations,
     * then the implicit default module and the built-in prelude (docs/LANGUAGE_SPEC.md section 20).
     */
    private Optional<Symbol> resolveName(String name) {
        Optional<Symbol> local = symbols.resolveLocalChain(name);
        if (local.isPresent()) {
            return local;
        }
        ModuleContents contents = currentModuleContents();
        if (contents != null) {
            Symbol symbol = contents.symbols.get(name);
            if (symbol != null) {
                return Optional.of(symbol);
            }
        }
        return symbols.resolveInRoot(name);
    }

    /**
     * Reports a body reference to a binding of an enclosing function that the current function boundary
     * hides, and returns true when it did. The specification names this diagnostic for exactly that shape
     * — "An outer local or parameter referenced by the body but omitted from the capture list is
     * {@code SEM_UNLISTED_CAPTURE} ... reported on the body reference. ... Top-level and module-qualified
     * function declarations are globally resolved declarations rather than local state and need no
     * capture entry" — and that same passage keeps a genuine typo an unknown name, because a name no
     * enclosing function declares is hidden by nothing.
     *
     * <p>A {@code var} that the capture list <em>does</em> name never reaches here: the item is rejected as
     * a mutable capture and its name is recorded so the body reports the mutable-capture code for it, as
     * the specification requires. An unlisted {@code var} does reach here, and stays this diagnostic —
     * "Referencing the same outer `var` without listing it remains `SEM_UNLISTED_CAPTURE` at the body
     * reference; the compiler never silently converts it into a capture".
     */
    private boolean reportUnlistedCapture(String name, SourceSpan span) {
        if (!symbols.hiddenAcrossFunctionBoundary(name)) {
            return false;
        }
        error(DiagnosticCode.SEM_UNLISTED_CAPTURE, span, //
                        "an anonymous function body uses '" + name + "' from the enclosing function, which it does not capture");
        return true;
    }

    /**
     * Reports a body reference to a {@code var} that this closure's capture list names, and returns true
     * when it did. Such a name is deliberately not bound in the body's scope — binding a mirror of it
     * would be exactly the silent conversion the specification forbids — so a use of it resolves to
     * nothing and has to be classified here rather than by resolution. "Naming a `var` in a capture list
     * is {@code SEM_MUTABLE_CAPTURE} ({@code SOLV-SEM-057}), reported on that capture item, and a read or
     * write of that captured name in the body is reported with the same code": one code, two placements,
     * and the program is already rejected by the item report that precedes this one.
     */
    private boolean reportCapturedMutableUse(String name, SourceSpan span) {
        if (!currentRejectedCaptures.contains(name)) {
            return false;
        }
        error(DiagnosticCode.SEM_MUTABLE_CAPTURE, span, "an anonymous function body uses capture item '" + name + "' which names a mutable 'var' binding");
        return true;
    }

    /** A module prefix followed by a member path, e.g. {@code math.add} or {@code math.Result.Ok}. */
    private static final class QualifiedPrefix {
        final String module;
        final List<String> path;
        /**
         * Whether the step that reaches the last path element is a namespace step ({@code ::}) rather
         * than a member step ({@code .}). A static member of a class is reached with {@code .}, as in
         * {@code math::Counter.reset}, so the qualified read and call paths must be able to tell
         * {@code math::Counter.reset} from {@code math::Counter::reset}.
         */
        final boolean lastStepIsNamespace;

        QualifiedPrefix(String module, List<String> path, boolean lastStepIsNamespace) {
            this.module = module;
            this.path = path;
            this.lastStepIsNamespace = lastStepIsNamespace;
        }
    }

    /**
     * Classifies a reference rooted at a visible module prefix, or returns {@code null} when the
     * receiver chain is ordinary member access. A qualified reference requires the step immediately
     * after the prefix to be {@code ::}; a {@code .} there is member access on a value. Once the
     * prefix is reached through {@code ::}, a following {@code .} is allowed so a variant such as
     * {@code math::Result.Ok} is a path. The path holds the names after the prefix.
     */
    private QualifiedPrefix qualifiedPrefix(ExpressionNode node) {
        List<String> path = new ArrayList<>();
        List<Boolean> namespaceSteps = new ArrayList<>();
        ExpressionNode current = node;
        while (true) {
            if (current instanceof MemberAccessExprNode member) {
                if (member.isSafe()) {
                    return null;
                }
                path.add(0, member.memberName());
                namespaceSteps.add(0, false);
                current = member.receiver();
            } else if (current instanceof NamespaceAccessExprNode namespace) {
                path.add(0, namespace.memberName());
                namespaceSteps.add(0, true);
                current = namespace.receiver();
            } else {
                break;
            }
        }
        if (current instanceof NameRefExprNode name) {
            String module = currentPrefixes.get(name.name());
            if (module != null && !path.isEmpty() && namespaceSteps.get(0)) {
                return new QualifiedPrefix(module, path, namespaceSteps.get(namespaceSteps.size() - 1));
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------
    // Declaration collection
    // ---------------------------------------------------------------------------------------------

    private void collectDeclarations(CompilationUnitNode unit) {
        declareBuiltins();
        // Pass A: register every class's and interface's nominal type first so any declaration order
        // may reference a nominal type by name in property, parameter, return, and local types.
        for (DeclarationNode declaration : unit.declarations()) {
            useScope(declaration);
            if (declaration instanceof ClassDeclNode classDeclaration) {
                ClassType type = new ClassType(classDeclaration.name());
                classTypes.put(classDeclaration, type);
                declarationsByType.put(type, classDeclaration);
                declareNominalType(type, classDeclaration.span());
                List<TypeParameterType> typeParameters = declareTypeParameters(classDeclaration.typeParameters());
                classTypeParameters.put(classDeclaration, typeParameters);
                type.resolveTypeParameters(typeParameters);
            } else if (declaration instanceof InterfaceDeclNode interfaceDeclaration) {
                InterfaceType type = new InterfaceType(interfaceDeclaration.name());
                interfaceTypes.put(interfaceDeclaration, type);
                interfaceDeclarationsByType.put(type, interfaceDeclaration);
                declareNominalType(type, interfaceDeclaration.span());
                List<TypeParameterType> typeParameters = declareTypeParameters(interfaceDeclaration.typeParameters());
                interfaceTypeParameters.put(interfaceDeclaration, typeParameters);
                type.resolveTypeParameters(typeParameters);
            } else if (declaration instanceof EnumDeclNode enumDeclaration) {
                EnumType type = new EnumType(enumDeclaration.name());
                enumTypes.put(enumDeclaration, type);
                declareNominalType(type, enumDeclaration.span());
                List<TypeParameterType> typeParameters = declareTypeParameters(enumDeclaration.typeParameters());
                enumTypeParameters.put(enumDeclaration, typeParameters);
                type.resolveTypeParameters(typeParameters);
            }
        }
        // Pass A2: resolve `extends` clauses and interface-extension lists, reject cycles, and install
        // supertypes and interface edges before any subclass or conforming class is collected.
        resolveSuperclasses(unit);
        resolveInterfaceExtensions(unit);
        // Pass B: top-level functions.
        for (DeclarationNode declaration : unit.declarations()) {
            if (declaration instanceof FunctionDeclNode function) {
                collectFunction(function);
            }
        }
        // Pass C: interface members in extended-first order so a class can inspect the full contract.
        for (InterfaceDeclNode interfaceDeclaration : interfaceExtensionOrder(unit)) {
            collectInterface(interfaceDeclaration, interfaceTypes.get(interfaceDeclaration));
        }
        // The guest exception graph is built here (before class members are collected) from the purely
        // syntactic superclass names, so collectClass can reject a member that would collide with the
        // synthesized message field/accessor of a guest exception type (docs/LANGUAGE_SPEC.md section 22).
        buildExceptionGraph(unit);
        // Pass D: class members in superclass-first order so a subclass can inspect its superclass.
        for (ClassDeclNode classDeclaration : inheritanceOrder(unit)) {
            collectClass(classDeclaration, classTypes.get(classDeclaration));
        }
        // Pass E: enum variants. A variant's value types may reference any nominal type, all of
        // which Pass A already registered, so enum order does not matter.
        for (DeclarationNode declaration : unit.declarations()) {
            if (declaration instanceof EnumDeclNode enumDeclaration) {
                collectEnum(enumDeclaration, enumTypes.get(enumDeclaration));
            }
        }
        // Pass F: the closed subtype set of every sealed class, once all classes exist.
        resolveSealedSubtypes(unit);
        // (The guest exception graph was already built before Pass D, so exception classification is
        // available both to member-reservation checks and to throw/catch validation in checkBodies.)
        collectImplicitMain(unit);
    }

    /**
     * Builds the implicit {@code func main(): Unit} from a file's executable top-level statements
     * (docs/LANGUAGE_SPEC.md section 6). The statements become the body of a compiler-synthesized
     * top-level function named {@code main}, which is the only executable entry point; a file with no
     * top-level statements has no entry point and does nothing.
     */
    private void collectImplicitMain(CompilationUnitNode unit) {
        List<StatementNode> statements = unit.statements();
        if (statements.isEmpty()) {
            return;
        }
        SourceSpan first = statements.get(0).span();
        SourceSpan last = statements.get(statements.size() - 1).span();
        // The merged implicit main may span several physical files; only synthesize a covering span
        // when both ends belong to the same source. Otherwise anchor it at the first statement.
        SourceSpan span = first.sourceId() == last.sourceId() ? SourceSpan.of(first.sourceId(), first.startOffset(), last.endOffset()) : first;
        BlockNode body = new BlockNode(statements, span);
        FunctionDeclNode declaration = new FunctionDeclNode(false, false, "main", List.of(), List.of(), new TypeRefNode("Unit", span), body, span);
        FunctionSymbol symbol = new FunctionSymbol("main", span, List.of(), List.of(), UnitType.INSTANCE, true, declaration);
        declaredFunctions.put(declaration, symbol);
        functions.put("main", symbol);
        symbols.declare(symbol);
        implicitMain = symbol;
        entryPoint = symbol;
    }

    /** Registers a user-declared nominal type, rejecting a duplicate or built-in name shadow. */
    private void declareNominalType(Type type, SourceSpan span) {
        if (currentModule == null) {
            if (typeEnvironment.resolve(type.name()).isPresent()) {
                error(DiagnosticCode.RESOL_DUPLICATE_NAME, span, "type name '" + type.name() + "' is already declared");
            } else {
                typeEnvironment.declare(type);
            }
            return;
        }
        if (typeEnvironment.isBuiltin(type.name())) {
            error(DiagnosticCode.RESOL_DUPLICATE_NAME, span, "type name '" + type.name() + "' is already declared");
            return;
        }
        if (module(currentModule).types.putIfAbsent(type.name(), type) != null) {
            error(DiagnosticCode.RESOL_DUPLICATE_NAME, span, "type name '" + type.name() + "' is already declared in module '" + currentModule + "'");
        }
    }

    /**
     * Registers a top-level declaration in the current module. The implicit default module keeps its
     * declarations in the flat program scope; a named module keeps its own namespace. Returns whether
     * the name was free so the caller can add the symbol to the aggregate lowering maps.
     */
    private boolean declareTopLevel(Symbol symbol, SourceSpan span, String kind) {
        if (currentModule == null) {
            if (!symbols.declare(symbol)) {
                error(DiagnosticCode.RESOL_DUPLICATE_NAME, span, kind + " '" + symbol.name() + "' is already declared");
                return false;
            }
            return true;
        }
        if (!module(currentModule).declare(symbol)) {
            error(DiagnosticCode.RESOL_DUPLICATE_NAME, span, kind + " '" + symbol.name() + "' is already declared in module '" + currentModule + "'");
            return false;
        }
        return true;
    }

    /** The unique key a top-level declaration uses in the aggregate maps lowering consumes. */
    private String aggregateKey(String name) {
        return currentModule == null ? name : currentModule + "." + name;
    }

    /**
     * Creates the nominal {@link TypeParameterType} for each declared type parameter, reporting a
     * duplicate name within the list. The returned list is in source order.
     */
    private List<TypeParameterType> declareTypeParameters(List<TypeParameterNode> declarations) {
        List<TypeParameterType> parameters = new ArrayList<>(declarations.size());
        Set<String> names = new HashSet<>();
        for (TypeParameterNode declaration : declarations) {
            if (!names.add(declaration.name())) {
                error(DiagnosticCode.RESOL_DUPLICATE_NAME, declaration.span(), "type parameter '" + declaration.name() + "' is already declared");
            }
            parameters.add(new TypeParameterType(declaration.name()));
        }
        return parameters;
    }

    /** The type-parameter scope introduced by a declaration, keyed by written name. */
    private static Map<String, TypeParameterType> scopeOf(List<TypeParameterType> typeParameters) {
        Map<String, TypeParameterType> scope = new HashMap<>();
        for (TypeParameterType parameter : typeParameters) {
            scope.put(parameter.name(), parameter);
        }
        return scope;
    }

    /** An outer type-parameter scope with an inner declaration's parameters added, shadowing by name. */
    private static Map<String, TypeParameterType> mergedScope(Map<String, TypeParameterType> outer, List<TypeParameterType> inner) {
        Map<String, TypeParameterType> scope = new HashMap<>(outer);
        for (TypeParameterType parameter : inner) {
            scope.put(parameter.name(), parameter);
        }
        return scope;
    }

    /** The interface declaration behind a resolved type, plain or a generic application. */
    private InterfaceDeclNode interfaceDeclarationFor(Type type) {
        Type base = type instanceof ParameterizedType parameterized ? parameterized.base() : type;
        return base instanceof InterfaceType interfaceType ? interfaceDeclarationsByType.get(interfaceType) : null;
    }

    /** The class declaration behind a resolved type, plain or a generic application. */
    private ClassDeclNode classDeclarationFor(Type type) {
        Type base = type instanceof ParameterizedType parameterized ? parameterized.base() : type;
        return base instanceof ClassType classType ? declarationsByType.get(classType) : null;
    }

    /**
     * Resolves each written interface {@code extends} reference, reports a non-interface or unknown
     * name, rejects extension cycles, and installs the resolved extension list on every
     * {@link InterfaceType}. A cycle is broken at the offending edge so later passes terminate.
     */
    private void resolveInterfaceExtensions(CompilationUnitNode unit) {
        for (DeclarationNode declaration : unit.declarations()) {
            if (!(declaration instanceof InterfaceDeclNode interfaceDeclaration)) {
                continue;
            }
            typeParameterScope = scopeOf(interfaceTypeParameters.getOrDefault(interfaceDeclaration, List.of()));
            useScope(interfaceDeclaration);
            List<InterfaceDeclNode> parents = new ArrayList<>();
            List<Type> parentTypes = new ArrayList<>();
            for (TypeRef reference : interfaceDeclaration.superInterfaces()) {
                Type resolved = resolveType(reference);
                if (resolved == null) {
                    continue;
                }
                InterfaceDeclNode parentDeclaration = interfaceDeclarationFor(resolved);
                if (parentDeclaration == null) {
                    errorExpected(DiagnosticCode.SEM_INVALID_INTERFACE, reference.span(), "an interface may extend only interfaces", "an interface type", resolved.name());
                    continue;
                }
                parents.add(parentDeclaration);
                parentTypes.add(resolved);
            }
            superInterfaceDeclarations.put(interfaceDeclaration, parents);
            resolvedSuperInterfaceTypes.put(interfaceDeclaration, parentTypes);
            typeParameterScope = new HashMap<>();
        }
        detectInterfaceCycles(unit);
        for (DeclarationNode declaration : unit.declarations()) {
            if (declaration instanceof InterfaceDeclNode interfaceDeclaration) {
                interfaceTypes.get(interfaceDeclaration).resolveSuperInterfaceTypes(resolvedSuperInterfaceTypes.getOrDefault(interfaceDeclaration, List.of()));
            }
        }
    }

    private void detectInterfaceCycles(CompilationUnitNode unit) {
        Set<InterfaceDeclNode> done = Collections.newSetFromMap(new IdentityHashMap<>());
        for (DeclarationNode declaration : unit.declarations()) {
            if (!(declaration instanceof InterfaceDeclNode start)) {
                continue;
            }
            List<InterfaceDeclNode> path = new ArrayList<>();
            Set<InterfaceDeclNode> onPath = Collections.newSetFromMap(new IdentityHashMap<>());
            List<InterfaceDeclNode> frontier = new ArrayList<>(superInterfaceDeclarations.getOrDefault(start, List.of()));
            while (!frontier.isEmpty()) {
                InterfaceDeclNode current = frontier.remove(frontier.size() - 1);
                if (onPath.add(current)) {
                    path.add(current);
                    frontier.addAll(superInterfaceDeclarations.getOrDefault(current, List.of()));
                    continue;
                }
                if (!done.contains(current)) {
                    error(DiagnosticCode.SEM_INTERFACE_CYCLE, current.span(), "interface '" + current.name() + "' is part of an interface-extension cycle");
                    // Drop the back edge so the extension walk in later passes terminates.
                    superInterfaceDeclarations.replaceAll((decl, parents) -> {
                        List<InterfaceDeclNode> kept = new ArrayList<>(parents);
                        kept.remove(current);
                        return kept;
                    });
                }
            }
            done.addAll(path);
        }
    }

    /** Interface declarations ordered so that every extended interface precedes its extender. */
    private List<InterfaceDeclNode> interfaceExtensionOrder(CompilationUnitNode unit) {
        List<InterfaceDeclNode> order = new ArrayList<>();
        Set<InterfaceDeclNode> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (DeclarationNode declaration : unit.declarations()) {
            if (declaration instanceof InterfaceDeclNode interfaceDeclaration) {
                appendSuperInterfaceFirst(interfaceDeclaration, visited, order);
            }
        }
        return order;
    }

    private void appendSuperInterfaceFirst(InterfaceDeclNode interfaceDeclaration, Set<InterfaceDeclNode> visited, List<InterfaceDeclNode> order) {
        if (!visited.add(interfaceDeclaration)) {
            return;
        }
        for (InterfaceDeclNode parent : superInterfaceDeclarations.getOrDefault(interfaceDeclaration, List.of())) {
            appendSuperInterfaceFirst(parent, visited, order);
        }
        order.add(interfaceDeclaration);
    }

    /**
     * Resolves each written {@code extends} and {@code implements} reference, reports invalid
     * superclasses, interfaces, and inheritance cycles, and installs the resolved supertype and
     * interface list on every {@link ClassType}. A cycle is broken at the offending edge so later
     * passes terminate.
     */
    private void resolveSuperclasses(CompilationUnitNode unit) {
        for (DeclarationNode declaration : unit.declarations()) {
            if (!(declaration instanceof ClassDeclNode classDeclaration)) {
                continue;
            }
            typeParameterScope = scopeOf(classTypeParameters.getOrDefault(classDeclaration, List.of()));
            useScope(classDeclaration);
            if (classDeclaration.superClass().isPresent()) {
                TypeRef reference = classDeclaration.superClass().get();
                Type resolved = resolveType(reference);
                if (resolved != null && resolved != AnyType.INSTANCE) {
                    // The built-in exception bases (Exception/RuntimeException/ApplicationException) have no
                    // source declaration, so they cannot link into the normal superclass chain or supply
                    // inherited members. They are still valid super types for user-defined exceptions:
                    // buildExceptionGraph classifies them and catch matching uses that graph instead of
                    // the runtime class hierarchy (docs/LANGUAGE_SPEC.md error-handling phases).
                    if (resolved instanceof ClassType && isExceptionBaseType(resolved.name())) {
                        // accepted as a valid exception superclass; left unlinked from the resolved chain
                    } else {
                        ClassDeclNode superDeclaration = classDeclarationFor(resolved);
                        if (superDeclaration == null) {
                            errorExpected(DiagnosticCode.SEM_INVALID_SUPERCLASS, reference.span(), "a class may extend only a class or Any", "a class type", resolved.name());
                        } else {
                            superDeclarations.put(classDeclaration, superDeclaration);
                            resolvedSuperTypes.put(classDeclaration, resolved);
                        }
                    }
                }
            }
            List<Type> implemented = new ArrayList<>();
            for (TypeRef reference : classDeclaration.interfaces()) {
                Type resolved = resolveType(reference);
                if (resolved == null) {
                    continue;
                }
                if (interfaceDeclarationFor(resolved) == null) {
                    errorExpected(DiagnosticCode.SEM_INVALID_INTERFACE, reference.span(), "a class may implement only interfaces", "an interface type", resolved.name());
                    continue;
                }
                implemented.add(resolved);
            }
            classTypes.get(classDeclaration).resolveInterfaceTypes(implemented);
            typeParameterScope = new HashMap<>();
        }
        detectInheritanceCycles(unit);
        for (DeclarationNode declaration : unit.declarations()) {
            if (declaration instanceof ClassDeclNode classDeclaration) {
                ClassDeclNode superDeclaration = superDeclarations.get(classDeclaration);
                Type superType = superDeclaration == null ? null : resolvedSuperTypes.get(classDeclaration);
                classTypes.get(classDeclaration).resolveSuperType(superType);
            }
        }
    }

    private void detectInheritanceCycles(CompilationUnitNode unit) {
        Set<ClassDeclNode> done = Collections.newSetFromMap(new IdentityHashMap<>());
        for (DeclarationNode declaration : unit.declarations()) {
            if (!(declaration instanceof ClassDeclNode start)) {
                continue;
            }
            List<ClassDeclNode> path = new ArrayList<>();
            Set<ClassDeclNode> onPath = Collections.newSetFromMap(new IdentityHashMap<>());
            ClassDeclNode current = start;
            while (current != null && !done.contains(current)) {
                if (!onPath.add(current)) {
                    error(DiagnosticCode.SEM_INHERITANCE_CYCLE, current.span(), "class '" + current.name() + "' is part of an inheritance cycle");
                    superDeclarations.remove(current);
                    break;
                }
                path.add(current);
                current = superDeclarations.get(current);
            }
            done.addAll(path);
        }
    }

    /** Class declarations ordered so that every superclass precedes its subclasses. */
    private List<ClassDeclNode> inheritanceOrder(CompilationUnitNode unit) {
        List<ClassDeclNode> order = new ArrayList<>();
        Set<ClassDeclNode> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (DeclarationNode declaration : unit.declarations()) {
            if (declaration instanceof ClassDeclNode classDeclaration) {
                appendSuperFirst(classDeclaration, visited, order);
            }
        }
        return order;
    }

    private void appendSuperFirst(ClassDeclNode classDeclaration, Set<ClassDeclNode> visited, List<ClassDeclNode> order) {
        if (!visited.add(classDeclaration)) {
            return;
        }
        ClassDeclNode superDeclaration = superDeclarations.get(classDeclaration);
        if (superDeclaration != null) {
            appendSuperFirst(superDeclaration, visited, order);
        }
        order.add(classDeclaration);
    }

    /**
     * Installs every sealed class's permitted subtype metadata (docs/LANGUAGE_SPEC.md section 12):
     * its direct subtypes (the hierarchy's variants) and the complete transitive closure, which the
     * specification guarantees is closed because a sealed class may only be extended by declarations
     * in the same source file. A sealed class that is never extended has an empty set.
     */
    private void resolveSealedSubtypes(CompilationUnitNode unit) {
        Map<ClassDeclNode, List<ClassSymbol>> directSubtypes = new IdentityHashMap<>();
        for (DeclarationNode declaration : unit.declarations()) {
            if (!(declaration instanceof ClassDeclNode classDeclaration)) {
                continue;
            }
            ClassSymbol subclass = declaredClasses.get(classDeclaration);
            ClassDeclNode superDeclaration = superDeclarations.get(classDeclaration);
            ClassSymbol superSymbol = superDeclaration == null ? null : declaredClasses.get(superDeclaration);
            if (subclass != null && superSymbol != null) {
                directSubtypes.computeIfAbsent(superDeclaration, key -> new ArrayList<>()).add(subclass);
            }
        }
        for (DeclarationNode declaration : unit.declarations()) {
            if (declaration instanceof ClassDeclNode classDeclaration) {
                ClassSymbol classSymbol = declaredClasses.get(classDeclaration);
                if (classSymbol != null && classSymbol.isSealed()) {
                    List<ClassSymbol> direct = List.copyOf(directSubtypes.getOrDefault(classDeclaration, List.of()));
                    classSymbol.resolvePermittedSubtypes(direct, transitiveSubtypes(classDeclaration, directSubtypes));
                }
            }
        }
    }

    /** The transitive closure of direct subclasses, cycle-safe for a malformed hierarchy. */
    private static List<ClassSymbol> transitiveSubtypes(ClassDeclNode root, Map<ClassDeclNode, List<ClassSymbol>> directSubtypes) {
        List<ClassSymbol> closure = new ArrayList<>();
        Set<ClassSymbol> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<ClassDeclNode> frontier = new ArrayDeque<>();
        frontier.add(root);
        while (!frontier.isEmpty()) {
            ClassDeclNode current = frontier.removeFirst();
            for (ClassSymbol direct : directSubtypes.getOrDefault(current, List.of())) {
                if (visited.add(direct)) {
                    closure.add(direct);
                    frontier.addLast(direct.declaration());
                }
            }
        }
        return List.copyOf(closure);
    }

    private void declareBuiltins() {
        // print/println accept every value INCLUDING null, so the parameter is the nullable top type.
        // A null argument renders as "null" (docs/LANGUAGE_SPEC.md section 6).
        Type anyIncludingNull = AnyType.INSTANCE.nullableView();
        declareBuiltin("print", anyIncludingNull);
        declareBuiltin("println", anyIncludingNull);
        declareBuiltin("exit", IntegerType.INSTANCE);
    }

    private void declareBuiltin(String name, Type parameterType) {
        FunctionSymbol symbol = FunctionSymbol.builtin(name, List.of(parameterType));
        symbols.declare(symbol);
        functions.put(name, symbol);
    }

    private void collectFunction(FunctionDeclNode declaration) {
        useScope(declaration);
        List<TypeParameterType> typeParameters = declareTypeParameters(declaration.typeParameters());
        Map<String, TypeParameterType> previousScope = typeParameterScope;
        typeParameterScope = scopeOf(typeParameters);
        List<VariableSymbol> parameters = buildParameters(declaration.parameters());
        Type returnType = resolveType(declaration.returnType());
        typeParameterScope = previousScope;
        FunctionSymbol functionSymbol = new FunctionSymbol(declaration.name(), declaration.span(), parameters, typeParameters, //
                        returnType != null ? returnType : AnyType.INSTANCE, returnType != null, declaration);
        declaredFunctions.put(declaration, functionSymbol);
        if (declareTopLevel(functionSymbol, declaration.span(), "function")) {
            functions.put(aggregateKey(declaration.name()), functionSymbol);
        }
        if ("main".equals(declaration.name())) {
            error(DiagnosticCode.SEM_INVALID_ENTRY_POINT, declaration.span(), "an explicit 'main' function is not supported; executable top-level statements form the entry point");
        }
    }

    /**
     * Collects one interface declaration: its members in source order (abstract signatures and
     * default methods) and its already-resolved extended interfaces. Duplicate member names within
     * one interface are rejected; a name a class must later resolve across interfaces is not.
     */
    private void collectInterface(InterfaceDeclNode declaration, InterfaceType type) {
        useScope(declaration);
        List<InterfaceSymbol> parents = new ArrayList<>();
        for (InterfaceDeclNode parent : superInterfaceDeclarations.getOrDefault(declaration, List.of())) {
            InterfaceSymbol symbol = declaredInterfaces.get(parent);
            if (symbol != null) {
                parents.add(symbol);
            }
        }
        List<FunctionSymbol> members = new ArrayList<>();
        Set<String> memberNames = new HashSet<>();
        Map<String, TypeParameterType> interfaceScope = scopeOf(interfaceTypeParameters.getOrDefault(declaration, List.of()));
        for (SignatureDeclNode signature : declaration.signatures()) {
            List<TypeParameterType> memberTypeParameters = declareTypeParameters(signature.typeParameters());
            typeParameterScope = mergedScope(interfaceScope, memberTypeParameters);
            List<VariableSymbol> parameters = buildParameters(signature.parameters());
            Type returnType = resolveType(signature.returnType());
            typeParameterScope = new HashMap<>();
            FunctionSymbol symbol = FunctionSymbol.declaredInterfaceSignature(signature.name(), signature.span(), parameters, memberTypeParameters, //
                            returnType != null ? returnType : AnyType.INSTANCE, returnType != null, signature, declaration);
            if ("toString".equals(signature.name())) {
                error(DiagnosticCode.SEM_RESERVED_MEMBER, signature.span(), "member 'toString' is reserved by Any.toString and cannot be declared by an interface");
            }
            if ("equals".equals(signature.name())) {
                error(DiagnosticCode.SEM_RESERVED_MEMBER, signature.span(), "member 'equals' is reserved by Any.equals and cannot be declared by an interface");
            }
            if ("hashCode".equals(signature.name())) {
                error(DiagnosticCode.SEM_RESERVED_MEMBER, signature.span(), "member 'hashCode' is reserved by Any.hashCode and cannot be declared by an interface");
            }
            members.add(symbol);
            if (!memberNames.add(signature.name())) {
                error(DiagnosticCode.RESOL_DUPLICATE_NAME, signature.span(), "member '" + signature.name() + "' is already declared");
            }
        }
        for (FunctionDeclNode method : declaration.defaultMethods()) {
            List<TypeParameterType> memberTypeParameters = declareTypeParameters(method.typeParameters());
            typeParameterScope = mergedScope(interfaceScope, memberTypeParameters);
            List<VariableSymbol> parameters = buildParameters(method.parameters());
            Type returnType = resolveType(method.returnType());
            typeParameterScope = new HashMap<>();
            FunctionSymbol symbol = FunctionSymbol.declaredInterfaceMethod(method.name(), method.span(), parameters, memberTypeParameters, //
                            returnType != null ? returnType : AnyType.INSTANCE, returnType != null, method, declaration);
            if ("toString".equals(method.name())) {
                error(DiagnosticCode.SEM_RESERVED_MEMBER, method.span(), "member 'toString' is reserved by Any.toString and cannot be declared by an interface");
            }
            if ("equals".equals(method.name())) {
                error(DiagnosticCode.SEM_RESERVED_MEMBER, method.span(), "member 'equals' is reserved by Any.equals and cannot be declared by an interface");
            }
            if ("hashCode".equals(method.name())) {
                error(DiagnosticCode.SEM_RESERVED_MEMBER, method.span(), "member 'hashCode' is reserved by Any.hashCode and cannot be declared by an interface");
            }
            members.add(symbol);
            if (!memberNames.add(method.name())) {
                error(DiagnosticCode.RESOL_DUPLICATE_NAME, method.span(), "member '" + method.name() + "' is already declared");
            }
        }
        for (FunctionSymbol member : members) {
            if (member.isAbstractSignature()) {
                boolean covered = false;
                for (InterfaceSymbol parent : parents) {
                    if (parent.defaultSupplies(member)) {
                        covered = true;
                        break;
                    }
                }
                if (covered) {
                    // The inherited default stays reachable through the extension, so a restated
                    // abstract signature leaves a conforming class with both and nothing to prefer.
                    errorExpected(DiagnosticCode.SEM_INVALID_INTERFACE, member.declarationSpan(), //
                                    "interface '" + declaration.name() + "' restates '" + member.name() + "' as a requirement although its extension already supplies a default", "no restatement of " + member.name(), "an abstract signature");
                }
            }
        }
        InterfaceSymbol interfaceSymbol = new InterfaceSymbol(declaration, type, parents, members);
        declaredInterfaces.put(declaration, interfaceSymbol);
        symbolsByInterfaceType.put(type, interfaceSymbol);
        if (declareTopLevel(interfaceSymbol, declaration.span(), "interface")) {
            interfaces.put(aggregateKey(declaration.name()), interfaceSymbol);
        }
    }

    /**
     * Collects one enum declaration: its variants in source order and their positional value types,
     * resolved in the enum's own type-parameter scope. Duplicate variant names are rejected. The
     * complete variant list installed here is the closed-variant metadata of
     * docs/LANGUAGE_SPEC.md section 12.
     */
    private void collectEnum(EnumDeclNode declaration, EnumType type) {
        useScope(declaration);
        Map<String, TypeParameterType> previousScope = typeParameterScope;
        typeParameterScope = scopeOf(enumTypeParameters.getOrDefault(declaration, List.of()));
        EnumSymbol enumSymbol = new EnumSymbol(declaration, type);
        List<EnumVariantSymbol> variants = new ArrayList<>();
        Set<String> variantNames = new HashSet<>();
        for (EnumVariantNode variant : declaration.variants()) {
            List<Type> valueTypes = new ArrayList<>(variant.valueTypes().size());
            for (TypeRef valueType : variant.valueTypes()) {
                Type resolved = resolveType(valueType);
                valueTypes.add(resolved != null ? resolved : AnyType.INSTANCE);
            }
            variants.add(new EnumVariantSymbol(enumSymbol, variant.name(), variant.span(), valueTypes, variant));
            if (!variantNames.add(variant.name())) {
                error(DiagnosticCode.RESOL_DUPLICATE_NAME, variant.span(), "variant '" + variant.name() + "' is already declared");
            }
        }
        enumSymbol.resolveVariants(variants);
        typeParameterScope = previousScope;
        declaredEnums.put(declaration, enumSymbol);
        symbolsByEnumType.put(type, enumSymbol);
        if (declareTopLevel(enumSymbol, declaration.span(), "enum")) {
            enums.put(aggregateKey(declaration.name()), enumSymbol);
        }
    }

    private void collectClass(ClassDeclNode declaration, ClassType type) {
        useScope(declaration);
        Map<String, TypeParameterType> previousScope = typeParameterScope;
        typeParameterScope = scopeOf(classTypeParameters.getOrDefault(declaration, List.of()));
        ClassDeclNode superDeclaration = superDeclarations.get(declaration);
        ClassSymbol superSymbol = superDeclaration == null ? null : declaredClasses.get(superDeclaration);
        if (superDeclaration != null && !superDeclaration.isSealed() && !superDeclaration.isOpen()) {
            errorExpected(DiagnosticCode.SEM_EXTEND_FINAL, declaration.superClass().orElseThrow().span(), //
                            "class '" + declaration.name() + "' cannot extend final class", "an open or sealed class", superDeclaration.name());
        }
        if (superDeclaration != null && superDeclaration.isSealed() //
                        && superDeclaration.span().sourceId() != declaration.span().sourceId()) {
            // A sealed class's subtype set is closed only within its own physical source file; an
            // include splices items into one program but does not erase that file boundary.
            error(DiagnosticCode.SEM_SEALED_SUBTYPE_OUTSIDE_FILE, declaration.span(), //
                            "sealed class '" + superDeclaration.name() + "' may be extended only in its own source file");
        }

        // A guest exception type carries a compiler-synthesized private message field and a getMessage()
        // accessor (docs/LANGUAGE_SPEC.md section 22). Those two names are reserved on every exception
        // class so a user member can never collide with the synthesized storage or its read accessor.
        boolean synthesizedMessage = isExceptionName(declaration.name());
        List<PropertySymbol> properties = new ArrayList<>();
        Set<String> propertyNames = new HashSet<>();
        if (superSymbol != null) {
            for (PropertySymbol inherited : superSymbol.properties()) {
                propertyNames.add(inherited.name());
            }
        }
        int index = superSymbol == null ? 0 : superSymbol.properties().size();
        // Properties and delegates share one source-ordered field layout and one duplicate-name set
        // (docs/LANGUAGE_SPEC.md sections 2 and 9). A delegate is an immutable, explicitly typed
        // property plus the interface contract it can forward; a delegate whose written type is not
        // an interface is reported and excluded from forwarding.
        List<DelegateBinding> delegateBindings = new ArrayList<>();
        for (AstNode member : declaration.members()) {
            if (member instanceof PropertyDeclNode property) {
                // A `static` property is class-level storage: it gets no slot in the instance field
                // layout, no constructor initialization, and no place in the duplicate-name set that
                // governs inherited field shadowing. It is collected separately below.
                if (property.isStatic()) {
                    continue;
                }
                Type propertyType = resolveType(property.declaredType().orElseThrow());
                if ("toString".equals(property.name())) {
                    error(DiagnosticCode.SEM_RESERVED_MEMBER, property.span(), "member 'toString' is reserved by Any.toString and must be declared as an override method");
                }
                if ("equals".equals(property.name())) {
                    error(DiagnosticCode.SEM_RESERVED_MEMBER, property.span(), "member 'equals' is reserved by Any.equals and must be declared as an override method");
                }
                if ("hashCode".equals(property.name())) {
                    error(DiagnosticCode.SEM_RESERVED_MEMBER, property.span(), "member 'hashCode' is reserved by Any.hashCode and must be declared as an override method");
                }
                if (synthesizedMessage && isSynthesizedMessageMember(property.name())) {
                    error(DiagnosticCode.SEM_RESERVED_MEMBER, property.span(), "member '" + property.name() + "' is reserved by the synthesized exception message");
                }
                if (propertyNames.add(property.name())) {
                    properties.add(new PropertySymbol(property.name(), property.span(), propertyType != null ? propertyType : AnyType.INSTANCE, //
                                    property.bindingKind() == BindingKind.VAR, property.initializer().isPresent(), index));
                } else {
                    error(DiagnosticCode.RESOL_DUPLICATE_NAME, property.span(), "property '" + property.name() + "' is already declared");
                }
                index++;
            } else if (member instanceof DelegateDeclNode delegate) {
                Type declaredType = resolveType(delegate.declaredType());
                if ("toString".equals(delegate.name())) {
                    error(DiagnosticCode.SEM_RESERVED_MEMBER, delegate.span(), "member 'toString' is reserved by Any.toString and must be declared as an override method");
                }
                if ("equals".equals(delegate.name())) {
                    error(DiagnosticCode.SEM_RESERVED_MEMBER, delegate.span(), "member 'equals' is reserved by Any.equals and must be declared as an override method");
                }
                if ("hashCode".equals(delegate.name())) {
                    error(DiagnosticCode.SEM_RESERVED_MEMBER, delegate.span(), "member 'hashCode' is reserved by Any.hashCode and must be declared as an override method");
                }
                if (synthesizedMessage && isSynthesizedMessageMember(delegate.name())) {
                    error(DiagnosticCode.SEM_RESERVED_MEMBER, delegate.span(), "member '" + delegate.name() + "' is reserved by the synthesized exception message");
                }
                if (!propertyNames.add(delegate.name())) {
                    error(DiagnosticCode.RESOL_DUPLICATE_NAME, delegate.span(), "property '" + delegate.name() + "' is already declared");
                    index++;
                    continue;
                }
                PropertySymbol property = new PropertySymbol(delegate.name(), delegate.span(), declaredType != null ? declaredType : AnyType.INSTANCE, //
                                false, delegate.initializer().isPresent(), true, index);
                properties.add(property);
                index++;
                if (declaredType instanceof InterfaceType interfaceType) {
                    InterfaceSymbol contract = symbolsByInterfaceType.get(interfaceType);
                    if (contract != null) {
                        delegateBindings.add(new DelegateBinding(property, declaredType, contract));
                        continue;
                    }
                }
                if (declaredType != null) {
                    // An unknown type name is already reported by resolveType; only a resolved non-interface
                    // type is a delegation error in its own right.
                    errorExpected(DiagnosticCode.SEM_INVALID_DELEGATE_TYPE, delegate.span(), //
                                    "delegate '" + delegate.name() + "' must have an interface type", "an interface type", declaredType.name());
                }
            }
        }

        List<FunctionSymbol> methods = new ArrayList<>();
        Set<String> methodNames = new HashSet<>();
        Map<TypeParameterType, Type> superSubstitution = substitutionFor(type.superType().orElse(AnyType.INSTANCE));
        for (FunctionDeclNode method : declaration.methods()) {
            List<TypeParameterType> methodTypeParameters = declareTypeParameters(method.typeParameters());
            Map<String, TypeParameterType> classScope = typeParameterScope;
            typeParameterScope = mergedScope(classScope, methodTypeParameters);
            List<VariableSymbol> parameters = buildParameters(method.parameters());
            Type returnType = resolveType(method.returnType());
            typeParameterScope = classScope;
            if (synthesizedMessage && isSynthesizedMessageMember(method.name())) {
                error(DiagnosticCode.SEM_RESERVED_MEMBER, method.span(), "method '" + method.name() + "' is reserved by the synthesized exception message");
            }
            FunctionSymbol symbol = FunctionSymbol.declaredMethod(method.name(), method.span(), parameters, methodTypeParameters, //
                            returnType != null ? returnType : AnyType.INSTANCE, returnType != null, method, declaration, method.isOpen(), method.isOverride());
            methods.add(symbol);
            if (propertyNames.contains(method.name())) {
                error(DiagnosticCode.RESOL_DUPLICATE_NAME, method.span(), "member '" + method.name() + "' is already declared");
            } else if (!methodNames.add(method.name())) {
                error(DiagnosticCode.RESOL_DUPLICATE_NAME, method.span(), "method '" + method.name() + "' is already declared");
            } else {
                validateOverride(superSymbol, symbol, superSubstitution);
            }
        }
        validateEqualsHashCodePair(declaration, methods);

        // Static members (docs/LANGUAGE_SPEC.md section 7). These are collected after the instance
        // members so a name shared with an instance member is detected against the complete instance
        // namespace, and are never added to `properties`, `methods`, `propertyNames`, or `methodNames`:
        // class-level storage has no instance slot and a static method never enters the virtual table.
        // A static member cannot be `open` or `override`, so `validateOverride` never runs for one.
        List<PropertySymbol> staticProperties = new ArrayList<>();
        List<FunctionSymbol> staticMethods = new ArrayList<>();
        boolean previousStaticMember = checkingStaticMember;
        Set<TypeParameterType> previousForbiddenParameters = forbiddenTypeParameters;
        checkingStaticMember = true;
        forbiddenTypeParameters = new HashSet<>(classTypeParameters.getOrDefault(declaration, List.of()));
        for (PropertyDeclNode property : declaration.staticProperties()) {
            // The instance method loop has already run, so `methodNames` holds every instance method
            // and `propertyNames` every instance property regardless of written order. A static property
            // must be refused against both: it shares one member namespace with them, and a static
            // property read through the class name would otherwise be shadowed by an instance method of
            // the same name with no diagnostic for either (docs/LANGUAGE_SPEC.md section 7).
            if (methodNames.contains(property.name())) {
                error(DiagnosticCode.RESOL_DUPLICATE_NAME, property.span(), "member '" + property.name() + "' is already declared");
            } else if (!propertyNames.add(property.name())) {
                error(DiagnosticCode.RESOL_DUPLICATE_NAME, property.span(), "property '" + property.name() + "' is already declared");
            }
            Type propertyType = resolveType(property.declaredType().orElseThrow());
            staticProperties.add(new PropertySymbol(property.name(), property.span(), propertyType != null ? propertyType : AnyType.INSTANCE, //
                            property.bindingKind() == BindingKind.VAR, property.initializer().isPresent(), PropertySymbol.STATIC_SLOT));
        }
        for (FunctionDeclNode method : declaration.staticMethods()) {
            if (method.isOpen() || method.isOverride()) {
                // A static member has no receiver, so there is nothing to specialize or replace; the
                // modifiers are rejected rather than silently ignored.
                error(DiagnosticCode.SEM_INVALID_STATIC_MODIFIER, method.span(), "static member '" + method.name() + "' cannot be declared open or override");
            }
            List<TypeParameterType> methodTypeParameters = declareTypeParameters(method.typeParameters());
            // The method's own type parameters are legal in its signature; only the class's are forbidden.
            forbiddenTypeParameters = new HashSet<>(classTypeParameters.getOrDefault(declaration, List.of()));
            forbiddenTypeParameters.removeAll(methodTypeParameters);
            Map<String, TypeParameterType> classScope = typeParameterScope;
            typeParameterScope = mergedScope(classScope, methodTypeParameters);
            List<VariableSymbol> parameters = buildParameters(method.parameters());
            Type returnType = resolveType(method.returnType());
            typeParameterScope = classScope;
            if (propertyNames.contains(method.name()) || methodNames.contains(method.name())) {
                // `propertyNames` and `methodNames` already hold every instance member and every
                // static member seen so far, so one collision test covers a static method against an
                // instance property, an instance method, a static property, and another static method.
                // A second `add` test would be unreachable: reaching the else branch means neither set
                // holds the name, so recording it cannot fail.
                error(DiagnosticCode.RESOL_DUPLICATE_NAME, method.span(), "member '" + method.name() + "' is already declared");
            } else {
                methodNames.add(method.name());
                staticMethods.add(FunctionSymbol.declaredStaticMethod(method.name(), method.span(), parameters, methodTypeParameters, //
                                returnType != null ? returnType : AnyType.INSTANCE, returnType != null, method, declaration));
            }
        }
        StaticBlockNode staticBlock = null;
        List<StaticBlockNode> staticBlocks = declaration.staticBlocks();
        if (!staticBlocks.isEmpty()) {
            staticBlock = staticBlocks.get(0);
            if (staticBlocks.size() > 1) {
                // The grammar tolerates the duplicate so the second block is named precisely.
                error(DiagnosticCode.SEM_DUPLICATE_STATIC_BLOCK, staticBlocks.get(1).span(), "a class may declare at most one static initializer block");
            }
        }
        checkingStaticMember = previousStaticMember;
        forbiddenTypeParameters = previousForbiddenParameters;

        for (AstNode member : declaration.members()) {
            String memberName = null;
            if (member instanceof PropertyDeclNode property) {
                memberName = property.name();
            } else if (member instanceof DelegateDeclNode delegate) {
                memberName = delegate.name();
            } else if (member instanceof FunctionDeclNode method) {
                memberName = method.name();
            }
            if (memberName != null && memberName.equals(declaration.name())) {
                // C#/Dart forbid a member with the enclosing type's name; only the constructor may use it.
                error(DiagnosticCode.SEM_MEMBER_NAMED_AFTER_CLASS, member.span(), "class member '" + memberName + "' cannot have the same name as its class");
            }
        }

        FunctionSymbol constructor = null;
        for (ConstructorDeclNode constructorDecl : declaration.constructors()) {
            if (!constructorDecl.name().equals(declaration.name())) {
                error(DiagnosticCode.SEM_CONSTRUCTOR_NAME, constructorDecl.span(), "constructor '" + constructorDecl.name() + "' must be named after its class '" + declaration.name() + "'");
            }
        }
        if (declaration.constructors().size() > 1) {
            error(DiagnosticCode.SEM_DUPLICATE_CONSTRUCTOR, declaration.constructors().get(1).span(), "a class may declare at most one constructor");
        }
        if (declaration.constructors().size() == 1) {
            ConstructorDeclNode constructorDecl = declaration.constructors().get(0);
            constructor = FunctionSymbol.declaredConstructor(constructorDecl.span(), buildParameters(constructorDecl.parameters()), constructorDecl, declaration);
        } else {
            for (PropertySymbol property : properties) {
                if (!property.hasInitializer()) {
                    error(DiagnosticCode.SEM_CLASS_REQUIRES_INITIALIZER, property.declarationSpan(), //
                                    "property '" + property.name() + "' needs an initializer because the class has no constructor");
                }
            }
            if (superRequiresArguments(superSymbol)) {
                error(DiagnosticCode.SEM_MISSING_SUPER_INIT_IMPLICIT, declaration.span(), //
                                "class '" + declaration.name() + "' must declare a constructor to call super(...)");
            }
        }

        List<InterfaceSymbol> implementedInterfaces = new ArrayList<>();
        for (Type written : type.interfaceTypes()) {
            InterfaceSymbol symbol = interfaceSymbolFor(written);
            if (symbol != null) {
                implementedInterfaces.add(symbol);
            }
        }
        Map<InterfaceDeclNode, Map<TypeParameterType, Type>> interfaceBindings = collectInterfaceBindings(type.interfaceTypes());
        ClassSymbol classSymbol = new ClassSymbol(declaration, type, declaration.isOpen(), declaration.isSealed(), superSymbol, implementedInterfaces, delegateBindings, properties, methods, constructor, interfaceBindings, staticProperties, staticMethods, staticBlock);
        reportInterfaceConformance(classSymbol);
        declaredClasses.put(declaration, classSymbol);
        symbolsByType.put(type, classSymbol);
        if (declareTopLevel(classSymbol, declaration.span(), "class")) {
            classes.put(aggregateKey(declaration.name()), classSymbol);
        }
        typeParameterScope = previousScope;
    }

    /**
     * Collects, for every interface a class reaches through its {@code implements} and interface
     * {@code extends} edges, the substitution from that interface's declared type parameters to the
     * types the class applies. A generic application such as {@code Repository<User>} binds
     * {@code Repository}'s {@code T} to {@code User}; a non-generic interface contributes an empty
     * substitution. Interface conformance substitutes a requirement's types with this mapping before
     * comparing it with an implementation (docs/LANGUAGE_SPEC.md section 11).
     */
    private Map<InterfaceDeclNode, Map<TypeParameterType, Type>> collectInterfaceBindings(List<Type> edges) {
        Map<InterfaceDeclNode, Map<TypeParameterType, Type>> bindings = new IdentityHashMap<>();
        for (Type edge : edges) {
            collectInterfaceBinding(edge, bindings);
        }
        return bindings;
    }

    private void collectInterfaceBinding(Type edge, Map<InterfaceDeclNode, Map<TypeParameterType, Type>> bindings) {
        InterfaceSymbol face = interfaceSymbolFor(edge);
        if (face == null || bindings.containsKey(face.declaration())) {
            return;
        }
        bindings.put(face.declaration(), substitutionFor(edge));
        for (Type parent : edge.interfaceTypes()) {
            collectInterfaceBinding(parent, bindings);
        }
    }

    /**
     * Reports interface-conformance failures of a class (docs/LANGUAGE_SPEC.md sections 8 and 9): a
     * required member with no implementation, conflicting interface defaults or several delegates the
     * class did not resolve, and an implementation or forwarded delegate member whose parameter types
     * or return type does not conform to its requirement.
     */
    private void reportInterfaceConformance(ClassSymbol classSymbol) {
        for (FunctionSymbol requirement : classSymbol.missingInterfaceRequirements()) {
            errorExpected(DiagnosticCode.SEM_MISSING_INTERFACE_IMPLEMENTATION, classSymbol.declaration().span(), //
                            "class '" + classSymbol.name() + "' does not implement interface member '" + requirement.name() + "'", "an implementation of " + requirement.name(), "nothing");
        }
        for (FunctionSymbol requirement : classSymbol.conflictingInterfaceRequirements()) {
            InterfaceSymbol owner = conflictingDefaultOwner(classSymbol, requirement);
            SourceSpan span = owner == null ? classSymbol.declaration().span() : owner.declaration().span();
            errorExpected(DiagnosticCode.SEM_CONFLICTING_DEFAULTS, span, //
                            "class '" + classSymbol.name() + "' inherits conflicting defaults for '" + requirement.name() + "' and must explicitly resolve it", "an implementation of " + requirement.name(), "two interface defaults");
        }
        for (FunctionSymbol requirement : classSymbol.ambiguousDelegatedRequirements()) {
            errorExpected(DiagnosticCode.SEM_AMBIGUOUS_DELEGATION, classSymbol.declaration().span(), //
                            "class '" + classSymbol.name() + "' has more than one delegate supplying '" + requirement.name() + "' and must explicitly resolve it", "an implementation of " + requirement.name(), "two delegates");
        }
        for (Map.Entry<FunctionSymbol, PropertySymbol> conflict : classSymbol.delegateSignatureConflicts().entrySet()) {
            FunctionSymbol requirement = conflict.getKey();
            PropertySymbol delegate = conflict.getValue();
            errorExpected(DiagnosticCode.SEM_DELEGATE_SIGNATURE, delegate.declarationSpan(), //
                            "delegate '" + delegate.name() + "' cannot supply '" + requirement.name() + "': the forwarded member must keep the required parameter types and a covariant return type", //
                            requirement.returnType().name(), requirement.name());
        }
        for (Map.Entry<FunctionSymbol, FunctionSymbol> conflict : classSymbol.interfaceSignatureConflicts().entrySet()) {
            FunctionSymbol requirement = conflict.getKey();
            FunctionSymbol implementation = conflict.getValue();
            errorExpected(DiagnosticCode.SEM_IMPLEMENTATION_SIGNATURE, implementation.declarationSpan(), //
                            "implementation of '" + requirement.name() + "' must keep the required parameter types and a covariant return type", requirement.returnType().name(), implementation.returnType().name());
        }
    }

    /** The interface whose requirement name is supplied by more than one default. */
    private InterfaceSymbol conflictingDefaultOwner(ClassSymbol classSymbol, FunctionSymbol requirement) {
        for (InterfaceSymbol face : classSymbol.allInterfaces()) {
            if (face.membersNamed(requirement.name()).size() > 1) {
                return face;
            }
        }
        return classSymbol.allInterfaces().isEmpty() ? null : classSymbol.allInterfaces().get(0);
    }

    /**
     * Enforces that {@code equals} and {@code hashCode} are overridden together in one class
     * (docs/LANGUAGE_SPEC.md section 3). A class that overrides {@code equals} must also override
     * {@code hashCode} in the same class declaration, and vice versa.
     *
     * <p>The rule is per-declaration and never satisfied by inheritance. An inherited {@code hashCode}
     * is exactly the hazard the pairing exists to prevent: a subclass that adds equality-relevant
     * fields and overrides {@code equals} still inherits a {@code hashCode} that ignores them, which
     * Java only warns about (and which Solvik can reject, because it can see the declaration). Each
     * error is reported on the single member that is unpaired, so a class missing one of the two gets
     * one diagnostic rather than a pair that implies two independent mistakes.
     */
    private void validateEqualsHashCodePair(ClassDeclNode declaration, List<FunctionSymbol> methods) {
        FunctionSymbol equals = null;
        FunctionSymbol hashCode = null;
        for (FunctionSymbol method : methods) {
            if (!method.isOverride()) {
                // A non-override member named equals/hashCode is already reported as an accidental
                // override by validateOverride; it does not participate in the pairing.
                continue;
            }
            if ("equals".equals(method.name())) {
                equals = method;
            } else if ("hashCode".equals(method.name())) {
                hashCode = method;
            }
        }
        if (equals != null && hashCode == null) {
            error(DiagnosticCode.SEM_EQUALS_WITHOUT_HASHCODE, equals.declarationSpan(), //
                            "class '" + declaration.name() + "' overrides 'equals' and must also override 'hashCode' so equal values hash alike");
        } else if (hashCode != null && equals == null) {
            error(DiagnosticCode.SEM_HASHCODE_WITHOUT_EQUALS, hashCode.declarationSpan(), //
                            "class '" + declaration.name() + "' overrides 'hashCode' but not 'equals'; a custom hash requires a matching equality rule");
        }
    }

    /** Validates that a method's {@code override} modifier and signature match the inherited method. */
    private void validateOverride(ClassSymbol superSymbol, FunctionSymbol method, Map<TypeParameterType, Type> superSubstitution) {
        if ("toString".equals(method.name())) {
            validateToStringOverride(superSymbol, method);
            return;
        }
        if ("equals".equals(method.name())) {
            validateEqualsOverride(superSymbol, method);
            return;
        }
        if ("hashCode".equals(method.name())) {
            validateHashCodeOverride(superSymbol, method);
            return;
        }
        FunctionSymbol inherited = superSymbol == null ? null : superSymbol.nearestDeclaredClassMethod(method.name()).orElse(null);
        if (method.isOverride()) {
            if (inherited == null) {
                error(DiagnosticCode.SEM_OVERRIDE_WITHOUT_SUPER, method.declarationSpan(), //
                                "method '" + method.name() + "' is marked override but no inherited method matches");
                return;
            }
            if (!inherited.isOpen()) {
                error(DiagnosticCode.SEM_OVERRIDE_FINAL, method.declarationSpan(), //
                                "method '" + method.name() + "' cannot override a final method");
                return;
            }
            if (method.isReturnTypeKnown() && (!sameParameterTypes(inherited, method, superSubstitution) || !method.returnType().isAssignableTo(inherited.returnType().substitute(superSubstitution)))) {
                errorExpected(DiagnosticCode.SEM_OVERRIDE_SIGNATURE, method.declarationSpan(), //
                                "override of '" + method.name() + "' must keep the inherited parameter types and a covariant return type", inherited.returnType().substitute(superSubstitution).name(), method.returnType().name());
            }
        } else if (inherited != null) {
            error(DiagnosticCode.SEM_ACCIDENTAL_OVERRIDE, method.declarationSpan(), //
                            "method '" + method.name() + "' overrides an inherited method and must be declared override");
        }
    }

    /**
     * Validates a user declaration named {@code toString} against the built-in root member
     * {@code Any.toString(): String} (docs/LANGUAGE_SPEC.md sections 4 and 7). Because every class
     * inherits the built-in member, an override is always {@code override}, takes no arguments, and
     * returns exactly {@code String}; overloading it is impossible. A further override follows the
     * ordinary {@code open}/{@code final} rules, so a non-{@code open} user override is final.
     */
    private void validateToStringOverride(ClassSymbol superSymbol, FunctionSymbol method) {
        if (!method.parameters().isEmpty()) {
            errorExpected(DiagnosticCode.SEM_OVERRIDE_SIGNATURE, method.declarationSpan(), //
                            "override of 'toString' must keep the inherited parameter types and a covariant return type", "() -> String", methodSignatureParameters(method));
            return;
        }
        if (!method.isOverride()) {
            error(DiagnosticCode.SEM_ACCIDENTAL_OVERRIDE, method.declarationSpan(), "method 'toString' overrides 'Any.toString' and must be declared override");
            return;
        }
        if (method.isReturnTypeKnown() && !method.returnType().isAssignableTo(StringType.INSTANCE)) {
            errorExpected(DiagnosticCode.SEM_OVERRIDE_SIGNATURE, method.declarationSpan(), //
                            "override of 'toString' must keep the inherited parameter types and a covariant return type", "String", method.returnType().name());
        }
        // A further override requires the inherited override to be open. The universal root member is
        // always open, so only a user override declared without `open` is final.
        FunctionSymbol inherited = superSymbol == null ? null : superSymbol.nearestDeclaredClassMethod("toString").orElse(null);
        if (inherited != null && !inherited.isOpen()) {
            error(DiagnosticCode.SEM_OVERRIDE_FINAL, method.declarationSpan(), "method 'toString' cannot override a final method");
        }
    }

    /**
     * Validates a user declaration named {@code hashCode} against the built-in root member
     * {@code Any.hashCode(): Integer} (docs/LANGUAGE_SPEC.md section 3). Like {@code toString}, every
     * class inherits it, so an override is always {@code override}, takes no arguments, and returns
     * exactly {@code Integer}. {@code Integer} is final and has no subtypes, so the return type is
     * checked for identity rather than assignability, matching {@code equals}.
     */
    private void validateHashCodeOverride(ClassSymbol superSymbol, FunctionSymbol method) {
        if (!method.parameters().isEmpty()) {
            errorExpected(DiagnosticCode.SEM_OVERRIDE_SIGNATURE, method.declarationSpan(), //
                            "override of 'hashCode' must keep the inherited parameter types and a covariant return type", "() -> Integer", methodSignatureParameters(method));
            return;
        }
        if (!method.isOverride()) {
            error(DiagnosticCode.SEM_ACCIDENTAL_OVERRIDE, method.declarationSpan(), "method 'hashCode' overrides 'Any.hashCode' and must be declared override");
            return;
        }
        if (method.isReturnTypeKnown() && method.returnType() != IntegerType.INSTANCE) {
            errorExpected(DiagnosticCode.SEM_OVERRIDE_SIGNATURE, method.declarationSpan(), //
                            "override of 'hashCode' must keep the inherited parameter types and a covariant return type", "Integer", method.returnType().name());
        }
        FunctionSymbol inherited = superSymbol == null ? null : superSymbol.nearestDeclaredClassMethod("hashCode").orElse(null);
        if (inherited != null && !inherited.isOpen()) {
            error(DiagnosticCode.SEM_OVERRIDE_FINAL, method.declarationSpan(), "method 'hashCode' cannot override a final method");
        }
    }

    /**
     * Validates a user declaration named {@code equals} against the built-in root member
     * {@code Any.equals(other: Any?): Boolean} (docs/LANGUAGE_SPEC.md section 3). Because every
     * class inherits the built-in member, an override is always {@code override}, takes exactly one
     * parameter typed exactly {@code Any?}, and returns exactly {@code Boolean}.
     */
    private void validateEqualsOverride(ClassSymbol superSymbol, FunctionSymbol method) {
        if (method.parameters().size() != 1) {
            errorExpected(DiagnosticCode.SEM_OVERRIDE_SIGNATURE, method.declarationSpan(), //
                            "override of 'equals' must keep the inherited parameter types and a covariant return type", "(Any?) -> Boolean", methodSignatureParameters(method));
            return;
        }
        if (!method.isOverride()) {
            error(DiagnosticCode.SEM_ACCIDENTAL_OVERRIDE, method.declarationSpan(), "method 'equals' overrides 'Any.equals' and must be declared override");
            return;
        }
        if (method.parameters().get(0).type() != AnyType.INSTANCE.nullableView()) {
            errorExpected(DiagnosticCode.SEM_OVERRIDE_SIGNATURE, method.declarationSpan(), //
                            "override of 'equals' must keep the inherited parameter types and a covariant return type", "(Any?) -> Boolean", methodSignatureParameters(method));
        }
        if (method.isReturnTypeKnown() && method.returnType() != BooleanType.INSTANCE) {
            errorExpected(DiagnosticCode.SEM_OVERRIDE_SIGNATURE, method.declarationSpan(), //
                            "override of 'equals' must keep the inherited parameter types and a covariant return type", "(Any?) -> Boolean", method.returnType().name());
        }
        // A further override requires the inherited override to be open. The universal root member is
        // always open, so only a user override declared without `open` is final.
        FunctionSymbol inherited = superSymbol == null ? null : superSymbol.nearestDeclaredClassMethod("equals").orElse(null);
        if (inherited != null && !inherited.isOpen()) {
            error(DiagnosticCode.SEM_OVERRIDE_FINAL, method.declarationSpan(), "method 'equals' cannot override a final method");
        }
    }

    /** Renders a method's declared parameter types for a signature diagnostic. */
    private static String methodSignatureParameters(FunctionSymbol method) {
        StringBuilder builder = new StringBuilder("(");
        for (int i = 0; i < method.parameters().size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(method.parameters().get(i).type().name());
        }
        return builder.append(')').toString();
    }

    private static boolean sameParameterTypes(FunctionSymbol inherited, FunctionSymbol method, Map<TypeParameterType, Type> superSubstitution) {
        if (inherited.parameters().size() != method.parameters().size()) {
            return false;
        }
        for (int i = 0; i < inherited.parameters().size(); i++) {
            if (inherited.parameters().get(i).type().substitute(superSubstitution) != method.parameters().get(i).type()) {
                return false;
            }
        }
        return true;
    }

    /** Whether constructing a subclass of {@code superSymbol} requires explicit {@code super(...)} arguments. */
    private static boolean superRequiresArguments(ClassSymbol superSymbol) {
        return superSymbol != null && superSymbol.constructor().map(constructor -> !constructor.parameters().isEmpty()).orElse(false);
    }

    private List<VariableSymbol> buildParameters(List<ParameterNode> parameters) {
        List<VariableSymbol> symbols = new ArrayList<>(parameters.size());
        for (ParameterNode parameter : parameters) {
            Type parameterType = resolveType(parameter.type());
            VariableSymbol symbol = new VariableSymbol(parameter.name(), parameter.span(), //
                            parameterType != null ? parameterType : AnyType.INSTANCE, false, true);
            symbol.markInitialized();
            symbols.add(symbol);
        }
        return symbols;
    }

    // ---------------------------------------------------------------------------------------------
    // Body checking
    // ---------------------------------------------------------------------------------------------

    private void checkBodies(CompilationUnitNode unit) {
        for (DeclarationNode declaration : unit.declarations()) {
            useScope(declaration);
            if (declaration instanceof FunctionDeclNode function) {
                checkCallable(declaredFunctions.get(function), function.body(), null, false);
            } else if (declaration instanceof ClassDeclNode classDeclaration) {
                checkClass(declaredClasses.get(classDeclaration));
            } else if (declaration instanceof InterfaceDeclNode interfaceDeclaration) {
                checkInterface(declaredInterfaces.get(interfaceDeclaration));
            }
        }
        if (implicitMain != null) {
            checkCallable(implicitMain, implicitMain.declaration().body(), null, false);
        }
    }

    /**
     * Checks the default method bodies of one interface. A default method has no owner instance of
     * its own: its receiver is the conforming object, so its body runs with the interface as its
     * nominal context and an unqualified call resolves through the interface's own member set.
     */
    private void checkInterface(InterfaceSymbol interfaceSymbol) {
        ClassSymbol previousClass = currentClass;
        FunctionSymbol previousFunction = currentFunction;
        InterfaceSymbol previousInterface = currentInterface;
        currentClass = null;
        currentFunction = null;
        currentInterface = interfaceSymbol;
        for (FunctionSymbol member : interfaceSymbol.declaredMembers()) {
            if (member.hasImplementation()) {
                checkCallable(member, member.declaration().body(), null, false);
            }
        }
        currentClass = previousClass;
        currentFunction = previousFunction;
        currentInterface = previousInterface;
    }

    /**
     * Checks one property initializer against the declared property type, with that type available as
     * the initializer's expected type (docs/LANGUAGE_SPEC.md section 7).
     *
     * <p>Pushing the declared type is what makes a property position a context in the sense section 6
     * needs: a generic function reference written there is instantiated to the property's declared
     * function type, so {@code val id: func(Integer): Integer = identity} behaves the same way as an
     * instance member and as a local. Leaving the type out would make instantiation work in one declared
     * position and fail in another for the same written expression, and the difference would be an
     * accident of where the declaration sits rather than a rule a reader could state.
     *
     * <p>A missing symbol is not a reason to invent an expected type, but the initializer still has to be
     * checked so its own errors are reported; only the assignability comparison is skipped, since there is
     * no declared type to compare against. The name is passed in only for the diagnostic, because the
     * instance and static placements have always been described distinctly.
     */
    private void checkInitializerAgainst(PropertySymbol symbol, ExpressionNode initializer, String placement) {
        Type declared = symbol == null ? null : symbol.type();
        if (declared != null) {
            expectedTypes.push(declared);
        }
        try {
            Type initializerType = checkExpression(initializer);
            if (declared != null && initializerType != null && !assignableOrWidened(initializer, initializerType, declared)) {
                errorExpected(DiagnosticCode.TYPE_MISMATCH, initializer.span(), //
                                "initializer is not assignable to " + placement + " type " + declared.name(), declared.name(), initializerType.name());
            }
        } finally {
            if (declared != null) {
                expectedTypes.pop();
            }
        }
    }

    private void checkClass(ClassSymbol classSymbol) {
        ClassSymbol previousClass = currentClass;
        FunctionSymbol previousFunction = currentFunction;
        InterfaceSymbol previousInterface = currentInterface;
        boolean previousChecking = checkingConstructor;
        currentClass = null;
        currentFunction = null;
        currentInterface = null;
        checkingConstructor = false;
        Map<String, TypeParameterType> previousScope = typeParameterScope;
        typeParameterScope = scopeOf(classTypeParameters.getOrDefault(classSymbol.declaration(), List.of()));
        for (AstNode member : classSymbol.declaration().members()) {
            ExpressionNode initializer = null;
            String memberName = null;
            if (member instanceof PropertyDeclNode property) {
                // A `static` property is checked in the dedicated static pass below, so skipping it here
                // keeps one initializer from being reported twice (docs/LANGUAGE_SPEC.md section 7).
                if (property.isStatic()) {
                    continue;
                }
                initializer = property.initializer().orElse(null);
                memberName = property.name();
            } else if (member instanceof DelegateDeclNode delegate) {
                initializer = delegate.initializer().orElse(null);
                memberName = delegate.name();
            }
            if (initializer == null) {
                continue;
            }
            PropertySymbol symbol = classSymbol.property(memberName).orElse(null);
            checkInitializerAgainst(symbol, initializer, "property");
        }
        for (FunctionSymbol method : classSymbol.declaredMethods()) {
            checkCallable(method, method.declaration().body(), classSymbol, false);
        }
        if (classSymbol.constructor().isPresent()) {
            FunctionSymbol constructor = classSymbol.constructor().get();
            checkCallable(constructor, constructor.constructorDeclaration().body(), classSymbol, true);
        }
        boolean previousStaticContext = checkingStaticMember;
        Set<TypeParameterType> previousForbidden = forbiddenTypeParameters;
        List<TypeParameterType> classParameterList = classTypeParameters.getOrDefault(classSymbol.declaration(), List.of());
        checkingStaticMember = true;
        forbiddenTypeParameters = new HashSet<>(classParameterList);
        for (PropertyDeclNode property : classSymbol.declaration().staticProperties()) {
            ExpressionNode initializer = property.initializer().orElse(null);
            if (initializer == null) {
                continue;
            }
            PropertySymbol symbol = classSymbol.staticProperty(property.name()).orElse(null);
            checkInitializerAgainst(symbol, initializer, "static property");
        }
        for (FunctionSymbol method : classSymbol.declaredStaticMethods()) {
            // The class is passed as the owner so its type parameters still resolve and a reference to
            // one can be reported precisely as SOLV-SEM-048 instead of a misleading unknown-type error;
            // `checkingStaticMember` is what rejects `this` and `super` inside the body. The method's
            // own type parameters are legal, so they leave the forbidden set for this iteration.
            forbiddenTypeParameters = new HashSet<>(classParameterList);
            forbiddenTypeParameters.removeAll(method.typeParameters());
            checkCallable(method, method.declaration().body(), classSymbol, false);
        }
        if (classSymbol.staticBlock().isPresent()) {
            forbiddenTypeParameters = new HashSet<>(classParameterList);
            checkStaticBlock(classSymbol.staticBlock().get(), classSymbol);
        }
        checkingStaticMember = previousStaticContext;
        forbiddenTypeParameters = previousForbidden;
        typeParameterScope = previousScope;
        currentClass = previousClass;
        currentFunction = previousFunction;
        currentInterface = previousInterface;
        checkingConstructor = previousChecking;
    }

    /**
     * Checks the statements of one {@code static} class initializer block (docs/LANGUAGE_SPEC.md
     * section 7). The block is a statement list rather than a callable, so it declares no parameters,
     * returns nothing, and is analyzed with {@code checkingStaticMember} set: {@code this} and
     * {@code super} are rejected inside it, and an unqualified call resolves only among the class's own
     * static methods because there is no receiver.
     */
    private void checkStaticBlock(StaticBlockNode block, ClassSymbol owner) {
        ClassSymbol previousClass = currentClass;
        FunctionSymbol previousFunction = currentFunction;
        InterfaceSymbol previousInterface = currentInterface;
        boolean previousChecking = checkingConstructor;
        boolean previousStaticContext = checkingStaticMember;
        CallExprNode previousSuperCall = sanctionedSuperCall;
        Set<PropertySymbol> previousInitialized = definitelyInitialized;
        Map<VariableSymbol, Type> previousNarrowed = narrowedTypes;
        Set<VariableSymbol> previousWritten = writtenVariables;
        int previousLoopDepth = loopDepth;
        int previousBreakDepth = breakDepth;
        currentClass = owner;
        currentFunction = null;
        currentInterface = null;
        checkingConstructor = false;
        checkingStaticMember = true;
        sanctionedSuperCall = null;
        definitelyInitialized = Collections.emptySet();
        narrowedTypes = new IdentityHashMap<>();
        writtenVariables = Collections.newSetFromMap(new IdentityHashMap<>());
        loopDepth = 0;
        breakDepth = 0;
        Map<String, TypeParameterType> previousScope = typeParameterScope;
        typeParameterScope = scopeOf(classTypeParameters.getOrDefault(owner.declaration(), List.of()));
        symbols.enterScope();
        checkBlock(block.body());
        symbols.exitScope();
        typeParameterScope = previousScope;
        currentClass = previousClass;
        currentFunction = previousFunction;
        currentInterface = previousInterface;
        checkingConstructor = previousChecking;
        checkingStaticMember = previousStaticContext;
        sanctionedSuperCall = previousSuperCall;
        definitelyInitialized = previousInitialized;
        narrowedTypes = previousNarrowed;
        writtenVariables = previousWritten;
        loopDepth = previousLoopDepth;
        breakDepth = previousBreakDepth;
    }

    /**
     * Checks one callable body. A method or constructor runs with the owning class as {@code this};
     * a constructor additionally tracks definite property initialization and verifies that every
     * property is assigned on every successful path.
     *
     * <p>All the per-body state is saved and restored rather than merely reset, because an anonymous
     * function's body is checked from inside an enclosing body and must leave the outer one intact.
     */
    private void checkCallable(FunctionSymbol function, BlockNode body, ClassSymbol owner, boolean constructor) {
        checkCallable(function, body, owner, constructor, false);
    }

    /**
     * Checks one callable body. When {@code lexicalBoundary} is set the body is checked as its own
     * function boundary: it opens a scope that hides every enclosing function's bindings, and it runs
     * with no receiver and no enclosing type parameters, because an anonymous function is written with
     * concrete types and cannot reach {@code this} or an outer binding until capture exists
     * (docs/LANGUAGE_SPEC.md section 6, "Anonymous functions" and "Explicit immutable closure
     * capture"). Globals stay visible: they are resolved outside the lexical chain.
     */
    private void checkCallable(FunctionSymbol function, BlockNode body, ClassSymbol owner, boolean constructor, boolean lexicalBoundary) {
        FunctionSymbol previousFunction = currentFunction;
        ClassSymbol previousClass = currentClass;
        InterfaceSymbol previousInterface = currentInterface;
        if (function.isInterfaceMember()) {
            currentInterface = declaredInterfaces.get(function.interfaceOwner());
            currentClass = null;
        }
        boolean previousChecking = checkingConstructor;
        CallExprNode previousSuperCall = sanctionedSuperCall;
        Set<PropertySymbol> previousInitialized = definitelyInitialized;
        Map<VariableSymbol, Type> previousNarrowed = narrowedTypes;
        Set<VariableSymbol> previousWritten = writtenVariables;
        currentFunction = function;
        checkingConstructor = constructor;
        sanctionedSuperCall = constructor ? firstSuperCall(body) : null;
        definitelyInitialized = initializedAtStart(owner, constructor);
        narrowedTypes = new IdentityHashMap<>();
        writtenVariables = Collections.newSetFromMap(new IdentityHashMap<>());
        int previousLoopDepth = loopDepth;
        int previousBreakDepth = breakDepth;
        loopDepth = 0;
        breakDepth = 0;
        boolean previousReceiverAvailable = enclosingReceiverAvailable;
        Map<String, TypeParameterType> previousScope = typeParameterScope;
        Set<String> previousRejectedCaptures = currentRejectedCaptures;
        CapturedValue previousBoundaryReceiver = currentBoundaryReceiver;
        if (lexicalBoundary) {
            // No receiver and no enclosing type parameters cross the boundary. Leaving `currentClass`
            // and `currentInterface` null stops `this` and `super` from resolving to the enclosing
            // method's receiver, while `enclosingReceiverAvailable` still records that such a receiver
            // exists — which is what lets their diagnostics report an unlisted capture instead of
            // claiming no receiver exists anywhere. An empty type-parameter scope makes an enclosing
            // method's type parameter report as unknown rather than silently visible
            // (docs/LANGUAGE_SPEC.md section 6).
            currentClass = null;
            currentInterface = null;
            typeParameterScope = Map.of();
            symbols.enterFunctionBoundaryScope();
            enclosingReceiverAvailable = previousClass != null || previousInterface != null || previousReceiverAvailable;
            // The names this closure's capture list named as `var`s. The item check rejected each of them
            // and bound none of them, so a body use has to be classified from this set rather than by
            // resolution -- binding a mirror would be the silent conversion the specification forbids.
            currentRejectedCaptures = function.rejectedCaptureNames();
            // The receiver this closure captured through `[this]`, if any, is the only receiver its body can
            // mean and the only one a nested `[this]` item can name. It is deliberately not installed as
            // `currentClass`: the body's `this` has this type, but nothing else about being inside a class
            // follows, and `super` in particular must stay unavailable to a closure body.
            currentBoundaryReceiver = function.receiverCapture().orElse(null);
        } else {
            currentClass = owner;
            typeParameterScope = mergedScope(scopeOf(ownerTypeParameters(function)), function.typeParameters());
            symbols.enterScope();
            enclosingReceiverAvailable = owner != null || function.isInterfaceMember() || previousReceiverAvailable;
        }
        for (VariableSymbol parameter : function.parameters()) {
            if (!symbols.declare(parameter)) {
                error(DiagnosticCode.RESOL_DUPLICATE_NAME, parameter.declarationSpan(), "parameter '" + parameter.name() + "' is already declared");
            }
        }
        if (lexicalBoundary) {
            // Capture bindings are declared after the parameters so that a capture naming a parameter is
            // reported against the written capture item, which is the defect a reader would point at, and
            // not against the parameter that was there first. A duplicate within the list is reported on
            // its second item. `declare` stays the single authority either way, which is why there is no
            // separate duplicate-capture pass (docs/LANGUAGE_SPEC.md section 6: a duplicate capture item
            // and a capture item naming a parameter are both SOLV-RESOL-002).
            for (CapturedValue captured : function.captures()) {
                if (!symbols.declare(captured.source())) {
                    error(DiagnosticCode.RESOL_DUPLICATE_NAME, captured.captureSpan(), "capture item '" + captured.name() + "' duplicates another capture or a parameter");
                }
            }
        }
        checkBlock(body);
        if (constructor) {
            checkPropertiesInitialized(owner, body.span());
            if (owner != null && superRequiresArguments(owner.superClass().orElse(null)) && sanctionedSuperCall == null) {
                error(DiagnosticCode.SEM_MISSING_SUPER_INIT, body.span(), "class '" + owner.name() + "' must call super(...) as the first statement of its constructor");
            }
        }
        SourceSpan declarationSpan = function.declaration() != null ? function.declaration().span() : function.declarationSpan();
        if (function.isReturnTypeKnown() && function.returnType() != UnitType.INSTANCE && !alwaysReturns(body)) {
            error(DiagnosticCode.TYPE_MISSING_RETURN_PATH, declarationSpan, callableDescription(function) + " must return a value on every path");
        }
        symbols.exitScope();
        typeParameterScope = previousScope;
        currentFunction = previousFunction;
        currentClass = previousClass;
        currentInterface = previousInterface;
        checkingConstructor = previousChecking;
        sanctionedSuperCall = previousSuperCall;
        definitelyInitialized = previousInitialized;
        narrowedTypes = previousNarrowed;
        writtenVariables = previousWritten;
        enclosingReceiverAvailable = previousReceiverAvailable;
        currentRejectedCaptures = previousRejectedCaptures;
        currentBoundaryReceiver = previousBoundaryReceiver;
        // Restored, not reset: a body checked from inside another body (an anonymous function inside a
        // loop, say) must leave the enclosing body's loop nesting intact so a later `break` in it is
        // still measured against the right number of enclosing loops.
        loopDepth = previousLoopDepth;
        breakDepth = previousBreakDepth;
    }

    /**
     * How a callable is named in a diagnostic. A written callable is named; an anonymous function has
     * no name to report, so it is described by what it is.
     */
    private static String callableDescription(FunctionSymbol function) {
        return function.isAnonymous() ? "an anonymous function" : "function '" + function.name() + "'";
    }

    /** The declared type parameters of the class or interface that owns a callable. */
    private List<TypeParameterType> ownerTypeParameters(FunctionSymbol function) {
        if (function.owner() != null) {
            return classTypeParameters.getOrDefault(function.owner(), List.of());
        }
        if (function.interfaceOwner() != null) {
            return interfaceTypeParameters.getOrDefault(function.interfaceOwner(), List.of());
        }
        return List.of();
    }

    /** The {@code super(...)} call that must open a constructor body, or {@code null}. */
    private static CallExprNode firstSuperCall(BlockNode body) {
        if (body.statements().isEmpty()) {
            return null;
        }
        StatementNode first = body.statements().get(0);
        if (first instanceof ExprStmtNode expressionStatement && expressionStatement.expression() instanceof CallExprNode call && call.callee() instanceof SuperExprNode) {
            return call;
        }
        return null;
    }

    private Set<PropertySymbol> initializedAtStart(ClassSymbol owner, boolean constructor) {
        if (!constructor || owner == null) {
            return Collections.emptySet();
        }
        Set<PropertySymbol> initialized = new HashSet<>();
        for (PropertySymbol property : owner.declaredProperties()) {
            if (property.hasInitializer()) {
                initialized.add(property);
            }
        }
        // The superclass constructor runs before the subclass constructor body, so every inherited
        // property is already initialized on entry to the subclass constructor.
        if (owner.superClass().isPresent()) {
            initialized.addAll(owner.superClass().get().properties());
        }
        return initialized;
    }

    private void checkPropertiesInitialized(ClassSymbol owner, SourceSpan span) {
        if (owner == null) {
            return;
        }
        for (PropertySymbol property : owner.properties()) {
            if (!property.hasInitializer() && !definitelyInitialized.contains(property)) {
                error(DiagnosticCode.TYPE_MISSING_PROPERTY_INITIALIZER, span, "property '" + property.name() + "' is not initialized on every constructor path");
            }
        }
    }

    private Set<PropertySymbol> copyInitialized() {
        return new HashSet<>(definitelyInitialized);
    }

    private static Set<PropertySymbol> intersection(Set<PropertySymbol> a, Set<PropertySymbol> b) {
        Set<PropertySymbol> result = new HashSet<>(a);
        result.retainAll(b);
        return result;
    }

    private void checkBlock(BlockNode block) {
        symbols.enterScope();
        for (StatementNode statement : block.statements()) {
            useScope(statement);
            checkStatement(statement);
        }
        // A value-required block carries its terminal expression here; a statement block never does.
        // The tail is checked in the block's own scope, after its statements (section 21).
        block.tail().ifPresent(this::checkExpression);
        symbols.exitScope();
    }

    private void checkStatement(StatementNode statement) {
        switch (statement.kind()) {
            case LOCAL_DECL -> checkLocalDecl((LocalDeclNode) statement);
            case IF_STMT -> checkIf((IfStmtNode) statement);
            case WHILE_STMT -> checkWhile((WhileStmtNode) statement);
            case FOR_STMT -> checkFor((ForStmtNode) statement);
            case FOR_IN_STMT -> checkForIn((ForInStmtNode) statement);
            case SWITCH_STMT -> checkSwitch((SwitchStmtNode) statement);
            case BLOCK -> checkBlock((BlockNode) statement);
            case BREAK_STMT -> checkLoopControl(statement);
            case CONTINUE_STMT -> checkLoopControl(statement);
            case ASSIGN_STMT -> checkAssign((AssignStmtNode) statement);
            case RETURN_STMT -> checkReturn((ReturnStmtNode) statement);
            case THROW_STMT -> checkThrow((ThrowStmtNode) statement);
            case TRY_STMT -> checkTry((TryStmtNode) statement);
            case EXPR_STMT -> checkExprStmt((ExprStmtNode) statement);
            default -> throw new IllegalStateException("not a statement kind: " + statement.kind());
        }
    }

    private void checkLocalDecl(LocalDeclNode declaration) {
        Type declaredType = null;
        if (declaration.declaredType().isPresent()) {
            declaredType = resolveType(declaration.declaredType().get());
        }
        if (declaredType != null) {
            expectedTypes.push(declaredType);
        }
        // The binding being declared is recorded while its initializer is checked, which is what lets a
        // capture item naming it report read-before-initialization (see `pendingDeclarations`).
        pendingDeclarations.push(declaration.name());
        try {
            Type initializerType = checkExpression(declaration.initializer());
            Type variableType;
            if (declaredType != null) {
                variableType = declaredType;
            if (initializerType != null && !assignableOrWidened(declaration.initializer(), initializerType, declaredType)) {
                errorExpected(DiagnosticCode.TYPE_MISMATCH, declaration.initializer().span(), //
                                "initializer is not assignable to declared type " + declaredType.name(), declaredType.name(), initializerType.name());
            }
        } else {
            variableType = initializerType != null ? initializerType : AnyType.INSTANCE;
        }
        boolean mutable = declaration.bindingKind() == BindingKind.VAR;
        VariableSymbol symbol = new VariableSymbol(declaration.name(), declaration.span(), variableType, mutable, false);
        symbol.markInitialized();
        if (!symbols.declare(symbol)) {
            error(DiagnosticCode.RESOL_DUPLICATE_NAME, declaration.span(), "name '" + declaration.name() + "' is already declared in this scope");
        }
        localSymbols.put(declaration, symbol);
        } finally {
            pendingDeclarations.pop();
            if (declaredType != null) {
                expectedTypes.pop();
            }
        }
    }

    private void checkIf(IfStmtNode statement) {
        Type condition = checkExpression(statement.condition());
        requireBoolean(condition, statement.condition());
        Refinement refinement = refinementOf(statement.condition());
        Set<PropertySymbol> before = copyInitialized();
        Map<VariableSymbol, Type> narrowingBefore = copyNarrowing();
        if (refinement != null) {
            applyRefinement(refinement, refinement.whenTrue);
        }
        checkBlock(statement.thenBlock());
        Set<PropertySymbol> afterThen = copyInitialized();
        Map<VariableSymbol, Type> narrowingAfterThen = copyNarrowing();
        restoreNarrowing(narrowingBefore);
        definitelyInitialized = before;
        if (statement.elseBranch().isPresent()) {
            if (refinement != null) {
                applyRefinement(refinement, refinement.whenFalse);
            }
            checkElseBranch(statement.elseBranch().get());
        }
        Set<PropertySymbol> afterElse = copyInitialized();
        Map<VariableSymbol, Type> narrowingAfterElse = copyNarrowing();
        // After the `if`, a refinement is valid only when both branches agree on it; a binding one
        // branch writes loses its refinement rather than being restored from the incoming state.
        narrowedTypes = intersectNarrowing(narrowingAfterThen, narrowingAfterElse);
        definitelyInitialized = intersection(afterThen, afterElse);
        // If the then-branch always transfers control, the code after the `if` is reachable only when
        // the condition was false, so the false refinement holds there (for example
        // `if (x == null) { return }` leaves `x` non-null).
        if (refinement != null && statement.elseBranch().isEmpty() && alwaysReturns(statement.thenBlock())) {
            applyRefinement(refinement, refinement.whenFalse);
        }
    }

    private void checkElseBranch(ElseBranchNode branch) {
        if (branch.isChainedIf()) {
            checkStatement(branch.chainedIf().get());
        } else {
            checkBlock(branch.block().get());
        }
    }

    private void checkWhile(WhileStmtNode statement) {
        Type condition = checkExpression(statement.condition());
        requireBoolean(condition, statement.condition());
        Refinement refinement = refinementOf(statement.condition());
        Set<PropertySymbol> before = copyInitialized();
        Map<VariableSymbol, Type> narrowingBefore = copyNarrowing();
        Set<VariableSymbol> writtenBefore = new HashSet<>(writtenVariables);
        if (refinement != null) {
            applyRefinement(refinement, refinement.whenTrue);
        }
        loopDepth++;
        breakDepth++;
        checkBlock(statement.body());
        breakDepth--;
        loopDepth--;
        restoreNarrowing(narrowingBefore);
        dropWrittenSince(writtenBefore);
        definitelyInitialized = before;
    }

    private void checkFor(ForStmtNode statement) {
        symbols.enterScope();
        if (statement.initializer().isPresent()) {
            StatementNode initializer = statement.initializer().get();
            if (initializer instanceof LocalDeclNode local) {
                checkLocalDecl(local);
            } else if (initializer instanceof AssignStmtNode assign) {
                checkAssign(assign);
            } else if (initializer instanceof ExprStmtNode expressionStatement) {
                checkExpression(expressionStatement.expression());
                error(DiagnosticCode.SEM_FOR_INITIALIZER, initializer.span(), "for initializer must be a local declaration or an assignment");
            }
        }
        Set<PropertySymbol> before = copyInitialized();
        Map<VariableSymbol, Type> narrowingBefore = copyNarrowing();
        Set<VariableSymbol> writtenBefore = new HashSet<>(writtenVariables);
        if (statement.condition().isPresent()) {
            Type condition = checkExpression(statement.condition().get());
            requireBoolean(condition, statement.condition().get());
            Refinement refinement = refinementOf(statement.condition().get());
            if (refinement != null) {
                applyRefinement(refinement, refinement.whenTrue);
            }
        }
        loopDepth++;
        breakDepth++;
        checkBlock(statement.body());
        breakDepth--;
        loopDepth--;
        if (statement.update().isPresent()) {
            StatementNode update = statement.update().get();
            if (update instanceof AssignStmtNode assign) {
                checkAssign(assign);
            } else if (update instanceof ExprStmtNode expressionStatement) {
                checkExpression(expressionStatement.expression());
                error(DiagnosticCode.SEM_FOR_UPDATE, update.span(), "for update clause must be an assignment");
            }
        }
        restoreNarrowing(narrowingBefore);
        dropWrittenSince(writtenBefore);
        definitelyInitialized = before;
        symbols.exitScope();
    }

    /**
     * Checks a range {@code for}-in loop (docs/LANGUAGE_SPEC.md section 17). Both bounds must be
     * {@code Integer}; the loop variable is an implicitly declared immutable {@code Integer} binding scoped
     * to the body. The body is a loop context, so {@code break} and {@code continue} are valid.
     */
    private void checkForIn(ForInStmtNode statement) {
        symbols.enterScope();
        Type startType = checkExpression(statement.start());
        if (startType != null && startType != IntegerType.INSTANCE) {
            errorExpected(DiagnosticCode.SEM_INVALID_RANGE_BOUND, statement.start().span(), "range start bound must have type Integer", "Integer", startType.name());
        }
        Type endType = checkExpression(statement.end());
        if (endType != null && endType != IntegerType.INSTANCE) {
            errorExpected(DiagnosticCode.SEM_INVALID_RANGE_BOUND, statement.end().span(), "range end bound must have type Integer", "Integer", endType.name());
        }
        VariableSymbol variable = new VariableSymbol(statement.variableName(), statement.span(), IntegerType.INSTANCE, false, false);
        variable.markInitialized();
        if (!symbols.declare(variable)) {
            error(DiagnosticCode.RESOL_DUPLICATE_NAME, statement.span(), "name '" + statement.variableName() + "' is already declared in this scope");
        }
        forInBindings.put(statement, variable);
        Set<PropertySymbol> before = copyInitialized();
        Map<VariableSymbol, Type> narrowingBefore = copyNarrowing();
        Set<VariableSymbol> writtenBefore = new HashSet<>(writtenVariables);
        loopDepth++;
        breakDepth++;
        checkBlock(statement.body());
        breakDepth--;
        loopDepth--;
        restoreNarrowing(narrowingBefore);
        dropWrittenSince(writtenBefore);
        definitelyInitialized = before;
        symbols.exitScope();
    }

    /**
     * Checks a non-fallthrough {@code switch} statement (docs/LANGUAGE_SPEC.md section 13). The
     * scrutinee is checked once; a constant label must be a compile-time constant assignable to the
     * switched value's type; a regex label requires a {@code String} value and its pattern is
     * validated and compiled here against the portable dialect. At most one {@code default} is
     * allowed and it must be last. Each case body is an implicit block checked in a fresh scope with
     * the incoming initialization state; no case may {@code break} out of the switch.
     */
    private void checkSwitch(SwitchStmtNode statement) {
        checkSwitchCases(statement.scrutinee(), statement.cases());
    }

    /**
     * Checks the scrutinee and cases shared by the statement and expression {@code switch} forms.
     * The scrutinee is checked once; a constant label must be a compile-time constant assignable to
     * the switched value's type; a regex label requires a {@code String} value and its pattern is
     * validated and compiled here against the portable dialect. At most one {@code default} is
     * allowed and it must be last. Each case body is an implicit block checked in a fresh scope with
     * the incoming initialization state; no case may {@code break} out of the switch.
     */
    private void checkSwitchCases(ExpressionNode scrutineeNode, List<SwitchCaseNode> cases) {
        Type scrutineeType = checkExpression(scrutineeNode);
        Set<PropertySymbol> before = copyInitialized();
        Map<VariableSymbol, Type> narrowingBefore = copyNarrowing();
        Set<VariableSymbol> writtenBefore = new HashSet<>(writtenVariables);
        boolean sawDefault = false;
        for (int i = 0; i < cases.size(); i++) {
            SwitchCaseNode switchCase = cases.get(i);
            if (switchCase.isDefault()) {
                if (sawDefault) {
                    error(DiagnosticCode.SEM_SWITCH_DUPLICATE_DEFAULT, switchCase.span(), "a switch may contain at most one default");
                }
                sawDefault = true;
                if (i != cases.size() - 1) {
                    error(DiagnosticCode.SEM_SWITCH_DEFAULT_NOT_LAST, switchCase.span(), "default must be the last case of a switch");
                }
            } else {
                // A non-default case after the default is only possible when the default itself is
                // misplaced; that single SEM_SWITCH_DEFAULT_NOT_LAST diagnostic is reported at the
                // default (the if-branch above), so it is not repeated here for each trailing case.
                for (CaseLabelNode label : switchCase.labels()) {
                    checkCaseLabel(label, scrutineeType);
                }
            }
            // Every case starts from the state on entry to the switch: a case may or may not run, and a
            // case body never falls through into the next one.
            definitelyInitialized = new HashSet<>(before);
            restoreNarrowing(narrowingBefore);
            int previousBreakDepth = breakDepth;
            breakDepth = 0;
            checkBlock(switchCase.body());
            breakDepth = previousBreakDepth;
        }
        restoreNarrowing(narrowingBefore);
        dropWrittenSince(writtenBefore);
        definitelyInitialized = before;
    }

    /**
     * Checks one case label. A constant label must be a compile-time constant expression assignable
     * to the switched value's type; a regex label requires a {@code String} value and is validated
     * and compiled once here against the portable dialect. The regex requirement is tested against the
     * switched type itself, not its non-null view: a nullable {@code String?} admits {@code null}, and
     * a regex case lowers to a complete match over the scrutinee as a {@code String}, so accepting a
     * nullable scrutinee would turn a statically knowable mismatch into a runtime failure on a null
     * scrutinee (docs/LANGUAGE_SPEC.md sections 13 and 19).
     */
    private void checkCaseLabel(CaseLabelNode label, Type scrutineeType) {
        if (label instanceof ConstantCaseLabelNode constant) {
            Type labelType = checkExpression(constant.expression());
            if (!isConstantExpression(constant.expression())) {
                error(DiagnosticCode.SEM_SWITCH_CASE_NOT_CONSTANT, label.span(), "a switch case label must be a compile-time constant");
            }
            if (labelType != null && scrutineeType != null && !labelType.isAssignableTo(scrutineeType)) {
                errorExpected(DiagnosticCode.TYPE_CASE_LABEL_MISMATCH, label.span(), //
                                "case label is not assignable to the switched type " + scrutineeType.name(), scrutineeType.name(), labelType.name());
            }
            return;
        }
        RegexCaseLabelNode regex = (RegexCaseLabelNode) label;
        checkExpression(regex.pattern());
        if (scrutineeType != StringType.INSTANCE) {
            errorExpected(DiagnosticCode.TYPE_REGEX_CASE_REQUIRES_STRING, label.span(), //
                            "a regex case requires a String switch value", "String", scrutineeType == null ? "an unresolved type" : scrutineeType.name());
            return;
        }
        Optional<String> pattern = constantString(regex.pattern());
        if (pattern.isPresent()) {
            try {
                regexCasePatterns.put(regex, RegexSyntax.compile(pattern.get()));
            } catch (RegexSyntax.InvalidPatternException e) {
                error(DiagnosticCode.TYPE_INVALID_REGEX_PATTERN, regex.pattern().span(), "invalid Regex pattern: " + e.getMessage());
            }
        }
    }

    /**
     * Whether an expression is a compile-time constant for a {@code switch} case label: a literal, a
     * parenthesized constant, or an arithmetic negation of a constant.
     */
    private static boolean isConstantExpression(ExpressionNode expression) {
        if (expression instanceof LiteralNode || expression instanceof NullLiteralNode) {
            return true;
        }
        if (expression instanceof ParenExprNode paren) {
            return isConstantExpression(paren.inner());
        }
        if (expression instanceof UnaryExprNode unary && unary.operator() == UnaryOperator.NEGATE) {
            return isConstantExpression(unary.operand());
        }
        return false;
    }

    private void checkLoopControl(StatementNode statement) {
        if (statement instanceof BreakStmtNode) {
            if (breakDepth == 0) {
                if (loopDepth > 0) {
                    // The break is lexically inside an enclosing loop but a switch case intervenes, so
                    // it would exit the switch rather than the loop (docs/LANGUAGE_SPEC.md section 13).
                    error(DiagnosticCode.SEM_BREAK_IN_SWITCH_CASE, statement.span(), "'break' cannot exit a switch case; put it in a loop nested inside the case");
                } else {
                    error(DiagnosticCode.SEM_LOOP_CONTROL_OUTSIDE_LOOP, statement.span(), "'break' is only valid inside a loop");
                }
            }
            return;
        }
        if (loopDepth == 0) {
            error(DiagnosticCode.SEM_LOOP_CONTROL_OUTSIDE_LOOP, statement.span(), "'continue' is only valid inside a loop");
        }
    }

    private void checkReturn(ReturnStmtNode statement) {
        if (currentFunction == null) {
            // Reached from a class initializer block, which is a statement list rather than a callable
            // and returns nothing: `return` exits the block early, and `return value` has no value to
            // return (docs/LANGUAGE_SPEC.md section 7).
            if (statement.value().isPresent()) {
                checkExpression(statement.value().get());
                error(DiagnosticCode.TYPE_UNEXPECTED_RETURN_VALUE, statement.span(), "a class initializer block cannot return a value");
            }
            return;
        }
        Type expected = currentFunction.returnType();
        if (statement.value().isPresent()) {
            ExpressionNode value = statement.value().get();
            // Thread the declared return type as the expected type so a generic variant construction
            // such as {@code return Err(e)} in a {@code Result}-returning function resolves all of its
            // type arguments from context.
            expectedTypes.push(expected);
            Type actual;
            try {
                actual = checkExpression(value);
            } finally {
                expectedTypes.pop();
            }
            if (expected == UnitType.INSTANCE) {
                error(DiagnosticCode.TYPE_UNEXPECTED_RETURN_VALUE, statement.span(), "a Unit function cannot return a value");
            } else if (actual != null && !assignableOrWidened(value, actual, expected)) {
                errorExpected(DiagnosticCode.TYPE_RETURN_MISMATCH, value.span(), //
                                "returned value is not assignable to " + expected.name(), expected.name(), actual.name());
            }
        } else if (expected != UnitType.INSTANCE && currentFunction.isReturnTypeKnown()) {
            error(DiagnosticCode.TYPE_MISSING_RETURN_VALUE, statement.span(), "a function returning " + expected.name() + " must return a value");
        }
        if (checkingConstructor) {
            checkPropertiesInitialized(currentClass, statement.span());
        }
    }

    /**
     * Checks a {@code throw expression} statement (docs/LANGUAGE_SPEC.md error-handling phases). The
     * operand is checked in the ordinary way; its assignability to the built-in {@code Exception} and
     * the ordering/duplication of surrounding handlers are verified by {@link #checkCatchSemantics},
     * which needs the full exception type graph. A {@code throw} ends its enclosing path, so it counts
     * as a non-falling transition for return-type analysis.
     */
    private void checkThrow(ThrowStmtNode statement) {
        ExpressionNode value = statement.value();
        Type actualType = checkExpression(value);
        if (actualType != null && !isAssignableToException(actualType)) {
            error(DiagnosticCode.SEM_THROW_NON_EXCEPTION, value.span(), "throw operand must be assignable to Exception");
        }
    }

    /**
     * Checks a {@code try} statement: the operation runs in the current scope, each handler binding is
     * declared in its own block scope so it shadows outer names and is invisible outside that handler,
     * and the finally clause (if any) runs in the current scope.
     */
    private void checkTry(TryStmtNode statement) {
        checkBlock(statement.tryBlock());
        // Handler types are compared against the earlier handlers of this same try, so each handler is
        // remembered in source order as it is validated (docs/LANGUAGE_SPEC.md section 6). Each binding is
        // also recorded for lowering so its frame slot can be allocated before the handler body runs.
        List<VariableSymbol> bindings = new ArrayList<>();
        List<String> seenHandlerTypes = new ArrayList<>();
        for (TryStmtNode.CatchClause clause : statement.catchClauses()) {
            symbols.enterScope();
            Type exceptionType = resolveType(clause.exceptionType());
            if (exceptionType == null || !(exceptionType instanceof ClassType classType) || !isExceptionName(classType.name())) {
                error(DiagnosticCode.SEM_INVALID_CATCH_TYPE, clause.exceptionType().span(), "catch handler must name an exception type");
            } else {
                String handlerName = classType.name();
                for (String earlier : seenHandlerTypes) {
                    if (subtypeOf(handlerName, earlier)) {
                        error(DiagnosticCode.SEM_UNREACHABLE_CATCH, clause.exceptionType().span(), "this handler is unreachable because '" + earlier + "' is caught first");
                        break;
                    }
                }
                seenHandlerTypes.add(handlerName);
            }
            VariableSymbol binding = new VariableSymbol(clause.bindingName(), clause.exceptionType().span(), exceptionType == null ? AnyType.INSTANCE : exceptionType, false, false);
            // The handler runs only with a caught value already bound to it, exactly as a parameter does,
            // so the binding starts out initialized and a plain rethrow can read it.
            binding.markInitialized();
            if (!symbols.declare(binding)) {
                error(DiagnosticCode.RESOL_DUPLICATE_NAME, clause.exceptionType().span(), "exception already caught under '" + clause.bindingName() + "'");
            }
            bindings.add(binding);
            checkBlock(clause.body());
            symbols.exitScope();
        }
        if (!bindings.isEmpty()) {
            catchBindings.put(statement, bindings);
        }
        if (statement.catchClauses().isEmpty() && statement.finallyBlock() == null) {
            error(DiagnosticCode.SEM_TRY_NEEDS_HANDLER, statement.span(), "a try block must have at least one catch clause or a finally clause");
        }
        if (statement.finallyBlock() != null) {
            checkBlock(statement.finallyBlock());
        }
    }

    /**
     * Builds the guest exception graph from each class's declared superclass reference, plus the edges
     * among the three built-in bases. The bases have no source declaration, so the declared graph alone
     * cannot represent them: a user class extending {@code RuntimeException} then has no path to
     * {@code Exception}, and a handler written on the root type would catch nothing. Adding the built-in
     * edges makes the graph the complete nominal hierarchy the specification defines (section 22.1), so
     * reachability, catch matching and unreachability diagnostics all agree.
     */
    private void buildExceptionGraph(CompilationUnitNode unit) {
        Map<String, String> parent = new HashMap<>(ExceptionBases.baseEdges());
        for (DeclarationNode declaration : unit.declarations()) {
            if (declaration instanceof ClassDeclNode classDecl && classDecl.superClass().isPresent()) {
                TypeRef superclass = classDecl.superClass().get();
                if (superclass instanceof TypeRefNode nominal) {
                    parent.put(classDecl.name(), nominal.name());
                }
            }
        }
        exceptionParents = parent;
        Set<String> exceptions = new HashSet<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Map.Entry<String, String> entry : parent.entrySet()) {
                if (!exceptions.contains(entry.getKey()) && looksLikeException(entry.getValue(), exceptions)) {
                    exceptions.add(entry.getKey());
                    changed = true;
                }
            }
        }
        exceptionClassNames = exceptions;
    }

    /** The three built-in guest exception base types have no source declaration; this mirrors the
     * runtime registry (SolvikExceptions) without importing Truffle runtime classes into semantic analysis. */
    private boolean isExceptionBaseType(String name) {
        return ExceptionBases.isBase(name);
    }

    /** A type name refers to a guest exception when it is built-in or extends one, directly or transitively. */
    private boolean looksLikeException(String name, Set<String> exceptions) {
        return isExceptionBaseType(name) || exceptions.contains(name);
    }

    /**
     * Whether {@code name} would collide with the compiler-synthesized message of a guest exception
     * type: the private message field is named {@code message} and its read accessor {@code getMessage}
     * (docs/LANGUAGE_SPEC.md section 22). Both are reserved on every exception class.
     */
    private static boolean isSynthesizedMessageMember(String name) {
        return "message".equals(name) || "getMessage".equals(name);
    }

    /** Whether {@code name} denotes a catchable guest exception type. */
    private boolean isExceptionName(String name) {
        return isExceptionBaseType(name) || exceptionClassNames.contains(name);
    }

    /** True when an operand of {@code throw} may be caught by a guest handler. */
    private boolean isAssignableToException(Type type) {
        return type instanceof ClassType classType && isExceptionName(classType.name());
    }

    /** Nominal subsequence: does the exception hierarchy path from {@code name} reach {@code ancestor}? */
    private boolean subtypeOf(String name, String ancestor) {
        String current = name;
        for (int guard = 0; guard < exceptionParents.size() + 1; guard++) {
            if (current.equals(ancestor)) {
                return true;
            }
            String next = exceptionParents.get(current);
            if (next == null) {
                return false;
            }
            current = next;
        }
        return false;
    }

    private void checkAssign(AssignStmtNode statement) {
        ExpressionNode value = statement.value();
        ExpressionNode target = statement.target();
        // The declared type of a binding or property is resolved before the value is typed, and only so
        // that a generic function reference written on the right-hand side has the expected function type
        // section 6 requires. Gating on that one shape keeps every other assignment analysing in the
        // order it always has — target type resolution can itself report, and diagnostic order is part of
        // what a program prints — so no existing expression form, a generic variant construction in
        // particular, newly infers from an assignment target.
        Type assignmentTargetType = isGenericFunctionReference(value) ? declaredAssignmentTargetType(target) : null;
        // An instance-property target is answered null above because its declared type needs the receiver
        // typed first; checkPropertyAssign supplies the expected type itself in that case.
        boolean genericReferenceValue = assignmentTargetType == null && isGenericFunctionReference(value);
        Type valueType;
        if (assignmentTargetType != null) {
            expectedTypes.push(assignmentTargetType);
            try {
                valueType = checkExpression(value);
            } finally {
                expectedTypes.pop();
            }
        } else if (genericReferenceValue && target instanceof MemberAccessExprNode) {
            // Left untyped here so the receiver is typed exactly once, by checkPropertyAssign.
            valueType = null;
        } else {
            valueType = checkExpression(value);
        }
        if (target instanceof NameRefExprNode name) {
            Optional<Symbol> resolved = resolveName(name.name());
            if (resolved.isEmpty()) {
                if (!reportCapturedMutableUse(name.name(), name.span()) && !reportUnlistedCapture(name.name(), name.span())) {
                    error(DiagnosticCode.RESOL_UNKNOWN_NAME, name.span(), "unknown name '" + name.name() + "'");
                }
                return;
            }
            Symbol symbol = resolved.get();
            if (!(symbol instanceof VariableSymbol variable)) {
                error(DiagnosticCode.TYPE_INVALID_ASSIGNMENT_TARGET, target.span(), "cannot assign to '" + name.name() + "'");
                return;
            }
            nameSymbols.put(name, variable);
            expressionTypes.put(name, variable.type());
            // Any write invalidates a prior flow-sensitive refinement of the binding, and is recorded
            // so a loop that writes the binding drops the refinement after the loop as well.
            narrowedTypes.remove(variable);
            writtenVariables.add(variable);
            if (!variable.isMutable()) {
                error(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE, target.span(), "cannot assign to immutable '" + name.name() + "'");
            }
            if (valueType != null && !assignableOrWidened(value, valueType, variable.type())) {
                errorExpected(DiagnosticCode.TYPE_MISMATCH, value.span(), //
                                "value is not assignable to " + variable.type().name(), variable.type().name(), valueType.name());
            }
            variable.markInitialized();
            return;
        }
        if (target instanceof MemberAccessExprNode member) {
            checkMemberAssign(member, value, valueType, genericReferenceValue);
            return;
        }
        error(DiagnosticCode.TYPE_INVALID_ASSIGNMENT_TARGET, target.span(), "assignment target must be a mutable local or a var property");
    }

    /**
     * Classifies a {@code target = value} assignment whose target is a member access and dispatches to the
     * one property check that owns it. The classification itself is unchanged from the sequence this method
     * replaces: a safe access is refused, a class name in target position names a static member rather than
     * a value (section 7), a module-qualified class name reaches the same static members a bare one does, and
     * anything else is an instance property write.
     *
     * <p>What is new is that this method, and not the checks it calls, is responsible for the value being
     * typed. A reference to a generic function on the right-hand side is held back rather than typed up front
     * — its type depends on the property it writes, and naming that property is exactly what the
     * classification above is for — so a check that stops before resolving one never got to supply the
     * expected type the reference was waiting for. The reference is typed here against no expectation
     * instead, which is what an unresolvable expression position does everywhere else in this analyzer. The
     * alternative is strictly worse than the code this replaced: an untyped expression reports nothing, so a
     * program with two defects — a target that does not resolve and a reference no context instantiates —
     * would show only one of them.
     */
    private void checkMemberAssign(MemberAccessExprNode member, ExpressionNode value, Type valueType, boolean typesValueHere) {
        if (!checkMemberTarget(member, value, valueType, typesValueHere) && typesValueHere) {
            checkExpression(value);
        }
    }

    /**
     * Routes a member assignment to its property check and returns whether that check typed the value.
     *
     * <p>Only a value the caller held back can come back untyped: {@code typesValueHere} is true exactly when
     * the caller left {@code valueType} unset, so every other assignment reaches a check with its value
     * already typed and the caller's fallback is never reached.
     */
    private boolean checkMemberTarget(MemberAccessExprNode member, ExpressionNode value, Type valueType, boolean typesValueHere) {
        if (member.isSafe()) {
            error(DiagnosticCode.TYPE_INVALID_ASSIGNMENT_TARGET, member.span(), "cannot assign through a '?.' safe access");
            return false;
        }
        if (member.receiver() instanceof NameRefExprNode name) {
            // A class name in assignment target position is a static member write; the class name
            // is not a value, so this must be decided before the receiver is typed (section 7).
            ClassSymbol classSymbol = classSymbolNamed(name);
            if (classSymbol != null) {
                return checkStaticPropertyAssign(member, classSymbol, member.memberName(), value, valueType, typesValueHere);
            }
        }
        QualifiedPrefix qualified = qualifiedPrefix(member);
        if (qualified != null && qualified.path.size() == 2 && !qualified.lastStepIsNamespace) {
            ModuleContents contents = modules.get(qualified.module);
            Symbol symbol = contents == null ? null : contents.symbols.get(qualified.path.get(0));
            if (symbol instanceof ClassSymbol classSymbol) {
                // A module-qualified class name reaches the same static members as the bare name,
                // so an unknown module or a non-class prefix stays on the ordinary path, which
                // reports the module/name error rather than a member error. A target written with
                // `::` throughout (`math::Counter::count`) is not a member access node at all and
                // is refused by the general assignment-target rule below.
                return checkStaticPropertyAssign(member, classSymbol, qualified.path.get(1), value, valueType, typesValueHere);
            }
        }
        return checkPropertyAssign(member, value, valueType, typesValueHere);
    }

    /**
     * The declared type an assignment target receives values at, for the one case where knowing it before
     * the value is typed changes what the value can be: a generic function reference used as a value needs
     * an expected function type (docs/LANGUAGE_SPEC.md section 6).
     *
     * <p>Only a binding is answered here, because a binding's declared type is settled without analyzing
     * anything else. A property is not: naming one requires the classification that
     * {@link #checkMemberAssign} performs — a static property through its owner's name, an instance one only
     * once its receiver is typed — and having this helper repeat that classification in a second place would
     * leave two definitions of "which property does this target write" free to drift apart. So the whole
     * member-access target, including the static case whose type is equally easy to state, is left to the one
     * place that has to make the decision anyway.
     *
     * <p>Returning nothing is never an error. A target this helper cannot answer keeps the order and the
     * diagnostics it has always had, and the reference on the right then reports its own missing expected type.
     */
    private Type declaredAssignmentTargetType(ExpressionNode target) {
        if (target instanceof NameRefExprNode name) {
            // An unknown name is reported by the assignment path itself, in its existing position.
            return resolveName(name.name()).orElse(null) instanceof VariableSymbol variable ? variable.type() : null;
        }
        return null;
    }

    /**
     * Checks a {@code receiver.member = value} assignment and enforces property mutability. Returns whether
     * {@code value} was typed by the end of it.
     *
     * <p>{@code typesValueHere} is set for one shape only: the value is a reference to a generic function,
     * which section 6 lets an expression position decide only once an expected function type is in scope, and
     * a property's declared type may be written in its owner's type parameters and so becomes available only
     * after the receiver's type arguments are known. This method, rather than the caller, is where an instance
     * property can supply that type, so it types the value with the property's substituted type pushed. Every
     * other value arrives already typed and analysis is unchanged.
     *
     * <p>The paths that stop before a property is resolved return {@code false}. They have reported their own
     * error, but a held-back value still has to be typed so that it reports its own too, and only the caller
     * can do that. The return value is therefore about what a reader is told, not about whether analysis may
     * continue: each of these programs is already unlowerable.
     */
    private boolean checkPropertyAssign(MemberAccessExprNode member, ExpressionNode value, Type valueType, boolean typesValueHere) {
        Type receiverType = checkExpression(member.receiver());
        if (isNullableType(receiverType)) {
            error(DiagnosticCode.TYPE_NULLABLE_DEREFERENCE, member.receiver().span(), //
                            "receiver of '" + member.memberName() + "' may be null; use '?.' or check for null first");
            return false;
        }
        if (receiverType == RegexType.INSTANCE) {
            if (isRegexMethodName(member.memberName())) {
                error(DiagnosticCode.TYPE_INVALID_ASSIGNMENT_TARGET, member.span(), "cannot assign to method '" + member.memberName() + "'");
            } else {
                error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, member.span(), "Regex has no property '" + member.memberName() + "'");
            }
            return false;
        }
        if (receiverType == RegexMatchType.INSTANCE) {
            switch (member.memberName()) {
                case "value", "start", "end", "groupCount" -> {
                    error(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE, member.span(), "cannot assign to immutable RegexMatch property '" + member.memberName() + "'");
                }
                case "group" -> {
                    error(DiagnosticCode.TYPE_INVALID_ASSIGNMENT_TARGET, member.span(), "cannot assign to method 'group'");
                }
                default -> {
                    error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, member.span(), "RegexMatch has no property '" + member.memberName() + "'");
                }
            }
            return false;
        }
        BuiltinCollectionMember collectionMember = collectionMember(receiverType, member.memberName());
        if (collectionMember != null) {
            if (collectionMember.isProperty()) {
                error(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE, member.span(), "cannot assign to immutable '" + member.memberName() + "'");
            } else {
                error(DiagnosticCode.TYPE_INVALID_ASSIGNMENT_TARGET, member.span(), "cannot assign to method '" + member.memberName() + "'");
            }
            return false;
        }
        ClassSymbol classSymbol = classSymbolFor(receiverType);
        if (classSymbol == null) {
            if (receiverType != null) {
                error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, member.span(), "type " + receiverType.name() + " has no member '" + member.memberName() + "'");
            }
            return false;
        }
        Optional<PropertySymbol> resolved = classSymbol.property(member.memberName());
        if (resolved.isEmpty()) {
            if (classSymbol.method(member.memberName()).isPresent()) {
                error(DiagnosticCode.TYPE_INVALID_ASSIGNMENT_TARGET, member.span(), "cannot assign to method '" + member.memberName() + "'");
            } else {
                error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, member.span(), "class " + classSymbol.name() + " has no property '" + member.memberName() + "'");
            }
            return false;
        }
        PropertySymbol property = resolved.get();
        propertyAccesses.put(member, property);
        Type propertyType = property.type().substitute(composeSubstitutions(classSymbol.propertySubstitution(property), substitutionFor(receiverType)));
        if (typesValueHere) {
            expectedTypes.push(propertyType);
            try {
                valueType = checkExpression(value);
            } finally {
                expectedTypes.pop();
            }
        }
        if (valueType != null && !assignableOrWidened(value, valueType, propertyType)) {
            errorExpected(DiagnosticCode.TYPE_MISMATCH, value.span(), //
                            "value is not assignable to property type " + propertyType.name(), propertyType.name(), valueType.name());
        }
        if (!property.isMutable()) {
            boolean firstConstructorAssignment = checkingConstructor && !property.hasInitializer() && !definitelyInitialized.contains(property);
            if (firstConstructorAssignment) {
                definitelyInitialized.add(property);
            } else {
                error(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE, member.span(), "cannot assign to immutable property '" + member.memberName() + "'");
            }
        } else if (checkingConstructor && !property.hasInitializer()) {
            definitelyInitialized.add(property);
        }
        return true;
    }

    private void checkExprStmt(ExprStmtNode statement) {
        Type type = checkExpression(statement.expression());
        ExpressionNode expression = statement.expression();
        // A propagation consumes the Result it unwraps, so {@code File.write(path, data)?} is a
        // legitimate standalone statement even though the operand value is not otherwise used.
        if (expression instanceof PropagationExprNode) {
            return;
        }
        if (!(expression instanceof CallExprNode)) {
            error(DiagnosticCode.SEM_VALUE_EXPRESSION_STATEMENT, expression.span(), "only a call may be used as a standalone statement");
            return;
        }
        // A Result must never be silently discarded (error-handling phases): only explicit consumption
        // via propagation, {@code .ignore()}, or a match is allowed.
        if (type != null && isResultType(type)) {
            error(DiagnosticCode.SEM_UNUSED_RESULT, expression.span(), //
                            "a Result<T, E> must be consumed; use ?, .ignore(), or handle it in a match");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Expression typing
    // ---------------------------------------------------------------------------------------------

    /**
     * Types the postfix propagation operator {@code expression?} (error-handling phases). The operand
     * is checked first so it is evaluated exactly once; its static type must be {@code Result<T, E>},
     * an enum named {@code Result} with two payload-carrying variants. The result type is the success
     * payload {@code T}. Propagation unwinds to the nearest enclosing function declared to return a
     * {@code Result}: that function's own {@code Result<T2, E2>} must accept the unwrapped value
     * ({@code T} assignable to {@code T2}) and the propagated error ({@code E} assignable to
     * {@code E2}). The operand type is preserved through propagation; it never widens the boundary's
     * declared result.
     */
    private Type checkPropagation(PropagationExprNode expression) {
        Type operandType = checkExpression(expression.operand());
        if (!isResultType(operandType)) {
            error(DiagnosticCode.SEM_RESULT_PROPAGATION_INVALID_OPERAND, expression.span(), //
                            "the ? propagation operator requires a Result<T, E> value but the operand has type " + labelOf(operandType));
            return operandType;
        }
        List<Type> arguments = ((ParameterizedType) operandType).arguments();
        Type successType = arguments.get(0);
        Type errorType = arguments.get(1);
        FunctionSymbol enclosing = currentFunction;
        if (enclosing == null || !isResultType(enclosing.returnType())) {
            error(DiagnosticCode.SEM_RESULT_PROPAGATION_NO_BOUNDARY, expression.span(), //
                            "there is no enclosing function returning a Result<T, E> for the ? propagation operator to propagate through");
            return successType;
        }
        List<Type> boundary = ((ParameterizedType) enclosing.returnType()).arguments();
        Type boundarySuccess = boundary.get(0);
        Type boundaryError = boundary.get(1);
        if (!successType.isAssignableTo(boundarySuccess)) {
            error(DiagnosticCode.SEM_RESULT_PROPAGATION_TYPE_MISMATCH, expression.span(), //
                            "the propagated value of type " + labelOf(successType) + " is not assignable to the result success type " + boundarySuccess.name());
        }
        if (!errorType.isAssignableTo(boundaryError)) {
            error(DiagnosticCode.SEM_RESULT_PROPAGATION_TYPE_MISMATCH, expression.span(), //
                            "the propagated error of type " + labelOf(errorType) + " is not assignable to the result error type " + boundaryError.name());
        }
        return successType;
    }

    /** Whether {@code type} is a {@code Result<T, E>} application: a two-argument enum named {@code Result}. */
    private boolean isResultType(Type type) {
        if (!(type instanceof ParameterizedType parameterized)) {
            return false;
        }
        Type base = parameterized.base();
        return "Result".equals(base.name()) && parameterized.arguments().size() == 2;
    }

    /** A human label for a possibly-null type used in error messages. */
    private static String labelOf(Type type) {
        return type == null ? "<none>" : type.name();
    }

    private Type checkExpression(ExpressionNode expression) {
        switch (expression.kind()) {
            case INTEGER_LITERAL:
                return record(expression, checkIntegerLiteral((IntegerLiteralNode) expression));
            case LONG_LITERAL:
                return record(expression, checkLongLiteral((LongLiteralNode) expression));
            case FLOATING_LITERAL:
                return record(expression, checkFloatingLiteral((FloatingLiteralNode) expression));
            case CHARACTER_LITERAL:
                return record(expression, checkCharacterLiteral((CharacterLiteralNode) expression));
            case BOOL_LITERAL:
                return record(expression, BooleanType.INSTANCE);
            case NULL_LITERAL:
                return record(expression, NullType.INSTANCE);
            case STRING_LITERAL:
                return record(expression, checkStringLiteral((StringLiteralNode) expression));
            case RAW_STRING_LITERAL:
                return record(expression, StringType.INSTANCE);
            case NAME_REF_EXPR:
                return record(expression, checkName((NameRefExprNode) expression));
            case THIS_EXPR:
                return record(expression, checkThis((ThisExprNode) expression));
            case SUPER_EXPR:
                return record(expression, checkSuperAsValue((SuperExprNode) expression));
            case PAREN_EXPR:
                return record(expression, checkExpression(((ParenExprNode) expression).inner()));
            case UNARY_EXPR:
                return record(expression, checkUnary((UnaryExprNode) expression));
            case BINARY_EXPR:
                return record(expression, checkBinary((BinaryExprNode) expression));
            case CALL_EXPR:
                return record(expression, checkCall((CallExprNode) expression));
            case PROPAGATION_EXPR:
                return record(expression, checkPropagation((PropagationExprNode) expression));
            case MAP_ENTRY_EXPR:
                return record(expression, checkMapEntry((MapEntryExprNode) expression));
            case TYPE_TEST_EXPR:
                return record(expression, checkTypeTest((TypeTestExprNode) expression));
            case CAST_EXPR:
                return record(expression, checkCast((CastExprNode) expression));
            case MEMBER_ACCESS_EXPR:
                return record(expression, checkMemberAccess((MemberAccessExprNode) expression));
            case NAMESPACE_ACCESS_EXPR:
                return record(expression, checkNamespaceAccess((NamespaceAccessExprNode) expression));
            case MATCH_EXPR:
                return record(expression, checkMatch((MatchExprNode) expression));
            case ANONYMOUS_FUNCTION_EXPR:
                return record(expression, checkAnonymousFunction((AnonymousFunctionExprNode) expression));
            case BLOCK_EXPR:
                return record(expression, checkBlockExpr((BlockExprNode) expression));
            case IF_EXPR:
                return record(expression, checkIfExpr((IfExprNode) expression));
            case SWITCH_EXPR:
                return record(expression, checkSwitchExpr((SwitchExprNode) expression));
            default:
                throw new IllegalStateException("not an expression kind: " + expression.kind());
        }
    }

    private Type record(ExpressionNode expression, Type type) {
        if (type != null) {
            expressionTypes.put(expression, type);
        }
        return type;
    }

    /**
     * Types a {@code key: value} entry used outside a built-in {@code Map} construction. Both sides
     * are still checked so their own type errors are reported, then the entry itself is rejected.
     */
    private Type checkMapEntry(MapEntryExprNode entry) {
        checkExpression(entry.key());
        checkExpression(entry.value());
        error(DiagnosticCode.SEM_MAP_ENTRY, entry.span(), "a `key: value` entry is only valid in a Map construction");
        return null;
    }

    private Type checkIntegerLiteral(IntegerLiteralNode literal) {
        BigInteger value = new BigInteger(literal.lexeme());
        if (value.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
            errorExpected(DiagnosticCode.TYPE_INTEGER_LITERAL_OUT_OF_RANGE, literal.span(), //
                            "integer literal is outside the signed 32-bit Integer range", "-2147483648..2147483647", literal.lexeme());
        }
        return IntegerType.INSTANCE;
    }

    private Type checkLongLiteral(LongLiteralNode literal) {
        String digits = literal.lexeme().substring(0, literal.lexeme().length() - 1);
        BigInteger value = new BigInteger(digits);
        if (value.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            errorExpected(DiagnosticCode.TYPE_LONG_LITERAL_OUT_OF_RANGE, literal.span(), //
                            "Long literal is outside the signed 64-bit range", "0..9223372036854775807", literal.lexeme());
        }
        return LongType.INSTANCE;
    }

    private Type checkFloatingLiteral(FloatingLiteralNode literal) {
        return literal.isFloat() ? FloatType.INSTANCE : DoubleType.INSTANCE;
    }

    /**
     * Validates a character literal: exactly one UTF-16 code unit, or one supported escape
     * (docs/LANGUAGE_SPEC.md sections 1 and 15). Supplementary code points are outside the initial
     * {@code Character} representation and are reported as invalid literals.
     */
    private Type checkCharacterLiteral(CharacterLiteralNode literal) {
        String text = literal.lexeme();
        String content = text.substring(1, text.length() - 1);
        if (content.length() == 1 && content.charAt(0) != '\\') {
            return CharacterType.INSTANCE;
        }
        if (content.length() == 2 && content.charAt(0) == '\\' && isSupportedCharacterEscape(content.charAt(1))) {
            return CharacterType.INSTANCE;
        }
        if (content.length() >= 2 && content.charAt(0) == '\\') {
            errorExpected(DiagnosticCode.LEXER_INVALID_ESCAPE, literal.span(), "unsupported character escape", "\\\\ \\\" \\' \\n \\r \\t \\0", content);
            return CharacterType.INSTANCE;
        }
        errorExpected(DiagnosticCode.TYPE_INVALID_CHARACTER_LITERAL, literal.span(), "character literal must contain exactly one character", "one character", text);
        return CharacterType.INSTANCE;
    }

    private static boolean isSupportedCharacterEscape(char escape) {
        return escape == '\\' || escape == '"' || escape == '\'' || escape == 'n' || escape == 'r' || escape == 't' || escape == '0';
    }

    private Type checkStringLiteral(StringLiteralNode literal) {
        Optional<String> invalid = StringEscapes.invalidEscape(literal.lexeme());
        if (invalid.isPresent()) {
            errorExpected(DiagnosticCode.LEXER_INVALID_ESCAPE, literal.span(), "unsupported string escape", "\\\\ \\\" \\n \\r \\t \\0", invalid.get());
        }
        return StringType.INSTANCE;
    }

    private Type checkName(NameRefExprNode name) {
        Optional<Symbol> resolved = resolveName(name.name());
        if (resolved.isEmpty()) {
            if (reportCapturedMutableUse(name.name(), name.span()) || reportUnlistedCapture(name.name(), name.span())) {
                return null;
            }
            error(DiagnosticCode.RESOL_UNKNOWN_NAME, name.span(), "unknown name '" + name.name() + "'");
            return null;
        }
        Symbol symbol = resolved.get();
        if (symbol instanceof FunctionSymbol function) {
            // A bare reference to a visible function is a function value, not an error
            // (docs/LANGUAGE_SPEC.md section 6, "Named functions as values"). The reference is recorded
            // in its own map rather than in `nameSymbols`, because that map is shared with the call
            // path: recording a function there would make an immediate call `sum(1, 2)` look like a
            // name that also has a function type, and lowering would send a statically resolved call
            // down the indirect path. Keeping the two facts apart is what preserves direct calls.
            return functionValueOf(name, function);
        }
        if (symbol instanceof ClassSymbol classSymbol) {
            error(DiagnosticCode.TYPE_CLASS_AS_VALUE, name.span(), "class '" + classSymbol.name() + "' cannot be used as a value");
            return null;
        }
        if (symbol instanceof InterfaceSymbol interfaceSymbol) {
            error(DiagnosticCode.TYPE_INTERFACE_AS_VALUE, name.span(), "interface '" + interfaceSymbol.name() + "' cannot be used as a value");
            return null;
        }
        if (symbol instanceof EnumSymbol enumSymbol) {
            error(DiagnosticCode.TYPE_ENUM_AS_VALUE, name.span(), "enum '" + enumSymbol.name() + "' must be constructed through one of its variants");
            return null;
        }
        VariableSymbol variable = (VariableSymbol) symbol;
        nameSymbols.put(name, variable);
        if (!variable.isInitialized()) {
            error(DiagnosticCode.TYPE_UNINITIALIZED_VARIABLE, name.span(), "variable '" + name.name() + "' is read before it is initialized");
        }
        // A flow-sensitive null or type refinement overrides the declared type for this read.
        Type narrowed = narrowedTypes.get(variable);
        return narrowed != null ? narrowed : variable.type();
    }

    /**
     * The function type of a reference to {@code function}, recorded so lowering emits the value
     * (docs/LANGUAGE_SPEC.md section 6, "Named functions as values").
     *
     * <p>A generic declaration is instantiated contextually here, to one monomorphic function type; see
     * {@link #instantiateGenericValue}. Nothing about the produced value differs from a non-generic
     * reference: "Contextual instantiations of one generic declaration at different function types also
     * share that declaration's canonical runtime identity: instantiation changes static typing, not the
     * underlying executable value", so both paths record the same {@link FunctionSymbol} and lowering
     * emits the same canonical value from the same site.
     *
     * <p>A predeclared function is <em>not</em> generic and so is never instantiated: lowering gives each
     * one a real call target, since {@code val output: func(Any?): Unit = println} is specified.
     */
    private Type functionValueOf(ExpressionNode reference, FunctionSymbol function) {
        if (!function.typeParameters().isEmpty()) {
            return instantiateGenericValue(reference, function);
        }
        functionReferences.put(reference, function);
        return function.functionType();
    }

    /**
     * Instantiates a generic function reference to one monomorphic function type under the expected type
     * in scope (docs/LANGUAGE_SPEC.md section 6, "Generic function values").
     *
     * <p>"A generic function declaration does not itself produce a first-class polymorphic value. It must
     * be instantiated to one monomorphic function type at each value-reference site, and that
     * instantiation is contextual." The expected function type supplies the constraints, one complete
     * substitution is determined, and it is applied to the declared parameter and result types. Ordinary
     * function-type assignability then runs on the substituted type in the caller, which is why nothing
     * here reports an assignability problem of its own.
     *
     * <p>Inference follows the order the section states. Declared parameter types are unified against the
     * expected parameter types first; the declared result is unified against the expected result second, so
     * a result position can confirm or complete a substitution but cannot overturn one the parameter
     * positions already established. {@link #unifyTypeParameter} binds by {@code putIfAbsent} for exactly
     * that reason, and reusing it rather than writing a parallel inference is what keeps a generic value
     * reference and a generic call agreeing about what the same pair of types means.
     *
     * <p>Anything that is not a complete function signature is insufficient, and the section names one
     * code for it: an expected {@code Any}, an unbounded type parameter, or no expected type at all is
     * {@code SOLV-TYPE-030} "reported on the function or method reference", and "no second inference
     * diagnostic exists". A reference whose substitution comes out incomplete reports that same code,
     * because the cause is the same condition the section describes — a type parameter the context never
     * determined — and two codes for one missing piece of evidence would leave a reader choosing between
     * them. Returning {@code null} after either report is what stops the caller adding an assignability
     * error for a type this reference never received.
     *
     * <p>An arity mismatch between the declaration and the expected type is deliberately <em>not</em> an
     * inference failure. The expected type still exposes a complete function signature, so the
     * substitution is derived from the positions the two lists share, and the resulting monomorphic type
     * simply is not assignable to what was expected — which the caller reports as the single mismatch the
     * reader actually has to fix.
     */
    private Type instantiateGenericValue(ExpressionNode reference, FunctionSymbol function) {
        List<TypeParameterType> typeParameters = function.typeParameters();
        FunctionType expected = completeFunctionSignatureOf(expectedTypes.isEmpty() ? null : expectedTypes.peek());
        if (expected == null) {
            errorExpected(DiagnosticCode.TYPE_CANNOT_INFER, reference.span(), //
                            "the generic function '" + function.name() + "' is used as a value with no expected function type to instantiate it", //
                            "a declared function type such as func(Integer): Integer", contextDescription());
            return null;
        }
        FunctionType declared = function.functionType();
        Map<TypeParameterType, Type> bindings = new IdentityHashMap<>();
        int positions = Math.min(declared.parameterTypes().size(), expected.parameterTypes().size());
        for (int i = 0; i < positions; i++) {
            unifyTypeParameter(declared.parameterTypes().get(i), expected.parameterTypes().get(i), typeParameters, bindings);
        }
        unifyTypeParameter(declared.returnType(), expected.returnType(), typeParameters, bindings);
        for (TypeParameterType parameter : typeParameters) {
            if (!bindings.containsKey(parameter)) {
                errorExpected(DiagnosticCode.TYPE_CANNOT_INFER, reference.span(), //
                                "the generic function '" + function.name() + "' has a type parameter the expected type does not determine: '" + parameter.name() + "'", //
                                "an expected function type that determines " + parameter.name(), contextDescription());
                return null;
            }
        }
        // Recorded as a canonical function value exactly as a non-generic reference is: the substitution
        // changed this expression's type and nothing about the executable value it denotes.
        functionReferences.put(reference, function);
        return declared.substitute(bindings);
    }

    /**
     * The complete function signature an expected type exposes for instantiating a generic function
     * reference, or {@code null} when it exposes none. A nullable function type exposes the signature it
     * wraps, because {@code (func(Integer): Integer)?} constrains every type parameter exactly as the
     * non-null form does and the nullability is the caller's assignability question. {@code Any} and a
     * bare type parameter expose none, which is what the section states: "An expected {@code Any}, an
     * unbounded type parameter, or any other type that does not expose a complete function signature is
     * insufficient." A generic class type such as {@code List<Integer>} is that "any other type" — it
     * names no parameter and result types, so nothing could be unified against them.
     */
    private static FunctionType completeFunctionSignatureOf(Type expected) {
        Type unwrapped = expected instanceof NullableType nullable ? nullable.inner() : expected;
        return unwrapped instanceof FunctionType functionType ? functionType : null;
    }

    /** How the expected type reads to a programmer, for the inference diagnostic's found clause. */
    private String contextDescription() {
        if (expectedTypes.isEmpty()) {
            return "no expected type";
        }
        return "the expected type " + expectedTypes.peek().name();
    }

    private Type checkThis(ThisExprNode expression) {
        if (checkingStaticMember) {
            // The enclosing class is still recorded so its type parameters resolve, so `this` must be
            // refused here: a static member runs with no receiver (docs/LANGUAGE_SPEC.md section 7).
            error(DiagnosticCode.RESOL_THIS_OUTSIDE_CLASS, expression.span(), "'this' is not available in a static member or class initializer block");
            return null;
        }
        if (currentClass != null) {
            return currentClass.type();
        }
        if (currentInterface != null) {
            // Inside a default method `this` is the conforming instance, statically the interface.
            return currentInterface.type();
        }
        if (currentBoundaryReceiver != null) {
            // `[this]` was written on this closure, so the receiver reaches the body as a captured value
            // rather than as a receiver in scope. Its type is the one the item resolved to wherever the
            // outermost receiver lived, and a `this` here is exactly what a nested `[this]` forwards.
            return currentBoundaryReceiver.type();
        }
        if (enclosingReceiverAvailable) {
            // The receiver exists in an enclosing callable but no `[this]` item carried it into this
            // anonymous function (docs/LANGUAGE_SPEC.md section 6, "Explicit immutable closure capture"):
            // "a closure body may use `this` only when `[this]` is written".
            error(DiagnosticCode.SEM_UNLISTED_CAPTURE, expression.span(), //
                            "an anonymous function body uses 'this', which it does not capture");
            return null;
        }
        error(DiagnosticCode.RESOL_THIS_OUTSIDE_CLASS, expression.span(), "'this' is only valid inside an instance method or constructor");
        return null;
    }

    /** {@code super} is never a value; it is legal only as a call or member receiver. */
    private Type checkSuperAsValue(SuperExprNode expression) {
        error(DiagnosticCode.SEM_SUPER_AS_VALUE, expression.span(), "'super' must be used as 'super(...)' or 'super.member'");
        return null;
    }

    /** Resolves the immediate superclass of the enclosing class, reporting when there is none. */
    private ClassSymbol requireSuperclass(SourceSpan span) {
        if (checkingStaticMember) {
            // Covers `super(...)`, `super.member`, and `super as T`, which all resolve through here.
            error(DiagnosticCode.RESOL_SUPER_OUTSIDE_CLASS, span, "'super' is not available in a static member or class initializer block");
            return null;
        }
        if (currentClass == null) {
            if (enclosingReceiverAvailable) {
                // `super` reaches the enclosing class's superclass through the receiver, which an
                // anonymous function does not have. The specification gives no capture form for `super`
                // and no dedicated code, so this stays the outside-a-class diagnostic with a message
                // that states the actual reason rather than the static-member one above.
                error(DiagnosticCode.RESOL_SUPER_OUTSIDE_CLASS, span, "'super' is not available inside an anonymous function");
                return null;
            }
            error(DiagnosticCode.RESOL_SUPER_OUTSIDE_CLASS, span, "'super' is only valid inside an instance method or constructor");
            return null;
        }
        if (currentClass.superClass().isEmpty()) {
            error(DiagnosticCode.RESOL_SUPER_OUTSIDE_CLASS, span, "class '" + currentClass.name() + "' has no superclass");
            return null;
        }
        return currentClass.superClass().get();
    }

    private Type checkUnary(UnaryExprNode expression) {
        Type operand = checkExpression(expression.operand());
        if (operand == null) {
            return null;
        }
        switch (expression.operator()) {
            case NOT:
                if (operand != BooleanType.INSTANCE) {
                    invalidOperands(expression.span(), expression.operator().spelling(), "Boolean", operand);
                    return null;
                }
                return BooleanType.INSTANCE;
            case NEGATE:
                if (!NumericTypes.isNumeric(operand)) {
                    invalidOperands(expression.span(), expression.operator().spelling(), "a numeric type", operand);
                    return null;
                }
                return operand;
            default:
                throw new IllegalStateException("unknown unary operator: " + expression.operator());
        }
    }

    private Type checkBinary(BinaryExprNode expression) {
        Type left = checkExpression(expression.left());
        Type right = checkExpression(expression.right());
        if (left == null || right == null) {
            return null;
        }
        switch (expression.operator().kind()) {
            case CONCAT:
                // `..` renders both operands through toString, so no operand type is excluded.
                return StringType.INSTANCE;
            case ARITHMETIC:
                // Same-type arithmetic is unchanged; otherwise both operands widen to their least
                // common widened numeric type when one exists without precision or range loss.
                if (NumericTypes.isNumeric(left) && left == right) {
                    return left;
                }
                Type arithmeticType = widenNumericOperands(expression, left, right);
                if (arithmeticType != null) {
                    return arithmeticType;
                }
                invalidOperands(expression.span(), expression.operator().spelling(), "two operands of the same or compatible numeric type", left, right);
                return null;
            case COMPARISON:
                if (NumericTypes.isNumeric(left) && left == right) {
                    return BooleanType.INSTANCE;
                }
                if (widenNumericOperands(expression, left, right) != null) {
                    return BooleanType.INSTANCE;
                }
                invalidOperands(expression.span(), expression.operator().spelling(), "two operands of the same or compatible numeric type", left, right);
                return null;
            case EQUALITY:
                if (left.isAssignableTo(right) || right.isAssignableTo(left)) {
                    return BooleanType.INSTANCE;
                }
                // Mixed numeric equality compares after both operands widen to their least common
                // widened numeric type; identity (`===`) is deliberately not widened here.
                if (widenNumericOperands(expression, left, right) != null) {
                    return BooleanType.INSTANCE;
                }
                invalidOperands(expression.span(), expression.operator().spelling(), "assignment-compatible operands", left, right);
                return null;
            case EQUALITY_IDENTITY:
                // `===`/`!==` first require the same one-direction assignment compatibility as `==`.
                if (!left.isAssignableTo(right) && !right.isAssignableTo(left)) {
                    invalidOperands(expression.span(), expression.operator().spelling(), "assignment-compatible operands", left, right);
                    return null;
                }
                // `Any`, scalars, enums, regex values and unbounded type parameters can be
                // assignment-compatible yet carry no Solvik allocation identity, so the static domain
                // is validated separately (docs/LANGUAGE_SPEC.md section 3).
                if (!identityDomain().isIdentityBearing(left) && !identityDomain().isIdentityBearing(right)) {
                    error(DiagnosticCode.TYPE_IDENTITY_OPERANDS, expression.span(), //
                                    "operator '" + expression.operator().spelling() + "' requires an identity-bearing operand, but " + left.name() + " and " + right.name() + " have no Solvik reference identity");
                    return null;
                }
                return BooleanType.INSTANCE;
            case LOGICAL:
                if (left == BooleanType.INSTANCE && right == BooleanType.INSTANCE) {
                    return BooleanType.INSTANCE;
                }
                invalidOperands(expression.span(), expression.operator().spelling(), "Boolean, Boolean", left, right);
                return null;
            case COALESCE:
                return checkCoalesce(expression, left, right);
            default:
                throw new IllegalStateException("unknown binary operator: " + expression.operator());
        }
    }

    /**
     * The identity-bearing types of the program under analysis, built once from the declared class
     * and interface types. Centralizing the classification keeps the identity domain out of the
     * individual visitors (docs/LANGUAGE_SPEC.md section 3).
     */
    private IdentityDomain identityDomain() {
        if (identityDomain == null) {
            identityDomain = IdentityDomain.forProgram(classTypes.values(), interfaceTypes.values());
        }
        return identityDomain;
    }

    /**
     * Types {@code left ?? right} (docs/LANGUAGE_SPEC.md section 5): the left operand must be nullable, and
     * the result is the common type of the non-null left and the right operand. A {@code null} literal
     * on the right is the non-null left type because it contributes no values of its own.
     */
    private Type checkCoalesce(BinaryExprNode expression, Type left, Type right) {
        if (left != NullType.INSTANCE && !(left instanceof NullableType)) {
            error(DiagnosticCode.TYPE_NULLABLE_REQUIRED, expression.left().span(), //
                            "the left operand of '??' must be nullable because it is evaluated for null");
            return right;
        }
        if (left == NullType.INSTANCE) {
            return right;
        }
        Type leftNonNull = ((NullableType) left).inner();
        if (right == NullType.INSTANCE) {
            return leftNonNull;
        }
        Type common = commonType(leftNonNull, right);
        if (common == null) {
            invalidOperands(expression.span(), expression.operator().spelling(), "a non-null left type and a right operand of a common type", left, right);
            return null;
        }
        return common;
    }

    /**
     * The nearest common type of two types under assignment compatibility, preferring the wider one.
     * Nullability participates: the common type of {@code String} and {@code String?} is
     * {@code String?}, while unrelated types have no common type.
     */
    private static Type commonType(Type a, Type b) {
        if (a.isAssignableTo(b)) {
            return b;
        }
        if (b.isAssignableTo(a)) {
            return a;
        }
        return null;
    }

    /**
     * Whether a value of {@code valueType} may stand where {@code target} is required: either it is
     * nominally assignable, or it is a numeric type that {@linkplain NumericTypes#widens widens} to
     * {@code target} without loss of range or precision (docs/LANGUAGE_SPEC.md section 4). When the
     * only route is a widening, the coercion is recorded so lowering wraps {@code expression} in a
     * convert node. Widening never fires for a nominal, nullable, generic, or narrowing target.
     *
     * <p>Coercion boundary (docs/LANGUAGE_SPEC.md section 4). Widening is applied ONLY where a value
     * flows into a typed slot: {@code val}/{@code var} initializer, property initializer, return,
     * argument, property/static assignment, and collection element/key/value. Sites below are
     * intentionally NOT widened: subtyping checks (override, interface, sealed, narrowing, type test,
     * cast), nominal identity/equality (which compares by `Any` value equality, not numeric
     * equivalence), `for-in` range bounds (exactly {@code Integer}), `case` labels (constants of the
     * scrutinee type; widening a constant would change the case), `is`/`as`, generics inference
     * targets, type-join results, and {@code Result} boundary types (which carry {@code Ok}/{@code Err}
     * type parameters — widening would silently change the {@code Result} type). Any site that adds a
     * coercion to a numeric slot MUST route through this helper or {@link #widenNumericOperands};
     * any site that must reject widening MUST use the bare {@code isAssignableTo}. New coercion sites
     * that call the bare form are a regression and are covered by {@code SolvikNumericWideningTest}.
     */
    private boolean assignableOrWidened(ExpressionNode expression, Type valueType, Type target) {
        if (valueType.isAssignableTo(target)) {
            return true;
        }
        if (NumericTypes.widens(valueType, target)) {
            coercions.put(expression, target);
            return true;
        }
        return false;
    }

    /**
     * Types the operands of a mixed numeric {@code +}, ordering, or {@code ==} operator by widening
     * each operand to their least common widened numeric type
     * ({@link NumericTypes#leastCommonNumeric}), recording a coercion for any operand that is not
     * already that type. Returns the common type, or {@code null} when the operands are not both
     * concrete numeric types or have no common widened type (for example {@code Long} and
     * {@code Float}), in which case the caller reports the operand diagnostic (section 4).
     */
    private Type widenNumericOperands(BinaryExprNode expression, Type left, Type right) {
        if (!NumericTypes.isNumeric(left) || !NumericTypes.isNumeric(right)) {
            return null;
        }
        Type common = NumericTypes.leastCommonNumeric(left, right).orElse(null);
        if (common == null) {
            return null;
        }
        if (left != common) {
            coercions.put(expression.left(), common);
        }
        if (right != common) {
            coercions.put(expression.right(), common);
        }
        return common;
    }

    /**
     * Types {@code value is T} (docs/LANGUAGE_SPEC.md section 18). The result is always {@code Boolean}.
     * The type operand must name a non-null type: a test against a nullable type is always true for
     * {@code null} and adds nothing to a non-null test, so the nullable marker is a diagnostic.
     */
    private Type checkTypeTest(TypeTestExprNode expression) {
        checkExpression(expression.operand());
        Type target = resolveType(expression.typeRef());
        if (target == null) {
            return BooleanType.INSTANCE;
        }
        if (target instanceof FunctionType) {
            error(DiagnosticCode.TYPE_INVALID_TYPE_OPERAND, expression.typeRef().span(), //
                            "a function type cannot be the target of a type test");
            return BooleanType.INSTANCE;
        }
        if (target instanceof NullableType || target == NullType.INSTANCE) {
            error(DiagnosticCode.TYPE_INVALID_TYPE_OPERAND, expression.typeRef().span(), //
                            "a type test must name a non-null type");
            return BooleanType.INSTANCE;
        }
        if (target instanceof ParameterizedType) {
            errorExpected(DiagnosticCode.TYPE_ERASED_TYPE_TEST, expression.typeRef().span(), //
                            "a runtime type test cannot inspect erased type arguments", "a non-generic type", target.name());
            return BooleanType.INSTANCE;
        }
        testedTypes.put(expression, target);
        return BooleanType.INSTANCE;
    }

    /**
     * Types a checked cast {@code value as T} (docs/LANGUAGE_SPEC.md section 18). The result type is
     * {@code T}; an unsuccessful cast is a runtime type error, so no static compatibility is required
     * and the cast itself performs the check.
     */
    private Type checkCast(CastExprNode expression) {
        checkExpression(expression.operand());
        Type target = resolveType(expression.typeRef());
        if (target == null) {
            return null;
        }
        if (target instanceof FunctionType) {
            error(DiagnosticCode.TYPE_INVALID_TYPE_OPERAND, expression.typeRef().span(), //
                            "a function type cannot be the target of a checked cast");
            return target;
        }
        if (target instanceof NullableType || target == NullType.INSTANCE) {
            error(DiagnosticCode.TYPE_INVALID_TYPE_OPERAND, expression.typeRef().span(), //
                            "a checked cast must name a non-null type");
            return target;
        }
        if (target instanceof ParameterizedType) {
            errorExpected(DiagnosticCode.TYPE_ERASED_TYPE_TEST, expression.typeRef().span(), //
                            "a runtime cast cannot inspect erased type arguments", "a non-generic type", target.name());
            return target;
        }
        testedTypes.put(expression, target);
        return target;
    }

    private Type checkCall(CallExprNode expression) {
        ExpressionNode callee = expression.callee();
        if (callee instanceof SuperExprNode) {
            return checkSuperConstructorCall(expression);
        }
        if (callee instanceof NameRefExprNode name) {
            Optional<Symbol> resolved = resolveName(name.name());
            if (resolved.isEmpty()) {
                Optional<Type> declaredType = typeEnvironment.resolve(name.name());
                if (declaredType.isPresent()) {
                    Type type = declaredType.get();
                    if (NumericTypes.isNumeric(type)) {
                        return checkConversion(expression, type);
                    }
                    if (type == RegexType.INSTANCE) {
                        return checkRegexConstruction(expression);
                    }
                    if (type instanceof BuiltinCollectionType collection) {
                        return checkCollectionConstruction(expression, collection);
                    }
                    error(DiagnosticCode.TYPE_INVALID_CONVERSION, name.span(), "'" + name.name() + "' is a type; only numeric types and Regex construct with a call");
                    return null;
                }
                if (checkingStaticMember && currentClass != null) {
                    // Inside a static member an unqualified call has no receiver, so it resolves only
                    // among the class's own static methods. It must never reach the virtual table:
                    // doing so would silently bind an instance method that has no receiver to call it
                    // (docs/LANGUAGE_SPEC.md section 7). Statics are not inherited, so ancestors are not
                    // consulted.
                    Optional<FunctionSymbol> staticMethod = currentClass.staticMethod(name.name());
                    if (staticMethod.isPresent()) {
                        return resolveMethodCall(expression, name, staticMethod.get(), false);
                    }
                    if (currentClass.method(name.name()).isPresent()) {
                        error(DiagnosticCode.RESOL_UNKNOWN_NAME, name.span(), //
                                        "a static member has no receiver, so it cannot call the instance method '" + name.name() + "'");
                        return null;
                    }
                    error(DiagnosticCode.RESOL_UNKNOWN_NAME, name.span(), "unknown name '" + name.name() + "'");
                    return null;
                }
                if (currentClass != null) {
                    Optional<FunctionSymbol> method = currentClass.method(name.name());
                    if (method.isPresent()) {
                        return resolveMethodCall(expression, name, method.get(), true);
                    }
                }
                if (currentInterface != null) {
                    // An unqualified call in a default method body is a call on the conforming
                    // instance, so a sibling requirement or default dispatches virtually.
                    Optional<FunctionSymbol> member = currentInterface.member(name.name());
                    if (member.isPresent()) {
                        return resolveMethodCall(expression, name, member.get(), true);
                    }
                }
                error(DiagnosticCode.RESOL_UNKNOWN_NAME, name.span(), "unknown name '" + name.name() + "'");
                return null;
            }
            Symbol symbol = resolved.get();
            if (symbol instanceof InterfaceSymbol interfaceSymbol) {
                // Interfaces are contracts, not constructible values (docs/LANGUAGE_SPEC.md 8).
                nameSymbols.put(name, interfaceSymbol);
                error(DiagnosticCode.TYPE_INTERFACE_AS_VALUE, name.span(), "interface '" + interfaceSymbol.name() + "' cannot be constructed or used as a value");
                return null;
            }
            if (symbol instanceof ClassSymbol classSymbol) {
                nameSymbols.put(name, classSymbol);
                expressionTypes.put(name, classSymbol.type());
                return checkConstruction(expression, classSymbol);
            }
            if (symbol instanceof EnumSymbol enumSymbol) {
                // An enum is a closed set of variants, not a constructor itself; no value of the enum
                // exists without naming one of its variants (docs/LANGUAGE_SPEC.md section 12).
                nameSymbols.put(name, enumSymbol);
                error(DiagnosticCode.TYPE_ENUM_AS_VALUE, name.span(), "enum '" + enumSymbol.name() + "' must be constructed through one of its variants");
                return null;
            }
            if (!(symbol instanceof FunctionSymbol function)) {
                if (symbol instanceof VariableSymbol variable) {
                    // A call through a function-typed binding is an indirect call, and a call through a
                    // binding of any other type is the ordinary "not callable" error
                    // (docs/LANGUAGE_SPEC.md section 6). Both must be decided from the binding's type
                    // here rather than by falling through, because the callee resolves to a variable and
                    // the code below assumes a call on a declaration.
                    nameSymbols.put(name, variable);
                    expressionTypes.put(name, variable.type());
                    // The refined type is what decides callability, so a null check that has narrowed a
                    // nullable function-typed binding makes the call legal, matching every other use of
                    // the binding (docs/LANGUAGE_SPEC.md section 6: a nullable function value cannot be
                    // invoked "without prior refinement or another existing non-null mechanism").
                    Type refined = narrowedTypes.getOrDefault(variable, variable.type());
                    if (refined instanceof FunctionType functionType) {
                        return checkIndirectCall(expression, functionType);
                    }
                }
                error(DiagnosticCode.TYPE_NOT_CALLABLE, name.span(), "'" + name.name() + "' is not a function");
                return null;
            }
            nameSymbols.put(name, function);
            expressionTypes.put(name, function.functionType());
            return resolveCallableType(expression, function.name(), function, Map.of());
        }
        QualifiedPrefix qualified = qualifiedPrefix(callee);
        if (qualified != null) {
            return checkQualifiedCall(expression, qualified);
        }
        if (callee instanceof MemberAccessExprNode member) {
            if (member.receiver() instanceof SuperExprNode) {
                return checkSuperMethodCall(expression, member);
            }
            if (member.receiver() instanceof NameRefExprNode name) {
                EnumSymbol enumSymbol = enumSymbolNamed(name);
                if (enumSymbol != null) {
                    return checkVariantConstruction(expression, member, enumSymbol);
                }
                ClassSymbol classSymbol = classSymbolNamed(name);
                if (classSymbol != null) {
                    return checkStaticMethodCall(expression, classSymbol, member.memberName(), member.span());
                }
            }
            return checkMethodCall(expression, member);
        }
        if (callee instanceof NamespaceAccessExprNode) {
            error(DiagnosticCode.RESOL_UNKNOWN_MODULE, callee.span(), "'::' must name a visible module or alias prefix");
            return null;
        }
        // A call whose callee is any other expression is indirect exactly when that expression has a
        // function type; `f(1)` where `f` is a function-typed field or a collection element reaches here
        // with an already-checked callee type, and must not be reported as "not callable"
        // (docs/LANGUAGE_SPEC.md section 6).
        if (checkExpression(callee) instanceof FunctionType functionType) {
            return checkIndirectCall(expression, functionType);
        }
        error(DiagnosticCode.TYPE_NOT_CALLABLE, callee.span(), "expression is not callable");
        return null;
    }

    /**
     * Types a call through a function-typed value (docs/LANGUAGE_SPEC.md section 6, "Function values and
     * invocation") and records it for lowering as an indirect call.
     *
     * <p>Arity is checked before argument-type compatibility, matching the ordering a resolved direct
     * call uses, so a call that supplies the wrong number of arguments reports the count rather than a
     * cascade of per-parameter mismatches. The declared parameter types come from the callee's own
     * function type, which is what makes a contravariantly assignable callee accepted without any
     * re-reading of the declaration it came from.
     *
     * <p>A nullable function type is not invocable and reaches the caller's "not callable" report: the
     * specification requires a refinement or another existing non-null mechanism first, and every such
     * mechanism is a separate expression form that would have produced a non-null type here.
     */
    private Type checkIndirectCall(CallExprNode expression, FunctionType functionType) {
        if (!expression.typeArguments().isEmpty()) {
            // A function value is monomorphic: it carries no type parameters to supply, so explicit
            // arguments here would either be ignored or imply a runtime instantiation that does not exist.
            for (TypeRef argument : expression.typeArguments()) {
                resolveType(argument);
            }
            error(DiagnosticCode.TYPE_NOT_GENERIC, expression.span(), //
                            "a function value is monomorphic, so it cannot be called with explicit type arguments");
        }
        // A function value is monomorphic, so every parameter type it declares is closed and can be
        // supplied to its arguments as an expected type before they are checked.
        List<Type> argumentTypes = checkArgumentTypesWithFinalParameters(expression, functionType.parameterTypes());
        if (!checkArity(expression, "the called function value", functionType.parameterTypes().size(), argumentTypes.size())) {
            return functionType.returnType();
        }
        checkArgumentTypesAgainst(expression, "the called function value", functionType.parameterTypes(), argumentTypes);
        indirectCalls.put(expression, functionType);
        return functionType.returnType();
    }

    /** Types {@code T(value)}, the explicit numeric conversion of docs/LANGUAGE_SPEC.md section 4. */
    private Type checkConversion(CallExprNode call, Type target) {
        List<ExpressionNode> arguments = call.arguments();
        if (!checkArity(call, target.name(), 1, arguments.size())) {
            for (ExpressionNode argument : arguments) {
                checkExpression(argument);
            }
            conversions.put(call, target);
            return target;
        }
        ExpressionNode argument = arguments.get(0);
        Type argumentType = checkExpression(argument);
        if (argumentType != null && !NumericTypes.isNumeric(argumentType)) {
            errorExpected(DiagnosticCode.TYPE_INVALID_CONVERSION, argument.span(), "numeric conversion requires a numeric value", "a numeric type", argumentType.name());
        } else {
            checkConstantConversion(argument, target);
        }
        conversions.put(call, target);
        return target;
    }

    /**
     * Types {@code Regex(pattern)}, the built-in regular-expression constructor
     * (docs/LANGUAGE_SPEC.md section 14). The argument must be a {@code String}. When it is a
     * source constant the pattern is validated against the portable dialect and compiled exactly
     * once here, so an invalid or unsupported constant is a compile-time diagnostic and lowering
     * reuses the compiled pattern. A dynamically computed pattern is validated at run time instead.
     */
    private Type checkRegexConstruction(CallExprNode call) {
        checkArguments(call, "Regex", List.of(StringType.INSTANCE));
        if (call.arguments().size() == 1) {
            Optional<String> constant = constantString(call.arguments().get(0));
            if (constant.isPresent()) {
                try {
                    regexConstants.put(call, RegexSyntax.compile(constant.get()));
                } catch (RegexSyntax.InvalidPatternException e) {
                    error(DiagnosticCode.TYPE_INVALID_REGEX_PATTERN, call.arguments().get(0).span(), "invalid Regex pattern: " + e.getMessage());
                }
            }
        }
        return RegexType.INSTANCE;
    }

    /**
     * The compile-time constant text of a string expression, or empty when the value is not a source
     * literal. Redundant parentheses around a literal do not hide the constant.
     */
    private static Optional<String> constantString(ExpressionNode expression) {
        ExpressionNode inner = expression;
        while (inner instanceof ParenExprNode paren) {
            inner = paren.inner();
        }
        if (inner instanceof StringLiteralNode literal) {
            return Optional.of(StringEscapes.unescape(literal.lexeme()));
        }
        if (inner instanceof RawStringLiteralNode raw) {
            return Optional.of(raw.value());
        }
        return Optional.empty();
    }

    /** Rejects an explicit conversion of a literal whose constant value cannot fit the target type. */
    private void checkConstantConversion(ExpressionNode argument, Type target) {
        if (!NumericTypes.isIntegral(target)) {
            return;
        }
        ExpressionNode literal = argument;
        boolean negative = false;
        while (true) {
            if (literal instanceof ParenExprNode paren) {
                literal = paren.inner();
            } else if (literal instanceof UnaryExprNode unary && unary.operator() == UnaryOperator.NEGATE) {
                negative = !negative;
                literal = unary.operand();
            } else {
                break;
            }
        }
        BigInteger value;
        if (literal instanceof IntegerLiteralNode integerLiteral) {
            value = new BigInteger(integerLiteral.lexeme());
        } else if (literal instanceof LongLiteralNode longLiteral) {
            String digits = longLiteral.lexeme();
            value = new BigInteger(digits.substring(0, digits.length() - 1));
        } else if (literal instanceof FloatingLiteralNode floating) {
            double parsed = floating.isFloat() ? Float.parseFloat(floating.numericText()) : Double.parseDouble(floating.numericText());
            if (!Double.isFinite(parsed)) {
                errorExpected(DiagnosticCode.TYPE_CONVERSION_OUT_OF_RANGE, argument.span(), //
                                "constant conversion to " + target.name() + " is out of range", integralRangeText(target), floating.numericText());
                return;
            }
            // Preserve the exact binary value; decimal round-trip text can move a Long boundary.
            value = new BigDecimal(parsed).toBigInteger();
        } else {
            return;
        }
        checkConstantRange(argument, target, negative ? value.negate() : value);
    }

    private void checkConstantRange(ExpressionNode argument, Type target, BigInteger value) {
        BigInteger min = integralMinimum(target);
        BigInteger max = integralMaximum(target);
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            errorExpected(DiagnosticCode.TYPE_CONVERSION_OUT_OF_RANGE, argument.span(), //
                            "constant conversion to " + target.name() + " is out of range", integralRangeText(target), value.toString());
        }
    }

    private static BigInteger integralMinimum(Type target) {
        if (target == ByteType.INSTANCE) {
            return BigInteger.valueOf(Byte.MIN_VALUE);
        }
        if (target == ShortType.INSTANCE) {
            return BigInteger.valueOf(Short.MIN_VALUE);
        }
        if (target == IntegerType.INSTANCE) {
            return BigInteger.valueOf(Integer.MIN_VALUE);
        }
        return BigInteger.valueOf(Long.MIN_VALUE);
    }

    private static BigInteger integralMaximum(Type target) {
        if (target == ByteType.INSTANCE) {
            return BigInteger.valueOf(Byte.MAX_VALUE);
        }
        if (target == ShortType.INSTANCE) {
            return BigInteger.valueOf(Short.MAX_VALUE);
        }
        if (target == IntegerType.INSTANCE) {
            return BigInteger.valueOf(Integer.MAX_VALUE);
        }
        return BigInteger.valueOf(Long.MAX_VALUE);
    }

    private static String integralRangeText(Type target) {
        return integralMinimum(target) + ".." + integralMaximum(target);
    }

    /** Validates the mandated {@code super(...)} call at the start of a subclass constructor. */
    private Type checkSuperConstructorCall(CallExprNode call) {
        ClassSymbol superClass = requireSuperclass(call.span());
        if (superClass == null) {
            return null;
        }
        if (!checkingConstructor || sanctionedSuperCall != call) {
            error(DiagnosticCode.SEM_SUPER_CALL_PLACEMENT, call.span(), "super(...) must be the first statement of its constructor");
            return null;
        }
        List<VariableSymbol> parameters = superClass.constructor().map(FunctionSymbol::parameters).orElseGet(List::of);
        checkArguments(call, "super", substitutedParameterTypes(parameters, superTypeSubstitution()));
        superConstructorCalls.put(call, superClass);
        return UnitType.INSTANCE;
    }

    /** The substitution the immediate supertype applies to its nominal base's type parameters. */
    private Map<TypeParameterType, Type> superTypeSubstitution() {
        if (currentClass == null) {
            return Map.of();
        }
        return substitutionFor(currentClass.type().superType().orElse(AnyType.INSTANCE));
    }

    /**
     * The declared type of {@code property} as seen through a receiver of {@code receiverType}, with the
     * property's own type parameters composed over the receiver's application. Shared with the member
     * read and assignment paths so a call through a function-typed property and a read of that same
     * property cannot disagree about its type.
     */
    private Type propertyTypeThrough(ClassSymbol classSymbol, PropertySymbol property, Type receiverType) {
        return property.type().substitute(composeSubstitutions(classSymbol.propertySubstitution(property), substitutionFor(receiverType)));
    }

    /**
     * Handles a call whose callee reads a property of function type: the call is indirect, invoking the
     * stored value (docs/LANGUAGE_SPEC.md section 6, "Bound method references": "a property may itself
     * have a function type ... member resolution decides statically whether `receiver.member` reads a
     * stored function value or creates a bound method value").
     *
     * <p>Returns the call's result type, or {@code null} when the member is not a function-typed
     * property, which lets the callers keep their existing "not callable" report for every other
     * property. Only the stored-value half of that sentence is implemented: creating a bound method value
     * is a later change, and no declared method reaches here as a property.
     */
    private Type checkCallThroughFunctionProperty(CallExprNode call, MemberAccessExprNode member, Type receiverType) {
        ClassSymbol classSymbol = classSymbolFor(receiverType);
        if (classSymbol == null) {
            return null;
        }
        Optional<PropertySymbol> property = classSymbol.property(member.memberName());
        if (property.isEmpty() || !(propertyTypeThrough(classSymbol, property.get(), receiverType) instanceof FunctionType functionType)) {
            return null;
        }
        // The member read is recorded as the property read it is, so lowering evaluates the receiver and
        // reads the slot exactly as an ordinary read of the same property would, and then invokes.
        propertyAccesses.put(member, property.get());
        expressionTypes.put(member, functionType);
        return checkIndirectCall(call, functionType);
    }

    /** Resolves {@code super.method(...)} to the immediate superclass implementation. */
    private Type checkSuperMethodCall(CallExprNode call, MemberAccessExprNode member) {
        ClassSymbol superClass = requireSuperclass(member.span());
        if (superClass == null) {
            return null;
        }
        if (superClass.property(member.memberName()).isPresent()) {
            error(DiagnosticCode.TYPE_NOT_CALLABLE, member.span(), "'" + member.memberName() + "' is not callable");
            return null;
        }
        Optional<FunctionSymbol> method = superClass.method(member.memberName());
        if (method.isEmpty()) {
            if ("equals".equals(member.memberName())) {
                // No source override exists in the hierarchy, so `super.equals` reaches the root
                // identity default. It has no runtime function to call, so the call is recorded as
                // a built-in equality call and lowering compares `this` against the argument
                // (docs/LANGUAGE_SPEC.md section 3).
                checkArguments(call, "equals", List.of(AnyType.INSTANCE.nullableView()));
                builtinEqualsCalls.add(call);
                return BooleanType.INSTANCE;
            }
            if ("hashCode".equals(member.memberName())) {
                // Mirror of the `super.equals` root default: no override exists anywhere in the
                // hierarchy, so there is no runtime function to call and the result is the identity
                // hash of `this`. Recorded as a built-in hash call so lowering emits the identity node
                // rather than re-dispatching to the current class's own override (section 3).
                List<Type> argumentTypes = checkArgumentTypes(call);
                if (!checkArity(call, "hashCode", 0, argumentTypes.size())) {
                    return null;
                }
                builtinHashCodeCalls.add(call);
                return IntegerType.INSTANCE;
            }
            error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, member.span(), "class " + superClass.name() + " has no method '" + member.memberName() + "'");
            return null;
        }
        FunctionSymbol target = method.get();
        expressionTypes.put(member, target.functionType());
        Type result = resolveCallableType(call, superClass.name() + "." + target.name(), target, composeSubstitutions(superClass.methodSubstitution(target.name()), superTypeSubstitution()));
        methodCalls.put(call, new ResolvedMethod(target, true, true));
        return result;
    }

    /**
     * Types a class construction {@code Name(arguments)} (docs/LANGUAGE_SPEC.md section 11). A
     * generic class's type arguments are inferred from the constructor arguments: {@code Box(5)}
     * constructs {@code Box<Integer>}. The inferred application is the type of the construction
     * expression, and the constructor's parameter types are substituted before the arguments are
     * checked. Inference failure is reported and leaves no usable construction type.
     */
    private Type checkConstruction(CallExprNode call, ClassSymbol classSymbol) {
        if (classSymbol.isSealed()) {
            // A sealed class is abstract: it exists only to close a hierarchy, so no value of the
            // sealed type itself can be constructed (docs/LANGUAGE_SPEC.md section 12).
            for (ExpressionNode argument : call.arguments()) {
                checkExpression(argument);
            }
            error(DiagnosticCode.SEM_CANNOT_CONSTRUCT_SEALED, call.span(), //
                            "sealed class '" + classSymbol.name() + "' is abstract and cannot be constructed; construct one of its subtypes");
            return classSymbol.type();
        }
        List<VariableSymbol> parameters = classSymbol.constructor().map(FunctionSymbol::parameters).orElseGet(List::of);
        List<Type> argumentTypes = checkArgumentTypes(call);
        List<Type> declaredParameterTypes = substitutedParameterTypes(parameters, Map.of());
        // A guest exception construction may carry one optional trailing message (docs/LANGUAGE_SPEC.md
        // section 22): Name(args) or Name(args..., message). The message is not a declared constructor
        // parameter; it is stored into the synthesized slot and read only through getMessage(). It is
        // excluded from the declared-parameter list below so it never participates in type-argument
        // inference. The synthesis is defined for a non-generic exception class: a generic class
        // constructs a parameterized type that throw and catch do not accept, so it is not throwable at
        // all today and must keep reporting an arity error rather than reinterpret an extra argument.
        boolean synthesizedMessage = classSymbol.type().typeParameters().isEmpty() && isExceptionName(classSymbol.name());
        int declaredCount = parameters.size();
        int messageArgumentIndex = synthesizedMessage && argumentTypes.size() == declaredCount + 1 ? declaredCount : -1;
        List<Type> declaredArgumentTypes = messageArgumentIndex < 0
                        ? argumentTypes
                        : new ArrayList<>(argumentTypes.subList(0, declaredCount));
        if (!checkArity(call, classSymbol.name(), declaredCount, declaredArgumentTypes.size())) {
            // A wrong count suppresses type inference and argument type checking; the program cannot
            // be lowered while the arity error exists. A single extra argument on an exception is not
            // a count error: it is the optional message, handled below.
            constructorCalls.put(call, classSymbol);
            return classSymbol.type();
        }
        List<TypeParameterType> typeParameters = classSymbol.type().typeParameters();
        Map<TypeParameterType, Type> substitution = explicitTypeArguments(call, typeParameters, declaredParameterTypes, declaredArgumentTypes, classSymbol.name());
        if (substitution == null) {
            substitution = inferCallableTypeArguments(call, typeParameters, declaredParameterTypes, declaredArgumentTypes);
        }
        checkArgumentTypesAgainst(call, classSymbol.name(), substitutedTypes(declaredParameterTypes, substitution), declaredArgumentTypes);
        if (messageArgumentIndex >= 0) {
            Type messageType = argumentTypes.get(messageArgumentIndex);
            if (messageType != null && !messageType.isAssignableTo(StringType.INSTANCE.nullableView())) {
                errorExpected(DiagnosticCode.TYPE_MISMATCH, call.arguments().get(messageArgumentIndex).span(), //
                                "exception message argument has the wrong type", "String?", messageType.name());
            }
        }
        constructorCalls.put(call, classSymbol);
        if (typeParameters.isEmpty()) {
            return classSymbol.type();
        }
        List<Type> arguments = new ArrayList<>(typeParameters.size());
        for (TypeParameterType parameter : typeParameters) {
            Type bound = substitution.get(parameter);
            arguments.add(bound != null ? bound : AnyType.INSTANCE);
        }
        return classSymbol.type().parameterizedView(arguments);
    }

    /** The enum symbol a receiver name resolves to, or {@code null} when it is not an enum name. */
    private EnumSymbol enumSymbolNamed(NameRefExprNode name) {
        Symbol symbol = resolveName(name.name()).orElse(null);
        return symbol instanceof EnumSymbol enumSymbol ? enumSymbol : null;
    }

    /**
     * The class a bare name denotes in receiver position, or {@code null} when the name is not a
     * visible class. A class name is not a value, so it is legal only as the root of a static member
     * reference, which is resolved by the caller before the receiver is typed as an expression
     * (docs/LANGUAGE_SPEC.md section 7). Any other use of the name stays on the ordinary path and is
     * reported as {@code SOLV-TYPE-016}.
     */
    private ClassSymbol classSymbolNamed(NameRefExprNode name) {
        Symbol symbol = resolveName(name.name()).orElse(null);
        return symbol instanceof ClassSymbol classSymbol ? classSymbol : null;
    }

    /**
     * Types {@code Class.member(...)} where the receiver is a class name. Only a static method of that
     * exact class is reachable: statics are not inherited, so ancestors are never consulted, and a name
     * that belongs to an instance member is reported rather than silently bound to a call that would
     * need a receiver the reference does not supply (docs/LANGUAGE_SPEC.md section 7).
     */
    private Type checkStaticMethodCall(CallExprNode call, ClassSymbol classSymbol, String memberName, SourceSpan span) {
        Optional<FunctionSymbol> staticMethod = classSymbol.staticMethod(memberName);
        if (staticMethod.isPresent()) {
            FunctionSymbol target = staticMethod.get();
            Type result = resolveCallableType(call, classSymbol.name() + "." + target.name(), target, Map.of());
            // The same recording shape an unqualified static call inside a static body produces, so
            // lowering distinguishes the two cases by the symbol's own static-ness alone.
            methodCalls.put(call, new ResolvedMethod(target, false));
            return result;
        }
        if (classSymbol.staticProperty(memberName).isPresent()) {
            error(DiagnosticCode.TYPE_NOT_CALLABLE, span, "static property '" + classSymbol.name() + "." + memberName + "' is not callable");
            return null;
        }
        if (classSymbol.property(memberName).isPresent() || classSymbol.method(memberName).isPresent()) {
            error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, span, //
                            "'" + memberName + "' is an instance member of '" + classSymbol.name() + "'; a static reference has no receiver");
            return null;
        }
        error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, span, "class " + classSymbol.name() + " has no static method '" + memberName + "'");
        return null;
    }

    /**
     * Resolves {@code Class.member} as a static property read. A static member of the class is reached
     * only through the class name, and an instance member of the same name is reported rather than
     * resolved, because reading it would need an object the reference does not supply
     * (docs/LANGUAGE_SPEC.md section 7).
     */
    private Type checkStaticPropertyRead(MemberAccessExprNode member, ClassSymbol classSymbol) {
        return checkStaticPropertyRead(member, classSymbol, member.memberName(), member.span());
    }

    /**
     * Checks {@code Class.member = value} and {@code module::Class.member = value}. The member must be
     * a mutable static property of the named class: a static cell is written only through its own
     * class name, and an instance member of the same name is reported rather than resolved because the
     * write would need an object the reference does not supply (docs/LANGUAGE_SPEC.md section 7).
     *
     * <p>Returns whether {@code value} was typed by the end of the check, which is the question
     * {@link #checkMemberAssign} asks of every property check it makes. With {@code typesValueHere} the
     * value is a reference to a generic function that this method types itself, against the declared type —
     * a static property's type is a fact of the declaration, available without analyzing anything else.
     * The paths that never reach a property return {@code false} so the reference still gets typed, and
     * still gets its own report, beside this method's.
     */
    private boolean checkStaticPropertyAssign(MemberAccessExprNode target, ClassSymbol classSymbol, String memberName, ExpressionNode value, Type valueType, boolean typesValueHere) {
        Optional<PropertySymbol> staticProperty = classSymbol.staticProperty(memberName);
        if (staticProperty.isEmpty()) {
            if (classSymbol.staticMethod(memberName).isPresent() || classSymbol.method(memberName).isPresent()) {
                error(DiagnosticCode.TYPE_INVALID_ASSIGNMENT_TARGET, target.span(), "cannot assign to method '" + memberName + "'");
            } else if (classSymbol.property(memberName).isPresent()) {
                error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, target.span(), //
                                "'" + memberName + "' is an instance property of '" + classSymbol.name() + "'; a static reference has no receiver");
            } else {
                error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, target.span(), "class " + classSymbol.name() + " has no static property '" + memberName + "'");
            }
            return false;
        }
        PropertySymbol property = staticProperty.get();
        propertyAccesses.put(target, property);
        if (typesValueHere) {
            // A static property's declared type is a fact of the declaration, so it can be supplied without
            // analyzing anything else; this is the one place it is supplied, which is what keeps the
            // classification of a static target in a single method.
            expectedTypes.push(property.type());
            try {
                valueType = checkExpression(value);
            } finally {
                expectedTypes.pop();
            }
        }
        if (valueType != null && !assignableOrWidened(value, valueType, property.type())) {
            errorExpected(DiagnosticCode.TYPE_MISMATCH, value.span(), //
                            "value is not assignable to property type " + property.type().name(), property.type().name(), valueType.name());
        }
        if (!property.isMutable()) {
            error(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE, target.span(), "cannot assign to immutable '" + classSymbol.name() + "." + memberName + "'");
        }
        return true;
    }

    /**
     * Resolves a static property read written through the class name, whether the reference is bare
     * ({@code Counter.limit}) or module-qualified ({@code math::Counter.limit}); the qualified caller
     * supplies the written name for diagnostics.
     */
    private Type checkStaticPropertyRead(ExpressionNode expression, ClassSymbol classSymbol, String memberName, SourceSpan span) {
        Optional<PropertySymbol> staticProperty = classSymbol.staticProperty(memberName);
        if (staticProperty.isPresent()) {
            PropertySymbol property = staticProperty.get();
            if (expression instanceof MemberAccessExprNode member) {
                propertyAccesses.put(member, property);
            }
            expressionTypes.put(expression, property.type());
            return property.type();
        }
        if (classSymbol.property(memberName).isPresent()) {
            error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, span, //
                            "'" + memberName + "' is an instance property of '" + classSymbol.name() + "'; a static reference has no receiver");
        } else if (classSymbol.method(memberName).isPresent()) {
            error(DiagnosticCode.TYPE_FUNCTION_AS_VALUE, span, "method '" + memberName + "' cannot be used as a value");
        } else if (classSymbol.staticMethod(memberName).isPresent()) {
            error(DiagnosticCode.TYPE_FUNCTION_AS_VALUE, span, "static method '" + memberName + "' cannot be used as a value");
        } else {
            error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, span, "class " + classSymbol.name() + " has no static property '" + memberName + "'");
        }
        return null;
    }

    /**
     * Types a qualified enum variant construction {@code Enum.Variant(values...)}
     * (docs/LANGUAGE_SPEC.md section 12). The variant's value types are substituted with the enum
     * type arguments inferred from the values, and the values are checked against the substituted
     * types. The result is the owning enum type, possibly a generic application.
     */
    private Type checkVariantConstruction(CallExprNode call, MemberAccessExprNode member, EnumSymbol enumSymbol) {
        return checkVariantConstruction(call, enumSymbol, member.memberName(), member.span());
    }

    /** Types a variant construction, resolving the variant by name so a module-qualified reference works. */
    private Type checkVariantConstruction(CallExprNode call, EnumSymbol enumSymbol, String variantName, SourceSpan span) {
        Optional<EnumVariantSymbol> resolved = enumSymbol.variant(variantName);
        if (resolved.isEmpty()) {
            for (ExpressionNode argument : call.arguments()) {
                checkExpression(argument);
            }
            error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, span, "enum " + enumSymbol.name() + " has no variant '" + variantName + "'");
            return null;
        }
        EnumVariantSymbol variant = resolved.get();
        List<Type> declaredValueTypes = variant.valueTypes();
        List<Type> argumentTypes = checkArgumentTypes(call);
        if (!checkArity(call, enumSymbol.name() + "." + variant.name(), declaredValueTypes.size(), argumentTypes.size())) {
            // A wrong count suppresses type inference and value type checking; the program cannot be
            // lowered while the arity error exists.
            variantConstructions.put(call, variant);
            return enumSymbol.type();
        }
        List<TypeParameterType> typeParameters = enumSymbol.type().typeParameters();
        Map<TypeParameterType, Type> substitution = resolveVariantTypeArguments(enumSymbol, span, declaredValueTypes, argumentTypes);
        checkArgumentTypesAgainst(call, enumSymbol.name() + "." + variant.name(), substitutedTypes(declaredValueTypes, substitution), argumentTypes);
        variantConstructions.put(call, variant);
        return enumConstructionType(enumSymbol.type(), typeParameters, substitution);
    }

    /**
     * Types a bare qualified variant reference {@code Enum.ValueLessVariant}. A variant that carries
     * values requires a call; a value-less variant of a non-generic enum is a complete value. A
     * generic enum's type arguments cannot be inferred from a value-less reference, so it is
     * reported like any other uninferable generic construction.
     */
    private Type checkVariantRead(MemberAccessExprNode expression, EnumSymbol enumSymbol) {
        return checkVariantRead(expression, enumSymbol, expression.memberName(), expression.span());
    }

    /** Types a value-less variant read, resolving the variant by name for a module-qualified reference. */
    private Type checkVariantRead(ExpressionNode expression, EnumSymbol enumSymbol, String variantName, SourceSpan span) {
        Optional<EnumVariantSymbol> resolved = enumSymbol.variant(variantName);
        if (resolved.isEmpty()) {
            error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, span, "enum " + enumSymbol.name() + " has no variant '" + variantName + "'");
            return null;
        }
        EnumVariantSymbol variant = resolved.get();
        if (!variant.valueTypes().isEmpty()) {
            errorExpected(DiagnosticCode.TYPE_ARITY_MISMATCH, span, //
                            "variant '" + variant.name() + "' requires " + variant.valueTypes().size() + " value(s) and cannot be used without a call", //
                            variant.valueTypes().size() + " value(s)", "a bare variant reference");
            return null;
        }
        variantConstructions.put(expression, variant);
        List<TypeParameterType> typeParameters = enumSymbol.type().typeParameters();
        if (typeParameters.isEmpty()) {
            return enumSymbol.type();
        }
        errorExpected(DiagnosticCode.TYPE_CANNOT_INFER, expression.span(), //
                        "cannot infer type argument for '" + typeParameters.get(0).name() + "' from a value-less variant reference", //
                        "a value that determines " + typeParameters.get(0).name(), "no value");
        return enumConstructionType(enumSymbol.type(), typeParameters, Map.of());
    }

    /** Types a call through a module prefix: a function call, a construction, or a variant construction. */
    private Type checkQualifiedCall(CallExprNode call, QualifiedPrefix qualified) {
        ModuleContents contents = modules.get(qualified.module);
        String written = qualified.module + "::" + String.join("::", qualified.path);
        if (contents == null) {
            for (ExpressionNode argument : call.arguments()) {
                checkExpression(argument);
            }
            error(DiagnosticCode.RESOL_UNKNOWN_MODULE, call.span(), "unknown module '" + qualified.module + "'");
            return null;
        }
        if (qualified.path.size() == 1) {
            Symbol symbol = contents.symbols.get(qualified.path.get(0));
            if (symbol instanceof FunctionSymbol function) {
                qualifiedFunctionCalls.put(call.callee(), function);
                return resolveCallableType(call, written, function, Map.of());
            }
            if (symbol instanceof ClassSymbol classSymbol) {
                return checkConstruction(call, classSymbol);
            }
            if (symbol instanceof EnumSymbol) {
                for (ExpressionNode argument : call.arguments()) {
                    checkExpression(argument);
                }
                error(DiagnosticCode.TYPE_ENUM_AS_VALUE, call.span(), "'" + written + "' is an enum; construct one of its variants");
                return null;
            }
            for (ExpressionNode argument : call.arguments()) {
                checkExpression(argument);
            }
            error(DiagnosticCode.RESOL_UNKNOWN_NAME, call.span(), "unknown name '" + written + "'");
            return null;
        }
        if (qualified.path.size() == 2) {
            Symbol symbol = contents.symbols.get(qualified.path.get(0));
            if (symbol instanceof EnumSymbol enumSymbol) {
                if (qualified.lastStepIsNamespace) {
                    for (ExpressionNode argument : call.arguments()) {
                        checkExpression(argument);
                    }
                    error(DiagnosticCode.RESOL_UNKNOWN_NAME, call.span(), "unknown qualified name '" + written + "'");
                    return null;
                }
                return checkVariantConstruction(call, enumSymbol, qualified.path.get(1), call.span());
            }
            if (symbol instanceof ClassSymbol classSymbol) {
                // A static member is reached with `.`, as `math::Counter.reset()`; `math::Counter::reset`
                // is not a qualified name the language defines.
                if (qualified.lastStepIsNamespace) {
                    for (ExpressionNode argument : call.arguments()) {
                        checkExpression(argument);
                    }
                    error(DiagnosticCode.RESOL_UNKNOWN_NAME, call.span(), "unknown qualified name '" + written + "'");
                    return null;
                }
                return checkStaticMethodCall(call, classSymbol, qualified.path.get(1), call.span());
            }
            for (ExpressionNode argument : call.arguments()) {
                checkExpression(argument);
            }
            error(DiagnosticCode.RESOL_UNKNOWN_NAME, call.span(), "unknown name '" + written + "'");
            return null;
        }
        for (ExpressionNode argument : call.arguments()) {
            checkExpression(argument);
        }
        error(DiagnosticCode.RESOL_UNKNOWN_NAME, call.span(), "unknown qualified name '" + written + "'");
        return null;
    }

    /** Types a bare module-qualified name used as a value; a module member is not a value. */
    private Type checkNamespaceAccess(NamespaceAccessExprNode expression) {
        QualifiedPrefix qualified = qualifiedPrefix(expression);
        if (qualified == null) {
            error(DiagnosticCode.RESOL_UNKNOWN_MODULE, expression.span(), "'::' must name a visible module or alias prefix");
            return null;
        }
        return checkQualifiedRead(expression, qualified);
    }

    /** Types a value reference through a module prefix: a variant read, or an illegal type-as-value. */
    private Type checkQualifiedRead(ExpressionNode expression, QualifiedPrefix qualified) {
        ModuleContents contents = modules.get(qualified.module);
        String written = qualified.module + "::" + String.join("::", qualified.path);
        if (contents == null) {
            error(DiagnosticCode.RESOL_UNKNOWN_MODULE, expression.span(), "unknown module '" + qualified.module + "'");
            return null;
        }
        if (qualified.path.size() == 2) {
            Symbol symbol = contents.symbols.get(qualified.path.get(0));
            if (symbol instanceof EnumSymbol enumSymbol) {
                if (qualified.lastStepIsNamespace) {
                    // Only `prefix::Enum.Variant` is a qualified variant name; `prefix::Enum::Variant`
                    // denotes nothing, and lowering has no node for it, so it must not analyze.
                    error(DiagnosticCode.RESOL_UNKNOWN_NAME, expression.span(), "unknown qualified name '" + written + "'");
                    return null;
                }
                return checkVariantRead(expression, enumSymbol, qualified.path.get(1), expression.span());
            }
            if (symbol instanceof ClassSymbol classSymbol) {
                if (qualified.lastStepIsNamespace) {
                    error(DiagnosticCode.RESOL_UNKNOWN_NAME, expression.span(), "unknown qualified name '" + written + "'");
                    return null;
                }
                return checkStaticPropertyRead(expression, classSymbol, qualified.path.get(1), expression.span());
            }
            error(DiagnosticCode.RESOL_UNKNOWN_NAME, expression.span(), "unknown name '" + written + "'");
            return null;
        }
        if (qualified.path.size() == 1) {
            Symbol symbol = contents.symbols.get(qualified.path.get(0));
            if (symbol instanceof ClassSymbol) {
                error(DiagnosticCode.TYPE_CLASS_AS_VALUE, expression.span(), "class '" + written + "' cannot be used as a value");
            } else if (symbol instanceof InterfaceSymbol) {
                error(DiagnosticCode.TYPE_INTERFACE_AS_VALUE, expression.span(), "interface '" + written + "' cannot be used as a value");
            } else if (symbol instanceof EnumSymbol) {
                error(DiagnosticCode.TYPE_ENUM_AS_VALUE, expression.span(), "enum '" + written + "' must be constructed through one of its variants");
            } else if (symbol instanceof FunctionSymbol function) {
                // `module::name` names the same declaration as the unqualified name, so it must yield
                // the same canonical function value and not a second identity
                // (docs/LANGUAGE_SPEC.md section 6).
                return functionValueOf(expression, function);
            } else {
                error(DiagnosticCode.RESOL_UNKNOWN_NAME, expression.span(), "unknown name '" + written + "'");
            }
            return null;
        }
        error(DiagnosticCode.RESOL_UNKNOWN_NAME, expression.span(), "unknown qualified name '" + written + "'");
        return null;
    }

    /** The construction type of an enum: the bare enum or its application to the inferred arguments. */
    private static Type enumConstructionType(EnumType enumType, List<TypeParameterType> typeParameters, Map<TypeParameterType, Type> substitution) {        if (typeParameters.isEmpty()) {
            return enumType;
        }
        List<Type> arguments = new ArrayList<>(typeParameters.size());
        for (TypeParameterType parameter : typeParameters) {
            Type bound = substitution.get(parameter);
            arguments.add(bound != null ? bound : AnyType.INSTANCE);
        }
        return enumType.parameterizedView(arguments);
    }

    /**
     * Resolves a member read through an interface-typed receiver. Interfaces carry methods only, so a
     * read of any kind is a method used as a value (docs/LANGUAGE_SPEC.md section 8).
     */
    private Type checkInterfaceMemberAccess(MemberAccessExprNode expression, Type interfaceType) {
        InterfaceSymbol interfaceSymbol = interfaceSymbolFor(interfaceType);
        if (interfaceSymbol == null) {
            return null;
        }
        Optional<FunctionSymbol> member = interfaceSymbol.member(expression.memberName());
        if (member.isPresent()) {
            error(DiagnosticCode.TYPE_FUNCTION_AS_VALUE, expression.span(), "method '" + expression.memberName() + "' cannot be used as a value");
        } else {
            error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, expression.span(), "interface " + interfaceType.name() + " has no member '" + expression.memberName() + "'");
        }
        return null;
    }

    private Type checkMethodCall(CallExprNode call, MemberAccessExprNode member) {
        Type receiverType = checkExpression(member.receiver());
        if (receiverType == null) {
            return null;
        }
        boolean nullableReceiver = isNullableType(receiverType);
        if (nullableReceiver && !member.isSafe()) {
            error(DiagnosticCode.TYPE_NULLABLE_DEREFERENCE, member.receiver().span(), //
                            "receiver of '" + member.memberName() + "' may be null; use '?.' or check for null first");
            return null;
        }
        Type result = resolveMethodReturnType(call, member, nullableReceiver ? receiverType.nonNullType() : receiverType);
        if (result == null) {
            return null;
        }
        // A safe call on a nullable receiver yields the member type made nullable; on a non-null
        // receiver it cannot return null and keeps the member type.
        return nullableReceiver ? result.nullableView() : result;
    }

    /** Resolves a member call on a resolved non-null receiver type, returning the declared return type. */
    private Type resolveMethodReturnType(CallExprNode call, MemberAccessExprNode member, Type receiverType) {
        if (isBuiltinToStringMember(member)) {
            return resolveBuiltinToStringCall(call);
        }
        if (isBuiltinEqualsMember(member)) {
            return resolveBuiltinEqualsCall(call);
        }
        if (isBuiltinHashCodeMember(member)) {
            return resolveBuiltinHashCodeCall(call);
        }
        if (receiverType == RegexType.INSTANCE) {
            return checkRegexMethodCall(call, member);
        }
        if (receiverType == RegexMatchType.INSTANCE) {
            if ("group".equals(member.memberName())) {
                checkArguments(call, "group", List.of(IntegerType.INSTANCE));
                return StringType.INSTANCE.nullableView();
            }
            error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, member.span(), "RegexMatch has no member '" + member.memberName() + "'");
            return null;
        }
        BuiltinCollectionMember collectionMember = collectionMember(receiverType, member.memberName());
        if (collectionMember != null) {
            if (collectionMember.isProperty()) {
                error(DiagnosticCode.TYPE_NOT_CALLABLE, member.span(), "'" + member.memberName() + "' is a property, not a method");
            } else {
                checkArguments(call, member.memberName(), collectionMember.substitutedParameterTypes(collectionContext(receiverType).substitution()));
            }
            return collectionMember.substitutedReturnType(collectionContext(receiverType).substitution());
        }
        if (isResultType(receiverType)) {
            return checkResultMethodCall(call, member, (ParameterizedType) receiverType);
        }
        // A guest exception exposes exactly one synthesized member: the getMessage() accessor over its
        // private message slot (docs/LANGUAGE_SPEC.md section 22). It is resolved before the declared
        // method table so an exception class with no declared members still answers the call.
        if (receiverType instanceof ClassType exceptionClass && isExceptionName(exceptionClass.name())
                        && "getMessage".equals(member.memberName())) {
            checkArguments(call, member.memberName(), List.of());
            return StringType.INSTANCE.nullableView();
        }
        InterfaceSymbol interfaceSymbol = interfaceSymbolFor(receiverType);
        if (interfaceSymbol != null) {
            return checkInterfaceMethodCall(call, member, receiverType, interfaceSymbol);
        }
        ClassSymbol classSymbol = classSymbolFor(receiverType);
        if (classSymbol == null) {
            error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, member.span(), "type " + receiverType.name() + " has no member '" + member.memberName() + "'");
            return null;
        }
        Optional<PropertySymbol> storedFunction = classSymbol.property(member.memberName());
        if (storedFunction.isPresent()) {
            // A property of function type holds a callable value, so the call invokes that value
            // indirectly; any other property stays "not callable". The caller applies the receiver's
            // nullability to the result, as it does for every other member call.
            Type throughProperty = checkCallThroughFunctionProperty(call, member, receiverType);
            if (throughProperty != null) {
                return throughProperty;
            }
            error(DiagnosticCode.TYPE_NOT_CALLABLE, member.span(), "'" + member.memberName() + "' is not callable");
            return null;
        }
        Optional<FunctionSymbol> method = classSymbol.method(member.memberName());
        if (method.isEmpty()) {
            error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, member.span(), "class " + receiverType.name() + " has no method '" + member.memberName() + "'");
            return null;
        }
        FunctionSymbol target = method.get();
        expressionTypes.put(member, target.functionType());
        Type result = resolveCallableType(call, classSymbol.name() + "." + target.name(), target, composeSubstitutions(classSymbol.methodSubstitution(member.memberName()), substitutionFor(receiverType)));
        methodCalls.put(call, new ResolvedMethod(target, false));
        return result;
    }

    /**
     * Whether a member call names the built-in root member {@code Any.toString()} (docs/LANGUAGE_SPEC.md
     * section 4). It is available on every receiver, so it is resolved before any per-type member
     * table; a user override is reached at run time through the receiver's method table.
     */
    private static boolean isBuiltinToStringMember(MemberAccessExprNode member) {
        return "toString".equals(member.memberName());
    }

    /**
     * Types a {@code toString()} call: no arguments, result {@code String}. Every argument is still
     * checked so a written argument reports its own error rather than being silently ignored.
     */
    private Type resolveBuiltinToStringCall(CallExprNode call) {
        List<Type> argumentTypes = checkArgumentTypes(call);
        if (!checkArity(call, "toString", 0, argumentTypes.size())) {
            return null;
        }
        builtinToStringCalls.add(call);
        return StringType.INSTANCE;
    }

    /**
     * Whether a member call names the built-in root member {@code Any.equals(other: Any?)}. Like
     * {@code toString}, it is available on every receiver and resolved before any per-type member
     * table, so a user override is reached at run time through the receiver's method table
     * (docs/LANGUAGE_SPEC.md sections 3.3 and 4).
     */
    private static boolean isBuiltinEqualsMember(MemberAccessExprNode member) {
        return "equals".equals(member.memberName());
    }

    /**
     * Types an {@code equals(other)} call: exactly one argument accepted by {@code Any?} and result
     * {@code Boolean}. A safe call on a nullable receiver keeps ordinary nullable-member rules, so
     * the caller makes the result nullable. Every argument is still checked so a written argument
     * reports its own error rather than being silently ignored.
     */
    private Type resolveBuiltinEqualsCall(CallExprNode call) {
        checkArguments(call, "equals", List.of(AnyType.INSTANCE.nullableView()));
        builtinEqualsCalls.add(call);
        return BooleanType.INSTANCE;
    }

    /**
     * Whether a member call names the built-in root member {@code Any.hashCode()}. Like
     * {@code toString} it takes no arguments and is available on every receiver, resolved before any
     * per-type member table, so a user override is reached at run time through the receiver's method
     * table (docs/LANGUAGE_SPEC.md sections 3 and 4).
     */
    private static boolean isBuiltinHashCodeMember(MemberAccessExprNode member) {
        return "hashCode".equals(member.memberName());
    }

    /**
     * Types a {@code hashCode()} call: no arguments, result {@code Integer}. Every argument is still
     * checked so a written argument reports its own error rather than being silently ignored.
     */
    private Type resolveBuiltinHashCodeCall(CallExprNode call) {
        List<Type> argumentTypes = checkArgumentTypes(call);
        if (!checkArity(call, "hashCode", 0, argumentTypes.size())) {
            return null;
        }
        builtinHashCodeCalls.add(call);
        return IntegerType.INSTANCE;
    }

    /**
     * Types one of the built-in {@code Regex} methods (docs/LANGUAGE_SPEC.md section 14):
     * {@code matches}, {@code find}, {@code findAll}, and {@code replace}.
     */
    private Type checkRegexMethodCall(CallExprNode call, MemberAccessExprNode member) {
        switch (member.memberName()) {
            case "matches" -> {
                checkArguments(call, "matches", List.of(StringType.INSTANCE));
                return BooleanType.INSTANCE;
            }
            case "find" -> {
                checkArguments(call, "find", List.of(StringType.INSTANCE));
                return RegexMatchType.INSTANCE.nullableView();
            }
            case "findAll" -> {
                checkArguments(call, "findAll", List.of(StringType.INSTANCE));
                return BuiltinCollectionTypes.LIST.parameterizedView(List.of(RegexMatchType.INSTANCE));
            }
            case "replace" -> {
                checkArguments(call, "replace", List.of(StringType.INSTANCE, StringType.INSTANCE));
                return StringType.INSTANCE;
            }
            default -> {
                error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, member.span(), "Regex has no member '" + member.memberName() + "'");
                return null;
            }
        }
    }

    /**
     * Resolves a call through an interface-typed receiver. Both a default method and an abstract
     * requirement are callable: the requirement is dispatched to the concrete implementor at runtime.
     */
    private Type checkInterfaceMethodCall(CallExprNode call, MemberAccessExprNode member, Type interfaceType, InterfaceSymbol interfaceSymbol) {
        Optional<FunctionSymbol> target = interfaceSymbol.member(member.memberName());
        if (target.isEmpty()) {
            error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, member.span(), "interface " + interfaceType.name() + " has no method '" + member.memberName() + "'");
            return null;
        }
        FunctionSymbol method = target.get();
        expressionTypes.put(member, method.functionType());
        Type result = resolveCallableType(call, interfaceType.name() + "." + method.name(), method, substitutionFor(interfaceType));
        methodCalls.put(call, new ResolvedMethod(method, false));
        return result;
    }

    private Type resolveMethodCall(CallExprNode call, NameRefExprNode calleeName, FunctionSymbol method, boolean implicitThis) {
        nameSymbols.put(calleeName, method);
        expressionTypes.put(calleeName, method.functionType());
        Type result = resolveCallableType(call, implicitReceiverLabel(method), method, Map.of());
        methodCalls.put(call, new ResolvedMethod(method, implicitThis));
        return result;
    }

    /** Qualified display name of a method reached through an implicit receiver, for diagnostics. */
    private String implicitReceiverLabel(FunctionSymbol method) {
        if (currentClass != null) {
            return currentClass.name() + "." + method.name();
        }
        if (currentInterface != null) {
            return currentInterface.name() + "." + method.name();
        }
        return method.name();
    }

    /**
     * Checks a call's arguments against a callable and returns the call's result type. The callable's
     * own type parameters are inferred from the argument types, then its parameter and return types
     * are substituted with the receiver and inferred substitutions (docs/LANGUAGE_SPEC.md section 11).
     */
    private Type resolveCallableType(CallExprNode call, String calleeLabel, FunctionSymbol target, Map<TypeParameterType, Type> receiverSubstitution) {
        List<Type> declaredParameterTypes = substitutedParameterTypes(target.parameters(), receiverSubstitution);
        List<DeferredArgument> deferred = new ArrayList<>();
        List<Type> argumentTypes = checkArgumentTypes(call, deferred);
        if (!checkArity(call, calleeLabel, target.parameterCount(), argumentTypes.size())) {
            // The receiver is not an explicit argument, so arity is the declared parameter count.
            // A wrong count suppresses inference and type checking; the program cannot be lowered
            // while the arity error exists, so a best-effort result type is sufficient here.
            return target.returnType().substitute(receiverSubstitution);
        }
        List<TypeParameterType> typeParameters = target.typeParameters();
        Map<TypeParameterType, Type> substitution = explicitTypeArguments(call, typeParameters, declaredParameterTypes, argumentTypes, calleeLabel);
        if (substitution == null) {
            // A deferred argument contributes nothing to this pass — that is the point of deferring it —
            // so the callee infers from the arguments it can see, exactly as it did before function values
            // could appear in an argument list.
            substitution = inferCallableTypeArguments(call, typeParameters, declaredParameterTypes, argumentTypes, target.returnType(), !deferred.isEmpty());
        }
        List<Type> checkedParameterTypes = substitutedTypes(declaredParameterTypes, substitution);
        if (substitution.size() == typeParameters.size()) {
            // Inference succeeded, so each parameter type is final. A deferred generic function reference
            // is typed against exactly that, which is what lets `apply(identity, 42)` instantiate
            // `identity` from the parameter it fills even though the callee is generic and had to decide
            // its own type parameter first. Where inference failed the callee has no final parameter type
            // at all, and the reference keeps reporting the inference failure rather than being typed
            // against a formal.
            checkDeferredArguments(deferred, checkedParameterTypes, argumentTypes);
        }
        checkArgumentTypesAgainst(call, calleeLabel, checkedParameterTypes, argumentTypes);
        return target.returnType().substitute(receiverSubstitution).substitute(substitution);
    }

    /**
     * Resolves explicit call type arguments when present; otherwise returns {@code null} so the call
     * infers its arguments. Explicit arguments on a non-generic callee are a {@code TYPE_NOT_GENERIC}
     * error, and a wrong count is a {@code TYPE_TYPE_ARGUMENT_ARITY} error.
     */
    private Map<TypeParameterType, Type> explicitTypeArguments(CallExprNode call, List<TypeParameterType> typeParameters, List<Type> parameterTypes, List<Type> argumentTypes, String calleeName) {
        return resolveExplicitTypeArguments(call, typeParameters, parameterTypes, argumentTypes, calleeName);
    }

    /**
     * Checks every argument of a call whose parameter types are already final, supplying each as the
     * expected type of the argument that fills it, and returns the arguments' static types in order.
     *
     * <p>Section 6 types a generic function reference used as a value against "a complete expected
     * function type", and an argument position whose parameter has a function type supplies one. Without
     * it, {@code apply(identity, 1)} would report {@code SOLV-TYPE-030} for a callee whose parameter type
     * is known without consulting the argument, while the equivalent
     * {@code val f: func(Integer): Integer = identity} compiled — a difference no reader could predict.
     *
     * <p>Every caller that reaches here knows its parameter types before the arguments are examined: a
     * call through a function value reads them from the value's monomorphic function type, and a call on a
     * member whose parameters this analyzer states itself (a built-in, a collection member, a {@code
     * super} call) knows them from the declaration. A call whose callee is a user-declared generic
     * callable instead goes through {@link #checkArgumentTypes(CallExprNode, List)}, which holds
     * generic-function references back until the same types are final, and {@link
     * #checkDeferredArguments} then finishes them with the expected-type rule below — so an argument is
     * checked the same way whichever path reached it.
     *
     * <p>Only a reference to a generic function is given an expected type. Nothing else in the language
     * consults an expected type at an argument position today — a generic variant construction written as
     * an argument is still an inference failure there — so leaving every other argument to the ordinary
     * path keeps section 6 satisfied without silently changing the rules any other expression form plays
     * by in that position.
     */
    private List<Type> checkArgumentTypesWithFinalParameters(CallExprNode call, List<Type> parameterTypes) {
        List<ExpressionNode> arguments = call.arguments();
        List<Type> types = new ArrayList<>(arguments.size());
        for (int i = 0; i < arguments.size(); i++) {
            ExpressionNode argument = arguments.get(i);
            Type parameterType = i < parameterTypes.size() ? parameterTypes.get(i) : null;
            boolean pushExpected = parameterType != null && isGenericFunctionReference(argument);
            if (pushExpected) {
                expectedTypes.push(parameterType);
            }
            try {
                types.add(checkExpression(argument));
            } finally {
                if (pushExpected) {
                    expectedTypes.pop();
                }
            }
        }
        return types;
    }

    /**
     * Checks the arguments of a call whose parameter types are not yet known, holding back every argument
     * that is a reference to a generic function so the caller can type it later.
     *
     * <p>Such an argument cannot be checked yet, because the parameter type it must match is not settled:
     * on a generic callee the parameter may be written in the callee's own type parameters, which
     * inference is about to decide from the other arguments. Binding the reference from a formal would
     * either fix a callee's type parameter from one argument ahead of the rest — so a later argument that
     * disagrees reports against the wrong expression — or leak a type parameter of one callable into the
     * context of another, which the two are not required to share. The argument is recorded in
     * {@code deferred} with no type and finished by {@link #checkDeferredArguments} once the callee's
     * parameters are final.
     *
     * <p>Every other argument is checked now, exactly as it always was, and gets no expected type: its
     * checking does not consult one, so there is nothing for the ordering to change.
     */
    private List<Type> checkArgumentTypes(CallExprNode call, List<DeferredArgument> deferred) {
        List<ExpressionNode> arguments = call.arguments();
        List<Type> types = new ArrayList<>(arguments.size());
        for (int i = 0; i < arguments.size(); i++) {
            ExpressionNode argument = arguments.get(i);
            if (deferred != null && isGenericFunctionReference(argument)) {
                deferred.add(new DeferredArgument(i, argument));
                types.add(null);
            } else {
                types.add(checkExpression(argument));
            }
        }
        return types;
    }

    /**
     * Checks every argument of a call with no parameter types to supply — the calls whose callee is not a
     * user-declared callable at all, or whose parameter types are irrelevant to typing the arguments.
     */
    private List<Type> checkArgumentTypes(CallExprNode call) {
        return checkArgumentTypesWithFinalParameters(call, List.of());
    }

    /**
     * Types the arguments {@link #checkArgumentTypes(CallExprNode, List)} held back, each against the
     * parameter type it fills, and writes the results into {@code argumentTypes}.
     *
     * <p>{@code parameterTypes} must be the callee's parameter types as finally decided — after its type
     * arguments are known and substituted — because that is the type the reference has to be assignable
     * to, and the same list the caller's assignability check compares against. An index past the declared
     * parameter count is left alone: a call may supply more arguments than the callee declares, and the
     * arity error already reported suppresses argument type checking.
     */
    private void checkDeferredArguments(List<DeferredArgument> deferred, List<Type> parameterTypes, List<Type> argumentTypes) {
        for (DeferredArgument argument : deferred) {
            if (argument.index() >= parameterTypes.size()) {
                continue;
            }
            expectedTypes.push(parameterTypes.get(argument.index()));
            try {
                argumentTypes.set(argument.index(), checkExpression(argument.expression()));
            } finally {
                expectedTypes.pop();
            }
        }
    }

    /**
     * One argument whose checking is postponed until the callee's parameter types are decided. The index
     * is the parameter it fills; the expression is what gets typed against that parameter's type.
     */
    private record DeferredArgument(int index, ExpressionNode expression) {
    }

    /**
     * Whether {@code expression} is a bare or module-qualified reference to a generic function, which is
     * the only argument shape for which an argument position supplies an expected function type.
     */
    private boolean isGenericFunctionReference(ExpressionNode expression) {
        if (expression instanceof NameRefExprNode name) {
            return resolveName(name.name()).orElse(null) instanceof FunctionSymbol function && !function.typeParameters().isEmpty();
        }
        QualifiedPrefix qualified = qualifiedPrefix(expression);
        if (qualified == null || qualified.path.size() != 1) {
            return false;
        }
        // `prefix::Name` names the same declaration the unqualified name does; a longer path is a variant
        // or static member read, which this revision does not accept as a function value at all.
        ModuleContents contents = modules.get(qualified.module);
        return contents != null
                && contents.symbols.get(qualified.path.get(0)) instanceof FunctionSymbol function
                && !function.typeParameters().isEmpty();
    }

    private void checkArguments(CallExprNode call, String calleeLabel, List<Type> parameterTypes) {
        List<Type> argumentTypes = checkArgumentTypesWithFinalParameters(call, parameterTypes);
        if (checkArity(call, calleeLabel, parameterTypes.size(), argumentTypes.size())) {
            checkArgumentTypesAgainst(call, calleeLabel, parameterTypes, argumentTypes);
        }
    }

    /**
     * Validates the number of explicit arguments supplied to a statically resolved callable
     * (docs/LANGUAGE_SPEC.md section 6). The receiver of an instance method or constructor is not an
     * explicit source argument, so callers pass only the declared parameter count. Returns whether
     * argument type checking may proceed; an arity error suppresses it so the count is never also
     * reported as a type problem.
     */
    private boolean checkArity(CallExprNode call, String calleeLabel, int parameterCount, int argumentCount) {
        if (parameterCount == argumentCount) {
            return true;
        }
        errorExpected(DiagnosticCode.TYPE_ARITY_MISMATCH, call.span(), //
                        "'" + calleeLabel + "' expects " + plural(parameterCount, "argument") + " but " + argumentCount + (argumentCount == 1 ? " was provided" : " were provided"), //
                        plural(parameterCount, "argument"), plural(argumentCount, "argument"));
        return false;
    }

    private static String plural(int count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }

    /**
     * Checks argument types after arity has already been validated, so every supplied argument has a
     * corresponding parameter.
     */
    private void checkArgumentTypesAgainst(CallExprNode call, String calleeLabel, List<Type> parameterTypes, List<Type> argumentTypes) {
        for (int i = 0; i < parameterTypes.size(); i++) {
            Type argumentType = argumentTypes.get(i);
            Type parameterType = parameterTypes.get(i);
            if (argumentType != null && !assignableOrWidened(call.arguments().get(i), argumentType, parameterType)) {
                errorExpected(DiagnosticCode.TYPE_MISMATCH, call.arguments().get(i).span(), //
                                "argument " + (i + 1) + " of '" + calleeLabel + "' has the wrong type", parameterType.name(), argumentType.name());
            }
        }
    }

    private static List<Type> substitutedParameterTypes(List<VariableSymbol> parameters, Map<TypeParameterType, Type> substitution) {
        if (substitution.isEmpty()) {
            List<Type> types = new ArrayList<>(parameters.size());
            for (VariableSymbol parameter : parameters) {
                types.add(parameter.type());
            }
            return types;
        }
        List<Type> types = new ArrayList<>(parameters.size());
        for (VariableSymbol parameter : parameters) {
            types.add(parameter.type().substitute(substitution));
        }
        return types;
    }

    private static List<Type> substitutedTypes(List<Type> types, Map<TypeParameterType, Type> substitution) {
        if (substitution.isEmpty()) {
            return types;
        }
        List<Type> substituted = new ArrayList<>(types.size());
        for (Type type : types) {
            substituted.add(type.substitute(substitution));
        }
        return substituted;
    }

    /**
     * The collection context behind a receiver type, carrying the collection descriptor and the
     * identity substitution that binds its type parameters to the receiver's arguments. Returns
     * {@code null} when the receiver is not a built-in collection application.
     */
    private static CollectionContext collectionContext(Type type) {
        if (type instanceof ParameterizedType parameterized && parameterized.base() instanceof BuiltinCollectionType collection) {
            return new CollectionContext(collection, parameterized.substitution());
        }
        if (type instanceof BuiltinCollectionType collection) {
            return new CollectionContext(collection, Map.of());
        }
        return null;
    }

    /**
     * Resolves a built-in collection construction {@code List<T>(...)}, {@code Set<T>(...)},
     * {@code Map<K, V>(key: value, ...)}, or {@code Stack<T>(...)} (docs/LANGUAGE_SPEC.md section 11).
     * Explicit type arguments bind the descriptor's type parameters; a construction that omits them
     * infers them from the enclosing expected type, so {@code val l: List<Integer> = List(1, 2)} resolves
     * {@code T} from the left-hand side. A construction with no expected type and no explicit type
     * arguments has no evidence for the type parameters. Value arguments become the collection's
     * initial elements, and a {@code Map} takes {@code key: value} entries.
     */
    private Type checkCollectionConstruction(CallExprNode expression, BuiltinCollectionType collection) {
        List<TypeRef> typeArguments = expression.typeArguments();
        if (!typeArguments.isEmpty() && typeArguments.size() != collection.typeParameters().size()) {
            errorExpected(DiagnosticCode.TYPE_TYPE_ARGUMENT_ARITY, expression.span(), //
                    "call to '" + collection.name() + "' has the wrong number of type arguments", //
                    Integer.toString(collection.typeParameters().size()), Integer.toString(typeArguments.size()));
            return null;
        }
        Type constructed;
        if (typeArguments.isEmpty()) {
            // Infer the type parameters from the enclosing declaration's expected type, so
            // {@code var l: List<Integer> = List(1, 2)} resolves T from the left-hand side. A
            // construction with no enclosing expected type has no evidence for the element type.
            Type expected = expectedTypes.isEmpty() ? null : expectedTypes.peek();
            if (!(expected instanceof ParameterizedType parameterized) || parameterized.base() != collection) {
                errorExpected(DiagnosticCode.TYPE_CANNOT_INFER, expression.span(), //
                        "cannot infer the type arguments of this " + collection.name() + " construction", //
                        "an explicit type argument or a declared type", "no type argument");
                return null;
            }
            expressionTypes.put(expression.callee(), parameterized);
            constructed = parameterized;
        } else {
            List<Type> argumentTypes = new ArrayList<>(typeArguments.size());
            for (TypeRef argument : typeArguments) {
                Type resolved = resolveType(argument);
                if (resolved == null) {
                    return null;
                }
                argumentTypes.add(resolved);
            }
            constructed = collection.parameterizedView(argumentTypes);
            expressionTypes.put(expression.callee(), constructed);
        }
        checkCollectionArguments(expression, collection, constructed);
        return constructed;
    }

    /**
     * Checks the value arguments of a collection construction against the constructed type
     * parameters. {@code List}, {@code Set}, and {@code Stack} take initial elements assignable to
     * the element type; {@code Map} takes {@code key: value} entries whose key and value are
     * assignable to {@code K} and {@code V}. A {@code key: value} entry in any other collection, or
     * a bare positional value in a {@code Map}, is rejected.
     */
    private void checkCollectionArguments(CallExprNode call, BuiltinCollectionType collection, Type constructed) {
        Map<TypeParameterType, Type> substitution =
                constructed instanceof ParameterizedType parameterized ? parameterized.substitution() : Map.of();
        boolean map = collection == BuiltinCollectionTypes.MAP;
        Type elementType = map ? null : substitution.get(collection.typeParameter(0));
        Type keyType = map ? substitution.get(collection.typeParameter(0)) : null;
        Type valueType = map ? substitution.get(collection.typeParameter(1)) : null;
        for (ExpressionNode argument : call.arguments()) {
            if (argument instanceof MapEntryExprNode entry) {
                if (!map) {
                    checkMapEntry(entry);
                    continue;
                }
                checkCollectionArgument(entry.key(), keyType, "key of '" + collection.name() + "'");
                checkCollectionArgument(entry.value(), valueType, "value of '" + collection.name() + "'");
                continue;
            }
            if (map) {
                checkExpression(argument);
                error(DiagnosticCode.TYPE_MISMATCH, argument.span(), //
                        "a Map construction initializes its entries with `key: value`, not a positional value");
                continue;
            }
            checkCollectionArgument(argument, elementType, "element of '" + collection.name() + "'");
        }
    }

    /**
     * Types one collection value argument with the expected element (or map key/value) type pushed,
     * so a nested collection construction infers from its element position rather than from the
     * outer declaration, and reports a type mismatch when the value is not assignable.
     */
    private void checkCollectionArgument(ExpressionNode argument, Type expected, String label) {
        if (expected != null) {
            expectedTypes.push(expected);
        }
        try {
            Type actual = checkExpression(argument);
            if (actual != null && expected != null && !assignableOrWidened(argument, actual, expected)) {
                errorExpected(DiagnosticCode.TYPE_MISMATCH, argument.span(), //
                        label + " has the wrong type", expected.name(), actual.name());
            }
        } finally {
            if (expected != null) {
                expectedTypes.pop();
            }
        }
    }

    /** A resolved built-in collection receiver and the substitution binding its type parameters. */
    private static final class CollectionContext {
        private final BuiltinCollectionType type;
        private final Map<TypeParameterType, Type> substitution;

        CollectionContext(BuiltinCollectionType type, Map<TypeParameterType, Type> substitution) {
            this.type = type;
            this.substitution = substitution;
        }

        BuiltinCollectionType collectionType() {
            return type;
        }

        Map<TypeParameterType, Type> substitution() {
            return substitution;
        }
    }

    /**
     * Resolves explicit call type arguments {@code Name<T>(...)} into an identity substitution that
     * binds the callee's type parameters to the written argument types. Returns {@code null} when the
     * call carries no explicit arguments so inference proceeds unchanged; otherwise it reports a
     * {@code TYPE_NOT_GENERIC} or {@code TYPE_TYPE_ARGUMENT_ARITY} diagnostic and returns {@code null}
     * when the arguments are invalid.
     */
    private Map<TypeParameterType, Type> resolveExplicitTypeArguments(CallExprNode call, List<TypeParameterType> typeParameters, List<Type> parameterTypes, List<Type> argumentTypes, String calleeName) {
        List<TypeRef> arguments = call.typeArguments();
        if (arguments.isEmpty()) {
            return null;
        }
        if (typeParameters.isEmpty()) {
            errorExpected(DiagnosticCode.TYPE_NOT_GENERIC, call.span(), //
                            "call to '" + calleeName + "' has no type parameters", "a generic callee", calleeName);
            return null;
        }
        if (arguments.size() != typeParameters.size()) {
            errorExpected(DiagnosticCode.TYPE_TYPE_ARGUMENT_ARITY, call.span(), //
                            "call to '" + calleeName + "' has the wrong number of type arguments", Integer.toString(typeParameters.size()), Integer.toString(arguments.size()));
            return null;
        }
        Map<TypeParameterType, Type> substitution = new IdentityHashMap<>();
        for (int i = 0; i < typeParameters.size(); i++) {
            Type resolved = resolveType(arguments.get(i));
            if (resolved == null) {
                // RESOL_UNKNOWN_TYPE was already reported at the type reference span.
                return null;
            }
            substitution.put(typeParameters.get(i), resolved);
        }
        return substitution;
    }

    private Map<TypeParameterType, Type> inferCallableTypeArguments(CallExprNode call, List<TypeParameterType> typeParameters, List<Type> parameterTypes, List<Type> argumentTypes) {
        return inferCallableTypeArguments(call, typeParameters, parameterTypes, argumentTypes, null, false);
    }

    /**
     * Infers a generic callable's type arguments from its argument types. A type parameter bound by
     * an argument is retained; an unbound parameter is reported as uninferable and yields no binding.
     *
     * <p>{@code declaredReturnType} is unified against the expected type in scope when, and only when,
     * {@code fromExpectedType} says an argument was deferred — which is what keeps
     * {@code val made: func(String): String = compose(identity)} working. {@code compose<T>(f: func(T): T)}
     * names {@code T} only inside its one parameter, and that parameter is the deferred argument, so the
     * only remaining evidence is the declared type the call is being checked against — the same evidence a
     * generic variant construction already takes from its enclosing declaration, and the same reason the
     * report is unchanged when even that is missing.
     *
     * <p>Deferring exists precisely so a type parameter is not bound from one argument ahead of the
     * others, so the declared result is consulted only after the argument positions produced no complete
     * substitution; a parameter those positions did bind keeps the binding they gave it. Gating on
     * {@code fromExpectedType} is what keeps a call with no deferred argument inferring exactly as it
     * always has, whether or not it happens to sit in a declared type's initializer.
     */
    private Map<TypeParameterType, Type> inferCallableTypeArguments(CallExprNode call, List<TypeParameterType> typeParameters, List<Type> parameterTypes, List<Type> argumentTypes, Type declaredReturnType, boolean fromExpectedType) {
        if (typeParameters.isEmpty()) {
            return Map.of();
        }
        Map<TypeParameterType, Type> bindings = new IdentityHashMap<>();
        int checked = Math.min(parameterTypes.size(), argumentTypes.size());
        for (int i = 0; i < checked; i++) {
            Type argumentType = argumentTypes.get(i);
            if (argumentType != null) {
                unifyTypeParameter(parameterTypes.get(i), argumentType, typeParameters, bindings);
            }
        }
        if (fromExpectedType && bindings.size() < typeParameters.size() && !expectedTypes.isEmpty()) {
            unifyTypeParameter(declaredReturnType, expectedTypes.peek(), typeParameters, bindings);
        }
        for (TypeParameterType parameter : typeParameters) {
            if (!bindings.containsKey(parameter)) {
                errorExpected(DiagnosticCode.TYPE_CANNOT_INFER, call.span(), //
                                "cannot infer type argument for '" + parameter.name() + "'", "an argument that determines " + parameter.name(), "no determining argument");
                return Map.of();
            }
        }
        return bindings;
    }

    /**
     * Resolves the type arguments of a variant construction (docs/LANGUAGE_SPEC.md section 12, and the
     * error-handling phases). Type parameters are first bound positionally from the argument types;
     * any still-unbound parameter is filled from the enclosing expected type when that expected type is
     * a generic application of the same enum, so {@code val r: Result<T, E> = Ok(v)} and {@code return
     * Err(e)ENCE in a {@code Result}-returning function resolve both arguments. A parameter with no
     * evidence at all is uninferable and yields its error, exactly like any other generic construction.
     */
    private Map<TypeParameterType, Type> resolveVariantTypeArguments(EnumSymbol enumSymbol, SourceSpan span, List<Type> declaredValueTypes, List<Type> argumentTypes) {
        List<TypeParameterType> typeParameters = enumSymbol.type().typeParameters();
        Map<TypeParameterType, Type> bindings = new IdentityHashMap<>();
        int checked = Math.min(declaredValueTypes.size(), argumentTypes.size());
        for (int i = 0; i < checked; i++) {
            Type argumentType = argumentTypes.get(i);
            if (argumentType != null) {
                unifyTypeParameter(declaredValueTypes.get(i), argumentType, typeParameters, bindings);
            }
        }
        if (bindings.size() == typeParameters.size()) {
            return bindings;
        }
        for (TypeParameterType parameter : typeParameters) {
            if (bindings.containsKey(parameter)) {
                continue;
            }
            Type provided = expectedTypeArgumentFor(enumSymbol, parameter);
            if (provided != null) {
                bindings.put(parameter, provided);
            }
        }
        for (TypeParameterType parameter : typeParameters) {
            if (!bindings.containsKey(parameter)) {
                errorExpected(DiagnosticCode.TYPE_CANNOT_INFER, span, //
                                "cannot infer type argument for '" + parameter.name() + "'", "an argument or declared type that determines " + parameter.name(), "no determining argument");
                return Map.of();
            }
        }
        return bindings;
    }

    /** The provided type for {@code parameter} from the enclosing expected {@code Result}, if any. */
    private Type expectedTypeArgumentFor(EnumSymbol enumSymbol, TypeParameterType parameter) {
        if (expectedTypes.isEmpty()) {
            return null;
        }
        Type expected = expectedTypes.peek();
        if (!(expected instanceof ParameterizedType parameterized) || parameterized.base() != enumSymbol.type()) {
            return null;
        }
        List<TypeParameterType> parameters = enumSymbol.type().typeParameters();
        int index = parameters.indexOf(parameter);
        if (index < 0 || index >= parameterized.arguments().size()) {
            return null;
        }
        return parameterized.arguments().get(index);
    }

    /** Binds a declared parameter type's free type parameters from one argument type. */
    private static void unifyTypeParameter(Type parameter, Type argument, List<TypeParameterType> typeParameters, Map<TypeParameterType, Type> bindings) {
        if (parameter instanceof TypeParameterType typeParameter && containsTypeParameter(typeParameters, typeParameter)) {
            bindings.putIfAbsent(typeParameter, argument);
            return;
        }
        if (parameter instanceof NullableType nullableParameter) {
            Type argumentInner = argument instanceof NullableType nullableArgument ? nullableArgument.inner() : argument;
            unifyTypeParameter(nullableParameter.inner(), argumentInner, typeParameters, bindings);
            return;
        }
        if (parameter instanceof ParameterizedType parameterized && argument instanceof ParameterizedType applied && parameterized.base() == applied.base()) {
            for (int i = 0; i < parameterized.arguments().size(); i++) {
                unifyTypeParameter(parameterized.arguments().get(i), applied.arguments().get(i), typeParameters, bindings);
            }
            return;
        }
        // A declared parameter may itself be a function type whose parameter or result positions
        // mention the type parameters, as `f: func(T): T`. A function value argument carries a
        // monomorphic function type, so descending both sides is what binds `T` from it.
        // Arity is deliberately not reconciled here: a mismatch leaves the parameter unbound and
        // the caller reports the resulting defect, rather than unifyTypeParameter inventing a second
        // arity diagnostic alongside the existing assignability one.
        if (parameter instanceof FunctionType declaredFunction && argument instanceof FunctionType appliedFunction
                && declaredFunction.parameterTypes().size() == appliedFunction.parameterTypes().size()) {
            for (int i = 0; i < declaredFunction.parameterTypes().size(); i++) {
                unifyTypeParameter(declaredFunction.parameterTypes().get(i), appliedFunction.parameterTypes().get(i), typeParameters, bindings);
            }
            unifyTypeParameter(declaredFunction.returnType(), appliedFunction.returnType(), typeParameters, bindings);
        }
    }

    private static boolean containsTypeParameter(List<TypeParameterType> typeParameters, TypeParameterType candidate) {
        for (TypeParameterType parameter : typeParameters) {
            if (parameter == candidate) {
                return true;
            }
        }
        return false;
    }

    private Type checkMemberAccess(MemberAccessExprNode expression) {
        if (expression.receiver() instanceof SuperExprNode) {
            return checkSuperMemberAccess(expression);
        }
        QualifiedPrefix qualified = qualifiedPrefix(expression);
        if (qualified != null) {
            return checkQualifiedRead(expression, qualified);
        }
        if (expression.receiver() instanceof NameRefExprNode name) {
            EnumSymbol enumSymbol = enumSymbolNamed(name);
            if (enumSymbol != null) {
                return checkVariantRead(expression, enumSymbol);
            }
            ClassSymbol classSymbol = classSymbolNamed(name);
            if (classSymbol != null) {
                return checkStaticPropertyRead(expression, classSymbol);
            }
        }
        Type receiverType = checkExpression(expression.receiver());
        if (receiverType == null) {
            return null;
        }
        boolean nullableReceiver = isNullableType(receiverType);
        if (nullableReceiver && !expression.isSafe()) {
            error(DiagnosticCode.TYPE_NULLABLE_DEREFERENCE, expression.receiver().span(), //
                            "receiver of '" + expression.memberName() + "' may be null; use '?.' or check for null first");
            return null;
        }
        Type result = resolveMemberRead(expression, nullableReceiver ? receiverType.nonNullType() : receiverType);
        if (result == null) {
            return null;
        }
        return nullableReceiver ? result.nullableView() : result;
    }

    /** Resolves a member read on a resolved non-null receiver type, returning the declared member type. */
    private Type resolveMemberRead(MemberAccessExprNode expression, Type receiverType) {
        // Any.toString/Any.equals/Any.hashCode are the universal members of every non-null value. A bare
        // member read of any of them is never a value and is rejected identically for every receiver kind,
        // whether or not the receiver's class overrides the member (docs/LANGUAGE_SPEC.md sections 3 and 4).
        String memberName = expression.memberName();
        if ("toString".equals(memberName) || "equals".equals(memberName) || "hashCode".equals(memberName)) {
            error(DiagnosticCode.TYPE_FUNCTION_AS_VALUE, expression.span(), "method '" + memberName + "' cannot be used as a value");
            return null;
        }
        if (receiverType == RegexType.INSTANCE) {
            if (isRegexMethodName(expression.memberName())) {
                error(DiagnosticCode.TYPE_FUNCTION_AS_VALUE, expression.span(), "method '" + expression.memberName() + "' cannot be used as a value");
            } else {
                error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, expression.span(), "Regex has no member '" + expression.memberName() + "'");
            }
            return null;
        }
        if (receiverType == RegexMatchType.INSTANCE) {
            switch (expression.memberName()) {
                case "value" -> {
                    return StringType.INSTANCE;
                }
                case "start", "end", "groupCount" -> {
                    return IntegerType.INSTANCE;
                }
                case "group" -> {
                    error(DiagnosticCode.TYPE_FUNCTION_AS_VALUE, expression.span(), "method 'group' cannot be used as a value");
                    return null;
                }
                default -> {
                    error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, expression.span(), "RegexMatch has no member '" + expression.memberName() + "'");
                    return null;
                }
            }
        }
        BuiltinCollectionMember collectionMember = collectionMember(receiverType, expression.memberName());
        if (collectionMember != null) {
            if (collectionMember.isProperty()) {
                return collectionMember.returnType().substitute(collectionContext(receiverType).substitution());
            }
            error(DiagnosticCode.TYPE_FUNCTION_AS_VALUE, expression.span(), "method '" + expression.memberName() + "' cannot be used as a value");
            return null;
        }
        if (isResultType(receiverType)) {
            // A Result carries its operations as synthesized members (docs/LANGUAGE_SPEC.md
            // error-handling operations): a known name read without a call is a method used as a
            // value, and any other name is simply not a Result member.
            if (isResultOperationName(expression.memberName())) {
                error(DiagnosticCode.TYPE_FUNCTION_AS_VALUE, expression.span(), "method '" + expression.memberName() + "' cannot be used as a value");
            } else {
                error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, expression.span(), "Result has no member '" + expression.memberName() + "'");
            }
            return null;
        }
        if (receiverType instanceof ClassType exceptionReadClass && isExceptionName(exceptionReadClass.name())
                        && "getMessage".equals(expression.memberName())) {
            // The synthesized exception accessor is a method, so a bare read is a method used as a value;
            // the private message field itself is never a member and reads fall through to unknown-member.
            error(DiagnosticCode.TYPE_FUNCTION_AS_VALUE, expression.span(), "method 'getMessage' cannot be used as a value");
            return null;
        }
        if (interfaceSymbolFor(receiverType) != null) {
            return checkInterfaceMemberAccess(expression, receiverType);
        }
        ClassSymbol classSymbol = classSymbolFor(receiverType);
        if (classSymbol == null) {
            error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, expression.span(), "type " + receiverType.name() + " has no member '" + expression.memberName() + "'");
            return null;
        }
        Optional<PropertySymbol> property = classSymbol.property(expression.memberName());
        if (property.isPresent()) {
            PropertySymbol resolved = property.get();
            propertyAccesses.put(expression, resolved);
            if (checkingConstructor && !resolved.hasInitializer() && !definitelyInitialized.contains(resolved)) {
                error(DiagnosticCode.TYPE_UNINITIALIZED_PROPERTY, expression.span(), "property '" + resolved.name() + "' is read before it is initialized");
            }
            return resolved.type().substitute(composeSubstitutions(classSymbol.propertySubstitution(resolved), substitutionFor(receiverType)));
        }
        if (classSymbol.method(expression.memberName()).isPresent()) {
            error(DiagnosticCode.TYPE_FUNCTION_AS_VALUE, expression.span(), "method '" + expression.memberName() + "' cannot be used as a value");
        } else {
            error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, expression.span(), "class " + classSymbol.name() + " has no member '" + expression.memberName() + "'");
        }
        return null;
    }

    /** Whether a member name denotes one of the built-in {@code Regex} methods. */
    private static boolean isRegexMethodName(String name) {
        return "matches".equals(name) || "find".equals(name) || "findAll".equals(name) || "replace".equals(name);
    }

    /**
     * Whether a member name denotes a synthesized {@code Result} error-handling operation
     * ({@code isOk}, {@code isErr}, {@code unwrap}, {@code unwrapErr}, {@code expect}, or
     * {@code ignore}). The success ({@code Ok}) and error ({@code Err}) variants are identified
     * positionally (first and second), matching the propagation operator (docs/LANGUAGE_SPEC.md
     * error-handling operations).
     */
    private static boolean isResultOperationName(String name) {
        return switch (name) {
            case "isOk", "isErr", "unwrap", "unwrapErr", "expect", "ignore" -> true;
            default -> false;
        };
    }

    /**
     * Types a {@code Result} operation call. {@code isOk}/{@code isErr}/{@code ignore} take no
     * arguments; {@code unwrap}/{@code unwrapErr} take none and yield the success ({@code T}) or error
     * ({@code E}) payload; {@code expect} takes one {@code String} message and yields {@code T}. The
     * success/error payloads come from the receiver's two type arguments.
     */
    private Type checkResultMethodCall(CallExprNode call, MemberAccessExprNode member, ParameterizedType resultType) {
        Type successType = resultType.arguments().get(0);
        Type errorType = resultType.arguments().get(1);
        switch (member.memberName()) {
            case "isOk":
            case "isErr": {
                checkArguments(call, member.memberName(), List.of());
                return BooleanType.INSTANCE;
            }
            case "unwrap": {
                checkArguments(call, member.memberName(), List.of());
                return successType;
            }
            case "unwrapErr": {
                checkArguments(call, member.memberName(), List.of());
                return errorType;
            }
            case "expect": {
                checkArguments(call, member.memberName(), List.of(StringType.INSTANCE));
                return successType;
            }
            case "ignore": {
                checkArguments(call, member.memberName(), List.of());
                return UnitType.INSTANCE;
            }
            default: {
                error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, member.span(), "Result has no member '" + member.memberName() + "'");
                return null;
            }
        }
    }

    /**
     * Resolves {@code super.property}; inherited properties are already initialized by the superclass.
     */
    private Type checkSuperMemberAccess(MemberAccessExprNode expression) {
        ClassSymbol superClass = requireSuperclass(expression.span());
        if (superClass == null) {
            return null;
        }
        Optional<PropertySymbol> property = superClass.property(expression.memberName());
        if (property.isPresent()) {
            propertyAccesses.put(expression, property.get());
            return property.get().type().substitute(composeSubstitutions(superClass.propertySubstitution(property.get()), superTypeSubstitution()));
        }
        if (superClass.method(expression.memberName()).isPresent()) {
            error(DiagnosticCode.TYPE_FUNCTION_AS_VALUE, expression.span(), "method '" + expression.memberName() + "' cannot be used as a value");
        } else {
            error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, expression.span(), "class " + superClass.name() + " has no member '" + expression.memberName() + "'");
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------
    // Exhaustive match (docs/LANGUAGE_SPEC.md section 12)
    // ---------------------------------------------------------------------------------------------

    /** Tracks the variants and sealed subtypes a match's earlier branches already cover. */
    private static final class MatchCoverage {
        private final Set<EnumVariantSymbol> exhaustedVariants = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<ClassSymbol> sealedSubtypes = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<String> seenPatterns = new HashSet<>();
        private boolean catchAll;
    }

    /**
     * Types an anonymous function expression (docs/LANGUAGE_SPEC.md section 6, "Anonymous functions")
     * as the function value it produces. Its parameter and return types resolve in the enclosing scope
     * — they are written with concrete types, so nothing about the body's own scope reaches them — and
     * its body is then checked as its own function boundary, so {@code break} and {@code continue}
     * cannot cross into or out of it and a {@code return} returns from it. The result type of the
     * expression is the function type the value has, which is what an assignment or a call site matches
     * against.
     *
     * <p>The capture list is resolved in the enclosing scope <em>before</em> the body is checked, because a
     * capture item names a binding of the enclosing function and so must resolve where the expression is
     * written and not where its body is. {@link #resolveCaptures} produces both the values to bind in the
     * body and the rejected {@code var} names the body has to report, from that one resolution pass.
     */
    private Type checkAnonymousFunction(AnonymousFunctionExprNode expression) {
        Map<String, TypeParameterType> previousScope = typeParameterScope;
        typeParameterScope = Map.of();
        List<VariableSymbol> parameters = buildParameters(expression.parameters());
        Type returnType = resolveType(expression.returnType());
        Captures captures = resolveCaptures(expression);
        typeParameterScope = previousScope;
        boolean returnTypeKnown = returnType != null;
        FunctionSymbol function = FunctionSymbol.anonymous("<anonymous>", expression.span(), parameters, //
                        returnType != null ? returnType : AnyType.INSTANCE, returnTypeKnown, expression.body(), captures.bound(), captures.rejected());
        checkCallable(function, expression.body(), null, false, true);
        anonymousFunctions.put(expression, function);
        return function.functionType();
    }

    /**
     * Resolves one anonymous function's capture items against the closure-creation site (docs/LANGUAGE_SPEC.md
     * section 6, "Explicit immutable closure capture") in a single pass, producing both the values to bind
     * in the body — in source order, which the specification uses as environment order — and the names it
     * rejected as {@code var}s.
     *
     * <p>One pass over one resolution is the point: the rejected names have to reach the body so it can
     * report the mutable-capture code for each use, and a second lookup could disagree with the first
     * about what an item named. Both halves of the result come from the same decision about the same item.
     *
     * <p>Items resolve through the ordinary lexical chain, which is what makes capture transitivity
     * structural rather than a rule to remember: the chain stops at the enclosing boundary, so the only
     * outer name an inner item can find is one the enclosing closure itself holds — either its own local or
     * a capture binding it declared. "In nested closures, a name used in an inner capture list counts as a
     * use by the enclosing closure, so every intervening closure must list and forward that value
     * explicitly" then follows from no name above the enclosing boundary being reachable at all.
     *
     * <p>An item that resolves to an immutable local or parameter is recorded directly, with the enclosing
     * binding as its source: lowering gives it a slot of its own in the closure's frame, so the value is
     * copied at creation and a later reassignment of the enclosing binding is invisible through the
     * closure. An item that names a {@code var} is reported and its name recorded, never bound — binding a
     * mirror of it would be the silent conversion the specification forbids. An item naming {@code this} is
     * recorded with no source binding and becomes the receiver of the created value.
     */
    private Captures resolveCaptures(AnonymousFunctionExprNode expression) {
        List<CapturedValue> bound = new ArrayList<>();
        Set<String> rejected = new LinkedHashSet<>();
        for (CaptureItem item : expression.captures()) {
            if (item.isThis()) {
                CapturedValue receiver = resolveReceiverCapture(item);
                if (receiver != null) {
                    bound.add(receiver);
                }
                continue;
            }
            Symbol resolved = symbols.resolveLocalChain(item.name()).orElse(null);
            if (resolved == null) {
                if (declaringBindingNamed(item.name())) {
                    // Naming the binding this very expression initializes is the one case where a capture
                    // item names a real binding that resolution cannot see, because that binding is not
                    // declared until the initializer this expression belongs to has produced a value. The
                    // specification calls it read-before-initialization rather than invalid or unknown,
                    // which is honest about the cause: the value does not exist yet.
                    error(DiagnosticCode.TYPE_UNINITIALIZED_VARIABLE, item.span(), "capture item '" + item.name() + "' names the variable being initialized");
                    continue;
                }
                // "An unknown name in a capture list remains SOLV-RESOL-001": the item position earns no
                // different code, so a typo in a capture list reads like a typo anywhere else.
                error(DiagnosticCode.RESOL_UNKNOWN_NAME, item.span(), "unknown name '" + item.name() + "' in capture list");
                continue;
            }
            // Unreachable under the current grammar, and allow-listed as such by
            // SolvikDiagnosticCodeCoverageTest: `resolveLocalChain` walks only the scopes below the root
            // scope, and the only symbols declared into those scopes are VariableSymbols, so a capture
            // item naming a function, class, enum, or interface resolves to nothing and is reported above
            // as SOLV-RESOL-001. Kept live deliberately: a future revision that declares a non-variable
            // symbol into a function scope makes this the correct diagnostic, and the alternative is to
            // bind the item to a symbol that capture is defined to reject. The allow-list entry in
            // SolvikDiagnosticCodeCoverageTest carries the full reachability analysis.
            if (!(resolved instanceof VariableSymbol binding)) {
                error(DiagnosticCode.SEM_INVALID_CAPTURE, item.span(), "'" + item.name() + "' names a " + symbolKind(resolved) + ", which capture cannot bind");
                continue;
            }
            if (binding.isMutable()) {
                error(DiagnosticCode.SEM_MUTABLE_CAPTURE, item.span(), "capture item '" + item.name() + "' names a mutable 'var' binding, which capture cannot bind");
                rejected.add(item.name());
                continue;
            }
            bound.add(CapturedValue.ofBinding(item, binding));
        }
        return new Captures(List.copyOf(bound), Set.copyOf(rejected));
    }

    /**
     * What one capture list resolved to: the values its accepted items bind in the body, and the names its
     * rejected {@code var} items leave unbound so the body can still report them.
     */
    private record Captures(List<CapturedValue> bound, Set<String> rejected) {

        Captures {
            bound = List.copyOf(bound);
            rejected = Set.copyOf(rejected);
        }
    }

    /**
     * Resolves a written {@code this} capture item. "a closure body may use {@code this} only when
     * {@code [this]} is written", and the item itself needs an enclosing instance receiver to name:
     * {@code this} where none exists "remains {@code SOLV-RESOL-005}", which is the same decision
     * {@link #checkThis} makes for a body that writes {@code this} without capturing it.
     *
     * <p>An item written inside another closure forwards that closure's captured receiver rather than
     * finding a receiver of its own, because a closure body has no receiver and the enclosing closure is
     * the only thing that could supply one. The forwarding item is still required: it is the written use
     * that obliges the intervening closure to carry the value.
     */
    private CapturedValue resolveReceiverCapture(CaptureItem item) {
        if (currentBoundaryReceiver != null) {
            return CapturedValue.ofReceiver(currentBoundaryReceiver.type(), item.span());
        }
        Type receiverType = enclosingReceiverType();
        if (receiverType == null) {
            error(DiagnosticCode.RESOL_THIS_OUTSIDE_CLASS, item.span(), "'this' is only valid inside an instance method or constructor");
            return null;
        }
        return CapturedValue.ofReceiver(receiverType, item.span());
    }

    /**
     * The static type of the receiver an enclosing callable would offer a {@code [this]} capture, or
     * {@code null} when no enclosing instance callable exists. Derived from the same fields
     * {@link #checkThis} consults, so the two cannot disagree about whether a receiver is in scope; a
     * static member records no receiver, because it runs with none.
     */
    private Type enclosingReceiverType() {
        if (checkingStaticMember) {
            return null;
        }
        if (currentClass != null) {
            return currentClass.type();
        }
        if (currentInterface != null) {
            return currentInterface.type();
        }
        return null;
    }

    /** What kind of declaration a captured name resolved to, for a diagnostic that must say why it is not capturable. */
    private static String symbolKind(Symbol symbol) {
        if (symbol instanceof FunctionSymbol) {
            return "function declaration";
        }
        if (symbol instanceof ClassSymbol) {
            return "class";
        }
        if (symbol instanceof InterfaceSymbol) {
            return "interface";
        }
        if (symbol instanceof EnumSymbol) {
            return "enum";
        }
        return "declaration";
    }

    /**
     * Types a {@code match} expression: checks every branch pattern against the scrutinee type,
     * verifies exhaustiveness for a known closed variant set, and computes the nearest common
     * declared supertype of the branch results. A pattern form incompatible with the scrutinee and
     * a non-exhaustive match are compile-time errors, so a malformed match never lowers.
     */
    private Type checkMatch(MatchExprNode expression) {
        Type scrutineeType = checkExpression(expression.scrutinee());
        Type matchedType = scrutineeType == null ? null : scrutineeType.nonNullType();
        boolean nullable = scrutineeType instanceof NullableType || scrutineeType == NullType.INSTANCE;
        MatchCoverage coverage = new MatchCoverage();
        List<Type> resultTypes = new ArrayList<>();
        for (MatchBranchNode branch : expression.branches()) {
            symbols.enterScope();
            checkPattern(branch.pattern(), matchedType);
            String signature = patternSignature(branch.pattern());
            if (coverage.catchAll || coverage.seenPatterns.contains(signature) || isPatternCovered(branch.pattern(), coverage, matchedType)) {
                error(DiagnosticCode.SEM_MATCH_UNREACHABLE_PATTERN, branch.pattern().span(), "this match branch is unreachable because an earlier branch already covers it");
            } else {
                coverage.seenPatterns.add(signature);
                markPatternCovered(branch.pattern(), coverage, matchedType);
            }
            Type resultType = checkExpression(branch.result());
            if (resultType != null) {
                resultTypes.add(resultType);
            }
            symbols.exitScope();
        }
        reportMatchExhaustiveness(expression, matchedType, nullable);
        Type resultType = nearestCommonSupertype(resultTypes);
        if (resultType == null && !resultTypes.isEmpty()) {
            error(DiagnosticCode.TYPE_MATCH_RESULT, expression.span(), "match branch results have no nearest common declared supertype");
        }
        return resultType;
    }

    // ---------------------------------------------------------------------------------------------
    // Value-producing block, if, and switch expressions (docs/LANGUAGE_SPEC.md section 21)
    // ---------------------------------------------------------------------------------------------

    /**
     * Types a brace-delimited block expression. Its statements and tail are checked in one lexical
     * scope, and every normally completing path must reach a tail result; an empty block or one
     * that ends in a declaration or assignment is rejected. The block's type is the shared join of
     * its normally completing tail results, or {@code Nothing} when no path completes normally.
     */
    private Type checkBlockExpr(BlockExprNode expression) {
        checkBlock(expression.body());
        Flow flow = flowOfValueBlock(expression.body());
        if (flow.normalWithoutValue) {
            error(DiagnosticCode.SEM_BLOCK_RESULT_REQUIRED, expression.body().span(), "a block used as a value must end in a tail expression");
        }
        return branchResultType(flow, expression.span());
    }

    /**
     * Types an {@code if} expression. The condition must be {@code Boolean}, the expression must
     * have an {@code else} path, and each normally completing branch must produce a tail result.
     * Abrupt branches are excluded from the result join, and flow narrowing and definite
     * initialization are computed over the branches exactly as for the statement form.
     */
    private Type checkIfExpr(IfExprNode expression) {
        Type condition = checkExpression(expression.condition());
        requireBoolean(condition, expression.condition());
        Refinement refinement = refinementOf(expression.condition());
        Set<PropertySymbol> before = copyInitialized();
        Map<VariableSymbol, Type> narrowingBefore = copyNarrowing();
        if (refinement != null) {
            applyRefinement(refinement, refinement.whenTrue);
        }
        checkBlock(expression.thenBlock());
        Set<PropertySymbol> afterThen = copyInitialized();
        Map<VariableSymbol, Type> narrowingAfterThen = copyNarrowing();
        restoreNarrowing(narrowingBefore);
        definitelyInitialized = before;
        boolean hasElse = expression.elseValue().isPresent();
        if (hasElse) {
            if (refinement != null) {
                applyRefinement(refinement, refinement.whenFalse);
            }
            checkExpression(expression.elseValue().get());
        }
        Set<PropertySymbol> afterElse = copyInitialized();
        Map<VariableSymbol, Type> narrowingAfterElse = copyNarrowing();
        narrowedTypes = intersectNarrowing(narrowingAfterThen, narrowingAfterElse);
        definitelyInitialized = intersection(afterThen, afterElse);
        if (!hasElse) {
            error(DiagnosticCode.SEM_IF_EXPRESSION_MISSING_ELSE, expression.span(), "an if used as an expression must have an else branch");
        }
        Flow flow = new Flow();
        mergeFlow(flow, flowOfValueBlock(expression.thenBlock()));
        if (hasElse) {
            mergeFlow(flow, flowOfExpression(expression.elseValue().get()));
        } else {
            flow.normalWithoutValue = true;
        }
        return branchResultType(flow, expression.span());
    }

    /**
     * Types a {@code switch} expression. The shared scrutinee and label checks run first; unlike the
     * statement form an expression {@code switch} must contain exactly one last {@code default} so
     * value production is explicit. Every normally completing case body, including {@code default},
     * must produce a tail result, and abrupt cases are excluded from the result join.
     */
    private Type checkSwitchExpr(SwitchExprNode expression) {
        checkSwitchCases(expression.scrutinee(), expression.cases());
        boolean hasDefault = false;
        for (SwitchCaseNode switchCase : expression.cases()) {
            if (switchCase.isDefault()) {
                hasDefault = true;
            }
        }
        if (!hasDefault) {
            error(DiagnosticCode.SEM_SWITCH_EXPRESSION_MISSING_DEFAULT, expression.span(), "a switch used as an expression must have a default case");
        }
        Flow flow = new Flow();
        for (SwitchCaseNode switchCase : expression.cases()) {
            Flow caseFlow = flowOfValueBlock(switchCase.body());
            if (caseFlow.normalWithoutValue) {
                error(DiagnosticCode.SEM_BLOCK_RESULT_REQUIRED, switchCase.body().span(), "a switch case used as a value must end in a tail expression");
            }
            mergeFlow(flow, caseFlow);
        }
        if (!hasDefault) {
            flow.normalWithoutValue = true;
        }
        return branchResultType(flow, expression.span());
    }

    /**
     * The shared result type of a value-producing construct: the nearest common declared supertype
     * of its normally completing branch results, or {@code Nothing} when no branch completes
     * normally. A set with no single nearest supertype is {@link DiagnosticCode#TYPE_BRANCH_RESULT}.
     */
    private Type branchResultType(Flow flow, SourceSpan span) {
        if (flow.normalValues.isEmpty()) {
            return NothingType.INSTANCE;
        }
        Type joined = nearestCommonSupertype(flow.normalValues);
        if (joined == null) {
            error(DiagnosticCode.TYPE_BRANCH_RESULT, span, "normally completing branches have no nearest common declared supertype: " + distinctTypeNames(flow.normalValues));
            return NothingType.INSTANCE;
        }
        return joined;
    }

    /** A comma-separated list of the distinct type names a diagnostic reported about. */
    private static String distinctTypeNames(List<Type> types) {
        List<String> names = new ArrayList<>();
        for (Type type : types) {
            if (!names.contains(type.name())) {
                names.add(type.name());
            }
        }
        return String.join(", ", names);
    }

    /**
     * The compile-time completion of a statement or value-required body. It distinguishes a normal
     * completion that carries a result from one that does not, and it records whether
     * {@code return}, {@code break}, or {@code continue} can carry control out of the construct, so
     * abrupt paths are represented by control flow rather than by a fabricated value.
     */
    private static final class Flow {
        final List<Type> normalValues = new ArrayList<>();
        boolean normalWithoutValue;
        boolean returns;
        boolean breaks;
        boolean continues;

        boolean canCompleteNormally() {
            return normalWithoutValue || !normalValues.isEmpty();
        }

        static Flow normalNoValue() {
            Flow flow = new Flow();
            flow.normalWithoutValue = true;
            return flow;
        }
    }

    private static void mergeFlow(Flow target, Flow source) {
        target.normalValues.addAll(source.normalValues);
        target.normalWithoutValue |= source.normalWithoutValue;
        target.returns |= source.returns;
        target.breaks |= source.breaks;
        target.continues |= source.continues;
    }

    /** The completion of a statement, used to combine branches and sequences. */
    private Flow completionOf(StatementNode statement) {
        switch (statement.kind()) {
            case RETURN_STMT: {
                Flow flow = new Flow();
                flow.returns = true;
                return flow;
            }
            case BREAK_STMT: {
                Flow flow = new Flow();
                flow.breaks = true;
                return flow;
            }
            case CONTINUE_STMT: {
                Flow flow = new Flow();
                flow.continues = true;
                return flow;
            }
            case BLOCK:
                return flowOfStatementSequence(((BlockNode) statement).statements());
            case IF_STMT: {
                IfStmtNode ifStatement = (IfStmtNode) statement;
                Flow flow = new Flow();
                mergeFlow(flow, completionOf(ifStatement.thenBlock()));
                if (ifStatement.elseBranch().isPresent()) {
                    mergeFlow(flow, completionOfElse(ifStatement.elseBranch().get()));
                } else {
                    flow.normalWithoutValue = true;
                }
                return flow;
            }
            case SWITCH_STMT: {
                SwitchStmtNode switchStatement = (SwitchStmtNode) statement;
                Flow flow = new Flow();
                boolean hasDefault = false;
                for (SwitchCaseNode switchCase : switchStatement.cases()) {
                    if (switchCase.isDefault()) {
                        hasDefault = true;
                    }
                    mergeFlow(flow, flowOfStatementSequence(switchCase.body().statements()));
                }
                if (!hasDefault) {
                    flow.normalWithoutValue = true;
                }
                return flow;
            }
            case WHILE_STMT:
                return loopCompletion(((WhileStmtNode) statement).body());
            case FOR_STMT:
                return loopCompletion(((ForStmtNode) statement).body());
            case FOR_IN_STMT:
                return loopCompletion(((ForInStmtNode) statement).body());
            default:
                return Flow.normalNoValue();
        }
    }

    private Flow completionOfElse(ElseBranchNode branch) {
        if (branch.isChainedIf()) {
            return completionOf(branch.chainedIf().get());
        }
        return completionOf(branch.block().get());
    }

    /** A loop always completes normally and contains any {@code break}/{@code continue} it sees. */
    private Flow loopCompletion(BlockNode body) {
        Flow bodyFlow = flowOfStatementSequence(body.statements());
        Flow flow = Flow.normalNoValue();
        flow.returns = bodyFlow.returns;
        return flow;
    }

    /**
     * Combines a sequence of statements: only the last reachable statement decides whether the
     * sequence can fall through, while abrupt outcomes accumulate from every reachable statement.
     */
    private Flow flowOfStatementSequence(List<StatementNode> statements) {
        Flow result = Flow.normalNoValue();
        for (StatementNode statement : statements) {
            if (!result.canCompleteNormally()) {
                break;
            }
            Flow flow = completionOf(statement);
            result.normalValues.clear();
            result.normalWithoutValue = flow.canCompleteNormally();
            result.returns |= flow.returns;
            result.breaks |= flow.breaks;
            result.continues |= flow.continues;
        }
        return result;
    }

    /** The completion of a value-required block: its statements followed by its optional tail. */
    private Flow flowOfValueBlock(BlockNode block) {
        Flow prefix = flowOfStatementSequence(block.statements());
        Flow result = new Flow();
        if (block.tail().isPresent()) {
            if (prefix.canCompleteNormally()) {
                Flow tailFlow = flowOfExpression(block.tail().get());
                result.returns = prefix.returns | tailFlow.returns;
                result.breaks = prefix.breaks | tailFlow.breaks;
                result.continues = prefix.continues | tailFlow.continues;
                result.normalValues.addAll(tailFlow.normalValues);
                result.normalWithoutValue = tailFlow.normalWithoutValue;
            } else {
                result.returns = prefix.returns;
                result.breaks = prefix.breaks;
                result.continues = prefix.continues;
            }
        } else {
            result.returns = prefix.returns;
            result.breaks = prefix.breaks;
            result.continues = prefix.continues;
            result.normalWithoutValue = prefix.canCompleteNormally();
        }
        return result;
    }

    /** The completion of an expression inside a value-required construct. */
    private Flow flowOfExpression(ExpressionNode expression) {
        if (expression instanceof BlockExprNode blockExpr) {
            return flowOfValueBlock(blockExpr.body());
        }
        if (expression instanceof IfExprNode ifExpr) {
            Flow flow = new Flow();
            mergeFlow(flow, flowOfValueBlock(ifExpr.thenBlock()));
            if (ifExpr.elseValue().isPresent()) {
                mergeFlow(flow, flowOfExpression(ifExpr.elseValue().get()));
            } else {
                flow.normalWithoutValue = true;
            }
            return flow;
        }
        if (expression instanceof SwitchExprNode switchExpr) {
            Flow flow = new Flow();
            boolean hasDefault = false;
            for (SwitchCaseNode switchCase : switchExpr.cases()) {
                if (switchCase.isDefault()) {
                    hasDefault = true;
                }
                mergeFlow(flow, flowOfValueBlock(switchCase.body()));
            }
            if (!hasDefault) {
                flow.normalWithoutValue = true;
            }
            return flow;
        }
        Flow flow = new Flow();
        Type type = expressionTypes.get(expression);
        if (type != null) {
            flow.normalValues.add(type);
        } else {
            flow.normalWithoutValue = true;
        }
        return flow;
    }

    /** The class symbol behind a type when it names a sealed class, or {@code null}. */
    private ClassSymbol sealedClassSymbol(Type type) {
        ClassSymbol symbol = classSymbolFor(type);
        return symbol != null && symbol.isSealed() ? symbol : null;
    }

    /** The concrete (constructible) subtypes of a sealed class, in declaration order. */
    private static List<ClassSymbol> concreteSubtypes(ClassSymbol sealed) {
        List<ClassSymbol> concrete = new ArrayList<>();
        for (ClassSymbol subtype : sealed.allSubtypes()) {
            if (!subtype.isSealed()) {
                concrete.add(subtype);
            }
        }
        return concrete;
    }

    /** Checks one pattern against the type of the value it matches. */
    private void checkPattern(PatternNode pattern, Type valueType) {
        if (pattern instanceof WildcardPatternNode) {
            return;
        }
        if (pattern instanceof BindingPatternNode binding) {
            checkBindingPattern(binding, valueType);
            return;
        }
        if (pattern instanceof EnumPatternNode enumPattern) {
            checkEnumPattern(enumPattern, valueType);
            return;
        }
        throw new IllegalStateException("unknown pattern kind: " + pattern.kind());
    }

    /**
     * Checks a {@code name: Type} or bare variant binding: the written subtype must be a non-null,
     * non-erased subtype of the matched value type, and the name must be free in the branch scope.
     */
    private void checkBindingPattern(BindingPatternNode pattern, Type valueType) {
        Type declared = null;
        if (pattern.typeRef().isPresent()) {
            Type resolved = resolveType(pattern.typeRef().get());
            if (resolved != null) {
                if (resolved instanceof NullableType || resolved == NullType.INSTANCE) {
                    error(DiagnosticCode.TYPE_INVALID_TYPE_OPERAND, pattern.typeRef().get().span(), "a binding pattern must name a non-null type");
                } else if (resolved instanceof ParameterizedType) {
                    errorExpected(DiagnosticCode.TYPE_ERASED_TYPE_TEST, pattern.typeRef().get().span(), //
                                    "a runtime binding pattern cannot inspect erased type arguments", "a non-generic type", resolved.name());
                } else if (valueType != null && !resolved.isAssignableTo(valueType)) {
                    errorExpected(DiagnosticCode.TYPE_MATCH_PATTERN, pattern.span(), //
                                    "binding pattern type is not a subtype of the matched type", valueType.name(), resolved.name());
                } else {
                    declared = resolved;
                }
            }
        }
        Type bindingType = declared != null ? declared : (valueType != null ? valueType : AnyType.INSTANCE);
        VariableSymbol variable = new VariableSymbol(pattern.name(), pattern.span(), bindingType, false, false);
        variable.markInitialized();
        if (!symbols.declare(variable)) {
            error(DiagnosticCode.RESOL_DUPLICATE_NAME, pattern.span(), "name '" + pattern.name() + "' is already declared in this branch");
        }
        patternBindings.put(pattern, variable);
        if (declared != null) {
            patternBindingTypes.put(pattern, declared);
        }
    }

    /**
     * Checks an enum variant pattern: the matched value must be of the enum type, the variant must
     * be one of its known variants, and the sub-pattern count must match the variant's value count.
     * Each sub-pattern is checked against the variant's substituted value type.
     */
    private void checkEnumPattern(EnumPatternNode pattern, Type valueType) {
        EnumSymbol enumSymbol = valueType == null ? null : enumSymbolFor(valueType);
        if (enumSymbol == null) {
            errorExpected(DiagnosticCode.TYPE_MATCH_PATTERN, pattern.span(), //
                            "an enum variant pattern requires an enum-typed value", "an enum type", valueType == null ? "an unresolved type" : valueType.name());
            for (PatternNode argument : pattern.arguments()) {
                checkPattern(argument, null);
            }
            return;
        }
        Optional<EnumVariantSymbol> resolved = enumSymbol.variant(pattern.variantName());
        if (resolved.isEmpty()) {
            error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, pattern.span(), "enum " + enumSymbol.name() + " has no variant '" + pattern.variantName() + "'");
            for (PatternNode argument : pattern.arguments()) {
                checkPattern(argument, null);
            }
            return;
        }
        EnumVariantSymbol variant = resolved.get();
        if (variant.valueTypes().size() != pattern.arguments().size()) {
            errorExpected(DiagnosticCode.TYPE_ARITY_MISMATCH, pattern.span(), //
                            "variant '" + pattern.variantName() + "' pattern has the wrong number of sub-patterns", //
                            Integer.toString(variant.valueTypes().size()), Integer.toString(pattern.arguments().size()));
            return;
        }
        List<Type> valueTypes = substitutedTypes(variant.valueTypes(), substitutionFor(valueType));
        for (int i = 0; i < pattern.arguments().size(); i++) {
            checkPattern(pattern.arguments().get(i), valueTypes.get(i));
        }
        enumPatterns.put(pattern, variant);
    }

    /** Whether an earlier branch of the match already covers every value this pattern matches. */
    private boolean isPatternCovered(PatternNode pattern, MatchCoverage coverage, Type matchedType) {
        if (pattern instanceof WildcardPatternNode) {
            return false;
        }
        if (pattern instanceof BindingPatternNode binding) {
            Type declared = patternBindingTypes.get(binding);
            if (declared == null) {
                // A bare binding matches every value, so it is a duplicate only after a catch-all.
                return false;
            }
            ClassSymbol sealed = sealedClassSymbol(matchedType);
            if (sealed == null) {
                return false;
            }
            for (ClassSymbol subtype : concreteSubtypes(sealed)) {
                if (subtype.type().isAssignableTo(declared) && !coverage.sealedSubtypes.contains(subtype)) {
                    return false;
                }
            }
            return true;
        }
        EnumPatternNode enumPattern = (EnumPatternNode) pattern;
        EnumVariantSymbol variant = enumPatterns.get(enumPattern);
        return variant != null && coverage.exhaustedVariants.contains(variant);
    }

    /** Records the variants or subtypes a pattern covers so a later duplicate can be detected. */
    private void markPatternCovered(PatternNode pattern, MatchCoverage coverage, Type matchedType) {
        if (pattern instanceof WildcardPatternNode) {
            coverage.catchAll = true;
            return;
        }
        if (pattern instanceof BindingPatternNode binding) {
            Type declared = patternBindingTypes.get(binding);
            if (declared == null) {
                coverage.catchAll = true;
                return;
            }
            ClassSymbol sealed = sealedClassSymbol(matchedType);
            if (sealed == null) {
                // A typed binding covers only the non-null values it names, so it never makes a
                // later wildcard unreachable; duplicates are caught by the signature check instead.
                return;
            }
            for (ClassSymbol subtype : concreteSubtypes(sealed)) {
                if (subtype.type().isAssignableTo(declared)) {
                    coverage.sealedSubtypes.add(subtype);
                }
            }
            return;
        }
        EnumPatternNode enumPattern = (EnumPatternNode) pattern;
        EnumVariantSymbol variant = enumPatterns.get(enumPattern);
        if (variant != null && irrefutableArguments(enumPattern)) {
            // Only a variant pattern whose sub-patterns match every value of the variant exhausts that
            // variant; a nested refutable pattern like Wrap(Some(x)) covers only part of it.
            coverage.exhaustedVariants.add(variant);
        }
    }

    /** Whether every sub-pattern of a variant pattern is irrefutable. */
    private static boolean irrefutableArguments(EnumPatternNode pattern) {
        for (PatternNode argument : pattern.arguments()) {
            if (argument instanceof EnumPatternNode) {
                return false;
            }
        }
        return true;
    }

    /**
     * A canonical, name-independent signature of a pattern used to detect exact duplicate branches.
     * Binding names are ignored because two bindings of the same subtype cover the same values.
     */
    private static String patternSignature(PatternNode pattern) {
        if (pattern instanceof WildcardPatternNode) {
            return "_";
        }
        if (pattern instanceof BindingPatternNode binding) {
            return binding.typeRef().map(type -> ":" + typeRefSignature(type)).orElse("_");
        }
        EnumPatternNode enumPattern = (EnumPatternNode) pattern;
        StringBuilder signature = new StringBuilder(enumPattern.variantName()).append('(');
        for (int i = 0; i < enumPattern.arguments().size(); i++) {
            if (i > 0) {
                signature.append(',');
            }
            signature.append(patternSignature(enumPattern.arguments().get(i)));
        }
        return signature.append(')').toString();
    }

    /** A canonical signature of a written type reference, including its nullable marker. */
    private static String typeRefSignature(TypeRef reference) {
        if (reference instanceof FunctionTypeRefNode function) {
            StringBuilder parameters = new StringBuilder("func(");
            for (int i = 0; i < function.parameterRefs().size(); i++) {
                if (i > 0) {
                    parameters.append(',');
                }
                parameters.append(typeRefSignature(function.parameterRefs().get(i)));
            }
            parameters.append("): ").append(typeRefSignature(function.returnTypeRef()));
            return function.isNullable() ? "(" + parameters + ")?" : parameters.toString();
        }
        TypeRefNode nominal = (TypeRefNode) reference;
        StringBuilder signature = new StringBuilder(nominal.name());
        if (!nominal.arguments().isEmpty()) {
            signature.append('(');
            for (int i = 0; i < nominal.arguments().size(); i++) {
                if (i > 0) {
                    signature.append(',');
                }
                signature.append(typeRefSignature(nominal.arguments().get(i)));
            }
            signature.append(')');
        }
        return nominal.isNullable() ? signature.append('?').toString() : signature.toString();
    }

    /**
     * Reports a match that leaves a known variant, sealed subtype, or null case uncovered. The
     * check is recursive so nested variant patterns such as {@code Wrap(Some(x))} contribute to the
     * coverage of their outer variant instead of being treated as opaque.
     */
    private void reportMatchExhaustiveness(MatchExprNode expression, Type matchedType, boolean nullable) {
        List<PatternNode> patterns = new ArrayList<>();
        for (MatchBranchNode branch : expression.branches()) {
            patterns.add(branch.pattern());
        }
        if (patternsCover(patterns, matchedType, nullable)) {
            return;
        }
        List<String> missing = new ArrayList<>();
        boolean valueCatchAll = matchedType != null && hasValueCatchAll(patterns, matchedType);
        if (matchedType == null) {
            missing.add("a wildcard pattern");
        } else if (enumSymbolFor(matchedType) != null) {
            if (!valueCatchAll) {
                EnumSymbol enumSymbol = enumSymbolFor(matchedType);
                for (EnumVariantSymbol variant : enumSymbol.variants()) {
                    if (!variantCovered(patterns, matchedType, variant)) {
                        missing.add(variant.name());
                    }
                }
            }
        } else if (sealedClassSymbol(matchedType) != null) {
            if (!valueCatchAll) {
                for (ClassSymbol subtype : concreteSubtypes(sealedClassSymbol(matchedType))) {
                    if (!subtypeCovered(patterns, subtype)) {
                        missing.add(subtype.name());
                    }
                }
            }
        } else if (!valueCatchAll) {
            missing.add("a wildcard pattern");
        }
        if (nullable) {
            missing.add("null");
        }
        error(DiagnosticCode.SEM_MATCH_NOT_EXHAUSTIVE, expression.span(), "match is not exhaustive; missing " + String.join(", ", missing));
    }

    /**
     * Whether a set of patterns covers every value of {@code matchedType}, and {@code null} when the
     * type is nullable. A catch-all covers everything; otherwise a closed enum needs every variant
     * covered and a sealed class needs every concrete subtype covered.
     */
    private boolean patternsCover(List<PatternNode> patterns, Type matchedType, boolean nullable) {
        if (hasNullCatchAll(patterns)) {
            return true;
        }
        // A typed binding covers only non-null values, so a nullable scrutinee still needs a wildcard
        // or a bare binding even when every non-null value is covered.
        if (nullable || matchedType == null) {
            return false;
        }
        if (hasValueCatchAll(patterns, matchedType)) {
            return true;
        }
        EnumSymbol enumSymbol = enumSymbolFor(matchedType);
        if (enumSymbol != null) {
            for (EnumVariantSymbol variant : enumSymbol.variants()) {
                if (!variantCovered(patterns, matchedType, variant)) {
                    return false;
                }
            }
            return true;
        }
        ClassSymbol sealed = sealedClassSymbol(matchedType);
        if (sealed != null) {
            for (ClassSymbol subtype : concreteSubtypes(sealed)) {
                if (!subtypeCovered(patterns, subtype)) {
                    return false;
                }
            }
            return true;
        }
        // A non-closed type has no known variant set, so only a catch-all makes a match exhaustive.
        return false;
    }

    /** Whether any pattern matches every value including {@code null}: a wildcard or bare binding. */
    private boolean hasNullCatchAll(List<PatternNode> patterns) {
        for (PatternNode pattern : patterns) {
            if (pattern instanceof WildcardPatternNode) {
                return true;
            }
            if (pattern instanceof BindingPatternNode binding && patternBindingTypes.get(binding) == null) {
                return true;
            }
        }
        return false;
    }

    /** Whether any pattern matches every non-null value of the matched type. */
    private boolean hasValueCatchAll(List<PatternNode> patterns, Type matchedType) {
        if (hasNullCatchAll(patterns)) {
            return true;
        }
        for (PatternNode pattern : patterns) {
            if (pattern instanceof BindingPatternNode binding) {
                Type declared = patternBindingTypes.get(binding);
                if (declared != null && matchedType.isAssignableTo(declared)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether the patterns cover every value of one enum variant, recursing into sub-patterns. */
    private boolean variantCovered(List<PatternNode> patterns, Type matchedType, EnumVariantSymbol variant) {
        List<EnumPatternNode> matching = new ArrayList<>();
        for (PatternNode pattern : patterns) {
            if (pattern instanceof EnumPatternNode enumPattern && enumPatterns.get(enumPattern) == variant) {
                matching.add(enumPattern);
            }
        }
        if (matching.isEmpty()) {
            return false;
        }
        List<Type> valueTypes = substitutedTypes(variant.valueTypes(), substitutionFor(matchedType));
        for (int i = 0; i < valueTypes.size(); i++) {
            List<PatternNode> argumentPatterns = new ArrayList<>(matching.size());
            for (EnumPatternNode parent : matching) {
                argumentPatterns.add(parent.arguments().get(i));
            }
            if (!patternsCover(argumentPatterns, valueTypes.get(i), false)) {
                return false;
            }
        }
        return true;
    }

    /** Whether a typed binding pattern covers a concrete sealed subtype. */
    private boolean subtypeCovered(List<PatternNode> patterns, ClassSymbol subtype) {
        for (PatternNode pattern : patterns) {
            if (pattern instanceof BindingPatternNode binding) {
                Type declared = patternBindingTypes.get(binding);
                if (declared != null && subtype.type().isAssignableTo(declared)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The nearest common declared supertype of the branch result types, shared with every other
     * value-producing construct through {@link TypeJoin}. Nullability is folded in, and a set with
     * no single nearest supertype is ill-typed.
     */
    private static Type nearestCommonSupertype(List<Type> types) {
        return TypeJoin.nearestCommonSupertype(types);
    }

    private void requireBoolean(Type type, ExpressionNode where) {
        if (type != null && type != BooleanType.INSTANCE) {
            errorExpected(DiagnosticCode.TYPE_CONDITION_NOT_BOOLEAN, where.span(), "condition must have type Boolean", "Boolean", type.name());
        }
    }

    private void invalidOperands(SourceSpan span, String operator, String expected, Type... found) {
        StringBuilder actual = new StringBuilder();
        for (int i = 0; i < found.length; i++) {
            if (i > 0) {
                actual.append(", ");
            }
            actual.append(found[i].name());
        }
        errorExpected(DiagnosticCode.TYPE_INVALID_OPERANDS, span, "operator '" + operator + "' cannot be applied to " + actual, expected, actual.toString());
    }

    // ---------------------------------------------------------------------------------------------
    // Null-safety narrowing
    // ---------------------------------------------------------------------------------------------

    /**
     * A flow-sensitive refinement produced by a null test or type test over one binding: the type the
     * binding has when the condition is true and the type it has when the condition is false
     * (docs/LANGUAGE_SPEC.md section 5).
     */
    private static final class Refinement {
        private final VariableSymbol variable;
        private final Type whenTrue;
        private final Type whenFalse;

        Refinement(VariableSymbol variable, Type whenTrue, Type whenFalse) {
            this.variable = variable;
            this.whenTrue = whenTrue;
            this.whenFalse = whenFalse;
        }
    }

    /** Whether {@code type} admits {@code null}, so a null check can refine it. */
    private static boolean isNullableType(Type type) {
        return type instanceof NullableType || type == NullType.INSTANCE;
    }

    /**
     * Extracts the refinement a condition establishes, or {@code null} when it establishes none.
     * Recognized forms are a null comparison of a variable ({@code x != null}, {@code x == null}), a
     * type test of a variable ({@code x is T}), and the negation of either. The name symbols are
     * populated because the condition has already been type-checked before this runs.
     */
    private Refinement refinementOf(ExpressionNode condition) {
        if (condition instanceof ParenExprNode paren) {
            return refinementOf(paren.inner());
        }
        if (condition instanceof UnaryExprNode unary && unary.operator() == UnaryOperator.NOT) {
            Refinement inner = refinementOf(unary.operand());
            return inner == null ? null : new Refinement(inner.variable, inner.whenFalse, inner.whenTrue);
        }
        if (condition instanceof BinaryExprNode binary && (binary.operator() == BinaryOperator.EQ || binary.operator() == BinaryOperator.NEQ
                || binary.operator() == BinaryOperator.EQEQ || binary.operator() == BinaryOperator.NEQEQ)) {
            NameRefExprNode name = null;
            if (binary.left() instanceof NameRefExprNode leftName && binary.right() instanceof NullLiteralNode) {
                name = leftName;
            } else if (binary.right() instanceof NameRefExprNode rightName && binary.left() instanceof NullLiteralNode) {
                name = rightName;
            }
            if (name == null) {
                return null;
            }
            VariableSymbol variable = narrowableVariable(name);
            if (variable == null || !isNullableType(variable.type())) {
                return null;
            }
            Type nonNull = variable.type().nonNullType();
            boolean equality = binary.operator() == BinaryOperator.EQ || binary.operator() == BinaryOperator.EQEQ;
            return equality ? new Refinement(variable, NullType.INSTANCE, nonNull) : new Refinement(variable, nonNull, NullType.INSTANCE);
        }
        if (condition instanceof TypeTestExprNode test && test.operand() instanceof NameRefExprNode name) {
            VariableSymbol variable = narrowableVariable(name);
            Type target = testedTypes.get(test);
            if (variable == null || target == null) {
                return null;
            }
            // Narrowing may only specialize the declared type; an unrelated test adds no information.
            Type narrowed = target.isAssignableTo(variable.type()) ? target : variable.type();
            return new Refinement(variable, narrowed, variable.type());
        }
        return null;
    }

    /** The variable a name reference resolved to, or {@code null} when it is not a variable. */
    private VariableSymbol narrowableVariable(NameRefExprNode name) {
        Symbol symbol = nameSymbols.get(name);
        return symbol instanceof VariableSymbol variable ? variable : null;
    }

    /** Applies the refinement branch type, or clears a refinement that no longer narrows anything. */
    private void applyRefinement(Refinement refinement, Type type) {
        if (type == refinement.variable.type()) {
            narrowedTypes.remove(refinement.variable);
        } else {
            narrowedTypes.put(refinement.variable, type);
        }
    }

    private Map<VariableSymbol, Type> copyNarrowing() {
        return new IdentityHashMap<>(narrowedTypes);
    }

    private void restoreNarrowing(Map<VariableSymbol, Type> snapshot) {
        narrowedTypes = new IdentityHashMap<>(snapshot);
    }

    /**
     * The refinements on which both branches agree. A refinement present in only one branch is not
     * valid after the join, so it is dropped rather than restored from the incoming state.
     */
    private static Map<VariableSymbol, Type> intersectNarrowing(Map<VariableSymbol, Type> a, Map<VariableSymbol, Type> b) {
        Map<VariableSymbol, Type> result = new IdentityHashMap<>();
        for (Map.Entry<VariableSymbol, Type> entry : a.entrySet()) {
            if (b.get(entry.getKey()) == entry.getValue()) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    /**
     * Drops the refinement of every binding first written inside a loop, because the loop may have
     * executed and the refinement established before it may no longer hold afterwards.
     */
    private void dropWrittenSince(Set<VariableSymbol> before) {
        for (VariableSymbol written : writtenVariables) {
            if (!before.contains(written)) {
                narrowedTypes.remove(written);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Type resolution and control-flow helpers
    // ---------------------------------------------------------------------------------------------

    private Type resolveType(TypeRef reference) {
        if (resolvedTypes.containsKey(reference)) {
            return resolvedTypes.get(reference);
        }
        Type result = reference instanceof FunctionTypeRefNode functionRef //
                ? resolveFunctionTypeReference(functionRef)
                : resolveTypeUncached((TypeRefNode) reference);
        resolvedTypes.put(reference, result);
        return result;
    }

    /**
     * Resolves a written function type reference to the canonical {@link FunctionType}. The result
     * type is synthesized as {@code Unit} when omitted, matching a callable with no declared return.
     * Any component that failed to resolve becomes {@link AnyType} so the recorded error, not a
     * null reference, is what downstream checks observe.
     */
    private Type resolveFunctionTypeReference(FunctionTypeRefNode reference) {
        List<Type> parameterTypes = new ArrayList<>(reference.parameterRefs().size());
        for (TypeRef parameterRef : reference.parameterRefs()) {
            Type parameterType = resolveType(parameterRef);
            parameterTypes.add(parameterType == null ? AnyType.INSTANCE : parameterType);
        }
        Type returnType = resolveType(reference.returnTypeRef());
        Type function = FunctionType.canonical(parameterTypes, returnType == null ? AnyType.INSTANCE : returnType);
        // The grouped `(func(...): R)?` spelling makes the whole function value nullable; the
        // ungrouped `func(...): R?` nulls only the result, which the nested return reference already
        // recorded. isNullable() is set exactly for the grouped form (docs/LANGUAGE_SPEC.md section 11).
        return reference.isNullable() ? function.nullableView() : function;
    }

    private Type resolveTypeUncached(TypeRefNode reference) {
        boolean applied = !reference.arguments().isEmpty();
        TypeParameterType parameter = reference.hasModulePrefix() ? null : typeParameterScope.get(reference.name());
        Type base;
        if (parameter != null) {
            if (forbiddenTypeParameters.contains(parameter)) {
                // A static member cannot mention a type parameter of its class: the member is reached
                // through the class name, where no instantiation of that parameter exists
                // (docs/LANGUAGE_SPEC.md section 7).
                error(DiagnosticCode.SEM_TYPE_PARAMETER_IN_STATIC_MEMBER, reference.span(), //
                                "static member cannot use type parameter '" + parameter.name() + "' of its enclosing class");
            }
            if (applied) {
                errorExpected(DiagnosticCode.TYPE_NOT_GENERIC, reference.span(), //
                                "type parameter '" + reference.name() + "' cannot take type arguments", "no type arguments", reference.arguments().size() + " type argument(s)");
                for (TypeRef argument : reference.arguments()) {
                    resolveType(argument);
                }
            }
            base = parameter;
        } else {
            Optional<Type> resolved;
            if (reference.hasModulePrefix()) {
                String moduleName = currentPrefixes.get(reference.modulePrefix());
                if (moduleName == null) {
                    errorExpected(DiagnosticCode.RESOL_UNKNOWN_MODULE, reference.span(), //
                                    "unknown module prefix '" + reference.modulePrefix() + "'", "a visible module prefix", "'" + reference.modulePrefix() + "'");
                    for (TypeRef argument : reference.arguments()) {
                        resolveType(argument);
                    }
                    return null;
                }
                ModuleContents contents = modules.get(moduleName);
                Type moduleType = contents == null ? null : contents.types.get(reference.name());
                resolved = Optional.ofNullable(moduleType);
            } else {
                ModuleContents contents = currentModuleContents();
                Type moduleType = contents == null ? null : contents.types.get(reference.name());
                resolved = moduleType != null ? Optional.of(moduleType) : typeEnvironment.resolve(reference.name());
            }
            if (resolved.isEmpty()) {
                String written = reference.hasModulePrefix() ? reference.modulePrefix() + "." + reference.name() : reference.name();
                errorExpected(DiagnosticCode.RESOL_UNKNOWN_TYPE, reference.span(), //
                                "unknown type '" + written + "'", "a declared or built-in type", "'" + written + "'");
                return null;
            }
            base = resolved.get();
            List<TypeParameterType> parameters = base.typeParameters();
            if (applied) {
                if (parameters.isEmpty()) {
                    errorExpected(DiagnosticCode.TYPE_NOT_GENERIC, reference.span(), //
                                    "type '" + reference.name() + "' is not generic and cannot take type arguments", "a generic type", reference.name());
                    for (TypeRef argument : reference.arguments()) {
                        resolveType(argument);
                    }
                } else if (parameters.size() != reference.arguments().size()) {
                    errorExpected(DiagnosticCode.TYPE_TYPE_ARGUMENT_ARITY, reference.span(), //
                                    "generic type '" + reference.name() + "' has the wrong number of type arguments", Integer.toString(parameters.size()), Integer.toString(reference.arguments().size()));
                    for (TypeRef argument : reference.arguments()) {
                        resolveType(argument);
                    }
                } else {
                    List<Type> arguments = new ArrayList<>(reference.arguments().size());
                    boolean complete = true;
                    for (TypeRef argument : reference.arguments()) {
                        Type resolvedArgument = resolveType(argument);
                        if (resolvedArgument == null) {
                            complete = false;
                        } else {
                            arguments.add(resolvedArgument);
                        }
                    }
                    if (complete) {
                        base = base.parameterizedView(arguments);
                    }
                }
            } else if (!parameters.isEmpty()) {
                errorExpected(DiagnosticCode.TYPE_RAW_GENERIC_TYPE, reference.span(), //
                                "generic type '" + reference.name() + "' requires type arguments", parameters.size() + " type argument(s)", "none");
            }
        }
        return reference.isNullable() ? base.nullableView() : base;
    }

    /**
     * The class symbol behind a receiver type, whether it is a plain class type or a generic
     * application of one. Returns {@code null} for any other type.
     */
    private ClassSymbol classSymbolFor(Type type) {
        if (type instanceof ClassType classType) {
            return symbolsByType.get(classType);
        }
        if (type instanceof ParameterizedType parameterized && parameterized.base() instanceof ClassType classType) {
            return symbolsByType.get(classType);
        }
        return null;
    }

    /** The interface symbol behind a receiver type, plain or a generic application. */
    private InterfaceSymbol interfaceSymbolFor(Type type) {
        if (type instanceof InterfaceType interfaceType) {
            return symbolsByInterfaceType.get(interfaceType);
        }
        if (type instanceof ParameterizedType parameterized && parameterized.base() instanceof InterfaceType interfaceType) {
            return symbolsByInterfaceType.get(interfaceType);
        }
        return null;
    }

    /** The enum symbol behind a receiver type, plain or a generic application. */
    private EnumSymbol enumSymbolFor(Type type) {
        Type base = type instanceof ParameterizedType parameterized ? parameterized.base() : type;
        if (base instanceof EnumType enumType) {
            return symbolsByEnumType.get(enumType);
        }
        return null;
    }

    /**
     * The member a collection receiver exposes by name, or {@code null} when the receiver is not a
     * generic application of a built-in collection (docs/LANGUAGE_SPEC.md section 11). The member
     * table is shared by every collection kind, so one descriptor covers all four types.
     */
    private static BuiltinCollectionMember collectionMember(Type type, String name) {
        CollectionContext context = collectionContext(type);
        if (context == null) {
            return null;
        }
        return context.collectionType().members().get(name);
    }

    /**
     * The substitution a receiver type applies to the type parameters of its nominal base
     * (docs/LANGUAGE_SPEC.md section 11); empty for a plain non-generic receiver.
     */
    private static Map<TypeParameterType, Type> substitutionFor(Type type) {
        if (type instanceof ParameterizedType parameterized) {
            return parameterized.substitution();
        }
        return Map.of();
    }

    /** Composes two substitutions: the values of {@code outer} are themselves substituted by {@code inner}. */
    private static Map<TypeParameterType, Type> composeSubstitutions(Map<TypeParameterType, Type> outer, Map<TypeParameterType, Type> inner) {
        if (outer.isEmpty() || inner.isEmpty()) {
            return Map.copyOf(outer);
        }
        Map<TypeParameterType, Type> composed = new IdentityHashMap<>();
        for (Map.Entry<TypeParameterType, Type> entry : outer.entrySet()) {
            composed.put(entry.getKey(), entry.getValue().substitute(inner));
        }
        return composed;
    }

    /**
     * Whether a statement definitely transfers control out of its enclosing function through a
     * value-returning {@code return}. Conservative by design: a loop never counts as a guaranteed
     * return even when its condition is the constant {@code true}.
     */
    private static boolean alwaysReturns(StatementNode statement) {
        if (statement instanceof ReturnStmtNode || statement instanceof ThrowStmtNode) {
            return true;
        }
        if (statement instanceof BlockNode block) {
            for (StatementNode inner : block.statements()) {
                if (alwaysReturns(inner)) {
                    return true;
                }
            }
            return false;
        }
        if (statement instanceof IfStmtNode ifStatement) {
            return ifStatement.elseBranch().isPresent() && alwaysReturns(ifStatement.thenBlock()) && alwaysReturnsElse(ifStatement.elseBranch().get());
        }
        if (statement instanceof SwitchStmtNode switchStatement) {
            // A switch guarantees a return only when it has a default and every case body does, since
            // a value that matches no case leaves the statement normally.
            boolean hasDefault = false;
            for (SwitchCaseNode switchCase : switchStatement.cases()) {
                if (switchCase.isDefault()) {
                    hasDefault = true;
                }
                if (!alwaysReturns(switchCase.body())) {
                    return false;
                }
            }
            return hasDefault;
        }
        if (statement instanceof TryStmtNode tryStatement) {
            // Java's completion rules for try (docs/LANGUAGE_SPEC.md section 22.3): an abrupt
            // completion from the finally clause replaces whatever was in flight, so a finally block
            // that always transfers control guarantees it for the whole statement. Otherwise the
            // statement's completion is the try block's, and — when handlers exist — a handler body
            // that can complete normally leaves the statement reachable.
            if (tryStatement.finallyBlock() != null && alwaysReturns(tryStatement.finallyBlock())) {
                return true;
            }
            if (!alwaysReturns(tryStatement.tryBlock())) {
                return false;
            }
            for (TryStmtNode.CatchClause clause : tryStatement.catchClauses()) {
                if (!alwaysReturns(clause.body())) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private static boolean alwaysReturnsElse(ElseBranchNode branch) {
        return branch.isChainedIf() ? alwaysReturns(branch.chainedIf().get()) : alwaysReturns(branch.block().get());
    }

    private void error(DiagnosticCode code, SourceSpan span, String message) {
        diagnostics.add(Diagnostic.error(code, span, message));
    }

    private void errorExpected(DiagnosticCode code, SourceSpan span, String message, String expected, String found) {
        diagnostics.add(Diagnostic.expectedFound(code, span, message, expected, found));
    }
}
