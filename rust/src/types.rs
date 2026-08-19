//! Type representations and compatibility rules.
//!
//! Port of internal/types/types.go. Types are shared via `Rc`; struct method
//! tables use interior mutability because methods are registered after the
//! struct type is created and published in scopes.

use std::cell::RefCell;
use std::collections::HashMap;
use std::rc::Rc;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Kind {
    Invalid,
    Void,
    Bool,
    Byte,
    Int,
    Float,
    Char,
    String,
    List,
    Map,
    Stack,
    Function,
    Module,
    Exception,
    Enum,
    Struct,
    Trait,
    Any,
}

#[derive(Clone, Debug)]
pub struct StructFieldInfo {
    pub name: String,
    pub ty: Rc<Type>,
    pub is_mut: bool,
    pub is_pub: bool,
}

#[derive(Clone, Debug)]
pub struct StructMethodInfo {
    pub func_index: i32,
    pub signature: Rc<Type>,
    pub is_pub: bool,
    pub is_mut: bool,
}

#[derive(Clone, Debug)]
pub struct TraitMethodInfo {
    pub signature: Rc<Type>,
    pub is_pub: bool,
    pub is_mut: bool,
}

pub struct Type {
    pub kind: Kind,
    pub nullable: bool,
    pub element: Option<Rc<Type>>,
    pub key_type: Option<Rc<Type>>,
    pub value_type: Option<Rc<Type>>,
    pub params: Vec<Rc<Type>>,
    pub ret: Option<Rc<Type>>,
    pub variadic: bool,
    // Enum fields
    pub enum_name: String,
    pub enum_variant: String,
    pub enum_values: Option<Rc<HashMap<String, i64>>>,
    // Struct fields
    pub struct_name: String,
    pub struct_fields: Vec<StructFieldInfo>,
    pub struct_methods: RefCell<HashMap<String, Rc<StructMethodInfo>>>,
    // Trait fields
    pub trait_name: String,
    pub trait_methods: HashMap<String, Rc<TraitMethodInfo>>,
}

impl std::fmt::Debug for Type {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.named())
    }
}

impl Type {
    pub(crate) fn prim(kind: Kind) -> Type {
        Type {
            kind,
            nullable: false,
            element: None,
            key_type: None,
            value_type: None,
            params: Vec::new(),
            ret: None,
            variadic: false,
            enum_name: String::new(),
            enum_variant: String::new(),
            enum_values: None,
            struct_name: String::new(),
            struct_fields: Vec::new(),
            struct_methods: RefCell::new(HashMap::new()),
            trait_name: String::new(),
            trait_methods: HashMap::new(),
        }
    }

    pub fn named(&self) -> String {
        let base = self.base_name();
        if self.nullable {
            format!("{}?", base)
        } else {
            base
        }
    }

    fn base_name(&self) -> String {
        match self.kind {
            Kind::Invalid => "<invalid>".to_string(),
            Kind::Void => "void".to_string(),
            Kind::Bool => "bool".to_string(),
            Kind::Byte => "byte".to_string(),
            Kind::Int => "int".to_string(),
            Kind::Float => "float".to_string(),
            Kind::Char => "char".to_string(),
            Kind::String => "string".to_string(),
            Kind::Exception => "exception".to_string(),
            Kind::Enum => {
                if !self.enum_variant.is_empty() {
                    format!("{}.{}", self.enum_name, self.enum_variant)
                } else {
                    self.enum_name.clone()
                }
            }
            Kind::Struct => self.struct_name.clone(),
            Kind::Trait => self.trait_name.clone(),
            Kind::List => match &self.element {
                Some(e) => format!("list<{}>", e.named()),
                None => "list".to_string(),
            },
            Kind::Stack => match &self.element {
                Some(e) => format!("stack<{}>", e.named()),
                None => "stack".to_string(),
            },
            Kind::Map => match (&self.key_type, &self.value_type) {
                (Some(k), Some(v)) => format!("map<{}, {}>", k.named(), v.named()),
                _ => "map".to_string(),
            },
            Kind::Function => self.function_name(),
            Kind::Module => "module".to_string(),
            Kind::Any => "any".to_string(),
        }
    }

