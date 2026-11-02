package dev.solvik.compiler.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.ast.Ast;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.lexer.Lexer;
import dev.solvik.compiler.parser.Parser;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import dev.solvik.compiler.testsupport.Checking;

/** Generic user types: declaration, inference, substitution, and field fixation. */
@DisplayName("Type checker / generics")
class GenericsTest {

    private static final String BOX = """
            type Box<T> {
                mut value: T? = Null

                pub func Box(value: T) {
                    self.value = value
                }

                pub func get() -> T? {
                    return self.value
                }
            }
            """;

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

    private static void assertRejected(String source) {
        assertThat(diagnostics(source).all()).isNotEmpty();
    }

    @Test
    void a_generic_type_is_declared_built_and_used() {
        assertAccepted(BOX + """
                numbers := Box(1)
                words := Box("text")
                count := numbers.get()          // Integer?
                if count != Null {
                    TextWriter(Stdio.stdout()).writeLine((count + 1) .. "")
                }
                TextWriter(Stdio.stdout()).writeLine((words.get()) .. "")
                """);
    }

    @Test
    @DisplayName("a construction must decide its type arguments where it is declared")
    void an_undecided_construction_is_reported_at_its_declaration() {
        assertRejected("""
                type List<T> {
                    mut items: T?[2] = T?[2] { }
                    mut count := 0

                    pub func List() { }

                    pub func add(value: T) {
                        items[count] = value
                        count = count + 1
                    }
                }

                list := List()
                list.add(1)
                """);
        assertThat(diagnostics("""
                type List<T> {
                    mut items: T?[2] = T?[2] { }

                    pub func List() { }
                }

                list := List()
                """).all())
                .anySatisfy(diagnostic -> assertThat(diagnostic.message())
                        .contains("cannot infer type argument 'T'"));
    }

    @Test
    @DisplayName("an inferred instantiation is an ordinary generic type and rejects the wrong value")
    void a_generic_instantiation_rejects_a_conflicting_argument() {
        assertRejected("""
                type Both<T> {
                    mut value: T? = Null

                    pub func Both(value: T) {
                        self.value = value
                    }

                    pub func set(other: T) {
                        self.value = other
                    }
                }

                both := Both(1)
                both.set("text")
                """);
    }

    @Test
    @DisplayName("a parameter typed by an array of the parameter is inferred from the argument")
    void an_array_parameter_decides_the_argument() {
        assertAccepted("""
                type Bag<T> {
                    mut items: T?[2] = T?[2] { }

                    pub func Bag(seed: T) {
                        items[0] = seed
                    }

                    pub func first(values: T[]) -> T {
                        return values[0]
                    }
                }

                bag := Bag("a")
                TextWriter(Stdio.stdout()).writeLine((bag.first(String[2] {"x", "y"})) .. "")
                """);
    }

    @Test
    void two_type_parameters_are_inferred_independently() {
        assertAccepted("""
                type Pair<A, B> {
                    first: A? = Null
                    second: B? = Null

                    pub func Pair(first: A, second: B) {
                        self.first = first
                        self.second = second
                    }

                    pub func describe() -> String {
                        return "(" .. self.first .. ", " .. self.second .. ")"
                    }
                }

                pair := Pair(1, "one")
                TextWriter(Stdio.stdout()).writeLine((pair.describe()) .. "")
                """);
    }

    @Test
    @DisplayName("a type parameter may appear as an argument type and as an array element")
    void type_parameters_compose_with_other_types() {
        assertAccepted(BOX + """
                boxes := Box(Box(1))
                TextWriter(Stdio.stdout()).writeLine((boxes.get()) .. "")

                list := Box(Byte[2] {1, 2})
                mut values := list.get()        // Byte[2]?
                if values != Null {
                    TextWriter(Stdio.stdout()).writeLine((values.size) .. "")
                }
                """);
    }

