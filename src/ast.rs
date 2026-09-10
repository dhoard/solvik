//! Solvik abstract syntax tree.
//
// Spans and some variants are retained for diagnostics and future
// language features even when not yet read.
#![allow(dead_code)]

use crate::source::Span;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Visibility {
    Private,
    Public,
}

#[derive(Debug, Clone)]
pub struct Program {
    pub module: String,
    pub module_span: Span,
    pub uses: Vec<UseDecl>,
    pub items: Vec<Item>,
}

#[derive(Debug, Clone)]
pub struct UseDecl {
    pub path: String, // after "file:" or "url:"
    pub alias: Option<String>,
    pub scheme: UseScheme,
    pub span: Span,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum UseScheme {
    File,
    Url,
}

#[derive(Debug, Clone)]
pub enum Item {
    Class(ClassDef),
    Interface(InterfaceDef),
    Enum(EnumDef),
}

#[derive(Debug, Clone)]
pub struct TypeParam {
    pub name: String,
    /// Nominal interface constraints.
    pub constraints: Vec<TypeRef>,
}

#[derive(Debug, Clone)]
pub struct ClassDef {
    pub name: String,
    pub name_span: Span,
    pub type_params: Vec<TypeParam>,
    /// Interfaces this class explicitly implements.
    pub implements: Vec<TypeRef>,
    /// Explicit interface delegation to composed fields.
    pub delegates: Vec<DelegateDecl>,
    pub fields: Vec<FieldDecl>,
    pub methods: Vec<MethodDef>,
    pub span: Span,
}

#[derive(Debug, Clone)]
pub struct FieldDecl {
    pub name: String,
    pub ty: TypeRef,
    pub mutable: bool,
    pub span: Span,
}

#[derive(Debug, Clone)]
pub struct InterfaceDef {
    pub name: String,
    pub name_span: Span,
    pub type_params: Vec<TypeParam>,
    pub extends: Vec<TypeRef>,
    pub methods: Vec<MethodDef>,
    pub span: Span,
}

#[derive(Debug, Clone)]
pub struct MethodDef {
    pub name: String,
    pub name_span: Span,
    /// `true` when exported to the public method surface; `false` (default)
    /// is class-private.
    pub is_public: bool,
    pub is_static: bool,
    pub type_params: Vec<TypeParam>,
    pub params: Vec<Param>,
    pub return_ty: Option<TypeRef>,
    pub body: Option<Block>,
    pub span: Span,
}

#[derive(Debug, Clone)]
pub struct Param {
    pub name: String,
    pub ty: TypeRef,
    pub default: Option<Expr>,
    pub variadic: bool,
    pub span: Span,
}

#[derive(Debug, Clone)]
pub struct EnumDef {
    pub name: String,
    pub name_span: Span,
    pub type_params: Vec<TypeParam>,
    pub variants: Vec<EnumVariant>,
    pub span: Span,
}

#[derive(Debug, Clone)]
pub struct EnumVariant {
    pub name: String,
    pub payload: Option<TypeRef>,
    pub span: Span,
}

#[derive(Debug, Clone)]
pub struct Block {
    pub stmts: Vec<Stmt>,
    pub span: Span,
}

#[derive(Debug, Clone)]
pub struct TypeRef {
    pub base: TypeBase,
    pub nullable: bool,
    pub span: Span,
}

impl TypeRef {
    pub fn named(name: &str, span: Span) -> Self {
        TypeRef {
            base: TypeBase::Named(name.to_string()),
            nullable: false,
            span,
        }
    }
}

#[derive(Debug, Clone)]
pub enum TypeBase {
    Named(String),          // includes built-ins and `Self`
    Nullable(Box<TypeRef>), // parsed form; normalized to nullable flag
    Generic(String, Vec<TypeRef>),
}

// ---------------------------------------------------------------------------
// Statements
// ---------------------------------------------------------------------------

#[derive(Debug, Clone)]
pub enum Stmt {
    Decl(DeclStmt),
    Expr(ExprStmt),
    Return(Option<Expr>),
    If(Box<IfStmt>),
    While(Box<WhileStmt>),
    ForIn(Box<ForInStmt>),
    Switch(Box<SwitchStmt>),
    Try(Box<TryStmt>),
    Throw(Expr),
    Break,
    Continue,
}

#[derive(Debug, Clone)]
pub struct DeclStmt {
    pub name: String,
    pub ty: TypeRef,
    pub init: Option<Expr>,
    pub mutable: bool,
    pub span: Span,
}

#[derive(Debug, Clone)]
pub struct ExprStmt {
    pub expr: Expr,
    pub span: Span,
}

#[derive(Debug, Clone)]
pub struct IfStmt {
    pub cond: Expr,
    pub then: Block,
    pub else_branch: Option<ElseBranch>,
    pub span: Span,
}

#[derive(Debug, Clone)]
pub enum ElseBranch {
    Block(Block),
    If(Box<IfStmt>),
}

#[derive(Debug, Clone)]
pub struct WhileStmt {
    pub cond: Expr,
    pub body: Block,
    pub span: Span,
}

#[derive(Debug, Clone)]
pub struct ForInStmt {
    pub var: String,
    pub var_span: Span,
    pub iter: Expr,
    pub body: Block,
    pub span: Span,
}

#[derive(Debug, Clone)]
pub struct SwitchStmt {
    pub subject: Expr,
    pub cases: Vec<SwitchCase>,
    pub span: Span,
}

#[derive(Debug, Clone)]
pub struct SwitchCase {
    pub values: Vec<Expr>, // literal values; empty = default
    pub is_default: bool,
    pub body: Block,
    pub span: Span,
}

#[derive(Debug, Clone)]
pub struct TryStmt {
    pub body: Block,
    pub catch_name: Option<String>,
    pub catch_body: Option<Block>,
    pub finally_body: Option<Block>,
    pub span: Span,
}

// ---------------------------------------------------------------------------
// Expressions
// ---------------------------------------------------------------------------

#[derive(Debug, Clone)]
pub enum Expr {
    Int(i64, Span),
    Float(f64, Span),
    Bool(bool, Span),
    Char(char, Span),
    String(String, Span),
    Null(Span),
    Ident(String, Span),
    List(Vec<Expr>, Span),
    Map(Vec<(Expr, Expr)>, Span),
    Call(CallExpr),
    Member(MemberExpr),
    StaticAccess(StaticAccessExpr),
    Binary(BinaryExpr),
    Unary(UnaryOp, Box<Expr>),
    Coalesce(Box<Expr>, Box<Expr>),
    Range(Box<Expr>, Box<Expr>),
    Match(Box<MatchExpr>),
    SelfInit(Box<SelfInitExpr>),
    Assign(AssignExpr),
    Update(UpdateOp, Box<AssignExpr>),
}

/// Explicit delegation of an interface to a composed field member.
#[derive(Debug, Clone)]
pub struct DelegateDecl {
    pub interface: TypeRef,
    /// Resolved interface id, filled by the resolver (0 if unresolved).
    pub interface_id: u32,
    /// Resolved interface type arguments (empty for non-generic interfaces).
    pub interface_args: Vec<crate::types::BaseType>,
    pub target_field: String,
    pub span: Span,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum UnaryOp {
    Neg,
    Not,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum UpdateOp {
    Add,
    Sub,
    Mul,
    Div,
    Mod,
}

#[derive(Debug, Clone)]
pub struct CallExpr {
    pub callee: Box<Expr>,
    pub args: Vec<Arg>,
    pub span: Span,
}

#[derive(Debug, Clone)]
pub struct Arg {
    pub name: Option<String>, // named argument
    pub expr: Expr,
    pub spread: bool, // `...list` expansion
    pub span: Span,
}

#[derive(Debug, Clone)]
pub struct MemberExpr {
    pub obj: Box<Expr>,
    pub name: String,
    pub span: Span,
}

#[derive(Debug, Clone)]
pub struct StaticAccessExpr {
    pub ty: TypeRef,
    pub name: String,
    pub span: Span,
}

#[derive(Debug, Clone)]
pub struct BinaryExpr {
    pub op: BinOp,
    pub left: Box<Expr>,
    pub right: Box<Expr>,
    pub span: Span,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum BinOp {
    Add,
    Sub,
    Mul,
    Div,
    Mod,
    Concat, // .. on strings
    And,    // &&
    Or,     // ||
    Eq,
    Ne,
    Lt,
    Le,
    Gt,
    Ge,
}

#[derive(Debug, Clone)]
pub struct MatchExpr {
    pub subject: Box<Expr>,
    pub arms: Vec<MatchArm>,
    pub span: Span,
}

#[derive(Debug, Clone)]
pub struct MatchArm {
    pub pattern: Pattern,
    pub guard: Option<Expr>,
    pub body: Expr,
    pub span: Span,
}

#[derive(Debug, Clone)]
pub enum Pattern {
    Wildcard,
    LiteralInt(i64),
    LiteralFloat(f64),
    LiteralBool(bool),
    LiteralChar(char),
    LiteralString(String),
    Bind(String),
    Variant(String, Vec<Pattern>),
    List(Vec<Pattern>),
}

#[derive(Debug, Clone)]
pub struct SelfInitExpr {
    /// Field initializers `{ name: expr, ... }` for `Self { ... }`.
    pub fields: Vec<(String, Expr)>,
    pub span: Span,
}

#[derive(Debug, Clone)]
pub struct AssignExpr {
    pub target: Box<Expr>,
    pub value: Box<Expr>,
    pub span: Span,
}

impl Expr {
    /// Source span of the expression. Literals carry their own token
    /// spans; operator forms without a dedicated span field delegate to
    /// their leading operand.
    pub fn span(&self) -> Span {
        match self {
            Expr::Int(_, s)
            | Expr::Float(_, s)
            | Expr::Bool(_, s)
            | Expr::Char(_, s)
            | Expr::String(_, s)
            | Expr::Null(s)
            | Expr::Ident(_, s)
            | Expr::List(_, s)
            | Expr::Map(_, s) => *s,
            Expr::Call(c) => c.span,
            Expr::Member(m) => m.span,
            Expr::StaticAccess(s) => s.span,
            Expr::Binary(b) => b.span,
            Expr::Match(m) => m.span,
            Expr::SelfInit(si) => si.span,
            Expr::Assign(a) => a.span,
            Expr::Update(_, a) => a.span,
            Expr::Unary(_, e) => e.span(),
            Expr::Coalesce(l, _) => l.span(),
            Expr::Range(l, _) => l.span(),
        }
    }
}
