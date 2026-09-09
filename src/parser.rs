//! Solvik recursive-descent parser.
//!
//! Statement termination: a newline normally terminates a complete statement;
//! `;` explicitly terminates and allows multiple statements per line. A
//! statement continues across newlines while the expression is incomplete
//! (unmatched delimiter, trailing binary operator/comma) or while the next
//! line begins with a continuation operator.

use crate::ast::*;
use crate::diagnostic::Diagnostics;
use crate::lexer::{Token, TokenKind};
use crate::source::Span;

pub struct Parser {
    tokens: Vec<Token>,
    pos: usize,
    /// Unmatched opening-delimiter depth; newlines never terminate inside.
    depth: i32,
    last_kind: TokenKind,
    pub diags: Diagnostics,
}

impl Parser {
    pub fn new(tokens: Vec<Token>) -> Self {
        Parser {
            tokens,
            pos: 0,
            depth: 0,
            last_kind: TokenKind::Eof,
            diags: Diagnostics::default(),
        }
    }

    // ------------------------------------------------------------------
    // Token stream helpers
    // ------------------------------------------------------------------

    fn peek(&self) -> &Token {
        &self.tokens[self.pos.min(self.tokens.len() - 1)]
    }

    fn peek_kind(&self) -> TokenKind {
        self.peek().kind
    }

    fn peek_at(&self, n: usize) -> TokenKind {
        self.tokens
            .get(self.pos + n)
            .map(|t| t.kind)
            .unwrap_or(TokenKind::Eof)
    }

    fn advance(&mut self) -> Token {
        let t = self.tokens[self.pos.min(self.tokens.len() - 1)].clone();
        match t.kind {
            // Braces do not count: statement bodies rely on newline
            // termination, and literal/block parsers skip newlines themselves.
            TokenKind::LParen | TokenKind::LBracket => self.depth += 1,
            TokenKind::RParen | TokenKind::RBracket => self.depth -= 1,
            _ => {}
        }
        self.last_kind = t.kind;
        if self.pos < self.tokens.len() - 1 {
            self.pos += 1;
        }
        t
    }

    fn check(&self, kind: TokenKind) -> bool {
        self.peek_kind() == kind
    }

    /// Consume the token if it has the given kind.
    fn eat(&mut self, kind: TokenKind) -> bool {
        if self.check(kind) {
            self.advance();
            true
        } else {
            false
        }
    }

    fn expect(&mut self, kind: TokenKind, what: &str) -> bool {
        if self.eat(kind) {
            true
        } else {
            self.error(&format!("expected {}, found {}", what, self.describe()));
            false
        }
    }

    fn expect_ident(&mut self, what: &str) -> Option<String> {
        if self.check(TokenKind::Ident) {
            Some(self.advance().text)
        } else {
            self.error(&format!("expected {}, found {}", what, self.describe()));
            None
        }
    }

    fn describe(&self) -> String {
        match self.peek().kind {
            TokenKind::Eof => "end of file".to_string(),
            TokenKind::Newline => "newline".to_string(),
            _ => format!("{:?}", self.peek().text),
        }
    }

    fn error(&mut self, msg: &str) {
        let span = self.peek().span;
        self.diags.err_at("P001", msg, span);
    }

    /// Skip blank lines. A single newline is skipped when the expression is
    /// known to continue (tracked by the caller through `incomplete`).
    fn skip_newlines(&mut self) {
        while self.check(TokenKind::Newline) {
            self.advance();
        }
    }

    /// Consume a comma in a delimited list and report whether another item
    /// follows it. Newlines after the comma are part of the separator so a
    /// trailing comma may be placed on its own line before the delimiter.
    ///
    /// If the token after the comma is neither the delimiter nor a valid
    /// item, the caller parses it as the next item and emits the usual
    /// diagnostic instead of silently accepting an empty element.
    fn consume_list_comma(&mut self, closing: TokenKind) -> bool {
        if !self.eat(TokenKind::Comma) {
            return false;
        }
        self.skip_newlines();
        !self.check(closing)
    }

    /// Decide whether the pending newline(s) should be treated as a statement
    /// terminator given that the previous token was `prev`.
    fn newline_terminates(&self, prev: TokenKind) -> bool {
        if !self.check(TokenKind::Newline) {
            return false;
        }
        if self.depth > 0 {
            return false;
        }
        if prev.continues_expression() {
            return false;
        }
        // Look ahead past newlines for an operator-first continuation.
        let mut i = self.pos + 1;
        while i < self.tokens.len() && self.tokens[i].kind == TokenKind::Newline {
            i += 1;
        }
        if let Some(t) = self.tokens.get(i) {
            if t.kind.starts_continuation() {
                return false;
            }
        }
        true
    }

    /// End a statement: accept newline / semicolon / block close / EOF.
    fn end_statement(&mut self, prev: TokenKind) {
        if self.eat(TokenKind::Semicolon) {
            return;
        }
        if self.newline_terminates(prev) {
            self.skip_newlines();
            return;
        }
        if matches!(self.peek_kind(), TokenKind::Eof | TokenKind::RBrace) {
            return;
        }
        self.error("missing statement terminator (newline or ';')");
        // Recover: swallow newlines so following statements can parse.
        self.skip_newlines();
    }

    /// Skip tokens until we reach a safe resync point (newline, '}', EOF).
    fn resync(&mut self) {
        while !matches!(
            self.peek_kind(),
            TokenKind::Eof | TokenKind::RBrace | TokenKind::Newline
        ) {
            self.advance();
        }
        self.skip_newlines();
    }

    // ------------------------------------------------------------------
    // Program
    // ------------------------------------------------------------------

    pub fn parse_program(&mut self) -> Option<Program> {
        self.skip_newlines();
        if !self.expect(TokenKind::Package, "'package'") {
            self.resync();
            return None;
        }
        let package_span = self.peek().span;
        let package = self.expect_ident("package name")?;
        self.end_statement(TokenKind::Ident);
        self.skip_newlines();

        let mut uses = Vec::new();
        loop {
            self.skip_newlines();
            if !self.check(TokenKind::Use) {
                break;
            }
            let start = self.advance().span;
            let scheme = if self.eat(TokenKind::Ident)
                && self.tokens[self.pos.saturating_sub(1)].text == "file"
            {
                // "use file:path" — 'file' parsed as ident
                UseScheme::File
            } else if self.check(TokenKind::Ident) && self.peek().text == "url" {
                self.advance();
                UseScheme::Url
            } else {
                self.error("expected 'file:' or 'url:' after 'use'");
                UseScheme::File
            };
            let _ = self.eat(TokenKind::Colon);
            let path = if self.check(TokenKind::Ident) {
                // path may contain dots: collect ident(.ident)*
                let mut name = self.advance().text;
                while self.check(TokenKind::Dot) && self.peek_at(1) == TokenKind::Ident {
                    self.advance();
                    name.push('.');
                    name.push_str(&self.advance().text);
                }
                name
            } else {
                self.error("expected dependency path");
                String::new()
            };
            uses.push(UseDecl {
                path,
                scheme,
                span: Span::join(start, self.peek().span),
            });
            self.end_statement(TokenKind::Ident);
        }

        let mut items = Vec::new();
        loop {
            self.skip_newlines();
            match self.peek_kind() {
                TokenKind::Eof => break,
                TokenKind::Class => items.push(Item::Class(self.parse_class()?)),
                TokenKind::Interface => items.push(Item::Interface(self.parse_interface()?)),
                TokenKind::Enum => items.push(Item::Enum(self.parse_enum()?)),
                _ => {
                    self.error(&format!(
                        "expected class, interface, or enum declaration, found {}",
                        self.describe()
                    ));
                    // A stray '}' is a resync stop token: resync would stop
                    // right on it and the loop would spin, so consume it.
                    if self.check(TokenKind::RBrace) {
                        self.advance();
                    } else {
                        self.resync();
                    }
                    continue;
                }
            }
            self.skip_newlines();
        }
        Some(Program {
            package,
            package_span,
            uses,
            items,
        })
    }

