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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.solvik.ast.declaration.ClassDeclNode;
import org.solvik.ast.declaration.ConstructorDeclNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.declaration.InterfaceDeclNode;
import org.solvik.ast.declaration.SignatureDeclNode;
import org.solvik.source.SourceSpan;
import org.solvik.type.FunctionType;
import org.solvik.type.Type;
import org.solvik.type.TypeParameterType;
import org.solvik.type.UnitType;

/**
 * A declared callable: a top-level function, a class instance method, a class constructor, an
 * interface default method, an interface abstract signature, or a compiler-synthesized
 * delegation forwarding method. It carries its parameter bindings, its return type, its syntax
 * declaration, the owner it was declared on, and (for a class method) its {@code open}/{@code
 * override} modifiers.
 *
 * <p>{@link #hasImplementation()} is the interface-conformance distinction
 * (docs/LANGUAGE_SPEC.md section 8): only an interface abstract signature lacks one, so a required
 * member is satisfied by a symbol that supplies a body — a class method, an inherited default, or a
 * synthesized forwarding method (docs/LANGUAGE_SPEC.md section 9).
 *
 * <p>A default method is recorded as a member of the interface that declares it and is installed
 * unchanged into the virtual method table of every class that conforms to that interface without
 * supplying its own implementation. It is therefore not copied or renamed per class: the class's
 * table entry is this same symbol.
 */
public final class FunctionSymbol extends Symbol {

    private final List<VariableSymbol> parameters;
    private final List<TypeParameterType> typeParameters;
    private final Type returnType;
    private final boolean returnTypeKnown;
    private final FunctionDeclNode declaration;
    private final ConstructorDeclNode constructorDeclaration;
    private final SignatureDeclNode signatureDeclaration;
    private final ClassDeclNode owner;
    private final InterfaceDeclNode interfaceOwner;
    private final FunctionSymbol forwardedDelegate;
    private final PropertySymbol delegateProperty;
    private final FunctionType functionType;
    private final boolean builtin;
    private final boolean open;
    private final boolean override;

    FunctionSymbol(String name, SourceSpan declarationSpan, List<VariableSymbol> parameters, Type returnType, boolean returnTypeKnown, FunctionDeclNode declaration) {
        this(name, declarationSpan, parameters, List.of(), returnType, returnTypeKnown, declaration, null, null, null, null, null, null, false, false, false);
    }

    /** Creates a written top-level function with its declared type parameters. */
    FunctionSymbol(String name, SourceSpan declarationSpan, List<VariableSymbol> parameters, List<TypeParameterType> typeParameters, Type returnType, boolean returnTypeKnown, FunctionDeclNode declaration) {
        this(name, declarationSpan, parameters, typeParameters, returnType, returnTypeKnown, declaration, null, null, null, null, null, null, false, false, false);
    }

    private FunctionSymbol(String name, SourceSpan declarationSpan, List<VariableSymbol> parameters, List<TypeParameterType> typeParameters, Type returnType, boolean returnTypeKnown, FunctionDeclNode declaration, ConstructorDeclNode constructorDeclaration,
                    SignatureDeclNode signatureDeclaration, ClassDeclNode owner, InterfaceDeclNode interfaceOwner, FunctionSymbol forwardedDelegate, PropertySymbol delegateProperty, boolean builtin, boolean open, boolean override) {
        super(name, declarationSpan);
        this.parameters = List.copyOf(parameters);
        this.typeParameters = List.copyOf(typeParameters);
        this.returnType = Objects.requireNonNull(returnType);
        this.returnTypeKnown = returnTypeKnown;
        this.declaration = declaration;
        this.constructorDeclaration = constructorDeclaration;
        this.signatureDeclaration = signatureDeclaration;
        this.owner = owner;
        this.interfaceOwner = interfaceOwner;
        this.forwardedDelegate = forwardedDelegate;
        this.delegateProperty = delegateProperty;
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
        return new FunctionSymbol(name, SourceSpan.of(0, 0), parameters, List.of(), returnType, true, null, null, null, null, null, null, null, true, false, false);
    }

    /** Creates an instance method of a class; the method's receiver is implicit. */
    static FunctionSymbol declaredMethod(String name, SourceSpan declarationSpan, List<VariableSymbol> parameters, List<TypeParameterType> typeParameters, Type returnType, boolean returnTypeKnown, FunctionDeclNode declaration, ClassDeclNode owner,
                    boolean open, boolean override) {
        return new FunctionSymbol(name, declarationSpan, parameters, typeParameters, returnType, returnTypeKnown, declaration, null, null, owner, null, null, null, false, open, override);
    }

    /** Creates a class constructor; its receiver is implicit and it returns {@code Unit}. */
    static FunctionSymbol declaredConstructor(SourceSpan declarationSpan, List<VariableSymbol> parameters, ConstructorDeclNode declaration, ClassDeclNode owner) {
        return new FunctionSymbol("<init>", declarationSpan, parameters, List.of(), UnitType.INSTANCE, true, null, declaration, null, owner, null, null, null, false, false, false);
    }

