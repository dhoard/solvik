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
import static org.solvik.test.SolvikTestSupport.parseFails;

import org.junit.jupiter.api.Test;
import org.solvik.diagnostic.Diagnostic;
import org.solvik.diagnostic.DiagnosticBag;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.source.SourceSpan;

/**
 * Negative tests for Rust-style raw strings (docs/LANGUAGE_SPEC.md section 15). A raw string is
 * well-formed only when the opening `r + N '#' + '"'` and the closing `'"' + N '#'` carry the same
 * hash count. When the opening delimiter carries more hashes than the closing one, the scan reaches
 * end of input and reports SOLV-LEX-002 at the opening delimiter, naming the exact `" + '#'*` closing
 * delimiter that was expected.
 */
public final class SolvikRawStringNegativeTest {

    private static Diagnostic lexError(DiagnosticBag bag, DiagnosticCode code) {
        return bag.all().stream().filter(d -> d.code() == code).findFirst().orElseThrow(
            () -> new AssertionError("missing " + code + " in " + bag.all()));
    }

    private static String funcTemplate(String literal) {
        return "func f(): Unit {\n    val s = " + literal + "\n}\n";
    }

    /**
     * `r"#"…"#` opens with an extra `#` that the closing delimiter does not carry, so the literal is
     * unterminated. The diagnostic names the plain `"` that was expected.
     */
    @Test
    public void rHashQuoteMismatchIsUnterminated() {
        String src = funcTemplate("r\"#abc\"#");
        DiagnosticBag bag = parseFails("neg.sol", src);
        assertThat(bag.hasErrors()).isTrue();
        Diagnostic d = lexError(bag, DiagnosticCode.LEXER_UNTERMINATED_RAW_STRING);
        assertThat(d.code().stableCode()).isEqualTo("SOLV-LEX-002");
        assertThat(d.expected().orElseThrow()).isEqualTo("\"");
        assertThat(d.message().contains("expected closing delimiter")).isTrue();
        int open = src.indexOf("r\"#");
        assertThat(d.span()).isEqualTo(SourceSpan.of(open, open + 2));
    }

    /** Opening with two hashes but a single-hash close expects `"##`. */
    @Test
    public void tooFewHashesInClosingIsUnterminated() {
        String src = funcTemplate("r##\"abc\"#");
        Diagnostic d = lexError(parseFails("neg.sol", src), DiagnosticCode.LEXER_UNTERMINATED_RAW_STRING);
        assertThat(d.expected().orElseThrow()).isEqualTo("\"##");
        int open = src.indexOf("r##\"");
        assertThat(d.span()).isEqualTo(SourceSpan.of(open, open + 4));
    }

    /** Opening with three hashes but a single-hash close expects `"###`. */
    @Test
    public void tooFewHashesInClosingThreeHashes() {
        String src = funcTemplate("r###\"abc\"#");
        Diagnostic d = lexError(parseFails("neg.sol", src), DiagnosticCode.LEXER_UNTERMINATED_RAW_STRING);
        assertThat(d.expected().orElseThrow()).isEqualTo("\"###");
        int open = src.indexOf("r###\"");
        assertThat(d.span()).isEqualTo(SourceSpan.of(open, open + 5));
    }

    /** Opening with four hashes but a single-hash close expects `"####`. */
    @Test
    public void tooFewHashesInClosingFourHashes() {
        String src = funcTemplate("r####\"abc\"#");
        Diagnostic d = lexError(parseFails("neg.sol", src), DiagnosticCode.LEXER_UNTERMINATED_RAW_STRING);
        assertThat(d.expected().orElseThrow()).isEqualTo("\"####");
        int open = src.indexOf("r####\"");
        assertThat(d.span()).isEqualTo(SourceSpan.of(open, open + 6));
    }

    /** A stray hash inside the body (closing with fewer hashes) is unterminated. */
    @Test
    public void strayHashInsideBodyIsUnterminated() {
        String src = funcTemplate("r##\"ab\"c\"#");
        Diagnostic d = lexError(parseFails("neg.sol", src), DiagnosticCode.LEXER_UNTERMINATED_RAW_STRING);
        assertThat(d.expected().orElseThrow()).isEqualTo("\"##");
    }

    /** A mismatched form yields no AST, like every other lexical failure. */
    @Test
    public void mismatchedDelimiterExposesNoAst() {
        parseFails("noast.sol", funcTemplate("r\"#abc\"#"));
    }
}
