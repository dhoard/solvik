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
import static org.solvik.test.SolvikTestSupport.parseOk;

import java.util.List;
import org.junit.Test;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.statement.ReturnStmtNode;
import org.solvik.diagnostic.Diagnostic;
import org.solvik.diagnostic.DiagnosticBag;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.semantic.CheckedProgram;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;
import org.solvik.type.StringType;

/**
 * Static-semantics tests for {@code Any.toString()} (docs/LANGUAGE_SPEC.md section 4): the built-in
 * root member resolves on every receiver kind and yields {@code String}; a class override must use
 * {@code override}, keep the empty parameter list, and return {@code String}; and a stored member
 * may not reuse the reserved name.
 */
public final class SolvikToStringSemanticTest {

    private static CheckedProgram check(String text) {
        CompilationUnitNode unit = parseOk("tostring.sol", text);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertTrue("analysis must succeed: " + result.diagnostics().all(), result.isSuccess());
        return result.requireProgram();
    }

    private static DiagnosticBag checkFails(String text) {
        CompilationUnitNode unit = parseOk("tostringneg.sol", text);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertFalse("analysis must fail: " + text, result.isSuccess());
        assertTrue("failed analysis must expose no program", result.program().isEmpty());
        assertTrue("failed analysis must carry diagnostics", result.diagnostics().hasErrors());
        for (Diagnostic diagnostic : result.diagnostics().all()) {
            assertTrue("span within source bounds: " + diagnostic.span(), diagnostic.span().endOffset() <= text.length());
        }
        return result.diagnostics();
    }

    private static Diagnostic first(DiagnosticBag bag) {
        List<Diagnostic> all = bag.all();
        assertFalse(all.isEmpty());
        return all.get(0);
    }

    @Test
    public void toStringResolvesOnEveryReceiverKind() {
        check("""
                interface Named {
                    func name(): String
                }
                class User implements Named {
                    val label: String

                    User(label: String) {
                        this.label = label
                    }

                    func name(): String {
                        return this.label
                    }

                    override func toString(): String {
                        return this.label
                    }
                }
                enum Color {
                    Red
                    Green
                }
                func render(i: Int, l: Long, d: Double, b: Boolean, c: Char, s: String, a: Any, o: Object, u: User, n: Named, color: Color): String {
                    return i.toString() .. l.toString() .. d.toString() .. b.toString() .. c.toString() .. s.toString() .. a.toString() .. o.toString() .. u.toString() .. n.toString() .. color.toString()
                }
                """);
    }

    @Test
    public void toStringCallHasStringType() {
        CheckedProgram program = check("func f(x: Any): String {\n    return x.toString()\n}\n");
        FunctionDeclNode fn = (FunctionDeclNode) program.unit().declarations().get(0);
        ReturnStmtNode statement = (ReturnStmtNode) fn.body().statements().get(0);
        assertEquals(StringType.INSTANCE, program.typeOf(statement.value().orElseThrow()).orElseThrow());
    }

    @Test
    public void safeToStringOnNullableYieldsNullableString() {
        CheckedProgram program = check("func f(x: String?): String? {\n    return x?.toString()\n}\n");
        FunctionDeclNode fn = (FunctionDeclNode) program.unit().declarations().get(0);
        ReturnStmtNode statement = (ReturnStmtNode) fn.body().statements().get(0);
        assertEquals(StringType.INSTANCE.nullableView(), program.typeOf(statement.value().orElseThrow()).orElseThrow());
    }

    @Test
    public void declaringOverrideToStringIsAccepted() {
        check("""
                class C {
                    override func toString(): String {
                        return "c"
                    }
                }
                """);
    }

    @Test
    public void toStringOnNullableReceiverWithoutSafeAccessIsRejected() {
        assertEquals(DiagnosticCode.TYPE_NULLABLE_DEREFERENCE, first(checkFails("func f(x: String?): String {\n    return x.toString()\n}\n")).code());
    }

    @Test
    public void toStringWithAnArgumentIsRejected() {
        assertEquals(DiagnosticCode.TYPE_ARITY_MISMATCH, first(checkFails("func f(x: Any): String {\n    return x.toString(1)\n}\n")).code());
    }

    @Test
    public void toStringWithoutOverrideIsRejected() {
        assertEquals(DiagnosticCode.SEM_ACCIDENTAL_OVERRIDE, first(checkFails("class C {\n    func toString(): String {\n        return \"c\"\n    }\n}\n")).code());
    }

    @Test
    public void overrideToStringWithWrongReturnTypeIsRejected() {
        assertEquals(DiagnosticCode.SEM_OVERRIDE_SIGNATURE, first(checkFails("class C {\n    override func toString(): Int {\n        return 1\n    }\n}\n")).code());
    }

    @Test
    public void overrideToStringWithParameterIsRejected() {
        assertEquals(DiagnosticCode.SEM_OVERRIDE_SIGNATURE, first(checkFails("class C {\n    override func toString(x: Int): String {\n        return \"c\"\n    }\n}\n")).code());
    }

    @Test
    public void propertyNamedToStringIsRejected() {
        assertEquals(DiagnosticCode.SEM_RESERVED_MEMBER, first(checkFails("class C {\n    val toString: String = \"c\"\n}\n")).code());
    }

    @Test
    public void interfaceMemberNamedToStringIsRejected() {
        assertEquals(DiagnosticCode.SEM_RESERVED_MEMBER, first(checkFails("interface Named {\n    func toString(): String\n}\n")).code());
    }

    @Test
    public void toStringAsAValueIsRejected() {
        assertEquals(DiagnosticCode.TYPE_FUNCTION_AS_VALUE, first(checkFails("func f(x: Any): Any {\n    return x.toString\n}\n")).code());
    }
}
