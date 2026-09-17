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
package org.solvik.ast.declaration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.source.SourceSpan;

/**
 * A written type reference: a simple name, optional generic type arguments, and, from Phase 10, an
 * optional {@code ?} marking the nullable type {@code T?} (docs/LANGUAGE_SPEC.md sections 5 and 11).
 * Phase 1 records the name only; resolution to the compiler type model happens in later phases.
 */
public final class TypeRefNode extends AstNode {

    private final String modulePrefix;
    private final String name;
    private final List<TypeRefNode> arguments;
    private final boolean nullable;

    public TypeRefNode(String name, SourceSpan span) {
        this(name, List.of(), false, span);
    }

    public TypeRefNode(String name, boolean nullable, SourceSpan span) {
        this(name, List.of(), nullable, span);
    }

    public TypeRefNode(String name, List<TypeRefNode> arguments, boolean nullable, SourceSpan span) {
        this(null, name, arguments, nullable, span);
    }

    /**
     * A written type with an optional module prefix ({@code prefix.Name}). The prefix is the single
     * identifier before the one allowed dot, or {@code null} for an unqualified type reference.
     */
    public TypeRefNode(String modulePrefix, String name, List<TypeRefNode> arguments, boolean nullable, SourceSpan span) {
        super(AstKind.TYPE_REF, span);
        this.modulePrefix = modulePrefix;
        this.name = Objects.requireNonNull(name);
        this.arguments = List.copyOf(arguments);
        this.nullable = nullable;
    }

    /** The written module prefix, or {@code null} for an unqualified type reference. */
    public String modulePrefix() {
        return modulePrefix;
    }

    /** Whether the reference was written with a module prefix. */
    public boolean hasModulePrefix() {
        return modulePrefix != null;
    }

    public String name() {
        return name;
    }

    /** The written generic type arguments, in source order; empty for a plain type reference. */
    public List<TypeRefNode> arguments() {
        return arguments;
    }

    /** Whether the reference was written with a trailing {@code ?}. */
    public boolean isNullable() {
        return nullable;
    }

    @Override
    public List<AstNode> children() {
        return new ArrayList<>(arguments);
    }
}
