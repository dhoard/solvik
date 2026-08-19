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
	"encoding/hex"
	"fmt"
	"strconv"
	"strings"

	"github.com/dhoard/solvik-language/internal/ast"
	"github.com/dhoard/solvik-language/internal/diagnostic"
	"github.com/dhoard/solvik-language/internal/lexer"
	"github.com/dhoard/solvik-language/internal/source"
	"github.com/dhoard/solvik-language/internal/types"
)

// Parser performs syntactic analysis.
type Parser struct {
	src                *source.Source
	tokens             []lexer.Token
	pos                int
	diags              *diagnostic.Diagnostics
	errCount           int
	maxErrors          int
	seenPackage        bool // true after package declaration is parsed
	seenFunc           bool // true after first function declaration is parsed
	allowStructLiteral bool
	typeDepth          int
	pendingTypeGts     int
}

const defaultMaxErrors = 50

// New creates a new parser.
func New(src *source.Source, tokens []lexer.Token) *Parser {
	return &Parser{
		src:                src,
		tokens:             tokens,
		diags:              diagnostic.NewDiagnostics(),
		maxErrors:          defaultMaxErrors,
		allowStructLiteral: true,
	}
}

// Parse parses the token stream and returns the AST.
func (p *Parser) Parse() (*ast.Program, *diagnostic.Diagnostics) {
	prog := &ast.Program{}

	// Parse package declaration
	// Must skip leading newlines (blank lines allowed before package)
	for p.match(lexer.TokenNewline) {
	}

	if p.match(lexer.TokenPackage) {
		p.seenPackage = true
		if p.check(lexer.TokenIdentifier) {
			prog.Module = p.parseDottedName()
		} else {
			p.addError("P001", "expected module name after 'package'", p.peek().Span)
		}
		p.expectNewlineOrSemicolon()
	}

	// Parse use declarations and functions
	for !p.isAtEnd() && p.errCount < p.maxErrors {
		if p.match(lexer.TokenPackage) {
			if !p.seenPackage {
				// First package encountered in the loop (after leading newlines)
				p.seenPackage = true
				if p.check(lexer.TokenIdentifier) {
					prog.Module = p.parseDottedName()
				} else {
					p.addError("P001", "expected module name after 'package'", p.peek().Span)
				}
				p.expectNewlineOrSemicolon()
			} else {
				p.addError("P049", "duplicate 'package' declaration", p.peek().Span)
				if p.check(lexer.TokenIdentifier) {
					p.advance()
				}
				p.expectNewlineOrSemicolon()
			}
		} else if p.match(lexer.TokenUse) {
			if !p.seenPackage {
				p.addError("P048", "file must start with a 'package' declaration", p.peek().Span)
			} else if p.seenFunc {
				p.addError("P050", "'use' declaration must appear before any function declaration", p.peek().Span)
			}
			useDecl := p.parseUse()
			if useDecl != nil {
				prog.Uses = append(prog.Uses, useDecl)
			}
			p.expectNewlineOrSemicolon()
		} else if p.match(lexer.TokenEnum) {
			if !p.seenPackage {
				p.addError("P048", "file must start with a 'package' declaration", p.peek().Span)
			}
			enumDecl := p.parseEnumDecl()
			if enumDecl != nil {
				prog.Enums = append(prog.Enums, enumDecl)
			}
			p.expectNewlineOrSemicolon()
		} else if p.match(lexer.TokenStruct) {
			if !p.seenPackage {
				p.addError("P048", "file must start with a 'package' declaration", p.peek().Span)
			}
			structDecl := p.parseStructDecl()
			if structDecl != nil {
				prog.Structs = append(prog.Structs, structDecl)
			}
			p.expectNewlineOrSemicolon()
		} else if p.match(lexer.TokenTrait) {
			if !p.seenPackage {
				p.addError("P048", "file must start with a 'package' declaration", p.peek().Span)
			}
			traitDecl := p.parseTraitDecl()
			if traitDecl != nil {
				prog.Traits = append(prog.Traits, traitDecl)
			}
			p.expectNewlineOrSemicolon()
		} else if p.match(lexer.TokenFunc) {
			if !p.seenPackage {
				p.addError("P048", "file must start with a 'package' declaration", p.peek().Span)
			}
			p.seenFunc = true
			fn := p.parseFunction()
			if fn != nil {
				prog.Funcs = append(prog.Funcs, fn)
			}
			p.expectNewlineOrSemicolon()
		} else if p.match(lexer.TokenNewline) || p.match(lexer.TokenSemicolon) {
			// Skip blank lines
		} else {
			if !p.seenPackage {
				p.addError("P048", "file must start with a 'package' declaration", p.peek().Span)
			} else {
				p.addError("P002", "expected function declaration or use", p.peek().Span)
			}
			p.synchronize()
		}
	}

	if !p.seenPackage {
		p.addError("P048", "file must start with a 'package' declaration", p.peek().Span)
	}

	if p.errCount >= p.maxErrors {
		p.diags.AddError("P045", "too many parse errors; stopping", source.Span{})
	}

	return prog, p.diags
}

