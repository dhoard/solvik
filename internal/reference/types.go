package reference

// Type helpers shared by the parser, validator, and interpreter. These mirror
// the Python reference functions of the same names.

var builtinTypeNames = map[string]bool{
	"bool": true, "byte": true, "int": true, "float": true, "char": true, "string": true,
	"list": true, "map": true, "stack": true, "any": true, "void": true, "exception": true,
	"regex": true, "func": true, "null": true, "<unknown>": true,
}

var coreTraitNames = map[string]bool{
	"Stringable": true, "Equatable": true, "Comparable": true, "Hashable": true,
	"Countable": true, "Iterable": true, "Collection": true,
}

// splitTypeName splits a possibly qualified type name into (package, local).
func splitTypeName(name string) (string, string) {
	for i := len(name) - 1; i >= 0; i-- {
		if name[i] == '.' {
			return name[:i], name[i+1:]
		}
	}
	return "", name
}

// typeKey is the canonical (package, local) identity key for a type name.
func typeKey(name, pkg string) pkgKey {
	p, local := splitTypeName(name)
	if p == "" {
		return pkgKey{pkg, local}
	}
	return pkgKey{p, local}
}

// dottedExpressionName returns the dotted name of a Name or member chain.
func dottedExpressionName(expr any) (string, bool) {
	switch e := expr.(type) {
	case *Name:
		return e.Name, true
	case *Member:
		if base, ok := dottedExpressionName(e.Obj); ok {
			return base + "." + e.Name, true
		}
	}
	return "", false
}

func canonicalTypeName(name, pkg string) string {
	if name == "" || containsByteStr(name, '.') {
		return name
	}
	return pkg + "." + name
}

func containsByteStr(s string, c byte) bool {
	for i := 0; i < len(s); i++ {
		if s[i] == c {
			return true
		}
	}
	return false
}

// substituteType replaces type-parameter names with their bindings.
func substituteType(typ TypeRef, bindings map[string]TypeRef) TypeRef {
	if replacement, ok := bindings[typ.Name]; ok && len(typ.Args) == 0 {
		if typ.Nullable && !replacement.Nullable {
			replacement.Nullable = true
			return replacement
		}
		return replacement
	}
	args := make([]TypeRef, len(typ.Args))
	for i, a := range typ.Args {
		args[i] = substituteType(a, bindings)
	}
	return TypeRef{Name: typ.Name, Args: args, Nullable: typ.Nullable}
}

// bindTypePattern unifies a declared type pattern with an actual type,
// binding type parameters. Returns whether the shapes are compatible.
func bindTypePattern(pattern, actual TypeRef, variables map[string]bool, bindings map[string]TypeRef) bool {
	if variables[pattern.Name] && len(pattern.Args) == 0 {
		previous, seen := bindings[pattern.Name]
		if actual.Name == "null" {
			// A null value carries no type evidence.
			return true
		}
		if !seen || previous.equal(unknownT) {
			if pattern.Nullable {
				bindings[pattern.Name] = actual.nonnull()
			} else {
				bindings[pattern.Name] = actual
			}
			return true
		}
		return actual.equal(unknownT) || previous.equal(actual) || previous.equal(actual.nonnull())
	}
	if pattern.Name != actual.Name {
		return actual.equal(unknownT)
	}
	if len(pattern.Args) > 0 && len(pattern.Args) != len(actual.Args) {
		return false
	}
	for i := range pattern.Args {
		if !bindTypePattern(pattern.Args[i], actual.Args[i], variables, bindings) {
			return false
		}
	}
	return true
}

// freeNames returns all names mentioned by a type, including generic args.
func freeNames(t TypeRef) map[string]bool {
	names := map[string]bool{t.Name: true}
	for _, a := range t.Args {
		for n := range freeNames(a) {
			names[n] = true
		}
	}
	return names
}

// ---- canonicalization pass (Phase 5 identity) ----

func canonicalizeTypeRef(t *TypeRef, pkg string, params map[string]bool) {
	if t.Name != "" && !containsByteStr(t.Name, '.') && !params[t.Name] &&
		!builtinTypeNames[t.Name] && !coreTraitNames[t.Name] {
		t.Name = pkg + "." + t.Name
	}
	for i := range t.Args {
		canonicalizeTypeRef(&t.Args[i], pkg, params)
	}
}

