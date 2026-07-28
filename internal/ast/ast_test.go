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

package ast_test

import (
	"testing"

	"github.com/dhoard/solvik-language/internal/ast"
	"github.com/dhoard/solvik-language/internal/source"
	"github.com/dhoard/solvik-language/internal/types"
)

func testSpan() source.Span {
	return source.NewSpan("test.sol", 0, 5, 1, 1, 1, 6)
}

func TestProgram(t *testing.T) {
	p := &ast.Program{
		SpanNode: ast.WithSpan(testSpan()),
		Module:   "test",
	}
	if p.Module != "test" {
		t.Error("Module field mismatch")
	}
	if p.Span() != testSpan() {
		t.Error("Span mismatch")
	}
}

func TestFunction(t *testing.T) {
	fn := &ast.Function{
		SpanNode: ast.WithSpan(testSpan()),
		Name:     "main",
		Module:   "test",
		Parameters: []*ast.Parameter{
			{SpanNode: ast.WithSpan(testSpan()), Name: "x", Type: &ast.TypeAnnotation{Kind: types.KindInt}},
		},
		ReturnTypes: []*ast.TypeAnnotation{{Kind: types.KindInt}},
		Body:        &ast.Block{SpanNode: ast.WithSpan(testSpan())},
	}
	if fn.Name != "main" || fn.Module != "test" {
		t.Error("Function fields mismatch")
	}
	if len(fn.Parameters) != 1 || fn.Parameters[0].Name != "x" {
		t.Error("Parameter mismatch")
	}
	if len(fn.ReturnTypes) != 1 || fn.ReturnTypes[0].Kind != types.KindInt {
		t.Error("ReturnTypes mismatch")
	}
}

func TestVariableDecl(t *testing.T) {
	decl := &ast.VariableDecl{
		SpanNode: ast.WithSpan(testSpan()),
		Name:     "counter",
		Type:     &ast.TypeAnnotation{Kind: types.KindInt},
		InitExpr: &ast.IntLiteral{SpanNode: ast.WithSpan(testSpan()), Value: 42},
		IsMut:    true,
	}
	if decl.Name != "counter" || !decl.IsMut {
		t.Error("VariableDecl fields mismatch")
	}
	if lit, ok := decl.InitExpr.(*ast.IntLiteral); !ok || lit.Value != 42 {
		t.Error("InitExpr mismatch")
	}
}

func TestBlock(t *testing.T) {
	b := &ast.Block{
		SpanNode: ast.WithSpan(testSpan()),
		Statements: []ast.Statement{
			&ast.ExprStmt{SpanNode: ast.WithSpan(testSpan()), Expr: &ast.IntLiteral{SpanNode: ast.WithSpan(testSpan()), Value: 1}},
			&ast.ExprStmt{SpanNode: ast.WithSpan(testSpan()), Expr: &ast.IntLiteral{SpanNode: ast.WithSpan(testSpan()), Value: 2}},
		},
	}
	if len(b.Statements) != 2 {
		t.Errorf("expected 2 statements, got %d", len(b.Statements))
	}
}

func TestIfStmt(t *testing.T) {
	ifStmt := &ast.IfStmt{
		SpanNode:  ast.WithSpan(testSpan()),
		Condition: &ast.BoolLiteral{SpanNode: ast.WithSpan(testSpan()), Value: true},
		Then:      &ast.Block{SpanNode: ast.WithSpan(testSpan())},
		Else:      &ast.Block{SpanNode: ast.WithSpan(testSpan())},
	}
	if ifStmt.Condition == nil || ifStmt.Then == nil || ifStmt.Else == nil {
		t.Error("IfStmt fields should not be nil")
	}
}

func TestWhileStmt(t *testing.T) {
	w := &ast.WhileStmt{
		SpanNode:  ast.WithSpan(testSpan()),
		Condition: &ast.BoolLiteral{SpanNode: ast.WithSpan(testSpan()), Value: true},
		Body:      &ast.Block{SpanNode: ast.WithSpan(testSpan())},
	}
	if w.Condition == nil || w.Body == nil {
		t.Error("WhileStmt fields should not be nil")
	}
}

