//! Recursive-descent parsing.
//!
//! Port of internal/parser/parser.go.

use crate::ast::*;
use crate::diagnostic::{Diagnostics, CODE_PARSER_BARE_RETURN_ARROW, CODE_PARSER_STATEMENT_SEPARATOR};
use crate::gocompat::go_parse_int_base0;
use crate::lexer::{Token, TokenKind};
use crate::source::{Source, Span};
use crate::types::Kind;
use std::collections::HashSet;

const DEFAULT_MAX_ERRORS: usize = 50;

// Precedence levels (tighter = higher)
const PREC_LOWEST: u8 = 0;
const PREC_ASSIGNMENT: u8 = 1;
const PREC_COALESCING: u8 = 2;
const PREC_OR: u8 = 3;
const PREC_AND: u8 = 4;
const PREC_EQUALITY: u8 = 5;
const PREC_COMPARISON: u8 = 6;
const PREC_BIT_OR: u8 = 7;
const PREC_BIT_XOR: u8 = 8;
const PREC_BIT_AND: u8 = 9;
const PREC_SHIFT: u8 = 10;
const PREC_CONCAT: u8 = 11;
const PREC_TERM: u8 = 12;
const PREC_FACTOR: u8 = 13;
const PREC_UNARY: u8 = 14;
const PREC_PRIMARY: u8 = 15;

fn eof_token() -> Token {
    Token {
        kind: TokenKind::Eof,
        lexeme: String::new(),
        span: Span::default(),
    }
}

pub struct Parser<'a> {
    src: &'a Source,
    tokens: Vec<Token>,
    pos: usize,
    diags: Diagnostics,
    err_count: usize,
    max_errors: usize,
    seen_package: bool,
    seen_func: bool,
    allow_struct_literal: bool,
    type_depth: usize,
    pending_type_gts: usize,
    generic_types: HashSet<String>,
}

