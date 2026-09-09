//! Expression/statement type checking and IR emission.
//!
//! Every method body is checked exactly once. Generic declarations are
//! statically checked at call sites (constraints, argument compatibility) and
//! erased to `Object` inside method bodies (type erasure), matching the nominal
//! interface model.

//
// Data-structure fields retained for diagnostics/debugging/future use.
#![allow(dead_code)]
use crate::ast::*;
use crate::diagnostic::Diagnostics;
use crate::ir::{IrClass, IrFunction, IrInstr, IrInterface, IrModule, IrOp};
use crate::resolve::{MethodInfo, ParamInfo, ResolvedProgram};
use crate::source::SourceManager;
use crate::stdlib::builtins;
use crate::types::{BaseType, SubtypeOracle, Ty};
use std::collections::HashMap;

/// A compilable method template: a class method or an interface default.
#[derive(Clone, Copy, PartialEq, Eq, Hash, Debug)]
pub enum Template {
    Class(u32, usize),
    InterfaceDefault(u32, usize),
}

/// A resolved interface default method: template, params, return type.
pub(crate) type IfaceImpl = (Template, Vec<ParamInfo>, Option<Ty>);

impl Template {
    fn key(self) -> u64 {
        match self {
            Template::Class(c, i) => (c as u64) << 16 | i as u64,
            Template::InterfaceDefault(i, m) => 0x0001_0000_0000u64 | ((i as u64) << 16) | m as u64,
        }
    }
}

#[derive(Debug, Clone)]
pub struct LocalVar {
    name: String,
    ty: Ty,
    mutable: bool,
    /// Scope depth at declaration; removed when that scope ends.
    scope: usize,
}

/// Per-method compilation state (owned by the Checker while a body is checked).
#[derive(Debug)]
pub struct FnState {
    pub func: IrFunction,
    pub locals: Vec<LocalVar>,
    pub names: HashMap<String, usize>,
    /// Null-narrowing scopes: name -> narrowed type.
    pub narrowing: Vec<HashMap<String, Ty>>,
    /// Current lexical scope depth (for local lifetime).
    pub scope_depth: usize,
    /// Pending break/continue jump instruction indices per active loop.
    pub loops: Vec<(Vec<usize>, Vec<usize>)>,
    /// Current method return type (None = void).
    pub ret_ty: Option<Ty>,
    /// Names of the enclosing class/interface + method type parameters,
    /// in declaration order (for resolving type refs in bodies).
    pub type_param_names: Vec<String>,
    /// Line of the construct currently being checked (for source maps).
    pub cur_line: u32,
    /// Class whose method body is being checked (None for interface defaults).
    pub class_id: Option<u32>,
    /// True when the current method is static (no `self`).
    pub is_static: bool,
    /// Interface whose default body is being checked (self : Interface).
    pub iface_id: Option<u32>,
    /// Expected type for a literal being checked (list/map inference).
    pub expected_literal: Option<Ty>,
    /// Lexical context stack (innermost last) used to resolve which
    /// enclosing try regions a break/continue actually exits.
    pub ctx_stack: Vec<CtxEntry>,
}

/// One lexical level on the checker's context stack: the current unit
/// (statement or block) sits at index `idx` in a list of `len` items owned
/// by `owner`.
#[derive(Debug, Clone, Copy)]
pub struct CtxEntry {
    pub idx: usize,
    pub len: usize,
    pub owner: CtxOwner,
}

/// What kind of construct owns a statement list.
#[derive(Debug, Clone, Copy, PartialEq)]
pub enum CtxOwner {
    /// Function/method body.
    Function,
    /// while / for-in body.
    Loop,
    /// if-then block; `has_else` records whether an else branch follows.
    IfThen { has_else: bool },
    /// if-else block.
    IfElse,
    /// Marker for an else-if chain: the unit is the inner if, whose
    /// position is that of the outer if.
    ElseIf,
    /// switch case body.
    SwitchCase,
    /// try body; `has_finally` records the clause.
    TryBody { has_finally: bool },
    /// catch body; `has_finally` records the clause. Without a finally
    /// there is no active region here (the body region was popped at catch
    /// entry), so exiting it needs no cleanup.
    CatchBody { has_finally: bool },
    /// finally body (the try's own finally is already running).
    Finally,
}

/// Instruction a break/continue trampoline must emit when it exits the
/// construct owning a statement list: `Some(true)` = run the finally
/// (FinallyDivert), `Some(false)` = drop the region (TryEnd), `None` =
/// nothing to clean up.
fn exit_action(owner: CtxOwner) -> Option<bool> {
    match owner {
        CtxOwner::TryBody { has_finally } => Some(has_finally),
        CtxOwner::CatchBody { has_finally } => has_finally.then_some(true),
        _ => None,
    }
}

impl FnState {
    fn emit(&mut self, instr: IrInstr) -> usize {
        let ip = self.func.instrs.len() as u32;
        self.func.line_map.push((ip, self.cur_line));
        self.func.instrs.push(instr);
        ip as usize
    }

    fn patch(&mut self, idx: usize, ip: u32) {
        match &mut self.func.instrs[idx] {
            IrInstr::Jump(t) | IrInstr::JumpIfFalse(t) | IrInstr::JumpIfTrue(t) => *t = ip,
            _ => panic!("patch target is not a jump"),
        }
    }

    fn patch_jumps(&mut self, jumps: &[usize], target: u32) {
        for &j in jumps {
            self.patch(j, target);
        }
    }

    fn decl_local(&mut self, name: &str, ty: Ty, mutable: bool) -> usize {
        let slot = self.locals.len();
        self.locals.push(LocalVar {
            name: name.to_string(),
            ty,
            mutable,
            scope: self.scope_depth,
        });
        self.names.insert(name.to_string(), slot);
        slot
    }

    fn lookup_local(&self, name: &str) -> Option<usize> {
        self.names.get(name).copied()
    }

    fn local_type(&self, name: &str) -> Option<Ty> {
        if let Some(over) = self
            .narrowing
            .iter()
            .rev()
            .find_map(|scope| scope.get(name))
        {
            return Some(over.clone());
        }
        self.lookup_local(name).map(|i| self.locals[i].ty.clone())
    }

    fn begin_scope(&mut self) {
        self.narrowing.push(HashMap::new());
        self.scope_depth += 1;
    }

    fn end_scope(&mut self) {
        self.narrowing.pop();
        self.scope_depth -= 1;
        // Drop only the *names* declared inside the closed scope so outer
        // bindings become visible again. Local slots are never removed:
        // emitted IR refers to them by absolute index.
        self.names
            .retain(|_, slot| self.locals[*slot].scope <= self.scope_depth);
    }

    fn narrow(&mut self, name: &str, ty: Ty) {
        if let Some(scope) = self.narrowing.last_mut() {
            scope.insert(name.to_string(), ty);
        }
    }

    fn set_line(&mut self, span: crate::source::Span, sources: &SourceManager) {
        // Synthetic expressions carry a zero span; ignore them so they don't
        // clobber the real source line.
        if span.start == 0 && span.end == 0 {
            return;
        }
        if let Some(line) = sources.line_number(span) {
            self.cur_line = line;
        }
    }
}

/// Shared compiler context passed to all checking functions.
pub struct Ctx<'a> {
    pub program: &'a ResolvedProgram,
    pub sources: &'a SourceManager,
    pub diags: &'a mut Diagnostics,
    pub ir: &'a mut IrModule,
    pub fn_ids: &'a mut HashMap<u64, u32>,
}

impl<'a> Ctx<'a> {
    fn err_at(&mut self, code: &'static str, msg: impl Into<String>, span: crate::source::Span) {
        self.diags.err_at(code, msg, span);
    }

    fn err(&mut self, code: &'static str, msg: impl Into<String>) {
        self.diags.err(code, msg);
    }
}

pub struct Checker<'a> {
    pub program: &'a ResolvedProgram,
    pub sources: &'a SourceManager,
    pub diags: Diagnostics,
    pub ir: IrModule,
    /// Compiled function id per template.
    fn_ids: HashMap<u64, u32>,
    /// State of the method body currently being checked.
    state: Option<FnState>,
}

impl<'a> Checker<'a> {
    pub fn new(program: &'a ResolvedProgram, sources: &'a SourceManager) -> Self {
        Checker {
            program,
            sources,
            diags: Diagnostics::default(),
            ir: IrModule::new(),
            fn_ids: HashMap::new(),
            state: None,
        }
    }

    fn state_mut(&mut self) -> &mut FnState {
        self.state.as_mut().expect("no active method state")
    }

    pub fn err_at(
        &mut self,
        code: &'static str,
        msg: impl Into<String>,
        span: crate::source::Span,
    ) {
        self.diags.err_at(code, msg, span);
    }

    pub fn err(&mut self, code: &'static str, msg: impl Into<String>) {
        self.diags.err(code, msg);
    }

    /// Compile every method of every class and interface default, build
    /// class/interface metadata, and select the entry point.
    pub fn check_program(&mut self) {
        // Interface defaults first (classes may reference them).
        for iface in &self.program.interfaces {
            if iface.id < crate::resolve::builtin::BUILTIN_COUNT as u32 {
                continue;
            }
            for (idx, m) in iface.methods.iter().enumerate() {
                if m.body.is_some() {
                    self.compile_template(Template::InterfaceDefault(iface.id, idx));
                }
            }
        }
        for class in &self.program.classes {
            for (idx, m) in class.methods.iter().enumerate() {
                if m.body.is_some() {
                    self.compile_template(Template::Class(class.id, idx));
                }
            }
        }
        self.build_class_metadata();
        let _ = ();
        if let Some((cid, midx)) = self.program.entry {
            let fid = self
                .fn_ids
                .get(&(Template::Class(cid, midx).key()))
                .copied();
            self.ir.entry = fid;
            if fid.is_none() {
                self.err("C207", "entry point 'Main.run' has no body");
            }
        }
    }

    /// Compile one method template; returns its function id.
    /// Compile one method template; returns its function id.
    pub fn compile_template(&mut self, template: Template) -> u32 {
        compile_template(
            self.program,
            self.sources,
            &mut self.diags,
            &mut self.ir,
            &mut self.fn_ids,
            template,
        )
    }

    // ------------------------------------------------------------------
    // Block / statement checking (delegated to FnCtx below)
    // ------------------------------------------------------------------
}

fn check_block(ctx: &mut Ctx<'_>, st: &mut FnState, block: &Block, owner: CtxOwner) {
    for (i, stmt) in block.stmts.iter().enumerate() {
        st.ctx_stack.push(CtxEntry {
            idx: i,
            len: block.stmts.len(),
            owner,
        });
        check_stmt(ctx, st, stmt);
        st.ctx_stack.pop();
    }
}

fn check_stmt(ctx: &mut Ctx<'_>, st: &mut FnState, stmt: &Stmt) {
    match stmt {
        Stmt::Decl(d) => check_decl(ctx, st, d),
        Stmt::Expr(e) => check_expr_stmt(ctx, st, e),
        Stmt::Return(value) => check_return(ctx, st, value),
        Stmt::If(s) => check_if(ctx, st, s),
        Stmt::While(s) => check_while(ctx, st, s),
        Stmt::ForIn(s) => check_for_in(ctx, st, s),
        Stmt::Switch(s) => check_switch(ctx, st, s),
        Stmt::Try(s) => check_try(ctx, st, s),
        Stmt::Throw(e) => {
            st.set_line(e.span(), ctx.sources);
            check_expr(ctx, st, e);
            st.emit(IrInstr::Op(IrOp::Throw));
        }
        Stmt::Break => check_break_continue(ctx, st, true),
        Stmt::Continue => check_break_continue(ctx, st, false),
    }
}

/// Loop entries hold (break_jump_idx, continue_jump_idx): indices of
/// pending Jump instructions patched when the loop closes.
fn check_break_continue(ctx: &mut Ctx<'_>, st: &mut FnState, is_break: bool) {
    let n = st.loops.len();
    if n == 0 {
        ctx.err("C140", "'break'/'continue' outside of a loop");
        return;
    }
    // A break/continue must exit every enclosing try region it actually
    // leaves: run the finally (FinallyDivert) or drop the region (TryEnd)
    // before jumping to the loop target. Walk the lexical context stack
    // from the break site outward. Contexts between the break site and the
    // loop are always left by the break; above the loop, the walk tracks
    // where the loop end lies: as soon as it stays inside a body, no
    // further enclosing try is exited.
    let mut actions: Vec<bool> = Vec::new();
    let mut seen_loop = false;
    for e in st.ctx_stack.iter().rev() {
        if !seen_loop && !matches!(e.owner, CtxOwner::Loop) {
            if let Some(a) = exit_action(e.owner) {
                actions.push(a);
            }
            continue;
        }
        seen_loop = true;
        if e.idx + 1 < e.len {
            // A following sibling runs: the loop end stays inside this and
            // all outer bodies.
            break;
        }
        match e.owner {
            CtxOwner::Function => break,
            CtxOwner::ElseIf => {
                // The unit is the inner if; its position is the outer if's.
            }
            _ => {
                if let Some(a) = exit_action(e.owner) {
                    actions.push(a);
                }
            }
        }
    }
    for &has_fin in &actions {
        if has_fin {
            st.emit(IrInstr::Op(IrOp::FinallyDivert));
        } else {
            st.emit(IrInstr::Op(IrOp::TryEnd));
        }
    }
    let ip = st.emit(IrInstr::Jump(0));
    let entry = &mut st.loops[n - 1];
    if is_break {
        entry.0.push(ip);
    } else {
        entry.1.push(ip);
    }
}

fn patch_jumps(_ctx: &mut Ctx<'_>, st: &mut FnState, jumps: &[usize], target: u32) {
    for &j in jumps {
        st.patch(j, target);
    }
}

fn check_decl(ctx: &mut Ctx<'_>, st: &mut FnState, d: &DeclStmt) {
    st.set_line(d.span, ctx.sources);
    if st.names.contains_key(&d.name) {
        ctx.err_at(
            "C131",
            format!("duplicate local variable '{}'", d.name),
            d.span,
        );
    }
    let ty = resolve_type_ref(
        ctx.diags,
        ctx.program,
        &d.ty,
        &self_type_params(st),
        st.class_id,
        d.span,
    );
    let init_ty = match &d.init {
        Some(e) => {
            if matches!(e, Expr::List(_) | Expr::Map(_)) {
                st.expected_literal = Some(ty.clone());
            }
            let t = check_expr(ctx, st, e);
            st.expected_literal = None;
            t
        }
        None => Ty::null(),
    };
    if !crate::types::is_subtype(&init_ty, &ty, ctx.program) {
        ctx.err_at(
            "C132",
            format!(
                "cannot assign {} to '{}': expected {}",
                type_display(ctx.program, &init_ty),
                d.name,
                type_display(ctx.program, &ty)
            ),
            d.span,
        );
    }
    let slot = st.decl_local(&d.name, ty, d.mutable);
    match &d.init {
        Some(_) => {
            // Initializer value is on top of the stack.
            st.emit(IrInstr::StoreLocal(slot as u16));
        }
        None => {
            let c = ctx.ir.intern_const(crate::ir::IrConst::Null);
            st.emit(IrInstr::LoadConst(c));
            st.emit(IrInstr::StoreLocal(slot as u16));
        }
    }
}

fn self_type_params(st: &FnState) -> Vec<String> {
    st.type_param_names.clone()
}

fn check_expr_stmt(ctx: &mut Ctx<'_>, st: &mut FnState, e: &ExprStmt) {
    st.set_line(e.span, ctx.sources);
    match &e.expr {
        Expr::Assign(a) => check_assign(ctx, st, a),
        Expr::Update(op, a) => check_update(ctx, st, *op, a),
        other => {
            let t = check_expr(ctx, st, other);
            // Void calls leave nothing on the stack.
            if !matches!(t.base, BaseType::Void) {
                st.emit(IrInstr::Op(IrOp::Pop));
            }
        }
    }
}

/// Check `target = value`, emitting exactly one codegen pass.
fn check_assign(ctx: &mut Ctx<'_>, st: &mut FnState, a: &AssignExpr) {
    assign_with(ctx, st, a, false, None)
}

/// Check `target op= value`.
fn check_update(ctx: &mut Ctx<'_>, st: &mut FnState, op: UpdateOp, a: &AssignExpr) {
    assign_with(ctx, st, a, true, Some(op))
}

