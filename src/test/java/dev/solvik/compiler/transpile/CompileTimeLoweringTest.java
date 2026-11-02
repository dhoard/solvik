package dev.solvik.compiler.transpile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.solvik.compiler.SolvikCompiler;
import dev.solvik.compiler.check.TypeChecker;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
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

/** Source-shape and behavior contracts for direct, JDK-only Java lowering. */
class CompileTimeLoweringTest {

    private static Map<String, String> transpile(String source) {
            return new SolvikCompiler("lowering.sol").transpile(source);
    }

    private record Outcome(String stdout, String stderr, Throwable failure) { }

    private static Outcome run(String source, Path directory) throws Exception {
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
        PrintStream originalErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        Throwable failure = null;
        // No compiler or third-party classes are visible to the generated program.
        try (URLClassLoader loader = new URLClassLoader(new URL[]{classes.toUri().toURL()}, null);
                PrintStream output = new PrintStream(out, true, StandardCharsets.UTF_8);
                PrintStream errors = new PrintStream(err, true, StandardCharsets.UTF_8)) {
            System.setOut(output);
            System.setErr(errors);
            try {
                loader.loadClass(GeneratedNames.PROGRAM_BINARY_NAME).getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            } catch (InvocationTargetException exception) {
                failure = exception.getCause();
            } finally {
                System.setOut(originalOut);
                System.setErr(originalErr);
            }
        }
        return new Outcome(out.toString(StandardCharsets.UTF_8),
                err.toString(StandardCharsets.UTF_8), failure);
    }

    @Test
    void emits_direct_text_writer_calls_without_output_builtins() {
        String java = transpile("""
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).write(("hello") .. "")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("world") .. "")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).write("")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("value=") .. (1))
                """).get("Solvik$Program.java");

        assertThat(java).contains(
                        "new TextWriter(Stdio.stdout()).write(\"hello\" + \"\");",
                        "new TextWriter(Stdio.stdout()).writeLine(\"world\" + \"\");",
                        "new TextWriter(Stdio.stdout()).writeLine(\"\");",
                        "new TextWriter(Stdio.stdout()).write(\"\");",
                        "new TextWriter(Stdio.stdout()).writeLine(\"value=\" + 1);")
                .doesNotContain("System.out.print", "Solvik$Program.print(",
                        "Solvik$Program.println(", "static void print(", "static void println(",
                        "System.out.println(", "static String toStringOf(", "new StringBuilder()");
    }

    @Test
    void lowers_typed_arithmetic_rendering_and_safe_equality() {
        String java = transpile("""
                mut i := 2
                d := 1.5
                b := Byte(3)
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((-i) .. (-d) .. (b + i) .. (i + d) .. (i < d))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((String(d)) .. (i.toString()) .. (Integer(i)) .. (Integer(b)) .. (BigDecimal(i)) .. (BigDecimal(d)))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("n=" .. i .. d) .. "")
                array := Integer[1] {1}
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((array == array) .. (array != array) .. (array === array) .. (array.equals(array)))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((i === i) .. ("a" == "a") .. (true == false) .. ("a".equals("a")))
                """).get("Solvik$Program.java");
        int support = java.indexOf("\n    public static String render(");
        String body = java.substring(0, support < 0 ? java.length() : support);

        assertThat(body).contains("Objects.requireNonNull(", "-Objects.requireNonNull(",
                        "Solvik$Program.plainText(", "String.valueOf(")
                .doesNotContain("Solvik$Program.toStringOf(", "Solvik$Program.render(",
                        "Solvik$Program.equal(", "Solvik$Program.identity(", "Solvik$Program.concat(");
    }

    @Test
    void preserves_output_formatting_and_nulls(@TempDir Path directory)
            throws Exception {
        Outcome outcome = run("""
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).write("")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).write(("a") .. "")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).write(("b") .. ("c"))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Null) .. "")
                mut decimal: BigDecimal? = BigDecimal("1e3")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((decimal) .. (" ") .. (BigDecimal("1.50")) .. (" ") .. (BigDecimal("1e-3")))
                decimal = Null
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((decimal) .. (" ") .. (String(decimal)))
                items := BigDecimal?[2] {BigDecimal("1e3"), Null}
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((items) .. "")
                func show(value: Any) { solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((value) .. "") }
                func number(value: Number) { solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((value) .. "") }
                show(items)
                number(BigDecimal("1e3"))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((BigDecimal?[][1] {items}) .. "")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Byte(7)) .. (" ") .. (true) .. (" ") .. (4))
                """, directory);

        assertThat(outcome.failure()).isNull();
        assertThat(outcome.stderr()).isEmpty();
        assertThat(outcome.stdout()).isEqualTo("""

                abcnull
                1000 1.50 0.001
                null null
                [1000, null]
                [1000, null]
                1000
                [[1000, null]]
                7 true 4
                """);
    }

