//! Lexical scoping and name resolution.
//!
//! Port of internal/resolver/resolver.go.

use crate::ast::*;
use crate::diagnostic::Diagnostics;
use crate::source::Span;
use crate::symbol::{Scope, Symbol, SymbolKind};
use crate::types::{self, Kind, Type};
use std::cell::Cell;
use std::collections::{HashMap, HashSet};
use std::rc::Rc;

fn builtin_functions() -> &'static HashSet<&'static str> {
    static SET: std::sync::OnceLock<HashSet<&'static str>> = std::sync::OnceLock::new();
    SET.get_or_init(|| {
        [
            "print", "println", "string", "int", "float", "byte", "bool", "typeOf",
            "isType", "regex", "byteLength", "charAt", "substring", "contains",
            "startsWith", "endsWith", "indexOf", "toUpper", "toLower", "trim",
            "split", "join", "abs", "min", "max", "floor", "ceil", "round",
            "sqrt", "pow", "sin", "cos", "tan", "now", "sleep",
        ]
        .into_iter()
        .collect()
    })
}

fn known_modules() -> &'static [&'static str] {
    &[
        "core", "string", "math", "map", "env", "file", "process", "time",
        "random", "path", "base64", "hash", "secrets", "stack",
    ]
}

fn base_symbol() -> Symbol {
    Symbol {
        name: String::new(),
        kind: SymbolKind::Variable,
        ty: None,
        slot: -1,
        parameter: false,
        module_name: String::new(),
        defined: Cell::new(false),
        mut_flag: false,
        is_struct_field: false,
        field_index: 0,
        field_of_slot: 0,
    }
}

pub struct Resolver {
    diags: Diagnostics,
    scope: Rc<Scope>,
    func_names: HashSet<String>,
    loop_depth: i32,
    module_name: String,
    enum_names: HashSet<String>,
    struct_types: HashMap<String, Rc<Type>>,
    #[allow(dead_code)]
    trait_types: HashMap<String, Rc<Type>>,
    /// Mangled names ("module.func") of all functions across modules/files.
    all_funcs: Option<HashSet<String>>,
    external_type_names: HashSet<String>,
}

impl Resolver {
    pub fn new() -> Resolver {
        Resolver {
            diags: Diagnostics::new(),
            scope: Scope::new(None, None),
            func_names: HashSet::new(),
            loop_depth: 0,
            module_name: String::new(),
            enum_names: HashSet::new(),
            struct_types: HashMap::new(),
            trait_types: HashMap::new(),
            all_funcs: None,
            external_type_names: HashSet::new(),
        }
    }

    pub fn set_external_type_names(&mut self, names: &[String]) {
        for n in names {
            self.external_type_names.insert(n.clone());
        }
    }

    pub fn set_all_funcs(&mut self, names: HashSet<String>) {
        self.all_funcs = Some(names);
    }

