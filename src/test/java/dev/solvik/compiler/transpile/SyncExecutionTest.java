package dev.solvik.compiler.transpile;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.SolvikCompiler;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code sync} blocks run with Java monitor semantics ({@code LANGUAGE.md} §7.5).
 *
 * <p>Every case asserts a post-{@code await()} total or a fixed string, so no assertion depends on
 * interleaving: contention is proved by an exact count, and release is proved by entering the same
 * monitor again after the block was left by {@code raise}, {@code return}, {@code break},
 * {@code continue}, an abort, and {@code exit}.
 */
@DisplayName("sync blocks execute with Java monitor semantics (§7.5)")
class SyncExecutionTest {

    private static final String COUNTER = """
            type Counter {
                pub mut total := 0
                pub func Counter() { }
                pub func bump() { total = total + 1 }
            }

            type Bumper implements solvik::concurrency::Task {
                counter := Counter()
                rounds := 0
                pub func Bumper(counter: Counter, rounds: Integer) {
                    self.counter = counter
                    self.rounds = rounds
                }
                pub func run() {
                    mut done := 0
                    while done < rounds {
                        sync counter {
                            counter.bump()
                        }
                        done = done + 1
                    }
                }
            }
            """;

    /** One {@code writeLine} statement for the expression inside it. */
    private static String writeLine(String expression) {
        return "solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((" + expression
                + "))\n";
    }

    /** The program's standard output and the throwable that ended it, if any. */
    private record Outcome(String stdout, Throwable failure) {

        String failureClass() {
            return failure == null ? "" : failure.getClass().getName();
        }

        Object failureCode() throws Exception {
            assertThat(failure).isNotNull();
            return failure.getClass().getMethod("code").invoke(failure);
        }
    }

