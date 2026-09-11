//! Bytecode disassembler: renders a decoded `CodeModule` as readable text.
//!
//! Used by `SOLVIK_DUMP_BC=1` and by debugging tools. Jump and handler
//! operands are shown as instruction indices (with byte offsets) so the
//! output lines up with the verifier's view of the code.

use crate::bytecode::{CodeModule, ConstVal};

/// Disassemble every function in the module.
pub fn disassemble(module: &CodeModule) -> String {
    let mut out = String::new();
    out.push_str(&format!(
        "module v{} constants={} functions={} classes={} interfaces={}\n",
        module.version,
        module.constants.len(),
        module.functions.len(),
        module.classes.len(),
        module.interfaces.len()
    ));
    for (i, c) in module.constants.iter().enumerate() {
        out.push_str(&format!("  const {:3} = {}\n", i, const_str(c)));
    }
    for (i, f) in module.functions.iter().enumerate() {
        disassemble_function(&mut out, module, i, f);
    }
    out
}

fn disassemble_function(
    out: &mut String,
    module: &CodeModule,
    fid: usize,
    f: &crate::bytecode::CodeFunction,
) {
    let entry_mark = if module.entry == Some(fid as u32) {
        " *"
    } else {
        ""
    };
    out.push_str(&format!(
        "\n== fn {} {}(locals={} max_stack={} ret={}){}\n",
        fid, f.name, f.local_count, f.max_stack, f.returns_value, entry_mark
    ));

    // Decode the instruction stream into (offset, index, op, operands).
    let mut ip = 0usize;
    let code = &f.code;
    // Byte offset -> instruction index, for jump targets.
    let mut offsets: Vec<u32> = Vec::new();
    while ip < code.len() {
        offsets.push(ip as u32);
        let op =
            crate::ir::IrOp::from_code(code[ip]).unwrap_or_else(|| panic!("bad opcode at {ip}"));
        ip += 1;
        for k in 0..op.operand_count() {
            ip += op.operand_size(k);
        }
    }
    let idx_of = |off: u32| -> String {
        offsets
            .iter()
            .position(|&o| o == off)
            .map(|i| format!("#{i} @ {off}"))
            .unwrap_or_else(|| format!("?@{off}"))
    };

    // Per-instruction line numbers from the (byte offset, line) map.
    let mut lines = vec![0u32; offsets.len()];
    let mut cur = 0u32;
    let mut li = 0usize;
    for (i, &off) in offsets.iter().enumerate() {
        while li < f.line_map.len() && f.line_map[li].0 <= off {
            cur = f.line_map[li].1;
            li += 1;
        }
        lines[i] = cur;
    }

    ip = 0usize;
    let mut n = 0usize;
    while ip < code.len() {
        let start = ip;
        let op =
            crate::ir::IrOp::from_code(code[ip]).unwrap_or_else(|| panic!("bad opcode at {ip}"));
        ip += 1;
        let mut a = [0u32; 3];
        for (k, slot) in a.iter_mut().enumerate().take(op.operand_count()) {
            let size = op.operand_size(k);
            for b in 0..size {
                *slot |= (code[ip + b] as u32) << (8 * b);
            }
            ip += size;
        }
        let line = if lines[n] != 0 {
            format!(" L{}", lines[n])
        } else {
            String::new()
        };
        out.push_str(&format!("{start:5} {:4} {:?}{line}\n", n, op));
        // Render operands with resolved names.
        let arg = |v: u32| v as u16;
        match op {
            crate::ir::IrOp::LoadConst => {
                out.push_str(&format!(
                    "      const {} = {}\n",
                    a[0],
                    const_str(
                        module
                            .constants
                            .get(a[0] as usize)
                            .unwrap_or(&ConstVal::Null)
                    )
                ));
            }
            crate::ir::IrOp::LoadLocal | crate::ir::IrOp::StoreLocal => {
                out.push_str(&format!("      local {}\n", arg(a[0])));
            }
            crate::ir::IrOp::Jump | crate::ir::IrOp::JumpIfFalse | crate::ir::IrOp::JumpIfTrue => {
                out.push_str(&format!("      -> {}\n", idx_of(a[0])));
            }
            crate::ir::IrOp::CallFn => {
                out.push_str(&format!(
                    "      fn {} arity {}\n",
                    func_name(module, a[0] as usize),
                    arg(a[1])
                ));
            }
            crate::ir::IrOp::CallStatic => {
                out.push_str(&format!(
                    "      fn {} arity {} target {}\n",
                    func_name(module, a[0] as usize),
                    arg(a[1]),
                    class_name(module, arg(a[2]))
                ));
            }
            crate::ir::IrOp::CallClass => {
                out.push_str(&format!(
                    "      class {} slot {} ({}) arity {}\n",
                    arg(a[0]),
                    arg(a[1]),
                    method_slot_name(module, arg(a[0]) as usize, arg(a[1]) as usize),
                    arg(a[2])
                ));
            }
            crate::ir::IrOp::CallInterface => {
                out.push_str(&format!(
                    "      iface {} slot {} ({}) arity {}\n",
                    arg(a[0]),
                    arg(a[1]),
                    iface_slot(module, arg(a[0]) as usize, arg(a[1]) as usize),
                    arg(a[2])
                ));
            }
            crate::ir::IrOp::CallNative => {
                out.push_str(&format!("      native#{} arity {}\n", arg(a[0]), arg(a[1])));
            }
            crate::ir::IrOp::CallDynamic => {
                let name = module
                    .dyn_names
                    .get(arg(a[0]) as usize)
                    .cloned()
                    .unwrap_or_default();
                out.push_str(&format!("      dyn `{name}` arity {}\n", arg(a[1])));
            }
            crate::ir::IrOp::NewObject => {
                out.push_str(&format!(
                    "      class {} fields {}\n",
                    class_name(module, arg(a[0])),
                    arg(a[1])
                ));
            }
            crate::ir::IrOp::LoadField | crate::ir::IrOp::StoreField => {
                out.push_str(&format!("      field {}\n", arg(a[0])));
            }
            crate::ir::IrOp::LoadStatic | crate::ir::IrOp::StoreStatic => {
                let cls = arg(a[0]);
                let slot = arg(a[1]) as usize;
                let fname = module
                    .classes
                    .get(cls as usize)
                    .and_then(|c| c.static_fields.get(slot))
                    .map(|(n, _)| n.clone())
                    .unwrap_or_else(|| "?".into());
                out.push_str(&format!(
                    "      static {}.{} (slot {} in {})\n",
                    class_name(module, cls),
                    fname,
                    slot,
                    class_name(module, cls)
                ));
            }
            crate::ir::IrOp::NewList | crate::ir::IrOp::NewMap => {
                out.push_str(&format!("      cap {}\n", arg(a[0])));
            }
            crate::ir::IrOp::NewEnum => {
                out.push_str(&format!(
                    "      enum {} variant {} payload={}\n",
                    arg(a[0]),
                    a[1],
                    a[2] != 0
                ));
            }
            crate::ir::IrOp::TryBegin => {
                out.push_str(&format!(
                    "      catch {} finally {}\n",
                    if a[0] == 0 { "-".into() } else { idx_of(a[0]) },
                    if a[1] == 0 { "-".into() } else { idx_of(a[1]) }
                ));
            }
            _ => {}
        }
        n += 1;
    }
}

