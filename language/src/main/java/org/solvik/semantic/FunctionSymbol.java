/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.semantic;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.solvik.ast.declaration.ClassDeclNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.declaration.InitDeclNode;
import org.solvik.source.SourceSpan;
import org.solvik.type.FunctionType;
import org.solvik.type.Type;
import org.solvik.type.UnitType;

/**
 * A declared callable: a top-level function, a class instance method, or a class {@code init}
 * constructor. It carries its parameter bindings, its return type, its syntax declaration, and (for
 * a method) its {@code open}/{@code override} modifiers. A method additionally records its owning
 * class; a constructor records its {@link InitDeclNode} instead of a {@link FunctionDeclNode}.
 */
public final class FunctionSymbol extends Symbol {

    private final List<VariableSymbol> parameters;
    private final Type returnType;
    private final boolean returnTypeKnown;
    private final FunctionDeclNode declaration;
    private final InitDeclNode initDeclaration;
    private final ClassDeclNode owner;
    private final FunctionType functionType;
    private final boolean builtin;
    private final boolean open;
    private final boolean override;

    FunctionSymbol(String name, SourceSpan declarationSpan, List<VariableSymbol> parameters, Type returnType, boolean returnTypeKnown, FunctionDeclNode declaration) {
        this(name, declarationSpan, parameters, returnType, returnTypeKnown, declaration, null, null, false, false, false);
    }

    private FunctionSymbol(String name, SourceSpan declarationSpan, List<VariableSymbol> parameters, Type returnType, boolean returnTypeKnown, FunctionDeclNode declaration, InitDeclNode initDeclaration, ClassDeclNode owner, boolean builtin,
                    boolean open, boolean override) {
        super(name, declarationSpan);
        this.parameters = List.copyOf(parameters);
        this.returnType = Objects.requireNonNull(returnType);
        this.returnTypeKnown = returnTypeKnown;
        this.declaration = declaration;
        this.initDeclaration = initDeclaration;
        this.owner = owner;
        this.builtin = builtin;
        this.open = open;
        this.override = override;
        List<Type> parameterTypes = new ArrayList<>(this.parameters.size());
        for (VariableSymbol parameter : this.parameters) {
            parameterTypes.add(parameter.type());
        }
        this.functionType = new FunctionType(parameterTypes, returnType);
    }

    /**
     * Creates a predeclared built-in function such as {@code print}/{@code println}. Built-ins have
     * no syntax declaration; their parameters are synthetic immutable bindings.
     */
    public static FunctionSymbol builtin(String name, List<Type> parameterTypes, Type returnType) {
        List<VariableSymbol> parameters = new ArrayList<>(parameterTypes.size());
        for (int i = 0; i < parameterTypes.size(); i++) {
            VariableSymbol parameter = new VariableSymbol("arg" + i, SourceSpan.of(0, 0), parameterTypes.get(i), false, true);
            parameter.markInitialized();
            parameters.add(parameter);
        }
        return new FunctionSymbol(name, SourceSpan.of(0, 0), parameters, returnType, true, null, null, null, true, false, false);
    }

    /** Creates an instance method of a class; the method's receiver is implicit. */
    static FunctionSymbol declaredMethod(String name, SourceSpan declarationSpan, List<VariableSymbol> parameters, Type returnType, boolean returnTypeKnown, FunctionDeclNode declaration, ClassDeclNode owner, boolean open,
                    boolean override) {
        return new FunctionSymbol(name, declarationSpan, parameters, returnType, returnTypeKnown, declaration, null, owner, false, open, override);
    }

    /** Creates a class {@code init} constructor; its receiver is implicit and it returns {@code Unit}. */
    static FunctionSymbol declaredConstructor(SourceSpan declarationSpan, List<VariableSymbol> parameters, InitDeclNode declaration, ClassDeclNode owner) {
        return new FunctionSymbol("<init>", declarationSpan, parameters, UnitType.INSTANCE, true, null, declaration, owner, false, false, false);
    }

    public List<VariableSymbol> parameters() {
        return parameters;
    }

    public Type returnType() {
        return returnType;
    }

    /** Whether the written return type resolved; false suppresses cascading return diagnostics. */
    public boolean isReturnTypeKnown() {
        return returnTypeKnown;
    }

    /** The syntax declaration, or {@code null} for a built-in or a constructor. */
    public FunctionDeclNode declaration() {
        return declaration;
    }

    /** The {@code init} syntax declaration, or {@code null} for every non-constructor callable. */
    public InitDeclNode initDeclaration() {
        return initDeclaration;
    }

    /** Whether this is a predeclared built-in rather than a source declaration. */
    public boolean isBuiltin() {
        return builtin;
    }

    /** The class declaration that owns this instance method or constructor, or {@code null}. */
    public ClassDeclNode owner() {
        return owner;
    }

    /** Whether this is an instance method rather than a top-level function or constructor. */
    public boolean isMethod() {
        return owner != null && initDeclaration == null;
    }

    /** Whether this is a class {@code init} constructor. */
    public boolean isConstructor() {
        return initDeclaration != null;
    }

    /** Whether this method declaration used the {@code open} modifier. */
    public boolean isOpen() {
        return open;
    }

    /** Whether this method declaration used the {@code override} modifier. */
    public boolean isOverride() {
        return override;
    }

    /** The type of the function as a call target, used only to type a call's callee. */
    public FunctionType functionType() {
        return functionType;
    }
}
