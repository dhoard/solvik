package bootstrap

// Token model used by the first Solvik-native frontend bootstrap.
// The frontend keeps source positions explicit so diagnostics can be emitted
// without depending on an implementation-language parser.

pub enum TokenKind {
    Identifier
    Number
    String
    Character
    Symbol
    Newline
    End
    Error
}

pub struct Token {
    pub kind: String,
    pub text: String,
    pub line: Int,
    pub column: Int,
}

pub struct TokenStream {
    pub tokens: Map<Int, Token>,
    pub count: Int,
}

pub func token(kind: String, text: String, line: Int, column: Int) -> Token {
    return Token { kind: kind, text: text, line: line, column: column }
}
