//! Compiles the typed AST into bytecode.
//!
//! Port of internal/compiler/compiler.go.

use crate::ast::*;
use crate::bytecode::{self, Constant, ConstantKind, NativeDecl, Opcode, TraitMethodTable};
use crate::diagnostic::Diagnostics;
use crate::symbol::{Scope, Symbol, SymbolKind};
use crate::types::{self, Kind, Type};
use std::cell::Cell;
use std::collections::HashMap;
use std::rc::Rc;

#[derive(Clone, Copy, PartialEq)]
enum FuncRef {
    Top(usize),
    Method(usize, usize),
}

struct LoopInfo {
    start_offset: usize,
    break_jumps: Vec<usize>,
}

/// A try context. `finally_body` is a raw pointer into the function body
/// being compiled (an owned local in `compile_function` that does not move
/// for the duration of compilation).
struct TryFrame {
    has_finally: bool,
    finally_body: *mut Block,
}

struct SlotRef {
    index: usize,
    is_ref_type: bool,
}

struct JumpPatch {
    offset: usize,
    target: usize,
    pending: bool,
}

struct HandlerMeta {
    setup_offset: usize,
    stack_depth: usize,
    catch_offset: usize,
    finally_offset: usize,
}

struct ConstantEntry {
    kind: ConstantKind,
    data: u64,
    s: String,
}

struct Emitter {
    code: Vec<u8>,
    offsets: Vec<usize>,
    max_stack: i32,
    curr_stack: i32,
    pending_jumps: Vec<JumpPatch>,
    constants: Vec<ConstantEntry>,
    string_map: HashMap<String, usize>,
    handler_info: Vec<HandlerMeta>,
}

impl Emitter {
    fn new() -> Emitter {
        Emitter {
            code: Vec::new(),
            offsets: Vec::new(),
            max_stack: 0,
            curr_stack: 0,
            pending_jumps: Vec::new(),
            constants: Vec::new(),
            string_map: HashMap::new(),
            handler_info: Vec::new(),
        }
    }

    fn stack_effect(&mut self, op: Opcode) {
        let info = bytecode::instruction_info(op as u8).unwrap();
        self.curr_stack -= info.pop_count;
        if self.curr_stack < 0 {
            self.curr_stack = 0;
        }
        self.curr_stack += info.push_count;
        if self.curr_stack > self.max_stack {
            self.max_stack = self.curr_stack;
        }
    }

    fn emit0(&mut self, op: Opcode) -> usize {
        let offset = self.code.len();
        self.code.push(op as u8);
        self.stack_effect(op);
        self.offsets.push(offset);
        offset
    }

    fn emit1(&mut self, op: Opcode, v: u64) -> usize {
        let offset = self.code.len();
        self.code.push(op as u8);
        self.stack_effect(op);
        let op_type = bytecode::instruction_info(op as u8).unwrap().operands[0];
        self.append_operand(op_type, v);
        self.offsets.push(offset);
        offset
    }

    fn emit2(&mut self, op: Opcode, v1: u64, v2: u64) -> usize {
        let offset = self.code.len();
        self.code.push(op as u8);
        self.stack_effect(op);
        let ops = bytecode::instruction_info(op as u8).unwrap().operands;
        self.append_operand(ops[0], v1);
        self.append_operand(ops[1], v2);
        self.offsets.push(offset);
        offset
    }

    fn append_operand(&mut self, op_type: crate::bytecode::OperandType, v: u64) {
        use crate::bytecode::OperandType;
        match op_type {
            OperandType::Uint8 => self.code.push(v as u8),
            OperandType::Uint16 => self.code.extend_from_slice(&(v as u16).to_be_bytes()),
            OperandType::Uint32 | OperandType::Float32 | OperandType::Int32 => {
                self.code.extend_from_slice(&(v as u32).to_be_bytes())
            }
            OperandType::Int64 | OperandType::Float64 => self.code.extend_from_slice(&v.to_be_bytes()),
            OperandType::Str | OperandType::FuncIndex => {
                self.code.extend_from_slice(&(v as u32).to_be_bytes())
            }
            OperandType::None => {}
        }
    }

    fn emit_jump(&mut self, op: Opcode) -> usize {
        let offset = self.code.len();
        self.code.push(op as u8);
        self.code.extend_from_slice(&[0, 0, 0, 0]);
        self.pending_jumps.push(JumpPatch {
            offset: offset + 1,
            target: 0,
            pending: true,
        });
        self.offsets.push(offset);
        self.pending_jumps.len() - 1
    }

    fn patch_jump(&mut self, jump_idx: usize) {
        if jump_idx >= self.pending_jumps.len() {
            return;
        }
        self.pending_jumps[jump_idx].target = self.code.len();
        self.pending_jumps[jump_idx].pending = false;
    }

    fn resolve_jumps(&mut self) -> Result<(), String> {
        for jp in &self.pending_jumps {
            if jp.pending {
                return Err(format!("unresolved jump at offset {}", jp.offset));
            }
            let inst_end = jp.offset + 4;
            let jmp_value = (jp.target as i64 - inst_end as i64) as u32;
            let bytes = jmp_value.to_be_bytes();
            self.code[jp.offset] = bytes[0];
            self.code[jp.offset + 1] = bytes[1];
            self.code[jp.offset + 2] = bytes[2];
            self.code[jp.offset + 3] = bytes[3];
        }
        Ok(())
    }

    fn finalize_handler(&mut self, idx: usize) {
        if idx >= self.handler_info.len() {
            return;
        }
        let h = &self.handler_info[idx];
        let insn_end = h.setup_offset + 11;
        let mut pos = h.setup_offset + 1;

        let rel_catch: i32 = if h.catch_offset > 0 {
            (h.catch_offset as i64 - insn_end as i64) as i32
        } else {
            0
        };
        let cb = rel_catch.to_be_bytes();
        self.code[pos..pos + 4].copy_from_slice(&cb);
        pos += 4;

        let rel_finally: i32 = if h.finally_offset > 0 {
            (h.finally_offset as i64 - insn_end as i64) as i32
        } else {
            0
        };
        let fb = rel_finally.to_be_bytes();
        self.code[pos..pos + 4].copy_from_slice(&fb);
        pos += 4;

        let sb = (h.stack_depth as u16).to_be_bytes();
        self.code[pos..pos + 2].copy_from_slice(&sb);
    }

    fn current_offset(&self) -> usize {
        self.code.len()
    }

    fn last_opcode(&self) -> u8 {
        if self.code.is_empty() {
            Opcode::Nop as u8
        } else {
            self.code[self.code.len() - 1]
        }
    }

    fn add_string(&mut self, s: &str) -> usize {
        if let Some(idx) = self.string_map.get(s) {
            return *idx;
        }
        let idx = self.constants.len();
        self.constants.push(ConstantEntry {
            kind: ConstantKind::Str,
            data: 0,
            s: s.to_string(),
        });
        self.string_map.insert(s.to_string(), idx);
        idx
    }

    fn build_constants(&self) -> Vec<Constant> {
        self.constants
            .iter()
            .map(|c| Constant {
                kind: c.kind,
                data: c.data,
                s: c.s.clone(),
            })
            .collect()
    }
}

fn base_symbol() -> Symbol {
    Symbol {
        name: String::new(),
        kind: SymbolKind::Variable,
        ty: None,
        slot: -1,
        parameter: false,
        module_name: String::new(),
        defined: Cell::new(false),
        mut_flag: false,
        is_struct_field: false,
        field_index: 0,
        field_of_slot: 0,
    }
}

pub struct Compiler {
    diags: Diagnostics,
    scope: Rc<Scope>,
    funcs: Vec<FuncRef>,
    func_map: HashMap<String, usize>,
    /// Module of each registered function index.
    func_modules: HashMap<usize, String>,
    /// (variadic, fixed param count, param types) per function index.
    func_call_info: HashMap<usize, (bool, usize, Vec<Rc<Type>>)>,
    /// trait name -> (method name -> slot index).
    trait_method_slots: HashMap<String, HashMap<String, usize>>,
    current_module: String,
    natives: Vec<NativeDecl>,
    native_map: HashMap<String, usize>,
    loops: Vec<LoopInfo>,
    trys: Vec<TryFrame>,
    next_slot: usize,
    free_slots: Vec<usize>,
    scope_slot_refs: Vec<Vec<SlotRef>>,
    inlining_finally: bool,
    current_ret_type: Option<Rc<Type>>,
    current_struct: Option<Rc<Type>>,
    #[allow(dead_code)]
    trait_types: HashMap<String, Rc<Type>>,
}

