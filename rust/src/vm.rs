//! Stack-based virtual machine.
//!
//! Port of internal/vm/vm.go.

use crate::bytecode::{self, Opcode};
use crate::gocompat::go_format_float;
use std::cell::RefCell;
use std::collections::HashMap;
use std::rc::Rc;
use std::time::Instant;

// ---- Values ----

#[derive(Clone)]
pub enum Value {
    Null,
    Bool(bool),
    Byte(u8),
    Int(i64),
    Float(f64),
    Char(u32),
    Str(Rc<String>),
    List(Rc<RefCell<Vec<Value>>>),
    Map(Rc<MapValue>),
    Regex(Rc<regex::Regex>),
    Exception(Rc<ExceptionValue>),
    Struct(Rc<StructValue>),
    Trait(Rc<TraitValue>),
    Stack(Rc<RefCell<Vec<Value>>>),
}

pub struct ExceptionValue {
    pub message: String,
    pub trace: String,
}

pub struct StructValue {
    pub type_name: String,
    pub fields: RefCell<Vec<Value>>,
}

pub struct TraitValue {
    pub data: Value,
    pub methods: Vec<usize>,
    pub type_name: String,
}

pub struct MapValue {
    pub entries: RefCell<Vec<(Value, Value)>>,
}

impl MapValue {
    fn find_entry(&self, key: &Value) -> Option<usize> {
        self.entries
            .borrow()
            .iter()
            .position(|(k, _)| value_equal(k, key))
    }
}

impl Value {
    pub fn str(s: impl Into<String>) -> Value {
        Value::Str(Rc::new(s.into()))
    }

    pub fn is_null(&self) -> bool {
        matches!(self, Value::Null)
    }

    pub fn is_truthy(&self) -> bool {
        match self {
            Value::Bool(b) => *b,
            Value::Null => false,
            _ => true,
        }
    }

    pub fn as_bool(&self) -> bool {
        match self {
            Value::Bool(b) => *b,
            _ => false,
        }
    }

    /// Widening int conversion, matching Go's Value.Int().
    pub fn as_int(&self) -> i64 {
        match self {
            Value::Int(i) => *i,
            Value::Byte(b) => *b as i64,
            Value::Char(c) => *c as i64,
            Value::Float(f) => *f as i64,
            Value::Bool(b) => {
                if *b {
                    1
                } else {
                    0
                }
            }
            _ => 0,
        }
    }

    /// Widening float conversion, matching Go's Value.Double().
    pub fn as_double(&self) -> f64 {
        match self {
            Value::Float(f) => *f,
            Value::Int(i) => *i as f64,
            Value::Byte(b) => *b as f64,
            _ => 0.0,
        }
    }

    pub fn as_char(&self) -> u32 {
        match self {
            Value::Char(c) => *c,
            _ => 0,
        }
    }

    pub fn list_len(&self) -> usize {
        match self {
            Value::List(l) => l.borrow().len(),
            _ => 0,
        }
    }

    pub fn list_get(&self, i: usize) -> Option<Value> {
        match self {
            Value::List(l) => l.borrow().get(i).cloned(),
            _ => None,
        }
    }

    pub fn map_len(&self) -> usize {
        match self {
            Value::Map(m) => m.entries.borrow().len(),
            _ => 0,
        }
    }

    pub fn map_contains(&self, key: &Value) -> bool {
        match self {
            Value::Map(m) => m.find_entry(key).is_some(),
            _ => false,
        }
    }

    pub fn stack_len(&self) -> usize {
        match self {
            Value::Stack(s) => s.borrow().len(),
            _ => 0,
        }
    }

    pub fn stack_push(&self, val: Value) {
        if let Value::Stack(s) = self {
            s.borrow_mut().push(val);
        }
    }

    pub fn stack_pop(&self) -> Option<Value> {
        if let Value::Stack(s) = self {
            s.borrow_mut().pop()
        } else {
            None
        }
    }

    pub fn stack_peek(&self) -> Option<Value> {
        if let Value::Stack(s) = self {
            s.borrow().last().cloned()
        } else {
            None
        }
    }

    pub fn struct_type_name(&self) -> String {
        match self {
            Value::Struct(s) => s.type_name.clone(),
            Value::Trait(t) => t.type_name.clone(),
            _ => String::new(),
        }
    }

    pub fn regex_match(&self, s: &str) -> bool {
        match self {
            Value::Regex(r) => r.is_match(s),
            _ => false,
        }
    }

    /// Go's Value.String(): display conversion used by print, string(), and
    /// concatenation.
    pub fn display_string(&self) -> String {
        match self {
            Value::Str(s) => s.to_string(),
            Value::Bool(b) => {
                if *b {
                    "true".to_string()
                } else {
                    "false".to_string()
                }
            }
            Value::Byte(b) => b.to_string(),
            Value::Int(i) => i.to_string(),
            Value::Float(f) => go_format_float(*f),
            Value::Char(c) => char::from_u32(*c).map(|c| c.to_string()).unwrap_or_default(),
            Value::Null => "null".to_string(),
            Value::List(l) => {
                let mut b = String::from("[");
                for (i, e) in l.borrow().iter().enumerate() {
                    if i > 0 {
                        b.push_str(", ");
                    }
                    b.push_str(&e.display_string());
                }
                b.push(']');
                b
            }
            Value::Map(m) => {
                let mut b = String::from("{");
                for (i, (k, v)) in m.entries.borrow().iter().enumerate() {
                    if i > 0 {
                        b.push_str(", ");
                    }
                    b.push_str(&k.display_string());
                    b.push_str(": ");
                    b.push_str(&v.display_string());
                }
                b.push('}');
                b
            }
            Value::Exception(e) => e.message.clone(),
            Value::Struct(s) => {
                let mut b = s.type_name.clone();
                b.push('(');
                for (i, f) in s.fields.borrow().iter().enumerate() {
                    if i > 0 {
                        b.push_str(", ");
                    }
                    b.push_str(&f.display_string());
                }
                b.push(')');
                b
            }
            Value::Stack(s) => {
                let mut b = String::from("[");
                for (i, e) in s.borrow().iter().enumerate() {
                    if i > 0 {
                        b.push_str(", ");
                    }
                    b.push_str(&e.display_string());
                }
                b.push(']');
                b
            }
            Value::Trait(t) => t.data.display_string(),
            Value::Regex(_) => "<regex>".to_string(),
        }
    }
}

