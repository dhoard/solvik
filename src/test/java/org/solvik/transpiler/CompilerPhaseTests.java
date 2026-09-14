package org.solvik.transpiler;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.solvik.transpiler.backend.JavaEmitter;
import org.solvik.transpiler.backend.JavaIr;
import org.solvik.transpiler.backend.JavaLowerer;
import org.solvik.transpiler.backend.JavaProgram;
import org.solvik.transpiler.backend.RuntimeFeature;

import static org.solvik.transpiler.TypeModel.*;
import static org.solvik.transpiler.language.Language.*;

/**
 * Focused tests for individual compiler phases and for the architectural
 * boundaries between them. End-to-end behavior stays in {@code FrontendTests}
 * and {@code ConformanceTest}; this suite asserts that each phase has a single
 * responsibility and that the backend consumes typed IR rather than the AST.
 */
final class CompilerPhaseTests {
    private CompilerPhaseTests() {}

    @Test
    void parserBuildsPrecedenceTree() throws Exception {
        Ast.CompilationUnit unit = parse("precedence.sol", """
                package precedence
                struct Main {
                    pub func run(args: String...): Integer {
                        let value: Integer = 1 + 2 * 3
                        return value
                    }
                }
                """);
        Ast.StructDecl main = (Ast.StructDecl) unit.declarations().get(0);
        Ast.VarDecl declaration = (Ast.VarDecl) main.methods().get(0).body().statements().get(0);
        Ast.BinaryExpr add = (Ast.BinaryExpr) declaration.initializer();
        require(add.op() == BinaryOp.ADD, "top-level operator is not addition");
        require(((Ast.BinaryExpr) add.right()).op() == BinaryOp.MUL, "multiplication did not bind tighter");
    }

    @Test
    void solvikLowererProducesTypedResolvedIr() throws Exception {
        SolvikProgram program = lower("""
                package lowered
                struct Main {
                    pub func run(args: String...): Integer {
                        var total: Long = 1
                        total = total + 2
                        return 0
                    }
                }
                """, "lowered.sol");
        SolvikProgram.Struct main = program.structs().get(0);
        SolvikProgram.Method run = main.methods().get(0);
        SolvikStmt.VarDecl declaration = (SolvikStmt.VarDecl) run.body().get(0);
        require(declaration.type().base() == Base.LONG, "lowered local lost its type");
        SolvikStmt.Expr assignment = (SolvikStmt.Expr) run.body().get(1);
        require(assignment.expression() instanceof SolvikIr.Assign, "assignment was not lowered to IR");
        SolvikIr.Assign assign = (SolvikIr.Assign) assignment.expression();
        require(assign.value() instanceof SolvikIr.Binary, "assigned expression is not a typed binary");
        require(((SolvikIr.Binary) assign.value()).leftType().base() == Base.LONG, "operand type missing");
    }

    @Test
    void irOptimizerFoldsExactConstantsAndPreservesOverflow() throws Exception {
        SolvikProgram program = lower("""
                package folded
                struct Main {
                    pub func run(args: String...): Integer {
                        let value: Long = 2 + 3 * 4
                        return Integer.from(value)
                    }
                }
                """, "folded.sol");
        SolvikStmt.VarDecl declaration = (SolvikStmt.VarDecl) program.structs().get(0).methods().get(0).body().get(0);
        SolvikIr initializer = declaration.initializer() instanceof SolvikIr.Coerce coerce ? coerce.value() : declaration.initializer();
        require(initializer instanceof SolvikIr.Literal, "constant arithmetic was not folded");
        require(((SolvikIr.Literal) initializer).text().equals("14"), "folded value is wrong");

        SolvikProgram overflow = lower("""
                package overflow
                struct Main {
                    pub func run(args: String...): Integer {
                        return Integer.from(9223372036854775807 + 1)
                    }
                }
                """, "overflow.sol");
        SolvikStmt.Return result = (SolvikStmt.Return) overflow.structs().get(0).methods().get(0).body().get(0);
        require(!(result.value() instanceof SolvikIr.Literal), "overflowing addition must not be folded");
    }

