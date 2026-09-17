/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.semantic;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.solvik.ast.declaration.ClassDeclNode;
import org.solvik.type.ClassType;

/**
 * A compiled class descriptor (docs/ARCHITECTURE.md "Classes"): its nominal type, its single optional
 * superclass, the interfaces it implements, its statically declared property layout, its instance
 * methods, and its optional explicit {@code init}. Runtime class metadata is a separate
 * representation produced by lowering.
 *
 * <p>{@link #properties()} and {@link #methods()} include inherited members with the subclass's own
 * members following (properties) or replacing (methods) them, which is the layout and virtual dispatch
 * table used by lowering. {@link #declaredProperties()} and {@link #declaredMethods()} expose only
 * what the class itself writes.
 *
 * <p>Interface conformance (docs/LANGUAGE_SPEC.md section 8) is resolved while the descriptor is
 * built, because it needs the completed virtual table. For every interface requirement the effective
 * implementation is chosen by the precedence in docs/ARCHITECTURE.md "Delegation":
 *
 * <ol>
 * <li>a method declared by this class;</li>
 * <li>a valid implementation declared by a superclass (an inherited class method);</li>
 * <li>an unambiguous interface default.</li>
 * </ol>
 *
 * Two or more distinct interface defaults for one unresolved requirement is a conflict the class must
 * resolve itself, and a requirement with no implementation at all is unsatisfied. The chosen results
 * are exposed by {@link #interfaceImplementations()}, {@link #missingInterfaceRequirements()},
 * {@link #conflictingInterfaceRequirements()}, and {@link #interfaceSignatureConflicts()} for the
 * semantic pass to report as diagnostics; a resolved default is additionally installed into the
 * virtual table so a call through a class-typed or interface-typed receiver reaches it.
 */
public final class ClassSymbol extends Symbol {

    private final ClassDeclNode declaration;
    private final ClassType type;
    private final boolean open;
    private final ClassSymbol superClass;
    private final List<InterfaceSymbol> interfaces;
    private final List<InterfaceSymbol> allInterfaces;
    private final List<PropertySymbol> declaredProperties;
    private final List<PropertySymbol> properties;
    private final Map<String, PropertySymbol> propertiesByName = new LinkedHashMap<>();
    private final List<FunctionSymbol> declaredMethods;
    private final List<FunctionSymbol> methods;
    private final Map<String, FunctionSymbol> methodsByName = new LinkedHashMap<>();
    private final Map<String, FunctionSymbol> interfaceImplementations = new LinkedHashMap<>();
    private final List<FunctionSymbol> missingInterfaceRequirements = new ArrayList<>();
    private final List<FunctionSymbol> conflictingInterfaceRequirements = new ArrayList<>();
    private final Map<FunctionSymbol, FunctionSymbol> interfaceSignatureConflicts = new IdentityHashMap<>();
    private final FunctionSymbol constructor;

    ClassSymbol(ClassDeclNode declaration, ClassType type, boolean open, ClassSymbol superClass, List<InterfaceSymbol> interfaces, List<PropertySymbol> declaredProperties, List<FunctionSymbol> declaredMethods, FunctionSymbol constructor) {
        super(declaration.name(), declaration.span());
        this.declaration = Objects.requireNonNull(declaration);
        this.type = Objects.requireNonNull(type);
        this.open = open;
        this.superClass = superClass;
        this.interfaces = List.copyOf(interfaces);
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

        if (superClass != null) {
            methodsByName.putAll(superClass.methodsByName);
        }
        for (FunctionSymbol method : this.declaredMethods) {
            methodsByName.put(method.name(), method);
        }

        this.allInterfaces = resolveInterfaceClosure(this.interfaces, superClass);
        resolveInterfaceConformance();

        this.methods = List.copyOf(methodsByName.values());
    }

    /** The interface closure visible to this class: its own {@code implements} list plus inherited ones. */
    private static List<InterfaceSymbol> resolveInterfaceClosure(List<InterfaceSymbol> direct, ClassSymbol superClass) {
        List<InterfaceSymbol> closure = new ArrayList<>();
        for (InterfaceSymbol face : direct) {
            addDistinctInterface(closure, face);
        }
        if (superClass != null) {
            for (InterfaceSymbol face : superClass.allInterfaces) {
                addDistinctInterface(closure, face);
            }
        }
        return List.copyOf(closure);
    }

    private static void addDistinctInterface(List<InterfaceSymbol> closure, InterfaceSymbol face) {
        for (InterfaceSymbol existing : closure) {
            if (existing == face) {
                return;
            }
        }
        closure.add(face);
    }