fn assign_with(
    ctx: &mut Ctx<'_>,
    st: &mut FnState,
    a: &AssignExpr,
    is_update: bool,
    uop: Option<UpdateOp>,
) {
    match a.target.as_ref() {
        Expr::Ident(name) => {
            if name == "self" {
                ctx.err_at("C133", "cannot assign to 'self'", a.target.span());
                return;
            }
            // Classify the target: local, own-class field (self fallback),
            // or global.
            #[derive(Clone)]
            enum Tgt {
                Local(u16, Ty),
                Field(u16, Ty),
                Global(Ty),
            }
            let tgt: Option<Tgt> = match st.lookup_local(name) {
                Some(i) => {
                    if !st.locals[i].mutable {
                        ctx.err_at(
                            "C134",
                            format!("cannot assign to immutable variable '{}'", name),
                            a.target.span(),
                        );
                    }
                    Some(Tgt::Local(
                        i as u16,
                        st.local_type(name).unwrap_or(Ty::object()),
                    ))
                }
                None => {
                    if is_global(name) {
                        ctx.err_at(
                            "C135",
                            format!("'{}' is an immutable runtime binding", name),
                            a.target.span(),
                        );
                        None
                    } else {
                        ctx.err_at(
                            "C136",
                            format!("unknown variable '{}'", name),
                            a.target.span(),
                        );
                        None
                    }
                }
            };
            let (target_ty, is_field, _is_global_tgt) = match &tgt {
                Some(Tgt::Local(_, t)) => (t.clone(), false, false),
                Some(Tgt::Field(_, t)) => (t.clone(), true, false),
                Some(Tgt::Global(t)) => (t.clone(), false, true),
                None => return,
            };
            if is_update && is_field {
                // Old value must end up under the new value on the stack;
                // Dup keeps the receiver alive across the read.
                if let Some(Tgt::Field(fslot, _)) = &tgt {
                    st.emit(IrInstr::LoadLocal(st.lookup_local("$self").unwrap() as u16));
                    st.emit(IrInstr::Op(IrOp::Dup));
                    st.emit(IrInstr::LoadField(*fslot));
                }
            }
            // Plain field stores need the receiver under the value on
            // the stack, so load it before evaluating the value.
            if !is_update {
                if let Some(Tgt::Field(_, _)) = &tgt {
                    st.emit(IrInstr::LoadLocal(st.lookup_local("$self").unwrap() as u16));
                }
            }
            // Compound updates need the old value under the new one.
            if is_update {
                if let Some(Tgt::Local(s, _)) = &tgt {
                    st.emit(IrInstr::LoadLocal(*s));
                }
            }
            let val_ty = check_expr(ctx, st, &a.value);
            if !crate::types::is_subtype(&val_ty, &target_ty, ctx.program) {
                ctx.err_at(
                    "C132",
                    format!(
                        "cannot assign {} to '{}': expected {}",
                        type_display(ctx.program, &val_ty),
                        name,
                        type_display(ctx.program, &target_ty)
                    ),
                    a.span,
                );
            }
            if is_update {
                let bin = match uop.unwrap() {
                    UpdateOp::Add => BinOp::Add,
                    UpdateOp::Sub => BinOp::Sub,
                    UpdateOp::Mul => BinOp::Mul,
                    UpdateOp::Div => BinOp::Div,
                    UpdateOp::Mod => BinOp::Mod,
                };
                emit_bin_arith(ctx, st, bin, &target_ty, a.target.span());
            }
            match &tgt {
                Some(Tgt::Local(s, _)) => {
                    st.emit(IrInstr::StoreLocal(*s));
                }
                Some(Tgt::Field(fslot, _)) => {
                    st.emit(IrInstr::StoreField(*fslot));
                    // StoreField keeps the receiver; drop it.
                    st.emit(IrInstr::Op(IrOp::Pop));
                }
                Some(Tgt::Global(_)) => {
                    st.emit(IrInstr::StoreGlobal(global_slot(name)));
                }
                None => {}
            }
        }
        Expr::Member(m) => {
            let recv_ty = check_expr(ctx, st, &m.obj);
            let (slot, field_ty, mutable) = match resolve_field(ctx, st, &recv_ty, &m.name) {
                Some(x) => x,
                None => {
                    // Distinguish privacy violation from unknown member.
                    if let BaseType::Class(cid, _) = &recv_ty.base {
                        let info = &ctx.program.classes[*cid as usize];
                        if let Some(f) = info.fields.iter().find(|f| f.name == m.name) {
                            ctx.err_at(
                                "C162",
                                format!(
                                    "field '{}' is private to class '{}'",
                                    m.name,
                                    ctx.program.class_name(f.declaring)
                                ),
                                m.span,
                            );
                        } else {
                            ctx.err_at(
                                "C171",
                                format!(
                                    "no field or method '{}' on {}",
                                    m.name,
                                    type_display(ctx.program, &recv_ty)
                                ),
                                m.span,
                            );
                        }
                    } else {
                        ctx.err_at(
                            "C171",
                            format!(
                                "no field '{}' on {}",
                                m.name,
                                type_display(ctx.program, &recv_ty)
                            ),
                            m.span,
                        );
                    }
                    return;
                }
            };
            if !mutable {
                ctx.err_at(
                    "C162",
                    format!(
                        "field '{}' is immutable; declare it with 'mutable' to assign",
                        m.name
                    ),
                    m.span,
                );
            }
            st.emit(IrInstr::Op(IrOp::NullCheck));
            if is_update {
                // Compound update: read the old value into a slot that ends
                // up *under* the new value. Duplicate the receiver, read
                // the field through the copy, then evaluate the value.
                // Stack: [recv, old] -> value -> [recv, old, val].
                st.emit(IrInstr::Op(IrOp::Dup));
                st.emit(IrInstr::LoadField(slot));
            }
            let val_ty = check_expr(ctx, st, &a.value);
            if !crate::types::is_subtype(&val_ty, &field_ty, ctx.program) {
                ctx.err_at(
                    "C132",
                    format!(
                        "cannot assign {} to field '{}': expected {}",
                        type_display(ctx.program, &val_ty),
                        m.name,
                        type_display(ctx.program, &field_ty)
                    ),
                    a.span,
                );
            }
            if is_update {
                let bin = match uop.unwrap() {
                    UpdateOp::Add => BinOp::Add,
                    UpdateOp::Sub => BinOp::Sub,
                    UpdateOp::Mul => BinOp::Mul,
                    UpdateOp::Div => BinOp::Div,
                    UpdateOp::Mod => BinOp::Mod,
                };
                emit_bin_arith(ctx, st, bin, &field_ty, a.target.span());
            }
            st.emit(IrInstr::StoreField(slot));
            // StoreField keeps the receiver; drop it.
            st.emit(IrInstr::Op(IrOp::Pop));
        }
        other => {
            ctx.err_at("C138", "invalid assignment target", other.span());
        }
    }
}

fn require_int_index(ctx: &mut Ctx<'_>, _st: &mut FnState, idx_ty: &Ty, idx: &Expr) {
    if !matches!(idx_ty.base, BaseType::Long | BaseType::Byte) {
        ctx.err_at("C139", "index must be an integer", idx.span());
    }
}

fn check_return(ctx: &mut Ctx<'_>, st: &mut FnState, value: &Option<Expr>) {
    match (value, &st.ret_ty) {
        (None, None) => {
            st.emit(IrInstr::ReturnVoid);
        }
        (None, Some(ty)) if matches!(ty.base, BaseType::Void) => {
            // `: Void` is a valid annotation; a bare `return` is fine.
            st.emit(IrInstr::ReturnVoid);
        }
        (None, Some(ty)) => {
            ctx.err_at(
                "C141",
                format!(
                    "missing return value (expected {})",
                    type_display(ctx.program, ty)
                ),
                crate::source::Span::new(0, 0, 0),
            );
            let c = ctx.ir.intern_const(crate::ir::IrConst::Null);
            st.emit(IrInstr::LoadConst(c));
            st.emit(IrInstr::Return);
        }
        (Some(e), _) => {
            let vty = check_expr(ctx, st, e);
            match &st.ret_ty {
                Some(ty) => {
                    if !crate::types::is_subtype(&vty, ty, ctx.program) {
                        ctx.err_at(
                            "C142",
                            format!(
                                "return type {} does not match expected {}",
                                type_display(ctx.program, &vty),
                                type_display(ctx.program, ty)
                            ),
                            e.span(),
                        );
                    }
                }
                None => {
                    ctx.err_at(
                        "C143",
                        "method has no return type but returns a value",
                        e.span(),
                    );
                }
            }
            st.emit(IrInstr::Return);
        }
    }
}

/// True when control can never fall out of this statement list: some
/// reachable statement unconditionally leaves (return/throw/break/continue
/// or a conditional whose every branch leaves). Anything uncertain is
/// treated conservatively as falling through.
fn block_diverges(stmts: &[Stmt]) -> bool {
    stmts.iter().any(stmt_diverges)
}

fn stmt_diverges(s: &Stmt) -> bool {
    match s {
        Stmt::Return(_) | Stmt::Throw(_) | Stmt::Break | Stmt::Continue => true,
        Stmt::If(i) => if_diverges(i),
        Stmt::Switch(sw) => switch_diverges(sw),
        Stmt::Try(t) => try_diverges(t),
        _ => false,
    }
}

/// A try statement diverges when control can never fall past it.
fn try_diverges(t: &crate::ast::TryStmt) -> bool {
    let body_div = block_diverges(&t.body.stmts);
    let catch_div = match &t.catch_body {
        Some(c) => block_diverges(&c.stmts),
        None => true,
    };
    let fin_div = t
        .finally_body
        .as_ref()
        .is_some_and(|f| block_diverges(&f.stmts));
    // Control falls past the try when:
    //  - the body completes normally and no (diverging) finally follows;
    //  - an exception from the body is caught by a non-diverging catch and
    //    no (diverging) finally follows (any body may throw at runtime).
    // Body returns/breaks never fall past, and an uncaught exception
    // rethrows.
    let falls_past = (!body_div && !fin_div) || (t.catch_body.is_some() && !catch_div && !fin_div);
    !falls_past
}

/// An `if` (possibly an `else if` chain) only diverges when it has an else
/// branch and both sides diverge.
fn if_diverges(i: &IfStmt) -> bool {
    let Some(else_branch) = &i.else_branch else {
        return false;
    };
    if !block_diverges(&i.then.stmts) {
        return false;
    }
    match else_branch {
        ElseBranch::Block(b) => block_diverges(&b.stmts),
        ElseBranch::If(inner) => if_diverges(inner),
    }
}

/// True when some reachable statement unconditionally ends in a return,
/// throw or break — i.e. control can neither fall out nor continue. Used
/// to omit a loop back-edge that would be dead code.
fn block_exits(stmts: &[Stmt]) -> bool {
    stmts.iter().any(stmt_exits)
}

fn stmt_exits(s: &Stmt) -> bool {
    match s {
        Stmt::Return(_) | Stmt::Throw(_) | Stmt::Break => true,
        Stmt::If(i) => if_exits(i),
        Stmt::Switch(sw) => switch_exits(sw),
        _ => false,
    }
}

fn if_exits(i: &IfStmt) -> bool {
    let Some(else_branch) = &i.else_branch else {
        return false;
    };
    if !block_exits(&i.then.stmts) {
        return false;
    }
    match else_branch {
        ElseBranch::Block(b) => block_exits(&b.stmts),
        ElseBranch::If(inner) => if_exits(inner),
    }
}

fn switch_exits(sw: &SwitchStmt) -> bool {
    if !sw.cases.iter().any(|c| c.is_default) {
        return false;
    }
    sw.cases.iter().all(|c| block_exits(&c.body.stmts))
}

/// A switch diverges only when a default case exists and every case body
/// (including the default) diverges.
fn switch_diverges(sw: &SwitchStmt) -> bool {
    if !sw.cases.iter().any(|c| c.is_default) {
        return false;
    }
    sw.cases.iter().all(|c| block_diverges(&c.body.stmts))
}

/// Fixed-point reachability over the linear instruction stream. Every
/// non-terminating instruction falls through to the next one; branches add
/// extra edges. `on_exception` includes TryBegin -> catch-region edges.
/// `rethrow_ok(i)` decides whether the `FinallyEnd` at index `i` may fall
/// through (it only does when reachable by normal completion, i.e.
/// without a pending exception). Returns (reachable set, can a path reach
/// one-past-the-end).
fn ir_reach(
    instrs: &[IrInstr],
    on_exception: bool,
    rethrow_ok: impl Fn(usize) -> bool,
) -> (Vec<bool>, bool) {
    use IrInstr::*;
    let n = instrs.len();
    if n == 0 {
        return (vec![], false);
    }
    let term = |i: &IrInstr| matches!(i, Return | ReturnVoid | Op(crate::ir::IrOp::Throw));
    let mut reach = vec![false; n];
    reach[0] = true;
    let mut off_end = false;
    loop {
        let mut changed = false;
        for i in 0..n {
            if !reach[i] {
                continue;
            }
            let ins = &instrs[i];
            if term(ins) {
                continue;
            }
            if matches!(ins, Op(crate::ir::IrOp::FinallyEnd)) && !rethrow_ok(i) {
                continue; // pending exception: control leaves the function
            }
            // Explicit branch edges (Jump has no fall-through).
            let mut edge = |t: u32, changed: &mut bool| {
                let t = t as usize;
                if t >= n {
                    off_end = true;
                } else if !reach[t] {
                    reach[t] = true;
                    *changed = true;
                }
            };
            match ins {
                Jump(t) => edge(*t, &mut changed),
                JumpIfFalse(t) | JumpIfTrue(t) => edge(*t, &mut changed),
                TryBegin(c, f) if on_exception => {
                    // An in-flight exception resumes at the catch region
                    // (or, for finally-only regions where catch == finally,
                    // at the finally body). The finally region is otherwise
                    // reached through explicit Jump instructions, which are
                    // modeled above; normal flow falls through below.
                    let _ = f;
                    edge(*c, &mut changed);
                }
                _ => {}
            }
            if matches!(ins, Jump(_)) {
                continue; // unconditional branch: no fall-through
            }
            // Fall-through to the next instruction.
            let ft = i + 1;
            if ft >= n {
                off_end = true;
            } else if !reach[ft] {
                reach[ft] = true;
                changed = true;
            }
        }
        if !changed {
            break;
        }
    }
    (reach, off_end)
}

/// Structural reachability of one-past-the-end, including exception edges
/// (a finally block entered only through an in-flight exception is treated
/// as reachable because `FinallyEnd` may fall through). Used to
/// decide whether an explicit epilogue is required to keep the verifier
/// happy and to give end-pointing jumps a landing pad.
fn body_flows_off_end(instrs: &[IrInstr]) -> bool {
    ir_reach(instrs, true, |_| true).1
}

/// Semantic reachability of one-past-the-end: an exception-reachable
/// finally body rethrows (`FinallyEnd`) and so never falls off the
/// end; a catch region resumes normal flow, so exception edges still count.
/// Used to decide the "missing return" (C130) diagnostic.
fn body_flows_off_end_semantically(instrs: &[IrInstr]) -> bool {
    let (normal, off_normal) = ir_reach(instrs, false, |_| true);
    if off_normal {
        return true;
    }
    // Exception edges can resume *normal* flow only through catch regions;
    // rethrows only fall through when also normally reachable.
    ir_reach(instrs, true, |i| normal[i]).1
}

fn check_if(ctx: &mut Ctx<'_>, st: &mut FnState, s: &crate::ast::IfStmt) {
    st.set_line(s.span, ctx.sources);
    check_cond(ctx, st, &s.cond);
    let else_ip = st.emit(IrInstr::JumpIfFalse(0));
    // Null narrowing for `x != null` / `x == null`.
    st.begin_scope();
    if let Some((name, then_nonnull)) = null_narrow(&s.cond, true) {
        let nt = narrowed_type(ctx, st, name, then_nonnull);
        st.narrow(name, nt);
    }
    check_block(
        ctx,
        st,
        &s.then,
        CtxOwner::IfThen {
            has_else: s.else_branch.is_some(),
        },
    );
    st.end_scope();
    // When the then-branch can fall out, it must jump over the else branch
    // to the merge point. When it always diverges the jump would be dead
    // code (rejected by the verifier), so skip it.
    let end_jump = if s.else_branch.is_some() && !block_diverges(&s.then.stmts) {
        Some(st.emit(IrInstr::Jump(0)))
    } else {
        None
    };
    st.patch(else_ip, st.func.instrs.len() as u32);
    if let Some(branch) = &s.else_branch {
        st.begin_scope();
        if let Some((name, else_nonnull)) = null_narrow(&s.cond, false) {
            let nt = narrowed_type(ctx, st, name, else_nonnull);
            st.narrow(name, nt);
        }
        match branch {
            ElseBranch::Block(b) => check_block(ctx, st, b, CtxOwner::IfElse),
            ElseBranch::If(inner) => {
                st.ctx_stack.push(CtxEntry {
                    idx: 0,
                    len: 0,
                    owner: CtxOwner::ElseIf,
                });
                check_if(ctx, st, inner);
                st.ctx_stack.pop();
            }
        }
        st.end_scope();
    }
    if let Some(j) = end_jump {
        st.patch(j, st.func.instrs.len() as u32);
    }
}

fn narrowed_type(_ctx: &mut Ctx<'_>, st: &mut FnState, name: &str, nonnull: bool) -> Ty {
    let base = st.local_type(name).unwrap_or(Ty::object());
    if nonnull {
        Ty::new(base.base, false)
    } else {
        Ty::new(BaseType::Null, true)
    }
}

fn check_cond(ctx: &mut Ctx<'_>, st: &mut FnState, cond: &Expr) {
    let ty = check_expr(ctx, st, cond);
    if !matches!(ty.base, BaseType::Bool) {
        ctx.err_at(
            "C144",
            format!(
                "condition must be Bool, found {}",
                type_display(ctx.program, &ty)
            ),
            cond.span(),
        );
    }
    if ty.nullable {
        st.emit(IrInstr::Op(IrOp::NullCheck));
    }
}

fn check_while(ctx: &mut Ctx<'_>, st: &mut FnState, s: &crate::ast::WhileStmt) {
    st.set_line(s.span, ctx.sources);
    let start = st.func.instrs.len() as u32;
    st.loops.push((vec![], vec![]));
    check_cond(ctx, st, &s.cond);
    let end_ip = st.emit(IrInstr::JumpIfFalse(0));
    check_block(ctx, st, &s.body, CtxOwner::Loop);
    // Loop back-edge: give the GC a chance to reclaim dead objects. When
    // every body path returns/throws/breaks the back-edge is dead code.
    if !block_exits(&s.body.stmts) {
        st.emit(IrInstr::GcHint);
        st.emit(IrInstr::Jump(start));
    }
    let end = st.func.instrs.len() as u32;
    let (breaks, conts) = st.loops.pop().unwrap();
    st.patch(end_ip, end);
    st.patch_jumps(&breaks, end);
    // continue re-evaluates the condition.
    st.patch_jumps(&conts, start);
}