    @Test
    void javaLowererChoosesCheckedArithmeticAndStructuredNodes() throws Exception {
        JavaProgram program = javaProgram("""
                package backend
                struct Main {
                    pub func run(args: String...): Integer {
                        return 0
                    }
                }
                """, "backend.sol");
        JavaLowerer lowerer = new JavaLowerer(program);
        Type longType = named(Base.LONG, "Long");
        SolvikIr sum = new SolvikIr.Binary(BinaryOp.ADD,
                new SolvikIr.Local("a", longType), new SolvikIr.Local("b", longType),
                longType, longType, longType);
        JavaIr lowered = lowerer.lower(sum);
        require(lowered instanceof JavaIr.StaticCall, "checked addition is not a structured static call");
        require(JavaIr.render(lowered).equals("Math.addExact(v_a, v_b)"), "checked addition rendered wrongly: " + JavaIr.render(lowered));
    }

    @Test
    void javaEmitterInsertsPrecedenceParentheses() {
        Type integer = named(Base.INTEGER, "Integer");
        JavaIr a = JavaIr.identifier("a", integer);
        JavaIr b = JavaIr.identifier("b", integer);
        JavaIr c = JavaIr.identifier("c", integer);
        require(JavaIr.render(JavaIr.infix("+", a, JavaIr.infix("*", b, c, integer), integer)).equals("a + b * c"),
                "multiplication precedence");
        require(JavaIr.render(JavaIr.infix("*", JavaIr.infix("+", a, b, integer), c, integer)).equals("(a + b) * c"),
                "parenthesized sum under multiplication");
        require(JavaIr.render(JavaIr.infix("-", a, JavaIr.infix("-", b, c, integer), integer)).equals("a - (b - c)"),
                "right-associative subtraction needs parentheses");
        require(JavaIr.render(JavaIr.infix("-", JavaIr.infix("-", a, b, integer), c, integer)).equals("a - b - c"),
                "left-associative subtraction must not add parentheses");
        require(JavaIr.render(JavaIr.infix("/", a, JavaIr.infix("*", b, c, integer), integer)).equals("a / (b * c)"),
                "division grouping");
        require(JavaIr.render(JavaIr.infix("&&", JavaIr.prefix("!", a, integer), b, integer)).equals("!a && b"),
                "negation binds tighter than conjunction");
        require(JavaIr.render(JavaIr.infix("&&", a, JavaIr.infix("||", b, c, integer), integer)).equals("a && (b || c)"),
                "disjunction under conjunction needs parentheses");
    }

    @Test
    void javaEmitterRendersNestedConditionalsStructurally() {
        Type integer = named(Base.INTEGER, "Integer");
        JavaIr a = JavaIr.identifier("a", integer);
        JavaIr b = JavaIr.identifier("b", integer);
        JavaIr c = JavaIr.identifier("c", integer);
        JavaIr conditional = JavaIr.ternary(a, b, JavaIr.ternary(b, a, c, integer), integer);
        require(JavaIr.render(conditional).equals("a ? b : b ? a : c"), "nested conditional rendering: " + JavaIr.render(conditional));
    }

    @Test
    void runtimeFeatureReachabilityIsStructural() throws Exception {
        JavaProgram simple = javaProgram("""
                package tiny
                struct Main {
                    pub func run(args: String...): Integer {
                        let value: Long = 1 + 2
                        return Integer.from(value)
                    }
                }
                """, "tiny.sol");
        emit(simple);
        require(simple.features().contains(RuntimeFeature.LIST), "entry-point argument list runtime is always required");
        for (RuntimeFeature unused : List.of(RuntimeFeature.REGEX, RuntimeFeature.THREAD, RuntimeFeature.JSON,
                RuntimeFeature.FILE, RuntimeFeature.PROCESS, RuntimeFeature.MAP)) {
            require(!simple.features().contains(unused), "unused feature reachable: " + unused);
        }

        JavaProgram regex = javaProgram("""
                package matches
                struct Main {
                    pub func run(args: String...): Integer {
                        let pattern: Regex = Regex.new("[0-9]+")
                        if pattern.matches("a1") {
                            return 1
                        }
                        return 0
                    }
                }
                """, "regex.sol");
        emit(regex);
        require(regex.features().contains(RuntimeFeature.REGEX), "regex use did not record REGEX");
    }

