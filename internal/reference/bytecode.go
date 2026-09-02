package reference

// Semantic bytecode compiler and VM.
//
// This is deliberately separate from the historical typed-AST compiler.  The
// semantic frontend is the language authority, so its AST is lowered into a
// compact instruction stream here and executed by a real instruction
// dispatcher.  The instruction operands retain source metadata where that is
// useful for pattern matching and diagnostics, but execution never walks a
// Block/Stmt tree.

import (
	"fmt"
	"math"
	"regexp"
)

type bcOp uint8

const (
	bcPush bcOp = iota
	bcLoad
	bcStore
	bcPop
	bcUnary
	bcBinary
	bcCall
	bcMember
	bcIndex
	bcList
	bcMap
	bcStruct
	bcMakeClosure
	bcAssignPrepare
	bcAssign
	bcJump
	bcJumpFalse
	bcJumpTrue
	bcJumpNotNull
	bcIf
	bcWhile
	bcFor
	bcSwitch
	bcTry
	bcThrow
	bcReturn
	bcBreak
	bcContinue
	bcNoop
)

type bcInstruction struct {
	op    bcOp
	a, b  int
	name  string
	value any
	code  *bcCode
	code2 *bcCode
}

type bcCode struct{ instructions []bcInstruction }

type bcFunction struct {
	decl *FunctionDecl
	code *bcCode
}

// bcCallable is the runtime representation of both named functions and
// closures.  Captures are represented by the lexical environment pointer, so
// mutable captures share their binding cell with the creator.
type bcCallable struct {
	function        *bcFunction
	env             *env
	pkg             string
	receiver        *structValue
	receiverMutable bool
}

type bcCompiler struct{ returnType TypeRef }

func (bcCompiler) compileProgram(in *Interpreter) map[*FunctionDecl]*bcFunction {
	out := map[*FunctionDecl]*bcFunction{}
	for _, ns := range in.packages {
		for _, value := range ns.values {
			if fn, ok := value.(*userFunction); ok {
				out[fn.decl] = &bcFunction{decl: fn.decl}
			}
		}
	}
	for _, decl := range in.structs {
		for _, method := range decl.Methods {
			if _, exists := out[method]; !exists {
				out[method] = &bcFunction{decl: method}
			}
		}
	}
	for _, fn := range out {
		seedBCReturnTypes(fn.decl.Body, fn.decl.ReturnType)
		fn.code = (bcCompiler{returnType: fn.decl.ReturnType}).compileBlock(fn.decl.Body)
	}
	return out
}

func seedBCReturnTypes(block *Block, typ TypeRef) {
	if block == nil {
		return
	}
	for _, raw := range block.Statements {
		switch stmt := raw.(type) {
		case *ReturnStmt:
			seedBCExpectedType(stmt.Value, typ)
		case *Block:
			seedBCReturnTypes(stmt, typ)
		case *IfStmt:
			seedBCReturnTypes(stmt.ThenBlock, typ)
			if branch, ok := stmt.ElseBranch.(*Block); ok {
				seedBCReturnTypes(branch, typ)
			}
			if branch, ok := stmt.ElseBranch.(*IfStmt); ok {
				seedBCReturnTypes(branch.ThenBlock, typ)
			}
		case *WhileStmt:
			seedBCReturnTypes(stmt.Body, typ)
		case *ForStmt:
			seedBCReturnTypes(stmt.Body, typ)
		case *SwitchStmt:
			for _, branch := range stmt.Cases {
				seedBCReturnTypes(branch.Body, typ)
			}
		case *TryStmt:
			seedBCReturnTypes(stmt.TryBlock, typ)
			seedBCReturnTypes(stmt.CatchBlock, typ)
			seedBCReturnTypes(stmt.FinallyBlk, typ)
		}
	}
}

func (bcCompiler) compileBlock(block *Block) *bcCode {
	code := &bcCode{}
	if block == nil {
		return code
	}
	for _, stmt := range block.Statements {
		code.emitStmt(stmt)
	}
	return code
}

// compileBlock is also available from an already-emitting code object for
// structured child bodies. Return-type seeding is performed before the parent
// function is lowered, so these child blocks share the prepared AST metadata.
func (c *bcCode) compileBlock(block *Block) *bcCode { return (bcCompiler{}).compileBlock(block) }

func (c *bcCode) emit(i bcInstruction) { c.instructions = append(c.instructions, i) }

