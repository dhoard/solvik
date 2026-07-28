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

package symbol_test

import (
	"testing"

	"github.com/dhoard/solvik-language/internal/symbol"
	"github.com/dhoard/solvik-language/internal/types"
)

func TestNewScope(t *testing.T) {
	s := symbol.NewScope(nil, types.Int)
	if s.Parent != nil {
		t.Error("expected nil parent for root scope")
	}
	if s.Depth != 0 {
		t.Errorf("expected depth 0, got %d", s.Depth)
	}
	if s.FuncType != types.Int {
		t.Error("FuncType mismatch")
	}
}

func TestNewScopeNested(t *testing.T) {
	parent := symbol.NewScope(nil, nil)
	child := symbol.NewScope(parent, types.String)
	if child.Parent != parent {
		t.Error("expected parent to be set")
	}
	if child.Depth != 1 {
		t.Errorf("expected depth 1, got %d", child.Depth)
	}
	if child.FuncType != types.String {
		t.Error("FuncType mismatch")
	}
}

func TestDeclare(t *testing.T) {
	s := symbol.NewScope(nil, nil)
	err := s.Declare(&symbol.Symbol{Name: "x", Kind: symbol.KindVariable, Type: types.Int})
	if err != nil {
		t.Fatalf("Declare failed: %v", err)
	}
	// Duplicate should fail
	err = s.Declare(&symbol.Symbol{Name: "x", Kind: symbol.KindVariable, Type: types.Int})
	if err == nil {
		t.Error("expected error for duplicate declaration")
	}
}

func TestResolve(t *testing.T) {
	s := symbol.NewScope(nil, nil)
	s.Declare(&symbol.Symbol{Name: "x", Kind: symbol.KindVariable, Type: types.Int})
	sym := s.Resolve("x")
	if sym == nil {
		t.Fatal("expected to resolve 'x'")
	}
	if sym.Name != "x" || sym.Type != types.Int {
		t.Error("resolved symbol mismatch")
	}
	// Undefined
	if s.Resolve("undefined") != nil {
		t.Error("expected nil for undefined symbol")
	}
}

func TestResolveInCurrent(t *testing.T) {
	parent := symbol.NewScope(nil, nil)
	child := symbol.NewScope(parent, nil)
	parent.Declare(&symbol.Symbol{Name: "a", Kind: symbol.KindVariable, Type: types.Int})
	child.Declare(&symbol.Symbol{Name: "b", Kind: symbol.KindVariable, Type: types.String})

	// ResolveInCurrent should only find in current scope
	if child.ResolveInCurrent("a") != nil {
		t.Error("ResolveInCurrent should not find parent symbols")
	}
	if child.ResolveInCurrent("b") == nil {
		t.Error("ResolveInCurrent should find local symbols")
	}
}

func TestResolveParentScope(t *testing.T) {
	parent := symbol.NewScope(nil, nil)
	child := symbol.NewScope(parent, nil)
	parent.Declare(&symbol.Symbol{Name: "x", Kind: symbol.KindVariable, Type: types.Int})
	// Resolve should traverse up
	sym := child.Resolve("x")
	if sym == nil {
		t.Fatal("expected to resolve 'x' from child scope")
	}
	if sym.Type != types.Int {
		t.Error("resolved symbol type mismatch")
	}
}

func TestResolveShadowing(t *testing.T) {
	parent := symbol.NewScope(nil, nil)
	child := symbol.NewScope(parent, nil)
	parent.Declare(&symbol.Symbol{Name: "x", Kind: symbol.KindVariable, Type: types.Int})
	child.Declare(&symbol.Symbol{Name: "x", Kind: symbol.KindVariable, Type: types.String})

	// Child should resolve to child's symbol (shadowing)
	sym := child.Resolve("x")
	if sym == nil {
		t.Fatal("expected to resolve 'x'")
	}
	if sym.Type != types.String {
		t.Error("expected shadowed symbol to have String type")
	}
}

