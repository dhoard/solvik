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
import org.solvik.ast.declaration.ClassDeclNode;
import org.solvik.ast.declaration.ConstructorDeclNode;
import org.solvik.ast.declaration.FunctionDeclNode;
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
        assertThat(result.isSuccess()).as("analysis must succeed: " + result.diagnostics().all()).isTrue();
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
        assertThat(user.type() instanceof ClassType).isTrue();
        assertThat(user.name()).isEqualTo("User");
        assertThat(program.classOf(classDeclaration(program, 0)).orElseThrow()).isEqualTo(user);

        assertThat(user.properties().size()).isEqualTo(2);
        PropertySymbol id = user.property("id").orElseThrow();
        assertThat(id.type()).isEqualTo(IntType.INSTANCE);
        assertThat(id.isMutable()).isFalse();
        assertThat(id.hasInitializer()).isFalse();
        assertThat(id.index()).isEqualTo(0);

        PropertySymbol name = user.property("name").orElseThrow();
        assertThat(name.type()).isEqualTo(StringType.INSTANCE);
        assertThat(name.isMutable()).isTrue();
        assertThat(name.index()).isEqualTo(1);

        assertThat(user.methods().size()).isEqualTo(1);
        assertThat(user.method("describe").orElseThrow().isMethod()).isTrue();
        assertThat(user.hasExplicitInit()).isTrue();
        assertThat(user.constructor().orElseThrow().parameters().size()).isEqualTo(2);
        assertThat(user.constructor().orElseThrow().owner().name()).isEqualTo("User");
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
        assertThat(program.constructorOf(construction).orElseThrow()).isEqualTo(user);
        assertThat(program.typeOf(construction).orElseThrow()).isEqualTo(user.type());
        assertThat(program.typeOf(construction.callee()).orElseThrow()).isEqualTo(user.type());
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
        assertThat(program.propertyOf(read).orElseThrow()).isEqualTo(name);
        assertThat(program.typeOf(read).orElseThrow()).isEqualTo(StringType.INSTANCE);

        AssignStmtNode write = (AssignStmtNode) use.body().statements().get(1);
        MemberAccessExprNode target = (MemberAccessExprNode) write.target();
        assertThat(program.propertyOf(target).orElseThrow()).isEqualTo(name);
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
        assertThat(implicitResolved.method()).isEqualTo(greeter.method("label").orElseThrow());
        assertThat(implicitResolved.isImplicitThis()).isTrue();
        assertThat(program.typeOf(implicit).orElseThrow()).isEqualTo(StringType.INSTANCE);

        FunctionDeclNode use = function(program, 1);
        CallExprNode explicit = (CallExprNode) ((ReturnStmtNode) use.body().statements().get(0)).value().orElseThrow();
        ResolvedMethod explicitResolved = program.methodOf(explicit).orElseThrow();
        assertThat(explicitResolved.isImplicitThis()).isFalse();
        MemberAccessExprNode callee = (MemberAccessExprNode) explicit.callee();
        assertThat(program.typeOf(callee).orElseThrow() instanceof FunctionType).isTrue();
        assertThat(program.typeOf(explicit).orElseThrow()).isEqualTo(StringType.INSTANCE);
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
        assertThat(program.typeOf(thisExpression).orElseThrow()).isEqualTo(holder.type());
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
        assertThat(counter.property("count").orElseThrow().hasInitializer()).isTrue();
        assertThat(counter.property("label").orElseThrow().hasInitializer()).isTrue();
        // A class with no init builds only when every property has a declaration initializer.
        assertThat(counter.hasExplicitInit()).isFalse();
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
        assertThat(accumulator.method("addUpTo").orElseThrow().returnType()).isEqualTo(IntType.INSTANCE);
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
        assertThat(program.entryPoint().isEmpty()).isTrue();
        assertThat(program.classSymbol("Point").isPresent()).isTrue();
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
        assertThat(program.propertyOf((MemberAccessExprNode) assignment.target()).orElseThrow()).isEqualTo(cell.property("value").orElseThrow());
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
        assertThat(program.methodOf(call).orElseThrow().method()).isEqualTo(program.classSymbol("Logger").orElseThrow().method("log").orElseThrow());
    }
}
