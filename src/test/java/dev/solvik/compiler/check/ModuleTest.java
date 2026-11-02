package dev.solvik.compiler.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.ast.Ast;
import dev.solvik.compiler.diagnostic.Diagnostic;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.lexer.Lexer;
import dev.solvik.compiler.lexer.TokenKind;
import dev.solvik.compiler.parser.Parser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import dev.solvik.compiler.testsupport.Checking;

/**
 * Modules for user types (LANGUAGE.md §2, "Modules").
 *
 * <p>A module is a container of type names: blocks with the same name merge, a qualified name is
 * written {@code geometry::Point}, and an unqualified name resolves to the enclosing module first
 * and the top level second.
 */
@DisplayName("Type checker / modules")
class ModuleTest {

    private static final String POINT = """
            module geometry {
                pub type Point {
                    pub mut x := 0
                    pub mut y := 0

                    pub func Point(x: Integer, y: Integer) {
                        self.x = x
                        self.y = y
                    }

                    pub func shifted(dx: Integer) -> Point {
                        return Point(x + dx, y)
                    }
                }
            }
            """;

    @Test
    @DisplayName("a qualified type is constructed through its module")
    void a_qualified_type_is_constructed_through_its_module() {
        assertAccepted(POINT + """
                point := geometry::Point(3, 4)
                TextWriter(Stdio.stdout()).writeLine((point.shifted(1)) .. "")
                """);
    }

    @Test
    void one_type_name_may_exist_in_two_modules() {
        assertAccepted(POINT + """
                module billing {
                    pub type Point {
                        pub note := "bill"

                        pub func Point() { }
                    }
                }

                a := geometry::Point(1, 2)
                b := billing::Point()
                TextWriter(Stdio.stdout()).writeLine((a.type()) .. (" ") .. (b.type()) .. (" ") .. (b.note))
                """);
    }

    @Test
    void blocks_with_the_same_name_merge() {
        assertAccepted(POINT + """
                module geometry {
                    pub type Circle {
                        pub radius := 1

                        pub func Circle(radius: Integer) {
                            self.radius = radius
                        }

                        pub func share(scaled: Point) -> Point {
                            return scaled
                        }
                    }
                }

                circle := geometry::Circle(2)
                TextWriter(Stdio.stdout()).writeLine((circle.share(geometry::Point(1, 1))) .. (" ") .. (circle.radius))
                """);
    }

    @Test
    void a_duplicate_in_one_module_is_reported_with_the_qualified_spelling() {
        assertRejected(POINT + """
                module geometry {
                    pub type Point {
                        pub func Point() { }
                    }
                }
                """, TypeChecker.TYPE_ERROR, "geometry::Point");
    }

    @Test
    void an_unqualified_module_type_is_unknown_at_the_top_level() {
        assertRejected(POINT + """
                point := Point(1, 2)
                """, TypeChecker.TYPE_ERROR, "geometry::Point");
    }

    @Test
    void an_unknown_module_and_an_unknown_member_are_distinguished() {
        assertRejected(POINT + """
                point := shapes::Point(1, 2)
                """, TypeChecker.TYPE_ERROR, "unknown module 'shapes'");
        assertRejected(POINT + """
                point := geometry::Circle(1, 2)
                """, TypeChecker.TYPE_ERROR, "declares no type or function 'Circle'");
    }

    @Test
    void a_module_type_shadows_a_global_type_of_the_same_name() {
        assertAccepted("""
                type Point {
                    pub mut x := 0

                    pub func Point(x: Integer) {
                        self.x = x
                    }
                }

                module geometry {
                    pub type Point {
                        pub mut x := 0

                        pub func Point(x: Integer) {
                            self.x = x
                        }

                        pub func scoped() -> Point {
                            return Point(x + 1)
                        }
                    }
                }

                global := Point(1)
                scoped := geometry::Point(2)
                TextWriter(Stdio.stdout()).writeLine((global.x) .. (" ") .. (scoped.scoped().x))
                """);
    }

    @Test
    void a_qualified_type_composes_with_arrays_and_generics() {
        assertAccepted(POINT + """
                module geometry {
                    pub type Box<T> {
                        pub mut value: T? = Null

                        pub func Box(value: T) {
                            self.value = value
                        }
                    }
                }

                func total(points: geometry::Point[]) -> Integer {
                    mut sum := 0
                    for point in points {
                        sum = sum + point.x
                    }
                    return sum
                }

                grid := geometry::Point[2] { geometry::Point(1, 0), geometry::Point(2, 0) }
                box := geometry::Box(41)
                TextWriter(Stdio.stdout()).writeLine((total(grid)) .. (" ") .. (box.value))
                """);
    }