func (c *bcCode) emitStmt(stmt any) {
	switch s := stmt.(type) {
	case *Block:
		c.emit(bcInstruction{op: bcNoop, code: c.compileBlock(s)})
	case *VarDecl:
		seedBCExpectedType(s.Value, s.Type)
		if s.Value != nil {
			c.emitExpr(s.Value)
		} else {
			c.emit(bcInstruction{op: bcPush, value: nil})
		}
		c.emit(bcInstruction{op: bcStore, name: s.Name, value: s})
	case *ExprStmt:
		c.emitExpr(s.Expr)
		c.emit(bcInstruction{op: bcPop})
	case *IfStmt:
		c.emitExpr(s.Condition)
		c.emit(bcInstruction{op: bcIf, code: c.compileBlock(s.ThenBlock), value: s.ElseBranch})
	case *WhileStmt:
		c.emit(bcInstruction{op: bcWhile, value: s.Condition, code: c.compileBlock(s.Body)})
	case *ForStmt:
		c.emit(bcInstruction{op: bcFor, value: s, code: c.compileBlock(s.Body)})
	case *SwitchStmt:
		c.emit(bcInstruction{op: bcSwitch, value: s})
	case *TryStmt:
		c.emit(bcInstruction{op: bcTry, value: s, code: c.compileBlock(s.TryBlock), code2: c.compileBlock(s.CatchBlock)})
	case *ThrowStmt:
		c.emitExpr(s.Value)
		c.emit(bcInstruction{op: bcThrow})
	case *ReturnStmt:
		if s.Value != nil {
			c.emitExpr(s.Value)
		} else {
			c.emit(bcInstruction{op: bcPush, value: nil})
		}
		c.emit(bcInstruction{op: bcReturn})
	case *BreakStmt:
		c.emit(bcInstruction{op: bcBreak})
	case *ContinueStmt:
		c.emit(bcInstruction{op: bcContinue})
	}
}

func seedBCExpectedType(value any, typ TypeRef) {
	if value == nil {
		return
	}
	if expr, ok := value.(*StructExpr); ok && len(expr.TypeArgs) == 0 && typ.Name == expr.TypeName && len(typ.Args) > 0 {
		expr.expectedType = typ.nonnull()
	}
	if call, ok := value.(*Call); ok && len(call.TypeArgs) == 0 {
		if member, ok := call.Callee.(*Member); ok {
			if dotted, ok := dottedExpressionName(member.Obj); ok {
				_, local := splitTypeName(dotted)
				_, target := splitTypeName(typ.Name)
				if local == target && len(typ.Args) > 0 {
					call.expectedType = typ.nonnull()
				}
			}
		}
	}
}

func (c *bcCode) emitExpr(expr any) {
	switch x := expr.(type) {
	case *Literal:
		c.emit(bcInstruction{op: bcPush, value: x.Value})
	case *Name:
		c.emit(bcInstruction{op: bcLoad, name: x.Name})
	case *FuncExpr:
		seedBCReturnTypes(x.Body, x.ReturnType)
		c.emit(bcInstruction{op: bcMakeClosure, value: x})
	case *Unary:
		c.emitExpr(x.Expr)
		c.emit(bcInstruction{op: bcUnary, name: x.Op})
	case *Binary:
		// Preserve language short-circuiting and null-coalescing without
		// evaluating the right side eagerly.
		if x.Op == "&&" || x.Op == "||" || x.Op == "??" {
			c.emitExpr(x.Left)
			c.emit(bcInstruction{op: bcJumpFalse, name: x.Op, value: x.Right})
			return
		}
		c.emitExpr(x.Left)
		c.emitExpr(x.Right)
		c.emit(bcInstruction{op: bcBinary, name: x.Op})
	case *Assign:
		c.emit(bcInstruction{op: bcAssignPrepare, value: x})
		c.emitExpr(x.Value)
		c.emit(bcInstruction{op: bcAssign, value: x})
	case *Call:
		c.emitExpr(x.Callee)
		for _, arg := range x.Args {
			c.emitExpr(arg.Expr)
		}
		c.emit(bcInstruction{op: bcCall, a: len(x.Args), value: x})
	case *Member:
		c.emitExpr(x.Obj)
		c.emit(bcInstruction{op: bcMember, name: x.Name, value: x})
	case *Index:
		c.emitExpr(x.Obj)
		c.emitExpr(x.Index)
		c.emit(bcInstruction{op: bcIndex})
	case *ListExpr:
		for _, item := range x.Items {
			c.emitExpr(item)
		}
		c.emit(bcInstruction{op: bcList, a: len(x.Items)})
	case *MapExpr:
		for _, item := range x.Items {
			c.emitExpr(item.Key)
			c.emitExpr(item.Value)
		}
		c.emit(bcInstruction{op: bcMap, a: len(x.Items)})
	case *StructExpr:
		for _, field := range x.Fields {
			c.emitExpr(field.Value)
		}
		c.emit(bcInstruction{op: bcStruct, a: len(x.Fields), name: x.TypeName, value: x})
	default:
		c.emit(bcInstruction{op: bcPush, value: nil})
	}
}

