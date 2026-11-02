package dev.solvik.compiler.transpile;

import java.util.Set;

/** Injective Java names: Solvik modules are packages; {@code solvik} is a reserved module. */
public final class GeneratedNames {
    public static final String GLOBAL_PACKAGE = "solvik";
    public static final String PROGRAM_CLASS = "Solvik$Program";
    public static final String PROGRAM_BINARY_NAME = GLOBAL_PACKAGE + "." + PROGRAM_CLASS;
    public static final String FUNCTIONS_CLASS = "Solvik$Functions";
    /**
     * The nested support types of the program class.
     *
     * <p>{@code $} cannot appear in a Solvik identifier, so no declared type can take these names:
     * a user {@code type Error}, for example, stays {@code solvik.Error} instead of binding to the
     * support class inside {@code Solvik$Program}.
     */
    public static final String ERROR_CLASS = "$solvikError";
    public static final String EXIT_CLASS = "$solvikExit";
    /** The nested support type a raised or caught Solvik {@code Exception} is lowered to (§7.4). */
    public static final String EXCEPTION_CLASS = "$solvikException";
    /**
     * The generated adapter that implements bundled native bindings.
     *
     * <p>It is a top-level type in the generated {@code solvik} package, emitted only when a program
     * reaches a binding that needs it. The name contains {@code $}, so no Solvik identifier can
     * spell it and no user type can collide with it; it is not a runtime library.
     */
    public static final String IO_CLASS = "Solvik$Io";
    /** The generated adapter's source path and file name. */
    public static final String IO_SOURCE_PATH = "solvik/" + IO_CLASS + ".java";

    /** The binary name of the generated abort class, which the CLI recognizes (§13). */
    public static String errorBinaryName() {
        return PROGRAM_BINARY_NAME + "$" + ERROR_CLASS;
    }

    /** The binary name of the generated exit-status class, which the CLI recognizes (§7.3). */
    public static String exitBinaryName() {
        return PROGRAM_BINARY_NAME + "$" + EXIT_CLASS;
    }

    /** The binary name of the generated exception class, which the CLI recognizes (§7.4, §13). */
    public static String exceptionBinaryName() {
        return PROGRAM_BINARY_NAME + "$" + EXCEPTION_CLASS;
    }
    /** Java keywords and literals, which cannot name a generated declaration. */
    private static final Set<String> JAVA_KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
            "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while",
            "true", "false", "null", "_", "var", "yield", "record");

    /**
     * The support methods {@code GeneratedIntrinsics} emits into the program class.
     *
     * <p>A Solvik function that lowers into that class must not take one of these names: a matching
     * signature would duplicate the support member, and a compatible overload would capture the
     * support code's own calls. {@code SupportMemberNamesTest} keeps this set equal to the registry,
     * so a new support member cannot be added without reserving its name here.
     */
    private static final Set<String> SUPPORT_MEMBER_NAMES = Set.of(
            "array", "checked", "concat", "conversion", "divide", "equal", "equalFloat",
            "equalFloating", "equalIntegral", "exit", "fail", "hash", "identity",
            "isDecimalText", "isFloatingText", "isIntegerText", "isScalar", "plainText", "render",
            "remainder", "required", "textIndex", "textSplit", "textSubstring", "toBigDecimal",
            "toBigInteger", "toBoolean", "toByte", "toDouble", "toFloat", "toInteger", "toLong",
            "toShort", "type");

    private GeneratedNames() { }

    /**
     * A Solvik identifier as a legal Java identifier.
     *
     * <p>Solvik identifiers may spell a Java keyword ({@code var short : 1}), so generated code
     * appends {@code $}, which no Solvik identifier can contain and therefore can never collide.
     */
    public static String javaIdentifier(String name) {
        return JAVA_KEYWORDS.contains(name) ? name + "$" : name;
    }

    /** Solvik module path to Java package; the reserved root {@code java} becomes {@code solvik.java}. */
    public static String javaPackage(String modulePath) {
        String dotted = modulePath.replace("::", ".");
        if (dotted.equals("java") || dotted.startsWith("java.")) return "solvik." + dotted;
        return dotted;
    }

    public static String javaClassName(String qualifiedName) {
        int separator = qualifiedName.lastIndexOf("::");
        return separator < 0 ? GLOBAL_PACKAGE + "."
                + javaIdentifier(qualifiedName)
                : javaPackage(qualifiedName.substring(0, separator)) + "."
                        + javaIdentifier(qualifiedName.substring(separator + 2));
    }

    public static String sourcePath(String qualifiedName) {
        int separator = qualifiedName.lastIndexOf("::");
        return separator < 0 ? javaIdentifier(qualifiedName) + ".java"
                : javaPackage(qualifiedName.substring(0, separator)).replace('.', '/') + "/"
                        + javaIdentifier(qualifiedName.substring(separator + 2)) + ".java";
    }

    /**
     * Static functions must not hide Object's instance contract methods or the program class's
     * support members. '$' is source-inexpressible, so the suffixed name cannot collide with a
     * Solvik declaration of its own.
     */
    public static String functionName(String name) {
        String identifier = javaIdentifier(name);
        return switch (name) {
            case "equals", "hashCode", "toString" -> identifier + "$function";
            default -> SUPPORT_MEMBER_NAMES.contains(name) ? identifier + "$function" : identifier;
        };
    }

    /** The support member names a Solvik function may not take; exposed for the registry test. */
    static Set<String> supportMemberNames() {
        return SUPPORT_MEMBER_NAMES;
    }

    /** Source path of a module's generated function holder. */
    public static String moduleFunctionsPath(String module) {
        return javaPackage(module).replace('.', '/') + "/" + FUNCTIONS_CLASS + ".java";
    }

    public static String moduleFunctions(String module) {
        return javaPackage(module) + "." + FUNCTIONS_CLASS;
    }
}