func TestDeclareAllKinds(t *testing.T) {
	s := symbol.NewScope(nil, nil)
	tests := []struct {
		kind symbol.Kind
		name string
	}{
		{symbol.KindVariable, "a"},
		{symbol.KindFunction, "b"},
		{symbol.KindModule, "c"},
		{symbol.KindNativeFunction, "d"},
	}
	for _, tt := range tests {
		err := s.Declare(&symbol.Symbol{Name: tt.name, Kind: tt.kind, Type: types.Int})
		if err != nil {
			t.Errorf("Declare kind %v failed: %v", tt.kind, err)
		}
	}
}

func TestSymbolFields(t *testing.T) {
	sym := &symbol.Symbol{
		Name:       "foo",
		Kind:       symbol.KindFunction,
		Type:       types.FunctionType([]*types.Type{types.Int}, types.Void),
		Slot:       2,
		FuncIndex:  5,
		Parameter:  true,
		ModuleName: "mymod",
		Defined:    true,
		Mut:        true,
	}
	if sym.Name != "foo" || sym.Kind != symbol.KindFunction || sym.Slot != 2 || sym.FuncIndex != 5 {
		t.Error("Symbol fields mismatch")
	}
	if !sym.Parameter || !sym.Defined || !sym.Mut {
		t.Error("Symbol boolean fields mismatch")
	}
	if sym.ModuleName != "mymod" {
		t.Error("ModuleName mismatch")
	}
}

func TestAllSymbols(t *testing.T) {
	parent := symbol.NewScope(nil, nil)
	parent.Declare(&symbol.Symbol{Name: "a", Kind: symbol.KindVariable, Type: types.Int})
	child := symbol.NewScope(parent, nil)
	child.Declare(&symbol.Symbol{Name: "b", Kind: symbol.KindVariable, Type: types.String})

	all := child.AllSymbols()
	if len(all) != 2 {
		t.Errorf("expected 2 visible symbols, got %d", len(all))
	}
	if _, ok := all["a"]; !ok {
		t.Error("expected 'a' in visible symbols")
	}
	if _, ok := all["b"]; !ok {
		t.Error("expected 'b' in visible symbols")
	}
}

func TestAllSymbolsShadowing(t *testing.T) {
	parent := symbol.NewScope(nil, nil)
	parent.Declare(&symbol.Symbol{Name: "x", Kind: symbol.KindVariable, Type: types.Int})
	child := symbol.NewScope(parent, nil)
	child.Declare(&symbol.Symbol{Name: "x", Kind: symbol.KindVariable, Type: types.String})

	all := child.AllSymbols()
	if len(all) != 1 {
		t.Errorf("expected 1 visible symbol (shadowed), got %d", len(all))
	}
	// The child's symbol wins
	if all["x"].Type != types.String {
		t.Error("expected child's String type to shadow parent's Int")
	}
}

func TestScopeDepth(t *testing.T) {
	s1 := symbol.NewScope(nil, nil)
	s2 := symbol.NewScope(s1, nil)
	s3 := symbol.NewScope(s2, nil)

	if s1.Depth != 0 || s2.Depth != 1 || s3.Depth != 2 {
		t.Errorf("depths: want 0,1,2 got %d,%d,%d", s1.Depth, s2.Depth, s3.Depth)
	}
}

func TestResolveNested(t *testing.T) {
	s1 := symbol.NewScope(nil, nil)
	s1.Declare(&symbol.Symbol{Name: "a", Kind: symbol.KindVariable, Type: types.Int})
	s2 := symbol.NewScope(s1, nil)
	s2.Declare(&symbol.Symbol{Name: "b", Kind: symbol.KindVariable, Type: types.String})
	s3 := symbol.NewScope(s2, nil)
	s3.Declare(&symbol.Symbol{Name: "c", Kind: symbol.KindVariable, Type: types.Float})

	if s3.Resolve("a") == nil || s3.Resolve("b") == nil || s3.Resolve("c") == nil {
		t.Error("failed to resolve nested symbols")
	}
	if s1.Resolve("c") != nil {
		t.Error("should not resolve child symbols from parent")
	}
}
