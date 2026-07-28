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

// Package lexer provides lexical analysis (tokenization) for the language.
package lexer

import (
	"fmt"
	"strings"
	"unicode/utf8"

	"github.com/dhoard/solvik-language/internal/diagnostic"
	"github.com/dhoard/solvik-language/internal/source"
)

// TokenKind classifies a token.
type TokenKind int

const (
	TokenEOF TokenKind = iota
	TokenError

	// Literals
	TokenIntLiteral
	TokenFloatLiteral
	TokenBoolLiteral
	TokenCharLiteral
	TokenStringLiteral
	TokenByteLiteral
	TokenNullLiteral

	// Identifiers and keywords
	TokenIdentifier
	TokenFunc
	TokenIf
	TokenElse
	TokenWhile
	TokenFor
	TokenIn
	TokenReturn
	TokenBreak
	TokenContinue
	TokenTrue
	TokenFalse
	TokenNull
	TokenPackage
	TokenImport
	TokenUse

	// Types
	TokenBool
	TokenByte
	TokenInt
	TokenFloat
	TokenChar
	TokenString
	TokenVoid
	TokenList
	TokenMap

	// Operators and delimiters
	TokenConcat // ..
	TokenPlus
	TokenMinus
	TokenStar
	TokenSlash
	TokenPercent
	TokenEq         // ==
	TokenNe         // !=
	TokenLt         // <
	TokenLe         // <=
	TokenGt         // >
	TokenGe         // >=
	TokenAssign     // =
	TokenAnd        // &&
	TokenOr         // ||
	TokenNot        // !
	TokenBitAnd     // &
	TokenBitOr      // |
	TokenBitXor     // ^
	TokenBitNot     // ~
	TokenShiftLeft  // <<
	TokenShiftRight // >>

	// Delimiters
	TokenLParen       // (
	TokenRParen       // )
	TokenLBrace       // {
	TokenRBrace       // }
	TokenLBracket     // [
	TokenRBracket     // ]
	TokenComma        // ,
	TokenColon        // :
	TokenSemicolon    // ;
	TokenArrow        // ->
	TokenDot          // .
	TokenEllipsis     // ...
	TokenQuestion     // ?
	TokenNullCoalesce // ??

	// Keywords
	TokenSwitch
	TokenCase
	TokenDefault
	TokenTry
	TokenCatch
	TokenFinally
	TokenThrow
	TokenException
	TokenMut
	TokenEnum

	// Newline (significant newline separating statements)
	TokenNewline
)

// Token represents a lexical token.
type Token struct {
	Kind   TokenKind
	Lexeme string
	Span   source.Span
}

