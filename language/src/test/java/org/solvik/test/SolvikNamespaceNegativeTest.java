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
 * (class, interface, enum) is rejected with the matching diagnostic instead of being silently typed.
 * These pin the classification of each qualified read and call shape, including the unknown-module,
 * unknown-name, and unknown-variant branches. A callable is a declaration rather than a value, so a
 * qualified call is accepted while a qualified bare function name used as a value is rejected.
 */
public final class SolvikNamespaceNegativeTest {

    private static final String LIBRARY = """
            module my_lib {
                class Thing {
                }
                class Holder {
                    var static mutable count: Integer = 0
                    var static limit: Integer = 1
                    method static bump(): Integer {
                        Holder.count = Holder.count + 1
                        return Holder.count
                    }
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
                "root.sol", "include \"lib.sol\"\nfunc f() {\n" + body + "\n}\n",
                "lib.sol", LIBRARY));
        assertThat(resolved.isSuccess()).as("resolution must succeed: " + resolved.diagnostics().all()).isTrue();
        return SolvikSemanticAnalyzer.analyze(resolved.requireUnit());
    }

    @Test
    public void qualifiedClassReadIsRejected() {
        assertThat(firstError("var x: Any = my_lib::Thing")).isEqualTo(DiagnosticCode.TYPE_CLASS_AS_VALUE);
    }

    @Test
    public void qualifiedInterfaceReadIsRejected() {
        assertThat(firstError("var x: Any = my_lib::Contract")).isEqualTo(DiagnosticCode.TYPE_INTERFACE_AS_VALUE);
    }

    @Test
    public void qualifiedEnumReadIsRejected() {
        assertThat(firstError("var x: Any = my_lib::Color")).isEqualTo(DiagnosticCode.TYPE_ENUM_AS_VALUE);
    }

    /**
     * A qualified function name denotes a declaration, not a value, so reading it as one is rejected
     * exactly as the unqualified form is.
     */
    @Test
    public void qualifiedFunctionReadAsAValueIsRejected() {
        assertThat(firstError("var x: Any = my_lib::hello")).isEqualTo(DiagnosticCode.TYPE_FUNCTION_AS_VALUE);
    }

    @Test
    public void qualifiedUnknownFunctionReadIsRejected() {
        assertThat(firstError("var x: Any = my_lib::nothing")).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_NAME);
    }

    @Test
    public void qualifiedUnknownReadIsRejected() {
        assertThat(firstError("var x: Any = my_lib::Nope")).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_NAME);
    }

    @Test
    public void qualifiedReadOfANonEnumMemberIsRejected() {
        assertThat(firstError("var x: Any = my_lib::Nope.Bar")).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_NAME);
    }

    @Test
    public void qualifiedCallOfAnEnumIsRejected() {
        assertThat(firstError("my_lib::Color()")).isEqualTo(DiagnosticCode.TYPE_ENUM_AS_VALUE);
    }

    @Test
    public void qualifiedCallOfAnUnknownNameIsRejected() {
        assertThat(firstError("my_lib::Nope()")).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_NAME);
    }

    @Test
    public void qualifiedCallOfAnInterfaceIsRejected() {
        assertThat(firstError("my_lib::Contract()")).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_NAME);
    }

    @Test
    public void qualifiedCallOfAnUnknownVariantIsRejected() {
        assertThat(firstError("my_lib::Color.Nope()")).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_MEMBER);
    }

    @Test
    public void deeplyQualifiedCallIsRejected() {
        assertThat(firstError("my_lib::A.B.C()")).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_NAME);
    }

    @Test
    public void qualifiedCallErrorPathsCheckTheirArguments() {
        assertThat(firstError("my_lib::Color(5)")).isEqualTo(DiagnosticCode.TYPE_ENUM_AS_VALUE);
        assertThat(firstError("my_lib::Nope(5)")).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_NAME);
        // The module and the class both resolve, so only the member is unknown; this matches the enum
        // case above, where an unknown variant of a known enum is an unknown member.
        assertThat(firstError("my_lib::Thing.Nope(5)")).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_MEMBER);
        assertThat(firstError("my_lib::A.B.C(5)")).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_NAME);
    }

    @Test
    public void qualifiedCallStillReportsAnInvalidArgument() {
        SemanticResult result = analyze("my_lib::Nope(Byte(300))");
        assertThat(result.isSuccess()).as("analysis must fail").isFalse();
        assertThat(result.diagnostics().all()).extracting(Diagnostic::code)
                .contains(DiagnosticCode.TYPE_CONVERSION_OUT_OF_RANGE, DiagnosticCode.RESOL_UNKNOWN_NAME);
    }

    @Test
    public void unknownModulePrefixReadIsRejected() {
        assertThat(firstError("var x: Any = nope::Thing")).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_MODULE);
    }

    @Test
    public void qualifiedVariantReadResolves() {
        SemanticResult result = analyze("var color: my_lib::Color = my_lib::Color.Red");
        assertThat(result.isSuccess()).as("expected success but got " + result.diagnostics().all()).isTrue();
    }

    // ---------------------------------------------------------------------------------------------
    // Qualified static members (docs/LANGUAGE_SPEC.md section 7)
    // ---------------------------------------------------------------------------------------------

    @Test
    public void qualifiedStaticMemberReadResolves() {
        SemanticResult result = analyze("var n: Integer = my_lib::Holder.count\nvar limit: Integer = my_lib::Holder.limit\nvar bumped: Integer = my_lib::Holder.bump()");
        assertThat(result.isSuccess()).as("expected success but got " + result.diagnostics().all()).isTrue();
    }

    @Test
    public void qualifiedStaticPropertyAssignmentResolves() {
        SemanticResult result = analyze("my_lib::Holder.count = 4");
        assertThat(result.isSuccess()).as("expected success but got " + result.diagnostics().all()).isTrue();
    }

    @Test
    public void anAllNamespaceQualifiedStaticMemberNameIsRejected() {
        // Only `prefix::Class.member` names a static member. `prefix::Class::member` is not a qualified
        // name the language defines, and lowering has no node for a bare namespace chain used as a
        // value, so it must be refused by analysis rather than escaping as a host failure.
        assertThat(firstError("var n: Integer = my_lib::Holder::count")).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_NAME);
    }

    @Test
    public void anAllNamespaceQualifiedStaticMemberCallIsRejected() {
        assertThat(firstError("my_lib::Holder::bump()")).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_NAME);
    }

    @Test
    public void anAllNamespaceQualifiedVariantReadIsRejected() {
        // The same rule guards the enum variant path, which shares the qualified read.
        assertThat(firstError("var color: my_lib::Color = my_lib::Color::Red")).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_NAME);
    }

    @Test
    public void anAllNamespaceQualifiedVariantCallIsRejected() {
        assertThat(firstError("my_lib::Color::Red()")).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_NAME);
    }

    @Test
    public void aQualifiedStaticMemberDoesNotReachAnInstanceMember() {
        assertThat(firstError("var n: Integer = my_lib::Thing.nope")).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_MEMBER);
    }
}
