//! Bytecode verification before execution.
//!
//! Port of internal/verifier/verifier.go (Verify path only; the runtime never
//! calls VerifyStackDepth).

use crate::bytecode::{self, Opcode};

pub fn verify(prog: &bytecode::Program) -> Result<(), String> {
    if prog.magic != bytecode::MAGIC {
        return Err(format!("invalid magic: {:x}", prog.magic));
    }
    if prog.version > bytecode::FORMAT_VERSION {
        return Err(format!("unsupported version: {}", prog.version));
    }

    if prog.functions.len() > 65535 {
        return Err(format!("too many functions: {}", prog.functions.len()));
    }

    for (fn_idx, f) in prog.functions.iter().enumerate() {
        verify_function(prog, fn_idx, f)?;
    }
    Ok(())
}

fn verify_function(prog: &bytecode::Program, fn_idx: usize, f: &bytecode::Function) -> Result<(), String> {
    if f.param_count > 255 {
        return Err(format!("function {}: too many parameters: {}", fn_idx, f.param_count));
    }
    if f.local_count > 65535 {
        return Err(format!("function {}: too many locals: {}", fn_idx, f.local_count));
    }
    if f.max_stack > 65535 {
        return Err(format!("function {}: max stack too large: {}", fn_idx, f.max_stack));
    }
    if f.code.is_empty() {
        return Err(format!("function {}: empty code", fn_idx));
    }

    // Verify code structure
    let mut offset = 0usize;
    let mut instructions = 0usize;
    while offset < f.code.len() {
        instructions += 1;
        if instructions > 1_000_000 {
            return Err("too many instructions".to_string());
        }
        match bytecode::decode_full(&f.code, offset) {
            Some((_, _, next)) => offset = next,
            None => return Err("decode error".to_string()),
        }
    }
    if offset != f.code.len() {
        return Err("code length mismatch".to_string());
    }

    verify_instructions(prog, f)?;
    verify_constants(f)?;
    verify_jumps(f)
}

fn verify_instructions(prog: &bytecode::Program, f: &bytecode::Function) -> Result<(), String> {
    let mut offset = 0usize;
    let total_locals = f.param_count + f.local_count;
    let const_count = f.constants.len();

    while offset < f.code.len() {
        let (op, operands, next) = match bytecode::decode_full(&f.code, offset) {
            Some(d) => d,
            None => return Err("decode error".to_string()),
        };

        if op == Opcode::LoadLocal as u8 || op == Opcode::StoreLocal as u8 {
            let idx = operands[0] as usize;
            if idx >= total_locals {
                return Err(format!(
                    "local index {} out of range (max {})",
                    idx,
                    total_locals.saturating_sub(1)
                ));
            }
        } else if op == Opcode::ConstString as u8 {
            let idx = operands[0] as usize;
            if idx >= const_count {
                return Err(format!("constant index {} out of range", idx));
            }
            if f.constants[idx].kind != bytecode::ConstantKind::Str {
                return Err(format!("expected string constant at index {}", idx));
            }
        } else if op == Opcode::Call as u8 {
            let fn_idx2 = operands[0] as usize;
            let arg_count = operands[1] as usize;
            if fn_idx2 >= prog.functions.len() {
                return Err(format!("function index {} out of range", fn_idx2));
            }
            let target = &prog.functions[fn_idx2];
            if target.param_count != arg_count {
                return Err(format!(
                    "function {} expects {} arguments but call has {}",
                    target.name, target.param_count, arg_count
                ));
            }
        } else if op == Opcode::CallNative as u8 {
            let native_idx = operands[0] as usize;
            let arg_count = operands[1] as usize;
            if native_idx >= prog.natives.len() {
                return Err(format!("native function index {} out of range", native_idx));
            }
            let nd = &prog.natives[native_idx];
            if nd.params >= 0 && nd.params as usize != arg_count {
                return Err(format!(
                    "native {}.{} expects {} arguments but call has {}",
                    nd.module, nd.name, nd.params, arg_count
                ));
            }
        } else if op == Opcode::ReturnMulti as u8 {
            let count = operands[0];
            if count > 255 {
                return Err(format!("RETURN_MULTI count {} out of range", count));
            }
        } else if op == Opcode::SetupHandler as u8 {
            let catch_off = operands[0] as u32 as i32;
            let finally_off = operands[1] as u32 as i32;
            if catch_off == 0 && finally_off == 0 {
                return Err("SETUP_HANDLER requires at least one of catch or finally".to_string());
            }
            if catch_off != 0 {
                let catch_target = next as i64 + catch_off as i64;
                if catch_target < 0 || catch_target > f.code.len() as i64 {
                    return Err(format!("catch target {} out of range", catch_target));
                }
            }
            if finally_off != 0 {
                let ftarget = next as i64 + finally_off as i64;
                if ftarget < 0 || ftarget > f.code.len() as i64 {
                    return Err(format!("finally target {} out of range", ftarget));
                }
            }
        }

        offset = next;
    }
    Ok(())
}

fn verify_constants(f: &bytecode::Function) -> Result<(), String> {
    use bytecode::ConstantKind;
    for (i, c) in f.constants.iter().enumerate() {
        match c.kind {
            ConstantKind::None => return Err(format!("constant {} has no kind", i)),
            ConstantKind::Bool
            | ConstantKind::Byte
            | ConstantKind::Int
            | ConstantKind::Float
            | ConstantKind::Char
            | ConstantKind::Str => {}
        }
    }
    Ok(())
}

fn verify_jumps(f: &bytecode::Function) -> Result<(), String> {
    let code = &f.code;
    let mut offset = 0usize;
    while offset < code.len() {
        let (op, operands, next) = match bytecode::decode_full(code, offset) {
            Some(d) => d,
            None => return Err("decode error".to_string()),
        };
        if matches!(
            op,
            x if x == Opcode::Jump as u8
                || x == Opcode::JumpIfFalse as u8
                || x == Opcode::JumpIfTrue as u8
                || x == Opcode::JumpIfNotNull as u8
        ) {
            let jump_offset = operands[0] as u32 as i32;
            let target = next as i64 + jump_offset as i64;
            if target < 0 || target > code.len() as i64 {
                return Err(format!(
                    "jump target {} out of range (code size {})",
                    target,
                    code.len()
                ));
            }
        }
        offset = next;
    }
    Ok(())
}
