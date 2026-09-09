//! Compiled bytecode module: the serializable artifact produced by the
//! compiler and consumed by the verifier and the VM.

pub mod decode;
pub mod encode;

use crate::ir::IrConst;

/// A typed constant-pool entry.
#[derive(Debug, Clone, PartialEq)]
pub enum ConstVal {
    Null,
    Bool(bool),
    Long(i64),
    Double(f64),
    Char(char),
    Str(String),
}

impl From<IrConst> for ConstVal {
    fn from(c: IrConst) -> Self {
        match c {
            IrConst::Null => ConstVal::Null,
            IrConst::Bool(b) => ConstVal::Bool(b),
            IrConst::Long(i) => ConstVal::Long(i),
            IrConst::Double(f) => ConstVal::Double(f),
            IrConst::Char(c) => ConstVal::Char(c),
            IrConst::Str(s) => ConstVal::Str(s),
        }
    }
}

/// One compiled function: raw code plus metadata.
#[derive(Debug)]
pub struct CodeFunction {
    /// Debug name, e.g. `Main.run`.
    pub name: String,
    /// Parameter names (for stack traces).
    pub params: Vec<String>,
    /// Number of local slots (parameters occupy the first slots).
    pub local_count: u16,
    /// Maximum operand-stack depth above the local region on any
    /// verifier-accepted path. Filled in by `verifier::verify_with_max_stacks`
    /// (or read from a version-2 module); 0 until then.
    pub max_stack: u16,
    /// True when the function returns a value.
    pub returns_value: bool,
    /// Machine code.
    pub code: Vec<u8>,
    /// (byte offset, source line) pairs.
    pub line_map: Vec<(u32, u32)>,
    /// Source file index.
    pub source_file: u32,
}

/// Class metadata for dispatch.
#[derive(Debug)]
pub struct ClassMeta {
    pub name: String,
    /// Parent class id, or None.
    pub parent: Option<u32>,
    pub field_count: u16,
    /// Vtable slot names (for dynamic dispatch).
    pub vtable_names: Vec<String>,
    /// Function id per vtable slot.
    pub vtable: Vec<u32>,
    /// Static methods: (name, function id).
    pub statics: Vec<(String, u32)>,
    /// Interface dispatch tables: (interface id, function ids per slot).
    pub interfaces: Vec<(u32, Vec<u32>)>,
}

/// Interface metadata.
#[derive(Debug)]
pub struct IfaceMeta {
    pub name: String,
    pub slots: Vec<String>,
    /// Default implementation function id per slot (None = abstract).
    pub defaults: Vec<Option<u32>>,
}

/// The full compiled module.
#[derive(Debug)]
pub struct CodeModule {
    pub version: u32,
    pub constants: Vec<ConstVal>,
    pub functions: Vec<CodeFunction>,
    pub classes: Vec<ClassMeta>,
    pub interfaces: Vec<IfaceMeta>,
    /// Interned method names for dynamic dispatch.
    pub dyn_names: Vec<String>,
    /// Entry point function id (Main.run).
    pub entry: Option<u32>,
    /// Source file names (for the source map).
    pub sources: Vec<String>,
}

impl CodeModule {
    pub const FORMAT_VERSION: u32 = 2;
}

#[cfg(test)]
mod tests {
    use super::*;

    /// A minimal but structurally valid module: one constant, one function
    /// that loads the constant and returns it, one source.
    fn sample_module() -> CodeModule {
        CodeModule {
            version: CodeModule::FORMAT_VERSION,
            constants: vec![ConstVal::Long(42), ConstVal::Str("hi".into())],
            functions: vec![CodeFunction {
                name: "Main.run".into(),
                params: vec![],
                local_count: 1,
                max_stack: 0,
                returns_value: true,
                // LoadConst(0); Return
                code: vec![
                    crate::ir::IrOp::LoadConst.code(),
                    0,
                    0,
                    0,
                    0,
                    crate::ir::IrOp::Return.code(),
                ],
                line_map: vec![(0, 1)],
                source_file: 0,
            }],
            classes: vec![],
            interfaces: vec![],
            dyn_names: vec![],
            entry: Some(0),
            sources: vec!["main.sol".into()],
        }
    }

    #[test]
    fn encode_decode_round_trip() {
        let mut m = sample_module();
        m.functions[0].max_stack = 3;
        let bytes = encode::encode(&m);
        let back = decode::decode(&bytes).expect("decode should succeed");
        assert_eq!(back.version, m.version);
        assert_eq!(back.constants.len(), m.constants.len());
        assert_eq!(back.functions.len(), m.functions.len());
        assert_eq!(back.functions[0].name, "Main.run");
        assert_eq!(back.functions[0].code, m.functions[0].code);
        assert_eq!(back.functions[0].max_stack, 3);
        assert_eq!(back.entry, m.entry);
        assert_eq!(back.sources, m.sources);
    }

    #[test]
    fn deterministic_encoding() {
        let m = sample_module();
        let a = encode::encode(&m);
        let b = encode::encode(&m);
        assert_eq!(a, b, "encoding must be deterministic");
    }

    #[test]
    fn rejects_bad_magic() {
        let mut bytes = encode::encode(&sample_module());
        bytes[0] = b'X'; // corrupt magic
        assert!(decode::decode(&bytes).is_err());
    }

    #[test]
    fn rejects_truncated_input() {
        let bytes = encode::encode(&sample_module());
        let cut = bytes.len() / 2;
        assert!(decode::decode(&bytes[..cut]).is_err());
    }

    #[test]
    fn rejects_empty_input() {
        assert!(decode::decode(b"").is_err());
    }

    #[test]
    fn rejects_wrong_version() {
        let mut bytes = encode::encode(&sample_module());
        // version is bytes 4..8 (little-endian u32)
        bytes[4..8].copy_from_slice(&999u32.to_le_bytes());
        assert!(decode::decode(&bytes).is_err());
    }
    #[test]
    fn rejects_invalid_character_constants() {
        for cp in [0xd800u32, 0x110000, u32::MAX] {
            let mut m = sample_module();
            m.constants = vec![ConstVal::Char('a')];
            let mut bytes = encode::encode(&m);
            bytes[13..17].copy_from_slice(&cp.to_le_bytes());
            assert!(decode::decode(&bytes).is_err(), "accepted U+{cp:X}");
        }
    }

    #[test]
    fn rejects_noncanonical_booleans() {
        let mut m = sample_module();
        m.constants = vec![ConstVal::Bool(false)];
        let mut bytes = encode::encode(&m);
        bytes[13] = 2;
        assert!(decode::decode(&bytes).is_err());
    }

    #[test]
    fn rejects_trailing_bytes_and_all_truncations() {
        let mut bytes = encode::encode(&sample_module());
        for cut in 0..bytes.len() {
            assert!(decode::decode(&bytes[..cut]).is_err(), "accepted cut {cut}");
        }
        bytes.push(0);
        assert!(decode::decode(&bytes).is_err());
    }
}
