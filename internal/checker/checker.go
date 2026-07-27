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
	"long":    types.FunctionType([]*types.Type{types.String}, types.Long),
	"double":  types.FunctionType([]*types.Type{types.String}, types.Double),
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
	// Math module functions (accept any numeric types via Invalid)
	"abs":   types.FunctionType([]*types.Type{types.Double}, types.Double),
	"min":   types.FunctionType([]*types.Type{types.Double, types.Double}, types.Double),
	"max":   types.FunctionType([]*types.Type{types.Double, types.Double}, types.Double),
	"floor": types.FunctionType([]*types.Type{types.Double}, types.Double),
	"ceil":  types.FunctionType([]*types.Type{types.Double}, types.Double),
	"round": types.FunctionType([]*types.Type{types.Double}, types.Double),
	"sqrt":  types.FunctionType([]*types.Type{types.Double}, types.Double),
	"pow":   types.FunctionType([]*types.Type{types.Double, types.Double}, types.Double),
	"sin":   types.FunctionType([]*types.Type{types.Double}, types.Double),
	"cos":   types.FunctionType([]*types.Type{types.Double}, types.Double),
	"tan":   types.FunctionType([]*types.Type{types.Double}, types.Double),
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
	// Process module
	"process.run": types.FunctionType([]*types.Type{types.String}, types.Int),
	// Time module
	"time.now":   types.FunctionType(nil, types.Long),
	"time.sleep": types.FunctionType([]*types.Type{types.Long}, types.Void),
}

// Checker performs type checking.
type Checker struct {
	diags     *diagnostic.Diagnostics
	scope     *symbol.Scope
	funcs     []*ast.Function
	funcMap   map[string]int
	src       *source.Source
	loopDepth int
	// Track the enclosing if-condition for null narrowing
	narrowingVar string      // variable name being narrowed (set during if-condition check)
	narrowedType *types.Type // the narrowed type (non-nullable)
	// Track definite assignment
	definitelyAssigned map[string]bool // variables that have been assigned in the current path
	// External functions from other modules (mangled names)
	allFuncs map[string]*ast.Function
	// Skip main function check (for library modules in multi-file compilation)
	skipMainCheck bool
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

	// Declare known modules (built-in modules available without explicit import)
	for _, mod := range []string{"core", "string", "math", "env", "file", "process", "time"} {
		if c.scope.Resolve(mod) == nil {
			c.scope.Declare(&symbol.Symbol{
				Name:       mod,
				Kind:       symbol.KindModule,
				Defined:    true,
				ModuleName: mod,
			})
		}
	}

	// Check each function
	for i, fn := range prog.Funcs {
		c.checkFunction(fn, i)
	}

	// Check for main function
	c.checkMain(prog)

	if c.diags.HasErrors() {
		return c.diags, nil
	}
	return c.diags, nil
}

// checkFunction checks a function declaration.
func (c *Checker) checkFunction(fn *ast.Function, funcIdx int) {
	// Build parameter types
	var paramTypes []*types.Type
	for _, p := range fn.Parameters {
		if p.Type != nil && p.Type.ResolvedType != nil {
			paramTypes = append(paramTypes, p.Type.ResolvedType)
		}
	}

	var retType *types.Type
	if fn.ReturnType != nil && fn.ReturnType.ResolvedType != nil {
		retType = fn.ReturnType.ResolvedType
	} else {
		retType = types.Void
	}

	funcType := types.FunctionType(paramTypes, retType)

	// Enter function scope
	oldScope := c.scope
	c.scope = symbol.NewScope(oldScope, funcType)

	// Declare parameters
	slot := 0
	for _, p := range fn.Parameters {
		t := types.Invalid
		if p.Type != nil && p.Type.ResolvedType != nil {
			t = p.Type.ResolvedType
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

	// Check body
	ifVal := c.checkBlock(fn.Body, retType, true)

	// Check return paths
	if !retType.IsVoid() {
		if !ifVal {
			c.diags.AddError("C001", fmt.Sprintf("missing return in function '%s' returning %s", fn.Name, retType.Named()), fn.Span())
		}
	}

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
			if sym != nil && sym.Type != nil {
				_ = sym.Type.IsAssignableFrom(t) // will be checked
			}
		}
		return false
	}
	return false
}

