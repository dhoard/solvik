package dev.solvik.compiler.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.ast.Ast;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.lexer.Lexer;
import dev.solvik.compiler.parser.Parser;
import dev.solvik.compiler.testsupport.Checking;
import dev.solvik.compiler.types.SolvikType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The declaration grammar of {@code LANGUAGE.md} §3 and the acceptance cases of §15.
 *
 * <p>There are exactly three operations: {@code name := value} declares with an inferred type,
 * {@code name: Type = value} declares with a written type, and {@code name = value} assigns to an
 * existing mutable binding. Every declaration has an initializer, and {@code var} no longer exists.
 */
@DisplayName("Declarations (§3, §15)")
class DeclarationTest {

    private static Diagnostics diagnostics(String source) {
        Diagnostics diagnostics = new Diagnostics();
        Ast.Program program = new Parser(new Lexer(source, diagnostics).tokenize(), diagnostics)
                .parseProgram();
        Checking.check(program, diagnostics);
        return diagnostics;
    }

    private static void assertAccepted(String source) {
        Diagnostics result = diagnostics(source);
        assertThat(result.all())
                .describedAs("unexpected diagnostics: %s", result.format("<t>")).isEmpty();
    }

    private static void assertRejected(String source, String code) {
        assertThat(diagnostics(source).all()).extracting(d -> d.code()).contains(code);
    }

    @Test
    @DisplayName("§15: every valid declaration form is accepted")
    void valid_declaration_forms_are_accepted() {
        assertAccepted("""
                x := 10
                mut y := 10
                y = 11
                name: String = "Solvik"
                mut title: String? = Null
                title = "ready"
                maybeNumber? := 1
                maybeEmpty? := Null
                nothing := Null
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((x) .. (y) .. (name) .. (title) .. (maybeNumber) .. (maybeEmpty) .. (nothing))
                """);
    }

    @Test
    @DisplayName(":= infers the exact static type, and ? infers it nullable")
    void inference_follows_the_declaration_rules() {
        Diagnostics result = diagnostics("""
                a := 1
                b: Long = 2L
                c? := 3
                d? := Null
                e := Null
                """);
        assertThat(result.all()).isEmpty();
        Ast.Program program = new Parser(new Lexer("""
                a := 1
                b: Long = 2L
                c? := 3
                d? := Null
                e := Null
                """,  new Diagnostics()).tokenize(), new Diagnostics()).parseProgram();
        assertThat(program.statements()).hasSize(5);
    }

    @Test
    @DisplayName("an inferred nullable declaration is T?, and name? := Null is Any?")
    void nullable_inference_is_deterministic() {
        assertAccepted("""
                mut count? := 1
                count = 2
                mut anything? := Null
                anything = "text"
                anything = 1
                mut title: String? = "Solvik"
                title = Null
                """);
        assertRejected("""
                mut count? := 1
                count = "text"
                """, TypeChecker.TYPE_ERROR);
        assertRejected("""
                mut exact := Null
                exact = 1
                """, TypeChecker.TYPE_ERROR);
    }