func TestForStmt(t *testing.T) {
	f := &ast.ForStmt{
		SpanNode:      ast.WithSpan(testSpan()),
		Variable:      "item",
		ValueVariable: "",
		Iterable:      &ast.Identifier{SpanNode: ast.WithSpan(testSpan()), Name: "items"},
		Body:          &ast.Block{SpanNode: ast.WithSpan(testSpan())},
	}
	if f.Variable != "item" || f.ValueVariable != "" {
		t.Error("ForStmt single variable fields mismatch")
	}

	// Map unpacking form
	f2 := &ast.ForStmt{
		SpanNode:      ast.WithSpan(testSpan()),
		Variable:      "key",
		ValueVariable: "value",
		Iterable:      &ast.Identifier{SpanNode: ast.WithSpan(testSpan()), Name: "m"},
		Body:          &ast.Block{SpanNode: ast.WithSpan(testSpan())},
	}
	if f2.Variable != "key" || f2.ValueVariable != "value" {
		t.Error("ForStmt unpacking fields mismatch")
	}
}

func TestSwitchStmt(t *testing.T) {
	sw := &ast.SwitchStmt{
		SpanNode:   ast.WithSpan(testSpan()),
		Expression: &ast.Identifier{SpanNode: ast.WithSpan(testSpan()), Name: "x"},
		Cases: []ast.SwitchCase{
			{
				SpanNode:   ast.WithSpan(testSpan()),
				Expression: &ast.IntLiteral{SpanNode: ast.WithSpan(testSpan()), Value: 1},
				Body:       &ast.Block{SpanNode: ast.WithSpan(testSpan())},
			},
		},
		Default: &ast.Block{SpanNode: ast.WithSpan(testSpan())},
	}
	if len(sw.Cases) != 1 {
		t.Errorf("expected 1 case, got %d", len(sw.Cases))
	}
	if sw.Default == nil {
		t.Error("expected default block")
	}
}

func TestTryStmt(t *testing.T) {
	// try/catch
	try := &ast.TryStmt{
		SpanNode: ast.WithSpan(testSpan()),
		TryBody:  &ast.Block{SpanNode: ast.WithSpan(testSpan())},
		Catch: &ast.CatchClause{
			SpanNode:  ast.WithSpan(testSpan()),
			ParamName: "e",
			ParamType: &ast.TypeAnnotation{Kind: types.KindException},
			Body:      &ast.Block{SpanNode: ast.WithSpan(testSpan())},
		},
	}
	if try.Catch == nil || try.Catch.ParamName != "e" {
		t.Error("CatchClause fields mismatch")
	}

	// try/finally
	try2 := &ast.TryStmt{
		SpanNode: ast.WithSpan(testSpan()),
		TryBody:  &ast.Block{SpanNode: ast.WithSpan(testSpan())},
		Finally:  &ast.Block{SpanNode: ast.WithSpan(testSpan())},
	}
	if try2.Finally == nil {
		t.Error("expected finally block")
	}

	// try/catch/finally
	try3 := &ast.TryStmt{
		SpanNode: ast.WithSpan(testSpan()),
		TryBody:  &ast.Block{SpanNode: ast.WithSpan(testSpan())},
		Catch: &ast.CatchClause{
			SpanNode:  ast.WithSpan(testSpan()),
			ParamName: "e",
			ParamType: &ast.TypeAnnotation{Kind: types.KindException},
			Body:      &ast.Block{SpanNode: ast.WithSpan(testSpan())},
		},
		Finally: &ast.Block{SpanNode: ast.WithSpan(testSpan())},
	}
	if try3.Catch == nil || try3.Finally == nil {
		t.Error("expected catch and finally")
	}
}

func TestThrowStmt(t *testing.T) {
	th := &ast.ThrowStmt{
		SpanNode: ast.WithSpan(testSpan()),
		Value:    &ast.StringLiteral{SpanNode: ast.WithSpan(testSpan()), Value: "error occurred"},
	}
	if th.Value == nil {
		t.Error("ThrowStmt.Value should not be nil")
	}
	if s, ok := th.Value.(*ast.StringLiteral); !ok || s.Value != "error occurred" {
		t.Error("ThrowStmt value mismatch")
	}
}