    // ------------------------------------------------------------------
    // Type parameters and types
    // ------------------------------------------------------------------

    fn parse_type_params(&mut self) -> Vec<TypeParam> {
        let mut params = Vec::new();
        if self.eat(TokenKind::Lt) {
            self.skip_newlines();
            while let Some(name) = self.expect_ident("type parameter name") {
                let mut constraints = Vec::new();
                if self.eat(TokenKind::Colon) {
                    constraints.push(self.parse_type_ref());
                    while self.eat(TokenKind::Ampersand) {
                        constraints.push(self.parse_type_ref());
                    }
                }
                params.push(TypeParam { name, constraints });
                if !self.consume_list_comma(TokenKind::Gt) {
                    break;
                }
            }
            self.skip_newlines();
            self.expect(TokenKind::Gt, "'>' after type parameters");
        }
        params
    }

    /// Parse a type reference including an optional trailing `?`.
    pub fn parse_type_ref(&mut self) -> TypeRef {
        let start = self.peek().span;
        let base = match self.peek_kind() {
            TokenKind::SelfKw => {
                self.advance();
                TypeBase::Named("Self".to_string())
            }
            TokenKind::Ident => {
                let name = self.advance().text;
                if self.check(TokenKind::Lt) {
                    self.advance();
                    self.skip_newlines();
                    let mut args = vec![self.parse_type_ref()];
                    while self.consume_list_comma(TokenKind::Gt) {
                        args.push(self.parse_type_ref());
                    }
                    self.skip_newlines();
                    self.expect(TokenKind::Gt, "'>' closing generic type");
                    TypeBase::Generic(name, args)
                } else {
                    TypeBase::Named(name)
                }
            }
            _ => {
                self.error(&format!("expected type, found {}", self.describe()));
                TypeBase::Named(String::from("?"))
            }
        };
        let nullable = self.eat(TokenKind::QuestionMark);
        TypeRef {
            base,
            nullable,
            span: Span::join(start, self.peek().span),
        }
    }

    // ------------------------------------------------------------------
    // Classes
    // ------------------------------------------------------------------

    fn parse_class(&mut self) -> Option<ClassDef> {
        let start = self.advance().span; // 'class'
        let name_span = self.peek().span;
        let name = self.expect_ident("class name")?;
        let type_params = self.parse_type_params();
        let extends = if self.eat(TokenKind::Extends) {
            Some(self.parse_type_ref())
        } else {
            None
        };
        let mut implements = Vec::new();
        if self.eat(TokenKind::Implements) {
            implements.push(self.parse_type_ref());
            while self.consume_list_comma(TokenKind::LBrace) {
                implements.push(self.parse_type_ref());
            }
        }
        self.expect(TokenKind::LBrace, "'{' opening class body");
        let (fields, methods) = self.parse_class_members()?;
        self.expect(TokenKind::RBrace, "'}' closing class body");
        Some(ClassDef {
            name,
            name_span,
            type_params,
            extends,
            implements,
            fields,
            methods,
            span: Span::join(start, self.peek().span),
        })
    }

    fn parse_class_members(&mut self) -> Option<(Vec<FieldDecl>, Vec<MethodDef>)> {
        let mut fields = Vec::new();
        let mut methods = Vec::new();
        loop {
            self.skip_newlines();
            match self.peek_kind() {
                TokenKind::RBrace => break,
                TokenKind::Eof => {
                    self.error("unexpected end of file in class body");
                    return None;
                }
                TokenKind::Mut => {
                    self.advance();
                    if !self.check(TokenKind::LBrace) {
                        // Single mutable field: `mut name: Type`.
                        let fspan = self.peek().span;
                        let fname = match self.expect_ident("field name after 'mut'") {
                            Some(n) => n,
                            None => {
                                self.resync();
                                continue;
                            }
                        };
                        self.expect(TokenKind::Colon, "':' after field name");
                        let ty = self.parse_type_ref();
                        fields.push(FieldDecl {
                            name: fname,
                            ty,
                            mutable: true,
                            visibility: Visibility::Private,
                            span: fspan,
                        });
                        self.end_statement(TokenKind::Question);
                        continue;
                    }
                    // mut { field: Type ... } block
                    self.advance();
                    loop {
                        self.skip_newlines();
                        if self.eat(TokenKind::RBrace) {
                            break;
                        }
                        let fspan = self.peek().span;
                        let fname = match self.expect_ident("field name in mut block") {
                            Some(n) => n,
                            None => {
                                self.resync();
                                continue;
                            }
                        };
                        self.expect(TokenKind::Colon, "':' after field name");
                        let ty = self.parse_type_ref();
                        fields.push(FieldDecl {
                            name: fname,
                            ty,
                            mutable: true,
                            visibility: Visibility::Private,
                            span: fspan,
                        });
                        self.end_statement(TokenKind::Question);
                    }
                }
                TokenKind::Ident => {
                    // `ident (` starts a method; `ident :` starts a field.
                    if self.peek_at(1) == TokenKind::Colon {
                        let fspan = self.peek().span;
                        let fname = self.advance().text; // ident
                        self.advance(); // ':'
                        let ty = self.parse_type_ref();
                        fields.push(FieldDecl {
                            name: fname,
                            ty,
                            mutable: false,
                            visibility: Visibility::Private,
                            span: fspan,
                        });
                        self.end_statement(TokenKind::Question);
                    } else {
                        let m = self.parse_method(true)?;
                        methods.push(m);
                    }
                }
                TokenKind::Pub
                | TokenKind::Private
                | TokenKind::Protected
                | TokenKind::Static
                | TokenKind::Override => {
                    // Look ahead past modifiers: `ident :` is a field with
                    // visibility, `ident (` is a method.
                    let mut i = 0usize;
                    let mut vis = Visibility::Private;
                    let mut is_field = false;
                    loop {
                        match self.peek_at(i) {
                            TokenKind::Pub => {
                                vis = Visibility::Pub;
                                i += 1;
                            }
                            TokenKind::Private => {
                                vis = Visibility::Private;
                                i += 1;
                            }
                            TokenKind::Protected => {
                                vis = Visibility::Protected;
                                i += 1;
                            }
                            TokenKind::Static | TokenKind::Override => {
                                i += 1;
                            }
                            TokenKind::Ident => {
                                is_field = self.peek_at(i + 1) == TokenKind::Colon;
                                break;
                            }
                            _ => break,
                        }
                    }
                    if is_field {
                        while matches!(
                            self.peek_kind(),
                            TokenKind::Pub | TokenKind::Private | TokenKind::Protected
                        ) {
                            self.advance();
                        }
                        let fspan = self.peek().span;
                        let fname = self.advance().text;
                        self.advance(); // ':'
                        let ty = self.parse_type_ref();
                        fields.push(FieldDecl {
                            name: fname,
                            ty,
                            mutable: false,
                            visibility: vis,
                            span: fspan,
                        });
                        self.end_statement(TokenKind::Question);
                    } else {
                        let m = self.parse_method(true)?;
                        methods.push(m);
                    }
                }
                _ => {
                    self.error(&format!(
                        "expected field or method in class body, found {}",
                        self.describe()
                    ));
                    self.resync();
                }
            }
        }
        Some((fields, methods))
    }

