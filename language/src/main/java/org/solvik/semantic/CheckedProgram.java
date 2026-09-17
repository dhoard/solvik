/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.semantic;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.ClassDeclNode;
import org.solvik.ast.declaration.EnumDeclNode;
import org.solvik.ast.declaration.InterfaceDeclNode;
import org.solvik.ast.expression.CallExprNode;
import org.solvik.ast.expression.ExpressionNode;
import org.solvik.ast.expression.MemberAccessExprNode;
import org.solvik.ast.expression.NameRefExprNode;
import org.solvik.ast.pattern.BindingPatternNode;
import org.solvik.ast.pattern.EnumPatternNode;
import org.solvik.ast.statement.LocalDeclNode;
import org.solvik.ast.statement.RegexCaseLabelNode;
import org.solvik.regex.RegexPattern;
import org.solvik.type.Type;

/**
 * A fully typed semantic result: the syntax AST plus the compiler facts computed for it, with no
 * runtime or Truffle representation. This is the compile-time boundary that typed lowering consumes
 * in a later phase.
 *
 * <p>Every value-producing expression that type checking succeeded on has exactly one recorded
 * type. Declaration facts are exposed by identity so a caller can map a syntax node back to the
 * symbol it introduced. Member accesses additionally record the statically resolved property, and
 * call expressions record construction or method resolution, so lowering never re-resolves.
 */
public final class CheckedProgram {

    private final CompilationUnitNode unit;
    private final Map<String, FunctionSymbol> functions;
    private final Map<String, ClassSymbol> classes;
    private final Map<String, InterfaceSymbol> interfaces;
    private final Map<String, EnumSymbol> enums;
    private final Map<ClassDeclNode, ClassSymbol> classDeclarations;
    private final Map<InterfaceDeclNode, InterfaceSymbol> interfaceDeclarations;
    private final Map<EnumDeclNode, EnumSymbol> enumDeclarations;
    private final Map<ExpressionNode, Type> expressionTypes;
    private final Map<LocalDeclNode, VariableSymbol> localSymbols;
    private final Map<NameRefExprNode, Symbol> nameSymbols;
    private final Map<MemberAccessExprNode, PropertySymbol> propertyAccesses;
    private final Map<CallExprNode, ClassSymbol> constructorCalls;
    private final Map<CallExprNode, ResolvedMethod> methodCalls;
    private final Map<CallExprNode, Type> conversions;
    private final Map<ExpressionNode, Type> testedTypes;
    private final Map<CallExprNode, ClassSymbol> superConstructorCalls;
    private final Map<ExpressionNode, EnumVariantSymbol> variantConstructions;
    private final Map<CallExprNode, RegexPattern> regexConstants;
    private final Map<EnumPatternNode, EnumVariantSymbol> enumPatterns;
    private final Map<BindingPatternNode, VariableSymbol> patternBindings;
    private final Map<BindingPatternNode, Type> patternBindingTypes;
    private final Map<RegexCaseLabelNode, RegexPattern> regexCasePatterns;
    private final FunctionSymbol entryPoint;

