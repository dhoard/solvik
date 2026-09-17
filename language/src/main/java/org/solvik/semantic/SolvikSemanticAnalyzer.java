/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
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
import org.solvik.ast.declaration.InitDeclNode;
import org.solvik.ast.declaration.InterfaceDeclNode;
import org.solvik.ast.declaration.ParameterNode;
import org.solvik.ast.declaration.PropertyDeclNode;
import org.solvik.ast.declaration.SignatureDeclNode;
import org.solvik.ast.declaration.TypeParameterNode;
import org.solvik.ast.declaration.TypeRefNode;
import org.solvik.ast.expression.BinaryExprNode;
import org.solvik.ast.expression.BinaryOperator;
import org.solvik.ast.expression.BoolLiteralNode;
import org.solvik.ast.expression.CallExprNode;
import org.solvik.ast.expression.CastExprNode;
import org.solvik.ast.expression.CharLiteralNode;
import org.solvik.ast.expression.ExpressionNode;
import org.solvik.ast.expression.FloatingLiteralNode;
import org.solvik.ast.expression.IntLiteralNode;
import org.solvik.ast.expression.LongLiteralNode;
import org.solvik.ast.expression.MatchBranchNode;
import org.solvik.ast.expression.MatchExprNode;
import org.solvik.ast.expression.MemberAccessExprNode;
import org.solvik.ast.expression.NameRefExprNode;
import org.solvik.ast.expression.NullLiteralNode;
import org.solvik.ast.expression.ParenExprNode;
import org.solvik.ast.expression.RawStringLiteralNode;
import org.solvik.ast.expression.StringLiteralNode;
import org.solvik.ast.expression.SuperExprNode;
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
import org.solvik.type.IntType;
import org.solvik.type.ListType;
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
    private final Map<String, InterfaceSymbol> interfaces = new LinkedHashMap<>();
    private final Map<String, EnumSymbol> enums = new LinkedHashMap<>();
    private final Map<FunctionDeclNode, FunctionSymbol> declaredFunctions = new IdentityHashMap<>();
    private final Map<ClassDeclNode, ClassSymbol> declaredClasses = new IdentityHashMap<>();
    private final Map<ClassDeclNode, ClassType> classTypes = new IdentityHashMap<>();
    private final Map<EnumDeclNode, EnumSymbol> declaredEnums = new IdentityHashMap<>();
    private final Map<EnumDeclNode, EnumType> enumTypes = new IdentityHashMap<>();
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
        return SemanticResult.success(new CheckedProgram(unit, analyzer.functions, analyzer.classes, analyzer.interfaces, analyzer.enums, analyzer.declaredClasses, analyzer.declaredInterfaces, analyzer.declaredEnums, analyzer.expressionTypes, analyzer.localSymbols, analyzer.nameSymbols, analyzer.propertyAccesses, analyzer.constructorCalls, analyzer.methodCalls, analyzer.conversions, analyzer.testedTypes, analyzer.superConstructorCalls, analyzer.variantConstructions, analyzer.regexConstants, analyzer.enumPatterns, analyzer.patternBindings, analyzer.patternBindingTypes, analyzer.entryPoint));
    }

    // ---------------------------------------------------------------------------------------------
    // Declaration collection
    // ---------------------------------------------------------------------------------------------

    private void collectDeclarations(CompilationUnitNode unit) {
        declareBuiltins();
        // Pass A: register every class's and interface's nominal type first so any declaration order
        // may reference a nominal type by name in property, parameter, return, and local types.
        for (DeclarationNode declaration : unit.declarations()) {
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
        FunctionSymbol main = functions.get("main");
        if (main != null && main.parameters().isEmpty() && main.isReturnTypeKnown() && main.returnType() == UnitType.INSTANCE) {
            entryPoint = main;
        }
    }

    /** Registers a user-declared nominal type, rejecting a duplicate or built-in name shadow. */
    private void declareNominalType(Type type, SourceSpan span) {
        if (typeEnvironment.resolve(type.name()).isPresent()) {
            error(DiagnosticCode.RESOL_DUPLICATE_NAME, span, "type name '" + type.name() + "' is already declared");
        } else {
            typeEnvironment.declare(type);
        }
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
        declareBuiltin("print");
        declareBuiltin("println");
    }

    private void declareBuiltin(String name) {
        FunctionSymbol symbol = FunctionSymbol.builtin(name, List.of(AnyType.INSTANCE), UnitType.INSTANCE);
        symbols.declare(symbol);
        functions.put(name, symbol);
    }

    private void collectFunction(FunctionDeclNode declaration) {
        List<TypeParameterType> typeParameters = declareTypeParameters(declaration.typeParameters());
        Map<String, TypeParameterType> previousScope = typeParameterScope;
        typeParameterScope = scopeOf(typeParameters);
        List<VariableSymbol> parameters = buildParameters(declaration.parameters());
        Type returnType = resolveType(declaration.returnType());
        typeParameterScope = previousScope;
        FunctionSymbol functionSymbol = new FunctionSymbol(declaration.name(), declaration.span(), parameters, typeParameters, //
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

    /**
     * Collects one interface declaration: its members in source order (abstract signatures and
     * default methods) and its already-resolved extended interfaces. Duplicate member names within
     * one interface are rejected; a name a class must later resolve across interfaces is not.
     */
    private void collectInterface(InterfaceDeclNode declaration, InterfaceType type) {
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
        if (!symbols.declare(interfaceSymbol)) {
            error(DiagnosticCode.RESOL_DUPLICATE_NAME, declaration.span(), "interface '" + declaration.name() + "' is already declared");
        } else {
            interfaces.put(declaration.name(), interfaceSymbol);
        }
    }

    /**
     * Collects one enum declaration: its variants in source order and their positional value types,
     * resolved in the enum's own type-parameter scope. Duplicate variant names are rejected. The
     * complete variant list installed here is the closed-variant metadata of
     * docs/LANGUAGE_SPEC.md section 12.
     */
    private void collectEnum(EnumDeclNode declaration, EnumType type) {
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
        if (!symbols.declare(enumSymbol)) {
            error(DiagnosticCode.RESOL_DUPLICATE_NAME, declaration.span(), "enum '" + declaration.name() + "' is already declared");
        } else {
            enums.put(declaration.name(), enumSymbol);
        }
    }

    private void collectClass(ClassDeclNode declaration, ClassType type) {
        Map<String, TypeParameterType> previousScope = typeParameterScope;
        typeParameterScope = scopeOf(classTypeParameters.getOrDefault(declaration, List.of()));
        ClassDeclNode superDeclaration = superDeclarations.get(declaration);
        ClassSymbol superSymbol = superDeclaration == null ? null : declaredClasses.get(superDeclaration);
        if (superDeclaration != null && !superDeclaration.isSealed() && !superDeclaration.isOpen()) {
            errorExpected(DiagnosticCode.SEM_EXTEND_FINAL, declaration.superClass().orElseThrow().span(), //
                            "class '" + declaration.name() + "' cannot extend final class", "an open or sealed class", superDeclaration.name());
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
                if (propertyNames.add(property.name())) {
                    properties.add(new PropertySymbol(property.name(), property.span(), propertyType != null ? propertyType : AnyType.INSTANCE, //
                                    property.bindingKind() == BindingKind.VAR, property.initializer().isPresent(), index));
                } else {
                    error(DiagnosticCode.RESOL_DUPLICATE_NAME, property.span(), "property '" + property.name() + "' is already declared");
                }
                index++;
            } else if (member instanceof DelegateDeclNode delegate) {
                Type declaredType = resolveType(delegate.declaredType());
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
        if (!symbols.declare(classSymbol)) {
            error(DiagnosticCode.RESOL_DUPLICATE_NAME, declaration.span(), "class '" + declaration.name() + "' is already declared");
        } else {
            classes.put(declaration.name(), classSymbol);
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
            } else if (declaration instanceof InterfaceDeclNode interfaceDeclaration) {
                checkInterface(declaredInterfaces.get(interfaceDeclaration));
            }
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
            checkCallable(constructor, constructor.initDeclaration().body(), classSymbol, true);
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
                error(DiagnosticCode.SEM_MISSING_SUPER_INIT, body.span(), "class '" + owner.name() + "' must call super(...) as the first statement of init");
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
        checkBlock(statement.body());
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
        restoreNarrowing(narrowingBefore);
        dropWrittenSince(writtenBefore);
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
        if (listElementType(receiverType) != null) {
            if ("size".equals(member.memberName())) {
                error(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE, member.span(), "cannot assign to immutable List 'size'");
            } else {
                error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, member.span(), "type " + receiverType.name() + " has no property '" + member.memberName() + "'");
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
            case TYPE_TEST_EXPR:
                return record(expression, checkTypeTest((TypeTestExprNode) expression));
            case CAST_EXPR:
                return record(expression, checkCast((CastExprNode) expression));
            case MEMBER_ACCESS_EXPR:
                return record(expression, checkMemberAccess((MemberAccessExprNode) expression));
            case MATCH_EXPR:
                return record(expression, checkMatch((MatchExprNode) expression));
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
        error(DiagnosticCode.RESOL_THIS_OUTSIDE_CLASS, expression.span(), "'this' is only valid inside an instance method or init");
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
            case COALESCE:
                return checkCoalesce(expression, left, right);
            default:
                throw new IllegalStateException("unknown binary operator: " + expression.operator());
        }
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
            Optional<Symbol> resolved = symbols.resolve(name.name());
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

    /**
     * Types {@code Regex(pattern)}, the built-in regular-expression constructor
     * (docs/LANGUAGE_SPEC.md section 14). The argument must be a {@code String}. When it is a
     * source constant the pattern is validated against the portable dialect and compiled exactly
     * once here, so an invalid or unsupported constant is a compile-time diagnostic and lowering
     * reuses the compiled pattern. A dynamically computed pattern is validated at run time instead.
     */
    private Type checkRegexConstruction(CallExprNode call) {
        List<Type> argumentTypes = checkArgumentTypes(call);
        checkArgumentTypesAgainst(call, "Regex", List.of(StringType.INSTANCE), argumentTypes);
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
            error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, member.span(), "class " + superClass.name() + " has no method '" + member.memberName() + "'");
            return null;
        }
        FunctionSymbol target = method.get();
        expressionTypes.put(member, target.functionType());
        Type result = resolveCallableType(call, target.name(), target, composeSubstitutions(superClass.methodSubstitution(target.name()), superTypeSubstitution()));
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
        List<TypeParameterType> typeParameters = classSymbol.type().typeParameters();
        Map<TypeParameterType, Type> substitution = inferCallableTypeArguments(call, typeParameters, declaredParameterTypes, argumentTypes);
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
        Symbol symbol = symbols.resolve(name.name()).orElse(null);
        return symbol instanceof EnumSymbol enumSymbol ? enumSymbol : null;
    }

    /**
     * Types a qualified enum variant construction {@code Enum.Variant(values...)}
     * (docs/LANGUAGE_SPEC.md section 12). The variant's value types are substituted with the enum
     * type arguments inferred from the values, and the values are checked against the substituted
     * types. The result is the owning enum type, possibly a generic application.
     */
    private Type checkVariantConstruction(CallExprNode call, MemberAccessExprNode member, EnumSymbol enumSymbol) {
        Optional<EnumVariantSymbol> resolved = enumSymbol.variant(member.memberName());
        if (resolved.isEmpty()) {
            for (ExpressionNode argument : call.arguments()) {
                checkExpression(argument);
            }
            error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, member.span(), "enum " + enumSymbol.name() + " has no variant '" + member.memberName() + "'");
            return null;
        }
        EnumVariantSymbol variant = resolved.get();
        List<Type> declaredValueTypes = variant.valueTypes();
        List<Type> argumentTypes = checkArgumentTypes(call);
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
        Optional<EnumVariantSymbol> resolved = enumSymbol.variant(expression.memberName());
        if (resolved.isEmpty()) {
            error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, expression.span(), "enum " + enumSymbol.name() + " has no variant '" + expression.memberName() + "'");
            return null;
        }
        EnumVariantSymbol variant = resolved.get();
        if (!variant.valueTypes().isEmpty()) {
            errorExpected(DiagnosticCode.TYPE_ARITY_MISMATCH, expression.span(), //
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

    /** The construction type of an enum: the bare enum or its application to the inferred arguments. */
    private static Type enumConstructionType(EnumType enumType, List<TypeParameterType> typeParameters, Map<TypeParameterType, Type> substitution) {
        if (typeParameters.isEmpty()) {
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
        Type element = listElementType(receiverType);
        if (element != null) {
            if ("get".equals(member.memberName())) {
                checkArguments(call, "get", List.of(IntType.INSTANCE));
                return element;
            }
            if ("size".equals(member.memberName())) {
                error(DiagnosticCode.TYPE_NOT_CALLABLE, member.span(), "'size' is a property, not a method");
            } else {
                error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, member.span(), "List has no member '" + member.memberName() + "'");
            }
            return null;
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
        Type result = resolveCallableType(call, target.name(), target, composeSubstitutions(classSymbol.methodSubstitution(member.memberName()), substitutionFor(receiverType)));
        methodCalls.put(call, new ResolvedMethod(target, false));
        return result;
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
                return ListType.INSTANCE.parameterizedView(List.of(RegexMatchType.INSTANCE));
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
        Type result = resolveCallableType(call, method.name(), method, substitutionFor(interfaceType));
        methodCalls.put(call, new ResolvedMethod(method, false));
        return result;
    }

    private Type resolveMethodCall(CallExprNode call, NameRefExprNode calleeName, FunctionSymbol method, boolean implicitThis) {
        nameSymbols.put(calleeName, method);
        expressionTypes.put(calleeName, method.functionType());
        Type result = resolveCallableType(call, method.name(), method, Map.of());
        methodCalls.put(call, new ResolvedMethod(method, implicitThis));
        return result;
    }

    /**
     * Checks a call's arguments against a callable and returns the call's result type. The callable's
     * own type parameters are inferred from the argument types, then its parameter and return types
     * are substituted with the receiver and inferred substitutions (docs/LANGUAGE_SPEC.md section 11).
     */
    private Type resolveCallableType(CallExprNode call, String calleeName, FunctionSymbol target, Map<TypeParameterType, Type> receiverSubstitution) {
        List<Type> argumentTypes = checkArgumentTypes(call);
        List<Type> declaredParameterTypes = substitutedParameterTypes(target.parameters(), receiverSubstitution);
        Map<TypeParameterType, Type> methodSubstitution = inferCallableTypeArguments(call, target.typeParameters(), declaredParameterTypes, argumentTypes);
        checkArgumentTypesAgainst(call, calleeName, substitutedTypes(declaredParameterTypes, methodSubstitution), argumentTypes);
        return target.returnType().substitute(receiverSubstitution).substitute(methodSubstitution);
    }

    /** Checks every argument expression once and returns its static type, in order. */
    private List<Type> checkArgumentTypes(CallExprNode call) {
        List<Type> types = new ArrayList<>(call.arguments().size());
        for (ExpressionNode argument : call.arguments()) {
            types.add(checkExpression(argument));
        }
        return types;
    }

    private void checkArguments(CallExprNode call, String calleeName, List<Type> parameterTypes) {
        checkArgumentTypesAgainst(call, calleeName, parameterTypes, checkArgumentTypes(call));
    }

    private void checkArgumentTypesAgainst(CallExprNode call, String calleeName, List<Type> parameterTypes, List<Type> argumentTypes) {
        if (parameterTypes.size() != argumentTypes.size()) {
            errorExpected(DiagnosticCode.TYPE_ARITY_MISMATCH, call.span(), //
                            "call to '" + calleeName + "' has the wrong number of arguments", Integer.toString(parameterTypes.size()), Integer.toString(argumentTypes.size()));
        }
        int checked = Math.min(parameterTypes.size(), argumentTypes.size());
        for (int i = 0; i < checked; i++) {
            Type argumentType = argumentTypes.get(i);
            Type parameterType = parameterTypes.get(i);
            if (argumentType != null && !argumentType.isAssignableTo(parameterType)) {
                errorExpected(DiagnosticCode.TYPE_MISMATCH, call.arguments().get(i).span(), //
                                "argument " + (i + 1) + " of '" + calleeName + "' has the wrong type", parameterType.name(), argumentType.name());
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
        Type element = listElementType(receiverType);
        if (element != null) {
            if ("size".equals(expression.memberName())) {
                return IntType.INSTANCE;
            }
            if ("get".equals(expression.memberName())) {
                error(DiagnosticCode.TYPE_FUNCTION_AS_VALUE, expression.span(), "method 'get' cannot be used as a value");
            } else {
                error(DiagnosticCode.RESOL_UNKNOWN_MEMBER, expression.span(), "List has no member '" + expression.memberName() + "'");
            }
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
     * The nearest common declared supertype of the branch result types: the most specific type that
     * every result is assignable to. Nullability is folded in, so {@code null} joined with
     * {@code String} is {@code String?}, and a set with no single nearest supertype is ill-typed.
     */
    private static Type nearestCommonSupertype(List<Type> types) {
        if (types.isEmpty()) {
            return null;
        }
        Type result = types.get(0);
        for (int i = 1; i < types.size(); i++) {
            result = joinTypes(result, types.get(i));
            if (result == null) {
                return null;
            }
        }
        return result;
    }

    /** The join of two result types under the declared hierarchy; {@code null} when none exists. */
    private static Type joinTypes(Type a, Type b) {
        if (a == b) {
            return a;
        }
        if (a == NullType.INSTANCE) {
            return b.nullableView();
        }
        if (b == NullType.INSTANCE) {
            return a.nullableView();
        }
        boolean nullable = a.isNullable() || b.isNullable();
        Type left = a.nonNullType();
        Type right = b.nonNullType();
        Type joined;
        if (left == right) {
            joined = left;
        } else if (left.isAssignableTo(right)) {
            joined = right;
        } else if (right.isAssignableTo(left)) {
            joined = left;
        } else {
            Set<Type> leftSupers = supertypesOf(left);
            List<Type> common = new ArrayList<>();
            for (Type candidate : supertypesOf(right)) {
                if (leftSupers.contains(candidate)) {
                    common.add(candidate);
                }
            }
            Type minimal = null;
            for (Type candidate : common) {
                boolean mostSpecific = true;
                for (Type other : common) {
                    if (other != candidate && !candidate.isSubtypeOf(other)) {
                        mostSpecific = false;
                        break;
                    }
                }
                if (mostSpecific) {
                    if (minimal != null) {
                        return null;
                    }
                    minimal = candidate;
                }
            }
            joined = minimal;
        }
        if (joined == null) {
            return null;
        }
        return nullable ? joined.nullableView() : joined;
    }

    /** The reflexive-transitive closure of a type's declared supertypes and interface edges. */
    private static Set<Type> supertypesOf(Type type) {
        Set<Type> result = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Type> frontier = new ArrayDeque<>();
        frontier.add(type);
        while (!frontier.isEmpty()) {
            Type current = frontier.removeFirst();
            if (!result.add(current)) {
                continue;
            }
            current.superType().ifPresent(frontier::addLast);
            for (Type face : current.interfaceTypes()) {
                frontier.addLast(face);
            }
        }
        return result;
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
        if (condition instanceof BinaryExprNode binary && (binary.operator() == BinaryOperator.EQ || binary.operator() == BinaryOperator.NEQ)) {
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
            boolean equality = binary.operator() == BinaryOperator.EQ;
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
        TypeParameterType parameter = typeParameterScope.get(reference.name());
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
            Optional<Type> resolved = typeEnvironment.resolve(reference.name());
            if (resolved.isEmpty()) {
                errorExpected(DiagnosticCode.RESOL_UNKNOWN_TYPE, reference.span(), //
                                "unknown type '" + reference.name() + "'", "a declared or built-in type", "'" + reference.name() + "'");
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
            return enums.get(enumType.name());
        }
        return null;
    }

    /**
     * The element type of a {@code List<T>} receiver, or {@code null} when the receiver is not a
     * generic application of the built-in {@code List} (docs/LANGUAGE_SPEC.md section 11).
     */
    private static Type listElementType(Type type) {
        if (type instanceof ParameterizedType parameterized && parameterized.base() == ListType.INSTANCE) {
            return parameterized.arguments().get(0);
        }
        return null;
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
