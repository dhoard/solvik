// Copyright (c) 2026-present Douglas Hoard
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// Package parser implements recursive-descent parsing for the language.
package parser

import (
	"fmt"
	"strconv"

	"github.com/dhoard/solvik-language/internal/ast"
	"github.com/dhoard/solvik-language/internal/diagnostic"
	"github.com/dhoard/solvik-language/internal/lexer"
	"github.com/dhoard/solvik-language/internal/source"
	"github.com/dhoard/solvik-language/internal/types"
)

// Parser performs syntactic analysis.
type Parser struct {
	src       *source.Source
	tokens    []lexer.Token
	pos       int
	diags     *diagnostic.Diagnostics
	errCount  int
	maxErrors int
}

const defaultMaxErrors = 50

// New creates a new parser.
func New(src *source.Source, tokens []lexer.Token) *Parser {
	return &Parser{
		src:       src,
		tokens:    tokens,
		diags:     diagnostic.NewDiagnostics(),
		maxErrors: defaultMaxErrors,
	}
}

// Parse parses the token stream and returns the AST.
func (p *Parser) Parse() (*ast.Program, *diagnostic.Diagnostics) {
	prog := &ast.Program{}

	// Parse package declaration
	if p.match(lexer.TokenPackage) {
		if p.check(lexer.TokenIdentifier) {
			prog.Module = p.advance().Lexeme
		} else {
			p.addError("P001", "expected module name after 'package'", p.peek().Span)
		}
		p.expectNewlineOrSemicolon()
	}

	// Parse imports and functions
	for !p.isAtEnd() && p.errCount < p.maxErrors {
		if p.match(lexer.TokenImport) {
			imp := p.parseImport()
			if imp != nil {
				prog.Imports = append(prog.Imports, imp)
			}
			p.expectNewlineOrSemicolon()
		} else if p.match(lexer.TokenFunc) {
			fn := p.parseFunction()
			if fn != nil {
				prog.Funcs = append(prog.Funcs, fn)
			}
		} else if p.match(lexer.TokenNewline) || p.match(lexer.TokenSemicolon) {
			// Skip blank lines
		} else {
			p.addError("P002", "expected function declaration or import", p.peek().Span)
			p.synchronize()
		}
	}

	if p.errCount >= p.maxErrors {
		p.diags.AddError("P045", "too many parse errors; stopping", source.Span{})
	}

	return prog, p.diags
}

// parseImport parses an import statement.
func (p *Parser) parseImport() *ast.Import {
	if !p.check(lexer.TokenIdentifier) {
		p.addError("P003", "expected module name in import", p.peek().Span)
		return nil
	}
	tok := p.advance()
	imp := &ast.Import{
		SpanNode: ast.WithSpan(tok.Span),
		Module:   tok.Lexeme,
	}
	return imp
}

// parseFunction parses a function declaration.
func (p *Parser) parseFunction() *ast.Function {
	start := p.previous().Span

	// Function name
	if !p.check(lexer.TokenIdentifier) {
		p.addError("P004", "expected function name", p.peek().Span)
		return nil
	}
	nameTok := p.advance()

	fn := &ast.Function{
		SpanNode: ast.WithSpan(nameTok.Span),
		Name:     nameTok.Lexeme,
	}

	// Parameters
	if !p.match(lexer.TokenLParen) {
		p.addError("P005", "expected '(' after function name", p.peek().Span)
		p.synchronize()
		return nil
	}

	fn.Parameters = p.parseParameters()

	if !p.match(lexer.TokenRParen) {
		p.addError("P006", "expected ')' after parameters", p.peek().Span)
		// Try to recover - look for '{' or '->'
	}

	// Return type (optional)
	if p.match(lexer.TokenArrow) {
		fn.ReturnType = p.parseTypeAnnotation()
		if fn.ReturnType != nil {
			fn.ReturnType = p.parseNullableSuffix(fn.ReturnType)
		}
	}

	// Body
	if !p.match(lexer.TokenLBrace) {
		p.addError("P007", "expected '{' for function body", p.peek().Span)
		p.synchronize()
		return fn
	}

	fn.Body = p.parseBlock()
	if fn.Body == nil {
		fn.Body = &ast.Block{SpanNode: ast.WithSpan(p.previous().Span)}
	}

	// Update span to include the whole function
	fn.SpanNode = ast.WithSpan(source.SpanBetween(
		p.src.PosFromOffset(start.Start),
		p.src.PosFromOffset(p.previous().Span.End),
	))
	return fn
}

// parseParameters parses parameter list.
func (p *Parser) parseParameters() []*ast.Parameter {
	var params []*ast.Parameter
	for !p.check(lexer.TokenRParen) && !p.isAtEnd() {
		if len(params) > 0 {
			if !p.match(lexer.TokenComma) {
				break
			}
			if p.check(lexer.TokenRParen) {
				break
			}
		}

		if !p.check(lexer.TokenIdentifier) {
			p.addError("P008", "expected parameter name", p.peek().Span)
			break
		}
		nameTok := p.advance()

		if !p.match(lexer.TokenColon) {
			p.addError("P009", "expected ':' after parameter name", p.peek().Span)
			break
		}

		typeAnn := p.parseTypeAnnotation()
		if typeAnn == nil {
			p.addError("P010", "expected parameter type", p.peek().Span)
			break
		}

		// Check for nullable suffix '?'
		typeAnn = p.parseNullableSuffix(typeAnn)

		params = append(params, &ast.Parameter{
			SpanNode: ast.WithSpan(nameTok.Span),
			Name:     nameTok.Lexeme,
			Type:     typeAnn,
		})
	}
	return params
}

