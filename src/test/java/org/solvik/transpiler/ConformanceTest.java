package org.solvik.transpiler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * End-to-end conformance suite for the Solvik-to-Java transpiler.
 *
 * <p>Each fixture directory under {@code test/cases/} is transpiled in-process,
 * the generated Java is compiled with {@code javac --release 17 -Xlint:all
 * -Werror}, and the resulting program is executed in a separate JVM. Source
 * diagnostics must exit the CLI with code {@code 1}; generated runtime
 * failures must exit with code {@code 2}; successful programs must match their
 * golden {@code expected.out}. Error-case {@code expected.out} files describe
 * the removed native VM's messages and are therefore not compared, matching
 * the previous Java runner. The suite also pins the top-level
 * {@code example.sol} smoke test, wrapper-name collision behavior, and
 * single-file independence.</p>
 */
final class ConformanceTest {
    private static final Path BASEDIR = Path.of(System.getProperty("solvik.basedir", System.getProperty("user.dir")))
            .toAbsolutePath().normalize();
    private static final Path CASES = BASEDIR.resolve("test/cases");
    private static final String JAVA = executable("java");
    private static final String JAVAC = executable("javac");
    private static final Pattern PACKAGE_DECLARATION = Pattern.compile("^package\\s", Pattern.MULTILINE);

    private ConformanceTest() {}

