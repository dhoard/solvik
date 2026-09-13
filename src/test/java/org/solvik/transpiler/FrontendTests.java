package org.solvik.transpiler;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.solvik.transpiler.Ast.*;
import static org.solvik.transpiler.TypeModel.*;

/** Focused frontend unit tests for the lexer, parser, analyzer, IR, and emitter. */
public final class FrontendTests {
    private FrontendTests() {}

    @Test
    void lexerRetainsNewlinesAndHandlesNestedComments() {
        Lexer lexer = new Lexer("lexer.sol", "package p\n/* outer /* nested */ outer */\nstruct Main {}\nr#\"raw\\ntext\"#\n");
        List<Token> tokens = lexer.tokenize();
        require(lexer.diagnostics().isEmpty(), "lexer diagnostics: " + lexer.diagnostics());
        require(tokens.stream().anyMatch(t -> t.kind() == TokenKind.NEWLINE), "newline token was discarded");
        require(tokens.stream().anyMatch(t -> t.kind() == TokenKind.STRUCT), "struct token missing");
        require(tokens.stream().anyMatch(t -> t.kind() == TokenKind.STRING && t.text().equals("raw\\ntext")), "raw string token missing");
    }

    @Test
    void parserBuildsPrecedenceTreeAndSpans() throws Exception {
        CompilationUnit unit = parse("parser.sol", """
                package parser
                struct Main {
                    public func run(args: String...): Long {
                        let value: Integer = 1 + 2 * 3
                        return value
                    }
                }
                """);
        StructDecl main = (StructDecl) unit.declarations().get(0);
        VarDecl value = (VarDecl) main.methods().get(0).body().statements().get(0);
        require(value.initializer() instanceof BinaryExpr, "arithmetic expression was not parsed");
        BinaryExpr add = (BinaryExpr) value.initializer();
        require(add.op() == BinaryOp.ADD && add.right() instanceof BinaryExpr,
                "operator precedence tree is incorrect");
        require(((BinaryExpr) add.right()).op() == BinaryOp.MUL, "multiplication did not bind tighter");
        require(add.span().line() == 4 && add.span().start() < add.span().end(), "expression span is incorrect");
    }

    @Test
    void numericPromotionMatchesJava17() {
        Type b = named(Base.BYTE, "Byte");
        Type s = named(Base.SHORT, "Short");
        Type i = named(Base.INTEGER, "Integer");
        Type l = named(Base.LONG, "Long");
        Type f = named(Base.FLOAT, "Float");
        Type d = named(Base.DOUBLE, "Double");
        requireBase(binaryPromotion(b, b), Base.INTEGER);
        requireBase(binaryPromotion(s, s), Base.INTEGER);
        requireBase(binaryPromotion(b, s), Base.INTEGER);
        requireBase(binaryPromotion(b, i), Base.INTEGER);
        requireBase(binaryPromotion(s, i), Base.INTEGER);
        requireBase(binaryPromotion(i, l), Base.LONG);
        requireBase(binaryPromotion(l, f), Base.FLOAT);
        requireBase(binaryPromotion(f, d), Base.DOUBLE);
        requireBase(binaryPromotion(l, d), Base.DOUBLE);
        requireBase(unaryNumeric(b), Base.INTEGER);
        requireBase(unaryNumeric(s), Base.INTEGER);
    }

    @Test
    void semanticChecksCoverNarrowingAndNullablePatterns() throws Exception {
        String valid = """
                package semantic
                struct Main {
                    public func run(args: String...): Long {
                        let b: Byte = 127
                        let s: Short = 32767
                        return b + s
                    }
                }
                """;
        SemanticAnalyzer.Model model = analyze(valid, "semantic.sol");
        StructDecl main = (StructDecl) model.unit.declarations().get(0);
        ReturnStmt result = (ReturnStmt) main.methods().get(0).body().statements().get(2);
        requireBase(model.type(result.value()), Base.INTEGER);

        expectCompileError("C188", """
                package narrowing
                struct Main {
                    public func run(args: String...): Long {
                        let i: Integer = 100
                        let b: Byte = i
                        return 0
                    }
                }
                """, "narrowing.sol");
        expectCompileError("C219", """
                package nullable
                enum E {
                    value
                }
                struct Main {
                    public func run(args: String...): Long {
                        let value: E? = null
                        return match value {
                            E.value => 1
                            _ => 0
                        }
                    }
                }
                """, "nullable.sol");
    }

