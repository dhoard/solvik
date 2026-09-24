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

import org.junit.jupiter.api.Test;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.semantic.CheckedProgram;
import org.solvik.semantic.ClassSymbol;
import org.solvik.semantic.FunctionSymbol;
import org.solvik.semantic.PropertySymbol;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;
import org.solvik.type.IntegerType;
import org.solvik.type.StringType;

/**
 * Positive static-semantics tests for static members and the class initializer
 * (docs/LANGUAGE_SPEC.md section 7). The load-bearing assertions are the separation invariants: a
 * static property never appears in the instance layout and a static method never enters the virtual
 * dispatch table, because those two lists are what object construction, property access, and virtual
 * dispatch read.
 */
public final class SolvikStaticMemberSemanticTest {

    private static CheckedProgram check(String text) {
        CompilationUnitNode unit = parseOk("static.sol", text);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertThat(result.isSuccess()).as("analysis must succeed: " + result.diagnostics().all()).isTrue();
        return result.requireProgram();
    }

    @Test
    public void staticMembersAreRecordedSeparatelyFromInstanceMembers() {
        ClassSymbol counter = check("""
                class Counter {
                    static val limit: Integer = 10
                    static func reset() {
                        println("reset")
                    }
                    val id: Integer = 1
                    func describe(): String {
                        return "counter"
                    }
                }
                """).classSymbol("Counter").orElseThrow();

        assertThat(counter.declaredStaticProperties()).hasSize(1);
        assertThat(counter.declaredStaticProperties().get(0).name()).isEqualTo("limit");
        assertThat(counter.declaredStaticMethods()).hasSize(1);
        assertThat(counter.declaredStaticMethods().get(0).name()).isEqualTo("reset");

        // The separation invariants. Object layout reads properties(), virtual dispatch reads
        // methods(), and neither may contain a static member.
        assertThat(counter.properties()).hasSize(1);
        assertThat(counter.properties().get(0).name()).isEqualTo("id");
        assertThat(counter.declaredMethods()).hasSize(1);
        assertThat(counter.declaredMethods().get(0).name()).isEqualTo("describe");
        assertThat(counter.method("reset")).isEmpty();
        assertThat(counter.property("limit")).isEmpty();
    }

    @Test
    public void aStaticPropertyHasNoInstanceFieldSlot() {
        ClassSymbol counter = check("""
                class Counter {
                    val id: Integer = 1
                    static val limit: Integer = 10
                }
                """).classSymbol("Counter").orElseThrow();

        // The instance property keeps slot 0; the static is marked with the sentinel so lowering can
        // never reserve an object slot for class-level storage.
        assertThat(counter.properties().get(0).index()).isEqualTo(0);
        assertThat(counter.declaredStaticProperties().get(0).index()).isEqualTo(PropertySymbol.STATIC_SLOT);
        assertThat(PropertySymbol.STATIC_SLOT).isNegative();
    }

    @Test
    public void aStaticPropertyKeepsItsOwnDeclaredTypeAndMutability() {
        ClassSymbol counter = check("""
                class Counter {
                    static val limit: Integer = 10
                    static var attempts: String = "none"
                }
                """).classSymbol("Counter").orElseThrow();

        PropertySymbol limit = counter.staticProperty("limit").orElseThrow();
        assertThat(limit.type()).isEqualTo(IntegerType.INSTANCE);
        assertThat(limit.isMutable()).isFalse();
        assertThat(limit.hasInitializer()).isTrue();

        PropertySymbol attempts = counter.staticProperty("attempts").orElseThrow();
        assertThat(attempts.type()).isEqualTo(StringType.INSTANCE);
        assertThat(attempts.isMutable()).isTrue();
    }

    @Test
    public void aStaticMethodIsMarkedStaticWhileAnInstanceMethodIsNot() {
        ClassSymbol counter = check("""
                class Counter {
                    static func reset() {
                        println("reset")
                    }
                    func describe(): String {
                        return "counter"
                    }
                }
                """).classSymbol("Counter").orElseThrow();

        FunctionSymbol reset = counter.staticMethod("reset").orElseThrow();
        assertThat(reset.isStatic()).isTrue();
        assertThat(counter.method("describe").orElseThrow().isStatic()).isFalse();
    }

    @Test
    public void theClassInitializerBlockIsRecordedOnTheClassSymbol() {
        ClassSymbol counter = check("""
                class Counter {
                    static val limit: Integer = 10
                    static {
                        println("initializing")
                    }
                }
                """).classSymbol("Counter").orElseThrow();

        assertThat(counter.staticBlock()).isPresent();
        assertThat(counter.staticBlock().orElseThrow().body().statements()).hasSize(1);
    }

