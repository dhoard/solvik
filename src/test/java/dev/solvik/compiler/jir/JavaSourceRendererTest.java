package dev.solvik.compiler.jir;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Java source renderer")
class JavaSourceRendererTest {

    private static final JavaSourceRenderer RENDERER = new JavaSourceRenderer();

    private static JDecl.CompilationUnit unit(String packageName, JDecl.ClassDecl... types) {
        return new JDecl.CompilationUnit(packageName, List.of(types));
    }

    private static JDecl.ClassDecl sealedClass(String name) {
        return new JDecl.ClassDecl(JDecl.Kind.CLASS,
                Set.of(JDecl.Modifier.PUBLIC, JDecl.Modifier.FINAL), name, List.of(), null,
                List.of(), List.of(), List.of());
    }

    private static JDecl.Method staticMethod(String name, JType returnType, JExpr body) {
        return new JDecl.Method(Set.of(JDecl.Modifier.PUBLIC, JDecl.Modifier.STATIC), List.of(),
                returnType, name, List.of(), List.of(new JStmt.Return(body)), List.of());
    }

    private static JExpr intLiteral(int value) {
        return new JExpr.IntLiteral(Integer.toString(value), JType.Primitive.INT);
    }

    @Test
    @DisplayName("parentheses follow Java precedence and associativity")
    void adds_parentheses_from_precedence() {
        JExpr a = new JExpr.Name("a", JType.Primitive.INT);
        JExpr b = new JExpr.Name("b", JType.Primitive.INT);
        JExpr c = new JExpr.Name("c", JType.Primitive.INT);
        JExpr sum = new JExpr.Binary(JExpr.BinaryOp.PLUS, a, b, JType.Primitive.INT);
        JExpr product = new JExpr.Binary(JExpr.BinaryOp.MULTIPLY, sum, c, JType.Primitive.INT);
        JType intType = JType.Primitive.INT;
        JDecl.ClassDecl type = sealedClass("Precedence").withMembers(List.of(
                staticMethod("grouped", intType, product),
                staticMethod("flat", intType,
                        new JExpr.Binary(JExpr.BinaryOp.PLUS, a,
                                new JExpr.Binary(JExpr.BinaryOp.MULTIPLY, b, c, intType), intType)),
                staticMethod("rightSubtraction", intType,
                        new JExpr.Binary(JExpr.BinaryOp.MINUS, a,
                                new JExpr.Binary(JExpr.BinaryOp.MINUS, b, c, intType), intType)),
                staticMethod("negated", intType,
                        new JExpr.Unary(JExpr.UnaryOp.NEGATE,
                                new JExpr.Unary(JExpr.UnaryOp.NEGATE, a, intType), intType)),
                staticMethod("cast", intType,
                        new JExpr.Cast(JType.Primitive.INT, sum))));

        String java = RENDERER.render(unit("demo", type));

        assertThat(java)
                .contains("return (a + b) * c;")
                .contains("return a + b * c;")
                .contains("return a - (b - c);")
                .contains("return -(-a);")
                .contains("return (int) (a + b);");
    }

    @Test
    @DisplayName("a reference cast of a signed operand keeps the cast a cast (JLS 15.16)")
    void parenthesizes_a_signed_cast_operand() {
        JType.Declared object = JTypes.OBJECT;
        JDecl.ClassDecl type = sealedClass("Casts").withMembers(List.of(
                staticMethod("boxedNegative", object, new JExpr.Cast(object,
                        new JExpr.Unary(JExpr.UnaryOp.NEGATE,
                                new JExpr.IntLiteral("1", JType.Primitive.INT),
                                JType.Primitive.INT))),
                staticMethod("boxedName", object, new JExpr.Cast(object,
                        new JExpr.Name("value", JType.Primitive.INT))),
                staticMethod("primitiveNegative", JType.Primitive.BYTE,
                        new JExpr.Cast(JType.Primitive.BYTE,
                                new JExpr.Unary(JExpr.UnaryOp.NEGATE,
                                        new JExpr.IntLiteral("1", JType.Primitive.INT),
                                        JType.Primitive.INT)))));

        String java = RENDERER.render(unit("demo", type));

        assertThat(java)
                .contains("return (Object) (-1);")
                .contains("return (Object) value;")
                .contains("return (byte) -1;")
                .doesNotContain("(Object) -1;");
    }

