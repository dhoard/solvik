package dev.solvik.compiler.cli;

import dev.solvik.compiler.transpile.GeneratedNames;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Map;

/**
 * On-disk layout of one cached compilation, published atomically.
 *
 * <pre>
 * &lt;cache root&gt;/&lt;key&gt;/
 *     classes/        compiled classes — the only artifact a cache hit needs
 *     complete        marker written only after a successful compile
 * </pre>
 *
 * <p>Generated Java sources live in {@code src/} only while compiling and are deleted once the
 * classes exist.
 *
 * <p>Two properties matter for correctness when several terminals run the command at once:
 *
 * <ul>
 *   <li><b>Readers never see a partial entry.</b> Work happens in a private staging directory named
 *       after the process and a nanosecond stamp, and is moved into place with a single atomic
 *       rename that happens only after the {@code complete} marker is written.</li>
 *   <li><b>Nothing is ever deleted under a running program.</b> A published entry is immutable, so a
 *       process executing class files from it is unaffected by other processes. The loser of a race
 *       discards its own staging directory and uses the winner's entry, which is equivalent because
 *       the key already covers the source text.</li>
 * </ul>
 */
public final class CompilationCache {

    private static final String MARKER = "complete";

    private final Path entry;
    private final Path staging;

    private CompilationCache(Path entry, Path staging) {
        this.entry = entry;
        this.staging = staging;
    }

    /** Returns the usable cache entry for {@code key}, or {@code null} when it is not reusable. */
    public static CompilationCache openIfUsable(Path root, String key) {
        Path entry = root.resolve(key);
        return isComplete(entry) ? new CompilationCache(entry, null) : null;
    }

    /**
     * Starts a fresh entry in a private staging directory. The entry becomes visible to other
     * processes only when {@link #publish()} succeeds.
     */
    public static CompilationCache staging(Path root, String key) throws IOException {
        Files.createDirectories(root);
        Path staging = root.resolve(".staging-" + key + "-"
                + ProcessHandle.current().pid() + "-" + System.nanoTime());
        Files.createDirectories(staging.resolve("src"));
        Files.createDirectories(staging.resolve("classes"));
        return new CompilationCache(root.resolve(key), staging);
    }

    public Path sourceDirectory() {
        return directory().resolve("src");
    }

    public Path classesDirectory() {
        return directory().resolve("classes");
    }

    private Path directory() {
        return staging != null ? staging : entry;
    }

    /** Writes the generated Java files into {@code src/} and returns their paths. */
    public Path[] writeSources(Map<String, String> files) throws IOException {
        Path[] paths = new Path[files.size()];
        int index = 0;
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path target = sourceDirectory().resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue(), StandardCharsets.UTF_8);
            paths[index++] = target;
        }
        return paths;
    }

    /**
     * Publishes the staged entry: marks it complete, then moves it into place atomically.
     *
     * <p>If another process published the same key first, its entry is kept and this one is
     * discarded. Both contain the same program, because the key covers the source text.
     */
    public CompilationCache publish() throws IOException {
        Path source = staging;
        if (source == null) {
            return this;
        }
        Files.writeString(source.resolve(MARKER), "ok", StandardCharsets.UTF_8);
        try {
            Files.move(source, entry, StandardCopyOption.ATOMIC_MOVE);
            return new CompilationCache(entry, null);
        } catch (IOException lostRace) {
            deleteRecursively(source);
            if (isComplete(entry)) {
                return new CompilationCache(entry, null);
            }
            throw lostRace;
        }
    }

    /**
     * Removes the generated sources once they have been compiled.
     *
     * <p>Only class files are needed to run a cached program, so the Java sources are dropped before
     * publishing; the cache then holds no duplicated copy of code that can no longer be used.
     */
    public void discardSources() throws IOException {
        deleteRecursively(directory().resolve("src"));
    }

    /** Discards a staged entry after a failed compile; a published entry is left untouched. */
    public void abandon() {
        if (staging == null) {
            return;
        }
        try {
            deleteRecursively(staging);
        } catch (IOException ignored) {
            // best effort: a leftover staging directory is never treated as a cache hit
        }
    }

    private static boolean isComplete(Path entry) {
        String programClass = GeneratedNames.PROGRAM_BINARY_NAME.replace('.', '/') + ".class";
        return Files.isRegularFile(entry.resolve(MARKER))
                && Files.isRegularFile(entry.resolve("classes").resolve(programClass));
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
