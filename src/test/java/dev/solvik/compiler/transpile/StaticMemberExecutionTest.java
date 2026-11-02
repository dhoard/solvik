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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Static type members execute identically through both declaration backends. */
class StaticMemberExecutionTest {

    private static Map<String, String> transpile(String source) {
            return new SolvikCompiler("static.sol").transpile(source);
    }

    private static String output(String source, Path directory) throws Exception {
        Map<String, String> files = transpile(source);
        Path sources = Files.createDirectories(directory.resolve("src"));
        Path classes = Files.createDirectories(directory.resolve("classes"));
        Path emptyClasspath = Files.createDirectories(directory.resolve("empty-classpath"));
        List<String> arguments = new ArrayList<>(List.of("--release", "25", "-classpath",
                emptyClasspath.toString(), "-d", classes.toString()));
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path path = sources.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, file.getValue(), StandardCharsets.UTF_8);
            arguments.add(path.toString());
        }
        assertThat(ToolProvider.getSystemJavaCompiler()
                .run(null, null, null, arguments.toArray(String[]::new))).isZero();

        PrintStream originalOut = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (URLClassLoader loader = new URLClassLoader(new URL[]{classes.toUri().toURL()}, null);
                PrintStream captured = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            System.setOut(captured);
            loader.loadClass(GeneratedNames.PROGRAM_BINARY_NAME)
                    .getMethod("main", String[].class)
                    .invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(originalOut);
        }
        return buffer.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    @Test
    void static_state_is_shared_and_bare_and_type_access_agree(@TempDir Path directory) throws Exception {
        String output = output("""
                type Counter {
                    static mut total := 0
                    static label := "count"
                    static pub kind := "counter"
                    static pub mut limit := 10

                    pub func Counter() { }

                    pub func add(amount: Integer) -> Integer {
                        total = total + amount
                        return total
                    }

                    static pub func reset() {
                        total = 0
                    }

                    static pub func report() -> String {
                        return label .. "=" .. total
                    }
                }

                first := Counter()
                second := Counter()
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((first.add(1)) .. (" ") .. (second.add(2)) .. (" ") .. (Counter.report()))
                Counter.reset()
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Counter.report()) .. "")
                Counter.limit = 5
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Counter.kind) .. (Counter.limit))
                """, directory);

        assertThat(output).isEqualTo("1 3 count=3\ncount=0\ncounter5\n");
    }

    @Test
    void module_qualified_static_access_and_private_static_helpers_work(@TempDir Path directory) throws Exception {
        String output = output("""
                module stats {
                    pub type Counter {
                        static mut total := 0
                        static prefix := "stats:"

                        pub func Counter() { }

                        pub func add(amount: Integer) -> Integer {
                            total = total + amount
                            return total
                        }

                        static pub func reset() {
                            total = 0
                        }

                        static pub func label() -> String {
                            return prefix .. total
                        }
                    }
                }

                counter := stats::Counter()
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((counter.add(2)) .. (" ") .. (counter.add(3)) .. (" ") .. (stats::Counter.label()))
                stats::Counter.reset()
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((stats::Counter.label()) .. "")
                """, directory);

        assertThat(output).isEqualTo("2 5 stats:5\nstats:0\n");
    }

    @Test
    void static_field_initializers_run_in_source_order(@TempDir Path directory)
            throws Exception {
        String output = output("""
                type Settings {
                    static base := 10
                    static pub doubled := base + base
                    pub func Settings() { }
                }

                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Settings.doubled) .. "")
                """, directory);

        assertThat(output).isEqualTo("20\n");
    }

    @Test
    void a_static_factory_can_use_its_private_constructor(@TempDir Path directory)
            throws Exception {
        String output = output("""
                type Node {
                    value := 0

                    func Node(value: Integer) {
                        self.value = value
                    }

                    static pub func of(value: Integer) -> Node {
                        return Node(value)
                    }

                    pub func get() -> Integer {
                        return value
                    }
                }

                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Node.of(7).get()) .. "")
                """, directory);

        assertThat(output).isEqualTo("7\n");
    }

    @Test
    void a_static_string_field_appends_through_the_type(@TempDir Path directory)
            throws Exception {
        String output = output("""
                type Log {
                    static pub mut text := "a"
                    pub func Log() { }
                }

                Log.text ..= "-b"
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Log.text) .. "")
                """, directory);

        assertThat(output).isEqualTo("a-b\n");
    }

    @Test
    void static_functions_with_an_object_contract_name_keep_their_source_name(@TempDir Path directory) throws Exception {
        String output = output("""
                type Format {
                    pub func Format() { }
                    static pub func toString() -> String { return "static" }
                }

                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Format.toString()) .. "")
                """, directory);

        assertThat(output).isEqualTo("static\n");
    }

    @Test
    void a_static_only_java_binding_runs(@TempDir Path directory) throws Exception {
        String output = output("""
                module jdk {
                    pub type Math native "java.lang.Math" {
                        static pub func abs(value: Integer) -> Integer
                        static pub func max(a: Integer, b: Integer) -> Integer
                        static pub func sqrt(value: Double) -> Double
                        static pub func floor(value: Double) -> Double
                    }
                }

                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((jdk::Math.abs(-7)) .. (" ") .. (jdk::Math.max(3, 9)) .. (" ") .. (jdk::Math.sqrt(81.0)) .. (" ") .. (jdk::Math.floor(2.9)))
                """, directory);

        assertThat(output).isEqualTo("7 9 9.0 2.0\n");
    }

    @Test
    void a_native_binding_can_mix_static_and_instance_methods(@TempDir Path directory) throws Exception {
        String output = output("""
                module jdk {
                    pub type Text native "java.lang.String" {
                        pub func Text()
                        pub func length() -> Integer
                        static pub func valueOf(value: Integer) -> String
                    }
                }

                text := jdk::Text()
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((jdk::Text.valueOf(123)) .. (" ") .. (text.length()))
                """, directory);

        assertThat(output).isEqualTo("123 0\n");
    }
}
