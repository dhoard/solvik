package reference

import "fmt"

func fmtI64(v int64) string                        { return fmt.Sprintf("%d", v) }
func fmtF64(v float64) string                      { return fmt.Sprintf("%g", v) }
func fmtSPrintf(format string, args ...any) string { return fmt.Sprintf(format, args...) }
func fmtGeneric(v any) string                      { return fmt.Sprintf("%v", v) }

// Runtime values mirror the Python reference: byteValue, charValue,
// enumValue, stackValue, structValue, regexValue, exceptionValue,
// userFunction, boundMethod, closureValue, nativeFn, namespace,
// enumTypeValue, caseConstructor, structTypeValue.

type byteValue struct{ v int64 }
type enumValue struct {
	enumName   string
	memberName string
	value      int64
	payload    []any
	typeArgs   []TypeRef
}
type stackValue struct{ items []any }
type structValue struct {
	typeName string
	fields   map[string]any
	typeArgs []TypeRef
}
type regexValue struct {
	pattern string
}
type exceptionValue struct {
	message string
	trace   string
	code    string
}
type userFunction struct {
	decl *FunctionDecl
	pkg  string
}
type boundMethod struct {
	receiver        *structValue
	fn              *userFunction
	receiverMutable bool
}
type closureValue struct {
	decl            *FunctionDecl
	env             *env
	pkg             string
	receiver        *structValue
	receiverMutable bool
	typeRef         TypeRef
}
type nativeFn struct {
	name string
	fn   func(args ...any) any
}
type namespace struct {
	name   string
	values map[string]any
	callFn func(args ...any) any
}

// solvikMap is the runtime representation of a language map.  A Go
// map[string]any is insufficient here: Solvik permits scalar, enum, and
// callable keys, and stringifying keys would make (for example) int(1) and
// enum Color.Green collide.  Keep the original key alongside a canonical
// comparable key so lookup, iteration, copying, and rendering preserve
// language semantics.
type solvikMapKey struct {
	kind  string
	value string
}

type solvikMapEntry struct {
	key   any
	value any
}

type solvikMap struct {
	entries map[solvikMapKey]solvikMapEntry
	order   []solvikMapKey
}

func newSolvikMap() *solvikMap {
	return &solvikMap{entries: map[solvikMapKey]solvikMapEntry{}}
}

func solvikMapKeyOf(v any) (solvikMapKey, bool) {
	switch x := v.(type) {
	case string:
		return solvikMapKey{"string", x}, true
	case charValue:
		return solvikMapKey{"char", string(x)}, true
	case bool:
		return solvikMapKey{"bool", fmt.Sprintf("%t", x)}, true
	case *byteValue:
		return solvikMapKey{"byte", fmtI64(x.v)}, true
	case int64:
		return solvikMapKey{"int", fmtI64(x)}, true
	case float64:
		return solvikMapKey{"float", fmtF64(x)}, true
	case *enumValue:
		return solvikMapKey{"enum", x.enumName + "." + x.memberName + "(" + solvikStringArgs(x.payload) + ")"}, true
	case *userFunction:
		return solvikMapKey{"function", fmt.Sprintf("user:%p", x)}, true
	case *closureValue:
		return solvikMapKey{"function", fmt.Sprintf("closure:%p", x)}, true
	case *boundMethod:
		return solvikMapKey{"function", fmt.Sprintf("method:%p", x)}, true
	case *nativeFn:
		return solvikMapKey{"function", fmt.Sprintf("native:%p", x)}, true
	default:
		return solvikMapKey{}, false
	}
}

func solvikStringArgs(values []any) string {
	out := ""
	for i, value := range values {
		if i > 0 {
			out += ","
		}
		out += solvikString(value)
	}
	return out
}

func (m *solvikMap) set(key, value any) {
	mk, ok := solvikMapKeyOf(key)
	if !ok {
		panic(runtimeErr("map key must be a scalar value"))
	}
	if _, exists := m.entries[mk]; !exists {
		m.order = append(m.order, mk)
	}
	m.entries[mk] = solvikMapEntry{key: key, value: value}
}

func (m *solvikMap) get(key any) (any, bool) {
	mk, ok := solvikMapKeyOf(key)
	if !ok {
		return nil, false
	}
	entry, exists := m.entries[mk]
	if !exists {
		return nil, false
	}
	return entry.value, true
}

