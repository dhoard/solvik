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
 * {@code include} and {@code alias} block syntax (LANGUAGE.md §3, "Source files").
 *
 * <p>Both blocks are mandatory, even for one entry, and both accept a newline or {@code ;} between
 * entries. A single-line block is valid; a single-line declaration without braces is not. The tests
 * here pin the accepted separator mixtures and every rejected spelling, including the removed
 * {@code import} forms.
 */
@DisplayName("include and alias syntax")
class IncludeAliasSyntaxTest {

    private static final String FILE = "test.sol";

    private static Ast.Program parse(String source) {
        Diagnostics diagnostics = new Diagnostics();
        Ast.Program program = new Parser(new Lexer(source, diagnostics, FILE).tokenize(), diagnostics)
                .parseProgram();
        assertThat(diagnostics.all())
                .describedAs("unexpected diagnostics: %s", diagnostics.format(FILE))
                .isEmpty();
        return program;
    }

    private static Diagnostics errors(String source) {
        Diagnostics diagnostics = new Diagnostics();
        new Parser(new Lexer(source, diagnostics, FILE).tokenize(), diagnostics).parseProgram();
        return diagnostics;
    }

    private static List<String> codes(Diagnostics diagnostics) {
        return diagnostics.all().stream().map(Diagnostic::code).toList();
    }

    @Nested
    @DisplayName("lexing")
    class Lexing {

        @Test
        void include_and_alias_are_keywords() {
            Diagnostics diagnostics = new Diagnostics();
            List<Token> tokens = new Lexer("include alias", diagnostics).tokenize();

            assertThat(tokens.get(0).kind()).isEqualTo(TokenKind.INCLUDE);
            assertThat(tokens.get(1).kind()).isEqualTo(TokenKind.ALIAS);
            assertThat(TokenKind.INCLUDE.isKeyword()).isTrue();
            assertThat(TokenKind.ALIAS.isKeyword()).isTrue();
        }

        @Test
        @DisplayName("import is still tokenized so the parser can name both replacements")
        void import_remains_a_token() {
            Diagnostics diagnostics = new Diagnostics();
            List<Token> tokens = new Lexer("import", diagnostics).tokenize();

            assertThat(tokens.get(0).kind()).isEqualTo(TokenKind.IMPORT);
            assertThat(diagnostics.all()).isEmpty();
        }

        @Test
        void a_spelling_that_only_contains_the_keyword_stays_an_identifier() {
            Diagnostics diagnostics = new Diagnostics();
            List<Token> tokens = new Lexer("included aliases", diagnostics).tokenize();

            assertThat(tokens.get(0).kind()).isEqualTo(TokenKind.IDENTIFIER);
            assertThat(tokens.get(1).kind()).isEqualTo(TokenKind.IDENTIFIER);
        }
    }

    @Nested
    @DisplayName("include blocks")
    class Includes {

        @Test
        void a_multiline_block_separates_entries_by_newlines() {
            Ast.Program program = parse("""
                    include {
                        "models.sol"
                        "parsers.sol"
                    }
                    """);

            assertThat(program.includes()).extracting(Ast.IncludeDecl::path)
                    .containsExactly("models.sol", "parsers.sol");
        }

        @Test
        @DisplayName("a one-entry block is valid on one line")
        void a_one_entry_block_is_valid() {
            Ast.Program program = parse("include { \"models.sol\" }\n");

            assertThat(program.includes()).extracting(Ast.IncludeDecl::path)
                    .containsExactly("models.sol");
        }

        @Test
        void a_single_line_block_may_mix_entries_and_semicolons() {
            Ast.Program program = parse("include { \"a.sol\"; \"b.sol\" }\n");

            assertThat(program.includes()).extracting(Ast.IncludeDecl::path)
                    .containsExactly("a.sol", "b.sol");
        }

        @Test
        @DisplayName("mixed separators, repeated separators, comments, and a trailing separator")
        void separators_may_be_mixed_and_repeated() {
            Ast.Program program = parse("""
                    include {
                        "a.sol"
                        "b.sol"; "c.sol"

                        // a comment line and a lone separator are layout
                        ;
                        "d.sol";
                    }
                    """);

            assertThat(program.includes()).extracting(Ast.IncludeDecl::path)
                    .containsExactly("a.sol", "b.sol", "c.sol", "d.sol");
        }