// checkVarDecl checks a variable declaration.
func (c *Checker) checkVarDecl(decl *ast.VariableDecl) bool {
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

// checkReturnStmt checks a return statement.
func (c *Checker) checkReturnStmt(stmt *ast.ReturnStmt, retType *types.Type) bool {
	if stmt.Value != nil {
		valType := c.checkExpr(stmt.Value, nil)
		if valType != nil && valType.IsValid() && retType != nil && retType.IsValid() {
			if !retType.IsAssignableFrom(valType) {
				c.diags.AddError("C010",
					fmt.Sprintf("cannot return %s from function returning %s", valType.Named(), retType.Named()),
					stmt.Span())
			}
		}
		return true
	}

	// Return without value
	if retType != nil && !retType.IsVoid() {
		c.diags.AddError("C011",
			fmt.Sprintf("missing return value in function returning %s", retType.Named()),
			stmt.Span())
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
	case *ast.LongLiteral:
		t = types.Long
	case *ast.FloatLiteral:
		t = types.Float
	case *ast.DoubleLiteral:
		t = types.Double
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
	case *ast.NullCoalescing:
		t = c.checkNullCoalescing(e)
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

		// Check for functions from other modules
		if c.allFuncs != nil {
			for mangledName, fn := range c.allFuncs {
				dotIdx := -1
				for i := len(mangledName) - 1; i >= 0; i-- {
					if mangledName[i] == '.' {
						dotIdx = i
						break
					}
				}
				if dotIdx >= 0 && mangledName[dotIdx+1:] == ident.Name {
					return c.funcToType(fn)
				}
			}
		}

		return types.Invalid
	}

	// Definite assignment check: if variable is not defined and is not a parameter
	if !sym.Parameter && !sym.Defined {
		c.diags.AddError("C032", fmt.Sprintf("variable '%s' may not have been assigned", sym.Name), ident.Span())
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
		// Check for string concatenation
		if expr.Operator == ast.BinAdd && leftType.IsString() && rightType.IsString() {
			return types.String
		}
		if expr.Operator == ast.BinAdd && leftType.IsString() && rightType.IsString() {
			return types.String
		}
		if !leftType.IsNumeric() || !rightType.IsNumeric() {
			c.diags.AddError("C015", fmt.Sprintf("cannot apply %s to %s and %s", expr.Operator, leftType.Named(), rightType.Named()), expr.Span())
			return types.Invalid
		}
		return types.CommonNumericType(leftType, rightType)

	case ast.BinEq, ast.BinNe:
		// Equality: same type or comparable
		if leftType.Kind != rightType.Kind && leftType.Kind != types.KindInvalid && rightType.Kind != types.KindInvalid {
			// Allow null comparison
			if leftType.Kind == types.KindInvalid || rightType.Kind == types.KindInvalid {
				// null comparison is fine
			} else if leftType.IsNumeric() && rightType.IsNumeric() {
				// Numeric comparison across types is fine (e.g., int vs long)
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

	case ast.BinConcat:
		if !leftType.IsString() || !rightType.IsString() {
			c.diags.AddError("C021", "string concatenation requires string operands", expr.Span())
			return types.Invalid
		}
		return types.String
	}

	return types.Invalid
}

// checkCall checks a function call expression.
func (c *Checker) checkCall(expr *ast.CallExpr) *types.Type {
	fnType := c.checkExpr(expr.Function, nil)
	if !fnType.IsValid() || fnType.Kind != types.KindFunction {
		c.diags.AddError("C022", "called expression is not a function", expr.Span())
		return types.Invalid
	}

	if len(expr.Args) != len(fnType.Params) {
		c.diags.AddError("C023",
			fmt.Sprintf("expected %d arguments but got %d", len(fnType.Params), len(expr.Args)),
			expr.Span())
		return types.Invalid
	}

	for i, arg := range expr.Args {
		argType := c.checkExpr(arg, fnType.Params[i])
		if argType.IsValid() && fnType.Params[i] != nil && fnType.Params[i].IsValid() {
			if !fnType.Params[i].IsAssignableFrom(argType) {
				c.diags.AddError("C024",
					fmt.Sprintf("argument %d: expected %s but got %s", i+1, fnType.Params[i].Named(), argType.Named()),
					arg.Span())
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
				fmt.Sprintf("invalid map key type: %s (allowed: bool, byte, int, long, char, string)", kt.Named()),
				expr.Keys[i].Span())
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
			for _, mod := range []string{"core", "string", "math", "env", "file", "process", "time"} {
				if ident.Name == mod {
					isModule = true
					moduleName = mod
					break
				}
			}
		}
	}

	if isModule {
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

	// For non-module member access, resolve the object and report error
	objType := c.checkExpr(expr.Object, nil)
	c.diags.AddError("C040", fmt.Sprintf("cannot access member '%s' of %s", expr.Member, objType.Named()), expr.Span())
	return types.Invalid
}

// funcToType converts a function AST node to a function type.
func (c *Checker) funcToType(fn *ast.Function) *types.Type {
	var params []*types.Type
	for _, p := range fn.Parameters {
		t := types.Invalid
		if p.Type != nil && p.Type.ResolvedType != nil {
			t = p.Type.ResolvedType
		}
		params = append(params, t)
	}
	var ret *types.Type
	if fn.ReturnType != nil && fn.ReturnType.ResolvedType != nil {
		ret = fn.ReturnType.ResolvedType
	} else {
		ret = types.Void
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
	// main can return void or int
	if mainFn.ReturnType != nil {
		t := mainFn.ReturnType.ResolvedType
		if t != nil && !t.IsVoid() && !t.Equals(types.Int) && !t.IsVoid() {
			c.diags.AddError("C031", "main must return int or void", mainFn.Span())
		}
	}
}

var _ = strconv.Itoa
var _ = diagnostic.NewError
var _ = source.Span{}
