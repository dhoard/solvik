package dev.solvik.compiler.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.ast.Ast;
import dev.solvik.compiler.bind.JavaMathMethods;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.lexer.Lexer;
import dev.solvik.compiler.parser.Parser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import dev.solvik.compiler.testsupport.Checking;

class OverloadTest {

    private static Diagnostics check(String source) {
        Diagnostics diagnostics = new Diagnostics();
        Ast.Program program = new Parser(new Lexer(source, diagnostics).tokenize(), diagnostics)
                .parseProgram();
        Checking.check(program, diagnostics);
        return diagnostics;
    }

    private static void accepted(String source) {
        Diagnostics diagnostics = check(source);
        assertThat(diagnostics.all()).describedAs("%s", diagnostics.format("<t>")).isEmpty();
    }

    @Test
    void functions_methods_and_constructors_overload_by_parameter_signature() {
        accepted("""
                func describe(value: Integer) -> String { return "integer" }
                func describe(value: String) -> String { return "string" }

                type Box {
                    mut value := 0
                    pub func Box(value: Integer) { self.value = value }
                    pub func Box(value: String) { self.value = value.size() }
                    pub func put(value: Integer) { self.value = value }
                    pub func put(value: String) { self.value = value.size() }
                }

                first := Box(1)
                second := Box("two")
                first.put(3)
                second.put("four")
                TextWriter(Stdio.stdout()).writeLine((describe(1)) .. (describe("x")))
                """);
    }

    @Test
    void exact_and_erased_duplicate_signatures_are_rejected() {
        assertThat(check("""
                func f(value: Integer) { }
                func f(other: Integer) -> Integer { return other }
                """).all()).extracting(diagnostic -> diagnostic.code())
                .contains(TypeChecker.TYPE_ERROR);
        assertThat(check("""
                func f(value: Integer[]) { }
                func f(value: String[]) { }
                """).all()).extracting(diagnostic -> diagnostic.code())
                .contains(TypeChecker.TYPE_ERROR);
    }

    @Test
    void a_null_call_to_unrelated_nullable_overloads_is_ambiguous() {
        assertThat(check("""
                type Left { pub func Left() { } }
                type Right { pub func Right() { } }
                func choose(value: Left?) -> Integer { return 1 }
                func choose(value: Right?) -> Integer { return 2 }
                TextWriter(Stdio.stdout()).writeLine((choose(Null)) .. "")
                """).all()).extracting(diagnostic -> diagnostic.code())
                .contains(JavaMathMethods.AMBIGUOUS);
    }

    @Test
    @DisplayName("Null matches a nullable parameter and never an ordinary one")
    void null_selects_the_nullable_overload() {
        assertThat(check("""
                func choose(value: Integer) -> Integer { return value }
                func choose(value: Integer?) -> Integer { return 0 }
                TextWriter(Stdio.stdout()).writeLine((choose(Null)) .. (" ") .. (choose(1)))
                """).all()).extracting(diagnostic -> diagnostic.code())
                .contains(TypeChecker.TYPE_ERROR);   // the two erase to one Java signature
        assertThat(check("""
                type Left { pub func Left() { } }
                func choose(value: Left) -> Integer { return 1 }
                func choose(value: Integer?) -> Integer { return 2 }
                TextWriter(Stdio.stdout()).writeLine((choose(Null)) .. (" ") .. (choose(Left())))
                """).all()).extracting(diagnostic -> diagnostic.code())
                .doesNotContain(JavaMathMethods.AMBIGUOUS);
        assertThat(check("""
                type Left { pub func Left() { } }
                func choose(value: Left) -> Integer { return 1 }
                TextWriter(Stdio.stdout()).writeLine((choose(Null)) .. "")
                """).all()).extracting(diagnostic -> diagnostic.code())
                .contains(TypeChecker.TYPE_ERROR);
    }
}
