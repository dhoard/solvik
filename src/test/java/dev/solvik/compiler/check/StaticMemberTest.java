package dev.solvik.compiler.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.ast.Ast;
import dev.solvik.compiler.bind.JavaBinding;
import dev.solvik.compiler.diagnostic.Diagnostic;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.lexer.Lexer;
import dev.solvik.compiler.parser.Parser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import dev.solvik.compiler.testsupport.Checking;

/**
 * Static type members (§3, "Static members").
 *
 * <p>{@code static} identifies a field or function that belongs to its type rather than to an
 * instance. It is not a type, module, local, or parameter modifier.
 */
@DisplayName("Type checker / static members")
class StaticMemberTest {

    private static final String COUNTER = """
            type Counter {
                static mut total := 0
                static label := "count"
                static pub kind := "counter"
                static pub mut limit := 10

                pub func Counter() { }

                pub func add(amount: Integer) -> Integer {
                    total = total + amount
                    return total
                }

                static pub func reset() {
                    total = 0
                }

                static pub func report() -> String {
                    return label .. "=" .. total
                }
            }
            """;

    private static Diagnostics check(String source) {
        Diagnostics diagnostics = new Diagnostics();
        Ast.Program program = new Parser(new Lexer(source, diagnostics).tokenize(), diagnostics)
                .parseProgram();
        Checking.check(program, diagnostics);
        return diagnostics;
    }

    private static Ast.Program parse(String source, Diagnostics diagnostics) {
        return new Parser(new Lexer(source, diagnostics).tokenize(), diagnostics).parseProgram();
    }

    private static void assertAccepted(String source) {
        Diagnostics result = check(source);
        assertThat(result.all())
                .describedAs("unexpected diagnostics: %s", result.format("<t>")).isEmpty();
    }

    private static void assertRejected(String source, String code) {
        assertThat(check(source).all())
                .describedAs("expected %s for: %s", code, source)
                .extracting(Diagnostic::code)
                .contains(code);
    }

    @Test
    void parser_records_static_fields_and_functions() {
        Diagnostics diagnostics = new Diagnostics();
        Ast.Program program = parse(COUNTER, diagnostics);
        assertThat(diagnostics.all()).isEmpty();
        Ast.TypeDecl type = program.types().getFirst();
        assertThat(type.fields()).filteredOn(Ast.FieldDecl::isStatic)
                .extracting(Ast.FieldDecl::name)
                .containsExactly("total", "label", "kind", "limit");
        assertThat(type.methods()).filteredOn(Ast.FuncDecl::isStatic)
                .extracting(Ast.FuncDecl::name)
                .containsExactly("reset", "report");
    }

    @Test
    void static_members_are_reached_through_the_type_and_bare_inside_it() {
        assertAccepted(COUNTER + """
                first := Counter()
                second := Counter()
                firstTotal := first.add(1)
                secondTotal := second.add(2)
                TextWriter(Stdio.stdout()).writeLine((Counter.kind) .. (" ") .. (Counter.limit) .. (" ") .. (firstTotal) .. (" ") .. (secondTotal))
                thirdTotal := first.add(3)
                Counter.limit = 20
                Counter.reset()
                TextWriter(Stdio.stdout()).writeLine((Counter.report()) .. (" ") .. (Counter.limit) .. (" ") .. (thirdTotal))
                """);
    }

    @Test
    @DisplayName("a module-qualified static member is written module::Type.member")
    void qualified_static_access_is_accepted() {
        assertAccepted("""
                module stats {
                    pub type Counter {
                        static mut total := 0
                        pub func Counter() { }
                        static pub func reset() { total = 0 }
                        static pub func next() -> Integer {
                            total = total + 1
                            return total
                        }
                        static pub label := "stats"
                    }
                }
                stats::Counter.reset()
                TextWriter(Stdio.stdout()).writeLine((stats::Counter.next()) .. (" ") .. (stats::Counter.label))
                """);
    }

    @Test
    void a_static_field_initializer_may_read_an_earlier_static_field() {
        assertAccepted("""
                type Settings {
                    static base := 10
                    static pub doubled := base + base
                    pub func Settings() { }
                }
                TextWriter(Stdio.stdout()).writeLine((Settings.doubled) .. "")
                """);
    }

