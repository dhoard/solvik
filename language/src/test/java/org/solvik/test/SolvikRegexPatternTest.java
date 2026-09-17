/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.solvik.regex.RegexPattern;
import org.solvik.regex.RegexSyntax;

/**
 * Phase 14 tests for the portable Solvik regex dialect (docs/LANGUAGE_SPEC.md section 14): the
 * supported constructs compile, while backreferences, lookaround, embedded flags, and other
 * engine-specific extensions are rejected before the engine sees the pattern.
 */
public final class SolvikRegexPatternTest {

    private static RegexPattern compile(String pattern) {
        return RegexSyntax.compile(pattern);
    }

    private static void accepts(String pattern) {
        assertTrue("pattern must be portable: " + pattern, RegexSyntax.unsupported(pattern).isEmpty());
        // An accepted pattern must also be acceptable to the engine.
        assertEquals(pattern, compile(pattern).source());
    }

    private static String rejection(String pattern) {
        assertTrue("pattern must be rejected: " + pattern, RegexSyntax.unsupported(pattern).isPresent());
        RegexSyntax.InvalidPatternException failure = null;
        try {
            compile(pattern);
        } catch (RegexSyntax.InvalidPatternException e) {
            failure = e;
        }
        assertTrue("compiling a rejected pattern must fail: " + pattern, failure != null);
        assertTrue("the diagnostic must be non-empty", failure.getMessage() != null && !failure.getMessage().isEmpty());
        return failure.getMessage();
    }

    @Test
    public void literalsAndWildcardsAreSupported() {
        accepts("abc");
        accepts(".");
        accepts("^abc$");
    }

    @Test
    public void characterClassesAndRangesAreSupported() {
        accepts("[abc]");
        accepts("[^abc]");
        accepts("[A-Za-z0-9_]");
        accepts("[a-]");
    }

    @Test
    public void capturingGroupsAndAlternationAreSupported() {
        accepts("(a|b)");
        accepts("((a)(b))");
        accepts("^$|x");
    }

    @Test
    public void quantifiersAreSupported() {
        accepts("a*");
        accepts("a+");
        accepts("a?");
        accepts("a{3}");
        accepts("a{3,}");
        accepts("a{3,5}");
        accepts("a{0,0}");
    }

    @Test
    public void asciiClassesAndNegationsAreSupported() {
        accepts("\\d\\D\\s\\S\\w\\W");
        accepts("^\\d+$");
        accepts("[\\d\\s]+");
    }

    @Test
    public void escapedPunctuationAndControlCharactersAreSupported() {
        accepts("\\.");
        accepts("\\*\\(\\)\\[\\]\\{\\}\\|\\^\\$\\\\");
        accepts("\\n\\r\\t");
    }

    @Test
    public void rawStringPatternsCompile() {
        RegexPattern pattern = compile("^\\d+\\s+\\w+$");
        assertTrue(pattern.compiled().matcher("42 words").matches());
        assertFalse(pattern.compiled().matcher("words 42").matches());
    }

    @Test
    public void lookaroundAndGroupExtensionsAreRejected() {
        rejection("(?=x)");
        rejection("(?!x)");
        rejection("(?<=x)");
        rejection("(?<!x)");
        rejection("(?:x)");
        rejection("(?<name>x)");
        rejection("(?i)abc");
        rejection("a(?i:b)c");
    }

    @Test
    public void backreferencesAreRejected() {
        rejection("(a)\\1");
        rejection("(a)\\k<name>");
    }

    @Test
    public void anchorAndUnicodeEscapesAreRejected() {
        rejection("\\bword\\b");
        rejection("\\Aword\\z");
        rejection("\\p{L}+");
        rejection("\\R");
    }

    @Test
    public void engineSpecificQuantifiersAndClassesAreRejected() {
        rejection("a*+");
        rejection("a*?");
        rejection("a{2,}+");
        rejection("[a&&b]");
    }

    @Test
    public void structurallyInvalidPatternsAreRejected() {
        rejection("(");
        rejection(")");
        rejection("[abc");
        rejection("[]");
        rejection("{");
        rejection("*");
        rejection("a{2,1}");
        rejection("\\");
    }

    @Test
    public void rejectedPatternsReportAPosition() {
        assertTrue(rejection("(?=x)").contains("pattern index"));
        assertTrue(rejection("(a)\\1").contains("\\1"));
    }
}
