//! Bytecode instruction set and encoding/decoding.
//!
//! Port of internal/bytecode/bytecode.go (serialization is not needed by the
//! CLI pipeline and is omitted).

pub const MAGIC: u32 = 0x4C414E47; // "LANG"
pub const FORMAT_VERSION: u32 = 8;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
#[repr(u8)]
pub enum Opcode {
    Nop = 0,
    ConstBool,
    ConstByte,
    ConstInt,
    ConstFloat,
    ConstChar,
    ConstString,
    ConstNull,
    LoadLocal,
    StoreLocal,
    Pop,
    Dup,
    AddInt,
    SubInt,
    MulInt,
    DivInt,
    RemInt,
    NegInt,
    AddFloat,
    SubFloat,
    MulFloat,
    DivFloat,
    RemFloat,
    NegFloat,
    ConcatString,
    EqBool,
    EqInt,
    EqFloat,
    EqChar,
    EqString,
    EqRef,
    LtInt,
    LeInt,
    GtInt,
    GeInt,
    LtFloat,
    LeFloat,
    GtFloat,
    GeFloat,
    NotBool,
    BitAndInt,
    BitOrInt,
    BitXorInt,
    BitNotInt,
    ShiftLeftInt,
    ShiftRightInt,
    Jump,
    JumpIfFalse,
    JumpIfTrue,
    JumpIfNotNull,
    Call,
    CallNative,
    Return,
    ReturnVoid,
    ReturnMulti,
    NewList,
    ListGet,
    ListSet,
    ListAppend,
    ListExtend,
    ListLength,
    NewMap,
    MapGet,
    MapSet,
    MapKeys,
    CheckNotNull,
    CheckType,
    NewStack,
    StackPush,
    StackPop,
    StackPeek,
    StackSize,
    StructNew,
    FieldLoad,
    FieldStore,
    TraitNew,
    TraitInvoke,
    ConvertByteToInt,
    ConvertIntToFloat,
    Throw,
    SetupHandler,
    RemoveHandler,
    NewException,
    ExceptionField,
}

pub const OP_MAX: usize = Opcode::ExceptionField as usize + 1;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum OperandType {
    None,
    Uint8,
    Uint16,
    Uint32,
    Int32,
    Int64,
    Float32,
    Float64,
    Str,
    FuncIndex,
}

pub struct InstructionInfo {
    pub opcode: Opcode,
    pub name: &'static str,
    pub operands: &'static [OperandType],
    pub pop_count: i32,
    pub push_count: i32,
}

use OperandType::*;