fn check_for_in(ctx: &mut Ctx<'_>, st: &mut FnState, s: &crate::ast::ForInStmt) {
    st.set_line(s.span, ctx.sources);
    let iter_ty = check_expr(ctx, st, &s.iter);
    #[derive(Clone, Copy, PartialEq)]
    enum Kind {
        List,
        Stack,
        String,
        MapKeys,
        Range,
        Other,
    }
    let (kind, elem) = match &iter_ty.base {
        BaseType::List(e) => (Kind::List, e.as_ref().clone()),
        BaseType::Stack(e) => (Kind::Stack, e.as_ref().clone()),
        BaseType::String => (Kind::String, BaseType::Char),
        BaseType::Map(k, _) => (Kind::MapKeys, k.as_ref().clone()),
        BaseType::Range => (Kind::Range, BaseType::Long),
        _ => (Kind::Other, BaseType::Object),
    };
    if kind == Kind::Other {
        ctx.err_at(
            "C145",
            format!(
                "cannot iterate over {}",
                type_display(ctx.program, &iter_ty)
            ),
            s.iter.span(),
        );
        return;
    }
    if iter_ty.nullable {
        st.emit(IrInstr::Op(IrOp::NullCheck));
    }
    let var_slot = st.decl_local(&s.var, Ty::non_null(elem.clone()), false);
    st.loops.push((vec![], vec![]));
    let mut get_op = IrOp::ListGet;
    let mut len_op = IrOp::ListLen;

    match kind {
        Kind::List => {}
        Kind::Stack => {
            get_op = IrOp::StackGet;
            len_op = IrOp::StackLen;
        }
        Kind::String => {
            get_op = IrOp::StrCharAt;
            len_op = IrOp::StrLen;
        }
        Kind::MapKeys => {
            // Materialize keys as a list first.
            st.emit(IrInstr::Op(IrOp::MapKeys));
        }
        Kind::Range => {
            // Stack: [start, end].
            let s_slot = st.decl_local("$rs", Ty::long(), false);
            let e_slot = st.decl_local("$re", Ty::long(), false);
            let i_slot = st.decl_local("$ri", Ty::long(), false);
            // Stack is [start, end]; pop in reverse order.
            st.emit(IrInstr::StoreLocal(e_slot as u16));
            st.emit(IrInstr::StoreLocal(s_slot as u16));
            st.emit(IrInstr::LoadLocal(s_slot as u16));
            st.emit(IrInstr::StoreLocal(i_slot as u16));
            let start = st.func.instrs.len() as u32;
            st.emit(IrInstr::LoadLocal(i_slot as u16));
            st.emit(IrInstr::LoadLocal(e_slot as u16));
            st.emit(IrInstr::Op(IrOp::LtLong));
            let end_ip = st.emit(IrInstr::JumpIfFalse(0));
            st.emit(IrInstr::LoadLocal(i_slot as u16));
            st.emit(IrInstr::StoreLocal(var_slot as u16));
            check_block(ctx, st, &s.body, CtxOwner::Loop);
            let (breaks, conts) = st.loops.pop().unwrap();
            // When every body path returns/throws/breaks, the increment and
            // back-edge are dead code (and no continue can target them).
            if !block_exits(&s.body.stmts) {
                let inc_ip = st.func.instrs.len() as u32;
                st.emit(IrInstr::LoadLocal(i_slot as u16));
                let one = ctx.ir.intern_const(crate::ir::IrConst::Long(1));
                st.emit(IrInstr::LoadConst(one));
                st.emit(IrInstr::Op(IrOp::AddLong));
                st.emit(IrInstr::StoreLocal(i_slot as u16));
                // Loop back-edge: give the GC a chance to reclaim dead objects.
                st.emit(IrInstr::GcHint);
                st.emit(IrInstr::Jump(start));
                st.patch_jumps(&conts, inc_ip);
            }
            let end = st.func.instrs.len() as u32;
            st.patch(end_ip, end);
            st.patch_jumps(&breaks, end);
            return;
        }
        Kind::Other => unreachable!(),
    }
    // Collection/string lowering: keep the iterable in a local.
    let iter_slot = st.decl_local("$iter", iter_ty.clone(), false);
    st.emit(IrInstr::StoreLocal(iter_slot as u16));
    let len_slot = st.decl_local("$len", Ty::long(), false);
    let i_slot = st.decl_local("$i", Ty::long(), false);
    st.emit(IrInstr::LoadLocal(iter_slot as u16));
    st.emit(IrInstr::Op(len_op));
    st.emit(IrInstr::StoreLocal(len_slot as u16));
    let zero = ctx.ir.intern_const(crate::ir::IrConst::Long(0));
    st.emit(IrInstr::LoadConst(zero));
    st.emit(IrInstr::StoreLocal(i_slot as u16));
    let start = st.func.instrs.len() as u32;
    st.emit(IrInstr::LoadLocal(i_slot as u16));
    st.emit(IrInstr::LoadLocal(len_slot as u16));
    st.emit(IrInstr::Op(IrOp::LtLong));
    let end_ip = st.emit(IrInstr::JumpIfFalse(0));
    st.emit(IrInstr::LoadLocal(iter_slot as u16));
    st.emit(IrInstr::Op(IrOp::NullCheck));
    st.emit(IrInstr::LoadLocal(i_slot as u16));
    st.emit(IrInstr::Op(get_op));
    st.emit(IrInstr::StoreLocal(var_slot as u16));
    check_block(ctx, st, &s.body, CtxOwner::Loop);
    let (breaks, conts) = st.loops.pop().unwrap();
    // When every body path returns/throws/breaks, the increment and
    // back-edge are dead code (and no continue can target them).
    if !block_exits(&s.body.stmts) {
        let inc_ip = st.func.instrs.len() as u32;
        st.emit(IrInstr::LoadLocal(i_slot as u16));
        let one = ctx.ir.intern_const(crate::ir::IrConst::Long(1));
        st.emit(IrInstr::LoadConst(one));
        st.emit(IrInstr::Op(IrOp::AddLong));
        st.emit(IrInstr::StoreLocal(i_slot as u16));
        // Loop back-edge: give the GC a chance to reclaim dead objects.
        st.emit(IrInstr::GcHint);
        st.emit(IrInstr::Jump(start));
        st.patch_jumps(&conts, inc_ip);
    }
    let end = st.func.instrs.len() as u32;
    st.patch(end_ip, end);
    st.patch_jumps(&breaks, end);
}

/// Detect `x != null` / `x == null` conditions for null narrowing.
fn null_narrow(cond: &Expr, then_branch: bool) -> Option<(&str, bool)> {
    match cond {
        Expr::Binary(b) => match b.op {
            BinOp::Ne => {
                if let (Expr::Ident(n), Expr::Null) = (&*b.left, &*b.right) {
                    Some((n, then_branch))
                } else if let (Expr::Null, Expr::Ident(n)) = (&*b.left, &*b.right) {
                    Some((n, then_branch))
                } else {
                    None
                }
            }
            BinOp::Eq => {
                if let (Expr::Ident(n), Expr::Null) = (&*b.left, &*b.right) {
                    Some((n, !then_branch))
                } else if let (Expr::Null, Expr::Ident(n)) = (&*b.left, &*b.right) {
                    Some((n, !then_branch))
                } else {
                    None
                }
            }
            _ => None,
        },
        _ => None,
    }
}

fn check_switch(ctx: &mut Ctx<'_>, st: &mut FnState, s: &crate::ast::SwitchStmt) {
    st.set_line(s.span, ctx.sources);
    let subj_ty = check_expr(ctx, st, &s.subject);
    let subj_slot = st.decl_local("$subj", subj_ty.clone(), false);
    st.emit(IrInstr::StoreLocal(subj_slot as u16));
    // Pass 1: emit all case tests, collecting jump indices per case.
    let mut case_jumps: Vec<Option<Vec<usize>>> = vec![None; s.cases.len()];
    let mut default_idx: Option<usize> = None;
    for (ci, case) in s.cases.iter().enumerate() {
        if case.is_default {
            default_idx = Some(ci);
            continue;
        }
        let mut jumps = vec![];
        for v in &case.values {
            st.emit(IrInstr::LoadLocal(subj_slot as u16));
            let lit_ty = check_expr(ctx, st, v);
            if !crate::types::is_subtype(&lit_ty, &subj_ty, ctx.program)
                && !crate::types::is_subtype(&subj_ty, &lit_ty, ctx.program)
            {
                ctx.err_at(
                    "C146",
                    format!(
                        "case value {} is not compatible with switch subject {}",
                        type_display(ctx.program, &lit_ty),
                        type_display(ctx.program, &subj_ty)
                    ),
                    v.span(),
                );
            }
            emit_eq(ctx, st, &subj_ty, &lit_ty, v.span());
            jumps.push(st.emit(IrInstr::JumpIfTrue(0)));
        }
        case_jumps[ci] = Some(jumps);
    }
    // No case matched: run the default body (if any), else skip all bodies.
    let fallthrough = st.emit(IrInstr::Jump(0));
    // Pass 2: emit bodies (including default); patch jumps to body starts.
    let mut body_starts: Vec<u32> = vec![0; s.cases.len()];
    let mut end_jumps: Vec<usize> = vec![];
    for (ci, case) in s.cases.iter().enumerate() {
        body_starts[ci] = st.func.instrs.len() as u32;
        check_block(ctx, st, &case.body, CtxOwner::SwitchCase);
        // A body that cannot fall out needs no jump to the merge point.
        if !block_diverges(&case.body.stmts) {
            end_jumps.push(st.emit(IrInstr::Jump(0)));
        }
    }
    let end = st.func.instrs.len() as u32;
    for (ci, jumps) in case_jumps.iter().enumerate() {
        if let Some(js) = jumps {
            for &j in js {
                st.patch(j, body_starts[ci]);
            }
        }
    }
    match default_idx {
        Some(ci) => st.patch(fallthrough, body_starts[ci]),
        None => st.patch(fallthrough, end),
    }
    for &j in &end_jumps {
        st.patch(j, end);
    }
}

fn check_try(ctx: &mut Ctx<'_>, st: &mut FnState, s: &crate::ast::TryStmt) {
    st.set_line(s.span, ctx.sources);
    let has_catch = s.catch_body.is_some();
    let has_finally = s.finally_body.is_some();
    // Layout:
    //   TryBegin catch finally        (catch == finally: no catch;
    //                                   finally == 0: no finally)
    //   body
    //   [TryEnd]                      (body can complete normally)
    //   [jmp finally]                 (normal completion skips the clauses)
    // catch: StoreLocal(var)          (the body region was already popped
    //                                   by the unwinder)
    //        [TryBegin finally finally]
    //                                  (exceptions raised in the catch body
    //                                   still run the finally)
    //        catch body
    //        [TryEnd]                 (catch can complete normally)
    //        [jmp finally]
    // finally: finally body
    //         [FinallyEnd]            (rethrow a passed-through exception,
    //                                  complete a deferred return, or resume
    //                                  a diverted break/continue)
    let tb_idx = st.emit(IrInstr::TryBegin(0, 0));
    let body_div = block_diverges(&s.body.stmts);
    check_block(ctx, st, &s.body, CtxOwner::TryBody { has_finally });
    // The region is closed only when the body can complete normally; when
    // every body path leaves (returns/throws/breaks), TryEnd would be dead
    // code (the VM cleans regions up on return/unwind, and break/continue
    // trampolines pop or divert the region explicitly).
    if !body_div {
        st.emit(IrInstr::Op(IrOp::TryEnd));
    }
    // Normal completion must skip the catch/finally clauses. Skipped when
    // the body cannot complete normally (the jump would be dead code).
    let normal_jump = if (has_catch || has_finally) && !body_div {
        Some(st.emit(IrInstr::Jump(0)))
    } else {
        None
    };
    let catch_at = st.func.instrs.len() as u32;
    let mut catch_jump: Option<usize> = None;
    let mut r2_idx: Option<usize> = None;
    if has_catch {
        let var_name = s.catch_name.clone().unwrap_or_else(|| "err".into());
        let var_slot = st.decl_local(&var_name, Ty::object(), false);
        st.emit(IrInstr::StoreLocal(var_slot as u16));
        if has_finally {
            r2_idx = Some(st.emit(IrInstr::TryBegin(0, 0)));
        }
        let catch_body = s.catch_body.as_ref().unwrap();
        let cdiv = block_diverges(&catch_body.stmts);
        check_block(ctx, st, catch_body, CtxOwner::CatchBody { has_finally });
        if has_finally && !cdiv {
            st.emit(IrInstr::Op(IrOp::TryEnd));
            catch_jump = Some(st.emit(IrInstr::Jump(0)));
        }
    }
    // The finally body runs on normal completion of the body or catch, when
    // an exception passes through (no catch), and when an exception is
    // raised inside the catch body; with the catch-body region above it is
    // reachable whenever present, so it is always emitted.
    let finally_at = st.func.instrs.len() as u32;
    if has_finally {
        let fin_body = s.finally_body.as_ref().unwrap();
        let fin_div = block_diverges(&fin_body.stmts);
        check_block(ctx, st, fin_body, CtxOwner::Finally);
        if !fin_div {
            // An exception may be passing through, a return may be
            // deferred, or a diverted break/continue may resume here.
            st.emit(IrInstr::Op(IrOp::FinallyEnd));
        }
    }
    if let Some(j) = normal_jump {
        st.patch(j, finally_at);
    }
    if let Some(j) = catch_jump {
        st.patch(j, finally_at);
    }
    if let Some(i) = r2_idx {
        match &mut st.func.instrs[i] {
            IrInstr::TryBegin(c, f) => {
                *c = finally_at;
                *f = finally_at;
            }
            _ => unreachable!(),
        }
    }
    match &mut st.func.instrs[tb_idx] {
        IrInstr::TryBegin(c, f) => {
            *c = catch_at;
            *f = if has_finally { finally_at } else { 0 };
        }
        _ => unreachable!(),
    }
}

/// Type-check an expression, leaving its value on the stack.
fn check_expr(ctx: &mut Ctx<'_>, st: &mut FnState, e: &Expr) -> Ty {
    st.set_line(e.span(), ctx.sources);
    match e {
        Expr::Int(v) => {
            let c = ctx.ir.intern_const(crate::ir::IrConst::Long(*v));
            st.emit(IrInstr::LoadConst(c));
            Ty::long()
        }
        Expr::Float(v) => {
            let c = ctx.ir.intern_const(crate::ir::IrConst::Double(*v));
            st.emit(IrInstr::LoadConst(c));
            Ty::double()
        }
        Expr::Bool(v) => {
            let c = ctx.ir.intern_const(crate::ir::IrConst::Bool(*v));
            st.emit(IrInstr::LoadConst(c));
            Ty::bool_()
        }
        Expr::Char(v) => {
            let c = ctx.ir.intern_const(crate::ir::IrConst::Char(*v));
            st.emit(IrInstr::LoadConst(c));
            Ty::char_()
        }
        Expr::String(v) => {
            let c = ctx.ir.intern_const(crate::ir::IrConst::Str(v.clone()));
            st.emit(IrInstr::LoadConst(c));
            Ty::string()
        }
        Expr::Null => {
            let c = ctx.ir.intern_const(crate::ir::IrConst::Null);
            st.emit(IrInstr::LoadConst(c));
            Ty::null()
        }
        Expr::Ident(name) => check_ident(ctx, st, name, e.span()),
        Expr::List(elements) => check_list(ctx, st, elements),
        Expr::Map(entries) => check_map(ctx, st, entries),
        Expr::Stack(_) => {
            ctx.err_at(
                "C150",
                "stack literals are not supported; use Stack.new() and push",
                e.span(),
            );
            Ty::object()
        }
        Expr::Call(c) => check_call(ctx, st, c),
        Expr::Member(m) => check_member(ctx, st, m),
        Expr::StaticAccess(sa) => check_static_access_value(ctx, st, sa),
        Expr::Binary(b) => check_binary(ctx, st, b),
        Expr::Unary(op, inner) => check_unary(ctx, st, *op, inner),
        Expr::Coalesce(l, r) => check_coalesce(ctx, st, l, r),
        Expr::Range(l, r) => check_range(ctx, st, l, r),
        Expr::Match(m) => check_match(ctx, st, m),
        Expr::SelfInit(si) => check_self_init(ctx, st, si),
        Expr::SuperCall(sc) => check_super_call(ctx, st, sc),
        Expr::Assign(_) | Expr::Update(_, _) => {
            ctx.err_at("C152", "assignment is only valid as a statement", e.span());
            Ty::object()
        }
    }
}

fn check_ident(ctx: &mut Ctx<'_>, st: &mut FnState, name: &str, span: crate::source::Span) -> Ty {
    if name == "self" {
        if st.class_id.is_none() {
            ctx.err_at("C153", "'self' is not available here", span);
            return Ty::object();
        }
        if let Some(i) = st.lookup_local("self") {
            st.emit(IrInstr::LoadLocal(i as u16));
            return st.local_type("self").unwrap_or(Ty::object());
        }
        ctx.err_at("C153", "'self' is not available in static methods", span);
        return Ty::object();
    }
    if let Some(i) = st.lookup_local(name) {
        st.emit(IrInstr::LoadLocal(i as u16));
        return st.local_type(name).unwrap_or(Ty::object());
    }
    if is_global(name) {
        st.emit(IrInstr::LoadGlobal(global_slot(name)));
        return global_type(name);
    }
    ctx.err_at("C136", format!("unknown variable '{}'", name), span);
    Ty::object()
}

/// `Color.red` / `Color.blue(7)` used as a value (not a call).
fn check_static_access_value(ctx: &mut Ctx<'_>, st: &mut FnState, sa: &StaticAccessExpr) -> Ty {
    let base = resolve_type_ref(
        ctx.diags,
        ctx.program,
        &sa.ty,
        &self_type_params(st),
        st.class_id,
        sa.span,
    );
    if let BaseType::Enum(eid, args) = &base.base {
        let einfo = &ctx.program.enums[*eid as usize];
        match einfo.variants.iter().position(|v| v.name == sa.name) {
            Some(vi) => {
                if einfo.variants[vi].payload.is_some() {
                    ctx.err_at(
                        "C165",
                        format!(
                            "variant '{}' requires a payload; use {}.{}(...)",
                            sa.name,
                            type_display(ctx.program, &base),
                            sa.name
                        ),
                        sa.span,
                    );
                }
                st.emit(IrInstr::NewEnum(*eid as u16, vi as u8, false));
                return Ty::non_null(BaseType::Enum(*eid, args.clone()));
            }
            None => {
                ctx.err_at(
                    "C167",
                    format!(
                        "no variant '{}' on enum {}",
                        sa.name,
                        type_display(ctx.program, &base)
                    ),
                    sa.span,
                );
            }
        }
    } else {
        ctx.err_at(
            "C151",
            "static access must be a method call or enum variant",
            sa.span,
        );
    }
    Ty::object()
}

fn check_list(ctx: &mut Ctx<'_>, st: &mut FnState, elements: &[Expr]) -> Ty {
    // Evaluate elements into temp locals (single codegen pass), then
    // build the list.
    let mut tmps = vec![];
    let mut elem: Option<BaseType> = None;
    for el in elements {
        let t = check_expr(ctx, st, el);
        match &mut elem {
            None => elem = Some(t.base.clone()),
            Some(e) => {
                if *e != t.base {
                    *e = BaseType::Object;
                }
            }
        }
        let slot = st.decl_local("$lt", t, false);
        st.emit(IrInstr::StoreLocal(slot as u16));
        tmps.push(slot);
    }
    let elem_ty = match &st.expected_literal {
        Some(exp) => match &exp.base {
            BaseType::List(e) => e.as_ref().clone(),
            _ => elem.unwrap_or(BaseType::Object),
        },
        None => elem.unwrap_or(BaseType::Object),
    };
    for (i, slot) in tmps.iter().enumerate() {
        let actual = st
            .locals
            .get(*slot)
            .map(|local| local.ty.clone())
            .unwrap_or_else(Ty::object);
        let expected = Ty::non_null(elem_ty.clone());
        if st.expected_literal.is_some()
            && !crate::types::is_subtype(&actual, &expected, ctx.program)
        {
            ctx.err_at(
                "C214",
                format!(
                    "list element type {} does not match {}",
                    type_display(ctx.program, &actual),
                    type_display(ctx.program, &expected)
                ),
                elements[i].span(),
            );
        }
    }
    st.emit(IrInstr::NewList(elements.len() as u16));
    for &slot in &tmps {
        st.emit(IrInstr::LoadLocal(slot as u16));
        st.emit(IrInstr::Op(IrOp::ListAdd));
    }
    Ty::non_null(BaseType::List(Box::new(elem_ty)))
}