    pub fn resolve(&mut self, prog: &mut Program) -> Diagnostics {
        self.enum_names = prog.enums.iter().map(|e| e.name.clone()).collect();

        // Process trait declarations (before structs)
        let mut traits = std::mem::take(&mut prog.traits);
        for td in &mut traits {
            self.process_trait_decl(td);
        }

        // Process struct declarations
        let mut structs = std::mem::take(&mut prog.structs);
        for sd in &mut structs {
            let mut fields = Vec::new();
            for f in &mut sd.fields {
                resolve_type_annotation(&mut f.ty);
                let ft = f.ty.resolved();
                fields.push(types::StructFieldInfo {
                    name: f.name.clone(),
                    ty: ft,
                    is_mut: f.is_mut,
                    is_pub: f.is_pub,
                });
            }
            let struct_ty = types::struct_type(&sd.name, fields);
            self.struct_types.insert(sd.name.clone(), struct_ty.clone());

            self.scope.declare(Rc::new(Symbol {
                name: sd.name.clone(),
                kind: SymbolKind::Struct,
                ty: Some(struct_ty.clone()),
                module_name: sd.name.clone(),
                ..base_symbol()
            }));

            for m in &mut sd.methods {
                self.func_names.insert(m.name.clone());
                if !prog.module.is_empty() {
                    self.func_names.insert(format!("{}.{}", prog.module, m.name));
                }
                // Resolve the _self parameter type to the struct type
                if !m.parameters.is_empty() && m.parameters[0].name == "_self" {
                    m.parameters[0].ty.resolved = Some(struct_ty.clone());
                }
                for p in &mut m.parameters {
                    if p.name != "_self" {
                        resolve_type_annotation(&mut p.ty);
                    }
                }
                for rt in &mut m.return_types {
                    resolve_type_annotation(rt);
                }
            }
        }

        // Collect function declarations
        let mut funcs = std::mem::take(&mut prog.funcs);
        {
            let module = &prog.module;
            for f in &funcs {
                if !module.is_empty() {
                    self.func_names.insert(format!("{}.{}", module, f.name));
                } else {
                    self.func_names.insert(f.name.clone());
                }
                self.func_names.insert(f.name.clone());
            }
        }

        // Declare modules from allFuncs
        if let Some(all) = &self.all_funcs.clone() {
            for mangled in all {
                if let Some(dot_idx) = mangled.rfind('.') {
                    let mod_name = &mangled[..dot_idx];
                    if self.scope.resolve(mod_name).is_none()
                        && !known_modules().contains(&mod_name)
                    {
                        self.scope.declare(Symbol::new_module(mod_name));
                    }
                }
            }
        }

        for m in known_modules() {
            if self.scope.resolve(m).is_none() {
                self.scope.declare(Symbol::new_module(m));
            }
        }

        // Enum names in scope (module-like for MemberExpr resolution)
        for en in &prog.enums {
            self.scope.declare(Symbol::new_module(&en.name));
        }

        self.module_name = prog.module.clone();

        for f in &mut funcs {
            self.resolve_function(f);
        }

        prog.funcs = funcs;
        prog.structs = structs;
        prog.traits = traits;
        std::mem::take(&mut self.diags)
    }

    fn resolve_function(&mut self, f: &mut Function) {
        for p in &mut f.parameters {
            resolve_type_annotation(&mut p.ty);
        }
        for rt in &mut f.return_types {
            resolve_type_annotation(rt);
        }

        let old_scope = self.scope.clone();
        self.scope = Scope::new(Some(old_scope.clone()), None);

        let mut slot = 0i32;
        for p in &f.parameters {
            let mut param_ty = p.ty.resolved();
            if p.variadic {
                param_ty = types::list_of(param_ty);
            }
            let sym = Rc::new(Symbol {
                name: p.name.clone(),
                kind: SymbolKind::Variable,
                ty: Some(param_ty),
                slot,
                parameter: true,
                ..base_symbol()
            });
            sym.defined.set(true);
            self.scope.declare(sym);
            slot += 1;
        }

        // Struct methods: expose receiver and fields
        if !f.struct_name.is_empty() {
            if let Some(struct_ty) = self.struct_types.get(&f.struct_name).cloned() {
                let self_sym = Rc::new(Symbol {
                    name: "self".to_string(),
                    kind: SymbolKind::Variable,
                    ty: Some(struct_ty.clone()),
                    slot: 0,
                    parameter: true,
                    ..base_symbol()
                });
                self_sym.defined.set(true);
                self.scope.declare(self_sym);

                for (i, field) in struct_ty.struct_fields.iter().enumerate() {
                    let sym = Rc::new(Symbol {
                        name: field.name.clone(),
                        kind: SymbolKind::Variable,
                        ty: Some(field.ty.clone()),
                        slot: 0,
                        defined: Cell::new(true),
                        mut_flag: field.is_mut,
                        is_struct_field: true,
                        field_index: i,
                        field_of_slot: 0,
                        parameter: false,
                        module_name: String::new(),
                    });
                    self.scope.declare(sym);
                }
            }
        }

        if let Some(body) = &mut f.body {
            self.resolve_block(body);
        }

        self.scope = old_scope;
    }

