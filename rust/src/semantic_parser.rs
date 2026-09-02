//! Recursive-descent parser for the native semantic AST.

use crate::semantic_ast::*;
use crate::semantic_lexer::{lex, Literal, Position, Token, TokenKind};
use crate::semantic_types::{TypeParam, TypeRef};

#[derive(Clone, Debug, PartialEq)]
pub struct ParseError { pub position: Position, pub message: String }

pub fn parse(source: &str) -> Result<Program, ParseError> {
    Parser { tokens: lex(source).map_err(|e| ParseError { position: e.position, message: e.message })?, index: 0 }.program()
}

struct Parser { tokens: Vec<Token>, index: usize }

impl Parser {
    fn cur(&self) -> &Token { &self.tokens[self.index] }
    fn peek(&self, n: usize) -> &Token { &self.tokens[(self.index + n).min(self.tokens.len() - 1)] }
    fn at_text(&self, text: &str) -> bool { self.cur().text == text }
    fn at_kind(&self, kind: &TokenKind) -> bool { &self.cur().kind == kind }
    fn bump(&mut self) -> Token { let t = self.cur().clone(); if !self.at_kind(&TokenKind::Eof) { self.index += 1; } t }
    fn take_text(&mut self, text: &str) -> bool { if self.at_text(text) { self.bump(); true } else { false } }
    fn expect_text(&mut self, text: &str) -> Result<Token, ParseError> { if self.at_text(text) { Ok(self.bump()) } else { self.error(format!("expected {:?}, found {:?}", text, self.cur().text)) } }
    fn expect_type_close(&mut self) -> Result<Token, ParseError> {
        // Keep shift operators intact in expressions, but split `>>` while
        // consuming nested generic type arguments.
        if self.cur().text == ">>" {
            let token = self.cur().clone();
            let mut first = token.clone(); first.text = ">".into();
            let mut second = token; second.text = ">".into();
            self.tokens[self.index] = first;
            self.tokens.insert(self.index + 1, second);
        }
        self.expect_text(">")
    }
    fn error<T>(&self, message: impl Into<String>) -> Result<T, ParseError> { Err(ParseError { position: self.cur().position, message: message.into() }) }
    fn terms(&mut self) { while self.at_kind(&TokenKind::Newline) || self.at_kind(&TokenKind::Semi) { self.bump(); } }
    fn optional_term(&mut self) { if self.at_kind(&TokenKind::Newline) || self.at_kind(&TokenKind::Semi) { self.bump(); } }

    fn program(&mut self) -> Result<Program, ParseError> {
        self.terms(); self.expect_text("package")?; let package = self.bump().text; self.terms();
        let mut program = Program { package, ..Program::default() };
        while self.at_text("use") { program.uses.push(self.use_decl()?); self.terms(); }
        while !self.at_kind(&TokenKind::Eof) {
            self.terms(); if self.at_kind(&TokenKind::Eof) { break; }
            program.declarations.push(self.declaration()?);
            if !self.at_kind(&TokenKind::Eof) && !self.at_kind(&TokenKind::Newline) && !self.at_kind(&TokenKind::Semi) {
                return self.error("P078: adjacent top-level declarations require a newline or semicolon");
            }
            self.terms();
        }
        Ok(program)
    }

    fn use_decl(&mut self) -> Result<UseDecl, ParseError> {
        self.expect_text("use")?; let scheme = self.bump().text; self.expect_text(":")?;
        let mut value = String::new();
        if matches!(self.cur().kind, TokenKind::String) { value = self.bump().text; }
        else { while !self.at_kind(&TokenKind::Newline) && !self.at_kind(&TokenKind::Semi) && !self.at_kind(&TokenKind::Eof) { value.push_str(&self.bump().text); } }
        Ok(UseDecl { scheme, value })
    }

    fn declaration(&mut self) -> Result<Decl, ParseError> {
        let public = self.take_text("pub");
        match self.cur().text.as_str() {
            "func" => Ok(Decl::Function(self.function(public, false, true)?)),
            "struct" => Ok(Decl::Struct(self.struct_decl(public)?)),
            "enum" => Ok(Decl::Enum(self.enum_decl(public)?)),
            "trait" => Ok(Decl::Trait(self.trait_decl(public)?)),
            _ => self.error("expected top-level function, struct, enum, or trait"),
        }
    }

