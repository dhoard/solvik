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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.solvik.test.SolvikTestSupport.parseOk;

import org.junit.Test;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.ClassDeclNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.declaration.ConstructorDeclNode;
import org.solvik.ast.expression.CallExprNode;
import org.solvik.ast.expression.MemberAccessExprNode;
import org.solvik.ast.expression.ThisExprNode;
import org.solvik.ast.statement.AssignStmtNode;
import org.solvik.ast.statement.ExprStmtNode;
import org.solvik.ast.statement.LocalDeclNode;
import org.solvik.ast.statement.ReturnStmtNode;
import org.solvik.semantic.CheckedProgram;
import org.solvik.semantic.ClassSymbol;
import org.solvik.semantic.PropertySymbol;
import org.solvik.semantic.ResolvedMethod;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;
import org.solvik.type.ClassType;
import org.solvik.type.FunctionType;
import org.solvik.type.IntType;
import org.solvik.type.StringType;

/**
 * Positive Phase 6 static-semantics tests: class declarations produce class descriptors with
 * properties, methods, and a constructor; construction, member access, method invocation, and
 * {@code this} all resolve to recorded compiler facts.
 */
public final class SolvikClassSemanticTest {

    private static CheckedProgram check(String text) {
        CompilationUnitNode unit = parseOk("class.sol", text);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertTrue("analysis must succeed: " + result.diagnostics().all(), result.isSuccess());
        return result.requireProgram();
    }

    private static FunctionDeclNode function(CheckedProgram program, int index) {
        return (FunctionDeclNode) program.unit().declarations().get(index);
    }

    private static ClassDeclNode classDeclaration(CheckedProgram program, int index) {
        return (ClassDeclNode) program.unit().declarations().get(index);
    }

    @Test
    public void classSymbolDescribesPropertiesMethodsAndConstructor() {
        CheckedProgram program = check("""
                class User {
                    val id: Int
                    var name: String

                    User(id: Int, name: String) {
                        this.id = id
                        this.name = name
                    }

                    func describe(): String {
                        return this.name
                    }
                }
                """);
        ClassSymbol user = program.classSymbol("User").orElseThrow();
        assertTrue(user.type() instanceof ClassType);
        assertEquals("User", user.name());
        assertEquals(user, program.classOf(classDeclaration(program, 0)).orElseThrow());

        assertEquals(2, user.properties().size());
        PropertySymbol id = user.property("id").orElseThrow();
        assertEquals(IntType.INSTANCE, id.type());
        assertFalse(id.isMutable());
        assertFalse(id.hasInitializer());
        assertEquals(0, id.index());

        PropertySymbol name = user.property("name").orElseThrow();
        assertEquals(StringType.INSTANCE, name.type());
        assertTrue(name.isMutable());
        assertEquals(1, name.index());

        assertEquals(1, user.methods().size());
        assertTrue(user.method("describe").orElseThrow().isMethod());
        assertTrue(user.hasExplicitInit());
        assertEquals(2, user.constructor().orElseThrow().parameters().size());
        assertEquals("User", user.constructor().orElseThrow().owner().name());
    }

    @Test
    public void constructionCallIsTypedAndResolved() {
        CheckedProgram program = check("""
                class User {
                    val id: Int
                    User(id: Int) {
                        this.id = id
                    }
                }
                func make(): User {
                    return User(1)
                }
                """);
        FunctionDeclNode make = function(program, 1);
        CallExprNode construction = (CallExprNode) ((ReturnStmtNode) make.body().statements().get(0)).value().orElseThrow();
        ClassSymbol user = program.classSymbol("User").orElseThrow();
        assertEquals(user, program.constructorOf(construction).orElseThrow());
        assertEquals(user.type(), program.typeOf(construction).orElseThrow());
        assertEquals(user.type(), program.typeOf(construction.callee()).orElseThrow());
    }

    @Test
    public void memberReadsAndWritesResolveToProperties() {
        CheckedProgram program = check("""
                class User {
                    var name: String
                    User(name: String) {
                        this.name = name
                    }
                }
                func use(u: User): String {
                    val current = u.name
                    u.name = "new"
                    return current
                }
                """);
        FunctionDeclNode use = function(program, 1);
        LocalDeclNode local = (LocalDeclNode) use.body().statements().get(0);
        MemberAccessExprNode read = (MemberAccessExprNode) local.initializer();
        PropertySymbol name = program.classSymbol("User").orElseThrow().property("name").orElseThrow();
        assertEquals(name, program.propertyOf(read).orElseThrow());
        assertEquals(StringType.INSTANCE, program.typeOf(read).orElseThrow());

        AssignStmtNode write = (AssignStmtNode) use.body().statements().get(1);
        MemberAccessExprNode target = (MemberAccessExprNode) write.target();
        assertEquals(name, program.propertyOf(target).orElseThrow());
    }

