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
import static org.junit.Assert.assertTrue;
import static org.solvik.test.SolvikTestSupport.local;
import static org.solvik.test.SolvikTestSupport.parseOk;

import org.junit.Test;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.expression.CastExprNode;
import org.solvik.ast.expression.ExpressionNode;
import org.solvik.ast.expression.MemberAccessExprNode;
import org.solvik.ast.expression.NameRefExprNode;
import org.solvik.ast.expression.TypeTestExprNode;
import org.solvik.ast.statement.BlockNode;
import org.solvik.ast.statement.IfStmtNode;
import org.solvik.ast.statement.LocalDeclNode;
import org.solvik.ast.statement.ReturnStmtNode;
import org.solvik.semantic.CheckedProgram;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;
import org.solvik.type.BooleanType;
import org.solvik.type.ClassType;
import org.solvik.type.IntType;
import org.solvik.type.NullType;
import org.solvik.type.ObjectType;
import org.solvik.type.StringType;
import org.solvik.type.Type;

/**
 * Positive Phase 10 semantic tests (docs/LANGUAGE_SPEC.md sections 5 and 18): nullable assignment
 * compatibility, the types of {@code ?.}, {@code ??}, {@code is}, and {@code as}, and flow-sensitive
 * narrowing of null checks and type tests.
 */
public final class SolvikNullSafetySemanticTest {

    private static CheckedProgram check(String text) {
        CompilationUnitNode unit = parseOk("nullsem.sol", text);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertTrue("analysis must succeed: " + result.diagnostics().all(), result.isSuccess());
        return result.requireProgram();
    }

    private static FunctionDeclNode function(CheckedProgram program, int index) {
        return (FunctionDeclNode) program.unit().declarations().get(index);
    }

    private static Type boxType(CheckedProgram program) {
        return program.classSymbol("Box").orElseThrow().type();
    }

    /** The canonical {@code Box} fixture used by the nullable member-access tests. */
    private static final String BOX = """
            class Box {
                val value: Int

                Box(value: Int) {
                    this.value = value
                }
            }
            """;

    @Test
    public void nullLiteralHasTheNullType() {
        CheckedProgram program = check("func f(): Unit {\n    val x = null\n}\n");
        assertEquals(NullType.INSTANCE, program.typeOf(local(function(program, 0), 0).initializer()).orElseThrow());
    }

    @Test
    public void nonNullValuesAreAssignableToNullableTypes() {
        CheckedProgram program = check("func f(s: String): Unit {\n    val a: String? = s\n    val b: String? = null\n}\n");
        FunctionDeclNode fn = function(program, 0);
        Type stringNullable = StringType.INSTANCE.nullableView();
        assertEquals(stringNullable, program.symbolOf(local(fn, 0)).orElseThrow().type());
        assertEquals(stringNullable, program.symbolOf(local(fn, 1)).orElseThrow().type());
    }

    @Test
    public void nullableIsAssignableToANullableSupertype() {
        CheckedProgram program = check("func f(s: String?): Object? {\n    return s\n}\n");
        assertEquals(ObjectType.INSTANCE.nullableView(), program.function("f").orElseThrow().returnType());
    }

    @Test
    public void safeAccessMakesTheResultNullable() {
        CheckedProgram program = check(BOX + """
                func f(box: Box?): Int? {
                    return box?.value
                }
                """);
        FunctionDeclNode fn = function(program, 1);
        ExpressionNode value = ret0(fn);
        assertEquals(IntType.INSTANCE.nullableView(), program.typeOf(value).orElseThrow());
    }

    @Test
    public void safeAccessOnANonNullReceiverStaysNonNull() {
        CheckedProgram program = check(BOX + """
                func f(box: Box): Int {
                    return box?.value
                }
                """);
        FunctionDeclNode fn = function(program, 1);
        assertEquals(IntType.INSTANCE, program.typeOf(ret0(fn)).orElseThrow());
    }

    @Test
    public void coalescingWithANonNullRightIsNonNull() {
        CheckedProgram program = check("func f(s: String?): String {\n    return s ?? \"unknown\"\n}\n");
        assertEquals(StringType.INSTANCE, program.typeOf(ret0(function(program, 0))).orElseThrow());
    }

    @Test
    public void coalescingTwoNullablesStaysNullable() {
        CheckedProgram program = check("func f(s: String?, t: String?): String? {\n    return s ?? t\n}\n");
        assertEquals(StringType.INSTANCE.nullableView(), program.typeOf(ret0(function(program, 0))).orElseThrow());
    }

    @Test
    public void typeTestIsBooleanAndRecordsItsTarget() {
        CheckedProgram program = check(BOX + """
                func f(v: Any): Boolean {
                    return v is Box
                }
                """);
        FunctionDeclNode fn = function(program, 1);
        TypeTestExprNode test = (TypeTestExprNode) ret0(fn);
        assertEquals(BooleanType.INSTANCE, program.typeOf(test).orElseThrow());
        assertEquals(boxType(program), program.testedTypeOf(test).orElseThrow());
    }

    @Test
    public void checkedCastHasTheTargetType() {
        CheckedProgram program = check(BOX + """
                func f(v: Any): Box {
                    return v as Box
                }
                """);
        FunctionDeclNode fn = function(program, 1);
        CastExprNode cast = (CastExprNode) ret0(fn);
        assertEquals(boxType(program), program.typeOf(cast).orElseThrow());
        assertEquals(boxType(program), program.testedTypeOf(cast).orElseThrow());
    }

