// Copyright (c) 2026-present Douglas Hoard
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// Package ast defines the abstract syntax tree nodes for the language.
package ast

import (
	"github.com/dhoard/solvik-language/internal/source"
	"github.com/dhoard/solvik-language/internal/types"
)

// Node represents a node in the AST.
type Node interface {
	Span() source.Span
}

// --- Top-level nodes ---

// Program represents the root of the AST.
type Program struct {
	SpanNode
	Module  string
	Imports []*Import
	Uses    []*UseDecl
	Enums   []*EnumDecl
	Funcs   []*Function
}

// Import represents a module import.
type Import struct {
	SpanNode
	Module string
	Alias  string // empty if no alias
}

// UseDecl represents a use dependency declaration.
type UseDecl struct {
	SpanNode
	SourceType string // "url" or "file"
	Path       string // URL or file path
	Checksum   string // lowercase sha-256 hex (64 chars), empty if not provided
	Insecure   bool   // if true, allow HTTP and skip TLS verification (ignored for file:)
}

// --- Enum declarations ---

// EnumDecl represents an enum type declaration.
type EnumDecl struct {
	SpanNode
	Name     string
	Variants []EnumVariant
}

// EnumVariant represents a single variant in an enum.
type EnumVariant struct {
	SpanNode
	Name        string
	Value       *int32 // nil if auto-assigned; pointer to distinguish 0 from "not set"
	ResolvedInt int32  // set during type checking (the final assigned value)
}

// --- Functions ---

// Function represents a function declaration.
type Function struct {
	SpanNode
	Name        string
	Module      string // module/package name this function belongs to
	Parameters  []*Parameter
	ReturnTypes []*TypeAnnotation
	Body        *Block
	Native      bool // true for native/host functions
}

// SingleReturnType returns the single return type annotation, or nil if multi/void.
// Provided for backward compatibility during the transition to multi-return.
func (f *Function) SingleReturnType() *TypeAnnotation {
	if len(f.ReturnTypes) == 1 {
		return f.ReturnTypes[0]
	}
	return nil
}

// ReturnCount returns the number of return values (0 for void).
func (f *Function) ReturnCount() int {
	return len(f.ReturnTypes)
}

// Parameter represents a function parameter.
type Parameter struct {
	SpanNode
	Name     string
	Type     *TypeAnnotation
	Variadic bool // true if this is the ...T variadic parameter
}

// --- Type annotation ---

// TypeAnnotation represents a type annotation in source.
type TypeAnnotation struct {
	SpanNode
	Kind         types.Kind
	Element      *TypeAnnotation // for List<T>
	KeyType      *TypeAnnotation // for Map<K,V>
	ValueType    *TypeAnnotation // for Map<K,V>
	Nullable     bool
	ResolvedType *types.Type // set during type checking
	TypeName     string      // for user-defined type names like "Color"
}

// --- Declarations ---

// VariableDecl represents a variable declaration.
type VariableDecl struct {
	SpanNode
	Name     string
	Type     *TypeAnnotation
	InitExpr Expression
	IsMut    bool // true if declared with 'mut' keyword (mutable)
}

// --- Statements ---

// Statement represents a statement.
type Statement interface {
	Node
	stmtNode()
}

// Block represents a block of statements.
type Block struct {
	SpanNode
	Statements []Statement
}

// AssignStmt represents an assignment.
type AssignStmt struct {
	SpanNode
	Name  string
	Value Expression
}

// MultiAssignExpr represents a multi-target assignment expression: a, b = expr
// Where expr returns multiple values (e.g., multi-return function call).
type MultiAssignExpr struct {
	SpanNode
	Names []string
	Value Expression
}

// IfStmt represents an if/else if/else block.
type IfStmt struct {
	SpanNode
	Condition Expression
	Then      *Block
	ElseIf    []*IfStmt // chained else-if branches
	Else      *Block
}

// WhileStmt represents a while loop.
type WhileStmt struct {
	SpanNode
	Condition Expression
	Body      *Block
}

// SwitchStmt represents a switch statement.
type SwitchStmt struct {
	SpanNode
	Expression Expression
	Cases      []SwitchCase
	Default    *Block // nil if no default clause
}

// SwitchCase represents a single case clause in a switch statement.
type SwitchCase struct {
	SpanNode
	Expression Expression
	Body       *Block
}

// TryStmt represents a try/catch/finally statement.
type TryStmt struct {
	SpanNode
	TryBody *Block
	Catch   *CatchClause // optional
	Finally *Block       // optional
}

// CatchClause represents a catch clause in a try statement.
type CatchClause struct {
	SpanNode
	ParamName string
	ParamType *TypeAnnotation
	Body      *Block
}

// ThrowStmt represents a throw statement.
type ThrowStmt struct {
	SpanNode
	Value Expression
}

