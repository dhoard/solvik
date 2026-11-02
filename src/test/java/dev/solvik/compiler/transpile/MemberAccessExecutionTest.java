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
 * End-to-end runs of member access (§2, "Visibility and mutability").
 *
 * <p>A {@code pub} member is emitted as a public Java field, which is what lets generated code
 * read it from outside without an accessor method.
 */
@DisplayName("member access executed")
class MemberAccessExecutionTest {

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
    void a_public_member_is_read_from_outside_without_an_accessor(@TempDir Path directory)
            throws Exception {
        String output = run("""
                type Point {
                    pub x := 0
                    pub y := 0

                    pub func Point(x: Integer, y: Integer) {
                        self.x = x
                        self.y = y
                    }
                }

                corner := Point(3, 4)
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((corner.x) .. (" ") .. (corner.y))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((corner.x + corner.y) .. "")
                """, directory);

        assertThat(output).isEqualTo("3 4\n7\n");
    }

    @Test
    @DisplayName("a pub mut member may be written from outside, and the type sees the write")
    void a_public_mutable_member_is_written_from_outside(@TempDir Path directory) throws Exception {
        String output = run("""
                type Balance {
                    pub mut amount := 0

                    pub func Balance() {
                        self.amount = 0
                    }

                    pub func doubled() -> Integer {
                        return self.amount + self.amount
                    }
                }

                balance := Balance()
                balance.amount = 21
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((balance.amount) .. (" ") .. (balance.doubled()))
                """, directory);

        assertThat(output).isEqualTo("21 42\n");
    }

    @Test
    void a_private_member_is_reached_through_the_type(@TempDir Path directory) throws Exception {
        String output = run("""
                type Counter {
                    mut total := 0
                    mut calls := 0

                    pub func Counter() {
                        self.total = 0
                        self.calls = 0
                    }

                    pub func add(amount: Integer) {
                        self.total = self.total + amount
                        self.calls = self.calls + 1
                    }

                    pub func report() -> String {
                        return self.total .. " in " .. self.calls
                    }
                }

                counter := Counter()
                counter.add(2)
                counter.add(3)
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((counter.report()) .. "")
                """, directory);

        assertThat(output).isEqualTo("5 in 2\n");
    }

    @Test
    @DisplayName("members of a generic type keep their visibility and mutability")
    void a_generic_type_publishes_its_members(@TempDir Path directory) throws Exception {
        String output = run("""
                type Box<T> {
                    pub mut value: T? = Null

                    pub func Box(value: T) {
                        self.value = value
                    }
                }

                box := Box(1)
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((box.value) .. (" ") .. (box.value == Null))
                box.value = 2
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((box.value) .. "")
                """, directory);

        assertThat(output).isEqualTo("1 false\n2\n");
    }

    @Test
    @DisplayName("plain member names and self. reach the same storage")
    void plain_member_names_are_the_same_storage(@TempDir Path directory) throws Exception {
        String output = run("""
                type Counter {
                    mut total := 0
                    label := "counter"

                    pub func Counter(label: String) {
                        self.label = label
                        total = 0
                    }

                    pub func add(amount: Integer) {
                        total = total + amount
                        bump()
                    }

                    func bump() {
                        total = total + 1
                    }

                    pub func doubled() -> Integer {
                        mut total := 100
                        return total + self.total
                    }

                    pub func report() -> String {
                        return label .. "=" .. total
                    }
                }

                counter := Counter("hits")
                counter.add(2)
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((counter.report()) .. (" ") .. (counter.doubled()))
                """, directory);

        // add(2) stores 2, bump() makes 3, and doubled() sees 100 + 3 through `self.total`.
        assertThat(output).isEqualTo("hits=3 103\n");
    }

    @Test
    void public_members_of_nested_types_are_reachable(@TempDir Path directory) throws Exception {
        String output = run("""
                type Inner {
                    pub label := "in"

                    pub func Inner() {
                        self.label = "in"
                    }
                }

                type Outer {
                    pub inner := Inner()
                    pub name := "out"

                    pub func Outer() {
                        self.inner = Inner()
                        self.name = "out"
                    }

                    pub func describe() -> String {
                        return self.name .. "/" .. self.inner.label
                    }
                }

                container := Outer()
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((container.name) .. (" ") .. (container.inner.label) .. (" ") .. (container.describe()))
                """, directory);

        assertThat(output).isEqualTo("out in out/in\n");
    }
}
