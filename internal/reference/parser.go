package reference

// parser is a recursive-descent parser mirroring the Python reference,
// including the backtracking explicit-type-argument rule.
type parser struct {
	ts []token
	i  int
}

func (p *parser) cur() token { return p.ts[p.i] }
func (p *parser) peek(n int) token {
	j := p.i + n
	if j >= len(p.ts) {
		j = len(p.ts) - 1
	}
	return p.ts[j]
}
func (p *parser) at(k tk) bool { return p.cur().Kind == k }
func (p *parser) advance() token {
	t := p.cur()
	if t.Kind != tkEOF {
		p.i++
	}
	return t
}
func (p *parser) match(kinds ...tk) (token, bool) {
	for _, k := range kinds {
		if p.cur().Kind == k {
			return p.advance(), true
		}
	}
	return token{}, false
}
func (p *parser) expect(kind tk, message string) token {
	if p.cur().Kind != kind {
		if message == "" {
			message = "expected " + tkName(kind)
		}
		panic(parseErr(p.cur().Pos, "%s, found %q", message, p.cur().Text))
	}
	return p.advance()
}
func (p *parser) skipTerms() {
	for {
		if _, ok := p.match(tkNewline, tkSemi); !ok {
			return
		}
	}
}
func (p *parser) skipNewlines() {
	for {
		if _, ok := p.match(tkNewline); !ok {
			return
		}
	}
}

func matchOpt(p *parser, k tk) bool {
	_, ok := p.match(k)
	return ok
}

func tkName(k tk) string {
	switch k {
	case tkEOF:
		return "EOF"
	case tkNewline:
		return "newline"
	case tkSemi:
		return "';'"
	case tkIdent:
		return "identifier"
	case tkInt:
		return "integer"
	case tkFloat:
		return "float"
	case tkString:
		return "string"
	case tkChar:
		return "character"
	case tkLBrace:
		return "'{'"
	case tkRBrace:
		return "'}'"
	case tkLParen:
		return "'('"
	case tkRParen:
		return "')'"
	case tkLBracket:
		return "'['"
	case tkRBracket:
		return "']'"
	case tkComma:
		return "','"
	case tkColon:
		return "':'"
	case tkGT:
		return "'>'"
	case tkLT:
		return "'<'"
	case tkQuestion:
		return "'?'"
	default:
		return "token"
	}
}

func (p *parser) parse() *Program {
	p.skipTerms()
	pkgTok := p.expect(tkPackage, "source file must begin with package declaration")
	_ = pkgTok
	package_ := p.expect(tkIdent, "expected package name").Text
	p.skipTerms()
	uses := []UseDecl{}
	for p.at(tkUse) {
		uses = append(uses, p.parseUse())
		p.skipTerms()
	}
	decls := []any{}
	for !p.at(tkEOF) {
		p.skipTerms()
		if p.at(tkEOF) {
			break
		}
		decls = append(decls, p.parseTopDecl())
		p.skipTerms()
		if !p.at(tkEOF) && p.ts[p.i-1].Kind != tkNewline && p.ts[p.i-1].Kind != tkSemi {
			panic(diagErr("P078", p.cur().Pos, 1, "expected a newline or semicolon after declaration"))
		}
	}
	filename := "<source>"
	if len(p.ts) > 0 {
		filename = p.ts[0].Pos.File
	}
	return &Program{Package: package_, Uses: uses, Declarations: decls, File: filename}
}

func (p *parser) parseUse() UseDecl {
	pos := p.expect(tkUse, "").Pos
	scheme := p.expect(tkIdent, "expected dependency scheme (file or url)").Text
	p.expect(tkColon, "")
	if p.at(tkString) {
		v := p.advance().Val.(string)
		return p.parseUseFlags(pos, scheme, v)
	}
	pieces := []string{}
	for p.cur().Kind != tkNewline && p.cur().Kind != tkSemi && p.cur().Kind != tkEOF {
		if p.at(tkIdent) && (p.cur().Text == "checksum" || p.cur().Text == "insecure") && p.peek(1).Kind == tkColon {
			break
		}
		pieces = append(pieces, p.advance().Text)
	}
	value := joinStrings(pieces)
	return p.parseUseFlags(pos, scheme, value)
}