    /** Creates an interface default method; its receiver is the implementing instance. */
    static FunctionSymbol declaredInterfaceMethod(String name, SourceSpan declarationSpan, List<VariableSymbol> parameters, List<TypeParameterType> typeParameters, Type returnType, boolean returnTypeKnown, FunctionDeclNode declaration,
                    InterfaceDeclNode owner) {
        return new FunctionSymbol(name, declarationSpan, parameters, typeParameters, returnType, returnTypeKnown, declaration, null, null, null, owner, null, null, false, false, false);
    }

    /** Creates an interface abstract signature, which declares a requirement but no body. */
    static FunctionSymbol declaredInterfaceSignature(String name, SourceSpan declarationSpan, List<VariableSymbol> parameters, List<TypeParameterType> typeParameters, Type returnType, boolean returnTypeKnown, SignatureDeclNode declaration,
                    InterfaceDeclNode owner) {
        return new FunctionSymbol(name, declarationSpan, parameters, typeParameters, returnType, returnTypeKnown, null, null, declaration, null, owner, null, null, false, false, false);
    }

    /**
     * Creates the method the compiler synthesizes to satisfy an interface member by forwarding to a
     * {@code delegate} property (docs/LANGUAGE_SPEC.md section 9). The synthesized method takes the
     * receiver in frame slot zero and calls {@code forwarded} on the value of {@code delegateProperty},
     * so it is executable exactly like a declared method and needs no syntax declaration.
     */
    static FunctionSymbol delegatedMethod(String name, SourceSpan declarationSpan, List<VariableSymbol> parameters, Type returnType, boolean returnTypeKnown, //
                    FunctionSymbol forwarded, PropertySymbol delegateProperty, ClassDeclNode owner) {
        return new FunctionSymbol(name, declarationSpan, parameters, List.of(), returnType, returnTypeKnown, null, null, null, owner, null, //
                        Objects.requireNonNull(forwarded), Objects.requireNonNull(delegateProperty), false, false, false);
    }

    public List<VariableSymbol> parameters() {
        return parameters;
    }

    /** The declared type parameters of a generic function or method, in source order. */
    public List<TypeParameterType> typeParameters() {
        return typeParameters;
    }

    public Type returnType() {
        return returnType;
    }

    /** Whether the written return type resolved; false suppresses cascading return diagnostics. */
    public boolean isReturnTypeKnown() {
        return returnTypeKnown;
    }

    /** The syntax declaration, or {@code null} for a built-in, a constructor, or a signature. */
    public FunctionDeclNode declaration() {
        return declaration;
    }

    /** The constructor syntax declaration, or {@code null} for every non-constructor callable. */
    public ConstructorDeclNode constructorDeclaration() {
        return constructorDeclaration;
    }

    /** The abstract-signature syntax declaration, or {@code null} unless this is a requirement. */
    public SignatureDeclNode signatureDeclaration() {
        return signatureDeclaration;
    }

    /** Whether this is a predeclared built-in rather than a source declaration. */
    public boolean isBuiltin() {
        return builtin;
    }

    /** The class declaration that owns this instance method or constructor, or {@code null}. */
    public ClassDeclNode owner() {
        return owner;
    }

    /** The interface declaration that owns this default method or abstract signature, or {@code null}. */
    public InterfaceDeclNode interfaceOwner() {
        return interfaceOwner;
    }

    /** Whether this is a class or interface member rather than a top-level function or constructor. */
    public boolean isMethod() {
        return (owner != null || interfaceOwner != null) && constructorDeclaration == null;
    }

    /** Whether this member was declared on an interface (default method or abstract signature). */
    public boolean isInterfaceMember() {
        return interfaceOwner != null;
    }

    /**
     * Whether this symbol is compiler-synthesized rather than written: a delegation forwarding method
     * (docs/LANGUAGE_SPEC.md section 9) is the only case.
     */
    public boolean isSynthesized() {
        return forwardedDelegate != null;
    }

    /** The delegate's own implementation that a forwarding method calls, or {@code null}. */
    public FunctionSymbol forwardedDelegate() {
        return forwardedDelegate;
    }

    /** The {@code delegate} property that a forwarding method reads, or {@code null}. */
    public PropertySymbol delegateProperty() {
        return delegateProperty;
    }

    /** Whether this is a class constructor. */
    public boolean isConstructor() {
        return constructorDeclaration != null;
    }

    /**
     * Whether this callable supplies a body: a written body or a compiler-synthesized delegation
     * forwarding body. Only an interface abstract signature lacks one, which is what makes it a
     * requirement an implementing class, an inherited default, or a delegate must satisfy.
     */
    public boolean hasImplementation() {
        return declaration != null || constructorDeclaration != null || forwardedDelegate != null;
    }

    /** Whether this is an interface abstract signature: a required member with no body. */
    public boolean isAbstractSignature() {
        return signatureDeclaration != null;
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
