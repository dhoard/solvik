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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;

/**
 * End-to-end execution tests for static members (docs/LANGUAGE_SPEC.md section 7) run through the
 * Truffle AST backend.
 *
 * <p>The first test is a regression: lowering once walked every property declaration when building a
 * constructor body and looked each one up in the instance property layout, so a declared
 * {@code static val} reached an internal "no symbol for property" failure that surfaced as a raw host
 * exception rather than a diagnostic. A static property has no instance slot, so the constructor body
 * must skip it.
 */
public final class SolvikStaticMemberExecutionTest {

    private static String run(String source) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            context.eval(build(source, "test.sol"));
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

    private static String evalFile(Path root) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PolyglotException failure = null;
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            context.eval(Source.newBuilder("solvik", root.toFile()).build());
        } catch (PolyglotException e) {
            failure = e;
        }
        assertThat(failure).as("unexpected failure: " + failure).isNull();
        return out.toString(StandardCharsets.UTF_8);
    }

    private static Path write(Path directory, String name, String content) throws IOException {
        Path file = directory.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    public void aClassWithAStaticPropertyAndAnInstancePropertyStillConstructs() {
        assertThat(run("""
                class Counter {
                    static val limit: Integer = 10
                    val id: Integer = 1
                }
                val first = Counter()
                println(first.id)
                """)).isEqualTo("1\n");
    }

    @Test
    public void aStaticPropertyWithoutAnInitializerDoesNotRequireAConstructor() {
        // A static holds its type's default value until assigned, so the "every property needs an
        // initializer" rule must not fire for it, and the instance property still initializes.
        assertThat(run("""
                class Counter {
                    static val limit: Integer
                    val id: Integer = 7
                }
                val first = Counter()
                println(first.id)
                """)).isEqualTo("7\n");
    }

    @Test
    public void aStaticMemberDoesNotAffectInstanceLayoutAcrossSeveralInstances() {
        // Each object carries only its own instance properties: three constructions produce three
        // independent ids, which is what would break if a static joined the instance layout.
        assertThat(run("""
                class Counter {
                    static val limit: Integer = 10
                    val id: Integer
                    Counter(id: Integer) {
                        this.id = id
                    }
                }
                println(Counter(1).id)
                println(Counter(2).id)
                println(Counter(3).id)
                """)).isEqualTo("1\n2\n3\n");
    }

    @Test
    public void anInstanceMethodStillDispatchesNormallyInAClassThatDeclaresStaticMembers() {
        assertThat(run("""
                class Counter {
                    static func helper(): Integer {
                        return 5
                    }
                    val base: Integer
                    Counter(base: Integer) {
                        this.base = base
                    }
                    func doubled(): Integer {
                        return this.base * 2
                    }
                }
                println(Counter(21).doubled())
                """)).isEqualTo("42\n");
    }

    @Test
    public void aClassInitializerBlockRunsOnceOnFirstActiveUse() {
        // The initializer does not run before `println("main")`; reading the static property is the first
        // active use, so the block runs then, and only once (docs/LANGUAGE_SPEC.md section 7).
        assertThat(run("""
                class Counter {
                    static var seen: Integer = 0
                    static {
                        println("initializer ran")
                    }
                }
                println("main")
                println(Counter.seen)
                println(Counter.seen)
                """)).isEqualTo("main\ninitializer ran\n0\n0\n");
    }

    @Test
    public void aClassInitializerBlockOfAnUnusedClassNeverRuns() {
        // Initialization is lazy, not Java-independent eager: a class the program never actively uses is
        // never initialized, so its block never runs and its cells keep their defaults
        // (docs/LANGUAGE_SPEC.md section 7).
        assertThat(run("""
                class Unused {
                    static {
                        println("unused init")
                    }
                }
                println("main")
                """)).isEqualTo("main\n");
    }

    @Test
    public void noClassInitializerRunsWhenTheProgramHasNoExecutableStatements() {
        // A program with no top-level statements has no active use of any class, so nothing is
        // initialized. Under the previous eager model this printed; under lazy semantics it is silent
        // (docs/LANGUAGE_SPEC.md section 7).
        assertThat(run("""
                class OnlyClass {
                    static {
                        println("init with no main")
                    }
                }
                """)).isEqualTo("");
    }

    @Test
    public void aReturnExitsAClassInitializerEarly() {
        assertThat(run("""
                class Counter {
                    static var log: Integer = 0
                    static {
                        Counter.log = 1
                        if (true) {
                            return
                        }
                        Counter.log = 2
                    }
                }
                println(Counter.log)
                """)).isEqualTo("1\n");
    }

    @Test
    public void aBaseClassInitializerRunsBeforeTheDerivedClassInitializer() {
        assertThat(run("""
                open class Base {
                    static var trace: Integer = 0
                    static {
                        Base.trace = 10
                    }
                }
                class Derived extends Base {
                    static var derivedTrace: Integer = 0
                    static {
                        Derived.derivedTrace = Base.trace * 100
                    }
                }
                println(Derived.derivedTrace)
                """)).isEqualTo("1000\n");
    }

    @Test
    public void aDerivedClassInitializerRunsAfterASuperclassDeclaredLaterInSource() {
        // Lazy, dependency-ordered initialization: on the first active use of Derived, its superclass Base
        // is initialized first even though Base is declared later in the source, so the base value is set
        // up before the derived initializer reads it (docs/LANGUAGE_SPEC.md section 7).
        assertThat(run("""
                class Derived extends Base {
                    static var derivedTrace: Integer = 0
                    static {
                        Derived.derivedTrace = Base.trace * 100
                    }
                }
                open class Base {
                    static var trace: Integer = 0
                    static {
                        Base.trace = 10
                    }
                }
                println(Derived.derivedTrace)
                """)).isEqualTo("1000\n");
    }

    @Test
    public void anIntermediateClassInitializerRunsBetweenItsAncestors() {
        // Actively using the most-derived class C initializes the whole chain base-first, so the blocks
        // append in A, B, C order (docs/LANGUAGE_SPEC.md section 7).
        assertThat(run("""
                open class A {
                    static var trace: String = ""
                    static {
                        A.trace = A.trace .. "A"
                    }
                }
                open class B extends A {
                    static {
                        A.trace = A.trace .. "B"
                    }
                }
                class C extends B {
                    static func report(): String {
                        return A.trace
                    }
                    static {
                        A.trace = A.trace .. "C"
                    }
                }
                println(C.report())
                """)).isEqualTo("ABC\n");
    }

    @Test
    public void eachStaticPropertyIsInitializedBeforeItsClassInitializerBlock() {
        assertThat(run("""
                class Counter {
                    static var seed: Integer = 5
                    static var seen: Integer = 0
                    static {
                        Counter.seen = Counter.seed
                    }
                }
                println(Counter.seen)
                """)).isEqualTo("5\n");
    }

    @Test
    public void aCrossClassStaticInitializerResolvesRegardlessOfDeclarationOrder() {
        // Regression: under the previous eager, declaration-ordered model a static initializer that read
        // another class's static saw 0 when the reader was declared first and the real value when the
        // dependency was declared first. Lazy, use-triggered initialization makes the two orders agree
        // (docs/LANGUAGE_SPEC.md section 7).
        String forward = """
                class B {
                    static var b: Integer = 5
                }
                class A {
                    static var a: Integer = B.b + 1
                }
                println(A.a)
                """;
        String reversed = """
                class A {
                    static var a: Integer = B.b + 1
                }
                class B {
                    static var b: Integer = 5
                }
                println(A.a)
                """;
        assertThat(run(forward)).isEqualTo("6\n");
        assertThat(run(reversed)).isEqualTo("6\n");
    }

    @Test
    public void constructingAnInstanceInitializesItsClassBeforeTheConstructorRuns() {
        // Construction is an active use, so the class initializer runs before the new object is built, and
        // a static the initializer set is already visible inside the constructor (docs/LANGUAGE_SPEC.md
        // section 7).
        assertThat(run("""
                class Thing {
                    static var base: Integer = 0
                    static {
                        Thing.base = 10
                    }
                    var field: Integer
                    Thing() {
                        this.field = Thing.base
                    }
                }
                println(Thing().field)
                """)).isEqualTo("10\n");
    }

    @Test
    public void aTypeTestIsNotAnActiveUseAndDoesNotInitializeAClass() {
        // `is`/`as` type tests are not active uses, so a test against an otherwise untouched class does not
        // initialize it and its block never runs (docs/LANGUAGE_SPEC.md section 7).
        assertThat(run("""
                open class Probe {
                    static {
                        println("probe initialized")
                    }
                }
                val value: Any = 1
                println(value is Probe)
                """)).isEqualTo("false\n");
    }

    @Test
    public void aStaticMethodIsCallableFromAClassInitializerOfAnotherClass() {
        assertThat(run("""
                class Helper {
                    static func answer(): Integer {
                        return 42
                    }
                }
                class UsesHelper {
                    static var value: Integer = 0
                    static {
                        UsesHelper.value = Helper.answer()
                    }
                }
                println(UsesHelper.value)
                """)).isEqualTo("42\n");
    }

    @Test
    public void anInitializedStaticPropertyOfEachPrimitiveTypeReadsItsTypeDefault() {
        // The cell is pre-seeded with the declared type's default, so an uninitialized static and one
        // explicitly initialized to the zero literal must be indistinguishable, including through `==`
        // for the object-represented types.
        assertThat(run("""
                class Defaults {
                    static var i: Integer
                    static var l: Long
                    static var f: Float
                    static var d: Double
                    static var b: Boolean
                    static var by: Byte
                    static var sh: Short
                    static var c: Character
                    static func report() {
                        println(Defaults.i == 0)
                        println(Defaults.l == Long(0))
                        println(Defaults.f == Float(0.0))
                        println(Defaults.d == Double(0.0))
                        println(Defaults.b == false)
                        println(Defaults.by == Byte(0))
                        println(Defaults.sh == Short(0))
                        println(Defaults.c == '\\0')
                    }
                }
                Defaults.report()
                """)).isEqualTo("true\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\n");
    }

    @Test
    public void anInitializedStaticReferencePropertyDefaultsToNull() {
        assertThat(run("""
                class Defaults {
                    static var name: String
                }
                println(Defaults.name)
                """)).isEqualTo("null\n");
    }

    @Test
    public void aStaticCellIsSharedAcrossEveryReferenceIncludingFromInstanceMethods() {
        assertThat(run("""
                class Counter {
                    static var count: Integer = 0
                    static func bump(): Integer {
                        Counter.count = Counter.count + 1
                        return Counter.count
                    }
                    func bumpToo(): Integer {
                        return Counter.bump()
                    }
                }
                print(Counter.bump())
                print(Counter().bumpToo())
                print(Counter.count)
                """)).isEqualTo("122");
    }

    @Test
    public void aSuperclassAndSubclassStaticPropertyOfTheSameNameAreIndependent() {
        assertThat(run("""
                open class Base {
                    static var count: Integer = 1
                }
                class Derived extends Base {
                    static var count: Integer = 2
                    static func report(): String {
                        print(Base.count)
                        print(Derived.count)
                        return ""
                    }
                }
                Derived.report()
                """)).isEqualTo("12");
    }

    @Test
    public void aQualifiedStaticMemberIsInitializedAndReachedThroughItsModulePrefix() throws IOException {
        Path dir = Files.createTempDirectory("solvik-static-modules");
        try {
            write(dir, "math.sol", "module math\n\nclass Counter {\n    static var count: Integer = 0\n    static val limit: Integer = 7\n    static func bump(): Integer {\n        Counter.count = Counter.count + 1\n        return Counter.count\n    }\n    static {\n        Counter.count = 100\n    }\n}\n");
            Path root = write(dir, "root.sol", "include \"math.sol\" alias math\nprint(math::Counter.count)\nprint(math::Counter.limit)\nprint(math::Counter.bump())\nmath::Counter.count = 5\nprint(math::Counter.count)\n");
            assertThat(evalFile(root)).isEqualTo("10071015");
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    public void aReturnStatementInAClassInitializerDoesNotCrashTheCompiler() {
        // Regression: the block was analyzed with no enclosing callable, and the return check read that
        // callable unconditionally, so this escaped as a raw host NullPointerException instead of either
        // compiling or reporting a diagnostic.
        assertThat(run("""
                class Counter {
                    static {
                        return
                    }
                }
                println("main")
                """)).isEqualTo("main\n");
    }

    @Test
    public void aClassInitializerCannotReturnValueBecauseItReturnsNothing() {
        // A rejected program reports through the context rather than printing, matching how every other
        // static-semantics failure surfaces end to end.
        assertThatExceptionOfType(PolyglotException.class).isThrownBy(() -> run("""
                class Counter {
                    static {
                        return 1
                    }
                }
                println("main")
                """)).withMessageContaining("SOLV-TYPE-011").withMessageContaining("class initializer block cannot return a value");
    }
}
