//! Static type checking on the resolved AST.
//!
//! Port of internal/checker/checker.go.

use crate::ast::*;
use crate::diagnostic::{Diagnostics, CODE_CHECKER_STRUCT_POSITIONAL};
use crate::source::Span;
use crate::symbol::{Scope, Symbol, SymbolKind};
use crate::types::{self, Kind, StructFieldInfo, StructMethodInfo, TraitMethodInfo, Type};
use std::cell::Cell;
use std::collections::HashMap;
use std::rc::Rc;

/// Signature of a function declared in another module/file.
#[derive(Clone)]
pub struct ExternalFunc {
    pub params: Vec<Rc<Type>>,
    pub variadic: bool,
    pub ret: Rc<Type>,
}

/// A function signature: (params, variadic, return).
pub type Sig = (Vec<Rc<Type>>, bool, Rc<Type>);

fn builtin_funcs(name: &str) -> Option<Sig> {
    use types::*;
    let f = |params: Vec<Rc<Type>>, ret: Rc<Type>| Some((params, false, ret));
    let vf = |params: Vec<Rc<Type>>, ret: Rc<Type>| Some((params, true, ret));
    match name {
        "print" => f(vec![t_string()], t_void()),
        "println" => f(vec![t_string()], t_void()),
        "regex" => f(vec![t_invalid()], t_invalid()),
        "string" => f(vec![t_invalid()], t_string()),
        "int" => f(vec![t_invalid()], t_int()),
        "float" => f(vec![t_invalid()], t_float()),
        "byte" => f(vec![t_invalid()], t_byte()),
        "bool" => f(vec![t_invalid()], t_bool()),
        "typeOf" => f(vec![t_invalid()], t_string()),
        "isType" => f(vec![t_any(), t_string()], t_bool()),
        "PI" => f(vec![], t_float()),
        "E" => f(vec![], t_float()),
        "abs" => f(vec![t_float()], t_float()),
        "min" => f(vec![t_float(), t_float()], t_float()),
        "max" => f(vec![t_float(), t_float()], t_float()),
        "floor" => f(vec![t_float()], t_float()),
        "ceil" => f(vec![t_float()], t_float()),
        "round" => f(vec![t_float()], t_float()),
        "sqrt" => f(vec![t_float()], t_float()),
        "pow" => f(vec![t_float(), t_float()], t_float()),
        "sin" => f(vec![t_float()], t_float()),
        "cos" => f(vec![t_float()], t_float()),
        "tan" => f(vec![t_float()], t_float()),
        "env.get" => f(vec![t_string()], nullable_of(&t_string())),
        "env.set" => f(vec![t_string(), t_string()], t_void()),
        "env.keys" => f(vec![], list_of(t_string())),
        "file.read" => f(vec![t_string()], t_string()),
        "file.write" => f(vec![t_string(), t_string()], t_void()),
        "file.append" => f(vec![t_string(), t_string()], t_void()),
        "file.delete" => f(vec![t_string()], t_void()),
        "file.exists" => f(vec![t_string()], t_bool()),
        "file.temp" => f(vec![t_string()], t_string()),
        "file.tempDir" => f(vec![t_string()], t_string()),
        "process.run" => vf(vec![t_string(), t_string()], t_int()),
        "time.now" => f(vec![], t_int()),
        "time.sleep" => f(vec![t_int()], t_void()),
        "random.float" => f(vec![], t_float()),
        "random.int" => f(vec![t_int(), t_int()], t_int()),
        "random.range" => f(vec![t_int(), t_int()], t_int()),
        "random.uniform" => f(vec![t_float(), t_float()], t_float()),
        "random.choice" => f(vec![t_invalid()], t_any()),
        "random.shuffle" => f(vec![t_invalid()], t_any()),
        "random.sample" => f(vec![t_invalid(), t_int()], t_any()),
        "random.seed" => f(vec![t_int()], t_void()),
        "string.join" => f(vec![list_of(t_string()), t_string()], t_string()),
        "path.join" => vf(vec![t_string(), t_string()], t_string()),
        "path.basename" => f(vec![t_string()], t_string()),
        "path.dirname" => f(vec![t_string()], t_string()),
        "path.ext" => f(vec![t_string()], t_string()),
        "path.abs" => f(vec![t_string()], t_string()),
        "path.exists" => f(vec![t_string()], t_bool()),
        "base64.encode" => f(vec![t_string()], t_string()),
        "base64.decode" => f(vec![t_string()], t_string()),
        "hash.md5" => f(vec![t_string()], t_string()),
        "hash.sha1" => f(vec![t_string()], t_string()),
        "hash.sha256" => f(vec![t_string()], t_string()),
        "hash.sha512" => f(vec![t_string()], t_string()),
        "secrets.token" => f(vec![t_int()], t_string()),
        "secrets.hex" => f(vec![t_int()], t_string()),
        _ => None,
    }
}

/// Builtin method signatures. The first parameter is the implicit receiver.
fn builtin_methods(type_name: &str, member: &str) -> Option<Sig> {
    use types::*;
    let f = |params: Vec<Rc<Type>>, ret: Rc<Type>| Some((params, false, ret));
    match (type_name, member) {
        ("stack", "push") => f(vec![t_invalid(), t_invalid()], t_void()),
        ("stack", "pop") => f(vec![t_invalid()], t_any()),
        ("stack", "peek") => f(vec![t_invalid()], t_any()),
        ("stack", "len") => f(vec![t_invalid()], t_int()),
        ("stack", "isEmpty") => f(vec![t_invalid()], t_bool()),
        ("string", "len") => f(vec![t_string()], t_int()),
        ("string", "byteLength") => f(vec![t_string()], t_int()),
        ("string", "charAt") => f(vec![t_string(), t_int()], t_char()),
        ("string", "substring") => f(vec![t_string(), t_int(), t_int()], t_string()),
        ("string", "contains") => f(vec![t_string(), t_string()], t_bool()),
        ("string", "startsWith") => f(vec![t_string(), t_string()], t_bool()),
        ("string", "endsWith") => f(vec![t_string(), t_string()], t_bool()),
        ("string", "indexOf") => f(vec![t_string(), t_string()], t_int()),
        ("string", "toUpper") => f(vec![t_string()], t_string()),
        ("string", "toLower") => f(vec![t_string()], t_string()),
        ("string", "trim") => f(vec![t_string()], t_string()),
        ("string", "split") => f(vec![t_string(), t_string()], list_of(t_string())),
        ("map", "contains") => f(vec![t_invalid(), t_invalid()], t_bool()),
        ("map", "len") => f(vec![t_invalid()], t_int()),
        ("list", "len") => f(vec![t_invalid()], t_int()),
        _ => None,
    }
}

fn known_module_list() -> &'static [&'static str] {
    &[
        "core", "string", "math", "map", "env", "file", "process", "time",
        "random", "path", "base64", "hash", "secrets", "stack",
    ]
}

/// Identifies a function in the program: top-level or struct method.
#[derive(Clone, Copy, PartialEq)]
enum FuncRef {
    Top(usize),
    Method(usize, usize),
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

pub struct Checker {
    diags: Diagnostics,
    scope: Rc<Scope>,
    funcs: Vec<FuncRef>,
    func_map: HashMap<String, usize>,
    /// Signature snapshots parallel to `funcs`, refreshed when a function
    /// begins checking (mirrors Go's lazy annotation reads).
    func_sigs: Vec<Sig>,
    loop_depth: i32,
    module_name: String,
    narrowing_var: Option<String>,
    narrowed_type: Option<Rc<Type>>,
    all_funcs: Option<HashMap<String, ExternalFunc>>,
    /// Same-package types (including this file's own, pre-registered by the
    /// runtime): name -> shared type.
    package_types: HashMap<String, Rc<Type>>,
    skip_main_check: bool,
    current_func_idx: i32,
    current_return_types: Vec<Option<Rc<Type>>>,
    enum_types: HashMap<String, Rc<Type>>,
    struct_types: HashMap<String, Rc<Type>>,
    current_struct: Option<Rc<Type>>,
    current_method_mut: bool,
    trait_types: HashMap<String, Rc<Type>>,
}

impl Checker {
    pub fn new() -> Checker {
        Checker {
            diags: Diagnostics::new(),
            scope: Scope::new(None, None),
            funcs: Vec::new(),
            func_map: HashMap::new(),
            func_sigs: Vec::new(),
            loop_depth: 0,
            module_name: String::new(),
            narrowing_var: None,
            narrowed_type: None,
            all_funcs: None,
            package_types: HashMap::new(),
            skip_main_check: false,
            current_func_idx: -1,
            current_return_types: Vec::new(),
            enum_types: HashMap::new(),
            struct_types: HashMap::new(),
            current_struct: None,
            current_method_mut: false,
            trait_types: HashMap::new(),
        }
    }

    pub fn set_all_funcs(&mut self, funcs: HashMap<String, ExternalFunc>) {
        self.all_funcs = Some(funcs);
    }

    pub fn set_package_types(&mut self, types: HashMap<String, Rc<Type>>) {
        self.package_types = types;
    }

    pub fn set_skip_main_check(&mut self, skip: bool) {
        self.skip_main_check = skip;
    }

