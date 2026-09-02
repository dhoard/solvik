//! Static validation for the native semantic frontend.
//!
//! Runtime checks remain in `semantic_runtime` as a defensive layer. This
//! pass handles the source errors that must be rejected before entrypoint
//! execution, while keeping the type model shared with the parser/runtime.

use crate::semantic_ast::{Block, Decl, EnumDecl, Expr, Function, Program, Stmt, StructDecl};
use crate::semantic_types::{assignable, TypeRef};
use std::collections::HashMap;

#[derive(Clone, Debug)]
pub struct ValidationError { pub code: String, pub message: String }

impl ValidationError {
    fn new(code: &str, message: impl Into<String>) -> Self { Self { code: code.into(), message: message.into() } }
}

#[derive(Clone)]
struct Binding { typ: TypeRef, mutable: bool }

pub fn validate(program: &Program) -> Result<(), ValidationError> {
    let mut validator = Validator::new(program, &[]);
    validator.validate()
}

pub fn validate_with_dependencies(program: &Program, dependencies: &[Program]) -> Result<(), ValidationError> {
    let mut validator = Validator::new(program, dependencies);
    validator.validate()
}

struct Validator<'a> {
    program: &'a Program,
    functions: HashMap<String, Function>,
    structs: HashMap<String, StructDecl>,
    enums: HashMap<String, EnumDecl>,
    traits: HashMap<String, usize>,
    external_types: HashMap<String, bool>,
    scopes: Vec<HashMap<String, Binding>>,
    current: Option<Function>,
    current_struct: Option<StructDecl>,
    loops: usize,
}

impl<'a> Validator<'a> {
    fn new(program: &'a Program, dependencies: &[Program]) -> Self {
        let mut functions = HashMap::new(); let mut structs = HashMap::new(); let mut enums = HashMap::new(); let mut traits = HashMap::new();
        for declaration in &program.declarations { match declaration {
            Decl::Function(function) => { functions.insert(function.name.clone(), function.clone()); }
            Decl::Struct(structure) => { structs.insert(structure.name.clone(), structure.clone()); }
            Decl::Enum(enumeration) => { enums.insert(enumeration.name.clone(), enumeration.clone()); }
            Decl::Trait(trait_decl) => { traits.insert(trait_decl.name.clone(), trait_decl.type_params.len()); }
        }}
        for (name, arity) in [("Stringable", 0), ("Equatable", 0), ("Comparable", 0), ("Hashable", 0), ("Countable", 0), ("Iterable", 1), ("Collection", 1)] { traits.insert(name.into(), arity); }
        let mut external_types = HashMap::new();
        for dependency in dependencies {
            for declaration in &dependency.declarations {
                match declaration {
                    Decl::Struct(structure) => { let key = format!("{}.{}", dependency.package, structure.name); external_types.insert(key.clone(), structure.public); let mut qualified = structure.clone(); qualified.name = key.clone(); structs.insert(key, qualified); }
                    Decl::Enum(enumeration) => { let key = format!("{}.{}", dependency.package, enumeration.name); external_types.insert(key.clone(), enumeration.public); let mut qualified = enumeration.clone(); qualified.name = key.clone(); enums.insert(key, qualified); }
                    Decl::Trait(trait_decl) => { traits.insert(format!("{}.{}", dependency.package, trait_decl.name), trait_decl.type_params.len()); }
                    Decl::Function(_) => {}
                }
            }
        }
        Self { program, functions, structs, enums, traits, external_types, scopes: Vec::new(), current: None, current_struct: None, loops: 0 }
    }

    fn validate(&mut self) -> Result<(), ValidationError> {
        let mut names = HashMap::<String, &'static str>::new();
        for declaration in &self.program.declarations {
            let (name, kind) = match declaration {
                Decl::Function(function) => (&function.name, "function"), Decl::Struct(structure) => (&structure.name, "struct"),
                Decl::Enum(enumeration) => (&enumeration.name, "enum"), Decl::Trait(trait_decl) => (&trait_decl.name, "trait"),
            };
            if names.insert(name.clone(), kind).is_some() { let code = if kind == "function" { "C090" } else { "C109" }; return Err(ValidationError::new(code, format!("'{}' is already declared", name))); }
        }
        // Imported declarations are registered for name lookup, but their
        // fields and bodies must be validated in the owning package. Their
        // field annotations are package-local (for example `AstNode` inside
        // `bootstrap_native.Ast`), so validating them here would incorrectly
        // resolve those names against the entry package.
        for declaration in &self.program.declarations {
            match declaration {
                Decl::Struct(structure) => self.validate_struct_decl(structure)?,
                Decl::Enum(enumeration) => self.validate_enum_decl(enumeration)?,
                Decl::Function(_) | Decl::Trait(_) => {}
            }
        }
        for declaration in &self.program.declarations {
            if let Decl::Function(function) = declaration { self.validate_function(function, None)?; }
        }
        Ok(())
    }

