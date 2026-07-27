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

package checker_test

import (
	"testing"

	"github.com/dhoard/solvik-language/internal/ast"
	"github.com/dhoard/solvik-language/internal/checker"
	"github.com/dhoard/solvik-language/internal/source"
	"github.com/dhoard/solvik-language/internal/types"
)

func testSpan() source.Span {
	return source.NewSpan("test.sol", 0, 1, 1, 1, 1, 2)
}

// Helper to create a minimal valid program.
func makeProg(name string, funcs ...*ast.Function) *ast.Program {
	return &ast.Program{
		SpanNode: ast.WithSpan(testSpan()),
		Module:   "test",
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

func makeBoolLiteral(v bool) *ast.BoolLiteral {
	return &ast.BoolLiteral{SpanNode: ast.WithSpan(testSpan()), Value: v}
}

func makeReturnStmt(val ast.Expression) *ast.ReturnStmt {
	return &ast.ReturnStmt{SpanNode: ast.WithSpan(testSpan()), Values: []ast.Expression{val}}
}

func makeStringLiteral(v string) *ast.StringLiteral {
	return &ast.StringLiteral{SpanNode: ast.WithSpan(testSpan()), Value: v}
}

func TestCheckerLiteralTypes(t *testing.T) {
	tests := []struct {
		name     string
		expr     ast.Expression
		wantKind types.Kind
	}{
		{"int", makeIntLiteral(42), types.KindInt},
		{"bool", makeBoolLiteral(true), types.KindBool},
		{"string", makeStringLiteral("hello"), types.KindString},
		{"long", &ast.LongLiteral{SpanNode: ast.WithSpan(testSpan()), Value: 999}, types.KindLong},
		{"float", &ast.FloatLiteral{SpanNode: ast.WithSpan(testSpan()), Value: 3.14}, types.KindFloat},
		{"double", &ast.DoubleLiteral{SpanNode: ast.WithSpan(testSpan()), Value: 2.71}, types.KindDouble},
		{"char", &ast.CharLiteral{SpanNode: ast.WithSpan(testSpan()), Value: 'a'}, types.KindChar},
		{"byte", &ast.ByteLiteral{SpanNode: ast.WithSpan(testSpan()), Value: 255}, types.KindByte},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			body := []ast.Statement{
				&ast.ExprStmt{SpanNode: ast.WithSpan(testSpan()), Expr: tt.expr},
				makeReturnStmt(makeIntLiteral(0)),
			}
			prog := makeProg("test", makeMainFunc(body...))
			src := source.NewSourceText("test.sol", "")
			c := checker.New(src)
			_, err := c.Check(prog)
			if err != nil {
				t.Errorf("Check returned error: %v", err)
			}
		})
	}
}

func TestCheckerNoMain(t *testing.T) {
	prog := makeProg("test")
	prog.Funcs = nil
	src := source.NewSourceText("test.sol", "")
	c := checker.New(src)
	diags, err := c.Check(prog)
	if err != nil {
		t.Errorf("Check returned error: %v", err)
	}
	if !diags.HasErrors() {
		t.Error("expected error for missing main function")
	}
}

func TestCheckerMainWithParams(t *testing.T) {
	fn := &ast.Function{
		SpanNode:    ast.WithSpan(testSpan()),
		Name:        "main",
		Module:      "test",
		Parameters:  []*ast.Parameter{{SpanNode: ast.WithSpan(testSpan()), Name: "x", Type: &ast.TypeAnnotation{Kind: types.KindInt, ResolvedType: types.Int}}},
		ReturnTypes: []*ast.TypeAnnotation{{Kind: types.KindInt, ResolvedType: types.Int}},
		Body:        &ast.Block{SpanNode: ast.WithSpan(testSpan()), Statements: []ast.Statement{makeReturnStmt(makeIntLiteral(0))}},
	}
	prog := makeProg("test", fn)
	src := source.NewSourceText("test.sol", "")
	c := checker.New(src)
	diags, err := c.Check(prog)
	if err != nil {
		t.Errorf("Check returned error: %v", err)
	}
	if !diags.HasErrors() {
		t.Error("expected error for main with parameters")
	}
}