pub fn value_equal(a: &Value, b: &Value) -> bool {
    match (a, b) {
        (Value::Null, Value::Null) => true,
        (Value::Bool(x), Value::Bool(y)) => x == y,
        (Value::Byte(x), Value::Byte(y)) => x == y,
        (Value::Int(x), Value::Int(y)) => x == y,
        (Value::Float(x), Value::Float(y)) => x == y,
        (Value::Char(x), Value::Char(y)) => x == y,
        (Value::Str(x), Value::Str(y)) => x == y,
        (Value::List(x), Value::List(y)) => {
            let x = x.borrow();
            let y = y.borrow();
            if x.len() != y.len() {
                return false;
            }
            x.iter().zip(y.iter()).all(|(a, b)| value_equal(a, b))
        }
        (Value::Map(x), Value::Map(y)) => {
            let x = x.entries.borrow();
            let y = y.entries.borrow();
            if x.len() != y.len() {
                return false;
            }
            for (xa, ya) in x.iter().zip(y.iter()) {
                if !value_equal(&xa.0, &ya.0) || !value_equal(&xa.1, &ya.1) {
                    return false;
                }
            }
            true
        }
        (Value::Stack(x), Value::Stack(y)) => {
            let x = x.borrow();
            let y = y.borrow();
            if x.len() != y.len() {
                return false;
            }
            x.iter().zip(y.iter()).all(|(a, b)| value_equal(a, b))
        }
        (Value::Struct(x), Value::Struct(y)) => {
            if x.type_name != y.type_name {
                return false;
            }
            let xf = x.fields.borrow();
            let yf = y.fields.borrow();
            if xf.len() != yf.len() {
                return false;
            }
            xf.iter().zip(yf.iter()).all(|(a, b)| value_equal(a, b))
        }
        (Value::Trait(x), Value::Trait(y)) => {
            x.type_name == y.type_name && value_equal(&x.data, &y.data)
        }
        _ => false,
    }
}

/// Canonical runtime type tag (matches the compiler's CHECK_TYPE tags and the
/// typeOf native).
pub fn value_type_tag(v: &Value) -> String {
    match v {
        Value::Null => "null".to_string(),
        Value::Bool(_) => "bool".to_string(),
        Value::Byte(_) => "byte".to_string(),
        Value::Int(_) => "int".to_string(),
        Value::Float(_) => "float".to_string(),
        Value::Char(_) => "char".to_string(),
        Value::Str(_) => "string".to_string(),
        Value::List(_) => "list".to_string(),
        Value::Map(_) => "map".to_string(),
        Value::Stack(_) => "stack".to_string(),
        Value::Regex(_) => "regex".to_string(),
        Value::Exception(_) => "exception".to_string(),
        Value::Struct(_) => v.struct_type_name().to_lowercase(),
        Value::Trait(_) => v.struct_type_name().to_lowercase(),
    }
}

// ---- Errors ----

#[derive(Clone, Debug)]
pub struct StackFrame {
    pub function: String,
    pub offset: usize,
    pub line: usize,
    pub column: usize,
}

#[derive(Clone, Debug)]
pub struct RuntimeError {
    pub code: String,
    pub message: String,
    pub function: String,
    pub offset: usize,
    pub line: usize,
    pub column: usize,
    pub stack: Vec<StackFrame>,
}

impl RuntimeError {
    pub fn error_string(&self) -> String {
        format!("runtime {}: {}", self.code, self.message)
    }
}

/// Formats a stack trace exactly like Go's vm.FormatStackTrace.
pub fn format_stack_trace(err: &RuntimeError) -> String {
    let mut b = String::new();
    b.push_str(&format!("runtime error: {}: {}\n", err.code, err.message));
    for sf in err.stack.iter().rev() {
        b.push_str(&format!("  at {}", sf.function));
        if sf.line > 0 {
            b.push_str(&format!(" at line {}", sf.line));
        }
        b.push('\n');
    }
    if !err.function.is_empty() {
        b.push_str(&format!("  at {}", err.function));
        if err.line > 0 {
            b.push_str(&format!(" at line {}", err.line));
        }
        b.push('\n');
    }
    b
}

// ---- Limits ----

#[derive(Clone, Copy)]
pub struct Limits {
    pub max_stack_size: usize,
    pub max_call_depth: usize,
    pub max_instructions: i64,
    #[allow(dead_code)]
    pub max_string_size: usize,
    pub max_list_size: usize,
    pub max_map_size: usize,
}

impl Default for Limits {
    fn default() -> Limits {
        Limits {
            max_stack_size: 65536,
            max_call_depth: 1024,
            max_instructions: 10_000_000,
            max_string_size: 1 << 24,
            max_list_size: 1 << 24,
            max_map_size: 1 << 20,
        }
    }
}

// ---- Natives ----

pub type NativeHandler = fn(&[Value]) -> Result<Value, String>;

pub struct NativeRegistry {
    funcs: HashMap<String, NativeHandler>,
}

impl NativeRegistry {
    pub fn new() -> NativeRegistry {
        NativeRegistry {
            funcs: HashMap::new(),
        }
    }

    pub fn register(&mut self, name: &str, handler: NativeHandler) {
        self.funcs.insert(name.to_string(), handler);
    }

    pub fn lookup(&self, name: &str) -> Option<NativeHandler> {
        self.funcs.get(name).copied()
    }

    pub fn iter(&self) -> std::collections::hash_map::Iter<'_, String, NativeHandler> {
        self.funcs.iter()
    }
}

// ---- Frames and handlers ----

#[derive(Clone, Copy)]
struct CallFrame {
    function_id: usize,
    ip: usize,
    stack_base: usize,
    #[allow(dead_code)]
    local_base: usize,
    local_count: usize,
}

struct HandlerEntry {
    catch_offset: usize,
    finally_offset: usize,
    stack_depth: usize,
    frame_index: usize,
    active: bool,
    in_finally: bool,
}

// ---- VM ----

pub struct Vm {
    program: Option<Rc<bytecode::Program>>,
    stack: Vec<Value>,
    frames: Vec<CallFrame>,
    natives: NativeRegistry,
    native_cache: Vec<Option<NativeHandler>>,
    limits: Limits,
    deadline: Option<Instant>,
    inst_count: i64,
    handler_stack: Vec<HandlerEntry>,
    pending_exception: Option<Value>,
}

enum RunResult {
    Done(Value),
    Err(RuntimeError),
}

impl Vm {
    pub fn new(natives: NativeRegistry, limits: Limits) -> Vm {
        Vm {
            program: None,
            stack: Vec::with_capacity(1024),
            frames: Vec::with_capacity(64),
            natives,
            native_cache: Vec::new(),
            limits,
            deadline: None,
            inst_count: 0,
            handler_stack: Vec::new(),
            pending_exception: None,
        }
    }

    pub fn set_deadline(&mut self, deadline: Option<Instant>) {
        self.deadline = deadline;
    }

