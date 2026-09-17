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
package org.solvik.truffle.object;

import java.util.Objects;
import org.solvik.type.EnumType;

/**
 * Runtime metadata for a Solvik enum (docs/LANGUAGE_SPEC.md section 12). It carries the enum's
 * identity, the compiler's nominal {@link EnumType} (used by {@code is}/{@code as} tests because
 * generic arguments are erased), and its name for display.
 *
 * <p>Instances are created once per lowered enum declaration. The complete variant set lives in the
 * compiler's {@code EnumSymbol}; runtime construction is statically resolved and passes the selected
 * variant directly, so no runtime name lookup is needed.
 */
public final class SolvikEnumClass {

    private final String name;
    private final EnumType type;

    public SolvikEnumClass(String name, EnumType type) {
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
    }

    public String name() {
        return name;
    }

    /** The compile-time nominal type of this enum, compared by identity in runtime type tests. */
    public EnumType type() {
        return type;
    }

    @Override
    public String toString() {
        return name;
    }
}