    fn function_name(&self) -> String {
        let mut s = String::from("(");
        for (i, p) in self.params.iter().enumerate() {
            if i > 0 {
                s.push_str(", ");
            }
            if self.variadic && i == self.params.len() - 1 {
                s.push_str("...");
            }
            s.push_str(&p.named());
        }
        s.push_str(") -> ");
        if let Some(r) = &self.ret {
            s.push_str(&r.named());
        } else {
            s.push_str("void");
        }
        s
    }

    /// Structural type equality.
    pub fn equals(&self, other: &Type) -> bool {
        if self.kind != other.kind || self.nullable != other.nullable {
            return false;
        }
        match self.kind {
            Kind::List | Kind::Stack => types_equal(&self.element, &other.element),
            Kind::Map => {
                types_equal(&self.key_type, &other.key_type)
                    && types_equal(&self.value_type, &other.value_type)
            }
            Kind::Function => {
                if !types_equal(&self.ret, &other.ret)
                    || self.params.len() != other.params.len()
                    || self.variadic != other.variadic
                {
                    return false;
                }
                for i in 0..self.params.len() {
                    if !types_equal(&Some(self.params[i].clone()), &Some(other.params[i].clone())) {
                        return false;
                    }
                }
                true
            }
            Kind::Enum => self.enum_name == other.enum_name && self.enum_variant == other.enum_variant,
            Kind::Struct => self.struct_name == other.struct_name,
            Kind::Trait => self.trait_name == other.trait_name,
            _ => true,
        }
    }

    pub fn is_reference_type(&self) -> bool {
        matches!(
            self.kind,
            Kind::String | Kind::List | Kind::Map | Kind::Stack | Kind::Exception | Kind::Struct | Kind::Trait
        )
    }

    pub fn is_primitive(&self) -> bool {
        if self.nullable {
            return false;
        }
        matches!(
            self.kind,
            Kind::Bool | Kind::Byte | Kind::Int | Kind::Float | Kind::Char
        )
    }

    pub fn is_integer(&self) -> bool {
        matches!(self.kind, Kind::Byte | Kind::Int)
    }

    pub fn is_numeric(&self) -> bool {
        matches!(self.kind, Kind::Byte | Kind::Int | Kind::Float)
    }

    pub fn is_bool(&self) -> bool {
        self.kind == Kind::Bool
    }

    pub fn is_string(&self) -> bool {
        self.kind == Kind::String
    }

    pub fn is_char(&self) -> bool {
        self.kind == Kind::Char
    }

    pub fn is_exception(&self) -> bool {
        self.kind == Kind::Exception
    }

    pub fn is_void(&self) -> bool {
        self.kind == Kind::Void
    }

    pub fn is_valid(&self) -> bool {
        self.kind != Kind::Invalid
    }

    pub fn is_null(&self) -> bool {
        self.kind == Kind::Invalid
    }

    pub fn is_any(&self) -> bool {
        self.kind == Kind::Any
    }

    pub fn is_nullable(&self) -> bool {
        self.nullable
    }

    pub fn is_valid_map_key(&self) -> bool {
        if self.nullable {
            return false;
        }
        matches!(
            self.kind,
            Kind::Bool | Kind::Byte | Kind::Int | Kind::Char | Kind::String | Kind::Enum
        )
    }

    fn rank(&self) -> i32 {
        match self.kind {
            Kind::Byte => 0,
            Kind::Int => 1,
            Kind::Float => 2,
            _ => -1,
        }
    }