func (m *solvikMap) keys() []any {
	out := make([]any, 0, len(m.order))
	for _, mk := range m.order {
		if entry, ok := m.entries[mk]; ok {
			out = append(out, entry.key)
		}
	}
	return out
}

func (m *solvikMap) entriesInOrder() []solvikMapEntry {
	out := make([]solvikMapEntry, 0, len(m.order))
	for _, mk := range m.order {
		if entry, ok := m.entries[mk]; ok {
			out = append(out, entry)
		}
	}
	return out
}

type enumTypeValue struct {
	decl          *EnumDecl
	members       map[string]*enumValue
	canonicalName string
}
type caseConstructor struct {
	enumName     string
	caseName     string
	payloadTypes []TypeRef
	typeArgs     []TypeRef
	pkg          string
}
type structTypeValue struct{ decl *StructDecl }

type binding struct {
	value        any
	declaredType TypeRef
	mutable      bool
}

type env struct {
	parent   *env
	bindings map[string]*binding
}

func newEnv(parent *env) *env { return &env{parent: parent, bindings: map[string]*binding{}} }

func (e *env) declare(name string, value any, typ TypeRef, mutable bool) {
	if _, exists := e.bindings[name]; exists {
		panic(runtimeErr("duplicate variable %s", name))
	}
	e.bindings[name] = &binding{value: value, declaredType: typ, mutable: mutable}
}

func (e *env) find(name string) *env {
	if b, ok := e.bindings[name]; ok && b != nil {
		return e
	}
	if e.parent != nil {
		return e.parent.find(name)
	}
	panic(runtimeErr("undefined name %s", name))
}

func (e *env) getBinding(name string) *binding { return e.find(name).bindings[name] }
func (e *env) get(name string) any             { return e.getBinding(name).value }

// copyValue implements Solvik value semantics.
func copyValue(v any) any {
	switch x := v.(type) {
	case *structValue:
		fields := map[string]any{}
		for k, val := range x.fields {
			fields[k] = copyValue(val)
		}
		return &structValue{typeName: x.typeName, fields: fields, typeArgs: x.typeArgs}
	case []any:
		out := make([]any, len(x))
		for i, item := range x {
			out[i] = copyValue(item)
		}
		return out
	case map[string]any:
		out := map[string]any{}
		for k, val := range x {
			out[k] = copyValue(val)
		}
		return out
	case *solvikMap:
		out := newSolvikMap()
		for _, entry := range x.entriesInOrder() {
			out.set(copyValue(entry.key), copyValue(entry.value))
		}
		return out
	case *stackValue:
		items := make([]any, len(x.items))
		for i, item := range x.items {
			items[i] = copyValue(item)
		}
		return &stackValue{items: items}
	}
	return v
}

func numericValue(v any) any {
	switch x := v.(type) {
	case *byteValue:
		return x.v
	case charValue:
		return int64([]rune(x)[0])
	}
	return v
}

func typeNameOf(v any) string {
	switch x := v.(type) {
	case nil:
		return "null"
	case bool:
		return "bool"
	case *byteValue:
		return "byte"
	case *enumValue:
		return localLower(x.enumName)
	case charValue:
		return "char"
	case int64:
		return "int"
	case float64:
		return "float"
	case string:
		return "string"
	case []any:
		return "list"
	case map[string]any:
		return "map"
	case *solvikMap:
		return "map"
	case *stackValue:
		return "stack"
	case *structValue:
		return localLower(x.typeName)
	case *userFunction, *closureValue, *boundMethod, *bcCallable, *nativeFn:
		return "function"
	case *exceptionValue:
		return "exception"
	case *regexValue:
		return "regex"
	}
	return "any"
}

func localLower(name string) string {
	_, local := splitTypeName(name)
	return toLower(local)
}