    fn validate_struct_decl(&mut self, structure: &StructDecl) -> Result<(), ValidationError> {
        let mut fields = HashMap::new();
        for field in &structure.fields {
            if fields.insert(field.name.clone(), ()).is_some() { return Err(ValidationError::new("C091", format!("duplicate field '{}'", field.name))); }
            self.check_type(&field.typ, &structure.type_params.iter().map(|p| p.name.clone()).collect())?;
            if field.typ.name == structure.name && !field.typ.nullable { return Err(ValidationError::new("C097", format!("recursive struct field '{}' must be nullable or indirect", field.name))); }
        }
        for method in &structure.methods { self.validate_function(method, Some(structure))?; }
        Ok(())
    }

    fn validate_enum_decl(&self, enumeration: &EnumDecl) -> Result<(), ValidationError> {
        let mut members = HashMap::new(); let has_payload = enumeration.members.iter().any(|member| !member.payload.is_empty());
        for member in &enumeration.members {
            if members.insert(member.name.clone(), ()).is_some() { return Err(ValidationError::new("C091", format!("duplicate enum case '{}'", member.name))); }
            if has_payload && member.value.is_some() { return Err(ValidationError::new("C107", "payload enums cannot use integer case values")); }
            for typ in &member.payload { self.check_type(typ, &enumeration.type_params.iter().map(|p| p.name.clone()).collect())?; }
        }
        Ok(())
    }

    fn check_type(&self, typ: &TypeRef, params: &Vec<String>) -> Result<(), ValidationError> {
        for argument in &typ.args { self.check_type(argument, params)?; }
        if typ.name == "void" { return Ok(()); }
        if typ.name == "func" {
            if typ.args.is_empty() { return Err(ValidationError::new("P076", "function types require a return type")); }
            if typ.args[..typ.args.len() - 1].iter().any(|arg| arg.name == "void") { return Err(ValidationError::new("C104", "void is only allowed as a function return type")); }
            return Ok(())
        }
        if params.iter().any(|param| param == &typ.name) || self.traits.contains_key(&typ.name) || ["any", "exception", "regex", "bool", "byte", "int", "float", "char", "string"].contains(&typ.name.as_str()) { return Ok(()); }
        if let Some(public) = self.external_types.get(&typ.name) { if !*public { return Err(ValidationError::new("C120", format!("type '{}' is private in its package", typ.name))); } }
        let expected = match typ.name.as_str() { "list" | "stack" => Some(1), "map" => Some(2), _ => self.structs.get(&typ.name).map(|s| s.type_params.len()).or_else(|| self.enums.get(&typ.name).map(|e| e.type_params.len())) };
        match expected {
            Some(arity) if arity != typ.args.len() => Err(ValidationError::new("C096", format!("type '{}' requires {} type argument(s)", typ.name, arity))),
            Some(_) => Ok(()),
            None => Err(ValidationError::new("C110", format!("unknown type '{}'", typ.name))),
        }
    }

    fn validate_function(&mut self, function: &Function, owner: Option<&StructDecl>) -> Result<(), ValidationError> {
        if function.name == "main" && owner.is_none() {
            if !function.params.is_empty() { return Err(ValidationError::new("C123", "entry function 'main' must take no parameters")); }
            if function.return_type.name != "int" && function.return_type.name != "void" { return Err(ValidationError::new("C124", format!("entry function 'main' must return int or nothing, not {}", function.return_type))); }
        }
        if let Some(owner) = owner { if function.type_params.iter().any(|parameter| owner.type_params.iter().any(|outer| outer.name == parameter.name)) { return Err(ValidationError::new("C099", "method type parameter shadows an enclosing type parameter")); } }
        if owner.is_some() && !function.mutating && function.body.as_ref().is_some_and(contains_self_assignment) { return Err(ValidationError::new("C068", "mutating a receiver requires a mutating method")); }
        for parameter in &function.type_params { for constraint in &parameter.constraints { let Some(arity) = self.traits.get(&constraint.name) else { return Err(ValidationError::new("C110", format!("unknown type '{}' in constraint", constraint.name))); }; if *arity != constraint.args.len() { return Err(ValidationError::new("C096", format!("constraint '{}' requires {} type argument(s)", constraint.name, arity))); } } }
        let mut params = HashMap::new();
        let mut type_params: Vec<String> = owner.map(|structure| structure.type_params.iter().map(|p| p.name.clone()).collect()).unwrap_or_default();
        type_params.extend(function.type_params.iter().map(|p| p.name.clone()));
        for param in &function.params {
            if params.insert(param.name.clone(), ()).is_some() { return Err(ValidationError::new("C092", format!("duplicate parameter '{}'", param.name))); }
            self.check_type(&param.typ, &type_params)?;
        }
        if function.return_type.name != "void" { self.check_type(&function.return_type, &type_params)?; }
        let Some(body) = &function.body else { return Ok(()); };
        let saved = (self.current.clone(), self.current_struct.clone(), self.scopes.clone());
        self.current = Some(function.clone()); self.current_struct = owner.cloned(); self.scopes = vec![params.into_iter().map(|(name, _)| {
            let param = function.params.iter().find(|param| param.name == name).unwrap();
            (name, Binding { typ: if param.variadic { TypeRef::generic("list", vec![param.typ.clone()]) } else { param.typ.clone() }, mutable: false })
        }).collect()];
        self.loops = 0;
        self.check_block(body)?;
        if function.return_type.name != "void" && !definitely_returns(body) { return Err(ValidationError::new("C111", format!("function '{}' does not return on every path", function.name))); }
        (self.current, self.current_struct, self.scopes) = saved;
        Ok(())
    }

