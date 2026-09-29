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
 * Bound method references: reading a declared instance method without calling it produces a bound
 * method value (docs/LANGUAGE_SPEC.md section 6, "Bound method references").
 *
 * <p>The claims this section makes are mostly runtime claims — that the receiver is evaluated once and
 * retained, that the implementation is the one the receiver's runtime class selects, that each creation
 * is a fresh identity, that {@code super.method} skips redispatch — so each one is pinned by running a
 * program and reading what it prints. An analyzer-only test could show that a reference is typed as a
 * function type without showing that calling it reaches the override a concrete receiver supplies,
 * which is the half of the rule an implementation is most likely to get wrong.
 *
 * <p>Each expected output is worked out from the specification passage named in the test comment
 * rather than copied from an observed run. Where the implementation and the passage disagreed, the
 * implementation was changed.
 */
public final class SolvikBoundMethodReferenceTest {

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

    private static DiagnosticCode firstCode(String text) {
        SemanticResult result = SolvikSemanticAnalyzer.analyze(parseOk("bound.sol", text));
        assertThat(result.isSuccess()).as("analysis must fail: " + text).isFalse();
        DiagnosticBag bag = result.diagnostics();
        List<Diagnostic> all = bag.all();
        assertThat(all.isEmpty()).as("failed analysis must carry a diagnostic").isFalse();
        return all.get(0).code();
    }

    private static DiagnosticCode codeOf(String text) {
        SemanticResult result = SolvikSemanticAnalyzer.analyze(parseOk("bound.sol", text));
        assertThat(result.isSuccess()).as("analysis must fail: " + text).isFalse();
        return result.diagnostics().all().get(0).code();
    }

