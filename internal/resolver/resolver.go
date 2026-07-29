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
	"float":      true,
	"byte":       true,
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
	"map":     true,
	"env":     true,
	"file":    true,
	"process": true,
	"time":    true,
	"random":  true,
	"path":    true,
	"base64":  true,
	"hash":    true,
}

// Resolver performs name resolution on the AST.
type Resolver struct {
	diags       *diagnostic.Diagnostics
	scope       *symbol.Scope
	funcs       []*ast.Function
	funcMap     map[string]int // function name -> index
	loopDepth   int
	src         *source.Source
	moduleName  string                 // current module name for scoped resolution
	enumNames   map[string]bool        // names of declared enums
	structTypes map[string]*types.Type // struct name -> struct type
	traitTypes  map[string]*types.Type // trait name -> trait type
	// External functions from other modules (mangled names like "module.func")
	allFuncs map[string]*ast.Function
}

// New creates a new resolver.
func New(src *source.Source) *Resolver {
	return &Resolver{
		diags:       diagnostic.NewDiagnostics(),
		scope:       symbol.NewScope(nil, nil),
		funcMap:     make(map[string]int),
		src:         src,
		structTypes: make(map[string]*types.Type),
		traitTypes:  make(map[string]*types.Type),
	}
}

// SetAllFuncs sets the complete map of all functions across modules.
// This is used for multi-file compilation to enable cross-module resolution.
func (r *Resolver) SetAllFuncs(funcs map[string]*ast.Function) {
	r.allFuncs = funcs
}