    @Test
    public void aClassWithoutStaticMembersHasEmptyStaticCollections() {
        ClassSymbol plain = check("""
                class Plain {
                    val id: Integer = 1
                    func describe(): String {
                        return "plain"
                    }
                }
                """).classSymbol("Plain").orElseThrow();

        assertThat(plain.declaredStaticProperties()).isEmpty();
        assertThat(plain.declaredStaticMethods()).isEmpty();
        assertThat(plain.staticBlock()).isEmpty();
    }

    @Test
    public void aStaticMemberDoesNotDisturbConstructorInitializationOfInstanceProperties() {
        // With no constructor written, every instance property must still carry an initializer; the
        // rule is reported for instance properties only, never for a static without one.
        CheckedProgram program = check("""
                class Counter {
                    static val limit: Integer
                    val id: Integer = 1
                }
                """);
        ClassSymbol counter = program.classSymbol("Counter").orElseThrow();
        assertThat(counter.properties()).hasSize(1);
        assertThat(counter.properties().get(0).hasInitializer()).isTrue();
        // A static with no initializer is legal: it simply holds its type's default value.
        assertThat(counter.declaredStaticProperties().get(0).hasInitializer()).isFalse();
    }

    @Test
    public void anUnqualifiedCallToASiblingStaticMethodResolves() {
        // Inside a static member an unqualified call may reach another static of the same class; this
        // is the counterpart to the negative test that forbids reaching an instance method.
        CheckedProgram program = check("""
                class Counter {
                    static func base(): Integer {
                        return 1
                    }
                    static func total(): Integer {
                        return base()
                    }
                }
                """);
        ClassSymbol counter = program.classSymbol("Counter").orElseThrow();
        assertThat(counter.staticMethod("total")).isPresent();
    }

    @Test
    public void aClassInitializerMayCallASiblingStaticMethod() {
        check("""
                class Counter {
                    static func initialize(): Integer {
                        return 1
                    }
                    static {
                        val ignored: Integer = initialize()
                    }
                }
                """);
    }

    @Test
    public void aStaticMethodMayDeclareItsOwnTypeParameters() {
        // Only the enclosing class's type parameters are forbidden; a static's own are legal, matching
        // Java's `static <T> T id(T value)`.
        ClassSymbol counter = check("""
                class Counter {
                    static func identity<Value>(value: Value): Value {
                        return value
                    }
                }
                """).classSymbol("Counter").orElseThrow();

        FunctionSymbol identity = counter.staticMethod("identity").orElseThrow();
        assertThat(identity.typeParameters()).hasSize(1);
    }

    @Test
    public void aStaticMemberIsNotInheritedSoASubclassMayDeclareTheSameName() {
        // Statics are reached through the declaring class's name, so a base and a derived class sharing
        // a static name are two independent members rather than a shadowing conflict.
        CheckedProgram program = check("""
                open class Base {
                    static val label: String = "base"
                }
                class Derived extends Base {
                    static val label: String = "derived"
                }
                """);
        ClassSymbol base = program.classSymbol("Base").orElseThrow();
        ClassSymbol derived = program.classSymbol("Derived").orElseThrow();

        assertThat(base.staticProperty("label")).isPresent();
        assertThat(derived.staticProperty("label")).isPresent();
        // Not inherited: the derived class's own view does not expose the base's static, and the base
        // contributes nothing to the derived class's instance layout.
        assertThat(derived.declaredStaticProperties()).hasSize(1);
        assertThat(derived.properties()).isEmpty();
    }

    @Test
    public void instanceMembersKeepResolvingNormallyAlongsideStaticMembers() {
        // Guard against the static-context flag leaking: an instance method still reaches a sibling
        // instance method through an unqualified call and still uses `this`.
        check("""
                class Counter {
                    val id: Integer = 1
                    func count(): Integer {
                        return this.id
                    }
                    func twice(): Integer {
                        return this.count()
                    }
                    static func reset() {
                        println("reset")
                    }
                }
                """);
    }

    @Test
    public void aStaticMemberInOneClassDoesNotLeakIntoAnUnrelatedClass() {
        ClassSymbol other = check("""
                class Counter {
                    static val limit: Integer = 10
                }
                class Other {
                    val id: Integer = 1
                }
                """).classSymbol("Other").orElseThrow();

        assertThat(other.declaredStaticProperties()).isEmpty();
        assertThat(other.declaredStaticMethods()).isEmpty();
        assertThat(other.staticBlock()).isEmpty();
    }

    @Test
    public void theImplicitMainEntryPointIsUnaffectedByStaticMembers() {
        // The whole file is still analyzed as an implicit `main`, so a class carrying static members
        // must not displace or corrupt the recorded entry point.
        CheckedProgram program = check("""
                class Counter {
                    static val limit: Integer = 10
                    static func reset() {
                        println("reset")
                    }
                }
                println("running")
                """);
        assertThat(program.entryPoint()).isPresent();
        assertThat(program.classSymbol("Counter").orElseThrow().declaredStaticProperties()).hasSize(1);
    }
}