    /// Whether a value of `src` can be assigned to this type.
    pub fn is_assignable_from(&self, src: &Type) -> bool {
        if self.kind == Kind::Any || src.kind == Kind::Any {
            return true;
        }

        // Null can be assigned to nullable types
        if src.kind == Kind::Invalid && self.nullable {
            return true;
        }

        if self.equals(src) {
            return true;
        }

        // Trait satisfaction: a struct can be assigned to a trait type
        if self.kind == Kind::Trait && src.kind == Kind::Struct {
            return struct_satisfies_trait(src, self);
        }

        // Trait-to-trait assignment (same trait)
        if self.kind == Kind::Trait && src.kind == Kind::Trait {
            return self.trait_name == src.trait_name;
        }

        // Nullable assignment: T? accepts T
        if self.nullable && !src.nullable && self.kind == src.kind {
            match self.kind {
                Kind::String | Kind::List | Kind::Map | Kind::Stack => {
                    return types_equal(&self.element, &src.element)
                        && types_equal(&self.key_type, &src.key_type)
                        && types_equal(&self.value_type, &src.value_type);
                }
                Kind::Struct => return self.struct_name == src.struct_name,
                Kind::Trait => return self.trait_name == src.trait_name,
                Kind::Int | Kind::Float | Kind::Byte | Kind::Bool | Kind::Char => return true,
                _ => {}
            }
        }

        // Nullable trait assignment
        if self.nullable && !src.nullable && self.kind == Kind::Trait && src.kind == Kind::Struct {
            return struct_satisfies_trait(src, self);
        }

        // Numeric widening: byte -> int -> float
        if !self.nullable && !src.nullable {
            if self.kind == Kind::Int && src.kind == Kind::Byte {
                return true;
            }
            if self.kind == Kind::Float && (src.kind == Kind::Byte || src.kind == Kind::Int) {
                return true;
            }
        }

        // Numeric widening into a nullable target
        if self.nullable && !src.nullable {
            if self.kind == Kind::Int && src.kind == Kind::Byte {
                return true;
            }
            if self.kind == Kind::Float && (src.kind == Kind::Byte || src.kind == Kind::Int) {
                return true;
            }
        }

        // String can be assigned to exception (auto-conversion)
        if self.kind == Kind::Exception && src.kind == Kind::String && !src.nullable {
            return true;
        }

        // A specific enum variant can be assigned to the base enum type
        if self.kind == Kind::Enum && src.kind == Kind::Enum {
            if self.enum_name == src.enum_name && self.enum_variant.is_empty() {
                return true;
            }
        }

        false
    }

    pub fn without_nullable(&self) -> Rc<Type> {
        let mut cp = Type::prim(self.kind);
        clone_type_into(self, &mut cp);
        cp.nullable = false;
        Rc::new(cp)
    }
}

/// Deep-copies the structural fields of `src` into `dst` (kind and primitive
/// layout already set by the caller).
fn clone_type_into(src: &Type, dst: &mut Type) {
    dst.nullable = src.nullable;
    dst.element = src.element.clone();
    dst.key_type = src.key_type.clone();
    dst.value_type = src.value_type.clone();
    dst.params = src.params.clone();
    dst.ret = src.ret.clone();
    dst.variadic = src.variadic;
    dst.enum_name = src.enum_name.clone();
    dst.enum_variant = src.enum_variant.clone();
    dst.enum_values = src.enum_values.clone();
    dst.struct_name = src.struct_name.clone();
    dst.struct_fields = src.struct_fields.clone();
    *dst.struct_methods.borrow_mut() = src.struct_methods.borrow().clone();
    dst.trait_name = src.trait_name.clone();
    dst.trait_methods = src.trait_methods.clone();
}

pub fn types_equal(a: &Option<Rc<Type>>, b: &Option<Rc<Type>>) -> bool {
    match (a, b) {
        (None, None) => true,
        (Some(x), Some(y)) => x.equals(y),
        _ => false,
    }
}

// ---- Predefined singletons ----

macro_rules! singleton {
    ($fn_name:ident, $kind:expr) => {
        pub fn $fn_name() -> Rc<Type> {
            thread_local! {
                static T: Rc<Type> = Rc::new(Type::prim($kind));
            }
            T.with(|t| t.clone())
        }
    };
}

singleton!(t_void, Kind::Void);
singleton!(t_bool, Kind::Bool);
singleton!(t_byte, Kind::Byte);
singleton!(t_int, Kind::Int);
singleton!(t_float, Kind::Float);
singleton!(t_char, Kind::Char);
singleton!(t_string, Kind::String);
singleton!(t_exception, Kind::Exception);
singleton!(t_invalid, Kind::Invalid);
singleton!(t_any, Kind::Any);

pub fn list_of(element: Rc<Type>) -> Rc<Type> {
    let mut t = Type::prim(Kind::List);
    t.element = Some(element);
    Rc::new(t)
}

