/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.solvik.test.SolvikTestSupport.parseFails;
import static org.solvik.test.SolvikTestSupport.parseOk;

import java.util.List;
import org.junit.Test;
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
                    fun save(value: String): Unit
                }
                class UserService implements Repository {
                    delegate val repository: Repository
                }
                """);
        ClassDeclNode service = (ClassDeclNode) unit.declarations().get(1);
        assertEquals(1, service.delegates().size());
        DelegateDeclNode repository = service.delegates().get(0);
        assertEquals(AstKind.DELEGATE_DECL, repository.kind());
        assertEquals("repository", repository.name());
        assertEquals("Repository", repository.declaredType().name());
        assertFalse(repository.initializer().isPresent());
    }

    @Test
    public void delegateCarriesADeclarationInitializer() {
        CompilationUnitNode unit = parseOk("delegateinit.sol", """
                interface Repository {
                    fun save(value: String): Unit
                }
                class UserService implements Repository {
                    delegate val repository: Repository = Repository()
                }
                """);
        DelegateDeclNode repository = ((ClassDeclNode) unit.declarations().get(1)).delegates().get(0);
        assertTrue(repository.initializer().isPresent());
        assertEquals(repository.initializer().get(), repository.children().get(1));
    }

    @Test
    public void delegatesAndPropertiesKeepMembersInSourceOrder() {
        CompilationUnitNode unit = parseOk("order.sol", """
                interface Named {
                    fun name(): String
                }
                class C implements Named {
                    val before: Int
                    delegate val shared: Named
                    var after: Int

                    init(shared: Named) {
                        this.before = 1
                        this.shared = shared
                        this.after = 2
                    }

                    fun name(): String {
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
        assertEquals(List.of("before", "shared", "after"), memberNames);
        assertEquals(List.of("before", "after"), c.properties().stream().map(PropertyDeclNode::name).toList());
        assertEquals(List.of("shared"), c.delegates().stream().map(DelegateDeclNode::name).toList());
    }

    @Test
    public void aDelegateMayPrecedeOrFollowAnInitializer() {
        CompilationUnitNode unit = parseOk("mixed.sol", """
                interface Named {
                    fun name(): String
                }
                class C implements Named {
                    delegate val shared: Named

                    init(shared: Named) {
                        this.shared = shared
                    }

                    fun name(): String {
                        return shared.name()
                    }
                }
                """);
        ClassDeclNode c = (ClassDeclNode) unit.declarations().get(1);
        assertEquals(1, c.delegates().size());
        assertEquals(1, c.initializers().size());
        assertEquals(1, c.methods().size());
    }

    @Test
    public void delegateVarIsRejected() {
        parseFails("delegatevar.sol", """
                interface Named {
                    fun name(): String
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
                    fun name(): String
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
                    fun name(): String
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
                    fun name(): String
                }
                class C implements Named {
                    delegate val shared: Named fun name(): String {
                        return "c"
                    }
                }
                """);
    }

    @Test
    public void aTopLevelDelegateIsRejected() {
        parseFails("toplevel.sol", """
                interface Named {
                    fun name(): String
                }
                delegate val shared: Named
                """);
    }

    @Test
    public void delegateIsNotAnExpressionName() {
        parseFails("expression.sol", """
                fun f(): Int {
                    return delegate
                }
                """);
    }

    @Test
    public void delegateNodeExposesItsDeclaredTypeAsAChild() {
        CompilationUnitNode unit = parseOk("children.sol", """
                interface Named {
                    fun name(): String
                }
                class C implements Named {
                    delegate val shared: Named
                }
                """);
        DelegateDeclNode shared = ((ClassDeclNode) unit.declarations().get(1)).delegates().get(0);
        List<AstNode> children = shared.children();
        assertEquals(1, children.size());
        assertEquals(AstKind.TYPE_REF, children.get(0).kind());
    }
}
