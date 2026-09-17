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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.Test;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.diagnostic.Diagnostic;
import org.solvik.diagnostic.DiagnosticBag;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;

/**
 * Callable arity tests (docs/LANGUAGE_SPEC.md section 6). Arity is a semantic property: the parser
 * accepts any number of arguments and static analysis validates the count of every statically
 * resolved callable before argument types are considered. Invalid programs must fail during
 * semantic analysis, never by executing and reaching a runtime check.
 */
public final class SolvikAritySemanticTest {

    // ------------------------------------------------------------------ zero-argument callables

    @Test
    public void zeroArgumentFunctionCalledCorrectlyExecutes() {
        assertEquals("0\n", runMain("""
                func zero(): Int {
                    return 0
                }
                println(zero())
                """));
    }

    @Test
    public void zeroArgumentFunctionCalledWithOneArgumentIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                func zero(): Int {
                    return 0
                }
                func f(): Int {
                    return zero(1)
                }
                """));
        assertEquals(DiagnosticCode.TYPE_ARITY_MISMATCH, diagnostic.code());
        assertEquals("'zero' expects 0 arguments but 1 was provided", diagnostic.message());
    }

    // ------------------------------------------------------------------ one-argument callables

    @Test
    public void oneArgumentFunctionCalledCorrectlyExecutes() {
        assertEquals("5\n", runMain("""
                func echo(value: Int): Int {
                    return value
                }
                println(echo(5))
                """));
    }

    @Test
    public void oneArgumentFunctionCalledWithNoArgumentsIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                func echo(value: Int): Int {
                    return value
                }
                func f(): Int {
                    return echo()
                }
                """));
        assertEquals(DiagnosticCode.TYPE_ARITY_MISMATCH, diagnostic.code());
        assertEquals("'echo' expects 1 argument but 0 were provided", diagnostic.message());
    }

    @Test
    public void oneArgumentFunctionCalledWithTwoArgumentsIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                func echo(value: Int): Int {
                    return value
                }
                func f(): Int {
                    return echo(1, 2)
                }
                """));
        assertEquals(DiagnosticCode.TYPE_ARITY_MISMATCH, diagnostic.code());
    }

    // ------------------------------------------------------------------ multi-argument callables

    @Test
    public void multiArgumentFunctionCalledCorrectlyExecutes() {
        assertEquals("3\n", runMain("""
                func add(a: Int, b: Int): Int {
                    return a + b
                }
                println(add(1, 2))
                """));
    }

    @Test
    public void multiArgumentFunctionCalledWithTooFewArgumentsIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                func add(a: Int, b: Int): Int {
                    return a + b
                }
                func f(): Int {
                    return add(1)
                }
                """));
        assertEquals(DiagnosticCode.TYPE_ARITY_MISMATCH, diagnostic.code());
        assertEquals("'add' expects 2 arguments but 1 was provided", diagnostic.message());
    }

    @Test
    public void multiArgumentFunctionCalledWithTooManyArgumentsIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                func add(a: Int, b: Int): Int {
                    return a + b
                }
                func f(): Int {
                    return add(1, 2, 3)
                }
                """));
        assertEquals(DiagnosticCode.TYPE_ARITY_MISMATCH, diagnostic.code());
        assertEquals("'add' expects 2 arguments but 3 were provided", diagnostic.message());
    }

    // ------------------------------------------------------------------ instance methods

    @Test
    public void instanceMethodCorrectArityExecutes() {
        assertEquals("2\n", runMain("""
                class Counter {
                    var value: Int = 0

                    func bump(by: Int): Unit {
                        this.value = this.value + by
                    }
                }
                val counter = Counter()
                counter.bump(2)
                println(counter.value)
                """));
    }

    @Test
    public void instanceMethodWithTooFewArgumentsIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class Counter {
                    var value: Int = 0

                    func bump(by: Int): Unit {
                        this.value = this.value + by
                    }
                }
                func f(counter: Counter): Unit {
                    counter.bump()
                }
                """));
        assertEquals(DiagnosticCode.TYPE_ARITY_MISMATCH, diagnostic.code());
        assertEquals("'Counter.bump' expects 1 argument but 0 were provided", diagnostic.message());
    }

    @Test
    public void instanceMethodWithTooManyArgumentsIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class Counter {
                    var value: Int = 0

                    func bump(by: Int): Unit {
                        this.value = this.value + by
                    }
                }
                func f(counter: Counter): Unit {
                    counter.bump(1, 2)
                }
                """));
        assertEquals(DiagnosticCode.TYPE_ARITY_MISMATCH, diagnostic.code());
        assertEquals("'Counter.bump' expects 1 argument but 2 were provided", diagnostic.message());
    }

    @Test
    public void implicitThisMethodCallWithWrongArityIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class Counter {
                    var value: Int = 0

                    func bump(by: Int): Unit {
                        this.value = this.value + by
                    }

                    func broken(): Unit {
                        this.bump(1, 2)
                    }
                }
                """));
        assertEquals(DiagnosticCode.TYPE_ARITY_MISMATCH, diagnostic.code());
        assertEquals("'Counter.bump' expects 1 argument but 2 were provided", diagnostic.message());
    }

    @Test
    public void superMethodCallWithWrongArityIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                open class Base {
                    open func scale(by: Int): Int {
                        return by
                    }
                }
                class Derived extends Base {
                    override func scale(by: Int): Int {
                        return super.scale()
                    }
                }
                """));
        assertEquals(DiagnosticCode.TYPE_ARITY_MISMATCH, diagnostic.code());
        assertEquals("'Base.scale' expects 1 argument but 0 were provided", diagnostic.message());
    }

    // ------------------------------------------------------------------ constructors

    @Test
    public void constructorCorrectArityExecutes() {
        assertEquals("Doug\n", runMain("""
                class User {
                    val name: String

                    User(name: String) {
                        this.name = name
                    }
                }
                val user = User("Doug")
                println(user.name)
                """));
    }

    @Test
    public void constructorWithTooFewArgumentsIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class User {
                    val name: String

                    User(name: String) {
                        this.name = name
                    }
                }
                func f(): User {
                    return User()
                }
                """));
        assertEquals(DiagnosticCode.TYPE_ARITY_MISMATCH, diagnostic.code());
        assertEquals("'User' expects 1 argument but 0 were provided", diagnostic.message());
    }

    @Test
    public void constructorWithTooManyArgumentsIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class User {
                    val name: String

                    User(name: String) {
                        this.name = name
                    }
                }
                func f(): User {
                    return User("Doug", 55, true)
                }
                """));
        assertEquals(DiagnosticCode.TYPE_ARITY_MISMATCH, diagnostic.code());
        assertEquals("'User' expects 1 argument but 3 were provided", diagnostic.message());
    }

    // ------------------------------------------------------------------ built-in callables

    @Test
    public void builtinPrintlnCorrectArityExecutes() {
        assertEquals("hello\n", runMain("""
                println("hello")
                """));
    }

    @Test
    public void builtinPrintlnWithNoArgumentsIsRejected() {
        assertEquals(DiagnosticCode.TYPE_ARITY_MISMATCH, first(checkFails("""
                func f(): Unit {
                    println()
                }
                """)).code());
    }

    @Test
    public void builtinPrintlnWithTwoArgumentsIsRejected() {
        assertEquals(DiagnosticCode.TYPE_ARITY_MISMATCH, first(checkFails("""
                func f(): Unit {
                    println("a", "b")
                }
                """)).code());
    }

    @Test
    public void builtinExitCorrectArityIsAccepted() {
        assertTrue(analyze("""
                func f(): Unit {
                    exit(0)
                }
                """).isSuccess());
    }

    @Test
    public void builtinExitWithWrongArityIsRejected() {
        assertEquals(DiagnosticCode.TYPE_ARITY_MISMATCH, first(checkFails("""
                func f(): Unit {
                    exit()
                }
                """)).code());
        assertEquals(DiagnosticCode.TYPE_ARITY_MISMATCH, first(checkFails("""
                func f(): Unit {
                    exit(0, 1)
                }
                """)).code());
    }

    @Test
    public void builtinToStringWithWrongArityIsRejected() {
        assertEquals(DiagnosticCode.TYPE_ARITY_MISMATCH, first(checkFails("""
                func f(x: Any): String {
                    return x.toString(1)
                }
                """)).code());
    }

    // ------------------------------------------------------------------ interface methods

    @Test
    public void interfaceMethodCorrectArityExecutes() {
        assertEquals("hi Doug\n", runMain("""
                interface Greeter {
                    func greet(name: String): String
                }
                class Friendly implements Greeter {
                    func greet(name: String): String {
                        return "hi " .. name
                    }
                }
                func use(greeter: Greeter): String {
                    return greeter.greet("Doug")
                }
                println(use(Friendly()))
                """));
    }

    @Test
    public void interfaceMethodWithWrongArityIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                interface Greeter {
                    func greet(name: String): String
                }
                func use(greeter: Greeter): String {
                    return greeter.greet()
                }
                """));
        assertEquals(DiagnosticCode.TYPE_ARITY_MISMATCH, diagnostic.code());
        assertEquals("'Greeter.greet' expects 1 argument but 0 were provided", diagnostic.message());
    }

    // ------------------------------------------------------------------ generic callables

    @Test
    public void genericFunctionCorrectArityExecutes() {
        assertEquals("7\n", runMain("""
                func identity<T>(value: T): T {
                    return value
                }
                println(identity(7))
                """));
    }

    @Test
    public void genericFunctionWithWrongArityIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                func identity<T>(value: T): T {
                    return value
                }
                func f(): Int {
                    return identity(1, 2)
                }
                """));
        assertEquals(DiagnosticCode.TYPE_ARITY_MISMATCH, diagnostic.code());
        assertEquals("'identity' expects 1 argument but 2 were provided", diagnostic.message());
    }

    @Test
    public void genericConstructorWithWrongArityIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class Box<T> {
                    var value: T

                    Box(value: T) {
                        this.value = value
                    }
                }
                func f(): Box<Int> {
                    return Box()
                }
                """));
        assertEquals(DiagnosticCode.TYPE_ARITY_MISMATCH, diagnostic.code());
        assertEquals("'Box' expects 1 argument but 0 were provided", diagnostic.message());
    }

    // ------------------------------------------------------------------ ordering and diagnostics

    @Test
    public void arityErrorSuppressesArgumentTypeErrors() {
        DiagnosticBag bag = checkFails("""
                func add(a: Int, b: Int): Int {
                    return a + b
                }
                func f(): Int {
                    return add("wrong", true, 3)
                }
                """);
        assertEquals(1, bag.all().size());
        assertEquals(DiagnosticCode.TYPE_ARITY_MISMATCH, first(bag).code());
        assertNoCode(bag, DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void wrongCountWithWrongTypesReportsOnlyArity() {
        DiagnosticBag bag = checkFails("""
                func echo(value: Int): Int {
                    return value
                }
                func f(): Int {
                    return echo("wrong", true)
                }
                """);
        assertEquals(1, bag.all().size());
        assertEquals(DiagnosticCode.TYPE_ARITY_MISMATCH, first(bag).code());
        assertNoCode(bag, DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void correctArityStillReportsArgumentTypeErrors() {
        // A one-argument call with one wrong-typed argument has correct arity, so the type analysis
        // path still reports the argument type rather than an arity error.
        Diagnostic diagnostic = first(checkFails("""
                func echo(value: Int): Int {
                    return value
                }
                func f(): Int {
                    return echo("wrong")
                }
                """));
        assertEquals(DiagnosticCode.TYPE_MISMATCH, diagnostic.code());
    }

    // ------------------------------------------------------------------ parser responsibility

    @Test
    public void parserAcceptsAnyArgumentCount() {
        // The parser must not inspect the declaration of foo: every syntactic call is legal.
        parseOk("arity.sol", """
                func foo(a: Int, b: Int): Unit {
                }
                func f(): Unit {
                    foo()
                    foo(1)
                    foo(1, 2)
                    foo(1, 2, 3)
                }
                """);
    }

    // ------------------------------------------------------------------ helpers

    private static DiagnosticBag checkFails(String text) {
        SemanticResult result = analyze(text);
        assertFalse("analysis must fail: " + text, result.isSuccess());
        assertTrue("failed analysis must expose no program", result.program().isEmpty());
        assertTrue("failed analysis must carry diagnostics", result.diagnostics().hasErrors());
        for (Diagnostic diagnostic : result.diagnostics().all()) {
            assertTrue("span within source bounds: " + diagnostic.span(), diagnostic.span().endOffset() <= text.length());
        }
        return result.diagnostics();
    }

    private static SemanticResult analyze(String text) {
        CompilationUnitNode unit = parseOk("arity.sol", text);
        return SolvikSemanticAnalyzer.analyze(unit);
    }

    private static Diagnostic first(DiagnosticBag bag) {
        List<Diagnostic> all = bag.all();
        assertFalse(all.isEmpty());
        return all.get(0);
    }

    private static void assertNoCode(DiagnosticBag bag, DiagnosticCode code) {
        for (Diagnostic diagnostic : bag.all()) {
            assertFalse("unexpected " + code + ": " + diagnostic, diagnostic.code() == code);
        }
    }

    private static Source build(Source.Builder builder) {
        try {
            return builder.build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String runMain(String source) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            context.eval(build(Source.newBuilder("solvik", source, "test.sol")));
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}