func TestReturnStmt(t *testing.T) {
	// With value
	r := &ast.ReturnStmt{
		SpanNode: ast.WithSpan(testSpan()),
		Values:   []ast.Expression{&ast.IntLiteral{SpanNode: ast.WithSpan(testSpan()), Value: 0}},
	}
	if len(r.Values) != 1 {
		t.Error("ReturnStmt should have 1 value")
	}

	// Without value (void return)
	r2 := &ast.ReturnStmt{
		SpanNode: ast.WithSpan(testSpan()),
	}
	if len(r2.Values) != 0 {
		t.Error("void return should have no values")
	}
}

func TestBreakContinue(t *testing.T) {
	b := &ast.BreakStmt{SpanNode: ast.WithSpan(testSpan())}
	c := &ast.ContinueStmt{SpanNode: ast.WithSpan(testSpan())}
	if b.Span() != testSpan() || c.Span() != testSpan() {
		t.Error("Span mismatch on break/continue")
	}
}

func TestAssignStmt(t *testing.T) {
	a := &ast.AssignStmt{
		SpanNode: ast.WithSpan(testSpan()),
		Name:     "x",
		Value:    &ast.IntLiteral{SpanNode: ast.WithSpan(testSpan()), Value: 10},
	}
	if a.Name != "x" {
		t.Error("AssignStmt.Name mismatch")
	}
	if lit, ok := a.Value.(*ast.IntLiteral); !ok || lit.Value != 10 {
		t.Error("AssignStmt.Value mismatch")
	}
}

func TestExprStmt(t *testing.T) {
	e := &ast.ExprStmt{
		SpanNode: ast.WithSpan(testSpan()),
		Expr:     &ast.CallExpr{SpanNode: ast.WithSpan(testSpan())},
	}
	if e.Expr == nil {
		t.Error("ExprStmt.Expr should not be nil")
	}
}

// --- Literal expressions ---

func TestIntLiteral(t *testing.T) {
	l := &ast.IntLiteral{SpanNode: ast.WithSpan(testSpan()), Value: 42}
	if l.Value != 42 {
		t.Errorf("Value = %d, want 42", l.Value)
	}
}

func TestLongLiteral(t *testing.T) {
	l := &ast.LongLiteral{SpanNode: ast.WithSpan(testSpan()), Value: 9999999999}
	if l.Value != 9999999999 {
		t.Errorf("Value = %d, want 9999999999", l.Value)
	}
}

func TestFloatLiteral(t *testing.T) {
	l := &ast.FloatLiteral{SpanNode: ast.WithSpan(testSpan()), Value: 3.14}
	if l.Value != 3.14 {
		t.Errorf("Value = %g, want 3.14", l.Value)
	}
}

func TestDoubleLiteral(t *testing.T) {
	l := &ast.DoubleLiteral{SpanNode: ast.WithSpan(testSpan()), Value: 2.718281828}
	if l.Value != 2.718281828 {
		t.Errorf("Value = %g, want 2.718281828", l.Value)
	}
}

func TestBoolLiteral(t *testing.T) {
	l := &ast.BoolLiteral{SpanNode: ast.WithSpan(testSpan()), Value: true}
	if l.Value != true {
		t.Error("Value should be true")
	}
}

func TestCharLiteral(t *testing.T) {
	l := &ast.CharLiteral{SpanNode: ast.WithSpan(testSpan()), Value: 'A'}
	if l.Value != 'A' {
		t.Errorf("Value = %c, want A", l.Value)
	}
}

func TestStringLiteral(t *testing.T) {
	l := &ast.StringLiteral{SpanNode: ast.WithSpan(testSpan()), Value: "hello"}
	if l.Value != "hello" {
		t.Errorf("Value = %q, want hello", l.Value)
	}
}

