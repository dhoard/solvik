//! Bytecode compiler: lowers the resolved IR module into a `CodeModule`.
//!
//! The compiler consumes only IR (never AST): it assigns byte offsets,
//! fixes up jump targets, and copies dispatch metadata.

use crate::bytecode::{ClassMeta, CodeFunction, CodeModule, ConstVal, IfaceMeta};
use crate::ir::{IrInstr, IrModule};
use crate::source::SourceManager;

/// Size in bytes of one encoded instruction (before jump fixup).
fn instr_size(instr: &IrInstr) -> usize {
    use IrInstr::*;
    match instr {
        Op(op) => 1 + op.operand_count() * operand_size(*op),
        LoadConst(_) => 5,
        LoadLocal(_) | StoreLocal(_) | LoadGlobal(_) | StoreGlobal(_) => 3,
        Jump(_) | JumpIfFalse(_) | JumpIfTrue(_) => 5,
        CallFn(..) => 7,
        CallStatic(..) => 9,
        CallClass(..) | CallInterface(..) => 7,
        CallNative(..) | CallDynamic(..) => 5,
        NewObject(..) => 5,
        LoadField(_) | StoreField(_) => 3,
        LoadStatic(..) | StoreStatic(..) => 5,
        NewList(_) | NewMap(_) => 3,
        NewStack => 1,
        NewEnum(..) => 5,
        TryBegin(..) => 9,
        Return | ReturnVoid | GcHint => 1,
    }
}

fn operand_size(op: crate::ir::IrOp) -> usize {
    use crate::ir::IrOp::*;
    match op {
        LoadConst | Jump | JumpIfFalse | JumpIfTrue | CallFn | CallStatic => 4,
        _ => 2,
    }
}

fn push_u16(out: &mut Vec<u8>, v: u16) {
    out.extend_from_slice(&v.to_le_bytes());
}

fn push_u32(out: &mut Vec<u8>, v: u32) {
    out.extend_from_slice(&v.to_le_bytes());
}

/// Encode one instruction given its own byte offset and the instruction
/// offset table (for jump targets).
fn encode_instr(out: &mut Vec<u8>, instr: &IrInstr, offsets: &[u32]) {
    use crate::ir::IrOp;
    use IrInstr::*;
    match instr {
        Op(op) => {
            out.push(op.code());
            // Generic operand emission for plain ops (none carry operands
            // beyond what dedicated variants cover).
            debug_assert_eq!(op.operand_count(), 0);
        }
        LoadConst(c) => {
            out.push(IrOp::LoadConst.code());
            push_u32(out, *c);
        }
        LoadLocal(s) => {
            out.push(IrOp::LoadLocal.code());
            push_u16(out, *s);
        }
        StoreLocal(s) => {
            out.push(IrOp::StoreLocal.code());
            push_u16(out, *s);
        }
        LoadGlobal(s) => {
            out.push(IrOp::LoadGlobal.code());
            push_u16(out, *s);
        }
        StoreGlobal(s) => {
            out.push(IrOp::StoreGlobal.code());
            push_u16(out, *s);
        }
        Jump(t) => {
            out.push(IrOp::Jump.code());
            push_u32(out, target_offset(*t, offsets));
        }
        JumpIfFalse(t) => {
            out.push(IrOp::JumpIfFalse.code());
            push_u32(out, target_offset(*t, offsets));
        }
        JumpIfTrue(t) => {
            out.push(IrOp::JumpIfTrue.code());
            push_u32(out, target_offset(*t, offsets));
        }
        CallFn(fid, arity) => {
            out.push(IrOp::CallFn.code());
            push_u32(out, *fid);
            push_u16(out, *arity);
        }
        CallStatic(fid, arity, target) => {
            out.push(IrOp::CallStatic.code());
            push_u32(out, *fid);
            push_u16(out, *arity);
            push_u16(out, *target);
        }
        CallClass(class, slot, arity) => {
            out.push(IrOp::CallClass.code());
            push_u16(out, *class);
            push_u16(out, *slot);
            push_u16(out, *arity);
        }
        CallInterface(iface, slot, arity) => {
            out.push(IrOp::CallInterface.code());
            push_u16(out, *iface);
            push_u16(out, *slot);
            push_u16(out, *arity);
        }
        CallNative(native, arity) => {
            out.push(IrOp::CallNative.code());
            push_u16(out, *native);
            push_u16(out, *arity);
        }
        CallDynamic(name_id, arity) => {
            out.push(IrOp::CallDynamic.code());
            push_u16(out, *name_id);
            push_u16(out, *arity);
        }
        NewObject(class, fields) => {
            out.push(IrOp::NewObject.code());
            push_u16(out, *class);
            push_u16(out, *fields);
        }
        LoadField(slot) => {
            out.push(IrOp::LoadField.code());
            push_u16(out, *slot);
        }
        StoreField(slot) => {
            out.push(IrOp::StoreField.code());
            push_u16(out, *slot);
        }
        LoadStatic(class, slot) => {
            out.push(IrOp::LoadStatic.code());
            push_u16(out, *class);
            push_u16(out, *slot);
        }
        StoreStatic(class, slot) => {
            out.push(IrOp::StoreStatic.code());
            push_u16(out, *class);
            push_u16(out, *slot);
        }
        NewList(cap) => {
            out.push(IrOp::NewList.code());
            push_u16(out, *cap);
        }
        NewMap(cap) => {
            out.push(IrOp::NewMap.code());
            push_u16(out, *cap);
        }
        NewStack => out.push(IrOp::NewStack.code()),
        NewEnum(eid, vi, has_payload) => {
            out.push(IrOp::NewEnum.code());
            push_u16(out, *eid);
            out.push(*vi);
            out.push(u8::from(*has_payload));
        }
        TryBegin(catch_ip, finally_ip) => {
            out.push(IrOp::TryBegin.code());
            push_u32(out, target_offset(*catch_ip, offsets));
            push_u32(out, target_offset(*finally_ip, offsets));
        }
        Return => out.push(IrOp::Return.code()),
        ReturnVoid => out.push(IrOp::ReturnVoid.code()),
        GcHint => out.push(IrOp::GcHint.code()),
    }
}

