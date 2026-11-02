package dev.solvik.compiler.ast;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.diagnostic.SourcePos;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AST nodes")
class AstTest {

    private static final SourcePos POS = new SourcePos(0, 1, 1);

    @Test
    void type_reference_renders_written_syntax() {
        Ast.TypeRef simple = new Ast.TypeRef.Named("Integer", POS);
        Ast.TypeRef array = new Ast.TypeRef.Array(
                new Ast.TypeRef.Named("Byte", POS), null, false, POS);
        Ast.TypeRef matrix = new Ast.TypeRef.Array(
                new Ast.TypeRef.Array(new Ast.TypeRef.Named("Integer", POS), null, false, POS),
                null, false, POS);

        assertThat(simple).isInstanceOf(Ast.TypeRef.Named.class);
        assertThat(simple).hasToString("Integer");
        assertThat(array).isInstanceOf(Ast.TypeRef.Array.class);
        assertThat(array).hasToString("Byte[]");
        assertThat(matrix).hasToString("Integer[][]");
        assertThat(((Ast.TypeRef.Array) matrix).element()).hasToString("Integer[]");
        assertThat(((Ast.TypeRef.Array) array).element()).hasToString("Byte");
    }

    @Test
    void a_fixed_length_array_names_its_length_and_a_nullable_type_ends_with_a_question_mark() {
        Ast.TypeRef fixed = new Ast.TypeRef.Array(new Ast.TypeRef.Named("Integer", POS),
                new Ast.IntLit("2", POS), false, POS);
        Ast.TypeRef nullableElements = new Ast.TypeRef.Array(
                new Ast.TypeRef.Named("String", List.of(), true, POS), new Ast.IntLit("3", POS),
                false, POS);
        Ast.TypeRef nullableArray = new Ast.TypeRef.Array(new Ast.TypeRef.Named("String", POS),
                new Ast.IntLit("3", POS), true, POS);

        assertThat(fixed).hasToString("Integer[2]");
        assertThat(nullableElements).hasToString("String?[3]");
        assertThat(nullableArray).hasToString("String[3]?");
        assertThat(nullableArray.nullable()).isTrue();
    }

    @Test
    void a_written_generic_argument_is_not_an_array() {
        assertThat(new Ast.TypeRef.Named("Array", POS)).isInstanceOf(Ast.TypeRef.Named.class);
        assertThat(new Ast.TypeRef.Named("Box",
                List.of(new Ast.TypeRef.Named("Integer", POS)), false, POS))
                .isInstanceOf(Ast.TypeRef.Named.class);
    }

    @Test
    void a_declared_name_matching_the_type_marks_a_constructor() {
        Ast.FuncDecl constructor = new Ast.FuncDecl("Point", List.of(), null, List.of(), POS);
        Ast.FuncDecl method = new Ast.FuncDecl("toString", List.of(),
                new Ast.TypeRef.Named("String", POS), List.of(), POS);

        assertThat(constructor.isConstructor("Point")).isTrue();
        assertThat(method.isConstructor("Point")).isFalse();
    }

    @Test
    void programs_expose_their_declarations() {
        Ast.Program program = new Ast.Program(
                List.of(new Ast.TypeDecl("Point", List.of(), List.of(), POS)),
                List.of(),
                List.of(new Ast.VarStmt("x", new Ast.NullLit(POS), POS)),
                POS);

        assertThat(program.types()).hasSize(1);
        assertThat(program.statements()).hasSize(1);
        assertThat(program.pos()).isEqualTo(POS);
    }
}