func TestByteLiteral(t *testing.T) {
	l := &ast.ByteLiteral{SpanNode: ast.WithSpan(testSpan()), Value: 255}
	if l.Value != 255 {
		t.Errorf("Value = %d, want 255", l.Value)
	}
}

func TestNullLiteral(t *testing.T) {
	l := &ast.NullLiteral{SpanNode: ast.WithSpan(testSpan())}
	if l == nil {
		t.Error("NullLiteral should not be nil")
	}
}

func TestIdentifier(t *testing.T) {
	id := &ast.Identifier{SpanNode: ast.WithSpan(testSpan()), Name: "myVar"}
	if id.Name != "myVar" {
		t.Errorf("Name = %q, want myVar", id.Name)
	}
}

// --- Complex expressions ---

func TestUnaryExpr(t *testing.T) {
	u := &ast.UnaryExpr{
		SpanNode: ast.WithSpan(testSpan()),
		Operator: ast.UnaryNegate,
		Operand:  &ast.IntLiteral{SpanNode: ast.WithSpan(testSpan()), Value: 5},
	}
	if u.Operator != ast.UnaryNegate {
		t.Error("Operator should be UnaryNegate")
	}
}

func TestBinaryExpr(t *testing.T) {
	b := &ast.BinaryExpr{
		SpanNode: ast.WithSpan(testSpan()),
		Operator: ast.BinAdd,
		Left:     &ast.IntLiteral{SpanNode: ast.WithSpan(testSpan()), Value: 1},
		Right:    &ast.IntLiteral{SpanNode: ast.WithSpan(testSpan()), Value: 2},
	}
	if b.Operator != ast.BinAdd {
		t.Error("Operator should be BinAdd")
	}
}

func TestCallExpr(t *testing.T) {
	c := &ast.CallExpr{
		SpanNode: ast.WithSpan(testSpan()),
		Function: &ast.Identifier{SpanNode: ast.WithSpan(testSpan()), Name: "foo"},
		Args: []ast.Expression{
			&ast.IntLiteral{SpanNode: ast.WithSpan(testSpan()), Value: 1},
			&ast.StringLiteral{SpanNode: ast.WithSpan(testSpan()), Value: "arg"},
		},
	}
	if len(c.Args) != 2 {
		t.Errorf("expected 2 args, got %d", len(c.Args))
	}
}

func TestIndexExpr(t *testing.T) {
	idx := &ast.IndexExpr{
		SpanNode: ast.WithSpan(testSpan()),
		Target:   &ast.Identifier{SpanNode: ast.WithSpan(testSpan()), Name: "arr"},
		Index:    &ast.IntLiteral{SpanNode: ast.WithSpan(testSpan()), Value: 0},
	}
	if idx.Index == nil {
		t.Error("IndexExpr.Index should not be nil")
	}
}

func TestListLiteral(t *testing.T) {
	l := &ast.ListLiteral{
		SpanNode: ast.WithSpan(testSpan()),
		Elements: []ast.Expression{
			&ast.IntLiteral{SpanNode: ast.WithSpan(testSpan()), Value: 1},
			&ast.IntLiteral{SpanNode: ast.WithSpan(testSpan()), Value: 2},
		},
	}
	if len(l.Elements) != 2 {
		t.Errorf("expected 2 elements, got %d", len(l.Elements))
	}
}

func TestMapLiteral(t *testing.T) {
	m := &ast.MapLiteral{
		SpanNode: ast.WithSpan(testSpan()),
		Keys: []ast.Expression{
			&ast.StringLiteral{SpanNode: ast.WithSpan(testSpan()), Value: "key1"},
		},
		Values: []ast.Expression{
			&ast.IntLiteral{SpanNode: ast.WithSpan(testSpan()), Value: 42},
		},
	}
	if len(m.Keys) != 1 || len(m.Values) != 1 {
		t.Errorf("expected 1 key and 1 value, got %d keys and %d values", len(m.Keys), len(m.Values))
	}
}

