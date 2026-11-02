package dev.solvik.compiler.parser;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.ast.Ast;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.lexer.Lexer;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Parser")
class ParserTest {

    private static Ast.Program parse(String source) {
        Diagnostics diagnostics = new Diagnostics();
        Ast.Program program = new Parser(new Lexer(source, diagnostics).tokenize(), diagnostics)
                .parseProgram();
        assertThat(diagnostics.all())
                .describedAs("unexpected diagnostics: %s", diagnostics.format("<test>"))
                .isEmpty();
        return program;
    }

    private static Ast.Expr expression(String source) {
        Ast.VarStmt statement = (Ast.VarStmt) parse("mut x := " + source).statements().getFirst();
        return statement.initializer();
    }

    private static Ast.Stmt firstStatement(String source) {
        return parse(source).statements().getFirst();
    }

    @Nested
    @DisplayName("declarations")
    class Declarations {

        @Test
        void parses_each_declaration_form() {
            Ast.Program program = parse("a := 1\nmut b := 2\nc: Long = 3L\nd? := 4");
            assertThat(program.statements()).hasSize(4);
            Ast.VarStmt inferred = (Ast.VarStmt) program.statements().get(0);
            assertThat(inferred.initializer()).isInstanceOf(Ast.IntLit.class);
            assertThat(inferred.type()).isNull();
            assertThat(inferred.isMutable()).isFalse();
            Ast.VarStmt mutable = (Ast.VarStmt) program.statements().get(1);
            assertThat(mutable.isMutable()).isTrue();
            Ast.VarStmt explicit = (Ast.VarStmt) program.statements().get(2);
            assertThat(((Ast.TypeRef.Named) explicit.type()).name()).isEqualTo("Long");
            Ast.VarStmt nullable = (Ast.VarStmt) program.statements().get(3);
            assertThat(nullable.nullableInferred()).isTrue();
            assertThat(nullable.type()).isNull();
        }

        @Test
        void rejects_a_declaration_without_an_initializer_and_the_removed_var_keyword() {
            Diagnostics diagnostics = new Diagnostics();
            new Parser(new Lexer("x: Integer\nvar y := 1\n", diagnostics).tokenize(), diagnostics)
                    .parseProgram();
            assertThat(diagnostics.all()).extracting(d -> d.code())
                    .contains(Parser.DECLARATION_FORM, Parser.REMOVED_SYNTAX);
        }

        @Test
        @DisplayName("a type body holds fields and a constructor named after the type")
        void parses_a_type_declaration() {
            Ast.Program program = parse("""
                    type Point {
                        mut x := 0
                        y := 0

                        pub func Point(x: Integer, y: Integer) {
                            self.x = x
                            self.y = y
                        }
                    }
                    """);
            assertThat(program.types()).hasSize(1);
            Ast.TypeDecl type = program.types().getFirst();
            assertThat(type.name()).isEqualTo("Point");
            assertThat(type.fields()).extracting(Ast.FieldDecl::name).containsExactly("x", "y");
            assertThat(type.fields().get(1).initializer()).isInstanceOf(Ast.IntLit.class);
            assertThat(type.methods()).hasSize(1);
            Ast.FuncDecl constructor = type.methods().getFirst();
            assertThat(constructor.name()).isEqualTo("Point");
            assertThat(constructor.returnType()).isNull();
            assertThat(constructor.params()).extracting(Ast.Param::name)
                    .containsExactly("x", "y");
            assertThat(type.methods().getFirst().isConstructor("Point")).isTrue();
        }

        @Test
        void parses_a_function_with_a_return_type_and_parameters() {
            Ast.Program program = parse("""
                    func describe(value: Any, index: Integer,) -> String {
                        return value.type()
                    }
                    """);
            Ast.FuncDecl function = program.functions().getFirst();
            assertThat(function.name()).isEqualTo("describe");
            assertThat(function.params()).hasSize(2);
            assertThat(function.returnType()).isEqualTo(
                    new Ast.TypeRef.Named("String", function.returnType().pos()));
            assertThat(function.body()).hasSize(1);
        }