func (p *parser) parseUseFlags(pos SourcePos, scheme, value string) UseDecl {
	u := UseDecl{Scheme: scheme, Value: value, Pos: pos}
	for p.cur().Kind != tkNewline && p.cur().Kind != tkSemi && p.cur().Kind != tkEOF {
		key := p.expect(tkIdent, "").Text
		p.expect(tkColon, "")
		switch key {
		case "checksum":
			p.expect(tkIdent, "")
			p.expect(tkColon, "")
			u.Checksum = p.advance().Text
		case "insecure":
			t := p.advance()
			u.Insecure = t.Kind == tkTrue || t.Text == "true"
		default:
			panic(parseErr(pos, "unknown use flag %s", key))
		}
	}
	return u
}

func joinStrings(parts []string) string {
	out := ""
	for _, s := range parts {
		out += s
	}
	return out
}

func (p *parser) parseTopDecl() any {
	_, public := p.match(tkPub)
	if p.at(tkFunc) {
		return p.parseFunction(public, false, "", true)
	}
	if p.at(tkStruct) {
		return p.parseStruct(public)
	}
	if p.at(tkTrait) {
		return p.parseTrait(public)
	}
	if p.at(tkEnum) {
		return p.parseEnum(public)
	}
	panic(parseErr(p.cur().Pos, "expected top-level func, struct, trait, or enum"))
}

// expectTypeGT consumes one `>` in type context, splitting `>>` if needed.
func (p *parser) expectTypeGT() {
	if p.at(tkGT) {
		p.advance()
		return
	}
	if p.at(tkShr) {
		t := p.cur()
		p.ts[p.i] = token{Kind: tkGT, Text: ">", Pos: t.Pos}
		p.ts = append(p.ts[:p.i+1], p.ts[p.i:]...)
		p.ts[p.i+1] = token{Kind: tkGT, Text: ">", Pos: t.Pos}
		p.advance()
		return
	}
	panic(parseErr(p.cur().Pos, "expected '>' to close generic type"))
}

func (p *parser) parseType() TypeRef {
	if p.at(tkFunc) {
		// Function type: func<P1, ..., Pn, R>.
		p.expect(tkFunc, "")
		if !matchOpt(p, tkLT) {
			panic(diagErr("P076", p.cur().Pos, 4, "function types require at least a return type; write func<ReturnType> or func<P1, ..., ReturnType>"))
		}
		p.skipNewlines()
		args := []TypeRef{p.parseType()}
		for {
			if _, ok := p.match(tkComma); !ok {
				break
			}
			p.skipNewlines()
			args = append(args, p.parseType())
		}
		p.skipNewlines()
		p.expectTypeGT()
		nullable := matchOpt(p, tkQuestion)
		return TypeRef{Name: "func", Args: args, Nullable: nullable}
	}
	name := p.expect(tkIdent, "expected type name").Text
	for matchOpt(p, tkDot) {
		name += "." + p.expect(tkIdent, "expected type name after '.'").Text
	}
	var args []TypeRef
	if matchOpt(p, tkLT) {
		p.skipNewlines()
		args = append(args, p.parseType())
		for {
			if _, ok := p.match(tkComma); !ok {
				break
			}
			p.skipNewlines()
			args = append(args, p.parseType())
		}
		p.skipNewlines()
		p.expectTypeGT()
	}
	nullable := matchOpt(p, tkQuestion)
	return TypeRef{Name: name, Args: args, Nullable: nullable}
}

func boolT(_ token, ok bool) bool { return ok }

