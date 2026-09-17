/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
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
