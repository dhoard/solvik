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
import static org.solvik.test.SolvikTestSupport.expectThrows;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.source.SourceFile;

/**
 * Structural guarantees required by docs/ARCHITECTURE.md: syntax nodes are immutable, Truffle-free,
 * and expose no analysis or execution slots. Executable Truffle nodes are a separate representation
 * that Phase 1 must not reach.
 */
public final class SolvikAstStructureTest {

    private static CompilationUnitNode parse(String text) {
        var result = org.solvik.parser.SolvikParser.parse(new SourceFile("structure.sol", text));
        assertThat(result.isSuccess()).as("parse must succeed: " + result.diagnostics().all()).isTrue();
        return result.requireAst();
    }

    /** Every AST field must be final so parsed trees cannot mutate after construction. */
    @Test
    public void everyAstFieldIsFinal() throws Exception {
        String sourceRoot = findSourceRoot();
        List<String> violations = new ArrayList<>();
        for (Path p : solvikSources(sourceRoot, "/ast/")) {
            String text = Files.readString(p);
            // Reject non-final instance fields declared in this file.
            for (String line : text.split("\n")) {
                String t = line.trim();
                if (!t.endsWith(";") || t.startsWith("//") || t.startsWith("*") || t.startsWith("@")) {
                    continue;
                }
                boolean declarationLike = t.matches("^private\\s+.*;$") || t.matches("^protected\\s+.*;$");
                if (!declarationLike) {
                    continue;
                }
                // Skip constants, statics, and method declarations.
                if (t.contains("static ") || t.contains("(")) {
                    continue;
                }
                if (!t.contains(" final ")) {
                    violations.add(p.getFileName() + ": " + t);
                }
            }
        }
        assertThat(violations).as("AST fields must be final: " + violations).isEqualTo(List.of());
    }

    /** Concrete AST classes must be final so lowering cannot subclass them with mutable state. */
    @Test
    public void concreteNodeClassesAreFinal() throws Exception {
        String sourceRoot = findSourceRoot();
        List<String> violations = new ArrayList<>();
        for (Path p : solvikSources(sourceRoot, "/ast/")) {
            String text = Files.readString(p);
            for (String line : text.split("\n")) {
                String t = line.trim();
                if (t.startsWith("public class ")) {
                    violations.add(p.getFileName() + ": " + t);
                }
            }
        }
        assertThat(violations).as("concrete node classes must be final: " + violations).isEqualTo(List.of());
    }

    /**
     * Front-end isolation: no file under {@code org/solvik} may import Truffle or Graal APIs. The
     * Phase 1 front end must run entirely without the execution engine.
     */
    @Test
    public void frontEndDoesNotDependOnTruffle() throws Exception {
        String sourceRoot = findSourceRoot();
        List<String> violations = new ArrayList<>();
        for (Path p : solvikSources(sourceRoot, "")) {
            String text = Files.readString(p);
            for (String line : text.split("\n")) {
                String t = line.trim();
                if (t.startsWith("import com.oracle.truffle") || t.startsWith("import org.graalvm.polyglot") || t.startsWith("import com.oracle.graal")) {
                    violations.add(p + ": " + t);
                }
            }
        }
        assertThat(violations).as("org.solvik sources must stay engine-independent").isEqualTo(List.of());
    }

    /** No AST type may extend or reference a Truffle executable-node base class. */
    @Test
    public void astTypesDoNotExtendExecutableNodes() throws Exception {
        String sourceRoot = findSourceRoot();
        List<String> violations = new ArrayList<>();
        for (Path p : solvikSources(sourceRoot, "/ast/")) {
            String text = Files.readString(p);
            for (String line : text.split("\n")) {
                String t = line.trim();
                if (t.contains("extends Node") || t.contains("DirectCallNode") || t.contains("CallTarget")) {
                    violations.add(p.getFileName() + ": " + t);
                }
            }
        }
        assertThat(violations).isEqualTo(List.of());
    }

