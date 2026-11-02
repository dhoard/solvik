package dev.solvik.compiler.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.ast.Ast;
import dev.solvik.compiler.bind.JavaMathMethods;
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
 * Nullability rules of {@code LANGUAGE.md} §2, §4.5, §5, §7.1 and the acceptance cases of §15.
 *
 * <p>Ordinary types are non-null, {@code T?} admits {@code Null}, and {@code Null} is a singleton
 * type of its own. Member access, indexing, arithmetic, conditions, and switch subjects need a
 * non-null value; printing, equality, and {@code String(value)} accept a nullable one.
 */
@DisplayName("Nullability (§2, §7.1, §15)")
class NullabilityTest {

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

    private static void assertUnsafe(String source) {
        assertThat(diagnostics(source).all()).extracting(d -> d.code())
                .describedAs("expected SOL-T015 for: %s", source)
                .contains(TypeChecker.NULL_UNSAFE);
    }

    @Test
    @DisplayName("§15: printing, equality, and String(value) accept Null")
    void null_safe_operations_accept_null() {
        assertAccepted("""
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Null) .. "")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Null == Null) .. "")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Null != Null) .. "")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Null === Null) .. "")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((String(Null)) .. "")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("value=" .. Null) .. "")
                """);
        assertAccepted("""
                text: String? = Null
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((text) .. (" ") .. (text == Null) .. (" ") .. (text != "x") .. (" ") .. (String(text)))
                """);
    }

    @Test
    @DisplayName("Null inhabits only a nullable type, and never an ordinary one")
    void null_is_assignable_only_to_a_nullable_type() {
        assertAccepted("""
                a: Integer? = Null
                b: Any? = Null
                c: String[2]? = Null
                d := Null
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((a) .. (b) .. (c) .. (d))
                """);
        assertThat(diagnostics("a: Integer = Null").all()).extracting(d -> d.code())
                .contains(TypeChecker.TYPE_ERROR);
        assertThat(diagnostics("a: Any = Null").all()).extracting(d -> d.code())
                .contains(TypeChecker.TYPE_ERROR);
        assertThat(diagnostics("a: String = Null").all()).extracting(d -> d.code())
                .contains(TypeChecker.TYPE_ERROR);
    }

    @Test
    @DisplayName("Null is a return type and a written type, distinct from no return value")
    void null_is_a_written_type() {
        assertAccepted("""
                func onlyNull() -> Null {
                    return Null
                }
                held: Null = Null
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((onlyNull()) .. (" ") .. (held) .. (" ") .. (onlyNull() == Null))
                """);
        assertThat(diagnostics("""
                func nothing() -> Null {
                    return
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((nothing()) .. "")
                """).all()).extracting(d -> d.code()).contains(TypeChecker.TYPE_ERROR);
        assertThat(diagnostics("""
                func noValue() {
                    return
                }
                value: Null = noValue()
                """).all()).extracting(d -> d.code()).contains(TypeChecker.TYPE_ERROR);
    }

    @Test
    void null_is_not_an_exception_type_and_nullable_is_not_a_union() {
        assertThat(diagnostics("x: Null? = Null").all()).extracting(d -> d.code())
                .contains(TypeChecker.TYPE_ERROR);
        assertThat(diagnostics("x: String?? = Null").all()).extracting(d -> d.code())
                .contains(Parser.TYPE_FORM);
    }

    @Test
    @DisplayName("a nullable value must be proven present before it is dereferenced")
    void nullable_values_are_not_usable_as_values() {
        assertUnsafe("v: String? = Null\nsolvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((v.size) .. \"\")");
        assertUnsafe("v: Integer? = Null\nsolvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((v + 1) .. \"\")");
        assertUnsafe("v: Integer? = Null\nsolvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((-v) .. \"\")");
        assertUnsafe("v: Boolean? = Null\nif v { }");
        assertUnsafe("v: Boolean? = Null\nsolvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((v && true) .. \"\")");
        assertUnsafe("v: Integer[2]? = Null\nsolvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((v[0]) .. \"\")");
        assertUnsafe("v: Integer[2]? = Null\nsolvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((v.size) .. \"\")");
        assertUnsafe("v: Integer[2]? = Null\nfor n in v { solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((n) .. \"\") }");
        assertUnsafe("v: Integer? = Null\nswitch v { case 1 { } }");
        assertUnsafe("v: String? = Null\nsolvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((v.equals(\"x\")) .. \"\")");
        assertUnsafe("mut v: String? = Null\nv ..= \"x\"");
    }

