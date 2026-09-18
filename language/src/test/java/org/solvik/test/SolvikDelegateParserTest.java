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
import static org.solvik.test.SolvikTestSupport.parseFails;
import static org.solvik.test.SolvikTestSupport.parseOk;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.ClassDeclNode;
import org.solvik.ast.declaration.DelegateDeclNode;
import org.solvik.ast.declaration.PropertyDeclNode;

/**
 * Phase 9 parser tests: {@code delegate val name: InterfaceType} as a class member
 * (docs/LANGUAGE_SPEC.md section 9). A delegate is an immutable, explicitly typed property, so the
 * grammar requires {@code val} and a type annotation and permits a declaration initializer.
 */
public final class SolvikDelegateParserTest {

    @Test
    public void specificationDelegateShapeParses() {
        CompilationUnitNode unit = parseOk("delegate.sol", """
                interface Repository {
                    func save(value: String): Unit
                }
                class UserService implements Repository {
                    delegate val repository: Repository
                }
                """);
        ClassDeclNode service = (ClassDeclNode) unit.declarations().get(1);
        assertThat(service.delegates().size()).isEqualTo(1);
        DelegateDeclNode repository = service.delegates().get(0);
        assertThat(repository.kind()).isEqualTo(AstKind.DELEGATE_DECL);
        assertThat(repository.name()).isEqualTo("repository");
        assertThat(repository.declaredType().name()).isEqualTo("Repository");
        assertThat(repository.initializer().isPresent()).isFalse();
    }

    @Test
    public void delegateCarriesADeclarationInitializer() {
        CompilationUnitNode unit = parseOk("delegateinit.sol", """
                interface Repository {
                    func save(value: String): Unit
                }
                class UserService implements Repository {
                    delegate val repository: Repository = Repository()
                }
                """);
        DelegateDeclNode repository = ((ClassDeclNode) unit.declarations().get(1)).delegates().get(0);
        assertThat(repository.initializer().isPresent()).isTrue();
        assertThat(repository.children().get(1)).isEqualTo(repository.initializer().get());
    }

    @Test
    public void delegatesAndPropertiesKeepMembersInSourceOrder() {
        CompilationUnitNode unit = parseOk("order.sol", """
                interface Named {
                    func name(): String
                }
                class C implements Named {
                    val before: Int
                    delegate val shared: Named
                    var after: Int

                    C(shared: Named) {
                        this.before = 1
                        this.shared = shared
                        this.after = 2
                    }

                    func name(): String {
                        return "c"
                    }
                }
                """);
        ClassDeclNode c = (ClassDeclNode) unit.declarations().get(1);
        List<String> memberNames = new java.util.ArrayList<>();
        for (AstNode member : c.members()) {
            if (member instanceof PropertyDeclNode property) {
                memberNames.add(property.name());
            } else if (member instanceof DelegateDeclNode delegate) {
                memberNames.add(delegate.name());
            }
        }
        assertThat(memberNames).isEqualTo(List.of("before", "shared", "after"));
        assertThat(c.properties().stream().map(PropertyDeclNode::name).toList()).isEqualTo(List.of("before", "after"));
        assertThat(c.delegates().stream().map(DelegateDeclNode::name).toList()).isEqualTo(List.of("shared"));
    }

    @Test
    public void aDelegateMayPrecedeOrFollowAnInitializer() {
        CompilationUnitNode unit = parseOk("mixed.sol", """
                interface Named {
                    func name(): String
                }
                class C implements Named {
                    delegate val shared: Named

                    C(shared: Named) {
                        this.shared = shared
                    }

                    func name(): String {
                        return shared.name()
                    }
                }
                """);
        ClassDeclNode c = (ClassDeclNode) unit.declarations().get(1);
        assertThat(c.delegates().size()).isEqualTo(1);
        assertThat(c.constructors().size()).isEqualTo(1);
        assertThat(c.methods().size()).isEqualTo(1);
    }

    @Test
    public void delegateVarIsRejected() {
        parseFails("delegatevar.sol", """
                interface Named {
                    func name(): String
                }
                class C implements Named {
                    delegate var shared: Named
                }
                """);
    }

    @Test
    public void delegateRequiresAnExplicitType() {
        parseFails("delegatenotype.sol", """
                interface Named {
                    func name(): String
                }
                class C implements Named {
                    delegate val shared
                }
                """);
    }

    @Test
    public void delegateRequiresTheValKeyword() {
        parseFails("delegatenoval.sol", """
                interface Named {
                    func name(): String
                }
                class C implements Named {
                    delegate shared: Named
                }
                """);
    }

    @Test
    public void delegateWithoutATerminatorOnOneLineIsRejected() {
        parseFails("delegatenosemi.sol", """
                interface Named {
                    func name(): String
                }
                class C implements Named {
                    delegate val shared: Named func name(): String {
                        return "c"
                    }
                }
                """);
    }

    @Test
    public void aTopLevelDelegateIsRejected() {
        parseFails("toplevel.sol", """
                interface Named {
                    func name(): String
                }
                delegate val shared: Named
                """);
    }

    @Test
    public void delegateIsNotAnExpressionName() {
        parseFails("expression.sol", """
                func f(): Int {
                    return delegate
                }
                """);
    }

    @Test
    public void delegateNodeExposesItsDeclaredTypeAsAChild() {
        CompilationUnitNode unit = parseOk("children.sol", """
                interface Named {
                    func name(): String
                }
                class C implements Named {
                    delegate val shared: Named
                }
                """);
        DelegateDeclNode shared = ((ClassDeclNode) unit.declarations().get(1)).delegates().get(0);
        List<AstNode> children = shared.children();
        assertThat(children.size()).isEqualTo(1);
        assertThat(children.get(0).kind()).isEqualTo(AstKind.TYPE_REF);
    }
}