    /** AST nodes expose only accessors: no setters, mutators, or child insertion points. */
    @Test
    public void astExposesNoMutatorMethods() throws Exception {
        String sourceRoot = findSourceRoot();
        List<String> violations = new ArrayList<>();
        for (Path p : solvikSources(sourceRoot, "/ast/")) {
            String text = Files.readString(p);
            for (String line : text.split("\n")) {
                String t = line.trim();
                if (t.matches("public\\s+\\w[\\w<>, .?]*\\s+(set[A-Z]\\w*|add[A-Z]\\w*|clear|remove[A-Z]?\\w*)\\s*\\(.*")) {
                    violations.add(p.getFileName() + ": " + t);
                }
            }
        }
        assertThat(violations).isEqualTo(List.of());
    }

    /** Reflection fallback: reachable node classes must not declare setter methods. */
    @Test
    public void reachableNodeClassesHaveNoSetters() {
        Set<Class<?>> seen = new HashSet<>();
        Deque<AstNode> stack = new ArrayDeque<>();
        stack.push(parse("""
                func f(a: Int): Int {
                    val v: Int = (a + obj.g(1)) * 2;
                    var w: Int = h(v, obj.field);
                    if (true) {
                        obj.store(1);
                        return v;
                    } else {
                        return w;
                    }
                }
                """));
        while (!stack.isEmpty()) {
            AstNode n = stack.pop();
            seen.add(n.getClass());
            stack.addAll(n.children());
        }
        assertThat(seen.size() >= 12).isTrue();
        for (Class<?> c : seen) {
            for (Method m : c.getMethods()) {
                assertThat(m.getName().startsWith("set")).as(c.getSimpleName() + "." + m.getName() + " is a mutator").isFalse();
            }
        }
    }

    /** Required node families from the Phase 1 prompt must all exist and be produced. */
    @Test
    public void requiredNodeFamiliesAreProduced() {
        Set<AstKind> kinds = new HashSet<>();
        Deque<AstNode> stack = new ArrayDeque<>();
        stack.push(parse("""
                func f(a: Int): Int {
                    val v: Int = (a + obj.g(1)) * 2;
                    var w: Int = true;
                    if (true) {
                        obj.store(1);
                        return v;
                    } else if (false) {
                        plain();
                        return a;
                    } else {
                        return v;
                    }
                }
                """));
        while (!stack.isEmpty()) {
            AstNode n = stack.pop();
            kinds.add(n.kind());
            stack.addAll(n.children());
        }
        assertThat(kinds.containsAll(List.of(//
                AstKind.COMPILATION_UNIT, //
                AstKind.FUNCTION_DECL, //
                AstKind.PARAMETER, //
                AstKind.TYPE_REF, //
                AstKind.BLOCK, //
                AstKind.LOCAL_DECL, //
                AstKind.IF_STMT, //
                AstKind.ELSE_BRANCH, //
                AstKind.RETURN_STMT, //
                AstKind.EXPR_STMT, //
                AstKind.BINARY_EXPR, //
                AstKind.CALL_EXPR, //
                AstKind.MEMBER_ACCESS_EXPR, //
                AstKind.PAREN_EXPR, //
                AstKind.NAME_REF_EXPR, //
                AstKind.INT_LITERAL, //
                AstKind.BOOL_LITERAL))).isTrue();
    }

    /** The Phase 4 control-flow and assignment nodes must all be reachable from a parsed program. */
    @Test
    public void phaseFourNodeFamiliesAreProduced() {
        Set<AstKind> kinds = new HashSet<>();
        Deque<AstNode> stack = new ArrayDeque<>();
        stack.push(parse("""
                func f(n: Int): Int {
                    var total = 0
                    for (var i = 0; i < n; i = i + 1) {
                        total = total + i
                        if (total > 100) {
                            break
                        }
                        continue
                    }
                    while (total > 0) {
                        total = total - 1
                    }
                    val check = !false && (total == 0 || total <= n)
                    return total
                }
                """));
        while (!stack.isEmpty()) {
            AstNode n = stack.pop();
            kinds.add(n.kind());
            stack.addAll(n.children());
        }
        assertThat(kinds.containsAll(List.of(//
                AstKind.ASSIGN_STMT, //
                AstKind.WHILE_STMT, //
                AstKind.FOR_STMT, //
                AstKind.BREAK_STMT, //
                AstKind.CONTINUE_STMT, //
                AstKind.UNARY_EXPR))).isTrue();
    }

