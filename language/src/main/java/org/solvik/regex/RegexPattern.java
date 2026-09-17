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
package org.solvik.regex;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A validated Solvik regex pattern together with the engine representation compiled from it
 * (docs/LANGUAGE_SPEC.md section 14). One instance is created per compiled pattern: static analysis
 * creates it once for a source constant so lowering can reuse it for every execution, and the
 * runtime creates it when a dynamically constructed pattern is compiled. Compilation itself lives in
 * {@link RegexSyntax}, which owns the portable dialect.
 */
public final class RegexPattern {

    private final String source;
    private final Pattern compiled;

    RegexPattern(String source, Pattern compiled) {
        this.source = Objects.requireNonNull(source, "source");
        this.compiled = Objects.requireNonNull(compiled, "compiled");
    }

    /** The pattern text this value was compiled from. */
    public String source() {
        return source;
    }

    /** The engine representation, compiled exactly once for this pattern. */
    public Pattern compiled() {
        return compiled;
    }
}
