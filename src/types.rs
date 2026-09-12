//! Solvik static type representation.
//
// Opcode table and type constructors are retained as architectural
// surface even when not every entry is emitted yet.
#![allow(dead_code)]
//!
//! Types are either nullable or not; nullability is orthogonal to the base
//! type. Subtyping:
//! - `T` is a subtype of `T?`
//! - primitive numeric widening (Java-inspired):
//!   `Byte <: Short <: Integer <: Long <: Float <: Double`
//! - `BigInteger` and `BigDecimal` participate in no implicit widening;
//!   crossing their boundary requires an explicit conversion
//! - class `C` is a subtype of every interface it (transitively)
//!   implements; classes do not inherit from classes
//! - generic type arguments are invariant: `G<A> <: G<B>` only when
//!   `A == B` (including nullability)
//! - `Null` is a subtype of every nullable type
//! - `Object` accepts every value

use std::fmt;

#[derive(Clone, PartialEq, Eq, Hash, Debug)]
pub enum BaseType {
    Boolean,
    Byte,
    Short,
    Integer,
    Long,
    Float,
    Double,
    /// Arbitrary-precision signed integer.
    BigInteger,
    /// Arbitrary-precision decimal (unscaled integer + scale).
    BigDecimal,
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
    /// Structured exception object (`Exception` type).
    pub const EXCEPTION: u8 = 6;
}

impl BaseType {
    pub fn native(kind: u8) -> Self {
        BaseType::Native(kind)
    }

    // ------------------------------------------------------------------
    // Centralized scalar numeric rules.
    // Every phase (checker, optimizer, verifier, VM) must use these helpers
    // instead of ad-hoc type matches so the numeric model stays coherent.
    // ------------------------------------------------------------------

    /// Signed fixed-width integral primitives (`Byte`, `Short`, `Integer`,
    /// `Long`). Arithmetic on these is checked; overflow is a runtime error.
    pub fn is_integral(self: &BaseType) -> bool {
        matches!(
            self,
            BaseType::Byte | BaseType::Short | BaseType::Integer | BaseType::Long
        )
    }

    /// IEEE-754 floating-point primitives (`Float`, `Double`).
    pub fn is_floating(self: &BaseType) -> bool {
        matches!(self, BaseType::Float | BaseType::Double)
    }

    /// Any primitive numeric type (integral or floating).
    pub fn is_primitive_numeric(self: &BaseType) -> bool {
        self.is_integral() || self.is_floating()
    }

    /// Arbitrary-precision numerics. These never widen implicitly into or
    /// out of primitive numerics.
    pub fn is_arbitrary_precision(self: &BaseType) -> bool {
        matches!(self, BaseType::BigInteger | BaseType::BigDecimal)
    }

    /// Any type that supports the arithmetic operators.
    pub fn is_numeric(self: &BaseType) -> bool {
        self.is_primitive_numeric() || self.is_arbitrary_precision()
    }

    /// Implicit widening edge in the primitive numeric lattice.
    ///
    /// `Long -> Float` / `Long -> Double` may lose precision; that is
    /// deliberate Java parity for ordinary primitive widening.
    pub fn widens_to(self: &BaseType, target: &BaseType) -> bool {
        if self == target {
            return true;
        }
        matches!(
            (self, target),
            (BaseType::Byte, BaseType::Short)
                | (BaseType::Byte, BaseType::Integer)
                | (BaseType::Byte, BaseType::Long)
                | (BaseType::Byte, BaseType::Float)
                | (BaseType::Byte, BaseType::Double)
                | (BaseType::Short, BaseType::Integer)
                | (BaseType::Short, BaseType::Long)
                | (BaseType::Short, BaseType::Float)
                | (BaseType::Short, BaseType::Double)
                | (BaseType::Integer, BaseType::Long)
                | (BaseType::Integer, BaseType::Float)
                | (BaseType::Integer, BaseType::Double)
                | (BaseType::Long, BaseType::Float)
                | (BaseType::Long, BaseType::Double)
                | (BaseType::Float, BaseType::Double)
        )
    }