static INSTRUCTIONS: [InstructionInfo; OP_MAX] = [
    InstructionInfo { opcode: Opcode::Nop, name: "NOP", operands: &[], pop_count: 0, push_count: 0 },
    InstructionInfo { opcode: Opcode::ConstBool, name: "CONST_BOOL", operands: &[Uint8], pop_count: 0, push_count: 1 },
    InstructionInfo { opcode: Opcode::ConstByte, name: "CONST_BYTE", operands: &[Uint8], pop_count: 0, push_count: 1 },
    InstructionInfo { opcode: Opcode::ConstInt, name: "CONST_INT", operands: &[Int64], pop_count: 0, push_count: 1 },
    InstructionInfo { opcode: Opcode::ConstFloat, name: "CONST_FLOAT", operands: &[Float64], pop_count: 0, push_count: 1 },
    InstructionInfo { opcode: Opcode::ConstChar, name: "CONST_CHAR", operands: &[Uint32], pop_count: 0, push_count: 1 },
    InstructionInfo { opcode: Opcode::ConstString, name: "CONST_STRING", operands: &[Str], pop_count: 0, push_count: 1 },
    InstructionInfo { opcode: Opcode::ConstNull, name: "CONST_NULL", operands: &[], pop_count: 0, push_count: 1 },
    InstructionInfo { opcode: Opcode::LoadLocal, name: "LOAD_LOCAL", operands: &[Uint16], pop_count: 0, push_count: 1 },
    InstructionInfo { opcode: Opcode::StoreLocal, name: "STORE_LOCAL", operands: &[Uint16], pop_count: 1, push_count: 0 },
    InstructionInfo { opcode: Opcode::Pop, name: "POP", operands: &[], pop_count: 1, push_count: 0 },
    InstructionInfo { opcode: Opcode::Dup, name: "DUP", operands: &[], pop_count: 1, push_count: 2 },
    InstructionInfo { opcode: Opcode::AddInt, name: "ADD_INT", operands: &[], pop_count: 2, push_count: 1 },
    InstructionInfo { opcode: Opcode::SubInt, name: "SUB_INT", operands: &[], pop_count: 2, push_count: 1 },
    InstructionInfo { opcode: Opcode::MulInt, name: "MUL_INT", operands: &[], pop_count: 2, push_count: 1 },
    InstructionInfo { opcode: Opcode::DivInt, name: "DIV_INT", operands: &[], pop_count: 2, push_count: 1 },
    InstructionInfo { opcode: Opcode::RemInt, name: "REM_INT", operands: &[], pop_count: 2, push_count: 1 },
    InstructionInfo { opcode: Opcode::NegInt, name: "NEG_INT", operands: &[], pop_count: 1, push_count: 1 },
    InstructionInfo { opcode: Opcode::AddFloat, name: "ADD_FLOAT", operands: &[], pop_count: 2, push_count: 1 },
    InstructionInfo { opcode: Opcode::SubFloat, name: "SUB_FLOAT", operands: &[], pop_count: 2, push_count: 1 },
    InstructionInfo { opcode: Opcode::MulFloat, name: "MUL_FLOAT", operands: &[], pop_count: 2, push_count: 1 },
    InstructionInfo { opcode: Opcode::DivFloat, name: "DIV_FLOAT", operands: &[], pop_count: 2, push_count: 1 },
    InstructionInfo { opcode: Opcode::RemFloat, name: "REM_FLOAT", operands: &[], pop_count: 2, push_count: 1 },
    InstructionInfo { opcode: Opcode::NegFloat, name: "NEG_FLOAT", operands: &[], pop_count: 1, push_count: 1 },
    InstructionInfo { opcode: Opcode::ConcatString, name: "CONCAT_STRING", operands: &[], pop_count: 2, push_count: 1 },
    InstructionInfo { opcode: Opcode::EqBool, name: "EQ_BOOL", operands: &[], pop_count: 2, push_count: 1 },
    InstructionInfo { opcode: Opcode::EqInt, name: "EQ_INT", operands: &[], pop_count: 2, push_count: 1 },
    InstructionInfo { opcode: Opcode::EqFloat, name: "EQ_FLOAT", operands: &[], pop_count: 2, push_count: 1 },
    InstructionInfo { opcode: Opcode::EqChar, name: "EQ_CHAR", operands: &[], pop_count: 2, push_count: 1 },
    InstructionInfo { opcode: Opcode::EqString, name: "EQ_STRING", operands: &[], pop_count: 2, push_count: 1 },
    InstructionInfo { opcode: Opcode::EqRef, name: "EQ_REF", operands: &[], pop_count: 2, push_count: 1 },
    InstructionInfo { opcode: Opcode::LtInt, name: "LT_INT", operands: &[], pop_count: 2, push_count: 1 },
    InstructionInfo { opcode: Opcode::LeInt, name: "LE_INT", operands: &[], pop_count: 2, push_count: 1 },
    InstructionInfo { opcode: Opcode::GtInt, name: "GT_INT", operands: &[], pop_count: 2, push_count: 1 },
    InstructionInfo { opcode: Opcode::GeInt, name: "GE_INT", operands: &[], pop_count: 2, push_count: 1 },
    InstructionInfo { opcode: Opcode::LtFloat, name: "LT_FLOAT", operands: &[], pop_count: 2, push_count: 1 },
    InstructionInfo { opcode: Opcode::LeFloat, name: "LE_FLOAT", operands: &[], pop_count: 2, push_count: 1 },
    InstructionInfo { opcode: Opcode::GtFloat, name: "GT_FLOAT", operands: &[], pop_count: 2, push_count: 1 },
    InstructionInfo { opcode: Opcode::GeFloat, name: "GE_FLOAT", operands: &[], pop_count: 2, push_count: 1 },
    InstructionInfo { opcode: Opcode::NotBool, name: "NOT_BOOL", operands: &[], pop_count: 1, push_count: 1 },
    InstructionInfo { opcode: Opcode::BitAndInt, name: "BIT_AND_INT", operands: &[], pop_count: 2, push_count: 1 },
    InstructionInfo { opcode: Opcode::BitOrInt, name: "BIT_OR_INT", operands: &[], pop_count: 2, push_count: 1 },
    InstructionInfo { opcode: Opcode::BitXorInt, name: "BIT_XOR_INT", operands: &[], pop_count: 2, push_count: 1 },
    InstructionInfo { opcode: Opcode::BitNotInt, name: "BIT_NOT_INT", operands: &[], pop_count: 1, push_count: 1 },
    InstructionInfo { opcode: Opcode::ShiftLeftInt, name: "SHIFT_LEFT_INT", operands: &[], pop_count: 2, push_count: 1 },
    InstructionInfo { opcode: Opcode::ShiftRightInt, name: "SHIFT_RIGHT_INT", operands: &[], pop_count: 2, push_count: 1 },
    InstructionInfo { opcode: Opcode::Jump, name: "JUMP", operands: &[Int32], pop_count: 0, push_count: 0 },
    InstructionInfo { opcode: Opcode::JumpIfFalse, name: "JUMP_IF_FALSE", operands: &[Int32], pop_count: 1, push_count: 0 },
    InstructionInfo { opcode: Opcode::JumpIfTrue, name: "JUMP_IF_TRUE", operands: &[Int32], pop_count: 1, push_count: 0 },
    InstructionInfo { opcode: Opcode::JumpIfNotNull, name: "JUMP_IF_NOT_NULL", operands: &[Int32], pop_count: 1, push_count: 0 },
    InstructionInfo { opcode: Opcode::Call, name: "CALL", operands: &[FuncIndex, Uint8], pop_count: 0, push_count: 0 },
    InstructionInfo { opcode: Opcode::CallNative, name: "CALL_NATIVE", operands: &[Uint16, Uint8], pop_count: 0, push_count: 0 },
    InstructionInfo { opcode: Opcode::Return, name: "RETURN", operands: &[], pop_count: 1, push_count: 0 },
    InstructionInfo { opcode: Opcode::ReturnVoid, name: "RETURN_VOID", operands: &[], pop_count: 0, push_count: 0 },
    InstructionInfo { opcode: Opcode::ReturnMulti, name: "RETURN_MULTI", operands: &[Uint8], pop_count: 0, push_count: 0 },
    InstructionInfo { opcode: Opcode::NewList, name: "NEW_LIST", operands: &[Uint8], pop_count: 0, push_count: 1 },
    InstructionInfo { opcode: Opcode::ListGet, name: "LIST_GET", operands: &[], pop_count: 2, push_count: 1 },
    InstructionInfo { opcode: Opcode::ListSet, name: "LIST_SET", operands: &[], pop_count: 3, push_count: 0 },
    InstructionInfo { opcode: Opcode::ListAppend, name: "LIST_APPEND", operands: &[], pop_count: 2, push_count: 1 },
    InstructionInfo { opcode: Opcode::ListExtend, name: "LIST_EXTEND", operands: &[], pop_count: 2, push_count: 1 },
    InstructionInfo { opcode: Opcode::ListLength, name: "LIST_LENGTH", operands: &[], pop_count: 1, push_count: 1 },
    InstructionInfo { opcode: Opcode::NewMap, name: "NEW_MAP", operands: &[], pop_count: 0, push_count: 1 },
    InstructionInfo { opcode: Opcode::MapGet, name: "MAP_GET", operands: &[], pop_count: 2, push_count: 1 },
    InstructionInfo { opcode: Opcode::MapSet, name: "MAP_SET", operands: &[], pop_count: 3, push_count: 1 },
    InstructionInfo { opcode: Opcode::MapKeys, name: "MAP_KEYS", operands: &[], pop_count: 1, push_count: 1 },
    InstructionInfo { opcode: Opcode::CheckNotNull, name: "CHECK_NOT_NULL", operands: &[], pop_count: 1, push_count: 1 },
    InstructionInfo { opcode: Opcode::CheckType, name: "CHECK_TYPE", operands: &[Str, Uint8], pop_count: 1, push_count: 1 },
    InstructionInfo { opcode: Opcode::NewStack, name: "NEW_STACK", operands: &[], pop_count: 0, push_count: 1 },
    InstructionInfo { opcode: Opcode::StackPush, name: "STACK_PUSH", operands: &[], pop_count: 2, push_count: 1 },
    InstructionInfo { opcode: Opcode::StackPop, name: "STACK_POP", operands: &[], pop_count: 1, push_count: 2 },
    InstructionInfo { opcode: Opcode::StackPeek, name: "STACK_PEEK", operands: &[], pop_count: 1, push_count: 1 },
    InstructionInfo { opcode: Opcode::StackSize, name: "STACK_SIZE", operands: &[], pop_count: 1, push_count: 1 },
    InstructionInfo { opcode: Opcode::StructNew, name: "STRUCT_NEW", operands: &[Uint8, Uint16], pop_count: 0, push_count: 1 },
    InstructionInfo { opcode: Opcode::FieldLoad, name: "FIELD_LOAD", operands: &[Uint16], pop_count: 1, push_count: 1 },
    InstructionInfo { opcode: Opcode::FieldStore, name: "FIELD_STORE", operands: &[Uint16], pop_count: 2, push_count: 0 },
    InstructionInfo { opcode: Opcode::TraitNew, name: "TRAIT_NEW", operands: &[Uint16, Uint16], pop_count: 1, push_count: 1 },
    InstructionInfo { opcode: Opcode::TraitInvoke, name: "TRAIT_INVOKE", operands: &[Uint16, Uint8], pop_count: 0, push_count: 0 },
    InstructionInfo { opcode: Opcode::ConvertByteToInt, name: "CONVERT_BYTE_TO_INT", operands: &[], pop_count: 1, push_count: 1 },
    InstructionInfo { opcode: Opcode::ConvertIntToFloat, name: "CONVERT_INT_TO_FLOAT", operands: &[], pop_count: 1, push_count: 1 },
    InstructionInfo { opcode: Opcode::Throw, name: "THROW", operands: &[], pop_count: 1, push_count: 0 },
    InstructionInfo { opcode: Opcode::SetupHandler, name: "SETUP_HANDLER", operands: &[Int32, Int32, Uint16], pop_count: 0, push_count: 0 },
    InstructionInfo { opcode: Opcode::RemoveHandler, name: "REMOVE_HANDLER", operands: &[], pop_count: 0, push_count: 0 },
    InstructionInfo { opcode: Opcode::NewException, name: "NEW_EXCEPTION", operands: &[], pop_count: 1, push_count: 1 },
    InstructionInfo { opcode: Opcode::ExceptionField, name: "EXCEPTION_FIELD", operands: &[Uint8], pop_count: 1, push_count: 1 },
];

