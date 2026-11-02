package dev.solvik.compiler.transpile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.solvik.compiler.SolvikCompiler;
import dev.solvik.compiler.check.TypeChecker;
import dev.solvik.compiler.transpile.GeneratedNames;
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
 * Runs the declaration and nullability surface end to end ({@code LANGUAGE.md} §3, §5, §7.1).
 *
 * <p>The generated program uses the JDK alone: {@code T?} is a Java reference whose {@code null}
 * is the Solvik {@code Null} value, so no runtime library and no wrapper object is involved.
 */
@DisplayName("declarations and nullability executed")
class DeclarationExecutionTest {

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
    @DisplayName("the three declaration forms run, and mut is what allows assignment")
    void declaration_forms_run(@TempDir Path directory) throws Exception {
        String output = run("""
                count := 10
                mut total := count + 1
                total = total + 1
                name: String = "Solvik"
                mut label: String? = Null
                label = "ready"
                TextWriter(Stdio.stdout()).writeLine((count) .. (" ") .. (total) .. (" ") .. (name) .. (" ") .. (label))
                """, directory);

        assertThat(output).isEqualTo("10 12 Solvik ready\n");
    }

    @Test
    @DisplayName("Null prints as null and compares equal to Null, while a present value works")
    void null_is_a_value(@TempDir Path directory) throws Exception {
        String output = run("""
                nothing := Null
                mut maybe: Integer? = 1
                TextWriter(Stdio.stdout()).writeLine((nothing) .. (" ") .. (nothing == Null) .. (" ") .. (Null === Null) .. (" ") .. (String(Null)))
                TextWriter(Stdio.stdout()).writeLine((maybe) .. (" ") .. (maybe == Null))
                maybe = Null
                TextWriter(Stdio.stdout()).writeLine((maybe) .. (" ") .. (maybe == Null) .. (" ") .. (maybe != 1))
                maybe = 3
                TextWriter(Stdio.stdout()).writeLine((maybe) .. (" ") .. (maybe + 1))
                """, directory);

        assertThat(output).isEqualTo("""
                null true true null
                1 false
                null true true
                3 4
                """);
    }

    @Test
    @DisplayName("name? := infers T?, and name? := Null infers Any?")
    void nullable_inference_runs(@TempDir Path directory) throws Exception {
        String output = run("""
                mut inferred? := "hello"
                TextWriter(Stdio.stdout()).writeLine((inferred) .. "")
                inferred = Null
                TextWriter(Stdio.stdout()).writeLine((inferred) .. (" ") .. (inferred == Null))
                inferred = "world"
                TextWriter(Stdio.stdout()).writeLine((inferred) .. "")
                mut anything? := Null
                anything = "text"
                TextWriter(Stdio.stdout()).writeLine((anything) .. "")
                anything = 42
                TextWriter(Stdio.stdout()).writeLine((anything) .. "")
                """, directory);

        assertThat(output).isEqualTo("hello\nnull true\nworld\ntext\n42\n");
    }

    @Test
    @DisplayName("a null check proves presence, and the proven value is usable")
    void narrowing_runs(@TempDir Path directory) throws Exception {
        String output = run("""
                func describe(text: String?) -> String {
                    if text == Null {
                        return "absent"
                    }
                    return text.size() .. ":" .. text
                }

                mut label: String? = Null
                if label != Null {
                    TextWriter(Stdio.stdout()).writeLine(("unreachable") .. "")
                } else {
                    TextWriter(Stdio.stdout()).writeLine(("empty") .. "")
                }
                label = "ready"
                if label != Null && label.size() > 0 {
                    TextWriter(Stdio.stdout()).writeLine((label.toUpperCase()) .. "")
                }
                TextWriter(Stdio.stdout()).writeLine((describe(Null)) .. (" ") .. (describe("four")))

                mut count: Integer? = Null
                count = 7
                TextWriter(Stdio.stdout()).writeLine((count + 1) .. "")
                """, directory);

        assertThat(output).isEqualTo("""
                empty
                READY
                absent 4:four
                8
                """);
    }

    @Test
    @DisplayName("array nullability is tracked for the array and for its elements separately")
    void array_nullability_runs(@TempDir Path directory) throws Exception {
        String output = run("""
                plain := Integer[2] {1, 2}
                holes := Integer?[2] {1, Null}
                TextWriter(Stdio.stdout()).writeLine((plain[0] + plain[1]) .. (" ") .. (holes[0]) .. (" ") .. (holes[1] == Null))
                holes[1] = 9
                TextWriter(Stdio.stdout()).writeLine((holes[1]) .. "")

                mut optional: Integer[2]? = Integer[2] {3, 4}
                TextWriter(Stdio.stdout()).writeLine((optional == Null) .. "")
                optional = Null
                TextWriter(Stdio.stdout()).writeLine((optional == Null) .. "")

                partial := String?[3] {"a"}
                TextWriter(Stdio.stdout()).writeLine((partial.size) .. (" ") .. (partial))
                """, directory);

        assertThat(output).isEqualTo("""
                3 1 true
                9
                false
                true
                3 [a, null, null]
                """);
    }

    @Test
    @DisplayName("Null stops at the operations that need a value, before it can be evaluated")
    void nullable_use_is_a_compile_error() {
        assertThatThrownBy(() -> transpile("""
                mut value: Integer? = 1
                value = Null
                TextWriter(Stdio.stdout()).writeLine((value + 1) .. "")
                """)).isInstanceOf(SolvikCompiler.CompilationFailure.class)
                .hasMessageContaining(TypeChecker.NULL_UNSAFE);
        assertThatThrownBy(() -> transpile("""
                text: String? = Null
                TextWriter(Stdio.stdout()).writeLine((text.size()) .. "")
                """)).isInstanceOf(SolvikCompiler.CompilationFailure.class)
                .hasMessageContaining(TypeChecker.NULL_UNSAFE);
    }

    @Test
    @DisplayName("a nullable field is read as nullable, and a local copy proves presence")
    void nullable_field_runs(@TempDir Path directory) throws Exception {
        String output = run("""
                type Box {
                    pub mut value: String? = Null

                    pub func Box() { }

                    pub func textSize() -> Integer {
                        mut mine: String? = self.value
                        if mine == Null {
                            return 0
                        }
                        return mine.size()
                    }
                }
                box := Box()
                TextWriter(Stdio.stdout()).writeLine((box.textSize()) .. "")
                box.value = "text"
                TextWriter(Stdio.stdout()).writeLine((box.textSize()) .. (" ") .. (box.value))
                """, directory);

        assertThat(output).isEqualTo("0\n4 text\n");
    }

    private static Map<String, String> transpile(String source) {
        return new SolvikCompiler("test.sol").transpile(source);
    }
}
