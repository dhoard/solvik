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

import java.util.List;
import org.junit.jupiter.api.Test;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.diagnostic.Diagnostic;
import org.solvik.diagnostic.DiagnosticBag;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;

/**
 * Negative Phase 9 semantic tests: an ambiguous delegation, a delegate whose declared type is not an
 * interface, a delegate that cannot supply a requirement because its signature does not conform, and
 * a delegate that is not definitely initialized each produce a source-located diagnostic and no typed
 * result.
 */
public final class SolvikDelegateNegativeTest {

    private static DiagnosticBag checkFails(String text) {
        CompilationUnitNode unit = parseOk("delegateneg.sol", text);
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

    @Test
    public void twoDelegatesSupplyingOneMemberAreAmbiguous() {
        Diagnostic diagnostic = first(checkFails("""
                interface PrinterA {
                    func print(): Unit
                }
                interface PrinterB {
                    func print(): Unit
                }
                class X implements PrinterA, PrinterB {
                    delegate val a: PrinterA
                    delegate val b: PrinterB

                    X(a: PrinterA, b: PrinterB) {
                        this.a = a
                        this.b = b
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_AMBIGUOUS_DELEGATION);
    }

    @Test
    public void twoDelegatesOfTheSameInterfaceTypeAreAmbiguous() {
        Diagnostic diagnostic = first(checkFails("""
                interface Printer {
                    func print(): Unit
                }
                class X implements Printer {
                    delegate val a: Printer
                    delegate val b: Printer

                    X(a: Printer, b: Printer) {
                        this.a = a
                        this.b = b
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_AMBIGUOUS_DELEGATION);
    }

    @Test
    public void aClassTypedDelegateIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class MemoryRepository {
                }
                class Service {
                    delegate val repository: MemoryRepository = MemoryRepository()
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_INVALID_DELEGATE_TYPE);
    }

    @Test
    public void aBuiltinTypedDelegateIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class Service {
                    delegate val value: Int = 1
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_INVALID_DELEGATE_TYPE);
    }

    @Test
    public void anUnknownDelegateTypeIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class Service {
                    delegate val repository: Missing
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_TYPE);
    }

    @Test
    public void aDelegateWithoutInitializerRequiresAConstructor() {
        Diagnostic diagnostic = first(checkFails("""
                interface Named {
                    func name(): String
                }
                class Service implements Named {
                    delegate val named: Named
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_CLASS_REQUIRES_INITIALIZER);
    }

    @Test
    public void aDelegateNotAssignedInConstructorIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                interface Named {
                    func name(): String
                }
                class Service implements Named {
                    delegate val named: Named

                    Service() {
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_MISSING_PROPERTY_INITIALIZER);
    }

    @Test
    public void aDelegateAssignedTwiceInConstructorIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                interface Named {
                    func name(): String
                }
                class Service implements Named {
                    delegate val named: Named

                    Service(named: Named) {
                        this.named = named
                        this.named = named
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE);
    }

    @Test
    public void aDelegateWrittenAfterConstructionIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                interface Named {
                    func name(): String
                }
                class Service implements Named {
                    delegate val named: Named

                    Service(named: Named) {
                        this.named = named
                    }

                    func replace(other: Named): Unit {
                        this.named = other
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE);
        assertThat(diagnostic.message().contains("named")).as(diagnostic.message()).isTrue();
    }

    @Test
    public void aDelegateMemberWithWrongParameterTypesIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                interface Sink {
                    func put(value: Int): Unit
                }
                interface StringSink {
                    func put(value: String): Unit
                }
                class Service implements Sink {
                    delegate val sink: StringSink

                    Service(sink: StringSink) {
                        this.sink = sink
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_DELEGATE_SIGNATURE);
    }

    @Test
    public void aDelegateMemberWithANonCovariantReturnIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                interface Producer {
                    func get(): String
                }
                interface AnyProducer {
                    func get(): Any
                }
                class Service implements Producer {
                    delegate val producer: AnyProducer

                    Service(producer: AnyProducer) {
                        this.producer = producer
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_DELEGATE_SIGNATURE);
    }

    @Test
    public void aDelegateNameCollidingWithAPropertyIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                interface Named {
                    func name(): String
                }
                class Service implements Named {
                    val named: Int = 1
                    delegate val named: Named = Memory()
                }
                class Memory implements Named {
                    func name(): String {
                        return "x"
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_DUPLICATE_NAME);
    }

    @Test
    public void aDelegateNameCollidingWithAMethodIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                interface Named {
                    func name(): String
                }
                class Service implements Named {
                    delegate val run: Named

                    Service(run: Named) {
                        this.run = run
                    }

                    func run(): Unit {
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_DUPLICATE_NAME);
    }

    @Test
    public void aDelegateInitializerWithTheWrongTypeIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                interface Named {
                    func name(): String
                }
                class Service implements Named {
                    delegate val named: Named = 1
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void ambiguousDelegationSuppressesEveryDiagnosticResult() {
        DiagnosticBag bag = checkFails("""
                interface PrinterA {
                    func print(): Unit
                }
                interface PrinterB {
                    func print(): Unit
                }
                class X implements PrinterA, PrinterB {
                    delegate val a: PrinterA
                    delegate val b: PrinterB

                    X(a: PrinterA, b: PrinterB) {
                        this.a = a
                        this.b = b
                    }
                }
                    println("unreachable")
                """);
        assertThat(bag.all().stream().anyMatch(d -> d.code() == DiagnosticCode.SEM_AMBIGUOUS_DELEGATION)).isTrue();
    }
}
