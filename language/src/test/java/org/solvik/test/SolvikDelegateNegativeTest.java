/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.solvik.test.SolvikTestSupport.parseOk;

import java.util.List;
import org.junit.Test;
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
        assertEquals(DiagnosticCode.SEM_AMBIGUOUS_DELEGATION, diagnostic.code());
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
        assertEquals(DiagnosticCode.SEM_AMBIGUOUS_DELEGATION, diagnostic.code());
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
        assertEquals(DiagnosticCode.SEM_INVALID_DELEGATE_TYPE, diagnostic.code());
    }

    @Test
    public void aBuiltinTypedDelegateIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class Service {
                    delegate val value: Int = 1
                }
                """));
        assertEquals(DiagnosticCode.SEM_INVALID_DELEGATE_TYPE, diagnostic.code());
    }

    @Test
    public void anUnknownDelegateTypeIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class Service {
                    delegate val repository: Missing
                }
                """));
        assertEquals(DiagnosticCode.RESOL_UNKNOWN_TYPE, diagnostic.code());
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
        assertEquals(DiagnosticCode.SEM_CLASS_REQUIRES_INITIALIZER, diagnostic.code());
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
        assertEquals(DiagnosticCode.TYPE_MISSING_PROPERTY_INITIALIZER, diagnostic.code());
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
        assertEquals(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE, diagnostic.code());
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
        assertEquals(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE, diagnostic.code());
        assertTrue(diagnostic.message(), diagnostic.message().contains("named"));
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
        assertEquals(DiagnosticCode.SEM_DELEGATE_SIGNATURE, diagnostic.code());
    }

    @Test
    public void aDelegateMemberWithANonCovariantReturnIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                interface Producer {
                    func get(): String
                }
                interface AnyProducer {
                    func get(): Object
                }
                class Service implements Producer {
                    delegate val producer: AnyProducer

                    Service(producer: AnyProducer) {
                        this.producer = producer
                    }
                }
                """));
        assertEquals(DiagnosticCode.SEM_DELEGATE_SIGNATURE, diagnostic.code());
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
        assertEquals(DiagnosticCode.RESOL_DUPLICATE_NAME, diagnostic.code());
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
        assertEquals(DiagnosticCode.RESOL_DUPLICATE_NAME, diagnostic.code());
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
        assertEquals(DiagnosticCode.TYPE_MISMATCH, diagnostic.code());
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
        assertTrue(bag.all().stream().anyMatch(d -> d.code() == DiagnosticCode.SEM_AMBIGUOUS_DELEGATION));
    }
}
