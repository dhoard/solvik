package dev.solvik.compiler.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.SolvikCompiler;
import dev.solvik.compiler.ast.Ast;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.lexer.Lexer;
import dev.solvik.compiler.parser.Parser;
import dev.solvik.compiler.testsupport.Checking;
import dev.solvik.compiler.transpile.GeneratedNames;
import dev.solvik.compiler.types.SolvikType;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The {@code T[]} array type spelling (LANGUAGE.md §6): every type position, nested positions,
 * nested literals, and rejection of the removed {@code Array<T>} spelling.
 */
@DisplayName("array type spelling")
class ArrayTypeSpellingTest {

    private static List<dev.solvik.compiler.diagnostic.Diagnostic> parseDiagnostics(String source) {
        Diagnostics diagnostics = new Diagnostics();
        new Parser(new Lexer(source, diagnostics).tokenize(), diagnostics).parseProgram();
        return diagnostics.all();
    }

    private static List<dev.solvik.compiler.diagnostic.Diagnostic> checkDiagnostics(String source) {
        Diagnostics diagnostics = new Diagnostics();
        var program = new Parser(new Lexer(source, diagnostics).tokenize(), diagnostics)
                .parseProgram();
        Checking.check(program, diagnostics);
        return diagnostics.all();
    }

    private record Outcome(String stdout, Throwable failure) {
    }

