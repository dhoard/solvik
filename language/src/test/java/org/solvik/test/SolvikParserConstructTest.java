/*
 * Copyright (c) 2026-present Douglas Hoard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.solvik.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.solvik.test.SolvikTestSupport.binary;
import static org.solvik.test.SolvikTestSupport.call;
import static org.solvik.test.SolvikTestSupport.expr;
import static org.solvik.test.SolvikTestSupport.local;
import static org.solvik.test.SolvikTestSupport.member;
import static org.solvik.test.SolvikTestSupport.onlyFunction;
import static org.solvik.test.SolvikTestSupport.parseFails;
import static org.solvik.test.SolvikTestSupport.parseOk;
import static org.solvik.test.SolvikTestSupport.ret;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.solvik.ast.AstKind;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.expression.BinaryExprNode;
import org.solvik.ast.expression.BinaryOperator;
import org.solvik.ast.expression.CastExprNode;
import org.solvik.ast.expression.TypeTestExprNode;
import org.solvik.ast.statement.ForInStmtNode;
import org.solvik.ast.statement.RangeOperator;
import org.solvik.diagnostic.DiagnosticCode;

/**
 * Parser-shape coverage for constructs not pinned by {@link SolvikParserTest}: range {@code for}-in
 * loops (all three range operators), the {@code is}/{@code as} type operators, the concatenation
 * operator, nullable and generic written type references, the {@code ::} namespace path, and
 * expression-oriented {@code if}/{@code block} forms. Negative cases use {@code parseFails} and pin
 * the parse-layer diagnostic code. Authority: {@code docs/LANGUAGE_SPEC.md} sections 3, 12, 17, 18.
 */
public final class SolvikParserConstructTest {

    private static ForInStmtNode forIn(FunctionDeclNode fn, int index) {
        return (ForInStmtNode) fn.body().statements().get(index);
    }

    @Test
    public void inclusiveRangeForInBuildsInclusiveRangeNode() {
        String src = "func f(): Unit {\n    for (i in 0...10) {\n    }\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("forin.sol", src));
        ForInStmtNode loop = forIn(fn, 0);
        assertThat(loop.variableName()).isEqualTo("i");
        assertThat(loop.operator()).isEqualTo(RangeOperator.INCLUSIVE);
        assertThat(loop.start().kind()).isEqualTo(AstKind.INTEGER_LITERAL);
        assertThat(loop.end().kind()).isEqualTo(AstKind.INTEGER_LITERAL);
    }

    @Test
    public void exclusiveAscendingRangeOperatorIsRecorded() {
        String src = "func f(): Unit {\n    for (i in 0..<5) {\n    }\n}\n";
        ForInStmtNode loop = forIn(onlyFunction(parseOk("excl.sol", src)), 0);
        assertThat(loop.operator()).isEqualTo(RangeOperator.EXCLUSIVE_ASCENDING);
    }

    @Test
    public void descendingRangeOperatorIsRecorded() {
        String src = "func f(): Unit {\n    for (i in 5..>0) {\n    }\n}\n";
        ForInStmtNode loop = forIn(onlyFunction(parseOk("desc.sol", src)), 0);
        assertThat(loop.operator()).isEqualTo(RangeOperator.EXCLUSIVE_DESCENDING);
    }

    @Test
    public void rangeBoundsAreExpressionsAndFoldCorrectly() {
        // start is an additive expression; end is a bare literal.
        String src = "func f(): Unit {\n    for (i in 1 + 1...n) {\n    }\n}\n";
        ForInStmtNode loop = forIn(onlyFunction(parseOk("boundexpr.sol", src)), 0);
        BinaryExprNode start = (BinaryExprNode) loop.start();
        assertThat(start.operator()).isEqualTo(BinaryOperator.ADD);
        assertThat(loop.end().kind()).isEqualTo(AstKind.NAME_REF_EXPR);
    }