    fn resolve_block(&mut self, block: &mut Block) {
        for stmt in &mut block.statements {
            self.resolve_statement(stmt);
        }
    }

    fn resolve_statement(&mut self, stmt: &mut StmtNode) {
        match &mut stmt.kind {
            StmtKind::VarDecl(d) => self.resolve_var_decl(d),
            StmtKind::If(s) => self.resolve_if_stmt(s),
            StmtKind::While(s) => self.resolve_while_stmt(s),
            StmtKind::For(s) => self.resolve_for_stmt(s),
            StmtKind::Try(s) => self.resolve_try_stmt(s),
            StmtKind::Throw(e) => self.resolve_expr(e),
            StmtKind::Switch(s) => self.resolve_switch_stmt(s),
            StmtKind::Break => {
                if self.loop_depth <= 0 {
                    self.diags.add_error("R002", "break outside loop", stmt.span.clone());
                }
            }
            StmtKind::Continue => {
                if self.loop_depth <= 0 {
                    self.diags.add_error("R003", "continue outside loop", stmt.span.clone());
                }
            }
            StmtKind::Return(values) => {
                for v in values {
                    self.resolve_expr(v);
                }
            }
            StmtKind::Expr(e) => self.resolve_expr(e),
            StmtKind::Block(b) => self.resolve_block_scope(b),
        }
    }

    fn resolve_var_decl(&mut self, decl: &mut VarDecl) {
        resolve_type_annotation(&mut decl.ty);
        if let Some(init) = &mut decl.init {
            self.resolve_expr(init);
        }
        let var_ty = decl.ty.resolved();
        let sym = Rc::new(Symbol {
            name: decl.name.clone(),
            kind: SymbolKind::Variable,
            ty: Some(var_ty),
            slot: -1,
            defined: Cell::new(decl.init.is_some()),
            mut_flag: decl.is_mut,
            ..base_symbol()
        });
        self.scope.declare(sym);
    }

    fn resolve_if_stmt(&mut self, stmt: &mut IfStmt) {
        self.resolve_expr(&mut stmt.condition);
        self.resolve_block_scope(&mut stmt.then_block);
        for ei in &mut stmt.else_ifs {
            self.resolve_if_stmt(ei);
        }
        if let Some(eb) = &mut stmt.else_block {
            self.resolve_block_scope(eb);
        }
    }

    fn resolve_while_stmt(&mut self, stmt: &mut WhileStmt) {
        self.resolve_expr(&mut stmt.condition);
        self.loop_depth += 1;
        self.resolve_block_scope(&mut stmt.body);
        self.loop_depth -= 1;
    }

    fn resolve_for_stmt(&mut self, stmt: &mut ForStmt) {
        self.resolve_expr(&mut stmt.iterable);

        let old_scope = self.scope.clone();
        let func_type = self.scope.func_type.clone();
        self.scope = Scope::new(Some(old_scope.clone()), func_type);

        let sym = Rc::new(Symbol {
            name: stmt.variable.clone(),
            kind: SymbolKind::Variable,
            ty: Some(types::t_invalid()),
            slot: -1,
            ..base_symbol()
        });
        sym.defined.set(true);
        self.scope.declare(sym);

        if !stmt.value_variable.is_empty() {
            let sym = Rc::new(Symbol {
                name: stmt.value_variable.clone(),
                kind: SymbolKind::Variable,
                ty: Some(types::t_invalid()),
                slot: -1,
                ..base_symbol()
            });
            sym.defined.set(true);
            self.scope.declare(sym);
        }

        self.loop_depth += 1;
        self.resolve_block(&mut stmt.body);
        self.loop_depth -= 1;

        self.scope = old_scope;
    }

