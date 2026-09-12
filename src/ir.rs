//! Resolved typed IR.
//
// Opcode table and type constructors are retained as architectural
// surface even when not every entry is emitted yet.
#![allow(dead_code)]
//!
//! The checker emits IR instructions with fully resolved integer IDs:
//! constants, locals, classes, method_table/interface slots, natives. The bytecode
//! compiler consumes only this representation — never the AST — and the VM
//! never performs source-level name resolution.

use crate::types::Ty;
use std::collections::HashMap;
use std::hash::{Hash, Hasher};

#[derive(Debug, Clone, PartialEq)]
pub enum IrConst {
    Null,
    Bool(bool),
    Byte(i8),
    Short(i16),
    Integer(i32),
    Long(i64),
    Float(f32),
    Double(f64),
    /// Arbitrary-precision integer, canonical decimal text.
    BigInt(String),
    /// Arbitrary-precision decimal, canonical decimal text.
    BigDecimal(String),
    Char(char),
    Str(String),
}

/// Explicit-conversion targets for `IrOp::Convert`.
pub mod conv_target {
    pub const BYTE: u8 = 0;
    pub const SHORT: u8 = 1;
    pub const INTEGER: u8 = 2;
    pub const LONG: u8 = 3;
    pub const FLOAT: u8 = 4;
    pub const DOUBLE: u8 = 5;
    pub const BOOLEAN: u8 = 6;
    pub const CHAR: u8 = 7;
    pub const STRING: u8 = 8;
    pub const BIG_INTEGER: u8 = 9;
    pub const BIG_DECIMAL: u8 = 10;

    /// Map a scalar base type to its conversion tag (None for types that
    /// are not explicit conversion targets, e.g. Object/List/Map/Void).
    pub fn of(base: &crate::types::BaseType) -> Option<u8> {
        use crate::types::BaseType::*;
        Some(match base {
            Byte => BYTE,
            Short => SHORT,
            Integer => INTEGER,
            Long => LONG,
            Float => FLOAT,
            Double => DOUBLE,
            Boolean => BOOLEAN,
            Char => CHAR,
            String => STRING,
            BigInteger => BIG_INTEGER,
            BigDecimal => BIG_DECIMAL,
            _ => return None,
        })
    }
}

