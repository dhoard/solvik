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
import static org.solvik.test.SolvikTestSupport.body;
import static org.solvik.test.SolvikTestSupport.expr;
import static org.solvik.test.SolvikTestSupport.local;
import static org.solvik.test.SolvikTestSupport.parseOk;
import static org.solvik.test.SolvikTestSupport.ret;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;
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
import org.solvik.type.IntegerType;
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
        assertThat(result.isSuccess()).as("analysis must succeed: " + result.diagnostics().all()).isTrue();
        return result.requireProgram();
    }

    private static FunctionDeclNode function(CheckedProgram program, int index) {
        return (FunctionDeclNode) program.unit().declarations().get(index);
    }

    /** The canonical proof program for the statically checked core. */
    @Test
    public void canonicalProofIsResolvedAndStaticallyChecked() {
        CheckedProgram program = check("func add(a: Integer, b: Integer): Integer {\n    return a + b\n}\n");
        FunctionSymbol add = program.function("add").orElseThrow();
        assertThat(add.name()).isEqualTo("add");
        assertThat(add.returnType()).isEqualTo(IntegerType.INSTANCE);
        assertThat(add.parameters().size()).isEqualTo(2);
        assertThat(add.parameters().get(0).type()).isEqualTo(IntegerType.INSTANCE);
        assertThat(add.parameters().get(1).type()).isEqualTo(IntegerType.INSTANCE);
        assertThat(program.entryPoint().isEmpty()).as("no executable entry point is declared").isTrue();

        FunctionDeclNode declaration = function(program, 0);
        BinaryExprNode sum = (BinaryExprNode) ret(declaration, 0).value().orElseThrow();
        assertThat(program.typeOf(sum).orElseThrow()).isEqualTo(IntegerType.INSTANCE);
        assertThat(program.typeOf(sum.left()).orElseThrow()).isEqualTo(IntegerType.INSTANCE);
        assertThat(program.typeOf(sum.right()).orElseThrow()).isEqualTo(IntegerType.INSTANCE);
    }

    /** A callable that omits its return type is typed `Unit` (specification section 6). */
    @Test
    public void omittedReturnTypeIsUnit() {
        CheckedProgram program = check("func f() {\n    return\n}\n");
        assertThat(program.function("f").orElseThrow().returnType()).isEqualTo(UnitType.INSTANCE);
    }

    /** An explicit `: Unit` and an omitted return type denote the same type. */
    @Test
    public void explicitUnitReturnTypeIsEquivalentToAnOmittedOne() {
        CheckedProgram program = check("func omitted() {\n    return\n}\nfunc written(): Unit {\n    omitted()\n}\n");
        assertThat(program.function("omitted").orElseThrow().returnType()).isEqualTo(UnitType.INSTANCE);
        assertThat(program.function("written").orElseThrow().returnType()).isEqualTo(UnitType.INSTANCE);
    }

    /** Every value-producing expression in a checked program has exactly one recorded type. */
    @Test
    public void everyValueExpressionIsTyped() {
        String src = "func g(a: Integer): Integer {\n" + //
                "    return a\n" + //
                "}\n" + //
                "func f(n: Integer): Integer {\n" + //
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
                assertThat(program.typeOf(expression).isPresent()).as(expression.kind() + " " + expression.span() + " has no recorded type").isTrue();
            }
            stack.addAll(node.children());
        }
        assertThat(expressions >= 20).as("expected a rich set of expressions").isTrue();
    }

    @Test
    public void bareStatementsBecomeTheImplicitMainEntryPoint() {
        CheckedProgram program = check("println(\"hi\")\nexit(0)\n");
        FunctionSymbol main = program.entryPoint().orElseThrow();
        assertThat(main.name()).isEqualTo("main");
        assertThat(main.returnType()).isEqualTo(UnitType.INSTANCE);
        assertThat(main.parameters().isEmpty()).isTrue();
        assertThat(program.unit().declarations().size()).isEqualTo(0);
        assertThat(program.unit().statements().size()).isEqualTo(2);
    }

    @Test
    public void implicitMainCanCallDeclarationsFromTheSameFile() {
        CheckedProgram program = check("helper()\nfunc helper(): Unit {\n    println(\"x\")\n}\n");
        assertThat(program.entryPoint().orElseThrow().name()).isEqualTo("main");
        assertThat(program.unit().declarations().size()).isEqualTo(1);
        assertThat(program.unit().statements().size()).isEqualTo(1);
    }

    @Test
    public void localTypeInferenceAndMutabilityAreRecorded() {
        String src = "func f(): Integer {\n    val inferred = 1\n    var annotated: Integer = inferred\n    val text = \"hi\"\n    annotated = 2\n    return annotated\n}\n";
        CheckedProgram program = check(src);
        FunctionDeclNode fn = function(program, 0);
        VariableSymbol inferred = program.symbolOf(local(fn, 0)).orElseThrow();
        assertThat(inferred.type()).isEqualTo(IntegerType.INSTANCE);
        assertThat(inferred.isMutable()).isFalse();
        assertThat(inferred.isInitialized()).isTrue();

        VariableSymbol annotated = program.symbolOf(local(fn, 1)).orElseThrow();
        assertThat(annotated.isMutable()).isTrue();

        VariableSymbol text = program.symbolOf(local(fn, 2)).orElseThrow();
        assertThat(text.type()).isEqualTo(StringType.INSTANCE);
    }

    @Test
    public void nestedBlocksMayShadowOuterDeclarations() {
        String src = "func f(): Integer {\n    val x = 1\n    if (true) {\n        val x = 2\n        return x\n    }\n    return x\n}\n";
        check(src);
    }

    @Test
    public void forInitializerVariableIsScopedToTheLoop() {
        check("func f(): Integer {\n    for (var i = 0; i < 3; i = i + 1) {\n        return i\n    }\n    val i = 9\n    return i\n}\n");
    }

    @Test
    public void anyAcceptsEveryValueTypeWithoutDisablingChecking() {
        CheckedProgram program = check("func f(): Any {\n    val text: Any = \"hello\"\n    val number: Any = 1\n    val flag: Any = true\n    return text\n}\n");
        FunctionDeclNode fn = function(program, 0);
        assertThat(program.symbolOf(local(fn, 0)).orElseThrow().type()).isEqualTo(org.solvik.type.AnyType.INSTANCE);
    }

    @Test
    public void operatorsTypeToTheirDeclaredResultTypes() {
        String src = "func ops(a: Integer, b: Integer, c: Boolean): Boolean {\n" + //
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
            assertThat(program.typeOf(local(fn, i).initializer()).orElseThrow()).as("local " + i).isEqualTo(IntegerType.INSTANCE);
        }
        for (int i = 5; i <= 13; i++) {
            assertThat(program.typeOf(local(fn, i).initializer()).orElseThrow()).as("local " + i).isEqualTo(BooleanType.INSTANCE);
        }
        UnaryExprNode negated = (UnaryExprNode) local(fn, 4).initializer();
        assertThat(program.typeOf(negated.operand()).orElseThrow()).isEqualTo(IntegerType.INSTANCE);
    }

    @Test
    public void concatenationYieldsAString() {
        CheckedProgram program = check("func f(s: String, t: String): String {\n    val joined = s .. t\n    return joined\n}\n");
        FunctionDeclNode fn = function(program, 0);
        assertThat(program.typeOf(local(fn, 0).initializer()).orElseThrow()).isEqualTo(StringType.INSTANCE);
    }

    @Test
    public void rawStringLiteralHasStringType() {
        CheckedProgram program = check("func f(): String {\n    val pattern = r#\"\\d+\"#\n    return pattern\n}\n");
        FunctionDeclNode fn = function(program, 0);
        assertThat(program.typeOf(local(fn, 0).initializer()).orElseThrow()).isEqualTo(StringType.INSTANCE);
    }

    @Test
    public void callsTypeCheckAgainstTheDeclaredSignature() {
        CheckedProgram program = check("func add(a: Integer, b: Integer): Integer {\n    return a + b\n}\nfunc f(): Integer {\n    return add(1, 2)\n}\n");
        FunctionDeclNode f = function(program, 1);
        CallExprNode call = (CallExprNode) ret(f, 0).value().orElseThrow();
        assertThat(program.typeOf(call).orElseThrow()).isEqualTo(IntegerType.INSTANCE);
        assertThat(program.typeOf(call.callee()).orElseThrow() instanceof FunctionType).isTrue();
    }

    @Test
    public void exitIsPredeclaredAsIntegerToUnit() {
        CheckedProgram program = check("func f(): Unit {\n    exit(2)\n}\n");
        FunctionSymbol exit = program.function("exit").orElseThrow();
        assertThat(exit.isBuiltin()).isTrue();
        assertThat(exit.returnType()).isEqualTo(UnitType.INSTANCE);
        assertThat(exit.parameters().size()).isEqualTo(1);
        assertThat(exit.parameters().get(0).type()).isEqualTo(IntegerType.INSTANCE);
        CallExprNode call = (CallExprNode) expr(function(program, 0), 0).expression();
        assertThat(program.typeOf(call).orElseThrow()).isEqualTo(UnitType.INSTANCE);
    }

    @Test
    public void functionsMayCallForwardDeclarations() {
        check("func f(): Integer {\n    return g()\n}\nfunc g(): Integer {\n    return 1\n}\n");
    }

    @Test
    public void loopsAndLoopControlCheckInsideLoops() {
        String src = "func f(n: Integer): Integer {\n" + //
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
        assertThat(program.function("f").orElseThrow().returnType()).isEqualTo(IntegerType.INSTANCE);
        List<AstNode> statements = new ArrayList<>(body(function(program, 0)).statements());
        assertThat(statements.size()).isEqualTo(5);
    }

    @Test
    public void siblingScopeBlocksMayReuseALocalName() {
        String src = "func f(): Integer {\n" + //
                "    var total = 0\n" + //
                "    {\n" + //
                "        val result = 1\n" + //
                "        total = total + result\n" + //
                "    }\n" + //
                "    {\n" + //
                "        val result = 2\n" + //
                "        total = total + result\n" + //
                "    }\n" + //
                "    return total\n" + //
                "}\n";
        CheckedProgram program = check(src);
        assertThat(body(function(program, 0)).statements().size()).isEqualTo(4);
    }
}
