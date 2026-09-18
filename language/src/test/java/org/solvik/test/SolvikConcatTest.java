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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.statement.ReturnStmtNode;
import org.solvik.diagnostic.Diagnostic;
import org.solvik.diagnostic.DiagnosticBag;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.semantic.CheckedProgram;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;
import org.solvik.type.StringType;

/**
 * Tests for {@code ..} string concatenation (docs/LANGUAGE_SPEC.md section 3): both operands are
 * rendered through {@code toString}, the result is always {@code String}, arithmetic binds tighter,
 * {@code null} renders as {@code "null"}, and {@code +} no longer concatenates strings.
 */
public final class SolvikConcatTest {

    private static CheckedProgram check(String text) {
        CompilationUnitNode unit = parseOk("concat.sol", text);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertThat(result.isSuccess()).as("analysis must succeed: " + result.diagnostics().all()).isTrue();
        return result.requireProgram();
    }

    private static DiagnosticBag checkFails(String text) {
        CompilationUnitNode unit = parseOk("concatneg.sol", text);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertThat(result.isSuccess()).as("analysis must fail: " + text).isFalse();
        assertThat(result.diagnostics().hasErrors()).as("failed analysis must carry diagnostics").isTrue();
        for (Diagnostic diagnostic : result.diagnostics().all()) {
            assertThat(diagnostic.span().endOffset() <= text.length()).as("span within source bounds: " + diagnostic.span()).isTrue();
        }
        return result.diagnostics();
    }

    private static Diagnostic first(DiagnosticBag bag) {
        List<Diagnostic> all = bag.all();
        assertThat(all.isEmpty()).isFalse();
        return all.get(0);
    }

    private static String run(String source) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            context.eval(build(source, "concat.sol"));
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static Source build(String source, String name) {
        try {
            return Source.newBuilder("solvik", source, name).build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    public void concatResultIsAlwaysString() {
        CheckedProgram program = check("func f(): String {\n    return 1 .. 2\n}\n");
        FunctionDeclNode fn = (FunctionDeclNode) program.unit().declarations().get(0);
        ReturnStmtNode statement = (ReturnStmtNode) fn.body().statements().get(0);
        assertThat(program.typeOf(statement.value().orElseThrow()).orElseThrow()).isEqualTo(StringType.INSTANCE);
    }

    @Test
    public void concatAcceptsAnyNonNullOperandTypes() {
        check("func f(x: Any, y: Any, n: Int, b: Boolean): String {\n    return x .. y .. n .. b\n}\n");
    }

    @Test
    public void plusOnStringsIsRejected() {
        assertThat(first(checkFails("func f(): String {\n    return \"a\" + \"b\"\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_INVALID_OPERANDS);
    }

    @Test
    public void concatenatesUsingToString() {
        assertThat(run("""
                println("a" .. "b")
                println(1 .. "x")
                println(true .. "!")
                println('c' .. "d")
                println("[" .. 2.5 .. "]")
                """)).isEqualTo("ab\n1x\ntrue!\ncd\n[2.5]\n");
    }

    @Test
    public void arithmeticBindsTighterThanConcat() {
        assertThat(run("""
                println(1 + 2 .. "x")
                println("x" .. 1 + 2)
                println(1 .. 2 == "12")
                """)).isEqualTo("3x\nx3\ntrue\n");
    }

    @Test
    public void nullConcatenatesAsNull() {
        assertThat(run("""
                val s: String? = null
                println("[" .. s .. "]")
                """)).isEqualTo("[null]\n");
    }

    @Test
    public void objectOverrideParticipatesInConcatenation() {
        assertThat(run("""
                class Tag {
                    override func toString(): String {
                        return "TAG"
                    }
                }
                println("<" .. Tag() .. ">")
                """)).isEqualTo("<TAG>\n");
    }
}
