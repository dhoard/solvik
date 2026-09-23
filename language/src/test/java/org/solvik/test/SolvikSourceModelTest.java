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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.solvik.parser.ModuleNames;
import org.solvik.source.SourceFile;
import org.solvik.source.SourceSpan;
import org.solvik.source.StringEscapes;

/**
 * Unit coverage for the front-end support types the lexer and parser are built on: the source/span
 * offset model, the normal-string escape rules of {@code docs/LANGUAGE_SPEC.md} section 15, and the
 * module-name rule of section 20. These are the layers a span or escape regression surfaces in
 * first, so each contract is pinned directly rather than only through a compiled program.
 */
public final class SolvikSourceModelTest {

    // ---- SourceSpan -----------------------------------------------------------------------------

    @Test
    public void spanLengthEmptyAndContainment() {
        SourceSpan span = SourceSpan.of(2, 3, 7);
        assertThat(span.sourceId()).isEqualTo(2);
        assertThat(span.length()).isEqualTo(4);
        assertThat(span.isEmpty()).isFalse();
        assertThat(span.contains(3)).as("the start offset is inside the span").isTrue();
        assertThat(span.contains(6)).isTrue();
        assertThat(span.contains(7)).as("the end offset is exclusive").isFalse();
        assertThat(SourceSpan.of(0, 5, 5).isEmpty()).isTrue();
    }

    @Test
    public void containmentRequiresTheSameSource() {
        SourceSpan span = SourceSpan.of(2, 3, 7);
        assertThat(span.contains(SourceSpan.of(2, 4, 6))).isTrue();
        assertThat(span.contains(SourceSpan.of(2, 3, 7))).isTrue();
        assertThat(span.contains(SourceSpan.of(2, 2, 6))).isFalse();
        assertThat(span.contains(SourceSpan.of(2, 4, 8))).isFalse();
        assertThat(span.contains(SourceSpan.of(3, 4, 6))).as("a span from another source never nests").isFalse();
    }

    @Test
    public void overlapRequiresTheSameSourceAndASharedOffset() {
        SourceSpan span = SourceSpan.of(2, 3, 7);
        assertThat(span.overlaps(SourceSpan.of(2, 6, 9))).isTrue();
        assertThat(span.overlaps(SourceSpan.of(2, 6, 7))).isTrue();
        assertThat(span.overlaps(SourceSpan.of(2, 7, 9))).as("touching spans do not overlap").isFalse();
        assertThat(span.overlaps(SourceSpan.of(2, 1, 3))).isFalse();
        assertThat(span.overlaps(SourceSpan.of(3, 6, 9))).as("a span from another source never overlaps").isFalse();
    }

    @Test
    public void spansOrderBySourceThenStartThenEnd() {
        SourceSpan span = SourceSpan.of(2, 3, 7);
        assertThat(span.compareTo(SourceSpan.of(2, 3, 7))).isZero();
        assertThat(span.compareTo(SourceSpan.of(2, 2, 9))).isPositive();
        assertThat(span.compareTo(SourceSpan.of(2, 4, 4))).isNegative();
        assertThat(span.compareTo(SourceSpan.of(3, 0, 0))).isNegative();
        assertThat(span.compareTo(SourceSpan.of(1, 9, 9))).isPositive();
        // Equal source and start fall back to the end offset, so a shorter span sorts first.
        assertThat(span.compareTo(SourceSpan.of(2, 3, 9))).isNegative();
        assertThat(span.compareTo(SourceSpan.of(2, 3, 5))).isPositive();
    }