type bcVM struct {
	in    *Interpreter
	stack []any
	funcs map[*FunctionDecl]*bcFunction
}

func (in *Interpreter) runBytecode(entryPackage string) (int, error) {
	in.entryPackage = entryPackage
	functions := (bcCompiler{}).compileProgram(in)
	for pkg, ns := range in.packages {
		for name, value := range ns.values {
			if fn, ok := value.(*userFunction); ok {
				if compiled := functions[fn.decl]; compiled != nil {
					ns.values[name] = &bcCallable{function: compiled, env: nil, pkg: pkg}
				}
			}
		}
	}
	ns := in.packages[entryPackage]
	main, ok := ns.values["main"].(*bcCallable)
	if !ok {
		return 1, newSolvikError("package %q has no main function", entryPackage)
	}
	vm := &bcVM{in: in, funcs: functions}
	result, err := vm.call(main, nil, false, nil, nil)
	if err != nil {
		return 2, err
	}
	if n, ok := result.(int64); ok {
		return int(n), nil
	}
	if n, ok := result.(float64); ok {
		return int(n), nil
	}
	return 0, nil
}

func (vm *bcVM) push(v any) { vm.stack = append(vm.stack, v) }
func (vm *bcVM) pop() any {
	n := len(vm.stack) - 1
	if n < 0 {
		return nil
	}
	v := vm.stack[n]
	vm.stack = vm.stack[:n]
	return v
}