    /** Compiles the generated Java with an empty classpath, then runs it. */
    private static Outcome run(String source, Path directory) throws Exception {
        Map<String, String> files = new SolvikCompiler("array.sol").transpile(source);
        Path sources = Files.createDirectories(directory.resolve("src"));
        Path classes = Files.createDirectories(directory.resolve("classes"));
        Path emptyClasspath = Files.createDirectories(directory.resolve("empty-classpath"));
        List<String> arguments = new ArrayList<>(List.of("--release", "25", "-classpath",
                emptyClasspath.toString(), "-d", classes.toString()));
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path path = sources.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, file.getValue(), StandardCharsets.UTF_8);
            arguments.add(path.toString());
        }
        assertThat(ToolProvider.getSystemJavaCompiler()
                .run(null, null, null, arguments.toArray(String[]::new))).isZero();

        PrintStream originalOut = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Throwable failure = null;
        try (URLClassLoader loader = new URLClassLoader(new URL[]{classes.toUri().toURL()}, null);
                PrintStream output = new PrintStream(out, true, StandardCharsets.UTF_8)) {
            System.setOut(output);
            try {
                loader.loadClass(GeneratedNames.PROGRAM_BINARY_NAME)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            } catch (InvocationTargetException exception) {
                failure = exception.getCause();
            } finally {
                System.setOut(originalOut);
            }
        }
        return new Outcome(out.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"), failure);
    }

    @Test
    @DisplayName("T[] works in parameters, returns, native declarations, and generic arguments")
    void array_types_work_in_every_type_position(@TempDir Path directory) throws Exception {
        Outcome outcome = run("""
                trait Counter native "java.lang.AutoCloseable" {
                    pub func close()
                    pub func count(values: Integer[]) -> Integer { return values.size }
                }
                type Job implements Counter {
                    pub func Job() { }
                    pub func close() { }
                }
                type Box<T> {
                    pub func Box(seed: T) { }
                    pub func first(items: T[]) -> T { return items[0] }
                }
                func flatten(rows: Integer[][]) -> Integer {
                    return rows[0][1] + rows[1].size
                }
                numbers := Integer[2] {1, 2}
                grid := Integer[][2] { Integer[2] {1, 2}, Integer[2] {3, 4} }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Job().count(numbers)) .. (" ") .. (Box("x").first(String[1] {"x"})) .. (" ") .. (flatten(grid)))
                lists := solvik::collections::List<Integer[]>()
                lists.add(grid[0])
                mut stored: Integer[]? = lists.get(0)
                if stored != Null {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((stored.size) .. "")
                }
                """, directory);

        assertThat(outcome.failure()).isNull();
        assertThat(outcome.stdout()).isEqualTo("2 x 4\n2\n");
    }

    @Test
    @DisplayName("a nested literal spells the array as the element type followed by its size")
    void nested_array_literals_use_the_element_type_with_a_size(@TempDir Path directory)
            throws Exception {
        Outcome outcome = run("""
                grid := Integer[][2] { Integer[2] {1, 2}, Integer[2] {3, 4} }
                cube := Integer?[2][2][1] { Integer?[2][2] {
                    Integer?[2] {5, 6}, Integer?[2] {7, Null} } }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((grid[0][1]) .. (grid[1].size) .. (" ") .. (cube[0][1][0]) .. (" ") .. (cube.size))
                """, directory);

        assertThat(outcome.failure()).isNull();
        assertThat(outcome.stdout()).isEqualTo("22 7 1\n");
    }

    @Test
    @DisplayName("Array<T> is rejected with a diagnostic that shows the bracket spellings")
    void the_removed_generic_spelling_is_rejected() {
        var simple = parseDiagnostics("func f(values: Array<Integer>) -> Integer { return 1 }");
        assertThat(simple).extracting(d -> d.code()).containsExactly(Parser.ARRAY_SPELLING);
        assertThat(simple.getFirst().message())
                .startsWith("the 'Array<T>' array spelling was removed; write 'Integer[]'");

        var nested = parseDiagnostics("func f(values: Array<Array<Byte>>) { }");
        assertThat(nested).extracting(d -> d.code())
                .containsOnly(Parser.ARRAY_SPELLING);
        assertThat(nested.stream().map(d -> d.message()).toList())
                .anySatisfy(message -> assertThat(message).contains("Byte[]"));

        var literal = parseDiagnostics("grid := Array<Integer>[2] { Integer[2] {1, 2} }");
        assertThat(literal).extracting(d -> d.code()).contains(Parser.ARRAY_SPELLING);
        assertThat(literal.stream().map(d -> d.message()).toList())
                .anySatisfy(message -> assertThat(message).contains("Integer[]"));
    }

    @Test
    @DisplayName("diagnostics render array types with brackets")
    void diagnostics_render_bracket_spelling() {
        var diagnostics = checkDiagnostics("""
                func first(values: Integer[]) -> Integer { return values[0] }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((first(1)) .. "")
                """);

        assertThat(diagnostics.stream().map(d -> d.message()).toList())
                .anySatisfy(message -> assertThat(message).contains("Integer[]"));
    }

    @Test
    @DisplayName("Array is an ordinary user type name, while Array<T> stays the removed spelling")
    void array_is_an_ordinary_user_type_name(@TempDir Path directory) throws Exception {
        Outcome outcome = run("""
                type Array {
                    count := 0
                    pub func Array() { }
                    pub func value() -> Integer { return count }
                }
                a := Array()
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((a.type()) .. (" ") .. (a.value()))
                """, directory);

        assertThat(outcome.failure()).isNull();
        assertThat(outcome.stdout()).isEqualTo("Array 0\n");
    }

    @Test
    @DisplayName("Array is no longer a built-in type name")
    void array_is_not_a_builtin_type() {
        var diagnostics = checkDiagnostics("func f(values: Array) { }");

        assertThat(diagnostics).extracting(d -> d.code()).contains(TypeChecker.TYPE_ERROR);
        assertThat(diagnostics).allSatisfy(diagnostic ->
                assertThat(diagnostic.message()).contains("unknown type 'Array'"));
    }

    @Test
    @DisplayName("Solvik and AST types render arrays with brackets")
    void written_and_semantic_types_render_brackets() {
        assertThat(SolvikType.array(SolvikType.BYTE)).hasToString("Byte[]");
        assertThat(SolvikType.array(SolvikType.array(SolvikType.BYTE))).hasToString("Byte[][]");
        assertThat(SolvikType.user("Map", List.of(SolvikType.STRING,
                SolvikType.array(SolvikType.BYTE)))).hasToString("Map<String, Byte[]>");

        var parsed = new Parser(new Lexer("func f(x: Map<String, Byte[]>) { }", new Diagnostics())
                .tokenize(), new Diagnostics()).parseProgram();
        var type = (Ast.TypeRef.Named) parsed.functions().getFirst().params().getFirst().type();
        assertThat(type.name()).isEqualTo("Map");
        assertThat(type.arguments()).hasSize(2);
        assertThat(type.arguments().get(1)).isInstanceOf(Ast.TypeRef.Array.class);
        assertThat(type.arguments().get(1).toString()).isEqualTo("Byte[]");
    }
}
