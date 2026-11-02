package dev.solvik.compiler.lexer;

import dev.solvik.compiler.diagnostic.SourcePos;

/**
 * One lexical token.
 *
 * @param kind   the token kind
 * @param lexeme the exact source text consumed, except a numeric literal's suffix: the lexeme holds
 *               the digits and {@link #suffix()} holds the suffix that followed them
 * @param value  decoded content for a {@code STRING_LITERAL}, otherwise {@code null}
 * @param suffix the uppercase suffix of an {@code INT_LITERAL} or {@code DECIMAL_LITERAL}, otherwise
 *               {@link NumericSuffix#NONE}
 * @param pos    position of the first character
 */
public record Token(TokenKind kind, String lexeme, String value, NumericSuffix suffix, SourcePos pos) {

    /** A token without a numeric suffix, which is every token but a suffixed literal. */
    public Token(TokenKind kind, String lexeme, String value, SourcePos pos) {
        this(kind, lexeme, value, NumericSuffix.NONE, pos);
    }

    public static Token of(TokenKind kind, String lexeme, SourcePos pos) {
        return new Token(kind, lexeme, null, NumericSuffix.NONE, pos);
    }

    public static Token of(TokenKind kind, String lexeme, NumericSuffix suffix, SourcePos pos) {
        return new Token(kind, lexeme, null, suffix, pos);
    }

    public boolean is(TokenKind expected) {
        return kind == expected;
    }

    @Override
    public String toString() {
        return kind + (lexeme.isEmpty() ? "" : "('" + lexeme + suffix.text() + "')");
    }
}
