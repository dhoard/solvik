package dev.solvik.compiler.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.transpile.GeneratedNames;
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

/**
 * Compiles a transpiled program with {@code javac --release 25} and runs its entry point.
 *
 * <p>The generated sources are compiled against an empty classpath, so a program may depend only on
 * the JDK. The generated classes are loaded in a fresh loader whose parent is the boot loader, then
 * the program's {@code main} runs with a captured {@code System.out}. This is the same contract the
 * language-consistency suite enforces, packaged so a focused test can compare observable output.
 */
public final class GeneratedProgram {

    private GeneratedProgram() {
    }

    /** Writes, compiles, and runs {@code files} under {@code root}; returns its standard output. */
    public static String run(Map<String, String> files, Path root) throws Exception {
        Path sources = Files.createDirectories(root.resolve("src"));
        Path classes = Files.createDirectories(root.resolve("classes"));
        Path empty = Files.createDirectories(root.resolve("empty-classpath"));
        List<String> arguments = new ArrayList<>(List.of("--release", "25", "-classpath",
                empty.toString(), "-d", classes.toString()));
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path target = sources.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue(), StandardCharsets.UTF_8);
            arguments.add(target.toString());
        }
        assertThat(ToolProvider.getSystemJavaCompiler()
                .run(null, null, null, arguments.toArray(String[]::new))).isZero();
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (URLClassLoader loader = new URLClassLoader(new URL[]{classes.toUri().toURL()}, null);
                PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            System.setOut(out);
            loader.loadClass(GeneratedNames.PROGRAM_BINARY_NAME).getMethod("main", String[].class)
                    .invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