    @Test
    @DisplayName("narrowing proves presence inside the branch that checked it")
    void a_null_check_narrows_a_binding() {
        assertAccepted("""
                text: String? = Null
                if text != Null {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((text.size()) .. (" ") .. (text.substring(0, 1)))
                }
                """);
        assertAccepted("""
                text: String? = Null
                if text == Null {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("absent") .. "")
                } else {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((text.size()) .. "")
                }
                """);
        assertAccepted("""
                func size(text: String?) -> Integer {
                    if text == Null {
                        return 0
                    }
                    return text.size()
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((size(Null)) .. (" ") .. (size("four")))
                """);
        assertAccepted("""
                a: String? = Null
                b: String? = "x"
                if a != Null && b != Null {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((a.size() + b.size()) .. "")
                }
                """);
        assertAccepted("""
                mut n: Integer? = Null
                while n != Null && n > 0 {
                    n = n - 1
                    if n == 0 {
                        n = Null
                    }
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((n) .. "")
                """);
        assertAccepted("""
                text: String? = Null
                if !(text == Null) {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((text.size()) .. "")
                }
                """);
    }

    @Test
    @DisplayName("a write may restore Null, so a proven fact does not survive it")
    void a_write_drops_the_proven_fact() {
        assertUnsafe("""
                mut text: String? = Null
                if text != Null {
                    text = Null
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((text.size()) .. "")
                }
                """);
        assertAccepted("""
                mut text: String? = Null
                if text != Null {
                    text = "again"
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((text.size()) .. "")
                }
                """);
    }

    @Test
    @DisplayName("a value assigned or initialized non-null is present until written again")
    void a_non_null_value_proves_presence() {
        assertAccepted("""
                mut text: String? = "ready"
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((text.size()) .. "")
                text = "again"
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((text.size()) .. "")
                """);
        assertAccepted("""
                mut count: Integer? = 1
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((count + 1) .. "")
                """);
        assertUnsafe("""
                mut text: String? = "ready"
                text = Null
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((text.size()) .. "")
                """);
    }

    @Test
    @DisplayName("a null check does not reach a value it could not prove: a field or an element")
    void narrowing_applies_to_bindings_only() {
        assertUnsafe("""
                type Box {
                    pub value: String? = Null

                    pub func Box() { }
                }
                box := Box()
                if box.value != Null {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((box.value.size()) .. "")
                }
                """);
        assertUnsafe("""
                values: String?[2] = String?[2] {Null, Null}
                if values[0] != Null {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((values[0].size()) .. "")
                }
                """);
    }

    @Test
    @DisplayName("a nullable value may be passed only to a nullable parameter")
    void nullable_parameters_accept_null() {
        assertAccepted("""
                func describe(value: String?) -> String {
                    if value == Null {
                        return "absent"
                    }
                    return value
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((describe(Null)) .. (" ") .. (describe("x")))
                """);
        assertThat(diagnostics("""
                func describe(value: String) -> String { return value }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((describe(Null)) .. "")
                """).all()).extracting(d -> d.code()).contains(TypeChecker.TYPE_ERROR);
        assertThat(diagnostics("""
                func describe(value: String?) -> String {
                    if value == Null { return "absent" }
                    return value
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((describe(Null)) .. "")
                """).all()).isEmpty();
    }

    @Test
    @DisplayName("Null and a nullable value become Any only through Any?")
    void any_and_any_nullable_are_distinct() {
        assertAccepted("""
                mut value: Any? = Null
                value = 1
                value = "text"
                value = 1.5
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((value) .. "")
                """);
        assertThat(diagnostics("""
                value: Any = Null
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((value) .. "")
                """).all()).extracting(d -> d.code()).contains(TypeChecker.TYPE_ERROR);
        assertThat(diagnostics("""
                mut number: Number? = Null
                number = 1
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((number) .. "")
                """).all()).isEmpty();
    }

