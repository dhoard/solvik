package dev.solvik.compiler.transpile;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.SolvikCompiler;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The shape of the Java a Solvik program gets.
 *
 * <p>These are contracts about the generated code, not only about its behavior: a program pays for
 * the support code it actually reaches, an array access is a direct Java operation, and output is
 * one Java write of one concatenation. Every source-shape case runs for both declaration backends,
 * and the behavioral cases compile the generated Java with an empty dependency classpath, so a
 * pruned helper or a lowered operation cannot silently break the JDK-only rule.
 */
@DisplayName("generated Java output")
class GeneratedOutputOptimizationTest {

    private static Map<String, String> transpile(String source) {
            return new SolvikCompiler("output.sol").transpile(source);
    }

    private static String program(String source) {
        return transpile(source).get("Solvik$Program.java");
    }

    /** The generated {@code main} body alone, before the support block the library brings along. */
    private static String mainBody(String programClass) {
        int main = programClass.indexOf("public static void main");
        int support = programClass.indexOf("Support routines");
        return programClass.substring(main, support);
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
                .run(null, null, null, arguments.toArray(String[]::new)))
                .describedAs("javac on the generated sources").isZero();

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

    // ------------------------------------------------------- support code selection

    @DisplayName("a program that needs no support code adds none of its own")
    void emits_no_support_code_for_a_program_that_needs_none() {
        String minimal = program("""
                mut total := 1 + 2
                total = total * 3
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("total=") .. (total))
                """);
        String reachingMore = program("""
                items := Integer[3] {1, 2}
                count := 2
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((items[0]) .. (count / count))
                exit(0)
                """);

        // The packaged library is part of every program (§11), so the support members it reaches are
        // always present. What a program must not do is reach any further than it needs to.
        assertThat(minimal).doesNotContain("public static String concat(", "public static int divide(",
                        "public static void exit(", "public static <T> java.util.List<T> array(",
                        "public static Byte toByte(", "public static Short toShort(");
        assertThat(mainBody(minimal)).doesNotContain("Solvik$Program.");
        assertThat(minimal.split("\n").length).isLessThan(reachingMore.split("\n").length);
        assertThat(reachingMore).contains("public static int divide(",
                "public static <T> java.util.List<T> array(", "public static void exit(");
    }

    @Test
    @DisplayName("an array's size member reads the carrier length and adds no support code")
    void array_size_member_reads_the_carrier_length() {
        String java = program("""
                values := Integer[3] {1, 2, 3}
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((values.size) .. "")
                """);
        // A member read is a direct List.size() call: no support member, helper, or runtime type.
        assertThat(java).contains(
                "new TextWriter(Stdio.stdout()).writeLine(String.valueOf((Object) values.size()) + \"\");")
                .doesNotContain("Solvik$Runtime", "Support routines");
    }

    @Test
    @DisplayName("a sync block is one Java synchronized statement over the existing value")
    void sync_lowers_to_a_java_synchronized_statement() {
        String guard = """
                type Guard {
                    pub mut hits := 0
                    pub func Guard() { }
                    pub func hit() { hits = hits + 1 }
                }
                """;
        String plain = program(guard + """
                guard := Guard()
                guard.hit()
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("hits=") .. (guard.hits))
                """);
        String guarded = program(guard + """
                guard := Guard()
                sync guard {
                    guard.hit()
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("hits=") .. (guard.hits))
                """);

        // Release is Java's own behavior, so the only difference from the same program without
        // `sync` is the statement itself: no helper, wrapper, lock object, or try/finally is emitted.
        String statement = "        synchronized (guard) {\n"
                + "            guard.hit();\n"
                + "        }\n";
        assertThat(guarded).contains(statement);
        assertThat(guarded.replace(statement, "        guard.hit();\n")).isEqualTo(plain);
        assertThat(guarded).doesNotContain("Solvik$Runtime");
    }

    @Test
    @DisplayName("a collection loop calls the iterator the declared protocol returns")
    void a_collection_loop_calls_the_declared_protocol() {
        String java = program("""
                items := solvik::collection::List<Integer>()
                items.add(7)
                mut total := 0
                for value in items {
                    total = total + value
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("total: ") .. (total))
                """);

        // One iterator per loop, then the JDK's own hasNext()/next() through the Solvik trait: no
        // helper, no per-element allocation, and no cast around the element.
        assertThat(java).contains(
                "$loop0: for (Iterator<Integer> $it1 = items.iterator(); $it1.hasNext(); ) {",
                "Integer value = $it1.next();")
                .doesNotContain("$Adapter", "$of(", "Solvik$Program.iterator");
        // An array loop keeps the enhanced-for shape it has always had.
        String array = program("""
                mut total := 0
                for value in Integer[2] {1, 2} {
                    total = total + value
                }
                """);
        assertThat(array).contains("for (Integer value : Arrays.<Integer>asList(1, 2)) {");
    }

