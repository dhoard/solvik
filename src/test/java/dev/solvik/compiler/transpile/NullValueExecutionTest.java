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
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end runs of the {@code Null} singleton and nullable types (§2, §3).
 *
 * <p>{@code Null} is a first-class value of its own type, carried by Java {@code null}; only
 * {@code T?} accepts it, and a nullable value must be proven present before it is used. The
 * generated Java therefore never declares a Java primitive for a value that may be Null.
 */
@DisplayName("Null values executed")
class NullValueExecutionTest {

    @Test
    @DisplayName("a negative literal compares with Null without a Java-invalid cast")
    void negative_literals_compare_with_null(@TempDir Path directory) throws Exception {
        String output = run("""
                TextWriter(Stdio.stdout()).writeLine((-1 == Null) .. (" ") .. (Null == -1) .. (" ") .. (-1 != Null))
                mut absent: Any? = Null
                TextWriter(Stdio.stdout()).writeLine((absent == -1) .. (" ") .. (absent == Null))
                """, directory);

        assertThat(output).isEqualTo("false false true\nfalse true\n");
    }


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
    @DisplayName("a Byte or Boolean nullable binding holds Null, and Java keeps it boxed")
    void primitive_backed_types_can_hold_null(@TempDir Path directory) throws Exception {
        String output = run("""
                mut octet: Byte? = Null
                octet = Byte(7)
                TextWriter(Stdio.stdout()).writeLine((octet) .. "")
                octet = Null
                TextWriter(Stdio.stdout()).writeLine((octet) .. "")

                mut flag: Boolean? = Null
                flag = true
                TextWriter(Stdio.stdout()).writeLine((flag) .. "")
                flag = Null
                TextWriter(Stdio.stdout()).writeLine((flag) .. "")
                """, directory);

        assertThat(output).isEqualTo("7\nnull\ntrue\nnull\n");
    }

    @Test
    @DisplayName("Null is printed as null and compares equal to Null, and a present value is restored")
    void a_null_value_prints_and_compares(@TempDir Path directory) throws Exception {
        String output = run("""
                mut x: Integer? = 1
                x = Null
                TextWriter(Stdio.stdout()).writeLine((x) .. (" ") .. (x == Null))
                x = 2
                TextWriter(Stdio.stdout()).writeLine((x) .. (" ") .. (x + 1))

                mut words: String[2]? = String[2] {"a", "b"}
                words = Null
                TextWriter(Stdio.stdout()).writeLine((words) .. (" ") .. (words == Null))
                nothing := Null
                TextWriter(Stdio.stdout()).writeLine((nothing) .. (" ") .. (nothing == Null) .. (" ") .. (Null === Null) .. (" ") .. (String(Null)))
                """, directory);

        assertThat(output).isEqualTo("null true\n2 3\nnull true\nnull true true null\n");
    }

    @Test
    void every_type_can_hold_null_and_be_restored(@TempDir Path directory) throws Exception {
        String output = run("""
                type Point {
                    mut x := 0

                    pub func Point() {
                        self.x = 0
                    }

                    pub func describe() -> String {
                        return "(0, 0)"
                    }
                }

                mut octet: Byte? = Byte(9)
                mut flag: Boolean? = false
                mut decimal: Double? = 1.5
                mut text: String? = "text"
                mut point: Point? = Point()
                octet = Null
                flag = Null
                decimal = Null
                text = Null
                point = Null
                TextWriter(Stdio.stdout()).writeLine((octet) .. (" ") .. (flag) .. (" ") .. (decimal) .. (" ") .. (text) .. (" ") .. (point))

                octet = Byte(1)
                flag = true
                decimal = 2.5
                text = "again"
                point = Point()
                mut restored: Point? = point
                if restored != Null {
                    TextWriter(Stdio.stdout()).writeLine((octet) .. (" ") .. (flag) .. (" ") .. (decimal) .. (" ") .. (text) .. (" ") .. (restored.describe()))
                }
                """, directory);

        assertThat(output).isEqualTo("""
                null null null null null
                1 true 2.5 again (0, 0)
                """);
    }

    @Test
    @DisplayName("a nullable element holds Null, and the element type stays put")
    void an_element_may_be_null(@TempDir Path directory) throws Exception {
        String output = run("""
                items := Integer?[3] {1, Null, 3}
                TextWriter(Stdio.stdout()).writeLine((items[1]) .. (" ") .. (items[1] == Null) .. (" ") .. (items[2]))
                items[1] = 2
                TextWriter(Stdio.stdout()).writeLine((items[1]) .. "")
                """, directory);

        assertThat(output).isEqualTo("null true 3\n2\n");
    }

    @Test
    @DisplayName("a nullable value used as a value is rejected before it can abort")
    void a_nullable_value_needs_a_null_check(@TempDir Path directory) {
        assertThatThrownBy(() -> run("""
                mut x: Integer? = 1
                x = Null
                TextWriter(Stdio.stdout()).writeLine((x + 1) .. "")
                """, directory)).isInstanceOf(SolvikCompiler.CompilationFailure.class)
                .hasMessageContaining(TypeChecker.NULL_UNSAFE);

        assertThatThrownBy(() -> run("""
                mut flag: Boolean? = Null
                if flag {
                    TextWriter(Stdio.stdout()).writeLine(("yes") .. "")
                }
                """, directory)).isInstanceOf(SolvikCompiler.CompilationFailure.class)
                .hasMessageContaining(TypeChecker.NULL_UNSAFE);

        assertThatThrownBy(() -> run("""
                mut text: String? = Null
                TextWriter(Stdio.stdout()).writeLine((text.size()) .. "")
                """, directory)).isInstanceOf(SolvikCompiler.CompilationFailure.class)
                .hasMessageContaining(TypeChecker.NULL_UNSAFE);
    }

    /** Runs a program that is expected to abort and returns the code its abort carries. */
    private String abortCode(String source, Path directory) throws Exception {
        Path sources = directory.resolve("abort-src");
        Path classes = directory.resolve("abort-classes");
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

        PrintStream originalErr = System.err;
        Throwable abort;
        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{classes.toUri().toURL()}, getClass().getClassLoader())) {
            System.setErr(new PrintStream(OutputStreamSink.INSTANCE, true, StandardCharsets.UTF_8));
            try {
                loader.loadClass(GeneratedNames.PROGRAM_BINARY_NAME)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
                abort = null;
            } catch (InvocationTargetException failure) {
                abort = failure.getCause();
            } finally {
                System.setErr(originalErr);
            }
        }
        assertThat(abort).describedAs("the program should have aborted").isNotNull();
        if ((GeneratedNames.errorBinaryName()).equals(abort.getClass().getName())) {
            return (String) abort.getClass().getMethod("code").invoke(abort);
        }
        // A null dereference happens inside generated Java, so it arrives as the JVM's own
        // exception; the driver maps it to SOL-R008 just as this mirrors (see Main#runtimeCode).
        assertThat(abort).isInstanceOf(NullPointerException.class);
        return "SOL-R008";
    }

    /** Discards the abort message the runtime writes to standard error. */
    private static final class OutputStreamSink extends java.io.OutputStream {
        static final OutputStreamSink INSTANCE = new OutputStreamSink();

        @Override
        public void write(int ignored) {
        }
    }
}