    @Test
    @DisplayName("overload applicability checks nullability (§8)")
    void overloads_respect_nullability() {
        // `Null` matches a nullable parameter and never an ordinary one.
        assertThat(diagnostics("""
                type Left { pub func Left() { } }
                func choose(value: Left) -> Integer { return 1 }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((choose(Null)) .. "")
                """).all()).extracting(d -> d.code()).contains(TypeChecker.TYPE_ERROR);
        // Two unrelated nullable overloads are equally good, so the call is ambiguous.
        assertThat(diagnostics("""
                type Left { pub func Left() { } }
                type Right { pub func Right() { } }
                func choose(value: Left?) -> Integer { return 1 }
                func choose(value: Right?) -> Integer { return 2 }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((choose(Null)) .. "")
                """).all()).extracting(d -> d.code()).contains(JavaMathMethods.AMBIGUOUS);
        // A non-nullable and a nullable overload erase to one Java signature.
        assertThat(diagnostics("""
                func choose(value: Integer) -> Integer { return value }
                func choose(value: Integer?) -> Integer { return 0 }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((choose(1)) .. "")
                """).all()).extracting(d -> d.code()).contains(TypeChecker.TYPE_ERROR);
    }

    @Test
    @DisplayName("the array and its elements carry nullability independently (§5)")
    void array_and_element_nullability_are_independent() {
        assertAccepted("""
                plain := Integer[2] {1, 2}
                nullableElements := Integer?[2] {1, Null}
                nullableArray: Integer[2]? = Integer[2] {1, 2}
                both: Integer?[2]? = Integer?[2] {Null, 2}
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((plain) .. (nullableElements) .. (nullableArray) .. (both))
                """);
        assertAccepted("""
                elements: String?[2] = String?[2] {Null, Null}
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((elements[0] == Null) .. (" ") .. (elements[1] == Null))
                """);
        assertAccepted("""
                array: String[2]? = String[2] {"a", "b"}
                if array != Null {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((array[0].size()) .. "")
                }
                """);
        assertThat(diagnostics("""
                array: String[2] = Null
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((array) .. "")
                """).all()).extracting(d -> d.code()).contains(TypeChecker.TYPE_ERROR);
        assertUnsafe("""
                array: String[2]? = Null
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((array[0]) .. "")
                """);
    }

