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

/**
 * One class-level storage cell for a {@code static} property (docs/LANGUAGE_SPEC.md section 7). The
 * cell belongs to the declaring {@link SolvikClass} rather than to any object, so every read of the
 * member observes the latest write to it from anywhere in the program.
 *
 * <p>The value is boxed, exactly as an instance property value is boxed in the object's property
 * array, so a {@code static var} of any declared type stores and reloads without a representation
 * change. The declared type is enforced at compile time and is not rechecked at run time, matching how
 * instance properties are stored.
 */
public final class SolvikStaticCell {

    /**
     * The current value. Solvik programs are single-threaded and class initialization completes before
     * the first statement of the program's entry point, so no guest code can observe a partially
     * initialized cell; {@code volatile} additionally publishes the initializing write ahead of any
     * later read from runtime-compiled code.
     */
    private volatile Object value;

    public SolvikStaticCell(Object initialValue) {
        this.value = initialValue;
    }

    public Object get() {
        return value;
    }

    public void set(Object newValue) {
        this.value = newValue;
    }
}
