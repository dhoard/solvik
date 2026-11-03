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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end runs of {@code for ... in} over a declared iteration protocol (§7, §11.3).
 *
 * <p>Every case compiles the generated Java with an empty dependency classpath under
 * {@code -Xlint:all -Werror}, so the adapter and the emitted loop must be warning-free as well as
 * correct. The array cases stay here to prove the unchanged path still runs.
 */
@DisplayName("collection iteration executed")
class CollectionIterationExecutionTest {

    private record Outcome(String stdout, String stderr, Throwable failure) { }

    private static Outcome run(String source, Path directory) throws Exception {
        Map<String, String> files = new SolvikCompiler("iteration.sol").transpile(source);
        Path sources = Files.createDirectories(directory.resolve("src"));
        Path classes = Files.createDirectories(directory.resolve("classes"));
        Path emptyClasspath = Files.createDirectories(directory.resolve("empty-classpath"));
        List<String> arguments = new ArrayList<>(List.of("--release", "25", "-Xlint:all",
                "-Werror", "-classpath", emptyClasspath.toString(), "-d", classes.toString()));
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path path = sources.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, file.getValue(), StandardCharsets.UTF_8);
            arguments.add(path.toString());
        }
        assertThat(ToolProvider.getSystemJavaCompiler()
                .run(null, null, null, arguments.toArray(String[]::new)))
                .describedAs("javac on the generated sources").isZero();

        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        Throwable failure = null;
        try (URLClassLoader loader = new URLClassLoader(new URL[]{classes.toUri().toURL()}, null);
                PrintStream output = new PrintStream(out, true, StandardCharsets.UTF_8);
                PrintStream errors = new PrintStream(err, true, StandardCharsets.UTF_8)) {
            System.setOut(output);
            System.setErr(errors);
            try {
                loader.loadClass(GeneratedNames.PROGRAM_BINARY_NAME)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            } catch (InvocationTargetException exception) {
                failure = exception.getCause();
            } finally {
                System.setOut(originalOut);
                System.setErr(originalErr);
            }
        }
        return new Outcome(out.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"),
                err.toString(StandardCharsets.UTF_8), failure);
    }

    private static void prints(String source, String expected, Path directory) throws Exception {
        Outcome outcome = run(source, directory);
        assertThat(outcome.failure()).describedAs("program failure: %s", outcome.failure()).isNull();
        assertThat(outcome.stderr()).isEmpty();
        assertThat(outcome.stdout()).isEqualTo(expected);
    }

    @Test
    @DisplayName("a List and a Stack iterate in their own order with non-null elements")
    void iterates_a_list_and_a_stack(@TempDir Path directory) throws Exception {
        prints("""
                values := solvik::collection::List<Integer>()
                values.add(3)
                values.add(4)
                values.add(5)
                mut total := 0
                for value in values {
                    total = total + value
                }

                words := solvik::collection::Stack<String>()
                words.push("a")
                words.push("b")
                mut joined := ""
                for word in words {
                    joined = joined .. word
                }

                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("list: ")
                    \\ .. (total) .. (" stack: ") .. (joined) .. (" size: ") .. (values.size()))
                """, "list: 12 stack: ab size: 3\n", directory);
    }

    @Test
    void an_empty_collection_iterates_zero_times(@TempDir Path directory) throws Exception {
        prints("""
                values := solvik::collection::List<Integer>()
                mut seen := 0
                for value in values {
                    seen = seen + 1
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("empty: ") .. (seen))
                """, "empty: 0\n", directory);
    }

    @Test
    @DisplayName("a nullable element type keeps its Null, so the body narrows it first")
    void nullable_elements_keep_their_nullability(@TempDir Path directory) throws Exception {
        prints("""
                names := solvik::collection::List<String?>()
                names.add("ada")
                names.add(Null)
                names.add("grace")
                mut letters := 0
                for name in names {
                    if name != Null {
                        letters = letters + name.size()
                    }
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("letters: ") .. (letters))
                """, "letters: 8\n", directory);
    }

    @Test
    @DisplayName("break and continue keep their meaning inside a collection loop")
    void loop_control_works_in_a_collection_loop(@TempDir Path directory) throws Exception {
        prints("""
                values := solvik::collection::List<Integer>()
                values.add(1)
                values.add(2)
                values.add(3)
                values.add(4)
                values.add(5)
                mut total := 0
                for value in values {
                    if value == 3 {
                        continue
                    }
                    if value == 5 {
                        break
                    }
                    total = total + value
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("total: ") .. (total))
                """, "total: 7\n", directory);
    }

    @Test
    @DisplayName("a raise inside a collection loop unwinds to the enclosing catch")
    void a_raise_inside_a_collection_loop_unwinds(@TempDir Path directory) throws Exception {
        prints("""
                func reject(value: Integer) {
                    raise Exception(41, "rejected " .. value)
                }
                values := solvik::collection::List<Integer>()
                values.add(1)
                values.add(2)
                values.add(3)
                mut seen := 0
                try {
                    for value in values {
                        if value == 2 {
                            reject(value)
                        }
                        seen = seen + 1
                    }
                } catch (error: Exception) {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("caught: ")
                        \\ .. (error.getCode()) .. (" after ") .. (seen))
                }
                """, "caught: 41 after 1\n", directory);
    }

    @Test
    @DisplayName("a user type participates by declaring iterator()")
    void a_user_type_participates(@TempDir Path directory) throws Exception {
        prints("""
                type Roster {
                    pub names := solvik::collection::List<String>()

                    pub func Roster() { }

                    pub func iterator() -> solvik::collection::Iterator<String> {
                        return self.names.iterator()
                    }
                }
                roster := Roster()
                roster.names.add("ada")
                roster.names.add("grace")
                mut letters := 0
                for name in roster {
                    letters = letters + name.size()
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("roster: ") .. (letters))
                """, "roster: 8\n", directory);
    }

    @Test
    @DisplayName("the iterated expression is evaluated exactly once")
    void the_iterable_is_evaluated_once(@TempDir Path directory) throws Exception {
        prints("""
                type Source {
                    pub mut calls := 0

                    pub func Source() { }

                    pub func items() -> solvik::collection::List<Integer> {
                        calls = calls + 1
                        values := solvik::collection::List<Integer>()
                        values.add(1)
                        values.add(2)
                        return values
                    }
                }
                source := Source()
                mut total := 0
                for value in source.items() {
                    total = total + value
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("once: ")
                    \\ .. (total) .. (" calls: ") .. (source.calls))
                """, "once: 3 calls: 1\n", directory);
    }

    @Test
    @DisplayName("a type parameter element is declared with the type variable")
    void a_type_parameter_element_is_iterated(@TempDir Path directory) throws Exception {
        prints("""
                type Box<T> {
                    pub items := solvik::collection::List<T>()

                    pub func Box(seed: T) {
                        items.add(seed)
                    }

                    pub func count() -> Integer {
                        mut seen := 0
                        for value in self.items {
                            seen = seen + 1
                        }
                        return seen
                    }
                }
                box := Box(7)
                box.items.add(8)
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("box: ") .. (box.count()))
                """, "box: 2\n", directory);
    }

    @Test
    @DisplayName("mutating the collection during iteration propagates the JDK's own failure")
    void a_mutation_during_iteration_propagates_unchanged(@TempDir Path directory)
            throws Exception {
        Outcome outcome = run("""
                values := solvik::collection::List<Integer>()
                values.add(1)
                values.add(2)
                for value in values {
                    values.add(value)
                }
                """, directory);

        assertThat(outcome.failure()).isNotNull();
        assertThat(outcome.failure().getClass().getName())
                .isEqualTo("java.util.ConcurrentModificationException");
    }

    @Test
    @DisplayName("an array loop keeps its unchanged shape and behavior")
    void an_array_loop_still_runs(@TempDir Path directory) throws Exception {
        prints("""
                mut total := 0
                for value in Integer[3] {4, 5, 6} {
                    total = total + value
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("array: ") .. (total))
                """, "array: 15\n", directory);
    }
}