    @Test
    @DisplayName("a nullable element keeps its type, so indexing yields T?")
    void nullable_elements_yield_nullable_values() {
        assertUnsafe("""
                values: Integer?[2] = Integer?[2] {1, 2}
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((values[0] + 1) .. "")
                """);
        assertAccepted("""
                values: Integer?[2] = Integer?[2] {1, 2}
                first := values[0]
                if first != Null {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((first + 1) .. "")
                }
                """);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "x: Integer = Null",
            "x: Boolean = Null",
            "x: String = Null",
            "x: BigInteger = Null",
            "x: Point = Null",
    })
    @DisplayName("§15: Null cannot inhabit an ordinary type")
    void null_is_rejected_by_every_ordinary_type(String declaration) {
        assertThat(diagnostics("""
                type Point { pub func Point() { } }
                """ + declaration).all()).extracting(d -> d.code())
                .contains(TypeChecker.TYPE_ERROR);
    }

    @Test
    @DisplayName("equality is null-safe for every operand shape")
    void equality_is_null_safe() {
        assertAccepted("""
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Null == Null) .. (" ") .. (Null == 1) .. (" ") .. (Null == "text") .. (" ") .. (Null == true))
                a: Integer? = Null
                b: Integer? = 1
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((a == b) .. (" ") .. (a == Null) .. (" ") .. (b == 1))
                c: String[2]? = Null
                d := String[2] {"a", "b"}
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((c == d) .. (" ") .. (d == Null) .. (" ") .. (d === d))
                """);
    }

    @Test
    @DisplayName("the static type of a nullable binding never changes")
    void mutation_changes_the_value_not_the_type() {
        assertThat(diagnostics("""
                mut v: Integer? = 1
                v = "text"
                """).all()).extracting(d -> d.code()).contains(TypeChecker.TYPE_ERROR);
        assertThat(diagnostics("""
                mut v? := 1
                v = 1.5
                """).all()).extracting(d -> d.code()).contains(TypeChecker.TYPE_ERROR);
        assertAccepted("""
                mut v: Any? = Null
                v = 1
                v = "text"
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((v) .. "")
                """);
    }

    @Test
    @DisplayName("a Null-typed binding is immutable in practice: it can hold only Null")
    void a_null_typed_binding_holds_only_null() {
        assertAccepted("nothing := Null\nsolvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((nothing) .. (\" \") .. (nothing == Null))");
        assertThat(diagnostics("mut nothing := Null\nnothing = 1").all())
                .extracting(d -> d.code()).contains(TypeChecker.TYPE_ERROR);
        assertThat(diagnostics("nothing := Null\nsolvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((nothing + 1) .. \"\")").all())
                .extracting(d -> d.code()).contains(TypeChecker.TYPE_ERROR);
    }

    @Test
    @DisplayName("a nullable value works as a return value only for a nullable return type")
    void return_types_check_nullability() {
        assertAccepted("""
                func missing() -> String? {
                    return Null
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((missing()) .. "")
                """);
        assertThat(diagnostics("""
                func missing() -> String {
                    return Null
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((missing()) .. "")
                """).all()).extracting(d -> d.code()).contains(TypeChecker.TYPE_ERROR);
        assertThat(diagnostics("""
                func maybe(value: Integer?) -> Integer {
                    return value
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((maybe(1)) .. "")
                """).all()).extracting(d -> d.code()).contains(TypeChecker.TYPE_ERROR);
    }

    @Test
    @DisplayName("a nullable value is not implicitly unboxed by a conversion")
    void conversions_do_not_assert_non_null() {
        assertThat(diagnostics("""
                v: Integer? = Null
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Integer(v)) .. "")
                """).all()).extracting(d -> d.code()).contains(TypeChecker.TYPE_ERROR);
        assertAccepted("""
                v: Integer? = Null
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((String(v)) .. "")
                """);
    }

    @Test
    @DisplayName("a nullable receiver and a nullable argument both reject Null at the boundary")
    void equals_on_a_nullable_receiver_needs_a_check() {
        assertUnsafe("""
                type Point { pub func Point() { } }
                p: Point? = Null
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((p.equals(Null)) .. "")
                """);
        assertAccepted("""
                type Point { pub func Point() { } }
                p: Point? = Null
                if p != Null {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((p.equals(Null)) .. (" ") .. (p.equals(p)))
                }
                """);
    }

    @Test
    @DisplayName("the type of an expression records the nullable type, not a widened one")
    void expression_types_keep_nullability() {
        Diagnostics diagnostics = new Diagnostics();
        Ast.Program program = new Parser(new Lexer("""
                x: Integer? = Null
                y := x
                """, diagnostics).tokenize(), diagnostics).parseProgram();
        TypeChecker.Result result = Checking.check(program, diagnostics);

        assertThat(diagnostics.all()).isEmpty();
        Ast.VarStmt declaration = (Ast.VarStmt) program.statements().get(1);
        assertThat(result.typeOf(declaration)).isEqualTo(SolvikType.INTEGER.asNullable());
        assertThat(result.typeOf(declaration.initializer()))
                .isEqualTo(SolvikType.INTEGER.asNullable());
    }

    @Test
    @DisplayName("array element nullability is invariant, so a non-null element stays non-null")
    void array_element_nullability_is_invariant() {
        // Assigning T[N] to T?[N] would alias one array under two element types, and a write
        // through the nullable one would put Null into an array declared to hold values (§2.2).
        assertThat(diagnostics("""
                mut plain: String[2] = String[2] {"x", "y"}
                mut nullable: String?[2] = plain
                nullable[0] = Null
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((plain[0]) .. "")
                """).all()).extracting(d -> d.code()).contains(TypeChecker.TYPE_ERROR);
        assertThat(diagnostics("""
                inner := String[2] {"a", "b"}
                outer := String?[2][2] {inner, inner}
                """).all()).extracting(d -> d.code()).contains(TypeChecker.TYPE_ERROR);
        // The array itself may still widen: a nullable array cannot be indexed before a check.
        assertAccepted("""
                values := String[2] {"a", "b"}
                maybe: String[2]? = values
                if maybe != Null {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((maybe[0].size()) .. "")
                }
                """);
        // An element may still be a non-null value where the element type is nullable: the
        // literal owns its slots, and each slot's type is the declared element type.
        assertAccepted("""
                values: String?[2] = String?[2] {"a", "b"}
                values[0] = "c"
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((values[0]) .. "")
                """);
    }

    @Test
    @DisplayName("a nullable field is read as its declared nullable type")
    void nullable_fields_are_read_as_nullable() {
        assertUnsafe("""
                type Box {
                    pub value: String? = Null

                    pub func Box() { }
                }
                box := Box()
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((box.value.size()) .. "")
                """);
        assertAccepted("""
                type Box {
                    pub value: String? = Null

                    pub func Box() { }

                    pub func textSize() -> Integer {
                        mut mine: String? = self.value
                        if mine == Null {
                            return 0
                        }
                        return mine.size()
                    }
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Box().textSize()) .. "")
                """);
    }
}
