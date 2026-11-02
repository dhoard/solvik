package dev.solvik.compiler.diagnostic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Diagnostics")
class DiagnosticsTest {

    @Test
    void collects_errors_and_warnings_and_renders_both() {
        Diagnostics diagnostics = new Diagnostics();
        diagnostics.error("C1", new SourcePos(0, 1, 1), "boom");
        diagnostics.add(Diagnostic.warning("C2", new SourcePos(5, 2, 3), "careful"));

        assertThat(diagnostics.size()).isEqualTo(2);
        assertThat(diagnostics.hasErrors()).isTrue();
        assertThat(diagnostics.all()).hasSize(2);
        assertThat(diagnostics.format("a.sol")).contains(
                "a.sol:1:1: error [C1]: boom",
                "a.sol:2:3: warning [C2]: careful");
    }

    @Test
    void warnings_alone_do_not_fail_and_throw_nothing() {
        Diagnostics diagnostics = new Diagnostics();
        diagnostics.add(Diagnostic.warning("C2", SourcePos.START, "careful"));

        assertThat(diagnostics.hasErrors()).isFalse();
        assertThatCode(diagnostics::throwIfErrors).doesNotThrowAnyException();
        assertThat(diagnostics.format("a.sol")).contains("warning [C2]");
    }

    @Test
    void throw_if_errors_carries_every_diagnostic() {
        Diagnostics diagnostics = new Diagnostics();
        diagnostics.error("C1", SourcePos.START, "first");
        diagnostics.error("C3", new SourcePos(9, 3, 1), "second");

        assertThatThrownBy(diagnostics::throwIfErrors)
                .isInstanceOf(SolvikException.class)
                .hasMessageContaining("compilation failed with 2 error(s)")
                .hasMessageContaining("first")
                .hasMessageContaining("second");
    }

    @Test
    void exception_exposes_its_diagnostics() {
        SolvikException exception = new SolvikException(
                List.of(Diagnostic.error("C1", SourcePos.START, "boom")));

        assertThat(exception.diagnostics()).hasSize(1);
        assertThat(exception.diagnostics().getFirst().code()).isEqualTo("C1");
    }

    @Test
    void remove_last_discards_the_newest_and_tolerates_an_empty_list() {
        Diagnostics diagnostics = new Diagnostics();
        diagnostics.removeLast(); // nothing to remove
        assertThat(diagnostics.size()).isZero();

        diagnostics.error("C1", SourcePos.START, "a");
        diagnostics.error("C2", SourcePos.START, "b");
        diagnostics.removeLast();

        assertThat(diagnostics.all()).singleElement()
                .extracting(Diagnostic::code).isEqualTo("C1");
    }

    @Test
    void severity_distinguishes_errors_from_warnings() {
        assertThat(Diagnostic.error("C1", SourcePos.START, "x").isError()).isTrue();
        assertThat(Diagnostic.warning("C2", SourcePos.START, "x").isError()).isFalse();
        assertThat(Diagnostic.warning("C2", SourcePos.START, "x").severity())
                .isEqualTo(Diagnostic.Severity.WARNING);
    }

    @Test
    void source_positions_render_and_expose_start() {
        assertThat(new SourcePos(7, 4, 12).toString()).isEqualTo("4:12");
        assertThat(SourcePos.START).isEqualTo(new SourcePos(0, 1, 1));
    }
}
