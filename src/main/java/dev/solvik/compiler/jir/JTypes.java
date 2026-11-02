package dev.solvik.compiler.jir;

import dev.solvik.compiler.transpile.GeneratedNames;
import dev.solvik.compiler.types.SolvikType;
import java.util.List;

/** The Java types the backend names directly, and the Solvik-to-Java type mapping. */
public final class JTypes {
    private JTypes() {
    }

    public static final JType VOID = new JType.Void();

    public static final JType.Declared OBJECT = JType.Declared.of("java.lang", "Object");
    public static final JType.Declared STRING = JType.Declared.of("java.lang", "String");
    public static final JType.Declared BOOLEAN = JType.Declared.of("java.lang", "Boolean");
    public static final JType.Declared BYTE = JType.Declared.of("java.lang", "Byte");
    public static final JType.Declared SHORT = JType.Declared.of("java.lang", "Short");
    public static final JType.Declared INTEGER = JType.Declared.of("java.lang", "Integer");
    public static final JType.Declared LONG = JType.Declared.of("java.lang", "Long");
    public static final JType.Declared FLOAT = JType.Declared.of("java.lang", "Float");
    public static final JType.Declared DOUBLE = JType.Declared.of("java.lang", "Double");
    public static final JType.Declared CHARACTER = JType.Declared.of("java.lang", "Character");
    public static final JType.Declared NUMBER = JType.Declared.of("java.lang", "Number");
    public static final JType.Declared BIG_INTEGER = JType.Declared.of("java.math", "BigInteger");
    public static final JType.Declared BIG_DECIMAL = JType.Declared.of("java.math", "BigDecimal");
    public static final JType.Declared ITERABLE = JType.Declared.of("java.lang", "Iterable");
    public static final JType.Declared STRING_BUILDER = JType.Declared.of("java.lang", "StringBuilder");
    public static final JType.Declared PRINT_STREAM = JType.Declared.of("java.io", "PrintStream");
    public static final JType.Declared SYSTEM = JType.Declared.of("java.lang", "System");
    public static final JType.Declared OBJECTS = JType.Declared.of("java.util", "Objects");
    public static final JType.Declared ARRAYS = JType.Declared.of("java.util", "Arrays");
    public static final JType.Declared PATTERN = JType.Declared.of("java.util.regex", "Pattern");
    public static final JType.Declared RUNTIME_EXCEPTION =
            JType.Declared.of("java.lang", "RuntimeException");
    public static final JType.Declared EXCEPTION = JType.Declared.of("java.lang", "Exception");
    public static final JType.Declared LOCALE = JType.Declared.of("java.util", "Locale");

    /** {@code java.util.List<T>}. */
    public static JType.Declared list(JType element) {
        return JType.Declared.of("java.util", "List").withArguments(List.of(element));
    }

    /** {@code java.util.List<?>}. */
    public static JType.Declared listOfAny() {
        return list(JType.Wildcard.any());
    }

    /** {@code java.util.ArrayList<T>}. */
    public static JType.Declared arrayList(JType element) {
        return JType.Declared.of("java.util", "ArrayList").withArguments(List.of(element));
    }

    /** The program class that holds generated support members. */
    public static JType.Declared programClass() {
        return JType.Declared.of(GeneratedNames.GLOBAL_PACKAGE, GeneratedNames.PROGRAM_CLASS);
    }

    /** A nested type of the program class, such as its runtime {@code Error}. */
    public static JType.Declared programNested(String name) {
        return new JType.Declared(GeneratedNames.GLOBAL_PACKAGE,
                List.of(GeneratedNames.PROGRAM_CLASS), name, List.of());
    }

    /**
     * A declared type named by a qualified Solvik type name ({@code geometry::Point}).
     *
     * <p>The generated Java name keeps one class per Solvik type, in the package the module maps
     * to, so this conversion is the single place that knows the naming rule.
     */
    public static JType.Declared generated(String qualifiedName) {
        String binary = GeneratedNames.javaClassName(qualifiedName);
        int separator = binary.lastIndexOf('.');
        return JType.Declared.of(binary.substring(0, separator), binary.substring(separator + 1));
    }

    /** The Java type that names a class; primitives map to their primitive type. */
    public static JType forClass(Class<?> type) {
        if (type.isPrimitive()) {
            return switch (type.getName()) {
                case "byte" -> JType.Primitive.BYTE;
                case "short" -> JType.Primitive.SHORT;
                case "int" -> JType.Primitive.INT;
                case "long" -> JType.Primitive.LONG;
                case "float" -> JType.Primitive.FLOAT;
                case "double" -> JType.Primitive.DOUBLE;
                case "boolean" -> JType.Primitive.BOOLEAN;
                case "char" -> JType.Primitive.CHAR;
                default -> throw new IllegalArgumentException("not a Java type: " + type);
            };
        }
        String canonical = type.getCanonicalName();
        if (canonical == null) {
            throw new IllegalArgumentException("a local or anonymous class has no Java type name");
        }
        String packageName = type.getPackageName();
        String rest = packageName.isEmpty() ? canonical : canonical.substring(packageName.length() + 1);
        String[] parts = rest.split("\\.");
        if (parts.length == 1) {
            return JType.Declared.of(packageName, parts[0]);
        }
        return new JType.Declared(packageName, List.of(parts).subList(0, parts.length - 1),
                parts[parts.length - 1], List.of());
    }

    /** The declared type that names a Java reference class, including a nested one. */
    public static JType.Declared declaredForClass(Class<?> type) {
        if (forClass(type) instanceof JType.Declared declared) {
            return declared;
        }
        throw new IllegalArgumentException("not a Java reference class: " + type.getName());
    }

    /**
     * The Java carrier of a Solvik type.
     *
     * <p>This is representation, not semantics: the checker has already decided what each
     * expression means. Type variables are emitted as their own name, arrays as
     * {@code java.util.List<Element>} (the committed array carrier), the singleton
     * {@code Null} type as {@code Object}, and {@code T?} as exactly the carrier of {@code T}.
     */
    public static JType of(SolvikType type) {
        if (type == null) {
            return OBJECT;
        }
        return switch (type.kind()) {
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
            case NUMBER -> NUMBER;
            case ANY, NULL -> OBJECT;
            case VOID -> VOID;
            case VARIABLE -> new JType.TypeVariable(type.userName());
            case EXCEPTION -> programNested(GeneratedNames.EXCEPTION_CLASS);
            case ARRAY -> list(of(type.element()));
            case USER -> {
                JType.Declared declared = generated(type.userName());
                if (type.arguments().isEmpty()) {
                    yield declared;
                }
                yield declared.withArguments(type.arguments().stream().map(JTypes::of).toList());
            }
        };
    }

    /** Whether a Solvik type is carried by a Java primitive when lowered as an operand. */
    public static JType primitive(SolvikType type) {
        if (type == null || !type.isPrimitiveNumeric()) {
            throw new IllegalArgumentException("not a primitive numeric carrier: " + type);
        }
        return switch (type.kind()) {
            case BYTE -> JType.Primitive.BYTE;
            case SHORT -> JType.Primitive.SHORT;
            case INTEGER -> JType.Primitive.INT;
            case LONG -> JType.Primitive.LONG;
            case FLOAT -> JType.Primitive.FLOAT;
            case DOUBLE -> JType.Primitive.DOUBLE;
            default -> throw new IllegalArgumentException("not a primitive numeric carrier: " + type);
        };
    }
}
