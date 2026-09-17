/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.solvik.test.SolvikTestSupport.parseOk;

import java.util.List;
import org.junit.Test;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.expression.ExpressionNode;
import org.solvik.ast.statement.LocalDeclNode;
import org.solvik.ast.statement.ReturnStmtNode;
import org.solvik.ast.statement.StatementNode;
import org.solvik.regex.RegexPattern;
import org.solvik.semantic.CheckedProgram;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;
import org.solvik.type.AnyType;
import org.solvik.type.BooleanType;
import org.solvik.type.IntType;
import org.solvik.type.ListType;
import org.solvik.type.ObjectType;
import org.solvik.type.RegexMatchType;
import org.solvik.type.RegexType;
import org.solvik.type.StringType;
import org.solvik.type.Type;

/**
 * Positive Phase 14 semantic tests (docs/LANGUAGE_SPEC.md section 14): the built-in {@code Regex}
 * and {@code RegexMatch} types, their statically typed API, raw and normal string constants, and the
 * one-time compilation of a constant pattern.
 */
public final class SolvikRegexSemanticTest {

    private static CheckedProgram check(String text) {
        CompilationUnitNode unit = parseOk("regex.sol", text);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertTrue("analysis must succeed: " + result.diagnostics().all(), result.isSuccess());
        return result.requireProgram();
    }

    private static FunctionDeclNode function(CheckedProgram program, String name) {
        for (var declaration : program.unit().declarations()) {
            if (declaration instanceof FunctionDeclNode function && function.name().equals(name)) {
                return function;
            }
        }
        throw new AssertionError("no function named " + name);
    }

    private static ExpressionNode localInitializer(CheckedProgram program, String functionName, int index) {
        StatementNode statement;
        if ("main".equals(functionName)) {
            statement = program.unit().statements().get(index);
        } else {
            statement = function(program, functionName).body().statements().get(index);
        }
        return ((LocalDeclNode) statement).initializer();
    }

    private static Type typeOfLocal(CheckedProgram program, String functionName, int index) {
        return program.typeOf(localInitializer(program, functionName, index)).orElseThrow();
    }

    private static Type typeOfReturn(CheckedProgram program, String functionName, int index) {
        ReturnStmtNode statement = (ReturnStmtNode) function(program, functionName).body().statements().get(index);
        return program.typeOf(statement.value().orElseThrow()).orElseThrow();
    }

    @Test
    public void regexConstructionHasTheRegexType() {
        CheckedProgram program = check("""
                    val re = Regex("a")
                """);
        assertSame(RegexType.INSTANCE, typeOfLocal(program, "main", 0));
        assertTrue(RegexType.INSTANCE.isSubtypeOf(ObjectType.INSTANCE));
        assertTrue(RegexType.INSTANCE.isSubtypeOf(AnyType.INSTANCE));
        assertTrue(RegexMatchType.INSTANCE.isSubtypeOf(ObjectType.INSTANCE));
        assertTrue(RegexMatchType.INSTANCE.isSubtypeOf(AnyType.INSTANCE));
    }

    @Test
    public void matchesReturnsBoolean() {
        CheckedProgram program = check("""
                func matched(re: Regex): Boolean {
                    return re.matches("123")
                }
                """);
        assertSame(BooleanType.INSTANCE, typeOfReturn(program, "matched", 0));
    }

    @Test
    public void findReturnsANullableRegexMatch() {
        CheckedProgram program = check("""
                func first(re: Regex): RegexMatch? {
                    return re.find("123")
                }
                """);
        assertSame(RegexMatchType.INSTANCE.nullableView(), typeOfReturn(program, "first", 0));
    }

    @Test
    public void findAllReturnsAListOfRegexMatch() {
        CheckedProgram program = check("""
                func all(re: Regex): List<RegexMatch> {
                    return re.findAll("123")
                }
                """);
        assertSame(ListType.INSTANCE.parameterizedView(List.of(RegexMatchType.INSTANCE)), typeOfReturn(program, "all", 0));
    }

    @Test
    public void replaceReturnsString() {
        CheckedProgram program = check("""
                func scrub(re: Regex): String {
                    return re.replace("a1b2", "#")
                }
                """);
        assertSame(StringType.INSTANCE, typeOfReturn(program, "scrub", 0));
    }

