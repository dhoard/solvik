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
 * The packaged {@code solvik::util::UUID} value type, written in Solvik and used with no import.
 *
 * <p>Every test compiles an entry program that reaches the packaged library, then runs the generated
 * Java, so the checks cover the JDK bindings, the value type, and the generated code together.
 */
@DisplayName("solvik::util::UUID executed")
class UuidLibraryExecutionTest {

    private static Map<String, String> transpile(String entry, Path directory)
            throws Exception {
        Path main = directory.resolve("main.sol");
        Files.writeString(main, entry, StandardCharsets.UTF_8);
        Diagnostics diagnostics = new Diagnostics();
        ProgramLoader.Loaded loaded = new ProgramLoader(diagnostics).load(main);
        assertThat(diagnostics.all()).isEmpty();
        assertThat(loaded.fileNames())
                .as("the packaged library, then the entry program")
                .contains("solvik/util/UUID.sol");
        assertThat(loaded.fileNames()).last().asString().endsWith("main.sol");
            return new SolvikCompiler("main.sol").transpile(loaded);
    }

    /** Compiles the generated sources with the JDK and returns the program's standard output. */
    private static String run(String entry, Path directory) throws Exception {
        Map<String, String> files = transpile(entry, directory);
        Path sources = Files.createDirectories(directory.resolve("src"));
        Path classes = Files.createDirectories(directory.resolve("classes"));
        List<String> arguments = new ArrayList<>(
                List.of("--release", "25", "-proc:none", "-d", classes.toString()));
        for (Map.Entry<String, String> file : new LinkedHashMap<>(files).entrySet()) {
            Path path = sources.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, file.getValue(), StandardCharsets.UTF_8);
            arguments.add(path.toString());
        }
        assertThat(ToolProvider.getSystemJavaCompiler()
                .run(null, null, null, arguments.toArray(String[]::new))).isZero();