        @Test
        void an_inline_comment_may_follow_an_entry() {
            Ast.Program program = parse("include { \"a.sol\" // first\n \"b.sol\" }\n");

            assertThat(program.includes()).extracting(Ast.IncludeDecl::path)
                    .containsExactly("a.sol", "b.sol");
        }

        @Test
        @DisplayName("each path keeps its own source position")
        void each_path_keeps_its_own_position() {
            Ast.Program program = parse("include {\n    \"a.sol\"\n    \"b.sol\"\n}\n");

            Ast.IncludeDecl first = program.includes().get(0);
            Ast.IncludeDecl second = program.includes().get(1);
            assertThat(first.pos().line()).isEqualTo(2);
            assertThat(first.pos().column()).isEqualTo(5);
            assertThat(first.pos().file()).isEqualTo(FILE);
            assertThat(second.pos().line()).isEqualTo(3);
        }

        @Test
        void multiple_blocks_may_interleave_with_declarations() {
            Ast.Program program = parse("""
                    include { "a.sol" }
                    type Point { pub func Point() { } }
                    include { "b.sol" }
                    p := Point()
                    """);

            assertThat(program.includes()).extracting(Ast.IncludeDecl::path)
                    .containsExactly("a.sol", "b.sol");
            assertThat(program.types()).hasSize(1);
            assertThat(program.statements()).hasSize(1);
        }

        @Test
        void an_adjacent_entry_without_a_separator_is_rejected() {
            Diagnostics diagnostics = errors("include { \"a.sol\" \"b.sol\" }\n");

            assertThat(diagnostics.all()).singleElement().satisfies(diagnostic -> {
                assertThat(diagnostic.code()).isEqualTo(Parser.EXPECTED_TOKEN);
                assertThat(diagnostic.message()).contains("separated by a newline or ';'");
            });
        }

        @Test
        void a_comma_is_rejected_with_the_separator_replacement() {
            Diagnostics diagnostics = errors("include { \"a.sol\", \"b.sol\" }\n");

            assertThat(diagnostics.all()).singleElement().satisfies(diagnostic -> {
                assertThat(diagnostic.code()).isEqualTo(Parser.EXPECTED_TOKEN);
                assertThat(diagnostic.message()).contains("comma");
            });
        }

        @Test
        void an_empty_block_is_rejected() {
            assertThat(codes(errors("include { }\n"))).contains(Parser.DECLARATION_FORM);
            assertThat(codes(errors("include {\n\n}\n"))).contains(Parser.DECLARATION_FORM);
        }

        @Test
        void a_missing_block_is_rejected_with_the_block_form() {
            assertThat(errors("include \"a.sol\"\n").all()).singleElement().satisfies(diagnostic -> {
                assertThat(diagnostic.code()).isEqualTo(Parser.EXPECTED_TOKEN);
                assertThat(diagnostic.message()).contains("include { \"path.sol\" }");
            });
        }

        @Test
        void an_entry_that_is_not_a_string_literal_is_rejected() {
            assertThat(codes(errors("include { models }\n"))).contains(Parser.EXPECTED_TOKEN);
        }

        @Test
        @DisplayName("the removed import spellings name their replacements")
        void both_old_import_spellings_are_rejected() {
            for (String source : List.of("import \"a.sol\"\n", "import { \"a.sol\" }\n")) {
                assertThat(errors(source).all()).singleElement().satisfies(diagnostic -> {
                    assertThat(diagnostic.code()).isEqualTo(Parser.REMOVED_SYNTAX);
                    assertThat(diagnostic.message())
                            .contains("include { \"path.sol\" }")
                            .contains("alias { module::Type Name }");
                });
            }
        }

        @Test
        void an_include_inside_another_body_is_rejected() {
            for (String source : List.of("""
                    module m {
                        include { "a.sol" }
                    }
                    """, """
                    type T {
                        include { "a.sol" }
                    }
                    """, """
                    func f() {
                        include { "a.sol" }
                    }
                    """)) {
                assertThat(errors(source).all()).singleElement().satisfies(diagnostic -> {
                    assertThat(diagnostic.code()).isEqualTo(Parser.INVALID_DECLARATION);
                    assertThat(diagnostic.message()).contains("file-level");
                });
            }
        }
    }

    @Nested
    @DisplayName("alias blocks")
    class Aliases {