pub fn map_of(key: Rc<Type>, value: Rc<Type>) -> Rc<Type> {
    let mut t = Type::prim(Kind::Map);
    t.key_type = Some(key);
    t.value_type = Some(value);
    Rc::new(t)
}

pub fn stack_of(element: Rc<Type>) -> Rc<Type> {
    let mut t = Type::prim(Kind::Stack);
    t.element = Some(element);
    Rc::new(t)
}

pub fn nullable_of(t: &Rc<Type>) -> Rc<Type> {
    let mut cp = Type::prim(t.kind);
    clone_type_into(t, &mut cp);
    cp.nullable = true;
    Rc::new(cp)
}

pub fn function_type(params: Vec<Rc<Type>>, ret: Option<Rc<Type>>) -> Rc<Type> {
    let mut t = Type::prim(Kind::Function);
    t.params = params;
    t.ret = ret;
    Rc::new(t)
}

pub fn function_type_with(params: Vec<Rc<Type>>, variadic: bool, ret: Rc<Type>) -> Rc<Type> {
    if variadic {
        variadic_function_type(params, Some(ret))
    } else {
        function_type(params, Some(ret))
    }
}

pub fn variadic_function_type(params: Vec<Rc<Type>>, ret: Option<Rc<Type>>) -> Rc<Type> {
    let mut t = Type::prim(Kind::Function);
    t.params = params;
    t.ret = ret;
    t.variadic = true;
    Rc::new(t)
}

pub fn enum_type(name: &str, values: HashMap<String, i64>) -> Rc<Type> {
    let mut t = Type::prim(Kind::Enum);
    t.enum_name = name.to_string();
    t.enum_values = Some(Rc::new(values));
    Rc::new(t)
}

pub fn enum_variant_type(enum_ty: &Rc<Type>, variant: &str) -> Rc<Type> {
    let mut t = Type::prim(Kind::Enum);
    t.enum_name = enum_ty.enum_name.clone();
    t.enum_variant = variant.to_string();
    t.enum_values = enum_ty.enum_values.clone();
    Rc::new(t)
}

pub fn enum_variant_value(t: &Type) -> Option<i64> {
    if t.kind != Kind::Enum || t.enum_variant.is_empty() {
        return None;
    }
    t.enum_values.as_ref().and_then(|m| m.get(&t.enum_variant).copied())
}

pub fn common_numeric_type(a: &Rc<Type>, b: &Rc<Type>) -> Rc<Type> {
    if a.rank() >= b.rank() {
        a.clone()
    } else {
        b.clone()
    }
}

pub fn struct_type(name: &str, fields: Vec<StructFieldInfo>) -> Rc<Type> {
    let mut t = Type::prim(Kind::Struct);
    t.struct_name = name.to_string();
    t.struct_fields = fields;
    Rc::new(t)
}

pub fn trait_type(name: &str, methods: HashMap<String, Rc<TraitMethodInfo>>) -> Rc<Type> {
    let mut t = Type::prim(Kind::Trait);
    t.trait_name = name.to_string();
    t.trait_methods = methods;
    Rc::new(t)
}

/// Checks whether a struct type satisfies all methods of a trait.
pub fn struct_satisfies_trait(struct_ty: &Type, trait_ty: &Type) -> bool {
    if struct_ty.kind != Kind::Struct || trait_ty.kind != Kind::Trait {
        return false;
    }
    for (method_name, trait_method) in &trait_ty.trait_methods {
        let struct_method = match struct_ty.struct_methods.borrow().get(method_name) {
            Some(m) => m.clone(),
            None => return false,
        };
        if !struct_method.is_pub {
            return false;
        }
        if trait_method.is_mut != struct_method.is_mut {
            return false;
        }
        let trait_sig = &trait_method.signature;
        let struct_sig = &struct_method.signature;
        if !types_equal(&trait_sig.ret, &struct_sig.ret) {
            return false;
        }
        if trait_sig.params.len() + 1 != struct_sig.params.len() {
            return false;
        }
        for (i, tp) in trait_sig.params.iter().enumerate() {
            if !tp.equals(&struct_sig.params[i + 1]) {
                return false;
            }
        }
    }
    true
}