func (p *parser) parseTypeParams() []TypeParam {
	if !matchOpt(p, tkLT) {
		return nil
	}
	p.skipNewlines()
	params := []TypeParam{}
	seen := map[string]bool{}
	for {
		nameTok := p.expect(tkIdent, "expected generic type parameter")
		if seen[nameTok.Text] {
			panic(parseErr(nameTok.Pos, "duplicate generic type parameter %s", nameTok.Text))
		}
		seen[nameTok.Text] = true
		var constraints []TypeRef
		if _, ok := p.match(tkColon); ok {
			constraints = append(constraints, p.parseType())
			for {
				if _, ok := p.match(tkAmp); !ok {
					break
				}
				constraints = append(constraints, p.parseType())
			}
		}
		params = append(params, TypeParam{Name: nameTok.Text, Constraints: constraints})
		p.skipNewlines()
		if _, ok := p.match(tkComma); !ok {
			break
		}
		p.skipNewlines()
	}
	p.expectTypeGT()
	return params
}

func (p *parser) parseParamList() []Param {
	p.expect(tkLParen, "")
	p.skipNewlines()
	params := []Param{}
	if !p.at(tkRParen) {
		for {
			paramTok := p.expect(tkIdent, "expected parameter name")
			p.expect(tkColon, "")
			variadic := matchOpt(p, tkEllipsis)
			ptype := p.parseType()
			params = append(params, Param{Name: paramTok.Text, Type: ptype, Variadic: variadic, Pos: &paramTok.Pos})
			p.skipNewlines()
			if _, ok := p.match(tkComma); !ok {
				break
			}
			p.skipNewlines()
			if p.at(tkRParen) {
				break
			}
		}
	}
	p.expect(tkRParen, "")
	return params
}

func (p *parser) parseFuncExpr(pos SourcePos) *FuncExpr {
	if p.at(tkLT) {
		panic(parseErr(pos, "anonymous functions cannot declare type parameters; write func(name: type) ..."))
	}
	params := p.parseParamList()
	rtype := voidT
	if _, ok := p.match(tkArrow); ok {
		rtype = p.parseType()
	}
	p.skipNewlines()
	body := p.parseBlock()
	return &FuncExpr{Params: params, ReturnType: rtype, Body: body, Pos: pos}
}

func (p *parser) parseFunction(public, mutating bool, owner string, bodyRequired bool) *FunctionDecl {
	pos := p.expect(tkFunc, "").Pos
	name := p.expect(tkIdent, "expected function name").Text
	typeParams := p.parseTypeParams()
	params := p.parseParamList()
	rtype := voidT
	if _, ok := p.match(tkArrow); ok {
		rtype = p.parseType()
	}
	p.skipNewlines()
	var body *Block
	if bodyRequired {
		body = p.parseBlock()
	}
	return &FunctionDecl{Name: name, Params: params, ReturnType: rtype, Body: body,
		Pos: pos, Public: public, Mutating: mutating, OwnerStruct: owner, TypeParams: typeParams}
}

func (p *parser) parseStruct(public bool) *StructDecl {
	pos := p.expect(tkStruct, "").Pos
	name := p.expect(tkIdent, "").Text
	typeParams := p.parseTypeParams()
	p.skipNewlines()
	p.expect(tkLBrace, "")
	p.skipTerms()
	fields := []FieldDecl{}
	methods := []*FunctionDecl{}
	for !p.at(tkRBrace) {
		memberPublic := matchOpt(p, tkPub)
		mut := matchOpt(p, tkMut)
		if p.at(tkFunc) {
			methods = append(methods, p.parseFunction(memberPublic, mut, name, true))
			p.skipTerms()
			continue
		}
		fieldTok := p.expect(tkIdent, "expected struct field or method")
		p.expect(tkColon, "")
		ftype := p.parseType()
		fields = append(fields, FieldDecl{Name: fieldTok.Text, Type: ftype, Public: memberPublic, Mutable: mut, Pos: &fieldTok.Pos})
		p.match(tkComma)
		p.skipTerms()
		if !p.at(tkRBrace) && p.ts[p.i-1].Kind != tkNewline && p.ts[p.i-1].Kind != tkSemi && p.ts[p.i-1].Kind != tkComma {
			panic(diagErr("P078", p.cur().Pos, 1, "expected a newline, semicolon, or comma after struct member"))
		}
	}
	p.expect(tkRBrace, "")
	return &StructDecl{Name: name, Fields: fields, Methods: methods, Pos: pos, TypeParams: typeParams, Public: public}
}