    @Test
    public void regexMatchPropertiesAndMembersAreTyped() {
        CheckedProgram program = check("""
                func value(m: RegexMatch): String {
                    return m.value
                }

                func start(m: RegexMatch): Int {
                    return m.start
                }

                func end(m: RegexMatch): Int {
                    return m.end
                }

                func count(m: RegexMatch): Int {
                    return m.groupCount
                }

                func group(m: RegexMatch): String? {
                    return m.group(0)
                }
                """);
        assertSame(StringType.INSTANCE, typeOfReturn(program, "value", 0));
        assertSame(IntType.INSTANCE, typeOfReturn(program, "start", 0));
        assertSame(IntType.INSTANCE, typeOfReturn(program, "end", 0));
        assertSame(IntType.INSTANCE, typeOfReturn(program, "count", 0));
        assertSame(StringType.INSTANCE.nullableView(), typeOfReturn(program, "group", 0));
    }

    @Test
    public void findAllElementsAndSizeAreUsable() {
        CheckedProgram program = check("""
                func first(re: Regex): RegexMatch {
                    return re.findAll("a").get(0)
                }

                func count(re: Regex): Int {
                    return re.findAll("a").size
                }
                """);
        assertSame(RegexMatchType.INSTANCE, typeOfReturn(program, "first", 0));
        assertSame(IntType.INSTANCE, typeOfReturn(program, "count", 0));
    }

    @Test
    public void constantPatternsAreCompiledOnceDuringAnalysis() {
        CheckedProgram program = check("""
                    val re = Regex(r#"^\\d+$"#)
                    val other = Regex("a\\\\d")
                """);
        ExpressionNode raw = localInitializer(program, "main", 0);
        RegexPattern rawPattern = program.regexConstantOf(raw).orElseThrow();
        assertEquals("^\\d+$", rawPattern.source());
        assertTrue(rawPattern.compiled().matcher("123").matches());
        assertFalse(rawPattern.compiled().matcher("12a").matches());
        RegexPattern normalPattern = program.regexConstantOf(localInitializer(program, "main", 1)).orElseThrow();
        assertEquals("a\\d", normalPattern.source());
        assertTrue(normalPattern.compiled().matcher("a7").matches());
    }

    @Test
    public void dynamicPatternsAreNotCompiledDuringAnalysis() {
        CheckedProgram program = check("""
                func make(): String {
                    return "a"
                }

                    val re = Regex(make())
                """);
        assertTrue(program.regexConstantOf(localInitializer(program, "main", 0)).isEmpty());
    }

    @Test
    public void constantPatternsAreCompiledForEveryConstantPosition() {
        CheckedProgram program = check("""
                    val a = Regex("(a)")
                    val b = Regex((r#"\\d"#))
                """);
        assertTrue(program.regexConstantOf(localInitializer(program, "main", 0)).isPresent());
        assertTrue(program.regexConstantOf(localInitializer(program, "main", 1)).isPresent());
    }

    @Test
    public void regexTypesSupportTypeTestsAndCasts() {
        CheckedProgram program = check("""
                func isRegex(value: Any): Boolean {
                    return value is Regex
                }

                func asRegex(value: Any): Regex {
                    return value as Regex
                }

                func isMatch(value: Any): Boolean {
                    return value is RegexMatch
                }

                func asMatch(value: Any): RegexMatch? {
                    return value as RegexMatch
                }
                """);
        assertSame(BooleanType.INSTANCE, typeOfReturn(program, "isRegex", 0));
        assertSame(RegexType.INSTANCE, typeOfReturn(program, "asRegex", 0));
        assertSame(BooleanType.INSTANCE, typeOfReturn(program, "isMatch", 0));
        assertSame(RegexMatchType.INSTANCE, typeOfReturn(program, "asMatch", 0));
    }

    @Test
    public void safeAccessOnNullableRegexAndMatchKeepsNullability() {
        CheckedProgram program = check("""
                func call(re: Regex?): Boolean? {
                    return re?.matches("a")
                }

                func value(m: RegexMatch?): String? {
                    return m?.value
                }

                func group(m: RegexMatch?): String? {
                    return m?.group(1)
                }
                """);
        assertSame(BooleanType.INSTANCE.nullableView(), typeOfReturn(program, "call", 0));
        assertSame(StringType.INSTANCE.nullableView(), typeOfReturn(program, "value", 0));
        assertSame(StringType.INSTANCE.nullableView(), typeOfReturn(program, "group", 0));
    }

    @Test
    public void regexValuesFlowThroughFunctionsAndBindings() {
        CheckedProgram program = check("""
                func build(): Regex {
                    return Regex(r#"\\w+"#)
                }

                func accept(re: Regex): Boolean {
                    return re.matches("word")
                }

                    val re: Regex = build()
                    val ok: Boolean = accept(re)
                """);
        assertSame(RegexType.INSTANCE, typeOfReturn(program, "build", 0));
        assertSame(BooleanType.INSTANCE, typeOfReturn(program, "accept", 0));
        assertSame(BooleanType.INSTANCE, typeOfLocal(program, "main", 1));
    }
}
