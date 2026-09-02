//! Native lexer for the frozen semantic language surface.
//!
//! This is intentionally separate from the legacy bytecode lexer while the
//! Phase 10 frontend is being ported. It preserves newline tokens, Unicode
//! character literals, nested comments, raw strings, and the complete token
//! vocabulary needed by the semantic parser.

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Position {
    pub line: usize,
    pub column: usize,
}

#[derive(Clone, Debug, PartialEq)]
pub enum TokenKind {
    Eof,
    Newline,
    Semi,
    Ident,
    Int,
    Float,
    String,
    Char,
    Bool,
    Null,
    Keyword(&'static str),
    Operator(&'static str),
    Delimiter(char),
}

#[derive(Clone, Debug, PartialEq)]
pub enum Literal {
    Int(i64),
    Float(f64),
    String(String),
    Char(char),
    Bool(bool),
    Null,
    None,
}

#[derive(Clone, Debug, PartialEq)]
pub struct Token {
    pub kind: TokenKind,
    pub text: String,
    pub literal: Literal,
    pub position: Position,
}

#[derive(Clone, Debug, PartialEq)]
pub struct LexError {
    pub position: Position,
    pub message: String,
}

pub fn lex(source: &str) -> Result<Vec<Token>, LexError> {
    Lexer { source, offset: 0, line: 1, column: 1 }.run()
}

struct Lexer<'a> {
    source: &'a str,
    offset: usize,
    line: usize,
    column: usize,
}

impl<'a> Lexer<'a> {
    fn position(&self) -> Position { Position { line: self.line, column: self.column } }
    fn rest(&self) -> &'a str { &self.source[self.offset..] }

    fn bump(&mut self) -> Option<char> {
        let ch = self.rest().chars().next()?;
        self.offset += ch.len_utf8();
        if ch == '\n' { self.line += 1; self.column = 1; } else { self.column += 1; }
        Some(ch)
    }

    fn starts(&self, text: &str) -> bool { self.rest().starts_with(text) }

    fn take(&mut self, text: &str) -> bool {
        if !self.starts(text) { return false; }
        for _ in text.chars() { self.bump(); }
        true
    }

    fn error<T>(&self, message: impl Into<String>) -> Result<T, LexError> {
        Err(LexError { position: self.position(), message: message.into() })
    }

    fn unicode_escape(&mut self, width: usize, position: Position) -> Result<char, LexError> {
        let mut digits = String::new();
        for _ in 0..width {
            let Some(ch) = self.rest().chars().next() else { return Err(LexError { position, message: "incomplete hexadecimal escape".into() }); };
            if !ch.is_ascii_hexdigit() { return Err(LexError { position, message: "invalid hexadecimal escape".into() }); }
            digits.push(ch); self.bump();
        }
        let value = u32::from_str_radix(&digits, 16).map_err(|_| LexError { position, message: "invalid hexadecimal escape".into() })?;
        char::from_u32(value).ok_or_else(|| LexError { position, message: "hexadecimal escape is not a Unicode scalar value".into() })
    }

    fn run(mut self) -> Result<Vec<Token>, LexError> {
        let mut tokens = Vec::new();
        loop {
            let token = self.next()?;
            let end = token.kind == TokenKind::Eof;
            tokens.push(token);
            if end { return Ok(tokens); }
        }
    }

