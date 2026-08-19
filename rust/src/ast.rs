//! AST node definitions.
//!
//! Port of internal/ast/ast.go. Nodes carry a span and (for expressions) the
//! type assigned during checking, mirroring Go's embedded SpanNode.

use crate::source::Span;
use crate::types::{Kind, Type};
use std::rc::Rc;

// ---- Top level ----

#[derive(Default)]
pub struct Program {
    pub span: Span,
    pub module: String,
    pub uses: Vec<UseDecl>,
    pub enums: Vec<EnumDecl>,
    pub structs: Vec<StructDecl>,
    pub traits: Vec<TraitDecl>,
    pub funcs: Vec<Function>,
}

pub struct UseDecl {
    pub span: Span,
    pub source_type: String, // "url" or "file"
    pub path: String,
    pub checksum: String,
    pub insecure: bool,
}

pub struct EnumDecl {
    pub span: Span,
    pub name: String,
    pub variants: Vec<EnumVariant>,
}

pub struct EnumVariant {
    pub span: Span,
    pub name: String,
    pub value: Option<i64>,
    pub resolved_int: i64,
}

pub struct StructDecl {
    pub span: Span,
    pub name: String,
    pub fields: Vec<StructField>,
    pub methods: Vec<Function>,
}

pub struct StructField {
    pub span: Span,
    pub name: String,
    pub ty: TypeAnnotation,
    pub is_mut: bool,
    pub is_pub: bool,
}

pub struct TraitDecl {
    pub span: Span,
    pub name: String,
    pub methods: Vec<Function>, // abstract methods (no body)
}

// ---- Functions ----

pub struct Function {
    pub span: Span,
    pub name: String,
    pub module: String,
    pub struct_name: String,
    pub is_pub: bool,
    pub is_mut: bool,
    pub parameters: Vec<Parameter>,
    pub return_types: Vec<TypeAnnotation>,
    pub body: Option<Block>,
    pub native: bool,
}

impl Function {
    pub fn new(span: Span, name: &str) -> Function {
        Function {
            span,
            name: name.to_string(),
            module: String::new(),
            struct_name: String::new(),
            is_pub: false,
            is_mut: false,
            parameters: Vec::new(),
            return_types: Vec::new(),
            body: None,
            native: false,
        }
    }
}

pub struct Parameter {
    pub span: Span,
    pub name: String,
    pub ty: TypeAnnotation,
    pub variadic: bool,
}

// ---- Type annotations ----

#[derive(Clone)]
pub struct TypeAnnotation {
    pub span: Span,
    pub kind: Kind,
    pub element: Option<Box<TypeAnnotation>>,
    pub key_type: Option<Box<TypeAnnotation>>,
    pub value_type: Option<Box<TypeAnnotation>>,
    pub nullable: bool,
    pub resolved: Option<Rc<Type>>,
    pub type_name: String,
}

impl TypeAnnotation {
    pub fn prim(span: Span, kind: Kind) -> TypeAnnotation {
        TypeAnnotation {
            span,
            kind,
            element: None,
            key_type: None,
            value_type: None,
            nullable: false,
            resolved: None,
            type_name: String::new(),
        }
    }

    pub fn named(span: Span, type_name: &str) -> TypeAnnotation {
        TypeAnnotation {
            span,
            kind: Kind::Invalid,
            element: None,
            key_type: None,
            value_type: None,
            nullable: false,
            resolved: None,
            type_name: type_name.to_string(),
        }
    }

    pub fn resolved(&self) -> Rc<Type> {
        self.resolved.clone().unwrap_or_else(crate::types::t_invalid)
    }
}

// ---- Statements ----

pub struct StmtNode {
    pub span: Span,
    pub kind: StmtKind,
}

pub enum StmtKind {
    VarDecl(VarDecl),
    Block(Block),
    If(IfStmt),
    While(WhileStmt),
    For(ForStmt),
    Break,
    Continue,
    Return(Vec<ExprNode>),
    Switch(SwitchStmt),
    Try(TryStmt),
    Throw(ExprNode),
    Expr(ExprNode),
}

pub struct Block {
    pub span: Span,
    pub statements: Vec<StmtNode>,
}

