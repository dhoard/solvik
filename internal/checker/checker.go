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

// Package checker performs static type checking on the resolved AST.
package checker

import (
	"fmt"
	"strconv"

	"github.com/dhoard/solvik-language/internal/ast"
	"github.com/dhoard/solvik-language/internal/diagnostic"
	"github.com/dhoard/solvik-language/internal/source"
	"github.com/dhoard/solvik-language/internal/symbol"
	"github.com/dhoard/solvik-language/internal/types"
)

// builtinFuncs defines the signatures of builtin native functions.
var builtinFuncs = map[string]*types.Type{
	"print":   types.FunctionType([]*types.Type{types.String}, types.Void),
	"println": types.FunctionType([]*types.Type{types.String}, types.Void),
	"regex":   types.FunctionType([]*types.Type{types.String}, types.Invalid),
	"len":     types.FunctionType([]*types.Type{types.Invalid /* any list/map */}, types.Int),
	"string":  types.FunctionType([]*types.Type{types.Invalid /* any */}, types.String),
	"int":     types.FunctionType([]*types.Type{types.String}, types.Int),
	"float":   types.FunctionType([]*types.Type{types.String}, types.Float),
	"byte":    types.FunctionType([]*types.Type{types.Invalid /* int or float */}, types.Byte),
	"bool":    types.FunctionType([]*types.Type{types.Invalid /* any */}, types.Bool),
	"typeOf":  types.FunctionType([]*types.Type{types.Invalid /* any */}, types.String),
	// String module functions
	"length":     types.FunctionType([]*types.Type{types.String}, types.Int),
	"byteLength": types.FunctionType([]*types.Type{types.String}, types.Int),
	"charAt":     types.FunctionType([]*types.Type{types.String, types.Int}, types.Char),
	"substring":  types.FunctionType([]*types.Type{types.String, types.Int, types.Int}, types.String),
	"contains":   types.FunctionType([]*types.Type{types.String, types.String}, types.Bool),
	"startsWith": types.FunctionType([]*types.Type{types.String, types.String}, types.Bool),
	"endsWith":   types.FunctionType([]*types.Type{types.String, types.String}, types.Bool),
	"indexOf":    types.FunctionType([]*types.Type{types.String, types.String}, types.Int),
	"toUpper":    types.FunctionType([]*types.Type{types.String}, types.String),
	"toLower":    types.FunctionType([]*types.Type{types.String}, types.String),
	"trim":       types.FunctionType([]*types.Type{types.String}, types.String),
	"split":      types.FunctionType([]*types.Type{types.String, types.String}, types.ListOf(types.String)),
	"join":       types.FunctionType([]*types.Type{types.ListOf(types.String), types.String}, types.String),
	// Math module functions
	"PI":    types.FunctionType(nil, types.Float),
	"E":     types.FunctionType(nil, types.Float),
	"abs":   types.FunctionType([]*types.Type{types.Float}, types.Float),
	"min":   types.FunctionType([]*types.Type{types.Float, types.Float}, types.Float),
	"max":   types.FunctionType([]*types.Type{types.Float, types.Float}, types.Float),
	"floor": types.FunctionType([]*types.Type{types.Float}, types.Float),
	"ceil":  types.FunctionType([]*types.Type{types.Float}, types.Float),
	"round": types.FunctionType([]*types.Type{types.Float}, types.Float),
	"sqrt":  types.FunctionType([]*types.Type{types.Float}, types.Float),
	"pow":   types.FunctionType([]*types.Type{types.Float, types.Float}, types.Float),
	"sin":   types.FunctionType([]*types.Type{types.Float}, types.Float),
	"cos":   types.FunctionType([]*types.Type{types.Float}, types.Float),
	"tan":   types.FunctionType([]*types.Type{types.Float}, types.Float),
	// Env module
	"env.get":  types.FunctionType([]*types.Type{types.String}, types.NullableOf(types.String)),
	"env.set":  types.FunctionType([]*types.Type{types.String, types.String}, types.Void),
	"env.keys": types.FunctionType(nil, types.ListOf(types.String)),
	// File module
	"file.read":   types.FunctionType([]*types.Type{types.String}, types.String),
	"file.write":  types.FunctionType([]*types.Type{types.String, types.String}, types.Void),
	"file.append": types.FunctionType([]*types.Type{types.String, types.String}, types.Void),
	"file.delete": types.FunctionType([]*types.Type{types.String}, types.Void),
	"file.exists": types.FunctionType([]*types.Type{types.String}, types.Bool),
	// Map module
	"map.contains": types.FunctionType([]*types.Type{types.Invalid /* map */, types.Invalid /* key */}, types.Bool),
	// Process module
	"process.run": types.VariadicFunctionType([]*types.Type{types.String, types.String}, types.Int),
	// Time module
	"time.now":   types.FunctionType(nil, types.Int),
	"time.sleep": types.FunctionType([]*types.Type{types.Int}, types.Void),
	// Random module
	"random.float":   types.FunctionType(nil, types.Float),
	"random.int":     types.FunctionType([]*types.Type{types.Int, types.Int}, types.Int),
	"random.range":   types.FunctionType([]*types.Type{types.Int, types.Int}, types.Int),
	"random.uniform": types.FunctionType([]*types.Type{types.Float, types.Float}, types.Float),
	"random.choice":  types.FunctionType([]*types.Type{types.Invalid /* any list */}, types.Any /* element type */),
	"random.shuffle": types.FunctionType([]*types.Type{types.Invalid /* any list */}, types.Any /* list type */),
	"random.sample":  types.FunctionType([]*types.Type{types.Invalid /* any list */, types.Int}, types.Any /* list type */),
	"random.seed":    types.FunctionType([]*types.Type{types.Int}, types.Void),
	// Path module
	"path.join":     types.VariadicFunctionType([]*types.Type{types.String, types.String}, types.String),
	"path.basename": types.FunctionType([]*types.Type{types.String}, types.String),
	"path.dirname":  types.FunctionType([]*types.Type{types.String}, types.String),
	"path.ext":      types.FunctionType([]*types.Type{types.String}, types.String),
	"path.abs":      types.FunctionType([]*types.Type{types.String}, types.String),
	"path.exists":   types.FunctionType([]*types.Type{types.String}, types.Bool),
}

// Checker performs type checking.
type Checker struct {
	diags      *diagnostic.Diagnostics
	scope      *symbol.Scope
	funcs      []*ast.Function
	funcMap    map[string]int
	src        *source.Source
	loopDepth  int
	moduleName string // current module name for scoped resolution
	// Track the enclosing if-condition for null narrowing
	narrowingVar string      // variable name being narrowed (set during if-condition check)
	narrowedType *types.Type // the narrowed type (non-nullable)
	// Track definite assignment
	definitelyAssigned map[string]bool // variables that have been assigned in the current path
	// External functions from other modules (mangled names)
	allFuncs map[string]*ast.Function
	// Skip main function check (for library modules in multi-file compilation)
	skipMainCheck bool
	// Track current function for multi-return checking
	currentFuncIdx int
	// Enum tracking
	enums     []*ast.EnumDecl
	enumTypes map[string]*types.Type // enum name -> base enum type
	// Struct tracking
	structs       []*ast.StructDecl
	structTypes   map[string]*types.Type // struct name -> struct type
	currentStruct *types.Type            // set when checking a struct method body
	// Trait tracking
	traitTypes map[string]*types.Type // trait name -> trait type
	// Satisfaction cache: "structName:traitName" -> bool
	satisfactionCache map[string]bool
}

// SetAllFuncs sets the complete map of all functions across modules.
func (c *Checker) SetAllFuncs(funcs map[string]*ast.Function) {
	c.allFuncs = funcs
}

// SetSkipMainCheck configures whether to skip the main function check.
func (c *Checker) SetSkipMainCheck(skip bool) {
	c.skipMainCheck = skip
}

// New creates a new type checker.
func New(src *source.Source) *Checker {
	return &Checker{
		diags:   diagnostic.NewDiagnostics(),
		scope:   symbol.NewScope(nil, nil),
		funcMap: make(map[string]int),
		src:     src,
	}
}