    CheckedProgram(CompilationUnitNode unit, Map<String, FunctionSymbol> functions, Map<String, ClassSymbol> classes, Map<String, InterfaceSymbol> interfaces, Map<String, EnumSymbol> enums, Map<ClassDeclNode, ClassSymbol> classDeclarations, Map<InterfaceDeclNode, InterfaceSymbol> interfaceDeclarations, Map<EnumDeclNode, EnumSymbol> enumDeclarations, Map<ExpressionNode, Type> expressionTypes, Map<LocalDeclNode, VariableSymbol> localSymbols, Map<NameRefExprNode, Symbol> nameSymbols, Map<MemberAccessExprNode, PropertySymbol> propertyAccesses, Map<CallExprNode, ClassSymbol> constructorCalls, Map<CallExprNode, ResolvedMethod> methodCalls, Map<CallExprNode, Type> conversions, Map<ExpressionNode, Type> testedTypes, Map<CallExprNode, ClassSymbol> superConstructorCalls, Map<ExpressionNode, EnumVariantSymbol> variantConstructions, Map<CallExprNode, RegexPattern> regexConstants, Map<EnumPatternNode, EnumVariantSymbol> enumPatterns, Map<BindingPatternNode, VariableSymbol> patternBindings, Map<BindingPatternNode, Type> patternBindingTypes, Map<RegexCaseLabelNode, RegexPattern> regexCasePatterns, FunctionSymbol entryPoint) {
        this.unit = Objects.requireNonNull(unit);
        this.functions = Collections.unmodifiableMap(new LinkedHashMap<>(functions));
        this.classes = Collections.unmodifiableMap(new LinkedHashMap<>(classes));
        this.interfaces = Collections.unmodifiableMap(new LinkedHashMap<>(interfaces));
        this.enums = Collections.unmodifiableMap(new LinkedHashMap<>(enums));
        this.classDeclarations = Collections.unmodifiableMap(new IdentityHashMap<>(classDeclarations));
        this.interfaceDeclarations = Collections.unmodifiableMap(new IdentityHashMap<>(interfaceDeclarations));
        this.enumDeclarations = Collections.unmodifiableMap(new IdentityHashMap<>(enumDeclarations));
        this.expressionTypes = Collections.unmodifiableMap(new IdentityHashMap<>(expressionTypes));
        this.localSymbols = Collections.unmodifiableMap(new IdentityHashMap<>(localSymbols));
        this.nameSymbols = Collections.unmodifiableMap(new IdentityHashMap<>(nameSymbols));
        this.propertyAccesses = Collections.unmodifiableMap(new IdentityHashMap<>(propertyAccesses));
        this.constructorCalls = Collections.unmodifiableMap(new IdentityHashMap<>(constructorCalls));
        this.methodCalls = Collections.unmodifiableMap(new IdentityHashMap<>(methodCalls));
        this.conversions = Collections.unmodifiableMap(new IdentityHashMap<>(conversions));
        this.testedTypes = Collections.unmodifiableMap(new IdentityHashMap<>(testedTypes));
        this.superConstructorCalls = Collections.unmodifiableMap(new IdentityHashMap<>(superConstructorCalls));
        this.variantConstructions = Collections.unmodifiableMap(new IdentityHashMap<>(variantConstructions));
        this.regexConstants = Collections.unmodifiableMap(new IdentityHashMap<>(regexConstants));
        this.enumPatterns = Collections.unmodifiableMap(new IdentityHashMap<>(enumPatterns));
        this.patternBindings = Collections.unmodifiableMap(new IdentityHashMap<>(patternBindings));
        this.patternBindingTypes = Collections.unmodifiableMap(new IdentityHashMap<>(patternBindingTypes));
        this.regexCasePatterns = Collections.unmodifiableMap(new IdentityHashMap<>(regexCasePatterns));
        this.entryPoint = entryPoint;
    }

    public CompilationUnitNode unit() {
        return unit;
    }

    /** Top-level functions in declaration order, keyed by name. */
    public Map<String, FunctionSymbol> functions() {
        return functions;
    }

    public Optional<FunctionSymbol> function(String name) {
        return Optional.ofNullable(functions.get(name));
    }

    /** Declared classes in declaration order, keyed by name. */
    public Map<String, ClassSymbol> classes() {
        return classes;
    }

    public Optional<ClassSymbol> classSymbol(String name) {
        return Optional.ofNullable(classes.get(name));
    }

    /** The class symbol introduced by a class declaration. */
    public Optional<ClassSymbol> classOf(ClassDeclNode declaration) {
        return Optional.ofNullable(classDeclarations.get(declaration));
    }

    /** Declared interfaces in declaration order, keyed by name. */
    public Map<String, InterfaceSymbol> interfaces() {
        return interfaces;
    }

    public Optional<InterfaceSymbol> interfaceSymbol(String name) {
        return Optional.ofNullable(interfaces.get(name));
    }

    /** The interface symbol introduced by an interface declaration. */
    public Optional<InterfaceSymbol> interfaceOf(InterfaceDeclNode declaration) {
        return Optional.ofNullable(interfaceDeclarations.get(declaration));
    }

    /** Declared enums in declaration order, keyed by name. */
    public Map<String, EnumSymbol> enums() {
        return enums;
    }

    public Optional<EnumSymbol> enumSymbol(String name) {
        return Optional.ofNullable(enums.get(name));
    }

    /** The enum symbol introduced by an enum declaration. */
    public Optional<EnumSymbol> enumOf(EnumDeclNode declaration) {
        return Optional.ofNullable(enumDeclarations.get(declaration));
    }

    /** The validated {@code fun main(): Unit} entry point, when the source declares one. */
    public Optional<FunctionSymbol> entryPoint() {
        return Optional.ofNullable(entryPoint);
    }

    /** The static type of a value-producing expression; empty only when checking did not succeed. */
    public Optional<Type> typeOf(ExpressionNode expression) {
        return Optional.ofNullable(expressionTypes.get(expression));
    }

    /** The variable symbol introduced by a local declaration. */
    public Optional<VariableSymbol> symbolOf(LocalDeclNode declaration) {
        return Optional.ofNullable(localSymbols.get(declaration));
    }

    /**
     * The symbol a name reference resolved to during analysis: a {@link VariableSymbol} for a read
     * or assignment target, a {@link FunctionSymbol} for a function or method callee, or a
     * {@link ClassSymbol} for a class named as a constructor.
     */
    public Optional<Symbol> symbolOf(NameRefExprNode name) {
        return Optional.ofNullable(nameSymbols.get(name));
    }

    /** The property statically resolved for a member access used as a value or assignment target. */
    public Optional<PropertySymbol> propertyOf(MemberAccessExprNode member) {
        return Optional.ofNullable(propertyAccesses.get(member));
    }