func TestMemberExpr(t *testing.T) {
	m := &ast.MemberExpr{
		SpanNode: ast.WithSpan(testSpan()),
		Object:   &ast.Identifier{SpanNode: ast.WithSpan(testSpan()), Name: "module"},
		Member:   "function",
	}
	if m.Member != "function" {
		t.Errorf("Member = %q, want function", m.Member)
	}
}

func TestNullCoalescing(t *testing.T) {
	nc := &ast.NullCoalescing{
		SpanNode: ast.WithSpan(testSpan()),
		Left:     &ast.Identifier{SpanNode: ast.WithSpan(testSpan()), Name: "nullable"},
		Right:    &ast.StringLiteral{SpanNode: ast.WithSpan(testSpan()), Value: "default"},
	}
	if nc.Left == nil || nc.Right == nil {
		t.Error("NullCoalescing fields should not be nil")
	}
}

func TestTypeAnnotation(t *testing.T) {
	// Simple type
	ta := &ast.TypeAnnotation{
		SpanNode: ast.WithSpan(testSpan()),
		Kind:     types.KindInt,
	}
	if ta.Kind != types.KindInt {
		t.Error("TypeAnnotation Kind mismatch")
	}

	// List type
	ta2 := &ast.TypeAnnotation{
		SpanNode: ast.WithSpan(testSpan()),
		Kind:     types.KindList,
		Element:  &ast.TypeAnnotation{Kind: types.KindString},
	}
	if ta2.Element.Kind != types.KindString {
		t.Error("List element type mismatch")
	}

	// Map type
	ta3 := &ast.TypeAnnotation{
		SpanNode:  ast.WithSpan(testSpan()),
		Kind:      types.KindMap,
		KeyType:   &ast.TypeAnnotation{Kind: types.KindString},
		ValueType: &ast.TypeAnnotation{Kind: types.KindInt},
	}
	if ta3.KeyType.Kind != types.KindString || ta3.ValueType.Kind != types.KindInt {
		t.Error("Map key/value types mismatch")
	}

	// Nullable type
	ta4 := &ast.TypeAnnotation{
		SpanNode: ast.WithSpan(testSpan()),
		Kind:     types.KindString,
		Nullable: true,
	}
	if !ta4.Nullable {
		t.Error("expected nullable")
	}

	// With resolved type
	ta5 := &ast.TypeAnnotation{
		SpanNode:     ast.WithSpan(testSpan()),
		Kind:         types.KindInt,
		ResolvedType: types.Int,
	}
	if ta5.ResolvedType != types.Int {
		t.Error("ResolvedType mismatch")
	}
}

// --- Operator string representations ---

func TestUnaryOpString(t *testing.T) {
	tests := []struct {
		op   ast.UnaryOp
		want string
	}{
		{ast.UnaryNegate, "-"},
		{ast.UnaryNot, "!"},
		{ast.UnaryBitNot, "~"},
		{ast.UnaryOp(99), "?"},
	}
	for _, tt := range tests {
		if got := tt.op.String(); got != tt.want {
			t.Errorf("UnaryOp(%d).String() = %q, want %q", tt.op, got, tt.want)
		}
	}
}

func TestBinOpString(t *testing.T) {
	tests := []struct {
		op   ast.BinOp
		want string
	}{
		{ast.BinAssign, "="},
		{ast.BinAdd, "+"},
		{ast.BinSub, "-"},
		{ast.BinMul, "*"},
		{ast.BinDiv, "/"},
		{ast.BinMod, "%"},
		{ast.BinEq, "=="},
		{ast.BinNe, "!="},
		{ast.BinLt, "<"},
		{ast.BinLe, "<="},
		{ast.BinGt, ">"},
		{ast.BinGe, ">="},
		{ast.BinAnd, "&&"},
		{ast.BinOr, "||"},
		{ast.BinBitAnd, "&"},
		{ast.BinBitOr, "|"},
		{ast.BinBitXor, "^"},
		{ast.BinShiftLeft, "<<"},
		{ast.BinShiftRight, ">>"},
		{ast.BinStrConcat, ".."},
		{ast.BinOp(99), "?"},
	}
	for _, tt := range tests {
		if got := tt.op.String(); got != tt.want {
			t.Errorf("BinOp(%d).String() = %q, want %q", tt.op, got, tt.want)
		}
	}
}