    fn next(&mut self) -> Result<Token, LexError> {
        loop {
            match self.rest().chars().next() {
                None => return Ok(Token { kind: TokenKind::Eof, text: String::new(), literal: Literal::None, position: self.position() }),
                Some(' ' | '\t' | '\r') => { self.bump(); }
                Some('\n') => {
                    let position = self.position(); self.bump();
                    return Ok(Token { kind: TokenKind::Newline, text: "\n".into(), literal: Literal::None, position });
                }
                Some(_) if self.starts("//") => { while !matches!(self.rest().chars().next(), None | Some('\n')) { self.bump(); } }
                Some(_) if self.starts("/*") => self.block_comment()?,
                _ => break,
            }
        }

        let position = self.position();
        let ch = self.rest().chars().next().unwrap();
        if ch == 'r' && (self.rest().chars().nth(1) == Some('"') || self.rest().chars().nth(1) == Some('#')) {
            if let Some(value) = self.raw_string()? {
                return Ok(Token { kind: TokenKind::String, text: value.clone(), literal: Literal::String(value), position });
            }
        }
        if ch.is_ascii_alphabetic() || ch == '_' {
            let start = self.offset;
            self.bump();
            while self.rest().chars().next().is_some_and(|c| c.is_ascii_alphanumeric() || c == '_') { self.bump(); }
            let text = self.source[start..self.offset].to_string();
            let (kind, literal) = match text.as_str() {
                "true" => (TokenKind::Bool, Literal::Bool(true)), "false" => (TokenKind::Bool, Literal::Bool(false)),
                "null" => (TokenKind::Null, Literal::Null),
                "package" | "use" | "struct" | "trait" | "enum" | "func" | "mut" | "pub" |
                "if" | "else" | "while" | "for" | "in" | "switch" | "case" | "default" |
                "try" | "catch" | "finally" | "throw" | "return" | "break" | "continue" =>
                    (TokenKind::Keyword(Box::leak(text.clone().into_boxed_str())), Literal::None),
                _ => (TokenKind::Ident, Literal::None),
            };
            return Ok(Token { kind, text, literal, position });
        }
        if ch.is_ascii_digit() { return self.number(position); }
        if ch == '"' || ch == '\'' { return self.quoted(position, ch); }

        for (text, kind) in [
            ("...", TokenKind::Operator("...")), ("??", TokenKind::Operator("??")), ("..", TokenKind::Operator("..")),
            ("->", TokenKind::Operator("->")), ("==", TokenKind::Operator("==")), ("!=", TokenKind::Operator("!=")),
            ("<=", TokenKind::Operator("<=")), (">=", TokenKind::Operator(">=")), ("&&", TokenKind::Operator("&&")),
            ("||", TokenKind::Operator("||")), ("<<", TokenKind::Operator("<<")), (">>", TokenKind::Operator(">>")),
        ] {
            if self.take(text) { return Ok(Token { kind, text: text.into(), literal: Literal::None, position }); }
        }
        let kind = match ch {
            ';' => TokenKind::Semi,
            '(' | ')' | '{' | '}' | '[' | ']' | ',' | ':' | '.' | '?' => TokenKind::Delimiter(ch),
            '+' | '-' | '*' | '/' | '%' | '!' | '~' | '&' | '|' | '^' | '<' | '>' | '=' => TokenKind::Operator(Box::leak(ch.to_string().into_boxed_str())),
            _ => return self.error(format!("unexpected character {:?}", ch)),
        };
        self.bump();
        Ok(Token { kind, text: ch.to_string(), literal: Literal::None, position })
    }

    fn block_comment(&mut self) -> Result<(), LexError> {
        let position = self.position(); self.take("/*"); let mut depth = 1;
        while depth > 0 {
            if self.rest().is_empty() { return Err(LexError { position, message: "unterminated block comment".into() }); }
            if self.take("/*") { depth += 1; } else if self.take("*/") { depth -= 1; } else { self.bump(); }
        }
        Ok(())
    }

    fn raw_string(&mut self) -> Result<Option<String>, LexError> {
        let save = (self.offset, self.line, self.column); self.bump(); let mut hashes = 0;
        while self.rest().starts_with('#') { self.bump(); hashes += 1; }
        if !self.take("\"") { self.offset = save.0; self.line = save.1; self.column = save.2; return Ok(None); }
        let start = self.offset; let marker = format!("\"{}", "#".repeat(hashes));
        while !self.starts(&marker) { if self.rest().is_empty() { return self.error("unterminated raw string"); } self.bump(); }
        let value = self.source[start..self.offset].to_string(); self.take(&marker); Ok(Some(value))
    }

