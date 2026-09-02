package reference

import (
	"strings"
)

// theInterpreter is the active interpreter (mirrors the Python INTERP global).
var theInterpreter *Interpreter

// Interpreter is the tree-walking semantic reference.
type Interpreter struct {
	packages      map[string]*namespace
	structs       map[[2]string]*StructDecl
	traits        map[[2]string]*TraitDecl
	enums         map[[2]string]*enumTypeValue
	entryPackage  string
	builtins      map[string]any
	coreTraits    map[string]*TraitDecl
	typeBindings  []map[string]TypeRef
	expectedTypes []TypeRef
	stdout        *strings.Builder
}

type pkgKey = [2]string

func NewInterpreter() *Interpreter {
	theInterpreter = &Interpreter{
		packages:   map[string]*namespace{},
		structs:    map[[2]string]*StructDecl{},
		traits:     map[[2]string]*TraitDecl{},
		enums:      map[[2]string]*enumTypeValue{},
		coreTraits: coreTraitDecls(),
		stdout:     &strings.Builder{},
	}
	theInterpreter.builtins = buildBuiltins()
	return theInterpreter
}

func (in *Interpreter) structByKey(pkg, name string) *StructDecl {
	return in.structs[pkgKey{pkg, name}]
}

func (in *Interpreter) print(s string) { in.stdout.WriteString(s) }

func (in *Interpreter) addProgram(p *Program) {
	canonicalizeProgram(p)
	validateProgram(in, p)
	ns, ok := in.packages[p.Package]
	if !ok {
		ns = &namespace{name: p.Package, values: map[string]any{}}
		in.packages[p.Package] = ns
	}
	for _, d := range p.Declarations {
		switch decl := d.(type) {
		case *StructDecl:
			in.structs[pkgKey{p.Package, decl.Name}] = decl
			ns.values[decl.Name] = &structTypeValue{decl: decl}
		case *TraitDecl:
			in.traits[pkgKey{p.Package, decl.Name}] = decl
		case *EnumDecl:
			nextValue := int64(0)
			members := map[string]*enumValue{}
			for _, m := range decl.Members {
				if m.Value != nil {
					nextValue = *m.Value
				}
				members[m.Name] = &enumValue{enumName: decl.Name, memberName: m.Name, value: nextValue}
				nextValue++
			}
			et := &enumTypeValue{decl: decl, members: members, canonicalName: p.Package + "." + decl.Name}
			in.enums[pkgKey{p.Package, decl.Name}] = et
			ns.values[decl.Name] = et
		}
	}
	for _, d := range p.Declarations {
		switch decl := d.(type) {
		case *FunctionDecl:
			ns.values[decl.Name] = &userFunction{decl: decl, pkg: p.Package}
		case *StructDecl:
			for _, m := range decl.Methods {
				m.OwnerStruct = decl.Name
			}
		}
	}
}

func (in *Interpreter) run(entryPackage string) (int, error) {
	in.entryPackage = entryPackage
	ns := in.packages[entryPackage]
	if ns == nil {
		return 1, newSolvikError("package %q has no main function", entryPackage)
	}
	mainFn, ok := ns.values["main"].(*userFunction)
	if !ok {
		return 1, newSolvikError("package %q has no main function", entryPackage)
	}
	result, err := in.callValue(mainFn, nil, false, nil, nil)
	if err != nil {
		return 2, err
	}
	if result == nil {
		return 0, nil
	}
	switch v := result.(type) {
	case int64:
		return int(v), nil
	case float64:
		return int(v), nil
	}
	return 0, nil
}

func (in *Interpreter) resolveRuntimeType(t TypeRef) TypeRef {
	resolved := t
	for i := len(in.typeBindings) - 1; i >= 0; i-- {
		resolved = substituteType(resolved, in.typeBindings[i])
	}
	return resolved
}

