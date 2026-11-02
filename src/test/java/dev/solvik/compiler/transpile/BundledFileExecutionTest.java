package dev.solvik.compiler.transpile;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.SolvikCompiler;
import dev.solvik.compiler.diagnostic.Diagnostics;
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
 * The bundled {@code File}, {@code ByteReader}, and {@code ByteWriter} types.
 *
 * <p>Each test compiles a program against the packaged library and runs the generated Java with an
 * empty dependency classpath. File paths come from JUnit's temporary directory, so no test reads or
 * writes repository data, and every program deletes what it creates (the temporary directory is
 * removed by JUnit as well).
 */
@DisplayName("bundled File and byte I/O executed")
class BundledFileExecutionTest {

    private static String path(Path file) {
        return file.toAbsolutePath().toString()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private static Map<String, String> transpile(String entry, Path directory) throws Exception {
        Path main = directory.resolve("main.sol");
        Files.createDirectories(directory);
        Files.writeString(main, entry, StandardCharsets.UTF_8);
        ProgramLoader.Loaded loaded = new ProgramLoader(new Diagnostics()).load(main);
        return new SolvikCompiler("main.sol").transpile(loaded);
    }

    private static String compilationError(String entry, Path directory) throws Exception {
        Path main = directory.resolve("main.sol");
        Files.createDirectories(directory);
        Files.writeString(main, entry, StandardCharsets.UTF_8);
        ProgramLoader.Loaded loaded = new ProgramLoader(new Diagnostics()).load(main);
        try {
            new SolvikCompiler("main.sol").transpile(loaded);
        } catch (SolvikCompiler.CompilationFailure failure) {
            return failure.getMessage();
        }
        throw new AssertionError("expected the program to be rejected");
    }

    private static String run(String entry, Path directory) throws Exception {
        Map<String, String> files = transpile(entry, directory);
        Path sources = Files.createDirectories(directory.resolve("src"));
        Path classes = Files.createDirectories(directory.resolve("classes"));
        List<String> arguments = new ArrayList<>(
                List.of("--release", "25", "-proc:none", "-d", classes.toString()));
        for (Map.Entry<String, String> file : new LinkedHashMap<>(files).entrySet()) {
            Path target = sources.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue(), StandardCharsets.UTF_8);
            arguments.add(target.toString());
        }
        assertThat(ToolProvider.getSystemJavaCompiler()
                .run(null, null, null, arguments.toArray(String[]::new))).isZero();

        PrintStream originalOut = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{classes.toUri().toURL()},
                BundledFileExecutionTest.class.getClassLoader())) {
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
    @DisplayName("write, append, and read round-trip a file through both byte wrappers")
    void file_write_append_read_lifecycle(@TempDir Path directory) throws Exception {
        String path = path(directory.resolve("lifecycle.bin"));
        String output = run("""
                path := "%s"
                file := solvik::io::File.openWrite(path)
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("path=" .. (file.path() == path)
                    \\ .. " open=" .. file.isOpen())
                writer := solvik::io::ByteWriter(file.channel())
                writer.write(Byte[3] {65, 66, 67})
                writer.close()
                file.close()
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("closed=" .. (file.isOpen() == false))

                appended := solvik::io::File.openAppend(path)
                solvik::io::ByteWriter(appended.channel()).write(Byte[1] {68})
                appended.close()

                readFile := solvik::io::File.openRead(path)
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("readable=" .. readFile.channel().canRead()
                    \\ .. " writable=" .. readFile.channel().canWrite())
                reader := solvik::io::ByteReader(readFile.channel())
                all := reader.read(10)
                if all != Null {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("bytes=" .. all)
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("eof=" .. (reader.read(1) == Null))
                reader.close()
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("channelOpen="
                    \\ .. readFile.channel().isOpen())
                readFile.close()
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("exists=" .. solvik::io::File.exists(path)
                    \\ .. " deleted=" .. solvik::io::File.delete(path) .. " after=" .. solvik::io::File.exists(path))
                """.formatted(path), directory);

        assertThat(output).isEqualTo("""
                path=true open=true
                closed=true
                readable=true writable=false
                bytes=[65, 66, 67, 68]
                eof=true
                channelOpen=true
                exists=true deleted=true after=false
                """);
        assertThat(Files.exists(directory.resolve("lifecycle.bin"))).isFalse();
    }

    @Test
    @DisplayName("openWrite truncates, openAppend creates, and read reports chunks then EOF")
    void open_modes_and_byte_chunks(@TempDir Path directory) throws Exception {
        String path = path(directory.resolve("modes.bin"));
        String output = run("""
                path := "%s"
                first := solvik::io::File.openWrite(path)
                solvik::io::ByteWriter(first.channel()).write(Byte[3] {97, 98, 99})
                first.close()

                truncated := solvik::io::File.openWrite(path)
                solvik::io::ByteWriter(truncated.channel()).write(Byte[1] {122})
                truncated.close()

                created := solvik::io::File.openAppend(path)
                solvik::io::ByteWriter(created.channel()).write(Byte[2] {121, 120})
                created.close()

                readFile := solvik::io::File.openRead(path)
                reader := solvik::io::ByteReader(readFile.channel())
                head := reader.read(2)
                tail := reader.read(2)
                if head != Null && tail != Null {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("head=" .. head .. " tail=" .. tail
                        \\ .. " eof=" .. (reader.read(1) == Null))
                }
                reader.close()
                readFile.close()
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("deleted=" .. solvik::io::File.delete(path))
                """.formatted(path), directory);

        assertThat(output).isEqualTo("head=[122, 121] tail=[120] eof=true\ndeleted=true\n");
    }

    @Test
    @DisplayName("byte values 0x00, 0x7F, 0x80, and 0xFF survive a file round trip")
    void byte_values_round_trip(@TempDir Path directory) throws Exception {
        String path = path(directory.resolve("bytes.bin"));
        String output = run("""
                path := "%s"
                file := solvik::io::File.openWrite(path)
                solvik::io::ByteWriter(file.channel()).write(Byte[4] {0, 127, -128, -1})
                file.close()

                readFile := solvik::io::File.openRead(path)
                reader := solvik::io::ByteReader(readFile.channel())
                back := reader.read(4)
                if back != Null {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("bytes=" .. back)
                }
                reader.close()
                readFile.close()
                cleaned := solvik::io::File.delete(path)
                """.formatted(path), directory);

        assertThat(output).isEqualTo("bytes=[0, 127, -128, -1]\n");
    }

    @Test
    @DisplayName("a read handle is not writable and a write handle is not readable")
    void file_channel_capabilities(@TempDir Path directory) throws Exception {
        String path = path(directory.resolve("directions.bin"));
        String output = run("""
                path := "%s"
                writeFile := solvik::io::File.openWrite(path)
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("write=" .. writeFile.channel().canWrite()
                    \\ .. " read=" .. writeFile.channel().canRead())
                writeFile.close()

                readFile := solvik::io::File.openRead(path)
                try {
                    ignored := readFile.channel().write(Byte[1] {65})
                } catch (error: Exception) {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("readOnly="
                        \\ .. (error.getCode() == solvik::io::IoError.Unsupported))
                }
                readFile.close()

                writeAgain := solvik::io::File.openAppend(path)
                try {
                    ignored := writeAgain.channel().read(1)
                } catch (error: Exception) {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("writeOnly="
                        \\ .. (error.getCode() == solvik::io::IoError.Unsupported))
                }
                writeAgain.close()
                cleaned := solvik::io::File.delete(path)
                """.formatted(path), directory);

        assertThat(output).isEqualTo("""
                write=true read=false
                readOnly=true
                writeOnly=true
                """);
    }

    @Test
    @DisplayName("closed files and wrappers raise Closed while borrowers leave the channel open")
    void close_semantics(@TempDir Path directory) throws Exception {
        String path = path(directory.resolve("close.bin"));
        String output = run("""
                path := "%s"
                file := solvik::io::File.openWrite(path)
                writer := solvik::io::ByteWriter(file.channel())
                writer.write(Byte[1] {65})
                writer.close()
                writer.close()
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("channelOpen=" .. file.channel().isOpen())
                try {
                    writer.write(Byte[1] {66})
                } catch (error: Exception) {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("writerClosed="
                        \\ .. (error.getCode() == solvik::io::IoError.Closed))
                }
                try {
                    writer.flush()
                } catch (error: Exception) {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("flushClosed="
                        \\ .. (error.getCode() == solvik::io::IoError.Closed))
                }
                savedChannel := file.channel()
                file.close()
                file.close()
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("fileOpen=" .. file.isOpen()
                    \\ .. " ownedClosed=" .. (savedChannel.isOpen() == false))
                try {
                    ignored := file.channel()
                } catch (error: Exception) {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("fileClosed="
                        \\ .. (error.getCode() == solvik::io::IoError.Closed))
                }
                try {
                    ignored := savedChannel.read(1)
                } catch (error: Exception) {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("ownedChannel="
                        \\ .. (error.getCode() == solvik::io::IoError.Closed))
                }

                readFile := solvik::io::File.openRead(path)
                reader := solvik::io::ByteReader(readFile.channel())
                reader.close()
                reader.close()
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("readChannelOpen="
                    \\ .. readFile.channel().isOpen())
                try {
                    ignored := reader.read(1)
                } catch (error: Exception) {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("readerClosed="
                        \\ .. (error.getCode() == solvik::io::IoError.Closed))
                }
                readFile.close()
                cleaned := solvik::io::File.delete(path)
                """.formatted(path), directory);

        assertThat(output).isEqualTo("""
                channelOpen=true
                writerClosed=true
                flushClosed=true
                fileOpen=false ownedClosed=true
                fileClosed=true
                ownedChannel=true
                readChannelOpen=true
                readerClosed=true
                """);
    }

    @Test
    @DisplayName("missing and malformed paths raise the documented codes")
    void path_failures_are_io_errors(@TempDir Path directory) throws Exception {
        String missing = path(directory.resolve("missing.bin"));
        String output = run("""
                try {
                    missing := solvik::io::File.openRead("%s")
                } catch (error: Exception) {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("missing="
                        \\ .. (error.getCode() == solvik::io::IoError.Io))
                }
                try {
                    ignored := solvik::io::File.exists("")
                } catch (error: Exception) {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("invalid="
                        \\ .. (error.getCode() == solvik::io::IoError.InvalidArgument))
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("deletedAbsent="
                    \\ .. solvik::io::File.delete("%s"))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("exists=" .. solvik::io::File.exists("%s"))
                """.formatted(missing, missing, missing), directory);

        assertThat(output).isEqualTo("""
                missing=true
                invalid=true
                deletedAbsent=false
                exists=false
                """);
    }

    @Test
    @DisplayName("delete refuses a directory instead of removing it")
    void delete_rejects_a_directory(@TempDir Path directory) throws Exception {
        Path folder = Files.createDirectories(directory.resolve("folder"));
        String output = run("""
                path := "%s"
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("exists=" .. solvik::io::File.exists(path))
                try {
                    removed := solvik::io::File.delete(path)
                } catch (error: Exception) {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("directory="
                        \\ .. (error.getCode() == solvik::io::IoError.Io))
                }
                """.formatted(path(folder)), directory);

        assertThat(output).isEqualTo("exists=true\ndirectory=true\n");
        assertThat(Files.isDirectory(folder)).isTrue();
    }

    @Test
    @DisplayName("a file channel installed as stdout is not closed by the setter")
    void stdio_does_not_close_an_installed_file(@TempDir Path directory) throws Exception {
        String path = path(directory.resolve("stdio.bin"));
        String output = run("""
                path := "%s"
                saved := solvik::io::Stdio.stdout()
                file := solvik::io::File.openWrite(path)
                solvik::io::Stdio.setStdout(file.channel())
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("installed")
                solvik::io::Stdio.setStdout(saved)
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("fileOpen=" .. file.isOpen())

                readFile := solvik::io::File.openRead(path)
                line := solvik::io::TextReader(readFile.channel()).readLine()
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("line=" .. line)
                readFile.close()
                file.close()
                cleaned := solvik::io::File.delete(path)
                """.formatted(path), directory);

        assertThat(output).isEqualTo("fileOpen=true\nline=installed\n");
    }

    @Test
    @DisplayName("the File constructor and the open helpers are not public API")
    void construction_and_bindings_are_not_exported(@TempDir Path directory) throws Exception {
        assertThat(compilationError("forged := solvik::io::File(solvik::io::Channel.bytes(), \"x\")\n", directory))
                .contains("SOL-T001")
                .contains("the constructor of 'solvik::io::File' is private");
        assertThat(compilationError("channel := solvik::io::File.openReadChannel(\"x\")\n", directory))
                .contains("SOL-T001")
                .contains("method 'openReadChannel' of solvik::io::File is private");
    }

    @Test
    @DisplayName("the new types and adapter are emitted only when reached")
    void file_support_is_emitted_on_demand(@TempDir Path directory) throws Exception {
        Map<String, String> plain = transpile("value := 1\n", directory);
        assertThat(plain).doesNotContainKey(GeneratedNames.IO_SOURCE_PATH);
        assertThat(PackagedLibrary.programFiles(plain)).containsExactly("Solvik$Program.java");
        assertThat(plain).doesNotContainKeys("solvik/io/File.java", "solvik/io/ByteReader.java", "solvik/io/ByteWriter.java");

        Path fileDirectory = Files.createDirectories(directory.resolve("file"));
        Map<String, String> withFile = transpile("""
                file := solvik::io::File.openWrite("unused.tmp")
                file.close()
                cleaned := solvik::io::File.delete("unused.tmp")
                """, fileDirectory);
        assertThat(withFile).containsKeys(GeneratedNames.IO_SOURCE_PATH, "solvik/io/File.java",
                "solvik/io/Channel.java", "solvik/io/IoError.java");
        // The wrappers are not mentioned by this program, so they stay unemitted.
        assertThat(withFile).doesNotContainKeys("solvik/io/ByteReader.java", "solvik/io/ByteWriter.java");

        Path readerDirectory = Files.createDirectories(directory.resolve("reader"));
        Map<String, String> withReader = transpile("""
                reader := solvik::io::ByteReader(solvik::io::Channel.bytes())
                reader.close()
                """, readerDirectory);
        assertThat(withReader).containsKeys("solvik/io/ByteReader.java", "solvik/io/Channel.java");
        assertThat(withReader).doesNotContainKeys("solvik/io/File.java", "solvik/io/ByteWriter.java");
    }

    @Test
    @DisplayName("the public File class names no JDK stream or path type")
    void file_public_shape_is_solvik_only(@TempDir Path directory) throws Exception {
        Map<String, String> files = transpile("""
                file := solvik::io::File.openWrite("unused.tmp")
                file.close()
                cleaned := solvik::io::File.delete("unused.tmp")
                """, directory);

        assertThat(files.get("solvik/io/File.java"))
                .contains("public static File openRead(String path)")
                .contains("public static File openWrite(String path)")
                .contains("public static File openAppend(String path)")
                .contains("public static Boolean exists(String path)")
                .contains("public static Boolean delete(String path)")
                .contains("public Channel channel()")
                .doesNotContain("java.io", "java.nio", "FileInputStream", "FileOutputStream");
        assertThat(files.get(GeneratedNames.IO_SOURCE_PATH))
                .contains("openFileRead", "openFileWrite", "openFileAppend", "fileExists",
                        "fileDelete", "java.nio.file.Files");
    }
}
