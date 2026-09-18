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

import com.oracle.truffle.api.TruffleLanguage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;
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
            assertThat(solvik).as("solvik must be registered").isNotNull();
            assertThat(solvik.getName()).isEqualTo("Solvik");
            assertThat(solvik.getMimeTypes().contains("application/x-solvik")).isTrue();
            assertThat(solvik.getDefaultMimeType()).isEqualTo("application/x-solvik");
            assertThat(engine.getLanguages().get("sl")).as("SimpleLanguage must not be exposed").isNull();
        }
    }

    @Test
    public void registrationDeclaresSolvikIdentityAndFileDetector() {
        TruffleLanguage.Registration registration = SolvikLanguage.class.getAnnotation(TruffleLanguage.Registration.class);
        assertThat(registration).isNotNull();
        assertThat(registration.id()).isEqualTo("solvik");
        assertThat(registration.name()).isEqualTo("Solvik");
        assertThat(registration.defaultMimeType()).isEqualTo("application/x-solvik");
        assertThat(registration.fileTypeDetectors()).containsExactly(SolvikFileDetector.class);
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
            assertThat(out.toString(StandardCharsets.UTF_8)).as("file detection must run the .sol program").isEqualTo("7\n");
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
        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("9\n");
    }

    @Test
    public void simpleLanguageFunctionKeywordIsRejectedWithoutCompatMode() throws IOException {
        try (Context context = Context.newBuilder("solvik").allowAllAccess(true).build()) {
            try {
                context.eval(Source.newBuilder("solvik", "function main() {}\n", "legacy.sol").build());
                throw new AssertionError("SimpleLanguage syntax must be rejected");
            } catch (org.graalvm.polyglot.PolyglotException e) {
                assertThat(e.isSyntaxError()).isTrue();
                assertThat(e.getMessage().isBlank()).as(e.getMessage()).isFalse();
            }
        }
    }
}