    @Test
    @DisplayName("a generic type iterates its own type variable")
    void a_generic_type_iterates_its_own_type_variable() {
        String box = transpile("""
                type Box<T> {
                    pub items := solvik::collection::List<T>()

                    pub func Box(seed: T) {
                        items.add(seed)
                    }

                    pub func count() -> Integer {
                        mut seen := 0
                        for value in self.items {
                            seen = seen + 1
                        }
                        return seen
                    }
                }
                box := Box(1)
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("box: ") .. (box.count()))
                """).get("Box.java");

        assertThat(box).contains(
                "for (Iterator<T> $it1 = this.items.iterator(); $it1.hasNext(); ) {",
                "T value = $it1.next();");
    }

    @Test
    @DisplayName("the iterator type is emitted only for a program that reaches a collection")
    void the_iterator_type_follows_the_packaged_type_that_names_it() {
        assertThat(transpile("""
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("plain") .. "")
                """)).doesNotContainKey("solvik/collection/Iterator.java");

        Map<String, String> constructs = transpile("""
                items := solvik::collection::List<Integer>()
                items.add(1)
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("size: ") .. (items.size()))
                """);
        // List.sol names the protocol type, so reaching the collection reaches the trait it declares.
        assertThat(constructs).containsKey("solvik/collection/Iterator.java");
        // Iteration adds no support member: the adapter lives in the reached trait file.
        assertThat(constructs.get("Solvik$Program.java")).doesNotContain("Iterator", "checkedResult");
    }

    @Test
    @DisplayName("a sync monitor expression is evaluated once")
    void a_sync_monitor_is_evaluated_once() {
        String java = program("""
                type Guard {
                    pub func Guard() { }
                    pub func hit() { }
                }
                func pick(guard: Guard) -> Guard { return guard }
                guard := Guard()
                sync pick(guard) {
                    guard.hit()
                }
                """);

        // The monitor is lowered once into the statement, so one call site reaches `pick`.
        assertThat(java).containsPattern("synchronized \\(Solvik\\$Program\\.pick\\(.*\\)\\) \\{");
        assertThat(java.split("Solvik\\$Program\\.pick\\(", -1)).hasSize(2);
    }

    @DisplayName("only the reachable support members are emitted, with what they need")
    void emits_only_reachable_support_members() {
        String java = program("""
                func show(value: Any) {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((value .. "!") .. "")
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((value.equals(value)) .. "")
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((value === "!") .. "")
                }
                text := "42"
                count := 2
                items := Integer[3] {7}
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Integer(text)) .. (items[0]) .. (count / count) .. (count % count))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((text.type()) .. (text.split(",").size) .. (text.hashCode()))
                show(items)
                exit(0)
                """);

        // Reached directly or transitively by the emitted Java.
        assertThat(java).contains("public static String render(", "public static String concat(",
                        "public static Integer toInteger(", "public static BigInteger toBigInteger(",
                        "public static BigDecimal toBigDecimal(",
                        "private static boolean isIntegerText(", "private static boolean isDecimalText(",
                        "private static void required(", "private static RuntimeException conversion(",
                        "public static RuntimeException fail(", "public static final class Error",
                        "public static int divide(", "public static int remainder(",
                        // A support group is a name, so every overload of a used name travels with it.
                        "public static BigDecimal divide(",
                        "public static java.util.List<String> textSplit(",
                        "public static boolean equal(", "public static boolean identity(",
                        "private static boolean isScalar(", "public static Integer hash(",
                        "public static String type(", "public static void exit(",
                        "public static final class Exit",
                        "public static <T> java.util.List<T> array(");
        // Never mentioned by this program or by the packaged library that comes with every program.
        assertThat(java).doesNotContain("public static Byte toByte(", "public static Short toShort(",
                "public static Float toFloat(",
                "public static Double toDouble(", "public static Boolean toBoolean(",
                "public static boolean equalFloat(", "public static boolean equalFloating(",
                "private static boolean isFloatingText(",
                "SOL-R005");
    }

    @DisplayName("a native adapter keeps the failure helper it throws")
    void keeps_the_helper_a_native_adapter_throws() {
        String java = program("""
                module jdk {
                    pub type Math native "java.lang.Math" {
                        static pub func abs(value: Integer) -> Integer
                    }
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((jdk::Math.abs(-3)) .. "")
                """);

        assertThat(java).contains("public static RuntimeException fail(",
                "public static final class Error");
    }

