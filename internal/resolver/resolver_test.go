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

package resolver_test

import (
	"testing"

	"github.com/dhoard/solvik-language/internal/ast"
	"github.com/dhoard/solvik-language/internal/resolver"
	"github.com/dhoard/solvik-language/internal/source"
	"github.com/dhoard/solvik-language/internal/types"
)

func testSpan() source.Span {
	return source.NewSpan("test.sol", 0, 1, 1, 1, 1, 2)
}

func makeProg(name string, funcs ...*ast.Function) *ast.Program {
	return &ast.Program{
		SpanNode: ast.WithSpan(testSpan()),
		Module:   name,
		Funcs:    funcs,
	}
}

func makeMainFunc(body ...ast.Statement) *ast.Function {
	return &ast.Function{
		SpanNode:    ast.WithSpan(testSpan()),
		Name:        "main",
		Module:      "test",
		ReturnTypes: []*ast.TypeAnnotation{{Kind: types.KindInt, ResolvedType: types.Int}},
		Body:        &ast.Block{SpanNode: ast.WithSpan(testSpan()), Statements: body},
	}
}

func makeIntLiteral(v int32) *ast.IntLiteral {
	return &ast.IntLiteral{SpanNode: ast.WithSpan(testSpan()), Value: v}
}

func makeReturnStmt(val ast.Expression) *ast.ReturnStmt {
	return &ast.ReturnStmt{SpanNode: ast.WithSpan(testSpan()), Values: []ast.Expression{val}}
}

func TestResolveSimple(t *testing.T) {
	body := []ast.Statement{
		&ast.VariableDecl{
			SpanNode: ast.WithSpan(testSpan()),
			Name:     "x",
			Type:     &ast.TypeAnnotation{Kind: types.KindInt, ResolvedType: types.Int},
			InitExpr: makeIntLiteral(42),
		},
		makeReturnStmt(makeIntLiteral(0)),
	}
	prog := makeProg("test", makeMainFunc(body...))
	src := source.NewSourceText("test.sol", "")
	r := resolver.New(src)
	diags, err := r.Resolve(prog)
	if err != nil {
		t.Errorf("Resolve returned error: %v", err)
	}
	if diags.HasErrors() {
		t.Errorf("unexpected errors: %v", diags.All())
	}
}

func TestResolveUndeclaredVariable(t *testing.T) {
	body := []ast.Statement{
		&ast.AssignStmt{
			SpanNode: ast.WithSpan(testSpan()),
			Name:     "undefined",
			Value:    makeIntLiteral(42),
		},
		makeReturnStmt(makeIntLiteral(0)),
	}
	prog := makeProg("test", makeMainFunc(body...))
	src := source.NewSourceText("test.sol", "")
	r := resolver.New(src)
	diags, err := r.Resolve(prog)
	if err != nil {
		t.Errorf("Resolve returned error: %v", err)
	}
	if !diags.HasErrors() {
		t.Error("expected error for undeclared variable")
	}
}

func TestResolveUndeclaredIdentifier(t *testing.T) {
	body := []ast.Statement{
		&ast.ExprStmt{SpanNode: ast.WithSpan(testSpan()), Expr: &ast.Identifier{SpanNode: ast.WithSpan(testSpan()), Name: "undefinedVar"}},
		makeReturnStmt(makeIntLiteral(0)),
	}
	prog := makeProg("test", makeMainFunc(body...))
	src := source.NewSourceText("test.sol", "")
	r := resolver.New(src)
	diags, err := r.Resolve(prog)
	if err != nil {
		t.Errorf("Resolve returned error: %v", err)
	}
	if !diags.HasErrors() {
		t.Error("expected error for undeclared identifier")
	}
}

func TestResolveBreakOutsideLoop(t *testing.T) {
	body := []ast.Statement{
		&ast.BreakStmt{SpanNode: ast.WithSpan(testSpan())},
		makeReturnStmt(makeIntLiteral(0)),
	}
	prog := makeProg("test", makeMainFunc(body...))
	src := source.NewSourceText("test.sol", "")
	r := resolver.New(src)
	diags, err := r.Resolve(prog)
	if err != nil {
		t.Errorf("Resolve returned error: %v", err)
	}
	if !diags.HasErrors() {
		t.Error("expected error for break outside loop")
	}
}

