package dev.solvik.compiler.transpile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.solvik.compiler.SolvikCompiler;
import dev.solvik.compiler.testsupport.PackagedLibrary;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Transpiler")
class TranspilerTest {

    private static Map<String, String> transpile(String source) {
        return new SolvikCompiler("test.sol").transpile(source);
    }

    @Nested
    @DisplayName("emitted files")
    class EmittedFiles {

        @Test
        void emits_one_file_per_class_named_after_it() {
            Map<String, String> files = transpile("a := 1");

            assertThat(PackagedLibrary.programFiles(files)).containsExactly("Solvik$Program.java");
            assertThat(files.keySet()).allSatisfy(name ->
                    assertThat(name).endsWith(".java"));
            assertThat(files.get("Solvik$Program.java"))
                    .contains("public final class Solvik$Program {");
        }

        @Test
        void output_is_deterministic() {
            String source = "a := 1\nTextWriter(Stdio.stdout()).writeLine((a) .. \"\")";
            assertThat(transpile(source)).isEqualTo(transpile(source));
        }

        @Test
        @DisplayName("type() dispatch names generic types with unbounded wildcards")
        void type_dispatch_avoids_raw_generic_types() {
            String java = transpile("""
                    type Box<T> {
                        mut value: T? = Null
                        pub func Box(value: T) { self.value = value }
                        pub func get() -> T? { return value }
                    }
                    type Pair<A, B> {
                        pub func Pair() { }
                    }
                    TextWriter(Stdio.stdout()).writeLine((Box(1).type()) .. "")
                    """).get("Solvik$Program.java");

            assertThat(java)
                    .contains("value instanceof Box<?> userType")
                    .contains("value instanceof Pair<?, ?> userType")
                    .doesNotContain("value instanceof Box userType")
                    .doesNotContain("value instanceof Pair userType");
        }

        @Test
        @DisplayName("the program class holds top-level statements in main and functions as methods")
        void separates_statements_from_functions() {
            String java = transpile("""
                    func twice(n: Integer) -> Integer {
                        return n * 2
                    }
                    x := twice(4)
                    """).get("Solvik$Program.java");

            assertThat(java).contains("public static void main(String[] args) {");
            assertThat(java).contains("twice(").contains("Integer n");
        }

        @Test
        void emits_java_constructs_that_match_the_settled_semantics() {
            String java = transpile("""
                    mut i := 1
                    d := 1.5
                    b := Byte(127)
                    mut s := "a" .. i
                    flag := i < d
                    bytes := Byte[2] {1, 2}
                    mut n := bytes.size
                    TextWriter(Stdio.stdout()).writeLine((s) .. (flag) .. (n))
                    """).get("Solvik$Program.java");

            assertThat(java)
                    .contains("Integer i = 1;")
                    .contains("Double d = 1.5d;")
                    .contains("Solvik$Program.toByte(")
                    .contains("String s =")
                    .contains("Objects.requireNonNull(i).doubleValue()"
                            + " < Objects.requireNonNull(d).doubleValue()")
                    .contains("Arrays.<Byte>asList((byte) 1, (byte) 2)")
                    .contains("Integer n = bytes.size();")
                    .contains("new TextWriter(Stdio.stdout()).writeLine(s + flag + n);")
                    .doesNotContain("Solvik$Program.println(");
        }

        @Test
        @DisplayName("braces follow K&R placement")
        void uses_kr_braces() {
            String java = transpile("""
                    if 1 < 2 {
                        TextWriter(Stdio.stdout()).writeLine(("a") .. "")
                    } else {
                        TextWriter(Stdio.stdout()).writeLine(("b") .. "")
                    }
                    while 1 < 2 {
                        TextWriter(Stdio.stdout()).writeLine(("c") .. "")
                    }
                    """).get("Solvik$Program.java");

            assertThat(java).contains("if (");
            assertThat(java).contains(") {\n");
            assertThat(java).contains("} else {\n");
            assertThat(java).doesNotContain(")\n{");
        }

        @Test
        void escapes_string_literals_for_java() {
            String java = transpile("TextWriter(Stdio.stdout()).writeLine((\"quote \\\" tab \\t\") .. \"\")").get("Solvik$Program.java");
            assertThat(java).contains("\"quote \\\" tab \\t\"");
        }
    }

    @Nested
    @DisplayName("compiled and executed output")
    class Execution {

        private String run(String source, Path directory) throws Exception {
            Path sourceDirectory = directory.resolve("src");
            Path classes = directory.resolve("classes");
            Files.createDirectories(sourceDirectory);
            Files.createDirectories(classes);

            Map<String, String> files = transpile(source);
            for (Map.Entry<String, String> entry : files.entrySet()) {
                Path $path = sourceDirectory.resolve(entry.getKey());
                Files.createDirectories($path.getParent());
                Files.writeString($path, entry.getValue(),
                        StandardCharsets.UTF_8);
            }

            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            List<String> arguments = Stream.concat(
                    Stream.of("-d", classes.toString()),
                    files.keySet().stream().map(name -> sourceDirectory.resolve(name).toString())
            ).toList();
            assertThat(compiler.run(null, null, null, arguments.toArray(String[]::new)))
                    .describedAs("javac on generated sources").isZero();

            String output = captureMainOutput(classes);
            deleteRecursively(directory);
            return output;
        }