    static Stream<Arguments> fixtures() throws IOException {
        try (Stream<Path> entries = Files.list(CASES)) {
            List<Arguments> ordered = new ArrayList<>();
            List<Path> directories = entries.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            int index = 0;
            for (Path directory : directories) {
                if (Files.isRegularFile(directory.resolve("main.sol"))) {
                    ordered.add(Arguments.of(directory.getFileName().toString(), index++, directory));
                }
            }
            return ordered.stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void fixture(String name, int index, Path directory, @TempDir Path work) throws Exception {
        Path main = directory.resolve("main.sol");
        Path expectedOut = directory.resolve("expected.out");
        int expectedCode = expectedCode(directory);
        String className = "SolvikCase" + index;
        String javaSource;
        try {
            javaSource = Transpiler.toJava(relativeName(main), Files.readString(main, StandardCharsets.UTF_8), className);
        } catch (CompileException e) {
            assertEquals(1, expectedCode, () -> name + ": unexpected compile failure: " + e.diagnostics());
            return;
        }
        assertNotEquals(1, expectedCode, () -> name + ": expected a source diagnostic but transpilation succeeded");
        assertFalse(PACKAGE_DECLARATION.matcher(javaSource).find(),
                () -> name + ": generated source contains a package declaration");

        Path javaFile = work.resolve(className + ".java");
        Files.writeString(javaFile, javaSource, StandardCharsets.UTF_8);
        Path classes = compile(work, javaFile);
        List<String> arguments = Files.isRegularFile(directory.resolve("args.txt"))
                ? Files.readAllLines(directory.resolve("args.txt"), StandardCharsets.UTF_8)
                : List.of();
        Path stdin = Files.isRegularFile(directory.resolve("stdin.txt")) ? directory.resolve("stdin.txt") : null;
        ProcessResult result;
        try {
            result = runProgram(work, classes, className, arguments, stdin);
        } finally {
            // The file-stdlib fixture runs from the repository root and normally
            // deletes its scratch file; clean up if a failing run left one behind.
            Files.deleteIfExists(BASEDIR.resolve("tmp-file-stdlib-note.txt"));
        }
        assertEquals(expectedCode, result.exitCode(), () -> name + ": runtime output:\n" + result.output());
        if (expectedCode == 0 && Files.isRegularFile(expectedOut)) {
            assertEquals(stripTrailingNewlines(Files.readString(expectedOut, StandardCharsets.UTF_8)),
                    stripTrailingNewlines(result.output()),
                    () -> name + ": output differs from expected.out");
        }
    }

    @Test
    void exampleProgramTranspilesCompilesAndRuns(@TempDir Path work) throws Exception {
        Path example = BASEDIR.resolve("example.sol");
        String javaSource = Transpiler.toJava(example.toString(), Files.readString(example, StandardCharsets.UTF_8),
                "SolvikExample");
        Path javaFile = work.resolve("SolvikExample.java");
        Files.writeString(javaFile, javaSource, StandardCharsets.UTF_8);
        Path classes = compile(work, javaFile);
        ProcessResult result = runProgram(work, classes, "SolvikExample", List.of(), null);
        assertEquals(0, result.exitCode(), () -> "example.sol failed:\n" + result.output());
    }

    @Test
    void wrapperNameCollisionKeepsGeneratedEntryPoint(@TempDir Path work) throws Exception {
        Path main = CASES.resolve("01-hello/main.sol");
        String javaSource = Transpiler.toJava(relativeName(main), Files.readString(main, StandardCharsets.UTF_8), "Main");
        Path javaFile = work.resolve("Main.java");
        Files.writeString(javaFile, javaSource, StandardCharsets.UTF_8);
        Path classes = compile(work, javaFile);
        ProcessResult result = runProgram(work, classes, "Main", List.of(), null);
        assertEquals(0, result.exitCode(), () -> "wrapper-collision runtime output:\n" + result.output());
        assertEquals("hello\nworld!", stripTrailingNewlines(result.output()));
    }

    @Test
    void generatedFileIsSelfContained(@TempDir Path work) throws Exception {
        Path main = CASES.resolve("01-hello/main.sol");
        String javaSource = Transpiler.toJava(relativeName(main), Files.readString(main, StandardCharsets.UTF_8), "Program");
        Path source = Files.createDirectories(work.resolve("source"));
        Path javaFile = source.resolve("Program.java");
        Files.writeString(javaFile, javaSource, StandardCharsets.UTF_8);

        Path isolated = Files.createDirectories(work.resolve("isolated"));
        Files.copy(javaFile, isolated.resolve("Program.java"));
        Path classes = Files.createDirectories(isolated.resolve("classes"));
        ProcessResult compilation = run(isolated, isolated, List.of(JAVAC, "--release", "17", "-Xlint:all", "-Werror",
                "-d", classes.toString(), "Program.java"), null);
        assertEquals(0, compilation.exitCode(), () -> "isolated javac failed:\n" + compilation.output());

        ProcessResult result = run(isolated, isolated, List.of(JAVA, "-cp", classes.toString(), "Program"), null);
        assertEquals(0, result.exitCode(), () -> "isolated runtime output:\n" + result.output());
        assertEquals("hello\nworld!", stripTrailingNewlines(result.output()));
    }

    @Test
    void cliRejectsSourceDiagnosticsWithExitCodeOne(@TempDir Path work) throws Exception {
        Path main = CASES.resolve("18-compile-error/main.sol");
        ProcessResult result = run(work, work, List.of(JAVA, "-cp", BASEDIR.resolve("target/classes").toString(),
                "org.solvik.transpiler.SolvikTranspiler", main.toString(), "CliFailure"), null);
        assertEquals(1, result.exitCode(), () -> "CLI diagnostics exit code:\n" + result.output());
        assertFalse(Files.exists(work.resolve("CliFailure.java")), "failed compilation must not write output");
    }

    @Test
    void cliRejectsBadUsageWithExitCodeThree(@TempDir Path work) throws Exception {
        ProcessResult result = run(work, work, List.of(JAVA, "-cp", BASEDIR.resolve("target/classes").toString(),
                "org.solvik.transpiler.SolvikTranspiler"), null);
        assertEquals(3, result.exitCode(), () -> "CLI usage exit code:\n" + result.output());
    }

    private static Path compile(Path work, Path javaFile) throws Exception {
        Path classes = Files.createDirectories(work.resolve("classes"));
        ProcessResult result = run(work, work, List.of(JAVAC, "--release", "17", "-Xlint:all", "-Werror",
                "-d", classes.toString(), javaFile.toString()), null);
        assertEquals(0, result.exitCode(), () -> "javac failed:\n" + result.output());
        return classes;
    }

    private static ProcessResult runProgram(Path work, Path classes, String className, List<String> arguments, Path stdin)
            throws Exception {
        List<String> command = new ArrayList<>(List.of(JAVA, "-cp", classes.toString(), className));
        command.addAll(arguments);
        // Fixtures run from the repository root, matching the previous runner; some
        // resolve file-system paths relative to it. Generated classes stay in work.
        return run(BASEDIR, work, command, stdin);
    }

    private static ProcessResult run(Path processDirectory, Path tempDirectory, List<String> command, Path stdin)
            throws IOException, InterruptedException {
        Path outputFile = Files.createTempFile(tempDirectory, "process-", ".out");
        Path inputFile = stdin != null ? stdin : Files.createTempFile(tempDirectory, "empty-", ".in");
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(processDirectory.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(outputFile.toFile());
        builder.redirectInput(inputFile.toFile());
        try {
            Process process = builder.start();
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor();
                throw new AssertionError("process timed out: " + command);
            }
            return new ProcessResult(process.exitValue(), Files.readString(outputFile, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(outputFile);
            if (stdin == null) Files.deleteIfExists(inputFile);
        }
    }

    private static int expectedCode(Path directory) throws IOException {
        Path code = directory.resolve("expected.code");
        if (!Files.isRegularFile(code)) return 0;
        return Integer.parseInt(Files.readString(code, StandardCharsets.UTF_8).trim());
    }

    private static String relativeName(Path file) {
        return BASEDIR.relativize(file).toString().replace('\\', '/');
    }

    private static String stripTrailingNewlines(String value) {
        int end = value.length();
        while (end > 0 && (value.charAt(end - 1) == '\n' || value.charAt(end - 1) == '\r')) end--;
        return value.substring(0, end);
    }

    private static String executable(String name) {
        String fileName = System.getProperty("os.name").toLowerCase().contains("win") ? name + ".exe" : name;
        return Path.of(System.getProperty("java.home"), "bin", fileName).toString();
    }

    private record ProcessResult(int exitCode, String output) {}
}