    /**
     * Chooses the effective implementation of every interface member visible to this class and records
     * unsatisfied, conflicting, and mismatched members. Runs after the virtual table is assembled so a
     * class or inherited method is already visible.
     *
     * <p>Each name in the interface contract is resolved by the precedence in docs/ARCHITECTURE.md
     * "Delegation": this class's own method, then a superclass method, then an unambiguous interface
     * default. A name that some interface declares as an abstract requirement must resolve, so no
     * implementation at all is unsatisfied and two distinct defaults are a conflict the class must
     * resolve itself. Finally, the resolved implementation must conform to every member of that name,
     * whether the member was written as a requirement or as a default: replacing an inherited default
     * with an incompatible signature would otherwise break a call made through the interface type.
     */
    private void resolveInterfaceConformance() {
        Map<String, List<FunctionSymbol>> contract = new LinkedHashMap<>();
        for (InterfaceSymbol face : allInterfaces) {
            for (FunctionSymbol member : face.members()) {
                List<FunctionSymbol> group = contract.computeIfAbsent(member.name(), k -> new ArrayList<>());
                if (!containsIdentity(group, member)) {
                    group.add(member);
                }
            }
        }
        for (Map.Entry<String, List<FunctionSymbol>> entry : contract.entrySet()) {
            String name = entry.getKey();
            List<FunctionSymbol> members = entry.getValue();
            FunctionSymbol chosen = declaredMethodNamed(name).orElse(null);
            if (chosen == null) {
                chosen = inheritedClassMethod(name).orElse(null);
            }
            if (chosen == null) {
                List<FunctionSymbol> defaults = applicableDefaults(members);
                if (defaults.isEmpty()) {
                    // Nothing in the class, an ancestor class, or an interface supplies a body.
                    missingInterfaceRequirements.addAll(members);
                    continue;
                }
                if (defaults.size() > 1) {
                    // Several interface defaults supply the name and the class resolves none of them,
                    // which the specification requires the class to settle explicitly.
                    conflictingInterfaceRequirements.addAll(members);
                    continue;
                }
                chosen = defaults.get(0);
                // A resolved default becomes part of this class's virtual dispatch table so a call
                // through any conforming receiver reaches it.
                methodsByName.put(name, chosen);
            }
            interfaceImplementations.put(name, chosen);
            for (FunctionSymbol member : members) {
                if (!signaturesConform(member, chosen)) {
                    interfaceSignatureConflicts.put(member, chosen);
                }
            }
        }
    }

    /** The distinct interface members of a contract name that supply an implementation. */
    private static List<FunctionSymbol> applicableDefaults(List<FunctionSymbol> members) {
        List<FunctionSymbol> defaults = new ArrayList<>();
        for (FunctionSymbol member : members) {
            if (member.hasImplementation() && !containsIdentity(defaults, member)) {
                defaults.add(member);
            }
        }
        return defaults;
    }

    /**
     * Whether an implementing method keeps the required parameter types and returns a subtype of the
     * required return type (docs/LANGUAGE_SPEC.md section 8). Unresolved written types suppress the
     * check so the unknown-type diagnostic is the only reported error.
     */
    private static boolean signaturesConform(FunctionSymbol requirement, FunctionSymbol implementation) {
        if (!requirement.isReturnTypeKnown() || !implementation.isReturnTypeKnown()) {
            return true;
        }
        return sameParameterTypes(requirement, implementation) && implementation.returnType().isAssignableTo(requirement.returnType());
    }

    private Optional<FunctionSymbol> declaredMethodNamed(String name) {
        for (FunctionSymbol method : declaredMethods) {
            if (method.name().equals(name)) {
                return Optional.of(method);
            }
        }
        return Optional.empty();
    }

    private static boolean containsIdentity(List<FunctionSymbol> list, FunctionSymbol candidate) {
        for (FunctionSymbol existing : list) {
            if (existing == candidate) {
                return true;
            }
        }
        return false;
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

    /** The interfaces named directly in this class's {@code implements} clause, in source order. */
    public List<InterfaceSymbol> interfaces() {
        return interfaces;
    }

    /** Every interface this class conforms to: its own list plus those inherited from its superclass. */
    public List<InterfaceSymbol> allInterfaces() {
        return allInterfaces;
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

    /** The virtual dispatch table: inherited and defaulted methods replaced by this class's own. */
    public List<FunctionSymbol> methods() {
        return methods;
    }

    /**
     * The method the virtual dispatch table exposes for {@code name}: this class's own method, an
     * inherited one, a resolved interface default, or empty when nothing supplies the name.
     */
    public Optional<FunctionSymbol> method(String name) {
        return Optional.ofNullable(methodsByName.get(name));
    }

    /**
     * The nearest implementation of {@code name} supplied by a superclass declaration, skipping the
     * class's own declaration and any interface default installed into an ancestor's table. Interface
     * conformance uses this because its precedence checks this class's own method first.
     */
    public Optional<FunctionSymbol> inheritedClassMethod(String name) {
        for (ClassSymbol current = superClass; current != null; current = current.superClass) {
            Optional<FunctionSymbol> declared = current.declaredMethodNamed(name);
            if (declared.isPresent()) {
                return declared;
            }
        }
        return Optional.empty();
    }

    /**
     * The nearest implementation of {@code name} declared by this class or one of its ancestor
     * classes, never an interface default. Override validation uses this because {@code override}
     * governs class inheritance only: replacing an inherited interface default is an implementing
     * method and needs no modifier.
     */
    public Optional<FunctionSymbol> nearestDeclaredClassMethod(String name) {
        for (ClassSymbol current = this; current != null; current = current.superClass) {
            Optional<FunctionSymbol> declared = current.declaredMethodNamed(name);
            if (declared.isPresent()) {
                return declared;
            }
        }
        return Optional.empty();
    }

    /** The effective implementation chosen for an interface requirement name, when one exists. */
    public Optional<FunctionSymbol> interfaceImplementation(String name) {
        return Optional.ofNullable(interfaceImplementations.get(name));
    }

    /** Interface requirements with no implementation from the class, an ancestor, or a default. */
    public List<FunctionSymbol> missingInterfaceRequirements() {
        return List.copyOf(missingInterfaceRequirements);
    }

    /** Interface requirements with more than one applicable interface default and no resolution. */
    public List<FunctionSymbol> conflictingInterfaceRequirements() {
        return List.copyOf(conflictingInterfaceRequirements);
    }

    /** Each mismatched requirement mapped to the implementation that failed to conform to it. */
    public Map<FunctionSymbol, FunctionSymbol> interfaceSignatureConflicts() {
        return Map.copyOf(interfaceSignatureConflicts);
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