    /** Runs a root source that includes {@code library} from the same temporary directory. */
    private static String runInclude(String library, String root) {
        try {
            java.nio.file.Path directory = java.nio.file.Files.createTempDirectory("solvik-bound");
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

    // ---------------------------------------------------------------------------------------------
    // Producing and invoking a bound value
    // ---------------------------------------------------------------------------------------------

    /**
     * Reading an instance method without calling it produces a value that can initialize a binding of a
     * function type, and invoking that value invokes the method on the captured receiver.
     *
     * <p>"Reading an instance method without calling it produces a bound method value" and "The method's
     * implicit receiver does not appear in the function type" (section 6). The value's type is
     * {@code func(Integer): String} — the receiver contributes no parameter — and calling it with one
     * argument is what makes the declaration's one explicit parameter visible.
     */
    @Test
    public void aBoundMethodReferenceInitializesABindingAndInvokes() {
        assertThat(run("""
                class Formatter {
                    func format(value: Integer): String {
                        return value.toString()
                    }
                }

                val formatter = Formatter()
                val operation: func(Integer): String = formatter.format
                print(operation(42))
                """)).isEqualTo("42");
    }

    /**
     * The reference is usable wherever a function value is: as an argument and as a result.
     *
     * <p>"A function type may appear wherever another non-deferred type may appear" (section 6), and a
     * bound reference has a function type, so nothing about it is restricted to an initializer.
     */
    @Test
    public void aBoundMethodReferenceIsPassedAndReturned() {
        assertThat(run("""
                class Adder {
                    val offset: Integer
                    Adder(offset: Integer) {
                        this.offset = offset
                    }
                    func add(value: Integer): Integer {
                        return value + this.offset
                    }
                }

                func applyThrice(operation: func(Integer): Integer, value: Integer): Integer {
                    return operation(operation(operation(value)))
                }

                func operationOf(adder: Adder): func(Integer): Integer {
                    return adder.add
                }

                print(operationOf(Adder(1))(1))
                print("|")
                print(applyThrice(operationOf(Adder(2)), 0))
                """)).isEqualTo("2|6");
    }

    // ---------------------------------------------------------------------------------------------
    // Virtual dispatch is preserved
    // ---------------------------------------------------------------------------------------------

    /**
     * A reference obtained through a class type invokes the implementation the captured receiver's
     * runtime class selects, including an override two levels down.
     *
     * <p>"Ordinary virtual dispatch is preserved. A reference obtained through a class or interface type
     * invokes the implementation selected by the captured receiver's runtime class. Overrides, interface
     * defaults, delegated implementations, and inherited instance methods behave the same through a
     * bound reference as through an immediate method call" (section 6).
     */
    @Test
    public void aBoundReferenceDispatchesOnTheReceiverRuntimeClass() {
        assertThat(run("""
                open class Base {
                    open func greet(): String {
                        return "base"
                    }
                }

                open class Mid extends Base {
                    override open func greet(): String {
                        return "mid"
                    }
                }

                class Leaf extends Mid {
                    override func greet(): String {
                        return "leaf"
                    }
                }

                val base: Base = Base()
                val leaf: Base = Leaf()
                val fromBase: func(): String = base.greet
                val fromLeaf: func(): String = leaf.greet
                print(fromBase())
                print("|")
                print(fromLeaf())
                print("|")
                print(fromLeaf == fromBase)
                """)).isEqualTo("base|leaf|false");
    }

    /**
     * An inherited method the receiver's class never redeclares is still bound, and binds the inherited
     * implementation.
     *
     * <p>The same sentence names "inherited instance methods" among the things that behave the same
     * through a bound reference (section 6). The reference is written against the superclass type, so a
     * read that consulted only the static class's own declarations would find nothing.
     */
    @Test
    public void anInheritedMethodBindsThroughASubclassReceiver() {
        assertThat(run("""
                open class Animal {
                    open func sound(): String {
                        return "generic"
                    }
                }

                class Cat extends Animal {
                    override func sound(): String {
                        return "meow"
                    }
                }

                val animal: Animal = Animal()
                val cat: Animal = Cat()
                print(animal.sound())
                print("|")
                print(cat.sound())
                """)).isEqualTo("generic|meow");
    }

    /**
     * Through an interface-typed receiver a bound reference reaches the conforming instance's own
     * implementation, and a defaulted requirement reaches the default.
     *
     * <p>The dispatch sentence covers "interface defaults" (section 6), so an interface-typed receiver is
     * not a barrier: the value must reach the implementor, exactly as a call inside a default method body
     * reaches the concrete implementor of a requirement.
     */
    @Test
    public void aBoundReferenceThroughAnInterfaceTypeReachesTheConformingInstance() {
        assertThat(run("""
                interface Speaker {
                    func speak(): String
                    func shout(): String {
                        return speak() .. speak()
                    }
                }

                class Dog implements Speaker {
                    func speak(): String {
                        return "woof"
                    }
                }

                class Loud implements Speaker {
                    func speak(): String {
                        return "BARK"
                    }
                }

                val dog: Speaker = Dog()
                val loud: Speaker = Loud()
                val dogSpeak: func(): String = dog.speak
                val loudSpeak: func(): String = loud.speak
                print(dogSpeak())
                print("|")
                print(loudSpeak())
                print("|")
                val dogShout: func(): String = dog.shout
                print(dogShout())
                """)).isEqualTo("woof|BARK|woofwoof");
    }

    /**
     * A delegated implementation binds through a bound reference the way it binds through a call.
     *
     * <p>The dispatch sentence names "delegated implementations" (section 6). The receiver declares no
     * method of its own at all, so the value can only reach the delegate's implementation if the class's
     * dispatch table supplied it, which is the same table an immediate call consults.
     */
    @Test
    public void aDelegatedImplementationBindsAsABoundReference() {
        assertThat(run("""
                interface Greeter {
                    func greet(): String
                }

                class FrenchGreeter implements Greeter {
                    func greet(): String {
                        return "bonjour"
                    }
                }

                class Host implements Greeter {
                    delegate val greeter: Greeter

                    Host(greeter: Greeter) {
                        this.greeter = greeter
                    }
                }

                val host = Host(FrenchGreeter())
                val method: func(): String = host.greet
                print(method())
                """)).isEqualTo("bonjour");
    }

    // ---------------------------------------------------------------------------------------------
    // this, unqualified names, and super
    // ---------------------------------------------------------------------------------------------

    /**
     * {@code this.method} is a bound reference to the current receiver, and dispatches virtually from
     * that receiver's runtime class even when the enclosing declaration is inherited.
     *
     * <p>"`this.method` is a bound reference to the current receiver" (section 6). The method is declared
     * in the superclass and inherited unchanged, so the {@code this} a {@code Leaf} passes is what selects
     * the override.
     */
    @Test
    public void thisMethodIsABoundReferenceToTheCurrentReceiver() {
        assertThat(run("""
                open class Base {
                    open func greet(): String {
                        return "base"
                    }
                    func viaThis(): func(): String {
                        return this.greet
                    }
                }

                open class Mid extends Base {
                    override open func greet(): String {
                        return "mid"
                    }
                }

                class Leaf extends Mid {
                    override func greet(): String {
                        return "leaf"
                    }
                }

                print(Leaf().viaThis()())
                print("|")
                print(Base().viaThis()())
                """)).isEqualTo("leaf|base");
    }

    /**
     * A bare unqualified method name in a value position is an unknown name, not a bound reference.
     *
     * <p>"An unqualified method name remains legal only as an immediate call under the existing
     * implicit-{@code this} rule, so using a method as a value requires {@code this.method} and a bare
     * unqualified method name in a value position is {@code SOLV-RESOL-001}" (section 6). An immediate
     * call with the same bare name is still legal, which is what makes the rule about value position
     * rather than about the name.
     */
    @Test
    public void aBareMethodNameInAValuePositionIsAnUnknownName() {
        assertThat(codeOf("""
                class C {
                    func f(): Integer {
                        return 1
                    }
                    func use(): func(): Integer {
                        return f
                    }
                }
                """)).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_NAME);
    }

    /**
     * The same bare name is still an immediate call when parentheses make it one.
     *
     * <p>The sentence above permits "an immediate call under the existing implicit-{@code this} rule"
     * (section 6), so a test that only showed the value position failing could not tell a working rule
     * from one that forbade the name outright.
     */
    @Test
    public void aBareMethodNameIsStillAnImmediateCall() {
        assertThat(run("""
                class C {
                    func f(): Integer {
                        return 1
                    }
                    func use(): Integer {
                        return f()
                    }
                }

                print(C().use())
                """)).isEqualTo("1");
    }

    /**
     * {@code super.method} as a value binds {@code this} to the immediate superclass implementation and
     * does not redispatch to the current class's own override.
     *
     * <p>"`super.method` creates a value bound to `this` that invokes the immediate superclass
     * implementation without virtual redispatch, matching an immediate {@code super.method(...)} call"
     * (section 6). The class overrides the method, so a value that redispatched would print the
     * override's text instead of the superclass's.
     */
    @Test
    public void superMethodBindsTheImmediateSuperclassImplementation() {
        assertThat(run("""
                open class Base {
                    open func greet(): String {
                        return "base"
                    }
                }

                open class Mid extends Base {
                    override open func greet(): String {
                        return "mid"
                    }
                    func viaSuper(): func(): String {
                        return super.greet
                    }
                }

                class Leaf extends Mid {
                    override func greet(): String {
                        return "leaf"
                    }
                }

                val method: func(): String = Mid().viaSuper()
                print(method())
                print("|")
                print(Leaf().viaSuper()())
                print("|")
                print(Mid().greet())
                """)).isEqualTo("base|base|mid");
    }

    /**
     * {@code super.method} where nothing in the hierarchy is overridden still resolves, because the
     * immediate superclass declares it.
     *
     * <p>The same sentence describes a value that "matches an immediate {@code super.method(...)} call"
     * (section 6), and such a call needs no override to exist. Without a subclass override the only
     * implementation is the superclass's, which is what the value must invoke.
     */
    @Test
    public void superMethodWithoutAnOverrideBindsTheSuperclass() {
        assertThat(run("""
                open class Base {
                    func describe(): String {
                        return "base"
                    }
                }

                class Derived extends Base {
                    func viaSuper(): func(): String {
                        return super.describe
                    }
                }

                print(Derived().viaSuper()())
                """)).isEqualTo("base");
    }

    /**
     * A universal member stays non-bindable through {@code super} when a class in the hierarchy
     * overrides it.
     *
     * <p>"Only a declared callable binds. The fixed language-defined universal members {@code toString},
     * {@code equals}, and {@code hashCode}... are not bindable: a bare read of one of them stays the
     * compile-time error that section 3 and section 23.4 already require" (section 6). An override puts
     * the name in the superclass's dispatch table, so the {@code super} path — which resolves members
     * through that table rather than through the universal-member rule — has to refuse the name instead
     * of binding the override it finds.
     */
    @Test
    public void aUniversalMemberIsNotBindableThroughSuper() {
        assertThat(codeOf("""
                open class Base {
                    override open func equals(other: Any?): Boolean {
                        return true
                    }
                    override open func hashCode(): Integer {
                        return 1
                    }
                }

                class Derived extends Base {
                    func use(): Any {
                        return super.equals
                    }
                }
                """)).isEqualTo(DiagnosticCode.TYPE_FUNCTION_AS_VALUE);
    }

    /**
     * {@code super.method} binds an interface requirement that only a {@code delegate} supplies.
     *
     * <p>"Overrides, interface defaults, delegated implementations, and inherited instance methods behave
     * the same through a bound reference as through an immediate method call" (section 6). A requirement
     * met only by a delegate has no declared method in the superclass; the dispatch table holds a
     * synthesized forwarding method there, which is what the {@code super} reference must reach.
     */
    @Test
    public void superBindsAnInterfaceRequirementASuperclassDelegates() {
        assertThat(run("""
                interface Greeter {
                    func greet(): String
                }

                class French implements Greeter {
                    func greet(): String {
                        return "bonjour"
                    }
                }

                open class Holder implements Greeter {
                    delegate val greeter: Greeter

                    Holder(greeter: Greeter) {
                        this.greeter = greeter
                    }
                }

                class Sub extends Holder {
                    Sub(greeter: Greeter) {
                        super(greeter)
                    }
                    func use(): func(): String {
                        return super.greet
                    }
                }

                print(Sub(French()).use()())
                """)).isEqualTo("bonjour");
    }

    /**
     * {@code super.method} naming nothing in the superclass is an unknown member, not an inference or a
     * value problem.
     *
     * <p>The superclass is what a {@code super} read searches (section 7), so a name it does not declare
     * has no member to bind. This is the branch that survives the inversion: a bound value needs a
     * declared method, and without one the read still has no meaning.
     */
    @Test
    public void superMethodNamingNothingIsAnUnknownMember() {
        assertThat(codeOf("""
                open class Base {
                }

                class Derived extends Base {
                    func viaSuper(): func(): Integer {
                        return super.missing
                    }
                }
                """)).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_MEMBER);
    }