fn check_map(ctx: &mut Ctx<'_>, st: &mut FnState, entries: &[(Expr, Expr)]) -> Ty {
    // Single codegen pass: evaluate entries into temp locals first.
    let mut ktys = vec![];
    let mut vtys = vec![];
    let mut ktmps = vec![];
    let mut vtmps = vec![];
    for (k, v) in entries {
        let kt = check_expr(ctx, st, k);
        let vt = check_expr(ctx, st, v);
        let ks = st.decl_local("$mk", kt.clone(), false);
        let vs = st.decl_local("$mv", vt.clone(), false);
        // Stack is [k, v]; pop in reverse order.
        st.emit(IrInstr::StoreLocal(vs as u16));
        st.emit(IrInstr::StoreLocal(ks as u16));
        ktmps.push(ks);
        vtmps.push(vs);
        ktys.push(kt);
        vtys.push(vt);
    }
    // Element types: expected annotation wins, otherwise unify.
    let (mut kty, mut vty) = match &st.expected_literal {
        Some(exp) => match &exp.base {
            BaseType::Map(k, v) => (k.as_ref().clone(), v.as_ref().clone()),
            _ => (BaseType::Object, BaseType::Object),
        },
        None => (BaseType::Object, BaseType::Object),
    };
    let inferred = |tys: &[Ty]| -> BaseType {
        let out = tys[0].base.clone();
        for t in &tys[1..] {
            if t.base != out {
                return BaseType::Object;
            }
        }
        out
    };
    if st.expected_literal.is_none() {
        if !ktys.is_empty() {
            kty = inferred(&ktys);
        }
        if !vtys.is_empty() {
            vty = inferred(&vtys);
        }
    }
    // Validate entries against the resolved element types.
    for (i, kt) in ktys.iter().enumerate() {
        if !crate::types::is_subtype(kt, &Ty::non_null(kty.clone()), ctx.program) {
            ctx.err_at(
                "C183",
                format!(
                    "map key type {} does not match {}",
                    type_display(ctx.program, kt),
                    type_display(ctx.program, &Ty::non_null(kty.clone()))
                ),
                entries[i].0.span(),
            );
        }
    }
    for (i, vt) in vtys.iter().enumerate() {
        if !crate::types::is_subtype(vt, &Ty::non_null(vty.clone()), ctx.program) {
            ctx.err_at(
                "C184",
                format!(
                    "map value type {} does not match {}",
                    type_display(ctx.program, vt),
                    type_display(ctx.program, &Ty::non_null(vty.clone()))
                ),
                entries[i].1.span(),
            );
        }
    }
    st.emit(IrInstr::NewMap(entries.len() as u16));
    for (ks, vs) in ktmps.iter().zip(vtmps.iter()) {
        st.emit(IrInstr::LoadLocal(*ks as u16));
        st.emit(IrInstr::LoadLocal(*vs as u16));
        st.emit(IrInstr::Op(IrOp::MapPut));
    }
    Ty::non_null(BaseType::Map(Box::new(kty), Box::new(vty)))
}

fn check_binary(ctx: &mut Ctx<'_>, st: &mut FnState, b: &BinaryExpr) -> Ty {
    match b.op {
        BinOp::And => check_logical(ctx, st, b, true),
        BinOp::Or => check_logical(ctx, st, b, false),
        BinOp::Eq | BinOp::Ne => {
            let lt = check_expr(ctx, st, &b.left);
            let rt = check_expr(ctx, st, &b.right);
            check_comparable(ctx, st, &lt, &rt, b.span);
            emit_eq(ctx, st, &lt, &rt, b.span);
            if b.op == BinOp::Ne {
                st.emit(IrInstr::Op(IrOp::Not));
            }
            Ty::bool_()
        }
        BinOp::Lt | BinOp::Le | BinOp::Gt | BinOp::Ge => {
            let lt = check_expr(ctx, st, &b.left);
            let rt = check_expr(ctx, st, &b.right);
            check_orderable(ctx, st, &lt, &rt, b.span);
            emit_ord(ctx, st, b.op, &lt, &rt, b.span);
            Ty::bool_()
        }
        BinOp::Add | BinOp::Sub | BinOp::Mul | BinOp::Div | BinOp::Mod => {
            let lt = check_expr(ctx, st, &b.left);
            let rt = check_expr(ctx, st, &b.right);
            if b.op == BinOp::Add && (is_string(ctx, st, &lt) || is_string(ctx, st, &rt)) {
                // String concatenation via `..` is handled in Concat;
                // `+` on strings is an error.
                ctx.err_at("C154", "use '..' for string concatenation", b.span);
                return Ty::string();
            }
            let num = numeric_type(ctx, st, &lt, &rt, b.span);
            if num.is_none() {
                return Ty::object();
            }
            let num = num.unwrap();
            emit_bin_arith(ctx, st, b.op, &num, b.span);
            num
        }
        BinOp::Concat => {
            let lt = check_expr(ctx, st, &b.left);
            let rt = check_expr(ctx, st, &b.right);
            if matches!(lt.base, BaseType::Void | BaseType::Range)
                || matches!(rt.base, BaseType::Void | BaseType::Range)
            {
                ctx.err_at("C155", "'..' requires value operands", b.span);
                Ty::object()
            } else if is_string(ctx, st, &lt) || is_string(ctx, st, &rt) {
                st.emit(IrInstr::Op(IrOp::StrConcat));
                Ty::string()
            } else if matches!(lt.base, BaseType::Long | BaseType::Byte)
                && matches!(rt.base, BaseType::Long | BaseType::Byte)
            {
                // Integer range.
                Ty::non_null(BaseType::Range)
            } else {
                ctx.err_at(
                    "C155",
                    "'..' requires a String operand or two Integers",
                    b.span,
                );
                Ty::object()
            }
        }
    }
}

fn is_string(_ctx: &mut Ctx<'_>, _st: &mut FnState, t: &Ty) -> bool {
    matches!(t.base, BaseType::String)
}

fn check_comparable(
    ctx: &mut Ctx<'_>,
    st: &mut FnState,
    lt: &Ty,
    rt: &Ty,
    span: crate::source::Span,
) {
    if !types_compatible(ctx, st, lt, rt) {
        ctx.err_at(
            "C156",
            format!(
                "cannot compare {} with {}",
                type_display(ctx.program, lt),
                type_display(ctx.program, rt)
            ),
            span,
        );
    }
}

fn check_orderable(
    ctx: &mut Ctx<'_>,
    st: &mut FnState,
    lt: &Ty,
    rt: &Ty,
    span: crate::source::Span,
) {
    check_comparable(ctx, st, lt, rt, span);
    let ok = matches!(
        lt.base,
        BaseType::Long
            | BaseType::Byte
            | BaseType::Double
            | BaseType::Char
            | BaseType::String
            | BaseType::Object
    );
    if !ok {
        ctx.err_at(
            "C157",
            format!("type {} is not orderable", type_display(ctx.program, lt)),
            span,
        );
    }
}

/// Equality is allowed when the bases are identical, one side is a null
/// literal type, or either side is Object.
fn types_compatible(_ctx: &mut Ctx<'_>, _st: &mut FnState, lt: &Ty, rt: &Ty) -> bool {
    if matches!(lt.base, BaseType::Null) || matches!(rt.base, BaseType::Null) {
        return true;
    }
    if matches!(lt.base, BaseType::Object) || matches!(rt.base, BaseType::Object) {
        return true;
    }
    if lt.base == rt.base {
        return true;
    }
    // Numeric widening.
    matches!(
        (&lt.base, &rt.base),
        (BaseType::Byte, BaseType::Long)
            | (BaseType::Long, BaseType::Byte)
            | (BaseType::Byte, BaseType::Double)
            | (BaseType::Double, BaseType::Byte)
            | (BaseType::Long, BaseType::Double)
            | (BaseType::Double, BaseType::Long)
    )
}

/// Pick the arithmetic result type for numeric operands.
fn numeric_type(
    ctx: &mut Ctx<'_>,
    _st: &mut FnState,
    lt: &Ty,
    rt: &Ty,
    span: crate::source::Span,
) -> Option<Ty> {
    let both = |b: &BaseType| matches!(b, BaseType::Long | BaseType::Byte | BaseType::Double);
    if both(&lt.base) && both(&rt.base) {
        if matches!(lt.base, BaseType::Double) || matches!(rt.base, BaseType::Double) {
            Some(Ty::double())
        } else {
            Some(Ty::long())
        }
    } else {
        ctx.err_at(
            "C158",
            format!(
                "arithmetic requires numeric operands, found {} and {}",
                type_display(ctx.program, lt),
                type_display(ctx.program, rt)
            ),
            span,
        );
        None
    }
}

fn emit_bin_arith(
    _ctx: &mut Ctx<'_>,
    st: &mut FnState,
    op: BinOp,
    ty: &Ty,
    span: crate::source::Span,
) {
    let o = if matches!(ty.base, BaseType::Double) {
        match op {
            BinOp::Add => IrOp::AddDouble,
            BinOp::Sub => IrOp::SubDouble,
            BinOp::Mul => IrOp::MulDouble,
            BinOp::Div => IrOp::DivDouble,
            BinOp::Mod => IrOp::ModDouble,
            _ => unreachable!(),
        }
    } else {
        match op {
            BinOp::Add => IrOp::AddLong,
            BinOp::Sub => IrOp::SubLong,
            BinOp::Mul => IrOp::MulLong,
            BinOp::Div => IrOp::DivLong,
            BinOp::Mod => IrOp::ModLong,
            _ => unreachable!(),
        }
    };
    let _ = span;
    st.emit(IrInstr::Op(o));
}

fn emit_eq(_ctx: &mut Ctx<'_>, st: &mut FnState, lt: &Ty, rt: &Ty, span: crate::source::Span) {
    let _ = span;
    // Nullable operands need null-safe content equality.
    if lt.nullable
        || rt.nullable
        || matches!(lt.base, BaseType::Null)
        || matches!(rt.base, BaseType::Null)
    {
        st.emit(IrInstr::Op(IrOp::EqObject));
        return;
    }
    // Mixed Long/Byte/Double compares promote to Double (the VM accepts Long
    // operands on float ops), matching numeric widening.
    let o = if numeric_cmp_float(&lt.base, &rt.base) {
        IrOp::EqDouble
    } else {
        match lt.base {
            BaseType::Long | BaseType::Byte => IrOp::EqLong,
            BaseType::Double => IrOp::EqDouble,
            BaseType::Bool => IrOp::EqBool,
            BaseType::Char => IrOp::EqChar,
            BaseType::String => IrOp::EqString,
            BaseType::Null => IrOp::EqDyn,
            BaseType::Object => IrOp::EqDyn,
            BaseType::Enum(_, _) => IrOp::EqEnum,
            BaseType::Class(_, _) | BaseType::Interface(_, _) => IrOp::EqObject,
            _ => IrOp::EqDyn,
        }
    };
    st.emit(IrInstr::Op(o));
}

fn emit_ord(
    _ctx: &mut Ctx<'_>,
    st: &mut FnState,
    op: BinOp,
    lt: &Ty,
    rt: &Ty,
    span: crate::source::Span,
) {
    let _ = span;
    let o = if numeric_cmp_float(&lt.base, &rt.base) {
        match op {
            BinOp::Lt => IrOp::LtDouble,
            BinOp::Le => IrOp::LeDouble,
            BinOp::Gt => IrOp::GtDouble,
            BinOp::Ge => IrOp::GeDouble,
            _ => unreachable!(),
        }
    } else {
        match lt.base {
            BaseType::Long | BaseType::Byte => match op {
                BinOp::Lt => IrOp::LtLong,
                BinOp::Le => IrOp::LeLong,
                BinOp::Gt => IrOp::GtLong,
                BinOp::Ge => IrOp::GeLong,
                _ => unreachable!(),
            },
            BaseType::Double => match op {
                BinOp::Lt => IrOp::LtDouble,
                BinOp::Le => IrOp::LeDouble,
                BinOp::Gt => IrOp::GtDouble,
                BinOp::Ge => IrOp::GeDouble,
                _ => unreachable!(),
            },
            BaseType::Char => match op {
                BinOp::Lt => IrOp::LtChar,
                BinOp::Le => IrOp::LeChar,
                BinOp::Gt => IrOp::GtChar,
                BinOp::Ge => IrOp::GeChar,
                _ => unreachable!(),
            },
            BaseType::String => match op {
                BinOp::Lt => IrOp::LtString,
                BinOp::Le => IrOp::LeString,
                BinOp::Gt => IrOp::GtString,
                BinOp::Ge => IrOp::GeString,
                _ => unreachable!(),
            },
            _ => match op {
                BinOp::Lt => IrOp::LtDyn,
                BinOp::Le => IrOp::LeDyn,
                BinOp::Gt => IrOp::GtDyn,
                BinOp::Ge => IrOp::GeDyn,
                _ => unreachable!(),
            },
        }
    };
    st.emit(IrInstr::Op(o));
}

/// Whether a comparison between `lt` and `rt` must use Double opcodes:
/// both sides are numeric and at least one is a Double.
fn numeric_cmp_float(lt: &BaseType, rt: &BaseType) -> bool {
    let num = |b: &BaseType| matches!(b, BaseType::Long | BaseType::Byte | BaseType::Double);
    num(lt) && num(rt) && (matches!(lt, BaseType::Double) || matches!(rt, BaseType::Double))
}

fn check_logical(ctx: &mut Ctx<'_>, st: &mut FnState, b: &BinaryExpr, is_and: bool) -> Ty {
    // Layout:
    //   left
    //   J1 -> SIDE
    //   right
    //   J2 -> SIDE
    //   LoadConst(mid)      // and: true, or: false
    //   J3 -> MERGE
    // SIDE: LoadConst(side) // and: false, or: true
    // MERGE:
    check_cond(ctx, st, &b.left);
    let first = if is_and {
        st.emit(IrInstr::JumpIfFalse(0))
    } else {
        st.emit(IrInstr::JumpIfTrue(0))
    };
    check_cond(ctx, st, &b.right);
    let second = if is_and {
        st.emit(IrInstr::JumpIfFalse(0))
    } else {
        st.emit(IrInstr::JumpIfTrue(0))
    };
    let mid_const = is_and;
    let c1 = ctx.ir.intern_const(crate::ir::IrConst::Bool(mid_const));
    st.emit(IrInstr::LoadConst(c1));
    let to_merge = st.emit(IrInstr::Jump(0));
    let side_ip = st.func.instrs.len() as u32;
    let side_const = !is_and;
    let c2 = ctx.ir.intern_const(crate::ir::IrConst::Bool(side_const));
    st.emit(IrInstr::LoadConst(c2));
    let merge_ip = st.func.instrs.len() as u32;
    st.patch(first, side_ip);
    st.patch(second, side_ip);
    st.patch(to_merge, merge_ip);
    Ty::bool_()
}

fn check_unary(ctx: &mut Ctx<'_>, st: &mut FnState, op: UnaryOp, inner: &Expr) -> Ty {
    let t = check_expr(ctx, st, inner);
    match op {
        UnaryOp::Neg => {
            if matches!(t.base, BaseType::Long | BaseType::Byte) {
                st.emit(IrInstr::Op(IrOp::NegLong));
                Ty::long()
            } else if matches!(t.base, BaseType::Double) {
                st.emit(IrInstr::Op(IrOp::NegDouble));
                Ty::double()
            } else {
                ctx.err_at("C159", "unary '-' requires a numeric operand", inner.span());
                Ty::object()
            }
        }
        UnaryOp::Not => {
            if !matches!(t.base, BaseType::Bool) {
                ctx.err_at("C160", "unary '!' requires a Bool operand", inner.span());
                return Ty::object();
            }
            if t.nullable {
                st.emit(IrInstr::Op(IrOp::NullCheck));
            }
            st.emit(IrInstr::Op(IrOp::Not));
            Ty::bool_()
        }
    }
}

fn check_coalesce(ctx: &mut Ctx<'_>, st: &mut FnState, l: &Expr, r: &Expr) -> Ty {
    let lt = check_expr(ctx, st, l);
    // Keep the left value alive across the null test.
    st.emit(IrInstr::Op(IrOp::Dup));
    st.emit(IrInstr::Op(IrOp::IsNull));
    let take_right = st.emit(IrInstr::JumpIfTrue(0));
    let end_jump = st.emit(IrInstr::Jump(0));
    st.patch(take_right, st.func.instrs.len() as u32);
    // Left was null: drop it, then evaluate the right side.
    st.emit(IrInstr::Op(IrOp::Pop));
    let rt = check_expr(ctx, st, r);
    st.patch(end_jump, st.func.instrs.len() as u32);
    // Result type: left non-null widened with right.
    let left_nn = Ty::new(lt.base.clone(), false);
    if crate::types::is_subtype(&left_nn, &rt, ctx.program) {
        rt
    } else if crate::types::is_subtype(&rt, &left_nn, ctx.program) {
        left_nn
    } else {
        Ty::object()
    }
}

fn check_range(ctx: &mut Ctx<'_>, st: &mut FnState, l: &Expr, r: &Expr) -> Ty {
    let lt = check_expr(ctx, st, l);
    let rt = check_expr(ctx, st, r);
    if !matches!(lt.base, BaseType::Long | BaseType::Byte)
        || !matches!(rt.base, BaseType::Long | BaseType::Byte)
    {
        ctx.err_at("C161", "range bounds must be integers", l.span());
        return Ty::object();
    }
    Ty::non_null(BaseType::Range)
}

/// Static field resolution: returns (slot, field type) when `name` is a
/// field of the receiver class accessible from the current class.
fn resolve_field(
    ctx: &mut Ctx<'_>,
    st: &mut FnState,
    recv_ty: &Ty,
    name: &str,
) -> Option<(u16, Ty, bool)> {
    let (cid, args) = match &recv_ty.base {
        BaseType::Class(c, a) => (*c, a),
        _ => return None,
    };
    let info = &ctx.program.classes[cid as usize];
    let (slot, f) = info
        .fields
        .iter()
        .enumerate()
        .find(|(_, f)| f.name == name)?;
    // Encapsulation: public fields are open; otherwise only the
    // declaring class may touch its fields.
    if !f.is_pub && st.class_id != Some(f.declaring) {
        return None;
    }
    let mut ty = f.ty.clone();
    // Erase the class's own type variables to Object for storage types.
    let subst: Vec<Option<BaseType>> = args.iter().map(|a| Some(a.clone())).collect();
    ty = ty.substitute(&subst);
    Some((slot as u16, ty, f.mutable))
}

