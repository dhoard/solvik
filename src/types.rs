//! Solvik static type representation.
//
// Opcode table and type constructors are retained as architectural
// surface even when not every entry is emitted yet.
#![allow(dead_code)]
//!
//! Types are either nullable or not; nullability is orthogonal to the base
//! type. Subtyping:
//! - `T` is a subtype of `T?`
//! - numeric widening: `Byte <: Int <: Float`
//! - class `C` is a subtype of its parent class and of every interface it
//!   (transitively) implements
//! - `Null` is a subtype of every nullable type
//! - `Object` accepts every value

use std::fmt;

#[derive(Clone, PartialEq, Eq, Hash, Debug)]
pub enum BaseType {
    Bool,
    Byte,
    Int,
    Float,
    Char,
    String,
    Object,
    Void,
    Null,
    List(Box<BaseType>),
    Map(Box<BaseType>, Box<BaseType>),
    Stack(Box<BaseType>),
    Set(Box<BaseType>),
    /// User class: id + invariant type arguments.
    Class(u32, Vec<BaseType>),
    /// User interface: id + invariant type arguments.
    Interface(u32, Vec<BaseType>),
    /// User enum: id + invariant type arguments.
    Enum(u32, Vec<BaseType>),
    /// Unresolved type variable (index into the enclosing declaration's
    /// type-parameter list).
    TypeVar(u32),
    /// Compiler-owned runtime object kinds (Thread, Mutex, ...).
    Native(u8),
    /// Internal: integer range produced by `a..b`.
    Range,
}

/// Native object kind tags.
pub mod native_kind {
    pub const THREAD: u8 = 0;
    pub const MUTEX: u8 = 1;
    pub const SEMAPHORE: u8 = 2;
    pub const PROCESS: u8 = 3;
    pub const REGEX: u8 = 4;
    pub const STREAM: u8 = 5;
}

impl BaseType {
    pub fn native(kind: u8) -> Self {
        BaseType::Native(kind)
    }

    pub fn list(elem: BaseType) -> Self {
        BaseType::List(Box::new(elem))
    }

    pub fn map(k: BaseType, v: BaseType) -> Self {
        BaseType::Map(Box::new(k), Box::new(v))
    }

    pub fn stack(elem: BaseType) -> Self {
        BaseType::Stack(Box::new(elem))
    }

    pub fn class(id: u32, args: Vec<BaseType>) -> Self {
        BaseType::Class(id, args)
    }

    pub fn interface(id: u32, args: Vec<BaseType>) -> Self {
        BaseType::Interface(id, args)
    }

    pub fn enum_(id: u32, args: Vec<BaseType>) -> Self {
        BaseType::Enum(id, args)
    }

    /// Number of type parameters this base type declares.
    pub fn arity(&self) -> usize {
        match self {
            BaseType::List(_) | BaseType::Stack(_) | BaseType::Set(_) => 1,
            BaseType::Map(_, _) => 2,
            BaseType::Class(_, a) | BaseType::Interface(_, a) | BaseType::Enum(_, a) => a.len(),
            _ => 0,
        }
    }

    /// Apply a substitution (type-var index -> replacement) to this type.
    pub fn substitute(&self, subst: &[Option<BaseType>]) -> BaseType {
        match self {
            BaseType::TypeVar(i) => subst
                .get(*i as usize)
                .and_then(|s| s.clone())
                .unwrap_or(BaseType::Object),
            BaseType::List(e) => BaseType::List(Box::new(e.substitute(subst))),
            BaseType::Map(k, v) => {
                BaseType::Map(Box::new(k.substitute(subst)), Box::new(v.substitute(subst)))
            }
            BaseType::Stack(e) => BaseType::Stack(Box::new(e.substitute(subst))),
            BaseType::Set(e) => BaseType::Set(Box::new(e.substitute(subst))),
            BaseType::Class(id, args)
            | BaseType::Interface(id, args)
            | BaseType::Enum(id, args) => {
                let args: Vec<BaseType> = args.iter().map(|a| a.substitute(subst)).collect();
                match self {
                    BaseType::Class(_, _) => BaseType::Class(*id, args),
                    BaseType::Interface(_, _) => BaseType::Interface(*id, args),
                    _ => BaseType::Enum(*id, args),
                }
            }
            other => other.clone(),
        }
    }

    /// True when the type contains any type variable.
    pub fn contains_type_var(&self) -> bool {
        match self {
            BaseType::TypeVar(_) => true,
            BaseType::List(e) => e.contains_type_var(),
            BaseType::Map(k, v) => k.contains_type_var() || v.contains_type_var(),
            BaseType::Stack(e) => e.contains_type_var(),
            BaseType::Set(e) => e.contains_type_var(),
            BaseType::Class(_, a) | BaseType::Interface(_, a) | BaseType::Enum(_, a) => {
                a.iter().any(BaseType::contains_type_var)
            }
            _ => false,
        }
    }
}

