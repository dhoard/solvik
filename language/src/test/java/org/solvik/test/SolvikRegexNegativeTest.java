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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.solvik.test.SolvikTestSupport.parseOk;

import java.util.List;
import org.junit.Test;
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
        assertFalse("analysis must fail: " + text, result.isSuccess());
        assertTrue("failed analysis must expose no program", result.program().isEmpty());
        assertTrue("failed analysis must carry diagnostics", result.diagnostics().hasErrors());
        for (Diagnostic diagnostic : result.diagnostics().all()) {
            assertTrue("span within source bounds: " + diagnostic.span(), diagnostic.span().endOffset() <= text.length());
        }
        return result.diagnostics();
    }

    private static Diagnostic first(DiagnosticBag bag) {
        List<Diagnostic> all = bag.all();
        assertFalse(all.isEmpty());
        return all.get(0);
    }

    @Test
    public void regexConstructionRequiresExactlyOneArgument() {
        assertEquals(DiagnosticCode.TYPE_ARITY_MISMATCH, first(checkFails("""
                    val re = Regex()
                """)).code());
        assertEquals(DiagnosticCode.TYPE_ARITY_MISMATCH, first(checkFails("""
                    val re = Regex("a", "b")
                """)).code());
    }

    @Test
    public void regexConstructionRequiresAString() {
        assertEquals(DiagnosticCode.TYPE_MISMATCH, first(checkFails("""
                    val re = Regex(1)
                """)).code());
    }

    @Test
    public void regexConstructionRejectsANullableString() {
        assertEquals(DiagnosticCode.TYPE_MISMATCH, first(checkFails("""
                    val re = Regex(null)
                """)).code());
    }

    @Test
    public void anInvalidConstantPatternIsACompileTimeDiagnostic() {
        assertEquals(DiagnosticCode.TYPE_INVALID_REGEX_PATTERN, first(checkFails("""
                    val re = Regex("(")
                """)).code());
    }

    @Test
    public void aReversedRepetitionConstantIsRejected() {
        assertEquals(DiagnosticCode.TYPE_INVALID_REGEX_PATTERN, first(checkFails("""
                    val re = Regex("a{2,1}")
                """)).code());
    }

    @Test
    public void lookaroundInAConstantPatternIsRejected() {
        assertEquals(DiagnosticCode.TYPE_INVALID_REGEX_PATTERN, first(checkFails("""
                    val re = Regex(r"(?=a)")
                """)).code());
    }

    @Test
    public void aBackreferenceInAConstantPatternIsRejected() {
        assertEquals(DiagnosticCode.TYPE_INVALID_REGEX_PATTERN, first(checkFails("""
                    val re = Regex(r"(a)\\1")
                """)).code());
    }

    @Test
    public void embeddedFlagsInAConstantPatternAreRejected() {
        assertEquals(DiagnosticCode.TYPE_INVALID_REGEX_PATTERN, first(checkFails("""
                    val re = Regex(r"(?i)abc")
                """)).code());
    }

    @Test
    public void regexMatchCannotBeConstructed() {
        assertEquals(DiagnosticCode.TYPE_INVALID_CONVERSION, first(checkFails("""
                    val m = RegexMatch()
                """)).code());
    }

    @Test
    public void regexCannotBeUsedAsABareValue() {
        assertEquals(DiagnosticCode.RESOL_UNKNOWN_NAME, first(checkFails("""
                    val r = Regex
                """)).code());
    }

    @Test
    public void anUnknownRegexMethodIsRejected() {
        assertEquals(DiagnosticCode.RESOL_UNKNOWN_MEMBER, first(checkFails("""
                func test(re: Regex): Boolean {
                    return re.test("a")
                }
                """)).code());
    }

    @Test
    public void aRegexMethodArgumentTypeIsChecked() {
        assertEquals(DiagnosticCode.TYPE_MISMATCH, first(checkFails("""
                func test(re: Regex): Boolean {
                    return re.matches(1)
                }
                """)).code());
    }

    @Test
    public void aRegexReplaceNeedsTwoArguments() {
        assertEquals(DiagnosticCode.TYPE_ARITY_MISMATCH, first(checkFails("""
                func scrub(re: Regex): String {
                    return re.replace("a")
                }
                """)).code());
    }

    @Test
    public void aRegexMethodNameCannotBeUsedAsAValue() {
        assertEquals(DiagnosticCode.TYPE_FUNCTION_AS_VALUE, first(checkFails("""
                func test(re: Regex): Unit {
                    val f = re.matches
                }
                """)).code());
    }

    @Test
    public void assigningToARegexMethodIsRejected() {
        assertEquals(DiagnosticCode.TYPE_INVALID_ASSIGNMENT_TARGET, first(checkFails("""
                func test(re: Regex): Unit {
                    re.matches = "a"
                }
                """)).code());
    }

    @Test
    public void aNullableRegexCannotBeDereferencedDirectly() {
        assertEquals(DiagnosticCode.TYPE_NULLABLE_DEREFERENCE, first(checkFails("""
                func test(re: Regex?): Boolean {
                    return re.matches("a")
                }
                """)).code());
    }

    @Test
    public void anUnknownRegexMatchMemberIsRejected() {
        assertEquals(DiagnosticCode.RESOL_UNKNOWN_MEMBER, first(checkFails("""
                func test(m: RegexMatch): String {
                    return m.kind
                }
                """)).code());
    }

    @Test
    public void anUnknownRegexMatchMethodIsRejected() {
        assertEquals(DiagnosticCode.RESOL_UNKNOWN_MEMBER, first(checkFails("""
                func test(m: RegexMatch): String? {
                    return m.capture(1)
                }
                """)).code());
    }

    @Test
    public void regexMatchPropertiesAreImmutable() {
        assertEquals(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE, first(checkFails("""
                func test(m: RegexMatch): Unit {
                    m.value = "x"
                }
                """)).code());
        assertEquals(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE, first(checkFails("""
                func test(m: RegexMatch): Unit {
                    m.start = 0
                }
                """)).code());
    }

    @Test
    public void aRegexMatchMethodNameCannotBeUsedAsAValue() {
        assertEquals(DiagnosticCode.TYPE_FUNCTION_AS_VALUE, first(checkFails("""
                func test(m: RegexMatch): Unit {
                    val f = m.group
                }
                """)).code());
    }

    @Test
    public void assigningToARegexMatchMethodIsRejected() {
        assertEquals(DiagnosticCode.TYPE_INVALID_ASSIGNMENT_TARGET, first(checkFails("""
                func test(m: RegexMatch): Unit {
                    m.group = 0
                }
                """)).code());
    }

    @Test
    public void aRegexMatchGroupArgumentMustBeInt() {
        assertEquals(DiagnosticCode.TYPE_MISMATCH, first(checkFails("""
                func test(m: RegexMatch): String? {
                    return m.group("0")
                }
                """)).code());
    }

    @Test
    public void aNullableRegexMatchCannotBeDereferencedDirectly() {
        assertEquals(DiagnosticCode.TYPE_NULLABLE_DEREFERENCE, first(checkFails("""
                func test(m: RegexMatch?): String {
                    return m.value
                }
                """)).code());
    }

    @Test
    public void regexTypesCannotBeExtendedOrImplemented() {
        assertEquals(DiagnosticCode.SEM_INVALID_SUPERCLASS, first(checkFails("""
                class Custom extends Regex {
                }
                """)).code());
        assertEquals(DiagnosticCode.SEM_INVALID_INTERFACE, first(checkFails("""
                class Custom implements RegexMatch {
                }
                """)).code());
    }
}
