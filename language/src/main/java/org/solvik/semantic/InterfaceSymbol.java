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
import org.solvik.ast.declaration.InterfaceDeclNode;
import org.solvik.type.InterfaceType;

/**
 * A compiled interface descriptor (docs/ARCHITECTURE.md "Classes"): its nominal type, the interfaces
 * it extends, the abstract member signatures it requires, and the default methods it supplies
 * (docs/LANGUAGE_SPEC.md section 8).
 *
 * <p>{@link #members()} is the complete interface view used for conformance: inherited members first,
 * then this interface's own declarations. A name may be visible through several extension paths, so
 * {@link #membersNamed(String)} is multi-valued; the same declaration reached by two paths is stored
 * once (identity deduplication), which is what keeps a diamond extension from looking like a default
 * conflict.
 */
public final class InterfaceSymbol extends Symbol {

    private final InterfaceDeclNode declaration;
    private final InterfaceType type;
    private final List<InterfaceSymbol> superInterfaces;
    private final List<FunctionSymbol> declaredMembers;
    private final Map<String, List<FunctionSymbol>> membersByName = new LinkedHashMap<>();

    InterfaceSymbol(InterfaceDeclNode declaration, InterfaceType type, List<InterfaceSymbol> superInterfaces, List<FunctionSymbol> declaredMembers) {
        super(declaration.name(), declaration.span());
        this.declaration = Objects.requireNonNull(declaration);
        this.type = Objects.requireNonNull(type);
        this.superInterfaces = List.copyOf(superInterfaces);
        this.declaredMembers = List.copyOf(declaredMembers);

        Map<String, List<FunctionSymbol>> merged = new LinkedHashMap<>();
        for (InterfaceSymbol parent : this.superInterfaces) {
            for (Map.Entry<String, List<FunctionSymbol>> entry : parent.membersByName.entrySet()) {
                addDistinct(merged.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()), entry.getValue());
            }
        }
        for (FunctionSymbol member : this.declaredMembers) {
            // A member declared here hides every inherited member of the same name, so an interface
            // that restates an extended default or requirement is not itself a conflict.
            merged.put(member.name(), new ArrayList<>(List.of(member)));
        }
        merged.replaceAll((name, list) -> List.copyOf(list));
        this.membersByName.putAll(merged);
    }

    /** Appends every element of {@code incoming} that is not already present by identity. */
    private static void addDistinct(List<FunctionSymbol> target, List<FunctionSymbol> incoming) {
        for (FunctionSymbol candidate : incoming) {
            boolean seen = false;
            for (FunctionSymbol existing : target) {
                if (existing == candidate) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                target.add(candidate);
            }
        }
    }

    public InterfaceDeclNode declaration() {
        return declaration;
    }

    /** The nominal compile-time type of this interface. */
    public InterfaceType type() {
        return type;
    }

    /** The interfaces named directly in this interface's {@code extends} clause. */
    public List<InterfaceSymbol> superInterfaces() {
        return superInterfaces;
    }

    /** Only the members declared directly by this interface, in source order. */
    public List<FunctionSymbol> declaredMembers() {
        return declaredMembers;
    }

    /** Every member visible through this interface: inherited first, then declared. */
    public List<FunctionSymbol> members() {
        List<FunctionSymbol> all = new ArrayList<>();
        for (List<FunctionSymbol> group : membersByName.values()) {
            all.addAll(group);
        }
        return List.copyOf(all);
    }

    /**
     * Every member of this name visible through this interface. A member declared by this interface
     * replaces inherited ones, so more than one entry means the name arrives as distinct members along
     * several extension paths; that becomes an error only for a conforming class that cannot resolve
     * which implementation applies.
     */
    public List<FunctionSymbol> membersNamed(String name) {
        return membersByName.getOrDefault(name, List.of());
    }

    /** The first member this interface exposes for {@code name}, or empty when none does. */
    public Optional<FunctionSymbol> member(String name) {
        List<FunctionSymbol> found = membersByName.get(name);
        return found == null || found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    /**
     * Whether one extension path supplies a body for every member {@code requirement} demands, so an
     * extension that restates it adds no new work for a conforming class.
     */
    public boolean defaultSupplies(FunctionSymbol requirement) {
        for (FunctionSymbol member : membersNamed(requirement.name())) {
            if (member != requirement && member.hasImplementation()) {
                return true;
            }
        }
        return false;
    }
}