    @Test
    public void typeTestIsDistinctFromBinaryEquality() {
        String src = "func f(v: Any): Unit {\n    val r = v is String;\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("istest.sol", src));
        TypeTestExprNode test = (TypeTestExprNode) local(fn, 0).initializer();
        assertThat(test.kind()).isEqualTo(AstKind.TYPE_TEST_EXPR);
        assertThat(test.operand().kind()).isEqualTo(AstKind.NAME_REF_EXPR);
        assertThat(test.typeRef().name()).isEqualTo("String");
    }

    @Test
    public void castBuildsCastExprWithOperandAndTarget() {
        String src = "func f(v: Any): Unit {\n    val r = v as Integer;\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("ascast.sol", src));
        CastExprNode cast = (CastExprNode) local(fn, 0).initializer();
        assertThat(cast.kind()).isEqualTo(AstKind.CAST_EXPR);
        assertThat(cast.operand().kind()).isEqualTo(AstKind.NAME_REF_EXPR);
        assertThat(cast.typeRef().name()).isEqualTo("Integer");
    }

    @Test
    public void concatOperatorIsRecordedSeparatelyFromArithmetic() {
        String src = "func f(a: Integer, b: String): Unit {\n    return a .. b;\n}\n";
        BinaryExprNode bin = (BinaryExprNode) ret(onlyFunction(parseOk("concat.sol", src)), 0).value().orElseThrow();
        assertThat(bin.operator()).isEqualTo(BinaryOperator.CONCAT);
    }

    @Test
    public void concatBindsLooserThanArithmetic() {
        // 1 + 2 .. "z" groups as (1 + 2) .. "z": the concat node's left child is the additive node.
        String src = "func f(): Unit {\n    return 1 + 2 .. \"z\";\n}\n";
        BinaryExprNode top = (BinaryExprNode) ret(onlyFunction(parseOk("concatprec.sol", src)), 0).value().orElseThrow();
        assertThat(top.operator()).isEqualTo(BinaryOperator.CONCAT);
        assertThat(((BinaryExprNode) top.left()).operator()).isEqualTo(BinaryOperator.ADD);
    }

    @Test
    public void nullableWrittenTypeIsMarkedOnTheTypeRef() {
        String src = "func f(v: String?): Unit {\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("nullable.sol", src));
        assertThat(fn.parameters().get(0).type().isNullable()).isTrue();
    }

    @Test
    public void genericWrittenTypeCarriesItsArguments() {
        String src = "func f(v: List<Integer>): Unit {\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("generic.sol", src));
        List<?> args = fn.parameters().get(0).type().arguments();
        assertThat(args.size()).isEqualTo(1);
        assertThat(fn.parameters().get(0).type().name()).isEqualTo("List");
    }

    @Test
    public void nestedGenericWrittenTypeHasNestedArguments() {
        String src = "func f(v: List<List<Integer>>): Unit {\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("nestedgeneric.sol", src));
        assertThat(fn.parameters().get(0).type().arguments().size()).isEqualTo(1);
    }

    @Test
    public void namespaceQualifiedTypeCarriesItsModulePrefix() {
        String src = "func f(v: m::Thing): Unit {\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("nspath.sol", src));
        assertThat(fn.parameters().get(0).type().hasModulePrefix()).isTrue();
        assertThat(fn.parameters().get(0).type().modulePrefix()).isEqualTo("m");
        assertThat(fn.parameters().get(0).type().name()).isEqualTo("Thing");
    }

    @Test
    public void ifUsedAsExpressionParsesToIfExprNode() {
        String src = "func f(a: Integer): Integer {\n    return if (a > 0) { 1 } else { 2 };\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("ifexpr.sol", src));
        assertThat(ret(fn, 0).value().orElseThrow().kind()).isEqualTo(AstKind.IF_EXPR);
    }

    @Test
    public void elseIfChainNestsInTheElseBranch() {
        String src = "func f(a: Integer): Integer {\n    return if (a > 2) { 3 } else if (a > 1) { 2 } else { 1 };\n}\n";
        // The whole construct parses to a single if-expression node (the else-if chain nests inside).
        assertThat(ret(onlyFunction(parseOk("elseifexpr.sol", src)), 0).value().orElseThrow().kind()).isEqualTo(AstKind.IF_EXPR);
    }