    // ------------------------------------------------------------------
    // Interfaces
    // ------------------------------------------------------------------

    fn parse_interface(&mut self) -> Option<InterfaceDef> {
        let start = self.advance().span; // 'interface'
        let name_span = self.peek().span;
        let name = self.expect_ident("interface name")?;
        let type_params = self.parse_type_params();
        let mut extends = Vec::new();
        if self.eat(TokenKind::Extends) {
            extends.push(self.parse_type_ref());
            while self.consume_list_comma(TokenKind::LBrace) {
                extends.push(self.parse_type_ref());
            }
        }
        self.expect(TokenKind::LBrace, "'{' opening interface body");
        let mut methods = Vec::new();
        loop {
            self.skip_newlines();
            match self.peek_kind() {
                TokenKind::RBrace => break,
                TokenKind::Eof => {
                    self.error("unexpected end of file in interface body");
                    return None;
                }
                _ => {
                    let m = self.parse_method(true)?;
                    methods.push(m);
                }
            }
        }
        self.expect(TokenKind::RBrace, "'}' closing interface body");
        Some(InterfaceDef {
            name,
            name_span,
            type_params,
            extends,
            methods,
            span: Span::join(start, self.peek().span),
        })
    }

    // ------------------------------------------------------------------
    // Enums
    // ------------------------------------------------------------------

    fn parse_enum(&mut self) -> Option<EnumDef> {
        let start = self.advance().span; // 'enum'
        let name_span = self.peek().span;
        let name = self.expect_ident("enum name")?;
        let type_params = self.parse_type_params();
        self.expect(TokenKind::LBrace, "'{' opening enum body");
        let mut variants = Vec::new();
        loop {
            self.skip_newlines();
            match self.peek_kind() {
                TokenKind::RBrace => break,
                TokenKind::Eof => {
                    self.error("unexpected end of file in enum body");
                    return None;
                }
                _ => {
                    let vspan = self.peek().span;
                    let vname = match self.expect_ident("variant name") {
                        Some(n) => n,
                        None => {
                            self.resync();
                            continue;
                        }
                    };
                    let payload = if self.eat(TokenKind::LParen) {
                        let ty = self.parse_type_ref();
                        self.expect(TokenKind::RParen, "')' closing variant payload");
                        Some(ty)
                    } else {
                        None
                    };
                    variants.push(EnumVariant {
                        name: vname,
                        payload,
                        span: vspan,
                    });
                    // Variants may be separated by newlines or commas.
                    if !self.eat(TokenKind::Comma) {
                        self.end_statement(TokenKind::RParen);
                    }
                }
            }
        }
        self.expect(TokenKind::RBrace, "'}' closing enum body");
        Some(EnumDef {
            name,
            name_span,
            type_params,
            variants,
            span: Span::join(start, self.peek().span),
        })
    }

    // ------------------------------------------------------------------
    // Methods
    // ------------------------------------------------------------------

    fn parse_method(&mut self, allow_body: bool) -> Option<MethodDef> {
        let start = self.peek().span;
        let mut visibility = Visibility::Private;
        let mut is_override = false;
        let mut is_static = false;
        loop {
            match self.peek_kind() {
                TokenKind::Pub => {
                    self.advance();
                    visibility = Visibility::Pub;
                }
                TokenKind::Protected => {
                    self.advance();
                    visibility = Visibility::Protected;
                }
                TokenKind::Private => {
                    self.advance();
                    visibility = Visibility::Private;
                }
                TokenKind::Override => {
                    self.advance();
                    is_override = true;
                }
                TokenKind::Static => {
                    self.advance();
                    is_static = true;
                }
                _ => break,
            }
        }
        let name_span = self.peek().span;
        let name = self.expect_ident("method name")?;
        let type_params = self.parse_type_params();
        self.expect(TokenKind::LParen, "'(' after method name");
        let params = self.parse_params()?;
        self.expect(TokenKind::RParen, "')' closing parameter list");
        let return_ty = if self.eat(TokenKind::Colon) {
            Some(self.parse_type_ref())
        } else {
            None
        };
        let body = if self.check(TokenKind::LBrace) {
            if !allow_body {
                self.error("interface methods may not have bodies unless they are defaults");
            }
            Some(self.parse_block()?)
        } else {
            None
        };
        if body.is_none() {
            self.end_statement(TokenKind::Question);
        }
        Some(MethodDef {
            name,
            name_span,
            visibility,
            is_static,
            is_override,
            type_params,
            params,
            return_ty,
            body,
            span: Span::join(start, self.peek().span),
        })
    }

    fn parse_params(&mut self) -> Option<Vec<Param>> {
        let mut params = Vec::new();
        loop {
            self.skip_newlines();
            if self.check(TokenKind::RParen) {
                break;
            }
            let pspan = self.peek().span;
            let pname = match self.expect_ident("parameter name") {
                Some(n) => n,
                None => {
                    self.resync();
                    return None;
                }
            };
            self.expect(TokenKind::Colon, "':' after parameter name");
            let ty = self.parse_type_ref();
            let (default, variadic) = if self.eat(TokenKind::DotDotDot) {
                (None, true)
            } else if self.eat(TokenKind::Assign) {
                (Some(self.parse_expr()?), false)
            } else {
                (None, false)
            };
            params.push(Param {
                name: pname,
                ty,
                default,
                variadic,
                span: pspan,
            });
            if !self.consume_list_comma(TokenKind::RParen) {
                break;
            }
        }
        Some(params)
    }