pub fn instruction_info(op: u8) -> Option<&'static InstructionInfo> {
    INSTRUCTIONS.get(op as usize)
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ConstantKind {
    None = 0,
    Bool = 1,
    Byte = 2,
    Int = 3,
    Float = 4,
    Char = 5,
    Str = 6,
}

#[derive(Clone, Debug)]
pub struct Constant {
    pub kind: ConstantKind,
    pub data: u64,
    pub s: String,
}

#[derive(Clone, Debug, Default)]
pub struct SourceSpan {
    pub start_line: u16,
    pub start_col: u16,
    pub end_line: u16,
    pub end_col: u16,
}

#[derive(Default)]
pub struct Function {
    pub name: String,
    pub param_count: usize,
    pub local_count: usize,
    pub max_stack: usize,
    pub return_count: usize,
    pub code: Vec<u8>,
    pub constants: Vec<Constant>,
    pub source_map: Vec<SourceSpan>,
}

#[derive(Clone, Debug)]
pub struct NativeDecl {
    pub module: String,
    pub name: String,
    pub params: i32, // -1 = variadic
    pub ret: bool,
}

#[derive(Clone, Debug)]
pub struct TraitMethodTable {
    pub trait_name: String,
    pub struct_type_name: String,
    pub method_indices: Vec<usize>,
}

