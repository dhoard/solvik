package dev.solvik.compiler.jir;

import java.util.List;
import java.util.Objects;

/**
 * A structural Java type used by the typed Java IR.
 *
 * <p>Types are represented by shape rather than by preformatted text: a primitive, an array, a
 * type variable, a wildcard, a declared class, or {@code void}. A declared type keeps its package
 * and any enclosing class names, so the renderer alone decides whether to import it or to spell it
 * fully qualified. Nothing in this package renders Java source.
 */
public sealed interface JType {

    /** The primitive Java types the backend emits. */
    enum Primitive implements JType {
        BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BOOLEAN, CHAR;

        /** The Java keyword spelling. */
        public String keyword() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** The result type of a method that returns nothing. */
    record Void() implements JType {
        @Override
        public String toString() {
            return "void";
        }
    }

    /** A Java array; the element type carries the dimensions. */
    record Array(JType element) implements JType {
        public Array {
            Objects.requireNonNull(element, "array element type");
        }

        @Override
        public String toString() {
            return element + "[]";
        }
    }

    /**
     * A declared class, interface, or enum.
     *
     * @param packageName the package, or the empty string for the unnamed package
     * @param enclosing   the enclosing top-level-to-innermost class names, empty for a top-level type
     * @param name        the simple name, which may contain {@code $}
     * @param arguments   the type arguments, empty for a raw or non-generic type
     */
    record Declared(String packageName, List<String> enclosing, String name, List<JType> arguments)
            implements JType {

        public Declared {
            Objects.requireNonNull(packageName, "package name");
            enclosing = List.copyOf(enclosing);
            Objects.requireNonNull(name, "type name");
            arguments = List.copyOf(arguments);
            if (name.isEmpty()) {
                throw new IllegalArgumentException("a declared type needs a name");
            }
        }

        /** A top-level declared type with no type arguments. */
        public static Declared of(String packageName, String name) {
            return new Declared(packageName, List.of(), name, List.of());
        }

        /** This type with the given type arguments. */
        public Declared withArguments(List<JType> typeArguments) {
            return new Declared(packageName, enclosing, name, typeArguments);
        }

        /** The simple name of the top-level class that owns this type. */
        public String topLevelName() {
            return enclosing.isEmpty() ? name : enclosing.getFirst();
        }

        /** The binary name, for example {@code solvik.Solvik$Program.Error}. */
        public String binaryName() {
            String prefix = packageName.isEmpty() ? "" : packageName + ".";
            String nested = String.join(".", enclosing);
            return prefix + (nested.isEmpty() ? name : nested + "." + name);
        }

        /** Whether this type has the given binary name. */
        public boolean hasBinaryName(String binaryName) {
            return binaryName().equals(binaryName);
        }

        @Override
        public String toString() {
            return binaryName();
        }
    }

    /** A class or method type parameter, such as {@code T}. */
    record TypeVariable(String name) implements JType {
        public TypeVariable {
            Objects.requireNonNull(name, "type variable name");
        }
    }

    /**
     * A wildcard type argument.
     *
     * @param bound the bound, or {@code null} for an unbounded {@code ?}
     * @param lower whether this is a {@code ? super} bound instead of a {@code ? extends} one
     */
    record Wildcard(JType bound, boolean lower) implements JType {
        public Wildcard {
            if (bound == null && lower) {
                throw new IllegalArgumentException("an unbounded wildcard has no lower bound");
            }
        }

        @Override
        public String toString() {
            if (bound == null) {
                return "?";
            }
            return "? " + (lower ? "super " : "extends ") + bound;
        }

        /** The unbounded wildcard {@code ?}. */
        public static Wildcard any() {
            return new Wildcard(null, false);
        }
    }

    /** Whether this type is {@code void}. */
    default boolean isVoid() {
        return this instanceof Void;
    }

    /** Whether this type is a primitive. */
    default boolean isPrimitive() {
        return this instanceof Primitive;
    }

    /** The number of array dimensions in this type. */
    default int dimensions() {
        int count = 0;
        JType current = this;
        while (current instanceof Array array) {
            count++;
            current = array.element();
        }
        return count;
    }
}