impl fmt::Display for BaseType {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            BaseType::Bool => write!(f, "Bool"),
            BaseType::Byte => write!(f, "Byte"),
            BaseType::Int => write!(f, "Int"),
            BaseType::Float => write!(f, "Float"),
            BaseType::Char => write!(f, "Char"),
            BaseType::String => write!(f, "String"),
            BaseType::Object => write!(f, "Object"),
            BaseType::Void => write!(f, "Void"),
            BaseType::Null => write!(f, "null"),
            BaseType::List(e) => write!(f, "List<{}>", e),
            BaseType::Map(k, v) => write!(f, "Map<{}, {}>", k, v),
            BaseType::Stack(e) => write!(f, "Stack<{}>", e),
            BaseType::Set(e) => write!(f, "Set<{}>", e),
            BaseType::Class(_, args) => write!(f, "<class{}>", type_args(args)),
            BaseType::Interface(_, args) => write!(f, "<interface{}>", type_args(args)),
            BaseType::Enum(_, args) => write!(f, "<enum{}>", type_args(args)),
            BaseType::TypeVar(i) => write!(f, "T{}", i),
            BaseType::Range => write!(f, "Range"),
            BaseType::Native(k) => {
                let n = match *k {
                    crate::types::native_kind::THREAD => "Thread",
                    crate::types::native_kind::MUTEX => "Mutex",
                    crate::types::native_kind::SEMAPHORE => "Semaphore",
                    crate::types::native_kind::PROCESS => "Process",
                    crate::types::native_kind::REGEX => "Regex",
                    _ => "Stream",
                };
                write!(f, "{}", n)
            }
        }
    }
}

fn type_args(args: &[BaseType]) -> String {
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

/// A type with its nullability.
#[derive(Clone, PartialEq, Eq, Hash, Debug)]
pub struct Ty {
    pub base: BaseType,
    pub nullable: bool,
}

impl Ty {
    pub fn new(base: BaseType, nullable: bool) -> Self {
        Ty { base, nullable }
    }

    pub fn non_null(base: BaseType) -> Self {
        Ty {
            base,
            nullable: false,
        }
    }

    pub fn nullable(base: BaseType) -> Self {
        Ty {
            base,
            nullable: true,
        }
    }

    pub fn bool_() -> Ty {
        Ty::non_null(BaseType::Bool)
    }
    pub fn byte() -> Ty {
        Ty::non_null(BaseType::Byte)
    }
    pub fn int() -> Ty {
        Ty::non_null(BaseType::Int)
    }
    pub fn float() -> Ty {
        Ty::non_null(BaseType::Float)
    }
    pub fn char_() -> Ty {
        Ty::non_null(BaseType::Char)
    }
    pub fn string() -> Ty {
        Ty::non_null(BaseType::String)
    }
    pub fn object() -> Ty {
        Ty::non_null(BaseType::Object)
    }
    pub fn void() -> Ty {
        Ty::non_null(BaseType::Void)
    }
    pub fn null() -> Ty {
        Ty::nullable(BaseType::Null)
    }

    pub fn substitute(&self, subst: &[Option<BaseType>]) -> Ty {
        Ty {
            base: self.base.substitute(subst),
            nullable: self.nullable,
        }
    }

    pub fn contains_type_var(&self) -> bool {
        self.base.contains_type_var()
    }

    /// Display name for diagnostics (uses registered names when available).
    pub fn display(&self, names: &dyn Fn(&BaseType) -> String) -> String {
        format!(
            "{}{}",
            names(&self.base),
            if self.nullable { "?" } else { "" }
        )
    }
}

impl fmt::Display for Ty {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}{}", self.base, if self.nullable { "?" } else { "" })
    }
}

/// `source` is assignable to `target`.
pub fn is_subtype(source: &Ty, target: &Ty, program: &dyn SubtypeOracle) -> bool {
    if source.base == target.base && source.nullable <= target.nullable {
        return true;
    }
    // Null flows into any nullable type.
    if matches!(source.base, BaseType::Null) && target.nullable {
        return true;
    }
    // Any accepts everything; everything is accepted by Any targets.
    if matches!(target.base, BaseType::Object) {
        return true;
    }
    // Numeric widening.
    match (&source.base, &target.base) {
        (BaseType::Byte, BaseType::Int)
        | (BaseType::Byte, BaseType::Float)
        | (BaseType::Int, BaseType::Float) => {
            return source.nullable <= target.nullable;
        }
        _ => {}
    }
    // Class inheritance and interface conformance.
    if let (BaseType::Class(c, cargs), BaseType::Class(p, pargs)) = (&source.base, &target.base) {
        if cargs == pargs && program.class_is_subclass_of(*c, *p) {
            return source.nullable <= target.nullable;
        }
    }
    if let (BaseType::Class(c, cargs), BaseType::Interface(i, iargs)) = (&source.base, &target.base)
    {
        if cargs == iargs && program.class_implements_interface(*c, *i) {
            return source.nullable <= target.nullable;
        }
    }
    if let (BaseType::Interface(s, sargs), BaseType::Interface(t, targs)) =
        (&source.base, &target.base)
    {
        if sargs == targs && program.interface_extends(*s, *t) {
            return source.nullable <= target.nullable;
        }
    }
    false
}

/// Oracle for nominal relationships, implemented by the resolver.
pub trait SubtypeOracle {
    fn class_is_subclass_of(&self, child: u32, ancestor: u32) -> bool;
    fn class_implements_interface(&self, class: u32, interface: u32) -> bool;
    fn interface_extends(&self, child: u32, ancestor: u32) -> bool;
}