    @Test
    public void invalidSpansAreRejectedAtConstruction() {
        assertThatThrownBy(() -> SourceSpan.of(-1, 0, 0)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sourceId");
        assertThatThrownBy(() -> SourceSpan.of(0, -1, 0)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("startOffset");
        assertThatThrownBy(() -> SourceSpan.of(0, 5, 4)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("endOffset");
    }

    // ---- SourceFile physical-line model ---------------------------------------------------------

    @Test
    public void everyLineTerminatorStyleCountsAsAPhysicalLineBreak() {
        SourceFile lf = new SourceFile("lf.sol", "a\nb\nc");
        SourceFile crlf = new SourceFile("crlf.sol", "a\r\nb\r\nc");
        SourceFile cr = new SourceFile("cr.sol", "a\rb\rc");
        assertThat(lf.lineCount()).isEqualTo(3);
        assertThat(lf.lineColumnAt(2)).isEqualTo(new org.solvik.source.LineColumn(2, 1));
        assertThat(lf.lineColumnAt(4)).isEqualTo(new org.solvik.source.LineColumn(3, 1));
        assertThat(cr.lineCount()).isEqualTo(3);
        assertThat(cr.lineColumnAt(2)).isEqualTo(new org.solvik.source.LineColumn(2, 1));
        assertThat(cr.lineColumnAt(4)).isEqualTo(new org.solvik.source.LineColumn(3, 1));
        // CRLF is one break, not two, so the next line starts after the pair. The LF half of the
        // pair still belongs to the line it terminates, which is why the break starts at index 3.
        assertThat(crlf.lineCount()).isEqualTo(3);
        assertThat(crlf.lineColumnAt(2)).as("the LF half of a CRLF remains on the terminated line").isEqualTo(new org.solvik.source.LineColumn(1, 3));
        assertThat(crlf.lineColumnAt(3)).isEqualTo(new org.solvik.source.LineColumn(2, 1));
        assertThat(crlf.lineColumnAt(6)).isEqualTo(new org.solvik.source.LineColumn(3, 1));
    }

    @Test
    public void offsetConversionsRoundTripAndClamp() {
        SourceFile file = new SourceFile("f.sol", "a\nb");
        assertThat(file.offsetAt(1, 1)).isEqualTo(0);
        assertThat(file.offsetAt(2, 1)).isEqualTo(2);
        assertThat(file.lineColumnAt(0)).isEqualTo(new org.solvik.source.LineColumn(1, 1));
        assertThat(file.lineColumnAt(2)).isEqualTo(new org.solvik.source.LineColumn(2, 1));
        // A position past the end of a line is capped at the next line's first character, and a
        // position past the end of the file is capped at the file length.
        assertThat(file.offsetAt(1, 9)).isEqualTo(2);
        assertThat(file.offsetAt(2, 9)).isEqualTo(3);
        assertThat(file.lineColumnAt(99)).isEqualTo(new org.solvik.source.LineColumn(2, 2));
    }

    @Test
    public void invalidSourceCoordinatesAreRejected() {
        SourceFile file = new SourceFile("f.sol", "a\nb");
        assertThatThrownBy(() -> file.offsetAt(0, 1)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("line 0");
        assertThatThrownBy(() -> file.offsetAt(3, 1)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("line 3");
        assertThatThrownBy(() -> file.offsetAt(1, 0)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("column");
        assertThatThrownBy(() -> new SourceFile(-1, "bad.sol", "x")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("id");
    }

    @Test
    public void sliceAndLocationFormattingUseTheOwnedSource() {
        SourceFile file = new SourceFile("named.sol", "abc");
        assertThat(file.textLength()).isEqualTo(3);
        assertThat(file.slice(SourceSpan.of(0, 0, 2))).isEqualTo("ab");
        assertThat(file.formatLocation(SourceSpan.of(0, 2, 3))).isEqualTo("named.sol:1:3");
        assertThatThrownBy(() -> file.slice(SourceSpan.of(1, 0, 1))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("does not belong");
        assertThatThrownBy(() -> file.formatLocation(SourceSpan.of(1, 0, 1))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("does not belong");
        assertThatThrownBy(() -> file.slice(SourceSpan.of(0, 0, 9))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("exceeds source length");
    }

    // ---- Normal-string escapes ------------------------------------------------------------------

    @Test
    public void exactlyTheSixDocumentedEscapesAreAccepted() {
        // Section 15: `\\`, `\"`, `\n`, `\r`, `\t`, and `\0`. Anything else is a lexical error.
        for (String lexeme : new String[] {"\"a\\nb\"", "\"a\\rb\"", "\"a\\tb\"", "\"a\\0b\"", "\"a\\\\b\"", "\"a\\\"b\"", "\"\""}) {
            assertThat(StringEscapes.invalidEscape(lexeme)).as(lexeme).isEmpty();
        }
        assertThat(StringEscapes.invalidEscape("\"a\\qb\"")).contains("\\q");
        assertThat(StringEscapes.invalidEscape("\"a\\'b\"")).contains("\\'");
        assertThat(StringEscapes.invalidEscape("\"a\\ b\"")).contains("\\ ");
    }

    @Test
    public void onlyTheFirstUnsupportedEscapeIsReported() {
        assertThat(StringEscapes.invalidEscape("\"\\q\\q\"")).contains("\\q");
    }

    @Test
    public void aLoneTrailingBackslashIsReportedDefensively() {
        // No legal lexeme ends in a backslash before its closing quote (the backslash would escape
        // the quote), so this is a defensive reading rule for a helper that also serves include
        // paths; it must report rather than overrun the lexeme.
        assertThat(StringEscapes.invalidEscape("\"abc\\\"")).contains("\\");
        assertThat(StringEscapes.unescape("\"abc\\\"")).isEqualTo("abc\\");
    }

    @Test
    public void supportedEscapesDecodeToTheirCharacter() {
        assertThat(StringEscapes.unescape("\"a\\nb\"")).isEqualTo("a\nb");
        assertThat(StringEscapes.unescape("\"a\\rb\"")).isEqualTo("a\rb");
        assertThat(StringEscapes.unescape("\"a\\tb\"")).isEqualTo("a\tb");
        assertThat(StringEscapes.unescape("\"a\\0b\"")).isEqualTo("a\u0000b");
        assertThat(StringEscapes.unescape("\"a\\\\b\"")).isEqualTo("a\\b");
        assertThat(StringEscapes.unescape("\"a\\\"b\"")).isEqualTo("a\"b");
    }

    @Test
    public void unescapeLeavesPlainTextUnsupportedEscapesAndEmptyInputAlone() {
        assertThat(StringEscapes.unescape("\"plain text\"")).isEqualTo("plain text");
        assertThat(StringEscapes.unescape("\"\"")).isEmpty();
        // An unsupported escape is preserved verbatim rather than dropped; the diagnostic path is
        // responsible for the rejection.
        assertThat(StringEscapes.unescape("\"a\\qb\"")).isEqualTo("a\\qb");
        // Escapes are decoded one pair at a time, so a doubled prefix does not consume the next one.
        assertThat(StringEscapes.unescape("\"\\n\\t\"")).isEqualTo("\n\t");
    }

    // ---- File scope -----------------------------------------------------------------------------

    @Test
    public void theDefaultFileScopeHasNoModuleAndNoPrefixes() {
        org.solvik.parser.FileScope scope = org.solvik.parser.FileScope.DEFAULT;
        assertThat(scope.moduleName()).isEmpty();
        assertThat(scope.moduleNameOrNull()).isNull();
        assertThat(scope.isDefaultModule()).isTrue();
        assertThat(scope.prefixes()).isEmpty();
        assertThat(scope.moduleForPrefix("anything")).isEmpty();
    }

    @Test
    public void aNamedFileScopeExposesItsModuleAndVisiblePrefixesInDeclarationOrder() {
        // The accessor promises declaration order, which a plain unordered copy would not preserve,
        // and a lookup for an unknown prefix must be empty rather than null.
        java.util.Map<String, String> prefixes = new java.util.LinkedHashMap<>();
        prefixes.put("alpha", "first_module");
        prefixes.put("beta", "second_module");
        org.solvik.parser.FileScope scope = new org.solvik.parser.FileScope("owner", prefixes);
        assertThat(scope.moduleName()).contains("owner");
        assertThat(scope.moduleNameOrNull()).isEqualTo("owner");
        assertThat(scope.isDefaultModule()).isFalse();
        assertThat(scope.prefixes()).containsExactlyEntriesOf(prefixes);
        assertThat(scope.prefixes().keySet()).containsExactly("alpha", "beta");
        assertThat(scope.moduleForPrefix("alpha")).contains("first_module");
        assertThat(scope.moduleForPrefix("missing")).isEmpty();
        // The exposed map is a copy, so mutating the caller's map does not change the scope.
        prefixes.put("gamma", "third_module");
        assertThat(scope.prefixes()).doesNotContainKey("gamma");
    }

    @Test
    public void aFileScopeRejectsAMissingPrefixMap() {
        assertThatThrownBy(() -> new org.solvik.parser.FileScope("owner", null)).isInstanceOf(NullPointerException.class);
    }

    // ---- Module and alias names -----------------------------------------------------------------

    @Test
    public void moduleNamesAreLowercaseUnderscoreSeparated() {
        for (String valid : new String[] {"app", "app_one", "a1_b2", "core", "a_b_c", "x1"}) {
            assertThat(ModuleNames.isValid(valid)).as(valid).isTrue();
        }
        for (String invalid : new String[] {"App", "APP", "1app", "app_", "_app", "app__one", "", "a-b", "a.b", "app one", "café"}) {
            assertThat(ModuleNames.isValid(invalid)).as("[" + invalid + "]").isFalse();
        }
        assertThat(ModuleNames.isValid(null)).as("a missing name is not a valid one").isFalse();
    }
}