    fn check_block(&mut self, block: &Block) -> Result<(), ValidationError> {
        let mut terminated = false;
        self.scopes.push(HashMap::new());
        for statement in &block.statements {
            if terminated { return Err(ValidationError::new("C112", "unreachable statement")); }
            self.check_statement(statement)?;
            terminated = matches!(statement, Stmt::Return(_) | Stmt::Throw(_) | Stmt::Break | Stmt::Continue);
        }
        self.scopes.pop(); Ok(())
    }

    fn lookup(&self, name: &str) -> Option<Binding> { self.scopes.iter().rev().find_map(|scope| scope.get(name).cloned()) }
    fn declare(&mut self, name: String, binding: Binding) { if let Some(scope) = self.scopes.last_mut() { scope.insert(name, binding); } }

    fn check_statement(&mut self, statement: &Stmt) -> Result<(), ValidationError> {
        if let Stmt::Var { typ: Some(typ), value: Some(value), .. } = statement {
            let actual = self.infer(value)?;
            if typ.name == "list" && typ.args.len() == 1 && actual.name == "list" && !compatible(&actual, typ) { return Err(ValidationError::new("C082", "list element type mismatch")); }
            if typ.name == "map" && typ.args.len() == 2 && actual.name == "map" && !compatible(&actual, typ) { return Err(ValidationError::new("C037", "map value type mismatch")); }
        }
        if let Stmt::If { condition, then_block, else_branch } = statement {
            self.infer(condition)?;
            let refinement = match condition {
                Expr::Binary { left, op, right } if op == "!=" && matches!(right.as_ref(), Expr::Null) => {
                    if let Expr::Name { name, .. } = left.as_ref() { self.lookup(name).map(|binding| (name.clone(), binding.typ.nonnull())) } else { None }
                }
                Expr::Binary { left, op, right } if op == "!=" && matches!(left.as_ref(), Expr::Null) => {
                    if let Expr::Name { name, .. } = right.as_ref() { self.lookup(name).map(|binding| (name.clone(), binding.typ.nonnull())) } else { None }
                }
                _ => None,
            };
            if let Some((name, typ)) = refinement { self.scopes.push(HashMap::new()); self.declare(name, Binding { typ, mutable: false }); self.check_block(then_block)?; self.scopes.pop(); } else { self.check_block(then_block)?; }
            if let Some(branch) = else_branch { self.check_statement(branch)?; }
            return Ok(());
        }
        match statement {
            Stmt::Var { name, typ, value, mutable } => {
                if let Some(typ) = typ { let params = self.current.as_ref().map(|f| f.type_params.iter().map(|p| p.name.clone()).collect()).unwrap_or_default(); if typ.name == "void" { return Err(ValidationError::new("C122", "void is not a value type")); } self.check_type(typ, &params)?; if let Some(value) = value { let actual = self.infer(value)?; let struct_literal_match = matches!(value, Expr::Struct { name, .. } if name == &typ.name && !typ.args.is_empty()); let enum_constructor_match = matches!(value, Expr::Call { callee, .. } if expression_name(callee).is_some_and(|name| name.rsplit('.').nth(1) == Some(typ.name.rsplit('.').next().unwrap_or(&typ.name)) && !typ.args.is_empty())); if enum_constructor_match { self.check_enum_initializer(value, typ)?; } if typ.name == "func" && actual.name == "func" && actual != *typ { return Err(ValidationError::new("C100", "function signature mismatch")); } if !struct_literal_match && !enum_constructor_match && !compatible(&actual, typ) && !(typ.name == "exception" && actual.name == "string") { return Err(ValidationError::new("C118", format!("declared type {} but initializer has type {}", typ, actual))); } } self.declare(name.clone(), Binding { typ: typ.clone(), mutable: *mutable }); }
            }
            Stmt::Expr(expression) => { self.infer(expression)?; }
            Stmt::Return(expression) => {
                let expected = self.current.as_ref().map(|function| function.return_type.clone()).unwrap_or_else(|| TypeRef::named("void"));
                match expression { None if expected.name != "void" => return Err(ValidationError::new("C115", "return requires a value")), Some(_) if expected.name == "void" => return Err(ValidationError::new("C115", "void function cannot return a value")), Some(expression) => { let actual = self.infer(expression)?; if !compatible(&actual, &expected) { return Err(ValidationError::new("C114", format!("return value of type {} is not assignable to {}", actual, expected))); } }, _ => {} }
            }
            Stmt::If { condition, then_block, else_branch } => { self.infer(condition)?; self.check_block(then_block)?; if let Some(branch) = else_branch { self.check_statement(branch)?; } }
            Stmt::While { condition, body } => { self.infer(condition)?; self.loops += 1; self.check_block(body)?; self.loops -= 1; }
            Stmt::For { names, iterable, body } => { let typ = self.infer(iterable)?; let element = if typ.name == "map" && names.len() == 2 && typ.args.len() == 2 { (typ.args[0].clone(), typ.args[1].clone()) } else { (typ.args.first().cloned().unwrap_or_else(|| TypeRef::named("unknown")), TypeRef::named("unknown")) }; self.loops += 1; self.scopes.push(HashMap::new()); self.declare(names[0].clone(), Binding { typ: element.0, mutable: false }); if names.len() == 2 { self.declare(names[1].clone(), Binding { typ: element.1, mutable: false }); } self.check_block(body)?; self.scopes.pop(); self.loops -= 1; }
            Stmt::Switch { value, cases } => self.check_switch(value, cases)?,
            Stmt::Try { body, catch, finally, .. } => { self.check_block(body)?; if let Some(catch) = catch { self.check_block(catch)?; } if let Some(finally) = finally { self.check_block(finally)?; } }
            Stmt::Throw(expression) => { self.infer(expression)?; }
            Stmt::Break | Stmt::Continue if self.loops == 0 => return Err(ValidationError::new("C113", "break or continue outside of a loop")),
            Stmt::Block(block) => self.check_block(block)?,
            _ => {}
        }
        Ok(())
    }

