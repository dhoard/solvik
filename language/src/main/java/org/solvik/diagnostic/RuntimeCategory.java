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
package org.solvik.diagnostic;

/**
 * A stable, machine-readable classification of a Solvik runtime failure, exported to interop so a
 * consuming boundary (the launcher's structured execute channel, and beyond it the TCK adapters) reads
 * the failure kind as a field rather than by matching human-readable text.
 *
 * <p>The spelling of each constant is part of the diagnostic contract and matches the runtime-category
 * vocabulary the TCK protocol and manifest schemas expect; a name, once introduced, must keep
 * identifying the same class of failure even if the human message changes.
 */
public enum RuntimeCategory {
    /** Integer arithmetic overflow or division by zero. */
    ARITHMETIC_ERROR,
    /** A checked cast ({@code as}) to an incompatible type failed at run time. */
    CAST_FAILURE,
    /** A nullable value was dereferenced without a null check. */
    NULL_DEREFERENCE,
    /** A collection operation failed (missing {@code Map} key, empty {@code Stack}, unknown member). */
    COLLECTION_FAILURE,
    /** A {@code List} index was out of range. */
    INDEX_OUT_OF_BOUNDS,
    /** A dynamically constructed {@code Regex} pattern was invalid. */
    REGEX_FAILURE,
    /** A {@code Result.unwrap}/{@code unwrapErr}/{@code expect} was applied to the wrong variant. */
    RESULT_WRONG_VARIANT,
    /** A thrown guest value escaped every handler at the program boundary. */
    UNCAUGHT_EXCEPTION,
    /** A runtime failure with no more specific stable classification. */
    OTHER_RUNTIME_ERROR
}
