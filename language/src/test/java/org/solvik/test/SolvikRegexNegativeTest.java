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
import org.solvik.diagnostic.Diagnostic;
import org.solvik.diagnostic.DiagnosticBag;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;

/**
 * Negative Phase 14 semantic tests (docs/LANGUAGE_SPEC.md section 14): invalid constant patterns,
 * construction and argument errors, unknown members, immutability, nullable dereference, and using
 * a member as a value.
 */
public final class SolvikRegexNegativeTest {

    private static DiagnosticBag checkFails(String text) {
        CompilationUnitNode unit = parseOk("regexneg.sol", text);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertThat(result.isSuccess()).as("analysis must fail: " + text).isFalse();
        assertThat(result.program().isEmpty()).as("failed analysis must expose no program").isTrue();
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

    @Test
    public void regexConstructionRequiresExactlyOneArgument() {
        assertThat(first(checkFails("""
                    val re = Regex()
                """)).code()).isEqualTo(DiagnosticCode.TYPE_ARITY_MISMATCH);
        assertThat(first(checkFails("""
                    val re = Regex("a", "b")
                """)).code()).isEqualTo(DiagnosticCode.TYPE_ARITY_MISMATCH);
    }

    @Test
    public void regexConstructionRequiresAString() {
        assertThat(first(checkFails("""
                    val re = Regex(1)
                """)).code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void regexConstructionRejectsANullableString() {
        assertThat(first(checkFails("""
                    val re = Regex(null)
                """)).code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void anInvalidConstantPatternIsACompileTimeDiagnostic() {
        assertThat(first(checkFails("""
                    val re = Regex("(")
                """)).code()).isEqualTo(DiagnosticCode.TYPE_INVALID_REGEX_PATTERN);
    }

    @Test
    public void aReversedRepetitionConstantIsRejected() {
        assertThat(first(checkFails("""
                    val re = Regex("a{2,1}")
                """)).code()).isEqualTo(DiagnosticCode.TYPE_INVALID_REGEX_PATTERN);
    }

    @Test
    public void lookaroundInAConstantPatternIsRejected() {
        assertThat(first(checkFails("""
                    val re = Regex(r"(?=a)")
                """)).code()).isEqualTo(DiagnosticCode.TYPE_INVALID_REGEX_PATTERN);
    }

    @Test
    public void aBackreferenceInAConstantPatternIsRejected() {
        assertThat(first(checkFails("""
                    val re = Regex(r"(a)\\1")
                """)).code()).isEqualTo(DiagnosticCode.TYPE_INVALID_REGEX_PATTERN);
    }

    @Test
    public void embeddedFlagsInAConstantPatternAreRejected() {
        assertThat(first(checkFails("""
                    val re = Regex(r"(?i)abc")
                """)).code()).isEqualTo(DiagnosticCode.TYPE_INVALID_REGEX_PATTERN);
    }

    @Test
    public void regexMatchCannotBeConstructed() {
        assertThat(first(checkFails("""
                    val m = RegexMatch()
                """)).code()).isEqualTo(DiagnosticCode.TYPE_INVALID_CONVERSION);
    }

    @Test
    public void regexCannotBeUsedAsABareValue() {
        assertThat(first(checkFails("""
                    val r = Regex
                """)).code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_NAME);
    }

    @Test
    public void anUnknownRegexMethodIsRejected() {
        assertThat(first(checkFails("""
                func test(re: Regex): Boolean {
                    return re.test("a")
                }
                """)).code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_MEMBER);
    }

    @Test
    public void aRegexMethodArgumentTypeIsChecked() {
        assertThat(first(checkFails("""
                func test(re: Regex): Boolean {
                    return re.matches(1)
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void aRegexReplaceNeedsTwoArguments() {
        assertThat(first(checkFails("""
                func scrub(re: Regex): String {
                    return re.replace("a")
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_ARITY_MISMATCH);
    }

    @Test
    public void aRegexMethodNameCannotBeUsedAsAValue() {
        assertThat(first(checkFails("""
                func test(re: Regex): Unit {
                    val f = re.matches
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_FUNCTION_AS_VALUE);
    }

    @Test
    public void assigningToARegexMethodIsRejected() {
        assertThat(first(checkFails("""
                func test(re: Regex): Unit {
                    re.matches = "a"
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_INVALID_ASSIGNMENT_TARGET);
    }

    @Test
    public void aNullableRegexCannotBeDereferencedDirectly() {
        assertThat(first(checkFails("""
                func test(re: Regex?): Boolean {
                    return re.matches("a")
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_NULLABLE_DEREFERENCE);
    }

    @Test
    public void anUnknownRegexMatchMemberIsRejected() {
        assertThat(first(checkFails("""
                func test(m: RegexMatch): String {
                    return m.kind
                }
                """)).code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_MEMBER);
    }

    @Test
    public void anUnknownRegexMatchMethodIsRejected() {
        assertThat(first(checkFails("""
                func test(m: RegexMatch): String? {
                    return m.capture(1)
                }
                """)).code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_MEMBER);
    }

    @Test
    public void regexMatchPropertiesAreImmutable() {
        assertThat(first(checkFails("""
                func test(m: RegexMatch): Unit {
                    m.value = "x"
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE);
        assertThat(first(checkFails("""
                func test(m: RegexMatch): Unit {
                    m.start = 0
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE);
    }

    @Test
    public void aRegexMatchMethodNameCannotBeUsedAsAValue() {
        assertThat(first(checkFails("""
                func test(m: RegexMatch): Unit {
                    val f = m.group
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_FUNCTION_AS_VALUE);
    }

    @Test
    public void assigningToARegexMatchMethodIsRejected() {
        assertThat(first(checkFails("""
                func test(m: RegexMatch): Unit {
                    m.group = 0
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_INVALID_ASSIGNMENT_TARGET);
    }

    @Test
    public void aRegexMatchGroupArgumentMustBeInt() {
        assertThat(first(checkFails("""
                func test(m: RegexMatch): String? {
                    return m.group("0")
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void aNullableRegexMatchCannotBeDereferencedDirectly() {
        assertThat(first(checkFails("""
                func test(m: RegexMatch?): String {
                    return m.value
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_NULLABLE_DEREFERENCE);
    }

    @Test
    public void regexTypesCannotBeExtendedOrImplemented() {
        assertThat(first(checkFails("""
                class Custom extends Regex {
                }
                """)).code()).isEqualTo(DiagnosticCode.SEM_INVALID_SUPERCLASS);
        assertThat(first(checkFails("""
                class Custom implements RegexMatch {
                }
                """)).code()).isEqualTo(DiagnosticCode.SEM_INVALID_INTERFACE);
    }
}