func TestResolveContinueOutsideLoop(t *testing.T) {
	body := []ast.Statement{
		&ast.ContinueStmt{SpanNode: ast.WithSpan(testSpan())},
		makeReturnStmt(makeIntLiteral(0)),
	}
	prog := makeProg("test", makeMainFunc(body...))
	src := source.NewSourceText("test.sol", "")
	r := resolver.New(src)
	diags, err := r.Resolve(prog)
	if err != nil {
		t.Errorf("Resolve returned error: %v", err)
	}
	if !diags.HasErrors() {
		t.Error("expected error for continue outside loop")
	}
}

func TestResolveBreakInsideLoop(t *testing.T) {
	body := []ast.Statement{
		&ast.WhileStmt{
			SpanNode:  ast.WithSpan(testSpan()),
			Condition: &ast.BoolLiteral{SpanNode: ast.WithSpan(testSpan()), Value: true},
			Body: &ast.Block{SpanNode: ast.WithSpan(testSpan()), Statements: []ast.Statement{
				&ast.BreakStmt{SpanNode: ast.WithSpan(testSpan())},
			}},
		},
		makeReturnStmt(makeIntLiteral(0)),
	}
	prog := makeProg("test", makeMainFunc(body...))
	src := source.NewSourceText("test.sol", "")
	r := resolver.New(src)
	diags, err := r.Resolve(prog)
	if err != nil {
		t.Errorf("Resolve returned error: %v", err)
	}
	if diags.HasErrors() {
		t.Errorf("unexpected errors: %v", diags.All())
	}
}

func TestResolveForStmt(t *testing.T) {
	body := []ast.Statement{
		&ast.ForStmt{
			SpanNode: ast.WithSpan(testSpan()),
			Variable: "x",
			Iterable: &ast.ListLiteral{
				SpanNode: ast.WithSpan(testSpan()),
				Elements: []ast.Expression{makeIntLiteral(1)},
			},
			Body: &ast.Block{SpanNode: ast.WithSpan(testSpan())},
		},
		makeReturnStmt(makeIntLiteral(0)),
	}
	prog := makeProg("test", makeMainFunc(body...))
	src := source.NewSourceText("test.sol", "")
	r := resolver.New(src)
	diags, err := r.Resolve(prog)
	if err != nil {
		t.Errorf("Resolve returned error: %v", err)
	}
	if diags.HasErrors() {
		t.Errorf("unexpected errors: %v", diags.All())
	}
}

func TestResolveForStmtUnpack(t *testing.T) {
	// First declare m as a variable
	body := []ast.Statement{
		&ast.VariableDecl{
			SpanNode: ast.WithSpan(testSpan()),
			Name:     "m",
			Type:     &ast.TypeAnnotation{Kind: types.KindMap, ResolvedType: types.MapOf(types.String, types.Int)},
			InitExpr: &ast.MapLiteral{SpanNode: ast.WithSpan(testSpan()),
				Keys:   []ast.Expression{&ast.StringLiteral{SpanNode: ast.WithSpan(testSpan()), Value: "a"}},
				Values: []ast.Expression{makeIntLiteral(1)},
			},
		},
		&ast.ForStmt{
			SpanNode:      ast.WithSpan(testSpan()),
			Variable:      "k",
			ValueVariable: "v",
			Iterable:      &ast.Identifier{SpanNode: ast.WithSpan(testSpan()), Name: "m"},
			Body:          &ast.Block{SpanNode: ast.WithSpan(testSpan())},
		},
		makeReturnStmt(makeIntLiteral(0)),
	}
	prog := makeProg("test", makeMainFunc(body...))
	src := source.NewSourceText("test.sol", "")
	r := resolver.New(src)
	diags, err := r.Resolve(prog)
	if err != nil {
		t.Errorf("Resolve returned error: %v", err)
	}
	if diags.HasErrors() {
		t.Errorf("unexpected errors: %v", diags.All())
	}
}