    /// Binary numeric promotion for `+ - * / %`:
    /// 1. either operand `Double` -> `Double`
    /// 2. else either `Float` -> `Float`
    /// 3. else either `Long` -> `Long`
    /// 4. else `Byte`/`Short`/`Integer` operands promote to `Integer`
    ///
    /// `None` when the pair is not a legal primitive arithmetic combination
    /// (arbitrary-precision types only combine with themselves).
    pub fn binary_promotion(self: &BaseType, other: &BaseType) -> Option<BaseType> {
        if self.is_arbitrary_precision() || other.is_arbitrary_precision() {
            return (self == other).then(|| self.clone());
        }
        if !self.is_primitive_numeric() || !other.is_primitive_numeric() {
            return None;
        }
        Some(match (self, other) {
            (BaseType::Double, _) | (_, BaseType::Double) => BaseType::Double,
            (BaseType::Float, _) | (_, BaseType::Float) => BaseType::Float,
            (BaseType::Long, _) | (_, BaseType::Long) => BaseType::Long,
            _ => BaseType::Integer,
        })
    }

    /// Result type of unary `-` (same promotion as binary, no mixing).
    pub fn unary_result(self: &BaseType) -> Option<BaseType> {
        if self.is_primitive_numeric() {
            Some(match self {
                BaseType::Byte | BaseType::Short | BaseType::Integer => BaseType::Integer,
                other => other.clone(),
            })
        } else if self.is_arbitrary_precision() {
            Some(self.clone())
        } else {
            None
        }
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
            BaseType::Boolean => write!(f, "Boolean"),
            BaseType::Byte => write!(f, "Byte"),
            BaseType::Short => write!(f, "Short"),
            BaseType::Integer => write!(f, "Integer"),
            BaseType::Long => write!(f, "Long"),
            BaseType::Float => write!(f, "Float"),
            BaseType::Double => write!(f, "Double"),
            BaseType::BigInteger => write!(f, "BigInteger"),
            BaseType::BigDecimal => write!(f, "BigDecimal"),
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
                    crate::types::native_kind::EXCEPTION => "Exception",
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

    pub fn boolean() -> Ty {
        Ty::non_null(BaseType::Boolean)
    }
    pub fn byte() -> Ty {
        Ty::non_null(BaseType::Byte)
    }
    pub fn short_() -> Ty {
        Ty::non_null(BaseType::Short)
    }
    pub fn integer() -> Ty {
        Ty::non_null(BaseType::Integer)
    }
    pub fn long() -> Ty {
        Ty::non_null(BaseType::Long)
    }
    pub fn float_() -> Ty {
        Ty::non_null(BaseType::Float)
    }
    pub fn double() -> Ty {
        Ty::non_null(BaseType::Double)
    }
    pub fn big_integer() -> Ty {
        Ty::non_null(BaseType::BigInteger)
    }
    pub fn big_decimal() -> Ty {
        Ty::non_null(BaseType::BigDecimal)
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
    // Primitive numeric widening (centralized lattice).
    if source.base.widens_to(&target.base) && source.base != target.base {
        return source.nullable <= target.nullable;
    }
    // Interface conformance. Classes do not inherit from classes, so
    // there is no class-to-class subtyping. Generic arguments are checked
    // against the class's declared interface bindings after substituting
    // the source's type arguments.
    if let (BaseType::Class(c, cargs), BaseType::Interface(i, iargs)) = (&source.base, &target.base)
    {
        if let Some(bargs) = program.class_interface_args(*c, *i) {
            let subst: Vec<Option<BaseType>> = cargs.iter().cloned().map(Some).collect();
            let sub: Vec<BaseType> = bargs.iter().map(|a| a.substitute(&subst)).collect();
            if sub == *iargs {
                return source.nullable <= target.nullable;
            }
        }
    }
    if let (BaseType::Interface(s, sargs), BaseType::Interface(t, targs)) =
        (&source.base, &target.base)
    {
        if let Some(bargs) = program.interface_parent_args(*s, *t) {
            let subst: Vec<Option<BaseType>> = sargs.iter().cloned().map(Some).collect();
            let sub: Vec<BaseType> = bargs.iter().map(|a| a.substitute(&subst)).collect();
            if sub == *targs {
                return source.nullable <= target.nullable;
            }
        }
    }
    false
}

/// Oracle for nominal relationships, implemented by the resolver.
pub trait SubtypeOracle {
    /// Resolved type arguments with which `class` implements `interface`,
    /// expressed in the class's own type-parameter space; `None` when the
    /// class does not implement the interface at all.
    fn class_interface_args(&self, class: u32, interface: u32) -> Option<Vec<BaseType>>;
    fn interface_extends(&self, child: u32, ancestor: u32) -> bool;
    /// Resolved type arguments with which `child` binds `ancestor`,
    /// expressed in `child`'s own type-parameter space; `None` when `child`
    /// does not extend `ancestor`. For `child == ancestor` this is the
    /// identity binding (the interface's own type parameters).
    fn interface_parent_args(&self, child: u32, ancestor: u32) -> Option<Vec<BaseType>>;
}
