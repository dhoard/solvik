package bootstrap

// The bootstrap AST uses integer links instead of implementation-language
// pointers. This makes recursive trees straightforward to represent with
// Solvik's own map and struct values while keeping nodes inspectable.

pub enum AstKind {
    Program
    Package
    Function
    Parameter
    Block
    Variable
    Return
    If
    While
    Expression
    Name
    IntLiteral
    StringLiteral
    CharLiteral
    BooleanLiteral
    Unary
    Binary
    Call
    Closure
    Member
    Index
    Type
    Struct
    Field
    Enum
    EnumCase
    Trait
    Error
}

pub struct AstNode {
    pub kind: string,
    pub text: string,
    pub number: int,
    pub left: int,
    pub right: int,
    pub mut next: int,
    pub line: int,
    pub column: int,
}

pub struct Ast {
    pub nodes: map<int, AstNode>,
    pub count: int,
    pub root: int,
    pub errors: int,
}

pub func makeNode(kind: string, text: string, number: int, left: int, right: int, next: int, line: int, column: int) -> AstNode {
    return AstNode {
        kind: kind,
        text: text,
        number: number,
        left: left,
        right: right,
        next: next,
        line: line,
        column: column,
    }
}

pub func emptyNode() -> AstNode {
    return makeNode("error", "", 0, -1, -1, -1, 0, 0)
}

pub func emptyAst() -> Ast {
    return Ast { nodes: {}, count: 0, root: 0, errors: 0 }
}

pub func kindName(kind: string) -> string {
    return kind
}

pub func countChildren(tree: Ast, node: AstNode) -> int {
    mut total: int = 0
    if node.kind == "if" {
        total = total + countChain(tree, node.left)
        total = total + countChain(tree, node.right)
        total = total + countChain(tree, node.number)
    } else if node.kind == "function" || node.kind == "closure" {
        total = total + countChain(tree, node.left)
        total = total + countChain(tree, node.right)
    } else if node.kind == "block" || node.kind == "program" || node.kind == "struct" || node.kind == "enum" || node.kind == "trait" {
        total = total + countChain(tree, node.left)
        total = total + countChain(tree, node.right)
    } else if node.kind == "variable" || node.kind == "binary" || node.kind == "index" {
        total = total + countChain(tree, node.left)
        total = total + countChain(tree, node.right)
    } else if node.kind == "return" || node.kind == "unary" || node.kind == "member" || node.kind == "field" || node.kind == "enum_case" || node.kind == "expression" {
        total = total + countChain(tree, node.left)
    } else if node.kind == "call" {
        total = total + countChain(tree, node.left)
        total = total + countChain(tree, node.right)
    } else if node.kind == "while" {
        total = total + countChain(tree, node.left)
        total = total + countChain(tree, node.right)
    }
    return total
}

pub func countChain(tree: Ast, id: int) -> int {
    mut total: int = 0
    mut current: int = id
    while current >= 0 {
        node: AstNode = tree.nodes[current]
        total = total + 1 + countChildren(tree, node)
        current = node.next
    }
    return total
}

pub func countTree(tree: Ast) -> int {
    return countChain(tree, tree.root)
}
