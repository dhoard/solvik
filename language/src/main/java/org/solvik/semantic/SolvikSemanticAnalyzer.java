/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.semantic;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.ClassDeclNode;
import org.solvik.ast.declaration.DeclarationNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.declaration.InitDeclNode;
import org.solvik.ast.declaration.ParameterNode;
import org.solvik.ast.declaration.PropertyDeclNode;
import org.solvik.ast.declaration.TypeRefNode;
import org.solvik.ast.expression.BinaryExprNode;
import org.solvik.ast.expression.BoolLiteralNode;
import org.solvik.ast.expression.CallExprNode;
import org.solvik.ast.expression.CharLiteralNode;
import org.solvik.ast.expression.ExpressionNode;
import org.solvik.ast.expression.FloatingLiteralNode;
import org.solvik.ast.expression.IntLiteralNode;
import org.solvik.ast.expression.LongLiteralNode;
import org.solvik.ast.expression.MemberAccessExprNode;
import org.solvik.ast.expression.NameRefExprNode;
import org.solvik.ast.expression.ParenExprNode;
import org.solvik.ast.expression.RawStringLiteralNode;
import org.solvik.ast.expression.StringLiteralNode;
import org.solvik.ast.expression.SuperExprNode;
import org.solvik.ast.expression.ThisExprNode;
import org.solvik.ast.expression.UnaryExprNode;
import org.solvik.ast.expression.UnaryOperator;
import org.solvik.ast.statement.AssignStmtNode;
import org.solvik.ast.statement.BindingKind;
import org.solvik.ast.statement.BlockNode;
import org.solvik.ast.statement.BreakStmtNode;
import org.solvik.ast.statement.ContinueStmtNode;
import org.solvik.ast.statement.ElseBranchNode;
import org.solvik.ast.statement.ExprStmtNode;
import org.solvik.ast.statement.ForStmtNode;
import org.solvik.ast.statement.IfStmtNode;
import org.solvik.ast.statement.LocalDeclNode;
import org.solvik.ast.statement.ReturnStmtNode;
import org.solvik.ast.statement.StatementNode;
import org.solvik.ast.statement.WhileStmtNode;
import org.solvik.diagnostic.Diagnostic;
import org.solvik.diagnostic.DiagnosticBag;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.source.SourceSpan;
import org.solvik.source.StringEscapes;
import org.solvik.type.AnyType;
import org.solvik.type.BooleanType;
import org.solvik.type.ByteType;
import org.solvik.type.CharType;
import org.solvik.type.ClassType;
import org.solvik.type.DoubleType;
import org.solvik.type.FloatType;
import org.solvik.type.IntType;
import org.solvik.type.LongType;
import org.solvik.type.NumericTypes;
import org.solvik.type.ObjectType;
import org.solvik.type.ShortType;
import org.solvik.type.StringType;
import org.solvik.type.Type;
import org.solvik.type.TypeEnvironment;
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
    private final DiagnosticBag.Builder diagnostics = DiagnosticBag.builder();
    private final SymbolTable symbols = new SymbolTable();
    private final Map<ExpressionNode, Type> expressionTypes = new IdentityHashMap<>();
    private final Map<LocalDeclNode, VariableSymbol> localSymbols = new IdentityHashMap<>();
    private final Map<NameRefExprNode, Symbol> nameSymbols = new IdentityHashMap<>();
    private final Map<MemberAccessExprNode, PropertySymbol> propertyAccesses = new IdentityHashMap<>();
    private final Map<CallExprNode, ClassSymbol> constructorCalls = new IdentityHashMap<>();
    private final Map<CallExprNode, ResolvedMethod> methodCalls = new IdentityHashMap<>();
    private final Map<String, FunctionSymbol> functions = new LinkedHashMap<>();
    private final Map<String, ClassSymbol> classes = new LinkedHashMap<>();
    private final Map<FunctionDeclNode, FunctionSymbol> declaredFunctions = new IdentityHashMap<>();
    private final Map<ClassDeclNode, ClassSymbol> declaredClasses = new IdentityHashMap<>();
    private final Map<ClassDeclNode, ClassType> classTypes = new IdentityHashMap<>();
    private final Map<ClassType, ClassSymbol> symbolsByType = new IdentityHashMap<>();
    private final Map<ClassType, ClassDeclNode> declarationsByType = new IdentityHashMap<>();
    private final Map<ClassDeclNode, ClassDeclNode> superDeclarations = new IdentityHashMap<>();
    private final Map<CallExprNode, Type> conversions = new IdentityHashMap<>();
    private final Map<CallExprNode, ClassSymbol> superConstructorCalls = new IdentityHashMap<>();

    private FunctionSymbol currentFunction;
    private ClassSymbol currentClass;
    private boolean checkingConstructor;
    private CallExprNode sanctionedSuperCall;
    private Set<PropertySymbol> definitelyInitialized = Collections.emptySet();
    private int loopDepth;
    private FunctionSymbol entryPoint;

    private SolvikSemanticAnalyzer(TypeEnvironment typeEnvironment) {
        this.typeEnvironment = Objects.requireNonNull(typeEnvironment);
    }

    /** Runs declaration collection and static checking over a parsed Solvik source file. */
    public static SemanticResult analyze(CompilationUnitNode unit) {
        Objects.requireNonNull(unit, "unit");
        SolvikSemanticAnalyzer analyzer = new SolvikSemanticAnalyzer(new TypeEnvironment());
        analyzer.collectDeclarations(unit);
        analyzer.checkBodies(unit);
        DiagnosticBag bag = analyzer.diagnostics.build();
        if (bag.hasErrors()) {
            return SemanticResult.failure(bag);
        }
        return SemanticResult.success(new CheckedProgram(unit, analyzer.functions, analyzer.classes, analyzer.declaredClasses, analyzer.expressionTypes, analyzer.localSymbols, analyzer.nameSymbols, analyzer.propertyAccesses, analyzer.constructorCalls, analyzer.methodCalls, analyzer.conversions, analyzer.superConstructorCalls, analyzer.entryPoint));
    }

    // ---------------------------------------------------------------------------------------------
    // Declaration collection
    // ---------------------------------------------------------------------------------------------

    private void collectDeclarations(CompilationUnitNode unit) {
        declareBuiltins();
        // Pass A: register every class's nominal type first so any declaration order may reference
        // a class by name in property, parameter, return, and local types.
        for (DeclarationNode declaration : unit.declarations()) {
            if (declaration instanceof ClassDeclNode classDeclaration) {
                ClassType type = new ClassType(classDeclaration.name());
                classTypes.put(classDeclaration, type);
                declarationsByType.put(type, classDeclaration);
                if (typeEnvironment.resolve(classDeclaration.name()).isPresent()) {
                    error(DiagnosticCode.RESOL_DUPLICATE_NAME, classDeclaration.span(), "type name '" + classDeclaration.name() + "' is already declared");
                } else {
                    typeEnvironment.declare(type);
                }
            }
        }
        // Pass A2: resolve `extends` clauses, reject cycles, and install supertypes before any
        // subclass member is collected.
        resolveSuperclasses(unit);
        // Pass B: top-level functions.
        for (DeclarationNode declaration : unit.declarations()) {
            if (declaration instanceof FunctionDeclNode function) {
                collectFunction(function);
            }
        }
        // Pass C: class members in superclass-first order so a subclass can inspect its superclass.
        for (ClassDeclNode classDeclaration : inheritanceOrder(unit)) {
            collectClass(classDeclaration, classTypes.get(classDeclaration));
        }
        FunctionSymbol main = functions.get("main");
        if (main != null && main.parameters().isEmpty() && main.isReturnTypeKnown() && main.returnType() == UnitType.INSTANCE) {
            entryPoint = main;
        }
    }

    /**
     * Resolves each written {@code extends} reference to a class declaration, reports invalid
     * superclasses and inheritance cycles, and installs the resolved supertype on every
     * {@link ClassType}. A cycle is broken at the offending edge so later passes terminate.
     */
    private void resolveSuperclasses(CompilationUnitNode unit) {
        for (DeclarationNode declaration : unit.declarations()) {
            if (!(declaration instanceof ClassDeclNode classDeclaration) || classDeclaration.superClass().isEmpty()) {
                continue;
            }
            TypeRefNode reference = classDeclaration.superClass().get();
            Type resolved = resolveType(reference);
            if (resolved == null || resolved == ObjectType.INSTANCE) {
                continue;
            }
            if (!(resolved instanceof ClassType superType)) {
                errorExpected(DiagnosticCode.SEM_INVALID_SUPERCLASS, reference.span(), "a class may extend only a class or Object", "a class type", resolved.name());
                continue;
            }
            ClassDeclNode superDeclaration = declarationsByType.get(superType);
            if (superDeclaration == null) {
                errorExpected(DiagnosticCode.SEM_INVALID_SUPERCLASS, reference.span(), "a class may extend only a class or Object", "a class type", resolved.name());
                continue;
            }
            superDeclarations.put(classDeclaration, superDeclaration);
        }
        detectInheritanceCycles(unit);
        for (DeclarationNode declaration : unit.declarations()) {
            if (declaration instanceof ClassDeclNode classDeclaration) {
                ClassDeclNode superDeclaration = superDeclarations.get(classDeclaration);
                classTypes.get(classDeclaration).resolveSuperType(superDeclaration == null ? null : classTypes.get(superDeclaration));
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

    private void declareBuiltins() {
        declareBuiltin("print");
        declareBuiltin("println");
    }

    private void declareBuiltin(String name) {
        FunctionSymbol symbol = FunctionSymbol.builtin(name, List.of(AnyType.INSTANCE), UnitType.INSTANCE);
        symbols.declare(symbol);
        functions.put(name, symbol);
    }

    private void collectFunction(FunctionDeclNode declaration) {
        Type returnType = resolveType(declaration.returnType());
        List<VariableSymbol> parameters = buildParameters(declaration.parameters());
        FunctionSymbol functionSymbol = new FunctionSymbol(declaration.name(), declaration.span(), parameters, //
                        returnType != null ? returnType : AnyType.INSTANCE, returnType != null, declaration);
        declaredFunctions.put(declaration, functionSymbol);
        if (!symbols.declare(functionSymbol)) {
            error(DiagnosticCode.RESOL_DUPLICATE_NAME, declaration.span(), "function '" + declaration.name() + "' is already declared");
        } else {
            functions.put(declaration.name(), functionSymbol);
        }
        if ("main".equals(declaration.name())) {
            validateEntryPointSignature(functionSymbol);
        }
    }

    private void collectClass(ClassDeclNode declaration, ClassType type) {
        ClassDeclNode superDeclaration = superDeclarations.get(declaration);
        ClassSymbol superSymbol = superDeclaration == null ? null : declaredClasses.get(superDeclaration);
        if (superDeclaration != null && !superDeclaration.isOpen()) {
            errorExpected(DiagnosticCode.SEM_EXTEND_FINAL, declaration.superClass().orElseThrow().span(), //
                            "class '" + declaration.name() + "' cannot extend final class", "an open class", superDeclaration.name());
        }

        List<PropertySymbol> properties = new ArrayList<>();
        Set<String> propertyNames = new HashSet<>();
        if (superSymbol != null) {
            for (PropertySymbol inherited : superSymbol.properties()) {
                propertyNames.add(inherited.name());
            }
        }
        int index = superSymbol == null ? 0 : superSymbol.properties().size();
        for (PropertyDeclNode property : declaration.properties()) {
            Type propertyType = resolveType(property.declaredType().orElseThrow());
            if (propertyNames.add(property.name())) {
                properties.add(new PropertySymbol(property.name(), property.span(), propertyType != null ? propertyType : AnyType.INSTANCE, //
                                property.bindingKind() == BindingKind.VAR, property.initializer().isPresent(), index));
            } else {
                error(DiagnosticCode.RESOL_DUPLICATE_NAME, property.span(), "property '" + property.name() + "' is already declared");
            }
            index++;
        }

        List<FunctionSymbol> methods = new ArrayList<>();
        Set<String> methodNames = new HashSet<>();
        for (FunctionDeclNode method : declaration.methods()) {
            List<VariableSymbol> parameters = buildParameters(method.parameters());
            Type returnType = resolveType(method.returnType());
            FunctionSymbol symbol = FunctionSymbol.declaredMethod(method.name(), method.span(), parameters, //
                            returnType != null ? returnType : AnyType.INSTANCE, returnType != null, method, declaration, method.isOpen(), method.isOverride());
            methods.add(symbol);
            if (propertyNames.contains(method.name())) {
                error(DiagnosticCode.RESOL_DUPLICATE_NAME, method.span(), "member '" + method.name() + "' is already declared");
            } else if (!methodNames.add(method.name())) {
                error(DiagnosticCode.RESOL_DUPLICATE_NAME, method.span(), "method '" + method.name() + "' is already declared");
            } else {
                validateOverride(superSymbol, symbol);
            }
        }

        FunctionSymbol constructor = null;
        if (declaration.initializers().size() > 1) {
            error(DiagnosticCode.SEM_DUPLICATE_INIT, declaration.initializers().get(1).span(), "a class may declare at most one init");
        }
        if (declaration.initializers().size() == 1) {
            InitDeclNode init = declaration.initializers().get(0);
            constructor = FunctionSymbol.declaredConstructor(init.span(), buildParameters(init.parameters()), init, declaration);
        } else {
            for (PropertySymbol property : properties) {
                if (!property.hasInitializer()) {
                    error(DiagnosticCode.SEM_CLASS_REQUIRES_INITIALIZER, property.declarationSpan(), //
                                    "property '" + property.name() + "' needs an initializer because the class has no init");
                }
            }
            if (superRequiresArguments(superSymbol)) {
                error(DiagnosticCode.SEM_MISSING_SUPER_INIT_IMPLICIT, declaration.span(), //
                                "class '" + declaration.name() + "' must declare init to call super(...)");
            }
        }

        ClassSymbol classSymbol = new ClassSymbol(declaration, type, declaration.isOpen(), superSymbol, properties, methods, constructor);
        declaredClasses.put(declaration, classSymbol);
        symbolsByType.put(type, classSymbol);
        if (!symbols.declare(classSymbol)) {
            error(DiagnosticCode.RESOL_DUPLICATE_NAME, declaration.span(), "class '" + declaration.name() + "' is already declared");
        } else {
            classes.put(declaration.name(), classSymbol);
        }
    }

    /** Validates that a method's {@code override} modifier and signature match the inherited method. */
    private void validateOverride(ClassSymbol superSymbol, FunctionSymbol method) {
        FunctionSymbol inherited = superSymbol == null ? null : superSymbol.method(method.name()).orElse(null);
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
            if (method.isReturnTypeKnown() && (!sameParameterTypes(inherited, method) || !method.returnType().isAssignableTo(inherited.returnType()))) {
                errorExpected(DiagnosticCode.SEM_OVERRIDE_SIGNATURE, method.declarationSpan(), //
                                "override of '" + method.name() + "' must keep the inherited parameter types and a covariant return type", inherited.returnType().name(), method.returnType().name());
            }
        } else if (inherited != null) {
            error(DiagnosticCode.SEM_ACCIDENTAL_OVERRIDE, method.declarationSpan(), //
                            "method '" + method.name() + "' overrides an inherited method and must be declared override");
        }
    }

    private static boolean sameParameterTypes(FunctionSymbol a, FunctionSymbol b) {
        if (a.parameters().size() != b.parameters().size()) {
            return false;
        }
        for (int i = 0; i < a.parameters().size(); i++) {
            if (a.parameters().get(i).type() != b.parameters().get(i).type()) {
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

    private void validateEntryPointSignature(FunctionSymbol function) {
        if (!function.parameters().isEmpty()) {
            errorExpected(DiagnosticCode.SEM_INVALID_ENTRY_POINT, function.declaration().span(), //
                            "entry point 'main' must take no parameters", "fun main(): Unit", "fun main(" + function.parameters().size() + " parameter(s))");
        }
        if (function.isReturnTypeKnown() && function.returnType() != UnitType.INSTANCE) {
            errorExpected(DiagnosticCode.SEM_INVALID_ENTRY_POINT, function.declaration().span(), //
                            "entry point 'main' must return Unit", "Unit", function.returnType().name());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Body checking
    // ---------------------------------------------------------------------------------------------

    private void checkBodies(CompilationUnitNode unit) {
        for (DeclarationNode declaration : unit.declarations()) {
            if (declaration instanceof FunctionDeclNode function) {
                checkCallable(declaredFunctions.get(function), function.body(), null, false);
            } else if (declaration instanceof ClassDeclNode classDeclaration) {
                checkClass(declaredClasses.get(classDeclaration));
            }
        }
    }

    private void checkClass(ClassSymbol classSymbol) {
        ClassSymbol previousClass = currentClass;
        FunctionSymbol previousFunction = currentFunction;
        boolean previousChecking = checkingConstructor;
        currentClass = null;
        currentFunction = null;
        checkingConstructor = false;
        for (PropertyDeclNode property : classSymbol.declaration().properties()) {
            if (property.initializer().isPresent()) {
                PropertySymbol symbol = classSymbol.property(property.name()).orElse(null);
                Type initializerType = checkExpression(property.initializer().get());
                if (symbol != null && initializerType != null && !initializerType.isAssignableTo(symbol.type())) {
                    errorExpected(DiagnosticCode.TYPE_MISMATCH, property.initializer().get().span(), //
                                    "initializer is not assignable to property type " + symbol.type().name(), symbol.type().name(), initializerType.name());
                }
            }
        }
        for (FunctionSymbol method : classSymbol.declaredMethods()) {
            checkCallable(method, method.declaration().body(), classSymbol, false);
        }
        if (classSymbol.constructor().isPresent()) {
            FunctionSymbol constructor = classSymbol.constructor().get();
            checkCallable(constructor, constructor.initDeclaration().body(), classSymbol, true);
        }
        currentClass = previousClass;
        currentFunction = previousFunction;
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
        boolean previousChecking = checkingConstructor;
        CallExprNode previousSuperCall = sanctionedSuperCall;
        Set<PropertySymbol> previousInitialized = definitelyInitialized;
        currentFunction = function;
        currentClass = owner;
        checkingConstructor = constructor;
        sanctionedSuperCall = constructor ? firstSuperCall(body) : null;
        definitelyInitialized = initializedAtStart(owner, constructor);
        loopDepth = 0;
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
                error(DiagnosticCode.SEM_MISSING_SUPER_INIT, body.span(), "class '" + owner.name() + "' must call super(...) as the first statement of init");
            }
        }
        SourceSpan declarationSpan = function.declaration() != null ? function.declaration().span() : function.declarationSpan();
        if (function.isReturnTypeKnown() && function.returnType() != UnitType.INSTANCE && !alwaysReturns(body)) {
            error(DiagnosticCode.TYPE_MISSING_RETURN_PATH, declarationSpan, "function '" + function.name() + "' must return a value on every path");
        }
        symbols.exitScope();
        currentFunction = previousFunction;
        currentClass = previousClass;
        checkingConstructor = previousChecking;
        sanctionedSuperCall = previousSuperCall;
        definitelyInitialized = previousInitialized;
        loopDepth = 0;
    }

    /** The {@code super(...)} call that must open an {@code init} body, or {@code null}. */
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
        // The superclass constructor runs before the subclass init body, so every inherited
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
            checkStatement(statement);
        }
        symbols.exitScope();
    }

    private void checkStatement(StatementNode statement) {
        switch (statement.kind()) {
            case LOCAL_DECL -> checkLocalDecl((LocalDeclNode) statement);
            case IF_STMT -> checkIf((IfStmtNode) statement);
            case WHILE_STMT -> checkWhile((WhileStmtNode) statement);
            case FOR_STMT -> checkFor((ForStmtNode) statement);
            case BREAK_STMT -> checkLoopControl(statement);
            case CONTINUE_STMT -> checkLoopControl(statement);
            case ASSIGN_STMT -> checkAssign((AssignStmtNode) statement);
            case RETURN_STMT -> checkReturn((ReturnStmtNode) statement);
            case EXPR_STMT -> checkExprStmt((ExprStmtNode) statement);
            default -> throw new IllegalStateException("not a statement kind: " + statement.kind());
        }
    }

    private void checkLocalDecl(LocalDeclNode declaration) {
        Type initializerType = checkExpression(declaration.initializer());
        Type declaredType = null;
        if (declaration.declaredType().isPresent()) {
            declaredType = resolveType(declaration.declaredType().get());
        }
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
    }

    private void checkIf(IfStmtNode statement) {
        Type condition = checkExpression(statement.condition());
        requireBoolean(condition, statement.condition());
        Set<PropertySymbol> before = copyInitialized();
        checkBlock(statement.thenBlock());
        Set<PropertySymbol> afterThen = copyInitialized();
        definitelyInitialized = before;
        if (statement.elseBranch().isPresent()) {
            checkElseBranch(statement.elseBranch().get());
        }
        Set<PropertySymbol> afterElse = copyInitialized();
        definitelyInitialized = intersection(afterThen, afterElse);
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
        Set<PropertySymbol> before = copyInitialized();
        loopDepth++;
        checkBlock(statement.body());
        loopDepth--;
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
        if (statement.condition().isPresent()) {
            Type condition = checkExpression(statement.condition().get());
            requireBoolean(condition, statement.condition().get());
        }
        loopDepth++;
        checkBlock(statement.body());
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
        definitelyInitialized = before;
        symbols.exitScope();
    }

    private void checkLoopControl(StatementNode statement) {
        if (loopDepth == 0) {
            error(DiagnosticCode.SEM_LOOP_CONTROL_OUTSIDE_LOOP, statement.span(), "'" + keywordOf(statement) + "' is only valid inside a loop");
        }
    }

    private static String keywordOf(StatementNode statement) {
        return statement instanceof BreakStmtNode ? "break" : "continue";
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
            Optional<Symbol> resolved = symbols.resolve(name.name());
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
            checkPropertyAssign(member, value, valueType);
            return;
        }
        error(DiagnosticCode.TYPE_INVALID_ASSIGNMENT_TARGET, target.span(), "assignment target must be a mutable local or a var property");
    }

    /** Checks a {@code receiver.member = value} assignment and enforces property mutability. */
    private void checkPropertyAssign(MemberAccessExprNode member, ExpressionNode value, Type valueType) {
        Type receiverType = checkExpression(member.receiver());
        if (!(receiverType instanceof ClassType classType)) {
            if (receiverType != null) {
                error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, member.span(), "type " + receiverType.name() + " has no member '" + member.memberName() + "'");
            }
            return;
        }
        ClassSymbol classSymbol = symbolsByType.get(classType);
        if (classSymbol == null) {
            return;
        }
        Optional<PropertySymbol> resolved = classSymbol.property(member.memberName());
        if (resolved.isEmpty()) {
            if (classSymbol.method(member.memberName()).isPresent()) {
                error(DiagnosticCode.TYPE_INVALID_ASSIGNMENT_TARGET, member.span(), "cannot assign to method '" + member.memberName() + "'");
            } else {
                error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, member.span(), "class " + classType.name() + " has no property '" + member.memberName() + "'");
            }
            return;
        }
        PropertySymbol property = resolved.get();
        propertyAccesses.put(member, property);
        if (valueType != null && !valueType.isAssignableTo(property.type())) {
            errorExpected(DiagnosticCode.TYPE_MISMATCH, value.span(), //
                            "value is not assignable to property type " + property.type().name(), property.type().name(), valueType.name());
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
            case MEMBER_ACCESS_EXPR:
                return record(expression, checkMemberAccess((MemberAccessExprNode) expression));
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
        Optional<Symbol> resolved = symbols.resolve(name.name());
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
        VariableSymbol variable = (VariableSymbol) symbol;
        nameSymbols.put(name, variable);
        if (!variable.isInitialized()) {
            error(DiagnosticCode.TYPE_UNINITIALIZED_VARIABLE, name.span(), "variable '" + name.name() + "' is read before it is initialized");
        }
        return variable.type();
    }

    private Type checkThis(ThisExprNode expression) {
        if (currentClass == null) {
            error(DiagnosticCode.RESOL_THIS_OUTSIDE_CLASS, expression.span(), "'this' is only valid inside an instance method or init");
            return null;
        }
        return currentClass.type();
    }

    /** {@code super} is never a value; it is legal only as a call or member receiver. */
    private Type checkSuperAsValue(SuperExprNode expression) {
        error(DiagnosticCode.SEM_SUPER_AS_VALUE, expression.span(), "'super' must be used as 'super(...)' or 'super.member'");
        return null;
    }

    /** Resolves the immediate superclass of the enclosing class, reporting when there is none. */
    private ClassSymbol requireSuperclass(SourceSpan span) {
        if (currentClass == null) {
            error(DiagnosticCode.RESOL_SUPER_OUTSIDE_CLASS, span, "'super' is only valid inside an instance method or init");
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
            case ARITHMETIC:
                if (expression.operator() == org.solvik.ast.expression.BinaryOperator.ADD && left == StringType.INSTANCE && right == StringType.INSTANCE) {
                    return StringType.INSTANCE;
                }
                if (NumericTypes.isNumeric(left) && left == right) {
                    return left;
                }
                invalidOperands(expression.span(), expression.operator().spelling(), "two operands of the same numeric type, or two Strings", left, right);
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
            case LOGICAL:
                if (left == BooleanType.INSTANCE && right == BooleanType.INSTANCE) {
                    return BooleanType.INSTANCE;
                }
                invalidOperands(expression.span(), expression.operator().spelling(), "Boolean, Boolean", left, right);
                return null;
            default:
                throw new IllegalStateException("unknown binary operator: " + expression.operator());
        }
    }

    private Type checkCall(CallExprNode expression) {
        ExpressionNode callee = expression.callee();
        if (callee instanceof SuperExprNode) {
            return checkSuperConstructorCall(expression);
        }
        if (callee instanceof NameRefExprNode name) {
            Optional<Symbol> resolved = symbols.resolve(name.name());
            if (resolved.isEmpty()) {
                Optional<Type> declaredType = typeEnvironment.resolve(name.name());
                if (declaredType.isPresent()) {
                    if (NumericTypes.isNumeric(declaredType.get())) {
                        return checkConversion(expression, declaredType.get());
                    }
                    error(DiagnosticCode.TYPE_INVALID_CONVERSION, name.span(), "'" + name.name() + "' is a type; only numeric types convert with a call");
                    return null;
                }
                if (currentClass != null) {
                    Optional<FunctionSymbol> method = currentClass.method(name.name());
                    if (method.isPresent()) {
                        return resolveMethodCall(expression, name, method.get(), true);
                    }
                }
                error(DiagnosticCode.RESOL_UNKNOWN_NAME, name.span(), "unknown name '" + name.name() + "'");
                return null;
            }
            Symbol symbol = resolved.get();
            if (symbol instanceof ClassSymbol classSymbol) {
                nameSymbols.put(name, classSymbol);
                expressionTypes.put(name, classSymbol.type());
                constructionArguments(expression, classSymbol);
                constructorCalls.put(expression, classSymbol);
                return classSymbol.type();
            }
            if (!(symbol instanceof FunctionSymbol function)) {
                error(DiagnosticCode.TYPE_NOT_CALLABLE, name.span(), "'" + name.name() + "' is not a function");
                return null;
            }
            nameSymbols.put(name, function);
            expressionTypes.put(name, function.functionType());
            checkArguments(expression, function.name(), function.parameters());
            return function.returnType();
        }
        if (callee instanceof MemberAccessExprNode member) {
            if (member.receiver() instanceof SuperExprNode) {
                return checkSuperMethodCall(expression, member);
            }
            return checkMethodCall(expression, member);
        }
        if (checkExpression(callee) != null) {
            error(DiagnosticCode.TYPE_NOT_CALLABLE, callee.span(), "expression is not callable");
        }
        return null;
    }

    /** Types {@code T(value)}, the explicit numeric conversion of docs/LANGUAGE_SPEC.md section 4. */
    private Type checkConversion(CallExprNode call, Type target) {
        List<ExpressionNode> arguments = call.arguments();
        if (arguments.size() != 1) {
            errorExpected(DiagnosticCode.TYPE_ARITY_MISMATCH, call.span(), //
                            "numeric conversion '" + target.name() + "' expects exactly one argument", "1", Integer.toString(arguments.size()));
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

    /** Validates the mandated {@code super(...)} call at the start of a subclass {@code init}. */
    private Type checkSuperConstructorCall(CallExprNode call) {
        ClassSymbol superClass = requireSuperclass(call.span());
        if (superClass == null) {
            return null;
        }
        if (!checkingConstructor || sanctionedSuperCall != call) {
            error(DiagnosticCode.SEM_SUPER_CALL_PLACEMENT, call.span(), "super(...) must be the first statement of init");
            return null;
        }
        List<VariableSymbol> parameters = superClass.constructor().map(FunctionSymbol::parameters).orElseGet(List::of);
        checkArguments(call, "super", parameters);
        superConstructorCalls.put(call, superClass);
        return UnitType.INSTANCE;
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
            error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, member.span(), "class " + superClass.name() + " has no method '" + member.memberName() + "'");
            return null;
        }
        FunctionSymbol target = method.get();
        expressionTypes.put(member, target.functionType());
        checkArguments(call, target.name(), target.parameters());
        methodCalls.put(call, new ResolvedMethod(target, true, true));
        return target.returnType();
    }

    private void constructionArguments(CallExprNode call, ClassSymbol classSymbol) {
        List<VariableSymbol> parameters = classSymbol.constructor().map(FunctionSymbol::parameters).orElseGet(List::of);
        checkArguments(call, classSymbol.name(), parameters);
    }

    private Type checkMethodCall(CallExprNode call, MemberAccessExprNode member) {
        Type receiverType = checkExpression(member.receiver());
        if (!(receiverType instanceof ClassType classType)) {
            if (receiverType != null) {
                error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, member.span(), "type " + receiverType.name() + " has no member '" + member.memberName() + "'");
            }
            return null;
        }
        ClassSymbol classSymbol = symbolsByType.get(classType);
        if (classSymbol == null) {
            return null;
        }
        if (classSymbol.property(member.memberName()).isPresent()) {
            error(DiagnosticCode.TYPE_NOT_CALLABLE, member.span(), "'" + member.memberName() + "' is not callable");
            return null;
        }
        Optional<FunctionSymbol> method = classSymbol.method(member.memberName());
        if (method.isEmpty()) {
            error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, member.span(), "class " + classType.name() + " has no method '" + member.memberName() + "'");
            return null;
        }
        FunctionSymbol target = method.get();
        expressionTypes.put(member, target.functionType());
        checkArguments(call, target.name(), target.parameters());
        methodCalls.put(call, new ResolvedMethod(target, false));
        return target.returnType();
    }

    private Type resolveMethodCall(CallExprNode call, NameRefExprNode calleeName, FunctionSymbol method, boolean implicitThis) {
        nameSymbols.put(calleeName, method);
        expressionTypes.put(calleeName, method.functionType());
        checkArguments(call, method.name(), method.parameters());
        methodCalls.put(call, new ResolvedMethod(method, implicitThis));
        return method.returnType();
    }

    private void checkArguments(CallExprNode call, String calleeName, List<VariableSymbol> parameters) {
        List<ExpressionNode> arguments = call.arguments();
        if (parameters.size() != arguments.size()) {
            errorExpected(DiagnosticCode.TYPE_ARITY_MISMATCH, call.span(), //
                            "call to '" + calleeName + "' has the wrong number of arguments", Integer.toString(parameters.size()), Integer.toString(arguments.size()));
        }
        int checked = Math.min(parameters.size(), arguments.size());
        for (int i = 0; i < checked; i++) {
            Type argumentType = checkExpression(arguments.get(i));
            Type parameterType = parameters.get(i).type();
            if (argumentType != null && !argumentType.isAssignableTo(parameterType)) {
                errorExpected(DiagnosticCode.TYPE_MISMATCH, arguments.get(i).span(), //
                                "argument " + (i + 1) + " of '" + calleeName + "' has the wrong type", parameterType.name(), argumentType.name());
            }
        }
        for (int i = checked; i < arguments.size(); i++) {
            checkExpression(arguments.get(i));
        }
    }

    private Type checkMemberAccess(MemberAccessExprNode expression) {
        if (expression.receiver() instanceof SuperExprNode) {
            return checkSuperMemberAccess(expression);
        }
        Type receiverType = checkExpression(expression.receiver());
        if (!(receiverType instanceof ClassType classType)) {
            if (receiverType != null) {
                error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, expression.span(), "type " + receiverType.name() + " has no member '" + expression.memberName() + "'");
            }
            return null;
        }
        ClassSymbol classSymbol = symbolsByType.get(classType);
        if (classSymbol == null) {
            return null;
        }
        Optional<PropertySymbol> property = classSymbol.property(expression.memberName());
        if (property.isPresent()) {
            PropertySymbol resolved = property.get();
            propertyAccesses.put(expression, resolved);
            if (checkingConstructor && !resolved.hasInitializer() && !definitelyInitialized.contains(resolved)) {
                error(DiagnosticCode.TYPE_UNINITIALIZED_PROPERTY, expression.span(), "property '" + resolved.name() + "' is read before it is initialized");
            }
            return resolved.type();
        }
        if (classSymbol.method(expression.memberName()).isPresent()) {
            error(DiagnosticCode.TYPE_FUNCTION_AS_VALUE, expression.span(), "method '" + expression.memberName() + "' cannot be used as a value");
        } else {
            error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, expression.span(), "class " + classType.name() + " has no member '" + expression.memberName() + "'");
        }
        return null;
    }

    /** Resolves {@code super.property}; inherited properties are already initialized by the superclass. */
    private Type checkSuperMemberAccess(MemberAccessExprNode expression) {
        ClassSymbol superClass = requireSuperclass(expression.span());
        if (superClass == null) {
            return null;
        }
        Optional<PropertySymbol> property = superClass.property(expression.memberName());
        if (property.isPresent()) {
            propertyAccesses.put(expression, property.get());
            return property.get().type();
        }
        if (superClass.method(expression.memberName()).isPresent()) {
            error(DiagnosticCode.TYPE_FUNCTION_AS_VALUE, expression.span(), "method '" + expression.memberName() + "' cannot be used as a value");
        } else {
            error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, expression.span(), "class " + superClass.name() + " has no member '" + expression.memberName() + "'");
        }
        return null;
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
    // Type resolution and control-flow helpers
    // ---------------------------------------------------------------------------------------------

    private Type resolveType(TypeRefNode reference) {
        Optional<Type> resolved = typeEnvironment.resolve(reference.name());
        if (resolved.isEmpty()) {
            errorExpected(DiagnosticCode.RESOL_UNKNOWN_TYPE, reference.span(), //
                            "unknown type '" + reference.name() + "'", "a declared or built-in type", "'" + reference.name() + "'");
            return null;
        }
        return resolved.get();
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
