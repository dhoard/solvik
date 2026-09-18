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
package org.solvik.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.solvik.test.SolvikTestSupport.parseOk;

import java.util.List;
import org.junit.jupiter.api.Test;
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
import org.solvik.type.BuiltinCollectionTypes;
import org.solvik.type.IntType;
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
        assertThat(result.isSuccess()).as("analysis must succeed: " + result.diagnostics().all()).isTrue();
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
        assertThat(typeOfLocal(program, "main", 0)).isSameAs(RegexType.INSTANCE);
        assertThat(RegexType.INSTANCE.isSubtypeOf(ObjectType.INSTANCE)).isTrue();
        assertThat(RegexType.INSTANCE.isSubtypeOf(AnyType.INSTANCE)).isTrue();
        assertThat(RegexMatchType.INSTANCE.isSubtypeOf(ObjectType.INSTANCE)).isTrue();
        assertThat(RegexMatchType.INSTANCE.isSubtypeOf(AnyType.INSTANCE)).isTrue();
    }

    @Test
    public void matchesReturnsBoolean() {
        CheckedProgram program = check("""
                func matched(re: Regex): Boolean {
                    return re.matches("123")
                }
                """);
        assertThat(typeOfReturn(program, "matched", 0)).isSameAs(BooleanType.INSTANCE);
    }

    @Test
    public void findReturnsANullableRegexMatch() {
        CheckedProgram program = check("""
                func first(re: Regex): RegexMatch? {
                    return re.find("123")
                }
                """);
        assertThat(typeOfReturn(program, "first", 0)).isSameAs(RegexMatchType.INSTANCE.nullableView());
    }

    @Test
    public void findAllReturnsAListOfRegexMatch() {
        CheckedProgram program = check("""
                func all(re: Regex): List<RegexMatch> {
                    return re.findAll("123")
                }
                """);
        assertThat(typeOfReturn(program, "all", 0)).isSameAs(BuiltinCollectionTypes.LIST.parameterizedView(List.of(RegexMatchType.INSTANCE)));
    }

    @Test
    public void replaceReturnsString() {
        CheckedProgram program = check("""
                func scrub(re: Regex): String {
                    return re.replace("a1b2", "#")
                }
                """);
        assertThat(typeOfReturn(program, "scrub", 0)).isSameAs(StringType.INSTANCE);
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
        assertThat(typeOfReturn(program, "value", 0)).isSameAs(StringType.INSTANCE);
        assertThat(typeOfReturn(program, "start", 0)).isSameAs(IntType.INSTANCE);
        assertThat(typeOfReturn(program, "end", 0)).isSameAs(IntType.INSTANCE);
        assertThat(typeOfReturn(program, "count", 0)).isSameAs(IntType.INSTANCE);
        assertThat(typeOfReturn(program, "group", 0)).isSameAs(StringType.INSTANCE.nullableView());
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
        assertThat(typeOfReturn(program, "first", 0)).isSameAs(RegexMatchType.INSTANCE);
        assertThat(typeOfReturn(program, "count", 0)).isSameAs(IntType.INSTANCE);
    }

    @Test
    public void constantPatternsAreCompiledOnceDuringAnalysis() {
        CheckedProgram program = check("""
                    val re = Regex(r#"^\\d+$"#)
                    val other = Regex("a\\\\d")
                """);
        ExpressionNode raw = localInitializer(program, "main", 0);
        RegexPattern rawPattern = program.regexConstantOf(raw).orElseThrow();
        assertThat(rawPattern.source()).isEqualTo("^\\d+$");
        assertThat(rawPattern.compiled().matcher("123").matches()).isTrue();
        assertThat(rawPattern.compiled().matcher("12a").matches()).isFalse();
        RegexPattern normalPattern = program.regexConstantOf(localInitializer(program, "main", 1)).orElseThrow();
        assertThat(normalPattern.source()).isEqualTo("a\\d");
        assertThat(normalPattern.compiled().matcher("a7").matches()).isTrue();
    }

    @Test
    public void dynamicPatternsAreNotCompiledDuringAnalysis() {
        CheckedProgram program = check("""
                func make(): String {
                    return "a"
                }

                    val re = Regex(make())
                """);
        assertThat(program.regexConstantOf(localInitializer(program, "main", 0)).isEmpty()).isTrue();
    }

    @Test
    public void constantPatternsAreCompiledForEveryConstantPosition() {
        CheckedProgram program = check("""
                    val a = Regex("(a)")
                    val b = Regex((r#"\\d"#))
                """);
        assertThat(program.regexConstantOf(localInitializer(program, "main", 0)).isPresent()).isTrue();
        assertThat(program.regexConstantOf(localInitializer(program, "main", 1)).isPresent()).isTrue();
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
        assertThat(typeOfReturn(program, "isRegex", 0)).isSameAs(BooleanType.INSTANCE);
        assertThat(typeOfReturn(program, "asRegex", 0)).isSameAs(RegexType.INSTANCE);
        assertThat(typeOfReturn(program, "isMatch", 0)).isSameAs(BooleanType.INSTANCE);
        assertThat(typeOfReturn(program, "asMatch", 0)).isSameAs(RegexMatchType.INSTANCE);
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
        assertThat(typeOfReturn(program, "call", 0)).isSameAs(BooleanType.INSTANCE.nullableView());
        assertThat(typeOfReturn(program, "value", 0)).isSameAs(StringType.INSTANCE.nullableView());
        assertThat(typeOfReturn(program, "group", 0)).isSameAs(StringType.INSTANCE.nullableView());
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
        assertThat(typeOfReturn(program, "build", 0)).isSameAs(RegexType.INSTANCE);
        assertThat(typeOfReturn(program, "accept", 0)).isSameAs(BooleanType.INSTANCE);
        assertThat(typeOfLocal(program, "main", 1)).isSameAs(BooleanType.INSTANCE);
    }
}