    fn type_params(&mut self) -> Result<Vec<TypeParam>, ParseError> {
        if !self.take_text("<") { return Ok(Vec::new()); }
        let mut result = Vec::new();
        while !self.at_text(">") && self.cur().text != ">>" {
            let name = self.bump().text; let mut constraints = Vec::new();
            if self.take_text(":") { constraints.push(self.typ()?); while self.take_text("&") { constraints.push(self.typ()?); } }
            result.push(TypeParam::new(name, constraints));
            if !self.take_text(",") { break; }
        }
        self.expect_type_close()?; Ok(result)
    }

    fn typ(&mut self) -> Result<TypeRef, ParseError> {
        let mut name = self.bump().text;
        while self.take_text(".") { name.push('.'); name.push_str(&self.bump().text); }
        let mut args = Vec::new();
        if self.take_text("<") {
            while !self.at_text(">") { args.push(self.typ()?); if !self.take_text(",") { break; } }
            self.expect_type_close()?;
        }
        let mut result = TypeRef::generic(name, args); if self.take_text("?") { result.nullable = true; } Ok(result)
    }

    fn params(&mut self) -> Result<Vec<Param>, ParseError> {
        self.expect_text("(")?; self.terms(); let mut result = Vec::new();
        while !self.at_text(")") {
            let name = self.bump().text; self.expect_text(":")?; let variadic = self.take_text("..."); let typ = self.typ()?;
            result.push(Param { name, typ, variadic }); self.terms(); if !self.take_text(",") { break; } self.terms();
        }
        self.expect_text(")")?; Ok(result)
    }

    fn function(&mut self, public: bool, mutating: bool, body_required: bool) -> Result<Function, ParseError> {
        self.expect_text("func")?; let name = self.bump().text; let type_params = self.type_params()?; let params = self.params()?;
        let return_type = if self.take_text("->") { self.typ()? } else { TypeRef::named("void") };
        let body = if body_required { self.terms(); Some(self.block()?) } else { None };
        Ok(Function { name, public, mutating, params, return_type, type_params, body })
    }

    fn struct_decl(&mut self, public: bool) -> Result<StructDecl, ParseError> {
        self.expect_text("struct")?; let name = self.bump().text; let type_params = self.type_params()?; self.terms(); self.expect_text("{")?; self.terms();
        let mut fields = Vec::new(); let mut methods = Vec::new();
        while !self.at_text("}") {
            let member_public = self.take_text("pub"); let mutable = self.take_text("mut");
            if self.at_text("func") { methods.push(self.function(member_public, mutable, true)?); }
            else { let field_name = self.bump().text; self.expect_text(":")?; let typ = self.typ()?; fields.push(Field { name: field_name, typ, public: member_public, mutable }); }
            self.terms(); let _ = self.take_text(","); self.terms();
        }
        self.expect_text("}")?; Ok(StructDecl { name, public, fields, methods, type_params })
    }

    fn trait_decl(&mut self, public: bool) -> Result<TraitDecl, ParseError> {
        self.expect_text("trait")?; let name = self.bump().text; let type_params = self.type_params()?; self.terms(); self.expect_text("{")?; self.terms(); let mut methods = Vec::new();
        while !self.at_text("}") { let mutating = self.take_text("mut"); methods.push(self.function(true, mutating, false)?); self.terms(); }
        self.expect_text("}")?; Ok(TraitDecl { name, public, methods, type_params })
    }

    fn enum_decl(&mut self, public: bool) -> Result<EnumDecl, ParseError> {
        self.expect_text("enum")?; let name = self.bump().text; let type_params = self.type_params()?; self.terms(); self.expect_text("{")?; self.terms(); let mut members = Vec::new();
        while !self.at_text("}") { let member = self.bump().text; let mut payload = Vec::new(); if self.take_text("(") { while !self.at_text(")") { payload.push(self.typ()?); if !self.take_text(",") { break; } } self.expect_text(")")?; }
            let value = if self.take_text("=") { Some(self.bump().text.parse().map_err(|_| ParseError { position: self.cur().position, message: "enum value must be an integer".into() })?) } else { None };
            members.push(EnumMember { name: member, payload, value }); self.terms(); let _ = self.take_text(","); self.terms();
        }
        self.expect_text("}")?; Ok(EnumDecl { name, public, members, type_params })
    }

    fn block(&mut self) -> Result<Block, ParseError> {
        self.expect_text("{")?; self.terms(); let mut statements = Vec::new();
        while !self.at_text("}") { if self.at_kind(&TokenKind::Eof) { return self.error("unterminated block"); } let statement = self.statement()?; let compound = matches!(statement, Stmt::If { .. } | Stmt::While { .. } | Stmt::For { .. } | Stmt::Switch { .. } | Stmt::Try { .. } | Stmt::Block(_)); statements.push(statement); if !compound && !self.at_text("}") && !self.at_kind(&TokenKind::Newline) && !self.at_kind(&TokenKind::Semi) { return self.error("P078: adjacent statements require a newline or semicolon"); } self.terms(); }
        self.expect_text("}")?; Ok(Block { statements })
    }