    /** The class constructed by a call expression, when the callee names a class. */
    public Optional<ClassSymbol> constructorOf(CallExprNode call) {
        return Optional.ofNullable(constructorCalls.get(call));
    }

    /** The statically resolved method call, including whether the receiver is an implicit {@code this}. */
    public Optional<ResolvedMethod> methodOf(CallExprNode call) {
        return Optional.ofNullable(methodCalls.get(call));
    }

    /** The target numeric type of an explicit numeric conversion call {@code T(value)}. */
    public Optional<Type> conversionOf(CallExprNode call) {
        return Optional.ofNullable(conversions.get(call));
    }

    /**
     * The written target type of a type test {@code value is T} or a checked cast
     * {@code value as T}. The expression's own type is {@code Boolean} for a test and {@code T} for
     * a cast, so the target is recorded separately for lowering's runtime type check.
     */
    public Optional<Type> testedTypeOf(ExpressionNode expression) {
        return Optional.ofNullable(testedTypes.get(expression));
    }

    /** The superclass constructed by a {@code super(...)} call in an {@code init}. */
    public Optional<ClassSymbol> superConstructorOf(CallExprNode call) {
        return Optional.ofNullable(superConstructorCalls.get(call));
    }

    /** The enum variant constructed by a qualified variant call or value-less variant read. */
    public Optional<EnumVariantSymbol> variantOf(ExpressionNode expression) {
        return Optional.ofNullable(variantConstructions.get(expression));
    }

    /**
     * The compiled constant of a {@code Regex(pattern)} construction whose pattern is a source
     * constant. Static analysis compiled it once, so lowering can reuse the same compiled pattern
     * for every execution; a dynamically constructed pattern is absent.
     */
    public Optional<RegexPattern> regexConstantOf(ExpressionNode expression) {
        return Optional.ofNullable(regexConstants.get(expression));
    }

    /** An identity map from typed expressions to their static types, for whole-program traversal. */
    public Map<ExpressionNode, Type> expressionTypes() {
        return expressionTypes;
    }

    /** An identity map from local declarations to the symbols they introduce. */
    public Map<LocalDeclNode, VariableSymbol> localSymbols() {
        return localSymbols;
    }

    /** An identity map from name references to the symbols they resolved to. */
    public Map<NameRefExprNode, Symbol> nameSymbols() {
        return nameSymbols;
    }

    /** An identity map from member accesses to the properties they resolved to. */
    public Map<MemberAccessExprNode, PropertySymbol> propertyAccesses() {
        return propertyAccesses;
    }

    /** An identity map from call expressions to the classes they construct. */
    public Map<CallExprNode, ClassSymbol> constructorCalls() {
        return constructorCalls;
    }

    /** An identity map from call expressions to the methods they invoke. */
    public Map<CallExprNode, ResolvedMethod> methodCalls() {
        return methodCalls;
    }

    /** An identity map from call expressions to the explicit numeric conversion target types. */
    public Map<CallExprNode, Type> conversions() {
        return conversions;
    }

    /** An identity map from {@code super(...)} calls to the superclass they construct. */
    public Map<CallExprNode, ClassSymbol> superConstructorCalls() {
        return superConstructorCalls;
    }

    /** An identity map from enum variant constructions to the variant they construct. */
    public Map<ExpressionNode, EnumVariantSymbol> variantConstructions() {
        return variantConstructions;
    }

    /** An identity map from constant {@code Regex} constructions to their compiled patterns. */
    public Map<CallExprNode, RegexPattern> regexConstants() {
        return regexConstants;
    }

    /** The enum variant a {@code match} variant pattern destructures, when it resolved. */
    public Optional<EnumVariantSymbol> enumPatternOf(EnumPatternNode pattern) {
        return Optional.ofNullable(enumPatterns.get(pattern));
    }

    /** The variable a {@code match} binding pattern introduces. */
    public Optional<VariableSymbol> patternBindingOf(BindingPatternNode pattern) {
        return Optional.ofNullable(patternBindings.get(pattern));
    }

    /**
     * The written subtype of a {@code name: Type} binding pattern, present only for a typed binding
     * that resolved; a bare variant binding has no runtime subtype test and is absent.
     */
    public Optional<Type> bindingTypeOf(BindingPatternNode pattern) {
        return Optional.ofNullable(patternBindingTypes.get(pattern));
    }

    /**
     * The compiled constant of a {@code switch} regex case label. Static analysis compiled it once,
     * so lowering builds one runtime {@code Regex} value reused for every execution; an invalid
     * pattern has already been reported and never reaches lowering.
     */
    public Optional<RegexPattern> regexCasePatternOf(RegexCaseLabelNode label) {
        return Optional.ofNullable(regexCasePatterns.get(label));
    }

    /** Convenience: the declared functions as a list in declaration order. */
    public List<FunctionSymbol> functionList() {
        return List.copyOf(functions.values());
    }
}
