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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Child-adoption coverage for lowered Truffle nodes. A field whose declared type is a node type must
 * carry {@code @Child} or {@code @Children}, or Truffle cannot see those children for instrumentation,
 * debugging, AST traversal, or node-tree reachability. The omission is invisible to behavior tests
 * because the field still executes normally, so it is guarded structurally here.
 * {@code SolvikCollectionConstructNode} is the motivating case: its initial-element arguments were
 * held in an unannotated array field and were unreachable from the node tree until the annotation
 * was added.
 */
public final class SolvikCollectionAdoptionTest {

    /**
     * Every non-static field in the lowered node package whose declared type is a Solvik node type
     * (single or array) carries {@code @Child} or {@code @Children}.
     */
    @Test
    public void everyNodeTypedFieldInTruffleNodesCarriesAChildAnnotation() throws IOException {
        Path sourceRoot = null;
        for (Path candidate : List.of(Path.of("src", "main", "java", "org", "solvik", "truffle", "nodes"),
                        Path.of("language", "src", "main", "java", "org", "solvik", "truffle", "nodes"))) {
            if (Files.isDirectory(candidate)) {
                sourceRoot = candidate;
                break;
            }
        }
        assertThat(sourceRoot).as("the lowered node package must be locatable from the test working directory").isNotNull();
        List<String> violations = new ArrayList<>();
        try (var paths = Files.list(sourceRoot)) {
            for (Path file : paths.filter(f -> f.getFileName().toString().endsWith(".java")).sorted().toList()) {
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).trim();
                    if (!line.startsWith("private") || line.contains("static") || !declaresSolvikNodeField(line)) {
                        continue;
                    }
                    String previous = i > 0 ? lines.get(i - 1).trim() : "";
                    if (!line.contains("@Child") && !line.contains("@Children") && !previous.contains("@Child") && !previous.contains("@Children")) {
                        violations.add(file.getFileName() + ":" + (i + 1) + ": " + line);
                    }
                }
            }
        }
        assertThat(violations).as("node-typed fields must be adopted with @Child or @Children").isEqualTo(List.of());
    }

    /** Whether a field declaration line names a Solvik node type as its declared type. */
    private static boolean declaresSolvikNodeField(String line) {
        for (String token : line.split("[\\s<>=(),]+")) {
            if (token.startsWith("Solvik") && token.contains("Node")) {
                return true;
            }
        }
        return false;
    }
}
