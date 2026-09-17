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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.solvik.test.SolvikTestSupport.body;
import static org.solvik.test.SolvikTestSupport.expr;
import static org.solvik.test.SolvikTestSupport.local;
import static org.solvik.test.SolvikTestSupport.parseOk;
import static org.solvik.test.SolvikTestSupport.ret;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.Test;
import org.solvik.ast.AstNode;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.expression.BinaryExprNode;
import org.solvik.ast.expression.CallExprNode;
import org.solvik.ast.expression.ExpressionNode;
import org.solvik.ast.expression.UnaryExprNode;
import org.solvik.semantic.CheckedProgram;
import org.solvik.semantic.FunctionSymbol;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;
import org.solvik.semantic.VariableSymbol;
import org.solvik.type.BooleanType;
import org.solvik.type.FunctionType;
import org.solvik.type.IntType;
import org.solvik.type.StringType;
import org.solvik.type.UnitType;

/**
 * Positive Phase 4 static-semantics tests: the canonical proof program resolves and type-checks,
 * every value expression receives a type, and the declared built-ins, scopes, operators, loops,
 * calls, and entry point behave per docs/LANGUAGE_SPEC.md.
 */
public final class SolvikSemanticTest {

    private static CheckedProgram check(String text) {
        CompilationUnitNode unit = parseOk("sem.sol", text);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertTrue("analysis must succeed: " + result.diagnostics().all(), result.isSuccess());
        return result.requireProgram();
    }

    private static FunctionDeclNode function(CheckedProgram program, int index) {
        return (FunctionDeclNode) program.unit().declarations().get(index);
    }

    /** The canonical proof program for the statically checked core. */
    @Test
    public void canonicalProofIsResolvedAndStaticallyChecked() {
        CheckedProgram program = check("func add(a: Int, b: Int): Int {\n    return a + b\n}\n");
        FunctionSymbol add = program.function("add").orElseThrow();
        assertEquals("add", add.name());
        assertEquals(IntType.INSTANCE, add.returnType());
        assertEquals(2, add.parameters().size());
        assertEquals(IntType.INSTANCE, add.parameters().get(0).type());
        assertEquals(IntType.INSTANCE, add.parameters().get(1).type());
        assertTrue("no executable entry point is declared", program.entryPoint().isEmpty());

        FunctionDeclNode declaration = function(program, 0);
        BinaryExprNode sum = (BinaryExprNode) ret(declaration, 0).value().orElseThrow();
        assertEquals(IntType.INSTANCE, program.typeOf(sum).orElseThrow());
        assertEquals(IntType.INSTANCE, program.typeOf(sum.left()).orElseThrow());
        assertEquals(IntType.INSTANCE, program.typeOf(sum.right()).orElseThrow());
    }

    /** A callable that omits its return type is typed `Unit` (specification section 6). */
    @Test
    public void omittedReturnTypeIsUnit() {
        CheckedProgram program = check("func f() {\n    return\n}\n");
        assertEquals(UnitType.INSTANCE, program.function("f").orElseThrow().returnType());
    }

    /** An explicit `: Unit` and an omitted return type denote the same type. */
    @Test
    public void explicitUnitReturnTypeIsEquivalentToAnOmittedOne() {
        CheckedProgram program = check("func omitted() {\n    return\n}\nfunc written(): Unit {\n    omitted()\n}\n");
        assertEquals(UnitType.INSTANCE, program.function("omitted").orElseThrow().returnType());
        assertEquals(UnitType.INSTANCE, program.function("written").orElseThrow().returnType());
    }