    @Test
    void a_module_may_declare_functions_callable_through_its_name() {
        assertAccepted("""
                module geometry {
                    pub func area(width: Integer, height: Integer) -> Integer {
                        return width * height
                    }

                    pub func hidden() -> Integer {
                        return 1
                    }

                    pub func exposed() -> Integer {
                        return hidden()
                    }
                }

                TextWriter(Stdio.stdout()).writeLine((geometry::area(2, 3)) .. (" ") .. (geometry::exposed()))
                """);
    }

    @Test
    void module_functions_merge_across_blocks_and_shadow_global_ones() {
        assertAccepted("""
                func describe() -> String {
                    return "global"
                }

                module geometry {
                    pub func describe() -> String {
                        return "scoped"
                    }

                    pub func viaSibling() -> String {
                        return describe()
                    }
                }

                module geometry {
                    pub func other() -> String {
                        return describe()
                    }
                }

                TextWriter(Stdio.stdout()).writeLine((describe()) .. (" ") .. (geometry::describe()) .. (" ") .. (geometry::viaSibling()) .. (" ") .. (geometry::other()))
                """);
    }

    @Test
    void a_duplicate_function_in_one_module_is_rejected_with_the_qualified_spelling() {
        assertRejected("""
                module geometry {
                    pub func area() -> Integer {
                        return 1
                    }

                    pub func area() -> Integer {
                        return 2
                    }
                }
                """, TypeChecker.TYPE_ERROR, "geometry::area");
    }

    @Test
    void a_module_function_is_unknown_outside_its_module_without_qualification() {
        assertRejected("""
                module geometry {
                    pub func area() -> Integer {
                        return 1
                    }
                }

                TextWriter(Stdio.stdout()).writeLine((area()) .. "")
                """, TypeChecker.TYPE_ERROR);
    }

    @Test
    void a_type_may_call_a_module_function_and_a_global_function() {
        assertAccepted("""
                func twice(n: Integer) -> Integer {
                    return n * 2
                }

                module geometry {
                    pub type Point {
                        pub x := 0

                        pub func Point(x: Integer) {
                            self.x = x
                        }

                        pub func doubled() -> Integer {
                            return twice(x)
                        }
                    }

                    pub func area(width: Integer) -> Integer {
                        return width * width
                    }
                }

                TextWriter(Stdio.stdout()).writeLine((geometry::Point(3).doubled()) .. (" ") .. (geometry::area(4)))
                """);
    }

    @Test
    void a_qualified_name_that_is_neither_a_type_nor_a_function_is_reported() {
        assertRejected("""
                module geometry {
                    pub func area() -> Integer {
                        return 1
                    }
                }

                TextWriter(Stdio.stdout()).writeLine((geometry::perimeter()) .. "")
                """, TypeChecker.TYPE_ERROR, "declares no type or function 'perimeter'");
    }

    @Test
    void a_private_type_is_unreachable_from_outside_its_module() {
        assertRejected("""
                module shapes {
                    type Hidden {
                        pub v := 1

                        pub func Hidden() { }
                    }
                }

                leaked := shapes::Hidden()
                """, TypeChecker.PRIVATE_DECLARATION, "shapes::Hidden");
    }

    @Test
    void a_private_type_is_reachable_from_inside_its_module() {
        assertAccepted("""
                module shapes {
                    type Hidden {
                        pub v := 7

                        pub func Hidden() { }
                    }

                    pub type Shown {
                        pub func Shown() { }

                        pub func peek() -> Integer {
                            return Hidden().v        // a body may use a private type
                        }
                    }

                    pub func fresh() -> Integer {
                        return Hidden().v
                    }
                }

                TextWriter(Stdio.stdout()).writeLine((shapes::Shown().peek()) .. (" ") .. (shapes::fresh()))
                """);
    }

    @Test
    void a_private_function_is_unreachable_from_outside_its_module() {
        assertRejected("""
                module shapes {
                    func helper() -> Integer {
                        return 1
                    }

                    pub func api() -> Integer {
                        return helper()
                    }
                }

                TextWriter(Stdio.stdout()).writeLine((shapes::api()) .. (" ") .. (shapes::helper()))
                """, TypeChecker.PRIVATE_DECLARATION, "shapes::helper");
    }

