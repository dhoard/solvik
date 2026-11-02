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

/**
 * An array's length is part of its type (§5). A non-nullable element type must list every element;
 * a nullable one may list fewer, and the omission is defined by the construction rule.
 */
@DisplayName("declared array size")
class ArraySizeExecutionTest {

    @TempDir
    Path directory;

    @Test
    @DisplayName("size() is the declared length, and an omitted nullable element is Null (§5.1)")
    void declared_length_fills_the_remaining_elements_with_null() throws IOException {
        String output = run("""
                names := String?[4] {"ann", "bob"}
                TextWriter(Stdio.stdout()).writeLine((names.size) .. (" ") .. (names[1]) .. (" ") .. (names[2] == Null) .. (" ") .. (names))
                names[3] = "dee"
                TextWriter(Stdio.stdout()).writeLine((names[3]) .. (" ") .. (names.size))
                """);
        assertThat(output).isEqualTo("4 bob true [ann, bob, null, null]\ndee 4\n");
    }

    @Test
    @DisplayName("the size survives a call, and iterating visits every declared slot")
    void the_declared_size_is_part_of_the_value() throws IOException {
        String output = run("""
                func counts(values: Integer?[]) -> Integer {
                    mut seen := 0
                    for v in values {
                        seen = seen + 1
                    }
                    return seen
                }

                numbers := Integer?[5] {1, 2, 3}
                TextWriter(Stdio.stdout()).writeLine((numbers.size) .. (" ") .. (counts(numbers)) .. (" ") .. (numbers.size))
                """);
        assertThat(output).isEqualTo("5 5 5\n");
    }

    @Test
    @DisplayName("a mutable slot can be filled after the array is created")
    void an_unset_slot_is_assignable_later() throws IOException {
        String output = run("""
                cells := Integer?[3] { }
                TextWriter(Stdio.stdout()).writeLine((cells.size) .. (" ") .. (cells[0] == Null) .. (" ") .. (cells))
                cells[0] = 7
                cells[2] = 9
                TextWriter(Stdio.stdout()).writeLine((cells[0]) .. (" ") .. (cells[1] == Null) .. (" ") .. (cells[2]))
                """);
        assertThat(output).isEqualTo("3 true [null, null, null]\n7 true 9\n");
    }

    @Test
    @DisplayName("each level of a nested array keeps its own declared size")
    void nested_levels_keep_their_declared_sizes() throws IOException {
        String output = run("""
                grid := Integer?[][3] {Integer?[2] {1}, Integer?[2] {2}, Integer?[2] {3}}
                TextWriter(Stdio.stdout()).writeLine((grid.size) .. (" ") .. (grid[0].size) .. (" ") .. (grid[1][1] == Null))
                grid[1] = Integer?[2] {8, 9}
                TextWriter(Stdio.stdout()).writeLine((grid[1][1]) .. (" ") .. (grid))
                """);
        assertThat(output).isEqualTo("3 2 true\n9 [[1, null], [8, 9], [3, null]]\n");
    }

    @Test
    @DisplayName("the size member is the constructed length, in every expression position (§5)")
    void the_size_member_is_the_constructed_length() throws IOException {
        String output = run("""
                fixed := Byte[10] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9}
                TextWriter(Stdio.stdout()).writeLine((fixed.size) .. (" ") .. (fixed[9]))
                slots := Byte?[10] {}
                TextWriter(Stdio.stdout()).writeLine((slots.size) .. (" ") .. (slots[0] == Null))
                TextWriter(Stdio.stdout()).writeLine((fixed.size .. " items") .. (" ") .. (fixed.size == 10) .. (" ") .. (fixed.size + 1))
                mut limit := 0
                mut index := 0
                while index < fixed.size {
                    index = index + 1
                    limit = index
                }
                TextWriter(Stdio.stdout()).writeLine(("loop: ") .. (limit) .. (" ") .. (fixed[fixed.size - 1]))
                """);
        assertThat(output).isEqualTo("10 9\n10 true\n10 items true 11\nloop: 10 9\n");
    }

    @Test
    @DisplayName("a size-erased array and an array field report their runtime length (§5)")
    void erased_and_field_arrays_report_their_length() throws IOException {
        String output = run("""
                func width(values: Integer[]) -> Integer {
                    return values.size
                }
                type Row {
                    pub cells := Integer[3] {1, 2, 3}
                    pub func Row() { }
                }
                fields := "a,b,c".split(",")
                TextWriter(Stdio.stdout()).writeLine((fields.size) .. (" ") .. (width(Integer[2] {1, 2})) .. (" ") .. (Row().cells.size))
                """);
        assertThat(output).isEqualTo("3 2 3\n");
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