pub struct Program {
    pub magic: u32,
    pub version: u32,
    pub module_name: String,
    pub functions: Vec<Function>,
    pub globals: usize,
    pub natives: Vec<NativeDecl>,
    pub trait_tables: Vec<TraitMethodTable>,
}

impl Program {
    pub fn new() -> Program {
        Program {
            magic: MAGIC,
            version: FORMAT_VERSION,
            module_name: String::new(),
            functions: Vec::new(),
            globals: 0,
            natives: Vec::new(),
            trait_tables: Vec::new(),
        }
    }
}


/// Decodes the opcode and up to 2 operands at `offset` without allocation.
/// Returns (opcode, [operands], next_offset).
pub fn decode_op(code: &[u8], offset: usize) -> Option<(u8, [u64; 2], usize)> {
    if offset >= code.len() {
        return Option::None;
    }
    let op = code[offset];
    let info = instruction_info(op)?;
    let mut operands = [0u64; 2];
    let mut pos = offset + 1;

    for (i, &op_type) in info.operands.iter().enumerate() {
        if i >= 2 {
            break;
        }
        match op_type {
            OperandType::Uint8 => {
                if pos >= code.len() {
                    return Option::None;
                }
                operands[i] = code[pos] as u64;
                pos += 1;
            }
            OperandType::Uint16 => {
                if pos + 2 > code.len() {
                    return Option::None;
                }
                operands[i] = u16::from_be_bytes([code[pos], code[pos + 1]]) as u64;
                pos += 2;
            }
            OperandType::Uint32 | OperandType::Float32 | OperandType::Int32 => {
                if pos + 4 > code.len() {
                    return Option::None;
                }
                operands[i] = u32::from_be_bytes([code[pos], code[pos + 1], code[pos + 2], code[pos + 3]]) as u64;
                pos += 4;
            }
            OperandType::Int64 | OperandType::Float64 => {
                if pos + 8 > code.len() {
                    return Option::None;
                }
                let mut b = [0u8; 8];
                b.copy_from_slice(&code[pos..pos + 8]);
                operands[i] = u64::from_be_bytes(b);
                pos += 8;
            }
            OperandType::Str | OperandType::FuncIndex => {
                if pos + 4 > code.len() {
                    return Option::None;
                }
                operands[i] = u32::from_be_bytes([code[pos], code[pos + 1], code[pos + 2], code[pos + 3]]) as u64;
                pos += 4;
            }
            OperandType::None => {}
        }
    }

    Some((op, operands, pos))
}

