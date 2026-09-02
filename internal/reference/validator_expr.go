package reference

// Expression type inference and assignability for the validator.

func (v *validator) methodSigForType(t TypeRef, name string) *methodSig {
	if built := builtinMethodSignature(t, name); built != nil {
		return built
	}
	s := v.structOf(t.Name)
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

func (v *validator) traitSatisfaction(actual, expected TypeRef, variables map[string]bool) (bool, map[string]TypeRef) {
	trait := v.traitOf(expected.Name)
	if trait == nil {
		return false, nil
	}
	return traitSatisfaction(actual, expected, trait, v.methodSigForType, variables)
}

func (v *validator) typeSatisfiesTrait(actual, expected TypeRef) bool {
	ok, _ := v.traitSatisfaction(actual, expected, nil)
	return ok
}

func (v *validator) assignable(actual, expected TypeRef) bool {
	if actual.equal(unknownT) || expected.equal(unknownT) || expected.Name == "any" {
		return true
	}
	if actual.Name == "null" {
		return expected.Nullable
	}
	if v.currentTypeParams[expected.Name] {
		return true
	}
	if v.currentTypeParams[actual.Name] {
		return actual.Name == expected.Name || expected.Name == "any"
	}
	if actual.Nullable && !expected.Nullable {
		return false
	}
	if expected.Nullable {
		expected = expected.nonnull()
	}
	if actual.Nullable {
		actual = actual.nonnull()
	}
	if actual.Name == expected.Name && actual.Name == "func" {
		return sameFuncType(actual, expected)
	}
	if v.sameTypeName(actual.Name, expected.Name) && (v.structOf(actual.Name) != nil || v.enumOf(actual.Name) != nil) {
		if len(actual.Args) == 0 || len(expected.Args) == 0 {
			return true
		}
		if len(actual.Args) != len(expected.Args) {
			return false
		}
		for i := range actual.Args {
			a, e := actual.Args[i], expected.Args[i]
			if !a.equal(e) && !a.equal(unknownT) && !e.equal(unknownT) {
				return false
			}
		}
		return true
	}
	if v.sameTypeName(actual.Name, expected.Name) {
		if (len(actual.Args) > 0) != (len(expected.Args) > 0) {
			return len(actual.Args) == 0 || len(expected.Args) == 0
		}
		if len(actual.Args) != len(expected.Args) {
			return false
		}
		for i := range actual.Args {
			if !v.assignable(actual.Args[i], expected.Args[i]) {
				return false
			}
		}
		return true
	}
	if v.typeSatisfiesTrait(actual, expected) {
		return true
	}
	_, aLocal := splitTypeName(actual.Name)
	_, eLocal := splitTypeName(expected.Name)
	return (aLocal == "byte" && eLocal == "int") || (aLocal == "byte" && eLocal == "float") || (aLocal == "int" && eLocal == "float")
}

func (v *validator) functionSignature(f *FunctionDecl) TypeRef {
	if len(f.TypeParams) > 0 || isVariadicDecl(f) {
		return unknownT
	}
	args := make([]TypeRef, 0, len(f.Params)+1)
	for _, p := range f.Params {
		args = append(args, p.Type)
	}
	args = append(args, f.ReturnType)
	return typeRefN("func", args)
}

func (v *validator) checkFuncValueCall(sig TypeRef, expr *Call, description string) TypeRef {
	params := sig.Args[:len(sig.Args)-1]
	result := sig.Args[len(sig.Args)-1]
	for _, a := range expr.Args {
		if a.Spread {
			return result
		}
	}
	if len(expr.Args) != len(params) {
		v.error("C101", expr.Pos, v.toLineEnd(expr.Pos), "%s expects %d argument(s), found %d", description, len(params), len(expr.Args))
	}
	for i, a := range expr.Args {
		if i >= len(params) {
			break
		}
		actual := v.infer(a.Expr)
		if !actual.equal(unknownT) && !v.assignable(actual, params[i]) {
			v.error("C101", exprPos(a.Expr, expr.Pos), literalSpan(a.Expr), "argument %d of %s: expected %s but got %s", i+1, description, params[i], actual)
		}
	}
	return result
}

func (v *validator) checkFuncAssignment(expected TypeRef, expr any) {
	if expected.Name != "func" {
		return
	}
	actual := v.infer(expr)
	if actual.Name == "func" && !actual.nonnull().equal(expected.nonnull()) {
		v.error("C100", exprPos(expr, SourcePos{}), literalSpan(expr), "function signature mismatch: expected %s but got %s", expected, actual)
	}
}

func (v *validator) infer(e any) TypeRef {
	switch x := e.(type) {
	case *Literal:
		if x.LiteralKind == "null" {
			return nullT
		}
		return typeRef(x.LiteralKind)
	case *Name:
		if b := v.lookup(x.Name); b != nil {
			return b.typ
		}
		if fd := v.receiverField(x.Name); fd != nil {
			return fd.Type
		}
		if x.Name == "self" && v.currentStruct != nil {
			return typeRef(v.currentStruct.Name)
		}
		if f := v.functions[x.Name]; f != nil {
			return v.functionSignature(f)
		}
		if v.enumOf(x.Name) != nil {
			return typeRefN(v.canonical(x.Name), x.TypeArgs)
		}
		return unknownT
	case *FuncExpr:
		return v.inferFuncExpr(x)
	case *ListExpr:
		if len(x.Items) == 0 {
			return typeRef("list", unknownT)
		}
		return typeRef("list", v.infer(x.Items[0]))
	case *MapExpr:
		if len(x.Items) == 0 {
			return typeRef("map", unknownT, unknownT)
		}
		return typeRef("map", v.infer(x.Items[0].Key), v.infer(x.Items[0].Value))
	case *StructExpr:
		return v.inferStructExpr(x, map[string]TypeRef{})
	case *Unary:
		operand := v.infer(x.Expr)
		if x.Op == "!" {
			return typeRef("bool")
		}
		return operand
	case *Binary:
		left := v.infer(x.Left)
		right := v.infer(x.Right)
		switch x.Op {
		case "??":
			if left.Name == "void" || left.Name == "module" || left.Name == "regex" {
				v.error("C028", exprPos(x.Left, x.Pos), literalSpan(x.Left), "left operand of ?? must be a value, got %s", left.Name)
			}
			if left.Name == "null" {
				return right
			}
			return left.nonnull()
		case "<", "<=", ">", ">=":
			if (left.Name == "char") != (right.Name == "char") && !left.equal(unknownT) && !right.equal(unknownT) {
				v.error("C017", exprPos(x.Left, x.Pos), v.toLineEnd(x.Pos), "cannot apply %s to %s and %s", x.Op, left, right)
			}
			return typeRef("bool")
		case "==", "!=", "&&", "||":
			return typeRef("bool")
		case "..":
			return typeRef("string")
		}
		if left.Name == "float" || right.Name == "float" {
			return typeRef("float")
		}
		if !left.equal(unknownT) {
			return left
		}
		return right
	case *Assign:
		v.checkAssignmentReceiver(x.Target, x.Pos)
		v.checkAssignmentTarget(x.Target, x.Pos)
		targetType := v.infer(x.Target)
		if targetType.Name == "func" {
			v.checkFuncAssignment(targetType, x.Value)
		}
		valueType := v.infer(x.Value)
		if !targetType.equal(unknownT) && !valueType.equal(unknownT) && valueType.Name != "any" && targetType.Name != "any" &&
			!(targetType.Name == "exception" && valueType.Name == "string") && !v.assignable(valueType, targetType) {
			v.error("C119", x.Pos, v.toLineEnd(x.Pos), "cannot assign %s to target of type %s", valueType, targetType)
		}
		if !targetType.equal(unknownT) {
			return targetType
		}
		return valueType
	case *Index:
		objType := v.infer(x.Obj)
		v.infer(x.Index)
		if objType.Name == "map" && len(objType.Args) == 2 {
			return objType.Args[1]
		}
		if (objType.Name == "list" || objType.Name == "stack") && len(objType.Args) > 0 {
			return objType.Args[0]
		}
		if objType.Name == "string" {
			return typeRef("char")
		}
		return unknownT
	case *Member:
		return v.inferMember(x)
	case *Call:
		return v.inferCall(x)
	}
	return unknownT
}

func (v *validator) inferFuncExpr(x *FuncExpr) TypeRef {
	seen := map[string]bool{}
	for i := range x.Params {
		if seen[x.Params[i].Name] {
			v.error("C092", *x.Params[i].Pos, 1, "duplicate parameter '%s' in anonymous function", x.Params[i].Name)
		}
		seen[x.Params[i].Name] = true
		v.checkAnnotationType(x.Params[i].Type, *x.Params[i].Pos)
	}
	if x.ReturnType.Name != "void" {
		v.checkAnnotationType(x.ReturnType, x.Pos)
	}
	oldFunction := v.currentFunction
	decl := &FunctionDecl{Name: "<closure>", Params: x.Params, ReturnType: x.ReturnType, Body: x.Body, Pos: x.Pos, Mutating: oldFunction != nil && oldFunction.Mutating}
	v.currentFunction = decl
	params := map[string]staticBinding{}
	for i := range x.Params {
		p := &x.Params[i]
		if p.Variadic {
			params[p.Name] = staticBinding{typ: typeRef("list", p.Type)}
		} else {
			params[p.Name] = staticBinding{typ: p.Type}
		}
	}
	v.scopes = append(v.scopes, params)
	v.checkBlock(x.Body, false)
	v.scopes = v.scopes[:len(v.scopes)-1]
	v.currentFunction = oldFunction
	if x.ReturnType.Name != "void" && !v.blockDefinitelyReturns(x.Body) {
		v.error("C111", x.Pos, v.toLineEnd(x.Pos), "closure declares return type %s but not every path returns a value", x.ReturnType)
	}
	args := make([]TypeRef, 0, len(x.Params)+1)
	for _, p := range x.Params {
		args = append(args, p.Type)
	}
	args = append(args, x.ReturnType)
	return typeRefN("func", args)
}

func (v *validator) checkAssignmentReceiver(target any, pos SourcePos) {
	if v.currentStruct == nil || v.currentFunction == nil || v.currentFunction.Mutating {
		return
	}
	if n, ok := target.(*Name); ok {
		if v.lookup(n.Name) == nil && v.receiverField(n.Name) != nil {
			v.error("C068", pos, v.toLineEnd(pos), "method '%s' is not mutating and cannot assign receiver field '%s'; declare it as 'mut func'", v.currentStruct.Name, n.Name)
		}
	}
	if m, ok := target.(*Member); ok {
		if n, ok := m.Obj.(*Name); ok && n.Name == "self" {
			v.error("C068", pos, v.toLineEnd(pos), "method '%s' is not mutating and cannot assign receiver field '%s'; declare it as 'mut func'", v.currentStruct.Name, m.Name)
		}
	}
}

func (v *validator) checkAssignmentTarget(target any, pos SourcePos) {
	switch t := target.(type) {
	case *Name:
		if t.Name == "self" {
			return
		}
		if b := v.lookup(t.Name); b != nil && !b.mutable {
			v.error("C116", pos, v.toLineEnd(pos), "cannot assign to immutable variable '%s'; declare it as 'mut'", t.Name)
		}
	case *Member:
		if n, ok := t.Obj.(*Name); ok && n.Name == "self" {
			if fd := v.receiverField(t.Name); fd != nil && !fd.Mutable {
				v.error("C117", pos, v.toLineEnd(pos), "cannot assign to immutable field '%s' of struct '%s'; declare the field as 'pub mut'", t.Name, v.currentStruct.Name)
			}
			return
		}
		objType := v.infer(t.Obj)
		s := v.structOf(objType.Name)
		if s != nil {
			for i := range s.Fields {
				if s.Fields[i].Name == t.Name && !s.Fields[i].Mutable {
					v.error("C117", pos, v.toLineEnd(pos), "cannot assign to immutable field '%s' of struct '%s'", t.Name, s.Name)
				}
			}
		}
	}
}

func (v *validator) inferMember(x *Member) TypeRef {
	objType := v.infer(x.Obj)
	s := v.structOf(objType.Name)
	if s != nil {
		crossPkg := false
		pkgOf, _ := splitTypeName(v.canonical(objType.Name))
		if pkgOf != "" && pkgOf != v.pkg {
			crossPkg = true
		}
		for i := range s.Fields {
			if s.Fields[i].Name == x.Name {
				if crossPkg && !s.Fields[i].Public {
					v.error("C120", x.Pos, v.toLineEnd(x.Pos), "field '%s' of '%s' is private; only pub fields are visible outside the package", x.Name, v.canonical(objType.Name))
				}
				bindings := map[string]TypeRef{}
				for j, p := range s.TypeParams {
					if j < len(objType.Args) {
						bindings[p.Name] = objType.Args[j]
					}
				}
				return substituteType(s.Fields[i].Type, bindings)
			}
		}
		var method *FunctionDecl
		for _, m := range s.Methods {
			if m.Name == x.Name {
				method = m
				break
			}
		}
		if method != nil && crossPkg && !method.Public {
			v.error("C120", x.Pos, v.toLineEnd(x.Pos), "method '%s' of '%s' is private; only pub methods are visible outside the package", x.Name, v.canonical(objType.Name))
		}
		if sig := v.methodSigForType(objType, x.Name); sig != nil {
			args := append(append([]TypeRef{}, sig.params...), sig.returnType)
			return typeRefN("func", args)
		}
	}
	enum := v.enumOf(objType.Name)
	if enum != nil {
		enumCanon := v.canonical(dottedOrName(x.Obj, objType.Name))
		member := enumMember(enum, x.Name)
		if member != nil {
			if len(member.PayloadTypes) > 0 {
				if len(objType.Args) > 0 {
					bindings := map[string]TypeRef{}
					for i, p := range enum.TypeParams {
						if i < len(objType.Args) {
							bindings[p.Name] = objType.Args[i]
						}
					}
					args := make([]TypeRef, 0, len(member.PayloadTypes)+1)
					for _, pt := range member.PayloadTypes {
						args = append(args, substituteType(pt, bindings))
					}
					args = append(args, typeRefN(enumCanon, objType.Args))
					return typeRefN("func", args)
				}
				return unknownT
			}
			return typeRefN(enumCanon, objType.Args)
		}
	}
	if built := builtinMethodSignature(objType, x.Name); built != nil {
		args := append(append([]TypeRef{}, built.params...), built.returnType)
		return typeRefN("func", args)
	}
	return unknownT
}

func dottedOrName(e any, fallback string) string {
	if d, ok := dottedExpressionName(e); ok {
		return d
	}
	return fallback
}

func (v *validator) inferCall(x *Call) TypeRef {
	argTypes := make([]TypeRef, 0, len(x.Args))
	for _, a := range x.Args {
		argTypes = append(argTypes, v.infer(a.Expr))
	}
	switch callee := x.Callee.(type) {
	case *Name:
		if b := v.lookup(callee.Name); b != nil {
			if b.typ.Name == "func" {
				return v.checkFuncValueCall(b.typ, x, "function value '"+callee.Name+"'")
			}
			if !b.typ.equal(unknownT) && b.typ.Name != "any" && !b.typ.Nullable {
				v.error("C102", x.Pos, v.toLineEnd(x.Pos), "cannot call '%s': value of type %s is not callable", callee.Name, b.typ)
			}
			if b.typ.Nullable && b.typ.Name != "any" {
				if b.typ.nonnull().Name == "func" {
					return b.typ.nonnull().Args[len(b.typ.nonnull().Args)-1]
				}
			}
			return unknownT
		}
		if f := v.functions[callee.Name]; f != nil {
			if len(f.TypeParams) > 0 || len(x.TypeArgs) > 0 {
				return v.inferGenericCall(f, argTypes, x.TypeArgs)
			}
			variadicParam := (*Param)(nil)
			if len(f.Params) > 0 && f.Params[len(f.Params)-1].Variadic {
				variadicParam = &f.Params[len(f.Params)-1]
			}
			fixedCount := len(f.Params)
			if variadicParam != nil {
				fixedCount--
			}
			for i, a := range x.Args {
				if a.Spread || i >= len(f.Params) {
					continue
				}
				if argTypes[i].equal(unknownT) || v.assignable(argTypes[i], f.Params[i].Type) {
					continue
				}
				v.error("C101", exprPos(a.Expr, x.Pos), literalSpan(a.Expr), "argument %d of '%s': expected %s but got %s", i+1, callee.Name, f.Params[i].Type, argTypes[i])
			}
			if variadicParam != nil {
				for i := fixedCount; i < len(x.Args); i++ {
					if x.Args[i].Spread {
						continue
					}
					if !argTypes[i].equal(unknownT) && !v.assignable(argTypes[i], variadicParam.Type) {
						v.error("C101", exprPos(x.Args[i].Expr, x.Pos), literalSpan(x.Args[i].Expr), "argument %d of '%s': expected %s but got %s", i+1, callee.Name, variadicParam.Type, argTypes[i])
					}
				}
			}
			return f.ReturnType
		}
		if v.currentStruct != nil {
			for _, m := range v.currentStruct.Methods {
				if m.Name == callee.Name {
					return m.ReturnType
				}
			}
		}
		switch callee.Name {
		case "print", "println":
			return voidT
		case "string", "typeOf":
			return typeRef("string")
		case "int":
			return typeRef("int")
		case "byte":
			return typeRef("byte")
		case "float":
			return typeRef("float")
		case "bool":
			return typeRef("bool")
		case "regex":
			return regexT
		case "isType":
			return typeRef("bool")
		case "stack":
			return typeRef("stack", unknownT)
		}
		return unknownT
	case *Member:
		if dotted, dok := dottedExpressionName(callee); dok {
			if qf := v.functions[dotted]; qf != nil && len(qf.TypeParams) == 0 {
				for i, a := range x.Args {
					if a.Spread || i >= len(qf.Params) {
						continue
					}
					if !argTypes[i].equal(unknownT) && !v.assignable(argTypes[i], qf.Params[i].Type) {
						v.error("C101", exprPos(a.Expr, x.Pos), literalSpan(a.Expr), "argument %d of '%s': expected %s but got %s", i+1, dotted, qf.Params[i].Type, argTypes[i])
					}
				}
				return qf.ReturnType
			}
		}
		objType := v.infer(callee.Obj)
		s := v.structOf(objType.Name)
		var method *FunctionDecl
		if s != nil {
			for _, m := range s.Methods {
				if m.Name == callee.Name {
					method = m
					break
				}
			}
			if method != nil && method.Mutating && !v.receiverIsMutable(callee.Obj) {
				receiverName := "receiver"
				if n, ok := callee.Obj.(*Name); ok {
					receiverName = n.Name
				}
				v.error("C068", x.Pos, v.toLineEnd(x.Pos), "cannot call mutating method '%s' on immutable struct variable '%s'; declare the variable as 'mut'", method.Name, receiverName)
			}
			if method != nil && len(method.TypeParams) > 0 {
				if len(x.TypeArgs) == len(method.TypeParams) {
					bindings := map[string]TypeRef{}
					for i, tp := range method.TypeParams {
						bindings[tp.Name] = x.TypeArgs[i]
					}
					return substituteType(method.ReturnType, bindings)
				}
				return method.ReturnType
			}
		}
		enum := v.enumOf(objType.Name)
		if enum != nil {
			return v.inferEnumConstruction(enum, objType, x)
		}
		calleeType := v.inferMember(callee)
		if calleeType.Name == "func" {
			return v.checkFuncValueCall(calleeType, x, "'"+callee.Name+"'")
		}
		return unknownT
	}
	calleeType := v.infer(x.Callee)
	if calleeType.Name == "func" {
		return v.checkFuncValueCall(calleeType, x, "function value")
	}
	return unknownT
}

func (v *validator) receiverIsMutable(e any) bool {
	if n, ok := e.(*Name); ok {
		if n.Name == "self" {
			return v.currentFunction != nil && v.currentFunction.Mutating
		}
		if b := v.lookup(n.Name); b != nil {
			return b.mutable
		}
	}
	return false
}

func (v *validator) inferGenericCall(f *FunctionDecl, argTypes []TypeRef, typeArgs []TypeRef) TypeRef {
	variables := typeParamNames(f.TypeParams)
	bindings := map[string]TypeRef{}
	if len(typeArgs) > 0 {
		if len(typeArgs) != len(f.TypeParams) {
			v.error("C096", f.Pos, v.toLineEnd(f.Pos), "function '%s' requires %d type argument(s), found %d", f.Name, len(f.TypeParams), len(typeArgs))
		}
		for i, tp := range f.TypeParams {
			if i < len(typeArgs) {
				bindings[tp.Name] = typeArgs[i]
			}
		}
	}
	for i, p := range f.Params {
		if i >= len(argTypes) {
			break
		}
		bindTypePattern(p.Type, argTypes[i], variables, bindings)
	}
	if len(typeArgs) > 0 {
		for i, p := range f.Params {
			if i >= len(argTypes) {
				break
			}
			concrete := substituteType(p.Type, bindings)
			free := false
			for n := range freeNames(concrete) {
				if variables[n] {
					free = true
				}
			}
			if !free && !argTypes[i].equal(unknownT) && !v.assignable(argTypes[i], concrete) {
				v.error("C101", f.Pos, v.toLineEnd(f.Pos), "argument %d of '%s': expected %s but got %s", i+1, f.Name, concrete, argTypes[i])
			}
		}
	}
	for _, tp := range f.TypeParams {
		actual := bindingOrUnknown(bindings, tp.Name)
		if actual.equal(unknownT) {
			continue
		}
		for _, constraint := range tp.Constraints {
			need := substituteType(constraint, bindings)
			unbound := map[string]bool{}
			for _, t := range f.TypeParams {
				if a, seen := bindings[t.Name]; !seen || a.equal(unknownT) {
					unbound[t.Name] = true
				}
			}
			ok, solved := v.traitSatisfaction(actual, need, unbound)
			if !ok {
				v.error("C095", f.Pos, v.toLineEnd(f.Pos), "type %s does not satisfy generic constraint %s", actual, need)
			}
			for k, val := range solved {
				bindings[k] = val
			}
		}
	}
	return substituteType(f.ReturnType, bindings)
}

func (v *validator) checkValueForType(expr any, expected TypeRef) {
	if list, ok := expr.(*ListExpr); ok && expected.Name == "list" && len(expected.Args) > 0 {
		for _, item := range list.Items {
			actual := v.infer(item)
			if !v.assignable(actual, expected.Args[0]) {
				v.error("C082", exprPos(item, list.Pos), literalSpan(item), "list element: expected %s but got %s", expected.Args[0], actual)
			}
		}
		return
	}
	if m, ok := expr.(*MapExpr); ok && expected.Name == "map" && len(expected.Args) == 2 {
		for _, kv := range m.Items {
			v.infer(kv.Key)
			actual := v.infer(kv.Value)
			if !v.assignable(actual, expected.Args[1]) {
				v.error("C037", exprPos(kv.Value, m.Pos), literalSpan(kv.Value), "cannot assign %s to map value of type %s", actual, expected.Args[1])
			}
		}
		return
	}
	if se, ok := expr.(*StructExpr); ok && expected.Name == se.TypeName && len(expected.Args) > 0 {
		v.checkStructValue(expr, expected)
		return
	}
	if expected.Name == "func" {
		v.checkFuncAssignment(expected, expr)
	}
	v.checkStructValue(expr, expected)
	actual := v.infer(expr)
	if !actual.equal(unknownT) && actual.Name != "any" && expected.Name != "any" && expected.Name != "<unknown>" &&
		!(expected.Name == "exception" && actual.Name == "string") && !v.assignable(actual, expected) {
		v.error("C118", exprPos(expr, SourcePos{}), literalSpan(expr), "declared type %s but initializer has type %s", expected, actual)
	}
}

func (v *validator) checkEnumValue(expr any, expected TypeRef) {
	if c, ok := expr.(*Call); ok && len(c.TypeArgs) == 0 {
		if m, ok := c.Callee.(*Member); ok {
			if dotted, dok := dottedExpressionName(m.Obj); dok && expected.Name != "" && len(expected.Args) > 0 {
				_, dLocal := splitTypeName(dotted)
				_, tLocal := splitTypeName(expected.Name)
				if dLocal == tLocal {
					if enum := v.enumOf(dotted); enum != nil && len(expected.Args) == len(enum.TypeParams) {
						v.inferEnumConstruction(enum, typeRefN(v.canonical(dotted), expected.Args), c)
						return
					}
				}
			}
		}
	}
	v.infer(expr)
}

func (v *validator) checkStructValue(expr any, expected TypeRef) {
	if se, ok := expr.(*StructExpr); ok && expected.Name == se.TypeName && len(expected.Args) > 0 && len(se.TypeArgs) == 0 {
		if s := v.structOf(expected.Name); s != nil && len(expected.Args) == len(s.TypeParams) {
			seeds := map[string]TypeRef{}
			for i, p := range s.TypeParams {
				if i < len(expected.Args) {
					seeds[p.Name] = expected.Args[i]
				}
			}
			v.inferStructExpr(se, seeds)
			return
		}
	}
	v.checkEnumValue(expr, expected)
}

func (v *validator) inferStructExpr(x *StructExpr, seeds map[string]TypeRef) TypeRef {
	s := v.structOf(x.TypeName)
	if s == nil {
		for _, f := range x.Fields {
			v.infer(f.Value)
		}
		return typeRefN(x.TypeName, x.TypeArgs)
	}
	v.checkTypeVisibility(s, x.TypeName, x.Pos, "struct")
	if len(x.TypeArgs) > 0 && len(x.TypeArgs) != len(s.TypeParams) {
		v.error("C096", x.Pos, v.toLineEnd(x.Pos), "struct '%s' requires %d type argument(s), found %d", s.Name, len(s.TypeParams), len(x.TypeArgs))
	}
	if crossPkg := splitFirstPkg(v.canonical(x.TypeName)) != v.pkg; crossPkg {
		publicFields := map[string]bool{}
		for _, f := range s.Fields {
			if f.Public {
				publicFields[f.Name] = true
			}
		}
		for _, f := range x.Fields {
			if !publicFields[f.Name] {
				v.error("C120", exprPos(f.Value, x.Pos), literalSpan(f.Value), "field '%s' of '%s' is private; only pub fields are visible outside the package", f.Name, v.canonical(x.TypeName))
			}
		}
	}
	variables := typeParamNames(s.TypeParams)
	typeBindings := map[string]TypeRef{}
	for k, val := range seeds {
		typeBindings[k] = val
	}
	for i, p := range s.TypeParams {
		if i < len(x.TypeArgs) {
			typeBindings[p.Name] = x.TypeArgs[i]
		}
	}
	fieldMap := map[string]*FieldDecl{}
	for i := range s.Fields {
		fieldMap[s.Fields[i].Name] = &s.Fields[i]
	}
	for _, f := range x.Fields {
		actual := v.infer(f.Value)
		if fd := fieldMap[f.Name]; fd != nil {
			bindTypePattern(fd.Type, actual, variables, typeBindings)
		}
	}
	for _, p := range s.TypeParams {
		actual := bindingOrUnknown(typeBindings, p.Name)
		if !actual.equal(unknownT) && !v.currentTypeParams[actual.Name] {
			for _, constraint := range p.Constraints {
				need := substituteType(constraint, typeBindings)
				if !v.typeSatisfiesTrait(actual, need) {
					v.error("C095", x.Pos, v.toLineEnd(x.Pos), "type %s does not satisfy generic constraint %s", actual, need)
				}
			}
		}
	}
	for _, f := range x.Fields {
		fd := fieldMap[f.Name]
		if fd == nil {
			continue
		}
		concrete := substituteType(fd.Type, typeBindings)
		free := false
		for n := range freeNames(concrete) {
			if variables[n] {
				free = true
			}
		}
		if free {
			continue
		}
		actual := v.infer(f.Value)
		if !concrete.equal(unknownT) && !actual.equal(unknownT) && !v.assignable(actual, concrete) {
			v.error("C098", exprPos(f.Value, x.Pos), literalSpan(f.Value), "field '%s' of struct '%s': expected %s but got %s", f.Name, s.Name, concrete, actual)
		}
	}
	args := make([]TypeRef, len(s.TypeParams))
	for i, p := range s.TypeParams {
		args[i] = bindingOrUnknown(typeBindings, p.Name)
	}
	return typeRefN(x.TypeName, args)
}

func splitFirstPkg(name string) string {
	p, _ := splitTypeName(name)
	return p
}

func (v *validator) inferEnumConstruction(enum *EnumDecl, objType TypeRef, x *Call) TypeRef {
	memberName := ""
	if m, ok := x.Callee.(*Member); ok {
		memberName = m.Name
	}
	member := enumMember(enum, memberName)
	if member == nil {
		v.error("C107", x.Pos, v.toLineEnd(x.Pos), "enum '%s' has no case '%s'", enum.Name, memberName)
		return unknownT
	}
	explicit := memberTypeArgsOf(x.Callee.(*Member).Obj)
	if len(explicit) == 0 {
		explicit = objType.Args
	}
	if len(member.PayloadTypes) == 0 {
		if len(x.Args) > 0 {
			v.error("C107", x.Pos, v.toLineEnd(x.Pos), "case '%s' takes no payload", memberName)
		}
		return typeRefN(enum.Name, explicit)
	}
	if len(explicit) > 0 && len(explicit) != len(enum.TypeParams) {
		v.error("C096", x.Pos, v.toLineEnd(x.Pos), "enum '%s' requires %d type argument(s), found %d", enum.Name, len(enum.TypeParams), len(explicit))
	}
	variables := typeParamNames(enum.TypeParams)
	typeBindings := map[string]TypeRef{}
	for i, p := range enum.TypeParams {
		if i < len(explicit) {
			typeBindings[p.Name] = explicit[i]
		}
	}
	argTypes := make([]TypeRef, len(x.Args))
	for i, a := range x.Args {
		argTypes[i] = v.infer(a.Expr)
	}
	if len(argTypes) != len(member.PayloadTypes) {
		v.error("C101", x.Pos, v.toLineEnd(x.Pos), "case '%s' expects %d payload value(s), found %d", memberName, len(member.PayloadTypes), len(argTypes))
	}
	for i, ptype := range member.PayloadTypes {
		if i >= len(argTypes) {
			break
		}
		bindTypePattern(ptype, argTypes[i], variables, typeBindings)
	}
	for i, ptype := range member.PayloadTypes {
		if i >= len(argTypes) {
			break
		}
		concrete := substituteType(ptype, typeBindings)
		free := false
		for n := range freeNames(concrete) {
			if variables[n] {
				free = true
			}
		}
		if !free && !argTypes[i].equal(unknownT) && !v.assignable(argTypes[i], concrete) {
			v.error("C101", x.Pos, v.toLineEnd(x.Pos), "payload of case '%s': expected %s but got %s", memberName, concrete, argTypes[i])
		}
	}
	for _, p := range enum.TypeParams {
		actual := bindingOrUnknown(typeBindings, p.Name)
		if !actual.equal(unknownT) && !v.currentTypeParams[actual.Name] {
			for _, constraint := range p.Constraints {
				need := substituteType(constraint, typeBindings)
				if !v.typeSatisfiesTrait(actual, need) {
					v.error("C095", x.Pos, v.toLineEnd(x.Pos), "type %s does not satisfy generic constraint %s", actual, need)
				}
			}
		}
	}
	args := make([]TypeRef, len(enum.TypeParams))
	for i, p := range enum.TypeParams {
		args[i] = bindingOrUnknown(typeBindings, p.Name)
	}
	return typeRefN(enum.Name, args)
}