    fn check_switch(&mut self, value: &Expr, cases: &[(Option<Expr>, Block)]) -> Result<(), ValidationError> {
        let switch_type = self.infer(value)?;
        let enum_name = switch_type.name.rsplit('.').next().unwrap_or(&switch_type.name).to_string();
        let enumeration = self.enums.get(&enum_name).cloned();
        let mut seen = HashMap::new(); let mut has_default = false;
        for (pattern, body) in cases {
            if let Some(pattern) = pattern {
                if let Expr::Call { callee, args, .. } = pattern {
                    if let Expr::Name { name, .. } = callee.as_ref() {
                        if name == "regex" { self.infer(pattern)?; self.check_block(body)?; continue; }
                    }
                    if let Expr::Name { name, .. } = callee.as_ref() {
                        let Some(enumeration) = &enumeration else { return Err(ValidationError::new("C107", "invalid enum pattern")); };
                        let Some(member) = enumeration.members.iter().find(|member| member.name == *name) else { return Err(ValidationError::new("C107", "invalid enum pattern")); };
                        if member.payload.len() != args.len() { return Err(ValidationError::new("C107", "enum pattern payload count mismatch")); }
                        for (argument, expected) in args.iter().zip(&member.payload) {
                            let expected = instantiate_type(expected, enumeration, &switch_type);
                            if !valid_pattern_element(&argument.expr) { return Err(ValidationError::new("C107", "invalid enum pattern element")); }
                            if let Some(actual) = literal_type(&argument.expr) { if !compatible(&actual, &expected) { return Err(ValidationError::new("C108", "literal pattern type does not match payload type")); } }
                        }
                        if seen.insert(name.clone(), ()).is_some() { return Err(ValidationError::new("C106", format!("duplicate case pattern '{}'", name))); }
                        self.check_block(body)?;
                        continue;
                    }
                }
                match pattern {
                    Expr::Member { object, name, .. } => {
                        let pattern_type = expression_name(object).unwrap_or_default();
                        if enumeration.is_none() && self.enums.contains_key(pattern_type.rsplit('.').next().unwrap_or(&pattern_type)) { return Err(ValidationError::new("C094", "switch case enum does not match switch enum")); }
                        if enumeration.is_some() && pattern_type.rsplit('.').next() != Some(enum_name.as_str()) { return Err(ValidationError::new("C094", "switch case enum does not match switch enum")); }
                        if let Some(enumeration) = &enumeration { if let Some(member) = enumeration.members.iter().find(|member| member.name == *name) { if !member.payload.is_empty() { return Err(ValidationError::new("C107", "payload case requires pattern arguments")); } } }
                        if seen.insert(name.clone(), ()).is_some() { return Err(ValidationError::new("C106", format!("duplicate case pattern '{}'", name))); }
                    }
                    Expr::Call { callee, args, .. } => {
                        let (object, name) = match callee.as_ref() { Expr::Member { object, name, .. } => (object, name), _ => return Err(ValidationError::new("C107", "invalid enum pattern")) };
                        let pattern_type = expression_name(object).unwrap_or_default();
                        if enumeration.is_none() && self.enums.contains_key(pattern_type.rsplit('.').next().unwrap_or(&pattern_type)) { return Err(ValidationError::new("C094", "switch case enum does not match switch enum")); }
                        if enumeration.is_some() && pattern_type.rsplit('.').next() != Some(enum_name.as_str()) { return Err(ValidationError::new("C094", "switch case enum does not match switch enum")); }
                        if let Some(enumeration) = &enumeration { if let Some(member) = enumeration.members.iter().find(|member| member.name == *name) { if member.payload.len() != args.len() { return Err(ValidationError::new("C107", "enum pattern payload count mismatch")); } for (argument, expected) in args.iter().zip(&member.payload) { let expected = instantiate_type(expected, enumeration, &switch_type); if !valid_pattern_element(&argument.expr) { return Err(ValidationError::new("C107", "invalid enum pattern element")); } if let Some(actual) = literal_type(&argument.expr) { if !compatible(&actual, &expected) { return Err(ValidationError::new("C108", "literal pattern type does not match payload type")); } } } } }
                        if seen.insert(name.clone(), ()).is_some() { return Err(ValidationError::new("C106", format!("duplicate case pattern '{}'", name))); }
                    }
                    _ => { if let Some(pattern_name) = expression_name(pattern) { if self.enums.contains_key(pattern_name.rsplit('.').next().unwrap_or(&pattern_name)) { return Err(ValidationError::new("C094", "switch case enum does not match switch enum")); } } let case_type = self.infer(pattern)?; if case_type.name == "null" && !switch_type.nullable { return Err(ValidationError::new("C094", "null case cannot match a non-nullable switch")); } if case_type.name != "unknown" && !compatible(&case_type, &switch_type) { return Err(ValidationError::new("C094", "switch case type does not match switch value")); } }
                }
            } else { has_default = true; }
            self.check_block(body)?;
        }
        if let Some(enumeration) = enumeration { if !has_default && enumeration.members.iter().any(|member| !seen.contains_key(&member.name)) { return Err(ValidationError::new("C105", format!("non-exhaustive switch over enum '{}'", enumeration.name))); } }
        Ok(())
    }