func (vm *bcVM) run(code *bcCode, e *env, pkg string, receiver *structValue, receiverMutable bool) {
	for pc := 0; pc < len(code.instructions); pc++ {
		i := code.instructions[pc]
		switch i.op {
		case bcPush:
			vm.push(copyValue(i.value))
		case bcLoad:
			if binding := e.tryFindBinding(i.name); binding != nil {
				// Keep reference-bearing collection/struct values attached to their
				// binding. Assignment mutability is enforced by assignTarget; a
				// stack or list method must still mutate the stored object.
				vm.push(binding.value)
			} else {
				vm.push(vm.in.resolveName(i.name, e, pkg, receiver, receiverMutable))
			}
		case bcStore:
			v := vm.pop()
			decl := i.value.(*VarDecl)
			if decl.Type.Name != "" {
				v = vm.in.coerceForType(v, decl.Type)
			}
			e.declare(i.name, v, decl.Type, decl.Mutable)
		case bcPop:
			vm.pop()
		case bcNoop:
			if i.code != nil {
				vm.run(i.code, newEnv(e), pkg, receiver, receiverMutable)
			}
		case bcUnary:
			vm.push(vm.unary(i.name, vm.pop()))
		case bcBinary:
			right, left := vm.pop(), vm.pop()
			vm.push(vm.binary(i.name, left, right))
		case bcAssign:
			v := vm.pop()
			assignment := i.value.(*Assign)
			vm.push(vm.assign(assignment.Target, v, e, pkg, receiver, receiverMutable))
		case bcCall:
			rawArgs := make([]any, i.a)
			for n := i.a - 1; n >= 0; n-- {
				rawArgs[n] = vm.pop()
			}
			callee := vm.pop()
			call := i.value.(*Call)
			args := make([]any, 0, len(rawArgs))
			for n, value := range rawArgs {
				if call.Args[n].Spread {
					items, ok := value.([]any)
					if !ok {
						panic(runtimeErr("spread requires a list"))
					}
					args = append(args, items...)
				} else {
					args = append(args, value)
				}
			}
			hints := make([]any, len(call.Args))
			for n, arg := range call.Args {
				if arg.Spread {
					continue
				}
				hints[n] = vm.in.declaredTypeOf(arg.Expr, e, receiver, pkg)
			}
			if ctor, ok := callee.(*caseConstructor); ok {
				var expected *TypeRef
				if typ, ok := call.expectedType.(TypeRef); ok {
					expected = &typ
				}
				vm.push(vm.in.constructEnumCase(ctor, args, expected, hints))
				continue
			}
			v, err := vm.call(callee, args, receiverMutable, call.TypeArgs, hints)
			if err != nil {
				panic(err)
			}
			vm.push(v)
		case bcMember:
			obj := vm.pop()
			vm.push(vm.member(obj, i.name, i.value.(*Member), e, pkg, receiver, receiverMutable))
		case bcIndex:
			idx, obj := vm.pop(), vm.pop()
			vm.push(vm.index(obj, idx))
		case bcList:
			items := make([]any, i.a)
			for n := i.a - 1; n >= 0; n-- {
				items[n] = vm.pop()
			}
			vm.push(items)
		case bcMap:
			m := newSolvikMap()
			pairs := make([]any, i.a*2)
			for n := len(pairs) - 1; n >= 0; n-- {
				pairs[n] = vm.pop()
			}
			for n := 0; n < i.a; n++ {
				m.set(pairs[2*n], pairs[2*n+1])
			}
			vm.push(m)
		case bcStruct:
			values := make([]any, i.a)
			for n := i.a - 1; n >= 0; n-- {
				values[n] = vm.pop()
			}
			vm.push(vm.structValue(i.name, i.value.(*StructExpr), values, e, pkg, receiver, receiverMutable))
		case bcMakeClosure:
			x := i.value.(*FuncExpr)
			params := append([]Param(nil), x.Params...)
			decl := &FunctionDecl{Name: "<closure>", Params: params, ReturnType: x.ReturnType, Body: x.Body, Pos: x.Pos}
			fn := &bcFunction{decl: decl, code: (bcCompiler{}).compileBlock(x.Body)}
			vm.push(&bcCallable{function: fn, env: e, pkg: pkg, receiver: receiver, receiverMutable: receiverMutable})
		case bcAssignPrepare:
			assignment := i.value.(*Assign)
			if typ, ok := vm.in.targetDeclaredType(assignment.Target, e, pkg, receiver).(TypeRef); ok {
				seedBCExpectedType(assignment.Value, typ)
			}
		case bcIf:
			cond := vm.pop()
			if truth(cond) {
				vm.run(i.code, newEnv(e), pkg, receiver, receiverMutable)
			} else if branch, ok := i.value.(*IfStmt); ok {
				vm.run((bcCompiler{}).compileBlock(branch.ThenBlock), newEnv(e), pkg, receiver, receiverMutable)
			} else if b, ok := i.value.(*Block); ok {
				vm.run((bcCompiler{}).compileBlock(b), newEnv(e), pkg, receiver, receiverMutable)
			}
		case bcWhile:
			for truth(vm.evalExpr(i.value, e, pkg, receiver, receiverMutable)) {
				broke := false
				func() {
					defer func() {
						if r := recover(); r != nil {
							if _, ok := r.(*breakSignal); ok {
								broke = true
								return
							}
							if _, ok := r.(*continueSignal); ok {
								return
							}
							panic(r)
						}
					}()
					vm.run(i.code, newEnv(e), pkg, receiver, receiverMutable)
				}()
				if broke {
					break
				}
			}
		case bcFor:
			vm.runFor(i.value.(*ForStmt), i.code, e, pkg, receiver, receiverMutable)
		case bcSwitch:
			vm.runSwitch(i.value.(*SwitchStmt), e, pkg, receiver, receiverMutable)
		case bcTry:
			vm.runTry(i.value.(*TryStmt), i.code, i.code2, e, pkg, receiver, receiverMutable)
		case bcThrow:
			v := vm.pop()
			if s, ok := v.(string); ok {
				panic(&exceptionValue{message: s})
			}
			if ex, ok := v.(*exceptionValue); ok {
				panic(ex)
			}
			panic(runtimeErr("throw requires string or exception"))
		case bcReturn:
			panic(&returnSignal{value: vm.pop()})
		case bcBreak:
			panic(&breakSignal{})
		case bcContinue:
			panic(&continueSignal{})
		case bcJumpFalse, bcJumpTrue, bcJumpNotNull:
			left := vm.pop()
			right := i.value
			switch i.name {
			case "??":
				if left != nil {
					vm.push(left)
				} else {
					vm.push(vm.evalExpr(right, e, pkg, receiver, receiverMutable))
				}
			case "&&":
				if !truth(left) {
					vm.push(false)
				} else {
					vm.push(truth(vm.evalExpr(right, e, pkg, receiver, receiverMutable)))
				}
			case "||":
				if truth(left) {
					vm.push(true)
				} else {
					vm.push(truth(vm.evalExpr(right, e, pkg, receiver, receiverMutable)))
				}
			}
		}
	}
}

type loopDone struct{}

func (vm *bcVM) evalExpr(expr any, e *env, pkg string, receiver *structValue, receiverMutable bool) any {
	tmp := &bcCode{}
	tmp.emitExpr(expr)
	vm.run(tmp, e, pkg, receiver, receiverMutable)
	return vm.pop()
}

func (vm *bcVM) unary(op string, value any) any {
	v := numericValue(value)
	switch op {
	case "!":
		return !truth(v)
	case "-":
		switch n := v.(type) {
		case int64:
			return -n
		case float64:
			return -n
		}
	case "+":
		return v
	case "~":
		if n, ok := v.(int64); ok {
			return ^n
		}
	}
	panic(runtimeErr("unsupported unary operator %s", op))
}

