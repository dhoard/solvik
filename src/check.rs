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

/// A compilable method template: a class method, an interface default, or
/// a synthetic per-class static field initializer.
#[derive(Clone, Copy, PartialEq, Eq, Hash, Debug)]
pub enum Template {
    Class(u32, usize),
    InterfaceDefault(u32, usize),
    /// Synthetic static initializer for one class's static fields.
    StaticInit(u32),
}

/// A resolved interface default method: template, params, return type.
pub(crate) type IfaceImpl = (Template, Vec<ParamInfo>, Option<Ty>);

impl Template {
    fn key(self) -> u64 {
        match self {
            Template::Class(c, i) => (c as u64) << 16 | i as u64,
            Template::InterfaceDefault(i, m) => 0x0001_0000_0000u64 | ((i as u64) << 16) | m as u64,
            Template::StaticInit(c) => 0x0002_0000_0000u64 | c as u64,
        }
    }
}

#[derive(Debug, Clone)]
pub struct LocalVar {
    name: String,
    ty: Ty,
    mutable: bool,
    /// Span of the declaration (for shadow diagnostics). Zero for synthetic
    /// temps and parameters (which carry no source span).
    decl_span: crate::source::Span,
    /// True when this local is a method parameter or `self`; such bindings
    /// are reported with a span-free shadow message (ParamInfo has no span).
    is_param: bool,
}

/// Per-method compilation state (owned by the Checker while a body is checked).
#[derive(Debug)]
pub struct FnState {
    pub func: IrFunction,
    pub locals: Vec<LocalVar>,
    /// One binding map per scope depth (index 0 is the base map that holds
    /// `self`/parameters; see the invariant asserted in `begin_scope`).
    pub names: Vec<HashMap<String, usize>>,
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
    /// True while checking a synthetic static initializer body; static field
    /// reads are rejected there to remove initialization-order hazards.
    pub in_static_init: bool,
    /// True while checking the class's static block, which runs after all
    /// static field initializers and may therefore read static fields.
    pub in_static_block: bool,
    /// True only while checking a for-in iterator expression; an integer
    /// range (`a..b`) is legal there and nowhere else. The flag is consumed
    /// by the range itself so nested ranges are rejected.
    pub in_for_in_iter: bool,
    /// Interface whose default body is being checked (self : Interface).
    pub iface_id: Option<u32>,
    /// Expected type for a literal being checked (list/map inference).
    pub expected_literal: Option<Ty>,
    /// Lexical context stack (innermost last) used to resolve which
    /// enclosing try regions a break/continue actually exits.
    pub ctx_stack: Vec<CtxOwner>,
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

    fn decl_local(
        &mut self,
        name: &str,
        ty: Ty,
        mutable: bool,
        decl_span: crate::source::Span,
        is_param: bool,
    ) -> usize {
        let slot = self.locals.len();
        self.locals.push(LocalVar {
            name: name.to_string(),
            ty,
            mutable,
            decl_span,
            is_param,
        });
        // Insert into the innermost scope's map. An existing entry means the
        // caller is declaring a shadow (see check_decl), not a duplicate.
        self.names[self.scope_depth].insert(name.to_string(), slot);
        slot
    }

    /// Declare a synthetic `$`-prefixed temporary (no source span, never a
    /// user-visible binding).
    fn decl_temp(&mut self, name: &str, ty: Ty) -> usize {
        self.decl_local(name, ty, false, crate::source::Span::new(0, 0, 0), false)
    }

    /// Drop any null-narrowing recorded for `name` in *every* scope. Called
    /// on a shadowing declaration: the new binding may be nullable, so a
    /// stale narrowing from an outer binding would authorize non-null use
    /// unsoundly. Conservative — the outer narrowing is not restored when the
    /// shadow block ends.
    fn invalidate_narrowing(&mut self, name: &str) {
        for scope in &mut self.narrowing {
            scope.remove(name);
        }
    }

    fn lookup_local(&self, name: &str) -> Option<usize> {
        // Search innermost-first so the nearest binding wins.
        for depth in (0..=self.scope_depth).rev() {
            if let Some(slot) = self.names[depth].get(name) {
                return Some(*slot);
            }
        }
        None
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
        // The names stack must always carry exactly one base map deeper than
        // the narrowing stack: the base map (index 0) holds `self`/parameters,
        // which are declared before any `begin_scope` and never narrowed.
        assert!(self.names.len() == self.narrowing.len() + 1);
        self.names.push(HashMap::new());
        self.narrowing.push(HashMap::new());
        self.scope_depth += 1;
    }

    fn end_scope(&mut self) {
        // Popping the names map restores outer bindings automatically; local
        // slots are never removed because emitted IR refers to them by
        // absolute index.
        self.narrowing.pop();
        self.names.pop();
        self.scope_depth -= 1;
        assert!(!self.names.is_empty());
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
        // Synthetic static initializers: one per class with static fields
        // or a static block. Compilation is eager; execution is deferred
        // until each class's first active use.
        for class in &self.program.classes {
            if !class.static_fields.is_empty() || class.def.static_block.is_some() {
                self.compile_template(Template::StaticInit(class.id));
            }
        }
        self.build_class_metadata();
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
    // Reachability: once a statement diverges (return/throw/break/continue
    // or a construct that always diverges), every later statement in the
    // same block is unreachable (C245) and is neither checked nor emitted.
    let mut diverged = false;
    for stmt in block.stmts.iter() {
        if diverged {
            ctx.err_at("C245", "unreachable statement", stmt.span());
            continue;
        }
        st.ctx_stack.push(owner);
        check_stmt(ctx, st, stmt);
        st.ctx_stack.pop();
        diverged = stmt_diverges(stmt);
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
            let t = check_expr(ctx, st, e);
            // Only object values are throwable: the built-in Exception,
            // class instances, or interface-typed values.
            let throwable = matches!(t.base, BaseType::Native(k) if k == crate::types::native_kind::EXCEPTION)
                || matches!(t.base, BaseType::Class(_, _) | BaseType::Interface(_, _));
            if !throwable {
                ctx.err_at(
                    "C242",
                    format!(
                        "cannot throw {}: only Exception, class, or interface values are throwable",
                        type_display(ctx.program, &t)
                    ),
                    e.span(),
                );
            } else if t.nullable {
                ctx.err_at(
                    "C242",
                    format!(
                        "cannot throw nullable {}: a thrown value must be non-null",
                        type_display(ctx.program, &t)
                    ),
                    e.span(),
                );
            }
            st.emit(IrInstr::Op(IrOp::Throw));
        }
        Stmt::Break => check_break_continue(ctx, st, true),
        Stmt::Continue => check_break_continue(ctx, st, false),
        Stmt::ScopeBlock(block) => check_scope_block(ctx, st, block),
    }
}

/// Check a standalone `{ ... }` scope block.
/// Emits error C141 for `return` inside scope blocks.
fn check_scope_block(ctx: &mut Ctx<'_>, st: &mut FnState, block: &Block) {
    // Reject `return` inside scope blocks — they are not function bodies.
    for stmt in &block.stmts {
        if matches!(stmt, Stmt::Return(_)) {
            let span = stmt.span();
            ctx.err_at("C141", "'return' not allowed inside scope block", span);
        }
    }
    st.begin_scope();
    check_block(ctx, st, block, CtxOwner::Function);
    st.end_scope();
}

