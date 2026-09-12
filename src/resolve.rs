//! Name resolution and declaration-level semantic analysis.
//!
//! Produces a `ResolvedProgram`: symbol tables, class-local field slots,
//! interface conformance and delegation metadata, class-local and interface
//! dispatch metadata, and entry-point selection. Expression-level type
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
    /// Built-in exception interface; every throwable object conforms.
    pub const THROWSABLE: u32 = 9;
    pub const BUILTIN_COUNT: usize = 10;
}

// Fields are always private; ownership is tracked by `declaring`.
#[derive(Debug, Clone)]
pub struct FieldInfo {
    pub name: String,
    pub ty: Ty,
    pub mutable: bool,
    /// Class that declares the field (encapsulation owner).
    pub declaring: u32,
    /// `true` for class-level static fields (stored in `ClassInfo.static_fields`
    /// with their own slot namespace, distinct from instance slots).
    pub is_static: bool,
}

#[derive(Debug, Clone)]
pub struct ParamInfo {
    pub name: String,
    pub ty: Ty,
    pub default: Option<Expr>,
    pub variadic: bool,
}

/// A synthetic forwarding implementation generated from
/// `delegate Interface to field`.
#[derive(Debug, Clone)]
pub struct DelegateTarget {
    /// Private field that stores the composed object.
    pub field: String,
    pub field_slot: u16,
    /// Interface whose method is forwarded.
    pub interface: u32,
    /// Interface dispatch slot used by `CallInterface`.
    pub iface_slot: u16,
}

#[derive(Debug, Clone)]
pub struct MethodInfo {
    pub name: String,
    /// `true` when exported to the public method surface; `false` is
    /// class-private. Fields have no visibility: they are always private.
    pub visibility: Visibility,
    pub is_static: bool,
    pub type_params: Vec<TypeParam>,
    pub params: Vec<ParamInfo>,
    pub return_ty: Option<Ty>,
    pub body: Option<Block>,
    /// Set for compiler-generated delegation wrappers.
    pub delegate: Option<DelegateTarget>,
    pub span: Span,
}

impl MethodInfo {
    fn from_def(def: &MethodDef) -> MethodInfo {
        // Fields are always private; methods are private unless `public`.
        let visibility = if def.is_public {
            crate::ast::Visibility::Public
        } else {
            crate::ast::Visibility::Private
        };
        MethodInfo {
            name: def.name.clone(),
            visibility,
            is_static: def.is_static,
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
            delegate: None,
            span: def.span,
        }
    }

    /// True for a compiler-generated delegation wrapper.
    pub fn is_delegate(&self) -> bool {
        self.delegate.is_some()
    }
}

/// The effective implementation selected for a class-level method contract.
#[derive(Debug, Clone)]
pub struct EffectiveMethod {
    pub name: String,
    /// Index into `ClassInfo.methods` for a class-local or synthetic
    /// delegated implementation; `None` when an interface default provides it.
    pub class_method: Option<usize>,
    /// Most-specific interface default provider when `class_method` is `None`.
    pub default: Option<(u32, usize)>,
    /// Public effective surface (interfaces and `Object` dynamic dispatch).
    pub is_public: bool,
}

#[derive(Debug)]
pub struct ClassInfo {
    pub id: u32,
    pub name: String,
    pub type_params: Vec<TypeParam>,
    /// Interfaces this class explicitly implements (nominal conformance only).
    pub direct_interfaces: Vec<u32>,
    /// Transitive interface closure from direct interfaces and interface
    /// parents only; never from class inheritance.
    pub all_interfaces: Vec<u32>,
    /// Resolved type arguments for every interface in `all_interfaces`.
    pub interface_bindings: Vec<(u32, Vec<BaseType>)>,
    /// Class-local field slots: slot 0..N-1 are this class's own instance
    /// fields. Static fields live in `static_fields` with a separate slot
    /// namespace and never participate in construction or delegation.
    pub fields: Vec<FieldInfo>,
    /// Static fields in declaration order; slot = index.
    pub static_fields: Vec<FieldInfo>,
    /// Dispatch-slot names for this class: direct instance methods followed
    /// by synthetic delegation wrappers.
    pub method_names: Vec<String>,
    pub methods: Vec<MethodInfo>,
    /// Effective implementation per name/signature contract, including
    /// interface defaults; used by the checker and metadata builder.
    pub effective_methods: Vec<EffectiveMethod>,
    /// Explicit interface delegation to composed fields.
    pub delegates: Vec<DelegateDecl>,
    pub def: ClassDef,
}

impl ClassInfo {
    /// Slot index of a dispatch name within this class's method table.
    pub fn method_slot(&self, name: &str) -> Option<u16> {
        self.method_names
            .iter()
            .position(|n| n == name)
            .map(|i| i as u16)
    }

    /// Direct instance method with the given name in this class only.
    pub fn find_local_method(&self, name: &str) -> Option<(usize, &MethodInfo)> {
        self.methods
            .iter()
            .enumerate()
            .find(|(_, m)| m.name == name && !m.is_static)
    }

    /// Direct static method with the given name in this class only.
    pub fn find_local_static(&self, name: &str) -> Option<(usize, &MethodInfo)> {
        self.methods
            .iter()
            .enumerate()
            .find(|(_, m)| m.name == name && m.is_static)
    }

