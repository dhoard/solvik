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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Behavior of uppercase numeric-literal suffixes, lowered by both Java backends. */
class NumericSuffixExecutionTest {

    private static Map<String, String> transpile(String source) {
            return new SolvikCompiler("suffixes.sol").transpile(source);
    }

    private static String run(String source, Path directory) throws Exception {
        Map<String, String> files = transpile(source);
        Path sources = Files.createDirectories(directory.resolve("src"));
        Path classes = Files.createDirectories(directory.resolve("classes"));
        List<String> arguments = new ArrayList<>(List.of("--release", "25", "-d", classes.toString()));
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
                PrintStream output = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            System.setOut(output);
            try {
                loader.loadClass(GeneratedNames.PROGRAM_BINARY_NAME).getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            } catch (InvocationTargetException failure) {
                throw new AssertionError(failure.getCause());
            } finally {
                System.setOut(originalOut);
            }
        }
        return buffer.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    @Test
    void a_suffix_matches_its_conversion_constructor(@TempDir Path directory)
            throws Exception {
        String output = run("""
                TextWriter(Stdio.stdout()).writeLine((1B) .. (" ") .. (Byte(1)))
                TextWriter(Stdio.stdout()).writeLine((1S) .. (" ") .. (Short(1)))
                TextWriter(Stdio.stdout()).writeLine((1I) .. (" ") .. (Integer(1)))
                TextWriter(Stdio.stdout()).writeLine((1L) .. (" ") .. (Long(1)))
                TextWriter(Stdio.stdout()).writeLine((1F) .. (" ") .. (Float(1)))
                TextWriter(Stdio.stdout()).writeLine((1.5F) .. (" ") .. (Float(1.5)))
                TextWriter(Stdio.stdout()).writeLine((1D) .. (" ") .. (Double(1)))
                TextWriter(Stdio.stdout()).writeLine((1.5D) .. (" ") .. (Double(1.5)))
                TextWriter(Stdio.stdout()).writeLine((1BI) .. (" ") .. (BigInteger(1)))
                TextWriter(Stdio.stdout()).writeLine((2BD) .. (" ") .. (BigDecimal(2)))
                TextWriter(Stdio.stdout()).writeLine((1.50BD) .. (" ") .. (BigDecimal("1.50")))
                """, directory);

        assertThat(output).isEqualTo("""
                1 1
                1 1
                1 1
                1 1
                1.0 1.0
                1.5 1.5
                1.0 1.0
                1.5 1.5
                1 1
                2 2
                1.50 1.50
                """);
    }

    @Test
    void a_suffix_decides_the_runtime_type(@TempDir Path directory) throws Exception {
        String output = run("""
                TextWriter(Stdio.stdout()).writeLine((1B.type()) .. (" ") .. (1S.type()) .. (" ") .. (1I.type()) .. (" ") .. (1L.type()) .. (" ") .. (1F.type()) .. (" ") .. (1D.type()) .. (" ") .. (1BI.type()) .. (" ") .. (1BD.type()))
                TextWriter(Stdio.stdout()).writeLine((1.type()) .. (" ") .. (1.5.type()))
                """, directory);

        assertThat(output).isEqualTo("""
                Byte Short Integer Long Float Double BigInteger BigDecimal
                Integer Double
                """);
    }

    @Test
    void a_sign_folds_into_a_suffixed_literal(@TempDir Path directory)
            throws Exception {
        String output = run("""
                minimum := -128B
                TextWriter(Stdio.stdout()).writeLine((-128B) .. (" ") .. (-32768S) .. (" ") .. (-2147483648I) .. (" ") .. (-9223372036854775808L))
                TextWriter(Stdio.stdout()).writeLine((minimum.type()) .. (" ") .. (-1.5BD) .. (" ") .. (-1.5F))
                """, directory);

        assertThat(output).isEqualTo("""
                -128 -32768 -2147483648 -9223372036854775808
                Byte -1.5 -1.5
                """);
    }

