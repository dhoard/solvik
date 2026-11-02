package dev.solvik.compiler.types;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A Solvik type: an ordinary non-null type, a nullable {@code T?} type, or the {@link Kind#NULL}
 * singleton type.
 *
 * <p>Ordinary types are non-nullable unless {@link #nullable()} says otherwise, so {@code String}
 * and {@code String?} are different types ({@code LANGUAGE.md} §2). {@code Null} is a type of its
 * own and is not a subtype of {@code Any}; it is assignable only to a nullable type.
 *
 * <p>An array type keeps the nullability of the array and of its elements independently, and a
 * fixed-length array keeps its length: {@code String?[2]} is a non-null array of two nullable
 * strings, {@code String[2]?} is a nullable array of non-null strings, and {@code String[3]} is a
 * different type from {@code String[2]} (§5). A {@code null} length is the size-erased
 * {@code String[]} array type.
 *
 * <p>Numeric promotion is an expression rule ({@link #widest}), never a subtyping relation, and
 * assignment is type-stable.
 */
public record SolvikType(Kind kind, boolean nullable, SolvikType element, Integer length,
                         String userName, List<SolvikType> arguments) {

    /** The carrier of a Solvik type. Declaration order groups the numeric carriers together. */
    public enum Kind {
        BYTE, SHORT, INTEGER, LONG, FLOAT, DOUBLE, BIG_INTEGER, BIG_DECIMAL,
        STRING, BOOLEAN, NULL, ANY, NUMBER, ARRAY, USER, EXCEPTION, VOID, VARIABLE
    }

    public SolvikType {
        arguments = List.copyOf(arguments);
        if (kind == Kind.NULL && nullable) {
            throw new IllegalArgumentException("Null is the type of one non-null value");
        }
        if (kind == Kind.ARRAY && element == null) {
            throw new IllegalArgumentException("an array type needs an element type");
        }
        if (kind != Kind.ARRAY && element != null) {
            throw new IllegalArgumentException("only an array type has an element type");
        }
        if (kind == Kind.ARRAY && length != null && length < 1) {
            throw new IllegalArgumentException("a fixed array length is at least 1");
        }
        if (kind != Kind.ARRAY && length != null) {
            throw new IllegalArgumentException("only an array type has a length");
        }
        if (kind == Kind.VOID && nullable) {
            throw new IllegalArgumentException("void is not a value type");
        }
    }

    public static final SolvikType BYTE = scalar(Kind.BYTE);
    public static final SolvikType SHORT = scalar(Kind.SHORT);
    public static final SolvikType INTEGER = scalar(Kind.INTEGER);
    public static final SolvikType LONG = scalar(Kind.LONG);
    public static final SolvikType FLOAT = scalar(Kind.FLOAT);
    public static final SolvikType DOUBLE = scalar(Kind.DOUBLE);
    public static final SolvikType BIG_INTEGER = scalar(Kind.BIG_INTEGER);
    public static final SolvikType BIG_DECIMAL = scalar(Kind.BIG_DECIMAL);
    public static final SolvikType STRING = scalar(Kind.STRING);
    public static final SolvikType BOOLEAN = scalar(Kind.BOOLEAN);
    /** The singleton type {@code Null}, whose only value is written {@code Null} (§2.1). */
    public static final SolvikType NULL = scalar(Kind.NULL);
    /**
     * The built-in exception type {@code Exception} (§7.4).
     *
     * <p>Solvik has no inheritance, so this one nominal type is the entire catchable surface: a
     * {@code catch} clause names it, and the value carries an {@code Integer} code for dispatch and
     * a {@code String} message.
     */
    public static final SolvikType EXCEPTION = scalar(Kind.EXCEPTION);
    public static final SolvikType ANY = scalar(Kind.ANY);
    public static final SolvikType NUMBER = scalar(Kind.NUMBER);
    public static final SolvikType VOID = scalar(Kind.VOID);

    /** The built-in type spelled {@code name}, or {@code null} when the name is not built in. */
    public static SolvikType named(String name) {
        return switch (name) {
            case "Byte" -> BYTE;
            case "Short" -> SHORT;
            case "Integer" -> INTEGER;
            case "Long" -> LONG;
            case "Float" -> FLOAT;
            case "Double" -> DOUBLE;
            case "BigInteger" -> BIG_INTEGER;
            case "BigDecimal" -> BIG_DECIMAL;
            case "String" -> STRING;
            case "Boolean" -> BOOLEAN;
            case "Exception" -> EXCEPTION;
            case "Null" -> NULL;
            case "Any" -> ANY;
            case "Number" -> NUMBER;
            default -> null;
        };
    }

    /** The size-erased array type {@code T[]}. */
    public static SolvikType array(SolvikType element) {
        return array(element, null);
    }

    /** An array type: fixed when {@code length} is not {@code null}, size-erased otherwise. */
    public static SolvikType array(SolvikType element, Integer length) {
        return new SolvikType(Kind.ARRAY, false, element, length, null, List.of());
    }

    public static SolvikType user(String name) {
        return user(name, List.of());
    }

    public static SolvikType user(String name, List<SolvikType> arguments) {
        return new SolvikType(Kind.USER, false, null, null, name, List.copyOf(arguments));
    }

    public static SolvikType variable(String name) {
        return new SolvikType(Kind.VARIABLE, false, null, null, name, List.of());
    }

    /** This type with {@code ?} applied: the value may be {@code Null} as well. */
    public SolvikType asNullable() {
        return nullable || kind == Kind.NULL || kind == Kind.VOID ? this
                : new SolvikType(kind, true, element, length, userName, arguments);
    }

    /**
     * This type without {@code ?}: the type of a value proven non-null (§7.1).
     *
     * <p>A scalar resolves to the shared non-null constant, so a proven value compares by identity
     * with the type it names.
     */
    public SolvikType asNonNull() {
        if (!nullable) {
            return this;
        }
        return switch (kind) {
            case BYTE -> BYTE;
            case SHORT -> SHORT;
            case INTEGER -> INTEGER;
            case LONG -> LONG;
            case FLOAT -> FLOAT;
            case DOUBLE -> DOUBLE;
            case BIG_INTEGER -> BIG_INTEGER;
            case BIG_DECIMAL -> BIG_DECIMAL;
            case STRING -> STRING;
            case BOOLEAN -> BOOLEAN;
            case EXCEPTION -> EXCEPTION;
            case NULL -> NULL;
            case ANY -> ANY;
            case NUMBER -> NUMBER;
            default -> new SolvikType(kind, false, element, length, userName, arguments);
        };
    }

    public boolean isNullable() {
        return nullable;
    }

    public boolean isVariable() {
        return kind == Kind.VARIABLE;
    }

    public boolean isArray() {
        return kind == Kind.ARRAY;
    }

    /** Whether this is the singleton {@code Null} type. */
    public boolean isNull() {
        return kind == Kind.NULL;
    }

    /** Whether the type is a numeric carrier, primitive or arbitrary precision. */
    public boolean isNumeric() {
        return kind == Kind.BYTE || kind == Kind.SHORT || kind == Kind.INTEGER
                || kind == Kind.LONG || kind == Kind.FLOAT || kind == Kind.DOUBLE
                || kind == Kind.BIG_INTEGER || kind == Kind.BIG_DECIMAL;
    }

    /** Whether the type is a numeric carrier the JVM stores as a primitive. */
    public boolean isPrimitiveNumeric() {
        return kind == Kind.BYTE || kind == Kind.SHORT || kind == Kind.INTEGER
                || kind == Kind.LONG || kind == Kind.FLOAT || kind == Kind.DOUBLE;
    }

    /** Whether the type is a whole-valued numeric carrier. */
    public boolean isIntegral() {
        return kind == Kind.BYTE || kind == Kind.SHORT || kind == Kind.INTEGER
                || kind == Kind.LONG || kind == Kind.BIG_INTEGER;
    }

    /** {@code this} with every type variable replaced by its argument in {@code mapping}. */
    public SolvikType substitute(Map<String, SolvikType> mapping) {
        return switch (kind) {
            case VARIABLE -> {
                SolvikType substituted = mapping.get(userName);
                if (substituted == null) {
                    yield this;
                }
                // `T?` keeps its `?` once the argument is known (§8.2).
                yield nullable ? substituted.asNullable() : substituted;
            }
            case ARRAY -> {
                SolvikType substituted = array(element.substitute(mapping), length);
                yield nullable ? substituted.asNullable() : substituted;
            }
            case USER -> {
                SolvikType substituted = user(userName, arguments.stream()
                        .map(argument -> argument.substitute(mapping)).toList());
                yield nullable ? substituted.asNullable() : substituted;
            }
            default -> this;
        };
    }

    /**
     * The carrier an arithmetic expression on {@code left} and {@code right} is evaluated in, or
     * {@code null} when the two carriers do not mix.
     *
     * <p>Only primitive carriers promote; an arbitrary-precision carrier mixes with neither a
     * primitive nor a different arbitrary-precision carrier ({@code LANGUAGE.md} §4).
     */
    public static SolvikType widest(SolvikType left, SolvikType right) {
        if (left == null || right == null || !left.isNumeric() || !right.isNumeric()) {
            return null;
        }
        if (!left.isPrimitiveNumeric() || !right.isPrimitiveNumeric()) {
            return left.equals(right) ? left : null;
        }
        if (left == DOUBLE || right == DOUBLE) {
            return DOUBLE;
        }
        if (left == FLOAT || right == FLOAT) {
            return FLOAT;
        }
        if (left == LONG || right == LONG) {
            return LONG;
        }
        return INTEGER;
    }

    /**
     * Assignment compatibility: a value of this type may initialize a binding of {@code target}
     * (§2.2).
     *
     * <p>{@code T} is assignable to {@code T?}, {@code Null} is assignable to any {@code T?} and to
     * {@code Any?}, and a fixed-length array is assignable only to an array of the same length or
     * to the size-erased array type. Numeric promotion is reserved for expressions.
     */
    public boolean isAssignableTo(SolvikType target) {
        if (equals(target)) return true;
        if (target == null) return false;
        if (kind == Kind.VOID || target.kind == Kind.VOID) return false;
        if (kind == Kind.NULL) return target.nullable;
        if (nullable && !target.nullable) return false;
        if (kind == target.kind) return sameKindAssignable(target);
        return switch (target.kind) {
            case ANY -> true;
            case NUMBER -> isNumeric();
            default -> false;
        };
    }

    /** Same-kind compatibility once exact equality and nullability have been settled. */
    private boolean sameKindAssignable(SolvikType target) {
        return switch (kind) {
            case ARRAY -> arrayAssignable(target);
            case USER -> userName.equals(target.userName) && arguments.equals(target.arguments());
            case VOID -> true;
            default -> true;
        };
    }

    /**
     * Whether this array value may be assigned to {@code target} (§5).
     *
     * <p>Every length the target fixes must match, so a {@code T[3]} type never stands for a
     * two-element array. The element type must match exactly at every level: a widening would let
     * a write through the target store a value the source type does not admit. Element nullability
     * in particular is invariant, because assigning {@code T[N]} to {@code T?[N]} would let a
     * {@code Null} be written into an array whose elements are declared non-null (§2.2, §12).
     */
    private boolean arrayAssignable(SolvikType target) {
        if (nullable && !target.nullable) {
            return false;
        }
        if (target.length != null && (length == null || !length.equals(target.length))) {
            return false; // an array of unknown length is not a fixed-length array
        }
        if (element.kind == Kind.ARRAY || target.element.kind == Kind.ARRAY) {
            return element.kind == target.element.kind
                    && element.arrayAssignable(target.element);
        }
        return element.equals(target.element);
    }

    @Override
    public String toString() {
        return switch (kind) {
            case BIG_INTEGER -> "BigInteger" + suffix();
            case BIG_DECIMAL -> "BigDecimal" + suffix();
            case ARRAY -> element + "[" + (length == null ? "" : length.toString()) + "]"
                    + suffix();
            case USER -> userName + (arguments.isEmpty() ? ""
                    : "<" + arguments.stream().map(Object::toString)
                            .collect(Collectors.joining(", ")) + ">") + suffix();
            case NULL -> "Null";
            default -> capitalized(kind) + suffix();
        };
    }

    private String suffix() {
        return nullable ? "?" : "";
    }

    private static SolvikType scalar(Kind kind) {
        return new SolvikType(kind, false, null, null, null, List.of());
    }

    /** {@code INTEGER} as {@code Integer}: the spelling a diagnostic shows. */
    private static String capitalized(Kind kind) {
        String name = kind.name();
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }
}
