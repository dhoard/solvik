package reference

// Enum construction, pattern matching, and the callable dispatch.

func (in *Interpreter) constructEnumCase(ctor *caseConstructor, args []any, expected *TypeRef, hints []any) *enumValue {
	k := typeKey(ctor.enumName, ctor.pkg)
	et := in.enums[k]
	if et == nil {
		panic(runtimeErrCode("E069", "unknown enum %s", ctor.enumName))
	}
	decl := et.decl
	var member *EnumMember
	for i := range decl.Members {
		if decl.Members[i].Name == ctor.caseName {
			member = &decl.Members[i]
			break
		}
	}
	if member == nil {
		panic(runtimeErrCode("E069", "enum %s has no case %s", decl.Name, ctor.caseName))
	}
	if len(args) != len(ctor.payloadTypes) {
		panic(runtimeErrCode("E068", "case %s expects %d payload value(s), found %d", ctor.caseName, len(ctor.payloadTypes), len(args)))
	}
	variables := typeParamNames(decl.TypeParams)
	typeBindings := map[string]TypeRef{}
	for i, p := range decl.TypeParams {
		if i < len(ctor.typeArgs) {
			typeBindings[p.Name] = in.resolveRuntimeType(ctor.typeArgs[i])
		}
	}
	if expected != nil {
		_, elocal := splitTypeName(expected.Name)
		if elocal == decl.Name && len(expected.Args) == len(decl.TypeParams) {
			for i, p := range decl.TypeParams {
				if i < len(expected.Args) {
					if _, seen := typeBindings[p.Name]; !seen {
						typeBindings[p.Name] = in.resolveRuntimeType(expected.Args[i])
					}
				}
			}
		}
	}
	for i, ptype := range member.PayloadTypes {
		actual := valueTypeRef(args[i])
		if actual.Name == "null" && i < len(hints) {
			if h, ok := hints[i].(TypeRef); ok {
				actual = h
			}
		}
		bindTypePattern(ptype, actual, variables, typeBindings)
	}
	for _, p := range decl.TypeParams {
		actual, ok := typeBindings[p.Name]
		if !ok || actual.equal(unknownT) {
			panic(runtimeErrCode("E067", "cannot infer type parameter %s for enum %s; use explicit type arguments like %s<...> or annotate the value's type", p.Name, decl.Name, decl.Name))
		}
		for _, c := range p.Constraints {
			need := substituteType(c, typeBindings)
			if !in.typeSatisfiesTrait(actual, need) {
				panic(runtimeErrCode("E067", "type %s does not satisfy generic constraint %s", actual, need))
			}
		}
	}
	payload := make([]any, len(member.PayloadTypes))
	for i, ptype := range member.PayloadTypes {
		payload[i] = in.coerceForType(args[i], substituteType(ptype, typeBindings))
	}
	typeArgs := make([]TypeRef, len(decl.TypeParams))
	for i, p := range decl.TypeParams {
		typeArgs[i] = bindingOrUnknown(typeBindings, p.Name)
	}
	return &enumValue{enumName: et.canonicalName, memberName: member.Name, value: et.members[member.Name].value, payload: payload, typeArgs: typeArgs}
}

func (in *Interpreter) resolvePatternEnum(call *Call, e *env, pkg string, receiver *structValue, receiverMutable bool, enclosing *enumTypeValue) (*enumTypeValue, string, []any, bool) {
	if m, ok := call.Callee.(*Member); ok {
		if _, dok := dottedExpressionName(m.Obj); dok {
			obj := in.evalExpr(m.Obj, e, pkg, receiver, receiverMutable)
			if etv, isET := obj.(*enumTypeValue); isET {
				if _, has := etv.members[m.Name]; has {
					elements := make([]any, 0, len(call.Args))
					for _, a := range call.Args {
						elements = append(elements, a.Expr)
					}
					return etv, m.Name, elements, true
				}
			}
		}
	}
	if n, ok := call.Callee.(*Name); ok {
		if _, has := enclosing.members[n.Name]; has {
			elements := make([]any, 0, len(call.Args))
			for _, a := range call.Args {
				elements = append(elements, a.Expr)
			}
			return enclosing, n.Name, elements, true
		}
	}
	return nil, "", nil, false
}

type patternBinding struct {
	value any
	typ   TypeRef
}