    pub fn execute(&mut self, prog: Rc<bytecode::Program>) -> Result<Value, RuntimeError> {
        self.program = Some(prog.clone());
        self.inst_count = 0;

        // Pre-resolve native functions
        self.native_cache = Vec::with_capacity(prog.natives.len());
        for nd in &prog.natives {
            let full_name = format!("{}.{}", nd.module, nd.name);
            let nf = self
                .natives
                .lookup(&full_name)
                .or_else(|| self.natives.lookup(&nd.name));
            self.native_cache.push(nf);
        }

        let main_idx = prog.functions.iter().position(|f| f.name == "main");
        let main_idx = match main_idx {
            Some(i) => i,
            None => {
                return Err(RuntimeError {
                    code: "E001".to_string(),
                    message: "no main function found".to_string(),
                    function: String::new(),
                    offset: 0,
                    line: 0,
                    column: 0,
                    stack: Vec::new(),
                })
            }
        };

        let total_locals = prog.functions[main_idx].param_count + prog.functions[main_idx].local_count;
        self.frames.push(CallFrame {
            function_id: main_idx,
            ip: 0,
            stack_base: 0,
            local_base: 0,
            local_count: total_locals,
        });
        for _ in 0..total_locals {
            self.stack.push(Value::Null);
        }

        match self.run() {
            RunResult::Done(v) => Ok(v),
            RunResult::Err(e) => Err(e),
        }
    }

