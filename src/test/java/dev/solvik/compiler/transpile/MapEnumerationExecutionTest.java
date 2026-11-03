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
 * End-to-end runs of map enumeration through the declared iteration protocol (§7, §11.3).
 *
 * <p>{@code solvik::collection::Map.keys()} returns a {@code solvik::collection::Set<K>} and
 * {@code values()} a {@code solvik::collection::Collection<V>}; both publish {@code iterator()}, so
 * {@code for} reads them like any other packaged collection. Every case compiles the generated Java
 * with an empty dependency classpath under {@code -Xlint:all -Werror}, so the {@code $of} conversion
 * inside the two new trait adapters must be warning-free as well as correct.
 *
 * <p>A {@code java.util.HashMap} has no defined iteration order, so these cases assert totals,
 * sizes, and membership — never an order the JDK does not promise.
 */
@DisplayName("map enumeration executed")
class MapEnumerationExecutionTest {

    private record Outcome(String stdout, String stderr, Throwable failure) { }

    private static Outcome run(String source, Path directory) throws Exception {
        Map<String, String> files = new SolvikCompiler("enumeration.sol").transpile(source);
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
    @DisplayName("keys and values iterate with the map's own key and value types")
    void keys_and_values_iterate_without_assuming_an_order(@TempDir Path directory) throws Exception {
        prints("""
                scores := solvik::collection::Map<String, Integer>()
                scores.put("ann", 10)
                scores.put("bob", 20)
                scores.put("cid", 30)
                mut letters := 0
                for name in scores.keys() {
                    letters = letters + name.size()
                }
                mut total := 0
                for score in scores.values() {
                    total = total + score
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("letters: ")
                    \\ .. (letters) .. (" total: ") .. (total))
                """, "letters: 9 total: 60\n", directory);
    }

    @Test
    @DisplayName("a key or value view reports its size, membership, and emptiness")
    void the_views_expose_the_members_they_declare(@TempDir Path directory) throws Exception {
        prints("""
                scores := solvik::collection::Map<String, Integer>()
                scores.put("ann", 10)
                scores.put("bob", 20)
                keys := scores.keys()
                values := scores.values()
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("keys: ")
                    \\ .. (keys.size()) .. (" ") .. (keys.contains("bob")) .. (" ")
                    \\ .. (keys.contains("cid")) .. (" ") .. (keys.isEmpty()))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("values: ")
                    \\ .. (values.size()) .. (" ") .. (values.contains(20)) .. (" ")
                    \\ .. (values.contains(99)) .. (" ") .. (values.isEmpty()))
                """, "keys: 2 true false false\nvalues: 2 true false false\n", directory);
    }

    @Test
    @DisplayName("an empty map enumerates zero keys and zero values")
    void an_empty_map_iterates_zero_times(@TempDir Path directory) throws Exception {
        prints("""
                scores := solvik::collection::Map<String, Integer>()
                mut seen := 0
                for name in scores.keys() {
                    seen = seen + 1
                }
                for score in scores.values() {
                    seen = seen + 1
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("empty: ") .. (seen))
                """, "empty: 0\n", directory);
    }

    @Test
    @DisplayName("a nullable instantiation keeps its Null, so the body narrows it first")
    void nullable_instantiations_keep_their_nullability(@TempDir Path directory) throws Exception {
        prints("""
                scores := solvik::collection::Map<String?, Integer?>()
                scores.put("ann", 10)
                mut report := ""
                for name in scores.keys() {
                    if name != Null {
                        report = report .. name
                    }
                }
                for score in scores.values() {
                    if score != Null {
                        report = report .. ":" .. score + 1
                    }
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((report))
                """, "ann:11\n", directory);
    }

    @Test
    @DisplayName("a map of maps enumerates as the inner map type")
    void a_map_of_maps_iterates_as_the_inner_map(@TempDir Path directory) throws Exception {
        prints("""
                groups := solvik::collection::Map<String, solvik::collection::Map<String, Integer>>()
                inner := solvik::collection::Map<String, Integer>()
                inner.put("x", 5)
                inner.put("y", 6)
                groups.put("one", inner)
                mut total := 0
                mut names := 0
                for group in groups.values() {
                    for key in group.keys() {
                        names = names + key.size()
                    }
                    for value in group.values() {
                        total = total + value
                    }
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("groups: ")
                    \\ .. (names) .. (" total: ") .. (total))
                """, "groups: 2 total: 11\n", directory);
    }

    @Test
    @DisplayName("mutating the map during enumeration propagates the JDK's own failure")
    void a_mutation_during_enumeration_propagates_unchanged(@TempDir Path directory)
            throws Exception {
        Outcome outcome = run("""
                scores := solvik::collection::Map<String, Integer>()
                scores.put("ann", 10)
                scores.put("bob", 20)
                for name in scores.keys() {
                    scores.put(name .. "!", 1)
                }
                """, directory);

        assertThat(outcome.failure()).isNotNull();
        assertThat(outcome.failure().getClass().getName())
                .isEqualTo("java.util.ConcurrentModificationException");
    }

    @Test
    @DisplayName("the list, stack, and get contracts are unchanged")
    void the_existing_collection_contracts_are_unchanged(@TempDir Path directory) throws Exception {
        prints("""
                scores := solvik::collection::Map<String, Integer>()
                scores.put("ann", 10)
                missing := scores.get("cid")
                scores.put("ann", 11)
                updated := scores.get("ann")
                if missing == Null && updated != Null {
                    words := solvik::collection::List<String>()
                    words.add("ab")
                    words.add("cd")
                    mut joined := ""
                    for word in words {
                        joined = joined .. word
                    }
                    stack := solvik::collection::Stack<Integer>()
                    stack.push(7)
                    mut top := 0
                    for value in stack {
                        top = top + value
                    }
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("kept: ")
                        \\ .. (joined) .. (" ") .. (top) .. (" ") .. (updated))
                }
                """, "kept: abcd 7 11\n", directory);
    }
}
