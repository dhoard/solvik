package dev.solvik.compiler.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.ast.Ast;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.lexer.Lexer;
import dev.solvik.compiler.parser.Parser;
import org.junit.jupiter.api.Test;
import dev.solvik.compiler.testsupport.Checking;

class TraitTest {

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
    void a_type_nominally_implements_a_contract() {
        accepted("""
                trait Work {
                    pub func run()
                }

                type Job implements Work {
                    pub func Job() { }
                    pub func run() { }
                }

                func execute(work: Work) {
                    work.run()
                }

                execute(Job())
                """);
    }

    @Test
    void parser_records_trait_kind_and_implementation_list() {
        Diagnostics diagnostics = new Diagnostics();
        Ast.Program program = new Parser(new Lexer("""
                trait Work { pub func run() }
                trait Named { pub func name() -> String }
                type Job implements Work, Named {
                    pub func Job() { }
                    pub func run() { }
                    pub func name() -> String { return "job" }
                }
                """, diagnostics).tokenize(), diagnostics).parseProgram();
        assertThat(diagnostics.all()).isEmpty();
        assertThat(program.types()).hasSize(3);
        assertThat(program.types().getFirst().isTrait()).isTrue();
        assertThat(program.types().getFirst().methods().getFirst().hasBody()).isFalse();
        assertThat(program.types().getLast().implementedTraits())
                .extracting(type -> ((Ast.TypeRef.Named) type).name())
                .containsExactly("Work", "Named");
    }

    @Test
    void parser_distinguishes_required_empty_and_nonempty_default_functions() {
        Diagnostics diagnostics = new Diagnostics();
        Ast.Program program = new Parser(new Lexer("""
                trait Work {
                    pub func required()
                    pub func empty() { }
                    pub func answer() -> Integer { return 42 }
                }
                """, diagnostics).tokenize(), diagnostics).parseProgram();
        assertThat(diagnostics.all()).isEmpty();
        assertThat(program.types().getFirst().methods())
                .extracting(Ast.FuncDecl::hasBody).containsExactly(false, true, true);
        assertThat(program.types().getFirst().methods().get(1).body()).isEmpty();
        assertThat(program.types().getFirst().methods().get(2).body()).hasSize(1);
    }

    @Test
    void a_missing_or_private_contract_method_is_rejected() {
        assertThat(check("""
                trait Work { pub func run() }
                type Job implements Work { pub func Job() { } }
                """).all()).extracting(diagnostic -> diagnostic.code())
                .contains(TypeChecker.TYPE_ERROR);
        assertThat(check("""
                trait Work { pub func run() }
                type Job implements Work {
                    pub func Job() { }
                    func run() { }
                }
                """).all()).extracting(diagnostic -> diagnostic.code())
                .contains(TypeChecker.TYPE_ERROR);
    }

    @Test
    void traits_are_contract_only_and_not_constructible() {
        assertThat(check("""
                trait Work { pub state := 0 }
                work := Work()
                """).all()).isNotEmpty();
    }

    @Test
    void a_default_can_call_a_required_function_and_is_visible_on_the_concrete_type() {
        accepted("""
                trait Named {
                    pub func name() -> String
                    pub func label() -> String { return "name=" .. name() }
                }
                type Job implements Named {
                    pub func Job() { }
                    pub func name() -> String { return "job" }
                }
                job := Job()
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((job.label()) .. "")
                """);
    }

    @Test
    void one_default_satisfies_matching_abstract_traits_but_two_defaults_conflict() {
        accepted("""
                trait DefaultName { pub func name() -> String { return "default" } }
                trait NeedsName { pub func name() -> String }
                type Job implements DefaultName, NeedsName { pub func Job() { } }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Job().name()) .. "")
                """);
        assertThat(check("""
                trait First { pub func name() -> String { return "first" } }
                trait Second { pub func name() -> String { return "second" } }
                type Job implements First, Second { pub func Job() { } }
                """).format("<t>")).contains("conflicting defaults", "declare a public override");
        accepted("""
                trait First { pub func name() -> String { return "first" } }
                trait Second { pub func name() -> String { return "second" } }
                type Job implements First, Second {
                    pub func Job() { }
                    pub func name() -> String { return "job" }
                }
                """);
    }

    @Test
    void invalid_default_contracts_are_rejected_before_java_emission() {
        assertThat(check("""
                trait Bad { pub func toString() -> String { return "bad" } }
                """).format("<t>")).contains("cannot have a default body");
        assertThat(check("""
                trait Left { pub func value() -> Integer { return 1 } }
                trait Right { pub func value() -> String }
                type Both implements Left, Right { pub func Both() { } }
                """).format("<t>")).contains("incompatible returns");
        assertThat(check("""
                trait Work { pub func run() { solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((self.missing) .. "") } }
                """).all()).isNotEmpty();
        assertThat(check("""
                trait Work { pub func run() { } }
                type Job implements Work {
                    pub func Job() { }
                    func run() { }
                }
                """).format("<t>")).contains("must be a public instance function");
    }

    @Test
    void explicit_default_calls_require_self_direct_implementation_and_a_body() {
        assertThat(check("""
                trait Retry { pub func delay(attempt: Integer) -> Long { return Long(attempt) } }
                type Backoff implements Retry {
                    pub func Backoff() { }
                    pub func delay(attempt: Integer) -> Long {
                        return Retry.delay(Backoff(), attempt)
                    }
                }
                """).format("<t>")).contains("requires self as its receiver");
        assertThat(check("""
                trait Retry { pub func delay(attempt: Integer) -> Long { return Long(attempt) } }
                type Backoff {
                    pub func Backoff() { }
                    pub func delay(attempt: Integer) -> Long {
                        return Retry.delay(self, attempt)
                    }
                }
                """).format("<t>")).contains("does not directly implement trait 'Retry'");
        assertThat(check("""
                trait Retry { pub func delay(attempt: Integer) -> Long }
                type Backoff implements Retry {
                    pub func Backoff() { }
                    pub func delay(attempt: Integer) -> Long {
                        return Retry.delay(self, attempt)
                    }
                }
                """).format("<t>")).contains("has no default body");
        assertThat(check("""
                trait Retry { pub func delay(attempt: Integer) -> Long { return Long(attempt) } }
                type Backoff implements Retry { pub func Backoff() { } }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Retry.delay(Backoff(), 1)) .. "")
                """).format("<t>")).contains("valid only in an instance function");
        assertThat(check("""
                trait Retry { pub func delay(attempt: Integer) -> Long { return Long(attempt) } }
                type Backoff implements Retry {
                    pub func Backoff() { }
                    pub func delay(attempt: Integer) -> Long {
                        return Retry::delay(self, attempt)
                    }
                }
                """).format("<t>")).contains("unknown module 'Retry'");
    }
}
