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
 * {@code try}, {@code catch}, {@code finally}, and {@code raise} at run time ({@code LANGUAGE.md}
 * §7.4).
 *
 * <p>The semantics are Java's own, so the tests run the generated program with no compiler or
 * third-party classes visible, exactly as a user runs it, and check the observable effect: what the
 * program printed, which throwable left it, and which cleanup ran on the way.
 */
@DisplayName("exception handling at run time (§7.4)")
class ExceptionExecutionTest {

    private record Outcome(String stdout, String stderr, Throwable failure) { }

    private static Outcome run(String source, Path directory) throws Exception {
        Map<String, String> files = new SolvikCompiler("exceptions.sol").transpile(source);
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
                .run(null, null, null, arguments.toArray(String[]::new))).isZero();

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
                loader.loadClass(GeneratedNames.PROGRAM_BINARY_NAME).getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            } catch (InvocationTargetException exception) {
                failure = exception.getCause();
            } finally {
                System.setOut(originalOut);
                System.setErr(originalErr);
            }
        }
        return new Outcome(out.toString(StandardCharsets.UTF_8),
                err.toString(StandardCharsets.UTF_8), failure);
    }

    private static void output(String source, String expected, Path directory) throws Exception {
        Outcome result = run(source, directory);
        assertThat(result.failure()).isNull();
        assertThat(result.stderr()).isEmpty();
        assertThat(result.stdout()).isEqualTo(expected);
    }

    @Test
    @DisplayName("a raised exception is caught and its code and message select the handler")
    void a_raised_exception_is_caught(@TempDir Path directory) throws Exception {
        output("""
                type Codes {
                    static pub Denied := 4001
                    static pub Missing := 4002
                }

                func check(kind: Integer) -> Integer {
                    if kind == 1 {
                        raise Exception(Codes.Denied, "denied")
                    }
                    if kind == 2 {
                        raise Exception(Codes.Missing, "missing")
                    }
                    return 99
                }

                mut log := ""
                try {
                    log ..= String(check(1))
                } catch (e: Exception) {
                    log ..= "caught "
                    if e.getCode() == Codes.Denied {
                        log ..= "denied:"
                    }
                    switch e.getCode() {
                        case 4001 { log ..= "switch-1" }
                        case 4002 { log ..= "switch-2" }
                        default { log ..= "switch-other" }
                    }
                    log ..= " " .. e.getMessage() .. " [" .. e.getCode() .. "]"
                }
                TextWriter(Stdio.stdout()).writeLine((log) .. "")
                TextWriter(Stdio.stdout()).writeLine((check(0) == 99) .. (" ") .. (check(3)))
                """, """
                caught denied:switch-1 denied [4001]
                true 99
                """, directory);
    }

    @Test
    @DisplayName("finally runs on the normal path, on return, and around loop control")
    void finally_runs_on_every_exit(@TempDir Path directory) throws Exception {
        output("""
                func pick(flag: Boolean) -> Integer {
                    try {
                        if flag {
                            return 10
                        }
                        return 20
                    } finally {
                        TextWriter(Stdio.stdout()).writeLine(("finally") .. "")
                    }
                }

                TextWriter(Stdio.stdout()).writeLine((pick(true)) .. "")
                TextWriter(Stdio.stdout()).writeLine((pick(false)) .. "")

                mut log := ""
                for item in Integer[3] {1, 2, 3} {
                    try {
                        if item == 2 {
                            continue
                        }
                        if item == 3 {
                            break
                        }
                        log ..= String(item)
                    } finally {
                        log ..= "!"
                    }
                }
                TextWriter(Stdio.stdout()).writeLine((log) .. "")
                """, """
                finally
                10
                finally
                20
                1!!!
                """, directory);
    }

    @Test
    @DisplayName("a try/finally without a catch cleans up and lets the raise through")
    void try_finally_propagates(@TempDir Path directory) throws Exception {
        Outcome result = run("""
                func attempt() {
                    try {
                        raise Exception(9, "boom")
                    } finally {
                        TextWriter(Stdio.stdout()).writeLine(("cleanup") .. "")
                    }
                }

                try {
                    attempt()
                } finally {
                    TextWriter(Stdio.stdout()).writeLine(("outer cleanup") .. "")
                }
                """, directory);

        assertThat(result.stdout()).isEqualTo("cleanup\nouter cleanup\n");
        assertThat(result.failure()).isNotNull();
        assertThat(result.failure().getClass().getName())
                .isEqualTo(GeneratedNames.exceptionBinaryName());
        assertThat(result.failure().getMessage()).isEqualTo("boom");
    }

    @Test
    @DisplayName("an uncaught exception carries its code and renders as [code] message")
    void an_uncaught_exception_carries_its_code(@TempDir Path directory) throws Exception {
        Outcome result = run("TextWriter(Stdio.stdout()).writeLine((Exception(7, \"seven\")) .. \"\")\n", directory);

        assertThat(result.failure()).isNull();
        assertThat(result.stdout()).isEqualTo("[7] seven\n");
    }

    @Test
    @DisplayName("a runtime abort is not catchable and reports its own code")
    void aborts_are_not_catchable(@TempDir Path directory) throws Exception {
        Outcome result = run("""
                try {
                    values := Integer[2] {1, 2}
                    TextWriter(Stdio.stdout()).writeLine((values[7]) .. "")
                } catch (e: Exception) {
                    TextWriter(Stdio.stdout()).writeLine(("must not catch") .. "")
                } finally {
                    TextWriter(Stdio.stdout()).writeLine(("cleanup") .. "")
                }
                """, directory);

        assertThat(result.stdout()).isEqualTo("cleanup\n");
        assertThat(result.failure()).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    @DisplayName("exit is not catchable, and finally still runs")
    void exit_is_not_catchable(@TempDir Path directory) throws Exception {
        Outcome result = run("""
                try {
                    exit(3)
                } catch (e: Exception) {
                    TextWriter(Stdio.stdout()).writeLine(("must not catch") .. "")
                } finally {
                    TextWriter(Stdio.stdout()).writeLine(("cleanup") .. "")
                }
                TextWriter(Stdio.stdout()).writeLine(("unreachable") .. "")
                """, directory);

        assertThat(result.stdout()).isEqualTo("cleanup\n");
        assertThat(result.failure().getClass().getName())
                .isEqualTo(GeneratedNames.exitBinaryName());
    }

    @Test
    @DisplayName("a negative code aborts with SOL-R012 instead of reaching a handler")
    void a_negative_code_aborts(@TempDir Path directory) throws Exception {
        Outcome result = run("""
                mut code := 0
                code = code - 5
                try {
                    raise Exception(code, "negative")
                } catch (e: Exception) {
                    TextWriter(Stdio.stdout()).writeLine(("must not catch") .. "")
                }
                """, directory);

        assertThat(result.stdout()).isEmpty();
        assertThat(result.stderr()).contains("SOL-R012");
        assertThat(result.failure().getClass().getName())
                .isEqualTo(GeneratedNames.errorBinaryName());
    }

    @Test
    @DisplayName("a catch may raise again, and the outer clause handles it")
    void a_catch_may_raise_again(@TempDir Path directory) throws Exception {
        output("""
                func attempt() {
                    raise Exception(5000, "inner")
                }

                try {
                    try {
                        attempt()
                    } catch (e: Exception) {
                        TextWriter(Stdio.stdout()).writeLine(("inner ") .. (e.getCode()))
                        raise Exception(e.getCode() + 1, e.getMessage())
                    }
                } catch (recaught: Exception) {
                    TextWriter(Stdio.stdout()).writeLine(("outer ") .. (recaught))
                }
                TextWriter(Stdio.stdout()).writeLine(("done") .. "")
                """, """
                inner 5000
                outer [5001] inner
                done
                """, directory);
    }

    @Test
    @DisplayName("a nullable Exception is usable after a null check, and type() names it")
    void a_nullable_exception_narrows(@TempDir Path directory) throws Exception {
        output("""
                mut maybe: Exception? = Null
                maybe = Exception(7, "seven")
                if maybe != Null {
                    TextWriter(Stdio.stdout()).writeLine((maybe.type()) .. (" ") .. (maybe.getCode()) .. (" ") .. (maybe.getMessage()))
                }
                TextWriter(Stdio.stdout()).writeLine((String(Exception(8, "eight"))) .. "")
                """, """
                Exception 7 seven
                [8] eight
                """, directory);
    }

    @Test
    @DisplayName("a module function and a type method can raise and catch across generated files")
    void modules_and_types_can_raise_and_catch(@TempDir Path directory) throws Exception {
        output("""
                module guard {
                    pub func require(ok: Boolean) {
                        if !ok {
                            raise Exception(6001, "guard failed")
                        }
                    }

                    pub type Checker {
                        pub func Checker() { }

                        pub func run(ok: Boolean) -> String {
                            try {
                                guard::require(ok)
                                return "ok"
                            } catch (problem: Exception) {
                                return "caught " .. problem.getCode() .. ": " .. problem.getMessage()
                            }
                        }
                    }
                }

                TextWriter(Stdio.stdout()).writeLine((guard::Checker().run(true)) .. "")
                TextWriter(Stdio.stdout()).writeLine((guard::Checker().run(false)) .. "")
                """, """
                ok
                caught 6001: guard failed
                """, directory);
    }

    @Test
    @DisplayName("the exception class is emitted only when the program uses it")
    void the_support_class_is_emitted_on_demand(@TempDir Path directory) {
        Map<String, String> plain = new SolvikCompiler("plain.sol").transpile("plain := 1\n");
        assertThat(plain.get("Solvik$Program.java")).doesNotContain(GeneratedNames.EXCEPTION_CLASS);
        assertThat(plain).doesNotContainKey(GeneratedNames.IO_SOURCE_PATH);

        Map<String, String> io = new SolvikCompiler("io.sol").transpile("""
                TextWriter(Stdio.stdout()).writeLine(("plain") .. "")
                """);
        // The generated adapter raises the program's Solvik Exception, so I/O pulls it in.
        assertThat(io).containsKey(GeneratedNames.IO_SOURCE_PATH);
        assertThat(io.get("Solvik$Program.java"))
                .contains("public static final class " + GeneratedNames.EXCEPTION_CLASS);

        Map<String, String> used = new SolvikCompiler("used.sol").transpile("""
                try {
                    raise Exception(1, "x")
                } catch (e: Exception) {
                    TextWriter(Stdio.stdout()).writeLine((e.getCode()) .. "")
                }
                """);
        String program = used.get("Solvik$Program.java");
        assertThat(program)
                .contains("public static final class " + GeneratedNames.EXCEPTION_CLASS
                        + " extends RuntimeException")
                .contains("catch (Solvik$Program." + GeneratedNames.EXCEPTION_CLASS + " e)")
                .contains("throw new Solvik$Program." + GeneratedNames.EXCEPTION_CLASS
                        + "(1, \"x\")");
    }

    @Test
    @DisplayName("type() still names the declared user types when exceptions are used")
    void type_dispatch_keeps_user_types(@TempDir Path directory) throws Exception {
        output("""
                type Point {
                    pub x := 1
                    pub func Point() { }
                }

                TextWriter(Stdio.stdout()).writeLine((Point().type()) .. "")
                try {
                    raise Exception(1, "x")
                } catch (e: Exception) {
                    TextWriter(Stdio.stdout()).writeLine((e.type()) .. "")
                }
                """, """
                Point
                Exception
                """, directory);
    }
}