/// Loop entries hold (break_jump_idx, continue_jump_idx): indices of
/// pending Jump instructions patched when the loop closes.
fn check_break_continue(ctx: &mut Ctx<'_>, st: &mut FnState, is_break: bool) {
    let n = st.loops.len();
    if n == 0 {
        ctx.err("C140", "'break'/'continue' outside of a loop");
        return;
    }
    // A break/continue jumps from the break site to the loop target (loop
    // end for break, loop start for continue). Both targets lie inside
    // every body that lexically contains the loop — a try region closes
    // only after its whole body — so the try regions actually left are
    // exactly those opened between the loop start and the break site.
    // Walk the lexical context stack from the break site up to the nearest
    // loop and emit one cleanup per crossed region: run the finally
    // (FinallyDivert) or drop the region (TryEnd).
    let mut actions: Vec<bool> = Vec::new();
    for e in st.ctx_stack.iter().rev() {
        if matches!(e, CtxOwner::Loop) {
            break;
        }
        if let Some(a) = exit_action(*e) {
            actions.push(a);
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

/// Redeclaring a name that is still visible is a compile error (C240):
/// Solvik follows Java's local-name rules and has no shadowing. Also
/// invalidates any null-narrowing recorded for the old binding.
fn check_redeclaration(
    ctx: &mut Ctx<'_>,
    st: &mut FnState,
    name: &str,
    decl_span: crate::source::Span,
) {
    if let Some(prev_slot) = st.lookup_local(name) {
        st.invalidate_narrowing(name);
        let msg = if st.locals[prev_slot].is_param {
            format!("local '{}' redeclares parameter '{}'", name, name)
        } else {
            let line = ctx
                .sources
                .line_number(st.locals[prev_slot].decl_span)
                .unwrap_or(0);
            format!("local '{}' redeclares name from line {}", name, line)
        };
        ctx.err_at("C240", msg, decl_span);
    }
}

fn check_decl(ctx: &mut Ctx<'_>, st: &mut FnState, d: &DeclStmt) {
    st.set_line(d.span, ctx.sources);
    check_redeclaration(ctx, st, &d.name, d.span);
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
            // Collection literals infer element types from the declared
            // type; collection-typed declarations also provide the expected
            // type so `List.new()`/`withCapacity` constructors infer their
            // type arguments from it.
            let is_collection = matches!(
                ty.base,
                BaseType::List(_) | BaseType::Map(_, _) | BaseType::Stack(_) | BaseType::Set(_)
            );
            if matches!(e, Expr::List(_, _) | Expr::Map(_, _)) || is_collection {
                st.expected_literal = Some(ty.clone());
            }
            let t = check_expr(ctx, st, e);
            st.expected_literal = None;
            t
        }
        None => Ty::null(),
    };
    if let Some(e) = &d.init {
        if !crate::types::is_subtype(&init_ty, &ty, ctx.program)
            && !coerce_value(st, &init_ty, &ty, e)
        {
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
        } else {
            emit_coerce(st, &init_ty, &ty);
        }
    }
    let slot = st.decl_local(&d.name, ty, d.mutable, d.span, false);
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

/// Emit an implicit widening conversion when a value of a narrower numeric
/// type flows into a wider numeric target (Java-style assignment
/// conversion). A no-op unless the bases differ and widen.
fn emit_coerce(st: &mut FnState, from: &Ty, to: &Ty) {
    if from.nullable || to.nullable {
        return;
    }
    if from.base == to.base || !from.base.widens_to(&to.base) {
        return;
    }
    if let Some(tag) = crate::ir::conv_target::of(&to.base) {
        st.emit(IrInstr::Convert(tag));
    }
}

/// True when `e` is an integer literal (optionally negated) whose value
/// fits the integral target type — Java-style constant narrowing.
fn int_literal_fits(e: &crate::ast::Expr, target: &BaseType) -> bool {
    use crate::ast::{IntLiteral, UnaryOp};
    let value = match e {
        crate::ast::Expr::Int(IntLiteral::I64(v), _) => Some(*v),
        crate::ast::Expr::Unary(UnaryOp::Neg, inner)
            if matches!(inner.as_ref(), crate::ast::Expr::Int(IntLiteral::I64(_), _)) =>
        {
            match inner.as_ref() {
                crate::ast::Expr::Int(IntLiteral::I64(v), _) => Some(-v),
                _ => unreachable!(),
            }
        }
        _ => None,
    };
    let Some(value) = value else {
        return false;
    };
    match target {
        BaseType::Byte => value >= i8::MIN as i64 && value <= i8::MAX as i64,
        BaseType::Short => value >= i16::MIN as i64 && value <= i16::MAX as i64,
        BaseType::Integer => value >= i32::MIN as i64 && value <= i32::MAX as i64,
        BaseType::Long => true,
        _ => false,
    }
}

/// Legal assignment conversion: emits any required `Convert` and reports
/// success. Covers widening of arbitrary values and constant narrowing of
/// integer literals into narrower integral targets.
fn coerce_value(st: &mut FnState, val_ty: &Ty, target: &Ty, expr: &crate::ast::Expr) -> bool {
    if val_ty.base == target.base {
        return val_ty.nullable <= target.nullable;
    }
    if !val_ty.nullable && val_ty.base.widens_to(&target.base) {
        if let Some(tag) = crate::ir::conv_target::of(&target.base) {
            st.emit(IrInstr::Convert(tag));
        }
        return true;
    }
    if !val_ty.nullable
        && !target.nullable
        && val_ty.base.is_integral()
        && target.base.is_integral()
        && int_literal_fits(expr, &target.base)
    {
        if let Some(tag) = crate::ir::conv_target::of(&target.base) {
            st.emit(IrInstr::Convert(tag));
        }
        return true;
    }
    false
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
        Expr::Ident(name, _) => {
            if name == "self" {
                ctx.err_at("C133", "cannot assign to 'self'", a.target.span());
                return;
            }
            // Classify the target: local, or a static field of the
            // declaring class (bare name, valid only inside a static block).
            #[derive(Clone)]
            enum Tgt {
                Local(u16, Ty),
                /// Bare-name target for a static field of the declaring
                /// class, valid only inside a static block.
                Static(u16, u16, Ty),
            }
            let tgt: Option<Tgt> = match st.lookup_local(name) {
                Some(i) => {
                    // The assign-once rule for immutable locals is enforced
                    // by the definite-assignment pre-pass (C134/C239).
                    Some(Tgt::Local(
                        i as u16,
                        st.local_type(name).unwrap_or(Ty::object()),
                    ))
                }
                None => {
                    // Inside a static block, a bare name may target a
                    // static field of the declaring class.
                    let static_field = if st.in_static_block {
                        st.class_id.and_then(|cid| {
                            let info = &ctx.program.classes[cid as usize];
                            info.static_fields
                                .iter()
                                .enumerate()
                                .find(|(_, f)| f.name == *name)
                                .map(|(slot, f)| (cid, slot, f))
                        })
                    } else {
                        None
                    };
                    if let Some((cid, slot, f)) = static_field {
                        if !f.mutable {
                            ctx.err_at(
                                "C226",
                                format!(
                                    "field '{}' is immutable; declare it with 'mutable' to assign",
                                    name
                                ),
                                a.target.span(),
                            );
                            None
                        } else {
                            Some(Tgt::Static(cid as u16, slot as u16, f.ty.clone()))
                        }
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
            let target_ty = match &tgt {
                Some(Tgt::Local(_, t)) | Some(Tgt::Static(_, _, t)) => t.clone(),
                None => return,
            };
            // Compound updates need the old value under the new one.
            if is_update {
                if let Some(Tgt::Local(s, _)) = &tgt {
                    st.emit(IrInstr::LoadLocal(*s));
                }
                if let Some(Tgt::Static(c, s, _)) = &tgt {
                    st.emit(IrInstr::LoadStatic(*c, *s));
                }
            }
            let val_ty = check_expr(ctx, st, &a.value);
            let concat_update = uop == Some(UpdateOp::Concat);
            if concat_update {
                // `t ..= v` desugars to `t = t .. v`: the target must be a
                // String; the operand is formatted like any `..` operand.
                if !matches!(target_ty.base, BaseType::String) {
                    ctx.err_at("C155", "'..=' requires a String target", a.target.span());
                }
            } else if is_update {
                // Compound update (Java semantics): the promoted arithmetic
                // result is cast back to the target type; integral targets
                // get a runtime range check when narrowing.
                let prom = target_ty.base.binary_promotion(&val_ty.base);
                if prom != Some(target_ty.base.clone())
                    && !(prom.is_some()
                        && target_ty.base.is_integral()
                        && val_ty.base.is_integral())
                {
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
            } else if !crate::types::is_subtype(&val_ty, &target_ty, ctx.program)
                && !coerce_value(st, &val_ty, &target_ty, &a.value)
            {
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
            } else if !concat_update {
                emit_coerce(st, &val_ty, &target_ty);
            }
            if is_update {
                if concat_update {
                    st.emit(IrInstr::Op(IrOp::StrConcat));
                } else {
                    let bin = match uop.unwrap() {
                        UpdateOp::Add => BinOp::Add,
                        UpdateOp::Sub => BinOp::Sub,
                        UpdateOp::Mul => BinOp::Mul,
                        UpdateOp::Div => BinOp::Div,
                        UpdateOp::Mod => BinOp::Mod,
                        UpdateOp::Concat => unreachable!("concat update handled above"),
                    };
                    let o = match bin {
                        BinOp::Add => IrOp::Add,
                        BinOp::Sub => IrOp::Sub,
                        BinOp::Mul => IrOp::Mul,
                        BinOp::Div => IrOp::Div,
                        BinOp::Mod => IrOp::Mod,
                        _ => unreachable!(),
                    };
                    st.emit(IrInstr::Op(o));
                    // Cast the promoted result back to the target width
                    // (Java compound-assignment cast; runtime range check).
                    if let Some(prom) = target_ty.base.binary_promotion(&val_ty.base) {
                        if prom != target_ty.base && target_ty.base.is_integral() {
                            if let Some(tag) = crate::ir::conv_target::of(&target_ty.base) {
                                st.emit(IrInstr::Convert(tag));
                            }
                        }
                    }
                }
            }
            match &tgt {
                Some(Tgt::Local(s, _)) => {
                    st.emit(IrInstr::StoreLocal(*s));
                }
                Some(Tgt::Static(c, s, _)) => {
                    st.emit(IrInstr::StoreStatic(*c, *s));
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
                        if info.static_fields.iter().any(|f| f.name == m.name) {
                            ctx.err_at(
                                "C234",
                                format!(
                                    "field '{}' is static; use {}.{} instead of obj.{}",
                                    m.name, info.name, m.name, m.name
                                ),
                                m.span,
                            );
                            return;
                        }
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
                    "C226",
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
            let concat_update = uop == Some(UpdateOp::Concat);
            if concat_update {
                // `f ..= v` desugars to `f = f .. v`: the field must be a
                // String; the operand is formatted like any `..` operand.
                if !matches!(field_ty.base, BaseType::String) {
                    ctx.err_at("C155", "'..=' requires a String target", a.target.span());
                }
            } else if is_update {
                let prom = field_ty.base.binary_promotion(&val_ty.base);
                if prom != Some(field_ty.base.clone())
                    && !(prom.is_some() && field_ty.base.is_integral() && val_ty.base.is_integral())
                {
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
            } else if !crate::types::is_subtype(&val_ty, &field_ty, ctx.program)
                && !coerce_value(st, &val_ty, &field_ty, &a.value)
            {
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
            } else if !concat_update {
                emit_coerce(st, &val_ty, &field_ty);
            }
            if is_update {
                if concat_update {
                    st.emit(IrInstr::Op(IrOp::StrConcat));
                } else {
                    let bin = match uop.unwrap() {
                        UpdateOp::Add => BinOp::Add,
                        UpdateOp::Sub => BinOp::Sub,
                        UpdateOp::Mul => BinOp::Mul,
                        UpdateOp::Div => BinOp::Div,
                        UpdateOp::Mod => BinOp::Mod,
                        UpdateOp::Concat => unreachable!("concat update handled above"),
                    };
                    let o = match bin {
                        BinOp::Add => IrOp::Add,
                        BinOp::Sub => IrOp::Sub,
                        BinOp::Mul => IrOp::Mul,
                        BinOp::Div => IrOp::Div,
                        BinOp::Mod => IrOp::Mod,
                        _ => unreachable!(),
                    };
                    st.emit(IrInstr::Op(o));
                    // Cast the promoted result back to the target width
                    // (Java compound-assignment cast; runtime range check).
                    if let Some(prom) = field_ty.base.binary_promotion(&val_ty.base) {
                        if prom != field_ty.base && field_ty.base.is_integral() {
                            if let Some(tag) = crate::ir::conv_target::of(&field_ty.base) {
                                st.emit(IrInstr::Convert(tag));
                            }
                        }
                    }
                }
            }
            st.emit(IrInstr::StoreField(slot));
            // StoreField keeps the receiver; drop it.
            st.emit(IrInstr::Op(IrOp::Pop));
        }
        Expr::StaticAccess(sa) => {
            // Static field assignment: ClassName.field = expr / op= expr.
            let base = resolve_type_ref(
                ctx.diags,
                ctx.program,
                &sa.ty,
                &self_type_params(st),
                st.class_id,
                sa.span,
            );
            let (cid, args) = match &base.base {
                BaseType::Class(c, a) => (*c, a),
                _ => {
                    ctx.err_at("C138", "invalid assignment target", a.target.span());
                    return;
                }
            };
            let info = &ctx.program.classes[cid as usize];
            let (slot, f) = match info
                .static_fields
                .iter()
                .enumerate()
                .find(|(_, f)| f.name == sa.name)
            {
                Some(x) => x,
                None => {
                    ctx.err_at("C138", "invalid assignment target", a.target.span());
                    return;
                }
            };
            if st.class_id != Some(cid) {
                ctx.err_at(
                    "C162",
                    format!(
                        "field '{}' is private to class '{}'",
                        sa.name,
                        ctx.program.class_name(f.declaring)
                    ),
                    a.target.span(),
                );
                return;
            }
            if !f.mutable {
                ctx.err_at(
                    "C226",
                    format!(
                        "field '{}' is immutable; declare it with 'mutable' to assign",
                        sa.name
                    ),
                    a.target.span(),
                );
                return;
            }
            let subst: Vec<Option<BaseType>> = args.iter().map(|x| Some(x.clone())).collect();
            let field_ty = f.ty.substitute(&subst);
            if is_update {
                // Compound update: read the old value first so it ends up
                // under the new value on the stack.
                st.emit(IrInstr::LoadStatic(cid as u16, slot as u16));
            }
            let val_ty = check_expr(ctx, st, &a.value);
            let concat_update = uop == Some(UpdateOp::Concat);
            if concat_update {
                // `T.f ..= v` desugars to `T.f = T.f .. v`: the field must
                // be a String; the operand is formatted like any `..` operand.
                if !matches!(field_ty.base, BaseType::String) {
                    ctx.err_at("C155", "'..=' requires a String target", a.target.span());
                }
            } else if is_update {
                let prom = field_ty.base.binary_promotion(&val_ty.base);
                if prom != Some(field_ty.base.clone())
                    && !(prom.is_some() && field_ty.base.is_integral() && val_ty.base.is_integral())
                {
                    ctx.err_at(
                        "C132",
                        format!(
                            "cannot assign {} to field '{}': expected {}",
                            type_display(ctx.program, &val_ty),
                            sa.name,
                            type_display(ctx.program, &field_ty)
                        ),
                        a.span,
                    );
                }
            } else if !crate::types::is_subtype(&val_ty, &field_ty, ctx.program)
                && !coerce_value(st, &val_ty, &field_ty, &a.value)
            {
                ctx.err_at(
                    "C132",
                    format!(
                        "cannot assign {} to field '{}': expected {}",
                        type_display(ctx.program, &val_ty),
                        sa.name,
                        type_display(ctx.program, &field_ty)
                    ),
                    a.span,
                );
            } else if !concat_update {
                emit_coerce(st, &val_ty, &field_ty);
            }
            if is_update {
                if concat_update {
                    st.emit(IrInstr::Op(IrOp::StrConcat));
                } else {
                    let bin = match uop.unwrap() {
                        UpdateOp::Add => BinOp::Add,
                        UpdateOp::Sub => BinOp::Sub,
                        UpdateOp::Mul => BinOp::Mul,
                        UpdateOp::Div => BinOp::Div,
                        UpdateOp::Mod => BinOp::Mod,
                        UpdateOp::Concat => unreachable!("concat update handled above"),
                    };
                    let o = match bin {
                        BinOp::Add => IrOp::Add,
                        BinOp::Sub => IrOp::Sub,
                        BinOp::Mul => IrOp::Mul,
                        BinOp::Div => IrOp::Div,
                        BinOp::Mod => IrOp::Mod,
                        _ => unreachable!(),
                    };
                    st.emit(IrInstr::Op(o));
                    // Cast the promoted result back to the target width
                    // (Java compound-assignment cast; runtime range check).
                    if let Some(prom) = field_ty.base.binary_promotion(&val_ty.base) {
                        if prom != field_ty.base && field_ty.base.is_integral() {
                            if let Some(tag) = crate::ir::conv_target::of(&field_ty.base) {
                                st.emit(IrInstr::Convert(tag));
                            }
                        }
                    }
                }
            }
            st.emit(IrInstr::StoreStatic(cid as u16, slot as u16));
        }
        other => {
            ctx.err_at("C138", "invalid assignment target", other.span());
        }
    }
}

fn require_int_index(ctx: &mut Ctx<'_>, _st: &mut FnState, idx_ty: &Ty, idx: &Expr) {
    if !idx_ty.base.is_integral() {
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
                "C149",
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
            // Collection literals infer element types from the declared
            // return type, like local declarations.
            let saved = st.expected_literal.clone();
            if matches!(e, Expr::List(_, _) | Expr::Map(_, _)) {
                if let Some(ty) = &st.ret_ty {
                    st.expected_literal = Some(ty.clone());
                }
            }
            let vty = check_expr(ctx, st, e);
            st.expected_literal = saved;
            match &st.ret_ty {
                Some(ty) => {
                    if !crate::types::is_subtype(&vty, ty, ctx.program) {
                        // Java-style widening at the return (e.g. an Integer
                        // expression from a Long function).
                        if !vty.nullable && vty.base.widens_to(&ty.base) {
                            if let Some(tag) = crate::ir::conv_target::of(&ty.base) {
                                st.emit(IrInstr::Convert(tag));
                            }
                        } else {
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
    let some_catch_falls = t.catches.iter().any(|c| !block_diverges(&c.body.stmts));
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
    let falls_past = (!body_div && !fin_div) || (some_catch_falls && !fin_div);
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

// ---------------------------------------------------------------------------
// Definite assignment (C239): a pure AST pre-pass run once per function
// body before IR emission. It tracks, per scope depth, which declared
// names are definitely assigned at each program point, following Java's
// rules: intersection across branch joins, fixed point over loop bodies,
// conservative union into finally bodies.
// ---------------------------------------------------------------------------

/// Snapshot of the three per-depth name sets carried across joins.
type DaSnap = (
    Vec<std::collections::HashSet<String>>,
    Vec<std::collections::HashSet<String>>,
    Vec<std::collections::HashSet<String>>,
);

struct Da<'a> {
    diags: &'a mut Diagnostics,
    /// Names declared at each scope depth.
    declared: Vec<std::collections::HashSet<String>>,
    /// Names definitely assigned at each scope depth.
    assigned: Vec<std::collections::HashSet<String>>,
    /// Mutable local names at each scope depth (for the assign-once rule).
    mutable_names: Vec<std::collections::HashSet<String>>,
    /// Suppress diagnostics during fixed-point iterations (the final pass
    /// reports).
    report: bool,
}

impl<'a> Da<'a> {
    fn new(diags: &'a mut Diagnostics, params: &[String], has_self: bool) -> Self {
        let mut declared = vec![std::collections::HashSet::new()];
        let mut assigned = vec![std::collections::HashSet::new()];
        if has_self {
            declared[0].insert("self".into());
            assigned[0].insert("self".into());
        }
        for p in params {
            declared[0].insert(p.clone());
            assigned[0].insert(p.clone());
        }
        let mutable_names = vec![std::collections::HashSet::new()];
        Da {
            diags,
            declared,
            assigned,
            mutable_names,
            report: true,
        }
    }

    fn depth(&self) -> usize {
        self.declared.len() - 1
    }

    fn visible(&self, name: &str) -> bool {
        self.declared.iter().any(|s| s.contains(name))
    }

    fn is_assigned(&self, name: &str) -> bool {
        self.assigned.iter().any(|s| s.contains(name))
    }

    fn declare(&mut self, name: &str, mutable: bool) {
        let d = self.declared.len() - 1;
        self.declared[d].insert(name.into());
        if mutable {
            self.mutable_names[d].insert(name.into());
        }
    }

    /// True when `name` is a visible immutable local (assign-once rule).
    fn is_immutable_local(&self, name: &str) -> bool {
        if !self.visible(name) {
            return false;
        }
        !self.mutable_names.iter().any(|s| s.contains(name))
    }

    /// Mark `name` assigned at its declaring depth (no-op for names that
    /// are not visible locals — fields and statics are always available).
    fn assign(&mut self, name: &str) {
        if let Some(d) = self.declared.iter().rposition(|s| s.contains(name)) {
            self.assigned[d].insert(name.into());
        }
    }

    fn read(&mut self, name: &str, span: crate::source::Span) {
        if self.report && self.visible(name) && !self.is_assigned(name) {
            self.diags.err_at(
                "C239",
                format!("variable '{}' may not have been initialized", name),
                span,
            );
        }
    }

    fn begin(&mut self) {
        self.declared.push(std::collections::HashSet::new());
        self.assigned.push(std::collections::HashSet::new());
        self.mutable_names.push(std::collections::HashSet::new());
    }

    fn end(&mut self) {
        self.declared.pop();
        self.assigned.pop();
        self.mutable_names.pop();
    }

    fn snapshot(&self) -> DaSnap {
        (
            self.declared.clone(),
            self.assigned.clone(),
            self.mutable_names.clone(),
        )
    }

    fn restore(&mut self, snap: DaSnap) {
        self.declared = snap.0;
        self.assigned = snap.1;
        self.mutable_names = snap.2;
    }

    /// Join point: keep only names assigned on every incoming path.
    fn intersect_paths(&mut self, paths: &[Vec<std::collections::HashSet<String>>]) {
        let n = self.assigned.len();
        for i in 0..n {
            let mut acc = paths[0].get(i).cloned().unwrap_or_default();
            for p in &paths[1..] {
                let next = p.get(i).cloned().unwrap_or_default();
                acc = acc.intersection(&next).cloned().collect();
            }
            self.assigned[i] = acc;
        }
    }

    /// Union of all incoming paths (finally bodies run from any path).
    fn union_paths(&mut self, paths: &[Vec<std::collections::HashSet<String>>]) {
        let n = self.assigned.len();
        for i in 0..n {
            let mut acc = std::collections::HashSet::new();
            for p in paths {
                if let Some(s) = p.get(i) {
                    acc.extend(s.iter().cloned());
                }
            }
            self.assigned[i] = acc;
        }
    }
}

/// Entry point: check definite assignment over a whole function body.
fn da_check_body(diags: &mut Diagnostics, body: &Block, params: &[String], has_self: bool) {
    let mut d = Da::new(diags, params, has_self);
    da_block(&mut d, &body.stmts);
}

/// Analyze a block sequentially; returns whether control diverges.
fn da_block(d: &mut Da, stmts: &[Stmt]) -> bool {
    let mut diverged = false;
    for s in stmts {
        if diverged {
            break; // unreachable tail: the main pass reports C245
        }
        diverged = da_stmt(d, s);
    }
    diverged
}

fn da_stmt(d: &mut Da, s: &Stmt) -> bool {
    match s {
        Stmt::Decl(x) => {
            if let Some(init) = &x.init {
                da_expr(d, init);
            }
            d.declare(&x.name, x.mutable);
            // A declaration without initializer leaves the binding
            // definitely unassigned; the first assignment initializes it.
            if x.init.is_some() {
                d.assign(&x.name);
            }
            false
        }
        Stmt::Expr(e) => {
            da_expr(d, &e.expr);
            false
        }
        Stmt::Return(v) => {
            if let Some(e) = v {
                da_expr(d, e);
            }
            true
        }
        Stmt::Throw(e) => {
            da_expr(d, e);
            true
        }
        Stmt::Break | Stmt::Continue => true,
        Stmt::If(i) => da_if(d, i),
        Stmt::While(w) => {
            da_expr(d, &w.cond);
            let (dec, asn, muts) = d.snapshot();
            // Fixed point: re-enter the body with everything assigned by
            // previous iterations (monotone growth, bounded by name count).
            let mut cur = asn.clone();
            for _ in 0..64 {
                d.restore((dec.clone(), cur.clone(), muts.clone()));
                d.report = false;
                d.begin();
                da_block(d, &w.body.stmts);
                d.end();
                d.report = true;
                let exit = d.assigned.clone();
                let next: Vec<std::collections::HashSet<String>> = (0..cur.len())
                    .map(|i| {
                        let extra = exit.get(i).cloned().unwrap_or_default();
                        cur[i].union(&extra).cloned().collect()
                    })
                    .collect();
                if next == cur {
                    break;
                }
                cur = next;
            }
            // Final pass with diagnostics enabled.
            d.restore((dec.clone(), cur, muts.clone()));
            d.begin();
            da_block(d, &w.body.stmts);
            d.end();
            // After the loop only pre-loop assignments survive (the loop
            // may not have executed).
            d.restore((dec, asn, muts));
            false
        }
        Stmt::ForIn(f) => {
            da_expr(d, &f.iter);
            let (dec, asn, muts) = d.snapshot();
            let mut cur = asn.clone();
            for _ in 0..64 {
                d.restore((dec.clone(), cur.clone(), muts.clone()));
                d.report = false;
                d.begin();
                d.declare(&f.var, false);
                d.assign(&f.var);
                da_block(d, &f.body.stmts);
                d.end();
                d.report = true;
                let exit = d.assigned.clone();
                let next: Vec<std::collections::HashSet<String>> = (0..cur.len())
                    .map(|i| {
                        let extra = exit.get(i).cloned().unwrap_or_default();
                        cur[i].union(&extra).cloned().collect()
                    })
                    .collect();
                if next == cur {
                    break;
                }
                cur = next;
            }
            d.restore((dec.clone(), cur, muts.clone()));
            d.begin();
            d.declare(&f.var, false);
            d.assign(&f.var);
            da_block(d, &f.body.stmts);
            d.end();
            // The loop variable is scoped to the statement; after the loop
            // only pre-loop assignments survive.
            d.restore((dec, asn, muts));
            false
        }
        Stmt::Switch(sw) => {
            da_expr(d, &sw.subject);
            let (dec, asn, muts) = d.snapshot();
            let mut paths: Vec<Vec<std::collections::HashSet<String>>> = vec![];
            for c in &sw.cases {
                d.begin();
                da_block(d, &c.body.stmts);
                paths.push(d.assigned.clone());
                d.restore((dec.clone(), asn.clone(), muts.clone()));
            }
            if !sw.cases.iter().any(|c| c.is_default) {
                // No default: control may fall through unhandled.
                paths.push(asn.clone());
            }
            d.restore((dec, asn, muts));
            d.intersect_paths(&paths);
            switch_diverges(sw)
        }
        Stmt::Try(t) => {
            let (dec, asn, muts) = d.snapshot();
            d.begin();
            da_block(d, &t.body.stmts);
            let a_try = d.assigned.clone();
            d.restore((dec.clone(), asn.clone(), muts.clone()));
            let mut paths = vec![a_try];
            for c in &t.catches {
                d.begin();
                d.declare(&c.name, false);
                d.assign(&c.name);
                da_block(d, &c.body.stmts);
                paths.push(d.assigned.clone());
                d.restore((dec.clone(), asn.clone(), muts.clone()));
            }
            if let Some(fin) = &t.finally_body {
                d.union_paths(&paths);
                d.begin();
                da_block(d, &fin.stmts);
                paths.push(d.assigned.clone());
                d.end();
            }
            d.restore((dec, asn, muts));
            d.intersect_paths(&paths);
            try_diverges(t)
        }
        Stmt::ScopeBlock(b) => {
            d.begin();
            let div = da_block(d, &b.stmts);
            d.end();
            div
        }
    }
}

fn da_if(d: &mut Da, i: &IfStmt) -> bool {
    da_expr(d, &i.cond);
    let (dec, asn, muts) = d.snapshot();
    d.begin();
    let then_div = da_block(d, &i.then.stmts);
    let a1 = d.assigned.clone();
    d.restore((dec.clone(), asn.clone(), muts.clone()));
    d.begin();
    let else_div = match &i.else_branch {
        Some(ElseBranch::Block(b)) => da_block(d, &b.stmts),
        Some(ElseBranch::If(inner)) => da_if(d, inner),
        None => false,
    };
    let a2 = d.assigned.clone();
    d.restore((dec, asn, muts));
    d.intersect_paths(&[a1, a2]);
    then_div && else_div
}

fn da_expr(d: &mut Da, e: &Expr) {
    match e {
        Expr::Ident(n, span) => d.read(n, *span),
        Expr::Binary(b) => {
            da_expr(d, &b.left);
            da_expr(d, &b.right);
        }
        Expr::Unary(_, inner) => da_expr(d, inner),
        Expr::Coalesce(l, r) => {
            da_expr(d, l);
            da_expr(d, r);
        }
        Expr::Range(l, r) => {
            da_expr(d, l);
            da_expr(d, r);
        }
        Expr::Call(c) => {
            da_expr(d, &c.callee);
            for a in &c.args {
                da_expr(d, &a.expr);
            }
        }
        Expr::Member(m) => da_expr(d, &m.obj),
        Expr::StaticAccess(_) => {}
        Expr::List(elts, _) => {
            for el in elts {
                da_expr(d, el);
            }
        }
        Expr::Map(entries, _) => {
            for (k, v) in entries {
                da_expr(d, k);
                da_expr(d, v);
            }
        }
        Expr::Match(m) => {
            da_expr(d, &m.subject);
            for arm in &m.arms {
                d.begin();
                da_bindings(d, &arm.pattern);
                if let Some(g) = &arm.guard {
                    da_expr(d, g);
                }
                da_expr(d, &arm.body);
                d.end();
            }
        }
        Expr::SelfInit(si) => {
            for (_, init) in &si.fields {
                da_expr(d, init);
            }
        }
        Expr::Assign(a) => {
            da_assign_target(d, a, true);
        }
        Expr::Update(_, a) => {
            // Update operators read the target first.
            if let Expr::Ident(n, span) = &*a.target {
                d.read(n, *span);
            }
            da_assign_target(d, a, true);
        }
        Expr::Null(_)
        | Expr::Int(..)
        | Expr::Real(..)
        | Expr::Bool(..)
        | Expr::Char(..)
        | Expr::String(..) => {}
    }
}

fn da_assign_target(d: &mut Da, a: &AssignExpr, mark_assigned: bool) {
    match &*a.target {
        Expr::Ident(n, span) => {
            // Assign-once rule: an immutable local may be assigned only
            // while it is still definitely unassigned (blank-variable
            // initialization). A second assignment on the same path is C134.
            if d.report && d.is_immutable_local(n) && d.is_assigned(n) {
                d.diags.err_at(
                    "C134",
                    format!("cannot assign to immutable variable '{}'", n),
                    *span,
                );
            }
            if mark_assigned {
                d.assign(n);
            }
        }
        other => da_expr(d, other),
    }
    da_expr(d, &a.value);
}

fn da_bindings(d: &mut Da, p: &Pattern) {
    match p {
        Pattern::Bind(name) => {
            d.declare(name, false);
            d.assign(name);
        }
        Pattern::Variant(_, subs) | Pattern::List(subs) => {
            for sp in subs {
                da_bindings(d, sp);
            }
        }
        _ => {}
    }
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
                st.ctx_stack.push(CtxOwner::ElseIf);
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
    if !matches!(ty.base, BaseType::Boolean) {
        ctx.err_at(
            "C144",
            format!(
                "condition must be Boolean, found {}",
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
    // Block scope: loop-body locals are visible only inside the body (Rust
    // parity). `inner` below is therefore gone after the loop.
    st.begin_scope();
    check_block(ctx, st, &s.body, CtxOwner::Loop);
    st.end_scope();
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
    // An integer range (`a..b`) is only legal as an iterator expression;
    // the flag is consumed by the range when present.
    st.in_for_in_iter = true;
    let iter_ty = check_expr(ctx, st, &s.iter);
    st.in_for_in_iter = false;
    #[derive(Clone, Copy, PartialEq)]
    enum Kind {
        List,
        Stack,
        Set,
        String,
        MapKeys,
        Range,
        Other,
    }
    let (kind, elem) = match &iter_ty.base {
        BaseType::List(e) => (Kind::List, e.as_ref().clone()),
        BaseType::Stack(e) => (Kind::Stack, e.as_ref().clone()),
        BaseType::Set(e) => (Kind::Set, e.as_ref().clone()),
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
        Kind::Set => {
            // Materialize the members as a list first. Iteration order is
            // unspecified (hash layout), matching Set's unordered contract.
            st.emit(IrInstr::CallNative(
                crate::stdlib::builtins::nat::SET_TO_LIST,
                0,
            ));
        }
        Kind::Range => {
            // Stack: [start, end].
            let s_slot = st.decl_temp("$rs", Ty::long());
            let e_slot = st.decl_temp("$re", Ty::long());
            let i_slot = st.decl_temp("$ri", Ty::long());
            // Stack is [start, end]; pop in reverse order.
            st.emit(IrInstr::StoreLocal(e_slot as u16));
            st.emit(IrInstr::StoreLocal(s_slot as u16));
            st.emit(IrInstr::LoadLocal(s_slot as u16));
            st.emit(IrInstr::StoreLocal(i_slot as u16));
            let start = st.func.instrs.len() as u32;
            st.emit(IrInstr::LoadLocal(i_slot as u16));
            st.emit(IrInstr::LoadLocal(e_slot as u16));
            st.emit(IrInstr::Op(IrOp::Lt));
            let end_ip = st.emit(IrInstr::JumpIfFalse(0));
            // Block scope: the loop variable lives only inside the body.
            st.begin_scope();
            check_redeclaration(ctx, st, &s.var, s.var_span);
            let var_slot =
                st.decl_local(&s.var, Ty::non_null(elem.clone()), false, s.var_span, false);
            st.emit(IrInstr::LoadLocal(i_slot as u16));
            st.emit(IrInstr::StoreLocal(var_slot as u16));
            check_block(ctx, st, &s.body, CtxOwner::Loop);
            st.end_scope();
            let (breaks, conts) = st.loops.pop().unwrap();
            // When every body path returns/throws/breaks, the increment and
            // back-edge are dead code (and no continue can target them).
            if !block_exits(&s.body.stmts) {
                let inc_ip = st.func.instrs.len() as u32;
                st.emit(IrInstr::LoadLocal(i_slot as u16));
                let one = ctx.ir.intern_const(crate::ir::IrConst::Long(1));
                st.emit(IrInstr::LoadConst(one));
                st.emit(IrInstr::Op(IrOp::Add));
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
    let iter_slot = st.decl_temp("$iter", iter_ty.clone());
    st.emit(IrInstr::StoreLocal(iter_slot as u16));
    let len_slot = st.decl_temp("$len", Ty::long());
    let i_slot = st.decl_temp("$i", Ty::long());
    st.emit(IrInstr::LoadLocal(iter_slot as u16));
    st.emit(IrInstr::Op(len_op));
    st.emit(IrInstr::StoreLocal(len_slot as u16));
    let zero = ctx.ir.intern_const(crate::ir::IrConst::Long(0));
    st.emit(IrInstr::LoadConst(zero));
    st.emit(IrInstr::StoreLocal(i_slot as u16));
    let start = st.func.instrs.len() as u32;
    st.emit(IrInstr::LoadLocal(i_slot as u16));
    st.emit(IrInstr::LoadLocal(len_slot as u16));
    st.emit(IrInstr::Op(IrOp::Lt));
    let end_ip = st.emit(IrInstr::JumpIfFalse(0));
    st.emit(IrInstr::LoadLocal(iter_slot as u16));
    st.emit(IrInstr::Op(IrOp::NullCheck));
    st.emit(IrInstr::LoadLocal(i_slot as u16));
    st.emit(IrInstr::Op(get_op));
    // Block scope: the loop variable lives only inside the body.
    st.begin_scope();
    check_redeclaration(ctx, st, &s.var, s.var_span);
    let var_slot = st.decl_local(&s.var, Ty::non_null(elem.clone()), false, s.var_span, false);
    st.emit(IrInstr::StoreLocal(var_slot as u16));
    check_block(ctx, st, &s.body, CtxOwner::Loop);
    st.end_scope();
    let (breaks, conts) = st.loops.pop().unwrap();
    // When every body path returns/throws/breaks, the increment and
    // back-edge are dead code (and no continue can target them).
    if !block_exits(&s.body.stmts) {
        let inc_ip = st.func.instrs.len() as u32;
        st.emit(IrInstr::LoadLocal(i_slot as u16));
        let one = ctx.ir.intern_const(crate::ir::IrConst::Long(1));
        st.emit(IrInstr::LoadConst(one));
        st.emit(IrInstr::Op(IrOp::Add));
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
                if let (Expr::Ident(n, _), Expr::Null(_)) = (&*b.left, &*b.right) {
                    Some((n, then_branch))
                } else if let (Expr::Null(_), Expr::Ident(n, _)) = (&*b.left, &*b.right) {
                    Some((n, then_branch))
                } else {
                    None
                }
            }
            BinOp::Eq => {
                if let (Expr::Ident(n, _), Expr::Null(_)) = (&*b.left, &*b.right) {
                    Some((n, !then_branch))
                } else if let (Expr::Null(_), Expr::Ident(n, _)) = (&*b.left, &*b.right) {
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
    let subj_slot = st.decl_temp("$subj", subj_ty.clone());
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
        // Block scope: each case body is its own name scope, so sibling
        // cases may reuse the same name without a shadow warning.
        st.begin_scope();
        check_block(ctx, st, &case.body, CtxOwner::SwitchCase);
        st.end_scope();
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
    let has_finally = s.finally_body.is_some();
    // Layout:
    //   TryBegin catch finally        (finally == 0: no finally)
    //   body
    //   [TryEnd]                      (body can complete normally)
    //   [jmp finally]                 (normal completion skips the clauses)
    // catch: StoreLocal($ex)          (the body region was already popped
    //                                   by the unwinder; the exception is
    //                                   on top of the stack)
    //        for each clause i:
    //          LoadLocal($ex)
    //          Conforms(id_i, kind_i)
    //          JumpIfTrue handler_i
    //        LoadLocal($ex); Throw    (no clause matched: rethrow)
    // handler_i: LoadLocal($ex)
    //            StoreLocal(var_i)
    //            [TryBegin finally finally]
    //                                  (exceptions raised in the catch body
    //                                  still run the finally)
    //            catch body
    //            [TryEnd]             (catch can complete normally)
    //            [jmp finally]
    // finally: finally body
    //          [FinallyEnd]           (rethrow a passed-through exception,
    //                                  complete a deferred return, or resume
    //                                  a diverted break/continue)
    let tb_idx = st.emit(IrInstr::TryBegin(0, 0));
    let body_div = block_diverges(&s.body.stmts);
    // Block scope: try-body locals don't leak past the try statement.
    st.begin_scope();
    check_block(ctx, st, &s.body, CtxOwner::TryBody { has_finally });
    st.end_scope();
    // The region is closed only when the body can complete normally; when
    // every body path leaves (returns/throws/breaks), TryEnd would be dead
    // code (the VM cleans regions up on return/unwind, and break/continue
    // trampolines pop or divert the region explicitly).
    if !body_div {
        st.emit(IrInstr::Op(IrOp::TryEnd));
    }
    // Normal completion must skip the catch/finally clauses. Skipped when
    // the body cannot complete normally (the jump would be dead code).
    let normal_jump = if (has_finally || !s.catches.is_empty()) && !body_div {
        Some(st.emit(IrInstr::Jump(0)))
    } else {
        None
    };
    let catch_at = st.func.instrs.len() as u32;
    // Resolve every catch type up front so bad types are reported before
    // any dispatch code is emitted.
    let mut clauses: Vec<(u16, u8, Ty)> = vec![];
    for c in &s.catches {
        // `Throwable` is the built-in catch-all interface; it resolves to
        // Object as a plain type, so recognize it by name here.
        if let crate::ast::TypeBase::Named(n) = &c.ty.base {
            if n == "Throwable" && !c.ty.nullable {
                clauses.push((
                    crate::resolve::builtin::THROWSABLE as u16,
                    crate::ir::conforms_kind::INTERFACE,
                    Ty::object(),
                ));
                continue;
            }
        }
        let ty = resolve_type_ref(
            ctx.diags,
            ctx.program,
            &c.ty,
            &self_type_params(st),
            st.class_id,
            c.ty.span,
        );
        let kind = match &ty.base {
            BaseType::Class(cid, _) => (*cid as u16, crate::ir::conforms_kind::CLASS),
            BaseType::Interface(iid, _) => (*iid as u16, crate::ir::conforms_kind::INTERFACE),
            BaseType::Native(k) if *k == crate::types::native_kind::EXCEPTION => {
                (0, crate::ir::conforms_kind::EXCEPTION)
            }
            _ => {
                ctx.err_at(
                    "C241",
                    format!(
                        "catch type must be a class, an interface, or Exception, found {}",
                        type_display(ctx.program, &ty)
                    ),
                    c.ty.span,
                );
                continue;
            }
        };
        clauses.push((kind.0, kind.1, Ty::new(ty.base.clone(), false)));
    }
    // Unreachable-catch check: a clause is dead when an earlier clause
    // already conforms every value it could bind. With no class
    // inheritance this means: identical types, a class behind an earlier
    // interface it implements, or an interface behind an earlier interface
    // it extends.
    for (i, ci) in clauses.iter().enumerate() {
        for cj in &clauses[..i] {
            let shadowed = match (&cj.2.base, &ci.2.base) {
                // A preceding Throwable clause catches everything.
                (BaseType::Interface(jid, _), _) if *jid == crate::resolve::builtin::THROWSABLE => {
                    true
                }
                // Throwable itself is shadowed by any earlier catch-all.
                (_, BaseType::Interface(iid, _)) if *iid == crate::resolve::builtin::THROWSABLE => {
                    matches!(
                        cj.2.base,
                        BaseType::Interface(jid, _) if jid == crate::resolve::builtin::THROWSABLE
                    )
                }
                (a, b) if a == b => true,
                (BaseType::Interface(jid, _), BaseType::Class(cid, _)) => ctx.program.classes
                    [*cid as usize]
                    .all_interfaces
                    .contains(jid),
                (BaseType::Interface(jid, _), BaseType::Interface(iid, _)) => {
                    *jid == *iid || ctx.program.interface_extends(*iid, *jid)
                }
                _ => false,
            };
            if shadowed {
                ctx.err_at(
                    "C243",
                    format!(
                        "unreachable catch: {} is already handled by an earlier clause",
                        type_display(ctx.program, &ci.2)
                    ),
                    s.catches[i].ty.span,
                );
                break;
            }
        }
    }
    // (jump idx, target): real clause targets carry the clause index;
    // usize::MAX marks a normal-completion jump into the finally.
    let mut jumps: Vec<(usize, usize)> = vec![];
    let mut r2_idxs: Vec<usize> = vec![];
    if !clauses.is_empty() {
        // Temp local that holds the in-flight exception while the clauses
        // are tested in source order; the first matching clause copies it
        // into its own parameter.
        let ex_slot = st.decl_temp("$ex", Ty::object());
        st.emit(IrInstr::StoreLocal(ex_slot as u16));
        for (i, &(cid, kind, _)) in clauses.iter().enumerate() {
            st.emit(IrInstr::LoadLocal(ex_slot as u16));
            st.emit(IrInstr::Conforms(cid, kind));
            let j = st.emit(IrInstr::JumpIfTrue(0));
            jumps.push((j, i));
        }
        // No clause matched: rethrow the original exception.
        st.emit(IrInstr::LoadLocal(ex_slot as u16));
        st.emit(IrInstr::Op(IrOp::Throw));
        for (j, i) in jumps.clone() {
            st.patch(j, st.func.instrs.len() as u32);
            let (_, _, var_ty) = &clauses[i];
            let clause = &s.catches[i];
            // Block scope: the catch parameter stops leaking past the
            // clause body. Each clause has its own scope, so sibling
            // clauses may reuse the same parameter name.
            st.begin_scope();
            check_redeclaration(ctx, st, &clause.name, clause.name_span);
            let var_slot =
                st.decl_local(&clause.name, var_ty.clone(), false, clause.name_span, false);
            st.emit(IrInstr::LoadLocal(ex_slot as u16));
            st.emit(IrInstr::StoreLocal(var_slot as u16));
            if has_finally {
                r2_idxs.push(st.emit(IrInstr::TryBegin(0, 0)));
            }
            let cdiv = block_diverges(&clause.body.stmts);
            check_block(ctx, st, &clause.body, CtxOwner::CatchBody { has_finally });
            st.end_scope();
            if has_finally && !cdiv {
                st.emit(IrInstr::Op(IrOp::TryEnd));
                let fj = st.emit(IrInstr::Jump(0));
                jumps.push((fj, usize::MAX));
            }
        }
    }
    // The finally body runs on normal completion of the body or any catch,
    // when an exception passes through (no matching catch), and when an
    // exception is raised inside a catch body; with the catch-body region
    // above it is reachable whenever present, so it is always emitted.
    let finally_at = st.func.instrs.len() as u32;
    if has_finally {
        let fin_body = s.finally_body.as_ref().unwrap();
        let fin_div = block_diverges(&fin_body.stmts);
        // Block scope: finally-body locals don't leak past the finally.
        st.begin_scope();
        check_block(ctx, st, fin_body, CtxOwner::Finally);
        st.end_scope();
        if !fin_div {
            // An exception may be passing through, a return may be
            // deferred, or a diverted break/continue may resume here.
            st.emit(IrInstr::Op(IrOp::FinallyEnd));
        }
    }
    if let Some(j) = normal_jump {
        st.patch(j, finally_at);
    }
    for (j, i) in jumps {
        if i == usize::MAX {
            st.patch(j, finally_at);
        }
    }
    for &i in &r2_idxs {
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
        Expr::Int(lit, _) => {
            // Unsuffixed integer literals are Integer when they fit i32,
            // Long when they fit i64, and BigInteger beyond that.
            let (c, ty) = match lit {
                crate::ast::IntLiteral::I64(v) if (*v as i32) as i64 == *v => {
                    (crate::ir::IrConst::Integer(*v as i32), Ty::integer())
                }
                crate::ast::IntLiteral::I64(v) => (crate::ir::IrConst::Long(*v), Ty::long()),
                crate::ast::IntLiteral::Big(s) => {
                    (crate::ir::IrConst::BigInt(s.clone()), Ty::big_integer())
                }
            };
            let c = ctx.ir.intern_const(c);
            st.emit(IrInstr::LoadConst(c));
            ty
        }
        Expr::Real(lit, _) => {
            // Unsuffixed real literals are Double; `f`/`F` selects Float;
            // `bd`/`BD` selects BigDecimal with exact decimal text.
            let (c, ty) = match lit {
                crate::ast::RealLiteral::Float(f) => (crate::ir::IrConst::Float(*f), Ty::float_()),
                crate::ast::RealLiteral::Double(d) => {
                    (crate::ir::IrConst::Double(*d), Ty::double())
                }
                crate::ast::RealLiteral::Decimal(s) => {
                    (crate::ir::IrConst::BigDecimal(s.clone()), Ty::big_decimal())
                }
            };
            let c = ctx.ir.intern_const(c);
            st.emit(IrInstr::LoadConst(c));
            ty
        }
        Expr::Bool(v, _) => {
            let c = ctx.ir.intern_const(crate::ir::IrConst::Bool(*v));
            st.emit(IrInstr::LoadConst(c));
            Ty::boolean()
        }
        Expr::Char(v, _) => {
            let c = ctx.ir.intern_const(crate::ir::IrConst::Char(*v));
            st.emit(IrInstr::LoadConst(c));
            Ty::char_()
        }
        Expr::String(v, _) => {
            let c = ctx.ir.intern_const(crate::ir::IrConst::Str(v.clone()));
            st.emit(IrInstr::LoadConst(c));
            Ty::string()
        }
        Expr::Null(_) => {
            let c = ctx.ir.intern_const(crate::ir::IrConst::Null);
            st.emit(IrInstr::LoadConst(c));
            Ty::null()
        }
        Expr::Ident(name, _) => check_ident(ctx, st, name, e.span()),
        Expr::List(elements, _) => check_list(ctx, st, elements),
        Expr::Map(entries, _) => check_map(ctx, st, entries),
        Expr::Call(c) => check_call(ctx, st, c),
        Expr::Member(m) => check_member(ctx, st, m),
        Expr::StaticAccess(sa) => check_static_access_value(ctx, st, sa),
        Expr::Binary(b) => check_binary(ctx, st, b),
        Expr::Unary(op, inner) => check_unary(ctx, st, *op, inner),
        Expr::Coalesce(l, r) => check_coalesce(ctx, st, l, r),
        Expr::Range(l, r) => check_range(ctx, st, l, r),
        Expr::Match(m) => check_match(ctx, st, m),
        Expr::SelfInit(si) => check_self_init(ctx, st, si),
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
    // Inside a static block, a bare name resolves to a static field of the
    // declaring class (the block runs after every field initializer, so the
    // slot is fully initialized). Locals shadow static fields.
    if st.in_static_block {
        if let Some(cid) = st.class_id {
            let info = &ctx.program.classes[cid as usize];
            if let Some((slot, f)) = info
                .static_fields
                .iter()
                .enumerate()
                .find(|(_, f)| f.name == name)
            {
                st.emit(IrInstr::LoadStatic(cid as u16, slot as u16));
                return f.ty.clone();
            }
        }
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
    // Built-in scalar constants: `Integer.MAX_VALUE`, `Double.NaN`, ...
    if let Some((c, ty)) = builtin_scalar_constant(&base.base, &sa.name) {
        let ci = ctx.ir.intern_const(c);
        st.emit(IrInstr::LoadConst(ci));
        return ty;
    }
    if let BaseType::Class(cid, args) = &base.base {
        // Static field read: `ClassName.field` / `Self.field`. Fields are
        // private to their declaring class; access is type-qualified only.
        let info = &ctx.program.classes[*cid as usize];
        if let Some((slot, f)) = info
            .static_fields
            .iter()
            .enumerate()
            .find(|(_, f)| f.name == sa.name)
        {
            if st.in_static_init && !st.in_static_block {
                ctx.err_at(
                    "C233",
                    format!(
                        "static field '{}' cannot be read during static initialization",
                        sa.name
                    ),
                    sa.span,
                );
                return Ty::object();
            }
            if st.class_id != Some(*cid) {
                ctx.err_at(
                    "C162",
                    format!(
                        "field '{}' is private to class '{}'",
                        sa.name,
                        ctx.program.class_name(f.declaring)
                    ),
                    sa.span,
                );
                return Ty::object();
            }
            let subst: Vec<Option<BaseType>> = args.iter().map(|a| Some(a.clone())).collect();
            let ty = f.ty.substitute(&subst);
            st.emit(IrInstr::LoadStatic(*cid as u16, slot as u16));
            return ty;
        }
        ctx.err_at(
            "C151",
            "static access must be a method call or enum variant",
            sa.span,
        );
        return Ty::object();
    }
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

/// Zero-argument constants exposed on the built-in scalar types.
fn builtin_scalar_constant(base: &BaseType, name: &str) -> Option<(crate::ir::IrConst, Ty)> {
    use crate::ir::IrConst;
    match (base, name) {
        (BaseType::Byte, "MIN_VALUE") => Some((IrConst::Byte(i8::MIN), Ty::byte())),
        (BaseType::Byte, "MAX_VALUE") => Some((IrConst::Byte(i8::MAX), Ty::byte())),
        (BaseType::Short, "MIN_VALUE") => Some((IrConst::Short(i16::MIN), Ty::short_())),
        (BaseType::Short, "MAX_VALUE") => Some((IrConst::Short(i16::MAX), Ty::short_())),
        (BaseType::Integer, "MIN_VALUE") => Some((IrConst::Integer(i32::MIN), Ty::integer())),
        (BaseType::Integer, "MAX_VALUE") => Some((IrConst::Integer(i32::MAX), Ty::integer())),
        (BaseType::Long, "MIN_VALUE") => Some((IrConst::Long(i64::MIN), Ty::long())),
        (BaseType::Long, "MAX_VALUE") => Some((IrConst::Long(i64::MAX), Ty::long())),
        (BaseType::Float, "MIN_VALUE") => Some((IrConst::Float(f32::MIN), Ty::float_())),
        (BaseType::Float, "MAX_VALUE") => Some((IrConst::Float(f32::MAX), Ty::float_())),
        (BaseType::Float, "NaN") => Some((IrConst::Float(f32::NAN), Ty::float_())),
        (BaseType::Float, "POSITIVE_INFINITY") => {
            Some((IrConst::Float(f32::INFINITY), Ty::float_()))
        }
        (BaseType::Float, "NEGATIVE_INFINITY") => {
            Some((IrConst::Float(f32::NEG_INFINITY), Ty::float_()))
        }
        (BaseType::Double, "MIN_VALUE") => Some((IrConst::Double(f64::MIN), Ty::double())),
        (BaseType::Double, "MAX_VALUE") => Some((IrConst::Double(f64::MAX), Ty::double())),
        (BaseType::Double, "NaN") => Some((IrConst::Double(f64::NAN), Ty::double())),
        (BaseType::Double, "POSITIVE_INFINITY") => {
            Some((IrConst::Double(f64::INFINITY), Ty::double()))
        }
        (BaseType::Double, "NEGATIVE_INFINITY") => {
            Some((IrConst::Double(f64::NEG_INFINITY), Ty::double()))
        }
        _ => None,
    }
}

fn check_list(ctx: &mut Ctx<'_>, st: &mut FnState, elements: &[Expr]) -> Ty {
    // Evaluate elements into temp locals (single codegen pass), then
    // build the list.
    // Expected element type from this literal's declared type; nested
    // container literals inherit it as their own expected type.
    let nested_expected: Option<Ty> = match &st.expected_literal {
        Some(exp) => match &exp.base {
            BaseType::List(e) => Some(Ty::non_null(e.as_ref().clone())),
            _ => None,
        },
        None => None,
    };
    let mut tmps = vec![];
    let mut elem: Option<BaseType> = None;
    for el in elements {
        let saved = st.expected_literal.clone();
        if let Some(ne) = &nested_expected {
            if matches!(el, Expr::List(_, _) | Expr::Map(_, _)) {
                st.expected_literal = Some(ne.clone());
            }
        }
        let t = check_expr(ctx, st, el);
        st.expected_literal = saved;
        match &mut elem {
            None => elem = Some(t.base.clone()),
            Some(e) => {
                if *e != t.base {
                    *e = BaseType::Object;
                }
            }
        }
        let slot = st.decl_temp("$lt", t);
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
            && !(actual.nullable == expected.nullable && actual.base.widens_to(&expected.base))
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
        let actual = st
            .locals
            .get(slot)
            .map(|local| local.ty.clone())
            .unwrap_or_else(Ty::object);
        st.emit(IrInstr::LoadLocal(slot as u16));
        // Widen narrower numeric elements to the declared element type
        // (e.g. Integer literals in a `List<Long>`).
        if !actual.nullable && actual.base != elem_ty && actual.base.widens_to(&elem_ty) {
            if let Some(tag) = crate::ir::conv_target::of(&elem_ty) {
                st.emit(IrInstr::Convert(tag));
            }
        }
        st.emit(IrInstr::Op(IrOp::ListAdd));
    }
    Ty::non_null(BaseType::List(Box::new(elem_ty)))
}

fn check_map(ctx: &mut Ctx<'_>, st: &mut FnState, entries: &[(Expr, Expr)]) -> Ty {
    // Single codegen pass: evaluate entries into temp locals first.
    // Expected key/value types from this literal's declared type; nested
    // container literals inside entries inherit them as their own
    // expected type.
    let expected_kv: Option<(BaseType, BaseType)> = match &st.expected_literal {
        Some(exp) => match &exp.base {
            BaseType::Map(k, v) => Some((k.as_ref().clone(), v.as_ref().clone())),
            _ => None,
        },
        None => None,
    };
    let mut ktys = vec![];
    let mut vtys = vec![];
    let mut ktmps = vec![];
    let mut vtmps = vec![];
    for (k, v) in entries {
        let saved_k = st.expected_literal.clone();
        if let Some((ek, _)) = &expected_kv {
            if matches!(k, Expr::List(_, _) | Expr::Map(_, _)) {
                st.expected_literal = Some(Ty::non_null(ek.clone()));
            }
        }
        let kt = check_expr(ctx, st, k);
        st.expected_literal = saved_k;
        let saved_v = st.expected_literal.clone();
        if let Some((_, ev)) = &expected_kv {
            if matches!(v, Expr::List(_, _) | Expr::Map(_, _)) {
                st.expected_literal = Some(Ty::non_null(ev.clone()));
            }
        }
        let vt = check_expr(ctx, st, v);
        st.expected_literal = saved_v;
        let ks = st.decl_temp("$mk", kt.clone());
        let vs = st.decl_temp("$mv", vt.clone());
        // Stack is [k, v]; pop in reverse order.
        st.emit(IrInstr::StoreLocal(vs as u16));
        st.emit(IrInstr::StoreLocal(ks as u16));
        ktmps.push(ks);
        vtmps.push(vs);
        ktys.push(kt);
        vtys.push(vt);
    }
    // Element types: expected annotation wins, otherwise unify.
    let (mut kty, mut vty) = match &expected_kv {
        Some((k, v)) => (k.clone(), v.clone()),
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
        if !crate::types::is_subtype(kt, &Ty::non_null(kty.clone()), ctx.program)
            && !kt.nullable
            && kt.base.widens_to(&kty)
        {
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
        if !crate::types::is_subtype(vt, &Ty::non_null(vty.clone()), ctx.program)
            && !vt.nullable
            && vt.base.widens_to(&vty)
        {
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
    for ((ks, vs), (kt, vt)) in ktmps
        .iter()
        .zip(vtmps.iter())
        .zip(ktys.iter().zip(vtys.iter()))
    {
        // MapPut returns the previous value (Java Map.put shape); keep the
        // map on the stack across entries.
        st.emit(IrInstr::Op(IrOp::Dup));
        st.emit(IrInstr::LoadLocal(*ks as u16));
        emit_coerce(st, kt, &Ty::non_null(kty.clone()));
        st.emit(IrInstr::LoadLocal(*vs as u16));
        emit_coerce(st, vt, &Ty::non_null(vty.clone()));
        st.emit(IrInstr::Op(IrOp::MapPut));
        st.emit(IrInstr::Op(IrOp::Pop));
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
            Ty::boolean()
        }
        BinOp::Lt | BinOp::Le | BinOp::Gt | BinOp::Ge => {
            let lt = check_expr(ctx, st, &b.left);
            let rt = check_expr(ctx, st, &b.right);
            check_orderable(ctx, st, &lt, &rt, b.span);
            emit_ord(ctx, st, b.op, &lt, &rt, b.span);
            Ty::boolean()
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
            match lt.base.binary_promotion(&rt.base) {
                Some(prom) => {
                    // One opcode per operator; the VM dispatches on the
                    // runtime value kinds (which match the promoted type).
                    let o = match b.op {
                        BinOp::Add => IrOp::Add,
                        BinOp::Sub => IrOp::Sub,
                        BinOp::Mul => IrOp::Mul,
                        BinOp::Div => IrOp::Div,
                        BinOp::Mod => IrOp::Mod,
                        _ => unreachable!(),
                    };
                    st.emit(IrInstr::Op(o));
                    Ty::non_null(prom)
                }
                None => {
                    ctx.err_at(
                        "C158",
                        format!(
                            "arithmetic requires numeric operands, found {} and {}",
                            type_display(ctx.program, &lt),
                            type_display(ctx.program, &rt)
                        ),
                        b.span,
                    );
                    // Balance the checker's stack: both operands were pushed.
                    st.emit(IrInstr::Op(IrOp::Pop));
                    st.emit(IrInstr::Op(IrOp::Pop));
                    Ty::object()
                }
            }
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
            } else if lt.base.is_integral() && rt.base.is_integral() {
                if st.in_for_in_iter {
                    // Integer range: both bounds stay on the stack for the
                    // for-in loop to consume. Consume the flag so a nested
                    // range is rejected.
                    st.in_for_in_iter = false;
                    Ty::non_null(BaseType::Range)
                } else {
                    ctx.err_at(
                        "C147",
                        "range expressions can only be used in for-in loops",
                        b.span,
                    );
                    // Balance the checker's stack: both bounds were pushed,
                    // but this expression yields a single (error) value.
                    st.emit(IrInstr::Op(IrOp::Pop));
                    Ty::object()
                }
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
    let ok = lt.base.is_numeric()
        || matches!(
            lt.base,
            BaseType::Char | BaseType::String | BaseType::Object
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
/// literal type, either side is Object, or one numeric type widens to the
/// other (the comparison then uses the wider type).
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
    lt.base.widens_to(&rt.base) || rt.base.widens_to(&lt.base)
}

fn emit_eq(_ctx: &mut Ctx<'_>, st: &mut FnState, _lt: &Ty, _rt: &Ty, _span: crate::source::Span) {
    // A single equality opcode: the VM compares primitives by value,
    // strings by content, enums and collections structurally, and other
    // objects by identity — null-safe in every case.
    st.emit(IrInstr::Op(IrOp::Eq));
}

fn emit_ord(
    _ctx: &mut Ctx<'_>,
    st: &mut FnState,
    op: BinOp,
    _lt: &Ty,
    _rt: &Ty,
    _span: crate::source::Span,
) {
    let o = match op {
        BinOp::Lt => IrOp::Lt,
        BinOp::Le => IrOp::Le,
        BinOp::Gt => IrOp::Gt,
        BinOp::Ge => IrOp::Ge,
        _ => unreachable!(),
    };
    st.emit(IrInstr::Op(o));
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
    Ty::boolean()
}

fn check_unary(ctx: &mut Ctx<'_>, st: &mut FnState, op: UnaryOp, inner: &Expr) -> Ty {
    let t = check_expr(ctx, st, inner);
    match op {
        UnaryOp::Neg => match t.base.unary_result() {
            Some(r) => {
                // Byte/Short/Integer promote to Integer (Java parity);
                // wider numerics keep their type.
                st.emit(IrInstr::Op(IrOp::Neg));
                Ty::non_null(r)
            }
            None => {
                ctx.err_at("C159", "unary '-' requires a numeric operand", inner.span());
                st.emit(IrInstr::Op(IrOp::Pop));
                Ty::object()
            }
        },
        UnaryOp::Not => {
            if !matches!(t.base, BaseType::Boolean) {
                ctx.err_at("C160", "unary '!' requires a Boolean operand", inner.span());
                st.emit(IrInstr::Op(IrOp::Pop));
                return Ty::object();
            }
            if t.nullable {
                st.emit(IrInstr::Op(IrOp::NullCheck));
            }
            st.emit(IrInstr::Op(IrOp::Not));
            Ty::boolean()
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
    // Result type: left non-null widened with right. The `??` operator is a
    // compile-time coercion keyed off the *declared* type of the left operand,
    // so a null-narrowing that reduced it to `null?` (the `else` branch of an
    // `!= null` test) must not erase the base type it recovers here.
    let left_base = if lt.base == BaseType::Null {
        match l {
            Expr::Ident(name, _) => st
                .lookup_local(name)
                .map(|i| st.locals[i].ty.base.clone())
                .unwrap_or(lt.base.clone()),
            _ => lt.base.clone(),
        }
    } else {
        lt.base.clone()
    };
    let left_nn = Ty::new(left_base, false);
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
    if !lt.base.is_integral() || !rt.base.is_integral() {
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
    // Encapsulation: fields are always private and accessible only inside
    // the exact class that declares them. External read/write is a compile
    // error (handled by the caller's privacy branch).
    if st.class_id != Some(f.declaring) {
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
        if info.static_fields.iter().any(|f| f.name == m.name) {
            // A static field is never reachable through an object receiver;
            // it requires type-qualified access.
            ctx.err_at(
                "C234",
                format!(
                    "field '{}' is static; use {}.{} instead of obj.{}",
                    m.name, info.name, m.name, m.name
                ),
                m.span,
            );
            return Ty::object();
        }
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
        Expr::Ident(name, _) => {
            // Unqualified method call on self.
            if !st.is_static {
                if let Some(self_slot) = st.lookup_local("self") {
                    let self_ty = st.local_type("self").unwrap_or(Ty::object());
                    let has_effective = matches!(&self_ty.base, BaseType::Class(c2, _) if ctx.program.classes[*c2 as usize].find_effective(name).is_some());
                    let is_object = matches!(self_ty.base, BaseType::Object);
                    let is_iface = matches!(&self_ty.base, BaseType::Interface(i2, _) if ctx.program.interfaces[*i2 as usize].slots.iter().any(|sl| sl.name == *name));
                    if has_effective || is_object || is_iface {
                        st.emit(IrInstr::LoadLocal(self_slot as u16));
                        return dispatch_method(ctx, st, self_ty, name, c.span, c);
                    }
                }
            }
            // Inside a static block, a bare name may call a static method
            // of the declaring class, exactly like Self.name(...).
            if st.in_static_block {
                if let Some(cid) = st.class_id {
                    let info = &ctx.program.classes[cid as usize];
                    if info.find_local_static(name).is_some() {
                        let sa = StaticAccessExpr {
                            ty: TypeRef::named("Self", c.span),
                            name: name.clone(),
                            span: c.span,
                        };
                        return call_static(ctx, st, &sa, c);
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
        "Math"
            | "Type"
            | "Base64"
            | "Hash"
            | "Json"
            | "Time"
            | "Random"
            | "File"
            | "Test"
            | "System"
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
                    builtin_ret_type(&sig, &recv.base)
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
            // Static methods belong only to their declaring class; there is
            // no inheritance.
            match ctx.program.classes[*cid as usize].find_local_static(&sa.name) {
                Some((midx, _)) => {
                    let didx = *cid;
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
                    let (arg_tys, n_args, full_subst) = match_args(ctx, st, m, &class_args, c);
                    // Static methods are not inherited, so a factory always
                    // constructs the class it is declared on.
                    let ret = m.return_ty.clone().unwrap_or(Ty::void());
                    // Instantiate the return type with the resolved class
                    // arguments and any inferred method type arguments.
                    let ret = ret.substitute(&full_subst);
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
                    // Collection constructors infer their type arguments
                    // from the declared type of the enclosing declaration
                    // when one is in effect (`let l: List<String> = List.new()`).
                    let base2 = match st.expected_literal.as_ref() {
                        Some(exp) if same_collection_kind(&exp.base, &base.base) => {
                            exp.base.clone()
                        }
                        _ => base.base.clone(),
                    };
                    check_builtin_args(
                        ctx,
                        st,
                        &sig,
                        &tname,
                        &sa.name,
                        c,
                        &Ty {
                            base: base2.clone(),
                            nullable: false,
                        },
                    );
                    st.emit(IrInstr::CallNative(sig.native, c.args.len() as u16));
                    builtin_ret_type(&sig, &base2)
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

/// Universal object contract fallback: every reference value supports
/// `equals(other: Object?): Boolean` and `hashCode(): Long` unless a more
/// specific method already matched. Emits the call and returns the result
/// type when the name and arity match; `None` otherwise.
fn emit_object_contract(
    ctx: &mut Ctx<'_>,
    st: &mut FnState,
    name: &str,
    c: &CallExpr,
) -> Option<Ty> {
    match name {
        "equals" if c.args.len() == 1 && !c.args[0].spread && c.args[0].name.is_none() => {
            // The argument flows into Object?; any non-Void value is accepted.
            let aty = check_expr(ctx, st, &c.args[0].expr);
            if matches!(aty.base, BaseType::Void) {
                ctx.err_at("C171", "equals expects a value, found Void", c.args[0].span);
                return Some(Ty::boolean());
            }
            st.emit(IrInstr::CallNative(builtins::nat::OBJECT_EQUALS, 1));
            Some(Ty::boolean())
        }
        "hashCode" if c.args.is_empty() => {
            st.emit(IrInstr::CallNative(builtins::nat::OBJECT_HASHCODE, 0));
            Some(Ty::long())
        }
        _ => None,
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
            let dinfo = &program.classes[*cid as usize];
            // Effective method resolution: explicit class method (or a
            // synthetic delegation wrapper), then interface default.
            let eff = dinfo.find_effective(name).cloned();
            match eff {
                Some(e) if e.class_method.is_some() => {
                    let midx = e.class_method.unwrap();
                    let mdef = dinfo.methods[midx].clone();
                    if mdef.is_static {
                        ctx.err_at(
                            "C169",
                            format!("'{}' is static; use {}.{}(...)", name, dinfo.name, name),
                            span,
                        );
                        return Ty::object();
                    }
                    // Methods are private unless declared `public`; a private
                    // method is accessible only inside its declaring class.
                    if mdef.visibility != Visibility::Public && st.class_id != Some(*cid) {
                        ctx.err_at(
                            "C118",
                            format!("method '{}' is private to class '{}'", name, dinfo.name),
                            span,
                        );
                        return Ty::object();
                    }
                    let slot = match dinfo.method_slot(name) {
                        Some(s) => s,
                        None => {
                            ctx.err_at(
                                "C170",
                                format!(
                                    "method '{}' not found in dispatch table of {}",
                                    name, dinfo.name
                                ),
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
                        Template::Class(*cid, midx),
                    );
                    let (_arg_tys, n_args, full_subst) = match_args(ctx, st, &mdef, args, c);
                    let ret = mdef.return_ty.clone().unwrap_or(Ty::void());
                    st.emit(IrInstr::CallClass(*cid as u16, slot, n_args as u16));
                    ret.substitute(&full_subst)
                }
                Some(e) if e.default.is_some() => {
                    let (did, didx) = e.default.unwrap();
                    let iface = &program.interfaces[did as usize];
                    let slot = iface.slots.iter().position(|s| s.name == name).unwrap_or(0);
                    // Instantiate the default's raw signature with the
                    // receiver class's binding for the default-providing
                    // interface.
                    let bargs = program.classes[*cid as usize]
                        .interface_bindings
                        .iter()
                        .find(|(id, _)| *id == did)
                        .map(|(_, a)| a.clone())
                        .unwrap_or_default();
                    let subst: Vec<Option<BaseType>> = args.iter().cloned().map(Some).collect();
                    let did_args: Vec<BaseType> =
                        bargs.iter().map(|a| a.substitute(&subst)).collect();
                    let (params, ret) = instantiate_default_sig(program, did, didx, &did_args);
                    let dmethod = &program.interfaces[did as usize].methods[didx];
                    compile_template(
                        ctx.program,
                        ctx.sources,
                        ctx.diags,
                        ctx.ir,
                        ctx.fn_ids,
                        Template::InterfaceDefault(did, didx),
                    );
                    let (_arg_tys, n_args, subst_full) =
                        match_args_with(ctx, st, &did_args, &dmethod.type_params, &params, c);
                    // The signature above was instantiated with `did_args`
                    // (which may still carry type variables inside generic
                    // bodies); prepend the original binding so only the
                    // method's own type variables are replaced by the
                    // inferred values.
                    let method_subst = subst_full[did_args.len()..].to_vec();
                    let mut full_subst: Vec<Option<BaseType>> =
                        did_args.iter().cloned().map(Some).collect();
                    full_subst.extend(method_subst);
                    st.emit(IrInstr::CallInterface(
                        did as u16,
                        slot as u16,
                        n_args as u16,
                    ));
                    ret.map(|r| r.substitute(&full_subst)).unwrap_or(Ty::void())
                }
                _ => {
                    // Universal object-contract and toString fallbacks.
                    if let Some(t) = emit_object_contract(ctx, st, name, c) {
                        return t;
                    }
                    if name == "toString" {
                        st.emit(IrInstr::CallNative(builtins::nat::TO_STRING, 0));
                        return Ty::string();
                    }
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
        BaseType::Interface(iid, iargs) => {
            // Built-in interfaces expose native instance methods.
            if *iid < crate::resolve::builtin::BUILTIN_COUNT as u32 {
                let bname = builtin_name_of(ctx.program, &BaseType::Interface(*iid, vec![]));
                if let Some(sig) = builtins::instance_method(&bname, name) {
                    check_builtin_args(ctx, st, &sig, &bname, name, c, &recv_ty);
                    st.emit(IrInstr::CallNative(sig.native, c.args.len() as u16));
                    return builtin_ret_type(&sig, &recv_ty.base);
                }
            }
            let iface = &ctx.program.interfaces[*iid as usize];
            match iface.slots.iter().position(|s| s.name == name) {
                Some(slot) => {
                    // Find the concrete/default implementation signature.
                    let impl_fn = interface_default_for(ctx.program, *iid, name);
                    match impl_fn {
                        Some(template) => {
                            let (did, didx) = match template {
                                Template::InterfaceDefault(i, j) => (i, j),
                                _ => unreachable!("interface_default_for yields defaults only"),
                            };
                            // Binding from the receiver interface to the
                            // default provider, instantiated with the
                            // receiver's type arguments.
                            let bind = ctx
                                .program
                                .interface_parent_args(*iid, did)
                                .unwrap_or_default();
                            let subst: Vec<Option<BaseType>> =
                                iargs.iter().cloned().map(Some).collect();
                            let did_args: Vec<BaseType> =
                                bind.iter().map(|a| a.substitute(&subst)).collect();
                            let (params, ret) =
                                instantiate_default_sig(ctx.program, did, didx, &did_args);
                            let dmethod = &ctx.program.interfaces[did as usize].methods[didx];
                            let _fid = compile_template(
                                ctx.program,
                                ctx.sources,
                                ctx.diags,
                                ctx.ir,
                                ctx.fn_ids,
                                template,
                            );
                            let (_arg_tys, n_args, subst_full) = match_args_with(
                                ctx,
                                st,
                                &did_args,
                                &dmethod.type_params,
                                &params,
                                c,
                            );
                            // See the class-receiver default branch: keep the
                            // receiver's binding intact, replace only the
                            // method's own type variables.
                            let method_subst = subst_full[did_args.len()..].to_vec();
                            let mut full_subst: Vec<Option<BaseType>> =
                                did_args.iter().cloned().map(Some).collect();
                            full_subst.extend(method_subst);
                            st.emit(IrInstr::CallInterface(
                                *iid as u16,
                                slot as u16,
                                n_args as u16,
                            ));
                            ret.map(|r| r.substitute(&full_subst)).unwrap_or(Ty::void())
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
                    if let Some(t) = emit_object_contract(ctx, st, name, c) {
                        return t;
                    }
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
            if let Some(t) = emit_object_contract(ctx, st, name, c) {
                return t;
            }
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
            if let Some(t) = emit_object_contract(ctx, st, name, c) {
                return t;
            }
            if name == "toString" {
                st.emit(IrInstr::CallNative(builtins::nat::TO_STRING, 0));
                return Ty::string();
            }
            if c.args.iter().any(|a| a.spread) {
                ctx.err_at(
                    "C189",
                    "spread is not supported in dynamic method calls",
                    span,
                );
                for a in &c.args {
                    check_expr(ctx, st, &a.expr);
                    st.emit(IrInstr::Op(IrOp::Pop));
                }
                return Ty::object();
            }
            // Evaluate the arguments left-to-right; CallDynamic consumes
            // receiver plus one stack slot per argument.
            for a in &c.args {
                check_expr(ctx, st, &a.expr);
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
                    builtin_ret_type(&sig, &recv_ty.base)
                }
                None => {
                    if let Some(t) = emit_object_contract(ctx, st, name, c) {
                        return t;
                    }
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
/// values in parameter declaration order and returns the completed
/// substitution table (class/interface type args first, then the
/// method's own inferred type args) so callers can instantiate the
/// return type.
fn match_args(
    ctx: &mut Ctx<'_>,
    st: &mut FnState,
    m: &MethodInfo,
    class_args: &[BaseType],
    c: &CallExpr,
) -> (Vec<Ty>, usize, Vec<Option<BaseType>>) {
    // Substitution table: class type vars first (indices 0..class_n),
    // then method type vars.
    let mut subst: Vec<Option<BaseType>> = class_args.iter().map(|a| Some(a.clone())).collect();
    subst.extend(m.type_params.iter().map(|_| None));
    lower_call_args(ctx, st, m, &mut subst, c)
}

fn match_args_with(
    ctx: &mut Ctx<'_>,
    st: &mut FnState,
    fixed_args: &[BaseType],
    mtype_params: &[TypeParam],
    params: &[ParamInfo],
    c: &CallExpr,
) -> (Vec<Ty>, usize, Vec<Option<BaseType>>) {
    let fake = MethodInfo {
        name: String::new(),
        visibility: Visibility::Public,
        is_static: false,
        type_params: mtype_params.to_vec(),
        params: params.to_vec(),
        return_ty: None,
        body: None,
        delegate: None,
        span: crate::source::Span::new(0, 0, 0),
    };
    // Fixed class/interface args first (they may still carry type vars of
    // a generic receiver), then the method's own type vars.
    let mut subst: Vec<Option<BaseType>> = fixed_args.iter().cloned().map(Some).collect();
    subst.extend(mtype_params.iter().map(|_| None));
    lower_call_args(ctx, st, &fake, &mut subst, c)
}

fn lower_call_args(
    ctx: &mut Ctx<'_>,
    st: &mut FnState,
    m: &MethodInfo,
    subst: &mut [Option<BaseType>],
    c: &CallExpr,
) -> (Vec<Ty>, usize, Vec<Option<BaseType>>) {
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
    let param_names: Vec<String> = params.iter().map(|p| p.name.clone()).collect();
    // Class/interface bindings (if any) precede the method's own type vars
    // and are fixed: inference must not rebind them from argument types.
    let fixed_len = subst.len() - m.type_params.len();
    // Infer generics from provided arguments.
    for (i, p) in params.iter().enumerate() {
        if p.variadic {
            let elem_ty = p.ty.substitute(subst);
            let tail_start = required_end.min(pos_count);
            for arg in c.args[tail_start..pos_count].iter() {
                if arg.name.is_none() && !arg.spread {
                    let t = expr_probe_type(ctx, st, &arg.expr);
                    let saved: Vec<Option<BaseType>> = subst[..fixed_len].to_vec();
                    unify(ctx, st, &elem_ty.base, &t.base, subst);
                    for (slot, val) in subst[..fixed_len].iter_mut().zip(&saved) {
                        *slot = val.clone();
                    }
                }
            }
            // A named argument targeting the variadic parameter supplies the
            // whole list; probe its element type for inference.
            if let Some(arg) = c
                .args
                .iter()
                .find(|a| a.name.as_deref() == Some(p.name.as_str()))
            {
                if let BaseType::List(e) = expr_probe_type(ctx, st, &arg.expr).base {
                    let saved: Vec<Option<BaseType>> = subst[..fixed_len].to_vec();
                    unify(ctx, st, &elem_ty.base, &e, subst);
                    for (slot, val) in subst[..fixed_len].iter_mut().zip(&saved) {
                        *slot = val.clone();
                    }
                }
            }
            continue;
        }
        let src = arg_source(ctx, st, i, pos_count, &param_names, c);
        if let Some((expr, spread)) = src {
            if !spread {
                let t = expr_probe_type(ctx, st, expr);
                let saved: Vec<Option<BaseType>> = subst[..fixed_len].to_vec();
                unify(ctx, st, &p.ty.base, &t.base, subst);
                for (slot, val) in subst[..fixed_len].iter_mut().zip(&saved) {
                    *slot = val.clone();
                }
            }
        }
    }
    // Unresolved vars erase to Object.
    for s in subst.iter_mut() {
        if s.is_none() {
            *s = Some(BaseType::Object);
        }
    }
    // Constraint checking. `subst` may carry class/interface type args
    // before the method's own type vars (see `match_args`), so index from
    // the end of the table.
    let method_offset = subst.len() - m.type_params.len();
    for (i, tp) in m.type_params.iter().enumerate() {
        let inferred = subst[method_offset + i].clone().unwrap_or(BaseType::Object);
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
            // A named argument may target the variadic parameter directly;
            // its value must be a List that is spliced into the parameter
            // list. Mixing it with a positional tail is ambiguous.
            let named_arg = c
                .args
                .iter()
                .find(|a| a.name.as_deref() == Some(p.name.as_str()));
            if named_arg.is_some() && pos_count > required_end {
                ctx.err_at(
                    "C148",
                    format!(
                        "variadic parameter '{}' was provided both positionally and by name",
                        p.name
                    ),
                    c.span,
                );
            }
            match named_arg {
                Some(arg) if pos_count <= required_end => {
                    let t = check_expr(ctx, st, &arg.expr);
                    match &t.base {
                        BaseType::List(e) => {
                            let et = Ty::non_null(e.as_ref().substitute(subst));
                            if !crate::types::is_subtype(&et, &elem_ty, ctx.program) {
                                ctx.err_at(
                                    "C181",
                                    format!(
                                        "element type {} of named variadic argument does not match parameter '{}'",
                                        type_display(ctx.program, &et),
                                        p.name
                                    ),
                                    arg.expr.span(),
                                );
                            }
                            st.emit(IrInstr::Op(IrOp::ListExtend));
                        }
                        _ => {
                            ctx.err_at(
                                "C183",
                                format!(
                                    "argument type {} does not match parameter '{}' of type List<{}>",
                                    type_display(ctx.program, &t),
                                    p.name,
                                    type_display(ctx.program, &elem_ty)
                                ),
                                arg.expr.span(),
                            );
                            // Balance the stack unless the value is void
                            // (void expressions push nothing).
                            if !matches!(t.base, BaseType::Void) {
                                st.emit(IrInstr::Op(IrOp::Pop));
                            }
                        }
                    }
                }
                _ => {
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
                                    ctx.err_at(
                                        "C182",
                                        "spread requires a List value",
                                        arg.expr.span(),
                                    );
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
                emit_coerce(st, &t, &param_ty);
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
    (out, emitted, subst.to_vec())
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
        Expr::Int(lit, _) => match lit {
            crate::ast::IntLiteral::I64(v) if (*v as i32) as i64 == *v => Ty::integer(),
            crate::ast::IntLiteral::I64(_) => Ty::long(),
            crate::ast::IntLiteral::Big(_) => Ty::big_integer(),
        },
        Expr::Real(lit, _) => match lit {
            crate::ast::RealLiteral::Float(_) => Ty::float_(),
            crate::ast::RealLiteral::Double(_) => Ty::double(),
            crate::ast::RealLiteral::Decimal(_) => Ty::big_decimal(),
        },
        Expr::Bool(_, _) => Ty::boolean(),
        Expr::Char(_, _) => Ty::char_(),
        Expr::String(_, _) => Ty::string(),
        Expr::Null(_) => Ty::null(),
        Expr::Ident(name, _) => {
            if let Some(t) = st.local_type(name) {
                return t;
            }
            Ty::object()
        }
        Expr::Member(m) if matches!(m.obj.as_ref(), Expr::Ident(name, _) if name == "self") => {
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
        (BaseType::Interface(c, cargs), BaseType::Interface(req, rargs)) => {
            match ctx.program.interface_parent_args(*c, *req) {
                Some(bargs) => {
                    let subst: Vec<Option<BaseType>> = cargs.iter().cloned().map(Some).collect();
                    let sub: Vec<BaseType> = bargs.iter().map(|a| a.substitute(&subst)).collect();
                    sub == *rargs
                }
                None => false,
            }
        }
        (BaseType::Class(c, cargs), BaseType::Interface(req, rargs)) => {
            match ctx.program.class_interface_args(*c, *req) {
                Some(bargs) => {
                    let subst: Vec<Option<BaseType>> = cargs.iter().cloned().map(Some).collect();
                    let sub: Vec<BaseType> = bargs.iter().map(|a| a.substitute(&subst)).collect();
                    sub == *rargs
                }
                None => false,
            }
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
    // Generic positions are bound to the receiver's type arguments before
    // argument checking (List<String>.add(42) is rejected here).
    let params = instantiate_builtin_sig(sig, &recv.base).0;
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
    let last_optional = params
        .last()
        .is_some_and(|p| p.nullable && matches!(p.base, BaseType::String));
    let min_args = if last_optional {
        params.len().saturating_sub(1)
    } else {
        params.len()
    };
    if c.args.len() < min_args || c.args.len() > params.len() {
        ctx.err_at(
            "C187",
            format!(
                "{}.{} expects {} argument(s), found {}",
                tname,
                method,
                params.len(),
                c.args.len()
            ),
            c.span,
        );
    }
    for (a, pt) in c.args.iter().zip(params.iter()) {
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
}

/// Instantiate a built-in signature's generic positions against the
/// receiver's type arguments: `List<T>` binds variable 0, `Map<K, V>` binds
/// 0 and 1, `Stack<T>`/`Set<T>` bind 0. Returns (params, ret).
fn instantiate_builtin_sig(sig: &builtins::BuiltinSig, recv: &BaseType) -> (Vec<Ty>, Ty) {
    let subst: Vec<Option<BaseType>> = match recv {
        BaseType::List(e) => vec![Some(e.as_ref().clone())],
        BaseType::Map(k, v) => vec![Some(k.as_ref().clone()), Some(v.as_ref().clone())],
        BaseType::Stack(e) | BaseType::Set(e) => vec![Some(e.as_ref().clone())],
        _ => vec![],
    };
    let params = sig.params.iter().map(|p| p.substitute(&subst)).collect();
    let ret = sig.ret.substitute(&subst);
    (params, ret)
}

/// The instantiated return type of a built-in call on `recv`.
fn builtin_ret_type(sig: &builtins::BuiltinSig, recv: &BaseType) -> Ty {
    instantiate_builtin_sig(sig, recv).1
}

/// True when both bases are the same collection kind (for constructor
/// inference from a declared type).
fn same_collection_kind(a: &BaseType, b: &BaseType) -> bool {
    matches!(
        (a, b),
        (BaseType::List(_), BaseType::List(_))
            | (BaseType::Map(_, _), BaseType::Map(_, _))
            | (BaseType::Stack(_), BaseType::Stack(_))
            | (BaseType::Set(_), BaseType::Set(_))
    )
}

fn param_names_of(m: &MethodInfo) -> Vec<String> {
    m.type_params.iter().map(|p| p.name.clone()).collect()
}

fn check_match(ctx: &mut Ctx<'_>, st: &mut FnState, m: &MatchExpr) -> Ty {
    let subj_ty = check_expr(ctx, st, &m.subject);
    let subj_slot = st.decl_temp("$match", subj_ty.clone());
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
            None => result_ty = Some(body_ty.clone()),
            Some(rt) => {
                if rt.base != body_ty.base {
                    // Java-style arm unification: a narrower numeric arm
                    // widens to an already-established wider arm type.
                    // (The reverse direction would require rewriting
                    // already-emitted arms and stays an error.)
                    if body_ty.nullable == rt.nullable && body_ty.base.widens_to(&rt.base) {
                        if let Some(tag) = crate::ir::conv_target::of(&rt.base) {
                            st.emit(IrInstr::Convert(tag));
                        }
                    } else {
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
                    }
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
        Pattern::LiteralInt(v) => Some(match v {
            crate::ast::IntLiteral::I64(x) if (*x as i32) as i64 == *x => Ty::integer(),
            crate::ast::IntLiteral::I64(_) => Ty::long(),
            crate::ast::IntLiteral::Big(_) => Ty::big_integer(),
        }),
        Pattern::LiteralFloat(_) => Some(Ty::float_()),
        Pattern::LiteralDouble(_) => Some(Ty::double()),
        Pattern::LiteralBool(_) => Some(Ty::boolean()),
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
    // The literal may be narrower than the subject (e.g. an Integer
    // literal tested against a Long subject); it is widened at emission.
    subject.base == literal.base || literal.base.widens_to(&subject.base)
}

/// Widen an integer value to a wider numeric constant (always exact for
/// integer targets; Float/Double targets use the usual binary conversion).
fn widen_int_const(v: i64, target: &crate::types::BaseType) -> Option<crate::ir::IrConst> {
    use crate::types::BaseType::*;
    Some(match target {
        Short => crate::ir::IrConst::Short(v as i16),
        Integer => crate::ir::IrConst::Integer(v as i32),
        Long => crate::ir::IrConst::Long(v),
        Float => crate::ir::IrConst::Float(v as f32),
        Double => crate::ir::IrConst::Double(v as f64),
        _ => return None,
    })
}

fn emit_literal_test(
    ctx: &mut Ctx<'_>,
    st: &mut FnState,
    subj_slot: usize,
    subj_ty: &Ty,
    lit: crate::ir::IrConst,
) {
    st.emit(IrInstr::LoadLocal(subj_slot as u16));
    // Widen a narrower numeric literal to the subject type so the runtime
    // comparison sees matching representations.
    let lit = match &lit {
        c @ (crate::ir::IrConst::Byte(_)
        | crate::ir::IrConst::Short(_)
        | crate::ir::IrConst::Integer(_)
        | crate::ir::IrConst::Long(_)
        | crate::ir::IrConst::Float(_)) => {
            let lit_base = match c {
                crate::ir::IrConst::Byte(_) => crate::types::BaseType::Byte,
                crate::ir::IrConst::Short(_) => crate::types::BaseType::Short,
                crate::ir::IrConst::Integer(_) => crate::types::BaseType::Integer,
                crate::ir::IrConst::Long(_) => crate::types::BaseType::Long,
                _ => crate::types::BaseType::Float,
            };
            if lit_base != subj_ty.base && lit_base.widens_to(&subj_ty.base) {
                match (c, &subj_ty.base) {
                    (
                        crate::ir::IrConst::Byte(i),
                        t @ (crate::types::BaseType::Short
                        | crate::types::BaseType::Integer
                        | crate::types::BaseType::Long
                        | crate::types::BaseType::Float
                        | crate::types::BaseType::Double),
                    ) => widen_int_const(*i as i64, t),
                    (
                        crate::ir::IrConst::Short(i),
                        t @ (crate::types::BaseType::Integer
                        | crate::types::BaseType::Long
                        | crate::types::BaseType::Float
                        | crate::types::BaseType::Double),
                    ) => widen_int_const(*i as i64, t),
                    (
                        crate::ir::IrConst::Integer(i),
                        t @ (crate::types::BaseType::Long
                        | crate::types::BaseType::Float
                        | crate::types::BaseType::Double),
                    ) => widen_int_const(*i as i64, t),
                    (
                        crate::ir::IrConst::Long(i),
                        t @ (crate::types::BaseType::Float | crate::types::BaseType::Double),
                    ) => widen_int_const(*i, t),
                    (crate::ir::IrConst::Float(f), crate::types::BaseType::Double) => {
                        Some(crate::ir::IrConst::Double(*f as f64))
                    }
                    _ => None,
                }
                .unwrap_or_else(|| c.clone())
            } else {
                c.clone()
            }
        }
        other => other.clone(),
    };
    let c = ctx.ir.intern_const(lit.clone());
    st.emit(IrInstr::LoadConst(c));
    // The literal carries its own type so numeric subjects use the correct
    // comparison semantics.
    let lit_ty = match &lit {
        crate::ir::IrConst::Byte(_) => Ty::byte(),
        crate::ir::IrConst::Short(_) => Ty::short_(),
        crate::ir::IrConst::Integer(_) => Ty::integer(),
        crate::ir::IrConst::Long(_) => Ty::long(),
        crate::ir::IrConst::Float(_) => Ty::float_(),
        crate::ir::IrConst::Double(_) => Ty::double(),
        crate::ir::IrConst::BigInt(_) => Ty::big_integer(),
        crate::ir::IrConst::BigDecimal(_) => Ty::big_decimal(),
        crate::ir::IrConst::Bool(_) => Ty::boolean(),
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
            let c = match v {
                crate::ast::IntLiteral::I64(x) if (*x as i32) as i64 == *x => {
                    crate::ir::IrConst::Integer(*x as i32)
                }
                crate::ast::IntLiteral::I64(x) => crate::ir::IrConst::Long(*x),
                crate::ast::IntLiteral::Big(s) => crate::ir::IrConst::BigInt(s.clone()),
            };
            emit_literal_test(ctx, st, subj_slot, subj_ty, c);
        }
        Pattern::LiteralFloat(v) => {
            emit_literal_test(ctx, st, subj_slot, subj_ty, crate::ir::IrConst::Float(*v));
        }
        Pattern::LiteralDouble(v) => {
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
                    st.emit(IrInstr::Op(IrOp::Eq));
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
                        let payload_slot = st.decl_temp("$mtest", payload_ty.clone());
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
            st.emit(IrInstr::Op(IrOp::Eq));
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
                let elem_slot = st.decl_temp("$elem", elem_ty.clone());
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
            check_redeclaration(ctx, st, name, crate::source::Span::new(0, 0, 0));
            let slot = st.decl_local(
                name,
                subj_ty.clone(),
                false,
                crate::source::Span::new(0, 0, 0),
                false,
            );
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
                let payload_slot = st.decl_temp("$payload", payload_ty.clone());
                st.emit(IrInstr::LoadLocal(subj_slot as u16));
                st.emit(IrInstr::Op(IrOp::EnumPayload));
                st.emit(IrInstr::StoreLocal(payload_slot as u16));
                for sp in sub.iter() {
                    bind_pattern(ctx, st, sp, payload_slot, &payload_ty);
                }
            }
        }
        Pattern::List(pats) => {
            // Bindings carry the subject's element type, consistent with
            // check_pattern_test (which tests nested patterns against it).
            let elem_ty = match &subj_ty.base {
                BaseType::List(e) => Ty::non_null(e.as_ref().clone()),
                _ => Ty::object(),
            };
            for (i, sp) in pats.iter().enumerate() {
                let elem_slot = st.decl_temp("$elem", elem_ty.clone());
                st.emit(IrInstr::LoadLocal(subj_slot as u16));
                let ci = ctx.ir.intern_const(crate::ir::IrConst::Long(i as i64));
                st.emit(IrInstr::LoadConst(ci));
                st.emit(IrInstr::Op(IrOp::ListGet));
                st.emit(IrInstr::StoreLocal(elem_slot as u16));
                bind_pattern(ctx, st, sp, elem_slot, &elem_ty);
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
    // Validate field list: exactly the class's own *instance* fields,
    // each once. Static fields are not instance slots and never appear in
    // `Self { ... }`.
    let mut seen = std::collections::HashSet::new();
    for (name, _) in &si.fields {
        if !info.fields.iter().any(|f| f.name == *name) {
            if info.static_fields.iter().any(|f| f.name == *name) {
                ctx.err_at(
                    "C235",
                    format!(
                        "'{}' is a static field of '{}'; static fields are not initialized in Self {{ ... }}",
                        name, info.name
                    ),
                    si.span,
                );
            } else {
                ctx.err_at(
                    "C193",
                    format!("'{}' is not a field of '{}'", name, info.name),
                    si.span,
                );
            }
        }
        if !seen.insert(name.clone()) {
            ctx.err_at(
                "C194",
                format!("field '{}' initialized more than once", name),
                si.span,
            );
        }
    }
    for name in &info
        .fields
        .iter()
        .map(|f| f.name.clone())
        .collect::<Vec<_>>()
    {
        if !seen.contains(name) {
            ctx.err_at(
                "C195",
                format!("field '{}' is not initialized in construction", name),
                si.span,
            );
        }
    }
    // Emit: allocate exactly this class's fields, then store own fields.
    st.emit(IrInstr::NewObject(cid as u16, info.fields.len() as u16));
    for (name, expr) in &si.fields {
        let slot = info
            .fields
            .iter()
            .position(|f| f.name == *name)
            .unwrap_or(0) as u16;
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
                .map(|a| {
                    if a.nullable {
                        diags.err_at(
                            "C103",
                            "nullable types cannot be used as type arguments".to_string(),
                            a.span,
                        );
                    }
                    resolve_type_base(diags, program, a, type_params, class_id, a.span)
                })
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
        "Boolean" => return BaseType::Boolean,
        "Byte" => return BaseType::Byte,
        "Short" => return BaseType::Short,
        "Integer" => return BaseType::Integer,
        "Long" => return BaseType::Long,
        "Float" => return BaseType::Float,
        "Double" => return BaseType::Double,
        "BigInteger" => return BaseType::BigInteger,
        "BigDecimal" => return BaseType::BigDecimal,
        "Char" => return BaseType::Char,
        "String" => return BaseType::String,
        "Object" => return BaseType::Object,
        // `Throwable` names the universal throwable object; `Exception` is
        // the built-in structured exception type.
        "Throwable" => return BaseType::Object,
        "Exception" => return BaseType::native(crate::types::native_kind::EXCEPTION),
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
        "System" => return BaseType::Object,
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

/// Source-level name for built-in receiver types (for builtin tables).
pub fn builtin_name_of(program: &ResolvedProgram, base: &BaseType) -> String {
    match base {
        BaseType::Boolean => "Boolean".into(),
        BaseType::Byte => "Byte".into(),
        BaseType::Short => "Short".into(),
        BaseType::Integer => "Integer".into(),
        BaseType::Long => "Long".into(),
        BaseType::Float => "Float".into(),
        BaseType::Double => "Double".into(),
        BaseType::BigInteger => "BigInteger".into(),
        BaseType::BigDecimal => "BigDecimal".into(),
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
            crate::types::native_kind::EXCEPTION => "Exception".into(),
            _ => "Stream".into(),
        },
        _ => "Object".into(),
    }
}

/// Default implementation of an interface method for dispatch metadata:
/// the most specific default in the interface hierarchy.
pub fn interface_default_for(
    program: &ResolvedProgram,
    iface_id: u32,
    name: &str,
) -> Option<Template> {
    let iface = &program.interfaces[iface_id as usize];
    let slot = iface.slots.iter().find(|s| s.name == name)?;
    // Most specific provider: this interface first, then its parents
    // nearest-first (`all_parents` is BFS-ordered by specificity).
    let mut order: Vec<u32> = vec![iface_id];
    order.extend(&iface.all_parents);
    for iid in order {
        let i = &program.interfaces[iid as usize];
        if let Some(idx) = i
            .methods
            .iter()
            .position(|m| m.name == name && m.body.is_some())
        {
            return Some(Template::InterfaceDefault(iid, idx));
        }
    }
    slot.default
        .or(slot.decl)
        .map(|(iid, idx)| Template::InterfaceDefault(iid, idx))
}

/// Concrete signature of a default implementation for a receiver that binds
/// interface `did` with `did_args`. The method's own type parameters remain
/// type variables for call-site inference.
fn instantiate_default_sig(
    program: &ResolvedProgram,
    did: u32,
    didx: usize,
    did_args: &[BaseType],
) -> (Vec<ParamInfo>, Option<Ty>) {
    let m = &program.interfaces[did as usize].methods[didx];
    let mut subst: Vec<Option<BaseType>> = did_args.iter().cloned().map(Some).collect();
    // Identity-map the method's own type vars: `substitute` erases `None`
    // entries to Object, which would destroy the method's generics before
    // call-site inference runs.
    let base = subst.len();
    subst.extend((0..m.type_params.len()).map(|i| Some(BaseType::TypeVar((base + i) as u32))));
    let params = m
        .params
        .iter()
        .map(|p| ParamInfo {
            ty: p.ty.substitute(&subst),
            ..p.clone()
        })
        .collect();
    let ret = m.return_ty.as_ref().map(|t| t.substitute(&subst));
    (params, ret)
}

impl<'a> Checker<'a> {
    /// Build IR class/interface metadata: class dispatch tables, dynamic
    /// method tables, statics, and interface dispatch tables.
    pub fn build_class_metadata(&mut self) {
        let program = self.program;
        for c in &program.classes {
            // Class-local dispatch table (explicit methods plus synthetic
            // delegation wrappers). Concrete calls never need subclass
            // dispatch because class inheritance does not exist.
            let mut method_table = vec![];
            for name in &c.method_names {
                let fid = match c.find_local_method(name) {
                    Some((midx, _)) => self.compile_template(Template::Class(c.id, midx)),
                    None => 0,
                };
                method_table.push(fid);
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
            // Interface dispatch tables use the effective implementation:
            // explicit class method, delegation wrapper, or default.
            let mut interfaces = vec![];
            for iid in &c.all_interfaces {
                let iface = &program.interfaces[*iid as usize];
                let mut dispatch = vec![];
                for slot in &iface.slots {
                    let fid = match c.find_effective(&slot.name) {
                        Some(e) if e.class_method.is_some() => {
                            self.compile_template(Template::Class(c.id, e.class_method.unwrap()))
                        }
                        Some(e) if e.default.is_some() => {
                            let (did, didx) = e.default.unwrap();
                            self.compile_template(Template::InterfaceDefault(did, didx))
                        }
                        _ => 0,
                    };
                    dispatch.push(fid);
                }
                interfaces.push((*iid, dispatch));
            }
            // Dynamic `Object` dispatch exposes only the public effective
            // surface: explicit public methods, delegated wrappers, and
            // interface defaults. Private methods are not exposed.
            let dyn_methods: Vec<(String, u32)> = c
                .effective_methods
                .iter()
                .filter(|e| e.is_public)
                .filter_map(|e| {
                    let fid = match (e.class_method, e.default) {
                        (Some(mi), _) => self.compile_template(Template::Class(c.id, mi)),
                        (None, Some((did, didx))) => {
                            self.compile_template(Template::InterfaceDefault(did, didx))
                        }
                        _ => return None,
                    };
                    Some((e.name.clone(), fid))
                })
                .collect();
            let static_init = if c.static_fields.is_empty() && c.def.static_block.is_none() {
                None
            } else {
                Some(self.compile_template(Template::StaticInit(c.id)))
            };
            self.ir.classes.push(IrClass {
                id: c.id,
                name: c.name.clone(),
                field_count: c.fields.len() as u16,
                method_names: c.method_names.clone(),
                method_table,
                dyn_methods,
                statics,
                static_fields: c
                    .static_fields
                    .iter()
                    .enumerate()
                    .map(|(i, f)| (f.name.clone(), i as u16))
                    .collect(),
                static_init,
                interfaces,
            });
        }
        for i in &program.interfaces {
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
        Template::StaticInit(cid) => {
            let c = &program.classes[cid as usize];
            (
                format!("{}.static_init", c.name),
                MethodInfo {
                    name: "$static_init".to_string(),
                    visibility: Visibility::Private,
                    is_static: true,
                    type_params: vec![],
                    params: vec![],
                    return_ty: None,
                    body: None,
                    delegate: None,
                    span: c.def.span,
                },
                c.def.span.file,
                false,
                Some(cid),
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
        Template::StaticInit(cid) => program.classes[cid as usize]
            .type_params
            .iter()
            .map(|p| p.name.clone())
            .collect(),
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
        // names carries a single base map at depth 0 for `self`/parameters
        // (declared before any begin_scope); narrowing intentionally starts
        // empty because it is only recorded inside an if/else/match scope.
        names: vec![HashMap::new()],
        narrowing: vec![],
        scope_depth: 0,
        loops: vec![],
        ret_ty: None,
        cur_line: 1,
        class_id,
        is_static: info.is_static,
        in_static_init: matches!(template, Template::StaticInit(_)),
        in_static_block: false,
        in_for_in_iter: false,
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
                // The receiver carries the interface's own type variables so
                // default bodies type-check against their declared signature.
                let n = program.interfaces[iid as usize].type_params.len();
                let args: Vec<BaseType> = (0..n).map(|i| BaseType::TypeVar(i as u32)).collect();
                Ty::non_null(BaseType::Interface(iid, args))
            }
            _ => Ty::object(),
        };
        st.func.params.push("self".to_string());
        st.func.param_tys.push(self_ty.clone());
        st.decl_local(
            "self",
            self_ty,
            false,
            crate::source::Span::new(0, 0, 0),
            true,
        );
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
        st.decl_local(&p.name, ty, false, crate::source::Span::new(0, 0, 0), true);
    }

    let ret_ty = info.return_ty.clone();
    st.ret_ty = ret_ty.clone();

    // Synthetic static initializer: evaluate each static field's
    // initializer in declaration order and store it into the class's
    // static slot vector, then run the class's single static block. No
    // parameters, no self, void return. The VM executes this function
    // lazily, once, at the class's first active use.
    if let Template::StaticInit(cid) = template {
        let c = &program.classes[cid as usize];
        let def_fields = c.def.fields.clone();
        if let Some(block) = &c.def.static_block {
            da_check_body(diags, block, &[], false);
        }
        let mut ctx = Ctx {
            program,
            sources,
            diags,
            ir,
            fn_ids,
        };
        for (slot, f) in c.static_fields.iter().enumerate() {
            if let Some(init) = def_fields
                .iter()
                .find(|d| d.name == f.name)
                .and_then(|d| d.init.as_ref())
            {
                // Collection literals infer their element types from the
                // field's declared type, like local declarations.
                if matches!(init, Expr::List(_, _) | Expr::Map(_, _)) {
                    st.expected_literal = Some(f.ty.clone());
                }
                let t = check_expr(&mut ctx, &mut st, init);
                st.expected_literal = None;
                if !crate::types::is_subtype(&t, &f.ty, program)
                    && !coerce_value(&mut st, &t, &f.ty, init)
                {
                    ctx.err_at(
                        "C132",
                        format!(
                            "cannot assign {} to '{}': expected {}",
                            type_display(program, &t),
                            f.name,
                            type_display(program, &f.ty)
                        ),
                        init.span(),
                    );
                } else {
                    emit_coerce(&mut st, &t, &f.ty);
                }
                st.emit(IrInstr::StoreStatic(cid as u16, slot as u16));
            }
        }
        // The class's single static block runs after every field
        // initializer, so it may read and write (mutable) static fields.
        if let Some(block) = &c.def.static_block {
            st.in_static_block = true;
            check_block(&mut ctx, &mut st, block, CtxOwner::Function);
            st.in_static_block = false;
        }
        // Epilogue only when control can fall off the end; a block ending
        // in throw or bare return already terminates the function.
        if body_flows_off_end(&st.func.instrs) {
            st.emit(IrInstr::ReturnVoid);
        }
        let mut finished = st.func;
        finished.local_count = st.locals.len() as u16;
        finished.returns_value = false;
        ir.functions[fid as usize] = finished;
        return fid;
    }

    // Delegation wrappers are lowered directly: the field's static value is
    // used as the receiver and each declared argument is forwarded once,
    // left to right, through ordinary interface dispatch.
    if let Some(dt) = info.delegate.clone() {
        st.emit(IrInstr::LoadLocal(0));
        st.emit(IrInstr::LoadField(dt.field_slot));
        for i in 0..info.params.len() {
            st.emit(IrInstr::LoadLocal((1 + i) as u16));
        }
        st.emit(IrInstr::CallInterface(
            dt.interface as u16,
            dt.iface_slot,
            info.params.len() as u16,
        ));
        let value_ret = matches!(&ret_ty, Some(rt) if !matches!(rt.base, BaseType::Void));
        if value_ret {
            st.emit(IrInstr::Return);
        } else {
            st.emit(IrInstr::ReturnVoid);
        }
        let mut finished = st.func;
        finished.local_count = st.locals.len() as u16;
        finished.returns_value = value_ret;
        ir.functions[fid as usize] = finished;
        return fid;
    }

    if let Some(body) = &info.body {
        let param_names: Vec<String> = info.params.iter().map(|p| p.name.clone()).collect();
        da_check_body(diags, body, &param_names, is_instance);
    }
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

#[cfg(test)]
mod tests {
    use super::*;

    /// Run lex -> parse -> resolve -> check and return the checker's
    /// diagnostics (plus any earlier-stage diagnostics).
    fn check_src(text: &str) -> Diagnostics {
        let mut sources = SourceManager::default();
        sources.add("t.sol", text.to_string());
        let mut diags = Diagnostics::default();
        let tokens = crate::lexer::Lexer::new(0, text).tokenize(&mut diags);
        let mut p = crate::parser::Parser::new(tokens);
        let program = p.parse_program().expect("program should parse");
        diags.items.append(&mut p.diags.items);
        if diags.has_errors() {
            return diags;
        }
        let rp = crate::resolve::resolve_program(&program, &mut diags);
        if diags.has_errors() {
            return diags;
        }
        let mut checker = Checker::new(&rp, &sources);
        checker.check_program();
        checker.diags
    }

    fn has_code(d: &Diagnostics, code: &str) -> bool {
        d.items.iter().any(|x| x.code == code)
    }

    const COUNTER: &str = "package m\n\
        class Counter {\n\
            static mutable total: Long = 0\n\
            static limit: Long = 10\n\
            public static new(): Self { return Self {} }\n\
            public static tick(): Long {\n\
                Self.total += 1\n\
                if Self.total > Counter.limit {\n\
                    Counter.total = Counter.limit\n\
                }\n\
                return Self.total\n\
            }\n\
            public current(): Long { return Counter.total }\n\
        }\n\
        class Main { public static run(args: String...): Long { return 0 } }\n";

    #[test]
    fn static_field_access_compiles_in_declaring_class() {
        // Self.-qualified and class-name-qualified reads, plain assignment,
        // and compound assignment all lower to LoadStatic/StoreStatic.
        let mut sources = SourceManager::default();
        sources.add("t.sol", COUNTER.to_string());
        let mut diags = Diagnostics::default();
        let tokens = crate::lexer::Lexer::new(0, COUNTER).tokenize(&mut diags);
        let mut p = crate::parser::Parser::new(tokens);
        let program = p.parse_program().unwrap();
        diags.items.append(&mut p.diags.items);
        let rp = crate::resolve::resolve_program(&program, &mut diags);
        assert!(!diags.has_errors(), "{:?}", diags.items);
        let mut checker = Checker::new(&rp, &sources);
        checker.check_program();
        assert!(!checker.diags.has_errors(), "{:?}", checker.diags.items);
        let counter = checker
            .ir
            .classes
            .iter()
            .find(|c| c.name == "Counter")
            .unwrap();
        assert_eq!(
            counter.static_fields,
            vec![("total".into(), 0u16), ("limit".into(), 1u16)]
        );
        assert!(counter.static_init.is_some(), "init function recorded");
        let init = counter.static_init.unwrap();
        let f = &checker.ir.functions[init as usize];
        assert!(f.params.is_empty() && !f.returns_value);
        let ops: Vec<_> = f
            .instrs
            .iter()
            .filter_map(|i| match i {
                IrInstr::StoreStatic(c, s) => Some((*c, *s)),
                _ => None,
            })
            .collect();
        assert_eq!(ops.len(), 2, "one store per static field: {:?}", f.instrs);
        // Declaration order is preserved.
        assert_eq!(ops[0].1, 0);
        assert_eq!(ops[1].1, 1);
        // The tick method uses both load and store forms.
        let tick = checker
            .ir
            .functions
            .iter()
            .find(|f| f.name == "Counter.tick")
            .unwrap();
        let uses_load = tick
            .instrs
            .iter()
            .any(|i| matches!(i, IrInstr::LoadStatic(_, _)));
        let uses_store = tick
            .instrs
            .iter()
            .any(|i| matches!(i, IrInstr::StoreStatic(_, _)));
        assert!(
            uses_load && uses_store,
            "tick must read and write the static"
        );
    }

    #[test]
    fn static_call_carries_owning_class_operand() {
        // CallStatic encodes the target class id as its third operand; the
        // VM uses it as the lazy-initialization owner of the call.
        let text = "package m\n\
            class A {\n\
                static mutable n: Long = 0\n\
                public static bump(): Long { A.n += 1; return A.n }\n\
            }\n\
            class B {\n\
                public static go(): Long { return A.bump() }\n\
            }\n\
            class Main { public static run(args: String...): Long { return B.go() } }\n";
        let mut sources = SourceManager::default();
        sources.add("t.sol", text.to_string());
        let mut diags = Diagnostics::default();
        let tokens = crate::lexer::Lexer::new(0, text).tokenize(&mut diags);
        let mut p = crate::parser::Parser::new(tokens);
        let program = p.parse_program().unwrap();
        diags.items.append(&mut p.diags.items);
        let rp = crate::resolve::resolve_program(&program, &mut diags);
        assert!(!diags.has_errors(), "{:?}", diags.items);
        let mut checker = Checker::new(&rp, &sources);
        checker.check_program();
        assert!(!checker.diags.has_errors(), "{:?}", checker.diags.items);
        let a_id = checker
            .ir
            .classes
            .iter()
            .find(|c| c.name == "A")
            .unwrap()
            .id;
        let go = checker
            .ir
            .functions
            .iter()
            .find(|f| f.name == "B.go")
            .unwrap();
        let call = go
            .instrs
            .iter()
            .find_map(|i| match i {
                IrInstr::CallStatic(_, _, cls) => Some(*cls),
                _ => None,
            })
            .expect("B.go must call A.bump through CallStatic");
        assert_eq!(call, a_id as u16, "CallStatic must name the owning class");
    }

    #[test]
    fn external_static_read_is_private() {
        let d = check_src(
            "package m\n\
             class A {\n\
                 static x: Long = 1\n\
                 public static new(): Self { return Self {} }\n\
             }\n\
             class Main { public static run(args: String...): Long { return A.x } }\n",
        );
        assert!(has_code(&d, "C162"), "{:?}", d.items);
    }

    #[test]
    fn external_static_write_is_private() {
        let d = check_src(
            "package m\n\
             class A {\n\
                 static mutable x: Long = 1\n\
                 public static new(): Self { return Self {} }\n\
             }\n\
             class Main { public static run(args: String...): Long { A.x = 2; return 0 } }\n",
        );
        assert!(has_code(&d, "C162"), "{:?}", d.items);
    }

    #[test]
    fn immutable_static_assignment_rejected() {
        let d = check_src(
            "package m\n\
             class A {\n\
                 static x: Long = 1\n\
                 public static bump(): Long { A.x = 2; return A.x }\n\
             }\n\
             class Main { public static run(args: String...): Long { return 0 } }\n",
        );
        assert!(has_code(&d, "C226"), "{:?}", d.items);
    }

    #[test]
    fn object_receiver_static_field_rejected_on_read_and_write() {
        let read = check_src(
            "package m\n\
             class A {\n\
                 static x: Long = 1\n\
                 public static new(): Self { return Self {} }\n\
                 public get(): Long { let a: A = A.new(); return a.x }\n\
             }\n\
             class Main { public static run(args: String...): Long { return 0 } }\n",
        );
        assert!(has_code(&read, "C234"), "{:?}", read.items);
        let write = check_src(
            "package m\n\
             class A {\n\
                 static mutable x: Long = 1\n\
                 public static new(): Self { return Self {} }\n\
                 public set(v: Long) { let a: A = A.new(); a.x = v }\n\
             }\n\
             class Main { public static run(args: String...): Long { return 0 } }\n",
        );
        assert!(has_code(&write, "C234"), "{:?}", write.items);
    }

    #[test]
    fn static_initializer_cannot_read_static_fields() {
        let d = check_src(
            "package m\n\
             class A {\n\
                 static mutable x: Long = 1\n\
                 static y: Long = A.x\n\
             }\n\
             class Main { public static run(args: String...): Long { return 0 } }\n",
        );
        assert!(has_code(&d, "C233"), "{:?}", d.items);
        // Self-qualified reads are rejected too.
        let d2 = check_src(
            "package m\n\
             class A {\n\
                 static mutable x: Long = 1\n\
                 static y: Long = Self.x\n\
             }\n\
             class Main { public static run(args: String...): Long { return 0 } }\n",
        );
        assert!(has_code(&d2, "C233"), "{:?}", d2.items);
    }

    #[test]
    fn self_init_rejects_static_field_names() {
        let d = check_src(
            "package m\n\
             class A {\n\
                 static mutable x: Long = 1\n\
                 public static new(): Self { return Self { x: 2, } }\n\
             }\n\
             class Main { public static run(args: String...): Long { return 0 } }\n",
        );
        assert!(has_code(&d, "C235"), "{:?}", d.items);
    }

    #[test]
    fn static_initializer_may_call_methods_and_build_collections() {
        let d = check_src(
            "package m\n\
             class A {\n\
                 static mutable n: Long = 0\n\
                 public static bump(): Long { A.n += 1; return A.n }\n\
             }\n\
             class B {\n\
                 static v: Long = A.bump()\n\
                 static items: List<Long> = [1, 2, 3]\n\
             }\n\
             class Main { public static run(args: String...): Long { return 0 } }\n",
        );
        assert!(!d.has_errors(), "{:?}", d.items);
    }

    #[test]
    fn static_block_may_read_and_write_static_fields() {
        // The block runs after every field initializer, so unlike field
        // initializers it may read and write (mutable) static fields.
        let d = check_src(
            "package m\n\
             class A {\n\
                 static mutable x: Long = 1\n\
                 static y: Long = 2\n\
                 static {\n\
                     let sum: Long = Self.x + A.y\n\
                     A.x = sum\n\
                     A.x += 1\n\
                 }\n\
             }\n\
             class Main { public static run(args: String...): Long { return 0 } }\n",
        );
        assert!(!d.has_errors(), "{:?}", d.items);
    }

    #[test]
    fn static_block_resolves_static_members_by_bare_name() {
        // Inside the block, static fields and static methods of the
        // declaring class resolve without a class or Self qualifier.
        let d = check_src(
            "package m\n\
             class A {\n\
                 static mutable x: Long = 1\n\
                 static y: Long = 2\n\
                 public static double(v: Long): Long { return v * 2 }\n\
                 static {\n\
                     let sum: Long = x + y\n\
                     x = double(sum)\n\
                     x += 1\n\
                 }\n\
             }\n\
             class Main { public static run(args: String...): Long { return 0 } }\n",
        );
        assert!(!d.has_errors(), "{:?}", d.items);
        // Outside the block, bare names still do not alias static fields.
        let d2 = check_src(
            "package m\n\
             class A {\n\
                 static mutable x: Long = 1\n\
                 public static get(): Long { return x }\n\
             }\n\
             class Main { public static run(args: String...): Long { return 0 } }\n",
        );
        assert!(has_code(&d2, "C136"), "{:?}", d2.items);
    }

    #[test]
    fn static_block_rejects_self_and_return_value() {
        let d = check_src(
            "package m\n\
             class A {\n\
                 f: Long\n\
                 static { self.f = 1 }\n\
             }\n\
             class Main { public static run(args: String...): Long { return 0 } }\n",
        );
        assert!(has_code(&d, "C153"), "{:?}", d.items);
        let d2 = check_src(
            "package m\n\
             class A {\n\
                 static { return 1 }\n\
             }\n\
             class Main { public static run(args: String...): Long { return 0 } }\n",
        );
        assert!(has_code(&d2, "C143"), "{:?}", d2.items);
    }

    #[test]
    fn static_block_rejects_immutable_static_write() {
        let d = check_src(
            "package m\n\
             class A {\n\
                 static x: Long = 1\n\
                 static { A.x = 2 }\n\
             }\n\
             class Main { public static run(args: String...): Long { return 0 } }\n",
        );
        assert!(has_code(&d, "C226"), "{:?}", d.items);
    }

    #[test]
    fn static_block_alone_produces_static_init() {
        // A class with a static block but no static fields still gets its
        // synthetic initializer, and the block body lands inside it after
        // the (empty) field-initializer phase.
        let text = "package m\n\
            class A {\n\
                static { System.out().println(1) }\n\
            }\n\
            class Main { public static run(args: String...): Long { return 0 } }\n";
        let mut sources = SourceManager::default();
        sources.add("t.sol", text.to_string());
        let mut diags = Diagnostics::default();
        let tokens = crate::lexer::Lexer::new(0, text).tokenize(&mut diags);
        let mut p = crate::parser::Parser::new(tokens);
        let program = p.parse_program().unwrap();
        diags.items.append(&mut p.diags.items);
        let rp = crate::resolve::resolve_program(&program, &mut diags);
        assert!(!diags.has_errors(), "{:?}", diags.items);
        let mut checker = Checker::new(&rp, &sources);
        checker.check_program();
        assert!(!checker.diags.has_errors(), "{:?}", checker.diags.items);
        let a = checker.ir.classes.iter().find(|c| c.name == "A").unwrap();
        assert!(a.static_fields.is_empty());
        let init = a.static_init.expect("static block implies static_init");
        let f = &checker.ir.functions[init as usize];
        assert!(f.params.is_empty() && !f.returns_value);
        // The block's println call must be compiled into the init function.
        assert!(
            f.instrs.iter().any(|i| matches!(
                i,
                IrInstr::CallFn { .. } | IrInstr::CallStatic { .. } | IrInstr::CallNative { .. }
            )),
            "block body missing from init: {:?}",
            f.instrs
        );
    }
}
