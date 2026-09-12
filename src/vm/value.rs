//! Runtime values.//
// Data-structure fields retained for diagnostics/debugging/future use.
#![allow(dead_code)]

use super::heap::GcRef;

/// A runtime value. Objects are heap handles. Each numeric type keeps its
/// own variant so the static type is observable at runtime (a `Byte` value
/// stays a `Byte` through arithmetic that preserves it).
#[derive(Debug, Clone, Copy, PartialEq)]
pub enum Value {
    Null,
    Boolean(bool),
    Byte(i8),
    Short(i16),
    Integer(i32),
    Long(i64),
    Float(f32),
    Double(f64),
    Char(char),
    Object(GcRef),
}

impl Value {
    pub fn is_null(self) -> bool {
        matches!(self, Value::Null)
    }

    /// Integral value (Byte..Long) as i64.
    pub fn int_value(self) -> Option<i64> {
        match self {
            Value::Byte(i) => Some(i as i64),
            Value::Short(i) => Some(i as i64),
            Value::Integer(i) => Some(i as i64),
            Value::Long(i) => Some(i),
            _ => None,
        }
    }

    /// Width rank of an integral value: 0=Byte 1=Short 2=Integer 3=Long.
    pub fn int_rank(self) -> Option<u8> {
        match self {
            Value::Byte(_) => Some(0),
            Value::Short(_) => Some(1),
            Value::Integer(_) => Some(2),
            Value::Long(_) => Some(3),
            _ => None,
        }
    }

    /// Floating-point value (Float/Double) as f64.
    pub fn float_value(self) -> Option<f64> {
        match self {
            Value::Float(f) => Some(f as f64),
            Value::Double(f) => Some(f),
            _ => None,
        }
    }

    /// Any primitive numeric (integral or floating) as f64, for cross-kind
    /// comparison of an integer with a float.
    pub fn num_f64(self) -> Option<f64> {
        if let Some(i) = self.int_value() {
            return Some(i as f64);
        }
        self.float_value()
    }

    /// Legacy helpers kept for call sites that only care about one kind.
    pub fn as_int(self) -> Option<i64> {
        self.int_value()
    }

    pub fn as_float(self) -> Option<f64> {
        self.float_value()
    }

    pub fn as_bool(self) -> Option<bool> {
        match self {
            Value::Boolean(b) => Some(b),
            _ => None,
        }
    }

    pub fn as_char(self) -> Option<char> {
        match self {
            Value::Char(c) => Some(c),
            _ => None,
        }
    }

    pub fn as_object(self) -> Option<GcRef> {
        match self {
            Value::Object(r) => Some(r),
            _ => None,
        }
    }

    /// Universal string conversion (the runtime backing of `toString`).
    pub fn to_display(self, heap: &super::heap::Heap) -> String {
        match self {
            Value::Null => "null".to_string(),
            Value::Boolean(b) => b.to_string(),
            Value::Byte(i) => i.to_string(),
            Value::Short(i) => i.to_string(),
            Value::Integer(i) => i.to_string(),
            Value::Long(i) => i.to_string(),
            Value::Float(f) => f.to_string(),
            Value::Double(f) => f.to_string(),
            Value::Char(c) => c.to_string(),
            Value::Object(r) => heap.object_string(r),
        }
    }
}