// Check performs type checking on a program.
func (c *Checker) Check(prog *ast.Program) (*diagnostic.Diagnostics, error) {
	// Collect and check enum declarations first
	c.enums = prog.Enums
	c.enumTypes = make(map[string]*types.Type)
	for _, en := range prog.Enums {
		c.checkEnumDecl(en)
	}

	// Process trait declarations (before structs so they are available for type annotations)
	c.traitTypes = make(map[string]*types.Type)
	c.satisfactionCache = make(map[string]bool)
	for _, td := range prog.Traits {
		c.checkTraitDecl(td)
	}

	// Collect function declarations
	for _, fn := range prog.Funcs {
		idx := len(c.funcs)
		c.funcs = append(c.funcs, fn)
		if prog.Module != "" {
			c.funcMap[prog.Module+"."+fn.Name] = idx
		}
		c.funcMap[fn.Name] = idx
	}

	// Declare imported modules
	for _, imp := range prog.Imports {
		name := imp.Module
		if imp.Alias != "" {
			name = imp.Alias
		}
		c.scope.Declare(&symbol.Symbol{
			Name:       name,
			Kind:       symbol.KindModule,
			Defined:    true,
			ModuleName: imp.Module,
		})
	}

	// Declare modules from allFuncs (cross-file modules from use declarations)
	if c.allFuncs != nil {
		for mangled := range c.allFuncs {
			dotIdx := -1
			for i := len(mangled) - 1; i >= 0; i-- {
				if mangled[i] == '.' {
					dotIdx = i
					break
				}
			}
			if dotIdx >= 0 {
				modName := mangled[:dotIdx]
				if c.scope.Resolve(modName) == nil {
					c.scope.Declare(&symbol.Symbol{
						Name:       modName,
						Kind:       symbol.KindModule,
						Defined:    true,
						ModuleName: modName,
					})
				}
			}
		}
	}

	// Declare known modules (built-in modules available without explicit import)
	for _, mod := range []string{"core", "string", "math", "env", "file", "process", "time", "random", "path"} {
		if c.scope.Resolve(mod) == nil {
			c.scope.Declare(&symbol.Symbol{
				Name:       mod,
				Kind:       symbol.KindModule,
				Defined:    true,
				ModuleName: mod,
			})
		}
	}

	// Process struct declarations
	c.structs = prog.Structs
	c.structTypes = make(map[string]*types.Type)
	for _, sd := range prog.Structs {
		c.checkStructDecl(sd)
	}

	c.moduleName = prog.Module

	// Check each function
	for i, fn := range prog.Funcs {
		c.checkFunction(fn, i)
	}

	// Check struct methods
	for _, sd := range prog.Structs {
		for _, m := range sd.Methods {
			if idx, ok := c.funcMap[m.Name]; ok {
				c.checkFunction(m, idx)
			}
		}
	}

	// Check for main function
	c.checkMain(prog)

	if c.diags.HasErrors() {
		return c.diags, nil
	}
	return c.diags, nil
}

// checkEnumDecl checks an enum declaration and registers it in scope.
func (c *Checker) checkEnumDecl(en *ast.EnumDecl) {
	// Auto-assign values and validate
	usedValues := make(map[int32]bool)
	usedNames := make(map[string]bool)
	nextVal := int32(0)
	enumValues := make(map[string]int32)

	for i := range en.Variants {
		v := &en.Variants[i]

		// Check duplicate name
		if usedNames[v.Name] {
			c.diags.AddError("C046",
				fmt.Sprintf("duplicate variant name '%s' in enum '%s'", v.Name, en.Name),
				v.Span())
			continue
		}
		usedNames[v.Name] = true

		// Determine value
		var val int32
		if v.Value != nil {
			val = *v.Value
			nextVal = val + 1
		} else {
			val = nextVal
			nextVal++
		}

		// Check duplicate value
		if usedValues[val] {
			c.diags.AddError("C047",
				fmt.Sprintf("duplicate value %d in enum '%s'", val, en.Name),
				v.Span())
		}
		usedValues[val] = true

		v.ResolvedInt = val
		enumValues[v.Name] = val
	}

	// Create enum type and register in scope
	enumType := types.EnumType(en.Name, enumValues)
	c.enumTypes[en.Name] = enumType

	// Register enum name as a module-like symbol so Color.Red resolves as MemberExpr
	c.scope.Declare(&symbol.Symbol{
		Name:       en.Name,
		Kind:       symbol.KindModule,
		Defined:    true,
		ModuleName: en.Name,
		Type:       enumType,
	})
}

