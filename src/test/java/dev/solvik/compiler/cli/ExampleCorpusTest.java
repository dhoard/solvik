package dev.solvik.compiler.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.JavaTarget;
import dev.solvik.compiler.SolvikCompiler;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.load.ProgramLoader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the complete language corpus at the repository root (LANGUAGE.md §1–§14, PROJECT.md
 * "Language corpus"). The corpus is the executable inventory of the language surface, so it must
 * compile and run through both declaration backends, and its generated Java must stay warning-free.
 */
@DisplayName("example.sol language corpus")
class ExampleCorpusTest {

    @TempDir
    Path directory;

    @AfterEach
    void clearProperties() {
        System.clearProperty("solvik.cache.dir");
    }

    @Test
    void the_complete_corpus_compiles_and_runs() {
        System.setProperty("solvik.cache.dir", directory.resolve("cache").toString());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        Path corpus = Path.of("example.sol").toAbsolutePath();
        assertThat(corpus).exists();

        int status = Main.run(new String[]{"run", corpus.toString()},
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        String stdout = out.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertThat(status).isZero();
        assertThat(err.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(stdout).contains(
                "more escapes: true 22\n",
                "grouped concatenation: true\n",
                "suffix types: Byte Short Integer Long Float Double BigInteger BigDecimal Float\n",
                "boxed equality: false true false true\n",
                "user equals: false true false false false\n",
                "module merge: counter=1 int:1 string:x module-private\n",
                "qualified trait default: override:echo:x override:echo:y\n",
                "declared toString: hello world hello world Text\n",
                "generic null: null true\n",
                "composed defaults: first+second\n",
                "virtual thread result: 42 alive=false\n",
                "thread interrupt: true alive=false\n",
                "sync guarded: 1000\n",
                "sync nested: 1001\n",
                "sync returned: 1002\n",
                "sync raise: [4009] raised inside sync\n",
                "sync after raise: 1003\n",
                "caught: [4001] value must be positive, found 0\n",
                "dispatch: denied value must be positive, found 0\n",
                "switch: denied\n",
                "accepted 5\n",
                "returned 1 2\n",
                "finally trace: 1!!!\n",
                "inner cleanup\n",
                "outer caught: 4002 inner failure\n",
                "reraised: [4004] wrapped again\n",
                "heldOver: Exception 4004 [4004] wrapped again\n",
                "planned: 4001 planned failure\n",
                "final status code: 0\n",
                "memory: open=true read=true write=true\n",
                "read 5 bytes: [102, 105, 114, 115, 116]\n",
                "line 1: []\n",
                "line 2: [second]\n",
                "lines: 4\n",
                "write accepted 4 bytes\n",
                "round trip: 6 bytes, first=0, last=66\n",
                "echo: echoed eof=true\n",
                "captured: captured to memory\n",
                "stdin readable=true writable=false\n",
                "stdin: line from memory stdin\n",
                "stderr: captured stderr\n",
                "closed: true the channel is closed\n",
                "protected: true\n",
                "encoding error: true after 1 byte\n",
                "file: path=true open=true canWrite=true\n",
                "file: canRead=true canWrite=false\n",
                "file bytes: [0, 127, -128, -1] then [65, 66] eof=true\n",
                "file: reader closed, channel open=true\n",
                "file closed: true\n",
                "byte reader closed: true\n",
                "file missing: true\n",
                "file exists: true\n",
                "file deleted: true gone=true\n",
                "static before=\n",
                "iteration: 15 ab 1\n",
                "protocol: 5\n",
                "type body: 3 2 root\n",
                "static mention=\n",
                "static ordered=field-block:field\n",
                "static ordered again=field-block:field\n",
                "static singleton=configured\n");
        assertThat(stdout).doesNotContain("unexpected");
    }

    @Test
    void generated_java_is_warning_free() throws IOException {
        Diagnostics diagnostics = new Diagnostics();
        ProgramLoader.Loaded loaded =
                new ProgramLoader(diagnostics).load(Path.of("example.sol").toAbsolutePath());
        assertThat(diagnostics.all()).isEmpty();

        Map<String, String> files = new SolvikCompiler("example.sol").transpile(loaded);
        Path sources = Files.createDirectories(directory.resolve("lint/src"));
        Path classes = Files.createDirectories(directory.resolve("lint/classes"));
        Path emptyClasspath = Files.createDirectories(
                directory.resolve("lint/empty-classpath"));
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path path = sources.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, file.getValue(), StandardCharsets.UTF_8);
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).isNotNull();
        DiagnosticCollector<JavaFileObject> collected = new DiagnosticCollector<>();
        try (StandardJavaFileManager manager =
                     compiler.getStandardFileManager(collected, Locale.ROOT, StandardCharsets.UTF_8)) {
            List<String> options = List.of("--release", JavaTarget.RELEASE_OPTION, "-proc:none",
                    "-Xlint:all", "-Werror", "-classpath", emptyClasspath.toString(),
                    "-d", classes.toString());
            boolean ok = compiler.getTask(new StringWriter(), manager, collected, options, null,
                    manager.getJavaFileObjectsFromPaths(files.keySet().stream()
                            .map(name -> sources.resolve(name)).toList())).call();
            assertThat(collected.getDiagnostics()).isEmpty();
            assertThat(ok).isTrue();
        }
    }
}