// parseUse parses:
//
//	use url:<url> checksum:sha256:<hex> insecure:true
//
// URL, file, and checksum values may be quoted or unquoted. Quoted values
// use the normal or raw string literal syntax and are useful when a value
// contains whitespace or other characters that need escaping.
func (p *Parser) parseUse() *ast.UseDecl {
	decl := &ast.UseDecl{
		SpanNode: ast.WithSpan(p.previous().Span),
	}

	// Parse url: or file: prefix
	if !p.check(lexer.TokenIdentifier) {
		p.addError("P100", "expected 'url:' or 'file:' after 'use'", p.peek().Span)
		return nil
	}
	srcTok := p.advance()
	if !p.match(lexer.TokenColon) {
		p.addError("P102", "expected ':' after source type", p.peek().Span)
		return nil
	}
	switch srcTok.Lexeme {
	case "url":
		decl.SourceType = "url"
	case "file":
		decl.SourceType = "file"
	default:
		p.addError("P103", "expected 'url:' or 'file:', got '"+srcTok.Lexeme+":'", srcTok.Span)
		return nil
	}

	// Parse the value — quoted, raw, or an unquoted value through the end of
	// the token. The latter is needed for URL punctuation such as : and /,
	// which are not part of Solvik identifiers.
	path, _, ok := p.parseUseValue()
	if !ok {
		p.addError("P101", "expected path value after '"+decl.SourceType+":'", p.peek().Span)
		return nil
	}
	decl.Path = path

	// Parse optional flags
	seenChecksum := false
	seenInsecure := false
	for !p.checkNewlineOrSemicolon() && !p.isAtEnd() {
		if !p.check(lexer.TokenIdentifier) {
			p.addError("P104", "expected flag name after use value", p.peek().Span)
			p.advance()
			continue
		}
		flagTok := p.advance()
		if !p.match(lexer.TokenColon) {
			p.addError("P105", "expected ':' after flag name", p.peek().Span)
			break
		}
		switch flagTok.Lexeme {
		case "checksum":
			if seenChecksum {
				p.addError("P106", "duplicate 'checksum' flag", flagTok.Span)
			}
			seenChecksum = true

			checksumValue, checksumSpan, checksumOK := p.parseChecksumValue()
			if !checksumOK {
				p.addError("P107", "expected checksum in the form 'sha256:<64-hex>'", p.peek().Span)
				break
			}
			checksum, valid := normalizeChecksum(checksumValue)
			if !valid {
				p.addError("P108", "checksum must use the form 'sha256:<64-hex>'", checksumSpan)
				break
			}
			decl.Checksum = checksum

		case "insecure":
			if seenInsecure {
				p.addError("P109", "duplicate 'insecure' flag", flagTok.Span)
			}
			seenInsecure = true
			if !p.check(lexer.TokenBoolLiteral) {
				p.addError("P110", "expected 'true' or 'false' after 'insecure:'", p.peek().Span)
				break
			}
			valTok := p.advance()
			switch valTok.Lexeme {
			case "true":
				decl.Insecure = true
			case "false":
				decl.Insecure = false
			}

		default:
			p.addError("P111", fmt.Sprintf("unknown flag '%s'", flagTok.Lexeme), flagTok.Span)
		}
	}

	// Validate
	if decl.SourceType == "url" {
		if strings.HasPrefix(decl.Path, "http://") && !decl.Insecure {
			p.addError("P112", "http URLs require insecure:true flag", decl.Span())
		}
		if strings.HasPrefix(decl.Path, "https://") && decl.Checksum == "" && !decl.Insecure {
			p.addError("P113", "sha-256 checksum or insecure:true is required for https URLs", decl.Span())
		}
	}

	return decl
}

// parseUseValue parses one use-directive value. A quoted or raw string is
// already represented by one lexer token; an unquoted value is recovered from
// the source until whitespace or a statement terminator so URL punctuation is
// preserved verbatim.
func (p *Parser) parseUseValue() (string, source.Span, bool) {
	if p.check(lexer.TokenStringLiteral) {
		tok := p.advance()
		return tok.Lexeme, tok.Span, true
	}
	if p.isAtEnd() || p.checkNewlineOrSemicolon() {
		return "", p.peek().Span, false
	}

	start := p.peek().Span.Start
	end := start
	for end < len(p.src.Content) && !isUseValueTerminator(p.src.Content[end]) {
		end++
	}
	if end == start {
		return "", p.peek().Span, false
	}

	for !p.isAtEnd() && p.peek().Span.Start < end {
		p.advance()
	}
	return string(p.src.Content[start:end]), p.src.SpanFromRange(start, end), true
}

// parseChecksumValue accepts both checksum:sha256:<hex> and
// checksum:"sha256:<hex>". The form checksum:sha256:"<hex>" is accepted too
// so only the checksum payload needs quoting when necessary.
func (p *Parser) parseChecksumValue() (string, source.Span, bool) {
	if p.check(lexer.TokenIdentifier) && p.peek().Lexeme == "sha256" {
		algorithm := p.advance()
		if !p.match(lexer.TokenColon) {
			return "", algorithm.Span, false
		}
		value, span, ok := p.parseUseValue()
		if !ok {
			return "", span, false
		}
		return "sha256:" + value, p.src.SpanFromRange(algorithm.Span.Start, span.End), true
	}
	return p.parseUseValue()
}

func normalizeChecksum(value string) (string, bool) {
	if !strings.HasPrefix(value, "sha256:") {
		return "", false
	}
	checksum := strings.ToLower(strings.TrimPrefix(value, "sha256:"))
	if len(checksum) != 64 {
		return "", false
	}
	if _, err := hex.DecodeString(checksum); err != nil {
		return "", false
	}
	return checksum, true
}

func isUseValueTerminator(ch byte) bool {
	switch ch {
	case ' ', '\t', '\n', '\r', ';':
		return true
	default:
		return false
	}
}

// parseDottedName reads a sequence of dot-separated identifiers:
//
//	foo, foo.bar, foo.bar.baz
//
// Returns the joined dotted string.
func (p *Parser) parseDottedName() string {
	var parts []string
	parts = append(parts, p.advance().Lexeme)
	for p.match(lexer.TokenDot) {
		if !p.check(lexer.TokenIdentifier) {
			p.addError("P095", "expected identifier after '.'", p.peek().Span)
			break
		}
		parts = append(parts, p.advance().Lexeme)
	}
	return strings.Join(parts, ".")
}

// parseEnumDecl parses: enum Name { Var1, Var2, Var3 = 5, }
func (p *Parser) parseEnumDecl() *ast.EnumDecl {
	// Enum name
	if !p.check(lexer.TokenIdentifier) {
		p.addError("P076", "expected enum name", p.peek().Span)
		return nil
	}
	nameTok := p.advance()

	enumDecl := &ast.EnumDecl{
		SpanNode: ast.WithSpan(nameTok.Span),
		Name:     nameTok.Lexeme,
	}

	// Expect '{'
	if !p.match(lexer.TokenLBrace) {
		p.addError("P081", "expected '{' after enum name", p.peek().Span)
		return nil
	}

	// Skip newlines after '{'
	for p.match(lexer.TokenNewline) {
	}

	// Parse variants
	for !p.check(lexer.TokenRBrace) && !p.isAtEnd() {
		// Skip newlines inside the enum block
		for p.match(lexer.TokenNewline) {
		}

		// Check for closing brace
		if p.check(lexer.TokenRBrace) {
			break
		}

		// Variant name
		if !p.check(lexer.TokenIdentifier) {
			p.addError("P096", "expected variant name in enum", p.peek().Span)
			break
		}
		varTok := p.advance()
		variant := ast.EnumVariant{
			SpanNode: ast.WithSpan(varTok.Span),
			Name:     varTok.Lexeme,
		}

		// Optional '= <int-literal>'
		if p.match(lexer.TokenAssign) {
			if p.check(lexer.TokenIntLiteral) {
				valTok := p.advance()
				val := parseInt64(valTok.Lexeme)
				variant.Value = &val
			} else {
				p.addError("P097", "expected integer literal after '=' in enum variant", p.peek().Span)
			}
		}

		enumDecl.Variants = append(enumDecl.Variants, variant)

		// Comma separator (optional trailing comma)
		if !p.match(lexer.TokenComma) {
			break
		}
		// Allow trailing comma before '}' or semicolon
		if p.check(lexer.TokenRBrace) || p.check(lexer.TokenSemicolon) {
			break
		}
	}

	if !p.match(lexer.TokenRBrace) {
		p.addError("P084", "expected '}' after enum variants", p.peek().Span)
	}

	return enumDecl
}

