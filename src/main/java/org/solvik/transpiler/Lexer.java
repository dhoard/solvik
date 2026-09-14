package org.solvik.transpiler;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Handwritten lexer. Newlines are retained because Solvik uses them as terminators. */
public final class Lexer {
    private static final Map<String, TokenKind> KEYWORDS = Map.ofEntries(
            Map.entry("package", TokenKind.PACKAGE), Map.entry("use", TokenKind.USE),
            Map.entry("struct", TokenKind.STRUCT), Map.entry("trait", TokenKind.TRAIT),
            Map.entry("enum", TokenKind.ENUM), Map.entry("extends", TokenKind.EXTENDS),
            Map.entry("implements", TokenKind.IMPLEMENTS), Map.entry("Self", TokenKind.SELF_TYPE),
            Map.entry("self", TokenKind.SELF), Map.entry("pub", TokenKind.PUB),
            Map.entry("func", TokenKind.FUNC), Map.entry("delegate", TokenKind.DELEGATE),
            Map.entry("to", TokenKind.TO), Map.entry("static", TokenKind.STATIC),
            Map.entry("var", TokenKind.VAR), Map.entry("match", TokenKind.MATCH),
            Map.entry("if", TokenKind.IF), Map.entry("else", TokenKind.ELSE),
            Map.entry("let", TokenKind.LET), Map.entry("while", TokenKind.WHILE),
            Map.entry("for", TokenKind.FOR), Map.entry("in", TokenKind.IN),
            Map.entry("switch", TokenKind.SWITCH), Map.entry("case", TokenKind.CASE),
            Map.entry("default", TokenKind.DEFAULT), Map.entry("try", TokenKind.TRY),
            Map.entry("catch", TokenKind.CATCH), Map.entry("finally", TokenKind.FINALLY),
            Map.entry("throw", TokenKind.THROW), Map.entry("return", TokenKind.RETURN),
            Map.entry("break", TokenKind.BREAK), Map.entry("continue", TokenKind.CONTINUE),
            Map.entry("true", TokenKind.TRUE), Map.entry("false", TokenKind.FALSE),
            Map.entry("null", TokenKind.NULL), Map.entry("is", TokenKind.IS));

    private final String file;
    private final String source;
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private int pos;
    private int line = 1;
    private int column = 1;

    public Lexer(String file, String source) {
        this.file = file;
        this.source = source;
    }

    public List<Token> tokenize() {
        List<Token> result = new ArrayList<>(Math.max(16, source.length() / 4));
        while (!atEnd()) {
            skipTrivia();
            if (atEnd()) break;
            int start = pos, startLine = line, startColumn = column;
            char c = advance();
            if (c == '\n') {
                result.add(token(TokenKind.NEWLINE, "\n", start, startLine, startColumn));
            } else if (isIdentStart(c)) {
                if (c == 'r' && (peek() == '"' || peek() == '#')) {
                    result.add(rawString(start, startLine, startColumn));
                } else {
                    while (isIdentPart(peek())) advance();
                    String text = source.substring(start, pos);
                    TokenKind kind = KEYWORDS.getOrDefault(text, TokenKind.IDENT);
                    if (kind == TokenKind.IDENT) {
                        if (text.equals("public")) error("L002", "'public' is no longer a Solvik keyword; use 'pub'", start, startLine, startColumn);
                        else if (text.equals("mutable")) error("L002", "'mutable' is no longer a Solvik keyword; use 'var'", start, startLine, startColumn);
                    }
                    result.add(token(kind, text, start, startLine, startColumn));
                }
            } else if (digit(c)) {
                result.add(number(start, startLine, startColumn));
            } else if (c == '"') {
                result.add(string(start, startLine, startColumn));
            } else if (c == '\'') {
                result.add(character(start, startLine, startColumn));
            } else {
                TokenKind kind = switch (c) {
                    case '(' -> TokenKind.LPAREN; case ')' -> TokenKind.RPAREN;
                    case '{' -> TokenKind.LBRACE; case '}' -> TokenKind.RBRACE;
                    case '[' -> TokenKind.LBRACKET; case ']' -> TokenKind.RBRACKET;
                    case ',' -> TokenKind.COMMA; case ':' -> TokenKind.COLON;
                    case ';' -> TokenKind.SEMICOLON; case '.' -> dotKind();
                    case '?' -> take('?') ? TokenKind.COALESCE : TokenKind.QUESTION_MARK;
                    case '=' -> take('=') ? TokenKind.EQ : (take('>') ? TokenKind.ARROW : TokenKind.ASSIGN);
                    case '!' -> take('=') ? TokenKind.NE : TokenKind.BANG;
                    case '<' -> take('=') ? TokenKind.LE : TokenKind.LT;
                    case '>' -> take('=') ? TokenKind.GE : TokenKind.GT;
                    case '+' -> take('=') ? TokenKind.PLUS_ASSIGN : TokenKind.PLUS;
                    case '-' -> take('=') ? TokenKind.MINUS_ASSIGN : TokenKind.MINUS;
                    case '*' -> take('=') ? TokenKind.STAR_ASSIGN : TokenKind.STAR;
                    case '/' -> take('=') ? TokenKind.SLASH_ASSIGN : TokenKind.SLASH;
                    case '%' -> take('=') ? TokenKind.PERCENT_ASSIGN : TokenKind.PERCENT;
                    case '&' -> take('&') ? TokenKind.AND : TokenKind.AMPERSAND;
                    case '|' -> take('|') ? TokenKind.OR : bad("unexpected character '|'; use '||'");
                    default -> bad("unexpected character '" + c + "'");
                };
                if (kind != null) result.add(token(kind, source.substring(start, pos), start, startLine, startColumn));
            }
        }
        result.add(new Token(TokenKind.EOF, "", new Span(file, pos, pos, line, column)));
        return result;
    }