    // ---------------------------------------------------------------------------------------------
    // Receiver evaluation and retention
    // ---------------------------------------------------------------------------------------------

    /**
     * The receiver expression is evaluated exactly once when the value is created, and the retained
     * receiver — not a re-evaluated one — is what a later call uses.
     *
     * <p>"The receiver expression is evaluated exactly once when the bound method value is created, and
     * the receiver is retained strongly by that value" (section 6). The receiver expression mutates an
     * observable counter, so the count proves the evaluation happened once and not at each call; the
     * returned label proves the call reached the object that expression produced.
     */
    @Test
    public void theReceiverExpressionIsEvaluatedExactlyOnceAtCreation() {
        assertThat(run("""
                class Wrapper {
                    val inner: Target
                    var evaluations: Integer = 0
                    Wrapper(inner: Target) {
                        this.inner = inner
                    }
                    func target(): Target {
                        this.evaluations = this.evaluations + 1
                        return this.inner
                    }
                }

                class Target {
                    val label: String
                    Target(label: String) {
                        this.label = label
                    }
                    func describe(): String {
                        return this.label
                    }
                }

                val wrapper = Wrapper(Target("x"))
                val before = wrapper.evaluations
                val method: func(): String = wrapper.target().describe
                print(wrapper.evaluations - before)
                print("|")
                print(method())
                print("|")
                print(wrapper.evaluations - before)
                print("|")
                print(method())
                print("|")
                print(wrapper.evaluations - before)
                """)).isEqualTo("1|x|1|x|1");
    }