// parseStructDecl parses: struct Name { field: Type, ... func ... }
func (p *Parser) parseStructDecl() *ast.StructDecl {
	// Struct name
	if !p.check(lexer.TokenIdentifier) {
		p.addError("P077", "expected struct name", p.peek().Span)
		return nil
	}
	nameTok := p.advance()

	structDecl := &ast.StructDecl{
		SpanNode: ast.WithSpan(nameTok.Span),
		Name:     nameTok.Lexeme,
	}

	// Expect '{'
	if !p.match(lexer.TokenLBrace) {
		p.addError("P082", "expected '{' after struct name", p.peek().Span)
		return nil
	}

	// Skip newlines after '{'
	for p.match(lexer.TokenNewline) {
	}

	// Parse fields and methods
	for !p.check(lexer.TokenRBrace) && !p.isAtEnd() {
		// Skip newlines inside the struct block
		for p.match(lexer.TokenNewline) {
		}

		// Check for closing brace
		if p.check(lexer.TokenRBrace) {
			break
		}

		// Check for pub keyword
		isPub := false
		if p.match(lexer.TokenPub) {
			isPub = true
		}

		// Check for mut keyword
		isMut := false
		if p.match(lexer.TokenMut) {
			isMut = true
		}

		// Check for func keyword (method)
		if p.match(lexer.TokenFunc) {
			fn := p.parseFunction()
			if fn != nil {
				fn.StructName = structDecl.Name
				fn.Name = structDecl.Name + "." + fn.Name
				fn.IsPub = isPub
				fn.IsMut = isMut
				// Add implicit struct parameter as first parameter
				selfParam := &ast.Parameter{
					SpanNode: fn.SpanNode,
					Name:     "_self",
					Type: &ast.TypeAnnotation{
						Kind:     types.KindStruct,
						TypeName: structDecl.Name,
					},
				}
				fn.Parameters = append([]*ast.Parameter{selfParam}, fn.Parameters...)
				structDecl.Methods = append(structDecl.Methods, fn)
			}
			continue
		}

		// If we consumed 'pub'/'mut' but didn't find func, it's a field
		// Parse field: name: Type
		if !p.check(lexer.TokenIdentifier) {
			p.addError("P098", "expected field name or 'func' in struct", p.peek().Span)
			p.synchronize()
			continue
		}
		fieldTok := p.advance()

		if !p.match(lexer.TokenColon) {
			p.addError("P087", "expected ':' after field name", p.peek().Span)
			p.synchronize()
			continue
		}

		typeAnn := p.parseTypeAnnotation()
		if typeAnn == nil {
			p.addError("P088", "expected field type", p.peek().Span)
			p.synchronize()
			continue
		}
		field := &ast.StructField{
			SpanNode: ast.WithSpan(fieldTok.Span),
			Name:     fieldTok.Lexeme,
			Type:     typeAnn,
			IsMut:    isMut,
			IsPub:    isPub,
		}
		structDecl.Fields = append(structDecl.Fields, field)

		// Comma separator (optional trailing comma)
		if !p.match(lexer.TokenComma) {
			// No comma — also accept newline as separator
			if !p.check(lexer.TokenRBrace) && !p.check(lexer.TokenNewline) {
				break
			}
		}
	}

	if !p.match(lexer.TokenRBrace) {
		p.addError("P085", "expected '}' after struct fields", p.peek().Span)
	}

	return structDecl
}

// parseTraitDecl parses: trait Name { func methodSignature ... }
func (p *Parser) parseTraitDecl() *ast.TraitDecl {
	// Trait name
	if !p.check(lexer.TokenIdentifier) {
		p.addError("P078", "expected trait name", p.peek().Span)
		return nil
	}
	nameTok := p.advance()

	traitDecl := &ast.TraitDecl{
		SpanNode: ast.WithSpan(nameTok.Span),
		Name:     nameTok.Lexeme,
	}

	// Expect '{'
	if !p.match(lexer.TokenLBrace) {
		p.addError("P083", "expected '{' after trait name", p.peek().Span)
		return nil
	}

	// Skip newlines after '{'
	for p.match(lexer.TokenNewline) {
	}

	// Parse method signatures
	for !p.check(lexer.TokenRBrace) && !p.isAtEnd() {
		// Skip newlines inside the trait block
		for p.match(lexer.TokenNewline) {
		}

		// Check for closing brace
		if p.check(lexer.TokenRBrace) {
			break
		}

		// A trait method is non-mutating by default. `mut func` carries the
		// receiver-mutability contract into implementations.
		isMut := p.match(lexer.TokenMut)

		// Expect 'func' keyword
		if !p.match(lexer.TokenFunc) {
			p.addError("P072", "expected 'func' in trait declaration", p.peek().Span)
			p.synchronize()
			continue
		}

		// Parse method signature: func name(params) -> ReturnType
		fn := p.parseTraitMethod()
		if fn != nil {
			fn.IsMut = isMut
			traitDecl.Methods = append(traitDecl.Methods, fn)
		}
	}

	if !p.match(lexer.TokenRBrace) {
		p.addError("P086", "expected '}' after trait methods", p.peek().Span)
	}

	return traitDecl
}

