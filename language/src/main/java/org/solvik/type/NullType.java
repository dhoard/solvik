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

/**
 * The type of the {@code null} literal (docs/LANGUAGE_SPEC.md section 5). It is the only type with a
 * value that is not a value of any non-null type: {@code null} is assignable only to a nullable type
 * {@code T?}, so this type is not a subtype of any non-null type and is deliberately absent from the
 * built-in type namespace.
 *
 * <p>Because the bottom type {@code Nothing} is a subtype of every type, {@code NullType} is not the
 * bottom type; it is a distinct nullable type with exactly one value.
 */
public final class NullType extends Type {

    public static final NullType INSTANCE = new NullType();

    private NullType() {
        super("Null");
    }

    @Override
    public boolean isNullable() {
        return true;
    }
}
