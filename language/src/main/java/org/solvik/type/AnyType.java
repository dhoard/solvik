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
 * The top type {@code Any}: every non-null Solvik value is assignable to it, and it is not
 * assignable to any type except itself. Assigning to {@code Any} never disables static checking
 * (docs/LANGUAGE_SPEC.md sections 3 and 4).
 */
public final class AnyType extends Type {

    public static final AnyType INSTANCE = new AnyType();

    private AnyType() {
        super("Any");
    }
}