// parseTraitParser parses a method signature inside a trait (no body).
func (p *Parser) parseTraitMethod() *ast.Function {
	start := p.previous().Span

	// Method name
	if !p.check(lexer.TokenIdentifier) {
		p.addError("P079", "expected method name", p.peek().Span)
		return nil
	}
	nameTok := p.advance()

	fn := &ast.Function{
		SpanNode: ast.WithSpan(nameTok.Span),
		Name:     nameTok.Lexeme,
	}

	// Parameters
	if !p.match(lexer.TokenLParen) {
		p.addError("P080", "expected '(' after method name", p.peek().Span)
		p.synchronize()
		return nil
	}

	fn.Parameters = p.parseParameters()

	if !p.match(lexer.TokenRParen) {
		p.addError("P006", "expected ')' after parameters", p.peek().Span)
	}

	// Return type (optional)
	if p.match(lexer.TokenArrow) {
		fn.ReturnTypes = p.parseReturnTypes()
	}

	// No body — trait methods are abstract
	// Update span
	fn.SpanNode = ast.WithSpan(source.SpanBetween(
		p.src.PosFromOffset(start.Start),
		p.src.PosFromOffset(p.previous().Span.End),
	))

	return fn
}

// parseInt64 parses an int64 literal lexeme.
func parseInt64(lexeme string) int64 {
	// Strip underscores first
	cleaned := strings.ReplaceAll(lexeme, "_", "")
	// Use strconv with auto-detect (handles 0x, 0X, 0o prefixes)
	val, err := strconv.ParseInt(cleaned, 0, 64)
	if err != nil {
		return 0
	}
	return val
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

	// Return type (optional) — a function without an arrow returns void.
	if p.match(lexer.TokenArrow) {
		fn.ReturnTypes = p.parseReturnTypes()
	}

	// Body
	for p.match(lexer.TokenNewline) {
	}
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

		// Check for variadic '...' before the type
		variadic := p.match(lexer.TokenEllipsis)

		typeAnn := p.parseTypeAnnotation()
		if typeAnn == nil {
			p.addError("P010", "expected parameter type", p.peek().Span)
			break
		}

		if variadic && typeAnn != nil && typeAnn.Nullable {
			p.addError("P117", "variadic parameter cannot be nullable", nameTok.Span)
		}

		params = append(params, &ast.Parameter{
			SpanNode: ast.WithSpan(nameTok.Span),
			Name:     nameTok.Lexeme,
			Type:     typeAnn,
			Variadic: variadic,
		})

		// After a variadic parameter, no more params are allowed
		if variadic {
			// Consume optional trailing comma
			p.match(lexer.TokenComma)
			break
		}
	}
	return params
}

// parseTypeAnnotation parses a complete recursive type, including its
// nullable suffix. Generic closing delimiters are handled contextually so a
// lexer-produced >> can close two nested generic types.
func (p *Parser) parseTypeAnnotation() *ast.TypeAnnotation {
	topLevel := p.typeDepth == 0
	p.typeDepth++
	defer func() {
		p.typeDepth--
	}()

	t := p.parsePrimaryType()
	if t == nil {
		if p.check(lexer.TokenQuestion) {
			p.addError("P017", "expected type before '?'", p.peek().Span)
			p.advance()
		}
		return nil
	}

	// If a nested generic consumed >>, the pending closer belongs to the
	// parent type and must be consumed before a nullable suffix can be
	// considered. This keeps list<list<int>>? nullable at the outer level.
	if p.pendingTypeGts == 0 && p.match(lexer.TokenQuestion) {
		t.Nullable = true
	}

	// A remaining pending closer means >> appeared without enough nested
	// generic types to consume both delimiters. Report it at the outermost
	// type parse instead of leaking it into the following declaration.
	if topLevel && p.pendingTypeGts > 0 {
		p.addError("P091", "unexpected extra '>' in type", p.previous().Span)
		p.pendingTypeGts = 0
	}
	return t
}

