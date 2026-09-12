//! Binary deserialization of a `CodeModule`.

use crate::bytecode::{ClassMeta, CodeFunction, CodeModule, ConstVal, IfaceMeta};

const MAGIC: &[u8; 4] = b"SOLV";
const NONE: u32 = 0xFFFF_FFFF;

#[derive(Debug)]
pub struct DecodeError {
    pub message: String,
    pub offset: usize,
}

impl DecodeError {
    fn at(offset: usize, msg: impl Into<String>) -> Self {
        DecodeError {
            message: msg.into(),
            offset,
        }
    }
}

impl std::fmt::Display for DecodeError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{} (at offset {})", self.message, self.offset)
    }
}

struct Reader<'a> {
    buf: &'a [u8],
    pos: usize,
}

impl<'a> Reader<'a> {
    fn new(buf: &'a [u8]) -> Self {
        Reader { buf, pos: 0 }
    }

    fn fail(&self, msg: impl Into<String>) -> DecodeError {
        DecodeError::at(self.pos, msg)
    }

    fn bytes(&mut self, n: usize) -> Result<&'a [u8], DecodeError> {
        if n > self.buf.len() - self.pos {
            return Err(self.fail("truncated module"));
        }
        let b = &self.buf[self.pos..self.pos + n];
        self.pos += n;
        Ok(b)
    }

    fn u8(&mut self) -> Result<u8, DecodeError> {
        Ok(self.bytes(1)?[0])
    }

    fn boolean(&mut self) -> Result<bool, DecodeError> {
        match self.u8()? {
            0 => Ok(false),
            1 => Ok(true),
            _ => Err(self.fail("invalid boolean (expected 0 or 1)")),
        }
    }

    fn u16(&mut self) -> Result<u16, DecodeError> {
        let b = self.bytes(2)?;
        Ok(u16::from_le_bytes([b[0], b[1]]))
    }

    fn u32(&mut self) -> Result<u32, DecodeError> {
        let b = self.bytes(4)?;
        Ok(u32::from_le_bytes([b[0], b[1], b[2], b[3]]))
    }

    fn i16(&mut self) -> Result<i16, DecodeError> {
        Ok(i16::from_le_bytes([self.u8()?, self.u8()?]))
    }

    fn i32(&mut self) -> Result<i32, DecodeError> {
        Ok(i32::from_le_bytes([
            self.u8()?,
            self.u8()?,
            self.u8()?,
            self.u8()?,
        ]))
    }

    fn i64(&mut self) -> Result<i64, DecodeError> {
        let b = self.bytes(8)?;
        Ok(i64::from_le_bytes(b.try_into().unwrap()))
    }

    fn f64(&mut self) -> Result<f64, DecodeError> {
        let b = self.bytes(8)?;
        Ok(f64::from_bits(u64::from_le_bytes(b.try_into().unwrap())))
    }

    fn string(&mut self) -> Result<String, DecodeError> {
        let len = self.u16()? as usize;
        let b = self.bytes(len)?;
        std::str::from_utf8(b)
            .map(|s| s.to_string())
            .map_err(|_| self.fail("invalid utf-8"))
    }
}