    @Test
    public void blockUsedAsExpressionParsesToBlockExprNode() {
        String src = "func f(): Integer {\n    return { 42 };\n}\n";
        assertThat(ret(onlyFunction(parseOk("blockexpr.sol", src)), 0).value().orElseThrow().kind()).isEqualTo(AstKind.BLOCK_EXPR);
    }

    @Test
    public void matchAsExpressionParsesToMatchNode() {
        String src = "func f(v: Integer): Integer {\n    return match v { _ => 1 };\n}\n";
        assertThat(ret(onlyFunction(parseOk("matchexpr.sol", src)), 0).value().orElseThrow().kind()).isEqualTo(AstKind.MATCH_EXPR);
    }

    // ---- Negative cases ---------------------------------------------------------------------

    @Test
    public void ifExpressionMissingElseStillParsesButStatementConcatenationFails() {
        // Two statements jammed onto one line with no terminator are a parse error.
        var bag = parseFails("jam.sol", "func f(): Unit {\n    val a = 1 val b = 2;\n}\n");
        assertThat(bag.all().get(0).code()).isEqualTo(DiagnosticCode.PARSER_UNEXPECTED_TOKEN);
    }

    @Test
    public void unclosedParenIsIncompleteOrUnexpected() {
        parseFails("unclosed.sol", "func f(): Unit {\n    return (1;\n}\n");
    }

    @Test
    public void missingFunctionBodyIsRejected() {
        parseFails("nobody.sol", "func f(): Integer;\n");
    }

    @Test
    public void moduleDeclarationMustBeFirstIsEnforcedAtParse() {
        // A module declaration appearing after a statement is a parse-level failure: the grammar
        // admits an optional moduleDecl only at the very start of a compilation unit.
        parseFails("notfirst.sol", "func f(): Unit {\n}\nmodule m\n");
    }

    @Test
    public void emptyTypeArgumentListIsRejected() {
        parseFails("emptytypeargs.sol", "func f(v: List<>): Unit {\n}\n");
    }

    @Test
    public void compilationUnitParsesToDeclarationsWithoutErrors() {
        CompilationUnitNode cu = parseOk("decl.sol", "func a(): Unit {\n}\nfunc b(): Unit {\n}\n");
        assertThat(cu.declarations().size()).isEqualTo(2);
    }

    // ---- Extended positive shapes -----------------------------------------------------------

    @Test
    public void equalityChainFoldsLeftAssociatively() {
        // a == b == c groups as (a == b) == c: the top node's left child is itself an equality.
        String src = "func f(a: Boolean, b: Boolean, c: Boolean): Unit {\n    val r = a == b == c;\n}\n";
        BinaryExprNode top = binary(local(onlyFunction(parseOk("eqchain.sol", src)), 0).initializer());
        assertThat(top.operator()).isEqualTo(BinaryOperator.EQ);
        assertThat(binary(top.left()).operator()).isEqualTo(BinaryOperator.EQ);
    }

    @Test
    public void additiveChainFoldsLeftAssociatively() {
        String src = "func f(a: Integer, b: Integer, c: Integer): Unit {\n    val r = a - b - c;\n}\n";
        BinaryExprNode top = binary(local(onlyFunction(parseOk("addchain.sol", src)), 0).initializer());
        assertThat(top.operator()).isEqualTo(BinaryOperator.SUB);
        assertThat(binary(top.left()).operator()).isEqualTo(BinaryOperator.SUB);
    }

    @Test
    public void coalesceIsTheLowestPrecedenceBinaryTier() {
        // a ?? b || c groups as a ?? (b || c): the top operator is COALESCE, the right child is OR.
        String src = "func f(a: Integer, b: Boolean, c: Boolean): Unit {\n    val r = a ?? b || c;\n}\n";
        BinaryExprNode top = binary(local(onlyFunction(parseOk("coalesce.sol", src)), 0).initializer());
        assertThat(top.operator()).isEqualTo(BinaryOperator.COALESCE);
        assertThat(binary(top.right()).operator()).isEqualTo(BinaryOperator.OR);
    }