// ForStmt represents a for-in loop.
// Supports:
//
//	for var in iterable       (single variable)
//	for (key, value) in map   (key/value unpacking for maps)
type ForStmt struct {
	SpanNode
	Variable      string
	ValueVariable string // set for (key, value) unpacking; empty for single variable
	Iterable      Expression
	Body          *Block
}

// BreakStmt represents a break statement.
type BreakStmt struct {
	SpanNode
}

// ContinueStmt represents a continue statement.
type ContinueStmt struct {
	SpanNode
}

// ReturnStmt represents a return statement.
type ReturnStmt struct {
	SpanNode
	Values []Expression
}

// ExprStmt wraps an expression as a statement.
type ExprStmt struct {
	SpanNode
	Expr Expression
}

func (*VariableDecl) stmtNode() {}
func (*Block) stmtNode()        {}
func (*AssignStmt) stmtNode()   {}
func (*IfStmt) stmtNode()       {}
func (*WhileStmt) stmtNode()    {}
func (*ForStmt) stmtNode()      {}
func (*BreakStmt) stmtNode()    {}
func (*ContinueStmt) stmtNode() {}
func (*ReturnStmt) stmtNode()   {}
func (*SwitchStmt) stmtNode()   {}
func (*TryStmt) stmtNode()      {}
func (*ThrowStmt) stmtNode()    {}
func (*ExprStmt) stmtNode()     {}

// --- Expressions ---

// Expression represents an expression.
type Expression interface {
	Node
	TypeCarrier
	exprNode()
}

// TypeCarrier is implemented by nodes that carry a resolved type.
type TypeCarrier interface {
	SetExprType(t *types.Type)
	GetExprType() *types.Type
}

// Literal expressions
type (
	IntLiteral struct {
		SpanNode
		Value int32
	}
	LongLiteral struct {
		SpanNode
		Value int64
	}
	FloatLiteral struct {
		SpanNode
		Value float32
	}
	DoubleLiteral struct {
		SpanNode
		Value float64
	}
	BoolLiteral struct {
		SpanNode
		Value bool
	}
	CharLiteral struct {
		SpanNode
		Value rune
	}
	StringLiteral struct {
		SpanNode
		Value string
	}
	ByteLiteral struct {
		SpanNode
		Value uint8
	}
	NullLiteral struct {
		SpanNode
	}
)

// Identifier represents a variable/parameter reference.
type Identifier struct {
	SpanNode
	Name string
}

// UnaryExpr represents a unary operation.
type UnaryExpr struct {
	SpanNode
	Operator UnaryOp
	Operand  Expression
}

// BinaryExpr represents a binary operation.
type BinaryExpr struct {
	SpanNode
	Operator BinOp
	Left     Expression
	Right    Expression
}

// CallExpr represents a function call.
type CallExpr struct {
	SpanNode
	Function Expression // identifier or other expression
	Args     []Expression
}

// IndexExpr represents an indexing operation (list[index] or map[key]).
type IndexExpr struct {
	SpanNode
	Target Expression
	Index  Expression
}

// ListLiteral represents a list literal [1, 2, 3].
type ListLiteral struct {
	SpanNode
	Elements []Expression
}

// MapLiteral represents a map literal {"key": value}.
type MapLiteral struct {
	SpanNode
	Keys   []Expression
	Values []Expression
}

// MemberExpr represents a member access expression (e.g., module.function).
type MemberExpr struct {
	SpanNode
	Object Expression // the object/module being accessed
	Member string     // the member name
}

// EnumVariantRef represents a reference to an enum variant like Color.Red.
// This is a convenience node; it can also be represented as a MemberExpr.
type EnumVariantRef struct {
	SpanNode
	EnumName    string // "Color"
	VariantName string // "Red"
}

// SpreadExpr represents an expression with ... suffix used to spread a list into variadic args.
type SpreadExpr struct {
	SpanNode
	Expr Expression // the list expression being spread
}

// NullCoalescing represents the ?? operator.
type NullCoalescing struct {
	SpanNode
	Left  Expression
	Right Expression
}

// Implement exprNode marker.
func (*IntLiteral) exprNode()      {}
func (*LongLiteral) exprNode()     {}
func (*FloatLiteral) exprNode()    {}
func (*DoubleLiteral) exprNode()   {}
func (*BoolLiteral) exprNode()     {}
func (*CharLiteral) exprNode()     {}
func (*StringLiteral) exprNode()   {}
func (*ByteLiteral) exprNode()     {}
func (*NullLiteral) exprNode()     {}
func (*Identifier) exprNode()      {}
func (*UnaryExpr) exprNode()       {}
func (*BinaryExpr) exprNode()      {}
func (*CallExpr) exprNode()        {}
func (*IndexExpr) exprNode()       {}
func (*ListLiteral) exprNode()     {}
func (*MapLiteral) exprNode()      {}
func (*MemberExpr) exprNode()      {}
func (*EnumVariantRef) exprNode()  {}
func (*SpreadExpr) exprNode()      {}
func (*NullCoalescing) exprNode()  {}
func (*MultiAssignExpr) exprNode() {}

