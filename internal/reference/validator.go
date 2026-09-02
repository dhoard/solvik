package reference

// validator is the static checker, mirroring the Python SemanticValidator.
// It runs before execution and rejects statically invalid programs.

type staticBinding struct {
	typ     TypeRef
	mutable bool
}

type validator struct {
	interp            *Interpreter
	program           *Program
	pkg               string
	structs           map[string]*StructDecl
	traits            map[string]*TraitDecl
	enums             map[string]*EnumDecl
	functions         map[string]*FunctionDecl
	currentFunction   *FunctionDecl
	currentStruct     *StructDecl
	currentTypeParams map[string]bool
	scopes            []map[string]staticBinding
	loopDepth         int
}

func newValidator(in *Interpreter, p *Program) *validator {
	v := &validator{
		interp:            in,
		program:           p,
		pkg:               p.Package,
		structs:           map[string]*StructDecl{},
		traits:            coreTraitDecls(),
		enums:             map[string]*EnumDecl{},
		functions:         map[string]*FunctionDecl{},
		currentTypeParams: map[string]bool{},
	}
	for key, d := range in.structs {
		v.structs[key[0]+"."+key[1]] = d
	}
	for key, d := range in.traits {
		v.traits[key[0]+"."+key[1]] = d
	}
	for key, et := range in.enums {
		v.enums[key[0]+"."+key[1]] = et.decl
	}
	for _, d := range p.Declarations {
		switch decl := d.(type) {
		case *StructDecl:
			v.structs[p.Package+"."+decl.Name] = decl
		case *TraitDecl:
			v.traits[p.Package+"."+decl.Name] = decl
		case *EnumDecl:
			v.enums[p.Package+"."+decl.Name] = decl
		}
	}
	for pkg, ns := range in.packages {
		for name, val := range ns.values {
			if uf, ok := val.(*userFunction); ok {
				v.functions[pkg+"."+name] = uf.decl
			}
		}
	}
	return v
}

func (v *validator) canonical(name string) string {
	if name == "" || containsByteStr(name, '.') {
		return name
	}
	return v.pkg + "." + name
}

func (v *validator) structOf(name string) *StructDecl { return v.structs[v.canonical(name)] }
func (v *validator) enumOf(name string) *EnumDecl     { return v.enums[v.canonical(name)] }
func (v *validator) traitOf(name string) *TraitDecl {
	if t, ok := v.traits[v.canonical(name)]; ok {
		return t
	}
	return v.traits[name]
}
func (v *validator) sameTypeName(a, b string) bool { return v.canonical(a) == v.canonical(b) }

func (v *validator) error(code string, pos SourcePos, span int, format string, args ...any) {
	panic(diagErr(code, pos, span, format, args...))
}

func (v *validator) toLineEnd(pos SourcePos) int {
	line := ""
	if src, ok := sourceRegistry[pos.File]; ok {
		lines := splitLines(src)
		if pos.Line-1 < len(lines) {
			line = lines[pos.Line-1]
		}
	}
	if pos.Col <= len(line) {
		return len(line) - pos.Col + 1
	}
	return 1
}

func literalSpan(e any) int {
	if lit, ok := e.(*Literal); ok {
		switch lit.LiteralKind {
		case "string", "char":
			return len([]rune(lit.Value.(string))) + 2
		case "null":
			return 4
		}
		return len(fmtGeneric(lit.Value))
	}
	return 1
}