    public List<Diagnostic> diagnostics() { return List.copyOf(diagnostics); }

    private Token rawString(int start, int startLine, int startColumn) {
        int hashes = 0;
        while (peek() == '#') { advance(); hashes++; }
        if (!take('"')) return badToken("malformed raw string", start, startLine, startColumn);
        StringBuilder value = new StringBuilder();
        while (!atEnd()) {
            char c = advance();
            if (c == '"') {
                int save = pos, saveLine = line, saveColumn = column;
                int found = 0;
                while (found < hashes && peek() == '#') { advance(); found++; }
                if (found == hashes) return token(TokenKind.STRING, value.toString(), start, startLine, startColumn);
                pos = save; line = saveLine; column = saveColumn;
            }
            value.append(c);
        }
        error("L001", "unterminated raw string literal", start, startLine, startColumn);
        return token(TokenKind.STRING, value.toString(), start, startLine, startColumn);
    }

    private Token string(int start, int startLine, int startColumn) {
        StringBuilder value = new StringBuilder();
        while (!atEnd()) {
            char c = advance();
            if (c == '"') return token(TokenKind.STRING, value.toString(), start, startLine, startColumn);
            if (c == '\\') {
                String escaped = escape(start, startLine, startColumn);
                if (escaped != null) value.append(escaped);
            } else if (c == '\n' || c == '\r') {
                error("L001", "newline in string literal", start, startLine, startColumn);
                break;
            } else value.append(c);
        }
        error("L001", "unterminated string literal", start, startLine, startColumn);
        return token(TokenKind.STRING, value.toString(), start, startLine, startColumn);
    }

    private Token character(int start, int startLine, int startColumn) {
        String value;
        if (take('\\')) value = escape(start, startLine, startColumn);
        else if (!atEnd()) value = Character.toString(advance());
        else value = null;
        if (!take('\'')) error("L001", "unterminated char literal", start, startLine, startColumn);
        if (value == null) value = "?";
        return token(TokenKind.CHAR, value, start, startLine, startColumn);
    }

    private String escape(int start, int startLine, int startColumn) {
        if (atEnd()) { error("L001", "unterminated escape sequence", start, startLine, startColumn); return null; }
        char c = advance();
        return switch (c) {
            case 'n' -> "\n"; case 't' -> "\t"; case 'r' -> "\r"; case '0' -> "\0";
            case '\\' -> "\\"; case '"' -> "\""; case '\'' -> "'";
            case 'x' -> codePoint(readHex(2, start, startLine, startColumn), start, startLine, startColumn);
            case 'u' -> {
                int n = take('{') ? readBracedHex(start, startLine, startColumn) : readHex(4, start, startLine, startColumn);
                yield codePoint(n, start, startLine, startColumn);
            }
            case 'U' -> codePoint(readHex(8, start, startLine, startColumn), start, startLine, startColumn);
            default -> { error("L001", "unknown escape sequence \\" + c + "'", start, startLine, startColumn); yield null; }
        };
    }

    private int readBracedHex(int start, int startLine, int startColumn) {
        int value = 0, count = 0;
        while (!atEnd() && peek() != '}') {
            int digit = Character.digit(advance(), 16);
            if (digit < 0 || ++count > 6) { error("L001", "invalid braced unicode escape", start, startLine, startColumn); return -1; }
            value = value * 16 + digit;
        }
        if (count == 0 || !take('}')) error("L001", "invalid braced unicode escape", start, startLine, startColumn);
        return value;
    }