// --- Operators ---

// UnaryOp represents a unary operator.
type UnaryOp int

const (
	UnaryNegate UnaryOp = iota // -
	UnaryNot                   // !
	UnaryBitNot                // ~
)

func (op UnaryOp) String() string {
	switch op {
	case UnaryNegate:
		return "-"
	case UnaryNot:
		return "!"
	case UnaryBitNot:
		return "~"
	default:
		return "?"
	}
}

// BinOp represents a binary operator.
type BinOp int

const (
	BinAssign     BinOp = iota // =
	BinAdd                     // +
	BinSub                     // -
	BinMul                     // *
	BinDiv                     // /
	BinMod                     // %
	BinEq                      // ==
	BinNe                      // !=
	BinLt                      // <
	BinLe                      // <=
	BinGt                      // >
	BinGe                      // >=
	BinAnd                     // &&
	BinOr                      // ||
	BinBitAnd                  // &
	BinBitOr                   // |
	BinBitXor                  // ^
	BinShiftLeft               // <<
	BinShiftRight              // >>
	BinStrConcat               // ++ (string concatenation)
)

func (op BinOp) String() string {
	switch op {
	case BinAssign:
		return "="
	case BinAdd:
		return "+"
	case BinSub:
		return "-"
	case BinMul:
		return "*"
	case BinDiv:
		return "/"
	case BinMod:
		return "%"
	case BinEq:
		return "=="
	case BinNe:
		return "!="
	case BinLt:
		return "<"
	case BinLe:
		return "<="
	case BinGt:
		return ">"
	case BinGe:
		return ">="
	case BinAnd:
		return "&&"
	case BinOr:
		return "||"
	case BinBitAnd:
		return "&"
	case BinBitOr:
		return "|"
	case BinBitXor:
		return "^"
	case BinShiftLeft:
		return "<<"
	case BinShiftRight:
		return ">>"
	case BinStrConcat:
		return ".."
	default:
		return "?"
	}
}

// --- Helper node ---

// SpanNode provides embedded span storage for nodes.
type SpanNode struct {
	span     source.Span
	ExprType *types.Type // set during type checking for expression nodes
}

// Span returns the node's source span.
func (n *SpanNode) Span() source.Span {
	return n.span
}

// SetSpan sets the node's source span.
func (n *SpanNode) SetSpan(s source.Span) {
	n.span = s
}

// SetExprType sets the resolved type for this node.
func (n *SpanNode) SetExprType(t *types.Type) {
	n.ExprType = t
}

// GetExprType returns the resolved type for this node.
func (n *SpanNode) GetExprType() *types.Type {
	return n.ExprType
}

// WithSpan creates a span node with the given span.
func WithSpan(s source.Span) SpanNode {
	return SpanNode{span: s}
}

// Ensure interfaces are satisfied at compile time.
var (
	_ Node       = (*Program)(nil)
	_ Node       = (*Import)(nil)
	_ Node       = (*Function)(nil)
	_ Node       = (*Parameter)(nil)
	_ Node       = (*VariableDecl)(nil)
	_ Statement  = (*Block)(nil)
	_ Statement  = (*AssignStmt)(nil)
	_ Statement  = (*IfStmt)(nil)
	_ Statement  = (*WhileStmt)(nil)
	_ Statement  = (*ForStmt)(nil)
	_ Statement  = (*BreakStmt)(nil)
	_ Statement  = (*ContinueStmt)(nil)
	_ Statement  = (*ReturnStmt)(nil)
	_ Statement  = (*ExprStmt)(nil)
	_ Statement  = (*SwitchStmt)(nil)
	_ Expression = (*IntLiteral)(nil)
	_ Expression = (*LongLiteral)(nil)
	_ Expression = (*FloatLiteral)(nil)
	_ Expression = (*DoubleLiteral)(nil)
	_ Expression = (*BoolLiteral)(nil)
	_ Expression = (*CharLiteral)(nil)
	_ Expression = (*StringLiteral)(nil)
	_ Expression = (*ByteLiteral)(nil)
	_ Expression = (*NullLiteral)(nil)
	_ Expression = (*Identifier)(nil)
	_ Expression = (*UnaryExpr)(nil)
	_ Expression = (*BinaryExpr)(nil)
	_ Expression = (*CallExpr)(nil)
	_ Expression = (*IndexExpr)(nil)
	_ Expression = (*ListLiteral)(nil)
	_ Expression = (*MapLiteral)(nil)
	_ Expression = (*MemberExpr)(nil)
	_ Expression = (*NullCoalescing)(nil)
	_ Expression = (*MultiAssignExpr)(nil)
	_ Expression = (*EnumVariantRef)(nil)
	_ Expression = (*SpreadExpr)(nil)
)