    @Test
    void generic_values_are_children_of_any_and_keep_their_name() {
        assertAccepted(BOX + """
                values := Any[2] {Box(1), Box("x")}
                for value in values {
                    TextWriter(Stdio.stdout()).writeLine((value.type()) .. "")
                }
                """);
    }

    @Test
    void every_field_declaration_needs_an_initializer() {
        assertRejected("type Lazy {\n value: Integer\n}");
        assertRejected("type Lazy {\n var value\n}");
        assertAccepted("""
                type Eager {
                    value := 1
                }
                """);
    }

    @Test
    void unconstructible_uninferable_and_mistyped_uses_are_rejected() {
        assertRejected(BOX + "b := Box()");                 // arity
        assertRejected(BOX + "b := Box(1, 2)");             // arity
        assertRejected("""
                type Holder<T> {
                    mut value: T? = Null

                    pub func Holder() {
                        self.value = 1
                    }
                }
                h := Holder()
                """);                                                // no argument to infer from
        assertRejected("""
                type Box<T> {
                    mut value: T? = Null

                    pub func Box(value: T) {
                        self.value = value
                    }

                    pub func peek() -> T? {
                        return self.value
                    }
                }
                b := Box(1)
                s := b.peek()
                n := s + "text"
                """);                                                // Integer? is not numeric
    }

    @Test
    @DisplayName("a nullable generic field must be proven present before arithmetic")
    void a_nullable_generic_field_requires_narrowing() {
        assertRejected("""
                type Box<T> {
                    mut value: T? = Null

                    pub func Box(value: T) {
                        self.value = value
                    }

                    pub func peek() -> T? {
                        return self.value
                    }
                }
                b := Box(1)
                TextWriter(Stdio.stdout()).writeLine((b.peek() + 1) .. "")
                """);
    }

    @Test
    @DisplayName("a generic type cannot be named where a type is written")
    void written_type_arguments_are_rejected() {
        assertRejected(BOX + "func tag(b: Box<Integer>) -> String {\n    return \"b\"\n}");
        assertRejected(BOX + "func empty() -> Box<Integer> {\n    return Box(1)\n}");
        assertRejected(BOX + "func many(boxes: Box<Integer>[]) -> String {\n"
                + "    return \"x\"\n}");
        assertRejected(BOX + "func tag(b: Box) -> String {\n    return \"b\"\n}");
        assertRejected(BOX + "func many(boxes: Box[]) -> String {\n    return \"x\"\n}");
    }

    @Test
    void a_written_generic_type_reports_its_own_code() {
        List<String> codes = diagnostics(BOX + "func tag(b: Box<Integer>) -> String {\n"
                + "    return \"b\"\n}").all().stream().map(d -> d.code()).toList();
        assertThat(codes).contains(TypeChecker.WRITTEN_TYPE_ARGUMENT);
    }

    @Test
    @DisplayName("array types, plain user types, and type parameters stay writable")
    void other_written_types_are_unaffected() {
        assertAccepted("""
                type Point {
                    x := 0

                    pub func Point() { }
                }
                func first(ps: Point[]) -> String {
                    return \"ok\"
                }
                func counts(values: Integer[]) -> Integer {
                    return values.size
                }
                func grid(cells: Integer[][]) -> Integer {
                    return cells.size
                }
                """);
        assertAccepted("""
                type Bag<T> {
                    mut value: T? = Null

                    pub func Bag(value: T) {
                        self.value = value
                    }

                    pub func first(items: T[]) -> T {
                        return items[0]
                    }
                }
                bag := Bag(1)
                TextWriter(Stdio.stdout()).writeLine((bag.first(Integer[2] {4, 5})) .. "")
                """);
    }

    @Test
    void type_parameters_must_start_uppercase() {
        List<String> codes = diagnostics("type Box<t> {\n value := 1\n}")
                .all().stream().map(d -> d.code()).toList();
        assertThat(codes).isNotEmpty();
    }
}