    private int readHex(int count, int start, int startLine, int startColumn) {
        int value = 0;
        for (int i = 0; i < count; i++) {
            if (atEnd()) { error("L001", "incomplete unicode escape", start, startLine, startColumn); return -1; }
            int digit = Character.digit(advance(), 16);
            if (digit < 0) { error("L001", "invalid unicode escape", start, startLine, startColumn); return -1; }
            value = value * 16 + digit;
        }
        return value;
    }

    private String codePoint(int value, int start, int startLine, int startColumn) {
        if (value < 0) return null;
        try { return new String(Character.toChars(value)); }
        catch (IllegalArgumentException e) { error("L001", "invalid Unicode scalar value", start, startLine, startColumn); return null; }
    }

    private Token number(int start, int startLine, int startColumn) {
        boolean real = false;
        if (source.charAt(start) == '0' && (peek() == 'x' || peek() == 'X' || peek() == 'o' || peek() == 'O' || peek() == 'b' || peek() == 'B')) {
            advance();
            while (Character.digit(peek(), source.charAt(start) == '0' && source.charAt(start + 1) == 'x' ? 16 : 10) >= 0 || peek() == '_') advance();
        } else {
            while (digit(peek()) || peek() == '_') advance();
            if (peek() == '.' && digit(peek(1))) { real = true; advance(); while (digit(peek()) || peek() == '_') advance(); }
            if (peek() == 'e' || peek() == 'E') { real = true; advance(); if (peek() == '+' || peek() == '-') advance(); int exponentStart = pos; while (digit(peek()) || peek() == '_') advance(); if (pos == exponentStart) error("L001", "malformed floating-point literal", start, startLine, startColumn); }
        }
        if (peek() == 'f' || peek() == 'F' || peek() == 'd' || peek() == 'D') { real = true; advance(); }
        else if ((peek() == 'b' || peek() == 'B') && (peek(1) == 'd' || peek(1) == 'D')) { real = true; advance(); advance(); }
        return token(real ? TokenKind.REAL : TokenKind.INT, source.substring(start, pos), start, startLine, startColumn);
    }

    private TokenKind dotKind() {
        if (!take('.')) return TokenKind.DOT;
        if (take('.')) return TokenKind.SPREAD;
        if (take('=')) return TokenKind.RANGE_INCLUSIVE;
        return TokenKind.RANGE;
    }

    private void skipTrivia() {
        while (!atEnd()) {
            if (peek() == ' ' || peek() == '\t' || peek() == '\r') { advance(); continue; }
            if (peek() == '/' && peek(1) == '/') { while (!atEnd() && peek() != '\n') advance(); continue; }
            if (peek() == '/' && peek(1) == '*') { blockComment(); continue; }
            break;
        }
    }

    private void blockComment() {
        int start = pos, sl = line, sc = column; advance(); advance(); int depth = 1;
        while (!atEnd() && depth > 0) {
            if (peek() == '/' && peek(1) == '*') { advance(); advance(); depth++; }
            else if (peek() == '*' && peek(1) == '/') { advance(); advance(); depth--; }
            else advance();
        }
        if (depth != 0) error("L001", "unterminated block comment", start, sl, sc);
    }

    private char peek() { return peek(0); }
    private char peek(int offset) { return pos + offset < source.length() ? source.charAt(pos + offset) : '\0'; }
    private char advance() { char c = source.charAt(pos++); if (c == '\n') { line++; column = 1; } else column++; return c; }
    private boolean take(char c) { if (peek() != c) return false; advance(); return true; }
    private boolean atEnd() { return pos >= source.length(); }
    private boolean digit(char c) { return (c >= '0' && c <= '9') || (c > 127 && Character.isDigit(c)); }
    private boolean isIdentStart(char c) { return c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c > 127 && Character.isLetter(c)); }
    private boolean isIdentPart(char c) { return c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || (c > 127 && Character.isLetterOrDigit(c)); }
    private Token token(TokenKind kind, String text, int start, int startLine, int startColumn) { return new Token(kind, text, new Span(file, start, pos, startLine, startColumn)); }
    private Token badToken(String message, int start, int sl, int sc) { error("L001", message, start, sl, sc); return token(TokenKind.STRING, "", start, sl, sc); }
    private TokenKind bad(String message) { error("L001", message, pos - 1, line, Math.max(1, column - 1)); return null; }
    private void error(String code, String message, int at, int atLine, int atColumn) { diagnostics.add(new Diagnostic(code, message, new Span(file, at, Math.min(source.length(), at + 1), atLine, atColumn))); }
}