func (vm *bcVM) binary(op string, left, right any) any {
	if op == "&&" {
		return truth(left) && truth(right)
	}
	if op == "||" {
		return truth(left) || truth(right)
	}
	if op == "??" {
		if left != nil {
			return left
		}
		return right
	}
	if op == "==" {
		return vm.in.equal(left, right)
	}
	if op == "!=" {
		return !vm.in.equal(left, right)
	}
	if op == ".." {
		return solvikString(left) + solvikString(right)
	}
	if op == "<" || op == "<=" || op == ">" || op == ">=" {
		return compareValues(left, right, op)
	}
	if op == "+" || op == "-" || op == "*" || op == "/" || op == "%" {
		a := numericValue(left)
		if af, ok := a.(float64); ok {
			bf := toFloat64BC(right)
			switch op {
			case "+":
				return af + bf
			case "-":
				return af - bf
			case "*":
				return af * bf
			case "/":
				return af / bf
			case "%":
				return math.Mod(af, bf)
			}
		}
		ai := toIntLike(left)
		bi := toIntLike(right)
		switch op {
		case "+":
			return ai + bi
		case "-":
			return ai - bi
		case "*":
			return ai * bi
		case "/":
			if bi == 0 {
				panic(runtimeErrCode("E031", "division by zero"))
			}
			return ai / bi
		case "%":
			if bi == 0 {
				panic(runtimeErrCode("E031", "division by zero"))
			}
			return ai % bi
		}
	}
	if op == "&" || op == "|" || op == "^" || op == "<<" || op == ">>" {
		a, b := toIntLike(left), toIntLike(right)
		switch op {
		case "&":
			return a & b
		case "|":
			return a | b
		case "^":
			return a ^ b
		case "<<":
			return a << uint(b)
		case ">>":
			return a >> uint(b)
		}
	}
	panic(runtimeErr("unsupported binary operator %s", op))
}

func compareValues(a, b any, op string) bool {
	var n int
	switch x := a.(type) {
	case string:
		n = cmpString(x, fmt.Sprint(b))
	case int64:
		n = cmpInt(x, toIntLike(b))
	case float64:
		y := toFloat64BC(b)
		if x < y {
			n = -1
		} else if x > y {
			n = 1
		}
	default:
		if fmt.Sprint(a) < fmt.Sprint(b) {
			n = -1
		} else if fmt.Sprint(a) > fmt.Sprint(b) {
			n = 1
		}
	}
	switch op {
	case "<":
		return n < 0
	case "<=":
		return n <= 0
	case ">":
		return n > 0
	default:
		return n >= 0
	}
}
func cmpString(a, b string) int {
	if a < b {
		return -1
	}
	if a > b {
		return 1
	}
	return 0
}
func cmpInt(a, b int64) int {
	if a < b {
		return -1
	}
	if a > b {
		return 1
	}
	return 0
}
func toFloat64BC(v any) float64 {
	switch x := numericValue(v).(type) {
	case float64:
		return x
	case int64:
		return float64(x)
	}
	return 0
}

func (vm *bcVM) assign(target any, value any, e *env, pkg string, receiver *structValue, receiverMutable bool) any {
	switch t := target.(type) {
	case *Name:
		return vm.in.assignTarget(t, value, e, pkg, receiver, receiverMutable)
	case *Member:
		return vm.in.assignTarget(t, value, e, pkg, receiver, receiverMutable)
	case *Index:
		return vm.in.assignTarget(t, value, e, pkg, receiver, receiverMutable)
	}
	panic(runtimeErr("invalid assignment target"))
}

func (vm *bcVM) index(obj, idx any) any {
	if obj == nil {
		panic(runtimeErrCode("E031", "null reference"))
	}
	switch x := obj.(type) {
	case string:
		i := int(toIntLike(idx))
		r := []rune(x)
		if i < 0 || i >= len(r) {
			panic(runtimeErrCode("E031", "index out of range"))
		}
		return charValue(string(r[i]))
	case []any:
		i := int(toIntLike(idx))
		if i < 0 || i >= len(x) {
			panic(runtimeErrCode("E031", "index out of range"))
		}
		return copyValue(x[i])
	case *solvikMap:
		v, ok := x.get(idx)
		if !ok {
			panic(runtimeErrCode("E031", "map key not found"))
		}
		return copyValue(v)
	}
	panic(runtimeErr("value is not indexable"))
}

