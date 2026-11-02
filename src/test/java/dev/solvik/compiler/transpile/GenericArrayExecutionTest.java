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
 * Generic arrays: an array whose element type is a type parameter (§2, "Generics"). Java cannot write
 * {@code new T[]}, so the array is created with its erased component type and cast back; Solvik never
 * reflects on the component type, so the erasure is invisible.
 */
@DisplayName("generic arrays execute")
class GenericArrayExecutionTest {

    @TempDir
    Path directory;

    @Test
    @DisplayName("a type parameter may be the element type of an array")
    void a_generic_array_holds_values_of_the_inferred_type() throws IOException {
        String output = run("""
                type Slots<T> {
                    pub mut items: T?[3] = T?[3] { }
                    mut count := 0

                    pub func Slots(seed: T) {
                        self.items[0] = seed
                        self.count = 1
                    }

                    pub func add(value: T) {
                        self.items[self.count] = value
                        self.count = self.count + 1
                    }

                    pub func last() -> T? {
                        return self.items[self.count - 1]
                    }
                }

                words := Slots("a")
                words.add("b")
                words.add("c")
                numbers := Slots(10)
                numbers.add(20)
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((words.last()) .. (numbers.last()) .. (" ") .. (words.items))
                """);
        assertThat(output).isEqualTo("c20 [a, b, c]\n");
    }

    @Test
    @DisplayName("a generic container indexes in constant time through a two-level array")
    void a_generic_container_indexes_in_constant_time() throws IOException {
        String output = run("""
                type List<T> {
                    pub mut blocks: T?[][4] = T?[][4] { T?[2] { }, T?[2] { }, T?[2] { }, T?[2] { } }
                    mut count := 0

                    pub func List(seed: T) {
                        self.blocks[0][0] = seed
                        self.count = 1
                    }

                    pub func add(value: T) {
                        index := self.count
                        block := index / 2
                        self.blocks[block][index % 2] = value
                        self.count = self.count + 1
                    }

                    pub func get(index: Integer) -> T? {
                        return self.blocks[index / 2][index % 2]
                    }

                    // `size()` is the array built-in and shadows a method of that name, so a
                    // container names its own count differently.
                    pub func count() -> Integer {
                        return self.count
                    }
                }

                list := List(0)
                list.add(1)
                list.add(2)
                list.add(3)
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((list.count()) .. (" ") .. (list.get(0)) .. (list.get(3)))
                """);
        assertThat(output).isEqualTo("4 03\n");
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
