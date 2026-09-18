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

/**
 * End-to-end tests for {@code Any.toString()}, user overrides, and null display
 * (docs/LANGUAGE_SPEC.md sections 4 and 6). Scalar conversions are Java-style; a class override is
 * reached from {@code println}, an explicit {@code toString()} call, and the {@code ..} operator.
 */
public final class SolvikToStringExecutionTest {

    private static String run(String source) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            context.eval(build(source, "tostring.sol"));
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
    public void builtInScalarsRenderJavaStyleStrings() {
        assertThat(run("""
                func noop() {
                }
                println(42.toString())
                println(42L.toString())
                println(3.5.toString())
                println(2.5f.toString())
                println(true.toString())
                println('x'.toString())
                println("s".toString())
                println(noop().toString())
                """)).isEqualTo("42\n42\n3.5\n2.5\ntrue\nx\ns\nUnit\n");
    }

    @Test
    public void builtInScalarRenderCoversByteShortNegativeAndLargeLong() {
        assertThat(run("""
                func noop() {
                }
                println(Byte(5).toString())
                println(Short(-3).toString())
                println(Byte(127).toString())
                println(Short(-128).toString())
                println((-1).toString())
                println((-1000000).toString())
                println(1000000000000L.toString())
                """)).isEqualTo("5\n-3\n127\n-128\n-1\n-1000000\n1000000000000\n");
    }

    @Test
    public void userOverrideIsUsedByPrintlnAndConcat() {
        assertThat(run("""
                class Money {
                    val cents: Int

                    Money(cents: Int) {
                        this.cents = cents
                    }

                    override func toString(): String {
                        return "$" .. this.cents
                    }
                }

                val price = Money(1250)
                println(price)
                println(price.toString())
                println("price=" .. price)
                """)).isEqualTo("$1250\n$1250\nprice=$1250\n");
    }

    @Test
    public void overrideDispatchesThroughAnyAndInheritance() {
        assertThat(run("""
                open class Base {
                    open override func toString(): String {
                        return "base"
                    }
                }

                class Child extends Base {
                    override func toString(): String {
                        return "child"
                    }
                }

                val b: Any = Base()
                val c: Any = Child()
                println(b)
                println(c)
                println(Child())
                """)).isEqualTo("base\nchild\nchild\n");
    }

    @Test
    public void objectWithoutOverrideDisplaysItsClassName() {
        assertThat(run("""
                class Plain {
                }
                println(Plain())
                """)).isEqualTo("Plain\n");
    }

    @Test
    public void nullRendersAsNull() {
        assertThat(run("""
                val missing: String? = null
                println(missing)
                println(null)
                """)).isEqualTo("null\nnull\n");
    }
}