    @Test
    void emitterProducesOnePackageFreeThreadSafeSource() throws Exception {
        SemanticAnalyzer.Model model = analyze("""
                package emitted
                struct Main {
                    public func run(args: String...): Long {
                        return 0
                    }
                }
                """, "emitted.sol");
        String source = new JavaEmitter(model, "Generated", "emitted.sol").emit();
        require(source.contains("public final class Generated"), "wrapper declaration missing");
        require(!source.contains("\npackage "), "generated source has a package declaration");
        require(source.contains("synchronized"), "collection runtime is not synchronized");
    }

    @Test
    void emitterUsesDirectJavaForCommonConstructs() throws Exception {
        SemanticAnalyzer.Model model = analyze("""
                package codegen
                struct Holder {
                    value: Long
                }
                struct Main {
                    public func run(args: String...): Long {
                        let mutable total: Long = 0
                        total += 1
                        let maybe: Long? = null
                        let text: String = "ab"
                        let mutable chars: Long = 0
                        let mutable tiny: Short = 0
                        tiny += 1
                        for ch in text {
                            chars += 1
                        }
                        if total != 0 && maybe == null {
                            return total
                        }
                        let big: BigInteger = BigInteger.from(7)
                        System.getOut().println(big)
                        return chars
                    }
                }
                """, "codegen.sol");
        String source = new JavaEmitter(model, "Generated", "codegen.sol").emit();
        require(source.contains("RT.addLong(v_total, 1L)"), "compound assignment is not checked: " + source);
        require(source.contains("(v_maybe == null)"), "null comparison was not lowered directly: " + source);
        require(!source.contains("RT.eq(v_maybe, null)"), "null comparison still routed through RT.eq");
        require(source.contains("for (String "), "string iteration is not typed as String: " + source);
        require(source.contains("private final long f_value;"), "immutable field is not final: " + source);
        require(source.contains("RT.convertShort(RT.addInt(v_tiny, 1))"), "narrow compound assignment is not checked: " + source);
        require(source.contains("BigInteger.valueOf(7L)"), "constant BigInteger.from does not build directly: " + source);
    }

    @Test
    void irOptimizerFoldsConstantsAndPreservesOverflow() throws Exception {
        String folded = emit("""
                package fold
                struct Main {
                    public func run(args: String...): Long {
                        let value: Long = 2 + 3 * 4
                        return value
                    }
                }
                """, "fold.sol");
        require(!folded.contains("RT.addLong") && !folded.contains("RT.mulLong"),
                "integer constants were not folded: " + folded);
        require(folded.contains("14"), "folded constant is missing: " + folded);

        String overflow = emit("""
                package overflow
                struct Main {
                    public func run(args: String...): Long {
                        return 9223372036854775807 + 1
                    }
                }
                """, "overflow.sol");
        require(overflow.contains("RT.addLong"), "overflowing addition must stay checked: " + overflow);

        String remOverflow = emit("""
                package rem
                struct Main {
                    public func run(args: String...): Long {
                        return (-9223372036854775807 - 1) % -1
                    }
                }
                """, "rem.sol");
        require(remOverflow.contains("RT.remLong"), "MIN_VALUE % -1 must stay checked: " + remOverflow);

        String not = emit("""
                package logic
                struct Main {
                    public func run(args: String...): Long {
                        if !true {
                            return 1
                        }
                        return 0
                    }
                }
                """, "logic.sol");
        require(not.contains("return 0L") && !not.contains("!true"),
                "constant boolean negation was not folded: " + not);
    }