// String returns a readable representation of the token kind.
func (k TokenKind) String() string {
	switch k {
	case TokenEOF:
		return "EOF"
	case TokenError:
		return "error"
	case TokenIntLiteral:
		return "integer literal"
	case TokenFloatLiteral:
		return "float literal"
	case TokenBoolLiteral:
		return "bool literal"
	case TokenCharLiteral:
		return "char literal"
	case TokenStringLiteral:
		return "string literal"
	case TokenByteLiteral:
		return "byte literal"
	case TokenNullLiteral:
		return "null literal"
	case TokenIdentifier:
		return "identifier"
	case TokenFunc:
		return "func"
	case TokenIf:
		return "if"
	case TokenElse:
		return "else"
	case TokenWhile:
		return "while"
	case TokenFor:
		return "for"
	case TokenIn:
		return "in"
	case TokenReturn:
		return "return"
	case TokenBreak:
		return "break"
	case TokenContinue:
		return "continue"
	case TokenTrue:
		return "true"
	case TokenFalse:
		return "false"
	case TokenNull:
		return "null"
	case TokenPackage:
		return "package"
	case TokenImport:
		return "import"
	case TokenUse:
		return "use"
	case TokenBool:
		return "bool"
	case TokenByte:
		return "byte"
	case TokenInt:
		return "int"
	case TokenFloat:
		return "float"
	case TokenChar:
		return "char"
	case TokenString:
		return "string"
	case TokenVoid:
		return "void"
	case TokenList:
		return "List"
	case TokenMap:
		return "Map"
	case TokenConcat:
		return ".."
	case TokenPlus:
		return "+"
	case TokenMinus:
		return "-"
	case TokenStar:
		return "*"
	case TokenSlash:
		return "/"
	case TokenPercent:
		return "%"
	case TokenEq:
		return "=="
	case TokenNe:
		return "!="
	case TokenLt:
		return "<"
	case TokenLe:
		return "<="
	case TokenGt:
		return ">"
	case TokenGe:
		return ">="
	case TokenAssign:
		return "="
	case TokenAnd:
		return "&&"
	case TokenOr:
		return "||"
	case TokenNot:
		return "!"
	case TokenBitAnd:
		return "&"
	case TokenBitOr:
		return "|"
	case TokenBitXor:
		return "^"
	case TokenBitNot:
		return "~"
	case TokenShiftLeft:
		return "<<"
	case TokenShiftRight:
		return ">>"
	case TokenLParen:
		return "("
	case TokenRParen:
		return ")"
	case TokenLBrace:
		return "{"
	case TokenRBrace:
		return "}"
	case TokenLBracket:
		return "["
	case TokenRBracket:
		return "]"
	case TokenComma:
		return ","
	case TokenColon:
		return ":"
	case TokenSemicolon:
		return ";"
	case TokenArrow:
		return "->"
	case TokenDot:
		return "."
	case TokenEllipsis:
		return "..."
	case TokenQuestion:
		return "?"
	case TokenNullCoalesce:
		return "??"
	case TokenSwitch:
		return "switch"
	case TokenCase:
		return "case"
	case TokenDefault:
		return "default"
	case TokenTry:
		return "try"
	case TokenCatch:
		return "catch"
	case TokenFinally:
		return "finally"
	case TokenThrow:
		return "throw"
	case TokenException:
		return "exception"
	case TokenMut:
		return "mut"
	case TokenEnum:
		return "enum"
	case TokenNewline:
		return "newline"
	default:
		return fmt.Sprintf("token(%d)", k)
	}
}

// Lexer performs lexical analysis.
type Lexer struct {
	src     *source.Source
	start   int // start of current token
	current int // current position
	line    int // current line (1-based)
	column  int // current column (1-based)
	diags   *diagnostic.Diagnostics
	tokens  []Token
	eof     bool
}

// New creates a new lexer for the given source.
func New(src *source.Source) *Lexer {
	return &Lexer{
		src:    src,
		line:   1,
		column: 1,
		diags:  diagnostic.NewDiagnostics(),
	}
}

// Tokenize performs full tokenization and returns all tokens.
func (l *Lexer) Tokenize() ([]Token, *diagnostic.Diagnostics) {
	for !l.eof {
		tok := l.nextToken()
		l.tokens = append(l.tokens, tok)
		if tok.Kind == TokenEOF {
			break
		}
	}
	return l.tokens, l.diags
}

