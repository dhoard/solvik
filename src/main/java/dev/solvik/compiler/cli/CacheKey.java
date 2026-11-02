package dev.solvik.compiler.cli;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Derives the cache key for one compilation, and decides where the cache lives.
 *
 * <p>A cache entry is valid only for a given source text <em>and</em> a given compiler build, so the
 * key combines a digest of the source with a stamp of the compiler's own class files. When the
 * compiler is rebuilt the stamp changes, and stale class files can never be reused.
 */
public final class CacheKey {

    private static final String CACHE_DIR_PROPERTY = "solvik.cache.dir";
    private static final String CACHE_DIR_ENV = "SOLVIK_CACHE_DIR";

    private CacheKey() {
    }

    /**
     * A stable key for a program loaded from an entry file and its imports.
     *
     * <p>{@code keepSources} is part of the key: an entry that retains generated Java is a different
     * artifact from one that does not, so the two never share a directory.
     *
     * @param fingerprint every file's name and text, in load order (§3, "Source files")
     */
    public static String of(String fingerprint, boolean keepSources) {
        return sha256(compilerStamp() + '\u0000'
                + (keepSources ? "keep-sources" : "classes-only") + '\u0000' + fingerprint);
    }

    /**
     * A key no other compilation uses or will reuse.
     *
     * <p>{@code check} compiles into a scratch entry and deletes it: a random key guarantees that the
     * entry can neither collide with a published one nor be mistaken for a cache hit by another
     * process, even when many checks run at once.
     */
    public static String unique() {
        return "scratch-" + UUID.randomUUID();
    }

    /**
     * Resolves the cache root: {@code -Dsolvik.cache.dir}, then {@code SOLVIK_CACHE_DIR}, then
     * {@code $XDG_CACHE_HOME/solvik}, then {@code ~/.cache/solvik}.
     */
    public static Path cacheRoot() {
        String property = System.getProperty(CACHE_DIR_PROPERTY);
        if (property != null && !property.isBlank()) {
            return Path.of(property);
        }
        String environment = System.getenv(CACHE_DIR_ENV);
        if (environment != null && !environment.isBlank()) {
            return Path.of(environment);
        }
        String xdg = System.getenv("XDG_CACHE_HOME");
        Path base = xdg != null && !xdg.isBlank()
                ? Path.of(xdg)
                : Path.of(System.getProperty("user.home"), ".cache");
        return base.resolve("solvik");
    }

    /**
     * A stamp for the running compiler build: the newest modification time of the class files that
     * make it up, so a rebuild invalidates every cache entry.
     */
    private static String compilerStamp() {
        try {
            URL location = CacheKey.class.getProtectionDomain().getCodeSource().getLocation();
            Path path = Path.of(location.toURI());
            if (Files.isDirectory(path)) {
                try (Stream<Path> files = Files.walk(path)) {
                    long newest = files.filter(Files::isRegularFile)
                            .mapToLong(file -> file.toFile().lastModified())
                            .max()
                            .orElse(0L);
                    return Long.toString(newest);
                }
            }
            return Long.toString(Files.getLastModifiedTime(path).toMillis());
        } catch (Exception unavailable) {
            return "unknown"; // still deterministic for a given session
        }
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the JVM", impossible);
        }
    }
}
