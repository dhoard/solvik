package dev.solvik.compiler.load;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.solvik.compiler.SolvikCompiler;
import dev.solvik.compiler.cli.Main;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.report.DiagnosticReporter;
import dev.solvik.compiler.transpile.GeneratedNames;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Multi-file programs (LANGUAGE.md §3, "Source files").
 *
 * <p>An {@code import "path"} is relative to the file that writes it and contributes declarations
 * only; only the entry file's statements run. Modules merge by name across files.
 */
@DisplayName("imports and source files")
class ImportTest {

    @TempDir
    Path directory;

    private Path write(String relative, String content) throws IOException {
        Path file = directory.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    /** The name diagnostics use for a file, which is how the loader reports it. */
    private String name(String relative) {
        return DiagnosticReporter.displayName(directory.resolve(relative));
    }

    private ProgramLoader.Loaded load(String entry, Diagnostics diagnostics) {
        return new ProgramLoader(diagnostics).load(directory.resolve(entry));
    }

    @Test
    void an_imported_module_merges_with_the_entry_file() throws IOException {
        write("lib/shapes.sol", """
                module shapes {
                    pub type Point {
                        pub x := 0

                        pub func Point(x: Integer) {
                            self.x = x
                        }
                    }
                }
                """);
        write("app.sol", """
                import "lib/shapes.sol"

                module shapes {
                    pub func origin() -> Point {
                        return Point(0)
                    }
                }

                TextWriter(Stdio.stdout()).writeLine((shapes::origin().x) .. "")
                """);

        Diagnostics diagnostics = new Diagnostics();
        ProgramLoader.Loaded loaded = load("app.sol", diagnostics);

        assertThat(diagnostics.all()).isEmpty();
        assertThat(loaded.programFileNames()).containsExactly(name("app.sol"), name("lib/shapes.sol"));
        assertThat(run(loaded)).isEqualTo("0\n");
    }

    @Test
    void imports_are_transitive() throws IOException {
        write("inner.sol", """
                module inner {
                    pub func seven() -> Integer {
                        return 7
                    }
                }
                """);
        write("middle.sol", """
                import "inner.sol"

                module middle {
                    pub func doubled() -> Integer {
                        return inner::seven() + inner::seven()
                    }
                }
                """);
        write("app.sol", """
                import "middle.sol"

                TextWriter(Stdio.stdout()).writeLine((middle::doubled()) .. (" ") .. (inner::seven()))
                """);

        Diagnostics diagnostics = new Diagnostics();
        ProgramLoader.Loaded loaded = load("app.sol", diagnostics);

        assertThat(diagnostics.all()).isEmpty();
        assertThat(loaded.programFileNames()).containsExactly(name("app.sol"), name("middle.sol"),
                name("inner.sol"));
        assertThat(run(loaded)).isEqualTo("14 7\n");
    }

    @Test
    void an_import_cycle_is_reported_with_its_path() throws IOException {
        write("a.sol", """
                import "b.sol"

                module a {
                    pub func fromB() -> Integer {
                        return b::value()
                    }
                }
                """);
        write("b.sol", """
                import "a.sol"

                module b {
                    pub func value() -> Integer {
                        return 1
                    }
                }
                """);
        write("app.sol", "import \"a.sol\"\n\nTextWriter(Stdio.stdout()).writeLine((a::fromB()) .. \"\")\n");

        Diagnostics diagnostics = new Diagnostics();
        load("app.sol", diagnostics);

        assertThat(diagnostics.all()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo(ProgramLoader.IMPORT_CYCLE);
            assertThat(diagnostic.pos().file()).isEqualTo(name("b.sol"));
            assertThat(diagnostic.message())
                    .contains(name("a.sol") + " -> " + name("b.sol") + " -> " + name("a.sol"));
        });
    }