// nextToken reads and returns the next token.
func (l *Lexer) nextToken() Token {
	l.skipWhitespace()
	l.start = l.current

	if l.current >= len(l.src.Content) {
		return l.makeToken(TokenEOF)
	}

	ch := l.peek()

	// Handle newlines
	if ch == '\n' {
		l.advance()
		l.line++
		l.column = 1
		return l.makeToken(TokenNewline)
	}
	if ch == '\r' {
		l.advance()
		if l.peek() == '\n' {
			l.advance()
		}
		l.line++
		l.column = 1
		return l.makeToken(TokenNewline)
	}

	// Handle comments
	if ch == '/' {
		next := l.peekNext()
		if next == '/' {
			l.skipLineComment()
			return l.nextToken()
		}
		if next == '*' {
			l.skipBlockComment()
			return l.nextToken()
		}
	}

	// Handle semicolon
	if ch == ';' {
		l.advance()
		return l.makeToken(TokenSemicolon)
	}

	// Handle identifiers and keywords
	if isIdentStart(ch) {
		// Check for raw string literal before treating as identifier.
		// A raw string starts with 'r' followed by zero or more '#' followed by '"'.
		if ch == 'r' {
			next := l.peekNext()
			if next == '"' || next == '#' {
				return l.readRawString()
			}
		}
		return l.readIdentifier()
	}

	// Handle numbers
	if isDigit(ch) || (ch == '.' && isDigit(l.peekNext())) {
		return l.readNumber()
	}

	// Handle strings
	if ch == '"' {
		return l.readString()
	}

	// Handle char literals
	if ch == '\'' {
		return l.readChar()
	}

	// Handle operators and delimiters
	switch ch {
	case '(':
		l.advance()
		return l.makeToken(TokenLParen)
	case ')':
		l.advance()
		return l.makeToken(TokenRParen)
	case '{':
		l.advance()
		return l.makeToken(TokenLBrace)
	case '}':
		l.advance()
		return l.makeToken(TokenRBrace)
	case '[':
		l.advance()
		return l.makeToken(TokenLBracket)
	case ']':
		l.advance()
		return l.makeToken(TokenRBracket)
	case ',':
		l.advance()
		return l.makeToken(TokenComma)
	case ':':
		l.advance()
		return l.makeToken(TokenColon)
	case '.':
		l.advance()
		// Check for '..' string concatenation operator
		if l.peek() == '.' {
			l.advance() // consume second '.'
			// Check for '...' ellipsis (three consecutive dots)
			if l.peek() == '.' {
				l.advance() // consume third '.'
				return l.makeToken(TokenEllipsis)
			}
			return l.makeToken(TokenConcat)
		}
		return l.makeToken(TokenDot)
	case '+':
		l.advance()
		if l.peek() == '+' {
			l.advance()
			return l.makeToken(TokenConcat)
		}
		return l.makeToken(TokenPlus)
	case '-':
		l.advance()
		if l.peek() == '>' {
			l.advance()
			return l.makeToken(TokenArrow)
		}
		return l.makeToken(TokenMinus)
	case '*':
		l.advance()
		return l.makeToken(TokenStar)
	case '/':
		l.advance()
		return l.makeToken(TokenSlash)
	case '%':
		l.advance()
		return l.makeToken(TokenPercent)
	case '=':
		l.advance()
		if l.peek() == '=' {
			l.advance()
			return l.makeToken(TokenEq)
		}
		return l.makeToken(TokenAssign)
	case '!':
		l.advance()
		if l.peek() == '=' {
			l.advance()
			return l.makeToken(TokenNe)
		}
		return l.makeToken(TokenNot)
	case '<':
		l.advance()
		if l.peek() == '=' {
			l.advance()
			return l.makeToken(TokenLe)
		}
		if l.peek() == '<' {
			l.advance()
			return l.makeToken(TokenShiftLeft)
		}
		return l.makeToken(TokenLt)
	case '>':
		l.advance()
		if l.peek() == '=' {
			l.advance()
			return l.makeToken(TokenGe)
		}
		if l.peek() == '>' {
			l.advance()
			return l.makeToken(TokenShiftRight)
		}
		return l.makeToken(TokenGt)
	case '&':
		l.advance()
		if l.peek() == '&' {
			l.advance()
			return l.makeToken(TokenAnd)
		}
		return l.makeToken(TokenBitAnd)
	case '|':
		l.advance()
		if l.peek() == '|' {
			l.advance()
			return l.makeToken(TokenOr)
		}
		return l.makeToken(TokenBitOr)
	case '^':
		l.advance()
		return l.makeToken(TokenBitXor)
	case '~':
		l.advance()
		return l.makeToken(TokenBitNot)
	case '?':
		l.advance()
		if l.peek() == '?' {
			l.advance()
			return l.makeToken(TokenNullCoalesce)
		}
		return l.makeToken(TokenQuestion)
	}

	// Unexpected character
	l.advance()
	l.diags.AddError("L001", fmt.Sprintf("unexpected character: %q", ch), l.currentSpan())
	return l.makeToken(TokenError)
}