/// Kind tags for `IrOp::Conforms` (runtime catch-clause matching).
pub mod conforms_kind {
    /// User-defined class (exact class match; there is no inheritance).
    pub const CLASS: u8 = 0;
    /// Interface (user-defined or built-in, e.g. `Throwable`).
    pub const INTERFACE: u8 = 1;
    /// The built-in `Exception` native object.
    pub const EXCEPTION: u8 = 2;
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum IrOp {
    // constants / locals / globals
    LoadConst,
    LoadLocal,
    StoreLocal,
    // arithmetic: operands carry their own scalar type; the checker
    // guarantees a legal combination and the promoted result type. The VM
    // dispatches on the runtime value variants (checked integer arithmetic
    // in the promoted width, IEEE-754 floats, arbitrary-precision objects).
    Add,
    Sub,
    Mul,
    Div,
    Mod,
    Neg,
    // explicit conversion (range/validity checked at runtime)
    Convert,
    // logic / null
    Not,
    And,
    Pop,
    IsNull,
    NullCheck,
    // equality / ordering with the universal object-contract semantics:
    // primitives by value (numeric promotion), strings by content, enums
    // and collections structurally, other objects by identity.
    Eq,
    Lt,
    Le,
    Gt,
    Ge,
    // control flow
    Jump,
    JumpIfFalse,
    JumpIfTrue,
    // calls
    CallFn,
    CallStatic,
    CallClass,
    CallInterface,
    CallNative,
    CallDynamic,
    // objects
    NewObject,
    LoadField,
    StoreField,
    /// Read a static field slot of a class: pushes the value.
    LoadStatic,
    /// Write a static field slot of a class: pops the value.
    StoreStatic,
    /// Runtime nominal conformance test for catch dispatch: pops a value,
    /// pushes Boolean. Operands: (type id, kind tag).
    Conforms,
    // collections
    NewList,
    NewMap,
    NewStack,
    ListSpread,
    ListAdd,
    ListGet,
    ListSet,
    ListRemove,
    ListLen,
    ListContains,
    ListIndexOf,
    ListReverse,
    ListSort,
    ListJoin,
    ListClear,
    MapPut,
    MapGet,
    MapRemove,
    MapContainsKey,
    MapLen,
    MapKeys,
    MapValues,
    MapClear,
    StackPush,
    StackPop,
    StackPeek,
    StackGet,
    StackLen,
    StackEmpty,
    // strings
    StrLen,
    StrConcat,
    StrSubstr,
    StrContains,
    StrStartsWith,
    StrEndsWith,
    StrSplit,
    StrReplace,
    StrTrim,
    StrUpper,
    StrLower,
    StrIndex,
    StrCharAt,
    // enums
    NewEnum,
    EnumIndex,
    EnumPayload,
    // exceptions
    Throw,
    TryBegin,
    TryEnd,
    /// End of a finally body: rethrow an exception that passed through,
    /// complete a deferred return, or resume a diverted break/continue.
    FinallyEnd,
    /// Divert to the innermost enclosing finally (break/continue
    /// trampoline); control resumes at the next instruction once the
    /// finally body completes.
    FinallyDivert,
    ListExtend,
    // termination
    Return,
    ReturnVoid,
    // stack manipulation
    /// Duplicate the top of the value stack.
    Dup,
    // housekeeping
    GcHint,
}

impl IrOp {
    /// Number of immediate operands following the opcode byte.
    /// Size in bytes of the n-th operand (must mirror the compiler).
    pub fn operand_size(self, idx: usize) -> usize {
        use IrOp::*;
        match self {
            LoadConst => 4,
            Convert => 1,
            LoadLocal | StoreLocal => 2,
            Jump | JumpIfFalse | JumpIfTrue => 4,
            CallFn | CallStatic => match idx {
                0 => 4,
                _ => 2,
            },
            CallClass | CallInterface => 2,
            CallNative | CallDynamic => 2,
            NewObject => 2,
            LoadField | StoreField => 2,
            LoadStatic | StoreStatic => 2,
            NewList | NewMap => 2,
            NewEnum => match idx {
                0 => 2,
                _ => 1,
            },
            TryBegin => 4,
            Conforms => match idx {
                0 => 2,
                _ => 1,
            },
            _ => 0,
        }
    }

    pub fn operand_count(self) -> usize {
        use IrOp::*;
        match self {
            LoadConst | LoadLocal | StoreLocal | Convert => 1,
            Jump | JumpIfFalse | JumpIfTrue => 1,
            CallStatic => 3,
            CallFn | CallNative | CallDynamic => 2,
            CallClass | CallInterface => 3,
            NewObject => 2,
            LoadField | StoreField => 1,
            LoadStatic | StoreStatic => 2,
            NewList | NewMap => 1,
            NewStack => 0,
            NewEnum => 3,
            TryBegin => 2,
            Conforms => 2,
            _ => 0,
        }
    }
}

impl IrOp {
    /// Stable single-byte opcode for the bytecode encoding.
    pub fn code(self) -> u8 {
        use IrOp::*;
        match self {
            LoadConst => 0,
            LoadLocal => 1,
            StoreLocal => 2,
            Add => 3,
            Sub => 4,
            Mul => 5,
            Div => 6,
            Mod => 7,
            Neg => 8,
            Convert => 9,
            Not => 10,
            And => 11,
            Pop => 12,
            IsNull => 13,
            NullCheck => 14,
            Eq => 15,
            Lt => 16,
            Le => 17,
            Gt => 18,
            Ge => 19,
            Jump => 20,
            JumpIfFalse => 21,
            JumpIfTrue => 22,
            CallFn => 23,
            CallStatic => 24,
            CallClass => 25,
            CallInterface => 26,
            CallNative => 27,
            CallDynamic => 28,
            NewObject => 29,
            LoadField => 30,
            StoreField => 31,
            LoadStatic => 32,
            StoreStatic => 33,
            Conforms => 34,
            NewList => 35,
            NewMap => 36,
            NewStack => 37,
            ListSpread => 38,
            ListAdd => 39,
            ListGet => 40,
            ListSet => 41,
            ListRemove => 42,
            ListLen => 43,
            ListContains => 44,
            ListIndexOf => 45,
            ListReverse => 46,
            ListSort => 47,
            ListJoin => 48,
            ListClear => 49,
            MapPut => 50,
            MapGet => 51,
            MapRemove => 52,
            MapContainsKey => 53,
            MapLen => 54,
            MapKeys => 55,
            MapValues => 56,
            MapClear => 57,
            StackPush => 58,
            StackPop => 59,
            StackPeek => 60,
            StackGet => 61,
            StackLen => 62,
            StackEmpty => 63,
            StrLen => 64,
            StrConcat => 65,
            StrSubstr => 66,
            StrContains => 67,
            StrStartsWith => 68,
            StrEndsWith => 69,
            StrSplit => 70,
            StrReplace => 71,
            StrTrim => 72,
            StrUpper => 73,
            StrLower => 74,
            StrIndex => 75,
            StrCharAt => 76,
            NewEnum => 77,
            EnumIndex => 78,
            EnumPayload => 79,
            Throw => 80,
            TryBegin => 81,
            TryEnd => 82,
            Return => 83,
            ReturnVoid => 84,
            Dup => 85,
            GcHint => 86,
            FinallyEnd => 87,
            FinallyDivert => 88,
            ListExtend => 89,
        }
    }