// parseTypeAnnotation parses a type annotation.
func (p *Parser) parseTypeAnnotation() *ast.TypeAnnotation {
	if p.match(lexer.TokenBool) {
		return &ast.TypeAnnotation{Kind: types.KindBool, SpanNode: ast.WithSpan(p.previous().Span)}
	}
	if p.match(lexer.TokenByte) {
		return &ast.TypeAnnotation{Kind: types.KindByte, SpanNode: ast.WithSpan(p.previous().Span)}
	}
	if p.match(lexer.TokenInt) {
		return &ast.TypeAnnotation{Kind: types.KindInt, SpanNode: ast.WithSpan(p.previous().Span)}
	}
	if p.match(lexer.TokenLong) {
		return &ast.TypeAnnotation{Kind: types.KindLong, SpanNode: ast.WithSpan(p.previous().Span)}
	}
	if p.match(lexer.TokenFloat) {
		return &ast.TypeAnnotation{Kind: types.KindFloat, SpanNode: ast.WithSpan(p.previous().Span)}
	}
	if p.match(lexer.TokenDouble) {
		return &ast.TypeAnnotation{Kind: types.KindDouble, SpanNode: ast.WithSpan(p.previous().Span)}
	}
	if p.match(lexer.TokenChar) {
		return &ast.TypeAnnotation{Kind: types.KindChar, SpanNode: ast.WithSpan(p.previous().Span)}
	}
	if p.match(lexer.TokenString) {
		return &ast.TypeAnnotation{Kind: types.KindString, SpanNode: ast.WithSpan(p.previous().Span)}
	}
	if p.match(lexer.TokenVoid) {
		return &ast.TypeAnnotation{Kind: types.KindVoid, SpanNode: ast.WithSpan(p.previous().Span)}
	}
	if p.match(lexer.TokenList) {
		if p.match(lexer.TokenLt) {
			elem := p.parseTypeAnnotation()
			if elem == nil {
				p.addError("P011", "expected element type in List<T>", p.peek().Span)
				return nil
			}
			if !p.match(lexer.TokenGt) {
				p.addError("P012", "expected '>' after List element type", p.peek().Span)
			}
			return &ast.TypeAnnotation{
				Kind:     types.KindList,
				Element:  elem,
				SpanNode: ast.WithSpan(p.previous().Span),
			}
		}
		return &ast.TypeAnnotation{Kind: types.KindList, SpanNode: ast.WithSpan(p.previous().Span)}
	}
	if p.match(lexer.TokenMap) {
		if p.match(lexer.TokenLt) {
			keyT := p.parseTypeAnnotation()
			if keyT == nil {
				p.addError("P013", "expected key type in Map<K,V>", p.peek().Span)
				return nil
			}
			if !p.match(lexer.TokenComma) {
				p.addError("P014", "expected ',' between key and value types", p.peek().Span)
				return nil
			}
			valT := p.parseTypeAnnotation()
			if valT == nil {
				p.addError("P015", "expected value type in Map<K,V>", p.peek().Span)
				return nil
			}
			if !p.match(lexer.TokenGt) {
				p.addError("P016", "expected '>' after Map types", p.peek().Span)
			}
			return &ast.TypeAnnotation{
				Kind:      types.KindMap,
				KeyType:   keyT,
				ValueType: valT,
				SpanNode:  ast.WithSpan(p.previous().Span),
			}
		}
		return &ast.TypeAnnotation{Kind: types.KindMap, SpanNode: ast.WithSpan(p.previous().Span)}
	}

	// Check for nullable suffix '?'
	if p.check(lexer.TokenQuestion) {
		// Parsing context: this is a nullable type, but we need a base type first
		// This case shouldn't normally be reached without a base type
		p.addError("P017", "expected type before '?'", p.peek().Span)
		_ = p.advance()
		return nil
	}

	return nil
}

// parseNullableSuffix checks for '?' after a type annotation.
func (p *Parser) parseNullableSuffix(t *ast.TypeAnnotation) *ast.TypeAnnotation {
	if t == nil {
		return nil
	}
	if p.match(lexer.TokenQuestion) {
		t.Nullable = true
	}
	return t
}

// parseBlock parses a block { ... }.
func (p *Parser) parseBlock() *ast.Block {
	startSpan := p.previous().Span
	block := &ast.Block{}

	for !p.check(lexer.TokenRBrace) && !p.isAtEnd() {
		// Track position to detect infinite loops
		beforePos := p.pos

		stmt := p.parseStatement()
		if stmt != nil {
			block.Statements = append(block.Statements, stmt)
		}

		// If position didn't advance and we got nil, consume one token to prevent infinite loop
		if stmt == nil && p.pos == beforePos && !p.isAtEnd() {
			if p.check(lexer.TokenNewline) || p.check(lexer.TokenSemicolon) {
				p.advance()
			} else if !p.check(lexer.TokenRBrace) {
				p.advance()
			}
		}

		// After a statement, expect newline or semicolon (unless at end of block)
		if !p.check(lexer.TokenRBrace) && !p.isAtEnd() {
			p.expectNewlineOrSemicolon()
		}
	}

	if !p.match(lexer.TokenRBrace) {
		p.addError("P018", "expected '}' to close block", p.peek().Span)
	} else {
		endSpan := p.previous().Span
		block.SpanNode = ast.WithSpan(source.SpanBetween(
			p.src.PosFromOffset(startSpan.Start),
			p.src.PosFromOffset(endSpan.End),
		))
	}

	return block
}