func (in *Interpreter) methodSigForType(t TypeRef, name string) *methodSig {
	if built := builtinMethodSignature(t, name); built != nil {
		return built
	}
	key := in.typeKeyOf(t.Name)
	s := in.structs[key]
	if s == nil {
		return nil
	}
	bindings := map[string]TypeRef{}
	for i, p := range s.TypeParams {
		if i < len(t.Args) {
			bindings[p.Name] = t.Args[i]
		}
	}
	var m *FunctionDecl
	for _, mm := range s.Methods {
		if mm.Public && mm.Name == name {
			m = mm
			break
		}
	}
	if m == nil {
		return nil
	}
	params := make([]TypeRef, len(m.Params))
	for i, p := range m.Params {
		params[i] = substituteType(p.Type, bindings)
	}
	return &methodSig{params: params, returnType: substituteType(m.ReturnType, bindings), mutating: m.Mutating}
}

func (in *Interpreter) ambientPackage() string { return in.entryPackage }

func (in *Interpreter) traitSatisfaction(actual, expected TypeRef, variables map[string]bool) (bool, map[string]TypeRef) {
	key := typeKey(expected.Name, in.ambientPackage())
	trait := in.traits[key]
	if trait == nil {
		trait = in.coreTraits[expected.Name]
	}
	if trait == nil {
		return false, nil
	}
	resolve := func(t TypeRef, n string) *methodSig { return in.methodSigForType(t, n) }
	return traitSatisfaction(actual, expected, trait, resolve, variables)
}

func (in *Interpreter) typeSatisfiesTrait(actual, expected TypeRef) bool {
	ok, _ := in.traitSatisfaction(actual, expected, nil)
	return ok
}

func (in *Interpreter) genericArityOf(t TypeRef) *int {
	switch t.Name {
	case "list", "stack":
		n := 1
		return &n
	case "map":
		n := 2
		return &n
	}
	key := typeKey(t.Name, in.ambientPackage())
	if d := in.structs[key]; d != nil {
		n := len(d.TypeParams)
		return &n
	}
	if e := in.enums[key]; e != nil {
		n := len(e.decl.TypeParams)
		return &n
	}
	return nil
}

func (in *Interpreter) valueMatchesType(v any, t TypeRef) bool {
	t = in.resolveRuntimeType(t)
	if t.Name == "any" {
		return true
	}
	base := t.nonnull()
	if len(base.Args) > 0 {
		if arity := in.genericArityOf(base); arity != nil && len(base.Args) != *arity {
			return false
		}
	}
	if v == nil {
		return t.Nullable
	}
	switch base.Name {
	case "bool":
		_, ok := v.(bool)
		return ok
	case "byte":
		_, ok := v.(*byteValue)
		return ok
	case "int":
		_, ok1 := v.(int64)
		_, ok2 := v.(*byteValue)
		_, isBool := v.(bool)
		return (ok1 || ok2) && !isBool
	case "float":
		switch v.(type) {
		case int64, float64, *byteValue:
			_, isBool := v.(bool)
			return !isBool
		}
		return false
	case "char":
		_, ok := v.(charValue)
		return ok
	case "string":
		s, ok := v.(string)
		_, isChar := v.(charValue)
		return ok && s != "" || (ok && s == "" && !isChar && false) || (ok && !isChar)
	case "exception":
		_, ok := v.(*exceptionValue)
		return ok
	case "func":
		return sameFuncType(functionValueType(v), base)
	case "list":
		xs, ok := v.([]any)
		if !ok {
			return false
		}
		if len(base.Args) == 0 {
			return true
		}
		for _, x := range xs {
			if !in.valueMatchesType(x, base.Args[0]) {
				return false
			}
		}
		return true
	case "stack":
		st, ok := v.(*stackValue)
		if !ok {
			return false
		}
		if len(base.Args) == 0 {
			return true
		}
		for _, x := range st.items {
			if !in.valueMatchesType(x, base.Args[0]) {
				return false
			}
		}
		return true
	case "map":
		m, ok := v.(*solvikMap)
		if !ok {
			return false
		}
		if len(base.Args) != 2 {
			return true
		}
		for _, entry := range m.entriesInOrder() {
			if !in.valueMatchesType(entry.key, base.Args[0]) || !in.valueMatchesType(entry.value, base.Args[1]) {
				return false
			}
		}
		return true
	}
	key := typeKey(base.Name, in.ambientPackage())
	if _, ok := in.structs[key]; ok {
		sv, ok := v.(*structValue)
		if !ok || sv.typeName != base.Name {
			return false
		}
		return len(base.Args) == 0 || len(sv.typeArgs) == 0 || typeArgsEqual(base.Args, sv.typeArgs)
	}
	if _, ok := in.enums[key]; ok {
		ev, ok := v.(*enumValue)
		return ok && ev.enumName == base.Name
	}
	if _, ok := in.traits[key]; ok || in.coreTraits[base.Name] != nil {
		return in.typeSatisfiesTrait(valueTypeRef(v), base)
	}
	return false
}