    /// Total number of defined opcodes.
    pub const COUNT: usize = 90;
}

impl IrOp {
    /// Reverse mapping from the single-byte opcode.
    pub fn from_code(byte: u8) -> Option<IrOp> {
        use IrOp::*;
        match byte {
            0 => Some(LoadConst),
            1 => Some(LoadLocal),
            2 => Some(StoreLocal),
            3 => Some(Add),
            4 => Some(Sub),
            5 => Some(Mul),
            6 => Some(Div),
            7 => Some(Mod),
            8 => Some(Neg),
            9 => Some(Convert),
            10 => Some(Not),
            11 => Some(And),
            12 => Some(Pop),
            13 => Some(IsNull),
            14 => Some(NullCheck),
            15 => Some(Eq),
            16 => Some(Lt),
            17 => Some(Le),
            18 => Some(Gt),
            19 => Some(Ge),
            20 => Some(Jump),
            21 => Some(JumpIfFalse),
            22 => Some(JumpIfTrue),
            23 => Some(CallFn),
            24 => Some(CallStatic),
            25 => Some(CallClass),
            26 => Some(CallInterface),
            27 => Some(CallNative),
            28 => Some(CallDynamic),
            29 => Some(NewObject),
            30 => Some(LoadField),
            31 => Some(StoreField),
            32 => Some(LoadStatic),
            33 => Some(StoreStatic),
            34 => Some(Conforms),
            35 => Some(NewList),
            36 => Some(NewMap),
            37 => Some(NewStack),
            38 => Some(ListSpread),
            39 => Some(ListAdd),
            40 => Some(ListGet),
            41 => Some(ListSet),
            42 => Some(ListRemove),
            43 => Some(ListLen),
            44 => Some(ListContains),
            45 => Some(ListIndexOf),
            46 => Some(ListReverse),
            47 => Some(ListSort),
            48 => Some(ListJoin),
            49 => Some(ListClear),
            50 => Some(MapPut),
            51 => Some(MapGet),
            52 => Some(MapRemove),
            53 => Some(MapContainsKey),
            54 => Some(MapLen),
            55 => Some(MapKeys),
            56 => Some(MapValues),
            57 => Some(MapClear),
            58 => Some(StackPush),
            59 => Some(StackPop),
            60 => Some(StackPeek),
            61 => Some(StackGet),
            62 => Some(StackLen),
            63 => Some(StackEmpty),
            64 => Some(StrLen),
            65 => Some(StrConcat),
            66 => Some(StrSubstr),
            67 => Some(StrContains),
            68 => Some(StrStartsWith),
            69 => Some(StrEndsWith),
            70 => Some(StrSplit),
            71 => Some(StrReplace),
            72 => Some(StrTrim),
            73 => Some(StrUpper),
            74 => Some(StrLower),
            75 => Some(StrIndex),
            76 => Some(StrCharAt),
            77 => Some(NewEnum),
            78 => Some(EnumIndex),
            79 => Some(EnumPayload),
            80 => Some(Throw),
            81 => Some(TryBegin),
            82 => Some(TryEnd),
            83 => Some(Return),
            84 => Some(ReturnVoid),
            85 => Some(Dup),
            86 => Some(GcHint),
            87 => Some(FinallyEnd),
            88 => Some(FinallyDivert),
            89 => Some(ListExtend),
            _ => None,
        }
    }
}
/// A resolved instruction. Operands are encoded inline for clarity; the
/// bytecode compiler serializes them compactly.
#[derive(Debug, Clone, Copy, PartialEq)]
pub enum IrInstr {
    Op(IrOp),
    LoadConst(u32),
    LoadLocal(u16),
    StoreLocal(u16),
    /// Explicit conversion to `conv_target` tag.
    Convert(u8),
    Jump(u32),
    JumpIfFalse(u32),
    JumpIfTrue(u32),
    CallFn(u32, u16),
    /// (function id, arity, target class id for Self construction;
    /// 0xFFFF = the declaring class).
    CallStatic(u32, u16, u16),
    CallClass(u16, u16, u16),
    CallInterface(u16, u16, u16),
    CallNative(u16, u16),
    CallDynamic(u16, u16),
    NewObject(u16, u16),
    LoadField(u16),
    StoreField(u16),
    /// (declaring class id, static slot).
    LoadStatic(u16, u16),
    /// (declaring class id, static slot).
    StoreStatic(u16, u16),
    NewList(u16),
    NewMap(u16),
    NewStack,
    NewEnum(u16, u8, bool),
    TryBegin(u32, u32),
    /// (type id, conforms kind tag) for catch-clause dispatch.
    Conforms(u16, u8),
    Return,
    ReturnVoid,
    GcHint,
}

impl std::fmt::Display for IrInstr {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            IrInstr::Op(op) => write!(f, "{op:?}"),
            IrInstr::LoadConst(c) => write!(f, "LoadConst({c})"),
            IrInstr::LoadLocal(s) => write!(f, "LoadLocal({s})"),
            IrInstr::StoreLocal(s) => write!(f, "StoreLocal({s})"),
            IrInstr::Convert(t) => write!(f, "Convert({t})"),
            IrInstr::Jump(t) => write!(f, "Jump({t})"),
            IrInstr::JumpIfFalse(t) => write!(f, "JumpIfFalse({t})"),
            IrInstr::JumpIfTrue(t) => write!(f, "JumpIfTrue({t})"),
            IrInstr::CallFn(a, b) => write!(f, "CallFn({a}, {b})"),
            IrInstr::CallStatic(a, b, c) => write!(f, "CallStatic({a}, {b}, {c})"),
            IrInstr::CallClass(a, b, c) => write!(f, "CallClass({a}, {b}, {c})"),
            IrInstr::CallInterface(a, b, c) => write!(f, "CallInterface({a}, {b}, {c})"),
            IrInstr::CallNative(a, b) => write!(f, "CallNative({a}, {b})"),
            IrInstr::CallDynamic(a, b) => write!(f, "CallDynamic({a}, {b})"),
            IrInstr::NewObject(a, b) => write!(f, "NewObject({a}, {b})"),
            IrInstr::LoadField(s) => write!(f, "LoadField({s})"),
            IrInstr::StoreField(s) => write!(f, "StoreField({s})"),
            IrInstr::LoadStatic(c, s) => write!(f, "LoadStatic({c}, {s})"),
            IrInstr::StoreStatic(c, s) => write!(f, "StoreStatic({c}, {s})"),
            IrInstr::NewList(c) => write!(f, "NewList({c})"),
            IrInstr::NewMap(c) => write!(f, "NewMap({c})"),
            IrInstr::NewStack => write!(f, "NewStack"),
            IrInstr::NewEnum(a, b, c) => write!(f, "NewEnum({a}, {b}, {c})"),
            IrInstr::TryBegin(a, b) => write!(f, "TryBegin({a}, {b})"),
            IrInstr::Conforms(a, b) => write!(f, "Conforms({a}, {b})"),
            IrInstr::Return => write!(f, "Return"),
            IrInstr::ReturnVoid => write!(f, "ReturnVoid"),
            IrInstr::GcHint => write!(f, "GcHint"),
        }
    }
}

