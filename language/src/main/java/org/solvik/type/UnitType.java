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
 * The internal no-value sentinel of a callable that completes without producing a value
 * (docs/LANGUAGE_SPEC.md section 6). It has no source-level spelling: a callable that writes no
 * {@code : Type} produces no value, and an expression whose type is this sentinel cannot be used
 * where a value is required.
 *
 * <p>It is deliberately not a subtype of {@code Any}: "produces no value" means the expression has no
 * value to pass on, so {@code var x: Any = f()} where {@code f} declares no result is rejected by
 * ordinary assignability rather than being silently treated as a value.
 */
public final class UnitType extends Type {

    public static final UnitType INSTANCE = new UnitType();

    private UnitType() {
        super("no value");
    }
}