// readIdentifier reads an identifier or keyword.
func (l *Lexer) readIdentifier() Token {
	for isIdentPart(l.peek()) {
		l.advance()
	}
	lexeme := string(l.src.Content[l.start:l.current])
	kind := lookupKeyword(lexeme)
	return l.makeToken(kind)
}

// readNumber reads a numeric literal.
func (l *Lexer) readNumber() Token {
	// Check for hex
	if l.peek() == '0' && (l.peekNext() == 'x' || l.peekNext() == 'X') {
		return l.readHexNumber()
	}

	// Read integer part (supports underscores between digits)
	isFloat := false
	l.skipNumberUnderscores()

	// Check for decimal point or float suffix
	if l.peek() == '.' && !l.isDotDot() {
		l.advance()
		isFloat = true
		l.skipNumberUnderscores()
	}

	// Check for exponent
	if l.peek() == 'e' || l.peek() == 'E' {
		l.advance()
		if l.peek() == '+' || l.peek() == '-' {
			l.advance()
		}
		if !isDigit(l.peek()) {
			l.diags.AddError("L002", "expected digit in exponent", l.currentSpan())
			return l.makeToken(TokenError)
		}
		// Allow underscores in exponent digits
		for isDigit(l.peek()) || l.peek() == '_' {
			if l.peek() == '_' {
				l.advance()
				continue
			}
			l.advance()
		}
		isFloat = true
	}

	// Build raw lexeme and validate underscores
	rawLexeme := string(l.src.Content[l.start:l.current])
	if strings.Contains(rawLexeme, "_") {
		if msg := l.validateUnderscoreFormat(rawLexeme); msg != "" {
			l.diags.AddError("L011", msg, l.currentSpan())
			return l.makeToken(TokenError)
		}
	}

	// Build clean lexeme with underscores stripped
	lexeme := l.stripNumberLexeme()

	// Check for type suffixes
	if l.peek() == 'f' || l.peek() == 'F' {
		l.advance()
		return l.makeTokenFloat(lexeme)
	}
	if l.peek() == 'd' || l.peek() == 'D' {
		l.advance()
		l.diags.AddError("L012", "'D' suffix is no longer supported; floating-point literals have type float", l.currentSpan())
		return l.makeToken(TokenError)
	}
	if l.peek() == 'L' || l.peek() == 'l' {
		l.advance()
		l.diags.AddError("L013", "'L' suffix is no longer supported; integer literals have type int", l.currentSpan())
		return l.makeToken(TokenError)
	}

	if isFloat {
		return l.makeTokenFloat(lexeme)
	}
	return l.makeTokenInt(lexeme)
}

// readHexNumber reads a hexadecimal integer.
func (l *Lexer) readHexNumber() Token {
	l.advance() // skip 0
	l.advance() // skip x or X
	start := l.current
	for isHexDigit(l.peek()) || l.peek() == '_' {
		if l.peek() == '_' {
			l.advance()
			continue
		}
		l.advance()
	}
	if l.current == start {
		l.diags.AddError("L003", "expected hex digits after 0x", l.currentSpan())
		return l.makeToken(TokenError)
	}
	// Validate underscores before stripping
	rawLexeme := string(l.src.Content[l.start:l.current])
	if strings.Contains(rawLexeme, "_") {
		if msg := l.validateUnderscoreFormat(rawLexeme); msg != "" {
			l.diags.AddError("L011", msg, l.currentSpan())
			return l.makeToken(TokenError)
		}
	}

	// Build hex lexeme with underscores stripped (keep the "0x" prefix)
	cleanHex := strings.ReplaceAll(rawLexeme, "_", "")

	if l.peek() == 'f' || l.peek() == 'F' {
		l.advance()
		return l.makeTokenFloat(cleanHex)
	}
	if l.peek() == 'd' || l.peek() == 'D' {
		l.advance()
		l.diags.AddError("L012", "'D' suffix is no longer supported; floating-point literals have type float", l.currentSpan())
		return l.makeToken(TokenError)
	}
	if l.peek() == 'L' || l.peek() == 'l' {
		l.advance()
		l.diags.AddError("L013", "'L' suffix is no longer supported; integer literals have type int", l.currentSpan())
		return l.makeToken(TokenError)
	}
	return l.makeTokenInt(cleanHex)
}

