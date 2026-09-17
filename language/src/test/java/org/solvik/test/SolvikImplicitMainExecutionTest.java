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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.Test;

/**
 * End-to-end tests for executable top-level statements: a file's bare statements form an implicit
 * {@code func main(): Unit} (docs/LANGUAGE_SPEC.md section 6) and run end to end, in source order,
 * alongside declarations from the same file.
 */
public final class SolvikImplicitMainExecutionTest {

    private static String run(String text) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            context.eval(build(text));
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static Source build(String text) {
        try {
            return Source.newBuilder("solvik", text, "implicit.sol").build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    public void bareStatementsExecute() {
        assertEquals("hi\n", run("println(\"hi\")\n"));
    }

    @Test
    public void topLevelLocalsAndLoopsRun() {
        assertEquals("6\n", run("""
                var total = 0
                for (var i = 1; i <= 3; i = i + 1) {
                    total = total + i
                }
                println(total)
                """));
    }

    @Test
    public void implicitMainCallsFunctionsDeclaredLaterInTheFile() {
        assertEquals("3\n", run("""
                println(add(1, 2))
                func add(a: Int, b: Int): Int {
                    return a + b
                }
                """));
    }

    @Test
    public void declarationsAndStatementsRunInSourceOrder() {
        assertEquals("a\nb\n", run("""
                println("a")
                func f(): Unit {
                    println("b")
                }
                f()
                """));
    }

    @Test
    public void bareExitTerminatesWithTheGivenStatus() {
        try (Context context = Context.newBuilder("solvik").allowAllAccess(true).build()) {
            context.eval(build("println(\"before\")\nexit(4)\nprintln(\"after\")\n"));
            fail("exit must terminate the program");
        } catch (PolyglotException ex) {
            assertTrue("exception must be an exit", ex.isExit());
            assertEquals(4, ex.getExitStatus());
        }
    }
}