func TestResolveIfStmt(t *testing.T) {
	body := []ast.Statement{
		&ast.IfStmt{
			SpanNode:  ast.WithSpan(testSpan()),
			Condition: &ast.BoolLiteral{SpanNode: ast.WithSpan(testSpan()), Value: true},
			Then: &ast.Block{SpanNode: ast.WithSpan(testSpan()), Statements: []ast.Statement{
				&ast.VariableDecl{
					SpanNode: ast.WithSpan(testSpan()),
					Name:     "inner",
					Type:     &ast.TypeAnnotation{Kind: types.KindInt, ResolvedType: types.Int},
					InitExpr: makeIntLiteral(1),
				},
			}},
			ElseIf: []*ast.IfStmt{
				{
					SpanNode:  ast.WithSpan(testSpan()),
					Condition: &ast.BoolLiteral{SpanNode: ast.WithSpan(testSpan()), Value: false},
					Then:      &ast.Block{SpanNode: ast.WithSpan(testSpan())},
				},
			},
			Else: &ast.Block{SpanNode: ast.WithSpan(testSpan())},
		},
		makeReturnStmt(makeIntLiteral(0)),
	}
	prog := makeProg("test", makeMainFunc(body...))
	src := source.NewSourceText("test.sol", "")
	r := resolver.New(src)
	diags, err := r.Resolve(prog)
	if err != nil {
		t.Errorf("Resolve returned error: %v", err)
	}
	if diags.HasErrors() {
		t.Errorf("unexpected errors: %v", diags.All())
	}
}

func TestResolveSwitchStmt(t *testing.T) {
	body := []ast.Statement{
		&ast.SwitchStmt{
			SpanNode:   ast.WithSpan(testSpan()),
			Expression: &ast.Identifier{SpanNode: ast.WithSpan(testSpan()), Name: "x"},
			Cases: []ast.SwitchCase{
				{
					SpanNode:   ast.WithSpan(testSpan()),
					Expression: makeIntLiteral(1),
					Body:       &ast.Block{SpanNode: ast.WithSpan(testSpan())},
				},
			},
			Default: &ast.Block{SpanNode: ast.WithSpan(testSpan())},
		},
		makeReturnStmt(makeIntLiteral(0)),
	}
	// Need to declare x first
	decl := &ast.VariableDecl{
		SpanNode: ast.WithSpan(testSpan()),
		Name:     "x",
		Type:     &ast.TypeAnnotation{Kind: types.KindInt, ResolvedType: types.Int},
		InitExpr: makeIntLiteral(1),
	}
	body2 := []ast.Statement{decl, body[0], body[1]}
	prog := makeProg("test", makeMainFunc(body2...))
	src := source.NewSourceText("test.sol", "")
	r := resolver.New(src)
	diags, err := r.Resolve(prog)
	if err != nil {
		t.Errorf("Resolve returned error: %v", err)
	}
	if diags.HasErrors() {
		t.Errorf("unexpected errors: %v", diags.All())
	}
}

func TestResolveTryStmt(t *testing.T) {
	body := []ast.Statement{
		&ast.TryStmt{
			SpanNode: ast.WithSpan(testSpan()),
			TryBody:  &ast.Block{SpanNode: ast.WithSpan(testSpan())},
			Catch: &ast.CatchClause{
				SpanNode:  ast.WithSpan(testSpan()),
				ParamName: "e",
				ParamType: &ast.TypeAnnotation{Kind: types.KindException, ResolvedType: types.Exception},
				Body:      &ast.Block{SpanNode: ast.WithSpan(testSpan())},
			},
			Finally: &ast.Block{SpanNode: ast.WithSpan(testSpan())},
		},
		makeReturnStmt(makeIntLiteral(0)),
	}
	prog := makeProg("test", makeMainFunc(body...))
	src := source.NewSourceText("test.sol", "")
	r := resolver.New(src)
	diags, err := r.Resolve(prog)
	if err != nil {
		t.Errorf("Resolve returned error: %v", err)
	}
	if diags.HasErrors() {
		t.Errorf("unexpected errors: %v", diags.All())
	}
}