// parseStatement parses a single statement.
// Returns nil for blank lines or no-op statements.
func (p *Parser) parseStatement() ast.Statement {
	// Check error limit before parsing another statement
	if p.errCount >= p.maxErrors {
		return nil
	}

	// Skip leading newlines/semicolons
	for p.check(lexer.TokenNewline) || p.check(lexer.TokenSemicolon) {
		p.advance()
	}

	// Check for declaration: identifier ':' type '=' ...
	if p.check(lexer.TokenIdentifier) {
		// Look ahead to see if this is a declaration (identifier ':' identifier)
		save := p.pos
		p.advance()
		isDecl := p.check(lexer.TokenColon)
		p.pos = save

		if isDecl {
			stmt := p.parseVarDecl()
			if stmt != nil {
				return stmt
			}
			// parseVarDecl failed but may have partially consumed tokens.
			// Skip to end of line to avoid infinite loop.
			p.skipToStatementBoundary()
			return nil
		}
	}

	if p.match(lexer.TokenFunc) {
		// Nested function? For now, treat as error
		p.addError("P019", "nested functions not supported", p.peek().Span)
		return nil
	}

	if p.match(lexer.TokenIf) {
		return p.parseIfStmt()
	}

	if p.match(lexer.TokenWhile) {
		return p.parseWhileStmt()
	}

	if p.match(lexer.TokenFor) {
		return p.parseForStmt()
	}

	if p.match(lexer.TokenSwitch) {
		return p.parseSwitchStmt()
	}

	if p.match(lexer.TokenBreak) {
		return &ast.BreakStmt{SpanNode: ast.WithSpan(p.previous().Span)}
	}

	if p.match(lexer.TokenContinue) {
		return &ast.ContinueStmt{SpanNode: ast.WithSpan(p.previous().Span)}
	}

	if p.match(lexer.TokenReturn) {
		stmt := &ast.ReturnStmt{SpanNode: ast.WithSpan(p.previous().Span)}
		if !p.checkNewlineOrSemicolon() && !p.check(lexer.TokenRBrace) {
			stmt.Value = p.parseExpression()
		}
		return stmt
	}

	if p.match(lexer.TokenLBrace) {
		return p.parseBlock()
	}

	// Try to parse as expression statement (assignment or expression)
	if p.check(lexer.TokenNewline) || p.check(lexer.TokenSemicolon) || p.check(lexer.TokenRBrace) || p.isAtEnd() {
		return nil
	}

	stmt := p.parseExprStmt()
	if stmt == nil {
		// Skip to end of line to avoid infinite loop
		p.skipToStatementBoundary()
	}
	return stmt
}

// parseVarDecl parses a variable declaration.
func (p *Parser) parseVarDecl() *ast.VariableDecl {
	nameTok := p.advance() // identifier
	if !p.match(lexer.TokenColon) {
		p.addError("P020", "expected ':' after variable name", p.peek().Span)
		return nil
	}

	typeAnn := p.parseTypeAnnotation()
	if typeAnn == nil {
		p.addError("P021", "expected type in variable declaration", p.peek().Span)
		return nil
	}

	// Check for nullable suffix '?'
	typeAnn = p.parseNullableSuffix(typeAnn)

	decl := &ast.VariableDecl{
		SpanNode: ast.WithSpan(nameTok.Span),
		Name:     nameTok.Lexeme,
		Type:     typeAnn,
	}

	if p.match(lexer.TokenAssign) {
		decl.InitExpr = p.parseExpression()
	}

	return decl
}

// parseSwitchStmt parses a switch/case/default statement.
func (p *Parser) parseSwitchStmt() ast.Statement {
	startSpan := p.previous().Span

	// Parse the switch expression
	switchExpr := p.parseExpression()

	// Expect '{' to start the case block
	if !p.match(lexer.TokenLBrace) {
		p.addError("P050", "expected '{' after switch expression", p.peek().Span)
		return &ast.SwitchStmt{SpanNode: ast.WithSpan(startSpan), Expression: switchExpr}
	}

	stmt := &ast.SwitchStmt{
		SpanNode:   ast.WithSpan(startSpan),
		Expression: switchExpr,
	}

	hasDefault := false

	// Parse case clauses until '}'
	for !p.check(lexer.TokenRBrace) && !p.isAtEnd() {
		// Skip newlines before cases
		for p.match(lexer.TokenNewline) {
		}

		if p.check(lexer.TokenRBrace) || p.isAtEnd() {
			break
		}

		if p.match(lexer.TokenCase) {
			caseNode := ast.SwitchCase{SpanNode: ast.WithSpan(p.previous().Span)}

			// Parse the case expression
			caseNode.Expression = p.parseExpression()

			// Expect ':' after the case expression
			if !p.match(lexer.TokenColon) {
				p.addError("P051", "expected ':' after case expression", p.peek().Span)
				if !p.check(lexer.TokenRBrace) && !p.isAtEnd() {
					p.advance()
				}
				continue
			}

			// Parse the case body
			caseNode.Body = p.parseCaseBody()
			stmt.Cases = append(stmt.Cases, caseNode)
		} else if p.match(lexer.TokenDefault) {
			if hasDefault {
				p.addError("P052", "duplicate default clause in switch", p.peek().Span)
				if !p.check(lexer.TokenRBrace) && !p.isAtEnd() {
					p.advance()
				}
				continue
			}
			hasDefault = true

			// Expect ':' after default
			if !p.match(lexer.TokenColon) {
				p.addError("P053", "expected ':' after default", p.peek().Span)
				if !p.check(lexer.TokenRBrace) && !p.isAtEnd() {
					p.advance()
				}
				continue
			}

			// Parse the default body
			stmt.Default = p.parseCaseBody()
		} else {
			p.addError("P054", "expected 'case' or 'default' in switch", p.peek().Span)
			p.advance()
		}
	}

	// Consume the closing '}'
	if !p.match(lexer.TokenRBrace) {
		p.addError("P055", "expected '}' to close switch", p.peek().Span)
	} else {
		endSpan := p.previous().Span
		stmt.SpanNode = ast.WithSpan(source.SpanBetween(
			p.src.PosFromOffset(startSpan.Start),
			p.src.PosFromOffset(endSpan.End),
		))
	}

	return stmt
}

