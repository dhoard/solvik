package dev.solvik.compiler.transpile;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.SolvikCompiler;
import dev.solvik.compiler.load.ProgramLoader;
import dev.solvik.compiler.testsupport.PackagedLibrary;
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
 * The bundled {@code Channel}, {@code Stdio}, {@code TextReader}, and {@code TextWriter} types.
 *
 * <p>Every test compiles a program against the packaged library and runs the generated Java with an
 * empty dependency classpath, so it covers the Solvik declarations, the generated payload/adapters,
 * and the JDK-only adapter source together. All channels are in-memory unless a test explicitly
 * exercises {@code Stdio}; nothing here touches a real file or blocks on process input.
 */
@DisplayName("bundled I/O types executed")
class BundledIoExecutionTest {

    private static Map<String, String> transpile(String entry, Path directory) throws Exception {
        Path main = directory.resolve("main.sol");
        Files.writeString(main, entry, StandardCharsets.UTF_8);
        ProgramLoader.Loaded loaded = new ProgramLoader(
                new dev.solvik.compiler.diagnostic.Diagnostics()).load(main);
        return new SolvikCompiler("main.sol").transpile(loaded);
    }

    /** Compiles and runs {@code entry}, returning its standard output with LF line endings. */
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
                new URL[]{classes.toUri().toURL()},
                BundledIoExecutionTest.class.getClassLoader())) {
            System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
            loader.loadClass(GeneratedNames.PROGRAM_BINARY_NAME)
                    .getMethod("main", String[].class)
                    .invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(originalOut);
        }
        return buffer.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    /** Compiles a program that must be rejected, returning the rendered diagnostics. */
    private static String compilationError(String entry, Path directory) throws Exception {
        Path main = directory.resolve("main.sol");
        Files.writeString(main, entry, StandardCharsets.UTF_8);
        ProgramLoader.Loaded loaded = new ProgramLoader(
                new dev.solvik.compiler.diagnostic.Diagnostics()).load(main);
        try {
            new SolvikCompiler("main.sol").transpile(loaded);
        } catch (SolvikCompiler.CompilationFailure failure) {
            return failure.getMessage();
        }
        throw new AssertionError("expected the program to be rejected");
    }

    @Test
    @DisplayName("read returns at most the requested bytes and Null only at end of input")
    void read_returns_partial_bytes_and_null_at_eof(@TempDir Path directory) throws Exception {
        String output = run("""
                channel := Channel.bytes()
                written := channel.write(Byte[3] {65, 66, 67})
                part := channel.read(2)
                rest := channel.read(2)
                done := channel.read(1)
                if part != Null && rest != Null {
                    TextWriter(Stdio.stdout()).writeLine("part=" .. part .. " rest=" .. rest
                        \\ .. " eof=" .. (done == Null))
                }
                TextWriter(Stdio.stdout()).writeLine("written=" .. written)
                """, directory);

        assertThat(output).isEqualTo("part=[65, 66] rest=[67] eof=true\nwritten=3\n");
    }

    @Test
    @DisplayName("write and writeAll round-trip every byte shape through the array carrier")
    void write_round_trips_all_byte_values(@TempDir Path directory) throws Exception {
        String output = run("""
                channel := Channel.bytes()
                payload := Byte[4] {0, 127, -128, -1}
                first := channel.write(payload)
                channel.writeAll(Byte[2] {65, 66})
                back := channel.read(6)
                if back != Null {
                    TextWriter(Stdio.stdout()).writeLine("first=" .. first .. " bytes=" .. back)
                }
                """, directory);

        assertThat(output).isEqualTo("first=4 bytes=[0, 127, -128, -1, 65, 66]\n");
    }

    @Test
    @DisplayName("close is idempotent, shared by aliases, and later operations fail")
    void close_is_shared_by_aliases(@TempDir Path directory) throws Exception {
        String output = run("""
                channel := Channel.bytes()
                alias := channel
                channel.close()
                alias.close()
                TextWriter(Stdio.stdout()).writeLine("open=" .. channel.isOpen())
                try {
                    ignored := alias.read(1)
                } catch (error: Exception) {
                    TextWriter(Stdio.stdout()).writeLine("closed=" .. (error.getCode() == IoError.Closed))
                }
                """, directory);

        assertThat(output).isEqualTo("open=false\nclosed=true\n");
    }

    @Test
    @DisplayName("a standard handle reports its direction and rejects the other operation")
    void capability_violations_are_io_errors(@TempDir Path directory) throws Exception {
        String output = run("""
                out := Stdio.stdout()
                TextWriter(Stdio.stdout()).writeLine("readable=" .. out.canRead()
                    \\ .. " writable=" .. out.canWrite())
                try {
                    ignored := out.read(1)
                } catch (error: Exception) {
                    TextWriter(Stdio.stdout()).writeLine("unsupported="
                        \\ .. (error.getCode() == IoError.Unsupported))
                }
                """, directory);

        assertThat(output).isEqualTo("readable=false writable=true\nunsupported=true\n");
    }

    @Test
    @DisplayName("Stdio setters redirect, snapshot the binding, and never close a channel")
    void stdio_redirection_snapshots_the_current_channel(@TempDir Path directory) throws Exception {
        String output = run("""
                saved := Stdio.stdout()
                captured := Channel.bytes()

                writer := TextWriter(saved)
                Stdio.setStdout(captured)
                writer.writeLine("original")
                TextWriter(Stdio.stdout()).writeLine("redirected")
                Stdio.setStdout(saved)

                reader := TextReader(captured)
                TextWriter(Stdio.stdout()).writeLine("captured=" .. reader.readLine())
                reader.close()
                TextWriter(Stdio.stdout()).writeLine("open=" .. captured.isOpen())
                """, directory);

        assertThat(output).isEqualTo("original\ncaptured=redirected\nopen=true\n");
    }

    @Test
    @DisplayName("an invalid Stdio replacement raises and keeps the previous binding")
    void invalid_standard_replacement_is_rejected(@TempDir Path directory) throws Exception {
        String output = run("""
                try {
                    Stdio.setStdout(Stdio.stdin())
                } catch (error: Exception) {
                    TextWriter(Stdio.stdout()).writeLine("rejected="
                        \\ .. (error.getCode() == IoError.Unsupported))
                }
                TextWriter(Stdio.stdout()).writeLine("still the original")
                """, directory);

        assertThat(output).isEqualTo("rejected=true\nstill the original\n");
    }

    @Test
    @DisplayName("the original standard channels are protected from close")
    void standard_channels_cannot_be_closed(@TempDir Path directory) throws Exception {
        String output = run("""
                try {
                    Stdio.stderr().close()
                } catch (error: Exception) {
                    TextWriter(Stdio.stdout()).writeLine("protected="
                        \\ .. (error.getCode() == IoError.Unsupported))
                }
                """, directory);

        assertThat(output).isEqualTo("protected=true\n");
    }

    @Test
    @DisplayName("readLine recognizes LF, CRLF, CR, empty lines, and an unterminated last line")
    void read_line_endings() throws Exception {
        Path directory = Files.createTempDirectory("solvik-lines");
        String output = run("""
                channel := Channel.bytes()
                writer := TextWriter(channel)
                writer.write("a\\nb\\r\\nc\\rd\\n\\nlast")
                writer.close()
                reader := TextReader(channel)
                mut index := 0
                mut line: String? = reader.readLine()
                while line != Null && index < 10 {
                    index = index + 1
                    TextWriter(Stdio.stdout()).writeLine(index .. "=[" .. line .. "]")
                    line = reader.readLine()
                }
                TextWriter(Stdio.stdout()).writeLine("done=" .. (line == Null))
                reader.close()
                """, directory);

        assertThat(output).isEqualTo("""
                1=[a]
                2=[b]
                3=[c]
                4=[d]
                5=[]
                6=[last]
                done=true
                """);
    }

    @Test
    @DisplayName("empty input returns Null while an empty line returns an empty String")
    void empty_input_is_not_an_empty_line(@TempDir Path directory) throws Exception {
        String output = run("""
                empty := Channel.bytes()
                noLines := TextReader(empty)
                TextWriter(Stdio.stdout()).writeLine("empty=" .. (noLines.readLine() == Null))
                noLines.close()

                oneNewline := Channel.bytes()
                writer := TextWriter(oneNewline)
                writer.write("\\n")
                writer.close()
                blank := TextReader(oneNewline)
                first := blank.readLine()
                TextWriter(Stdio.stdout()).writeLine("blank=[" .. first ..
                    \\ "] eof=" .. (blank.readLine() == Null))
                blank.close()
                """, directory);

        assertThat(output).isEqualTo("empty=true\nblank=[] eof=true\n");
    }

    @Test
    @DisplayName("UTF-8 decodes across byte boundaries and round-trips through the writer")
    void utf8_round_trips(@TempDir Path directory) throws Exception {
        String output = run("""
                channel := Channel.bytes()
                writer := TextWriter(channel)
                writer.write("héllo 日本 🚀\\nsecond")
                writer.close()
                reader := TextReader(channel)
                first := reader.readLine()
                second := reader.readLine()
                if first != Null && second != Null {
                    TextWriter(Stdio.stdout()).writeLine(first)
                    TextWriter(Stdio.stdout()).writeLine(second)
                }
                reader.close()
                """, directory);

        assertThat(output).isEqualTo("héllo 日本 🚀\nsecond\n");
    }

    @Test
    @DisplayName("malformed UTF-8 raises IoError.Encoding rather than replacing a character")
    void malformed_utf8_raises_encoding_error(@TempDir Path directory) throws Exception {
        String output = run("""
                channel := Channel.bytes()
                written := channel.write(Byte[1] {-1B})
                reader := TextReader(channel)
                try {
                    line := reader.readLine()
                    TextWriter(Stdio.stdout()).writeLine("unexpected")
                } catch (error: Exception) {
                    TextWriter(Stdio.stdout()).writeLine("encoding="
                        \\ .. (error.getCode() == IoError.Encoding) .. " written=" .. written)
                }
                reader.close()
                """, directory);

        assertThat(output).isEqualTo("encoding=true written=1\n");
    }

    @Test
    @DisplayName("closing a reader does not close its channel, and reading after close fails")
    void reader_close_leaves_the_channel_open(@TempDir Path directory) throws Exception {
        String output = run("""
                channel := Channel.bytes()
                reader := TextReader(channel)
                reader.close()
                TextWriter(Stdio.stdout()).writeLine("channelOpen=" .. channel.isOpen())
                try {
                    line := reader.readLine()
                } catch (error: Exception) {
                    TextWriter(Stdio.stdout()).writeLine("closed="
                        \\ .. (error.getCode() == IoError.Closed))
                }
                """, directory);

        assertThat(output).isEqualTo("channelOpen=true\nclosed=true\n");
    }

    @Test
    @DisplayName("write translates nothing and writeLine appends exactly the platform terminator")
    void write_line_uses_the_published_terminator(@TempDir Path directory) throws Exception {
        String output = run("""
                channel := Channel.bytes()
                writer := TextWriter(channel)
                writer.write("a\\nb")
                writer.writeLine("c")
                writer.close()
                back := channel.read(100)
                if back != Null {
                    TextWriter(Stdio.stdout()).writeLine("bytes=" .. back)
                }
                TextWriter(Stdio.stdout()).writeLine("ending=" .. TextWriter.LINE_ENDING)
                """, directory);

        String terminator = System.lineSeparator();
        assertThat(output).isEqualTo("bytes=[97, 10, 98, 99" + byteList(terminator) + "]\nending="
                + terminator.replace("\r\n", "\n") + "\n");
    }

    private static String byteList(String text) {
        StringBuilder builder = new StringBuilder();
        for (byte value : text.getBytes(StandardCharsets.UTF_8)) {
            builder.append(", ").append(value & 0xFF);
        }
        return builder.toString();
    }

    @Test
    @DisplayName("writeLine exercises a literal newline in the argument and an empty line")
    void write_line_does_not_deduplicate_terminators(@TempDir Path directory) throws Exception {
        String output = run("""
                channel := Channel.bytes()
                writer := TextWriter(channel)
                writer.writeLine("a\\n")
                writer.writeLine("")
                writer.close()
                back := channel.read(100)
                if back != Null {
                    TextWriter(Stdio.stdout()).writeLine("count=" .. back.size)
                }
                """, directory);

        int expected = ("a\n" + System.lineSeparator() + System.lineSeparator())
                .getBytes(StandardCharsets.UTF_8).length;
        assertThat(output).isEqualTo("count=" + expected + "\n");
    }

    @Test
    @DisplayName("closing a writer is idempotent, leaves the channel open, and rejects a write")
    void writer_close_leaves_the_channel_open(@TempDir Path directory) throws Exception {
        String output = run("""
                channel := Channel.bytes()
                writer := TextWriter(channel)
                writer.write("x")
                writer.close()
                writer.close()
                try {
                    writer.write("y")
                } catch (error: Exception) {
                    TextWriter(Stdio.stdout()).writeLine("closed="
                        \\ .. (error.getCode() == IoError.Closed))
                }
                TextWriter(Stdio.stdout()).writeLine("channelOpen=" .. channel.isOpen())
                back := channel.read(10)
                if back != Null {
                    TextWriter(Stdio.stdout()).writeLine("bytes=" .. back)
                }
                """, directory);

        assertThat(output).isEqualTo("closed=true\nchannelOpen=true\nbytes=[120]\n");
    }

    @Test
    @DisplayName("a very large read request returns the available bytes without a huge allocation")
    void read_bounds_its_chunk(@TempDir Path directory) throws Exception {
        String output = run("""
                channel := Channel.bytes()
                sent := channel.write(Byte[1] {65})
                part := channel.read(2147483647)
                if part != Null {
                    TextWriter(Stdio.stdout()).writeLine("bytes=" .. part .. " sent=" .. sent)
                }
                """, directory);

        assertThat(output).isEqualTo("bytes=[65] sent=1\n");
    }

    @Test
    @DisplayName("a temporary writer reaches the standard output without a later flush")
    void temporary_writer_is_visible(@TempDir Path directory) throws Exception {
        String output = run("""
                TextWriter(Stdio.stdout()).writeLine("hello")
                """, directory);

        assertThat(output).isEqualTo("hello\n");
    }

    @Test
    @DisplayName("the adapter and I/O types appear only in a program that reaches them")
    void io_support_is_emitted_on_demand(@TempDir Path directory) throws Exception {
        Map<String, String> plain = transpile("value := 1\n", directory);
        assertThat(plain).doesNotContainKey(GeneratedNames.IO_SOURCE_PATH);
        assertThat(PackagedLibrary.programFiles(plain)).containsExactly("Solvik$Program.java");
        assertThat(plain).doesNotContainKeys("Channel.java", "Stdio.java", "TextReader.java",
                "TextWriter.java", "IoError.java", "File.java", "ByteReader.java",
                "ByteWriter.java");

        Path ioDirectory = Files.createDirectories(directory.resolve("io"));
        Map<String, String> io = transpile("""
                out := TextWriter(Stdio.stdout())
                out.writeLine("io")
                """, ioDirectory);
        assertThat(io).containsKeys(GeneratedNames.IO_SOURCE_PATH, "TextWriter.java",
                "Stdio.java", "Channel.java");
        assertThat(io.get(GeneratedNames.IO_SOURCE_PATH))
                .contains("public final class Solvik$Io")
                .contains("public static synchronized Object stdout()");
    }

    @Test
    @DisplayName("LINE_ENDING is a static immutable member, not instance state")
    void line_ending_access_rules(@TempDir Path directory) throws Exception {
        assertThat(compilationError("""
                writer := TextWriter(Stdio.stdout())
                value := writer.LINE_ENDING
                """, directory))
                .contains("SOL-T001")
                .contains("is static");

        assertThat(compilationError("TextWriter.LINE_ENDING = \"x\"\n", directory))
                .contains("SOL-T010")
                .contains("immutable");
    }
}