        @Test
        void parses_an_array_of_integers_type() {
            Ast.Program program = parse("""
                    func ids() -> Integer[] {
                        return 0
                    }
                    """);
            Ast.TypeRef.Array returnType =
                    (Ast.TypeRef.Array) program.functions().getFirst().returnType();
            assertThat(returnType.size()).isNull();
            assertThat(((Ast.TypeRef.Named) returnType.element()).name()).isEqualTo("Integer");
        }
    }

    @Nested
    @DisplayName("statements")
    class Statements {

        @Test
        void parses_every_control_flow_form() {
            assertThat(firstStatement("if a { }")).isInstanceOf(Ast.IfStmt.class);
            assertThat(firstStatement("while a { }")).isInstanceOf(Ast.WhileStmt.class);
            assertThat(firstStatement("for x in items { }")).isInstanceOf(Ast.ForStmt.class);
            assertThat(firstStatement("return 1")).isInstanceOf(Ast.ReturnStmt.class);
            assertThat(firstStatement("return")).isInstanceOf(Ast.ReturnStmt.class);
        }

        @Test
        @DisplayName("else if chains collect as branches and else stays a final block")
        void parses_else_if_chains() {
            Ast.IfStmt statement = (Ast.IfStmt) firstStatement("""
                    if a {
                        f()
                    } else if b {
                        g()
                    } else {
                        h()
                    }
                    """);
            assertThat(statement.branches()).hasSize(2);
            assertThat(statement.elseBody()).hasSize(1);
        }

        @Test
        @DisplayName("switch parses literal cases, a regex case, and a default")
        void parses_a_switch() {
            Ast.SwitchStmt statement = (Ast.SwitchStmt) firstStatement("""
                    switch method {
                        case "GET" {
                            f()
                        }
                        case regex "^/api/.*" {
                            g()
                        }
                        default {
                            h()
                        }
                    }
                    """);
            assertThat(statement.subject()).isInstanceOf(Ast.NameExpr.class);
            assertThat(statement.cases()).hasSize(2);
            assertThat(statement.cases().get(0).regex()).isFalse();
            assertThat(statement.cases().get(0).label()).isInstanceOf(Ast.StringLit.class);
            assertThat(statement.cases().get(1).regex()).isTrue();
            assertThat(statement.cases().get(1).label()).isInstanceOf(Ast.StringLit.class);
            assertThat(statement.defaultBody()).hasSize(1);
        }

        @Test
        @DisplayName("parentheses around a switch subject are optional and group away")
        void parentheses_around_the_switch_subject_are_optional() {
            Ast.SwitchStmt bare = (Ast.SwitchStmt) firstStatement("""
                    switch method {
                        case "GET" {
                            f()
                        }
                    }
                    """);
            Ast.SwitchStmt grouped = (Ast.SwitchStmt) firstStatement("""
                    switch (method) {
                        case "GET" {
                            f()
                        }
                    }
                    """);
            assertThat(bare.subject()).isInstanceOf(Ast.NameExpr.class);
            assertThat(grouped.subject()).isInstanceOf(Ast.NameExpr.class);
            assertThat(grouped.cases()).hasSize(1);
        }

        @Test
        void a_switch_may_have_no_default_and_negated_numeric_labels() {
            Ast.SwitchStmt statement = (Ast.SwitchStmt) firstStatement("""
                    switch code {
                        case -1 {
                            f()
                        }
                    }
                    """);
            assertThat(statement.defaultBody()).isNull();
            assertThat(statement.cases().getFirst().label()).isInstanceOf(Ast.UnaryExpr.class);
        }

        @Test
        void a_non_literal_case_label_is_a_parse_error() {
            Diagnostics diagnostics = new Diagnostics();
            new Parser(new Lexer("""
                    switch method {
                        case other {
                            f()
                        }
                    }
                    """, diagnostics).tokenize(), diagnostics).parseProgram();
            assertThat(diagnostics.all())
                    .extracting(dev.solvik.compiler.diagnostic.Diagnostic::code)
                    .contains(Parser.EXPECTED_TOKEN);
        }

