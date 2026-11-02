package dev.solvik.compiler.testsupport;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lets a test talk about a program's own output without restating the packaged library (§11).
 *
 * <p>Every packaged {@code .sol} resource is part of every program, so its generated Java always
 * appears beside the program's files. The packaged namespaces live under the reserved {@code solvik}
 * package, which no program file may use, so that prefix separates the two.
 */
public final class PackagedLibrary {

    private PackagedLibrary() {
    }

    /**
     * Packaged types declared at the top level, so their generated files sit beside the program.
     *
     * <p>The bundled I/O types are globally available by name and therefore have no module prefix;
     * they are still packaged declarations, not program output.
     */
    private static final Set<String> PACKAGED_GLOBAL_TYPES = Set.of(
            "ByteReader.java", "ByteWriter.java", "Channel.java", "File.java", "IoError.java",
            "Stdio.java", "TextReader.java", "TextWriter.java");

    /** Whether a generated Java file belongs to the packaged library rather than the program. */
    public static boolean isPackaged(String generatedFileName) {
        return generatedFileName.startsWith("solvik/")
                || PACKAGED_GLOBAL_TYPES.contains(generatedFileName);
    }

    /** The generated files of the program itself, in file-name order. */
    public static List<String> programFiles(Map<String, String> files) {
        return files.keySet().stream()
                .filter(name -> !isPackaged(name))
                .sorted()
                .toList();
    }

    /** The generated Java source of the program class, the one file every program emits. */
    public static String programClass(Map<String, String> files) {
        return files.get("Solvik$Program.java");
    }
}