impl Compiler {
    pub fn new() -> Compiler {
        Compiler {
            diags: Diagnostics::new(),
            scope: Scope::new(None, None),
            funcs: Vec::new(),
            func_map: HashMap::new(),
            func_modules: HashMap::new(),
            func_call_info: HashMap::new(),
            trait_method_slots: HashMap::new(),
            current_module: String::new(),
            natives: Vec::new(),
            native_map: HashMap::new(),
            loops: Vec::new(),
            trys: Vec::new(),
            next_slot: 0,
            free_slots: Vec::new(),
            scope_slot_refs: Vec::new(),
            inlining_finally: false,
            current_ret_type: None,
            current_struct: None,
            trait_types: HashMap::new(),
        }
    }

    fn func<'a>(&self, prog: &'a Program, r: FuncRef) -> &'a Function {
        match r {
            FuncRef::Top(i) => &prog.funcs[i],
            FuncRef::Method(s, m) => &prog.structs[s].methods[m],
        }
    }

    fn register_func(&mut self, r: FuncRef, prog: &Program) {
        let idx = self.funcs.len();
        let f = self.func(prog, r);
        let name = f.name.clone();
        let module = f.module.clone();
        self.funcs.push(r);
        self.func_map.insert(name.clone(), idx);
        if !module.is_empty() {
            self.func_map.insert(format!("{}.{}", module, name), idx);
        }
        self.func_modules.insert(idx, module);

        // Call info for variadic dispatch
        let mut variadic = false;
        let mut param_types = Vec::new();
        for p in &f.parameters {
            param_types.push(p.ty.resolved());
            if p.variadic {
                variadic = true;
            }
        }
        let fixed = if variadic { f.parameters.len() - 1 } else { 0 };
        self.func_call_info.insert(idx, (variadic, fixed, param_types));
    }

    pub fn compile(&mut self, prog: &mut Program) -> (Option<bytecode::Program>, Diagnostics) {
        if !prog.module.is_empty() {
            self.current_module = prog.module.clone();
        }

        self.register_all_natives();

        // Collect function declarations
        for i in 0..prog.funcs.len() {
            self.register_func(FuncRef::Top(i), prog);
        }
        // Collect struct methods
        for s in 0..prog.structs.len() {
            for m in 0..prog.structs[s].methods.len() {
                self.register_func(FuncRef::Method(s, m), prog);
            }
        }

        // Collect trait types and method slots
        let mut traits = std::mem::take(&mut prog.traits);
        for td in &mut traits {
            let mut methods = HashMap::new();
            let mut slots = HashMap::new();
            for (slot, m) in td.methods.iter().enumerate() {
                let mut param_types = Vec::new();
                for p in &m.parameters {
                    param_types.push(p.ty.resolved());
                }
                let ret_type = if m.return_types.len() == 1 {
                    m.return_types[0].resolved()
                } else {
                    types::t_void()
                };
                methods.insert(
                    m.name.clone(),
                    Rc::new(types::TraitMethodInfo {
                        signature: types::function_type(param_types, Some(ret_type)),
                        is_pub: true,
                        is_mut: m.is_mut,
                    }),
                );
                slots.insert(m.name.clone(), slot);
            }
            self.trait_types
                .insert(td.name.clone(), types::trait_type(&td.name, methods));
            self.trait_method_slots.insert(td.name.clone(), slots);
        }

        // Snapshot trait structure for table building (methods per trait).
        let trait_structure: Vec<(String, Vec<String>)> = traits
            .iter()
            .map(|td| (td.name.clone(), td.methods.iter().map(|m| m.name.clone()).collect()))
            .collect();
        prog.traits = traits;

        // Compile each function
        let mut bc_funcs: Vec<Option<bytecode::Function>> = Vec::new();
        for i in 0..self.funcs.len() {
            let r = self.funcs[i];
            match self.compile_function(prog, r) {
                Some(f) => bc_funcs.push(Some(f)),
                None => {
                    let span = self.func(prog, r).span.clone();
                    self.diags.add_error("CP001", "compilation error", span);
                    bc_funcs.push(None);
                }
            }
        }

        if self.diags.has_errors() {
            return (None, std::mem::take(&mut self.diags));
        }

        // Build trait method tables
        let mut trait_tables = Vec::new();
        for (trait_name, method_names) in &trait_structure {
            for sd in &prog.structs {
                let mut satisfies = true;
                let mut table = TraitMethodTable {
                    trait_name: trait_name.clone(),
                    struct_type_name: sd.name.clone(),
                    method_indices: Vec::with_capacity(method_names.len()),
                };
                for method_name in method_names {
                    let full = format!("{}.{}", sd.name, method_name);
                    if let Some(fn_idx) = self.func_map.get(&full) {
                        table.method_indices.push(*fn_idx);
                    } else {
                        satisfies = false;
                        break;
                    }
                }
                if satisfies {
                    trait_tables.push(table);
                }
            }
        }

        let mut bc_prog = bytecode::Program::new();
        bc_prog.functions = bc_funcs.into_iter().map(|f| f.unwrap()).collect();
        bc_prog.natives = self.natives.clone();
        bc_prog.trait_tables = trait_tables;

        (Some(bc_prog), std::mem::take(&mut self.diags))
    }

    fn register_all_natives(&mut self) {
        let entries: &[(&str, &str, i32, bool)] = &[
            ("core", "print", 1, false),
            ("core", "println", 1, false),
            ("core", "string", 1, true),
            ("core", "int", 1, true),
            ("core", "float", 1, true),
            ("core", "byte", 1, true),
            ("core", "bool", 1, true),
            ("core", "typeOf", 1, true),
            ("core", "isType", 2, true),
            ("core", "regex", 1, true),
            ("string", "len", 1, true),
            ("string", "byteLength", 1, true),
            ("string", "charAt", 2, true),
            ("string", "substring", 3, true),
            ("string", "contains", 2, true),
            ("string", "startsWith", 2, true),
            ("string", "endsWith", 2, true),
            ("string", "indexOf", 2, true),
            ("string", "toUpper", 1, true),
            ("string", "toLower", 1, true),
            ("string", "trim", 1, true),
            ("string", "split", 2, true),
            ("string", "join", 2, true),
            ("math", "PI", 0, true),
            ("math", "E", 0, true),
            ("math", "abs", 1, true),
            ("math", "min", 2, true),
            ("math", "max", 2, true),
            ("math", "floor", 1, true),
            ("math", "ceil", 1, true),
            ("math", "round", 1, true),
            ("math", "sqrt", 1, true),
            ("math", "pow", 2, true),
            ("math", "sin", 1, true),
            ("math", "cos", 1, true),
            ("math", "tan", 1, true),
            ("env", "get", 1, true),
            ("env", "set", 2, false),
            ("env", "keys", 0, true),
            ("file", "read", 1, true),
            ("file", "write", 2, false),
            ("file", "append", 2, false),
            ("file", "delete", 1, false),
            ("file", "exists", 1, true),
            ("file", "temp", 1, true),
            ("file", "tempDir", 1, true),
            ("map", "contains", 2, true),
            ("map", "len", 1, true),
            ("list", "len", 1, true),
            ("process", "args", 0, true),
            ("process", "run", -1, true),
            ("time", "now", 0, true),
            ("time", "sleep", 1, false),
            ("random", "float", 0, true),
            ("random", "int", 2, true),
            ("random", "range", 2, true),
            ("random", "uniform", 2, true),
            ("random", "choice", 1, true),
            ("random", "shuffle", 1, true),
            ("random", "sample", 2, true),
            ("random", "seed", 1, false),
            ("path", "join", -1, true),
            ("path", "basename", 1, true),
            ("path", "dirname", 1, true),
            ("path", "ext", 1, true),
            ("path", "abs", 1, true),
            ("path", "exists", 1, true),
            ("base64", "encode", 1, true),
            ("base64", "decode", 1, true),
            ("hash", "md5", 1, true),
            ("hash", "sha1", 1, true),
            ("hash", "sha256", 1, true),
            ("hash", "sha512", 1, true),
            ("secrets", "token", 1, true),
            ("secrets", "hex", 1, true),
            ("stack", "push", 2, false),
            ("stack", "pop", 1, true),
            ("stack", "peek", 1, true),
            ("stack", "len", 1, true),
            ("stack", "isEmpty", 1, true),
        ];
        for (module, name, params, ret) in entries {
            self.register_native(module, name, *params, *ret);
        }
    }

    fn register_native(&mut self, module: &str, name: &str, params: i32, ret: bool) {
        let key = format!("{}.{}", module, name);
        if !self.native_map.contains_key(&key) {
            let idx = self.natives.len();
            self.natives.push(NativeDecl {
                module: module.to_string(),
                name: name.to_string(),
                params,
                ret,
            });
            self.native_map.insert(key, idx);
        }
    }

    fn compile_function(&mut self, prog: &mut Program, r: FuncRef) -> Option<bytecode::Function> {
        let old_scope = self.scope.clone();
        self.scope = Scope::new(Some(old_scope.clone()), None);

        let (module, name, param_count, return_count) = {
            let f = self.func(prog, r);
            (
                f.module.clone(),
                f.name.clone(),
                f.parameters.len(),
                f.return_types.len(),
            )
        };
        if !module.is_empty() {
            self.current_module = module;
        }

        let ret_type = {
            let f = self.func(prog, r);
            if f.return_types.len() == 1 {
                f.return_types[0].resolved()
            } else {
                types::t_void()
            }
        };
        self.current_ret_type = Some(ret_type);

        self.next_slot = 0;
        self.free_slots.clear();
        self.scope_slot_refs.clear();

        // Declare parameters
        for pi in 0..param_count {
            let (pname, pty) = {
                let f = self.func(prog, r);
                let p = &f.parameters[pi];
                (p.name.clone(), p.ty.resolved())
            };
            let slot = self.allocate_slot(pty.is_reference_type());
            let sym = Rc::new(Symbol {
                name: pname,
                kind: SymbolKind::Variable,
                ty: Some(pty),
                slot: slot as i32,
                parameter: true,
                ..base_symbol()
            });
            sym.defined.set(true);
            self.scope.declare(sym);
        }

        // Struct method receiver
        let old_struct = self.current_struct.clone();
        self.current_struct = None;
        let struct_name = self.func(prog, r).struct_name.clone();
        if !struct_name.is_empty() && param_count > 0 {
            let self_type = self.func(prog, r).parameters[0].ty.resolved();
            if self_type.kind == Kind::Struct {
                self.current_struct = Some(self_type.clone());
                if let Some(self_sym) = self.scope.resolve("_self") {
                    let slot = self_sym.slot;
                    let sym = Rc::new(Symbol {
                        name: "self".to_string(),
                        kind: SymbolKind::Variable,
                        ty: Some(self_type.clone()),
                        slot,
                        parameter: true,
                        ..base_symbol()
                    });
                    sym.defined.set(true);
                    self.scope.declare(sym);
                    let fields: Vec<(String, Rc<Type>, bool)> = self_type
                        .struct_fields
                        .iter()
                        .map(|f| (f.name.clone(), f.ty.clone(), f.is_mut))
                        .collect();
                    for (i, (fname, fty, fmut)) in fields.into_iter().enumerate() {
                        let sym = Rc::new(Symbol {
                            name: fname,
                            kind: SymbolKind::Variable,
                            ty: Some(fty),
                            slot,
                            defined: Cell::new(true),
                            mut_flag: fmut,
                            is_struct_field: true,
                            field_index: i,
                            field_of_slot: slot,
                            parameter: false,
                            module_name: String::new(),
                        });
                        self.scope.declare(sym);
                    }
                }
            }
        }

        let mut emitter = Emitter::new();
        let old_loops = std::mem::take(&mut self.loops);
        let old_trys = std::mem::take(&mut self.trys);

        // Move the body out of the program for the duration of compilation so
        // the borrow below is on a stable local. The local never moves again,
        // which keeps the raw finally-block pointers in `self.trys` valid.
        let mut body_taken = match r {
            FuncRef::Top(i) => prog.funcs[i].body.take(),
            FuncRef::Method(s, m) => prog.structs[s].methods[m].body.take(),
        };
        if let Some(body) = body_taken.as_mut() {
            self.compile_block(body, &mut emitter);
        }

        // Emit return if not already terminated
        let last = emitter.last_opcode();
        if last != Opcode::Return as u8
            && last != Opcode::ReturnVoid as u8
            && last != Opcode::ReturnMulti as u8
        {
            match return_count {
                0 => {
                    emitter.emit0(Opcode::ReturnVoid);
                }
                1 => {
                    emitter.emit0(Opcode::Return);
                }
                _ => {
                    for _ in 0..return_count {
                        emitter.emit0(Opcode::ConstNull);
                    }
                    emitter.emit1(Opcode::ReturnMulti, return_count as u64);
                }
            }
        }

        emitter.resolve_jumps().ok()?;

        let local_count = self.next_slot.saturating_sub(param_count);
        self.loops = old_loops;
        self.trys = old_trys;

        // Restore the body
        match r {
            FuncRef::Top(i) => prog.funcs[i].body = body_taken,
            FuncRef::Method(s, m) => prog.structs[s].methods[m].body = body_taken,
        }

        self.current_struct = old_struct;
        self.scope = old_scope;

        let constants = emitter.build_constants();
        let code = emitter.code;
        Some(bytecode::Function {
            name,
            param_count,
            local_count,
            max_stack: emitter.max_stack.max(0) as usize,
            return_count,
            code,
            source_map: Vec::new(),
            constants,
        })
    }

    fn compile_block(&mut self, block: &mut Block, e: &mut Emitter) {
        let old_scope = self.scope.clone();
        let ft = self.scope.func_type.clone();
        self.scope = Scope::new(Some(old_scope.clone()), ft);
        self.scope_slot_refs.push(Vec::new());

        for stmt in &mut block.statements {
            self.compile_statement(stmt, e);
        }

        self.exit_scope(e);
        self.scope = old_scope;
    }

    fn compile_statement(&mut self, stmt: &mut StmtNode, e: &mut Emitter) {
        match &mut stmt.kind {
            StmtKind::VarDecl(d) => self.compile_var_decl(d, e),
            StmtKind::Expr(x) => self.compile_expr_stmt(x, e),
            StmtKind::Block(b) => self.compile_block(b, e),
            StmtKind::If(s) => self.compile_if_stmt(s, e, false),
            StmtKind::While(s) => self.compile_while_stmt(s, e),
            StmtKind::For(s) => self.compile_for_stmt(s, e),
            StmtKind::Try(s) => self.compile_try_stmt(s, e),
            StmtKind::Throw(x) => self.compile_throw_stmt(x, e),
            StmtKind::Switch(s) => self.compile_switch_stmt(s, e),
            StmtKind::Return(values) => self.compile_return_stmt(values, e),
            StmtKind::Break => self.compile_break_stmt(e),
            StmtKind::Continue => self.compile_continue_stmt(e),
        }
    }

    fn compile_var_decl(&mut self, decl: &mut VarDecl, e: &mut Emitter) {
        if let Some(init) = &mut decl.init {
            self.compile_expr(init, e);
            let declared = decl.ty.resolved();
            let init_type = init.get_type();
            if declared.is_exception() {
                if let Some(it) = &init_type {
                    if it.is_string() {
                        e.emit0(Opcode::NewException);
                    }
                }
            }
            if let Some(it) = &init_type {
                self.emit_widening_conversion(it, &declared, e);
            }
            if declared.kind == Kind::Trait {
                if let Some(it) = &init_type {
                    if it.kind == Kind::Struct {
                        self.emit_trait_new(it, &declared, e);
                    }
                }
            }
            self.emit_type_check(init_type.as_ref(), &declared, e);
        } else {
            e.emit0(Opcode::ConstNull);
        }

        let var_type = decl.ty.resolved();
        let slot = self.allocate_slot(var_type.is_reference_type());
        let sym = Rc::new(Symbol {
            name: decl.name.clone(),
            kind: SymbolKind::Variable,
            ty: Some(var_type),
            slot: slot as i32,
            defined: Cell::new(true),
            mut_flag: decl.is_mut,
            ..base_symbol()
        });
        self.scope.declare(sym);
        e.emit1(Opcode::StoreLocal, slot as u64);
    }

    fn compile_expr_stmt(&mut self, stmt: &mut ExprNode, e: &mut Emitter) {
        if let ExprKind::Binary { op: BinOp::Assign, .. } = &stmt.kind {
            self.compile_assignment(stmt, e);
            return;
        }
        self.compile_expr(stmt, e);
        let is_void = stmt.get_type().map(|t| t.is_void()).unwrap_or(false);
        if !is_void {
            e.emit0(Opcode::Pop);
        }
    }

    fn compile_assignment(&mut self, be: &mut ExprNode, e: &mut Emitter) {
        let ExprKind::Binary { left, right, .. } = &mut be.kind else { unreachable!() };
        match &mut left.kind {
            ExprKind::Ident(name) => {
                let name = name.clone();
                self.compile_ident_assignment(&name, right, e);
            }
            ExprKind::Index { .. } => self.compile_index_assignment(left, right, e),
            ExprKind::Member { .. } => self.compile_field_assignment(left, right, e),
            _ => {}
        }
    }

    fn compile_field_assignment(&mut self, member: &mut ExprNode, value: &mut ExprNode, e: &mut Emitter) {
        let ExprKind::Member { object, member: mname } = &mut member.kind else { unreachable!() };
        let obj_type = match object.get_type() {
            Some(t) if t.kind == Kind::Struct => t,
            _ => return,
        };

        let mut field_idx: i64 = -1;
        for (i, field) in obj_type.struct_fields.iter().enumerate() {
            if field.name == *mname {
                field_idx = i as i64;
                break;
            }
        }
        if field_idx < 0 {
            return;
        }

        self.compile_expr(object, e);
        self.emit_null_check(object.get_type().as_ref(), e);
        self.compile_expr(value, e);
        e.emit1(Opcode::FieldStore, field_idx as u64);
    }

    fn compile_ident_assignment(&mut self, name: &str, value: &mut ExprNode, e: &mut Emitter) {
        let sym = match self.scope.resolve(name) {
            Some(s) => s,
            None => return,
        };

        if sym.is_struct_field {
            e.emit1(Opcode::LoadLocal, sym.field_of_slot as u64);
            self.compile_expr(value, e);
            e.emit1(Opcode::FieldStore, sym.field_index as u64);
            return;
        }

        self.compile_expr(value, e);

        if let Some(sym_ty) = &sym.ty {
            if sym_ty.kind == Kind::Trait {
                if let Some(vt) = value.get_type() {
                    if vt.kind == Kind::Struct {
                        self.emit_trait_new(&vt, sym_ty, e);
                    }
                }
            }
            self.emit_type_check(value.get_type().as_ref(), sym_ty, e);
        }

        e.emit1(Opcode::StoreLocal, sym.slot as u64);
    }

    fn compile_index_assignment(&mut self, index_expr: &mut ExprNode, value: &mut ExprNode, e: &mut Emitter) {
        let ExprKind::Index { target, index } = &mut index_expr.kind else { unreachable!() };
        let target_type = target.get_type();

        self.compile_expr(target, e);
        self.emit_null_check(target.get_type().as_ref(), e);
        self.compile_expr(index, e);
        self.compile_expr(value, e);

        if let Some(tt) = &target_type {
            if tt.kind == Kind::Map {
                e.emit0(Opcode::MapSet);
                e.emit0(Opcode::Pop);
                return;
            }
        }
        e.emit0(Opcode::ListSet);
    }

    fn compile_if_stmt(&mut self, stmt: &mut IfStmt, e: &mut Emitter, is_else_if: bool) {
        if !is_else_if {
            self.compile_expr(&mut stmt.condition, e);
        }

        let else_jump = e.emit_jump(Opcode::JumpIfFalse);
        self.compile_block(&mut stmt.then_block, e);

        if !stmt.else_ifs.is_empty() || stmt.else_block.is_some() {
            let end_jump = e.emit_jump(Opcode::Jump);
            e.patch_jump(else_jump);

            for ei in &mut stmt.else_ifs {
                self.compile_if_stmt(ei, e, false);
            }
            if let Some(eb) = &mut stmt.else_block {
                self.compile_block(eb, e);
            }
            e.patch_jump(end_jump);
        } else {
            e.patch_jump(else_jump);
        }
    }

    fn compile_while_stmt(&mut self, stmt: &mut WhileStmt, e: &mut Emitter) {
        let loop_start = e.current_offset();

        self.compile_expr(&mut stmt.condition, e);
        let exit_jump = e.emit_jump(Opcode::JumpIfFalse);

        let old_loops_len = self.loops.len();
        self.loops.push(LoopInfo {
            start_offset: loop_start,
            break_jumps: Vec::new(),
        });

        self.compile_block(&mut stmt.body, e);

        let back_jump = e.emit_jump(Opcode::Jump);
        if back_jump < e.pending_jumps.len() {
            e.pending_jumps[back_jump].target = loop_start;
            e.pending_jumps[back_jump].pending = false;
        }

        let exit_offset = e.current_offset();
        e.patch_jump(exit_jump);

        if self.loops.len() > old_loops_len {
            let break_jumps = std::mem::take(&mut self.loops[old_loops_len].break_jumps);
            for jump_idx in break_jumps {
                if jump_idx < e.pending_jumps.len() {
                    e.pending_jumps[jump_idx].target = exit_offset;
                    e.pending_jumps[jump_idx].pending = false;
                }
            }
            self.loops.truncate(old_loops_len);
        }
    }

    fn compile_for_stmt(&mut self, stmt: &mut ForStmt, e: &mut Emitter) {
        let iter_type = stmt.iterable.get_type();
        let is_map = iter_type.as_ref().map(|t| t.kind == Kind::Map).unwrap_or(false);

        self.compile_expr(&mut stmt.iterable, e);
        self.emit_null_check(iter_type.as_ref(), e);

        let iter_slot = self.allocate_slot(iter_type.as_ref().map(|t| t.is_reference_type()).unwrap_or(false));
        let index_slot = self.allocate_slot(false);
        let mut keys_slot: i64 = -1;

        e.emit1(Opcode::StoreLocal, iter_slot as u64);

        if is_map {
            keys_slot = self.allocate_slot(true) as i64;
            e.emit1(Opcode::LoadLocal, iter_slot as u64);
            e.emit0(Opcode::MapKeys);
            e.emit1(Opcode::StoreLocal, keys_slot as u64);
        }

        e.emit1(Opcode::ConstInt, 0);
        e.emit1(Opcode::StoreLocal, index_slot as u64);

        let loop_start = e.current_offset();

        e.emit1(Opcode::LoadLocal, index_slot as u64);
        if is_map {
            e.emit1(Opcode::LoadLocal, keys_slot as u64);
            e.emit0(Opcode::ListLength);
        } else {
            e.emit1(Opcode::LoadLocal, iter_slot as u64);
            e.emit0(Opcode::ListLength);
        }
        e.emit0(Opcode::LtInt);
        let exit_jump = e.emit_jump(Opcode::JumpIfFalse);

        if is_map {
            e.emit1(Opcode::LoadLocal, keys_slot as u64);
            e.emit1(Opcode::LoadLocal, index_slot as u64);
            e.emit0(Opcode::ListGet);
        } else {
            e.emit1(Opcode::LoadLocal, iter_slot as u64);
            e.emit1(Opcode::LoadLocal, index_slot as u64);
            e.emit0(Opcode::ListGet);
        }

        let old_scope = self.scope.clone();
        let ft = self.scope.func_type.clone();
        self.scope = Scope::new(Some(old_scope.clone()), ft);
        self.scope_slot_refs.push(Vec::new());

        let mut elem_type: Option<Rc<Type>> = None;
        if let Some(it) = &iter_type {
            if it.kind == Kind::List {
                elem_type = it.element.clone();
            } else if it.is_string() {
                elem_type = Some(types::t_char());
            } else if it.kind == Kind::Map {
                elem_type = it.key_type.clone();
            }
        }
        let elem_type = elem_type.unwrap_or_else(types::t_invalid);

        let loop_var_slot = self.allocate_slot(elem_type.is_reference_type());
        let sym = Rc::new(Symbol {
            name: stmt.variable.clone(),
            kind: SymbolKind::Variable,
            ty: Some(elem_type),
            slot: loop_var_slot as i32,
            defined: Cell::new(true),
            ..base_symbol()
        });
        self.scope.declare(sym);
        e.emit1(Opcode::StoreLocal, loop_var_slot as u64);

        if is_map && !stmt.value_variable.is_empty() {
            // Two-variable map unpacking: the key has already been stored in
            // loop_var_slot, so reload it and use it to look up the value.
            e.emit1(Opcode::LoadLocal, iter_slot as u64);
            e.emit1(Opcode::LoadLocal, loop_var_slot as u64);
            e.emit0(Opcode::MapGet);

            let val_type = iter_type
                .as_ref()
                .and_then(|t| t.value_type.clone())
                .unwrap_or_else(types::t_invalid);
            let val_var_slot = self.allocate_slot(val_type.is_reference_type());
            let sym = Rc::new(Symbol {
                name: stmt.value_variable.clone(),
                kind: SymbolKind::Variable,
                ty: Some(val_type),
                slot: val_var_slot as i32,
                defined: Cell::new(true),
                ..base_symbol()
            });
            self.scope.declare(sym);
            e.emit1(Opcode::StoreLocal, val_var_slot as u64);
        }

        let old_loops_len = self.loops.len();
        self.loops.push(LoopInfo {
            start_offset: loop_start,
            break_jumps: Vec::new(),
        });

        self.compile_block(&mut stmt.body, e);

        e.emit1(Opcode::LoadLocal, index_slot as u64);
        e.emit1(Opcode::ConstInt, 1);
        e.emit0(Opcode::AddInt);
        e.emit1(Opcode::StoreLocal, index_slot as u64);

        let back_jump = e.emit_jump(Opcode::Jump);
        if back_jump < e.pending_jumps.len() {
            e.pending_jumps[back_jump].target = loop_start;
            e.pending_jumps[back_jump].pending = false;
        }

        let exit_offset = e.current_offset();
        e.patch_jump(exit_jump);

        if self.loops.len() > old_loops_len {
            let break_jumps = std::mem::take(&mut self.loops[old_loops_len].break_jumps);
            for jump_idx in break_jumps {
                if jump_idx < e.pending_jumps.len() {
                    e.pending_jumps[jump_idx].target = exit_offset;
                    e.pending_jumps[jump_idx].pending = false;
                }
            }
            self.loops.truncate(old_loops_len);
        }

        self.exit_scope(e);
        self.scope = old_scope;

        if iter_type.as_ref().map(|t| t.is_reference_type()).unwrap_or(false) {
            e.emit0(Opcode::ConstNull);
            e.emit1(Opcode::StoreLocal, iter_slot as u64);
        }
        if keys_slot >= 0 {
            e.emit0(Opcode::ConstNull);
            e.emit1(Opcode::StoreLocal, keys_slot as u64);
        }
    }

    fn compile_switch_stmt(&mut self, stmt: &mut SwitchStmt, e: &mut Emitter) {
        let switch_expr_type = stmt.expression.get_type();
        let slot =
            self.allocate_slot(switch_expr_type.as_ref().map(|t| t.is_reference_type()).unwrap_or(false));
        self.compile_expr(&mut stmt.expression, e);
        e.emit1(Opcode::StoreLocal, slot as u64);

        let mut end_jumps: Vec<usize> = Vec::new();

        let case_count = stmt.cases.len();
        for i in 0..case_count {
            e.emit1(Opcode::LoadLocal, slot as u64);
            e.emit0(Opcode::Dup);
            let case_expr_type = {
                let cse = &mut stmt.cases[i];
                self.compile_expr(&mut cse.expression, e);
                cse.expression.get_type()
            };
            let op = eq_opcode_for(switch_expr_type.as_ref(), case_expr_type.as_ref());
            e.emit0(op);
            let cj = e.emit_jump(Opcode::JumpIfFalse);
            e.emit0(Opcode::Pop);
            {
                let cse = &mut stmt.cases[i];
                self.compile_block(&mut cse.body, e);
            }
            let ej = e.emit_jump(Opcode::Jump);
            end_jumps.push(ej);
            e.patch_jump(cj);
            e.emit0(Opcode::Pop);
        }

        if let Some(db) = &mut stmt.default_block {
            self.compile_block(db, e);
        }

        for jmp in end_jumps {
            e.patch_jump(jmp);
        }

        if switch_expr_type.as_ref().map(|t| t.is_reference_type()).unwrap_or(false) {
            e.emit0(Opcode::ConstNull);
            e.emit1(Opcode::StoreLocal, slot as u64);
        }
    }

    fn compile_try_stmt(&mut self, stmt: &mut TryStmt, e: &mut Emitter) {
        let stack_depth = e.curr_stack.max(0) as usize;
        let has_catch = stmt.catch.is_some();
        let has_finally = stmt.finally_block.is_some();

        let handler_setup_offset = e.code.len();
        e.code.push(Opcode::SetupHandler as u8);
        e.code.extend_from_slice(&[0, 0, 0, 0]);
        e.code.extend_from_slice(&[0, 0, 0, 0]);
        e.code.extend_from_slice(&[0, 0]);
        e.offsets.push(handler_setup_offset);

        let handler_idx = e.handler_info.len();
        e.handler_info.push(HandlerMeta {
            setup_offset: handler_setup_offset,
            stack_depth,
            catch_offset: 0,
            finally_offset: 0,
        });

        let old_try_stack = std::mem::take(&mut self.trys);
        if has_finally {
            let finally_ptr = stmt.finally_block.as_mut().unwrap() as *mut Block;
            self.trys.push(TryFrame {
                has_finally: true,
                finally_body: finally_ptr,
            });
        } else {
            self.trys.push(TryFrame {
                has_finally: false,
                finally_body: std::ptr::null_mut(),
            });
        }

        self.compile_block(&mut stmt.try_body, e);
        self.trys.pop();

        let mut skip_catch_jump: i64 = -1;
        if has_catch {
            skip_catch_jump = e.emit_jump(Opcode::Jump) as i64;
        }

        if has_catch {
            let catch_offset = e.current_offset();
            e.handler_info[handler_idx].catch_offset = catch_offset;

            if has_finally {
                let finally_ptr = stmt.finally_block.as_mut().unwrap() as *mut Block;
                self.trys.push(TryFrame {
                    has_finally: true,
                    finally_body: finally_ptr,
                });
            }

            let param_name = stmt.catch.as_ref().unwrap().param_name.clone();

            let old_scope = self.scope.clone();
            let ft = self.scope.func_type.clone();
            self.scope = Scope::new(Some(old_scope.clone()), ft);
            self.scope_slot_refs.push(Vec::new());

            let param_slot = self.allocate_slot(true);
            let sym = Rc::new(Symbol {
                name: param_name,
                kind: SymbolKind::Variable,
                ty: Some(types::t_exception()),
                slot: param_slot as i32,
                defined: Cell::new(true),
                ..base_symbol()
            });
            self.scope.declare(sym);

            e.emit1(Opcode::StoreLocal, param_slot as u64);

            if let Some(catch) = &mut stmt.catch {
                self.compile_block(&mut catch.body, e);
            }

            self.exit_scope(e);
            self.scope = old_scope;

            if has_finally {
                self.trys.pop();
            }
        }

        if has_finally {
            if skip_catch_jump >= 0 {
                e.patch_jump(skip_catch_jump as usize);
            }
            let finally_offset = e.current_offset();
            e.handler_info[handler_idx].finally_offset = finally_offset;
            if let Some(fb) = &mut stmt.finally_block {
                self.compile_block(fb, e);
            }
            e.emit0(Opcode::RemoveHandler);
        } else {
            if skip_catch_jump >= 0 {
                e.patch_jump(skip_catch_jump as usize);
            }
            e.emit0(Opcode::RemoveHandler);
        }

        self.trys = old_try_stack;
        e.finalize_handler(handler_idx);
    }

    fn compile_throw_stmt(&mut self, stmt: &mut ExprNode, e: &mut Emitter) {
        self.compile_expr(stmt, e);
        if let Some(vt) = stmt.get_type() {
            if vt.is_string() {
                e.emit0(Opcode::NewException);
            }
        }
        e.emit0(Opcode::Throw);
    }

    fn emit_null_check(&self, t: Option<&Rc<Type>>, e: &mut Emitter) {
        if let Some(t) = t {
            if t.is_nullable() {
                e.emit0(Opcode::CheckNotNull);
            }
        }
    }

    fn emit_type_check(&self, value_type: Option<&Rc<Type>>, target_type: &Rc<Type>, e: &mut Emitter) {
        let vt = match value_type {
            Some(t) => t,
            None => return,
        };
        if vt.kind != Kind::Any {
            return;
        }
        let tag = match type_tag_for(target_type) {
            Some(t) => t,
            None => return,
        };
        let nullable = if target_type.is_nullable() { 1u64 } else { 0u64 };
        let idx = e.add_string(&tag);
        e.emit2(Opcode::CheckType, idx as u64, nullable);
    }

    fn emit_widening_conversion(&self, from: &Rc<Type>, to: &Rc<Type>, e: &mut Emitter) {
        if !from.is_valid() || !to.is_valid() {
            return;
        }
        if to.kind == Kind::Float && from.is_numeric() && from.kind != Kind::Float {
            e.emit0(Opcode::ConvertIntToFloat);
        }
    }

    fn emit_trait_new(&self, struct_type: &Rc<Type>, trait_type: &Rc<Type>, e: &mut Emitter) {
        let trait_idx = e.add_string(&trait_type.trait_name);
        let struct_idx = e.add_string(&struct_type.struct_name);
        e.emit2(Opcode::TraitNew, trait_idx as u64, struct_idx as u64);
    }

    fn emit_arg_trait_wrap(&self, arg_type: Option<&Rc<Type>>, param_type: Option<&Rc<Type>>, e: &mut Emitter) {
        let pt = match param_type {
            Some(p) if p.kind == Kind::Trait => p.clone(),
            _ => return,
        };
        if let Some(at) = arg_type {
            if at.kind == Kind::Struct {
                self.emit_trait_new(at, &pt, e);
            }
        }
    }

    fn compile_return_stmt(&mut self, values: &mut [ExprNode], e: &mut Emitter) {
        let val_count = values.len();
        let current_ret_is_exception = self
            .current_ret_type
            .as_ref()
            .map(|t| t.is_exception())
            .unwrap_or(false);

        let has_finally = self.trys.iter().any(|t| t.has_finally);

        if !has_finally || self.inlining_finally {
            match val_count {
                0 => {
                    e.emit0(Opcode::ReturnVoid);
                }
                1 => {
                    self.compile_expr(&mut values[0], e);
                    if let Some(rt) = &self.current_ret_type {
                        self.emit_type_check(values[0].get_type().as_ref(), rt, e);
                    }
                    if let (Some(vt), Some(rt)) = (values[0].get_type(), self.current_ret_type.clone()) {
                        self.emit_widening_conversion(&vt, &rt, e);
                    }
                    if val_count == 1 && current_ret_is_exception {
                        if let Some(vt) = values[0].get_type() {
                            if vt.is_string() {
                                e.emit0(Opcode::NewException);
                            }
                        }
                    }
                    e.emit0(Opcode::Return);
                }
                _ => {
                    for val in values.iter_mut() {
                        self.compile_expr(val, e);
                    }
                    e.emit1(Opcode::ReturnMulti, val_count as u64);
                }
            }
            return;
        }

        // Return inside try-finally: push values first, inline finallys, return.
        for val in values.iter_mut() {
            self.compile_expr(val, e);
            if let Some(rt) = &self.current_ret_type {
                self.emit_type_check(val.get_type().as_ref(), rt, e);
            }
        }
        if val_count == 1 && current_ret_is_exception {
            if let Some(vt) = values[0].get_type() {
                if vt.is_string() {
                    e.emit0(Opcode::NewException);
                }
            }
        }
        if val_count == 0 {
            e.emit0(Opcode::ConstNull);
        }

        self.inline_finally_bodies(e);

        if val_count == 0 {
            e.emit0(Opcode::ReturnVoid);
        } else if val_count == 1 {
            e.emit0(Opcode::Return);
        } else {
            e.emit1(Opcode::ReturnMulti, val_count as u64);
        }
    }

    fn compile_break_stmt(&mut self, e: &mut Emitter) {
        if self.loops.is_empty() {
            return;
        }

        if !self.inlining_finally && self.trys.iter().any(|t| t.has_finally) {
            self.inline_finally_bodies(e);
        }

        let jump_idx = e.emit_jump(Opcode::Jump);
        let n = self.loops.len();
        self.loops[n - 1].break_jumps.push(jump_idx);
    }

    fn compile_continue_stmt(&mut self, e: &mut Emitter) {
        if self.loops.is_empty() {
            return;
        }

        if !self.inlining_finally && self.trys.iter().any(|t| t.has_finally) {
            self.inline_finally_bodies(e);
        }

        let loop_start = self.loops[self.loops.len() - 1].start_offset;
        let jump_idx = e.emit_jump(Opcode::Jump);
        if jump_idx < e.pending_jumps.len() {
            e.pending_jumps[jump_idx].target = loop_start;
            e.pending_jumps[jump_idx].pending = false;
        }
    }

    /// Inlines the finally bodies of all enclosing try frames (innermost first).
    ///
    /// # Safety contract
    /// The raw pointers stored in `self.trys` point into the function body
    /// local owned by `compile_function`, which does not move during
    /// compilation. Each finally block is disjoint from the statement tree
    /// currently being compiled.
    fn inline_finally_bodies(&mut self, e: &mut Emitter) {
        self.inlining_finally = true;
        let frames: Vec<*mut Block> = self
            .trys
            .iter()
            .rev()
            .filter(|t| t.has_finally)
            .map(|t| t.finally_body)
            .collect();
        for fb in frames {
            unsafe {
                self.compile_block(&mut *fb, e);
            }
        }
        self.inlining_finally = false;
    }

    fn compile_expr(&mut self, expr: &mut ExprNode, e: &mut Emitter) {
        match &mut expr.kind {
            ExprKind::Int(v) => {
                e.emit1(Opcode::ConstInt, *v as u64);
            }
            ExprKind::Float(v) => {
                e.emit1(Opcode::ConstFloat, v.to_bits());
            }
            ExprKind::Bool(v) => {
                e.emit1(Opcode::ConstBool, if *v { 1 } else { 0 });
            }
            ExprKind::Char(v) => {
                e.emit1(Opcode::ConstChar, *v as u64);
            }
            ExprKind::Str(v) => {
                let idx = e.add_string(v);
                e.emit1(Opcode::ConstString, idx as u64);
            }
            ExprKind::Byte(v) => {
                e.emit1(Opcode::ConstByte, *v as u64);
            }
            ExprKind::Null => {
                e.emit0(Opcode::ConstNull);
            }
            ExprKind::Ident(name) => {
                let name = name.clone();
                self.compile_identifier(&name, e);
            }
            ExprKind::Unary { .. } => self.compile_unary(expr, e),
            ExprKind::Binary { .. } => self.compile_binary(expr, e),
            ExprKind::Call { .. } => self.compile_call(expr, e),
            ExprKind::Index { .. } => self.compile_index(expr, e),
            ExprKind::List(_) => self.compile_list_literal(expr, e),
            ExprKind::Map { .. } => self.compile_map_literal(expr, e),
            ExprKind::StructLit { .. } => self.compile_struct_literal(expr, e),
            ExprKind::Member { .. } => self.compile_member_expr(expr, e),
            ExprKind::NullCoalescing { .. } => self.compile_null_coalescing(expr, e),
            ExprKind::Spread(inner) => self.compile_expr(inner, e),
        }
    }

    fn compile_identifier(&mut self, name: &str, e: &mut Emitter) {
        if let Some(sym) = self.scope.resolve(name) {
            if sym.is_struct_field {
                e.emit1(Opcode::LoadLocal, sym.field_of_slot as u64);
                e.emit1(Opcode::FieldLoad, sym.field_index as u64);
                return;
            }
            e.emit1(Opcode::LoadLocal, sym.slot as u64);
        }
        // Function references are only used in calls
    }

    fn compile_unary(&mut self, expr: &mut ExprNode, e: &mut Emitter) {
        let ExprKind::Unary { op, operand } = &mut expr.kind else { unreachable!() };
        self.compile_expr(operand, e);
        self.emit_null_check(operand.get_type().as_ref(), e);

        let operand_type = operand.get_type();

        match op {
            UnaryOp::Negate => {
                if operand_type.as_ref().map(|t| t.kind == Kind::Float).unwrap_or(false) {
                    e.emit0(Opcode::NegFloat);
                } else {
                    e.emit0(Opcode::NegInt);
                }
            }
            UnaryOp::Not => {
                e.emit0(Opcode::NotBool);
            }
            UnaryOp::BitNot => {
                e.emit0(Opcode::BitNotInt);
            }
        }
    }

    fn compile_binary(&mut self, expr: &mut ExprNode, e: &mut Emitter) {
        let ExprKind::Binary { op, left, right } = &mut expr.kind else { unreachable!() };

        if *op == BinOp::And {
            self.compile_and(left, right, e);
            return;
        }
        if *op == BinOp::Or {
            self.compile_or(left, right, e);
            return;
        }

        self.compile_expr(left, e);
        let null_safe = matches!(op, BinOp::Eq | BinOp::Ne);
        if !null_safe {
            self.emit_null_check(left.get_type().as_ref(), e);
        }
        self.compile_expr(right, e);
        if !null_safe {
            self.emit_null_check(right.get_type().as_ref(), e);
        }

        let left_type = left.get_type();
        let right_type = right.get_type();

        let common_type: Option<Rc<Type>> = match (&left_type, &right_type) {
            (Some(l), Some(r)) if l.is_valid() && r.is_valid() => {
                if l.is_numeric() && r.is_numeric() {
                    Some(types::common_numeric_type(l, r))
                } else {
                    None
                }
            }
            _ => None,
        };
        let ct_ref = common_type.as_ref();

        let l_is_null = left_type.as_ref().map(|t| t.is_null()).unwrap_or(false);
        let r_is_null = right_type.as_ref().map(|t| t.is_null()).unwrap_or(false);
        let l_is_string = left_type.as_ref().map(|t| t.is_string()).unwrap_or(false);
        let r_is_string = right_type.as_ref().map(|t| t.is_string()).unwrap_or(false);
        let l_is_bool = left_type.as_ref().map(|t| t.is_bool()).unwrap_or(false);
        let r_is_bool = right_type.as_ref().map(|t| t.is_bool()).unwrap_or(false);

        match op {
            BinOp::Add => emit_arith(e, ct_ref, Opcode::AddFloat, Opcode::AddInt),
            BinOp::Sub => emit_arith(e, ct_ref, Opcode::SubFloat, Opcode::SubInt),
            BinOp::Mul => emit_arith(e, ct_ref, Opcode::MulFloat, Opcode::MulInt),
            BinOp::Div => emit_arith(e, ct_ref, Opcode::DivFloat, Opcode::DivInt),
            BinOp::Mod => emit_arith(e, ct_ref, Opcode::RemFloat, Opcode::RemInt),
            BinOp::Eq | BinOp::Ne => {
                if l_is_null || r_is_null {
                    e.emit0(Opcode::EqRef);
                } else if l_is_string && r_is_string {
                    e.emit0(Opcode::EqString);
                } else if l_is_bool && r_is_bool {
                    e.emit0(Opcode::EqBool);
                } else if let Some(ct) = ct_ref.filter(|c| c.is_valid()) {
                    if ct.kind == Kind::Float {
                        e.emit0(Opcode::EqFloat);
                    } else {
                        e.emit0(Opcode::EqInt);
                    }
                } else {
                    e.emit0(Opcode::EqRef);
                }
                if *op == BinOp::Ne {
                    e.emit0(Opcode::NotBool);
                }
            }
            BinOp::Lt => emit_arith(e, ct_ref, Opcode::LtFloat, Opcode::LtInt),
            BinOp::Le => emit_arith(e, ct_ref, Opcode::LeFloat, Opcode::LeInt),
            BinOp::Gt => emit_arith(e, ct_ref, Opcode::GtFloat, Opcode::GtInt),
            BinOp::Ge => emit_arith(e, ct_ref, Opcode::GeFloat, Opcode::GeInt),
            BinOp::BitAnd => { e.emit0(Opcode::BitAndInt); }
            BinOp::BitOr => { e.emit0(Opcode::BitOrInt); }
            BinOp::BitXor => { e.emit0(Opcode::BitXorInt); }
            BinOp::ShiftLeft => { e.emit0(Opcode::ShiftLeftInt); }
            BinOp::ShiftRight => { e.emit0(Opcode::ShiftRightInt); }
            BinOp::StrConcat => { e.emit0(Opcode::ConcatString); }
            BinOp::Assign => {}
            BinOp::And | BinOp::Or => {}
        }
    }

    fn compile_and(&mut self, left: &mut ExprNode, right: &mut ExprNode, e: &mut Emitter) {
        self.compile_expr(left, e);
        self.emit_null_check(left.get_type().as_ref(), e);
        let false_jump = e.emit_jump(Opcode::JumpIfFalse);
        self.compile_expr(right, e);
        self.emit_null_check(right.get_type().as_ref(), e);
        let end_jump = e.emit_jump(Opcode::Jump);
        e.patch_jump(false_jump);
        e.emit1(Opcode::ConstBool, 0);
        e.patch_jump(end_jump);
    }

    fn compile_or(&mut self, left: &mut ExprNode, right: &mut ExprNode, e: &mut Emitter) {
        self.compile_expr(left, e);
        self.emit_null_check(left.get_type().as_ref(), e);
        let true_jump = e.emit_jump(Opcode::JumpIfTrue);
        self.compile_expr(right, e);
        self.emit_null_check(right.get_type().as_ref(), e);
        let end_jump = e.emit_jump(Opcode::Jump);
        e.patch_jump(true_jump);
        e.emit1(Opcode::ConstBool, 1);
        e.patch_jump(end_jump);
    }

    fn compile_call(&mut self, expr: &mut ExprNode, e: &mut Emitter) {
        // stack() constructor
        if let ExprKind::Call { function, .. } = &expr.kind {
            if let ExprKind::Ident(fname) = &function.kind {
                if fname == "stack" {
                    let et = expr.get_type();
                    if et.as_ref().map(|t| t.kind == Kind::Stack).unwrap_or(false) {
                        e.emit0(Opcode::NewStack);
                        return;
                    }
                }
            }
        }

        if let ExprKind::Call { function, args } = &mut expr.kind {
            if let ExprKind::Member { object, member } = &mut function.kind {
                let obj_type = object.get_type();

                if let Some(ot) = &obj_type {
                    // Struct method calls
                    if ot.kind == Kind::Struct {
                        let has_method = ot.struct_methods.borrow().contains_key(member.as_str());
                        if has_method {
                            let full = format!("{}.{}", ot.struct_name, member);
                            if let Some(fn_idx) = self.func_map.get(&full).copied() {
                                self.compile_expr(object, e);
                                self.emit_null_check(obj_type.as_ref(), e);
                                for arg in args.iter_mut() {
                                    self.compile_expr(arg, e);
                                }
                                e.emit2(Opcode::Call, fn_idx as u64, (args.len() + 1) as u64);
                                return;
                            }
                        }
                    }

                    // Trait method calls
                    if ot.kind == Kind::Trait {
                        self.compile_expr(object, e);
                        self.emit_null_check(obj_type.as_ref(), e);
                        for arg in args.iter_mut() {
                            self.compile_expr(arg, e);
                        }
                        let method_slot = self
                            .trait_method_slots
                            .get(&ot.trait_name)
                            .and_then(|slots| slots.get(member.as_str()).copied())
                            .unwrap_or(0);
                        e.emit2(Opcode::TraitInvoke, method_slot as u64, args.len() as u64);
                        return;
                    }

                    // Builtin type method calls
                    let type_name = builtin_type_name(ot);
                    if !type_name.is_empty() {
                        let full = format!("{}.{}", type_name, member);
                        if let Some(native_idx) = self.native_map.get(&full).copied() {
                            self.compile_expr(object, e);
                            self.emit_null_check(obj_type.as_ref(), e);
                            for arg in args.iter_mut() {
                                self.compile_expr(arg, e);
                            }
                            e.emit2(Opcode::CallNative, native_idx as u64, (args.len() + 1) as u64);
                            return;
                        }
                    }
                }

                // Module-qualified calls: module.function(...)
                if let ExprKind::Ident(iname) = &object.kind {
                    let full = format!("{}.{}", iname, member);
                    if let Some(native_idx) = self.native_map.get(&full).copied() {
                        for arg in args.iter_mut() {
                            self.compile_expr(arg, e);
                        }
                        e.emit2(Opcode::CallNative, native_idx as u64, args.len() as u64);
                        return;
                    }
                    if let Some(fn_idx) = self.func_map.get(&full).copied() {
                        self.compile_variadic_call(fn_idx, args, e);
                        return;
                    }
                }
            }
        }

        if let ExprKind::Call { function, args } = &mut expr.kind {
            if let ExprKind::Ident(iname) = &function.kind {
                // Unqualified method call inside a struct method
                if let Some(cs) = self.current_struct.clone() {
                    let mi = cs.struct_methods.borrow().get(iname.as_str()).cloned();
                    if let Some(mi) = mi {
                        let mut fn_idx = mi.func_index as usize;
                        let full = format!("{}.{}", cs.struct_name, iname);
                        if let Some(idx) = self.func_map.get(&full).copied() {
                            fn_idx = idx;
                        }
                        e.emit1(Opcode::LoadLocal, 0);
                        for arg in args.iter_mut() {
                            self.compile_expr(arg, e);
                        }
                        e.emit2(Opcode::Call, fn_idx as u64, (args.len() + 1) as u64);
                        return;
                    }
                }

                // Native functions (unqualified)
                let native_key = format!("core.{}", iname);
                if let Some(native_idx) = self.native_map.get(&native_key).copied() {
                    for arg in args.iter_mut() {
                        self.compile_expr(arg, e);
                    }
                    e.emit2(Opcode::CallNative, native_idx as u64, args.len() as u64);
                    return;
                }

                // User functions (unqualified, scoped to current module)
                if let Some(fn_idx) = self.func_map.get(iname.as_str()).copied() {
                    let target_module = self.func_modules.get(&fn_idx).cloned().unwrap_or_default();
                    if target_module.is_empty() || target_module == self.current_module {
                        self.compile_variadic_call(fn_idx, args, e);
                        return;
                    }
                }
            }
        }

        // Generic call
        if let ExprKind::Call { function, args } = &mut expr.kind {
            self.compile_expr(function, e);
            for arg in args.iter_mut() {
                self.compile_expr(arg, e);
            }
        }
    }

    fn compile_variadic_call(&mut self, fn_idx: usize, args: &mut [ExprNode], e: &mut Emitter) {
        let (is_variadic, fixed_count, param_types) = self
            .func_call_info
            .get(&fn_idx)
            .cloned()
            .unwrap_or((false, 0, Vec::new()));

        if !is_variadic {
            for (i, arg) in args.iter_mut().enumerate() {
                self.compile_expr(arg, e);
                if let Some(pt) = param_types.get(i) {
                    self.emit_arg_trait_wrap(arg.get_type().as_ref(), Some(pt), e);
                    self.emit_type_check(arg.get_type().as_ref(), pt, e);
                }
            }
            e.emit2(Opcode::Call, fn_idx as u64, args.len() as u64);
            return;
        }

        for i in 0..fixed_count.min(args.len()) {
            self.compile_expr(&mut args[i], e);
            if let Some(pt) = param_types.get(i) {
                self.emit_arg_trait_wrap(args[i].get_type().as_ref(), Some(pt), e);
                self.emit_type_check(args[i].get_type().as_ref(), pt, e);
            }
        }

        let variadic_count = args.len().saturating_sub(fixed_count);
        if variadic_count > 0 {
            e.emit1(Opcode::NewList, variadic_count as u64);
            for i in fixed_count..args.len() {
                if let ExprKind::Spread(inner) = &mut args[i].kind {
                    self.compile_expr(inner, e);
                    e.emit0(Opcode::ListExtend);
                } else {
                    self.compile_expr(&mut args[i], e);
                    if let Some(pt) = param_types.get(fixed_count) {
                        self.emit_type_check(args[i].get_type().as_ref(), pt, e);
                    }
                    e.emit0(Opcode::ListAppend);
                }
            }
        } else {
            e.emit1(Opcode::NewList, 0);
        }

        e.emit2(Opcode::Call, fn_idx as u64, (fixed_count + 1) as u64);
    }

    fn compile_index(&mut self, expr: &mut ExprNode, e: &mut Emitter) {
        let ExprKind::Index { target, index } = &mut expr.kind else { unreachable!() };
        self.compile_expr(target, e);
        self.emit_null_check(target.get_type().as_ref(), e);
        self.compile_expr(index, e);

        let target_type = target.get_type();
        if let Some(tt) = &target_type {
            if tt.kind == Kind::Map {
                e.emit0(Opcode::MapGet);
                return;
            }
        }
        e.emit0(Opcode::ListGet);
    }

    fn compile_list_literal(&mut self, expr: &mut ExprNode, e: &mut Emitter) {
        let list_type = expr.get_type();
        let ExprKind::List(elements) = &mut expr.kind else { unreachable!() };
        let elem_trait_type = list_type
            .as_ref()
            .and_then(|t| t.element.clone())
            .filter(|t| t.kind == Kind::Trait);

        e.emit1(Opcode::NewList, elements.len() as u64);
        let elem_type = list_type
            .as_ref()
            .filter(|t| t.kind == Kind::List)
            .and_then(|t| t.element.clone());
        for el in elements.iter_mut() {
            self.compile_expr(el, e);
            if let (Some(et_trait), Some(el_ty)) = (&elem_trait_type, el.get_type()) {
                if el_ty.kind == Kind::Struct {
                    self.emit_trait_new(&el_ty, et_trait, e);
                }
            }
            if let Some(et) = &elem_type {
                self.emit_type_check(el.get_type().as_ref(), et, e);
            }
            e.emit0(Opcode::ListAppend);
        }
    }

    fn compile_struct_literal(&mut self, expr: &mut ExprNode, e: &mut Emitter) {
        let expr_type = expr.get_type();
        let ExprKind::StructLit { fields, values, .. } = &mut expr.kind else { unreachable!() };
        let struct_type = match expr_type {
            Some(t) if t.kind == Kind::Struct => t,
            _ => return,
        };

        let mut field_map: HashMap<String, usize> = HashMap::new();
        for (i, f) in struct_type.struct_fields.iter().enumerate() {
            field_map.insert(f.name.clone(), i);
        }

        // Reorder named fields into declaration order
        let mut value_slots: Vec<Option<usize>> = vec![None; struct_type.struct_fields.len()];
        for (i, name) in fields.iter().enumerate() {
            if let Some(idx) = field_map.get(name) {
                value_slots[*idx] = Some(i);
            }
        }

        for (decl_idx, slot) in value_slots.iter().enumerate() {
            if let Some(vi) = slot {
                self.compile_expr(&mut values[*vi], e);
                let ft = struct_type.struct_fields[decl_idx].ty.clone();
                self.emit_type_check(values[*vi].get_type().as_ref(), &ft, e);
            } else {
                e.emit0(Opcode::ConstNull);
            }
        }

        let type_idx = e.add_string(&struct_type.struct_name);
        e.emit2(Opcode::StructNew, struct_type.struct_fields.len() as u64, type_idx as u64);
    }

    fn compile_map_literal(&mut self, expr: &mut ExprNode, e: &mut Emitter) {
        let mt = expr.get_type();
        let ExprKind::Map { keys, values } = &mut expr.kind else { unreachable!() };
        let (key_type, val_type) = match mt {
            Some(mt) if mt.kind == Kind::Map => (mt.key_type.clone(), mt.value_type.clone()),
            _ => (None, None),
        };

        e.emit0(Opcode::NewMap);
        for i in 0..keys.len() {
            e.emit0(Opcode::Dup);
            self.compile_expr(&mut keys[i], e);
            if let Some(kt) = &key_type {
                self.emit_type_check(keys[i].get_type().as_ref(), kt, e);
            }
            self.compile_expr(&mut values[i], e);
            if let Some(vt) = &val_type {
                self.emit_type_check(values[i].get_type().as_ref(), vt, e);
            }
            e.emit0(Opcode::MapSet);
            e.emit0(Opcode::Pop);
        }
    }

    fn compile_member_expr(&mut self, expr: &mut ExprNode, e: &mut Emitter) {
        // Enum variant reference
        let expr_type = expr.get_type();
        let ExprKind::Member { object, member } = &mut expr.kind else { unreachable!() };
        if let Some(et) = &expr_type {
            if et.kind == Kind::Enum && !et.enum_variant.is_empty() {
                if let Some(val) = types::enum_variant_value(et) {
                    e.emit1(Opcode::ConstInt, val as u64);
                    return;
                }
            }
        }

        self.compile_expr(object, e);
        self.emit_null_check(object.get_type().as_ref(), e);

        let obj_type = object.get_type();
        if let Some(ot) = &obj_type {
            if ot.is_exception() {
                match member.as_str() {
                    "message" => {
                        e.emit1(Opcode::ExceptionField, 0);
                        return;
                    }
                    "trace" => {
                        e.emit1(Opcode::ExceptionField, 1);
                        return;
                    }
                    "code" => {
                        e.emit1(Opcode::ExceptionField, 2);
                        return;
                    }
                    _ => {}
                }
            }

            if ot.kind == Kind::Struct {
                for (i, field) in ot.struct_fields.iter().enumerate() {
                    if field.name == *member {
                        e.emit1(Opcode::FieldLoad, i as u64);
                        return;
                    }
                }
            }
        }
        // Otherwise the member is a module function compiled by compile_call
    }

    fn compile_null_coalescing(&mut self, expr: &mut ExprNode, e: &mut Emitter) {
        let ExprKind::NullCoalescing { left, right } = &mut expr.kind else { unreachable!() };
        self.compile_expr(left, e);
        e.emit0(Opcode::Dup);
        let non_null_jump = e.emit_jump(Opcode::JumpIfNotNull);
        e.emit0(Opcode::Pop);
        self.compile_expr(right, e);
        let end_jump = e.emit_jump(Opcode::Jump);
        e.patch_jump(non_null_jump);
        e.patch_jump(end_jump);
    }

    fn allocate_slot(&mut self, is_ref_type: bool) -> usize {
        let slot = if let Some(s) = self.free_slots.pop() {
            s
        } else {
            let s = self.next_slot;
            self.next_slot += 1;
            s
        };
        if let Some(refs) = self.scope_slot_refs.last_mut() {
            refs.push(SlotRef {
                index: slot,
                is_ref_type,
            });
        }
        slot
    }

    fn exit_scope(&mut self, e: &mut Emitter) {
        let refs = match self.scope_slot_refs.pop() {
            Some(r) => r,
            None => return,
        };
        for sr in refs {
            if sr.is_ref_type {
                e.emit0(Opcode::ConstNull);
                e.emit1(Opcode::StoreLocal, sr.index as u64);
            }
            self.free_slots.push(sr.index);
        }
    }
}