    @Test
    void runtimeFeatureDependenciesAreExplicit() throws Exception {
        JavaProgram program = javaProgram("""
                package deps
                struct Main {
                    pub func run(args: String...): Integer {
                        return 0
                    }
                }
                """, "deps.sol");
        program.require(RuntimeFeature.JSON);
        require(program.features().contains(RuntimeFeature.LIST) && program.features().contains(RuntimeFeature.MAP),
                "JSON must pull in the collections it serializes");
        program.require(RuntimeFeature.CONVERSIONS);
        require(program.features().contains(RuntimeFeature.STRING_ACCESS), "conversions must pull in string access");
    }

    @Test
    void compilationIsDeterministic() throws Exception {
        String source = """
                package deterministic
                struct Point {
                    x: Long
                    pub func new(x: Long): Self { return Self { x: x, } }
                    pub func get(self): Long { return self.x }
                }
                struct Main {
                    pub func run(args: String...): Integer {
                        let p: Point = Point.new(3)
                        let values: List<Long> = [1, 2, 3]
                        return Integer.from(p.get() + values.get(0))
                    }
                }
                """;
        String first = Transpiler.toJava("deterministic.sol", source, "Generated");
        String second = Transpiler.toJava("deterministic.sol", source, "Generated");
        require(first.equals(second), "generated Java is not byte-for-byte deterministic");
    }

    @Test
    void diagnosticsCarryStableCodesAndLocations() {
        try {
            Transpiler.toJava("diagnostic.sol", """
                    package diagnostic
                    struct Main {
                        pub func run(args: String...): Integer {
                            let value: Integer = "text"
                            return value
                        }
                    }
                    """, "Generated");
            throw new AssertionError("expected a source diagnostic");
        } catch (CompileException e) {
            require(!e.diagnostics().isEmpty(), "compilation failure carried no diagnostics");
            Diagnostic diagnostic = e.diagnostics().get(0);
            require(diagnostic.code().startsWith("C"), "diagnostic code is not stable: " + diagnostic.code());
            require(diagnostic.span().line() == 4, "diagnostic line is wrong: " + diagnostic.span().line());
            require(diagnostic.span().column() > 0, "diagnostic column is missing");
            require(diagnostic.toString().contains("diagnostic.sol:4"), "rendered diagnostic lacks its location");
        }
    }

    @Test
    void phasesAreReportedIndividually() throws Exception {
        List<String> phases = new java.util.ArrayList<>();
        Transpiler.toJava("phases.sol", """
                package phases
                struct Main {
                    pub func run(args: String...): Integer {
                        return 0
                    }
                }
                """, "Generated", (name, nanos) -> phases.add(name));
        for (String expected : List.of("lexer", "parser", "semantic", "solvik lowering", "ir optimization", "java lowering", "java emission", "total")) {
            require(phases.contains(expected), "missing compiler phase timing: " + expected);
        }
    }

    private static JavaProgram javaProgram(String source, String file) throws Exception {
        SemanticAnalyzer.Model model = new SemanticAnalyzer().analyze(parse(file, source));
        SolvikProgram program = new IrOptimizer().optimize(new SolvikLowerer(model).lower());
        return new JavaProgram(program, "Generated", file);
    }

    private static String emit(JavaProgram program) {
        return new JavaEmitter(program).emit();
    }

    private static SolvikProgram lower(String source, String file) throws Exception {
        return new IrOptimizer().optimize(new SolvikLowerer(new SemanticAnalyzer().analyze(parse(file, source))).lower());
    }

    private static Ast.CompilationUnit parse(String file, String source) throws Exception {
        Lexer lexer = new Lexer(file, source);
        List<Token> tokens = lexer.tokenize();
        require(lexer.diagnostics().isEmpty(), "lexer diagnostics: " + lexer.diagnostics());
        Parser parser = new Parser(tokens);
        Ast.CompilationUnit unit = parser.parse(file);
        require(parser.diagnostics().isEmpty(), "parser diagnostics: " + parser.diagnostics());
        return unit;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