func typeArgsEqual(a, b []TypeRef) bool {
	if len(a) != len(b) {
		return false
	}
	for i := range a {
		if !a[i].equal(b[i]) {
			return false
		}
	}
	return true
}

func (in *Interpreter) coerceForType(v any, t TypeRef) any {
	t = in.resolveRuntimeType(t)
	base := t.nonnull()
	if len(base.Args) > 0 {
		if arity := in.genericArityOf(base); arity != nil && len(base.Args) != *arity {
			panic(runtimeErrCode("E067", "type '%s' requires %d type argument(s), found %d", base.Name, *arity, len(base.Args)))
		}
	}
	if v == nil {
		if t.Nullable {
			return nil
		}
		panic(runtimeErrCode("E066", "type mismatch: null is not assignable to %s", t))
	}
	// Numeric widening: byte -> int -> float.
	if base.Name == "int" {
		if b, ok := v.(*byteValue); ok {
			return b.v
		}
	}
	if base.Name == "float" {
		switch x := v.(type) {
		case *byteValue:
			return float64(x.v)
		case int64:
			if _, isBool := v.(bool); !isBool {
				return float64(x)
			}
		}
	}
	if base.Name == "exception" {
		if s, ok := v.(string); ok {
			return &exceptionValue{message: s}
		}
	}
	if base.Name == "any" {
		return copyValue(v)
	}
	if !in.valueMatchesType(v, t) {
		panic(runtimeErrCode("E066", "type mismatch: %s is not assignable to %s", typeNameOf(v), t))
	}
	return copyValue(v)
}

// ---- statements ----

func (in *Interpreter) execBlock(b *Block, e *env, pkg string, newScope bool, receiver *structValue, receiverMutable bool) {
	local := e
	if newScope {
		local = newEnv(e)
	}
	for _, s := range b.Statements {
		in.execStmt(s, local, pkg, receiver, receiverMutable)
	}
}