fn func_name(module: &CodeModule, fid: usize) -> String {
    module
        .functions
        .get(fid)
        .map(|f| format!("{} ({})", fid, f.name))
        .unwrap_or_else(|| format!("{fid}?"))
}

fn class_name(module: &CodeModule, cid: u16) -> String {
    if cid == 0xFFFF {
        return "<declaring>".into();
    }
    module
        .classes
        .get(cid as usize)
        .map(|c| c.name.clone())
        .unwrap_or_else(|| format!("class#{cid}?"))
}

fn method_slot_name(module: &CodeModule, cid: usize, slot: usize) -> String {
    module
        .classes
        .get(cid)
        .and_then(|c| c.method_names.get(slot))
        .cloned()
        .unwrap_or_else(|| "?".into())
}

fn iface_slot(module: &CodeModule, iid: usize, slot: usize) -> String {
    module
        .interfaces
        .get(iid)
        .and_then(|i| i.slots.get(slot))
        .cloned()
        .unwrap_or_else(|| "?".into())
}

fn const_str(c: &ConstVal) -> String {
    use ConstVal::*;
    match c {
        Null => "null".into(),
        Bool(b) => b.to_string(),
        Long(i) => i.to_string(),
        Double(d) => d.to_string(),
        Char(ch) => format!("'{ch}'"),
        Str(s) => format!("\"{s}\""),
    }
}
