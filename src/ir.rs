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
    Long(i64),
    Double(f64),
    Char(char),
    Str(String),
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum IrOp {
    // constants / locals / globals
    LoadConst,
    LoadLocal,
    StoreLocal,
    LoadGlobal,
    StoreGlobal,
    // long arithmetic
    AddLong,
    SubLong,
    MulLong,
    DivLong,
    ModLong,
    NegLong,
    // double arithmetic
    AddDouble,
    SubDouble,
    MulDouble,
    DivDouble,
    ModDouble,
    NegDouble,
    // conversions (catchable failures)
    ToLong,
    ToDouble,
    ToByte,
    ToBool,
    ToChar,
    ToStringValue,
    // logic / null
    Not,
    And,
    Pop,
    IsNull,
    NullCheck,
    // equality (typed)
    EqLong,
    EqDouble,
    EqBool,
    EqChar,
    EqString,
    EqObject,
    EqEnum,
    EqDyn,
    // ordering (typed)
    LtLong,
    LeLong,
    GtLong,
    GeLong,
    LtDouble,
    LeDouble,
    GtDouble,
    GeDouble,
    LtChar,
    LeChar,
    GtChar,
    GeChar,
    LtString,
    LeString,
    GtString,
    GeString,
    LtDyn,
    LeDyn,
    GtDyn,
    GeDyn,
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
    IdentityEq,
    IdentityNe,
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
            LoadLocal | StoreLocal | LoadGlobal | StoreGlobal => 2,
            Jump | JumpIfFalse | JumpIfTrue => 4,
            CallFn | CallStatic => match idx {
                0 => 4,
                _ => 2,
            },
            CallClass | CallInterface => 2,
            CallNative | CallDynamic => 2,
            NewObject => 2,
            LoadField | StoreField => 2,
            NewList | NewMap => 2,
            NewEnum => match idx {
                0 => 2,
                _ => 1,
            },
            TryBegin => 4,
            _ => 0,
        }
    }

    pub fn operand_count(self) -> usize {
        use IrOp::*;
        match self {
            LoadConst | LoadLocal | StoreLocal | LoadGlobal | StoreGlobal => 1,
            Jump | JumpIfFalse | JumpIfTrue => 1,
            CallStatic => 3,
            CallFn | CallNative | CallDynamic => 2,
            CallClass | CallInterface => 3,
            NewObject => 2,
            LoadField | StoreField => 1,
            NewList | NewMap => 1,
            NewStack => 0,
            NewEnum => 3,
            TryBegin => 2,
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
            LoadGlobal => 3,
            StoreGlobal => 4,
            AddLong => 5,
            SubLong => 6,
            MulLong => 7,
            DivLong => 8,
            ModLong => 9,
            NegLong => 10,
            AddDouble => 11,
            SubDouble => 12,
            MulDouble => 13,
            DivDouble => 14,
            ModDouble => 15,
            NegDouble => 16,
            ToLong => 17,
            ToDouble => 18,
            ToByte => 19,
            ToBool => 20,
            ToChar => 21,
            ToStringValue => 22,
            Not => 23,
            And => 24,
            Pop => 25,
            IsNull => 26,
            NullCheck => 27,
            EqLong => 28,
            EqDouble => 29,
            EqBool => 30,
            EqChar => 31,
            EqString => 32,
            EqObject => 33,
            EqEnum => 34,
            EqDyn => 35,
            LtLong => 36,
            LeLong => 37,
            GtLong => 38,
            GeLong => 39,
            LtDouble => 40,
            LeDouble => 41,
            GtDouble => 42,
            GeDouble => 43,
            LtChar => 44,
            LeChar => 45,
            GtChar => 46,
            GeChar => 47,
            LtString => 48,
            LeString => 49,
            GtString => 50,
            GeString => 51,
            LtDyn => 52,
            LeDyn => 53,
            GtDyn => 54,
            GeDyn => 55,
            Jump => 56,
            JumpIfFalse => 57,
            JumpIfTrue => 58,
            CallFn => 59,
            CallStatic => 60,
            CallClass => 61,
            CallInterface => 62,
            CallNative => 63,
            CallDynamic => 64,
            NewObject => 65,
            LoadField => 66,
            StoreField => 67,
            IdentityEq => 68,
            IdentityNe => 69,
            NewList => 70,
            NewMap => 71,
            NewStack => 72,
            ListSpread => 73,
            ListAdd => 74,
            ListGet => 75,
            ListSet => 76,
            ListRemove => 77,
            ListLen => 78,
            ListContains => 79,
            ListIndexOf => 80,
            ListReverse => 81,
            ListSort => 82,
            ListJoin => 83,
            ListClear => 84,
            MapPut => 85,
            MapGet => 86,
            MapRemove => 87,
            MapContainsKey => 88,
            MapLen => 89,
            MapKeys => 90,
            MapValues => 91,
            MapClear => 92,
            StackPush => 93,
            StackPop => 94,
            StackPeek => 95,
            StackGet => 96,
            StackLen => 97,
            StackEmpty => 98,
            StrLen => 99,
            StrConcat => 100,
            StrSubstr => 101,
            StrContains => 102,
            StrStartsWith => 103,
            StrEndsWith => 104,
            StrSplit => 105,
            StrReplace => 106,
            StrTrim => 107,
            StrUpper => 108,
            StrLower => 109,
            StrIndex => 110,
            StrCharAt => 111,
            NewEnum => 112,
            EnumIndex => 113,
            EnumPayload => 114,
            Throw => 115,
            TryBegin => 116,
            TryEnd => 117,
            Return => 118,
            ReturnVoid => 119,
            Dup => 120,
            GcHint => 121,
            FinallyEnd => 122,
            FinallyDivert => 123,
            ListExtend => 124,
        }
    }

    /// Total number of defined opcodes.
    pub const COUNT: usize = 125;
}

