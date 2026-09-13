package org.solvik.transpiler;

import java.util.List;
import java.util.Objects;

/** Solvik types are kept separate from Java source spellings. */
public final class TypeModel {
    private TypeModel() {}

    public enum Base {
        BOOLEAN, BYTE, SHORT, INTEGER, LONG, FLOAT, DOUBLE, BIG_INTEGER, BIG_DECIMAL,
        CHAR, STRING, OBJECT, VOID, NULL, LIST, MAP, STACK, SET, STRUCT, INTERFACE,
        ENUM, TYPE_VAR, RANGE, WRITER, READER, THREAD, MUTEX, SEMAPHORE, PROCESS,
        REGEX, EXCEPTION, THROWABLE, RUNNABLE, UNKNOWN
    }

    public record Type(Base base, String name, List<Type> args, boolean nullableFlag) {
        public Type { args = List.copyOf(args); }
        public Type(Base base) { this(base, null, List.of(), false); }
        public Type(Base base, boolean nullable) { this(base, null, List.of(), nullable); }
        public boolean nullable() { return nullableFlag; }
        public boolean primitive() { return switch (base) { case BOOLEAN, BYTE, SHORT, INTEGER, LONG, FLOAT, DOUBLE, CHAR -> true; default -> false; }; }
        public boolean numeric() { return switch (base) { case BYTE, SHORT, INTEGER, LONG, FLOAT, DOUBLE, BIG_INTEGER, BIG_DECIMAL -> true; default -> false; }; }
        public boolean integral() { return switch (base) { case BYTE, SHORT, INTEGER, LONG -> true; default -> false; }; }
        public boolean floating() { return base == Base.FLOAT || base == Base.DOUBLE; }
        public Type nonNull() { return nullableFlag ? new Type(base, name, args, false) : this; }
        public Type withArgs(List<Type> replacement) { return new Type(base, name, replacement, nullableFlag); }
        @Override public String toString() { String n = name != null ? name : baseName(base); if (!args.isEmpty()) n += "<" + String.join(", ", args.stream().map(Type::toString).toList()) + ">"; return n + (nullableFlag ? "?" : ""); }
    }

    public static Type named(Base base, String name) { return new Type(base, name, List.of(), false); }
    public static Type generic(Base base, String name, List<Type> args) { return new Type(base, name, args, false); }
    public static Type var(String name) { return new Type(Base.TYPE_VAR, name, List.of(), false); }
    public static String baseName(Base b) { return switch (b) {
        case BOOLEAN -> "Boolean"; case BYTE -> "Byte"; case SHORT -> "Short"; case INTEGER -> "Integer";
        case LONG -> "Long"; case FLOAT -> "Float"; case DOUBLE -> "Double"; case BIG_INTEGER -> "BigInteger";
        case BIG_DECIMAL -> "BigDecimal"; case CHAR -> "Char"; case STRING -> "String"; case OBJECT -> "Object";
        case VOID -> "Void"; case NULL -> "null"; case LIST -> "List"; case MAP -> "Map"; case STACK -> "Stack";
        case SET -> "Set"; case STRUCT, INTERFACE, ENUM, TYPE_VAR, RANGE, WRITER, READER, THREAD, MUTEX,
                SEMAPHORE, PROCESS, REGEX, EXCEPTION, THROWABLE, RUNNABLE, UNKNOWN -> b.name();
    }; }

    public static Type fromName(String name, List<Type> args, boolean nullable) {
        Base b = switch (name) {
            case "Boolean" -> Base.BOOLEAN; case "Byte" -> Base.BYTE; case "Short" -> Base.SHORT;
            case "Integer" -> Base.INTEGER; case "Long" -> Base.LONG; case "Float" -> Base.FLOAT;
            case "Double" -> Base.DOUBLE; case "BigInteger" -> Base.BIG_INTEGER; case "BigDecimal" -> Base.BIG_DECIMAL;
            case "Char" -> Base.CHAR; case "String" -> Base.STRING; case "Object" -> Base.OBJECT; case "Void" -> Base.VOID;
            case "List" -> Base.LIST; case "Map" -> Base.MAP; case "Stack" -> Base.STACK; case "Set" -> Base.SET;
            case "Writer" -> Base.WRITER; case "Reader" -> Base.READER; case "Thread" -> Base.THREAD;
            case "Mutex" -> Base.MUTEX; case "Semaphore" -> Base.SEMAPHORE; case "Process" -> Base.PROCESS;
            case "Regex" -> Base.REGEX; case "Exception" -> Base.EXCEPTION; case "Throwable" -> Base.THROWABLE; case "Runnable" -> Base.RUNNABLE;
            case "Range" -> Base.RANGE; default -> Base.UNKNOWN;
        };
        return new Type(b, name, args, nullable);
    }

    /** Java 17 binary numeric promotion, including unary promotion of byte/short. */
    public static Type binaryPromotion(Type a, Type b) {
        if (a.base == Base.BIG_INTEGER && b.base == Base.BIG_INTEGER) return named(Base.BIG_INTEGER, "BigInteger");
        if (a.base == Base.BIG_DECIMAL && b.base == Base.BIG_DECIMAL) return named(Base.BIG_DECIMAL, "BigDecimal");
        if (!a.primitive() || !b.primitive() || a.base == Base.CHAR || b.base == Base.CHAR) return null;
        if (a.base == Base.DOUBLE || b.base == Base.DOUBLE) return named(Base.DOUBLE, "Double");
        if (a.base == Base.FLOAT || b.base == Base.FLOAT) return named(Base.FLOAT, "Float");
        if (a.base == Base.LONG || b.base == Base.LONG) return named(Base.LONG, "Long");
        return named(Base.INTEGER, "Integer");
    }

    public static Type unaryNumeric(Type a) {
        if (a.base == Base.BYTE || a.base == Base.SHORT || a.base == Base.INTEGER) return named(Base.INTEGER, "Integer");
        if (a.numeric()) return a.nonNull();
        return null;
    }

    public static boolean numericWidening(Base source, Base target) {
        if (source == target) return true;
        return switch (source) {
            case BYTE -> target == Base.SHORT || target == Base.INTEGER || target == Base.LONG || target == Base.FLOAT || target == Base.DOUBLE;
            case SHORT -> target == Base.INTEGER || target == Base.LONG || target == Base.FLOAT || target == Base.DOUBLE;
            case INTEGER -> target == Base.LONG || target == Base.FLOAT || target == Base.DOUBLE;
            case LONG -> target == Base.FLOAT || target == Base.DOUBLE;
            case FLOAT -> target == Base.DOUBLE;
            default -> false;
        };
    }

    public static boolean sameIgnoringNull(Type a, Type b) {
        return a.base == b.base && Objects.equals(a.name, b.name) && a.args.equals(b.args);
    }
}
