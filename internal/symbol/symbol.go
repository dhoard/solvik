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

// Package symbol provides symbol table management for name resolution.
package symbol

import (
	"fmt"

	"github.com/dhoard/solvik-language/internal/types"
)

// Kind classifies a symbol.
type Kind int

const (
	KindVariable Kind = iota
	KindFunction
	KindModule
	KindNativeFunction
	KindStruct // struct type symbol
	KindTrait  // trait type symbol
)

// Symbol represents a resolved name binding.
type Symbol struct {
	Name       string
	Kind       Kind
	Type       *types.Type
	Slot       int    // local/parameter slot number
	FuncIndex  int    // function index for function symbols
	Parameter  bool   // true if this is a parameter
	ModuleName string // module name for module symbols
	Defined    bool   // true if definitely assigned
	Mut        bool   // true if declared with 'mut' keyword (mutable)
	// Struct field tracking
	IsStructField bool // true if this symbol represents a struct field
	FieldIndex    int  // index of the field in the struct
	FieldOfSlot   int  // slot of the struct value this field belongs to
}

// Scope represents a lexical scope.
type Scope struct {
	Parent   *Scope
	Symbols  map[string]*Symbol
	FuncType *types.Type // the function type this scope belongs to (for return checking)
	Depth    int
}

// NewScope creates a new scope.
func NewScope(parent *Scope, funcType *types.Type) *Scope {
	depth := 0
	if parent != nil {
		depth = parent.Depth + 1
	}
	return &Scope{
		Parent:   parent,
		Symbols:  make(map[string]*Symbol),
		FuncType: funcType,
		Depth:    depth,
	}
}

// Declare adds a symbol to the current scope.
func (s *Scope) Declare(sym *Symbol) error {
	if _, exists := s.Symbols[sym.Name]; exists {
		return fmt.Errorf("duplicate declaration: %s", sym.Name)
	}
	s.Symbols[sym.Name] = sym
	return nil
}

// Resolve looks up a name in the current and enclosing scopes.
func (s *Scope) Resolve(name string) *Symbol {
	for scope := s; scope != nil; scope = scope.Parent {
		if sym, exists := scope.Symbols[name]; exists {
			return sym
		}
	}
	return nil
}

// ResolveInCurrent looks up a name only in the current scope.
func (s *Scope) ResolveInCurrent(name string) *Symbol {
	return s.Symbols[name]
}

// AllSymbols returns all symbols visible from this scope.
func (s *Scope) AllSymbols() map[string]*Symbol {
	result := make(map[string]*Symbol)
	for scope := s; scope != nil; scope = scope.Parent {
		for name, sym := range scope.Symbols {
			if _, exists := result[name]; !exists {
				result[name] = sym
			}
		}
	}
	return result
}
