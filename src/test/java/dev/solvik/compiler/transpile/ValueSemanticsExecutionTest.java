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
 * {@code ===}, {@code hashCode()}, and rendered values (§7, §8, §13.6).
 *
 * <p>Scalars have no identity beyond their value, so {@code ===} compares values instead of
 * exposing Java's integer cache; equal numbers hash alike whatever their carrier or scale.
 */
@DisplayName("identity, hashing, and rendering executed")
class ValueSemanticsExecutionTest {

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
    @DisplayName("a negative literal renders through the Object carrier without breaking Java")
    void negative_literals_render_through_the_object_carrier(@TempDir Path directory)
            throws Exception {
        String output = run("""
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((-1 .. "x") .. (" ") .. ("y" .. -2))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((String(-1)) .. (" ") .. (String(-1.5)) .. (" ") .. (String(-1L)))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((-1) .. (" ") .. (-2.5))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(((0 - 1) .. "z") .. "")
                """, directory);

        assertThat(output).isEqualTo("-1x y-2\n-1 -1.5 -1\n-1 -2.5\n-1z\n");
    }

    @Test
    @DisplayName("=== on scalars compares values, so no cache size can change the answer")
    void scalar_identity_is_value_identity(@TempDir Path directory) throws Exception {
        String output = run("""
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Byte(7) === Byte(7)) .. (" ") .. (Byte(100) === Byte(100)))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Byte(7) === Byte(100)) .. "")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((1 === 1) .. (" ") .. (1.0 === 1.0) .. (" ") .. (1 === 2))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("a" === "a") .. (" ") .. ("a" === "b"))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((true === true) .. (" ") .. (true === false))
                first := 1
                second := 1
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((first === second) .. "")
                """, directory);

        assertThat(output).isEqualTo("""
                true true
                false
                true true false
                true false
                true false
                true
                """);
    }

    @Test
    @DisplayName("=== on arrays and user types stays reference identity")
    void reference_identity_is_unchanged(@TempDir Path directory) throws Exception {
        String output = run("""
                left := Integer[1] {1}
                right := Integer[1] {1}
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((left === left) .. (" ") .. (left === right) .. (" ") .. (left == right))

                type Point {
                    x := 0

                    pub func Point() {
                        self.x = 0
                    }
                }

                p := Point()
                q := Point()
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((p === p) .. (" ") .. (p === q) .. (" ") .. (p == q))
                """, directory);

        // Arrays and user types are mutable objects: `==` and `===` are both identity for them,
        // because value equality for them is still open (§13.9).
        assertThat(output).isEqualTo("true false false\ntrue false false\n");
    }

    @Test
    void array_equality_is_reference_based_for_partial_nested_and_any_values(
            @TempDir Path directory) throws Exception {
        String output = run("""
                left := Integer?[2] {1}
                right := Integer?[2] {1}
                alias := left
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((left == alias) .. (" ") .. (left != right) .. (" ") .. (left.equals(right)))
                nested := Integer?[][1] {left}
                other := Integer?[][1] {left}
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((nested == nested) .. (" ") .. (nested == other))
                values := Any[2] {left, right}
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((values[0] == values[0]) .. (" ") .. (values[0].equals(values[1])))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((left == Null) .. (" ") .. (Null == left) .. (" ") .. (Null == Null))
                """, directory);

        assertThat(output).isEqualTo("""
                true true false
                true false
                true false
                false false true
                """);
    }

    @Test
    @DisplayName("equal numbers hash alike whatever their carrier or scale")
    void hashing_agrees_with_widened_equality(@TempDir Path directory) throws Exception {
        String output = run("""
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((1 == 1.0) .. (" ") .. (1.hashCode() == 1.0.hashCode()))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Byte(1).hashCode() == 1.hashCode()) .. (" ") .. (Byte(1) == 1))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((1.50.hashCode() == 1.5.hashCode()) .. (" ") .. (1.50 == 1.5))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((2.hashCode() == 2.0.hashCode()) .. "")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((1.hashCode() == 2.hashCode()) .. "")
                """, directory);

        // `==` promotes like Java's primitive comparison, while `hashCode` is Java's boxed hash:
        // the same asymmetry Java itself has between `1 == 1.0` and `Integer.equals(Double)`.
        assertThat(output).isEqualTo("""
                true false
                true true
                true true
                false
                false
                """);
    }

    @Test
    @DisplayName("a BigDecimal renders without scientific notation and keeps its written scale")
    void decimals_render_in_plain_form(@TempDir Path directory) throws Exception {
        String output = run("""
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((BigDecimal("1e3")) .. (" ") .. (BigDecimal("1e-3")) .. (" ") .. (BigDecimal("1.50")))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((1.0 / 4) .. (" ") .. (0.1 + 0.2))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((BigDecimal("1e3") .. " and " .. BigDecimal("2.500")) .. "")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((String(BigDecimal("1e3"))) .. "")
                """, directory);

        assertThat(output).isEqualTo("""
                1000 0.001 1.50
                0.25 0.30000000000000004
                1000 and 2.500
                1000
                """);
    }
}