// parsePrimaryType parses a type before its nullable suffix.
func (p *Parser) parsePrimaryType() *ast.TypeAnnotation {
	if p.match(lexer.TokenBool) {
		return &ast.TypeAnnotation{Kind: types.KindBool, SpanNode: ast.WithSpan(p.previous().Span)}
	}
	if p.match(lexer.TokenByte) {
		return &ast.TypeAnnotation{Kind: types.KindByte, SpanNode: ast.WithSpan(p.previous().Span)}
	}
	if p.match(lexer.TokenInt) {
		return &ast.TypeAnnotation{Kind: types.KindInt, SpanNode: ast.WithSpan(p.previous().Span)}
	}
	// Check for removed types 'long' and 'double' (now ordinary identifiers)
	if p.check(lexer.TokenIdentifier) && (p.peek().Lexeme == "long" || p.peek().Lexeme == "double") {
		tok := p.advance()
		if tok.Lexeme == "long" {
			p.addError("P071", "unknown type 'long'; use 'int', which is a signed 64-bit integer", tok.Span)
		} else {
			p.addError("P119", "unknown type 'double'; use 'float', which is a 64-bit floating-point type", tok.Span)
		}
		return &ast.TypeAnnotation{Kind: types.KindInvalid, SpanNode: ast.WithSpan(tok.Span)}
	}
	if p.match(lexer.TokenFloat) {
		return &ast.TypeAnnotation{Kind: types.KindFloat, SpanNode: ast.WithSpan(p.previous().Span)}
	}
	if p.match(lexer.TokenChar) {
		return &ast.TypeAnnotation{Kind: types.KindChar, SpanNode: ast.WithSpan(p.previous().Span)}
	}
	if p.match(lexer.TokenString) {
		return &ast.TypeAnnotation{Kind: types.KindString, SpanNode: ast.WithSpan(p.previous().Span)}
	}
	if p.match(lexer.TokenException) {
		return &ast.TypeAnnotation{Kind: types.KindException, SpanNode: ast.WithSpan(p.previous().Span)}
	}
	if p.match(lexer.TokenVoid) {
		return &ast.TypeAnnotation{Kind: types.KindVoid, SpanNode: ast.WithSpan(p.previous().Span)}
	}
	if p.match(lexer.TokenAny) {
		return &ast.TypeAnnotation{Kind: types.KindAny, SpanNode: ast.WithSpan(p.previous().Span)}
	}
	if p.match(lexer.TokenList) {
		if p.match(lexer.TokenLt) {
			elem := p.parseTypeAnnotation()
			if elem == nil {
				p.addError("P011", "expected element type in List<T>", p.peek().Span)
				return nil
			}
			if !p.matchTypeGt() {
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
	if p.match(lexer.TokenStack) {
		if p.match(lexer.TokenLt) {
			elem := p.parseTypeAnnotation()
			if elem == nil {
				p.addError("P089", "expected element type in Stack<T>", p.peek().Span)
				return nil
			}
			if !p.matchTypeGt() {
				p.addError("P090", "expected '>' after Stack element type", p.peek().Span)
			}
			return &ast.TypeAnnotation{
				Kind:     types.KindStack,
				Element:  elem,
				SpanNode: ast.WithSpan(p.previous().Span),
			}
		}
		return &ast.TypeAnnotation{Kind: types.KindStack, SpanNode: ast.WithSpan(p.previous().Span)}
	}
	// Check for user-defined type names (e.g., enum types like "Color")
	if p.match(lexer.TokenIdentifier) {
		return &ast.TypeAnnotation{
			Kind:     types.KindInvalid,
			TypeName: p.previous().Lexeme,
			SpanNode: ast.WithSpan(p.previous().Span),
		}
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
			if !p.matchTypeGt() {
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

	return nil
}

// parseReturnTypes parses the required value type after ->.
// Void functions omit the arrow entirely.
// Multiple return types are not supported - use a struct instead.
func (p *Parser) parseReturnTypes() []*ast.TypeAnnotation {
	var types []*ast.TypeAnnotation

	// A newline after the arrow is continuation because a return type is
	// required. Once skipped, a body or separator means the arrow is bare.
	for p.match(lexer.TokenNewline) {
	}

	// Void is represented internally, but is not valid source-level syntax.
	if p.check(lexer.TokenVoid) {
		p.addError(diagnostic.CodeParserBareReturnArrow,
			"void functions must omit the return arrow; use 'func name(...)'", p.peek().Span)
		p.advance()
		return nil
	}

	// A bare arrow is invalid.
	if p.check(lexer.TokenLBrace) || p.checkNewlineOrSemicolon() || p.isAtEnd() {
		p.addError(diagnostic.CodeParserBareReturnArrow,
			"expected a return type after '->'; omit the arrow for void functions", p.peek().Span)
		return nil
	}

	// Parse first type
	first := p.parseTypeAnnotation()
	if first == nil {
		return nil
	}
	types = append(types, first)

	// Reject multiple return types (comma after first type)
	if p.match(lexer.TokenComma) {
		p.addError("P070", "multiple return types are not supported; use a struct type instead", p.previous().Span)
		return types
	}

	return types
}

// parseCommaSeparatedExprList parses a comma-separated expression list.
// Supports: "expr", "expr, expr", "expr, expr,"
func (p *Parser) parseCommaSeparatedExprList() []ast.Expression {
	var exprs []ast.Expression

	first := p.parseExpression()
	if first == nil {
		return nil
	}
	exprs = append(exprs, first)

	for p.match(lexer.TokenComma) {
		// Check for trailing comma before newline/semicolon/}
		if p.checkNewlineOrSemicolon() || p.check(lexer.TokenRBrace) {
			break
		}
		next := p.parseExpression()
		if next == nil {
			break
		}
		exprs = append(exprs, next)
	}

	return exprs
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

	// Check for mut keyword (mutable declaration)
	isMut := false
	if p.match(lexer.TokenMut) {
		isMut = true
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
				stmt.IsMut = isMut
				return stmt
			}
			// parseVarDecl failed but may have partially consumed tokens.
			// Skip to end of line to avoid infinite loop.
			p.skipToStatementBoundary()
			return nil
		}
	}

	// If we consumed 'mut' but didn't find a declaration, report error
	if isMut {
		p.addError("P068", "mut requires a variable declaration", p.peek().Span)
		return nil
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

	if p.match(lexer.TokenTry) {
		return p.parseTryStmt()
	}

	if p.match(lexer.TokenThrow) {
		return p.parseThrowStmt()
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
			// Parse the first expression
			first := p.parseExpression()
			if first != nil {
				stmt.Values = append(stmt.Values, first)
				// Reject multiple return values (comma after first expression)
				if p.match(lexer.TokenComma) {
					p.addError("P120", "multiple return values are not supported; use a struct instead", p.previous().Span)
					// Continue parsing to report better errors
					for !p.checkNewlineOrSemicolon() && !p.check(lexer.TokenRBrace) && !p.isAtEnd() {
						if p.parseExpression() != nil {
							if !p.match(lexer.TokenComma) {
								break
							}
						} else {
							break
						}
					}
				}
			}
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

	decl := &ast.VariableDecl{
		SpanNode: ast.WithSpan(nameTok.Span),
		Name:     nameTok.Lexeme,
		Type:     typeAnn,
	}

	if p.match(lexer.TokenAssign) {
		p.skipExpressionNewlines()
		decl.InitExpr = p.parseExpression()
	}

	return decl
}

// parseSwitchStmt parses a switch/case/default statement.
func (p *Parser) parseSwitchStmt() ast.Statement {
	startSpan := p.previous().Span

	// Parse the switch expression
	switchExpr := p.parseHeaderExpression()

	// Expect '{' to start the case block
	p.skipExpressionNewlines()
	if !p.match(lexer.TokenLBrace) {
		p.addError("P116", "expected '{' after switch expression", p.peek().Span)
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
			p.skipExpressionNewlines()
			caseNode.Expression = p.parseExpression()

			// Colons are not part of switch case syntax. Detect the old form
			// explicitly so it does not get mistaken for the start of a body.
			if p.match(lexer.TokenColon) {
				p.addError("P051", "case labels do not use ':'; expected '{' after case expression", p.previous().Span)
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

			// Colons are not part of switch default syntax. Detect the old form
			// explicitly so it receives a useful diagnostic.
			if p.match(lexer.TokenColon) {
				p.addError("P053", "default labels do not use ':'; expected '{'", p.previous().Span)
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

// parseTryStmt parses a try/catch/finally statement.
func (p *Parser) parseTryStmt() ast.Statement {
	startSpan := p.previous().Span

	// Parse try block
	p.skipExpressionNewlines()
	if !p.match(lexer.TokenLBrace) {
		p.addError("P056", "expected '{' for try body", p.peek().Span)
		p.skipToBodyEnd()
		return &ast.TryStmt{TryBody: &ast.Block{}, SpanNode: ast.WithSpan(startSpan)}
	}
	tryBlock := p.parseBlock()

	stmt := &ast.TryStmt{
		TryBody:  tryBlock,
		SpanNode: ast.WithSpan(startSpan),
	}

	hasCatch := false
	hasFinally := false

	// Parse optional catch clause
	clauseStart := p.pos
	p.skipExpressionNewlines()
	if p.match(lexer.TokenCatch) {
		hasCatch = true
		catchClause, ok := p.parseCatchClause()
		if ok {
			stmt.Catch = catchClause
		}

		// A newline before finally is continuation only when finally follows.
		finallyStart := p.pos
		p.skipExpressionNewlines()
		if !p.check(lexer.TokenFinally) {
			p.pos = finallyStart
		}
	} else {
		// Preserve the statement terminator when there is no catch clause.
		p.pos = clauseStart
	}

	// Parse optional finally clause. Preserve the newline when no finally
	// clause follows so the enclosing block can validate termination.
	finallyStart := p.pos
	p.skipExpressionNewlines()
	if p.match(lexer.TokenFinally) {
		hasFinally = true
		finallyBlock := p.parseFinallyBlock()
		stmt.Finally = finallyBlock
	} else {
		p.pos = finallyStart
	}

	// Validate: at least one of catch or finally
	if !hasCatch && !hasFinally {
		p.addError("P057", "try statement requires catch or finally", p.peek().Span)
	}

	// Validate: catch after finally is not allowed
	if hasFinally {
		// Check if there's a catch after finally without consuming the next
		// statement's terminator when no catch follows.
		catchStart := p.pos
		p.skipExpressionNewlines()
		if p.check(lexer.TokenCatch) {
			p.addError("P058", "catch must appear before finally", p.peek().Span)
		} else {
			p.pos = catchStart
		}
	}

	// Update span
	endSpan := p.previous().Span
	stmt.SpanNode = ast.WithSpan(source.SpanBetween(
		p.src.PosFromOffset(startSpan.Start),
		p.src.PosFromOffset(endSpan.End),
	))

	return stmt
}

// parseCatchClause parses a catch clause: catch (identifier: type) { block }
func (p *Parser) parseCatchClause() (*ast.CatchClause, bool) {
	startSpan := p.previous().Span

	// Expect '('
	if !p.match(lexer.TokenLParen) {
		p.addError("P059", "expected '(' after catch", p.peek().Span)
		return nil, false
	}

	// Parse parameter name
	if !p.check(lexer.TokenIdentifier) {
		p.addError("P060", "expected parameter name in catch", p.peek().Span)
		// Try to recover
		if p.match(lexer.TokenRParen) {
		}
		return nil, false
	}
	paramTok := p.advance()

	// Expect ':'
	if !p.match(lexer.TokenColon) {
		p.addError("P061", "expected ':' after catch parameter name", p.peek().Span)
		return nil, false
	}

	// Parse type
	typeAnn := p.parseTypeAnnotation()
	if typeAnn == nil {
		p.addError("P063", "expected type in catch parameter", p.peek().Span)
		return nil, false
	}

	// Expect ')'
	if !p.match(lexer.TokenRParen) {
		p.addError("P064", "expected ')' after catch parameter", p.peek().Span)
	}

	// Expect '{'
	p.skipExpressionNewlines()
	if !p.match(lexer.TokenLBrace) {
		p.addError("P065", "expected '{' for catch body", p.peek().Span)
		p.skipToBodyEnd()
		return &ast.CatchClause{
			ParamName: paramTok.Lexeme,
			ParamType: typeAnn,
			Body:      &ast.Block{SpanNode: ast.WithSpan(p.peek().Span)},
			SpanNode:  ast.WithSpan(startSpan),
		}, true
	}

	body := p.parseBlock()

	return &ast.CatchClause{
		ParamName: paramTok.Lexeme,
		ParamType: typeAnn,
		Body:      body,
		SpanNode:  ast.WithSpan(startSpan),
	}, true
}

// parseFinallyBlock parses a finally block.
func (p *Parser) parseFinallyBlock() *ast.Block {
	p.skipExpressionNewlines()
	if !p.match(lexer.TokenLBrace) {
		p.addError("P066", "expected '{' for finally body", p.peek().Span)
		p.skipToBodyEnd()
		return &ast.Block{SpanNode: ast.WithSpan(p.peek().Span)}
	}
	return p.parseBlock()
}

// parseThrowStmt parses a throw statement.
func (p *Parser) parseThrowStmt() ast.Statement {
	startSpan := p.previous().Span

	// Parse the thrown expression
	p.skipExpressionNewlines()
	expr := p.parseExpression()
	if expr == nil {
		p.addError("P067", "expected expression after throw", p.peek().Span)
		return &ast.ThrowStmt{SpanNode: ast.WithSpan(startSpan)}
	}

	return &ast.ThrowStmt{
		Value: expr,
		SpanNode: ast.WithSpan(source.SpanBetween(
			p.src.PosFromOffset(startSpan.Start),
			p.src.PosFromOffset(expr.Span().End),
		)),
	}
}

// parseCaseBody parses the body of a case clause.
// Braces are now required — case bodies must be wrapped in { }.
func (p *Parser) parseCaseBody() *ast.Block {
	p.skipExpressionNewlines()
	if !p.match(lexer.TokenLBrace) {
		p.addError("P069", "expected '{' for case body", p.peek().Span)
		// Skip to next case, default, or closing brace for error recovery
		for !p.isAtEnd() {
			if p.check(lexer.TokenCase) || p.check(lexer.TokenDefault) || p.check(lexer.TokenRBrace) {
				break
			}
			p.advance()
		}
		return &ast.Block{SpanNode: ast.WithSpan(p.peek().Span)}
	}
	return p.parseBlock()
}

// parseIfStmt parses an if/else if/else statement.
func (p *Parser) parseIfStmt() *ast.IfStmt {
	startSpan := p.previous().Span

	condition := p.parseHeaderExpression()

	p.skipExpressionNewlines()
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

	// Parse else if / else. Only consume separators if an else clause follows;
	// otherwise the outer block still needs to validate the statement boundary.
	elseStart := p.pos
	for p.match(lexer.TokenNewline) || p.match(lexer.TokenSemicolon) {
	}
	if !p.check(lexer.TokenElse) {
		p.pos = elseStart
	}

	if p.match(lexer.TokenElse) {
		p.skipExpressionNewlines()
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

	condition := p.parseHeaderExpression()

	p.skipExpressionNewlines()
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

	// Canonical map unpacking is `for key, value in entries`.
	// Continue parsing the legacy parenthesized form so it can produce one
	// focused migration diagnostic instead of cascading errors.
	if p.match(lexer.TokenLParen) {
		if !p.check(lexer.TokenIdentifier) {
			p.addError("P092", "expected key variable name in for", p.peek().Span)
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
			p.addError("P114", "expected ')' after for variables", p.peek().Span)
			return nil
		}
		p.addError("P075", "map iteration bindings do not use parentheses; use 'for key, value in map'", node.Span())
	} else {
		if !p.check(lexer.TokenIdentifier) {
			p.addError("P030", "expected loop variable name", p.peek().Span)
			return nil
		}
		node.Variable = p.advance().Lexeme

		// A comma introduces the second binding of a map iteration.
		if p.match(lexer.TokenComma) {
			if !p.check(lexer.TokenIdentifier) {
				p.addError("P047", "expected value variable name in for", p.peek().Span)
				return nil
			}
			node.ValueVariable = p.advance().Lexeme
		}
	}

	if !p.match(lexer.TokenIn) {
		p.addError("P031", "expected 'in' after loop variable", p.peek().Span)
	}

	node.Iterable = p.parseHeaderExpression()

	p.skipExpressionNewlines()
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

// tryParseIdentList attempts to parse a comma-separated list of identifiers.
// Returns the list if successful (2+ identifiers), otherwise resets position and returns nil.
func (p *Parser) tryParseIdentList() []*ast.Identifier {
	save := p.pos
	var ids []*ast.Identifier
	for {
		if !p.check(lexer.TokenIdentifier) {
			p.pos = save
			return nil
		}
		id := &ast.Identifier{SpanNode: ast.WithSpan(p.peek().Span), Name: p.peek().Lexeme}
		p.advance()
		ids = append(ids, id)
		if !p.match(lexer.TokenComma) {
			break
		}
		// Allow trailing comma
		if p.check(lexer.TokenAssign) || p.checkNewlineOrSemicolon() || p.check(lexer.TokenRBrace) {
			break
		}
	}
	if len(ids) < 2 || !p.check(lexer.TokenAssign) {
		p.pos = save
		return nil
	}
	return ids
}

// parseExprStmt parses an expression or assignment statement.
func (p *Parser) parseExprStmt() *ast.ExprStmt {
	start := p.pos

	// Check for multi-target assignment and reject it: a, b = expr
	if ids := p.tryParseIdentList(); len(ids) >= 2 {
		// Report an error: multi-target assignment is not supported
		lastIdent := ids[len(ids)-1]
		p.addError("P122", "multi-target assignment is not supported; use a struct type instead", lastIdent.Span())
		// Skip past the '=' and expression to recover
		if p.match(lexer.TokenAssign) {
			p.skipExpressionNewlines()
			p.parseExpression()
		}
		return nil
	}

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
		p.skipExpressionNewlines()
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
	PrecConcat     // ..
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
	case lexer.TokenConcat:
		return PrecConcat
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

// parseHeaderExpression parses an expression that is followed by a required
// statement block. Struct literals are disabled here so the block's opening
// brace cannot be consumed as part of the expression.
func (p *Parser) parseHeaderExpression() ast.Expression {
	allow := p.allowStructLiteral
	p.allowStructLiteral = false
	p.skipExpressionNewlines()
	expr := p.parseExpression()
	p.allowStructLiteral = allow
	return expr
}

// parsePrecedence parses an expression with a given minimum precedence.
func (p *Parser) parsePrecedence(minPrec int) ast.Expression {
	prefix := p.parsePrefix()
	if prefix == nil {
		return nil
	}

	for {
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

// parseContinuationPrecedence parses an expression whose first token is
// required by the preceding operator or delimiter. Newlines are whitespace
// in that position; elsewhere parseExpression leaves them visible so the
// enclosing statement parser can enforce termination.
func (p *Parser) parseContinuationPrecedence(minPrec int) ast.Expression {
	p.skipExpressionNewlines()
	return p.parsePrecedence(minPrec)
}

// parsePrefix parses a prefix expression.
func (p *Parser) parsePrefix() ast.Expression {
	// .. is not a prefix operator
	if p.check(lexer.TokenConcat) {
		p.addError("P118", "'..' is a binary string-concatenation operator and cannot be used as a prefix", p.peek().Span)
		p.advance()
		return nil
	}

	// Handle unary operators
	if p.match(lexer.TokenMinus) {
		opSpan := p.previous().Span
		operand := p.parseContinuationPrecedence(PrecUnary)
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
		operand := p.parseContinuationPrecedence(PrecUnary)
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
		operand := p.parseContinuationPrecedence(PrecUnary)
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
		val, err := strconv.ParseInt(tok.Lexeme, 0, 64)
		if err != nil {
			p.diags.AddError("P035", fmt.Sprintf("invalid integer literal: %s", err), tok.Span)
			return nil
		}
		return &ast.IntLiteral{SpanNode: ast.WithSpan(tok.Span), Value: val}
	}
	if tok := p.advanceIf(lexer.TokenFloatLiteral); tok != nil {
		val, err := strconv.ParseFloat(tok.Lexeme, 64)
		if err != nil {
			p.addError("P037", fmt.Sprintf("invalid float literal: %s", err), tok.Span)
			return nil
		}
		return &ast.FloatLiteral{SpanNode: ast.WithSpan(tok.Span), Value: val}
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
		// A struct literal starts with a type name followed by '{'. Keep this
		// in prefix parsing rather than treating '{' as a general infix
		// operator; otherwise block constructs such as `for x in items {`
		// are misinterpreted as struct literals.
		ident := &ast.Identifier{SpanNode: ast.WithSpan(tok.Span), Name: tok.Lexeme}
		if p.allowStructLiteral && p.match(lexer.TokenLBrace) {
			return p.parseStructLiteral(ident.Name, ident.Span())
		}
		return ident
	}
	if p.check(lexer.TokenString) || p.check(lexer.TokenInt) ||
		p.check(lexer.TokenFloat) || p.check(lexer.TokenBool) ||
		p.check(lexer.TokenByte) || p.check(lexer.TokenChar) || p.check(lexer.TokenVoid) ||
		p.check(lexer.TokenList) || p.check(lexer.TokenMap) || p.check(lexer.TokenStack) || p.check(lexer.TokenException) {
		tok := p.advance()
		return &ast.Identifier{SpanNode: ast.WithSpan(tok.Span), Name: tok.Lexeme}
	}

	// Grouping
	if p.match(lexer.TokenLParen) {
		p.skipExpressionNewlines()
		expr := p.parseExpression()
		if expr == nil {
			return nil
		}
		p.skipExpressionNewlines()
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
		p.skipExpressionNewlines()
		var memberName string
		var memberSpan source.Span

		// Accept identifiers and type/builtin keywords as member names
		// This allows core.bool, core.int, and other qualified builtins.
		if p.check(lexer.TokenIdentifier) || p.check(lexer.TokenBool) || p.check(lexer.TokenByte) ||
			p.check(lexer.TokenInt) || p.check(lexer.TokenFloat) ||
			p.check(lexer.TokenChar) || p.check(lexer.TokenString) ||
			p.check(lexer.TokenVoid) || p.check(lexer.TokenList) || p.check(lexer.TokenMap) || p.check(lexer.TokenStack) {
			memberTok := p.advance()
			memberSpan = memberTok.Span
			memberName = memberTok.Lexeme
			if memberTok.Kind == lexer.TokenBool {
				memberName = "bool"
			} else if memberTok.Kind == lexer.TokenByte {
				memberName = "byte"
			} else if memberTok.Kind == lexer.TokenInt {
				memberName = "int"
			} else if memberTok.Kind == lexer.TokenFloat {
				memberName = "float"
			} else if memberTok.Kind == lexer.TokenChar {
				memberName = "char"
			} else if memberTok.Kind == lexer.TokenString {
				memberName = "string"
			} else if memberTok.Kind == lexer.TokenException {
				memberName = "exception"
			} else if memberTok.Kind == lexer.TokenVoid {
				memberName = "void"
			} else if memberTok.Kind == lexer.TokenList {
				memberName = "List"
			} else if memberTok.Kind == lexer.TokenMap {
				memberName = "Map"
			} else if memberTok.Kind == lexer.TokenStack {
				memberName = "stack"
			} else {
				memberName = memberTok.Lexeme
			}
		} else {
			p.addError("P115", "expected member name after '.'", p.peek().Span)
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

	// Null coalescing (right-associative so chains like a ?? b ?? c parse as
	// a ?? (b ?? c), matching the C# null-coalescing behavior Solvik draws on)
	if kind == lexer.TokenNullCoalesce {
		p.advance()
		right := p.parseContinuationPrecedence(PrecCoalescing - 1)
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

	right := p.parseContinuationPrecedence(rightPrec)
	if right == nil {
		// If .. is used as a postfix (no right operand), report a clear error
		if kind == lexer.TokenConcat {
			p.addError("P121", "'..' is a binary string-concatenation operator requiring a left and right operand, but the right operand is missing", p.previous().Span)
		}
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
		// Check for spread '...' after the expression
		if p.match(lexer.TokenEllipsis) {
			arg = &ast.SpreadExpr{
				SpanNode: ast.WithSpan(source.SpanBetween(
					p.src.PosFromOffset(arg.Span().Start),
					p.src.PosFromOffset(p.previous().Span.End),
				)),
				Expr: arg,
			}
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
	p.skipExpressionNewlines()
	index := p.parseExpression()
	p.skipExpressionNewlines()
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

// parseStructLiteral parses a named-field struct literal: Name { field: value, ... }
func (p *Parser) parseStructLiteral(typeName string, startSpan source.Span) *ast.StructLiteral {
	lit := &ast.StructLiteral{
		SpanNode: ast.WithSpan(startSpan),
		TypeName: typeName,
	}

	for !p.check(lexer.TokenRBrace) && !p.isAtEnd() {
		for p.match(lexer.TokenNewline) {
		}
		if p.check(lexer.TokenRBrace) {
			break
		}
		if len(lit.Fields) > 0 {
			if !p.match(lexer.TokenComma) {
				break
			}
			for p.match(lexer.TokenNewline) {
			}
			if p.check(lexer.TokenRBrace) {
				break
			}
		}

		// Parse field name
		if !p.check(lexer.TokenIdentifier) {
			p.addError("P099", "expected field name in struct literal", p.peek().Span)
			break
		}
		fieldName := p.advance().Lexeme

		if !p.match(lexer.TokenColon) {
			p.addError("P087", "expected ':' after field name", p.peek().Span)
			break
		}

		p.skipExpressionNewlines()
		value := p.parseExpression()
		if value == nil {
			break
		}

		lit.Fields = append(lit.Fields, fieldName)
		lit.Values = append(lit.Values, value)
		for p.match(lexer.TokenNewline) {
		}
	}

	for p.match(lexer.TokenNewline) {
	}
	if !p.match(lexer.TokenRBrace) {
		p.addError("P094", "expected '}' to close struct literal", p.peek().Span)
	} else {
		endSpan := p.previous().Span
		lit.SpanNode = ast.WithSpan(source.SpanBetween(
			p.src.PosFromOffset(startSpan.Start),
			p.src.PosFromOffset(endSpan.End),
		))
	}

	return lit
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
		p.skipExpressionNewlines()
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
	case lexer.TokenConcat:
		return ast.BinStrConcat
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
	if p.isAtEnd() || p.check(lexer.TokenRBrace) {
		return
	}
	p.addError(diagnostic.CodeParserStatementSeparator,
		"expected newline or ';' between statements", p.peek().Span)
}

// skipExpressionNewlines consumes newlines only where the surrounding syntax
// requires an expression to continue. It is deliberately not used after a
// complete expression, where a newline is a statement terminator.
func (p *Parser) skipExpressionNewlines() {
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
		case lexer.TokenFunc, lexer.TokenEnum, lexer.TokenStruct, lexer.TokenTrait, lexer.TokenIf, lexer.TokenWhile, lexer.TokenFor,
			lexer.TokenSwitch, lexer.TokenCase, lexer.TokenDefault,
			lexer.TokenReturn, lexer.TokenBreak, lexer.TokenContinue,
			lexer.TokenPackage, lexer.TokenRBrace,
			lexer.TokenEllipsis:
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

// matchTypeGt consumes one generic closing delimiter. A lexer-produced >>
// closes the current type and leaves one pending closer for its parent type.
func (p *Parser) matchTypeGt() bool {
	if p.pendingTypeGts > 0 {
		p.pendingTypeGts--
		return true
	}
	if p.match(lexer.TokenGt) {
		return true
	}
	if p.match(lexer.TokenShiftRight) {
		p.pendingTypeGts++
		return true
	}
	return false
}