func TestResolveThrowStmt(t *testing.T) {
	body := []ast.Statement{
		&ast.ThrowStmt{
			SpanNode: ast.WithSpan(testSpan()),
			Value:    &ast.StringLiteral{SpanNode: ast.WithSpan(testSpan()), Value: "error"},
		},
	}
	prog := makeProg("test", makeMainFunc(body...))
	src := source.NewSourceText("test.sol", "")
	r := resolver.New(src)
	diags, err := r.Resolve(prog)
	if err != nil {
		t.Errorf("Resolve returned error: %v", err)
	}
	if diags.HasErrors() {
		t.Errorf("unexpected errors: %v", diags.All())
	}
}

func TestResolveBlockScope(t *testing.T) {
	body := []ast.Statement{
		&ast.Block{
			SpanNode: ast.WithSpan(testSpan()),
			Statements: []ast.Statement{
				&ast.VariableDecl{
					SpanNode: ast.WithSpan(testSpan()),
					Name:     "inner",
					Type:     &ast.TypeAnnotation{Kind: types.KindInt, ResolvedType: types.Int},
					InitExpr: makeIntLiteral(1),
				},
			},
		},
		makeReturnStmt(makeIntLiteral(0)),
	}
	prog := makeProg("test", makeMainFunc(body...))
	src := source.NewSourceText("test.sol", "")
	r := resolver.New(src)
	diags, err := r.Resolve(prog)
	if err != nil {
		t.Errorf("Resolve returned error: %v", err)
	}
	if diags.HasErrors() {
		t.Errorf("unexpected errors: %v", diags.All())
	}
}

func TestResolveBinaryExpr(t *testing.T) {
	body := []ast.Statement{
		&ast.ExprStmt{SpanNode: ast.WithSpan(testSpan()), Expr: &ast.BinaryExpr{
			SpanNode: ast.WithSpan(testSpan()),
			Operator: ast.BinAdd,
			Left:     makeIntLiteral(1),
			Right:    makeIntLiteral(2),
		}},
		makeReturnStmt(makeIntLiteral(0)),
	}
	prog := makeProg("test", makeMainFunc(body...))
	src := source.NewSourceText("test.sol", "")
	r := resolver.New(src)
	diags, err := r.Resolve(prog)
	if err != nil {
		t.Errorf("Resolve returned error: %v", err)
	}
	if diags.HasErrors() {
		t.Errorf("unexpected errors: %v", diags.All())
	}
}

func TestResolveCallExpr(t *testing.T) {
	helper := &ast.Function{
		SpanNode:    ast.WithSpan(testSpan()),
		Name:        "helper",
		Module:      "test",
		ReturnTypes: []*ast.TypeAnnotation{{Kind: types.KindInt, ResolvedType: types.Int}},
		Body:        &ast.Block{SpanNode: ast.WithSpan(testSpan()), Statements: []ast.Statement{makeReturnStmt(makeIntLiteral(0))}},
	}
	body := []ast.Statement{
		&ast.ExprStmt{SpanNode: ast.WithSpan(testSpan()), Expr: &ast.CallExpr{
			SpanNode: ast.WithSpan(testSpan()),
			Function: &ast.Identifier{SpanNode: ast.WithSpan(testSpan()), Name: "helper"},
		}},
		makeReturnStmt(makeIntLiteral(0)),
	}
	mainFn := makeMainFunc(body...)
	prog := makeProg("test", helper, mainFn)
	src := source.NewSourceText("test.sol", "")
	r := resolver.New(src)
	diags, err := r.Resolve(prog)
	if err != nil {
		t.Errorf("Resolve returned error: %v", err)
	}
	if diags.HasErrors() {
		t.Errorf("unexpected errors: %v", diags.All())
	}
}

func TestResolveMemberExpr(t *testing.T) {
	// module.function() - the module is a known module
	body := []ast.Statement{
		&ast.ExprStmt{SpanNode: ast.WithSpan(testSpan()), Expr: &ast.MemberExpr{
			SpanNode: ast.WithSpan(testSpan()),
			Object:   &ast.Identifier{SpanNode: ast.WithSpan(testSpan()), Name: "string"},
			Member:   "length",
		}},
		makeReturnStmt(makeIntLiteral(0)),
	}
	prog := makeProg("test", makeMainFunc(body...))
	src := source.NewSourceText("test.sol", "")
	r := resolver.New(src)
	diags, err := r.Resolve(prog)
	if err != nil {
		t.Errorf("Resolve returned error: %v", err)
	}
	if diags.HasErrors() {
		t.Errorf("unexpected errors: %v", diags.All())
	}
}

