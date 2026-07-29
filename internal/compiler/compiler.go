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

// Package compiler compiles typed AST into bytecode.
package compiler

import (
	"fmt"
	"math"

	"github.com/dhoard/solvik-language/internal/ast"
	"github.com/dhoard/solvik-language/internal/bytecode"
	"github.com/dhoard/solvik-language/internal/diagnostic"
	"github.com/dhoard/solvik-language/internal/source"
	"github.com/dhoard/solvik-language/internal/symbol"
	"github.com/dhoard/solvik-language/internal/types"
)

type constantEntry struct {
	kind bytecode.ConstantKind
	data uint64
	str  string
}

// loopInfo tracks the state of a single loop for break/continue compilation.
type loopInfo struct {
	startOffset int   // offset of the loop start (for continue)
	exitOffset  int   // offset of the loop exit (for break), set after compiling body
	breakJumps  []int // indices of pendingJumps for break statements
}

// tryFrame tracks the state of a single try context for finally handling.
type tryFrame struct {
	hasFinally  bool       // true if this try has a finally block
	finallyBody *ast.Block // the finally block AST (to inline before return/break/continue)
}

// slotRef tracks a slot allocated within a scope for cleanup on scope exit.
type slotRef struct {
	index     int
	isRefType bool // true if the variable holds a reference type (string, list, map, exception)
}

// pendingAction represents an abrupt completion that must go through finally.
type pendingAction int

const (
	pendingNone pendingAction = iota
	pendingReturn
	pendingBreak
	pendingContinue
)

// Compiler compiles a typed AST into bytecode.
type Compiler struct {
	diags         *diagnostic.Diagnostics
	src           *source.Source
	prog          *ast.Program
	scope         *symbol.Scope
	funcs         []*ast.Function
	funcMap       map[string]int
	funcIndex     int    // current function index
	currentModule string // module of the function currently being compiled
	natives       []bytecode.NativeDecl
	nativeMap     map[string]int // "module.name" -> native index
	loops         []loopInfo     // stack of active loops for break/continue
	trys          []tryFrame     // stack of active try contexts for finally handling
	nextSlot      int            // next available local slot in current function
	freeSlots     []int          // reusable slots from exited scopes
	scopeSlotRefs [][]slotRef    // per-scope slot tracking stack; nil entries for untracked scopes
	// inliningFinally is set when compiling an inlined finally body to prevent
	// recursive inlining of the same finally blocks from return/break/continue
	// inside the finally body.
	inliningFinally bool
	currentRetType  *types.Type // return type of the current function
	currentStruct   *types.Type // set when compiling a struct method body
	// Trait tracking
	traitTypes map[string]*types.Type // trait name -> trait type
	// External functions from all modules, mapped as "module.funcname" -> index in allFunctions slice
	allFuncMap   map[string]int
	allFunctions []*ast.Function
}

// SetExternalFunctions sets the complete map of all functions across modules.
// This is used for multi-file compilation to resolve cross-module calls.
func (c *Compiler) SetExternalFunctions(funcs []*ast.Function, funcMap map[string]int) {
	c.allFunctions = funcs
	c.allFuncMap = funcMap
}

// New creates a new compiler.
func New(src *source.Source) *Compiler {
	if src == nil {
		src = source.NewSourceText("<internal>", "")
	}
	return &Compiler{
		diags:      diagnostic.NewDiagnostics(),
		src:        src,
		scope:      symbol.NewScope(nil, nil),
		funcMap:    make(map[string]int),
		nativeMap:  make(map[string]int),
		traitTypes: make(map[string]*types.Type),
	}
}

// Compile compiles a program to bytecode.
func (c *Compiler) Compile(prog *ast.Program) (*bytecode.Program, *diagnostic.Diagnostics) {
	c.prog = prog

	// Set current module from the program
	if prog.Module != "" {
		c.currentModule = prog.Module
	}

	// Register native functions - Core module
	c.registerNative("core", "print", 1, true)
	c.registerNative("core", "println", 1, true)
	c.registerNative("core", "string", 1, true)
	c.registerNative("core", "int", 1, true)
	c.registerNative("core", "float", 1, true)
	c.registerNative("core", "byte", 1, true)
	c.registerNative("core", "bool", 1, true)
	c.registerNative("core", "typeOf", 1, true)
	c.registerNative("core", "regex", 1, true)
	c.registerNative("core", "len", 1, true)

	// String module
	c.registerNative("string", "length", 1, true)
	c.registerNative("string", "byteLength", 1, true)
	c.registerNative("string", "charAt", 2, true)
	c.registerNative("string", "substring", 3, true)
	c.registerNative("string", "contains", 2, true)
	c.registerNative("string", "startsWith", 2, true)
	c.registerNative("string", "endsWith", 2, true)
	c.registerNative("string", "indexOf", 2, true)
	c.registerNative("string", "toUpper", 1, true)
	c.registerNative("string", "toLower", 1, true)
	c.registerNative("string", "trim", 1, true)
	c.registerNative("string", "split", 2, true)
	c.registerNative("string", "join", 2, true)

	// Math module
	c.registerNative("math", "PI", 0, true)
	c.registerNative("math", "E", 0, true)
	c.registerNative("math", "abs", 1, true)
	c.registerNative("math", "min", 2, true)
	c.registerNative("math", "max", 2, true)
	c.registerNative("math", "floor", 1, true)
	c.registerNative("math", "ceil", 1, true)
	c.registerNative("math", "round", 1, true)
	c.registerNative("math", "sqrt", 1, true)
	c.registerNative("math", "pow", 2, true)
	c.registerNative("math", "sin", 1, true)
	c.registerNative("math", "cos", 1, true)
	c.registerNative("math", "tan", 1, true)

	// Env module
	c.registerNative("env", "get", 1, true)
	c.registerNative("env", "set", 2, false)
	c.registerNative("env", "keys", 0, true)

	// File module
	c.registerNative("file", "read", 1, true)
	c.registerNative("file", "write", 2, false)
	c.registerNative("file", "append", 2, false)
	c.registerNative("file", "delete", 1, false)
	c.registerNative("file", "exists", 1, true)

	// Map module
	c.registerNative("map", "contains", 2, true)

	// Process module
	c.registerNative("process", "run", -1, true) // variadic - uses any arg count

	// Time module
	c.registerNative("time", "now", 0, true)
	c.registerNative("time", "sleep", 1, false)

	// Random module
	c.registerNative("random", "float", 0, true)
	c.registerNative("random", "int", 2, true)
	c.registerNative("random", "range", 2, true)
	c.registerNative("random", "uniform", 2, true)
	c.registerNative("random", "choice", 1, true)
	c.registerNative("random", "shuffle", 1, true)
	c.registerNative("random", "sample", 2, true)
	c.registerNative("random", "seed", 1, false)

	// Path module
	c.registerNative("path", "join", -1, true) // variadic
	c.registerNative("path", "basename", 1, true)
	c.registerNative("path", "dirname", 1, true)
	c.registerNative("path", "ext", 1, true)
	c.registerNative("path", "abs", 1, true)
	c.registerNative("path", "exists", 1, true)

	// Collect function declarations first
	for _, fn := range prog.Funcs {
		idx := len(c.funcs)
		c.funcs = append(c.funcs, fn)
		c.funcMap[fn.Name] = idx
		if fn.Module != "" {
			c.funcMap[fn.Module+"."+fn.Name] = idx
		}
	}

	// Collect struct methods
	for _, sd := range prog.Structs {
		for _, m := range sd.Methods {
			idx := len(c.funcs)
			c.funcs = append(c.funcs, m)
			c.funcMap[m.Name] = idx
		}
	}

	// Collect trait types
	for _, td := range prog.Traits {
		methods := make(map[string]*types.TraitMethodInfo)
		for _, m := range td.Methods {
			var paramTypes []*types.Type
			for _, p := range m.Parameters {
				t := types.Invalid
				if p.Type != nil && p.Type.ResolvedType != nil {
					t = p.Type.ResolvedType
				}
				paramTypes = append(paramTypes, t)
			}
			var retType *types.Type
			if len(m.ReturnTypes) == 1 && m.ReturnTypes[0] != nil && m.ReturnTypes[0].ResolvedType != nil {
				retType = m.ReturnTypes[0].ResolvedType
			} else {
				retType = types.Void
			}
			methods[m.Name] = &types.TraitMethodInfo{
				Signature: types.FunctionType(paramTypes, retType),
				IsPub:     true,
			}
		}
		c.traitTypes[td.Name] = types.TraitType(td.Name, methods)
	}

	// Compile each function
	bcFuncs := make([]bytecode.Function, len(c.funcs))
	for i, fn := range c.funcs {
		bf, err := c.compileFunction(fn, i)
		if err != nil {
			c.diags.AddError("CP001", fmt.Sprintf("compilation error: %v", err), fn.Span())
		} else {
			bcFuncs[i] = *bf
		}
	}

	if c.diags.HasErrors() {
		return nil, c.diags
	}

	// Build trait method tables for all (trait, struct) pairs
	var traitTables []bytecode.TraitMethodTable
	for _, td := range prog.Traits {
		traitName := td.Name
		traitType, ok := c.traitTypes[traitName]
		if !ok || traitType.TraitMethods == nil {
			continue
		}
		for _, sd := range prog.Structs {
			// Check if this struct has all required trait methods
			satisfies := true
			table := bytecode.TraitMethodTable{
				TraitName:      traitName,
				StructTypeName: sd.Name,
				MethodIndices:  make([]int, 0, len(td.Methods)),
			}
			for _, method := range td.Methods {
				fullMethodName := sd.Name + "." + method.Name
				if fnIdx, exists := c.funcMap[fullMethodName]; exists {
					table.MethodIndices = append(table.MethodIndices, fnIdx)
				} else {
					satisfies = false
					break
				}
			}
			if satisfies {
				traitTables = append(traitTables, table)
			}
		}
	}

	bcProg := &bytecode.Program{
		Magic:       bytecode.Magic,
		Version:     bytecode.FormatVersion,
		Functions:   bcFuncs,
		Natives:     c.natives,
		TraitTables: traitTables,
	}

	return bcProg, c.diags
}