fn check_member(ctx: &mut Ctx<'_>, st: &mut FnState, m: &MemberExpr) -> Ty {
    let recv_ty = check_expr(ctx, st, &m.obj);
    if recv_ty.nullable {
        st.emit(IrInstr::Op(IrOp::NullCheck));
    }
    if let Some((slot, fty, _)) = resolve_field(ctx, st, &recv_ty, &m.name) {
        st.emit(IrInstr::LoadField(slot));
        return fty;
    }
    // Distinguish privacy violation from unknown member for diagnostics.
    if let BaseType::Class(cid, _) = &recv_ty.base {
        let info = &ctx.program.classes[*cid as usize];
        if info.fields.iter().any(|f| f.name == m.name) {
            let owner = info
                .fields
                .iter()
                .find(|f| f.name == m.name)
                .unwrap()
                .declaring;
            ctx.err_at(
                "C162",
                format!(
                    "field '{}' is private to class '{}'",
                    m.name,
                    ctx.program.class_name(owner)
                ),
                m.span,
            );
            return Ty::object();
        }
    }
    ctx.err_at(
        "C151",
        format!("member '{}' must be a method call", m.name),
        m.span,
    );
    Ty::object()
}

fn check_call(ctx: &mut Ctx<'_>, st: &mut FnState, c: &CallExpr) -> Ty {
    match &*c.callee {
        Expr::StaticAccess(sa) => call_static(ctx, st, sa, c),
        Expr::Member(m) => call_member(ctx, st, m, c),
        Expr::Ident(name) => {
            // Unqualified method call on self.
            if !st.is_static {
                if let Some(self_slot) = st.lookup_local("self") {
                    let self_ty = st.local_type("self").unwrap_or(Ty::object());
                    let is_vtable = matches!(&self_ty.base, BaseType::Class(c2, _) if ctx.program.classes[*c2 as usize].vtable_names.iter().any(|v| v == name));
                    let is_object = matches!(self_ty.base, BaseType::Object);
                    let is_iface = matches!(&self_ty.base, BaseType::Interface(i2, _) if ctx.program.interfaces[*i2 as usize].slots.iter().any(|sl| sl.name == *name));
                    if is_vtable || is_object || is_iface {
                        st.emit(IrInstr::LoadLocal(self_slot as u16));
                        return dispatch_method(ctx, st, self_ty, name, c.span, c);
                    }
                }
            }
            ctx.err_at("C163", "expression is not callable", c.span);
            Ty::object()
        }
        other => {
            ctx.err_at("C163", "expression is not callable", c.span);
            check_expr(ctx, st, other);
            for a in &c.args {
                check_expr(ctx, st, &a.expr);
                st.emit(IrInstr::Op(IrOp::Pop));
            }
            Ty::object()
        }
    }
}

fn is_builtin_namespace(name: &str) -> bool {
    matches!(
        name,
        "Math" | "Type" | "Base64" | "Hash" | "Json" | "Time" | "Random" | "File" | "Test"
    )
}

fn call_static(ctx: &mut Ctx<'_>, st: &mut FnState, sa: &StaticAccessExpr, c: &CallExpr) -> Ty {
    // Built-in static namespaces (Math.sqrt, Type.of, File.read, ...).
    if let TypeBase::Named(n) = &sa.ty.base {
        if is_builtin_namespace(n) {
            return match builtins::static_member(n, &sa.name) {
                Some(sig) => {
                    let recv = Ty::object();
                    check_builtin_args(ctx, st, &sig, n, &sa.name, c, &recv);
                    st.emit(IrInstr::CallNative(sig.native, c.args.len() as u16));
                    instantiate_builtin_ret(ctx, st, &sig, &recv)
                }
                None => {
                    ctx.err_at(
                        "C168",
                        format!("no static member '{}' on {}", sa.name, n),
                        sa.span,
                    );
                    Ty::object()
                }
            };
        }
    }
    let base = resolve_type_ref(
        ctx.diags,
        ctx.program,
        &sa.ty,
        &self_type_params(st),
        st.class_id,
        sa.span,
    );
    match &base.base {
        BaseType::Class(cid, args) => {
            match ctx.program.classes[*cid as usize].find_static_method(ctx.program, *cid, &sa.name)
            {
                Some((didx, midx)) => {
                    let dinfo = &ctx.program.classes[didx as usize];
                    let m = &dinfo.methods[midx];
                    let fid = compile_template(
                        ctx.program,
                        ctx.sources,
                        ctx.diags,
                        ctx.ir,
                        ctx.fn_ids,
                        Template::Class(didx, midx),
                    );
                    // Infer missing class type arguments from actual args.
                    let n_tp = dinfo.type_params.len();
                    let mut class_args = args.clone();
                    while class_args.len() < n_tp {
                        class_args.push(BaseType::Object);
                    }
                    if class_args.iter().any(|a| matches!(a, BaseType::TypeVar(_))) {
                        let mut subst: Vec<Option<BaseType>> = class_args
                            .iter()
                            .map(|a| {
                                if matches!(a, BaseType::TypeVar(_)) {
                                    None
                                } else {
                                    Some(a.clone())
                                }
                            })
                            .collect();
                        subst.extend(m.type_params.iter().map(|_| None));
                        let pnames: Vec<String> = m.params.iter().map(|p| p.name.clone()).collect();
                        let mut pos_count = 0usize;
                        for a in &c.args {
                            if a.name.is_some() {
                                break;
                            }
                            pos_count += 1;
                        }
                        for (i, p) in m.params.iter().enumerate() {
                            if let Some((expr, spread)) =
                                arg_source(ctx, st, i, pos_count, &pnames, c)
                            {
                                if !spread {
                                    let t = expr_probe_type(ctx, st, expr);
                                    unify(ctx, st, &p.ty.base, &t.base, &mut subst);
                                }
                            }
                        }
                        for s2 in subst.iter_mut() {
                            if s2.is_none() {
                                *s2 = Some(BaseType::Object);
                            }
                        }
                        class_args = subst.into_iter().take(n_tp).map(|o| o.unwrap()).collect();
                    } else {
                        // No inference needed; drop padding.
                        class_args.truncate(n_tp);
                    }
                    let (arg_tys, n_args) = match_args(ctx, st, m, &class_args, c);
                    // Self rebinding: a factory declared in an ancestor
                    // and invoked on a subclass constructs the subclass.
                    let ret = m.return_ty.clone().unwrap_or(Ty::void());
                    let ret = if let BaseType::Class(dc, darefs) = &ret.base {
                        if *dc == didx && didx != *cid {
                            Ty::new(BaseType::Class(*cid, class_args.clone()), ret.nullable)
                        } else {
                            let mut subst: Vec<Option<BaseType>> =
                                class_args.iter().map(|a| Some(a.clone())).collect();
                            subst.extend(darefs.iter().map(|_| None));
                            ret.substitute(&subst)
                        }
                    } else {
                        ret
                    };
                    st.emit(IrInstr::CallStatic(fid, n_args as u16, *cid as u16));
                    let _ = arg_tys;
                    ret
                }
                None => {
                    ctx.err_at(
                        "C164",
                        format!(
                            "no static method '{}' on {}",
                            sa.name,
                            type_display(ctx.program, &base)
                        ),
                        sa.span,
                    );
                    Ty::object()
                }
            }
        }
        BaseType::Enum(eid, args) => {
            // Enum variant construction: Color.red / Shape.rect(w, h)
            let einfo = &ctx.program.enums[*eid as usize];
            match einfo.variants.iter().position(|v| v.name == sa.name) {
                Some(vi) => {
                    let payload = &einfo.variants[vi].payload;
                    let n_args = c.args.len();
                    match payload {
                        Some(_) if n_args != 1 => {
                            ctx.err_at(
                                "C165",
                                format!("variant '{}' takes exactly one payload value", sa.name),
                                sa.span,
                            );
                        }
                        None if n_args != 0 => {
                            ctx.err_at(
                                "C165",
                                format!("variant '{}' takes no payload", sa.name),
                                sa.span,
                            );
                        }
                        _ => {}
                    }
                    if let Some(pt) = payload {
                        if n_args == 1 {
                            let at = check_expr(ctx, st, &c.args[0].expr);
                            let subst: Vec<Option<BaseType>> =
                                args.iter().map(|a| Some(a.clone())).collect();
                            let pt2 = pt.substitute(&subst);
                            if !crate::types::is_subtype(&at, &pt2, ctx.program) {
                                ctx.err_at(
                                    "C166",
                                    format!(
                                        "payload type {} does not match expected {}",
                                        type_display(ctx.program, &at),
                                        type_display(ctx.program, &pt2)
                                    ),
                                    c.args[0].span,
                                );
                            }
                        }
                    }
                    st.emit(IrInstr::NewEnum(*eid as u16, vi as u8, payload.is_some()));
                    Ty::non_null(BaseType::Enum(*eid, args.clone()))
                }
                None => {
                    ctx.err_at(
                        "C167",
                        format!(
                            "no variant '{}' on enum {}",
                            sa.name,
                            type_display(ctx.program, &base)
                        ),
                        sa.span,
                    );
                    Ty::object()
                }
            }
        }
        _ => {
            // Built-in static namespace (Math.sqrt, Long.from, ...).
            let tname = builtin_name_of(ctx.program, &base.base);
            match builtins::static_member(&tname, &sa.name) {
                Some(sig) => {
                    check_builtin_args(ctx, st, &sig, &tname, &sa.name, c, &base);
                    st.emit(IrInstr::CallNative(sig.native, c.args.len() as u16));
                    instantiate_builtin_ret(ctx, st, &sig, &base)
                }
                None => {
                    ctx.err_at(
                        "C168",
                        format!("no static member '{}' on {}", sa.name, tname),
                        sa.span,
                    );
                    Ty::object()
                }
            }
        }
    }
}

fn call_member(ctx: &mut Ctx<'_>, st: &mut FnState, m: &MemberExpr, c: &CallExpr) -> Ty {
    let recv_ty = check_expr(ctx, st, &m.obj);
    if recv_ty.nullable {
        st.emit(IrInstr::Op(IrOp::NullCheck));
    }
    dispatch_method(ctx, st, recv_ty, &m.name, m.span, c)
}

fn dispatch_method(
    ctx: &mut Ctx<'_>,
    st: &mut FnState,
    recv_ty: Ty,
    name: &str,
    span: crate::source::Span,
    c: &CallExpr,
) -> Ty {
    match &recv_ty.base {
        BaseType::Class(cid, args) => {
            let program = ctx.program;
            match program.classes[*cid as usize].find_instance_method(program, *cid, name) {
                Some((didx, midx)) => {
                    let dinfo = &program.classes[didx as usize];
                    let mdef = &dinfo.methods[midx];
                    if mdef.is_static {
                        ctx.err_at(
                            "C169",
                            format!("'{}' is static; use {}.{}(...)", name, dinfo.name, name),
                            span,
                        );
                        return Ty::object();
                    }
                    let slot = match dinfo.vtable_slot(name) {
                        Some(s) => s,
                        None => {
                            ctx.err_at(
                                "C170",
                                format!("method '{}' not found in vtable of {}", name, dinfo.name),
                                span,
                            );
                            return Ty::object();
                        }
                    };
                    let _fid = compile_template(
                        ctx.program,
                        ctx.sources,
                        ctx.diags,
                        ctx.ir,
                        ctx.fn_ids,
                        Template::Class(didx, midx),
                    );
                    let (_arg_tys, n_args) = match_args(ctx, st, mdef, args, c);
                    let ret = mdef.return_ty.clone().unwrap_or(Ty::void());
                    let subst: Vec<Option<BaseType>> =
                        args.iter().map(|a| Some(a.clone())).collect();
                    st.emit(IrInstr::CallVirtual(*cid as u16, slot, n_args as u16));
                    ret.substitute(&subst)
                }
                None => {
                    // Universal toString fallback.
                    if name == "toString" {
                        st.emit(IrInstr::CallNative(builtins::nat::TO_STRING, 0));
                        return Ty::string();
                    }
                    // Interface default methods are callable through the
                    // concrete class type as well.
                    let mut found: Option<(u32, IfaceImpl)> = None;
                    for iid in &program.classes[*cid as usize].all_interfaces {
                        if let Some(impl_) = interface_impl_for(program, *iid, name) {
                            found = Some((*iid, impl_));
                            break;
                        }
                    }
                    match found {
                        Some((iid, (template, params, ret))) => {
                            let slot = program.interfaces[iid as usize]
                                .slots
                                .iter()
                                .position(|s| s.name == name)
                                .unwrap();
                            compile_template(
                                ctx.program,
                                ctx.sources,
                                ctx.diags,
                                ctx.ir,
                                ctx.fn_ids,
                                template,
                            );
                            let (_arg_tys, n_args) =
                                match_args_with(ctx, st, &params, ret.as_ref(), c);
                            st.emit(IrInstr::CallInterface(
                                iid as u16,
                                slot as u16,
                                n_args as u16,
                            ));
                            ret.clone().unwrap_or(Ty::void())
                        }
                        None => {
                            ctx.err_at(
                                "C171",
                                format!(
                                    "no method '{}' on {}",
                                    name,
                                    type_display(ctx.program, &recv_ty)
                                ),
                                span,
                            );
                            Ty::object()
                        }
                    }
                }
            }
        }
        BaseType::Interface(iid, _args) => {
            // Built-in interfaces expose native instance methods.
            if *iid < crate::resolve::builtin::BUILTIN_COUNT as u32 {
                let bname = builtin_name_of(ctx.program, &BaseType::Interface(*iid, vec![]));
                if let Some(sig) = builtins::instance_method(&bname, name) {
                    check_builtin_args(ctx, st, &sig, &bname, name, c, &recv_ty);
                    st.emit(IrInstr::CallNative(sig.native, c.args.len() as u16));
                    return instantiate_builtin_ret(ctx, st, &sig, &recv_ty);
                }
            }
            let iface = &ctx.program.interfaces[*iid as usize];
            match iface.slots.iter().position(|s| s.name == name) {
                Some(slot) => {
                    // Find the concrete/default implementation signature.
                    let impl_fn = interface_impl_for(ctx.program, *iid, name);
                    match impl_fn {
                        Some((template, params, ret)) => {
                            let _fid = compile_template(
                                ctx.program,
                                ctx.sources,
                                ctx.diags,
                                ctx.ir,
                                ctx.fn_ids,
                                template,
                            );
                            let (_arg_tys, n_args) =
                                match_args_with(ctx, st, &params, ret.as_ref(), c);
                            st.emit(IrInstr::CallInterface(
                                *iid as u16,
                                slot as u16,
                                n_args as u16,
                            ));
                            ret.unwrap_or(Ty::void())
                        }
                        None => {
                            ctx.err_at(
                                "C172",
                                format!("interface method '{}' has no implementation", name),
                                span,
                            );
                            Ty::object()
                        }
                    }
                }
                None => {
                    if name == "toString" {
                        st.emit(IrInstr::CallNative(builtins::nat::TO_STRING, 0));
                        Ty::string()
                    } else {
                        ctx.err_at(
                            "C173",
                            format!(
                                "interface {} has no method '{}'",
                                ctx.program.interface_name(*iid),
                                name
                            ),
                            span,
                        );
                        Ty::object()
                    }
                }
            }
        }
        BaseType::Enum(_, _) => {
            if name == "toString" {
                st.emit(IrInstr::CallNative(builtins::nat::TO_STRING, 0));
                Ty::string()
            } else {
                ctx.err_at(
                    "C174",
                    format!("enums have no methods (found '{}')", name),
                    span,
                );
                Ty::object()
            }
        }
        BaseType::Object => {
            // Dynamic dispatch on Object-typed receivers.
            if name == "toString" {
                st.emit(IrInstr::CallNative(builtins::nat::TO_STRING, 0));
                return Ty::string();
            }
            let name_id = ctx.ir.intern_dyn_name(name);
            st.emit(IrInstr::CallDynamic(name_id, c.args.len() as u16));
            Ty::object()
        }
        other => {
            // Built-in instance methods.
            let tname = builtin_name_of(ctx.program, other);
            match builtins::instance_method(&tname, name) {
                Some(sig) => {
                    check_builtin_args(ctx, st, &sig, &tname, name, c, &recv_ty);
                    st.emit(IrInstr::CallNative(sig.native, c.args.len() as u16));
                    instantiate_builtin_ret(ctx, st, &sig, &recv_ty)
                }
                None => {
                    if name == "toString" {
                        st.emit(IrInstr::CallNative(builtins::nat::TO_STRING, 0));
                        Ty::string()
                    } else {
                        ctx.err_at("C175", format!("no method '{}' on {}", name, tname), span);
                        Ty::object()
                    }
                }
            }
        }
    }
}

/// Lower call arguments for a user method: positional + named binding,
/// defaults, variadics, spread, and generic inference. Emits argument
/// values in parameter declaration order.
fn match_args(
    ctx: &mut Ctx<'_>,
    st: &mut FnState,
    m: &MethodInfo,
    class_args: &[BaseType],
    c: &CallExpr,
) -> (Vec<Ty>, usize) {
    // Substitution table: class type vars first (indices 0..class_n),
    // then method type vars.
    let mut subst: Vec<Option<BaseType>> = class_args.iter().map(|a| Some(a.clone())).collect();
    subst.extend(m.type_params.iter().map(|_| None));
    lower_call_args(ctx, st, m, &mut subst, c)
}

fn match_args_with(
    ctx: &mut Ctx<'_>,
    st: &mut FnState,
    params: &[ParamInfo],
    _ret: Option<&Ty>,
    c: &CallExpr,
) -> (Vec<Ty>, usize) {
    let mut empty: Vec<Option<BaseType>> = vec![];
    let fake = MethodInfo {
        name: String::new(),
        visibility: Visibility::Public,
        is_static: false,
        is_override: false,
        type_params: vec![],
        params: params.to_vec(),
        return_ty: None,
        body: None,
        span: crate::source::Span::new(0, 0, 0),
    };
    lower_call_args(ctx, st, &fake, &mut empty, c)
}