// parseCaseBody parses the body of a case clause.
func (p *Parser) parseCaseBody() *ast.Block {
	block := &ast.Block{}

	for !p.isAtEnd() {
		if p.check(lexer.TokenCase) || p.check(lexer.TokenDefault) || p.check(lexer.TokenRBrace) {
			break
		}

		if p.check(lexer.TokenNewline) || p.check(lexer.TokenSemicolon) {
			p.advance()
			continue
		}

		stmt := p.parseStatement()
		if stmt != nil {
			block.Statements = append(block.Statements, stmt)
		}

		if !p.check(lexer.TokenCase) && !p.check(lexer.TokenDefault) && !p.check(lexer.TokenRBrace) && !p.isAtEnd() {
			p.expectNewlineOrSemicolon()
		}
	}

	return block
}

// parseIfStmt parses an if/else if/else statement.
func (p *Parser) parseIfStmt() *ast.IfStmt {
	startSpan := p.previous().Span

	condition := p.parseExpression()

	if !p.match(lexer.TokenLBrace) {
		p.addError("P024", "expected '{' for if body", p.peek().Span)
		// Skip past the erroneous brace-less body to avoid cascading errors
		p.skipToBodyEnd()
		return &ast.IfStmt{
			Condition: condition,
			Then:      &ast.Block{SpanNode: ast.WithSpan(startSpan)},
			SpanNode:  ast.WithSpan(startSpan),
		}
	}

	thenBlock := p.parseBlock()

	stmt := &ast.IfStmt{
		SpanNode:  ast.WithSpan(startSpan),
		Condition: condition,
		Then:      thenBlock,
	}

	// Parse else if / else
	for p.match(lexer.TokenNewline) || p.match(lexer.TokenSemicolon) {
		// Skip newlines before else
	}

	if p.match(lexer.TokenElse) {
		if p.match(lexer.TokenIf) {
			// else if
			elseIf := p.parseIfStmt()
			if elseIf != nil {
				stmt.ElseIf = append(stmt.ElseIf, elseIf)
			}
		} else if p.match(lexer.TokenLBrace) {
			// else
			stmt.Else = p.parseBlock()
		} else {
			p.addError("P025", "expected 'if' or '{' after 'else'", p.peek().Span)
			// Skip past the erroneous brace-less body to avoid cascading errors
			p.skipToBodyEnd()
		}
	}

	return stmt
}

// parseWhileStmt parses a while loop.
func (p *Parser) parseWhileStmt() *ast.WhileStmt {
	startSpan := p.previous().Span

	condition := p.parseExpression()

	if !p.match(lexer.TokenLBrace) {
		p.addError("P028", "expected '{' for while body", p.peek().Span)
		// Skip past the erroneous brace-less body to avoid cascading errors
		p.skipToBodyEnd()
		return &ast.WhileStmt{
			Condition: condition,
			Body:      &ast.Block{SpanNode: ast.WithSpan(startSpan)},
			SpanNode:  ast.WithSpan(startSpan),
		}
	}

	body := p.parseBlock()

	return &ast.WhileStmt{
		SpanNode:  ast.WithSpan(startSpan),
		Condition: condition,
		Body:      body,
	}
}

// parseForStmt parses a for-in loop.
func (p *Parser) parseForStmt() *ast.ForStmt {
	startSpan := p.previous().Span
	node := &ast.ForStmt{SpanNode: ast.WithSpan(startSpan)}

	// Check for (key, value) unpacking syntax
	if p.match(lexer.TokenLParen) {
		if !p.check(lexer.TokenIdentifier) {
			p.addError("P030", "expected key variable name in for", p.peek().Span)
			return nil
		}
		node.Variable = p.advance().Lexeme

		if !p.match(lexer.TokenComma) {
			p.addError("P046", "expected ',' after key variable in for", p.peek().Span)
			return nil
		}

		if !p.check(lexer.TokenIdentifier) {
			p.addError("P047", "expected value variable name in for", p.peek().Span)
			return nil
		}
		node.ValueVariable = p.advance().Lexeme

		if !p.match(lexer.TokenRParen) {
			p.addError("P048", "expected ')' after for variables", p.peek().Span)
			return nil
		}
	} else {
		// Single variable
		if !p.check(lexer.TokenIdentifier) {
			p.addError("P030", "expected loop variable name", p.peek().Span)
			return nil
		}
		node.Variable = p.advance().Lexeme
	}

	if !p.match(lexer.TokenIn) {
		p.addError("P031", "expected 'in' after loop variable", p.peek().Span)
	}

	node.Iterable = p.parseExpression()

	if !p.match(lexer.TokenLBrace) {
		p.addError("P033", "expected '{' for for body", p.peek().Span)
		// Skip past the erroneous brace-less body to avoid cascading errors
		p.skipToBodyEnd()
		node.Body = &ast.Block{SpanNode: ast.WithSpan(startSpan)}
		return node
	}

	node.Body = p.parseBlock()

	return node
}

// parseExprStmt parses an expression or assignment statement.
func (p *Parser) parseExprStmt() *ast.ExprStmt {
	start := p.pos
	expr := p.parseExpression()
	if expr == nil {
		return nil
	}

	// Check for assignment: target = expression
	if p.match(lexer.TokenAssign) {
		// Accept identifier or index expression as assignment target
		var target ast.Expression
		switch e := expr.(type) {
		case *ast.Identifier:
			target = e
		case *ast.IndexExpr:
			target = e
		default:
			p.addError("P034", "cannot assign to non-identifier or non-index expression", p.peek().Span)
			// Still parse the value to keep going
			p.parseExpression()
			return nil
		}
		assignSpan := source.SpanBetween(
			p.src.PosFromOffset(expr.Span().Start),
			p.src.PosFromOffset(p.peek().Span.End),
		)
		value := p.parseExpression()
		return &ast.ExprStmt{
			SpanNode: ast.WithSpan(assignSpan),
			Expr: &ast.BinaryExpr{
				SpanNode: ast.WithSpan(assignSpan),
				Operator: ast.BinAssign,
				Left:     target,
				Right:    value,
			},
		}
	}

	// Restore position - we didn't need to peek ahead
	_ = start

	return &ast.ExprStmt{
		SpanNode: ast.WithSpan(expr.Span()),
		Expr:     expr,
	}
}