// validate is the entry point; it panics with *DiagnosticError on the first
// problem, matching the Python reference.
func validateProgram(in *Interpreter, p *Program) {
	v := newValidator(in, p)
	seenTypes := map[string]any{}
	for _, d := range p.Declarations {
		switch decl := d.(type) {
		case *FunctionDecl:
			if _, dup := v.functions[decl.Name]; dup {
				v.error("C090", decl.Pos, v.toLineEnd(decl.Pos), "duplicate function '%s'", decl.Name)
			}
			v.functions[decl.Name] = decl
			if other, has := seenTypes[decl.Name]; has {
				v.dupName(decl.Pos, decl.Name, other, "function")
			} else {
				seenTypes[decl.Name] = decl
			}
		case *StructDecl:
			if other, has := seenTypes[decl.Name]; has {
				v.dupName(decl.Pos, decl.Name, other, "struct")
			} else {
				seenTypes[decl.Name] = decl
			}
			seenFields := map[string]bool{}
			for i := range decl.Fields {
				f := &decl.Fields[i]
				if seenFields[f.Name] {
					v.error("C091", fieldPos(f), 1, "duplicate field '%s' in struct '%s'", f.Name, decl.Name)
				}
				seenFields[f.Name] = true
			}
		case *TraitDecl, *EnumDecl:
			name := decl.(interface{ GetName() string }).GetName()
			if other, has := seenTypes[name]; has {
				v.dupName(declPos(decl), name, other, "declaration")
			} else {
				seenTypes[name] = decl
			}
		}
	}
	for _, d := range p.Declarations {
		if s, ok := d.(*StructDecl); ok {
			v.checkStructRecursion(s, []string{s.Name})
		}
	}
	for _, d := range p.Declarations {
		switch decl := d.(type) {
		case *FunctionDecl:
			v.validateFunction(decl, nil)
		case *StructDecl:
			v.checkConstraints(decl.TypeParams, decl.Pos)
			for _, m := range decl.Methods {
				v.validateFunction(m, decl)
			}
			old := v.currentTypeParams
			v.currentTypeParams = typeParamNames(decl.TypeParams)
			for i := range decl.Fields {
				v.checkAnnotationType(decl.Fields[i].Type, fieldPos(&decl.Fields[i]))
			}
			v.currentTypeParams = old
		case *TraitDecl:
			v.checkConstraints(decl.TypeParams, decl.Pos)
			for _, m := range decl.Methods {
				v.validateParams(m)
			}
		case *EnumDecl:
			v.validateEnumDecl(decl)
		}
	}
}

func (d *StructDecl) GetName() string { return d.Name }
func (d *TraitDecl) GetName() string  { return d.Name }
func (d *EnumDecl) GetName() string   { return d.Name }

func (v *validator) dupName(pos SourcePos, name string, other any, kind string) {
	otherKind := "declaration"
	switch other.(type) {
	case *FunctionDecl:
		otherKind = "function"
	case *StructDecl:
		otherKind = "struct"
	case *TraitDecl:
		otherKind = "trait"
	case *EnumDecl:
		otherKind = "enum"
	}
	v.error("C109", pos, v.toLineEnd(pos), "'%s' is already declared as a %s; top-level names must be unique within a package (%s)", name, otherKind, kind)
}

func fieldPos(f *FieldDecl) SourcePos {
	if f.Pos != nil {
		return *f.Pos
	}
	return SourcePos{}
}

func (v *validator) checkStructRecursion(decl *StructDecl, path []string) {
	for _, field := range decl.Fields {
		if field.Type.Nullable {
			continue
		}
		target := v.structOf(field.Type.Name)
		if target == nil {
			continue
		}
		cycle := false
		for _, p := range path {
			if p == target.Name {
				cycle = true
				break
			}
		}
		if cycle {
			chain := ""
			for i, p := range path {
				if i > 0 {
					chain += " -> "
				}
				chain += p
			}
			chain += " -> " + target.Name
			v.error("C097", fieldPos(&field), 1, "recursive struct field '%s' of type '%s' must be nullable or indirect (cycle: %s)", field.Name, field.Type.Name, chain)
		}
		v.checkStructRecursion(target, append(path, target.Name))
	}
}

func (v *validator) checkConstraints(typeParams []TypeParam, pos SourcePos) {
	for _, tp := range typeParams {
		for _, constraint := range tp.Constraints {
			trait := v.traitOf(constraint.Name)
			if trait == nil {
				v.error("C110", pos, v.toLineEnd(pos), "unknown type '%s' in constraint", constraint.Name)
				continue
			}
			v.checkTypeVisibility(trait, constraint.Name, pos, "trait")
			if len(constraint.Args) != len(trait.TypeParams) {
				kind := "generic trait"
				if len(trait.TypeParams) == 0 {
					kind = "non-generic trait"
				}
				v.error("C096", pos, v.toLineEnd(pos), "constraint '%s' is a %s; it requires exactly %d type argument(s)", constraint.Name, kind, len(trait.TypeParams))
			}
		}
	}
}