    fn run(&mut self) -> RunResult {
        const LIMIT_CHECK_INTERVAL: i64 = 1024;

        loop {
            self.inst_count += 1;
            if self.limits.max_instructions > 0 && self.inst_count & (LIMIT_CHECK_INTERVAL - 1) == 0 {
                if self.inst_count > self.limits.max_instructions {
                    return RunResult::Err(self.rt_error("E003", "instruction limit exceeded"));
                }
                if let Some(d) = self.deadline {
                    if Instant::now() >= d {
                        return RunResult::Err(self.rt_error("E002", "execution cancelled"));
                    }
                }
            }

            let prog = self.program.clone().unwrap();
            let frame_idx = match self.frames.last() {
                Some(_) => self.frames.len() - 1,
                None => return RunResult::Err(self.rt_error("E004", "execution fell off end of code")),
            };
            let function_id = self.frames[frame_idx].function_id;
            let fn_struct = &prog.functions[function_id];

            if self.frames[frame_idx].ip >= fn_struct.code.len() {
                return RunResult::Err(self.rt_error("E004", "execution fell off end of code"));
            }

            let ip = self.frames[frame_idx].ip;
            let decoded = bytecode::decode_op(&fn_struct.code, ip);
            let (op, operands, next_ip) = match decoded {
                Some(d) => d,
                None => {
                    return RunResult::Err(self.rt_error("E005", "decode error"));
                }
            };

            self.frames[frame_idx].ip = next_ip;

            macro_rules! pop_int {
                () => {
                    self.pop().as_int()
                };
            }
            macro_rules! pop_f64 {
                () => {
                    self.pop().as_double()
                };
            }
            macro_rules! pop_bool {
                () => {
                    self.pop().as_bool()
                };
            }
            macro_rules! pop_str {
                () => {
                    self.pop().display_string()
                };
            }

            let op_enum = |b: u8| unsafe { std::mem::transmute::<u8, Opcode>(b) };
            let _ = op_enum;

            match op {
                x if x == Opcode::Nop as u8 => {}

                x if x == Opcode::ConstBool as u8 => {
                    self.push(Value::Bool(operands[0] != 0));
                }
                x if x == Opcode::ConstByte as u8 => {
                    self.push(Value::Byte(operands[0] as u8));
                }
                x if x == Opcode::ConstInt as u8 => {
                    self.push(Value::Int(operands[0] as i64));
                }
                x if x == Opcode::ConstFloat as u8 => {
                    self.push(Value::Float(f64::from_bits(operands[0])));
                }
                x if x == Opcode::ConstChar as u8 => {
                    self.push(Value::Char(operands[0] as u32));
                }
                x if x == Opcode::ConstString as u8 => {
                    let idx = operands[0] as usize;
                    if idx < fn_struct.constants.len() {
                        self.push(Value::Str(Rc::new(fn_struct.constants[idx].s.clone())));
                    } else {
                        self.push(Value::str(""));
                    }
                }
                x if x == Opcode::ConstNull as u8 => {
                    self.push(Value::Null);
                }

                x if x == Opcode::LoadLocal as u8 => {
                    let idx = operands[0] as usize;
                    let addr = self.frames[frame_idx].stack_base + idx;
                    if addr < self.stack.len() {
                        let v = self.stack[addr].clone();
                        self.push(v);
                    } else {
                        return RunResult::Err(
                            self.error_at_current("E006", &format!("local index {} out of range", idx)),
                        );
                    }
                }
                x if x == Opcode::StoreLocal as u8 => {
                    let idx = operands[0] as usize;
                    let addr = self.frames[frame_idx].stack_base + idx;
                    let val = self.pop();
                    if addr < self.stack.len() {
                        self.stack[addr] = val;
                    } else {
                        return RunResult::Err(
                            self.error_at_current("E007", &format!("local index {} out of range", idx)),
                        );
                    }
                }

                x if x == Opcode::Pop as u8 => {
                    self.pop();
                }
                x if x == Opcode::Dup as u8 => {
                    let top = self.peek();
                    self.push(top);
                }

                x if x == Opcode::AddInt as u8 => {
                    let b = pop_int!();
                    let a = pop_int!();
                    self.push(Value::Int(a.wrapping_add(b)));
                }
                x if x == Opcode::SubInt as u8 => {
                    let b = pop_int!();
                    let a = pop_int!();
                    self.push(Value::Int(a.wrapping_sub(b)));
                }
                x if x == Opcode::MulInt as u8 => {
                    let b = pop_int!();
                    let a = pop_int!();
                    self.push(Value::Int(a.wrapping_mul(b)));
                }
                x if x == Opcode::DivInt as u8 => {
                    let b = pop_int!();
                    let a = pop_int!();
                    if b == 0 {
                        match self.throw_runtime("E010", "integer division by zero") {
                            Some(e) => return RunResult::Err(e),
                            None => continue,
                        }
                    }
                    self.push(Value::Int(a.wrapping_div(b)));
                }
                x if x == Opcode::RemInt as u8 => {
                    let b = pop_int!();
                    let a = pop_int!();
                    if b == 0 {
                        match self.throw_runtime("E011", "integer modulo by zero") {
                            Some(e) => return RunResult::Err(e),
                            None => continue,
                        }
                    }
                    self.push(Value::Int(a.wrapping_rem(b)));
                }
                x if x == Opcode::NegInt as u8 => {
                    let v = pop_int!();
                    self.push(Value::Int(v.wrapping_neg()));
                }

                x if x == Opcode::AddFloat as u8 => {
                    let b = pop_f64!();
                    let a = pop_f64!();
                    self.push(Value::Float(a + b));
                }
                x if x == Opcode::SubFloat as u8 => {
                    let b = pop_f64!();
                    let a = pop_f64!();
                    self.push(Value::Float(a - b));
                }
                x if x == Opcode::MulFloat as u8 => {
                    let b = pop_f64!();
                    let a = pop_f64!();
                    self.push(Value::Float(a * b));
                }
                x if x == Opcode::DivFloat as u8 => {
                    let b = pop_f64!();
                    let a = pop_f64!();
                    self.push(Value::Float(a / b));
                }
                x if x == Opcode::RemFloat as u8 => {
                    let b = pop_f64!();
                    let a = pop_f64!();
                    self.push(Value::Float(a % b));
                }
                x if x == Opcode::NegFloat as u8 => {
                    let v = pop_f64!();
                    self.push(Value::Float(-v));
                }

                x if x == Opcode::ConcatString as u8 => {
                    let b = pop_str!();
                    let a = pop_str!();
                    self.push(Value::str(format!("{}{}", a, b)));
                }

                x if x == Opcode::EqBool as u8 => {
                    let b = pop_bool!();
                    let a = pop_bool!();
                    self.push(Value::Bool(a == b));
                }
                x if x == Opcode::EqInt as u8 => {
                    let b = pop_int!();
                    let a = pop_int!();
                    self.push(Value::Bool(a == b));
                }
                x if x == Opcode::EqFloat as u8 => {
                    let b = pop_f64!();
                    let a = pop_f64!();
                    self.push(Value::Bool(a == b));
                }
                x if x == Opcode::EqChar as u8 => {
                    let b = self.pop().as_char();
                    let a = self.pop().as_char();
                    self.push(Value::Bool(a == b));
                }
                x if x == Opcode::EqString as u8 => {
                    let b = pop_str!();
                    let a = pop_str!();
                    self.push(Value::Bool(a == b));
                }
                x if x == Opcode::EqRef as u8 => {
                    let b = self.pop();
                    let a = self.pop();
                    if let Value::Regex(r) = &a {
                        if let Value::Str(s) = &b {
                            self.push(Value::Bool(r.is_match(s)));
                        } else {
                            self.push(Value::Bool(false));
                        }
                    } else if let Value::Regex(r) = &b {
                        if let Value::Str(s) = &a {
                            self.push(Value::Bool(r.is_match(s)));
                        } else {
                            self.push(Value::Bool(false));
                        }
                    } else {
                        self.push(Value::Bool(value_equal(&a, &b)));
                    }
                }

                x if x == Opcode::LtInt as u8 => {
                    let b = pop_int!();
                    let a = pop_int!();
                    self.push(Value::Bool(a < b));
                }
                x if x == Opcode::LeInt as u8 => {
                    let b = pop_int!();
                    let a = pop_int!();
                    self.push(Value::Bool(a <= b));
                }
                x if x == Opcode::GtInt as u8 => {
                    let b = pop_int!();
                    let a = pop_int!();
                    self.push(Value::Bool(a > b));
                }
                x if x == Opcode::GeInt as u8 => {
                    let b = pop_int!();
                    let a = pop_int!();
                    self.push(Value::Bool(a >= b));
                }
                x if x == Opcode::LtFloat as u8 => {
                    let b = pop_f64!();
                    let a = pop_f64!();
                    self.push(Value::Bool(a < b));
                }
                x if x == Opcode::LeFloat as u8 => {
                    let b = pop_f64!();
                    let a = pop_f64!();
                    self.push(Value::Bool(a <= b));
                }
                x if x == Opcode::GtFloat as u8 => {
                    let b = pop_f64!();
                    let a = pop_f64!();
                    self.push(Value::Bool(a > b));
                }
                x if x == Opcode::GeFloat as u8 => {
                    let b = pop_f64!();
                    let a = pop_f64!();
                    self.push(Value::Bool(a >= b));
                }

                x if x == Opcode::NotBool as u8 => {
                    let v = pop_bool!();
                    self.push(Value::Bool(!v));
                }

                x if x == Opcode::BitAndInt as u8 => {
                    let b = pop_int!();
                    let a = pop_int!();
                    self.push(Value::Int(a & b));
                }
                x if x == Opcode::BitOrInt as u8 => {
                    let b = pop_int!();
                    let a = pop_int!();
                    self.push(Value::Int(a | b));
                }
                x if x == Opcode::BitXorInt as u8 => {
                    let b = pop_int!();
                    let a = pop_int!();
                    self.push(Value::Int(a ^ b));
                }
                x if x == Opcode::BitNotInt as u8 => {
                    let v = pop_int!();
                    self.push(Value::Int(!v));
                }
                x if x == Opcode::ShiftLeftInt as u8 => {
                    let b = pop_int!();
                    let a = pop_int!();
                    // Go: shift count >= 64 yields 0
                    let v = if b < 0 || b >= 64 {
                        0
                    } else {
                        a.wrapping_shl(b as u32)
                    };
                    self.push(Value::Int(v));
                }
                x if x == Opcode::ShiftRightInt as u8 => {
                    let b = pop_int!();
                    let a = pop_int!();
                    // Go: arithmetic shift; count >= 64 yields sign fill
                    let v = if b < 0 || b >= 64 {
                        if a < 0 {
                            -1
                        } else {
                            0
                        }
                    } else {
                        a >> b
                    };
                    self.push(Value::Int(v));
                }

                x if x == Opcode::Jump as u8 => {
                    let offset = operands[0] as u32 as i32;
                    let target = (next_ip as i64 + offset as i64) as usize;
                    self.frames[frame_idx].ip = target;
                }
                x if x == Opcode::JumpIfFalse as u8 => {
                    let offset = operands[0] as u32 as i32;
                    let cond = pop_bool!();
                    if !cond {
                        let target = (next_ip as i64 + offset as i64) as usize;
                        self.frames[frame_idx].ip = target;
                    }
                }
                x if x == Opcode::JumpIfTrue as u8 => {
                    let offset = operands[0] as u32 as i32;
                    let cond = pop_bool!();
                    if cond {
                        let target = (next_ip as i64 + offset as i64) as usize;
                        self.frames[frame_idx].ip = target;
                    }
                }
                x if x == Opcode::JumpIfNotNull as u8 => {
                    let offset = operands[0] as u32 as i32;
                    let v = self.pop();
                    if !v.is_null() {
                        let target = (next_ip as i64 + offset as i64) as usize;
                        self.frames[frame_idx].ip = target;
                    }
                }

                x if x == Opcode::Call as u8 => {
                    if let Some(d) = self.deadline {
                        if Instant::now() >= d {
                            return RunResult::Err(self.rt_error("E002", "execution cancelled"));
                        }
                    }
                    let fn_idx = operands[0] as usize;
                    let arg_count = operands[1] as usize;

                    if fn_idx >= prog.functions.len() {
                        return RunResult::Err(
                            self.error_at_current("E014", &format!("invalid function index: {}", fn_idx)),
                        );
                    }

                    let target = &prog.functions[fn_idx];
                    let total_locals = target.param_count + target.local_count;

                    if self.limits.max_call_depth > 0 && self.frames.len() >= self.limits.max_call_depth {
                        return RunResult::Err(
                            self.error_at_current("E015", "maximum call depth exceeded"),
                        );
                    }

                    let mut args: Vec<Value> = Vec::with_capacity(arg_count);
                    for _ in 0..arg_count {
                        args.push(self.pop());
                    }
                    args.reverse();

                    let stack_base = self.stack.len();
                    self.frames.push(CallFrame {
                        function_id: fn_idx,
                        ip: 0,
                        stack_base,
                        local_base: 0,
                        local_count: total_locals,
                    });

                    for i in 0..total_locals {
                        if i < target.param_count && i < args.len() {
                            self.stack.push(args[i].clone());
                        } else {
                            self.stack.push(Value::Null);
                        }
                    }
                }

                x if x == Opcode::CallNative as u8 => {
                    let native_idx = operands[0] as usize;
                    let arg_count = operands[1] as usize;

                    if native_idx >= prog.natives.len() {
                        match self.throw_runtime(
                            "E016",
                            &format!("invalid native function index: {}", native_idx),
                        ) {
                            Some(e) => return RunResult::Err(e),
                            None => continue,
                        }
                    }

                    let nd = &prog.natives[native_idx];

                    let mut args: Vec<Value> = Vec::with_capacity(arg_count);
                    for _ in 0..arg_count {
                        args.push(self.pop());
                    }
                    args.reverse();

                    let nf = match self.native_cache[native_idx] {
                        Some(h) => h,
                        None => {
                            match self.throw_runtime(
                                "E017",
                                &format!("native function not found: {}.{}", nd.module, nd.name),
                            ) {
                                Some(e) => return RunResult::Err(e),
                                None => continue,
                            }
                        }
                    };

                    match nf(&args) {
                        Ok(result) => {
                            if nd.ret {
                                self.push(result);
                            }
                        }
                        Err(err) => {
                            match self.throw_runtime("E018", &err) {
                                Some(e) => return RunResult::Err(e),
                                None => continue,
                            }
                        }
                    }
                }

                x if x == Opcode::Return as u8 => {
                    if let Some(d) = self.deadline {
                        if Instant::now() >= d {
                            return RunResult::Err(self.rt_error("E002", "execution cancelled"));
                        }
                    }
                    let val = self.pop();
                    self.pop_frame();

                    if self.frames.is_empty() {
                        return RunResult::Done(val);
                    }
                    self.push(val);
                }
                x if x == Opcode::ReturnVoid as u8 => {
                    if let Some(d) = self.deadline {
                        if Instant::now() >= d {
                            return RunResult::Err(self.rt_error("E002", "execution cancelled"));
                        }
                    }
                    self.pop_frame();

                    if self.frames.is_empty() {
                        return RunResult::Done(Value::Null);
                    }
                }
                x if x == Opcode::ReturnMulti as u8 => {
                    if let Some(d) = self.deadline {
                        if Instant::now() >= d {
                            return RunResult::Err(self.rt_error("E002", "execution cancelled"));
                        }
                    }
                    let count = operands[0] as usize;
                    let mut vals: Vec<Value> = Vec::with_capacity(count);
                    for _ in 0..count {
                        vals.push(self.pop());
                    }
                    vals.reverse();
                    self.pop_frame();

                    if self.frames.is_empty() {
                        if count > 0 {
                            return RunResult::Done(vals[0].clone());
                        }
                        return RunResult::Done(Value::Null);
                    }
                    for v in vals {
                        self.push(v);
                    }
                }

                x if x == Opcode::NewList as u8 => {
                    self.push(Value::List(Rc::new(RefCell::new(Vec::new()))));
                }

                x if x == Opcode::ListGet as u8 => {
                    let idx = pop_int!();
                    let val = self.pop();

                    match &val {
                        Value::List(l) => {
                            let l = l.borrow();
                            if idx < 0 || idx as usize >= l.len() {
                                match self.throw_runtime(
                                    "E020",
                                    &format!("list index out of range: {} (length {})", idx, l.len()),
                                ) {
                                    Some(e) => return RunResult::Err(e),
                                    None => continue,
                                }
                            }
                            let v = l[idx as usize].clone();
                            self.push(v);
                        }
                        Value::Stack(s) => {
                            let s = s.borrow();
                            if idx < 0 || idx as usize >= s.len() {
                                let len = s.len();
                                drop(s);
                                match self.throw_runtime(
                                    "E020",
                                    &format!("stack index out of range: {} (length {})", idx, len),
                                ) {
                                    Some(e) => return RunResult::Err(e),
                                    None => continue,
                                }
                            }
                            let v = s[idx as usize].clone();
                            drop(s);
                            self.push(v);
                        }
                        Value::Str(s) => {
                            let chars: Vec<char> = s.chars().collect();
                            if idx < 0 || idx as usize >= chars.len() {
                                match self.throw_runtime(
                                    "E020",
                                    &format!("string index out of range: {} (length {})", idx, chars.len()),
                                ) {
                                    Some(e) => return RunResult::Err(e),
                                    None => continue,
                                }
                            }
                            self.push(Value::Char(chars[idx as usize] as u32));
                        }
                        _ => {
                            match self.throw_runtime("E019", "cannot index non-list/non-stack") {
                                Some(e) => return RunResult::Err(e),
                                None => continue,
                            }
                        }
                    }
                }

                x if x == Opcode::ListSet as u8 => {
                    let val = self.pop();
                    let idx = pop_int!();
                    let list = self.pop();

                    let l = match &list {
                        Value::List(l) => l.clone(),
                        _ => {
                            match self.throw_runtime("E021", "cannot index non-list") {
                                Some(e) => return RunResult::Err(e),
                                None => continue,
                            }
                        }
                    };
                    let len = l.borrow().len();
                    if idx < 0 || idx as usize >= len {
                        match self.throw_runtime(
                            "E022",
                            &format!("list index out of range: {} (length {})", idx, len),
                        ) {
                            Some(e) => return RunResult::Err(e),
                            None => continue,
                        }
                    }
                    l.borrow_mut()[idx as usize] = val;
                }

                x if x == Opcode::ListAppend as u8 => {
                    let val = self.pop();
                    let list = self.pop();

                    let l = match &list {
                        Value::List(l) => l.clone(),
                        _ => {
                            match self.throw_runtime("E023", "cannot append to non-list") {
                                Some(e) => return RunResult::Err(e),
                                None => continue,
                            }
                        }
                    };
                    if l.borrow().len() >= self.limits.max_list_size {
                        match self.throw_runtime("E024", "list size limit exceeded") {
                            Some(e) => return RunResult::Err(e),
                            None => continue,
                        }
                    }
                    l.borrow_mut().push(val);
                    self.push(Value::List(l));
                }

                x if x == Opcode::ListExtend as u8 => {
                    let src = self.pop();
                    let dst = self.pop();

                    let (dl, sl) = match (&dst, &src) {
                        (Value::List(d), Value::List(s)) => (d.clone(), s.clone()),
                        _ => {
                            match self.throw_runtime("E023", "cannot spread non-list") {
                                Some(e) => return RunResult::Err(e),
                                None => continue,
                            }
                        }
                    };
                    let total = dl.borrow().len() + sl.borrow().len();
                    if total > self.limits.max_list_size {
                        match self.throw_runtime("E024", "list size limit exceeded") {
                            Some(e) => return RunResult::Err(e),
                            None => continue,
                        }
                    }
                    let src_vals: Vec<Value> = sl.borrow().clone();
                    dl.borrow_mut().extend(src_vals);
                    self.push(Value::List(dl));
                }

                x if x == Opcode::ListLength as u8 => {
                    let val = self.pop();
                    match &val {
                        Value::List(l) => {
                            self.push(Value::Int(l.borrow().len() as i64));
                        }
                        Value::Stack(s) => {
                            self.push(Value::Int(s.borrow().len() as i64));
                        }
                        Value::Str(s) => {
                            self.push(Value::Int(s.chars().count() as i64));
                        }
                        _ => {
                            return RunResult::Err(
                                self.error_at_current("E025", "cannot get length of non-list/non-stack"),
                            );
                        }
                    }
                }

                x if x == Opcode::NewMap as u8 => {
                    self.push(Value::Map(Rc::new(MapValue {
                        entries: RefCell::new(Vec::new()),
                    })));
                }

                x if x == Opcode::MapGet as u8 => {
                    let key = self.pop();
                    let m = self.pop();

                    let mv = match &m {
                        Value::Map(m) => m.clone(),
                        _ => {
                            match self.throw_runtime("E026", "cannot index non-map") {
                                Some(e) => return RunResult::Err(e),
                                None => continue,
                            }
                        }
                    };
                    match mv.find_entry(&key) {
                        Some(idx) => {
                            let v = mv.entries.borrow()[idx].1.clone();
                            self.push(v);
                        }
                        None => {
                            self.push(Value::Null);
                        }
                    }
                }

                x if x == Opcode::MapSet as u8 => {
                    let val = self.pop();
                    let key = self.pop();
                    let m = self.pop();

                    let mv = match &m {
                        Value::Map(m) => m.clone(),
                        _ => {
                            return RunResult::Err(self.error_at_current("E027", "cannot set on non-map"));
                        }
                    };
                    match mv.find_entry(&key) {
                        Some(idx) => {
                            mv.entries.borrow_mut()[idx].1 = val;
                        }
                        None => {
                            if mv.entries.borrow().len() >= self.limits.max_map_size {
                                return RunResult::Err(
                                    self.error_at_current("E028", "map size limit exceeded"),
                                );
                            }
                            mv.entries.borrow_mut().push((key, val));
                        }
                    }
                    self.push(Value::Map(mv));
                }

                x if x == Opcode::MapKeys as u8 => {
                    let m = self.pop();
                    let mv = match &m {
                        Value::Map(m) => m.clone(),
                        _ => {
                            return RunResult::Err(self.error_at_current("E039", "cannot get keys of non-map"));
                        }
                    };
                    let keys: Vec<Value> = mv.entries.borrow().iter().map(|(k, _)| k.clone()).collect();
                    self.push(Value::List(Rc::new(RefCell::new(keys))));
                }

                x if x == Opcode::NewStack as u8 => {
                    self.push(Value::Stack(Rc::new(RefCell::new(Vec::new()))));
                }

                x if x == Opcode::StackPush as u8 => {
                    let val = self.pop();
                    let stack_val = self.pop();
                    match &stack_val {
                        Value::Stack(_) => {
                            stack_val.stack_push(val);
                            self.push(stack_val);
                        }
                        _ => {
                            return RunResult::Err(self.error_at_current("E060", "cannot push to non-stack"));
                        }
                    }
                }

                x if x == Opcode::StackPop as u8 => {
                    let stack_val = self.pop();
                    match &stack_val {
                        Value::Stack(_) => {
                            if stack_val.stack_len() == 0 {
                                match self.throw_runtime("E062", "pop from empty stack") {
                                    Some(e) => return RunResult::Err(e),
                                    None => continue,
                                }
                            }
                            let top = stack_val.stack_pop().unwrap_or(Value::Null);
                            self.push(top);
                            self.push(stack_val);
                        }
                        _ => {
                            return RunResult::Err(self.error_at_current("E061", "cannot pop from non-stack"));
                        }
                    }
                }

                x if x == Opcode::StackPeek as u8 => {
                    let stack_val = self.pop();
                    match &stack_val {
                        Value::Stack(_) => {
                            if stack_val.stack_len() == 0 {
                                match self.throw_runtime("E064", "peek from empty stack") {
                                    Some(e) => return RunResult::Err(e),
                                    None => continue,
                                }
                            }
                            let top = stack_val.stack_peek().unwrap_or(Value::Null);
                            self.push(top);
                        }
                        _ => {
                            return RunResult::Err(self.error_at_current("E063", "cannot peek from non-stack"));
                        }
                    }
                }

                x if x == Opcode::StackSize as u8 => {
                    let stack_val = self.pop();
                    match &stack_val {
                        Value::Stack(_) => {
                            self.push(Value::Int(stack_val.stack_len() as i64));
                        }
                        _ => {
                            return RunResult::Err(self.error_at_current("E065", "cannot get size of non-stack"));
                        }
                    }
                }

                x if x == Opcode::CheckNotNull as u8 => {
                    let v = self.peek();
                    if v.is_null() {
                        match self.throw_runtime("E031", "null reference") {
                            Some(e) => return RunResult::Err(e),
                            None => continue,
                        }
                    }
                }

                x if x == Opcode::CheckType as u8 => {
                    let type_idx = operands[0] as usize;
                    let nullable = operands[1] != 0;
                    let expected = if type_idx < fn_struct.constants.len() {
                        fn_struct.constants[type_idx].s.clone()
                    } else {
                        String::new()
                    };
                    let v = self.peek();
                    if v.is_null() {
                        if !nullable {
                            match self.throw_runtime(
                                "E066",
                                &format!("type mismatch: expected {}, got null", expected),
                            ) {
                                Some(e) => return RunResult::Err(e),
                                None => continue,
                            }
                        }
                    } else {
                        let actual = value_type_tag(&v);
                        if actual != expected {
                            match self.throw_runtime(
                                "E066",
                                &format!("type mismatch: expected {}, got {}", expected, actual),
                            ) {
                                Some(e) => return RunResult::Err(e),
                                None => continue,
                            }
                        }
                    }
                }

                x if x == Opcode::StructNew as u8 => {
                    let field_count = operands[0] as usize;
                    let type_name_idx = operands[1] as usize;
                    let type_name = if type_name_idx < fn_struct.constants.len() {
                        fn_struct.constants[type_name_idx].s.clone()
                    } else {
                        String::new()
                    };
                    let mut fields: Vec<Value> = Vec::with_capacity(field_count);
                    for _ in 0..field_count {
                        fields.push(self.pop());
                    }
                    fields.reverse();
                    self.push(Value::Struct(Rc::new(StructValue {
                        type_name,
                        fields: RefCell::new(fields),
                    })));
                }

                x if x == Opcode::FieldLoad as u8 => {
                    let field_idx = operands[0] as usize;
                    let obj = self.pop();
                    let sv = match &obj {
                        Value::Struct(s) => s.clone(),
                        _ => {
                            return RunResult::Err(
                                self.error_at_current("E042", "cannot access field of non-struct value"),
                            );
                        }
                    };
                    let len = sv.fields.borrow().len();
                    if field_idx >= len {
                        return RunResult::Err(
                            self.error_at_current("E043", &format!("field index {} out of range", field_idx)),
                        );
                    }
                    let v = sv.fields.borrow()[field_idx].clone();
                    self.push(v);
                }

                x if x == Opcode::FieldStore as u8 => {
                    let field_idx = operands[0] as usize;
                    let val = self.pop();
                    let obj = self.pop();
                    let sv = match &obj {
                        Value::Struct(s) => s.clone(),
                        _ => {
                            return RunResult::Err(
                                self.error_at_current("E044", "cannot store field of non-struct value"),
                            );
                        }
                    };
                    let len = sv.fields.borrow().len();
                    if field_idx >= len {
                        return RunResult::Err(
                            self.error_at_current("E045", &format!("field index {} out of range", field_idx)),
                        );
                    }
                    sv.fields.borrow_mut()[field_idx] = val;
                }

                x if x == Opcode::TraitNew as u8 => {
                    let trait_name_idx = operands[0] as usize;
                    let struct_type_name_idx = operands[1] as usize;
                    let struct_val = self.pop();

                    let trait_name = if trait_name_idx < fn_struct.constants.len() {
                        fn_struct.constants[trait_name_idx].s.clone()
                    } else {
                        String::new()
                    };
                    let struct_type_name = if struct_type_name_idx < fn_struct.constants.len() {
                        fn_struct.constants[struct_type_name_idx].s.clone()
                    } else {
                        String::new()
                    };

                    let methods = self.build_trait_method_table(&trait_name, &struct_type_name);
                    self.push(Value::Trait(Rc::new(TraitValue {
                        data: struct_val,
                        methods,
                        type_name: struct_type_name,
                    })));
                }

                x if x == Opcode::TraitInvoke as u8 => {
                    let method_idx = operands[0] as usize;
                    let arg_count = operands[1] as usize;

                    let mut args: Vec<Value> = Vec::with_capacity(arg_count);
                    for _ in 0..arg_count {
                        args.push(self.pop());
                    }
                    args.reverse();

                    let trait_val = self.pop();
                    let tv = match &trait_val {
                        Value::Trait(t) => t.clone(),
                        _ => {
                            return RunResult::Err(
                                self.error_at_current("E050", "expected trait value for method invocation"),
                            );
                        }
                    };

                    if method_idx >= tv.methods.len() {
                        return RunResult::Err(self.error_at_current(
                            "E051",
                            &format!("trait method index {} out of range", method_idx),
                        ));
                    }
                    let fn_idx = tv.methods[method_idx];

                    if fn_idx >= prog.functions.len() {
                        return RunResult::Err(self.error_at_current(
                            "E052",
                            &format!("invalid function index {} for trait method", fn_idx),
                        ));
                    }

                    let target = &prog.functions[fn_idx];
                    let total_locals = target.param_count + target.local_count;

                    if self.limits.max_call_depth > 0 && self.frames.len() >= self.limits.max_call_depth {
                        return RunResult::Err(
                            self.error_at_current("E015", "maximum call depth exceeded"),
                        );
                    }

                    let mut call_args: Vec<Value> = Vec::with_capacity(1 + arg_count);
                    call_args.push(tv.data.clone());
                    call_args.extend(args);

                    let stack_base = self.stack.len();
                    self.frames.push(CallFrame {
                        function_id: fn_idx,
                        ip: 0,
                        stack_base,
                        local_base: 0,
                        local_count: total_locals,
                    });

                    for i in 0..total_locals {
                        if i < target.param_count && i < call_args.len() {
                            self.stack.push(call_args[i].clone());
                        } else {
                            self.stack.push(Value::Null);
                        }
                    }
                }

                x if x == Opcode::ConvertByteToInt as u8 => {
                    let v = self.pop();
                    let b = match &v {
                        Value::Byte(b) => *b,
                        _ => 0,
                    };
                    self.push(Value::Int(b as i64));
                }
                x if x == Opcode::ConvertIntToFloat as u8 => {
                    let v = pop_int!();
                    self.push(Value::Float(v as f64));
                }

                x if x == Opcode::Throw as u8 => {
                    let exc_val = self.pop();
                    let msg = match &exc_val {
                        Value::Exception(e) => e.message.clone(),
                        other => other.display_string(),
                    };

                    let handled = self.handle_exception(&exc_val);
                    if !handled {
                        return RunResult::Err(
                            self.error_at_current("E040", &format!("uncaught exception: {}", msg)),
                        );
                    }
                }

                x if x == Opcode::SetupHandler as u8 => {
                    // Full instruction: op(1) + catch(4) + finally(4) + depth(2)
                    // decode_op advanced the IP past 2 operands (offset+9).
                    let insn_start = next_ip.saturating_sub(9);
                    let catch_offset = operands[0] as u32 as i32;
                    let finally_offset = operands[1] as u32 as i32;
                    let stack_depth_pos = insn_start + 9;
                    if stack_depth_pos + 1 < fn_struct.code.len() {
                        let stack_depth =
                            u16::from_be_bytes([fn_struct.code[stack_depth_pos], fn_struct.code[stack_depth_pos + 1]])
                                as usize;
                        let insn_end = insn_start + 11;

                        let catch_target = if catch_offset != 0 {
                            (insn_end as i64 + catch_offset as i64) as usize
                        } else {
                            0
                        };
                        let finally_target = if finally_offset != 0 {
                            (insn_end as i64 + finally_offset as i64) as usize
                        } else {
                            0
                        };

                        self.handler_stack.push(HandlerEntry {
                            catch_offset: catch_target,
                            finally_offset: finally_target,
                            stack_depth,
                            frame_index: self.frames.len() - 1,
                            active: true,
                            in_finally: false,
                        });
                    }
                    self.frames[frame_idx].ip = insn_start + 11;
                }

                x if x == Opcode::RemoveHandler as u8 => {
                    self.handler_stack.pop();
                    if let Some(exc_val) = self.pending_exception.take() {
                        let msg = match &exc_val {
                            Value::Exception(e) => e.message.clone(),
                            other => other.display_string(),
                        };
                        let handled = self.handle_exception(&exc_val);
                        if !handled {
                            return RunResult::Err(
                                self.error_at_current("E040", &format!("uncaught exception: {}", msg)),
                            );
                        }
                    }
                }

                x if x == Opcode::NewException as u8 => {
                    let msg = self.pop().display_string();
                    let exc_val = self.build_exception_value(&msg);
                    self.push(exc_val);
                }

                x if x == Opcode::ExceptionField as u8 => {
                    let field_id = operands[0] as usize;
                    let exc_val = self.pop();
                    let ev = match &exc_val {
                        Value::Exception(e) => e.clone(),
                        other => {
                            return RunResult::Err(self.error_at_current(
                                "E041",
                                &format!("expected exception, got {}", other.display_string()),
                            ));
                        }
                    };
                    match field_id {
                        0 => self.push(Value::str(ev.message.clone())),
                        1 => self.push(Value::str(ev.trace.clone())),
                        _ => {}
                    }
                }

                _ => {
                    return RunResult::Err(
                        self.error_at_current("E032", &format!("unknown opcode: {}", op)),
                    );
                }
            }
        }
    }