    fn check_enum_initializer(&mut self, expression: &Expr, expected: &TypeRef) -> Result<(), ValidationError> {
        let Expr::Call { callee, args, .. } = expression else { return Ok(()); };
        let Expr::Member { object, name, .. } = callee.as_ref() else { return Ok(()); };
        let Some(path) = expression_name(object) else { return Ok(()); };
        let enum_key = path.rsplit('.').next().unwrap_or(&path);
        let Some(enumeration) = self.enums.get(enum_key).cloned() else { return Ok(()); };
        let Some(member) = enumeration.members.iter().find(|member| member.name == *name) else { return Ok(()); };
        if member.payload.len() != args.len() { return Err(ValidationError::new("C101", "enum construction payload count mismatch")); }
        for (argument, payload_type) in args.iter().zip(&member.payload) {
            let expected_payload = instantiate_type(payload_type, &enumeration, expected);
            let actual = self.infer(&argument.expr)?;
            if !compatible(&actual, &expected_payload) { return Err(ValidationError::new("C101", "enum construction payload type mismatch")); }
        }
        Ok(())
    }

    fn satisfies(&self, actual: &TypeRef, parameter: &crate::semantic_types::TypeParam) -> bool {
        parameter.constraints.iter().all(|constraint| match constraint.name.rsplit('.').next().unwrap_or(&constraint.name) {
            "Stringable" => ["string", "bool", "byte", "char", "int", "float"].contains(&actual.name.as_str()) || self.structs.get(&actual.name).is_some_and(|structure| structure.methods.iter().any(|method| method.name == "string")),
            "Equatable" => true,
            "Countable" => actual.name == "string" || actual.name == "list" || actual.name == "map" || actual.name == "stack",
            "Iterable" | "Collection" => actual.name == "string" || actual.name == "list" || actual.name == "map" || actual.name == "stack" || self.structs.get(&actual.name).is_some_and(|structure| structure.methods.iter().any(|method| method.name == "iterator")),
            _ => true,
        })
    }