// compileFunction compiles a single function.
func (c *Compiler) compileFunction(fn *ast.Function, fnIdx int) (*bytecode.Function, error) {
	// Build scope for this function
	oldScope := c.scope
	c.scope = symbol.NewScope(oldScope, nil)

	// Set current module from the function
	if fn.Module != "" {
		c.currentModule = fn.Module
	}

	// Build param types for function type
	var paramTypes []*types.Type
	for _, p := range fn.Parameters {
		t := types.Invalid
		if p.Type != nil && p.Type.ResolvedType != nil {
			t = p.Type.ResolvedType
		}
		paramTypes = append(paramTypes, t)
	}

	var retType *types.Type
	if len(fn.ReturnTypes) == 1 && fn.ReturnTypes[0] != nil && fn.ReturnTypes[0].ResolvedType != nil {
		retType = fn.ReturnTypes[0].ResolvedType
	} else {
		retType = types.Void
	}
	c.currentRetType = retType

	// Initialize slot management
	c.nextSlot = 0
	c.freeSlots = nil
	c.scopeSlotRefs = nil

	// Declare parameters
	for _, p := range fn.Parameters {
		t := types.Invalid
		if p.Type != nil && p.Type.ResolvedType != nil {
			t = p.Type.ResolvedType
		}
		slot := c.allocateSlot(t != nil && t.IsReferenceType())
		c.scope.Declare(&symbol.Symbol{
			Name:      p.Name,
			Kind:      symbol.KindVariable,
			Type:      t,
			Slot:      slot,
			Parameter: true,
			Defined:   true,
		})
	}

	// If this is a method, declare struct field symbols in scope
	oldStruct := c.currentStruct
	c.currentStruct = nil
	if fn.StructName != "" && len(fn.Parameters) > 0 {
		selfParam := fn.Parameters[0]
		if selfParam.Type != nil && selfParam.Type.ResolvedType != nil && selfParam.Type.ResolvedType.Kind == types.KindStruct {
			structType := selfParam.Type.ResolvedType
			c.currentStruct = structType
			selfSym := c.scope.Resolve(selfParam.Name)
			if selfSym != nil {
				for i, field := range structType.StructFields {
					c.scope.Declare(&symbol.Symbol{
						Name:          field.Name,
						Kind:          symbol.KindVariable,
						Type:          field.Type,
						Slot:          selfSym.Slot,
						Defined:       true,
						Mut:           field.IsMut,
						IsStructField: true,
						FieldIndex:    i,
						FieldOfSlot:   selfSym.Slot,
					})
				}
			}
		}
	}

	// Emit code
	emitter := newEmitter()
	c.funcIndex = fnIdx

	// Save old loop info
	oldLoops := c.loops
	c.loops = nil

	// Compile body
	if fn.Body != nil {
		c.compileBlock(fn.Body, emitter)
	}

	// Emit return if not already terminated
	lastInst := emitter.lastOpcode()
	if lastInst != bytecode.OpRETURN && lastInst != bytecode.OpRETURN_VOID && lastInst != bytecode.OpRETURN_MULTI {
		retCount := len(fn.ReturnTypes)
		if retCount == 0 {
			emitter.emit0(bytecode.OpRETURN_VOID)
		} else if retCount == 1 {
			// This shouldn't happen - type checker should catch missing returns
			emitter.emit0(bytecode.OpRETURN)
		} else {
			// Shouldn't happen for multi-return either, but handle gracefully
			for range fn.ReturnTypes {
				emitter.emit0(bytecode.OpCONST_NULL)
			}
			emitter.emit1(bytecode.OpRETURN_MULTI, uint64(retCount))
		}
	}

	// Resolve forward jumps
	if err := emitter.resolveJumps(); err != nil {
		return nil, err
	}

	code, sourceMap := emitter.finalize()
	localCount := c.nextSlot - len(fn.Parameters)
	if localCount < 0 {
		localCount = 0
	}

	c.loops = oldLoops
	c.currentStruct = oldStruct
	c.scope = oldScope

	return &bytecode.Function{
		Name:        fn.Name,
		ParamCount:  len(fn.Parameters),
		LocalCount:  localCount,
		MaxStack:    emitter.maxStack,
		ReturnCount: len(fn.ReturnTypes),
		Code:        code,
		SourceMap:   sourceMap,
		Constants:   emitter.buildConstants(),
	}, nil
}

// compileBlock compiles a block of statements. On scope exit, reference-type
// variable slots are nulled to release backing data for Go GC, and all slots
// in the scope are returned to the free pool for reuse by sibling scopes.
func (c *Compiler) compileBlock(block *ast.Block, e *emitter) {
	if block == nil {
		return
	}

	// Enter new scope
	oldScope := c.scope
	c.scope = symbol.NewScope(oldScope, c.scope.FuncType)

	// Push scope tracker
	c.scopeSlotRefs = append(c.scopeSlotRefs, nil)

	for _, stmt := range block.Statements {
		c.compileStatement(stmt, e)
	}

	// Exit scope: null reference-type slots, free all slots for reuse
	c.exitScope(e)

	c.scope = oldScope
}

// compileStatement compiles a single statement.
func (c *Compiler) compileStatement(stmt ast.Statement, e *emitter) {
	switch s := stmt.(type) {
	case *ast.VariableDecl:
		c.compileVarDecl(s, e)
	case *ast.ExprStmt:
		c.compileExprStmt(s, e)
	case *ast.Block:
		c.compileBlock(s, e)
	case *ast.IfStmt:
		c.compileIfStmt(s, e, false)
	case *ast.WhileStmt:
		c.compileWhileStmt(s, e)
	case *ast.ForStmt:
		c.compileForStmt(s, e)
	case *ast.TryStmt:
		c.compileTryStmt(s, e)
	case *ast.ThrowStmt:
		c.compileThrowStmt(s, e)
	case *ast.SwitchStmt:
		c.compileSwitchStmt(s, e)
	case *ast.ReturnStmt:
		c.compileReturnStmt(s, e)
	case *ast.BreakStmt:
		c.compileBreakStmt(s, e)
	case *ast.ContinueStmt:
		c.compileContinueStmt(s, e)
	case *ast.AssignStmt:
		c.compileAssignStmt(s, e)
	}
}

// compileVarDecl compiles a variable declaration.
func (c *Compiler) compileVarDecl(decl *ast.VariableDecl, e *emitter) {
	if decl.InitExpr != nil {
		c.compileExpr(decl.InitExpr, e)
		// If target type is exception and initializer is string, convert to exception
		if decl.Type != nil && decl.Type.ResolvedType != nil && decl.Type.ResolvedType.IsException() {
			initType := decl.InitExpr.GetExprType()
			if initType != nil && initType.IsString() {
				e.emit0(bytecode.OpNEW_EXCEPTION)
			}
		}
		// Emit widening conversion if needed (int -> float, byte -> int, byte -> float)
		if decl.Type != nil && decl.Type.ResolvedType != nil && decl.InitExpr.GetExprType() != nil {
			c.emitWideningConversion(decl.InitExpr.GetExprType(), decl.Type.ResolvedType, e)
		}
		// Emit trait wrap if assigning struct to trait-typed variable
		if decl.Type != nil && decl.Type.ResolvedType != nil && decl.Type.ResolvedType.Kind == types.KindTrait {
			initType := decl.InitExpr.GetExprType()
			if initType != nil && initType.Kind == types.KindStruct {
				c.emitTraitNew(initType, decl.Type.ResolvedType, e)
			}
		}
	} else {
		e.emit0(bytecode.OpCONST_NULL)
	}

	// Determine slot
	varType := types.Invalid
	if decl.Type != nil && decl.Type.ResolvedType != nil {
		varType = decl.Type.ResolvedType
	}

	// Allocate slot for this variable (pass isRefType)
	slot := c.allocateSlot(varType != nil && varType.IsReferenceType())
	// Declare in compiler's scope
	c.scope.Declare(&symbol.Symbol{
		Name:    decl.Name,
		Kind:    symbol.KindVariable,
		Type:    varType,
		Slot:    slot,
		Defined: true,
		Mut:     decl.IsMut,
	})
	e.emit1(bytecode.OpSTORE_LOCAL, uint64(slot))
}

// compileExprStmt compiles an expression statement.
func (c *Compiler) compileExprStmt(stmt *ast.ExprStmt, e *emitter) {
	// Check if it's a multi-target assignment
	if ma, ok := stmt.Expr.(*ast.MultiAssignExpr); ok {
		c.compileMultiAssign(ma, e)
		return
	}
	// Check if it's an assignment (BinaryExpr with BinAssign)
	if be, ok := stmt.Expr.(*ast.BinaryExpr); ok && be.Operator == ast.BinAssign {
		c.compileAssignment(be, e)
		return
	}
	// Compile the expression
	c.compileExpr(stmt.Expr, e)
	// Discard the expression value unless the expression is void
	// (void function calls don't push a value, so POP would pop a local)
	exprType := stmt.Expr.GetExprType()
	if exprType == nil || !exprType.IsVoid() {
		e.emit0(bytecode.OpPOP) // discard expression value
	}
}

// compileAssignment compiles an assignment :=
// compileMultiAssign compiles a multi-target assignment: a, b = expr.
func (c *Compiler) compileMultiAssign(ma *ast.MultiAssignExpr, e *emitter) {
	// Compile the value expression (pushes N values onto stack)
	c.compileExpr(ma.Value, e)

	// Store values in reverse order (last pushed is on top, store to last variable first)
	for i := len(ma.Names) - 1; i >= 0; i-- {
		name := ma.Names[i]
		sym := c.scope.Resolve(name)
		if sym != nil {
			e.emit1(bytecode.OpSTORE_LOCAL, uint64(sym.Slot))
		}
	}
}

func (c *Compiler) compileAssignment(be *ast.BinaryExpr, e *emitter) {
	switch left := be.Left.(type) {
	case *ast.Identifier:
		c.compileIdentAssignment(left, be.Right, e)
	case *ast.IndexExpr:
		c.compileIndexAssignment(left, be.Right, e)
	case *ast.MemberExpr:
		c.compileFieldAssignment(left, be.Right, e)
	}
}

// compileFieldAssignment compiles assignment to a struct field: p.x = value.
func (c *Compiler) compileFieldAssignment(member *ast.MemberExpr, value ast.Expression, e *emitter) {
	objType := member.Object.GetExprType()
	if objType == nil || objType.Kind != types.KindStruct {
		return
	}

	// Find field index
	fieldIdx := -1
	for i, field := range objType.StructFields {
		if field.Name == member.Member {
			fieldIdx = i
			break
		}
	}
	if fieldIdx < 0 {
		return
	}

	// Load struct, compile value, store field
	c.compileExpr(member.Object, e)
	c.compileExpr(value, e)
	e.emit1(bytecode.OpFIELD_STORE, uint64(fieldIdx))
}