    fn resolve_switch_stmt(&mut self, stmt: &mut SwitchStmt) {
        self.resolve_expr(&mut stmt.expression);
        for c in &mut stmt.cases {
            self.resolve_expr(&mut c.expression);
            self.resolve_block_scope(&mut c.body);
        }
        if let Some(db) = &mut stmt.default_block {
            self.resolve_block_scope(db);
        }
    }

    fn resolve_try_stmt(&mut self, stmt: &mut TryStmt) {
        self.resolve_block_scope(&mut stmt.try_body);

        if let Some(catch) = &mut stmt.catch {
            let old_scope = self.scope.clone();
            let func_type = self.scope.func_type.clone();
            self.scope = Scope::new(Some(old_scope.clone()), func_type);

            resolve_type_annotation(&mut catch.param_type);
            let param_ty = catch.param_type.resolved();

            let sym = Rc::new(Symbol {
                name: catch.param_name.clone(),
                kind: SymbolKind::Variable,
                ty: Some(param_ty),
                slot: -1,
                ..base_symbol()
            });
            sym.defined.set(true);
            self.scope.declare(sym);

            self.resolve_block(&mut catch.body);
            self.scope = old_scope;
        }

        if let Some(fb) = &mut stmt.finally_block {
            self.resolve_block_scope(fb);
        }
    }

    fn resolve_block_scope(&mut self, block: &mut Block) {
        let old_scope = self.scope.clone();
        let func_type = self.scope.func_type.clone();
        self.scope = Scope::new(Some(old_scope.clone()), func_type);
        self.resolve_block(block);
        self.scope = old_scope;
    }

    fn resolve_expr(&mut self, expr: &mut ExprNode) {
        match &mut expr.kind {
            ExprKind::Ident(name) => self.resolve_identifier(name, &expr.span),
            ExprKind::Unary { operand, .. } => self.resolve_expr(operand),
            ExprKind::Binary { left, right, .. } => {
                self.resolve_expr(left);
                self.resolve_expr(right);
            }
            ExprKind::Call { function, args } => {
                self.resolve_expr(function);
                for arg in args {
                    self.resolve_expr(arg);
                }
            }
            ExprKind::Index { target, index } => {
                self.resolve_expr(target);
                self.resolve_expr(index);
            }
            ExprKind::List(elements) => {
                for el in elements {
                    self.resolve_expr(el);
                }
            }
            ExprKind::Map { keys, values } => {
                for i in 0..keys.len() {
                    self.resolve_expr(&mut keys[i]);
                    self.resolve_expr(&mut values[i]);
                }
            }
            ExprKind::Member { object, member } => {
                self.resolve_expr(object);
                if let ExprKind::Ident(name) = &object.kind {
                    let sym = self.scope.resolve(name);
                    if let Some(sym) = sym {
                        if sym.kind == SymbolKind::Module {
                            return;
                        }
                    }
                    if known_modules().contains(&name.as_str()) {
                        return;
                    }
                    if let Some(all) = &self.all_funcs {
                        let mangled = format!("{}.{}", name, member);
                        if all.contains(&mangled) {
                            return;
                        }
                        if known_modules().contains(&name.as_str())
                            && builtin_functions().contains(member.as_str())
                        {
                            return;
                        }
                    }
                }
                if let ExprKind::Ident(name) = &object.kind {
                    if self.enum_names.contains(name) || self.external_type_names.contains(name) {
                        return;
                    }
                }
            }
            ExprKind::Spread(inner) => self.resolve_expr(inner),
            ExprKind::StructLit { values, .. } => {
                for v in values {
                    self.resolve_expr(v);
                }
            }
            ExprKind::NullCoalescing { left, right } => {
                self.resolve_expr(left);
                self.resolve_expr(right);
            }
            _ => {}
        }
    }

