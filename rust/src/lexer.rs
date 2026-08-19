//! Lexical analysis (tokenization).
//!
//! Port of internal/lexer/lexer.go. Operates on bytes; char literals decode
//! full UTF-8 runes exactly like the Go implementation.

use crate::diagnostic::Diagnostics;
use crate::source::{Source, Span};

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum TokenKind {
    Eof,
    Error,
    // Literals
    IntLiteral,
    FloatLiteral,
    BoolLiteral,
    CharLiteral,
    StringLiteral,
    // Identifiers and keywords
    Identifier,
    Func,
    If,
    Else,
    While,
    For,
    In,
    Return,
    Break,
    Continue,
    True,
    False,
    Null,
    Package,
    Use,
    // Types
    Bool,
    Byte,
    Int,
    Float,
    Char,
    String,
    Void,
    List,
    Map,
    Stack,
    Any,
    // Operators and delimiters
    Concat, // ..
    Plus,
    Minus,
    Star,
    Slash,
    Percent,
    Eq,     // ==
    Ne,     // !=
    Lt,     // <
    Le,     // <=
    Gt,     // >
    Ge,     // >=
    Assign, // =
    And,    // &&
    Or,     // ||
    Not,    // !
    BitAnd,
    BitOr,
    BitXor,
    BitNot,
    ShiftLeft,
    ShiftRight,
    // Delimiters
    LParen,
    RParen,
    LBrace,
    RBrace,
    LBracket,
    RBracket,
    Comma,
    Colon,
    Semicolon,
    Arrow,
    Dot,
    Ellipsis,
    Question,
    NullCoalesce,
    // Keywords
    Switch,
    Case,
    Default,
    Try,
    Catch,
    Finally,
    Throw,
    Exception,
    Mut,
    Enum,
    Struct,
    Trait,
    Pub,
    // Significant newline
    Newline,
}

impl TokenKind {
    pub fn as_str(&self) -> &'static str {
        use TokenKind::*;
        match self {
            Eof => "EOF",
            Error => "error",
            IntLiteral => "integer literal",
            FloatLiteral => "float literal",
            BoolLiteral => "bool literal",
            CharLiteral => "char literal",
            StringLiteral => "string literal",
            Identifier => "identifier",
            Func => "func",
            If => "if",
            Else => "else",
            While => "while",
            For => "for",
            In => "in",
            Return => "return",
            Break => "break",
            Continue => "continue",
            True => "true",
            False => "false",
            Null => "null",
            Package => "package",
            Use => "use",
            Bool => "bool",
            Byte => "byte",
            Int => "int",
            Float => "float",
            Char => "char",
            String => "string",
            Void => "void",
            List => "list",
            Map => "map",
            Stack => "stack",
            Any => "any",
            Concat => "..",
            Plus => "+",
            Minus => "-",
            Star => "*",
            Slash => "/",
            Percent => "%",
            Eq => "==",
            Ne => "!=",
            Lt => "<",
            Le => "<=",
            Gt => ">",
            Ge => ">=",
            Assign => "=",
            And => "&&",
            Or => "||",
            Not => "!",
            BitAnd => "&",
            BitOr => "|",
            BitXor => "^",
            BitNot => "~",
            ShiftLeft => "<<",
            ShiftRight => ">>",
            LParen => "(",
            RParen => ")",
            LBrace => "{",
            RBrace => "}",
            LBracket => "[",
            RBracket => "]",
            Comma => ",",
            Colon => ":",
            Semicolon => ";",
            Arrow => "->",
            Dot => ".",
            Ellipsis => "...",
            Question => "?",
            NullCoalesce => "??",
            Switch => "switch",
            Case => "case",
            Default => "default",
            Try => "try",
            Catch => "catch",
            Finally => "finally",
            Throw => "throw",
            Exception => "exception",
            Mut => "mut",
            Enum => "enum",
            Struct => "struct",
            Trait => "trait",
            Pub => "pub",
            Newline => "newline",
        }
    }
}