    /** The Phase 6 class, property, constructor, and this nodes must all be reachable from a program. */
    @Test
    public void phaseSixNodeFamiliesAreProduced() {
        Set<AstKind> kinds = new HashSet<>();
        Deque<AstNode> stack = new ArrayDeque<>();
        stack.push(parse("""
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
                """));
        while (!stack.isEmpty()) {
            AstNode n = stack.pop();
            kinds.add(n.kind());
            stack.addAll(n.children());
        }
        assertThat(kinds.containsAll(List.of(//
                AstKind.CLASS_DECL, //
                AstKind.PROPERTY_DECL, //
                AstKind.CONSTRUCTOR_DECL, //
                AstKind.THIS_EXPR))).isTrue();
    }

    @Test
    public void phaseSevenNodeFamiliesAreProduced() {
        Set<AstKind> kinds = new HashSet<>();
        Deque<AstNode> stack = new ArrayDeque<>();
        stack.push(parse("""
                open class Animal {
                    open func speak(): String {
                        return "..."
                    }
                }
                class Dog extends Animal {
                    override func speak(): String {
                        return super.speak()
                    }
                }
                func literals(): Unit {
                    val l = 1L
                    val f = 1.5f
                    val d = 1.5
                    val c = 'A'
                }
                """));
        while (!stack.isEmpty()) {
            AstNode node = stack.pop();
            kinds.add(node.kind());
            stack.addAll(node.children());
        }
        assertThat(kinds.containsAll(List.of(//
                AstKind.CLASS_DECL, //
                AstKind.SUPER_EXPR, //
                AstKind.LONG_LITERAL, //
                AstKind.FLOATING_LITERAL, //
                AstKind.CHAR_LITERAL))).isTrue();
    }

    @Test
    public void phaseEightNodeFamiliesAreProduced() {
        Set<AstKind> kinds = new HashSet<>();
        Deque<AstNode> stack = new ArrayDeque<>();
        stack.push(parse("""
                interface Named {
                    func name(): String

                    func greeting(): String {
                        return "Hello " .. name()
                    }
                }
                class User implements Named {
                    val label: String

                    User(label: String) {
                        this.label = label
                    }

                    func name(): String {
                        return this.label
                    }
                }
                """));
        while (!stack.isEmpty()) {
            AstNode node = stack.pop();
            kinds.add(node.kind());
            stack.addAll(node.children());
        }
        assertThat(kinds.containsAll(List.of(//
                AstKind.INTERFACE_DECL, //
                AstKind.SIGNATURE_DECL, //
                AstKind.FUNCTION_DECL, //
                AstKind.CLASS_DECL))).isTrue();
    }

    /** A delegate is a distinct declaration node whose declared type is its child. */
    @Test
    public void phaseNineNodeFamiliesAreProduced() {
        Set<AstKind> kinds = new HashSet<>();
        Deque<AstNode> stack = new ArrayDeque<>();
        stack.push(parse("""
                interface Named {
                    func name(): String
                }
                class Service implements Named {
                    delegate val named: Named

                    Service(named: Named) {
                        this.named = named
                    }
                }
                """));
        while (!stack.isEmpty()) {
            AstNode node = stack.pop();
            kinds.add(node.kind());
            stack.addAll(node.children());
        }
        assertThat(kinds.containsAll(List.of(//
                AstKind.DELEGATE_DECL, //
                AstKind.INTERFACE_DECL, //
                AstKind.CLASS_DECL))).isTrue();
    }

    /** An interface abstract signature is a distinct node with no body child. */
    @Test
    public void phaseTenNodeFamiliesAreProduced() {
        Set<AstKind> kinds = new HashSet<>();
        Deque<AstNode> stack = new ArrayDeque<>();
        stack.push(parse("""
                class Box {
                    val value: Int

                    Box(value: Int) {
                        this.value = value
                    }
                }
                func f(box: Box?, v: Any): Int? {
                    val missing = null
                    val safe = box?.value
                    val fallback = safe ?? 0
                    val tested = v is Box
                    val cast = v as Box
                    if (box != null) {
                        return box.value
                    }
                    return fallback
                }
                """));
        while (!stack.isEmpty()) {
            AstNode node = stack.pop();
            kinds.add(node.kind());
            stack.addAll(node.children());
        }
        assertThat(kinds.containsAll(List.of(//
                AstKind.NULL_LITERAL, //
                AstKind.TYPE_TEST_EXPR, //
                AstKind.CAST_EXPR, //
                AstKind.BINARY_EXPR, //
                AstKind.MEMBER_ACCESS_EXPR))).isTrue();
    }