func (vm *bcVM) structValue(name string, x *StructExpr, values []any, e *env, pkg string, receiver *structValue, receiverMutable bool) any {
	decl := vm.in.structs[vm.in.typeKeyOf(name)]
	if decl == nil {
		panic(runtimeErr("unknown struct %s", name))
	}
	fields := map[string]any{}
	for n, f := range x.Fields {
		fields[f.Name] = values[n]
	}
	if len(fields) != len(decl.Fields) {
		panic(runtimeErr("struct literal for %s must initialize every field exactly once", name))
	}
	variables := typeParamNames(decl.TypeParams)
	typeBindings := map[string]TypeRef{}
	for n, parameter := range decl.TypeParams {
		if n < len(x.TypeArgs) {
			typeBindings[parameter.Name] = vm.in.resolveRuntimeType(x.TypeArgs[n])
		}
	}
	if expected, ok := x.expectedType.(TypeRef); ok {
		_, local := splitTypeName(expected.Name)
		if local == decl.Name && len(expected.Args) == len(decl.TypeParams) {
			for n, parameter := range decl.TypeParams {
				if _, exists := typeBindings[parameter.Name]; !exists {
					typeBindings[parameter.Name] = vm.in.resolveRuntimeType(expected.Args[n])
				}
			}
		}
	}
	for _, field := range decl.Fields {
		raw, exists := fields[field.Name]
		if !exists {
			panic(runtimeErr("struct literal for %s must initialize every field exactly once", name))
		}
		bindTypePattern(field.Type, valueTypeRef(raw), variables, typeBindings)
	}
	for _, parameter := range decl.TypeParams {
		actual := bindingOrUnknown(typeBindings, parameter.Name)
		if actual.equal(unknownT) {
			panic(runtimeErrCode("E067", "cannot infer type parameter %s for struct %s; annotate the declaration or use explicit type arguments like %s<...> [line %d]", parameter.Name, decl.Name, decl.Name, x.Pos.Line))
		}
		for _, constraint := range parameter.Constraints {
			need := substituteType(constraint, typeBindings)
			if !vm.in.typeSatisfiesTrait(actual, need) {
				panic(runtimeErrCode("E067", "type %s does not satisfy generic constraint %s", actual, need))
			}
		}
	}
	for _, field := range decl.Fields {
		fields[field.Name] = vm.in.coerceForType(fields[field.Name], substituteType(field.Type, typeBindings))
	}
	typeArgs := make([]TypeRef, len(decl.TypeParams))
	for n, parameter := range decl.TypeParams {
		typeArgs[n] = bindingOrUnknown(typeBindings, parameter.Name)
	}
	canonicalName := name
	if owner, _ := splitTypeName(name); owner == "" {
		canonicalName = pkg + "." + name
	}
	return &structValue{typeName: canonicalName, fields: fields, typeArgs: typeArgs}
}

func (vm *bcVM) member(obj any, name string, member *Member, e *env, pkg string, receiver *structValue, receiverMutable bool) any {
	if obj == nil {
		panic(runtimeErrCode("E031", "null reference"))
	}
	switch x := obj.(type) {
	case *namespace:
		if v, ok := x.values[name]; ok {
			return v
		}
	case *enumTypeValue:
		if m, ok := x.members[name]; ok {
			for _, mbr := range x.decl.Members {
				if mbr.Name == name && len(mbr.PayloadTypes) > 0 {
					typeArgs := member.TypeArgs
					if len(typeArgs) == 0 {
						typeArgs = memberTypeArgs(member.Obj)
					}
					return &caseConstructor{enumName: x.canonicalName, caseName: name, payloadTypes: mbr.PayloadTypes, typeArgs: typeArgs, pkg: pkg}
				}
			}
			ev := *m
			ev.enumName = x.canonicalName
			ev.typeArgs = memberTypeArgs(member.Obj)
			return &ev
		}
	case *exceptionValue:
		if name == "message" {
			return x.message
		}
		if name == "trace" {
			return x.trace
		}
		if name == "code" {
			return x.code
		}
	case *structValue:
		decl := vm.in.structs[vm.in.typeKeyOf(x.typeName)]
		ownerPkg, _ := splitTypeName(x.typeName)
		if v, ok := x.fields[name]; ok {
			private := false
			if decl != nil {
				for _, field := range decl.Fields {
					if field.Name == name {
						private = !field.Public
						break
					}
				}
			}
			if decl != nil && ownerPkg != "" && ownerPkg != pkg && private {
				panic(runtimeErrCode("E070", "field '%s' is private", name))
			}
			return copyValue(v)
		}
		for _, m := range vm.in.structs[vm.in.typeKeyOf(x.typeName)].Methods {
			if m.Name == name {
				fn := vm.funcs[m]
				return &bcCallable{function: fn, env: nil, pkg: pkg, receiver: x, receiverMutable: vm.in.targetIsMutable(member.Obj, e, receiver, receiverMutable)}
			}
		}
	}
	if m := builtinMethod(obj, name, vm.in); m != nil {
		return m
	}
	panic(runtimeErr("type %s has no member %s", typeNameOf(obj), name))
}

