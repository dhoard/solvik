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
package org.solvik.type;

import java.util.Optional;

/**
 * The built-in {@code Char} type, a single Unicode scalar value (docs/LANGUAGE_SPEC.md sections 1
 * and 4). {@code Char} is not a numeric type: it has its own literals, compares by value, and
 * displays as its contents.
 */
public final class CharType extends Type {

    public static final CharType INSTANCE = new CharType();

    private CharType() {
        super("Char");
    }

    @Override
    public Optional<Type> superType() {
        return Optional.of(AnyType.INSTANCE);
    }
}
