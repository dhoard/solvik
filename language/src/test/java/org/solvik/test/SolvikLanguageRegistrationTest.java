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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import com.oracle.truffle.api.TruffleLanguage;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.Source;
import org.junit.Test;
import org.solvik.truffle.SolvikFileDetector;
import org.solvik.truffle.SolvikLanguage;

/**
 * Phase 5 cutover tests: the runtime registers the {@code solvik} language id, the
 * {@code application/x-solvik} MIME type, and {@code .sol} file detection, and it exposes no
 * SimpleLanguage alias.
 */
public final class SolvikLanguageRegistrationTest {

    @Test
    public void engineRegistersSolvikAndNoSimpleLanguageAlias() {
        try (Engine engine = Engine.create()) {
            Language solvik = engine.getLanguages().get("solvik");
            assertNotNull("solvik must be registered", solvik);
            assertEquals("Solvik", solvik.getName());
            assertTrue(solvik.getMimeTypes().contains("application/x-solvik"));
            assertEquals("application/x-solvik", solvik.getDefaultMimeType());
            assertNull("SimpleLanguage must not be exposed", engine.getLanguages().get("sl"));
        }
    }

    @Test
    public void registrationDeclaresSolvikIdentityAndFileDetector() {
        TruffleLanguage.Registration registration = SolvikLanguage.class.getAnnotation(TruffleLanguage.Registration.class);
        assertNotNull(registration);
        assertEquals("solvik", registration.id());
        assertEquals("Solvik", registration.name());
        assertEquals("application/x-solvik", registration.defaultMimeType());
        assertArrayEquals(new Class<?>[]{SolvikFileDetector.class}, registration.fileTypeDetectors());
    }

    @Test
    public void solExtensionIsDetected() throws IOException {
        Path file = Files.createTempFile("solvik", ".sol");
        try {
            Files.writeString(file, "  println(7)\n");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (Context context = Context.newBuilder().out(out).err(out).allowAllAccess(true).build()) {
                context.eval(Source.newBuilder("solvik", file.toFile()).build());
            }
            assertEquals("file detection must run the .sol program", "7\n", out.toString(StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void mimeTypeIsAccepted() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder().out(out).err(out).allowAllAccess(true).build()) {
            Source source = Source.newBuilder("solvik", "  println(9)\n", "mime.sol").mimeType("application/x-solvik").build();
            context.eval(source);
        }
        assertEquals("9\n", out.toString(StandardCharsets.UTF_8));
    }

    @Test
    public void simpleLanguageFunctionKeywordIsRejectedWithoutCompatMode() throws IOException {
        try (Context context = Context.newBuilder("solvik").allowAllAccess(true).build()) {
            try {
                context.eval(Source.newBuilder("solvik", "function main() {}\n", "legacy.sol").build());
                throw new AssertionError("SimpleLanguage syntax must be rejected");
            } catch (org.graalvm.polyglot.PolyglotException e) {
                assertTrue(e.isSyntaxError());
                assertFalse(e.getMessage(), e.getMessage().isBlank());
            }
        }
    }
}
