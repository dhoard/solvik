package reference

import (
	"math"
	"regexp"
)

// regexCache compiles regex values lazily.
var regexCache = map[string]*regexp.Regexp{}

func regexCompiled(r *regexValue) *regexp.Regexp {
	if re, ok := regexCache[r.pattern]; ok {
		return re
	}
	re, err := regexp.Compile(r.pattern)
	if err != nil {
		panic(runtimeErr("invalid regex: %v", err))
	}
	regexCache[r.pattern] = re
	return re
}

// ---- expressions ----

func (in *Interpreter) tryEval(e any, env *env, pkg string, receiver *structValue, receiverMutable bool) (any, error) {
	var out any
	func() {
		defer func() {
			if r := recover(); r != nil {
				panic(r)
			}
		}()
		out = in.evalExpr(e, env, pkg, receiver, receiverMutable)
	}()
	return out, nil
}

func (in *Interpreter) evalExpr(e any, env *env, pkg string, receiver *structValue, receiverMutable bool) any {
	switch x := e.(type) {
	case *Literal:
		return x.Value
	case *Name:
		return in.resolveName(x.Name, env, pkg, receiver, receiverMutable)
	case *FuncExpr:
		args := make([]TypeRef, 0, len(x.Params)+1)
		for _, p := range x.Params {
			args = append(args, in.resolveRuntimeType(p.Type))
		}
		args = append(args, in.resolveRuntimeType(x.ReturnType))
		sig := typeRefN("func", args)
		decl := &FunctionDecl{Name: "<closure>", Params: x.Params, ReturnType: x.ReturnType, Body: x.Body, Pos: x.Pos}
		return &closureValue{decl: decl, env: env, pkg: pkg, receiver: receiver, receiverMutable: receiverMutable, typeRef: sig}
	case *ListExpr:
		out := make([]any, 0, len(x.Items))
		for _, item := range x.Items {
			out = append(out, copyValue(in.evalExpr(item, env, pkg, receiver, receiverMutable)))
		}
		return out
	case *MapExpr:
		m := newSolvikMap()
		for _, kv := range x.Items {
			k := in.evalExpr(kv.Key, env, pkg, receiver, receiverMutable)
			m.set(k, copyValue(in.evalExpr(kv.Value, env, pkg, receiver, receiverMutable)))
		}
		return m
	case *StructExpr:
		return in.evalStructExpr(x, env, pkg, receiver, receiverMutable)
	case *Unary:
		v := in.evalExpr(x.Expr, env, pkg, receiver, receiverMutable)
		v = numericValue(v)
		switch x.Op {
		case "!":
			return !truth(v)
		case "-":
			switch n := v.(type) {
			case int64:
				return -n
			case float64:
				return -n
			}
			panic(runtimeErr("unary - requires a number"))
		case "+":
			return v
		case "~":
			n, ok := v.(int64)
			if !ok {
				panic(runtimeErr("operator ~ requires an integer"))
			}
			return ^n
		}
		panic(runtimeErr("unsupported unary operator %s", x.Op))
	case *Binary:
		return in.evalBinary(x, env, pkg, receiver, receiverMutable)
	case *Assign:
		in.seedAssignment(x.Target, x.Value, env, pkg, receiver)
		value := in.evalExpr(x.Value, env, pkg, receiver, receiverMutable)
		return in.assignTarget(x.Target, value, env, pkg, receiver, receiverMutable)
	case *Index:
		obj := in.evalExpr(x.Obj, env, pkg, receiver, receiverMutable)
		if obj == nil {
			panic(runtimeErrCode("E031", "null reference"))
		}
		idx := in.evalExpr(x.Index, env, pkg, receiver, receiverMutable)
		switch o := obj.(type) {
		case string:
			runes := []rune(o)
			i := int(toIntLike(idx))
			if i < 0 || i >= len(runes) {
				panic(runtimeErrCode("E031", "index out of range"))
			}
			return charValue(string(runes[i]))
		case []any:
			i := int(toIntLike(idx))
			if i < 0 || i >= len(o) {
				panic(runtimeErrCode("E031", "index out of range"))
			}
			return copyValue(o[i])
		case *solvikMap:
			v, has := o.get(idx)
			if !has {
				panic(runtimeErrCode("E031", "map key not found"))
			}
			return copyValue(v)
		}
		panic(runtimeErr("value is not indexable"))
	case *Member:
		return in.evalMember(x, env, pkg, receiver, receiverMutable)
	case *Call:
		callee, mutable := in.resolveCallCallee(x.Callee, env, pkg, receiver, receiverMutable)
		args := []any{}
		hints := []any{}
		for _, a := range x.Args {
			v := in.evalExpr(a.Expr, env, pkg, receiver, receiverMutable)
			if a.Spread {
				list, ok := v.([]any)
				if !ok {
					panic(runtimeErr("spread requires a list"))
				}
				for _, item := range list {
					args = append(args, copyValue(item))
					hints = append(hints, nil)
				}
			} else {
				args = append(args, v)
				if v == nil {
					hints = append(hints, in.declaredTypeOf(a.Expr, env, receiver, pkg))
				} else {
					hints = append(hints, nil)
				}
			}
		}
		if ctor, ok := callee.(*caseConstructor); ok {
			var expected *TypeRef
			if et, has := structExprExpected(x); has {
				expected = et
			}
			return in.constructEnumCase(ctor, args, expected, hints)
		}
		result, err := in.callValue(callee, args, mutable, x.TypeArgs, hints)
		if err != nil {
			panic(err)
		}
		return result
	}
	panic(newSolvikError("unhandled expression node"))
}