/// Size in bytes of the instruction with the given opcode.
pub fn instruction_size(op: u8) -> Option<usize> {
    let info = instruction_info(op)?;
    let mut size = 1;
    for &t in info.operands {
        size += match t {
            OperandType::None => 0,
            OperandType::Uint8 => 1,
            OperandType::Uint16 => 2,
            OperandType::Uint32 | OperandType::Float32 | OperandType::Int32 => 4,
            OperandType::Int64 | OperandType::Float64 => 8,
            OperandType::Str | OperandType::FuncIndex => 4,
        };
    }
    Some(size)
}

/// Full instruction decode (all operands), mirroring Go's bytecode.Decode.
/// Returns (opcode, operands, next_offset).
pub fn decode_full(code: &[u8], offset: usize) -> Option<(u8, Vec<u64>, usize)> {
    if offset >= code.len() {
        return Option::None;
    }
    let op = code[offset];
    let info = instruction_info(op)?;
    let mut operands: Vec<u64> = Vec::with_capacity(info.operands.len());
    let mut pos = offset + 1;

    for &op_type in info.operands {
        match op_type {
            OperandType::Uint8 => {
                if pos >= code.len() {
                    return Option::None;
                }
                operands.push(code[pos] as u64);
                pos += 1;
            }
            OperandType::Uint16 => {
                if pos + 2 > code.len() {
                    return Option::None;
                }
                operands.push(u16::from_be_bytes([code[pos], code[pos + 1]]) as u64);
                pos += 2;
            }
            OperandType::Uint32 | OperandType::Float32 | OperandType::Int32 => {
                if pos + 4 > code.len() {
                    return Option::None;
                }
                operands.push(u32::from_be_bytes([code[pos], code[pos + 1], code[pos + 2], code[pos + 3]]) as u64);
                pos += 4;
            }
            OperandType::Int64 | OperandType::Float64 => {
                if pos + 8 > code.len() {
                    return Option::None;
                }
                let mut b = [0u8; 8];
                b.copy_from_slice(&code[pos..pos + 8]);
                operands.push(u64::from_be_bytes(b));
                pos += 8;
            }
            OperandType::Str | OperandType::FuncIndex => {
                if pos + 4 > code.len() {
                    return Option::None;
                }
                operands.push(u32::from_be_bytes([code[pos], code[pos + 1], code[pos + 2], code[pos + 3]]) as u64);
                pos += 4;
            }
            OperandType::None => {}
        }
    }

    Some((op, operands, pos))
}
