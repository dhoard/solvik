package dev.solvik.compiler.bind;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/**
 * Ordered, explicit application dependencies; never implicitly inherits the compiler classpath.
 *
 * <p>Only readable JAR files are accepted. Directories, wildcards, empty entries, and manifest
 * {@code Class-Path} attributes are rejected, because each would make the resolved classpath — and
 * therefore the cache key — depend on something other than the arguments the caller wrote.
 */
public record JavaClasspath(List<Path> entries) {

    public JavaClasspath {
        entries = List.copyOf(entries);
    }

    public static JavaClasspath empty() {
        return new JavaClasspath(List.of());
    }

    /**
     * Parses {@code --cp} values: one or more platform-separated JAR paths, in the order given.
     *
     * @throws IOException when an entry is empty, wildcarded, unreadable, not a JAR, or declares a
     *                     manifest {@code Class-Path}
     */
    public static JavaClasspath parse(List<String> options) throws IOException {
        List<Path> entries = new ArrayList<>();
        for (String option : options) {
            for (String entry : option.split(Pattern.quote(File.pathSeparator), -1)) {
                entries.add(readEntry(entry));
            }
        }
        return new JavaClasspath(entries);
    }

    private static Path readEntry(String entry) throws IOException {
        if (entry.isEmpty() || entry.contains("*")) {
            throw new IOException(
                    "classpath requires explicit, non-empty JAR paths; wildcards are unsupported");
        }
        Path path;
        try {
            path = Path.of(entry).toRealPath();
        } catch (IOException | RuntimeException invalid) {
            throw new IOException("not a readable JAR: " + entry, invalid);
        }
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IOException("classpath entry is not a readable JAR: " + entry);
        }
        try (JarFile jar = new JarFile(path.toFile())) {
            // Resolving manifest Class-Path implicitly would make cache identity incomplete.
            if (jar.getManifest() != null
                    && jar.getManifest().getMainAttributes().getValue("Class-Path") != null) {
                throw new IOException("manifest Class-Path is unsupported; list dependencies "
                        + "explicitly: " + entry);
            }
        }
        return path;
    }

    public URLClassLoader openLoader() throws IOException {
        List<URL> urls = new ArrayList<>();
        for (Path entry : entries) {
            urls.add(entry.toUri().toURL());
        }
        return new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader());
    }

    /** The JVM version and the content digest of every entry, in order, as cache-key input. */
    public String fingerprint() throws IOException {
        StringBuilder text = new StringBuilder(System.getProperty("java.version"));
        MessageDigest digest = sha256();
        for (Path entry : entries) {
            digest.reset();
            try (InputStream input = Files.newInputStream(entry)) {
                byte[] bytes = new byte[8192];
                for (int count; (count = input.read(bytes)) != -1; ) {
                    digest.update(bytes, 0, count);
                }
            }
            text.append('\0').append(entry).append('\0')
                    .append(HexFormat.of().formatHex(digest.digest()));
        }
        return text.toString();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            // Every Java runtime is required to provide SHA-256.
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