    fn infer(&mut self, expression: &Expr) -> Result<TypeRef, ValidationError> {
        let unknown = || TypeRef::named("unknown");
        if let Expr::Call { args, .. } = expression {
            for argument in args { self.infer(&argument.expr)?; }
        }
        if let Expr::Call { callee, .. } = expression {
            if let Expr::Name { name, .. } = callee.as_ref() {
                if self.lookup(name).is_some_and(|binding| binding.typ.name == "any") { return Ok(unknown()); }
                if name == "print" || name == "println" { return Ok(TypeRef::named("void")); }
            }
            if let Expr::Member { object, name, .. } = callee.as_ref() {
                if let Expr::Name { name: receiver, .. } = object.as_ref() {
                    if let Some(binding) = self.lookup(receiver) { if let Some(structure) = self.structs.get(&binding.typ.name) { if structure.methods.iter().any(|method| method.name == *name && method.mutating) && !binding.mutable { return Err(ValidationError::new("C068", "mutating method requires a mutable receiver")); } } }
                }
            }
        }
        if let Expr::Member { object, .. } = expression {
            if let Some(path) = expression_name(object) {
                if let Some(name) = path.rsplit('.').next() {
                    if self.enums.contains_key(name) { return Ok(TypeRef::named(name)); }
                }
            }
        }
        if let Expr::Binary { left, op, right } = expression {
            if op == "??" {
                let left_type = self.infer(left)?;
                if left_type.name == "void" { return Err(ValidationError::new("C028", "null coalescing cannot use a void expression")); }
                return self.infer(right);
            }
            if ["<", "<=", ">", ">="].contains(&op.as_str()) {
                let left_type = self.infer(left)?; let right_type = self.infer(right)?;
                if (left_type.name == "char") != (right_type.name == "char") { return Err(ValidationError::new("C017", "characters may only be compared with characters")); }
            }
        }
        if let Expr::Member { object, name, .. } = expression {
            let object_type = self.infer(object)?;
            if object_type.name.contains('.') {
                if let Some(structure) = self.structs.get(&object_type.name) {
                    if structure.fields.iter().any(|field| field.name == *name && !field.public) || structure.methods.iter().any(|method| method.name == *name && !method.public) {
                        return Err(ValidationError::new("C120", format!("member '{}' is private in its package", name)));
                    }
                }
            }
        }
        if let Expr::List(items) = expression {
            let mut element = items.iter().find(|item| !matches!(item, Expr::Null)).map(|item| self.infer(item)).transpose()?.unwrap_or_else(unknown);
            if items.iter().any(|item| matches!(item, Expr::Null)) { element.nullable = true; }
            return Ok(TypeRef::generic("list", vec![element]));
        }
        Ok(match expression {
            Expr::Int(_) => TypeRef::named("int"), Expr::Float(_) => TypeRef::named("float"), Expr::Bool(_) => TypeRef::named("bool"), Expr::Char(_) => TypeRef::named("char"), Expr::String(_) => TypeRef::named("string"), Expr::Null => TypeRef::named("null"),
            Expr::Name { name, .. } => self.lookup(name).map(|binding| binding.typ).or_else(|| self.functions.get(name).filter(|function| function.type_params.is_empty() && !function.params.last().is_some_and(|param| param.variadic)).map(|function| TypeRef::generic("func", function.params.iter().map(|param| param.typ.clone()).chain(std::iter::once(function.return_type.clone())).collect()))).unwrap_or_else(unknown),
            Expr::List(items) => TypeRef::generic("list", vec![items.first().map(|item| self.infer(item)).transpose()?.unwrap_or_else(unknown)]),
            Expr::Map(items) => TypeRef::generic("map", vec![items.first().map(|(key, _)| self.infer(key)).transpose()?.unwrap_or_else(unknown), items.first().map(|(_, value)| self.infer(value)).transpose()?.unwrap_or_else(unknown)]),
            Expr::Index { object, .. } => { let typ = self.infer(object)?; if typ.name == "string" { TypeRef::named("char") } else { typ.args.last().cloned().unwrap_or_else(unknown) } }
            Expr::Unary { op, expr } => if op == "!" { TypeRef::named("bool") } else { self.infer(expr)? },
            Expr::Binary { left, op, right } => { let l = self.infer(left)?; let r = self.infer(right)?; if op == "=" { if let Expr::Name { name, .. } = left.as_ref() { if let Some(binding) = self.lookup(name) { if !binding.mutable { return Err(ValidationError::new("C116", format!("cannot assign to immutable binding '{}'", name))); } } } if let Expr::Member { object, name, .. } = left.as_ref() { let object_type = self.infer(object)?; if let Some(structure) = self.structs.get(&object_type.name) { if let Some(field) = structure.fields.iter().find(|field| field.name == *name) { if !field.mutable { return Err(ValidationError::new("C117", format!("field '{}' is immutable", name))); } } } } if !compatible(&r, &l) { return Err(ValidationError::new("C119", format!("cannot assign {} to {}", r, l))); } l } else if ["==", "!=", "<", "<=", ">", ">=", "&&", "||"].contains(&op.as_str()) { TypeRef::named("bool") } else if op == ".." { TypeRef::named("string") } else if l.name == "float" || r.name == "float" { TypeRef::named("float") } else { l } }
            Expr::Call { callee, args, type_args } => { let explicit_args = match callee.as_ref() { Expr::Name { type_args, .. } | Expr::Member { type_args, .. } => type_args, _ => type_args }; if let Expr::Name { name, .. } = callee.as_ref() { if let Some(function) = self.functions.get(name).cloned() { if !function.type_params.is_empty() && !explicit_args.is_empty() && explicit_args.len() != function.type_params.len() { return Err(ValidationError::new("C096", "generic type argument count mismatch")); } if let Some(param) = function.params.last() { if param.variadic { for argument in args { let actual = self.infer(&argument.expr)?; if !compatible(&actual, &param.typ) { return Err(ValidationError::new("C101", "variadic argument type mismatch")); } } return Ok(function.return_type); } } for (argument, param) in args.iter().zip(&function.params) { let actual = self.infer(&argument.expr)?; if !function.type_params.is_empty() && !explicit_args.is_empty() { if let Some(index) = function.type_params.iter().position(|type_param| type_param.name == param.typ.name) { if index < explicit_args.len() && !compatible(&actual, &explicit_args[index]) { return Err(ValidationError::new("C101", "explicit type argument does not match argument")); } } } if let Some(type_param) = function.type_params.iter().find(|type_param| type_param.name == param.typ.name) { if !self.satisfies(&actual, type_param) { return Err(ValidationError::new("C095", format!("type {} does not satisfy generic constraint", actual))); } } } } } else if let Expr::Member { object, name, .. } = callee.as_ref() { if let Some(enum_name) = expression_name(object) { if let Some(enumeration) = self.enums.get(enum_name.rsplit('.').next().unwrap_or(&enum_name)).cloned() { if let Some(member) = enumeration.members.iter().find(|member| member.name == *name) { if member.payload.len() != args.len() { return Err(ValidationError::new("C101", "enum construction payload count mismatch")); } for (argument, expected) in args.iter().zip(&member.payload) { let actual = self.infer(&argument.expr)?; if !compatible(&actual, expected) { return Err(ValidationError::new("C101", "enum construction payload type mismatch")); } } return Ok(TypeRef::named(&enumeration.name)); } } } } let typ = self.infer(callee)?; if typ.name == "func" && !typ.args.is_empty() { let params = &typ.args[..typ.args.len()-1]; if args.len() != params.len() { return Err(ValidationError::new("C101", "function argument count mismatch")); } for (argument, expected) in args.iter().zip(params) { let actual = self.infer(&argument.expr)?; if !compatible(&actual, expected) { return Err(ValidationError::new("C101", "function argument type mismatch")); } } } else if typ.name != "unknown" { return Err(ValidationError::new("C102", "value is not callable")); } typ.args.last().cloned().unwrap_or_else(unknown) }
            Expr::Member { object, name, .. } => { let typ = self.infer(object)?; if ["string", "list", "map", "stack"].contains(&typ.name.as_str()) { unknown() } else if let Some(structure) = self.structs.get(&typ.name) { if let Some(field) = structure.fields.iter().find(|field| field.name == *name) { field.typ.clone() } else if let Some(method) = structure.methods.iter().find(|method| method.name == *name) { TypeRef::generic("func", method.params.iter().map(|param| param.typ.clone()).chain(std::iter::once(method.return_type.clone())).collect()) } else { unknown() } } else { unknown() } }
            Expr::Struct { name, fields, .. } => { if let Some(structure) = self.structs.get(name).cloned() { for (field, value) in fields { let declared = structure.fields.iter().find(|candidate| candidate.name == *field).cloned(); if let Some(declared) = declared { let actual = self.infer(value)?; if !compatible(&actual, &declared.typ) { return Err(ValidationError::new("C098", format!("field '{}' has incompatible type", field))); } for parameter in &structure.type_params { if declared.typ.name == parameter.name && !self.satisfies(&actual, parameter) { return Err(ValidationError::new("C095", format!("type {} does not satisfy generic constraint", actual))); } } } } TypeRef::named(name) } else { unknown() } }
            Expr::Function { params, return_type, .. } => TypeRef::generic("func", params.iter().map(|param| param.typ.clone()).chain(std::iter::once(return_type.clone())).collect()),
            Expr::Spread(inner) => { let typ = self.infer(inner)?; typ.args.first().cloned().unwrap_or_else(unknown) },
        })
    }
}