impl IrInstr {
    pub fn op(&self) -> Option<IrOp> {
        use IrInstr::*;
        match self {
            Op(op) => Some(*op),
            LoadConst(_) => Some(IrOp::LoadConst),
            LoadLocal(_) => Some(IrOp::LoadLocal),
            StoreLocal(_) => Some(IrOp::StoreLocal),
            Convert(_) => Some(IrOp::Convert),
            Jump(_) => Some(IrOp::Jump),
            JumpIfFalse(_) => Some(IrOp::JumpIfFalse),
            JumpIfTrue(_) => Some(IrOp::JumpIfTrue),
            CallFn(..) => Some(IrOp::CallFn),
            CallStatic(..) => Some(IrOp::CallStatic),
            CallClass(..) => Some(IrOp::CallClass),
            CallInterface(..) => Some(IrOp::CallInterface),
            CallNative(..) => Some(IrOp::CallNative),
            CallDynamic(..) => Some(IrOp::CallDynamic),
            NewObject(..) => Some(IrOp::NewObject),
            LoadField(_) => Some(IrOp::LoadField),
            StoreField(_) => Some(IrOp::StoreField),
            LoadStatic(..) => Some(IrOp::LoadStatic),
            StoreStatic(..) => Some(IrOp::StoreStatic),
            NewList(_) => Some(IrOp::NewList),
            NewMap(_) => Some(IrOp::NewMap),
            NewStack => Some(IrOp::NewStack),
            NewEnum(..) => Some(IrOp::NewEnum),
            TryBegin(..) => Some(IrOp::TryBegin),
            Conforms(..) => Some(IrOp::Conforms),
            Return => Some(IrOp::Return),
            ReturnVoid => Some(IrOp::ReturnVoid),
            GcHint => Some(IrOp::GcHint),
        }
    }
}

