package dev.solvik.compiler.cli;

import dev.solvik.compiler.JavaTarget;
import dev.solvik.compiler.SolvikCompiler;
import dev.solvik.compiler.bind.JavaClasspath;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.load.ProgramLoader;
import dev.solvik.compiler.report.DiagnosticReporter;
import dev.solvik.compiler.transpile.GeneratedNames;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

/**
 * Command line entry point:
 * {@code solvik [--keep-sources] [--cp <jar-list>] run|check <source file>}.
 *
 * <p>Every invocation names a {@link Command}. Both commands transpile the source to Java and compile
 * it with the JDK compiler; {@code run} then executes the result, while {@code check} stops after the
 * compile so its exit status answers whether the file compiles at all. The program is the entry file
 * together with every file it imports, transitively, and the packaged standard library, so an error
 * anywhere in it fails both commands. {@code run} caches its artifacts under {@code ~/.cache/solvik/}
 * keyed by the loaded source text, the dependency classpath, the {@code --keep-sources} mode, and the
 * compiler build, so an unchanged program is neither transpiled nor compiled again — it is executed
 * straight from the cached class files. {@code check} is a one-off compile: it reads no cache entry,
 * publishes none, and deletes the scratch entry it used. The command word is required:
 * {@code solvik app.sol} without one is a usage error.
 *
 * <p>Exit codes: {@code 0} success, {@code 1} compile error or runtime abort, {@code 2} bad usage.
 */
public final class Main {

    private static final String USAGE =
            "usage: solvik [--keep-sources] [--cp <jar-list>] " + Command.words()
                    + " <source file>";

    private static final String PROGRAM_CLASS = GeneratedNames.PROGRAM_BINARY_NAME;

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /**
     * A command word: what an invocation does. Exactly one is required on every command line.
     *
     * <p>Adding a command is one constant here, one branch in {@link #run} that names the method
     * implementing it, and that method; a command that needs its own options extends the shared parse
     * below rather than adding a second parser. The command word must be the first positional
     * argument, so global options may appear on either side of it ({@code solvik --cp lib/a.jar run
     * app.sol} and {@code solvik run --cp lib/a.jar app.sol} are the same invocation). A source file
     * whose path is exactly a command word must be spelled as a path ({@code ./run}, {@code ./check}).
     */
    private enum Command {

        /** Transpiles, compiles, and executes a source file. */
        RUN("run"),

        /** Transpiles and compiles a source file in a scratch entry it deletes, never running it. */
        CHECK("check");

        private final String word;

        Command(String word) {
            this.word = word;
        }

        /** The word that selects this command on the command line. */
        String word() {
            return word;
        }

        /** The command {@code token} names, or {@code null} when it names no command. */
        static Command named(String token) {
            for (Command command : values()) {
                if (command.word.equals(token)) {
                    return command;
                }
            }
            return null;
        }

        /** The recognized command words, in declaration order, for the usage message. */
        static String words() {
            return Arrays.stream(values()).map(Command::word).collect(Collectors.joining("|"));
        }
    }

