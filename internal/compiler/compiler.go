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
	kind  bytecode.ConstantKind
	data  uint64
	data2 uint64
	str   string
}

// loopInfo tracks the state of a single loop for break/continue compilation.
type loopInfo struct {
	startOffset int   // offset of the loop start (for continue)
	exitOffset  int   // offset of the loop exit (for break), set after compiling body
	breakJumps  []int // indices of pendingJumps for break statements
}

// Compiler compiles a typed AST into bytecode.
type Compiler struct {
	diags     *diagnostic.Diagnostics
	src       *source.Source
	prog      *ast.Program
	scope     *symbol.Scope
	funcs     []*ast.Function
	funcMap   map[string]int
	funcIndex int // current function index
	natives   []bytecode.NativeDecl
	nativeMap map[string]int // "module.name" -> native index
	loops     []loopInfo     // stack of active loops for break/continue
	nextSlot  int            // next available local slot in current function
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
		diags:     diagnostic.NewDiagnostics(),
		src:       src,
		scope:     symbol.NewScope(nil, nil),
		funcMap:   make(map[string]int),
		nativeMap: make(map[string]int),
	}
}

// Compile compiles a program to bytecode.
func (c *Compiler) Compile(prog *ast.Program) (*bytecode.Program, *diagnostic.Diagnostics) {
	c.prog = prog

	// Register native functions - Core module
	c.registerNative("core", "print", 1, true)
	c.registerNative("core", "println", 1, true)
	c.registerNative("core", "string", 1, true)
	c.registerNative("core", "int", 1, true)
	c.registerNative("core", "long", 1, true)
	c.registerNative("core", "double", 1, true)
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

	// Process module
	c.registerNative("process", "run", -1, true) // variadic - uses any arg count

	// Time module
	c.registerNative("time", "now", 0, true)
	c.registerNative("time", "sleep", 1, false)

	// Collect function declarations first
	for _, fn := range prog.Funcs {
		idx := len(c.funcs)
		c.funcs = append(c.funcs, fn)
		c.funcMap[fn.Name] = idx
	}

	// Compile each function
	bcFuncs := make([]bytecode.Function, len(prog.Funcs))
	for i, fn := range prog.Funcs {
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

	bcProg := &bytecode.Program{
		Magic:     bytecode.Magic,
		Version:   bytecode.FormatVersion,
		Functions: bcFuncs,
		Natives:   c.natives,
	}

	return bcProg, c.diags
}

// compileFunction compiles a single function.
func (c *Compiler) compileFunction(fn *ast.Function, fnIdx int) (*bytecode.Function, error) {
	// Build scope for this function
	oldScope := c.scope
	c.scope = symbol.NewScope(oldScope, nil)

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
	if fn.ReturnType != nil && fn.ReturnType.ResolvedType != nil {
		retType = fn.ReturnType.ResolvedType
	} else {
		retType = types.Void
	}

	// Initialize slot counter
	c.nextSlot = 0

	// Declare parameters
	for _, p := range fn.Parameters {
		t := types.Invalid
		if p.Type != nil && p.Type.ResolvedType != nil {
			t = p.Type.ResolvedType
		}
		slot := c.allocateSlot()
		c.scope.Declare(&symbol.Symbol{
			Name:      p.Name,
			Kind:      symbol.KindVariable,
			Type:      t,
			Slot:      slot,
			Parameter: true,
			Defined:   true,
		})
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
	if lastInst != bytecode.OpRETURN && lastInst != bytecode.OpRETURN_VOID {
		if retType.IsVoid() {
			emitter.emit(bytecode.OpRETURN_VOID)
		} else {
			// This shouldn't happen - type checker should catch missing returns
			emitter.emit(bytecode.OpRETURN)
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
	c.scope = oldScope

	return &bytecode.Function{
		Name:       fn.Name,
		ParamCount: len(fn.Parameters),
		LocalCount: localCount,
		MaxStack:   emitter.maxStack,
		Code:       code,
		SourceMap:  sourceMap,
		Constants:  emitter.buildConstants(),
	}, nil
}

// compileBlock compiles a block of statements.
func (c *Compiler) compileBlock(block *ast.Block, e *emitter) {
	if block == nil {
		return
	}

	// Enter new scope
	oldScope := c.scope
	c.scope = symbol.NewScope(oldScope, c.scope.FuncType)

	for _, stmt := range block.Statements {
		c.compileStatement(stmt, e)
	}

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
	} else {
		e.emit(bytecode.OpCONST_NULL)
	}

	// Determine slot
	varType := types.Invalid
	if decl.Type != nil && decl.Type.ResolvedType != nil {
		varType = decl.Type.ResolvedType
	}

	// Allocate slot for this variable
	slot := c.allocateSlot()
	// Declare in compiler's scope
	c.scope.Declare(&symbol.Symbol{
		Name:    decl.Name,
		Kind:    symbol.KindVariable,
		Type:    varType,
		Slot:    slot,
		Defined: true,
	})
	e.emit(bytecode.OpSTORE_LOCAL, uint64(slot))
}

// compileExprStmt compiles an expression statement.
func (c *Compiler) compileExprStmt(stmt *ast.ExprStmt, e *emitter) {
	// Check if it's an assignment (BinaryExpr with BinAssign)
	if be, ok := stmt.Expr.(*ast.BinaryExpr); ok && be.Operator == ast.BinAssign {
		c.compileAssignment(be, e)
		return
	}
	c.compileExpr(stmt.Expr, e)
	e.emit(bytecode.OpPOP) // discard expression value
}

// compileAssignment compiles an assignment :=
func (c *Compiler) compileAssignment(be *ast.BinaryExpr, e *emitter) {
	switch left := be.Left.(type) {
	case *ast.Identifier:
		c.compileIdentAssignment(left, be.Right, e)
	case *ast.IndexExpr:
		c.compileIndexAssignment(left, be.Right, e)
	}
}

// compileIdentAssignment compiles assignment to an identifier.
func (c *Compiler) compileIdentAssignment(ident *ast.Identifier, value ast.Expression, e *emitter) {
	sym := c.scope.Resolve(ident.Name)
	if sym == nil {
		return
	}

	c.compileExpr(value, e)
	e.emit(bytecode.OpSTORE_LOCAL, uint64(sym.Slot))
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
		e.emit(bytecode.OpMAP_SET)
	} else {
		e.emit(bytecode.OpLIST_SET)
	}
}

// compileAssignStmt compiles an assignment statement.
func (c *Compiler) compileAssignStmt(stmt *ast.AssignStmt, e *emitter) {
	sym := c.scope.Resolve(stmt.Name)
	if sym == nil {
		return
	}
	c.compileExpr(stmt.Value, e)
	e.emit(bytecode.OpSTORE_LOCAL, uint64(sym.Slot))
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

	// Compile iterable expression
	c.compileExpr(stmt.Iterable, e)

	iterSlot := c.allocateSlot()
	indexSlot := c.allocateSlot()

	e.emit(bytecode.OpSTORE_LOCAL, uint64(iterSlot)) // save iterable
	e.emit(bytecode.OpCONST_INT, 0)
	e.emit(bytecode.OpSTORE_LOCAL, uint64(indexSlot)) // index = 0

	loopStart := e.currentOffset()

	// Compare: index < length (for List) or index < size (for Map)
	e.emit(bytecode.OpLOAD_LOCAL, uint64(indexSlot)) // push index
	e.emit(bytecode.OpLOAD_LOCAL, uint64(iterSlot))  // push iterable
	if iterType != nil && iterType.Kind == types.KindMap {
		e.emit(bytecode.OpMAP_LENGTH) // pops iterable, pushes size
	} else {
		e.emit(bytecode.OpLIST_LENGTH) // pops iterable, pushes length
	}
	e.emit(bytecode.OpLT_INT)
	exitJump := e.emitJump(bytecode.OpJUMP_IF_FALSE)

	// Get element at index (or key/value for maps)
	if iterType != nil && iterType.Kind == types.KindMap {
		// For maps, we use MAP_GET to get the value by key
		// The keys are stored in order, but we don't have direct index->key mapping
		// For simplicity, dump keys to a temp list and index into that
		e.emit(bytecode.OpLOAD_LOCAL, uint64(iterSlot))
		e.emit(bytecode.OpLOAD_LOCAL, uint64(indexSlot))
		e.emit(bytecode.OpLIST_GET) // HACK: this won't work for maps directly
		// Actually we need a different approach for map iteration
		// For now, emit a simple index-based loop
		_ = e
	} else {
		// For lists/strings, get element at index
		e.emit(bytecode.OpLOAD_LOCAL, uint64(iterSlot))  // push iterable
		e.emit(bytecode.OpLOAD_LOCAL, uint64(indexSlot)) // push index
		e.emit(bytecode.OpLIST_GET)                      // pops iterable+index, pushes element
	}

	// Store in loop variable(s)
	oldScope := c.scope
	c.scope = symbol.NewScope(oldScope, c.scope.FuncType)

	var elemType *types.Type
	if iterType != nil {
		if iterType.Kind == types.KindList && iterType.Element != nil {
			elemType = iterType.Element
		} else if iterType.IsString() {
			elemType = types.Char
		} else if iterType.Kind == types.KindMap {
			elemType = iterType.KeyType
		}
	}
	if elemType == nil {
		elemType = types.Invalid
	}

	if stmt.ValueVariable == "" {
		// Single variable: store element (or key for maps)
		loopVarSlot := c.allocateSlot()
		c.scope.Declare(&symbol.Symbol{
			Name:    stmt.Variable,
			Kind:    symbol.KindVariable,
			Type:    elemType,
			Slot:    loopVarSlot,
			Defined: true,
		})
		e.emit(bytecode.OpSTORE_LOCAL, uint64(loopVarSlot))
	} else {
		// (key, value) unpacking: only for maps
		// Store key and value separately
		_ = stmt.ValueVariable
		// For now, just store the single value (key) and note value is not available
		loopVarSlot := c.allocateSlot()
		c.scope.Declare(&symbol.Symbol{
			Name:    stmt.Variable,
			Kind:    symbol.KindVariable,
			Type:    elemType,
			Slot:    loopVarSlot,
			Defined: true,
		})
		e.emit(bytecode.OpSTORE_LOCAL, uint64(loopVarSlot))
		// Value variable gets null for now (TODO: implement proper key/value iteration)
		valVarSlot := c.allocateSlot()
		c.scope.Declare(&symbol.Symbol{
			Name:    stmt.ValueVariable,
			Kind:    symbol.KindVariable,
			Type:    types.Invalid,
			Slot:    valVarSlot,
			Defined: true,
		})
		e.emit(bytecode.OpCONST_NULL)
		e.emit(bytecode.OpSTORE_LOCAL, uint64(valVarSlot))
	}

	// Push loop info (for break/continue)
	oldLoops := c.loops
	li := loopInfo{startOffset: loopStart, exitOffset: 0}
	c.loops = append(c.loops, li)

	// Body
	c.compileBlock(stmt.Body, e)

	// Increment index
	e.emit(bytecode.OpLOAD_LOCAL, uint64(indexSlot))
	e.emit(bytecode.OpCONST_INT, 1)
	e.emit(bytecode.OpADD_INT)
	e.emit(bytecode.OpSTORE_LOCAL, uint64(indexSlot))

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

	c.scope = oldScope
	c.loops = oldLoops
}

// compileSwitchStmt compiles a switch statement.
// Strategy: compile switch expr once, then for each case DUP it, compile case expr,
// compare, JUMP_IF_FALSE to next case. On match POP switch value, exec body, JUMP to end.
// Default: POP switch value, exec body.
func (c *Compiler) compileSwitchStmt(stmt *ast.SwitchStmt, e *emitter) {
	// Compile the switch expression (once)
	slot := c.allocateSlot()
	c.compileExpr(stmt.Expression, e)
	e.emit(bytecode.OpSTORE_LOCAL, uint64(slot))

	// Jump targets for case matching
	caseJumps := make([]int, len(stmt.Cases))
	endJumps := make([]int, 0)

	for i, cse := range stmt.Cases {
		// Load switch value
		e.emit(bytecode.OpLOAD_LOCAL, uint64(slot))
		// Compile case expression
		c.compileExpr(cse.Expression, e)
		// Compare using REF equality (handles all types at runtime)
		e.emit(bytecode.OpEQ_REF)
		// Jump to next case if not equal
		caseJumps[i] = e.emitJump(bytecode.OpJUMP_IF_FALSE)
		// Equal - pop the switch value and execute body
		e.emit(bytecode.OpPOP) // remove result from EQ_REF
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
		e.emit(bytecode.OpPOP)
		c.compileBlock(stmt.Default, e)
	} else {
		// No default - just pop the switch value
		e.emit(bytecode.OpPOP)
	}

	// Patch all the end jumps
	for _, jmp := range endJumps {
		e.patchJump(jmp)
	}
}

// compileReturnStmt compiles a return statement.
func (c *Compiler) compileReturnStmt(stmt *ast.ReturnStmt, e *emitter) {
	if stmt.Value != nil {
		c.compileExpr(stmt.Value, e)
		e.emit(bytecode.OpRETURN)
	} else {
		e.emit(bytecode.OpRETURN_VOID)
	}
}

// compileBreakStmt compiles a break statement.
func (c *Compiler) compileBreakStmt(stmt *ast.BreakStmt, e *emitter) {
	if len(c.loops) == 0 {
		return
	}
	// Emit jump to loop exit; the exit offset is patched later
	jumpIdx := e.emitJump(bytecode.OpJUMP)
	// Store the jump index so we can patch it when we know the exit offset
	currentLoop := &c.loops[len(c.loops)-1]
	currentLoop.breakJumps = append(currentLoop.breakJumps, jumpIdx)
}

// compileContinueStmt compiles a continue statement.
func (c *Compiler) compileContinueStmt(stmt *ast.ContinueStmt, e *emitter) {
	if len(c.loops) == 0 {
		return
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
		e.emit(bytecode.OpCONST_INT, uint64(int32(ex.Value)))
	case *ast.LongLiteral:
		e.emit(bytecode.OpCONST_LONG, uint64(ex.Value))
	case *ast.FloatLiteral:
		e.emit(bytecode.OpCONST_FLOAT, uint64(math.Float32bits(ex.Value)))
	case *ast.DoubleLiteral:
		e.emit(bytecode.OpCONST_DOUBLE, math.Float64bits(ex.Value))
	case *ast.BoolLiteral:
		if ex.Value {
			e.emit(bytecode.OpCONST_BOOL, 1)
		} else {
			e.emit(bytecode.OpCONST_BOOL, 0)
		}
	case *ast.CharLiteral:
		e.emit(bytecode.OpCONST_CHAR, uint64(ex.Value))
	case *ast.StringLiteral:
		idx := uint64(e.addString(ex.Value))
		e.emit(bytecode.OpCONST_STRING, idx)
	case *ast.ByteLiteral:
		e.emit(bytecode.OpCONST_BYTE, uint64(ex.Value))
	case *ast.NullLiteral:
		e.emit(bytecode.OpCONST_NULL)
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
	case *ast.MemberExpr:
		c.compileMemberExpr(ex, e)
	case *ast.NullCoalescing:
		c.compileNullCoalescing(ex, e)
	}
}

// compileIdentifier compiles an identifier reference.
func (c *Compiler) compileIdentifier(ident *ast.Identifier, e *emitter) {
	sym := c.scope.Resolve(ident.Name)
	if sym != nil {
		e.emit(bytecode.OpLOAD_LOCAL, uint64(sym.Slot))
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
		if operandType != nil && operandType.Kind == types.KindLong {
			e.emit(bytecode.OpNEG_LONG)
		} else if operandType != nil && operandType.Kind == types.KindFloat {
			e.emit(bytecode.OpNEG_FLOAT)
		} else if operandType != nil && operandType.Kind == types.KindDouble {
			e.emit(bytecode.OpNEG_DOUBLE)
		} else {
			e.emit(bytecode.OpNEG_INT)
		}
	case ast.UnaryNot:
		e.emit(bytecode.OpNOT_BOOL)
	case ast.UnaryBitNot:
		if operandType != nil && operandType.Kind == types.KindLong {
			e.emit(bytecode.OpBIT_NOT_LONG)
		} else {
			e.emit(bytecode.OpBIT_NOT_INT)
		}
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
		// String concatenation check via resolved types
		if (leftType != nil && leftType.IsString()) || (rightType != nil && rightType.IsString()) {
			e.emit(bytecode.OpCONCAT_STRING)
		} else if commonType != nil && commonType.IsValid() {
			switch commonType.Kind {
			case types.KindLong:
				e.emit(bytecode.OpADD_LONG)
			case types.KindFloat:
				e.emit(bytecode.OpADD_FLOAT)
			case types.KindDouble:
				e.emit(bytecode.OpADD_DOUBLE)
			default:
				e.emit(bytecode.OpADD_INT)
			}
		} else {
			e.emit(bytecode.OpADD_INT)
		}
	case ast.BinSub:
		if commonType != nil && commonType.IsValid() {
			switch commonType.Kind {
			case types.KindLong:
				e.emit(bytecode.OpSUB_LONG)
			case types.KindFloat:
				e.emit(bytecode.OpSUB_FLOAT)
			case types.KindDouble:
				e.emit(bytecode.OpSUB_DOUBLE)
			default:
				e.emit(bytecode.OpSUB_INT)
			}
		} else {
			e.emit(bytecode.OpSUB_INT)
		}
	case ast.BinMul:
		if commonType != nil && commonType.IsValid() {
			switch commonType.Kind {
			case types.KindLong:
				e.emit(bytecode.OpMUL_LONG)
			case types.KindFloat:
				e.emit(bytecode.OpMUL_FLOAT)
			case types.KindDouble:
				e.emit(bytecode.OpMUL_DOUBLE)
			default:
				e.emit(bytecode.OpMUL_INT)
			}
		} else {
			e.emit(bytecode.OpMUL_INT)
		}
	case ast.BinDiv:
		if commonType != nil && commonType.IsValid() {
			switch commonType.Kind {
			case types.KindLong:
				e.emit(bytecode.OpDIV_LONG)
			case types.KindFloat:
				e.emit(bytecode.OpDIV_FLOAT)
			case types.KindDouble:
				e.emit(bytecode.OpDIV_DOUBLE)
			default:
				e.emit(bytecode.OpDIV_INT)
			}
		} else {
			e.emit(bytecode.OpDIV_INT)
		}
	case ast.BinMod:
		if commonType != nil && commonType.IsValid() {
			switch commonType.Kind {
			case types.KindLong:
				e.emit(bytecode.OpREM_LONG)
			default:
				e.emit(bytecode.OpREM_INT)
			}
		} else {
			e.emit(bytecode.OpREM_INT)
		}
	case ast.BinEq:
		// For equality, use type-specific comparison only when both types are the same numeric/string type
		if (leftType != nil && leftType.IsNull()) || (rightType != nil && rightType.IsNull()) {
			e.emit(bytecode.OpEQ_REF)
		} else if leftType != nil && leftType.IsString() && rightType != nil && rightType.IsString() {
			e.emit(bytecode.OpEQ_STRING)
		} else if leftType != nil && leftType.IsBool() && rightType != nil && rightType.IsBool() {
			e.emit(bytecode.OpEQ_BOOL)
		} else if commonType != nil && commonType.IsValid() && commonType.IsNumeric() {
			switch commonType.Kind {
			case types.KindLong:
				e.emit(bytecode.OpEQ_LONG)
			case types.KindFloat:
				e.emit(bytecode.OpEQ_FLOAT)
			case types.KindDouble:
				e.emit(bytecode.OpEQ_DOUBLE)
			default:
				e.emit(bytecode.OpEQ_INT)
			}
		} else {
			// Reference/structural equality for lists, maps, and other types
			e.emit(bytecode.OpEQ_REF)
		}
	case ast.BinNe:
		if (leftType != nil && leftType.IsNull()) || (rightType != nil && rightType.IsNull()) {
			e.emit(bytecode.OpEQ_REF)
			e.emit(bytecode.OpNOT_BOOL)
		} else if leftType != nil && leftType.IsString() && rightType != nil && rightType.IsString() {
			e.emit(bytecode.OpEQ_STRING)
			e.emit(bytecode.OpNOT_BOOL)
		} else if leftType != nil && leftType.IsBool() && rightType != nil && rightType.IsBool() {
			e.emit(bytecode.OpEQ_BOOL)
			e.emit(bytecode.OpNOT_BOOL)
		} else if commonType != nil && commonType.IsValid() && commonType.IsNumeric() {
			switch commonType.Kind {
			case types.KindLong:
				e.emit(bytecode.OpEQ_LONG)
			case types.KindFloat:
				e.emit(bytecode.OpEQ_FLOAT)
			case types.KindDouble:
				e.emit(bytecode.OpEQ_DOUBLE)
			default:
				e.emit(bytecode.OpEQ_INT)
			}
			e.emit(bytecode.OpNOT_BOOL)
		} else {
			e.emit(bytecode.OpEQ_REF)
			e.emit(bytecode.OpNOT_BOOL)
		}
	case ast.BinLt:
		if commonType != nil && commonType.IsValid() {
			switch commonType.Kind {
			case types.KindLong:
				e.emit(bytecode.OpLT_LONG)
			case types.KindFloat:
				e.emit(bytecode.OpLT_FLOAT)
			case types.KindDouble:
				e.emit(bytecode.OpLT_DOUBLE)
			default:
				e.emit(bytecode.OpLT_INT)
			}
		} else {
			e.emit(bytecode.OpLT_INT)
		}
	case ast.BinLe:
		if commonType != nil && commonType.IsValid() {
			switch commonType.Kind {
			case types.KindLong:
				e.emit(bytecode.OpLE_LONG)
			case types.KindFloat:
				e.emit(bytecode.OpLE_FLOAT)
			case types.KindDouble:
				e.emit(bytecode.OpLE_DOUBLE)
			default:
				e.emit(bytecode.OpLE_INT)
			}
		} else {
			e.emit(bytecode.OpLE_INT)
		}
	case ast.BinGt:
		if commonType != nil && commonType.IsValid() {
			switch commonType.Kind {
			case types.KindLong:
				e.emit(bytecode.OpGT_LONG)
			case types.KindFloat:
				e.emit(bytecode.OpGT_FLOAT)
			case types.KindDouble:
				e.emit(bytecode.OpGT_DOUBLE)
			default:
				e.emit(bytecode.OpGT_INT)
			}
		} else {
			e.emit(bytecode.OpGT_INT)
		}
	case ast.BinGe:
		if commonType != nil && commonType.IsValid() {
			switch commonType.Kind {
			case types.KindLong:
				e.emit(bytecode.OpGE_LONG)
			case types.KindFloat:
				e.emit(bytecode.OpGE_FLOAT)
			case types.KindDouble:
				e.emit(bytecode.OpGE_DOUBLE)
			default:
				e.emit(bytecode.OpGE_INT)
			}
		} else {
			e.emit(bytecode.OpGE_INT)
		}
	case ast.BinBitAnd:
		if commonType != nil && commonType.Kind == types.KindLong {
			e.emit(bytecode.OpBIT_AND_LONG)
		} else {
			e.emit(bytecode.OpBIT_AND_INT)
		}
	case ast.BinBitOr:
		if commonType != nil && commonType.Kind == types.KindLong {
			e.emit(bytecode.OpBIT_OR_LONG)
		} else {
			e.emit(bytecode.OpBIT_OR_INT)
		}
	case ast.BinBitXor:
		if commonType != nil && commonType.Kind == types.KindLong {
			e.emit(bytecode.OpBIT_XOR_LONG)
		} else {
			e.emit(bytecode.OpBIT_XOR_INT)
		}
	case ast.BinShiftLeft:
		if commonType != nil && commonType.Kind == types.KindLong {
			e.emit(bytecode.OpSHIFT_LEFT_LONG)
		} else {
			e.emit(bytecode.OpSHIFT_LEFT_INT)
		}
	case ast.BinShiftRight:
		if commonType != nil && commonType.Kind == types.KindLong {
			e.emit(bytecode.OpSHIFT_RIGHT_LONG)
		} else {
			e.emit(bytecode.OpSHIFT_RIGHT_INT)
		}
	case ast.BinConcat:
		e.emit(bytecode.OpCONCAT_STRING)
	}
}

// compileAnd compiles short-circuit &&.
func (c *Compiler) compileAnd(expr *ast.BinaryExpr, e *emitter) {
	c.compileExpr(expr.Left, e)
	endJump := e.emitJump(bytecode.OpJUMP_IF_FALSE) // if left is false, skip right
	c.compileExpr(expr.Right, e)
	e.patchJump(endJump)
}

// compileOr compiles short-circuit ||.
func (c *Compiler) compileOr(expr *ast.BinaryExpr, e *emitter) {
	c.compileExpr(expr.Left, e)
	trueJump := e.emitJump(bytecode.OpJUMP_IF_TRUE) // if left is true, skip right
	c.compileExpr(expr.Right, e)
	e.patchJump(trueJump)
}

// compileCall compiles a function call.
func (c *Compiler) compileCall(expr *ast.CallExpr, e *emitter) {
	// Handle module.function() calls
	if member, ok := expr.Function.(*ast.MemberExpr); ok {
		if ident, ok := member.Object.(*ast.Identifier); ok {
			// Check for module-qualified native functions: core.print etc.
			fullName := ident.Name + "." + member.Member
			if nativeIdx, exists := c.nativeMap[fullName]; exists {
				for _, arg := range expr.Args {
					c.compileExpr(arg, e)
				}
				e.emit(bytecode.OpCALL_NATIVE, uint64(nativeIdx), uint64(len(expr.Args)))
				return
			}
			// Check for module-qualified user functions
			if fnIdx, exists := c.funcMap[fullName]; exists {
				for _, arg := range expr.Args {
					c.compileExpr(arg, e)
				}
				e.emit(bytecode.OpCALL, uint64(fnIdx), uint64(len(expr.Args)))
				return
			}
		}
	}

	// Check if it's a simple identifier (native or user function)
	if ident, ok := expr.Function.(*ast.Identifier); ok {
		// Check native functions first (unqualified)
		if nativeIdx, exists := c.nativeMap["core."+ident.Name]; exists {
			for _, arg := range expr.Args {
				c.compileExpr(arg, e)
			}
			e.emit(bytecode.OpCALL_NATIVE, uint64(nativeIdx), uint64(len(expr.Args)))
			return
		}

		// Check user functions (unqualified, current module)
		if fnIdx, exists := c.funcMap[ident.Name]; exists {
			for _, arg := range expr.Args {
				c.compileExpr(arg, e)
			}
			e.emit(bytecode.OpCALL, uint64(fnIdx), uint64(len(expr.Args)))
			return
		}

		// Check external functions (cross-module reference)
		if c.allFunctions != nil && c.allFuncMap != nil {
			for mangled, extIdx := range c.allFuncMap {
				dotIdx := -1
				for i := len(mangled) - 1; i >= 0; i-- {
					if mangled[i] == '.' {
						dotIdx = i
						break
					}
				}
				if dotIdx >= 0 && mangled[dotIdx+1:] == ident.Name {
					for _, arg := range expr.Args {
						c.compileExpr(arg, e)
					}
					e.emit(bytecode.OpCALL, uint64(extIdx), uint64(len(expr.Args)))
					return
				}
			}
		}
	}

	// Generic call
	c.compileExpr(expr.Function, e)
	for _, arg := range expr.Args {
		c.compileExpr(arg, e)
	}
}

// compileIndex compiles an indexing expression (list[index] or map[key]).
func (c *Compiler) compileIndex(expr *ast.IndexExpr, e *emitter) {
	c.compileExpr(expr.Target, e)
	c.compileExpr(expr.Index, e)

	targetType := expr.Target.GetExprType()
	if targetType != nil && targetType.Kind == types.KindMap {
		e.emit(bytecode.OpMAP_GET)
	} else {
		e.emit(bytecode.OpLIST_GET)
	}
}

// compileListLiteral compiles a list literal.
func (c *Compiler) compileListLiteral(expr *ast.ListLiteral, e *emitter) {
	e.emit(bytecode.OpNEW_LIST, uint64(len(expr.Elements)))
	for _, el := range expr.Elements {
		c.compileExpr(el, e)
		e.emit(bytecode.OpLIST_APPEND)
	}
}

// compileMapLiteral compiles a map literal.
func (c *Compiler) compileMapLiteral(expr *ast.MapLiteral, e *emitter) {
	e.emit(bytecode.OpNEW_MAP)
	for i := range expr.Keys {
		// DUP the map, add the entry via MAP_SET (which pushes modified map back),
		// then POP to discard the extra copy, keeping only the modified map
		e.emit(bytecode.OpDUP)
		c.compileExpr(expr.Keys[i], e)
		c.compileExpr(expr.Values[i], e)
		e.emit(bytecode.OpMAP_SET) // pops k,v,dup-map; pushes modified map back
		e.emit(bytecode.OpPOP)     // discard the modified map from MAP_SET
	}
	// The original map (with all modifications via shared backing array) is on the stack
}

// compileMemberExpr compiles a member access expression (module.function).
// Currently, member expressions are only used in call contexts,
// so we just compile the object and trust the call handler.
func (c *Compiler) compileMemberExpr(expr *ast.MemberExpr, e *emitter) {
	// MemberExpr is typically used in calls, handled by compileCall
	// For standalone evaluation, compile the object
	c.compileExpr(expr.Object, e)
}

// compileNullCoalescing compiles a ?? expression.
func (c *Compiler) compileNullCoalescing(expr *ast.NullCoalescing, e *emitter) {
	c.compileExpr(expr.Left, e)
	c.compileExpr(expr.Right, e)
	e.emit(bytecode.OpCOALESCE)
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
}

type jumpPatch struct {
	offset  int  // byte offset of the jump offset operand
	target  int  // target offset if known
	pending bool // if true, target needs to be filled in
}

func newEmitter() *emitter {
	return &emitter{
		stringMap: make(map[string]int),
	}
}

func (e *emitter) emit(op bytecode.Opcode, operands ...uint64) int {
	offset := len(e.code)
	e.code = append(e.code, byte(op))
	_ = offset

	info := bytecode.Instructions[op]
	// Track stack depth
	popCount := info.PopCount
	pushCount := info.PushCount
	e.currStack -= popCount
	if e.currStack < 0 {
		e.currStack = 0
	}
	e.currStack += pushCount
	if e.currStack > e.maxStack {
		e.maxStack = e.currStack
	}

	// Write operands
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
			Kind:  c.kind,
			Data:  c.data,
			Data2: c.data2,
			Str:   c.str,
		}
	}
	return result
}

func (e *emitter) finalize() ([]byte, []bytecode.SourceSpan) {
	return e.code, e.sourceMap
}

// -- Slot management --

func (c *Compiler) allocateSlot() int {
	slot := c.nextSlot
	c.nextSlot++
	return slot
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
