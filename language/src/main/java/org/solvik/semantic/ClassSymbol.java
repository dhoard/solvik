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
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.solvik.ast.declaration.ClassDeclNode;
import org.solvik.ast.declaration.InterfaceDeclNode;
import org.solvik.ast.declaration.StaticBlockNode;
import org.solvik.type.ClassType;
import org.solvik.type.ParameterizedType;
import org.solvik.type.Type;
import org.solvik.type.TypeParameterType;

/**
 * A compiled class descriptor (docs/ARCHITECTURE.md "Classes"): its nominal type, its single optional
 * superclass, the interfaces it implements, its statically declared property and delegate layout, its
 * instance methods, and its optional explicit constructor. Runtime class metadata is a separate
 * representation produced by lowering.
 *
 * <p>{@link #properties()} and {@link #methods()} include inherited members with the subclass's own
 * members following (properties) or replacing (methods) them, which is the layout and virtual dispatch
 * table used by lowering. {@link #declaredProperties()} and {@link #declaredMethods()} expose only
 * what the class itself writes.
 *
 * <p>Interface conformance (docs/LANGUAGE_SPEC.md sections 8 and 9) is resolved while the descriptor
 * is built, because it needs the completed virtual table. For every interface member visible to the
 * class, the effective implementation is chosen by the precedence in docs/ARCHITECTURE.md
 * "Delegation":
 *
 * <ol>
 * <li>a method declared by this class;</li>
 * <li>a valid implementation declared by a superclass, including a forwarding method a superclass
 * already resolved (an inherited implementation);</li>
 * <li>an unambiguous delegated implementation supplied by a {@code delegate} property;</li>
 * <li>an unambiguous interface default.</li>
 * </ol>
 *
 * Two distinct delegates supplying one member is an ambiguity the class must settle with an explicit
 * method, and so are two distinct interface defaults when nothing earlier supplies the member. The
 * chosen results are exposed by {@link #interfaceImplementations()},
 * {@link #missingInterfaceRequirements()}, {@link #conflictingInterfaceRequirements()},
 * {@link #ambiguousDelegatedRequirements()}, {@link #delegatedRequirements()}, and
 * {@link #interfaceSignatureConflicts()} for the semantic pass to report as diagnostics; a resolved
 * default or forwarding method is additionally installed into the virtual table so a call through a
 * class-typed or interface-typed receiver reaches it.
 */
public final class ClassSymbol extends Symbol {

    private final ClassDeclNode declaration;
    private final ClassType type;
    private final boolean open;
    private final boolean sealed;
    private final ClassSymbol superClass;
    private final List<InterfaceSymbol> interfaces;
    private final List<InterfaceSymbol> allInterfaces;
    private final List<DelegateBinding> delegates;
    private final List<PropertySymbol> declaredProperties;
    private final List<PropertySymbol> properties;
    private final Map<String, PropertySymbol> propertiesByName = new LinkedHashMap<>();
    private final List<FunctionSymbol> declaredMethods;
    private final List<FunctionSymbol> methods;
    private final Map<String, FunctionSymbol> methodsByName = new LinkedHashMap<>();
    /**
     * The class's own {@code static} properties and methods (docs/LANGUAGE_SPEC.md section 7). Kept
     * entirely separate from the instance layout and virtual table, and deliberately <em>not</em>
     * merged with any superclass's statics: a static member is reached only through the name of the
     * class that declares it, so it is neither inherited nor overridable.
     */
    private final List<PropertySymbol> declaredStaticProperties;
    private final Map<String, PropertySymbol> staticPropertiesByName = new LinkedHashMap<>();
    private final List<FunctionSymbol> declaredStaticMethods;
    private final Map<String, FunctionSymbol> staticMethodsByName = new LinkedHashMap<>();
    private final StaticBlockNode staticBlock;
    private final Map<String, FunctionSymbol> interfaceImplementations = new LinkedHashMap<>();
    private final Map<String, PropertySymbol> delegatedRequirements = new LinkedHashMap<>();
    private final List<FunctionSymbol> missingInterfaceRequirements = new ArrayList<>();
    private final List<FunctionSymbol> conflictingInterfaceRequirements = new ArrayList<>();
    private final List<FunctionSymbol> ambiguousDelegatedRequirements = new ArrayList<>();
    private final Map<FunctionSymbol, FunctionSymbol> interfaceSignatureConflicts = new IdentityHashMap<>();
    private final Map<FunctionSymbol, PropertySymbol> delegateSignatureConflicts = new IdentityHashMap<>();
    private final FunctionSymbol constructor;
    /**
     * For every interface in this class's closure, the mapping from that interface's type parameters
     * to the types this class applies them to (docs/LANGUAGE_SPEC.md section 11). A non-generic
     * implementation of {@code Repository<User>} binds {@code T} to {@code User}; a generic class
     * binds it to one of its own type parameters. Conformance compares signatures after substitution.
     */
    private final Map<InterfaceDeclNode, Map<TypeParameterType, Type>> interfaceBindings;
    /** For each property, the substitution from its declaring type's parameters to this class's. */
    private final Map<PropertySymbol, Map<TypeParameterType, Type>> propertySubstitutions = new IdentityHashMap<>();
    /** For each dispatch name, the substitution from the implementation's declaring parameters. */
    private final Map<String, Map<TypeParameterType, Type>> methodSubstitutions = new LinkedHashMap<>();
    /**
     * The complete permitted subtype set of a sealed class, installed after every class is
     * collected (docs/LANGUAGE_SPEC.md section 12). A non-sealed class keeps empty sets. The direct
     * set is the sealed hierarchy's variant set; the transitive set is every descendant, which the
     * specification guarantees is closed when the file is compiled.
     */
    private List<ClassSymbol> permittedSubtypes = List.of();
    private List<ClassSymbol> allSubtypes = List.of();

