package dev.solvik.compiler.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Two hazards the cache must survive: files that share a name, and the same command started twice at
 * once from separate terminals (modeled as separate processes, since that is what a terminal is).
 */
@DisplayName("Cache collisions")
class CacheCollisionTest {

    @TempDir
    Path directory;

    private Path write(String relative, String content) throws IOException {
        Path file = directory.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private Path cacheRoot() {
        return directory.resolve("cache");
    }

    private int run(Path source) {
        var out = new java.io.ByteArrayOutputStream();
        var err = new java.io.ByteArrayOutputStream();
        System.setProperty("solvik.cache.dir", cacheRoot().toString());
        try {
            return Main.run(new String[]{"run", source.toString()},
                    new java.io.PrintStream(out, true, StandardCharsets.UTF_8),
                    new java.io.PrintStream(err, true, StandardCharsets.UTF_8));
        } finally {
            System.clearProperty("solvik.cache.dir");
        }
    }

    private List<Path> entries() throws IOException {
        if (!Files.isDirectory(cacheRoot())) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(cacheRoot())) {
            return paths.filter(Files::isDirectory)
                    .filter(path -> !path.getFileName().toString().startsWith(".staging-"))
                    .toList();
        }
    }

    private List<String> stagingLeftovers() throws IOException {
        if (!Files.isDirectory(cacheRoot())) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(cacheRoot())) {
            return paths.filter(path -> path.getFileName().toString().startsWith(".staging-"))
                    .map(path -> path.getFileName().toString())
                    .toList();
        }
    }

    @Test
    @DisplayName("same file name in different directories with different content never collide")
    void same_name_different_content() throws IOException {
        Path first = write("a/demo.sol", "solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((\"from a\") .. \"\")\n");
        Path second = write("b/demo.sol", "solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((\"from b\") .. \"\")\n");

        assertThat(run(first)).isZero();
        assertThat(run(second)).isZero();

        // Distinct programs, distinct entries: neither run may execute the other's class files.
        assertThat(entries()).hasSize(2);
        assertThat(stagingLeftovers()).isEmpty();

        // Both entries remain valid and independent on a second run.
        assertThat(run(first)).isZero();
        assertThat(entries()).hasSize(2);
    }

    @Test
    @DisplayName("identical text at different paths gets its own entry, because imports are relative")
    void same_content_different_path() throws IOException {
        Path first = write("a/demo.sol", "solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((\"same\") .. \"\")\n");
        Path second = write("b/demo.sol", "solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((\"same\") .. \"\")\n");

        assertThat(run(first)).isZero();
        assertThat(run(second)).isZero();

        // The key covers every loaded file's path and text (LANGUAGE.md §3, "Source files"): the same
        // entry text in two directories can import two different files, so the entries must differ.
        assertThat(entries()).hasSize(2);
    }

    @Test
    @DisplayName("a changed file under the same name gets its own entry")
    void same_name_changed_content() throws IOException {
        Path source = write("demo.sol", "solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((1) .. \"\")\n");
        assertThat(run(source)).isZero();
        Files.writeString(source, "solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((2) .. \"\")\n", StandardCharsets.UTF_8);
        assertThat(run(source)).isZero();

        assertThat(entries()).hasSize(2);
    }

    @Test
    @DisplayName("concurrent processes on the same file all succeed with one published entry")
    void concurrent_processes() throws Exception {
        Path source = write("race.sol", """
                mut total := 0
                mut i := 0
                while i < 1000 {
                    total = total + i
                    i = i + 1
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("total=") .. (total))
                """);
        String expected = "total=499500\n";

        List<Process> processes = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            processes.add(startProcess(source));
        }
        for (Process process : processes) {
            assertThat(process.waitFor(120, TimeUnit.SECONDS))
                    .describedAs("process finished").isTrue();
            String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
            String err = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(process.exitValue()).describedAs("exit code, stderr: %s", err).isZero();
            assertThat(out).isEqualTo(expected);
        }

        assertThat(entries()).hasSize(1);
        assertThat(stagingLeftovers()).describedAs("failed racers clean up their staging").isEmpty();
    }

    private Process startProcess(Path source) throws IOException {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        return new ProcessBuilder(
                java,
                "-Dsolvik.cache.dir=" + cacheRoot(),
                "-cp", System.getProperty("java.class.path"),
                Main.class.getName(),
                "run", source.toString())
                .redirectErrorStream(false)
                .start();
    }
}