    @Test
    void suffixed_operands_promote_like_their_carriers(@TempDir Path directory)
            throws Exception {
        String output = run("""
                TextWriter(Stdio.stdout()).writeLine((1B + 1B) .. (" ") .. (1L + 1) .. (" ") .. (1F + 1) .. (" ") .. (1BD + 1BD) .. (" ") .. (1BI * 2BI))
                TextWriter(Stdio.stdout()).writeLine((10L / 3L) .. (" ") .. (10L % 3L) .. (" ") .. (1BD == 1BD) .. (" ") .. (1BD == 1.0BD))
                """, directory);

        // BigDecimal equality is boxed equality, so the scale is part of the value (§4).
        assertThat(output).isEqualTo("2 2 2.0 2 2\n3 1 true false\n");
    }

    @Test
    void big_number_suffixes_reach_java_methods(@TempDir Path directory)
            throws Exception {
        String output = run("""
                TextWriter(Stdio.stdout()).writeLine((1BI.pow(3)) .. (" ") .. (12BI.gcd(18BI)) .. (" ") .. (1.50BD.scale()) .. (" ") .. (2BD.add(1BD)) .. (" ") .. (1.50BD.stripTrailingZeros()))
                """, directory);

        assertThat(output).isEqualTo("1 6 2 3 1.5\n");
    }

    @Test
    void a_suffixed_label_matches_a_byte_subject(@TempDir Path directory)
            throws Exception {
        String output = run("""
                b := Byte(7)
                switch b {
                    case 7B {
                        TextWriter(Stdio.stdout()).writeLine(("seven") .. "")
                    }
                    default {
                        TextWriter(Stdio.stdout()).writeLine(("other") .. "")
                    }
                }
                n := 1
                switch n {
                    case 1I {
                        TextWriter(Stdio.stdout()).writeLine(("one") .. "")
                    }
                    default {
                        TextWriter(Stdio.stdout()).writeLine(("other") .. "")
                    }
                }
                """, directory);

        assertThat(output).isEqualTo("seven\none\n");
    }

    @Test
    void typed_arrays_mix_suffixed_and_adopted_elements(@TempDir Path directory)
            throws Exception {
        String output = run("""
                longs := Long[2] {1L, 2}
                bytes := Byte[2] {-128B, 127B}
                floats := Float[1] {1.5F}
                decimals := BigDecimal[1] {1.50BD}
                TextWriter(Stdio.stdout()).writeLine((longs[0]) .. (" ") .. (longs[1]) .. (" ") .. (bytes[0]) .. (" ") .. (bytes[1]) .. (" ") .. (floats[0]) .. (" ") .. (decimals[0]))
                """, directory);

        assertThat(output).isEqualTo("1 2 -128 127 1.5 1.50\n");
    }

    @Test
    void generated_java_carries_the_suffix_type(@TempDir Path directory) {
        String java = transpile("""
                a := 1B
                b := 1S
                c := 1L
                d := 1F
                e := 1D
                f := 1BI
                g := 1.50BD
                TextWriter(Stdio.stdout()).writeLine((a) .. (b) .. (c) .. (d) .. (e) .. (f) .. (g))
                """).get("Solvik$Program.java");

        assertThat(java).contains("(byte) 1", "(short) 1", "1L", "1f", "1d",
                "new BigInteger(\"1\")", "new BigDecimal(\"1.50\")");
        assertThat(java).doesNotContain("1B", "1S", "1F", "1D", "1BI", "1BD");
    }

    @Test
    void a_suffix_never_reaches_a_case_label(@TempDir Path directory) {
        String java = transpile("""
                n := 1
                switch n {
                    case 1I {
                        TextWriter(Stdio.stdout()).writeLine(("one") .. "")
                    }
                }
                """).get("Solvik$Program.java");

        assertThat(java).contains("1");
        assertThat(java).doesNotContain("1I");
    }
}