// compileIdentAssignment compiles assignment to an identifier.
func (c *Compiler) compileIdentAssignment(ident *ast.Identifier, value ast.Expression, e *emitter) {
	sym := c.scope.Resolve(ident.Name)
	if sym == nil {
		return
	}

	// For struct field assignments, load struct first, then value, then FIELD_STORE
	if sym.IsStructField {
		e.emit1(bytecode.OpLOAD_LOCAL, uint64(sym.FieldOfSlot))
		c.compileExpr(value, e)
		e.emit1(bytecode.OpFIELD_STORE, uint64(sym.FieldIndex))
		return
	}

	c.compileExpr(value, e)

	// Emit trait wrap if assigning struct to trait-typed variable
	if sym.Type != nil && sym.Type.Kind == types.KindTrait {
		valType := value.GetExprType()
		if valType != nil && valType.Kind == types.KindStruct {
			c.emitTraitNew(valType, sym.Type, e)
		}
	}

	e.emit1(bytecode.OpSTORE_LOCAL, uint64(sym.Slot))
}

// compileIndexAssignment compiles assignment to an index expression (list[index] or map[key]).
func (c *Compiler) compileIndexAssignment(indexExpr *ast.IndexExpr, value ast.Expression, e *emitter) {
	// For list[index] = value: push list, push index, push value, emit LIST_SET
	// For map[key] = value: push map, push key, push value, emit MAP_SET
	targetType := indexExpr.Target.GetExprType()

	c.compileExpr(indexExpr.Target, e)
	c.compileExpr(indexExpr.Index, e)
	c.compileExpr(value, e)

	if targetType != nil && targetType.Kind == types.KindMap {
		e.emit0(bytecode.OpMAP_SET)
	} else {
		e.emit0(bytecode.OpLIST_SET)
	}
}

// compileAssignStmt compiles an assignment statement.
func (c *Compiler) compileAssignStmt(stmt *ast.AssignStmt, e *emitter) {
	sym := c.scope.Resolve(stmt.Name)
	if sym == nil {
		return
	}
	c.compileExpr(stmt.Value, e)
	e.emit1(bytecode.OpSTORE_LOCAL, uint64(sym.Slot))
}

// compileIfStmt compiles an if statement.
func (c *Compiler) compileIfStmt(stmt *ast.IfStmt, e *emitter, isElseIf bool) {
	if !isElseIf {
		c.compileExpr(stmt.Condition, e)
	}

	elseJump := e.emitJump(bytecode.OpJUMP_IF_FALSE) // jump to else/end if false

	// Then branch
	c.compileBlock(stmt.Then, e)

	// Check for else-if chains and else
	if len(stmt.ElseIf) > 0 || stmt.Else != nil {
		endJump := e.emitJump(bytecode.OpJUMP) // jump over else

		// Patch else jump to here
		e.patchJump(elseJump)

		// Compile else-if chains
		for _, ei := range stmt.ElseIf {
			c.compileIfStmt(ei, e, false)
		}

		// Compile else
		if stmt.Else != nil {
			c.compileBlock(stmt.Else, e)
		}

		e.patchJump(endJump)
	} else {
		e.patchJump(elseJump)
	}
}

// compileWhileStmt compiles a while loop.
func (c *Compiler) compileWhileStmt(stmt *ast.WhileStmt, e *emitter) {
	loopStart := e.currentOffset()

	c.compileExpr(stmt.Condition, e)
	exitJump := e.emitJump(bytecode.OpJUMP_IF_FALSE)

	// Push loop info for break/continue
	oldLoops := c.loops
	li := loopInfo{startOffset: loopStart, exitOffset: 0}
	c.loops = append(c.loops, li)

	c.compileBlock(stmt.Body, e)

	// Jump back to loop start (backward jump)
	backJump := e.emitJump(bytecode.OpJUMP)
	if backJump < len(e.pendingJumps) {
		e.pendingJumps[backJump].target = loopStart
		e.pendingJumps[backJump].pending = false
	}

	exitOffset := e.currentOffset()
	e.patchJump(exitJump)

	// Patch break jumps for this loop
	if len(c.loops) > 0 {
		currentLoop := &c.loops[len(c.loops)-1]
		currentLoop.exitOffset = exitOffset
		for _, jumpIdx := range currentLoop.breakJumps {
			if jumpIdx < len(e.pendingJumps) {
				e.pendingJumps[jumpIdx].target = exitOffset
				e.pendingJumps[jumpIdx].pending = false
			}
		}
	}

	c.loops = oldLoops
}

// compileForStmt compiles a for-in loop.
func (c *Compiler) compileForStmt(stmt *ast.ForStmt, e *emitter) {
	iterType := stmt.Iterable.GetExprType()
	isMap := iterType != nil && iterType.Kind == types.KindMap

	// Compile iterable expression
	c.compileExpr(stmt.Iterable, e)

	iterSlot := c.allocateSlot(iterType != nil && iterType.IsReferenceType())
	indexSlot := c.allocateSlot(false) // index is always int
	keysSlot := -1

	e.emit1(bytecode.OpSTORE_LOCAL, uint64(iterSlot)) // save iterable

	if isMap {
		// Emit MAP_KEYS to get a list of keys, store as a separate local
		keysSlot = c.allocateSlot(true) // List<KeyType> is always a reference type
		e.emit1(bytecode.OpLOAD_LOCAL, uint64(iterSlot))
		e.emit0(bytecode.OpMAP_KEYS)
		e.emit1(bytecode.OpSTORE_LOCAL, uint64(keysSlot))
	}

	e.emit1(bytecode.OpCONST_INT, 0)
	e.emit1(bytecode.OpSTORE_LOCAL, uint64(indexSlot)) // index = 0

	loopStart := e.currentOffset()

	// Compare: index < length
	e.emit1(bytecode.OpLOAD_LOCAL, uint64(indexSlot)) // push index
	if isMap {
		e.emit1(bytecode.OpLOAD_LOCAL, uint64(keysSlot)) // push keys list
		e.emit0(bytecode.OpLIST_LENGTH)                  // keys list length
	} else {
		e.emit1(bytecode.OpLOAD_LOCAL, uint64(iterSlot)) // push iterable
		e.emit0(bytecode.OpLIST_LENGTH)                  // list length
	}
	e.emit0(bytecode.OpLT_INT)
	exitJump := e.emitJump(bytecode.OpJUMP_IF_FALSE)

	// Get element at index
	if isMap {
		// Push the key from the keys list
		e.emit1(bytecode.OpLOAD_LOCAL, uint64(keysSlot))  // push keys list
		e.emit1(bytecode.OpLOAD_LOCAL, uint64(indexSlot)) // push index
		e.emit0(bytecode.OpLIST_GET)                      // push key
	} else {
		// For lists/strings, get element at index
		e.emit1(bytecode.OpLOAD_LOCAL, uint64(iterSlot))  // push iterable
		e.emit1(bytecode.OpLOAD_LOCAL, uint64(indexSlot)) // push index
		e.emit0(bytecode.OpLIST_GET)                      // pops iterable+index, pushes element
	}

	// If (key, value) unpacking, also get the value via MAP_GET
	if isMap && stmt.ValueVariable != "" {
		// The key is on the stack; duplicate it so we can store it and use it for MAP_GET
		e.emit0(bytecode.OpDUP)
	}

	// Store in loop variable(s)
	oldScope := c.scope
	c.scope = symbol.NewScope(oldScope, c.scope.FuncType)
	// Push scope tracker for the for-loop variable scope (loopVarSlot, valVarSlot)
	c.scopeSlotRefs = append(c.scopeSlotRefs, nil)

	var elemType *types.Type
	if iterType != nil {
		if iterType.Kind == types.KindList && iterType.Element != nil {
			elemType = iterType.Element
		} else if iterType.IsString() {
			elemType = types.Char
		} else if iterType.Kind == types.KindMap && iterType.KeyType != nil {
			elemType = iterType.KeyType
		}
	}
	if elemType == nil {
		elemType = types.Invalid
	}

	// Single variable: store element (or key for maps)
	loopVarSlot := c.allocateSlot(elemType != nil && elemType.IsReferenceType())
	c.scope.Declare(&symbol.Symbol{
		Name:    stmt.Variable,
		Kind:    symbol.KindVariable,
		Type:    elemType,
		Slot:    loopVarSlot,
		Defined: true,
	})
	e.emit1(bytecode.OpSTORE_LOCAL, uint64(loopVarSlot))

	if isMap && stmt.ValueVariable != "" {
		// (key, value) unpacking: key already stored, now get value via MAP_GET
		// The DUP'd key is on the stack; use it to look up the value
		e.emit1(bytecode.OpLOAD_LOCAL, uint64(iterSlot))    // push original map
		e.emit1(bytecode.OpLOAD_LOCAL, uint64(loopVarSlot)) // push key (stored above)
		e.emit0(bytecode.OpMAP_GET)                         // pops map+key, pushes value

		valType := types.Invalid
		if iterType != nil && iterType.ValueType != nil {
			valType = iterType.ValueType
		}
		valVarSlot := c.allocateSlot(valType != nil && valType.IsReferenceType())
		c.scope.Declare(&symbol.Symbol{
			Name:    stmt.ValueVariable,
			Kind:    symbol.KindVariable,
			Type:    valType,
			Slot:    valVarSlot,
			Defined: true,
		})
		e.emit1(bytecode.OpSTORE_LOCAL, uint64(valVarSlot))
	}

	// Push loop info (for break/continue)
	oldLoops := c.loops
	li := loopInfo{startOffset: loopStart, exitOffset: 0}
	c.loops = append(c.loops, li)

	// Body
	c.compileBlock(stmt.Body, e)

	// Increment index
	e.emit1(bytecode.OpLOAD_LOCAL, uint64(indexSlot))
	e.emit1(bytecode.OpCONST_INT, 1)
	e.emit0(bytecode.OpADD_INT)
	e.emit1(bytecode.OpSTORE_LOCAL, uint64(indexSlot))

	// Jump back to loop start (backward jump)
	backJump := e.emitJump(bytecode.OpJUMP)
	if backJump < len(e.pendingJumps) {
		e.pendingJumps[backJump].target = loopStart
		e.pendingJumps[backJump].pending = false
	}

	exitOffset := e.currentOffset()
	e.patchJump(exitJump)

	// Patch break jumps for this loop
	if len(c.loops) > 0 {
		currentLoop := &c.loops[len(c.loops)-1]
		currentLoop.exitOffset = exitOffset
		for _, jumpIdx := range currentLoop.breakJumps {
			if jumpIdx < len(e.pendingJumps) {
				e.pendingJumps[jumpIdx].target = exitOffset
				e.pendingJumps[jumpIdx].pending = false
			}
		}
	}

	// Exit for-loop scope (free loopVarSlot, valVarSlot)
	c.exitScope(e)
	c.scope = oldScope
	c.loops = oldLoops
}

