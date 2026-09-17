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

import java.util.List;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.ast.expression.LiteralNode;
import org.solvik.ast.expression.RawStringLiteralNode;
import org.solvik.ast.expression.StringLiteralNode;
import org.solvik.source.SourceSpan;

/**
 * A top-level {@code include <path>} directive (docs/LANGUAGE_SPEC.md section 20). It is a syntax
 * directive, not a declaration or an executable statement: it carries the written path literal
 * until include resolution replaces it with the included file's items.
 *
 * <p>The written path must be a normal or raw string literal because path decoding uses the string
 * escape rules; any other literal kind is rejected when the node is constructed.
 */
public final class IncludeDeclNode extends AstNode {

    private final LiteralNode path;
    private final String alias;

    public IncludeDeclNode(LiteralNode path, SourceSpan span) {
        this(path, null, span);
    }

    public IncludeDeclNode(LiteralNode path, String alias, SourceSpan span) {
        super(AstKind.INCLUDE_DECL, span);
        if (!(path instanceof StringLiteralNode) && !(path instanceof RawStringLiteralNode)) {
            throw new IllegalArgumentException("include path must be a string literal but was " + path.getClass().getSimpleName());
        }
        this.path = path;
        this.alias = alias;
    }

    /** The written path literal, still in its source spelling. */
    public LiteralNode pathLiteral() {
        return path;
    }

    /** The written {@code alias <name>} prefix, or {@code null} when the include is unaliased. */
    public String alias() {
        return alias;
    }

    /** Whether this include binds a file-local namespace prefix. */
    public boolean hasAlias() {
        return alias != null;
    }

    @Override
    public List<AstNode> children() {
        return List.of(path);
    }
}