// --- Expression parsing with Pratt/precedence climbing ---

type prefixFn func() ast.Expression
type infixFn func(left ast.Expression) ast.Expression

// Precedence levels
const (
	PrecLowest     = iota
	PrecAssignment // =
	PrecCoalescing // ??
	PrecOr         // ||
	PrecAnd        // &&
	PrecEquality   // == !=
	PrecComparison // < <= > >=
	PrecBitOr      // |
	PrecBitXor     // ^
	PrecBitAnd     // &
	PrecShift      // << >>
	PrecRange      // ..
	PrecTerm       // + -
	PrecFactor     // * / %
	PrecUnary      // ! - ~
	PrecPrimary    // literals, identifiers, calls, indexing
)

// precedence returns the precedence of a binary operator token.
func precedence(kind lexer.TokenKind) int {
	switch kind {
	case lexer.TokenAssign:
		return PrecAssignment
	case lexer.TokenNullCoalesce:
		return PrecCoalescing
	case lexer.TokenOr:
		return PrecOr
	case lexer.TokenAnd:
		return PrecAnd
	case lexer.TokenEq, lexer.TokenNe:
		return PrecEquality
	case lexer.TokenLt, lexer.TokenLe, lexer.TokenGt, lexer.TokenGe:
		return PrecComparison
	case lexer.TokenBitOr:
		return PrecBitOr
	case lexer.TokenBitXor:
		return PrecBitXor
	case lexer.TokenBitAnd:
		return PrecBitAnd
	case lexer.TokenShiftLeft, lexer.TokenShiftRight:
		return PrecShift
	case lexer.TokenPlus, lexer.TokenMinus:
		return PrecTerm
	case lexer.TokenStar, lexer.TokenSlash, lexer.TokenPercent:
		return PrecFactor
	case lexer.TokenLParen, lexer.TokenLBracket, lexer.TokenDot:
		return PrecPrimary
	default:
		return PrecLowest
	}
}

// parseExpression parses an expression using Pratt parsing.
func (p *Parser) parseExpression() ast.Expression {
	return p.parsePrecedence(PrecLowest)
}

// parsePrecedence parses an expression with a given minimum precedence.
func (p *Parser) parsePrecedence(minPrec int) ast.Expression {
	prefix := p.parsePrefix()
	if prefix == nil {
		return nil
	}

	for {
		// Check for newlines that should be ignored (inside brackets, after operators)
		// After a prefix expression, newlines are NOT significant
		for p.match(lexer.TokenNewline) {
			// Skip newlines within expressions
		}

		kind := p.peek().Kind
		prec := precedence(kind)
		if prec <= minPrec {
			break
		}

		infix := p.parseInfix(prefix, kind)
		if infix == nil {
			break
		}
		prefix = infix
	}

	return prefix
}

// parsePrefix parses a prefix expression.
func (p *Parser) parsePrefix() ast.Expression {
	// Handle unary operators
	if p.match(lexer.TokenMinus) {
		opSpan := p.previous().Span
		operand := p.parsePrecedence(PrecUnary)
		if operand == nil {
			return nil
		}
		span := source.SpanBetween(p.src.PosFromOffset(opSpan.Start), p.src.PosFromOffset(operand.Span().End))
		return &ast.UnaryExpr{
			SpanNode: ast.WithSpan(span),
			Operator: ast.UnaryNegate,
			Operand:  operand,
		}
	}
	if p.match(lexer.TokenNot) {
		opSpan := p.previous().Span
		operand := p.parsePrecedence(PrecUnary)
		if operand == nil {
			return nil
		}
		span := source.SpanBetween(p.src.PosFromOffset(opSpan.Start), p.src.PosFromOffset(operand.Span().End))
		return &ast.UnaryExpr{
			SpanNode: ast.WithSpan(span),
			Operator: ast.UnaryNot,
			Operand:  operand,
		}
	}
	if p.match(lexer.TokenBitNot) {
		opSpan := p.previous().Span
		operand := p.parsePrecedence(PrecUnary)
		if operand == nil {
			return nil
		}
		span := source.SpanBetween(p.src.PosFromOffset(opSpan.Start), p.src.PosFromOffset(operand.Span().End))
		return &ast.UnaryExpr{
			SpanNode: ast.WithSpan(span),
			Operator: ast.UnaryBitNot,
			Operand:  operand,
		}
	}

	// Primary expressions
	if tok := p.advanceIf(lexer.TokenIntLiteral); tok != nil {
		val, err := strconv.ParseInt(tok.Lexeme, 10, 32)
		if err != nil {
			p.diags.AddError("P035", fmt.Sprintf("invalid integer literal: %s", err), tok.Span)
			return nil
		}
		return &ast.IntLiteral{SpanNode: ast.WithSpan(tok.Span), Value: int32(val)}
	}
	if tok := p.advanceIf(lexer.TokenLongLiteral); tok != nil {
		val, err := strconv.ParseInt(tok.Lexeme, 10, 64)
		if err != nil {
			p.addError("P036", fmt.Sprintf("invalid long literal: %s", err), tok.Span)
			return nil
		}
		return &ast.LongLiteral{SpanNode: ast.WithSpan(tok.Span), Value: val}
	}
	if tok := p.advanceIf(lexer.TokenFloatLiteral); tok != nil {
		val, err := strconv.ParseFloat(tok.Lexeme, 32)
		if err != nil {
			p.addError("P037", fmt.Sprintf("invalid float literal: %s", err), tok.Span)
			return nil
		}
		return &ast.FloatLiteral{SpanNode: ast.WithSpan(tok.Span), Value: float32(val)}
	}
	if tok := p.advanceIf(lexer.TokenDoubleLiteral); tok != nil {
		val, err := strconv.ParseFloat(tok.Lexeme, 64)
		if err != nil {
			p.addError("P038", fmt.Sprintf("invalid double literal: %s", err), tok.Span)
			return nil
		}
		return &ast.DoubleLiteral{SpanNode: ast.WithSpan(tok.Span), Value: val}
	}
	if tok := p.advanceIf(lexer.TokenBoolLiteral); tok != nil {
		val := tok.Lexeme == "true"
		return &ast.BoolLiteral{SpanNode: ast.WithSpan(tok.Span), Value: val}
	}
	if tok := p.advanceIf(lexer.TokenCharLiteral); tok != nil {
		runes := []rune(tok.Lexeme)
		var val rune
		if len(runes) > 0 {
			val = runes[0]
		}
		return &ast.CharLiteral{SpanNode: ast.WithSpan(tok.Span), Value: val}
	}
	if tok := p.advanceIf(lexer.TokenStringLiteral); tok != nil {
		return &ast.StringLiteral{SpanNode: ast.WithSpan(tok.Span), Value: tok.Lexeme}
	}
	if p.match(lexer.TokenNull) {
		return &ast.NullLiteral{SpanNode: ast.WithSpan(p.previous().Span)}
	}

	// Identifier (including type keywords used as function names like string(), int())
	if tok := p.advanceIf(lexer.TokenIdentifier); tok != nil {
		return &ast.Identifier{SpanNode: ast.WithSpan(tok.Span), Name: tok.Lexeme}
	}
	if p.check(lexer.TokenString) || p.check(lexer.TokenInt) || p.check(lexer.TokenLong) ||
		p.check(lexer.TokenDouble) || p.check(lexer.TokenFloat) || p.check(lexer.TokenBool) ||
		p.check(lexer.TokenByte) || p.check(lexer.TokenChar) || p.check(lexer.TokenVoid) ||
		p.check(lexer.TokenList) || p.check(lexer.TokenMap) {
		tok := p.advance()
		return &ast.Identifier{SpanNode: ast.WithSpan(tok.Span), Name: tok.Lexeme}
	}

	// Grouping
	if p.match(lexer.TokenLParen) {
		expr := p.parseExpression()
		if expr == nil {
			return nil
		}
		if !p.match(lexer.TokenRParen) {
			p.addError("P039", "expected ')' after expression", p.peek().Span)
		}
		return expr
	}

	// List literal
	if p.match(lexer.TokenLBracket) {
		return p.parseListLiteral()
	}

	// Map literal
	if p.match(lexer.TokenLBrace) {
		return p.parseMapLiteral()
	}

	// Null coalescing prefix? no, it's an infix operator
	return nil
}

