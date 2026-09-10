//! Solvik lexer. Emits tokens with spans and explicit newline tokens so the
//! parser can implement optional-semicolon / newline statement termination.

use crate::diagnostic::Diagnostics;
use crate::source::Span;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TokenKind {
    Eof,
    Newline,
    Ident,
    IntLit,
    FloatLit,
    StringLit,
    QuestionMark,
    CharLit,
    // keywords
    Module,
    Use,
    Class,
    Interface,
    Enum,
    Extends,
    Implements,
    SelfKw,
    SelfV,
    Super,
    Override,
    Public,
    Private,
    Protected,
    Static,
    Mutable,
    Match,
    If,
    Else,
    Let,
    While,
    For,
    In,
    Switch,
    Case,
    Default,
    Try,
    Catch,
    Finally,
    Throw,
    Return,
    Break,
    Continue,
    True,
    False,
    Null,
    Is,
    // punctuation / operators
    LParen,
    RParen,
    LBrace,
    RBrace,
    LBracket,
    RBracket,
    Lt,
    Gt,
    Comma,
    Dot,
    DotDot,
    DotDotDot,
    Colon,
    Semicolon,
    Assign,
    EqEq,
    NotEq,
    LtEq,
    GtEq,
    Bang,
    Plus,
    Minus,
    Star,
    Slash,
    Percent,
    Ampersand,
    PlusEq,
    MinusEq,
    StarEq,
    SlashEq,
    PercentEq,
    Question,
    AndAnd,
    OrOr,
    FatArrow,
}

impl TokenKind {
    pub fn keyword(name: &str) -> Option<TokenKind> {
        Some(match name {
            "module" => TokenKind::Module,
            "use" => TokenKind::Use,
            "class" => TokenKind::Class,
            "interface" => TokenKind::Interface,
            "enum" => TokenKind::Enum,
            "extends" => TokenKind::Extends,
            "implements" => TokenKind::Implements,
            "Self" => TokenKind::SelfKw,
            "self" => TokenKind::SelfV,
            "super" => TokenKind::Super,
            "override" => TokenKind::Override,
            "public" => TokenKind::Public,
            "private" => TokenKind::Private,
            "protected" => TokenKind::Protected,
            "static" => TokenKind::Static,
            "mutable" => TokenKind::Mutable,
            "match" => TokenKind::Match,
            "if" => TokenKind::If,
            "else" => TokenKind::Else,
            "let" => TokenKind::Let,
            "while" => TokenKind::While,
            "for" => TokenKind::For,
            "in" => TokenKind::In,
            "switch" => TokenKind::Switch,
            "case" => TokenKind::Case,
            "default" => TokenKind::Default,
            "try" => TokenKind::Try,
            "catch" => TokenKind::Catch,
            "finally" => TokenKind::Finally,
            "throw" => TokenKind::Throw,
            "return" => TokenKind::Return,
            "break" => TokenKind::Break,
            "continue" => TokenKind::Continue,
            "true" => TokenKind::True,
            "false" => TokenKind::False,
            "null" => TokenKind::Null,
            "is" => TokenKind::Is,
            _ => return None,
        })
    }

    /// True when a newline directly after this token may be skipped because
    /// the expression continues on the next line.
    pub fn continues_expression(self) -> bool {
        matches!(
            self,
            TokenKind::Plus
                | TokenKind::Minus
                | TokenKind::Star
                | TokenKind::Slash
                | TokenKind::Percent
                | TokenKind::Lt
                | TokenKind::Gt
                | TokenKind::LtEq
                | TokenKind::GtEq
                | TokenKind::EqEq
                | TokenKind::NotEq
                | TokenKind::Bang
                | TokenKind::Ampersand
                | TokenKind::Comma
                | TokenKind::LParen
                | TokenKind::LBrace
                | TokenKind::Assign
                | TokenKind::PlusEq
                | TokenKind::MinusEq
                | TokenKind::StarEq
                | TokenKind::SlashEq
                | TokenKind::PercentEq
                | TokenKind::Colon
                | TokenKind::Dot
                | TokenKind::DotDot
                | TokenKind::AndAnd
                | TokenKind::OrOr
                | TokenKind::FatArrow
        )
    }

