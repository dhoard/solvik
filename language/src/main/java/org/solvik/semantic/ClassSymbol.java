/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.semantic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.solvik.ast.declaration.ClassDeclNode;
import org.solvik.source.SourceSpan;
import org.solvik.type.ClassType;

/**
 * A compiled class descriptor (docs/ARCHITECTURE.md "Classes"): its nominal type, its single
 * optional superclass, its statically declared property layout, its instance methods, and its
 * optional explicit {@code init}. Runtime class metadata is a separate representation produced by
 * lowering.
 *
 * <p>{@link #properties()} and {@link #methods()} include inherited members with the subclass's own
 * members following (properties) or replacing (methods) them, which is the layout and virtual
 * dispatch table used by lowering. {@link #declaredProperties()} and {@link #declaredMethods()}
 * expose only what the class itself writes.
 */
public final class ClassSymbol extends Symbol {

    private final ClassDeclNode declaration;
    private final ClassType type;
    private final boolean open;
    private final ClassSymbol superClass;
    private final List<PropertySymbol> declaredProperties;
    private final List<PropertySymbol> properties;
    private final Map<String, PropertySymbol> propertiesByName = new LinkedHashMap<>();
    private final List<FunctionSymbol> declaredMethods;
    private final List<FunctionSymbol> methods;
    private final Map<String, FunctionSymbol> methodsByName = new LinkedHashMap<>();
    private final FunctionSymbol constructor;

    ClassSymbol(ClassDeclNode declaration, ClassType type, boolean open, ClassSymbol superClass, List<PropertySymbol> declaredProperties, List<FunctionSymbol> declaredMethods, FunctionSymbol constructor) {
        super(declaration.name(), declaration.span());
        this.declaration = Objects.requireNonNull(declaration);
        this.type = Objects.requireNonNull(type);
        this.open = open;
        this.superClass = superClass;
        this.declaredProperties = List.copyOf(declaredProperties);
        this.declaredMethods = List.copyOf(declaredMethods);
        this.constructor = constructor;

        List<PropertySymbol> allProperties = new ArrayList<>();
        if (superClass != null) {
            allProperties.addAll(superClass.properties());
        }
        allProperties.addAll(this.declaredProperties);
        this.properties = List.copyOf(allProperties);
        for (PropertySymbol property : this.properties) {
            propertiesByName.put(property.name(), property);
        }

        Map<String, FunctionSymbol> allMethods = new LinkedHashMap<>();
        if (superClass != null) {
            allMethods.putAll(superClass.methodsByName);
        }
        for (FunctionSymbol method : this.declaredMethods) {
            allMethods.put(method.name(), method);
        }
        this.methods = List.copyOf(allMethods.values());
        this.methodsByName.putAll(allMethods);
    }

    public ClassDeclNode declaration() {
        return declaration;
    }

    /** The nominal compile-time type of instances of this class. */
    public ClassType type() {
        return type;
    }

    /** Whether the class was declared {@code open} and may be extended. */
    public boolean isOpen() {
        return open;
    }

    /** The single resolved superclass, or empty when the class derives directly from {@code Object}. */
    public Optional<ClassSymbol> superClass() {
        return Optional.ofNullable(superClass);
    }

    /** Only the properties declared directly by this class. */
    public List<PropertySymbol> declaredProperties() {
        return declaredProperties;
    }

    /** All properties in layout order: inherited first, then this class's own. */
    public List<PropertySymbol> properties() {
        return properties;
    }

    public Optional<PropertySymbol> property(String name) {
        return Optional.ofNullable(propertiesByName.get(name));
    }

    /** Only the methods declared directly by this class. */
    public List<FunctionSymbol> declaredMethods() {
        return declaredMethods;
    }

    /** The virtual dispatch table: inherited methods with this class's overrides replacing them. */
    public List<FunctionSymbol> methods() {
        return methods;
    }

    /**
     * The method the virtual dispatch table exposes for {@code name}: the override nearest this
     * class, or empty when the class (or any ancestor) declares no such method.
     */
    public Optional<FunctionSymbol> method(String name) {
        return Optional.ofNullable(methodsByName.get(name));
    }

    /** Whether this class is the same as or a subclass of {@code other}. */
    public boolean isSubclassOf(ClassSymbol other) {
        for (ClassSymbol current = this; current != null; current = current.superClass) {
            if (current == other) {
                return true;
            }
        }
        return false;
    }

    /** The explicit {@code init} signature, or empty when the class relies on property initializers. */
    public Optional<FunctionSymbol> constructor() {
        return Optional.ofNullable(constructor);
    }

    public boolean hasExplicitInit() {
        return constructor != null;
    }
}