func structExprExpected(c *Call) (*TypeRef, bool) {
	if t, ok := c.expectedType.(TypeRef); ok {
		return &t, true
	}
	return nil, false
}

func toIntLike(v any) int64 {
	switch n := numericValue(v).(type) {
	case int64:
		return n
	case float64:
		return int64(n)
	}
	panic(runtimeErr("index must be an integer"))
}

func (in *Interpreter) evalStructExpr(x *StructExpr, env *env, pkg string, receiver *structValue, receiverMutable bool) any {
	decl := in.structs[in.typeKeyOf(x.TypeName)]
	if decl == nil {
		panic(runtimeErr("unknown struct %s", x.TypeName))
	}
	fieldSet := map[string]any{}
	supplied := map[string]any{}
	for _, f := range x.Fields {
		supplied[f.Name] = in.evalExpr(f.Value, env, pkg, receiver, receiverMutable)
	}
	if len(supplied) != len(decl.Fields) {
		panic(runtimeErr("struct literal for %s must initialize every field exactly once", x.TypeName))
	}
	for _, f := range decl.Fields {
		if _, ok := supplied[f.Name]; !ok {
			panic(runtimeErr("struct literal for %s must initialize every field exactly once", x.TypeName))
		}
	}
	variables := typeParamNames(decl.TypeParams)
	typeBindings := map[string]TypeRef{}
	for i, p := range decl.TypeParams {
		if i < len(x.TypeArgs) {
			typeBindings[p.Name] = in.resolveRuntimeType(x.TypeArgs[i])
		}
	}
	if et, ok := x.expectedType.(TypeRef); ok {
		_, etLocal := splitTypeName(et.Name)
		if etLocal == decl.Name && len(et.Args) == len(decl.TypeParams) {
			for i, p := range decl.TypeParams {
				if i < len(et.Args) {
					if _, seen := typeBindings[p.Name]; !seen {
						typeBindings[p.Name] = in.resolveRuntimeType(et.Args[i])
					}
				}
			}
		}
	}
	for _, f := range decl.Fields {
		raw := supplied[f.Name]
		actual := valueTypeRef(raw)
		if actual.Name == "null" {
			if hint := in.declaredTypeOf(fieldExprByName(x, f.Name), env, receiver, pkg); hint != nil {
				if ht, ok := hint.(TypeRef); ok {
					actual = ht
				}
			}
		}
		bindTypePattern(f.Type, actual, variables, typeBindings)
	}
	for _, p := range decl.TypeParams {
		actual, ok := typeBindings[p.Name]
		if !ok || actual.equal(unknownT) {
			panic(runtimeErrCode("E067", "cannot infer type parameter %s for struct %s; annotate the declaration or use explicit type arguments like %s<...> [line %d]", p.Name, decl.Name, decl.Name, x.Pos.Line))
		}
		for _, c := range p.Constraints {
			need := substituteType(c, typeBindings)
			if !in.typeSatisfiesTrait(actual, need) {
				panic(runtimeErrCode("E067", "type %s does not satisfy generic constraint %s", actual, need))
			}
		}
	}
	for _, f := range decl.Fields {
		fieldSet[f.Name] = in.coerceForType(supplied[f.Name], substituteType(f.Type, typeBindings))
	}
	typeArgs := make([]TypeRef, len(decl.TypeParams))
	for i, p := range decl.TypeParams {
		typeArgs[i] = bindingOrUnknown(typeBindings, p.Name)
	}
	return &structValue{typeName: x.TypeName, fields: fieldSet, typeArgs: typeArgs}
}