    // ------------------------------------------------------------------
    // Blocks and statements
    // ------------------------------------------------------------------

    fn parse_block(&mut self) -> Option<Block> {
        // Callers disagree on whether the opening '{' has already been
        // consumed: statement arms (`if`/`while`/`for`/`try`) run
        // `expect(LBrace)` first, while method bodies, `else`/`catch`/
        // `finally` blocks and switch-case bodies call this with the brace
        // still pending. Consume the brace only when it is actually next so
        // the first statement token is never swallowed.
        let start = if self.check(TokenKind::LBrace) {
            self.advance().span // '{'
        } else {
            self.peek().span
        };
        let mut stmts = Vec::new();
        loop {
            self.skip_newlines();
            match self.peek_kind() {
                TokenKind::RBrace => break,
                TokenKind::Eof => {
                    self.error("unexpected end of file in block");
                    return None;
                }
                _ => {
                    let s = self.parse_statement()?;
                    stmts.push(s);
                }
            }
        }
        self.expect(TokenKind::RBrace, "'}' closing block");
        Some(Block {
            stmts,
            span: Span::join(start, self.peek().span),
        })
    }

    fn parse_statement(&mut self) -> Option<Stmt> {
        let start = self.peek().span;
        match self.peek_kind() {
            TokenKind::Return => {
                self.advance();
                let value = if self.check(TokenKind::Newline)
                    || self.check(TokenKind::Semicolon)
                    || self.check(TokenKind::RBrace)
                    || self.check(TokenKind::Eof)
                {
                    None
                } else {
                    Some(self.parse_expr()?)
                };
                self.end_statement(TokenKind::Return);
                Some(Stmt::Return(value))
            }
            TokenKind::If => {
                self.advance();
                let cond = self.parse_expr()?;
                self.expect(TokenKind::LBrace, "'{' after if condition");
                let then = self.parse_block()?;
                let else_branch = if self.eat(TokenKind::Else) {
                    if self.check(TokenKind::If) {
                        let inner = self.parse_statement()?;
                        match inner {
                            Stmt::If(s) => Some(ElseBranch::If(s)),
                            _ => {
                                self.error("'else if' must be followed by an if statement");
                                None
                            }
                        }
                    } else {
                        Some(ElseBranch::Block(self.parse_block()?))
                    }
                } else {
                    None
                };
                Some(Stmt::If(Box::new(IfStmt {
                    cond,
                    then,
                    else_branch,
                    span: Span::join(start, self.peek().span),
                })))
            }
            TokenKind::While => {
                self.advance();
                let cond = self.parse_expr()?;
                self.expect(TokenKind::LBrace, "'{' after while condition");
                let body = self.parse_block()?;
                Some(Stmt::While(Box::new(WhileStmt {
                    cond,
                    body,
                    span: Span::join(start, self.peek().span),
                })))
            }
            TokenKind::For => {
                self.advance();
                let vspan = self.peek().span;
                let var = self.expect_ident("loop variable name")?;
                self.expect(TokenKind::In, "'in' in for loop");
                let iter = self.parse_expr()?;
                self.expect(TokenKind::LBrace, "'{' after for expression");
                let body = self.parse_block()?;
                Some(Stmt::ForIn(Box::new(ForInStmt {
                    var,
                    var_span: vspan,
                    iter,
                    body,
                    span: Span::join(start, self.peek().span),
                })))
            }
            TokenKind::Switch => {
                self.advance();
                let subject = self.parse_expr()?;
                self.expect(TokenKind::LBrace, "'{' after switch subject");
                let mut cases = Vec::new();
                loop {
                    self.skip_newlines();
                    match self.peek_kind() {
                        TokenKind::RBrace => break,
                        TokenKind::Eof => {
                            self.error("unexpected end of file in switch");
                            return None;
                        }
                        TokenKind::Case => {
                            self.advance();
                            let vspan = self.peek().span;
                            let first = self.parse_expr()?;
                            let mut values = vec![first];
                            while self.eat(TokenKind::Comma) {
                                values.push(self.parse_expr()?);
                            }
                            self.expect(TokenKind::Colon, "':' after case values");
                            let body = self.parse_block()?;
                            cases.push(SwitchCase {
                                values,
                                is_default: false,
                                body,
                                span: vspan,
                            });
                        }
                        TokenKind::Default => {
                            self.advance();
                            self.expect(TokenKind::Colon, "':' after 'default'");
                            let body = self.parse_block()?;
                            cases.push(SwitchCase {
                                values: vec![],
                                is_default: true,
                                body,
                                span: start,
                            });
                        }
                        _ => {
                            self.error("expected 'case' or 'default' in switch");
                            self.resync();
                        }
                    }
                }
                self.expect(TokenKind::RBrace, "'}' closing switch");
                Some(Stmt::Switch(Box::new(SwitchStmt {
                    subject,
                    cases,
                    span: Span::join(start, self.peek().span),
                })))
            }
            TokenKind::Try => {
                self.advance();
                self.expect(TokenKind::LBrace, "'{' after 'try'");
                let body = self.parse_block()?;
                let (catch_name, catch_body) = if self.eat(TokenKind::Catch) {
                    let name = if self.eat(TokenKind::LParen) {
                        let n = self.expect_ident("catch parameter name")?;
                        self.expect(TokenKind::RParen, "')' after catch parameter");
                        n
                    } else {
                        self.expect_ident("catch parameter name")?
                    };
                    let cb = self.parse_block()?;
                    (Some(name), Some(cb))
                } else {
                    (None, None)
                };
                let finally_body = if self.eat(TokenKind::Finally) {
                    Some(self.parse_block()?)
                } else {
                    None
                };
                if catch_body.is_none() && finally_body.is_none() {
                    self.error("'try' requires a 'catch' or 'finally' clause");
                }
                Some(Stmt::Try(Box::new(TryStmt {
                    body,
                    catch_name,
                    catch_body,
                    finally_body,
                    span: Span::join(start, self.peek().span),
                })))
            }
            TokenKind::Throw => {
                self.advance();
                let e = self.parse_expr()?;
                self.end_statement(TokenKind::Throw);
                Some(Stmt::Throw(e))
            }
            TokenKind::Break => {
                self.advance();
                self.end_statement(TokenKind::Break);
                Some(Stmt::Break)
            }
            TokenKind::Continue => {
                self.advance();
                self.end_statement(TokenKind::Continue);
                Some(Stmt::Continue)
            }
            TokenKind::Mut | TokenKind::Ident => {
                // Declaration or expression statement.
                let mutable = self.eat(TokenKind::Mut);
                if mutable || (self.check(TokenKind::Ident) && self.peek_at(1) == TokenKind::Colon)
                {
                    let dspan = self.peek().span;
                    let name = self.expect_ident("variable name")?;
                    self.expect(TokenKind::Colon, "':' after variable name");
                    let ty = self.parse_type_ref();
                    let init = if self.eat(TokenKind::Assign) {
                        Some(self.parse_expr()?)
                    } else {
                        None
                    };
                    self.end_statement(TokenKind::Question);
                    Some(Stmt::Decl(DeclStmt {
                        name,
                        ty,
                        init,
                        mutable,
                        span: dspan,
                    }))
                } else {
                    let expr = self.parse_expr()?;
                    let expr = self.parse_assign_tail(expr, start)?;
                    self.end_statement(TokenKind::Question);
                    Some(Stmt::Expr(ExprStmt { expr, span: start }))
                }
            }
            _ => {
                let expr = self.parse_expr()?;
                let expr = self.parse_assign_tail(expr, start)?;
                self.end_statement(TokenKind::Question);
                Some(Stmt::Expr(ExprStmt { expr, span: start }))
            }
        }
    }

