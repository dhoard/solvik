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

/** Both declaration backends must implement the same language contracts, using the JDK alone. */
class LanguageConsistencyExecutionTest {
    private static Map<String, String> transpile(String source) {
            return new SolvikCompiler("consistency.sol").transpile(source);
    }

    private record Outcome(String stdout, String stderr, Throwable failure) { }

    private static Outcome run(String source, Path directory) throws Exception {
        Map<String, String> files = transpile(source);
        Path sources = Files.createDirectories(directory.resolve("src"));
        Path classes = Files.createDirectories(directory.resolve("classes"));
        Path emptyClasspath = Files.createDirectories(directory.resolve("empty-classpath"));
        List<String> arguments = new ArrayList<>(List.of("--release", "25", "-Xlint:all",
                "-Werror", "-classpath", emptyClasspath.toString(), "-d", classes.toString()));
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

    private static void output(String source, String expected, Path directory)
            throws Exception {
        Outcome result = run(source, directory);
        assertThat(result.failure()).isNull();
        assertThat(result.stderr()).isEmpty();
        assertThat(result.stdout()).isEqualTo(expected);
    }

    @Test
    void statement_continuation_markers_follow_the_documented_forms(@TempDir Path directory)
            throws Exception {
        // LANGUAGE.md §1: a leading `\` cancels the preceding newline, blank lines and comments
        // may precede it, and one marker continues across every newline since the last line.
        output("""
                message := "a"
                \\ .. "b"
                \\ .. "c"
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((message) .. "")

                mut total := 0
                total = 1
                \\ + 2
                \\ + 3
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((total) .. "")

                greeting := "hello"
                // a comment line, a blank line, a marker-only line, then the rest

                \\
                \\ .. " "
                \\ .. "world"
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((greeting) .. "")
                """, "abc\n6\nhello world\n", directory);
    }

    @Test
    void explicit_equals_is_boxed_regardless_of_static_type(@TempDir Path directory)
            throws Exception {
        output("""
                func boxed(x: Any, y: Any) -> Boolean { return x.equals(y) }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((1.equals(1.0)) .. (" ") .. (Float("NaN").equals(Float("NaN"))) .. (" ") .. (Double("-0.0").equals(0.0)))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((boxed(1, 1.0)) .. (" ") .. (boxed(Float("NaN"), Float("NaN"))) .. (" ") .. (boxed(Double("-0.0"), 0.0)))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((1 == 1.0) .. (" ") .. (1 === 1.0))
                left := Integer[1] {1}
                right := Integer[1] {1}
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((left.equals(left)) .. (" ") .. (left.equals(right)) .. (" ") .. (boxed(left, right)))
                mut empty: Integer? = 1
                empty = Null
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((empty == Null) .. (" ") .. (empty == 1) .. (" ") .. (Null == Null))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((BigDecimal("1.0").equals(BigDecimal("1.00"))) .. "")
                """, "false true false\nfalse true false\ntrue false\ntrue false false\ntrue false true\nfalse\n",
                directory);
    }

    @Test
    void equality_uses_the_promoted_float_carrier(@TempDir Path directory)
            throws Exception {
        output("""
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Float(16777216) == 16777217) .. (" ") .. (Float(16777216) >= 16777217) .. (" ") .. (Float(16777216) != 16777217))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Float("9007199254740992") == Long("9007199254740993")) .. "")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Double(16777216) == 16777217) .. "")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Float("NaN") == Float("NaN")) .. (" ") .. (Float("-0.0") == Float("0.0")))
                mut missing: Float? = Float(1)
                missing = Null
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((missing == Null) .. (" ") .. (missing == Float(1)) .. (" ") .. (missing == missing))
                func next(state: Integer[], value: Integer) -> Float {
                    state[0] = state[0] + 1
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).write((value) .. "")
                    return Float(value)
                }
                state := Integer[1] {0}
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((next(state, 1) == next(state, 2)) .. (" ") .. (state[0]))
                """, "true true false\ntrue\nfalse\nfalse true\ntrue false true\n12false 2\n", directory);
    }

    @Test
    void object_contract_overrides_work_without_changing_operator_identity(
            @TempDir Path directory) throws Exception {
        output("""
                type Item<T> {
                    pub func Item(seed: T) {}
                    pub func equals(other: Any?) -> Boolean { solvik::io::TextWriter(solvik::io::Stdio.stdout()).write(("equals:") .. ""); return true }
                    pub func hashCode() -> Integer { return 17 }
                    pub func toString() -> String { return "item" }
                }
                func same(x: Any, y: Any) -> Boolean { return x == y }
                func boxed(x: Any, y: Any) -> Boolean { return x.equals(y) }
                a := Item(1)
                b := Item(2)
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((a == b) .. (" ") .. (a === b) .. (" ") .. (a == a) .. (" ") .. (same(a, b)))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((a.equals(b)) .. (" ") .. (boxed(a, b)) .. (" ") .. (a.equals(a)) .. (" ") .. (a.hashCode()) .. (" ") .. (a))
                func hashCode() -> Integer? { return Null }
                func equals(value: Any?) -> Boolean? { return Null }
                module names { pub func toString() -> String? { return Null } }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((hashCode()) .. (" ") .. (equals(1)) .. (" ") .. (names::toString()))
                """, "false false true false\nequals:equals:equals:true true true 17 item\nnull null null\n", directory);
    }

    @Test
    void equals_checks_and_casts_its_peer_before_member_comparison(
            @TempDir Path directory) throws Exception {
        output("""
                type Box<T> {
                    mut value: T? = Null

                    pub func Box(value: T) {
                        self.value = value
                    }

                    func has(value: T?) -> Boolean {
                        mut mine: T? = self.value
                        if mine == Null {
                            return value == Null
                        }
                        return mine.equals(value)
                    }

                    pub func equals(other: Any?) -> Boolean {
                        solvik::io::TextWriter(solvik::io::Stdio.stdout()).write(("body:") .. "")
                        return other.has(self.value) && self.has(other.value)
                    }
                }
                type Other { pub func Other() {} }
                one := Box(1)
                same := Box(1)
                different := Box(2)
                other := Other()
                func stop() { exit(0) } // emits the serializable Exit support without invoking it
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((one == other) .. (" ") .. (one.equals(other)))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((one.equals(same)) .. (" ") .. (one.equals(different)) .. (" ") .. (one.equals("not a box")) .. (" ") .. (one.equals(Null)))
                """, "false false\nbody:body:true false false false\n", directory);
    }

    @Test
    void null_is_rejected_by_conversion_and_by_the_object_contract_signatures() {
        assertThatThrownBy(() -> transpile("""
                func missing() -> Boolean? { return Null }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Boolean(missing())) .. "")
                """)).isInstanceOf(SolvikCompiler.CompilationFailure.class)
                .hasMessageContaining(TypeChecker.TYPE_ERROR);

        for (String method : new String[]{"hashCode", "equals"}) {
            String declaration = "hashCode".equals(method)
                    ? "pub func hashCode() -> Integer? { return Null }"
                    : "pub func equals(other: Any?) -> Boolean? { return Null }";
            assertThatThrownBy(() -> transpile("type Item { pub func Item() {}\n" + declaration
                    + " }\nsolvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Item()." + method
                    + "(Item())) .. \"\")"))
                    .isInstanceOf(SolvikCompiler.CompilationFailure.class)
                    .hasMessageContaining(TypeChecker.TYPE_ERROR);
        }
    }

    @Test
    void native_contract_equality_does_not_leak_into_operator_identity(
            @TempDir Path directory) throws Exception {
        output("""
                type Text native "java.lang.String" {
                    pub func Text()
                    pub func equals(other: Any?) -> Boolean
                    pub func hashCode() -> Integer
                }
                func same(x: Any, y: Any) -> Boolean { return x == y }
                a := Text()
                b := Text()
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((a.equals("")) .. (" ") .. (a.hashCode()) .. (" ") .. (a == b) .. (" ") .. (a === b) .. (" ") .. (same(a, "")))
                """, "true 0 false false false\n", directory);
    }

    @Test
    void numeric_array_literals_use_context_for_sign_and_range(@TempDir Path directory)
            throws Exception {
        output("""
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Byte[3] {-128, -1, 127}) .. "")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Short[3] {-32768, -1, 32767}) .. "")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Integer[2] {-2147483648, 2147483647}) .. "")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Long[3] {-9223372036854775808, -2147483648, 9223372036854775807}) .. "")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((BigInteger[2] {-999999999999999999999, 999999999999999999999}) .. "")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((BigDecimal[2] {-999999999999999999999, -1.50}) .. "")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Float[2] {-1.5, -0.0}) .. "")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Double[2] {-1.5, -0.0}) .. "")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Byte?[2] {- -1}) .. "")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Long[][1] {Long[1] {2147483648}}) .. "")
                """, """
                [-128, -1, 127]
                [-32768, -1, 32767]
                [-2147483648, 2147483647]
                [-9223372036854775808, -2147483648, 9223372036854775807]
                [-999999999999999999999, 999999999999999999999]
                [-999999999999999999999, -1.50]
                [-1.5, -0.0]
                [-1.5, -0.0]
                [1, null]
                [[2147483648]]
                """, directory);
    }

    @Test
    void signed_byte_switches_match_negative_and_positive_limits(
            @TempDir Path directory) throws Exception {
        output("""
                func describe(value: Byte) {
                    switch value {
                        case -128 { solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("min") .. "") }
                        case -1 { solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("negative") .. "") }
                        case 127 { solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("max") .. "") }
                        default { solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("other") .. "") }
                    }
                }
                describe(Byte(-128))
                describe(Byte(-1))
                describe(Byte(127))
                describe(Byte(0))
                """, "min\nnegative\nmax\nother\n", directory);
    }

    @Test
    void boolean_literals_short_indexes_and_field_append_work(
            @TempDir Path directory) throws Exception {
        output("""
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Boolean(true)) .. (" ") .. (Boolean(false)))
                values := Integer[2] {1, 2}
                values[Short(0)] = 3
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((values[Short(0)]) .. (" ") .. (values[Byte(1)]))
                type Item { pub mut text := "a"
                    pub func Item() {} }
                item := Item()
                item.text ..= values[Short(0)]
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((item.text) .. "")
                """, "true false\n3 2\na3\n", directory);
    }
}
