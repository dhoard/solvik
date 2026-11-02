package dev.solvik.compiler.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.ast.Ast;
import dev.solvik.compiler.bind.BuiltinBindings;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.lexer.Lexer;
import dev.solvik.compiler.parser.Parser;
import dev.solvik.compiler.testsupport.Checking;
import dev.solvik.compiler.types.SolvikType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The restricted {@code native} declaration and the compiler's bundled binding registry
 * ({@code LANGUAGE.md} §11.6; {@code plans/SOLVIK_IO_DESIGN.md} §6).
 *
 * <p>Trust is loader provenance, not spelling: only a type parsed from a packaged resource may
 * declare a native member, and every declaration must match one exact registry entry.
 */
@DisplayName("bundled native declarations")
class BundledNativeTest {

    private static Ast.Program parse(String source, Diagnostics diagnostics) {
        return new Parser(new Lexer(source, diagnostics).tokenize(), diagnostics).parseProgram();
    }

    @Test
    @DisplayName("a program file cannot declare a native member")
    void untrusted_native_declaration_is_rejected() {
        Diagnostics diagnostics = new Diagnostics();
        Ast.Program program = parse("""
                type Widget {
                    pub native func run() -> Integer
                }
                """, diagnostics);
        TypeChecker.check(program, diagnostics);
        assertThat(diagnostics.all()).anySatisfy(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo(TypeChecker.TYPE_ERROR);
            assertThat(diagnostic.message()).contains("compiler-bundled binding");
        });
    }

    @Test
    @DisplayName("a native member of a trait or a top-level function is rejected")
    void native_belongs_to_a_bundled_type() {
        Diagnostics traitDiagnostics = new Diagnostics();
        Ast.Program trait = parse("""
                trait Widget {
                    native func run() -> Integer
                }
                """, traitDiagnostics);
        TypeChecker.check(trait, traitDiagnostics);
        assertThat(traitDiagnostics.all()).anySatisfy(diagnostic ->
                assertThat(diagnostic.code()).isEqualTo(TypeChecker.TYPE_ERROR));

        Diagnostics functionDiagnostics = new Diagnostics();
        Ast.Program function = parse("native func run() -> Integer\n", functionDiagnostics);
        TypeChecker.check(function, functionDiagnostics);
        assertThat(functionDiagnostics.all()).anySatisfy(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo(TypeChecker.TYPE_ERROR);
            assertThat(diagnostic.message()).contains("belongs to a bundled type");
        });
    }

    @Test
    @DisplayName("a trusted native signature with no registry entry is SOL-T013")
    void missing_registry_entry_is_a_build_error() {
        Diagnostics diagnostics = new Diagnostics();
        Ast.Program program = parse("""
                type Widget {
                    pub native func missing() -> Integer
                }
                """, diagnostics);
        Ast.TypeDecl widget = program.types().getFirst();
        TypeChecker.check(program, diagnostics, ClassLoader.getPlatformClassLoader(), Set.of(),
                Set.of(widget));
        assertThat(diagnostics.all()).anySatisfy(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("SOL-T013");
            assertThat(diagnostic.message()).contains("Widget#missing()", "binding registry");
        });
    }

    @Test
    @DisplayName("a trusted native signature whose result differs from the registry entry is SOL-T013")
    void mismatched_result_is_a_build_error() {
        Diagnostics diagnostics = new Diagnostics();
        Ast.Program program = parse("""
                type Channel {
                    pub native func read(maxBytes: Integer) -> String
                }
                """, diagnostics);
        Ast.TypeDecl channel = program.types().getFirst();
        TypeChecker.check(program, diagnostics, ClassLoader.getPlatformClassLoader(), Set.of(),
                Set.of(channel));
        assertThat(diagnostics.all()).anySatisfy(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("SOL-T013");
            assertThat(diagnostic.message()).contains("declares 'String'", "returns 'Byte[]?'");
        });
    }

    @Test
    @DisplayName("a native declaration has no Solvik body")
    void native_body_is_a_parse_error() {
        Diagnostics diagnostics = new Diagnostics();
        parse("""
                type Widget {
                    pub native func run() -> Integer { return 1 }
                }
                """, diagnostics);
        assertThat(diagnostics.all()).anySatisfy(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("SOL-P004");
            assertThat(diagnostic.message()).contains("no Solvik body");
        });
    }

    @Test
    @DisplayName("the registry matches owner, member, static-ness, parameters, and result")
    void registry_requires_an_exact_signature() {
        assertThat(BuiltinBindings.resolve("Channel", "read", false,
                List.of(SolvikType.INTEGER))).isNotNull();
        assertThat(BuiltinBindings.resolve("Channel", "read", true,
                List.of(SolvikType.INTEGER))).isNull();
        assertThat(BuiltinBindings.resolve("Channel", "read", false,
                List.of(SolvikType.STRING))).isNull();
        assertThat(BuiltinBindings.resolve("Channel", "bytes", false, List.of())).isNull();
        assertThat(BuiltinBindings.resolve("TextWriter", "lineSeparator", true, List.of()))
                .isNotNull();
        assertThat(BuiltinBindings.resolve("TextWriter", "lineSeparator", false, List.of()))
                .isNull();
        assertThat(BuiltinBindings.resolve("File", "openReadChannel", true,
                List.of(SolvikType.STRING))).isNotNull();
        assertThat(BuiltinBindings.resolve("File", "openReadChannel", false,
                List.of(SolvikType.STRING))).isNull();
        assertThat(BuiltinBindings.resolve("File", "openReadChannel", true,
                List.of(SolvikType.INTEGER))).isNull();
        assertThat(BuiltinBindings.resolve("File", "exists", true,
                List.of(SolvikType.STRING))).isNotNull();
        assertThat(BuiltinBindings.resolve("File", "delete", true, List.of()))
                .isNull();
    }

    @Test
    @DisplayName("print and println are no longer built-in call names")
    void removed_output_builtins_are_unresolved() {
        Diagnostics diagnostics = new Diagnostics();
        Ast.Program program = parse("println(1)\n", diagnostics);
        Checking.check(program, diagnostics);
        assertThat(diagnostics.all()).anySatisfy(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo(TypeChecker.TYPE_ERROR);
            assertThat(diagnostic.message()).contains("unknown function 'println'");
        });
    }
}