    @Test
    void a_pub_declaration_exposes_only_visible_types() {
        assertRejected("""
                module shapes {
                    type Hidden {
                        pub v := 1

                        pub func Hidden() { }
                    }

                    pub type Shown {
                        pub inner := Hidden()

                        pub func Shown() { }
                    }
                }
                """, TypeChecker.PRIVATE_EXPOSURE, "m::Shown".replace("m::", "shapes::"));
        assertRejected("""
                module shapes {
                    type Hidden {
                        pub v := 1

                        pub func Hidden() { }
                    }

                    pub type Shown {
                        pub func Shown() { }

                        pub func make() -> Hidden {
                            return Hidden()
                        }
                    }
                }
                """, TypeChecker.PRIVATE_EXPOSURE, "shapes::Hidden");
        assertRejected("""
                module shapes {
                    type Hidden {
                        pub v := 1

                        pub func Hidden() { }
                    }

                    pub func consume(hidden: Hidden) -> Integer {
                        return hidden.v
                    }
                }
                """, TypeChecker.PRIVATE_EXPOSURE, "shapes::Hidden");
    }

    @Test
    void a_pub_declaration_may_expose_a_pub_type_of_its_module() {
        assertAccepted("""
                module shapes {
                    pub type Point {
                        pub x := 0

                        pub func Point(x: Integer) {
                            self.x = x
                        }
                    }

                    pub func origin() -> Point {
                        return Point(0)
                    }
                }

                TextWriter(Stdio.stdout()).writeLine((shapes::origin().x) .. "")
                """);
    }

    @Test
    void a_module_holds_no_statements_or_variables() {
        assertRejected("""
                module geometry {
                    x := 1
                }
                """, Parser.INVALID_DECLARATION);
        assertRejected("""
                module geometry {
                    TextWriter(Stdio.stdout()).writeLine(("nope") .. "")
                }
                """, Parser.INVALID_DECLARATION);
    }

    @Test
    void pub_is_only_valid_inside_a_module() {
        assertRejected("""
                pub type Point {
                    pub func Point() { }
                }
                """, Parser.INVALID_DECLARATION);
    }

    @Test
    void pub_is_recorded_on_the_declaration() {
        Diagnostics diagnostics = new Diagnostics();
        Ast.Program program = new Parser(new Lexer("""
                module geometry {
                    pub type Point {
                        pub func Point() { }
                    }

                    type Hidden {
                        pub func Hidden() { }
                    }
                }
                """, diagnostics).tokenize(), diagnostics).parseProgram();

        assertThat(program.modules()).singleElement()
                .satisfies(module -> {
                    assertThat(module.name()).isEqualTo("geometry");
                    assertThat(module.types()).extracting(Ast.TypeDecl::isPublic)
                            .containsExactly(true, false);
                });
        assertThat(diagnostics.all()).isEmpty();
    }

    @Test
    @DisplayName("the reserved module name 'solvik' is rejected because it holds generated Java")
    void a_module_named_like_the_generated_package_is_rejected() {
        assertRejected("""
                module solvik {
                    type Program {
                        pub func Program() { }
                    }
                }
                """, TypeChecker.TYPE_ERROR, "reserved");
    }

    @Test
    void the_module_keyword_and_the_qualifier_are_tokens() {
        Diagnostics diagnostics = new Diagnostics();
        var kinds = new Lexer("module geometry::Point", diagnostics).tokenize().stream()
                .map(token -> token.kind())
                .toList();

        assertThat(kinds).containsExactly(TokenKind.MODULE, TokenKind.IDENTIFIER,
                TokenKind.COLON_COLON, TokenKind.IDENTIFIER, TokenKind.EOF);
        assertThat(diagnostics.all()).isEmpty();
    }

    private static void assertAccepted(String source) {
        Diagnostics result = check(source);
        assertThat(result.all())
                .describedAs("unexpected diagnostics: %s", result.format("<t>"))
                .isEmpty();
    }

    private static void assertRejected(String source, String code) {
        assertRejected(source, code, null);
    }

    private static void assertRejected(String source, String code, String mentioned) {
        var diagnostics = check(source).all();
        assertThat(diagnostics).describedAs("expected %s for: %s", code, source)
                .extracting(Diagnostic::code)
                .contains(code);
        if (mentioned != null) {
            assertThat(diagnostics).extracting(Diagnostic::message)
                    .anySatisfy(message -> assertThat(message).contains(mentioned));
        }
    }

    private static Diagnostics check(String source) {
        Diagnostics diagnostics = new Diagnostics();
        Ast.Program program = new Parser(new Lexer(source, diagnostics).tokenize(), diagnostics)
                .parseProgram();
        Checking.check(program, diagnostics);
        return diagnostics;
    }
}
