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
 * Negative tests for member-assignment targets and superclass member access
 * (docs/LANGUAGE_SPEC.md sections 3, 7, and 14). An assignment must name a mutable local or
 * {@code var} property; a method, an immutable property, an unknown member, and a member reached
 * through a possibly-null receiver are each rejected with a distinct, source-located diagnostic.
 * {@code super.member} resolves only an inherited property, never a method used as a value.
 */
public final class SolvikPropertyAssignmentNegativeTest {

    private static final String REGEX_MATCH = """
            func use() {
                val pattern = Regex(r"a")
                val found = pattern.find("a")
                if (found != null) {
            %s
                }
            }
            """;

    private static DiagnosticBag checkFails(String text) {
        CompilationUnitNode unit = parseOk("assign.sol", text);
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

    private static DiagnosticCode codeOf(String text) {
        return first(checkFails(text)).code();
    }

    @Test
    public void assigningToAnImmutableCollectionPropertyIsRejected() {
        assertThat(codeOf("func f(l: List<Int>) {\n    l.size = 5\n}\n")).isEqualTo(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE);
        assertThat(codeOf("func f(l: List<Int>) {\n    l.isEmpty = true\n}\n")).isEqualTo(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE);
    }

    @Test
    public void assigningToACollectionMethodIsRejected() {
        assertThat(codeOf("func f(l: List<Int>) {\n    l.add = 5\n}\n")).isEqualTo(DiagnosticCode.TYPE_INVALID_ASSIGNMENT_TARGET);
    }

    @Test
    public void assigningToAnUnknownCollectionMemberIsRejected() {
        assertThat(codeOf("func f(l: List<Int>) {\n    l.missing = 5\n}\n")).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_MEMBER);
    }

    @Test
    public void assigningToARegexMethodIsRejected() {
        assertThat(codeOf("func f() {\n    var r = Regex(r\"a\")\n    r.matches = 5\n}\n")).isEqualTo(DiagnosticCode.TYPE_INVALID_ASSIGNMENT_TARGET);
    }

    @Test
    public void assigningToAnUnknownRegexMemberIsRejected() {
        assertThat(codeOf("func f() {\n    var r = Regex(r\"a\")\n    r.value = \"x\"\n}\n")).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_MEMBER);
    }

    @Test
    public void assigningToAnImmutableRegexMatchPropertyIsRejected() {
        assertThat(codeOf(REGEX_MATCH.formatted("        found.value = \"x\""))).isEqualTo(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE);
    }

    @Test
    public void assigningToARegexMatchMethodIsRejected() {
        assertThat(codeOf(REGEX_MATCH.formatted("        found.group = 5"))).isEqualTo(DiagnosticCode.TYPE_INVALID_ASSIGNMENT_TARGET);
    }

    @Test
    public void assigningToAnUnknownRegexMatchMemberIsRejected() {
        assertThat(codeOf(REGEX_MATCH.formatted("        found.missing = 5"))).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_MEMBER);
    }

    @Test
    public void assigningToAClassMethodIsRejected() {
        assertThat(codeOf("""
                class C {
                    func g(): Int {
                        return 1
                    }

                    func h() {
                        this.g = 5
                    }
                }
                """)).isEqualTo(DiagnosticCode.TYPE_INVALID_ASSIGNMENT_TARGET);
    }

    @Test
    public void assigningToAnUnknownClassPropertyIsRejected() {
        assertThat(codeOf("""
                class C {
                    func h() {
                        this.missing = 5
                    }
                }
                """)).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_MEMBER);
    }

    @Test
    public void assigningThroughANullableReceiverIsRejected() {
        assertThat(codeOf("""
                class C {
                    var x: Int = 0

                    func h() {
                        var c: C? = null
                        c.x = 5
                    }
                }
                """)).isEqualTo(DiagnosticCode.TYPE_NULLABLE_DEREFERENCE);
    }

    @Test
    public void readingAnInheritedSuperPropertyResolves() {
        SemanticResult result = SolvikSemanticAnalyzer.analyze(parseOk("super.sol", """
                open class B {
                    var p: Int = 3
                }

                class C extends B {
                    func h(): Int {
                        return super.p
                    }
                }
                """));
        assertThat(result.isSuccess()).as("expected success but got " + result.diagnostics().all()).isTrue();
    }

    @Test
    public void readingASuperMethodAsAValueIsRejected() {
        assertThat(codeOf("""
                open class B {
                    func g(): Int {
                        return 1
                    }
                }

                class C extends B {
                    func h(): Int {
                        return super.g
                    }
                }
                """)).isEqualTo(DiagnosticCode.TYPE_FUNCTION_AS_VALUE);
    }

    @Test
    public void readingAnUnknownSuperMemberIsRejected() {
        assertThat(codeOf("""
                open class B {
                }

                class C extends B {
                    func h(): Int {
                        return super.missing
                    }
                }
                """)).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_MEMBER);
    }
}