func TestResolveNullCoalescing(t *testing.T) {
	body := []ast.Statement{
		&ast.ExprStmt{SpanNode: ast.WithSpan(testSpan()), Expr: &ast.NullCoalescing{
			SpanNode: ast.WithSpan(testSpan()),
			Left:     &ast.NullLiteral{SpanNode: ast.WithSpan(testSpan())},
			Right:    &ast.StringLiteral{SpanNode: ast.WithSpan(testSpan()), Value: "default"},
		}},
		makeReturnStmt(makeIntLiteral(0)),
	}
	prog := makeProg("test", makeMainFunc(body...))
	src := source.NewSourceText("test.sol", "")
	r := resolver.New(src)
	diags, err := r.Resolve(prog)
	if err != nil {
		t.Errorf("Resolve returned error: %v", err)
	}
	if diags.HasErrors() {
		t.Errorf("unexpected errors: %v", diags.All())
	}
}

func TestResolveMapLiteral(t *testing.T) {
	body := []ast.Statement{
		&ast.ExprStmt{SpanNode: ast.WithSpan(testSpan()), Expr: &ast.MapLiteral{
			SpanNode: ast.WithSpan(testSpan()),
			Keys:     []ast.Expression{&ast.StringLiteral{SpanNode: ast.WithSpan(testSpan()), Value: "k"}},
			Values:   []ast.Expression{makeIntLiteral(1)},
		}},
		makeReturnStmt(makeIntLiteral(0)),
	}
	prog := makeProg("test", makeMainFunc(body...))
	src := source.NewSourceText("test.sol", "")
	r := resolver.New(src)
	diags, err := r.Resolve(prog)
	if err != nil {
		t.Errorf("Resolve returned error: %v", err)
	}
	if diags.HasErrors() {
		t.Errorf("unexpected errors: %v", diags.All())
	}
}

func TestResolveIndexExpr(t *testing.T) {
	body := []ast.Statement{
		&ast.ExprStmt{SpanNode: ast.WithSpan(testSpan()), Expr: &ast.IndexExpr{
			SpanNode: ast.WithSpan(testSpan()),
			Target:   &ast.ListLiteral{SpanNode: ast.WithSpan(testSpan()), Elements: []ast.Expression{makeIntLiteral(1)}},
			Index:    makeIntLiteral(0),
		}},
		makeReturnStmt(makeIntLiteral(0)),
	}
	prog := makeProg("test", makeMainFunc(body...))
	src := source.NewSourceText("test.sol", "")
	r := resolver.New(src)
	diags, err := r.Resolve(prog)
	if err != nil {
		t.Errorf("Resolve returned error: %v", err)
	}
	if diags.HasErrors() {
		t.Errorf("unexpected errors: %v", diags.All())
	}
}

func TestResolveImport(t *testing.T) {
	prog := makeProg("test", makeMainFunc(makeReturnStmt(makeIntLiteral(0))))
	prog.Imports = []*ast.Import{
		{SpanNode: ast.WithSpan(testSpan()), Module: "fmt", Alias: ""},
	}
	src := source.NewSourceText("test.sol", "")
	r := resolver.New(src)
	diags, err := r.Resolve(prog)
	if err != nil {
		t.Errorf("Resolve returned error: %v", err)
	}
	if diags.HasErrors() {
		t.Errorf("unexpected errors: %v", diags.All())
	}
}

func TestResolveImportWithAlias(t *testing.T) {
	prog := makeProg("test", makeMainFunc(makeReturnStmt(makeIntLiteral(0))))
	prog.Imports = []*ast.Import{
		{SpanNode: ast.WithSpan(testSpan()), Module: "fmt", Alias: "f"},
	}
	src := source.NewSourceText("test.sol", "")
	r := resolver.New(src)
	diags, err := r.Resolve(prog)
	if err != nil {
		t.Errorf("Resolve returned error: %v", err)
	}
	if diags.HasErrors() {
		t.Errorf("unexpected errors: %v", diags.All())
	}
}