func (in *Interpreter) matchEnumPattern(value any, et *enumTypeValue, caseName string, elements []any, e *env, pkg string, receiver *structValue, receiverMutable bool) (bool, map[string]patternBinding) {
	ev, isEV := value.(*enumValue)
	if !isEV || ev.enumName != et.canonicalName || ev.memberName != caseName {
		return false, nil
	}
	var member *EnumMember
	for i := range et.decl.Members {
		if et.decl.Members[i].Name == caseName {
			member = &et.decl.Members[i]
			break
		}
	}
	if member == nil {
		panic(runtimeErrCode("E069", "enum %s has no case %s", et.decl.Name, caseName))
	}
	if len(elements) != len(member.PayloadTypes) {
		panic(runtimeErrCode("E068", "case %s expects %d payload value(s), found %d", caseName, len(member.PayloadTypes), len(elements)))
	}
	bindings := map[string]patternBinding{}
	typeBindings := map[string]TypeRef{}
	for i, p := range et.decl.TypeParams {
		if i < len(ev.typeArgs) {
			typeBindings[p.Name] = ev.typeArgs[i]
		}
	}
	for i, element := range elements {
		ptype := member.PayloadTypes[i]
		payload := ev.payload[i]
		concrete := substituteType(ptype, typeBindings)
		switch el := element.(type) {
		case *Name:
			if el.Name == "_" {
				continue
			}
			bindings[el.Name] = patternBinding{value: copyValue(payload), typ: concrete}
		case *Literal:
			if !in.equal(payload, el.Value) {
				return false, nil
			}
		case *Call:
			sub, subCase, subElements, ok := in.resolvePatternEnum(el, e, pkg, receiver, receiverMutable, et)
			if !ok {
				panic(runtimeErrCode("E069", "invalid nested pattern element"))
			}
			subOK, subBindings := in.matchEnumPattern(payload, sub, subCase, subElements, e, pkg, receiver, receiverMutable)
			if !subOK {
				return false, nil
			}
			for k, v := range subBindings {
				bindings[k] = v
			}
		default:
			panic(runtimeErrCode("E069", "invalid pattern element"))
		}
	}
	return true, bindings
}

// ---- callable dispatch ----

func copiedArgs(args []any) []any {
	out := make([]any, len(args))
	for i, a := range args {
		out[i] = copyValue(a)
	}
	return out
}

func (in *Interpreter) callValue(callee any, args []any, receiverMutable bool, typeArgs []TypeRef, hints []any) (result any, err error) {
	if callee == nil {
		return nil, runtimeErrCode("E031", "null reference")
	}
	switch c := callee.(type) {
	case *namespace:
		if c.callFn != nil {
			return c.callFn(copiedArgs(args)...), nil
		}
		return nil, runtimeErr("value of type %s is not callable", typeNameOf(callee))
	case *nativeFn:
		defer func() {
			if r := recover(); r != nil {
				if rs, isRS := r.(*RuntimeSignal); isRS {
					panic(rs)
				}
				if _, isParse := r.(*ParseError); isParse {
					panic(r)
				}
				if _, isDiag := r.(*DiagnosticError); isDiag {
					panic(r)
				}
				err = runtimeErr("%v", r)
				return
			}
		}()
		copied := make([]any, len(args))
		for i, a := range args {
			copied[i] = copyValue(a)
		}
		return c.fn(copied...), nil
	case *boundMethod:
		if c.fn.decl.Mutating && !c.receiverMutable {
			return nil, runtimeErr("mutating method %s requires mutable receiver", c.fn.decl.Name)
		}
		return in.callUser(c.fn, args, c.receiver, c.receiverMutable, typeArgs, hints)
	case *caseConstructor:
		panic(runtimeErr("case constructors must be constructed with arguments"))
	case *closureValue:
		return in.callClosure(c, args)
	case *userFunction:
		return in.callUser(c, args, nil, false, typeArgs, hints)
	case *bcCallable:
		vm := &bcVM{in: in, funcs: map[*FunctionDecl]*bcFunction{c.function.decl: c.function}}
		return vm.callBytecode(c, args, typeArgs, hints)
	}
	return nil, runtimeErr("value of type %s is not callable", typeNameOf(callee))
}

func (in *Interpreter) callClosure(c *closureValue, args []any) (result any, err error) {
	d := c.decl
	fixed := len(d.Params)
	variadic := false
	if isVariadicDecl(d) {
		fixed--
		variadic = true
	}
	if len(args) < fixed || (!variadic && len(args) != len(d.Params)) {
		return nil, runtimeErrCode("E068", "closure expects %d argument(s), found %d", len(d.Params), len(args))
	}
	e := newEnv(c.env)
	for i, p := range d.Params {
		if p.Variadic {
			rest := make([]any, 0)
			for _, x := range args[i:] {
				rest = append(rest, in.coerceForType(x, p.Type))
			}
			e.declare(p.Name, rest, typeRef("list", p.Type), false)
		} else {
			e.declare(p.Name, in.coerceForType(args[i], p.Type), p.Type, false)
		}
	}
	var ret any
	func() {
		defer func() {
			if r := recover(); r != nil {
				switch sig := r.(type) {
				case *breakSignal, *continueSignal:
					panic(runtimeErrCode("E068", "break/continue outside of loop"))
				case *returnSignal:
					if d.ReturnType.Name == "void" {
						ret = nil
						return
					}
					ret = in.coerceForType(sig.value, d.ReturnType)
					return
				default:
					panic(r)
				}
			}
		}()
		in.execBlock(d.Body, e, c.pkg, false, c.receiver, c.receiverMutable)
	}()
	if d.ReturnType.Name != "void" && ret == nil {
		panic(runtimeErrCode("E068", "closure reached end without returning %s", d.ReturnType))
	}
	return ret, nil
}