    @Test
    void emitterHoistsMatchAndSpecializesConcatenation() throws Exception {
        String match = emit("""
                package matcher
                enum Shape {
                    circle(Double)
                    square(Double)
                }
                struct Area {
                    public func area(s: Shape): Double {
                        return match s {
                            Shape.circle(r) => r * r
                            Shape.square(a) => a * a
                        }
                    }
                }
                struct Main {
                    public func run(args: String...): Long {
                        return 0
                    }
                }
                """, "match.sol");
        require(!match.contains("Supplier"), "value match still uses a Supplier lambda: " + match);
        require(match.contains("Object __match"), "hoisted match subject was not stored: " + match);

        String concat = emit("""
                package concat
                struct Main {
                    public func run(args: String...): Long {
                        let text: String = "n="
                        let n: Long = 7
                        let line: String = text .. n
                        return line.length()
                    }
                }
                """, "concat.sol");
        require(concat.contains("v_text + v_n"), "string/number concatenation is not direct: " + concat);
        require(!concat.contains("RT.cat(v_text, v_n)"), "string/number concatenation still boxes through RT.cat");
    }

    @Test
    void emittedRuntimeCachesRegexPatterns() throws Exception {
        String source = emit("""
                package regex
                struct Main {
                    public func run(args: String...): Long {
                        let re: Regex = Regex.new("[0-9]+")
                        if re.matches("abc123") {
                            return 1
                        }
                        return 0
                    }
                }
                """, "regex.sol");
        require(source.contains("REGEXES"), "regex runtime does not cache compiled patterns");
        require(source.contains("static Pattern regex(String s)"), "regex cache helper is missing");
    }

    @Test
    void irOptimizerFoldsConstantComparisons() throws Exception {
        String folded = emit("""
                package cmpfold
                struct Main {
                    public func run(args: String...): Long {
                        let a: Boolean = 1 < 2
                        let b: Boolean = 3 >= 3
                        let c: Boolean = "same" == "same"
                        let d: Boolean = "a" != "b"
                        System.getOut().println(a)
                        System.getOut().println(b)
                        System.getOut().println(c)
                        System.getOut().println(d)
                        return 0
                    }
                }
                """, "cmpfold.sol");
        require(!folded.contains("RT.compare"), "constant comparisons were not folded: " + folded);
        require(folded.contains("boolean v_a = true;") && folded.contains("boolean v_d = true;"),
                "folded comparison literals are missing: " + folded);

        String unfolded = emit("""
                package cmpkeep
                struct Main {
                    public func run(args: String...): Long {
                        let n: Long = 1
                        if n < 2 {
                            return 1
                        }
                        return 0
                    }
                }
                """, "cmpkeep.sol");
        require(unfolded.contains("v_n < 2"), "non-constant comparison was folded away: " + unfolded);
    }

    @Test
    void emitterLowersIntegerSwitchToJavaSwitch() throws Exception {
        String integerSwitch = emit("""
                package swint
                struct Main {
                    public func run(args: String...): Long {
                        let x: Integer = 2
                        switch x {
                            case 1: {
                                return 1
                            }
                            case 2, 3: {
                                System.getOut().println("hit")
                            }
                            default: {
                                return 9
                            }
                        }
                        return 0
                    }
                }
                """, "swint.sol");
        require(integerSwitch.contains("switch (__switch"), "Integer switch did not lower to a Java switch: " + integerSwitch);
        require(integerSwitch.contains("case 2:"), "case label missing: " + integerSwitch);
        require(integerSwitch.contains("break;"), "non-diverging case body is missing its break: " + integerSwitch);

        String longSwitch = emit("""
                package swlong
                struct Main {
                    public func run(args: String...): Long {
                        let x: Long = 2
                        switch x {
                            case 2: {
                                return 1
                            }
                        }
                        return 0
                    }
                }
                """, "swlong.sol");
        require(!longSwitch.contains("switch (__switch"), "Long switch must stay an if/else chain for javac compatibility: " + longSwitch);

        String duplicateSwitch = emit("""
                package swdup
                struct Main {
                    public func run(args: String...): Long {
                        let x: Integer = 2
                        switch x {
                            case 1: {
                                return 1
                            }
                            case 1: {
                                return 2
                            }
                        }
                        return 0
                    }
                }
                """, "swdup.sol");
        require(!duplicateSwitch.contains("switch (__switch"), "duplicate case values must stay an if/else chain: " + duplicateSwitch);

        String loopEscapeSwitch = emit("""
                package swbreak
                struct Main {
                    public func run(args: String...): Long {
                        let mutable i: Long = 0
                        while i < 10 {
                            switch i {
                                case 5: {
                                    break
                                }
                                default: {
                                    i += 1
                                }
                            }
                        }
                        return i
                    }
                }
                """, "swbreak.sol");
        require(!loopEscapeSwitch.contains("switch (__switch"), "switch with a loop break must stay an if/else chain: " + loopEscapeSwitch);
    }

