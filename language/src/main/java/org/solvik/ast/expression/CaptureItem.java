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
package org.solvik.ast.expression;

import java.util.Objects;
import org.solvik.source.SourceSpan;

/**
 * One item of an anonymous function's explicit capture list (docs/LANGUAGE_SPEC.md section 6,
 * "Explicit immutable closure capture"): an identifier naming a binding of an enclosing function, or
 * {@code this}.
 *
 * <p>This is syntax, not a resolved reference. It records only what was written and where, because
 * which binding an item names is decided by the semantic layer at the closure-creation site — the same
 * site decides whether that binding is eligible to be captured, and reports the item when it is not.
 * Keeping the item unresolved is also what keeps the AST free of shared semantic state: the analyzer
 * records its decision about an item in its own tables, keyed by this node.
 *
 * <p>An item is one token wide by construction: it holds a name and no expression. That is the
 * structural reason capture aliases and arbitrary capture expressions cannot be written — the
 * specification supports neither, and an AST that cannot express them cannot silently accept them.
 *
 * <p>{@code this} is a keyword and so can never be an {@code Identifier}, which makes the written form
 * recoverable from the name alone; {@link #isThis()} is therefore derived rather than stored, and an
 * item cannot claim to be a receiver capture while naming something else.
 */
public record CaptureItem(String name, SourceSpan span) {

    /**
     * The lexeme of the receiver capture item. {@code this} is a keyword and so can never collide with an
     * identifier in a capture list, which is what lets one string identify the receiver case; it matches
     * the {@code THIS} token of the grammar and is shared with
     * {@link org.solvik.semantic.CapturedValue} rather than duplicated at each use.
     */
    public static final String THIS_LEXEME = "this";

    public CaptureItem {
        Objects.requireNonNull(name);
        Objects.requireNonNull(span);
    }

    /** Whether this item is the written {@code this} rather than an identifier. */
    public boolean isThis() {
        return name.equals(THIS_LEXEME);
    }
}