#[derive(Clone, Debug)]
pub struct Token {
    pub kind: TokenKind,
    pub lexeme: String,
    pub span: Span,
}

fn lookup_keyword(ident: &str) -> TokenKind {
    use TokenKind::*;
    match ident {
        "func" => Func,
        "if" => If,
        "else" => Else,
        "while" => While,
        "for" => For,
        "in" => In,
        "return" => Return,
        "break" => Break,
        "continue" => Continue,
        "true" | "false" => BoolLiteral,
        "null" => Null,
        "package" => Package,
        "use" => Use,
        "bool" => Bool,
        "byte" => Byte,
        "int" => Int,
        "float" => Float,
        "char" => Char,
        "string" => String,
        "void" => Void,
        "list" => List,
        "switch" => Switch,
        "case" => Case,
        "default" => Default,
        "map" => Map,
        "stack" => Stack,
        "any" => Any,
        "try" => Try,
        "catch" => Catch,
        "finally" => Finally,
        "throw" => Throw,
        "exception" => Exception,
        "mut" => Mut,
        "enum" => Enum,
        "struct" => Struct,
        "trait" => Trait,
        "pub" => Pub,
        _ => Identifier,
    }
}

pub struct Lexer<'a> {
    src: &'a Source,
    start: usize,
    current: usize,
    diags: Diagnostics,
    tokens: Vec<Token>,
    eof: bool,
}

