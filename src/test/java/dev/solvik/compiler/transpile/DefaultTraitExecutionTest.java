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

/** End-to-end default trait dispatch through both Java declaration backends. */
class DefaultTraitExecutionTest {

    private static Map<String, String> transpile(String source) {
            return new SolvikCompiler("defaults.sol").transpile(source);
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
    void defaults_dispatch_through_concrete_and_trait_types(@TempDir Path directory) throws Exception {
        String source = """
                trait Named {
                    pub func name() -> String
                    pub func label() -> String { return "name=" .. name() }
                    pub func empty() { }
                }
                trait NeedsLabel { pub func label() -> String }
                trait Formatted {
                    pub func format(value: Integer) -> String { return "int=" .. value }
                    pub func format(value: String) -> String { return "text=" .. value }
                    pub func classify(value: String) -> String {
                        switch value {
                            case regex "[0-9]+" { return "digits" }
                            default { return "other" }
                        }
                    }
                }
                type Plain implements Named, NeedsLabel, Formatted {
                    pub func Plain() { }
                    pub func name() -> String { return "plain" }
                }
                type Custom implements Named {
                    pub func Custom() { }
                    pub func name() -> String { return "custom" }
                    pub func label() -> String { return "override" }
                }
                func show(value: Named) -> String { return value.label() }

                plain := Plain()
                plain.empty()
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((plain.label()) .. (" ") .. (show(plain)) .. (" ") .. (plain.format(7)) .. (" ") .. (plain.format("x")) .. (" ") .. (plain.classify("42")) .. (" ") .. (show(Custom())))
                """;
        Map<String, String> files = transpile(source);
        assertThat(files.get("Named.java"))
                .contains("default String label()", "default void empty()",
                        "public static String $default$label$0(Named $solvikSelf)",
                        "return Named.$default$label$0(this);");
        assertThat(files.get("Formatted.java"))
                .contains("default String classify(String value)",
                        "public static final Pattern $solvikCase0");
        assertThat(files.get("Plain.java")).contains("Named.$default$label$0(this)");
        assertThat(output(source, directory))
                .isEqualTo("name=plain name=plain int=7 text=x digits override\n");
    }

    @Test
    void native_traits_can_add_source_defaults(@TempDir Path directory)
            throws Exception {
        String source = """
                trait FriendlyCloseable native "java.lang.AutoCloseable" {
                    pub func close()
                    pub func description() -> String { return "friendly" }
                }
                type Job implements FriendlyCloseable {
                    pub func Job() { }
                    pub func close() { }
                    pub func description() -> String {
                        return "wrapped " .. FriendlyCloseable.description(self)
                    }
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Job().description()) .. "")
                """;
        Map<String, String> files = transpile(source);
        assertThat(files.get("FriendlyCloseable.java"))
                .contains("extends AutoCloseable", "default String description()");
        assertThat(files.get("Job.java")).contains("FriendlyCloseable.$default$description$0(this)");
        assertThat(output(source, directory)).isEqualTo("wrapped friendly\n");
    }

    @Test
    void an_override_can_reuse_and_compose_explicit_trait_defaults(@TempDir Path directory) throws Exception {
        String source = """
                trait Retry {
                    pub func attempts() -> Integer
                    pub func attemptOnce() -> Boolean
                    pub func delayMs(attempt: Integer) -> Long {
                        return 100L * Long(attempt)
                    }
                }
                trait MinimumDelay {
                    pub func delayMs(attempt: Integer) -> Long { return 25L }
                }
                type Backoff implements Retry {
                    pub func Backoff() { }
                    pub func attempts() -> Integer { return 3 }
                    pub func attemptOnce() -> Boolean { return true }
                    pub func delayMs(attempt: Integer) -> Long {
                        return 2L * Retry.delayMs(self, attempt)
                    }
                }
                type Combined implements Retry, MinimumDelay {
                    pub func Combined() { }
                    pub func attempts() -> Integer { return 1 }
                    pub func attemptOnce() -> Boolean { return true }
                    pub func delayMs(attempt: Integer) -> Long {
                        return Retry.delayMs(self, attempt) + MinimumDelay.delayMs(self, attempt)
                    }
                }
                func delay(retry: Retry) -> Long { return retry.delayMs(2) }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Backoff().delayMs(2)) .. (" ") .. (delay(Backoff())) .. (" ") .. (Combined().delayMs(2)))
                """;
        Map<String, String> files = transpile(source);
        assertThat(files.get("Backoff.java")).contains("Retry.$default$delayMs$0(this, attempt)");
        assertThat(files.get("Combined.java"))
                .contains("Retry.$default$delayMs$0(this, attempt)",
                        "MinimumDelay.$default$delayMs$0(this, attempt)");
        assertThat(files.get("Retry.java"))
                .contains("default Long delayMs(Integer attempt)",
                        "return Retry.$default$delayMs$0(this, attempt);",
                        "public static Long $default$delayMs$0(Retry $solvikSelf, Integer attempt)");
        assertThat(output(source, directory)).isEqualTo("400 400 225\n");
    }

    @Test
    void an_explicit_default_call_names_the_checked_overload(@TempDir Path directory) throws Exception {
        String source = """
                trait Pick {
                    pub func pick(value: String?) -> String { return "string" }
                    pub func pick(value: Any?) -> String { return "any" }
                }
                type Chooser implements Pick {
                    pub func Chooser() { }
                    pub func pick(value: String) -> String { return "override" }
                    pub func describe() -> String {
                        return Pick.pick(self, Null) .. "/" .. Pick.pick(self, "x")
                    }
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Chooser().describe()) .. (" ") .. (Chooser().pick("y")))
                """;
        Map<String, String> files = transpile(source);
        assertThat(files.get("Pick.java"))
                .contains("public static String $default$pick$0(Pick $solvikSelf, String value)",
                        "public static String $default$pick$1(Pick $solvikSelf, Object value)");
        assertThat(files.get("Chooser.java"))
                .contains("Pick.$default$pick$0(this, (String) null)",
                        "Pick.$default$pick$0(this, \"x\")");
        assertThat(output(source, directory)).isEqualTo("string/string override\n");
    }

    @Test
    void inherited_signatures_resolve_in_the_trait_module(@TempDir Path directory) throws Exception {
        String source = """
                module contracts {
                    pub type Word {
                        pub text := ""
                        pub func Word(text: String) { self.text = text }
                    }
                    pub trait Echo {
                        pub func echo(value: Word) -> Word { return value }
                    }
                }
                type Echoer implements contracts::Echo {
                    pub func Echoer() { }
                    pub func echo(value: contracts::Word) -> contracts::Word {
                        return contracts::Echo.echo(self, value)
                    }
                }
                word := contracts::Word("module")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Echoer().echo(word).text) .. "")
                """;
        assertThat(output(source, directory)).isEqualTo("module\n");
    }
}
