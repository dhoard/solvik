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
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.statement.LocalDeclNode;
import org.solvik.semantic.CheckedProgram;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;
import org.solvik.type.IntegerType;
import org.solvik.type.NothingType;
import org.solvik.type.AnyType;
import org.solvik.type.StringType;
import org.solvik.type.Type;

/** Positive semantic tests for expression-oriented constructs (docs/LANGUAGE_SPEC.md section 21). */
public final class SolvikExpressionOrientedSemanticTest {

    private static CheckedProgram check(String text) {
        CompilationUnitNode unit = parseOk("expr.sol", text);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertThat(result.isSuccess()).as("analysis must succeed: " + result.diagnostics().all()).isTrue();
        return result.requireProgram();
    }

    private static SemanticResult checkFails(String text) {
        CompilationUnitNode unit = parseOk("expr.sol", text);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertThat(result.isSuccess()).as("analysis must fail but succeeded").isFalse();
        return result;
    }

    private static DiagnosticCode error(SemanticResult result) {
        return result.diagnostics().all().get(0).code();
    }

    private static Type initializerType(CheckedProgram program, String functionName) {
        for (var declaration : program.unit().declarations()) {
            if (declaration instanceof FunctionDeclNode function && function.name().equals(functionName)) {
                LocalDeclNode local = (LocalDeclNode) function.body().statements().get(0);
                return program.typeOf(local.initializer()).orElseThrow();
            }
        }
        throw new AssertionError("no function named " + functionName);
    }

    @Test
    public void blockExpressionResultTypes() {
        assertThat(initializerType(check("""
                func a(): Integer {
                    var x: Integer = {
                        1
                    }
                    return x
                }

                """), "a")).isSameAs(IntegerType.INSTANCE);
        assertThat(initializerType(check("""
                func a(): Integer {
                    var x: Integer = {
                        var local: Integer = 10; local + 20
                    }
                    return x
                }

                """), "a")).isSameAs(IntegerType.INSTANCE);
        assertThat(initializerType(check("""
                func a(): Integer {
                    var x: Integer = {
                        var inner: Integer = {
                            20
                        }
                        ; inner + 22
                    }
                    return x
                }

                """), "a")).isSameAs(IntegerType.INSTANCE);
    }

    @Test
    public void ifExpressionJoinsIntegerAndStringToAny() {
        Type type = initializerType(check("""
                func a(flag: Boolean): Integer {
                    var x: Any = if (flag) {
                        1
                    }
                    else {
                        "text"
                    }
                    return 1
                }

                """), "a");
        assertThat(type).isSameAs(AnyType.INSTANCE);
    }

    @Test
    public void ifExpressionJoinsExactSubtypeAndNullable() {
        assertThat(initializerType(check("""
                func a(flag: Boolean): Integer {
                    var x: Integer = if (flag) {
                        1
                    }
                    else {
                        2
                    }
                    return x
                }

                """), "a")).isSameAs(IntegerType.INSTANCE);
        assertThat(initializerType(check("""
                func a(flag: Boolean, value: String?): String? {
                    var x: String? = if (flag) {
                        value
                    }
                    else {
                        "fallback"
                    }
                    return x
                }

                """), "a")).isSameAs(StringType.INSTANCE.nullableView());
    }

    /**
     * A block or conditional that produces no value cannot be bound: there is no source type for "no
     * value", so the initializer is rejected exactly as any other non-value is.
     */
    @Test
    public void noValueTailResultsCannotBeBound() {
        assertThat(error(checkFails("""
                func a() {
                    var x: Integer = {
                        println("done")
                    }
                }

                """))).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
        assertThat(error(checkFails("""
                func a(flag: Boolean) {
                    var x: Integer = if (flag) {
                        println("a")
                    }
                    else {
                        println("b")
                    }
                }

                """))).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void abruptBranchIsExcludedFromTheJoin() {
        assertThat(initializerType(check("""
                func a(name: String?): String {
                    var x: String = if (name != null) {
                        name
                    }
                    else {
                        return "fallback"
                    }
                    return x
                }

                """), "a")).isSameAs(StringType.INSTANCE);
    }

    @Test
    public void ifWithAllAbruptBranchesHasTypeNothing() {
        assertThat(initializerType(check("""
                func a(flag: Boolean): Integer {
                    var x: Nothing = if (flag) {
                        return 1
                    }
                    else {
                        return 2
                    }
                    return 0
                }

                """), "a")).isSameAs(NothingType.INSTANCE);
    }

    @Test
    public void switchExpressionJoinsCaseResults() {
        assertThat(initializerType(check("""
                func a(value: Integer): String {
                    var x: String = switch (value) {
                        case 1 {
                            "one"
                        }
                        default {
                            "other"
                        }
                    }
                    return x
                }

                """), "a")).isSameAs(StringType.INSTANCE);
    }

    @Test
    public void expressionConstructsAreAcceptedAsArgumentsAssignmentsAndReturns() {
        CheckedProgram program = check("""
                func classify(value: Integer): String {
                    return if (value == 0) {
                        "zero"
                    }
                    else {
                        "nonzero"
                    }
                }

                var mutable score: Integer = 0
                score = if (true) {
                    10
                }
                else {
                    0
                }
                print(if (true) {
                    "a"
                }
                else {
                    "b"
                }
                )
                print(classify(score))
                print(switch (score) {
                    case 10 {
                        "ten"
                    }
                    default {
                        "other"
                    }
                }
                )
                """);
        assertThat(program.entryPoint()).isPresent();
    }

    @Test
    public void statementIfWithoutElseAndStatementSwitchWithoutDefaultRemainValid() {
        check("""
                func run(value: Integer, debug: Boolean) {
                    if (debug) {
                        print("debug")
                    }
                    switch (value) {
                        case 1 {
                            print("one")
                        }
                    }
                }

                """);
    }

    @Test
    public void matchBlockBranchSharesTheJoinImplementation() {
        assertThat(initializerType(check("""
                enum Result {
                    Ok(Integer)
                    Error(String)
                }
                func a(result: Result): Integer {
                    var x: Integer = match result {
                        Ok(value) => {
                            print("ok")
                            value
                        }
                        Error(message) => {
                            print(message)
                            0
                        }
                    }
                    return x
                }

                """), "a")).isSameAs(IntegerType.INSTANCE);
    }

    @Test
    public void flowNarrowingAppliesInsideAnIfExpressionBranch() {
        assertThat(initializerType(check("""
                func need(value: String): Integer {
                    return 0
                }
                func a(value: String?): Integer {
                    var x: Integer = if (value != null) {
                        need(value)
                    }
                    else {
                        0
                    }
                    return x
                }

                """), "a")).isSameAs(IntegerType.INSTANCE);
    }

    @Test
    public void switchAbruptCaseIsExcludedFromTheJoin() {
        assertThat(initializerType(check("""
                func a(value: Integer): String {
                    var x: String = switch (value) {
                        case 0 {
                            return "zero"
                        }
                        default {
                            "nonzero"
                        }
                    }
                    return x
                }

                """), "a")).isSameAs(StringType.INSTANCE);
    }

    @Test
    public void branchesJoinToACommonInterface() {
        Type type = initializerType(check("""
                interface Named {
                    method name(): String
                }
                class A implements Named {
                    method name(): String {
                        return "a"
                    }
                }
                class B implements Named {
                    method name(): String {
                        return "b"
                    }
                }
                func f(flag: Boolean): Named {
                    var x: Named = if (flag) {
                        A()
                    }
                    else {
                        B()
                    }
                    return x
                }

                """), "f");
        assertThat(type.name()).isEqualTo("Named");
    }
}