fn emit_arith(e: &mut Emitter, common_type: Option<&Rc<Type>>, float_op: Opcode, int_op: Opcode) {
    if let Some(ct) = common_type.filter(|c| c.is_valid() && c.kind == Kind::Float) {
        let _ = ct;
        e.emit0(float_op);
    } else {
        e.emit0(int_op);
    }
}

fn type_tag_for(t: &Rc<Type>) -> Option<String> {
    match t.kind {
        Kind::Bool => Some("bool".to_string()),
        Kind::Byte => Some("byte".to_string()),
        Kind::Int | Kind::Enum => Some("int".to_string()),
        Kind::Float => Some("float".to_string()),
        Kind::Char => Some("char".to_string()),
        Kind::String => Some("string".to_string()),
        Kind::Exception => Some("exception".to_string()),
        Kind::List => Some("list".to_string()),
        Kind::Map => Some("map".to_string()),
        Kind::Stack => Some("stack".to_string()),
        Kind::Struct => Some(t.struct_name.to_lowercase()),
        _ => None,
    }
}

fn eq_opcode_for(switch_type: Option<&Rc<Type>>, case_type: Option<&Rc<Type>>) -> Opcode {
    if let (Some(st), Some(ct)) = (switch_type, case_type) {
        if st.is_valid()
            && ct.is_valid()
            && !st.is_nullable()
            && !ct.is_nullable()
            && st.is_numeric()
            && ct.is_numeric()
            && types::common_numeric_type(st, ct).kind == Kind::Float
        {
            return Opcode::EqFloat;
        }
        if let (Some(st), Some(ct)) = (switch_type, case_type) {
            if st.is_valid()
                && ct.is_valid()
                && !st.is_nullable()
                && !ct.is_nullable()
                && st.is_numeric()
                && ct.is_numeric()
            {
                return Opcode::EqInt;
            }
        }
    }
    Opcode::EqRef
}

fn builtin_type_name(t: &Rc<Type>) -> &'static str {
    match t.kind {
        Kind::Stack => "stack",
        Kind::String => "string",
        Kind::Map => "map",
        Kind::List => "list",
        _ => "",
    }
}