        @Test
        void distinguishes_assignment_append_and_calls() {
            assertThat(firstStatement("x = 1")).isInstanceOf(Ast.AssignStmt.class);
            assertThat(((Ast.AssignStmt) firstStatement("x ..= \"a\"")).operator()).isEqualTo("..=");
            assertThat(firstStatement("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((1) .. \"\")")).isInstanceOf(Ast.ExprStmt.class);
        }

        @Test
        void accepts_semicolon_separated_statements_on_one_line() {
            assertThat(parse("a := 1; b := 2").statements()).hasSize(2);
            assertThat(parse("a := 1;;").statements()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("expressions (§7 precedence)")
    class Expressions {

        private static String render(Ast.Expr expr) {
            return switch (expr) {
                case Ast.BinaryExpr b -> "(" + render(b.left()) + b.operator() + render(b.right()) + ")";
                case Ast.UnaryExpr u -> "(" + u.operator() + render(u.operand()) + ")";
                case Ast.IntLit i -> i.text();
                case Ast.DecimalLit d -> d.text();
                case Ast.StringLit s -> "\"" + s.value() + "\"";
                case Ast.NameExpr n -> n.name();
                case Ast.QualifiedNameExpr q -> q.qualifiedName();
                case Ast.OuterExpr o -> "outer " + o.name();
                case Ast.IndexExpr i -> render(i.target()) + "[" + render(i.index()) + "]";
                case Ast.CallExpr c -> render(c.callee()) + "()";
                case Ast.ArrayLit a -> "array(" + a.arrayType() + ")";
                case Ast.MemberExpr m -> render(m.target()) + "." + m.name();
                case Ast.BoolLit b -> Boolean.toString(b.value());
                case Ast.NullLit ignored -> "null";
                case Ast.TypeApplyExpr t -> render(t.target()) + t.arguments();
            };
        }

        @Test
        void follows_the_settled_precedence_table() {
            assertThat(render(expression("2 + 3 * 4"))).isEqualTo("(2+(3*4))");
            assertThat(render(expression("a + b .. c"))).isEqualTo("((a+b)..c)");
            assertThat(render(expression("a .. b == c"))).isEqualTo("((a..b)==c)");
            assertThat(render(expression("a == b && c != d"))).isEqualTo("((a==b)&&(c!=d))");
            assertThat(render(expression("a || b && c"))).isEqualTo("(a||(b&&c))");
            assertThat(render(expression("-a * b"))).isEqualTo("((-a)*b)");
            assertThat(render(expression("!a || b"))).isEqualTo("((!a)||b)");
        }

        @Test
        @DisplayName("a typed array literal is not confused with indexing")
        void distinguishes_array_literals_from_indexing() {
            assertThat(expression("Byte[3] {1, 2, 3}")).isInstanceOf(Ast.ArrayLit.class);
            assertThat(expression("bytes[0]")).isInstanceOf(Ast.IndexExpr.class);
            assertThat(render(expression("bytes[i]"))).isEqualTo("bytes[i]");
        }

        @Test
        void parses_postfix_chains() {
            assertThat(render(expression("self.x"))).isEqualTo("self.x");
            assertThat(expression("f(1).size()")).isInstanceOf(Ast.CallExpr.class);
            assertThat(expression("grid[0][1]")).isInstanceOf(Ast.IndexExpr.class);
        }

        @Test
        void parses_calls_with_trailing_commas() {
            Ast.CallExpr call = (Ast.CallExpr) expression("f(1, 2,)");
            assertThat(call.arguments()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("delimited constructs span lines (§1)")
    class DelimitedConstructs {

        private static List<dev.solvik.compiler.diagnostic.Diagnostic> diagnostics(String source) {
            Diagnostics diagnostics = new Diagnostics();
            new Parser(new Lexer(source, diagnostics).tokenize(), diagnostics).parseProgram();
            return diagnostics.all();
        }

        @Test
        @DisplayName("a grouped expression may span lines, including between its operands")
        void a_grouped_expression_may_span_lines() {
            Ast.BinaryExpr sum = (Ast.BinaryExpr) expression("(\n    1 +\n    2\n)");

            assertThat(sum.operator()).isEqualTo("+");
            assertThat(((Ast.IntLit) sum.left()).text()).isEqualTo("1");
            assertThat(((Ast.IntLit) sum.right()).text()).isEqualTo("2");
        }

        @Test
        @DisplayName("an index may span lines inside its brackets")
        void an_index_may_span_lines() {
            Ast.IndexExpr index = (Ast.IndexExpr) expression("values[\n    0\n]");

            assertThat(((Ast.NameExpr) index.target()).name()).isEqualTo("values");
            assertThat(((Ast.IntLit) index.index()).text()).isEqualTo("0");
        }

        @Test
        @DisplayName("a parenthesized condition may span lines")
        void a_parenthesized_condition_may_span_lines() {
            Ast.IfStmt statement = (Ast.IfStmt) firstStatement(
                    "if (\n    a\n    && b) {\n}\n");

            assertThat(statement.branches()).hasSize(1);
            assertThat(statement.branches().getFirst().condition()).isInstanceOf(
                    Ast.BinaryExpr.class);
        }

        @Test
        @DisplayName("type arguments and an array size may span lines")
        void type_arguments_and_array_sizes_may_span_lines() {
            Ast.Program program = parse("""
                    lists := solvik::collections::List<
                        String
                    >()
                    values := Integer[
                    2
                    ] {1, 2}
                    """);

            assertThat(program.statements()).hasSize(2);
        }

        @Test
        @DisplayName("a newline outside a delimiter still ends a statement")
        void a_newline_outside_a_delimiter_still_ends_a_statement() {
            assertThat(diagnostics("total := 1 +\n2\n")).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("errors")
    class Errors {

        private static List<String> codes(String source) {
            Diagnostics diagnostics = new Diagnostics();
            new Parser(new Lexer(source, diagnostics).tokenize(), diagnostics).parseProgram();
            return diagnostics.all().stream().map(d -> d.code()).toList();
        }

        @Test
        void reports_a_non_statement_expression() {
            assertThat(codes("1 + 2")).contains(Parser.INVALID_STATEMENT);
        }

        @Test
        void reports_a_missing_brace() {
            assertThat(codes("if a { ")).contains(Parser.EXPECTED_TOKEN);
        }

        @Test
        void reports_an_unexpected_token() {
            assertThat(codes("mut x := )")).isNotEmpty();
        }

        private static List<String> messages(String source) {
            Diagnostics diagnostics = new Diagnostics();
            new Parser(new Lexer(source, diagnostics).tokenize(), diagnostics).parseProgram();
            return diagnostics.all().stream().map(d -> d.message()).toList();
        }

        @Test
        @DisplayName("a written type carries the fixed length, including a nested one")
        void a_type_may_name_a_fixed_length() {
            Ast.Program program = parse("func f(g: Integer[2][2]) -> Integer { return 1 }");
            Ast.TypeRef.Array outer =
                    (Ast.TypeRef.Array) program.functions().getFirst().params().getFirst().type();
            assertThat(outer).hasToString("Integer[2][2]");
            assertThat(((Ast.IntLit) outer.size()).text()).isEqualTo("2");
        }

        @Test
        @DisplayName("a nested array literal names its element type and a length at every level")
        void parses_a_nested_array_literal() {
            Ast.Program program = parse(
                    "grid := Integer[2][2] {Integer[2] {1, 2}, Integer[2] {3, 4}}\n");
            assertThat(codes("grid := Integer[2][2] {Integer[2] {1, 2}, Integer[2] {3, 4}}\n"))
                    .isEmpty();
            Ast.VarStmt declaration = (Ast.VarStmt) program.statements().getFirst();
            assertThat(declaration.initializer()).isInstanceOf(Ast.ArrayLit.class);
            assertThat(((Ast.ArrayLit) declaration.initializer()).arrayType())
                    .hasToString("Integer[2][2]");
        }

        @Test
        void reports_a_repeated_nullability_suffix() {
            assertThat(codes("func f(value: String??) { }")).containsExactly(Parser.TYPE_FORM);
        }

        @Test
        @DisplayName("indexing a nested array twice is not mistaken for a nested literal")
        void an_index_chain_stays_an_index_chain() {
            assertThat(codes("cell := grid[0][1]\n")).isEmpty();
            assertThat(codes("if grid[0][1] {\n    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((1) .. \"\")\n}\n")).isEmpty();
        }
    }
}
