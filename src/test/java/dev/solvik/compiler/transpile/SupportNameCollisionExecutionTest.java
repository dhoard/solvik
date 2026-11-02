package dev.solvik.compiler.transpile;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.SolvikCompiler;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
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
 * A Solvik function may take a name the generated program class already uses for its own support
 * code ({@code LANGUAGE.md} §12).
 *
 * <p>The support members are emitted into the same class as top-level functions, so a same-named
 * overload would either duplicate a member or capture the support code's own calls. Lowering gives
 * such a function the source-inexpressible {@code $function} suffix, which keeps both the
 * declaration and every call to it distinct from the support member of the same name.
 */
@DisplayName("support member names and user functions coexist (§12)")
class SupportNameCollisionExecutionTest {

    private String run(String source, Path directory) throws Exception {
        Path sources = directory.resolve("src");
        Path classes = directory.resolve("classes");
        Files.createDirectories(sources);
        Files.createDirectories(classes);

        Map<String, String> files = new SolvikCompiler("test.sol").transpile(source);
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path path = sources.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, file.getValue());
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
    @DisplayName("a top-level function may share a support member's name")
    void a_top_level_function_may_take_a_support_name(@TempDir Path directory) throws Exception {
        String output = run("""
                func render(value: Any) -> String { return "user:" .. String(value) }
                func toLong(value: Integer) -> Integer { return value + 1 }
                func checked(value: Any) -> Any { return value }

                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((render("x")) .. "")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((toLong(1)) .. (" ") .. (checked("c")))

                // The support members still do their own work: rendering a dynamic value and an
                // array of dynamic values must not call the user functions above.
                number: Number = 5
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((number) .. "")
                values := Any[2] {1, "two"}
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((values) .. "")
                """, directory);

        assertThat(output).isEqualTo("""
                user:x
                2 c
                5
                [1, two]
                """);
    }

    @Test
    @DisplayName("a module function and a static member keep their own support-like names")
    void qualified_declarations_keep_their_support_like_names(@TempDir Path directory)
            throws Exception {
        String output = run("""
                module tools {
                    pub func plainText(value: Integer) -> String { return "module:" .. value }
                }
                type Helper {
                    pub func Helper() { }
                    static pub func render(value: Integer) -> String { return "static:" .. value }
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((tools::plainText(1)) .. (" ") .. (Helper.render(2)))
                """, directory);

        assertThat(output).isEqualTo("module:1 static:2\n");
    }
}
