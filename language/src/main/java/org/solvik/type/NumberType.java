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
 * The abstract numeric root {@code Number} (docs/LANGUAGE_SPEC.md section 4). It has no values of
 * its own; {@code Byte}, {@code Short}, {@code Int}, {@code Long}, {@code Float}, and {@code Double}
 * are its subtypes. Using {@code Number} as a declared type accepts any numeric value, but
 * arithmetic still requires same-type operands. It derives directly from {@code Any}.
 */
public final class NumberType extends Type {

    public static final NumberType INSTANCE = new NumberType();

    private NumberType() {
        super("Number");
    }

    @Override
    public Optional<Type> superType() {
        return Optional.of(AnyType.INSTANCE);
    }
}
