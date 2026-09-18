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
import org.solvik.ast.declaration.TypeParameterNode;
import org.solvik.ast.declaration.TypeRefNode;
import org.solvik.ast.expression.BinaryExprNode;
import org.solvik.ast.expression.BinaryOperator;
import org.solvik.ast.expression.BlockExprNode;
import org.solvik.ast.expression.BoolLiteralNode;
import org.solvik.ast.expression.CallExprNode;
import org.solvik.ast.expression.CastExprNode;
import org.solvik.ast.expression.CharLiteralNode;
import org.solvik.ast.expression.ExpressionNode;
import org.solvik.ast.expression.FloatingLiteralNode;
import org.solvik.ast.expression.IfExprNode;
import org.solvik.ast.expression.IntLiteralNode;
import org.solvik.ast.expression.LiteralNode;
import org.solvik.ast.expression.LongLiteralNode;
import org.solvik.ast.expression.MapEntryExprNode;
import org.solvik.ast.expression.MatchBranchNode;
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
import org.solvik.type.BooleanType;
import org.solvik.type.ByteType;
import org.solvik.type.CharType;
import org.solvik.type.ClassType;
import org.solvik.type.InterfaceType;
import org.solvik.type.DoubleType;
import org.solvik.type.EnumType;
import org.solvik.type.FloatType;
import org.solvik.type.IdentityDomain;
import org.solvik.type.IntType;
import org.solvik.type.BuiltinCollectionMember;
import org.solvik.type.BuiltinCollectionType;
import org.solvik.type.BuiltinCollectionTypes;
import org.solvik.type.LongType;
import org.solvik.type.NothingType;
import org.solvik.type.NullType;
import org.solvik.type.NullableType;
import org.solvik.type.NumericTypes;
import org.solvik.type.ObjectType;
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
    private final Map<MemberAccessExprNode, PropertySymbol> propertyAccesses = new IdentityHashMap<>();
    private final Map<CallExprNode, ClassSymbol> constructorCalls = new IdentityHashMap<>();
    private final Map<CallExprNode, ResolvedMethod> methodCalls = new IdentityHashMap<>();
    /** The {@code toString()} calls that resolve to the built-in root member rather than a class method. */
    private final Set<CallExprNode> builtinToStringCalls = Collections.newSetFromMap(new IdentityHashMap<>());
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
    private final Map<ExpressionNode, Type> testedTypes = new IdentityHashMap<>();
    private final Map<CallExprNode, ClassSymbol> superConstructorCalls = new IdentityHashMap<>();
    /** The enum variant constructed by a call or a value-less variant read, for lowering. */
    private final Map<ExpressionNode, EnumVariantSymbol> variantConstructions = new IdentityHashMap<>();
    /** The compiled constant of each {@code Regex} construction whose pattern is a source constant. */
    private final Map<CallExprNode, RegexPattern> regexConstants = new IdentityHashMap<>();
    /** The compiled pattern of each {@code switch} regex case whose label is a string literal. */
    private final Map<RegexCaseLabelNode, RegexPattern> regexCasePatterns = new IdentityHashMap<>();
    private final Map<EnumPatternNode, EnumVariantSymbol> enumPatterns = new IdentityHashMap<>();
    private final Map<BindingPatternNode, VariableSymbol> patternBindings = new IdentityHashMap<>();
    private final Map<BindingPatternNode, Type> patternBindingTypes = new IdentityHashMap<>();
    /** Types already resolved for a written type reference, so an error is reported only once. */
    private final Map<TypeRefNode, Type> resolvedTypes = new IdentityHashMap<>();
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

    private FunctionSymbol currentFunction;
    private ClassSymbol currentClass;
    private InterfaceSymbol currentInterface;
    /** Expected types of the enclosing value bindings, most specific first, for type inference. */
    private final Deque<Type> expectedTypes = new ArrayDeque<>();
    private boolean checkingConstructor;
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
        return SemanticResult.success(new CheckedProgram(unit, analyzer.functions, analyzer.classes, analyzer.interfaces, analyzer.enums, analyzer.declaredClasses, analyzer.declaredInterfaces, analyzer.declaredEnums, analyzer.expressionTypes, analyzer.localSymbols, analyzer.nameSymbols, analyzer.propertyAccesses, analyzer.constructorCalls, analyzer.methodCalls, analyzer.builtinToStringCalls, analyzer.builtinEqualsCalls, analyzer.forInBindings, analyzer.conversions, analyzer.testedTypes, analyzer.superConstructorCalls, analyzer.variantConstructions, analyzer.regexConstants, analyzer.enumPatterns, analyzer.patternBindings, analyzer.patternBindingTypes, analyzer.regexCasePatterns, analyzer.qualifiedFunctionCalls, analyzer.entryPoint));
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

    /** A module prefix followed by a member path, e.g. {@code math.add} or {@code math.Result.Ok}. */
    private static final class QualifiedPrefix {
        final String module;
        final List<String> path;

        QualifiedPrefix(String module, List<String> path) {
            this.module = module;
            this.path = path;
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
                return new QualifiedPrefix(module, path);
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
            for (TypeRefNode reference : interfaceDeclaration.superInterfaces()) {
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
                TypeRefNode reference = classDeclaration.superClass().get();
                Type resolved = resolveType(reference);
                if (resolved != null && resolved != ObjectType.INSTANCE) {
                    ClassDeclNode superDeclaration = classDeclarationFor(resolved);
                    if (superDeclaration == null) {
                        errorExpected(DiagnosticCode.SEM_INVALID_SUPERCLASS, reference.span(), "a class may extend only a class or Object", "a class type", resolved.name());
                    } else {
                        superDeclarations.put(classDeclaration, superDeclaration);
                        resolvedSuperTypes.put(classDeclaration, resolved);
                    }
                }
            }
            List<Type> implemented = new ArrayList<>();
            for (TypeRefNode reference : classDeclaration.interfaces()) {
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
        declareBuiltin("exit", IntType.INSTANCE);
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
            for (TypeRefNode valueType : variant.valueTypes()) {
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
                Type propertyType = resolveType(property.declaredType().orElseThrow());
                if ("toString".equals(property.name())) {
                    error(DiagnosticCode.SEM_RESERVED_MEMBER, property.span(), "member 'toString' is reserved by Any.toString and must be declared as an override method");
                }
                if ("equals".equals(property.name())) {
                    error(DiagnosticCode.SEM_RESERVED_MEMBER, property.span(), "member 'equals' is reserved by Any.equals and must be declared as an override method");
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
        Map<TypeParameterType, Type> superSubstitution = substitutionFor(type.superType().orElse(ObjectType.INSTANCE));
        for (FunctionDeclNode method : declaration.methods()) {
            List<TypeParameterType> methodTypeParameters = declareTypeParameters(method.typeParameters());
            Map<String, TypeParameterType> classScope = typeParameterScope;
            typeParameterScope = mergedScope(classScope, methodTypeParameters);
            List<VariableSymbol> parameters = buildParameters(method.parameters());
            Type returnType = resolveType(method.returnType());
            typeParameterScope = classScope;
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
        ClassSymbol classSymbol = new ClassSymbol(declaration, type, declaration.isOpen(), declaration.isSealed(), superSymbol, implementedInterfaces, delegateBindings, properties, methods, constructor, interfaceBindings);
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

    /** Validates that a method's {@code override} modifier and signature match the inherited method. */
    private void validateOverride(ClassSymbol superSymbol, FunctionSymbol method, Map<TypeParameterType, Type> superSubstitution) {
        if ("toString".equals(method.name())) {
            validateToStringOverride(method);
            return;
        }
        if ("equals".equals(method.name())) {
            validateEqualsOverride(superSymbol, method);
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
     * returns exactly {@code String}; overloading it is impossible.
     */
    private void validateToStringOverride(FunctionSymbol method) {
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
            Type initializerType = checkExpression(initializer);
            if (symbol != null && initializerType != null && !initializerType.isAssignableTo(symbol.type())) {
                errorExpected(DiagnosticCode.TYPE_MISMATCH, initializer.span(), //
                                "initializer is not assignable to property type " + symbol.type().name(), symbol.type().name(), initializerType.name());
            }
        }
        for (FunctionSymbol method : classSymbol.declaredMethods()) {
            checkCallable(method, method.declaration().body(), classSymbol, false);
        }
        if (classSymbol.constructor().isPresent()) {
            FunctionSymbol constructor = classSymbol.constructor().get();
            checkCallable(constructor, constructor.constructorDeclaration().body(), classSymbol, true);
        }
        typeParameterScope = previousScope;
        currentClass = previousClass;
        currentFunction = previousFunction;
        currentInterface = previousInterface;
        checkingConstructor = previousChecking;
    }

    /**
     * Checks one callable body. A method or constructor runs with the owning class as {@code this};
     * a constructor additionally tracks definite property initialization and verifies that every
     * property is assigned on every successful path.
     */
    private void checkCallable(FunctionSymbol function, BlockNode body, ClassSymbol owner, boolean constructor) {
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
        currentClass = owner;
        checkingConstructor = constructor;
        sanctionedSuperCall = constructor ? firstSuperCall(body) : null;
        definitelyInitialized = initializedAtStart(owner, constructor);
        narrowedTypes = new IdentityHashMap<>();
        writtenVariables = Collections.newSetFromMap(new IdentityHashMap<>());
        loopDepth = 0;
        breakDepth = 0;
        Map<String, TypeParameterType> previousScope = typeParameterScope;
        typeParameterScope = mergedScope(scopeOf(ownerTypeParameters(function)), function.typeParameters());
        symbols.enterScope();
        for (VariableSymbol parameter : function.parameters()) {
            if (!symbols.declare(parameter)) {
                error(DiagnosticCode.RESOL_DUPLICATE_NAME, parameter.declarationSpan(), "parameter '" + parameter.name() + "' is already declared");
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
            error(DiagnosticCode.TYPE_MISSING_RETURN_PATH, declarationSpan, "function '" + function.name() + "' must return a value on every path");
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
        loopDepth = 0;
        breakDepth = 0;
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
        try {
            Type initializerType = checkExpression(declaration.initializer());
            Type variableType;
            if (declaredType != null) {
                variableType = declaredType;
            if (initializerType != null && !initializerType.isAssignableTo(declaredType)) {
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
     * {@code Int}; the loop variable is an implicitly declared immutable {@code Int} binding scoped
     * to the body. The body is a loop context, so {@code break} and {@code continue} are valid.
     */
    private void checkForIn(ForInStmtNode statement) {
        symbols.enterScope();
        Type startType = checkExpression(statement.start());
        if (startType != null && startType != IntType.INSTANCE) {
            errorExpected(DiagnosticCode.SEM_INVALID_RANGE_BOUND, statement.start().span(), "range start bound must have type Int", "Int", startType.name());
        }
        Type endType = checkExpression(statement.end());
        if (endType != null && endType != IntType.INSTANCE) {
            errorExpected(DiagnosticCode.SEM_INVALID_RANGE_BOUND, statement.end().span(), "range end bound must have type Int", "Int", endType.name());
        }
        VariableSymbol variable = new VariableSymbol(statement.variableName(), statement.span(), IntType.INSTANCE, false, false);
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
        Type valueType = scrutineeType == null ? null : scrutineeType.nonNullType();
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
                if (sawDefault) {
                    error(DiagnosticCode.SEM_SWITCH_DEFAULT_NOT_LAST, switchCase.span(), "default must be the last case of a switch");
                }
                for (CaseLabelNode label : switchCase.labels()) {
                    checkCaseLabel(label, valueType, scrutineeType);
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
     * and compiled once here against the portable dialect.
     */
    private void checkCaseLabel(CaseLabelNode label, Type valueType, Type scrutineeType) {
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
        if (valueType != StringType.INSTANCE) {
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
        Type expected = currentFunction.returnType();
        if (statement.value().isPresent()) {
            ExpressionNode value = statement.value().get();
            Type actual = checkExpression(value);
            if (expected == UnitType.INSTANCE) {
                error(DiagnosticCode.TYPE_UNEXPECTED_RETURN_VALUE, statement.span(), "a Unit function cannot return a value");
            } else if (actual != null && !actual.isAssignableTo(expected)) {
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

    private void checkAssign(AssignStmtNode statement) {
        ExpressionNode value = statement.value();
        Type valueType = checkExpression(value);
        ExpressionNode target = statement.target();
        if (target instanceof NameRefExprNode name) {
            Optional<Symbol> resolved = resolveName(name.name());
            if (resolved.isEmpty()) {
                error(DiagnosticCode.RESOL_UNKNOWN_NAME, name.span(), "unknown name '" + name.name() + "'");
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
            if (valueType != null && !valueType.isAssignableTo(variable.type())) {
                errorExpected(DiagnosticCode.TYPE_MISMATCH, value.span(), //
                                "value is not assignable to " + variable.type().name(), variable.type().name(), valueType.name());
            }
            variable.markInitialized();
            return;
        }
        if (target instanceof MemberAccessExprNode member) {
            if (member.isSafe()) {
                error(DiagnosticCode.TYPE_INVALID_ASSIGNMENT_TARGET, member.span(), "cannot assign through a '?.' safe access");
                return;
            }
            checkPropertyAssign(member, value, valueType);
            return;
        }
        error(DiagnosticCode.TYPE_INVALID_ASSIGNMENT_TARGET, target.span(), "assignment target must be a mutable local or a var property");
    }

    /** Checks a {@code receiver.member = value} assignment and enforces property mutability. */
    private void checkPropertyAssign(MemberAccessExprNode member, ExpressionNode value, Type valueType) {
        Type receiverType = checkExpression(member.receiver());
        if (isNullableType(receiverType)) {
            error(DiagnosticCode.TYPE_NULLABLE_DEREFERENCE, member.receiver().span(), //
                            "receiver of '" + member.memberName() + "' may be null; use '?.' or check for null first");
            return;
        }
        if (receiverType == RegexType.INSTANCE) {
            if (isRegexMethodName(member.memberName())) {
                error(DiagnosticCode.TYPE_INVALID_ASSIGNMENT_TARGET, member.span(), "cannot assign to method '" + member.memberName() + "'");
            } else {
                error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, member.span(), "Regex has no property '" + member.memberName() + "'");
            }
            return;
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
            return;
        }
        BuiltinCollectionMember collectionMember = collectionMember(receiverType, member.memberName());
        if (collectionMember != null) {
            if (collectionMember.isProperty()) {
                error(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE, member.span(), "cannot assign to immutable '" + member.memberName() + "'");
            } else {
                error(DiagnosticCode.TYPE_INVALID_ASSIGNMENT_TARGET, member.span(), "cannot assign to method '" + member.memberName() + "'");
            }
            return;
        }
        ClassSymbol classSymbol = classSymbolFor(receiverType);
        if (classSymbol == null) {
            if (receiverType != null) {
                error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, member.span(), "type " + receiverType.name() + " has no member '" + member.memberName() + "'");
            }
            return;
        }
        Optional<PropertySymbol> resolved = classSymbol.property(member.memberName());
        if (resolved.isEmpty()) {
            if (classSymbol.method(member.memberName()).isPresent()) {
                error(DiagnosticCode.TYPE_INVALID_ASSIGNMENT_TARGET, member.span(), "cannot assign to method '" + member.memberName() + "'");
            } else {
                error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, member.span(), "class " + classSymbol.name() + " has no property '" + member.memberName() + "'");
            }
            return;
        }
        PropertySymbol property = resolved.get();
        propertyAccesses.put(member, property);
        Type propertyType = property.type().substitute(composeSubstitutions(classSymbol.propertySubstitution(property), substitutionFor(receiverType)));
        if (valueType != null && !valueType.isAssignableTo(propertyType)) {
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
    }

    private void checkExprStmt(ExprStmtNode statement) {
        checkExpression(statement.expression());
        if (!(statement.expression() instanceof CallExprNode)) {
            error(DiagnosticCode.SEM_VALUE_EXPRESSION_STATEMENT, statement.expression().span(), "only a call may be used as a standalone statement");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Expression typing
    // ---------------------------------------------------------------------------------------------

    private Type checkExpression(ExpressionNode expression) {
        switch (expression.kind()) {
            case INT_LITERAL:
                return record(expression, checkIntLiteral((IntLiteralNode) expression));
            case LONG_LITERAL:
                return record(expression, checkLongLiteral((LongLiteralNode) expression));
            case FLOATING_LITERAL:
                return record(expression, checkFloatingLiteral((FloatingLiteralNode) expression));
            case CHAR_LITERAL:
                return record(expression, checkCharLiteral((CharLiteralNode) expression));
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

    private Type checkIntLiteral(IntLiteralNode literal) {
        BigInteger value = new BigInteger(literal.lexeme());
        if (value.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
            errorExpected(DiagnosticCode.TYPE_INT_LITERAL_OUT_OF_RANGE, literal.span(), //
                            "integer literal is outside the signed 32-bit Int range", "-2147483648..2147483647", literal.lexeme());
        }
        return IntType.INSTANCE;
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
     * {@code Char} representation and are reported as invalid literals.
     */
    private Type checkCharLiteral(CharLiteralNode literal) {
        String text = literal.lexeme();
        String content = text.substring(1, text.length() - 1);
        if (content.length() == 1 && content.charAt(0) != '\\') {
            return CharType.INSTANCE;
        }
        if (content.length() == 2 && content.charAt(0) == '\\' && isSupportedCharEscape(content.charAt(1))) {
            return CharType.INSTANCE;
        }
        if (content.length() >= 2 && content.charAt(0) == '\\') {
            errorExpected(DiagnosticCode.LEXER_INVALID_ESCAPE, literal.span(), "unsupported character escape", "\\\\ \\\" \\' \\n \\r \\t \\0", content);
            return CharType.INSTANCE;
        }
        errorExpected(DiagnosticCode.TYPE_INVALID_CHAR_LITERAL, literal.span(), "character literal must contain exactly one character", "one character", text);
        return CharType.INSTANCE;
    }

    private static boolean isSupportedCharEscape(char escape) {
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
            error(DiagnosticCode.RESOL_UNKNOWN_NAME, name.span(), "unknown name '" + name.name() + "'");
            return null;
        }
        Symbol symbol = resolved.get();
        if (symbol instanceof FunctionSymbol function) {
            error(DiagnosticCode.TYPE_FUNCTION_AS_VALUE, name.span(), "function '" + function.name() + "' cannot be used as a value");
            return null;
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

    private Type checkThis(ThisExprNode expression) {
        if (currentClass != null) {
            return currentClass.type();
        }
        if (currentInterface != null) {
            // Inside a default method `this` is the conforming instance, statically the interface.
            return currentInterface.type();
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
        if (currentClass == null) {
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
                if (NumericTypes.isNumeric(left) && left == right) {
                    return left;
                }
                invalidOperands(expression.span(), expression.operator().spelling(), "two operands of the same numeric type", left, right);
                return null;
            case COMPARISON:
                if (NumericTypes.isNumeric(left) && left == right) {
                    return BooleanType.INSTANCE;
                }
                invalidOperands(expression.span(), expression.operator().spelling(), "two operands of the same numeric type", left, right);
                return null;
            case EQUALITY:
                if (left.isAssignableTo(right) || right.isAssignableTo(left)) {
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
                // `Any`, `Object`, scalars, enums, regex values and unbounded type parameters can be
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
            }
            return checkMethodCall(expression, member);
        }
        if (callee instanceof NamespaceAccessExprNode) {
            error(DiagnosticCode.RESOL_UNKNOWN_MODULE, callee.span(), "'::' must name a visible module or alias prefix");
            return null;
        }
        if (checkExpression(callee) != null) {
            error(DiagnosticCode.TYPE_NOT_CALLABLE, callee.span(), "expression is not callable");
        }
        return null;
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
        if (argument instanceof IntLiteralNode intLiteral) {
            checkConstantRange(argument, target, new BigInteger(intLiteral.lexeme()));
        } else if (argument instanceof LongLiteralNode longLiteral) {
            String digits = longLiteral.lexeme();
            checkConstantRange(argument, target, new BigInteger(digits.substring(0, digits.length() - 1)));
        } else if (argument instanceof FloatingLiteralNode floating) {
            double parsed = Double.parseDouble(floating.numericText());
            if (Double.isNaN(parsed) || Double.isInfinite(parsed) || parsed < Long.MIN_VALUE || parsed > Long.MAX_VALUE) {
                errorExpected(DiagnosticCode.TYPE_CONVERSION_OUT_OF_RANGE, argument.span(), //
                                "constant conversion to " + target.name() + " is out of range", integralRangeText(target), floating.numericText());
                return;
            }
            checkConstantRange(argument, target, BigDecimal.valueOf(parsed).toBigInteger());
        }
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
        if (target == IntType.INSTANCE) {
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
        if (target == IntType.INSTANCE) {
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
        return substitutionFor(currentClass.type().superType().orElse(ObjectType.INSTANCE));
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
     * constructs {@code Box<Int>}. The inferred application is the type of the construction
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
        if (!checkArity(call, classSymbol.name(), parameters.size(), argumentTypes.size())) {
            // A wrong count suppresses type inference and argument type checking; the program cannot
            // be lowered while the arity error exists.
            constructorCalls.put(call, classSymbol);
            return classSymbol.type();
        }
        List<TypeParameterType> typeParameters = classSymbol.type().typeParameters();
        Map<TypeParameterType, Type> substitution = explicitTypeArguments(call, typeParameters, declaredParameterTypes, argumentTypes, classSymbol.name());
        if (substitution == null) {
            substitution = inferCallableTypeArguments(call, typeParameters, declaredParameterTypes, argumentTypes);
        }
        checkArgumentTypesAgainst(call, classSymbol.name(), substitutedTypes(declaredParameterTypes, substitution), argumentTypes);
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
        Map<TypeParameterType, Type> substitution = inferCallableTypeArguments(call, typeParameters, declaredValueTypes, argumentTypes);
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
                return checkVariantConstruction(call, enumSymbol, qualified.path.get(1), call.span());
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
                return checkVariantRead(expression, enumSymbol, qualified.path.get(1), expression.span());
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
            } else if (symbol instanceof FunctionSymbol) {
                error(DiagnosticCode.TYPE_FUNCTION_AS_VALUE, expression.span(), "function '" + written + "' cannot be used as a value");
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
        if (receiverType == RegexType.INSTANCE) {
            return checkRegexMethodCall(call, member);
        }
        if (receiverType == RegexMatchType.INSTANCE) {
            if ("group".equals(member.memberName())) {
                checkArguments(call, "group", List.of(IntType.INSTANCE));
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
        InterfaceSymbol interfaceSymbol = interfaceSymbolFor(receiverType);
        if (interfaceSymbol != null) {
            return checkInterfaceMethodCall(call, member, receiverType, interfaceSymbol);
        }
        ClassSymbol classSymbol = classSymbolFor(receiverType);
        if (classSymbol == null) {
            error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, member.span(), "type " + receiverType.name() + " has no member '" + member.memberName() + "'");
            return null;
        }
        if (classSymbol.property(member.memberName()).isPresent()) {
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
        List<Type> argumentTypes = checkArgumentTypes(call);
        List<Type> declaredParameterTypes = substitutedParameterTypes(target.parameters(), receiverSubstitution);
        if (!checkArity(call, calleeLabel, target.parameterCount(), argumentTypes.size())) {
            // The receiver is not an explicit argument, so arity is the declared parameter count.
            // A wrong count suppresses inference and type checking; the program cannot be lowered
            // while the arity error exists, so a best-effort result type is sufficient here.
            return target.returnType().substitute(receiverSubstitution);
        }
        Map<TypeParameterType, Type> substitution = explicitTypeArguments(call, target.typeParameters(), declaredParameterTypes, argumentTypes, calleeLabel);
        if (substitution == null) {
            substitution = inferCallableTypeArguments(call, target.typeParameters(), declaredParameterTypes, argumentTypes);
        }
        checkArgumentTypesAgainst(call, calleeLabel, substitutedTypes(declaredParameterTypes, substitution), argumentTypes);
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

    /** Checks every argument expression once and returns its static type, in order. */
    private List<Type> checkArgumentTypes(CallExprNode call) {
        List<Type> types = new ArrayList<>(call.arguments().size());
        for (ExpressionNode argument : call.arguments()) {
            types.add(checkExpression(argument));
        }
        return types;
    }

    private void checkArguments(CallExprNode call, String calleeLabel, List<Type> parameterTypes) {
        List<Type> argumentTypes = checkArgumentTypes(call);
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
            if (argumentType != null && !argumentType.isAssignableTo(parameterType)) {
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
     * infers them from the enclosing expected type, so {@code val l: List<Int> = List(1, 2)} resolves
     * {@code T} from the left-hand side. A construction with no expected type and no explicit type
     * arguments has no evidence for the type parameters. Value arguments become the collection's
     * initial elements, and a {@code Map} takes {@code key: value} entries.
     */
    private Type checkCollectionConstruction(CallExprNode expression, BuiltinCollectionType collection) {
        List<TypeRefNode> typeArguments = expression.typeArguments();
        if (!typeArguments.isEmpty() && typeArguments.size() != collection.typeParameters().size()) {
            errorExpected(DiagnosticCode.TYPE_TYPE_ARGUMENT_ARITY, expression.span(), //
                    "call to '" + collection.name() + "' has the wrong number of type arguments", //
                    Integer.toString(collection.typeParameters().size()), Integer.toString(typeArguments.size()));
            return null;
        }
        Type constructed;
        if (typeArguments.isEmpty()) {
            // Infer the type parameters from the enclosing declaration's expected type, so
            // {@code var l: List<Int> = List(1, 2)} resolves T from the left-hand side. A
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
            for (TypeRefNode argument : typeArguments) {
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
            if (actual != null && expected != null && !actual.isAssignableTo(expected)) {
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
        List<TypeRefNode> arguments = call.typeArguments();
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

    /**
     * Infers a generic callable's type arguments from its argument types. A type parameter bound by
     * an argument is retained; an unbound parameter is reported as uninferable and yields no binding.
     */
    private Map<TypeParameterType, Type> inferCallableTypeArguments(CallExprNode call, List<TypeParameterType> typeParameters, List<Type> parameterTypes, List<Type> argumentTypes) {
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
        for (TypeParameterType parameter : typeParameters) {
            if (!bindings.containsKey(parameter)) {
                errorExpected(DiagnosticCode.TYPE_CANNOT_INFER, call.span(), //
                                "cannot infer type argument for '" + parameter.name() + "'", "an argument that determines " + parameter.name(), "no determining argument");
                return Map.of();
            }
        }
        return bindings;
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
        if ("toString".equals(expression.memberName())) {
            // Any.toString is a method; a bare reference is never a value (docs/LANGUAGE_SPEC.md section 4).
            error(DiagnosticCode.TYPE_FUNCTION_AS_VALUE, expression.span(), "method 'toString' cannot be used as a value");
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
                    return IntType.INSTANCE;
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
    private static String typeRefSignature(TypeRefNode reference) {
        StringBuilder signature = new StringBuilder(reference.name());
        if (!reference.arguments().isEmpty()) {
            signature.append('(');
            for (int i = 0; i < reference.arguments().size(); i++) {
                if (i > 0) {
                    signature.append(',');
                }
                signature.append(typeRefSignature(reference.arguments().get(i)));
            }
            signature.append(')');
        }
        return reference.isNullable() ? signature.append('?').toString() : signature.toString();
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

    private Type resolveType(TypeRefNode reference) {
        if (resolvedTypes.containsKey(reference)) {
            return resolvedTypes.get(reference);
        }
        Type result = resolveTypeUncached(reference);
        resolvedTypes.put(reference, result);
        return result;
    }

    private Type resolveTypeUncached(TypeRefNode reference) {
        boolean applied = !reference.arguments().isEmpty();
        TypeParameterType parameter = reference.hasModulePrefix() ? null : typeParameterScope.get(reference.name());
        Type base;
        if (parameter != null) {
            if (applied) {
                errorExpected(DiagnosticCode.TYPE_NOT_GENERIC, reference.span(), //
                                "type parameter '" + reference.name() + "' cannot take type arguments", "no type arguments", reference.arguments().size() + " type argument(s)");
                for (TypeRefNode argument : reference.arguments()) {
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
                    for (TypeRefNode argument : reference.arguments()) {
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
                    for (TypeRefNode argument : reference.arguments()) {
                        resolveType(argument);
                    }
                } else if (parameters.size() != reference.arguments().size()) {
                    errorExpected(DiagnosticCode.TYPE_TYPE_ARGUMENT_ARITY, reference.span(), //
                                    "generic type '" + reference.name() + "' has the wrong number of type arguments", Integer.toString(parameters.size()), Integer.toString(reference.arguments().size()));
                    for (TypeRefNode argument : reference.arguments()) {
                        resolveType(argument);
                    }
                } else {
                    List<Type> arguments = new ArrayList<>(reference.arguments().size());
                    boolean complete = true;
                    for (TypeRefNode argument : reference.arguments()) {
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
        if (statement instanceof ReturnStmtNode) {
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