    @Test
    @DisplayName("imports are deterministic, minimal, and resolved against collisions")
    void resolves_imports_and_collisions() {
        JType.Declared list = JTypes.list(JTypes.STRING);
        JType.Declared firstItem = JType.Declared.of("first", "Item");
        JType.Declared secondItem = JType.Declared.of("second", "Item");
        JDecl.ClassDecl type = sealedClass("Imports").withMembers(List.of(
                staticMethod("items", list, new JExpr.New(JTypes.arrayList(JTypes.STRING),
                        List.of(), list)),
                staticMethod("big", JTypes.BIG_INTEGER,
                        new JExpr.New(JTypes.BIG_INTEGER,
                                List.of(new JExpr.StringLiteral("1")), JTypes.BIG_INTEGER)),
                staticMethod("first", firstItem, new JExpr.Cast(firstItem,
                        new JExpr.Name("value", JTypes.OBJECT))),
                staticMethod("second", secondItem, new JExpr.Cast(secondItem,
                        new JExpr.Name("value", JTypes.OBJECT)))));

        String java = RENDERER.render(unit("demo", type));

        assertThat(java).contains("""
                package demo;

                import java.math.BigInteger;
                import java.util.ArrayList;
                import java.util.List;

                public final class Imports {""");
        assertThat(java).doesNotContain("import first.Item;", "import second.Item;");
        assertThat(java).contains("return (first.Item) value;", "return (second.Item) value;");
    }

    @Test
    @DisplayName("a type in the same package needs no import and a nested type uses its owner")
    void resolves_same_package_and_nested_types() {
        JType.Declared helper = JType.Declared.of("demo", "Helper");
        JType.Declared error = JTypes.programNested("Error");
        JDecl.ClassDecl type = sealedClass("SamePackage").withMembers(List.of(
                staticMethod("helper", helper, new JExpr.New(helper, List.of(), helper)),
                staticMethod("error", error, new JExpr.Cast(error,
                        new JExpr.Name("failure", JTypes.OBJECT)))));

        String java = RENDERER.render(unit("demo", type));

        assertThat(java).contains("import solvik.Solvik$Program;");
        assertThat(java).doesNotContain("import demo.Helper;");
        assertThat(java).contains("return new Helper();", "return (Solvik$Program.Error) failure;");
    }

    @Test
    @DisplayName("escapes literals and renders statements with conventional formatting")
    void renders_statements_and_escapes_literals() {
        JExpr names = new JExpr.Name("names", JTypes.list(JTypes.STRING));
        JStmt loopBody = new JStmt.Block(List.of(
                new JStmt.VarDecl(JTypes.STRING, "name",
                        false, new JExpr.Name("item", JTypes.STRING), List.of()),
                new JStmt.ExpressionStatement(new JExpr.Call(
                        new JExpr.StaticField(JTypes.SYSTEM, "out", JTypes.PRINT_STREAM),
                        JTypes.PRINT_STREAM, "print", List.of(), List.of(
                                new JExpr.StringLiteral("a\"b\\c\n\t")), JTypes.VOID))));
        JDecl.Method run = new JDecl.Method(Set.of(JDecl.Modifier.PUBLIC, JDecl.Modifier.STATIC),
                List.of(), JTypes.VOID, "run", List.of(new JDecl.Parameter(
                        JTypes.list(JTypes.STRING), "names")),
                List.of(new JStmt.ForEach(null, JTypes.STRING, "item", names, loopBody)), List.of());
        JDecl.ClassDecl type = sealedClass("Statements").withMembers(List.of(run));

        String java = RENDERER.render(unit("demo", type));

        assertThat(java).contains("public static void run(List<String> names) {\n"
                + "        for (String item : names) {\n"
                + "            String name = item;\n"
                + "            System.out.print(\"a\\\"b\\\\c\\n\\t\");\n"
                + "        }\n"
                + "    }");
    }