impl IrOp {
    /// Reverse mapping from the single-byte opcode.
    pub fn from_code(byte: u8) -> Option<IrOp> {
        use IrOp::*;
        match byte {
            0 => Some(LoadConst),
            1 => Some(LoadLocal),
            2 => Some(StoreLocal),
            3 => Some(LoadGlobal),
            4 => Some(StoreGlobal),
            5 => Some(AddLong),
            6 => Some(SubLong),
            7 => Some(MulLong),
            8 => Some(DivLong),
            9 => Some(ModLong),
            10 => Some(NegLong),
            11 => Some(AddDouble),
            12 => Some(SubDouble),
            13 => Some(MulDouble),
            14 => Some(DivDouble),
            15 => Some(ModDouble),
            16 => Some(NegDouble),
            17 => Some(ToLong),
            18 => Some(ToDouble),
            19 => Some(ToByte),
            20 => Some(ToBool),
            21 => Some(ToChar),
            22 => Some(ToStringValue),
            23 => Some(Not),
            24 => Some(And),
            25 => Some(Pop),
            26 => Some(IsNull),
            27 => Some(NullCheck),
            28 => Some(EqLong),
            29 => Some(EqDouble),
            30 => Some(EqBool),
            31 => Some(EqChar),
            32 => Some(EqString),
            33 => Some(EqObject),
            34 => Some(EqEnum),
            35 => Some(EqDyn),
            36 => Some(LtLong),
            37 => Some(LeLong),
            38 => Some(GtLong),
            39 => Some(GeLong),
            40 => Some(LtDouble),
            41 => Some(LeDouble),
            42 => Some(GtDouble),
            43 => Some(GeDouble),
            44 => Some(LtChar),
            45 => Some(LeChar),
            46 => Some(GtChar),
            47 => Some(GeChar),
            48 => Some(LtString),
            49 => Some(LeString),
            50 => Some(GtString),
            51 => Some(GeString),
            52 => Some(LtDyn),
            53 => Some(LeDyn),
            54 => Some(GtDyn),
            55 => Some(GeDyn),
            56 => Some(Jump),
            57 => Some(JumpIfFalse),
            58 => Some(JumpIfTrue),
            59 => Some(CallFn),
            60 => Some(CallStatic),
            61 => Some(CallClass),
            62 => Some(CallInterface),
            63 => Some(CallNative),
            64 => Some(CallDynamic),
            65 => Some(NewObject),
            66 => Some(LoadField),
            67 => Some(StoreField),
            68 => Some(IdentityEq),
            69 => Some(IdentityNe),
            70 => Some(NewList),
            71 => Some(NewMap),
            72 => Some(NewStack),
            73 => Some(ListSpread),
            74 => Some(ListAdd),
            75 => Some(ListGet),
            76 => Some(ListSet),
            77 => Some(ListRemove),
            78 => Some(ListLen),
            79 => Some(ListContains),
            80 => Some(ListIndexOf),
            81 => Some(ListReverse),
            82 => Some(ListSort),
            83 => Some(ListJoin),
            84 => Some(ListClear),
            85 => Some(MapPut),
            86 => Some(MapGet),
            87 => Some(MapRemove),
            88 => Some(MapContainsKey),
            89 => Some(MapLen),
            90 => Some(MapKeys),
            91 => Some(MapValues),
            92 => Some(MapClear),
            93 => Some(StackPush),
            94 => Some(StackPop),
            95 => Some(StackPeek),
            96 => Some(StackGet),
            97 => Some(StackLen),
            98 => Some(StackEmpty),
            99 => Some(StrLen),
            100 => Some(StrConcat),
            101 => Some(StrSubstr),
            102 => Some(StrContains),
            103 => Some(StrStartsWith),
            104 => Some(StrEndsWith),
            105 => Some(StrSplit),
            106 => Some(StrReplace),
            107 => Some(StrTrim),
            108 => Some(StrUpper),
            109 => Some(StrLower),
            110 => Some(StrIndex),
            111 => Some(StrCharAt),
            112 => Some(NewEnum),
            113 => Some(EnumIndex),
            114 => Some(EnumPayload),
            115 => Some(Throw),
            116 => Some(TryBegin),
            117 => Some(TryEnd),
            118 => Some(Return),
            119 => Some(ReturnVoid),
            120 => Some(Dup),
            121 => Some(GcHint),
            122 => Some(FinallyEnd),
            123 => Some(FinallyDivert),
            124 => Some(ListExtend),
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
    LoadGlobal(u16),
    StoreGlobal(u16),
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
    NewList(u16),
    NewMap(u16),
    NewStack,
    NewEnum(u16, u8, bool),
    TryBegin(u32, u32),
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
            IrInstr::LoadGlobal(s) => write!(f, "LoadGlobal({s})"),
            IrInstr::StoreGlobal(s) => write!(f, "StoreGlobal({s})"),
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
            IrInstr::NewList(c) => write!(f, "NewList({c})"),
            IrInstr::NewMap(c) => write!(f, "NewMap({c})"),
            IrInstr::NewStack => write!(f, "NewStack"),
            IrInstr::NewEnum(a, b, c) => write!(f, "NewEnum({a}, {b}, {c})"),
            IrInstr::TryBegin(a, b) => write!(f, "TryBegin({a}, {b})"),
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
            LoadGlobal(_) => Some(IrOp::LoadGlobal),
            StoreGlobal(_) => Some(IrOp::StoreGlobal),
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
            NewList(_) => Some(IrOp::NewList),
            NewMap(_) => Some(IrOp::NewMap),
            NewStack => Some(IrOp::NewStack),
            NewEnum(..) => Some(IrOp::NewEnum),
            TryBegin(..) => Some(IrOp::TryBegin),
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
        IrConst::Long(v) => v.hash(&mut h),
        IrConst::Double(v) => {
            v.to_bits().hash(&mut h);
        }
        IrConst::Char(v) => v.hash(&mut h),
        IrConst::Str(v) => v.hash(&mut h),
    }
    h.finish()
}
