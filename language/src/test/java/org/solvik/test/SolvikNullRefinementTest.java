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
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;

/**
 * Flow-sensitive narrowing forms for null checks and type tests (docs/LANGUAGE_SPEC.md sections 5
 * and 18). A parenthesized or negated condition, a reversed {@code null != x} comparison, and a
 * negated {@code is} test all establish the same refinement as the direct form; a null check on an
 * expression that is not a stable variable refines nothing and a later dereference stays a
 * compile-time error.
 */
public final class SolvikNullRefinementTest {

    private static final String PRELUDE = """
            class Box {
                val value: Int

                Box(value: Int) {
                    this.value = value
                }
            }

            func box(): Box? {
                return null
            }
            """;

    @Test
    public void narrowingFormsExecuteWithNonNullValues() {
        assertThat(run(PRELUDE + """
                    func parenthesized(b: Box?): Int {
                        if ((b != null)) {
                            return b.value
                        }
                        return 0
                    }

                    func negated(b: Box?): Int {
                        if (!(b == null)) {
                            return b.value
                        }
                        return 0
                    }

                    func reversed(b: Box?): Int {
                        if (null != b) {
                            return b.value
                        }
                        return 0
                    }

                    func reversedElse(b: Box?): Int {
                        if (null == b) {
                            return 0
                        } else {
                            return b.value
                        }
                    }

                    func negatedType(v: Any): Int {
                        if (!(v is Box)) {
                            return 0
                        } else {
                            return v.value
                        }
                    }

                    println(parenthesized(Box(1)))
                    println(negated(Box(2)))
                    println(reversed(Box(3)))
                    println(reversedElse(Box(4)))
                    println(negatedType(Box(5)))
                """)).isEqualTo("1\n2\n3\n4\n5\n");
    }

    @Test
    public void negatedNullCheckNarrowsAWhileBody() {
        check(PRELUDE + """
                func loop(b: Box?): Int {
                    var total: Int = 0
                    while (!(b == null)) {
                        total = total + b.value
                    }
                    return total
                }
                """);
    }

    @Test
    public void nullCheckOnANonNarrowableExpressionRefinesNothing() {
        CompilationUnitNode unit = parseOk("refine.sol", PRELUDE + """
                func f(): Int {
                    if (box() != null) {
                        return box().value
                    }
                    return 0
                }
                """);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertThat(result.isSuccess()).as("analysis must fail: a call result is not a stable variable").isFalse();
        assertThat(result.diagnostics().all().get(0).code()).isEqualTo(DiagnosticCode.TYPE_NULLABLE_DEREFERENCE);
    }

    private static void check(String text) {
        CompilationUnitNode unit = parseOk("refine.sol", text);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertThat(result.isSuccess()).as("analysis must succeed: " + result.diagnostics().all()).isTrue();
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
            return Source.newBuilder("solvik", source, "refine.sol").build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