    ClassSymbol(ClassDeclNode declaration, ClassType type, boolean open, boolean sealed, ClassSymbol superClass, List<InterfaceSymbol> interfaces, List<DelegateBinding> delegates, //
                    List<PropertySymbol> declaredProperties, List<FunctionSymbol> declaredMethods, FunctionSymbol constructor, Map<InterfaceDeclNode, Map<TypeParameterType, Type>> interfaceBindings, //
                    List<PropertySymbol> declaredStaticProperties, List<FunctionSymbol> declaredStaticMethods, StaticBlockNode staticBlock) {
        super(declaration.name(), declaration.span());
        this.declaration = Objects.requireNonNull(declaration);
        this.type = Objects.requireNonNull(type);
        this.open = open;
        this.sealed = sealed;
        this.superClass = superClass;
        this.interfaces = List.copyOf(interfaces);
        this.delegates = List.copyOf(delegates);
        this.declaredProperties = List.copyOf(declaredProperties);
        this.declaredMethods = List.copyOf(declaredMethods);
        this.constructor = constructor;
        this.interfaceBindings = Map.copyOf(interfaceBindings);
        this.declaredStaticProperties = List.copyOf(declaredStaticProperties);
        for (PropertySymbol property : this.declaredStaticProperties) {
            staticPropertiesByName.put(property.name(), property);
        }
        this.declaredStaticMethods = List.copyOf(declaredStaticMethods);
        for (FunctionSymbol method : this.declaredStaticMethods) {
            staticMethodsByName.put(method.name(), method);
        }
        this.staticBlock = staticBlock;

        List<PropertySymbol> allProperties = new ArrayList<>();
        if (superClass != null) {
            allProperties.addAll(superClass.properties());
        }
        allProperties.addAll(this.declaredProperties);
        this.properties = List.copyOf(allProperties);
        Map<TypeParameterType, Type> superSubstitution = superTypeSubstitution();
        Map<TypeParameterType, Type> ownSubstitution = identitySubstitution(type);
        if (superClass != null) {
            for (PropertySymbol property : superClass.properties()) {
                propertySubstitutions.put(property, compose(superClass.propertySubstitutions.get(property), superSubstitution));
            }
        }
        for (PropertySymbol property : this.declaredProperties) {
            propertySubstitutions.put(property, ownSubstitution);
        }
        for (PropertySymbol property : this.properties) {
            propertiesByName.put(property.name(), property);
        }

        if (superClass != null) {
            for (Map.Entry<String, Map<TypeParameterType, Type>> entry : superClass.methodSubstitutions.entrySet()) {
                methodSubstitutions.put(entry.getKey(), compose(entry.getValue(), superSubstitution));
            }
        }
        for (FunctionSymbol method : this.declaredMethods) {
            methodSubstitutions.put(method.name(), ownSubstitution);
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

    /**
     * The substitution the immediate supertype applies to its nominal base's type parameters; empty
     * for a non-generic or absent superclass.
     */
    private Map<TypeParameterType, Type> superTypeSubstitution() {
        Type parent = type.superType().orElse(null);
        return parent instanceof ParameterizedType parameterized ? parameterized.substitution() : Map.of();
    }

    /** Composes inherited substitutions: {@code outer}'s values are themselves substituted by {@code inner}. */
    private static Map<TypeParameterType, Type> compose(Map<TypeParameterType, Type> outer, Map<TypeParameterType, Type> inner) {
        if (outer == null || outer.isEmpty() || inner.isEmpty()) {
            return outer == null ? Map.of() : Map.copyOf(outer);
        }
        Map<TypeParameterType, Type> composed = new IdentityHashMap<>();
        for (Map.Entry<TypeParameterType, Type> entry : outer.entrySet()) {
            composed.put(entry.getKey(), entry.getValue().substitute(inner));
        }
        return composed;
    }

    /** The identity substitution over a declaring type's own parameters; empty for a non-generic type. */
    private static Map<TypeParameterType, Type> identitySubstitution(Type declaringType) {
        List<TypeParameterType> parameters = declaringType.typeParameters();
        if (parameters.isEmpty()) {
            return Map.of();
        }
        Map<TypeParameterType, Type> identity = new IdentityHashMap<>();
        for (TypeParameterType parameter : parameters) {
            identity.put(parameter, parameter);
        }
        return identity;
    }

    /** The substitution mapping an interface member's declared parameters to this class's types. */
    private Map<TypeParameterType, Type> interfaceBinding(FunctionSymbol member) {
        Map<TypeParameterType, Type> binding = interfaceBindings.get(member.interfaceOwner());
        return binding == null ? Map.of() : binding;
    }

    /**
     * The substitution from a property's declaring type parameters to this class's, used to read an
     * inherited generic property through a class receiver (docs/LANGUAGE_SPEC.md section 11).
     */
    public Map<TypeParameterType, Type> propertySubstitution(PropertySymbol property) {
        Map<TypeParameterType, Type> substitution = propertySubstitutions.get(property);
        return substitution == null ? Map.of() : substitution;
    }

    /** The substitution from a dispatch entry's declaring type parameters to this class's. */
    public Map<TypeParameterType, Type> methodSubstitution(String name) {
        Map<TypeParameterType, Type> substitution = methodSubstitutions.get(name);
        return substitution == null ? Map.of() : substitution;
    }

    /** The interface closure visible to this class: its own {@code implements } list plus inherited ones. */
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
     * unsatisfied, ambiguous, and mismatched members. Runs after the virtual table is assembled so a
     * class or inherited method is already visible.
     *
     * <p>A member of a delegate's contract is a forwarding source whether it was written as a
     * requirement or as a default: the synthesized forwarding method dispatches on the delegate value's
     * runtime class, so a requirement is satisfied by whatever the delegate object implements. This is
     * what makes the specification's {@code Repository} example work with a delegate of a purely
     * abstract interface.
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
            PropertySymbol delegate = null;
            if (chosen == null) {
                chosen = inheritedClassMethod(name).orElse(null);
            }
            if (chosen == null) {
                // A forwarding method a superclass already resolved is an inherited implementation and
                // therefore outranks this class's own delegates and any interface default.
                chosen = inheritedDelegatedMethod(name).orElse(null);
            }
            if (chosen == null) {
                List<DelegateBinding> suppliers = delegateSuppliers(name);
                if (suppliers.size() == 1) {
                    DelegateBinding supplier = suppliers.get(0);
                    FunctionSymbol forwarded = forwardedMember(supplier, name);
                    delegate = supplier.property();
                    chosen = FunctionSymbol.delegatedMethod(name, delegate.declarationSpan(), forwarded.parameters(), forwarded.returnType(), //
                                    forwarded.isReturnTypeKnown(), forwarded, delegate, declaration);
                } else if (suppliers.size() > 1) {
                    // Two distinct delegate properties supply the member and the class resolves neither,
                    // which the specification requires to be an error rather than an arbitrary choice.
                    // One offense per member name: every interface member in this group shares the name
                    // the diagnostic reports, so a representative is added rather than the whole group.
                    ambiguousDelegatedRequirements.add(members.get(0));
                    continue;
                }
            }
            if (chosen == null) {
                List<FunctionSymbol> defaults = applicableDefaults(members);
                if (defaults.isEmpty()) {
                    // Nothing in the class, an ancestor class, a delegate, or an interface supplies a body.
                    missingInterfaceRequirements.add(members.get(0));
                    continue;
                }
                if (defaults.size() > 1) {
                    // Several interface defaults supply the name and nothing earlier resolves it, which
                    // the specification requires the class to settle explicitly.
                    conflictingInterfaceRequirements.add(members.get(0));
                    continue;
                }
                chosen = defaults.get(0);
            }
            interfaceImplementations.put(name, chosen);
            if (delegate != null) {
                delegatedRequirements.put(name, delegate);
            }
            if (delegate != null || chosen.isInterfaceMember()) {
                // A resolved default or forwarding method becomes part of this class's virtual dispatch
                // table so a call through any conforming receiver reaches it.
                methodsByName.put(name, chosen);
                FunctionSymbol signatureSource = chosen.isSynthesized() ? chosen.forwardedDelegate() : chosen;
                methodSubstitutions.put(name, interfaceBinding(signatureSource));
            }
            for (FunctionSymbol member : members) {
                if (!signaturesConform(member, chosen)) {
                    if (delegate != null) {
                        // The forwarded member's own signature does not fit the requirement the class
                        // declared, so the delegate cannot supply this member as written.
                        delegateSignatureConflicts.put(member, delegate);
                    } else {
                        interfaceSignatureConflicts.put(member, chosen);
                    }
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
     * The valid delegates whose contract exposes a member called {@code name}. One delegate counted
     * once however many extension paths reach the member, so a diamond contract is not an ambiguity,
     * while two delegate properties of the same interface type are two suppliers and therefore are.
     */
    private List<DelegateBinding> delegateSuppliers(String name) {
        List<DelegateBinding> suppliers = new ArrayList<>();
        for (DelegateBinding binding : delegates) {
            if (!binding.contract().membersNamed(name).isEmpty()) {
                suppliers.add(binding);
            }
        }
        return suppliers;
    }

    /** The delegate member a forwarding method calls: preferably one that already has a body. */
    private static FunctionSymbol forwardedMember(DelegateBinding supplier, String name) {
        List<FunctionSymbol> members = supplier.contract().membersNamed(name);
        for (FunctionSymbol member : members) {
            if (member.hasImplementation()) {
                return member;
            }
        }
        return members.get(0);
    }

    /**
     * Whether an implementing method keeps the required parameter types and returns a subtype of the
     * required return type (docs/LANGUAGE_SPEC.md sections 8 and 11). Any interface type parameters
     * in a requirement are substituted with the class's binding before comparison; a class method
     * already lives in the class's own type-parameter space. Unresolved written types suppress the
     * check so the unknown-type diagnostic is the only reported error.
     */
    private boolean signaturesConform(FunctionSymbol requirement, FunctionSymbol implementation) {
        if (!requirement.isReturnTypeKnown() || !implementation.isReturnTypeKnown()) {
            return true;
        }
        List<Type> requiredParameters = substitutedParameterTypes(requirement);
        List<Type> declaredParameters = substitutedParameterTypes(implementation);
        if (requiredParameters.size() != declaredParameters.size()) {
            return false;
        }
        for (int i = 0; i < requiredParameters.size(); i++) {
            if (requiredParameters.get(i) != declaredParameters.get(i)) {
                return false;
            }
        }
        return substitutedReturnType(implementation).isAssignableTo(substitutedReturnType(requirement));
    }

    private Map<TypeParameterType, Type> substitutionFor(FunctionSymbol member) {
        if (member.isInterfaceMember()) {
            Map<TypeParameterType, Type> binding = interfaceBindings.get(member.interfaceOwner());
            return binding == null ? Map.of() : binding;
        }
        return Map.of();
    }

    private List<Type> substitutedParameterTypes(FunctionSymbol member) {
        Map<TypeParameterType, Type> binding = substitutionFor(member);
        List<Type> types = new ArrayList<>(member.parameters().size());
        for (VariableSymbol parameter : member.parameters()) {
            types.add(parameter.type().substitute(binding));
        }
        return types;
    }

    private Type substitutedReturnType(FunctionSymbol member) {
        return member.returnType().substitute(substitutionFor(member));
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

    /** Whether the class was declared {@code sealed} (docs/LANGUAGE_SPEC.md section 12). */
    public boolean isSealed() {
        return sealed;
    }

    /**
     * Whether this class may be extended at all. An {@code open} class opts in explicitly and a
     * {@code sealed} class is extendable only because its same-file subtype set is closed; every
     * other class is final by default.
     */
    public boolean isExtendable() {
        return open || sealed;
    }

    /**
     * Installs the permitted subtype metadata of this sealed class. Called once by semantic analysis
     * after every class declaration has been collected; a non-sealed class is never given subtypes.
     */
    void resolvePermittedSubtypes(List<ClassSymbol> direct, List<ClassSymbol> transitive) {
        this.permittedSubtypes = List.copyOf(Objects.requireNonNull(direct));
        this.allSubtypes = List.copyOf(Objects.requireNonNull(transitive));
    }

    /** The direct permitted subtypes of a sealed class; empty for every other class. */
    public List<ClassSymbol> permittedSubtypes() {
        return permittedSubtypes;
    }

    /** Every descendant of a sealed class, direct or transitive; empty for every other class. */
    public List<ClassSymbol> allSubtypes() {
        return allSubtypes;
    }

    /** The single resolved superclass, or empty when the class derives directly from {@code Any}. */
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

    /** Every valid {@code delegate} declaration of this class, in source order. */
    public List<DelegateBinding> delegates() {
        return delegates;
    }

    /** Only the properties declared directly by this class, delegates included. */
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

    /**
     * This class's own {@code static} properties, in source order. These are never part of
     * {@link #properties()}: class-level storage is not per-instance layout.
     */
    public List<PropertySymbol> declaredStaticProperties() {
        return declaredStaticProperties;
    }

    /** The {@code static} property this class declares under {@code name}; statics are not inherited. */
    public Optional<PropertySymbol> staticProperty(String name) {
        return Optional.ofNullable(staticPropertiesByName.get(name));
    }

    /** This class's own {@code static} methods, in source order. Never part of the virtual table. */
    public List<FunctionSymbol> declaredStaticMethods() {
        return declaredStaticMethods;
    }

    /** The {@code static} method this class declares under {@code name}; statics are not inherited. */
    public Optional<FunctionSymbol> staticMethod(String name) {
        return Optional.ofNullable(staticMethodsByName.get(name));
    }

    /** The class initializer block, when the class declares one. */
    public Optional<StaticBlockNode> staticBlock() {
        return Optional.ofNullable(staticBlock);
    }

    /** The virtual dispatch table: inherited, defaulted, and delegated members replaced by own ones. */
    public List<FunctionSymbol> methods() {
        return methods;
    }

    /**
     * The method the virtual dispatch table exposes for {@code name}: this class's own method, an
     * inherited one, a resolved interface default, a synthesized forwarding method, or empty when
     * nothing supplies the name.
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
     * The nearest forwarding method a superclass resolved for {@code name}. A delegate lives in the
     * superclass, so a subclass inherits the forwarding implementation instead of being told the
     * requirement is missing or must be resolved again.
     */
    public Optional<FunctionSymbol> inheritedDelegatedMethod(String name) {
        for (ClassSymbol current = superClass; current != null; current = current.superClass) {
            FunctionSymbol resolved = current.interfaceImplementations.get(name);
            if (resolved != null && resolved.isSynthesized()) {
                return Optional.of(resolved);
            }
        }
        return Optional.empty();
    }

    /**
     * The nearest implementation of {@code name} declared by this class or one of its ancestor
     * classes, never an interface default or a synthesized forwarding method. Override validation uses
     * this because {@code override} governs class inheritance only: replacing an inherited interface
     * default is an implementing method and needs no modifier, and a forwarding method is not written.
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

    /** The effective implementation chosen for an interface member name, when one exists. */
    public Optional<FunctionSymbol> interfaceImplementation(String name) {
        return Optional.ofNullable(interfaceImplementations.get(name));
    }

    /** The delegate property a member is forwarded to, when the member was satisfied by delegation. */
    public Optional<PropertySymbol> delegatedRequirement(String name) {
        return Optional.ofNullable(delegatedRequirements.get(name));
    }

    /** Interface requirements with no implementation from the class, an ancestor, a delegate, or a default. */
    public List<FunctionSymbol> missingInterfaceRequirements() {
        return List.copyOf(missingInterfaceRequirements);
    }

    /** Interface requirements with more than one applicable interface default and no resolution. */
    public List<FunctionSymbol> conflictingInterfaceRequirements() {
        return List.copyOf(conflictingInterfaceRequirements);
    }

    /** Interface requirements supplied by more than one {@code delegate} property with no resolution. */
    public List<FunctionSymbol> ambiguousDelegatedRequirements() {
        return List.copyOf(ambiguousDelegatedRequirements);
    }

    /** Each mismatched requirement mapped to the implementation that failed to conform to it. */
    public Map<FunctionSymbol, FunctionSymbol> interfaceSignatureConflicts() {
        return Map.copyOf(interfaceSignatureConflicts);
    }

    /** Each requirement a delegate cannot supply, mapped to the delegate that would have supplied it. */
    public Map<FunctionSymbol, PropertySymbol> delegateSignatureConflicts() {
        return Map.copyOf(delegateSignatureConflicts);
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