    fn quoted(&mut self, position: Position, quote: char) -> Result<Token, LexError> {
        self.bump(); let mut value = String::new();
        loop {
            match self.rest().chars().next() {
                None | Some('\n') => return self.error("unterminated string/character literal"),
                Some(c) if c == quote => { self.bump(); break; }
                Some('\\') => {
                    self.bump(); let escaped = self.bump().ok_or_else(|| LexError { position, message: "unterminated escape sequence".into() })?;
                    let decoded = match escaped { 'n'=>'\n','t'=>'\t','r'=>'\r','\\'=>'\\','"'=>'"','\''=>'\'','0'=>'\0',
                        'x' => self.unicode_escape(2, position)?, 'u' => self.unicode_escape(4, position)?, 'U' => self.unicode_escape(8, position)?,
                        _ => return self.error(format!("unknown escape sequence '\\{}'", escaped)), };
                    value.push(decoded);
                }
                Some(_) => value.push(self.bump().unwrap()),
            }
        }
        if quote == '\'' {
            let mut chars = value.chars(); let c = chars.next().ok_or_else(|| LexError { position, message: "character literal must contain exactly one Unicode character".into() })?;
            if chars.next().is_some() { return self.error("character literal must contain exactly one Unicode character"); }
            Ok(Token { kind: TokenKind::Char, text: value, literal: Literal::Char(c), position })
        } else { Ok(Token { kind: TokenKind::String, text: value.clone(), literal: Literal::String(value), position }) }
    }

    fn number(&mut self, position: Position) -> Result<Token, LexError> {
        let start = self.offset; let mut dot = false; let mut exp = false;
        if self.starts("0x") || self.starts("0X") || self.starts("0b") || self.starts("0B") || self.starts("0o") || self.starts("0O") {
            self.bump(); let prefix = self.bump().unwrap(); let base = match prefix.to_ascii_lowercase() { 'x'=>16, 'b'=>2, _=>8 };
            while self.rest().chars().next().is_some_and(|c| c.is_ascii_hexdigit() || c == '_') { self.bump(); }
            let text = self.source[start..self.offset].to_string(); let digits = text[2..].replace('_', "");
            let value = i64::from_str_radix(&digits, base).map_err(|_| LexError { position, message: format!("invalid integer literal {}", text) })?;
            return Ok(Token { kind: TokenKind::Int, text, literal: Literal::Int(value), position });
        }
        while let Some(c) = self.rest().chars().next() {
            if c.is_ascii_digit() || c == '_' { self.bump(); }
            else if c == '.' && !dot && !exp && self.rest().chars().nth(1) != Some('.') { dot = true; self.bump(); }
            else if matches!(c, 'e' | 'E') && !exp { exp = true; self.bump(); if matches!(self.rest().chars().next(), Some('+'|'-')) { self.bump(); } }
            else { break; }
        }
        let text = self.source[start..self.offset].to_string(); let clean = text.replace('_', "");
        if dot || exp { let value = clean.parse::<f64>().map_err(|_| LexError { position, message: format!("invalid float literal {}", text) })?; Ok(Token { kind: TokenKind::Float, text, literal: Literal::Float(value), position }) }
        else { let value = clean.parse::<i64>().map_err(|_| LexError { position, message: format!("invalid integer literal {}", text) })?; Ok(Token { kind: TokenKind::Int, text, literal: Literal::Int(value), position }) }
    }
}

#[cfg(test)]
mod tests {
    use super::{lex, Literal, TokenKind};

    #[test]
    fn lexes_phase10_surface() {
        let tokens = lex("package main\nfunc identity<T>(x: T) -> T { return x }\n").unwrap();
        assert!(tokens.iter().any(|t| matches!(t.kind, TokenKind::Keyword("func"))));
        assert!(tokens.iter().any(|t| t.text == "<"));
        assert!(tokens.iter().any(|t| matches!(t.literal, Literal::String(_))) == false);
    }

    #[test]
    fn preserves_unicode_and_nested_comments() {
        let tokens = lex("/* outer /* inner */ done */\n'λ'\n").unwrap();
        assert!(tokens.iter().any(|t| matches!(t.literal, Literal::Char('λ'))));
    }
}
