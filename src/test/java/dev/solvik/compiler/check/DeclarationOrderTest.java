package dev.solvik.compiler.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.ast.Ast;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.lexer.Lexer;
import dev.solvik.compiler.parser.Parser;
import dev.solvik.compiler.testsupport.Checking;
import dev.solvik.compiler.types.SolvikType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a type body may name does not depend on declaration order (LANGUAGE.md §8.1).
 *
 * <p>Fields and signatures resolve while their own type is built, so they used to see only the
 * declarations indexed before them. A type body now resolves against every declaration in the
 * program: a packaged type with written arguments, a module type, and a type declared later in the
 * same file. The one ordering limitation that remains — reading another type's field from a field
 * initializer — is reported with a message that names the dependency (D-03).
 */
@DisplayName("Type-body name visibility")
class DeclarationOrderTest {

    private record Checked(Ast.Program program, Diagnostics diagnostics, TypeChecker.Result result) { }

    private static Checked check(String source) {
        Diagnostics diagnostics = new Diagnostics();
        Ast.Program program = new Parser(new Lexer(source, diagnostics).tokenize(), diagnostics)
                .parseProgram();
        assertThat(diagnostics.all()).describedAs("parse errors: %s",
                diagnostics.format("test.sol")).isEmpty();
        return new Checked(program, diagnostics, Checking.check(program, diagnostics));
    }

    private static void assertAccepted(String source) {
        Checked checked = check(source);
        assertThat(checked.diagnostics().all())
                .describedAs("unexpected diagnostics: %s", checked.diagnostics().format("test.sol"))
                .isEmpty();
    }

    private static void assertRejected(String source, String code) {
        Checked checked = check(source);
        assertThat(checked.diagnostics().all()).extracting(diagnostic -> diagnostic.code())
                .containsExactly(code);
    }

    /** The resolved type of the field named {@code field} on the top-level type {@code type}. */
    private static SolvikType fieldType(Checked checked, String type, String field) {
        Ast.TypeDecl declaration = checked.program().types().stream()
                .filter(candidate -> candidate.name().equals(type))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no type " + type));
        Ast.FieldDecl declared = declaration.fields().stream()
                .filter(candidate -> candidate.name().equals(field))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no field " + field));
        return checked.result().typeOf(declared);
    }

    @Test
    @DisplayName("a packaged native type takes written arguments in a field and a signature")
    void a_type_body_writes_packaged_type_arguments() {
        Checked checked = check("""
                type Basket {
                    items := solvik::collection::List<Integer>()
                    mut spare: solvik::collection::List<Integer>? = Null

                    pub func Basket() { }

                    pub func combined(source: solvik::collection::List<Integer>) -> Integer {
                        return self.items.size() + source.size()
                    }
                }
                """);
        assertThat(checked.diagnostics().all())
                .describedAs("unexpected diagnostics: %s", checked.diagnostics().format("test.sol"))
                .isEmpty();
        SolvikType integers = SolvikType.user("solvik::collection::List",
                List.of(SolvikType.INTEGER));
        assertThat(fieldType(checked, "Basket", "items"))
                .isEqualTo(integers);
        assertThat(fieldType(checked, "Basket", "spare"))
                .isEqualTo(integers.asNullable());
    }

    @Test
    @DisplayName("a field initializer constructs a type declared later in the same file")
    void a_type_body_constructs_a_later_type() {
        String typeFirst = """
                type Basket {
                    pub seal := Seal("basket")

                    pub func Basket() { }

                    pub func sealLabel() -> String { return self.seal.label }
                }

                type Seal {
                    pub label := ""

                    pub func Seal(label: String) {
                        self.label = label
                    }
                }
                """;
        String dependencyFirst = """
                type Seal {
                    pub label := ""

                    pub func Seal(label: String) {
                        self.label = label
                    }
                }

                type Basket {
                    pub seal := Seal("basket")

                    pub func Basket() { }

                    pub func sealLabel() -> String { return self.seal.label }
                }
                """;
        assertAccepted(typeFirst);
        assertAccepted(dependencyFirst);
        assertThat(fieldType(check(typeFirst), "Basket", "seal"))
                .describedAs("declaration order must not change the inferred field type")
                .isEqualTo(fieldType(check(dependencyFirst), "Basket", "seal"))
                .isEqualTo(SolvikType.user("Seal"));
    }

