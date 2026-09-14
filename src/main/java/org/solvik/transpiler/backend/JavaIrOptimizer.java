package org.solvik.transpiler.backend;

import java.util.ArrayList;
import java.util.List;

/**
 * Representation-level cleanup pass over {@link JavaIr} applied after Solvik
 * lowering and before rendering.
 *
 * <p>It performs only rewrites that can never change meaning: flattening a cast
 * of the same Java type and collapsing {@code Paren(Paren(x))} nesting. It
 * deliberately keeps explicit {@code Paren} nodes, because the lowerer uses
 * them to pin grouping that precedence alone would not preserve. Semantic
 * folding stays in {@code IrOptimizer}.</p>
 */
public final class JavaIrOptimizer {
    public JavaIr optimize(JavaIr node) {
        return rewrite(node);
    }

    private JavaIr rewrite(JavaIr node) {
        if (node instanceof JavaIr.Identifier || node instanceof JavaIr.This
                || node instanceof JavaIr.Literal || node instanceof JavaIr.StaticField
                || node instanceof JavaIr.Inline) return node;
        if (node instanceof JavaIr.Binary b) return new JavaIr.Binary(b.operator(), rewrite(b.left()), rewrite(b.right()), b.type());
        if (node instanceof JavaIr.Unary u) return new JavaIr.Unary(u.operator(), rewrite(u.operand()), u.type());
        if (node instanceof JavaIr.Paren p) {
            JavaIr inner = rewrite(p.value());
            if (inner instanceof JavaIr.Paren) return rewrite(inner);
            return new JavaIr.Paren(inner, p.type());
        }
        if (node instanceof JavaIr.Call c) return new JavaIr.Call(c.function(), c.typeArguments(), rewriteArgs(c.arguments()), c.type());
        if (node instanceof JavaIr.MethodCall m) return new JavaIr.MethodCall(rewrite(m.receiver()), m.name(), m.typeArguments(), rewriteArgs(m.arguments()), m.type());
        if (node instanceof JavaIr.StaticCall s) return new JavaIr.StaticCall(s.owner(), s.typeArguments(), s.method(), rewriteArgs(s.arguments()), s.type());
        if (node instanceof JavaIr.Conditional t) return new JavaIr.Conditional(rewrite(t.condition()), rewrite(t.whenTrue()), rewrite(t.whenFalse()), t.type());
        if (node instanceof JavaIr.Cast c) return new JavaIr.Cast(c.javaType(), flattenCast(rewrite(c.value()), c), c.type());
        if (node instanceof JavaIr.New n) return new JavaIr.New(n.typeName(), n.typeArguments(), rewriteArgs(n.arguments()), n.type());
        if (node instanceof JavaIr.InstanceOf i) return new JavaIr.InstanceOf(rewrite(i.value()), i.javaType(), i.type());
        if (node instanceof JavaIr.Assignment a) return new JavaIr.Assignment(rewrite(a.target()), rewrite(a.value()), a.type());
        if (node instanceof JavaIr.FieldAccess f) return new JavaIr.FieldAccess(rewrite(f.receiver()), f.field(), f.type());
        return node;
    }

    private List<JavaIr> rewriteArgs(List<JavaIr> args) {
        if (args.isEmpty()) return args;
        List<JavaIr> result = new ArrayList<>(args.size());
        for (JavaIr arg : args) result.add(rewrite(arg));
        return result;
    }

    /** Flatten {@code T((T) x)} to {@code (T) x}; never drops a needed cast. */
    private JavaIr flattenCast(JavaIr value, JavaIr.Cast outer) {
        if (value instanceof JavaIr.Cast inner && inner.javaType().equals(outer.javaType())) return flattenCast(inner.value(), outer);
        return value;
    }
}
