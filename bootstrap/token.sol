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
    pub kind: string,
    pub text: string,
    pub line: int,
    pub column: int,
}

pub struct TokenStream {
    pub tokens: map<int, Token>,
    pub count: int,
}

pub func token(kind: string, text: string, line: int, column: int) -> Token {
    return Token { kind: kind, text: text, line: line, column: column }
}
