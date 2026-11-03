package dev.solvik.compiler.transpile;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.SolvikCompiler;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

/** Nested arrays at run time (§6, "Nested arrays"): an element type may itself be an array. */
@DisplayName("nested arrays execute")
class NestedArrayExecutionTest {

    @TempDir
    Path directory;

    @Test
    @DisplayName("read, write, iterate, and pass a nested array to a function")
    void nested_arrays_read_write_and_pass() throws IOException {
        String output = run("""
                func flatten(rows: Integer[][]) -> Integer {
                    mut sum := 0
                    for row in rows {
                        for cell in row {
                            sum = sum + cell
                        }
                    }
                    return sum
                }

                grid := Integer[][2] {Integer[2] {1, 2}, Integer[2] {3, 4}}
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((grid.size) .. (" ") .. (grid[0].size) .. (" ") .. (grid[1][0]))
                grid[1][0] = 9
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((grid[1][0]) .. (" ") .. (flatten(grid)))
                """);
        assertThat(output).isEqualTo("2 2 3\n9 16\n");
    }

    @Test
    @DisplayName("array elements are references, so an inner array is shared, not copied")
    void nested_elements_are_references() throws IOException {
        String output = run("""
                grid := Integer[][2] {Integer[2] {1, 2}, Integer[2] {3, 4}}
                row := grid[0]
                row[1] = 7
                sameGrid := grid
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((grid[0][1]) .. (" ") .. (row === grid[0]) .. (" ") .. (sameGrid === grid) .. (" ") .. (row === grid[1]))
                """);
        assertThat(output).isEqualTo("7 true true false\n");
    }

    @Test
    @DisplayName("three levels nest the same way")
    void three_levels_nest() throws IOException {
        String output = run("""
                cube := Integer[][][1] {
                    Integer[][1] {Integer[2] {5, 6}}
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((cube[0][0][1]) .. (" ") .. (cube[0].size) .. (" ") .. (cube.size))
                """);
        assertThat(output).isEqualTo("6 1 1\n");
    }

    private String run(String source) throws IOException {
        Path sources = directory.resolve("src");
        Path classes = directory.resolve("classes");
        Files.createDirectories(sources);
        Files.createDirectories(classes);
        Map<String, String> files = new SolvikCompiler("Input.sol").transpile(source);
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
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        } finally {
            System.setOut(originalOut);
        }
        return buffer.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
