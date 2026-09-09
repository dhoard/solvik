//! Name resolution and declaration-level semantic analysis.
//!
//! Produces a `ResolvedProgram`: symbol tables, class hierarchy, field
//! layout (parent-first), vtable/interface dispatch metadata, override and
//! conformance validation, and entry-point selection. Expression-level type
//! checking happens later in `check.rs`.

//
// Data-structure fields retained for diagnostics/debugging/future use.
#![allow(dead_code)]
use crate::ast::*;
use crate::diagnostic::Diagnostics;
use crate::source::Span;
use crate::types::{BaseType, SubtypeOracle, Ty};
use std::collections::HashMap;

/// Reserved interface ids for compiler-owned nominal interfaces.
pub mod builtin {
    pub const WRITER: u32 = 0;
    pub const READER: u32 = 1;
    pub const RUNNABLE: u32 = 2;
    pub const EQUATABLE: u32 = 3;
    pub const HASHABLE: u32 = 4;
    pub const ITERABLE: u32 = 5;
    pub const COUNTABLE: u32 = 6;
    pub const STRINGABLE: u32 = 7;
    pub const COMPARABLE: u32 = 8;
    pub const BUILTIN_COUNT: usize = 9;
}

#[derive(Debug, Clone)]
pub struct FieldInfo {
    pub name: String,
    pub ty: Ty,
    pub mutable: bool,
    /// Public fields are accessible from any class.
    pub is_pub: bool,
    /// Class that declares the field (encapsulation owner).
    pub declaring: u32,
}

#[derive(Debug, Clone)]
pub struct ParamInfo {
    pub name: String,
    pub ty: Ty,
    pub default: Option<Expr>,
    pub variadic: bool,
}

#[derive(Debug, Clone)]
pub struct MethodInfo {
    pub name: String,
    pub visibility: Visibility,
    pub is_static: bool,
    pub is_override: bool,
    pub type_params: Vec<TypeParam>,
    pub params: Vec<ParamInfo>,
    pub return_ty: Option<Ty>,
    pub body: Option<Block>,
    pub span: Span,
}

impl MethodInfo {
    fn from_def(def: &MethodDef) -> MethodInfo {
        MethodInfo {
            name: def.name.clone(),
            visibility: def.visibility,
            is_static: def.is_static,
            is_override: def.is_override,
            type_params: def.type_params.clone(),
            params: def
                .params
                .iter()
                .map(|p| ParamInfo {
                    name: p.name.clone(),
                    ty: Ty::non_null(BaseType::Object), // filled during type pass
                    default: p.default.clone(),
                    variadic: p.variadic,
                })
                .collect(),
            return_ty: None, // filled during type pass
            body: def.body.clone(),
            span: def.span,
        }
    }
}

#[derive(Debug)]
pub struct ClassInfo {
    pub id: u32,
    pub name: String,
    pub type_params: Vec<TypeParam>,
    pub parent: Option<u32>,
    pub direct_interfaces: Vec<u32>,
    /// Transitive closure including parents' interfaces (deterministic order).
    pub all_interfaces: Vec<u32>,
    /// Parent-first field layout.
    pub fields: Vec<FieldInfo>,
    /// Own declared fields mapped to their slot.
    pub own_field_slots: HashMap<String, u16>,
    /// Vtable slot names for this class's hierarchy (parent-first).
    pub vtable_names: Vec<String>,
    pub methods: Vec<MethodInfo>,
    pub def: ClassDef,
}

impl ClassInfo {
    /// Slot index of an instance method name within this class's vtable.
    pub fn vtable_slot(&self, name: &str) -> Option<u16> {
        self.vtable_names
            .iter()
            .position(|n| n == name)
            .map(|i| i as u16)
    }

    /// Nearest method in the class chain (self first) with the given name.
    /// Returns (class_id, method_index).
    pub fn find_instance_method<'a>(
        &'a self,
        program: &'a ResolvedProgram,
        start_class: u32,
        name: &str,
    ) -> Option<(u32, usize)> {
        let mut c = Some(start_class);
        while let Some(cid) = c {
            let info = &program.classes[cid as usize];
            if let Some(idx) = info
                .methods
                .iter()
                .position(|m| m.name == name && !m.is_static)
            {
                return Some((cid, idx));
            }
            c = info.parent;
        }
        None
    }

    /// Nearest static method in the class chain.
    pub fn find_static_method<'a>(
        &'a self,
        program: &'a ResolvedProgram,
        start_class: u32,
        name: &str,
    ) -> Option<(u32, usize)> {
        let mut c = Some(start_class);
        while let Some(cid) = c {
            let info = &program.classes[cid as usize];
            if let Some(idx) = info
                .methods
                .iter()
                .position(|m| m.name == name && m.is_static)
            {
                return Some((cid, idx));
            }
            c = info.parent;
        }
        None
    }
}

#[derive(Debug, Clone)]
pub struct InterfaceSlot {
    pub name: String,
    /// (owning interface id, method index) of the default implementation, if any.
    pub default: Option<(u32, usize)>,
    /// (declaring interface id, method index) of the requirement.
    pub decl: Option<(u32, usize)>,
}

#[derive(Debug)]
pub struct InterfaceInfo {
    pub id: u32,
    pub name: String,
    pub type_params: Vec<TypeParam>,
    pub parents: Vec<u32>,
    pub all_parents: Vec<u32>,
    /// Ordered unique method slots (parents first, then own).
    pub slots: Vec<InterfaceSlot>,
    pub methods: Vec<MethodInfo>,
    pub def: InterfaceDef,
}

#[derive(Debug, Clone)]
pub struct EnumVariantInfo {
    pub name: String,
    pub payload: Option<Ty>,
}

#[derive(Debug)]
pub struct EnumInfo {
    pub id: u32,
    pub name: String,
    pub type_params: Vec<TypeParam>,
    pub variants: Vec<EnumVariantInfo>,
    pub def: EnumDef,
}

#[derive(Debug)]
pub struct ResolvedProgram {
    pub package: String,
    pub classes: Vec<ClassInfo>,
    pub interfaces: Vec<InterfaceInfo>,
    pub enums: Vec<EnumInfo>,
    /// Entry point: (class_id, method_index) of Main::run.
    pub entry: Option<(u32, usize)>,
}