// compileSwitchStmt compiles a switch statement.
// Strategy: compile switch expr once, then for each case DUP it, compile case expr,
// compare, JUMP_IF_FALSE to next case. On match POP switch value, exec body, JUMP to end.
// Default: POP switch value, exec body.
func (c *Compiler) compileSwitchStmt(stmt *ast.SwitchStmt, e *emitter) {
	// Compile the switch expression (once)
	switchExprType := stmt.Expression.GetExprType()
	slot := c.allocateSlot(switchExprType != nil && switchExprType.IsReferenceType())
	c.compileExpr(stmt.Expression, e)
	e.emit1(bytecode.OpSTORE_LOCAL, uint64(slot))

	// Jump targets for case matching
	caseJumps := make([]int, len(stmt.Cases))
	endJumps := make([]int, 0)

	for i, cse := range stmt.Cases {
		// Load switch value
		e.emit1(bytecode.OpLOAD_LOCAL, uint64(slot))
		// Compile case expression
		c.compileExpr(cse.Expression, e)
		// Compare using REF equality (handles all types at runtime)
		e.emit0(bytecode.OpEQ_REF)
		// Jump to next case if not equal
		caseJumps[i] = e.emitJump(bytecode.OpJUMP_IF_FALSE)
		// Equal - pop the switch value and execute body
		e.emit0(bytecode.OpPOP) // remove result from EQ_REF
		if cse.Body != nil {
			c.compileBlock(cse.Body, e)
		}
		// Jump to end after executing case body
		endJumps = append(endJumps, e.emitJump(bytecode.OpJUMP))
		// Patch the case-false jump to here
		e.patchJump(caseJumps[i])
	}

	// Default case: just pop the switch value
	if stmt.Default != nil {
		// Pop switch value (it's still on stack from the last failed case)
		e.emit0(bytecode.OpPOP)
		c.compileBlock(stmt.Default, e)
	} else {
		// No default - just pop the switch value
		e.emit0(bytecode.OpPOP)
	}

	// Patch all the end jumps
	for _, jmp := range endJumps {
		e.patchJump(jmp)
	}
}

// compileTryStmt compiles a try/catch/finally statement.
//
// The compilation strategy uses SETUP_HANDLER / REMOVE_HANDLER to mark
// protected regions. The handler info contains catch target, finally target,
// and stack depth to restore. The VM uses this info during exception unwinding.
//
// For non-exceptional control flow (normal completion), the finally block
// is compiled inline so it executes naturally. For return/break/continue
// inside the try block, the finally block is inlined before the control
// transfer instruction so it always executes first.
func (c *Compiler) compileTryStmt(stmt *ast.TryStmt, e *emitter) {
	// Record stack depth before try block
	stackDepth := e.currStack

	catchOffset := 0
	finallyOffset := 0
	hasCatch := stmt.Catch != nil
	hasFinally := stmt.Finally != nil

	// Emit SETUP_HANDLER with placeholder offsets
	// Layout: [op(1)][catchOffset(int32)][finallyOffset(int32)][stackDepth(uint16)] = 11 bytes
	handlerSetupOffset := len(e.code)
	e.code = append(e.code, byte(bytecode.OpSETUP_HANDLER))
	// Placeholder: catchOffset (4 bytes)
	e.code = append(e.code, 0, 0, 0, 0)
	// Placeholder: finallyOffset (4 bytes)
	e.code = append(e.code, 0, 0, 0, 0)
	// Placeholder: stackDepth (2 bytes)
	e.code = append(e.code, 0, 0)
	e.offsets = append(e.offsets, handlerSetupOffset)

	// Save the handler setup position for later patching
	handlerIdx := len(e.handlerInfo)
	e.handlerInfo = append(e.handlerInfo, handlerMeta{
		setupOffset: handlerSetupOffset,
		stackDepth:  stackDepth,
	})

	// Push try context for finally tracking
	oldTryStack := c.trys
	if hasFinally {
		c.trys = append(c.trys, tryFrame{
			hasFinally:  true,
			finallyBody: stmt.Finally,
		})
	} else {
		c.trys = append(c.trys, tryFrame{hasFinally: false})
	}

	// Compile try body
	c.compileBlock(stmt.TryBody, e)

	// Pop try context
	c.trys = c.trys[:len(c.trys)-1]

	// Normal completion: skip catch block
	skipCatchJump := -1
	if hasCatch {
		skipCatchJump = e.emitJump(bytecode.OpJUMP) // jump over catch to finally/end
	}

	// Patch catch handler target
	if hasCatch {
		catchOffset = e.currentOffset()
		e.handlerInfo[handlerIdx].catchOffset = catchOffset

		// Push try context for catch body (for return/break/continue in catch)
		if hasFinally {
			c.trys = append(c.trys, tryFrame{
				hasFinally:  true,
				finallyBody: stmt.Finally,
			})
		}

		// Compile catch clause: create scope for catch parameter
		oldScope := c.scope
		c.scope = symbol.NewScope(oldScope, c.scope.FuncType)
		c.scopeSlotRefs = append(c.scopeSlotRefs, nil) // push scope for catch param

		// Allocate slot for catch parameter (Exception is a reference type)
		paramSlot := c.allocateSlot(true)
		c.scope.Declare(&symbol.Symbol{
			Name:    stmt.Catch.ParamName,
			Kind:    symbol.KindVariable,
			Type:    types.Exception,
			Slot:    paramSlot,
			Defined: true,
		})

		// Store the caught exception value into the catch parameter
		e.emit1(bytecode.OpSTORE_LOCAL, uint64(paramSlot))

		// Compile catch body
		c.compileBlock(stmt.Catch.Body, e)

		// Exit catch scope (free param slot)
		c.exitScope(e)
		c.scope = oldScope

		// Pop try context for catch body
		if hasFinally {
			c.trys = c.trys[:len(c.trys)-1]
		}
	}

	// Finally block (executes in all cases: normal, caught, or propagating)
	if hasFinally {
		// Patch the skip-catch jump to here (before finally)
		if skipCatchJump >= 0 {
			e.patchJump(skipCatchJump)
		}

		// Compile finally body inline
		finallyOffset = e.currentOffset()
		e.handlerInfo[handlerIdx].finallyOffset = finallyOffset

		c.compileBlock(stmt.Finally, e)

		// After finally, remove handler and continue
		e.emit0(bytecode.OpREMOVE_HANDLER)
	} else {
		// No finally: just remove handler after catch
		if skipCatchJump >= 0 {
			e.patchJump(skipCatchJump)
		}
		e.emit0(bytecode.OpREMOVE_HANDLER)
	}

	// Restore try stack
	c.trys = oldTryStack

	// Patch the SETUP_HANDLER operands with actual offsets
	e.finalizeHandler(handlerIdx)
}

// compileThrowStmt compiles a throw statement.
func (c *Compiler) compileThrowStmt(stmt *ast.ThrowStmt, e *emitter) {
	c.compileExpr(stmt.Value, e)
	// If the value is a string, convert to exception with trace capture
	if stmt.Value != nil {
		valType := stmt.Value.GetExprType()
		if valType != nil && valType.IsString() {
			e.emit0(bytecode.OpNEW_EXCEPTION)
		}
	}
	e.emit0(bytecode.OpTHROW)
}

// emitWideningConversion emits implicit numeric widening conversions.
// Only emits CONVERT_INT_TO_FLOAT since byte arithmetic already promotes
// to int in the VM, and CONVERT_BYTE_TO_INT would panic on the int result.
func (c *Compiler) emitWideningConversion(from, to *types.Type, e *emitter) {
	if from == nil || to == nil || !from.IsValid() || !to.IsValid() {
		return
	}
	if to.Kind == types.KindFloat && from.IsNumeric() && from.Kind != types.KindFloat {
		e.emit0(bytecode.OpCONVERT_INT_TO_FLOAT)
	}
}

// emitTraitNew emits OpTRAIT_NEW to wrap a struct value into a trait fat pointer.
func (c *Compiler) emitTraitNew(structType, traitType *types.Type, e *emitter) {
	if structType == nil || traitType == nil {
		return
	}
	traitIdx := e.addString(traitType.TraitName)
	structIdx := e.addString(structType.StructName)
	e.emit2(bytecode.OpTRAIT_NEW, uint64(traitIdx), uint64(structIdx))
}

// emitArgTraitWrap checks if the argument needs trait wrapping and emits OpTRAIT_NEW.
func (c *Compiler) emitArgTraitWrap(arg ast.Expression, paramType *types.Type, e *emitter) {
	if paramType == nil || paramType.Kind != types.KindTrait {
		return
	}
	argType := arg.GetExprType()
	if argType != nil && argType.Kind == types.KindStruct {
		c.emitTraitNew(argType, paramType, e)
	}
}

// getFuncParamTypes returns the parameter types for a function by index.
func (c *Compiler) getFuncParamTypes(fnIdx int) []*types.Type {
	if fnIdx < 0 || fnIdx >= len(c.funcs) {
		return nil
	}
	fn := c.funcs[fnIdx]
	var params []*types.Type
	for _, p := range fn.Parameters {
		if p.Type != nil && p.Type.ResolvedType != nil {
			params = append(params, p.Type.ResolvedType)
		} else {
			params = append(params, types.Invalid)
		}
	}
	return params
}