func canonicalizeExpr(e any, pkg string, params map[string]bool) {
	switch x := e.(type) {
	case *Literal:
	case *Name:
		for i := range x.TypeArgs {
			canonicalizeTypeRef(&x.TypeArgs[i], pkg, params)
		}
	case *Unary:
		canonicalizeExpr(x.Expr, pkg, params)
	case *Binary:
		canonicalizeExpr(x.Left, pkg, params)
		canonicalizeExpr(x.Right, pkg, params)
	case *Assign:
		canonicalizeExpr(x.Target, pkg, params)
		canonicalizeExpr(x.Value, pkg, params)
	case *Call:
		for i := range x.TypeArgs {
			canonicalizeTypeRef(&x.TypeArgs[i], pkg, params)
		}
		for _, a := range x.Args {
			canonicalizeExpr(a.Expr, pkg, params)
		}
		canonicalizeExpr(x.Callee, pkg, params)
	case *Member:
		canonicalizeExpr(x.Obj, pkg, params)
	case *Index:
		canonicalizeExpr(x.Obj, pkg, params)
		canonicalizeExpr(x.Index, pkg, params)
	case *ListExpr:
		for _, item := range x.Items {
			canonicalizeExpr(item, pkg, params)
		}
	case *MapExpr:
		for _, kv := range x.Items {
			canonicalizeExpr(kv.Key, pkg, params)
			canonicalizeExpr(kv.Value, pkg, params)
		}
	case *StructExpr:
		if x.TypeName != "" && !containsByteStr(x.TypeName, '.') {
			x.TypeName = pkg + "." + x.TypeName
		}
		for i := range x.TypeArgs {
			canonicalizeTypeRef(&x.TypeArgs[i], pkg, params)
		}
		for i := range x.Fields {
			canonicalizeExpr(x.Fields[i].Value, pkg, params)
		}
	case *FuncExpr:
		fp := map[string]bool{}
		for n := range params {
			fp[n] = true
		}
		for i := range x.Params {
			canonicalizeTypeRef(&x.Params[i].Type, pkg, fp)
		}
		canonicalizeTypeRef(&x.ReturnType, pkg, fp)
		canonicalizeBlock(x.Body, pkg, fp)
	}
}

func canonicalizeBlock(b *Block, pkg string, params map[string]bool) {
	for _, s := range b.Statements {
		canonicalizeStatement(s, pkg, params)
	}
}

func canonicalizeStatement(s any, pkg string, params map[string]bool) {
	switch x := s.(type) {
	case *VarDecl:
		canonicalizeTypeRef(&x.Type, pkg, params)
		canonicalizeExpr(x.Value, pkg, params)
	case *ExprStmt:
		canonicalizeExpr(x.Expr, pkg, params)
	case *IfStmt:
		canonicalizeExpr(x.Condition, pkg, params)
		canonicalizeBlock(x.ThenBlock, pkg, params)
		if b, ok := x.ElseBranch.(*Block); ok {
			canonicalizeBlock(b, pkg, params)
		} else if ei, ok := x.ElseBranch.(*IfStmt); ok {
			canonicalizeStatement(ei, pkg, params)
		}
	case *WhileStmt:
		canonicalizeExpr(x.Condition, pkg, params)
		canonicalizeBlock(x.Body, pkg, params)
	case *ForStmt:
		canonicalizeExpr(x.Iterable, pkg, params)
		canonicalizeBlock(x.Body, pkg, params)
	case *SwitchStmt:
		canonicalizeExpr(x.Value, pkg, params)
		for i := range x.Cases {
			if x.Cases[i].Expr != nil {
				canonicalizeExpr(x.Cases[i].Expr, pkg, params)
			}
			canonicalizeBlock(x.Cases[i].Body, pkg, params)
		}
	case *TryStmt:
		canonicalizeBlock(x.TryBlock, pkg, params)
		if x.CatchBlock != nil {
			if x.CatchType != nil {
				canonicalizeTypeRef(x.CatchType, pkg, params)
			}
			canonicalizeBlock(x.CatchBlock, pkg, params)
		}
		if x.FinallyBlk != nil {
			canonicalizeBlock(x.FinallyBlk, pkg, params)
		}
	case *ThrowStmt:
		canonicalizeExpr(x.Value, pkg, params)
	case *ReturnStmt:
		if x.Value != nil {
			canonicalizeExpr(x.Value, pkg, params)
		}
	case *Block:
		canonicalizeBlock(x, pkg, params)
	}
}

func canonicalizeFunction(f *FunctionDecl, pkg string, params map[string]bool) {
	for i := range f.Params {
		canonicalizeTypeRef(&f.Params[i].Type, pkg, params)
	}
	canonicalizeTypeRef(&f.ReturnType, pkg, params)
	if f.Body != nil {
		canonicalizeBlock(f.Body, pkg, params)
	}
}

