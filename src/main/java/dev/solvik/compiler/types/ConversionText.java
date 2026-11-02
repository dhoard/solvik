package dev.solvik.compiler.types;

/**
 * The text grammar each conversion accepts (LANGUAGE.md §4, "Conversion").
 *
 * <p>These rules are the compiler's half of the contract: the checker applies them to literal
 * arguments so a conversion that cannot work is a compile error, and the generated runtime applies
 * the same rules to computed text. Keeping the two in step is the point of naming them here, and
 * {@code ConversionTextTest} asserts that both halves agree.
 *
 * <p>No form accepts surrounding whitespace, thousands separators, or underscores; nothing is
 * trimmed, because a conversion either reads the text it was given or fails.
 */
public final class ConversionText {

    private ConversionText() {
    }

    /** {@code [+-]?[0-9]+}: an arbitrary-precision decimal integer, and nothing else. */
    public static boolean isInteger(String text) {
        int index = sign(text);
        int start = index;
        while (index < text.length() && isDigit(text.charAt(index))) {
            index++;
        }
        return index > start && index == text.length();
    }

    /**
     * {@code [+-]?([0-9]+(\.[0-9]+)?|\.[0-9]+)([eE][+-]?[0-9]+)?}: a decimal number.
     *
     * <p>A dot needs digits on both sides — {@code "5."} and {@code "."} are rejected — and an
     * exponent is optional.
     */
    public static boolean isDecimal(String text) {
        int index = sign(text);
        int wholeStart = index;
        while (index < text.length() && isDigit(text.charAt(index))) {
            index++;
        }
        boolean whole = index > wholeStart;
        if (index < text.length() && text.charAt(index) == '.') {
            index++;
            int fractionStart = index;
            while (index < text.length() && isDigit(text.charAt(index))) {
                index++;
            }
            if (index == fractionStart) {
                return false; // "5." and "." have no fraction digits
            }
        } else if (!whole) {
            return false;
        }
        if (index < text.length() && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
            index++;
            if (index < text.length() && (text.charAt(index) == '+' || text.charAt(index) == '-')) {
                index++;
            }
            int exponentStart = index;
            while (index < text.length() && isDigit(text.charAt(index))) {
                index++;
            }
            if (index == exponentStart) {
                return false; // "1e" has no exponent
            }
        }
        return index == text.length();
    }

    /** Exactly {@code true} or {@code false}, in lower case. */
    public static boolean isBoolean(String text) {
        return "true".equals(text) || "false".equals(text);
    }

    private static int sign(String text) {
        return !text.isEmpty() && (text.charAt(0) == '+' || text.charAt(0) == '-') ? 1 : 0;
    }

    private static boolean isDigit(char character) {
        return character >= '0' && character <= '9';
    }
}