    fn statement(&mut self) -> Result<Stmt, ParseError> {
        self.terms();
        if self.at_text("if") { return self.if_stmt(); }
        if self.at_text("for") && self.peek(1).text == "(" { return self.error("P075: parenthesized map iteration is not supported"); }
        match self.cur().text.as_str() {
            "{" => return Ok(Stmt::Block(self.block()?)),
            "if" => { self.bump(); let condition = self.expr(0)?; self.terms(); let then_block = self.block()?; self.terms(); let else_branch = if self.take_text("else") { self.terms(); Some(Box::new(if self.take_text("if") { let condition = self.expr(0)?; self.terms(); Stmt::If { condition, then_block: self.block()?, else_branch: None } } else { Stmt::Block(self.block()?) })) } else { None }; return Ok(Stmt::If { condition, then_block, else_branch }); }
            "while" => { self.bump(); let condition = self.expr(0)?; self.terms(); return Ok(Stmt::While { condition, body: self.block()? }); }
            "for" => { self.bump(); let mut names = vec![self.bump().text]; if self.take_text(",") { names.push(self.bump().text); } self.expect_text("in")?; let iterable = self.expr(0)?; self.terms(); return Ok(Stmt::For { names, iterable, body: self.block()? }); }
            "return" => { self.bump(); if self.at_kind(&TokenKind::Newline) || self.at_kind(&TokenKind::Semi) || self.at_text("}") { return Ok(Stmt::Return(None)); } return Ok(Stmt::Return(Some(self.expr(0)?))); }
            "break" => { self.bump(); return Ok(Stmt::Break); }
            "continue" => { self.bump(); return Ok(Stmt::Continue); }
            "throw" => { self.bump(); return Ok(Stmt::Throw(self.expr(0)?)); }
            "switch" => return self.switch_stmt(),
            "try" => return self.try_stmt(),
            "mut" => { self.bump(); return self.variable(true); }
            _ => {}
        }
        if matches!(self.cur().kind, TokenKind::Ident | TokenKind::Keyword(_)) && self.peek(1).text == ":" { return self.variable(false); }
        Ok(Stmt::Expr(self.expr(0)?))
    }

    fn if_stmt(&mut self) -> Result<Stmt, ParseError> {
        self.expect_text("if")?;
        let condition = self.expr(0)?;
        self.terms();
        let then_block = self.block()?;
        self.terms();
        let else_branch = if self.take_text("else") {
            self.terms();
            if self.take_text("if") {
                Some(Box::new(self.if_stmt_after_if()?))
            } else {
                Some(Box::new(Stmt::Block(self.block()?)))
            }
        } else { None };
        Ok(Stmt::If { condition, then_block, else_branch })
    }

    fn if_stmt_after_if(&mut self) -> Result<Stmt, ParseError> {
        let condition = self.expr(0)?;
        self.terms();
        let then_block = self.block()?;
        self.terms();
        let else_branch = if self.take_text("else") {
            self.terms();
            if self.take_text("if") {
                Some(Box::new(self.if_stmt_after_if()?))
            } else {
                Some(Box::new(Stmt::Block(self.block()?)))
            }
        } else { None };
        Ok(Stmt::If { condition, then_block, else_branch })
    }

    fn variable(&mut self, mutable: bool) -> Result<Stmt, ParseError> {
        let name = self.bump().text; self.expect_text(":")?; let typ = self.typ()?; let value = if self.take_text("=") { Some(self.expr(0)?) } else { None };
        Ok(Stmt::Var { name, typ: Some(typ), value, mutable })
    }

    fn switch_stmt(&mut self) -> Result<Stmt, ParseError> {
        self.expect_text("switch")?; let value = self.expr(0)?; self.terms(); self.expect_text("{")?; self.terms(); let mut cases = Vec::new();
        while !self.at_text("}") { if self.take_text("case") { let expr = self.expr(0)?; self.terms(); cases.push((Some(expr), self.block()?)); } else if self.take_text("default") { self.terms(); cases.push((None, self.block()?)); } else { return self.error("expected switch case"); } self.terms(); }
        self.expect_text("}")?; Ok(Stmt::Switch { value, cases })
    }

