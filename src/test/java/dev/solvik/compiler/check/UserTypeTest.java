package dev.solvik.compiler.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.ast.Ast;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.lexer.Lexer;
import dev.solvik.compiler.parser.Parser;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import dev.solvik.compiler.testsupport.Checking;

/** Name resolution and typing for user-defined types (§2, §3 "Type constructors"). */
@DisplayName("Type checker / user types")
class UserTypeTest {

    private static List<String> codes(String source) {
        Diagnostics diagnostics = new Diagnostics();
        Ast.Program program = new Parser(new Lexer(source, diagnostics).tokenize(), diagnostics)
                .parseProgram();
        Checking.check(program, diagnostics);
        return diagnostics.all().stream().map(d -> d.code()).toList();
    }

    private static void assertAccepted(String source) {
        Diagnostics diagnostics = new Diagnostics();
        Ast.Program program = new Parser(new Lexer(source, diagnostics).tokenize(), diagnostics)
                .parseProgram();
        Checking.check(program, diagnostics);
        assertThat(diagnostics.all())
                .describedAs("unexpected diagnostics: %s", diagnostics.format("<t>")).isEmpty();
    }

    @Test
    @DisplayName("a type keeps its own size() function; the name is not reserved")
    void a_user_type_keeps_its_own_size_function() {
        assertAccepted("""
                type Ledger {
                    pub items := Integer[2] {1, 2}
                    pub func Ledger() { }
                    pub func size() -> Integer { return self.items.size }
                }
                TextWriter(Stdio.stdout()).writeLine((Ledger().size()) .. (" ") .. (Ledger().items.size))
                """);
    }

    @Test
    void accepts_a_type_with_a_field_a_constructor_and_methods() {
        assertAccepted("""
                type Point {
                    x := 0
                    y := 0

                    pub func Point(x: Integer, y: Integer) {
                        self.x = x
                        self.y = y
                    }

                    pub func toString() -> String {
                        return "(" .. self.x .. ", " .. self.y .. ")"
                    }
                }

                p := Point(1, 2)
                TextWriter(Stdio.stdout()).writeLine((p.toString()) .. "")
                """);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // two types with the same name
            "type Point { }\ntype Point { }",
            // duplicate field
            "type Point { x := 0\n x := 0 }",
            // a field without a type source
            "type Point { var x }",
            // construction without a constructor
            "type Point { x := 0 }\nvar p : Point()",
            // wrong argument count and wrong argument type
            "type Point {\n x := 0\n func Point(x: Integer) {\n  self.x = x\n }\n}\nvar p : Point()",
            "type Point {\n x := 0\n func Point(x: Integer) {\n  self.x = x\n }\n}\nvar p : Point(\"x\")",
            // unknown field and unknown method
            "type Point {\n x := 0\n func get() -> Integer {\n  return self.nope\n }\n}",
            "type Point {\n x := 0\n}\nvar p : Point()",
            // assigning a wrong type to a field
            "type Point {\n x := 0\n func set() {\n  self.x = \"text\"\n }\n}",
            // a constructor with a return type
            "type Point {\n x := 0\n func Point() -> Integer {\n  return self.x\n }\n}",
            // a method named after another type
            "type Point { x := 0 }\ntype Line { y := 0\n func Point() {\n  self.y = 1\n }\n}",
            // field access outside a type body
            "type Point { x := 0 }\nvar p : Point()\nTextWriter(Stdio.stdout()).writeLine((p.x) .. \"\")",
            // method call with the wrong arity and unknown type
            "type Point {\n x := 0\n func shift(n: Integer) {\n  self.x = n\n }\n}\nvar p : Point()\np.shift()",
            "p := Missing()",
            "func f(x: Missing) {\n}\nf(1)",
    })
    @DisplayName("rejects types that break a settled rule")
    void rejects(String source) {
        assertThat(codes(source)).isNotEmpty();
    }

    @Test
    void accepts_overloaded_constructors() {
        assertAccepted("""
                type Point {
                    x := 0
                    pub func Point() { self.x = 1 }
                    pub func Point(value: Integer) { self.x = value }
                }
                origin := Point()
                other := Point(2)
                TextWriter(Stdio.stdout()).writeLine((origin) .. (other))
                """);
    }

    @Test
    @DisplayName("a type may be named Solvik and SolvikProgram without colliding with generated classes")
    void accepts_generated_class_names() {
        assertAccepted("""
                type Solvik {
                    name := "s"

                    pub func Solvik(name: String) {
                        self.name = name
                    }

                    pub func describe() -> String {
                        return "Solvik(" .. self.name .. ")"
                    }
                }

                type SolvikProgram {
                    label := "p"

                    pub func SolvikProgram(label: String) {
                        self.label = label
                    }

                    pub func describe() -> String {
                        return "Program(" .. self.label .. ")"
                    }
                }

                a := Solvik("alpha")
                b := SolvikProgram("beta")
                TextWriter(Stdio.stdout()).writeLine((a.describe()) .. (" ") .. (b.describe()))
                """);
    }

    @Test
    void the_any_contract_is_inherited_by_user_types() {
        assertAccepted("""
                type Point {
                    x := 0

                    pub func Point(x: Integer) {
                        self.x = x
                    }
                }

                p := Point(1)
                TextWriter(Stdio.stdout()).writeLine((p.type()) .. (" ") .. (p.toString()) .. (" ") .. (p.equals(p)) .. (" ") .. (p.hashCode()))
                """);
    }

    @Test
    void user_types_flow_into_any_and_array_elements() {
        assertAccepted("""
                type Point {
                    x := 0

                    pub func Point(x: Integer) {
                        self.x = x
                    }
                }

                points := Any[2] {Point(1), Point(2)}
                for p in points {
                    TextWriter(Stdio.stdout()).writeLine((p.type()) .. "")
                }
                """);
    }
}