    @DisplayName("every support group compiles and runs in one program")
    void compiles_a_program_that_reaches_every_support_member(@TempDir Path directory)
            throws Exception {
        // One program that touches every support group: compiling and running it is what
        // proves no group is emitted as Java that does not compile.
        Outcome outcome = run("""
                func show(v: Any) {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((v === "x") .. "")
                }
                text := "12"
                n := 3
                fraction := 1.5F
                big := BigInteger("12")
                big2 := BigInteger("5")
                dec := BigDecimal("1.50")
                dec2 := BigDecimal("3")
                items := Integer[4] {1}
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Byte(text)) .. (Short(text)) .. (Integer(text)) .. (Long(text)) .. (Float(text)) .. (Double(text)) .. (BigInteger(text)) .. (BigDecimal(text)) .. (Boolean("true")))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((n / n) .. (n % n) .. (big / big2) .. (big % big2) .. (dec / dec2))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((items) .. (items .. "x") .. (items.equals(items)) .. (items.type()))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((text.hashCode()) .. (text.substring(1)) .. (text.substring(0, 1)) .. (text.split("2").size) .. (items.size) .. (items[0]))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((n == n) .. (n === n) .. (fraction == n) .. (1.5 == 1.5) .. (1.5 != 2.5) .. (fraction))
                longs := 4L
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((longs / 2L) .. (longs % 2L))
                show(text)
                exit(0)
                """, directory);

        assertThat(outcome.stderr()).isEmpty();
        assertThat(outcome.stdout()).isEqualTo("""
                1212121212.012.01212true
                10220.50
                [1, null, null, null][1, null, null, null]xtrueArray
                156921241
                truetruefalsetruetrue1.5
                20
                false
                """);
        // exit(0) leaves the program through a Solvik exit, not a normal return.
        assertThat(outcome.failure()).isNotNull();
        assertThat(outcome.failure().getClass().getName())
                .isEqualTo(GeneratedNames.exitBinaryName());
    }

    // ------------------------------------------------------------------- indexing

    @DisplayName("an array access is a direct Java list operation")
    void lowers_array_access_to_direct_Java_operations() {
        String java = program("""
                items := String[3] {"a", "b", "c"}
                offset := 1
                byteOffset := Byte(2)
                items[offset] = "x"
                items[0] ..= "!"
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((items[offset]) .. (items[byteOffset]) .. (items[1]))
                """);

        assertThat(java).contains("items.get(offset)", "items.set(offset, \"x\");",
                        "items.get(1)", "items.get(byteOffset)")
                .doesNotContain("Solvik$Program.index(", "SOL-R005");
    }

    @DisplayName("java indexing keeps reads, writes, and compound writes working")
    void runs_array_access_with_java_indexing(@TempDir Path directory)
            throws Exception {
        Outcome outcome = run("""
                items := String[3] {"a", "b", "c"}
                mut offset := 1
                shortOffset := Short(0)
                items[offset] = "x"
                items[0] ..= "!"
                offset = offset + 1
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((items[shortOffset]) .. (items[1]) .. (items[offset - 1]))
                grid := Integer[][2] { Integer[2] {1, 2}, Integer[2] {3, 4} }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((grid[1][0]) .. (grid[0][1]))
                filled := Integer[2] { }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((filled[0]) .. "")
                """, directory);

        assertThat(outcome.failure()).isNull();
        assertThat(outcome.stderr()).isEmpty();
        assertThat(outcome.stdout()).isEqualTo("a!xx\n32\nnull\n");
    }

    // ---------------------------------------------------------------------- output

    @DisplayName("only arguments that can act keep evaluation temporaries")
    void keeps_output_temporaries_only_for_arguments_that_can_act() {
        String pure = program("""
                text := "a"
                count := 1
                flag := false
                items := Integer[2] {1, 2}
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).write((text) .. (count) .. (items[0]) .. (-count) .. (!flag) .. (1.5))
                """);
        assertThat(pure).contains("System.out.print(text + count + items.get(0)"
                        + " + (-java.util.Objects.requireNonNull(count).intValue()) + !(flag) + 1.5d);")
                .doesNotContain("$solvikPrint", "new StringBuilder()");

        String acting = program("""
                func value() -> Integer {
                    return 1
                }
                count := 1
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).write((count) .. (value()))
                """);
        assertThat(acting).contains("final Integer $solvikPrint0 = count;",
                        "final Integer $solvikPrint1 = value();",
                        "System.out.print(String.valueOf((Object) ($solvikPrint0)) + $solvikPrint1);")
                .doesNotContain("new StringBuilder()");
    }

    @DisplayName("output and concatenation render text, never arithmetic")
    void concatenation_and_output_render_text_not_arithmetic(@TempDir Path directory)
            throws Exception {
        Outcome outcome = run("""
                count := 1
                flag := true
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((count .. 2) .. (flag .. "x") .. (count) .. (flag))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).write((1) .. (2))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((null) .. (null))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((count .. count) .. "")
                """, directory);

        assertThat(outcome.failure()).isNull();
        assertThat(outcome.stderr()).isEmpty();
        assertThat(outcome.stdout()).isEqualTo("12truex1true\n12\nnullnull\n11\n");
    }

    @DisplayName("a numeric literal needs no null check or unboxing call")
    void folds_numeric_literals_into_their_operators() {
        String java = program("""
                sum := 2 + 3
                doubled := 2 * 2.5
                negative := -7
                same := Double(2.5)
                text := String(10)
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((sum) .. (doubled) .. (negative) .. (same) .. (text))
                """);

        assertThat(java).contains("Integer sum = (2 + 3);", "Double doubled = (2d * 2.5d);",
                        "Integer negative = (-7);", "Double same = 2.5d;")
                .doesNotContain("java.util.Objects.requireNonNull(2)");
    }
}
