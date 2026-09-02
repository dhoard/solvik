package reference

import "sort"

// Statement and expression validation, continuing the Python SemanticValidator
// port: control flow, mutability, typing, switch patterns, assignability.

func (v *validator) lookup(name string) *staticBinding {
	for i := len(v.scopes) - 1; i >= 0; i-- {
		if b, ok := v.scopes[i][name]; ok {
			return &b
		}
	}
	return nil
}

func (v *validator) receiverField(name string) *FieldDecl {
	if v.currentStruct == nil {
		return nil
	}
	for i := range v.currentStruct.Fields {
		if v.currentStruct.Fields[i].Name == name {
			return &v.currentStruct.Fields[i]
		}
	}
	return nil
}

func (v *validator) blockDefinitelyReturns(b *Block) bool {
	for _, s := range b.Statements {
		switch x := s.(type) {
		case *ReturnStmt, *ThrowStmt:
			return true
		case *Block:
			if v.blockDefinitelyReturns(x) {
				return true
			}
		case *TryStmt:
			if v.blockDefinitelyReturns(x.TryBlock) {
				return true
			}
		case *IfStmt:
			if v.blockDefinitelyReturns(x.ThenBlock) {
				if eb, ok := x.ElseBranch.(*Block); ok {
					if v.blockDefinitelyReturns(eb) {
						return true
					}
				} else if ei, ok := x.ElseBranch.(*IfStmt); ok {
					if v.blockDefinitelyReturns(&Block{Statements: []any{ei}}) {
						return true
					}
				}
			}
		case *SwitchStmt:
			allReturn := true
			for _, c := range x.Cases {
				if !v.blockDefinitelyReturns(c.Body) {
					allReturn = false
					break
				}
			}
			if !allReturn {
				continue
			}
			hasDefault := false
			for _, c := range x.Cases {
				if c.Expr == nil {
					hasDefault = true
				}
			}
			if hasDefault {
				return true
			}
			enum := v.enumOf(v.infer(x.Value).Name)
			if enum != nil && v.switchCoversAllCases(x, enum, v.infer(x.Value).Name) {
				return true
			}
		case *WhileStmt:
			if lit, ok := x.Condition.(*Literal); ok && lit.Value == true && v.blockDefinitelyReturns(x.Body) {
				return true
			}
		}
	}
	return false
}

func (v *validator) switchCoversAllCases(stmt *SwitchStmt, enum *EnumDecl, enumCanon string) bool {
	covered := map[string]bool{}
	for _, c := range stmt.Cases {
		if c.Expr == nil {
			continue
		}
		if kind, enumExpr, caseName, _, ok := enumPatternShape(c.Expr); ok {
			if kind == "qualified" {
				pe := v.enumOf(mustDotted(enumExpr))
				if pe != nil && pe.Name == enum.Name {
					covered[caseName] = true
				}
			} else if enumMemberExists(enum, caseName) {
				covered[caseName] = true
			}
		} else if m, ok := c.Expr.(*Member); ok {
			if dotted, dok := dottedExpressionName(m.Obj); dok && v.canonical(dotted) == enumCanon {
				if member := enumMember(enum, m.Name); member != nil && len(member.PayloadTypes) == 0 {
					covered[m.Name] = true
				}
			}
		}
	}
	for _, m := range enum.Members {
		if !covered[m.Name] {
			return false
		}
	}
	return true
}

func mustDotted(e any) string {
	d, _ := dottedExpressionName(e)
	return d
}

func enumMember(enum *EnumDecl, name string) *EnumMember {
	for i := range enum.Members {
		if enum.Members[i].Name == name {
			return &enum.Members[i]
		}
	}
	return nil
}

func enumMemberExists(enum *EnumDecl, name string) bool { return enumMember(enum, name) != nil }

func (v *validator) statementTerminates(s any) bool {
	switch s.(type) {
	case *ReturnStmt, *ThrowStmt, *BreakStmt, *ContinueStmt:
		return true
	}
	return false
}