    /// True when this token may start a continuation of an expression on the
    /// next line (operator-first continuation).
    pub fn starts_continuation(self) -> bool {
        matches!(
            self,
            TokenKind::Plus
                | TokenKind::Minus
                | TokenKind::Star
                | TokenKind::Slash
                | TokenKind::Percent
                | TokenKind::Lt
                | TokenKind::Gt
                | TokenKind::LtEq
                | TokenKind::GtEq
                | TokenKind::EqEq
                | TokenKind::NotEq
                | TokenKind::Ampersand
                | TokenKind::Dot
                | TokenKind::DotDot
                | TokenKind::AndAnd
                | TokenKind::OrOr
        )
    }
}

#[derive(Debug, Clone)]
pub struct Token {
    pub kind: TokenKind,
    pub text: String,
    pub span: Span,
}

impl Token {
    fn new(kind: TokenKind, text: String, span: Span) -> Self {
        Token { kind, text, span }
    }
}

pub struct Lexer<'a> {
    file: u32,
    src: &'a [u8],
    pos: usize,
    line: u32,
}

impl<'a> Lexer<'a> {
    pub fn new(file: u32, src: &'a str) -> Self {
        Lexer {
            file,
            src: src.as_bytes(),
            pos: 0,
            line: 1,
        }
    }

    fn peek(&self) -> Option<u8> {
        self.src.get(self.pos).copied()
    }

    fn peek_at(&self, off: usize) -> Option<u8> {
        self.src.get(self.pos + off).copied()
    }

    fn bump(&mut self) -> Option<u8> {
        let b = self.peek()?;
        self.pos += 1;
        if b == b'\n' {
            self.line += 1;
        }
        Some(b)
    }

    fn span_from(&self, start: usize) -> Span {
        Span::new(self.file, start as u32, self.pos as u32)
    }

    fn err(&self, diags: &mut Diagnostics, msg: &str, at: usize) {
        diags.err_at(
            "L001",
            msg,
            Span::new(self.file, at as u32, (at + 1).max(at) as u32),
        );
    }

