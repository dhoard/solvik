package dev.solvik.compiler.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.ast.Ast;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.lexer.Lexer;
import dev.solvik.compiler.parser.Parser;
import dev.solvik.compiler.testsupport.Checking;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code static { ... }} blocks in concrete type bodies ({@code LANGUAGE.md} §3.4).
 *
 * <p>The block is a static-context statement list, not a function: no {@code self}, no type
 * parameters, and no {@code return}. It is rejected in traits and native declarations, which have
 * no concrete class initialization of their own.
 */
@DisplayName("static initializer blocks")
class StaticInitializerTest {

    private static Diagnostics diagnostics(String source) {
        Diagnostics diagnostics = new Diagnostics();
        Ast.Program program = new Parser(new Lexer(source, diagnostics).tokenize(), diagnostics)
                .parseProgram();
        Checking.check(program, diagnostics);
        return diagnostics;
    }

    private static void accepted(String source) {
        Diagnostics diagnostics = diagnostics(source);
        assertThat(diagnostics.all())
                .describedAs("unexpected diagnostics: %s", diagnostics.format("<t>"))
                .isEmpty();
    }

    private static void rejected(String source, String code, String message) {
        Diagnostics diagnostics = diagnostics(source);
        assertThat(diagnostics.all()).anySatisfy(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo(code);
            assertThat(diagnostic.message()).contains(message);
        });
    }

    @Test
    @DisplayName("a concrete type accepts ordered static blocks")
    void concrete_type_accepts_blocks() {
        accepted("""
                type Ordered {
                    static mut trace := ""
                    static pub first := trace

                    static {
                        trace = trace .. "a"
                    }

                    static pub func state() -> String {
                        return trace .. ":" .. first
                    }
                }
                """);
    }

    @Test
    @DisplayName("the parser records the field count at each block")
    void field_index_preserves_source_order() {
        Diagnostics diagnostics = new Diagnostics();
        Ast.Program program = new Parser(new Lexer("""
                type Ordered {
                    static first := 0
                    static second := 0
                    static third := 0
                    static { }
                }
                """, diagnostics).tokenize(), diagnostics).parseProgram();

        assertThat(program.types().getFirst().staticInitializers())
                .extracting(Ast.StaticInitializer::fieldIndex)
                .containsExactly(3);
    }

    @Test
    @DisplayName("a type rejects a second static block")
    void second_block_is_rejected() {
        rejected("""
                type Bad {
                    static first := 0
                    static { }
                    static { }
                }
                """, "SOL-P004", "at most one static initializer");
    }

    @Test
    @DisplayName("a static block has no return")
    void return_is_rejected() {
        rejected("""
                type Bad {
                    static {
                        return
                    }
                }
                """, TypeChecker.RETURN_OUTSIDE_FUNCTION, "static initializer");
    }

    @Test
    @DisplayName("a static block has no self")
    void self_is_rejected() {
        rejected("""
                type Bad {
                    static {
                        ignored := String(self)
                    }
                }
                """, TypeChecker.TYPE_ERROR, "no 'self'");
    }

    @Test
    @DisplayName("a static block cannot use an enclosing type parameter")
    void type_parameter_is_unavailable() {
        rejected("""
                type Box<T> {
                    static {
                        value: T? = Null
                    }
                }
                """, TypeChecker.TYPE_ERROR, "type parameter");
    }

    @Test
    @DisplayName("a static block cannot write an immutable static field")
    void immutable_static_field_is_rejected() {
        rejected("""
                type Bad {
                    static pub value := 1
                    static {
                        value = 2
                    }
                }
                """, TypeChecker.IMMUTABLE_MEMBER, "immutable");
    }

    @Test
    @DisplayName("a static block may construct the enclosing type through its private constructor")
    void private_constructor_is_available() {
        accepted("""
                type Config {
                    static mut instance: Config? = Null
                    static {
                        instance = Config()
                    }
                    func Config() { }
                    static pub func current() -> Config? {
                        return instance
                    }
                }
                """);
    }

    @Test
    @DisplayName("traits and native class bindings reject static blocks")
    void traits_and_native_types_reject_blocks() {
        rejected("""
                trait Bad {
                    static {
                        ignored := 1
                    }
                }
                """, "SOL-P004", "trait");
        rejected("""
                type Bad native "java.lang.Math" {
                    static {
                        ignored := 1
                    }
                }
                """, "SOL-P004", "native type");
    }

    @Test
    @DisplayName("a static block is only valid inside a type body")
    void block_outside_a_type_is_rejected() {
        rejected("""
                static {
                    ignored := 1
                }
                """, "SOL-P004", "static");
        rejected("""
                module m {
                    static {
                        ignored := 1
                    }
                }
                """, "SOL-P004", "static");
    }
}