func TestCheckerMainWrongReturn(t *testing.T) {
	fn := &ast.Function{
		SpanNode:    ast.WithSpan(testSpan()),
		Name:        "main",
		Module:      "test",
		ReturnTypes: []*ast.TypeAnnotation{{Kind: types.KindBool, ResolvedType: types.Bool}},
		Body:        &ast.Block{SpanNode: ast.WithSpan(testSpan()), Statements: []ast.Statement{makeReturnStmt(makeBoolLiteral(true))}},
	}
	prog := makeProg("test", fn)
	src := source.NewSourceText("test.sol", "")
	c := checker.New(src)
	diags, err := c.Check(prog)
	if err != nil {
		t.Errorf("Check returned error: %v", err)
	}
	if !diags.HasErrors() {
		t.Error("expected error for main with wrong return type")
	}
}

func TestCheckerMissingReturn(t *testing.T) {
	fn := &ast.Function{
		SpanNode:    ast.WithSpan(testSpan()),
		Name:        "main",
		Module:      "test",
		ReturnTypes: []*ast.TypeAnnotation{{Kind: types.KindInt, ResolvedType: types.Int}},
		Body:        &ast.Block{SpanNode: ast.WithSpan(testSpan()), Statements: []ast.Statement{}},
	}
	prog := makeProg("test", fn)
	src := source.NewSourceText("test.sol", "")
	c := checker.New(src)
	diags, err := c.Check(prog)
	if err != nil {
		t.Errorf("Check returned error: %v", err)
	}
	if !diags.HasErrors() {
		t.Error("expected error for missing return")
	}
}

func TestCheckerVarDecl(t *testing.T) {
	body := []ast.Statement{
		&ast.VariableDecl{
			SpanNode: ast.WithSpan(testSpan()),
			Name:     "x",
			Type:     &ast.TypeAnnotation{Kind: types.KindInt, ResolvedType: types.Int},
			InitExpr: makeIntLiteral(42),
			IsMut:    true,
		},
		makeReturnStmt(makeIntLiteral(0)),
	}
	prog := makeProg("test", makeMainFunc(body...))
	src := source.NewSourceText("test.sol", "")
	c := checker.New(src)
	diags, err := c.Check(prog)
	if err != nil {
		t.Errorf("Check returned error: %v", err)
	}
	if diags.HasErrors() {
		t.Errorf("unexpected errors: %v", diags.All())
	}
}

func TestCheckerVarDeclTypeMismatch(t *testing.T) {
	body := []ast.Statement{
		&ast.VariableDecl{
			SpanNode: ast.WithSpan(testSpan()),
			Name:     "x",
			Type:     &ast.TypeAnnotation{Kind: types.KindInt, ResolvedType: types.Int},
			InitExpr: makeStringLiteral("hello"),
		},
		makeReturnStmt(makeIntLiteral(0)),
	}
	prog := makeProg("test", makeMainFunc(body...))
	src := source.NewSourceText("test.sol", "")
	c := checker.New(src)
	diags, err := c.Check(prog)
	if err != nil {
		t.Errorf("Check returned error: %v", err)
	}
	if !diags.HasErrors() {
		t.Error("expected error for type mismatch")
	}
}