// readString reads a string literal.
func (l *Lexer) readString() Token {
	l.advance() // skip opening quote
	var buf []byte
	for {
		ch := l.peek()
		if ch == -1 {
			l.diags.AddError("L004", "unterminated string literal", l.currentSpan())
			return l.makeToken(TokenError)
		}
		if ch == '"' {
			l.advance()
			return l.makeTokenString(string(buf))
		}
		if ch == '\\' {
			l.advance()
			esc := l.peek()
			switch esc {
			case 'n':
				buf = append(buf, '\n')
			case 't':
				buf = append(buf, '\t')
			case 'r':
				buf = append(buf, '\r')
			case '\\':
				buf = append(buf, '\\')
			case '"':
				buf = append(buf, '"')
			case '0':
				buf = append(buf, 0)
			case 'x':
				l.advance()
				hi := l.peek()
				l.advance()
				lo := l.peek()
				buf = append(buf, byte(hexVal(hi)<<4|hexVal(lo)))
			case 'u':
				buf = append(buf, l.readUnicodeEscape(4)...)
			case 'U':
				buf = append(buf, l.readUnicodeEscape(8)...)
			default:
				buf = append(buf, '\\')
				buf = append(buf, byte(esc))
			}
			l.advance()
		} else {
			if ch == '\n' || ch == '\r' {
				l.diags.AddError("L005", "newline in string literal", l.currentSpan())
				return l.makeToken(TokenError)
			}
			buf = append(buf, byte(ch))
			l.advance()
		}
	}
}

// readRawString reads a Rust-style raw string literal.
// Supports r"...", r#"..."#, r##"..."##, etc. with up to 255 # delimiters.
// The content is returned literally without escape processing.
func (l *Lexer) readRawString() Token {
	l.advance() // skip 'r'

	// Count hashes to determine the delimiter depth
	hashCount := 0
	for l.peek() == '#' {
		hashCount++
		if hashCount > 255 {
			// Consume the rest of the hashes to report a clean error
			for l.peek() == '#' {
				l.advance()
			}
			l.diags.AddError("L010",
				"raw string delimiter too large: maximum is 255 '#' characters",
				l.currentSpan())
			return l.makeToken(TokenError)
		}
		l.advance()
	}

	// Must be followed by a double quote to be a raw string
	if l.peek() != '"' {
		// Not a raw string - could be an identifier starting with 'r'
		// Reset position and read as identifier
		l.current = l.start
		return l.readIdentifier()
	}
	l.advance() // skip opening '"'

	// Record where the raw string started (the 'r') for diagnostics
	rawStart := l.start

	// Scan the body content
	var buf []byte
	for {
		ch := l.peek()
		if ch == -1 {
			// Unterminated: report the expected closing delimiter
			expected := buildExpectedDelimiter(hashCount)
			msg := fmt.Sprintf("unterminated raw string literal, expected %s", expected)
			l.diags.AddError("L006", msg, l.src.SpanFromRange(rawStart, l.current))
			return l.makeToken(TokenError)
		}

		if ch == '"' {
			// Save position before trying to match the closing delimiter
			savedPos := l.current
			savedLine := l.line
			savedCol := l.column

			l.advance() // consume '"'
			matched := true
			for i := 0; i < hashCount; i++ {
				if l.peek() != '#' {
					matched = false
					break
				}
				l.advance()
			}

			if matched {
				// Found the closing delimiter
				return l.makeTokenString(string(buf))
			}

			// Not the closing delimiter - restore position so the '"'
			// and any '#' characters are included in the body.
			l.current = savedPos
			l.line = savedLine
			l.column = savedCol
			// Add the '"' to body and advance
			buf = append(buf, '"')
			l.advance()
			continue
		}

		buf = append(buf, byte(ch))
		if ch == '\n' {
			l.line++
		}
		l.advance()
	}
}