    private static Outcome run(String source, Path directory) throws Exception {
        Map<String, String> files = new SolvikCompiler("sync.sol").transpile(source);
        Path sources = Files.createDirectories(directory.resolve("src"));
        Path classes = Files.createDirectories(directory.resolve("classes"));
        Path emptyClasspath = Files.createDirectories(directory.resolve("empty-classpath"));
        List<String> arguments = new ArrayList<>(List.of("--release", "25", "-classpath",
                emptyClasspath.toString(), "-d", classes.toString()));
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path path = sources.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, file.getValue(), StandardCharsets.UTF_8);
            arguments.add(path.toString());
        }
        assertThat(ToolProvider.getSystemJavaCompiler()
                .run(null, null, null, arguments.toArray(String[]::new)))
                .describedAs("javac on the generated sources").isZero();

        PrintStream originalOut = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Throwable failure = null;
        // No compiler or third-party classes are visible to the generated program.
        try (URLClassLoader loader = new URLClassLoader(new URL[]{classes.toUri().toURL()}, null);
                PrintStream captured = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            System.setOut(captured);
            try {
                loader.loadClass(GeneratedNames.PROGRAM_BINARY_NAME)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            } catch (InvocationTargetException thrown) {
                failure = thrown.getCause();
            } finally {
                System.setOut(originalOut);
            }
        }
        return new Outcome(buffer.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"), failure);
    }

    private static void assertOutput(Outcome outcome, String... lines) {
        assertThat(outcome.failure()).isNull();
        assertThat(outcome.stdout()).isEqualTo(String.join("", List.of(lines).stream()
                .map(line -> line + "\n").toList()));
    }

    @Test
    @DisplayName("two tasks contending for one monitor keep every update")
    void contention_keeps_every_update(@TempDir Path directory) throws Exception {
        Outcome outcome = run(COUNTER + """
                counter := Counter()
                first := solvik::concurrency::Thread.start(Bumper(counter, 1000))
                second := solvik::concurrency::Thread.start(Bumper(counter, 1000))
                first.await()
                second.await()
                """
                + writeLine("\"total=\" .. (counter.total) .. \"\""), directory);

        assertOutput(outcome, "total=2000");
    }

    @Test
    @DisplayName("a nested block on the same monitor runs instead of deadlocking")
    void a_nested_block_on_the_same_monitor_is_reentrant(@TempDir Path directory) throws Exception {
        Outcome outcome = run(COUNTER + """
                counter := Counter()
                sync counter {
                    sync counter {
                        counter.bump()
                    }
                    counter.bump()
                }
                """
                + writeLine("\"total=\" .. (counter.total) .. \"\""), directory);

        assertOutput(outcome, "total=2");
    }

    @Test
    @DisplayName("an array value is a monitor")
    void an_array_is_a_monitor(@TempDir Path directory) throws Exception {
        Outcome outcome = run("""
                values := Integer[2] {0, 0}
                sync values {
                    values[0] = 41
                    values[1] = values[0] + 1
                }
                """
                + writeLine("(values[0]) .. (\"/\") .. (values[1])"), directory);

        assertOutput(outcome, "41/42");
    }

    @Test
    @DisplayName("return inside the block returns and releases the monitor")
    void a_return_releases_the_monitor(@TempDir Path directory) throws Exception {
        Outcome outcome = run(COUNTER + """
                func bumpThenReturn(counter: Counter) -> Integer {
                    sync counter {
                        counter.bump()
                        return counter.total
                    }
                }

                counter := Counter()
                """
                + writeLine("\"returned=\" .. (bumpThenReturn(counter)) .. \"\"")
                + "sync counter { counter.bump() }\n"
                + writeLine("\"total=\" .. (counter.total) .. \"\""), directory);

        assertOutput(outcome, "returned=1", "total=2");
    }

    @Test
    @DisplayName("break and continue inside the block release the monitor each time")
    void loop_control_releases_the_monitor(@TempDir Path directory) throws Exception {
        Outcome outcome = run(COUNTER + """
                counter := Counter()
                mut trace := ""
                for step in Integer[4] {1, 2, 3, 4} {
                    sync counter {
                        if step == 2 {
                            continue
                        }
                        if step == 4 {
                            break
                        }
                        counter.bump()
                        trace ..= String(step)
                    }
                }
                """
                + writeLine("\"trace=\" .. (trace) .. \" total=\" .. (counter.total)"), directory);

        assertOutput(outcome, "trace=13 total=2");
    }

    @Test
    @DisplayName("a raise inside the block leaves the monitor free for the next block")
    void a_raise_releases_the_monitor(@TempDir Path directory) throws Exception {
        Outcome outcome = run(COUNTER + """
                counter := Counter()
                try {
                    sync counter {
                        counter.bump()
                        raise Exception(4009, "inside")
                    }
                } catch (problem: Exception) {
                """
                + writeLine("\"caught=\" .. (problem) .. \"\"")
                + "}\n"
                + "sync counter { counter.bump() }\n"
                + writeLine("\"total=\" .. (counter.total) .. \"\""), directory);

        assertOutput(outcome, "caught=[4009] inside", "total=2");
    }

    @Test
    @DisplayName("a runtime abort inside the block keeps its code")
    void an_abort_keeps_its_code(@TempDir Path directory) throws Exception {
        Outcome outcome = run(COUNTER + """
                counter := Counter()
                sync counter {
                    zero := 0
                    counter.bump()
                """
                + writeLine("(10 / zero) .. \"\"")
                + "}\n", directory);

        assertThat(outcome.failureClass()).isEqualTo(GeneratedNames.errorBinaryName());
        assertThat(outcome.failureCode()).isEqualTo("SOL-R001");
        assertThat(outcome.stdout()).isEmpty();
    }

    @Test
    @DisplayName("exit inside the block keeps its status")
    void an_exit_keeps_its_status(@TempDir Path directory) throws Exception {
        Outcome outcome = run(COUNTER + """
                counter := Counter()
                sync counter {
                    counter.bump()
                """
                + writeLine("(\"before exit\") .. \"\"")
                + "    exit(7)\n"
                + "}\n", directory);

        assertThat(outcome.failureClass()).isEqualTo(GeneratedNames.exitBinaryName());
        assertThat(outcome.failureCode()).isEqualTo(7);
        assertThat(outcome.stdout()).isEqualTo("before exit\n");
    }
}
