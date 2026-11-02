package dev.solvik.compiler.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.ast.Ast;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.lexer.Lexer;
import dev.solvik.compiler.parser.Parser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import dev.solvik.compiler.testsupport.Checking;

/**
 * The built-in names are part of the language and cannot be redeclared ({@code LANGUAGE.md} §2.3,
 * §4.4, §4.6, §7.3, §10).
 *
 * <p>A declaration with a built-in type's name could never be referred to, because a written type
 * of that name always means the built-in one, and it would also hide the type's generated Java
 * carrier. A top-level function with a built-in callable's name can never be called either: the
 * call resolves to the built-in. Both are conflicts in the checked namespace, so they are reported
 * where the declaration is written.
 */
@DisplayName("built-in names are reserved (§2.3, §4.4, §4.6, §7.3, §10)")
class BuiltInNameTest {

    private static Diagnostics diagnostics(String source) {
        Diagnostics diagnostics = new Diagnostics();
        Ast.Program program = new Parser(new Lexer(source, diagnostics).tokenize(), diagnostics)
                .parseProgram();
        Checking.check(program, diagnostics);
        return diagnostics;
    }

    private static void assertRejected(String source) {
        assertThat(diagnostics(source).all()).extracting(d -> d.code())
                .describedAs("expected a type error for: %s", source)
                .contains(TypeChecker.TYPE_ERROR);
    }

    private static void assertAccepted(String source) {
        Diagnostics result = diagnostics(source);
        assertThat(result.all())
                .describedAs("unexpected diagnostics: %s", result.format("<t>"))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Byte", "Short", "Integer", "Long", "Float", "Double", "BigInteger",
            "BigDecimal", "String", "Boolean", "Any", "Number"})
    @DisplayName("a type cannot take a built-in type's name")
    void a_type_cannot_take_a_built_in_name(String name) {
        assertRejected("type " + name + " {\n    pub func " + name + "() { }\n}\n");
        assertRejected("module m {\n    pub type " + name + " {\n        pub func " + name
                + "() { }\n    }\n}\n");
    }

    @ParameterizedTest
    @ValueSource(strings = {"String", "Integer", "Boolean", "Long", "BigDecimal", "exit"})
    @DisplayName("a top-level function cannot take a built-in callable's name")
    void a_top_level_function_cannot_take_a_built_in_name(String name) {
        assertRejected("func " + name + "() {\n}\n");
    }

    @Test
    @DisplayName("a module function and a type member may use those names: both are qualified")
    void qualified_declarations_keep_their_names() {
        assertAccepted("""
                module m {
                    pub func println(value: Integer) { }
                    pub func Integer(value: Integer) -> Integer { return value + 1 }
                }
                m::println(1)
                m::println(m::Integer(1))
                TextWriter(Stdio.stdout()).writeLine((m::Integer(1)) .. "")
                """);
        assertAccepted("""
                type Tools {
                    pub func Tools() { }

                    pub func exit() -> Integer { return 7 }

                    static pub func toLong(value: Integer) -> Long { return Long(value) }

                    pub func run() -> Long { return Tools.toLong(self.exit()) }
                }
                TextWriter(Stdio.stdout()).writeLine((Tools().run()) .. "")
                """);
    }

    @Test
    @DisplayName("print and println are ordinary names now that the builtins are gone")
    void print_and_println_are_ordinary_names() {
        assertAccepted("""
                func println(value: Integer) {
                    TextWriter(Stdio.stdout()).writeLine((value) .. "")
                }

                func print() { }

                println(1)
                print()
                """);
    }

    @Test
    @DisplayName("an ordinary type name that only looks built in is still allowed")
    void only_exact_built_in_names_are_reserved() {
        assertAccepted("""
                type Strings {
                    pub func Strings() { }
                }
                TextWriter(Stdio.stdout()).writeLine((Strings()) .. "")
                """);
    }
}
