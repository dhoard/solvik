package dev.solvik.compiler.transpile;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.SolvikCompiler;
import dev.solvik.compiler.types.ConversionText;
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
 * Conversion text is decided the same way whether the text is a literal or a computed value.
 *
 * <p>The checker applies {@link ConversionText} to literals and the generated runtime applies its
 * own copy to computed text; this test pins the two halves together, so a conversion cannot be a
 * compile error in one place and accepted in the other. Samples are passed through a variable so
 * the compiler does not pre-check them.
 */
@DisplayName("conversion text: compile time and run time agree")
class ConversionTextAgreementTest {

    private static final List<String> INTEGERS = List.of(
            "0", "42", "+42", "-42", "0042", "", "+", " 42", "42 ", "1_000", "4.2", "4e2", "0x10");

    private static final List<String> DECIMALS = List.of(
            "0", "1.5", ".5", "1e3", "1E3", "1e+3", "1.5e-3", "5.", ".", "1e",
            "1.5.5", " 1.5", "1,5", "NaN", "Infinity");

    private static final List<String> BOOLEANS = List.of("true", "false", "True", "TRUE", "1", "");

    /** What the runtime decided for one sample: its rendered value, or the abort code. */
    private record Outcome(String value) {
    }

    @Test
    void integer_text_is_decided_the_same_way_in_both_halves(@TempDir Path directory) throws Exception {
        for (String sample : INTEGERS) {
            assertThat(ConversionText.isInteger(sample))
                    .describedAs("checker verdict for <%s>", sample)
                    .isEqualTo(converts("Integer", sample, directory));
        }
    }

    @Test
    void decimal_text_is_decided_the_same_way_in_both_halves(@TempDir Path directory) throws Exception {
        for (String sample : DECIMALS) {
            assertThat(ConversionText.isDecimal(sample))
                    .describedAs("checker verdict for <%s>", sample)
                    .isEqualTo(converts("BigDecimal", sample, directory));
        }
    }

    @Test
    void boolean_text_is_decided_the_same_way_in_both_halves(@TempDir Path directory) throws Exception {
        for (String sample : BOOLEANS) {
            assertThat(ConversionText.isBoolean(sample))
                    .describedAs("checker verdict for <%s>", sample)
                    .isEqualTo(converts("Boolean", sample, directory));
        }
    }

    /** Runs {@code writeLine(Target(text))} with the text in a variable, returning whether it ran. */
    private boolean converts(String target, String sample, Path directory) throws Exception {
        String quoted = "\"" + sample.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        String source = "text := " + quoted + "\n"
                + "TextWriter(Stdio.stdout()).writeLine((" + target + "(text)) .. \"\")\n";
        return runsWithoutAborting(source, directory.resolve("case-" + Math.abs(sample.hashCode())));
    }

    private boolean runsWithoutAborting(String source, Path directory) throws Exception {
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
        PrintStream originalErr = System.err;
        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{classes.toUri().toURL()}, getClass().getClassLoader())) {
            System.setOut(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
            try {
                loader.loadClass(GeneratedNames.PROGRAM_BINARY_NAME)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
                return true;
            } catch (InvocationTargetException failure) {
                assertThat(failure.getCause())
                        .describedAs("a rejected conversion must abort, not fail another way")
                        .hasMessageContaining("cannot convert");
                return false;
            }
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }
}
