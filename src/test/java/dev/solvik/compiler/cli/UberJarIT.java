package dev.solvik.compiler.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies the packaged uber jar: run by the failsafe plugin, after {@code package}, so the artifact
 * under test is the one users actually receive.
 */
@DisplayName("Uber jar")
class UberJarIT {

    private static final Path JAR = Path.of("target", "solvik.jar");

    @Test
    @DisplayName("is a single executable jar with a main class and no test dependencies")
    void has_a_main_class_and_no_test_dependencies() throws IOException {
        assumeTrue(Files.isRegularFile(JAR), "run through ./mvnw verify to build the jar first");

        try (JarFile jar = new JarFile(JAR.toFile())) {
            assertThat(jar.getManifest().getMainAttributes().getValue("Main-Class"))
                    .isEqualTo("dev.solvik.compiler.cli.Main");
            assertThat(jar.getEntry("dev/solvik/compiler/cli/Main.class")).isNotNull();
            assertThat(jar.getEntry("dev/solvik/compiler/transpile/GeneratedIntrinsics.class")).isNotNull();
            assertThat(jar.stream().map(entry -> entry.getName()).toList())
                    .describedAs("test dependencies must not be bundled")
                    .noneMatch(name -> name.startsWith("org/junit/") || name.startsWith("org/assertj/"));
        }
    }

    @Test
    @DisplayName("ships one resource per Solvik standard-library type and no legacy resources")
    void ships_individual_standard_library_bindings() throws IOException {
        try (JarFile jar = new JarFile(JAR.toFile())) {
            assertThat(jar.stream().map(entry -> entry.getName()).filter(name -> name.endsWith(".sol")))
                    .containsExactlyInAnyOrder("solvik/collections/Map.sol", "solvik/collections/Stack.sol",
                            "solvik/collections/List.sol", "solvik/concurrency/Task.sol",
                            "solvik/concurrency/Thread.sol", "solvik/util/UUID.sol",
                            "solvik/io/Channel.sol", "solvik/io/Stdio.sol",
                            "solvik/io/TextReader.sol", "solvik/io/TextWriter.sol",
                            "solvik/io/IoError.sol", "solvik/io/ByteReader.sol",
                            "solvik/io/ByteWriter.sol", "solvik/io/File.sol");
            assertThat(jar.getEntry("stdlib/java.sol")).isNull();
        }
    }

