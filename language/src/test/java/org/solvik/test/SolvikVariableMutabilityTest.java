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
import org.solvik.diagnostic.Diagnostic;
import org.solvik.diagnostic.DiagnosticBag;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.parser.SolvikParseResult;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;
import org.solvik.source.SourceFile;

/**
 * End-to-end tests for the normative variable semantics of {@code docs/LANGUAGE_SPEC.md} section 2:
 * {@code var} declares an immutable binding, {@code var mutable} declares a writable one, and normal
 * {@code =} assignment is the only reassignment mechanism. Both the semantic phase and the lowered
 * Truffle execution are exercised, so a divergence between "static check rejects it" and "the
 * program runs" cannot hide.
 */
public final class SolvikVariableMutabilityTest {

    private static String run(String source) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out)
                .option("engine.WarnInterpreterOnly", "false").allowAllAccess(true).build()) {
            context.eval(build(source, "mutability.sol"));
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

    private static DiagnosticBag checkFails(String text) {
        CompilationUnitNode unit = parseOk("mutability-neg.sol", text);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertThat(result.isSuccess()).as("analysis must fail: " + text).isFalse();
        assertThat(result.diagnostics().hasErrors()).isTrue();
        return result.diagnostics();
    }

    private static Diagnostic first(DiagnosticBag bag) {
        List<Diagnostic> all = bag.all();
        assertThat(all).isNotEmpty();
        return all.get(0);
    }

    // --- acceptance: immutable by default --------------------------------------------------------

    @Test
    public void immutableDeclarationBindsAndReads() {
        assertThat(run("var x: Integer = 10\nprint(x)")).isEqualTo("10");
    }

    @Test
    public void typedImmutableDeclarationBindsAndReads() {
        assertThat(run("var x: Integer = 10\nprint(x)")).isEqualTo("10");
    }

    // --- acceptance: mutable bindings ------------------------------------------------------------

    @Test
    public void mutableDeclarationReassigns() {
        assertThat(run("var mutable x: Integer = 10\nx = 20\nprint(x)")).isEqualTo("20");
    }

    @Test
    public void typedMutableDeclarationReassigns() {
        assertThat(run("var mutable x: Integer = 10\nx = 20\nprint(x)")).isEqualTo("20");
    }

    @Test
    public void mutableBindingMayBeAssignedRepeatedly() {
        assertThat(run("var mutable count: Integer = 0\ncount = 1\ncount = 2\ncount = count + 1\nprint(count)"))
                .isEqualTo("3");
    }

    // --- acceptance: shadowing applies to bindings, not spellings --------------------------------

    @Test
    public void anInnerMutableBindingShadowsAnOuterImmutableOne() {
        String src = "var x: Integer = 10\n{\n    var mutable x: Integer = 20\n    x = 30\n    print(x)\n}\nprint(x)";
        assertThat(run(src)).isEqualTo("3010");
    }

    @Test
    public void anInnerImmutableBindingShadowsAnOuterMutableOne() {
        String src = "var mutable x: Integer = 10\n{\n    var x: Integer = 20\n    print(x)\n}\nx = 11\nprint(x)";
        assertThat(run(src)).isEqualTo("2011");
    }

    // --- acceptance: binding immutability is not object immutability -----------------------------

    @Test
    public void anImmutableReferenceStillReachesMutableObjectState() {
        String src = """
                class User {
                    var mutable name: String

                    User(name: String) {
                        this.name = name
                    }
                }

                var user: User = User("Doug")
                user.name = "Douglas"
                print(user.name)

                """;
        assertThat(run(src)).isEqualTo("Douglas");
    }

    // --- rejection: immutable reassignment -------------------------------------------------------

    @Test
    public void immutableReassignmentIsRejected() {
        Diagnostic diagnostic = first(checkFails("var x: Integer = 10\nx = 20\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE);
        assertThat(diagnostic.span().startOffset()).isEqualTo("var x: Integer = 10\n".length());
    }

    @Test
    public void typedImmutableReassignmentIsRejected() {
        Diagnostic diagnostic = first(checkFails("var x: Integer = 10\nx = 20\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE);
    }

    @Test
    public void mutationOfAnOuterImmutableBindingFromANestedScopeIsRejected() {
        String src = "var x: Integer = 10\n{\n    x = 20\n}\n";
        Diagnostic diagnostic = first(checkFails(src));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE);
        assertThat(diagnostic.span().startOffset()).isEqualTo(src.indexOf("x = 20"));
    }

    @Test
    public void reassigningAParameterIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(p: Integer) {\n    p = 1\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE);
    }

    @Test
    public void reassigningAnImmutablePropertyIsRejected() {
        String src = """
                class C {
                    var name: String

                    C(name: String) {
                        this.name = name
                    }
                }

                func f(c: C) {
                    c.name = "b"
                }

                """;
        Diagnostic diagnostic = first(checkFails(src));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE);
    }

    // --- parser: the canonical form is keyword first, modifier second ----------------------------

    @Test
    public void aModifierBeforeTheDeclarationKeywordIsRejected() {
        SolvikParseResult result = org.solvik.parser.SolvikParser.parse(
                new SourceFile("modifier-first.sol", "mutable var x = 1\n"));
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    public void theRemovedValKeywordIsRejectedWithTheReplacementNamed() {
        SolvikParseResult result = org.solvik.parser.SolvikParser.parse(
                new SourceFile("removed.sol", "val x = 1\n"));
        assertThat(result.isSuccess()).isFalse();
        Diagnostic diagnostic = result.diagnostics().all().stream()
                .filter(d -> d.code() == DiagnosticCode.PARSER_UNSUPPORTED_REMOVED_SYNTAX)
                .findFirst()
                .orElseThrow();
        assertThat(diagnostic.message()).contains("'val' keyword was removed").contains("'var'");
        assertThat(diagnostic.span().startOffset()).isZero();
        assertThat(diagnostic.span().endOffset()).isEqualTo(3);
    }
}
