package dev.solvik.compiler.transpile;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.SolvikCompiler;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end runs of {@code break} and {@code continue} (§3, "Control flow").
 *
 * <p>The switch-inside-a-loop cases are the ones that matter: Java's {@code break} inside a switch
 * statement leaves the switch, so an unlabeled emission would compile and quietly loop forever.
 */
@DisplayName("break and continue executed")
class LoopControlExecutionTest {

    private String run(String source, Path directory) throws Exception {
        Path sources = directory.resolve("src");
        Path classes = directory.resolve("classes");
        Files.createDirectories(sources);
        Files.createDirectories(classes);

        Map<String, String> files = new SolvikCompiler("test.sol").transpile(source);
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path $path = sources.resolve(file.getKey());
            Files.createDirectories($path.getParent());
            Files.writeString($path, file.getValue());
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        List<String> arguments = Stream.concat(
                Stream.of("-d", classes.toString()),
                files.keySet().stream().map(name -> sources.resolve(name).toString())
        ).toList();
        assertThat(compiler.run(null, null, null, arguments.toArray(String[]::new))).isZero();

        PrintStream originalOut = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{classes.toUri().toURL()}, getClass().getClassLoader())) {
            System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
            loader.loadClass(GeneratedNames.PROGRAM_BINARY_NAME)
                    .getMethod("main", String[].class)
                    .invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(originalOut);
        }
        return buffer.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    @Test
    void continue_skips_and_break_stops_a_while_loop(@TempDir Path directory) throws Exception {
        String output = run("""
                mut n := 0
                while true {
                    n = n + 1
                    if n == 2 {
                        continue
                    }
                    if n > 4 {
                        break
                    }
                    TextWriter(Stdio.stdout()).writeLine((n) .. "")
                }
                TextWriter(Stdio.stdout()).writeLine(("after ") .. (n))
                """, directory);

        assertThat(output).isEqualTo("1\n3\n4\nafter 5\n");
    }

    @Test
    void continue_skips_and_break_stops_a_for_loop(@TempDir Path directory) throws Exception {
        String output = run("""
                items := Integer[6] {1, 2, 3, 4, 5, 6}
                mut sum := 0
                for item in items {
                    if item == 2 {
                        continue
                    }
                    if item > 4 {
                        break
                    }
                    sum = sum + item
                }
                TextWriter(Stdio.stdout()).writeLine((sum) .. "")
                """, directory);

        assertThat(output).isEqualTo("8\n");
    }

    @Test
    @DisplayName("break inside a switch arm leaves the loop, not the switch")
    void break_inside_a_switch_leaves_the_loop(@TempDir Path directory) throws Exception {
        String output = run("""
                items := Integer[3] {1, 2, 3}
                for item in items {
                    switch "always" {
                        case "always" {
                            if item == 2 {
                                break
                            }
                            TextWriter(Stdio.stdout()).writeLine(("arm ") .. (item))
                        }
                        default {
                            TextWriter(Stdio.stdout()).writeLine(("never") .. "")
                        }
                    }
                }
                TextWriter(Stdio.stdout()).writeLine(("left the loop") .. "")
                """, directory);

        assertThat(output).isEqualTo("arm 1\nleft the loop\n");
    }

    @Test
    @DisplayName("continue inside a switch arm starts the next iteration")
    void continue_inside_a_switch_skips_the_iteration(@TempDir Path directory) throws Exception {
        String output = run("""
                items := Integer[3] {1, 2, 3}
                for item in items {
                    switch "always" {
                        case "always" {
                            if item == 2 {
                                continue
                            }
                            TextWriter(Stdio.stdout()).writeLine(("arm ") .. (item))
                        }
                        default {
                            TextWriter(Stdio.stdout()).writeLine(("never") .. "")
                        }
                    }
                    TextWriter(Stdio.stdout()).writeLine(("after arm ") .. (item))
                }
                """, directory);

        assertThat(output).isEqualTo("arm 1\nafter arm 1\narm 3\nafter arm 3\n");
    }

    @Test
    void break_targets_the_innermost_loop(@TempDir Path directory) throws Exception {
        String output = run("""
                rows := Integer[2] {1, 2}
                for row in rows {
                    mut column := 0
                    while true {
                        column = column + 1
                        if column > 2 {
                            break
                        }
                        TextWriter(Stdio.stdout()).writeLine((row .. ":" .. column) .. "")
                    }
                }
                """, directory);

        assertThat(output).isEqualTo("1:1\n1:2\n2:1\n2:2\n");
    }

    @Test
    void a_loop_in_a_method_may_break(@TempDir Path directory) throws Exception {
        String output = run("""
                type Finder {
                    value := 0

                    pub func Finder() {
                        self.value = 0
                    }

                    pub func firstAbove(limit: Integer) -> Integer {
                        mut n := 0
                        while true {
                            n = n + 1
                            if n > limit {
                                break
                            }
                        }
                        return n
                    }
                }

                TextWriter(Stdio.stdout()).writeLine((Finder().firstAbove(4)) .. "")
                """, directory);

        assertThat(output).isEqualTo("5\n");
    }

    @Test
    void break_after_a_nested_loop_ends_the_outer_one(@TempDir Path directory) throws Exception {
        String output = run("""
                items := Integer[3] {1, 2, 3}
                for item in items {
                    if item == 2 {
                        TextWriter(Stdio.stdout()).writeLine(("skip") .. "")
                        break
                    }
                    TextWriter(Stdio.stdout()).writeLine((item) .. "")
                }
                TextWriter(Stdio.stdout()).writeLine(("done") .. "")
                """, directory);

        assertThat(output).isEqualTo("1\nskip\ndone\n");
    }
}
