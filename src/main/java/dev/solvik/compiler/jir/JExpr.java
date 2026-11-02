package dev.solvik.compiler.jir;

import java.util.List;
import java.util.Objects;

/**
 * A typed Java expression.
 *
 * <p>Every node carries its Java result type, so rendering never has to infer one. Calls name
 * their target owner and method, and conversions are explicit {@link Cast} nodes; grouping is not
 * represented here at all, because the renderer derives parentheses from operator precedence.
 */
public sealed interface JExpr {

    /** The Java type this expression produces. */
    JType type();

    /** A decimal, hexadecimal-free Java integer literal. */
    record IntLiteral(String digits, JType type) implements JExpr {
        public IntLiteral {
            Objects.requireNonNull(digits, "digits");
            if (!(type instanceof JType.Primitive primitive)
                    || primitive != JType.Primitive.INT && primitive != JType.Primitive.LONG) {
                throw new IllegalArgumentException("an integer literal is int or long, not " + type);
            }
        }
    }

    /** A floating-point literal; the renderer appends the suffix its type requires. */
    record FloatingLiteral(String digits, JType type) implements JExpr {
        public FloatingLiteral {
            Objects.requireNonNull(digits, "digits");
            if (!(type instanceof JType.Primitive primitive)
                    || primitive != JType.Primitive.DOUBLE && primitive != JType.Primitive.FLOAT) {
                throw new IllegalArgumentException("a fractional literal is double or float, not " + type);
            }
        }
    }

    /** A string literal whose value is unescaped. */
    record StringLiteral(String value) implements JExpr {
        public StringLiteral {
            Objects.requireNonNull(value, "string value");
        }

        @Override
        public JType type() {
            return JTypes.STRING;
        }
    }

    /** {@code true} or {@code false}. */
    record BooleanLiteral(boolean value) implements JExpr {
        @Override
        public JType type() {
            return JType.Primitive.BOOLEAN;
        }
    }

    /** The {@code null} literal. Its static Java type is {@code Object} until a cast says otherwise. */
    record NullLiteral() implements JExpr {
        @Override
        public JType type() {
            return JTypes.OBJECT;
        }
    }

    /**
     * A local variable, parameter, {@code this}, or a type-variable name.
     *
     * @param name the Java identifier
     */
    record Name(String name, JType type) implements JExpr {
        public Name {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
        }
    }

    /** Field access on a value, including {@code this.field}. */
    record FieldAccess(JExpr target, String name, JType type) implements JExpr {
        public FieldAccess {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(name, "field name");
            Objects.requireNonNull(type, "type");
        }
    }

    /** Static field access through its declaring owner. */
    record StaticField(JType.Declared owner, String name, JType type) implements JExpr {
        public StaticField {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(name, "field name");
            Objects.requireNonNull(type, "type");
        }
    }

    /**
     * A method invocation.
     *
     * @param receiver      the instance receiver, or {@code null} for a static call
     * @param owner         the declared owner whose member is invoked
     * @param name          the Java method name
     * @param typeArguments explicit generic arguments, empty when the call infers them
     * @param arguments     the argument expressions in evaluation order
     * @param type          the result type
     */
    record Call(JExpr receiver, JType.Declared owner, String name, List<JType> typeArguments,
                List<JExpr> arguments, JType type) implements JExpr {
        public Call {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(name, "method name");
            typeArguments = List.copyOf(typeArguments);
            arguments = List.copyOf(arguments);
            Objects.requireNonNull(type, "type");
        }
    }

    /** Constructor invocation. */
    record New(JType.Declared created, List<JExpr> arguments, JType type) implements JExpr {
        public New {
            Objects.requireNonNull(created, "created type");
            arguments = List.copyOf(arguments);
            Objects.requireNonNull(type, "type");
        }
    }

    /** Java array creation with explicit elements. */
    record NewArray(JType elementType, List<JExpr> elements) implements JExpr {
        public NewArray {
            Objects.requireNonNull(elementType, "element type");
            elements = List.copyOf(elements);
        }

        @Override
        public JType type() {
            return new JType.Array(elementType);
        }
    }

    /** Java array element access. */
    record ArrayAccess(JExpr array, JExpr index, JType type) implements JExpr {
        public ArrayAccess {
            Objects.requireNonNull(array, "array");
            Objects.requireNonNull(index, "index");
            Objects.requireNonNull(type, "type");
        }
    }

    /** A binary operator. */
    enum BinaryOp {
        PLUS("+"), MINUS("-"), MULTIPLY("*"), DIVIDE("/"), REMAINDER("%"),
        LESS("<"), LESS_EQUAL("<="), GREATER(">"), GREATER_EQUAL(">="),
        EQUAL("=="), NOT_EQUAL("!="), AND("&&"), OR("||");

        private final String symbol;

        BinaryOp(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }
    }

    /** A binary operation on two expressions. */
    record Binary(BinaryOp operator, JExpr left, JExpr right, JType type) implements JExpr {
        public Binary {
            Objects.requireNonNull(operator, "operator");
            Objects.requireNonNull(left, "left operand");
            Objects.requireNonNull(right, "right operand");
            Objects.requireNonNull(type, "type");
        }
    }

    /** A unary operator. */
    enum UnaryOp {
        NEGATE("-"), NOT("!"), INCREMENT("++"), DECREMENT("--");

        private final String symbol;

        UnaryOp(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }
    }

    /** A unary operation on an expression. */
    record Unary(UnaryOp operator, JExpr operand, JType type) implements JExpr {
        public Unary {
            Objects.requireNonNull(operator, "operator");
            Objects.requireNonNull(operand, "operand");
            Objects.requireNonNull(type, "type");
        }
    }

    /** An explicit Java cast. */
    record Cast(JType target, JExpr operand) implements JExpr {
        public Cast {
            Objects.requireNonNull(target, "target type");
            Objects.requireNonNull(operand, "operand");
        }

        @Override
        public JType type() {
            return target;
        }
    }

    /** A conditional expression. */
    record Conditional(JExpr condition, JExpr whenTrue, JExpr whenFalse, JType type)
            implements JExpr {
        public Conditional {
            Objects.requireNonNull(condition, "condition");
            Objects.requireNonNull(whenTrue, "true branch");
            Objects.requireNonNull(whenFalse, "false branch");
            Objects.requireNonNull(type, "type");
        }
    }

    /**
     * An assignment expression.
     *
     * <p>The target is restricted to an assignable form, so malformed IR cannot render as a
     * non-assignable Java expression.
     */
    record Assignment(JExpr target, JExpr value, JType type) implements JExpr {
        public Assignment {
            if (!(target instanceof Name || target instanceof FieldAccess
                    || target instanceof StaticField || target instanceof ArrayAccess)) {
                throw new IllegalArgumentException(
                        "an assignment target must be a variable, field, or array element, not "
                                + (target == null ? "null" : target.getClass().getSimpleName()));
            }
            Objects.requireNonNull(value, "assigned value");
            Objects.requireNonNull(type, "type");
        }
    }

    /**
     * An {@code instanceof} test, optionally binding a pattern variable.
     *
     * @param patternName the pattern variable name, or {@code null} for a plain test
     */
    record InstanceOf(JExpr operand, JType tested, String patternName) implements JExpr {
        public InstanceOf {
            Objects.requireNonNull(operand, "operand");
            Objects.requireNonNull(tested, "tested type");
            if (tested.isPrimitive() || tested.isVoid()) {
                throw new IllegalArgumentException("instanceof tests a reference type, not " + tested);
            }
        }

        @Override
        public JType type() {
            return JType.Primitive.BOOLEAN;
        }
    }

}