func (vm *bcVM) runFor(s *ForStmt, body *bcCode, e *env, pkg string, receiver *structValue, receiverMutable bool) {
	source := vm.evalExpr(s.Iterable, e, pkg, receiver, receiverMutable)
	if len(s.Names) == 2 {
		m, ok := source.(*solvikMap)
		if !ok {
			panic(runtimeErr("two-binding for loop requires a map"))
		}
		for _, entry := range m.entriesInOrder() {
			loop := newEnv(e)
			loop.declare(s.Names[0], copyValue(entry.key), anyT, false)
			loop.declare(s.Names[1], copyValue(entry.value), anyT, false)
			broke := false
			func() {
				defer func() {
					if r := recover(); r != nil {
						if _, ok := r.(*continueSignal); ok {
							return
						}
						if _, ok := r.(*breakSignal); ok {
							broke = true
							return
						}
						panic(r)
					}
				}()
				vm.run(body, loop, pkg, receiver, receiverMutable)
			}()
			if broke {
				break
			}
		}
		return
	}
	seq := vm.in.iterableValues(source, pkg)
	for _, item := range seq {
		broke := false
		loop := newEnv(e)
		loop.declare(s.Names[0], copyValue(item), anyT, false)
		func() {
			defer func() {
				if r := recover(); r != nil {
					if _, ok := r.(*continueSignal); ok {
						return
					}
					if _, ok := r.(*breakSignal); ok {
						broke = true
						return
					}
					panic(r)
				}
			}()
			vm.run(body, loop, pkg, receiver, receiverMutable)
		}()
		if broke {
			break
		}
	}
}

func (vm *bcVM) runSwitch(s *SwitchStmt, e *env, pkg string, receiver *structValue, receiverMutable bool) {
	value := vm.evalExpr(s.Value, e, pkg, receiver, receiverMutable)
	for _, c := range s.Cases {
		if c.Expr == nil {
			vm.run((bcCompiler{}).compileBlock(c.Body), newEnv(e), pkg, receiver, receiverMutable)
			return
		}
		if kind, enumExpr, caseName, elements, ok := enumPatternShape(c.Expr); ok {
			var et *enumTypeValue
			if kind == "qualified" {
				if obj := vm.evalExpr(enumExpr, e, pkg, receiver, receiverMutable); obj != nil {
					et, _ = obj.(*enumTypeValue)
				}
			} else if ev, ok := value.(*enumValue); ok {
				et = vm.in.enums[vm.in.typeKeyOf(ev.enumName)]
			}
			if et != nil {
				matched, bindings := vm.in.matchEnumPattern(value, et, caseName, elements, e, pkg, receiver, receiverMutable)
				if matched {
					local := newEnv(e)
					for n, b := range bindings {
						local.declare(n, b.value, b.typ, false)
					}
					vm.run((bcCompiler{}).compileBlock(c.Body), local, pkg, receiver, receiverMutable)
					return
				}
				continue
			}
		}
		candidate := vm.evalExpr(c.Expr, e, pkg, receiver, receiverMutable)
		if r, ok := candidate.(*regexValue); ok {
			if text, ok := value.(string); ok && regexCompiled(r).MatchString(text) {
				vm.run((bcCompiler{}).compileBlock(c.Body), newEnv(e), pkg, receiver, receiverMutable)
				return
			}
		} else if vm.in.equal(value, candidate) {
			vm.run((bcCompiler{}).compileBlock(c.Body), newEnv(e), pkg, receiver, receiverMutable)
			return
		}
	}
}

func (vm *bcVM) runTry(s *TryStmt, tryCode, catchCode *bcCode, e *env, pkg string, receiver *structValue, receiverMutable bool) {
	var pending any
	func() {
		defer func() {
			if r := recover(); r != nil {
				pending = r
			}
		}()
		vm.run(tryCode, e, pkg, receiver, receiverMutable)
	}()
	if pending != nil {
		if signal, ok := pending.(*RuntimeSignal); ok {
			pending = &exceptionValue{message: signal.Message, code: signal.Code}
		}
		if ex, ok := pending.(*exceptionValue); ok && s.CatchBlock != nil {
			ce := newEnv(e)
			name := s.CatchName
			if name == "" {
				name = "e"
			}
			ce.declare(name, ex, exceptionT, false)
			pending = nil
			func() {
				defer func() {
					if r := recover(); r != nil {
						pending = r
					}
				}()
				vm.run(catchCode, ce, pkg, receiver, receiverMutable)
			}()
		}
	}
	if s.FinallyBlk != nil {
		func() {
			defer func() {
				if r := recover(); r != nil {
					pending = r
				}
			}()
			vm.run((bcCompiler{}).compileBlock(s.FinallyBlk), e, pkg, receiver, receiverMutable)
		}()
	}
	if pending != nil {
		panic(pending)
	}
}

func (vm *bcVM) call(callee any, args []any, receiverMutable bool, typeArgs []TypeRef, hints []any) (any, error) {
	switch x := callee.(type) {
	case *bcCallable:
		return vm.callBytecode(x, args, typeArgs, hints)
	case *nativeFn:
		return x.fn(args...), nil
	default:
		return vm.in.callValue(callee, args, receiverMutable, typeArgs, hints)
	}
}