// --- SpanNode ---

func TestSpanNodeWithSpan(t *testing.T) {
	sn := ast.WithSpan(testSpan())
	if sn.Span() != testSpan() {
		t.Error("WithSpan: Span mismatch")
	}
}

func TestSpanNodeSetSpan(t *testing.T) {
	var sn ast.SpanNode
	sn.SetSpan(testSpan())
	if sn.Span() != testSpan() {
		t.Error("SetSpan: Span mismatch")
	}
}

func TestSpanNodeExprType(t *testing.T) {
	var sn ast.SpanNode
	sn.SetExprType(types.Int)
	if sn.GetExprType() != types.Int {
		t.Error("ExprType mismatch")
	}
	// Initially nil
	var sn2 ast.SpanNode
	if sn2.GetExprType() != nil {
		t.Error("ExprType should be nil initially")
	}
}

// --- Import and UseDecl ---

func TestImport(t *testing.T) {
	imp := &ast.Import{
		SpanNode: ast.WithSpan(testSpan()),
		Module:   "fmt",
		Alias:    "f",
	}
	if imp.Module != "fmt" || imp.Alias != "f" {
		t.Error("Import fields mismatch")
	}
}

func TestUseDecl(t *testing.T) {
	use := &ast.UseDecl{
		SpanNode: ast.WithSpan(testSpan()),
		Path:     "utils.string",
		Checksum: "abc123",
	}
	if use.Path != "utils.string" || use.Checksum != "abc123" {
		t.Error("UseDecl fields mismatch")
	}
}

func TestParameter(t *testing.T) {
	p := &ast.Parameter{
		SpanNode: ast.WithSpan(testSpan()),
		Name:     "param1",
		Type:     &ast.TypeAnnotation{Kind: types.KindString},
	}
	if p.Name != "param1" || p.Type.Kind != types.KindString {
		t.Error("Parameter fields mismatch")
	}
}

// --- Compile-time interface checks ---

func TestStmtNodeMarker(t *testing.T) {
	// Verify all statement types implement stmtNode via type assertion
	stmts := []ast.Statement{
		&ast.Block{},
		&ast.AssignStmt{},
		&ast.IfStmt{},
		&ast.WhileStmt{},
		&ast.ForStmt{},
		&ast.BreakStmt{},
		&ast.ContinueStmt{},
		&ast.ReturnStmt{},
		&ast.ExprStmt{},
		&ast.SwitchStmt{},
		&ast.TryStmt{},
		&ast.ThrowStmt{},
	}
	for i, stmt := range stmts {
		if stmt == nil {
			t.Errorf("statement %d should not be nil", i)
		}
	}
}

func TestExprNodeMarker(t *testing.T) {
	exprs := []ast.Expression{
		&ast.IntLiteral{},
		&ast.LongLiteral{},
		&ast.FloatLiteral{},
		&ast.DoubleLiteral{},
		&ast.BoolLiteral{},
		&ast.CharLiteral{},
		&ast.StringLiteral{},
		&ast.ByteLiteral{},
		&ast.NullLiteral{},
		&ast.Identifier{},
		&ast.UnaryExpr{},
		&ast.BinaryExpr{},
		&ast.CallExpr{},
		&ast.IndexExpr{},
		&ast.ListLiteral{},
		&ast.MapLiteral{},
		&ast.MemberExpr{},
		&ast.NullCoalescing{},
	}
	for i, expr := range exprs {
		if expr == nil {
			t.Errorf("expression %d should not be nil", i)
		}
	}
}

func TestVariableDeclStmt(t *testing.T) {
	vd := &ast.VariableDecl{
		SpanNode: ast.WithSpan(testSpan()),
		Name:     "x",
		IsMut:    false,
	}
	// Verify it implements Statement
	var _ ast.Statement = vd
	if vd.Name != "x" || vd.IsMut {
		t.Error("VariableDecl fields mismatch")
	}
}