    /// Consume a trailing assignment or compound-assignment operator and
    /// wrap `expr` in the corresponding AST node.
    fn parse_assign_tail(&mut self, expr: Expr, start: Span) -> Option<Expr> {
        match self.peek_kind() {
            TokenKind::Assign => {
                self.advance();
                let value = self.parse_expr()?;
                Some(Expr::Assign(AssignExpr {
                    target: Box::new(expr),
                    value: Box::new(value),
                    span: start,
                }))
            }
            TokenKind::PlusEq => {
                self.advance();
                let value = self.parse_expr()?;
                Some(Expr::Update(
                    UpdateOp::Add,
                    Box::new(AssignExpr {
                        target: Box::new(expr),
                        value: Box::new(value),
                        span: start,
                    }),
                ))
            }
            TokenKind::MinusEq => {
                self.advance();
                let value = self.parse_expr()?;
                Some(Expr::Update(
                    UpdateOp::Sub,
                    Box::new(AssignExpr {
                        target: Box::new(expr),
                        value: Box::new(value),
                        span: start,
                    }),
                ))
            }
            TokenKind::StarEq => {
                self.advance();
                let value = self.parse_expr()?;
                Some(Expr::Update(
                    UpdateOp::Mul,
                    Box::new(AssignExpr {
                        target: Box::new(expr),
                        value: Box::new(value),
                        span: start,
                    }),
                ))
            }
            TokenKind::SlashEq => {
                self.advance();
                let value = self.parse_expr()?;
                Some(Expr::Update(
                    UpdateOp::Div,
                    Box::new(AssignExpr {
                        target: Box::new(expr),
                        value: Box::new(value),
                        span: start,
                    }),
                ))
            }
            TokenKind::PercentEq => {
                self.advance();
                let value = self.parse_expr()?;
                Some(Expr::Update(
                    UpdateOp::Mod,
                    Box::new(AssignExpr {
                        target: Box::new(expr),
                        value: Box::new(value),
                        span: start,
                    }),
                ))
            }
            _ => Some(expr),
        }
    }

    // ------------------------------------------------------------------
    // Expressions (precedence climbing)
    // ------------------------------------------------------------------

    /// True when the current position holds `< T ... > ::` (a generic type
    /// used in static-access position).
    fn generic_static_follows(&self) -> bool {
        if self.peek_kind() != TokenKind::Lt {
            return false;
        }
        let mut i = self.pos + 1;
        let mut depth = 1i32; // the opening '<' at self.pos
        while let Some(t) = self.tokens.get(i) {
            match t.kind {
                TokenKind::Lt => depth += 1,
                TokenKind::Gt => {
                    depth -= 1;
                    if depth == 0 {
                        return self
                            .tokens
                            .get(i + 1)
                            .is_some_and(|n| n.kind == TokenKind::DoubleColon);
                    }
                }
                TokenKind::Eof => return false,
                _ => {}
            }
            i += 1;
        }
        false
    }

    /// Skip newlines inside an expression when it clearly continues: the
    /// previous token was a continuation operator, or the next line begins
    /// with one.
    fn skip_expr_newlines(&mut self) {
        let mut guard = 0;
        while self.check(TokenKind::Newline) && guard < 64 {
            guard += 1;
            // `??` is handled explicitly because TokenKind::Question doubles
            // as the neutral sentinel passed to end_statement().
            if self.depth > 0
                || self.last_kind.continues_expression()
                || matches!(self.last_kind, TokenKind::Question)
            {
                self.advance();
                continue;
            }
            let mut i = self.pos + 1;
            while i < self.tokens.len() && self.tokens[i].kind == TokenKind::Newline {
                i += 1;
            }
            if let Some(t) = self.tokens.get(i) {
                if t.kind.starts_continuation() {
                    self.advance();
                    continue;
                }
            }
            break;
        }
    }

    pub fn parse_expr(&mut self) -> Option<Expr> {
        self.parse_coalesce()
    }

    fn parse_coalesce(&mut self) -> Option<Expr> {
        self.skip_expr_newlines();
        let mut left = self.parse_or()?;
        loop {
            self.skip_expr_newlines();
            if !self.eat(TokenKind::Question) {
                break;
            }
            let right = self.parse_or()?;
            left = Expr::Coalesce(Box::new(left), Box::new(right));
        }
        Some(left)
    }

    fn parse_or(&mut self) -> Option<Expr> {
        self.skip_expr_newlines();
        let mut left = self.parse_and()?;
        loop {
            let start = self.peek().span;
            if !self.eat(TokenKind::OrOr) {
                break;
            }
            let right = self.parse_and()?;
            left = Expr::Binary(BinaryExpr {
                op: BinOp::Or,
                left: Box::new(left),
                right: Box::new(right),
                span: start,
            });
        }
        Some(left)
    }

    fn parse_and(&mut self) -> Option<Expr> {
        self.skip_expr_newlines();
        let mut left = self.parse_equality()?;
        loop {
            self.skip_expr_newlines();
            let start = self.peek().span;
            if !self.eat(TokenKind::AndAnd) {
                break;
            }
            let right = self.parse_equality()?;
            left = Expr::Binary(BinaryExpr {
                op: BinOp::And,
                left: Box::new(left),
                right: Box::new(right),
                span: start,
            });
        }
        Some(left)
    }

    fn parse_equality(&mut self) -> Option<Expr> {
        self.skip_expr_newlines();
        let mut left = self.parse_relational()?;
        loop {
            let start = self.peek().span;
            let op = if self.eat(TokenKind::EqEq) {
                Some(BinOp::Eq)
            } else if self.eat(TokenKind::NotEq) {
                Some(BinOp::Ne)
            } else {
                None
            };
            let Some(op) = op else { break };
            let right = self.parse_relational()?;
            left = Expr::Binary(BinaryExpr {
                op,
                left: Box::new(left),
                right: Box::new(right),
                span: start,
            });
        }
        Some(left)
    }

