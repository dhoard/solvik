//! Binary serialization of a `CodeModule`.
//!
//! Layout (all integers little-endian):
//!   magic    "SOLV" (4 bytes)
//!   version  u32
//!   constants: u32 count, then per entry: tag u8 + payload
//!     0 = null, 1 = bool (u8), 2 = long (i64), 3 = double (f64),
//!     4 = char (u32 code point), 5 = str (u32 len + utf8 bytes)
//!   functions: u32 count, then per function:
//!     u32 code_len + code bytes
//!     u16 local_count
//!     u16 max_stack
//!     u8 returns_value (0/1)
//!     u16 param_count + (u16 len + utf8) names
//!     u32 line_map_len + (u32 offset, u32 line) pairs
//!     u32 source_file
//!     u16 name_len + utf8 name
//!   classes: u32 count, then per class:
//!     name; u16 field_count;
//!     u16 method_names_len + names;
//!     u16 method_table_len + u32 fids;
//!     u16 dyn_methods_len + (name, u32 fid);
//!     u16 statics_len + (name, u32 fid);
//!     u16 ifaces_len + (u32 iface_id, u16 n_fids, u32 fids)
//!     u16 static_fields_len + (name, u16 slot)          (v4+)
//!     u32 static_init (0xFFFFFFFF = none)               (v4+)
//!   interfaces: u32 count, then per interface:
//!     name; u16 slot_count + names; u16 default_count + (u32 fid | 0xFFFFFFFF)
//!   dyn_names: u16 count + names
//!   entry: u32 (0xFFFFFFFF = none)
//!   sources: u32 count + names

use crate::bytecode::{CodeModule, ConstVal};

const MAGIC: &[u8; 4] = b"SOLV";
const NONE: u32 = 0xFFFF_FFFF;

fn push_str(out: &mut Vec<u8>, s: &str) {
    let b = s.as_bytes();
    out.extend_from_slice(&(b.len() as u16).to_le_bytes());
    out.extend_from_slice(b);
}

pub fn encode(module: &CodeModule) -> Vec<u8> {
    let mut out = Vec::new();
    out.extend_from_slice(MAGIC);
    out.extend_from_slice(&module.version.to_le_bytes());

    // Constants.
    out.extend_from_slice(&(module.constants.len() as u32).to_le_bytes());
    for c in &module.constants {
        match c {
            ConstVal::Null => out.push(0),
            ConstVal::Bool(b) => {
                out.push(1);
                out.push(u8::from(*b));
            }
            ConstVal::Byte(i) => {
                out.push(2);
                out.push(*i as u8);
            }
            ConstVal::Short(i) => {
                out.push(3);
                out.extend_from_slice(&i.to_le_bytes());
            }
            ConstVal::Integer(i) => {
                out.push(4);
                out.extend_from_slice(&i.to_le_bytes());
            }
            ConstVal::Long(i) => {
                out.push(5);
                out.extend_from_slice(&i.to_le_bytes());
            }
            ConstVal::Float(f) => {
                out.push(6);
                out.extend_from_slice(&f.to_bits().to_le_bytes());
            }
            ConstVal::Double(f) => {
                out.push(7);
                out.extend_from_slice(&f.to_bits().to_le_bytes());
            }
            ConstVal::BigInt(s) | ConstVal::BigDecimal(s) => {
                out.push(if matches!(c, ConstVal::BigInt(_)) {
                    8
                } else {
                    9
                });
                let b = s.as_bytes();
                out.extend_from_slice(&(b.len() as u32).to_le_bytes());
                out.extend_from_slice(b);
            }
            ConstVal::Char(ch) => {
                out.push(10);
                out.extend_from_slice(&(*ch as u32).to_le_bytes());
            }
            ConstVal::Str(s) => {
                out.push(11);
                let b = s.as_bytes();
                out.extend_from_slice(&(b.len() as u32).to_le_bytes());
                out.extend_from_slice(b);
            }
        }
    }

    // Functions.
    out.extend_from_slice(&(module.functions.len() as u32).to_le_bytes());
    for f in &module.functions {
        out.extend_from_slice(&(f.code.len() as u32).to_le_bytes());
        out.extend_from_slice(&f.code);
        out.extend_from_slice(&f.local_count.to_le_bytes());
        out.extend_from_slice(&f.max_stack.to_le_bytes());
        out.push(u8::from(f.returns_value));
        out.extend_from_slice(&(f.params.len() as u16).to_le_bytes());
        for p in &f.params {
            push_str(&mut out, p);
        }
        out.extend_from_slice(&(f.line_map.len() as u32).to_le_bytes());
        for (off, line) in &f.line_map {
            out.extend_from_slice(&off.to_le_bytes());
            out.extend_from_slice(&line.to_le_bytes());
        }
        out.extend_from_slice(&f.source_file.to_le_bytes());
        push_str(&mut out, &f.name);
    }

    // Classes.
    out.extend_from_slice(&(module.classes.len() as u32).to_le_bytes());
    for c in &module.classes {
        push_str(&mut out, &c.name);
        out.extend_from_slice(&c.field_count.to_le_bytes());
        out.extend_from_slice(&(c.method_names.len() as u16).to_le_bytes());
        for n in &c.method_names {
            push_str(&mut out, n);
        }
        out.extend_from_slice(&(c.method_table.len() as u16).to_le_bytes());
        for fid in &c.method_table {
            out.extend_from_slice(&fid.to_le_bytes());
        }
        out.extend_from_slice(&(c.dyn_methods.len() as u16).to_le_bytes());
        for (name, fid) in &c.dyn_methods {
            push_str(&mut out, name);
            out.extend_from_slice(&fid.to_le_bytes());
        }
        out.extend_from_slice(&(c.statics.len() as u16).to_le_bytes());
        for (name, fid) in &c.statics {
            push_str(&mut out, name);
            out.extend_from_slice(&fid.to_le_bytes());
        }
        out.extend_from_slice(&(c.interfaces.len() as u16).to_le_bytes());
        for (iid, fids) in &c.interfaces {
            out.extend_from_slice(&iid.to_le_bytes());
            out.extend_from_slice(&(fids.len() as u16).to_le_bytes());
            for fid in fids {
                out.extend_from_slice(&fid.to_le_bytes());
            }
        }
        out.extend_from_slice(&(c.static_fields.len() as u16).to_le_bytes());
        for (name, slot) in &c.static_fields {
            push_str(&mut out, name);
            out.extend_from_slice(&slot.to_le_bytes());
        }
        out.extend_from_slice(&c.static_init.unwrap_or(NONE).to_le_bytes());
    }

    // Interfaces.
    out.extend_from_slice(&(module.interfaces.len() as u32).to_le_bytes());
    for i in &module.interfaces {
        push_str(&mut out, &i.name);
        out.extend_from_slice(&(i.slots.len() as u16).to_le_bytes());
        for s in &i.slots {
            push_str(&mut out, s);
        }
        out.extend_from_slice(&(i.defaults.len() as u16).to_le_bytes());
        for d in &i.defaults {
            out.extend_from_slice(&d.unwrap_or(NONE).to_le_bytes());
        }
    }

    // Dynamic dispatch names.
    out.extend_from_slice(&(module.dyn_names.len() as u16).to_le_bytes());
    for n in &module.dyn_names {
        push_str(&mut out, n);
    }

    // Entry point.
    out.extend_from_slice(&module.entry.unwrap_or(NONE).to_le_bytes());

    // Sources.
    out.extend_from_slice(&(module.sources.len() as u32).to_le_bytes());
    for s in &module.sources {
        push_str(&mut out, s);
    }

    out
}
