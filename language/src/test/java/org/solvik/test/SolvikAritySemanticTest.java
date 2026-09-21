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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;
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
        assertThat(runMain("""
                func zero(): Integer {
                    return 0
                }
                println(zero())
                """)).isEqualTo("0\n");
    }

    @Test
    public void zeroArgumentFunctionCalledWithOneArgumentIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                func zero(): Integer {
                    return 0
                }
                func f(): Integer {
                    return zero(1)
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_ARITY_MISMATCH);
        assertThat(diagnostic.message()).isEqualTo("'zero' expects 0 arguments but 1 was provided");
    }

    // ------------------------------------------------------------------ one-argument callables

    @Test
    public void oneArgumentFunctionCalledCorrectlyExecutes() {
        assertThat(runMain("""
                func echo(value: Integer): Integer {
                    return value
                }
                println(echo(5))
                """)).isEqualTo("5\n");
    }

    @Test
    public void oneArgumentFunctionCalledWithNoArgumentsIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                func echo(value: Integer): Integer {
                    return value
                }
                func f(): Integer {
                    return echo()
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_ARITY_MISMATCH);
        assertThat(diagnostic.message()).isEqualTo("'echo' expects 1 argument but 0 were provided");
    }

    @Test
    public void oneArgumentFunctionCalledWithTwoArgumentsIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                func echo(value: Integer): Integer {
                    return value
                }
                func f(): Integer {
                    return echo(1, 2)
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_ARITY_MISMATCH);
    }

    // ------------------------------------------------------------------ multi-argument callables

    @Test
    public void multiArgumentFunctionCalledCorrectlyExecutes() {
        assertThat(runMain("""
                func add(a: Integer, b: Integer): Integer {
                    return a + b
                }
                println(add(1, 2))
                """)).isEqualTo("3\n");
    }

    @Test
    public void multiArgumentFunctionCalledWithTooFewArgumentsIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                func add(a: Integer, b: Integer): Integer {
                    return a + b
                }
                func f(): Integer {
                    return add(1)
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_ARITY_MISMATCH);
        assertThat(diagnostic.message()).isEqualTo("'add' expects 2 arguments but 1 was provided");
    }

    @Test
    public void multiArgumentFunctionCalledWithTooManyArgumentsIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                func add(a: Integer, b: Integer): Integer {
                    return a + b
                }
                func f(): Integer {
                    return add(1, 2, 3)
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_ARITY_MISMATCH);
        assertThat(diagnostic.message()).isEqualTo("'add' expects 2 arguments but 3 were provided");
    }

    // ------------------------------------------------------------------ instance methods

    @Test
    public void instanceMethodCorrectArityExecutes() {
        assertThat(runMain("""
                class Counter {
                    var value: Integer = 0

                    func bump(by: Integer): Unit {
                        this.value = this.value + by
                    }
                }
                val counter = Counter()
                counter.bump(2)
                println(counter.value)
                """)).isEqualTo("2\n");
    }

    @Test
    public void instanceMethodWithTooFewArgumentsIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class Counter {
                    var value: Integer = 0

                    func bump(by: Integer): Unit {
                        this.value = this.value + by
                    }
                }
                func f(counter: Counter): Unit {
                    counter.bump()
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_ARITY_MISMATCH);
        assertThat(diagnostic.message()).isEqualTo("'Counter.bump' expects 1 argument but 0 were provided");
    }

    @Test
    public void instanceMethodWithTooManyArgumentsIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class Counter {
                    var value: Integer = 0

                    func bump(by: Integer): Unit {
                        this.value = this.value + by
                    }
                }
                func f(counter: Counter): Unit {
                    counter.bump(1, 2)
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_ARITY_MISMATCH);
        assertThat(diagnostic.message()).isEqualTo("'Counter.bump' expects 1 argument but 2 were provided");
    }

    @Test
    public void implicitThisMethodCallWithWrongArityIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class Counter {
                    var value: Integer = 0

                    func bump(by: Integer): Unit {
                        this.value = this.value + by
                    }

                    func broken(): Unit {
                        this.bump(1, 2)
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_ARITY_MISMATCH);
        assertThat(diagnostic.message()).isEqualTo("'Counter.bump' expects 1 argument but 2 were provided");
    }

    @Test
    public void superMethodCallWithWrongArityIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                open class Base {
                    open func scale(by: Integer): Integer {
                        return by
                    }
                }
                class Derived extends Base {
                    override func scale(by: Integer): Integer {
                        return super.scale()
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_ARITY_MISMATCH);
        assertThat(diagnostic.message()).isEqualTo("'Base.scale' expects 1 argument but 0 were provided");
    }

    // ------------------------------------------------------------------ constructors

    @Test
    public void constructorCorrectArityExecutes() {
        assertThat(runMain("""
                class User {
                    val name: String

                    User(name: String) {
                        this.name = name
                    }
                }
                val user = User("Doug")
                println(user.name)
                """)).isEqualTo("Doug\n");
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
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_ARITY_MISMATCH);
        assertThat(diagnostic.message()).isEqualTo("'User' expects 1 argument but 0 were provided");
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
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_ARITY_MISMATCH);
        assertThat(diagnostic.message()).isEqualTo("'User' expects 1 argument but 3 were provided");
    }

    // ------------------------------------------------------------------ built-in callables

    @Test
    public void builtinPrintlnCorrectArityExecutes() {
        assertThat(runMain("""
                println("hello")
                """)).isEqualTo("hello\n");
    }

    @Test
    public void builtinPrintlnWithNoArgumentsIsRejected() {
        assertThat(first(checkFails("""
                func f(): Unit {
                    println()
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_ARITY_MISMATCH);
    }

    @Test
    public void builtinPrintlnWithTwoArgumentsIsRejected() {
        assertThat(first(checkFails("""
                func f(): Unit {
                    println("a", "b")
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_ARITY_MISMATCH);
    }

    @Test
    public void builtinExitCorrectArityIsAccepted() {
        assertThat(analyze("""
                func f(): Unit {
                    exit(0)
                }
                """).isSuccess()).isTrue();
    }

    @Test
    public void builtinExitWithWrongArityIsRejected() {
        assertThat(first(checkFails("""
                func f(): Unit {
                    exit()
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_ARITY_MISMATCH);
        assertThat(first(checkFails("""
                func f(): Unit {
                    exit(0, 1)
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_ARITY_MISMATCH);
    }

    @Test
    public void builtinToStringWithWrongArityIsRejected() {
        assertThat(first(checkFails("""
                func f(x: Any): String {
                    return x.toString(1)
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_ARITY_MISMATCH);
    }

    // ------------------------------------------------------------------ interface methods

    @Test
    public void interfaceMethodCorrectArityExecutes() {
        assertThat(runMain("""
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
                """)).isEqualTo("hi Doug\n");
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
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_ARITY_MISMATCH);
        assertThat(diagnostic.message()).isEqualTo("'Greeter.greet' expects 1 argument but 0 were provided");
    }

    // ------------------------------------------------------------------ generic callables

    @Test
    public void genericFunctionCorrectArityExecutes() {
        assertThat(runMain("""
                func identity<T>(value: T): T {
                    return value
                }
                println(identity(7))
                """)).isEqualTo("7\n");
    }

    @Test
    public void genericFunctionWithWrongArityIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                func identity<T>(value: T): T {
                    return value
                }
                func f(): Integer {
                    return identity(1, 2)
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_ARITY_MISMATCH);
        assertThat(diagnostic.message()).isEqualTo("'identity' expects 1 argument but 2 were provided");
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
                func f(): Box<Integer> {
                    return Box()
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_ARITY_MISMATCH);
        assertThat(diagnostic.message()).isEqualTo("'Box' expects 1 argument but 0 were provided");
    }

    // ------------------------------------------------------------------ ordering and diagnostics

    @Test
    public void arityErrorSuppressesArgumentTypeErrors() {
        DiagnosticBag bag = checkFails("""
                func add(a: Integer, b: Integer): Integer {
                    return a + b
                }
                func f(): Integer {
                    return add("wrong", true, 3)
                }
                """);
        assertThat(bag.all().size()).isEqualTo(1);
        assertThat(first(bag).code()).isEqualTo(DiagnosticCode.TYPE_ARITY_MISMATCH);
        assertNoCode(bag, DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void wrongCountWithWrongTypesReportsOnlyArity() {
        DiagnosticBag bag = checkFails("""
                func echo(value: Integer): Integer {
                    return value
                }
                func f(): Integer {
                    return echo("wrong", true)
                }
                """);
        assertThat(bag.all().size()).isEqualTo(1);
        assertThat(first(bag).code()).isEqualTo(DiagnosticCode.TYPE_ARITY_MISMATCH);
        assertNoCode(bag, DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void correctArityStillReportsArgumentTypeErrors() {
        // A one-argument call with one wrong-typed argument has correct arity, so the type analysis
        // path still reports the argument type rather than an arity error.
        Diagnostic diagnostic = first(checkFails("""
                func echo(value: Integer): Integer {
                    return value
                }
                func f(): Integer {
                    return echo("wrong")
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    // ------------------------------------------------------------------ parser responsibility

    @Test
    public void parserAcceptsAnyArgumentCount() {
        // The parser must not inspect the declaration of foo: every syntactic call is legal.
        parseOk("arity.sol", """
                func foo(a: Integer, b: Integer): Unit {
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
        assertThat(result.isSuccess()).as("analysis must fail: " + text).isFalse();
        assertThat(result.program().isEmpty()).as("failed analysis must expose no program").isTrue();
        assertThat(result.diagnostics().hasErrors()).as("failed analysis must carry diagnostics").isTrue();
        for (Diagnostic diagnostic : result.diagnostics().all()) {
            assertThat(diagnostic.span().endOffset() <= text.length()).as("span within source bounds: " + diagnostic.span()).isTrue();
        }
        return result.diagnostics();
    }

    private static SemanticResult analyze(String text) {
        CompilationUnitNode unit = parseOk("arity.sol", text);
        return SolvikSemanticAnalyzer.analyze(unit);
    }

    private static Diagnostic first(DiagnosticBag bag) {
        List<Diagnostic> all = bag.all();
        assertThat(all.isEmpty()).isFalse();
        return all.get(0);
    }

    private static void assertNoCode(DiagnosticBag bag, DiagnosticCode code) {
        for (Diagnostic diagnostic : bag.all()) {
            assertThat(diagnostic.code() == code).as("unexpected " + code + ": " + diagnostic).isFalse();
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