    @Test
    public void nullCheckNarrowsAReadToTheNonNullType() {
        CheckedProgram program = check(BOX + """
                func f(box: Box?): Int {
                    if (box != null) {
                        return box.value
                    }
                    return 0
                }
                """);
        FunctionDeclNode fn = function(program, 1);
        MemberAccessExprNode access = memberInThenBlock(fn);
        NameRefExprNode receiver = (NameRefExprNode) access.receiver();
        assertTrue("receiver narrows to the class type", program.typeOf(receiver).orElseThrow() instanceof ClassType);
        assertEquals(boxType(program), program.typeOf(receiver).orElseThrow());
        assertEquals(IntType.INSTANCE, program.typeOf(access).orElseThrow());
    }

    @Test
    public void typeTestNarrowsStableValues() {
        CheckedProgram program = check(BOX + """
                func f(v: Any): Int {
                    if (v is Box) {
                        return v.value
                    }
                    return 0
                }
                """);
        FunctionDeclNode fn = function(program, 1);
        MemberAccessExprNode access = memberInThenBlock(fn);
        NameRefExprNode receiver = (NameRefExprNode) access.receiver();
        assertEquals(boxType(program), program.typeOf(receiver).orElseThrow());
    }

    @Test
    public void aVarNarrowsTheSameWayUntilItIsWritten() {
        CheckedProgram program = check(BOX + """
                func f(): Int {
                    var box: Box? = Box(1)
                    if (box != null) {
                        return box.value
                    }
                    return 0
                }
                """);
        MemberAccessExprNode access = memberInThenBlock(function(program, 1));
        assertEquals(boxType(program), program.typeOf(access.receiver()).orElseThrow());
    }

    @Test
    public void anEarlyReturnNarrowsTheCodeAfterTheIf() {
        CheckedProgram program = check(BOX + """
                func f(box: Box?): Int {
                    if (box == null) {
                        return 0
                    }
                    return box.value
                }
                """);
        FunctionDeclNode fn = function(program, 1);
        // The second return is after the `if`, where the false branch of `box == null` holds.
        ReturnStmtNode second = (ReturnStmtNode) fn.body().statements().get(1);
        MemberAccessExprNode access = (MemberAccessExprNode) second.value().orElseThrow();
        assertEquals(boxType(program), program.typeOf(access.receiver()).orElseThrow());
    }

    @Test
    public void aWhileConditionNarrowsItsBody() {
        CheckedProgram program = check(BOX + """
                func f(box: Box?): Int {
                    var total: Int = 0
                    while (box != null) {
                        total = total + box.value
                    }
                    return total
                }
                """);
        FunctionDeclNode fn = function(program, 1);
        org.solvik.ast.statement.WhileStmtNode loop = (org.solvik.ast.statement.WhileStmtNode) fn.body().statements().get(1);
        org.solvik.ast.statement.AssignStmtNode assignment = (org.solvik.ast.statement.AssignStmtNode) loop.body().statements().get(0);
        org.solvik.ast.expression.BinaryExprNode sum = (org.solvik.ast.expression.BinaryExprNode) assignment.value();
        MemberAccessExprNode access = (MemberAccessExprNode) sum.right();
        assertEquals(boxType(program), program.typeOf(access.receiver()).orElseThrow());
    }

    @Test
    public void theElseBranchOfANullCheckIsNarrowed() {
        CheckedProgram program = check(BOX + """
                func f(box: Box?): Int {
                    if (box == null) {
                        return 0
                    } else {
                        return box.value
                    }
                }
                """);
        FunctionDeclNode fn = function(program, 1);
        IfStmtNode ifStatement = (IfStmtNode) fn.body().statements().get(0);
        BlockNode elseBlock = ifStatement.elseBranch().orElseThrow().block().orElseThrow();
        ReturnStmtNode inElse = (ReturnStmtNode) elseBlock.statements().get(0);
        MemberAccessExprNode access = (MemberAccessExprNode) inElse.value().orElseThrow();
        assertEquals(boxType(program), program.typeOf(access.receiver()).orElseThrow());
    }

    @Test
    public void nullablePropertiesAreStoredAndRead() {
        CheckedProgram program = check("""
                class Holder {
                    var name: String?

                    Holder(name: String?) {
                        this.name = name
                    }
                }
                func f(): Unit {
                    val holder = Holder(null)
                    holder.name = "Doug"
                }
                """);
        Type holderType = program.classSymbol("Holder").orElseThrow().type();
        assertEquals(StringType.INSTANCE.nullableView(), program.classSymbol("Holder").orElseThrow().property("name").orElseThrow().type());
        assertTrue(holderType.isSubtypeOf(ObjectType.INSTANCE));
    }

    private static ExpressionNode ret0(FunctionDeclNode function) {
        return ((ReturnStmtNode) function.body().statements().get(0)).value().orElseThrow();
    }

    /** The {@code box.value} member access inside the first {@code if} of a function body. */
    private static MemberAccessExprNode memberInThenBlock(FunctionDeclNode function) {
        IfStmtNode ifStatement = null;
        for (org.solvik.ast.statement.StatementNode statement : function.body().statements()) {
            if (statement instanceof IfStmtNode candidate) {
                ifStatement = candidate;
                break;
            }
        }
        if (ifStatement == null) {
            throw new AssertionError("no if statement in the function body");
        }
        BlockNode thenBlock = ifStatement.thenBlock();
        ReturnStmtNode returnStatement = (ReturnStmtNode) thenBlock.statements().get(0);
        return (MemberAccessExprNode) returnStatement.value().orElseThrow();
    }
}
