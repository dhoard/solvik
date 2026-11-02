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
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runtime aborts carry stable codes (§10).
 *
 * <p>A failure that cannot be decided while compiling aborts at run time, and the abort carries a
 * {@code SOL-R###} code so that tests and tooling match behavior by code rather than by prose.
 */
@DisplayName("runtime abort codes")
class RuntimeAbortTest {

    /** The outcome of running a program that is expected to abort. */
    private record Abort(String code, String message, String stderr, Throwable cause) {
    }

    private Abort runAborting(String source, Path directory) throws Exception {
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

        PrintStream originalErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        Throwable abort;
        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{classes.toUri().toURL()}, getClass().getClassLoader())) {
            System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
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
            String code = (String) abort.getClass().getMethod("code").invoke(abort);
            return new Abort(code, abort.getMessage(),
                    captured.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"), abort);
        }
        return new Abort(abort.getClass().getSimpleName(), abort.getMessage(),
                captured.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"), abort);
    }

    @Test
    void division_by_zero_reports_its_code(@TempDir Path directory) throws Exception {
        Abort abort = runAborting("""
                divisor := 0
                TextWriter(Stdio.stdout()).writeLine((10 / divisor) .. "")
                """, directory);

        assertThat(abort.code()).isEqualTo("SOL-R001");
        assertThat(abort.message()).isEqualTo("division by zero");
        assertThat(abort.stderr()).isEqualTo("solvik: runtime error [SOL-R001]: division by zero\n");
    }

    @Test
    void remainder_by_zero_reports_its_code(@TempDir Path directory) throws Exception {
        Abort abort = runAborting("""
                divisor := 0
                TextWriter(Stdio.stdout()).writeLine((10 % divisor) .. "")
                """, directory);

        assertThat(abort.code()).isEqualTo("SOL-R002");
    }

    @Test
    @DisplayName("a non-terminating BigDecimal division aborts with its own code")
    void non_terminating_division_reports_its_code(@TempDir Path directory) throws Exception {
        Abort abort = runAborting("""
                divisor := BigDecimal(3)
                TextWriter(Stdio.stdout()).writeLine((BigDecimal(1.0) / divisor) .. "")
                """, directory);

        assertThat(abort.code()).isEqualTo("SOL-R003");
        assertThat(abort.message()).isEqualTo("BigDecimal division has no terminating result: 1.0 / 3");
    }

    @Test
    void index_conversion_and_conversions_report_their_codes(@TempDir Path directory) throws Exception {
        Abort conversion = runAborting("""
                text := "abc"
                TextWriter(Stdio.stdout()).writeLine((Integer(text)) .. "")
                """, directory);
        assertThat(conversion.code()).isEqualTo("SOL-R006");
        assertThat(conversion.message()).isEqualTo("cannot convert 'abc' to Integer");
    }

    @Test
    @DisplayName("an exit code that is not a literal is checked at run time")
    void exit_range_reports_its_code(@TempDir Path directory) throws Exception {
        Abort abort = runAborting("""
                mut code := 300
                exit(code)
                """, directory);

        assertThat(abort.code()).isEqualTo("SOL-R007");
    }

    @Test
    @DisplayName("array reads and writes throw a JVM bounds exception for the CLI to map")
    void array_bounds_the_jvm_reports_are_mapped(@TempDir Path directory) throws Exception {
        Abort read = runAborting("""
                items := Integer[2] {1, 2}
                index := 5
                TextWriter(Stdio.stdout()).writeLine((items[index]) .. "")
                """, directory);
        assertThat(read.cause()).isInstanceOf(IndexOutOfBoundsException.class);

        Abort write = runAborting("""
                items := Integer[2] {1, 2}
                index := 5
                items[index] = 9
                """, directory);
        assertThat(write.cause()).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    @DisplayName("a declared non-null native result that is Java null aborts with SOL-R008")
    void a_native_null_where_a_value_is_required_reports_its_code(@TempDir Path directory)
            throws Exception {
        Abort abort = runAborting("""
                // java.util.HashMap.get returns null for a missing key, and this binding claims a
                // non-null Any, so the generated boundary must refuse the null (§11.1).
                module raw {
                    pub type Map native "java.util.HashMap" {
                        pub func Map()
                        pub func get(key: Any) -> Any native "get"
                    }
                }

                map := raw::Map()
                TextWriter(Stdio.stdout()).writeLine((map.get("absent")) .. "")
                """, directory);

        assertThat(abort.code()).isEqualTo("SOL-R008");
        assertThat(abort.message())
                .isEqualTo("a null value was used where a value is required");
        assertThat(abort.stderr()).isEqualTo(
                "solvik: runtime error [SOL-R008]: a null value was used where a value is required\n");
    }

    @Test
    @DisplayName("a divisor the compiler cannot know still aborts at run time, with the same code")
    void a_computed_zero_still_aborts_at_run_time(@TempDir Path directory) throws Exception {
        Abort abort = runAborting("""
                zero := 0
                TextWriter(Stdio.stdout()).writeLine((1 / zero) .. "")
                """, directory);

        assertThat(abort.code()).isEqualTo("SOL-R001");
    }
}