    /// Effective method contract with the given name, if any.
    pub fn find_effective(&self, name: &str) -> Option<&EffectiveMethod> {
        self.effective_methods.iter().find(|m| m.name == name)
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
    /// Resolved type arguments for each direct parent interface.
    pub parent_bindings: Vec<(u32, Vec<BaseType>)>,
    pub all_parents: Vec<u32>,
    /// Resolved type arguments for every transitive parent, expressed in
    /// this interface's own type-parameter space.
    pub all_parent_bindings: Vec<(u32, Vec<BaseType>)>,
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
    /// Entry point: (class_id, method_index) of Main.run.
    pub entry: Option<(u32, usize)>,
}

impl ResolvedProgram {
    fn local_type_name<'a>(&self, name: &'a str) -> Option<&'a str> {
        if let Some(rest) = name.strip_prefix(&self.package) {
            rest.strip_prefix('.')
        } else if name.contains('.') {
            None
        } else {
            Some(name)
        }
    }

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
    fn class_interface_args(&self, class: u32, interface: u32) -> Option<Vec<BaseType>> {
        self.classes[class as usize]
            .interface_bindings
            .iter()
            .find(|(id, _)| *id == interface)
            .map(|(_, args)| args.clone())
    }

    fn interface_extends(&self, child: u32, ancestor: u32) -> bool {
        if child == ancestor {
            return true;
        }
        self.interfaces[child as usize]
            .all_parents
            .contains(&ancestor)
    }

    fn interface_parent_args(&self, child: u32, ancestor: u32) -> Option<Vec<BaseType>> {
        let iface = &self.interfaces[child as usize];
        if child == ancestor {
            return Some(
                (0..iface.type_params.len())
                    .map(|i| BaseType::TypeVar(i as u32))
                    .collect(),
            );
        }
        iface
            .all_parent_bindings
            .iter()
            .find(|(id, _)| *id == ancestor)
            .map(|(_, args)| args.clone())
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
            "Boolean" => return Some(BaseType::Boolean),
            "Byte" => return Some(BaseType::Byte),
            "Short" => return Some(BaseType::Short),
            "Integer" => return Some(BaseType::Integer),
            "Long" => return Some(BaseType::Long),
            "Float" => return Some(BaseType::Float),
            "Double" => return Some(BaseType::Double),
            "BigInteger" => return Some(BaseType::BigInteger),
            "BigDecimal" => return Some(BaseType::BigDecimal),
            "Char" => return Some(BaseType::Char),
            "String" => return Some(BaseType::String),
            "Object" => return Some(BaseType::Object),
            // `Throwable` is the built-in exception interface; as a type it
            // names the universal throwable object.
            "Throwable" => return Some(BaseType::Object),
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
            "Exception" => return Some(BaseType::native(crate::types::native_kind::EXCEPTION)),
            "Math" | "Type" | "Base64" | "Hash" | "Json" | "Time" | "Random" | "File" | "Test" => {
                // Static namespaces: represented as their first member type for
                // resolution purposes; static_member lookup uses the name.
                return Some(BaseType::Object);
            }
            "System" => {
                // Singleton of static factory methods (out/in/err); the return
                // type of each member is fixed by static_member lookup.
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
        let name = self.local_type_name(name)?;
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
        parent_bindings: vec![],
        all_parents: vec![],
        all_parent_bindings: vec![],
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
            builtin_interface(builtin::THROWSABLE, "Throwable", &["message"]),
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
                    direct_interfaces: vec![],
                    all_interfaces: vec![],
                    interface_bindings: vec![],
                    fields: vec![],
                    static_fields: vec![],
                    method_names: vec![],
                    methods: vec![],
                    effective_methods: vec![],
                    delegates: vec![],
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
                    parent_bindings: vec![],
                    all_parents: vec![],
                    all_parent_bindings: vec![],
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

    // ---- 2. Resolve interface references and delegation ----------------
    for idx in 0..rp.classes.len() {
        let cid = rp.classes[idx].id;
        let cname = rp.classes[idx].name.clone();
        let implements = rp.classes[idx].def.implements.clone();
        let tparams = param_names(&rp.classes[idx].type_params);
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
                Some(BaseType::Interface(iid, iargs)) => {
                    if !rp.classes[idx].direct_interfaces.contains(&iid) {
                        rp.classes[idx].direct_interfaces.push(iid);
                        rp.classes[idx].interface_bindings.push((iid, iargs));
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
        // Resolve explicit interface delegation to composed fields.
        let delegates = rp.classes[idx].def.delegates.clone();
        for d in &delegates {
            // The delegated interface reference is resolved against the
            // class's type-parameter context.
            let r = {
                let mut ctx = TypeCtx {
                    program: &rp,
                    diags,
                    class_id: Some(cid),
                    type_params: tparams.clone(),
                };
                ctx.resolve(&d.interface)
            };
            match r {
                Some(BaseType::Interface(iid, iargs)) => {
                    rp.classes[idx].delegates.push(DelegateDecl {
                        interface: d.interface.clone(),
                        interface_id: iid,
                        interface_args: iargs,
                        target_field: d.target_field.clone(),
                        span: d.span,
                    });
                }
                Some(other) => {
                    diags.err_at(
                        "C110",
                        format!(
                            "class '{}' cannot delegate non-interface type {}",
                            cname, other
                        ),
                        d.interface.span,
                    );
                }
                None => {
                    diags.err_at(
                        "C110",
                        format!("class '{}' cannot delegate unresolved interface", cname),
                        d.interface.span,
                    );
                }
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
                Some(BaseType::Interface(pid, pargs)) => {
                    if pid == iid {
                        diags.err_at(
                            "C111",
                            format!("interface '{}' cannot extend itself", iname),
                            parent_ref.span,
                        );
                    } else if !rp.interfaces[idx].parents.contains(&pid) {
                        rp.interfaces[idx].parents.push(pid);
                        rp.interfaces[idx].parent_bindings.push((pid, pargs));
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

    // Class inheritance is removed, so no class inheritance cycles exist.

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

    // Transitive interface closure from direct interfaces and their
    // interface parents only. Composition never contributes interfaces.
    for idx in 0..rp.classes.len() {
        let mut bindings: Vec<(u32, Vec<BaseType>)> = rp.classes[idx].interface_bindings.clone();
        let mut queue = bindings.clone();
        while let Some((ii, args)) = queue.pop() {
            let iface = &rp.interfaces[ii as usize];
            let subst: Vec<Option<BaseType>> = args.iter().cloned().map(Some).collect();
            for (pid, pargs) in &iface.parent_bindings {
                if !bindings.iter().any(|(id, _)| id == pid) {
                    let sub: Vec<BaseType> = pargs.iter().map(|a| a.substitute(&subst)).collect();
                    bindings.push((*pid, sub.clone()));
                    queue.push((*pid, sub));
                }
            }
        }
        rp.classes[idx].all_interfaces = bindings.iter().map(|(id, _)| *id).collect();
        rp.classes[idx].interface_bindings = bindings;
    }
    // BFS so nearer ancestors precede farther ones: index order is a
    // specificity order (direct parents before grandparents), which
    // default-method resolution relies on.
    for idx in 0..rp.interfaces.len() {
        let mut acc: Vec<u32> = vec![];
        let mut queue: std::collections::VecDeque<u32> =
            rp.interfaces[idx].parents.iter().copied().collect();
        while let Some(p) = queue.pop_front() {
            if !acc.contains(&p) {
                acc.push(p);
                for gp in &rp.interfaces[p as usize].parents {
                    if !acc.contains(gp) && !queue.contains(gp) {
                        queue.push_back(*gp);
                    }
                }
            }
        }
        rp.interfaces[idx].all_parents = acc;
    }

    // Transitive interface bindings: for every ancestor, the resolved type
    // arguments expressed in this interface's own type-parameter space.
    for idx in 0..rp.interfaces.len() {
        let mut bindings: Vec<(u32, Vec<BaseType>)> = vec![];
        let mut queue: Vec<(u32, Vec<BaseType>)> = rp.interfaces[idx].parent_bindings.clone();
        while let Some((pid, pargs)) = queue.pop() {
            if bindings.iter().any(|(id, _)| *id == pid) {
                continue;
            }
            bindings.push((pid, pargs.clone()));
            let subst: Vec<Option<BaseType>> = pargs.iter().cloned().map(Some).collect();
            for (gid, gargs) in &rp.interfaces[pid as usize].parent_bindings {
                if !bindings.iter().any(|(id, _)| *id == *gid) {
                    let sub: Vec<BaseType> = gargs.iter().map(|a| a.substitute(&subst)).collect();
                    queue.push((*gid, sub));
                }
            }
        }
        rp.interfaces[idx].all_parent_bindings = bindings;
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

    // ---- 3. Class-local field slots and direct method names ------------
    // Instance fields are class-local: slot 0..N-1 are exactly this
    // class's own instance fields. There is no inherited offset prefix.
    // Static fields get their own slot vector; they are not instance slots.
    for idx in 0..rp.classes.len() {
        let cid = rp.classes[idx].id;
        let cname = rp.classes[idx].name.clone();
        let def_fields = rp.classes[idx].def.fields.clone();
        // A class may not declare two fields with the same name, and a
        // static field may not share a name with an instance field.
        let mut seen: HashMap<String, Span> = HashMap::new();
        for f in &def_fields {
            if let Some(_prev) = seen.insert(f.name.clone(), f.span) {
                diags.err_at(
                    "C230",
                    format!("duplicate field '{}' in class '{}'", f.name, cname),
                    f.span,
                );
            }
        }
        let mut fields: Vec<FieldInfo> = vec![];
        let mut static_fields: Vec<FieldInfo> = vec![];
        for f in &def_fields {
            let info = FieldInfo {
                name: f.name.clone(),
                ty: Ty::non_null(BaseType::Object), // filled by type pass
                mutable: f.mutable,
                declaring: cid,
                is_static: f.is_static,
            };
            if f.is_static {
                static_fields.push(info);
            } else {
                fields.push(info);
            }
        }
        let mut method_names: Vec<String> = vec![];
        for m in &rp.classes[idx].def.methods {
            if !m.is_static && !method_names.contains(&m.name) {
                method_names.push(m.name.clone());
            }
        }
        let methods: Vec<MethodInfo> = rp.classes[idx]
            .def
            .methods
            .iter()
            .map(MethodInfo::from_def)
            .collect();
        rp.classes[idx].fields = fields;
        rp.classes[idx].static_fields = static_fields;
        rp.classes[idx].method_names = method_names;
        rp.classes[idx].methods = methods;
    }

    // ---- 3b. Type pass: fill field/param/return types -------------------
    resolve_types(&mut rp, diags);

    // ---- 3c. Synthetic delegation wrappers and effective methods --------
    build_effective_methods(&mut rp, diags);

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
        let cname = rp.classes[idx].name.clone();
        let ctparams = param_names(&rp.classes[idx].type_params);
        // Phase 1: resolve into locals (no live borrows of rp).
        let field_tys: Vec<Option<Ty>> = rp.classes[idx]
            .def
            .fields
            .iter()
            .map(|f| resolve_one(rp, diags, Some(cid), &ctparams, &f.ty))
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
        // Phase 2: assign. Instance and static fields share declaration
        // order with `def.fields` but live in separate slot vectors.
        let decls: Vec<(String, bool, Span)> = rp.classes[idx]
            .def
            .fields
            .iter()
            .map(|f| (f.name.clone(), f.is_static, f.span))
            .collect();
        let mut inst = 0usize;
        let mut stat = 0usize;
        for ((_, is_static, _), ty) in decls.iter().zip(&field_tys) {
            if let Some(t) = ty {
                if *is_static {
                    rp.classes[idx].static_fields[stat].ty = t.clone();
                } else {
                    rp.classes[idx].fields[inst].ty = t.clone();
                }
            }
            if *is_static {
                stat += 1;
            } else {
                inst += 1;
            }
        }
        // Static fields are erased per class: their declared type may not
        // mention the class's own type parameters.
        for f in &rp.classes[idx].static_fields {
            if f.ty.contains_type_var() {
                let span = decls
                    .iter()
                    .find(|(name, _, _)| name == &f.name)
                    .map(|(_, _, sp)| *sp)
                    .unwrap_or(rp.classes[idx].def.span);
                diags.err_at(
                    "C231",
                    format!(
                        "static field '{}' of class '{}' may not use the class's type parameters",
                        f.name, cname
                    ),
                    span,
                );
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
// Method validation: duplicates, conformance, entry point
// ---------------------------------------------------------------------------

fn validate_methods(rp: &mut ResolvedProgram, diags: &mut Diagnostics) {
    for idx in 0..rp.classes.len() {
        let cname = rp.classes[idx].name.clone();
        let def_methods = rp.classes[idx].def.methods.clone();
        for m in &def_methods {
            if m.is_static && m.name == "new" {
                let returns_self = matches!(
                    m.return_ty.as_ref().map(|t| &t.base),
                    Some(TypeBase::Named(name)) if name == "Self"
                );
                if !returns_self {
                    diags.err_at("C130", "constructor 'new' must return 'Self'", m.span);
                }
            }
        }
        // Duplicate method names within the class (name-based resolution,
        // not general overloading).
        let mut seen: HashMap<String, usize> = HashMap::new();
        for (midx, m) in def_methods.iter().enumerate() {
            let key = format!("{}:{}", m.name, m.is_static);
            match seen.entry(key) {
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
    }
}

/// Compute each class's effective instance-method implementations.
///
/// Precedence per contract:
/// explicit class method > explicit delegation > most-specific interface
/// default > compile-time error. Delegation is lowered to synthetic
/// forwarding `MethodInfo` entries so later phases only see ordinary methods.
fn build_effective_methods(rp: &mut ResolvedProgram, diags: &mut Diagnostics) {
    for idx in 0..rp.classes.len() {
        synthesize_delegates(rp, diags, idx);
    }
    for idx in 0..rp.classes.len() {
        compute_effective_methods(rp, diags, idx);
    }
    validate_conformance(rp, diags);
}

/// Validate `delegate I to field` declarations and synthesize forwarding
/// methods for each interface slot not already implemented explicitly.
fn synthesize_delegates(rp: &mut ResolvedProgram, diags: &mut Diagnostics, idx: usize) {
    let cname = rp.classes[idx].name.clone();
    let delegates = rp.classes[idx].delegates.clone();
    let all_interfaces = rp.classes[idx].all_interfaces.clone();
    let mut delegated_ifaces: Vec<u32> = vec![];
    for d in &delegates {
        let iid = d.interface_id;
        let iname = rp.interface_name(iid).to_string();
        // 2. The interface must be in the class's effective `implements`
        //    closure; delegation never changes the public nominal type.
        if !all_interfaces.contains(&iid) {
            diags.err_at(
                "C220",
                format!(
                    "class '{}' delegates interface '{}' which is not in its 'implements' list",
                    cname, iname
                ),
                d.span,
            );
            continue;
        }
        // 17.1: the same interface delegated twice is rejected outright.
        if delegated_ifaces.contains(&iid) {
            diags.err_at(
                "C221",
                format!(
                    "interface '{}' is already delegated; remove the duplicate 'delegate' declaration",
                    iname
                ),
                d.span,
            );
            continue;
        }
        delegated_ifaces.push(iid);
        // 3/4/5: the target must be a direct, non-nullable instance field.
        let field = match rp.classes[idx]
            .fields
            .iter()
            .find(|f| f.name == d.target_field)
        {
            Some(f) => (f.ty.clone(), f.name.clone()),
            None => {
                if rp.classes[idx]
                    .static_fields
                    .iter()
                    .any(|f| f.name == d.target_field)
                {
                    diags.err_at(
                        "C232",
                        format!(
                            "delegate target '{}' is a static field; delegation requires an instance field",
                            d.target_field
                        ),
                        d.span,
                    );
                } else {
                    diags.err_at(
                        "C222",
                        format!(
                            "delegate target field '{}' does not exist in class '{}'",
                            d.target_field, cname
                        ),
                        d.span,
                    );
                }
                continue;
            }
        };
        let field_slot = rp.classes[idx]
            .fields
            .iter()
            .position(|f| f.name == d.target_field)
            .unwrap() as u16;
        if field.0.nullable {
            diags.err_at(
                "C223",
                format!(
                    "delegate target field '{}' must be non-nullable",
                    d.target_field
                ),
                d.span,
            );
            continue;
        }
        // 6/7: the field's declared static type must conform to `I`. A bare
        // `Object` never conforms; a type variable conforms only through a
        // nominal constraint that already conforms to `I`.
        let field_conforms = match &field.0.base {
            BaseType::TypeVar(k) => type_var_conforms(rp, diags, idx, *k, iid, &d.interface_args),
            _ => conforms_to_interface(&field.0, iid, &d.interface_args, rp),
        };
        if !field_conforms {
            diags.err_at(
                "C224",
                format!(
                    "delegate target '{}' of type {} does not implement interface '{}'",
                    d.target_field,
                    ty_name(rp, &field.0),
                    iname
                ),
                d.span,
            );
            continue;
        }
        let slots: Vec<(u16, String)> = rp.interfaces[iid as usize]
            .slots
            .iter()
            .enumerate()
            .map(|(i, s)| (i as u16, s.name.clone()))
            .collect();
        for (slot_idx, slot_name) in slots {
            // Explicit class method always wins over delegation (section 15).
            if rp.classes[idx]
                .methods
                .iter()
                .any(|m| m.name == slot_name && !m.is_static && !m.is_delegate())
            {
                continue;
            }
            // A wrapper from an earlier delegate: reusing the same field is
            // fine; a different field is an unresolved conflict (17.2).
            if let Some(existing) = rp.classes[idx]
                .methods
                .iter()
                .position(|m| m.name == slot_name && m.is_delegate())
            {
                let existing_field = rp.classes[idx].methods[existing]
                    .delegate
                    .as_ref()
                    .map(|dt| dt.field.clone())
                    .unwrap_or_default();
                if existing_field != d.target_field {
                    diags.err_at(
                        "C225",
                        format!(
                            "method '{}' has conflicting delegated implementations from fields '{}' and '{}'; declare it explicitly",
                            slot_name, existing_field, d.target_field
                        ),
                        d.span,
                    );
                }
                continue;
            }
            let (mut params, mut ret, type_params) = canonical_signature(rp, iid, &slot_name);
            // Instantiate the interface's type variables with the delegate
            // declaration's type arguments so the wrapper carries concrete
            // parameter and return types (the method's own type parameters,
            // indexed after the interface's, are left for call-site inference).
            if !d.interface_args.is_empty() {
                let subst: Vec<Option<BaseType>> =
                    d.interface_args.iter().cloned().map(Some).collect();
                for p in &mut params {
                    p.ty = p.ty.substitute(&subst);
                }
                if let Some(r) = &mut ret {
                    *r = r.substitute(&subst);
                }
            }
            let m = MethodInfo {
                name: slot_name.clone(),
                visibility: Visibility::Public,
                is_static: false,
                type_params,
                params,
                return_ty: ret,
                body: None,
                delegate: Some(DelegateTarget {
                    field: d.target_field.clone(),
                    field_slot,
                    interface: iid,
                    iface_slot: slot_idx,
                }),
                span: d.span,
            };
            rp.classes[idx].methods.push(m);
            if !rp.classes[idx].method_names.contains(&slot_name) {
                rp.classes[idx].method_names.push(slot_name);
            }
        }
    }
}

/// Build the ordered effective-method map for one class, filling in
/// interface defaults for contracts not supplied explicitly or by a
/// delegate.
fn compute_effective_methods(rp: &mut ResolvedProgram, diags: &mut Diagnostics, idx: usize) {
    let all_interfaces = rp.classes[idx].all_interfaces.clone();
    let class_span = rp.classes[idx].def.span;
    let mut eff: Vec<EffectiveMethod> = vec![];
    for (mi, m) in rp.classes[idx].methods.iter().enumerate() {
        if m.is_static {
            continue;
        }
        if eff.iter().any(|e| e.name == m.name) {
            continue;
        }
        eff.push(EffectiveMethod {
            name: m.name.clone(),
            class_method: Some(mi),
            default: None,
            is_public: m.visibility == Visibility::Public,
        });
    }
    for iid in &all_interfaces {
        let slots: Vec<String> = rp.interfaces[*iid as usize]
            .slots
            .iter()
            .map(|s| s.name.clone())
            .collect();
        for name in slots {
            if eff.iter().any(|e| e.name == name) {
                continue;
            }
            if let Some((did, didx)) = most_specific_default(rp, *iid, &name) {
                // 17.4: unrelated competing defaults require an explicit
                // class method.
                if default_conflict(rp, &all_interfaces, &name) {
                    diags.err_at(
                        "C124",
                        format!(
                            "class '{}' has conflicting default implementations of '{}'; declare the method explicitly",
                            rp.classes[idx].name, name
                        ),
                        class_span,
                    );
                }
                eff.push(EffectiveMethod {
                    name: name.clone(),
                    class_method: None,
                    default: Some((did, didx)),
                    is_public: true,
                });
            } else {
                eff.push(EffectiveMethod {
                    name: name.clone(),
                    class_method: None,
                    default: None,
                    is_public: true,
                });
            }
        }
    }
    rp.classes[idx].effective_methods = eff;
}

/// Check every interface requirement against the class's effective
/// implementation, validating visibility and signature compatibility.
fn validate_conformance(rp: &mut ResolvedProgram, diags: &mut Diagnostics) {
    for idx in 0..rp.classes.len() {
        let cname = rp.classes[idx].name.clone();
        let all_interfaces = rp.classes[idx].all_interfaces.clone();
        let eff = rp.classes[idx].effective_methods.clone();
        for iid in &all_interfaces {
            let iface_name = rp.interface_name(*iid).to_string();
            let slots: Vec<String> = rp.interfaces[*iid as usize]
                .slots
                .iter()
                .map(|s| s.name.clone())
                .collect();
            for slot_name in slots {
                let e = match eff.iter().find(|e| e.name == slot_name) {
                    Some(e) => e.clone(),
                    None => continue,
                };
                if let Some(mi) = e.class_method {
                    let m = rp.classes[idx].methods[mi].clone();
                    if m.visibility != Visibility::Public {
                        diags.err_at(
                            "C122",
                            format!(
                                "class '{}' satisfies interface '{}' with non-public method '{}'; declare it 'public'",
                                cname, iface_name, slot_name
                            ),
                            m.span,
                        );
                    }
                    let (req_params, req_ret, _) = canonical_signature(rp, *iid, &slot_name);
                    check_signature(
                        rp,
                        diags,
                        &cname,
                        &slot_name,
                        &iface_name,
                        &m,
                        &req_params,
                        &req_ret,
                    );
                } else if e.default.is_none() {
                    diags.err_at(
                        "C123",
                        format!(
                            "class '{}' does not implement required method '{}' of interface '{}'",
                            cname, slot_name, iface_name
                        ),
                        rp.classes[idx].def.span,
                    );
                }
            }
        }
    }
}

/// Most-specific default implementation for `name` in interface `iid`,
/// preferring the interface itself then its parents nearest-first.
fn most_specific_default(rp: &ResolvedProgram, iid: u32, name: &str) -> Option<(u32, usize)> {
    let iface = &rp.interfaces[iid as usize];
    let mut order: Vec<u32> = vec![iid];
    for p in &iface.all_parents {
        order.push(*p);
    }
    for oiid in order {
        if let Some(idx) = rp.interfaces[oiid as usize]
            .methods
            .iter()
            .position(|m| m.name == name && m.body.is_some())
        {
            return Some((oiid, idx));
        }
    }
    iface
        .slots
        .iter()
        .find(|s| s.name == name)
        .and_then(|s| s.default)
}

/// True when unrelated interfaces in the class's closure declare competing
/// defaults for `name` and no single provider is more specific than every
/// other. Two providers compete when neither extends the other; a set of
/// providers is unambiguous only when they form a total chain under
/// `extends`, so the deepest provider wins deterministically.
fn default_conflict(rp: &ResolvedProgram, all_interfaces: &[u32], name: &str) -> bool {
    let providers: Vec<u32> = all_interfaces
        .iter()
        .copied()
        .filter(|ii| {
            rp.interfaces[*ii as usize]
                .methods
                .iter()
                .any(|m| m.name == name && m.body.is_some())
        })
        .collect();
    if providers.len() < 2 {
        return false;
    }
    for (i, p) in providers.iter().enumerate() {
        for q in &providers[i + 1..] {
            if !rp.interface_extends(*p, *q) && !rp.interface_extends(*q, *p) {
                return true;
            }
        }
    }
    false
}

/// Canonical signature (params, return, type params) for an interface
/// contract: the most specific default, otherwise the requirement
/// declaration carried by the slot.
fn canonical_signature(
    rp: &ResolvedProgram,
    iid: u32,
    name: &str,
) -> (Vec<ParamInfo>, Option<Ty>, Vec<TypeParam>) {
    let iface = &rp.interfaces[iid as usize];
    let mut order: Vec<u32> = vec![iid];
    for p in &iface.all_parents {
        order.push(*p);
    }
    for oiid in order {
        if let Some(m) = rp.interfaces[oiid as usize]
            .methods
            .iter()
            .find(|m| m.name == name && m.body.is_some())
        {
            return (m.params.clone(), m.return_ty.clone(), m.type_params.clone());
        }
    }
    if let Some(slot) = iface.slots.iter().find(|s| s.name == name) {
        if let Some((did, didx)) = slot.decl {
            if let Some(m) = rp.interfaces[did as usize].methods.get(didx) {
                return (m.params.clone(), m.return_ty.clone(), m.type_params.clone());
            }
        }
    }
    (vec![], None, vec![])
}

/// Soundness check of an implementing method against an interface
/// requirement (section 18).
#[allow(clippy::too_many_arguments)]
fn check_signature(
    rp: &ResolvedProgram,
    diags: &mut Diagnostics,
    cname: &str,
    method: &str,
    iname: &str,
    m: &MethodInfo,
    req_params: &[ParamInfo],
    req_ret: &Option<Ty>,
) {
    let concrete = |t: &Ty| !t.contains_type_var();
    if req_params.len() != m.params.len() {
        diags.err_at(
            "C125",
            format!(
                "method '{}' in class '{}' takes {} parameter(s) but interface '{}' requires {}",
                method,
                cname,
                m.params.len(),
                iname,
                req_params.len()
            ),
            m.span,
        );
        return;
    }
    for (pi, (req, prov)) in req_params.iter().zip(&m.params).enumerate() {
        if concrete(&req.ty)
            && concrete(&prov.ty)
            && !crate::types::is_subtype(&req.ty, &prov.ty, rp)
        {
            diags.err_at(
                "C126",
                format!(
                    "parameter {} of method '{}' in class '{}' has type {} but interface '{}' requires {} (or a supertype)",
                    pi + 1,
                    method,
                    cname,
                    ty_name(rp, &prov.ty),
                    iname,
                    ty_name(rp, &req.ty)
                ),
                m.span,
            );
        }
    }
    let req_void = req_ret
        .as_ref()
        .is_none_or(|t| matches!(t.base, BaseType::Void));
    let prov_void = m
        .return_ty
        .as_ref()
        .is_none_or(|t| matches!(t.base, BaseType::Void));
    match (req_void, prov_void, req_ret, &m.return_ty) {
        (true, false, _, _) => diags.err_at(
            "C127",
            format!(
                "method '{}' in class '{}' returns a value but interface '{}' declares no return value",
                method, cname, iname
            ),
            m.span,
        ),
        (false, true, _, _) => diags.err_at(
            "C127",
            format!(
                "method '{}' in class '{}' returns nothing but interface '{}' requires a value",
                method, cname, iname
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
                    ty_name(rp, prov_t),
                    method,
                    cname,
                    ty_name(rp, req_t),
                    iname
                ),
                m.span,
            );
        }
        _ => {}
    }
}

/// Typed interface conformance used by delegation validation. Unlike the
/// id-only subtype oracle, this compares resolved generic arguments.
fn conforms_to_interface(ty: &Ty, iid: u32, iargs: &[BaseType], rp: &ResolvedProgram) -> bool {
    match &ty.base {
        BaseType::Class(c, cargs) => {
            match rp.classes[*c as usize]
                .interface_bindings
                .iter()
                .find(|(id, _)| *id == iid)
            {
                Some((_, bargs)) => {
                    let subst: Vec<Option<BaseType>> = cargs.iter().cloned().map(Some).collect();
                    let sub: Vec<BaseType> = bargs.iter().map(|a| a.substitute(&subst)).collect();
                    sub == iargs
                }
                None => false,
            }
        }
        BaseType::Interface(i, iargs2) => {
            let bargs: Vec<BaseType> = if *i == iid {
                (0..rp.interfaces[*i as usize].type_params.len())
                    .map(|k| BaseType::TypeVar(k as u32))
                    .collect()
            } else {
                match rp.interfaces[*i as usize]
                    .all_parent_bindings
                    .iter()
                    .find(|(id, _)| *id == iid)
                {
                    Some((_, a)) => a.clone(),
                    None => return false,
                }
            };
            let subst: Vec<Option<BaseType>> = iargs2.iter().cloned().map(Some).collect();
            let sub: Vec<BaseType> = bargs.iter().map(|a| a.substitute(&subst)).collect();
            sub == *iargs
        }
        BaseType::TypeVar(_) | BaseType::Object => {
            // Neither a bare type variable nor `Object` guarantees
            // conformance; a type variable is handled through its nominal
            // constraints by the caller.
            false
        }
        _ => false,
    }
}

/// Whether a class type variable's nominal constraints guarantee
/// conformance to the delegated interface.
fn type_var_conforms(
    rp: &ResolvedProgram,
    diags: &mut Diagnostics,
    class_idx: usize,
    var: u32,
    iid: u32,
    iargs: &[BaseType],
) -> bool {
    let tp = match rp.classes[class_idx].type_params.get(var as usize) {
        Some(tp) => tp,
        None => return false,
    };
    let tparams = param_names(&rp.classes[class_idx].type_params);
    tp.constraints.iter().any(|cref| {
        let r = {
            let mut ctx = TypeCtx {
                program: rp,
                diags,
                class_id: Some(rp.classes[class_idx].id),
                type_params: tparams.clone(),
            };
            ctx.resolve(cref)
        };
        matches!(
            &r,
            Some(BaseType::Interface(jid, jargs))
                if conforms_to_interface(
                    &Ty::non_null(BaseType::Interface(*jid, jargs.clone())),
                    iid,
                    iargs,
                    rp
                )
        )
    })
}

fn ty_name(rp: &ResolvedProgram, t: &Ty) -> String {
    format!(
        "{}{}",
        rp.type_name(&t.base),
        if t.nullable { "?" } else { "" }
    )
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
        diags.err_at("C202", "entry point 'Main.run' must be static", m.span);
        return;
    }
    if !m.is_public {
        diags.err_at(
            "C203",
            "entry point 'Main.run' must be public ('public')",
            m.span,
        );
        return;
    }
    if m.params.len() != 1 || !m.params[0].variadic || m.params[0].default.is_some() {
        diags.err_at(
            "C204",
            "entry point 'Main.run' must take exactly one variadic parameter 'args: String...'",
            m.span,
        );
        return;
    }
    rp.entry = Some((main.id, run));
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::lexer::Lexer;
    use crate::parser::Parser;

    fn resolve_src(text: &str) -> (ResolvedProgram, Diagnostics) {
        let mut diags = Diagnostics::default();
        let tokens = Lexer::new(0, text).tokenize(&mut diags);
        let mut p = Parser::new(tokens);
        let program = p.parse_program().expect("program should parse");
        diags.items.append(&mut p.diags.items);
        let rp = resolve_program(&program, &mut diags);
        (rp, diags)
    }

    fn class<'a>(rp: &'a ResolvedProgram, name: &str) -> &'a ClassInfo {
        rp.classes.iter().find(|c| c.name == name).unwrap()
    }

    #[test]
    fn interface_closure_has_no_class_parents() {
        let (rp, d) = resolve_src(
            "package m\n\
             interface A { a(): Long }\n\
             interface B extends A { b(): Long }\n\
             class C implements B {\n\
                 public a(): Long { return 1 }\n\
                 public b(): Long { return 2 }\n\
             }\n\
             class Main { public static run(args: String...): Long { return 0 } }",
        );
        assert!(!d.has_errors(), "{:?}", d.items);
        let c = class(&rp, "C");
        assert!(c.all_interfaces.contains(&rp.lookup_item("A").unwrap().1));
        assert!(c.all_interfaces.contains(&rp.lookup_item("B").unwrap().1));
        // Field slots are class-local; declaring class is the class itself.
        assert!(c.fields.iter().all(|f| f.declaring == c.id));
    }

    #[test]
    fn delegate_lowers_to_synthetic_method() {
        let (rp, d) = resolve_src(
            "package m\n\
             interface I { f(): Long }\n\
             class A implements I {\n\
                 public static new(): Self { return Self {} }\n\
                 public f(): Long { return 1 }\n\
             }\n\
             class C implements I {\n\
                 a: A\n\
                 delegate I to a\n\
                 public static new(): Self { return Self { a: A.new(), } }\n\
             }\n\
             class Main { public static run(args: String...): Long { return 0 } }",
        );
        assert!(!d.has_errors(), "{:?}", d.items);
        let c = class(&rp, "C");
        let e = c.find_effective("f").unwrap();
        let mi = e.class_method.expect("delegation must supply f");
        assert!(c.methods[mi].is_delegate());
        assert!(c.method_names.contains(&"f".to_string()));
    }

    #[test]
    fn explicit_method_beats_delegation() {
        let (rp, d) = resolve_src(
            "package m\n\
             interface I { f(): Long }\n\
             class A implements I {\n\
                 public static new(): Self { return Self {} }\n\
                 public f(): Long { return 1 }\n\
             }\n\
             class C implements I {\n\
                 a: A\n\
                 delegate I to a\n\
                 public f(): Long { return 2 }\n\
                 public static new(): Self { return Self { a: A.new(), } }\n\
             }\n\
             class Main { public static run(args: String...): Long { return 0 } }",
        );
        assert!(!d.has_errors(), "{:?}", d.items);
        let c = class(&rp, "C");
        let mi = c.find_effective("f").unwrap().class_method.unwrap();
        assert!(!c.methods[mi].is_delegate());
    }

    #[test]
    fn conflicting_delegates_are_rejected() {
        let (_rp, d) = resolve_src(
            "package m\n\
             interface A { value(): String }\n\
             interface B { value(): String }\n\
             class AImpl implements A { public static new(): Self { return Self {} } public value(): String { return \"a\" } }\n\
             class BImpl implements B { public static new(): Self { return Self {} } public value(): String { return \"b\" } }\n\
             class X implements A, B {\n\
                 a: AImpl\n\
                 b: BImpl\n\
                 delegate A to a\n\
                 delegate B to b\n\
                 public static new(): Self { return Self { a: AImpl.new(), b: BImpl.new(), } }\n\
             }\n\
             class Main { public static run(args: String...): Long { return 0 } }",
        );
        assert!(
            d.items.iter().any(|x| x.code == "C225"),
            "expected a delegation conflict: {:?}",
            d.items
        );
    }

    #[test]
    fn nullable_delegate_is_rejected() {
        let (_rp, d) = resolve_src(
            "package m\n\
             interface I { f(): Long }\n\
             class C implements I {\n\
                 a: I?\n\
                 delegate I to a\n\
             }\n\
             class Main { public static run(args: String...): Long { return 0 } }",
        );
        assert!(
            d.items.iter().any(|x| x.code == "C223"),
            "expected nullable-delegate error: {:?}",
            d.items
        );
    }

    #[test]
    fn generic_delegation_substitution_is_checked() {
        let (_rp, d) = resolve_src(
            "package m\n\
             interface Source<T> { get(): T }\n\
             class LongSource implements Source<Long> {\n\
                 public static new(): Self { return Self {} }\n\
                 public get(): Long { return 1 }\n\
             }\n\
             class Wrapper implements Source<String> {\n\
                 s: LongSource\n\
                 delegate Source<String> to s\n\
                 public static new(): Self { return Self { s: LongSource.new(), } }\n\
             }\n\
             class Main { public static run(args: String...): Long { return 0 } }",
        );
        assert!(
            d.items.iter().any(|x| x.code == "C224"),
            "expected generic conformance error: {:?}",
            d.items
        );
    }

    #[test]
    fn competing_sibling_defaults_are_rejected() {
        // B.f and C.f come from unrelated interfaces; neither provider is
        // more specific than the other, so an explicit method is required.
        let (_rp, d) = resolve_src(
            "package m\n\
             interface A { f(): Long { return 1 } }\n\
             interface B extends A { f(): Long { return 2 } }\n\
             interface C extends A { f(): Long { return 3 } }\n\
             class X implements B, C {\n\
                 public static new(): Self { return Self {} }\n\
             }\n\
             class Main { public static run(args: String...): Long { return 0 } }",
        );
        assert!(
            d.items.iter().any(|x| x.code == "C124"),
            "expected competing-default error: {:?}",
            d.items
        );
    }

    #[test]
    fn chained_defaults_resolve_to_most_specific() {
        // A single extends-chain is unambiguous: the deepest provider wins.
        let (rp, d) = resolve_src(
            "package m\n\
             interface A { f(): Long { return 1 } }\n\
             interface B extends A { f(): Long { return 2 } }\n\
             class X implements B {\n\
                 public static new(): Self { return Self {} }\n\
             }\n\
             class Main { public static run(args: String...): Long { return 0 } }",
        );
        assert!(!d.has_errors(), "{:?}", d.items);
        let e = class(&rp, "X").find_effective("f").unwrap();
        let (did, _) = e.default.expect("default must supply f");
        assert_eq!(did, rp.lookup_item("B").unwrap().1);
    }

    #[test]
    fn inherited_single_default_is_not_a_conflict() {
        // B and C extend A but only A provides the default: one provider,
        // no conflict.
        let (_rp, d) = resolve_src(
            "package m\n\
             interface A { f(): Long { return 1 } }\n\
             interface B extends A { }\n\
             interface C extends A { }\n\
             class X implements B, C {\n\
                 public static new(): Self { return Self {} }\n\
             }\n\
             class Main { public static run(args: String...): Long { return 0 } }",
        );
        assert!(!d.has_errors(), "{:?}", d.items);
    }

    #[test]
    fn effective_method_resolution_is_deterministic() {
        let src = "package m\n\
             interface I { f(): Long }\n\
             class A implements I {\n\
                 public static new(): Self { return Self {} }\n\
                 public f(): Long { return 1 }\n\
             }\n\
             class C implements I {\n\
                 a: A\n\
                 delegate I to a\n\
                 public static new(): Self { return Self { a: A.new(), } }\n\
             }\n\
             class Main { public static run(args: String...): Long { return 0 } }";
        let (rp1, _) = resolve_src(src);
        let (rp2, _) = resolve_src(src);
        let names1: Vec<&str> = class(&rp1, "C")
            .effective_methods
            .iter()
            .map(|e| e.name.as_str())
            .collect();
        let names2: Vec<&str> = class(&rp2, "C")
            .effective_methods
            .iter()
            .map(|e| e.name.as_str())
            .collect();
        assert_eq!(names1, names2);
        assert_eq!(names1, vec!["f"]);
    }

    #[test]
    fn static_fields_get_their_own_slot_namespace() {
        let (rp, d) = resolve_src(
            "package m\n\
             class A {\n\
                 x: Long\n\
                 static mutable n: Long = 0\n\
             }\n\
             class Main { public static run(args: String...): Long { return 0 } }",
        );
        assert!(!d.has_errors(), "{:?}", d.items);
        let c = class(&rp, "A");
        assert_eq!(c.fields.len(), 1);
        assert_eq!(c.fields[0].name, "x");
        assert!(!c.fields[0].is_static);
        assert_eq!(c.static_fields.len(), 1);
        assert_eq!(c.static_fields[0].name, "n");
        assert!(c.static_fields[0].is_static && c.static_fields[0].mutable);
        assert_eq!(c.static_fields[0].declaring, c.id);
    }

    #[test]
    fn rejects_duplicate_field_names_and_static_instance_collisions() {
        for src in [
            "package m\nclass A { x: Long\nstatic x: Long = 0 }\nclass Main { public static run(args: String...): Long { return 0 } }",
            "package m\nclass A { x: Long\nx: Long }\nclass Main { public static run(args: String...): Long { return 0 } }",
            "package m\nclass A { static x: Long = 0\nstatic x: Long = 1 }\nclass Main { public static run(args: String...): Long { return 0 } }",
        ] {
            let (_rp, d) = resolve_src(src);
            assert!(
                d.items.iter().any(|x| x.code == "C230"),
                "expected duplicate-field error: {:?}",
                d.items
            );
        }
    }

    #[test]
    fn rejects_class_type_params_in_static_field_types() {
        let (_rp, d) = resolve_src(
            "package m\n\
             class Box<T> {\n\
                 static item: T = null\n\
             }\n\
             class Main { public static run(args: String...): Long { return 0 } }",
        );
        assert!(
            d.items.iter().any(|x| x.code == "C231"),
            "expected type-parameter error: {:?}",
            d.items
        );
    }

    #[test]
    fn rejects_delegate_to_static_field() {
        let (_rp, d) = resolve_src(
            "package m\n\
             interface I { f(): Long }\n\
             class A implements I {\n\
                 static mutable x: Long = 1\n\
                 delegate I to x\n\
             }\n\
             class Main { public static run(args: String...): Long { return 0 } }",
        );
        assert!(
            d.items.iter().any(|x| x.code == "C232"),
            "expected static-delegate error: {:?}",
            d.items
        );
    }
}