    @Test
    void a_static_field_initializer_may_call_a_static_function() {
        assertAccepted("""
                type Settings {
                    static base := 10
                    static pub doubled := twice(base)
                    static func twice(value: Integer) -> Integer { return value + value }
                    pub func Settings() { }
                }
                TextWriter(Stdio.stdout()).writeLine((Settings.doubled) .. "")
                """);
    }

    @Test
    void a_static_function_may_construct_its_own_type_with_a_private_constructor() {
        assertAccepted("""
                type Node {
                    value := 0
                    func Node(value: Integer) { self.value = value }
                    static pub func of(value: Integer) -> Node { return Node(value) }
                    pub func get() -> Integer { return value }
                }
                TextWriter(Stdio.stdout()).writeLine((Node.of(7).get()) .. "")
                """);
    }

    @Test
    void a_static_field_needs_an_initializer() {
        assertRejected("""
                type Broken {
                    static value: Integer
                    pub func Broken() { }
                }
                """, Parser.DECLARATION_FORM);
        assertRejected("""
                type Broken {
                    static var value
                    pub func Broken() { }
                }
                """, Parser.REMOVED_SYNTAX);
    }

    @Test
    void a_static_field_is_immutable_without_mut() {
        assertRejected(COUNTER + "Counter.label = \"other\"\n",
                TypeChecker.IMMUTABLE_MEMBER);
        assertRejected(COUNTER + """
                counter := Counter()
                counter.label = "other"
                """, TypeChecker.TYPE_ERROR);
    }

    @Test
    void private_static_members_are_unreachable_from_outside_the_declaring_type() {
        assertRejected(COUNTER + "TextWriter(Stdio.stdout()).writeLine((Counter.total) .. \"\")\n", TypeChecker.TYPE_ERROR);
        assertRejected("""
                type Hidden {
                    pub func Hidden() { }
                    static func value() -> Integer { return 1 }
                    static total := 0
                }
                TextWriter(Stdio.stdout()).writeLine((Hidden.value()) .. "")
                """, TypeChecker.TYPE_ERROR);
        assertRejected(COUNTER + "Counter.total = 1\n", TypeChecker.TYPE_ERROR);
    }

    @Test
    void static_members_are_not_reached_through_an_instance() {
        assertRejected(COUNTER + """
                counter := Counter()
                TextWriter(Stdio.stdout()).writeLine((counter.label) .. "")
                """, TypeChecker.TYPE_ERROR);
        assertRejected(COUNTER + """
                counter := Counter()
                TextWriter(Stdio.stdout()).writeLine((counter.report()) .. "")
                """, TypeChecker.TYPE_ERROR);
        assertRejected(COUNTER + """
                counter := Counter()
                counter.limit = 2
                """, TypeChecker.TYPE_ERROR);
    }

    @Test
    void a_type_name_does_not_reach_instance_members() {
        assertRejected(COUNTER + "TextWriter(Stdio.stdout()).writeLine((Counter.total) .. \"\")\n", TypeChecker.TYPE_ERROR);
        assertRejected(COUNTER + "TextWriter(Stdio.stdout()).writeLine((Counter.add(1)) .. \"\")\n", TypeChecker.TYPE_ERROR);
        assertRejected("""
                type Point {
                    pub x := 0
                    pub func Point() { }
                    static pub func origin() -> Point { return Point() }
                }
                TextWriter(Stdio.stdout()).writeLine((Point.x) .. "")
                """, TypeChecker.TYPE_ERROR);
        assertRejected("""
                type Point {
                    pub x := 0
                    pub func Point() { }
                }
                TextWriter(Stdio.stdout()).writeLine((Point.x) .. "")
                """, TypeChecker.TYPE_ERROR);
        assertRejected("""
                type Point {
                    pub func Point() { }
                    pub func size() -> Integer { return 0 }
                }
                TextWriter(Stdio.stdout()).writeLine((Point.size()) .. "")
                """, TypeChecker.TYPE_ERROR);
        assertRejected("""
                type Point {
                    pub func Point() { }
                }
                TextWriter(Stdio.stdout()).writeLine((Point.Point()) .. "")
                """, TypeChecker.TYPE_ERROR);
    }

