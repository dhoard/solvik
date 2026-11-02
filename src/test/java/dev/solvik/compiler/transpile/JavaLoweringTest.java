package dev.solvik.compiler.transpile;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.SolvikCompiler;
import dev.solvik.compiler.ast.Ast;
import dev.solvik.compiler.check.TypeChecker;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.diagnostic.SourcePos;
import dev.solvik.compiler.lexer.Lexer;
import dev.solvik.compiler.parser.Parser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import dev.solvik.compiler.testsupport.Checking;

/** Lowering edge cases the end-to-end execution tests cannot reach. */
@DisplayName("Java lowering")
class JavaLoweringTest {

    private static TypeChecker.Result check(String source) {
        Diagnostics diagnostics = new Diagnostics();
        Ast.Program program = new Parser(new Lexer(source, diagnostics).tokenize(), diagnostics)
                .parseProgram();
        TypeChecker.Result result = Checking.check(program, diagnostics);
        assertThat(diagnostics.all()).describedAs("%s", diagnostics.format("<t>")).isEmpty();
        return result;
    }

    private static String program(String source) {
        return new SolvikCompiler("program.sol").transpile(source).get("Solvik$Program.java");
    }

    private static String type(String source, String name) {
        return new SolvikCompiler("type.sol").transpile(source).get(name + ".java");
    }

    @Test
    void declares_a_binding_as_its_inferred_type() {
        assertThat(program("mut total := 0\ntotal = 1\nTextWriter(Stdio.stdout()).writeLine((total) .. \"\")"))
                .contains("Integer total = 0;")
                .contains("total = 1;");
    }

    @Test
    @DisplayName("Null is a value of the Null type, carried by a Java reference")
    void lowers_the_null_singleton() {
        assertThat(program("nothing := Null\nTextWriter(Stdio.stdout()).writeLine((nothing) .. \"\")"))
                .contains("Object nothing = null;");
    }

    @Test
    @DisplayName("a generic fixed-size array uses java.util.List, not a generic Java array")
    void emits_erased_generic_array_creation() {
        assertThat(type("""
                type Box<T> {
                    mut value: T?[2] = T?[2] { }

                    pub func Box(seed: T) {
                        self.value = T?[2] { seed }
                    }
                }
                """, "Box"))
                .contains("Solvik$Program.<T>array(2, new Object[] { seed })");
    }

    @Test
    @DisplayName("a nested generic array uses a nested List representation")
    void emits_erased_nested_generic_array_creation() {
        assertThat(type("""
                type Box<T> {
                    mut value: T?[][1] = T?[][1] { T?[1] { } }

                    pub func Box(seed: T) {
                        self.value = T?[][1] { T?[1] { seed } }
                    }
                }
                """, "Box"))
                .contains("Arrays.<List<T>>asList(Arrays.<T>asList(seed))");
    }

    @Test
    @DisplayName("an array's length comes from its declared size, not from its element list (§6)")
    void emits_the_declared_array_size() {
        assertThat(program("exact := String[2] {\"a\", \"b\"}\nTextWriter(Stdio.stdout()).writeLine((exact) .. \"\")"))
                .contains("Arrays.<String>asList(\"a\", \"b\")");
        assertThat(program("short := String?[4] {\"a\"}\nTextWriter(Stdio.stdout()).writeLine((short) .. \"\")"))
                .contains("Solvik$Program.<String>array(4, new Object[] { \"a\" })");
        assertThat(program("empty := Integer?[3] { }\nTextWriter(Stdio.stdout()).writeLine((empty) .. \"\")"))
                .contains("Solvik$Program.<Integer>array(3, new Object[] {})");
    }

    @Test
    void emits_block_statements_loops_and_bare_returns() {
        String java = program("""
                func note() {
                    {
                        TextWriter(Stdio.stdout()).writeLine((1) .. "")
                    }
                    return
                }
                note()
                """);
        assertThat(java).contains("{\n").contains("return;");
    }

    @Test
    void emits_identity_comparison_and_boolean_operators() {
        String java = program("""
                a := 1
                b := 1
                TextWriter(Stdio.stdout()).writeLine((a === b) .. "")
                TextWriter(Stdio.stdout()).writeLine((a != b) .. "")
                TextWriter(Stdio.stdout()).writeLine((!(a == b) || a == b) .. "")
                """);
        assertThat(java)
                .contains("Objects.equals(a, b)")
                .contains("Solvik$Program.equalIntegral(a, b)")
                .contains("||");
    }