    pub fn tokenize(mut self, diags: &mut Diagnostics) -> Vec<Token> {
        let mut out = Vec::new();
        loop {
            self.skip_trivia(&mut out, diags);
            if self.pos >= self.src.len() {
                break;
            }
            let start = self.pos;
            let b = self.bump().unwrap();
            match b {
                b' ' | b'\t' | b'\r' => {}
                b'\n' => out.push(Token::new(
                    TokenKind::Newline,
                    "\n".into(),
                    self.span_from(start),
                )),
                b'/' if self.peek() == Some(b'/') => self.skip_line_comment(),
                b'/' if self.peek() == Some(b'*') => self.skip_block_comment(diags),
                b'r' if self.peek() == Some(b'"') => {
                    let quote_start = self.pos;
                    self.bump(); // consume '"'
                    self.lex_string_body(quote_start, true, &mut out, diags)
                }
                b'a'..=b'z' | b'A'..=b'Z' | b'_' => self.lex_ident(start, &mut out),
                b'0'..=b'9' => self.lex_number(start, &mut out, diags),
                b'"' => self.lex_string(start, false, &mut out, diags),
                b'\'' => self.lex_char(start, &mut out, diags),
                b'(' => out.push(Token::new(
                    TokenKind::LParen,
                    "(".into(),
                    self.span_from(start),
                )),
                b')' => out.push(Token::new(
                    TokenKind::RParen,
                    ")".into(),
                    self.span_from(start),
                )),
                b'{' => out.push(Token::new(
                    TokenKind::LBrace,
                    "{".into(),
                    self.span_from(start),
                )),
                b'}' => out.push(Token::new(
                    TokenKind::RBrace,
                    "}".into(),
                    self.span_from(start),
                )),
                b'[' => out.push(Token::new(
                    TokenKind::LBracket,
                    "[".into(),
                    self.span_from(start),
                )),
                b']' => out.push(Token::new(
                    TokenKind::RBracket,
                    "]".into(),
                    self.span_from(start),
                )),
                b',' => out.push(Token::new(
                    TokenKind::Comma,
                    ",".into(),
                    self.span_from(start),
                )),
                b';' => out.push(Token::new(
                    TokenKind::Semicolon,
                    ";".into(),
                    self.span_from(start),
                )),
                b':' => out.push(Token::new(
                    TokenKind::Colon,
                    ":".into(),
                    self.span_from(start),
                )),
                b'.' => {
                    if self.peek() == Some(b'.') {
                        self.bump();
                        if self.peek() == Some(b'.') {
                            self.bump();
                            out.push(Token::new(
                                TokenKind::DotDotDot,
                                "...".into(),
                                self.span_from(start),
                            ));
                        } else {
                            out.push(Token::new(
                                TokenKind::DotDot,
                                "..".into(),
                                self.span_from(start),
                            ));
                        }
                    } else {
                        out.push(Token::new(
                            TokenKind::Dot,
                            ".".into(),
                            self.span_from(start),
                        ));
                    }
                }
                b'?' => {
                    if self.peek() == Some(b'?') {
                        self.bump();
                        out.push(Token::new(
                            TokenKind::Question,
                            "??".into(),
                            self.span_from(start),
                        ));
                    } else {
                        out.push(Token::new(
                            TokenKind::QuestionMark,
                            "?".into(),
                            self.span_from(start),
                        ));
                    }
                }
                b'=' => {
                    if self.peek() == Some(b'=') {
                        self.bump();
                        out.push(Token::new(
                            TokenKind::EqEq,
                            "==".into(),
                            self.span_from(start),
                        ));
                    } else if self.peek() == Some(b'>') {
                        self.bump();
                        out.push(Token::new(
                            TokenKind::FatArrow,
                            "=>".into(),
                            self.span_from(start),
                        ));
                    } else {
                        out.push(Token::new(
                            TokenKind::Assign,
                            "=".into(),
                            self.span_from(start),
                        ));
                    }
                }
                b'!' => {
                    if self.peek() == Some(b'=') {
                        self.bump();
                        out.push(Token::new(
                            TokenKind::NotEq,
                            "!=".into(),
                            self.span_from(start),
                        ));
                    } else {
                        out.push(Token::new(
                            TokenKind::Bang,
                            "!".into(),
                            self.span_from(start),
                        ));
                    }
                }
                b'<' => {
                    if self.peek() == Some(b'=') {
                        self.bump();
                        out.push(Token::new(
                            TokenKind::LtEq,
                            "<=".into(),
                            self.span_from(start),
                        ));
                    } else {
                        out.push(Token::new(TokenKind::Lt, "<".into(), self.span_from(start)));
                    }
                }
                b'>' => {
                    if self.peek() == Some(b'=') {
                        self.bump();
                        out.push(Token::new(
                            TokenKind::GtEq,
                            ">=".into(),
                            self.span_from(start),
                        ));
                    } else {
                        out.push(Token::new(TokenKind::Gt, ">".into(), self.span_from(start)));
                    }
                }
                b'+' => {
                    if self.peek() == Some(b'=') {
                        self.bump();
                        out.push(Token::new(
                            TokenKind::PlusEq,
                            "+=".into(),
                            self.span_from(start),
                        ));
                    } else {
                        out.push(Token::new(
                            TokenKind::Plus,
                            "+".into(),
                            self.span_from(start),
                        ));
                    }
                }
                b'*' => {
                    if self.peek() == Some(b'=') {
                        self.bump();
                        out.push(Token::new(
                            TokenKind::StarEq,
                            "*=".into(),
                            self.span_from(start),
                        ));
                    } else {
                        out.push(Token::new(
                            TokenKind::Star,
                            "*".into(),
                            self.span_from(start),
                        ));
                    }
                }
                b'%' => {
                    if self.peek() == Some(b'=') {
                        self.bump();
                        out.push(Token::new(
                            TokenKind::PercentEq,
                            "%=".into(),
                            self.span_from(start),
                        ));
                    } else {
                        out.push(Token::new(
                            TokenKind::Percent,
                            "%".into(),
                            self.span_from(start),
                        ));
                    }
                }
                b'-' => {
                    if self.peek() == Some(b'=') {
                        self.bump();
                        out.push(Token::new(
                            TokenKind::MinusEq,
                            "-=".into(),
                            self.span_from(start),
                        ));
                    } else if self.peek() == Some(b'>') {
                        self.bump();
                        self.err(diags, "arrow return syntax '->' is not supported; use ': Type' after the parameter list", start);
                        out.push(Token::new(
                            TokenKind::RParen,
                            "->".into(),
                            self.span_from(start),
                        ));
                    } else {
                        out.push(Token::new(
                            TokenKind::Minus,
                            "-".into(),
                            self.span_from(start),
                        ));
                    }
                }
                b'/' => {
                    if self.peek() == Some(b'=') {
                        self.bump();
                        out.push(Token::new(
                            TokenKind::SlashEq,
                            "/=".into(),
                            self.span_from(start),
                        ));
                    } else {
                        out.push(Token::new(
                            TokenKind::Slash,
                            "/".into(),
                            self.span_from(start),
                        ));
                    }
                }
                b'&' => {
                    if self.peek() == Some(b'&') {
                        self.bump();
                        out.push(Token::new(
                            TokenKind::AndAnd,
                            "&&".into(),
                            self.span_from(start),
                        ));
                    } else {
                        out.push(Token::new(
                            TokenKind::Ampersand,
                            "&".into(),
                            self.span_from(start),
                        ));
                    }
                }
                b'|' => {
                    if self.peek() == Some(b'|') {
                        self.bump();
                        out.push(Token::new(
                            TokenKind::OrOr,
                            "||".into(),
                            self.span_from(start),
                        ));
                    } else {
                        self.err(
                            diags,
                            "unexpected character '|'; use '||' for logical or",
                            start,
                        );
                    }
                }
                other => {
                    let ch = std::str::from_utf8(&self.src[start..])
                        .ok()
                        .and_then(|s| s.chars().next())
                        .unwrap_or(other as char);
                    self.pos = start + ch.len_utf8();
                    self.err(diags, &format!("unexpected character {:?}", ch), start);
                }
            }
        }
        out.push(Token::new(
            TokenKind::Eof,
            String::new(),
            Span::new(self.file, self.src.len() as u32, self.src.len() as u32),
        ));
        out
    }