        private String captureMainOutput(Path classes) throws Exception {
            var originalOut = System.out;
            var buffer = new java.io.ByteArrayOutputStream();
            try (URLClassLoader loader = new URLClassLoader(
                    new URL[]{classes.toUri().toURL()}, getClass().getClassLoader())) {
                System.setOut(new java.io.PrintStream(buffer, true, StandardCharsets.UTF_8));
                Class<?> program = loader.loadClass(GeneratedNames.PROGRAM_BINARY_NAME);
                program.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            } finally {
                System.setOut(originalOut);
            }
            return buffer.toString(StandardCharsets.UTF_8);
        }

        private void deleteRecursively(Path root) throws IOException {
            try (Stream<Path> paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // best effort cleanup of a temp directory
                    }
                });
            }
        }

        @Test
        @DisplayName("arithmetic, widening, division, and concatenation")
        void runs_arithmetic(@TempDir Path directory) throws Exception {
            String output = run("""
                    TextWriter(Stdio.stdout()).writeLine((1 + 2 * 3) .. "")
                    TextWriter(Stdio.stdout()).writeLine((1 + 1.5) .. "")
                    TextWriter(Stdio.stdout()).writeLine((10 / 3) .. "")
                    TextWriter(Stdio.stdout()).writeLine((10 % 3) .. "")
                    TextWriter(Stdio.stdout()).writeLine((-7 / 2) .. "")
                    TextWriter(Stdio.stdout()).writeLine(("n=" .. 4) .. "")
                    """, directory);

            assertThat(output).isEqualTo("7\n2.5\n3\n1\n-3\nn=4\n");
        }

        @Test
        @DisplayName("control flow, arrays, indexing, and size")
        void runs_control_flow_and_arrays(@TempDir Path directory) throws Exception {
            String output = run("""
                    mut total := 0
                    bytes := Byte[3] {10, 20, 30}
                    mut i := 0
                    while i < bytes.size {
                        total = total + bytes[i]
                        i = i + 1
                    }
                    TextWriter(Stdio.stdout()).writeLine((total) .. "")

                    for b in bytes {
                        TextWriter(Stdio.stdout()).write((b) .. (","))
                    }
                    TextWriter(Stdio.stdout()).writeLine("")

                    if total > 50 {
                        TextWriter(Stdio.stdout()).writeLine(("big") .. "")
                    } else if total > 10 {
                        TextWriter(Stdio.stdout()).writeLine(("medium") .. "")
                    } else {
                        TextWriter(Stdio.stdout()).writeLine(("small") .. "")
                    }
                    """, directory);

            assertThat(output).isEqualTo("60\n10,20,30,\nbig\n");
        }

        @Test
        @DisplayName("functions, Any parameters, type(), and null output")
        void runs_functions_and_dynamic_types(@TempDir Path directory) throws Exception {
            String output = run("""
                    func describe(value: Any) -> String {
                        return value.type() .. "=" .. value
                    }

                    TextWriter(Stdio.stdout()).writeLine((describe(42)) .. "")
                    TextWriter(Stdio.stdout()).writeLine((describe("text")) .. "")
                    TextWriter(Stdio.stdout()).writeLine((describe(true)) .. "")
                    TextWriter(Stdio.stdout()).writeLine((Null) .. "")
                    """, directory);

            assertThat(output).isEqualTo("Integer=42\nString=text\nBoolean=true\nnull\n");
        }

        @Test
        @DisplayName("a runtime failure aborts with a non-zero status (§10)")
        void aborts_on_division_by_zero(@TempDir Path directory) throws Exception {
            Path sourceDirectory = directory.resolve("src");
            Files.createDirectories(sourceDirectory);
            Files.createDirectories(directory.resolve("classes"));
            Map<String, String> files = transpile("""
                    zero := 0
                    TextWriter(Stdio.stdout()).writeLine((10 / zero) .. "")
                    """);
            for (Map.Entry<String, String> entry : files.entrySet()) {
                Path $path = sourceDirectory.resolve(entry.getKey());
                Files.createDirectories($path.getParent());
                Files.writeString($path, entry.getValue());
            }
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            // The generated program is a unit: its class names the packaged types it may hold, so
            // every emitted file compiles together, as the CLI compiles them.
            List<String> javacArguments = new ArrayList<>(List.of(
                    "-d", directory.resolve("classes").toString()));
            files.keySet().forEach(name -> javacArguments.add(sourceDirectory.resolve(name).toString()));
            assertThat(compiler.run(null, null, null,
                    javacArguments.toArray(String[]::new))).isZero();

            try (URLClassLoader loader = new URLClassLoader(
                    new URL[]{directory.resolve("classes").toUri().toURL()},
                    getClass().getClassLoader())) {
                Class<?> program = loader.loadClass(GeneratedNames.PROGRAM_BINARY_NAME);
                var main = program.getMethod("main", String[].class);
                assertThatThrownBy(() -> main.invoke(null, (Object) new String[0]))
                        .hasRootCauseInstanceOf(RuntimeException.class)
                        .hasRootCauseMessage("division by zero");
            }
        }
    }

    @Nested
    @DisplayName("diagnostics")
    class Diagnostics {

        @Test
        void reports_source_positions_for_compile_errors() {
            assertThatThrownBy(() -> transpile("a := 1 + \"x\""))
                    .isInstanceOf(SolvikCompiler.CompilationFailure.class)
                    .hasMessageContaining("test.sol:1:8")
                    .hasMessageContaining("SOL-T001");
        }

        @Test
        void rejects_a_name_that_starts_lowercase() {
            assertThatThrownBy(() -> transpile("type point { }"))
                    .hasMessageContaining("SOL-T003");
        }
    }
}
