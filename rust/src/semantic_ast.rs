//! AST for the native semantic runtime.

use crate::semantic_types::{TypeParam, TypeRef};

#[derive(Clone, Debug, Default)]
pub struct Program {
    pub package: String,
    pub uses: Vec<UseDecl>,
    pub declarations: Vec<Decl>,
}

#[derive(Clone, Debug)]
pub struct UseDecl { pub scheme: String, pub value: String }

#[derive(Clone, Debug)]
pub enum Decl {
    Function(Function),
    Struct(StructDecl),
    Enum(EnumDecl),
    Trait(TraitDecl),
}

#[derive(Clone, Debug)]
pub struct Function {
    pub name: String,
    pub public: bool,
    pub mutating: bool,
    pub params: Vec<Param>,
    pub return_type: TypeRef,
    pub type_params: Vec<TypeParam>,
    pub body: Option<Block>,
}

#[derive(Clone, Debug)]
pub struct Param { pub name: String, pub typ: TypeRef, pub variadic: bool }

#[derive(Clone, Debug)]
pub struct StructDecl { pub name: String, pub public: bool, pub fields: Vec<Field>, pub methods: Vec<Function>, pub type_params: Vec<TypeParam> }
#[derive(Clone, Debug)]
pub struct Field { pub name: String, pub typ: TypeRef, pub public: bool, pub mutable: bool }
#[derive(Clone, Debug)]
pub struct TraitDecl { pub name: String, pub public: bool, pub methods: Vec<Function>, pub type_params: Vec<TypeParam> }
#[derive(Clone, Debug)]
pub struct EnumDecl { pub name: String, pub public: bool, pub members: Vec<EnumMember>, pub type_params: Vec<TypeParam> }
#[derive(Clone, Debug)]
pub struct EnumMember { pub name: String, pub payload: Vec<TypeRef>, pub value: Option<i64> }

#[derive(Clone, Debug)]
pub struct Block { pub statements: Vec<Stmt> }

#[derive(Clone, Debug)]
pub enum Stmt {
    Var { name: String, typ: Option<TypeRef>, value: Option<Expr>, mutable: bool },
    Expr(Expr),
    Return(Option<Expr>),
    If { condition: Expr, then_block: Block, else_branch: Option<Box<Stmt>> },
    While { condition: Expr, body: Block },
    For { names: Vec<String>, iterable: Expr, body: Block },
    Switch { value: Expr, cases: Vec<(Option<Expr>, Block)> },
    Try { body: Block, catch_name: Option<String>, catch: Option<Block>, finally: Option<Block> },
    Throw(Expr),
    Break,
    Continue,
    Block(Block),
}

#[derive(Clone, Debug)]
pub enum Expr {
    Int(i64), Float(f64), Bool(bool), Char(char), String(String), Null,
    Name { name: String, type_args: Vec<TypeRef> },
    Unary { op: String, expr: Box<Expr> },
    Binary { left: Box<Expr>, op: String, right: Box<Expr> },
    Call { callee: Box<Expr>, args: Vec<CallArg>, type_args: Vec<TypeRef> },
    Member { object: Box<Expr>, name: String, type_args: Vec<TypeRef> },
    Index { object: Box<Expr>, index: Box<Expr> },
    List(Vec<Expr>),
    Map(Vec<(Expr, Expr)>),
    Struct { name: String, type_args: Vec<TypeRef>, fields: Vec<(String, Expr)> },
    Function { params: Vec<Param>, return_type: TypeRef, body: Block },
    Spread(Box<Expr>),
}

#[derive(Clone, Debug)]
pub struct CallArg { pub expr: Expr, pub spread: bool }
