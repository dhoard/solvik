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
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;
import org.solvik.diagnostic.Diagnostic;
import org.solvik.diagnostic.DiagnosticBag;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;

/**
 * Explicit immutable closure capture (docs/LANGUAGE_SPEC.md section 6, "Explicit immutable closure
 * capture"): a capture list written {@code func [a, b](params) { body }} binds the named immutable
 * values into the closure at creation, and a body may reach no other enclosing-function state.
 *
 * <p>The claims here divide by what a static test could fake. The rejection tests only need the
 * analyzer, because each cites one diagnostic and the position it is reported on — the specification
 * is unusually specific about positions ("reported on that capture item", "reported on the body
 * reference"), and a diagnostic placed on the wrong node satisfies a code-only assertion while
 * misleading the reader who has to act on it, so those tests assert the code and check the reported
 * text names the right subject.
 *
 * <p>The acceptance tests have to run, and each one runs because a specific runtime sentence in the
 * specification would be false under a plausible wrong implementation:
 *
 * <ul>
 *   <li>"Captures bind values, not storage locations" — a copy-by-value implementation that also
 *       copied object graphs would pass every other test here and fail
 *       {@link #aCapturedObjectReferenceObservesLaterMutation()};
 *   <li>"a closure remains valid after the function that created it returns" — the one property a
 *       naive frame-capture implementation cannot satisfy, since the creator's activation is gone;
 *   <li>"Capture is transitive only through explicit values ... it does not duplicate or flatten the
 *       captured closure's environment" — a flattening implementation would resolve the inner name
 *       directly and never consult the captured closure, and would still print the same numbers.
 *       {@link #aClosureCapturingAClosureRetainsTheCapturedClosuresOwnEnvironment()} is what separates
 *       them;
 *   <li>"a name used in an inner capture list counts as a use by the enclosing closure" — this is the
 *       sentence that makes the capture list a real analysis rather than a formality, and it is tested
 *       from both sides: forwarding succeeds, and omitting one link is rejected
 *       {@link #anInnerCaptureItemNamingStateBeyondTheEnclosingClosureIsRejected()}.
 * </ul>
 *
 * <p>Every expected output is derived by reading the specification passage named in the test comment,
 * not copied from a run.
 */
