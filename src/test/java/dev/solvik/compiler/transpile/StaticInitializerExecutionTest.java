package dev.solvik.compiler.transpile;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.SolvikCompiler;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.load.ProgramLoader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code static { ... }} blocks executed through generated Java ({@code LANGUAGE.md} §3.4).
 *
 * <p>The tests pin first-use laziness, source ordering with static fields, once-only execution, the
 * private-constructor use case, block-local scopes, and regex initialization order.
 */
@DisplayName("static initializer blocks executed")
class StaticInitializerExecutionTest {

    private static Map<String, String> transpile(String entry, Path directory) throws Exception {
        Path main = directory.resolve("main.sol");
        Files.createDirectories(directory);
        Files.writeString(main, entry, StandardCharsets.UTF_8);
        ProgramLoader.Loaded loaded = new ProgramLoader(new Diagnostics()).load(main);
        return new SolvikCompiler("main.sol").transpile(loaded);
    }

    private static String run(String entry, Path directory) throws Exception {
        Map<String, String> files = transpile(entry, directory);
        Path sources = Files.createDirectories(directory.resolve("src"));
        Path classes = Files.createDirectories(directory.resolve("classes"));
        List<String> arguments = new ArrayList<>(
                List.of("--release", "25", "-proc:none", "-d", classes.toString()));
        for (Map.Entry<String, String> file : new LinkedHashMap<>(files).entrySet()) {
            Path target = sources.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue(), StandardCharsets.UTF_8);
            arguments.add(target.toString());
        }
        assertThat(ToolProvider.getSystemJavaCompiler()
                .run(null, null, null, arguments.toArray(String[]::new))).isZero();

        PrintStream originalOut = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{classes.toUri().toURL()},
                StaticInitializerExecutionTest.class.getClassLoader())) {
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
    @DisplayName("a block runs once on first active use, not on a type-only mention")
    void first_active_use_only(@TempDir Path directory) throws Exception {
        String output = run("""
                type Log {
                    static pub mut text := ""
                }

                type LazyProbe {
                    static {
                        Log.text = Log.text .. "lazy"
                    }
                }

                type Ordered {
                    static mut trace := "a"
                    static pub first := trace
                    static {
                        trace = trace .. "b"
                    }
                    static pub func state() -> String {
                        return trace .. ":" .. first
                    }
                }

                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("before=" .. Log.text)
                unused: LazyProbe? = Null
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("afterMention=" .. Log.text)
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("ordered=" .. Ordered.state())
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("again=" .. Ordered.state())
                """, directory);

        assertThat(output).isEqualTo("""
                before=
                afterMention=
                ordered=ab:a
                again=ab:a
                """);
    }

    @Test
    @DisplayName("construction and static field access run the block before the use")
    void construction_and_field_access_trigger(@TempDir Path directory) throws Exception {
        String output = run("""
                type Constructed {
                    static mut ready := false
                    static {
                        ready = true
                    }
                    pub func Constructed() { }
                    pub func isReady() -> Boolean { return ready }
                }

                type FieldReady {
                    static pub mut value := 0
                    static {
                        value = 9
                    }
                }

                instance := Constructed()
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("constructed=" .. instance.isReady())
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("field=" .. FieldReady.value)
                """, directory);

        assertThat(output).isEqualTo("constructed=true\nfield=9\n");
    }

    @Test
    @DisplayName("fields initialize before the block, and functions run after it")
    void fields_then_block_then_functions(@TempDir Path directory) throws Exception {
        String output = run("""
                type Ordered {
                    static mut trace := "a"
                    static pub first := trace
                    static pub second := trace
                    static {
                        trace = trace .. "b"
                    }
                    static pub func state() -> String {
                        return first .. "|" .. second .. "|" .. trace
                    }
                }

                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("state=" .. Ordered.state())
                """, directory);

        assertThat(output).isEqualTo("state=a|a|ab\n");
    }

    @Test
    @DisplayName("nested scopes inside the block keep separate locals")
    void nested_block_scopes(@TempDir Path directory) throws Exception {
        String output = run("""
                type Scoped {
                    static mut total := 0
                    static {
                        {
                            mut value := 1
                            total = value
                        }
                        {
                            mut value := 2
                            total = total + value
                        }
                    }
                    static pub func now() -> Integer {
                        return total
                    }
                }

                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("total=" .. Scoped.now())
                """, directory);

        assertThat(output).isEqualTo("total=3\n");
    }

    @Test
    @DisplayName("a static block can build a private-constructor singleton")
    void private_constructor_singleton(@TempDir Path directory) throws Exception {
        String output = run("""
                type Config {
                    static mut instance: Config? = Null
                    pub mut name := ""
                    static {
                        instance = Config("configured")
                    }
                    func Config(name: String) {
                        self.name = name
                    }
                    static pub func current() -> Config {
                        value := instance
                        if value == Null {
                            raise Exception(1, "static config was not initialized")
                        }
                        return value
                    }
                }

                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("singleton=" .. Config.current().name)
                """, directory);

        assertThat(output).isEqualTo("singleton=configured\n");
    }

    @Test
    @DisplayName("a regex switch works from a static block")
    void regex_switch_in_static_block(@TempDir Path directory) throws Exception {
        String output = run("""
                type Codes {
                    static mut code := 0
                    static {
                        switch "ERR-7" {
                            case regex "ERR-[0-9]+" { code = 7 }
                            default { code = -1 }
                        }
                    }
                    static pub func current() -> Integer {
                        return code
                    }
                }

                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("code=" .. Codes.current())
                """, directory);

        assertThat(output).isEqualTo("code=7\n");
    }

    @Test
    @DisplayName("a block that reads a later field sees its Java default")
    void later_fields_hold_defaults(@TempDir Path directory) throws Exception {
        String output = run("""
                type Forward {
                    static mut observed := ""
                    static {
                        observed = String(later)
                    }
                    static pub later := 5
                    static pub func state() -> String {
                        return observed
                    }
                }

                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("forward=" .. Forward.state())
                """, directory);

        assertThat(output).isEqualTo("forward=null\n");
    }

    @Test
    @DisplayName("the generated class renders a static block that calls its initializer method")
    void generated_shape(@TempDir Path directory) throws Exception {
        Map<String, String> files = transpile("""
                type Init {
                    static mut ready := false
                    static {
                        ready = true
                    }
                    static pub func isReady() -> Boolean {
                        return ready
                    }
                }

                value := Init.isReady()
                """, directory);

        assertThat(files.get("Init.java"))
                .contains("static {")
                .contains("Init.$solvikStaticInit0();")
                .contains("private static void $solvikStaticInit0()")
                .doesNotContain("System.out");
    }
}