func (p *parser) parseTrait(public bool) *TraitDecl {
	pos := p.expect(tkTrait, "").Pos
	name := p.expect(tkIdent, "").Text
	typeParams := p.parseTypeParams()
	p.skipNewlines()
	p.expect(tkLBrace, "")
	p.skipTerms()
	methods := []*FunctionDecl{}
	for !p.at(tkRBrace) {
		mut := matchOpt(p, tkMut)
		mp := p.expect(tkFunc, "").Pos
		mname := p.expect(tkIdent, "").Text
		p.expect(tkLParen, "")
		params := []Param{}
		if !p.at(tkRParen) {
			for {
				pt := p.expect(tkIdent, "")
				p.expect(tkColon, "")
				varr := matchOpt(p, tkEllipsis)
				pty := p.parseType()
				params = append(params, Param{Name: pt.Text, Type: pty, Variadic: varr, Pos: &pt.Pos})
				if _, ok := p.match(tkComma); !ok {
					break
				}
				if p.at(tkRParen) {
					break
				}
			}
		}
		p.expect(tkRParen, "")
		rt := voidT
		if matchOpt(p, tkArrow) {
			rt = p.parseType()
		}
		methods = append(methods, &FunctionDecl{Name: mname, Params: params, ReturnType: rt, Pos: mp, Public: true, Mutating: mut})
		p.skipTerms()
	}
	p.expect(tkRBrace, "")
	return &TraitDecl{Name: name, Methods: methods, Pos: pos, TypeParams: typeParams, Public: public}
}

func (p *parser) parseEnum(public bool) *EnumDecl {
	pos := p.expect(tkEnum, "").Pos
	name := p.expect(tkIdent, "").Text
	typeParams := p.parseTypeParams()
	p.skipNewlines()
	p.expect(tkLBrace, "")
	p.skipTerms()
	members := []EnumMember{}
	for !p.at(tkRBrace) {
		n := p.expect(tkIdent, "").Text
		var payload []TypeRef
		if _, ok := p.match(tkLParen); ok {
			p.skipNewlines()
			payload = append(payload, p.parseType())
			for {
				if _, ok := p.match(tkComma); !ok {
					break
				}
				p.skipNewlines()
				payload = append(payload, p.parseType())
			}
			p.skipNewlines()
			p.expect(tkRParen, "")
		}
		var value *int64
		if _, ok := p.match(tkAssign); ok {
			if payload != nil {
				panic(diagErr("P077", pos, 4, "payload case '%s' cannot declare an integer value; an enum with payload cases uses names only", n))
			}
			sign := int64(1)
			if _, ok := p.match(tkMinus); ok {
				sign = -1
			}
			v := sign * p.expect(tkInt, "enum value must be an integer literal").Val.(int64)
			value = &v
		}
		members = append(members, EnumMember{Name: n, Value: value, PayloadTypes: payload})
		p.match(tkComma)
		p.skipTerms()
	}
	p.expect(tkRBrace, "")
	return &EnumDecl{Name: name, Members: members, Pos: pos, TypeParams: typeParams, Public: public}
}

func (p *parser) parseBlock() *Block {
	pos := p.expect(tkLBrace, "").Pos
	p.skipTerms()
	items := []any{}
	for !p.at(tkRBrace) {
		if p.at(tkEOF) {
			panic(parseErr(pos, "unterminated block"))
		}
		items = append(items, p.parseStatement())
		p.skipTerms()
		if !p.at(tkRBrace) && !p.at(tkEOF) && p.ts[p.i-1].Kind != tkNewline && p.ts[p.i-1].Kind != tkSemi {
			panic(diagErr("P078", p.cur().Pos, 1, "expected a newline or semicolon after statement"))
		}
	}
	p.expect(tkRBrace, "")
	return &Block{Statements: items, Pos: pos}
}