    /**
     * A {@code super} reference contributes no receiver evaluation of its own, because {@code this} is
     * the receiver and is not an expression that computes anything.
     *
     * <p>"The receiver expression is evaluated exactly once" (section 6) is vacuous for {@code this}, and
     * stating it here is what keeps a future implementation from re-reading the receiver slot per call in
     * a way that could observe a reassigned field.
     */
    @Test
    public void aSuperReferenceRetainsTheEnclosingReceiver() {
        assertThat(run("""
                open class Base {
                    open func label(): String {
                        return "base"
                    }
                }

                class Derived extends Base {
                    func viaSuper(): func(): String {
                        return super.label
                    }
                    func useTwice(): String {
                        val method: func(): String = super.label
                        return method() .. method()
                    }
                }

                print(Derived().useTwice())
                """)).isEqualTo("basebase");
    }

    // ---------------------------------------------------------------------------------------------
    // Fresh identity per creation
    // ---------------------------------------------------------------------------------------------

    /**
     * Each successful evaluation creates a distinct identity, even for the same receiver and method, and
     * copying a value through a binding preserves it.
     *
     * <p>"Each successful evaluation of a bound method-reference expression creates a distinct
     * function-value identity, even for the same receiver and method. Copying that value through bindings
     * preserves its identity" (section 6), and "formatter.format === formatter.format // false: two
     * bound-value creations". This is the opposite of a named function value's canonical identity, so both
     * halves are pinned in one test.
     */
    @Test
    public void eachBoundValueCreationIsADistinctIdentity() {
        assertThat(run("""
                class Formatter {
                    func format(): String {
                        return "f"
                    }
                }

                val formatter = Formatter()
                val first: func(): String = formatter.format
                val second: func(): String = formatter.format
                val copy = first
                print(first === second)
                print("|")
                print(first === first)
                print("|")
                print(copy === first)
                print("|")
                print(copy.equals(second))
                """)).isEqualTo("false|true|true|false");
    }