impl ResolvedProgram {
    pub fn class_name(&self, id: u32) -> &str {
        &self.classes[id as usize].name
    }

    pub fn interface_name(&self, id: u32) -> &str {
        &self.interfaces[id as usize].name
    }

    pub fn enum_name(&self, id: u32) -> &str {
        &self.enums[id as usize].name
    }

    /// Display name for a base type (for diagnostics).
    pub fn type_name(&self, base: &BaseType) -> String {
        match base {
            BaseType::Class(id, args) => format!("{}{}", self.class_name(*id), args_str(args)),
            BaseType::Interface(id, args) => {
                format!("{}{}", self.interface_name(*id), args_str(args))
            }
            BaseType::Enum(id, args) => format!("{}{}", self.enum_name(*id), args_str(args)),
            other => other.to_string(),
        }
    }
}

fn args_str(args: &[BaseType]) -> String {
    if args.is_empty() {
        String::new()
    } else {
        format!(
            "<{}>",
            args.iter()
                .map(|a| a.to_string())
                .collect::<Vec<_>>()
                .join(", ")
        )
    }
}

impl crate::types::SubtypeOracle for ResolvedProgram {
    fn class_is_subclass_of(&self, child: u32, ancestor: u32) -> bool {
        let mut c = self.classes[child as usize].parent;
        while let Some(p) = c {
            if p == ancestor {
                return true;
            }
            c = self.classes[p as usize].parent;
        }
        false
    }

    fn class_implements_interface(&self, class: u32, interface: u32) -> bool {
        self.classes[class as usize]
            .all_interfaces
            .contains(&interface)
    }

    fn interface_extends(&self, child: u32, ancestor: u32) -> bool {
        if child == ancestor {
            return true;
        }
        self.interfaces[child as usize]
            .all_parents
            .contains(&ancestor)
    }
}

// ---------------------------------------------------------------------------
// Resolution driver
// ---------------------------------------------------------------------------

/// Context for resolving type references in declaration position.
struct TypeCtx<'a> {
    program: &'a ResolvedProgram,
    diags: &'a mut Diagnostics,
    /// Class being resolved (for `Self`).
    class_id: Option<u32>,
    /// Names of the enclosing declaration's type parameters.
    type_params: Vec<String>,
}

fn param_names(params: &[TypeParam]) -> Vec<String> {
    params.iter().map(|p| p.name.clone()).collect()
}

impl<'a> TypeCtx<'a> {
    fn resolve(&mut self, ty: &TypeRef) -> Option<BaseType> {
        let base = match &ty.base {
            TypeBase::Named(name) => self.resolve_named(name)?,
            TypeBase::Nullable(inner) => {
                // Nullable is represented via Ty.nullable; the parser also
                // sets the flag, so this arm is defensive.
                self.resolve(inner)?
            }
            TypeBase::Generic(name, args) => {
                let head = self.resolve_named(name)?;
                let expected = match &head {
                    BaseType::List(_) | BaseType::Stack(_) => Some(1),
                    BaseType::Map(_, _) => Some(2),
                    BaseType::Class(_, params)
                    | BaseType::Interface(_, params)
                    | BaseType::Enum(_, params) => Some(params.len()),
                    _ => None,
                };
                if let Some(expected) = expected {
                    if args.len() != expected {
                        self.diags.err_at(
                            "C102",
                            format!(
                                "generic type '{}' expects {} type arguments, found {}",
                                name,
                                expected,
                                args.len()
                            ),
                            ty.span,
                        );
                    }
                }
                let id = match &head {
                    BaseType::List(_) => {
                        return Some(BaseType::List(Box::new(self.resolve_arg(args, 0))))
                    }
                    BaseType::Map(_, _) => {
                        return Some(BaseType::Map(
                            Box::new(self.resolve_arg(args, 0)),
                            Box::new(self.resolve_arg(args, 1)),
                        ))
                    }
                    BaseType::Stack(_) => {
                        return Some(BaseType::Stack(Box::new(self.resolve_arg(args, 0))))
                    }
                    BaseType::Class(id, _) => *id,
                    BaseType::Interface(id, _) => *id,
                    BaseType::Enum(id, _) => *id,
                    other => {
                        self.diags.err_at(
                            "C101",
                            format!("{} is not a generic type", other),
                            ty.span,
                        );
                        return None;
                    }
                };
                let args: Vec<BaseType> = args
                    .iter()
                    .map(|a| self.resolve(a).unwrap_or(BaseType::Object))
                    .collect();
                if let Some(kind) = self.program.kind_of_type_name(name) {
                    match kind {
                        KindOf::Class => BaseType::Class(id, args),
                        KindOf::Interface => BaseType::Interface(id, args),
                        _ => BaseType::Enum(id, args),
                    }
                } else {
                    head
                }
            }
        };
        Some(base)
    }

    fn resolve_arg(&mut self, args: &[TypeRef], i: usize) -> BaseType {
        args.get(i)
            .and_then(|a| self.resolve(a))
            .unwrap_or(BaseType::Object)
    }

