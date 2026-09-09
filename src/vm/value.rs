//! Runtime values.//
// Data-structure fields retained for diagnostics/debugging/future use.
#![allow(dead_code)]

use super::heap::GcRef;

/// A runtime value. Objects are heap handles.
#[derive(Debug, Clone, Copy, PartialEq)]
pub enum Value {
    Null,
    Bool(bool),
    Int(i64),
    Float(f64),
    Char(char),
    Object(GcRef),
}

impl Value {
    pub fn is_null(self) -> bool {
        matches!(self, Value::Null)
    }

    pub fn as_int(self) -> Option<i64> {
        match self {
            Value::Int(i) => Some(i),
            _ => None,
        }
    }

    pub fn as_float(self) -> Option<f64> {
        match self {
            Value::Float(f) => Some(f),
            Value::Int(i) => Some(i as f64),
            _ => None,
        }
    }

    pub fn as_bool(self) -> Option<bool> {
        match self {
            Value::Bool(b) => Some(b),
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
            Value::Bool(b) => b.to_string(),
            Value::Int(i) => i.to_string(),
            Value::Float(f) => f.to_string(),
            Value::Char(c) => c.to_string(),
            Value::Object(r) => heap.object_string(r),
        }
    }
}
