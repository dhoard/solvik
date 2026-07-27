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

// Package resolver performs lexical scoping and name resolution.
package resolver

import (
	"github.com/dhoard/solvik-language/internal/ast"
	"github.com/dhoard/solvik-language/internal/diagnostic"
	"github.com/dhoard/solvik-language/internal/source"
	"github.com/dhoard/solvik-language/internal/symbol"
	"github.com/dhoard/solvik-language/internal/types"
)

// builtinFunctions are known native/host functions that don't need declaration.
var builtinFunctions = map[string]bool{
	"print":      true,
	"println":    true,
	"string":     true,
	"int":        true,
	"long":       true,
	"double":     true,
	"bool":       true,
	"typeOf":     true,
	"regex":      true,
	"len":        true,
	"length":     true,
	"byteLength": true,
	"charAt":     true,
	"substring":  true,
	"contains":   true,
	"startsWith": true,
	"endsWith":   true,
	"indexOf":    true,
	"toUpper":    true,
	"toLower":    true,
	"trim":       true,
	"split":      true,
	"join":       true,
	"abs":        true,
	"min":        true,
	"max":        true,
	"floor":      true,
	"ceil":       true,
	"round":      true,
	"sqrt":       true,
	"pow":        true,
	"sin":        true,
	"cos":        true,
	"tan":        true,
	"now":        true,
	"sleep":      true,
}

// knownModules are built-in modules that are always available.
var knownModules = map[string]bool{
	"core":    true,
	"string":  true,
	"math":    true,
	"env":     true,
	"file":    true,
	"process": true,
	"time":    true,
}

// Resolver performs name resolution on the AST.
type Resolver struct {
	diags     *diagnostic.Diagnostics
	scope     *symbol.Scope
	funcs     []*ast.Function
	funcMap   map[string]int // function name -> index
	loopDepth int
	src       *source.Source
	// External functions from other modules (mangled names like "module.func")
	allFuncs map[string]*ast.Function
}

// New creates a new resolver.
func New(src *source.Source) *Resolver {
	return &Resolver{
		diags:   diagnostic.NewDiagnostics(),
		scope:   symbol.NewScope(nil, nil),
		funcMap: make(map[string]int),
		src:     src,
	}
}

// SetAllFuncs sets the complete map of all functions across modules.
// This is used for multi-file compilation to enable cross-module resolution.
func (r *Resolver) SetAllFuncs(funcs map[string]*ast.Function) {
	r.allFuncs = funcs
}

// Resolve performs name resolution on a program.
func (r *Resolver) Resolve(prog *ast.Program) (*diagnostic.Diagnostics, error) {
	// Collect function declarations first
	for _, fn := range prog.Funcs {
		idx := len(r.funcs)
		r.funcs = append(r.funcs, fn)
		// Register with and without module prefix
		if prog.Module != "" {
			r.funcMap[prog.Module+"."+fn.Name] = idx
		} else {
			r.funcMap[fn.Name] = idx
		}
		r.funcMap[fn.Name] = idx // unqualified name too
	}

	// Declare imported modules
	for _, imp := range prog.Imports {
		if imp.Alias != "" {
			r.scope.Declare(&symbol.Symbol{
				Name:       imp.Alias,
				Kind:       symbol.KindModule,
				Defined:    true,
				ModuleName: imp.Module,
			})
		} else {
			r.scope.Declare(&symbol.Symbol{
				Name:       imp.Module,
				Kind:       symbol.KindModule,
				Defined:    true,
				ModuleName: imp.Module,
			})
		}
	}

	// Declare known modules (core, etc.)
	for mod := range knownModules {
		if r.scope.Resolve(mod) == nil {
			r.scope.Declare(&symbol.Symbol{
				Name:       mod,
				Kind:       symbol.KindModule,
				Defined:    true,
				ModuleName: mod,
			})
		}
	}

	// Resolve each function
	for i, fn := range prog.Funcs {
		r.resolveFunction(fn, i)
	}

	if r.diags.HasErrors() {
		return r.diags, nil
	}
	return r.diags, nil
}

