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
 * Anonymous function expressions (docs/LANGUAGE_SPEC.md section 6, "Anonymous functions"): a
 * {@code func(params): Return { body }} expression whose value is a function value, with a new identity
 * per evaluation, its own function boundary, and its own lexical scope.
 *
 * <p>The two claims that need a running program rather than an analyzer assertion are the ones a
 * type-model test could fake: that evaluation reaches the expression twice and produces two values that
 * are not {@code ===} to each other, and that re-reading a binding that already holds one of those
 * values returns the same value it stored. The first is the sentence "two evaluations are distinct even
 * when the expression captures no values"; the second is the sentence right after it. A shared
 * implementation-side value would satisfy every static-typing test in this file and fail both of those.
 *
 * <p>The function-boundary assertions are the other reason to run programs: a {@code break} that
 * escaped into an enclosing loop, or a {@code return} that returned from the enclosing function, would
 * both compile and be silently wrong, and only execution shows which function the control flow reached.
 *
 * <p>Every expected output is derived by reading the specification passage named in the test comment,
 * not copied from a run.
 */
public final class SolvikAnonymousFunctionTest {

    private static String run(String source) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out)
                .option("engine.WarnInterpreterOnly", "false").allowAllAccess(true).build()) {
            context.eval(build(source, "test.sol"));
        } catch (RuntimeException e) {
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

    private static DiagnosticBag analyze(String text) {
        SemanticResult result = SolvikSemanticAnalyzer.analyze(parseOk("anon.sol", text));
        assertThat(result.isSuccess()).as("analysis must fail: " + text).isFalse();
        DiagnosticBag bag = result.diagnostics();
        assertThat(bag.all().isEmpty()).as("failed analysis must carry a diagnostic").isFalse();
        return bag;
    }

    private static DiagnosticCode firstCode(String text) {
        return analyze(text).all().get(0).code();
    }

    /** The first diagnostic of {@code code} that analysis reports, or {@code null} when none has it. */
    private static Diagnostic first(String text, DiagnosticCode code) {
        for (Diagnostic diagnostic : analyze(text).all()) {
            if (diagnostic.code() == code) {
                return diagnostic;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------
    // Production and invocation
    // ---------------------------------------------------------------------------------------------

    /**
     * An anonymous function initializes a binding of function type, and calling through that binding
     * runs its body.
     *
     * <p>"An anonymous function is an expression written with `func`, an optional capture list, a
     * parenthesized parameter list, an optional return type, and a body", and "Its parameters must have
     * explicit types." (section 6).
     */
    @Test
    public void anAnonymousFunctionInitializesABindingAndIsInvoked() {
        assertThat(run("""
            func demo(): Integer {
                var double: func(Integer): Integer = func(value: Integer): Integer {
                    return value * 2
                }
                return double(21)
            }
            print(demo())
            """)).isEqualTo("42");
    }

    /**
     * A parameter list may be empty, and the body still runs when the value is invoked with no
     * arguments.
     *
     * <p>The grammar's parameter list is optional, and the invocation rules of section 6 give a call with
     * no arguments the zero-arity function type the value has.
     */
    @Test
    public void anAnonymousFunctionWithNoParametersIsInvokedWithNone() {
        assertThat(run("""
            func demo(): String {
                var give: func(): String = func(): String {
                    return "given"
                }
                return give()
            }
            print(demo())
            """)).isEqualTo("given");
    }

    /**
     * An omitted return type names {@code Unit} exactly as it does in a declaration, and the body runs
     * for its effect.
     *
     * <p>"Its return type follows the same rule as a named function: omitting it declares `Unit`"
     * (section 6). The binding's declared type is what a call site sees, so the run also proves the body
     * reached its effect rather than the value having become something else.
     */
    @Test
    public void anOmittedReturnTypeDeclaresUnit() {
        assertThat(run("""
            func shout(value: String): String {
                return value .. "!"
            }

            func demo(): String {
                var emit: func(String) = func(value: String) {
                    shout(value)
                }
                emit("a")
                return "ran"
            }
            print(demo())
            """)).isEqualTo("ran");
    }

    /**
     * The body may call a global function. Section 6 states that "Top-level and module-qualified
     * function declarations are globally resolved declarations rather than" local state "and need no"
     * capture "entry", so a global is reachable from a non-capturing body with nothing written for it.
     */
    @Test
    public void theBodyReachesGlobalDeclarationsWithoutACapture() {
        assertThat(run("""
            func triple(value: Integer): Integer {
                return value * 3
            }

            func demo(): Integer {
                var step: func(Integer): Integer = func(value: Integer): Integer {
                    return triple(value) + 1
                }
                return step(5)
            }
            print(demo())
            """)).isEqualTo("16");
    }

    /**
     * A value-returning anonymous function must write its return type: "a value-returning anonymous
     * function must write its return type and must return a compatible value on every normally
     * completing path", and "Function bodies never acquire an implicit tail result, and the ordinary
     * return diagnostics apply inside an anonymous function exactly as they do in a declaration"
     * (section 6). With the return type omitted the body is a `Unit` body, so returning a value from
     * it is the ordinary unexpected-return diagnostic rather than a silent {@code Unit}-to-
     * {@code Integer} function type.
     */
    @Test
    public void aValueReturningAnonymousFunctionMustWriteItsReturnType() {
        assertThat(firstCode("""
            func demo(): Integer {
                var step: func(Integer): Integer = func(value: Integer) {
                    return value + 1
                }
                return step(1)
            }
            print(demo())
            """)).isEqualTo(DiagnosticCode.TYPE_UNEXPECTED_RETURN_VALUE);
    }

    /**
     * Function bodies never acquire an implicit tail result, so a block whose last expression is not a
     * {@code return} does not produce a value: "Function bodies never acquire an implicit tail result,
     * and the ordinary return diagnostics apply inside an anonymous function exactly as they do in a
     * declaration" (section 6).
     */
    @Test
    public void aValueReturningAnonymousFunctionNeedsAReturnOnEveryPath() {
        assertThat(first("func demo(): Integer {\n"
                        + "    var step: func(): Integer = func(): Integer {\n"
                        + "        print(\"no result\")\n"
                        + "    }\n"
                        + "    return step()\n"
                        + "}\n"
                        + "print(demo())\n", DiagnosticCode.TYPE_MISSING_RETURN_PATH)).isNotNull();
    }

    // ---------------------------------------------------------------------------------------------
    // Fresh identity per evaluation
    // ---------------------------------------------------------------------------------------------

    /**
     * Two evaluations of one anonymous-function expression produce two values that are not reference
     * identical.
     *
     * <p>"An anonymous function creates a new function value every time evaluation reaches the
     * expression, and two evaluations are distinct even when the expression captures no values"
     * (section 6). The expression is reached twice through {@code maker()}; making the value a per-site
     * constant — which is what the named-function rule requires and what a tempting shared
     * implementation would do — would print {@code true} here. Solvik has no conditional operator, so
     * the assertion uses {@code ==}, which on function values is the same reference identity that {@code
     * ===} would have tested.
     */
    @Test
    public void twoEvaluationsOfOneAnonymousFunctionAreDistinct() {
        assertThat(run("""
            func demo(): Boolean {
                var maker: func(): func(): Integer = func(): func(): Integer {
                    return func(): Integer {
                        return 7
                    }
                }
                var one = maker()
                var two = maker()
                return one == two
            }
            print(demo())
            """)).isEqualTo("false");
    }

    /**
     * Each of the two distinct values is still callable and produces the body's result.
     *
     * <p>Fresh identity must not cost behaviour: both values invoke the same body, which is why they
     * agree on the result while disagreeing on identity.
     */
    @Test
    public void eachFreshValueIsStillCallable() {
        assertThat(run("""
            func demo(): String {
                var maker: func(): func(): Integer = func(): func(): Integer {
                    return func(): Integer {
                        return 7
                    }
                }
                var one = maker()
                var two = maker()
                return one() .. two()
            }
            print(demo())
            """)).isEqualTo("77");
    }

    /**
     * Re-reading a binding that holds an anonymous function value preserves its identity: "Re-reading a
     * local that holds an anonymous function value preserves its identity" (section 6). The expression
     * is evaluated once, into {@code stored}; the two reads of {@code stored} are not evaluations of the
     * anonymous-function expression and must therefore agree.
     */
    @Test
    public void reReadingABindingPreservesTheValueItStored() {
        assertThat(run("""
            func demo(): Boolean {
                var stored: func(): Integer = func(): Integer {
                    return 1
                }
                var again: func(): Integer = stored
                return stored == again
            }
            print(demo())
            """)).isEqualTo("true");
    }

    /**
     * The identity of an anonymous function value is reference identity, so equality and hashing follow
     * the fixed function-value rules rather than the expression's shape: two structurally identical
     * anonymous functions written at different sites are not equal.
     *
     * <p>"Semantic equality for function values is reference identity" (section 3), and the two values
     * here come from two separate expressions, hence two separate evaluations.
     */
    @Test
    public void twoWriteSitesProduceInequalValues() {
        assertThat(run("""
            func demo(): Boolean {
                var left: func(): Integer = func(): Integer {
                    return 1
                }
                var right: func(): Integer = func(): Integer {
                    return 1
                }
                return left == right
            }
            print(demo())
            """)).isEqualTo("false");
    }

    /**
     * Every rendering of an anonymous function value is the fixed string {@code func}: "`toString()`
     * for every function value returns the exact string `func`. It must not expose a Java class name,
     * memory address, node name, module path, captured values, or implementation details, so `print`,
     * `println`, and `..` render every function value as `func`" (section 6). The rendering must not
     * expose the debug name the lowering gives the body, nor any Java-level shape.
     */
    @Test
    public void anAnonymousFunctionValueRendersAsFunc() {
        assertThat(run("""
            func demo(): String {
                var value: func(): Integer = func(): Integer {
                    return 1
                }
                print(value)
                print(" ")
                print("" .. value)
                return ""
            }
            demo()
            """)).isEqualTo("func func");
    }

    // ---------------------------------------------------------------------------------------------
    // Scoping: shadowing, but no implicit access to enclosing locals
    // ---------------------------------------------------------------------------------------------

    /**
     * A body declaration may shadow an outer binding under the ordinary lexical rules: "a declaration
     * inside its body may shadow an outer binding under the ordinary lexical-scope rules" (section 6).
     * The inner {@code base} is the body's own local, so the body reads 10 and not 3, and the outer
     * binding is left alone.
     */
    @Test
    public void aBodyDeclarationMayShadowAnOuterBinding() {
        assertThat(run("""
            func demo(): Integer {
                var base: Integer = 3
                var compute: func(): Integer = func(): Integer {
                    var base: Integer = 10
                    return base
                }
                var outer: Integer = compute()
                return outer * 100 + base
            }
            print(demo())
            """)).isEqualTo("1003");
    }

    /**
     * The parameters are in scope for the body, so a body may use one twice and combine it with a global.
     *
     * <p>"An anonymous function introduces a function boundary and a lexical scope containing its
     * parameters and body locals; its parameters follow the existing immutable-parameter rule"
     * (section 6).
     */
    @Test
    public void theParametersAreTheBodysOwnScope() {
        assertThat(run("""
            func twice(value: Integer): Integer {
                return value * 2
            }

            func demo(): Integer {
                var combine: func(Integer): Integer = func(value: Integer): Integer {
                    return twice(value) + value
                }
                return combine(4)
            }
            print(demo())
            """)).isEqualTo("12");
    }

    /**
     * A body may not read an enclosing function's local: "An anonymous function has no implicit access
     * to local values from an enclosing function. Every such dependency must appear in an explicit
     * capture list" (section 6). That list is a later change, and "An outer local or parameter
     * referenced by the body but omitted from the capture list is `SEM_UNLISTED_CAPTURE`
     * (`SOLV-SEM-058`), reported on the body reference", so the read reports that code.
     */
    @Test
    public void readingAnEnclosingLocalIsAnUnlistedCapture() {
        Diagnostic diagnostic = first("""
            func demo(base: Integer): Integer {
                var compute: func(Integer): Integer = func(value: Integer): Integer {
                    return value + base
                }
                return compute(1)
            }
            print(demo(2))
            """, DiagnosticCode.SEM_UNLISTED_CAPTURE);
        assertThat(diagnostic).isNotNull();
    }

    /**
     * The unlisted-capture rule covers a write as well as a read, so the enclosing {@code var mutable} is
     * reported at the assignment target inside the body and not merely at a read.
     *
     * <p>The same sentence of section 6 governs both: the dependency "must appear in an explicit capture
     * list", and this revision can write none.
     */
    @Test
    public void writingAnEnclosingLocalIsAnUnlistedCapture() {
        assertThat(first("""
            func demo(): Integer {
                var mutable total: Integer = 0
                var bump: func() = func() {
                    total = total + 1
                }
                bump()
                return total
            }
            print(demo())
            """, DiagnosticCode.SEM_UNLISTED_CAPTURE)).isNotNull();
    }

    /**
     * A name that no enclosing function declares is still an unknown name, not a capture: the
     * specification reserves the capture diagnostic for a name that resolves to "a `var` local declared
     * in an enclosing function scope; an immutable parameter of an enclosing function; another function
     * value held by an immutable binding; or `this`", and a typo is none of those (section 6).
     */
    @Test
    public void anUnknownNameInsideTheBodyIsStillAnUnknownName() {
        assertThat(firstCode("""
            func demo(): Integer {
                var compute: func(): Integer = func(): Integer {
                    return nothingDeclaredAnywhere
                }
                return compute()
            }
            print(demo())
            """)).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_NAME);
    }

    /**
     * {@code this} in an anonymous body written inside an instance method is a dependency on the
     * enclosing receiver, which section 6 says must be written {@code [this]}: "This applies to `this` as
     * well: a closure body may use `this` only when `[this]` is written." Capture is a later change, so
     * the use is reported as unlisted.
     */
    @Test
    public void usingThisInsideTheBodyIsAnUnlistedCapture() {
        assertThat(first("""
            class Widget {
                var id: Integer = 1

                func viaClosure(): Integer {
                    var read: func(): Integer = func(): Integer {
                        return this.id
                    }
                    return read()
                }
            }

            func demo(): Integer {
                return Widget().viaClosure()
            }
            print(demo())
            """, DiagnosticCode.SEM_UNLISTED_CAPTURE)).isNotNull();
    }

    /**
     * {@code this} with no enclosing receiver at all keeps the code the specification names for that
     * case: "`this` where no instance receiver exists remains `SOLV-RESOL-005`" (section 6). The
     * anonymous function here is written in a top-level binding, so there is no receiver to capture and
     * the defect is not a missing capture entry.
     */
    @Test
    public void thisWithNoEnclosingReceiverIsStillOutsideAClass() {
        assertThat(firstCode("""
            var read: func(): Integer = func(): Integer {
                return this.id
            }
            print(1)
            """)).isEqualTo(DiagnosticCode.RESOL_THIS_OUTSIDE_CLASS);
    }

    /**
     * An anonymous function written in an instance method cannot use the enclosing class's type
     * parameter: the function is written with concrete types and writes no type parameters of its own,
     * so the name resolves to no type.
     */
    @Test
    public void anEnclosingTypeParameterIsNotVisibleInTheBody() {
        // The name appears in a type position, so it is reported as an unknown type.
        assertThat(firstCode("""
            mutable class Box<Value> {
                func make(): func(): Integer {
                    return func(): Value {
                        return null
                    }
                }
            }
            print(1)
            """)).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_TYPE);
    }

    // ---------------------------------------------------------------------------------------------
    // The function boundary
    // ---------------------------------------------------------------------------------------------

    /**
     * A {@code return} inside the body returns from the body and not from the function that created it:
     * "`return` inside a function body returns from that function body and never from the function that
     * created a closure" (section 6). The body's {@code return} must hand 7 to the caller and the
     * enclosing function must still reach its own {@code return}.
     */
    @Test
    public void aReturnInsideTheBodyReturnsFromTheBody() {
        assertThat(run("""
            func demo(): String {
                var give: func(): Integer = func(): Integer {
                    return 7
                }
                print(give())
                return "after"
            }
            print(demo())
            """)).isEqualTo("7after");
    }

    /**
     * A {@code break} cannot cross a function boundary: "`break` and `continue` cannot cross a function
     * boundary" (section 6). The body sits inside a {@code while}, so an implementation that treated the
     * body as part of the enclosing function's control flow would exit the loop from inside the body;
     * the correct reading is that the body contains no loop and the {@code break} is misplaced.
     */
    @Test
    public void breakCannotCrossTheFunctionBoundary() {
        assertThat(first("""
            func demo(): Integer {
                var mutable count: Integer = 0
                var body: func(): Integer = func(): Integer {
                    break
                    return 0
                }
                var mutable i: Integer = 0
                while (i < 3) {
                    i = i + 1
                    count = count + body()
                }
                return count
            }
            print(demo())
            """, DiagnosticCode.SEM_LOOP_CONTROL_OUTSIDE_LOOP)).isNotNull();
    }

    /**
     * A {@code break} in a loop that is itself inside the body targets the body's own loop: the boundary
     * stops the search outward, not the search inside.
     *
     * <p>Same sentence of section 6, read from the other side.
     */
    @Test
    public void breakInsideTheBodysOwnLoopIsLegal() {
        assertThat(run("""
            func demo(): Integer {
                var countTo: func(Integer): Integer = func(limit: Integer): Integer {
                    var mutable seen: Integer = 0
                    var mutable i: Integer = 0
                    while (true) {
                        i = i + 1
                        if (i > limit) {
                            break
                        }
                        seen = seen + i
                    }
                    return seen
                }
                return countTo(4)
            }
            print(demo())
            """)).isEqualTo("10");
    }

    /**
     * {@code continue} is held to the same boundary as {@code break}.
     */
    @Test
    public void continueCannotCrossTheFunctionBoundary() {
        assertThat(first("""
            func demo(): Integer {
                var body: func(): Integer = func(): Integer {
                    continue
                    return 0
                }
                var mutable i: Integer = 0
                while (i < 2) {
                    i = i + 1
                    body()
                }
                return i
            }
            print(demo())
            """, DiagnosticCode.SEM_LOOP_CONTROL_OUTSIDE_LOOP)).isNotNull();
    }

    /**
     * A bare anonymous function used as a statement is invalid: "A bare anonymous function or function
     * reference used as an expression statement remains invalid, because creating and discarding a
     * function value is not a call" (section 6). The existing rule that only a call may stand alone is
     * what enforces this, so the requirement is that the new expression kind does not smuggle itself
     * past it.
     */
    @Test
    public void aBareAnonymousFunctionIsNotAStatement() {
        assertThat(firstCode("""
            func(): Integer {
                return 1
            }
            print(1)
            """)).isEqualTo(DiagnosticCode.SEM_VALUE_EXPRESSION_STATEMENT);
    }

    // ---------------------------------------------------------------------------------------------
    // Nesting and placement
    // ---------------------------------------------------------------------------------------------

    /**
     * An anonymous function may be written inside another one, and the inner body may reach a global.
     *
     * <p>Section 6 gives an anonymous function the same body rules as a declaration, and a declaration's
     * body may contain expressions, so the nesting is just the same rule applied twice. The outer value
     * is produced, invoked, and yields the inner value, which is then invoked.
     */
    @Test
    public void anAnonymousFunctionMayContainAnother() {
        assertThat(run("""
            func label(value: Integer): String {
                return "v" .. value.toString()
            }

            func demo(): String {
                var outer: func(): func(): String = func(): func(): String {
                    return func(): String {
                        return label(3)
                    }
                }
                return outer()()
            }
            print(demo())
            """)).isEqualTo("v3");
    }

    /**
     * An anonymous function is legal in a property initializer and the stored value is invoked through
     * the receiver, because "A property may itself have a function type" (docs/LANGUAGE_SPEC.md
     * section 6, "Bound method references").
     */
    @Test
    public void anAnonymousFunctionInitializesAProperty() {
        assertThat(run("""
            func triple(value: Integer): Integer {
                return value * 3
            }

            class Holder {
                var step: func(Integer): Integer = func(value: Integer): Integer {
                    return triple(value) + 1
                }
            }

            func demo(): Integer {
                var holder = Holder()
                return holder.step(3)
            }
            print(demo())
            """)).isEqualTo("10");
    }

    /**
     * A nested anonymous function creates a new value each time the <em>outer</em> body runs, since
     * evaluation reaches the inner expression once per outer call.
     *
     * <p>This is the "every time evaluation reaches the expression" clause applied to a site that is
     * itself reached repeatedly: the inner expression is one write site but many evaluations.
     */
    @Test
    public void anInnerAnonymousFunctionIsFreshPerOuterCall() {
        assertThat(run("""
            func demo(): Boolean {
                var outer: func(): func(): Integer = func(): func(): Integer {
                    return func(): Integer {
                        return 2
                    }
                }
                var first = outer()
                var second = outer()
                return first == second
            }
            print(demo())
            """)).isEqualTo("false");
    }

    /**
     * An anonymous function may be invoked immediately where the call's syntax allows the expression,
     * confirming the value the expression produces is the value that gets called.
     *
     * <p>The braces are written inside the call, so the body's closing brace and the call's closing
     * paren take their own lines: the strict closing-brace rule of section 16 admits nothing but a
     * comment after a standalone {@code}}, which is why the closer closes on a line of its own.
     */
    @Test
    public void anAnonymousFunctionMayBeInvokedImmediately() {
        assertThat(run("""
            print((func(): Integer {
                return 1 + 2
            }
            )())
            """)).isEqualTo("3");
    }

    /**
     * Function-type assignability applies to an anonymous value exactly as it does to a named one, so a
     * value whose parameter is a supertype of the target's is accepted: "Function-type assignability is
     * contravariant in parameters and covariant in the result" (section 6).
     */
    @Test
    public void anAnonymousValueIsAssignableUnderFunctionTypeVariance() {
        assertThat(run("""
            mutable class Animal {
                var name: String = "animal"
            }

            mutable class Dog extends Animal {
            }

            func describe(animal: Animal): String {
                return animal.name
            }

            func demo(): String {
                var asDog: func(Dog): String = func(animal: Animal): String {
                    return describe(animal)
                }
                return asDog(Dog())
            }
            print(demo())
            """)).isEqualTo("animal");
    }

    /**
     * A call through an anonymous function value propagates a guest exception untranslated, the same
     * rule an indirect call through a named function value has.
     */
    @Test
    public void anExceptionThrownInsideTheBodyPropagatesOut() {
        assertThat(run("""
            class Boom extends RuntimeException {
            }

            func demo(): String {
                var raiser: func(): Integer = func(): Integer {
                    throw Boom("exploded")
                }
                try {
                    raiser()
                    return "no exception"
                }
                catch (error: Boom) {
                    return "caught " .. error.getMessage()
                }
            }
            print(demo())
            """)).isEqualTo("caught exploded");
    }
}