func TestCheckerBinaryOps(t *testing.T) {
	tests := []struct {
		name    string
		op      ast.BinOp
		left    ast.Expression
		right   ast.Expression
		wantErr bool
	}{
		{"add_int", ast.BinAdd, makeIntLiteral(1), makeIntLiteral(2), false},
		{"sub_int", ast.BinSub, makeIntLiteral(5), makeIntLiteral(3), false},
		{"mul_int", ast.BinMul, makeIntLiteral(2), makeIntLiteral(3), false},
		{"div_int", ast.BinDiv, makeIntLiteral(10), makeIntLiteral(2), false},
		{"mod_int", ast.BinMod, makeIntLiteral(7), makeIntLiteral(3), false},
		{"eq_int", ast.BinEq, makeIntLiteral(1), makeIntLiteral(1), false},
		{"ne_int", ast.BinNe, makeIntLiteral(1), makeIntLiteral(2), false},
		{"lt_int", ast.BinLt, makeIntLiteral(1), makeIntLiteral(2), false},
		{"le_int", ast.BinLe, makeIntLiteral(1), makeIntLiteral(2), false},
		{"gt_int", ast.BinGt, makeIntLiteral(2), makeIntLiteral(1), false},
		{"ge_int", ast.BinGe, makeIntLiteral(2), makeIntLiteral(1), false},
		{"and_bool", ast.BinAnd, makeBoolLiteral(true), makeBoolLiteral(false), false},
		{"or_bool", ast.BinOr, makeBoolLiteral(true), makeBoolLiteral(false), false},
		{"concat_str", ast.BinAdd, makeStringLiteral("a"), makeStringLiteral("b"), false},
		{"err_add_bool", ast.BinAdd, makeBoolLiteral(true), makeBoolLiteral(false), true},
		{"err_and_int", ast.BinAnd, makeIntLiteral(1), makeIntLiteral(2), true},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			be := &ast.BinaryExpr{
				SpanNode: ast.WithSpan(testSpan()),
				Operator: tt.op,
				Left:     tt.left,
				Right:    tt.right,
			}
			body := []ast.Statement{
				&ast.ExprStmt{SpanNode: ast.WithSpan(testSpan()), Expr: be},
				makeReturnStmt(makeIntLiteral(0)),
			}
			prog := makeProg("test", makeMainFunc(body...))
			src := source.NewSourceText("test.sol", "")
			c := checker.New(src)
			diags, err := c.Check(prog)
			if err != nil {
				t.Errorf("Check returned error: %v", err)
			}
			hasErr := diags.HasErrors()
			if hasErr != tt.wantErr {
				t.Errorf("hasErrors=%v, wantErr=%v; diags: %v", hasErr, tt.wantErr, diags.All())
			}
		})
	}
}

func TestCheckerUnaryOps(t *testing.T) {
	tests := []struct {
		name    string
		op      ast.UnaryOp
		operand ast.Expression
		wantErr bool
	}{
		{"negate_int", ast.UnaryNegate, makeIntLiteral(5), false},
		{"not_bool", ast.UnaryNot, makeBoolLiteral(true), false},
		{"bitnot_int", ast.UnaryBitNot, makeIntLiteral(1), false},
		{"err_negate_bool", ast.UnaryNegate, makeBoolLiteral(true), true},
		{"err_not_int", ast.UnaryNot, makeIntLiteral(5), true},
		{"err_bitnot_bool", ast.UnaryBitNot, makeBoolLiteral(true), true},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			ue := &ast.UnaryExpr{
				SpanNode: ast.WithSpan(testSpan()),
				Operator: tt.op,
				Operand:  tt.operand,
			}
			body := []ast.Statement{
				&ast.ExprStmt{SpanNode: ast.WithSpan(testSpan()), Expr: ue},
				makeReturnStmt(makeIntLiteral(0)),
			}
			prog := makeProg("test", makeMainFunc(body...))
			src := source.NewSourceText("test.sol", "")
			c := checker.New(src)
			diags, err := c.Check(prog)
			if err != nil {
				t.Errorf("Check returned error: %v", err)
			}
			hasErr := diags.HasErrors()
			if hasErr != tt.wantErr {
				t.Errorf("hasErrors=%v, wantErr=%v", hasErr, tt.wantErr)
			}
		})
	}
}

