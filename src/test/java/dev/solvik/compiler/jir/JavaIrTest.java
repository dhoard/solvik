package dev.solvik.compiler.jir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("typed Java IR")
class JavaIrTest {

    @Nested
    @DisplayName("types")
    class Types {

        @Test
        void represents_arrays_structurally_with_their_dimensions() {
            JType.Array bytes = new JType.Array(JTypes.BYTE);
            JType.Array matrix = new JType.Array(bytes);

            assertThat(matrix.dimensions()).isEqualTo(2);
            assertThat(matrix.element()).isEqualTo(bytes);
            assertThat(matrix).hasToString("java.lang.Byte[][]");
        }

        @Test
        void keeps_a_declared_type_as_package_and_name() {
            JType.Declared list = JTypes.list(JTypes.STRING);

            assertThat(list.packageName()).isEqualTo("java.util");
            assertThat(list.name()).isEqualTo("List");
            assertThat(list.topLevelName()).isEqualTo("List");
            assertThat(list.arguments()).containsExactly(JTypes.STRING);
            assertThat(list.binaryName()).isEqualTo("java.util.List");
        }

        @Test
        void represents_a_nested_generated_type_without_preformatted_text() {
            JType.Declared error = JTypes.programNested("Error");

            assertThat(error.topLevelName()).isEqualTo("Solvik$Program");
            assertThat(error.enclosing()).containsExactly("Solvik$Program");
            assertThat(error.binaryName()).isEqualTo("solvik.Solvik$Program.Error");
        }

        @Test
        void maps_solvik_types_to_java_carriers() {
            assertThat(JTypes.of(dev.solvik.compiler.types.SolvikType.array(
                    dev.solvik.compiler.types.SolvikType.BYTE)))
                    .isEqualTo(JTypes.list(JTypes.BYTE));
            assertThat(JTypes.of(dev.solvik.compiler.types.SolvikType.variable("T")))
                    .isEqualTo(new JType.TypeVariable("T"));
            assertThat(JTypes.of(dev.solvik.compiler.types.SolvikType.VOID)).isEqualTo(JTypes.VOID);
        }
    }

    @Nested
    @DisplayName("invariants")
    class Invariants {

        private static JDecl.CompilationUnit unit(JDecl.ClassDecl type) {
            return new JDecl.CompilationUnit("demo", List.of(type));
        }

        @Test
        void rejects_an_assignment_whose_target_cannot_be_assigned() {
            JExpr.Binary target = new JExpr.Binary(JExpr.BinaryOp.PLUS, new JExpr.Name("a",
                    JTypes.INTEGER), new JExpr.Name("b", JTypes.INTEGER), JTypes.INTEGER);

            assertThatThrownBy(() -> new JExpr.Assignment(target,
                    new JExpr.IntLiteral("1", JType.Primitive.INT), JTypes.INTEGER))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("assignment target");
        }

        @Test
        void rejects_duplicate_method_signatures() {
            JDecl.Method first = JDecl.Method.of(Set.of(JDecl.Modifier.PUBLIC), JTypes.VOID, "run",
                    List.of(), List.of());
            JDecl.Method second = JDecl.Method.of(Set.of(JDecl.Modifier.PUBLIC), JTypes.VOID, "run",
                    List.of(), List.of());
            JDecl.ClassDecl type = JDecl.ClassDecl.classOf("Runner").withMembers(List.of(first, second));

            assertThatThrownBy(() -> JavaIrValidator.validate(unit(type)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("duplicate method signature 'run'");
        }

        @Test
        void rejects_a_method_without_a_body_that_is_not_abstract() {
            JDecl.Method missing = new JDecl.Method(Set.of(JDecl.Modifier.PUBLIC), List.of(),
                    JTypes.VOID, "run", List.of(), null, List.of());
            JDecl.ClassDecl type = JDecl.ClassDecl.classOf("Runner").withMembers(List.of(missing));

            assertThatThrownBy(() -> JavaIrValidator.validate(unit(type)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("has no body but is not abstract");
        }

        @Test
        void rejects_a_void_array_element() {
            JDecl.Field field = JDecl.Field.of(Set.of(JDecl.Modifier.PRIVATE),
                    new JType.Array(JTypes.VOID), "bad");
            JDecl.ClassDecl type = JDecl.ClassDecl.classOf("Holder").withMembers(List.of(field));

            assertThatThrownBy(() -> JavaIrValidator.validate(unit(type)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("an array cannot hold void");
        }

        @Test
        void rejects_a_java_keyword_as_a_generated_name() {
            JDecl.ClassDecl type = JDecl.ClassDecl.classOf("class");

            assertThatThrownBy(() -> JavaIrValidator.validate(unit(type)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Java keyword");
        }

        @Test
        void accepts_a_well_formed_unit() {
            JDecl.Method main = JDecl.Method.of(Set.of(JDecl.Modifier.PUBLIC, JDecl.Modifier.STATIC),
                    JTypes.VOID, "main", List.of(new JDecl.Parameter(
                            new JType.Array(JTypes.STRING), "args")), List.of());

            JavaIrValidator.validate(unit(JDecl.ClassDecl.classOf("Program").withMembers(List.of(main))));
        }

        @Test
        void rejects_a_try_statement_with_neither_a_catch_nor_a_finally() {
            assertThatThrownBy(() -> new JStmt.Try(new JStmt.Block(List.of()), List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("needs a catch or a finally clause");
        }

        @Test
        void accepts_try_finally_without_a_catch_and_try_catch_without_a_finally() {
            JStmt body = new JStmt.Block(List.of());
            JStmt.Try withFinally = new JStmt.Try(body, List.of(), new JStmt.Block(List.of()));
            JStmt.Try withCatch = new JStmt.Try(body, List.of(new JStmt.Catch(
                    JTypes.RUNTIME_EXCEPTION, "caught", body)));

            assertThat(withFinally.catches()).isEmpty();
            assertThat(withFinally.finallyBody()).isNotNull();
            assertThat(withCatch.catches()).hasSize(1);
            assertThat(withCatch.finallyBody()).isNull();
        }
    }
}
