package dev.solvik.compiler.report;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.diagnostic.Diagnostic;
import dev.solvik.compiler.diagnostic.SourcePos;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Diagnostic reporter")
class DiagnosticReporterTest {

    private final DiagnosticReporter reporter = new DiagnosticReporter();

    @Test
    @DisplayName("reports file, position, code, message, and the offending line with a caret")
    void renders_all_four_facts() {
        String source = "a := 1\nb := 1 + \"text\"\nsolvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((b) .. \"\")\n";
        Diagnostic diagnostic = Diagnostic.error("SOL-T001",
                new SourcePos(20, 2, 15), "operator '+' needs numeric operands");

        String report = reporter.report("example.sol", source, List.of(diagnostic));

        String newline = System.lineSeparator();
        assertThat(report).isEqualTo(
                "example.sol:2:15: error [SOL-T001]: operator '+' needs numeric operands" + newline
                        + "  2 | b := 1 + \"text\"" + newline
                        + "    |               ^" + newline);
    }

    @Test
    void renders_every_diagnostic_in_order() {
        String source = "a := 1\nreturn\n";
        List<Diagnostic> diagnostics = List.of(
                Diagnostic.error("SOL-T005", new SourcePos(10, 2, 1), "return outside a function"),
                Diagnostic.error("SOL-T004", new SourcePos(20, 2, 1), "unreachable"));

        String report = reporter.report("f.sol", source, diagnostics);

        assertThat(report).contains("SOL-T005").contains("SOL-T004");
        assertThat(report.indexOf("SOL-T005")).isLessThan(report.indexOf("SOL-T004"));
    }

    @Test
    void handles_crlf_sources_and_wide_gutter_numbers() {
        String source = ("" + "a := 1\r\n").repeat(12) + "bad line\r\n";
        Diagnostic diagnostic = Diagnostic.error("C1", new SourcePos(100, 13, 4), "problem");

        String report = reporter.report("crlf.sol", source, List.of(diagnostic));

        assertThat(report).contains("13 | bad line\n").doesNotContain("\r");
        assertThat(report).contains("   |    ^");
    }

    @Test
    void tolerates_a_position_outside_the_source() {
        Diagnostic diagnostic = Diagnostic.error("C1", new SourcePos(0, 99, 1), "problem");

        String report = reporter.report("f.sol", "a := 1", List.of(diagnostic));

        assertThat(report).isEqualTo("f.sol:99:1: error [C1]: problem" + System.lineSeparator());
    }

    @Test
    void clamps_a_column_past_the_end_of_the_line() {
        Diagnostic diagnostic = Diagnostic.error("C1", new SourcePos(0, 1, 40), "problem");

        String report = reporter.report("f.sol", "short\n", List.of(diagnostic));

        assertThat(report).contains("  1 | short\n").contains("    |      ^");
    }

    @Test
    @DisplayName("names inside the working directory become relative; others stay as given")
    void computes_display_names() {
        Path current = Path.of("").toAbsolutePath();
        assertThat(DiagnosticReporter.displayName(current.resolve("example.sol")))
                .isEqualTo("example.sol");
        assertThat(DiagnosticReporter.displayName(Path.of("/elsewhere/outside.sol")))
                .isEqualTo("/elsewhere/outside.sol");
    }

    @Test
    void a_name_that_is_not_a_path_is_reported_unchanged() {
        assertThat(DiagnosticReporter.displayName("not a real file.sol"))
                .isEqualTo("not a real file.sol");
    }
}