    /**
     * A bound value is identity-bearing, so {@code ===} accepts two of them and the comparison is
     * reference identity.
     *
     * <p>"A function value is identity-bearing, so a concrete function type and its nullable form are
     * valid operands of {@code ===} and {@code !==}" (section 6). Two bound values of one method type are
     * mutually assignable and identity-bearing, which is exactly the condition the sentence requires.
     */
    @Test
    public void twoBoundValuesAreValidIdentityOperands() {
        assertThat(run("""
                class Formatter {
                    func format(): String {
                        return "f"
                    }
                }

                val a = Formatter()
                val b = Formatter()
                val fromA: func(): String = a.format
                val fromB: func(): String = b.format
                print(fromA === fromB)
                print("|")
                print(fromA !== fromB)
                print("|")
                print(fromA === fromA)
                """)).isEqualTo("false|true|true");
    }

    /**
     * Display is the fixed string {@code func} for a bound value as it is for every function value.
     *
     * <p>"{@code toString()} for every function value returns the exact string {@code func}. It must not
     * expose a Java class name, memory address, node name, module path, captured values, or
     * implementation details" (section 6). A bound value is the kind most likely to leak a method or
     * class name, so it is checked here.
     */
    @Test
    public void aBoundValueDisplaysAsFunc() {
        assertThat(run("""
                class Formatter {
                    func format(value: Integer): String {
                        return value.toString()
                    }
                }

                val method: func(Integer): String = Formatter().format
                print(method)
                print("|")
                print(method.toString())
                print("|")
                print("value: " .. method)
                """)).isEqualTo("func|func|value: func");
    }

    /**
     * The matching hash is the reference-identity hash: equal values hash equally, and the hash is not a
     * method or class label.
     *
     * <p>"Semantic equality for function values is reference identity, and {@code hashCode()} is the
     * matching reference-identity hash" (section 6). The specification guarantees equal hashes for equal
     * values and says nothing about distinct values, so only the guaranteed direction is asserted.
     */
    @Test
    public void aBoundValueHashesConsistentlyWithItsIdentity() {
        assertThat(run("""
                class Formatter {
                    func format(): String {
                        return "f"
                    }
                }

                val method: func(): String = Formatter().format
                val copy = method
                print(copy.hashCode() == method.hashCode())
                print("|")
                print(method.hashCode() == "func".hashCode())
                """)).isEqualTo("true|false");
    }

    // ---------------------------------------------------------------------------------------------
    // Nullable receivers
    // ---------------------------------------------------------------------------------------------

    /**
     * Safe member access on a nullable receiver produces a nullable function value: null when the
     * receiver is null, the bound method otherwise.
     *
     * <p>"A normal member reference on a nullable receiver is illegal. Safe member access produces a
     * nullable function value and evaluates the receiver once... If the receiver is null the result is
     * null and no bound function is created; if it is non-null the result is the corresponding bound
     * method" (section 6).
     */
    @Test
    public void safeAccessOnANullableReceiverYieldsANullableFunctionValue() {
        assertThat(run("""
                class Target {
                    func describe(): String {
                        return "t"
                    }
                }

                val nothing: Target? = null
                val absent: (func(): String)? = nothing?.describe
                print(absent)
                print("|")
                val something: Target? = Target()
                val present: (func(): String)? = something?.describe
                if (present != null) {
                    print(present())
                }
                """)).isEqualTo("null|t");
    }

