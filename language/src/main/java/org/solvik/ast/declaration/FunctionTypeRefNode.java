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
 * A written function type reference, {@code func(P1, P2): R}. Parameter names never appear; only
 * the parameter and return type references carry meaning, and they are ordinary {@link TypeRefNode}s.
 *
 * <p>An omitted return type is synthesized by the AST builder as a {@code Unit} type reference,
 * matching the rule that a callable with an omitted return type is {@code Unit}. Nullability of the
 * function value itself is recorded by {@link #isNullable()} and is spelled only as the grouped form
 * {@code (func(...): R)?}; {@code func(...): R?} instead makes the result nullable, so the grammar
 * preserves the distinction structurally rather than leaving it to be recovered from source text.
 */
public final class FunctionTypeRefNode extends TypeRef {

    private final List<TypeRef> parameterRefs;
    private final TypeRef returnTypeRef;
    private final boolean nullable;

    public FunctionTypeRefNode(List<TypeRef> parameterRefs, TypeRef returnTypeRef, boolean nullable, SourceSpan span) {
        super(AstKind.FUNCTION_TYPE_REF, span);
        this.parameterRefs = List.copyOf(parameterRefs);
        this.returnTypeRef = Objects.requireNonNull(returnTypeRef);
        this.nullable = nullable;
    }

    /** The written parameter type references, in source order; empty for a nullary function type. */
    public List<TypeRef> parameterRefs() {
        return parameterRefs;
    }

    /** The written return type reference, always present ({@code Unit} synthesized when omitted). */
    public TypeRef returnTypeRef() {
        return returnTypeRef;
    }

    /** Whether the whole function value is nullable, spelled only as the grouped {@code (func(...): R)?}. */
    public boolean isNullable() {
        return nullable;
    }

    /** The head name of a written function type is always {@code func}. */
    @Override
    public String name() {
        return "func";
    }

    @Override
    public List<AstNode> children() {
        ArrayList<AstNode> result = new ArrayList<>(parameterRefs.size() + 1);
        result.addAll(parameterRefs);
        result.add(returnTypeRef);
        return result;
    }
}