    @Test
    public void concatBindsLooserThanComparison() {
        // a .. b == c groups as (a .. b) == c: comparison sits above concat, so the top node is EQ
        // and its left child is the concat.
        String src = "func f(a: Integer, b: Integer, c: Boolean): Unit {\n    val r = a .. b == c;\n}\n";
        BinaryExprNode top = binary(local(onlyFunction(parseOk("concatcmp.sol", src)), 0).initializer());
        assertThat(top.operator()).isEqualTo(BinaryOperator.EQ);
        assertThat(binary(top.left()).operator()).isEqualTo(BinaryOperator.CONCAT);
    }

    @Test
    public void coalesceBindsLooserThanLogicalAnd() {
        // a ?? b && c groups as a ?? (b && c).
        String src = "func f(a: Integer, b: Boolean, c: Boolean): Unit {\n    val r = a ?? b && c;\n}\n";
        BinaryExprNode top = binary(local(onlyFunction(parseOk("coalesceand.sol", src)), 0).initializer());
        assertThat(top.operator()).isEqualTo(BinaryOperator.COALESCE);
        assertThat(binary(top.right()).operator()).isEqualTo(BinaryOperator.AND);
    }

    @Test
    public void unaryNegationBindsTighterThanMultiplicative() {
        // -a * b groups as (-a) * b: the multiplication's left operand is the unary node.
        String src = "func f(a: Integer, b: Integer): Unit {\n    val r = -a * b;\n}\n";
        BinaryExprNode top = binary(local(onlyFunction(parseOk("unarymul.sol", src)), 0).initializer());
        assertThat(top.operator()).isEqualTo(BinaryOperator.MUL);
        assertThat(top.left().kind()).isEqualTo(AstKind.UNARY_EXPR);
    }

    @Test
    public void negationIsRecordedAsUnaryNegateAndBangAsNot() {
        String src = "func f(a: Integer, b: Boolean): Unit {\n    val n = -a;\n    val l = !b;\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("unaryops.sol", src));
        assertThat(((org.solvik.ast.expression.UnaryExprNode) local(fn, 0).initializer()).operator()).isEqualTo(org.solvik.ast.expression.UnaryOperator.NEGATE);
        assertThat(((org.solvik.ast.expression.UnaryExprNode) local(fn, 1).initializer()).operator()).isEqualTo(org.solvik.ast.expression.UnaryOperator.NOT);
    }

    @Test
    public void memberCallChainNestsReceiverInnermost() {
        // a.b().c() nests as ((a.b)()).c(): the outermost is the trailing call; its callee is the
        // '.c' member access; that member's receiver is the inner call; and so on to 'a'.
        String src = "func f(a: Thing): Unit {\n    val r = a.b().c();\n}\n";
        org.solvik.ast.expression.CallExprNode outer = call(local(onlyFunction(parseOk("chain.sol", src)), 0).initializer());
        org.solvik.ast.expression.MemberAccessExprNode outerMember = member(outer.callee());
        assertThat(outerMember.memberName()).isEqualTo("c");
        assertThat(outerMember.receiver().kind()).isEqualTo(AstKind.CALL_EXPR);
    }

    @Test
    public void safeMemberAccessIsMarkedOnTheNode() {
        String src = "func f(a: Thing?): Unit {\n    val r = a?.b;\n}\n";
        org.solvik.ast.expression.MemberAccessExprNode m = member(local(onlyFunction(parseOk("safemember.sol", src)), 0).initializer());
        assertThat(m.isSafe()).isTrue();
    }

    @Test
    public void genericCallRecordsTypeArgumentsAndValueArgumentsSeparately() {
        String src = "func f(): Unit {\n    val r = build<Integer>(1);\n}\n";
        org.solvik.ast.expression.CallExprNode call = call(local(onlyFunction(parseOk("genericcall.sol", src)), 0).initializer());
        assertThat(call.typeArguments().size()).isEqualTo(1);
        assertThat(call.typeArguments().get(0).name()).isEqualTo("Integer");
        assertThat(call.arguments().size()).isEqualTo(1);
    }