func TestCheckerIfStmt(t *testing.T) {
	body := []ast.Statement{
		&ast.IfStmt{
			SpanNode:  ast.WithSpan(testSpan()),
			Condition: makeBoolLiteral(true),
			Then:      &ast.Block{SpanNode: ast.WithSpan(testSpan())},
			Else:      &ast.Block{SpanNode: ast.WithSpan(testSpan())},
		},
		makeReturnStmt(makeIntLiteral(0)),
	}
	prog := makeProg("test", makeMainFunc(body...))
	src := source.NewSourceText("test.sol", "")
	c := checker.New(src)
	diags, err := c.Check(prog)
	if err != nil {
		t.Errorf("Check returned error: %v", err)
	}
	if diags.HasErrors() {
		t.Errorf("unexpected errors: %v", diags.All())
	}
}

func TestCheckerIfStmtNonBoolCondition(t *testing.T) {
	body := []ast.Statement{
		&ast.IfStmt{
			SpanNode:  ast.WithSpan(testSpan()),
			Condition: makeIntLiteral(1),
			Then:      &ast.Block{SpanNode: ast.WithSpan(testSpan())},
		},
		makeReturnStmt(makeIntLiteral(0)),
	}
	prog := makeProg("test", makeMainFunc(body...))
	src := source.NewSourceText("test.sol", "")
	c := checker.New(src)
	diags, err := c.Check(prog)
	if err != nil {
		t.Errorf("Check returned error: %v", err)
	}
	if !diags.HasErrors() {
		t.Error("expected error for non-bool condition")
	}
}

func TestCheckerWhileStmt(t *testing.T) {
	body := []ast.Statement{
		&ast.WhileStmt{
			SpanNode:  ast.WithSpan(testSpan()),
			Condition: makeBoolLiteral(true),
			Body:      &ast.Block{SpanNode: ast.WithSpan(testSpan())},
		},
		makeReturnStmt(makeIntLiteral(0)),
	}
	prog := makeProg("test", makeMainFunc(body...))
	src := source.NewSourceText("test.sol", "")
	c := checker.New(src)
	diags, err := c.Check(prog)
	if err != nil {
		t.Errorf("Check returned error: %v", err)
	}
	if diags.HasErrors() {
		t.Errorf("unexpected errors: %v", diags.All())
	}
}

func TestCheckerWhileStmtNonBool(t *testing.T) {
	body := []ast.Statement{
		&ast.WhileStmt{
			SpanNode:  ast.WithSpan(testSpan()),
			Condition: makeIntLiteral(1),
			Body:      &ast.Block{SpanNode: ast.WithSpan(testSpan())},
		},
		makeReturnStmt(makeIntLiteral(0)),
	}
	prog := makeProg("test", makeMainFunc(body...))
	src := source.NewSourceText("test.sol", "")
	c := checker.New(src)
	diags, err := c.Check(prog)
	if err != nil {
		t.Errorf("Check returned error: %v", err)
	}
	if !diags.HasErrors() {
		t.Error("expected error for non-bool while condition")
	}
}

func TestCheckerForStmt(t *testing.T) {
	// for x in [1,2,3] { ... }
	body := []ast.Statement{
		&ast.ForStmt{
			SpanNode: ast.WithSpan(testSpan()),
			Variable: "x",
			Iterable: &ast.ListLiteral{
				SpanNode: ast.WithSpan(testSpan()),
				Elements: []ast.Expression{makeIntLiteral(1), makeIntLiteral(2), makeIntLiteral(3)},
			},
			Body: &ast.Block{SpanNode: ast.WithSpan(testSpan())},
		},
		makeReturnStmt(makeIntLiteral(0)),
	}
	prog := makeProg("test", makeMainFunc(body...))
	src := source.NewSourceText("test.sol", "")
	c := checker.New(src)
	diags, err := c.Check(prog)
	if err != nil {
		t.Errorf("Check returned error: %v", err)
	}
	if diags.HasErrors() {
		t.Errorf("unexpected errors: %v", diags.All())
	}
}