func fieldExprByName(x *StructExpr, name string) any {
	for _, f := range x.Fields {
		if f.Name == name {
			return f.Value
		}
	}
	return nil
}

func (in *Interpreter) evalBinary(x *Binary, env *env, pkg string, receiver *structValue, receiverMutable bool) any {
	left := in.evalExpr(x.Left, env, pkg, receiver, receiverMutable)
	switch x.Op {
	case "??":
		if left != nil {
			return left
		}
		return in.evalExpr(x.Right, env, pkg, receiver, receiverMutable)
	case "&&":
		if !truth(left) {
			return false
		}
		return truth(in.evalExpr(x.Right, env, pkg, receiver, receiverMutable))
	case "||":
		if truth(left) {
			return true
		}
		return truth(in.evalExpr(x.Right, env, pkg, receiver, receiverMutable))
	}
	right := in.evalExpr(x.Right, env, pkg, receiver, receiverMutable)
	if x.Op == "==" || x.Op == "!=" {
		q := in.equal(left, right)
		if x.Op == "==" {
			return q
		}
		return !q
	}
	if left == nil || right == nil {
		panic(runtimeErrCode("E031", "null reference"))
	}
	if x.Op == ".." {
		return solvikString(left) + solvikString(right)
	}
	a, b := numericValue(left), numericValue(right)
	af, aIsF := a.(float64)
	bf, bIsF := b.(float64)
	ai, aIsI := a.(int64)
	bi, bIsI := b.(int64)
	switch x.Op {
	case "+":
		if aIsF || bIsF {
			return toF(a) + toF(b)
		}
		return ai + bi
	case "-":
		if aIsF || bIsF {
			return toF(a) - toF(b)
		}
		return ai - bi
	case "*":
		if aIsF || bIsF {
			return toF(a) * toF(b)
		}
		return ai * bi
	case "/":
		if isZero(b) {
			panic(runtimeErrCode("E031", "division by zero"))
		}
		if aIsF || bIsF {
			return toF(a) / toF(b)
		}
		return ai / bi
	case "%":
		if isZero(b) {
			panic(runtimeErrCode("E031", "division by zero"))
		}
		if aIsF || bIsF {
			return math.Mod(toF(a), toF(b))
		}
		return ai % bi
	case "<<":
		return ai << uint64(bi)
	case ">>":
		return ai >> uint64(bi)
	case "&":
		return ai & bi
	case "|":
		return ai | bi
	case "^":
		return ai ^ bi
	case "<", "<=", ">", ">=":
		lc, lIsChar := left.(charValue)
		rc, rIsChar := right.(charValue)
		var av, bv float64
		if lIsChar && rIsChar {
			av, bv = float64([]rune(lc)[0]), float64([]rune(rc)[0])
		} else {
			av, bv = toF(a), toF(b)
		}
		switch x.Op {
		case "<":
			return av < bv
		case "<=":
			return av <= bv
		case ">":
			return av > bv
		default:
			return av >= bv
		}
	}
	_ = af
	_ = bf
	_ = aIsI
	_ = bIsI
	panic(runtimeErr("unsupported operator %s", x.Op))
}

func toF(v any) float64 {
	switch n := v.(type) {
	case int64:
		return float64(n)
	case float64:
		return n
	}
	panic(runtimeErr("operator requires numbers"))
}

