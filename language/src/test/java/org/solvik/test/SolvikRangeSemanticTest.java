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
import static org.solvik.test.SolvikTestSupport.parseFails;
import static org.solvik.test.SolvikTestSupport.parseOk;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.statement.ForInStmtNode;
import org.solvik.diagnostic.Diagnostic;
import org.solvik.diagnostic.DiagnosticBag;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.semantic.CheckedProgram;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;
import org.solvik.semantic.VariableSymbol;
import org.solvik.type.IntegerType;

/**
 * Static-semantics tests for range {@code for}-in loops (docs/LANGUAGE_SPEC.md section 17): the loop
 * variable is an implicitly declared immutable {@code Integer} scoped to the body, both bounds must be
 * {@code Integer}, and {@code in} is reserved.
 */
public final class SolvikRangeSemanticTest {

    private static CheckedProgram check(String text) {
        CompilationUnitNode unit = parseOk("range.sol", text);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertThat(result.isSuccess()).as("analysis must succeed: " + result.diagnostics().all()).isTrue();
        return result.requireProgram();
    }

    private static DiagnosticBag checkFails(String text) {
        CompilationUnitNode unit = parseOk("rangeneg.sol", text);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertThat(result.isSuccess()).as("analysis must fail: " + text).isFalse();
        assertThat(result.program().isEmpty()).as("failed analysis must expose no program").isTrue();
        assertThat(result.diagnostics().hasErrors()).as("failed analysis must carry diagnostics").isTrue();
        for (Diagnostic diagnostic : result.diagnostics().all()) {
            assertThat(diagnostic.span().endOffset() <= text.length()).as("span within source bounds: " + diagnostic.span()).isTrue();
        }
        return result.diagnostics();
    }

    private static Diagnostic first(DiagnosticBag bag) {
        List<Diagnostic> all = bag.all();
        assertThat(all.isEmpty()).isFalse();
        return all.get(0);
    }

    private static ForInStmtNode firstLoop(CheckedProgram program) {
        FunctionDeclNode fn = (FunctionDeclNode) program.unit().declarations().get(0);
        return (ForInStmtNode) fn.body().statements().get(0);
    }

    @Test
    public void loopVariableIsAnImmutableInteger() {
        CheckedProgram program = check("func f(): Unit {\n    for (i in 0...2) {\n        val copy: Integer = i\n    }\n}\n");
        VariableSymbol variable = program.forInBindingOf(firstLoop(program)).orElseThrow();
        assertThat(variable.type()).isEqualTo(IntegerType.INSTANCE);
        assertThat(variable.isMutable()).as("the loop variable is immutable").isFalse();
    }

    @Test
    public void arbitraryIntegerBoundsAreAccepted() {
        check("func bound(): Integer {\n    return 3\n}\nfunc f(): Unit {\n    val start = 1\n    for (i in start...bound()) {\n    }\n}\n");
    }

    @Test
    public void emptyAndReversedRangesAreNotErrors() {
        check("func f(): Unit {\n    for (i in 0..<0) {\n    }\n    for (j in 5...1) {\n    }\n    for (k in 0..>0) {\n    }\n}\n");
    }

    @Test
    public void breakAndContinueAreValidInsideTheLoop() {
        check("func f(): Unit {\n    for (i in 0...2) {\n        if (i == 1) {\n            continue\n        }\n        break\n    }\n}\n");
    }

    @Test
    public void loopVariableShadowsAnOuterBinding() {
        check("func f(): Integer {\n    var i = 99\n    for (i in 0...1) {\n    }\n    return i\n}\n");
    }

    @Test
    public void nonIntegerStartBoundIsRejected() {
        assertThat(first(checkFails("func f(): Unit {\n    for (i in 0.5...2) {\n    }\n}\n")).code()).isEqualTo(DiagnosticCode.SEM_INVALID_RANGE_BOUND);
    }

    @Test
    public void nonIntegerEndBoundIsRejected() {
        assertThat(first(checkFails("func f(): Unit {\n    for (i in 0...true) {\n    }\n}\n")).code()).isEqualTo(DiagnosticCode.SEM_INVALID_RANGE_BOUND);
    }

    @Test
    public void assigningToTheLoopVariableIsRejected() {
        assertThat(first(checkFails("func f(): Unit {\n    for (i in 0...2) {\n        i = 3\n    }\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE);
    }

    @Test
    public void inIsReserved() {
        parseFails("reserved.sol", "func f(): Integer {\n    val in = 1\n    return in\n}\n");
    }

    @Test
    public void rangeOperatorOutsideAForHeaderIsAParseError() {
        parseFails("rangeonly.sol", "func f(): Integer {\n    val x = 0...2\n    return x\n}\n");
    }
}