func (in *Interpreter) callUser(fn *userFunction, args []any, receiver *structValue, receiverMutable bool, typeArgs []TypeRef, hints []any) (result any, err error) {
	d := fn.decl
	fixed := len(d.Params)
	variadic := false
	if isVariadicDecl(d) {
		fixed--
		variadic = true
	}
	if len(args) < fixed || (!variadic && len(args) != len(d.Params)) {
		return nil, runtimeErr("%s argument count mismatch", d.Name)
	}
	if len(typeArgs) > 0 && len(typeArgs) != len(d.TypeParams) {
		return nil, runtimeErrCode("E067", "%s requires %d type argument(s), found %d", d.Name, len(d.TypeParams), len(typeArgs))
	}
	e := newEnv(nil)
	typeBindings := map[string]TypeRef{}
	if receiver != nil && d.OwnerStruct != "" {
		owner := in.structByKey(fn.pkg, d.OwnerStruct)
		if owner != nil {
			for i, p := range owner.TypeParams {
				if i < len(receiver.typeArgs) {
					typeBindings[p.Name] = receiver.typeArgs[i]
				}
			}
		}
	}
	for i, p := range d.TypeParams {
		if i < len(typeArgs) {
			typeBindings[p.Name] = in.resolveRuntimeType(typeArgs[i])
		}
	}
	variables := typeParamNames(d.TypeParams)
	for i, p := range d.Params {
		if p.Variadic {
			continue
		}
		actual := valueTypeRef(args[i])
		if actual.Name == "null" && hints != nil && i < len(hints) {
			if h, ok := hints[i].(TypeRef); ok {
				actual = h
			}
		}
		bindTypePattern(p.Type, actual, variables, typeBindings)
	}
	for _, p := range d.TypeParams {
		actual := bindingOrUnknown(typeBindings, p.Name)
		if actual.equal(unknownT) {
			// Constraint solving may still bind parameters that appear only
			// in constraints.
			continue
		}
		for _, c := range p.Constraints {
			need := substituteType(c, typeBindings)
			unbound := map[string]bool{}
			for _, tp := range d.TypeParams {
				if a, seen := typeBindings[tp.Name]; !seen || a.equal(unknownT) {
					unbound[tp.Name] = true
				}
			}
			okay, solved := in.traitSatisfaction(actual, need, unbound)
			if !okay {
				return nil, runtimeErrCode("E067", "type %s does not satisfy generic constraint %s", actual, need)
			}
			for k, v := range solved {
				typeBindings[k] = v
			}
		}
	}
	for _, p := range d.TypeParams {
		actual := bindingOrUnknown(typeBindings, p.Name)
		if actual.equal(unknownT) {
			return nil, runtimeErrCode("E067", "cannot infer type parameter %s for function %s; pass a non-null value, use explicit type arguments, or annotate the value's type", p.Name, d.Name)
		}
	}
	in.typeBindings = append(in.typeBindings, typeBindings)
	in.expectedTypes = append(in.expectedTypes, substituteType(d.ReturnType, typeBindings))
	defer func() {
		in.typeBindings = in.typeBindings[:len(in.typeBindings)-1]
		in.expectedTypes = in.expectedTypes[:len(in.expectedTypes)-1]
	}()
	for i, p := range d.Params {
		concrete := substituteType(p.Type, typeBindings)
		if p.Variadic {
			rest := make([]any, 0)
			for _, x := range args[i:] {
				rest = append(rest, in.coerceForType(x, concrete))
			}
			e.declare(p.Name, rest, typeRef("list", concrete), false)
		} else {
			e.declare(p.Name, in.coerceForType(args[i], concrete), concrete, false)
		}
	}
	var ret any
	returned := false
	func() {
		defer func() {
			if r := recover(); r != nil {
				if sig, isRet := r.(*returnSignal); isRet {
					resultType := substituteType(d.ReturnType, typeBindings)
					if resultType.Name == "void" {
						ret = nil
						returned = true
						return
					}
					ret = in.coerceForType(sig.value, resultType)
					returned = true
					return
				}
				panic(r)
			}
		}()
		in.execBlock(d.Body, e, fn.pkg, false, receiver, receiverMutable && d.Mutating)
	}()
	if !returned && d.ReturnType.Name != "void" {
		resultType := substituteType(d.ReturnType, typeBindings)
		panic(runtimeErr("function %s reached end without returning %s", d.Name, resultType))
	}
	return ret, nil
}