    @Test
    void a_static_function_has_no_self_and_no_instance_members() {
        assertRejected("""
                type Counter {
                    mut total := 0
                    pub func Counter() { }
                    static pub func read() -> Integer { return self.total }
                }
                """, TypeChecker.TYPE_ERROR);
        assertRejected("""
                type Counter {
                    mut total := 0
                    pub func Counter() { }
                    static pub func read() -> Integer { return total }
                }
                """, TypeChecker.TYPE_ERROR);
        assertRejected("""
                type Counter {
                    pub func Counter() { }
                    pub func value() -> Integer { return 1 }
                    static pub func read() -> Integer { return value() }
                }
                """, TypeChecker.TYPE_ERROR);
    }

    @Test
    void a_constructor_cannot_be_static() {
        assertRejected("""
                type Broken {
                    static pub func Broken() { }
                }
                """, TypeChecker.TYPE_ERROR);
    }

    @Test
    @DisplayName("static members of a generic type cannot use its type parameters")
    void generic_static_members_reject_type_parameters() {
        assertRejected("""
                type Box<T> {
                    mut value: T? = Null
                    pub func Box(value: T) { self.value = value }
                    static pub func make(value: T) -> Any { return value }
                }
                """, TypeChecker.TYPE_ERROR);
        assertRejected("""
                type Box<T> {
                    pub func Box() { }
                    static empty := T?[1] { }
                }
                """, TypeChecker.TYPE_ERROR);
        assertAccepted("""
                type Box<T> {
                    mut value: T? = Null
                    pub func Box(value: T) { self.value = value }
                    pub func get() -> T? { return value }
                }
                TextWriter(Stdio.stdout()).writeLine((Box(1).get()) .. "")
                """);
    }

    @Test
    void a_native_static_function_binds_one_static_java_method() {
        assertAccepted("""
                module jdk {
                    pub type Math native "java.lang.Math" {
                        static pub func abs(value: Integer) -> Integer
                        static pub func max(a: Integer, b: Integer) -> Integer
                    }
                }
                TextWriter(Stdio.stdout()).writeLine((jdk::Math.abs(-2)) .. (" ") .. (jdk::Math.max(1, 2)))
                """);
    }

    @Test
    void native_static_and_instance_declarations_must_match_the_java_member() {
        assertRejected("""
                type Wrong native "java.lang.Math" {
                    pub func abs(value: Integer) -> Integer
                }
                """, JavaBinding.INVALID_BINDING);
        assertRejected("""
                type Wrong native "java.lang.String" {
                    static pub func length() -> Integer
                }
                """, JavaBinding.INVALID_BINDING);
        assertRejected("""
                type Wrong native "java.lang.Math" {
                    static pub func Wrong() { }
                }
                """, JavaBinding.INVALID_BINDING);
    }

    @Test
    void static_is_not_allowed_on_a_type_declaration() {
        Diagnostics diagnostics = check("""
                static type Broken { }
                """);
        assertThat(diagnostics.all()).extracting(Diagnostic::code)
                .contains(Parser.INVALID_DECLARATION);
        assertThat(diagnostics.format("<t>"))
                .contains("cannot be applied to a type declaration");

        assertRejected("""
                module broken {
                    static type Hidden { }
                }
                """, Parser.INVALID_DECLARATION);
    }

    @Test
    void static_is_not_allowed_outside_a_type_body() {
        assertRejected("static func helper() { }\n", Parser.INVALID_DECLARATION);
        assertRejected("module tools {\n    static func helper() { }\n}\n",
                Parser.INVALID_DECLARATION);
        assertRejected("static value := 1\n", Parser.INVALID_DECLARATION);
        assertRejected("func f() {\n    static local := 1\n}\n", Parser.INVALID_STATEMENT);
    }

    @Test
    void duplicate_static_modifiers_are_rejected() {
        assertRejected("""
                type Broken {
                    static static value := 1
                    pub func Broken() { }
                }
                """, Parser.INVALID_DECLARATION);
        assertRejected("""
                type Broken {
                    static static func value() { }
                    pub func Broken() { }
                }
                """, Parser.INVALID_DECLARATION);
    }
}