fn expression_name(expression: &Expr) -> Option<String> { match expression { Expr::Name { name, .. } => Some(name.clone()), Expr::Member { object, name, .. } => Some(format!("{}.{}", expression_name(object)?, name)), _ => None } }
fn enum_case_name(expression: &Expr) -> Option<String> { match expression { Expr::Member { name, .. } => Some(name.clone()), Expr::Call { callee, .. } => enum_case_name(callee), _ => None } }
fn valid_pattern_element(expression: &Expr) -> bool { matches!(expression, Expr::Name { .. } | Expr::Int(_) | Expr::Float(_) | Expr::Bool(_) | Expr::Char(_) | Expr::String(_) | Expr::Null | Expr::Member { .. } | Expr::Call { .. }) }
fn literal_type(expression: &Expr) -> Option<TypeRef> { match expression { Expr::Int(_) => Some(TypeRef::named("int")), Expr::Float(_) => Some(TypeRef::named("float")), Expr::Bool(_) => Some(TypeRef::named("bool")), Expr::Char(_) => Some(TypeRef::named("char")), Expr::String(_) => Some(TypeRef::named("string")), Expr::Null => Some(TypeRef::named("null")), _ => None } }
fn instantiate_type(typ: &TypeRef, enumeration: &EnumDecl, actual: &TypeRef) -> TypeRef {
    if let Some(index) = enumeration.type_params.iter().position(|parameter| parameter.name == typ.name) {
        return actual.args.get(index).cloned().unwrap_or_else(|| typ.clone());
    }
    TypeRef { name: typ.name.clone(), args: typ.args.iter().map(|argument| instantiate_type(argument, enumeration, actual)).collect(), nullable: typ.nullable }
}