    // ---- Stack operations ----

    fn push(&mut self, v: Value) {
        if self.stack.len() >= self.limits.max_stack_size {
            // Hard limit: drop the value (mirrors Go behavior)
            return;
        }
        self.stack.push(v);
    }

    fn pop(&mut self) -> Value {
        match self.stack.pop() {
            Some(v) => v,
            None => Value::Null,
        }
    }

    fn peek(&self) -> Value {
        self.stack.last().cloned().unwrap_or(Value::Null)
    }

    fn pop_frame(&mut self) {
        if let Some(f) = self.frames.pop() {
            if f.stack_base < self.stack.len() {
                self.stack.truncate(f.stack_base);
            }
        }
    }

    fn build_trait_method_table(&self, trait_name: &str, struct_type_name: &str) -> Vec<usize> {
        if let Some(prog) = &self.program {
            for table in &prog.trait_tables {
                if table.trait_name == trait_name && table.struct_type_name == struct_type_name {
                    return table.method_indices.clone();
                }
            }
        }
        Vec::new()
    }

    // ---- Exception handling ----

    /// Looks for a handler that can catch the exception. Returns true when
    /// control was transferred to a handler.
    fn handle_exception(&mut self, exc_val: &Value) -> bool {
        let mut i = self.handler_stack.len() as i64 - 1;
        while i >= 0 {
            let idx = i as usize;
            let h_frame_index = self.handler_stack[idx].frame_index;
            let h_catch = self.handler_stack[idx].catch_offset;
            let h_finally = self.handler_stack[idx].finally_offset;
            let h_depth = self.handler_stack[idx].stack_depth;
            let h_active = self.handler_stack[idx].active;
            let h_in_finally = self.handler_stack[idx].in_finally;

            if h_frame_index > self.frames.len() - 1 {
                i -= 1;
                continue;
            }

            // Unwind frames down to the handler's frame
            while self.frames.len() > h_frame_index + 1 {
                self.pop_frame();
            }

            let cur_frame = self.frames.len() - 1;
            let stack_base = self.frames[cur_frame].stack_base;
            let local_count = self.frames[cur_frame].local_count;
            let target_depth = stack_base + local_count + h_depth;
            if target_depth < self.stack.len() {
                self.stack.truncate(target_depth);
            }

            if h_active && h_catch > 0 {
                self.push(exc_val.clone());
                self.frames[cur_frame].ip = h_catch;
                self.handler_stack[idx].active = false;
                self.pending_exception = None;
                return true;
            }

            if h_finally > 0 && !h_in_finally {
                self.pending_exception = Some(exc_val.clone());
                self.handler_stack[idx].in_finally = true;
                self.frames[cur_frame].ip = h_finally;
                self.handler_stack[idx].active = false;
                return true;
            }

            i -= 1;
        }
        false
    }