fn target_offset(instr_idx: u32, offsets: &[u32]) -> u32 {
    offsets.get(instr_idx as usize).copied().unwrap_or(0)
}

/// Compile one function's instruction stream.
fn compile_function(ir: &IrModule, fidx: usize) -> CodeFunction {
    let func = &ir.functions[fidx];
    // Pass 1: byte offset per instruction index.
    let mut offsets = Vec::with_capacity(func.instrs.len());
    let mut off: u32 = 0;
    for instr in &func.instrs {
        offsets.push(off);
        off += instr_size(instr) as u32;
    }
    // Pass 2: emit.
    let mut code = Vec::with_capacity(off as usize);
    for instr in &func.instrs {
        encode_instr(&mut code, instr, &offsets);
    }
    // Line map: IR instruction index -> byte offset.
    let line_map: Vec<(u32, u32)> = func
        .line_map
        .iter()
        .map(|(ip, line)| (target_offset(*ip, &offsets), *line))
        .collect();
    CodeFunction {
        name: func.name.clone(),
        params: func.params.clone(),
        local_count: func.local_count,
        // Filled in by the verifier after the encode/decode round trip.
        max_stack: 0,
        returns_value: func.returns_value,
        code,
        line_map,
        source_file: func.source_file,
    }
}

/// Lower a full IR module into a bytecode module.
pub fn compile_module(ir: &IrModule, sources: &SourceManager) -> CodeModule {
    let constants: Vec<ConstVal> = ir.constants.iter().map(|c| c.clone().into()).collect();
    let functions: Vec<CodeFunction> = (0..ir.functions.len())
        .map(|i| compile_function(ir, i))
        .collect();
    let classes: Vec<ClassMeta> = ir
        .classes
        .iter()
        .map(|c| ClassMeta {
            name: c.name.clone(),
            field_count: c.field_count,
            method_names: c.method_names.clone(),
            method_table: c.method_table.clone(),
            dyn_methods: c.dyn_methods.clone(),
            statics: c.statics.clone(),
            static_fields: c.static_fields.clone(),
            static_init: c.static_init,
            interfaces: c.interfaces.clone(),
        })
        .collect();
    let interfaces: Vec<IfaceMeta> = ir
        .interfaces
        .iter()
        .map(|i| IfaceMeta {
            name: i.name.clone(),
            slots: i.slots.clone(),
            defaults: i.defaults.clone(),
        })
        .collect();
    let sources_list: Vec<String> = sources.file_names();
    CodeModule {
        version: CodeModule::FORMAT_VERSION,
        constants,
        functions,
        classes,
        interfaces,
        dyn_names: ir.dyn_names.clone(),
        entry: ir.entry,
        sources: sources_list,
    }
}