func (p *parser) parseStatement() any {
	p.skipNewlines()
	pos := p.cur().Pos
	if p.at(tkLBrace) {
		return p.parseBlock()
	}
	if _, ok := p.match(tkIf); ok {
		return p.parseIfAfterIf(pos)
	}
	if _, ok := p.match(tkWhile); ok {
		cond := p.parseExpr(false)
		p.skipNewlines()
		return &WhileStmt{Condition: cond, Body: p.parseBlock(), Pos: pos}
	}
	if _, ok := p.match(tkFor); ok {
		return p.parseFor(pos)
	}
	if _, ok := p.match(tkSwitch); ok {
		return p.parseSwitch(pos)
	}
	if _, ok := p.match(tkTry); ok {
		return p.parseTry(pos)
	}
	if _, ok := p.match(tkThrow); ok {
		return &ThrowStmt{Value: p.parseExpr(true), Pos: pos}
	}
	if _, ok := p.match(tkReturn); ok {
		if p.cur().Kind == tkNewline || p.cur().Kind == tkSemi || p.cur().Kind == tkRBrace {
			return &ReturnStmt{Pos: pos}
		}
		return &ReturnStmt{Value: p.parseExpr(true), Pos: pos}
	}
	if _, ok := p.match(tkBreak); ok {
		return &BreakStmt{Pos: pos}
	}
	if _, ok := p.match(tkContinue); ok {
		return &ContinueStmt{Pos: pos}
	}
	mutable := matchOpt(p, tkMut)
	if p.at(tkIdent) && p.peek(1).Kind == tkColon {
		n := p.advance()
		p.expect(tkColon, "")
		typ := p.parseType()
		p.expect(tkAssign, "")
		return &VarDecl{Name: n.Text, Type: typ, Value: p.parseExpr(true), Mutable: mutable, Pos: pos}
	}
	if mutable {
		panic(parseErr(pos, "mut is only valid on a variable declaration"))
	}
	return &ExprStmt{Expr: p.parseExpr(true), Pos: pos}
}

func (p *parser) parseIfAfterIf(pos SourcePos) *IfStmt {
	cond := p.parseExpr(false)
	p.skipNewlines()
	then := p.parseBlock()
	p.skipNewlines()
	var other any
	if _, ok := p.match(tkElse); ok {
		p.skipNewlines()
		if _, ok := p.match(tkIf); ok {
			other = p.parseIfAfterIf(p.ts[p.i-1].Pos)
		} else {
			other = p.parseBlock()
		}
	}
	return &IfStmt{Condition: cond, ThenBlock: then, ElseBranch: other, Pos: pos}
}

func (p *parser) parseFor(pos SourcePos) *ForStmt {
	if p.at(tkLParen) {
		panic(diagErr("P075", pos, 3, "map iteration bindings do not use parentheses; use 'for key, value in map'"))
	}
	names := []string{p.expect(tkIdent, "expected loop binding").Text}
	if _, ok := p.match(tkComma); ok {
		names = append(names, p.expect(tkIdent, "").Text)
	}
	p.expect(tkIn, "")
	expr := p.parseExpr(false)
	p.skipNewlines()
	return &ForStmt{Names: names, Iterable: expr, Body: p.parseBlock(), Pos: pos}
}

