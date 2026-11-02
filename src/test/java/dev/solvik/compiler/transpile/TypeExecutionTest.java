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

/** End-to-end runs of programs that declare user-defined types. */
@DisplayName("User types executed")
class TypeExecutionTest {

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
    @DisplayName("numeric equality follows boxed value equality across carriers")
    void numeric_equality_widens(@TempDir Path directory) throws Exception {
        String output = run("""
                TextWriter(Stdio.stdout()).writeLine((1 == 1.0) .. "")
                TextWriter(Stdio.stdout()).writeLine((1.0 == 1.00) .. "")
                TextWriter(Stdio.stdout()).writeLine((Byte(100) == 100) .. "")
                TextWriter(Stdio.stdout()).writeLine((Integer(1) == 1) .. "")
                TextWriter(Stdio.stdout()).writeLine((1 != 1.0) .. "")
                TextWriter(Stdio.stdout()).writeLine((1 == 1.5) .. "")
                TextWriter(Stdio.stdout()).writeLine(("1" == "1") .. "")
                TextWriter(Stdio.stdout()).writeLine((1 === 1.0) .. "")
                """, directory);

        assertThat(output).isEqualTo("true\ntrue\ntrue\ntrue\nfalse\nfalse\ntrue\nfalse\n");
    }

    @Test
    @DisplayName("a type named Solvik and SolvikProgram runs beside the generated classes")
    void types_named_like_generated_classes(@TempDir Path directory) throws Exception {
        String output = run("""
                type Solvik {
                    name := "s"

                    pub func Solvik(name: String) {
                        self.name = name
                    }

                    pub func describe() -> String {
                        return "Solvik(" .. self.name .. ")"
                    }
                }

                type SolvikProgram {
                    label := "p"

                    pub func SolvikProgram(label: String) {
                        self.label = label
                    }

                    pub func describe() -> String {
                        return "Program(" .. self.label .. ")"
                    }
                }

                a := Solvik("alpha")
                b := SolvikProgram("beta")
                TextWriter(Stdio.stdout()).writeLine((a.describe()) .. "")
                TextWriter(Stdio.stdout()).writeLine((b.describe()) .. "")
                """, directory);

        assertThat(output).isEqualTo("Solvik(alpha)\nProgram(beta)\n");
    }

    @Test
    @DisplayName("fields, constructor, methods, self, and mutation")
    void counter_with_state(@TempDir Path directory) throws Exception {
        String output = run("""
                type Counter {
                    mut count := 0
                    name := "counter"

                    pub func Counter(name: String) {
                        self.name = name
                    }

                    pub func bump(by: Integer) {
                        self.count = self.count + by
                    }

                    pub func describe() -> String {
                        return self.name .. "=" .. self.count
                    }
                }

                c := Counter("hits")
                c.bump(2)
                c.bump(3)
                TextWriter(Stdio.stdout()).writeLine((c.describe()) .. "")
                TextWriter(Stdio.stdout()).writeLine((c.type()) .. "")
                TextWriter(Stdio.stdout()).writeLine((c.toString() == c.toString()) .. "")
                """, directory);

        assertThat(output).isEqualTo("hits=5\nCounter\ntrue\n");
    }

    @Test
    @DisplayName("generic types infer their arguments, substitute them, and report their name")
    void generic_types(@TempDir Path directory) throws Exception {
        String output = run("""
                type Box<T> {
                    mut value: T? = Null

                    pub func Box(value: T) {
                        self.value = value
                    }

                    pub func get() -> T? {
                        return self.value
                    }
                }

                numbers := Box(1)
                words := Box("text")
                count := numbers.get()
                if count != Null {
                    TextWriter(Stdio.stdout()).writeLine((count + 1) .. (" ") .. (numbers.type()))
                }
                TextWriter(Stdio.stdout()).writeLine((words.get()) .. (" ") .. (words.type()))
                """, directory);

        assertThat(output).isEqualTo("2 Box<Integer>\ntext Box<String>\n");
    }

    @Test
    @DisplayName("two parameters, nested instantiations, and generic values inside Any arrays")
    void two_parameters_and_nesting(@TempDir Path directory) throws Exception {
        String output = run("""
                type Box<T> {
                    mut value: T? = Null

                    pub func Box(value: T) {
                        self.value = value
                    }

                    pub func get() -> T? {
                        return self.value
                    }
                }

                type Pair<A, B> {
                    first: A? = Null
                    second: B? = Null

                    pub func Pair(first: A, second: B) {
                        self.first = first
                        self.second = second
                    }

                    pub func describe() -> String {
                        return "(" .. self.first .. ", " .. self.second .. ")"
                    }
                }

                pair := Pair(1, "one")
                TextWriter(Stdio.stdout()).writeLine((pair.describe()) .. (" ") .. (pair.type()))

                nested := Box(Box(7))
                inner := nested.get()
                if inner != Null {
                    seven := inner.get()
                    if seven != Null {
                        TextWriter(Stdio.stdout()).writeLine((seven + 1) .. (" ") .. (nested.type()))
                    }
                }

                values := Any[2] {Box(1), Box("x")}
                for value in values {
                    TextWriter(Stdio.stdout()).writeLine((value.type()) .. "")
                }
                """, directory);

        assertThat(output).isEqualTo("""
                (1, one) Pair<Integer, String>
                8 Box<Box<Integer>>
                Box<Integer>
                Box<String>
                """.replace("\r\n", "\n"));
    }

    @Test
    @DisplayName("types reach Any positions, arrays, and flow through for-in")
    void types_inside_arrays(@TempDir Path directory) throws Exception {
        String output = run("""
                type Item {
                    name := "?"

                    pub func Item(name: String) {
                        self.name = name
                    }

                    pub func toString() -> String {
                        return "Item(" .. self.name .. ")"
                    }
                }

                items := Any[3] {Item("a"), Item("b"), Item("c")}
                for item in items {
                    TextWriter(Stdio.stdout()).writeLine((item.type()) .. (" ") .. (item.toString()))
                }
                """, directory);

        assertThat(output).isEqualTo("Item Item(a)\nItem Item(b)\nItem Item(c)\n");
    }
}
