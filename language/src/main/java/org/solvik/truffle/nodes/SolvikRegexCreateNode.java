/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.nodes;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.solvik.regex.RegexPattern;
import org.solvik.regex.RegexSyntax;
import org.solvik.truffle.SolvikException;
import org.solvik.truffle.object.SolvikRegex;

/**
 * A {@code Regex(pattern)} construction whose pattern is only known at run time
 * (docs/LANGUAGE_SPEC.md section 14). The pattern is validated against the portable dialect and
 * compiled when it is first seen; an invalid or unsupported pattern raises a Solvik runtime regex
 * error. The last compiled pattern is cached so a construction repeated with the same text does not
 * recompile (a source constant never reaches this node: it uses
 * {@link SolvikRegexLiteralNode}).
 */
@NodeInfo(shortName = "Regex", description = "A dynamically constructed Solvik Regex value")
public final class SolvikRegexCreateNode extends SolvikExpressionNode {

    @Child private SolvikExpressionNode pattern;
    private RegexPattern cached;

    public SolvikRegexCreateNode(SolvikExpressionNode pattern) {
        this.pattern = pattern;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        String source = (String) pattern.executeGeneric(frame);
        RegexPattern compiled = cached;
        if (compiled == null || !compiled.source().equals(source)) {
            compiled = compile(source);
            cached = compiled;
        }
        return new SolvikRegex(compiled.source(), compiled.compiled());
    }

    @TruffleBoundary
    private RegexPattern compile(String source) {
        try {
            return RegexSyntax.compile(source);
        } catch (RegexSyntax.InvalidPatternException e) {
            throw SolvikException.regexError(e.getMessage(), this);
        }
    }
}
