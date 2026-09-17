/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.ast.pattern;

import java.util.List;
import java.util.Objects;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.source.SourceSpan;

/**
 * An enum variant pattern (docs/LANGUAGE_SPEC.md section 12): a variant name with an optional list
 * of nested argument patterns. A value-less variant is a bare name ({@code Red}); a value-carrying
 * variant names its positional values ({@code Ok(value)}), each of which is itself a binding,
 * wildcard, or nested variant pattern. Inside a {@code match} over a known enum the variant is
 * written unqualified.
 */
public final class EnumPatternNode extends PatternNode {

    private final String variantName;
    private final List<PatternNode> arguments;

    public EnumPatternNode(String variantName, List<PatternNode> arguments, SourceSpan span) {
        super(AstKind.ENUM_PATTERN, span);
        this.variantName = Objects.requireNonNull(variantName);
        this.arguments = List.copyOf(Objects.requireNonNull(arguments));
    }

    public String variantName() {
        return variantName;
    }

    public List<PatternNode> arguments() {
        return arguments;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<AstNode> children() {
        return List.copyOf((List) arguments);
    }
}
