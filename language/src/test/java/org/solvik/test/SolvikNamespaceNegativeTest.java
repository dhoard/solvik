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

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.solvik.diagnostic.Diagnostic;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.parser.IncludeResolutionResult;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;

/**
 * Negative name-resolution tests for module-qualified references (docs/LANGUAGE_SPEC.md section 20).
 * A qualified reference must resolve to a module member, and a module member that is not a value
 * (class, interface, enum, function) is rejected with the matching diagnostic instead of being
 * silently typed. These pin the classification of each qualified read and call shape, including the
 * unknown-module, unknown-name, and unknown-variant branches.
 */
public final class SolvikNamespaceNegativeTest {

    private static final String LIBRARY = """
            module my_lib
            class Thing {
            }
            interface Contract {
            }
            enum Color {
                Red
                Green
            }
            func hello(): Integer {
                return 1
            }
            """;

    private static DiagnosticCode firstError(String body) {
        return firstError(analyze(body));
    }

    private static DiagnosticCode firstError(SemanticResult result) {
        assertThat(result.isSuccess()).as("analysis must fail but produced no diagnostics").isFalse();
        assertThat(result.program().isEmpty()).as("failed analysis must expose no program").isTrue();
        return result.diagnostics().all().stream()
                .filter(Diagnostic::isError)
                .map(Diagnostic::code)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no error diagnostic: " + result.diagnostics().all()));
    }

    private static SemanticResult analyze(String body) {
        IncludeResolutionResult resolved = VirtualIncludeFiles.resolve("root.sol", Map.of(
                "root.sol", "include \"lib.sol\" alias m\nfunc f() {\n" + body + "\n}\n",
                "lib.sol", LIBRARY));
        assertThat(resolved.isSuccess()).as("resolution must succeed: " + resolved.diagnostics().all()).isTrue();
        return SolvikSemanticAnalyzer.analyze(resolved.requireUnit(), resolved.itemScopes());
    }

    @Test
    public void qualifiedClassReadIsRejected() {
        assertThat(firstError("val x = m::Thing")).isEqualTo(DiagnosticCode.TYPE_CLASS_AS_VALUE);
    }

    @Test
    public void qualifiedInterfaceReadIsRejected() {
        assertThat(firstError("val x = m::Contract")).isEqualTo(DiagnosticCode.TYPE_INTERFACE_AS_VALUE);
    }

    @Test
    public void qualifiedEnumReadIsRejected() {
        assertThat(firstError("val x = m::Color")).isEqualTo(DiagnosticCode.TYPE_ENUM_AS_VALUE);
    }

    @Test
    public void qualifiedFunctionReadIsRejected() {
        assertThat(firstError("val x = m::hello")).isEqualTo(DiagnosticCode.TYPE_FUNCTION_AS_VALUE);
    }

    @Test
    public void qualifiedUnknownReadIsRejected() {
        assertThat(firstError("val x = m::Nope")).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_NAME);
    }

    @Test
    public void qualifiedReadOfANonEnumMemberIsRejected() {
        assertThat(firstError("val x = m::Nope.Bar")).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_NAME);
    }

    @Test
    public void qualifiedCallOfAnEnumIsRejected() {
        assertThat(firstError("m::Color()")).isEqualTo(DiagnosticCode.TYPE_ENUM_AS_VALUE);
    }

    @Test
    public void qualifiedCallOfAnUnknownNameIsRejected() {
        assertThat(firstError("m::Nope()")).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_NAME);
    }

    @Test
    public void qualifiedCallOfAnInterfaceIsRejected() {
        assertThat(firstError("m::Contract()")).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_NAME);
    }

    @Test
    public void qualifiedCallOfAnUnknownVariantIsRejected() {
        assertThat(firstError("m::Color.Nope()")).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_MEMBER);
    }

    @Test
    public void deeplyQualifiedCallIsRejected() {
        assertThat(firstError("m::A.B.C()")).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_NAME);
    }

    @Test
    public void unknownModulePrefixReadIsRejected() {
        assertThat(firstError("val x = nope::Thing")).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_MODULE);
    }

    @Test
    public void qualifiedVariantReadResolves() {
        SemanticResult result = analyze("val color: m::Color = m::Color.Red");
        assertThat(result.isSuccess()).as("expected success but got " + result.diagnostics().all()).isTrue();
    }
}