// buildExpectedDelimiter returns the expected closing delimiter string
// like '"#' for hashCount=1, '"##' for hashCount=2, etc.
func buildExpectedDelimiter(hashCount int) string {
	s := make([]byte, 1+hashCount)
	s[0] = '"'
	for i := 0; i < hashCount; i++ {
		s[1+i] = '#'
	}
	return string(s)
}

// readChar reads a character literal.
func (l *Lexer) readChar() Token {
	l.advance() // skip opening quote

	ch := l.peek()
	if ch == -1 {
		l.diags.AddError("L007", "unterminated char literal", l.currentSpan())
		return l.makeToken(TokenError)
	}

	var r rune
	if ch == '\\' {
		l.advance()
		esc := l.peek()
		switch esc {
		case 'n':
			r = '\n'
		case 't':
			r = '\t'
		case 'r':
			r = '\r'
		case '\\':
			r = '\\'
		case '\'':
			r = '\''
		case '0':
			r = 0
		default:
			r = rune(esc)
		}
		l.advance()
	} else {
		r = rune(ch)
		l.advance()
	}

	if l.peek() != '\'' {
		l.diags.AddError("L008", "unterminated char literal (expected ') ", l.currentSpan())
		return l.makeToken(TokenError)
	}
	l.advance()
	return l.makeTokenChar(r)
}

// readUnicodeEscape reads \uXXXX or \UXXXXXXXX.
func (l *Lexer) readUnicodeEscape(digits int) []byte {
	var r rune
	for i := 0; i < digits; i++ {
		l.advance()
		ch := l.peek()
		r = r<<4 | rune(hexVal(ch))
	}
	var buf [utf8.UTFMax]byte
	n := utf8.EncodeRune(buf[:], r)
	return buf[:n]
}

// skipWhitespace skips whitespace (but not newlines).
func (l *Lexer) skipWhitespace() {
	for {
		ch := l.peek()
		if ch == ' ' || ch == '\t' || ch == '\f' || ch == '\v' {
			l.advance()
		} else {
			break
		}
	}
}

// skipLineComment skips a // comment.
func (l *Lexer) skipLineComment() {
	for {
		ch := l.peek()
		if ch == -1 || ch == '\n' || ch == '\r' {
			return
		}
		l.advance()
	}
}

// skipBlockComment skips a /* */ comment (supports nesting).
func (l *Lexer) skipBlockComment() {
	l.advance() // skip *
	depth := 1
	for depth > 0 {
		ch := l.peek()
		if ch == -1 {
			l.diags.AddError("L009", "unterminated block comment", l.currentSpan())
			return
		}
		if ch == '/' && l.peekNext() == '*' {
			l.advance()
			l.advance()
			depth++
		} else if ch == '*' && l.peekNext() == '/' {
			l.advance()
			l.advance()
			depth--
		} else {
			if ch == '\n' {
				l.line++
			}
			l.advance()
		}
	}
}

// isDotDot checks if we're at a .. sequence (not used in the language, but needed for number parsing).
func (l *Lexer) isDotDot() bool {
	return l.peek() == '.' && l.peekNext() == '.'
}

// Helper methods

func (l *Lexer) advance() {
	if l.current < len(l.src.Content) {
		ch := l.src.Content[l.current]
		l.current++
		if ch == '\n' {
			l.column = 1
			l.line++
		} else {
			l.column++
		}
	}
}

func (l *Lexer) peek() int {
	if l.current >= len(l.src.Content) {
		return -1
	}
	return int(l.src.Content[l.current])
}

func (l *Lexer) peekNext() int {
	if l.current+1 >= len(l.src.Content) {
		return -1
	}
	return int(l.src.Content[l.current+1])
}