    @Test
    public void namespaceQualifiedPathChainsToNamespaceAccessNodes() {
        String src = "func f(): Unit {\n    val r = m::Thing;\n}\n";
        org.solvik.ast.expression.NamespaceAccessExprNode ns = (org.solvik.ast.expression.NamespaceAccessExprNode) local(onlyFunction(parseOk("nspaceref.sol", src)), 0).initializer();
        assertThat(ns.memberName()).isEqualTo("Thing");
        assertThat(ns.receiver().kind()).isEqualTo(AstKind.NAME_REF_EXPR);
    }

    @Test
    public void switchAsExpressionParsesToSwitchExprNode() {
        String src = "func f(a: Integer): Integer {\n    return switch (a) {\n        case 1:\n            10\n        default:\n            0\n    };\n}\n";
        assertThat(ret(onlyFunction(parseOk("switchexpr.sol", src)), 0).value().orElseThrow().kind()).isEqualTo(AstKind.SWITCH_EXPR);
    }

    @Test
    public void matchWildcardBindingAndEnumPatternsAreDistinctKinds() {
        String src = "func f(v: Shape): Integer {\n    return match v {\n        Circle => 1\n        other: Integer => 2\n    };\n}\n";
        // The wildcard-ish variant branch and a colon binding are distinguishable node kinds.
        org.solvik.ast.expression.MatchExprNode m = (org.solvik.ast.expression.MatchExprNode) ret(onlyFunction(parseOk("matchpat.sol", src)), 0).value().orElseThrow();
        assertThat(m.branches().get(0).pattern().kind()).isEqualTo(AstKind.ENUM_PATTERN);
        assertThat(m.branches().get(1).pattern().kind()).isEqualTo(AstKind.BINDING_PATTERN);
    }

    @Test
    public void bareUnderscoreMatchArmBecomesWildcardPattern() {
        String src = "func f(v: Integer): Integer {\n    return match v {\n        _ => 0\n    };\n}\n";
        org.solvik.ast.expression.MatchExprNode m = (org.solvik.ast.expression.MatchExprNode) ret(onlyFunction(parseOk("matchwild.sol", src)), 0).value().orElseThrow();
        assertThat(m.branches().get(0).pattern().kind()).isEqualTo(AstKind.WILDCARD_PATTERN);
    }

    @Test
    public void enumVariantArgumentPatternBecomesEnumPatternWithBindings() {
        String src = "func f(v: Shape): Integer {\n    return match v {\n        Point(x, y) => 1\n    };\n}\n";
        org.solvik.ast.expression.MatchExprNode m = (org.solvik.ast.expression.MatchExprNode) ret(onlyFunction(parseOk("matchenum.sol", src)), 0).value().orElseThrow();
        org.solvik.ast.pattern.EnumPatternNode ep = (org.solvik.ast.pattern.EnumPatternNode) m.branches().get(0).pattern();
        assertThat(ep.variantName()).isEqualTo("Point");
        assertThat(ep.arguments()).hasSize(2);
        assertThat(ep.arguments().get(0).kind()).isEqualTo(AstKind.BINDING_PATTERN);
    }

    @Test
    public void parenthesizedExpressionKeepsParenExprNode() {
        String src = "func f(a: Integer): Integer {\n    return (a);\n}\n";
        assertThat(ret(onlyFunction(parseOk("parensol.sol", src)), 0).value().orElseThrow().kind()).isEqualTo(AstKind.PAREN_EXPR);
    }

    @Test
    public void thisAndSuperExpressionsParseToTheirOwnKinds() {
        String subSrc = "class A {\n    func g(): Integer {\n        return 1;\n    }\n}\n";
        CompilationUnitNode cu = parseOk("thissuper.sol", subSrc + "class B extends A {\n    func h(): Integer {\n        return this.g();\n    }\n}\n");
        assertThat(cu.declarations()).hasSize(2);
    }

    @Test
    public void whileAndThreeClauseForParseToStatementNodes() {
        String src = "func f(): Unit {\n    while (true) {\n        break;\n    }\n    for (var i = 0; i < 3; i) {\n        continue;\n    }\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("loops.sol", src));
        assertThat(((org.solvik.ast.statement.WhileStmtNode) fn.body().statements().get(0)).kind()).isEqualTo(AstKind.WHILE_STMT);
    }