    @Test
    void emittedRuntimeUsesDirectStringAccessors() throws Exception {
        String source = emit("""
                package stracc
                struct Main {
                    public func run(args: String...): Long {
                        let s: String = "héllo"
                        System.getOut().println(s.charAt(1))
                        System.getOut().println(s.substring(0, 2))
                        for c in s {
                            System.getOut().println(c)
                        }
                        return 0
                    }
                }
                """, "stracc.sol");
        require(source.contains("s.codePointAt(x)"), "charAt does not use direct code point access: " + source);
        require(source.contains("offsetByCodePoints"), "substring does not use offsetByCodePoints: " + source);
        require(!source.contains("codePoints().toArray()"), "string accessors still materialize a code point array: " + source);
        require(source.contains("static Iterable<String> codePoints(String s) { return () ->"),
                "string iteration is not lazy: " + source);
    }

    @Test
    void emittedListSortUsesNaturalOrdering() throws Exception {
        String source = emit("""
                package sortsem
                struct Main {
                    public func run(args: String...): Long {
                        let values: List<Long> = [100, 9]
                        values.sort()
                        return values.get(0)
                    }
                }
                """, "sortsem.sol");
        require(source.contains("data.sort(RT::compareValues)"), "list sort does not use the natural-ordering comparator: " + source);
        require(source.contains("list contains incomparable elements"), "incomparable sort error is missing: " + source);
        require(!source.contains("String.valueOf(a).compareTo"), "list sort still compares formatted strings: " + source);
    }

    @Test
    void definiteAssignmentHandlesManyLocals() throws Exception {
        StringBuilder source = new StringBuilder("package manylocals\nstruct Main {\n    public func run(args: String...): Long {\n");
        int locals = 300;
        for (int i = 0; i < locals; i++) source.append("        let mutable v").append(i).append(": Long = ").append(i).append('\n');
        for (int i = 0; i < locals; i++) {
            source.append("        if v").append(i).append(" > 0 {\n            v").append(i).append(" = v").append(i).append(" + 1\n        }\n");
        }
        source.append("        return 0\n    }\n}\n");
        analyze(source.toString(), "manylocals.sol");

        expectCompileError("C239", """
                package uninit
                struct Main {
                    public func run(args: String...): Long {
                        let mutable v: Long
                        while false {
                            v = 1
                        }
                        return v
                    }
                }
                """, "uninit.sol");
    }

    private static String emit(String source, String file) throws Exception {
        SemanticAnalyzer.Model model = analyze(source, file);
        return new JavaEmitter(model, "Generated", file).emit();
    }

    @Test
    void javaIrCarriesTypesAndRendersDeterministically() {
        Type integer = named(Base.INTEGER, "Integer");
        JavaIr sum = JavaIr.call("RT.addInt", List.of(JavaIr.atom("a", integer), JavaIr.atom("b", integer)), integer);
        require(JavaIr.render(sum).equals("RT.addInt(a, b)"), "helper call rendering: " + JavaIr.render(sum));
        require(sum.type().base() == Base.INTEGER, "IR node does not carry its type");
        JavaIr comparison = JavaIr.paren(JavaIr.infix("<",
                JavaIr.method(JavaIr.atom("a", integer), "compareTo", List.of(JavaIr.atom("b", integer)), integer),
                JavaIr.atom("0", integer), integer), integer);
        require(JavaIr.render(comparison).equals("(a.compareTo(b) < 0)"), "infix rendering: " + JavaIr.render(comparison));
        JavaIr cast = JavaIr.cast("long", JavaIr.atom("x", integer), named(Base.LONG, "Long"));
        require(JavaIr.render(cast).equals("(long)(x)"), "cast rendering: " + JavaIr.render(cast));
        JavaIr nullCheck = JavaIr.paren(JavaIr.infix("!=", JavaIr.atom("v", integer), JavaIr.atom("null", integer), integer), integer);
        require(JavaIr.render(nullCheck).equals("(v != null)"), "null-check rendering: " + JavaIr.render(nullCheck));
        require(JavaIr.render(sum).equals(JavaIr.render(sum)), "IR rendering is not deterministic");
    }

