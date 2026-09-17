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

/**
 * Runtime metadata for one declared enum variant (docs/LANGUAGE_SPEC.md section 12): its owning
 * enum, its name, and its positional value count. Variant identity is nominal, so {@code ==} on two
 * enum values compares their variants by reference.
 */
public final class SolvikEnumVariant {

    private final SolvikEnumClass owner;
    private final String name;
    private final int valueCount;

    public SolvikEnumVariant(SolvikEnumClass owner, String name, int valueCount) {
        this.owner = Objects.requireNonNull(owner);
        this.name = Objects.requireNonNull(name);
        this.valueCount = valueCount;
    }

    public SolvikEnumClass owner() {
        return owner;
    }

    public String name() {
        return name;
    }

    /** The number of positional values this variant carries. */
    public int valueCount() {
        return valueCount;
    }

    @Override
    public String toString() {
        return owner.name() + "." + name;
    }
}