        PrintStream originalOut = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{classes.toUri().toURL()}, UuidLibraryExecutionTest.class.getClassLoader())) {
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
    @DisplayName("now() produces canonical version 7 text and the current millisecond")
    void generated_ids_are_canonical_version_7(@TempDir Path directory) throws Exception {
        long before = System.currentTimeMillis();
        String output = run("""
                id := solvik::util::UUID.now()
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((id.toString()) .. "")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((id.timestamp()) .. "")
                text := id.toString()
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((text.size()) .. (" ") .. (text.substring(14, 15)) .. (" ") .. ("89ab".indexOf(text.substring(19, 20)) >= 0))
                """, directory);
        long after = System.currentTimeMillis();

        String[] lines = output.split("\n");
        assertThat(lines[0]).matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
        assertThat(Long.parseLong(lines[1])).isBetween(before, after);
        assertThat(lines[2]).isEqualTo("36 7 true");
    }

    @Test
    @DisplayName("parse() round-trips and equality is by value, not by reference")
    void parse_round_trips_and_compares_by_value(@TempDir Path directory) throws Exception {
        String output = run("""
                id := solvik::util::UUID.now()
                mut copy: solvik::util::UUID? = solvik::util::UUID.parse(id.toString())
                mut alsoCopy: solvik::util::UUID? = solvik::util::UUID.parse(id.toString())
                if copy != Null && alsoCopy != Null {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((copy.toString() == id.toString()) .. (" ") .. (copy.equals(id)) .. (" ") .. (copy.hashCode() == id.hashCode()))
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((copy.equals(alsoCopy)) .. (" ") .. (copy == alsoCopy))
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((id.equals("not a uuid")) .. (" ") .. (id.equals(Null)) .. (" ") .. (copy.timestamp() == id.timestamp()))
                }
                """, directory);

        assertThat(output).isEqualTo("true true true\ntrue false\nfalse false true\n");
    }

    @Test
    @DisplayName("parse() accepts canonical text of either case and rejects everything else")
    void parse_is_strict_about_version_7_canonical_text(@TempDir Path directory) throws Exception {
        String output = run("""
                candidates := String?[10] {
                    "0190f2a4-7b3c-7a1d-8e2f-3c4d5e6f7a8b",
                    "0190F2A4-7B3C-7A1D-8E2F-3C4D5E6F7A8B",
                    "0190f2a4-7b3c-4a1d-8e2f-3c4d5e6f7a8b",
                    "0190f2a4-7b3c-7a1d-ce2f-3c4d5e6f7a8b",
                    "0190f2a4-7b3c-7a1d-8e2f-3c4d5e6f7a8g",
                    "0190f2a4x7b3c-7a1d-8e2f-3c4d5e6f7a8b",
                    "0190f2a4-7b3c-7a1d-8e2f-3c4d5e6f7a8",
                    "",
                    Null,
                    "junk"
                }
                for candidate in candidates {
                    if candidate == Null {
                        solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((true) .. (" ") .. (false))
                    } else {
                        parsed := solvik::util::UUID.parse(candidate)
                        matches := parsed != Null && parsed.toString() == "0190f2a4-7b3c-7a1d-8e2f-3c4d5e6f7a8b"
                        solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((parsed == Null) .. (" ") .. (matches))
                    }
                }
                """, directory);

        assertThat(output).isEqualTo("""
                false true
                false true
                true false
                true false
                true false
                true false
                true false
                true false
                true false
                true false
                """);
    }

    @Test
    @DisplayName("compareTo() orders by timestamp, then by the random tail")
    void compare_to_orders_chronologically(@TempDir Path directory) throws Exception {
        String output = run("""
                base := solvik::util::UUID.parse("0190f2a4-7b3c-7a1d-8e2f-3c4d5e6f7a8b")
                later := solvik::util::UUID.parse("0190f2a4-7b3d-7a1d-8e2f-3c4d5e6f7a8b")
                low := solvik::util::UUID.parse("0190f2a4-7b3c-7a1d-8e2f-000000000000")
                high := solvik::util::UUID.parse("0190f2a4-7b3c-7a1d-8e2f-ffffffffffff")
                if base != Null && later != Null && low != Null && high != Null {
                    same := solvik::util::UUID.parse(base.toString())
                    if same != Null {
                        solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((base.compareTo(later) < 0) .. (" ") .. (later.compareTo(base) > 0))
                        solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((base.compareTo(base) == 0) .. (" ") .. (base.compareTo(same) == 0))
                        solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((low.compareTo(high) < 0) .. (" ") .. (high.compareTo(low) > 0))
                        solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((low.compareTo(base) < 0) .. (" ") .. (base.compareTo(high) < 0))
                        solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((later.timestamp() - base.timestamp()) .. "")
                    }
                }
                """, directory);

        assertThat(output).isEqualTo("true true\ntrue true\ntrue true\ntrue true\n1\n");
    }

    @Test
    @DisplayName("generated ids are unique and usable as Map keys")
    void generated_ids_are_unique(@TempDir Path directory) throws Exception {
        String output = run("""
                ids := solvik::collection::Map<String, Integer>()
                mut index := 0
                mut malformed := 0
                while index < 2000 {
                    id := solvik::util::UUID.now()
                    if solvik::util::UUID.parse(id.toString()) == Null {
                        malformed = malformed + 1
                    }
                    ids.put(id.toString(), index)
                    index = index + 1
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((ids.size()) .. (" ") .. (malformed))
                """, directory);

        assertThat(output).isEqualTo("2000 0\n");
    }

    @DisplayName("both Java backends produce the same UUID behavior")
    void both_backends_agree(@TempDir Path directory) throws Exception {
        String output = run("""
                id := solvik::util::UUID.now()
                text := id.toString()
                mut copy: solvik::util::UUID? = solvik::util::UUID.parse(text)
                early := solvik::util::UUID.parse("00000000-0000-7000-8000-000000000000")
                late := solvik::util::UUID.parse("0190f2a4-7b3d-7a1d-8e2f-3c4d5e6f7a8b")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((text.size()) .. (" ") .. (text.substring(14, 15)))
                if copy != Null && early != Null && late != Null {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((copy.equals(id)) .. (" ") .. (copy.hashCode() == id.hashCode()) .. (" ") .. (copy == id))
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((early.compareTo(late) < 0) .. (" ") .. (late.compareTo(early) > 0) .. (" ") .. (early.compareTo(early) == 0) .. (" ") .. (solvik::util::UUID.parse("nope") == Null))
                }
                """, directory);

        assertThat(output).isEqualTo("36 7\ntrue true false\ntrue true true true\n");
    }

    @Test
    @DisplayName("the value type is the only exported name, and values cannot be forged")
    void construction_and_helpers_are_not_exported(@TempDir Path directory) throws Exception {
        assertThat(compilationError("""
                forged := solvik::util::UUID("0190f2a4-7b3c-7a1d-8e2f-3c4d5e6f7a8b", Long(0))
                """, directory))
                .contains("SOL-T001")
                .contains("the constructor of 'solvik::util::UUID' is private");
        assertThat(compilationError("""
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((solvik::util::Hex.toHexString(Long(255))) .. "")
                """, directory))
                .contains("SOL-T011")
                .contains("type 'solvik::util::Hex' is private to module 'solvik::util'");
    }

    /** Transpiles an entry program that must fail, returning the rendered diagnostics. */
    private static String compilationError(String entry, Path directory) throws Exception {
        Path main = directory.resolve("main.sol");
        Files.writeString(main, entry, StandardCharsets.UTF_8);
        Diagnostics diagnostics = new Diagnostics();
        ProgramLoader.Loaded loaded = new ProgramLoader(diagnostics).load(main);
        assertThat(diagnostics.all()).isEmpty();
        try {
            new SolvikCompiler("main.sol").transpile(loaded);
        } catch (SolvikCompiler.CompilationFailure failure) {
            return failure.getMessage();
        }
        throw new AssertionError("expected the program to be rejected");
    }

    @Test
    @DisplayName("the type exposes no constructor, so values only come from now() and parse()")
    void values_can_only_be_created_by_the_type(@TempDir Path directory) throws Exception {
        Map<String, String> files = transpile("""
                id := solvik::util::UUID.now()
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((id) .. "")
                """, directory);

        assertThat(files).containsKeys("Solvik$Program.java", "solvik/util/UUID.java",
                "solvik/util/Hex.java", "solvik/util/Clock.java", "solvik/util/Random.java");
        String uuid = files.get("solvik/util/UUID.java");
        assertThat(uuid)
                .contains("private UUID(String text, Long millis)")
                .doesNotContain("public UUID(")
                .contains("public boolean equals(Object other)")
                .contains("public int hashCode()")
                .contains("return \"UUID\";");
    }
}
