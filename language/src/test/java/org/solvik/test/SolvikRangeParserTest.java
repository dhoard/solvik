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
import static org.solvik.test.SolvikTestSupport.assertNode;
import static org.solvik.test.SolvikTestSupport.body;
import static org.solvik.test.SolvikTestSupport.onlyFunction;
import static org.solvik.test.SolvikTestSupport.parseOk;

import org.junit.jupiter.api.Test;
import org.solvik.ast.AstKind;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.statement.ForInStmtNode;
import org.solvik.ast.statement.RangeOperator;

/**
 * Parser tests for the range {@code for}-in loop (docs/LANGUAGE_SPEC.md section 17): the three range
 * operators, the implicit loop variable, and nested range loops all produce the expected syntax-AST
 * shape with precise spans.
 */
public final class SolvikRangeParserTest {

    @Test
    public void inclusiveRangeShapeAndSpan() {
        String src = "func f(): Unit {\n    for (i in 1...5) {\n    }\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("range.sol", src));
        ForInStmtNode loop = (ForInStmtNode) body(fn).statements().get(0);
        assertNode(loop, AstKind.FOR_IN_STMT, src, "for (i in 1...5) {\n    }");
        assertThat(loop.variableName()).isEqualTo("i");
        assertThat(loop.operator()).isEqualTo(RangeOperator.INCLUSIVE);
        assertThat(loop.start().kind()).isEqualTo(AstKind.INTEGER_LITERAL);
        assertThat(loop.end().kind()).isEqualTo(AstKind.INTEGER_LITERAL);
    }

    @Test
    public void ascendingExclusiveRangeShape() {
        String src = "func f(): Unit {\n    for (n in 0 ..< 10) {\n    }\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("range.sol", src));
        ForInStmtNode loop = (ForInStmtNode) body(fn).statements().get(0);
        assertThat(loop.variableName()).isEqualTo("n");
        assertThat(loop.operator()).isEqualTo(RangeOperator.EXCLUSIVE_ASCENDING);
    }

    @Test
    public void descendingExclusiveRangeShape() {
        String src = "func f(): Unit {\n    for (n in 10..>0) {\n    }\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("range.sol", src));
        ForInStmtNode loop = (ForInStmtNode) body(fn).statements().get(0);
        assertThat(loop.operator()).isEqualTo(RangeOperator.EXCLUSIVE_DESCENDING);
    }

    @Test
    public void rangeBodyIsTheLoopBody() {
        String src = "func f(): Unit {\n    for (i in 1...3) {\n        val doubled = i + i\n    }\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("range.sol", src));
        ForInStmtNode loop = (ForInStmtNode) body(fn).statements().get(0);
        assertThat(loop.body().statements().size()).isEqualTo(1);
        assertNode(loop.body().statements().get(0), AstKind.LOCAL_DECL, src, "val doubled = i + i");
    }

    @Test
    public void nestedRangeLoopsNest() {
        String src = "func f(): Unit {\n    for (i in 1...2) {\n        for (j in 1..<3) {\n        }\n    }\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("range.sol", src));
        ForInStmtNode outer = (ForInStmtNode) body(fn).statements().get(0);
        ForInStmtNode inner = (ForInStmtNode) outer.body().statements().get(0);
        assertThat(inner.variableName()).isEqualTo("j");
        assertThat(inner.operator()).isEqualTo(RangeOperator.EXCLUSIVE_ASCENDING);
    }
}