fn lower_call_args(
    ctx: &mut Ctx<'_>,
    st: &mut FnState,
    m: &MethodInfo,
    subst: &mut [Option<BaseType>],
    c: &CallExpr,
) -> (Vec<Ty>, usize) {
    let params = &m.params;
    // Split positional / named.
    let mut pos_count = 0usize;
    for a in &c.args {
        if a.name.is_some() {
            break;
        }
        pos_count += 1;
    }
    let named_start = pos_count;
    // Validate named args.
    let mut named_idx: HashMap<String, usize> = HashMap::new();
    for a in &c.args[named_start..] {
        let name = a.name.as_deref().unwrap_or("");
        match params.iter().position(|p| p.name == name) {
            Some(i) => {
                if named_idx.insert(name.to_string(), i).is_some() {
                    ctx.err_at(
                        "C176",
                        format!("duplicate named argument '{}'", name),
                        a.span,
                    );
                }
            }
            None => {
                ctx.err_at("C177", format!("unknown named argument '{}'", name), a.span);
            }
        }
    }
    let n_params = params.len();
    let variadic = n_params > 0 && params[n_params - 1].variadic;
    let required_end = if variadic { n_params - 1 } else { n_params };
    if pos_count > n_params && !variadic {
        ctx.err_at(
            "C178",
            format!("too many arguments (expected at most {})", n_params),
            c.span,
        );
    }
    if variadic && pos_count < required_end.saturating_sub(0) {
        // Variadic may absorb the rest; only error if fewer than fixed params
        // and no named supply.
    }
    let param_names: Vec<String> = params.iter().map(|p| p.name.clone()).collect();
    // Infer generics from provided arguments.
    for (i, p) in params.iter().enumerate() {
        if p.variadic {
            let elem_ty = p.ty.substitute(subst);
            let tail_start = required_end.min(pos_count);
            for arg in c.args[tail_start..pos_count].iter() {
                if arg.name.is_none() && !arg.spread {
                    let t = expr_probe_type(ctx, st, &arg.expr);
                    unify(ctx, st, &elem_ty.base, &t.base, subst);
                }
            }
            continue;
        }
        let src = arg_source(ctx, st, i, pos_count, &param_names, c);
        if let Some((expr, spread)) = src {
            if !spread {
                let t = expr_probe_type(ctx, st, expr);
                unify(ctx, st, &p.ty.base, &t.base, subst);
            }
        }
    }
    // Unresolved vars erase to Object.
    for s in subst.iter_mut() {
        if s.is_none() {
            *s = Some(BaseType::Object);
        }
    }
    // Constraint checking.
    for (i, tp) in m.type_params.iter().enumerate() {
        let inferred = subst[i].clone().unwrap_or(BaseType::Object);
        for cref in &tp.constraints {
            let ctype = resolve_type_ref(
                ctx.diags,
                ctx.program,
                cref,
                &param_names_of(m),
                None,
                cref.span,
            );
            if !conforms(ctx, st, &inferred, &ctype.base) {
                ctx.err_at(
                    "C179",
                    format!(
                        "type {} does not satisfy constraint {}",
                        type_display(ctx.program, &Ty::non_null(inferred.clone())),
                        type_display(ctx.program, &ctype)
                    ),
                    cref.span,
                );
            }
        }
    }
    // Emit arguments in declaration order. Track how many argument values
    // land on the stack (defaults included) so the call can pop exactly that
    // many.
    let mut out = vec![];
    let mut emitted = 0usize;
    for (i, p) in params.iter().enumerate() {
        if p.variadic {
            let elem_ty = p.ty.substitute(subst);
            st.emit(IrInstr::NewList(0));
            let tail_start = required_end.min(pos_count);
            for arg in c.args[tail_start..pos_count].iter() {
                let t = check_expr(ctx, st, &arg.expr);
                if arg.spread {
                    match &t.base {
                        BaseType::List(e) => {
                            let et = Ty::non_null(e.as_ref().substitute(subst));
                            if !crate::types::is_subtype(&et, &elem_ty, ctx.program) {
                                ctx.err_at(
                                    "C181",
                                    format!(
                                        "spread element type {} does not match parameter {}",
                                        type_display(ctx.program, &et),
                                        type_display(ctx.program, &elem_ty)
                                    ),
                                    arg.expr.span(),
                                );
                            }
                            st.emit(IrInstr::Op(IrOp::ListExtend));
                        }
                        _ => {
                            ctx.err_at("C182", "spread requires a List value", arg.expr.span());
                        }
                    }
                } else {
                    if !crate::types::is_subtype(&t, &elem_ty, ctx.program) {
                        ctx.err_at(
                            "C183",
                            format!(
                                "argument type {} does not match parameter '{}' of type {}",
                                type_display(ctx.program, &t),
                                p.name,
                                type_display(ctx.program, &elem_ty)
                            ),
                            arg.expr.span(),
                        );
                    }
                    st.emit(IrInstr::Op(IrOp::ListAdd));
                }
            }
            out.push(Ty::non_null(BaseType::List(Box::new(elem_ty.base))));
            emitted += 1;
            continue;
        }
        let param_ty = p.ty.substitute(subst);
        let src = arg_source(ctx, st, i, pos_count, &param_names, c);
        match src {
            Some((expr, true)) => {
                // Spread on a non-variadic parameter: error recovery only
                // (variadic parameters are handled by the list-building
                // branch above). Drop the value to keep the stack balanced.
                ctx.err_at(
                    "C180",
                    "spread '...' is only valid for variadic parameters",
                    expr.span(),
                );
                let _t = check_expr(ctx, st, expr);
                st.emit(IrInstr::Op(IrOp::Pop));
                emitted += 1;
                out.push(param_ty);
            }
            Some((expr, false)) => {
                let t = check_expr(ctx, st, expr);
                if !crate::types::is_subtype(&t, &param_ty, ctx.program) {
                    ctx.err_at(
                        "C183",
                        format!(
                            "argument type {} does not match parameter '{}' of type {}",
                            type_display(ctx.program, &t),
                            p.name,
                            type_display(ctx.program, &param_ty)
                        ),
                        expr.span(),
                    );
                }
                emitted += 1;
                out.push(param_ty);
            }
            None => {
                // Omitted: default or missing.
                match &p.default {
                    Some(d) => {
                        let t = check_expr(ctx, st, d);
                        if !crate::types::is_subtype(&t, &param_ty, ctx.program) {
                            ctx.err_at(
                                "C184",
                                format!(
                                    "default value type {} does not match parameter type {}",
                                    type_display(ctx.program, &t),
                                    type_display(ctx.program, &param_ty)
                                ),
                                d.span(),
                            );
                        }
                        emitted += 1;
                        out.push(param_ty);
                    }
                    None => {
                        ctx.err_at(
                            "C185",
                            format!("missing required argument '{}'", p.name),
                            c.span,
                        );
                        let z = ctx.ir.intern_const(crate::ir::IrConst::Null);
                        st.emit(IrInstr::LoadConst(z));
                        emitted += 1;
                        out.push(param_ty);
                    }
                }
            }
        }
    }
    let _ = required_end;
    (out, emitted)
}

/// Source expression for parameter i, if provided.
fn arg_source<'b>(
    _ctx: &mut Ctx<'_>,
    _st: &mut FnState,
    i: usize,
    pos_count: usize,
    param_names: &[String],
    c: &'b CallExpr,
) -> Option<(&'b Expr, bool)> {
    if i < pos_count {
        return Some((&c.args[i].expr, c.args[i].spread));
    }
    for (j, a) in c.args.iter().enumerate() {
        if j < pos_count {
            continue;
        }
        if let Some(n) = &a.name {
            if *n == param_names[i] {
                return Some((&a.expr, a.spread));
            }
        }
    }
    None
}

/// Static type probe without codegen for expressions useful during generic
/// class-argument inference. Complex expressions still conservatively probe
/// as Object because checking them would emit duplicate instructions.
fn expr_probe_type(ctx: &mut Ctx<'_>, st: &mut FnState, e: &Expr) -> Ty {
    match e {
        Expr::Int(_) => Ty::long(),
        Expr::Float(_) => Ty::double(),
        Expr::Bool(_) => Ty::bool_(),
        Expr::Char(_) => Ty::char_(),
        Expr::String(_) => Ty::string(),
        Expr::Null => Ty::null(),
        Expr::Ident(name) => {
            if let Some(t) = st.local_type(name) {
                return t;
            }
            Ty::object()
        }
        Expr::Member(m) if matches!(m.obj.as_ref(), Expr::Ident(name) if name == "self") => {
            let self_ty = st.local_type("self").unwrap_or_else(Ty::object);
            resolve_field(ctx, st, &self_ty, &m.name)
                .map(|(_, field_ty, _)| field_ty)
                .unwrap_or_else(Ty::object)
        }
        _ => Ty::object(),
    }
}

fn unify(
    _ctx: &mut Ctx<'_>,
    _st: &mut FnState,
    target: &BaseType,
    source: &BaseType,
    subst: &mut [Option<BaseType>],
) {
    match target {
        BaseType::TypeVar(i) => {
            let i = *i as usize;
            if i >= subst.len() {
                return;
            }
            match &subst[i] {
                None => subst[i] = Some(source.clone()),
                Some(existing) => {
                    if *existing != *source
                        && *source != BaseType::Object
                        && *existing != BaseType::Object
                    {
                        subst[i] = Some(BaseType::Object);
                    }
                }
            }
        }
        BaseType::List(te) => {
            if let BaseType::List(se) = source {
                unify(_ctx, _st, te, se, subst);
            }
        }
        BaseType::Map(tk, tv) => {
            if let BaseType::Map(sk, sv) = source {
                unify(_ctx, _st, tk, sk, subst);
                unify(_ctx, _st, tv, sv, subst);
            }
        }
        BaseType::Stack(te) => {
            if let BaseType::Stack(se) = source {
                unify(_ctx, _st, te, se, subst);
            }
        }
        BaseType::Set(te) => {
            if let BaseType::Set(se) = source {
                unify(_ctx, _st, te, se, subst);
            }
        }
        _ => {}
    }
}

fn conforms(ctx: &mut Ctx<'_>, _st: &mut FnState, ty: &BaseType, constraint: &BaseType) -> bool {
    match (ty, constraint) {
        (BaseType::Object, _) => true,
        (BaseType::Interface(c, _), BaseType::Interface(req, _)) => {
            ctx.program.interface_extends(*c, *req)
        }
        (BaseType::Class(c, _), BaseType::Interface(req, _)) => {
            ctx.program.class_implements_interface(*c, *req)
        }
        (a, b) => a == b,
    }
}

fn check_builtin_args(
    ctx: &mut Ctx<'_>,
    st: &mut FnState,
    sig: &builtins::BuiltinSig,
    tname: &str,
    method: &str,
    c: &CallExpr,
    recv: &Ty,
) {
    if c.args.iter().any(|a| a.name.is_some()) {
        ctx.err_at(
            "C186",
            format!(
                "built-in {}.{} does not support named arguments",
                tname, method
            ),
            c.span,
        );
    }
    // A trailing nullable-String parameter is an optional message argument
    // (Test.assert / Test.assertEqual); it may be omitted.
    let last_optional = sig
        .params
        .last()
        .is_some_and(|p| p.nullable && matches!(p.base, BaseType::String));
    let min_args = if last_optional {
        sig.params.len().saturating_sub(1)
    } else {
        sig.params.len()
    };
    if c.args.len() < min_args || c.args.len() > sig.params.len() {
        ctx.err_at(
            "C187",
            format!(
                "{}.{} expects {} argument(s), found {}",
                tname,
                method,
                sig.params.len(),
                c.args.len()
            ),
            c.span,
        );
    }
    for (a, pt) in c.args.iter().zip(sig.params.iter()) {
        let t = check_expr(ctx, st, &a.expr);
        if !crate::types::is_subtype(&t, pt, ctx.program) {
            ctx.err_at(
                "C188",
                format!(
                    "{}.{} argument type {} does not match {}",
                    tname,
                    method,
                    type_display(ctx.program, &t),
                    type_display(ctx.program, pt)
                ),
                a.span,
            );
        }
    }
    let _ = recv;
}

/// Instantiate a built-in signature's return type with the receiver's
/// actual type arguments (e.g. List<Long>.get -> Long).
fn instantiate_builtin_ret(
    _ctx: &mut Ctx<'_>,
    _st: &mut FnState,
    sig: &builtins::BuiltinSig,
    recv: &Ty,
) -> Ty {
    let args: Vec<BaseType> = match &recv.base {
        BaseType::List(e) => vec![e.as_ref().clone()],
        BaseType::Map(k, v) => vec![k.as_ref().clone(), v.as_ref().clone()],
        BaseType::Stack(e) | BaseType::Set(e) => vec![e.as_ref().clone()],
        _ => vec![],
    };
    let mut base = substitute_any_args(sig.ret.base.clone(), &args);
    // Element/value accessors are declared as returning Object; refine to
    // the receiver's element (or map value) type.
    if matches!(base, BaseType::Object) {
        base = match &recv.base {
            BaseType::List(e) | BaseType::Stack(e) | BaseType::Set(e) => e.as_ref().clone(),
            BaseType::Map(_, v) => v.as_ref().clone(),
            _ => base,
        };
    }
    Ty {
        base,
        nullable: sig.ret.nullable,
    }
}

fn substitute_any_args(base: BaseType, args: &[BaseType]) -> BaseType {
    match base {
        BaseType::List(e) => {
            let e2 = if matches!(*e, BaseType::Object) {
                args.first().cloned().unwrap_or(BaseType::Object)
            } else {
                *e
            };
            BaseType::List(Box::new(e2))
        }
        BaseType::Map(k, v) => {
            let k2 = if matches!(*k, BaseType::Object) {
                args.first().cloned().unwrap_or(BaseType::Object)
            } else {
                *k
            };
            let v2 = if matches!(*v, BaseType::Object) {
                args.get(1).cloned().unwrap_or(BaseType::Object)
            } else {
                *v
            };
            BaseType::Map(Box::new(k2), Box::new(v2))
        }
        BaseType::Stack(e) => {
            let e2 = if matches!(*e, BaseType::Object) {
                args.first().cloned().unwrap_or(BaseType::Object)
            } else {
                *e
            };
            BaseType::Stack(Box::new(e2))
        }
        BaseType::Set(e) => {
            let e2 = if matches!(*e, BaseType::Object) {
                args.first().cloned().unwrap_or(BaseType::Object)
            } else {
                *e
            };
            BaseType::Set(Box::new(e2))
        }
        other => other,
    }
}

fn param_names_of(m: &MethodInfo) -> Vec<String> {
    m.type_params.iter().map(|p| p.name.clone()).collect()
}

fn check_match(ctx: &mut Ctx<'_>, st: &mut FnState, m: &MatchExpr) -> Ty {
    let subj_ty = check_expr(ctx, st, &m.subject);
    let subj_slot = st.decl_local("$match", subj_ty.clone(), false);
    st.emit(IrInstr::StoreLocal(subj_slot as u16));
    // Exhaustiveness: for enum subjects every variant must be covered.
    if let BaseType::Enum(eid, _) = &subj_ty.base {
        let einfo = &ctx.program.enums[*eid as usize];
        let mut covered = vec![false; einfo.variants.len()];
        let mut has_wildcard = false;
        for arm in &m.arms {
            match &arm.pattern {
                Pattern::Wildcard => has_wildcard = true,
                Pattern::Variant(name, _) => {
                    if let Some(i) = einfo.variants.iter().position(|v| v.name == *name) {
                        covered[i] = true;
                    }
                }
                _ => {}
            }
        }
        if !has_wildcard && !covered.iter().all(|c| *c) {
            let missing: Vec<&str> = einfo
                .variants
                .iter()
                .enumerate()
                .filter(|(i, _)| !covered[*i])
                .map(|(_, v)| v.name.as_str())
                .collect();
            ctx.err_at(
                "C190",
                format!(
                    "match is not exhaustive; missing variants: {}",
                    missing.join(", ")
                ),
                m.span,
            );
        }
    }
    // Layout:
    //   [arm0 test] JIfFalse->A1  bind0 body0 Jump->END
    //   [arm1 test] JIfFalse->A2  bind1 body1 Jump->END
    //   ...
    //   throw "no matching pattern"
    // END:
    let mut arm_starts: Vec<u32> = vec![];
    let mut test_jumps: Vec<usize> = vec![];
    let mut end_jumps: Vec<usize> = vec![];
    let mut result_ty: Option<Ty> = None;
    for arm in &m.arms {
        arm_starts.push(st.func.instrs.len() as u32);
        validate_pattern(ctx, &arm.pattern, &subj_ty, arm.span);
        check_pattern_test(ctx, st, &arm.pattern, subj_slot, &subj_ty);
        test_jumps.push(st.emit(IrInstr::JumpIfFalse(0)));
        st.begin_scope();
        bind_pattern(ctx, st, &arm.pattern, subj_slot, &subj_ty);
        let body_ty = check_expr(ctx, st, &arm.body);
        st.end_scope();
        match &result_ty {
            None => result_ty = Some(body_ty),
            Some(rt) => {
                if rt.base != body_ty.base {
                    ctx.err_at(
                        "C212",
                        format!(
                            "match arms return incompatible types {} and {}",
                            type_display(ctx.program, rt),
                            type_display(ctx.program, &body_ty)
                        ),
                        arm.span,
                    );
                    result_ty = Some(Ty::object());
                } else if rt.nullable != body_ty.nullable {
                    result_ty = Some(Ty::nullable(rt.base.clone()));
                }
            }
        }
        end_jumps.push(st.emit(IrInstr::Jump(0)));
    }
    let throw_ip = st.func.instrs.len() as u32;
    let msg = ctx
        .ir
        .intern_const(crate::ir::IrConst::Str("no matching pattern".into()));
    st.emit(IrInstr::LoadConst(msg));
    st.emit(IrInstr::Op(IrOp::Throw));
    let end_ip = st.func.instrs.len() as u32;
    for (i, tj) in test_jumps.iter().enumerate() {
        let target = arm_starts.get(i + 1).copied().unwrap_or(throw_ip);
        st.patch(*tj, target);
    }
    for ej in &end_jumps {
        st.patch(*ej, end_ip);
    }
    result_ty.unwrap_or(Ty::object())
}

