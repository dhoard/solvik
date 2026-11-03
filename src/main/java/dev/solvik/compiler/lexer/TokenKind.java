package dev.solvik.compiler.lexer;

/** Every token kind produced by {@link Lexer}. */
public enum TokenKind {

    // Literals and names
    IDENTIFIER,
    INT_LITERAL,
    DECIMAL_LITERAL,
    STRING_LITERAL,

    // Keywords (LANGUAGE.md §2 "Reserved words", §3 "Control flow")
    /** Removed by §13.1; kept so the parser can name the replacement for the old spelling. */
    VAR("var"),
    TYPE("type"),
    TRAIT("trait"),
    IMPLEMENTS("implements"),
    FUNC("func"),
    IF("if"),
    ELSE("else"),
    WHILE("while"),
    FOR("for"),
    IN("in"),
    RETURN("return"),
    TRUE("true"),
    FALSE("false"),
    NULL("Null"),
    /** Removed by §4.1; kept so the parser can point at {@code Null}. */
    NULL_LOWER("null"),
    SWITCH("switch"),
    CASE("case"),
    REGEX("regex"),
    DEFAULT("default"),
    OUTER("outer"),
    BREAK("break"),
    CONTINUE("continue"),
    PUB("pub"),
    MODULE("module"),
    /** Removed in favor of {@code include} and {@code alias}; kept so the parser can name them. */
    IMPORT("import"),
    INCLUDE("include"),
    ALIAS("alias"),
    NATIVE("native"),
    MUT("mut"),
    STATIC("static"),
    TRY("try"),
    CATCH("catch"),
    FINALLY("finally"),
    RAISE("raise"),
    SYNC("sync"),

    // Operators and punctuation (LANGUAGE.md §4 "Declarations", §4.3 precedence)
    ASSIGN("="),
    DECLARE(":="),
    QUESTION("?"),
    EQ("=="),
    EQ_IDENTITY("==="),
    NOT_EQ("!="),
    LT("<"),
    LE("<="),
    GT(">"),
    GE(">="),
    PLUS("+"),
    MINUS("-"),
    STAR("*"),
    SLASH("/"),
    PERCENT("%"),
    AND_AND("&&"),
    OR_OR("||"),
    BANG("!"),
    CONCAT(".."),
    CONCAT_ASSIGN("..="),
    ARROW("->"),
    DOT("."),
    COMMA(","),
    COLON(":"),
    COLON_COLON("::"),
    SEMICOLON(";"),
    LPAREN("("),
    RPAREN(")"),
    LBRACKET("["),
    RBRACKET("]"),
    LBRACE("{"),
    RBRACE("}"),

    // Structural
    NEWLINE,
    EOF;

    private final String fixedText;

    TokenKind() {
        this.fixedText = null;
    }

    TokenKind(String fixedText) {
        this.fixedText = fixedText;
    }

    /** The exact source text for a fixed token, or {@code null} for variable-text kinds. */
    public String fixedText() {
        return fixedText;
    }

    public boolean isKeyword() {
        return fixedText != null && ordinal() >= VAR.ordinal() && ordinal() <= SYNC.ordinal();
    }
}
