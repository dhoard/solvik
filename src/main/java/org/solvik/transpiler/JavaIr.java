package org.solvik.transpiler;

import java.util.List;

/**
 * Typed Java expression IR.
 *
 * <p>Type checking and lowering decide the node and its resolved type; the Java
 * emitter only renders Java text from it (see {@link #render}). This keeps the
 * type-driven code-generation decisions in one place instead of being
 * re-derived from the AST while writing strings.</p>
 *
 * <p>The backend boundary prefers structured nodes over opaque {@link #Atom}
 * strings so that later stages (notably {@link JavaIrOptimizer}) can inspect and
 * rewrite expressions without decoding Java source text.</p>
 */
public sealed interface JavaIr {
    TypeModel.Type type();

    /** A leaf whose Java spelling is already complete (locals, fields, literals, calls). */
    record Atom(String java, TypeModel.Type type) implements JavaIr {}

    /** {@code left operator right} with no surrounding parentheses. */
    record Infix(String operator, JavaIr left, JavaIr right, TypeModel.Type type) implements JavaIr {}

    /** {@code operator operand} with no surrounding parentheses. */
    record Prefix(String operator, JavaIr operand, TypeModel.Type type) implements JavaIr {}

    record Paren(JavaIr value, TypeModel.Type type) implements JavaIr {}

    /** A static or instance method invocation: {@code func(args)} or {@code recv.name(args)}. */
    record Call(String function, List<JavaIr> arguments, TypeModel.Type type) implements JavaIr {}

    record MethodCall(JavaIr receiver, String name, List<JavaIr> arguments, TypeModel.Type type) implements JavaIr {}

    record Ternary(JavaIr condition, JavaIr whenTrue, JavaIr whenFalse, TypeModel.Type type) implements JavaIr {}

    /** {@code (javaType)(value)}. */
    record Cast(String javaType, JavaIr value, TypeModel.Type type) implements JavaIr {}

    /** A {@code new Type(args)} construction. */
    record New(String typeName, List<JavaIr> arguments, TypeModel.Type type) implements JavaIr {}

    /** A static field read such as {@code Integer.MAX_VALUE}. */
    record StaticField(String owner, String field, TypeModel.Type type) implements JavaIr {}

    /** An instance field access such as {@code this.value}. */
    record FieldAccess(JavaIr receiver, String field, TypeModel.Type type) implements JavaIr {}

    /** A direct JDK static method call whose receiver is not a value expression, e.g. {@code Math.addExact(...)}. */
    record StaticMethodCall(String owner, String method, List<JavaIr> arguments, TypeModel.Type type) implements JavaIr {}

    static JavaIr atom(String java, TypeModel.Type type) { return new Atom(java, type); }
    static JavaIr infix(String operator, JavaIr left, JavaIr right, TypeModel.Type type) { return new Infix(operator, left, right, type); }
    static JavaIr prefix(String operator, JavaIr operand, TypeModel.Type type) { return new Prefix(operator, operand, type); }
    static JavaIr paren(JavaIr value, TypeModel.Type type) { return new Paren(value, type); }
    static JavaIr call(String function, List<JavaIr> arguments, TypeModel.Type type) { return new Call(function, arguments, type); }
    static JavaIr method(JavaIr receiver, String name, List<JavaIr> arguments, TypeModel.Type type) { return new MethodCall(receiver, name, arguments, type); }
    static JavaIr ternary(JavaIr condition, JavaIr whenTrue, JavaIr whenFalse, TypeModel.Type type) { return new Ternary(condition, whenTrue, whenFalse, type); }
    static JavaIr cast(String javaType, JavaIr value, TypeModel.Type type) { return new Cast(javaType, value, type); }
    static JavaIr newExpression(String typeName, List<JavaIr> arguments, TypeModel.Type type) { return new New(typeName, arguments, type); }
    static JavaIr staticField(String owner, String field, TypeModel.Type type) { return new StaticField(owner, field, type); }
    static JavaIr fieldAccess(JavaIr receiver, String field, TypeModel.Type type) { return new FieldAccess(receiver, field, type); }
    static JavaIr staticMethod(String owner, String method, List<JavaIr> arguments, TypeModel.Type type) { return new StaticMethodCall(owner, method, arguments, type); }

    static String render(JavaIr expression) {
        if (expression instanceof Atom a) return a.java();
        if (expression instanceof Paren p) return "(" + render(p.value()) + ")";
        if (expression instanceof Infix i) return render(i.left()) + " " + i.operator() + " " + render(i.right());
        if (expression instanceof Prefix p) return p.operator() + render(p.operand());
        if (expression instanceof Call c) return c.function() + "(" + renderAll(c.arguments()) + ")";
        if (expression instanceof MethodCall m) return render(m.receiver()) + "." + m.name() + "(" + renderAll(m.arguments()) + ")";
        if (expression instanceof Ternary t) return render(t.condition()) + " ? " + render(t.whenTrue()) + " : " + render(t.whenFalse());
        if (expression instanceof Cast c) return "(" + c.javaType() + ")(" + render(c.value()) + ")";
        if (expression instanceof New n) return "new " + n.typeName() + "(" + renderAll(n.arguments()) + ")";
        if (expression instanceof StaticField f) return f.owner() + "." + f.field();
        if (expression instanceof FieldAccess fa) return render(fa.receiver()) + "." + fa.field();
        if (expression instanceof StaticMethodCall sm) return sm.owner() + "." + sm.method() + "(" + renderAll(sm.arguments()) + ")";
        throw new IllegalStateException("unknown JavaIr node: " + expression);
    }

    private static String renderAll(List<JavaIr> expressions) {
        StringBuilder builder = new StringBuilder();
        for (JavaIr expression : expressions) {
            if (builder.length() > 0) builder.append(", ");
            builder.append(render(expression));
        }
        return builder.toString();
    }
}