    /** Generic declarations and type applications produce type-parameter nodes. */
    @Test
    public void phaseElevenNodeFamiliesAreProduced() {
        Set<AstKind> kinds = new HashSet<>();
        Deque<AstNode> stack = new ArrayDeque<>();
        stack.push(parse("""
                class Box<T> {
                    var value: T
                }
                interface Container<U> {
                    func get(): U
                }
                func identity<V>(x: V): V {
                    return x
                }
                func f(xs: List<String>): Box<Int> {
                    return Box(1)
                }
                """));
        while (!stack.isEmpty()) {
            AstNode node = stack.pop();
            kinds.add(node.kind());
            stack.addAll(node.children());
        }
        assertThat(kinds.containsAll(List.of(//
                AstKind.TYPE_PARAMETER, //
                AstKind.TYPE_REF, //
                AstKind.CLASS_DECL, //
                AstKind.INTERFACE_DECL, //
                AstKind.FUNCTION_DECL))).isTrue();
    }

    /** Enum declarations and their variants, plus the sealed modifier, produce their own nodes. */
    @Test
    public void phaseTwelveNodeFamiliesAreProduced() {
        Set<AstKind> kinds = new HashSet<>();
        Deque<AstNode> stack = new ArrayDeque<>();
        stack.push(parse("""
                sealed class Shape {
                }
                class Circle extends Shape {
                }
                enum Result<T> {
                    Ok(T)
                    Error
                }
                """));
        while (!stack.isEmpty()) {
            AstNode node = stack.pop();
            kinds.add(node.kind());
            stack.addAll(node.children());
        }
        assertThat(kinds.containsAll(List.of(//
                AstKind.ENUM_DECL, //
                AstKind.ENUM_VARIANT, //
                AstKind.CLASS_DECL))).isTrue();
    }

    /** Match expressions, their branches, and every initial pattern form produce their own nodes. */
    @Test
    public void phaseThirteenNodeFamiliesAreProduced() {
        Set<AstKind> kinds = new HashSet<>();
        Deque<AstNode> stack = new ArrayDeque<>();
        stack.push(parse("""
                enum Result {
                    Ok(Int)
                    Error(String)
                }
                sealed class Shape {
                }
                class Circle extends Shape {
                }
                func describe(result: Result, shape: Shape): String {
                    return match result {
                        Ok(value) => "ok"
                        Error(error) => match shape {
                            circle: Circle => "circle"
                            _ => "other"
                        }
                    }
                }
                """));
        while (!stack.isEmpty()) {
            AstNode node = stack.pop();
            kinds.add(node.kind());
            stack.addAll(node.children());
        }
        assertThat(kinds.containsAll(List.of(//
                AstKind.MATCH_EXPR, //
                AstKind.MATCH_BRANCH, //
                AstKind.WILDCARD_PATTERN, //
                AstKind.ENUM_PATTERN, //
                AstKind.BINDING_PATTERN))).isTrue();
    }

    @Test
    public void phaseFifteenNodeFamiliesAreProduced() {
        Set<AstKind> kinds = new HashSet<>();
        Deque<AstNode> stack = new ArrayDeque<>();
        stack.push(parse("""
                func classify(value: Int): Unit {
                    switch (value) {
                        case 1, 2:
                            println("small")
                        case 3:
                            println("three")
                        default:
                            println("other")
                    }
                }
                func matchText(input: String): Unit {
                    switch (input) {
                        case regex r#"^\\d+$"#:
                            println("number")
                        default:
                            println("text")
                    }
                }
                """));
        while (!stack.isEmpty()) {
            AstNode node = stack.pop();
            kinds.add(node.kind());
            stack.addAll(node.children());
        }
        assertThat(kinds.containsAll(List.of(//
                AstKind.SWITCH_STMT, //
                AstKind.SWITCH_CASE, //
                AstKind.CASE_LABEL, //
                AstKind.REGEX_CASE_LABEL))).isTrue();
    }