func isZero(v any) bool {
	switch n := v.(type) {
	case int64:
		return n == 0
	case float64:
		return n == 0
	}
	return false
}

func (in *Interpreter) equal(a, b any) bool {
	if a == nil || b == nil {
		return a == nil && b == nil
	}
	if as, ok := a.(*structValue); ok {
		bs, ok2 := b.(*structValue)
		if !ok2 {
			return false
		}
		if as.typeName != bs.typeName || len(as.fields) != len(bs.fields) {
			return false
		}
		for k, v := range as.fields {
			bv, has := bs.fields[k]
			if !has || !in.equal(v, bv) {
				return false
			}
		}
		return true
	}
	if ae, ok := a.(*enumValue); ok || enumIs(b) {
		be, ok2 := b.(*enumValue)
		if !ok || !ok2 {
			return false
		}
		if ae.enumName != be.enumName || ae.memberName != be.memberName {
			return false
		}
		if len(ae.payload) > 0 || len(be.payload) > 0 {
			if len(ae.payload) != len(be.payload) {
				return false
			}
			for i := range ae.payload {
				if !in.equal(ae.payload[i], be.payload[i]) {
					return false
				}
			}
			return true
		}
		return ae.value == be.value
	}
	if ab, ok := a.(*byteValue); ok {
		if bb, ok2 := b.(*byteValue); ok2 {
			return ab.v == bb.v
		}
		if bi, ok2 := b.(int64); ok2 {
			return ab.v == bi
		}
		return false
	}
	if ac, ok := a.(charValue); ok {
		bc, ok2 := b.(charValue)
		return ok2 && ac == bc
	}
	// Deep comparison for collection values.
	if al, ok := a.([]any); ok {
		bl, ok2 := b.([]any)
		if !ok2 || len(al) != len(bl) {
			return false
		}
		for i := range al {
			if !in.equal(al[i], bl[i]) {
				return false
			}
		}
		return true
	}
	if am, ok := a.(*solvikMap); ok {
		bm, ok2 := b.(*solvikMap)
		if !ok2 || len(am.entries) != len(bm.entries) {
			return false
		}
		for mk, entry := range am.entries {
			other, has := bm.entries[mk]
			if !has || !in.equal(entry.value, other.value) {
				return false
			}
		}
		return true
	}
	if as, ok := a.(*stackValue); ok {
		bs, ok2 := b.(*stackValue)
		if !ok2 || len(as.items) != len(bs.items) {
			return false
		}
		for i := range as.items {
			if !in.equal(as.items[i], bs.items[i]) {
				return false
			}
		}
		return true
	}
	if an, ok := numericValue(a).(int64); ok {
		if bn, ok2 := numericValue(b).(int64); ok2 {
			return an == bn
		}
	}
	if af, ok := numericValue(a).(float64); ok {
		switch bf := numericValue(b).(type) {
		case float64:
			return af == bf
		case int64:
			return af == float64(bf)
		}
	}
	if ai, ok := numericValue(a).(int64); ok {
		if bf, ok2 := numericValue(b).(float64); ok2 {
			return float64(ai) == bf
		}
	}
	if astr, ok := a.(string); ok {
		if bstr, ok2 := b.(string); ok2 {
			return astr == bstr
		}
	}
	return a == b
}

func enumIs(v any) bool {
	_, ok := v.(*enumValue)
	return ok
}

func samePointer(a, b any) bool {
	return a == b
}

func (in *Interpreter) resolveCallCallee(calleeExpr any, env *env, pkg string, receiver *structValue, receiverMutable bool) (any, bool) {
	m, isMember := calleeExpr.(*Member)
	if !isMember {
		return in.evalExpr(calleeExpr, env, pkg, receiver, receiverMutable), false
	}
	base := m.Obj
	mutable := in.targetIsMutable(base, env, receiver, receiverMutable)
	var actual any
	haveActual := false
	if n, ok := base.(*Name); ok {
		if b := env.tryFindBinding(n.Name); b != nil {
			actual = b.value
			haveActual = true
		} else if receiver != nil {
			if v, has := receiver.fields[n.Name]; has {
				actual = v
				haveActual = true
			}
		}
	}
	if !haveActual {
		return in.evalExpr(calleeExpr, env, pkg, receiver, receiverMutable), mutable
	}
	if sv, ok := actual.(*structValue); ok {
		key := in.typeKeyOf(sv.typeName)
		decl := in.structs[key]
		if decl != nil {
			for _, mm := range decl.Methods {
				if mm.Name == m.Name {
					return &boundMethod{receiver: sv, fn: &userFunction{decl: mm, pkg: pkg}, receiverMutable: mutable}, mutable
				}
			}
		}
	}
	if native := builtinMethod(actual, m.Name, in); native != nil {
		return native, mutable
	}
	return in.evalExpr(calleeExpr, env, pkg, receiver, receiverMutable), mutable
}