// canonicalizeProgram rewrites every type reference to canonical dotted form.
func canonicalizeProgram(p *Program) {
	pkg := p.Package
	for _, d := range p.Declarations {
		switch decl := d.(type) {
		case *FunctionDecl:
			params := typeParamNames(decl.TypeParams)
			canonicalizeFunction(decl, pkg, params)
		case *StructDecl:
			params := typeParamNames(decl.TypeParams)
			for i := range decl.Fields {
				canonicalizeTypeRef(&decl.Fields[i].Type, pkg, params)
			}
			for _, m := range decl.Methods {
				mp := typeParamNames(m.TypeParams)
				for n := range params {
					mp[n] = true
				}
				canonicalizeFunction(m, pkg, mp)
			}
		case *EnumDecl:
			params := typeParamNames(decl.TypeParams)
			for i := range decl.Members {
				for j := range decl.Members[i].PayloadTypes {
					canonicalizeTypeRef(&decl.Members[i].PayloadTypes[j], pkg, params)
				}
			}
		case *TraitDecl:
			params := typeParamNames(decl.TypeParams)
			for _, m := range decl.Methods {
				canonicalizeFunction(m, pkg, params)
			}
		}
	}
}

func typeParamNames(ps []TypeParam) map[string]bool {
	out := map[string]bool{}
	for _, p := range ps {
		out[p.Name] = true
	}
	return out
}

// ---- method signatures and trait satisfaction ----

type methodSig struct {
	params     []TypeRef
	returnType TypeRef
	mutating   bool
}

func sig(params []TypeRef, ret TypeRef) methodSig { return methodSig{params: params, returnType: ret} }

func sameSignature(a, b methodSig) bool {
	if a.mutating != b.mutating || len(a.params) != len(b.params) {
		return false
	}
	sameType := func(x, y TypeRef) bool {
		if x.equal(unknownT) || y.equal(unknownT) {
			return true
		}
		if x.Name == "any" || y.Name == "any" {
			return true
		}
		return x.equal(y)
	}
	if !sameType(a.returnType, b.returnType) {
		return false
	}
	for i := range a.params {
		if !sameType(a.params[i], b.params[i]) {
			return false
		}
	}
	return true
}

// unifyTraitArgument infers not-yet-bound trait type arguments from a concrete
// signature.
func unifyTraitArgument(need, have TypeRef, variables map[string]bool, found map[string]TypeRef) {
	if variables[need.Name] && len(need.Args) == 0 {
		if !have.equal(unknownT) {
			if _, seen := found[need.Name]; !seen {
				if need.Nullable {
					found[need.Name] = have.nonnull()
				} else {
					found[need.Name] = have
				}
			}
		}
		return
	}
	if need.Name == have.Name && len(need.Args) == len(have.Args) {
		for i := range need.Args {
			unifyTraitArgument(need.Args[i], have.Args[i], variables, found)
		}
	}
}

// traitSatisfaction is the central structural trait satisfaction check.
// methodSig resolves methods for the actual type. variables names
// function-level type parameters not yet bound; they may be solved from the
// actual type's method signatures. Returns (ok, solved).
func traitSatisfaction(actual, expected TypeRef, trait *TraitDecl,
	resolveMethod func(TypeRef, string) *methodSig, variables map[string]bool) (bool, map[string]TypeRef) {
	if trait == nil {
		return false, nil
	}
	if len(trait.TypeParams) > 0 && len(expected.Args) != len(trait.TypeParams) {
		return false, nil
	}
	bindings := map[string]TypeRef{}
	for i := range trait.TypeParams {
		if i < len(expected.Args) {
			bindings[trait.TypeParams[i].Name] = expected.Args[i]
		}
	}
	have := map[string]*methodSig{}
	for _, need := range trait.Methods {
		m := resolveMethod(actual, need.Name)
		if m == nil {
			return false, nil
		}
		have[need.Name] = m
	}
	solved := map[string]TypeRef{}
	if len(variables) > 0 {
		for _, need := range trait.Methods {
			h := have[need.Name]
			for i := range need.Params {
				if i < len(h.params) {
					unifyTraitArgument(substituteType(need.Params[i].Type, bindings), h.params[i], variables, solved)
				}
			}
			unifyTraitArgument(substituteType(need.ReturnType, bindings), h.returnType, variables, solved)
		}
	}
	resolve := func(t TypeRef) TypeRef {
		return substituteType(substituteType(t, solved), bindings)
	}
	for _, need := range trait.Methods {
		needParams := make([]TypeRef, len(need.Params))
		for i := range need.Params {
			needParams[i] = resolve(need.Params[i].Type)
		}
		needSig := methodSig{params: needParams, returnType: resolve(need.ReturnType), mutating: need.Mutating}
		if !sameSignature(*have[need.Name], needSig) {
			return false, nil
		}
	}
	return true, solved
}

func bindingOrUnknown(m map[string]TypeRef, name string) TypeRef {
	if t, ok := m[name]; ok {
		return t
	}
	return unknownT
}
