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
package org.solvik.ast.pattern;

import java.util.List;
import java.util.Objects;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.source.SourceSpan;

/**
 * An enum variant pattern (docs/LANGUAGE_SPEC.md section 12): a variant name with an optional list
 * of nested argument patterns. A value-less variant is a bare name ({@code Red}); a value-carrying
 * variant names its positional values ({@code Ok(value)}), each of which is itself a binding,
 * wildcard, or nested variant pattern. Inside a {@code match} over a known enum the variant is
 * written unqualified.
 */
public final class EnumPatternNode extends PatternNode {

    private final String variantName;
    private final List<PatternNode> arguments;

    public EnumPatternNode(String variantName, List<PatternNode> arguments, SourceSpan span) {
        super(AstKind.ENUM_PATTERN, span);
        this.variantName = Objects.requireNonNull(variantName);
        this.arguments = List.copyOf(Objects.requireNonNull(arguments));
    }

    public String variantName() {
        return variantName;
    }

    public List<PatternNode> arguments() {
        return arguments;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<AstNode> children() {
        return List.copyOf((List) arguments);
    }
}