    @Test
    public void methodCallsAreResolvedWithAndWithoutThis() {
        CheckedProgram program = check("""
                class Greeter {
                    val name: String
                    Greeter(name: String) {
                        this.name = name
                    }
                    func greeting(): String {
                        return label()
                    }
                    func label(): String {
                        return this.name
                    }
                }
                func use(g: Greeter): String {
                    return g.greeting()
                }
                """);
        ClassSymbol greeter = program.classSymbol("Greeter").orElseThrow();

        FunctionDeclNode greeting = greeter.declaration().methods().get(0);
        CallExprNode implicit = (CallExprNode) ((ReturnStmtNode) greeting.body().statements().get(0)).value().orElseThrow();
        ResolvedMethod implicitResolved = program.methodOf(implicit).orElseThrow();
        assertEquals(greeter.method("label").orElseThrow(), implicitResolved.method());
        assertTrue(implicitResolved.isImplicitThis());
        assertEquals(StringType.INSTANCE, program.typeOf(implicit).orElseThrow());

        FunctionDeclNode use = function(program, 1);
        CallExprNode explicit = (CallExprNode) ((ReturnStmtNode) use.body().statements().get(0)).value().orElseThrow();
        ResolvedMethod explicitResolved = program.methodOf(explicit).orElseThrow();
        assertFalse(explicitResolved.isImplicitThis());
        MemberAccessExprNode callee = (MemberAccessExprNode) explicit.callee();
        assertTrue(program.typeOf(callee).orElseThrow() instanceof FunctionType);
        assertEquals(StringType.INSTANCE, program.typeOf(explicit).orElseThrow());
    }

    @Test
    public void thisHasTheEnclosingClassType() {
        CheckedProgram program = check("""
                class Holder {
                    val value: Int
                    Holder(value: Int) {
                        this.value = value
                    }
                    func self(): Holder {
                        return this
                    }
                }
                """);
        ClassSymbol holder = program.classSymbol("Holder").orElseThrow();
        ConstructorDeclNode constructorDecl = holder.declaration().constructors().get(0);
        AssignStmtNode assignment = (AssignStmtNode) constructorDecl.body().statements().get(0);
        MemberAccessExprNode target = (MemberAccessExprNode) assignment.target();
        ThisExprNode thisExpression = (ThisExprNode) target.receiver();
        assertEquals(holder.type(), program.typeOf(thisExpression).orElseThrow());
    }

    @Test
    public void propertyDeclarationInitializersAreTyped() {
        CheckedProgram program = check("""
                class Counter {
                    var count: Int = 0
                    val label: String = "c"
                }
                """);
        ClassSymbol counter = program.classSymbol("Counter").orElseThrow();
        assertTrue(counter.property("count").orElseThrow().hasInitializer());
        assertTrue(counter.property("label").orElseThrow().hasInitializer());
        // A class with no init builds only when every property has a declaration initializer.
        assertFalse(counter.hasExplicitInit());
    }

    @Test
    public void methodBodiesMayUseLocalsLoopsAndConditions() {
        CheckedProgram program = check("""
                class Accumulator {
                    var total: Int = 0
                    func addUpTo(limit: Int): Int {
                        for (var i = 0; i < limit; i = i + 1) {
                            this.total = this.total + i
                        }
                        return this.total
                    }
                }
                """);
        ClassSymbol accumulator = program.classSymbol("Accumulator").orElseThrow();
        assertEquals(IntType.INSTANCE, accumulator.method("addUpTo").orElseThrow().returnType());
    }

    @Test
    public void programWithoutMainStillChecksClasses() {
        CheckedProgram program = check("""
                class Point {
                    val x: Int
                    Point(x: Int) {
                        this.x = x
                    }
                }
                """);
        assertTrue(program.entryPoint().isEmpty());
        assertTrue(program.classSymbol("Point").isPresent());
    }

    @Test
    public void classTypedValuesAreAssignableToObjectAndAny() {
        check("""
                class Marker {
                    val id: Int = 1
                }
                func asObject(m: Marker): Object {
                    return m
                }
                func asAny(m: Marker): Any {
                    return m
                }
                """);
    }

    @Test
    public void mutablePropertyAssignmentInsideMethodsIsTyped() {
        CheckedProgram program = check("""
                class Cell {
                    var value: Int
                    Cell(value: Int) {
                        this.value = value
                    }
                    func set(next: Int): Unit {
                        this.value = next
                    }
                }
                """);
        ClassSymbol cell = program.classSymbol("Cell").orElseThrow();
        FunctionDeclNode set = cell.declaration().methods().get(0);
        AssignStmtNode assignment = (AssignStmtNode) set.body().statements().get(0);
        assertEquals(cell.property("value").orElseThrow(), program.propertyOf((MemberAccessExprNode) assignment.target()).orElseThrow());
    }

    @Test
    public void expressionStatementCanBeAMethodCall() {
        CheckedProgram program = check("""
                class Logger {
                    func log(): Unit {
                        println("hi")
                    }
                }
                func run(): Unit {
                    Logger().log()
                }
                """);
        FunctionDeclNode run = function(program, 1);
        ExprStmtNode statement = (ExprStmtNode) run.body().statements().get(0);
        CallExprNode call = (CallExprNode) statement.expression();
        assertEquals(program.classSymbol("Logger").orElseThrow().method("log").orElseThrow(), program.methodOf(call).orElseThrow().method());
    }
}