    @Test
    @DisplayName("renders branches, labeled loops, switches, and try statements")
    void renders_control_flow() {
        JExpr flag = new JExpr.Name("flag", JType.Primitive.BOOLEAN);
        JStmt ifStatement = new JStmt.If(flag,
                new JStmt.Block(List.of(new JStmt.Break("loop"))),
                new JStmt.If(new JExpr.BooleanLiteral(true),
                        new JStmt.Block(List.of(new JStmt.Continue(null))),
                        new JStmt.Block(List.of(new JStmt.Return(null)))));
        JStmt loop = new JStmt.While("loop", flag, new JStmt.Block(List.of(ifStatement)));
        JStmt selection = new JStmt.Switch(flag, List.of(JStmt.SwitchArm.of(
                new JExpr.BooleanLiteral(true), List.of(new JStmt.Return(null)))), null);
        JStmt attempt = new JStmt.Try(new JStmt.Block(List.of(new JStmt.Throw(
                new JExpr.Name("failure", JTypes.RUNTIME_EXCEPTION)))),
                List.of(new JStmt.Catch(JTypes.RUNTIME_EXCEPTION, "problem",
                        new JStmt.Block(List.of(new JStmt.Return(null))))));
        JDecl.Method run = JDecl.Method.of(Set.of(JDecl.Modifier.PUBLIC, JDecl.Modifier.STATIC),
                JTypes.VOID, "run", List.of(), List.of(loop, selection, attempt));
        JDecl.ClassDecl type = sealedClass("Flow").withMembers(List.of(run));

        String java = RENDERER.render(unit("demo", type));

        assertThat(java).contains("        loop: while (flag) {\n"
                + "            if (flag) {\n"
                + "                break loop;\n"
                + "            } else if (true) {\n"
                + "                continue;\n"
                + "            } else {\n"
                + "                return;\n"
                + "            }\n"
                + "        }");
        assertThat(java).contains("        switch (flag) {\n"
                + "            case true -> {\n"
                + "                return;\n"
                + "            }\n"
                + "        }");
        assertThat(java).contains("        try {\n"
                + "            throw failure;\n"
                + "        } catch (RuntimeException problem) {\n"
                + "            return;\n"
                + "        }");
    }

    @Test
    @DisplayName("the rendered fixture compiles with javac")
    void rendered_output_compiles(@TempDir Path directory) throws Exception {
        JExpr greeting = new JExpr.StringLiteral("hello");
        JDecl.Method main = new JDecl.Method(Set.of(JDecl.Modifier.PUBLIC, JDecl.Modifier.STATIC),
                List.of(), JTypes.VOID, "main",
                List.of(new JDecl.Parameter(new JType.Array(JTypes.STRING), "args")),
                List.of(new JStmt.ExpressionStatement(new JExpr.Call(
                        new JExpr.StaticField(JTypes.SYSTEM, "out", JTypes.PRINT_STREAM),
                        JTypes.PRINT_STREAM, "print", List.of(),
                        List.of(greeting), JTypes.VOID))), List.of());
        JDecl.ClassDecl type = sealedClass("Compiled").withMembers(List.of(
                JDecl.Constructor.of(Set.of(JDecl.Modifier.PRIVATE), List.of(), List.of()), main));
        String java = RENDERER.render(unit("demo", type));

        Path source = directory.resolve("demo/Compiled.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, java, StandardCharsets.UTF_8);
        int status = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "25", "-Xlint:all", "-Werror", "-d", directory.toString(),
                source.toString());

        assertThat(status).describedAs("%s", java).isZero();
        assertThat(java).isEqualTo(RENDERER.render(unit("demo", type)));
    }
}