    fn skip_trivia(&mut self, out: &mut Vec<Token>, diags: &mut Diagnostics) {
        loop {
            match self.peek() {
                Some(b' ') | Some(b'\t') | Some(b'\r') => {
                    self.bump();
                }
                Some(b'/') if self.peek_at(1) == Some(b'/') => self.skip_line_comment(),
                Some(b'/') if self.peek_at(1) == Some(b'*') => self.skip_block_comment(diags),
                _ => break,
            }
        }
        let _ = out;
    }

    fn skip_line_comment(&mut self) {
        while let Some(b) = self.peek() {
            if b == b'\n' {
                break;
            }
            self.bump();
        }
    }

    fn skip_block_comment(&mut self, diags: &mut Diagnostics) {
        let start = self.pos;
        self.bump();
        self.bump();
        let mut depth = 1u32;
        while depth > 0 {
            match self.bump() {
                None => {
                    self.err(diags, "unterminated block comment", start);
                    return;
                }
                Some(b'/') if self.peek() == Some(b'*') => {
                    self.bump();
                    depth += 1;
                }
                Some(b'*') if self.peek() == Some(b'/') => {
                    self.bump();
                    depth -= 1;
                }
                _ => {}
            }
        }
    }

    fn lex_ident(&mut self, start: usize, out: &mut Vec<Token>) {
        while matches!(
            self.peek(),
            Some(b'0'..=b'9' | b'a'..=b'z' | b'A'..=b'Z' | b'_')
        ) {
            self.bump();
        }
        let text = std::str::from_utf8(&self.src[start..self.pos])
            .unwrap()
            .to_string();
        let kind = TokenKind::keyword(&text).unwrap_or(TokenKind::Ident);
        out.push(Token::new(kind, text, self.span_from(start)));
    }