// resolveFunction resolves names within a function.
func (r *Resolver) resolveFunction(fn *ast.Function, funcIdx int) {
	// Determine function type
	var paramTypes []*types.Type
	for _, p := range fn.Parameters {
		resolveTypeAnnotation(p.Type, r.scope)
		paramTypes = append(paramTypes, p.Type.ResolvedType)
	}

	var returnType *types.Type
	if fn.ReturnType != nil {
		resolveTypeAnnotation(fn.ReturnType, r.scope)
		returnType = fn.ReturnType.ResolvedType
	} else {
		returnType = types.Void
	}

	funcType := types.FunctionType(paramTypes, returnType)

	// Create function scope
	oldScope := r.scope
	r.scope = symbol.NewScope(oldScope, funcType)

	// Declare parameters
	slot := 0
	for _, p := range fn.Parameters {
		r.scope.Declare(&symbol.Symbol{
			Name:      p.Name,
			Kind:      symbol.KindVariable,
			Type:      p.Type.ResolvedType,
			Slot:      slot,
			Parameter: true,
			Defined:   true, // parameters are always defined
		})
		slot++
	}

	// Resolve body
	if fn.Body != nil {
		r.resolveBlock(fn.Body)
	}

	r.scope = oldScope
}

// resolveBlock resolves a block of statements.
func (r *Resolver) resolveBlock(block *ast.Block) {
	for _, stmt := range block.Statements {
		r.resolveStatement(stmt)
	}
}

// resolveStatement resolves a statement.
func (r *Resolver) resolveStatement(stmt ast.Statement) {
	if stmt == nil {
		return
	}
	switch s := stmt.(type) {
	case *ast.VariableDecl:
		if s != nil {
			r.resolveVarDecl(s)
		}
	case *ast.AssignStmt:
		if s != nil {
			r.resolveAssignStmt(s)
		}
	case *ast.IfStmt:
		if s != nil {
			r.resolveIfStmt(s)
		}
	case *ast.WhileStmt:
		if s != nil {
			r.resolveWhileStmt(s)
		}
	case *ast.ForStmt:
		if s != nil {
			r.resolveForStmt(s)
		}
	case *ast.SwitchStmt:
		if s != nil {
			r.resolveSwitchStmt(s)
		}
	case *ast.BreakStmt:
		if s != nil {
			r.resolveBreakStmt(s)
		}
	case *ast.ContinueStmt:
		if s != nil {
			r.resolveContinueStmt(s)
		}
	case *ast.ReturnStmt:
		if s != nil {
			r.resolveReturnStmt(s)
		}
	case *ast.ExprStmt:
		if s != nil {
			r.resolveExpr(s.Expr)
		}
	case *ast.Block:
		if s != nil {
			r.resolveBlockScope(s)
		}
	}
}

// resolveVarDecl resolves a variable declaration.
func (r *Resolver) resolveVarDecl(decl *ast.VariableDecl) {
	if decl.Type != nil {
		resolveTypeAnnotation(decl.Type, r.scope)
	}

	if decl.InitExpr != nil {
		r.resolveExpr(decl.InitExpr)
	}

	varType := types.Invalid
	if decl.Type != nil && decl.Type.ResolvedType != nil {
		varType = decl.Type.ResolvedType
	}

	r.scope.Declare(&symbol.Symbol{
		Name:    decl.Name,
		Kind:    symbol.KindVariable,
		Type:    varType,
		Slot:    -1, // assigned later
		Defined: decl.InitExpr != nil,
	})
}

// resolveAssignStmt resolves an assignment.
func (r *Resolver) resolveAssignStmt(stmt *ast.AssignStmt) {
	if stmt.Value != nil {
		r.resolveExpr(stmt.Value)
	}
	sym := r.scope.Resolve(stmt.Name)
	if sym == nil {
		r.diags.AddError("R001", "undeclared variable: "+stmt.Name, stmt.Span())
	}
}

// resolveIfStmt resolves an if statement.
func (r *Resolver) resolveIfStmt(stmt *ast.IfStmt) {
	if stmt.Condition != nil {
		r.resolveExpr(stmt.Condition)
	}
	if stmt.Then != nil {
		r.resolveBlockScope(stmt.Then)
	}
	for _, ei := range stmt.ElseIf {
		r.resolveIfStmt(ei)
	}
	if stmt.Else != nil {
		r.resolveBlockScope(stmt.Else)
	}
}