// compileReturnStmt compiles a return statement.
// If this return is inside a try block with a finally, the finally body
// is inlined before the return instruction so it executes before control transfer.
// The return values are pushed onto the stack first, then the finally blocks
// execute (they leave the stack balanced), and then RETURN/RETURN_MULTI pops them.
func (c *Compiler) compileReturnStmt(stmt *ast.ReturnStmt, e *emitter) {
	valCount := len(stmt.Values)

	// Helper to emit NEW_EXCEPTION if returning a string from an exception function
	// (only applies to single-return exception functions)
	maybeConvertToException := func(idx int) {
		if valCount == 1 && c.currentRetType != nil && c.currentRetType.IsException() {
			valType := stmt.Values[0].GetExprType()
			if valType != nil && valType.IsString() {
				e.emit0(bytecode.OpNEW_EXCEPTION)
			}
		}
	}

	emitReturn := func() {
		switch valCount {
		case 0:
			e.emit0(bytecode.OpRETURN_VOID)
		case 1:
			c.compileExpr(stmt.Values[0], e)
			// Emit widening conversion if needed
			if stmt.Values[0].GetExprType() != nil && c.currentRetType != nil {
				c.emitWideningConversion(stmt.Values[0].GetExprType(), c.currentRetType, e)
			}
			maybeConvertToException(0)
			e.emit0(bytecode.OpRETURN)
		default:
			for _, val := range stmt.Values {
				c.compileExpr(val, e)
			}
			e.emit1(bytecode.OpRETURN_MULTI, uint64(valCount))
		}
	}

	// If we're already inlining a finally body, compile return directly.
	if c.inliningFinally {
		emitReturn()
		return
	}

	// Check if we're inside a try-finally context
	hasFinally := false
	for i := len(c.trys) - 1; i >= 0; i-- {
		if c.trys[i].hasFinally {
			hasFinally = true
			break
		}
	}

	if hasFinally {
		// Push all return values first (they stay on stack through finally blocks)
		for _, val := range stmt.Values {
			c.compileExpr(val, e)
		}
		// If single return and exception conversion needed, apply it
		if valCount == 1 {
			maybeConvertToException(0)
		}
		// If no values, push null placeholder to keep stack balanced through finally
		if valCount == 0 {
			e.emit0(bytecode.OpCONST_NULL)
		}

		// Emit finally body for each enclosing try with finally (innermost first)
		c.inliningFinally = true
		for i := len(c.trys) - 1; i >= 0; i-- {
			if c.trys[i].hasFinally {
				c.compileBlock(c.trys[i].finallyBody, e)
			}
		}
		c.inliningFinally = false

		// Emit the appropriate return instruction
		if valCount == 0 {
			e.emit0(bytecode.OpRETURN_VOID)
		} else if valCount == 1 {
			e.emit0(bytecode.OpRETURN)
		} else {
			e.emit1(bytecode.OpRETURN_MULTI, uint64(valCount))
		}
	} else {
		emitReturn()
	}
}

// compileBreakStmt compiles a break statement.
// If this break is inside a try block with a finally, the finally body
// is inlined before the jump so it executes before control transfer.
func (c *Compiler) compileBreakStmt(stmt *ast.BreakStmt, e *emitter) {
	if len(c.loops) == 0 {
		return
	}

	// If we're already inlining a finally body, compile break directly.
	// A break inside finally supersedes any pending break/continue/return.
	if c.inliningFinally {
		jumpIdx := e.emitJump(bytecode.OpJUMP)
		currentLoop := &c.loops[len(c.loops)-1]
		currentLoop.breakJumps = append(currentLoop.breakJumps, jumpIdx)
		return
	}

	// Check if we're inside a try-finally context
	hasFinally := false
	for i := len(c.trys) - 1; i >= 0; i-- {
		if c.trys[i].hasFinally {
			hasFinally = true
			break
		}
	}

	if hasFinally {
		// Emit finally body for each enclosing try with finally (innermost first)
		c.inliningFinally = true
		for i := len(c.trys) - 1; i >= 0; i-- {
			if c.trys[i].hasFinally {
				c.compileBlock(c.trys[i].finallyBody, e)
			}
		}
		c.inliningFinally = false
	}

	// Emit jump to loop exit; the exit offset is patched later
	jumpIdx := e.emitJump(bytecode.OpJUMP)
	// Store the jump index so we can patch it when we know the exit offset
	currentLoop := &c.loops[len(c.loops)-1]
	currentLoop.breakJumps = append(currentLoop.breakJumps, jumpIdx)
}

// compileContinueStmt compiles a continue statement.
// If this continue is inside a try block with a finally, the finally body
// is inlined before the jump so it executes before control transfer.
func (c *Compiler) compileContinueStmt(stmt *ast.ContinueStmt, e *emitter) {
	if len(c.loops) == 0 {
		return
	}

	// If we're already inlining a finally body, compile continue directly.
	// A continue inside finally supersedes any pending break/continue/return.
	if c.inliningFinally {
		loopStart := c.loops[len(c.loops)-1].startOffset
		jumpIdx := e.emitJump(bytecode.OpJUMP)
		if jumpIdx < len(e.pendingJumps) {
			e.pendingJumps[jumpIdx].target = loopStart
			e.pendingJumps[jumpIdx].pending = false
		}
		return
	}

	// Check if we're inside a try-finally context
	hasFinally := false
	for i := len(c.trys) - 1; i >= 0; i-- {
		if c.trys[i].hasFinally {
			hasFinally = true
			break
		}
	}

	if hasFinally {
		// Emit finally body for each enclosing try with finally (innermost first)
		c.inliningFinally = true
		for i := len(c.trys) - 1; i >= 0; i-- {
			if c.trys[i].hasFinally {
				c.compileBlock(c.trys[i].finallyBody, e)
			}
		}
		c.inliningFinally = false
	}

	loopStart := c.loops[len(c.loops)-1].startOffset
	// Emit a backward jump
	jumpIdx := e.emitJump(bytecode.OpJUMP)
	// Set the target to loopStart (backward jump)
	if jumpIdx < len(e.pendingJumps) {
		e.pendingJumps[jumpIdx].target = loopStart
		e.pendingJumps[jumpIdx].pending = false
	}
}

// compileExpr compiles an expression, pushing its value onto the stack.
func (c *Compiler) compileExpr(expr ast.Expression, e *emitter) {
	switch ex := expr.(type) {
	case *ast.IntLiteral:
		e.emit1(bytecode.OpCONST_INT, uint64(ex.Value))
	case *ast.FloatLiteral:
		e.emit1(bytecode.OpCONST_FLOAT, math.Float64bits(ex.Value))
	case *ast.BoolLiteral:
		if ex.Value {
			e.emit1(bytecode.OpCONST_BOOL, 1)
		} else {
			e.emit1(bytecode.OpCONST_BOOL, 0)
		}
	case *ast.CharLiteral:
		e.emit1(bytecode.OpCONST_CHAR, uint64(ex.Value))
	case *ast.StringLiteral:
		idx := uint64(e.addString(ex.Value))
		e.emit1(bytecode.OpCONST_STRING, idx)
	case *ast.ByteLiteral:
		e.emit1(bytecode.OpCONST_BYTE, uint64(ex.Value))
	case *ast.NullLiteral:
		e.emit0(bytecode.OpCONST_NULL)
	case *ast.Identifier:
		c.compileIdentifier(ex, e)
	case *ast.UnaryExpr:
		c.compileUnary(ex, e)
	case *ast.BinaryExpr:
		c.compileBinary(ex, e)
	case *ast.CallExpr:
		c.compileCall(ex, e)
	case *ast.IndexExpr:
		c.compileIndex(ex, e)
	case *ast.ListLiteral:
		c.compileListLiteral(ex, e)
	case *ast.MapLiteral:
		c.compileMapLiteral(ex, e)
	case *ast.StructLiteral:
		c.compileStructLiteral(ex, e)
	case *ast.MultiAssignExpr:
		// Handled in compileExprStmt; no-op here (no value pushed)

	case *ast.MemberExpr:
		c.compileMemberExpr(ex, e)
	case *ast.NullCoalescing:
		c.compileNullCoalescing(ex, e)
	case *ast.SpreadExpr:
		c.compileExpr(ex.Expr, e)
	}
}

// compileIdentifier compiles an identifier reference.
func (c *Compiler) compileIdentifier(ident *ast.Identifier, e *emitter) {
	sym := c.scope.Resolve(ident.Name)
	if sym != nil {
		// For struct field references inside methods, emit FIELD_LOAD
		if sym.IsStructField {
			e.emit1(bytecode.OpLOAD_LOCAL, uint64(sym.FieldOfSlot))
			e.emit1(bytecode.OpFIELD_LOAD, uint64(sym.FieldIndex))
			return
		}
		e.emit1(bytecode.OpLOAD_LOCAL, uint64(sym.Slot))
		return
	}

	// Check if it's a function reference
	if idx, exists := c.funcMap[ident.Name]; exists {
		_ = idx
		// For now, function references are only used in calls
		return
	}
}

// compileUnary compiles a unary expression.
func (c *Compiler) compileUnary(expr *ast.UnaryExpr, e *emitter) {
	c.compileExpr(expr.Operand, e)

	// Get operand type for type-aware instruction selection
	operandType := expr.Operand.GetExprType()

	switch expr.Operator {
	case ast.UnaryNegate:
		if operandType != nil && operandType.Kind == types.KindFloat {
			e.emit0(bytecode.OpNEG_FLOAT)
		} else {
			e.emit0(bytecode.OpNEG_INT)
		}
	case ast.UnaryNot:
		e.emit0(bytecode.OpNOT_BOOL)
	case ast.UnaryBitNot:
		e.emit0(bytecode.OpBIT_NOT_INT)
	}
}