func (l *Lexer) pos() source.Pos {
	return l.src.PosFromOffset(l.current)
}

func (l *Lexer) currentSpan() source.Span {
	return l.src.SpanFromRange(l.start, l.current)
}

func (l *Lexer) makeToken(kind TokenKind) Token {
	return Token{
		Kind:   kind,
		Lexeme: string(l.src.Content[l.start:l.current]),
		Span:   l.src.SpanFromRange(l.start, l.current),
	}
}

func (l *Lexer) makeTokenInt(lexeme string) Token {
	return Token{
		Kind:   TokenIntLiteral,
		Lexeme: lexeme,
		Span:   l.src.SpanFromRange(l.start, l.current),
	}
}

func (l *Lexer) makeTokenFloat(lexeme string) Token {
	return Token{
		Kind:   TokenFloatLiteral,
		Lexeme: lexeme,
		Span:   l.src.SpanFromRange(l.start, l.current),
	}
}

func (l *Lexer) makeTokenString(value string) Token {
	// Calculate span (note: we've already consumed closing quote)
	span := l.src.SpanFromRange(l.start, l.current)
	return Token{
		Kind:   TokenStringLiteral,
		Lexeme: value,
		Span:   span,
	}
}

func (l *Lexer) makeTokenChar(r rune) Token {
	return Token{
		Kind:   TokenCharLiteral,
		Lexeme: string(r),
		Span:   l.src.SpanFromRange(l.start, l.current),
	}
}

// Character class helpers

// skipNumberUnderscores advances past digits and underscores in a number.
// Underscores are treated as digit separators (Java-style).
func (l *Lexer) skipNumberUnderscores() {
	for isDigit(l.peek()) || l.peek() == '_' {
		if l.peek() == '_' {
			l.advance()
			continue
		}
		l.advance()
	}
}

// stripNumberLexeme returns the source text from l.start to l.current
// with underscores removed.
func (l *Lexer) stripNumberLexeme() string {
	raw := string(l.src.Content[l.start:l.current])
	return strings.ReplaceAll(raw, "_", "")
}

// validateUnderscoreFormat checks underscores in a numeric literal's digit parts.
// Rules (from the spec):
//   - Underscores may appear between digits only.
//   - No leading/trailing underscore.
//   - Not adjacent to prefix (0x, 0b, 0o), decimal point, exponent marker/sign,
//     or type suffix. Consecutive underscores (__) are allowed.
//
// raw is the source content from l.start to l.current (suffix not consumed yet).
// Returns an empty string if valid, or an error message if invalid.
func (l *Lexer) validateUnderscoreFormat(raw string) string {
	// Strip numeric prefix (0x, 0b, 0o, 0X, 0B, 0O)
	digitPart := raw
	isHex := false
	if len(raw) >= 2 && raw[0] == '0' {
		switch raw[1] {
		case 'x', 'X', 'b', 'B', 'o', 'O':
			digitPart = raw[2:]
			if raw[1] == 'x' || raw[1] == 'X' {
				isHex = true
			}
		}
	}

	// Split by 'e'/'E' to separate mantissa from exponent
	// (The exponent marker was consumed during scanning, so the raw string
	// includes it. We need to extract it for proper validation.)
	mantissa := digitPart
	exponent := ""
	for i := 0; i < len(digitPart); i++ {
		if digitPart[i] == 'e' || digitPart[i] == 'E' {
			exponent = digitPart[i+1:]
			mantissa = digitPart[:i]
			break
		}
	}

	// Validate mantissa parts (split by decimal point)
	mParts := strings.SplitN(mantissa, ".", 2)
	for _, part := range mParts {
		if msg := checkUnderscorePositions(part, isHex); msg != "" {
			return msg
		}
	}

	// Validate exponent part (decimal only, never hex)
	if exponent != "" {
		// Strip optional sign
		if len(exponent) > 0 && (exponent[0] == '+' || exponent[0] == '-') {
			exponent = exponent[1:]
		}
		if msg := checkUnderscorePositions(exponent, false); msg != "" {
			return msg
		}
	}

	return ""
}