    fn resolve_named(&mut self, name: &str) -> Option<BaseType> {
        match name {
            "Bool" => return Some(BaseType::Bool),
            "Byte" => return Some(BaseType::Byte),
            "Int" => return Some(BaseType::Int),
            "Float" => return Some(BaseType::Float),
            "Char" => return Some(BaseType::Char),
            "String" => return Some(BaseType::String),
            "Object" => return Some(BaseType::Object),
            "Void" => return Some(BaseType::Void),
            "List" => return Some(BaseType::List(Box::new(BaseType::Object))),
            "Map" => {
                return Some(BaseType::Map(
                    Box::new(BaseType::Object),
                    Box::new(BaseType::Object),
                ))
            }
            "Stack" => return Some(BaseType::Stack(Box::new(BaseType::Object))),
            "Writer" => return Some(BaseType::Interface(builtin::WRITER, vec![])),
            "Reader" => return Some(BaseType::Interface(builtin::READER, vec![])),
            "Runnable" => return Some(BaseType::Interface(builtin::RUNNABLE, vec![])),
            "Equatable" => return Some(BaseType::Interface(builtin::EQUATABLE, vec![])),
            "Hashable" => return Some(BaseType::Interface(builtin::HASHABLE, vec![])),
            "Iterable" => return Some(BaseType::Interface(builtin::ITERABLE, vec![])),
            "Countable" => return Some(BaseType::Interface(builtin::COUNTABLE, vec![])),
            "Stringable" => return Some(BaseType::Interface(builtin::STRINGABLE, vec![])),
            "Comparable" => return Some(BaseType::Interface(builtin::COMPARABLE, vec![])),
            "Thread" => return Some(BaseType::native(crate::types::native_kind::THREAD)),
            "Mutex" => return Some(BaseType::native(crate::types::native_kind::MUTEX)),
            "Semaphore" => return Some(BaseType::native(crate::types::native_kind::SEMAPHORE)),
            "Process" => return Some(BaseType::native(crate::types::native_kind::PROCESS)),
            "Regex" => return Some(BaseType::native(crate::types::native_kind::REGEX)),
            "Stream" => return Some(BaseType::native(crate::types::native_kind::STREAM)),
            "Math" | "Type" | "Base64" | "Hash" | "Json" | "Time" | "Random" | "File" | "Test" => {
                // Static namespaces: represented as their first member type for
                // resolution purposes; static_member lookup uses the name.
                return Some(BaseType::Object);
            }
            _ => {}
        }
        if let Some(i) = self.type_params.iter().position(|p| p == name) {
            return Some(BaseType::TypeVar(i as u32));
        }
        if name == "Self" {
            let cid = self.class_id?;
            let n = self.program.classes[cid as usize].type_params.len();
            let args: Vec<BaseType> = (0..n).map(|i| BaseType::TypeVar(i as u32)).collect();
            return Some(BaseType::Class(cid, args));
        }
        match self.program.lookup_item(name) {
            Some((KindOf::Class, id)) => {
                let n = self.program.classes[id as usize].type_params.len();
                let args: Vec<BaseType> = (0..n).map(|i| BaseType::TypeVar(i as u32)).collect();
                Some(BaseType::Class(id, args))
            }
            Some((KindOf::Interface, id)) => {
                let n = self.program.interfaces[id as usize].type_params.len();
                let args: Vec<BaseType> = (0..n).map(|i| BaseType::TypeVar(i as u32)).collect();
                Some(BaseType::Interface(id, args))
            }
            Some((KindOf::Enum, id)) => {
                let n = self.program.enums[id as usize].type_params.len();
                let args: Vec<BaseType> = (0..n).map(|i| BaseType::TypeVar(i as u32)).collect();
                Some(BaseType::Enum(id, args))
            }
            None => {
                self.diags.err("C100", format!("unknown type '{}'", name));
                None
            }
        }
    }
}

#[derive(Clone, Copy, PartialEq, Eq)]
pub enum KindOf {
    Class,
    Interface,
    Enum,
}

impl ResolvedProgram {
    pub fn lookup_item(&self, name: &str) -> Option<(KindOf, u32)> {
        if let Some(c) = self.classes.iter().find(|c| c.name == name) {
            return Some((KindOf::Class, c.id));
        }
        if let Some(i) = self.interfaces.iter().find(|i| i.name == name) {
            return Some((KindOf::Interface, i.id));
        }
        if let Some(e) = self.enums.iter().find(|e| e.name == name) {
            return Some((KindOf::Enum, e.id));
        }
        None
    }

    pub fn kind_of_type_name(&self, name: &str) -> Option<KindOf> {
        self.lookup_item(name).map(|(k, _)| k)
    }
}

fn builtin_interface(id: u32, name: &str, slots: &[&str]) -> InterfaceInfo {
    InterfaceInfo {
        id,
        name: name.to_string(),
        type_params: vec![],
        parents: vec![],
        all_parents: vec![],
        slots: slots
            .iter()
            .map(|s| InterfaceSlot {
                name: s.to_string(),
                default: None,
                decl: None,
            })
            .collect(),
        methods: vec![],
        def: InterfaceDef {
            name: name.to_string(),
            name_span: Span::new(0, 0, 0),
            type_params: vec![],
            extends: vec![],
            methods: vec![],
            span: Span::new(0, 0, 0),
        },
    }
}