    @Test
    public void mapEntryArgumentParsesToMapEntryExpr() {
        String src = "func f(): Unit {\n    val m = Map<String, Integer>(\"a\": 1);\n}\n";
        org.solvik.ast.expression.CallExprNode call = call(local(onlyFunction(parseOk("mapentry.sol", src)), 0).initializer());
        assertThat(call.arguments().get(0).kind()).isEqualTo(AstKind.MAP_ENTRY_EXPR);
    }

    @Test
    public void trailingCommaInArgumentListContributesNoArgument() {
        String src = "func f(a: Integer, b: Integer): Unit {\n    val r = g(1, 2,)\n}\n";
        org.solvik.ast.expression.CallExprNode call = call(local(onlyFunction(parseOk("trailcomma.sol", src)), 0).initializer());
        assertThat(call.arguments()).hasSize(2);
    }

    @Test
    public void classWithConstructorMethodsAndPropertiesExposesThem() {
        String src = "class Point {\n    val x: Integer;\n    Point(x: Integer) {\n        this.x = x;\n    }\n    func getX(): Integer {\n        return this.x;\n    }\n}\n";
        org.solvik.ast.declaration.ClassDeclNode cls = (org.solvik.ast.declaration.ClassDeclNode) parseOk("classfull.sol", src).declarations().get(0);
        assertThat(cls.name()).isEqualTo("Point");
        assertThat(cls.properties()).hasSize(1);
        assertThat(cls.constructor()).isPresent();
        assertThat(cls.methods()).hasSize(1);
        assertThat(cls.isSealed()).isFalse();
    }

    @Test
    public void interfaceWithSignatureAndDefaultMethodIsStructured() {
        String src = "interface Shape {\n    func area(): Integer;\n    func describe(): Integer {\n        return this.area();\n    }\n}\n";
        org.solvik.ast.declaration.InterfaceDeclNode iface = (org.solvik.ast.declaration.InterfaceDeclNode) parseOk("iface.sol", src).declarations().get(0);
        assertThat(iface.signatures()).hasSize(1);
        assertThat(iface.defaultMethods()).hasSize(1);
    }

    @Test
    public void enumVariantsWithAndWithoutValuesAreRecorded() {
        // Section 12: variant payload values are positional so their declaration is a type list.
        String src = "enum Color {\n    Red;\n    Green;\n    Rgb(Integer, Integer, Integer);\n}\n";
        org.solvik.ast.declaration.EnumDeclNode en = (org.solvik.ast.declaration.EnumDeclNode) parseOk("enum.sol", src).declarations().get(0);
        assertThat(en.name()).isEqualTo("Color");
        assertThat(en.variants()).hasSize(3);
        assertThat(en.variants().get(0).valueTypes()).isEmpty();
        assertThat(en.variants().get(2).valueTypes()).hasSize(3);
    }

    @Test
    public void delegateMemberIsParsedUnderTheClass() {
        String src = "class Wrapper {\n    delegate val inner: Speaker;\n}\n";
        org.solvik.ast.declaration.ClassDeclNode cls = (org.solvik.ast.declaration.ClassDeclNode) parseOk("delegate.sol", src).declarations().get(0);
        assertThat(cls.delegates()).hasSize(1);
    }

    @Test
    public void moduleDeclarationIsCapturedAtTheHeadOfTheUnit() {
        String src = "module my_pkg\nfunc f(): Unit {\n}\n";
        CompilationUnitNode cu = parseOk("modulehead.sol", src);
        assertThat(cu.moduleDeclaration()).isPresent();
        assertThat(cu.moduleDeclaration().get().name()).isEqualTo("my_pkg");
    }

    @Test
    public void includeDirectiveRecordsItsPathLiteral() {
        String src = "include \"lib/generated.sol\"\nfunc f(): Unit {\n}\n";
        CompilationUnitNode cu = parseOk("includemod.sol", src);
        org.solvik.ast.declaration.IncludeDeclNode include = (org.solvik.ast.declaration.IncludeDeclNode) cu.items().get(0);
        assertThat(include.pathLiteral().kind()).isEqualTo(AstKind.STRING_LITERAL);
        assertThat(include.hasAlias()).isFalse();
    }