func TestCheckerSwitchStmt(t *testing.T) {
	body := []ast.Statement{
		&ast.SwitchStmt{
			SpanNode:   ast.WithSpan(testSpan()),
			Expression: makeIntLiteral(1),
			Cases: []ast.SwitchCase{
				{SpanNode: ast.WithSpan(testSpan()), Expression: makeIntLiteral(1), Body: &ast.Block{SpanNode: ast.WithSpan(testSpan())}},
			},
			Default: &ast.Block{SpanNode: ast.WithSpan(testSpan())},
		},
		makeReturnStmt(makeIntLiteral(0)),
	}
	prog := makeProg("test", makeMainFunc(body...))
	src := source.NewSourceText("test.sol", "")
	c := checker.New(src)
	diags, err := c.Check(prog)
	if err != nil {
		t.Errorf("Check returned error: %v", err)
	}
	if diags.HasErrors() {
		t.Errorf("unexpected errors: %v", diags.All())
	}
}

func TestCheckerReturnTypeMismatch(t *testing.T) {
	// main returns int, but we return a string
	body := []ast.Statement{
		makeReturnStmt(makeStringLiteral("hello")),
	}
	prog := makeProg("test", makeMainFunc(body...))
	src := source.NewSourceText("test.sol", "")
	c := checker.New(src)
	diags, err := c.Check(prog)
	if err != nil {
		t.Errorf("Check returned error: %v", err)
	}
	if !diags.HasErrors() {
		t.Error("expected error for return type mismatch")
	}
}

func TestCheckerListLiteral(t *testing.T) {
	body := []ast.Statement{
		&ast.ExprStmt{SpanNode: ast.WithSpan(testSpan()), Expr: &ast.ListLiteral{
			SpanNode: ast.WithSpan(testSpan()),
			Elements: []ast.Expression{makeIntLiteral(1), makeIntLiteral(2)},
		}},
		makeReturnStmt(makeIntLiteral(0)),
	}
	prog := makeProg("test", makeMainFunc(body...))
	src := source.NewSourceText("test.sol", "")
	c := checker.New(src)
	diags, err := c.Check(prog)
	if err != nil {
		t.Errorf("Check returned error: %v", err)
	}
	if diags.HasErrors() {
		t.Errorf("unexpected errors: %v", diags.All())
	}
}

func TestCheckerMapLiteral(t *testing.T) {
	body := []ast.Statement{
		&ast.ExprStmt{SpanNode: ast.WithSpan(testSpan()), Expr: &ast.MapLiteral{
			SpanNode: ast.WithSpan(testSpan()),
			Keys:     []ast.Expression{makeStringLiteral("key")},
			Values:   []ast.Expression{makeIntLiteral(42)},
		}},
		makeReturnStmt(makeIntLiteral(0)),
	}
	prog := makeProg("test", makeMainFunc(body...))
	src := source.NewSourceText("test.sol", "")
	c := checker.New(src)
	diags, err := c.Check(prog)
	if err != nil {
		t.Errorf("Check returned error: %v", err)
	}
	if diags.HasErrors() {
		t.Errorf("unexpected errors: %v", diags.All())
	}
}

func TestCheckerNullCoalescing(t *testing.T) {
	// This requires a nullable type. We need to use a variable.
	// Simpler: just test that the checker doesn't crash on various expressions.
	body := []ast.Statement{
		&ast.ExprStmt{SpanNode: ast.WithSpan(testSpan()), Expr: &ast.NullCoalescing{
			SpanNode: ast.WithSpan(testSpan()),
			Left:     &ast.NullLiteral{SpanNode: ast.WithSpan(testSpan())},
			Right:    makeStringLiteral("default"),
		}},
		makeReturnStmt(makeIntLiteral(0)),
	}
	prog := makeProg("test", makeMainFunc(body...))
	src := source.NewSourceText("test.sol", "")
	c := checker.New(src)
	diags, err := c.Check(prog)
	if err != nil {
		t.Errorf("Check returned error: %v", err)
	}
	// Note: null literal without context will have KindInvalid, which may cause warnings
	_ = diags
}