func solvikString(v any) string {
	switch x := v.(type) {
	case nil:
		return "null"
	case bool:
		if x {
			return "true"
		}
		return "false"
	case *byteValue:
		return fmtI64(x.v)
	case charValue:
		return string(x)
	case *enumValue:
		if len(x.payload) > 0 {
			out := x.memberName + "("
			for i, p := range x.payload {
				if i > 0 {
					out += ", "
				}
				out += solvikString(p)
			}
			return out + ")"
		}
		return fmtI64(x.value)
	case float64:
		if x == float64(int64(x)) {
			return fmtI64(int64(x))
		}
		return fmtF64(x)
	case string:
		return x
	case []any:
		out := "["
		for i, item := range x {
			if i > 0 {
				out += " "
			}
			out += solvikString(item)
		}
		return out + "]"
	case *stackValue:
		out := "["
		for i, item := range x.items {
			if i > 0 {
				out += " "
			}
			out += solvikString(item)
		}
		return out + "]"
	case map[string]any:
		out := "map["
		first := true
		for k, val := range x {
			if !first {
				out += " "
			}
			first = false
			out += solvikString(k) + ":" + solvikString(val)
		}
		return out + "]"
	case *solvikMap:
		out := "map["
		for i, entry := range x.entriesInOrder() {
			if i > 0 {
				out += " "
			}
			out += solvikString(entry.key) + ":" + solvikString(entry.value)
		}
		return out + "]"
	case *structValue:
		_, local := splitTypeName(x.typeName)
		out := local + "{"
		first := true
		for k, val := range x.fields {
			if !first {
				out += ", "
			}
			first = false
			out += k + ": " + solvikString(val)
		}
		return out + "}"
	case *exceptionValue:
		return x.message
	case *closureValue:
		return "<closure>"
	case *userFunction:
		return "<function " + x.decl.Name + ">"
	case *bcCallable:
		if x.function.decl.Name == "<closure>" {
			return "<closure>"
		}
		return "<function " + x.function.decl.Name + ">"
	case *boundMethod:
		return "<function " + x.fn.decl.Name + ">"
	case *nativeFn:
		return "<function " + x.name + ">"
	}
	return fmtGeneric(v)
}

func truth(v any) bool {
	if b, ok := v.(bool); ok {
		return b
	}
	panic(runtimeErrCode("E066", "condition requires bool, got %s", typeNameOf(v)))
}

func valueTypeRef(v any) TypeRef {
	switch x := v.(type) {
	case nil:
		return nullT
	case bool:
		return typeRef("bool")
	case *byteValue:
		return typeRef("byte")
	case *enumValue:
		return typeRefN(x.enumName, x.typeArgs)
	case charValue:
		return typeRef("char")
	case int64:
		return typeRef("int")
	case float64:
		return typeRef("float")
	case string:
		return typeRef("string")
	case []any:
		elem := unknownT
		if len(x) > 0 {
			elem = valueTypeRef(x[0])
		}
		return typeRef("list", elem)
	case map[string]any:
		k, val := unknownT, unknownT
		for key, value := range x {
			k, val = valueTypeRef(key), valueTypeRef(value)
			break
		}
		return typeRef("map", k, val)
	case *solvikMap:
		k, val := unknownT, unknownT
		for _, entry := range x.entriesInOrder() {
			k, val = valueTypeRef(entry.key), valueTypeRef(entry.value)
			break
		}
		return typeRef("map", k, val)
	case *stackValue:
		elem := unknownT
		if len(x.items) > 0 {
			elem = valueTypeRef(x.items[0])
		}
		return typeRef("stack", elem)
	case *structValue:
		return typeRefN(x.typeName, x.typeArgs)
	case *closureValue, *userFunction, *boundMethod, *bcCallable:
		return functionValueType(v)
	case *exceptionValue:
		return exceptionT
	case *regexValue:
		return regexT
	}
	return anyT
}

func isVariadicDecl(d *FunctionDecl) bool {
	return len(d.Params) > 0 && d.Params[len(d.Params)-1].Variadic
}

// functionValueType is the function type of a callable value.
func functionValueType(v any) TypeRef {
	switch x := v.(type) {
	case *closureValue:
		if isVariadicDecl(x.decl) {
			return anyT
		}
		return x.typeRef
	case *boundMethod:
		d := x.fn.decl
		if len(d.TypeParams) > 0 || isVariadicDecl(d) {
			return anyT
		}
		owner := theInterpreter.structByKey(x.fn.pkg, d.OwnerStruct)
		bindings := map[string]TypeRef{}
		if owner != nil {
			for i, p := range owner.TypeParams {
				if i < len(x.receiver.typeArgs) {
					bindings[p.Name] = x.receiver.typeArgs[i]
				}
			}
		}
		args := make([]TypeRef, 0, len(d.Params)+1)
		for _, p := range d.Params {
			args = append(args, substituteType(p.Type, bindings))
		}
		args = append(args, substituteType(d.ReturnType, bindings))
		return typeRefN("func", args)
	case *userFunction:
		d := x.decl
		if len(d.TypeParams) > 0 || isVariadicDecl(d) {
			return anyT
		}
		args := make([]TypeRef, 0, len(d.Params)+1)
		for _, p := range d.Params {
			args = append(args, p.Type)
		}
		args = append(args, d.ReturnType)
		return typeRefN("func", args)
	case *bcCallable:
		d := x.function.decl
		if len(d.TypeParams) > 0 || isVariadicDecl(d) {
			return anyT
		}
		args := make([]TypeRef, 0, len(d.Params)+1)
		for _, p := range d.Params {
			args = append(args, p.Type)
		}
		args = append(args, d.ReturnType)
		return typeRefN("func", args)
	}
	return anyT
}

