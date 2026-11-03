package dev.solvik.compiler.parser;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.ast.Ast;
import dev.solvik.compiler.diagnostic.Diagnostic;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.lexer.Lexer;
import dev.solvik.compiler.lexer.Token;
import dev.solvik.compiler.lexer.TokenKind;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@code sync} statement syntax ({@code LANGUAGE.md} §7.5).
 *
 * <p>The form is a keyword, an unparenthesized monitor expression, and a block, exactly like
 * {@code while}. {@code sync} is a reserved word, so it can no longer name a binding; a member name
 * after {@code .} may still be a keyword, as {@code .type()} already is (§8).
 */
@DisplayName("sync statement syntax")
class SyncSyntaxTest {

    private static Ast.Program parse(String source) {
        Diagnostics diagnostics = new Diagnostics();
        Ast.Program program = new Parser(new Lexer(source, diagnostics).tokenize(), diagnostics)
                .parseProgram();
        assertThat(diagnostics.all())
                .describedAs("unexpected diagnostics: %s", diagnostics.format("<test>"))
                .isEmpty();
        return program;
    }

    private static List<String> codes(String source) {
        Diagnostics diagnostics = new Diagnostics();
        new Parser(new Lexer(source, diagnostics).tokenize(), diagnostics).parseProgram();
        return diagnostics.all().stream().map(Diagnostic::code).toList();
    }

    @Test
    @DisplayName("sync is a keyword token")
    void sync_is_a_keyword() {
        Diagnostics diagnostics = new Diagnostics();
        List<Token> tokens = new Lexer("sync synonymous", diagnostics).tokenize();

        assertThat(tokens.get(0).kind()).isEqualTo(TokenKind.SYNC);
        assertThat(tokens.get(1).kind()).isEqualTo(TokenKind.IDENTIFIER);
        assertThat(TokenKind.SYNC.isKeyword()).isTrue();
        assertThat(diagnostics.all()).isEmpty();
    }

    @Test
    @DisplayName("the monitor is an expression and the body is a block of statements")
    void parses_a_sync_statement() {
        Ast.Stmt statement = parse("""
                sync guard {
                    guard.hit()
                }
                """).statements().getFirst();

        assertThat(statement).isInstanceOf(Ast.SyncStmt.class);
        Ast.SyncStmt sync = (Ast.SyncStmt) statement;
        assertThat(sync.monitor()).isInstanceOf(Ast.NameExpr.class);
        assertThat(((Ast.NameExpr) sync.monitor()).name()).isEqualTo("guard");
        assertThat(sync.body()).hasSize(1);
        assertThat(sync.body().getFirst()).isInstanceOf(Ast.ExprStmt.class);
    }

    @Test
    @DisplayName("the body may hold any statement, including another sync")
    void parses_arbitrary_statements_in_the_body() {
        Ast.SyncStmt sync = (Ast.SyncStmt) parse("""
                sync guard {
                    value := 1
                    if value == 1 {
                        sync guard {
                            guard.hit()
                        }
                    }
                }
                """).statements().getFirst();

        assertThat(sync.body()).hasSize(2);
        assertThat(sync.body().get(1)).isInstanceOf(Ast.IfStmt.class);
        Ast.IfStmt branch = (Ast.IfStmt) sync.body().get(1);
        assertThat(branch.branches().getFirst().body().getFirst()).isInstanceOf(Ast.SyncStmt.class);
    }

    @Test
    @DisplayName("a monitor may be a member read or a call")
    void parses_non_name_monitors() {
        List<Ast.Stmt> statements = parse("""
                sync holder.guard {
                    holder.hit()
                }
                sync pick() {
                    holder.hit()
                }
                """).statements();

        assertThat(((Ast.SyncStmt) statements.get(0)).monitor())
                .isInstanceOf(Ast.MemberExpr.class);
        assertThat(((Ast.SyncStmt) statements.get(1)).monitor())
                .isInstanceOf(Ast.CallExpr.class);
    }

    @Test
    @DisplayName("a keyword member name is still allowed after '.'")
    void a_member_may_still_be_named_sync() {
        Ast.VarStmt declaration = (Ast.VarStmt) parse("""
                mut value := holder.sync()
                """).statements().getFirst();

        Ast.CallExpr call = (Ast.CallExpr) declaration.initializer();
        assertThat(((Ast.MemberExpr) call.callee()).name()).isEqualTo("sync");
    }

    @Nested
    @DisplayName("rejections")
    class Rejections {

        @Test
        @DisplayName("sync cannot name a binding")
        void sync_is_a_reserved_word() {
            assertThat(codes("sync := 1")).contains(Parser.UNEXPECTED_TOKEN);
        }

        @Test
        @DisplayName("a sync needs a block")
        void a_sync_needs_a_block() {
            List<String> codes = codes("""
                    sync guard
                    """);
            assertThat(codes).contains(Parser.EXPECTED_TOKEN);
            assertThat(codes).doesNotContain(Parser.INVALID_STATEMENT);
        }

        @Test
        @DisplayName("a sync needs a monitor expression")
        void a_sync_needs_a_monitor() {
            assertThat(codes("""
                    sync {
                        value := 1
                    }
                    """)).contains(Parser.EXPECTED_TOKEN);
        }
    }
}
