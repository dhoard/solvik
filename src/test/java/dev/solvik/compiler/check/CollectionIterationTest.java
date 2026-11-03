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

/**
 * {@code for ... in} over a declared iteration protocol (LANGUAGE.md §7, §11.3).
 *
 * <p>An array is iterable by its own type. Any other operand participates only when its type
 * declares {@code pub func iterator() -> solvik::collection::Iterator<X>}: the loop then binds its
 * name to {@code X}, the type argument of the declared return, and nothing else — no {@code Map}, no
 * built-in scalar, no {@code Any}, and no member inherited from an implemented trait.
 */
@DisplayName("for ... in over a declared iteration protocol")
class CollectionIterationTest {

    private record Checked(Ast.Program program, Diagnostics diagnostics, TypeChecker.Result result) { }

    private static Checked check(String source) {
        Diagnostics diagnostics = new Diagnostics();
        Ast.Program program = new Parser(new Lexer(source, diagnostics).tokenize(), diagnostics)
                .parseProgram();
        assertThat(diagnostics.all()).describedAs("parse errors: %s",
                diagnostics.format("test.sol")).isEmpty();
        return new Checked(program, diagnostics, Checking.check(program, diagnostics));
    }

    private static Checked assertAccepted(String source) {
        Checked checked = check(source);
        assertThat(checked.diagnostics().all())
                .describedAs("unexpected diagnostics: %s", checked.diagnostics().format("test.sol"))
                .isEmpty();
        return checked;
    }

    private static void assertRejected(String source, String code) {
        Checked checked = check(source);
        assertThat(checked.diagnostics().all()).extracting(diagnostic -> diagnostic.code())
                .contains(code);
    }

    /** The type the checker bound to the loop name of the program's first top-level loop. */
    private static SolvikType loopType(Checked checked) {
        Ast.ForStmt loop = checked.program().statements().stream()
                .filter(Ast.ForStmt.class::isInstance)
                .map(Ast.ForStmt.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no top-level for statement"));
        return checked.result().bindingOf(loop).type();
    }

    @Test
    @DisplayName("a List's loop name is its element type, not the element type plus Null")
    void list_elements_are_the_declared_element_type() {
        Checked checked = assertAccepted("""
                items := solvik::collection::List<Integer>()
                items.add(1)
                mut total := 0
                for value in items {
                    total = total + value
                }
                """);

        assertThat(loopType(checked)).isEqualTo(SolvikType.INTEGER);
    }

    @Test
    void stack_elements_are_the_declared_element_type() {
        Checked checked = assertAccepted("""
                words := solvik::collection::Stack<String>()
                words.push("a")
                mut joined := ""
                for word in words {
                    joined = joined .. word
                }
                """);

        assertThat(loopType(checked)).isEqualTo(SolvikType.STRING);
    }

    @Test
    @DisplayName("a nullable element type stays nullable, so using it needs a narrowing check")
    void nullable_elements_stay_nullable() {
        Checked checked = assertAccepted("""
                items := solvik::collection::List<String?>()
                items.add("ada")
                items.add(Null)
                mut total := 0
                for name in items {
                    if name != Null {
                        total = total + name.size()
                    }
                }
                """);

        assertThat(loopType(checked)).isEqualTo(SolvikType.STRING.asNullable());
        assertRejected("""
                items := solvik::collection::List<String?>()
                items.add(Null)
                for name in items {
                    total := name.size()
                }
                """, TypeChecker.NULL_UNSAFE);
    }

    @Test
    @DisplayName("a user type participates by declaring the protocol itself")
    void a_user_type_participates_by_declaring_iterator() {
        Checked checked = assertAccepted("""
                type Roster {
                    pub names := solvik::collection::List<String>()

                    pub func Roster() { }

                    pub func iterator() -> solvik::collection::Iterator<String> {
                        return self.names.iterator()
                    }
                }
                roster := Roster()
                roster.names.add("ada")
                mut letters := 0
                for name in roster {
                    letters = letters + name.size()
                }
                """);

        assertThat(loopType(checked)).isEqualTo(SolvikType.STRING);
    }

    @Test
    @DisplayName("an array stays iterable by its own type")
    void an_array_stays_iterable() {
        Checked checked = assertAccepted("""
                mut total := 0
                for value in Integer[2] {1, 2} {
                    total = total + value
                }
                """);

        assertThat(loopType(checked)).isEqualTo(SolvikType.INTEGER);
    }

    @Test
    @DisplayName("the error names arrays and the protocol")
    void a_non_iterable_value_is_rejected_with_the_protocol_message() {
        Checked checked = check("""
                values := solvik::collection::Map<String, Integer>()
                for value in values {
                    value := 0
                }
                """);

        assertThat(checked.diagnostics().all()).hasSize(1);
        assertThat(checked.diagnostics().all().getFirst().code()).isEqualTo(TypeChecker.TYPE_ERROR);
        assertThat(checked.diagnostics().all().getFirst().message())
                .contains("an array", "iterator()", "solvik::collection::Iterator");
    }

    @Test
    void built_in_and_dynamic_values_are_not_iterable() {
        assertRejected("""
                value := 1
                for item in value { }
                """, TypeChecker.TYPE_ERROR);
        assertRejected("""
                mut value: Any = 1
                for item in value { }
                """, TypeChecker.TYPE_ERROR);
    }

    @Test
    void a_nullable_collection_needs_a_narrowing_check_first() {
        assertRejected("""
                mut items: solvik::collection::List<Integer>? = Null
                for value in items { }
                """, TypeChecker.NULL_UNSAFE);
    }

    @Test
    @DisplayName("only a public instance iterator() returning the packaged trait is the protocol")
    void a_different_iterator_member_is_not_the_protocol() {
        assertRejected("""
                type Wrong {
                    pub func Wrong() { }

                    pub func iterator() -> Integer {
                        return 1
                    }
                }
                wrong := Wrong()
                for value in wrong { }
                """, TypeChecker.TYPE_ERROR);

        assertRejected("""
                type StaticOwner {
                    pub func StaticOwner() { }

                    static pub func iterator() -> solvik::collection::Iterator<Integer> {
                        raise Exception(1, "not iterable")
                    }
                }
                wrong := StaticOwner()
                for value in wrong { }
                """, TypeChecker.TYPE_ERROR);

        assertRejected("""
                type Hidden {
                    pub func Hidden() { }

                    func iterator() -> solvik::collection::Iterator<Integer> {
                        raise Exception(1, "not iterable")
                    }
                }
                wrong := Hidden()
                for value in wrong { }
                """, TypeChecker.TYPE_ERROR);

        assertRejected("""
                type Parameterized {
                    pub func Parameterized() { }

                    pub func iterator(offset: Integer) -> solvik::collection::Iterator<Integer> {
                        items := solvik::collection::List<Integer>()
                        return items.iterator()
                    }
                }
                wrong := Parameterized()
                for value in wrong { }
                """, TypeChecker.TYPE_ERROR);
    }
}