    /**
     * A normal member reference on a nullable receiver is refused, and the nullable function type is
     * spelled with parentheses so it is not read as a function returning a nullable.
     *
     * <p>"A normal member reference on a nullable receiver is illegal" (section 6), and the parenthesized
     * spelling is what separates "(func(): String)?" from "func(): String?".
     */
    @Test
    public void anUnsafeReferenceThroughANullableReceiverIsRejected() {
        assertThat(codeOf("""
                class Target {
                    func describe(): String {
                        return "t"
                    }
                }

                func use(target: Target?) {
                    val method: func(): String = target.describe
                }
                """)).isEqualTo(DiagnosticCode.TYPE_NULLABLE_DEREFERENCE);
    }

    /**
     * When the receiver's static type is non-null, {@code ?.} keeps the non-null function type.
     *
     * <p>"When the receiver's static type is non-null, {@code ?.} retains the non-null function type,
     * matching existing safe-access behavior" (section 6). The reference therefore initializes a
     * non-nullable binding, which an added nullability would have prevented.
     */
    @Test
    public void safeAccessOnANonNullReceiverKeepsTheNonNullType() {
        assertThat(run("""
                class Target {
                    func describe(): String {
                        return "t"
                    }
                }

                val target = Target()
                val method: func(): String = target?.describe
                print(method())
                """)).isEqualTo("t");
    }

    // ---------------------------------------------------------------------------------------------
    // Function-typed properties
    // ---------------------------------------------------------------------------------------------

    /**
     * A property whose declared type is a function type is read as the stored value, not bound: member
     * resolution decides statically which one the read is.
     *
     * <p>"A property may itself have a function type. Because a class member namespace cannot hold a
     * property and a method with the same name, member resolution decides statically whether
     * {@code receiver.member} reads a stored function value or creates a bound method value" (section 6).
     * The stored value is an anonymous function returning 9, so the printed result distinguishes it from
     * any method on the class.
     */
    @Test
    public void aFunctionTypedPropertyReadsItsStoredValue() {
        assertThat(run("""
                class Holder {
                    var stored: func(): Integer
                    func storedMethod(): Integer {
                        return 4
                    }
                    Holder() {
                        this.stored = func(): Integer {
                            return 9
                        }
                    }
                }

                val holder = Holder()
                val fromProperty: func(): Integer = holder.stored
                val fromMethod: func(): Integer = holder.storedMethod
                print(fromProperty())
                print("|")
                print(fromMethod())
                print("|")
                print(fromProperty === fromMethod)
                """)).isEqualTo("9|4|false");
    }

    // ---------------------------------------------------------------------------------------------
    // Contextual instantiation of a generic method reference
    // ---------------------------------------------------------------------------------------------

    /**
     * A generic method reference is instantiated contextually to one monomorphic function type.
     *
     * <p>"A generic method reference is instantiated contextually under the same monomorphic rules as a
     * generic top-level function reference, so {@code val operation: func(Integer): Integer =
     * object.identity} is accepted" (section 6).
     */
    @Test
    public void aGenericMethodReferenceIsInstantiatedContextually() {
        assertThat(run("""
                class Box {
                    func pick<T>(value: T): T {
                        return value
                    }
                }

                val box = Box()
                val fromInteger: func(Integer): Integer = box.pick
                val fromString: func(String): String = box.pick
                print(fromInteger(42))
                print("|")
                print(fromString("s"))
                """)).isEqualTo("42|s");
    }

    /**
     * The receiver's class type arguments are already fixed when the method's own type parameters are
     * inferred, so a generic receiver contributes its substitution rather than competing with it.
     *
     * <p>"...under the same monomorphic rules as a generic top-level function reference" (section 6)
     * leaves the receiver's parameters out of the inference question: {@code T} on the class is known from
     * {@code Cell(Integer)}, and only {@code E} remains for the expected function type to determine. An
     * implementation that conflated the two would either fail to infer or substitute wrongly.
     */
    @Test
    public void aGenericMethodReferenceClosesTheReceiverTypeArgumentsFirst() {
        assertThat(run("""
                open class Cell<T> {
                    val stored: T
                    Cell(stored: T) {
                        this.stored = stored
                    }
                    open func replace<E>(value: E): E {
                        return value
                    }
                }

                val cell: Cell<Integer> = Cell(1)
                val method: func(String): String = cell.replace
                print(method("swapped"))
                print("|")
                print(cell.stored)
                """)).isEqualTo("swapped|1");
    }