    /** An interface abstract signature is a distinct node with no body child. */
    @Test
    public void interfaceSignatureHasNoBodyChild() {
        CompilationUnitNode unit = parse("interface I {\n    func f(): Int\n}\n");
        var declaration = (org.solvik.ast.declaration.InterfaceDeclNode) unit.declarations().get(0);
        var signature = declaration.signatures().get(0);
        assertThat(signature.kind()).isEqualTo(AstKind.SIGNATURE_DECL);
        assertThat(signature.hasBody()).isFalse();
        assertThat(signature.children().stream().map(AstNode::kind).toList()).isEqualTo(List.of(AstKind.TYPE_REF));
        assertThat(declaration.kind()).isEqualTo(AstKind.INTERFACE_DECL);
    }

    @Test
    public void constructorsRejectMissingArguments() throws Exception {
        var ctor = CompilationUnitNode.class.getConstructor(List.class, org.solvik.source.SourceSpan.class);
        var e1 = expectThrows(java.lang.reflect.InvocationTargetException.class, () -> ctor.newInstance(null, org.solvik.source.SourceSpan.of(0, 0)));
        assertThat(e1.getCause() instanceof NullPointerException).isTrue();
        var e2 = expectThrows(java.lang.reflect.InvocationTargetException.class, () -> ctor.newInstance(List.of(), null));
        assertThat(e2.getCause() instanceof NullPointerException).isTrue();
    }

    /** The parser entry point is a utility class; instances are not part of its contract. */
    @Test
    public void parserEntryPointIsUtility() throws Exception {
        Constructor<?>[] ctors = org.solvik.parser.SolvikParser.class.getDeclaredConstructors();
        assertThat(ctors.length).isEqualTo(1);
        assertThat(Modifier.isPrivate(ctors[0].getModifiers())).isTrue();
        assertThat(Modifier.isFinal(org.solvik.parser.SolvikParser.class.getModifiers())).isTrue();
    }

    /** Nothing in the front end may reach an execution construct by name. */
    @Test
    public void frontEndNeverNamesExecutionApi() throws Exception {
        String sourceRoot = findSourceRoot();
        List<String> forbidden = List.of("CallTarget", "DirectCallNode", "InteropLibrary", "TruffleLanguage", "@Specialization", "DynamicObject");
        List<String> violations = new ArrayList<>();
        for (Path p : solvikSources(sourceRoot, "")) {
            String text = Files.readString(p);
            for (String needle : forbidden) {
                if (text.contains(needle)) {
                    violations.add(p + " mentions " + needle);
                }
            }
        }
        assertThat(violations).isEqualTo(List.of());
    }

    private static String findSourceRoot() {
        for (String candidate : new String[]{"src/main/java", "language/src/main/java"}) {
            if (Files.isDirectory(Path.of(candidate, "org/solvik"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("cannot locate org/solvik sources from " + Path.of("").toAbsolutePath());
    }

    private static List<Path> solvikSources(String sourceRoot, String pathFragment) throws Exception {
        Path root = Path.of(sourceRoot, "org/solvik");
        try (var stream = Files.walk(root)) {
            List<Path> all = new ArrayList<>();
            for (Path p : stream.filter(f -> f.toString().endsWith(".java")).toList()) {
                String normalized = p.toString().replace('\\', '/');
                int idx = normalized.indexOf("org/solvik");
                String relative = idx >= 0 ? normalized.substring(idx) : normalized;
                // The execution backend (org/solvik/truffle) and its lowering adapter are allowed
                // to depend on Truffle; the engine-independent front end is everything else.
                if (relative.contains("/truffle/") || relative.contains("/lowering/")) {
                    continue;
                }
                if (pathFragment.isEmpty() || relative.contains(pathFragment)) {
                    all.add(p);
                }
            }
            return all;
        }
    }
}
