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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.parser.SolvikParseResult;
import org.solvik.parser.SolvikParser;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;
import org.solvik.source.SourceFile;

/**
 * Character literals (docs/LANGUAGE_SPEC.md section 1). A character literal is a single Unicode
 * scalar value or one of the normal string escapes, and it was not previously exercised beyond a
 * plain ASCII character. These tests pin escape decoding, display, and the lexical rejection of an
 * invalid escape, an empty literal, and a multi-character literal.
 */
public final class SolvikCharacterLiteralExecutionTest {

    @Test
    public void everySupportedCharacterEscapeDecodesAtRuntime() {
        assertThat(run("""
                    println('\\n')
                    println('\\r')
                    println('\\t')
                    println('\\0')
                    println('\\\\')
                    println('\\'')
                """)).isEqualTo("\n\n\r\n\t\n\0\n\\\n'\n");
    }

    @Test
    public void escapedCharactersCompareByValue() {
        assertThat(run("""
                    println('\\n' == '\\n')
                    println('\\n' != '\\t')
                """)).isEqualTo("true\ntrue\n");
    }

    @Test
    public void charactersConcatenateAndDisplay() {
        assertThat(run("""
                    val letter = 'A'
                    println(letter)
                    println(letter .. "B" .. 'C')
                """)).isEqualTo("A\nABC\n");
    }

    @Test
    public void invalidCharacterEscapeIsRejected() {
        assertThat(firstCode("func f(): Unit {\n    val c = '\\q'\n}\n")).isEqualTo(DiagnosticCode.LEXER_INVALID_ESCAPE);
    }

    @Test
    public void emptyCharacterLiteralIsRejected() {
        assertThat(hasError("func f(): Unit {\n    val c = ''\n}\n")).isTrue();
    }

    @Test
    public void multiCharacterCharacterLiteralIsRejected() {
        assertThat(firstCode("func f(): Unit {\n    val c = 'ab'\n}\n")).isEqualTo(DiagnosticCode.LEXER_ERROR);
    }

    /** Parses and analyzes {@code text}, returning the first diagnostic code from whichever stage fails. */
    private static DiagnosticCode firstCode(String text) {
        SolvikParseResult parsed = SolvikParser.parse(new SourceFile("char.sol", text));
        if (!parsed.isSuccess()) {
            assertThat(parsed.diagnostics().hasErrors()).as("failed parse must carry diagnostics").isTrue();
            return parsed.diagnostics().all().get(0).code();
        }
        SemanticResult result = SolvikSemanticAnalyzer.analyze(parsed.requireAst());
        assertThat(result.isSuccess()).as("analysis must fail: " + text).isFalse();
        return result.diagnostics().all().get(0).code();
    }

    private static boolean hasError(String text) {
        SolvikParseResult parsed = SolvikParser.parse(new SourceFile("char.sol", text));
        if (!parsed.isSuccess()) {
            return parsed.diagnostics().hasErrors();
        }
        return SolvikSemanticAnalyzer.analyze(parsed.requireAst()).diagnostics().hasErrors();
    }

    private static String run(String source) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            context.eval(build(source));
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static Source build(String source) {
        try {
            return Source.newBuilder("solvik", source, "char.sol").build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
