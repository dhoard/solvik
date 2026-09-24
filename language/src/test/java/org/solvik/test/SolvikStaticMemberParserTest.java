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
import static org.solvik.test.SolvikTestSupport.assertNode;
import static org.solvik.test.SolvikTestSupport.parseFails;
import static org.solvik.test.SolvikTestSupport.parseOk;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.ClassDeclNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.declaration.PropertyDeclNode;
import org.solvik.ast.declaration.StaticBlockNode;

/**
 * Parser tests for static members and the class initializer (docs/LANGUAGE_SPEC.md section 7).
 *
 * <p>The load-bearing assertions are that a {@code static} member is <em>represented</em> in the AST
 * rather than parsed and discarded, and that the instance views {@code properties()} and
 * {@code methods()} exclude static members, so class-level storage cannot leak into per-instance
 * object layout, constructor initialization, or virtual dispatch.
 */
public final class SolvikStaticMemberParserTest {

    private static ClassDeclNode onlyClass(CompilationUnitNode cu) {
        assertThat(cu.declarations().size()).isEqualTo(1);
        return (ClassDeclNode) cu.declarations().get(0);
    }

    @Test
    public void aStaticPropertyAndAStaticMethodAreRepresentedAsStaticMembers() {
        String src = """
                class Counter {
                    static val limit: Integer = 10
                    static func reset() {
                        println("reset")
                    }
                }
                """;
        ClassDeclNode counter = onlyClass(parseOk("counter.sol", src));

        assertThat(counter.staticProperties()).hasSize(1);
        PropertyDeclNode limit = counter.staticProperties().get(0);
        assertThat(limit.isStatic()).isTrue();
        assertThat(limit.name()).isEqualTo("limit");
        // The member span begins at `static`, so the keyword cannot vanish from a diagnostic that
        // points at the member. The inserted terminating SEMI is not part of the span, matching how
        // instance properties are spanned.
        assertNode(limit, AstKind.PROPERTY_DECL, src, "static val limit: Integer = 10");

        assertThat(counter.staticMethods()).hasSize(1);
        FunctionDeclNode reset = counter.staticMethods().get(0);
        assertThat(reset.isStatic()).isTrue();
        assertThat(reset.name()).isEqualTo("reset");
        assertNode(reset, AstKind.FUNCTION_DECL, src, "static func reset() {\n        println(\"reset\")\n    }");

        assertThat(counter.members()).hasSize(2);
    }

    @Test
    public void staticMembersDoNotAppearInTheInstanceViews() {
        String src = """
                class Counter {
                    val instanceCount: Integer
                    static val limit: Integer = 10
                    func describe(): String {
                        return "counter"
                    }
                    static func reset() {
                        println("reset")
                    }
                }
                """;
        ClassDeclNode counter = onlyClass(parseOk("counter.sol", src));

        // Instance views exclude statics: object layout, constructor initialization, and virtual
        // dispatch all read these lists, and a static must not participate in any of them.
        assertThat(counter.properties()).hasSize(1);
        assertThat(counter.properties().get(0).name()).isEqualTo("instanceCount");
        assertThat(counter.methods()).hasSize(1);
        assertThat(counter.methods().get(0).name()).isEqualTo("describe");

        assertThat(counter.staticProperties()).hasSize(1);
        assertThat(counter.staticProperties().get(0).name()).isEqualTo("limit");
        assertThat(counter.staticMethods()).hasSize(1);
        assertThat(counter.staticMethods().get(0).name()).isEqualTo("reset");
    }

    @Test
    public void aStaticBlockIsAPreservedStatementListInSourceOrder() {
        String src = """
                class Counter {
                    static val limit: Integer = 10
                    static {
                        println("initializing")
                    }
                    static func reset() {
                        println("reset")
                    }
                }
                """;
        ClassDeclNode counter = onlyClass(parseOk("counter.sol", src));

        assertThat(counter.staticBlock()).isPresent();
        StaticBlockNode block = counter.staticBlock().get();
        assertNode(block, AstKind.STATIC_BLOCK, src, "static {\n        println(\"initializing\")\n    }");
        assertThat(block.body().statements()).hasSize(1);

        // Source order across all three forms is preserved in the single member list.
        assertThat(counter.members().stream().map(AstNode::kind).toList()).containsExactly(
                AstKind.PROPERTY_DECL, AstKind.STATIC_BLOCK, AstKind.FUNCTION_DECL);
    }