    @Test
    @DisplayName("a field initializer constructs a type from a module")
    void a_type_body_constructs_a_module_type() {
        assertAccepted("""
                type Basket {
                    pub point := geo::Point(1, 2)

                    pub func Basket() { }

                    pub func sum() -> Integer { return self.point.x + self.point.y }
                }

                module geo {
                    pub type Point {
                        pub mut x := 0

                        pub mut y := 0

                        pub func Point(x: Integer, y: Integer) {
                            self.x = x
                            self.y = y
                        }
                    }
                }
                """);
    }

    @Test
    @DisplayName("only a native type takes written type arguments")
    void a_non_native_type_keeps_its_type_argument_diagnostic() {
        String genericType = """
                type Box<T> {
                    mut value: T? = Null

                    pub func Box(value: T) {
                        self.value = value
                    }
                }
                """;
        assertRejected(genericType + """
                type Basket {
                    item: Box<Integer>? = Null

                    pub func Basket() { }
                }
                """, TypeChecker.WRITTEN_TYPE_ARGUMENT);
        assertRejected("""
                type Basket {
                    item: Box<Integer>? = Null

                    pub func Basket() { }
                }
                """ + genericType, TypeChecker.WRITTEN_TYPE_ARGUMENT);
    }

    @Test
    @DisplayName("a generic type named without arguments is reported in either declaration order")
    void a_generic_type_named_without_arguments_is_order_independent() {
        String genericType = """
                type Box<T> {
                    mut value: T? = Null

                    pub func Box(value: T) {
                        self.value = value
                    }
                }
                """;
        String use = """
                type Basket {
                    item: Box? = Null

                    pub func Basket() { }
                }
                """;
        assertRejected(genericType + use, TypeChecker.WRITTEN_TYPE_ARGUMENT);
        assertRejected(use + genericType, TypeChecker.WRITTEN_TYPE_ARGUMENT);
    }

    @Test
    @DisplayName("a type with no constructor still says so")
    void a_type_without_a_constructor_keeps_its_diagnostic() {
        Checked checked = check("""
                type Basket {
                    helper := Helper()

                    pub func Basket() { }
                }

                type Helper {
                    static pub func make() -> Integer { return 1 }
                }
                """);
        assertThat(checked.diagnostics().all()).singleElement()
                .satisfies(diagnostic -> {
                    assertThat(diagnostic.code()).isEqualTo(TypeChecker.TYPE_ERROR);
                    assertThat(diagnostic.message()).contains("declares no constructor");
                });
    }

    @Test
    @DisplayName("another type's field names the ordering limitation, not a missing field")
    void a_cross_type_field_read_reports_the_ordering_limitation() {
        Checked staticRead = check("""
                type Basket {
                    limit := Settings.limit

                    pub func Basket() { }
                }

                type Settings {
                    static pub limit: Integer = 5
                }
                """);
        assertThat(staticRead.diagnostics().all()).singleElement()
                .satisfies(diagnostic -> {
                    assertThat(diagnostic.code()).isEqualTo(TypeChecker.TYPE_ERROR);
                    assertThat(diagnostic.message())
                            .contains("Settings")
                            .contains("Basket")
                            .doesNotContain("has no field");
                });

        Checked instanceRead = check("""
                type Basket {
                    limit := Settings().limit

                    pub func Basket() { }
                }

                type Settings {
                    pub limit := 5

                    pub func Settings() { }
                }
                """);
        assertThat(instanceRead.diagnostics().all()).singleElement()
                .satisfies(diagnostic -> {
                    assertThat(diagnostic.code()).isEqualTo(TypeChecker.TYPE_ERROR);
                    assertThat(diagnostic.message())
                            .contains("Settings")
                            .doesNotContain("has no field");
                });
    }

    @Test
    @DisplayName("a type's own static field is still readable from its field initializer")
    void a_same_type_static_field_read_is_unchanged() {
        assertAccepted("""
                type Settings {
                    x := Settings.limit
                    static pub limit: Integer = 1

                    pub func Settings() { }
                }
                """);
    }
}