pub fn decode(buf: &[u8]) -> Result<CodeModule, DecodeError> {
    let mut r = Reader::new(buf);
    if r.bytes(4)? != MAGIC {
        return Err(DecodeError::at(0, "bad magic (expected SOLV)"));
    }
    let version = r.u32()?;
    if version != CodeModule::FORMAT_VERSION {
        return Err(DecodeError::at(
            4,
            format!("unsupported module version {}", version),
        ));
    }

    // Constants.
    let mut constants = Vec::new();
    for _ in 0..r.u32()? {
        let tag = r.u8()?;
        match tag {
            0 => constants.push(ConstVal::Null),
            1 => constants.push(ConstVal::Bool(r.boolean()?)),
            2 => constants.push(ConstVal::Byte(r.u8()? as i8)),
            3 => constants.push(ConstVal::Short(r.i16()?)),
            4 => constants.push(ConstVal::Integer(r.i32()?)),
            5 => constants.push(ConstVal::Long(r.i64()?)),
            6 => constants.push(ConstVal::Float(f32::from_bits(r.u32()?))),
            7 => constants.push(ConstVal::Double(r.f64()?)),
            8 | 9 => {
                let len = r.u32()? as usize;
                let b = r.bytes(len)?;
                let s = std::str::from_utf8(b)
                    .map_err(|_| r.fail("invalid utf-8 in constant"))?
                    .to_string();
                if tag == 8 {
                    constants.push(ConstVal::BigInt(s));
                } else {
                    constants.push(ConstVal::BigDecimal(s));
                }
            }
            10 => {
                let cp = r.u32()?;
                constants.push(ConstVal::Char(
                    char::from_u32(cp).ok_or_else(|| r.fail("invalid Unicode code point"))?,
                ));
            }
            11 => {
                let len = r.u32()? as usize;
                let b = r.bytes(len)?;
                constants.push(ConstVal::Str(
                    std::str::from_utf8(b)
                        .map_err(|_| r.fail("invalid utf-8 in constant"))?
                        .to_string(),
                ));
            }
            t => return Err(r.fail(format!("unknown constant tag {}", t))),
        }
    }

    // Functions.
    let mut functions = Vec::new();
    for _ in 0..r.u32()? {
        let code_len = r.u32()? as usize;
        let code = r.bytes(code_len)?.to_vec();
        let local_count = r.u16()?;
        let max_stack = r.u16()?;
        let returns_value = r.boolean()?;
        let param_count = r.u16()?;
        let mut params = Vec::new();
        for _ in 0..param_count {
            params.push(r.string()?);
        }
        let lm_len = r.u32()?;
        let mut line_map = Vec::new();
        for _ in 0..lm_len {
            line_map.push((r.u32()?, r.u32()?));
        }
        let source_file = r.u32()?;
        let name = r.string()?;
        functions.push(CodeFunction {
            name,
            params,
            local_count,
            max_stack,
            returns_value,
            code,
            line_map,
            source_file,
        });
    }

    // Classes.
    let mut classes = Vec::new();
    for _ in 0..r.u32()? {
        let name = r.string()?;
        let field_count = r.u16()?;
        let mn_len = r.u16()?;
        let mut method_names = Vec::new();
        for _ in 0..mn_len {
            method_names.push(r.string()?);
        }
        let mt_len = r.u16()?;
        let mut method_table = Vec::new();
        for _ in 0..mt_len {
            method_table.push(r.u32()?);
        }
        let dm_len = r.u16()?;
        let mut dyn_methods = Vec::new();
        for _ in 0..dm_len {
            dyn_methods.push((r.string()?, r.u32()?));
        }
        let st_len = r.u16()?;
        let mut statics = Vec::new();
        for _ in 0..st_len {
            statics.push((r.string()?, r.u32()?));
        }
        let if_len = r.u16()?;
        let mut interfaces = Vec::new();
        for _ in 0..if_len {
            let iid = r.u32()?;
            let n = r.u16()?;
            let mut fids = Vec::new();
            for _ in 0..n {
                fids.push(r.u32()?);
            }
            interfaces.push((iid, fids));
        }
        let sf_len = r.u16()?;
        let mut static_fields = Vec::new();
        for _ in 0..sf_len {
            static_fields.push((r.string()?, r.u16()?));
        }
        let static_init_fid = r.u32()?;
        let static_init = if static_init_fid == NONE {
            None
        } else {
            Some(static_init_fid)
        };
        classes.push(ClassMeta {
            name,
            field_count,
            method_names,
            method_table,
            dyn_methods,
            statics,
            static_fields,
            static_init,
            interfaces,
        });
    }

    // Interfaces.
    let mut interfaces = Vec::new();
    for _ in 0..r.u32()? {
        let name = r.string()?;
        let slot_count = r.u16()?;
        let mut slots = Vec::new();
        for _ in 0..slot_count {
            slots.push(r.string()?);
        }
        let def_count = r.u16()?;
        let mut defaults = Vec::new();
        for _ in 0..def_count {
            let fid = r.u32()?;
            defaults.push(if fid == NONE { None } else { Some(fid) });
        }
        interfaces.push(IfaceMeta {
            name,
            slots,
            defaults,
        });
    }

    // Dynamic names.
    let dyn_count = r.u16()?;
    let mut dyn_names = Vec::new();
    for _ in 0..dyn_count {
        dyn_names.push(r.string()?);
    }

    // Entry.
    let entry_fid = r.u32()?;
    let entry = if entry_fid == NONE {
        None
    } else {
        Some(entry_fid)
    };

    // Sources.
    let src_count = r.u32()?;
    let mut sources = Vec::new();
    for _ in 0..src_count {
        sources.push(r.string()?);
    }

    if r.pos != buf.len() {
        return Err(r.fail("trailing bytes after module"));
    }

    Ok(CodeModule {
        version,
        constants,
        functions,
        classes,
        interfaces,
        dyn_names,
        entry,
        sources,
    })
}
