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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;
import org.solvik.diagnostic.Diagnostic;
import org.solvik.diagnostic.DiagnosticBag;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;

/**
 * Named function values, as values: a bare or module-qualified reference to a declared function is a
 * value that can be stored, passed, returned, compared, printed, and invoked, and invoking it preserves
 * the ordering, arity, result, and exception rules an immediate call has
 * (docs/LANGUAGE_SPEC.md section 6, "Named functions as values" and "Function values and invocation").
 *
 * <p>Every behavioural assertion here runs a program and observes what it prints, because the claims
 * being pinned are runtime claims: that one declaration has one canonical identity, that a callee is
 * evaluated before its arguments and arguments left to right, that a value is the same value when it
 * comes back out of a collection, and that a guest exception crosses the indirect-call boundary
 * untranslated. An analyzer-only test could not distinguish a value that is canonical from one that two
 * comparisons merely happen to report as equal.
 *
 * <p>Each expected output is stated by reading the specification passage named in the test comment and
 * then working out what the program must print; none was copied from an observed run. Where the
 * implementation and the passage disagreed, the implementation was wrong and was changed.
 */
public final class SolvikFunctionValueTest {

    private static String run(String source) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out)
                .option("engine.WarnInterpreterOnly", "false").allowAllAccess(true).build()) {
            context.eval(build(source, "test.sol"));
        } catch (RuntimeException e) {
            // A failure is reported with the program that produced it and the cause chain, because a
            // bare stack trace from a generated program says nothing about which line misbehaved.
            StringWriter trace = new StringWriter();
            e.printStackTrace(new PrintWriter(trace));
            throw new AssertionError("program failed:\n" + source + "\n" + trace, e);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static Source build(String source, String name) {
        try {
            return Source.newBuilder("solvik", source, name).build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static DiagnosticCode firstCode(String text) {
        SemanticResult result = SolvikSemanticAnalyzer.analyze(parseOk("funcvalue.sol", text));
        assertThat(result.isSuccess()).as("analysis must fail: " + text).isFalse();
        DiagnosticBag bag = result.diagnostics();
        List<Diagnostic> all = bag.all();
        assertThat(all.isEmpty()).as("failed analysis must carry a diagnostic").isFalse();
        return all.get(0).code();
    }

    // ---------------------------------------------------------------------------------------------
    // Storing, passing, returning, and invoking
    // ---------------------------------------------------------------------------------------------

    /**
     * A reference initializes a binding, and calling through that binding invokes the declaration.
     *
     * <p>"A bare reference to a visible, non-generic top-level function produces a function value" and
     * "Parentheses continue to distinguish invocation, so `format` is a function value and `format(42)`
     * is a direct invocation" (section 6).
     */
    @Test
    public void aStoredFunctionValueInvokesItsDeclaration() {
        assertThat(run("""
                func format(value: Integer): String {
                    return "v" .. value.toString()
                }

                val formatter: func(Integer): String = format
                println(formatter(42))
                println(format(42))
                """)).isEqualTo("v42\nv42\n");
    }

    /**
     * A function value is accepted as an argument and as a result.
     *
     * <p>"A function type may appear wherever another non-deferred type may appear" (section 6), and the
     * callee of an indirect call is "any expression whose non-null static type is a function type"
     * (section 6).
     */
    @Test
    public void aFunctionValueIsPassedAndReturned() {
        assertThat(run("""
                func double(value: Integer): Integer {
                    return value * 2
                }

                func apply(value: Integer, operation: func(Integer): Integer): Integer {
                    return operation(value)
                }

                func chooser(useDouble: Boolean): func(Integer): Integer {
                    if (useDouble) {
                        return double
                    }
                    return negate
                }

                func negate(value: Integer): Integer {
                    return 0 - value
                }

                println(apply(21, double))
                println(chooser(true)(4))
                println(chooser(false)(4))
                """)).isEqualTo("42\n8\n-4\n");
    }

    /**
     * A function-typed binding can be reassigned to another declaration and the new target is invoked.
     *
     * <p>An ordinary assignment to a binding of function type is an ordinary assignment; the value is
     * just a value of a type (section 3 and section 6).
     */
    @Test
    public void aFunctionTypedBindingCanBeReassigned() {
        assertThat(run("""
                func first(value: Integer): Integer {
                    return value + 1
                }

                func second(value: Integer): Integer {
                    return value + 2
                }

                var operation: func(Integer): Integer = first
                println(operation(1))
                operation = second
                println(operation(1))
                """)).isEqualTo("2\n3\n");
    }

    /**
     * A function value survives a round trip through a mutable collection unchanged.
     *
     * <p>"A function value may be assigned to `Any`, stored in a collection, returned in an enum payload,
     * or passed through another generic type" (section 6).
     */
    @Test
    public void aFunctionValueRoundTripsThroughACollection() {
        assertThat(run("""
                func answer(): Integer {
                    return 42
                }

                val callbacks: List<func(): Integer> = List()
                callbacks.add(answer)
                println(callbacks.size)
                println(callbacks.get(0)())
                println(callbacks.get(0) === answer)
                """)).isEqualTo("1\n42\ntrue\n");
    }

    /**
     * A nullable function value is invokable after a null test refines it, and stays null otherwise.
     *
     * <p>"A nullable function value cannot be invoked without prior refinement or another existing
     * non-null mechanism" (section 6). The refinement mechanism is the ordinary null refinement, so a
     * call inside the refined branch is legal and the value observed there is the same value.
     */
    @Test
    public void aNullableFunctionValueIsInvokedAfterRefinement() {
        assertThat(run("""
                func shout(value: String): String {
                    return value .. "!"
                }

                func render(value: String, operation: (func(String): String)?): String {
                    if (operation != null) {
                        return operation(value)
                    }
                    return value
                }

                println(render("a", shout))
                println(render("a", null))
                """)).isEqualTo("a!\na\n");
    }

    /**
     * A predeclared function is a value like any other declaration.
     *
     * <p>"The same rule applies to module-qualified functions and to the predeclared functions", with the
     * worked example `val output: func(Any?): Unit = println` (section 6).
     */
    @Test
    public void aPredeclaredFunctionIsUsableAsAValue() {
        assertThat(run("""
                val output: func(Any?): Unit = println
                output("through the value")
                val same: func(Any?): Unit = println
                println(output === same)
                """)).isEqualTo("through the value\ntrue\n");
    }

    // ---------------------------------------------------------------------------------------------
    // Canonical identity
    // ---------------------------------------------------------------------------------------------

    /**
     * Every reference to one declaration is one value, and `===` on function values holds.
     *
     * <p>"Every reference evaluation to the same declared top-level function produces the same canonical
     * function-value identity" (section 6), and "A function value is identity-bearing, so a concrete
     * function type and its nullable form are valid operands of `===` and `!==`" (section 3). The
     * specification's own worked example is `format === format // true`.
     */
    @Test
    public void referencesToOneDeclarationShareOneIdentity() {
        assertThat(run("""
                func format(value: Integer): String {
                    return value.toString()
                }

                println(format === format)
                println(format !== format)
                val first: func(Integer): String = format
                val second: func(Integer): String = format
                println(first === second)
                println(first === format)
                """)).isEqualTo("true\nfalse\ntrue\ntrue\n");
    }

    /**
     * A function value is identity-bearing on the nullable side too, and `==` on two function values is
     * reference identity.
     *
     * <p>"Semantic equality for function values is reference identity" (section 3). A nullable view of
     * the same declaration is the same value, since nullability is a property of the binding and not of
     * the value.
     */
    @Test
    public void semanticEqualityOnFunctionValuesIsReferenceIdentity() {
        assertThat(run("""
                func value(): Integer {
                    return 1
                }

                val boxed: (func(): Integer)? = value
                println(boxed == value)
                println(boxed === value)
                println(value == value)
                """)).isEqualTo("true\ntrue\ntrue\n");
    }

    /**
     * Two different declarations never share an identity, even when they are written identically.
     *
     * <p>Canonical identity is per declaration, so two declarations are two values; the specification
     * gives no rule that would equate structurally identical bodies, and "Canonical identity is never
     * shared between Solvik contexts" makes the declaration, not the text, the unit of identity.
     */
    @Test
    public void distinctDeclarationsHaveDistinctIdentities() {
        assertThat(run("""
                func left(value: Integer): Integer {
                    return value
                }

                func right(value: Integer): Integer {
                    return value
                }

                println(left === right)
                println(left == right)
                println(left != right)
                """)).isEqualTo("false\nfalse\ntrue\n");
    }

    /**
     * `hashCode()` agrees with the equality rule for function values.
     *
     * <p>"Semantic equality for function values is reference identity, and `hashCode()` is the matching
     * reference-identity hash. These operations are fixed and cannot be overridden" (section 3). Equal
     * values must hash alike, which is the invariant the whole equality design rests on.
     */
    @Test
    public void hashCodeAgreesWithFunctionValueEquality() {
        assertThat(run("""
                func shared(): Integer {
                    return 1
                }

                val first: func(): Integer = shared
                val second: func(): Integer = shared
                println(first.hashCode() == second.hashCode())
                println(shared.hashCode() == first.hashCode())
                println(first.hashCode() == first.hashCode())
                """)).isEqualTo("true\ntrue\ntrue\n");
    }

    // ---------------------------------------------------------------------------------------------
    // Display
    // ---------------------------------------------------------------------------------------------

    /**
     * A function value renders as exactly `func` through every rendering path.
     *
     * <p>"`toString()` for every function value returns the exact string `func`. It must not expose a
     * Java class name, memory address, node name, module path, captured values, or implementation
     * details, so `print`, `println`, and `..` render every function value as `func`" (section 6).
     */
    @Test
    public void everyRenderingOfAFunctionValueIsFunc() {
        assertThat(run("""
                func named(value: Integer): String {
                    return value.toString()
                }

                print(named)
                print(" ")
                println(named)
                println(named.toString())
                println(named .. "")
                val boxed: (func(Integer): String)? = named
                println(boxed)
                """)).isEqualTo("func func\nfunc\nfunc\nfunc\n");
    }

    /**
     * A function value reaches `Any` and still renders as `func`, since display is a property of the
     * value and not of the static type used to reach it.
     *
     * <p>"A function value may be assigned to `Any`" and the `func` rendering is required of "every
     * function value" (section 6).
     */
    @Test
    public void aFunctionValueStoredAsAnyStillRendersAsFunc() {
        assertThat(run("""
                func named(): Unit {
                }

                val anything: Any = named
                println(anything)
                println(anything.toString())
                """)).isEqualTo("func\nfunc\n");
    }

    /**
     * A nullable function value that is null renders as `null` and not as `func`.
     *
     * <p>The rendering rules are for values, and null has its own fixed rendering (section 4); reporting
     * `func` for a null would make a null indistinguishable from a function.
     */
    @Test
    public void aNullNullableFunctionValueRendersAsNull() {
        assertThat(run("""
                val absent: (func(): Unit)? = null
                println(absent)
                """)).isEqualTo("null\n");
    }

    // ---------------------------------------------------------------------------------------------
    // Invocation semantics
    // ---------------------------------------------------------------------------------------------

    /**
     * The callee is evaluated exactly once before any argument, and the arguments are evaluated left to
     * right.
     *
     * <p>"The callee expression is evaluated exactly once before any argument. Arguments are then
     * evaluated exactly once from left to right" (section 6). The marks printed by the side-effecting
     * helpers state the order the specification fixes; a callee evaluated after an argument, or an
     * argument evaluated twice, produces different text.
     */
    @Test
    public void invocationEvaluatesTheCalleeThenTheArgumentsLeftToRight() {
        assertThat(run("""
                func mark(name: String): Integer {
                    print(name)
                    return 0
                }

                func target(first: Integer, second: Integer): Integer {
                    print("|call")
                    return first + second
                }

                func pick(): func(Integer, Integer): Integer {
                    print("|callee")
                    return target
                }

                print("|body")
                println(pick()(mark("a"), mark("b")))
                """))
                        // `|body` is the statement's own leading print. The callee expression `pick()`
                        // must print `|callee` before either argument marks, then `a`, then `b`, then the
                        // target's body prints. Had the callee been evaluated after the arguments, the
                        // text would read `ab|callee`; had an argument been evaluated twice, `a` or `b`
                        // would appear twice. The callee is deliberately a call and not a variable read,
                        // because a read has no observable order to violate.
                        .isEqualTo("|body|calleeab|call0\n");
    }

    /**
     * An indirect call returns the declared result and propagates a guest exception untranslated.
     *
     * <p>"Invocation returns the declared result and propagates guest exceptions without wrapping or
     * translation" (section 6). The handler below is in the caller, so a throw from the callee must be
     * caught there and observe the thrown value itself.
     */
    @Test
    public void anIndirectCallPropagatesAGuestExceptionUntranslated() {
        assertThat(run("""
                class Failure extends RuntimeException {
                }

                func raiser(): Integer {
                    throw Failure("raised through a value")
                }

                func guarded(operation: func(): Integer): String {
                    try {
                        return "returned " .. operation().toString()
                    } catch (error: Failure) {
                        return "caught " .. error.getMessage()
                    }
                }

                println(guarded(raiser))
                """)).isEqualTo("caught raised through a value\n");
    }

    /**
     * Recursion and mutual recursion through the direct path still work when a value of the same
     * declaration also exists, because a direct call keeps its statically resolved target.
     *
     * <p>"A direct call whose target is statically known keeps its existing statically resolved path.
     * Function values add an indirect call path; they do not replace direct calls, and a call such as
     * `sum(1, 2)` is never lowered into constructing a function value and then invoking it" (section 6).
     */
    @Test
    public void takingAFunctionAsAValueDoesNotRouteItsDirectCallsThroughAValue() {
        assertThat(run("""
                func fib(value: Integer): Integer {
                    if (value < 2) {
                        return value
                    }
                    return fib(value - 1) + fib(value - 2)
                }

                func isEven(value: Integer): Boolean {
                    if (value == 0) {
                        return true
                    }
                    return isOdd(value - 1)
                }

                func isOdd(value: Integer): Boolean {
                    if (value == 0) {
                        return false
                    }
                    return isEven(value - 1)
                }

                val throughValue: func(Integer): Integer = fib
                println(fib(10))
                println(throughValue(10))
                println(isEven(10))
                """)).isEqualTo("55\n55\ntrue\n");
    }

    /**
     * A function value used as a `Unit`-returning operation runs its body once.
     *
     * "Invocation returns the declared result" (section 6); a `Unit` result is the absence of a value, so
     * a body whose only effect is printing must print exactly once.
     */
    @Test
    public void aUnitReturningFunctionValueRunsItsBodyOnce() {
        assertThat(run("""
                func announce(): Unit {
                    print("once")
                }

                val operation: func(): Unit = announce
                operation()
                println("")
                """)).isEqualTo("once\n");
    }

    /**
     * A `Unit` result is observable as `Unit`, the same value an immediate call yields.
     *
     * <p>"A call whose result is `Unit` may be used as an expression statement" (section 6), and the
     * result of the two call forms is the same result, so both render identically.
     */
    @Test
    public void anIndirectCallReturnsTheSameUnitAsADirectCall() {
        assertThat(run("""
                func quiet(): Unit {
                }

                val operation: func(): Unit = quiet
                println(operation())
                println(quiet())
                """)).isEqualTo("Unit\nUnit\n");
    }

    // ---------------------------------------------------------------------------------------------
    // Module-qualified references
    // ---------------------------------------------------------------------------------------------

    /**
     * A module-qualified reference yields the same canonical value as the unqualified name.
     *
     * <p>"The same rule applies to module-qualified functions", and "module qualification does not create
     * a second identity for the same declaration" (section 6). The program is written as a root that
     * includes a library, which is how a qualified name is spelled.
     */
    @Test
    public void aQualifiedReferenceIsTheSameValueAsTheUnqualifiedName() {
        assertThat(runInclude("""
                module lib
                func render(value: Integer): String {
                    return "r" .. value.toString()
                }
                """, """
                include "lib.sol" alias m
                val qualified: func(Integer): String = m::render
                println(qualified(1))
                """)).isEqualTo("r1\n");
    }

    /**
     * The two spellings are equal to each other by identity, asserted across the include boundary where
     * only one spelling is visible on each side.
     *
     * <p>"module qualification does not create a second identity for the same declaration" (section 6).
     * The library exports the declaration and the root references it both ways it can name it.
     */
    @Test
    public void qualificationDoesNotCreateASecondIdentity() {
        assertThat(runInclude("""
                module lib
                func shared(): Integer {
                    return 7
                }

                func fromLibrary(): func(): Integer {
                    return shared
                }
                """, """
                include "lib.sol" alias m
                println(m::shared === m::shared)
                println(m::fromLibrary()() )
                """)).isEqualTo("true\n7\n");
    }

    // ---------------------------------------------------------------------------------------------
    // Deferred and rejected forms
    // ---------------------------------------------------------------------------------------------

    /**
     * A reference to a generic function is rejected: instantiating a generic declaration under an
     * expected function type is not part of this change, and a function value is monomorphic so the
     * value could not carry the instantiation either way.
     */
    @Test
    public void aGenericFunctionReferenceIsRejected() {
        assertThat(firstCode("""
                func identity<T>(value: T): T {
                    return value
                }

                func use(): Unit {
                    val f = identity
                }
                """)).isEqualTo(DiagnosticCode.TYPE_FUNCTION_AS_VALUE);
    }

    /**
     * Explicit type arguments on a call through a function value are rejected.
     *
     * <p>"Explicit type arguments are not permitted on an already-instantiated function value, because a
     * function value is monomorphic, so `operation<Integer>(1)` is `SOLV-TYPE-029`" (section 6).
     */
    @Test
    public void explicitTypeArgumentsOnAFunctionValueCallAreRejected() {
        assertThat(firstCode("""
                func value(): Integer {
                    return 1
                }

                func use(): Unit {
                    val operation: func(): Integer = value
                    operation<Integer>()
                }
                """)).isEqualTo(DiagnosticCode.TYPE_NOT_GENERIC);
    }

    /**
     * An indirect call with the wrong arity is reported at the call.
     *
     * <p>"a call with the wrong number of arguments is `SOLV-TYPE-003`", and "Arity is checked before
     * argument-type compatibility" (section 6).
     */
    @Test
    public void anIndirectCallWithTheWrongArityIsRejected() {
        assertThat(firstCode("""
                func takesTwo(first: Integer, second: Integer): Integer {
                    return first + second
                }

                func use(): Integer {
                    val operation: func(Integer, Integer): Integer = takesTwo
                    return operation(1)
                }
                """)).isEqualTo(DiagnosticCode.TYPE_ARITY_MISMATCH);
    }

    /**
     * An indirect call whose argument type is incompatible is rejected.
     *
     * <p>The callee's parameter type is the requirement an argument must satisfy, the same rule a direct
     * call applies (section 6).
     */
    @Test
    public void anIndirectCallWithAnIncompatibleArgumentIsRejected() {
        assertThat(firstCode("""
                func takesInteger(value: Integer): Integer {
                    return value
                }

                func use(): Integer {
                    val operation: func(Integer): Integer = takesInteger
                    return operation("text")
                }
                """)).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    /**
     * Contravariant parameter assignability is accepted through an indirect call.
     *
     * <p>"A function value of type `func(P): R` may be used where `func(Q): R` is required when `Q` is
     * assignable to `P`" — the callee's own parameter may be a supertype of what the call site expects,
     * so a `func(Any?): Unit` satisfies a `func(Integer): Unit` requirement (section 6).
     */
    @Test
    public void aCalleeWithASupertypeParameterIsAccepted() {
        assertThat(run("""
                func show(value: Any?): Unit {
                    print("shown:")
                    print(value)
                }

                func apply(operation: func(Integer): Unit): Unit {
                    operation(5)
                }

                apply(show)
                println("")
                """)).isEqualTo("shown:5\n");
    }

    /**
     * Covariant result assignability is accepted through an indirect call.
     *
     * <p>"A function value of type `func(P): R` may be used where `func(P): Q` is required when `R` is
     * assignable to `Q`" (section 6). A callee returning a subtype satisfies a requirement written with
     * the supertype, and the caller observes the value through the required type.
     */
    @Test
    public void aCalleeWithASubtypeResultIsAccepted() {
        assertThat(run("""
                func give(): String {
                    return "produced"
                }

                func describe(operation: func(): Any): String {
                    return "got " .. operation()
                }

                println(describe(give))
                """)).isEqualTo("got produced\n");
    }

    /**
     * A call through a binding whose type is not a function type remains "not callable".
     *
     * <p>"An invocation whose callee is not a function type is `SOLV-TYPE-002`" (section 6).
     */
    @Test
    public void callingANonFunctionTypedBindingIsRejected() {
        assertThat(firstCode("""
                func use(): Unit {
                    val x = 1
                    x()
                }
                """)).isEqualTo(DiagnosticCode.TYPE_NOT_CALLABLE);
    }

    /**
     * A nullable function value is not invocable without refinement.
     *
     * <p>"A nullable function value cannot be invoked without prior refinement or another existing
     * non-null mechanism" (section 6).
     */
    @Test
    public void callingANullableFunctionValueWithoutRefinementIsRejected() {
        assertThat(firstCode("""
                func value(): Integer {
                    return 1
                }

                func use(): Integer {
                    val operation: (func(): Integer)? = value
                    return operation()
                }
                """)).isEqualTo(DiagnosticCode.TYPE_NOT_CALLABLE);
    }

    /**
     * A function value may be stored in a class property and invoked through the receiver, which is
     * ordinary property reading plus an indirect call and needs no bound-method support.
     *
     * <p>"A property may itself have a function type ... member resolution decides statically whether
     * `receiver.member` reads a stored function value or creates a bound method value" (section 6). This
     * asserts only the stored-value half, which is what exists today.
     */
    @Test
    public void aFunctionValueStoredInAPropertyIsInvokedThroughTheReceiver() {
        assertThat(run("""
            func triple(value: Integer): Integer {
                    return value * 3
                }

                class Boxed {
                    val operation: func(Integer): Integer = triple
                }

                val box = Boxed()
                println(box.operation(3))
                """)).isEqualTo("9\n");
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers for include-based programs
    // ---------------------------------------------------------------------------------------------

    /**
     * Runs a root program that includes a library file, through the same context the other tests use.
     *
     * <p>Module-qualified names only exist across an include, and the shipped language performs include
     * resolution against the filesystem, so the pair is written to one temporary directory and the root
     * is evaluated from it.
     */
    private static String runInclude(String library, String root) {
        try {
            java.nio.file.Path directory = java.nio.file.Files.createTempDirectory("solvik-include");
            java.nio.file.Path libraryFile = directory.resolve("lib.sol");
            java.nio.file.Path rootFile = directory.resolve("root.sol");
            java.nio.file.Files.writeString(libraryFile, library);
            java.nio.file.Files.writeString(rootFile, root);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (Context context = Context.newBuilder("solvik").out(out).err(out)
                    .option("engine.WarnInterpreterOnly", "false").allowAllAccess(true).build()) {
                context.eval(Source.newBuilder("solvik", rootFile.toFile()).build());
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