impl<'a> Parser<'a> {
    pub fn new(src: &'a Source, tokens: Vec<Token>) -> Parser<'a> {
        Parser {
            src,
            tokens,
            pos: 0,
            diags: Diagnostics::new(),
            err_count: 0,
            max_errors: DEFAULT_MAX_ERRORS,
            seen_package: false,
            seen_func: false,
            allow_struct_literal: true,
            type_depth: 0,
            pending_type_gts: 0,
            generic_types: HashSet::new(),
        }
    }

    pub fn parse(mut self) -> (Program, Diagnostics) {
        let mut prog = Program::default();

        // Package declaration (leading blank lines allowed)
        while self.match_kind(TokenKind::Newline) {}

        if self.match_kind(TokenKind::Package) {
            self.seen_package = true;
            if self.check(TokenKind::Identifier) {
                prog.module = self.parse_dotted_name();
            } else {
                self.add_error("P001", "expected module name after 'package'", self.peek().span.clone());
            }
            self.expect_newline_or_semicolon();
        }

        while !self.is_at_end() && self.err_count < self.max_errors {
            if self.match_kind(TokenKind::Package) {
                if !self.seen_package {
                    self.seen_package = true;
                    if self.check(TokenKind::Identifier) {
                        prog.module = self.parse_dotted_name();
                    } else {
                        self.add_error("P001", "expected module name after 'package'", self.peek().span.clone());
                    }
                    self.expect_newline_or_semicolon();
                } else {
                    self.add_error("P049", "duplicate 'package' declaration", self.peek().span.clone());
                    if self.check(TokenKind::Identifier) {
                        self.advance();
                    }
                    self.expect_newline_or_semicolon();
                }
            } else if self.match_kind(TokenKind::Use) {
                if !self.seen_package {
                    self.add_error("P048", "file must start with a 'package' declaration", self.peek().span.clone());
                } else if self.seen_func {
                    self.add_error("P050", "'use' declaration must appear before any function declaration", self.peek().span.clone());
                }
                if let Some(use_decl) = self.parse_use() {
                    prog.uses.push(use_decl);
                }
                self.expect_newline_or_semicolon();
            } else if self.match_kind(TokenKind::Enum) {
                if !self.seen_package {
                    self.add_error("P048", "file must start with a 'package' declaration", self.peek().span.clone());
                }
                if let Some(enum_decl) = self.parse_enum_decl() {
                    prog.enums.push(enum_decl);
                }
                self.expect_newline_or_semicolon();
            } else if self.match_kind(TokenKind::Struct) {
                if !self.seen_package {
                    self.add_error("P048", "file must start with a 'package' declaration", self.peek().span.clone());
                }
                if let Some(struct_decl) = self.parse_struct_decl() {
                    prog.structs.push(struct_decl);
                }
                self.expect_newline_or_semicolon();
            } else if self.match_kind(TokenKind::Trait) {
                if !self.seen_package {
                    self.add_error("P048", "file must start with a 'package' declaration", self.peek().span.clone());
                }
                if let Some(trait_decl) = self.parse_trait_decl() {
                    prog.traits.push(trait_decl);
                }
                self.expect_newline_or_semicolon();
            } else if self.match_kind(TokenKind::Func) {
                if !self.seen_package {
                    self.add_error("P048", "file must start with a 'package' declaration", self.peek().span.clone());
                }
                self.seen_func = true;
                if let Some(f) = self.parse_function() {
                    // parse_function in Go registers module via fn.Module set later
                    prog.funcs.push(f);
                }
                self.expect_newline_or_semicolon();
            } else if self.match_kind(TokenKind::Newline) || self.match_kind(TokenKind::Semicolon) {
                // Skip blank lines
            } else {
                if !self.seen_package {
                    self.add_error("P048", "file must start with a 'package' declaration", self.peek().span.clone());
                } else {
                    self.add_error("P002", "expected function declaration or use", self.peek().span.clone());
                }
                self.synchronize();
            }
        }

        if !self.seen_package {
            self.add_error("P048", "file must start with a 'package' declaration", self.peek().span.clone());
        }

        if self.err_count >= self.max_errors {
            self.diags.add_error("P045", "too many parse errors; stopping", Span::default());
        }

        (prog, self.diags)
    }

    // ---- use declarations ----

    fn parse_use(&mut self) -> Option<UseDecl> {
        let mut decl = UseDecl {
            span: self.previous().span.clone(),
            source_type: String::new(),
            path: String::new(),
            checksum: String::new(),
            insecure: false,
        };

        if !self.check(TokenKind::Identifier) {
            self.add_error("P100", "expected 'url:' or 'file:' after 'use'", self.peek().span.clone());
            return None;
        }
        let src_tok = self.advance();
        if !self.match_kind(TokenKind::Colon) {
            self.add_error("P102", "expected ':' after source type", self.peek().span.clone());
            return None;
        }
        match src_tok.lexeme.as_str() {
            "url" => decl.source_type = "url".to_string(),
            "file" => decl.source_type = "file".to_string(),
            _ => {
                self.add_error(
                    "P103",
                    &format!("expected 'url:' or 'file:', got '{}:'", src_tok.lexeme),
                    src_tok.span.clone(),
                );
                return None;
            }
        }

        let (path, _span) = match self.parse_use_value() {
            Some(v) => v,
            None => {
                self.add_error(
                    "P101",
                    &format!("expected path value after '{}:'", decl.source_type),
                    self.peek().span.clone(),
                );
                return None;
            }
        };
        decl.path = path;

        let mut seen_checksum = false;
        let mut seen_insecure = false;
        while !self.check_newline_or_semicolon() && !self.is_at_end() {
            if !self.check(TokenKind::Identifier) {
                self.add_error("P104", "expected flag name after use value", self.peek().span.clone());
                self.advance();
                continue;
            }
            let flag_tok = self.advance();
            if !self.match_kind(TokenKind::Colon) {
                self.add_error("P105", "expected ':' after flag name", self.peek().span.clone());
                break;
            }
            match flag_tok.lexeme.as_str() {
                "checksum" => {
                    if seen_checksum {
                        self.add_error("P106", "duplicate 'checksum' flag", flag_tok.span.clone());
                    }
                    seen_checksum = true;

                    let checksum_value = match self.parse_checksum_value() {
                        Some((v, _s)) => v,
                        None => {
                            self.add_error(
                                "P107",
                                "expected checksum in the form 'sha256:<64-hex>'",
                                self.peek().span.clone(),
                            );
                            break;
                        }
                    };
                    match normalize_checksum(&checksum_value) {
                        Some(c) => decl.checksum = c,
                        None => {
                            self.add_error("P108", "checksum must use the form 'sha256:<64-hex>'", self.peek().span.clone());
                            break;
                        }
                    }
                }
                "insecure" => {
                    if seen_insecure {
                        self.add_error("P109", "duplicate 'insecure' flag", flag_tok.span.clone());
                    }
                    seen_insecure = true;
                    if !self.check(TokenKind::BoolLiteral) {
                        self.add_error("P110", "expected 'true' or 'false' after 'insecure:'", self.peek().span.clone());
                        break;
                    }
                    let val_tok = self.advance();
                    match val_tok.lexeme.as_str() {
                        "true" => decl.insecure = true,
                        "false" => decl.insecure = false,
                        _ => {}
                    }
                }
                _ => {
                    self.add_error("P111", &format!("unknown flag '{}'", flag_tok.lexeme), flag_tok.span.clone());
                }
            }
        }

        if decl.source_type == "url" {
            if decl.path.starts_with("http://") && !decl.insecure {
                self.add_error("P112", "http URLs require insecure:true flag", decl.span.clone());
            }
            if decl.path.starts_with("https://") && decl.checksum.is_empty() && !decl.insecure {
                self.add_error("P113", "sha-256 checksum or insecure:true is required for https URLs", decl.span.clone());
            }
        }

        Some(decl)
    }

    fn parse_use_value(&mut self) -> Option<(String, Span)> {
        if self.check(TokenKind::StringLiteral) {
            let tok = self.advance();
            return Some((tok.lexeme, tok.span));
        }
        if self.is_at_end() || self.check_newline_or_semicolon() {
            return None;
        }

        let start = self.peek().span.start;
        let mut end = start;
        while end < self.src.content.len() && !is_use_value_terminator(self.src.content[end]) {
            end += 1;
        }
        if end == start {
            return None;
        }

        while !self.is_at_end() && self.peek().span.start < end {
            self.advance();
        }
        let text = String::from_utf8_lossy(&self.src.content[start..end]).to_string();
        Some((text, self.src.span_from_range(start, end)))
    }

    fn parse_checksum_value(&mut self) -> Option<(String, Span)> {
        if self.check(TokenKind::Identifier) && self.peek().lexeme == "sha256" {
            let algorithm = self.advance();
            if !self.match_kind(TokenKind::Colon) {
                return None;
            }
            let (value, span) = self.parse_use_value()?;
            let combined = self.src.span_from_range(algorithm.span.start, span.end);
            return Some((format!("sha256:{}", value), combined));
        }
        self.parse_use_value()
    }

    fn parse_dotted_name(&mut self) -> String {
        let mut parts = vec![self.advance().lexeme];
        while self.match_kind(TokenKind::Dot) {
            if !self.check(TokenKind::Identifier) {
                self.add_error("P095", "expected identifier after '.'", self.peek().span.clone());
                break;
            }
            parts.push(self.advance().lexeme);
        }
        parts.join(".")
    }

    fn parse_generic_parameter_list(&mut self) -> Vec<String> {
        if !self.match_kind(TokenKind::Lt) {
            return Vec::new();
        }
        let mut names = Vec::new();
        while !self.check(TokenKind::Gt) && !self.is_at_end() {
            if !self.check(TokenKind::Identifier) {
                self.add_error("P005", "expected generic type parameter", self.peek().span.clone());
                break;
            }
            names.push(self.advance().lexeme);
            if !self.match_kind(TokenKind::Comma) {
                break;
            }
        }
        if !self.match_kind(TokenKind::Gt) {
            self.add_error("P005", "expected '>' after generic type parameters", self.peek().span.clone());
        }
        names
    }

    fn skip_generic_parameter_list(&mut self) {
        let _ = self.parse_generic_parameter_list();
    }

    // ---- enum / struct / trait declarations ----

    fn parse_enum_decl(&mut self) -> Option<EnumDecl> {
        if !self.check(TokenKind::Identifier) {
            self.add_error("P076", "expected enum name", self.peek().span.clone());
            return None;
        }
        let name_tok = self.advance();
        self.skip_generic_parameter_list();
        let mut decl = EnumDecl {
            span: name_tok.span.clone(),
            name: name_tok.lexeme,
            variants: Vec::new(),
        };

        if !self.match_kind(TokenKind::LBrace) {
            self.add_error("P081", "expected '{' after enum name", self.peek().span.clone());
            return None;
        }
        while self.match_kind(TokenKind::Newline) {}

        while !self.check(TokenKind::RBrace) && !self.is_at_end() {
            while self.match_kind(TokenKind::Newline) {}
            if self.check(TokenKind::RBrace) {
                break;
            }
            if !self.check(TokenKind::Identifier) {
                self.add_error("P096", "expected variant name in enum", self.peek().span.clone());
                break;
            }
            let var_tok = self.advance();
            let mut variant = EnumVariant {
                span: var_tok.span.clone(),
                name: var_tok.lexeme,
                value: None,
                resolved_int: 0,
            };
            if self.match_kind(TokenKind::Assign) {
                if self.check(TokenKind::IntLiteral) {
                    let val_tok = self.advance();
                    variant.value = Some(go_parse_int_base0(&val_tok.lexeme).unwrap_or(0));
                } else {
                    self.add_error("P097", "expected integer literal after '=' in enum variant", self.peek().span.clone());
                }
            }
            decl.variants.push(variant);

            if !self.match_kind(TokenKind::Comma) {
                break;
            }
            if self.check(TokenKind::RBrace) || self.check(TokenKind::Semicolon) {
                break;
            }
        }

        if !self.match_kind(TokenKind::RBrace) {
            self.add_error("P084", "expected '}' after enum variants", self.peek().span.clone());
        }
        Some(decl)
    }

    fn parse_struct_decl(&mut self) -> Option<StructDecl> {
        if !self.check(TokenKind::Identifier) {
            self.add_error("P077", "expected struct name", self.peek().span.clone());
            return None;
        }
        let name_tok = self.advance();
        let saved_generic_types = self.generic_types.clone();
        let generic_types = self.parse_generic_parameter_list();
        self.generic_types.extend(generic_types);
        let mut decl = StructDecl {
            span: name_tok.span.clone(),
            name: name_tok.lexeme.clone(),
            fields: Vec::new(),
            methods: Vec::new(),
        };

        if !self.match_kind(TokenKind::LBrace) {
            self.add_error("P082", "expected '{' after struct name", self.peek().span.clone());
            return None;
        }
        while self.match_kind(TokenKind::Newline) {}

        while !self.check(TokenKind::RBrace) && !self.is_at_end() {
            while self.match_kind(TokenKind::Newline) {}
            if self.check(TokenKind::RBrace) {
                break;
            }

            let is_pub = self.match_kind(TokenKind::Pub);
            let is_mut = self.match_kind(TokenKind::Mut);

            if self.match_kind(TokenKind::Func) {
                if let Some(mut f) = self.parse_function() {
                    f.struct_name = decl.name.clone();
                    f.name = format!("{}.{}", decl.name, f.name);
                    f.is_pub = is_pub;
                    f.is_mut = is_mut;
                    let self_span = f.span.clone();
                    let self_param = Parameter {
                        span: self_span.clone(),
                        name: "_self".to_string(),
                        ty: TypeAnnotation::named(self_span, &decl.name),
                        variadic: false,
                    };
                    f.parameters.insert(0, self_param);
                    decl.methods.push(f);
                }
                continue;
            }

            if !self.check(TokenKind::Identifier) {
                self.add_error("P098", "expected field name or 'func' in struct", self.peek().span.clone());
                self.synchronize();
                continue;
            }
            let field_tok = self.advance();
            if !self.match_kind(TokenKind::Colon) {
                self.add_error("P087", "expected ':' after field name", self.peek().span.clone());
                self.synchronize();
                continue;
            }
            let type_ann = match self.parse_type_annotation() {
                Some(t) => t,
                None => {
                    self.add_error("P088", "expected field type", self.peek().span.clone());
                    self.synchronize();
                    continue;
                }
            };
            decl.fields.push(StructField {
                span: field_tok.span.clone(),
                name: field_tok.lexeme,
                ty: type_ann,
                is_mut,
                is_pub,
            });

            if !self.match_kind(TokenKind::Comma) {
                if !self.check(TokenKind::RBrace) && !self.check(TokenKind::Newline) {
                    break;
                }
            }
        }

        if !self.match_kind(TokenKind::RBrace) {
            self.add_error("P085", "expected '}' after struct fields", self.peek().span.clone());
        }
        self.generic_types = saved_generic_types;
        Some(decl)
    }

    fn parse_trait_decl(&mut self) -> Option<TraitDecl> {
        if !self.check(TokenKind::Identifier) {
            self.add_error("P078", "expected trait name", self.peek().span.clone());
            return None;
        }
        let name_tok = self.advance();
        let mut decl = TraitDecl {
            span: name_tok.span.clone(),
            name: name_tok.lexeme,
            methods: Vec::new(),
        };

        if !self.match_kind(TokenKind::LBrace) {
            self.add_error("P083", "expected '{' after trait name", self.peek().span.clone());
            return None;
        }
        while self.match_kind(TokenKind::Newline) {}

        while !self.check(TokenKind::RBrace) && !self.is_at_end() {
            while self.match_kind(TokenKind::Newline) {}
            if self.check(TokenKind::RBrace) {
                break;
            }

            let is_mut = self.match_kind(TokenKind::Mut);
            if !self.match_kind(TokenKind::Func) {
                self.add_error("P072", "expected 'func' in trait declaration", self.peek().span.clone());
                self.synchronize();
                continue;
            }
            if let Some(mut f) = self.parse_trait_method() {
                f.is_mut = is_mut;
                decl.methods.push(f);
            }
        }

        if !self.match_kind(TokenKind::RBrace) {
            self.add_error("P086", "expected '}' after trait methods", self.peek().span.clone());
        }
        Some(decl)
    }

    fn parse_trait_method(&mut self) -> Option<Function> {
        let start = self.previous().span.clone();

        if !self.check(TokenKind::Identifier) {
            self.add_error("P079", "expected method name", self.peek().span.clone());
            return None;
        }
        let name_tok = self.advance();
        let saved_generic_types = self.generic_types.clone();
        let generic_types = self.parse_generic_parameter_list();
        self.generic_types.extend(generic_types);
        let mut f = Function::new(name_tok.span.clone(), &name_tok.lexeme);

        if !self.match_kind(TokenKind::LParen) {
            self.add_error("P080", "expected '(' after method name", self.peek().span.clone());
            self.synchronize();
            return None;
        }
        f.parameters = self.parse_parameters();
        if !self.match_kind(TokenKind::RParen) {
            self.add_error("P006", "expected ')' after parameters", self.peek().span.clone());
        }
        if self.match_kind(TokenKind::Arrow) {
            f.return_types = self.parse_return_types();
        }

        let start_pos = self.src.pos_from_offset(start.start);
        let end_pos = self.src.pos_from_offset(self.previous().span.end);
        f.span = Span::between(&start_pos, &end_pos);
        self.generic_types = saved_generic_types;
        Some(f)
    }

    // ---- functions ----

    fn parse_function(&mut self) -> Option<Function> {
        let start = self.previous().span.clone();

        if !self.check(TokenKind::Identifier) {
            self.add_error("P004", "expected function name", self.peek().span.clone());
            return None;
        }
        let name_tok = self.advance();
        let mut f = Function::new(name_tok.span.clone(), &name_tok.lexeme);

        if !self.match_kind(TokenKind::LParen) {
            self.add_error("P005", "expected '(' after function name", self.peek().span.clone());
            self.synchronize();
            return None;
        }
        f.parameters = self.parse_parameters();
        if !self.match_kind(TokenKind::RParen) {
            self.add_error("P006", "expected ')' after parameters", self.peek().span.clone());
        }

        if self.match_kind(TokenKind::Arrow) {
            f.return_types = self.parse_return_types();
        }

        while self.match_kind(TokenKind::Newline) {}
        if !self.match_kind(TokenKind::LBrace) {
            self.add_error("P007", "expected '{' for function body", self.peek().span.clone());
            self.synchronize();
            return Some(f);
        }

        f.body = Some(self.parse_block());
        // Body defaults to an empty block if missing.
        if f.body.is_none() {
            f.body = Some(Block {
                span: self.previous().span.clone(),
                statements: Vec::new(),
            });
        }

        let start_pos = self.src.pos_from_offset(start.start);
        let end_pos = self.src.pos_from_offset(self.previous().span.end);
        f.span = Span::between(&start_pos, &end_pos);
        Some(f)
    }

    fn parse_parameters(&mut self) -> Vec<Parameter> {
        let mut params = Vec::new();
        while !self.check(TokenKind::RParen) && !self.is_at_end() {
            if !params.is_empty() {
                if !self.match_kind(TokenKind::Comma) {
                    break;
                }
                if self.check(TokenKind::RParen) {
                    break;
                }
            }

            if !self.check(TokenKind::Identifier) {
                self.add_error("P008", "expected parameter name", self.peek().span.clone());
                break;
            }
            let name_tok = self.advance();
            if !self.match_kind(TokenKind::Colon) {
                self.add_error("P009", "expected ':' after parameter name", self.peek().span.clone());
                break;
            }

            let variadic = self.match_kind(TokenKind::Ellipsis);
            let type_ann = match self.parse_type_annotation() {
                Some(t) => t,
                None => {
                    self.add_error("P010", "expected parameter type", self.peek().span.clone());
                    break;
                }
            };

            if variadic && type_ann.nullable {
                self.add_error("P117", "variadic parameter cannot be nullable", name_tok.span.clone());
            }

            params.push(Parameter {
                span: name_tok.span.clone(),
                name: name_tok.lexeme,
                ty: type_ann,
                variadic,
            });

            if variadic {
                self.match_kind(TokenKind::Comma);
                break;
            }
        }
        params
    }

    // ---- type annotations ----

    fn parse_type_annotation(&mut self) -> Option<TypeAnnotation> {
        let top_level = self.type_depth == 0;
        self.type_depth += 1;
        let result = self.parse_type_annotation_inner();
        self.type_depth -= 1;

        let mut t = match result {
            Some(t) => t,
            None => {
                if self.check(TokenKind::Question) {
                    self.add_error("P017", "expected type before '?'", self.peek().span.clone());
                    self.advance();
                }
                return None;
            }
        };

        if self.pending_type_gts == 0 && self.match_kind(TokenKind::Question) {
            t.nullable = true;
        }

        if top_level && self.pending_type_gts > 0 {
            self.add_error("P091", "unexpected extra '>' in type", self.previous().span.clone());
            self.pending_type_gts = 0;
        }
        Some(t)
    }

    fn parse_type_annotation_inner(&mut self) -> Option<TypeAnnotation> {
        let prev_span = self.previous().span.clone();
        if self.match_kind(TokenKind::Bool) {
            return Some(TypeAnnotation::prim(prev_span, Kind::Bool));
        }
        if self.match_kind(TokenKind::Byte) {
            return Some(TypeAnnotation::prim(prev_span, Kind::Byte));
        }
        if self.match_kind(TokenKind::Int) {
            return Some(TypeAnnotation::prim(prev_span, Kind::Int));
        }
        // Removed type names 'long' and 'double'
        if self.check(TokenKind::Identifier)
            && (self.peek().lexeme == "long" || self.peek().lexeme == "double")
        {
            let tok = self.advance();
            if tok.lexeme == "long" {
                self.add_error(
                    "P071",
                    "unknown type 'long'; use 'int', which is a signed 64-bit integer",
                    tok.span.clone(),
                );
            } else {
                self.add_error(
                    "P119",
                    "unknown type 'double'; use 'float', which is a 64-bit floating-point type",
                    tok.span.clone(),
                );
            }
            return Some(TypeAnnotation::prim(tok.span, Kind::Invalid));
        }
        if self.match_kind(TokenKind::Float) {
            return Some(TypeAnnotation::prim(prev_span, Kind::Float));
        }
        if self.match_kind(TokenKind::Char) {
            return Some(TypeAnnotation::prim(prev_span, Kind::Char));
        }
        if self.match_kind(TokenKind::String) {
            return Some(TypeAnnotation::prim(prev_span, Kind::String));
        }
        if self.match_kind(TokenKind::Exception) {
            return Some(TypeAnnotation::prim(prev_span, Kind::Exception));
        }
        if self.match_kind(TokenKind::Void) {
            return Some(TypeAnnotation::prim(prev_span, Kind::Void));
        }
        if self.match_kind(TokenKind::Any) {
            return Some(TypeAnnotation::prim(prev_span, Kind::Any));
        }
        if self.match_kind(TokenKind::List) {
            if self.match_kind(TokenKind::Lt) {
                let elem = match self.parse_type_annotation() {
                    Some(e) => e,
                    None => {
                        self.add_error("P011", "expected element type in List<T>", self.peek().span.clone());
                        return None;
                    }
                };
                if !self.match_type_gt() {
                    self.add_error("P012", "expected '>' after List element type", self.peek().span.clone());
                }
                let mut t = TypeAnnotation::prim(self.previous().span.clone(), Kind::List);
                t.element = Some(Box::new(elem));
                return Some(t);
            }
            return Some(TypeAnnotation::prim(prev_span, Kind::List));
        }
        if self.match_kind(TokenKind::Stack) {
            if self.match_kind(TokenKind::Lt) {
                let elem = match self.parse_type_annotation() {
                    Some(e) => e,
                    None => {
                        self.add_error("P089", "expected element type in Stack<T>", self.peek().span.clone());
                        return None;
                    }
                };
                if !self.match_type_gt() {
                    self.add_error("P090", "expected '>' after Stack element type", self.peek().span.clone());
                }
                let mut t = TypeAnnotation::prim(self.previous().span.clone(), Kind::Stack);
                t.element = Some(Box::new(elem));
                return Some(t);
            }
            return Some(TypeAnnotation::prim(prev_span, Kind::Stack));
        }
        // User-defined type names (checked after 'list' but before 'map',
        // exactly like the Go source).
        if self.match_kind(TokenKind::Identifier) {
            let tok_span = self.previous().span.clone();
            let tok_name = self.previous().lexeme.clone();
            if self.generic_types.contains(&tok_name) {
                return Some(TypeAnnotation::prim(tok_span, Kind::Any));
            }
            if self.match_kind(TokenKind::Lt) {
                let mut depth = 1usize;
                while depth > 0 && !self.is_at_end() {
                    if self.match_kind(TokenKind::Lt) {
                        depth += 1;
                    } else if self.match_type_gt() {
                        depth -= 1;
                    } else {
                        self.advance();
                    }
                }
            }
            return Some(TypeAnnotation::named(tok_span, &tok_name));
        }
        if self.match_kind(TokenKind::Map) {
            if self.match_kind(TokenKind::Lt) {
                let key_t = match self.parse_type_annotation() {
                    Some(k) => k,
                    None => {
                        self.add_error("P013", "expected key type in Map<K,V>", self.peek().span.clone());
                        return None;
                    }
                };
                if !self.match_kind(TokenKind::Comma) {
                    self.add_error("P014", "expected ',' between key and value types", self.peek().span.clone());
                    return None;
                }
                let val_t = match self.parse_type_annotation() {
                    Some(v) => v,
                    None => {
                        self.add_error("P015", "expected value type in Map<K,V>", self.peek().span.clone());
                        return None;
                    }
                };
                if !self.match_type_gt() {
                    self.add_error("P016", "expected '>' after Map types", self.peek().span.clone());
                }
                let mut t = TypeAnnotation::prim(self.previous().span.clone(), Kind::Map);
                t.key_type = Some(Box::new(key_t));
                t.value_type = Some(Box::new(val_t));
                return Some(t);
            }
            return Some(TypeAnnotation::prim(prev_span, Kind::Map));
        }

        None
    }

    fn parse_return_types(&mut self) -> Vec<TypeAnnotation> {
        while self.match_kind(TokenKind::Newline) {}

        if self.check(TokenKind::Void) {
            self.add_error(
                CODE_PARSER_BARE_RETURN_ARROW,
                "void functions must omit the return arrow; use 'func name(...)'",
                self.peek().span.clone(),
            );
            self.advance();
            return Vec::new();
        }

        if self.check(TokenKind::LBrace) || self.check_newline_or_semicolon() || self.is_at_end() {
            self.add_error(
                CODE_PARSER_BARE_RETURN_ARROW,
                "expected a return type after '->'; omit the arrow for void functions",
                self.peek().span.clone(),
            );
            return Vec::new();
        }

        let first = match self.parse_type_annotation() {
            Some(t) => t,
            None => return Vec::new(),
        };
        let types = vec![first];

        if self.match_kind(TokenKind::Comma) {
            self.add_error(
                "P070",
                "multiple return types are not supported; use a struct type instead",
                self.previous().span.clone(),
            );
            return types;
        }
        types
    }

    // ---- blocks and statements ----

    fn parse_block(&mut self) -> Block {
        let start_span = self.previous().span.clone();
        let mut block = Block {
            span: Span::default(),
            statements: Vec::new(),
        };

        while !self.check(TokenKind::RBrace) && !self.is_at_end() {
            let before_pos = self.pos;
            let stmt = self.parse_statement();
            if let Some(s) = stmt {
                block.statements.push(s);
            }
            // Prevent infinite loops on failed statements
            if self.pos == before_pos && !self.is_at_end() {
                if self.check(TokenKind::Newline) || self.check(TokenKind::Semicolon) {
                    self.advance();
                } else if !self.check(TokenKind::RBrace) {
                    self.advance();
                }
            }
            if !self.check(TokenKind::RBrace) && !self.is_at_end() {
                self.expect_newline_or_semicolon();
            }
        }

        if !self.match_kind(TokenKind::RBrace) {
            self.add_error("P018", "expected '}' to close block", self.peek().span.clone());
        } else {
            let end_span = self.previous().span.clone();
            let sp = self.src.pos_from_offset(start_span.start);
            let ep = self.src.pos_from_offset(end_span.end);
            block.span = Span::between(&sp, &ep);
        }
        block
    }

    fn parse_statement(&mut self) -> Option<StmtNode> {
        if self.err_count >= self.max_errors {
            return None;
        }

        while self.check(TokenKind::Newline) || self.check(TokenKind::Semicolon) {
            self.advance();
        }

        let is_mut = self.match_kind(TokenKind::Mut);

        // Declaration: identifier ':' type '=' ...
        if self.check(TokenKind::Identifier) {
            let save = self.pos;
            self.advance();
            let is_decl = self.check(TokenKind::Colon);
            self.pos = save;
            if is_decl {
                if let Some(mut stmt) = self.parse_var_decl() {
                    if let StmtKind::VarDecl(ref mut vd) = stmt.kind {
                        vd.is_mut = is_mut;
                    }
                    return Some(stmt);
                }
                self.skip_to_statement_boundary();
                return None;
            }
        }

        if is_mut {
            self.add_error("P068", "mut requires a variable declaration", self.peek().span.clone());
            return None;
        }

        if self.match_kind(TokenKind::Func) {
            self.add_error("P019", "nested functions not supported", self.peek().span.clone());
            return None;
        }

        if self.match_kind(TokenKind::If) {
            return Some(self.parse_if_stmt());
        }
        if self.match_kind(TokenKind::While) {
            return Some(self.parse_while_stmt());
        }
        if self.match_kind(TokenKind::For) {
            return self.parse_for_stmt();
        }
        if self.match_kind(TokenKind::Try) {
            return Some(self.parse_try_stmt());
        }
        if self.match_kind(TokenKind::Throw) {
            return Some(self.parse_throw_stmt());
        }
        if self.match_kind(TokenKind::Switch) {
            return Some(self.parse_switch_stmt());
        }
        if self.match_kind(TokenKind::Break) {
            let span = self.previous().span.clone();
            return Some(StmtNode {
                span,
                kind: StmtKind::Break,
            });
        }
        if self.match_kind(TokenKind::Continue) {
            let span = self.previous().span.clone();
            return Some(StmtNode {
                span,
                kind: StmtKind::Continue,
            });
        }
        if self.match_kind(TokenKind::Return) {
            let span = self.previous().span.clone();
            let mut values = Vec::new();
            if !self.check_newline_or_semicolon() && !self.check(TokenKind::RBrace) {
                if let Some(first) = self.parse_expression() {
                    values.push(first);
                    if self.match_kind(TokenKind::Comma) {
                        self.add_error(
                            "P120",
                            "multiple return values are not supported; use a struct instead",
                            self.previous().span.clone(),
                        );
                        while !self.check_newline_or_semicolon()
                            && !self.check(TokenKind::RBrace)
                            && !self.is_at_end()
                        {
                            if self.parse_expression().is_some() {
                                if !self.match_kind(TokenKind::Comma) {
                                    break;
                                }
                            } else {
                                break;
                            }
                        }
                    }
                }
            }
            return Some(StmtNode {
                span,
                kind: StmtKind::Return(values),
            });
        }

        if self.match_kind(TokenKind::LBrace) {
            let block = self.parse_block();
            let span = block.span.clone();
            return Some(StmtNode {
                span,
                kind: StmtKind::Block(block),
            });
        }

        if self.check(TokenKind::Newline)
            || self.check(TokenKind::Semicolon)
            || self.check(TokenKind::RBrace)
            || self.is_at_end()
        {
            return None;
        }

        match self.parse_expr_stmt() {
            Some(stmt) => Some(stmt),
            None => {
                self.skip_to_statement_boundary();
                None
            }
        }
    }

    fn parse_var_decl(&mut self) -> Option<StmtNode> {
        let name_tok = self.advance();
        let span = name_tok.span.clone();
        if !self.match_kind(TokenKind::Colon) {
            self.add_error("P020", "expected ':' after variable name", self.peek().span.clone());
            return None;
        }
        let type_ann = match self.parse_type_annotation() {
            Some(t) => t,
            None => {
                self.add_error("P021", "expected type in variable declaration", self.peek().span.clone());
                return None;
            }
        };

        let mut init = None;
        if self.match_kind(TokenKind::Assign) {
            self.skip_expression_newlines();
            init = self.parse_expression();
        }

        Some(StmtNode {
            span,
            kind: StmtKind::VarDecl(VarDecl {
                name: name_tok.lexeme,
                ty: type_ann,
                init,
                is_mut: false,
            }),
        })
    }

    fn parse_switch_stmt(&mut self) -> StmtNode {
        let start_span = self.previous().span.clone();

        let switch_expr = self.parse_header_expression();

        self.skip_expression_newlines();
        if !self.match_kind(TokenKind::LBrace) {
            self.add_error("P116", "expected '{' after switch expression", self.peek().span.clone());
            let span = start_span.clone();
            return StmtNode {
                span,
                kind: StmtKind::Switch(SwitchStmt {
                    expression: switch_expr,
                    cases: Vec::new(),
                    default_block: None,
                }),
            };
        }

        let mut stmt = SwitchStmt {
            expression: switch_expr,
            cases: Vec::new(),
            default_block: None,
        };
        let mut has_default = false;

        while !self.check(TokenKind::RBrace) && !self.is_at_end() {
            while self.match_kind(TokenKind::Newline) {}
            if self.check(TokenKind::RBrace) || self.is_at_end() {
                break;
            }

            if self.match_kind(TokenKind::Case) {
                let case_span = self.previous().span.clone();
                self.skip_expression_newlines();
                let case_expr = self.parse_expression();

                if self.match_kind(TokenKind::Colon) {
                    self.add_error(
                        "P051",
                        "case labels do not use ':'; expected '{' after case expression",
                        self.previous().span.clone(),
                    );
                }
                let body = self.parse_case_body();
                if let Some(expr) = case_expr {
                    stmt.cases.push(SwitchCase {
                        span: case_span,
                        expression: expr,
                        body,
                    });
                }
            } else if self.match_kind(TokenKind::Default) {
                if has_default {
                    self.add_error("P052", "duplicate default clause in switch", self.peek().span.clone());
                    if !self.check(TokenKind::RBrace) && !self.is_at_end() {
                        self.advance();
                    }
                    continue;
                }
                has_default = true;
                if self.match_kind(TokenKind::Colon) {
                    self.add_error("P053", "default labels do not use ':'; expected '{'", self.previous().span.clone());
                }
                stmt.default_block = Some(self.parse_case_body());
            } else {
                self.add_error("P054", "expected 'case' or 'default' in switch", self.peek().span.clone());
                self.advance();
            }
        }

        let span;
        if !self.match_kind(TokenKind::RBrace) {
            self.add_error("P055", "expected '}' to close switch", self.peek().span.clone());
            span = start_span;
        } else {
            let end_span = self.previous().span.clone();
            let sp = self.src.pos_from_offset(start_span.start);
            let ep = self.src.pos_from_offset(end_span.end);
            span = Span::between(&sp, &ep);
        }

        StmtNode {
            span,
            kind: StmtKind::Switch(stmt),
        }
    }

    fn parse_case_body(&mut self) -> Block {
        self.skip_expression_newlines();
        if !self.match_kind(TokenKind::LBrace) {
            self.add_error("P069", "expected '{' for case body", self.peek().span.clone());
            while !self.is_at_end() {
                if self.check(TokenKind::Case) || self.check(TokenKind::Default) || self.check(TokenKind::RBrace) {
                    break;
                }
                self.advance();
            }
            return Block {
                span: self.peek().span.clone(),
                statements: Vec::new(),
            };
        }
        self.parse_block()
    }

    fn parse_try_stmt(&mut self) -> StmtNode {
        let start_span = self.previous().span.clone();

        self.skip_expression_newlines();
        if !self.match_kind(TokenKind::LBrace) {
            self.add_error("P056", "expected '{' for try body", self.peek().span.clone());
            self.skip_to_body_end();
            return StmtNode {
                span: start_span,
                kind: StmtKind::Try(TryStmt {
                    try_body: Block {
                        span: Span::default(),
                        statements: Vec::new(),
                    },
                    catch: None,
                    finally_block: None,
                }),
            };
        }
        let try_block = self.parse_block();

        let mut stmt = TryStmt {
            try_body: try_block,
            catch: None,
            finally_block: None,
        };
        let mut has_catch = false;
        let mut has_finally = false;

        // Optional catch
        let clause_start = self.pos;
        self.skip_expression_newlines();
        if self.match_kind(TokenKind::Catch) {
            has_catch = true;
            stmt.catch = self.parse_catch_clause();
            // A newline before finally is continuation only when finally follows.
            let finally_start = self.pos;
            self.skip_expression_newlines();
            if !self.check(TokenKind::Finally) {
                self.pos = finally_start;
            }
        } else {
            self.pos = clause_start;
        }

        // Optional finally
        let finally_start = self.pos;
        self.skip_expression_newlines();
        if self.match_kind(TokenKind::Finally) {
            has_finally = true;
            stmt.finally_block = Some(self.parse_finally_block());
        } else {
            self.pos = finally_start;
        }

        if !has_catch && !has_finally {
            self.add_error("P057", "try statement requires catch or finally", self.peek().span.clone());
        }

        if has_finally {
            let catch_start = self.pos;
            self.skip_expression_newlines();
            if self.check(TokenKind::Catch) {
                self.add_error("P058", "catch must appear before finally", self.peek().span.clone());
            } else {
                self.pos = catch_start;
            }
        }

        let end_span = self.previous().span.clone();
        let sp = self.src.pos_from_offset(start_span.start);
        let ep = self.src.pos_from_offset(end_span.end);
        StmtNode {
            span: Span::between(&sp, &ep),
            kind: StmtKind::Try(stmt),
        }
    }

    fn parse_catch_clause(&mut self) -> Option<CatchClause> {
        let start_span = self.previous().span.clone();
        if !self.match_kind(TokenKind::LParen) {
            self.add_error("P059", "expected '(' after catch", self.peek().span.clone());
            return None;
        }
        if !self.check(TokenKind::Identifier) {
            self.add_error("P060", "expected parameter name in catch", self.peek().span.clone());
            self.match_kind(TokenKind::RParen);
            return None;
        }
        let param_tok = self.advance();
        if !self.match_kind(TokenKind::Colon) {
            self.add_error("P061", "expected ':' after catch parameter name", self.peek().span.clone());
            return None;
        }
        let type_ann = match self.parse_type_annotation() {
            Some(t) => t,
            None => {
                self.add_error("P063", "expected type in catch parameter", self.peek().span.clone());
                return None;
            }
        };
        if !self.match_kind(TokenKind::RParen) {
            self.add_error("P064", "expected ')' after catch parameter", self.peek().span.clone());
        }

        self.skip_expression_newlines();
        if !self.match_kind(TokenKind::LBrace) {
            self.add_error("P065", "expected '{' for catch body", self.peek().span.clone());
            self.skip_to_body_end();
            return Some(CatchClause {
                span: start_span,
                param_name: param_tok.lexeme,
                param_type: type_ann,
                body: Block {
                    span: self.peek().span.clone(),
                    statements: Vec::new(),
                },
            });
        }
        let body = self.parse_block();
        Some(CatchClause {
            span: start_span,
            param_name: param_tok.lexeme,
            param_type: type_ann,
            body,
        })
    }

    fn parse_finally_block(&mut self) -> Block {
        self.skip_expression_newlines();
        if !self.match_kind(TokenKind::LBrace) {
            self.add_error("P066", "expected '{' for finally body", self.peek().span.clone());
            self.skip_to_body_end();
            return Block {
                span: self.peek().span.clone(),
                statements: Vec::new(),
            };
        }
        self.parse_block()
    }

    fn parse_throw_stmt(&mut self) -> StmtNode {
        let start_span = self.previous().span.clone();
        self.skip_expression_newlines();
        let expr = self.parse_expression();
        match expr {
            Some(e) => {
                let sp = self.src.pos_from_offset(start_span.start);
                let ep = self.src.pos_from_offset(e.span.end);
                let span = Span::between(&sp, &ep);
                StmtNode {
                    span,
                    kind: StmtKind::Throw(e),
                }
            }
            None => {
                self.add_error("P067", "expected expression after throw", self.peek().span.clone());
                StmtNode {
                    span: start_span,
                    kind: StmtKind::Throw(ExprNode::new(Span::default(), ExprKind::Null)),
                }
            }
        }
    }

    fn parse_if_stmt(&mut self) -> StmtNode {
        let start_span = self.previous().span.clone();
        let condition = self.parse_header_expression();

        self.skip_expression_newlines();
        if !self.match_kind(TokenKind::LBrace) {
            self.add_error("P024", "expected '{' for if body", self.peek().span.clone());
            self.skip_to_body_end();
            return StmtNode {
                span: start_span,
                kind: StmtKind::If(IfStmt {
                    condition,
                    then_block: Block {
                        span: Span::default(),
                        statements: Vec::new(),
                    },
                    else_ifs: Vec::new(),
                    else_block: None,
                }),
            };
        }
        let then_block = self.parse_block();

        let mut stmt = IfStmt {
            condition,
            then_block,
            else_ifs: Vec::new(),
            else_block: None,
        };

        // Only consume separators when an else clause follows.
        let else_start = self.pos;
        while self.match_kind(TokenKind::Newline) || self.match_kind(TokenKind::Semicolon) {}
        if !self.check(TokenKind::Else) {
            self.pos = else_start;
        }

        if self.match_kind(TokenKind::Else) {
            self.skip_expression_newlines();
            if self.match_kind(TokenKind::If) {
                let else_if = self.parse_if_stmt();
                if let StmtKind::If(inner) = else_if.kind {
                    stmt.else_ifs.push(inner);
                }
            } else if self.match_kind(TokenKind::LBrace) {
                stmt.else_block = Some(self.parse_block());
            } else {
                self.add_error("P025", "expected 'if' or '{' after 'else'", self.peek().span.clone());
                self.skip_to_body_end();
            }
        }

        let span = start_span;
        StmtNode {
            span,
            kind: StmtKind::If(stmt),
        }
    }

    fn parse_while_stmt(&mut self) -> StmtNode {
        let start_span = self.previous().span.clone();
        let condition = self.parse_header_expression();

        self.skip_expression_newlines();
        if !self.match_kind(TokenKind::LBrace) {
            self.add_error("P028", "expected '{' for while body", self.peek().span.clone());
            self.skip_to_body_end();
            return StmtNode {
                span: start_span,
                kind: StmtKind::While(WhileStmt {
                    condition,
                    body: Block {
                        span: Span::default(),
                        statements: Vec::new(),
                    },
                }),
            };
        }
        let body = self.parse_block();
        StmtNode {
            span: start_span,
            kind: StmtKind::While(WhileStmt { condition, body }),
        }
    }

    fn parse_for_stmt(&mut self) -> Option<StmtNode> {
        let start_span = self.previous().span.clone();

        let variable;
        let mut value_variable = String::new();

        if self.match_kind(TokenKind::LParen) {
            // Legacy parenthesized map iteration form
            if !self.check(TokenKind::Identifier) {
                self.add_error("P092", "expected key variable name in for", self.peek().span.clone());
                return None;
            }
            variable = self.advance().lexeme;
            if !self.match_kind(TokenKind::Comma) {
                self.add_error("P046", "expected ',' after key variable in for", self.peek().span.clone());
                return None;
            }
            if !self.check(TokenKind::Identifier) {
                self.add_error("P047", "expected value variable name in for", self.peek().span.clone());
                return None;
            }
            value_variable = self.advance().lexeme;
            if !self.match_kind(TokenKind::RParen) {
                self.add_error("P114", "expected ')' after for variables", self.peek().span.clone());
                return None;
            }
            self.add_error(
                "P075",
                "map iteration bindings do not use parentheses; use 'for key, value in map'",
                start_span.clone(),
            );
        } else {
            if !self.check(TokenKind::Identifier) {
                self.add_error("P030", "expected loop variable name", self.peek().span.clone());
                return None;
            }
            variable = self.advance().lexeme;
            if self.match_kind(TokenKind::Comma) {
                if !self.check(TokenKind::Identifier) {
                    self.add_error("P047", "expected value variable name in for", self.peek().span.clone());
                    return None;
                }
                value_variable = self.advance().lexeme;
            }
        }

        if !self.match_kind(TokenKind::In) {
            self.add_error("P031", "expected 'in' after loop variable", self.peek().span.clone());
        }

        let iterable = self.parse_header_expression();

        self.skip_expression_newlines();
        let body = if !self.match_kind(TokenKind::LBrace) {
            self.add_error("P033", "expected '{' for for body", self.peek().span.clone());
            self.skip_to_body_end();
            Block {
                span: start_span.clone(),
                statements: Vec::new(),
            }
        } else {
            self.parse_block()
        };

        Some(StmtNode {
            span: start_span,
            kind: StmtKind::For(ForStmt {
                variable,
                value_variable,
                iterable,
                body,
            }),
        })
    }

    fn try_parse_ident_list(&mut self) -> Option<Vec<String>> {
        let save = self.pos;
        let mut ids = Vec::new();
        loop {
            if !self.check(TokenKind::Identifier) {
                self.pos = save;
                return None;
            }
            ids.push(self.peek().lexeme.clone());
            self.advance();
            if !self.match_kind(TokenKind::Comma) {
                break;
            }
            if self.check(TokenKind::Assign) || self.check_newline_or_semicolon() || self.check(TokenKind::RBrace) {
                break;
            }
        }
        if ids.len() < 2 || !self.check(TokenKind::Assign) {
            self.pos = save;
            return None;
        }
        Some(ids)
    }

    fn parse_expr_stmt(&mut self) -> Option<StmtNode> {
        // Reject multi-target assignment: a, b = expr
        if let Some(ids) = self.try_parse_ident_list() {
            let last_ident = ids.last().unwrap().clone();
            let span = self.peek().span.clone();
            self.pos += 0; // no-op to keep structure clear
            // find span of the last identifier: we advanced past them already
            let last_span = self.tokens[self.pos.checked_sub(1).unwrap_or(0)].span.clone();
            let _ = span;
            self.add_error(
                "P122",
                "multi-target assignment is not supported; use a struct type instead",
                last_span,
            );
            let _ = last_ident;
            if self.match_kind(TokenKind::Assign) {
                self.skip_expression_newlines();
                self.parse_expression();
            }
            return None;
        }

        let expr = self.parse_expression()?;

        if self.match_kind(TokenKind::Assign) {
            let is_ident = matches!(expr.kind, ExprKind::Ident(_));
            let is_index = matches!(expr.kind, ExprKind::Index { .. });
            if !is_ident && !is_index {
                self.add_error(
                    "P034",
                    "cannot assign to non-identifier or non-index expression",
                    self.peek().span.clone(),
                );
                self.parse_expression();
                return None;
            }
            let assign_span = {
                let sp = self.src.pos_from_offset(expr.span.start);
                let ep = self.src.pos_from_offset(self.peek().span.end);
                Span::between(&sp, &ep)
            };
            self.skip_expression_newlines();
            let value = self.parse_expression();
            if let Some(value) = value {
                let span = assign_span.clone();
                return Some(StmtNode {
                    span,
                    kind: StmtKind::Expr(ExprNode::new(
                        assign_span,
                        ExprKind::Binary {
                            op: BinOp::Assign,
                            left: Box::new(expr),
                            right: Box::new(value),
                        },
                    )),
                });
            }
            // Missing value: Go would still construct the node; keep the expr.
            let span = expr.span.clone();
            return Some(StmtNode {
                span,
                kind: StmtKind::Expr(expr),
            });
        }

        let span = expr.span.clone();
        Some(StmtNode {
            span,
            kind: StmtKind::Expr(expr),
        })
    }

    // ---- Expressions (Pratt parsing) ----

    fn parse_expression(&mut self) -> Option<ExprNode> {
        self.parse_precedence(PREC_LOWEST)
    }

    fn parse_header_expression(&mut self) -> ExprNode {
        let allow = self.allow_struct_literal;
        self.allow_struct_literal = false;
        self.skip_expression_newlines();
        let expr = self.parse_expression();
        self.allow_struct_literal = allow;
        expr.unwrap_or_else(|| ExprNode::new(Span::default(), ExprKind::Null))
    }

    fn parse_precedence(&mut self, min_prec: u8) -> Option<ExprNode> {
        let mut prefix = self.parse_prefix()?;

        loop {
            let kind = self.peek().kind;
            let prec = precedence(kind);
            if prec <= min_prec {
                return Some(prefix);
            }
            match self.parse_infix(prefix, kind) {
                InfixResult::Node(infix) => prefix = infix,
                InfixResult::Back(left) => return Some(left),
            }
        }
    }

    fn parse_continuation_precedence(&mut self, min_prec: u8) -> Option<ExprNode> {
        self.skip_expression_newlines();
        self.parse_precedence(min_prec)
    }

    fn parse_prefix(&mut self) -> Option<ExprNode> {
        if self.check(TokenKind::Concat) {
            self.add_error(
                "P118",
                "'..' is a binary string-concatenation operator and cannot be used as a prefix",
                self.peek().span.clone(),
            );
            self.advance();
            return None;
        }

        // Unary operators
        let unary_op = if self.match_kind(TokenKind::Minus) {
            Some(UnaryOp::Negate)
        } else if self.match_kind(TokenKind::Not) {
            Some(UnaryOp::Not)
        } else if self.match_kind(TokenKind::BitNot) {
            Some(UnaryOp::BitNot)
        } else {
            None
        };
        if let Some(op) = unary_op {
            let op_span = self.previous().span.clone();
            let operand = self.parse_continuation_precedence(PREC_UNARY)?;
            let sp = self.src.pos_from_offset(op_span.start);
            let ep = self.src.pos_from_offset(operand.span.end);
            let span = Span::between(&sp, &ep);
            return Some(ExprNode::new(
                span,
                ExprKind::Unary {
                    op,
                    operand: Box::new(operand),
                },
            ));
        }

        // Primary expressions
        if self.check(TokenKind::IntLiteral) {
            let tok = self.advance();
            match go_parse_int_base0(&tok.lexeme) {
                Some(v) => {
                    return Some(ExprNode::new(tok.span, ExprKind::Int(v)));
                }
                None => {
                    self.diags.add_error(
                        "P035",
                        &format!("invalid integer literal: parsing \"{}\": invalid syntax", tok.lexeme),
                        tok.span.clone(),
                    );
                    return None;
                }
            }
        }
        if self.check(TokenKind::FloatLiteral) {
            let tok = self.advance();
            match crate::gocompat::go_parse_float(&tok.lexeme) {
                Some(v) => {
                    return Some(ExprNode::new(tok.span, ExprKind::Float(v)));
                }
                None => {
                    self.diags.add_error(
                        "P037",
                        &format!("invalid float literal: parsing \"{}\": invalid syntax", tok.lexeme),
                        tok.span.clone(),
                    );
                    return None;
                }
            }
        }
        if self.check(TokenKind::BoolLiteral) {
            let tok = self.advance();
            let val = tok.lexeme == "true";
            return Some(ExprNode::new(tok.span, ExprKind::Bool(val)));
        }
        if self.check(TokenKind::CharLiteral) {
            let tok = self.advance();
            let val = tok.lexeme.chars().next().map(|c| c as u32).unwrap_or(0);
            return Some(ExprNode::new(tok.span, ExprKind::Char(val)));
        }
        if self.check(TokenKind::StringLiteral) {
            let tok = self.advance();
            return Some(ExprNode::new(tok.span, ExprKind::Str(tok.lexeme)));
        }
        if self.match_kind(TokenKind::Null) {
            let span = self.previous().span.clone();
            return Some(ExprNode::new(span, ExprKind::Null));
        }

        // Identifier (including type keywords used as function names)
        if self.check(TokenKind::Identifier) {
            let tok = self.advance();
            if self.allow_struct_literal && self.match_kind(TokenKind::LBrace) {
                return Some(self.parse_struct_literal(&tok.lexeme, tok.span));
            }
            return Some(ExprNode::new(tok.span, ExprKind::Ident(tok.lexeme)));
        }
        if matches!(
            self.peek().kind,
            TokenKind::String
                | TokenKind::Int
                | TokenKind::Float
                | TokenKind::Bool
                | TokenKind::Byte
                | TokenKind::Char
                | TokenKind::Void
                | TokenKind::List
                | TokenKind::Map
                | TokenKind::Stack
                | TokenKind::Exception
        ) {
            let tok = self.advance();
            return Some(ExprNode::new(tok.span, ExprKind::Ident(tok.lexeme)));
        }

        // Grouping
        if self.match_kind(TokenKind::LParen) {
            self.skip_expression_newlines();
            let expr = self.parse_expression()?;
            self.skip_expression_newlines();
            if !self.match_kind(TokenKind::RParen) {
                self.add_error("P039", "expected ')' after expression", self.peek().span.clone());
            }
            return Some(expr);
        }

        // List literal
        if self.match_kind(TokenKind::LBracket) {
            return Some(self.parse_list_literal());
        }

        // Map literal
        if self.match_kind(TokenKind::LBrace) {
            return Some(self.parse_map_literal());
        }

        None
    }

    fn parse_infix(&mut self, left: ExprNode, kind: TokenKind) -> InfixResult {
        // Function call
        if kind == TokenKind::LParen {
            self.advance();
            return InfixResult::Node(self.parse_call_expr(left));
        }

        // Member access
        if kind == TokenKind::Dot {
            self.advance();
            self.skip_expression_newlines();

            let member_ok = matches!(
                self.peek().kind,
                TokenKind::Identifier
                    | TokenKind::Bool
                    | TokenKind::Byte
                    | TokenKind::Int
                    | TokenKind::Float
                    | TokenKind::Char
                    | TokenKind::String
                    | TokenKind::Void
                    | TokenKind::List
                    | TokenKind::Map
                    | TokenKind::Stack
            );
            if !member_ok {
                self.add_error("P115", "expected member name after '.'", self.peek().span.clone());
                return InfixResult::Node(left);
            }
            let member_tok = self.advance();
            let member_name = match member_tok.kind {
                TokenKind::Bool => "bool".to_string(),
                TokenKind::Byte => "byte".to_string(),
                TokenKind::Int => "int".to_string(),
                TokenKind::Float => "float".to_string(),
                TokenKind::Char => "char".to_string(),
                TokenKind::String => "string".to_string(),
                TokenKind::Exception => "exception".to_string(),
                TokenKind::Void => "void".to_string(),
                TokenKind::List => "List".to_string(),
                TokenKind::Map => "Map".to_string(),
                TokenKind::Stack => "stack".to_string(),
                _ => member_tok.lexeme.clone(),
            };

            let sp = self.src.pos_from_offset(left.span.start);
            let ep = self.src.pos_from_offset(member_tok.span.end);
            let span = Span::between(&sp, &ep);
            return InfixResult::Node(ExprNode::new(
                span,
                ExprKind::Member {
                    object: Box::new(left),
                    member: member_name,
                },
            ));
        }

        // Indexing
        if kind == TokenKind::LBracket {
            self.advance();
            return InfixResult::Node(self.parse_index_expr(left));
        }

        // Null coalescing (right-associative)
        if kind == TokenKind::NullCoalesce {
            self.advance();
            let right = match self.parse_continuation_precedence(PREC_COALESCING - 1) {
                Some(r) => r,
                None => return InfixResult::Back(left),
            };
            let sp = self.src.pos_from_offset(left.span.start);
            let ep = self.src.pos_from_offset(right.span.end);
            let span = Span::between(&sp, &ep);
            return InfixResult::Node(ExprNode::new(
                span,
                ExprKind::NullCoalescing {
                    left: Box::new(left),
                    right: Box::new(right),
                },
            ));
        }

        // Binary operators
        let op = match binary_op_from_token(kind) {
            Some(o) => o,
            None => return InfixResult::Back(left),
        };

        self.advance();
        let prec = precedence(kind);
        let mut right_prec = prec;
        if kind == TokenKind::Assign {
            right_prec = prec - 1;
        }

        let right = match self.parse_continuation_precedence(right_prec) {
            Some(r) => r,
            None => {
                if kind == TokenKind::Concat {
                    self.add_error(
                        "P121",
                        "'..' is a binary string-concatenation operator requiring a left and right operand, but the right operand is missing",
                        self.previous().span.clone(),
                    );
                }
                return InfixResult::Back(left);
            }
        };

        let sp = self.src.pos_from_offset(left.span.start);
        let ep = self.src.pos_from_offset(right.span.end);
        let span = Span::between(&sp, &ep);
        InfixResult::Node(ExprNode::new(
            span,
            ExprKind::Binary {
                op,
                left: Box::new(left),
                right: Box::new(right),
            },
        ))
    }

    fn parse_call_expr(&mut self, function: ExprNode) -> ExprNode {
        let start_span = function.span.clone();
        let mut call = ExprNode::new(
            start_span.clone(),
            ExprKind::Call {
                function: Box::new(function),
                args: Vec::new(),
            },
        );

        while !self.check(TokenKind::RParen) && !self.is_at_end() {
            while self.match_kind(TokenKind::Newline) {}
            if self.check(TokenKind::RParen) {
                break;
            }
            let has_args = if let ExprKind::Call { args, .. } = &mut call.kind {
                !args.is_empty()
            } else {
                false
            };
            if has_args {
                if !self.match_kind(TokenKind::Comma) {
                    break;
                }
                while self.match_kind(TokenKind::Newline) {}
                if self.check(TokenKind::RParen) {
                    break;
                }
            }

            let arg = match self.parse_expression() {
                Some(a) => a,
                None => break,
            };
            let arg = if self.match_kind(TokenKind::Ellipsis) {
                let sp = self.src.pos_from_offset(arg.span.start);
                let ep = self.src.pos_from_offset(self.previous().span.end);
                let span = Span::between(&sp, &ep);
                ExprNode::new(span, ExprKind::Spread(Box::new(arg)))
            } else {
                arg
            };

            if let ExprKind::Call { args, .. } = &mut call.kind {
                args.push(arg);
            }
        }

        if !self.match_kind(TokenKind::RParen) {
            self.add_error("P040", "expected ')' after arguments", self.peek().span.clone());
        } else if let ExprKind::Call { args, .. } = &call.kind {
            let end_span = self.previous().span.clone();
            let sp = self.src.pos_from_offset(start_span.start);
            let ep = self.src.pos_from_offset(end_span.end);
            let _ = args;
            call.span = Span::between(&sp, &ep);
        }
        call
    }

    fn parse_index_expr(&mut self, target: ExprNode) -> ExprNode {
        self.skip_expression_newlines();
        let index = self.parse_expression();
        self.skip_expression_newlines();
        let mut span = target.span.clone();
        if !self.match_kind(TokenKind::RBracket) {
            self.add_error("P041", "expected ']' after index", self.peek().span.clone());
        } else {
            let end_span = self.previous().span.clone();
            let sp = self.src.pos_from_offset(span.start);
            let ep = self.src.pos_from_offset(end_span.end);
            span = Span::between(&sp, &ep);
        }
        let index = index.unwrap_or_else(|| ExprNode::new(Span::default(), ExprKind::Null));
        ExprNode::new(
            span,
            ExprKind::Index {
                target: Box::new(target),
                index: Box::new(index),
            },
        )
    }

    fn parse_struct_literal(&mut self, type_name: &str, start_span: Span) -> ExprNode {
        let mut lit = ExprNode::new(
            start_span.clone(),
            ExprKind::StructLit {
                type_name: type_name.to_string(),
                fields: Vec::new(),
                values: Vec::new(),
            },
        );

        while !self.check(TokenKind::RBrace) && !self.is_at_end() {
            while self.match_kind(TokenKind::Newline) {}
            if self.check(TokenKind::RBrace) {
                break;
            }
            let has_entries = if let ExprKind::StructLit { fields, .. } = &lit.kind {
                !fields.is_empty()
            } else {
                false
            };
            if has_entries {
                if !self.match_kind(TokenKind::Comma) {
                    break;
                }
                while self.match_kind(TokenKind::Newline) {}
                if self.check(TokenKind::RBrace) {
                    break;
                }
            }

            if !self.check(TokenKind::Identifier) {
                self.add_error("P099", "expected field name in struct literal", self.peek().span.clone());
                break;
            }
            let field_name = self.advance().lexeme;
            if !self.match_kind(TokenKind::Colon) {
                self.add_error("P087", "expected ':' after field name", self.peek().span.clone());
                break;
            }
            self.skip_expression_newlines();
            let value = match self.parse_expression() {
                Some(v) => v,
                None => break,
            };
            if let ExprKind::StructLit { fields, values, .. } = &mut lit.kind {
                fields.push(field_name);
                values.push(value);
            }
            while self.match_kind(TokenKind::Newline) {}
        }

        while self.match_kind(TokenKind::Newline) {}
        if !self.match_kind(TokenKind::RBrace) {
            self.add_error("P094", "expected '}' to close struct literal", self.peek().span.clone());
        } else {
            let end_span = self.previous().span.clone();
            let sp = self.src.pos_from_offset(start_span.start);
            let ep = self.src.pos_from_offset(end_span.end);
            lit.span = Span::between(&sp, &ep);
        }
        lit
    }

    fn parse_list_literal(&mut self) -> ExprNode {
        let start_span = self.previous().span.clone();
        let mut list = ExprNode::new(start_span.clone(), ExprKind::List(Vec::new()));

        while !self.check(TokenKind::RBracket) && !self.is_at_end() {
            while self.match_kind(TokenKind::Newline) {}
            if self.check(TokenKind::RBracket) {
                break;
            }
            let has_elems = if let ExprKind::List(elems) = &list.kind {
                !elems.is_empty()
            } else {
                false
            };
            if has_elems {
                if !self.match_kind(TokenKind::Comma) {
                    break;
                }
                while self.match_kind(TokenKind::Newline) {}
                if self.check(TokenKind::RBracket) {
                    break;
                }
            }
            let elem = match self.parse_expression() {
                Some(e) => e,
                None => break,
            };
            if let ExprKind::List(elems) = &mut list.kind {
                elems.push(elem);
            }
            while self.match_kind(TokenKind::Newline) {}
        }

        while self.match_kind(TokenKind::Newline) {}
        if !self.match_kind(TokenKind::RBracket) {
            self.add_error("P042", "expected ']' to close list literal", self.peek().span.clone());
        } else {
            let end_span = self.previous().span.clone();
            let sp = self.src.pos_from_offset(start_span.start);
            let ep = self.src.pos_from_offset(end_span.end);
            list.span = Span::between(&sp, &ep);
        }
        list
    }

    fn parse_map_literal(&mut self) -> ExprNode {
        let start_span = self.previous().span.clone();
        let mut m = ExprNode::new(
            start_span.clone(),
            ExprKind::Map {
                keys: Vec::new(),
                values: Vec::new(),
            },
        );

        while !self.check(TokenKind::RBrace) && !self.is_at_end() {
            while self.match_kind(TokenKind::Newline) {}
            if self.check(TokenKind::RBrace) {
                break;
            }
            let has_entries = if let ExprKind::Map { keys, .. } = &m.kind {
                !keys.is_empty()
            } else {
                false
            };
            if has_entries {
                if !self.match_kind(TokenKind::Comma) {
                    break;
                }
                while self.match_kind(TokenKind::Newline) {}
                if self.check(TokenKind::RBrace) {
                    break;
                }
            }
            let key = match self.parse_expression() {
                Some(k) => k,
                None => break,
            };
            if !self.match_kind(TokenKind::Colon) {
                self.add_error("P043", "expected ':' after map key", self.peek().span.clone());
                break;
            }
            self.skip_expression_newlines();
            let value = match self.parse_expression() {
                Some(v) => v,
                None => break,
            };
            if let ExprKind::Map { keys, values, .. } = &mut m.kind {
                keys.push(key);
                values.push(value);
            }
            while self.match_kind(TokenKind::Newline) {}
        }

        while self.match_kind(TokenKind::Newline) {}
        if !self.match_kind(TokenKind::RBrace) {
            self.add_error("P044", "expected '}' to close map literal", self.peek().span.clone());
        } else {
            let end_span = self.previous().span.clone();
            let sp = self.src.pos_from_offset(start_span.start);
            let ep = self.src.pos_from_offset(end_span.end);
            m.span = Span::between(&sp, &ep);
        }
        m
    }

    // ---- helpers ----

    fn skip_to_body_end(&mut self) {
        while !self.is_at_end() {
            if self.check(TokenKind::Newline)
                || self.check(TokenKind::Semicolon)
                || self.check(TokenKind::RBrace)
                || self.check(TokenKind::Else)
            {
                return;
            }
            self.advance();
        }
    }

    fn add_error(&mut self, code: &str, message: &str, span: Span) {
        self.diags.add_error(code, message, span);
        self.err_count += 1;
    }

    fn is_at_end(&self) -> bool {
        self.pos >= self.tokens.len() || self.tokens[self.pos].kind == TokenKind::Eof
    }

    fn peek(&self) -> &Token {
        if self.pos >= self.tokens.len() {
            static EOF_TOKEN: std::sync::OnceLock<Token> = std::sync::OnceLock::new();
            return EOF_TOKEN.get_or_init(|| eof_token());
        }
        &self.tokens[self.pos]
    }

    fn previous(&self) -> &Token {
        if self.pos == 0 {
            static EOF_TOKEN: std::sync::OnceLock<Token> = std::sync::OnceLock::new();
            return EOF_TOKEN.get_or_init(|| eof_token());
        }
        &self.tokens[self.pos - 1]
    }

    fn advance(&mut self) -> Token {
        if self.pos >= self.tokens.len() {
            return Token {
                kind: TokenKind::Eof,
                lexeme: String::new(),
                span: Span::default(),
            };
        }
        let tok = self.tokens[self.pos].clone();
        self.pos += 1;
        tok
    }

    fn check(&self, kind: TokenKind) -> bool {
        !self.is_at_end() && self.tokens[self.pos].kind == kind
    }

    fn match_kind(&mut self, kind: TokenKind) -> bool {
        if self.check(kind) {
            self.pos += 1;
            true
        } else {
            false
        }
    }

    fn check_newline_or_semicolon(&self) -> bool {
        self.check(TokenKind::Newline) || self.check(TokenKind::Semicolon)
    }

    fn expect_newline_or_semicolon(&mut self) {
        if self.match_kind(TokenKind::Newline) || self.match_kind(TokenKind::Semicolon) {
            return;
        }
        if self.is_at_end() || self.check(TokenKind::RBrace) {
            return;
        }
        self.add_error(
            CODE_PARSER_STATEMENT_SEPARATOR,
            "expected newline or ';' between statements",
            self.peek().span.clone(),
        );
    }

    fn skip_expression_newlines(&mut self) {
        while self.match_kind(TokenKind::Newline) {}
    }

    fn synchronize(&mut self) {
        while !self.is_at_end() {
            if self.match_kind(TokenKind::Newline) || self.match_kind(TokenKind::Semicolon) {
                return;
            }
            match self.peek().kind {
                TokenKind::Func
                | TokenKind::Enum
                | TokenKind::Struct
                | TokenKind::Trait
                | TokenKind::If
                | TokenKind::While
                | TokenKind::For
                | TokenKind::Switch
                | TokenKind::Case
                | TokenKind::Default
                | TokenKind::Return
                | TokenKind::Break
                | TokenKind::Continue
                | TokenKind::Package
                | TokenKind::RBrace
                | TokenKind::Ellipsis => return,
                _ => {}
            }
            self.advance();
        }
    }

    fn skip_to_statement_boundary(&mut self) {
        while !self.is_at_end() {
            if self.match_kind(TokenKind::Newline) || self.match_kind(TokenKind::Semicolon) {
                return;
            }
            if self.check(TokenKind::RBrace) {
                return;
            }
            self.advance();
        }
    }

    fn match_type_gt(&mut self) -> bool {
        if self.pending_type_gts > 0 {
            self.pending_type_gts -= 1;
            return true;
        }
        if self.match_kind(TokenKind::Gt) {
            return true;
        }
        if self.match_kind(TokenKind::ShiftRight) {
            self.pending_type_gts += 1;
            return true;
        }
        false
    }
}

fn precedence(kind: TokenKind) -> u8 {
    match kind {
        TokenKind::Assign => PREC_ASSIGNMENT,
        TokenKind::NullCoalesce => PREC_COALESCING,
        TokenKind::Or => PREC_OR,
        TokenKind::And => PREC_AND,
        TokenKind::Eq | TokenKind::Ne => PREC_EQUALITY,
        TokenKind::Lt | TokenKind::Le | TokenKind::Gt | TokenKind::Ge => PREC_COMPARISON,
        TokenKind::BitOr => PREC_BIT_OR,
        TokenKind::BitXor => PREC_BIT_XOR,
        TokenKind::BitAnd => PREC_BIT_AND,
        TokenKind::ShiftLeft | TokenKind::ShiftRight => PREC_SHIFT,
        TokenKind::Concat => PREC_CONCAT,
        TokenKind::Plus | TokenKind::Minus => PREC_TERM,
        TokenKind::Star | TokenKind::Slash | TokenKind::Percent => PREC_FACTOR,
        TokenKind::LParen | TokenKind::LBracket | TokenKind::Dot => PREC_PRIMARY,
        _ => PREC_LOWEST,
    }
}

fn binary_op_from_token(kind: TokenKind) -> Option<BinOp> {
    Some(match kind {
        TokenKind::Concat => BinOp::StrConcat,
        TokenKind::Plus => BinOp::Add,
        TokenKind::Minus => BinOp::Sub,
        TokenKind::Star => BinOp::Mul,
        TokenKind::Slash => BinOp::Div,
        TokenKind::Percent => BinOp::Mod,
        TokenKind::Eq => BinOp::Eq,
        TokenKind::Ne => BinOp::Ne,
        TokenKind::Lt => BinOp::Lt,
        TokenKind::Le => BinOp::Le,
        TokenKind::Gt => BinOp::Gt,
        TokenKind::Ge => BinOp::Ge,
        TokenKind::And => BinOp::And,
        TokenKind::Or => BinOp::Or,
        TokenKind::BitAnd => BinOp::BitAnd,
        TokenKind::BitOr => BinOp::BitOr,
        TokenKind::BitXor => BinOp::BitXor,
        TokenKind::ShiftLeft => BinOp::ShiftLeft,
        TokenKind::ShiftRight => BinOp::ShiftRight,
        TokenKind::Assign => BinOp::Assign,
        _ => return None,
    })
}

fn is_use_value_terminator(ch: u8) -> bool {
    matches!(ch, b' ' | b'\t' | b'\n' | b'\r' | b';')
}

/// Result of `parse_infix`: either a new node or the original left operand
/// returned when no infix operator could be applied.
enum InfixResult {
    Node(ExprNode),
    Back(ExprNode),
}

fn normalize_checksum(value: &str) -> Option<String> {
    let checksum = value.strip_prefix("sha256:")?.to_ascii_lowercase();
    if checksum.len() != 64 {
        return None;
    }
    if !checksum.bytes().all(|b| b.is_ascii_hexdigit()) {
        return None;
    }
    Some(checksum)
}