func (v *validator) checkTypeVisibility(decl any, name string, pos SourcePos, kind string) {
	pkg, _ := splitTypeName(v.canonical(name))
	public := false
	switch d := decl.(type) {
	case *StructDecl:
		public = d.Public
	case *TraitDecl:
		public = d.Public
	case *EnumDecl:
		public = d.Public
	}
	if pkg != "" && pkg != v.pkg && !public {
		v.error("C120", pos, v.toLineEnd(pos), "%s '%s' is private; declare it 'pub' to use it outside package '%s'", kind, v.canonical(name), pkg)
	}
}

func (v *validator) validateParams(f *FunctionDecl) {
	seen := map[string]bool{}
	for _, p := range f.Params {
		if seen[p.Name] {
			v.error("C092", *p.Pos, 1, "duplicate parameter '%s' in function '%s'", p.Name, f.Name)
		}
		seen[p.Name] = true
	}
}

func (v *validator) validateFunction(f *FunctionDecl, owner *StructDecl) {
	v.validateParams(f)
	if f.Name == "main" && owner == nil {
		if len(f.Params) > 0 {
			v.error("C123", f.Pos, v.toLineEnd(f.Pos), "entry function 'main' must take no parameters")
		}
		if f.ReturnType.Name != "int" && f.ReturnType.Name != "void" {
			v.error("C124", f.Pos, v.toLineEnd(f.Pos), "entry function 'main' must return int or nothing, not %s", f.ReturnType)
		}
	}
	if owner != nil {
		ownerParams := typeParamNames(owner.TypeParams)
		for _, tp := range f.TypeParams {
			if ownerParams[tp.Name] {
				v.error("C099", f.Pos, v.toLineEnd(f.Pos), "type parameter '%s' of method '%s' shadows a type parameter of struct '%s'; use a distinct name", tp.Name, f.Name, owner.Name)
			}
		}
	}
	v.checkConstraints(f.TypeParams, f.Pos)
	oldFunction, oldStruct, oldParams, oldScopes := v.currentFunction, v.currentStruct, v.currentTypeParams, v.scopes
	v.currentFunction, v.currentStruct = f, owner
	var ownerParams []TypeParam
	if owner != nil {
		ownerParams = owner.TypeParams
	}
	declared := append(append([]TypeParam{}, ownerParams...), f.TypeParams...)
	v.currentTypeParams = typeParamNames(declared)
	for i := range f.Params {
		pos := SourcePos{Line: 1, Col: 1}
		if f.Params[i].Pos != nil {
			pos = *f.Params[i].Pos
		}
		v.checkAnnotationType(f.Params[i].Type, pos)
	}
	if f.ReturnType.Name != "void" {
		v.checkAnnotationType(f.ReturnType, f.Pos)
	}
	if f.Body == nil {
		v.currentFunction, v.currentStruct, v.currentTypeParams, v.scopes = oldFunction, oldStruct, oldParams, oldScopes
		return
	}
	params := map[string]staticBinding{}
	for _, p := range f.Params {
		if p.Variadic {
			params[p.Name] = staticBinding{typ: typeRef("list", p.Type)}
		} else {
			params[p.Name] = staticBinding{typ: p.Type}
		}
	}
	v.scopes = []map[string]staticBinding{params}
	v.checkBlock(f.Body, false)
	if f.ReturnType.Name != "void" && !v.blockDefinitelyReturns(f.Body) {
		v.error("C111", f.Pos, v.toLineEnd(f.Pos), "function '%s' declares return type %s but not every path returns a value", f.Name, f.ReturnType)
	}
	v.currentFunction, v.currentStruct, v.currentTypeParams, v.scopes = oldFunction, oldStruct, oldParams, oldScopes
}