/// Check pattern types before emitting operations that assume a particular
/// runtime representation. This keeps malformed patterns as compile errors
/// instead of allowing them to reach the VM and fail at runtime.
fn validate_pattern(ctx: &mut Ctx<'_>, p: &Pattern, subj_ty: &Ty, span: crate::source::Span) {
    let literal_ty = |p: &Pattern| match p {
        Pattern::LiteralInt(_) => Some(Ty::long()),
        Pattern::LiteralFloat(_) => Some(Ty::double()),
        Pattern::LiteralBool(_) => Some(Ty::bool_()),
        Pattern::LiteralChar(_) => Some(Ty::char_()),
        Pattern::LiteralString(_) => Some(Ty::string()),
        _ => None,
    };
    if let Some(lit_ty) = literal_ty(p) {
        if !pattern_types_compatible(subj_ty, &lit_ty) {
            ctx.err_at(
                "C215",
                format!(
                    "pattern type {} does not match subject type {}",
                    type_display(ctx.program, &lit_ty),
                    type_display(ctx.program, subj_ty)
                ),
                span,
            );
        }
        return;
    }
    match p {
        Pattern::Variant(name, sub) => match &subj_ty.base {
            BaseType::Enum(eid, args) => {
                if subj_ty.nullable {
                    ctx.err_at(
                        "C219",
                        "variant patterns require a non-null enum subject",
                        span,
                    );
                }
                let einfo = &ctx.program.enums[*eid as usize];
                match einfo.variants.iter().find(|v| v.name == *name) {
                    Some(variant) => match (&variant.payload, sub.len()) {
                        (Some(_), 1) => {
                            let subst: Vec<Option<BaseType>> =
                                args.iter().map(|a| Some(a.clone())).collect();
                            let payload_ty = variant
                                .payload
                                .as_ref()
                                .expect("payload checked above")
                                .substitute(&subst);
                            validate_pattern(ctx, &sub[0], &payload_ty, span);
                        }
                        (Some(_), n) => ctx.err_at(
                            "C216",
                            format!(
                                "variant '{}' pattern expects one payload pattern, found {}",
                                name, n
                            ),
                            span,
                        ),
                        (None, 0) => {}
                        (None, n) => ctx.err_at(
                            "C216",
                            format!(
                                "variant '{}' pattern takes no payload patterns, found {}",
                                name, n
                            ),
                            span,
                        ),
                    },
                    None => ctx.err_at(
                        "C191",
                        format!("unknown variant '{}' in pattern", name),
                        span,
                    ),
                }
            }
            _ => ctx.err_at(
                "C217",
                format!(
                    "variant pattern requires an enum subject, found {}",
                    type_display(ctx.program, subj_ty)
                ),
                span,
            ),
        },
        Pattern::List(sub) => match &subj_ty.base {
            BaseType::List(elem) => {
                if subj_ty.nullable {
                    ctx.err_at(
                        "C219",
                        "list patterns require a non-null List subject",
                        span,
                    );
                }
                let elem_ty = Ty::non_null(elem.as_ref().clone());
                for nested in sub {
                    validate_pattern(ctx, nested, &elem_ty, span);
                }
            }
            _ => ctx.err_at(
                "C218",
                format!(
                    "list pattern requires a List subject, found {}",
                    type_display(ctx.program, subj_ty)
                ),
                span,
            ),
        },
        Pattern::Wildcard | Pattern::Bind(_) => {}
        _ => {}
    }
}

fn pattern_types_compatible(subject: &Ty, literal: &Ty) -> bool {
    if matches!(subject.base, BaseType::Object)
        || matches!(literal.base, BaseType::Object)
        || matches!(subject.base, BaseType::Null)
        || matches!(literal.base, BaseType::Null)
    {
        return true;
    }
    if subject.base == literal.base {
        return true;
    }
    matches!(
        (&subject.base, &literal.base),
        (BaseType::Byte, BaseType::Long)
            | (BaseType::Long, BaseType::Byte)
            | (BaseType::Byte, BaseType::Double)
            | (BaseType::Double, BaseType::Byte)
            | (BaseType::Long, BaseType::Double)
            | (BaseType::Double, BaseType::Long)
    )
}

fn emit_literal_test(
    ctx: &mut Ctx<'_>,
    st: &mut FnState,
    subj_slot: usize,
    subj_ty: &Ty,
    lit: crate::ir::IrConst,
) {
    st.emit(IrInstr::LoadLocal(subj_slot as u16));
    let c = ctx.ir.intern_const(lit.clone());
    st.emit(IrInstr::LoadConst(c));
    // The literal carries its own type so numeric subjects use the correct
    // comparison opcode (e.g. an Long subject against a Double literal).
    let lit_ty = match &lit {
        crate::ir::IrConst::Long(_) => Ty::long(),
        crate::ir::IrConst::Double(_) => Ty::double(),
        crate::ir::IrConst::Bool(_) => Ty::bool_(),
        crate::ir::IrConst::Char(_) => Ty::char_(),
        crate::ir::IrConst::Str(_) => Ty::string(),
        crate::ir::IrConst::Null => Ty::null(),
    };
    emit_eq(ctx, st, subj_ty, &lit_ty, crate::source::Span::new(0, 0, 0));
}

/// Emit a boolean test for the pattern (subject in local `subj_slot`).
fn check_pattern_test(
    ctx: &mut Ctx<'_>,
    st: &mut FnState,
    p: &Pattern,
    subj_slot: usize,
    subj_ty: &Ty,
) {
    match p {
        Pattern::Wildcard => {
            let t = ctx.ir.intern_const(crate::ir::IrConst::Bool(true));
            st.emit(IrInstr::LoadConst(t));
        }
        Pattern::LiteralInt(v) => {
            emit_literal_test(ctx, st, subj_slot, subj_ty, crate::ir::IrConst::Long(*v));
        }
        Pattern::LiteralFloat(v) => {
            emit_literal_test(ctx, st, subj_slot, subj_ty, crate::ir::IrConst::Double(*v));
        }
        Pattern::LiteralBool(v) => {
            emit_literal_test(ctx, st, subj_slot, subj_ty, crate::ir::IrConst::Bool(*v));
        }
        Pattern::LiteralChar(v) => {
            emit_literal_test(ctx, st, subj_slot, subj_ty, crate::ir::IrConst::Char(*v));
        }
        Pattern::LiteralString(v) => {
            emit_literal_test(
                ctx,
                st,
                subj_slot,
                subj_ty,
                crate::ir::IrConst::Str(v.clone()),
            );
        }
        Pattern::Bind(_) => {
            let t = ctx.ir.intern_const(crate::ir::IrConst::Bool(true));
            st.emit(IrInstr::LoadConst(t));
        }
        Pattern::Variant(name, sub) => {
            if let BaseType::Enum(eid, eargs) = &subj_ty.base {
                let einfo = &ctx.program.enums[*eid as usize];
                if let Some(vi) = einfo.variants.iter().position(|v| v.name == *name) {
                    st.emit(IrInstr::LoadLocal(subj_slot as u16));
                    st.emit(IrInstr::Op(IrOp::EnumIndex));
                    let c = ctx.ir.intern_const(crate::ir::IrConst::Long(vi as i64));
                    st.emit(IrInstr::LoadConst(c));
                    st.emit(IrInstr::Op(IrOp::EqLong));
                    if !sub.is_empty() {
                        // Payload must also match: extract it into a typed
                        // temporary and evaluate the nested pattern(s).
                        let subst: Vec<Option<BaseType>> =
                            eargs.iter().map(|a| Some(a.clone())).collect();
                        let payload_ty = einfo.variants[vi]
                            .payload
                            .as_ref()
                            .map(|p| p.substitute(&subst))
                            .unwrap_or_else(Ty::object);
                        let payload_slot = st.decl_local("$mtest", payload_ty.clone(), false);
                        st.emit(IrInstr::LoadLocal(subj_slot as u16));
                        st.emit(IrInstr::Op(IrOp::EnumPayload));
                        st.emit(IrInstr::StoreLocal(payload_slot as u16));
                        let nested = if sub.len() == 1 {
                            sub[0].clone()
                        } else {
                            // Multiple subpatterns view the (list) payload
                            // element-wise.
                            Pattern::List(sub.clone())
                        };
                        check_pattern_test(ctx, st, &nested, payload_slot, &payload_ty);
                        st.emit(IrInstr::Op(IrOp::And));
                    }
                    return;
                }
            }
            ctx.err_at(
                "C191",
                format!("unknown variant '{}' in pattern", name),
                crate::source::Span::new(0, 0, 0),
            );
            let f = ctx.ir.intern_const(crate::ir::IrConst::Bool(false));
            st.emit(IrInstr::LoadConst(f));
        }
        Pattern::List(pats) => {
            // Length test first; when it fails the element tests (which
            // index into the subject) must not run, so short-circuit.
            st.emit(IrInstr::LoadLocal(subj_slot as u16));
            st.emit(IrInstr::Op(IrOp::ListLen));
            let c = ctx
                .ir
                .intern_const(crate::ir::IrConst::Long(pats.len() as i64));
            st.emit(IrInstr::LoadConst(c));
            st.emit(IrInstr::Op(IrOp::EqLong));
            // Short-circuit on the length test: keep the accumulator on the
            // stack (via Dup) so the element AND-chain below still works,
            // while a false length jumps straight to the merge.
            st.emit(IrInstr::Op(IrOp::Dup));
            let skip = st.emit(IrInstr::JumpIfFalse(0));
            let elem_base = match &subj_ty.base {
                BaseType::List(e) => e.as_ref().clone(),
                _ => BaseType::Object,
            };
            for (i, sp) in pats.iter().enumerate() {
                let elem_ty = Ty::non_null(elem_base.clone());
                let elem_slot = st.decl_local("$elem", elem_ty.clone(), false);
                st.emit(IrInstr::LoadLocal(subj_slot as u16));
                let ci = ctx.ir.intern_const(crate::ir::IrConst::Long(i as i64));
                st.emit(IrInstr::LoadConst(ci));
                st.emit(IrInstr::Op(IrOp::ListGet));
                st.emit(IrInstr::StoreLocal(elem_slot as u16));
                check_pattern_test(ctx, st, sp, elem_slot, &elem_ty);
                st.emit(IrInstr::Op(IrOp::And));
            }
            let done = st.emit(IrInstr::Jump(0));
            st.patch(skip, st.func.instrs.len() as u32);
            st.patch(done, st.func.instrs.len() as u32);
        }
    }
}

/// Bind pattern variables (after a successful test).
fn bind_pattern(ctx: &mut Ctx<'_>, st: &mut FnState, p: &Pattern, subj_slot: usize, subj_ty: &Ty) {
    match p {
        Pattern::Bind(name) => {
            let slot = st.decl_local(name, subj_ty.clone(), false);
            st.emit(IrInstr::LoadLocal(subj_slot as u16));
            st.emit(IrInstr::StoreLocal(slot as u16));
        }
        Pattern::Variant(vname, sub) => {
            if !sub.is_empty() {
                // Resolve the variant's payload type from the subject enum.
                let payload_ty = match &subj_ty.base {
                    BaseType::Enum(eid, _) => {
                        let einfo = &ctx.program.enums[*eid as usize];
                        einfo
                            .variants
                            .iter()
                            .find(|v| v.name == *vname)
                            .and_then(|v| v.payload.clone())
                            .unwrap_or(Ty::object())
                    }
                    _ => Ty::object(),
                };
                // Payload value into a temp, then bind subpatterns.
                let payload_slot = st.decl_local("$payload", payload_ty.clone(), false);
                st.emit(IrInstr::LoadLocal(subj_slot as u16));
                st.emit(IrInstr::Op(IrOp::EnumPayload));
                st.emit(IrInstr::StoreLocal(payload_slot as u16));
                for sp in sub.iter() {
                    bind_pattern(ctx, st, sp, payload_slot, &payload_ty);
                }
            }
        }
        Pattern::List(pats) => {
            for (i, sp) in pats.iter().enumerate() {
                let elem_slot = st.decl_local("$elem", Ty::object(), false);
                st.emit(IrInstr::LoadLocal(subj_slot as u16));
                let ci = ctx.ir.intern_const(crate::ir::IrConst::Long(i as i64));
                st.emit(IrInstr::LoadConst(ci));
                st.emit(IrInstr::Op(IrOp::ListGet));
                st.emit(IrInstr::StoreLocal(elem_slot as u16));
                bind_pattern(ctx, st, sp, elem_slot, &Ty::object());
            }
        }
        _ => {}
    }
}

fn check_self_init(ctx: &mut Ctx<'_>, st: &mut FnState, si: &SelfInitExpr) -> Ty {
    let cid = match st.class_id {
        Some(c) => c,
        None => {
            ctx.err_at(
                "C192",
                "'Self { ... }' construction is only valid inside class methods",
                si.span,
            );
            return Ty::object();
        }
    };
    let info = &ctx.program.classes[cid as usize];
    // Validate field list: exactly the class's own fields, each once.
    let own: Vec<String> = info.def.fields.iter().map(|f| f.name.clone()).collect();
    let mut seen = std::collections::HashSet::new();
    for (name, _) in &si.fields {
        if !own.contains(name) {
            ctx.err_at(
                "C193",
                format!(
                    "'{}' is not a field of '{}' (parent fields cannot be initialized here)",
                    name, info.name
                ),
                si.span,
            );
        }
        if !seen.insert(name.clone()) {
            ctx.err_at(
                "C194",
                format!("field '{}' initialized more than once", name),
                si.span,
            );
        }
    }
    for name in &own {
        if !seen.contains(name) {
            ctx.err_at(
                "C195",
                format!("field '{}' is not initialized in construction", name),
                si.span,
            );
        }
    }
    // Parent construction.
    match (&si.super_init, info.parent) {
        (Some(e), Some(_)) => {
            let t = check_expr(ctx, st, e);
            if !matches!(t.base, BaseType::Class(p, _) if p == info.parent.unwrap()) {
                ctx.err_at(
                    "C196",
                    "super initializer must call the parent's factory",
                    si.span,
                );
            }
        }
        (None, Some(p)) => {
            // Parent without explicit init: only allowed when parent has
            // no fields (zero-initialized).
            let pinfo = &ctx.program.classes[p as usize];
            if !pinfo.def.fields.is_empty() {
                ctx.err_at(
                    "C197",
                    format!(
                        "class '{}' has parent fields; use 'super: {}.new(...)' in construction",
                        info.name,
                        ctx.program.class_name(p)
                    ),
                    si.span,
                );
            }
        }
        (Some(_), None) => {
            ctx.err_at("C198", "class has no parent; 'super:' is invalid", si.span);
        }
        (None, None) => {}
    }
    // Emit: allocate (or take parent object), then store own fields.
    if si.super_init.is_none() {
        st.emit(IrInstr::NewObject(cid as u16, info.fields.len() as u16));
    } else {
        // Stack holds the parent instance from `super: Parent.new(...)`.
        // Allocate the subclass and copy the parent's fields (which occupy
        // the first slots in the parent-first layout) into it.
        st.emit(IrInstr::NewObject(cid as u16, info.fields.len() as u16));
        let pcount = info
            .parent
            .map(|parent| ctx.program.classes[parent as usize].fields.len() as u16)
            .unwrap_or(0);
        st.emit(IrInstr::CopyFields(pcount));
    }
    for (name, expr) in &si.fields {
        let slot = info.own_field_slots.get(name).copied().unwrap_or(0);
        let fty = info
            .fields
            .iter()
            .find(|f| f.name == *name)
            .map(|f| f.ty.clone())
            .unwrap_or(Ty::object());
        let vty = check_expr(ctx, st, expr);
        if !crate::types::is_subtype(&vty, &fty, ctx.program) {
            ctx.err_at(
                "C199",
                format!(
                    "field '{}' expects {}, found {}",
                    name,
                    type_display(ctx.program, &fty),
                    type_display(ctx.program, &vty)
                ),
                expr.span(),
            );
        }
        st.emit(IrInstr::StoreField(slot));
    }
    let n = info.type_params.len();
    let args: Vec<BaseType> = (0..n).map(|i| BaseType::TypeVar(i as u32)).collect();
    Ty::non_null(BaseType::Class(cid, args))
}

fn check_super_call(ctx: &mut Ctx<'_>, st: &mut FnState, sc: &SuperCallExpr) -> Ty {
    let cid = match st.class_id {
        Some(c) => c,
        None => {
            ctx.err_at("C208", "'super' is not available here", sc.span);
            return Ty::object();
        }
    };
    let info = &ctx.program.classes[cid as usize];
    let pid = match info.parent {
        Some(p) => p,
        None => {
            ctx.err_at(
                "C209",
                format!(
                    "class '{}' has no parent; 'super.{}' is invalid",
                    info.name, sc.name
                ),
                sc.span,
            );
            return Ty::object();
        }
    };
    let pinfo = &ctx.program.classes[pid as usize];
    match pinfo.find_instance_method(ctx.program, pid, &sc.name) {
        Some((_didx, midx)) => {
            let dinfo = &ctx.program.classes[_didx as usize];
            let mdef = &dinfo.methods[midx];
            let slot = match dinfo.vtable_slot(&sc.name) {
                Some(s) => s,
                None => {
                    ctx.err_at(
                        "C210",
                        format!("method '{}' not found in parent vtable", sc.name),
                        sc.span,
                    );
                    return Ty::object();
                }
            };
            let fid = compile_template(
                ctx.program,
                ctx.sources,
                ctx.diags,
                ctx.ir,
                ctx.fn_ids,
                Template::Class(_didx, midx),
            );
            let _ = fid;
            // Stack layout: [self, arg...]; the receiver sits below the
            // arguments, which match_args evaluates exactly once.
            st.emit(IrInstr::LoadLocal(st.lookup_local("self").unwrap() as u16));
            let self_ty = st.local_type("self").unwrap_or(Ty::object());
            let class_args = match &self_ty.base {
                BaseType::Class(_, a) => a.clone(),
                _ => vec![],
            };
            let (_arg_tys, n_args) = match_args(
                ctx,
                st,
                mdef,
                &class_args,
                &crate::ast::CallExpr {
                    callee: Box::new(Expr::Ident(sc.name.clone())),
                    args: sc.args.clone(),
                    span: sc.span,
                },
            );
            st.emit(IrInstr::CallSuper(pid as u16, slot, n_args as u16));
            let ret = mdef.return_ty.clone().unwrap_or(Ty::void());
            let subst: Vec<Option<BaseType>> = class_args.iter().map(|a| Some(a.clone())).collect();
            ret.substitute(&subst)
        }
        None => {
            ctx.err_at(
                "C211",
                format!("parent has no method '{}'", sc.name),
                sc.span,
            );
            Ty::object()
        }
    }
}

/// Resolve a type reference in declaration/annotation position.
pub fn resolve_type_ref(
    diags: &mut Diagnostics,
    program: &ResolvedProgram,
    ty: &TypeRef,
    type_params: &[String],
    class_id: Option<u32>,
    span: crate::source::Span,
) -> Ty {
    let base = resolve_type_base(diags, program, ty, type_params, class_id, span);
    Ty {
        base,
        nullable: ty.nullable,
    }
}