// parseInfix parses an infix expression.
func (p *Parser) parseInfix(left ast.Expression, kind lexer.TokenKind) ast.Expression {
	// Function call
	if kind == lexer.TokenLParen {
		// We need to consume the '('
		p.advance()
		return p.parseCallExpr(left)
	}

	// Member access: module.function
	if kind == lexer.TokenDot {
		p.advance()
		var memberName string
		var memberSpan source.Span

		// Accept identifiers and type/builtin keywords as member names
		// This allows core.bool, core.int, string.length, etc.
		if p.check(lexer.TokenIdentifier) || p.check(lexer.TokenBool) || p.check(lexer.TokenByte) ||
			p.check(lexer.TokenInt) || p.check(lexer.TokenLong) || p.check(lexer.TokenFloat) ||
			p.check(lexer.TokenDouble) || p.check(lexer.TokenChar) || p.check(lexer.TokenString) ||
			p.check(lexer.TokenVoid) || p.check(lexer.TokenList) || p.check(lexer.TokenMap) {
			memberTok := p.advance()
			memberSpan = memberTok.Span
			memberName = memberTok.Lexeme
			if memberTok.Kind == lexer.TokenBool {
				memberName = "bool"
			} else if memberTok.Kind == lexer.TokenByte {
				memberName = "byte"
			} else if memberTok.Kind == lexer.TokenInt {
				memberName = "int"
			} else if memberTok.Kind == lexer.TokenLong {
				memberName = "long"
			} else if memberTok.Kind == lexer.TokenFloat {
				memberName = "float"
			} else if memberTok.Kind == lexer.TokenDouble {
				memberName = "double"
			} else if memberTok.Kind == lexer.TokenChar {
				memberName = "char"
			} else if memberTok.Kind == lexer.TokenString {
				memberName = "string"
			} else if memberTok.Kind == lexer.TokenVoid {
				memberName = "void"
			} else if memberTok.Kind == lexer.TokenList {
				memberName = "List"
			} else if memberTok.Kind == lexer.TokenMap {
				memberName = "Map"
			} else {
				memberName = memberTok.Lexeme
			}
		} else {
			p.addError("P049", "expected member name after '.'", p.peek().Span)
			return left
		}

		span := source.SpanBetween(
			p.src.PosFromOffset(left.Span().Start),
			p.src.PosFromOffset(memberSpan.End),
		)
		return &ast.MemberExpr{
			SpanNode: ast.WithSpan(span),
			Object:   left,
			Member:   memberName,
		}
	}

	// Indexing
	if kind == lexer.TokenLBracket {
		p.advance()
		return p.parseIndexExpr(left)
	}

	// Null coalescing
	if kind == lexer.TokenNullCoalesce {
		p.advance()
		right := p.parsePrecedence(PrecCoalescing)
		if right == nil {
			return nil
		}
		span := source.SpanBetween(
			p.src.PosFromOffset(left.Span().Start),
			p.src.PosFromOffset(right.Span().End),
		)
		return &ast.NullCoalescing{
			SpanNode: ast.WithSpan(span),
			Left:     left,
			Right:    right,
		}
	}

	// Binary operators
	op := p.binaryOpFromToken(kind)
	if op < 0 {
		return nil
	}

	p.advance()
	prec := precedence(kind)

	// For assignment, use lower precedence for right-associativity
	rightPrec := prec
	if kind == lexer.TokenAssign {
		rightPrec = prec - 1
	}

	right := p.parsePrecedence(rightPrec)
	if right == nil {
		return nil
	}

	span := source.SpanBetween(
		p.src.PosFromOffset(left.Span().Start),
		p.src.PosFromOffset(right.Span().End),
	)

	return &ast.BinaryExpr{
		SpanNode: ast.WithSpan(span),
		Operator: op,
		Left:     left,
		Right:    right,
	}
}