    /** Runs one compilation and execution; package-visible so tests can drive it. */
    public static int run(String[] args, PrintStream out, PrintStream err) {
        boolean keepSources = false;
        List<String> cpOptions = new ArrayList<>();
        List<String> operands = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String argument = args[i];
            if ("--cp".equals(argument)) {
                if (++i >= args.length || args[i].startsWith("--")) { err.println(USAGE); return 2; }
                cpOptions.add(args[i]);
            } else if ("--keep-sources".equals(argument)) {
                keepSources = true;
            } else if (argument.startsWith("--")) {
                err.println("solvik: unknown option: " + argument);
                err.println(USAGE);
                return 2;
            } else {
                operands.add(argument);
            }
        }
        if (operands.isEmpty()) {
            err.println(USAGE);
            return 2;
        }
        Command command = Command.named(operands.get(0));
        if (command == null) {
            // The first positional argument is the command word, so a bare file path is a usage
            // error rather than something to compile.
            err.println("solvik: unknown command: " + operands.get(0));
            err.println(USAGE);
            return 2;
        }
        operands.remove(0);
        if (operands.size() != 1) {
            err.println(USAGE);
            return 2;
        }
        String fileName = operands.get(0);
        return switch (command) {
            case RUN -> runProgram(fileName, keepSources, cpOptions, out, err);
            case CHECK -> checkProgram(fileName, cpOptions, err);
        };
    }

    /** A program that loaded and passed the loader's checks, with its resolved dependency classpath. */
    private record LoadedProgram(ProgramLoader.Loaded loaded, JavaClasspath classpath) {
    }

    /** What a command does with a program that loaded; the only difference between the commands. */
    @FunctionalInterface
    private interface Use {
        int apply(LoadedProgram program) throws IOException;
    }

    /**
     * {@code run}: loads, transpiles, compiles, and executes {@code fileName}, reusing a published
     * cache entry when one matches.
     *
     * @return the program's exit status, or {@code 1} when it could not be compiled or ran to a
     *     failure the CLI reports itself ({@code 2} is reserved for a bad command line)
     */
    private static int runProgram(String fileName, boolean keepSources, List<String> cpOptions,
                                  PrintStream out, PrintStream err) {
        return withProgram(fileName, cpOptions, err, program -> {
            Path root = CacheKey.cacheRoot();
            String key = CacheKey.of(program.loaded().fingerprint() + '\0'
                    + program.classpath().fingerprint(), keepSources);
            CompilationCache cache = CompilationCache.openIfUsable(root, key);
            if (cache == null) {
                // Transpile before the cache directory exists: a source that does not check must not
                // leave one behind.
                Map<String, String> files = transpile(program);
                cache = compile(files, root, key, keepSources, program.classpath(), err);
                if (cache == null) {
                    return 1;
                }
            }
            return execute(cache, program.classpath(), out, err);
        });
    }

    /**
     * {@code check}: loads, transpiles, and compiles {@code fileName} exactly as {@code run} does,
     * then stops. The program checked is the entry file with every file it imports, transitively, plus
     * the packaged standard library, so a diagnostic in any of them fails the check.
     *
     * <p>A check is a one-off compile: it never runs the program, never reads or publishes a reusable
     * cache entry, and deletes the scratch entry it compiled into. The cache root is therefore left
     * exactly as it was found, and a later {@code run} compiles for itself.
     *
     * @return {@code 0} when the file compiles, otherwise the status and report {@code run} would
     *     produce before executing anything
     */
    private static int checkProgram(String fileName, List<String> cpOptions, PrintStream err) {
        return withProgram(fileName, cpOptions, err, program -> {
            // Transpile first, so a source that does not check never creates the cache root at all.
            Map<String, String> files = transpile(program);
            // A random key keeps this compile away from every published entry; the staging directory
            // is deleted however the compile ends, so nothing is left behind. The generated sources
            // are discarded before javac's answer is returned: --keep-sources would only preserve
            // files that are deleted a moment later, so it applies to run alone.
            CompilationCache scratch = CompilationCache.staging(CacheKey.cacheRoot(), CacheKey.unique());
            try {
                return compileInto(scratch, files, false, program.classpath(), err) ? 0 : 1;
            } finally {
                scratch.abandon();
            }
        });
    }

    /**
     * Loads and checks {@code fileName}, then hands the loaded program to {@code use}.
     *
     * <p>Every failure before that point — a missing file, an unusable classpath, source diagnostics —
     * is reported here and returned as the exit status, so the two commands differ only in how they
     * turn a loaded program into a result.
     */
    private static int withProgram(String fileName, List<String> cpOptions, PrintStream err, Use use) {
        Path source = Path.of(fileName);
        if (!Files.isRegularFile(source)) {
            err.println("solvik: no such file: " + fileName);
            return 1;
        }
        try {
            JavaClasspath classpath;
            try { classpath = JavaClasspath.parse(cpOptions); }
            catch (IOException invalid) { err.println("solvik: invalid classpath: " + invalid.getMessage()); return 2; }
            Diagnostics diagnostics = new Diagnostics();
            ProgramLoader.Loaded loaded = new ProgramLoader(diagnostics).load(source);
            if (diagnostics.hasErrors()) {
                err.print(new DiagnosticReporter().report(loaded.sources(),
                        DiagnosticReporter.displayName(source), diagnostics.all()));
                return 1;
            }
            return use.apply(new LoadedProgram(loaded, classpath));
        } catch (SolvikCompiler.CompilationFailure failure) {
            err.print(failure.getMessage());
            return 1;
        } catch (IOException io) {
            err.println("solvik: cannot read or write: " + io);
            return 1;
        } catch (RuntimeException internal) {
            // A compiler bug must not look like a user error: report it plainly and exit non-zero.
            err.println("solvik: internal error: " + internal);
            return 1;
        }
    }

    /**
     * Transpiles a loaded program to Java source files keyed by name.
     *
     * @throws SolvikCompiler.CompilationFailure carrying the rendered diagnostics when any phase fails
     */
    private static Map<String, String> transpile(LoadedProgram program) {
        return new SolvikCompiler("entry", program.classpath()).transpile(program.loaded());
    }

    /**
     * Compiles already-transpiled files into a private staging entry, then publishes it, or returns
     * {@code null} on failure. Nothing is visible to other processes until publishing succeeds.
     */
    private static CompilationCache compile(Map<String, String> files, Path root, String key,
                                            boolean keepSources, JavaClasspath classpath, PrintStream err)
            throws IOException {
        CompilationCache cache = CompilationCache.staging(root, key);
        try {
            if (!compileInto(cache, files, keepSources, classpath, err)) {
                cache.abandon();
                return null;
            }
            return cache.publish();
        } catch (IOException | RuntimeException failure) {
            cache.abandon();
            throw failure;
        }
    }

    /**
     * Writes the generated Java into {@code cache} and compiles it with the JDK compiler.
     *
     * @return {@code false} when {@code javac} rejected the generated Java, after reporting why
     */
    private static boolean compileInto(CompilationCache cache, Map<String, String> files,
                                       boolean keepSources, JavaClasspath classpath, PrintStream err)
            throws IOException {
        String dependencyFingerprint = classpath.fingerprint();
        Path[] sources = cache.writeSources(files);
        if (!compileJava(sources, cache.classesDirectory(), classpath, err)) {
            return false;
        }
        if (!keepSources) {
            // Only class files are needed from here on; --keep-sources opts out.
            cache.discardSources();
        }
        if (!dependencyFingerprint.equals(classpath.fingerprint()))
            throw new IOException("native classpath changed during compilation; retry");
        return true;
    }

    private static boolean compileJava(Path[] sources, Path classes, JavaClasspath classpath, PrintStream err)
            throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            err.println("solvik: no Java compiler available; run on a JDK, not a JRE");
            return false;
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager =
                     compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_PATH, classpath.entries());
            List<String> options = List.of("--release", JavaTarget.RELEASE_OPTION, "-proc:none",
                    "-d", classes.toString());
            StringWriter output = new StringWriter();
            boolean ok = compiler.getTask(output, fileManager, diagnostics, options, null,
                    fileManager.getJavaFileObjects(sources)).call();
            if (!ok) {
                diagnostics.getDiagnostics().forEach(diagnostic ->
                        err.println("solvik: generated Java did not compile: " + diagnostic));
                String detail = output.toString();
                if (!detail.isBlank()) {
                    err.print(detail);
                }
            }
            return ok;
        }
    }

    /** An abort reported by this process rather than by the generated runtime. */
    private record Abort(String code, String message) {
    }

    /**
     * The stable code and message for an abort the runtime did not report itself (§10).
     *
     * <p>Array bounds and null dereferences surface as the JVM's own exceptions, which carry no
     * code; mapping them here keeps every abort reportable by code even when the failing operation
     * is a Java construct rather than a Solvik runtime call.
     *
     * @return the abort to report, or {@code null} when the failure has no mapped code
     */
    private static Abort abortFor(Throwable cause) {
        if (cause instanceof IndexOutOfBoundsException) {
            return new Abort("SOL-R004", "array index out of bounds");
        }
        if (cause instanceof NullPointerException) {
            return new Abort("SOL-R008", "a null value was used where a value is required");
        }
        return null;
    }

    /**
     * The underlying failure of a class-initialization error.
     *
     * <p>A static field initializer or {@code static { ... }} block runs inside the generated
     * {@code <clinit>}; Java wraps any failure in {@code ExceptionInInitializerError}, and a later
     * use of the erroneous class may add {@code NoClassDefFoundError}. Unwrapping lets the normal
     * exit/exception/abort classification report the cause the program actually produced.
     */
    private static Throwable initializationCause(Throwable failure) {
        Throwable current = failure;
        while (current != null
                && (current instanceof ExceptionInInitializerError
                        || current instanceof NoClassDefFoundError)) {
            Throwable next = current.getCause();
            if (next == null || next == current) {
                break;
            }
            current = next;
        }
        return current;
    }

    private static int exitStatus(Throwable exit) {
        try {
            return ((Number) exit.getClass().getMethod("code").invoke(exit)).intValue();
        } catch (ReflectiveOperationException notAnExit) {
            return 1;
        }
    }

    /**
     * The {@code [code] message} text of an uncaught Solvik exception (§7.4).
     *
     * <p>The generated class is loaded by the program's own class loader, so its accessors are read
     * reflectively rather than by linking the compiler to generated code.
     */
    private static String exceptionText(Throwable raised) {
        try {
            Object code = raised.getClass().getMethod("getCode").invoke(raised);
            String message = raised.getMessage();
            return "[" + code + "] " + (message == null ? "" : message);
        } catch (ReflectiveOperationException notAnException) {
            return String.valueOf(raised);
        }
    }

    /** Runs the cached program in this JVM, with program output going to {@code out}/{@code err}. */
    private static int execute(CompilationCache cache, JavaClasspath classpath, PrintStream out, PrintStream err) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        System.setOut(out);
        System.setErr(err);
        try (URLClassLoader dependencies = classpath.openLoader();
                URLClassLoader loader = new URLClassLoader(
                        new URL[]{cache.classesDirectory().toUri().toURL()}, dependencies)) {
            Class<?> program = loader.loadClass(PROGRAM_CLASS);
            program.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            return 0;
        } catch (InvocationTargetException failure) {
            Throwable cause = initializationCause(failure.getCause());
            // exit() carries its status out as a control-flow exception rather than killing the JVM.
            if (cause != null && GeneratedNames.exitBinaryName().equals(cause.getClass().getName())) {
                return exitStatus(cause);
            }
            if (cause != null && GeneratedNames.errorBinaryName().equals(cause.getClass().getName())) {
                // The runtime already reported this abort with its stable code (§10).
                return 1;
            }
            if (cause != null
                    && GeneratedNames.exceptionBinaryName().equals(cause.getClass().getName())) {
                // A raised exception no clause handled: report it by code and exit 1 (§7.4).
                err.println("solvik: runtime error [SOL-R013]: uncaught exception "
                        + exceptionText(cause));
                return 1;
            }
            Abort abort = abortFor(cause);
            if (abort == null) {
                err.println("solvik: the program failed: " + cause);
            } else {
                err.println("solvik: runtime error [" + abort.code() + "]: " + abort.message());
            }
            return 1;
        } catch (ReflectiveOperationException | IOException problem) {
            err.println("solvik: cannot run the compiled program: " + problem);
            return 1;
        } finally {
            out.flush();
            err.flush();
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }
}