func (v *validator) checkStatement(s any) {
	switch x := s.(type) {
	case *Block:
		v.checkBlock(x, true)
	case *VarDecl:
		v.checkAnnotationType(x.Type, x.Pos)
		v.checkValueForType(x.Value, x.Type)
		v.scopes[len(v.scopes)-1][x.Name] = staticBinding{typ: x.Type, mutable: x.Mutable}
	case *ExprStmt:
		v.infer(x.Expr)
	case *IfStmt:
		thenN, elseN := v.nullNarrowing(x.Condition)
		v.infer(x.Condition)
		v.checkNarrowedBlock(x.ThenBlock, thenN)
		if eb, ok := x.ElseBranch.(*Block); ok {
			v.checkNarrowedBlock(eb, elseN)
		} else if ei, ok := x.ElseBranch.(*IfStmt); ok {
			if len(elseN) > 0 {
				v.scopes = append(v.scopes, map[string]staticBinding{})
				for k, t := range elseN {
					v.scopes[len(v.scopes)-1][k] = staticBinding{typ: t}
				}
				v.checkStatement(ei)
				v.scopes = v.scopes[:len(v.scopes)-1]
			} else {
				v.checkStatement(ei)
			}
		}
	case *WhileStmt:
		v.loopDepth++
		v.infer(x.Condition)
		v.checkBlock(x.Body, true)
		v.loopDepth--
	case *ForStmt:
		iterableType := v.infer(x.Iterable)
		bindings := map[string]staticBinding{}
		if len(x.Names) == 2 && iterableType.Name == "map" && len(iterableType.Args) == 2 {
			bindings[x.Names[0]] = staticBinding{typ: iterableType.Args[0]}
			bindings[x.Names[1]] = staticBinding{typ: iterableType.Args[1]}
		} else {
			bindings[x.Names[0]] = staticBinding{typ: v.iterableElementType(iterableType)}
		}
		v.loopDepth++
		v.scopes = append(v.scopes, bindings)
		v.checkBlock(x.Body, false)
		v.scopes = v.scopes[:len(v.scopes)-1]
		v.loopDepth--
	case *SwitchStmt:
		v.checkSwitch(x)
	case *TryStmt:
		v.checkBlock(x.TryBlock, true)
		if x.CatchBlock != nil {
			ct := exceptionT
			if x.CatchType != nil {
				ct = *x.CatchType
			}
			name := x.CatchName
			if name == "" {
				name = "e"
			}
			v.scopes = append(v.scopes, map[string]staticBinding{name: {typ: ct}})
			v.checkBlock(x.CatchBlock, false)
			v.scopes = v.scopes[:len(v.scopes)-1]
		}
		if x.FinallyBlk != nil {
			v.checkBlock(x.FinallyBlk, true)
		}
	case *ThrowStmt:
		v.infer(x.Value)
	case *ReturnStmt:
		v.checkReturnStatement(x)
	case *BreakStmt, *ContinueStmt:
		if v.loopDepth == 0 {
			kind := "break"
			if _, isCont := x.(*ContinueStmt); isCont {
				kind = "continue"
			}
			var brkPos SourcePos
			switch brk := x.(type) {
			case *BreakStmt:
				brkPos = brk.Pos
			case *ContinueStmt:
				brkPos = brk.Pos
			}
			v.error("C113", brkPos, v.toLineEnd(brkPos), "%s outside of a loop (while/for)", kind)
		}
	}
}

func (v *validator) checkReturnStatement(x *ReturnStmt) {
	declared := voidT
	if v.currentFunction != nil {
		declared = v.currentFunction.ReturnType
	}
	if x.Value == nil {
		if declared.Name != "void" {
			v.error("C115", x.Pos, v.toLineEnd(x.Pos), "return without a value in a function returning %s", declared)
		}
		return
	}
	if declared.Name == "void" {
		v.error("C115", x.Pos, v.toLineEnd(x.Pos), "return with a value in a function that returns nothing")
		return
	}
	actual := v.infer(x.Value)
	if !actual.equal(unknownT) && !v.assignable(actual, declared) {
		v.error("C114", x.Pos, v.toLineEnd(x.Pos), "return value of type %s is not assignable to declared return type %s", actual, declared)
	}
}