fn compatible(actual: &TypeRef, expected: &TypeRef) -> bool {
    if actual.name == "unknown" || expected.name == "unknown" || actual.name == "any" || expected.name == "any" { return true; }
    if actual.name.len() == 1 && actual.name.chars().next().is_some_and(|ch| ch.is_ascii_uppercase()) { return true; }
    if expected.name.len() == 1 && expected.name.chars().next().is_some_and(|ch| ch.is_ascii_uppercase()) { return true; }
    if actual.name == expected.name && actual.name == "list" && actual.args.len() == expected.args.len() && actual.args.iter().zip(&expected.args).any(|(actual, expected)| actual.args.is_empty() && !expected.args.is_empty()) { return true; }
    if actual.name == expected.name {
        if actual.args.is_empty() && expected.args.is_empty() { return assignable(actual, expected); }
        if actual.args.is_empty() || expected.args.is_empty() { return true; }
        if actual.args.len() == expected.args.len() {
            return actual.args.iter().zip(&expected.args).all(|(actual, expected)| actual.name == "unknown" || expected.name == "unknown" || actual.name.len() == 1 && actual.name.chars().next().is_some_and(|ch| ch.is_ascii_uppercase()) || expected.name.chars().next().is_some_and(|ch| ch.is_ascii_uppercase()) || actual == expected);
        }
    }
    if expected.name.chars().next().is_some_and(|ch| ch.is_ascii_uppercase()) { return true; }
    assignable(actual, expected)
}

fn definitely_returns(block: &Block) -> bool { block.statements.iter().any(|statement| match statement { Stmt::Return(_) | Stmt::Throw(_) => true, Stmt::Block(block) => definitely_returns(block), Stmt::If { then_block, else_branch: Some(else_branch), .. } => definitely_returns(then_block) && definitely_returns_stmt(else_branch), Stmt::While { condition: Expr::Bool(true), body } => definitely_returns(body), Stmt::Switch { cases, .. } => !cases.is_empty() && cases.iter().all(|(_, body)| definitely_returns(body)), Stmt::Try { body, .. } => definitely_returns(body), _ => false }) }
fn definitely_returns_stmt(statement: &Stmt) -> bool { match statement { Stmt::Block(block) => definitely_returns(block), Stmt::If { then_block, else_branch: Some(else_branch), .. } => definitely_returns(then_block) && definitely_returns_stmt(else_branch), Stmt::While { condition: Expr::Bool(true), body } => definitely_returns(body), Stmt::Switch { cases, .. } => !cases.is_empty() && cases.iter().all(|(_, body)| definitely_returns(body)), Stmt::Try { body, .. } => definitely_returns(body), Stmt::Return(_) | Stmt::Throw(_) => true, _ => false } }
fn contains_self_assignment(block: &Block) -> bool { block.statements.iter().any(|statement| match statement { Stmt::Expr(Expr::Binary { left, op, .. }) if op == "=" => matches!(left.as_ref(), Expr::Member { object, .. } if matches!(object.as_ref(), Expr::Name { name, .. } if name == "self")), Stmt::If { then_block, else_branch, .. } => contains_self_assignment(then_block) || else_branch.as_ref().is_some_and(|branch| definitely_contains_self_assignment(branch)), Stmt::While { body, .. } => contains_self_assignment(body), Stmt::For { body, .. } => contains_self_assignment(body), Stmt::Block(body) => contains_self_assignment(body), Stmt::Try { body, catch, finally, .. } => contains_self_assignment(body) || catch.as_ref().is_some_and(contains_self_assignment) || finally.as_ref().is_some_and(contains_self_assignment), _ => false }) }
fn definitely_contains_self_assignment(statement: &Stmt) -> bool { match statement { Stmt::Block(body) => contains_self_assignment(body), Stmt::If { then_block, else_branch, .. } => contains_self_assignment(then_block) || else_branch.as_ref().is_some_and(|branch| definitely_contains_self_assignment(branch)), _ => false } }