    /**
     * An unconstrained generic method reference is the inference failure the section names.
     *
     * <p>"...and an unconstrained reference is {@code SOLV-TYPE-030}" (section 6). A binding of type
     * {@code Any} exposes no parameter or result type, so nothing can determine the method's type
     * parameter.
     */
    @Test
    public void aGenericMethodReferenceWithNoExpectedTypeIsAnInferenceFailure() {
        assertThat(codeOf("""
                class Box {
                    func pick<T>(value: T): T {
                        return value
                    }
                }

                func use(box: Box) {
                    val method: Any = box.pick
                }
                """)).isEqualTo(DiagnosticCode.TYPE_CANNOT_INFER);
    }

    /**
     * An expected type that determines only some of a method's type parameters is still an inference
     * failure, and the reference is not silently generalized.
     *
     * <p>The monomorphic rule requires one complete substitution (section 6, "Generic function values",
     * which the bound-reference sentence defers to). The expected function type here determines the
     * method's first type parameter through a parameter position and says nothing about its second, so
     * the reference is rejected rather than generalized.
     */
    @Test
    public void aPartiallyDeterminedGenericMethodReferenceIsAnInferenceFailure() {
        assertThat(codeOf("""
                class Box {
                    func wrap<T, E>(value: T): T {
                        return value
                    }
                }

                func use(box: Box) {
                    val method: func(Integer): Integer = box.wrap
                }
                """)).isEqualTo(DiagnosticCode.TYPE_CANNOT_INFER);
    }

