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
package org.solvik.ast.statement;

/**
 * Local and property binding kinds.
 *
 * <p>There is one binding keyword, {@code var}; {@code mutable} is a modifier that makes the binding
 * writable (docs/LANGUAGE_SPEC.md section 2). The kinds therefore record the presence or absence of
 * that modifier rather than two spellings. The spelling is fixed at {@code var mutable}: the modifier
 * follows the keyword, so an immutable binding is a plain {@code var} and a bare {@code mutable} is a
 * compile-time error.
 */
public enum BindingKind {
    /** Immutable binding: a plain {@code var}. */
    IMMUTABLE,
    /** Writable binding: a {@code var mutable}. */
    MUTABLE;

    /** The kind denoted by a declaration that does, or does not, carry the {@code mutable} modifier. */
    public static BindingKind fromMutable(boolean mutable) {
        return mutable ? MUTABLE : IMMUTABLE;
    }
}