func (e *env) tryFindBinding(name string) *binding {
	if b, ok := e.bindings[name]; ok {
		return b
	}
	if e.parent != nil {
		return e.parent.tryFindBinding(name)
	}
	return nil
}

func (in *Interpreter) resolveName(name string, env *env, pkg string, receiver *structValue, receiverMutable bool) any {
	if b := env.tryFindBinding(name); b != nil {
		return copyValue(b.value)
	}
	if receiver != nil {
		if v, has := receiver.fields[name]; has {
			return copyValue(v)
		}
		if name == "self" {
			return receiver
		}
		key := in.typeKeyOf(receiver.typeName)
		if decl := in.structs[key]; decl != nil {
			for _, m := range decl.Methods {
				if m.Name == name {
					return &boundMethod{receiver: receiver, fn: &userFunction{decl: m, pkg: pkg}, receiverMutable: receiverMutable}
				}
			}
		}
	}
	if ns := in.packages[pkg]; ns != nil {
		if v, has := ns.values[name]; has {
			return v
		}
	}
	if ns, has := in.packages[name]; has {
		return ns
	}
	if v, has := in.builtins[name]; has {
		return v
	}
	panic(runtimeErr("undefined name %s", name))
}

func (in *Interpreter) evalMember(x *Member, env *env, pkg string, receiver *structValue, receiverMutable bool) any {
	obj := in.evalExpr(x.Obj, env, pkg, receiver, receiverMutable)
	if obj == nil {
		panic(runtimeErrCode("E031", "null reference"))
	}
	switch o := obj.(type) {
	case *namespace:
		v, has := o.values[x.Name]
		if !has {
			panic(runtimeErr("namespace %s has no member %s", o.name, x.Name))
		}
		return v
	case *enumTypeValue:
		member, has := o.members[x.Name]
		if !has {
			panic(runtimeErr("enum %s has no member %s", o.decl.Name, x.Name))
		}
		typeArgs := []TypeRef{}
		if ta := memberTypeArgs(x.Obj); ta != nil {
			for _, a := range ta {
				typeArgs = append(typeArgs, in.resolveRuntimeType(a))
			}
		}
		for _, m := range o.decl.Members {
			if m.Name == x.Name && len(m.PayloadTypes) > 0 {
				return &caseConstructor{enumName: o.canonicalName, caseName: x.Name, payloadTypes: m.PayloadTypes, typeArgs: typeArgs, pkg: pkg}
			}
		}
		ev := *member
		ev.enumName = o.canonicalName
		ev.typeArgs = typeArgs
		return &ev
	case *exceptionValue:
		if x.Name == "message" {
			return o.message
		}
		if x.Name == "code" {
			return o.code
		}
		if x.Name == "trace" {
			return o.trace
		}
		panic(runtimeErr("exception has no member %s", x.Name))
	case *structValue:
		if v, has := o.fields[x.Name]; has {
			key := in.typeKeyOf(o.typeName)
			decl := in.structs[key]
			if decl != nil {
				for _, f := range decl.Fields {
					if f.Name == x.Name {
						pkgOfStruct, _ := splitTypeName(o.typeName)
						if !f.Public && pkgOfStruct != "" && pkgOfStruct != pkg {
							panic(runtimeErrCode("E070", "field '%s' of '%s' is private", x.Name, o.typeName))
						}
						break
					}
				}
			}
			return copyValue(v)
		}
		key := in.typeKeyOf(o.typeName)
		if decl := in.structs[key]; decl != nil {
			for _, m := range decl.Methods {
				if m.Name == x.Name {
					pkgOfStruct, _ := splitTypeName(o.typeName)
					if !m.Public && pkgOfStruct != "" && pkgOfStruct != pkg {
						panic(runtimeErrCode("E070", "method '%s' of '%s' is private", x.Name, o.typeName))
					}
					return &boundMethod{receiver: o, fn: &userFunction{decl: m, pkg: pkg}, receiverMutable: in.targetIsMutable(x.Obj, env, receiver, receiverMutable)}
				}
			}
			panic(runtimeErr("struct %s has no member %s", o.typeName, x.Name))
		}
		panic(runtimeErr("struct %s has no member %s", o.typeName, x.Name))
	}
	if method := builtinMethod(obj, x.Name, in); method != nil {
		return method
	}
	panic(runtimeErr("type %s has no member %s", typeNameOf(obj), x.Name))
}