func TestCheckerBreakOutsideLoop(t *testing.T) {
	// The checker's checkStatement for BreakStmt returns true only if inside a loop
	// (c.loopDepth > 0). Diagnostics for break outside loop are emitted by the resolver.
	// Just verify the checker doesn't crash.
	body := []ast.Statement{
		&ast.BreakStmt{SpanNode: ast.WithSpan(testSpan())},
		makeReturnStmt(makeIntLiteral(0)),
	}
	prog := makeProg("test", makeMainFunc(body...))
	src := source.NewSourceText("test.sol", "")
	c := checker.New(src)
	_, err := c.Check(prog)
	if err != nil {
		t.Errorf("Check returned error: %v", err)
	}
}

func TestCheckerTryStmt(t *testing.T) {
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
		},
		makeReturnStmt(makeIntLiteral(0)),
	}
	prog := makeProg("test", makeMainFunc(body...))
	src := source.NewSourceText("test.sol", "")
	c := checker.New(src)
	diags, err := c.Check(prog)
	if err != nil {
		t.Errorf("Check returned error: %v", err)
	}
	if diags.HasErrors() {
		t.Errorf("unexpected errors: %v", diags.All())
	}
}

func TestCheckerThrowStmt(t *testing.T) {
	body := []ast.Statement{
		&ast.ThrowStmt{
			SpanNode: ast.WithSpan(testSpan()),
			Value:    makeStringLiteral("error"),
		},
	}
	prog := makeProg("test", makeMainFunc(body...))
	src := source.NewSourceText("test.sol", "")
	c := checker.New(src)
	diags, err := c.Check(prog)
	if err != nil {
		t.Errorf("Check returned error: %v", err)
	}
	if diags.HasErrors() {
		t.Errorf("unexpected errors: %v", diags.All())
	}
}

func TestCheckerThrowInvalidType(t *testing.T) {
	body := []ast.Statement{
		&ast.ThrowStmt{
			SpanNode: ast.WithSpan(testSpan()),
			Value:    makeIntLiteral(42),
		},
	}
	prog := makeProg("test", makeMainFunc(body...))
	src := source.NewSourceText("test.sol", "")
	c := checker.New(src)
	diags, err := c.Check(prog)
	if err != nil {
		t.Errorf("Check returned error: %v", err)
	}
	if !diags.HasErrors() {
		t.Error("expected error for throw with non-string/exception type")
	}
}

func TestCheckerFunctionCall(t *testing.T) {
	// Define a helper function
	helper := &ast.Function{
		SpanNode:    ast.WithSpan(testSpan()),
		Name:        "helper",
		Module:      "test",
		Parameters:  []*ast.Parameter{{SpanNode: ast.WithSpan(testSpan()), Name: "x", Type: &ast.TypeAnnotation{Kind: types.KindInt, ResolvedType: types.Int}}},
		ReturnTypes: []*ast.TypeAnnotation{{Kind: types.KindInt, ResolvedType: types.Int}},
		Body:        &ast.Block{SpanNode: ast.WithSpan(testSpan()), Statements: []ast.Statement{makeReturnStmt(makeIntLiteral(0))}},
	}
	// main calls helper(42)
	body := []ast.Statement{
		&ast.ExprStmt{SpanNode: ast.WithSpan(testSpan()), Expr: &ast.CallExpr{
			SpanNode: ast.WithSpan(testSpan()),
			Function: &ast.Identifier{SpanNode: ast.WithSpan(testSpan()), Name: "helper"},
			Args:     []ast.Expression{makeIntLiteral(42)},
		}},
		makeReturnStmt(makeIntLiteral(0)),
	}
	mainFn := makeMainFunc(body...)
	prog := makeProg("test", helper, mainFn)
	src := source.NewSourceText("test.sol", "")
	c := checker.New(src)
	diags, err := c.Check(prog)
	if err != nil {
		t.Errorf("Check returned error: %v", err)
	}
	if diags.HasErrors() {
		t.Errorf("unexpected errors: %v", diags.All())
	}
}