// sameFuncType is function-type equality tolerating UNKNOWN entries.
func sameFuncType(a, b TypeRef) bool {
	if a.Name != "func" || b.Name != "func" {
		return a.equal(b)
	}
	if len(a.Args) != len(b.Args) {
		return false
	}
	for i := range a.Args {
		x, y := a.Args[i], b.Args[i]
		if !x.equal(y) && !x.equal(unknownT) && !y.equal(unknownT) {
			return false
		}
	}
	return true
}

// enumPatternShape recognizes possible enum-case patterns in switch cases.
func enumPatternShape(expr any) (kind string, enumExpr any, caseName string, elements []any, ok bool) {
	if c, isCall := expr.(*Call); isCall {
		elems := make([]any, 0, len(c.Args))
		for _, a := range c.Args {
			elems = append(elems, a.Expr)
		}
		if m, isMember := c.Callee.(*Member); isMember {
			if _, dok := dottedExpressionName(m.Obj); dok {
				return "qualified", m.Obj, m.Name, elems, true
			}
		}
		if n, isName := c.Callee.(*Name); isName {
			return "bare", nil, n.Name, elems, true
		}
	}
	return "", nil, "", nil, false
}

// builtinMethodSignature is the type-level contract for intrinsic methods.
func builtinMethodSignature(typ TypeRef, name string) *methodSig {
	base := typ.nonnull()
	var t TypeRef
	if len(base.Args) > 0 {
		t = base.Args[0]
	} else {
		t = unknownT
	}
	switch base.Name {
	case "bool", "byte", "int", "float", "char", "string", "list", "map", "stack", "func":
		if name == "string" {
			return &methodSig{returnType: typeRef("string")}
		}
		if name == "equals" {
			return &methodSig{params: []TypeRef{anyT}, returnType: typeRef("bool")}
		}
	}
	switch base.Name {
	case "byte", "int", "float":
		if name == "abs" {
			return &methodSig{returnType: base}
		}
	}
	switch base.Name {
	case "byte", "int", "float", "char", "string":
		if name == "compare" {
			return &methodSig{params: []TypeRef{anyT}, returnType: typeRef("int")}
		}
		if name == "hash" {
			return &methodSig{returnType: typeRef("int")}
		}
	}
	if base.Name == "bool" && name == "hash" {
		m := &methodSig{returnType: typeRef("int")}
		return m
	}
	if base.Name == "string" {
		table := map[string]*methodSig{
			"len":        {returnType: typeRef("int")},
			"isEmpty":    {returnType: typeRef("bool")},
			"contains":   {params: []TypeRef{typeRef("string")}, returnType: typeRef("bool")},
			"startsWith": {params: []TypeRef{typeRef("string")}, returnType: typeRef("bool")},
			"endsWith":   {params: []TypeRef{typeRef("string")}, returnType: typeRef("bool")},
			"indexOf":    {params: []TypeRef{typeRef("string")}, returnType: typeRef("int")},
			"byteLength": {returnType: typeRef("int")},
			"charAt":     {params: []TypeRef{typeRef("int")}, returnType: typeRef("char")},
			"substring":  {params: []TypeRef{typeRef("int"), typeRef("int")}, returnType: typeRef("string")},
			"toUpper":    {returnType: typeRef("string")},
			"toLower":    {returnType: typeRef("string")},
			"trim":       {returnType: typeRef("string")},
			"split":      {params: []TypeRef{typeRef("string")}, returnType: typeRef("list", typeRef("string"))},
			"iterator":   {returnType: typeRef("list", typeRef("char"))},
		}
		if m, ok := table[name]; ok {
			return m
		}
		return nil
	}
	if base.Name == "list" {
		nullableT := t
		nullableT.Nullable = true
		table := map[string]*methodSig{
			"len":      {returnType: typeRef("int")},
			"isEmpty":  {returnType: typeRef("bool")},
			"contains": {params: []TypeRef{t}, returnType: typeRef("bool")},
			"iterator": {returnType: typeRef("list", t)},
			"map":      {params: []TypeRef{typeRef("func", t, unknownT)}, returnType: typeRef("list", unknownT)},
			"filter":   {params: []TypeRef{typeRef("func", t, typeRef("bool"))}, returnType: typeRef("list", t)},
			"reduce":   {params: []TypeRef{typeRef("func", t, t, t)}, returnType: t},
			"fold":     {params: []TypeRef{unknownT, typeRef("func", unknownT, t, unknownT)}, returnType: unknownT},
			"find":     {params: []TypeRef{typeRef("func", t, typeRef("bool"))}, returnType: nullableT},
			"any":      {params: []TypeRef{typeRef("func", t, typeRef("bool"))}, returnType: typeRef("bool")},
			"all":      {params: []TypeRef{typeRef("func", t, typeRef("bool"))}, returnType: typeRef("bool")},
			"first":    {returnType: nullableT},
			"last":     {returnType: nullableT},
			"reverse":  {returnType: typeRef("list", t)},
			"sort":     {params: []TypeRef{typeRef("func", t, t, typeRef("int"))}, returnType: typeRef("list", t)},
		}
		if m, ok := table[name]; ok {
			return m
		}
		return nil
	}
	if base.Name == "map" {
		key := unknownT
		if len(base.Args) >= 1 {
			key = base.Args[0]
		}
		table := map[string]*methodSig{
			"len":      {returnType: typeRef("int")},
			"isEmpty":  {returnType: typeRef("bool")},
			"contains": {params: []TypeRef{key}, returnType: typeRef("bool")},
			"iterator": {returnType: typeRef("list", key)},
		}
		if m, ok := table[name]; ok {
			return m
		}
		return nil
	}
	if base.Name == "stack" {
		table := map[string]*methodSig{
			"len":      {returnType: typeRef("int")},
			"isEmpty":  {returnType: typeRef("bool")},
			"contains": {params: []TypeRef{t}, returnType: typeRef("bool")},
			"iterator": {returnType: typeRef("list", t)},
			"push":     {params: []TypeRef{t}, returnType: voidT},
			"pop":      {returnType: t},
			"peek":     {returnType: t},
		}
		if m, ok := table[name]; ok {
			return m
		}
		return nil
	}
	return nil
}