    /** Every value-producing expression in a checked program has exactly one recorded type. */
    @Test
    public void everyValueExpressionIsTyped() {
        String src = "func g(a: Int): Int {\n" + //
                "    return a\n" + //
                "}\n" + //
                "func f(n: Int): Int {\n" + //
                "    var total = 0\n" + //
                "    var remaining = n\n" + //
                "    val flag = n > 0 && !(n == 0)\n" + //
                "    while (flag && remaining > 0) {\n" + //
                "        total = total + remaining\n" + //
                "        remaining = remaining - 1\n" + //
                "        if (total > 100) {\n" + //
                "            break\n" + //
                "        }\n" + //
                "        continue\n" + //
                "    }\n" + //
                "    for (var i = 0; i < 3; i = i + 1) {\n" + //
                "        total = total + g(i)\n" + //
                "    }\n" + //
                "    return total\n" + //
                "}\n";
        CheckedProgram program = check(src);
        Deque<AstNode> stack = new ArrayDeque<>();
        for (AstNode declaration : program.unit().declarations()) {
            stack.push(declaration);
        }
        int expressions = 0;
        while (!stack.isEmpty()) {
            AstNode node = stack.pop();
            if (node instanceof ExpressionNode expression) {
                expressions++;
                assertTrue(expression.kind() + " " + expression.span() + " has no recorded type", program.typeOf(expression).isPresent());
            }
            stack.addAll(node.children());
        }
        assertTrue("expected a rich set of expressions", expressions >= 20);
    }

    @Test
    public void bareStatementsBecomeTheImplicitMainEntryPoint() {
        CheckedProgram program = check("println(\"hi\")\nexit(0)\n");
        FunctionSymbol main = program.entryPoint().orElseThrow();
        assertEquals("main", main.name());
        assertEquals(UnitType.INSTANCE, main.returnType());
        assertTrue(main.parameters().isEmpty());
        assertEquals(0, program.unit().declarations().size());
        assertEquals(2, program.unit().statements().size());
    }

    @Test
    public void implicitMainCanCallDeclarationsFromTheSameFile() {
        CheckedProgram program = check("helper()\nfunc helper(): Unit {\n    println(\"x\")\n}\n");
        assertEquals("main", program.entryPoint().orElseThrow().name());
        assertEquals(1, program.unit().declarations().size());
        assertEquals(1, program.unit().statements().size());
    }

    @Test
    public void localTypeInferenceAndMutabilityAreRecorded() {
        String src = "func f(): Int {\n    val inferred = 1\n    var annotated: Int = inferred\n    val text = \"hi\"\n    annotated = 2\n    return annotated\n}\n";
        CheckedProgram program = check(src);
        FunctionDeclNode fn = function(program, 0);
        VariableSymbol inferred = program.symbolOf(local(fn, 0)).orElseThrow();
        assertEquals(IntType.INSTANCE, inferred.type());
        assertFalse(inferred.isMutable());
        assertTrue(inferred.isInitialized());

        VariableSymbol annotated = program.symbolOf(local(fn, 1)).orElseThrow();
        assertTrue(annotated.isMutable());

        VariableSymbol text = program.symbolOf(local(fn, 2)).orElseThrow();
        assertEquals(StringType.INSTANCE, text.type());
    }

    @Test
    public void nestedBlocksMayShadowOuterDeclarations() {
        String src = "func f(): Int {\n    val x = 1\n    if (true) {\n        val x = 2\n        return x\n    }\n    return x\n}\n";
        check(src);
    }

    @Test
    public void forInitializerVariableIsScopedToTheLoop() {
        check("func f(): Int {\n    for (var i = 0; i < 3; i = i + 1) {\n        return i\n    }\n    val i = 9\n    return i\n}\n");
    }

    @Test
    public void anyAcceptsEveryValueTypeWithoutDisablingChecking() {
        CheckedProgram program = check("func f(): Any {\n    val text: Any = \"hello\"\n    val number: Any = 1\n    val flag: Any = true\n    return text\n}\n");
        FunctionDeclNode fn = function(program, 0);
        assertEquals(org.solvik.type.AnyType.INSTANCE, program.symbolOf(local(fn, 0)).orElseThrow().type());
    }

