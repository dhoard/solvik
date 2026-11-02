package dev.solvik.compiler.transpile;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.SolvikCompiler;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConcurrencyExecutionTest {

    private static final String TASK = """
            type SumTask implements solvik::concurrency::Task {
                left := 0
                right := 0
                mut answer := 0

                pub func SumTask(left: Integer, right: Integer) {
                    self.left = left
                    self.right = right
                }

                pub func run() {
                    answer = left + right
                }

                pub func result() -> Integer {
                    return answer
                }
            }

            task := SumTask(20, 22)
            thread := solvik::concurrency::Thread.start(task)
            thread.await()
            thread.await(1L)
            TextWriter(Stdio.stdout()).writeLine((task.result()) .. (" ") .. (thread.isAlive()) .. (" ") .. (thread.isInterrupted()))
            """;

    private static Map<String, String> transpile(String source) {
            return new SolvikCompiler("concurrency.sol").transpile(source);
    }

    private static String output(String source, Path directory) throws Exception {
        Map<String, String> files = transpile(source);
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
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (URLClassLoader loader = new URLClassLoader(new URL[]{classes.toUri().toURL()}, null);
                PrintStream captured = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            System.setOut(captured);
            loader.loadClass(GeneratedNames.PROGRAM_BINARY_NAME)
                    .getMethod("main", String[].class)
                    .invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(originalOut);
        }
        return buffer.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    @Test
    void a_task_runs_on_a_virtual_thread_and_publishes_state(@TempDir Path directory) throws Exception {
        Map<String, String> files = transpile(TASK);
        assertThat(files.get("solvik/concurrency/Task.java"))
                .contains("interface Task", "extends Runnable");
        assertThat(files.get("solvik/concurrency/Thread.java"))
                .contains("java.lang.Thread.startVirtualThread", "void await()",
                        "void await(Long milliseconds)")
                .doesNotContain("isVirtual");
        assertThat(files.get("SumTask.java")).contains("implements", "Task");
        assertThat(output(TASK, directory)).isEqualTo("42 false false\n");
    }

    @Test
    void overloads_execute_for_functions_methods_and_constructors(@TempDir Path directory) throws Exception {
        String source = """
                func describe(value: Integer) -> String { return "integer" }
                func describe(value: String) -> String { return "string" }
                func nullable(value: Any?) -> String { return "any" }
                func nullable(value: String?) -> String { return "string" }

                type Box {
                    mut size := 0
                    pub func Box(value: Integer) { size = value }
                    pub func Box(value: String) { size = value.size() }
                    pub func put(value: Integer) { size = value }
                    pub func put(value: String) { size = value.size() }
                    pub func get() -> Integer { return size }
                }

                one := Box(7)
                two := Box("abc")
                one.put("hello")
                two.put(9)
                TextWriter(Stdio.stdout()).writeLine((describe(1)) .. (" ") .. (describe("x")) .. (" ") .. (nullable(Null)) .. (" ") .. (one.get()) .. (" ") .. (two.get()))
                """;
        Map<String, String> files = transpile(source);
        assertThat(files.get("Solvik$Program.java")).contains("(String) null");
        assertThat(output(source, directory))
                .isEqualTo("integer string string 5 9\n");
    }
}