    fn try_stmt(&mut self) -> Result<Stmt, ParseError> {
        self.expect_text("try")?; self.terms(); let body = self.block()?; self.terms(); let mut catch_name = None; let mut catch = None;
        if self.take_text("catch") { self.expect_text("(")?; catch_name = Some(self.bump().text); if self.take_text(":") { let _ = self.typ()?; } self.expect_text(")")?; self.terms(); catch = Some(self.block()?); self.terms(); }
        let finally = if self.take_text("finally") { self.terms(); Some(self.block()?) } else { None };
        Ok(Stmt::Try { body, catch_name, catch, finally })
    }

    fn expr(&mut self, min_prec: u8) -> Result<Expr, ParseError> {
        let mut left = self.prefix()?;
        let mut steps = 0usize;
        loop {
            steps += 1;
            if steps > 1000 { return self.error(format!("parser made no progress at {:?}", self.cur().text)); }
            if matches!(self.cur().kind, TokenKind::Newline) && matches!(self.peek(1).text.as_str(), "??" | "||" | "&&" | "==" | "!=" | "<" | "<=" | ">" | ">=" | "|" | "^" | "&" | "<<" | ">>" | ".." | "+" | "-" | "*" | "/" | "%") { self.bump(); continue; }
            let current = self.cur().text.clone();
            let (op, prec) = match current.as_str() { "=" => (current.clone(), 1), "??" => (current.clone(), 2), "||" => (current.clone(), 3), "&&" => (current.clone(), 4), "==" | "!=" => (current.clone(), 5), "<" | "<=" | ">" | ">=" => (current.clone(), 6), "|" => (current.clone(), 7), "^" => (current.clone(), 8), "&" => (current.clone(), 9), "<<" | ">>" => (current.clone(), 10), ".." => (current.clone(), 11), "+" | "-" => (current.clone(), 12), "*" | "/" | "%" => (current.clone(), 13), _ => break };
            if prec < min_prec { break; } self.bump(); self.terms(); let right = self.expr(if op == "=" { prec } else { prec + 1 })?; left = Expr::Binary { left: Box::new(left), op, right: Box::new(right) };
        }
        Ok(left)
    }

    fn prefix(&mut self) -> Result<Expr, ParseError> {
        let mut expr = match self.cur().literal.clone() {
            Literal::Int(v) => { self.bump(); Expr::Int(v) }, Literal::Float(v) => { self.bump(); Expr::Float(v) }, Literal::String(v) => { self.bump(); Expr::String(v) }, Literal::Char(v) => { self.bump(); Expr::Char(v) }, Literal::Bool(v) => { self.bump(); Expr::Bool(v) }, Literal::Null => { self.bump(); Expr::Null }, Literal::None => match self.cur().text.as_str() {
                "!" | "-" | "~" => { let op = self.bump().text; Expr::Unary { op, expr: Box::new(self.prefix()?) } },
                "func" => self.func_expr()?,
                "(" => { self.bump(); let e = self.expr(0)?; self.expect_text(")")?; e },
                "[" => { self.bump(); self.list_expr()? },
                "{" => self.map_expr()?,
                _ => { let name = self.bump().text; let type_args = self.try_type_args()?; Expr::Name { name, type_args } },
            },
        };
        loop {
            if self.take_text("(") { let mut args = Vec::new(); self.terms(); while !self.at_text(")") { let spread = self.take_text("..."); args.push(CallArg { expr: self.expr(0)?, spread }); self.terms(); if !self.take_text(",") { break; } self.terms(); } self.expect_text(")")?; expr = Expr::Call { callee: Box::new(expr), args, type_args: Vec::new() }; }
            else if self.take_text(".") { let name = self.bump().text; let type_args = self.try_type_args()?; expr = Expr::Member { object: Box::new(expr), name, type_args }; }
            else if self.take_text("...") { expr = Expr::Spread(Box::new(expr)); }
            else if self.take_text("[") { let index = self.expr(0)?; self.expect_text("]")?; expr = Expr::Index { object: Box::new(expr), index: Box::new(index) }; }
            else if self.at_text("{") && self.looks_like_struct_literal(&expr) {
                let (name, type_args) = match &expr {
                    Expr::Name { name, type_args } => (name.clone(), type_args.clone()),
                    Expr::Member { .. } => (self.expression_path(&expr).ok_or_else(|| ParseError { position: self.cur().position, message: "struct literal requires a type name".into() })?, Vec::new()),
                    _ => break,
                };
                self.bump(); let mut fields = Vec::new(); self.terms();
                while !self.at_text("}") { let field = self.bump().text; self.expect_text(":")?; let value = self.expr(0)?; fields.push((field, value)); self.terms(); let _ = self.take_text(","); self.terms(); }
                self.expect_text("}")?; expr = Expr::Struct { name, type_args, fields };
            }
            else { break; }
        }
        Ok(expr)
    }

