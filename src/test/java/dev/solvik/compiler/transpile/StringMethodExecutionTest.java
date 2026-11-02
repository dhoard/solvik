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

/** End-to-end runs of the String methods (§5, "String operations"). */
@DisplayName("string methods executed")
class StringMethodExecutionTest {

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
    void size_and_the_three_strips_are_measured(@TempDir Path directory) throws Exception {
        String output = run("""
                text := "  padded  "
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((text.size()) .. "")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("[" .. text.strip() .. "]") .. "")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("[" .. text.stripLeading() .. "]") .. "")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("[" .. text.stripTrailing() .. "]") .. "")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("[" .. "   ".strip() .. "]") .. (" ") .. ("   ".strip().size()))
                """, directory);

        assertThat(output).isEqualTo("""
                10
                [padded]
                [padded  ]
                [  padded]
                [] 0
                """);
    }

    @Test
    void case_and_replace_work_on_the_whole_text(@TempDir Path directory) throws Exception {
        String output = run("""
                text := "Hello, World"
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((text.toUpperCase()) .. "")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((text.toLowerCase()) .. "")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((text.replace("World", "Solvik")) .. "")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((text.replace(",", "")) .. "")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("no match: ") .. (text.replace("zzz", "y")))
                """, directory);

        assertThat(output).isEqualTo("""
                HELLO, WORLD
                hello, world
                Hello, Solvik
                Hello World
                no match: Hello, World
                """);
    }

    @Test
    void substring_takes_a_start_and_an_optional_end(@TempDir Path directory) throws Exception {
        String output = run("""
                text := "abcdef"
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((text.substring(2)) .. "")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((text.substring(2, 4)) .. "")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("[" .. text.substring(6) .. "]") .. "")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("[" .. text.substring(0, 0) .. "]") .. "")
                """, directory);

        assertThat(output).isEqualTo("cdef\ncd\n[]\n[]\n");
    }

    @Test
    @DisplayName("indexOf reports -1 when the text is absent, rather than failing")
    void searching(@TempDir Path directory) throws Exception {
        String output = run("""
                text := "banana"
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((text.indexOf("an")) .. (" ") .. (text.indexOf("na")) .. (" ") .. (text.indexOf("zz")))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((text.contains("nan")) .. (" ") .. (text.contains("zz")))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((text.startsWith("ban")) .. (" ") .. (text.startsWith("nan")))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((text.endsWith("ana")) .. (" ") .. (text.endsWith("ban")))
                """, directory);

        assertThat(output).isEqualTo("1 2 -1\ntrue false\ntrue false\ntrue false\n");
    }

    @Test
    @DisplayName("split is literal, keeps empty parts, and keeps a trailing empty part")
    void splitting(@TempDir Path directory) throws Exception {
        String output = run("""
                parts := "a,b,,c".split(",")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((parts.size) .. (" ") .. (parts[0]) .. ("|") .. (parts[1]) .. ("|") .. (parts[2]) .. ("|") .. (parts[3]))
                trailing := "a,".split(",")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((trailing.size) .. (" [") .. (trailing[1]) .. ("]"))
                literal := "a.b".split(".")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((literal.size) .. (" ") .. (literal[0]) .. ("|") .. (literal[1]))
                none := "abc".split(",")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((none.size) .. (" ") .. (none[0]))
                """, directory);

        assertThat(output).isEqualTo("""
                4 a|b||c
                2 []
                2 a|b
                1 abc
                """);
    }

    @Test
    @DisplayName("a split result is an ordinary array: indexed, iterated, and writable")
    void split_results_are_arrays(@TempDir Path directory) throws Exception {
        String output = run("""
                words := "one two three".split(" ")
                for word in words {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).write((word.toUpperCase()) .. ("-"))
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((words[1]) .. "")
                words[1] = words[1].replace("w", "W")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((words[1]) .. (" ") .. (words.size))
                """, directory);

        assertThat(output).isEqualTo("ONE-TWO-THREE-\ntwo\ntWo 3\n");
    }

    @Test
    void string_methods_chain_and_compose_with_switch(@TempDir Path directory) throws Exception {
        String output = run("""
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((" a b ".strip().split(" ")[1].toUpperCase()) .. "")

                path := "/api/users/42"
                switch path.substring(path.indexOf("/users/") + 7) {
                    case "42" {
                        solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("the answer") .. "")
                    }
                    default {
                        solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("something else") .. "")
                    }
                }
                """, directory);

        assertThat(output).isEqualTo("B\nthe answer\n");
    }

    @Test
    @DisplayName("an impossible substring aborts with SOL-R009, an empty separator with SOL-R010")
    void range_failures_report_their_codes(@TempDir Path directory) throws Exception {
        assertThat(abortCode("""
                text := "abc"
                index := 9
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((text.substring(index)) .. "")
                """, directory)).isEqualTo("SOL-R009");

        assertThat(abortCode("""
                text := "abc"
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((text.substring(2, 1)) .. "")
                """, directory)).isEqualTo("SOL-R009");

        assertThat(abortCode("""
                text := "abc"
                separator := ""
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((text.split(separator)) .. "")
                """, directory)).isEqualTo("SOL-R010");
    }

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
            System.setErr(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
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
        return (String) abort.getClass().getMethod("code").invoke(abort);
    }
}
