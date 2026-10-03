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

    /**
     * The hierarchy the specification's variance and join examples use, with the two function
     * declarations whose types are incomparable. Tests about the join and about assignability append
     * their own statements to this text so each program states only the rule it pins.
     *
     * <p>{@code func(Dog): Dog} and {@code func(Animal): Animal} are deliberately the pair neither of
     * which is assignable to the other: that is the case where the join has to produce a type neither
     * branch declares.
     */
    private static final String ANIMAL_HIERARCHY = """
            mutable class Animal {
                Animal() {
                }

                func label(): String {
                    return "animal"
                }
            }

            class Dog extends Animal {
                Dog() {
                }
            }

            func dogToDog(dog: Dog): Dog {
                return dog
            }

            func animalToAnimal(animal: Animal): Animal {
                return animal
            }
            """;

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
        return firstDiagnostic(text).code();
    }

    /**
     * The report a rejected program must produce. Span assertions are taken from the report's own
     * offset rather than a hard-coded one, so a program can be reformatted without moving an expected
     * column.
     */
    private static Diagnostic firstDiagnostic(String text) {
        SemanticResult result = SolvikSemanticAnalyzer.analyze(parseOk("funcvalue.sol", text));
        assertThat(result.isSuccess()).as("analysis must fail: " + text).isFalse();
        DiagnosticBag bag = result.diagnostics();
        List<Diagnostic> all = bag.all();
        assertThat(all.isEmpty()).as("failed analysis must carry a diagnostic").isFalse();
        return all.get(0);
    }

    /** Asserts that {@code diagnostic} covers {@code token} at the position {@code marker} locates. */
    private static void assertCovers(String text, String marker, String token, Diagnostic diagnostic) {
        int at = text.indexOf(marker) + marker.indexOf(token);
        assertThat(at).as("marker present in the program").isGreaterThan(0);
        assertThat(diagnostic.span().startOffset()).as("diagnostic starts at the reported text").isEqualTo(at);
        assertThat(diagnostic.span().endOffset()).as("diagnostic ends after the reported text").isEqualTo(at + token.length());
    }

    /**
     * The single report a rejected program must produce. Each of these programs has exactly one defect,
     * so a second report means the first cascaded, and a span assertion alone cannot tell a clean
     * rejection from a cascading one.
     */
    private static Diagnostic onlyDiagnostic(String text) {
        SemanticResult result = SolvikSemanticAnalyzer.analyze(parseOk("funcvalue.sol", text));
        assertThat(result.isSuccess()).as("analysis must fail: " + text).isFalse();
        List<Diagnostic> all = result.diagnostics().all();
        assertThat(all).as("exactly one diagnostic, found: " + all).hasSize(1);
        return all.get(0);
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

                mutable val operation: func(Integer): Integer = first
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
     * A generic function used as a value with no expected function type cannot be instantiated.
     *
     * <p>"A generic function reference with no expected function type is `SOLV-TYPE-030`" (section 6).
     * An untyped local has no expected function type, and no second inference diagnostic exists: "no
     * second inference diagnostic exists for the same expression" (section 6). Instantiation where a
     * context supplies the type is covered by {@link SolvikGenericFunctionValueTest}.
     */
    @Test
    public void aGenericFunctionReferenceWithoutAnExpectedTypeCannotBeInferred() {
        assertThat(firstCode("""
                func identity<T>(value: T): T {
                    return value
                }

                func use(): Unit {
                    val f = identity
                }
                """)).isEqualTo(DiagnosticCode.TYPE_CANNOT_INFER);
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

    /**
     * One physical indirect call site driven by every kind of function value the language produces.
     *
     * <p>The dispatch node caching a stable target and the fallback for a site that keeps seeing new
     * values are the two halves of the same rule - "Invocation returns the declared result" for each of
     * them (section 6) - and only a site that sees several values reaches the fallback. A named
     * reference contributes its declaration's one canonical value, an anonymous expression and a bound
     * reference each contribute a value created by that evaluation, and a capturing closure carries the
     * environment the hidden arguments must supply. The same call therefore has to produce the right
     * answer for four different value shapes, twice over, so a cache that keeps serving the first target
     * after the second arrives is caught rather than assumed.
     */
    @Test
    public void oneIndirectCallSiteServesEveryKindOfFunctionValue() {
        assertThat(run("""
                func named(value: Integer): Integer {
                    return value + 1
                }

                class Step {
                    val amount: Integer = 1

                    func move(value: Integer): Integer {
                        return value + this.amount
                    }
                }

                func apply(operation: func(Integer): Integer): Integer {
                    return operation(41)
                }

                val amount = 1

                val closure = func [amount](value: Integer): Integer {
                    return value + amount
                }

                val anonymous = func(value: Integer): Integer {
                    return value + 1
                }

                val stepper = Step()

                println(apply(named))
                println(apply(anonymous))
                println(apply(closure))
                println(apply(stepper.move))
                println(apply(named))
                println(apply(stepper.move))
                """)).isEqualTo("42\n42\n42\n42\n42\n42\n");
    }

    // ---------------------------------------------------------------------------------------------
    // The shared type join, applied to function types
    // ---------------------------------------------------------------------------------------------

    /**
     * Two branches whose function types are incomparable join to their least common function supertype
     * and stay callable.
     *
     * <p>"The shared type join understands function types: for two same-arity function types each joined
     * parameter takes the more specific of the two when one is assignable to the other, and the joined
     * result is their nearest common result type. That joined function type is the least common function
     * supertype allowed by contravariant parameters and covariant results" (section 6). Neither
     * {@code func(Dog): Dog} nor {@code func(Animal): Animal} is assignable to the other, so neither
     * branch type can be the join: the parameter contributes {@code Dog}, the result contributes
     * {@code Animal}, and the value the {@code if} produces is a function the program can invoke.
     */
    @Test
    public void incomparableFunctionTypeBranchesJoinToACallableFunctionType() {
        assertThat(run(ANIMAL_HIERARCHY + """

                val flag = true

                val operation = if (flag) {
                    dogToDog
                } else {
                    animalToAnimal
                }

                println(operation(Dog()).label())
                """)).isEqualTo("animal\n");
    }

    /**
     * The same joined type satisfies a declared function type, here through an expression {@code switch}
     * and a declared result, because the rule belongs to the shared join and not to one construct.
     *
     * <p>The clause quoted above is stated of "the shared type join", which section 21.2, 21.4, 21.5, and
     * 12 all use, so a {@code switch} expression's cases and a function's declared result must agree with
     * what an {@code if} produces (sections 6 and 21).
     */
    @Test
    public void aJoinedFunctionTypeSatisfiesADeclaredFunctionType() {
        assertThat(run(ANIMAL_HIERARCHY + """

                func pick(kind: Integer): func(Dog): Animal {
                    return switch (kind) {
                        case 1:
                            dogToDog

                        default:
                            animalToAnimal
                    }
                }

                val chosen: func(Dog): Animal = pick(1)
                println(chosen(Dog()).label())
                """)).isEqualTo("animal\n");
    }

    /**
     * The joined type is the type the rule computes rather than a looser approximation of it, so the
     * same value fills neither a binding whose parameter is the wider member nor one whose result is the
     * narrower member. Without these two arms every positive test above is satisfied by any join that
     * produces *a* callable function type: widen the joined parameter or narrow the joined result and
     * the value is still callable and still reaches an {@code Animal} parameter.
     *
     * <p>"each joined parameter takes the more specific of the two when one is assignable to the other,
     * and the joined result is their nearest common result type. That joined function type is the least
     * common function supertype allowed by contravariant parameters and covariant results" (section 6).
     * With `func(Dog): Dog` and `func(Animal): Animal` as the branches the joined type is
     * `func(Dog): Animal`, which is not assignable to `func(Animal): Animal` — contravariant parameters
     * would need `Animal` assignable to `Dog` — nor to `func(Dog): Dog`, whose result the rule has just
     * widened. Each arm is its own program so that each position is the single reason its program is
     * refused.
     */
    @Test
    public void aJoinedFunctionTypeFillsNeitherAWiderParameterNorANarrowerResult() {
        String widerParameter = ANIMAL_HIERARCHY + """

                val flag: Boolean = true
                val joined = if (flag) { dogToDog } else { animalToAnimal }
                val wide: func(Animal): Animal = joined
                """;
        Diagnostic parameter = onlyDiagnostic(widerParameter);
        assertThat(parameter.code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
        assertCovers(widerParameter, "val wide: func(Animal): Animal = joined", "joined", parameter);

        String narrowerResult = ANIMAL_HIERARCHY + """

                val flag: Boolean = true
                val joined = if (flag) { dogToDog } else { animalToAnimal }
                val narrow: func(Dog): Dog = joined
                """;
        Diagnostic result = onlyDiagnostic(narrowerResult);
        assertThat(result.code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
        assertCovers(narrowerResult, "val narrow: func(Dog): Dog = joined", "joined", result);
    }

    /**
     * An unrelated parameter pair manufactures no function supertype: the ordinary join selects {@code
     * Any}, and a callee that is not a function type is refused.
     *
     * <p>"When a parameter pair is unrelated or the results have no unique join, no function-type join
     * exists and the ordinary join may still select a shared nominal supertype such as `Any`; a join
     * never introduces `Nothing`, a union, or an intersection in order to manufacture a function
     * supertype", read with "An invocation whose callee is not a function type is `SOLV-TYPE-002`"
     * (section 6). The diagnostic sits on the callee, which is the expression the rule makes
     * non-callable.
     */
    @Test
    public void functionTypesWithAnUnrelatedParameterPairJoinToAnyAndCannotBeInvoked() {
        String program = """
                class Side {
                    Side() {
                    }
                }

                func takeInteger(value: Integer): String {
                    return value.toString()
                }

                func takeSide(value: Side): String {
                    return value.toString()
                }

                val flag = true

                val operation = if (flag) {
                    takeInteger
                } else {
                    takeSide
                }

                println(operation(1))
                """;
        Diagnostic diagnostic = onlyDiagnostic(program);
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_NOT_CALLABLE);
        assertCovers(program, "println(operation(1))", "operation", diagnostic);
    }

    /**
     * Function types of different arity are not comparable at all, so no function-type join exists and
     * the joined value is not callable.
     *
     * <p>"both types have the same arity" is the first condition of function-type assignability, and the
     * join rule is stated for "two same-arity function types", so a unary and a binary branch have no
     * function supertype to join to (section 6).
     */
    @Test
    public void functionTypesOfDifferentArityJoinToAnyAndCannotBeInvoked() {
        String program = """
                func unary(value: Integer): String {
                    return value.toString()
                }

                func binary(first: Integer, second: Integer): String {
                    return first.toString()
                }

                val flag = true

                val operation = if (flag) {
                    unary
                } else {
                    binary
                }

                println(operation(1))
                """;
        Diagnostic diagnostic = onlyDiagnostic(program);
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_NOT_CALLABLE);
        assertCovers(program, "println(operation(1))", "operation", diagnostic);
    }

    // ---------------------------------------------------------------------------------------------
    // Assignability rules that must NOT be applied
    // ---------------------------------------------------------------------------------------------

    /**
     * Each position of a function type is refused on its own, so the accepted direction cannot be read
     * as an implementation that ignores one of the two variance rules. The section's own pair fails in
     * both positions at once, which is why the two arms here are written to fail in one position each:
     * an implementation that checks only the result would accept a both-positions-fail pair for the
     * parameter's sake and vice versa.
     *
     * <p>"Given `open class Animal` and `class Dog extends Animal`, a value of type `func(Animal): Dog`
     * is assignable to `func(Dog): Animal`, and a value of type `func(Dog): Animal` is not assignable to
     * `func(Animal): Dog`" (section 6). A `Dog` is an `Animal`, so contravariant parameters refuse a
     * `func(Dog): Dog` value at `func(Animal): Animal` even though its result would be accepted, and
     * covariant results refuse a `func(Animal): Animal` value at `func(Dog): Dog` even though its
     * parameter would be accepted.
     */
    @Test
    public void aFunctionTypeWithTheWrongVarianceDirectionIsRejected() {
        String parameter = ANIMAL_HIERARCHY + """

                val wrong: func(Animal): Animal = dogToDog
                """;
        Diagnostic narrower = onlyDiagnostic(parameter);
        assertThat(narrower.code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
        assertCovers(parameter, "val wrong: func(Animal): Animal = dogToDog", "dogToDog", narrower);

        String result = ANIMAL_HIERARCHY + """

                val narrow: func(Dog): Dog = animalToAnimal
                """;
        Diagnostic tooSpecific = onlyDiagnostic(result);
        assertThat(tooSpecific.code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
        assertCovers(result, "val narrow: func(Dog): Dog = animalToAnimal", "animalToAnimal", tooSpecific);
    }

    /**
     * Numeric widening is a conversion, not a subtype relation, so it is never applied inside a function
     * type even though an {@code Integer} argument may widen at an ordinary call site.
     *
     * <p>"Numeric widening is not a subtype relation (section 4) and is never applied inside
     * function-type assignability: a function accepting `Long` is not assignable to a function type
     * accepting `Integer` merely because an `Integer` argument may widen at an ordinary conversion
     * site" (section 6). The same value is bound to its own type in the same program, so the refusal can
     * only be about the parameter pair and not about the shape of the target or the callee.
     */
    @Test
    public void numericWideningIsNotAppliedInsideFunctionTypeAssignability() {
        String program = """
                func longToLong(value: Long): Long {
                    return value
                }

                val exact: func(Long): Long = longToLong
                val widened: func(Integer): Long = longToLong
                """;
        Diagnostic diagnostic = onlyDiagnostic(program);
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
        assertCovers(program, "val widened: func(Integer): Long = longToLong", "longToLong", diagnostic);
    }

    /**
     * A generic application of two comparable function types stays invariant, in both directions.
     *
     * <p>"Generic type arguments remain invariant, so `List<func(Dog): Animal>` and `List<func(Animal):
     * Dog>` are unrelated applications even though the function types inside them are comparable"
     * (section 6). The section's own pair is used rather than an incomparable one, so only invariance can
     * refuse the forward direction: its element types are exactly the pair section 6 accepts in that
     * direction. An identical type argument is assigned in the same program as the control that proves
     * the refusal is about a differing argument and not about function types inside `List` at all. Both
     * directions are written because an implementation can honour one and reverse the other; the reverse
     * arm is over-determined, since the element pair is not assignable that way at all.
     */
    @Test
    public void genericTypeArgumentsContainingFunctionTypesStayInvariant() {
        String program = ANIMAL_HIERARCHY + """

                val matching: List<func(Animal): Dog> = List()
                val accepted: List<func(Animal): Dog> = matching
                val forward: List<func(Dog): Animal> = matching
                """;
        Diagnostic diagnostic = onlyDiagnostic(program);
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
        assertCovers(program, "val forward: List<func(Dog): Animal> = matching", "matching", diagnostic);

        String reverse = ANIMAL_HIERARCHY + """

                val narrow: List<func(Dog): Animal> = List()
                val backward: List<func(Animal): Dog> = narrow
                """;
        Diagnostic reversed = onlyDiagnostic(reverse);
        assertThat(reversed.code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
        assertCovers(reverse, "val backward: List<func(Animal): Dog> = narrow", "narrow", reversed);
    }

    /**
     * A function value stored as {@code Any} still needs refinement before an identity operator, exactly
     * as every other identity-bearing value does, and it does not gain an exception on either side of
     * either operator.
     *
     * <p>"`Any` remains invalid for identity operations without refinement, as it does for every other
     * identity-bearing runtime value" (section 6), and section 3 keeps {@code Any} out of the
     * identity-bearing set even though every function value is one. The second arm puts an
     * {@code Any}-held `Integer` on the other side of `!==`, so the refusal cannot be read as a rule
     * about two function values meeting and the function value's own {@code Any} form cannot be read as
     * the operand that carries the blame.
     */
    @Test
    public void identityOperatorsOnFunctionValuesStoredAsAnyAreRejected() {
        String program = """
                func double(value: Integer): Integer {
                    return value * 2
                }

                val first: Any = double
                val second: Any = double
                println(first === second)
                """;
        Diagnostic diagnostic = onlyDiagnostic(program);
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_IDENTITY_OPERANDS);
        assertCovers(program, "println(first === second)", "first === second", diagnostic);

        String otherOperator = """
                func double(value: Integer): Integer {
                    return value * 2
                }

                val stored: Any = double
                val opaque: Any = 1
                println(stored !== opaque)
                """;
        Diagnostic reversed = onlyDiagnostic(otherOperator);
        assertThat(reversed.code()).isEqualTo(DiagnosticCode.TYPE_IDENTITY_OPERANDS);
        assertCovers(otherOperator, "println(stored !== opaque)", "stored !== opaque", reversed);
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
