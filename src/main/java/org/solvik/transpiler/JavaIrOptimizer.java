package org.solvik.transpiler;

import java.util.List;

/**
 * Java-specific optimization pass applied after Solvik IR lowering and before the
 * Java source is rendered. It performs only representation-level rewrites that
 * never change the program's meaning: flattening nested casts to the same Java
 * type and collapsing redundant nested parentheses. The emitted source then
 * reads like ordinary Java rather than a direct translation of the Solvik IR.
 *
 * <p>Semantic optimization stays in {@link IrOptimizer}. This pass deliberately
 * does not drop casts or boxing, because the optimizer has no reliable way to know
 * whether a value is currently boxed or primitive; it also preserves the
 * parentheses that the lowering uses to encode Java precedence.</p>
 */
final class JavaIrOptimizer {

    JavaIr optimize(JavaIr node) {
        return rewrite(node);
    }

    private JavaIr rewrite(JavaIr node) {
        if (node instanceof JavaIr.Atom) return node;
        if (node instanceof JavaIr.Infix i) {
            return JavaIr.infix(i.operator(), rewrite(i.left()), rewrite(i.right()), i.type());
        }
        if (node instanceof JavaIr.Prefix p) {
            return JavaIr.prefix(p.operator(), rewrite(p.operand()), p.type());
        }
        if (node instanceof JavaIr.Paren p) {
            // Parentheses are preserved because the lowering wraps many nodes in
            // Paren specifically to keep Java precedence correct (for example a
            // method receiver that is itself an infix expression); a blanket paren
            // drop would silently change precedence. Only the redundant
            // Paren(Paren(...)) nesting is collapsed, which never affects grouping.
            JavaIr inner = rewrite(p.value());
            if (inner instanceof JavaIr.Paren) return rewrite(inner);
            return JavaIr.paren(inner, p.type());
        }
        if (node instanceof JavaIr.Call c) {
            return JavaIr.call(c.function(), rewriteArgs(c.arguments()), c.type());
        }
        if (node instanceof JavaIr.MethodCall m) {
            return JavaIr.method(rewrite(m.receiver()), m.name(), rewriteArgs(m.arguments()), m.type());
        }
        if (node instanceof JavaIr.Ternary t) {
            return JavaIr.ternary(rewrite(t.condition()), rewrite(t.whenTrue()), rewrite(t.whenFalse()), t.type());
        }
        if (node instanceof JavaIr.Cast c) {
            return JavaIr.cast(c.javaType(), flattenCast(rewrite(c.value()), c), c.type());
        }
        if (node instanceof JavaIr.New n) {
            return JavaIr.newExpression(n.typeName(), rewriteArgs(n.arguments()), n.type());
        }
        if (node instanceof JavaIr.FieldAccess f) {
            return JavaIr.fieldAccess(rewrite(f.receiver()), f.field(), f.type());
        }
        if (node instanceof JavaIr.StaticMethodCall sm) {
            return JavaIr.staticMethod(sm.owner(), sm.method(), rewriteArgs(sm.arguments()), sm.type());
        }
        return node;
    }

    private List<JavaIr> rewriteArgs(List<JavaIr> args) {
        if (args.isEmpty()) return args;
        List<JavaIr> result = new java.util.ArrayList<>(args.size());
        for (JavaIr arg : args) result.add(rewrite(arg));
        return result;
    }

    /** Flatten {@code T((T) x)} to {@code (T) x}; never drops a needed cast. */
    private JavaIr flattenCast(JavaIr value, JavaIr.Cast outer) {
        if (value instanceof JavaIr.Cast inner && sameJavaType(inner, outer)) {
            return flattenCast(inner.value(), outer);
        }
        return value;
    }

    private boolean sameJavaType(JavaIr first, JavaIr second) {
        return javaTypeOf(first).equals(javaTypeOf(second));
    }

    private String javaTypeOf(JavaIr node) {
        TypeModel.Type type = node.type();
        if (type == null) return "Object";
        return switch (type.base()) {
            case BOOLEAN -> "boolean";
            case BYTE -> "byte";
            case SHORT -> "short";
            case INTEGER -> "int";
            case LONG -> "long";
            case FLOAT -> "float";
            case DOUBLE -> "double";
            case CHAR -> "String";
            case BIG_INTEGER -> "BigInteger";
            case BIG_DECIMAL -> "BigDecimal";
            case STRING -> "String";
            case OBJECT, NULL -> "Object";
            case VOID -> "void";
            case LIST -> "RT.SList";
            case MAP -> "RT.SMap";
            case STACK -> "RT.SStack";
            case SET -> "RT.SSet";
            case STRUCT -> "__S_" + type.name();
            case TRAIT -> "__I_" + type.name();
            case ENUM -> "__E_" + type.name();
            default -> "Object";
        };
    }
}