    fn list_expr(&mut self) -> Result<Expr, ParseError> {
        let mut items = Vec::new(); self.terms();
        while !self.at_text("]") {
            let spread = self.take_text("..."); let item = self.expr(0)?; items.push(if spread { Expr::Spread(Box::new(item)) } else { item });
            self.terms(); if !self.take_text(",") { break; } self.terms();
        }
        self.expect_text("]")?; Ok(Expr::List(items))
    }

    fn looks_like_struct_literal(&self, expression: &Expr) -> bool {
        let mut offset = 1;
        while matches!(self.peek(offset).kind, TokenKind::Newline | TokenKind::Semi) { offset += 1; }
        let type_like = self.expression_path(expression).and_then(|path| path.rsplit('.').next().map(str::to_string))
            .is_some_and(|name| name.chars().next().is_some_and(|ch| ch.is_ascii_uppercase()));
        (self.peek(offset).text == "}" && type_like && matches!(expression, Expr::Name { .. }))
            || (type_like && matches!(self.peek(offset).kind, TokenKind::Ident | TokenKind::Keyword(_)) && self.peek(offset + 1).text == ":")
    }

    fn expression_path(&self, expression: &Expr) -> Option<String> {
        match expression {
            Expr::Name { name, .. } => Some(name.clone()),
            Expr::Member { object, name, .. } => Some(format!("{}.{}", self.expression_path(object)?, name)),
            _ => None,
        }
    }

    fn map_expr(&mut self) -> Result<Expr, ParseError> {
        self.expect_text("{")?; self.terms(); let mut items = Vec::new();
        while !self.at_text("}") {
            let key = self.expr(0)?; self.expect_text(":")?; let value = self.expr(0)?; items.push((key, value));
            self.terms(); if !self.take_text(",") { break; } self.terms();
        }
        self.expect_text("}")?; Ok(Expr::Map(items))
    }

    fn func_expr(&mut self) -> Result<Expr, ParseError> {
        self.expect_text("func")?; let params = self.params()?; let return_type = if self.take_text("->") { self.typ()? } else { TypeRef::named("void") }; self.terms(); Ok(Expr::Function { params, return_type, body: self.block()? })
    }

    fn try_type_args(&mut self) -> Result<Vec<TypeRef>, ParseError> {
        if !self.at_text("<") { return Ok(Vec::new()); }
        let saved = self.index; self.bump(); let mut args = Vec::new();
        let parsed = (|| { while !self.at_text(">") && self.cur().text != ">>" { args.push(self.typ()?); if !self.take_text(",") { break; } } self.expect_type_close()?; Ok::<(), ParseError>(()) })();
        if parsed.is_err() { self.index = saved; return Ok(Vec::new()); }
        Ok(args)
    }
}

#[cfg(test)]
mod tests {
    use super::parse;
    use crate::semantic_ast::{Decl, Expr, Stmt};

    #[test]
    fn parses_generic_declarations_and_nullable_types() {
        let program = parse("package demo\nfunc identity<T>(value: T) -> T { return value }\nstruct Box<T> { value: T? }\n").unwrap();
        assert_eq!(program.package, "demo");
        assert!(matches!(&program.declarations[0], Decl::Function(f) if f.type_params.len() == 1));
        assert!(matches!(&program.declarations[1], Decl::Struct(s) if s.fields[0].typ.nullable));
    }

    #[test]
    fn parses_closures_and_nested_control_flow() {
        let program = parse("package demo\nfunc main() -> int {\n f: func<int, int> = func(x: int) -> int {\n  if x > 0 {\n   return x\n  }\n  return 0\n }\n return f(3)\n}\n").unwrap();
        let Decl::Function(main) = &program.declarations[0] else { panic!("expected function") };
        assert!(matches!(&main.body.as_ref().unwrap().statements[0], Stmt::Var { value: Some(Expr::Function { .. }), .. }));
    }

    #[test]
    fn parses_algebraic_enum_payloads_and_patterns_as_calls() {
        let program = parse("package demo\nenum Result<T, E> { Ok(T) Error(E) }\nfunc main() -> int {\n r: Result<int, string> = Result.Ok(1)\n return 0\n}\n").unwrap();
        assert!(matches!(&program.declarations[0], Decl::Enum(e) if e.members[0].payload.len() == 1));
        let Decl::Function(main) = &program.declarations[1] else { panic!("expected function") };
        assert_eq!(main.body.as_ref().unwrap().statements.len(), 2);
    }

}
