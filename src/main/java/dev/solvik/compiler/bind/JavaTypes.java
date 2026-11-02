package dev.solvik.compiler.bind;

import dev.solvik.compiler.types.SolvikType;

/**
 * Maps the Java types a binding may expose to their Solvik carriers.
 *
 * <p>Only public classes in {@code java.lang} and {@code java.math} with a named Solvik type are
 * representable. Everything else — wide primitives, arrays, collections, arbitrary application
 * classes — returns {@code null}, which callers report as an unsupported signature instead of
 * guessing a representation.
 *
 * <p>Native bindings and direct calls on the built-in big numbers share this mapping, so both paths
 * accept exactly the same Java signatures.
 */
final class JavaTypes {

    private JavaTypes() {
    }

    /**
     * The Solvik type of a raw Java class, or {@code null} when the class has no Solvik
     * representation.
     */
    static SolvikType of(Class<?> clazz) {
        if (clazz == void.class) {
            return SolvikType.VOID;
        }
        if (clazz == Object.class) {
            return SolvikType.ANY;
        }
        if (clazz == Number.class) {
            return SolvikType.NUMBER;
        }
        if (clazz.isPrimitive()) {
            return switch (clazz.getName()) {
                case "byte" -> SolvikType.BYTE;
                case "short" -> SolvikType.SHORT;
                case "int" -> SolvikType.INTEGER;
                case "long" -> SolvikType.LONG;
                case "float" -> SolvikType.FLOAT;
                case "double" -> SolvikType.DOUBLE;
                case "boolean" -> SolvikType.BOOLEAN;
                default -> null;
            };
        }
        String packageName = clazz.getPackageName();
        if (!packageName.equals("java.lang") && !packageName.equals("java.math")) {
            return null;
        }
        return SolvikType.named(clazz.getSimpleName());
    }
}
