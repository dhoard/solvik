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
import static org.solvik.test.SolvikTestSupport.parseOk;

import org.junit.jupiter.api.Test;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.expression.BinaryExprNode;
import org.solvik.ast.expression.BinaryOperator;
import org.solvik.ast.expression.ExpressionNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.statement.ReturnStmtNode;
import org.solvik.parser.SolvikParseResult;
import org.solvik.parser.SolvikParser;
import org.solvik.source.SourceFile;

/**
 * Parser tests for the four equality operators (docs/LANGUAGE_SPEC.md section 3):
 * {@code ===} and {@code !==} are distinct longest-match tokens, all four share one
 * left-associative precedence tier, and they bind looser than relational operators and tighter than
 * {@code &&}. These tests inspect the AST only; the identity typing rules are covered separately.
 */
public final class SolvikIdentityParserTest {

    private static ExpressionNode returnExpr(String text) {
        CompilationUnitNode unit = parseOk("identityparse.sol", text);
        FunctionDeclNode fn = (FunctionDeclNode) unit.declarations().get(0);
        ReturnStmtNode statement = (ReturnStmtNode) fn.body().statements().get(0);
        return statement.value().orElseThrow();
    }

    private static ExpressionNode expression(String inner) {
        return returnExpr("func f(a: Integer, b: Integer, c: Integer): Boolean {\n    return " + inner + "\n}\n");
    }

    @Test
    public void tripleEqualsIsOneLongestMatchToken() {
        BinaryExprNode binary = (BinaryExprNode) expression("a === b");
        assertThat(binary.operator()).isEqualTo(BinaryOperator.EQEQ);
        assertThat(binary.operator().spelling()).isEqualTo("===");
    }

    @Test
    public void tripleNotEqualsIsOneLongestMatchToken() {
        BinaryExprNode binary = (BinaryExprNode) expression("a !== b");
        assertThat(binary.operator()).isEqualTo(BinaryOperator.NEQEQ);
        assertThat(binary.operator().spelling()).isEqualTo("!==");
    }

    @Test
    public void doubleEqualsOperatorsStillParse() {
        assertThat(((BinaryExprNode) expression("a == b")).operator()).isEqualTo(BinaryOperator.EQ);
        assertThat(((BinaryExprNode) expression("a != b")).operator()).isEqualTo(BinaryOperator.NEQ);
    }

    @Test
    public void allFourOperatorsShareOneLeftAssociativeTier() {
        BinaryExprNode chained = (BinaryExprNode) expression("a == b === c");
        assertThat(chained.operator()).isEqualTo(BinaryOperator.EQEQ);
        BinaryExprNode left = (BinaryExprNode) chained.left();
        assertThat(left.operator()).isEqualTo(BinaryOperator.EQ);
    }

    @Test
    public void identityBindsTighterThanLogicalAnd() {
        BinaryExprNode and = (BinaryExprNode) expression("a === b && a !== c");
        assertThat(and.operator()).isEqualTo(BinaryOperator.AND);
        assertThat(((BinaryExprNode) and.left()).operator()).isEqualTo(BinaryOperator.EQEQ);
        assertThat(((BinaryExprNode) and.right()).operator()).isEqualTo(BinaryOperator.NEQEQ);
    }

    @Test
    public void relationalBindsTighterThanIdentity() {
        BinaryExprNode identity = (BinaryExprNode) expression("a < b === a < c");
        assertThat(identity.operator()).isEqualTo(BinaryOperator.EQEQ);
        assertThat(((BinaryExprNode) identity.left()).operator()).isEqualTo(BinaryOperator.LT);
        assertThat(((BinaryExprNode) identity.right()).operator()).isEqualTo(BinaryOperator.LT);
    }

    @Test
    public void doubleEqualsAndTripleEqualsAreDistinctKinds() {
        assertThat(BinaryOperator.EQ.kind()).isNotEqualTo(BinaryOperator.EQEQ.kind());
        assertThat(BinaryOperator.NEQ.kind()).isNotEqualTo(BinaryOperator.NEQEQ.kind());
    }

    @Test
    public void operatorsAreRecognizedWithoutSurroundingWhitespace() {
        assertThat(((BinaryExprNode) expression("a===b")).operator()).isEqualTo(BinaryOperator.EQEQ);
        assertThat(((BinaryExprNode) expression("a!==b")).operator()).isEqualTo(BinaryOperator.NEQEQ);
    }

    @Test
    public void arithmeticBindsTighterThanIdentity() {
        BinaryExprNode identity = (BinaryExprNode) expression("a + b === a * c");
        assertThat(identity.operator()).isEqualTo(BinaryOperator.EQEQ);
        assertThat(((BinaryExprNode) identity.left()).operator()).isEqualTo(BinaryOperator.ADD);
        assertThat(((BinaryExprNode) identity.right()).operator()).isEqualTo(BinaryOperator.MUL);
    }

    @Test
    public void coalesceBindsLooserThanIdentity() {
        BinaryExprNode coalesce = (BinaryExprNode) returnExpr(
                "func f(a: Integer?, b: Integer, c: Integer): Boolean {\n    return a ?? b === c\n}\n");
        assertThat(coalesce.operator()).isEqualTo(BinaryOperator.COALESCE);
        assertThat(((BinaryExprNode) coalesce.right()).operator()).isEqualTo(BinaryOperator.EQEQ);
    }

    @Test
    public void identityAppearsInConditionsAndBlocks() {
        parseOk("identityparse.sol", """
                func same(a: Integer, b: Integer): Boolean {
                    if (a === b) {
                        return true
                    }
                    return !(a !== b)
                }
                """);
    }

    @Test
    public void malformedIdentitySpellingsAreRejected() {
        assertParseFails("func f(a: Integer, b: Integer): Boolean {\n    return a == = b\n}\n");
        assertParseFails("func f(a: Integer, b: Integer): Boolean {\n    return a ! == b\n}\n");
        assertParseFails("func f(a: Integer, b: Integer): Boolean {\n    return a ==== b\n}\n");
    }

    private static void assertParseFails(String text) {
        SolvikParseResult result = SolvikParser.parse(new SourceFile("identityparse.sol", text));
        assertThat(result.isSuccess()).as("expected a parse error for: " + text).isFalse();
    }
}
