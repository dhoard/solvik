package org.solvik.transpiler.backend;

import java.util.List;

import org.solvik.transpiler.InternalCompilerException;

import org.solvik.transpiler.TypeModel;

/**
 * Java-specific expression IR: a structural description of a Java 17
 * expression plus the resolved Solvik type it produces.
 *
 * <p>The lowerer chooses the node (and therefore the Java representation);
 * the emitter only renders it. The nodes mirror real Java expression forms so
 * that no phase has to hide generated Java inside an opaque string. Rendering
 * is precedence-aware: {@link #render} inserts parentheses whenever a child
 * would otherwise rebind.</p>
 */
public sealed interface JavaIr {
    TypeModel.Type type();

    /** A bare Java name such as a local variable or a type variable. */
    record Identifier(String name, TypeModel.Type type) implements JavaIr {}

    /** The implicit/instance receiver {@code this}. */
    record This(TypeModel.Type type) implements JavaIr {}

    /** A complete Java literal (including {@code null}). */
    record Literal(String java, TypeModel.Type type) implements JavaIr {}

    /** {@code receiver.field}. */
    record FieldAccess(JavaIr receiver, String field, TypeModel.Type type) implements JavaIr {}

    /** {@code owner.field}, e.g. {@code Integer.MAX_VALUE}. */
    record StaticField(String owner, String field, TypeModel.Type type) implements JavaIr {}

    /** {@code new Type[<typeArguments>](args)}. */
    record New(String typeName, String typeArguments, List<JavaIr> arguments, TypeModel.Type type) implements JavaIr {}

    /** A prefix operator application, e.g. {@code -x} or {@code !x}. */
    record Unary(String operator, JavaIr operand, TypeModel.Type type) implements JavaIr {}

    /** A binary operator application, e.g. {@code a + b}. */
    record Binary(String operator, JavaIr left, JavaIr right, TypeModel.Type type) implements JavaIr {}

    /** {@code condition ? whenTrue : whenFalse}. */
    record Conditional(JavaIr condition, JavaIr whenTrue, JavaIr whenFalse, TypeModel.Type type) implements JavaIr {}

    /** {@code (javaType)(value)}. */
    record Cast(String javaType, JavaIr value, TypeModel.Type type) implements JavaIr {}

    /** {@code value instanceof javaType}. */
    record InstanceOf(JavaIr value, String javaType, TypeModel.Type type) implements JavaIr {}

    /** A call whose target is a bare function name, e.g. {@code Objects.equals(...)}. */
    record Call(String function, String typeArguments, List<JavaIr> arguments, TypeModel.Type type) implements JavaIr {}

    /** {@code receiver.<typeArguments>name(args)}. */
    record MethodCall(JavaIr receiver, String name, String typeArguments, List<JavaIr> arguments, TypeModel.Type type) implements JavaIr {}

    /** {@code owner.<typeArguments>method(args)}. */
    record StaticCall(String owner, String typeArguments, String method, List<JavaIr> arguments, TypeModel.Type type) implements JavaIr {}

    /** {@code target = value}. */
    record Assignment(JavaIr target, JavaIr value, TypeModel.Type type) implements JavaIr {}

    /** An explicit grouping pair supplied by the lowerer. */
    record Paren(JavaIr value, TypeModel.Type type) implements JavaIr {}

    /**
     * A pre-rendered Java fragment for forms the structural nodes do not model
     * (currently only a binding value-context match, which Java can express only
     * as a statement-bearing lambda). This is a deliberately narrow escape hatch
     * and is not used for ordinary expressions.
     */
    record Inline(String java, TypeModel.Type type) implements JavaIr {}

    /** Convenience factories used by tests and by callers that build IR directly. */
    static JavaIr identifier(String name, TypeModel.Type type) { return new Identifier(name, type); }
    static JavaIr literal(String java, TypeModel.Type type) { return new Literal(java, type); }
    static JavaIr paren(JavaIr value, TypeModel.Type type) { return new Paren(value, type); }
    static JavaIr infix(String operator, JavaIr left, JavaIr right, TypeModel.Type type) { return new Binary(operator, left, right, type); }
    static JavaIr prefix(String operator, JavaIr operand, TypeModel.Type type) { return new Unary(operator, operand, type); }
    static JavaIr cast(String javaType, JavaIr value, TypeModel.Type type) { return new Cast(javaType, value, type); }
    static JavaIr call(String function, List<JavaIr> arguments, TypeModel.Type type) { return new Call(function, "", arguments, type); }
    static JavaIr method(JavaIr receiver, String name, List<JavaIr> arguments, TypeModel.Type type) { return new MethodCall(receiver, name, "", arguments, type); }
    static JavaIr staticMethod(String owner, String method, List<JavaIr> arguments, TypeModel.Type type) { return new StaticCall(owner, "", method, arguments, type); }
    static JavaIr staticField(String owner, String field, TypeModel.Type type) { return new StaticField(owner, field, type); }
    static JavaIr fieldAccess(JavaIr receiver, String field, TypeModel.Type type) { return new FieldAccess(receiver, field, type); }
    static JavaIr newExpression(String typeName, List<JavaIr> arguments, TypeModel.Type type) { return new New(typeName, "", arguments, type); }
    static JavaIr ternary(JavaIr condition, JavaIr whenTrue, JavaIr whenFalse, TypeModel.Type type) { return new Conditional(condition, whenTrue, whenFalse, type); }