// parseCallExpr parses a function call: function(args...)
func (p *Parser) parseCallExpr(function ast.Expression) *ast.CallExpr {
	startSpan := function.Span()
	call := &ast.CallExpr{
		SpanNode: ast.WithSpan(startSpan),
		Function: function,
	}

	// Parse arguments
	for !p.check(lexer.TokenRParen) && !p.isAtEnd() {
		// Skip newlines before arguments
		for p.match(lexer.TokenNewline) {
		}
		if p.check(lexer.TokenRParen) {
			break
		}
		if len(call.Args) > 0 {
			if !p.match(lexer.TokenComma) {
				break
			}
			// Allow trailing comma
			for p.match(lexer.TokenNewline) {
			}
			if p.check(lexer.TokenRParen) {
				break
			}
		}
		arg := p.parseExpression()
		if arg == nil {
			break
		}
		call.Args = append(call.Args, arg)
	}

	if !p.match(lexer.TokenRParen) {
		p.addError("P040", "expected ')' after arguments", p.peek().Span)
	} else {
		endSpan := p.previous().Span
		call.SpanNode = ast.WithSpan(source.SpanBetween(
			p.src.PosFromOffset(startSpan.Start),
			p.src.PosFromOffset(endSpan.End),
		))
	}

	return call
}

// parseIndexExpr parses an indexing expression: expr[index]
func (p *Parser) parseIndexExpr(target ast.Expression) *ast.IndexExpr {
	index := p.parseExpression()
	span := target.Span()
	if !p.match(lexer.TokenRBracket) {
		p.addError("P041", "expected ']' after index", p.peek().Span)
	} else {
		endSpan := p.previous().Span
		span = source.SpanBetween(
			p.src.PosFromOffset(span.Start),
			p.src.PosFromOffset(endSpan.End),
		)
	}
	return &ast.IndexExpr{
		SpanNode: ast.WithSpan(span),
		Target:   target,
		Index:    index,
	}
}

// parseListLiteral parses a list literal [a, b, c].
func (p *Parser) parseListLiteral() *ast.ListLiteral {
	startSpan := p.previous().Span
	list := &ast.ListLiteral{}

	for !p.check(lexer.TokenRBracket) && !p.isAtEnd() {
		for p.match(lexer.TokenNewline) {
		}
		if p.check(lexer.TokenRBracket) {
			break
		}
		if len(list.Elements) > 0 {
			if !p.match(lexer.TokenComma) {
				break
			}
			for p.match(lexer.TokenNewline) {
			}
			if p.check(lexer.TokenRBracket) {
				break
			}
		}
		elem := p.parseExpression()
		if elem == nil {
			break
		}
		list.Elements = append(list.Elements, elem)
		for p.match(lexer.TokenNewline) {
		}
	}

	for p.match(lexer.TokenNewline) {
	}
	if !p.match(lexer.TokenRBracket) {
		p.addError("P042", "expected ']' to close list literal", p.peek().Span)
	} else {
		endSpan := p.previous().Span
		list.SpanNode = ast.WithSpan(source.SpanBetween(
			p.src.PosFromOffset(startSpan.Start),
			p.src.PosFromOffset(endSpan.End),
		))
	}

	return list
}

// parseMapLiteral parses a map literal {"key": value}.
// We check if this IS a map literal (not a block) by looking for
// an expression followed by ':' as the first entry.
func (p *Parser) parseMapLiteral() *ast.MapLiteral {
	startSpan := p.previous().Span
	m := &ast.MapLiteral{}

	// First, we must determine if this is actually a map literal or a statement block.
	// After '{', if we see an expression followed by ':', it's a map literal.
	// Otherwise, we treat it as a block (handled by parseBlock).
	// Since parseMapLiteral is only called from parsePrefix when we know it's
	// an expression context (not a statement), we parse map entries here.

	for !p.check(lexer.TokenRBrace) && !p.isAtEnd() {
		for p.match(lexer.TokenNewline) {
		}
		if p.check(lexer.TokenRBrace) {
			break
		}
		if len(m.Keys) > 0 {
			if !p.match(lexer.TokenComma) {
				break
			}
			for p.match(lexer.TokenNewline) {
			}
			if p.check(lexer.TokenRBrace) {
				break
			}
		}
		// Parse a key expression; it must be followed by ':' to be a map entry
		key := p.parseExpression()
		if key == nil {
			break
		}
		if !p.match(lexer.TokenColon) {
			// Not a map entry - this isn't a map literal after all
			// Push back the key expression by resetting position
			// Since we can't easily push back, we just break and treat as empty map
			// The caller (parsePrefix) should have determined this is a map literal
			p.addError("P043", "expected ':' after map key", p.peek().Span)
			break
		}
		value := p.parseExpression()
		if value == nil {
			break
		}
		m.Keys = append(m.Keys, key)
		m.Values = append(m.Values, value)
		for p.match(lexer.TokenNewline) {
		}
	}

	for p.match(lexer.TokenNewline) {
	}
	if !p.match(lexer.TokenRBrace) {
		p.addError("P044", "expected '}' to close map literal", p.peek().Span)
	} else {
		endSpan := p.previous().Span
		m.SpanNode = ast.WithSpan(source.SpanBetween(
			p.src.PosFromOffset(startSpan.Start),
			p.src.PosFromOffset(endSpan.End),
		))
	}

	return m
}