/// Resolve a parsed program into symbol tables and validated metadata.
pub fn resolve_program(program: &Program, diags: &mut Diagnostics) -> ResolvedProgram {
    let mut rp = ResolvedProgram {
        package: program.package.clone(),
        classes: vec![],
        interfaces: vec![
            builtin_interface(
                builtin::WRITER,
                "Writer",
                &["write", "print", "println", "redirect", "reset"],
            ),
            builtin_interface(builtin::READER, "Reader", &["readln", "readAll"]),
            builtin_interface(builtin::RUNNABLE, "Runnable", &["run"]),
            builtin_interface(builtin::EQUATABLE, "Equatable", &["equals"]),
            builtin_interface(builtin::HASHABLE, "Hashable", &["hash"]),
            builtin_interface(builtin::ITERABLE, "Iterable", &[]),
            builtin_interface(builtin::COUNTABLE, "Countable", &["size"]),
            builtin_interface(builtin::STRINGABLE, "Stringable", &["toString"]),
            builtin_interface(builtin::COMPARABLE, "Comparable", &["compare"]),
        ],
        enums: vec![],
        entry: None,
    };

    // ---- 1. Collect items, assign ids, reject duplicates ----------------
    for item in &program.items {
        match item {
            Item::Class(def) => {
                if rp.lookup_item(&def.name).is_some() {
                    diags.err_at(
                        "C109",
                        format!("duplicate type name '{}'", def.name),
                        def.name_span,
                    );
                    continue;
                }
                let id = rp.classes.len() as u32;
                rp.classes.push(ClassInfo {
                    id,
                    name: def.name.clone(),
                    type_params: def.type_params.clone(),
                    parent: None,
                    direct_interfaces: vec![],
                    all_interfaces: vec![],
                    fields: vec![],
                    own_field_slots: HashMap::new(),
                    vtable_names: vec![],
                    methods: vec![],
                    def: def.clone(),
                });
            }
            Item::Interface(def) => {
                if rp.lookup_item(&def.name).is_some() {
                    diags.err_at(
                        "C109",
                        format!("duplicate type name '{}'", def.name),
                        def.name_span,
                    );
                    continue;
                }
                let id = rp.interfaces.len() as u32;
                rp.interfaces.push(InterfaceInfo {
                    id,
                    name: def.name.clone(),
                    type_params: def.type_params.clone(),
                    parents: vec![],
                    all_parents: vec![],
                    slots: vec![],
                    methods: vec![],
                    def: def.clone(),
                });
            }
            Item::Enum(def) => {
                if rp.lookup_item(&def.name).is_some() {
                    diags.err_at(
                        "C109",
                        format!("duplicate type name '{}'", def.name),
                        def.name_span,
                    );
                    continue;
                }
                let id = rp.enums.len() as u32;
                rp.enums.push(EnumInfo {
                    id,
                    name: def.name.clone(),
                    type_params: def.type_params.clone(),
                    variants: vec![],
                    def: def.clone(),
                });
            }
        }
    }

    // ---- 2. Resolve parents and interface references --------------------
    for idx in 0..rp.classes.len() {
        let cid = rp.classes[idx].id;
        let cname = rp.classes[idx].name.clone();
        let extends = rp.classes[idx].def.extends.clone();
        let implements = rp.classes[idx].def.implements.clone();
        let tparams = param_names(&rp.classes[idx].type_params);
        let resolved_parent = {
            let mut ctx = TypeCtx {
                program: &rp,
                diags,
                class_id: Some(cid),
                type_params: tparams.clone(),
            };
            extends.as_ref().map(|r| ctx.resolve(r))
        };
        if let Some(parent_ref) = &extends {
            match resolved_parent {
                Some(Some(BaseType::Class(pid, _))) => {
                    if pid == cid {
                        diags.err_at(
                            "C111",
                            format!("class '{}' cannot extend itself", cname),
                            parent_ref.span,
                        );
                    } else {
                        rp.classes[idx].parent = Some(pid);
                    }
                }
                Some(Some(other)) => {
                    diags.err_at(
                        "C110",
                        format!("class '{}' cannot extend non-class type {}", cname, other),
                        parent_ref.span,
                    );
                }
                _ => {}
            }
        }
        for iface_ref in &implements {
            let r = {
                let mut ctx = TypeCtx {
                    program: &rp,
                    diags,
                    class_id: Some(cid),
                    type_params: tparams.clone(),
                };
                ctx.resolve(iface_ref)
            };
            match r {
                Some(BaseType::Interface(iid, _)) => {
                    if !rp.classes[idx].direct_interfaces.contains(&iid) {
                        rp.classes[idx].direct_interfaces.push(iid);
                    } else {
                        diags.err_at(
                            "C112",
                            format!(
                                "duplicate interface conformance '{}' for class '{}'",
                                rp.interface_name(iid),
                                cname
                            ),
                            iface_ref.span,
                        );
                    }
                }
                Some(other) => {
                    diags.err_at(
                        "C113",
                        format!(
                            "class '{}' cannot implement non-interface type {}",
                            cname, other
                        ),
                        iface_ref.span,
                    );
                }
                None => {}
            }
        }
    }
    for idx in 0..rp.interfaces.len() {
        let iid = rp.interfaces[idx].id;
        let iname = rp.interfaces[idx].name.clone();
        let parents_refs = rp.interfaces[idx].def.extends.clone();
        let tparams = param_names(&rp.interfaces[idx].type_params);
        for parent_ref in &parents_refs {
            let r = {
                let mut ctx = TypeCtx {
                    program: &rp,
                    diags,
                    class_id: None,
                    type_params: tparams.clone(),
                };
                ctx.resolve(parent_ref)
            };
            match r {
                Some(BaseType::Interface(pid, _)) => {
                    if pid == iid {
                        diags.err_at(
                            "C111",
                            format!("interface '{}' cannot extend itself", iname),
                            parent_ref.span,
                        );
                    } else if !rp.interfaces[idx].parents.contains(&pid) {
                        rp.interfaces[idx].parents.push(pid);
                    }
                }
                Some(other) => {
                    diags.err_at(
                        "C114",
                        format!(
                            "interface '{}' cannot extend non-interface type {}",
                            iname, other
                        ),
                        parent_ref.span,
                    );
                }
                None => {}
            }
        }
    }

    // Detect inheritance cycles.
    for idx in 0..rp.classes.len() {
        let c = &rp.classes[idx];
        let mut seen = std::collections::HashSet::new();
        let mut cur = c.parent;
        while let Some(p) = cur {
            if !seen.insert(p) || p == c.id {
                diags.err_at(
                    "C115",
                    format!("inheritance cycle involving class '{}'", c.name),
                    c.def.span,
                );
                // Break the cycle before field/vtable layout walks the
                // parent chain. The diagnostic remains the user-facing
                // result, while later compiler phases terminate normally.
                rp.classes[idx].parent = None;
                break;
            }
            cur = rp.classes[p as usize].parent;
        }
    }

    for idx in 0..rp.interfaces.len() {
        let iid = rp.interfaces[idx].id;
        let iname = rp.interfaces[idx].name.clone();
        let parents = rp.interfaces[idx].parents.clone();
        let mut bad = std::collections::HashSet::new();
        for parent in parents {
            let mut seen = std::collections::HashSet::new();
            let mut pending = vec![parent];
            while let Some(p) = pending.pop() {
                if p == iid {
                    diags.err_at(
                        "C115",
                        format!("inheritance cycle involving interface '{}'", iname),
                        rp.interfaces[idx].def.span,
                    );
                    bad.insert(parent);
                    break;
                }
                if seen.insert(p) {
                    pending.extend(rp.interfaces[p as usize].parents.iter().copied());
                }
            }
        }
        if !bad.is_empty() {
            rp.interfaces[idx]
                .parents
                .retain(|parent| !bad.contains(parent));
        }
    }

    // Transitive interface closure (parents' interfaces first, then own).
    for idx in 0..rp.classes.len() {
        let mut acc: Vec<u32> = vec![];
        let mut cur = rp.classes[idx].parent;
        while let Some(p) = cur {
            for i in &rp.classes[p as usize].all_interfaces {
                if !acc.contains(i) {
                    acc.push(*i);
                }
            }
            cur = rp.classes[p as usize].parent;
        }
        for i in &rp.classes[idx].direct_interfaces {
            // Include the parent interfaces of each direct interface.
            let mut stack = vec![*i];
            while let Some(ii) = stack.pop() {
                if !acc.contains(&ii) {
                    acc.push(ii);
                }
                for p in &rp.interfaces[ii as usize].parents {
                    if !acc.contains(p) {
                        stack.push(*p);
                    }
                }
            }
        }
        rp.classes[idx].all_interfaces = acc;
    }
    for idx in 0..rp.interfaces.len() {
        let mut acc: Vec<u32> = vec![];
        let mut stack: Vec<u32> = rp.interfaces[idx].parents.clone();
        while let Some(p) = stack.pop() {
            if !acc.contains(&p) {
                acc.push(p);
            }
            for gp in &rp.interfaces[p as usize].parents {
                if !acc.contains(gp) {
                    stack.push(*gp);
                }
            }
        }
        rp.interfaces[idx].all_parents = acc;
    }

    // Interface slots: parents' slots first, then own methods.
    // Built-in interfaces keep their fixed slot lists.
    for idx in 0..rp.interfaces.len() {
        let iid = rp.interfaces[idx].id;
        if (iid as usize) < builtin::BUILTIN_COUNT {
            continue;
        }
        let mut slots: Vec<InterfaceSlot> = vec![];
        // Deterministic order: BFS over parents.
        let mut ordered_parents: Vec<u32> = vec![];
        let mut queue: Vec<u32> = rp.interfaces[idx].parents.clone();
        while let Some(p) = queue.pop() {
            if !ordered_parents.contains(&p) {
                ordered_parents.push(p);
                for gp in rp.interfaces[p as usize].parents.iter().rev() {
                    if !queue.contains(gp) {
                        queue.push(*gp);
                    }
                }
            }
        }
        for p in ordered_parents {
            for s in &rp.interfaces[p as usize].slots {
                if !slots.iter().any(|x| x.name == s.name) {
                    slots.push(s.clone());
                }
            }
        }
        for (midx, m) in rp.interfaces[idx].def.methods.iter().enumerate() {
            if !slots.iter().any(|s| s.name == m.name) {
                let d = if m.body.is_some() {
                    Some((iid, midx))
                } else {
                    None
                };
                slots.push(InterfaceSlot {
                    name: m.name.clone(),
                    default: d,
                    decl: Some((iid, midx)),
                });
            } else if m.body.is_some() {
                let slot = slots.iter_mut().find(|s| s.name == m.name).unwrap();
                slot.default = Some((iid, midx));
            }
        }
        let methods: Vec<MethodInfo> = rp.interfaces[idx]
            .def
            .methods
            .iter()
            .map(MethodInfo::from_def)
            .collect();
        rp.interfaces[idx].slots = slots;
        rp.interfaces[idx].methods = methods;
    }

    // ---- 3. Field layout (parent-first) and vtable names ----------------
    for idx in 0..rp.classes.len() {
        let cid = rp.classes[idx].id;
        let cname = rp.classes[idx].name.clone();
        // Build chain root-first.
        let mut chain: Vec<u32> = vec![cid];
        let mut cur = rp.classes[idx].parent;
        while let Some(p) = cur {
            chain.push(p);
            cur = rp.classes[p as usize].parent;
        }
        chain.reverse();
        let mut fields: Vec<FieldInfo> = vec![];
        let mut vtable_names: Vec<String> = vec![];
        for c2 in &chain {
            let info = &rp.classes[*c2 as usize];
            for f in &info.def.fields {
                if fields.iter().any(|x| x.name == f.name) {
                    diags.err_at(
                        "C116",
                        format!(
                            "field '{}' shadows an inherited field in class '{}'",
                            f.name, cname
                        ),
                        f.span,
                    );
                    continue;
                }
                fields.push(FieldInfo {
                    name: f.name.clone(),
                    ty: Ty::non_null(BaseType::Object), // filled by type pass
                    mutable: f.mutable,
                    is_pub: f.visibility == crate::ast::Visibility::Pub,
                    declaring: *c2,
                });
            }
            for m in &info.def.methods {
                if !m.is_static && !vtable_names.contains(&m.name) {
                    vtable_names.push(m.name.clone());
                }
            }
        }
        let base = rp.classes[idx]
            .parent
            .map(|p| rp.classes[p as usize].fields.len())
            .unwrap_or(0);
        let own_slots: Vec<(String, u16)> = rp.classes[idx]
            .def
            .fields
            .iter()
            .enumerate()
            .map(|(off, f)| (f.name.clone(), (base + off) as u16))
            .collect();
        let methods: Vec<MethodInfo> = rp.classes[idx]
            .def
            .methods
            .iter()
            .map(MethodInfo::from_def)
            .collect();
        rp.classes[idx].fields = fields;
        rp.classes[idx].vtable_names = vtable_names;
        rp.classes[idx].methods = methods;
        rp.classes[idx].own_field_slots.clear();
        for (n, sl) in own_slots {
            rp.classes[idx].own_field_slots.insert(n, sl);
        }
    }

    // ---- 3b. Type pass: fill field/param/return types -------------------
    resolve_types(&mut rp, diags);

    // ---- 4. Method validation -------------------------------------------
    validate_methods(&mut rp, diags);

    // ---- 5. Entry point ---------------------------------------------------
    resolve_entry_point(&mut rp, diags);

    rp
}