    fn parse_relational(&mut self) -> Option<Expr> {
        self.skip_expr_newlines();
        let mut left = self.parse_additive()?;
        loop {
            let start = self.peek().span;
            let op = if self.eat(TokenKind::Lt) {
                Some(BinOp::Lt)
            } else if self.eat(TokenKind::LtEq) {
                Some(BinOp::Le)
            } else if self.eat(TokenKind::Gt) {
                Some(BinOp::Gt)
            } else if self.eat(TokenKind::GtEq) {
                Some(BinOp::Ge)
            } else {
                None
            };
            let Some(op) = op else { break };
            let right = self.parse_additive()?;
            left = Expr::Binary(BinaryExpr {
                op,
                left: Box::new(left),
                right: Box::new(right),
                span: start,
            });
        }
        Some(left)
    }

    fn parse_additive(&mut self) -> Option<Expr> {
        self.skip_expr_newlines();
        let mut left = self.parse_multiplicative()?;
        loop {
            self.skip_expr_newlines();
            let start = self.peek().span;
            let op = if self.eat(TokenKind::Plus) {
                Some(BinOp::Add)
            } else if self.eat(TokenKind::Minus) {
                Some(BinOp::Sub)
            } else if self.eat(TokenKind::DotDot) {
                // '..' is string concatenation or an integer range; the
                // checker decides based on operand types.
                Some(BinOp::Concat)
            } else {
                None
            };
            let Some(op) = op else { break };
            let right = self.parse_multiplicative()?;
            left = Expr::Binary(BinaryExpr {
                op,
                left: Box::new(left),
                right: Box::new(right),
                span: start,
            });
        }
        Some(left)
    }

    fn parse_multiplicative(&mut self) -> Option<Expr> {
        self.skip_expr_newlines();
        let mut left = self.parse_unary()?;
        loop {
            self.skip_expr_newlines();
            let start = self.peek().span;
            let op = if self.eat(TokenKind::Star) {
                Some(BinOp::Mul)
            } else if self.eat(TokenKind::Slash) {
                Some(BinOp::Div)
            } else if self.eat(TokenKind::Percent) {
                Some(BinOp::Mod)
            } else {
                None
            };
            let Some(op) = op else { break };
            let right = self.parse_unary()?;
            left = Expr::Binary(BinaryExpr {
                op,
                left: Box::new(left),
                right: Box::new(right),
                span: start,
            });
        }
        Some(left)
    }

    fn parse_unary(&mut self) -> Option<Expr> {
        if self.eat(TokenKind::Minus) {
            if self.check(TokenKind::IntLit)
                && !matches!(self.peek_at(1), TokenKind::Dot | TokenKind::LParen)
            {
                return self.parse_integer_literal(true).map(Expr::Int);
            }
            let e = self.parse_unary()?;
            return Some(Expr::Unary(UnaryOp::Neg, Box::new(e)));
        }
        if self.eat(TokenKind::Bang) {
            let e = self.parse_unary()?;
            return Some(Expr::Unary(UnaryOp::Not, Box::new(e)));
        }
        self.parse_postfix()
    }

    fn parse_postfix(&mut self) -> Option<Expr> {
        let mut expr = self.parse_primary()?;
        loop {
            if self.check(TokenKind::LParen) {
                let start = self.peek().span;
                self.advance();
                let args = self.parse_args()?;
                self.expect(TokenKind::RParen, "')' closing call");
                expr = Expr::Call(CallExpr {
                    callee: Box::new(expr),
                    args,
                    span: start,
                });
            } else if self.eat(TokenKind::Dot) {
                let name_span = self.peek().span;
                let name = self.expect_ident("member name after '.'")?;
                expr = Expr::Member(MemberExpr {
                    obj: Box::new(expr),
                    name,
                    span: name_span,
                });
            } else {
                break;
            }
        }
        Some(expr)
    }

    fn parse_args(&mut self) -> Option<Vec<Arg>> {
        let mut args = Vec::new();
        loop {
            self.skip_newlines();
            if self.check(TokenKind::RParen) {
                break;
            }
            let aspan = self.peek().span;
            // Named argument: ident ':' expr (but not a range/type).
            if self.check(TokenKind::Ident) && self.peek_at(1) == TokenKind::Colon {
                let name = self.advance().text;
                self.advance(); // ':'
                let e = self.parse_expr()?;
                args.push(Arg {
                    name: Some(name),
                    expr: e,
                    spread: false,
                    span: aspan,
                });
            } else {
                let spread = self.eat(TokenKind::DotDotDot);
                let e = self.parse_expr()?;
                args.push(Arg {
                    name: None,
                    expr: e,
                    spread,
                    span: aspan,
                });
            }
            if !self.consume_list_comma(TokenKind::RParen) {
                break;
            }
        }
        Some(args)
    }

    fn parse_integer_literal(&mut self, negative: bool) -> Option<i64> {
        let token = self.advance();
        let clean = token.text.replace('_', "");
        let (digits, radix) = if let Some(digits) = clean
            .strip_prefix("0x")
            .or_else(|| clean.strip_prefix("0X"))
        {
            (digits, 16)
        } else if let Some(digits) = clean.strip_prefix("0o") {
            (digits, 8)
        } else if let Some(digits) = clean.strip_prefix("0b") {
            (digits, 2)
        } else {
            (clean.as_str(), 10)
        };
        let signed = if negative {
            format!("-{digits}")
        } else {
            digits.to_string()
        };
        match i64::from_str_radix(&signed, radix) {
            Ok(value) => Some(value),
            Err(_) => {
                self.diags.err_at(
                    "P001",
                    "invalid or out-of-range integer literal",
                    token.span,
                );
                None
            }
        }
    }

    fn parse_float_literal(&mut self) -> Option<f64> {
        let token = self.advance();
        let clean = token.text.trim_end_matches(['f', 'F']).replace('_', "");
        match clean.parse::<f64>() {
            Ok(value) => Some(value),
            Err(_) => {
                self.diags
                    .err_at("P001", "invalid float literal", token.span);
                None
            }
        }
    }