    fn lex_number(&mut self, start: usize, out: &mut Vec<Token>, diags: &mut Diagnostics) {
        // Note: the main loop already consumed the first digit; the scan
        // below decides whether this literal is a float.
        let mut float = false;
        if self.peek() == Some(b'x') || self.peek() == Some(b'o') || self.peek() == Some(b'b') {
            let radix = match self.bump().unwrap() {
                b'x' => 16,
                b'o' => 8,
                _ => 2,
            };
            let digits_start = self.pos;
            while let Some(b) = self.peek() {
                if b == b'_' || (b as char).is_digit(radix) {
                    self.bump();
                } else {
                    break;
                }
            }
            if self.pos == digits_start {
                self.err(diags, "no digits after radix prefix", start);
            }
            let text = std::str::from_utf8(&self.src[start..self.pos]).unwrap();
            let value = i64::from_str_radix(&text[2..], radix).unwrap_or(0);
            out.push(Token::new(
                TokenKind::IntLit,
                text.to_string(),
                self.span_from(start),
            ));
            let _ = value;
            return;
        }
        while let Some(b) = self.peek() {
            if b.is_ascii_digit() || b == b'_' {
                self.bump();
            } else if b == b'.' && self.peek_at(1).is_some_and(|n| n.is_ascii_digit()) {
                float = true;
                self.bump();
            } else if (b == b'e' || b == b'E')
                && matches!(self.peek_at(1), Some(b'0'..=b'9' | b'+' | b'-'))
            {
                float = true;
                self.bump();
                if matches!(self.peek(), Some(b'+' | b'-')) {
                    self.bump();
                }
            } else {
                break;
            }
        }
        // An explicit float suffix (`1f`, `2.5F`) forces float typing.
        if matches!(self.peek(), Some(b'f' | b'F'))
            && !self
                .peek_at(1)
                .is_some_and(|c| c.is_ascii_alphabetic() || c == b'_')
        {
            self.bump();
            float = true;
        }
        let text = std::str::from_utf8(&self.src[start..self.pos])
            .unwrap()
            .to_string();
        let clean: String = text
            .chars()
            .filter(|c| *c != '_' && *c != 'f' && *c != 'F')
            .collect();
        if float {
            let v: f64 = clean.parse().unwrap_or(0.0);
            out.push(Token::new(TokenKind::FloatLit, text, self.span_from(start)));
            let _ = v;
        } else {
            out.push(Token::new(TokenKind::IntLit, text, self.span_from(start)));
        }
    }

    fn escape_value(&mut self, diags: &mut Diagnostics) -> Option<char> {
        let c = self.bump()?;
        match c {
            b'n' => Some('\n'),
            b't' => Some('\t'),
            b'r' => Some('\r'),
            b'0' => Some('\0'),
            b'\\' => Some('\\'),
            b'"' => Some('"'),
            b'\'' => Some('\''),
            b'x' => {
                let h = match self.hex_digit() {
                    Some(d) => d,
                    None => {
                        self.err(diags, "invalid hex escape '\\x'", self.pos);
                        return None;
                    }
                };
                let l = match self.hex_digit() {
                    Some(d) => d,
                    None => {
                        self.err(diags, "invalid hex escape '\\x'", self.pos);
                        return None;
                    }
                };
                char::from_u32((h * 16 + l) as u32)
            }
            b'u' => {
                let mut v: u32 = 0;
                for _ in 0..4 {
                    match self.hex_digit() {
                        Some(d) => v = v * 16 + d as u32,
                        None => {
                            self.err(diags, "invalid unicode escape '\\u'", self.pos);
                            return None;
                        }
                    }
                }
                match char::from_u32(v) {
                    Some(c) => Some(c),
                    None => {
                        self.err(
                            diags,
                            &format!("invalid unicode code point U+{:04X}", v),
                            self.pos - 4,
                        );
                        None
                    }
                }
            }
            b'U' => {
                let mut v: u32 = 0;
                for _ in 0..8 {
                    match self.hex_digit() {
                        Some(d) => v = v * 16 + d as u32,
                        None => {
                            self.err(diags, "invalid unicode escape '\\U'", self.pos);
                            return None;
                        }
                    }
                }
                match char::from_u32(v) {
                    Some(c) => Some(c),
                    None => {
                        self.err(
                            diags,
                            &format!("invalid unicode code point U+{:08X}", v),
                            self.pos - 8,
                        );
                        None
                    }
                }
            }
            other => {
                self.err(
                    diags,
                    &format!("unknown escape sequence '\\{}'", other as char),
                    self.pos - 1,
                );
                None
            }
        }
    }

    fn hex_digit(&mut self) -> Option<u8> {
        let c = self.bump()?;
        Some((c as char).to_digit(16)? as u8)
    }

    fn lex_string(
        &mut self,
        start: usize,
        raw: bool,
        out: &mut Vec<Token>,
        diags: &mut Diagnostics,
    ) {
        // The opening quote was already consumed by the main loop.
        self.lex_string_body(start, raw, out, diags)
    }