/// Resolve declared types (fields, parameters, return types) into concrete
/// `Ty` values using each declaration's type-parameter context.
fn resolve_types(rp: &mut ResolvedProgram, diags: &mut Diagnostics) {
    fn resolve_one(
        rp: &ResolvedProgram,
        diags: &mut Diagnostics,
        class_id: Option<u32>,
        type_params: &[String],
        ref_: &TypeRef,
    ) -> Option<Ty> {
        let mut ctx = TypeCtx {
            program: rp,
            diags,
            class_id,
            type_params: type_params.to_vec(),
        };
        ctx.resolve(ref_).map(|b| Ty {
            base: b,
            nullable: ref_.nullable,
        })
    }

    for idx in 0..rp.classes.len() {
        let cid = rp.classes[idx].id;
        let ctparams = param_names(&rp.classes[idx].type_params);
        // Phase 1: resolve into locals (no live borrows of rp).
        let field_tys: Vec<(String, Option<Ty>)> = rp.classes[idx]
            .def
            .fields
            .iter()
            .map(|f| {
                (
                    f.name.clone(),
                    resolve_one(rp, diags, Some(cid), &ctparams, &f.ty),
                )
            })
            .collect();
        let method_tys: Vec<(Vec<Option<Ty>>, Option<Ty>)> = rp.classes[idx]
            .def
            .methods
            .iter()
            .map(|mdef| {
                let mut tps = ctparams.clone();
                tps.extend(param_names(&mdef.type_params));
                let pts: Vec<Option<Ty>> = mdef
                    .params
                    .iter()
                    .map(|p| resolve_one(rp, diags, Some(cid), &tps, &p.ty))
                    .collect();
                let rt = mdef
                    .return_ty
                    .as_ref()
                    .and_then(|r| resolve_one(rp, diags, Some(cid), &tps, r));
                (pts, rt)
            })
            .collect();
        // Phase 2: assign.
        for (name, ty) in field_tys {
            if let Some(t) = ty {
                let off = rp.classes[idx].own_field_slots.get(&name).copied();
                if let Some(off) = off {
                    rp.classes[idx].fields[off as usize].ty = t;
                }
            }
        }
        for (midx, (pts, rt)) in method_tys.iter().enumerate() {
            for (pidx, t) in pts.iter().enumerate() {
                if let Some(t) = t {
                    rp.classes[idx].methods[midx].params[pidx].ty = t.clone();
                }
            }
            if let Some(t) = rt {
                rp.classes[idx].methods[midx].return_ty = Some(t.clone());
            }
        }
    }

    // Propagate resolved field types to inherited copies: a subclass's
    // field layout duplicates the parent's FieldInfo entries, which were
    // still at their Object placeholder when copied.
    for idx in 0..rp.classes.len() {
        let updates: Vec<(usize, Ty)> = rp.classes[idx]
            .fields
            .iter()
            .enumerate()
            .filter(|(_, f)| f.declaring != rp.classes[idx].id)
            .filter_map(|(fi, f)| {
                rp.classes[f.declaring as usize]
                    .fields
                    .iter()
                    .find(|sf| sf.name == f.name)
                    .map(|src| (fi, src.ty.clone()))
            })
            .collect();
        for (fi, ty) in updates {
            rp.classes[idx].fields[fi].ty = ty;
        }
    }

    for idx in 0..rp.enums.len() {
        let etparams = param_names(&rp.enums[idx].type_params);
        let variants: Vec<EnumVariantInfo> = rp.enums[idx]
            .def
            .variants
            .iter()
            .map(|v| {
                let payload = v
                    .payload
                    .as_ref()
                    .and_then(|r| resolve_one(rp, diags, None, &etparams, r));
                EnumVariantInfo {
                    name: v.name.clone(),
                    payload,
                }
            })
            .collect();
        rp.enums[idx].variants = variants;
    }

    for idx in 0..rp.interfaces.len() {
        if rp.interfaces[idx].id < builtin::BUILTIN_COUNT as u32 {
            continue;
        }
        let itparams = param_names(&rp.interfaces[idx].type_params);
        let method_tys: Vec<(Vec<Option<Ty>>, Option<Ty>)> = rp.interfaces[idx]
            .def
            .methods
            .iter()
            .map(|mdef| {
                let mut tps = itparams.clone();
                tps.extend(param_names(&mdef.type_params));
                let pts: Vec<Option<Ty>> = mdef
                    .params
                    .iter()
                    .map(|p| resolve_one(rp, diags, None, &tps, &p.ty))
                    .collect();
                let rt = mdef
                    .return_ty
                    .as_ref()
                    .and_then(|r| resolve_one(rp, diags, None, &tps, r));
                (pts, rt)
            })
            .collect();
        for (midx, (pts, rt)) in method_tys.iter().enumerate() {
            for (pidx, t) in pts.iter().enumerate() {
                if let Some(t) = t {
                    rp.interfaces[idx].methods[midx].params[pidx].ty = t.clone();
                }
            }
            if let Some(t) = rt {
                rp.interfaces[idx].methods[midx].return_ty = Some(t.clone());
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Method validation: duplicates, overrides, conformance, entry point
// ---------------------------------------------------------------------------

fn visibility_rank(v: Visibility) -> u8 {
    match v {
        Visibility::Private => 0,
        Visibility::Protected => 1,
        Visibility::Pub => 2,
    }
}

fn validate_methods(rp: &mut ResolvedProgram, diags: &mut Diagnostics) {
    for idx in 0..rp.classes.len() {
        let cid = rp.classes[idx].id;
        let cname = rp.classes[idx].name.clone();
        let parent = rp.classes[idx].parent;
        let all_ifaces = rp.classes[idx].all_interfaces.clone();
        let def_methods = rp.classes[idx].def.methods.clone();
        // Duplicate method names within the class.
        let mut seen: HashMap<String, usize> = HashMap::new();
        for (midx, m) in def_methods.iter().enumerate() {
            let key = format!("{}:{}", m.name, m.is_static);
            match seen.entry(key.clone()) {
                std::collections::hash_map::Entry::Vacant(e) => {
                    e.insert(midx);
                }
                std::collections::hash_map::Entry::Occupied(_) => {
                    diags.err_at(
                        "C117",
                        format!("duplicate method '{}' in class '{}'", m.name, cname),
                        m.span,
                    );
                }
            }
        }
        // Override rules against ancestors.
        let resolved_methods = &rp.classes[idx].methods;
        for (mi, m) in def_methods.iter().enumerate() {
            if m.is_static {
                continue;
            }
            let resolved = &resolved_methods[mi];
            let ancestor = rp.classes[idx]
                .find_instance_method(rp, parent.unwrap_or(cid), &m.name)
                .filter(|(cid2, _)| *cid2 != cid);
            // Also consider interface requirements/defaults.
            let iface_provider = all_ifaces.iter().find_map(|iid| {
                rp.interfaces[*iid as usize]
                    .slots
                    .iter()
                    .position(|s| s.name == m.name)
                    .map(|slot| (*iid, slot))
            });
            let overridden = ancestor.is_some() || iface_provider.is_some();
            if m.is_override && !overridden {
                diags.err_at(
                    "C118",
                    format!(
                        "method '{}' in class '{}' has 'override' but overrides nothing",
                        m.name, cname
                    ),
                    m.span,
                );
            }
            if !m.is_override && ancestor.is_some() {
                diags.err_at("C119", format!("method '{}' in class '{}' overrides an inherited method and must be marked 'override'", m.name, cname), m.span);
            }
            if let Some((aidx, ameth)) = ancestor {
                let am = &rp.classes[aidx as usize].methods[ameth];
                if visibility_rank(m.visibility) < visibility_rank(am.visibility) {
                    diags.err_at(
                        "C120",
                        format!(
                            "override of '{}' in class '{}' reduces visibility",
                            m.name, cname
                        ),
                        m.span,
                    );
                }
                if m.params.len() != am.params.len() {
                    diags.err_at(
                        "C121",
                        format!(
                            "override of '{}' in class '{}' has incompatible parameter arity",
                            m.name, cname
                        ),
                        m.span,
                    );
                }
                // Signature conformance with the overridden method (checked
                // only when both signatures are concrete, so generic erasure
                // never produces false positives). Overrides must accept at
                // least the ancestor's arguments and return a subtype of the
                // ancestor's result.
                let concrete = |t: &Ty| !t.contains_type_var();
                let same_arity = resolved.params.len() == am.params.len();
                if same_arity {
                    for (i, (pm, pa)) in resolved.params.iter().zip(&am.params).enumerate() {
                        if concrete(&pm.ty)
                            && concrete(&pa.ty)
                            && !crate::types::is_subtype(&pa.ty, &pm.ty, rp)
                        {
                            diags.err_at(
                                "C128",
                                format!(
                                    "override of '{}' in class '{}' has parameter {} of type {} which is narrower than the overridden {}",
                                    m.name,
                                    cname,
                                    i + 1,
                                    rp.type_name(&pm.ty.base)
                                        + if pm.ty.nullable { "?" } else { "" },
                                    rp.type_name(&pa.ty.base)
                                        + if pa.ty.nullable { "?" } else { "" }
                                ),
                                m.span,
                            );
                        }
                    }
                }
                let am_void = am
                    .return_ty
                    .as_ref()
                    .is_none_or(|t| matches!(t.base, BaseType::Void));
                let m_void = resolved
                    .return_ty
                    .as_ref()
                    .is_none_or(|t| matches!(t.base, BaseType::Void));
                match (am_void, m_void, &am.return_ty, &resolved.return_ty) {
                    (true, false, _, _) | (false, true, _, _) => diags.err_at(
                        "C129",
                        format!(
                            "override of '{}' in class '{}' changes whether a value is returned",
                            m.name, cname
                        ),
                        m.span,
                    ),
                    (false, false, Some(at), Some(mt))
                        if concrete(at)
                            && concrete(mt)
                            && !crate::types::is_subtype(mt, at, rp) =>
                    {
                        diags.err_at(
                            "C129",
                            format!(
                                "override of '{}' in class '{}' returns {} which is not a subtype of the overridden {}",
                                m.name,
                                cname,
                                rp.type_name(&mt.base) + if mt.nullable { "?" } else { "" },
                                rp.type_name(&at.base) + if at.nullable { "?" } else { "" }
                            ),
                            m.span,
                        );
                    }
                    _ => {}
                }
            }
        }
    }
    validate_conformance(rp, diags);
}

/// Every interface requirement must be satisfied by a pub method with a
/// compatible signature; competing unrelated defaults require an explicit
/// override.
fn validate_conformance(rp: &mut ResolvedProgram, diags: &mut Diagnostics) {
    for c in &rp.classes {
        for iid in &c.all_interfaces {
            let iface = &rp.interfaces[*iid as usize];
            for slot in &iface.slots {
                let own = c
                    .methods
                    .iter()
                    .position(|m| m.name == slot.name && !m.is_static)
                    .map(|idx| (c.id, idx));
                let inherited = c
                    .find_instance_method(rp, c.parent.unwrap_or(c.id), &slot.name)
                    .filter(|(cid, _)| *cid != c.id);
                let provider = own.or(inherited);
                match provider {
                    Some((cid, idx)) => {
                        let info = &rp.classes[cid as usize];
                        let m = &info.methods[idx];
                        if m.visibility != Visibility::Pub {
                            diags.err_at("C122", format!("class '{}' satisfies interface '{}' with non-public method '{}'", c.name, iface.name, slot.name), m.span);
                        }
                        // Signature conformance: interface-typed calls are
                        // checked against the interface's canonical method
                        // signature, so the class's implementation must
                        // accept at least those arguments and return a
                        // subtype of the declared result.
                        // Canonical signature = most specific default,
                        // otherwise the requirement declaration.
                        let mut req: Option<(Vec<ParamInfo>, Option<Ty>)> = None;
                        for oiid in
                            std::iter::once(*iid).chain(iface.all_parents.iter().rev().copied())
                        {
                            let oi = &rp.interfaces[oiid as usize];
                            if let Some(rm) = oi
                                .methods
                                .iter()
                                .find(|m| m.name == slot.name && m.body.is_some())
                            {
                                req = Some((rm.params.clone(), rm.return_ty.clone()));
                                break;
                            }
                        }
                        if req.is_none() {
                            if let Some((did, didx)) = slot.decl {
                                if let Some(rm) = rp.interfaces[did as usize].methods.get(didx) {
                                    req = Some((rm.params.clone(), rm.return_ty.clone()));
                                }
                            }
                        }
                        if let Some((req_params, req_ret)) = req {
                            let concrete = |t: &Ty| !t.contains_type_var();
                            let arity_ok = req_params.len() == m.params.len();
                            if !arity_ok {
                                diags.err_at(
                                    "C125",
                                    format!(
                                        "method '{}' in class '{}' takes {} parameter(s) but interface '{}' requires {}",
                                        slot.name,
                                        c.name,
                                        m.params.len(),
                                        iface.name,
                                        req_params.len()
                                    ),
                                    m.span,
                                );
                            } else {
                                for (pi, (req, prov)) in
                                    req_params.iter().zip(&m.params).enumerate()
                                {
                                    if concrete(&req.ty)
                                        && concrete(&prov.ty)
                                        && !crate::types::is_subtype(&req.ty, &prov.ty, rp)
                                    {
                                        diags.err_at(
                                            "C126",
                                            format!(
                                                "parameter {} of method '{}' in class '{}' has type {} but interface '{}' requires {} (or a supertype)",
                                                pi + 1,
                                                slot.name,
                                                c.name,
                                                rp.type_name(&prov.ty.base)
                                                    + if prov.ty.nullable { "?" } else { "" },
                                                iface.name,
                                                rp.type_name(&req.ty.base)
                                                    + if req.ty.nullable { "?" } else { "" }
                                            ),
                                            m.span,
                                        );
                                    }
                                }
                            }
                            let req_void = req_ret
                                .as_ref()
                                .is_none_or(|t| matches!(t.base, BaseType::Void));
                            let prov_void = m
                                .return_ty
                                .as_ref()
                                .is_none_or(|t| matches!(t.base, BaseType::Void));
                            match (req_void, prov_void, &req_ret, &m.return_ty) {
                                (true, false, _, _) => diags.err_at(
                                    "C127",
                                    format!(
                                        "method '{}' in class '{}' returns a value but interface '{}' declares no return value",
                                        slot.name, c.name, iface.name
                                    ),
                                    m.span,
                                ),
                                (false, true, _, _) => diags.err_at(
                                    "C127",
                                    format!(
                                        "method '{}' in class '{}' returns nothing but interface '{}' requires a value",
                                        slot.name, c.name, iface.name
                                    ),
                                    m.span,
                                ),
                                (false, false, Some(req_t), Some(prov_t))
                                    if concrete(req_t)
                                        && concrete(prov_t)
                                        && !crate::types::is_subtype(prov_t, req_t, rp) =>
                                {
                                    diags.err_at(
                                        "C127",
                                        format!(
                                            "return type {} of method '{}' in class '{}' is not a subtype of the {} required by interface '{}'",
                                            rp.type_name(&prov_t.base)
                                                + if prov_t.nullable { "?" } else { "" },
                                            slot.name,
                                            c.name,
                                            rp.type_name(&req_t.base)
                                                + if req_t.nullable { "?" } else { "" },
                                            iface.name
                                        ),
                                        m.span,
                                    );
                                }
                                _ => {}
                            }
                        }
                    }
                    None => {
                        if slot.default.is_none() {
                            diags.err_at("C123", format!("class '{}' does not implement required method '{}' of interface '{}'", c.name, slot.name, iface.name), c.def.span);
                        }
                    }
                }
            }
            // Default conflicts: two unrelated interfaces providing defaults
            // for the same slot while the class does not override it.
            for slot in &iface.slots {
                if c.methods
                    .iter()
                    .any(|m| m.name == slot.name && !m.is_static)
                {
                    continue; // explicit implementation wins
                }
                if c.find_instance_method(rp, c.parent.unwrap_or(c.id), &slot.name)
                    .filter(|(cid, _)| *cid != c.id)
                    .is_some()
                {
                    continue; // inherited implementation wins
                }
                let providers: Vec<u32> = c
                    .all_interfaces
                    .iter()
                    .copied()
                    .filter(|ii| {
                        rp.interfaces[*ii as usize]
                            .slots
                            .iter()
                            .any(|s| s.name == slot.name && s.default.is_some())
                    })
                    .collect();
                if providers.len() >= 2 {
                    // Conflict only when no single provider is more specific.
                    let dominated = providers.iter().any(|p| {
                        providers
                            .iter()
                            .any(|q| q != p && rp.interface_extends(*q, *p))
                    });
                    if !dominated {
                        diags.err_at(
                            "C124",
                            format!(
                                "class '{}' has conflicting default implementations of '{}' from interfaces {}; add an explicit 'override' method",
                                c.name,
                                slot.name,
                                providers.iter().map(|p| rp.interface_name(*p)).collect::<Vec<_>>().join(" and ")
                            ),
                            c.def.span,
                        );
                    }
                }
            }
        }
    }
}

fn resolve_entry_point(rp: &mut ResolvedProgram, diags: &mut Diagnostics) {
    let main_idx = match rp.classes.iter().position(|c| c.name == "Main") {
        Some(i) => i,
        None => {
            diags.err(
                "C200",
                "missing entry point: executable programs require 'class Main'",
            );
            return;
        }
    };
    let main = &rp.classes[main_idx];
    let run = main.def.methods.iter().position(|m| m.name == "run");
    let run = match run {
        Some(i) => i,
        None => {
            diags.err(
                "C201",
                "missing entry point: 'class Main' requires a 'run' method",
            );
            return;
        }
    };
    if rp.classes.iter().filter(|c| c.name == "Main").count() > 1 {
        diags.err("C206", "duplicate entry point: multiple 'Main' classes");
        return;
    }
    let m = &main.def.methods[run];
    if !m.is_static {
        diags.err_at("C202", "entry point 'Main::run' must be static", m.span);
        return;
    }
    if m.visibility != Visibility::Pub {
        diags.err_at(
            "C203",
            "entry point 'Main::run' must be public ('pub')",
            m.span,
        );
        return;
    }
    if m.params.len() != 1 || !m.params[0].variadic || m.params[0].default.is_some() {
        diags.err_at(
            "C204",
            "entry point 'Main::run' must take exactly one variadic parameter 'args: String...'",
            m.span,
        );
        return;
    }
    rp.entry = Some((main.id, run));
}