func TestResolveSetAllFuncs(t *testing.T) {
	src := source.NewSourceText("test.sol", "")
	r := resolver.New(src)
	r.SetAllFuncs(map[string]*ast.Function{
		"other.helper": {
			Name:        "helper",
			Module:      "other",
			ReturnTypes: []*ast.TypeAnnotation{{Kind: types.KindInt, ResolvedType: types.Int}},
			Body:        &ast.Block{SpanNode: ast.WithSpan(testSpan()), Statements: []ast.Statement{makeReturnStmt(makeIntLiteral(0))}},
		},
	})
	// Resolve a program that uses the other module
	prog := makeProg("test", makeMainFunc(makeReturnStmt(makeIntLiteral(0))))
	diags, err := r.Resolve(prog)
	if err != nil {
		t.Errorf("Resolve returned error: %v", err)
	}
	if diags.HasErrors() {
		t.Errorf("unexpected errors: %v", diags.All())
	}
}

func TestResolveReturnStmt(t *testing.T) {
	body := []ast.Statement{
		makeReturnStmt(makeIntLiteral(0)),
	}
	prog := makeProg("test", makeMainFunc(body...))
	src := source.NewSourceText("test.sol", "")
	r := resolver.New(src)
	diags, err := r.Resolve(prog)
	if err != nil {
		t.Errorf("Resolve returned error: %v", err)
	}
	if diags.HasErrors() {
		t.Errorf("unexpected errors: %v", diags.All())
	}
}

func TestResolveNew(t *testing.T) {
	src := source.NewSourceText("test.sol", "")
	r := resolver.New(src)
	if r == nil {
		t.Fatal("New returned nil")
	}
}

func TestResolveBuiltinFunction(t *testing.T) {
	// Using builtin function names like "print" should not cause errors
	body := []ast.Statement{
		&ast.ExprStmt{SpanNode: ast.WithSpan(testSpan()), Expr: &ast.Identifier{SpanNode: ast.WithSpan(testSpan()), Name: "println"}},
		makeReturnStmt(makeIntLiteral(0)),
	}
	prog := makeProg("test", makeMainFunc(body...))
	src := source.NewSourceText("test.sol", "")
	r := resolver.New(src)
	diags, err := r.Resolve(prog)
	if err != nil {
		t.Errorf("Resolve returned error: %v", err)
	}
	if diags.HasErrors() {
		t.Errorf("unexpected errors: %v", diags.All())
	}
}

func TestResolveKnownModule(t *testing.T) {
	body := []ast.Statement{
		&ast.ExprStmt{SpanNode: ast.WithSpan(testSpan()), Expr: &ast.Identifier{SpanNode: ast.WithSpan(testSpan()), Name: "string"}},
		makeReturnStmt(makeIntLiteral(0)),
	}
	prog := makeProg("test", makeMainFunc(body...))
	src := source.NewSourceText("test.sol", "")
	r := resolver.New(src)
	diags, err := r.Resolve(prog)
	if err != nil {
		t.Errorf("Resolve returned error: %v", err)
	}
	if diags.HasErrors() {
		t.Errorf("unexpected errors: %v", diags.All())
	}
}

func TestResolveUnaryExpr(t *testing.T) {
	body := []ast.Statement{
		&ast.ExprStmt{SpanNode: ast.WithSpan(testSpan()), Expr: &ast.UnaryExpr{
			SpanNode: ast.WithSpan(testSpan()),
			Operator: ast.UnaryNegate,
			Operand:  makeIntLiteral(5),
		}},
		makeReturnStmt(makeIntLiteral(0)),
	}
	prog := makeProg("test", makeMainFunc(body...))
	src := source.NewSourceText("test.sol", "")
	r := resolver.New(src)
	diags, err := r.Resolve(prog)
	if err != nil {
		t.Errorf("Resolve returned error: %v", err)
	}
	if diags.HasErrors() {
		t.Errorf("unexpected errors: %v", diags.All())
	}
}