#[derive(Debug)]
pub struct IrFunction {
    /// Debug name, e.g. `User.login` or `Foo.doubled$default`.
    pub name: String,
    pub params: Vec<String>,
    pub param_tys: Vec<Ty>,
    pub local_count: u16,
    /// True when the method returns a value (false = void).
    pub returns_value: bool,
    pub instrs: Vec<IrInstr>,
    pub source_file: u32,
    /// (ip, line) pairs for stack traces.
    pub line_map: Vec<(u32, u32)>,
}

#[derive(Debug)]
pub struct IrClass {
    pub id: u32,
    pub name: String,
    pub field_count: u16,
    /// Class-local instance-method names indexed by `method_table` slot.
    pub method_names: Vec<String>,
    /// One FunctionId per class-local instance-method slot.
    pub method_table: Vec<u32>,
    /// Public effective methods for `Object` dynamic dispatch:
    /// (name, FunctionId). Includes delegated wrappers and defaults.
    pub dyn_methods: Vec<(String, u32)>,
    /// Static methods: (name, FunctionId).
    pub statics: Vec<(String, u32)>,
    /// Static fields: (name, static slot). Slot namespace is separate from
    /// instance fields (`field_count`).
    pub static_fields: Vec<(String, u16)>,
    /// Synthetic static-initializer function id (None when the class has no
    /// static fields). The VM runs these in class declaration order before
    /// the entry point.
    pub static_init: Option<u32>,
    /// Interface implementations: (interface id, dispatch FunctionIds).
    pub interfaces: Vec<(u32, Vec<u32>)>,
}

