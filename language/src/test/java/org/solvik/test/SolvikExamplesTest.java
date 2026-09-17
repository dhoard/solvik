/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.Test;

/**
 * Release syntax examples (docs/IMPLEMENTATION_PLAN.md Phase 16): every checked-in
 * {@code language/tests/*.sol} example runs end to end through the {@code solvik} language and
 * produces exactly its {@code .output} golden file. The examples are the published syntax surface,
 * not migration fixtures.
 */
public final class SolvikExamplesTest {

    private static Path testDirectory() {
        Path direct = Path.of("tests");
        if (Files.isDirectory(direct)) {
            return direct;
        }
        return Path.of("language", "tests");
    }

    @Test
    public void everyExampleRunsAndMatchesItsGoldenOutput() throws IOException {
        Path directory = testDirectory();
        assertTrue("example directory must exist: " + directory, Files.isDirectory(directory));
        List<Path> examples;
        try (Stream<Path> files = Files.list(directory)) {
            examples = files.filter(path -> path.getFileName().toString().endsWith(".sol")).sorted().toList();
        }
        assertTrue("at least four Solvik examples are required", examples.size() >= 4);
        for (Path example : examples) {
            String fileName = example.getFileName().toString();
            Path golden = example.resolveSibling(fileName.substring(0, fileName.length() - ".sol".length()) + ".output");
            assertTrue(fileName + " must have a golden " + golden.getFileName(), Files.isRegularFile(golden));
            String expected = Files.readString(golden, StandardCharsets.UTF_8);
            String actual = run(readSource(example), fileName);
            assertEquals("output of " + fileName, expected, actual);
        }
    }

    private static Source readSource(Path example) {
        try {
            String text = Files.readString(example, StandardCharsets.UTF_8);
            return Source.newBuilder("solvik", text, example.getFileName().toString()).build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String run(Source source, String name) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            context.eval(source);
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}