// coreTraitDecls builds the predefined core traits.
func coreTraitDecls() map[string]*TraitDecl {
	p := SourcePos{File: "<solvik-core>", Line: 1, Col: 1}
	t := typeRef("T")
	method := func(name string, params []TypeRef, result TypeRef, mutating bool) *FunctionDecl {
		ps := make([]Param, len(params))
		for i, pt := range params {
			ps[i] = Param{Name: fmtSPrintf("p%d", i), Type: pt}
		}
		return &FunctionDecl{Name: name, Params: ps, ReturnType: result, Pos: p, Public: true, Mutating: mutating}
	}
	traits := map[string]*TraitDecl{
		"Stringable": {Name: "Stringable", Methods: []*FunctionDecl{method("string", nil, typeRef("string"), false)}, Pos: p},
		"Equatable":  {Name: "Equatable", Methods: []*FunctionDecl{method("equals", []TypeRef{anyT}, typeRef("bool"), false)}, Pos: p},
		"Comparable": {Name: "Comparable", Methods: []*FunctionDecl{method("compare", []TypeRef{anyT}, typeRef("int"), false)}, Pos: p},
		"Hashable":   {Name: "Hashable", Methods: []*FunctionDecl{method("hash", nil, typeRef("int"), false)}, Pos: p},
		"Countable":  {Name: "Countable", Methods: []*FunctionDecl{method("len", nil, typeRef("int"), false)}, Pos: p},
		"Iterable":   {Name: "Iterable", Methods: []*FunctionDecl{method("iterator", nil, typeRef("list", t), false)}, Pos: p, TypeParams: []TypeParam{{Name: "T"}}},
		"Collection": {Name: "Collection", Methods: []*FunctionDecl{
			method("len", nil, typeRef("int"), false),
			method("isEmpty", nil, typeRef("bool"), false),
			method("contains", []TypeRef{t}, typeRef("bool"), false),
			method("iterator", nil, typeRef("list", t), false),
		}, Pos: p, TypeParams: []TypeParam{{Name: "T"}}},
	}
	return traits
}