// resolveWhileStmt resolves a while loop.
func (r *Resolver) resolveWhileStmt(stmt *ast.WhileStmt) {
	if stmt.Condition != nil {
		r.resolveExpr(stmt.Condition)
	}
	r.loopDepth++
	if stmt.Body != nil {
		r.resolveBlockScope(stmt.Body)
	}
	r.loopDepth--
}

// resolveForStmt resolves a for-in loop.
func (r *Resolver) resolveForStmt(stmt *ast.ForStmt) {
	if stmt.Iterable != nil {
		r.resolveExpr(stmt.Iterable)
	}

	oldScope := r.scope
	r.scope = symbol.NewScope(oldScope, r.scope.FuncType)

	r.scope.Declare(&symbol.Symbol{
		Name:    stmt.Variable,
		Kind:    symbol.KindVariable,
		Type:    types.Invalid, // determined during type checking
		Slot:    -1,
		Defined: true,
	})

	// Declare the value variable for (key, value) unpacking
	if stmt.ValueVariable != "" {
		r.scope.Declare(&symbol.Symbol{
			Name:    stmt.ValueVariable,
			Kind:    symbol.KindVariable,
			Type:    types.Invalid, // determined during type checking
			Slot:    -1,
			Defined: true,
		})
	}

	r.loopDepth++
	if stmt.Body != nil {
		r.resolveBlock(stmt.Body)
	}
	r.loopDepth--

	r.scope = oldScope
}

// resolveBreakStmt resolves a break statement.
func (r *Resolver) resolveBreakStmt(stmt *ast.BreakStmt) {
	if r.loopDepth <= 0 {
		r.diags.AddError("R002", "break outside loop", stmt.Span())
	}
}

// resolveContinueStmt resolves a continue statement.
func (r *Resolver) resolveContinueStmt(stmt *ast.ContinueStmt) {
	if r.loopDepth <= 0 {
		r.diags.AddError("R003", "continue outside loop", stmt.Span())
	}
}

// resolveReturnStmt resolves a return statement.
func (r *Resolver) resolveReturnStmt(stmt *ast.ReturnStmt) {
	if stmt.Value != nil {
		r.resolveExpr(stmt.Value)
	}
}

// resolveSwitchStmt resolves a switch statement.
func (r *Resolver) resolveSwitchStmt(stmt *ast.SwitchStmt) {
	if stmt.Expression != nil {
		r.resolveExpr(stmt.Expression)
	}
	for _, c := range stmt.Cases {
		if c.Expression != nil {
			r.resolveExpr(c.Expression)
		}
		if c.Body != nil {
			r.resolveBlockScope(c.Body)
		}
	}
	if stmt.Default != nil {
		r.resolveBlockScope(stmt.Default)
	}
}

// resolveBlockScope resolves a block with a new scope.
func (r *Resolver) resolveBlockScope(block *ast.Block) {
	oldScope := r.scope
	r.scope = symbol.NewScope(oldScope, r.scope.FuncType)
	r.resolveBlock(block)
	r.scope = oldScope
}