    fn lex_string_body(
        &mut self,
        start: usize,
        raw: bool,
        out: &mut Vec<Token>,
        diags: &mut Diagnostics,
    ) {
        let mut value = String::new();
        loop {
            match self.bump() {
                None => {
                    self.err(diags, "unterminated string literal", start);
                    break;
                }
                Some(b'"') => break,
                Some(b'\\') if !raw => {
                    if let Some(c) = self.escape_value(diags) {
                        value.push(c)
                    }
                }
                Some(b) => {
                    if b < 0x80 {
                        value.push(b as char);
                    } else {
                        // multi-byte UTF-8: copy bytes
                        let byte_start = self.pos - 1;
                        let mut len = 1usize;
                        let lead = b;
                        let extra = if lead >= 0xF0 {
                            3
                        } else if lead >= 0xE0 {
                            2
                        } else {
                            1
                        };
                        for _ in 0..extra {
                            if self.peek().is_some_and(|c| (0x80..0xC0).contains(&c)) {
                                self.bump();
                                len += 1;
                            } else {
                                break;
                            }
                        }
                        let s = std::str::from_utf8(&self.src[byte_start..byte_start + len])
                            .unwrap_or("?");
                        value.push_str(s);
                    }
                }
            }
        }
        out.push(Token::new(
            TokenKind::StringLit,
            value,
            self.span_from(start),
        ));
    }

    fn lex_char(&mut self, start: usize, out: &mut Vec<Token>, diags: &mut Diagnostics) {
        // The opening quote was already consumed by the main loop.
        let c = if self.peek() == Some(b'\\') {
            self.bump();
            self.escape_value(diags)
        } else {
            let b = match self.bump() {
                Some(b) => b,
                None => {
                    self.err(diags, "unterminated char literal", start);
                    return;
                }
            };
            if b < 0x80 {
                Some(b as char)
            } else {
                let byte_start = self.pos - 1;
                let extra = if b >= 0xF0 {
                    3
                } else if b >= 0xE0 {
                    2
                } else {
                    1
                };
                for _ in 0..extra {
                    if self.peek().is_some_and(|c| (0x80..0xC0).contains(&c)) {
                        self.bump();
                    }
                }
                std::str::from_utf8(&self.src[byte_start..self.pos])
                    .ok()
                    .and_then(|s| s.chars().next())
            }
        };
        if self.bump() != Some(b'\'') {
            self.err(diags, "unterminated char literal", start);
        }
        match c {
            Some(ch) => out.push(Token::new(
                TokenKind::CharLit,
                ch.to_string(),
                self.span_from(start),
            )),
            None => out.push(Token::new(
                TokenKind::CharLit,
                "?".into(),
                self.span_from(start),
            )),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::diagnostic::Diagnostics;

    #[test]
    fn invalid_unicode_escapes_diagnose() {
        for src in ["\"\\uD800\"", "\"\\uZZZZ\"", "\"\\x1\"", "\"\\U00110000\""] {
            let mut diags = Diagnostics::default();
            Lexer::new(0, src).tokenize(&mut diags);
            assert!(!diags.items.is_empty(), "no diagnostic for {src}");
        }
    }

    #[test]
    fn valid_unicode_escapes() {
        let mut diags = Diagnostics::default();
        let toks = Lexer::new(0, "\"\\u0041\\x42\\U0001F600\"").tokenize(&mut diags);
        assert!(diags.items.is_empty());
        assert_eq!(toks[0].text, "AB\u{1F600}");
    }

    #[test]
    fn tokens_for_range() {
        let src = "1..5";
        let mut diags = Diagnostics::default();
        let toks = Lexer::new(0, src).tokenize(&mut diags);
        for t in &toks {
            eprintln!("{:?} {:?}", t.kind, t.text);
        }
        assert!(diags.items.is_empty());
    }

    #[test]
    fn tokens_for_interface() {
        let src = "module p\n\ninterface Foo {\n    foo(): Long\n}\n";
        let mut diags = Diagnostics::default();
        let toks = Lexer::new(0, src).tokenize(&mut diags);
        for t in &toks {
            eprintln!("{:?} {:?}", t.kind, t.text);
        }
        assert!(diags.items.is_empty());
    }
    #[test]
    fn unexpected_unicode_is_one_readable_diagnostic() {
        let mut diags = Diagnostics::default();
        Lexer::new(0, "é").tokenize(&mut diags);
        assert_eq!(diags.items.len(), 1);
        assert!(diags.items[0].message.contains('é'));
    }
}