func (p *parser) parseSwitch(pos SourcePos) *SwitchStmt {
	value := p.parseExpr(false)
	p.skipNewlines()
	p.expect(tkLBrace, "")
	p.skipTerms()
	cases := []SwitchCase{}
	for !p.at(tkRBrace) {
		if _, ok := p.match(tkCase); ok {
			e := p.parseExpr(false)
			p.skipNewlines()
			cases = append(cases, SwitchCase{Expr: e, Body: p.parseBlock()})
		} else if _, ok := p.match(tkDefault); ok {
			p.skipNewlines()
			cases = append(cases, SwitchCase{Expr: nil, Body: p.parseBlock()})
		} else {
			panic(parseErr(p.cur().Pos, "expected case or default"))
		}
		p.skipTerms()
	}
	p.expect(tkRBrace, "")
	return &SwitchStmt{Value: value, Cases: cases, Pos: pos}
}

func (p *parser) parseTry(pos SourcePos) *TryStmt {
	p.skipNewlines()
	tb := p.parseBlock()
	p.skipNewlines()
	var cn, ct string
	var ctRef *TypeRef
	var cb, fb *Block
	if _, ok := p.match(tkCatch); ok {
		p.skipNewlines()
		p.expect(tkLParen, "")
		cn = p.expect(tkIdent, "").Text
		p.expect(tkColon, "")
		t := p.parseType()
		ctRef = &t
		_ = ct
		p.expect(tkRParen, "")
		p.skipNewlines()
		cb = p.parseBlock()
		p.skipNewlines()
	}
	if _, ok := p.match(tkFinally); ok {
		p.skipNewlines()
		fb = p.parseBlock()
	}
	if cb == nil && fb == nil {
		panic(parseErr(pos, "try requires catch and/or finally"))
	}
	return &TryStmt{TryBlock: tb, CatchName: cn, CatchType: ctRef, CatchBlock: cb, FinallyBlk: fb, Pos: pos}
}

var precedence = map[tk]int{
	tkCoalesce: 1, tkOrOr: 2, tkAndAnd: 3, tkEqEq: 4, tkNe: 4,
	tkLT: 5, tkLe: 5, tkGT: 5, tkGe: 5,
	tkPipe: 6, tkCaret: 7, tkAmp: 8, tkShl: 9, tkShr: 9,
	tkConcat: 10, tkPlus: 11, tkMinus: 11, tkStar: 12, tkSlash: 12, tkPercent: 12,
}

func (p *parser) parseExpr(allowStructLiteral bool) any { return p.parseExprMin(0, allowStructLiteral) }

func (p *parser) parseExprMin(minPrec int, allowStructLiteral bool) any {
	p.skipNewlines()
	left := p.parsePrefix(allowStructLiteral)
	for {
		p.skipNewlines()
		if p.at(tkLParen) {
			args := p.parseCallArgs()
			pos := exprPos(left, p.cur().Pos)
			left = &Call{Callee: left, Args: args, Pos: pos}
			continue
		}
		if matchOpt(p, tkLBracket) {
			idx := p.parseExpr(true)
			p.expect(tkRBracket, "")
			left = &Index{Obj: left, Index: idx, Pos: exprPos(left, p.cur().Pos)}
			continue
		}
		if matchOpt(p, tkDot) {
			n := p.expect(tkIdent, "expected member name").Text
			left = &Member{Obj: left, Name: n, Pos: exprPos(left, p.cur().Pos)}
			continue
		}
		// Explicit generic instantiation paths.
		if p.at(tkLT) {
			isNameOrMember := false
			switch left.(type) {
			case *Name, *Member:
				isNameOrMember = true
			}
			if isNameOrMember {
				var follow []tk
				if _, isName := left.(*Name); isName {
					follow = []tk{tkLParen, tkDot}
					if allowStructLiteral {
						follow = append(follow, tkLBrace)
					}
				} else if _, ok := dottedExpressionName(left); ok {
					follow = []tk{tkLParen, tkDot}
					if allowStructLiteral {
						follow = append(follow, tkLBrace)
					}
				} else {
					follow = []tk{tkLParen}
				}
				if targs := p.tryParseTypeArgs(follow); targs != nil {
					if p.at(tkDot) {
						setTypeArgs(left, targs)
					} else if p.at(tkLBrace) {
						left = p.parseStructLiteralWithArgs(left, targs)
					} else {
						pos := exprPos(left, p.cur().Pos)
						left = &Call{Callee: left, Args: p.parseCallArgs(), Pos: pos, TypeArgs: targs}
					}
					continue
				}
			}
		}
		if p.at(tkLBrace) && allowStructLiteral {
			ok := false
			switch left.(type) {
			case *Name, *Member:
				_, ok = dottedExpressionName(left)
			}
			if ok {
				left = p.parseStructLiteralWithArgs(left, nil)
				continue
			}
		}
		if _, ok := p.match(tkAssign); ok {
			if minPrec > 0 {
				p.i--
				break
			}
			right := p.parseExprMin(0, allowStructLiteral)
			left = &Assign{Target: left, Value: right, Pos: exprPos(left, p.cur().Pos)}
			continue
		}
		op := p.cur().Kind
		prec, hasPrec := precedence[op]
		if !hasPrec || prec < minPrec {
			break
		}
		tok := p.advance()
		nextMin := prec
		if op != tkCoalesce {
			nextMin = prec + 1
		}
		right := p.parseExprMin(nextMin, allowStructLiteral)
		left = &Binary{Left: left, Op: tok.Text, Right: right, Pos: tok.Pos}
	}
	return left
}

