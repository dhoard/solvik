package org.solvik.transpiler;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused unit tests for the handwritten {@link Lexer}, with an emphasis on
 * numeric literal scanning: radix prefixes, decimal and floating-point forms,
 * suffixes, exponents, and underscore separators.
 */
final class LexerTests {
    private LexerTests() {}

    // -- Radix integers ----------------------------------------------------

    @Test
    void decimalIntegerLiteralsAreScanned() {
        for (String literal : List.of("0", "1", "42", "1_000_000")) {
            assertLiteral(literal, TokenKind.INT, literal);
        }
    }

    @Test
    void hexadecimalIntegerLiteralsAreScanned() {
        for (String literal : List.of("0x0", "0xff", "0xFF", "0Xff", "0XFF", "0xCAFE_BABE")) {
            assertLiteral(literal, TokenKind.INT, literal);
        }
    }

    @Test
    void octalIntegerLiteralsAreScanned() {
        for (String literal : List.of("0o0", "0o755", "0O755", "0o7_5_5")) {
            assertLiteral(literal, TokenKind.INT, literal);
        }
    }

    @Test
    void binaryIntegerLiteralsAreScanned() {
        for (String literal : List.of("0b0", "0b1010", "0B1010", "0b1010_0101")) {
            assertLiteral(literal, TokenKind.INT, literal);
        }
    }

    @Test
    void radixLiteralsDoNotFallBackToDecimalDigits() {
        // A radix prefix must reject digits that only exist in a larger radix
        // instead of scanning them as decimal digits.
        assertRejected("0b102", "invalid digit in binary");
        assertRejected("0b2", "malformed binary");
        assertRejected("0o8", "malformed octal");
        assertRejected("0o9", "malformed octal");
        assertRejected("0xG", "malformed hexadecimal");
        assertRejected("0x12G", "invalid digit in hexadecimal");
    }

    @Test
    void malformedRadixLiteralsRemainASingleToken() {
        for (String literal : List.of("0b102", "0b2", "0o8", "0o9", "0xG", "0x12G")) {
            List<Token> tokens = tokenize(literal);
            assertEquals(1, tokens.size() - 1, () -> literal + " was split into " + tokens);
            assertEquals(TokenKind.INT, tokens.get(0).kind(), literal);
        }
    }

    // -- Decimal and floating point ---------------------------------------

    @Test
    void decimalFloatingPointLiteralsAreScanned() {
        for (String literal : List.of("1.0", "1.5", "1_000.25", "1e3", "1E3", "1e+3", "1e-3",
                "1.25e10", "1.25E-10")) {
            assertLiteral(literal, TokenKind.REAL, literal);
        }
    }

    @Test
    void numericSuffixesSelectRealLiterals() {
        for (String literal : List.of("1f", "1F", "1.5f", "1.5F",
                "1d", "1D", "1.5d", "1.5D",
                "1bd", "1BD", "1.5bd", "1.5BD")) {
            assertLiteral(literal, TokenKind.REAL, literal);
        }
    }

    @Test
    void malformedExponentsAreRejected() {
        for (String literal : List.of("1e", "1E", "1e+", "1e-")) {
            assertRejected(literal, "malformed floating-point literal");
        }
    }

    @Test
    void decimalPointNotFollowedByDigitIsMemberAccess() {
        // A decimal point that is not followed by a digit is the member-access
        // operator, not the start of a fraction.
        List<Token> tokens = tokenize("1.foo");
        assertEquals(3, tokens.size() - 1, tokens.toString());
        assertEquals(TokenKind.INT, tokens.get(0).kind());
        assertEquals(TokenKind.DOT, tokens.get(1).kind());
        assertEquals(TokenKind.IDENT, tokens.get(2).kind());
    }

    // -- Underscore separators --------------------------------------------

    @Test
    void underscoreSeparatorsAreAllowedBetweenDigits() {
        assertLiteral("1_000", TokenKind.INT, "1_000");
        assertLiteral("0xCAFE_BABE", TokenKind.INT, "0xCAFE_BABE");
        assertLiteral("0b1010_0101", TokenKind.INT, "0b1010_0101");
        assertLiteral("0o7_5_5", TokenKind.INT, "0o7_5_5");
        assertLiteral("1_000.25", TokenKind.REAL, "1_000.25");
        assertLiteral("1.25e1_0", TokenKind.REAL, "1.25e1_0");
    }

    @Test
    void misplacedUnderscoreSeparatorsAreRejected() {
        for (String literal : List.of("1_", "1__0", "0x_ff", "0b_1010", "0o_7",
                "1e_3", "1_f", "1_.0", "1._5")) {
            assertRejected(literal, "digit separators must appear between two digits");
        }
    }

    @Test
    void underscoreDiagnosticPointsAtTheSeparator() {
        Diagnostic diagnostic = firstDiagnostic("0x_ff");
        assertEquals(1, diagnostic.span().line());
        assertEquals(3, diagnostic.span().column(), diagnostic.toString());
        assertEquals("L001", diagnostic.code());
    }

    @Test
    void malformedLiteralDiagnosticDoesNotBleedIntoFollowingToken() {
        List<Token> tokens = tokenize("0x12G + 1");
        assertEquals(TokenKind.INT, tokens.get(0).kind());
        assertEquals("0x12G", tokens.get(0).text());
        assertEquals(TokenKind.PLUS, tokens.get(1).kind());
        assertEquals("1", tokens.get(2).text());
    }

    // -- Helpers -----------------------------------------------------------

    private static void assertLiteral(String source, TokenKind kind, String text) {
        Lexer lexer = new Lexer("test.sol", source);
        List<Token> tokens = lexer.tokenize();
        assertTrue(lexer.diagnostics().isEmpty(), () -> source + " produced diagnostics: " + lexer.diagnostics());
        assertEquals(1, tokens.size() - 1, () -> source + " produced extra tokens: " + tokens);
        assertEquals(kind, tokens.get(0).kind(), source);
        assertEquals(text, tokens.get(0).text(), source);
    }

    private static List<Token> tokenize(String source) {
        Lexer lexer = new Lexer("test.sol", source);
        List<Token> tokens = lexer.tokenize();
        assertEquals(TokenKind.EOF, tokens.get(tokens.size() - 1).kind(), "lexer output must end with EOF");
        return tokens;
    }

    private static Diagnostic firstDiagnostic(String source) {
        Lexer lexer = new Lexer("test.sol", source);
        lexer.tokenize();
        assertTrue(!lexer.diagnostics().isEmpty(), () -> source + " produced no diagnostics");
        return lexer.diagnostics().get(0);
    }

    private static void assertRejected(String source, String messagePart) {
        Lexer lexer = new Lexer("test.sol", source);
        lexer.tokenize();
        boolean matched = lexer.diagnostics().stream()
                .anyMatch(d -> d.code().equals("L001") && d.message().contains(messagePart));
        assertTrue(matched, () -> source + ": expected L001 containing '" + messagePart
                + "', got " + lexer.diagnostics());
    }
}
