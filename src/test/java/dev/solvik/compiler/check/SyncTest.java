package dev.solvik.compiler.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.ast.Ast;
import dev.solvik.compiler.diagnostic.Diagnostic;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.lexer.Lexer;
import dev.solvik.compiler.parser.Parser;
import dev.solvik.compiler.testsupport.Checking;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@code sync monitor { ... }} checking ({@code LANGUAGE.md} §7.5).
 *
 * <p>The monitor is a non-null value whose identity the program owns — a user type or an array —
 * and the block adds no rule of its own, so loop control, {@code return}, and {@code raise} inside
 * it are checked exactly as they are in a bare block.
 */
@DisplayName("sync blocks are checked (§7.5)")
class SyncTest {

    private static final String GUARD = """
            type Guard {
                pub func Guard() { }
                pub func hit() { }
            }
            """;

    private static Diagnostics diagnostics(String source) {
        Diagnostics diagnostics = new Diagnostics();
        Ast.Program program = new Parser(new Lexer(source, diagnostics).tokenize(), diagnostics)
                .parseProgram();
        Checking.check(program, diagnostics);
        return diagnostics;
    }

    private static List<String> codes(String source) {
        return diagnostics(source).all().stream().map(Diagnostic::code).toList();
    }

    private static void assertAccepted(String source) {
        Diagnostics result = diagnostics(source);
        assertThat(result.all())
                .describedAs("unexpected diagnostics: %s", result.format("<test>"))
                .isEmpty();
    }

    @Test
    @DisplayName("a user type, an array, self, and a narrowed nullable are valid monitors")
    void valid_monitors_are_accepted() {
        assertAccepted(GUARD + "guard := Guard()\nsync guard { guard.hit() }");
        assertAccepted(GUARD + "guard := Guard()\nsync guard { }");
        assertAccepted("values := Integer[2] {1, 2}\nsync values { values[0] = 3 }");
        assertAccepted(GUARD + "type Holder {\n"
                + "    guard := Guard()\n"
                + "    pub func Holder() { }\n"
                + "    pub func locked() {\n"
                + "        sync self { }\n"
                + "        sync guard { guard.hit() }\n"
                + "    }\n"
                + "}");
        assertAccepted(GUARD + "maybe: Guard? = Null\n"
                + "if maybe != Null {\n"
                + "    sync maybe { maybe.hit() }\n"
                + "}");
    }

    @Test
    @DisplayName("a static initializer and a nested block on one monitor are accepted")
    void static_initializers_and_nesting_are_accepted() {
        assertAccepted(GUARD + "type Boot {\n"
                + "    static pub mut ready := 0\n"
                + "    static {\n"
                + "        guard := Guard()\n"
                + "        sync guard { ready = 1 }\n"
                + "        sync guard { sync guard { ready = 2 } }\n"
                + "    }\n"
                + "}");
    }

    @Test
    @DisplayName("loop control and return inside the block behave as in a bare block")
    void control_flow_inside_the_block_is_unchanged() {
        assertAccepted(GUARD + "guard := Guard()\n"
                + "mut kept := 0\n"
                + "for step in Integer[3] {1, 2, 3} {\n"
                + "    sync guard {\n"
                + "        if step == 2 { continue }\n"
                + "        if step == 3 { break }\n"
                + "        kept = kept + step\n"
                + "    }\n"
                + "}");
        assertAccepted(GUARD + "func first(guard: Guard) -> Integer {\n"
                + "    sync guard {\n"
                + "        return 1\n"
                + "    }\n"
                + "}");
        assertAccepted(GUARD + "func fire(guard: Guard) {\n"
                + "    sync guard { raise Exception(1, \"inside\") }\n"
                + "}");
    }

    @Test
    @DisplayName("loop control still needs a loop, and the block is not one")
    void loop_control_outside_a_loop_is_rejected() {
        assertThat(codes(GUARD + "guard := Guard()\nsync guard { break }"))
                .containsExactly(TypeChecker.LOOP_CONTROL_OUTSIDE_LOOP);
        assertThat(codes(GUARD + "guard := Guard()\nsync guard { continue }"))
                .containsExactly(TypeChecker.LOOP_CONTROL_OUTSIDE_LOOP);
    }

    @Test
    @DisplayName("the block is its own scope")
    void the_block_is_its_own_scope() {
        assertAccepted(GUARD + "guard := Guard()\n"
                + "sync guard { inside := 1 }\n"
                + "sync guard { inside := 2 }");
        assertThat(codes(GUARD + "guard := Guard()\nsync guard { hidden := 1 }\n"
                + "solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((hidden) .. \"\")"))
                .contains(TypeChecker.TYPE_ERROR);
    }

    @ParameterizedTest
    @DisplayName("a monitor whose identity the program does not own is rejected")
    @ValueSource(strings = {
            "sync 1 { }",
            "sync 1.5 { }",
            "sync 1L { }",
            "sync \"text\" { }",
            "sync true { }",
            "sync BigDecimal(1) { }",
            "sync Null { }",
            "anyValue: Any = 1\nsync anyValue { }",
            "number: Number = 1\nsync number { }",
            "failure := Exception(1, \"x\")\nsync failure { }"
    })
    void an_invalid_monitor_is_rejected(String source) {
        assertThat(codes(source)).containsExactly(TypeChecker.MONITOR_TYPE);
    }

    @Test
    @DisplayName("a nullable monitor is the ordinary missing-value failure, reported once")
    void a_nullable_monitor_is_rejected_once() {
        assertThat(codes(GUARD + "mut maybe: Guard? = Null\nsync maybe { }"))
                .containsExactly(TypeChecker.NULL_UNSAFE);
        assertThat(codes("mut text: String? = Null\nsync text { }"))
                .containsExactly(TypeChecker.NULL_UNSAFE);
    }

    @Test
    @DisplayName("an unknown monitor name is a name-resolution failure")
    void an_unknown_monitor_is_reported() {
        assertThat(codes("sync missing { }")).contains(TypeChecker.TYPE_ERROR);
    }

    @Test
    @DisplayName("a block that always leaves makes what follows unreachable")
    void a_block_that_always_leaves_is_a_terminator() {
        String source = GUARD + "func value(guard: Guard) -> Integer {\n"
                + "    sync guard {\n"
                + "        return 1\n"
                + "    }\n"
                + "    return 2\n"
                + "}";
        assertThat(codes(source)).containsExactly(TypeChecker.UNREACHABLE);
        assertThat(diagnostics(source).format("<test>"))
                .contains("a 'sync' statement that always leaves");
    }
}
