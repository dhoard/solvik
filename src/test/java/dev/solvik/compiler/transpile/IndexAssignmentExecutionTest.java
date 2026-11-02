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

/** End-to-end runs of element assignment (§6, "Indexing"). */
@DisplayName("array element assignment executed")
class IndexAssignmentExecutionTest {

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
    void an_element_can_be_written_and_read_back(@TempDir Path directory) throws Exception {
        String output = run("""
                x := Integer[2] {1, 0}
                x[0] = 2
                TextWriter(Stdio.stdout()).writeLine((x[0]) .. (" ") .. (x[1]))

                words := String[2] {"a", "b"}
                words[1] ..= "!"
                TextWriter(Stdio.stdout()).writeLine((words[0]) .. (words[1]))
                """, directory);

        assertThat(output).isEqualTo("2 0\nab!\n");
    }

    @Test
    void an_array_can_be_built_up_element_by_element(@TempDir Path directory) throws Exception {
        String output = run("""
                squares := Integer[5] {0, 0, 0, 0, 0}
                mut index := 0
                while index < squares.size {
                    squares[index] = (index + 1) * (index + 1)
                    index = index + 1
                }
                TextWriter(Stdio.stdout()).writeLine((squares[0]) .. (" ") .. (squares[1]) .. (" ") .. (squares[2]) .. (" ") .. (squares[4]))
                """, directory);

        assertThat(output).isEqualTo("1 4 9 25\n");
    }

    @Test
    void nested_elements_are_writable(@TempDir Path directory) throws Exception {
        String output = run("""
                grid := Integer[][2] {Integer[2] {1, 2}, Integer[2] {3, 4}}
                grid[1][0] = 9
                row := 0
                column := 1
                grid[row][column] = 8
                TextWriter(Stdio.stdout()).writeLine((grid[0][0]) .. (" ") .. (grid[0][1]) .. (" ") .. (grid[1][0]) .. (" ") .. (grid[1][1]))
                """, directory);

        assertThat(output).isEqualTo("1 8 9 4\n");
    }

    @Test
    @DisplayName("the array and index expressions are evaluated once per assignment")
    void the_target_and_index_are_evaluated_once(@TempDir Path directory) throws Exception {
        String output = run("""
                type Box {
                    items := Integer[3] {1, 2, 3}
                    mut reads := 0

                    pub func Box() {
                        self.reads = 0
                    }

                    pub func array() -> Integer[] {
                        self.reads = self.reads + 1
                        return self.items
                    }

                    pub func count() -> Integer {
                        return self.reads
                    }

                    pub func itemCount() -> Integer {
                        return self.items.size
                    }
                }

                box := Box()
                box.array()[0] = 7
                TextWriter(Stdio.stdout()).writeLine((box.count()) .. (" ") .. (box.itemCount()))
                """, directory);

        assertThat(output).isEqualTo("1 3\n");
    }

    @Test
    @DisplayName("a function may fill an array it was given")
    void a_parameter_can_be_mutated(@TempDir Path directory) throws Exception {
        String output = run("""
                func fill(items: String[]) {
                    items[0] = "first"
                    items[1] = "second"
                }

                words := String[2] {"", ""}
                fill(words)
                TextWriter(Stdio.stdout()).writeLine((words[0]) .. (" ") .. (words[1]))
                """, directory);

        assertThat(output).isEqualTo("first second\n");
    }

    @Test
    void a_shadowed_array_is_the_one_that_is_written(@TempDir Path directory) throws Exception {
        String output = run("""
                numbers := Integer[2] {1, 2}
                {
                    numbers := Integer[2] {5, 6}
                    numbers[0] = 9
                    outer numbers[1] = 8
                    TextWriter(Stdio.stdout()).writeLine((numbers[0]) .. (" ") .. (numbers[1]))
                }
                TextWriter(Stdio.stdout()).writeLine((numbers[0]) .. (" ") .. (numbers[1]))
                """, directory);

        assertThat(output).isEqualTo("9 6\n1 8\n");
    }

    @Test
    void byte_arrays_take_byte_values(@TempDir Path directory) throws Exception {
        String output = run("""
                bytes := Byte[3] {0, 0, 0}
                bytes[0] = Byte(1)
                bytes[2] = Byte(127)
                TextWriter(Stdio.stdout()).writeLine((bytes[0]) .. (" ") .. (bytes[1]) .. (" ") .. (bytes[2]))
                """, directory);

        assertThat(output).isEqualTo("1 0 127\n");
    }
}
