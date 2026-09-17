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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.LibraryFactory;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.Test;
import org.solvik.source.SourceFile;
import org.solvik.truffle.SolvikParseException;
import org.solvik.truffle.SolvikUnit;
import org.solvik.truffle.object.SolvikClass;
import org.solvik.truffle.object.SolvikObject;

/**
 * GraalVM interop validation (docs/IMPLEMENTATION_PLAN.md Phase 16): the Solvik runtime values and
 * compile errors expose the Truffle interop messages the polyglot and tooling layers rely on. The
 * direct {@link InteropLibrary} assertions exercise the same exported messages a host language
 * sees, and the polyglot assertions exercise the end-to-end {@code Value}/exception view.
 */
public final class SolvikInteropTest {

    private static final InteropLibrary INTEROP = LibraryFactory.resolve(InteropLibrary.class).getUncached();

    @Test
    public void unitIsANullLikeValueAndDisplaysAsUnit() throws Exception {
        assertTrue(INTEROP.isNull(SolvikUnit.INSTANCE));
        assertEquals("Unit", INTEROP.toDisplayString(SolvikUnit.INSTANCE));
    }

    @Test
    public void solvikObjectExposesItsClassNameToInterop() throws Exception {
        SolvikClass userClass = new SolvikClass("User", List.of("name"), List.of(false));
        SolvikObject user = new SolvikObject(userClass);
        assertFalse(INTEROP.isNull(user));
        assertEquals("User", INTEROP.toDisplayString(user));
    }

    @Test
    public void parseExceptionExposesParseErrorTypeAndSourceLocation() throws Exception {
        String text = "    val x: Int = \"nope\"\n";
        com.oracle.truffle.api.source.Source source = com.oracle.truffle.api.source.Source.newBuilder("solvik", text, "bad.sol").build();
        SourceFile file = new SourceFile("bad.sol", text);
        SolvikParseException failure = SolvikParseException.create(source, file, org.solvik.parser.SolvikParser.parse(file).diagnostics());
        assertEquals(ExceptionType.PARSE_ERROR, INTEROP.getExceptionType(failure));
        assertTrue(INTEROP.hasSourceLocation(failure));
        assertNotNull(INTEROP.getSourceLocation(failure));
    }

    @Test
    public void evaluatedEntryPointIsAUnitValueToPolyglot() {
        try (Context context = Context.newBuilder("solvik").allowAllAccess(true).build()) {
            Value result = context.eval(source("    println(1)\n", "unit.sol"));
            assertTrue("an evaluated Solvik source yields Unit", result.isNull());
        }
    }

    @Test
    public void polyglotExposesSolvikCompileErrorsAsLocatedSyntaxErrors() {
        try (Context context = Context.newBuilder("solvik").allowAllAccess(true).build()) {
            try {
                context.eval(source("    val x: Int = \"nope\"\n", "bad.sol"));
                throw new AssertionError("ill-typed source must be rejected");
            } catch (PolyglotException e) {
                assertTrue(e.isSyntaxError());
                assertNotNull(e.getSourceLocation());
                assertTrue(e.getMessage(), e.getMessage().contains("SOLV-TYPE-001"));
            }
        }
    }

    private static Source source(String text, String name) {
        try {
            return Source.newBuilder("solvik", text, name).build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