func exprPos(e any, fallback SourcePos) SourcePos {
	switch x := e.(type) {
	case *Name:
		return x.Pos
	case *Member:
		return x.Pos
	case *Call:
		return x.Pos
	case *Index:
		return x.Pos
	case *Literal:
		return x.Pos
	case *Unary:
		return x.Pos
	case *Binary:
		return x.Pos
	case *Assign:
		return x.Pos
	case *ListExpr:
		return x.Pos
	case *MapExpr:
		return x.Pos
	case *StructExpr:
		return x.Pos
	case *FuncExpr:
		return x.Pos
	}
	return fallback
}

func setTypeArgs(left any, args []TypeRef) {
	switch x := left.(type) {
	case *Name:
		x.TypeArgs = args
	case *Member:
		x.TypeArgs = args
	}
}

func (p *parser) tryParseTypeArgs(follow []tk) (args []TypeRef) {
	if !p.at(tkLT) {
		return nil
	}
	clone := &parser{ts: append([]token{}, p.ts...), i: p.i}
	func() {
		defer func() {
			if r := recover(); r != nil {
				if _, isParse := r.(*ParseError); isParse {
					args = nil
					return
				}
				if _, isDiag := r.(*DiagnosticError); isDiag {
					args = nil
					return
				}
				panic(r)
			}
		}()
		clone.advance()
		args = append(args, clone.parseType())
		for {
			if !matchOpt(clone, tkComma) {
				break
			}
			clone.skipNewlines()
			args = append(args, clone.parseType())
		}
		clone.expectTypeGT()
	}()
	if args == nil {
		return nil
	}
	matched := false
	for _, f := range follow {
		if clone.cur().Kind == f {
			matched = true
			break
		}
	}
	if !matched {
		return nil
	}
	p.ts = clone.ts
	p.i = clone.i
	return args
}

func (p *parser) parseCallArgs() []CallArg {
	p.expect(tkLParen, "")
	args := []CallArg{}
	p.skipNewlines()
	if !p.at(tkRParen) {
		for {
			e := p.parseExpr(true)
			spread := boolT(p.match(tkEllipsis))
			args = append(args, CallArg{Expr: e, Spread: spread})
			p.skipNewlines()
			if _, ok := p.match(tkComma); !ok {
				break
			}
			p.skipNewlines()
			if p.at(tkRParen) {
				break
			}
		}
	}
	p.expect(tkRParen, "")
	return args
}