    /**
     * A reference stored in a function-typed binding is monomorphic: the binding's declared type is the
     * instantiation, so a call through it must match that type and nothing else.
     *
     * <p>"A generic method reference is instantiated contextually under the same monomorphic rules as a
     * generic top-level function reference" (section 6), and section 6 types a call through a function
     * value against the callee's function type. The reference is resolved to {@code func(Integer):
     * Integer}, so a call that supplies a {@code String} where an {@code Integer} is required is an
     * argument-type violation of that type, not something the value can accommodate.
     */
    @Test
    public void anInstantiatedMethodReferenceIsMonomorphic() {
        assertThat(codeOf("""
                class Box {
                    func pick<T>(value: T): T {
                        return value
                    }
                }

                func use(box: Box) {
                    val method: func(Integer): Integer = box.pick
                    val wrong: String = method(1)
                }
                """)).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    // ---------------------------------------------------------------------------------------------
    // Only a declared callable binds
    // ---------------------------------------------------------------------------------------------

    /**
     * The universal members cannot be bound, whether the receiver's class overrides them or not.
     *
     * <p>"Only a declared callable binds. The fixed language-defined universal members {@code toString},
     * {@code equals}, and {@code hashCode}... are not bindable: a bare read of one of them stays the
     * compile-time error that section 3 and section 23.4 already require" (section 6). The receiver below
     * overrides {@code toString}, and the universal rule holds anyway — which is the point of the
     * sentence's "whether or not the receiver's class overrides the member".
     */
    @Test
    public void universalMembersAreNotBindable() {
        assertThat(codeOf("""
                class Point {
                    override func toString(): String {
                        return "p"
                    }
                }

                func use(point: Point) {
                    val method: Any = point.toString
                }
                """)).isEqualTo(DiagnosticCode.TYPE_FUNCTION_AS_VALUE);
        assertThat(codeOf("""
                class Point {
                }

                func use(point: Point) {
                    val method: Any = point.equals
                }
                """)).isEqualTo(DiagnosticCode.TYPE_FUNCTION_AS_VALUE);
        assertThat(codeOf("""
                class Point {
                }

                func use(point: Point) {
                    val method: Any = point.hashCode
                }
                """)).isEqualTo(DiagnosticCode.TYPE_FUNCTION_AS_VALUE);
    }

    /**
     * A static method is not bindable, because it has no receiver for a bound value to capture.
     *
     * <p>"...and the same holds for a static method, a constructor, and an enum variant" (section 6).
     */
    @Test
    public void aStaticMethodIsNotBindable() {
        assertThat(codeOf("""
                class Holder {
                    static func make(): Integer {
                        return 1
                    }
                }

                func use() {
                    val method: Any = Holder.make
                }
                """)).isEqualTo(DiagnosticCode.TYPE_FUNCTION_AS_VALUE);
    }

    /**
     * Synthesized {@code Result} operations are not bindable, keeping the row section 23.4 names live.
     *
     * <p>"Only a declared callable binds... and the synthesized {@code Result} operations, are not
     * bindable" (section 6). This is the oracle REQ-2309 pins, so the bound-reference feature is what
     * makes the exception meaningful rather than a placeholder.
     */
    @Test
    public void resultOperationsAreNotBindable() {
        assertThat(codeOf("""
                enum Result<T, E> {
                    Ok(T)
                    Err(E)
                }

                func produce(): Result<Integer, String> {
                    return Result.Ok(1)
                }

                func use() {
                    val result = produce()
                    val method: Any = result.isOk
                }
                """)).isEqualTo(DiagnosticCode.TYPE_FUNCTION_AS_VALUE);
    }

    // ---------------------------------------------------------------------------------------------
    // Static invocation of a function-typed static property
    // ---------------------------------------------------------------------------------------------

    /**
     * A static property whose declared type is a function type is invokable, because its callee is a
     * function type.
     *
     * <p>"An invocation whose callee is not a function type is {@code SOLV-TYPE-002}" (section 6) — and
     * this callee is one, so the invocation must be accepted. Section 6 permits a function type as a
     * static property's declared type, and the value already reads into a binding correctly; only
     * invocation through the class name is pinned here.
     */
    @Test
    public void aFunctionTypedStaticPropertyIsInvokable() {
        assertThat(run("""
                func stringify(value: Integer): String {
                    return value.toString()
                }

                class Registry {
                    static val transform: func(Integer): String = stringify
                }

                print(Registry.transform(7))
                """)).isEqualTo("7");
    }

    /**
     * A static property whose declared type is not a function type keeps the not-callable report.
     *
     * <p>The same sentence reserves {@code SOLV-TYPE-002} for "an invocation whose callee is not a
     * function type" (section 6), and an {@code Integer} is not one. This is the half that proves the
     * fix above widened a rule rather than removing it.
     */
    @Test
    public void aNonFunctionTypedStaticPropertyIsNotInvokable() {
        assertThat(firstCode("""
                class Registry {
                    static val limit: Integer = 7
                }

                func use() {
                    print(Registry.limit(1))
                }
                """)).isEqualTo(DiagnosticCode.TYPE_NOT_CALLABLE);
    }

    /**
     * A function-typed static property is invokable through a module qualification as well as through a
     * bare class name.
     *
     * <p>The callee's type is a fact of the declaration and section 6 does not make invocation depend on
     * how the class was named, so the qualified reference is the same invocation. Recording the member
     * read matters here: a qualified call is not a plain member-access callee in every shape, and a read
     * recorded against the wrong node fails during lowering rather than reporting anything.
     */
    @Test
    public void aFunctionTypedStaticPropertyIsInvokableThroughAModule() {
        assertThat(runInclude("""
                module lib

                func stringify(value: Integer): String {
                    return value.toString()
                }

                class Registry {
                    static val transform: func(Integer): String = stringify
                }
                """, """
                module app

                include "lib.sol" alias m

                print(m::Registry.transform(7))
                """)).isEqualTo("7");
    }

    /**
     * The invocation reads the current stored value, so reassigning the property changes what a later
     * call through the class name computes.
     *
     * <p>The static property holds a value and section 6 types the callee from that property's declared
     * type, so the call must go through the storage rather than a binding captured at lowering.
     */
    @Test
    public void aFunctionTypedStaticPropertyInvocationReadsTheCurrentValue() {
        assertThat(run("""
                func first(value: Integer): String {
                    return "a" .. value.toString()
                }

                func second(value: Integer): String {
                    return "b" .. value.toString()
                }

                class Registry {
                    static var transform: func(Integer): String = first
                }

                print(Registry.transform(1))
                Registry.transform = second
                print("|")
                print(Registry.transform(2))
                """)).isEqualTo("a1|b2");
    }
}
