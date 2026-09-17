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
package org.solvik.truffle;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

/**
 * The single runtime value of the Solvik {@code Unit} type (docs/LANGUAGE_SPEC.md section 4). A
 * function that returns normally without a value produces this singleton. It is visible to interop
 * as a null-like value so evaluating a unit-returning {@code main} produces no result.
 */
@ExportLibrary(InteropLibrary.class)
public final class SolvikUnit implements TruffleObject {

    /** The one and only {@code Unit} value. */
    public static final SolvikUnit INSTANCE = new SolvikUnit();

    private SolvikUnit() {
    }

    @ExportMessage
    boolean isNull() {
        return true;
    }

    @ExportMessage
    Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return "Unit";
    }

    @Override
    public String toString() {
        return "Unit";
    }
}