    fn func<'a>(&self, prog: &'a Program, r: FuncRef) -> &'a Function {
        match r {
            FuncRef::Top(i) => &prog.funcs[i],
            FuncRef::Method(s, m) => &prog.structs[s].methods[m],
        }
    }

    /// Computes the signature of a function from its current annotations.
    fn func_sig(&self, prog: &Program, r: FuncRef) -> Sig {
        let f = self.func(prog, r);
        let mut params = Vec::new();
        let mut variadic = false;
        for p in &f.parameters {
            params.push(p.ty.resolved());
            if p.variadic {
                variadic = true;
            }
        }
        let ret = if f.return_types.len() == 1 {
            f.return_types[0].resolved()
        } else {
            types::t_void()
        };
        (params, variadic, ret)
    }

    fn sig_type(sig: &Sig) -> Rc<Type> {
        types::function_type_with(sig.0.clone(), sig.1, sig.2.clone())
    }

    pub fn check(&mut self, prog: &mut Program) -> Diagnostics {
        // Enums (own)
        let mut enums = std::mem::take(&mut prog.enums);
        for en in &mut enums {
            self.check_enum_decl(en);
        }
        prog.enums = enums;

        // External (same-package) enums
        self.register_external_kinds(Kind::Enum, |c, name, ty| {
            c.enum_types.insert(name, ty);
        });

        // Traits (own)
        let mut traits = std::mem::take(&mut prog.traits);
        for td in &mut traits {
            self.check_trait_decl(td);
        }
        prog.traits = traits;

        // External traits
        self.register_external_kinds(Kind::Trait, |c, name, ty| {
            c.trait_types.insert(name, ty);
        });

        // Collect function declarations
        for i in 0..prog.funcs.len() {
            let name = prog.funcs[i].name.clone();
            if self.func_map.contains_key(&name) {
                let span = prog.funcs[i].span.clone();
                self.diags
                    .add_error("C090", &format!("duplicate function '{}'", name), span);
                continue;
            }
            let idx = self.funcs.len();
            self.funcs.push(FuncRef::Top(i));
            if !prog.module.is_empty() {
                self.func_map.insert(format!("{}.{}", prog.module, name), idx);
            }
            self.func_map.insert(name, idx);
        }

        // Declare modules from allFuncs
        if let Some(all) = self.all_funcs.clone() {
            for mangled in all.keys() {
                if let Some(dot_idx) = mangled.rfind('.') {
                    let mod_name = &mangled[..dot_idx];
                    if self.scope.resolve(mod_name).is_none() {
                        self.scope.declare(Symbol::new_module(mod_name));
                    }
                }
            }
        }

        for m in known_module_list() {
            if self.scope.resolve(m).is_none() {
                self.scope.declare(Symbol::new_module(m));
            }
        }

        // Structs (own)
        let mut structs = std::mem::take(&mut prog.structs);
        for si in 0..structs.len() {
            self.check_struct_decl(&mut structs, si);
        }
        prog.structs = structs;

        // External structs
        self.register_external_kinds(Kind::Struct, |c, name, ty| {
            c.struct_types.insert(name, ty);
        });

        self.module_name = prog.module.clone();

        // Snapshot top-level function signatures (pre-check state)
        self.func_sigs = self
            .funcs
            .iter()
            .map(|&r| self.func_sig(prog, r))
            .collect();

        // Check each top-level function
        for i in 0..prog.funcs.len() {
            self.check_function(prog, FuncRef::Top(i), i);
        }

        // Check struct methods
        let method_targets: Vec<(usize, FuncRef)> = self
            .funcs
            .iter()
            .enumerate()
            .filter(|(_, r)| matches!(r, FuncRef::Method(_, _)))
            .map(|(i, &r)| (i, r))
            .collect();
        for (idx, r) in method_targets {
            self.check_function(prog, r, idx);
        }

        self.check_main(prog);

        std::mem::take(&mut self.diags)
    }

    fn register_external_kinds(&mut self, kind: Kind, mut insert: impl FnMut(&mut Checker, String, Rc<Type>)) {
        let names: Vec<String> = self.package_types.keys().cloned().collect();
        for name in names {
            if let Some(ty) = self.package_types.get(&name) {
                if ty.kind == kind && self.scope.resolve(&name).is_none() {
                    self.scope.declare(Rc::new(Symbol {
                        name: name.clone(),
                        kind: match kind {
                            Kind::Enum => SymbolKind::Module,
                            Kind::Trait => SymbolKind::Trait,
                            _ => SymbolKind::Struct,
                        },
                        ty: Some(ty.clone()),
                        module_name: name.clone(),
                        ..base_symbol()
                    }));
                    insert(self, name, ty.clone());
                }
            }
        }
    }

    fn check_enum_decl(&mut self, en: &mut EnumDecl) {
        let mut used_values: HashMap<i64, bool> = HashMap::new();
        let mut used_names: HashMap<String, bool> = HashMap::new();
        let mut next_val: i64 = 0;
        let mut enum_values: HashMap<String, i64> = HashMap::new();

        for v in &mut en.variants {
            if used_names.contains_key(&v.name) {
                self.diags.add_error(
                    "C046",
                    &format!("duplicate variant name '{}' in enum '{}'", v.name, en.name),
                    v.span.clone(),
                );
                continue;
            }
            used_names.insert(v.name.clone(), true);

            let val = match v.value {
                Some(x) => {
                    next_val = x + 1;
                    x
                }
                None => {
                    let x = next_val;
                    next_val += 1;
                    x
                }
            };

            if used_values.contains_key(&val) {
                self.diags.add_error(
                    "C047",
                    &format!("duplicate value {} in enum '{}'", val, en.name),
                    v.span.clone(),
                );
            }
            used_values.insert(val, true);
            v.resolved_int = val;
            enum_values.insert(v.name.clone(), val);
        }

        let enum_ty = match self.package_types.get(&en.name) {
            Some(t) if t.kind == Kind::Enum => t.clone(),
            _ => types::enum_type(&en.name, enum_values),
        };
        self.enum_types.insert(en.name.clone(), enum_ty.clone());

        self.scope.declare(Rc::new(Symbol {
            name: en.name.clone(),
            kind: SymbolKind::Module,
            ty: Some(enum_ty),
            module_name: en.name.clone(),
            ..base_symbol()
        }));
    }

    fn check_trait_decl(&mut self, td: &mut TraitDecl) {
        let mut methods = HashMap::new();
        for m in &mut td.methods {
            let mut param_types = Vec::new();
            for p in &mut m.parameters {
                self.resolve_enum_type_annotation(&mut p.ty);
                param_types.push(p.ty.resolved());
            }
            for rt in &mut m.return_types {
                self.resolve_enum_type_annotation(rt);
            }
            let ret_type = if m.return_types.len() == 1 {
                m.return_types[0].resolved()
            } else {
                types::t_void()
            };
            methods.insert(
                m.name.clone(),
                Rc::new(TraitMethodInfo {
                    signature: types::function_type(param_types, Some(ret_type)),
                    is_pub: true,
                    is_mut: m.is_mut,
                }),
            );
        }

        let trait_ty = match self.package_types.get(&td.name) {
            Some(t) if t.kind == Kind::Trait => t.clone(),
            _ => types::trait_type(&td.name, methods),
        };
        self.trait_types.insert(td.name.clone(), trait_ty.clone());

        self.scope.declare(Rc::new(Symbol {
            name: td.name.clone(),
            kind: SymbolKind::Trait,
            ty: Some(trait_ty),
            module_name: td.name.clone(),
            ..base_symbol()
        }));
    }

    fn check_struct_decl(&mut self, structs: &mut [StructDecl], si: usize) {
        let struct_name = structs[si].name.clone();
        let mut fields: Vec<StructFieldInfo> = Vec::new();
        let mut seen_fields: HashMap<String, bool> = HashMap::new();

        for f in &mut structs[si].fields {
            if seen_fields.contains_key(&f.name) {
                self.diags.add_error(
                    "C091",
                    &format!("duplicate field '{}' in struct '{}'", f.name, struct_name),
                    f.span.clone(),
                );
                continue;
            }
            seen_fields.insert(f.name.clone(), true);
            self.resolve_enum_type_annotation(&mut f.ty);
            let ft = f.ty.resolved();
            fields.push(StructFieldInfo {
                name: f.name.clone(),
                ty: ft,
                is_mut: f.is_mut,
                is_pub: f.is_pub,
            });
        }

        let struct_ty = match self.package_types.get(&struct_name) {
            Some(t) if t.kind == Kind::Struct => t.clone(),
            _ => types::struct_type(&struct_name, fields),
        };
        self.struct_types.insert(struct_name.clone(), struct_ty.clone());

        self.scope.declare(Rc::new(Symbol {
            name: struct_name.clone(),
            kind: SymbolKind::Struct,
            ty: Some(struct_ty.clone()),
            module_name: struct_name.clone(),
            ..base_symbol()
        }));

        // Add methods to the checker's function list and register in struct type
        for mi in 0..structs[si].methods.len() {
            let idx = self.funcs.len();
            self.funcs.push(FuncRef::Method(si, mi));
            let mname = structs[si].methods[mi].name.clone();
            self.func_map.insert(mname, idx);

            let m = &mut structs[si].methods[mi];
            if !m.parameters.is_empty() && m.parameters[0].name == "_self" {
                m.parameters[0].ty.resolved = Some(struct_ty.clone());
            }
            for p in &mut m.parameters {
                if p.name != "_self" {
                    self.resolve_enum_type_annotation(&mut p.ty);
                }
            }
            for rt in &mut m.return_types {
                self.resolve_enum_type_annotation(rt);
            }

            let mut param_types = Vec::new();
            for p in &m.parameters {
                param_types.push(p.ty.resolved());
            }
            let ret_type = if m.return_types.len() == 1 {
                m.return_types[0].resolved()
            } else {
                types::t_void()
            };
            let method_type = types::function_type(param_types, Some(ret_type));
            let short_name = m.name[struct_name.len() + 1..].to_string();
            struct_ty.struct_methods.borrow_mut().insert(
                short_name,
                Rc::new(StructMethodInfo {
                    func_index: idx as i32,
                    signature: method_type,
                    is_pub: m.is_pub,
                    is_mut: m.is_mut,
                }),
            );
        }
    }

    fn resolve_enum_type_annotation(&self, ta: &mut TypeAnnotation) {
        if ta.kind == Kind::Invalid && !ta.type_name.is_empty() {
            if let Some(sym) = self.scope.resolve(&ta.type_name) {
                if let Some(t) = &sym.ty {
                    if matches!(t.kind, Kind::Enum | Kind::Struct | Kind::Trait) {
                        if ta.nullable {
                            ta.resolved = Some(types::nullable_of(t));
                        } else {
                            ta.resolved = Some(t.clone());
                        }
                    }
                }
            }
        } else if ta.kind == Kind::List {
            if let Some(elem) = &mut ta.element {
                self.resolve_enum_type_annotation(elem);
                if elem.resolved.is_some() {
                    let mut list_ty = types::list_of(elem.resolved());
                    if ta.nullable {
                        list_ty = types::nullable_of(&list_ty);
                    }
                    ta.resolved = Some(list_ty);
                }
            }
        } else if ta.kind == Kind::Stack {
            if let Some(elem) = &mut ta.element {
                self.resolve_enum_type_annotation(elem);
                if elem.resolved.is_some() {
                    let mut stack_ty = types::stack_of(elem.resolved());
                    if ta.nullable {
                        stack_ty = types::nullable_of(&stack_ty);
                    }
                    ta.resolved = Some(stack_ty);
                }
            }
        } else if ta.kind == Kind::Map {
            if let Some(key) = &mut ta.key_type {
                self.resolve_enum_type_annotation(key);
            }
            if let Some(value) = &mut ta.value_type {
                self.resolve_enum_type_annotation(value);
            }
            if let (Some(key), Some(value)) = (&ta.key_type, &ta.value_type) {
                if key.resolved.is_some() && value.resolved.is_some() {
                    let mut map_ty = types::map_of(key.resolved(), value.resolved());
                    if ta.nullable {
                        map_ty = types::nullable_of(&map_ty);
                    }
                    ta.resolved = Some(map_ty);
                }
            }
        }
    }

    fn check_function(&mut self, prog: &mut Program, r: FuncRef, func_idx: usize) {
        // Resolve any enum type annotations in parameters and returns.
        {
            let f = match r {
                FuncRef::Top(i) => &mut prog.funcs[i],
                FuncRef::Method(s, m) => &mut prog.structs[s].methods[m],
            };
            for p in &mut f.parameters {
                self.resolve_enum_type_annotation(&mut p.ty);
            }
            for rt in &mut f.return_types {
                self.resolve_enum_type_annotation(rt);
            }
        }

        // Snapshot the return types the way checkReturnStmt would see them:
        // Go reads c.funcs[c.currentFuncIdx].ReturnTypes, which for duplicate
        // (unregistered) functions may be a different function or nothing.
        let ctx_ref = if func_idx < self.funcs.len() {
            Some(self.funcs[func_idx])
        } else {
            None
        };
        self.current_return_types = match ctx_ref {
            Some(r2) => {
                let f2 = self.func(prog, r2);
                f2.return_types.iter().map(|rt| rt.resolved.clone()).collect()
            }
            None => Vec::new(),
        };

        let (params, rets, struct_name, is_mut, body_span, function_name) = {
            let f = match r {
                FuncRef::Top(i) => &mut prog.funcs[i],
                FuncRef::Method(s, m) => &mut prog.structs[s].methods[m],
            };
            (
                std::mem::take(&mut f.parameters),
                std::mem::take(&mut f.return_types),
                f.struct_name.clone(),
                f.is_mut,
                f.span.clone(),
                f.name.clone(),
            )
        };

        let mut param_types: Vec<Rc<Type>> = Vec::new();
        let mut last_param_variadic = false;
        for p in &params {
            param_types.push(p.ty.resolved());
            if p.variadic {
                last_param_variadic = true;
            }
        }

        let ret_type = if rets.len() == 1 {
            rets[0].resolved()
        } else {
            types::t_void()
        };
        // Go falls back to the function being checked when a duplicate
        // declaration means there is no registered function at func_idx.
        if self.current_return_types.is_empty() && !ret_type.is_void() {
            self.current_return_types = vec![Some(ret_type.clone())];
        }

        // Refresh the signature snapshot now that annotations are resolved.
        if func_idx < self.func_sigs.len() {
            self.func_sigs[func_idx] = (param_types.clone(), last_param_variadic, ret_type.clone());
        }

        let func_type = if last_param_variadic {
            types::variadic_function_type(param_types.clone(), Some(ret_type.clone()))
        } else {
            types::function_type(param_types.clone(), Some(ret_type.clone()))
        };

        // Enter function scope
        let old_scope = self.scope.clone();
        self.scope = Scope::new(Some(old_scope.clone()), Some(func_type));

        let mut slot = 0i32;
        let mut seen_params: HashMap<String, bool> = HashMap::new();
        for p in &params {
            if seen_params.contains_key(&p.name) {
                self.diags.add_error(
                    "C092",
                    &format!("duplicate parameter '{}' in function '{}'", p.name, function_name),
                    p.span.clone(),
                );
                continue;
            }
            seen_params.insert(p.name.clone(), true);
            let mut t = p.ty.resolved();
            if p.variadic {
                t = types::list_of(t);
            }
            if !t.is_valid() {
                t = types::t_invalid();
            }
            let sym = Rc::new(Symbol {
                name: p.name.clone(),
                kind: SymbolKind::Variable,
                ty: Some(t),
                slot,
                parameter: true,
                ..base_symbol()
            });
            sym.defined.set(true);
            self.scope.declare(sym);
            slot += 1;
        }

        self.current_func_idx = func_idx as i32;

        // Struct method receivers
        let old_struct = self.current_struct.clone();
        self.current_struct = None;
        if !struct_name.is_empty() {
            if let Some(struct_ty) = self.struct_types.get(&struct_name).cloned() {
                self.current_struct = Some(struct_ty.clone());
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

        // Check body
        let old_method_mut = self.current_method_mut;
        self.current_method_mut = !struct_name.is_empty() && is_mut;

        let mut if_val = false;
        {
            let f = match r {
                FuncRef::Top(i) => &mut prog.funcs[i],
                FuncRef::Method(s, m) => &mut prog.structs[s].methods[m],
            };
            f.parameters = params;
            f.return_types = rets;
            if let Some(body) = &mut f.body {
                if_val = self.check_block(body, &ret_type, true);
            }
        }

        self.current_method_mut = old_method_mut;
        self.current_struct = old_struct;

        // Missing-return check
        let ret_count = self.current_return_types.len();
        if ret_count > 0 && !if_val {
            if ret_count == 1 {
                self.diags.add_error(
                    "C001",
                    &format!(
                        "missing return in function '{}' returning {}",
                        function_name,
                        ret_type.named()
                    ),
                    body_span,
                );
            } else {
                self.diags.add_error(
                    "C001",
                    &format!(
                        "missing return in function '{}' returning {} values",
                        function_name, ret_count
                    ),
                    body_span,
                );
            }
        }

        self.current_func_idx = -1;
        self.current_return_types = Vec::new();
        self.scope = old_scope;
    }

    fn check_block(&mut self, block: &mut Block, ret_type: &Rc<Type>, create_scope: bool) -> bool {
        let old_scope = if create_scope {
            let saved = self.scope.clone();
            let ft = self.scope.func_type.clone();
            self.scope = Scope::new(Some(saved.clone()), ft);
            Some(saved)
        } else {
            None
        };

        let mut all_return = false;
        for stmt in &mut block.statements {
            if all_return {
                self.diags.add_warning("C002", "unreachable statement", stmt.span.clone());
                break;
            }
            if self.check_statement(stmt, ret_type) {
                all_return = true;
            }
        }

        if let Some(saved) = old_scope {
            self.scope = saved;
        }
        all_return
    }

    fn check_statement(&mut self, stmt: &mut StmtNode, ret_type: &Rc<Type>) -> bool {
        let span = stmt.span.clone();
        match &mut stmt.kind {
            StmtKind::VarDecl(d) => self.check_var_decl(d, &span),
            StmtKind::Expr(e) => {
                if let ExprKind::Binary { op: BinOp::Assign, .. } = &e.kind {
                    return self.check_assignment(e);
                }
                self.check_expr(e, None);
                false
            }
            StmtKind::Block(b) => self.check_block(b, ret_type, true),
            StmtKind::If(s) => self.check_if_stmt(s, ret_type),
            StmtKind::While(s) => self.check_while_stmt(s, ret_type),
            StmtKind::For(s) => self.check_for_stmt(s, ret_type),
            StmtKind::Try(s) => self.check_try_stmt(s, ret_type),
            StmtKind::Throw(s) => self.check_throw_stmt(s),
            StmtKind::Switch(s) => self.check_switch_stmt(s, ret_type),
            StmtKind::Return(values) => self.check_return_stmt(values, &span),
            StmtKind::Break => self.loop_depth > 0,
            StmtKind::Continue => self.loop_depth > 0,
        }
    }

    fn check_var_decl(&mut self, decl: &mut VarDecl, span: &Span) -> bool {
        self.resolve_enum_type_annotation(&mut decl.ty);

        let declared_type = decl.ty.resolved();

        let mut init_type: Option<Rc<Type>> = None;
        if let Some(init) = &mut decl.init {
            init_type = self.check_expr(init, Some(&declared_type));
        }

        if declared_type.is_valid() {
            if let Some(init_type) = &init_type {
                if init_type.is_null()
                    && declared_type.is_reference_type()
                    && !declared_type.is_nullable()
                {
                    self.diags.add_error(
                        "C032",
                        &format!("cannot assign null to non-nullable type {}", declared_type.named()),
                        span.clone(),
                    );
                } else if init_type.is_valid() && !declared_type.is_assignable_from(init_type) {
                    self.diags.add_error(
                        "C003",
                        &format!(
                            "cannot assign {} to {} in variable declaration",
                            init_type.named(),
                            declared_type.named()
                        ),
                        span.clone(),
                    );
                }
            }
        }

        let sym = Rc::new(Symbol {
            name: decl.name.clone(),
            kind: SymbolKind::Variable,
            ty: Some(declared_type),
            slot: -1,
            defined: Cell::new(decl.init.is_some()),
            mut_flag: decl.is_mut,
            ..base_symbol()
        });
        self.scope.declare(sym);
        false
    }

    fn check_assignment(&mut self, be: &mut ExprNode) -> bool {
        let expected = if let ExprKind::Binary { left, .. } = &be.kind {
            if let ExprKind::Ident(name) = &left.kind {
                self.scope.resolve(name).and_then(|s| s.ty.clone())
            } else {
                None
            }
        } else {
            None
        };

        let val_type = {
            let ExprKind::Binary { right, .. } = &mut be.kind else { unreachable!() };
            match self.check_expr(right, expected.as_ref()) {
                Some(t) if t.is_valid() => t,
                _ => return false,
            }
        };

        let span = be.span.clone();
        let ExprKind::Binary { left, .. } = &mut be.kind else { unreachable!() };
        match &mut left.kind {
            ExprKind::Ident(name) => {
                let name = name.clone();
                self.check_ident_assignment(&name, &val_type, left.span.clone(), &span)
            }
            ExprKind::Index { .. } => self.check_index_assignment(left, &val_type, &span),
            ExprKind::Member { .. } => self.check_field_assignment(left, &val_type, &span),
            _ => {
                self.diags.add_error(
                    "C004",
                    "left side of assignment must be an identifier or index expression",
                    left.span.clone(),
                );
                false
            }
        }
    }

    fn current_struct_name(&self) -> String {
        if let Some(cs) = &self.current_struct {
            if !cs.struct_name.is_empty() {
                return cs.struct_name.clone();
            }
        }
        "<receiver>".to_string()
    }

    fn check_ident_assignment(&mut self, name: &str, val_type: &Rc<Type>, ident_span: Span, span: &Span) -> bool {
        let sym = match self.scope.resolve(name) {
            Some(s) => s,
            None => {
                self.diags
                    .add_error("C005", &format!("undeclared variable: {}", name), ident_span);
                return false;
            }
        };

        if sym.is_struct_field && !self.current_method_mut {
            self.diags.add_error(
                "C068",
                &format!(
                    "method '{}' is not mutating and cannot assign receiver field '{}'; declare it as 'mut func'",
                    self.current_struct_name(),
                    sym.name
                ),
                span.clone(),
            );
            return false;
        }
        if sym.is_struct_field && !sym.mut_flag {
            self.diags.add_error(
                "C045",
                &format!("cannot assign to immutable field '{}'; consider adding 'mut'", sym.name),
                span.clone(),
            );
            return false;
        }
        if !sym.is_struct_field && !sym.mut_flag {
            self.diags.add_error(
                "C045",
                &format!("cannot assign to immutable variable '{}'; consider adding 'mut'", sym.name),
                span.clone(),
            );
            return false;
        }

        if let Some(sym_ty) = &sym.ty {
            if sym_ty.is_valid() {
                if val_type.is_null() && sym_ty.is_reference_type() && !sym_ty.is_nullable() {
                    self.diags.add_error(
                        "C033",
                        &format!("cannot assign null to non-nullable type {}", sym_ty.named()),
                        span.clone(),
                    );
                }
                if !sym_ty.is_assignable_from(val_type) {
                    self.diags.add_error(
                        "C006",
                        &format!("cannot assign {} to {}", val_type.named(), sym_ty.named()),
                        span.clone(),
                    );
                }
            }
        }

        sym.defined.set(true);
        false
    }

    fn check_index_assignment(&mut self, index_expr: &mut ExprNode, val_type: &Rc<Type>, span: &Span) -> bool {
        let (target, index) = match &mut index_expr.kind {
            ExprKind::Index { target, index } => (target, index),
            _ => unreachable!(),
        };
        let target_type = self.check_expr(target, None);
        let index_type = self.check_expr(index, None);

        let target_type = match target_type {
            Some(t) if t.is_valid() => t,
            _ => return false,
        };
        let index_type = match index_type {
            Some(t) if t.is_valid() => t,
            _ => return false,
        };

        if target_type.kind == Kind::List {
            if !index_type.is_integer() {
                self.diags.add_error("C025", "list index must be an integer", index.span.clone());
                return false;
            }
            if let Some(elem) = &target_type.element {
                if !elem.is_assignable_from(val_type) {
                    self.diags.add_error(
                        "C035",
                        &format!(
                            "cannot assign {} to list element of type {}",
                            val_type.named(),
                            elem.named()
                        ),
                        span.clone(),
                    );
                }
            }
            return false;
        }

        if target_type.kind == Kind::Map {
            if let Some(key_type) = &target_type.key_type {
                if !key_type.is_assignable_from(&index_type) {
                    self.diags.add_error(
                        "C036",
                        &format!(
                            "cannot use {} as map key of type {}",
                            index_type.named(),
                            key_type.named()
                        ),
                        index.span.clone(),
                    );
                }
            }
            if let Some(value_type) = &target_type.value_type {
                if !value_type.is_assignable_from(val_type) {
                    self.diags.add_error(
                        "C037",
                        &format!(
                            "cannot assign {} to map value of type {}",
                            val_type.named(),
                            value_type.named()
                        ),
                        span.clone(),
                    );
                }
            }
            return false;
        }

        self.diags.add_error(
            "C038",
            &format!("cannot index-assign to {}", target_type.named()),
            span.clone(),
        );
        false
    }

    fn check_field_assignment(&mut self, member: &mut ExprNode, val_type: &Rc<Type>, span: &Span) -> bool {
        let obj_type = {
            let ExprKind::Member { object, .. } = &mut member.kind else { unreachable!() };
            self.check_expr(object, None)
        };
        let obj_type = match obj_type {
            Some(t) if t.is_valid() && t.kind == Kind::Struct => t,
            _ => {
                self.diags.add_error("C069", "cannot assign to field of non-struct value", span.clone());
                return false;
            }
        };

        let member_name = {
            let ExprKind::Member { member, .. } = &member.kind else { unreachable!() };
            member.clone()
        };

        let mut field_type: Option<Rc<Type>> = None;
        let mut field_is_mut = false;
        let mut field_is_pub = false;
        let mut found = false;
        for field in &obj_type.struct_fields {
            if field.name == member_name {
                field_type = Some(field.ty.clone());
                field_is_mut = field.is_mut;
                field_is_pub = field.is_pub;
                found = true;
                break;
            }
        }

        if !found {
            self.diags.add_error(
                "C065",
                &format!("struct '{}' has no field '{}'", obj_type.struct_name, member_name),
                span.clone(),
            );
            return false;
        }

        let inside_same_struct = self
            .current_struct
            .as_ref()
            .map(|cs| cs.struct_name == obj_type.struct_name)
            .unwrap_or(false);
        if !inside_same_struct && !field_is_pub {
            self.diags.add_error(
                "C071",
                &format!("field '{}' of struct '{}' is private", member_name, obj_type.struct_name),
                span.clone(),
            );
            return false;
        }

        let is_self_receiver = {
            let ExprKind::Member { object, .. } = &member.kind else { unreachable!() };
            matches!(&object.kind, ExprKind::Ident(n) if n == "self")
        };
        if is_self_receiver && !self.current_method_mut {
            self.diags.add_error(
                "C068",
                &format!(
                    "method '{}' is not mutating and cannot assign receiver field '{}'; declare it as 'mut func'",
                    self.current_struct_name(),
                    member_name
                ),
                span.clone(),
            );
            return false;
        }
        if !field_is_mut {
            self.diags.add_error(
                "C070",
                &format!(
                    "cannot assign to immutable field '{}' of struct '{}'",
                    member_name, obj_type.struct_name
                ),
                span.clone(),
            );
            return false;
        }

        if let Some(ft) = &field_type {
            if ft.is_valid() && val_type.is_valid() && !ft.is_assignable_from(val_type) {
                self.diags.add_error(
                    "C077",
                    &format!(
                        "cannot assign {} to field '{}' of type {}",
                        val_type.named(),
                        member_name,
                        ft.named()
                    ),
                    span.clone(),
                );
            }
        }

        false
    }

    fn check_if_stmt(&mut self, stmt: &mut IfStmt, ret_type: &Rc<Type>) -> bool {
        let cond_type = self.check_expr(&mut stmt.condition, None);
        if let Some(ct) = &cond_type {
            if !ct.is_bool() {
                self.diags.add_error("C007", "if condition must be bool", stmt.condition.span.clone());
            }
        }

        // Null-comparison narrowing: if x != null
        let mut narrowed: Option<(String, Rc<Type>)> = None;
        if let ExprKind::Binary { op: BinOp::Ne, left, right } = &stmt.condition.kind {
            if matches!(right.kind, ExprKind::Null) {
                if let ExprKind::Ident(name) = &left.kind {
                    if let Some(sym) = self.scope.resolve(name) {
                        if let Some(sym_ty) = &sym.ty {
                            if sym_ty.is_nullable() {
                                narrowed = Some((name.clone(), sym_ty.without_nullable()));
                            }
                        }
                    }
                }
            }
        }
        if let Some((name, ty)) = narrowed {
            self.narrowing_var = Some(name);
            self.narrowed_type = Some(ty);
        }

        let then_returns = self.check_block(&mut stmt.then_block, ret_type, true);

        // Clear narrowing after the then branch
        let saved_var = self.narrowing_var.take();
        let saved_type = self.narrowed_type.take();

        let mut else_returns = false;
        if let Some(eb) = &mut stmt.else_block {
            else_returns = self.check_block(eb, ret_type, true);
        }

        // Restore narrowing for else-if chains (matches the Go flow)
        self.narrowing_var = saved_var;
        self.narrowed_type = saved_type;

        if then_returns && else_returns {
            let mut all_else_if_return = true;
            for ei in &mut stmt.else_ifs {
                if !self.check_if_stmt(ei, ret_type) {
                    all_else_if_return = false;
                }
            }
            if all_else_if_return {
                return true;
            }
        }
        false
    }

    fn check_while_stmt(&mut self, stmt: &mut WhileStmt, ret_type: &Rc<Type>) -> bool {
        let cond_type = self.check_expr(&mut stmt.condition, None);
        if let Some(ct) = &cond_type {
            if !ct.is_bool() {
                self.diags.add_error("C008", "while condition must be bool", stmt.condition.span.clone());
            }
        }

        self.loop_depth += 1;
        self.check_block(&mut stmt.body, ret_type, true);
        self.loop_depth -= 1;
        false
    }

    fn check_for_stmt(&mut self, stmt: &mut ForStmt, ret_type: &Rc<Type>) -> bool {
        let iter_type = self.check_expr(&mut stmt.iterable, None);

        let old_scope = self.scope.clone();
        let ft = self.scope.func_type.clone();
        self.scope = Scope::new(Some(old_scope.clone()), ft);

        let mut key_type: Option<Rc<Type>> = None;
        let mut val_type: Option<Rc<Type>> = None;

        let iter_ok = iter_type.as_ref().filter(|t| {
            matches!(t.kind, Kind::List | Kind::Stack | Kind::String | Kind::Map)
        });
        match &iter_ok {
            Some(it) => match it.kind {
                Kind::List | Kind::Stack => {
                    key_type = Some(types::t_int());
                    val_type = it.element.clone();
                }
                Kind::String => {
                    key_type = Some(types::t_int());
                    val_type = Some(types::t_char());
                }
                Kind::Map => {
                    key_type = it.key_type.clone();
                    val_type = it.value_type.clone();
                }
                _ => unreachable!(),
            },
            None => {
                self.diags
                    .add_error("C009", "for-in requires a List, string, or Map", stmt.iterable.span.clone());
            }
        }

        if stmt.value_variable.is_empty() {
            let var_type = if let Some(it) = &iter_type {
                if it.kind == Kind::Map {
                    key_type.clone()
                } else {
                    val_type.clone()
                }
            } else {
                None
            };
            let var_type = var_type.unwrap_or_else(types::t_invalid);
            let sym = Rc::new(Symbol {
                name: stmt.variable.clone(),
                kind: SymbolKind::Variable,
                ty: Some(var_type),
                slot: -1,
                ..base_symbol()
            });
            sym.defined.set(true);
            self.scope.declare(sym);
        } else {
            if iter_type.as_ref().map(|t| t.kind != Kind::Map).unwrap_or(true) {
                self.diags
                    .add_error("C039", "key, value unpacking requires a Map", stmt.iterable.span.clone());
            }
            let kt = key_type.unwrap_or_else(types::t_invalid);
            let vt = val_type.unwrap_or_else(types::t_invalid);
            let sym = Rc::new(Symbol {
                name: stmt.variable.clone(),
                kind: SymbolKind::Variable,
                ty: Some(kt),
                slot: -1,
                ..base_symbol()
            });
            sym.defined.set(true);
            self.scope.declare(sym);
            let sym = Rc::new(Symbol {
                name: stmt.value_variable.clone(),
                kind: SymbolKind::Variable,
                ty: Some(vt),
                slot: -1,
                ..base_symbol()
            });
            sym.defined.set(true);
            self.scope.declare(sym);
        }

        self.loop_depth += 1;
        self.check_block(&mut stmt.body, ret_type, false);
        self.loop_depth -= 1;

        self.scope = old_scope;
        false
    }

    fn check_switch_stmt(&mut self, stmt: &mut SwitchStmt, ret_type: &Rc<Type>) -> bool {
        let switch_type = self.check_expr(&mut stmt.expression, None);

        let mut all_return = true;
        for cse in &mut stmt.cases {
            let case_type = self.check_expr(&mut cse.expression, None);

            if let (Some(st), Some(ct)) = (&switch_type, &case_type) {
                if st.is_valid() && ct.is_valid() && (st.kind == Kind::Enum || ct.kind == Kind::Enum) {
                    if st.kind != Kind::Enum || ct.kind != Kind::Enum || st.enum_name != ct.enum_name {
                        self.diags.add_error(
                            "C080",
                            &format!("cannot compare {} and {} in switch", st.named(), ct.named()),
                            cse.expression.span.clone(),
                        );
                    }
                }
                if st.is_valid() {
                    if is_regex_case(&cse.expression) {
                        // pattern matching, not type equality
                    } else if ct.is_null() {
                        if !st.is_nullable() {
                            self.diags.add_error(
                                "C094",
                                &format!(
                                    "case null can never match switch of type {}; the switch type is not nullable",
                                    st.named()
                                ),
                                cse.expression.span.clone(),
                            );
                        }
                    } else if ct.is_valid()
                        && st.kind != Kind::Enum
                        && ct.kind != Kind::Enum
                        && !st.is_assignable_from(ct)
                    {
                        self.diags.add_error(
                            "C094",
                            &format!(
                                "cannot use case of type {} with switch of type {}",
                                ct.named(),
                                st.named()
                            ),
                            cse.expression.span.clone(),
                        );
                    }
                }
            }

            if !self.check_block(&mut cse.body, ret_type, true) {
                all_return = false;
            }
        }
        if let Some(db) = &mut stmt.default_block {
            if !self.check_block(db, ret_type, true) {
                all_return = false;
            }
        } else {
            all_return = false;
        }
        all_return
    }

    fn check_try_stmt(&mut self, stmt: &mut TryStmt, ret_type: &Rc<Type>) -> bool {
        let try_returns = self.check_block(&mut stmt.try_body, ret_type, true);

        let mut catch_returns = false;
        let has_catch = stmt.catch.is_some();
        if let Some(catch) = &mut stmt.catch {
            if catch.param_type.resolved.is_some() {
                let ptype = catch.param_type.resolved();
                if !ptype.is_exception() || ptype.is_nullable() {
                    self.diags
                        .add_error("C042", "catch parameter must have type exception", catch.span.clone());
                }
            }

            let old_scope = self.scope.clone();
            let ft = self.scope.func_type.clone();
            self.scope = Scope::new(Some(old_scope.clone()), ft);

            let param_type = if catch.param_type.resolved.is_some() {
                catch.param_type.resolved()
            } else {
                types::t_exception()
            };
            let sym = Rc::new(Symbol {
                name: catch.param_name.clone(),
                kind: SymbolKind::Variable,
                ty: Some(param_type),
                slot: -1,
                ..base_symbol()
            });
            sym.defined.set(true);
            self.scope.declare(sym);

            catch_returns = self.check_block(&mut catch.body, ret_type, false);

            self.scope = old_scope;
        }

        let mut finally_returns = false;
        if let Some(fb) = &mut stmt.finally_block {
            finally_returns = self.check_block(fb, ret_type, true);
        }

        if finally_returns {
            return true;
        }
        if !has_catch && try_returns {
            return true;
        }
        try_returns && catch_returns
    }

    fn check_throw_stmt(&mut self, stmt: &mut ExprNode) -> bool {
        let expr_type = self.check_expr(stmt, None);
        if let Some(et) = expr_type {
            if et.is_valid() {
                if !et.is_exception() && !et.is_string() {
                    self.diags.add_error(
                        "C043",
                        "throw expression must have type exception or string",
                        stmt.span.clone(),
                    );
                } else if et.is_nullable() {
                    self.diags
                        .add_error("C044", "cannot throw nullable exception", stmt.span.clone());
                }
            }
        }
        true
    }

    fn check_return_stmt(&mut self, values: &mut [ExprNode], span: &Span) -> bool {
        let expected_count = self.current_return_types.len();
        let val_count = values.len();

        if expected_count == 0 {
            if val_count > 0 {
                self.diags.add_error(
                    "C078",
                    &format!("function returns no values but return statement has {}", val_count),
                    span.clone(),
                );
            }
            return true;
        }

        if val_count == 0 {
            self.diags.add_error(
                "C011",
                "missing return value in function returning a value",
                span.clone(),
            );
            return true;
        }

        if val_count != expected_count {
            self.diags.add_error(
                "C010",
                &format!(
                    "function returns {} values but return statement has {}",
                    expected_count, val_count
                ),
                span.clone(),
            );
            return true;
        }

        for (i, val) in values.iter_mut().enumerate() {
            let val_type = self.check_expr(val, None);
            let val_type = match val_type {
                Some(t) if t.is_valid() => t,
                _ => continue,
            };
            if let Some(Some(expected_type)) = self.current_return_types.get(i) {
                if expected_type.is_valid() && !expected_type.is_assignable_from(&val_type) {
                    self.diags.add_error(
                        "C079",
                        &format!(
                            "cannot return {} as value {}: expected {}",
                            val_type.named(),
                            i + 1,
                            expected_type.named()
                        ),
                        val.span.clone(),
                    );
                }
            }
        }
        true
    }

    // ---- expressions ----

    fn check_expr(&mut self, expr: &mut ExprNode, expected: Option<&Rc<Type>>) -> Option<Rc<Type>> {
        let t = match &mut expr.kind {
            ExprKind::Int(_) => Some(types::t_int()),
            ExprKind::Float(_) => Some(types::t_float()),
            ExprKind::Bool(_) => Some(types::t_bool()),
            ExprKind::Char(_) => Some(types::t_char()),
            ExprKind::Str(_) => Some(types::t_string()),
            ExprKind::Byte(_) => Some(types::t_byte()),
            ExprKind::Null => Some(match expected {
                Some(e) if e.is_nullable() => types::nullable_of(e),
                _ => types::t_invalid(),
            }),
            ExprKind::Ident(name) => {
                let name = name.clone();
                let span = expr.span.clone();
                self.check_identifier(&name, &span)
            }
            ExprKind::Unary { .. } => self.check_unary(expr),
            ExprKind::Binary { .. } => self.check_binary(expr),
            ExprKind::Call { .. } => {
                let e = expected.cloned();
                self.check_call(expr, e.as_ref())
            }
            ExprKind::Index { .. } => self.check_index(expr),
            ExprKind::List(_) => {
                let e = expected.cloned();
                self.check_list_literal(expr, e.as_ref())
            }
            ExprKind::Map { .. } => {
                let e = expected.cloned();
                self.check_map_literal(expr, e.as_ref())
            }
            ExprKind::Member { .. } => self.check_member_expr(expr),
            ExprKind::StructLit { .. } => self.check_struct_literal(expr),
            ExprKind::NullCoalescing { .. } => self.check_null_coalescing(expr),
            ExprKind::Spread(inner) => self.check_expr(inner, None),
        };

        if let Some(t) = &t {
            expr.ty = Some(t.clone());
        }
        t
    }

    fn check_identifier(&mut self, name: &str, span: &Span) -> Option<Rc<Type>> {
        let sym = self.scope.resolve(name);
        let sym = match sym {
            Some(s) => s,
            None => {
                // Builtin function
                if let Some(sig) = builtin_funcs(name) {
                    return Some(Self::sig_type(&sig));
                }
                // User-defined function in this file
                if let Some(idx) = self.func_map.get(name) {
                    let sig = self.func_sigs[*idx].clone();
                    return Some(Self::sig_type(&sig));
                }
                // Function from the same module in another file
                if let Some(all) = &self.all_funcs {
                    for (mangled, f) in all.iter() {
                        if let Some(dot) = mangled.rfind('.') {
                            if &mangled[dot + 1..] == name && &mangled[..dot] == self.module_name {
                                let sig = (f.params.clone(), f.variadic, f.ret.clone());
                                return Some(Self::sig_type(&sig));
                            }
                        }
                    }
                }
                return Some(types::t_invalid());
            }
        };

        if !sym.parameter && !sym.defined.get() && !sym.is_struct_field {
            self.diags.add_error(
                "C085",
                &format!("variable '{}' may not have been assigned", sym.name),
                span.clone(),
            );
        }

        if sym.is_struct_field {
            return sym.ty.clone();
        }

        if sym.kind == SymbolKind::Module {
            // Prefer builtin function types for module names like "string"
            if let Some(sig) = builtin_funcs(&sym.name) {
                return Some(Self::sig_type(&sig));
            }
        }

        if let Some(nv) = &self.narrowing_var {
            if *nv == sym.name {
                if let Some(nt) = &self.narrowed_type {
                    return Some(nt.clone());
                }
            }
        }

        sym.ty.clone()
    }

    fn check_unary(&mut self, expr: &mut ExprNode) -> Option<Rc<Type>> {
        let ExprKind::Unary { op, operand } = &mut expr.kind else { unreachable!() };
        let operand_type = self.check_expr(operand, None)?;
        if !operand_type.is_valid() {
            return None;
        }

        match op {
            UnaryOp::Negate => {
                if !operand_type.is_numeric() {
                    self.diags
                        .add_error("C012", "cannot negate non-numeric type", expr.span.clone());
                    return Some(types::t_invalid());
                }
                Some(operand_type)
            }
            UnaryOp::Not => {
                if !operand_type.is_bool() {
                    self.diags
                        .add_error("C013", "cannot apply ! to non-bool type", expr.span.clone());
                    return Some(types::t_invalid());
                }
                Some(types::t_bool())
            }
            UnaryOp::BitNot => {
                if !operand_type.is_integer() {
                    self.diags
                        .add_error("C014", "cannot apply ~ to non-integer type", expr.span.clone());
                    return Some(types::t_invalid());
                }
                Some(operand_type)
            }
        }
    }

    fn check_binary(&mut self, expr: &mut ExprNode) -> Option<Rc<Type>> {
        let ExprKind::Binary { op, left, right } = &mut expr.kind else { unreachable!() };

        if *op == BinOp::Assign {
            self.check_assignment(expr);
            return Some(types::t_void());
        }

        let left_type = self.check_expr(left, None);
        let right_type = self.check_expr(right, None);

        if *op == BinOp::StrConcat {
            return Some(types::t_string());
        }

        let (lt, rt) = match (left_type, right_type) {
            (Some(l), Some(r)) => (l, r),
            _ => return None,
        };

        if !lt.is_valid() || !rt.is_valid() {
            if matches!(op, BinOp::Eq | BinOp::Ne) {
                return Some(types::t_bool());
            }
            return None;
        }

        match op {
            BinOp::Add | BinOp::Sub | BinOp::Mul | BinOp::Div | BinOp::Mod => {
                if !lt.is_numeric() || !rt.is_numeric() {
                    self.diags.add_error(
                        "C015",
                        &format!("cannot apply {} to {} and {}", op.as_str(), lt.named(), rt.named()),
                        expr.span.clone(),
                    );
                    return Some(types::t_invalid());
                }
                Some(types::common_numeric_type(&lt, &rt))
            }
            BinOp::Eq | BinOp::Ne => {
                if lt.kind == Kind::Enum && rt.kind == Kind::Enum {
                    if lt.enum_name != rt.enum_name {
                        self.diags.add_error(
                            "C016",
                            &format!("cannot compare {} and {} with ==", lt.named(), rt.named()),
                            expr.span.clone(),
                        );
                    }
                } else if lt.kind != rt.kind && lt.kind != Kind::Invalid && rt.kind != Kind::Invalid {
                    if lt.is_numeric() && rt.is_numeric() {
                        // numeric comparison across types is fine
                    } else {
                        self.diags.add_error(
                            "C016",
                            &format!("cannot compare {} and {} with ==", lt.named(), rt.named()),
                            expr.span.clone(),
                        );
                    }
                }
                Some(types::t_bool())
            }
            BinOp::Lt | BinOp::Le | BinOp::Gt | BinOp::Ge => {
                if !(lt.is_numeric() && rt.is_numeric()) && !(lt.is_char() && rt.is_char()) {
                    self.diags.add_error(
                        "C017",
                        &format!("cannot apply {} to {} and {}", op.as_str(), lt.named(), rt.named()),
                        expr.span.clone(),
                    );
                    return Some(types::t_invalid());
                }
                Some(types::t_bool())
            }
            BinOp::And | BinOp::Or => {
                if !lt.is_bool() || !rt.is_bool() {
                    self.diags.add_error(
                        "C018",
                        &format!("cannot apply {} to non-bool types", op.as_str()),
                        expr.span.clone(),
                    );
                    return Some(types::t_invalid());
                }
                Some(types::t_bool())
            }
            BinOp::BitAnd | BinOp::BitOr | BinOp::BitXor => {
                if !lt.is_integer() || !rt.is_integer() {
                    self.diags.add_error(
                        "C019",
                        &format!("cannot apply {} to non-integer types", op.as_str()),
                        expr.span.clone(),
                    );
                    return Some(types::t_invalid());
                }
                Some(types::common_numeric_type(&lt, &rt))
            }
            BinOp::ShiftLeft | BinOp::ShiftRight => {
                if !lt.is_integer() || !rt.is_integer() {
                    self.diags
                        .add_error("C020", "shift requires integer operands", expr.span.clone());
                    return Some(types::t_invalid());
                }
                Some(lt)
            }
            BinOp::StrConcat => Some(types::t_string()),
            BinOp::Assign => Some(types::t_void()),
        }
    }

    fn check_call(&mut self, expr: &mut ExprNode, expected: Option<&Rc<Type>>) -> Option<Rc<Type>> {
        {
        let ExprKind::Call { function, args } = &mut expr.kind else { unreachable!() };

        // int(enumValue) — explicit enum → int conversion
        if let ExprKind::Ident(fname) = &function.kind {
            if fname == "int" && args.len() == 1 {
                let arg_type = self.check_expr(&mut args[0], None);
                if let Some(at) = arg_type {
                    if at.is_valid() && at.kind == Kind::Enum {
                        return Some(types::t_int());
                    }
                }
            }
        }

        // Structs are constructed with named-field literals, not calls; also
        // handles unqualified method calls inside struct methods.
        if let ExprKind::Ident(fname) = &function.kind {
            if let Some(sym) = self.scope.resolve(fname) {
                if let Some(t) = &sym.ty {
                    if t.kind == Kind::Struct {
                        self.diags.add_error(
                            CODE_CHECKER_STRUCT_POSITIONAL,
                            &format!(
                                "struct '{}' requires named-field construction; use {} {{ field: value }}",
                                t.struct_name, t.struct_name
                            ),
                            expr.span.clone(),
                        );
                        return Some(types::t_invalid());
                    }
                }
            }
            // Unqualified method call inside a struct method (e.g. validate())
            if let Some(cs) = self.current_struct.clone() {
                let mi = cs.struct_methods.borrow().get(fname).cloned();
                if let Some(mi) = mi {
                    let fn_type = mi.signature.clone();
                    if mi.is_mut && !self.current_method_mut {
                        self.diags.add_error(
                            "C068",
                            &format!(
                                "non-mutating method cannot call mutating method '{}' on its receiver",
                                fname
                            ),
                            expr.span.clone(),
                        );
                    }
                    let param_count = fn_type.params.len().saturating_sub(1);
                    if args.len() != param_count {
                        self.diags.add_error(
                            "C023",
                            &format!("expected {} arguments but got {}", param_count, args.len()),
                            expr.span.clone(),
                        );
                    }
                    for i in 0..args.len() {
                        let expected_type = fn_type.params.get(i + 1).cloned();
                        let arg_type = self.check_expr(&mut args[i], expected_type.as_ref());
                        if let (Some(et), Some(at)) = (&expected_type, &arg_type) {
                            if et.is_valid() && at.is_valid() && !et.is_assignable_from(at) {
                                self.diags.add_error(
                                    "C024",
                                    &format!(
                                        "argument {}: expected {} but got {}",
                                        i + 1,
                                        et.named(),
                                        at.named()
                                    ),
                                    args[i].span.clone(),
                                );
                            }
                        }
                    }
                    return fn_type.ret.clone();
                }
            }
        }

        // stack() constructor
        if let ExprKind::Ident(fname) = &function.kind {
            if fname == "stack" {
                if !args.is_empty() {
                    self.diags
                        .add_error("C075", "stack() takes no arguments", expr.span.clone());
                    return Some(types::t_invalid());
                }
                if let Some(e) = expected {
                    if e.kind == Kind::Stack {
                        if let Some(elem) = &e.element {
                            return Some(types::stack_of(elem.clone()));
                        }
                    }
                }
                return Some(types::stack_of(types::t_any()));
            }
        }
        }

        // Method calls: p.move(10, 20), shape.draw(), s.push(10).
        // Extract the receiver type and member name in a scoped block so the
        // borrow of `expr.kind` is released before the helpers re-borrow it.
        let (member_obj_type, member_name) = {
            let ExprKind::Call { function, .. } = &mut expr.kind else { unreachable!() };
            if let ExprKind::Member { object, member } = &mut function.kind {
                let ot = self.check_expr(object, None);
                (ot, Some(member.clone()))
            } else {
                (None, None)
            }
        };

        if let (Some(ot), Some(member)) = (member_obj_type, member_name) {
            if ot.kind == Kind::Struct {
                return self.check_method_call(&ot, &member, expr);
            }
            if ot.kind == Kind::Trait {
                return self.check_trait_method_call(&ot, &member, expr);
            }
            let type_name = builtin_type_name(&ot);
            if !type_name.is_empty() {
                if let Some((params, _variadic, ret)) = builtin_methods(type_name, &member) {
                    let ExprKind::Call { args, .. } = &mut expr.kind else { unreachable!() };
                    let param_count = params.len().saturating_sub(1);
                    if args.len() != param_count {
                        self.diags.add_error(
                            "C023",
                            &format!("expected {} arguments but got {}", param_count, args.len()),
                            expr.span.clone(),
                        );
                        return Some(types::t_invalid());
                    }
                    for i in 0..args.len() {
                        let expected_type = params.get(i + 1).cloned();
                        let arg_type = self.check_expr(&mut args[i], expected_type.as_ref());
                        if let (Some(et), Some(at)) = (&expected_type, &arg_type) {
                            if et.is_valid() && at.is_valid() && !et.is_assignable_from(at) {
                                self.diags.add_error(
                                    "C024",
                                    &format!(
                                        "argument {}: expected {} but got {}",
                                        i + 1,
                                        et.named(),
                                        at.named()
                                    ),
                                    args[i].span.clone(),
                                );
                            }
                        }
                    }
                    return Some(ret);
                }
            }
        }

        let fn_type = {
            let ExprKind::Call { function, .. } = &mut expr.kind else { unreachable!() };
            self.check_expr(function, None)?
        };
        if !fn_type.is_valid() || fn_type.kind != Kind::Function {
            self.diags
                .add_error("C022", "called expression is not a function", expr.span.clone());
            return Some(types::t_invalid());
        }

        let is_variadic = fn_type.variadic;
        let mut fixed_count = fn_type.params.len();
        let variadic_elem_type = if is_variadic {
            fixed_count = fixed_count.saturating_sub(1);
            if fixed_count < fn_type.params.len() {
                fn_type.params[fixed_count].clone()
            } else {
                types::t_invalid()
            }
        } else {
            types::t_invalid()
        };

        {
        let ExprKind::Call { args, .. } = &mut expr.kind else { unreachable!() };
        if !is_variadic && args.len() != fn_type.params.len() {
            self.diags.add_error(
                "C023",
                &format!("expected {} arguments but got {}", fn_type.params.len(), args.len()),
                expr.span.clone(),
            );
            return Some(types::t_invalid());
        }
        if is_variadic && args.len() < fixed_count {
            self.diags.add_error(
                "C081",
                &format!("expected at least {} arguments but got {}", fixed_count, args.len()),
                expr.span.clone(),
            );
            return Some(types::t_invalid());
        }

        for i in 0..fixed_count.min(args.len()) {
            let expected_type = fn_type.params.get(i).cloned();
            let arg_type = self.check_expr(&mut args[i], expected_type.as_ref());
            if let (Some(et), Some(at)) = (&expected_type, &arg_type) {
                if at.is_valid() && et.is_valid() && !et.is_assignable_from(at) {
                    self.diags.add_error(
                        "C024",
                        &format!("argument {}: expected {} but got {}", i + 1, et.named(), at.named()),
                        args[i].span.clone(),
                    );
                }
            }
        }

        if is_variadic {
            for i in fixed_count..args.len() {
                if let ExprKind::Spread(inner) = &mut args[i].kind {
                    let spread_type = self.check_expr(inner, None);
                    match spread_type {
                        Some(st) if st.kind == Kind::List => {
                            if let Some(elem) = &st.element {
                                if !variadic_elem_type.is_assignable_from(elem) {
                                    self.diags.add_error(
                                        "C055",
                                        &format!("cannot spread {} into {}", st.named(), fn_type.named()),
                                        args[i].span.clone(),
                                    );
                                }
                            }
                        }
                        _ => {
                            self.diags
                                .add_error("C056", "spread expression must be a List", args[i].span.clone());
                        }
                    }
                } else {
                    let arg_type = self.check_expr(&mut args[i], Some(&variadic_elem_type));
                    if let Some(at) = &arg_type {
                        if at.is_valid() && variadic_elem_type.is_valid()
                            && !variadic_elem_type.is_assignable_from(at)
                        {
                            self.diags.add_error(
                                "C083",
                                &format!(
                                    "variadic argument {}: expected {} but got {}",
                                    i - fixed_count + 1,
                                    variadic_elem_type.named(),
                                    at.named()
                                ),
                                args[i].span.clone(),
                            );
                        }
                    }
                }
            }
        }
        }

        fn_type.ret.clone()
    }

    fn check_index(&mut self, expr: &mut ExprNode) -> Option<Rc<Type>> {
        let ExprKind::Index { target, index } = &mut expr.kind else { unreachable!() };
        let target_type = self.check_expr(target, None);
        let index_type = self.check_expr(index, None);

        let tt = target_type?;
        let it = index_type?;
        if !tt.is_valid() || !it.is_valid() {
            return Some(types::t_invalid());
        }

        if tt.kind == Kind::List {
            if !it.is_integer() {
                self.diags.add_error("C025", "list index must be an integer", index.span.clone());
                return Some(types::t_invalid());
            }
            return tt.element.clone().or(Some(types::t_invalid()));
        }

        if tt.kind == Kind::Map {
            return tt.value_type.clone();
        }

        if tt.is_string() {
            if !it.is_integer() {
                self.diags.add_error("C026", "string index must be an integer", index.span.clone());
                return Some(types::t_invalid());
            }
            return Some(types::t_char());
        }

        self.diags.add_error(
            "C027",
            &format!("cannot index {}", tt.named()),
            expr.span.clone(),
        );
        Some(types::t_invalid())
    }

    fn check_list_literal(&mut self, expr: &mut ExprNode, expected: Option<&Rc<Type>>) -> Option<Rc<Type>> {
        let ExprKind::List(elements) = &mut expr.kind else { unreachable!() };
        let mut elem_type: Option<Rc<Type>> = None;
        if let Some(e) = expected {
            if e.kind == Kind::List {
                elem_type = e.element.clone();
            }
        }

        for el in elements.iter_mut() {
            let t = self.check_expr(el, elem_type.as_ref());
            if let (Some(et), Some(t)) = (&elem_type, &t) {
                if t.is_valid() && !t.is_null() && !et.is_assignable_from(&t) {
                    self.diags.add_error(
                        "C082",
                        &format!("list element: expected {} but got {}", et.named(), t.named()),
                        el.span.clone(),
                    );
                }
            }
            if elem_type.is_none() {
                if let Some(t) = &t {
                    if t.is_valid() {
                        elem_type = Some(t.clone());
                    }
                }
            }
        }

        let elem_type = elem_type.unwrap_or_else(types::t_invalid);
        Some(types::list_of(elem_type))
    }

    fn check_map_literal(&mut self, expr: &mut ExprNode, expected: Option<&Rc<Type>>) -> Option<Rc<Type>> {
        let ExprKind::Map { keys, values } = &mut expr.kind else { unreachable!() };
        let mut key_type: Option<Rc<Type>> = None;
        let mut val_type: Option<Rc<Type>> = None;
        let mut from_expected = false;
        if let Some(e) = expected {
            if e.kind == Kind::Map {
                key_type = e.key_type.clone();
                val_type = e.value_type.clone();
                from_expected = key_type.is_some() && val_type.is_some();
            }
        }

        for i in 0..keys.len() {
            let kt = self.check_expr(&mut keys[i], key_type.as_ref());
            let vt = self.check_expr(&mut values[i], val_type.as_ref());

            if let Some(k) = &kt {
                if k.is_valid() && !k.is_valid_map_key() {
                    self.diags.add_error(
                        "C034",
                        &format!(
                            "invalid map key type: {} (allowed: bool, byte, int, char, string, enum)",
                            k.named()
                        ),
                        keys[i].span.clone(),
                    );
                }
            }

            if from_expected {
                if let (Some(k), Some(exp_k)) = (&kt, &key_type) {
                    if k.is_valid() && !k.is_null() && !exp_k.is_assignable_from(k) {
                        self.diags.add_error(
                            "C036",
                            &format!("cannot use {} as map key of type {}", k.named(), exp_k.named()),
                            keys[i].span.clone(),
                        );
                    }
                }
                if let (Some(v), Some(exp_v)) = (&vt, &val_type) {
                    if v.is_valid() && !v.is_null() && !exp_v.is_assignable_from(v) {
                        self.diags.add_error(
                            "C037",
                            &format!("cannot assign {} to map value of type {}", v.named(), exp_v.named()),
                            values[i].span.clone(),
                        );
                    }
                }
            }

            // Enum variant keys use the base enum type
            let kt = kt.map(|k| {
                if k.is_valid() && k.kind == Kind::Enum && !k.enum_variant.is_empty() {
                    let mut base = types::Type::prim(Kind::Enum);
                    base.enum_name = k.enum_name.clone();
                    base.enum_values = k.enum_values.clone();
                    Rc::new(base)
                } else {
                    k
                }
            });

            if key_type.is_none() {
                if let Some(k) = &kt {
                    if k.is_valid() {
                        key_type = Some(k.clone());
                    }
                }
            }
            if val_type.is_none() {
                if let Some(v) = &vt {
                    if v.is_valid() {
                        val_type = Some(v.clone());
                    }
                }
            }
        }

        let key_type = key_type.unwrap_or_else(types::t_invalid);
        let val_type = val_type.unwrap_or_else(types::t_invalid);
        Some(types::map_of(key_type, val_type))
    }

    fn check_struct_literal(&mut self, expr: &mut ExprNode) -> Option<Rc<Type>> {
        let ExprKind::StructLit { type_name, fields, values } = &mut expr.kind else { unreachable!() };

        let sym = self.scope.resolve(type_name);
        let struct_type = match sym {
            Some(s) => match &s.ty {
                Some(t) if t.kind == Kind::Struct => t.clone(),
                _ => {
                    self.diags
                        .add_error("C060", &format!("unknown struct type: {}", type_name), expr.span.clone());
                    return Some(types::t_invalid());
                }
            },
            None => {
                self.diags
                    .add_error("C060", &format!("unknown struct type: {}", type_name), expr.span.clone());
                return Some(types::t_invalid());
            }
        };

        let mut field_map: HashMap<String, usize> = HashMap::new();
        for (i, f) in struct_type.struct_fields.iter().enumerate() {
            field_map.insert(f.name.clone(), i);
        }

        let mut provided: HashMap<String, bool> = HashMap::new();
        for (i, name) in fields.iter().enumerate() {
            if !field_map.contains_key(name) {
                self.diags.add_error(
                    "C061",
                    &format!("struct '{}' has no field '{}'", type_name, name),
                    expr.span.clone(),
                );
                continue;
            }
            if provided.contains_key(name) {
                self.diags.add_error(
                    "C062",
                    &format!("duplicate field '{}' in struct literal", name),
                    expr.span.clone(),
                );
                continue;
            }
            provided.insert(name.clone(), true);

            let field_idx = field_map[name];
            let field_type = struct_type.struct_fields[field_idx].ty.clone();
            let val_type = self.check_expr(&mut values[i], Some(&field_type));

            if let Some(vt) = &val_type {
                if field_type.is_valid() && vt.is_valid() && !field_type.is_assignable_from(vt) {
                    self.diags.add_error(
                        "C077",
                        &format!(
                            "cannot assign {} to field '{}' of type {}",
                            vt.named(),
                            name,
                            field_type.named()
                        ),
                        values[i].span.clone(),
                    );
                }
            }
        }

        for name in field_map.keys() {
            if !provided.contains_key(name) {
                self.diags.add_error(
                    "C064",
                    &format!("missing field '{}' in struct literal", name),
                    expr.span.clone(),
                );
            }
        }

        Some(struct_type)
    }

    fn check_null_coalescing(&mut self, expr: &mut ExprNode) -> Option<Rc<Type>> {
        let ExprKind::NullCoalescing { left, right } = &mut expr.kind else { unreachable!() };

        let right_type = self.check_expr(right, None)?;
        match right_type.kind {
            Kind::Void | Kind::Function | Kind::Module => {
                self.diags.add_error(
                    "C028",
                    &format!("right operand of ?? must be a value, got {}", right_type.named()),
                    right.span.clone(),
                );
                return Some(types::t_invalid());
            }
            _ => {}
        }

        let left_type = self.check_expr(left, Some(&right_type))?;

        if left_type.is_null() {
            if is_regex_call(left) {
                self.diags.add_error("C028", "regex value cannot be used with ??", left.span.clone());
                return Some(types::t_invalid());
            }
            return Some(right_type);
        }
        if !left_type.is_valid() {
            return Some(types::t_invalid());
        }
        match left_type.kind {
            Kind::Void | Kind::Function | Kind::Module => {
                self.diags.add_error(
                    "C028",
                    &format!("left operand of ?? must be a value, got {}", left_type.named()),
                    left.span.clone(),
                );
                return Some(types::t_invalid());
            }
            _ => {}
        }

        if left_type.is_any() || right_type.is_any() {
            return Some(types::t_any());
        }
        let non_null_left = left_type.without_nullable();
        if right_type.is_assignable_from(&non_null_left) {
            return Some(non_null_left);
        }
        Some(right_type)
    }

    fn check_member_expr(&mut self, expr: &mut ExprNode) -> Option<Rc<Type>> {
        let ExprKind::Member { object, member } = &mut expr.kind else { unreachable!() };

        // Module access?
        let mut module_name = String::new();
        let mut is_module = false;
        if let ExprKind::Ident(name) = &object.kind {
            if let Some(sym) = self.scope.resolve(name) {
                if sym.kind == SymbolKind::Module {
                    is_module = true;
                    module_name = sym.module_name.clone();
                }
            }
            if !is_module && known_module_list().contains(&name.as_str()) {
                is_module = true;
                module_name = name.clone();
            }
        }

        if is_module {
            // Enum type reference (e.g., Color.Red)
            if let Some(sym) = self.scope.resolve(&module_name) {
                if let Some(t) = &sym.ty {
                    if t.kind == Kind::Enum {
                        if let Some(values) = &t.enum_values {
                            if values.contains_key(member.as_str()) {
                                return Some(types::enum_variant_type(t, member));
                            }
                        }
                        self.diags.add_error(
                            "C048",
                            &format!("enum '{}' has no variant '{}'", module_name, member),
                            expr.span.clone(),
                        );
                        return Some(types::t_invalid());
                    }
                }
            }

            let mangled = format!("{}.{}", module_name, member);

            if let Some(all) = self.all_funcs.clone() {
                if let Some(f) = all.get(&mangled) {
                    let sig = (f.params.clone(), f.variadic, f.ret.clone());
                    return Some(Self::sig_type(&sig));
                }
            }

            if let Some(idx) = self.func_map.get(&mangled) {
                let sig = self.func_sigs[*idx].clone();
                return Some(Self::sig_type(&sig));
            }

            if let Some(sig) = builtin_funcs(&mangled) {
                return Some(Self::sig_type(&sig));
            }
            if let Some(sig) = builtin_funcs(member) {
                return Some(Self::sig_type(&sig));
            }

            self.diags.add_error(
                "C041",
                &format!("module '{}' has no member '{}'", module_name, member),
                expr.span.clone(),
            );
            return Some(types::t_invalid());
        }

        let obj_type = self.check_expr(object, None)?;
        let obj_type = if obj_type.is_valid() { obj_type } else { return None };

        if obj_type.kind == Kind::Struct {
            let inside_same_struct = self
                .current_struct
                .as_ref()
                .map(|cs| cs.struct_name == obj_type.struct_name)
                .unwrap_or(false);
            for field in &obj_type.struct_fields {
                if field.name == *member {
                    if !inside_same_struct && !field.is_pub {
                        self.diags.add_error(
                            "C071",
                            &format!("field '{}' of struct '{}' is private", field.name, obj_type.struct_name),
                            expr.span.clone(),
                        );
                    }
                    return Some(field.ty.clone());
                }
            }
            let mi = obj_type.struct_methods.borrow().get(member).cloned();
            if let Some(mi) = mi {
                if !inside_same_struct && !mi.is_pub {
                    self.diags.add_error(
                        "C072",
                        &format!("method '{}' of struct '{}' is private", member, obj_type.struct_name),
                        expr.span.clone(),
                    );
                }
                return Some(mi.signature.clone());
            }
            self.diags.add_error(
                "C087",
                &format!("struct '{}' has no field or method '{}'", obj_type.struct_name, member),
                expr.span.clone(),
            );
            return Some(types::t_invalid());
        }

        if obj_type.kind == Kind::Trait {
            if let Some(mi) = obj_type.trait_methods.get(member.as_str()) {
                return Some(mi.signature.clone());
            }
            self.diags.add_error(
                "C073",
                &format!("trait '{}' has no method '{}'", obj_type.trait_name, member),
                expr.span.clone(),
            );
            return Some(types::t_invalid());
        }

        if obj_type.is_exception() {
            if member == "message" || member == "trace" {
                return Some(types::t_string());
            }
            self.diags.add_error(
                "C086",
                &format!("exception has no member '{}'", member),
                expr.span.clone(),
            );
            return Some(types::t_invalid());
        }

        self.diags.add_error(
            "C040",
            &format!("cannot access member '{}' of {}", member, obj_type.named()),
            expr.span.clone(),
        );
        Some(types::t_invalid())
    }

    fn check_method_call(&mut self, obj_type: &Rc<Type>, member: &str, expr: &mut ExprNode) -> Option<Rc<Type>> {
        let ExprKind::Call { function, args } = &mut expr.kind else { unreachable!() };
        let ExprKind::Member { object, .. } = &mut function.kind else { unreachable!() };

        let mi = obj_type.struct_methods.borrow().get(member).cloned();
        let mi = match mi {
            Some(m) => m,
            None => {
                self.diags.add_error(
                    "C088",
                    &format!("struct '{}' has no method '{}'", obj_type.struct_name, member),
                    expr.span.clone(),
                );
                return Some(types::t_invalid());
            }
        };

        let fn_type = mi.signature.clone();

        let inside_same_struct = self
            .current_struct
            .as_ref()
            .map(|cs| cs.struct_name == obj_type.struct_name)
            .unwrap_or(false);
        if !inside_same_struct && !mi.is_pub {
            self.diags.add_error(
                "C072",
                &format!("method '{}' of struct '{}' is private", member, obj_type.struct_name),
                expr.span.clone(),
            );
            return Some(types::t_invalid());
        }

        if mi.is_mut {
            if let ExprKind::Ident(name) = &object.kind {
                if name == "self" {
                    if !self.current_method_mut {
                        self.diags.add_error(
                            "C068",
                            &format!("cannot call mutating method '{}' from a non-mutating method", member),
                            expr.span.clone(),
                        );
                    }
                } else if let Some(sym) = self.scope.resolve(name) {
                    if !sym.mut_flag {
                        self.diags.add_error(
                            "C068",
                            &format!(
                                "cannot call mutating method '{}' on immutable struct variable '{}'; declare the variable as 'mut'",
                                member, name
                            ),
                            expr.span.clone(),
                        );
                    }
                }
            }
        }

        let param_count = fn_type.params.len().saturating_sub(1);
        if args.len() != param_count {
            self.diags.add_error(
                "C023",
                &format!("expected {} arguments but got {}", param_count, args.len()),
                expr.span.clone(),
            );
            return Some(types::t_invalid());
        }

        for i in 0..args.len() {
            let expected_type = fn_type.params.get(i + 1).cloned();
            let arg_type = self.check_expr(&mut args[i], expected_type.as_ref());
            if let (Some(et), Some(at)) = (&expected_type, &arg_type) {
                if et.is_valid() && at.is_valid() && !et.is_assignable_from(at) {
                    self.diags.add_error(
                        "C024",
                        &format!("argument {}: expected {} but got {}", i + 1, et.named(), at.named()),
                        args[i].span.clone(),
                    );
                }
            }
        }

        fn_type.ret.clone()
    }

    fn check_trait_method_call(&mut self, trait_type: &Rc<Type>, member: &str, expr: &mut ExprNode) -> Option<Rc<Type>> {
        let ExprKind::Call { function, args } = &mut expr.kind else { unreachable!() };
        let ExprKind::Member { object, .. } = &mut function.kind else { unreachable!() };

        let mi = match trait_type.trait_methods.get(member) {
            Some(m) => m.clone(),
            None => {
                self.diags.add_error(
                    "C073",
                    &format!("trait '{}' has no method '{}'", trait_type.trait_name, member),
                    expr.span.clone(),
                );
                return Some(types::t_invalid());
            }
        };

        let fn_type = mi.signature.clone();

        if mi.is_mut {
            if let ExprKind::Ident(name) = &object.kind {
                if name == "self" {
                    if !self.current_method_mut {
                        self.diags.add_error(
                            "C068",
                            &format!("cannot call mutating trait method '{}' from a non-mutating method", member),
                            expr.span.clone(),
                        );
                    }
                } else if let Some(sym) = self.scope.resolve(name) {
                    if !sym.mut_flag {
                        self.diags.add_error(
                            "C068",
                            &format!(
                                "cannot call mutating trait method '{}' on immutable receiver '{}'; declare the variable as 'mut'",
                                member, name
                            ),
                            expr.span.clone(),
                        );
                    }
                }
            }
        }

        if args.len() != fn_type.params.len() {
            self.diags.add_error(
                "C023",
                &format!("expected {} arguments but got {}", fn_type.params.len(), args.len()),
                expr.span.clone(),
            );
            return Some(types::t_invalid());
        }

        for i in 0..args.len() {
            let expected_type = fn_type.params.get(i).cloned();
            let arg_type = self.check_expr(&mut args[i], expected_type.as_ref());
            if let (Some(et), Some(at)) = (&expected_type, &arg_type) {
                if et.is_valid() && at.is_valid() && !et.is_assignable_from(at) {
                    self.diags.add_error(
                        "C024",
                        &format!("argument {}: expected {} but got {}", i + 1, et.named(), at.named()),
                        args[i].span.clone(),
                    );
                }
            }
        }

        fn_type.ret.clone()
    }

    fn check_main(&mut self, prog: &Program) {
        if self.skip_main_check {
            return;
        }
        let main_idx = match self.func_map.get("main") {
            Some(i) => *i,
            None => {
                self.diags.add_error("C029", "no main function found", prog.span.clone());
                return;
            }
        };
        let r = self.funcs[main_idx];
        let main_fn = self.func(prog, r);
        if !main_fn.parameters.is_empty() {
            self.diags
                .add_error("C030", "main function must not have parameters", main_fn.span.clone());
        }
        let return_count = main_fn.return_types.len();
        if return_count > 1 {
            self.diags.add_error(
                "C084",
                "main must return at most one value (int or void)",
                main_fn.span.clone(),
            );
        } else if return_count == 1 {
            let t = main_fn.return_types[0].resolved();
            if !t.is_void() && !t.equals(&types::t_int()) {
                self.diags
                    .add_error("C031", "main must return int or void", main_fn.span.clone());
            }
        }
    }
}

fn is_regex_case(expr: &ExprNode) -> bool {
    if let ExprKind::Call { function, .. } = &expr.kind {
        if let ExprKind::Ident(name) = &function.kind {
            return name == "regex";
        }
    }
    false
}

fn is_regex_call(expr: &ExprNode) -> bool {
    is_regex_case(expr)
}

fn builtin_type_name(t: &Type) -> &'static str {
    match t.kind {
        Kind::Stack => "stack",
        Kind::String => "string",
        Kind::Map => "map",
        Kind::List => "list",
        _ => "",
    }
}