// compileBinary compiles a binary expression, using type info for type-aware instruction selection.
func (c *Compiler) compileBinary(expr *ast.BinaryExpr, e *emitter) {
	// Handle short-circuit operators
	if expr.Operator == ast.BinAnd {
		c.compileAnd(expr, e)
		return
	}
	if expr.Operator == ast.BinOr {
		c.compileOr(expr, e)
		return
	}

	c.compileExpr(expr.Left, e)
	c.compileExpr(expr.Right, e)

	// Get resolved types for type-aware instruction selection
	leftType := expr.Left.GetExprType()
	rightType := expr.Right.GetExprType()

	// Determine the common type for arithmetic operations
	// Only compute this for numeric types - non-numeric types should use EQ_REF
	commonType := types.Invalid
	if leftType != nil && rightType != nil && leftType.IsValid() && rightType.IsValid() {
		if leftType.IsNumeric() && rightType.IsNumeric() {
			commonType = types.CommonNumericType(leftType, rightType)
		}
	}

	switch expr.Operator {
	case ast.BinAdd:
		if commonType != nil && commonType.Kind == types.KindFloat {
			e.emit0(bytecode.OpADD_FLOAT)
		} else {
			e.emit0(bytecode.OpADD_INT)
		}
	case ast.BinSub:
		if commonType != nil && commonType.Kind == types.KindFloat {
			e.emit0(bytecode.OpSUB_FLOAT)
		} else {
			e.emit0(bytecode.OpSUB_INT)
		}
	case ast.BinMul:
		if commonType != nil && commonType.Kind == types.KindFloat {
			e.emit0(bytecode.OpMUL_FLOAT)
		} else {
			e.emit0(bytecode.OpMUL_INT)
		}
	case ast.BinDiv:
		if commonType != nil && commonType.Kind == types.KindFloat {
			e.emit0(bytecode.OpDIV_FLOAT)
		} else {
			e.emit0(bytecode.OpDIV_INT)
		}
	case ast.BinMod:
		e.emit0(bytecode.OpREM_INT)
	case ast.BinEq:
		if (leftType != nil && leftType.IsNull()) || (rightType != nil && rightType.IsNull()) {
			e.emit0(bytecode.OpEQ_REF)
		} else if leftType != nil && leftType.IsString() && rightType != nil && rightType.IsString() {
			e.emit0(bytecode.OpEQ_STRING)
		} else if leftType != nil && leftType.IsBool() && rightType != nil && rightType.IsBool() {
			e.emit0(bytecode.OpEQ_BOOL)
		} else if commonType != nil && commonType.IsValid() && commonType.IsNumeric() {
			if commonType.Kind == types.KindFloat {
				e.emit0(bytecode.OpEQ_FLOAT)
			} else {
				e.emit0(bytecode.OpEQ_INT)
			}
		} else {
			e.emit0(bytecode.OpEQ_REF)
		}
	case ast.BinNe:
		if (leftType != nil && leftType.IsNull()) || (rightType != nil && rightType.IsNull()) {
			e.emit0(bytecode.OpEQ_REF)
			e.emit0(bytecode.OpNOT_BOOL)
		} else if leftType != nil && leftType.IsString() && rightType != nil && rightType.IsString() {
			e.emit0(bytecode.OpEQ_STRING)
			e.emit0(bytecode.OpNOT_BOOL)
		} else if leftType != nil && leftType.IsBool() && rightType != nil && rightType.IsBool() {
			e.emit0(bytecode.OpEQ_BOOL)
			e.emit0(bytecode.OpNOT_BOOL)
		} else if commonType != nil && commonType.IsValid() && commonType.IsNumeric() {
			if commonType.Kind == types.KindFloat {
				e.emit0(bytecode.OpEQ_FLOAT)
			} else {
				e.emit0(bytecode.OpEQ_INT)
			}
			e.emit0(bytecode.OpNOT_BOOL)
		} else {
			e.emit0(bytecode.OpEQ_REF)
			e.emit0(bytecode.OpNOT_BOOL)
		}
	case ast.BinLt:
		if commonType != nil && commonType.Kind == types.KindFloat {
			e.emit0(bytecode.OpLT_FLOAT)
		} else {
			e.emit0(bytecode.OpLT_INT)
		}
	case ast.BinLe:
		if commonType != nil && commonType.Kind == types.KindFloat {
			e.emit0(bytecode.OpLE_FLOAT)
		} else {
			e.emit0(bytecode.OpLE_INT)
		}
	case ast.BinGt:
		if commonType != nil && commonType.Kind == types.KindFloat {
			e.emit0(bytecode.OpGT_FLOAT)
		} else {
			e.emit0(bytecode.OpGT_INT)
		}
	case ast.BinGe:
		if commonType != nil && commonType.Kind == types.KindFloat {
			e.emit0(bytecode.OpGE_FLOAT)
		} else {
			e.emit0(bytecode.OpGE_INT)
		}
	case ast.BinBitAnd:
		e.emit0(bytecode.OpBIT_AND_INT)
	case ast.BinBitOr:
		e.emit0(bytecode.OpBIT_OR_INT)
	case ast.BinBitXor:
		e.emit0(bytecode.OpBIT_XOR_INT)
	case ast.BinShiftLeft:
		e.emit0(bytecode.OpSHIFT_LEFT_INT)
	case ast.BinShiftRight:
		e.emit0(bytecode.OpSHIFT_RIGHT_INT)
	case ast.BinStrConcat:
		e.emit0(bytecode.OpCONCAT_STRING)
	}
}

// compileAnd compiles short-circuit &&.
// Must leave exactly one bool on the stack in all paths.
func (c *Compiler) compileAnd(expr *ast.BinaryExpr, e *emitter) {
	c.compileExpr(expr.Left, e)
	falseJump := e.emitJump(bytecode.OpJUMP_IF_FALSE) // if left is false, jump to push false
	c.compileExpr(expr.Right, e)
	endJump := e.emitJump(bytecode.OpJUMP) // skip the false push
	e.patchJump(falseJump)
	e.emit1(bytecode.OpCONST_BOOL, 0) // push false (short-circuit result)
	e.patchJump(endJump)
}

// compileOr compiles short-circuit ||.
// Must leave exactly one bool on the stack in all paths.
func (c *Compiler) compileOr(expr *ast.BinaryExpr, e *emitter) {
	c.compileExpr(expr.Left, e)
	trueJump := e.emitJump(bytecode.OpJUMP_IF_TRUE) // if left is true, jump to push true
	c.compileExpr(expr.Right, e)
	endJump := e.emitJump(bytecode.OpJUMP) // skip the true push
	e.patchJump(trueJump)
	e.emit1(bytecode.OpCONST_BOOL, 1) // push true (short-circuit result)
	e.patchJump(endJump)
}

// compileCall compiles a function call.
func (c *Compiler) compileCall(expr *ast.CallExpr, e *emitter) {
	// Handle struct construction: Point(3, 4)
	if _, ok := expr.Function.(*ast.Identifier); ok {
		exprType := expr.GetExprType()
		if exprType != nil && exprType.Kind == types.KindStruct {
			// Compile arguments (field values in positional order)
			for _, arg := range expr.Args {
				c.compileExpr(arg, e)
			}
			typeIdx := e.addString(exprType.StructName)
			e.emit2(bytecode.OpSTRUCT_NEW, uint64(len(expr.Args)), uint64(typeIdx))
			return
		}
	}

	// Handle method calls: p.move(10, 20)
	if member, ok := expr.Function.(*ast.MemberExpr); ok {
		objType := member.Object.GetExprType()
		if objType != nil && objType.Kind == types.KindStruct {
			// Resolve method
			if objType.StructMethods != nil {
				if _, ok := objType.StructMethods[member.Member]; ok {
					// Look up method function index in compiler's funcMap
					fullMethodName := objType.StructName + "." + member.Member
					if fnIdx, exists := c.funcMap[fullMethodName]; exists {
						// Compile the struct object (first implicit argument)
						c.compileExpr(member.Object, e)
						// Compile explicit arguments
						for _, arg := range expr.Args {
							c.compileExpr(arg, e)
						}
						// Call the method function with struct + args
						e.emit2(bytecode.OpCALL, uint64(fnIdx), uint64(len(expr.Args)+1))
						return
					}
				}
			}
		}

		// Handle trait method calls: shape.draw()
		if objType != nil && objType.Kind == types.KindTrait {
			c.compileExpr(member.Object, e)
			for _, arg := range expr.Args {
				c.compileExpr(arg, e)
			}
			// Find the method slot index in the trait
			methodSlot := -1
			if objType.TraitMethods != nil {
				slot := 0
				for _, td := range c.prog.Traits {
					if td.Name == objType.TraitName {
						for _, m := range td.Methods {
							if m.Name == member.Member {
								methodSlot = slot
								break
							}
							slot++
						}
						break
					}
				}
			}
			if methodSlot < 0 {
				methodSlot = 0 // fallback
			}
			e.emit2(bytecode.OpTRAIT_INVOKE, uint64(methodSlot), uint64(len(expr.Args)))
			return
		}

		if ident, ok := member.Object.(*ast.Identifier); ok {
			// Check for module-qualified native functions: core.print etc.
			fullName := ident.Name + "." + member.Member
			if nativeIdx, exists := c.nativeMap[fullName]; exists {
				for _, arg := range expr.Args {
					c.compileExpr(arg, e)
				}
				e.emit2(bytecode.OpCALL_NATIVE, uint64(nativeIdx), uint64(len(expr.Args)))
				return
			}
			// Check for module-qualified user functions
			if fnIdx, exists := c.funcMap[fullName]; exists {
				c.compileVariadicCall(fnIdx, expr.Args, e)
				return
			}
		}
	}

	// Check if it's a simple identifier (native or user function)
	if ident, ok := expr.Function.(*ast.Identifier); ok {
		// Check for unqualified method call inside a struct method
		if c.currentStruct != nil && c.currentStruct.StructMethods != nil {
			if mi, ok := c.currentStruct.StructMethods[ident.Name]; ok {
				fnIdx := mi.FuncIndex
				// Use compiler's funcMap for correct index in combined programs
				fullMethodName := c.currentStruct.StructName + "." + ident.Name
				if idx, exists := c.funcMap[fullMethodName]; exists {
					fnIdx = idx
				}
				// Load implicit self parameter (slot 0)
				e.emit1(bytecode.OpLOAD_LOCAL, 0)
				// Compile explicit arguments
				for _, arg := range expr.Args {
					c.compileExpr(arg, e)
				}
				e.emit2(bytecode.OpCALL, uint64(fnIdx), uint64(len(expr.Args)+1))
				return
			}
		}

		// Check native functions first (unqualified)
		if nativeIdx, exists := c.nativeMap["core."+ident.Name]; exists {
			for _, arg := range expr.Args {
				c.compileExpr(arg, e)
			}
			e.emit2(bytecode.OpCALL_NATIVE, uint64(nativeIdx), uint64(len(expr.Args)))
			return
		}

		// Check user functions (unqualified, scoped to current module)
		if fnIdx, exists := c.funcMap[ident.Name]; exists {
			if fnIdx < len(c.funcs) {
				targetModule := c.funcs[fnIdx].Module
				if targetModule == "" || targetModule == c.currentModule {
					c.compileVariadicCall(fnIdx, expr.Args, e)
					return
				}
			}
		}

		// Check external functions (same-module reference via allFuncMap)
		if c.allFunctions != nil && c.allFuncMap != nil {
			for mangled, extIdx := range c.allFuncMap {
				dotIdx := -1
				for i := len(mangled) - 1; i >= 0; i-- {
					if mangled[i] == '.' {
						dotIdx = i
						break
					}
				}
				if dotIdx >= 0 && mangled[dotIdx+1:] == ident.Name && mangled[:dotIdx] == c.currentModule {
					c.compileVariadicCall(extIdx, expr.Args, e)
					return
				}
			}
		}
	}

	// Generic call — compile normally
	c.compileExpr(expr.Function, e)
	for _, arg := range expr.Args {
		c.compileExpr(arg, e)
	}
}