func (p *parser) parsePrefix(allowStructLiteral bool) any {
	t := p.advance()
	switch t.Kind {
	case tkInt, tkFloat:
		return &Literal{Value: t.Val, LiteralKind: "int", Pos: t.Pos}
	case tkFloat + 1000: // unreachable
		return nil
	}
	if t.Kind == tkInt {
		return &Literal{Value: t.Val, LiteralKind: "int", Pos: t.Pos}
	}
	switch t.Kind {
	case tkString:
		return &Literal{Value: t.Val, LiteralKind: "string", Pos: t.Pos}
	case tkChar:
		return &Literal{Value: charValue(t.Val.(string)), LiteralKind: "char", Pos: t.Pos}
	case tkTrue:
		return &Literal{Value: true, LiteralKind: "bool", Pos: t.Pos}
	case tkFalse:
		return &Literal{Value: false, LiteralKind: "bool", Pos: t.Pos}
	case tkNull:
		return &Literal{Value: nil, LiteralKind: "null", Pos: t.Pos}
	case tkIdent:
		return &Name{Name: t.Text, Pos: t.Pos}
	case tkFunc:
		return p.parseFuncExpr(t.Pos)
	case tkBang, tkMinus, tkTilde, tkPlus:
		return &Unary{Op: t.Text, Expr: p.parseExprMin(13, allowStructLiteral), Pos: t.Pos}
	case tkLParen:
		e := p.parseExpr(true)
		p.expect(tkRParen, "")
		return e
	case tkLBracket:
		items := []any{}
		p.skipNewlines()
		if !p.at(tkRBracket) {
			for {
				items = append(items, p.parseExpr(true))
				p.skipNewlines()
				if _, ok := p.match(tkComma); !ok {
					break
				}
				p.skipNewlines()
				if p.at(tkRBracket) {
					break
				}
			}
		}
		p.expect(tkRBracket, "")
		return &ListExpr{Items: items, Pos: t.Pos}
	case tkLBrace:
		items := []mapEntry{}
		p.skipNewlines()
		if !p.at(tkRBrace) {
			for {
				k := p.parseExpr(true)
				p.expect(tkColon, "")
				v := p.parseExpr(true)
				items = append(items, mapEntry{Key: k, Value: v})
				p.skipNewlines()
				if _, ok := p.match(tkComma); !ok {
					break
				}
				p.skipNewlines()
				if p.at(tkRBrace) {
					break
				}
			}
		}
		p.expect(tkRBrace, "")
		return &MapExpr{Items: items, Pos: t.Pos}
	}
	panic(parseErr(t.Pos, "expected expression, found %q", t.Text))
}

func (p *parser) parseStructLiteralWithArgs(typeExpr any, typeArgs []TypeRef) *StructExpr {
	name, ok := dottedExpressionName(typeExpr)
	if !ok {
		panic(parseErr(exprPos(typeExpr, p.cur().Pos), "struct literal requires a type name"))
	}
	if typeArgs == nil {
		switch x := typeExpr.(type) {
		case *Name:
			typeArgs = x.TypeArgs
		case *Member:
			typeArgs = x.TypeArgs
		}
	}
	p.expect(tkLBrace, "")
	p.skipNewlines()
	fields := []structField{}
	if !p.at(tkRBrace) {
		for {
			fname := p.expect(tkIdent, "expected struct field name").Text
			p.expect(tkColon, "")
			value := p.parseExpr(true)
			fields = append(fields, structField{Name: fname, Value: value})
			p.skipNewlines()
			if _, ok := p.match(tkComma); !ok {
				break
			}
			p.skipNewlines()
			if p.at(tkRBrace) {
				break
			}
		}
	}
	p.expect(tkRBrace, "")
	return &StructExpr{TypeName: name, Fields: fields, Pos: exprPos(typeExpr, p.cur().Pos), TypeArgs: typeArgs}
}

func parseSource(source, filename string) (p *Program, err error) {
	defer func() {
		if r := recover(); r != nil {
			if se, ok := r.(*ParseError); ok {
				err = se
				return
			}
			if de, ok := r.(*DiagnosticError); ok {
				err = de
				return
			}
			panic(r)
		}
	}()
	l := newLexer(source, filename)
	pp := &parser{ts: l.tokens()}
	return pp.parse(), nil
}