fn resolve_type_base(
    diags: &mut Diagnostics,
    program: &ResolvedProgram,
    ty: &TypeRef,
    type_params: &[String],
    class_id: Option<u32>,
    span: crate::source::Span,
) -> BaseType {
    match &ty.base {
        TypeBase::Named(name) => {
            resolve_named_type(diags, program, name, type_params, class_id, span)
        }
        TypeBase::Nullable(inner) => {
            resolve_type_base(diags, program, inner, type_params, class_id, span)
        }
        TypeBase::Generic(name, args) => {
            let head = resolve_named_type(diags, program, name, type_params, class_id, span);
            let expected = match &head {
                BaseType::List(_) | BaseType::Stack(_) | BaseType::Set(_) => Some(1),
                BaseType::Map(_, _) => Some(2),
                BaseType::Class(_, params)
                | BaseType::Interface(_, params)
                | BaseType::Enum(_, params) => Some(params.len()),
                _ => None,
            };
            if let Some(expected) = expected {
                if args.len() != expected
                    && !diags
                        .items
                        .iter()
                        .any(|d| d.code == "C102" && d.span == Some(ty.span))
                {
                    diags.err_at(
                        "C102",
                        format!(
                            "generic type '{}' expects {} type arguments, found {}",
                            name,
                            expected,
                            args.len()
                        ),
                        ty.span,
                    );
                }
            }
            let args: Vec<BaseType> = args
                .iter()
                .map(|a| resolve_type_base(diags, program, a, type_params, class_id, a.span))
                .collect();
            match head {
                BaseType::List(_) => BaseType::List(Box::new(
                    args.into_iter().next().unwrap_or(BaseType::Object),
                )),
                BaseType::Map(_, _) => {
                    let (k, v) = (
                        args.first().cloned().unwrap_or(BaseType::Object),
                        args.get(1).cloned().unwrap_or(BaseType::Object),
                    );
                    BaseType::Map(Box::new(k), Box::new(v))
                }
                BaseType::Stack(_) => BaseType::Stack(Box::new(
                    args.into_iter().next().unwrap_or(BaseType::Object),
                )),
                BaseType::Set(_) => BaseType::Set(Box::new(
                    args.into_iter().next().unwrap_or(BaseType::Object),
                )),
                BaseType::Class(id, _) => BaseType::Class(id, args),
                BaseType::Interface(id, _) => BaseType::Interface(id, args),
                BaseType::Enum(id, _) => BaseType::Enum(id, args),
                other => other,
            }
        }
    }
}

fn resolve_named_type(
    diags: &mut Diagnostics,
    program: &ResolvedProgram,
    name: &str,
    type_params: &[String],
    class_id: Option<u32>,
    span: crate::source::Span,
) -> BaseType {
    match name {
        "Bool" => return BaseType::Bool,
        "Byte" => return BaseType::Byte,
        "Long" => return BaseType::Long,
        "Double" => return BaseType::Double,
        "Char" => return BaseType::Char,
        "String" => return BaseType::String,
        "Object" => return BaseType::Object,
        "Void" => return BaseType::Void,
        "List" => return BaseType::List(Box::new(BaseType::Object)),
        "Map" => return BaseType::Map(Box::new(BaseType::Object), Box::new(BaseType::Object)),
        "Stack" => return BaseType::Stack(Box::new(BaseType::Object)),
        "Set" => return BaseType::Set(Box::new(BaseType::Object)),
        "Writer" => return BaseType::Interface(crate::resolve::builtin::WRITER, vec![]),
        "Reader" => return BaseType::Interface(crate::resolve::builtin::READER, vec![]),
        "Runnable" => return BaseType::Interface(crate::resolve::builtin::RUNNABLE, vec![]),
        "Equatable" => return BaseType::Interface(crate::resolve::builtin::EQUATABLE, vec![]),
        "Hashable" => return BaseType::Interface(crate::resolve::builtin::HASHABLE, vec![]),
        "Iterable" => return BaseType::Interface(crate::resolve::builtin::ITERABLE, vec![]),
        "Countable" => return BaseType::Interface(crate::resolve::builtin::COUNTABLE, vec![]),
        "Stringable" => return BaseType::Interface(crate::resolve::builtin::STRINGABLE, vec![]),
        "Comparable" => return BaseType::Interface(crate::resolve::builtin::COMPARABLE, vec![]),
        "Thread" => return BaseType::native(crate::types::native_kind::THREAD),
        "Mutex" => return BaseType::native(crate::types::native_kind::MUTEX),
        "Semaphore" => return BaseType::native(crate::types::native_kind::SEMAPHORE),
        "Process" => return BaseType::native(crate::types::native_kind::PROCESS),
        "Regex" => return BaseType::native(crate::types::native_kind::REGEX),
        "Stream" => return BaseType::native(crate::types::native_kind::STREAM),
        _ => {}
    }
    if let Some(i) = type_params.iter().position(|p| p == name) {
        return BaseType::TypeVar(i as u32);
    }
    if name == "Self" {
        if let Some(cid) = class_id {
            let n = program.classes[cid as usize].type_params.len();
            let args: Vec<BaseType> = (0..n).map(|i| BaseType::TypeVar(i as u32)).collect();
            return BaseType::Class(cid, args);
        }
        diags.err_at("C213", "'Self' is only valid inside a class", span);
        return BaseType::Object;
    }
    match program.lookup_item(name) {
        Some((crate::resolve::KindOf::Class, id)) => {
            let n = program.classes[id as usize].type_params.len();
            let args: Vec<BaseType> = (0..n).map(|i| BaseType::TypeVar(i as u32)).collect();
            BaseType::Class(id, args)
        }
        Some((crate::resolve::KindOf::Interface, id)) => {
            let n = program.interfaces[id as usize].type_params.len();
            let args: Vec<BaseType> = (0..n).map(|i| BaseType::TypeVar(i as u32)).collect();
            BaseType::Interface(id, args)
        }
        Some((crate::resolve::KindOf::Enum, id)) => {
            let n = program.enums[id as usize].type_params.len();
            let args: Vec<BaseType> = (0..n).map(|i| BaseType::TypeVar(i as u32)).collect();
            BaseType::Enum(id, args)
        }
        None => {
            diags.err_at("C100", format!("unknown type '{}'", name), span);
            BaseType::Object
        }
    }
}

pub fn type_display(program: &ResolvedProgram, ty: &Ty) -> String {
    let base = match &ty.base {
        BaseType::Class(id, args) => format!("{}{}", program.class_name(*id), args_str(args)),
        BaseType::Interface(id, args) => {
            format!("{}{}", program.interface_name(*id), args_str(args))
        }
        BaseType::Enum(id, args) => format!("{}{}", program.enum_name(*id), args_str(args)),
        other => other.to_string(),
    };
    format!("{}{}", base, if ty.nullable { "?" } else { "" })
}

pub fn is_global(name: &str) -> bool {
    matches!(name, "stdin" | "stdout" | "stderr")
}

pub fn global_slot(name: &str) -> u16 {
    match name {
        "stdin" => 0,
        "stdout" => 1,
        _ => 2,
    }
}

pub fn global_type(name: &str) -> Ty {
    match name {
        "stdin" => Ty::non_null(BaseType::Interface(crate::resolve::builtin::READER, vec![])),
        "stdout" => Ty::non_null(BaseType::Interface(crate::resolve::builtin::WRITER, vec![])),
        _ => Ty::non_null(BaseType::Interface(crate::resolve::builtin::WRITER, vec![])),
    }
}

/// Source-level name for built-in receiver types (for builtin tables).
pub fn builtin_name_of(program: &ResolvedProgram, base: &BaseType) -> String {
    match base {
        BaseType::Bool => "Bool".into(),
        BaseType::Byte => "Byte".into(),
        BaseType::Long => "Long".into(),
        BaseType::Double => "Double".into(),
        BaseType::Char => "Char".into(),
        BaseType::String => "String".into(),
        BaseType::List(_) => "List".into(),
        BaseType::Map(_, _) => "Map".into(),
        BaseType::Stack(_) => "Stack".into(),
        BaseType::Set(_) => "Set".into(),
        BaseType::Interface(i, _) => program.interface_name(*i).to_string(),
        BaseType::Native(k) => match *k {
            crate::types::native_kind::THREAD => "Thread".into(),
            crate::types::native_kind::MUTEX => "Mutex".into(),
            crate::types::native_kind::SEMAPHORE => "Semaphore".into(),
            crate::types::native_kind::PROCESS => "Process".into(),
            crate::types::native_kind::REGEX => "Regex".into(),
            _ => "Stream".into(),
        },
        _ => "Object".into(),
    }
}

/// Concrete implementation of an interface method for dispatch metadata:
/// the class override wins, otherwise the most specific default.
pub fn interface_impl_for(
    program: &ResolvedProgram,
    iface_id: u32,
    name: &str,
) -> Option<(Template, Vec<ParamInfo>, Option<Ty>)> {
    let iface = &program.interfaces[iface_id as usize];
    let slot = iface.slots.iter().find(|s| s.name == name)?;
    // Most specific provider: walk this interface's parents first (deepest
    // first), then itself.
    let mut order: Vec<u32> = vec![iface_id];
    for p in iface.all_parents.iter().rev() {
        order.push(*p);
    }
    for iid in order {
        let i = &program.interfaces[iid as usize];
        if let Some(idx) = i
            .methods
            .iter()
            .position(|m| m.name == name && m.body.is_some())
        {
            return Some((
                Template::InterfaceDefault(iid, idx),
                i.methods[idx].params.clone(),
                i.methods[idx].return_ty.clone(),
            ));
        }
    }
    match slot.default.or(slot.decl) {
        Some((iid, idx)) => {
            let i = &program.interfaces[iid as usize];
            Some((
                Template::InterfaceDefault(iid, idx),
                i.methods[idx].params.clone(),
                i.methods[idx].return_ty.clone(),
            ))
        }
        None => None,
    }
}

impl<'a> Checker<'a> {
    /// Build IR class/interface metadata: vtables, statics, interface
    /// dispatch tables.
    pub fn build_class_metadata(&mut self) {
        for c in &self.program.classes {
            // Vtable: nearest method per slot.
            let mut vtable = vec![];
            for name in &c.vtable_names {
                let fid = match c.find_instance_method(self.program, c.id, name) {
                    Some((didx, midx)) => self.compile_template(Template::Class(didx, midx)),
                    None => {
                        // Slot from an interface default with no class impl.
                        let mut found = None;
                        for iid in &c.all_interfaces {
                            if let Some((t, _, _)) = interface_impl_for(self.program, *iid, name) {
                                found = Some(t);
                                break;
                            }
                        }
                        match found {
                            Some(t) => self.compile_template(t),
                            None => 0,
                        }
                    }
                };
                vtable.push(fid);
            }
            let statics: Vec<(String, u32)> = c
                .methods
                .iter()
                .enumerate()
                .filter(|(_, m)| m.is_static)
                .map(|(i, m)| {
                    (
                        m.name.clone(),
                        self.compile_template(Template::Class(c.id, i)),
                    )
                })
                .collect();
            // Interface dispatch tables.
            let mut interfaces = vec![];
            for iid in &c.all_interfaces {
                let iface = &self.program.interfaces[*iid as usize];
                let mut dispatch = vec![];
                for slot in &iface.slots {
                    let fid = match c.find_instance_method(self.program, c.id, &slot.name) {
                        Some((didx, midx)) => self.compile_template(Template::Class(didx, midx)),
                        None => match interface_impl_for(self.program, *iid, &slot.name) {
                            Some((t, _, _)) => self.compile_template(t),
                            None => 0,
                        },
                    };
                    dispatch.push(fid);
                }
                interfaces.push((*iid, dispatch));
            }
            self.ir.classes.push(IrClass {
                id: c.id,
                name: c.name.clone(),
                parent: c.parent,
                field_count: c.fields.len() as u16,
                vtable_names: c.vtable_names.clone(),
                vtable,
                statics,
                interfaces,
            });
        }
        for i in &self.program.interfaces {
            let defaults: Vec<Option<u32>> = i
                .slots
                .iter()
                .map(|s| {
                    s.default.map(|(iid, idx)| {
                        self.compile_template(Template::InterfaceDefault(iid, idx))
                    })
                })
                .collect();
            self.ir.interfaces.push(IrInterface {
                id: i.id,
                name: i.name.clone(),
                slots: i.slots.iter().map(|s| s.name.clone()).collect(),
                defaults,
            });
        }
    }
}

fn args_str(args: &[BaseType]) -> String {
    if args.is_empty() {
        String::new()
    } else {
        format!(
            "<{}>",
            args.iter()
                .map(|a| a.to_string())
                .collect::<Vec<_>>()
                .join(", ")
        )
    }
}

/// Compile one method template into the IR module; returns its function id.
pub fn compile_template(
    program: &ResolvedProgram,
    sources: &SourceManager,
    diags: &mut Diagnostics,
    ir: &mut IrModule,
    fn_ids: &mut HashMap<u64, u32>,
    template: Template,
) -> u32 {
    let key = template.key();
    if let Some(fid) = fn_ids.get(&key) {
        return *fid;
    }
    let (name, info, source_file, is_instance, class_id) = match template {
        Template::Class(cid, idx) => {
            let c = &program.classes[cid as usize];
            let m = &c.methods[idx];
            (
                format!("{}.{}", c.name, m.name),
                m.clone(),
                c.def.span.file,
                !m.is_static,
                Some(cid),
            )
        }
        Template::InterfaceDefault(iid, idx) => {
            let i = &program.interfaces[iid as usize];
            let m = &i.methods[idx];
            (
                format!("{}.$default.{}", i.name, m.name),
                m.clone(),
                i.def.span.file,
                true,
                None,
            )
        }
    };
    // Reserve the function slot up front so recursive calls (direct or
    // mutual) resolve to this function while its body is still compiling.
    // The placeholder is replaced with the finished function below.
    let fid = ir.functions.len() as u32;
    ir.functions.push(IrFunction {
        name: String::new(),
        params: vec![],
        param_tys: vec![],
        local_count: 0,
        returns_value: false,
        instrs: vec![],
        source_file,
        line_map: vec![],
    });
    fn_ids.insert(key, fid);

    // Type-parameter names visible in the body: class/interface params
    // first, then the method's own.
    let type_param_names: Vec<String> = match template {
        Template::Class(cid, idx) => {
            let c = &program.classes[cid as usize];
            let m = &c.methods[idx];
            let mut v: Vec<String> = c.type_params.iter().map(|p| p.name.clone()).collect();
            v.extend(m.type_params.iter().map(|p| p.name.clone()));
            v
        }
        Template::InterfaceDefault(iid, idx) => {
            let i = &program.interfaces[iid as usize];
            let m = &i.methods[idx];
            let mut v: Vec<String> = i.type_params.iter().map(|p| p.name.clone()).collect();
            v.extend(m.type_params.iter().map(|p| p.name.clone()));
            v
        }
    };

    let mut st = FnState {
        func: IrFunction {
            name: name.clone(),
            params: vec![],
            param_tys: vec![],
            local_count: 0,
            returns_value: false,
            instrs: vec![],
            source_file,
            line_map: vec![],
        },
        locals: vec![],
        names: HashMap::new(),
        narrowing: vec![],
        scope_depth: 0,
        loops: vec![],
        ret_ty: None,
        cur_line: 1,
        class_id,
        is_static: info.is_static,
        iface_id: match template {
            Template::InterfaceDefault(iid, _) => Some(iid),
            _ => None,
        },
        expected_literal: None,
        type_param_names,
        ctx_stack: vec![],
    };

    // Instance receiver occupies local slot 0 (the VM places the receiver
    // first on the frame); named parameters follow.
    if is_instance {
        let self_ty = match (class_id, template) {
            (Some(cid), _) => {
                let n = program.classes[cid as usize].type_params.len();
                let args: Vec<BaseType> = (0..n).map(|i| BaseType::TypeVar(i as u32)).collect();
                Ty::non_null(BaseType::Class(cid, args))
            }
            (None, Template::InterfaceDefault(iid, _)) => {
                Ty::non_null(BaseType::Interface(iid, vec![]))
            }
            _ => Ty::object(),
        };
        st.func.params.push("self".to_string());
        st.func.param_tys.push(self_ty.clone());
        st.decl_local("self", self_ty, false);
    }
    // Parameter locals.
    for p in &info.params {
        let ty = if p.variadic {
            Ty::non_null(BaseType::List(Box::new(p.ty.base.clone())))
        } else {
            p.ty.clone()
        };
        st.func.params.push(p.name.clone());
        st.func.param_tys.push(ty.clone());
        st.decl_local(&p.name, ty, false);
    }

    let ret_ty = info.return_ty.clone();
    st.ret_ty = ret_ty.clone();

    let mut ctx = Ctx {
        program,
        sources,
        diags,
        ir,
        fn_ids,
    };
    if let Some(body) = &info.body {
        check_block(&mut ctx, &mut st, body, CtxOwner::Function);
    } else {
        // Abstract stub: never invoked (dispatch tables point at concrete
        // implementations), but must still be a well-formed function.
        if matches!(&ret_ty, Some(rt) if !matches!(rt.base, BaseType::Void)) {
            let c = ctx.ir.intern_const(crate::ir::IrConst::Null);
            st.emit(IrInstr::LoadConst(c));
            st.emit(IrInstr::Return);
        } else {
            st.emit(IrInstr::ReturnVoid);
        }
    }
    // Functions whose bodies may fall off the end (a conditional return, a
    // trailing if/while/try with a reachable exit path) need an explicit
    // epilogue. Without it, control-flow targets that point one-past-the-end
    // would be encoded as a jump to offset 0 (the function start), looping
    // forever. A value-returning function whose fall-through is *semantically*
    // reachable is also flagged with C130.
    let value_ret = matches!(&ret_ty, Some(rt) if !matches!(rt.base, BaseType::Void));
    if info.body.is_some() && body_flows_off_end(&st.func.instrs) {
        if value_ret && body_flows_off_end_semantically(&st.func.instrs) {
            ctx.err_at(
                "C130",
                format!("method '{}' must return a value", name),
                info.span,
            );
        }
        if value_ret {
            // Recover: return null.
            let c = ctx.ir.intern_const(crate::ir::IrConst::Null);
            st.emit(IrInstr::LoadConst(c));
            st.emit(IrInstr::Return);
        } else {
            st.emit(IrInstr::ReturnVoid);
        }
    }

    let mut finished = st.func;
    finished.local_count = st.locals.len() as u16;
    finished.returns_value = matches!(&ret_ty, Some(rt) if !matches!(rt.base, BaseType::Void));
    ir.functions[fid as usize] = finished;
    fid
}
