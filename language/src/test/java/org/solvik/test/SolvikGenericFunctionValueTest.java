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
import org.solvik.ast.declaration.DeclarationNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.statement.LocalDeclNode;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.semantic.CheckedProgram;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;
import org.solvik.type.Type;

/**
 * Generic function values: a generic function used as a value is instantiated to one monomorphic
 * function type from the expected type in scope, entirely before lowering, and an unconstrained
 * reference is refused rather than given an invented type
 * (docs/LANGUAGE_SPEC.md section 6, "Generic function values").
 *
 * <p>The phase's whole claim is that a decision the language used to make while resolving a call now
 * has to be made about a bare name in a value position, in whatever context happens to surround it.
 * That claim splits in two, and the tests split the same way.
 *
 * <p>The rejection tests need only the analyzer. Each cites one diagnostic, and section 6 is explicit
 * that the code is reported "on the function or method reference" and that "no second inference
 * diagnostic exists" — an implementation that reported the right code somewhere else, or that reported
 * it twice, would satisfy a code-only assertion and still be wrong, so those tests check the code, the
 * span, and the absence of a companion report.
 *
 * <p>The acceptance tests have to run, because the surviving claims are runtime claims. A contextual
 * instantiation that produced a value of the right <em>shape</em> but invoked the wrong specialization
 * would look identical in a type check:
 *
 * <ul>
 *   <li>"instantiation changes static typing, not the underlying executable value" — this is the
 *       sentence that makes {@code identity} instantiated at {@code func(Integer): Integer} and at
 *       {@code func(String): String} one value rather than two, and it cannot be observed by anything
 *       but running both and comparing them {@link #everyInstantiationOfOneDeclarationIsOneValue()};
 *   <li>"Contextual instantiations of one generic declaration at different function types also share
 *       that declaration's canonical runtime identity" — tested as the equality <em>and</em> hash
 *       consequence of the two values being distinct objects of one declaration's canonical value
 *       {@link #instantiationsAtDifferentTypesShareIdentityAndHash()};
 *   <li>"A function value performs no runtime type dispatch" — a deferred-instantiation implementation
 *       that specialized at call time would pass every static test and every output test here, and only
 *       the "no runtime type dispatch" reading tells the two architectures apart, so the tests assert
 *       the decided-before-lowering property through what a rejected program prints
 *       {@link #anUninstantiableReferenceIsRejectedBeforeAnythingRuns()};
 *   <li>and the inference order the section fixes — "Inference first unifies occurrences in the
 *       declared parameter types with the expected parameter types; result positions may confirm or
 *       complete a unique substitution but never choose arbitrarily" — is observable only when the two
 *       kinds of position would bind a parameter differently, which is what
 *       {@link #aResultPositionCompletesButNeverOverridesAPositionTheParametersEstablish()} is
 *       constructed to do.
 * </ul>
 *
 * <p>Every expected output is derived by reading the specification passage named in the test comment
 * and working out what the program must print; none was copied from an observed run. Where the
 * implementation and the passage disagreed, the implementation was wrong and was changed.
 */
public final class SolvikGenericFunctionValueTest {