// resolveExpr resolves an expression.
func (r *Resolver) resolveExpr(expr ast.Expression) {
	switch e := expr.(type) {
	case *ast.Identifier:
		r.resolveIdentifier(e)
	case *ast.UnaryExpr:
		r.resolveExpr(e.Operand)
	case *ast.BinaryExpr:
		r.resolveExpr(e.Left)
		r.resolveExpr(e.Right)
	case *ast.CallExpr:
		r.resolveExpr(e.Function)
		for _, arg := range e.Args {
			r.resolveExpr(arg)
		}
	case *ast.IndexExpr:
		r.resolveExpr(e.Target)
		r.resolveExpr(e.Index)
	case *ast.ListLiteral:
		for _, el := range e.Elements {
			r.resolveExpr(el)
		}
	case *ast.MapLiteral:
		for i := range e.Keys {
			r.resolveExpr(e.Keys[i])
			r.resolveExpr(e.Values[i])
		}
	case *ast.MemberExpr:
		r.resolveExpr(e.Object)
		// The member name is resolved via the object's type
		// Check if the object is an imported or known module
		if ident, ok := e.Object.(*ast.Identifier); ok {
			sym := r.scope.Resolve(ident.Name)
			if sym != nil && sym.Kind == symbol.KindModule {
				// Module members are resolved at type-checking time
				return
			}
			// Check for known modules (even if not imported)
			if knownModules[ident.Name] {
				// Known module members are valid
				return
			}
			// Also check against allFuncs for module.qualified calls
			if r.allFuncs != nil {
				mangledName := ident.Name + "." + e.Member
				if _, exists := r.allFuncs[mangledName]; exists {
					return
				}
				// Check for native functions like core.print
				if knownModules[ident.Name] && builtinFunctions[e.Member] {
					return
				}
			}
		}
	case *ast.NullCoalescing:
		r.resolveExpr(e.Left)
		r.resolveExpr(e.Right)
	// Literals need no resolution
	case *ast.IntLiteral, *ast.LongLiteral, *ast.FloatLiteral,
		*ast.DoubleLiteral, *ast.BoolLiteral, *ast.CharLiteral,
		*ast.StringLiteral, *ast.ByteLiteral, *ast.NullLiteral:
	}
}

// resolveIdentifier resolves an identifier reference.
func (r *Resolver) resolveIdentifier(ident *ast.Identifier) {
	sym := r.scope.Resolve(ident.Name)
	if sym == nil {
		// Check if it's a function name in the current module
		if _, exists := r.funcMap[ident.Name]; exists {
			return
		}
		// Check if it's a function in another module (mangled name "module.func")
		if r.allFuncs != nil {
			for mangled := range r.allFuncs {
				dotIdx := -1
				for i := len(mangled) - 1; i >= 0; i-- {
					if mangled[i] == '.' {
						dotIdx = i
						break
					}
				}
				if dotIdx >= 0 && mangled[dotIdx+1:] == ident.Name {
					return
				}
			}
		}
		// Check if it's a builtin native function first (since names like "string" can be
		// both functions and module names; function calls take priority)
		if builtinFunctions[ident.Name] {
			return
		}
		// Check if it's a known module name
		if knownModules[ident.Name] {
			return
		}
		r.diags.AddError("R004", "undeclared identifier: "+ident.Name, ident.Span())
	} else if sym.Kind == symbol.KindModule {
		// Module name is valid; members will be resolved during type checking
		return
	}
}

// resolveTypeAnnotation resolves type references in a type annotation.
func resolveTypeAnnotation(ta *ast.TypeAnnotation, scope *symbol.Scope) {
	if ta == nil {
		return
	}
	switch ta.Kind {
	case types.KindList:
		if ta.Element != nil {
			resolveTypeAnnotation(ta.Element, scope)
			ta.ResolvedType = types.ListOf(ta.Element.ResolvedType)
		}
	case types.KindMap:
		if ta.KeyType != nil && ta.ValueType != nil {
			resolveTypeAnnotation(ta.KeyType, scope)
			resolveTypeAnnotation(ta.ValueType, scope)
			ta.ResolvedType = types.MapOf(ta.KeyType.ResolvedType, ta.ValueType.ResolvedType)
		}
	default:
		ta.ResolvedType = kindToType(ta.Kind, ta.Nullable)
	}
}

func kindToType(kind types.Kind, nullable bool) *types.Type {
	switch kind {
	case types.KindBool:
		return boolPtr(types.Bool, nullable)
	case types.KindByte:
		return boolPtr(types.Byte, nullable)
	case types.KindInt:
		return boolPtr(types.Int, nullable)
	case types.KindLong:
		return boolPtr(types.Long, nullable)
	case types.KindFloat:
		return boolPtr(types.Float, nullable)
	case types.KindDouble:
		return boolPtr(types.Double, nullable)
	case types.KindChar:
		return boolPtr(types.Char, nullable)
	case types.KindString:
		return boolPtr(types.String, nullable)
	case types.KindVoid:
		return types.Void
	default:
		return types.Invalid
	}
}

func boolPtr(t *types.Type, nullable bool) *types.Type {
	if nullable {
		return types.NullableOf(t)
	}
	return t
}