    @Test
    void evaluates_all_arguments_once_before_rendering(@TempDir Path directory)
            throws Exception {
        Outcome outcome = run("""
                type Item {
                    pub mut label := "old"
                    pub func Item() { }
                    pub func toString() -> String {
                        solvik::io::TextWriter(solvik::io::Stdio.stdout()).write(("<render>") .. "")
                        return self.label
                    }
                }
                func update(item: Item) -> String {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).write(("<arg>") .. "")
                    item.label = "new"
                    return "!"
                }
                item := Item()
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((item) .. (update(item)))
                item.label = "old"
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((item .. update(item)) .. "")
                func change(items: Integer[]) -> String {
                    items[0] = 2
                    return "!"
                }
                items := Integer[1] {1}
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((items) .. (change(items)))
                """, directory);

        assertThat(outcome.failure()).isNull();
        assertThat(outcome.stderr()).isEmpty();
        assertThat(outcome.stdout()).isEqualTo("<arg><render>new!\n<arg><render>new!\n[2]!\n");
    }

    @Test
    void renders_computed_decimals_once(@TempDir Path directory) throws Exception {
        Outcome outcome = run("""
                func next() -> BigDecimal {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).write(("called:") .. "")
                    return BigDecimal("1e3")
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((next()) .. "")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((String(next())) .. "")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((next().toString()) .. "")
                """, directory);

        assertThat(outcome.failure()).isNull();
        assertThat(outcome.stderr()).isEmpty();
        assertThat(outcome.stdout()).isEqualTo("called:1000\ncalled:1000\ncalled:1000\n");
    }

    @Test
    void lowers_output_in_modules_generic_types_and_loops(@TempDir Path directory)
            throws Exception {
        Outcome outcome = run("""
                module output {
                    pub func write(value: Any) {
                        solvik::io::TextWriter(solvik::io::Stdio.stdout()).write(("module=") .. (value))
                        solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("")
                    }
                }
                type Box<T> {
                    mut value: T? = Null
                    pub func Box(seed: T) { self.value = seed }
                    pub func write() { solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("box=") .. (self.value)) }
                }
                for item in Integer[2] {1, 2} {
                    output::write(item)
                }
                Box(BigDecimal("1e3")).write()
                """, directory);

        assertThat(outcome.failure()).isNull();
        assertThat(outcome.stderr()).isEmpty();
        assertThat(outcome.stdout()).isEqualTo("module=1\nmodule=2\nbox=1000\n");
    }

    @Test
    void preserves_numeric_widening_and_nullable_scalar_equality(
            @TempDir Path directory) throws Exception {
        Outcome outcome = run("""
                b := Byte(127)
                mut i: Integer? = 2
                d := BigDecimal("1.50")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Integer(b)) .. (" ") .. (BigDecimal(b)) .. (" ") .. (BigDecimal(d)))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Integer(2)) .. (" ") .. (BigDecimal(2)))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((b + b) .. (" ") .. (BigDecimal(b) + d) .. (" ") .. (-b) .. (" ") .. (BigDecimal(i) * d))
                mut s: String? = "text"
                mut flag: Boolean? = true
                i = Null
                s = Null
                flag = Null
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((i == i) .. (" ") .. (i === i) .. (" ") .. (s == s) .. (" ") .. (flag == flag))
                """, directory);

        assertThat(outcome.failure()).isNull();
        assertThat(outcome.stderr()).isEmpty();
        assertThat(outcome.stdout()).isEqualTo("""
                127 127 1.50
                2 2
                254 128.50 -127 3.00
                true true true true
                """);
    }

    @Test
    void rendering_failure_does_not_write_partial_output(@TempDir Path directory)
            throws Exception {
        Outcome outcome = run("""
                type Broken {
                    pub func Broken() { }
                    pub func toString() -> String {
                        divisor := 0
                        return String(1 / divisor)
                    }
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("not written") .. (Broken()))
                """, directory);

        assertThat(outcome.failure()).isNotNull();
        assertThat(outcome.stdout()).isEmpty();
        assertThat(outcome.stderr())
                .isEqualTo("solvik: runtime error [SOL-R001]: division by zero\n");
    }

    @Test
    void a_nullable_numeric_operand_is_rejected_before_it_can_be_evaluated() {
        assertThatThrownBy(() -> transpile("""
                func rhs() -> Integer {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).write(("not evaluated") .. "")
                    return 1
                }
                mut value: Integer? = 1
                value = Null
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((value + rhs()) .. "")
                """)).isInstanceOf(SolvikCompiler.CompilationFailure.class)
                .hasMessageContaining(TypeChecker.NULL_UNSAFE);
    }

    @Test
    void argument_failure_does_not_write_partial_output(@TempDir Path directory)
            throws Exception {
        Outcome outcome = run("""
                type Loud {
                    pub func Loud() { }
                    pub func toString() -> String {
                        solvik::io::TextWriter(solvik::io::Stdio.stdout()).write(("not rendered") .. "")
                        return "not written"
                    }
                }
                divisor := 0
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Loud()) .. (1 / divisor))
                """, directory);

        assertThat(outcome.failure()).isNotNull();
        assertThat(outcome.stdout()).isEmpty();
        assertThat(outcome.stderr())
                .isEqualTo("solvik: runtime error [SOL-R001]: division by zero\n");
    }
}