// Resolve performs name resolution on a program.
func (r *Resolver) Resolve(prog *ast.Program) (*diagnostic.Diagnostics, error) {
	// Collect enum declarations first
	r.enumNames = make(map[string]bool)
	for _, en := range prog.Enums {
		r.enumNames[en.Name] = true
	}

	// Process trait declarations (before structs so traits are available for type annotations)
	for _, td := range prog.Traits {
		r.processTraitDecl(td)
	}

	// Process struct declarations
	for _, sd := range prog.Structs {
		// Build struct type
		var fields []types.StructFieldInfo
		for _, f := range sd.Fields {
			resolveTypeAnnotation(f.Type, r.scope)
			ft := types.Invalid
			if f.Type != nil && f.Type.ResolvedType != nil {
				ft = f.Type.ResolvedType
			}
			fields = append(fields, types.StructFieldInfo{
				Name:  f.Name,
				Type:  ft,
				IsMut: f.IsMut,
				IsPub: f.IsPub,
			})
		}
		structType := types.StructType(sd.Name, fields)
		r.structTypes[sd.Name] = structType

		// Register struct name as a module-like symbol for MemberExpr resolution
		r.scope.Declare(&symbol.Symbol{
			Name:       sd.Name,
			Kind:       symbol.KindStruct,
			Type:       structType,
			Defined:    true,
			ModuleName: sd.Name,
		})

		// Collect methods with mangled names
		for _, m := range sd.Methods {
			idx := len(r.funcs)
			r.funcs = append(r.funcs, m)
			r.funcMap[m.Name] = idx
			if prog.Module != "" {
				r.funcMap[prog.Module+"."+m.Name] = idx
			}
			// Resolve the _self parameter type to the struct type
			if len(m.Parameters) > 0 && m.Parameters[0].Name == "_self" {
				m.Parameters[0].Type.ResolvedType = structType
			}
			// Resolve remaining parameter types
			for _, p := range m.Parameters {
				if p.Name != "_self" {
					resolveTypeAnnotation(p.Type, r.scope)
				}
			}
			for _, rt := range m.ReturnTypes {
				resolveTypeAnnotation(rt, r.scope)
			}
			// Register method in struct type
			if structType.StructMethods != nil {
				// Build method signature
				var paramTypes []*types.Type
				for _, p := range m.Parameters {
					if p.Type != nil && p.Type.ResolvedType != nil {
						paramTypes = append(paramTypes, p.Type.ResolvedType)
					} else {
						paramTypes = append(paramTypes, types.Invalid)
					}
				}
				var retType *types.Type
				if len(m.ReturnTypes) == 1 && m.ReturnTypes[0] != nil && m.ReturnTypes[0].ResolvedType != nil {
					retType = m.ReturnTypes[0].ResolvedType
				} else {
					retType = types.Void
				}
				methodType := types.FunctionType(paramTypes, retType)
				shortName := m.Name[len(sd.Name)+1:] // "Point.distance" -> "distance"
				structType.StructMethods[shortName] = &types.StructMethodInfo{
					FuncIndex: idx,
					Signature: methodType,
					IsPub:     m.IsPub,
				}
			}
		}
	}

	// Collect function declarations
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

	// Declare modules from allFuncs (cross-file modules from use declarations)
	if r.allFuncs != nil {
		for mangled := range r.allFuncs {
			dotIdx := -1
			for i := len(mangled) - 1; i >= 0; i-- {
				if mangled[i] == '.' {
					dotIdx = i
					break
				}
			}
			if dotIdx >= 0 {
				modName := mangled[:dotIdx]
				if r.scope.Resolve(modName) == nil && !knownModules[modName] {
					r.scope.Declare(&symbol.Symbol{
						Name:       modName,
						Kind:       symbol.KindModule,
						Defined:    true,
						ModuleName: modName,
					})
				}
			}
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

	// Declare enum names in scope (same as modules, for MemberExpr resolution)
	for _, en := range prog.Enums {
		r.scope.Declare(&symbol.Symbol{
			Name:       en.Name,
			Kind:       symbol.KindModule,
			Defined:    true,
			ModuleName: en.Name,
		})
	}

	r.moduleName = prog.Module

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

	// Resolve all return types
	for _, rt := range fn.ReturnTypes {
		resolveTypeAnnotation(rt, r.scope)
	}
	var returnType *types.Type
	if len(fn.ReturnTypes) == 1 {
		returnType = fn.ReturnTypes[0].ResolvedType
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
		// Variadic parameters are List<T> inside the function body
		paramType := p.Type.ResolvedType
		if p.Variadic && paramType != nil {
			paramType = types.ListOf(paramType)
		}
		r.scope.Declare(&symbol.Symbol{
			Name:      p.Name,
			Kind:      symbol.KindVariable,
			Type:      paramType,
			Slot:      slot,
			Parameter: true,
			Defined:   true, // parameters are always defined
		})
		slot++
	}

	// If this is a method, declare struct fields in scope
	if fn.StructName != "" {
		if structType, ok := r.structTypes[fn.StructName]; ok {
			for i, field := range structType.StructFields {
				r.scope.Declare(&symbol.Symbol{
					Name:          field.Name,
					Kind:          symbol.KindVariable,
					Type:          field.Type,
					Slot:          0, // field access goes through parameter 0
					Defined:       true,
					Mut:           field.IsMut,
					IsStructField: true,
					FieldIndex:    i,
					FieldOfSlot:   0,
				})
			}
		}
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
	case *ast.TryStmt:
		if s != nil {
			r.resolveTryStmt(s)
		}
	case *ast.ThrowStmt:
		if s != nil {
			r.resolveThrowStmt(s)
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
		Mut:     decl.IsMut,
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
	for _, val := range stmt.Values {
		if val != nil {
			r.resolveExpr(val)
		}
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

// resolveTryStmt resolves a try/catch/finally statement.
func (r *Resolver) resolveTryStmt(stmt *ast.TryStmt) {
	// Try block has its own scope
	if stmt.TryBody != nil {
		r.resolveBlockScope(stmt.TryBody)
	}

	// Catch clause has its own scope with the parameter
	if stmt.Catch != nil {
		oldScope := r.scope
		r.scope = symbol.NewScope(oldScope, r.scope.FuncType)

		// Resolve catch parameter type
		if stmt.Catch.ParamType != nil {
			resolveTypeAnnotation(stmt.Catch.ParamType, r.scope)
		}

		// Declare catch parameter
		paramType := types.Invalid
		if stmt.Catch.ParamType != nil && stmt.Catch.ParamType.ResolvedType != nil {
			paramType = stmt.Catch.ParamType.ResolvedType
		}

		r.scope.Declare(&symbol.Symbol{
			Name:    stmt.Catch.ParamName,
			Kind:    symbol.KindVariable,
			Type:    paramType,
			Slot:    -1,
			Defined: true,
		})

		// Resolve catch body
		if stmt.Catch.Body != nil {
			r.resolveBlock(stmt.Catch.Body)
		}

		r.scope = oldScope
	}

	// Finally block has its own scope
	if stmt.Finally != nil {
		r.resolveBlockScope(stmt.Finally)
	}
}

// resolveThrowStmt resolves a throw statement.
func (r *Resolver) resolveThrowStmt(stmt *ast.ThrowStmt) {
	if stmt.Value != nil {
		r.resolveExpr(stmt.Value)
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
		// Check if the object is an enum type name
		if ident, ok := e.Object.(*ast.Identifier); ok {
			if r.enumNames[ident.Name] {
				// Enum variant reference; resolved during type checking
				return
			}
		}
	case *ast.SpreadExpr:
		r.resolveExpr(e.Expr)
	case *ast.MultiAssignExpr:
		r.resolveExpr(e.Value)

	case *ast.StructLiteral:
		for _, val := range e.Values {
			r.resolveExpr(val)
		}
	case *ast.NullCoalescing:
		r.resolveExpr(e.Left)
		r.resolveExpr(e.Right)
	// Literals need no resolution
	case *ast.IntLiteral, *ast.FloatLiteral,
		*ast.BoolLiteral, *ast.CharLiteral,
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
		// Check if it's a function in the same module (scoped by package name)
		if r.allFuncs != nil {
			for mangled := range r.allFuncs {
				dotIdx := -1
				for i := len(mangled) - 1; i >= 0; i-- {
					if mangled[i] == '.' {
						dotIdx = i
						break
					}
				}
				if dotIdx >= 0 && mangled[dotIdx+1:] == ident.Name && mangled[:dotIdx] == r.moduleName {
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

// processTraitDecl processes a trait declaration and registers it in scope.
func (r *Resolver) processTraitDecl(td *ast.TraitDecl) {
	methods := make(map[string]*types.TraitMethodInfo)

	for _, m := range td.Methods {
		// Resolve parameter types (skip _self — trait methods don't have it)
		var paramTypes []*types.Type
		for _, p := range m.Parameters {
			resolveTypeAnnotation(p.Type, r.scope)
			if p.Type != nil && p.Type.ResolvedType != nil {
				paramTypes = append(paramTypes, p.Type.ResolvedType)
			} else {
				paramTypes = append(paramTypes, types.Invalid)
			}
		}

		// Resolve return types
		for _, rt := range m.ReturnTypes {
			resolveTypeAnnotation(rt, r.scope)
		}
		var retType *types.Type
		if len(m.ReturnTypes) == 1 && m.ReturnTypes[0] != nil && m.ReturnTypes[0].ResolvedType != nil {
			retType = m.ReturnTypes[0].ResolvedType
		} else {
			retType = types.Void
		}

		methodType := types.FunctionType(paramTypes, retType)
		methods[m.Name] = &types.TraitMethodInfo{
			Signature: methodType,
			IsPub:     true,
		}
	}

	traitType := types.TraitType(td.Name, methods)
	r.traitTypes[td.Name] = traitType

	// Register trait name as a symbol in scope
	r.scope.Declare(&symbol.Symbol{
		Name:       td.Name,
		Kind:       symbol.KindTrait,
		Type:       traitType,
		Defined:    true,
		ModuleName: td.Name,
	})
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
		// Check for user-defined enum type name — leave unresolved for the checker
		if ta.Kind == types.KindInvalid && ta.TypeName != "" {
			// Don't set ResolvedType here; the checker will resolve it after processing enums
			return
		} else {
			ta.ResolvedType = kindToType(ta.Kind, ta.Nullable)
		}
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
	case types.KindFloat:
		return boolPtr(types.Float, nullable)
	case types.KindChar:
		return boolPtr(types.Char, nullable)
	case types.KindString:
		return boolPtr(types.String, nullable)
	case types.KindException:
		return boolPtr(types.Exception, nullable)
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