    @Test
    @DisplayName("runs every packaged standard-library type from the standalone jar")
    void runs_the_packaged_standard_library(@TempDir Path directory) throws Exception {
        Path source = directory.resolve("collections.sol");
        Files.writeString(source, """
                type SumTask implements solvik::concurrency::Task {
                    mut result := 0
                    pub func SumTask() { }
                    pub func run() { result = 42 }
                    pub func value() -> Integer { return result }
                }

                map := solvik::collections::Map<String, Integer>()
                map.put("answer", 42)
                stack := solvik::collections::Stack<Integer>()
                mut answer: Integer? = map.get("answer")
                if answer != Null {
                    stack.push(answer)
                }
                list := solvik::collections::List<Integer>()
                mut top: Integer? = stack.pop()
                if top != Null {
                    list.add(top)
                }
                mut id: solvik::util::UUID? = solvik::util::UUID.parse("0190F2A4-7B3C-7A1D-8E2F-3C4D5E6F7A8B")
                if id != Null {
                    TextWriter(Stdio.stdout()).writeLine((map.size()) .. (" ") .. (stack.empty()) .. (" ") .. (list.get(0)))
                    TextWriter(Stdio.stdout()).writeLine((id.toString()) .. (" ") .. (id.timestamp()) .. (" ") .. (id.compareTo(id) == 0))
                }
                task := SumTask()
                thread := solvik::concurrency::Thread.start(task)
                thread.await()
                TextWriter(Stdio.stdout()).writeLine((task.value()) .. (" ") .. (thread.isAlive()))
                """, StandardCharsets.UTF_8);
        Process process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Dsolvik.cache.dir=" + directory.resolve("cache"),
                "-jar", JAR.toString(), "run", source.toString())
                .redirectErrorStream(true)
                .start();
        assertThat(process.waitFor(120, TimeUnit.SECONDS)).isTrue();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
        assertThat(process.exitValue()).describedAs("output: %s", output).isZero();
        assertThat(output).isEqualTo("1 true 42\n"
                + "0190f2a4-7b3c-7a1d-8e2f-3c4d5e6f7a8b " + 0x0190f2a47b3cL + " true\n"
                + "42 false\n");
    }

    @Test
    @DisplayName("bundles no third-party runtime dependency")
    void bundles_no_third_party_runtime() throws IOException {
        try (JarFile jar = new JarFile(JAR.toFile())) {
            assertThat(jar.stream().map(entry -> entry.getName()).toList())
                    .describedAs("the compiler renders Java itself and has no runtime dependency")
                    .noneMatch(name -> name.startsWith("com/helger/")
                            || name.startsWith("org/slf4j/") || name.startsWith("org/jspecify/"));
            assertThat(jar.getEntry("dev/solvik/compiler/jir/JavaSourceRenderer.class"))
                    .describedAs("the purpose-built renderer is the code-generation path")
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("transpiles, compiles, and runs a program end to end")
    void runs_a_program(@TempDir Path directory) throws Exception {
        assumeTrue(Files.isRegularFile(JAR), "run through ./mvnw verify to build the jar first");

        Path source = directory.resolve("demo.sol");
        Files.writeString(source, """
                type Point {
                    x := 0

                    pub func Point(x: Integer) {
                        self.x = x
                    }

                    pub func toString() -> String {
                        return "Point(" .. self.x .. ")"
                    }
                }

                p := Point(7)
                TextWriter(Stdio.stdout()).writeLine((p.toString()) .. (" ") .. (p.type()))
                """, StandardCharsets.UTF_8);

        Process process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Dsolvik.cache.dir=" + directory.resolve("cache"),
                "-jar", JAR.toString(), "run",
                source.toString())
                .redirectErrorStream(true)
                .start();

        assertThat(process.waitFor(120, TimeUnit.SECONDS)).isTrue();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
        assertThat(process.exitValue()).describedAs("output: %s", output).isZero();
        assertThat(output).contains("Point(7) Point");
    }

    @Test
    @DisplayName("rejects an unknown command word with the usage exit code")
    void rejects_an_unknown_command(@TempDir Path directory) throws Exception {
        assumeTrue(Files.isRegularFile(JAR), "run through ./mvnw verify to build the jar first");

        Path source = directory.resolve("demo.sol");
        Files.writeString(source, "value := 1\n", StandardCharsets.UTF_8);

        Process process = new ProcessBuilder(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Dsolvik.cache.dir=" + directory.resolve("cache"),
                "-jar", JAR.toString(), "transpile", source.toString()))
                .redirectErrorStream(true)
                .start();

        assertThat(process.waitFor(120, TimeUnit.SECONDS)).isTrue();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).isEqualTo(2);
        assertThat(output).contains("unknown command: transpile").contains("usage: solvik")
                .contains("run");
        assertThat(directory.resolve("cache"))
                .describedAs("a rejected command line must not compile anything")
                .doesNotExist();
    }

    @Test
    @DisplayName("checks a program through the jar without running it")
    void checks_a_program_without_running_it(@TempDir Path directory) throws Exception {
        assumeTrue(Files.isRegularFile(JAR), "run through ./mvnw verify to build the jar first");

        Path source = directory.resolve("checked.sol");
        Files.writeString(source, """
                TextWriter(Stdio.stdout()).writeLine(("must not run") .. "")
                exit(7)
                """, StandardCharsets.UTF_8);

        Process process = new ProcessBuilder(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Dsolvik.cache.dir=" + directory.resolve("cache"),
                "-jar", JAR.toString(), "check", source.toString()))
                .redirectErrorStream(true)
                .start();

        assertThat(process.waitFor(120, TimeUnit.SECONDS)).isTrue();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).describedAs("output: %s", output).isZero();
        assertThat(output).describedAs("check must not run the program").isEmpty();

        Path broken = directory.resolve("broken-check.sol");
        Files.writeString(broken, "a := 1 + \"x\"\n", StandardCharsets.UTF_8);

        Process rejected = new ProcessBuilder(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Dsolvik.cache.dir=" + directory.resolve("cache"),
                "-jar", JAR.toString(), "check", broken.toString()))
                .redirectErrorStream(true)
                .start();

        assertThat(rejected.waitFor(120, TimeUnit.SECONDS)).isTrue();
        String diagnostic = new String(rejected.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(rejected.exitValue()).isEqualTo(1);
        assertThat(diagnostic).contains("SOL-T001").contains("broken-check.sol:1:8");
    }

    @Test
    @DisplayName("reports compile errors through the jar with the documented exit code")
    void reports_compile_errors(@TempDir Path directory) throws Exception {
        assumeTrue(Files.isRegularFile(JAR), "run through ./mvnw verify to build the jar first");

        Path source = directory.resolve("bad.sol");
        Files.writeString(source, "a := 1 + \"x\"\n", StandardCharsets.UTF_8);

        Process process = new ProcessBuilder(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Dsolvik.cache.dir=" + directory.resolve("cache"),
                "-jar", JAR.toString(), "run",
                source.toString()))
                .redirectErrorStream(true)
                .start();

        assertThat(process.waitFor(120, TimeUnit.SECONDS)).isTrue();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).isEqualTo(1);
        assertThat(output).contains("SOL-T001").contains("bad.sol:1:8");
    }
}