func (in *Interpreter) execStmt(s any, e *env, pkg string, receiver *structValue, receiverMutable bool) {
	switch x := s.(type) {
	case *Block:
		in.execBlock(x, e, pkg, true, receiver, receiverMutable)
	case *VarDecl:
		in.seedExpectedType(x.Value, x.Type)
		v := in.coerceForType(in.evalExpr(x.Value, e, pkg, receiver, receiverMutable), x.Type)
		e.declare(x.Name, v, x.Type, x.Mutable)
	case *ExprStmt:
		in.evalExpr(x.Expr, e, pkg, receiver, receiverMutable)
	case *IfStmt:
		if truth(in.evalExpr(x.Condition, e, pkg, receiver, receiverMutable)) {
			in.execBlock(x.ThenBlock, e, pkg, true, receiver, receiverMutable)
		} else if b, ok := x.ElseBranch.(*Block); ok {
			in.execBlock(b, e, pkg, true, receiver, receiverMutable)
		} else if ei, ok := x.ElseBranch.(*IfStmt); ok {
			in.execStmt(ei, e, pkg, receiver, receiverMutable)
		}
	case *WhileStmt:
		for truth(in.evalExpr(x.Condition, e, pkg, receiver, receiverMutable)) {
			breakLoop := false
			func() {
				defer func() {
					if r := recover(); r != nil {
						if _, isCont := r.(*continueSignal); isCont {
							return
						}
						if _, isBrk := r.(*breakSignal); isBrk {
							breakLoop = true
							return
						}
						panic(r)
					}
				}()
				in.execBlock(x.Body, e, pkg, true, receiver, receiverMutable)
			}()
			if breakLoop {
				break
			}
		}
	case *ForStmt:
		source := in.evalExpr(x.Iterable, e, pkg, receiver, receiverMutable)
		if source == nil {
			panic(runtimeErrCode("E031", "null reference"))
		}
		var seq []any
		if len(x.Names) == 2 {
			m, ok := source.(*solvikMap)
			if !ok {
				panic(runtimeErr("two-binding for loop requires a map"))
			}
			for _, entry := range m.entriesInOrder() {
				seq = append(seq, []any{entry.key, entry.value})
			}
			for _, item := range seq {
				pair := item.([]any)
				loopEnv := newEnv(e)
				loopEnv.declare(x.Names[0], copyValue(pair[0]), anyT, false)
				loopEnv.declare(x.Names[1], copyValue(pair[1]), anyT, false)
				if !in.runLoopBody(x.Body, loopEnv, pkg, receiver, receiverMutable) {
					break
				}
			}
			return
		}
		seq = in.iterableValues(source, pkg)
		for _, item := range seq {
			loopEnv := newEnv(e)
			loopEnv.declare(x.Names[0], copyValue(item), anyT, false)
			if !in.runLoopBody(x.Body, loopEnv, pkg, receiver, receiverMutable) {
				break
			}
		}
	case *SwitchStmt:
		value := in.evalExpr(x.Value, e, pkg, receiver, receiverMutable)
		for _, c := range x.Cases {
			matched := c.Expr == nil
			if c.Expr != nil {
				if kind, enumExpr, caseName, elements, ok := enumPatternShape(c.Expr); ok {
					var et *enumTypeValue
					if kind == "qualified" {
						if obj, err := in.tryEval(enumExpr, e, pkg, receiver, receiverMutable); err == nil {
							if etv, isET := obj.(*enumTypeValue); isET {
								if _, has := etv.members[caseName]; has {
									et = etv
								}
							}
						}
					} else if ev, isEV := value.(*enumValue); isEV {
						k := typeKey(ev.enumName, pkg)
						if etv := in.enums[k]; etv != nil {
							if _, has := etv.members[caseName]; has {
								et = etv
							}
						}
					}
					if et != nil {
						matched, bindings := in.matchEnumPattern(value, et, caseName, elements, e, pkg, receiver, receiverMutable)
						if matched {
							local := newEnv(e)
							for name, b := range bindings {
								local.declare(name, b.value, b.typ, false)
							}
							in.execBlock(c.Body, local, pkg, false, receiver, receiverMutable)
							return
						}
						continue
					}
				}
				cv := in.evalExpr(c.Expr, e, pkg, receiver, receiverMutable)
				if rv, isRegex := cv.(*regexValue); isRegex {
					if sv, isStr := value.(string); isStr {
						matched = regexCompiled(rv).MatchString(sv)
					}
				} else {
					matched = in.equal(value, cv)
				}
			}
			if matched {
				in.execBlock(c.Body, e, pkg, true, receiver, receiverMutable)
				return
			}
		}
	case *TryStmt:
		in.execTry(x, e, pkg, receiver, receiverMutable)
	case *ThrowStmt:
		v := in.evalExpr(x.Value, e, pkg, receiver, receiverMutable)
		if s, ok := v.(string); ok {
			v = &exceptionValue{message: s}
		}
		ev, ok := v.(*exceptionValue)
		if !ok {
			panic(runtimeErr("throw requires string or exception"))
		}
		panic(ev)
	case *ReturnStmt:
		if x.Value != nil && len(in.expectedTypes) > 0 {
			in.seedExpectedType(x.Value, in.expectedTypes[len(in.expectedTypes)-1])
		}
		var v any
		if x.Value != nil {
			v = copyValue(in.evalExpr(x.Value, e, pkg, receiver, receiverMutable))
		}
		panic(&returnSignal{value: v})
	case *BreakStmt:
		panic(&breakSignal{})
	case *ContinueStmt:
		panic(&continueSignal{})
	}
}