#[derive(Debug)]
pub struct IrInterface {
    pub id: u32,
    pub name: String,
    pub slots: Vec<String>,
    /// Default implementation FunctionId per slot (None = abstract).
    pub defaults: Vec<Option<u32>>,
}

#[derive(Debug)]
pub struct IrModule {
    pub constants: Vec<IrConst>,
    pub functions: Vec<IrFunction>,
    pub classes: Vec<IrClass>,
    pub interfaces: Vec<IrInterface>,
    /// Entry point function id (Main.run).
    pub entry: Option<u32>,
    /// Interned method names for dynamic (Any-receiver) dispatch.
    pub dyn_names: Vec<String>,
    /// Hash index for the constant pool. The values remain in `constants` so
    /// bytecode IDs stay compact; this index only avoids rescanning the whole
    /// pool while the checker is emitting IR.
    const_index: HashMap<u64, Vec<u32>>,
    /// Number of public `constants` entries represented by `const_index`.
    /// This detects direct pool edits made by inspection/tooling.
    const_indexed_len: usize,
}

impl Default for IrModule {
    fn default() -> Self {
        Self::new()
    }
}

impl IrModule {
    pub fn new() -> Self {
        IrModule {
            constants: vec![],
            functions: vec![],
            classes: vec![],
            interfaces: vec![],
            entry: None,
            dyn_names: vec![],
            const_index: HashMap::new(),
            const_indexed_len: 0,
        }
    }

    pub fn intern_const(&mut self, c: IrConst) -> u32 {
        // `constants` is public for inspection and existing tooling may have
        // populated it directly. Lazily rebuild the auxiliary index in that
        // case without making the common path scan the pool.
        if self.const_indexed_len != self.constants.len() {
            self.const_index.clear();
            for (i, existing) in self.constants.iter().enumerate() {
                self.const_index
                    .entry(const_hash(existing))
                    .or_default()
                    .push(i as u32);
            }
            self.const_indexed_len = self.constants.len();
        }
        let hash = const_hash(&c);
        if let Some(candidates) = self.const_index.get(&hash) {
            if let Some(&i) = candidates
                .iter()
                .find(|&&i| const_equal(&self.constants[i as usize], &c))
            {
                return i;
            }
        }
        self.constants.push(c);
        let index = (self.constants.len() - 1) as u32;
        self.const_index.entry(hash).or_default().push(index);
        self.const_indexed_len = self.constants.len();
        index
    }

    pub fn intern_dyn_name(&mut self, name: &str) -> u16 {
        if let Some(i) = self.dyn_names.iter().position(|x| x == name) {
            return i as u16;
        }
        self.dyn_names.push(name.to_string());
        (self.dyn_names.len() - 1) as u16
    }
}

/// Constant storage equality differs from language equality: signed zero is
/// observable through division and formatting, so preserve floating-point bits.
pub(crate) fn const_equal(a: &IrConst, b: &IrConst) -> bool {
    match (a, b) {
        (IrConst::Float(a), IrConst::Float(b)) => a.to_bits() == b.to_bits(),
        (IrConst::Double(a), IrConst::Double(b)) => a.to_bits() == b.to_bits(),
        _ => a == b,
    }
}

/// Hash a constant consistently with its storage equality.
pub(crate) fn const_hash(c: &IrConst) -> u64 {
    let mut h = std::collections::hash_map::DefaultHasher::new();
    std::mem::discriminant(c).hash(&mut h);
    match c {
        IrConst::Null => {}
        IrConst::Bool(v) => v.hash(&mut h),
        IrConst::Byte(v) => v.hash(&mut h),
        IrConst::Short(v) => v.hash(&mut h),
        IrConst::Integer(v) => v.hash(&mut h),
        IrConst::Long(v) => v.hash(&mut h),
        IrConst::Float(v) => v.to_bits().hash(&mut h),
        IrConst::Double(v) => v.to_bits().hash(&mut h),
        IrConst::BigInt(v) | IrConst::BigDecimal(v) | IrConst::Str(v) => v.hash(&mut h),
        IrConst::Char(v) => v.hash(&mut h),
    }
    h.finish()
}