    @Test
    void solvikIrIsBackendNeutralAndTyped() {
        Type integer = named(Base.INTEGER, "Integer");
        SolvikIr left = new SolvikIr.Local("a", integer);
        SolvikIr right = new SolvikIr.Local("b", integer);
        SolvikIr sum = new SolvikIr.Binary(BinaryOp.ADD, left, right, integer, integer, integer);
        require(sum.type().base() == Base.INTEGER, "binary IR node lost its type");
        require(((SolvikIr.Binary) sum).leftType().base() == Base.INTEGER, "binary IR node lost operand types");
        SolvikIr field = new SolvikIr.Field("Holder", "value", true, integer);
        require(((SolvikIr.Field) field).owner().equals("Holder") && ((SolvikIr.Field) field).isStatic(), "field IR lost resolution");
        SolvikIr self = new SolvikIr.Self(named(Base.STRUCT, "Holder"));
        require(self.type().base() == Base.STRUCT, "self IR lost its type");
    }

    @Test
    void solvikProgramAndStatementsCarryResolvedTypes() {
        Type integer = named(Base.INTEGER, "Integer");
        Type longType = named(Base.LONG, "Long");
        SolvikProgram.Parameter parameter = new SolvikProgram.Parameter("n", longType, false, false);
        SolvikStmt body = new SolvikStmt.Return(new SolvikIr.Local("n", longType));
        SolvikProgram.Method method = new SolvikProgram.Method("run", java.util.List.of(parameter), longType, false, true, false, java.util.List.of(), java.util.List.of(body));
        SolvikProgram.Field field = new SolvikProgram.Field("count", integer, true, false, new SolvikIr.Literal(LiteralKind.INT, "0", integer));
        SolvikProgram.Struct struct = new SolvikProgram.Struct("Main", java.util.List.of(), java.util.List.of(field), java.util.List.of(method), null, java.util.List.of(), 0);
        SolvikProgram program = new SolvikProgram(java.util.List.of(), java.util.List.of(), java.util.List.of(struct));
        require(program.structs().size() == 1, "program lost its struct");
        require(struct.index() == 0, "struct index missing");
        require(method.params().get(0).type().base() == Base.LONG, "parameter type missing");
        require(field.initializer().type().base() == Base.INTEGER, "field initializer type missing");
        require(body instanceof SolvikStmt.Return r && r.value().type().base() == Base.LONG, "statement type missing");
    }

    private static CompilationUnit parse(String file, String source) throws Exception {
        Lexer lexer = new Lexer(file, source);
        List<Token> tokens = lexer.tokenize();
        require(lexer.diagnostics().isEmpty(), "lexer diagnostics: " + lexer.diagnostics());
        Parser parser = new Parser(tokens);
        CompilationUnit unit = parser.parse(file);
        require(parser.diagnostics().isEmpty(), "parser diagnostics: " + parser.diagnostics());
        return unit;
    }

    private static SemanticAnalyzer.Model analyze(String source, String file) throws Exception {
        return new SemanticAnalyzer().analyze(parse(file, source));
    }

    private static void expectCompileError(String code, String source, String file) throws Exception {
        try {
            analyze(source, file);
            throw new AssertionError("expected diagnostic " + code);
        } catch (CompileException e) {
            require(e.diagnostics().stream().anyMatch(d -> d.code().equals(code)),
                    "expected " + code + ", got " + e.diagnostics());
        }
    }

    private static void requireBase(Type type, Base expected) {
        require(type != null && type.base() == expected, "expected " + expected + ", got " + type);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