    /** Renders a complete Java expression with minimal necessary parentheses. */
    static String render(JavaIr expression) {
        return render(expression, 0);
    }

    private static String render(JavaIr expression, int parentPrecedence) {
        String text = renderRaw(expression);
        return precedence(expression) < parentPrecedence ? "(" + text + ")" : text;
    }

    private static String renderRaw(JavaIr expression) {
        if (expression instanceof Identifier i) return i.name();
        if (expression instanceof This) return "this";
        if (expression instanceof Literal l) return l.java();
        if (expression instanceof FieldAccess f) return render(f.receiver(), PREC_POSTFIX) + "." + f.field();
        if (expression instanceof StaticField f) return f.owner() + "." + f.field();
        if (expression instanceof New n) return "new " + n.typeName() + n.typeArguments() + "(" + renderAll(n.arguments()) + ")";
        if (expression instanceof Unary u) return u.operator() + render(u.operand(), PREC_UNARY);
        if (expression instanceof Binary b) {
            // Java binary operators are left-associative: the right operand needs
            // parentheses at equal precedence, the left operand does not.
            int operatorPrecedence = precedence(expression);
            return render(b.left(), operatorPrecedence) + " " + b.operator() + " " + render(b.right(), operatorPrecedence + 1);
        }
        if (expression instanceof Conditional c) {
            return render(c.condition(), PREC_CONDITIONAL + 1) + " ? "
                    + render(c.whenTrue(), PREC_CONDITIONAL + 1) + " : "
                    + render(c.whenFalse(), PREC_CONDITIONAL);
        }
        if (expression instanceof Cast c) return "(" + c.javaType() + ")(" + render(c.value(), PREC_UNARY) + ")";
        if (expression instanceof InstanceOf i) return render(i.value(), PREC_RELATIONAL) + " instanceof " + i.javaType();
        if (expression instanceof Call c) return c.function() + c.typeArguments() + "(" + renderAll(c.arguments()) + ")";
        if (expression instanceof MethodCall m) return render(m.receiver(), PREC_POSTFIX) + "." + m.typeArguments() + m.name() + "(" + renderAll(m.arguments()) + ")";
        if (expression instanceof StaticCall s) return s.owner() + "." + s.typeArguments() + s.method() + "(" + renderAll(s.arguments()) + ")";
        if (expression instanceof Assignment a) return render(a.target(), PREC_ASSIGNMENT) + " = " + render(a.value(), PREC_ASSIGNMENT);
        if (expression instanceof Paren p) return "(" + render(p.value()) + ")";
        if (expression instanceof Inline i) return i.java();
        throw new InternalCompilerException("unknown JavaIr node: " + expression);
    }

    private static String renderAll(List<JavaIr> expressions) {
        StringBuilder builder = new StringBuilder();
        for (JavaIr expression : expressions) {
            if (builder.length() > 0) builder.append(", ");
            builder.append(render(expression));
        }
        return builder.toString();
    }

    int PREC_ASSIGNMENT = 1;
    int PREC_CONDITIONAL = 2;
    int PREC_OR = 3;
    int PREC_AND = 4;
    int PREC_EQUALITY = 5;
    int PREC_RELATIONAL = 6;
    int PREC_ADDITIVE = 7;
    int PREC_MULTIPLICATIVE = 8;
    int PREC_UNARY = 9;
    int PREC_POSTFIX = 10;
    int PREC_PRIMARY = 11;

    private static int precedence(JavaIr expression) {
        if (expression instanceof Assignment) return PREC_ASSIGNMENT;
        if (expression instanceof Conditional) return PREC_CONDITIONAL;
        if (expression instanceof Binary b) {
            return switch (b.operator()) {
                case "||" -> PREC_OR;
                case "&&" -> PREC_AND;
                case "==", "!=" -> PREC_EQUALITY;
                case "<", "<=", ">", ">=", "instanceof" -> PREC_RELATIONAL;
                case "+", "-" -> PREC_ADDITIVE;
                case "*", "/", "%" -> PREC_MULTIPLICATIVE;
                default -> PREC_PRIMARY;
            };
        }
        if (expression instanceof Unary) return PREC_UNARY;
        if (expression instanceof InstanceOf) return PREC_RELATIONAL;
        if (expression instanceof Cast) return PREC_UNARY;
        if (expression instanceof New || expression instanceof Call
                || expression instanceof MethodCall || expression instanceof StaticCall
                || expression instanceof FieldAccess || expression instanceof StaticField
                || expression instanceof Inline) return PREC_POSTFIX;
        return PREC_PRIMARY;
    }
}