// checkTraitDecl checks a trait declaration and registers it in scope.
func (c *Checker) checkTraitDecl(td *ast.TraitDecl) {
	methods := make(map[string]*types.TraitMethodInfo)

	for _, m := range td.Methods {
		// Resolve parameter types (no _self in trait methods)
		var paramTypes []*types.Type
		for _, p := range m.Parameters {
			c.resolveEnumTypeAnnotation(p.Type)
			if p.Type != nil && p.Type.ResolvedType != nil {
				paramTypes = append(paramTypes, p.Type.ResolvedType)
			} else {
				paramTypes = append(paramTypes, types.Invalid)
			}
		}

		// Resolve return types
		for _, rt := range m.ReturnTypes {
			c.resolveEnumTypeAnnotation(rt)
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
	c.traitTypes[td.Name] = traitType

	// Register in scope
	c.scope.Declare(&symbol.Symbol{
		Name:       td.Name,
		Kind:       symbol.KindTrait,
		Type:       traitType,
		Defined:    true,
		ModuleName: td.Name,
	})
}

// checkStructDecl checks a struct declaration and registers it in scope.
func (c *Checker) checkStructDecl(sd *ast.StructDecl) {
	var fields []types.StructFieldInfo
	for _, f := range sd.Fields {
		c.resolveEnumTypeAnnotation(f.Type)
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
	c.structTypes[sd.Name] = structType

	// Register in scope as a struct-type symbol
	c.scope.Declare(&symbol.Symbol{
		Name:       sd.Name,
		Kind:       symbol.KindStruct,
		Type:       structType,
		Defined:    true,
		ModuleName: sd.Name,
	})

	// Add methods to the checker's function list and register in struct type
	for _, m := range sd.Methods {
		idx := len(c.funcs)
		c.funcs = append(c.funcs, m)
		c.funcMap[m.Name] = idx

		// Resolve _self parameter type to the struct type
		if len(m.Parameters) > 0 && m.Parameters[0].Name == "_self" {
			m.Parameters[0].Type.ResolvedType = structType
		}
		// Resolve other parameter types
		for _, p := range m.Parameters {
			if p.Name != "_self" {
				c.resolveEnumTypeAnnotation(p.Type)
			}
		}
		for _, rt := range m.ReturnTypes {
			c.resolveEnumTypeAnnotation(rt)
		}

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
		shortName := m.Name[len(sd.Name)+1:]
		structType.StructMethods[shortName] = &types.StructMethodInfo{
			FuncIndex: idx,
			Signature: methodType,
			IsPub:     m.IsPub,
		}
	}
}

// resolveEnumTypeAnnotation resolves a type annotation that may be an enum type name.
// This is needed because the resolver leaves enum type names unresolved for the checker to handle.
func (c *Checker) resolveEnumTypeAnnotation(ta *ast.TypeAnnotation) {
	if ta == nil {
		return
	}
	if ta.Kind == types.KindInvalid && ta.TypeName != "" {
		// Look up the type name in scope — should be an enum or struct type
		sym := c.scope.Resolve(ta.TypeName)
		if sym != nil && sym.Type != nil && (sym.Type.Kind == types.KindEnum || sym.Type.Kind == types.KindStruct || sym.Type.Kind == types.KindTrait) {
			if ta.Nullable {
				ta.ResolvedType = types.NullableOf(sym.Type)
			} else {
				ta.ResolvedType = sym.Type
			}
		}
	} else if ta.Kind == types.KindList && ta.Element != nil {
		c.resolveEnumTypeAnnotation(ta.Element)
		if ta.Element.ResolvedType != nil {
			ta.ResolvedType = types.ListOf(ta.Element.ResolvedType)
		}
	} else if ta.Kind == types.KindMap {
		if ta.KeyType != nil {
			c.resolveEnumTypeAnnotation(ta.KeyType)
		}
		if ta.ValueType != nil {
			c.resolveEnumTypeAnnotation(ta.ValueType)
		}
		if ta.KeyType != nil && ta.KeyType.ResolvedType != nil &&
			ta.ValueType != nil && ta.ValueType.ResolvedType != nil {
			ta.ResolvedType = types.MapOf(ta.KeyType.ResolvedType, ta.ValueType.ResolvedType)
		}
	}
}

// checkFunction checks a function declaration.
func (c *Checker) checkFunction(fn *ast.Function, funcIdx int) {
	// Resolve any enum type annotations in parameters
	for _, p := range fn.Parameters {
		c.resolveEnumTypeAnnotation(p.Type)
	}
	for _, rt := range fn.ReturnTypes {
		c.resolveEnumTypeAnnotation(rt)
	}

	// Build parameter types
	var paramTypes []*types.Type
	lastParamVariadic := false
	for _, p := range fn.Parameters {
		if p.Type != nil && p.Type.ResolvedType != nil {
			paramTypes = append(paramTypes, p.Type.ResolvedType)
			if p.Variadic {
				lastParamVariadic = true
			}
		}
	}

	var retTypes []*types.Type
	for _, rt := range fn.ReturnTypes {
		if rt != nil && rt.ResolvedType != nil {
			retTypes = append(retTypes, rt.ResolvedType)
		}
	}
	var retType *types.Type
	if len(retTypes) == 1 {
		retType = retTypes[0]
	} else {
		retType = types.Void
	}

	var funcType *types.Type
	if lastParamVariadic {
		funcType = types.VariadicFunctionType(paramTypes, retType)
	} else {
		funcType = types.FunctionType(paramTypes, retType)
	}

	// Enter function scope
	oldScope := c.scope
	c.scope = symbol.NewScope(oldScope, funcType)

	// Declare parameters
	slot := 0
	for _, p := range fn.Parameters {
		t := types.Invalid
		if p.Type != nil && p.Type.ResolvedType != nil {
			t = p.Type.ResolvedType
			// Variadic parameters have type List<T> inside the function body
			if p.Variadic {
				t = types.ListOf(t)
			}
		}
		if !t.IsValid() {
			t = types.Invalid
		}
		sym := &symbol.Symbol{
			Name:      p.Name,
			Kind:      symbol.KindVariable,
			Type:      t,
			Slot:      slot,
			Parameter: true,
			Defined:   true,
		}
		c.scope.Declare(sym)
		slot++
	}

	// Set current function for multi-return checking
	c.currentFuncIdx = funcIdx

	// If this is a method, declare struct fields in scope
	oldStruct := c.currentStruct
	c.currentStruct = nil
	if fn.StructName != "" {
		if structType, ok := c.structTypes[fn.StructName]; ok {
			c.currentStruct = structType
			for i, field := range structType.StructFields {
				c.scope.Declare(&symbol.Symbol{
					Name:          field.Name,
					Kind:          symbol.KindVariable,
					Type:          field.Type,
					Slot:          0,
					Defined:       true,
					Mut:           field.IsMut,
					IsStructField: true,
					FieldIndex:    i,
					FieldOfSlot:   0,
				})
			}
		}
	}

	// Check body
	ifVal := c.checkBlock(fn.Body, retType, true)
	c.currentStruct = oldStruct

	// Check return paths
	if len(fn.ReturnTypes) > 0 {
		if !ifVal {
			if len(fn.ReturnTypes) == 1 {
				c.diags.AddError("C001", fmt.Sprintf("missing return in function '%s' returning %s", fn.Name, retType.Named()), fn.Span())
			} else {
				c.diags.AddError("C001", fmt.Sprintf("missing return in function '%s' returning %d values", fn.Name, len(fn.ReturnTypes)), fn.Span())
			}
		}
	}

	c.currentFuncIdx = -1
	c.scope = oldScope
}

// checkBlock checks a block and returns whether all paths return.
func (c *Checker) checkBlock(block *ast.Block, retType *types.Type, createScope bool) bool {
	if block == nil {
		return false
	}

	if createScope {
		oldScope := c.scope
		c.scope = symbol.NewScope(oldScope, c.scope.FuncType)
		defer func() { c.scope = oldScope }()
	}

	allReturn := false
	for _, stmt := range block.Statements {
		if allReturn {
			c.diags.AddWarning("C002", "unreachable statement", stmt.Span())
			break
		}
		result := c.checkStatement(stmt, retType)
		// A statement that definitely exits (return, break, continue) makes
		// subsequent statements unreachable
		if result {
			allReturn = true
		}
	}
	return allReturn
}

// checkStatement checks a statement and returns whether it definitely returns.
func (c *Checker) checkStatement(stmt ast.Statement, retType *types.Type) bool {
	if stmt == nil {
		return false
	}
	switch s := stmt.(type) {
	case *ast.VariableDecl:
		return c.checkVarDecl(s)
	case *ast.ExprStmt:
		if s.Expr == nil {
			return false
		}
		if be, ok := s.Expr.(*ast.BinaryExpr); ok && be.Operator == ast.BinAssign {
			// Assignment
			return c.checkAssignment(be)
		}
		c.checkExpr(s.Expr, nil)
		return false
	case *ast.Block:
		return c.checkBlock(s, retType, true)
	case *ast.IfStmt:
		return c.checkIfStmt(s, retType)
	case *ast.WhileStmt:
		return c.checkWhileStmt(s, retType)
	case *ast.ForStmt:
		return c.checkForStmt(s, retType)
	case *ast.TryStmt:
		return c.checkTryStmt(s, retType)
	case *ast.ThrowStmt:
		return c.checkThrowStmt(s, retType)
	case *ast.SwitchStmt:
		return c.checkSwitchStmt(s, retType)
	case *ast.ReturnStmt:
		return c.checkReturnStmt(s, retType)
	case *ast.BreakStmt:
		return c.loopDepth > 0 // definitely exits if inside a loop
	case *ast.ContinueStmt:
		return c.loopDepth > 0 // definitely exits if inside a loop
	case *ast.AssignStmt:
		t := c.checkExpr(s.Value, nil)
		if t != nil {
			sym := c.scope.Resolve(s.Name)
			if sym != nil {
				if !sym.Mut {
					c.diags.AddError("C045",
						fmt.Sprintf("cannot assign to immutable variable '%s'; consider adding 'mut'", sym.Name),
						s.Span())
					return false
				}
				if sym.Type != nil {
					_ = sym.Type.IsAssignableFrom(t)
				}
			}
		}
		return false
	}
	return false
}

// checkVarDecl checks a variable declaration.
func (c *Checker) checkVarDecl(decl *ast.VariableDecl) bool {
	// Resolve any enum type annotation
	c.resolveEnumTypeAnnotation(decl.Type)

	var declaredType *types.Type
	if decl.Type != nil && decl.Type.ResolvedType != nil {
		declaredType = decl.Type.ResolvedType
	}

	var initType *types.Type
	if decl.InitExpr != nil {
		// Pass the declared type as expected for better type inference (e.g., empty lists)
		initType = c.checkExpr(decl.InitExpr, declaredType)
	}

	if declaredType != nil && declaredType.IsValid() {
		if initType != nil {
			// Check null assignment to non-nullable reference type (even when initType is invalid/KindInvalid)
			if initType.IsNull() && declaredType.IsReferenceType() && !declaredType.IsNullable() {
				c.diags.AddError("C032",
					fmt.Sprintf("cannot assign null to non-nullable type %s", declaredType.Named()),
					decl.Span())
			} else if initType.IsValid() {
				if !declaredType.IsAssignableFrom(initType) {
					c.diags.AddError("C003",
						fmt.Sprintf("cannot assign %s to %s in variable declaration", initType.Named(), declaredType.Named()),
						decl.Span())
				}
			}
		}
	}

	// Declare in scope with type
	sym := &symbol.Symbol{
		Name:    decl.Name,
		Kind:    symbol.KindVariable,
		Type:    declaredType,
		Slot:    -1,
		Defined: decl.InitExpr != nil,
		Mut:     decl.IsMut,
	}
	c.scope.Declare(sym)
	return false
}

// checkAssignment checks an assignment expression.
func (c *Checker) checkAssignment(be *ast.BinaryExpr) bool {
	valType := c.checkExpr(be.Right, nil)
	if valType == nil || !valType.IsValid() {
		return false
	}

	switch left := be.Left.(type) {
	case *ast.Identifier:
		return c.checkIdentAssignment(left, valType, be.Span())
	case *ast.IndexExpr:
		return c.checkIndexAssignment(left, valType, be.Span())
	case *ast.MemberExpr:
		return c.checkFieldAssignment(left, valType, be.Span())
	default:
		c.diags.AddError("C004", "left side of assignment must be an identifier or index expression", be.Left.Span())
		return false
	}
}

// checkIdentAssignment checks assignment to an identifier.
func (c *Checker) checkIdentAssignment(ident *ast.Identifier, valType *types.Type, span source.Span) bool {
	sym := c.scope.Resolve(ident.Name)
	if sym == nil {
		c.diags.AddError("C005", "undeclared variable: "+ident.Name, ident.Span())
		return false
	}

	// For struct field assignments inside methods, check field mutability
	if sym.IsStructField && !sym.Mut {
		c.diags.AddError("C045",
			fmt.Sprintf("cannot assign to immutable field '%s'; consider adding 'mut'", sym.Name),
			span)
		return false
	}

	// Cannot assign to immutable variable (non-field)
	if !sym.IsStructField && !sym.Mut {
		c.diags.AddError("C045",
			fmt.Sprintf("cannot assign to immutable variable '%s'; consider adding 'mut'", sym.Name),
			span)
		return false
	}

	if sym.Type != nil && sym.Type.IsValid() {
		// Check null assignment to non-nullable reference type
		if valType.IsNull() && sym.Type.IsReferenceType() && !sym.Type.IsNullable() {
			c.diags.AddError("C033",
				fmt.Sprintf("cannot assign null to non-nullable type %s", sym.Type.Named()),
				span)
		}

		if !sym.Type.IsAssignableFrom(valType) {
			c.diags.AddError("C006",
				fmt.Sprintf("cannot assign %s to %s", valType.Named(), sym.Type.Named()),
				span)
		}
	}

	// Mark as definitely assigned
	sym.Defined = true
	return false
}

// checkIndexAssignment checks assignment to an index expression (list[index] or map[key]).
func (c *Checker) checkIndexAssignment(indexExpr *ast.IndexExpr, valType *types.Type, span source.Span) bool {
	targetType := c.checkExpr(indexExpr.Target, nil)
	indexType := c.checkExpr(indexExpr.Index, nil)

	if !targetType.IsValid() || !indexType.IsValid() {
		return false
	}

	if targetType.Kind == types.KindList {
		if !indexType.IsInteger() {
			c.diags.AddError("C025", "list index must be an integer", indexExpr.Index.Span())
			return false
		}
		if targetType.Element != nil && !targetType.Element.IsAssignableFrom(valType) {
			c.diags.AddError("C035",
				fmt.Sprintf("cannot assign %s to list element of type %s", valType.Named(), targetType.Element.Named()),
				span)
		}
		return false
	}

	if targetType.Kind == types.KindMap {
		if targetType.KeyType != nil && !targetType.KeyType.IsAssignableFrom(indexType) {
			c.diags.AddError("C036",
				fmt.Sprintf("cannot use %s as map key of type %s", indexType.Named(), targetType.KeyType.Named()),
				indexExpr.Index.Span())
		}
		if targetType.ValueType != nil && !targetType.ValueType.IsAssignableFrom(valType) {
			c.diags.AddError("C037",
				fmt.Sprintf("cannot assign %s to map value of type %s", valType.Named(), targetType.ValueType.Named()),
				span)
		}
		return false
	}

	c.diags.AddError("C038", fmt.Sprintf("cannot index-assign to %s", targetType.Named()), span)
	return false
}

// checkFieldAssignment checks assignment to a struct field.
func (c *Checker) checkFieldAssignment(member *ast.MemberExpr, valType *types.Type, span source.Span) bool {
	objType := c.checkExpr(member.Object, nil)
	if objType == nil || !objType.IsValid() || objType.Kind != types.KindStruct {
		c.diags.AddError("C069", "cannot assign to field of non-struct value", span)
		return false
	}

	// Find the field
	var fieldType *types.Type
	var fieldIsMut bool
	var fieldIsPub bool
	found := false
	for _, field := range objType.StructFields {
		if field.Name == member.Member {
			fieldType = field.Type
			fieldIsMut = field.IsMut
			fieldIsPub = field.IsPub
			found = true
			break
		}
	}

	if !found {
		c.diags.AddError("C065",
			fmt.Sprintf("struct '%s' has no field '%s'", objType.StructName, member.Member),
			span)
		return false
	}

	// Check visibility
	insideSameStruct := c.currentStruct != nil && c.currentStruct.StructName == objType.StructName
	if !insideSameStruct && !fieldIsPub {
		c.diags.AddError("C071",
			fmt.Sprintf("field '%s' of struct '%s' is private", member.Member, objType.StructName),
			span)
		return false
	}

	// Check field mutability
	if !fieldIsMut {
		c.diags.AddError("C070",
			fmt.Sprintf("cannot assign to immutable field '%s' of struct '%s'", member.Member, objType.StructName),
			span)
		return false
	}

	// Check type compatibility
	if fieldType != nil && fieldType.IsValid() && valType != nil && valType.IsValid() {
		if !fieldType.IsAssignableFrom(valType) {
			c.diags.AddError("C006",
				fmt.Sprintf("cannot assign %s to field '%s' of type %s",
					valType.Named(), member.Member, fieldType.Named()),
				span)
		}
	}

	return false
}

// checkMultiAssign checks a multi-target assignment: a, b = expr.
func (c *Checker) checkMultiAssign(expr *ast.MultiAssignExpr) *types.Type {
	valType := c.checkExpr(expr.Value, nil)
	if valType == nil || !valType.IsValid() {
		return types.Void
	}

	// Check that value is a call expression (the only thing that can return multiple values)
	if _, ok := expr.Value.(*ast.CallExpr); !ok {
		c.diags.AddError("C050",
			"multi-target assignment requires a function call that returns multiple values",
			expr.Span())
		return types.Void
	}

	// Validate each target name exists and is mutable
	for _, name := range expr.Names {
		sym := c.scope.Resolve(name)
		if sym == nil {
			c.diags.AddError("C005", "undeclared variable: "+name, expr.Span())
			continue
		}
		if !sym.Mut {
			c.diags.AddError("C045",
				fmt.Sprintf("cannot assign to immutable variable '%s'; consider adding 'mut'", name),
				expr.Span())
		}
		sym.Defined = true
	}

	return types.Void
}

// checkIfStmt checks an if statement.
func (c *Checker) checkIfStmt(stmt *ast.IfStmt, retType *types.Type) bool {
	if stmt.Condition != nil {
		condType := c.checkExpr(stmt.Condition, nil)
		if condType != nil && !condType.IsBool() {
			c.diags.AddError("C007", "if condition must be bool", stmt.Condition.Span())
		}

		// Check for null-comparison narrowing: if x != null
		if be, ok := stmt.Condition.(*ast.BinaryExpr); ok && be.Operator == ast.BinNe {
			if right, ok := be.Right.(*ast.NullLiteral); ok {
				_ = right
				if ident, ok := be.Left.(*ast.Identifier); ok {
					if sym := c.scope.Resolve(ident.Name); sym != nil && sym.Type != nil && sym.Type.IsNullable() {
						// Set narrowing context for the then branch
						c.narrowingVar = ident.Name
						c.narrowedType = sym.Type.WithoutNullable()
					}
				}
			}
		}
	}

	thenReturns := false
	if stmt.Then != nil {
		thenReturns = c.checkBlock(stmt.Then, retType, true)
	}

	// Clear narrowing after then branch
	savedNarrowingVar := c.narrowingVar
	savedNarrowedType := c.narrowedType
	c.narrowingVar = ""
	c.narrowedType = nil

	elseReturns := false
	if stmt.Else != nil {
		elseReturns = c.checkBlock(stmt.Else, retType, true)
	}

	// Restore narrowing for else-if chains
	c.narrowingVar = savedNarrowingVar
	c.narrowedType = savedNarrowedType

	// All paths return only if then AND (all else-if AND else) return
	if thenReturns && elseReturns {
		allElseIfReturn := true
		for _, ei := range stmt.ElseIf {
			if !c.checkIfStmt(ei, retType) {
				allElseIfReturn = false
			}
		}
		if allElseIfReturn {
			return true
		}
	}
	return false
}

// checkWhileStmt checks a while loop.
func (c *Checker) checkWhileStmt(stmt *ast.WhileStmt, retType *types.Type) bool {
	if stmt.Condition != nil {
		condType := c.checkExpr(stmt.Condition, nil)
		if condType != nil && !condType.IsBool() {
			c.diags.AddError("C008", "while condition must be bool", stmt.Condition.Span())
		}
	}

	c.loopDepth++
	if stmt.Body != nil {
		c.checkBlock(stmt.Body, retType, true)
	}
	c.loopDepth--
	return false
}

// checkForStmt checks a for-in loop.
func (c *Checker) checkForStmt(stmt *ast.ForStmt, retType *types.Type) bool {
	iterType := c.checkExpr(stmt.Iterable, nil)

	oldScope := c.scope
	c.scope = symbol.NewScope(oldScope, c.scope.FuncType)

	// Declare loop variable(s)
	var keyType, valType *types.Type

	if iterType != nil && iterType.Kind == types.KindList && iterType.Element != nil {
		keyType = types.Int // index
		valType = iterType.Element
	} else if iterType != nil && iterType.IsString() {
		keyType = types.Int // index
		valType = types.Char
	} else if iterType != nil && iterType.Kind == types.KindMap {
		keyType = iterType.KeyType
		valType = iterType.ValueType
	} else {
		c.diags.AddError("C009", "for-in requires a List, string, or Map", stmt.Iterable.Span())
	}

	// For single-variable for, iterate over keys (for maps) or elements (for lists)
	if stmt.ValueVariable == "" {
		// Single variable: iterates over elements for List, keys for Map, chars for string
		var varType *types.Type
		if iterType != nil && iterType.Kind == types.KindMap {
			varType = keyType // iterate over keys
		} else {
			varType = valType // iterate over elements
		}
		if varType == nil {
			varType = types.Invalid
		}
		c.scope.Declare(&symbol.Symbol{
			Name:    stmt.Variable,
			Kind:    symbol.KindVariable,
			Type:    varType,
			Slot:    -1,
			Defined: true,
		})
	} else {
		// (key, value) unpacking: used for maps
		if iterType == nil || iterType.Kind != types.KindMap {
			c.diags.AddError("C039", "(key, value) unpacking requires a Map", stmt.Iterable.Span())
		}
		if keyType == nil {
			keyType = types.Invalid
		}
		if valType == nil {
			valType = types.Invalid
		}
		c.scope.Declare(&symbol.Symbol{
			Name:    stmt.Variable,
			Kind:    symbol.KindVariable,
			Type:    keyType,
			Slot:    -1,
			Defined: true,
		})
		c.scope.Declare(&symbol.Symbol{
			Name:    stmt.ValueVariable,
			Kind:    symbol.KindVariable,
			Type:    valType,
			Slot:    -1,
			Defined: true,
		})
	}

	c.loopDepth++
	if stmt.Body != nil {
		c.checkBlock(stmt.Body, retType, false)
	}
	c.loopDepth--

	c.scope = oldScope
	return false
}

// checkSwitchStmt checks a switch statement.
func (c *Checker) checkSwitchStmt(stmt *ast.SwitchStmt, retType *types.Type) bool {
	if stmt.Expression != nil {
		c.checkExpr(stmt.Expression, nil)
	}
	allReturn := true
	for _, cse := range stmt.Cases {
		if cse.Expression != nil {
			c.checkExpr(cse.Expression, nil)
		}
		if cse.Body != nil {
			caseRet := c.checkBlock(cse.Body, retType, true)
			if !caseRet {
				allReturn = false
			}
		}
	}
	if stmt.Default != nil {
		defRet := c.checkBlock(stmt.Default, retType, true)
		if !defRet {
			allReturn = false
		}
	} else {
		allReturn = false // no default means not all paths return
	}
	return allReturn
}

// checkTryStmt checks a try/catch/finally statement.
func (c *Checker) checkTryStmt(stmt *ast.TryStmt, retType *types.Type) bool {
	// Check try body
	tryReturns := false
	if stmt.TryBody != nil {
		tryReturns = c.checkBlock(stmt.TryBody, retType, true)
	}

	catchReturns := false
	if stmt.Catch != nil {
		// Verify catch parameter type is exactly exception
		if stmt.Catch.ParamType != nil && stmt.Catch.ParamType.ResolvedType != nil {
			ptype := stmt.Catch.ParamType.ResolvedType
			if !ptype.IsException() || ptype.IsNullable() {
				c.diags.AddError("C042",
					"catch parameter must have type exception",
					stmt.Catch.Span())
			}
		}

		// Catch clause scope with catch parameter
		oldScope := c.scope
		c.scope = symbol.NewScope(oldScope, c.scope.FuncType)

		// Declare catch parameter
		paramType := types.Exception
		if stmt.Catch.ParamType != nil && stmt.Catch.ParamType.ResolvedType != nil {
			paramType = stmt.Catch.ParamType.ResolvedType
		}
		c.scope.Declare(&symbol.Symbol{
			Name:    stmt.Catch.ParamName,
			Kind:    symbol.KindVariable,
			Type:    paramType,
			Slot:    -1,
			Defined: true,
		})

		// Check catch body
		if stmt.Catch.Body != nil {
			catchReturns = c.checkBlock(stmt.Catch.Body, retType, false)
		}

		c.scope = oldScope
	}

	// Check finally body
	finallyReturns := false
	if stmt.Finally != nil {
		finallyReturns = c.checkBlock(stmt.Finally, retType, true)
	}

	// If finally always returns/throws, the whole try statement always returns
	if finallyReturns {
		return true
	}

	// If both try and catch return, and we have both, the whole statement returns
	if stmt.Catch != nil && tryReturns && catchReturns {
		return true
	}

	// If try returns and there's no catch (just finally), not all paths return
	// because an exception from try would skip catch and go to finally then propagate
	if stmt.Catch == nil && tryReturns && stmt.Finally != nil {
		return false
	}

	return tryReturns && catchReturns
}

// checkThrowStmt checks a throw statement.
func (c *Checker) checkThrowStmt(stmt *ast.ThrowStmt, retType *types.Type) bool {
	if stmt.Value != nil {
		exprType := c.checkExpr(stmt.Value, nil)
		if exprType != nil && exprType.IsValid() {
			if !exprType.IsException() && !exprType.IsString() {
				c.diags.AddError("C043",
					"throw expression must have type exception or string",
					stmt.Value.Span())
			} else if exprType.IsNullable() {
				c.diags.AddError("C044",
					"cannot throw nullable exception",
					stmt.Value.Span())
			}
		}
	}
	// throw never completes normally
	return true
}

// checkReturnStmt checks a return statement against the expected return types.
func (c *Checker) checkReturnStmt(stmt *ast.ReturnStmt, retType *types.Type) bool {
	// Get the function's return types from current function context
	var retTypes []*ast.TypeAnnotation
	if c.currentFuncIdx >= 0 && c.currentFuncIdx < len(c.funcs) {
		retTypes = c.funcs[c.currentFuncIdx].ReturnTypes
	}

	valCount := len(stmt.Values)
	expectedCount := len(retTypes)

	// If no explicit return types in context, use the single retType parameter
	if expectedCount == 0 && retType != nil && !retType.IsVoid() {
		expectedCount = 1
	}

	// Void function: reject return with values
	if expectedCount == 0 {
		if valCount > 0 {
			c.diags.AddError("C010",
				fmt.Sprintf("function returns no values but return statement has %d", valCount),
				stmt.Span())
		}
		return true
	}

	// Return without value in non-void function
	if valCount == 0 {
		if expectedCount == 1 {
			c.diags.AddError("C011",
				"missing return value in function returning a value",
				stmt.Span())
		} else {
			c.diags.AddError("C011",
				fmt.Sprintf("missing return value in function returning %d values", expectedCount),
				stmt.Span())
		}
		return true
	}

	// Count mismatch
	if valCount != expectedCount {
		c.diags.AddError("C010",
			fmt.Sprintf("function returns %d values but return statement has %d", expectedCount, valCount),
			stmt.Span())
		return true
	}

	// Check each value against the corresponding return type
	for i, val := range stmt.Values {
		valType := c.checkExpr(val, nil)
		if valType == nil || !valType.IsValid() {
			continue
		}
		var expectedType *types.Type
		if i < len(retTypes) && retTypes[i] != nil && retTypes[i].ResolvedType != nil {
			expectedType = retTypes[i].ResolvedType
		}
		if expectedType == nil || !expectedType.IsValid() {
			continue
		}
		if !expectedType.IsAssignableFrom(valType) {
			c.diags.AddError("C010",
				fmt.Sprintf("cannot return %s as value %d: expected %s", valType.Named(), i+1, expectedType.Named()),
				val.Span())
		}
	}
	return true
}

// checkExpr checks an expression and returns its type.
// The expected parameter is used for context (e.g., in list literals).
func (c *Checker) checkExpr(expr ast.Expression, expected *types.Type) *types.Type {
	if expr == nil {
		return types.Invalid
	}

	var t *types.Type
	switch e := expr.(type) {
	case *ast.IntLiteral:
		t = types.Int
	case *ast.FloatLiteral:
		t = types.Float
	case *ast.BoolLiteral:
		t = types.Bool
	case *ast.CharLiteral:
		t = types.Char
	case *ast.StringLiteral:
		t = types.String
	case *ast.ByteLiteral:
		t = types.Byte
	case *ast.NullLiteral:
		// Determine null type based on expected context
		if expected != nil && expected.IsNullable() {
			t = types.NullableOf(expected)
		} else {
			t = &types.Type{Kind: types.KindInvalid} // null without context
		}
	case *ast.Identifier:
		t = c.checkIdentifier(e)
	case *ast.UnaryExpr:
		t = c.checkUnary(e)
	case *ast.BinaryExpr:
		t = c.checkBinary(e)
	case *ast.MultiAssignExpr:
		t = c.checkMultiAssign(e)
	case *ast.CallExpr:
		t = c.checkCall(e)
	case *ast.IndexExpr:
		t = c.checkIndex(e)
	case *ast.ListLiteral:
		// Pass expected type for empty list inference
		if expected == nil {
			// Try to infer from the declaration context by looking up the type annotation
			_ = expected
		}
		t = c.checkListLiteral(e, expected)
	case *ast.MapLiteral:
		t = c.checkMapLiteral(e)
	case *ast.MemberExpr:
		t = c.checkMemberExpr(e)
	case *ast.StructLiteral:
		t = c.checkStructLiteral(e)
	case *ast.NullCoalescing:
		t = c.checkNullCoalescing(e)
	case *ast.SpreadExpr:
		t = c.checkExpr(e.Expr, nil)
	}

	// Store the resolved type on the expression node for the compiler to use
	if expr != nil && t != nil {
		if sn, ok := expr.(interface{ SetExprType(*types.Type) }); ok {
			sn.SetExprType(t)
		}
	}

	return t
}

// checkIdentifier checks an identifier reference.
func (c *Checker) checkIdentifier(ident *ast.Identifier) *types.Type {
	sym := c.scope.Resolve(ident.Name)
	if sym == nil {
		// Check if it's a builtin function
		if fnType, exists := builtinFuncs[ident.Name]; exists {
			return fnType
		}
		// Check if it's a user-defined function (current module)
		if idx, exists := c.funcMap[ident.Name]; exists {
			fn := c.funcs[idx]
			return c.funcToType(fn)
		}

		// Check for functions from the same module only
		if c.allFuncs != nil {
			for mangledName, fn := range c.allFuncs {
				dotIdx := -1
				for i := len(mangledName) - 1; i >= 0; i-- {
					if mangledName[i] == '.' {
						dotIdx = i
						break
					}
				}
				if dotIdx >= 0 && mangledName[dotIdx+1:] == ident.Name && mangledName[:dotIdx] == c.moduleName {
					return c.funcToType(fn)
				}
			}
		}

		return types.Invalid
	}

	// Definite assignment check: if variable is not defined and is not a parameter
	if !sym.Parameter && !sym.Defined && !sym.IsStructField {
		c.diags.AddError("C032", fmt.Sprintf("variable '%s' may not have been assigned", sym.Name), ident.Span())
	}

	// For struct field references inside methods, return the field type
	if sym.IsStructField {
		return sym.Type
	}

	// For module symbols that also have builtin function definitions (like "string"),
	// prefer the builtin function type since the user is calling it as a function
	if sym != nil && sym.Kind == symbol.KindModule {
		if fnType, exists := builtinFuncs[sym.Name]; exists {
			return fnType
		}
	}

	// Apply null narrowing: if this variable is the one being narrowed, return the narrowed type
	if sym != nil && sym.Name == c.narrowingVar && c.narrowedType != nil {
		return c.narrowedType
	}

	return sym.Type
}

// checkUnary checks a unary expression.
func (c *Checker) checkUnary(expr *ast.UnaryExpr) *types.Type {
	operandType := c.checkExpr(expr.Operand, nil)
	if !operandType.IsValid() {
		return types.Invalid
	}

	switch expr.Operator {
	case ast.UnaryNegate:
		if !operandType.IsNumeric() {
			c.diags.AddError("C012", "cannot negate non-numeric type", expr.Span())
			return types.Invalid
		}
		return operandType
	case ast.UnaryNot:
		if !operandType.IsBool() {
			c.diags.AddError("C013", "cannot apply ! to non-bool type", expr.Span())
			return types.Invalid
		}
		return types.Bool
	case ast.UnaryBitNot:
		if !operandType.IsInteger() {
			c.diags.AddError("C014", "cannot apply ~ to non-integer type", expr.Span())
			return types.Invalid
		}
		return operandType
	}
	return types.Invalid
}

// checkBinary checks a binary expression.
func (c *Checker) checkBinary(expr *ast.BinaryExpr) *types.Type {
	// Handle assignment specially
	if expr.Operator == ast.BinAssign {
		c.checkAssignment(expr)
		return types.Void
	}

	leftType := c.checkExpr(expr.Left, nil)
	rightType := c.checkExpr(expr.Right, nil)

	// String concatenation with ++ accepts all types (including null) by converting to string
	if expr.Operator == ast.BinStrConcat {
		return types.String
	}

	// Allow null comparisons (BinEq, BinNe) even when one side is null (KindInvalid)
	if !leftType.IsValid() || !rightType.IsValid() {
		if expr.Operator == ast.BinEq || expr.Operator == ast.BinNe {
			// Null comparison is valid, returns bool
			return types.Bool
		}
		return types.Invalid
	}

	switch expr.Operator {
	case ast.BinAdd, ast.BinSub, ast.BinMul, ast.BinDiv, ast.BinMod:
		if !leftType.IsNumeric() || !rightType.IsNumeric() {
			c.diags.AddError("C015", fmt.Sprintf("cannot apply %s to %s and %s", expr.Operator, leftType.Named(), rightType.Named()), expr.Span())
			return types.Invalid
		}
		return types.CommonNumericType(leftType, rightType)

	case ast.BinEq, ast.BinNe:
		// Equality: same type or comparable
		if leftType.Kind == types.KindEnum && rightType.Kind == types.KindEnum {
			// Both are enums: must be the same enum type for comparison
			if leftType.EnumName != rightType.EnumName {
				c.diags.AddError("C016", fmt.Sprintf("cannot compare %s and %s with ==", leftType.Named(), rightType.Named()), expr.Span())
			}
		} else if leftType.Kind != rightType.Kind && leftType.Kind != types.KindInvalid && rightType.Kind != types.KindInvalid {
			// Allow null comparison
			if leftType.Kind == types.KindInvalid || rightType.Kind == types.KindInvalid {
				// null comparison is fine
			} else if leftType.IsNumeric() && rightType.IsNumeric() {
				// Numeric comparison across types is fine (e.g., int vs long, enum vs int)
			} else {
				c.diags.AddError("C016", fmt.Sprintf("cannot compare %s and %s with ==", leftType.Named(), rightType.Named()), expr.Span())
			}
		}
		return types.Bool

	case ast.BinLt, ast.BinLe, ast.BinGt, ast.BinGe:
		if !leftType.IsNumeric() || !rightType.IsNumeric() {
			c.diags.AddError("C017", fmt.Sprintf("cannot apply %s to %s and %s", expr.Operator, leftType.Named(), rightType.Named()), expr.Span())
			return types.Invalid
		}
		// Allow mixed numeric types (e.g., int compared to long)
		return types.Bool

	case ast.BinAnd, ast.BinOr:
		if !leftType.IsBool() || !rightType.IsBool() {
			c.diags.AddError("C018", fmt.Sprintf("cannot apply %s to non-bool types", expr.Operator), expr.Span())
			return types.Invalid
		}
		return types.Bool

	case ast.BinBitAnd, ast.BinBitOr, ast.BinBitXor:
		if !leftType.IsInteger() || !rightType.IsInteger() {
			c.diags.AddError("C019", fmt.Sprintf("cannot apply %s to non-integer types", expr.Operator), expr.Span())
			return types.Invalid
		}
		return types.CommonNumericType(leftType, rightType)

	case ast.BinShiftLeft, ast.BinShiftRight:
		if !leftType.IsInteger() || !rightType.IsInteger() {
			c.diags.AddError("C020", "shift requires integer operands", expr.Span())
			return types.Invalid
		}
		return leftType

	case ast.BinStrConcat:
		return types.String
	}

	return types.Invalid
}

// checkCall checks a function call expression.
func (c *Checker) checkCall(expr *ast.CallExpr) *types.Type {
	// Check for struct construction: Point(3, 4)
	if ident, ok := expr.Function.(*ast.Identifier); ok {
		sym := c.scope.Resolve(ident.Name)
		if sym != nil && sym.Type != nil && sym.Type.Kind == types.KindStruct {
			return c.checkStructConstruction(sym.Type, expr)
		}
		// Check for unqualified method call inside a struct method: validate()
		if c.currentStruct != nil && c.currentStruct.StructMethods != nil {
			if mi, ok := c.currentStruct.StructMethods[ident.Name]; ok {
				fnType := mi.Signature
				// Implicit self arg already counted in the signature
				paramCount := len(fnType.Params) - 1
				if len(expr.Args) != paramCount {
					c.diags.AddError("C023",
						fmt.Sprintf("expected %d arguments but got %d", paramCount, len(expr.Args)),
						expr.Span())
				}
				for i, arg := range expr.Args {
					expectedType := fnType.Params[i+1]
					argType := c.checkExpr(arg, expectedType)
					if expectedType != nil && expectedType.IsValid() && argType != nil && argType.IsValid() {
						if !expectedType.IsAssignableFrom(argType) {
							c.diags.AddError("C024",
								fmt.Sprintf("argument %d: expected %s but got %s", i+1, expectedType.Named(), argType.Named()),
								arg.Span())
						}
					}
				}
				return fnType.Return
			}
		}
	}

	// Check for method calls: p.move(10, 20)
	if member, ok := expr.Function.(*ast.MemberExpr); ok {
		objType := c.checkExpr(member.Object, nil)
		if objType != nil && objType.Kind == types.KindStruct {
			return c.checkMethodCall(objType, member, expr)
		}
		// Check for trait method calls: shape.draw()
		if objType != nil && objType.Kind == types.KindTrait {
			return c.checkTraitMethodCall(objType, member, expr)
		}
	}

	fnType := c.checkExpr(expr.Function, nil)
	if !fnType.IsValid() || fnType.Kind != types.KindFunction {
		c.diags.AddError("C022", "called expression is not a function", expr.Span())
		return types.Invalid
	}

	// Determine variadic behavior
	isVariadic := fnType.Variadic
	fixedCount := len(fnType.Params)
	variadicElemType := types.Invalid
	if isVariadic {
		fixedCount-- // last param is the variadic element type
		if fixedCount >= 0 && fnType.Params[fixedCount] != nil {
			variadicElemType = fnType.Params[fixedCount]
		}
	}

	// Check argument count
	if !isVariadic && len(expr.Args) != len(fnType.Params) {
		c.diags.AddError("C023",
			fmt.Sprintf("expected %d arguments but got %d", len(fnType.Params), len(expr.Args)),
			expr.Span())
		return types.Invalid
	}
	if isVariadic && len(expr.Args) < fixedCount {
		c.diags.AddError("C023",
			fmt.Sprintf("expected at least %d arguments but got %d", fixedCount, len(expr.Args)),
			expr.Span())
		return types.Invalid
	}

	// Check fixed params
	for i := 0; i < fixedCount && i < len(expr.Args); i++ {
		expectedType := fnType.Params[i]
		argType := c.checkExpr(expr.Args[i], expectedType)
		if argType.IsValid() && expectedType != nil && expectedType.IsValid() {
			if !expectedType.IsAssignableFrom(argType) {
				c.diags.AddError("C024",
					fmt.Sprintf("argument %d: expected %s but got %s", i+1, expectedType.Named(), argType.Named()),
					expr.Args[i].Span())
			}
		}
	}

	// Check variadic args
	if isVariadic {
		for i := fixedCount; i < len(expr.Args); i++ {
			arg := expr.Args[i]
			// Check for spread expression
			if spread, ok := arg.(*ast.SpreadExpr); ok {
				spreadType := c.checkExpr(spread.Expr, nil)
				if spreadType != nil && spreadType.Kind == types.KindList {
					if !variadicElemType.IsAssignableFrom(spreadType.Element) {
						c.diags.AddError("C055",
							fmt.Sprintf("cannot spread %s into %s", spreadType.Named(), fnType.Named()),
							spread.Span())
					}
				} else {
					c.diags.AddError("C056",
						"spread expression must be a List",
						spread.Span())
				}
			} else {
				argType := c.checkExpr(arg, variadicElemType)
				if argType.IsValid() && variadicElemType.IsValid() {
					if !variadicElemType.IsAssignableFrom(argType) {
						c.diags.AddError("C024",
							fmt.Sprintf("variadic argument %d: expected %s but got %s",
								i-fixedCount+1, variadicElemType.Named(), argType.Named()),
							arg.Span())
					}
				}
			}
		}
	}

	return fnType.Return
}

// checkIndex checks an indexing expression.
func (c *Checker) checkIndex(expr *ast.IndexExpr) *types.Type {
	targetType := c.checkExpr(expr.Target, nil)
	indexType := c.checkExpr(expr.Index, nil)

	if !targetType.IsValid() || !indexType.IsValid() {
		return types.Invalid
	}

	if targetType.Kind == types.KindList {
		if !indexType.IsInteger() {
			c.diags.AddError("C025", "list index must be an integer", expr.Index.Span())
			return types.Invalid
		}
		if targetType.Element != nil {
			return targetType.Element
		}
		return types.Invalid
	}

	if targetType.Kind == types.KindMap {
		return targetType.ValueType
	}

	if targetType.IsString() {
		if !indexType.IsInteger() {
			c.diags.AddError("C026", "string index must be an integer", expr.Index.Span())
			return types.Invalid
		}
		return types.Char
	}

	c.diags.AddError("C027", fmt.Sprintf("cannot index %s", targetType.Named()), expr.Span())
	return types.Invalid
}

// checkListLiteral checks a list literal.
func (c *Checker) checkListLiteral(expr *ast.ListLiteral, expected *types.Type) *types.Type {
	var elemType *types.Type
	if expected != nil && expected.Kind == types.KindList && expected.Element != nil {
		elemType = expected.Element
	}

	for _, el := range expr.Elements {
		t := c.checkExpr(el, elemType)
		if elemType == nil && t != nil && t.IsValid() {
			elemType = t
		}
	}

	if elemType == nil {
		elemType = types.Invalid
	}
	return types.ListOf(elemType)
}

// checkMapLiteral checks a map literal.
func (c *Checker) checkMapLiteral(expr *ast.MapLiteral) *types.Type {
	var keyType, valType *types.Type
	for i := range expr.Keys {
		kt := c.checkExpr(expr.Keys[i], nil)
		vt := c.checkExpr(expr.Values[i], nil)

		// Validate map key type
		if kt != nil && kt.IsValid() && !kt.IsValidMapKey() {
			c.diags.AddError("C034",
				fmt.Sprintf("invalid map key type: %s (allowed: bool, byte, int, char, string, enum)", kt.Named()),
				expr.Keys[i].Span())
		}

		// If the key is an enum variant, use the base enum type instead
		if kt != nil && kt.IsValid() && kt.Kind == types.KindEnum && kt.EnumVariant != "" {
			// Use base enum type (without variant)
			baseType := &types.Type{
				Kind:       types.KindEnum,
				EnumName:   kt.EnumName,
				EnumValues: kt.EnumValues,
			}
			kt = baseType
		}

		if keyType == nil && kt != nil && kt.IsValid() {
			keyType = kt
		}
		if valType == nil && vt != nil && vt.IsValid() {
			valType = vt
		}
	}

	if keyType == nil {
		keyType = types.Invalid
	}
	if valType == nil {
		valType = types.Invalid
	}
	return types.MapOf(keyType, valType)
}

// checkStructLiteral checks a named-field struct literal.
func (c *Checker) checkStructLiteral(expr *ast.StructLiteral) *types.Type {
	sym := c.scope.Resolve(expr.TypeName)
	if sym == nil || sym.Type == nil || sym.Type.Kind != types.KindStruct {
		c.diags.AddError("C060", "unknown struct type: "+expr.TypeName, expr.Span())
		return types.Invalid
	}
	structType := sym.Type

	// Build field index map
	fieldMap := make(map[string]int)
	for i, f := range structType.StructFields {
		fieldMap[f.Name] = i
	}

	// Check each named field
	provided := make(map[string]bool)
	for i, name := range expr.Fields {
		if _, exists := fieldMap[name]; !exists {
			c.diags.AddError("C061", fmt.Sprintf("struct '%s' has no field '%s'", expr.TypeName, name), expr.Span())
			continue
		}
		if provided[name] {
			c.diags.AddError("C062", fmt.Sprintf("duplicate field '%s' in struct literal", name), expr.Span())
			continue
		}
		provided[name] = true

		fieldIdx := fieldMap[name]
		fieldType := structType.StructFields[fieldIdx].Type
		valType := c.checkExpr(expr.Values[i], fieldType)

		if fieldType != nil && fieldType.IsValid() && valType != nil && valType.IsValid() {
			if !fieldType.IsAssignableFrom(valType) {
				c.diags.AddError("C063",
					fmt.Sprintf("cannot assign %s to field '%s' of type %s",
						valType.Named(), name, fieldType.Named()),
					expr.Values[i].Span())
			}
		}
	}

	// Check that all fields are provided
	for name := range fieldMap {
		if !provided[name] {
			c.diags.AddError("C064", fmt.Sprintf("missing field '%s' in struct literal", name), expr.Span())
		}
	}

	return structType
}

// checkNullCoalescing checks the ?? operator.
func (c *Checker) checkNullCoalescing(expr *ast.NullCoalescing) *types.Type {
	leftType := c.checkExpr(expr.Left, nil)
	if !leftType.IsValid() {
		return types.Invalid
	}
	if !leftType.IsNullable() {
		c.diags.AddError("C028", "left operand of ?? must be nullable", expr.Left.Span())
	}

	rightType := c.checkExpr(expr.Right, nil)

	if leftType.IsValid() && rightType.IsValid() {
		// The result is the non-nullable version of the left type, or the common type
		nonNullLeft := leftType.WithoutNullable()
		if nonNullLeft != nil && rightType.IsAssignableFrom(nonNullLeft) {
			return nonNullLeft
		}
		return rightType
	}

	return types.Invalid
}

// checkMemberExpr checks a member access expression (module.function).
func (c *Checker) checkMemberExpr(expr *ast.MemberExpr) *types.Type {
	// Determine if the object is a module
	var moduleName string
	isModule := false
	if ident, ok := expr.Object.(*ast.Identifier); ok {
		if sym := c.scope.Resolve(ident.Name); sym != nil && sym.Kind == symbol.KindModule {
			isModule = true
			moduleName = sym.ModuleName
		}
		// Also check known modules that might conflict with function names
		if !isModule {
			for _, mod := range []string{"core", "string", "math", "map", "env", "file", "process", "time", "random", "path"} {
				if ident.Name == mod {
					isModule = true
					moduleName = mod
					break
				}
			}
		}
	}

	if isModule {
		// Check if this is an enum type reference (e.g., Color.Red)
		if sym := c.scope.Resolve(moduleName); sym != nil && sym.Type != nil && sym.Type.Kind == types.KindEnum {
			enumType := sym.Type
			if enumType.EnumValues != nil {
				if _, exists := enumType.EnumValues[expr.Member]; exists {
					// This is an enum variant reference
					return types.EnumVariantType(enumType, expr.Member)
				}
			}
			// Enum type found but no such variant
			c.diags.AddError("C048",
				fmt.Sprintf("enum '%s' has no variant '%s'", moduleName, expr.Member),
				expr.Span())
			return types.Invalid
		}

		mangledName := moduleName + "." + expr.Member

		// Check for functions from other modules (allFuncs)
		if c.allFuncs != nil {
			if fn, exists := c.allFuncs[mangledName]; exists {
				return c.funcToType(fn)
			}
		}

		// Check local funcMap
		if idx, exists := c.funcMap[mangledName]; exists {
			fn := c.funcs[idx]
			return c.funcToType(fn)
		}

		// Check for builtin module functions using fully qualified name
		if fnType, exists := builtinFuncs[mangledName]; exists {
			return fnType
		}
		// Fall back to unqualified name for module member lookup
		if fnType, exists := builtinFuncs[expr.Member]; exists {
			return fnType
		}

		// Known module member not found
		c.diags.AddError("C041", fmt.Sprintf("module '%s' has no member '%s'", moduleName, expr.Member), expr.Span())
		return types.Invalid
	}

	// For non-module member access, resolve the object type
	objType := c.checkExpr(expr.Object, nil)

	// Struct field/method access
	if objType.Kind == types.KindStruct {
		insideSameStruct := c.currentStruct != nil && c.currentStruct.StructName == objType.StructName
		// Check for field access
		for _, field := range objType.StructFields {
			if field.Name == expr.Member {
				if !insideSameStruct && !field.IsPub {
					c.diags.AddError("C071",
						fmt.Sprintf("field '%s' of struct '%s' is private", field.Name, objType.StructName),
						expr.Span())
				}
				return field.Type
			}
		}
		// Check for method access
		if objType.StructMethods != nil {
			if mi, ok := objType.StructMethods[expr.Member]; ok {
				if !insideSameStruct && !mi.IsPub {
					c.diags.AddError("C072",
						fmt.Sprintf("method '%s' of struct '%s' is private", expr.Member, objType.StructName),
						expr.Span())
				}
				return mi.Signature
			}
		}
		c.diags.AddError("C065",
			fmt.Sprintf("struct '%s' has no field or method '%s'", objType.StructName, expr.Member),
			expr.Span())
		return types.Invalid
	}

	// Trait method access
	if objType.Kind == types.KindTrait {
		if objType.TraitMethods != nil {
			if mi, ok := objType.TraitMethods[expr.Member]; ok {
				return mi.Signature
			}
		}
		c.diags.AddError("C073",
			fmt.Sprintf("trait '%s' has no method '%s'", objType.TraitName, expr.Member),
			expr.Span())
		return types.Invalid
	}

	// Exception member access: e.message, e.trace
	if objType.IsException() {
		if expr.Member == "message" || expr.Member == "trace" {
			return types.String
		}
		c.diags.AddError("C040",
			fmt.Sprintf("exception has no member '%s'", expr.Member),
			expr.Span())
		return types.Invalid
	}

	c.diags.AddError("C040", fmt.Sprintf("cannot access member '%s' of %s", expr.Member, objType.Named()), expr.Span())
	return types.Invalid
}

// checkStructConstruction checks positional struct construction: Point(3, 4).
func (c *Checker) checkStructConstruction(structType *types.Type, expr *ast.CallExpr) *types.Type {
	fieldCount := len(structType.StructFields)
	if len(expr.Args) != fieldCount {
		c.diags.AddError("C066",
			fmt.Sprintf("struct '%s' has %d fields but %d arguments provided",
				structType.StructName, fieldCount, len(expr.Args)),
			expr.Span())
		return structType
	}

	for i, arg := range expr.Args {
		expectedType := structType.StructFields[i].Type
		argType := c.checkExpr(arg, expectedType)
		if expectedType != nil && expectedType.IsValid() && argType != nil && argType.IsValid() {
			if !expectedType.IsAssignableFrom(argType) {
				c.diags.AddError("C067",
					fmt.Sprintf("field '%s': expected %s but got %s",
						structType.StructFields[i].Name, expectedType.Named(), argType.Named()),
					arg.Span())
			}
		}
	}

	return structType
}

// checkMethodCall checks a method call on a struct: p.move(10, 20).
func (c *Checker) checkMethodCall(objType *types.Type, member *ast.MemberExpr, expr *ast.CallExpr) *types.Type {
	if objType.StructMethods == nil {
		c.diags.AddError("C065",
			fmt.Sprintf("struct '%s' has no method '%s'", objType.StructName, member.Member),
			expr.Span())
		return types.Invalid
	}

	mi, ok := objType.StructMethods[member.Member]
	if !ok {
		c.diags.AddError("C065",
			fmt.Sprintf("struct '%s' has no method '%s'", objType.StructName, member.Member),
			expr.Span())
		return types.Invalid
	}

	fnType := mi.Signature

	// Check visibility
	insideSameStruct := c.currentStruct != nil && c.currentStruct.StructName == objType.StructName
	if !insideSameStruct && !mi.IsPub {
		c.diags.AddError("C072",
			fmt.Sprintf("method '%s' of struct '%s' is private", member.Member, objType.StructName),
			expr.Span())
		return types.Invalid
	}

	// Check mutability: if any mutable field exists, calling a method requires mut receiver
	// For now, check if the receiver variable is mutable
	if ident, ok := member.Object.(*ast.Identifier); ok {
		sym := c.scope.Resolve(ident.Name)
		if sym != nil && !sym.Mut {
			// Check if the method actually mutates any mutable field
			// For simplicity, require mut receiver for all method calls
			// since we can't easily determine which methods mutate
			c.diags.AddError("C068",
				fmt.Sprintf("cannot call method on immutable struct variable '%s'; consider adding 'mut'", ident.Name),
				expr.Span())
		}
	}

	// Check argument count (method receives the struct as first implicit arg)
	// The caller does NOT pass the struct; the compiler adds it.
	// So we check args against params[1:] (skipping the struct param).
	paramCount := len(fnType.Params) - 1 // subtract the implicit struct parameter
	if len(expr.Args) != paramCount {
		c.diags.AddError("C023",
			fmt.Sprintf("expected %d arguments but got %d", paramCount, len(expr.Args)),
			expr.Span())
		return types.Invalid
	}

	for i, arg := range expr.Args {
		expectedType := fnType.Params[i+1] // skip implicit struct param
		argType := c.checkExpr(arg, expectedType)
		if expectedType != nil && expectedType.IsValid() && argType != nil && argType.IsValid() {
			if !expectedType.IsAssignableFrom(argType) {
				c.diags.AddError("C024",
					fmt.Sprintf("argument %d: expected %s but got %s", i+1, expectedType.Named(), argType.Named()),
					arg.Span())
			}
		}
	}

	return fnType.Return
}

// checkTraitMethodCall checks a method call on a trait value.
func (c *Checker) checkTraitMethodCall(traitType *types.Type, member *ast.MemberExpr, expr *ast.CallExpr) *types.Type {
	if traitType.TraitMethods == nil {
		c.diags.AddError("C073",
			fmt.Sprintf("trait '%s' has no methods", traitType.TraitName),
			expr.Span())
		return types.Invalid
	}

	mi, ok := traitType.TraitMethods[member.Member]
	if !ok {
		c.diags.AddError("C073",
			fmt.Sprintf("trait '%s' has no method '%s'", traitType.TraitName, member.Member),
			expr.Span())
		return types.Invalid
	}

	fnType := mi.Signature

	// Check argument count (trait methods don't have _self)
	if len(expr.Args) != len(fnType.Params) {
		c.diags.AddError("C023",
			fmt.Sprintf("expected %d arguments but got %d", len(fnType.Params), len(expr.Args)),
			expr.Span())
		return types.Invalid
	}

	for i, arg := range expr.Args {
		expectedType := fnType.Params[i]
		argType := c.checkExpr(arg, expectedType)
		if expectedType != nil && expectedType.IsValid() && argType != nil && argType.IsValid() {
			if !expectedType.IsAssignableFrom(argType) {
				c.diags.AddError("C024",
					fmt.Sprintf("argument %d: expected %s but got %s", i+1, expectedType.Named(), argType.Named()),
					arg.Span())
			}
		}
	}

	return fnType.Return
}

// funcToType converts a function AST node to a function type.
func (c *Checker) funcToType(fn *ast.Function) *types.Type {
	var params []*types.Type
	isVariadic := false
	for _, p := range fn.Parameters {
		t := types.Invalid
		if p.Type != nil && p.Type.ResolvedType != nil {
			t = p.Type.ResolvedType
		}
		if p.Variadic {
			isVariadic = true
		}
		params = append(params, t)
	}
	var ret *types.Type
	if len(fn.ReturnTypes) == 1 && fn.ReturnTypes[0] != nil && fn.ReturnTypes[0].ResolvedType != nil {
		ret = fn.ReturnTypes[0].ResolvedType
	} else {
		ret = types.Void
	}
	if isVariadic {
		return types.VariadicFunctionType(params, ret)
	}
	return types.FunctionType(params, ret)
}

// checkMain checks that the main function exists and is well-formed.
func (c *Checker) checkMain(prog *ast.Program) {
	if c.skipMainCheck {
		return
	}
	mainIdx, exists := c.funcMap["main"]
	if !exists {
		c.diags.AddError("C029", "no main function found", prog.Span())
		return
	}
	mainFn := c.funcs[mainIdx]
	if len(mainFn.Parameters) > 0 {
		c.diags.AddError("C030", "main function must not have parameters", mainFn.Span())
	}
	// main can return void or int (or single return only)
	if len(mainFn.ReturnTypes) > 1 {
		c.diags.AddError("C031", "main must return at most one value (int or void)", mainFn.Span())
	} else if len(mainFn.ReturnTypes) == 1 {
		t := mainFn.ReturnTypes[0].ResolvedType
		if t != nil && !t.IsVoid() && !t.Equals(types.Int) {
			c.diags.AddError("C031", "main must return int or void", mainFn.Span())
		}
	}
}

var _ = strconv.Itoa
var _ = diagnostic.NewError
var _ = source.Span{}