// compileVariadicCall compiles a call to a function that may be variadic.
// If the function is variadic, the variadic args are packed into a List<T>.
func (c *Compiler) compileVariadicCall(fnIdx int, args []ast.Expression, e *emitter) {
	// Check if the target function is variadic
	isVariadic := false
	fixedCount := 0
	var paramTypes []*types.Type
	if fnIdx >= 0 && fnIdx < len(c.funcs) {
		fn := c.funcs[fnIdx]
		if len(fn.Parameters) > 0 && fn.Parameters[len(fn.Parameters)-1].Variadic {
			isVariadic = true
			fixedCount = len(fn.Parameters) - 1
		}
		paramTypes = c.getFuncParamTypes(fnIdx)
	}

	if !isVariadic {
		// Standard call — compile all args directly
		for i, arg := range args {
			c.compileExpr(arg, e)
			if i < len(paramTypes) {
				c.emitArgTraitWrap(arg, paramTypes[i], e)
			}
		}
		e.emit2(bytecode.OpCALL, uint64(fnIdx), uint64(len(args)))
		return
	}

	// Variadic call — compile fixed args, then pack variadic args into a list
	for i := 0; i < fixedCount && i < len(args); i++ {
		c.compileExpr(args[i], e)
		if i < len(paramTypes) {
			c.emitArgTraitWrap(args[i], paramTypes[i], e)
		}
	}

	// Build the variadic list
	variadicCount := len(args) - fixedCount
	if variadicCount > 0 {
		e.emit1(bytecode.OpNEW_LIST, uint64(variadicCount))
		for i := fixedCount; i < len(args); i++ {
			c.compileExpr(args[i], e)
			e.emit0(bytecode.OpLIST_APPEND)
		}
	} else {
		// Zero variadic args → push empty list
		e.emit1(bytecode.OpNEW_LIST, 0)
	}

	// Total args = fixed params + 1 (the list)
	e.emit2(bytecode.OpCALL, uint64(fnIdx), uint64(fixedCount+1))
}

// compileIndex compiles an indexing expression (list[index] or map[key]).
func (c *Compiler) compileIndex(expr *ast.IndexExpr, e *emitter) {
	c.compileExpr(expr.Target, e)
	c.compileExpr(expr.Index, e)

	targetType := expr.Target.GetExprType()
	if targetType != nil && targetType.Kind == types.KindMap {
		e.emit0(bytecode.OpMAP_GET)
	} else {
		e.emit0(bytecode.OpLIST_GET)
	}
}

// compileListLiteral compiles a list literal.
func (c *Compiler) compileListLiteral(expr *ast.ListLiteral, e *emitter) {
	// Determine if elements need trait wrapping
	listType := expr.GetExprType()
	var elemTraitType *types.Type
	if listType != nil && listType.Kind == types.KindList && listType.Element != nil && listType.Element.Kind == types.KindTrait {
		elemTraitType = listType.Element
	}

	e.emit1(bytecode.OpNEW_LIST, uint64(len(expr.Elements)))
	for _, el := range expr.Elements {
		c.compileExpr(el, e)
		// Wrap struct elements into trait fat pointers if needed
		if elemTraitType != nil {
			elType := el.GetExprType()
			if elType != nil && elType.Kind == types.KindStruct {
				c.emitTraitNew(elType, elemTraitType, e)
			}
		}
		e.emit0(bytecode.OpLIST_APPEND)
	}
}

// compileStructLiteral compiles a named-field struct literal.
func (c *Compiler) compileStructLiteral(expr *ast.StructLiteral, e *emitter) {
	exprType := expr.GetExprType()
	if exprType == nil || exprType.Kind != types.KindStruct {
		return
	}

	// Build field index map
	fieldMap := make(map[string]int)
	for i, f := range exprType.StructFields {
		fieldMap[f.Name] = i
	}

	// Emit field values in declaration order
	// We need to reorder the named fields to declaration order
	fieldValues := make([]ast.Expression, len(exprType.StructFields))
	for i, name := range expr.Fields {
		if idx, ok := fieldMap[name]; ok {
			fieldValues[idx] = expr.Values[i]
		}
	}
	for _, val := range fieldValues {
		if val != nil {
			c.compileExpr(val, e)
		} else {
			e.emit0(bytecode.OpCONST_NULL)
		}
	}

	// Add struct type name to constants
	typeIdx := e.addString(exprType.StructName)
	e.emit2(bytecode.OpSTRUCT_NEW, uint64(len(exprType.StructFields)), uint64(typeIdx))
}

// compileMapLiteral compiles a map literal.
func (c *Compiler) compileMapLiteral(expr *ast.MapLiteral, e *emitter) {
	e.emit0(bytecode.OpNEW_MAP)
	for i := range expr.Keys {
		// DUP the map, add the entry via MAP_SET (which pushes modified map back),
		// then POP to discard the extra copy, keeping only the modified map
		e.emit0(bytecode.OpDUP)
		c.compileExpr(expr.Keys[i], e)
		c.compileExpr(expr.Values[i], e)
		e.emit0(bytecode.OpMAP_SET) // pops k,v,dup-map; pushes modified map back
		e.emit0(bytecode.OpPOP)     // discard the modified map from MAP_SET
	}
	// The original map (with all modifications via shared backing array) is on the stack
}

// compileMemberExpr compiles a member access expression.
func (c *Compiler) compileMemberExpr(expr *ast.MemberExpr, e *emitter) {
	// Check if this is an enum variant reference first (don't compile the object)
	exprType := expr.GetExprType()
	if exprType != nil && exprType.Kind == types.KindEnum && exprType.EnumVariant != "" {
		if val, ok := types.EnumVariantValue(exprType); ok {
			e.emit1(bytecode.OpCONST_INT, uint64(val))
		}
		return
	}

	c.compileExpr(expr.Object, e)

	// Check if object type is exception (message/trace field access)
	objType := expr.Object.GetExprType()
	if objType != nil && objType.IsException() {
		switch expr.Member {
		case "message":
			e.emit1(bytecode.OpEXCEPTION_FIELD, 0)
			return
		case "trace":
			e.emit1(bytecode.OpEXCEPTION_FIELD, 1)
			return
		}
	}

	// Check if object type is a struct (field access)
	if objType != nil && objType.Kind == types.KindStruct {
		for i, field := range objType.StructFields {
			if field.Name == expr.Member {
				e.emit1(bytecode.OpFIELD_LOAD, uint64(i))
				return
			}
		}
	}

	// Otherwise, member is a module function — compiled later by compileCall
}

// compileNullCoalescing compiles a ?? expression.
func (c *Compiler) compileNullCoalescing(expr *ast.NullCoalescing, e *emitter) {
	c.compileExpr(expr.Left, e)
	c.compileExpr(expr.Right, e)
	e.emit0(bytecode.OpCOALESCE)
}

// emitJump emits a jump instruction with a placeholder offset.
func (e *emitter) emitJump(op bytecode.Opcode) int {
	return e.emitJumpWithOp(op)
}

// --- Emitter helper ---

type emitter struct {
	code         []byte
	offsets      []int // instruction offsets
	sourceMap    []bytecode.SourceSpan
	maxStack     int
	currStack    int
	pendingJumps []jumpPatch
	constants    []constantEntry
	stringMap    map[string]int // string -> constant index
	handlerInfo  []handlerMeta  // exception handler metadata
}

type jumpPatch struct {
	offset  int  // byte offset of the jump offset operand
	target  int  // target offset if known
	pending bool // if true, target needs to be filled in
}

// handlerMeta stores metadata about exception handlers for patching.
type handlerMeta struct {
	setupOffset   int // byte offset of the SETUP_HANDLER instruction
	stackDepth    int // stack depth to restore on exception
	catchOffset   int // catch handler target offset (0 if none)
	finallyOffset int // finally handler target offset (0 if none)
}

func newEmitter() *emitter {
	return &emitter{
		stringMap: make(map[string]int),
	}
}

// emit0 emits an instruction with no operands.
func (e *emitter) emit0(op bytecode.Opcode) int {
	offset := len(e.code)
	e.code = append(e.code, byte(op))
	info := bytecode.Instructions[op]
	e.currStack -= info.PopCount
	if e.currStack < 0 {
		e.currStack = 0
	}
	e.currStack += info.PushCount
	if e.currStack > e.maxStack {
		e.maxStack = e.currStack
	}
	e.offsets = append(e.offsets, offset)
	return offset
}

// emit1 emits an instruction with one operand.
func (e *emitter) emit1(op bytecode.Opcode, v uint64) int {
	offset := len(e.code)
	e.code = append(e.code, byte(op))
	info := bytecode.Instructions[op]
	e.currStack -= info.PopCount
	if e.currStack < 0 {
		e.currStack = 0
	}
	e.currStack += info.PushCount
	if e.currStack > e.maxStack {
		e.maxStack = e.currStack
	}

	opType := info.Operands[0]
	switch opType {
	case bytecode.OperandUint8:
		e.code = append(e.code, byte(v))
	case bytecode.OperandUint16:
		e.code = append(e.code, byte(v>>8), byte(v))
	case bytecode.OperandUint32, bytecode.OperandFloat32, bytecode.OperandInt32:
		e.code = append(e.code, byte(v>>24), byte(v>>16), byte(v>>8), byte(v))
	case bytecode.OperandInt64, bytecode.OperandFloat64:
		e.code = append(e.code, byte(v>>56), byte(v>>48), byte(v>>40), byte(v>>32), byte(v>>24), byte(v>>16), byte(v>>8), byte(v))
	case bytecode.OperandString, bytecode.OperandFuncIndex:
		e.code = append(e.code, byte(v>>24), byte(v>>16), byte(v>>8), byte(v))
	}

	e.offsets = append(e.offsets, offset)
	return offset
}