    @Test
    public void aStaticBlockPreservesSourceOrderAgainstLaterInstanceProperties() {
        String src = """
                class Counter {
                    static {
                        println("initializing")
                    }
                    val x: Integer
                }
                """;
        ClassDeclNode counter = onlyClass(parseOk("counter.sol", src));
        assertThat(counter.members().stream().map(AstNode::kind).toList())
                .containsExactly(AstKind.STATIC_BLOCK, AstKind.PROPERTY_DECL);
        assertThat(counter.properties()).hasSize(1);
    }

    @Test
    public void aStaticBlockTerminatesWithoutASemicolonThroughInsertion() {
        // A block ends in `}`, which is a semicolon-insertion terminator, so the class body needs no
        // explicit `;` after the initializer and may still be closed by the next line's `}`.
        String src = """
                class Counter {
                    static {
                        println("initialized")
                    }
                }
                """;
        ClassDeclNode counter = onlyClass(parseOk("counter.sol", src));
        assertThat(counter.staticBlock()).isPresent();
    }

    @Test
    public void aStaticPropertyStillRequiresAnExplicitTypeAnnotation() {
        // The `static` prefix does not relax the declaration rule: `propertyDecl` mandates
        // `: type`, so the missing annotation is a parse error exactly as it is for an instance
        // property (docs/LANGUAGE_SPEC.md sections 6 and 7).
        parseFails("counter.sol", """
                class Counter {
                    static val limit = 10
                }
                """);
    }

    @Test
    public void aStaticDelegateIsAParseErrorBecauseOnlyPropertiesAndMethodsMayBeStatic() {
        parseFails("speaker.sol", """
                interface Greeter {
                    func greet(): String
                }
                class Speaker {
                    static delegate impl: Greeter
                }
                """);
    }

    @Test
    public void aStaticConstructorIsAParseErrorBecauseConstructorsAreAlwaysInstanceLevel() {
        parseFails("counter.sol", """
                class Counter {
                    static Counter() {
                    }
                }
                """);
    }

    @Test
    public void staticIsReservedSoItCanNoLongerBeUsedAsAnIdentifier() {
        // Reserving `static` is an intentional breaking change; earlier spellings must fail loudly at
        // parse time rather than silently resolve to something else.
        parseFails("reserved.sol", """
                let static: Integer = 1
                """);
        parseFails("property.sol", """
                class C {
                    val static: Integer
                }
                """);
    }

    @Test
    public void aStaticBlockIsNotAcceptedInAnInterfaceOrEnumBody() {
        parseFails("iface.sol", """
                interface HasName {
                    static {
                        println("nope")
                    }
                }
                """);
        parseFails("dir.sol", """
                enum Direction {
                    NORTH
                    static {
                        println("nope")
                    }
                }
                """);
    }

    @Test
    public void methodModifiersRemainGrammaticalAfterStaticSoTheSemanticLayerCanReportThem() {
        // `static` always leads, so `open static` is a parse error, but `static open` parses so the
        // override rules can report the precise SOLV-SEM-047 diagnostic instead of a bare parse error.
        String src = """
                class Counter {
                    static open func reset() {
                        println("reset")
                    }
                }
                """;
        ClassDeclNode counter = onlyClass(parseOk("counter.sol", src));
        List<FunctionDeclNode> statics = counter.staticMethods();
        assertThat(statics).hasSize(1);
        assertThat(statics.get(0).isStatic()).isTrue();
        assertThat(statics.get(0).isOpen()).isTrue();
    }

    @Test
    public void twoStaticBlocksBothReachTheSemanticLayerWhichRejectsTheSecond() {
        // The grammar tolerates the duplicate so the diagnostic names the second block
        // (SOLV-SEM-046) rather than failing with a bare parse error.
        String src = """
                class Counter {
                    static {
                        println("first")
                    }
                    static {
                        println("second")
                    }
                }
                """;
        ClassDeclNode counter = onlyClass(parseOk("counter.sol", src));
        assertThat(counter.staticBlocks()).hasSize(2);
    }
}
