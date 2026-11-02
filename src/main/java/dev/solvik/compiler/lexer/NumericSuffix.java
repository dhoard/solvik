package dev.solvik.compiler.lexer;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * An uppercase suffix that fixes the type of a numeric literal ({@code LANGUAGE.md} §4).
 *
 * <p>A suffix names one numeric carrier: {@code 1B} is a {@code Byte} literal exactly as
 * {@code Byte(1)} is a {@code Byte} value. {@link #NONE} means the literal has no suffix, so the
 * checker keeps its default type or the element type of an enclosing typed array literal.
 *
 * <p>Suffixes are written in uppercase. The spelled forms are kept here so the lexer and its
 * diagnostics cannot disagree about them.
 */
public enum NumericSuffix {

    NONE(""),
    BYTE("B"),
    SHORT("S"),
    INTEGER("I"),
    LONG("L"),
    FLOAT("F"),
    DOUBLE("D"),
    BIG_INTEGER("BI"),
    BIG_DECIMAL("BD");

    private final String text;

    NumericSuffix(String text) {
        this.text = text;
    }

    /** The spelled suffix, or the empty string for {@link #NONE}. */
    public String text() {
        return text;
    }

    /** Whether the literal actually carries a suffix. */
    public boolean isExplicit() {
        return this != NONE;
    }

    /** The suffix spelled {@code text}, or {@code null} when no suffix is spelled that way. */
    public static NumericSuffix of(String text) {
        for (NumericSuffix suffix : values()) {
            if (suffix != NONE && suffix.text.equals(text)) {
                return suffix;
            }
        }
        return null;
    }

    /** The valid spellings, for diagnostics: {@code B, S, I, L, F, D, BI, BD}. */
    public static String spellings() {
        return Arrays.stream(values())
                .filter(NumericSuffix::isExplicit)
                .map(NumericSuffix::text)
                .collect(Collectors.joining(", "));
    }
}