func memberTypeArgs(obj any) []TypeRef {
	switch o := obj.(type) {
	case *Name:
		return o.TypeArgs
	case *Member:
		return o.TypeArgs
	}
	return nil
}

func (in *Interpreter) targetIsMutable(target any, env *env, receiver *structValue, receiverMutable bool) bool {
	switch t := target.(type) {
	case *Name:
		if t.Name == "self" && receiver != nil {
			return receiverMutable
		}
		if b := env.tryFindBinding(t.Name); b != nil {
			return b.mutable
		}
		if receiver != nil {
			if _, has := receiver.fields[t.Name]; has {
				return receiverMutable
			}
		}
		return false
	case *Member:
		return in.targetIsMutable(t.Obj, env, receiver, receiverMutable)
	}
	return false
}

func (in *Interpreter) assignTarget(target any, value any, env *env, pkg string, receiver *structValue, receiverMutable bool) any {
	switch t := target.(type) {
	case *Name:
		if b := env.tryFindBinding(t.Name); b != nil {
			if !b.mutable {
				panic(runtimeErr("cannot assign to immutable variable %s", t.Name))
			}
			b.value = in.coerceForType(value, b.declaredType)
			return copyValue(b.value)
		}
		if receiver != nil {
			if _, has := receiver.fields[t.Name]; has {
				if !receiverMutable {
					panic(runtimeErr("cannot mutate receiver from non-mutating method"))
				}
				key := in.typeKeyOf(receiver.typeName)
				decl := in.structs[key]
				var fd *FieldDecl
				if decl != nil {
					for i := range decl.Fields {
						if decl.Fields[i].Name == t.Name {
							fd = &decl.Fields[i]
							break
						}
					}
				}
				if fd != nil && !fd.Mutable {
					panic(runtimeErr("field %s is immutable", t.Name))
				}
				if fd != nil {
					receiver.fields[t.Name] = in.coerceForType(value, fd.Type)
				} else {
					receiver.fields[t.Name] = value
				}
				return copyValue(receiver.fields[t.Name])
			}
		}
		panic(runtimeErr("undefined assignment target %s", t.Name))
	case *Member:
		obj := in.evalExpr(t.Obj, env, pkg, receiver, receiverMutable)
		sv, ok := obj.(*structValue)
		if !ok {
			panic(runtimeErr("member assignment requires struct"))
		}
		key := in.typeKeyOf(sv.typeName)
		decl := in.structs[key]
		if decl == nil {
			panic(runtimeErr("unknown struct %s", sv.typeName))
		}
		var fd *FieldDecl
		for i := range decl.Fields {
			if decl.Fields[i].Name == t.Name {
				fd = &decl.Fields[i]
				break
			}
		}
		if fd == nil || !fd.Mutable {
			panic(runtimeErr("field %s is immutable", t.Name))
		}
		sv.fields[t.Name] = in.coerceForType(value, fd.Type)
		if n, isName := t.Obj.(*Name); isName && n.Name != "self" {
			if b := env.tryFindBinding(n.Name); b != nil {
				b.value = sv
			}
		}
		return copyValue(sv.fields[t.Name])
	case *Index:
		obj := in.evalExpr(t.Obj, env, pkg, receiver, receiverMutable)
		idx := in.evalExpr(t.Index, env, pkg, receiver, receiverMutable)
		switch o := obj.(type) {
		case []any:
			i := int(toIntLike(idx))
			if i < 0 || i >= len(o) {
				panic(runtimeErrCode("E031", "index out of range"))
			}
			o[i] = copyValue(value)
			if n, isName := t.Obj.(*Name); isName {
				if b := env.tryFindBinding(n.Name); b != nil {
					b.value = o
				}
			}
			return copyValue(value)
		case *solvikMap:
			o.set(idx, copyValue(value))
			if n, isName := t.Obj.(*Name); isName {
				if b := env.tryFindBinding(n.Name); b != nil {
					b.value = o
				}
			}
			return copyValue(value)
		}
		panic(runtimeErr("value is not index-assignable"))
	}
	panic(runtimeErr("invalid assignment target"))
}