func (in *Interpreter) runLoopBody(b *Block, e *env, pkg string, receiver *structValue, receiverMutable bool) (keepGoing bool) {
	defer func() {
		if r := recover(); r != nil {
			if _, isCont := r.(*continueSignal); isCont {
				keepGoing = true
				return
			}
			if _, isBrk := r.(*breakSignal); isBrk {
				keepGoing = false
				return
			}
			panic(r)
		}
	}()
	in.execBlock(b, e, pkg, false, receiver, receiverMutable)
	return true
}

func (in *Interpreter) execTry(x *TryStmt, e *env, pkg string, receiver *structValue, receiverMutable bool) {
	var pending any
	func() {
		defer func() {
			if r := recover(); r != nil {
				switch sig := r.(type) {
				case *returnSignal, *breakSignal, *continueSignal:
					pending = r
				case *exceptionValue:
					if x.CatchBlock != nil {
						ce := newEnv(e)
						name := x.CatchName
						if name == "" {
							name = "e"
						}
						ct := exceptionT
						if x.CatchType != nil {
							ct = *x.CatchType
						}
						ce.declare(name, sig, ct, false)
						func() {
							defer func() {
								if r2 := recover(); r2 != nil {
									pending = r2
								}
							}()
							in.execBlock(x.CatchBlock, ce, pkg, false, receiver, receiverMutable)
						}()
					} else {
						pending = r
					}
				case *RuntimeSignal:
					ev := &exceptionValue{message: sig.Message, code: sig.Code}
					if x.CatchBlock != nil {
						ce := newEnv(e)
						name := x.CatchName
						if name == "" {
							name = "e"
						}
						ct := exceptionT
						if x.CatchType != nil {
							ct = *x.CatchType
						}
						ce.declare(name, ev, ct, false)
						func() {
							defer func() {
								if r2 := recover(); r2 != nil {
									pending = r2
								}
							}()
							in.execBlock(x.CatchBlock, ce, pkg, false, receiver, receiverMutable)
						}()
					} else {
						pending = ev
					}
				default:
					panic(r)
				}
			}
		}()
		in.execBlock(x.TryBlock, e, pkg, true, receiver, receiverMutable)
	}()
	if x.FinallyBlk != nil {
		func() {
			defer func() {
				if r := recover(); r != nil {
					pending = r
				}
			}()
			in.execBlock(x.FinallyBlk, e, pkg, true, receiver, receiverMutable)
		}()
	}
	if pending != nil {
		panic(pending)
	}
}

func (in *Interpreter) iterableValues(source any, pkg string) []any {
	if native := builtinMethod(source, "iterator", in); native != nil {
		values := native.fn()
		if vs, ok := values.([]any); ok {
			return vs
		}
	}
	if sv, ok := source.(*structValue); ok {
		key := in.typeKeyOf(sv.typeName)
		decl := in.structs[key]
		if decl != nil {
			for _, m := range decl.Methods {
				if m.Public && m.Name == "iterator" {
					bm := &boundMethod{receiver: sv, fn: &userFunction{decl: m, pkg: pkg}, receiverMutable: false}
					result, err := in.callValue(bm, nil, false, nil, nil)
					if err != nil {
						panic(err)
					}
					if vs, ok := result.([]any); ok {
						return vs
					}
					panic(runtimeErr("iterator() on %s must return list<T>", sv.typeName))
				}
			}
		}
	}
	panic(runtimeErr("value of type %s is not iterable", typeNameOf(source)))
}

func (in *Interpreter) typeKeyOf(name string) pkgKey {
	return typeKey(name, in.ambientPackage())
}
