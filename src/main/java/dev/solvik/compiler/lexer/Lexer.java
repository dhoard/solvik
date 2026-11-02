package dev.solvik.compiler.lexer;

import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.diagnostic.SourcePos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns Solvik source into a token list in a single pass.
 *
 * <p>Rules implemented here come from {@code LANGUAGE.md}:
 * <ul>
 *   <li>§9 — a newline or {@code ;} terminates a statement; a newline is canceled when the next
 *       non-blank line begins with {@code \}; comments are whitespace, so a comment-only line counts
 *       as blank and a comment may precede the continuation marker.</li>
 *   <li>§9 "Comments" — {@code //} to end of line, and {@code /* ... *}{@code /} blocks that nest.</li>
 *   <li>§3 — literal forms: integer, fractional, quoted string with a closed escape set, and
 *       {@code r#"..."#} raw strings that preserve content exactly.</li>
 *   <li>§4 — a numeric literal may carry an uppercase suffix ({@code 1L}, {@code 2BD}) that fixes
 *       its type; see {@link NumericSuffix}.</li>
 * </ul>
 *
 * <p>The lexer always emits {@code NEWLINE} tokens outside strings and block comments and lets the
 * parser decide whether a newline is significant for the construct in progress: inside an array
 * literal or argument list it is skipped, and between statements it terminates.
 *
 * <p>This class is not thread-safe; create one per compilation.
 */
public final class Lexer {

    // Diagnostic codes are stable; tests assert on them rather than on message text.
    public static final String UNEXPECTED_CHARACTER = "SOL-L001";
    public static final String UNTERMINATED_STRING = "SOL-L002";
    public static final String UNKNOWN_ESCAPE = "SOL-L003";
    public static final String UNTERMINATED_RAW_STRING = "SOL-L004";
    public static final String UNTERMINATED_BLOCK_COMMENT = "SOL-L005";
    public static final String MISPLACED_CONTINUATION = "SOL-L006";
    public static final String INVALID_NUMERIC_SUFFIX = "SOL-L007";

    private static final Map<String, TokenKind> KEYWORDS = Map.ofEntries(
            Map.entry("var", TokenKind.VAR),
            Map.entry("type", TokenKind.TYPE),
            Map.entry("trait", TokenKind.TRAIT),
            Map.entry("implements", TokenKind.IMPLEMENTS),
            Map.entry("func", TokenKind.FUNC),
            Map.entry("if", TokenKind.IF),
            Map.entry("else", TokenKind.ELSE),
            Map.entry("while", TokenKind.WHILE),
            Map.entry("for", TokenKind.FOR),
            Map.entry("in", TokenKind.IN),
            Map.entry("return", TokenKind.RETURN),
            Map.entry("true", TokenKind.TRUE),
            Map.entry("false", TokenKind.FALSE),
            Map.entry("Null", TokenKind.NULL),
            Map.entry("null", TokenKind.NULL_LOWER),
            Map.entry("switch", TokenKind.SWITCH),
            Map.entry("case", TokenKind.CASE),
            Map.entry("regex", TokenKind.REGEX),
            Map.entry("default", TokenKind.DEFAULT),
            Map.entry("outer", TokenKind.OUTER),
            Map.entry("break", TokenKind.BREAK),
            Map.entry("continue", TokenKind.CONTINUE),
            Map.entry("pub", TokenKind.PUB),
            Map.entry("module", TokenKind.MODULE),
            Map.entry("import", TokenKind.IMPORT),
            Map.entry("native", TokenKind.NATIVE),
            Map.entry("mut", TokenKind.MUT),
            Map.entry("static", TokenKind.STATIC),
            Map.entry("try", TokenKind.TRY),
            Map.entry("catch", TokenKind.CATCH),
            Map.entry("finally", TokenKind.FINALLY),
            Map.entry("raise", TokenKind.RAISE));

    private final String source;
    private final Diagnostics diagnostics;
    private final String file;
    private final List<Token> tokens = new ArrayList<>();
    private int index;
    private int line = 1;
    private int column = 1;

    public Lexer(String source, Diagnostics diagnostics) {
        this(source, diagnostics, null);
    }

    /**
     * @param file the file this source came from, recorded in every position so a multi-file
     *             program can attribute each diagnostic (§3, "Source files")
     */
    public Lexer(String source, Diagnostics diagnostics, String file) {
        this.source = source;
        this.diagnostics = diagnostics;
        this.file = file;
    }

    /** Lexes the whole source, ending with an {@code EOF} token. */
    public List<Token> tokenize() {
        while (!atEnd()) {
            char c = peek();
            if (c == ' ' || c == '\t' || c == '\r') {
                advance();
            } else if (c == '\n') {
                newline();
            } else if (c == '/' && peek(1) == '/') {
                lineComment();
            } else if (c == '/' && peek(1) == '*') {
                blockComment();
            } else if (c == '"') {
                stringLiteral();
            } else if (isRawStringStart()) {
                rawStringLiteral();
            } else if (isDigit(c)) {
                number();
            } else if (isIdentifierStart(c)) {
                identifierOrKeyword();
            } else {
                operatorOrError();
            }
        }
        tokens.add(Token.of(TokenKind.EOF, "", pos()));
        return List.copyOf(tokens);
    }

    // ---------------------------------------------------------------- newlines

    private void newline() {
        if (tryContinuation()) {
            return; // newline canceled and the '\' consumed; no NEWLINE token
        }
        SourcePos p = pos(); // position of the newline itself, not of the next line
        advance();
        emitNewline(p);
    }

    private void emitNewline(SourcePos p) {
        if (tokens.isEmpty() || tokens.getLast().kind() != TokenKind.NEWLINE) {
            tokens.add(Token.of(TokenKind.NEWLINE, "\n", p));
        }
    }

    /**
     * Decides whether the newline at the current index is canceled by a continuation line.
     *
     * <p>Scans past blank lines and comments to the next significant character. When that character
     * is {@code \}, it is consumed as the marker and the scan position is kept; otherwise the scan
     * is rolled back and the caller emits a normal newline.
     */
    private boolean tryContinuation() {
        int savedIndex = index;
        int savedLine = line;
        int savedColumn = column;
        advance(); // step over the '\n'
        while (!atEnd()) {
            char c = peek();
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                advance();
            } else if (c == '/' && peek(1) == '/') {
                skipLineComment();
            } else if (c == '/' && peek(1) == '*') {
                if (!skipBlockCommentQuietly()) {
                    break; // unterminated: let the main loop report it
                }
            } else if (c == '\\') {
                advance(); // consume the marker
                return true;
            } else {
                break;
            }
        }
        index = savedIndex;
        line = savedLine;
        column = savedColumn;
        return false;
    }

    // ----------------------------------------------------------------- comments

    private void lineComment() {
        skipLineComment();
    }

    private void skipLineComment() {
        while (!atEnd() && peek() != '\n') {
            advance();
        }
    }

    private void blockComment() {
        SourcePos start = pos();
        if (!skipBlockCommentQuietly()) {
            diagnostics.error(UNTERMINATED_BLOCK_COMMENT, start, "unterminated block comment");
        }
    }

    /** Consumes a nested block comment; returns {@code false} when the source ends inside it. */
    private boolean skipBlockCommentQuietly() {
        advance(); // '/'
        advance(); // '*'
        int depth = 1;
        while (!atEnd()) {
            if (peek() == '/' && peek(1) == '*') {
                advance();
                advance();
                depth++;
            } else if (peek() == '*' && peek(1) == '/') {
                advance();
                advance();
                if (--depth == 0) {
                    return true;
                }
            } else {
                advance();
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ strings

    private void stringLiteral() {
        SourcePos start = pos();
        int startIndex = index;
        advance(); // opening quote
        StringBuilder value = new StringBuilder();
        boolean terminated = false;
        while (!atEnd()) {
            char c = peek();
            if (c == '\n') {
                break; // ordinary strings may not span lines (§3 literal rule, §9)
            }
            if (c == '"') {
                advance();
                terminated = true;
                break;
            }
            if (c == '\\') {
                advance();
                if (atEnd()) {
                    break;
                }
                char escaped = peek();
                advance();
                switch (escaped) {
                    case '\\' -> value.append('\\');
                    case '"' -> value.append('"');
                    case 'n' -> value.append('\n');
                    case 't' -> value.append('\t');
                    case 'r' -> value.append('\r');
                    case '0' -> value.append('\0');
                    default -> diagnostics.error(UNKNOWN_ESCAPE, start,
                            "unknown escape '\\" + escaped + "' in string literal");
                }
                continue;
            }
            value.append(c);
            advance();
        }
        if (!terminated) {
            diagnostics.error(UNTERMINATED_STRING, start, "unterminated string literal");
        }
        tokens.add(new Token(TokenKind.STRING_LITERAL,
                source.substring(startIndex, index), value.toString(), start));
    }

    /** True when the source at the cursor is {@code r}, zero or more {@code #}, then {@code "}. */
    private boolean isRawStringStart() {
        if (peek() != 'r') {
            return false;
        }
        int j = index + 1;
        while (j < source.length() && source.charAt(j) == '#') {
            j++;
        }
        return j < source.length() && source.charAt(j) == '"';
    }

    private void rawStringLiteral() {
        SourcePos start = pos();
        int startIndex = index;
        advance(); // 'r'
        int hashes = 0;
        while (peek() == '#') {
            advance();
            hashes++;
        }
        advance(); // opening quote
        StringBuilder value = new StringBuilder();
        boolean terminated = false;
        while (!atEnd()) {
            if (peek() == '"' && hashesFollow(hashes)) {
                advance(); // closing quote
                for (int k = 0; k < hashes; k++) {
                    advance();
                }
                terminated = true;
                break;
            }
            value.append(peek()); // no escape processing; newlines preserved
            advance();
        }
        if (!terminated) {
            diagnostics.error(UNTERMINATED_RAW_STRING, start, "unterminated raw string literal");
        }
        tokens.add(new Token(TokenKind.STRING_LITERAL,
                source.substring(startIndex, index), value.toString(), start));
    }

    private boolean hashesFollow(int count) {
        for (int k = 1; k <= count; k++) {
            int at = index + k;
            if (at >= source.length() || source.charAt(at) != '#') {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ numbers

    private void number() {
        SourcePos start = pos();
        int startIndex = index;
        while (isDigit(peek())) {
            advance();
        }
        TokenKind kind = TokenKind.INT_LITERAL;
        // A '.' starts a fractional part only when a digit follows, so `1..2` lexes as 1, '..', 2.
        if (peek() == '.' && isDigit(peek(1))) {
            kind = TokenKind.DECIMAL_LITERAL;
            advance();
            while (isDigit(peek())) {
                advance();
            }
        }
        // The suffix is reported apart from the lexeme, so every later stage keeps reading digits.
        String digits = source.substring(startIndex, index);
        NumericSuffix suffix = numericSuffix();
        tokens.add(Token.of(kind, digits, suffix, start));
    }

    /**
     * Reads the uppercase suffix of a numeric literal (§4), if one follows.
     *
     * <p>The whole run of uppercase letters is the suffix, so {@code 1BI} is a {@code BigInteger}
     * and never {@code 1B} followed by an identifier. An unknown run is reported here rather than
     * left to the parser, which would otherwise complain about a token two positions later. A
     * lowercase spelling of a valid suffix is a case error, and the run is consumed so the mistake
     * reports exactly once.
     */
    private NumericSuffix numericSuffix() {
        SourcePos start = pos();
        int upper = runLength(true);
        if (upper > 0) {
            String text = source.substring(index, index + upper);
            advance(upper);
            NumericSuffix suffix = NumericSuffix.of(text);
            if (suffix == null) {
                diagnostics.error(INVALID_NUMERIC_SUFFIX, start, "unknown numeric suffix '" + text
                        + "'; valid suffixes are " + NumericSuffix.spellings());
                return NumericSuffix.NONE;
            }
            return suffix;
        }
        int lower = runLength(false);
        if (lower > 0) {
            String text = source.substring(index, index + lower);
            NumericSuffix suffix = NumericSuffix.of(text.toUpperCase(Locale.ROOT));
            if (suffix != null) {
                advance(lower);
                diagnostics.error(INVALID_NUMERIC_SUFFIX, start,
                        "a numeric suffix is uppercase: write '" + suffix.text() + "' rather than '"
                                + text + "'");
                return suffix;
            }
        }
        return NumericSuffix.NONE;
    }

    /** The length of the run of uppercase ({@code upper}) or lowercase letters at the cursor. */
    private int runLength(boolean upper) {
        int length = 0;
        while (true) {
            char c = peek(length);
            if (upper ? c >= 'A' && c <= 'Z' : c >= 'a' && c <= 'z') {
                length++;
            } else {
                return length;
            }
        }
    }

    // --------------------------------------------------------------- identifiers

    private void identifierOrKeyword() {
        SourcePos start = pos();
        int startIndex = index;
        advance();
        while (isIdentifierPart(peek())) {
            advance();
        }
        String text = source.substring(startIndex, index);
        TokenKind keyword = KEYWORDS.get(text);
        tokens.add(Token.of(keyword != null ? keyword : TokenKind.IDENTIFIER, text, start));
    }

    // ---------------------------------------------------------------- operators

    private void operatorOrError() {
        SourcePos start = pos();
        char c = peek();
        switch (c) {
            case '=' -> {
                if (peek(1) == '=' && peek(2) == '=') {
                    advance(3);
                    add(TokenKind.EQ_IDENTITY, start);
                } else if (peek(1) == '=') {
                    advance(2);
                    add(TokenKind.EQ, start);
                } else {
                    advance();
                    add(TokenKind.ASSIGN, start);
                }
            }
            case '!' -> {
                if (peek(1) == '=') {
                    advance(2);
                    add(TokenKind.NOT_EQ, start);
                } else {
                    advance();
                    add(TokenKind.BANG, start);
                }
            }
            case '<' -> {
                if (peek(1) == '=') {
                    advance(2);
                    add(TokenKind.LE, start);
                } else {
                    advance();
                    add(TokenKind.LT, start);
                }
            }
            case '>' -> {
                if (peek(1) == '=') {
                    advance(2);
                    add(TokenKind.GE, start);
                } else {
                    advance();
                    add(TokenKind.GT, start);
                }
            }
            case '&' -> binary(start, '&', TokenKind.AND_AND);
            case '|' -> binary(start, '|', TokenKind.OR_OR);
            case '.' -> {
                if (peek(1) == '.' && peek(2) == '=') {
                    advance(3);
                    add(TokenKind.CONCAT_ASSIGN, start);
                } else if (peek(1) == '.') {
                    advance(2);
                    add(TokenKind.CONCAT, start);
                } else {
                    advance();
                    add(TokenKind.DOT, start);
                }
            }
            case '-' -> {
                if (peek(1) == '>') {
                    advance(2);
                    add(TokenKind.ARROW, start);
                } else {
                    advance();
                    add(TokenKind.MINUS, start);
                }
            }
            case '+' -> single(TokenKind.PLUS, start);
            case '*' -> single(TokenKind.STAR, start);
            case '/' -> single(TokenKind.SLASH, start);
            case '%' -> single(TokenKind.PERCENT, start);
            case '(' -> single(TokenKind.LPAREN, start);
            case ')' -> single(TokenKind.RPAREN, start);
            case '[' -> single(TokenKind.LBRACKET, start);
            case ']' -> single(TokenKind.RBRACKET, start);
            case '{' -> single(TokenKind.LBRACE, start);
            case '}' -> single(TokenKind.RBRACE, start);
            case ',' -> single(TokenKind.COMMA, start);
            case ':' -> {
                if (peek(1) == ':') {
                    advance(2);
                    add(TokenKind.COLON_COLON, start);
                } else if (peek(1) == '=') {
                    advance(2);
                    add(TokenKind.DECLARE, start);
                } else {
                    single(TokenKind.COLON, start);
                }
            }
            case '?' -> single(TokenKind.QUESTION, start);
            case ';' -> single(TokenKind.SEMICOLON, start);
            case '\\' -> {
                diagnostics.error(MISPLACED_CONTINUATION, start,
                        "'\\' continues a statement only at the start of a line following a newline");
                advance();
            }
            default -> {
                diagnostics.error(UNEXPECTED_CHARACTER, start,
                        "unexpected character '" + c + "'");
                advance();
            }
        }
    }

    private void binary(SourcePos start, char second, TokenKind doubled) {
        if (peek(1) == second) {
            advance(2);
            add(doubled, start);
        } else {
            diagnostics.error(UNEXPECTED_CHARACTER, start,
                    "unexpected character '" + peek() + "'");
            advance();
        }
    }

    private void single(TokenKind kind, SourcePos start) {
        advance();
        add(kind, start);
    }

    private void add(TokenKind kind, SourcePos start) {
        tokens.add(Token.of(kind, kind.fixedText(), start));
    }

    // ------------------------------------------------------------------ helpers

    private boolean atEnd() {
        return index >= source.length();
    }

    private char peek() {
        return index < source.length() ? source.charAt(index) : '\0';
    }

    private char peek(int ahead) {
        int at = index + ahead;
        return at < source.length() ? source.charAt(at) : '\0';
    }

    private char advance() {
        char c = source.charAt(index++);
        if (c == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
        return c;
    }

    private void advance(int count) {
        for (int k = 0; k < count; k++) {
            advance();
        }
    }

    private SourcePos pos() {
        return new SourcePos(index, line, column, file);
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isIdentifierStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isIdentifierPart(char c) {
        return isIdentifierStart(c) || isDigit(c);
    }
}