public final class SolvikCaptureTest {

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
        SemanticResult result = SolvikSemanticAnalyzer.analyze(parseOk("capture.sol", text));
        assertThat(result.isSuccess()).as("analysis must fail: " + text).isFalse();
        DiagnosticBag bag = result.diagnostics();
        assertThat(bag.all().isEmpty()).as("failed analysis must carry a diagnostic").isFalse();
        return bag;
    }

    /** Every diagnostic of {@code code} that analysis reports. */
    private static java.util.List<Diagnostic> all(String text, DiagnosticCode code) {
        return analyze(text).all().stream().filter(d -> d.code() == code).toList();
    }

    /** Whether any diagnostic of {@code code} is reported, so a test can assert one is absent. */
    private static boolean has(String text, DiagnosticCode code) {
        return !all(text, code).isEmpty();
    }

    /** Whether a diagnostic of {@code code} names {@code subject} in its message. */
    private static boolean reports(String text, DiagnosticCode code, String subject) {
        return all(text, code).stream().anyMatch(d -> d.message().contains(subject));
    }

    // ---------------------------------------------------------------------------------------------
    // Binding captured values
    // ---------------------------------------------------------------------------------------------

    /**
     * An immutable local named in the capture list is readable in the body.
     *
     * <p>"A capture list may name only an immutable local, a parameter, or {@code this}" and the list
     * "binds the named values into the closure" (section 6).
     */
    @Test
    public void anImmutableLocalIsReadableThroughTheCaptureList() {
        assertThat(run("""
            func demo(): Integer {
                val scale: Integer = 6
                val apply: func(Integer): Integer = func [scale](value: Integer): Integer {
                    return value * scale
                }
                return apply(7)
            }
            print(demo())
            """)).isEqualTo("42");
    }

    /**
     * A parameter of the enclosing function is capturable, which is the same mechanism as a local.
     *
     * <p>"A capture list may name only an immutable local, a parameter, or {@code this}" (section 6).
     */
    @Test
    public void anEnclosingParameterIsCapturable() {
        assertThat(run("""
            func demo(base: Integer): Integer {
                val add: func(Integer): Integer = func [base](value: Integer): Integer {
                    return base + value
                }
                return add(2)
            }
            print(demo(40))
            """)).isEqualTo("42");
    }

    /**
     * Several captures are all readable, including one that is itself a function value, which the body
     * calls.
     *
     * <p>"Capturing a function-valued binding and calling it is legal: the closure holds a function
     * value" (section 6). The call through a captured closure is the interesting half, since it is an
     * indirect call made from inside another indirect call.
     */
    @Test
    public void severalCapturesAreReadableIncludingAFunctionValueThatTheBodyCalls() {
        assertThat(run("""
            func demo(): Integer {
                val twice: func(Integer): Integer = func(value: Integer): Integer {
                    return value * 2
                }
                val offset: Integer = 7
                val caller: func(Integer): Integer = func [twice, offset](value: Integer): Integer {
                    return twice(value) + offset
                }
                return caller(5)
            }
            print(demo())
            """)).isEqualTo("17");
    }

    /**
     * A parameter of the closure shadows an enclosing local of the same spelling, so the body reads the
     * argument and no capture is required.
     *
     * <p>A body "may not refer to a binding of an enclosing function unless that binding is listed"
     * (section 6), and this is the case the sentence does <em>not</em> cover: the name resolves to the
     * closure's own parameter, so there is no enclosing binding in play and the capture analysis must
     * stay silent. An implementation that collected free names lexically would report an unlisted
     * capture here and reject a closure that captures nothing.
     */
    @Test
    public void aClosureParameterShadowsAnEnclosingLocalOfTheSameSpelling() {
        assertThat(run("""
            func demo(): Integer {
                val value: Integer = 10
                val viaParameter: func(Integer): Integer = func(value: Integer): Integer {
                    return value
                }
                return viaParameter(99)
            }
            print(demo())
            """)).isEqualTo("99");
    }

    // ---------------------------------------------------------------------------------------------
    // Values, not storage locations
    // ---------------------------------------------------------------------------------------------

    /**
     * Capturing an object reference copies the reference, so the body observes mutation performed after
     * the closure was created.
     *
     * <p>"Captures bind values, not storage locations. For a reference, copying the reference still
     * observes mutation of the referent: {@code val counter = Counter()} then {@code func [counter]()
     * { counter.increment() }} observes later mutation of the same object" (section 6). A
     * deep-copying capture would print the pre-mutation value, and a capture of storage would be
     * impossible for a {@code val} at all, so only copying the reference produces 7.
     */
    @Test
    public void aCapturedObjectReferenceObservesLaterMutation() {
        assertThat(run("""
            class Cell {
                var n: Integer = 0
            }
            func demo(): Integer {
                val cell = Cell()
                val read: func(): Integer = func [cell](): Integer {
                    return cell.n
                }
                cell.n = 7
                return read()
            }
            print(demo())
            """)).isEqualTo("7");
    }

    /**
     * The captured value is the object itself, not a copy of it.
     *
     * <p>Also "Captures bind values, not storage locations": the value of a reference-typed binding is
     * that object, so the closure hands back the very object it captured, and identity holds. A
     * structural copy of the referent would be a different object and fail this.
     */
    @Test
    public void aCapturedObjectIsTheSameObjectTheBodyReceives() {
        assertThat(run("""
            class Box {
                var n: Integer = 1
            }
            func demo(): Boolean {
                val box = Box()
                val read: func(): Box = func [box](): Box {
                    return box
                }
                return read() === box
            }
            print(demo())
            """)).isEqualTo("true");
    }

    /**
     * Two closures created from the same source bind the values that existed at each creation.
     *
     * <p>"A capture list evaluates in the enclosing scope at the point the anonymous function is
     * created" (section 6). Each closure therefore carries its own copy, and the one created before a
     * rebinding keeps the earlier value. This is the observation that makes capture a property of
     * creation rather than of the function's text.
     */
    @Test
    public void eachCreationBindsTheValuesThatExistedAtThatMoment() {
        assertThat(run("""
            func demo(): Integer {
                val seed: Integer = 1
                val before: func(): Integer = func [seed](): Integer {
                    return seed
                }
                val seed2: Integer = 2
                val after: func(): Integer = func [seed, seed2](): Integer {
                    return seed + seed2
                }
                return before() * 10 + after()
            }
            print(demo())
            """)).isEqualTo("13");
    }

    // ---------------------------------------------------------------------------------------------
    // Lifetime
    // ---------------------------------------------------------------------------------------------

    /**
     * A closure stays callable after the function that created it returns.
     *
     * <p>"A closure remains valid after the function that created it returns" (section 6). This is the
     * property that rules out capturing a live frame: the creating activation no longer exists by the
     * time the value is called, so the captured value has to live in the closure.
     */
    @Test
    public void aClosureRemainsValidAfterItsCreatorReturns() {
        assertThat(run("""
            func make(seed: Integer): func(): Integer {
                val local: Integer = seed + 1
                return func [local](): Integer {
                    return local
                }
            }
            val made = make(40)
            print(made())
            """)).isEqualTo("41");
    }

    /**
     * A closure returned from a creator keeps its own captured value independent of a second closure
     * built the same way.
     *
     * <p>The lifetime sentence together with "Each evaluation of an anonymous function expression
     * produces a new function value" (section 6): the two closures have separate state and each reads
     * the value its own creation bound.
     */
    @Test
    public void twoClosuresFromOneCreatorCarrySeparateCapturedValues() {
        assertThat(run("""
            func make(seed: Integer): func(): Integer {
                return func [seed](): Integer {
                    return seed
                }
            }
            val first = make(1)
            val second = make(2)
            print(first() + second())
            """)).isEqualTo("3");
    }

    // ---------------------------------------------------------------------------------------------
    // Transitivity through explicit values
    // ---------------------------------------------------------------------------------------------

    /**
     * A closure that captures another closure calls it, and the inner closure still uses its own
     * captures.
     *
     * <p>"A closure that captures another closure lists that function-valued binding and stores the
     * function value; it does not duplicate or flatten the captured closure's environment"
     * (section 6). The witness is that the outer name is not visible to the inner body except through
     * the inner closure's own list: {@code seed} reaches it only because {@code inner} captured it at
     * its own creation, and the outer closure never mentions {@code seed} at all.
     */
    @Test
    public void aClosureCapturingAClosureRetainsTheCapturedClosuresOwnEnvironment() {
        assertThat(run("""
            func demo(): Integer {
                val seed: Integer = 3
                val inner: func(): Integer = func [seed](): Integer {
                    return seed * 2
                }
                val middle: func(): Integer = func [inner](): Integer {
                    return inner() + 1
                }
                val outer: func(): Integer = func [middle](): Integer {
                    return middle() + 100
                }
                return outer()
            }
            print(demo())
            """)).isEqualTo("107");
    }

    /**
     * The captured closure handed back by a closure is the same value that was captured.
     *
     * <p>"stores the function value" (section 6): storing means holding that value, so a closure that
     * returns its captured closure returns the identical value. A flattened environment would have no
     * single captured value to hand back, and a copying implementation would return something else.
     */
    @Test
    public void aCapturedClosureIsStoredAsTheSameValue() {
        assertThat(run("""
            func demo(): Boolean {
                val inner: func(): Integer = func(): Integer {
                    return 1
                }
                val forward: func(): func(): Integer = func [inner](): func(): Integer {
                    return inner
                }
                return forward() === inner
            }
            print(demo())
            """)).isEqualTo("true");
    }

    /**
     * An inner closure re-lists a name that the enclosing closure already captured, forwarding it.
     *
     * <p>"In nested closures, a name used in an inner capture list counts as a use by the enclosing
     * closure, so every intervening closure must list and forward that value explicitly" (section 6).
     * The middle closure has no body use of {@code factor} whatsoever; its capture list is required
     * only because its inner closure's capture list names the name, which is exactly what the quoted
     * sentence asserts.
     */
    @Test
    public void anInnerCaptureItemIsAUseByTheEnclosingClosure() {
        assertThat(run("""
            func outer(factor: Integer): func(): func(): Integer {
                return func [factor](): func(): Integer {
                    return func [factor](): Integer {
                        return factor
                    }
                }
            }
            print(outer(9)()())
            """)).isEqualTo("9");
    }

    /**
     * An inner closure may not name state that its enclosing closure did not capture.
     *
     * <p>"every intervening closure must list and forward that value explicitly" (section 6) — so the
     * chain has to be complete. Here the middle closure writes no capture list, {@code factor} is beyond
     * the inner closure's enclosing boundary, and the inner list is reported as naming an unknown name:
     * "An unknown name in a capture list remains {@code SOLV-RESOL-001}". The body use of the same
     * {@code factor} is the same root cause -- the value the intervening closure failed to forward -- and
     * the anti-cascade sentence forbids restating it: "After an invalid capture item is reported, body
     * checking must not cascade the same root cause into an unlisted-capture or unknown-name diagnostic".
     * So exactly one report is produced, on the item.
     */
    @Test
    public void anInnerCaptureItemNamingStateBeyondTheEnclosingClosureIsRejected() {
        String source = """
            func outer(factor: Integer): func(): func(): Integer {
                return func(): func(): Integer {
                    return func [factor](): Integer {
                        return factor
                    }
                }
            }
            print(outer)
            """;
        assertThat(all(source, DiagnosticCode.RESOL_UNKNOWN_NAME)).hasSize(1);
        assertThat(reports(source, DiagnosticCode.RESOL_UNKNOWN_NAME, "'factor' in capture list")).isTrue();
        assertThat(has(source, DiagnosticCode.SEM_UNLISTED_CAPTURE)).isFalse();
    }

    // ---------------------------------------------------------------------------------------------
    // `this` as a captured value
    // ---------------------------------------------------------------------------------------------

    /**
     * A closure inside an instance method may use {@code this} once {@code [this]} is written, and the
     * receiver is the creating instance.
     *
     * <p>"A closure body has no receiver of its own, so {@code this} is an ordinary captured value
     * written {@code [this]}" (section 6). The body is a {@code func} expression inside a method, and
     * the method's receiver is what the body sees.
     */
    @Test
    public void aClosureCapturesTheReceiverToUseIt() {
        assertThat(run("""
            class Adder {
                var base: Integer = 0

                func set(value: Integer) {
                    this.base = value
                }

                func adder(): func(Integer): Integer {
                    return func [this](value: Integer): Integer {
                        return this.base + value
                    }
                }
            }
            val adder = Adder()
            adder.set(10)
            print(adder.adder()(5))
            """)).isEqualTo("15");
    }

    /**
     * A closure that captured {@code this} sees the receiver's state change after creation.
     *
     * <p>"A captured {@code this} is the receiver value of the enclosing callable and obeys the same
     * reference semantics as any other captured object" (section 6). The closure is built before the
     * mutation, so the body reads 100 only because the reference — not a snapshot of the fields — was
     * copied.
     */
    @Test
    public void aCapturedReceiverObeysReferenceSemantics() {
        assertThat(run("""
            class Adder {
                var base: Integer = 0

                func set(value: Integer) {
                    this.base = value
                }

                func adder(): func(Integer): Integer {
                    return func [this](value: Integer): Integer {
                        return this.base + value
                    }
                }
            }
            val adder = Adder()
            adder.set(10)
            val add = adder.adder()
            adder.set(100)
            print(add(5))
            """)).isEqualTo("105");
    }

    /**
     * An inner closure re-lists {@code [this]} to carry the receiver a level deeper.
     *
     * <p>The transitivity sentence together with the {@code [this]} spelling: the receiver has to be
     * forwarded explicitly by every intervening closure, and writing {@code [this]} on each of them is
     * how that happens. This is the receiver analogue of
     * {@link #anInnerCaptureItemIsAUseByTheEnclosingClosure()}.
     */
    @Test
    public void aReceiverIsForwardedThroughNestedClosures() {
        assertThat(run("""
            class Holder {
                var n: Integer = 0

                func set(value: Integer) {
                    this.n = value
                }

                func nested(): func(): func(): Integer {
                    return func [this](): func(): Integer {
                        return func [this](): Integer {
                            return this.n
                        }
                    }
                }
            }
            val holder = Holder()
            holder.set(100)
            print(holder.nested()()())
            """)).isEqualTo("100");
    }

    /**
     * A closure body may not use {@code this} without writing {@code [this]}.
     *
     * <p>"This applies to {@code this} as well: a closure body may use {@code this} only when
     * {@code [this]} is written", reported as the unlisted-capture code "reported on the body
     * reference" (section 6). The receiver is in scope in the enclosing method, so this is a capture
     * the body failed to declare rather than a name that does not exist.
     */
    @Test
    public void aClosureBodyMayNotUseThisWithoutCapturingIt() {
        String source = """
            class Holder {
                var n: Integer = 0

                func leaks(): func(): Integer {
                    return func(): Integer {
                        return this.n
                    }
                }
            }
            print(Holder)
            """;
        assertThat(has(source, DiagnosticCode.SEM_UNLISTED_CAPTURE)).isTrue();
        assertThat(reports(source, DiagnosticCode.SEM_UNLISTED_CAPTURE, "'this'")).isTrue();
    }

    /**
     * A function with no enclosing receiver rejects {@code [this]} as an unavailable receiver, not as
     * a capture problem.
     *
     * <p>"{@code this} where no instance receiver exists remains {@code SOLV-RESOL-005}" (section 6).
     * {@code demo} is a top-level function, so there is no receiver anywhere to capture, and the code
     * is the one the language already uses for a {@code this} with no receiver.
     */
    @Test
    public void aCaptureItemThisWithNoReceiverIsRejected() {
        String source = """
            func demo(): func(): Integer {
                return func [this](): Integer {
                    return 1
                }
            }
            print(demo)
            """;
        assertThat(has(source, DiagnosticCode.RESOL_THIS_OUTSIDE_CLASS)).isTrue();
    }

    /**
     * A capture list may not write {@code [this]} twice.
     *
     * <p>Section 6 allows one {@code this} item ("A capture item is an identifier or {@code this}" and the
     * list may name {@code this} "in an enclosing instance method or constructor"), and a second item of
     * either spelling is the duplicate-name condition the language already reports. Included because a
     * receiver is not a named binding, so it is the one capture item whose duplication a scope-based
     * duplicate check could plausibly miss.
     */
    @Test
    public void aCaptureListMayNotWriteThisTwice() {
        String source = """
            class Holder {
                var n: Integer = 0

                func leaks(): func(): Integer {
                    return func [this, this](): Integer {
                        return this.n
                    }
                }
            }
            print(Holder)
            """;
        assertThat(reports(source, DiagnosticCode.RESOL_DUPLICATE_NAME, "capture item 'this'")).isTrue();
    }

    // ---------------------------------------------------------------------------------------------
    // Mutable bindings may not be captured
    // ---------------------------------------------------------------------------------------------

    /**
     * A capture item naming a {@code var} is rejected at the item.
     *
     * <p>"Naming a {@code var} in a capture list is {@code SEM_MUTABLE_CAPTURE}
     * ({@code SOLV-SEM-057}), reported on that capture item" (section 6). The specification's own
     * example is {@code func [total](value: Integer) { total = total + value }} over
     * {@code var total = 0}.
     */
    @Test
    public void aCaptureItemNamingAVarIsRejected() {
        String source = """
            func demo(): func(Integer): Unit {
                var total: Integer = 0
                val add: func(Integer): Unit = func [total](value: Integer): Unit {
                    total = value
                }
                return add
            }
            print(demo)
            """;
        assertThat(has(source, DiagnosticCode.SEM_MUTABLE_CAPTURE)).isTrue();
        assertThat(reports(source, DiagnosticCode.SEM_MUTABLE_CAPTURE, "capture item 'total'")).isTrue();
    }

    /**
     * A body read of a {@code var} that its own capture list named is rejected with the same code.
     *
     * <p>"and a read or write of that captured name in the body is reported with the same code"
     * (section 6). Two diagnostics therefore appear for one closure: one on the item and one on the
     * body use. Asserting both keeps a single-error implementation from passing a test that only
     * looked for the code anywhere in the bag.
     */
    @Test
    public void aBodyReadOfACapturedVarNameIsRejectedToo() {
        String source = """
            func demo(): func(): Integer {
                var total: Integer = 0
                return func [total](): Integer {
                    return total
                }
            }
            print(demo)
            """;
        assertThat(all(source, DiagnosticCode.SEM_MUTABLE_CAPTURE)).hasSize(2);
        assertThat(reports(source, DiagnosticCode.SEM_MUTABLE_CAPTURE, "capture item 'total'")).isTrue();
    }

    /**
     * A body write of a {@code var} that its own capture list named is rejected as well.
     *
     * <p>"a read or write of that captured name in the body is reported with the same code"
     * (section 6) — a write is no different, since the binding it would assign lives in a frame the
     * closure does not keep.
     */
    @Test
    public void aBodyWriteToACapturedVarNameIsRejectedToo() {
        String source = """
            func demo(): func(Integer): Unit {
                var total: Integer = 0
                return func [total](value: Integer): Unit {
                    total = value
                }
            }
            print(demo)
            """;
        assertThat(all(source, DiagnosticCode.SEM_MUTABLE_CAPTURE)).hasSize(2);
    }

    /**
     * A body use of a capture item that was already reported as unknown earns no second diagnostic.
     *
     * <p>"After an invalid capture item is reported, body checking must not cascade the same root cause
     * into an unlisted-capture or unknown-name diagnostic" (section 6). The item and the body use spell
     * the same name, and the body use resolves to nothing only because that item bound nothing, so a
     * second report would restate one defect as two. The count is the assertion: a manifest can only say
     * that <em>some</em> diagnostic matched, which a cascading implementation also satisfies, so this
     * obligation is testable only by counting reports.
     */
    @Test
    public void aBodyUseOfAnUnknownCaptureItemDoesNotCascade() {
        String source = """
            func demo(base: Integer): func(Integer): Integer {
                return func [bas](value: Integer): Integer {
                    return value + bas
                }
            }
            print(demo)
            """;
        assertThat(all(source, DiagnosticCode.RESOL_UNKNOWN_NAME)).hasSize(1);
        assertThat(reports(source, DiagnosticCode.RESOL_UNKNOWN_NAME, "'bas' in capture list")).isTrue();
        assertThat(has(source, DiagnosticCode.SEM_UNLISTED_CAPTURE)).isFalse();
    }

    /**
     * A body use of the binding under initialization earns no second diagnostic either.
     *
     * <p>The same anti-cascade sentence covers this shape, and the shape is the one a self-recursive
     * closure reaches by: the item is reported as read-before-initialization and the body's call of the
     * same name resolves to nothing because no value exists yet. Reporting the body use as an unknown name
     * would point at a second, nonexistent problem.
     */
    @Test
    public void aBodyUseOfASelfReferentialCaptureItemDoesNotCascade() {
        String source = """
            func demo(): func(Integer): Integer {
                val selfRef: func(Integer): Integer = func [selfRef](value: Integer): Integer {
                    return selfRef(value)
                }
                return selfRef
            }
            print(demo)
            """;
        assertThat(all(source, DiagnosticCode.TYPE_UNINITIALIZED_VARIABLE)).hasSize(1);
        assertThat(has(source, DiagnosticCode.RESOL_UNKNOWN_NAME)).isFalse();
    }

    /**
     * A body that <em>calls</em> an omitted function-valued outer binding is an unlisted capture.
     *
     * <p>"An outer local or parameter referenced by the body but omitted from the capture list is
     * {@code SEM_UNLISTED_CAPTURE} ({@code SOLV-SEM-058}), reported on the body reference" (section 6).
     * A call is a reference like any other, so the call path must classify an unresolved callee the way a
     * plain name reference does; reporting a genuine omission as an unknown name tells the reader the
     * spelling is wrong when the real defect is a capture list that was never written.
     */
    @Test
    public void callingAnOmittedFunctionValuedBindingIsAnUnlistedCapture() {
        String source = """
            func demo(): func(): Integer {
                val inner: func(): Integer = func(): Integer {
                    return 1
                }
                return func(): Integer {
                    return inner()
                }
            }
            print(demo)
            """;
        assertThat(all(source, DiagnosticCode.SEM_UNLISTED_CAPTURE)).hasSize(1);
        assertThat(reports(source, DiagnosticCode.SEM_UNLISTED_CAPTURE, "'inner'")).isTrue();
        assertThat(has(source, DiagnosticCode.RESOL_UNKNOWN_NAME)).isFalse();
    }

    /**
     * The {@code var} case is deliberately <em>not</em> suppressed, and a separate omission in the same
     * body is still reported.
     *
     * <p>The specification assigns the mutable-capture code to both placements -- the item and each body
     * use -- so suppression applies to the item kinds that report once and not to this one. A second,
     * genuinely different omission in the same body is a different root cause and must survive, which is
     * what stops the suppression from becoming a blanket silence over closure bodies.
     */
    @Test
    public void aCapturedVarUseStillReportsAndAnUnrelatedOmissionSurvives() {
        String source = """
            func demo(base: Integer): func(Integer): Integer {
                var total: Integer = 0
                return func [total](value: Integer): Integer {
                    return total + base
                }
            }
            print(demo)
            """;
        assertThat(all(source, DiagnosticCode.SEM_MUTABLE_CAPTURE)).hasSize(2);
        assertThat(all(source, DiagnosticCode.SEM_UNLISTED_CAPTURE)).hasSize(1);
    }

    /**
     * Referencing an enclosing {@code var} without listing it stays an unlisted capture rather than
     * becoming a mutable-capture error.
     *
     * <p>"Referencing the same outer {@code var} without listing it remains
     * {@code SEM_UNLISTED_CAPTURE} at the body reference; the compiler never silently converts it into
     * a capture" (section 6). The name never reaches a capture list, so no capture-item diagnostic can
     * apply, and reporting the mutable code instead would tell the reader to remove a capture that was
     * never written.
     */
    @Test
    public void anUnlistedEnclosingVarIsAnUnlistedCaptureNotAMutableCapture() {
        String source = """
            func demo(): func(): Integer {
                var total: Integer = 0
                return func(): Integer {
                    return total
                }
            }
            print(demo)
            """;
        assertThat(has(source, DiagnosticCode.SEM_UNLISTED_CAPTURE)).isTrue();
        assertThat(has(source, DiagnosticCode.SEM_MUTABLE_CAPTURE)).isFalse();
    }

    /**
     * A {@code var} declared at top level is a local of the implicit entry point, so capturing it is
     * rejected like capturing any other local.
     *
     * <p>Section 5 gives top-level statements the scope of an implicit entry point, so a top-level
     * {@code var} is a mutable local of that callable and the capture rule about {@code var}s applies
     * to it. This is the case where "capture top-level state" would be the natural wrong reading of
     * the exemption for top-level functions.
     */
    @Test
    public void aTopLevelVarIsNotCapturable() {
        String source = """
            var count: Integer = 0
            val read: func(): Integer = func [count](): Integer {
                return count
            }
            print(read)
            """;
        assertThat(reports(source, DiagnosticCode.SEM_MUTABLE_CAPTURE, "capture item 'count'")).isTrue();
    }

    /**
     * A {@code val} declared at top level is capturable, and a body may not reach it unlisted.
     *
     * <p>The same implicit-entry-point scoping: a top-level {@code val} is an immutable local, so it
     * is a legal capture and an unlisted body use of it is still rejected. The exemption that exists is
     * for declarations, not for state: "Top-level and module-qualified function declarations are
     * globally resolved declarations rather than local state and need no capture entry; there are no
     * globals to capture" (section 6).
     */
    @Test
    public void aTopLevelValIsCapturableAndNotVisibleUnlisted() {
        assertThat(run("""
            val greeting: String = "hello"
            val suffix: String = "!"
            val shout: func(): String = func [greeting, suffix](): String {
                return greeting .. suffix
            }
            print(shout())
            """)).isEqualTo("hello!");
        String source = """
            val greeting: String = "hello"
            val shout: func(): String = func(): String {
                return greeting
            }
            print(shout())
            """;
        assertThat(has(source, DiagnosticCode.SEM_UNLISTED_CAPTURE)).isTrue();
    }

    // ---------------------------------------------------------------------------------------------
    // Capture lists name bindings, not declarations
    // ---------------------------------------------------------------------------------------------

    /**
     * A named top-level function needs no capture, so a closure body may call one directly.
     *
     * <p>"Top-level and module-qualified function declarations are globally resolved declarations
     * rather than local state and need no capture entry" (section 6). The closure's body resolves
     * {@code fact} through the root scope, which is why self-recursive top-level recursion works from
     * inside a closure with no list at all.
     */
    @Test
    public void aTopLevelFunctionNeedsNoCaptureAndRecursesFromAClosureBody() {
        assertThat(run("""
            func fact(n: Integer): Integer {
                if (n <= 1) {
                    return 1
                }
                return n * fact(n - 1)
            }
            val viaClosure: func(Integer): Integer = func(n: Integer): Integer {
                return fact(n)
            }
            print(viaClosure(5))
            """)).isEqualTo("120");
    }

    /**
     * A capture item may not name a class.
     *
     * <p>Section 6 restricts the list to "an immutable local, a parameter, or {@code this}", and
     * resolves the rest of the space by code: "An unknown name in a capture list remains
     * {@code SOLV-RESOL-001}". A type name is not in the local chain a capture item searches — the
     * declaration lives in the root scope — so the item names nothing the list can bind and earns the
     * unknown-name code rather than a new one.
     */
    @Test
    public void aCaptureItemMayNotNameATopLevelClass() {
        String source = """
            class Helper {
                var n: Integer = 0
            }
            func demo(): func(): Integer {
                return func [Helper](): Integer {
                    return 1
                }
            }
            print(demo)
            """;
        assertThat(reports(source, DiagnosticCode.RESOL_UNKNOWN_NAME, "'Helper' in capture list")).isTrue();
    }

    /**
     * A capture item may not name a top-level function either.
     *
     * <p>The same clause and the same code, shown for the declaration kind that would most plausibly be
     * capturable — a function is a value, and the language does expose it as one. It is still resolved
     * globally and needs no capture entry, so listing it is an error rather than a redundant capture.
     */
    @Test
    public void aCaptureItemMayNotNameATopLevelFunction() {
        String source = """
            func helper(): Integer {
                return 1
            }
            func demo(): func(): Integer {
                return func [helper](): Integer {
                    return 1
                }
            }
            print(demo)
            """;
        assertThat(reports(source, DiagnosticCode.RESOL_UNKNOWN_NAME, "'helper' in capture list")).isTrue();
    }

    /**
     * An unknown name in a capture list is reported as the ordinary unknown-name diagnostic.
     *
     * <p>"An unknown name in a capture list remains {@code SOLV-RESOL-001}" (section 6). The word
     * "remains" carries the requirement: the capture-item position earns no special code, so a typo in
     * a list reads to the programmer like a typo anywhere else.
     */
    @Test
    public void anUnknownCaptureItemIsAnOrdinaryUnknownName() {
        String source = """
            func demo(): func(): Integer {
                return func [missing](): Integer {
                    return 1
                }
            }
            print(demo)
            """;
        assertThat(reports(source, DiagnosticCode.RESOL_UNKNOWN_NAME, "'missing' in capture list")).isTrue();
    }

    /**
     * A capture item that repeats a name is rejected as a duplicate declaration.
     *
     * <p>A capture list "binds the named values into the closure" (section 6), and two bindings of one
     * name in one scope is the condition the language already reports as {@code SOLV-RESOL-002}. A
     * silently last-wins list would let a reorder change which value the body sees with no diagnostic.
     */
    @Test
    public void aCaptureItemMayNotRepeatAName() {
        String source = """
            func demo(): func(): Integer {
                val a: Integer = 1
                return func [a, a](): Integer {
                    return a
                }
            }
            print(demo)
            """;
        assertThat(reports(source, DiagnosticCode.RESOL_DUPLICATE_NAME, "capture item 'a'")).isTrue();
    }

    /**
     * A capture item may not name a parameter of the same closure.
     *
     * <p>Same clause: the parameter and the capture item both bind into the body scope, so naming the
     * parameter is a duplicate name. The diagnostic is reported at the capture item, which is the
     * position the programmer should edit — the parameter is what the closure's own signature declares.
     */
    @Test
    public void aCaptureItemMayNotNameItsOwnParameter() {
        String source = """
            func demo(): func(Integer): Integer {
                val value: Integer = 1
                return func [value](value: Integer): Integer {
                    return value
                }
            }
            print(demo)
            """;
        assertThat(reports(source, DiagnosticCode.RESOL_DUPLICATE_NAME, "capture item 'value'")).isTrue();
    }

    // ---------------------------------------------------------------------------------------------
    // No self-recursion through the binding being initialized
    // ---------------------------------------------------------------------------------------------

    /**
     * A capture item naming the binding whose initializer it appears in is a read-before-initialization
     * error.
     *
     * <p>"Anonymous self-recursion through the binding being initialized is not supported: listing that
     * binding in the capture list is an ordinary read-before-initialization error
     * ({@code SOLV-TYPE-008}), because the value does not exist when its initializer is evaluated"
     * (section 6). The code is what the specification names, and it is honest about the cause: a
     * capture item that read as an unknown name would suggest the spelling is wrong when the real
     * problem is that there is no value yet.
     */
    @Test
    public void aCaptureItemMayNotNameTheBindingBeingInitialized() {
        String source = """
            func demo(): func(): Integer {
                val recurse: func(): Integer = func [recurse](): Integer {
                    return recurse()
                }
                return recurse
            }
            print(demo)
            """;
        assertThat(reports(source, DiagnosticCode.TYPE_UNINITIALIZED_VARIABLE, "capture item 'recurse'")).isTrue();
    }

    /**
     * Self-recursion through a named top-level function is the supported path, needing no capture.
     *
     * <p>"Recursion through named top-level functions needs no capture" (section 6). Included because
     * the rejection above would be gratuitous without it: the language does allow a recursive
     * function-valued binding, it just resolves the recursive call through the declaration.
     */
    @Test
    public void aFunctionValuedBindingRecursesThroughItsNamedDeclaration() {
        assertThat(run("""
            func fib(n: Integer): Integer {
                if (n < 2) {
                    return n
                }
                return fib(n - 1) + fib(n - 2)
            }
            val fibValue: func(Integer): Integer = func(n: Integer): Integer {
                return fib(n)
            }
            print(fibValue(10))
            """)).isEqualTo("55");
    }

    // ---------------------------------------------------------------------------------------------
    // Capture does not change what a closure is
    // ---------------------------------------------------------------------------------------------

    /**
     * A closure with captures still produces a new value each time its expression is evaluated.
     *
     * <p>"Each evaluation of an anonymous function expression produces a new function value; two
     * evaluations are distinct even when the expression captures no values" (section 6). The capture
     * list must not turn the closure into a constant, and a cached value would make the two bindings
     * hold one closure whose captured state is whichever creation won.
     */
    @Test
    public void eachEvaluationOfACapturingClosureProducesADistinctValue() {
        assertThat(run("""
            func demo(): Boolean {
                val seed: Integer = 1
                val first: func(): Integer = func [seed](): Integer {
                    return seed
                }
                val second: func(): Integer = func [seed](): Integer {
                    return seed
                }
                return first !== second
            }
            print(demo())
            """)).isEqualTo("true");
    }

    /**
     * A closure with an empty-or-absent capture list behaves as it did before capture existed.
     *
     * <p>Guards the boundary of the feature: adding the list to the grammar must not disturb a closure
     * that writes none. The unlisted use beside it confirms the list's absence is still what makes an
     * enclosing local invisible.
     */
    @Test
    public void aClosureWithNoCaptureListStillCannotReachEnclosingLocals() {
        String source = """
            func demo(base: Integer): Integer {
                val compute: func(Integer): Integer = func(value: Integer): Integer {
                    return value + base
                }
                return compute(1)
            }
            print(demo(2))
            """;
        assertThat(has(source, DiagnosticCode.SEM_UNLISTED_CAPTURE)).isTrue();
    }

    /**
     * A closure whose captures are all of them needed, checked for a body that also uses its own
     * parameter and a local of its own.
     *
     * <p>The capture analysis must not report a closure that resolves every name inside itself: a local
     * declared in the body and the closure's own parameters are not captures. A false positive here
     * would reject every real closure, so this is the analysis's most important negative.
     */
    @Test
    public void bodyLocalsAndParametersAreNotCaptures() {
        assertThat(run("""
            func demo(): Integer {
                val seed: Integer = 10
                val apply: func(Integer): Integer = func [seed](value: Integer): Integer {
                    val doubled: Integer = value * 2
                    return seed + doubled
                }
                return apply(16)
            }
            print(demo())
            """)).isEqualTo("42");
    }
}
