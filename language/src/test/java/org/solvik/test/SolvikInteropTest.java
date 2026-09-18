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

import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.LibraryFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;
import org.solvik.source.SourceFile;
import org.solvik.truffle.SolvikParseException;
import org.solvik.truffle.SolvikUnit;
import org.solvik.truffle.object.SolvikClass;
import org.solvik.truffle.object.SolvikAny;

/**
 * GraalVM interop validation: the Solvik runtime values and
 * compile errors expose the Truffle interop messages the polyglot and tooling layers rely on. The
 * direct {@link InteropLibrary} assertions exercise the same exported messages a host language
 * sees, and the polyglot assertions exercise the end-to-end {@code Value}/exception view.
 */
public final class SolvikInteropTest {

    private static final InteropLibrary INTEROP = LibraryFactory.resolve(InteropLibrary.class).getUncached();

    @Test
    public void unitIsANullLikeValueAndDisplaysAsUnit() throws Exception {
        assertThat(INTEROP.isNull(SolvikUnit.INSTANCE)).isTrue();
        assertThat(INTEROP.toDisplayString(SolvikUnit.INSTANCE)).isEqualTo("Unit");
    }

    @Test
    public void solvikAnyExposesItsClassNameToInterop() throws Exception {
        SolvikClass userClass = new SolvikClass("User", List.of("name"), List.of(false));
        SolvikAny user = new SolvikAny(userClass);
        assertThat(INTEROP.isNull(user)).isFalse();
        assertThat(INTEROP.toDisplayString(user)).isEqualTo("User");
    }

    @Test
    public void parseExceptionExposesParseErrorTypeAndSourceLocation() throws Exception {
        String text = "    val x: Int = \"nope\"\n";
        com.oracle.truffle.api.source.Source source = com.oracle.truffle.api.source.Source.newBuilder("solvik", text, "bad.sol").build();
        SourceFile file = new SourceFile("bad.sol", text);
        SolvikParseException failure = SolvikParseException.create(source, file, org.solvik.parser.SolvikParser.parse(file).diagnostics());
        assertThat(INTEROP.getExceptionType(failure)).isEqualTo(ExceptionType.PARSE_ERROR);
        assertThat(INTEROP.hasSourceLocation(failure)).isTrue();
        assertThat(INTEROP.getSourceLocation(failure)).isNotNull();
    }

    @Test
    public void evaluatedEntryPointIsAUnitValueToPolyglot() {
        try (Context context = Context.newBuilder("solvik").allowAllAccess(true).build()) {
            Value result = context.eval(source("    println(1)\n", "unit.sol"));
            assertThat(result.isNull()).as("an evaluated Solvik source yields Unit").isTrue();
        }
    }

    @Test
    public void polyglotExposesSolvikCompileErrorsAsLocatedSyntaxErrors() {
        try (Context context = Context.newBuilder("solvik").allowAllAccess(true).build()) {
            try {
                context.eval(source("    val x: Int = \"nope\"\n", "bad.sol"));
                throw new AssertionError("ill-typed source must be rejected");
            } catch (PolyglotException e) {
                assertThat(e.isSyntaxError()).isTrue();
                assertThat(e.getSourceLocation()).isNotNull();
                assertThat(e.getMessage().contains("SOLV-TYPE-001")).as(e.getMessage()).isTrue();
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