    @Test
    void emits_unary_minus_for_both_numeric_kinds() {
        String java = program("""
                TextWriter(Stdio.stdout()).writeLine((-1) .. "")
                TextWriter(Stdio.stdout()).writeLine((-1.5) .. "")
                """);
        // A numeric literal is never null, so its sign is emitted with it.
        assertThat(java)
                .contains("new TextWriter(Stdio.stdout()).writeLine(String.valueOf((Object) (-1)) + \"\");")
                .contains("new TextWriter(Stdio.stdout()).writeLine(String.valueOf((Object) (-1.5d)) + \"\");");
    }

    @Test
    void emits_every_conversion_and_member_call() {
        String java = program("""
                b := Byte(1)
                i := Integer("2")
                d := BigDecimal(1)
                s := String(10)
                f := Boolean("true")
                TextWriter(Stdio.stdout()).writeLine((b) .. (i) .. (d) .. (s) .. (f))
                TextWriter(Stdio.stdout()).writeLine((s.type()) .. (s.toString()) .. (s.hashCode()) .. (s.equals("x")))
                TextWriter(Stdio.stdout()).writeLine((Integer[2] {1, 2}.size) .. "")
                """);
        assertThat(java)
                .contains("Solvik$Program.toByte(1)")
                .contains("String.valueOf((Object) 10)")
                .contains("Solvik$Program.toBoolean(\"true\")")
                .contains("Solvik$Program.type(s)")
                .contains("Solvik$Program.hash(s)")
                .contains("Objects.equals(s, \"x\")")
                .contains(".size()");
    }

    @Test
    void emits_a_checked_generic_peer_for_user_equals() {
        assertThat(type("""
                type Box<T> {
                    mut value: T? = Null

                    pub func Box(value: T) {
                        self.value = value
                    }

                    pub func equals(other: Any?) -> Boolean {
                        mut mine: T? = self.value
                        if mine == Null {
                            return false
                        }
                        return mine.equals(other.value)
                    }
                }
                """, "Box"))
                .contains("if (!(other instanceof Box<?>)) {")
                .contains("@SuppressWarnings(\"unchecked\")")
                .contains("Box<T> $solvikOther = (Box<T>) other;")
                .contains("$solvikOther.value");
    }

    @Test
    void generated_runtime_exceptions_declare_serial_versions() {
        String java = new SolvikCompiler("demo.sol").transpile("exit(0)")
                .get("Solvik$Program.java");

        assertThat(java)
                .contains("public static final class " + GeneratedNames.ERROR_CLASS
                        + " extends RuntimeException {\n"
                        + "        private static final long serialVersionUID = 1L;")
                .contains("public static final class " + GeneratedNames.EXIT_CLASS
                        + " extends RuntimeException {\n"
                        + "        private static final long serialVersionUID = 1L;");
    }

    @Test
    void transpiler_emits_one_file_per_declared_type() {
        Ast.Program program = new Ast.Program(
                List.of(new Ast.TypeDecl("Point", List.of(), List.of(),
                        new SourcePos(0, 1, 1))),
                List.of(), List.of(), new SourcePos(0, 1, 1));
        Diagnostics diagnostics = new Diagnostics();
        TypeChecker.Result types = Checking.check(program, diagnostics);

        Map<String, String> files = new Transpiler().transpile(program, types, diagnostics).files();

        assertThat(files).containsKey("Point.java");
        assertThat(files.get("Point.java")).contains("public final class Point");
    }

    @Test
    void transpiler_reports_its_output_as_an_immutable_map() {
        Transpiler.Output output = new Transpiler().transpile(
                new Ast.Program(List.of(), List.of(), List.of(), new SourcePos(0, 1, 1)),
                check("a := 1"), new Diagnostics());

        assertThat(output.file("Solvik$Program.java")).contains("Solvik$Program");
        assertThat(output.files()).containsKeys("Solvik$Program.java");
    }

    @Test
    @DisplayName("the retired legacy backend switch no longer selects another renderer")
    void the_legacy_switch_has_no_effect() {
        String source = "a := 1\nTextWriter(Stdio.stdout()).writeLine((a) .. \"\")";
        Map<String, String> unified = new SolvikCompiler("demo.sol").transpile(source);
        System.setProperty("solvik.legacyEmitter", "true");
        try {
            assertThat(new SolvikCompiler("demo.sol").transpile(source)).isEqualTo(unified);
        } finally {
            System.clearProperty("solvik.legacyEmitter");
        }
    }

    @Test
    void compiler_facade_returns_files_for_a_valid_program() {
        assertThat(new SolvikCompiler("demo.sol").transpile("TextWriter(Stdio.stdout()).writeLine((1) .. \"\")"))
                .containsKeys("Solvik$Program.java");
    }
}