        @Test
        void a_multiline_block_reads_the_target_first_and_the_local_name_second() {
            Ast.Program program = parse("""
                    alias {
                        foo::bar::Parser Parser
                        foo::bar::Formatter Fmt
                        foo::bar fb
                    }
                    """);

            assertThat(program.aliases()).extracting(Ast.AliasDecl::target)
                    .containsExactly("foo::bar::Parser", "foo::bar::Formatter", "foo::bar");
            assertThat(program.aliases()).extracting(Ast.AliasDecl::localName)
                    .containsExactly("Parser", "Fmt", "fb");
        }

        @Test
        void a_single_line_block_separates_entries_by_semicolons() {
            Ast.Program program = parse("alias { m::A A; m::B B }\n");

            assertThat(program.aliases()).extracting(Ast.AliasDecl::localName)
                    .containsExactly("A", "B");
        }

        @Test
        @DisplayName("a one-entry block is valid on one line")
        void a_one_entry_block_is_valid() {
            Ast.Program program = parse("alias { foo::bar::Parser Parser }\n");

            assertThat(program.aliases()).singleElement()
                    .satisfies(alias -> assertThat(alias.target()).isEqualTo("foo::bar::Parser"));
        }

        @Test
        void the_target_and_the_local_name_keep_their_own_positions() {
            Ast.Program program = parse("alias {\n    m::A LocalA\n}\n");

            Ast.AliasDecl alias = program.aliases().getFirst();
            assertThat(alias.targetPos().line()).isEqualTo(2);
            assertThat(alias.targetPos().column()).isEqualTo(5);
            assertThat(alias.namePos().line()).isEqualTo(2);
            assertThat(alias.namePos().column()).isEqualTo(10);
            assertThat(alias.pos()).isEqualTo(alias.targetPos());
        }

        @Test
        void alias_blocks_may_follow_code() {
            Ast.Program program = parse("p := 1\nalias { m::A A }\nq := 2\n");

            assertThat(program.aliases()).hasSize(1);
            assertThat(program.statements()).hasSize(2);
        }

        @Test
        void an_adjacent_entry_without_a_separator_is_rejected() {
            Diagnostics diagnostics = errors("alias { util::UUID UUID util::Parser Parser }\n");

            assertThat(codes(diagnostics)).contains(Parser.EXPECTED_TOKEN);
            assertThat(diagnostics.all()).anySatisfy(diagnostic ->
                    assertThat(diagnostic.message()).contains("separated by a newline or ';'"));
        }

        @Test
        void a_comma_is_rejected() {
            Diagnostics diagnostics = errors("alias { util::UUID UUID, util::Parser Parser }\n");

            assertThat(diagnostics.all()).anySatisfy(diagnostic ->
                    assertThat(diagnostic.message()).contains("comma"));
        }

        @Test
        void an_empty_block_is_rejected() {
            assertThat(codes(errors("alias { }\n"))).contains(Parser.DECLARATION_FORM);
        }

        @Test
        void a_missing_block_is_rejected_with_the_block_form() {
            assertThat(errors("alias util::UUID UUID\n").all()).singleElement()
                    .satisfies(diagnostic -> {
                        assertThat(diagnostic.code()).isEqualTo(Parser.EXPECTED_TOKEN);
                        assertThat(diagnostic.message()).contains("alias { module::Type Name }");
                    });
        }

        @Test
        void a_malformed_qualified_target_is_rejected() {
            assertThat(codes(errors("alias { util:: UUID }\n"))).contains(Parser.EXPECTED_TOKEN);
            assertThat(codes(errors("alias { ::UUID UUID }\n"))).contains(Parser.EXPECTED_TOKEN);
        }

        @Test
        void a_missing_local_name_is_rejected() {
            assertThat(codes(errors("alias { util::UUID }\n"))).contains(Parser.EXPECTED_TOKEN);
        }

        @Test
        void an_alias_inside_another_body_is_rejected() {
            for (String source : List.of("""
                    module m {
                        alias { util::UUID UUID }
                    }
                    """, """
                    type T {
                        alias { util::UUID UUID }
                    }
                    """, """
                    func f() {
                        alias { util::UUID UUID }
                    }
                    """)) {
                assertThat(errors(source).all()).singleElement().satisfies(diagnostic -> {
                    assertThat(diagnostic.code()).isEqualTo(Parser.INVALID_DECLARATION);
                    assertThat(diagnostic.message()).contains("file-level");
                });
            }
        }
    }
}
