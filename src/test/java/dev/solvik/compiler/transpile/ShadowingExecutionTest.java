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
 * End-to-end runs of shadowing and {@code outer} (§3, "Scope and shadowing").
 *
 * <p>These compile the generated Java, which is the point: Java forbids shadowing a local
 * variable, so every case here fails with a {@code javac} error if a shadow is not renamed.
 */
@DisplayName("scope and shadowing executed")
class ShadowingExecutionTest {

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
    @DisplayName("a shadow hides the outer value without changing it")
    void a_shadow_does_not_change_the_outer_value(@TempDir Path directory) throws Exception {
        String output = run("""
                x := 1
                {
                    x := 2
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("inner ") .. (x))
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("outer ") .. (x))
                """, directory);

        assertThat(output).isEqualTo("inner 2\nouter 1\n");
    }

    @Test
    void outer_reads_the_shadowed_value(@TempDir Path directory) throws Exception {
        String output = run("""
                x := 1
                {
                    x := outer x + 1
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((x) .. "")
                    {
                        x := outer x * 10
                        solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((x) .. "")
                    }
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((x) .. "")
                """, directory);

        // The inner block's `outer x` is the middle binding (2), not the outermost (1).
        assertThat(output).isEqualTo("2\n20\n1\n");
    }

    @Test
    void outer_assigns_through_to_the_shadowed_value(@TempDir Path directory) throws Exception {
        String output = run("""
                mut count := 0
                {
                    mut count := 10
                    outer count = outer count + 1
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("inner ") .. (count))
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("outer ") .. (count))
                """, directory);

        assertThat(output).isEqualTo("inner 10\nouter 1\n");
    }

    @Test
    @DisplayName("a shadow may take a different type, which is the point of reusing a name")
    void a_shadow_may_change_type(@TempDir Path directory) throws Exception {
        String output = run("""
                value := 1
                {
                    value := "text"
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((value) .. (" ") .. (value.type()))
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((value) .. (" ") .. (value.type()))
                """, directory);

        assertThat(output).isEqualTo("text String\n1 Integer\n");
    }

    @Test
    void a_loop_body_and_a_parameter_may_be_shadowed(@TempDir Path directory) throws Exception {
        String output = run("""
                func show(item: Integer) -> String {
                    {
                        item := "shadow"
                        return item .. " over " .. outer item
                    }
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((show(3)) .. "")

                items := Integer[2] {1, 2}
                for item in items {
                    item := item * 100
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((item) .. "")
                }
                """, directory);

        assertThat(output).isEqualTo("shadow over 3\n100\n200\n");
    }

    @Test
    void a_shadowed_slot_is_readable_through_outer(@TempDir Path directory) throws Exception {
        String output = run("""
                mut slot: Integer? = Null
                slot = 5
                {
                    mut slot := 9
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((slot) .. (" ") .. (outer slot))
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((slot) .. "")
                """, directory);

        assertThat(output).isEqualTo("9 5\n5\n");
    }

    @Test
    @DisplayName("a shadow inside a method of a user type keeps fields untouched")
    void a_shadow_inside_a_type_method(@TempDir Path directory) throws Exception {
        String output = run("""
                type Counter {
                    mut total := 0

                    pub func Counter() {
                        self.total = 0
                    }

                    pub func add(amount: Integer) {
                        total := amount
                        {
                            total := outer total + 1
                            self.total = self.total + total
                        }
                        self.total = self.total + total
                    }

                    pub func report() -> String {
                        return "total " .. self.total
                    }
                }

                counter := Counter()
                counter.add(10)      // adds 11 then 10
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((counter.report()) .. "")
                """, directory);

        assertThat(output).isEqualTo("total 21\n");
    }

    @Test
    @DisplayName("several shadows in sibling blocks each get their own storage")
    void sibling_blocks_may_reuse_a_name(@TempDir Path directory) throws Exception {
        String output = run("""
                base := 0
                {
                    base := 1
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((base) .. "")
                }
                {
                    base := 2
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((base) .. "")
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((base) .. "")
                """, directory);

        assertThat(output).isEqualTo("1\n2\n0\n");
    }
}
