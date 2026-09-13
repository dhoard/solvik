package org.solvik.transpiler;

import java.util.List;
import java.util.Map;

import static org.solvik.transpiler.Ast.BinaryOp;
import static org.solvik.transpiler.Ast.LiteralKind;
import static org.solvik.transpiler.Ast.UnaryOp;

/**
 * Backend-neutral typed Solvik expression IR.
 *
 * <p>Produced from the analyzed AST; every node carries its resolved Solvik
 * type. Nodes describe Solvik meaning, not Java spellings: the Java backend is
 * responsible for choosing helpers, casts, and Java expressions. The
 * {@link Deferred} node is a temporary migration aid for forms whose lowering
 * has not moved into the IR yet; it carries the analyzed AST node plus the
 * expected type used for spelling.</p>
 */
public sealed interface SolvikIr {
    TypeModel.Type type();

    /** The implicit {@code self} receiver (lowered to Java {@code this}). */
    record Self(TypeModel.Type type) implements SolvikIr {}

    /** A resolved local variable or parameter. */
    record Local(String name, TypeModel.Type type) implements SolvikIr {}

    /** A resolved field access on the current receiver or a type. */
    record Field(String owner, String name, boolean isStatic, TypeModel.Type type) implements SolvikIr {}

    /** A literal; {@code type} is the resolved spelling type. */
    record Literal(LiteralKind kind, String text, TypeModel.Type type) implements SolvikIr {}

    /** A list literal with its resolved element type. */
    record ListLiteral(List<SolvikIr> elements, TypeModel.Type elementType, TypeModel.Type type) implements SolvikIr {}

    /** A map literal with resolved key/value types. */
    record MapLiteral(List<Map.Entry<SolvikIr, SolvikIr>> entries, TypeModel.Type keyType, TypeModel.Type valueType, TypeModel.Type type) implements SolvikIr {}

    /** A resolved member access (field read or method reference). */
    record Member(SolvikIr receiver, String name, boolean isField, TypeModel.Type type) implements SolvikIr {}

    /** A field default (zero/null/empty) used to fill unspecified struct fields. */
    record DefaultValue(TypeModel.Type type) implements SolvikIr {}

    /** Construction of the enclosing struct via {@code Self { ... }}; values are in field order. */
    record SelfInit(String owner, java.util.List<SolvikIr> values, TypeModel.Type type) implements SolvikIr {}

    /** A resolved static/type-scoped value: enum variant, static field, or type constant. */
    record StaticRef(String name, TypeModel.Type type) implements SolvikIr {}

    /** Assignment to a resolved lvalue. */
    record Assign(SolvikIr target, SolvikIr value, TypeModel.Type type) implements SolvikIr {}

    record Unary(UnaryOp op, SolvikIr operand, TypeModel.Type operandType, TypeModel.Type type) implements SolvikIr {}

    record Binary(BinaryOp op, SolvikIr left, SolvikIr right,
                  TypeModel.Type leftType, TypeModel.Type rightType, TypeModel.Type type) implements SolvikIr {}

    record Coalesce(SolvikIr left, SolvikIr right, TypeModel.Type type) implements SolvikIr {}

    record Range(SolvikIr start, SolvikIr end, boolean inclusive, TypeModel.Type type) implements SolvikIr {}

    /** Compound assignment ({@code +=}, {@code ..=}, ...). */
    record Update(BinaryOp op, SolvikIr target, SolvikIr value, TypeModel.Type promoted, TypeModel.Type targetType, TypeModel.Type valueType) implements SolvikIr {
        public TypeModel.Type type() { return targetType; }
    }

    /** A resolved call: user method/constructor or builtin, with coerced arguments. */
    record Call(SolvikIr receiver, TypeModel.Type staticOwnerType, boolean staticTypeArguments, String implicitOwner,
                boolean implicitInstance, String name, String builtin, java.util.List<SolvikIr> arguments,
                TypeModel.Type receiverType, TypeModel.Type type) implements SolvikIr {}

    /** An explicit conversion/boxing/widening/narrowing of a value to a target type. */
    record Coerce(SolvikIr value, TypeModel.Type from, TypeModel.Type to, boolean constantInteger) implements SolvikIr {
        public TypeModel.Type type() { return to; }
    }

    /** A resolved pattern match with lowered arms. */
    record Match(SolvikIr subject, java.util.List<MatchArm> arms, TypeModel.Type type) implements SolvikIr {}

    record MatchArm(SolvikPattern pattern, SolvikIr body) {}

    /** A resolved match pattern (no Java spellings). */
    sealed interface SolvikPattern {
        record Wildcard() implements SolvikPattern {}
        record Bind(String name, TypeModel.Type type) implements SolvikPattern {}
        record Literal(SolvikIr value) implements SolvikPattern {}
        record ListPattern(java.util.List<SolvikPattern> elements, TypeModel.Type elementType) implements SolvikPattern {}
        record Variant(String enumName, int tag, TypeModel.Type payloadType, java.util.List<SolvikPattern> payload) implements SolvikPattern {}
    }
}
