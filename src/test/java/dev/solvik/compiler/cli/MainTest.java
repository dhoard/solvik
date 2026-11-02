package dev.solvik.compiler.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("solvik command")
class MainTest {

    @TempDir
    Path directory;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @BeforeEach
    void useTemporaryCache() {
        System.setProperty("solvik.cache.dir", directory.resolve("cache").toString());
    }

    @AfterEach
    void clearCacheProperty() {
        System.clearProperty("solvik.cache.dir");
    }

    /** Runs {@code solvik run <arguments>}. */
    private int run(String... arguments) {
        return solvik(withCommand("run", arguments));
    }

    /** Runs {@code solvik check <arguments>}. */
    private int check(String... arguments) {
        return solvik(withCommand("check", arguments));
    }

    /** Runs one command line exactly as given, so usage errors can be exercised. */
    private int solvik(String... arguments) {
        return Main.run(arguments, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    private static String[] withCommand(String command, String... arguments) {
        String[] commandLine = new String[arguments.length + 1];
        commandLine[0] = command;
        System.arraycopy(arguments, 0, commandLine, 1, arguments.length);
        return commandLine;
    }

    private String stdout() {
        return out.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private String stderr() {
        return err.toString(StandardCharsets.UTF_8);
    }

    private Path write(String name, String content) throws Exception {
        Path file = directory.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    @DisplayName("a command word and exactly one source file are required")
    void requires_a_command_and_one_source_file() {
        assertThat(solvik()).isEqualTo(2);
        assertThat(solvik("run")).isEqualTo(2);
        assertThat(run("a.sol", "out")).isEqualTo(2);
        assertThat(stderr()).contains("usage: solvik");
    }

    @Test
    @DisplayName("a source file without the required command word is a usage error")
    void rejects_a_verb_less_invocation() throws Exception {
        Path source = write("verb-less.sol", "TextWriter(Stdio.stdout()).writeLine((\"x\") .. \"\")\n");

        assertThat(solvik(source.toString())).isEqualTo(2);
        assertThat(stderr())
                .contains("unknown command: " + source)
                .contains("usage: solvik")
                .contains("run");
        assertThat(directory.resolve("cache"))
                .describedAs("a command line without a command word must not compile anything")
                .doesNotExist();
    }

    @Test
    void reports_a_missing_file() {
        assertThat(run(directory.resolve("absent.sol").toString())).isEqualTo(1);
        assertThat(stderr()).contains("no such file");
    }

    @Test
    @DisplayName("transpiles, compiles, and executes in one step")
    void runs_a_program() throws Exception {
        Path source = write("hello.sol", "TextWriter(Stdio.stdout()).writeLine((\"hello \" .. 1 + 2) .. \"\")\n");

        assertThat(run(source.toString())).isZero();
        assertThat(stdout()).isEqualTo("hello 3\n");
        assertThat(stderr()).isEmpty();
    }

    @Test
    @DisplayName("a runtime abort reports a stable code and exits non-zero")
    void reports_a_runtime_abort_with_its_code() throws Exception {
        Path source = write("abort.sol", """
                divisor := 0
                TextWriter(Stdio.stdout()).writeLine((10 / divisor) .. "")
                """);

        assertThat(run(source.toString())).isEqualTo(1);
        assertThat(stderr()).isEqualTo("solvik: runtime error [SOL-R001]: division by zero\n");
    }

    @Test
    @DisplayName("an uncaught exception reports its code and message and exits non-zero")
    void reports_an_uncaught_exception() throws Exception {
        Path source = write("raised.sol", """
                try {
                    raise Exception(4001, "denied")
                } finally {
                    TextWriter(Stdio.stdout()).writeLine(("cleanup") .. "")
                }
                """);

        assertThat(run(source.toString())).isEqualTo(1);
        assertThat(stdout()).isEqualTo("cleanup\n");
        assertThat(stderr())
                .isEqualTo("solvik: runtime error [SOL-R013]: uncaught exception [4001] denied\n");
    }

    @Test
    @DisplayName("a negative exception code aborts with its own stable code")
    void reports_a_negative_exception_code() throws Exception {
        Path source = write("negative.sol", """
                mut code := 0
                code = code - 1
                raise Exception(code, "negative")
                """);

        assertThat(run(source.toString())).isEqualTo(1);
        assertThat(stderr()).isEqualTo("solvik: runtime error [SOL-R012]: an exception code must"
                + " not be negative, found -1\n");
    }

    @ParameterizedTest
    @CsvSource({
            "'1, 2', 7, TextWriter(Stdio.stdout()).writeLine((items[index]) .. \"\")",
            "'1, 2', -1, items[index] = 9",
            "1, -1, TextWriter(Stdio.stdout()).writeLine((items[index]) .. \"\")",
            "1, 7, items[index] = 9"
    })
    @DisplayName("array bounds reported by the JVM are mapped to their own code")
    void maps_jvm_bounds_failures_to_a_code(String elements, int index, String operation)
            throws Exception {
        Path source = write("bounds.sol", """
                items := Integer?[2] {%s}
                index := %d
                %s
                """.formatted(elements, index, operation));

        assertThat(run(source.toString())).isEqualTo(1);
        assertThat(stderr()).isEqualTo("solvik: runtime error [SOL-R004]: array index out of bounds\n");
    }

    @Test
    @DisplayName("a nullable array index is rejected at compile time, not at run time")
    void a_nullable_index_needs_a_null_check() throws Exception {
        Path source = write("null-index.sol", """
                items := Integer[2] {1, 2}
                mut index: Integer? = 0
                index = Null
                TextWriter(Stdio.stdout()).writeLine((items[index]) .. "")
                """);

        assertThat(run(source.toString())).isEqualTo(1);
        assertThat(stderr()).contains("SOL-T015").contains("null-index.sol:4:")
                .contains("an array index needs a non-null value");
    }

    @Test
    @DisplayName("a raise in a static initializer reports the uncaught exception")
    void static_initializer_raise_is_reported() throws Exception {
        Path source = write("init-raise.sol", """
                type Boom {
                    static {
                        raise Exception(4001, "init failed")
                    }
                    pub func Boom() { }
                }
                value := Boom()
                """);

        assertThat(run(source.toString())).isEqualTo(1);
        assertThat(stderr()).isEqualTo(
                "solvik: runtime error [SOL-R013]: uncaught exception [4001] init failed\n");
    }

    @Test
    @DisplayName("exit in a static initializer carries its status out")
    void static_initializer_exit_uses_its_status() throws Exception {
        Path source = write("init-exit.sol", """
                type Stop {
                    static {
                        exit(7)
                    }
                    pub func Stop() { }
                }
                value := Stop()
                """);

        assertThat(run(source.toString())).isEqualTo(7);
        assertThat(stdout()).isEmpty();
        assertThat(stderr()).isEmpty();
    }

    @Test
    @DisplayName("a static initializer abort keeps its runtime code")
    void static_initializer_abort_keeps_its_code() throws Exception {
        Path source = write("init-abort.sol", """
                type Boom {
                    static {
                        divisor := 0
                        value := 10 / divisor
                    }
                    pub func Boom() { }
                }
                value := Boom()
                """);

        assertThat(run(source.toString())).isEqualTo(1);
        assertThat(stderr()).isEqualTo("solvik: runtime error [SOL-R001]: division by zero\n");
    }

    @Test
    @DisplayName("a class initialization failure is not catchable as a Solvik exception")
    void static_initializer_failure_is_not_catchable() throws Exception {
        Path source = write("init-uncatchable.sol", """
                type Boom {
                    static {
                        raise Exception(4001, "init failed")
                    }
                    pub func Boom() { }
                }
                try {
                    value := Boom()
                } catch (error: Exception) {
                    TextWriter(Stdio.stdout()).writeLine("caught")
                }
                """);

        assertThat(run(source.toString())).isEqualTo(1);
        assertThat(stdout()).isEmpty();
        assertThat(stderr()).contains("SOL-R013").contains("[4001] init failed");
    }

    @Test
    @DisplayName("a provably failing operation never runs: it is a compile error")
    void reports_a_static_failure_instead_of_running() throws Exception {
        Path source = write("static.sol", "TextWriter(Stdio.stdout()).writeLine((1 / 0) .. \"\")\n");

        assertThat(run(source.toString())).isEqualTo(1);
        assertThat(stderr()).contains("SOL-T008").contains("by zero always fails");
    }

    @Test
    void reports_compile_errors_with_positions_and_writes_no_cache() throws Exception {
        Path source = write("bad.sol", "a := 1 + \"x\"\n");

        assertThat(run(source.toString())).isEqualTo(1);
        assertThat(stderr())
                .contains("bad.sol:1:8")
                .contains("SOL-T001")
                .describedAs("the offending statement is shown with a caret")
                .contains("a := 1 + \"x\"")
                .contains("^");

        // A failed compile leaves no cache entry at all, so a broken run cannot poison the cache.
        assertThat(directory.resolve("cache")).doesNotExist();
    }

    @Test
    @DisplayName("a runtime abort exits non-zero and keeps its diagnostic")
    void reports_runtime_aborts() throws Exception {
        Path source = write("boom.sol", "zero := 0\nTextWriter(Stdio.stdout()).writeLine((10 / zero) .. \"\")\n");

        assertThat(run(source.toString())).isEqualTo(1);
        assertThat(stderr()).contains("division by zero");
    }

    @Test
    @DisplayName("exit(status) becomes the process status, with output flushed first")
    void exit_sets_the_status() throws Exception {
        Path source = write("quit.sol", "TextWriter(Stdio.stdout()).writeLine((\"bye\") .. \"\")\nexit(7)\n");

        assertThat(run(source.toString())).isEqualTo(7);
        assertThat(stdout()).isEqualTo("bye\n");
        assertThat(stderr()).isEmpty();
    }

    @Test
    void exit_without_a_status_is_success() throws Exception {
        Path source = write("quit-zero.sol", "TextWriter(Stdio.stdout()).writeLine((\"ok\") .. \"\")\nexit()\nTextWriter(Stdio.stdout()).writeLine((\"never\") .. \"\")\n");

        assertThat(run(source.toString())).isZero();
        assertThat(stdout()).isEqualTo("ok\n");
    }

    @Test
    @DisplayName("exit inside a try still runs finally and keeps its status")
    void exit_runs_finally_and_sets_the_status() throws Exception {
        Path source = write("quit-finally.sol", """
                try {
                    TextWriter(Stdio.stdout()).writeLine(("before") .. "")
                    exit(4)
                } catch (e: Exception) {
                    TextWriter(Stdio.stdout()).writeLine(("must not catch") .. "")
                } finally {
                    TextWriter(Stdio.stdout()).writeLine(("cleanup") .. "")
                }
                """);

        assertThat(run(source.toString())).isEqualTo(4);
        assertThat(stdout()).isEqualTo("before\ncleanup\n");
        assertThat(stderr()).isEmpty();
    }

    @Test
    @DisplayName("an unchanged file is executed from cached classes without recompiling")
    void reuses_the_cache() throws Exception {
        Path source = write("cached.sol", "TextWriter(Stdio.stdout()).writeLine((\"cached\") .. \"\")\n");

        assertThat(run(source.toString())).isZero();
        Path classes = singleCacheClasses();
        FileTime compiledAt = Files.getLastModifiedTime(classes);

        out.reset();
        assertThat(run(source.toString())).isZero();
        assertThat(stdout()).isEqualTo("cached\n");
        assertThat(Files.getLastModifiedTime(classes))
                .describedAs("class file must not be rewritten on a cache hit")
                .isEqualTo(compiledAt);
    }

    @Test
    void a_changed_source_produces_a_new_cache_entry() throws Exception {
        Path source = write("changed.sol", "TextWriter(Stdio.stdout()).writeLine((1) .. \"\")\n");
        assertThat(run(source.toString())).isZero();
        assertThat(cacheEntries()).hasSize(1);

        Files.writeString(source, "TextWriter(Stdio.stdout()).writeLine((2) .. \"\")\n", StandardCharsets.UTF_8);
        out.reset();
        assertThat(run(source.toString())).isZero();

        assertThat(stdout()).isEqualTo("2\n");
        assertThat(cacheEntries()).hasSize(2);
    }

    @Test
    @DisplayName("generated Java sources are deleted once the classes are compiled")
    void discards_generated_sources() throws Exception {
        Path source = write("kept.sol", "TextWriter(Stdio.stdout()).writeLine((\"hi\") .. \"\")\n");

        assertThat(run(source.toString())).isZero();

        try (var files = Files.walk(directory.resolve("cache"))) {
            List<String> names = files.filter(Files::isRegularFile)
                    .map(file -> file.getFileName().toString())
                    .toList();
            assertThat(names).describedAs("cache contents: %s", names)
                    .noneMatch(name -> name.endsWith(".java"))
                    .contains("Solvik$Program.class", "complete");
        }
    }

    @Test
    @DisplayName("--keep-sources retains the generated Java that a plain run deletes")
    void keep_sources_flag() throws Exception {
        Path source = write("kept.sol", "TextWriter(Stdio.stdout()).writeLine((1) .. \"\")\n");

        assertThat(run("--keep-sources", source.toString())).isZero();
        assertThat(stdout()).isEqualTo("1\n");
        try (var files = Files.walk(directory.resolve("cache"))) {
            List<String> names = files.filter(Files::isRegularFile)
                    .map(file -> file.getFileName().toString())
                    .toList();
            assertThat(names)
                    .contains("Solvik$Program.java", "Solvik$Program.class");
        }
    }

    @Test
    @DisplayName("the flag is part of the cache key, so the two modes never share an entry")
    void keep_sources_is_part_of_the_key() throws Exception {
        Path source = write("both.sol", "TextWriter(Stdio.stdout()).writeLine((2) .. \"\")\n");

        assertThat(run(source.toString())).isZero();
        assertThat(run("--keep-sources", source.toString())).isZero();

        assertThat(cacheEntries()).hasSize(2);
    }

    @Test
    void rejects_an_unknown_option() throws Exception {
        Path source = write("opt.sol", "TextWriter(Stdio.stdout()).writeLine((3) .. \"\")\n");

        assertThat(run("--nope", source.toString())).isEqualTo(2);
        assertThat(stderr()).contains("unknown option: --nope").contains("usage: solvik");
    }

    @Test
    @DisplayName("the run command word compiles and runs its file")
    void runs_a_program_through_the_run_command() throws Exception {
        Path source = write("command.sol",
                "TextWriter(Stdio.stdout()).writeLine((\"ran\") .. \"\")\n");

        assertThat(run(source.toString())).isZero();
        assertThat(stdout()).isEqualTo("ran\n");
        assertThat(stderr()).isEmpty();
    }

    @Test
    @DisplayName("a compile error under the run command keeps its diagnostic and exit code")
    void the_run_command_reports_compile_errors() throws Exception {
        Path source = write("command-bad.sol", "a := 1 + \"x\"\n");

        assertThat(run(source.toString())).isEqualTo(1);
        assertThat(stderr()).contains("SOL-T001").contains("command-bad.sol:1:8");
        assertThat(directory.resolve("cache")).doesNotExist();
    }

    @Test
    @DisplayName("global options are accepted on either side of the command word")
    void the_run_command_accepts_options_on_either_side() throws Exception {
        Path source = write("either.sol", "TextWriter(Stdio.stdout()).writeLine((4) .. \"\")\n");

        assertThat(solvik("--keep-sources", "run", source.toString())).isZero();
        assertThat(stdout()).isEqualTo("4\n");
        assertThat(generatedJavaNames()).contains("Solvik$Program.java");

        out.reset();
        assertThat(solvik("run", "--keep-sources", source.toString())).isZero();
        assertThat(stdout()).isEqualTo("4\n");
        assertThat(stderr()).isEmpty();
    }

    @Test
    @DisplayName("where the command word sits on the line is not part of the cache key")
    void the_command_word_position_is_not_part_of_the_key() throws Exception {
        Path source = write("shared.sol", "TextWriter(Stdio.stdout()).writeLine((5) .. \"\")\n");

        assertThat(solvik("--keep-sources", "run", source.toString())).isZero();
        Path classes = singleCacheClasses();
        FileTime compiledAt = Files.getLastModifiedTime(classes);

        out.reset();
        assertThat(solvik("run", "--keep-sources", source.toString())).isZero();

        assertThat(stdout()).isEqualTo("5\n");
        assertThat(cacheEntries()).hasSize(1);
        assertThat(Files.getLastModifiedTime(classes))
                .describedAs("both option spellings must reuse one cache entry")
                .isEqualTo(compiledAt);
    }

    @Test
    @DisplayName("the run command word requires a source file")
    void the_run_command_requires_a_file() {
        assertThat(solvik("run")).isEqualTo(2);
        assertThat(stderr()).contains("usage: solvik");
        assertThat(directory.resolve("cache")).doesNotExist();
    }

    @Test
    @DisplayName("an unknown command word is a usage error, not a missing file")
    void rejects_an_unknown_command() throws Exception {
        Path source = write("command.sol", "TextWriter(Stdio.stdout()).writeLine((6) .. \"\")\n");

        assertThat(solvik("build", source.toString())).isEqualTo(2);
        assertThat(stderr()).contains("unknown command: build").contains("usage: solvik");
        assertThat(directory.resolve("cache")).doesNotExist();
    }

    @Test
    @DisplayName("a second operand after the command word is a usage error")
    void rejects_a_second_operand_after_the_command() throws Exception {
        Path source = write("command.sol", "TextWriter(Stdio.stdout()).writeLine((7) .. \"\")\n");

        assertThat(run(source.toString(), "extra.sol")).isEqualTo(2);
        assertThat(stderr()).contains("usage: solvik");
        assertThat(directory.resolve("cache")).doesNotExist();
    }

    @Test
    @DisplayName("check compiles a program and reports success silently")
    void check_accepts_a_compiling_program() throws Exception {
        Path source = write("checked.sol",
                "TextWriter(Stdio.stdout()).writeLine((\"compiles\") .. \"\")\n");

        assertThat(check(source.toString())).isZero();
        assertThat(stdout()).isEmpty();
        assertThat(stderr()).isEmpty();
    }

    @Test
    @DisplayName("check never runs the program it compiles")
    void check_does_not_execute_the_program() throws Exception {
        Path source = write("not-run.sol", """
                TextWriter(Stdio.stdout()).writeLine(("must not run") .. "")
                exit(7)
                """);

        assertThat(check(source.toString())).isZero();
        assertThat(stdout()).describedAs("the program's output must not appear").isEmpty();
        assertThat(stderr()).isEmpty();
    }

    @Test
    @DisplayName("check accepts a program that would abort at run time")
    void check_ignores_run_time_failures() throws Exception {
        Path source = write("would-abort.sol", """
                divisor := 0
                TextWriter(Stdio.stdout()).writeLine((10 / divisor) .. "")
                """);

        assertThat(check(source.toString())).isZero();
        assertThat(stderr()).isEmpty();
    }

    @Test
    @DisplayName("check reports the compile error run would report and writes no cache")
    void check_reports_compile_errors() throws Exception {
        Path source = write("check-bad.sol", "a := 1 + \"x\"\n");

        assertThat(check(source.toString())).isEqualTo(1);
        assertThat(stdout()).isEmpty();
        assertThat(stderr())
                .contains("check-bad.sol:1:8")
                .contains("SOL-T001")
                .contains("a := 1 + \"x\"")
                .contains("^");
        assertThat(directory.resolve("cache")).doesNotExist();
    }

    @Test
    void check_reports_a_missing_file() {
        assertThat(check(directory.resolve("absent.sol").toString())).isEqualTo(1);
        assertThat(stderr()).contains("no such file");
    }

    @Test
    @DisplayName("check requires exactly one source file")
    void check_requires_one_source_file() throws Exception {
        Path source = write("check-operands.sol", "value := 1\n");

        assertThat(solvik("check")).isEqualTo(2);
        assertThat(check(source.toString(), "extra.sol")).isEqualTo(2);
        assertThat(stderr()).contains("usage: solvik").contains("run|check");
        assertThat(directory.resolve("cache")).doesNotExist();
    }

    @Test
    @DisplayName("check leaves no cache entry and does not warm the cache for run")
    void check_leaves_the_cache_untouched() throws Exception {
        Path source = write("scratch-check.sol",
                "TextWriter(Stdio.stdout()).writeLine((\"ran\") .. \"\")\n");

        assertThat(check(source.toString())).isZero();
        assertNoLeftoverEntry();

        // A second check recompiles from scratch rather than reusing anything.
        assertThat(check(source.toString())).isZero();
        assertNoLeftoverEntry();

        // run owns its entry, and only run's entry exists afterwards.
        assertThat(run(source.toString())).isZero();
        assertThat(stdout()).isEqualTo("ran\n");
        assertThat(stderr()).isEmpty();
        assertThat(cacheEntries()).hasSize(1);
    }

    @Test
    @DisplayName("a failed check deletes its scratch entry too")
    void check_deletes_its_scratch_entry_after_a_failure() throws Exception {
        Path source = write("scratch-fail.sol", """
                import "absent.sol"

                value := 1
                """);

        assertThat(check(source.toString())).isEqualTo(1);
        assertNoLeftoverEntry();
    }

    @Test
    @DisplayName("check accepts global options on either side of the word")
    void check_accepts_options_on_either_side() throws Exception {
        Path source = write("check-options.sol", "value := 1\n");

        // The generated Java of a check is deleted with its scratch entry, so --keep-sources is
        // accepted but has no lasting effect here.
        assertThat(solvik("--keep-sources", "check", source.toString())).isZero();
        assertNoLeftoverEntry();

        out.reset();
        assertThat(solvik("check", "--keep-sources", source.toString())).isZero();
        assertThat(stdout()).isEmpty();
        assertThat(stderr()).isEmpty();
        assertNoLeftoverEntry();
    }

    @Test
    @DisplayName("check rejects an error in an imported file and names that file")
    void check_reports_errors_in_an_imported_file() throws Exception {
        write("lib-bad.sol", """
                module lib {
                    pub func broken() -> Integer {
                        return "x"
                    }
                }
                """);
        Path source = write("uses-lib.sol", """
                import "lib-bad.sol"

                value := lib::broken()
                """);

        assertThat(check(source.toString())).isEqualTo(1);
        assertThat(stdout()).isEmpty();
        assertThat(stderr())
                .contains("lib-bad.sol:3:9")
                .contains("SOL-T001")
                .contains("cannot return String from a function returning Integer");
        assertThat(directory.resolve("cache")).doesNotExist();
    }

    @Test
    @DisplayName("check follows imports transitively and checks the whole program")
    void check_follows_transitive_imports() throws Exception {
        write("deep.sol", """
                module deep {
                    pub func broken() -> Integer {
                        return "x"
                    }
                }
                """);
        // mid.sol imports deep.sol but never calls the broken function, so only a whole-program check
        // can reach the error.
        write("mid.sol", """
                import "deep.sol"

                module mid {
                    pub func value() -> Integer {
                        return 1
                    }
                }
                """);
        Path source = write("transitive.sol", """
                import "mid.sol"

                value := mid::value()
                """);

        assertThat(check(source.toString())).isEqualTo(1);
        assertThat(stderr()).contains("deep.sol:3:9").contains("SOL-T001");
        assertThat(directory.resolve("cache")).doesNotExist();
    }

    @Test
    @DisplayName("check reports a missing import before compiling anything")
    void check_reports_a_missing_import() throws Exception {
        Path source = write("missing-import.sol", """
                import "absent.sol"

                value := 1
                """);

        assertThat(check(source.toString())).isEqualTo(1);
        assertThat(stderr()).contains("SOL-I001").contains("no such file: 'absent.sol'");
        assertThat(directory.resolve("cache")).doesNotExist();
    }

    @Test
    @DisplayName("check sees a change in an imported file, not just the entry file")
    void check_notices_a_changed_import() throws Exception {
        Path library = write("lib-edit.sol", """
                module lib {
                    pub func value() -> Integer {
                        return 1
                    }
                }
                """);
        Path source = write("uses-edit.sol", """
                import "lib-edit.sol"

                result := lib::value()
                """);

        assertThat(check(source.toString())).isZero();
        assertNoLeftoverEntry();

        Files.writeString(library, """
                module lib {
                    pub func value() -> Integer {
                        return "x"
                    }
                }
                """, StandardCharsets.UTF_8);

        assertThat(check(source.toString())).isEqualTo(1);
        assertThat(stderr()).contains("lib-edit.sol:3:9").contains("SOL-T001");
        assertNoLeftoverEntry();
    }

    /**
     * Compiles the corpus through {@code check}: the entry file, its direct and transitive imports,
     * and the packaged standard library.
     */
    @Test
    @DisplayName("check passes the complete language corpus and its imports")
    void check_accepts_the_corpus() throws Exception {
        Path corpus = Path.of("example.sol");
        assumeTrue(Files.isRegularFile(corpus), "the corpus lives at the project root");

        assertThat(check(corpus.toAbsolutePath().toString())).isZero();
        assertThat(stdout()).isEmpty();
        assertThat(stderr()).isEmpty();
    }

    /** Asserts that a check left nothing behind: no published entry and no staging directory. */
    private void assertNoLeftoverEntry() throws Exception {
        Path root = directory.resolve("cache");
        if (!Files.exists(root)) {
            return;
        }
        try (var entries = Files.list(root)) {
            assertThat(entries.map(entry -> entry.getFileName().toString()).toList())
                    .describedAs("a check must delete its scratch entry")
                    .isEmpty();
        }
    }

    private Path singleCacheClasses() throws Exception {
        try (var entries = Files.list(directory.resolve("cache"))) {
            return entries.findFirst().orElseThrow().resolve("classes")
                    .resolve("solvik").resolve("Solvik$Program.class");
        }
    }

    private List<String> generatedJavaNames() throws Exception {
        try (var files = Files.walk(directory.resolve("cache"))) {
            return files.filter(Files::isRegularFile)
                    .map(file -> file.getFileName().toString())
                    .toList();
        }
    }

    private java.util.List<Path> cacheEntries() throws Exception {
        try (var entries = Files.list(directory.resolve("cache"))) {
            return entries.toList();
        }
    }
}