    @Test
    public void operatorsTypeToTheirDeclaredResultTypes() {
        String src = "func ops(a: Int, b: Int, c: Boolean): Boolean {\n" + //
                "    val sum = a + b\n" + //
                "    val diff = a - b\n" + //
                "    val product = a * b\n" + //
                "    val quotient = a / b\n" + //
                "    val negated = -a\n" + //
                "    val less = a < b\n" + //
                "    val lessEq = a <= b\n" + //
                "    val greater = a > b\n" + //
                "    val greaterEq = a >= b\n" + //
                "    val equal = a == b\n" + //
                "    val notEqual = a != b\n" + //
                "    val and = c && c\n" + //
                "    val or = c || c\n" + //
                "    val not = !c\n" + //
                "    return and && or && not && less && lessEq && greater && greaterEq && equal && notEqual\n" + //
                "}\n";
        CheckedProgram program = check(src);
        FunctionDeclNode fn = function(program, 0);
        for (int i = 0; i <= 4; i++) {
            assertEquals("local " + i, IntType.INSTANCE, program.typeOf(local(fn, i).initializer()).orElseThrow());
        }
        for (int i = 5; i <= 13; i++) {
            assertEquals("local " + i, BooleanType.INSTANCE, program.typeOf(local(fn, i).initializer()).orElseThrow());
        }
        UnaryExprNode negated = (UnaryExprNode) local(fn, 4).initializer();
        assertEquals(IntType.INSTANCE, program.typeOf(negated.operand()).orElseThrow());
    }

    @Test
    public void stringConcatenationRequiresTwoStrings() {
        CheckedProgram program = check("func f(s: String, t: String): String {\n    val joined = s + t\n    return joined\n}\n");
        FunctionDeclNode fn = function(program, 0);
        assertEquals(StringType.INSTANCE, program.typeOf(local(fn, 0).initializer()).orElseThrow());
    }

    @Test
    public void rawStringLiteralHasStringType() {
        CheckedProgram program = check("func f(): String {\n    val pattern = r#\"\\d+\"#\n    return pattern\n}\n");
        FunctionDeclNode fn = function(program, 0);
        assertEquals(StringType.INSTANCE, program.typeOf(local(fn, 0).initializer()).orElseThrow());
    }

    @Test
    public void callsTypeCheckAgainstTheDeclaredSignature() {
        CheckedProgram program = check("func add(a: Int, b: Int): Int {\n    return a + b\n}\nfunc f(): Int {\n    return add(1, 2)\n}\n");
        FunctionDeclNode f = function(program, 1);
        CallExprNode call = (CallExprNode) ret(f, 0).value().orElseThrow();
        assertEquals(IntType.INSTANCE, program.typeOf(call).orElseThrow());
        assertTrue(program.typeOf(call.callee()).orElseThrow() instanceof FunctionType);
    }

    @Test
    public void exitIsPredeclaredAsIntToUnit() {
        CheckedProgram program = check("func f(): Unit {\n    exit(2)\n}\n");
        FunctionSymbol exit = program.function("exit").orElseThrow();
        assertTrue(exit.isBuiltin());
        assertEquals(UnitType.INSTANCE, exit.returnType());
        assertEquals(1, exit.parameters().size());
        assertEquals(IntType.INSTANCE, exit.parameters().get(0).type());
        CallExprNode call = (CallExprNode) expr(function(program, 0), 0).expression();
        assertEquals(UnitType.INSTANCE, program.typeOf(call).orElseThrow());
    }

    @Test
    public void functionsMayCallForwardDeclarations() {
        check("func f(): Int {\n    return g()\n}\nfunc g(): Int {\n    return 1\n}\n");
    }

    @Test
    public void loopsAndLoopControlCheckInsideLoops() {
        String src = "func f(n: Int): Int {\n" + //
                "    var total = 0\n" + //
                "    var remaining = n\n" + //
                "    while (remaining > 0) {\n" + //
                "        total = total + remaining\n" + //
                "        remaining = remaining - 1\n" + //
                "    }\n" + //
                "    for (var i = 0; i < 3; i = i + 1) {\n" + //
                "        if (i == 1) {\n" + //
                "            continue\n" + //
                "        }\n" + //
                "        if (i == 2) {\n" + //
                "            break\n" + //
                "        }\n" + //
                "        total = total + i\n" + //
                "    }\n" + //
                "    return total\n" + //
                "}\n";
        CheckedProgram program = check(src);
        assertEquals(IntType.INSTANCE, program.function("f").orElseThrow().returnType());
        List<AstNode> statements = new ArrayList<>(body(function(program, 0)).statements());
        assertEquals(5, statements.size());
    }
}
