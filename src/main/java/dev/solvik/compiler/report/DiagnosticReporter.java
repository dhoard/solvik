package dev.solvik.compiler.report;

import dev.solvik.compiler.diagnostic.Diagnostic;
import dev.solvik.compiler.diagnostic.SourcePos;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Renders language diagnostics for a person reading a terminal.
 *
 * <p>Each issue is reported as four facts: the file name relative to the current directory, the
 * line and column, the stable diagnostic code with its message, and the source line that provoked
 * it with a caret under the offending column.
 *
 * <pre>
 * example.sol:14:20: error [SOL-T001]: operator '+' needs numeric operands, found Integer and String
 *    14 | var broken : 1 + "text"
 *       |                 ^
 * </pre>
 *
 * <p>This layer exists so that reporting is a presentation concern: earlier phases produce
 * {@link Diagnostic} values with positions, and nothing about the language depends on how they are
 * printed.
 */
public final class DiagnosticReporter {

    private static final String GUTTER = "  ";

    /** Renders every diagnostic against {@code source}, in the order the phases recorded them. */
    public String report(String displayName, String source, List<Diagnostic> diagnostics) {
        StringBuilder out = new StringBuilder();
        String[] lines = source.split("\n", -1);
        for (Diagnostic diagnostic : diagnostics) {
            out.append(diagnostic.format(displayName)).append(System.lineSeparator());
            appendSourceLine(out, lines, diagnostic.pos());
        }
        return out.toString();
    }

    /**
     * Renders every diagnostic against the file it came from.
     *
     * @param sources     each file's text keyed by the display name recorded in its positions
     * @param displayName the name to use for a diagnostic whose position names no file
     */
    public String report(Map<String, String> sources, String displayName,
            List<Diagnostic> diagnostics) {
        StringBuilder out = new StringBuilder();
        for (Diagnostic diagnostic : diagnostics) {
            String file = diagnostic.pos().file() == null ? displayName : diagnostic.pos().file();
            out.append(diagnostic.format(file)).append(System.lineSeparator());
            String source = sources.getOrDefault(file, "");
            appendSourceLine(out, source.split("\n", -1), diagnostic.pos());
        }
        return out.toString();
    }

    /**
     * The name to show for {@code path}: relative to the current directory when it can be expressed
     * that way, otherwise the path as given.
     */
    public static String displayName(Path path) {
        try {
            Path absolute = path.toAbsolutePath().normalize();
            Path current = Path.of("").toAbsolutePath().normalize();
            Path relative = current.relativize(absolute);
            // A file above the current directory is clearer as the path the user typed than as a
            // chain of "../" segments.
            return relative.startsWith("..") ? path.toString() : relative.toString();
        } catch (RuntimeException notRelativizable) {
            return path.toString();
        }
    }

    /** Like {@link #displayName(Path)}, but for a name that may be a path or a bare file name. */
    public static String displayName(String name) {
        try {
            Path path = Path.of(name);
            return Files.exists(path) ? displayName(path) : name;
        } catch (RuntimeException notAPath) {
            return name;
        }
    }

    private void appendSourceLine(StringBuilder out, String[] lines, SourcePos pos) {
        int lineNumber = pos.line();
        if (lineNumber < 1 || lineNumber > lines.length) {
            return; // a position outside the source: the location line is all that can be said
        }
        String text = lines[lineNumber - 1];
        if (text.endsWith("\r")) {
            text = text.substring(0, text.length() - 1);
        }
        String number = Integer.toString(lineNumber);
        String padding = " ".repeat(number.length());
        out.append(GUTTER).append(number).append(" | ").append(text)
                .append(System.lineSeparator());
        int column = Math.max(1, Math.min(pos.column(), text.length() + 1));
        out.append(GUTTER).append(padding).append(" | ")
                .append(" ".repeat(column - 1)).append('^')
                .append(System.lineSeparator());
    }
}