    fn parse_primary(&mut self) -> Option<Expr> {
        self.skip_expr_newlines();
        let start = self.peek().span;
        match self.peek_kind() {
            TokenKind::IntLit => self.parse_integer_literal(false).map(Expr::Int),
            TokenKind::FloatLit => self.parse_float_literal().map(Expr::Float),
            TokenKind::StringLit => {
                let t = self.advance();
                Some(Expr::String(t.text))
            }
            TokenKind::CharLit => {
                let t = self.advance();
                Some(Expr::Char(t.text.chars().next().unwrap_or('?')))
            }
            TokenKind::True => {
                self.advance();
                Some(Expr::Bool(true))
            }
            TokenKind::False => {
                self.advance();
                Some(Expr::Bool(false))
            }
            TokenKind::Null => {
                self.advance();
                Some(Expr::Null)
            }
            TokenKind::SelfKw => {
                // `Self { ... }` object construction (only valid in factories).
                self.advance();
                if self.eat(TokenKind::LBrace) {
                    let init = self.parse_self_init_body()?;
                    Some(Expr::SelfInit(Box::new(init)))
                } else {
                    self.error(
                        "'Self' in expression position requires 'Self { ... }' construction",
                    );
                    Some(Expr::Ident("Self".into()))
                }
            }
            TokenKind::SelfV => {
                self.advance();
                Some(Expr::Ident("self".into()))
            }
            TokenKind::Super => {
                self.advance();
                if !self.eat(TokenKind::Dot) {
                    self.error("'super' must be followed by '.method(...)'");
                    return None;
                }
                let name = self.expect_ident("method name after 'super.'")?;
                self.expect(TokenKind::LParen, "'(' after super method name");
                let args = self.parse_args()?;
                self.expect(TokenKind::RParen, "')' closing super call");
                Some(Expr::SuperCall(SuperCallExpr {
                    name,
                    args,
                    span: start,
                }))
            }
            TokenKind::Match => {
                self.advance();
                let subject = self.parse_expr()?;
                self.expect(TokenKind::LBrace, "'{' after match subject");
                let mut arms = Vec::new();
                loop {
                    self.skip_newlines();
                    if self.eat(TokenKind::RBrace) {
                        break;
                    }
                    let aspan = self.peek().span;
                    let pattern = self.parse_pattern()?;
                    self.expect(TokenKind::FatArrow, "'=>' after match pattern");
                    let body = self.parse_expr()?;
                    arms.push(MatchArm {
                        pattern,
                        guard: None,
                        body,
                        span: aspan,
                    });
                    // Arms are separated by newlines or commas. Arm bodies
                    // are complete expressions, so a following arm may never
                    // continue the previous one (a next-line list pattern
                    // starting with '[' must not look like a postfix
                    // continuation).
                    if !self.eat(TokenKind::Comma) {
                        self.skip_arm_newlines();
                    }
                }
                Some(Expr::Match(Box::new(MatchExpr {
                    subject: Box::new(subject),
                    arms,
                    span: start,
                })))
            }
            TokenKind::LParen => {
                self.advance();
                let e = self.parse_expr()?;
                self.expect(TokenKind::RParen, "')' closing parenthesis");
                Some(e)
            }
            TokenKind::LBracket => {
                self.advance();
                let mut elements = Vec::new();
                loop {
                    self.skip_newlines();
                    if self.check(TokenKind::RBracket) {
                        break;
                    }
                    elements.push(self.parse_expr()?);
                    if !self.consume_list_comma(TokenKind::RBracket) {
                        break;
                    }
                }
                self.expect(TokenKind::RBracket, "']' closing list literal");
                Some(Expr::List(elements))
            }
            TokenKind::LBrace => {
                // Map literal: { key: value, ... }
                self.advance();
                let mut entries = Vec::new();
                loop {
                    self.skip_newlines();
                    if self.check(TokenKind::RBrace) {
                        break;
                    }
                    let key = self.parse_expr()?;
                    self.expect(TokenKind::Colon, "':' in map literal");
                    let value = self.parse_expr()?;
                    entries.push((key, value));
                    if !self.consume_list_comma(TokenKind::RBrace) {
                        break;
                    }
                }
                self.expect(TokenKind::RBrace, "'}' closing map literal");
                Some(Expr::Map(entries))
            }
            TokenKind::Ident => {
                let t = self.advance();
                // Static access: Type::name (possibly generic: List<Int>::new()).
                // `<` only opens generics when the balanced `>` is followed by `::`.
                if self.check(TokenKind::DoubleColon) || self.generic_static_follows() {
                    let ty = if self.check(TokenKind::Lt) {
                        self.advance();
                        self.skip_newlines();
                        let mut args = vec![self.parse_type_ref()];
                        while self.consume_list_comma(TokenKind::Gt) {
                            args.push(self.parse_type_ref());
                        }
                        self.skip_newlines();
                        self.expect(TokenKind::Gt, "'>' closing generic type");
                        TypeRef {
                            base: TypeBase::Generic(t.text.clone(), args),
                            nullable: false,
                            span: start,
                        }
                    } else {
                        TypeRef {
                            base: TypeBase::Named(t.text.clone()),
                            nullable: false,
                            span: start,
                        }
                    };
                    self.expect(TokenKind::DoubleColon, "'::' for static access");
                    let name = self.expect_ident("name after '::'")?;
                    return Some(Expr::StaticAccess(StaticAccessExpr {
                        ty,
                        name,
                        span: start,
                    }));
                }
                Some(Expr::Ident(t.text))
            }
            _ => {
                self.error(&format!("expected expression, found {}", self.describe()));
                None
            }
        }
    }

    fn parse_self_init_body(&mut self) -> Option<SelfInitExpr> {
        let start = Span::new(
            self.peek().span.file,
            self.peek().span.start,
            self.peek().span.end,
        );
        let mut super_init = None;
        let mut fields = Vec::new();
        loop {
            self.skip_newlines();
            if self.eat(TokenKind::RBrace) {
                break;
            }
            if self.check(TokenKind::Super) {
                self.advance();
                self.expect(TokenKind::Colon, "':' after 'super' in construction");
                let e = self.parse_expr()?;
                super_init = Some(e);
                if !self.eat(TokenKind::Comma) {
                    self.end_statement(TokenKind::Question);
                }
                continue;
            }
            let fname = self.expect_ident("field name in construction")?;
            let value = if self.eat(TokenKind::Colon) {
                self.parse_expr()?
            } else {
                Expr::Ident(fname.clone())
            };
            fields.push((fname, value));
            if !self.eat(TokenKind::Comma) {
                self.end_statement(TokenKind::Question);
            }
        }
        Some(SelfInitExpr {
            super_init,
            fields,
            span: start,
        })
    }

    // ------------------------------------------------------------------
    // Patterns
    // ------------------------------------------------------------------

    /// Consume the separator after a match-arm body (newline or ';'). The
    /// next arm is never a continuation of the previous body, so newlines
    /// are skipped unconditionally even when the next arm starts with an
    /// operator-looking token such as '['.
    fn skip_arm_newlines(&mut self) {
        if self.eat(TokenKind::Semicolon) {
            return;
        }
        if self.check(TokenKind::Newline) {
            self.skip_newlines();
            return;
        }
        if matches!(self.peek_kind(), TokenKind::Eof | TokenKind::RBrace) {
            return;
        }
        self.error("missing statement terminator (newline or ';') after match arm");
        self.skip_newlines();
    }

