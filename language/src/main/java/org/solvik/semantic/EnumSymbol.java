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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.solvik.ast.declaration.EnumDeclNode;
import org.solvik.type.EnumType;

/**
 * A compiled enum descriptor (docs/ARCHITECTURE.md "Type System" {@code EnumType} and
 * docs/LANGUAGE_SPEC.md section 12): the nominal type, its declared type parameters, and its
 * complete variant set in source order. That variant set is the closed-variant metadata the
 * specification requires: the compiler knows every permitted constructor when the file is compiled,
 * which is what makes exhaustive {@code match} possible in a later phase.
 *
 * <p>{@link #variant(String)} resolves a qualified constructor name such as the {@code Ok} in
 * {@code Result.Ok(value)}; an unknown name is reported by the semantic pass as an unknown member.
 */
public final class EnumSymbol extends Symbol {

    private final EnumDeclNode declaration;
    private final EnumType type;
    private List<EnumVariantSymbol> variants = List.of();
    private final Map<String, EnumVariantSymbol> variantsByName = new LinkedHashMap<>();

    EnumSymbol(EnumDeclNode declaration, EnumType type) {
        super(declaration.name(), declaration.span());
        this.declaration = Objects.requireNonNull(declaration);
        this.type = Objects.requireNonNull(type);
    }

    /** Installs the complete variant set once every variant's value types have been resolved. */
    void resolveVariants(List<EnumVariantSymbol> resolved) {
        this.variants = List.copyOf(Objects.requireNonNull(resolved));
        for (EnumVariantSymbol variant : this.variants) {
            variantsByName.put(variant.name(), variant);
        }
    }

    public EnumDeclNode declaration() {
        return declaration;
    }

    /** The nominal compile-time type of values of this enum. */
    public EnumType type() {
        return type;
    }

    /** The complete permitted variant set in source order. */
    public List<EnumVariantSymbol> variants() {
        return variants;
    }

    /** The variant with the given constructor name, or empty when the enum has none. */
    public Optional<EnumVariantSymbol> variant(String name) {
        return Optional.ofNullable(variantsByName.get(name));
    }
}
