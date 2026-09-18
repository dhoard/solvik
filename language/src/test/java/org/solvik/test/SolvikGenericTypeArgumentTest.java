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
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;

/**
 * Explicit call type arguments {@code Name<T>(...)} for generic functions, constructors, and
 * methods (docs/LANGUAGE_SPEC.md section 11). Written arguments are an identity substitution that
 * must match the callee's type-parameter count and be a declared type; a non-generic callee cannot
 * take them. Each invalid shape is a source-located compile-time error, and the valid shape executes
 * with the substituted types.
 */
public final class SolvikGenericTypeArgumentTest {

    private static final String PRELUDE = """
            class Box<T> {
                var value: T

                Box(value: T) {
                    this.value = value
                }

                func replaceWith<U>(value: U): U {
                    return value
                }
            }

            func identity<T>(x: T): T {
                return x
            }

            func plain(x: Int): Int {
                return x
            }
            """;

    private static DiagnosticBag checkFails(String text) {
        CompilationUnitNode unit = parseOk("typearg.sol", text);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertThat(result.isSuccess()).as("analysis must fail: " + text).isFalse();
        assertThat(result.program().isEmpty()).as("failed analysis must expose no program").isTrue();
        return result.diagnostics();
    }

    private static Diagnostic first(DiagnosticBag bag) {
        List<Diagnostic> all = bag.all();
        assertThat(all.isEmpty()).isFalse();
        return all.get(0);
    }

    @Test
    public void explicitTypeArgumentOnAFunctionExecutes() {
        assertThat(run(PRELUDE + "    println(identity<Int>(1))\n    println(identity<String>(\"x\"))\n"))
                .isEqualTo("1\nx\n");
    }

    @Test
    public void explicitTypeArgumentOnAConstructionExecutes() {
        assertThat(run(PRELUDE + "    val box = Box<Int>(5)\n    println(box.value)\n")).isEqualTo("5\n");
    }

    @Test
    public void explicitTypeArgumentOnAMethodExecutes() {
        assertThat(run(PRELUDE + "    val box = Box(5)\n    println(box.replaceWith<String>(\"x\"))\n")).isEqualTo("x\n");
    }

    @Test
    public void wrongArgumentTypeForAnExplicitTypeArgumentIsRejected() {
        assertThat(first(checkFails(PRELUDE + "func use() {\n    println(identity<Int>(\"x\"))\n}\n")).code())
                .isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void explicitTypeArgumentArityMismatchIsRejected() {
        Diagnostic diagnostic = first(checkFails(PRELUDE + "func use() {\n    println(identity<Int, String>(1))\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_TYPE_ARGUMENT_ARITY);
        assertThat(diagnostic.expected().orElseThrow()).isEqualTo("1");
        assertThat(diagnostic.found().orElseThrow()).isEqualTo("2");
    }

    @Test
    public void explicitTypeArgumentOnANonGenericCalleeIsRejected() {
        assertThat(first(checkFails(PRELUDE + "func use() {\n    println(plain<Int>(1))\n}\n")).code())
                .isEqualTo(DiagnosticCode.TYPE_NOT_GENERIC);
    }

    @Test
    public void unknownExplicitTypeArgumentIsRejected() {
        assertThat(first(checkFails(PRELUDE + "func use() {\n    println(identity<Unknown>(1))\n}\n")).code())
                .isEqualTo(DiagnosticCode.RESOL_UNKNOWN_TYPE);
    }

    @Test
    public void wrongValueArityIsStillReportedWithExplicitTypeArguments() {
        assertThat(first(checkFails(PRELUDE + "func use() {\n    println(identity<Int>(1, 2))\n}\n")).code())
                .isEqualTo(DiagnosticCode.TYPE_ARITY_MISMATCH);
    }

    @Test
    public void constructionExplicitTypeArgumentMismatchIsRejected() {
        assertThat(first(checkFails(PRELUDE + "func use() {\n    val box = Box<Int>(\"x\")\n}\n")).code())
                .isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    private static String run(String body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            context.eval(build(body));
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static Source build(String source) {
        try {
            return Source.newBuilder("solvik", source, "typearg.sol").build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