    @Test
    @DisplayName("recursion is rejected: a.sol -> b.sol -> c.sol -> a.sol")
    void a_three_file_cycle_is_reported_with_the_whole_path() throws IOException {
        write("a.sol", "import \"b.sol\"\n\nmodule a {\n    pub func one() -> Integer {\n        return 1\n    }\n}\n");
        write("b.sol", "import \"c.sol\"\n\nmodule b {\n    pub func two() -> Integer {\n        return 2\n    }\n}\n");
        write("c.sol", "import \"a.sol\"\n\nmodule c {\n    pub func three() -> Integer {\n        return 3\n    }\n}\n");

        Diagnostics diagnostics = new Diagnostics();
        load("a.sol", diagnostics);

        assertThat(diagnostics.all()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo(ProgramLoader.IMPORT_CYCLE);
            assertThat(diagnostic.pos().file()).isEqualTo(name("c.sol"));
            assertThat(diagnostic.message()).contains(name("a.sol") + " -> " + name("b.sol")
                    + " -> " + name("c.sol") + " -> " + name("a.sol"));
        });
    }

    @Test
    void a_file_that_imports_itself_is_a_cycle() throws IOException {
        write("self.sol", "import \"self.sol\"\n\nTextWriter(Stdio.stdout()).writeLine((1) .. \"\")\n");

        Diagnostics diagnostics = new Diagnostics();
        load("self.sol", diagnostics);

        assertThat(diagnostics.all()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo(ProgramLoader.IMPORT_CYCLE);
            assertThat(diagnostic.message()).contains(name("self.sol") + " -> " + name("self.sol"));
        });
    }

    @Test
    @DisplayName("a diamond is valid: app -> left, app -> right, left -> shared, right -> shared")
    void a_diamond_import_is_loaded_once() throws IOException {
        write("shared.sol", """
                module shared {
                    pub type Value {
                        pub n := 0

                        pub func Value(n: Integer) {
                            self.n = n
                        }
                    }
                }
                """);
        write("left.sol", "import \"shared.sol\"\n\nmodule left {\n    pub func make() -> shared::Value {\n        return shared::Value(1)\n    }\n}\n");
        write("right.sol", "import \"shared.sol\"\n\nmodule right {\n    pub func make() -> shared::Value {\n        return shared::Value(2)\n    }\n}\n");
        write("app.sol", "import \"left.sol\"\nimport \"right.sol\"\n\nTextWriter(Stdio.stdout()).writeLine((left::make().n) .. (\" \") .. (right::make().n))\n");

        Diagnostics diagnostics = new Diagnostics();
        ProgramLoader.Loaded loaded = load("app.sol", diagnostics);

        assertThat(diagnostics.all()).isEmpty();
        assertThat(loaded.programFileNames()).containsExactly(name("app.sol"), name("left.sol"),
                name("shared.sol"), name("right.sol"));
        assertThat(run(loaded)).isEqualTo("1 2\n");
    }

    @Test
    void only_the_entry_file_contributes_statements() throws IOException {
        write("lib.sol", """
                module lib {
                    pub func value() -> Integer {
                        return 1
                    }
                }

                TextWriter(Stdio.stdout()).writeLine(("must not run") .. "")
                """);
        write("app.sol", """
                import "lib.sol"

                TextWriter(Stdio.stdout()).writeLine(("entry ") .. (lib::value()))
                """);

        Diagnostics diagnostics = new Diagnostics();
        load("app.sol", diagnostics);

        assertThat(diagnostics.all()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo(ProgramLoader.STATEMENTS_IN_IMPORT);
            assertThat(diagnostic.pos().file()).isEqualTo(name("lib.sol"));
        });
    }

    @Test
    void a_missing_import_is_reported_against_the_importing_file() throws IOException {
        write("app.sol", "import \"nope.sol\"\n\nTextWriter(Stdio.stdout()).writeLine((1) .. \"\")\n");

        Diagnostics diagnostics = new Diagnostics();
        load("app.sol", diagnostics);

        assertThat(diagnostics.all()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo(ProgramLoader.MISSING_FILE);
            assertThat(diagnostic.message()).contains("nope.sol");
            assertThat(diagnostic.pos().file()).isEqualTo(name("app.sol"));
        });
    }

    @Test
    @DisplayName("an unreadable import is not left on the load stack for later cycle messages")
    void an_unreadable_import_does_not_appear_in_a_later_cycle_path() throws IOException {
        Path unreadable = write("unreadable.sol", "type Hidden { }\n");
        assumeTrue(Files.getFileStore(unreadable)
                        .supportsFileAttributeView(PosixFileAttributeView.class),
                "needs POSIX file permissions");
        Files.setPosixFilePermissions(unreadable, PosixFilePermissions.fromString("---------"));
        assumeTrue(!Files.isReadable(unreadable),
                "the current user ignores file permissions");
        try {
            write("a.sol", """
                    import "unreadable.sol"
                    import "c.sol"

                    TextWriter(Stdio.stdout()).writeLine(("a") .. "")
                    """);
            write("c.sol", """
                    import "a.sol"
                    """);

            Diagnostics diagnostics = new Diagnostics();
            load("a.sol", diagnostics);

            assertThat(diagnostics.all())
                    .anySatisfy(diagnostic -> assertThat(diagnostic.code())
                            .isEqualTo(ProgramLoader.MISSING_FILE));
            // The failed read must not stay between a.sol and c.sol, or the reported cycle would
            // name a file that is not on the path at all.
            assertThat(diagnostics.all()).filteredOn(diagnostic -> diagnostic.code()
                            .equals(ProgramLoader.IMPORT_CYCLE))
                    .singleElement()
                    .satisfies(diagnostic -> assertThat(diagnostic.message())
                            .isEqualTo("import cycle: " + name("a.sol") + " -> " + name("c.sol")
                                    + " -> " + name("a.sol")));
        } finally {
            Files.setPosixFilePermissions(unreadable, PosixFilePermissions.fromString("rw-------"));
        }
    }

    @Test
    void an_absolute_import_path_is_rejected() throws IOException {
        write("app.sol", "import \"/etc/passwd\"\n\nTextWriter(Stdio.stdout()).writeLine((1) .. \"\")\n");

        Diagnostics diagnostics = new Diagnostics();
        load("app.sol", diagnostics);

        assertThat(diagnostics.all()).singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.code())
                        .isEqualTo(ProgramLoader.ABSOLUTE_PATH));
    }

    @Test
    void module_privacy_applies_across_files() throws IOException {
        write("lib.sol", """
                module shapes {
                    type Hidden {
                        pub v := 1

                        pub func Hidden() { }
                    }
                }
                """);
        write("app.sol", """
                import "lib.sol"

                leaked := shapes::Hidden()
                """);

        var out = new java.io.ByteArrayOutputStream();
        var err = new java.io.ByteArrayOutputStream();
        try {
            int exit = Main.run(new String[]{"run", directory.resolve("app.sol").toString()},
                    new PrintStream(out, true, StandardCharsets.UTF_8),
                    new PrintStream(err, true, StandardCharsets.UTF_8));
            assertThat(exit).isEqualTo(1);
        } finally {
            System.clearProperty("solvik.cache.dir");
        }
        assertThat(err.toString(StandardCharsets.UTF_8))
                .contains("SOL-T011")
                .contains("shapes::Hidden");
    }

    @Test
    void a_diagnostic_names_the_file_it_came_from() throws IOException {
        write("lib.sol", "module lib {\n    pub func broken() -> Integer {\n        return \"text\"\n    }\n}\n");
        write("app.sol", "import \"lib.sol\"\n\nTextWriter(Stdio.stdout()).writeLine((lib::broken()) .. \"\")\n");

        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        try {
            System.setProperty("solvik.cache.dir", directory.resolve("cache").toString());
            int exit = Main.run(new String[]{"run", directory.resolve("app.sol").toString()},
                    new PrintStream(out, true, StandardCharsets.UTF_8),
                    new PrintStream(err, true, StandardCharsets.UTF_8));
            assertThat(exit).isEqualTo(1);
        } finally {
            System.clearProperty("solvik.cache.dir");
        }
        assertThat(err.toString(StandardCharsets.UTF_8))
                .contains("lib.sol:3:")
                .contains("SOL-T001");
    }

    @Test
    void a_single_source_compilation_refuses_imports() {
        assertThat(runFailure("import \"lib.sol\"\n\nTextWriter(Stdio.stdout()).writeLine((1) .. \"\")\n"))
                .contains(ProgramLoader.MISSING_FILE)
                .contains("run the compiler on a file path");
    }

    // ------------------------------------------------------------------ helpers

    private String runFailure(String source) {
        try {
            new SolvikCompiler("app.sol").transpile(source);
            return "";
        } catch (SolvikCompiler.CompilationFailure failure) {
            return failure.getMessage();
        }
    }

    private String run(ProgramLoader.Loaded loaded) throws IOException {
        Path sources = directory.resolve("out/src");
        Path classes = directory.resolve("out/classes");
        Files.createDirectories(sources);
        Files.createDirectories(classes);
        Map<String, String> files = new SolvikCompiler("app.sol").transpile(loaded);
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
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        } finally {
            System.setOut(originalOut);
        }
        return buffer.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