// emit2 emits an instruction with two operands.
func (e *emitter) emit2(op bytecode.Opcode, v1, v2 uint64) int {
	offset := len(e.code)
	e.code = append(e.code, byte(op))
	info := bytecode.Instructions[op]
	e.currStack -= info.PopCount
	if e.currStack < 0 {
		e.currStack = 0
	}
	e.currStack += info.PushCount
	if e.currStack > e.maxStack {
		e.maxStack = e.currStack
	}

	opType0 := info.Operands[0]
	switch opType0 {
	case bytecode.OperandUint8:
		e.code = append(e.code, byte(v1))
	case bytecode.OperandUint16:
		e.code = append(e.code, byte(v1>>8), byte(v1))
	case bytecode.OperandUint32, bytecode.OperandFloat32, bytecode.OperandInt32:
		e.code = append(e.code, byte(v1>>24), byte(v1>>16), byte(v1>>8), byte(v1))
	case bytecode.OperandInt64, bytecode.OperandFloat64:
		e.code = append(e.code, byte(v1>>56), byte(v1>>48), byte(v1>>40), byte(v1>>32), byte(v1>>24), byte(v1>>16), byte(v1>>8), byte(v1))
	case bytecode.OperandString, bytecode.OperandFuncIndex:
		e.code = append(e.code, byte(v1>>24), byte(v1>>16), byte(v1>>8), byte(v1))
	}

	opType1 := info.Operands[1]
	switch opType1 {
	case bytecode.OperandUint8:
		e.code = append(e.code, byte(v2))
	case bytecode.OperandUint16:
		e.code = append(e.code, byte(v2>>8), byte(v2))
	case bytecode.OperandUint32, bytecode.OperandFloat32, bytecode.OperandInt32:
		e.code = append(e.code, byte(v2>>24), byte(v2>>16), byte(v2>>8), byte(v2))
	case bytecode.OperandInt64, bytecode.OperandFloat64:
		e.code = append(e.code, byte(v2>>56), byte(v2>>48), byte(v2>>40), byte(v2>>32), byte(v2>>24), byte(v2>>16), byte(v2>>8), byte(v2))
	case bytecode.OperandString, bytecode.OperandFuncIndex:
		e.code = append(e.code, byte(v2>>24), byte(v2>>16), byte(v2>>8), byte(v2))
	}

	e.offsets = append(e.offsets, offset)
	return offset
}

// emit is kept for backward compatibility (used by emitJump, patchJump).
func (e *emitter) emit(op bytecode.Opcode, operands ...uint64) int {
	offset := len(e.code)
	e.code = append(e.code, byte(op))
	_ = offset

	info := bytecode.Instructions[op]
	e.currStack -= info.PopCount
	if e.currStack < 0 {
		e.currStack = 0
	}
	e.currStack += info.PushCount
	if e.currStack > e.maxStack {
		e.maxStack = e.currStack
	}

	for i, opType := range info.Operands {
		v := operands[i]
		switch opType {
		case bytecode.OperandUint8:
			e.code = append(e.code, byte(v))
		case bytecode.OperandUint16:
			e.code = append(e.code, byte(v>>8), byte(v))
		case bytecode.OperandUint32, bytecode.OperandFloat32:
			e.code = append(e.code, byte(v>>24), byte(v>>16), byte(v>>8), byte(v))
		case bytecode.OperandInt32:
			e.code = append(e.code, byte(v>>24), byte(v>>16), byte(v>>8), byte(v))
		case bytecode.OperandInt64, bytecode.OperandFloat64:
			e.code = append(e.code, byte(v>>56), byte(v>>48), byte(v>>40), byte(v>>32), byte(v>>24), byte(v>>16), byte(v>>8), byte(v))
		case bytecode.OperandString, bytecode.OperandFuncIndex:
			e.code = append(e.code, byte(v>>24), byte(v>>16), byte(v>>8), byte(v))
		}
	}

	e.offsets = append(e.offsets, offset)
	return offset
}

func (e *emitter) emitJumpWithOp(op bytecode.Opcode) int {
	offset := len(e.code)
	e.code = append(e.code, byte(op))
	// Placeholder 4 bytes for jump offset
	e.code = append(e.code, 0, 0, 0, 0)
	e.pendingJumps = append(e.pendingJumps, jumpPatch{offset: offset + 1, pending: true})
	e.offsets = append(e.offsets, offset)
	return len(e.pendingJumps) - 1 // return index
}

// finalizeHandler patches the SETUP_HANDLER instruction operands.
// The instruction layout is: [op(1)][catchOffset(int32)][finallyOffset(int32)][stackDepth(uint16)] = 11 bytes total.
// The catch and finally offsets are relative to the instruction end (setupOffset + 11).
func (e *emitter) finalizeHandler(idx int) {
	if idx >= len(e.handlerInfo) {
		return
	}
	h := e.handlerInfo[idx]
	insnEnd := h.setupOffset + 11 // end of the SETUP_HANDLER instruction

	// Encode: [catchOffset(int32)][finallyOffset(int32)][stackDepth(uint16)]
	pos := h.setupOffset + 1

	// catch offset (relative to instruction end, 0 if none)
	catchTarget := h.catchOffset
	if catchTarget > 0 {
		relCatch := catchTarget - insnEnd
		e.code[pos] = byte(relCatch >> 24)
		e.code[pos+1] = byte(relCatch >> 16)
		e.code[pos+2] = byte(relCatch >> 8)
		e.code[pos+3] = byte(relCatch)
	} else {
		e.code[pos] = 0
		e.code[pos+1] = 0
		e.code[pos+2] = 0
		e.code[pos+3] = 0
	}
	pos += 4

	// finally offset (relative to instruction end, 0 if none)
	finallyTarget := h.finallyOffset
	if finallyTarget > 0 {
		relFinally := finallyTarget - insnEnd
		e.code[pos] = byte(relFinally >> 24)
		e.code[pos+1] = byte(relFinally >> 16)
		e.code[pos+2] = byte(relFinally >> 8)
		e.code[pos+3] = byte(relFinally)
	} else {
		e.code[pos] = 0
		e.code[pos+1] = 0
		e.code[pos+2] = 0
		e.code[pos+3] = 0
	}
	pos += 4

	// stack depth (uint16)
	e.code[pos] = byte(h.stackDepth >> 8)
	e.code[pos+1] = byte(h.stackDepth)
}

func (e *emitter) patchJump(jumpIdx int) {
	if jumpIdx >= len(e.pendingJumps) {
		return
	}
	jp := &e.pendingJumps[jumpIdx]
	jp.target = len(e.code)
	jp.pending = false
}

func (e *emitter) resolveJumps() error {
	for _, jp := range e.pendingJumps {
		if jp.pending {
			return fmt.Errorf("unresolved jump at offset %d", jp.offset)
		}
		// The jump offset is from the byte after the operand (i.e., end of instruction)
		instEnd := jp.offset + 4
		jmpValue := jp.target - instEnd
		e.code[jp.offset] = byte(jmpValue >> 24)
		e.code[jp.offset+1] = byte(jmpValue >> 16)
		e.code[jp.offset+2] = byte(jmpValue >> 8)
		e.code[jp.offset+3] = byte(jmpValue)
	}
	return nil
}

func (e *emitter) currentOffset() int {
	return len(e.code)
}

func (e *emitter) lastOpcode() bytecode.Opcode {
	if len(e.code) == 0 {
		return bytecode.OpNOP
	}
	return bytecode.Opcode(e.code[len(e.code)-1])
}

func (e *emitter) addString(s string) int {
	if idx, ok := e.stringMap[s]; ok {
		return idx
	}
	idx := len(e.constants)
	e.constants = append(e.constants, constantEntry{
		kind: bytecode.ConstString,
		str:  s,
	})
	e.stringMap[s] = idx
	return idx
}

func (e *emitter) buildConstants() []bytecode.Constant {
	result := make([]bytecode.Constant, len(e.constants))
	for i, c := range e.constants {
		result[i] = bytecode.Constant{
			Kind: c.kind,
			Data: c.data,
			Str:  c.str,
		}
	}
	return result
}

func (e *emitter) finalize() ([]byte, []bytecode.SourceSpan) {
	return e.code, e.sourceMap
}

// -- Slot management --

// allocateSlot allocates a slot for a variable. It first tries the free pool
// from exited sibling scopes, falling back to a fresh slot. If a scope tracker
// is active (len(c.scopeSlotRefs) > 0), the slot is tracked for cleanup on
// scope exit.
func (c *Compiler) allocateSlot(isRefType bool) int {
	var slot int
	if len(c.freeSlots) > 0 {
		slot = c.freeSlots[len(c.freeSlots)-1]
		c.freeSlots = c.freeSlots[:len(c.freeSlots)-1]
	} else {
		slot = c.nextSlot
		c.nextSlot++
	}
	// Track in current scope if one is active
	if len(c.scopeSlotRefs) > 0 {
		idx := len(c.scopeSlotRefs) - 1
		c.scopeSlotRefs[idx] = append(c.scopeSlotRefs[idx], slotRef{
			index:     slot,
			isRefType: isRefType,
		})
	}
	return slot
}

// exitScope handles slot cleanup when a scope exits. Reference-type slots are
// nulled to release backing data for Go GC. All slots in this scope are
// returned to the free pool for reuse by sibling scopes.
func (c *Compiler) exitScope(e *emitter) {
	if len(c.scopeSlotRefs) == 0 {
		return
	}
	refs := c.scopeSlotRefs[len(c.scopeSlotRefs)-1]
	c.scopeSlotRefs = c.scopeSlotRefs[:len(c.scopeSlotRefs)-1]

	// Null reference-type slots to break Go references
	for _, sr := range refs {
		if sr.isRefType {
			e.emit0(bytecode.OpCONST_NULL)
			e.emit1(bytecode.OpSTORE_LOCAL, uint64(sr.index))
		}
		// Return slot to free pool
		c.freeSlots = append(c.freeSlots, sr.index)
	}
}

func (c *Compiler) registerNative(module, name string, params int, returns bool) {
	key := module + "." + name
	if _, exists := c.nativeMap[key]; !exists {
		idx := len(c.natives)
		c.natives = append(c.natives, bytecode.NativeDecl{
			Module: module,
			Name:   name,
			Params: params,
			Return: returns,
		})
		c.nativeMap[key] = idx
	}
}