impl<'a> Lexer<'a> {
    pub fn new(src: &'a Source) -> Lexer<'a> {
        Lexer {
            src,
            start: 0,
            current: 0,
            diags: Diagnostics::new(),
            tokens: Vec::new(),
            eof: false,
        }
    }

    pub fn tokenize(mut self) -> (Vec<Token>, Diagnostics) {
        while !self.eof {
            let tok = self.next_token();
            let is_eof = tok.kind == TokenKind::Eof;
            self.tokens.push(tok);
            if is_eof {
                break;
            }
        }
        (self.tokens, self.diags)
    }

    fn next_token(&mut self) -> Token {
        self.skip_whitespace();
        self.start = self.current;

        if self.current >= self.src.content.len() {
            return self.make_token(TokenKind::Eof);
        }

        let ch = self.peek();

        // Handle newlines
        if ch == b'\n' as i32 {
            self.advance();
            return self.make_token(TokenKind::Newline);
        }
        if ch == b'\r' as i32 {
            self.advance();
            if self.peek() == b'\n' as i32 {
                self.advance();
            }
            return self.make_token(TokenKind::Newline);
        }

        // Handle comments. A '//' or '/*' immediately preceded by ':' is part
        // of an unquoted URI scheme such as https:// and is not a comment.
        if ch == b'/' as i32 {
            let next = self.peek_next();
            let uri_scheme_delimiter =
                self.current > 0 && self.src.content[self.current - 1] == b':';
            if next == b'/' as i32 && !uri_scheme_delimiter {
                self.skip_line_comment();
                return self.next_token();
            }
            if next == b'*' as i32 && !uri_scheme_delimiter {
                self.skip_block_comment();
                return self.next_token();
            }
        }

        if ch == b';' as i32 {
            self.advance();
            return self.make_token(TokenKind::Semicolon);
        }

        // Identifiers and keywords (raw strings start with 'r')
        if is_ident_start(ch) {
            if ch == b'r' as i32 {
                let next = self.peek_next();
                if next == b'"' as i32 || next == b'#' as i32 {
                    return self.read_raw_string();
                }
            }
            return self.read_identifier();
        }

        // Numbers
        if is_digit(ch) || (ch == b'.' as i32 && is_digit(self.peek_next())) {
            return self.read_number();
        }

        // Strings
        if ch == b'"' as i32 {
            return self.read_string();
        }

        // Char literals
        if ch == b'\'' as i32 {
            return self.read_char();
        }

        use TokenKind::*;
        let simple = match ch as u8 {
            b'(' => Some(LParen),
            b')' => Some(RParen),
            b'{' => Some(LBrace),
            b'}' => Some(RBrace),
            b'[' => Some(LBracket),
            b']' => Some(RBracket),
            b',' => Some(Comma),
            b':' => Some(Colon),
            b'*' => Some(Star),
            b'/' => Some(Slash),
            b'%' => Some(Percent),
            b'^' => Some(BitXor),
            b'~' => Some(BitNot),
            _ => None,
        };
        if let Some(kind) = simple {
            self.advance();
            return self.make_token(kind);
        }

        match ch as u8 {
            b'.' => {
                self.advance();
                if self.peek() == b'.' as i32 {
                    self.advance();
                    if self.peek() == b'.' as i32 {
                        self.advance();
                        return self.make_token(Ellipsis);
                    }
                    return self.make_token(Concat);
                }
                self.make_token(Dot)
            }
            b'+' => {
                self.advance();
                self.make_token(Plus)
            }
            b'-' => {
                self.advance();
                if self.peek() == b'>' as i32 {
                    self.advance();
                    return self.make_token(Arrow);
                }
                self.make_token(Minus)
            }
            b'=' => {
                self.advance();
                if self.peek() == b'=' as i32 {
                    self.advance();
                    return self.make_token(Eq);
                }
                self.make_token(Assign)
            }
            b'!' => {
                self.advance();
                if self.peek() == b'=' as i32 {
                    self.advance();
                    return self.make_token(Ne);
                }
                self.make_token(Not)
            }
            b'<' => {
                self.advance();
                if self.peek() == b'=' as i32 {
                    self.advance();
                    return self.make_token(Le);
                }
                if self.peek() == b'<' as i32 {
                    self.advance();
                    return self.make_token(ShiftLeft);
                }
                self.make_token(Lt)
            }
            b'>' => {
                self.advance();
                if self.peek() == b'=' as i32 {
                    self.advance();
                    return self.make_token(Ge);
                }
                if self.peek() == b'>' as i32 {
                    self.advance();
                    return self.make_token(ShiftRight);
                }
                self.make_token(Gt)
            }
            b'&' => {
                self.advance();
                if self.peek() == b'&' as i32 {
                    self.advance();
                    return self.make_token(And);
                }
                self.make_token(BitAnd)
            }
            b'|' => {
                self.advance();
                if self.peek() == b'|' as i32 {
                    self.advance();
                    return self.make_token(Or);
                }
                self.make_token(BitOr)
            }
            b'?' => {
                self.advance();
                if self.peek() == b'?' as i32 {
                    self.advance();
                    return self.make_token(NullCoalesce);
                }
                self.make_token(Question)
            }
            _ => {
                // Unexpected character
                let ch_repr = char_repr(ch);
                self.advance();
                self.diags.add_error(
                    "L001",
                    &format!("unexpected character: {}", ch_repr),
                    self.current_span(),
                );
                self.make_token(Error)
            }
        }
    }

    fn read_identifier(&mut self) -> Token {
        while is_ident_part(self.peek()) {
            self.advance();
        }
        let lexeme = self.lexeme();
        let kind = lookup_keyword(&lexeme);
        self.make_token(kind)
    }

    fn read_number(&mut self) -> Token {
        // Check for prefixed integer literals (0x hex, 0b binary, 0o octal)
        if self.peek() == b'0' as i32 {
            match self.peek_next() as u8 {
                b'x' | b'X' | b'b' | b'B' | b'o' | b'O' => {
                    return self.read_prefixed_number();
                }
                _ => {}
            }
        }

        let mut is_float = false;
        self.skip_number_underscores();

        // Decimal point (not part of '..')
        if self.peek() == b'.' as i32 && !self.is_dot_dot() {
            self.advance();
            is_float = true;
            self.skip_number_underscores();
        }

        // Exponent
        if self.peek() == b'e' as i32 || self.peek() == b'E' as i32 {
            self.advance();
            if self.peek() == b'+' as i32 || self.peek() == b'-' as i32 {
                self.advance();
            }
            if !is_digit(self.peek()) {
                self.diags.add_error("L002", "expected digit in exponent", self.current_span());
                return self.make_token(TokenKind::Error);
            }
            while is_digit(self.peek()) || self.peek() == b'_' as i32 {
                self.advance();
            }
            is_float = true;
        }

        let raw = self.lexeme();
        if raw.contains('_') {
            if let Some(msg) = validate_underscore_format(&raw) {
                self.diags.add_error("L011", &msg, self.current_span());
                return self.make_token(TokenKind::Error);
            }
        }

        let lexeme = raw.replace('_', "");

        // Type suffixes
        match self.peek() as u8 {
            b'f' | b'F' => {
                self.advance();
                return self.make_lexeme_token(TokenKind::FloatLiteral, lexeme);
            }
            b'd' | b'D' => {
                self.advance();
                self.diags.add_error(
                    "L012",
                    "'D' suffix is no longer supported; floating-point literals have type float",
                    self.current_span(),
                );
                return self.make_token(TokenKind::Error);
            }
            b'L' | b'l' => {
                self.advance();
                self.diags.add_error(
                    "L013",
                    "'L' suffix is no longer supported; integer literals have type int",
                    self.current_span(),
                );
                return self.make_token(TokenKind::Error);
            }
            _ => {}
        }

        if is_float {
            self.make_lexeme_token(TokenKind::FloatLiteral, lexeme)
        } else {
            self.make_lexeme_token(TokenKind::IntLiteral, lexeme)
        }
    }

    fn read_prefixed_number(&mut self) -> Token {
        self.advance(); // skip 0
        let prefix = self.peek() as u8;
        self.advance(); // skip base prefix

        let base = match prefix {
            b'b' | b'B' => 2,
            b'o' | b'O' => 8,
            _ => 16,
        };
        let is_hex = base == 16;

        let start = self.current;
        while is_base_digit(self.peek(), base) || self.peek() == b'_' as i32 {
            self.advance();
        }
        if self.current == start {
            self.diags.add_error(
                "L014",
                &format!("expected digits after 0{} prefix", prefix as char),
                self.current_span(),
            );
            return self.make_token(TokenKind::Error);
        }

        let raw = self.lexeme();
        if raw.contains('_') {
            if let Some(msg) = validate_underscore_format(&raw) {
                self.diags.add_error("L011", &msg, self.current_span());
                return self.make_token(TokenKind::Error);
            }
        }

        let clean = raw.replace('_', "");

        if is_hex && matches!(self.peek() as u8, b'f' | b'F') {
            self.advance();
            return self.make_lexeme_token(TokenKind::FloatLiteral, clean);
        }
        match self.peek() as u8 {
            b'd' | b'D' => {
                self.advance();
                self.diags.add_error(
                    "L012",
                    "'D' suffix is no longer supported; floating-point literals have type float",
                    self.current_span(),
                );
                return self.make_token(TokenKind::Error);
            }
            b'L' | b'l' => {
                self.advance();
                self.diags.add_error(
                    "L013",
                    "'L' suffix is no longer supported; integer literals have type int",
                    self.current_span(),
                );
                return self.make_token(TokenKind::Error);
            }
            _ => {}
        }
        self.make_lexeme_token(TokenKind::IntLiteral, clean)
    }

    fn read_string(&mut self) -> Token {
        self.advance(); // skip opening quote
        let mut buf: Vec<u8> = Vec::new();
        loop {
            let ch = self.peek();
            if ch < 0 {
                self.diags.add_error("L004", "unterminated string literal", self.current_span());
                return self.make_token(TokenKind::Error);
            }
            if ch == b'"' as i32 {
                self.advance();
                return self.make_value_token(TokenKind::StringLiteral, &buf);
            }
            if ch == b'\\' as i32 {
                self.advance();
                let esc = self.peek();
                match esc as u8 {
                    b'n' => {
                        self.advance();
                        buf.push(b'\n');
                    }
                    b't' => {
                        self.advance();
                        buf.push(b'\t');
                    }
                    b'r' => {
                        self.advance();
                        buf.push(b'\r');
                    }
                    b'\\' => {
                        self.advance();
                        buf.push(b'\\');
                    }
                    b'"' => {
                        self.advance();
                        buf.push(b'"');
                    }
                    b'\'' => {
                        self.advance();
                        buf.push(b'\'');
                    }
                    b'0' => {
                        self.advance();
                        buf.push(0);
                    }
                    b'x' => {
                        self.advance();
                        let hi = self.peek();
                        self.advance();
                        let lo = self.peek();
                        self.advance();
                        if hi < 0 || lo < 0 || !is_hex_digit(hi) || !is_hex_digit(lo) {
                            self.diags.add_error(
                                "L017",
                                "invalid hexadecimal digits in \\x escape",
                                self.current_span(),
                            );
                            return self.make_token(TokenKind::Error);
                        }
                        buf.push(((hex_val(hi) << 4) | hex_val(lo)) as u8);
                    }
                    b'u' => match self.read_unicode_escape(4) {
                        Some(bytes) => buf.extend_from_slice(&bytes),
                        None => {
                            self.diags.add_error(
                                "L018",
                                "invalid hexadecimal digits or code point in \\u escape",
                                self.current_span(),
                            );
                            return self.make_token(TokenKind::Error);
                        }
                    },
                    b'U' => match self.read_unicode_escape(8) {
                        Some(bytes) => buf.extend_from_slice(&bytes),
                        None => {
                            self.diags.add_error(
                                "L018",
                                "invalid hexadecimal digits or code point in \\U escape",
                                self.current_span(),
                            );
                            return self.make_token(TokenKind::Error);
                        }
                    },
                    _ => {
                        let c = if esc < 0 { char::from_u32(0xFFFD).unwrap() } else { (esc as u8) as char };
                        self.diags.add_error(
                            "L016",
                            &format!("unknown escape sequence '\\{}'", c),
                            self.current_span(),
                        );
                        return self.make_token(TokenKind::Error);
                    }
                }
            } else if ch == b'\n' as i32 || ch == b'\r' as i32 {
                self.diags.add_error("L005", "newline in string literal", self.current_span());
                return self.make_token(TokenKind::Error);
            } else {
                buf.push(ch as u8);
                self.advance();
            }
        }
    }

    /// Reads a Rust-style raw string literal: r"...", r#"..."#, r##"..."##.
    fn read_raw_string(&mut self) -> Token {
        self.advance(); // skip 'r'

        let mut hash_count = 0usize;
        while self.peek() == b'#' as i32 {
            hash_count += 1;
            if hash_count > 255 {
                while self.peek() == b'#' as i32 {
                    self.advance();
                }
                self.diags.add_error(
                    "L010",
                    "raw string delimiter too large: maximum is 255 '#' characters",
                    self.current_span(),
                );
                return self.make_token(TokenKind::Error);
            }
            self.advance();
        }

        if self.peek() != b'"' as i32 {
            // Not a raw string — re-read as identifier.
            self.current = self.start;
            return self.read_identifier();
        }
        self.advance(); // skip opening '"'

        let raw_start = self.start;

        let mut buf: Vec<u8> = Vec::new();
        loop {
            let ch = self.peek();
            if ch < 0 {
                let expected = format!("\"{}", "#".repeat(hash_count));
                let msg = format!("unterminated raw string literal, expected {}", expected);
                let span = self.src.span_from_range(raw_start, self.current);
                self.diags.add_error("L006", &msg, span);
                return self.make_token(TokenKind::Error);
            }

            if ch == b'"' as i32 {
                let saved_pos = self.current;
                self.advance();
                let mut matched = true;
                for _ in 0..hash_count {
                    if self.peek() != b'#' as i32 {
                        matched = false;
                        break;
                    }
                    self.advance();
                }
                if matched {
                    return self.make_value_token(TokenKind::StringLiteral, &buf);
                }
                // Restore: the '"' (and any partial hashes) belong to the body.
                self.current = saved_pos;
                buf.push(b'"');
                self.advance();
                continue;
            }

            buf.push(ch as u8);
            self.advance();
        }
    }

    fn read_char(&mut self) -> Token {
        self.advance(); // skip opening quote

        let ch = self.peek();
        if ch < 0 {
            self.diags.add_error("L007", "unterminated char literal", self.current_span());
            return self.make_token(TokenKind::Error);
        }

        let r: u32;
        if ch == b'\\' as i32 {
            self.advance();
            let esc = self.peek();
            match esc as u8 {
                b'n' => {
                    self.advance();
                    r = '\n' as u32;
                }
                b't' => {
                    self.advance();
                    r = '\t' as u32;
                }
                b'r' => {
                    self.advance();
                    r = '\r' as u32;
                }
                b'\\' => {
                    self.advance();
                    r = '\\' as u32;
                }
                b'\'' => {
                    self.advance();
                    r = '\'' as u32;
                }
                b'0' => {
                    self.advance();
                    r = 0;
                }
                b'x' => {
                    self.advance();
                    let hi = self.peek();
                    self.advance();
                    let lo = self.peek();
                    self.advance();
                    if hi < 0 || lo < 0 || !is_hex_digit(hi) || !is_hex_digit(lo) {
                        self.diags.add_error(
                            "L017",
                            "invalid hexadecimal digits in \\x escape",
                            self.current_span(),
                        );
                        return self.make_token(TokenKind::Error);
                    }
                    r = ((hex_val(hi) << 4) | hex_val(lo)) as u32;
                }
                b'u' => match self.read_unicode_escape(4) {
                    Some(bytes) => {
                        r = decode_rune(&bytes).0;
                    }
                    None => {
                        self.diags.add_error(
                            "L018",
                            "invalid hexadecimal digits or code point in \\u escape",
                            self.current_span(),
                        );
                        return self.make_token(TokenKind::Error);
                    }
                },
                b'U' => match self.read_unicode_escape(8) {
                    Some(bytes) => {
                        r = decode_rune(&bytes).0;
                    }
                    None => {
                        self.diags.add_error(
                            "L018",
                            "invalid hexadecimal digits or code point in \\U escape",
                            self.current_span(),
                        );
                        return self.make_token(TokenKind::Error);
                    }
                },
                _ => {
                    let c = if esc < 0 { char::from_u32(0xFFFD).unwrap() } else { (esc as u8) as char };
                    self.diags.add_error(
                        "L016",
                        &format!("unknown escape sequence '\\{}'", c),
                        self.current_span(),
                    );
                    return self.make_token(TokenKind::Error);
                }
            }
        } else {
            // Decode a full UTF-8 rune so multi-byte characters work.
            let (rune, size) = decode_rune(&self.src.content[self.current..]);
            if rune == 0xFFFD && size == 1 {
                self.diags.add_error("L015", "invalid UTF-8 encoding in char literal", self.current_span());
                return self.make_token(TokenKind::Error);
            }
            r = rune;
            for _ in 0..size {
                self.advance();
            }
        }

        if self.peek() != b'\'' as i32 {
            self.diags.add_error("L008", "unterminated char literal (expected ')", self.current_span());
            return self.make_token(TokenKind::Error);
        }
        self.advance();
        self.make_lexeme_token(TokenKind::CharLiteral, char::from_u32(r).map(|c| c.to_string()).unwrap_or_default())
    }

    /// Reads \uXXXX or \UXXXXXXXX. On entry the escape letter is current.
    fn read_unicode_escape(&mut self, digits: usize) -> Option<Vec<u8>> {
        self.advance(); // consume the escape letter
        let mut r: u32 = 0;
        for _ in 0..digits {
            let ch = self.peek();
            if ch < 0 || !is_hex_digit(ch) {
                return None;
            }
            self.advance();
            r = (r << 4) | hex_val(ch) as u32;
        }
        if r > 0x10FFFF || (0xD800..=0xDFFF).contains(&r) {
            return None;
        }
        Some(char::from_u32(r).unwrap().to_string().into_bytes())
    }

    fn skip_whitespace(&mut self) {
        loop {
            match self.peek() as u8 as char {
                ' ' | '\t' | '\x0c' | '\x0b' => {
                    self.advance();
                }
                _ => break,
            }
        }
    }

    fn skip_line_comment(&mut self) {
        loop {
            let ch = self.peek();
            if ch < 0 || ch == b'\n' as i32 || ch == b'\r' as i32 {
                return;
            }
            self.advance();
        }
    }

    fn skip_block_comment(&mut self) {
        self.advance(); // skip *
        let mut depth = 1;
        while depth > 0 {
            let ch = self.peek();
            if ch < 0 {
                self.diags.add_error("L009", "unterminated block comment", self.current_span());
                return;
            }
            if ch == b'/' as i32 && self.peek_next() == b'*' as i32 {
                self.advance();
                self.advance();
                depth += 1;
            } else if ch == b'*' as i32 && self.peek_next() == b'/' as i32 {
                self.advance();
                self.advance();
                depth -= 1;
            } else {
                self.advance();
            }
        }
    }

    fn is_dot_dot(&self) -> bool {
        self.peek() == b'.' as i32 && self.peek_next() == b'.' as i32
    }

    fn advance(&mut self) {
        if self.current < self.src.content.len() {
            self.current += 1;
        }
    }

    fn peek(&self) -> i32 {
        if self.current >= self.src.content.len() {
            -1
        } else {
            self.src.content[self.current] as i32
        }
    }

    fn peek_next(&self) -> i32 {
        if self.current + 1 >= self.src.content.len() {
            -1
        } else {
            self.src.content[self.current + 1] as i32
        }
    }

    fn current_span(&self) -> Span {
        self.src.span_from_range(self.start, self.current)
    }

    fn lexeme(&self) -> String {
        String::from_utf8_lossy(&self.src.content[self.start..self.current]).to_string()
    }

    fn make_token(&self, kind: TokenKind) -> Token {
        Token {
            kind,
            lexeme: self.lexeme(),
            span: self.src.span_from_range(self.start, self.current),
        }
    }

    fn make_lexeme_token(&self, kind: TokenKind, lexeme: String) -> Token {
        Token {
            kind,
            lexeme,
            span: self.src.span_from_range(self.start, self.current),
        }
    }

    fn make_value_token(&self, kind: TokenKind, value: &[u8]) -> Token {
        Token {
            kind,
            lexeme: String::from_utf8_lossy(value).to_string(),
            span: self.src.span_from_range(self.start, self.current),
        }
    }

    fn skip_number_underscores(&mut self) {
        while is_digit(self.peek()) || self.peek() == b'_' as i32 {
            self.advance();
        }
    }
}

/// Decodes one UTF-8 rune like Go's utf8.DecodeRune: returns (rune, size),
/// with (0xFFFD, 1) for invalid encodings.
fn decode_rune(b: &[u8]) -> (u32, usize) {
    if b.is_empty() {
        return (0xFFFD, 0);
    }
    let b0 = b[0];
    let size = if b0 < 0x80 {
        1
    } else if b0 & 0xE0 == 0xC0 {
        2
    } else if b0 & 0xF0 == 0xE0 {
        3
    } else if b0 & 0xF8 == 0xF0 {
        4
    } else {
        return (0xFFFD, 1);
    };
    if b.len() < size {
        return (0xFFFD, 1);
    }
    match std::str::from_utf8(&b[..size]) {
        Ok(s) => {
            let c = s.chars().next().unwrap();
            (c as u32, size)
        }
        Err(_) => (0xFFFD, 1),
    }
}

fn char_repr(ch: i32) -> String {
    // Matches Go's %q for a rune.
    if ch < 0 {
        return "unexpected EOF".to_string();
    }
    if let Some(c) = char::from_u32(ch as u32) {
        format!("{:?}", c)
    } else {
        format!("\\x{:02x}", ch)
    }
}

fn is_ident_start(ch: i32) -> bool {
    ch == b'_' as i32 || (ch >= b'a' as i32 && ch <= b'z' as i32) || (ch >= b'A' as i32 && ch <= b'Z' as i32)
}

fn is_ident_part(ch: i32) -> bool {
    is_ident_start(ch) || is_digit(ch)
}

fn is_digit(ch: i32) -> bool {
    ch >= b'0' as i32 && ch <= b'9' as i32
}

fn is_hex_digit(ch: i32) -> bool {
    is_digit(ch) || (ch >= b'a' as i32 && ch <= b'f' as i32) || (ch >= b'A' as i32 && ch <= b'F' as i32)
}

fn is_base_digit(ch: i32, base: u32) -> bool {
    match base {
        2 => ch == b'0' as i32 || ch == b'1' as i32,
        8 => ch >= b'0' as i32 && ch <= b'7' as i32,
        _ => is_hex_digit(ch),
    }
}

fn hex_val(ch: i32) -> i32 {
    match ch {
        c if c >= b'0' as i32 && c <= b'9' as i32 => c - b'0' as i32,
        c if c >= b'a' as i32 && c <= b'f' as i32 => c - b'a' as i32 + 10,
        c if c >= b'A' as i32 && c <= b'F' as i32 => c - b'A' as i32 + 10,
        _ => 0,
    }
}

fn is_digit_byte(b: u8) -> bool {
    b.is_ascii_digit()
}

fn is_hex_digit_byte(b: u8) -> bool {
    b.is_ascii_hexdigit()
}

fn check_underscore_positions(s: &[u8], is_hex: bool) -> Option<String> {
    if s.is_empty() {
        return None;
    }
    if s[0] == b'_' {
        return Some("leading underscore in numeric literal".to_string());
    }
    if s[s.len() - 1] == b'_' {
        return Some("trailing underscore in numeric literal".to_string());
    }
    for i in 0..s.len() {
        if s[i] == b'_' {
            if i > 0 {
                let left = s[i - 1];
                if !is_digit_byte(left) && !(is_hex && is_hex_digit_byte(left)) {
                    return Some("underscore must appear between two digits".to_string());
                }
            }
            if i < s.len() - 1 {
                let right = s[i + 1];
                if !is_digit_byte(right) && !(is_hex && is_hex_digit_byte(right)) {
                    return Some("underscore must appear between two digits".to_string());
                }
            }
        }
    }
    None
}

fn validate_underscore_format(raw: &str) -> Option<String> {
    let raw_b = raw.as_bytes();
    let mut digit_part = raw_b;
    let mut is_hex = false;
    if raw_b.len() >= 2 && raw_b[0] == b'0' {
        match raw_b[1] {
            b'x' | b'X' | b'b' | b'B' | b'o' | b'O' => {
                digit_part = &raw_b[2..];
                if raw_b[1] == b'x' || raw_b[1] == b'X' {
                    is_hex = true;
                }
            }
            _ => {}
        }
    }

    let mut mantissa = digit_part;
    let mut exponent: &[u8] = b"";
    for i in 0..digit_part.len() {
        if digit_part[i] == b'e' || digit_part[i] == b'E' {
            exponent = &digit_part[i + 1..];
            mantissa = &digit_part[..i];
            break;
        }
    }

    let m_parts: Vec<&[u8]> = splitn2(mantissa, b'.');
    for part in m_parts {
        if let Some(msg) = check_underscore_positions(part, is_hex) {
            return Some(msg);
        }
    }

    if !exponent.is_empty() {
        let mut exp = exponent;
        if exp[0] == b'+' || exp[0] == b'-' {
            exp = &exp[1..];
        }
        if let Some(msg) = check_underscore_positions(exp, false) {
            return Some(msg);
        }
    }
    None
}

fn splitn2(s: &[u8], sep: u8) -> Vec<&[u8]> {
    match s.iter().position(|&c| c == sep) {
        Some(i) => vec![&s[..i], &s[i + 1..]],
        None => vec![s],
    }
}