func (in *Interpreter) declaredTypeOf(expr any, env *env, receiver *structValue, pkg string) any {
	n, ok := expr.(*Name)
	if !ok {
		return nil
	}
	if b := env.tryFindBinding(n.Name); b != nil {
		return b.declaredType
	}
	if receiver != nil {
		if _, has := receiver.fields[n.Name]; has {
			key := in.typeKeyOf(receiver.typeName)
			decl := in.structs[key]
			if decl != nil {
				bindings := map[string]TypeRef{}
				for i, p := range decl.TypeParams {
					if i < len(receiver.typeArgs) {
						bindings[p.Name] = receiver.typeArgs[i]
					}
				}
				for _, f := range decl.Fields {
					if f.Name == n.Name {
						return substituteType(f.Type, bindings)
					}
				}
			}
		}
	}
	return nil
}

func (in *Interpreter) targetDeclaredType(target any, env *env, pkg string, receiver *structValue) any {
	switch t := target.(type) {
	case *Name:
		if t.Name == "self" && receiver != nil {
			return nil
		}
		if b := env.tryFindBinding(t.Name); b != nil {
			return b.declaredType
		}
		if receiver != nil {
			if _, has := receiver.fields[t.Name]; has {
				return in.declaredTypeOf(&Name{Name: t.Name, Pos: t.Pos}, env, receiver, pkg)
			}
		}
		return nil
	case *Member:
		if n, ok := t.Obj.(*Name); ok {
			if n.Name == "self" && receiver != nil {
				return in.declaredTypeOf(&Name{Name: t.Name, Pos: t.Pos}, env, receiver, pkg)
			}
			if b := env.tryFindBinding(n.Name); b != nil {
				key := in.typeKeyOf(b.declaredType.Name)
				decl := in.structs[key]
				if decl == nil {
					return nil
				}
				bindings := map[string]TypeRef{}
				for i, p := range decl.TypeParams {
					if i < len(b.declaredType.Args) {
						bindings[p.Name] = b.declaredType.Args[i]
					}
				}
				for _, f := range decl.Fields {
					if f.Name == t.Name {
						return substituteType(f.Type, bindings)
					}
				}
			}
		}
		return nil
	}
	return nil
}

func (in *Interpreter) seedAssignment(target any, valueExpr any, env *env, pkg string, receiver *structValue) {
	typ := in.targetDeclaredType(target, env, pkg, receiver)
	if t, ok := typ.(TypeRef); ok {
		in.seedExpectedType(valueExpr, t)
	}
}

func (in *Interpreter) seedExpectedType(valueExpr any, typ TypeRef) {
	if se, ok := valueExpr.(*StructExpr); ok {
		if len(se.TypeArgs) == 0 && typ.Name == se.TypeName && len(typ.Args) > 0 {
			se.expectedType = typ.nonnull()
			return
		}
	}
	if c, ok := valueExpr.(*Call); ok && len(c.TypeArgs) == 0 {
		if m, isMember := c.Callee.(*Member); isMember {
			if dotted, dok := dottedExpressionName(m.Obj); dok {
				_, local := splitTypeName(dotted)
				if _, tlocal := splitTypeName(typ.Name); local == tlocal && len(typ.Args) > 0 {
					c.expectedType = typ.nonnull()
				}
			}
		}
	}
}