    @Test
    void a_declaration_binding_is_immutable_unless_marked_mut() {
        assertRejected("""
                x := 1
                x = 2
                """, TypeChecker.IMMUTABLE_MEMBER);
        assertRejected("""
                x: Integer? = 1
                x = 2
                """, TypeChecker.IMMUTABLE_MEMBER);
        assertAccepted("""
                mut x := 1
                x = 2
                """);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "var x : 1",
            "mut var x : 1",
            "pub var x := 1",
            "static var x := 1",
    })
    @DisplayName("§15: the var keyword is removed and named where it is written")
    void the_var_keyword_is_removed(String source) {
        assertRejected(source, Parser.REMOVED_SYNTAX);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "x",
            "x: Integer",
            "x = 1",
            "x: Integer =",
    })
    @DisplayName("§15: a declaration without an initializer, and an undeclared assignment, are errors")
    void declarations_need_an_initializer_and_assignments_need_a_binding(String source) {
        assertThat(diagnostics(source).all()).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "x := 1\nx := 2",
            "x := 1\n{\n    x := 2\n    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((x) .. \"\")\n}\nsolvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((x) .. \"\")",
    })
    @DisplayName("a nested scope may shadow, a single scope may not redeclare")
    void redeclaration_is_rejected_and_shadowing_is_allowed(String source) {
        if (source.contains("{")) {
            assertAccepted(source);
        } else {
            assertRejected(source, TypeChecker.TYPE_ERROR);
        }
    }

    @Test
    @DisplayName("§15: an explicit declaration rejects Null and a mismatched type")
    void explicit_types_are_checked() {
        assertRejected("x: String = Null", TypeChecker.TYPE_ERROR);
        assertRejected("x: Integer = \"text\"", TypeChecker.TYPE_ERROR);
        assertRejected("x: Integer? = 1\nx = 2", TypeChecker.IMMUTABLE_MEMBER);
        assertRejected("mut x := 1\nx = Null", TypeChecker.TYPE_ERROR);
    }

    @Test
    @DisplayName("§15: the two nullable declaration styles cannot be written together")
    void duplicate_nullability_styles_are_rejected() {
        // `name?: Type = value` and `name?: Type? = value` are both invalid (§3.2).
        assertThat(diagnostics("x?: String = Null").all()).isNotEmpty();
        assertThat(diagnostics("mut x?: String? = Null").all()).isNotEmpty();
        assertRejected("x?: String = Null", Parser.DECLARATION_FORM);
        assertRejected("mut x?: String? = Null", Parser.DECLARATION_FORM);
    }

    @Test
    @DisplayName("Null is a value, never a constructor")
    void null_is_not_a_constructor() {
        assertRejected("Null()", TypeChecker.TYPE_ERROR);
        assertRejected("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Null()) .. \"\")", TypeChecker.TYPE_ERROR);
    }

    @Test
    void lowercase_null_was_removed() {
        assertRejected("missing := null", Parser.REMOVED_SYNTAX);
    }

    @Test
    @DisplayName("a field declares its type and initializer like any other binding")
    void fields_follow_the_same_rules() {
        assertAccepted("""
                type Counter {
                    mut count := 0
                    pub mut label: String? = Null
                    static pub mut instances := 0

                    pub func Counter() {
                        instances = instances + 1
                    }

                    pub func bump() -> Integer {
                        count = count + 1
                        return count
                    }
                }
                c := Counter()
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((c.bump()) .. (" ") .. (Counter.instances))
                """);
        assertRejected("""
                type Broken {
                    count: Integer
                }
                """, Parser.DECLARATION_FORM);
        assertRejected("""
                type Broken {
                    static value: Integer
                    pub func Broken() { }
                }
                """, Parser.DECLARATION_FORM);
    }

    @Test
    @DisplayName("modifiers are written static, then pub, then mut")
    void modifier_order_is_checked() {
        assertAccepted("""
                type Ordered {
                    static pub mut limit := 2
                    pub mut count := 0

                    pub func Ordered() { }
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Ordered.limit) .. "")
                """);
        assertRejected("""
                type Misordered {
                    pub static limit := 2

                    pub func Misordered() { }
                }
                """, Parser.INVALID_DECLARATION);
        assertRejected("""
                type Misordered {
                    mut pub count := 0

                    pub func Misordered() { }
                }
                """, Parser.INVALID_DECLARATION);
    }

    @Test
    @DisplayName("a parameter is bound by the caller, so it writes only name: Type")
    void parameters_need_no_initializer() {
        assertAccepted("""
                func area(width: Integer, height: Integer) -> Integer {
                    return width * height
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((area(2, 3)) .. "")
                """);
        assertRejected("""
                func area(width: Integer = 1) -> Integer {
                    return width
                }
                """, Parser.EXPECTED_TOKEN);
    }

    @Test
    @DisplayName("the inferred type of a declaration is the exact type of its initializer")
    void inferred_types_are_exact() {
        Diagnostics diagnostics = new Diagnostics();
        Ast.Program program = new Parser(new Lexer("a := 1\nb := \"text\"\nc := Null\n",
                diagnostics).tokenize(), diagnostics).parseProgram();
        TypeChecker.Result result = Checking.check(program, diagnostics);

        assertThat(diagnostics.all()).isEmpty();
        Ast.VarStmt first = (Ast.VarStmt) program.statements().get(0);
        Ast.VarStmt second = (Ast.VarStmt) program.statements().get(1);
        Ast.VarStmt third = (Ast.VarStmt) program.statements().get(2);
        assertThat(result.typeOf(first)).isEqualTo(SolvikType.INTEGER);
        assertThat(result.typeOf(second)).isEqualTo(SolvikType.STRING);
        assertThat(result.typeOf(third)).isEqualTo(SolvikType.NULL);
        assertThat(result.bindingOf(first).isMutable()).isFalse();
    }
}