func (v *validator) nullNarrowing(cond any) (map[string]TypeRef, map[string]TypeRef) {
	thenN := map[string]TypeRef{}
	elseN := map[string]TypeRef{}
	if b, ok := cond.(*Binary); ok && (b.Op == "==" || b.Op == "!=") {
		var nullSide any
		var other any
		if lit, ok := b.Left.(*Literal); ok && lit.LiteralKind == "null" {
			nullSide = b.Left
			other = b.Right
		} else if lit, ok := b.Right.(*Literal); ok && lit.LiteralKind == "null" {
			nullSide = b.Right
			other = b.Left
		}
		if nullSide != nil {
			if n, ok := other.(*Name); ok {
				if binding := v.lookup(n.Name); binding != nil && binding.typ.Nullable {
					nonnull := binding.typ.nonnull()
					if b.Op == "!=" {
						thenN[n.Name] = nonnull
					} else {
						elseN[n.Name] = nonnull
					}
				}
			}
		}
	}
	return thenN, elseN
}

func (v *validator) checkNarrowedBlock(b *Block, narrow map[string]TypeRef) {
	if len(narrow) == 0 {
		v.checkBlock(b, true)
		return
	}
	base := map[string]staticBinding{}
	for k, val := range v.scopes[len(v.scopes)-1] {
		base[k] = val
	}
	for name, typ := range narrow {
		cur := v.lookup(name)
		base[name] = staticBinding{typ: typ, mutable: cur != nil && cur.mutable}
	}
	v.scopes = append(v.scopes, base)
	v.checkBlock(b, false)
	v.scopes = v.scopes[:len(v.scopes)-1]
}

func (v *validator) checkSwitch(stmt *SwitchStmt) {
	switchType := v.infer(stmt.Value)
	var enumDecl *EnumDecl
	if switchType.Name != "<unknown>" {
		enumDecl = v.enumOf(switchType.Name)
	}
	enumBindings := map[string]TypeRef{}
	if enumDecl != nil {
		for i, p := range enumDecl.TypeParams {
			if i < len(switchType.Args) {
				enumBindings[p.Name] = switchType.Args[i]
			}
		}
	}
	covered := map[string]bool{}
	hasDefault := false
	hasNullCase := false
	for _, c := range stmt.Cases {
		if c.Expr == nil {
			hasDefault = true
			v.checkBlock(c.Body, true)
			continue
		}
		var patEnum *EnumDecl
		var caseName string
		var elements []any
		var patArgs []TypeRef
		isPattern := false
		if kind, enumExpr, cn, elems, ok := enumPatternShape(c.Expr); ok {
			if kind == "qualified" {
				pe := v.enumOf(mustDotted(enumExpr))
				if pe != nil && enumMemberExists(pe, cn) {
					patEnum, caseName, elements, patArgs = pe, cn, elems, memberTypeArgsOf(enumExpr)
					isPattern = true
				}
			} else if enumDecl != nil && enumMemberExists(enumDecl, cn) {
				patEnum, caseName, elements = enumDecl, cn, elems
				isPattern = true
			}
		}
		if isPattern {
			compatible := enumDecl != nil && patEnum.Name == enumDecl.Name &&
				(len(patArgs) == 0 || len(switchType.Args) == 0 || typeArgsEqual(patArgs, switchType.Args))
			if !compatible {
				v.error("C094", exprPos(c.Expr, stmt.Pos), literalSpan(c.Expr), "case pattern of enum '%s' can never match switch of type %s", patEnum.Name, switchType)
			}
			if covered[caseName] {
				v.error("C106", exprPos(c.Expr, stmt.Pos), literalSpan(c.Expr), "duplicate case pattern '%s' already matched by an earlier case", caseName)
			}
			covered[caseName] = true
			bindings := v.validateEnumPattern(patEnum, caseName, elements, enumBindings, c.Expr)
			scope := map[string]staticBinding{}
			for k, t := range bindings {
				scope[k] = staticBinding{typ: t}
			}
			v.scopes = append(v.scopes, scope)
			v.checkBlock(c.Body, false)
			v.scopes = v.scopes[:len(v.scopes)-1]
			continue
		}
		caseType := v.infer(c.Expr)
		if caseType.Name == "null" {
			hasNullCase = true
		}
		if !v.switchTypesOverlap(switchType, caseType) {
			msg := "cannot use case of type " + caseType.String() + " with switch of type " + switchType.String()
			if caseType.Name == "null" {
				msg = "case null can never match switch of type " + switchType.String() + "; the switch type is not nullable"
			}
			v.error("C094", exprPos(c.Expr, stmt.Pos), literalSpan(c.Expr), "%s", msg)
		}
		if enumDecl != nil {
			if m, ok := c.Expr.(*Member); ok {
				if dotted, dok := dottedExpressionName(m.Obj); dok && v.canonical(dotted) == v.canonical(switchType.Name) {
					if member := enumMember(enumDecl, m.Name); member != nil {
						if len(member.PayloadTypes) > 0 {
							v.error("C107", m.Pos, literalSpan(m), "payload case '%s' requires pattern arguments in a switch, e.g. case %s.%s(value)", m.Name, enumDecl.Name, m.Name)
						} else if covered[m.Name] {
							v.error("C106", m.Pos, literalSpan(m), "duplicate case '%s' already matched by an earlier case", m.Name)
						} else {
							covered[m.Name] = true
						}
					}
				}
			}
		}
		v.checkBlock(c.Body, true)
	}
	if enumDecl != nil && !hasDefault {
		var missing []string
		for _, m := range enumDecl.Members {
			if !covered[m.Name] {
				missing = append(missing, m.Name)
			}
		}
		if switchType.Nullable && !hasNullCase {
			missing = append(missing, "null")
		}
		if len(missing) > 0 {
			sort.Strings(missing)
			v.error("C105", stmt.Pos, v.toLineEnd(stmt.Pos), "non-exhaustive switch over enum '%s'; missing case(s): %s", enumDecl.Name, joinStrings(missing))
		}
	}
}