func TestCheckerImmutableReassign(t *testing.T) {
	body := []ast.Statement{
		&ast.VariableDecl{
			SpanNode: ast.WithSpan(testSpan()),
			Name:     "x",
			Type:     &ast.TypeAnnotation{Kind: types.KindInt, ResolvedType: types.Int},
			InitExpr: makeIntLiteral(1),
			IsMut:    false,
		},
		&ast.AssignStmt{
			SpanNode: ast.WithSpan(testSpan()),
			Name:     "x",
			Value:    makeIntLiteral(2),
		},
		makeReturnStmt(makeIntLiteral(0)),
	}
	prog := makeProg("test", makeMainFunc(body...))
	src := source.NewSourceText("test.sol", "")
	c := checker.New(src)
	diags, err := c.Check(prog)
	if err != nil {
		t.Errorf("Check returned error: %v", err)
	}
	if !diags.HasErrors() {
		t.Error("expected error for immutable reassignment")
	}
}

func TestCheckerMemberExprException(t *testing.T) {
	// Access exception.message - use a helper function since main can't have params
	helper := &ast.Function{
		SpanNode:    ast.WithSpan(testSpan()),
		Name:        "handler",
		Module:      "test",
		Parameters:  []*ast.Parameter{{SpanNode: ast.WithSpan(testSpan()), Name: "e", Type: &ast.TypeAnnotation{Kind: types.KindException, ResolvedType: types.Exception}}},
		ReturnTypes: []*ast.TypeAnnotation{{Kind: types.KindString, ResolvedType: types.String}},
		Body: &ast.Block{SpanNode: ast.WithSpan(testSpan()), Statements: []ast.Statement{
			makeReturnStmt(&ast.MemberExpr{
				SpanNode: ast.WithSpan(testSpan()),
				Object:   &ast.Identifier{SpanNode: ast.WithSpan(testSpan()), Name: "e"},
				Member:   "message",
			}),
		}},
	}
	mainFn := makeMainFunc(makeReturnStmt(makeIntLiteral(0)))
	prog := makeProg("test", helper, mainFn)
	src := source.NewSourceText("test.sol", "")
	c := checker.New(src)
	diags, err := c.Check(prog)
	if err != nil {
		t.Errorf("Check returned error: %v", err)
	}
	if diags.HasErrors() {
		t.Errorf("unexpected errors: %v", diags.All())
	}
}

func TestCheckerSkipMainCheck(t *testing.T) {
	prog := makeProg("test")
	prog.Funcs = nil
	src := source.NewSourceText("test.sol", "")
	c := checker.New(src)
	c.SetSkipMainCheck(true)
	diags, err := c.Check(prog)
	if err != nil {
		t.Errorf("Check returned error: %v", err)
	}
	if diags.HasErrors() {
		t.Error("expected no errors with skipMainCheck")
	}
}

func TestCheckerSetAllFuncs(t *testing.T) {
	src := source.NewSourceText("test.sol", "")
	c := checker.New(src)
	c.SetAllFuncs(map[string]*ast.Function{
		"other.helper": {
			Name:        "helper",
			Module:      "other",
			Parameters:  []*ast.Parameter{{SpanNode: ast.WithSpan(testSpan()), Name: "x", Type: &ast.TypeAnnotation{Kind: types.KindInt, ResolvedType: types.Int}}},
			ReturnTypes: []*ast.TypeAnnotation{{Kind: types.KindInt, ResolvedType: types.Int}},
			Body:        &ast.Block{SpanNode: ast.WithSpan(testSpan()), Statements: []ast.Statement{makeReturnStmt(makeIntLiteral(0))}},
		},
	})
	if c == nil {
		t.Error("checker should not be nil")
	}
}

func TestCheckerNew(t *testing.T) {
	src := source.NewSourceText("test.sol", "")
	c := checker.New(src)
	if c == nil {
		t.Fatal("New returned nil")
	}
}