    fn resolve_identifier(&mut self, name: &str, span: &Span) {
        if self.scope.resolve(name).is_some() {
            return;
        }
        if self.func_names.contains(name) {
            return;
        }
        if let Some(all) = &self.all_funcs {
            for mangled in all {
                if let Some(dot) = mangled.rfind('.') {
                    if &mangled[dot + 1..] == name && &mangled[..dot] == self.module_name {
                        return;
                    }
                }
            }
        }
        if builtin_functions().contains(name) {
            return;
        }
        if known_modules().contains(&name) {
            return;
        }
        if self.external_type_names.contains(name) {
            return;
        }
        self.diags
            .add_error("R004", &format!("undeclared identifier: {}", name), span.clone());
    }

    fn process_trait_decl(&mut self, td: &mut TraitDecl) {
        let mut methods = HashMap::new();
        for m in &mut td.methods {
            let mut param_types = Vec::new();
            for p in &mut m.parameters {
                resolve_type_annotation(&mut p.ty);
                param_types.push(p.ty.resolved());
            }
            for rt in &mut m.return_types {
                resolve_type_annotation(rt);
            }
            let ret_type = if m.return_types.len() == 1 {
                m.return_types[0].resolved()
            } else {
                types::t_void()
            };
            methods.insert(
                m.name.clone(),
                Rc::new(types::TraitMethodInfo {
                    signature: types::function_type(param_types, Some(ret_type)),
                    is_pub: true,
                    is_mut: m.is_mut,
                }),
            );
        }
        let trait_ty = types::trait_type(&td.name, methods);
        self.trait_types.insert(td.name.clone(), trait_ty.clone());
        self.scope.declare(Rc::new(Symbol {
            name: td.name.clone(),
            kind: SymbolKind::Trait,
            ty: Some(trait_ty),
            module_name: td.name.clone(),
            ..base_symbol()
        }));
    }
}

/// Resolves a type annotation's builtin structure into a concrete type.
/// User-defined type names (Kind::Invalid + TypeName) are left for the checker.
pub fn resolve_type_annotation(ta: &mut TypeAnnotation) {
    match ta.kind {
        Kind::List => {
            if let Some(elem) = &mut ta.element {
                resolve_type_annotation(elem);
                let mut list_ty = types::list_of(elem.resolved());
                if ta.nullable {
                    list_ty = types::nullable_of(&list_ty);
                }
                ta.resolved = Some(list_ty);
            }
        }
        Kind::Stack => {
            if let Some(elem) = &mut ta.element {
                resolve_type_annotation(elem);
                let mut stack_ty = types::stack_of(elem.resolved());
                if ta.nullable {
                    stack_ty = types::nullable_of(&stack_ty);
                }
                ta.resolved = Some(stack_ty);
            }
        }
        Kind::Map => {
            if ta.key_type.is_some() && ta.value_type.is_some() {
                let key = ta.key_type.as_mut().unwrap();
                resolve_type_annotation(key);
                let value = ta.value_type.as_mut().unwrap();
                resolve_type_annotation(value);
                let mut map_ty = types::map_of(key.resolved(), value.resolved());
                if ta.nullable {
                    map_ty = types::nullable_of(&map_ty);
                }
                ta.resolved = Some(map_ty);
            }
        }
        Kind::Invalid if !ta.type_name.is_empty() => {
            // Left unresolved for the checker.
        }
        _ => {
            ta.resolved = Some(kind_to_type(ta.kind, ta.nullable));
        }
    }
}

fn kind_to_type(kind: Kind, nullable: bool) -> Rc<Type> {
    let base = match kind {
        Kind::Bool => types::t_bool(),
        Kind::Byte => types::t_byte(),
        Kind::Int => types::t_int(),
        Kind::Float => types::t_float(),
        Kind::Char => types::t_char(),
        Kind::String => types::t_string(),
        Kind::Exception => types::t_exception(),
        Kind::Void => types::t_void(),
        Kind::Any => types::t_any(),
        _ => types::t_invalid(),
    };
    if nullable {
        types::nullable_of(&base)
    } else {
        base
    }
}