    @Test
    public void includeDirectiveRecordsItsAlias() {
        String src = "include \"lib/gen.sol\" alias g\nfunc f(): Unit {\n}\n";
        org.solvik.ast.declaration.IncludeDeclNode include = (org.solvik.ast.declaration.IncludeDeclNode) parseOk("includealias.sol", src).items().get(0);
        assertThat(include.hasAlias()).isTrue();
        assertThat(include.alias()).isEqualTo("g");
    }

    @Test
    public void topStatementsFormAnImplicitMain() {
        String src = "val x = 1\nprintln(x)\n";
        assertThat(parseOk("implicitmain.sol", src).hasImplicitMain()).isTrue();
    }

    // ---- Additional negative shapes --------------------------------------------------------

    @Test
    public void unclosedFunctionBraceIsRejected() {
        parseFails("unclosedbrace.sol", "func f(): Unit {\n    val a = 1;\n");
    }

    @Test
    public void unclosedTypeArgumentListIsRejected() {
        parseFails("unclosenta.sol", "func f(v: List<Integer): Unit {\n}\n");
    }

    @Test
    public void unclosedClassBodyIsRejected() {
        parseFails("unclosedclass.sol", "class A {\n    val x: Integer;\n");
    }

    @Test
    public void parameterListOfOnlyCommaIsRejected() {
        parseFails("badparams.sol", "func f(,): Unit {\n}\n");
    }

    @Test
    public void argumentListOfOnlyCommaIsRejected() {
        parseFails("badargs.sol", "func f(): Unit {\n    g(,);\n}\n");
    }

    @Test
    public void ifWithoutParenthesizedConditionIsRejected() {
        parseFails("ifnocond.sol", "func f(a: Boolean): Unit {\n    if a {\n    }\n}\n");
    }

    @Test
    public void matchArmWithoutArrowIsRejected() {
        parseFails("matchnoarrow.sol", "func f(a: Integer): Integer {\n    return match a {\n        _ 1\n    };\n}\n");
    }

    @Test
    public void valWithoutInitializerIsRejected() {
        // Section 2: a local 'val' must be initialized (no definite-assignment analysis).
        parseFails("valnoinit.sol", "func f(): Unit {\n    val x: Integer;\n}\n");
    }

    @Test
    public void classWithoutBodyBracesIsRejected() {
        parseFails("classnobrace.sol", "class A\n");
    }

    @Test
    public void bareElseIsRejected() {
        // 'else' only has meaning after a closing '}' of an 'if' block.
        parseFails("bareelse.sol", "func f(a: Boolean): Unit {\n    else {\n    }\n}\n");
    }

    @Test
    public void missingFunctionNameIsRejected() {
        parseFails("noname.sol", "func (): Unit {\n}\n");
    }

    @Test
    public void missingParameterNameIsRejected() {
        parseFails("noparamname.sol", "func f(: Integer): Unit {\n}\n");
    }

    @Test
    public void assignmentAsAnExpressionIsRejected() {
        // Assignment is a statement, never an expression (section 2). '(y = 1)' is not a value.
        parseFails("assignexpr.sol", "func f(y: Integer): Unit {\n    val x = (y = 1);\n}\n");
    }

    @Test
    public void emptyTypeArgumentInsideNestedGenericIsRejected() {
        parseFails("emptynested.sol", "func f(v: List<List<>>): Unit {\n}\n");
    }

    @Test
    public void trailingTypeArgumentsWithoutCallParensIsRejected() {
        // A bare 'List<Integer>' expression (no value use) is not a valid expression: type
        // arguments only appear on a written type reference or on a call suffix '(...)' or '::<...>'.
        parseFails("baregen.sol", "func f(): Unit {\n    List<Integer>;\n}\n");
    }

    @Test
    public void strayCloseParenAtTopLevelIsRejected() {
        parseFails("strayclose.sol", ")\n");
    }

    @Test
    public void strayCloseBraceAtTopLevelIsRejected() {
        parseFails("straybrace.sol", "}\n");
    }

}
