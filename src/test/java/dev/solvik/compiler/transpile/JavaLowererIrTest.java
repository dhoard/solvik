package dev.solvik.compiler.transpile;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.ast.Ast;
import dev.solvik.compiler.check.TypeChecker;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.jir.JDecl;
import dev.solvik.compiler.jir.JExpr;
import dev.solvik.compiler.jir.JStmt;
import dev.solvik.compiler.jir.JType;
import dev.solvik.compiler.jir.JTypes;
import dev.solvik.compiler.lexer.Lexer;
import dev.solvik.compiler.parser.Parser;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import dev.solvik.compiler.testsupport.Checking;

/**
 * Lowering contracts observed on the typed Java IR itself, without going through the renderer.
 */
@DisplayName("Java lowering IR")
class JavaLowererIrTest {

    private record Lowered(JDecl.CompilationUnit unit, TypeChecker.Result types) {
    }

    private static Lowered lower(String source) {
        Diagnostics diagnostics = new Diagnostics();
        Ast.Program program = new Parser(new Lexer(source, diagnostics).tokenize(), diagnostics)
                .parseProgram();
        TypeChecker.Result types = Checking.check(program, diagnostics);
        assertThat(diagnostics.all()).describedAs("%s", diagnostics.format("<t>")).isEmpty();
        return new Lowered(new JavaLowerer(types, Set.of()).lowerProgram(program), types);
    }

    private static Lowered lowerType(String source, String name) {
        Diagnostics diagnostics = new Diagnostics();
        Ast.Program program = new Parser(new Lexer(source, diagnostics).tokenize(), diagnostics)
                .parseProgram();
        TypeChecker.Result types = Checking.check(program, diagnostics);
        assertThat(diagnostics.all()).describedAs("%s", diagnostics.format("<t>")).isEmpty();
        Ast.TypeDecl declaration = program.types().stream()
                .filter(type -> type.name().equals(name)).findFirst().orElseThrow();
        return new Lowered(new JavaLowerer(types, Set.of(name)).lowerType(declaration, name),
                types);
    }

    private static JDecl.Method method(JDecl.CompilationUnit unit, String name) {
        return unit.primary().members().stream()
                .filter(member -> member instanceof JDecl.Method candidate
                        && candidate.name().equals(name))
                .map(JDecl.Method.class::cast)
                .findFirst().orElseThrow();
    }

    private static void collectExpressions(List<JStmt> statements, List<JExpr> into) {
        for (JStmt statement : statements) {
            switch (statement) {
                case JStmt.Block block -> collectExpressions(block.statements(), into);
                case JStmt.VarDecl declaration -> {
                    if (declaration.initializer() != null) {
                        into.add(declaration.initializer());
                    }
                }
                case JStmt.ExpressionStatement expression ->
                        into.add(expression.expression());
                case JStmt.If branch -> {
                    into.add(branch.condition());
                    collectExpressions(List.of(branch.thenBranch()), into);
                    if (branch.elseBranch() != null) {
                        collectExpressions(List.of(branch.elseBranch()), into);
                    }
                }
                case JStmt.While loop -> {
                    into.add(loop.condition());
                    collectExpressions(List.of(loop.body()), into);
                }
                case JStmt.ForEach loop -> {
                    into.add(loop.iterable());
                    collectExpressions(List.of(loop.body()), into);
                }
                case JStmt.For loop -> {
                    collectExpressions(loop.init(), into);
                    into.add(loop.condition());
                    collectExpressions(loop.update(), into);
                    collectExpressions(List.of(loop.body()), into);
                }
                case JStmt.Switch selection -> {
                    into.add(selection.selector());
                    for (JStmt.SwitchArm arm : selection.arms()) {
                        if (arm.label() != null) {
                            into.add(arm.label());
                        }
                        collectExpressions(arm.body(), into);
                    }
                    if (selection.defaultBody() != null) {
                        collectExpressions(List.of(selection.defaultBody()), into);
                    }
                }
                case JStmt.Return returned -> {
                    if (returned.value() != null) {
                        into.add(returned.value());
                    }
                }
                case JStmt.Throw thrown -> into.add(thrown.exception());
                case JStmt.Try attempt -> {
                    collectExpressions(List.of(attempt.body()), into);
                    for (JStmt.Catch catcher : attempt.catches()) {
                        collectExpressions(List.of(catcher.body()), into);
                    }
                }
                case JStmt.Synchronized guarded -> {
                    into.add(guarded.monitor());
                    collectExpressions(List.of(guarded.body()), into);
                }
                case JStmt.SuperCall call -> into.addAll(call.arguments());
                case JStmt.Break ignored -> { }
                case JStmt.Continue ignored -> { }
            }
        }
    }