func memberTypeArgsOf(e any) []TypeRef {
	switch x := e.(type) {
	case *Name:
		return x.TypeArgs
	case *Member:
		return x.TypeArgs
	}
	return nil
}

func (v *validator) validateEnumPattern(patEnum *EnumDecl, caseName string, elements []any, enumBindings map[string]TypeRef, expr any) map[string]TypeRef {
	member := enumMember(patEnum, caseName)
	pos := exprPos(expr, patEnum.Pos)
	if member == nil {
		v.error("C107", pos, literalSpan(expr), "enum '%s' has no case '%s'", patEnum.Name, caseName)
		return map[string]TypeRef{}
	}
	if len(member.PayloadTypes) == 0 {
		if len(elements) > 0 {
			v.error("C107", pos, literalSpan(expr), "case '%s' takes no payload and cannot be used with pattern arguments", caseName)
		}
		return map[string]TypeRef{}
	}
	if len(elements) != len(member.PayloadTypes) {
		v.error("C107", pos, literalSpan(expr), "case '%s' expects %d payload value(s), found %d", caseName, len(member.PayloadTypes), len(elements))
	}
	bindings := map[string]TypeRef{}
	seen := map[string]bool{}
	for i, element := range elements {
		ptype := member.PayloadTypes[i]
		concrete := substituteType(ptype, enumBindings)
		free := false
		for n := range freeNames(concrete) {
			if enumParamName(patEnum, n) {
				free = true
			}
		}
		switch el := element.(type) {
		case *Name:
			if el.Name == "_" {
				continue
			}
			if seen[el.Name] {
				v.error("C107", el.Pos, literalSpan(el), "duplicate pattern binding '%s' in case '%s'", el.Name, caseName)
			}
			seen[el.Name] = true
			bindings[el.Name] = concrete
		case *Literal:
			actual := nullT
			if el.LiteralKind != "null" {
				actual = typeRef(el.LiteralKind)
			}
			if !free && !v.assignable(actual, concrete) {
				v.error("C108", el.Pos, literalSpan(el), "pattern payload for case '%s': expected %s but got %s", caseName, concrete, actual)
			}
		case *Call:
			var nestedEnum *EnumDecl
			var nestedCase string
			var nestedElems []any
			nestedBindings := enumBindings
			found := false
			if m, ok := el.Callee.(*Member); ok {
				if dotted, dok := dottedExpressionName(m.Obj); dok {
					if ne := v.enumOf(dotted); ne != nil && enumMemberExists(ne, m.Name) {
						nestedEnum, nestedCase, nestedElems = ne, m.Name, callArgsExprs(el)
						nestedBindings = map[string]TypeRef{}
						for i, p := range ne.TypeParams {
							ta := memberTypeArgsOf(m.Obj)
							if i < len(ta) {
								nestedBindings[p.Name] = ta[i]
							}
						}
						found = true
					}
				}
			} else if n, ok := el.Callee.(*Name); ok {
				if enumMemberExists(patEnum, n.Name) {
					nestedEnum, nestedCase, nestedElems = patEnum, n.Name, callArgsExprs(el)
					found = true
				}
			}
			if !found {
				v.error("C107", el.Pos, literalSpan(el), "invalid pattern element: expected a binding name, '_' wildcard, literal, or enum case pattern")
				continue
			}
			nestedType := TypeRef{Name: nestedEnum.Name}
			nestedArgs := make([]TypeRef, len(nestedEnum.TypeParams))
			for i, p := range nestedEnum.TypeParams {
				nestedArgs[i] = nestedBindings[p.Name]
			}
			nestedType.Args = nestedArgs
			if !free && !v.assignable(nestedType, concrete) {
				v.error("C108", el.Pos, literalSpan(el), "nested pattern of enum '%s' cannot match payload of type %s", nestedEnum.Name, concrete)
			}
			inner := v.validateEnumPattern(nestedEnum, nestedCase, nestedElems, nestedBindings, el)
			for k, t := range inner {
				if seen[k] {
					v.error("C107", el.Pos, literalSpan(el), "duplicate pattern binding '%s' in case '%s'", k, caseName)
				}
				seen[k] = true
				bindings[k] = t
			}
		default:
			v.error("C107", exprPos(el, pos), literalSpan(el), "invalid pattern element: expected a binding name, '_' wildcard, literal, or enum case pattern")
		}
	}
	return bindings
}