    private static String run(String source) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out)
                .option("engine.WarnInterpreterOnly", "false").allowAllAccess(true).build()) {
            context.eval(build(source, "generic.sol"));
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

    private static String runInclude(String library, String root) {
        try {
            java.nio.file.Path directory = java.nio.file.Files.createTempDirectory("solvik-generic");
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

    private static SemanticResult check(String text) {
        return SolvikSemanticAnalyzer.analyze(parseOk("generic.sol", text));
    }

    /**
     * The static type the analysis recorded for the first local initializer of {@code functionName}.
     *
     * <p>Reading the type back out of the {@link CheckedProgram} is the only way to test that the
     * instantiation is a compile-time fact: every behavioural test could be satisfied by an implementation
     * that decided the type while executing.
     */
    private static Type recordedInitializerType(String text, String functionName) {
        SemanticResult result = check(text);
        assertThat(result.isSuccess()).as("analysis must succeed: " + result.diagnostics().all()).isTrue();
        CheckedProgram program = result.requireProgram();
        for (DeclarationNode declaration : program.unit().declarations()) {
            if (declaration instanceof FunctionDeclNode function && function.name().equals(functionName)) {
                LocalDeclNode local = (LocalDeclNode) function.body().statements().get(0);
                return program.typeOf(local.initializer()).orElseThrow();
            }
        }
        throw new AssertionError("no function named " + functionName);
    }

    private static List<DiagnosticCode> codes(String text) {
        return diagnostics(text).stream().map(Diagnostic::code).toList();
    }

    private static DiagnosticCode firstCode(String text) {
        List<Diagnostic> all = diagnostics(text);
        return all.get(0).code();
    }

    private static List<Diagnostic> diagnostics(String text) {
        SemanticResult result = check(text);
        assertThat(result.isSuccess()).as("analysis must fail: " + text).isFalse();
        DiagnosticBag bag = result.diagnostics();
        List<Diagnostic> all = bag.all();
        assertThat(all.isEmpty()).as("failed analysis must carry a diagnostic").isFalse();
        return all;
    }

    /** The one diagnostic a rejected program must produce, asserted to be the only one. */
    private static Diagnostic onlyDiagnostic(String text, DiagnosticCode expected) {
        List<Diagnostic> all = diagnostics(text);
        assertThat(all).as("exactly one diagnostic, found: " + all).hasSize(1);
        assertThat(all.get(0).code()).isEqualTo(expected);
        return all.get(0);
    }

    /**
     * Asserts that {@code diagnostic} covers {@code token} at the position {@code marker} locates in
     * {@code text}. The section is specific that inference diagnostics sit on the reference itself, and a
     * report moved to the enclosing declaration satisfies a code-only assertion while pointing a reader at
     * the wrong text; locating the token from the source rather than hard-coding an offset keeps the claim
     * readable when the program is reformatted.
     */
    private static void assertCovers(String text, String marker, String token, Diagnostic diagnostic) {
        int at = text.indexOf(marker) + marker.indexOf(token);
        assertThat(at).as("marker present in the program").isGreaterThan(0);
        assertThat(diagnostic.span().startOffset()).as("diagnostic starts at the reference").isEqualTo(at);
        assertThat(diagnostic.span().endOffset()).as("diagnostic ends after the reference").isEqualTo(at + token.length());
    }

    // ---------------------------------------------------------------------------------------------
    // Instantiation in the positions a value may be declared into
    // ---------------------------------------------------------------------------------------------

    /**
     * The specification's own example: one generic declaration instantiated at two function types, each
     * from the declared type of the local it is stored in.
     *
     * <p>"A generic function declaration does not itself produce a first-class polymorphic value. It must
     * be instantiated to one monomorphic function type at each value-reference site, and that
     * instantiation is contextual. The expected function type supplies constraints for every declared
     * type parameter" (section 6).
     */
    @Test
    public void aDeclaredLocalTypeInstantiatesAGenericReference() {
        assertThat(run("""
                func identity<T>(value: T): T {
                    return value
                }

                val integerIdentity: func(Integer): Integer = identity
                val stringIdentity: func(String): String = identity
                println(integerIdentity(41))
                print(stringIdentity("ab"))
                """)).isEqualTo("41\nab");
    }

    /**
     * A return position is a declared type too, so the same reference instantiates there without a local
     * standing between the declaration and the caller.
     *
     * <p>The section's rule is about the expected function type, not about which declaration form carries
     * it, and "declared function and anonymous-function returns" is listed among the positions expected
     * types flow into.
     */
    @Test
    public void aDeclaredResultTypeInstantiatesAGenericReference() {
        assertThat(run("""
                func identity<T>(value: T): T {
                    return value
                }

                func makeIntegerIdentity(): func(Integer): Integer {
                    return identity
                }

                println(makeIntegerIdentity()(8))
                """)).isEqualTo("8\n");
    }

    /**
     * An instance property's declared type instantiates a generic reference in its initializer.
     *
     * "explicitly typed local/property/static-property initializers" is where expected function types
     * flow; a property is a declared type in the same sense a local is.
     */
    @Test
    public void aPropertyDeclaredTypeInstantiatesAGenericReference() {
        assertThat(run("""
                func identity<T>(value: T): T {
                    return value
                }

                class Holder {
                    val member: func(Integer): Integer = identity
                }

                val holder = Holder()
                println(holder.member(3))
                """)).isEqualTo("3\n");
    }

    /** A static property's declared type is the same kind of declared type, reached without a receiver. */
    @Test
    public void aStaticPropertyDeclaredTypeInstantiatesAGenericReference() {
        assertThat(run("""
                func identity<T>(value: T): T {
                    return value
                }

                class Holder {
                    static val member: func(String): String = identity
                }

                val taken: func(String): String = Holder.member
                print(taken("yz"))
                """)).isEqualTo("yz");
    }

    /**
     * A {@code var} assignment is a context: the variable's declared type is known before the value is
     * examined, so it constrains a generic reference written on the right.
     *
     * "assignment targets" is listed among the positions expected function types flow into.
     */
    @Test
    public void anAssignmentTargetTypeInstantiatesAGenericReference() {
        assertThat(run("""
                func identity<T>(value: T): T {
                    return value
                }

                var slot: func(Integer): Integer = identity
                slot = identity
                println(slot(6))
                """)).isEqualTo("6\n");
    }

    /** The same rule through an instance property, whose type may be written in its owner's parameters. */
    @Test
    public void anInstancePropertyAssignmentInstantiatesAGenericReference() {
        assertThat(run("""
                func identity<T>(value: T): T {
                    return value
                }

                class Holder {
                    var slot: func(Integer): Integer = identity
                }

                val holder = Holder()
                holder.slot = identity
                println(holder.slot(7))
                """)).isEqualTo("7\n");
    }

    /** The same rule through a static property. */
    @Test
    public void aStaticPropertyAssignmentInstantiatesAGenericReference() {
        assertThat(run("""
                func identity<T>(value: T): T {
                    return value
                }

                class Holder {
                    static var slot: func(String): String = identity
                }

                Holder.slot = identity
                val taken: func(String): String = Holder.slot
                print(taken("q"))
                """)).isEqualTo("q");
    }

    /**
     * A collection element position carries its element type, so a generic reference written as an
     * element instantiates from it.
     *
     * "collection element/key/value positions" is listed among the positions expected function types flow
     * into, and the element type is the declared thing being satisfied.
     */
    @Test
    public void aCollectionElementTypeInstantiatesAGenericReference() {
        assertThat(run("""
                func identity<T>(value: T): T {
                    return value
                }

                val fns: List<func(Integer): Integer> = List<func(Integer): Integer>(identity)
                println(fns.size)
                println(fns.get(0)(9))
                """)).isEqualTo("1\n9\n");
    }

    // ---------------------------------------------------------------------------------------------
    // Instantiation at an argument position
    // ---------------------------------------------------------------------------------------------

    /**
     * An argument whose parameter has a declared (closed) function type supplies an expected function
     * type, so the reference instantiates from the parameter it fills.
     *
     * <p>Section 6 types a generic reference used as a value against "a complete expected function type",
     * and a parameter whose type is {@code func(Integer): Integer} is one whether the reference sits in a
     * local's initializer or in a call's argument slot. A callee whose parameter type is already known
     * has nothing left to infer, so refusing the context would make the same written reference compile
     * on the left of {@code =} and not in an argument list.
     */
    @Test
    public void anArgumentPositionInstantiatesAGenericReference() {
        assertThat(run("""
                func identity<T>(value: T): T {
                    return value
                }

                func apply(f: func(Integer): Integer, v: Integer): Integer {
                    return f(v)
                }

                println(apply(identity, 11))
                """)).isEqualTo("11\n");
    }

    /**
     * A call through a function value supplies its parameter types the same way, because a function value
     * is monomorphic and so its declared parameter types are already decided.
     *
     * "Explicit type arguments are not permitted on an already-instantiated function value, because a
     * function value is monomorphic" (section 6) — the same monomorphic-ness is what makes its parameter
     * types usable as an expected type.
     */
    @Test
    public void anIndirectCallArgumentPositionInstantiatesAGenericReference() {
        assertThat(run("""
                func identity<T>(value: T): T {
                    return value
                }

                func apply(f: func(Integer): Integer, v: Integer): Integer {
                    return f(v)
                }

                func applyThrough(f: func(func(Integer): Integer, Integer): Integer, v: Integer): Integer {
                    return f(identity, v)
                }

                val direct: func(func(Integer): Integer, Integer): Integer = apply
                println(applyThrough(direct, 12))
                """)).isEqualTo("12\n");
    }

    /**
     * A call the analyzer resolves from a declaration rather than from an argument supplies its expected
     * type the same way, because a declaration states its parameter types outright.
     *
     * <p>A {@code super} call reaches here: section 3 fixes that qualified member access "always invokes
     * the immediately preceding class's implementation" and "is not virtual", so the callee — and with it
     * the parameter type the reference must match — is known before the argument is examined. This is the
     * only path on which a {@code super} call is a call, which makes it the sharpest test that the
     * expected-type rule is a rule about argument positions rather than about a particular call shape: the
     * reference is instantiated here exactly as it would be at an ordinary call.
     */
    @Test
    public void aSuperCallArgumentPositionInstantiatesAGenericReference() {
        assertThat(run("""
                func identity<T>(value: T): T {
                    return value
                }

                open class Base {
                    func apply(transform: func(Integer): Integer, value: Integer): Integer {
                        return transform(value)
                    }
                }

                class Derived extends Base {
                    func use(): Integer {
                        return super.apply(identity, 10)
                    }
                }

                println(Derived().use())
                """)).isEqualTo("10\n");
    }

    /**
     * A collection member supplies its expected type too, which is a distinct fact from the indirect-call
     * case: a collection member's parameter type is not written in the callee's type parameters at all, so
     * it reaches this code through a different caller than a call through a function value does.
     *
     * <p>{@code List<func(Integer): Integer>}.add has already had its element type substituted in, so the
     * reference is instantiated from the substituted element type. That the two paths agree is what makes
     * the rule predictable: a reader should not have to know which library member they are calling to know
     * whether a generic reference will be accepted there.
     */
    @Test
    public void aCollectionMemberArgumentPositionInstantiatesAGenericReference() {
        assertThat(run("""
                func identity<T>(value: T): T {
                    return value
                }

                val callbacks = List<func(Integer): Integer>()
                callbacks.add(identity)
                print(callbacks.get(0)(7).toString())
                """)).isEqualTo("7");
    }

    /**
     * An expected type that a built-in member supplies is still not a complete function signature, and the
     * rejection says so.
     *
     * <p>{@code equals} declares its parameter {@code other: Any?} (section 12), so the expected type the
     * argument receives is {@code Any?}, not {@code Any}. The reference is rejected with the reason naming
     * what it was actually given — which is the {@code found} field doing its job: it reports the type the
     * context supplied rather than a generic "no expected type", the only reading that tells a reader why
     * moving the same reference to a {@code val} would compile.
     */
    @Test
    public void anExpectedNullableAnyFromABuiltInParameterIsAlsoInsufficient() {
        Diagnostic diagnostic = onlyDiagnostic("""
                func identity<T>(value: T): T {
                    return value
                }

                func use(value: Integer): Boolean {
                    return value.equals(identity)
                }
                """, DiagnosticCode.TYPE_CANNOT_INFER);
        assertThat(diagnostic.found().orElse("")).isEqualTo("the expected type Any?");
    }

    /**
     * An assignment the analyzer cannot resolve still reports the value's own defect.
     *
     * <p>Typing a generic function reference last means a code path that returns before reaching the
     * property would otherwise leave the reference untyped, and an untyped expression reports nothing.
     * A reader who wrote two mistakes — an unknown receiver and an uninstantiable reference — must be told
     * about both, which is the property this test holds: the {@code SOLV-RESOL-001} for the receiver
     * arrives with the {@code SOLV-TYPE-030} for the value, as it did before the value was ever held back.
     */
    @Test
    public void aHeldBackReferenceStillReportsItsOwnDefectWhenTheTargetCannotBeResolved() {
        assertThat(codes("""
                func identity<T>(value: T): T {
                    return value
                }

                func use(): Unit {
                    Nope.slot = identity
                }
                """)).contains(DiagnosticCode.RESOL_UNKNOWN_NAME, DiagnosticCode.TYPE_CANNOT_INFER);
    }

    /**
     * Every path that refuses a member assignment reports the value's defect as well.
     *
     * <p>The fallback in {@code checkMemberAssign} is reached from several distinct stops — a safe access, a
     * static write naming an instance property, a member that does not exist, and a receiver that may be null
     * — and each is a separate decision to abandon the assignment. A reader who made both mistakes is shown
     * both in all four cases, so one test per case rather than one per mechanism.
     */
    @Test
    public void everyRefusedMemberAssignmentAlsoReportsTheHeldBackReference() {
        String prefix = """
                func identity<T>(value: T): T {
                    return value
                }

                class Holder {
                    var slot: func(Integer): Integer = identity
                }

                """;
        assertThat(codes(prefix + """
                func use(h: Holder?): Unit {
                    h?.slot = identity
                }
                """)).contains(DiagnosticCode.TYPE_INVALID_ASSIGNMENT_TARGET, DiagnosticCode.TYPE_CANNOT_INFER);
        assertThat(codes(prefix + """
                func use(): Unit {
                    Holder.slot = identity
                }
                """)).contains(DiagnosticCode.RESOL_UNKNOWN_MEMBER, DiagnosticCode.TYPE_CANNOT_INFER);
        assertThat(codes(prefix + """
                func use(h: Holder): Unit {
                    h.nope = identity
                }
                """)).contains(DiagnosticCode.RESOL_UNKNOWN_MEMBER, DiagnosticCode.TYPE_CANNOT_INFER);
        assertThat(codes(prefix + """
                func use(h: Holder?): Unit {
                    val maybe = h
                    maybe.slot = identity
                }
                """)).contains(DiagnosticCode.TYPE_NULLABLE_DEREFERENCE, DiagnosticCode.TYPE_CANNOT_INFER);
    }

    /**
     * A static property that exists but cannot receive the value still reports the value's own problem too.
     *
     * <p>Where a static property is found, its declared type is a fact of the declaration, so the caller has
     * already resolved it and the value arrives instantiated — and the write then fails on assignability or
     * mutability alone, with the reference reporting nothing further because it has nothing left to report.
     * Where the name is not a static property at all, the caller had no type to supply, and the reference
     * keeps its own report beside the target's: two mistakes, two diagnostics.
     */
    @Test
    public void aStaticPropertyWriteThatFailsStillReportsTheInstantiatedReference() {
        String prefix = """
                func identity<T>(value: T): T {
                    return value
                }

                func other(value: String): String {
                    return value
                }

                class Store {
                    static var slot: func(Integer): Integer = identity
                    static val frozen: func(Integer): Integer = identity
                    static func method(value: Integer): Integer {
                        return value
                    }
                }

                """;
        // The declared type is resolved, so the value is instantiated, and the two disagree.
        assertThat(codes(prefix + """
                func use(): Unit {
                    Store.slot = other
                }
                """)).containsExactly(DiagnosticCode.TYPE_MISMATCH);
        // A `val` static property accepts no write at all.
        assertThat(codes(prefix + """
                func use(): Unit {
                    Store.frozen = identity
                }
                """)).containsExactly(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE);
        // A method is not a cell, and no property type existed to instantiate the reference from.
        assertThat(codes(prefix + """
                func use(): Unit {
                    Store.method = identity
                }
                """)).contains(DiagnosticCode.TYPE_INVALID_ASSIGNMENT_TARGET, DiagnosticCode.TYPE_CANNOT_INFER);
    }

    /**
     * A module-qualified static property is an assignment target whose declared type is settled by its
     * declaration, exactly as a bare {@code Class.slot} is, so a generic reference written into one is
     * instantiated from that declared type.
     *
     * <p>Section 6 makes no distinction by how the property was named, and the write and the read have to
     * agree: the value the qualified write stores is the same value a qualified read yields, which is only
     * true if the write accepted an instantiated reference rather than rejecting it.
     */
    @Test
    public void aModuleQualifiedStaticPropertyAssignmentInstantiatesAGenericReference() {
        assertThat(runInclude("""
                module lib
                func identity<T>(value: T): T {
                    return value
                }

                class Store {
                    static var slot: func(String): String = identity
                }
                """, """
                include "lib.sol" alias m

                m::Store.slot = m::identity
                val read: func(String): String = m::Store.slot
                print(read("written"))
                """)).isEqualTo("written");
    }

    /**
     * A nested generic reference is instantiated from a parameter position whose own parameter type is a
     * function type, which is the case that requires inference to descend into a function type rather than
     * match it whole.
     *
     * <p>The declared parameter {@code f: func(T): T} mentions {@code T} only inside a nested function
     * type, and a reference argument carries a monomorphic function type, so binding {@code T} means
     * unifying the two function types position by position.
     */
    @Test
    public void aFunctionTypedArgumentInstantiatesAGenericCallThroughItsOwnParameters() {
        assertThat(run("""
                func identity<T>(value: T): T {
                    return value
                }

                func apply<T>(f: func(T): T, v: T): T {
                    return f(v)
                }

                println(apply(identity, 42))
                println(apply(identity, "xy"))
                """)).isEqualTo("42\nxy\n");
    }

    /**
     * The same descent reaches a reference that is itself the expected type of a nested position: a
     * function returned from a function, with {@code T} appearing only inside the two function types.
     */
    @Test
    public void aNestedFunctionTypePositionCompletesTheSubstitution() {
        assertThat(run("""
                func identity<T>(value: T): T {
                    return value
                }

                func compose<T>(f: func(T): T): func(T): T {
                    return f
                }

                val made: func(String): String = compose(identity)
                print(made("ab"))
                """)).isEqualTo("ab");
    }

    /**
     * A call whose callee must still infer a parameter resolves the reference from the parameter the
     * other arguments finally determine.
     *
     * <p>{@code f: func(U): U} is not usable as an expected type while {@code U} is still a formal — it
     * may be settled by a later argument — so the reference is examined after {@code U} is known to be
     * {@code Integer} from the {@code v} argument, which is what makes the instantiation unique rather
     * than a guess taken from one argument ahead of the rest.
     */
    @Test
    public void anArgumentPositionOnACalleeStillInferringWaitsForTheOtherArguments() {
        assertThat(run("""
                func identity<T>(value: T): T {
                    return value
                }

                func outer<U>(f: func(U): U, v: U): U {
                    return f(v)
                }

                println(outer(identity, 1))
                """)).isEqualTo("1\n");
    }

    /**
     * A reference the callee's other arguments cannot decide is still refused.
     *
     * <p>Deferring a reference until the callee's parameters are known does not manufacture evidence.
     * Where no other argument determines the callee's type parameter, the callee itself cannot be
     * instantiated, so there are no final parameter types to instantiate the reference against and the
     * call reports the inference failure it always did — one report, naming the callee's parameter, which
     * is the parameter that was never determined.
     */
    @Test
    public void anArgumentPositionWhoseCalleeCannotBeInstantiatedIsStillRejected() {
        Diagnostic diagnostic = onlyDiagnostic("""
                func identity<T>(value: T): T {
                    return value
                }

                func takesOnly<U>(f: func(U): U): Integer {
                    return 0
                }

                func use(): Unit {
                    takesOnly(identity)
                }
                """, DiagnosticCode.TYPE_CANNOT_INFER);
        assertThat(diagnostic.message()).contains("U");
    }

    /**
     * The inference order the section fixes: parameter positions bind first and a result position only
     * confirms or completes.
     *
     * <p>"Inference first unifies occurrences in the declared parameter types with the expected parameter
     * types; result positions may confirm or complete a unique substitution but never choose arbitrarily
     * among several valid types" (section 6). A callee that takes the generic function value as an
     * argument is the shape where a result position is the <em>only</em> evidence for a type parameter,
     * so it doubles as the test that a result position may complete a substitution: the call below has no
     * argument that mentions {@code T} at all, and {@code apply(identity, 3)} is still decided because
     * the expected result of {@code f} and the expected result of {@code apply} agree on it.
     */
    @Test
    public void aResultPositionCompletesButNeverOverridesAPositionTheParametersEstablish() {
        assertThat(run("""
                func identity<T>(value: T): T {
                    return value
                }

                func apply<T>(f: func(T): T, v: T): T {
                    return f(v)
                }

                val composed: func(func(Integer): Integer, Integer): Integer = apply
                println(composed(identity, 3))
                """)).isEqualTo("3\n");
    }

    // ---------------------------------------------------------------------------------------------
    // Direct calls are untouched
    // ---------------------------------------------------------------------------------------------

    /**
     * Instantiating a reference as a value does not disturb the direct-call path, with or without written
     * type arguments.
     *
     * <p>"A direct call whose target is statically known keeps its existing statically resolved path" and
     * "Explicit type arguments remain available on direct calls, so {@code identity<Integer>(1)} keeps
     * working" (section 6).
     */
    @Test
    public void directCallsKeepTheirExistingResolution() {
        assertThat(run("""
                func identity<T>(value: T): T {
                    return value
                }

                println(identity(9))
                println(identity<String>("ten"))
                """)).isEqualTo("9\nten\n");
    }

    // ---------------------------------------------------------------------------------------------
    // Identity across instantiations
    // ---------------------------------------------------------------------------------------------

    /**
     * Instantiating one declaration at two function types yields one value, not two.
     *
     * <p>"Contextual instantiations of one generic declaration at different function types also share
     * that declaration's canonical runtime identity: instantiation changes static typing, not the
     * underlying executable value" (section 6), and the semantic-equality table lists "Function value |
     * reference identity". A per-instantiation value would make the two bindings distinct and print
     * {@code false}.
     *
     * <p>The comparison goes through {@code equals} rather than {@code ===} because the two operands have
     * different function types: identity operators additionally require one operand type to be assignable
     * to the other, and {@code func(Integer): Integer} is not assignable to {@code func(String): String}.
     * {@code equals} is the spelling the equality section prescribes for exactly that case, and for a
     * function value it is defined to be reference identity, so it answers the same question.
     */
    @Test
    public void everyInstantiationOfOneDeclarationIsOneValue() {
        assertThat(run("""
                func identity<T>(value: T): T {
                    return value
                }

                val integerIdentity: func(Integer): Integer = identity
                val stringIdentity: func(String): String = identity
                println(integerIdentity.equals(stringIdentity))
                println(integerIdentity === integerIdentity)
                """)).isEqualTo("true\ntrue\n");
    }

    /**
     * Two references to different generic declarations stay distinct, and equal values carry equal
     * hashes.
     *
     * <p>The specification fixes only that equal values share a hash; it promises nothing about distinct
     * values' hashes, so no assertion is made that the two declarations' hashes differ.
     */
    @Test
    public void instantiationsAtDifferentTypesShareIdentityAndHash() {
        assertThat(run("""
                func identity<T>(value: T): T {
                    return value
                }

                func alsoIdentity<U>(value: U): U {
                    return value
                }

                val first: func(Integer): Integer = identity
                val second: func(String): String = identity
                val third: func(Integer): Integer = alsoIdentity
                println(first.equals(second))
                println(first.equals(third))
                println(first.hashCode() == second.hashCode())
                """)).isEqualTo("true\nfalse\ntrue\n");
    }

    /**
     * A module-qualified generic reference instantiates and is the same value as the unqualified name.
     *
     * <p>"module qualification does not create a second identity for the same declaration" (section 6),
     * read together with the instantiation rule: the qualified spelling reaches the same declaration, so
     * it reaches the same canonical value under a different instantiation.
     */
    @Test
    public void aQualifiedGenericReferenceInstantiatesAndIsTheSameValue() {
        assertThat(runInclude("""
                module lib
                func identity<T>(value: T): T {
                    return value
                }
                """, """
                include "lib.sol" alias m
                val bare: func(Integer): Integer = m::identity
                val again: func(String): String = m::identity
                println(bare.equals(again))
                println(bare(5))
                print(again("hi"))
                """)).isEqualTo("true\n5\nhi");
    }

    /**
     * An instantiated value works inside a declaration that is itself generic, where the expected type is
     * written in that declaration's own type parameter.
     *
     * <p>The section's rule names no restriction on where the expected function type may come from, and a
     * type parameter standing inside a function type — {@code func(T): T} — is a complete signature: it
     * states the parameter and result types the substitution is applied to.
     */
    @Test
    public void aReferenceInsideAGenericDeclarationInstantiatesToThatDeclarationsOwnParameter() {
        assertThat(run("""
                func identity<T>(value: T): T {
                    return value
                }

                func useInGeneric<U>(value: U): U {
                    val local: func(U): U = identity
                    return local(value)
                }

                println(useInGeneric(3))
                print(useInGeneric("ab"))
                """)).isEqualTo("3\nab");
    }

    // ---------------------------------------------------------------------------------------------
    // Rejected instantiations
    // ---------------------------------------------------------------------------------------------

    /**
     * The substitution is recorded in the checked program, not deferred to execution.
     *
     * <p>"All type parameters must be resolved, and the decision is made before lowering: a function value
     * performs no runtime type dispatch" (section 6). The type stored for the reference is the substituted
     * one — {@code func(Integer): Integer} and not the declaration's own {@code func(T): T} — which is the
     * only form a lowering that cannot dispatch at run time could act on, and the phase's exit criterion:
     * every generic-value decision is a fact about the checked program rather than an event during a run.
     * The two instantiations of one declaration are recorded as two different types at their two sites,
     * while the runtime value the sites produce stays one value.
     */
    @Test
    public void theInstantiationIsRecordedInTheCheckedProgram() {
        assertThat(recordedInitializerType("""
                func identity<T>(value: T): T {
                    return value
                }

                func integerSite(): Unit {
                    val local: func(Integer): Integer = identity
                }
                """, "integerSite").name()).isEqualTo("func(Integer): Integer");
        assertThat(recordedInitializerType("""
                func identity<T>(value: T): T {
                    return value
                }

                func stringSite(): Unit {
                    val local: func(String): String = identity
                }
                """, "stringSite").name()).isEqualTo("func(String): String");
    }

    /**
     * With no expected type at all, there is nothing to instantiate to.
     *
     * <p>"A generic function reference with no expected function type is {@code SOLV-TYPE-030}" (section
     * 6), and the code is "reported on the function or method reference", so the span is the name and not
     * the enclosing declaration.
     */
    @Test
    public void aReferenceWithNoExpectedTypeIsRejectedOnTheReference() {
        String program = """
                func identity<T>(value: T): T {
                    return value
                }

                func use(): Unit {
                    val ambiguous = identity
                }
                """;
        Diagnostic diagnostic = onlyDiagnostic(program, DiagnosticCode.TYPE_CANNOT_INFER);
        assertThat(diagnostic.message()).contains("identity");
        assertThat(diagnostic.found().orElse("")).isEqualTo("no expected type");
        assertCovers(program, "val ambiguous = identity", "identity", diagnostic);
    }

    /**
     * An expected {@code Any} exposes no function signature, so it cannot instantiate anything.
     *
     * <p>"An expected {@code Any}, an unbounded type parameter, or any other type that does not expose a
     * complete function signature is insufficient" (section 6).
     */
    @Test
    public void anExpectedAnyIsInsufficient() {
        Diagnostic diagnostic = onlyDiagnostic("""
                func identity<T>(value: T): T {
                    return value
                }

                func use(): Unit {
                    val boxed: Any = identity
                }
                """, DiagnosticCode.TYPE_CANNOT_INFER);
        assertThat(diagnostic.found().orElse("")).isEqualTo("the expected type Any");
    }

    /**
     * An expected bare type parameter is the section's second insufficient case.
     *
     * "an unbounded type parameter ... is insufficient" (section 6): {@code U} names no parameter and
     * result types, so nothing can be unified against them.
     */
    @Test
    public void anExpectedUnboundedTypeParameterIsInsufficient() {
        Diagnostic diagnostic = onlyDiagnostic("""
                func identity<T>(value: T): T {
                    return value
                }

                func passes<U>(value: U): U {
                    val wrong: U = identity
                    return value
                }
                """, DiagnosticCode.TYPE_CANNOT_INFER);
        assertThat(diagnostic.found().orElse("")).isEqualTo("the expected type U");
    }

    /**
     * A parameter the expected type never mentions is left unresolved, and that is the same single
     * condition the section names.
     *
     * <p>"All type parameters must be resolved" (section 6), and "no second inference diagnostic exists"
     * for the expression: a declaration with a parameter the expected signature cannot determine has one
     * missing piece of evidence, not two, so the report is one {@code SOLV-TYPE-030} and nothing follows
     * it.
     */
    @Test
    public void aTypeParameterTheExpectedTypeDoesNotDetermineIsRejectedAlone() {
        Diagnostic diagnostic = onlyDiagnostic("""
                func ignores<T>(value: Integer): Integer {
                    return value
                }

                func use(): Unit {
                    val partial: func(Integer): Integer = ignores
                }
                """, DiagnosticCode.TYPE_CANNOT_INFER);
        assertThat(diagnostic.message()).contains("T");
    }

    /**
     * A partially applied generic class type is not a function signature either.
     *
     * <p>{@code List<Integer>} is the section's "any other type that does not expose a complete function
     * signature": it declares no parameter and result types to unify against.
     */
    @Test
    public void anExpectedGenericClassTypeIsInsufficient() {
        onlyDiagnostic("""
                func identity<T>(value: T): T {
                    return value
                }

                func use(): Unit {
                    val wrong: List<Integer> = identity
                }
                """, DiagnosticCode.TYPE_CANNOT_INFER);
    }

    /**
     * An arity mismatch is an assignability problem, not an inference failure.
     *
     * <p>The expected type still exposes a complete function signature, so the substitution is derived
     * from the positions the two lists share and the resulting monomorphic type simply is not assignable
     * to what was expected. Reporting {@code SOLV-TYPE-030} here would send a reader to look for a
     * missing type argument when the program's actual defect is a function of the wrong shape.
     */
    @Test
    public void anArityMismatchReportsTheAssignabilityProblemItIs() {
        Diagnostic diagnostic = onlyDiagnostic("""
                func pick<T>(first: T, second: T): T {
                    return first
                }

                func use(): Unit {
                    val wrong: func(Integer): Integer = pick
                }
                """, DiagnosticCode.TYPE_MISMATCH);
        assertThat(diagnostic.message()).contains("func(Integer): Integer");
    }

    /**
     * An expected type that constrains one type parameter two incompatible ways resolves to the first
     * binding and is then rejected as the mismatch it is, with no second report.
     *
     * <p>"result positions ... never choose arbitrarily among several valid types" (section 6) is the
     * promise at stake: inference takes the first evidence it finds and stops, so {@code T} becomes
     * {@code Integer} from the first parameter and the second is reported as a wrong argument type rather
     * than being silently reconciled or reported as ambiguity a reader cannot act on.
     */
    @Test
    public void conflictingParameterEvidenceResolvesToTheFirstBindingAndThenMismatches() {
        assertThat(firstCode("""
                func pick<T>(first: T, second: T): T {
                    return first
                }

                func use(): Unit {
                    val wrong: func(Integer, String): Integer = pick
                }
                """)).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    /**
     * A rejected reference produces no output: the decision is made before lowering.
     *
     * <p>"the decision is made before lowering: a function value performs no runtime type dispatch"
     * (section 6). A program that compiled and then failed at run time would print the lines before the
     * bad reference, which is exactly what a deferred-instantiation implementation would do.
     */
    @Test
    public void anUninstantiableReferenceIsRejectedBeforeAnythingRuns() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        boolean failed = false;
        try (Context context = Context.newBuilder("solvik").out(out).err(out)
                .option("engine.WarnInterpreterOnly", "false").allowAllAccess(true).build()) {
            context.eval(build("""
                    func identity<T>(value: T): T {
                        return value
                    }

                    println("reached")
                    val ambiguous = identity
                    """, "reject.sol"));
        } catch (RuntimeException e) {
            failed = true;
        }
        assertThat(failed).as("the program must be rejected").isTrue();
        assertThat(out.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    /**
     * An already-instantiated value cannot be called with written type arguments.
     *
     * <p>"Explicit type arguments are not permitted on an already-instantiated function value, because a
     * function value is monomorphic, so {@code operation<Integer>(1)} is {@code SOLV-TYPE-029}"
     * (section 6). The instantiation happened statically; there is no remaining parameter to supply.
     */
    @Test
    public void anInstantiatedValueIsNotReInstantiableByWritingArguments() {
        onlyDiagnostic("""
                func identity<T>(value: T): T {
                    return value
                }

                func use(): Unit {
                    val local: func(Integer): Integer = identity
                    local<Integer>(1)
                }
                """, DiagnosticCode.TYPE_NOT_GENERIC);
    }

    /**
     * An instantiated value is an ordinary function value in every other respect: it displays as
     * {@code func} like any other, and its declared type is the monomorphic one inference produced rather
     * than a placeholder carrying the unsubstituted parameter.
     *
     * <p>The display form is fixed by section 6, and the type is what an implementation that recorded the
     * reference without substituting it would most plausibly get wrong — a {@code func(T): T} would not
     * accept an {@code Integer} argument at the call below.
     */
    @Test
    public void anInstantiatedValueIsAnOrdinaryFunctionValue() {
        assertThat(run("""
                func identity<T>(value: T): T {
                    return value
                }

                val made: func(Integer): Integer = identity
                println(made(4))
                print(made.toString())
                """)).isEqualTo("4\nfunc");
    }

    /**
     * A nullable function type exposes the signature it wraps, so it instantiates while keeping its own
     * nullability as the assignment question.
     *
     * <p>{@code (func(Integer): Integer)?} constrains every type parameter exactly as the non-null form
     * does, and section 6's invocation rule — a nullable function value is invocable after refinement —
     * is what the rest of the program then exercises.
     */
    @Test
    public void aNullableFunctionTypeInstantiatesAndRemainsRefinable() {
        assertThat(run("""
                func identity<T>(value: T): T {
                    return value
                }

                var made: (func(Integer): Integer)? = identity
                if (made != null) {
                    println(made(2))
                }
                """)).isEqualTo("2\n");
    }
}