    @Test
    @DisplayName("expressions carry their Java type and calls carry their resolved target")
    void expressions_are_typed_and_calls_name_their_target() {
        Lowered lowered = lower("""
                func twice(n: Integer) -> Integer { return n * 2 }
                result := twice(4)
                """);
        JDecl.Method main = method(lowered.unit(), "main");
        List<JExpr> expressions = new ArrayList<>();
        collectExpressions(main.body(), expressions);

        JExpr.Call call = expressions.stream()
                .filter(JExpr.Call.class::isInstance)
                .map(JExpr.Call.class::cast)
                .filter(candidate -> candidate.name().equals("twice"))
                .findFirst().orElseThrow();
        assertThat(call.receiver()).isNull();
        assertThat(call.owner().binaryName()).isEqualTo(GeneratedNames.PROGRAM_BINARY_NAME);
        assertThat(call.arguments()).singleElement()
                .isInstanceOfSatisfying(JExpr.IntLiteral.class, argument -> {
                    assertThat(argument.digits()).isEqualTo("4");
                    assertThat(argument.type()).isEqualTo(JType.Primitive.INT);
                });
    }

    @Test
    @DisplayName("a Solvik array lowers to the structural List carrier of its element type")
    void array_types_are_structural() {
        Lowered lowered = lower("""
                func grid(rows: Integer[][]) -> Integer {
                    return rows.size
                }
                """);
        JDecl.Method grid = method(lowered.unit(), "grid");
        JType parameter = grid.parameters().getFirst().type();

        assertThat(parameter).isEqualTo(JTypes.list(JTypes.list(JTypes.INTEGER)));
        assertThat(parameter).isEqualTo(new JType.Declared("java.util", List.of(), "List",
                List.of(new JType.Declared("java.util", List.of(), "List",
                        List.of(JTypes.INTEGER)))));
    }

    @Test
    @DisplayName("the single static block follows fields and calls its initializer method")
    void static_initializers_follow_fields() {
        Lowered lowered = lowerType("""
                type Init {
                    static mut a := 0
                    static mut b := 0
                    static {
                        a = 1
                        b = 2
                    }
                }
                """, "Init");

        List<JDecl.Member> members = lowered.unit().primary().members();
        int fieldA = indexOfField(members, "a");
        int fieldB = indexOfField(members, "b");
        int block = indexOfStaticInitializer(members, 0);
        assertThat(fieldA).isLessThan(fieldB);
        assertThat(fieldB).isLessThan(block);

        JDecl.StaticInitializer initializer = (JDecl.StaticInitializer) members.get(block);
        JStmt.ExpressionStatement statement =
                (JStmt.ExpressionStatement) initializer.body().getFirst();
        assertThat(((JExpr.Call) statement.expression()).name())
                .isEqualTo("$solvikStaticInit0");
        assertThat(method(lowered.unit(), "$solvikStaticInit0").body()).hasSize(2);
    }

    private static int indexOfField(List<JDecl.Member> members, String name) {
        for (int index = 0; index < members.size(); index++) {
            if (members.get(index) instanceof JDecl.Field field && field.name().equals(name)) {
                return index;
            }
        }
        throw new AssertionError("no field " + name);
    }

    private static int indexOfStaticInitializer(List<JDecl.Member> members, int ordinal) {
        int remaining = ordinal;
        for (int index = 0; index < members.size(); index++) {
            if (members.get(index) instanceof JDecl.StaticInitializer) {
                if (remaining == 0) {
                    return index;
                }
                remaining--;
            }
        }
        throw new AssertionError("no static initializer " + ordinal);
    }

    @Test
    @DisplayName("a concatenation passes its operands to rendering in source order")
    void concatenation_operands_are_structural() {
        Lowered lowered = lower("""
                type Box {
                    pub func Box() { }
                }
                count := 1
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).write((count) .. (Box()))
                """);
        JDecl.Method main = method(lowered.unit(), "main");
        JStmt.ExpressionStatement printed = (JStmt.ExpressionStatement) main.body().getLast();
        JExpr.Call write = (JExpr.Call) printed.expression();
        assertThat(write.name()).isEqualTo("write");

        JExpr.Call concatenation = (JExpr.Call) write.arguments().getFirst();
        assertThat(concatenation.name()).isEqualTo("concat");
        assertThat(concatenation.arguments()).hasSize(2);
        assertThat(concatenation.arguments().get(0)).isInstanceOf(JExpr.Name.class);
        assertThat(concatenation.arguments().get(1)).isInstanceOf(JExpr.New.class);
    }

    @Test
    @DisplayName("Object contract overrides keep their primitive Java signatures")
    void object_contract_overrides_keep_primitive_results() {
        Lowered lowered = lowerType("""
                type Point {
                    pub func Point() { }
                    pub func equals(other: Any?) -> Boolean { return false }
                    pub func hashCode() -> Integer { return 1 }
                }
                """, "Point");

        assertThat(method(lowered.unit(), "equals").returnType())
                .isEqualTo(JType.Primitive.BOOLEAN);
        assertThat(method(lowered.unit(), "hashCode").returnType())
                .isEqualTo(JType.Primitive.INT);
    }

    @Test
    @DisplayName("an array parameter keeps its nested List carrier in natives and traits")
    void trait_defaults_use_the_same_structural_types() {
        Lowered lowered = lowerType("""
                trait Counter {
                    pub func count(values: Integer[]) -> Integer { return values.size }
                }
                """, "Counter");

        JDecl.Method bridge = method(lowered.unit(), "$default$count$0");
        assertThat(bridge.parameters().get(1).type())
                .isEqualTo(JTypes.list(JTypes.INTEGER));
        assertThat(bridge.modifiers()).contains(JDecl.Modifier.STATIC);
    }
}