func enumParamName(enum *EnumDecl, name string) bool {
	for _, p := range enum.TypeParams {
		if p.Name == name {
			return true
		}
	}
	return false
}

func callArgsExprs(c *Call) []any {
	out := make([]any, 0, len(c.Args))
	for _, a := range c.Args {
		out = append(out, a.Expr)
	}
	return out
}

func (v *validator) switchTypesOverlap(switchType, caseType TypeRef) bool {
	if switchType.equal(unknownT) || caseType.equal(unknownT) || switchType.Name == "any" || caseType.Name == "any" {
		return true
	}
	if caseType.Name == "regex" {
		return switchType.Name == "string"
	}
	if caseType.Name == "null" {
		return switchType.Nullable
	}
	if switchType.Name == caseType.Name {
		return true
	}
	return (switchType.Name == "byte" || switchType.Name == "int" || switchType.Name == "float") &&
		(caseType.Name == "byte" || caseType.Name == "int" || caseType.Name == "float")
}

func (v *validator) iterableElementType(t TypeRef) TypeRef {
	if t.Name == "string" {
		return typeRef("char")
	}
	if (t.Name == "list" || t.Name == "stack") && len(t.Args) > 0 {
		return t.Args[0]
	}
	if t.Name == "map" && len(t.Args) > 0 {
		return t.Args[0]
	}
	if sig := v.methodSigForType(t, "iterator"); sig != nil && sig.returnType.Name == "list" && len(sig.returnType.Args) > 0 {
		return sig.returnType.Args[0]
	}
	return unknownT
}