// binaryOpFromToken converts a token kind to a binary operator.
func (p *Parser) binaryOpFromToken(kind lexer.TokenKind) ast.BinOp {
	switch kind {
	case lexer.TokenPlus:
		return ast.BinAdd
	case lexer.TokenMinus:
		return ast.BinSub
	case lexer.TokenStar:
		return ast.BinMul
	case lexer.TokenSlash:
		return ast.BinDiv
	case lexer.TokenPercent:
		return ast.BinMod
	case lexer.TokenEq:
		return ast.BinEq
	case lexer.TokenNe:
		return ast.BinNe
	case lexer.TokenLt:
		return ast.BinLt
	case lexer.TokenLe:
		return ast.BinLe
	case lexer.TokenGt:
		return ast.BinGt
	case lexer.TokenGe:
		return ast.BinGe
	case lexer.TokenAnd:
		return ast.BinAnd
	case lexer.TokenOr:
		return ast.BinOr
	case lexer.TokenBitAnd:
		return ast.BinBitAnd
	case lexer.TokenBitOr:
		return ast.BinBitOr
	case lexer.TokenBitXor:
		return ast.BinBitXor
	case lexer.TokenShiftLeft:
		return ast.BinShiftLeft
	case lexer.TokenShiftRight:
		return ast.BinShiftRight
	case lexer.TokenAssign:
		return ast.BinAssign // will be handled specially
	default:
		return -1
	}
}

// skipToBodyEnd skips tokens until a statement boundary or 'else' keyword.
// Used for error recovery when a required '{' is missing — this prevents
// the brace-less body statement from leaking into subsequent parsing.
func (p *Parser) skipToBodyEnd() {
	for !p.isAtEnd() {
		if p.check(lexer.TokenNewline) || p.check(lexer.TokenSemicolon) ||
			p.check(lexer.TokenRBrace) || p.check(lexer.TokenElse) {
			return
		}
		p.advance()
	}
}

// addError records a parse error with the given code and message.
// Returns true if the maximum error count has been reached.
func (p *Parser) addError(code, message string, span source.Span) bool {
	p.diags.AddError(code, message, span)
	p.errCount++
	return p.errCount >= p.maxErrors
}

// --- Helper methods ---

func (p *Parser) isAtEnd() bool {
	return p.pos >= len(p.tokens) || p.tokens[p.pos].Kind == lexer.TokenEOF
}

func (p *Parser) peek() lexer.Token {
	if p.pos >= len(p.tokens) {
		return lexer.Token{Kind: lexer.TokenEOF}
	}
	return p.tokens[p.pos]
}

func (p *Parser) previous() lexer.Token {
	if p.pos <= 0 {
		return lexer.Token{Kind: lexer.TokenEOF}
	}
	return p.tokens[p.pos-1]
}

func (p *Parser) advance() lexer.Token {
	if p.pos >= len(p.tokens) {
		return lexer.Token{Kind: lexer.TokenEOF}
	}
	tok := p.tokens[p.pos]
	p.pos++
	return tok
}

func (p *Parser) advanceIf(kind lexer.TokenKind) *lexer.Token {
	if p.check(kind) {
		tok := p.advance()
		return &tok
	}
	return nil
}

func (p *Parser) check(kind lexer.TokenKind) bool {
	return !p.isAtEnd() && p.tokens[p.pos].Kind == kind
}

func (p *Parser) match(kind lexer.TokenKind) bool {
	if p.check(kind) {
		p.advance()
		return true
	}
	return false
}

// checkNewlineOrSemicolon returns true if the next token is a newline or semicolon.
func (p *Parser) checkNewlineOrSemicolon() bool {
	return p.check(lexer.TokenNewline) || p.check(lexer.TokenSemicolon)
}

// expectNewlineOrSemicolon consumes a newline or semicolon if present.
// If neither is found, no error is reported (the caller handles error context).
func (p *Parser) expectNewlineOrSemicolon() {
	if p.match(lexer.TokenNewline) || p.match(lexer.TokenSemicolon) {
		return
	}
	// Skip any remaining newlines
	for p.match(lexer.TokenNewline) {
	}
}

// synchronize skips tokens until a statement boundary (for error recovery).
func (p *Parser) synchronize() {
	for !p.isAtEnd() {
		if p.match(lexer.TokenNewline) || p.match(lexer.TokenSemicolon) {
			return
		}
		switch p.peek().Kind {
		case lexer.TokenFunc, lexer.TokenIf, lexer.TokenWhile, lexer.TokenFor,
			lexer.TokenSwitch, lexer.TokenCase, lexer.TokenDefault,
			lexer.TokenReturn, lexer.TokenBreak, lexer.TokenContinue,
			lexer.TokenPackage, lexer.TokenImport, lexer.TokenRBrace:
			return
		}
		p.advance()
	}
}

// skipToStatementBoundary skips tokens until a newline/semicolon or block end.
func (p *Parser) skipToStatementBoundary() {
	for !p.isAtEnd() {
		if p.match(lexer.TokenNewline) || p.match(lexer.TokenSemicolon) {
			return
		}
		if p.check(lexer.TokenRBrace) {
			return
		}
		p.advance()
	}
}

// matchGt matches '>' or '>>' as a closing angle bracket for type annotations.
// This handles nested generics like List<List<int>> where >> is lexed as a single token.
func (p *Parser) matchGt() bool {
	if p.match(lexer.TokenGt) {
		return true
	}
	if p.match(lexer.TokenShiftRight) {
		// Split >> into two > tokens (conceptually)
		// Since we can't push back, we consume it and treat it as a single >
		// The outer type annotation will need to handle the missing >
		return true
	}
	return false
}