    fn build_exception_value(&self, msg: &str) -> Value {
        let mut b = String::new();
        b.push_str(&format!("exception: {}\n", msg));

        if let Some(prog) = &self.program {
            for i in (0..self.frames.len()).rev() {
                let f = &self.frames[i];
                let fn_struct = &prog.functions[f.function_id];
                b.push_str(&format!("  at {}", fn_struct.name));
                if f.ip < fn_struct.source_map.len() {
                    let sm = &fn_struct.source_map[f.ip];
                    b.push_str(&format!(" ({}:{}:{})", fn_struct.name, sm.start_line, sm.start_col));
                } else {
                    b.push_str(&format!(" (offset {})", f.ip));
                }
                b.push('\n');
            }
        }

        Value::Exception(Rc::new(ExceptionValue {
            message: msg.to_string(),
            trace: b,
        }))
    }

    /// Converts a runtime fault into a catchable exception. Returns Some(err)
    /// when uncaught (terminating execution), None when handled.
    fn throw_runtime(&mut self, code: &str, msg: &str) -> Option<RuntimeError> {
        let exc_val = self.build_exception_value(msg);
        if self.handle_exception(&exc_val) {
            return None;
        }
        Some(self.error_at_current(code, msg))
    }

    fn error_at_current(&self, code: &str, msg: &str) -> RuntimeError {
        let prog = self.program.as_ref().unwrap();
        let mut function = String::new();
        let mut offset = 0;
        let mut stack: Vec<StackFrame> = Vec::new();

        if let Some(f) = self.frames.last() {
            let fn_struct = &prog.functions[f.function_id];
            function = fn_struct.name.clone();
            offset = f.ip;
        }

        for f in self.frames.iter().rev() {
            let fn_struct = &prog.functions[f.function_id];
            stack.push(StackFrame {
                function: fn_struct.name.clone(),
                offset: f.ip,
                line: 0,
                column: 0,
            });
        }

        RuntimeError {
            code: code.to_string(),
            message: msg.to_string(),
            function,
            offset,
            line: 0,
            column: 0,
            stack,
        }
    }

    fn rt_error(&self, code: &str, msg: &str) -> RuntimeError {
        self.error_at_current(code, msg)
    }
}