func (v *validator) checkAnnotationType(t TypeRef, pos SourcePos) {
	for i := range t.Args {
		if t.Args[i].Name != "void" {
			v.checkAnnotationType(t.Args[i], pos)
		}
	}
	if t.Name == "func" {
		if len(t.Args) == 0 {
			v.error("C104", pos, v.toLineEnd(pos), "function types require at least a return type")
		}
		for _, arg := range t.Args[:len(t.Args)-1] {
			if arg.Name == "void" {
				v.error("C104", pos, v.toLineEnd(pos), "void is only allowed as the return element of a function type")
			}
		}
		return
	}
	if t.Name == "void" {
		v.error("C122", pos, v.toLineEnd(pos), "void is not a value type; it may appear only as the return element of a function type")
	}
	if v.currentTypeParams[t.Name] {
		return
	}
	switch t.Name {
	case "any", "exception", "regex", "bool", "byte", "int", "float", "char", "string", "<unknown>":
		return
	}
	var expectedArity *int
	switch t.Name {
	case "list", "stack":
		n := 1
		expectedArity = &n
	case "map":
		n := 2
		expectedArity = &n
	default:
		if s := v.structOf(t.Name); s != nil {
			v.checkTypeVisibility(s, t.Name, pos, "struct")
			n := len(s.TypeParams)
			expectedArity = &n
		} else if e := v.enumOf(t.Name); e != nil {
			v.checkTypeVisibility(e, t.Name, pos, "enum")
			n := len(e.TypeParams)
			expectedArity = &n
		}
	}
	if expectedArity == nil {
		known := t.Name == "bool" || t.Name == "byte" || t.Name == "int" || t.Name == "float" || t.Name == "char" || t.Name == "string" || t.Name == "<unknown>"
		if !known {
			if tr := v.traitOf(t.Name); tr != nil {
				v.checkTypeVisibility(tr, t.Name, pos, "trait")
				known = true
			}
		}
		if !known {
			v.error("C110", pos, v.toLineEnd(pos), "unknown type '%s'", t.Name)
		}
		return
	}
	if len(t.Args) != *expectedArity {
		kind := "generic type"
		if t.Name != "list" && t.Name != "stack" && t.Name != "map" {
			if v.enumOf(t.Name) != nil {
				kind = "enum"
			} else {
				kind = "struct"
			}
		}
		found := "none"
		if len(t.Args) > 0 {
			found = fmtGeneric(len(t.Args))
		}
		v.error("C096", pos, v.toLineEnd(pos), "%s '%s' requires %d type argument(s), found %s", kind, t.Name, *expectedArity, found)
	}
}

func (v *validator) validateEnumDecl(decl *EnumDecl) {
	v.checkConstraints(decl.TypeParams, decl.Pos)
	seen := map[string]bool{}
	hasPayload := false
	for _, m := range decl.Members {
		if len(m.PayloadTypes) > 0 {
			hasPayload = true
		}
	}
	old := v.currentTypeParams
	v.currentTypeParams = typeParamNames(decl.TypeParams)
	for _, m := range decl.Members {
		if seen[m.Name] {
			v.error("C091", decl.Pos, v.toLineEnd(decl.Pos), "duplicate case '%s' in enum '%s'", m.Name, decl.Name)
		}
		seen[m.Name] = true
		if hasPayload && m.Value != nil {
			v.error("C107", decl.Pos, v.toLineEnd(decl.Pos), "enum '%s' has payload cases, so case '%s' cannot declare an integer value; algebraic enums use case names only", decl.Name, m.Name)
		}
		for _, pt := range m.PayloadTypes {
			v.checkAnnotationType(pt, decl.Pos)
		}
	}
	v.currentTypeParams = old
}

func (v *validator) checkBlock(b *Block, newScope bool) {
	if newScope {
		v.scopes = append(v.scopes, map[string]staticBinding{})
	}
	defer func() {
		if newScope {
			v.scopes = v.scopes[:len(v.scopes)-1]
		}
	}()
	terminated := false
	for _, s := range b.Statements {
		if terminated {
			v.error("C112", stmtPos(s), v.toLineEnd(stmtPos(s)), "unreachable statement: execution cannot continue past an earlier return, throw, break, or continue")
		}
		v.checkStatement(s)
		if !terminated && v.statementTerminates(s) {
			terminated = true
		}
	}
}

func declPos(d any) SourcePos {
	switch x := d.(type) {
	case *FunctionDecl:
		return x.Pos
	case *StructDecl:
		return x.Pos
	case *TraitDecl:
		return x.Pos
	case *EnumDecl:
		return x.Pos
	}
	return SourcePos{}
}

func stmtPos(s any) SourcePos {
	switch x := s.(type) {
	case *Block:
		return x.Pos
	case *VarDecl:
		return x.Pos
	case *ExprStmt:
		return x.Pos
	case *IfStmt:
		return x.Pos
	case *WhileStmt:
		return x.Pos
	case *ForStmt:
		return x.Pos
	case *SwitchStmt:
		return x.Pos
	case *TryStmt:
		return x.Pos
	case *ThrowStmt:
		return x.Pos
	case *ReturnStmt:
		return x.Pos
	case *BreakStmt:
		return x.Pos
	case *ContinueStmt:
		return x.Pos
	}
	return SourcePos{}
}