pub struct VarDecl {
    pub name: String,
    pub ty: TypeAnnotation,
    pub init: Option<ExprNode>,
    pub is_mut: bool,
}

pub struct IfStmt {
    pub condition: ExprNode,
    pub then_block: Block,
    pub else_ifs: Vec<IfStmt>,
    pub else_block: Option<Block>,
}

pub struct WhileStmt {
    pub condition: ExprNode,
    pub body: Block,
}

pub struct SwitchStmt {
    pub expression: ExprNode,
    pub cases: Vec<SwitchCase>,
    pub default_block: Option<Block>,
}

pub struct SwitchCase {
    pub span: Span,
    pub expression: ExprNode,
    pub body: Block,
}

pub struct TryStmt {
    pub try_body: Block,
    pub catch: Option<CatchClause>,
    pub finally_block: Option<Block>,
}

pub struct CatchClause {
    pub span: Span,
    pub param_name: String,
    pub param_type: TypeAnnotation,
    pub body: Block,
}

pub struct ForStmt {
    pub variable: String,
    pub value_variable: String,
    pub iterable: ExprNode,
    pub body: Block,
}

// ---- Expressions ----

pub struct ExprNode {
    pub span: Span,
    pub ty: Option<Rc<Type>>,
    pub kind: ExprKind,
}

impl ExprNode {
    pub fn new(span: Span, kind: ExprKind) -> ExprNode {
        ExprNode { span, ty: None, kind }
    }

    /// Resolved type (defaults to the invalid sentinel like Go's GetExprType
    /// returning nil followed by defensive handling).
    pub fn get_type(&self) -> Option<Rc<Type>> {
        self.ty.clone()
    }
}

pub enum ExprKind {
    Int(i64),
    Float(f64),
    Bool(bool),
    Char(u32),
    Str(String),
    Byte(u8),
    Null,
    Ident(String),
    Unary {
        op: UnaryOp,
        operand: Box<ExprNode>,
    },
    Binary {
        op: BinOp,
        left: Box<ExprNode>,
        right: Box<ExprNode>,
    },
    Call {
        function: Box<ExprNode>,
        args: Vec<ExprNode>,
    },
    Index {
        target: Box<ExprNode>,
        index: Box<ExprNode>,
    },
    List(Vec<ExprNode>),
    Map {
        keys: Vec<ExprNode>,
        values: Vec<ExprNode>,
    },
    Member {
        object: Box<ExprNode>,
        member: String,
    },
    Spread(Box<ExprNode>),
    NullCoalescing {
        left: Box<ExprNode>,
        right: Box<ExprNode>,
    },
    StructLit {
        type_name: String,
        fields: Vec<String>,
        values: Vec<ExprNode>,
    },
}

// ---- Operators ----

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum UnaryOp {
    Negate,
    Not,
    BitNot,
}

impl UnaryOp {
    pub fn as_str(&self) -> &'static str {
        match self {
            UnaryOp::Negate => "-",
            UnaryOp::Not => "!",
            UnaryOp::BitNot => "~",
        }
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum BinOp {
    Assign,
    Add,
    Sub,
    Mul,
    Div,
    Mod,
    Eq,
    Ne,
    Lt,
    Le,
    Gt,
    Ge,
    And,
    Or,
    BitAnd,
    BitOr,
    BitXor,
    ShiftLeft,
    ShiftRight,
    StrConcat,
}

impl BinOp {
    pub fn as_str(&self) -> &'static str {
        match self {
            BinOp::Assign => "=",
            BinOp::Add => "+",
            BinOp::Sub => "-",
            BinOp::Mul => "*",
            BinOp::Div => "/",
            BinOp::Mod => "%",
            BinOp::Eq => "==",
            BinOp::Ne => "!=",
            BinOp::Lt => "<",
            BinOp::Le => "<=",
            BinOp::Gt => ">",
            BinOp::Ge => ">=",
            BinOp::And => "&&",
            BinOp::Or => "||",
            BinOp::BitAnd => "&",
            BinOp::BitOr => "|",
            BinOp::BitXor => "^",
            BinOp::ShiftLeft => "<<",
            BinOp::ShiftRight => ">>",
            BinOp::StrConcat => "..",
        }
    }
}