    fn parse_pattern(&mut self) -> Option<Pattern> {
        match self.peek_kind() {
            TokenKind::Ident if self.peek().text == "_" => {
                self.advance();
                Some(Pattern::Wildcard)
            }
            TokenKind::IntLit => self.parse_integer_literal(false).map(Pattern::LiteralInt),
            TokenKind::FloatLit => self.parse_float_literal().map(Pattern::LiteralFloat),
            TokenKind::StringLit => {
                let t = self.advance();
                Some(Pattern::LiteralString(t.text))
            }
            TokenKind::CharLit => {
                let t = self.advance();
                Some(Pattern::LiteralChar(t.text.chars().next().unwrap_or('?')))
            }
            TokenKind::True => {
                self.advance();
                Some(Pattern::LiteralBool(true))
            }
            TokenKind::False => {
                self.advance();
                Some(Pattern::LiteralBool(false))
            }
            TokenKind::LBracket => {
                self.advance();
                let mut pats = Vec::new();
                loop {
                    self.skip_newlines();
                    if self.check(TokenKind::RBracket) {
                        break;
                    }
                    pats.push(self.parse_pattern()?);
                    if !self.eat(TokenKind::Comma) {
                        break;
                    }
                }
                // The loop above breaks on the closing ']' without consuming
                // it (unless the list was empty); consume it now so the
                // caller sees the token after the pattern list.
                self.eat(TokenKind::RBracket);
                Some(Pattern::List(pats))
            }
            TokenKind::Ident => {
                let name = self.advance().text;
                if self.check(TokenKind::DoubleColon) {
                    // Qualified enum variant pattern: Color::Red
                    self.advance();
                    let vname = match self.peek_kind() {
                        TokenKind::Ident => self.advance().text,
                        _ => {
                            self.error("expected variant name after '::' in pattern");
                            return None;
                        }
                    };
                    if self.check(TokenKind::LParen) {
                        self.advance();
                        let mut sub = Vec::new();
                        loop {
                            self.skip_newlines();
                            if self.eat(TokenKind::RParen) {
                                break;
                            }
                            sub.push(self.parse_pattern()?);
                            if !self.eat(TokenKind::Comma) {
                                break;
                            }
                        }
                        self.eat(TokenKind::RParen);
                        Some(Pattern::Variant(vname, sub))
                    } else {
                        Some(Pattern::Variant(vname, Vec::new()))
                    }
                } else if self.check(TokenKind::LParen) {
                    // Variant pattern: Name(p1, p2)
                    self.advance();
                    let mut sub = Vec::new();
                    loop {
                        self.skip_newlines();
                        if self.eat(TokenKind::RParen) {
                            break;
                        }
                        sub.push(self.parse_pattern()?);
                        if !self.eat(TokenKind::Comma) {
                            break;
                        }
                    }
                    self.eat(TokenKind::RParen);
                    Some(Pattern::Variant(name, sub))
                } else {
                    Some(Pattern::Bind(name))
                }
            }
            _ => {
                self.error(&format!("expected pattern, found {}", self.describe()));
                None
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::lexer::Lexer;

    fn parser(text: &str) -> Parser {
        let mut diags = Diagnostics::default();
        Parser::new(Lexer::new(0, text).tokenize(&mut diags))
    }

    #[test]
    fn numeric_patterns_match_expression_values() {
        for (text, expected) in [("0x2a", 42), ("0o52", 42), ("0b10_1010", 42), ("42", 42)] {
            assert!(matches!(parser(text).parse_expr(), Some(Expr::Int(v)) if v == expected));
            assert!(
                matches!(parser(text).parse_pattern(), Some(Pattern::LiteralInt(v)) if v == expected),
                "{text}"
            );
        }
    }

    #[test]
    fn valid_numeric_literal_boundaries() {
        for text in ["9223372036854775807", "0x7fff_ffff_ffff_ffff"] {
            assert!(matches!(
                parser(text).parse_expr(),
                Some(Expr::Int(i64::MAX))
            ));
        }
        for text in ["-9223372036854775808", "-0x8000000000000000"] {
            assert!(matches!(
                parser(text).parse_expr(),
                Some(Expr::Int(i64::MIN))
            ));
        }
        for text in ["1.25", "1.25f", "1_2.5e-1F"] {
            assert!(matches!(parser(text).parse_expr(), Some(Expr::Float(v)) if v == 1.25));
            assert!(
                matches!(parser(text).parse_pattern(), Some(Pattern::LiteralFloat(v)) if v == 1.25)
            );
        }
        assert!(matches!(
            parser("-1.toString()").parse_expr(),
            Some(Expr::Unary(UnaryOp::Neg, _))
        ));
    }

    #[test]
    fn invalid_numeric_literals_are_rejected() {
        for text in [
            "9223372036854775808",
            "0x8000000000000000",
            "0b_",
            "1e+",
            "1.2.3",
            "1e2e3",
        ] {
            for pattern in [false, true] {
                let mut p = parser(text);
                if pattern {
                    p.parse_pattern();
                } else {
                    p.parse_expr();
                }
                assert!(
                    !p.diags.items.is_empty(),
                    "accepted {text}, pattern={pattern}"
                );
            }
        }
    }

    #[test]
    fn trailing_commas_are_accepted_across_delimited_lists() {
        let text = r#"
package trailing

interface Named {}

interface Sized<T,
> extends Named,
{
    size(value: T,
    ): Int
}

enum Color<T,
> {
    Red
    Blue(T),
}

class Pair<A,
    B,
> implements Named,
{
    first: A
    second: B

    pub static make(first: A,
        second: B,
    ): Self {
        return Self {
            first,
            second,
        }
    }
}

class Main {
    pub static run(args: List<String>,
    ): Int {
        values: List<Int,
        > = [
            1,
            2,
        ]
        map: Map<String, Int,
        > = {
            "a": 1,
            "b": 2,
        }
        pair: Pair<Int, Int,
        > = Pair<Int, Int,
        >::make(1, 2,
        )
        call(pair.first, pair.second,
        )
        return match pair.first {
            [1, x,
            ] => x,
            _ => 0,
        }
    }
}
"#;
        let mut p = parser(text);
        assert!(p.parse_program().is_some());
        assert!(
            p.diags.items.is_empty(),
            "parser diagnostics: {:?}",
            p.diags.items
        );
    }

    #[test]
    fn malformed_comma_lists_are_rejected() {
        for text in ["foo(, 1)", "[1,, 2]", "{ \"a\": 1,, }"] {
            let mut p = parser(text);
            p.parse_expr();
            assert!(!p.diags.items.is_empty(), "accepted malformed list: {text}");
        }

        let mut p = parser("List<Int,,>");
        p.parse_type_ref();
        assert!(!p.diags.items.is_empty(), "accepted malformed generic list");

        let mut p = parser("foo(1, ]");
        p.parse_expr();
        assert!(
            !p.diags.items.is_empty(),
            "accepted unexpected token after comma"
        );

        let mut p = parser(
            "package bad\n\
             class Main {\n\
                 pub static run(): Int {\n\
                     switch 1 {\n\
                         case 1, 2,: {}\n\
                     }\n\
                     return 0\n\
                 }\n\
             }\n",
        );
        p.parse_program();
        assert!(
            !p.diags.items.is_empty(),
            "accepted trailing comma before case ':'"
        );
    }
}