func (vm *bcVM) callBytecode(c *bcCallable, args []any, typeArgs []TypeRef, hints []any) (result any, err error) {
	d := c.function.decl
	fixed := len(d.Params)
	variadic := false
	if isVariadicDecl(d) {
		fixed--
		variadic = true
	}
	if len(args) < fixed || (!variadic && len(args) != len(d.Params)) {
		if d.Name == "<closure>" {
			return nil, runtimeErrCode("E068", "closure expects %d argument(s), found %d", len(d.Params), len(args))
		}
		return nil, runtimeErr("%s argument count mismatch", d.Name)
	}
	if len(typeArgs) > 0 && len(typeArgs) != len(d.TypeParams) {
		return nil, runtimeErrCode("E067", "%s requires %d type argument(s), found %d", d.Name, len(d.TypeParams), len(typeArgs))
	}
	typeBindings := map[string]TypeRef{}
	if c.receiver != nil && d.OwnerStruct != "" {
		owner := vm.in.structByKey(c.pkg, d.OwnerStruct)
		if owner != nil {
			for n, param := range owner.TypeParams {
				if n < len(c.receiver.typeArgs) {
					typeBindings[param.Name] = c.receiver.typeArgs[n]
				}
			}
		}
	}
	for n, param := range d.TypeParams {
		if n < len(typeArgs) {
			typeBindings[param.Name] = vm.in.resolveRuntimeType(typeArgs[n])
		}
	}
	variables := typeParamNames(d.TypeParams)
	for n, param := range d.Params {
		if param.Variadic {
			continue
		}
		actual := valueTypeRef(args[n])
		if actual.Name == "null" && n < len(hints) {
			if hint, ok := hints[n].(TypeRef); ok {
				actual = hint
			}
		}
		bindTypePattern(param.Type, actual, variables, typeBindings)
	}
	for _, param := range d.TypeParams {
		actual := bindingOrUnknown(typeBindings, param.Name)
		if actual.equal(unknownT) {
			continue
		}
		for _, constraint := range param.Constraints {
			need := substituteType(constraint, typeBindings)
			unbound := map[string]bool{}
			for _, other := range d.TypeParams {
				if bound, exists := typeBindings[other.Name]; !exists || bound.equal(unknownT) {
					unbound[other.Name] = true
				}
			}
			okay, solved := vm.in.traitSatisfaction(actual, need, unbound)
			if !okay {
				return nil, runtimeErrCode("E067", "type %s does not satisfy generic constraint %s", actual, need)
			}
			for name, bound := range solved {
				typeBindings[name] = bound
			}
		}
	}
	for _, param := range d.TypeParams {
		if bindingOrUnknown(typeBindings, param.Name).equal(unknownT) {
			return nil, runtimeErrCode("E067", "cannot infer type parameter %s for function %s; pass a non-null value, use explicit type arguments, or annotate the value's type", param.Name, d.Name)
		}
	}
	vm.in.typeBindings = append(vm.in.typeBindings, typeBindings)
	vm.in.expectedTypes = append(vm.in.expectedTypes, substituteType(d.ReturnType, typeBindings))
	defer func() {
		vm.in.typeBindings = vm.in.typeBindings[:len(vm.in.typeBindings)-1]
		vm.in.expectedTypes = vm.in.expectedTypes[:len(vm.in.expectedTypes)-1]
	}()
	e := newEnv(c.env)
	if e == nil {
		e = newEnv(nil)
	}
	for i, p := range d.Params {
		if p.Variadic {
			rest := []any{}
			for _, v := range args[i:] {
				rest = append(rest, vm.in.coerceForType(v, p.Type))
			}
			e.declare(p.Name, rest, typeRef("list", substituteType(p.Type, typeBindings)), false)
		} else {
			concrete := substituteType(p.Type, typeBindings)
			e.declare(p.Name, vm.in.coerceForType(args[i], concrete), concrete, false)
		}
	}
	defer func() {
		if r := recover(); r != nil {
			if x, ok := r.(*returnSignal); ok {
				if d.ReturnType.Name == "void" {
					result = nil
				} else {
					result = vm.in.coerceForType(x.value, substituteType(d.ReturnType, typeBindings))
				}
				return
			}
			panic(r)
		}
	}()
	vm.run(c.function.code, e, c.pkg, c.receiver, c.receiverMutable)
	if d.ReturnType.Name != "void" {
		panic(runtimeErrCode("E068", "function %s reached end without returning %s", d.Name, substituteType(d.ReturnType, typeBindings)))
	}
	return nil, nil
}

var _ = regexp.MustCompile