// checkUnderscorePositions checks a single digit string for proper underscore
// placement. An underscore must not be:
//   - At the start or end of the string
//   - Adjacent to a non-digit, non-underscore character
//
// Consecutive underscores (__) are allowed.
func checkUnderscorePositions(s string, isHex bool) string {
	if len(s) == 0 {
		return ""
	}

	// No leading underscore
	if s[0] == '_' {
		return "leading underscore in numeric literal"
	}
	// No trailing underscore
	if s[len(s)-1] == '_' {
		return "trailing underscore in numeric literal"
	}

	// Check each underscore's neighbors
	for i := 0; i < len(s); i++ {
		if s[i] == '_' {
			// Check left neighbor (must be digit or underscore)
			if i > 0 {
				left := s[i-1]
				if left != '_' && !isDigitByte(left) && !(isHex && isHexDigitByte(left)) {
					return "underscore must appear between two digits"
				}
			}
			// Check right neighbor (must be digit or underscore)
			if i < len(s)-1 {
				right := s[i+1]
				if right != '_' && !isDigitByte(right) && !(isHex && isHexDigitByte(right)) {
					return "underscore must appear between two digits"
				}
			}
		}
	}
	return ""
}

// isDigitByte checks if a byte is a decimal digit.
func isDigitByte(b byte) bool {
	return b >= '0' && b <= '9'
}

// isHexDigitByte checks if a byte is a hex digit (0-9, a-f, A-F).
func isHexDigitByte(b byte) bool {
	return isDigitByte(b) || (b >= 'a' && b <= 'f') || (b >= 'A' && b <= 'F')
}

func isIdentStart(ch int) bool {
	return ch == '_' || (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')
}

func isIdentPart(ch int) bool {
	return isIdentStart(ch) || isDigit(ch)
}

func isDigit(ch int) bool {
	return ch >= '0' && ch <= '9'
}

func isHexDigit(ch int) bool {
	return isDigit(ch) || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F')
}

func hexVal(ch int) int {
	switch {
	case ch >= '0' && ch <= '9':
		return ch - '0'
	case ch >= 'a' && ch <= 'f':
		return ch - 'a' + 10
	case ch >= 'A' && ch <= 'F':
		return ch - 'A' + 10
	default:
		return 0
	}
}

// lookupKeyword returns the token kind for a keyword or identifier.
func lookupKeyword(ident string) TokenKind {
	switch ident {
	case "func":
		return TokenFunc
	case "if":
		return TokenIf
	case "else":
		return TokenElse
	case "while":
		return TokenWhile
	case "for":
		return TokenFor
	case "in":
		return TokenIn
	case "return":
		return TokenReturn
	case "break":
		return TokenBreak
	case "continue":
		return TokenContinue
	case "true", "false":
		return TokenBoolLiteral
	case "null":
		return TokenNull
	case "package":
		return TokenPackage
	case "import":
		return TokenImport
	case "use":
		return TokenUse
	case "bool":
		return TokenBool
	case "byte":
		return TokenByte
	case "int":
		return TokenInt
	case "float":
		return TokenFloat
	case "char":
		return TokenChar
	case "string":
		return TokenString
	case "void":
		return TokenVoid
	case "List":
		return TokenList
	case "switch":
		return TokenSwitch
	case "case":
		return TokenCase
	case "default":
		return TokenDefault
	case "Map":
		return TokenMap
	case "try":
		return TokenTry
	case "catch":
		return TokenCatch
	case "finally":
		return TokenFinally
	case "throw":
		return TokenThrow
	case "exception":
		return TokenException
	case "mut":
		return TokenMut
	case "enum":
		return TokenEnum
	default:
		return TokenIdentifier
	}
}

// Unread for advancing needs handling - for now we just lex forward

// IsSignificantNewline returns true if a TokenNewline should be treated as
// significant (statement-terminating). This will be used by the parser.
// For now, all newlines are significant unless suppressed by context.
var IsSignificantNewline = true
