package reference

import (
	"fmt"
	"unicode/utf8"
)

// tk enumerates token kinds; the lexer is newline-sensitive like the Python
// reference so statement termination is visible to the parser.
type tk int

const (
	tkEOF tk = iota
	tkError
	tkNewline
	tkSemi
	tkIdent
	tkInt
	tkFloat
	tkString
	tkChar
	tkBool
	tkNull

	// keywords
	tkPackage
	tkUse
	tkStruct
	tkTrait
	tkEnum
	tkFunc
	tkMut
	tkPub
	tkIf
	tkElse
	tkWhile
	tkFor
	tkIn
	tkSwitch
	tkCase
	tkDefault
	tkTry
	tkCatch
	tkFinally
	tkThrow
	tkReturn
	tkBreak
	tkContinue
	tkTrue
	tkFalse

	// operators
	tkCoalesce // ??
	tkConcat   // ..
	tkArrow    // ->
	tkEqEq     // ==
	tkNe       // !=
	tkLt
	tkLe
	tkGt
	tkGe
	tkAndAnd
	tkOrOr
	tkShl
	tkShr
	tkPlus
	tkMinus
	tkStar
	tkSlash
	tkPercent
	tkBang
	tkTilde
	tkAmp
	tkPipe
	tkCaret

	// delimiters
	tkLParen
	tkRParen
	tkLBrace
	tkRBrace
	tkLBracket
	tkRBracket
	tkComma
	tkColon
	tkDot
	tkQuestion
	tkAssign
	tkEllipsis
	tkLT
	tkGT
)

var keywords = map[string]tk{
	"package": tkPackage, "use": tkUse, "struct": tkStruct, "trait": tkTrait,
	"enum": tkEnum, "func": tkFunc, "mut": tkMut, "pub": tkPub,
	"if": tkIf, "else": tkElse, "while": tkWhile, "for": tkFor, "in": tkIn,
	"switch": tkSwitch, "case": tkCase, "default": tkDefault,
	"try": tkTry, "catch": tkCatch, "finally": tkFinally, "throw": tkThrow,
	"return": tkReturn, "break": tkBreak, "continue": tkContinue,
	"true": tkTrue, "false": tkFalse, "null": tkNull,
}

// token is a lexical token; Value holds int64/float64/string/bool/char/nil.
type token struct {
	Kind tk
	Text string
	Val  any
	Pos  SourcePos
}

// charValue wraps a rune so it is distinct from strings in Val.
type charValue string

type lexer struct {
	src      string
	filename string
	i        int
	line     int
	col      int
}

func newLexer(src, filename string) *lexer {
	return &lexer{src: src, filename: filename, line: 1, col: 1}
}

func (l *lexer) pos() SourcePos { return SourcePos{l.filename, l.line, l.col} }

func (l *lexer) peek(n int) byte {
	j := l.i + n
	if j < len(l.src) {
		return l.src[j]
	}
	return 0
}

func (l *lexer) advance() byte {
	c := l.peek(0)
	if c == 0 {
		return 0
	}
	l.i++
	if c == '\n' {
		l.line++
		l.col = 1
	} else {
		l.col++
	}
	return c
}

func (l *lexer) match(text string) bool {
	if len(l.src)-l.i >= len(text) && l.src[l.i:l.i+len(text)] == text {
		for range text {
			l.advance()
		}
		return true
	}
	return false
}

func (l *lexer) hasPrefix(text string) bool {
	return len(l.src)-l.i >= len(text) && l.src[l.i:l.i+len(text)] == text
}

func (l *lexer) tokens() []token {
	out := []token{}
	for {
		t := l.nextToken()
		out = append(out, t)
		if t.Kind == tkEOF {
			registerSource(l.filename, l.src)
			return out
		}
	}
}

func (l *lexer) nextToken() token {
	for {
		p := l.pos()
		c := l.peek(0)
		if c == 0 {
			return token{Kind: tkEOF, Pos: p}
		}
		if c == ' ' || c == '\t' || c == '\r' {
			l.advance()
			continue
		}
		if c == '\n' {
			l.advance()
			return token{Kind: tkNewline, Text: "\n", Pos: p}
		}
		if l.hasPrefix("//") {
			for l.peek(0) != '\n' && l.peek(0) != 0 {
				l.advance()
			}
			continue
		}
		if l.hasPrefix("/*") {
			l.blockComment(p)
			continue
		}
		break
	}

	p := l.pos()
	c := l.peek(0)

	// Rust-style raw string: r"...", r#"..."#, ...
	if c == 'r' && (l.peek(1) == '"' || l.peek(1) == '#') {
		if t, ok := l.tryRawString(p); ok {
			return t
		}
	}

	if isAlpha(c) || c == '_' {
		start := l.i
		for isAlphaNum(l.peek(0)) || l.peek(0) == '_' {
			l.advance()
		}
		text := l.src[start:l.i]
		kind, ok := keywords[text]
		if !ok {
			kind = tkIdent
		}
		var val any
		if kind == tkTrue {
			val = true
		} else if kind == tkFalse {
			val = false
		} else if kind == tkNull {
			val = nil
		} else {
			val = text
		}
		return token{Kind: kind, Text: text, Val: val, Pos: p}
	}

	if isDigit(c) {
		return l.number(p)
	}
	if c == '"' {
		return l.quoted(p, '"', tkString)
	}
	if c == '\'' {
		t := l.quoted(p, '\'', tkChar)
		if len([]rune(t.Val.(string))) != 1 {
			panic(parseErr(p, "character literal must contain exactly one Unicode character"))
		}
		return t
	}

	multi := []struct {
		text string
		kind tk
	}{
		{"...", tkEllipsis}, {"??", tkCoalesce}, {"..", tkConcat}, {"->", tkArrow},
		{"==", tkEqEq}, {"!=", tkNe}, {"<=", tkLe}, {">=", tkGe},
		{"&&", tkAndAnd}, {"||", tkOrOr}, {"<<", tkShl}, {">>", tkShr},
	}
	for _, m := range multi {
		if l.match(m.text) {
			return token{Kind: m.kind, Text: m.text, Pos: p}
		}
	}

	singles := map[byte]tk{
		'(': tkLParen, ')': tkRParen, '{': tkLBrace, '}': tkRBrace,
		'[': tkLBracket, ']': tkRBracket, ',': tkComma, ':': tkColon,
		';': tkSemi, '.': tkDot, '?': tkQuestion, '+': tkPlus,
		'-': tkMinus, '*': tkStar, '/': tkSlash, '%': tkPercent,
		'!': tkBang, '~': tkTilde, '&': tkAmp, '|': tkPipe,
		'^': tkCaret, '<': tkLT, '>': tkGT, '=': tkAssign,
	}
	if kind, ok := singles[c]; ok {
		l.advance()
		return token{Kind: kind, Text: string(c), Pos: p}
	}
	panic(parseErr(p, "unexpected character %q", string(rune(c))))
}

func (l *lexer) blockComment(p SourcePos) {
	l.match("/*")
	depth := 1
	for depth > 0 {
		if l.peek(0) == 0 {
			panic(parseErr(p, "unterminated block comment"))
		}
		if l.hasPrefix("/*") {
			l.match("/*")
			depth++
		} else if l.hasPrefix("*/") {
			l.match("*/")
			depth--
		} else {
			l.advance()
		}
	}
}

func (l *lexer) tryRawString(p SourcePos) (token, bool) {
	saveI, saveLine, saveCol := l.i, l.line, l.col
	l.advance() // r
	hashes := 0
	for l.peek(0) == '#' {
		l.advance()
		hashes++
	}
	if l.peek(0) != '"' {
		l.i, l.line, l.col = saveI, saveLine, saveCol
		return token{}, false
	}
	l.advance()
	start := l.i
	endMarker := "\"" + repeatStr("#", hashes)
	for !l.hasPrefix(endMarker) {
		if l.peek(0) == 0 {
			panic(parseErr(p, "unterminated raw string"))
		}
		l.advance()
	}
	value := l.src[start:l.i]
	l.match(endMarker)
	return token{Kind: tkString, Text: value, Val: value, Pos: p}, true
}

func (l *lexer) quoted(p SourcePos, quote byte, kind tk) token {
	l.advance()
	chars := []rune{}
	escapes := map[byte]string{'n': "\n", 't': "\t", 'r': "\r", '\\': "\\", '"': "\"", '\'': "'", '0': "\x00"}
	for {
		c := l.peek(0)
		if c == 0 || c == '\n' {
			panic(parseErr(p, "unterminated string/character literal"))
		}
		if c == quote {
			l.advance()
			break
		}
		if c != '\\' {
			chars = append(chars, l.advanceRune())
			continue
		}
		l.advance()
		e := l.advance()
		if esc, ok := escapes[e]; ok {
			chars = append(chars, []rune(esc)[0])
			continue
		}
		widths := map[byte]int{'x': 2, 'u': 4, 'U': 8}
		if w, ok := widths[e]; ok {
			digits := ""
			for k := 0; k < w; k++ {
				digits += string(rune(l.advance()))
			}
			if !isAllHex(digits) {
				panic(diagErr("L017", p, 5, "invalid hexadecimal digits in \\x escape"))
			}
			var v int64
			fmt.Sscanf(digits, "%x", &v)
			chars = append(chars, rune(v))
			continue
		}
		panic(diagErr("L016", p, 3, "unknown escape sequence '\\%s'", string(rune(e))))
	}
	value := string(chars)
	return token{Kind: kind, Text: value, Val: value, Pos: p}
}

// advanceRune consumes one full UTF-8 rune and returns it.
func (l *lexer) advanceRune() rune {
	r, size := utf8.DecodeRuneInString(l.src[l.i:])
	for i := 0; i < size; i++ {
		l.advance()
	}
	return r
}

func (l *lexer) number(p SourcePos) token {
	start := l.i
	if l.peek(0) == '0' && (lowerByte(l.peek(1)) == 'x' || lowerByte(l.peek(1)) == 'b' || lowerByte(l.peek(1)) == 'o') {
		l.advance()
		prefix := lowerByte(l.advance())
		var valid string
		var base int
		switch prefix {
		case 'x':
			valid, base = "0123456789abcdefABCDEF", 16
		case 'b':
			valid, base = "01", 2
		default:
			valid, base = "01234567", 8
		}
		for stringsContainsByte(valid, l.peek(0)) || l.peek(0) == '_' {
			l.advance()
		}
		text := l.src[start:l.i]
		l.validateNumericUnderscores(text, p)
		v := parseIntBase(replaceAll(text, "_", "")[2:], base)
		return token{Kind: tkInt, Text: text, Val: v, Pos: p}
	}

	sawDot := false
	sawExp := false
	for {
		c := l.peek(0)
		if isDigit(c) || c == '_' {
			l.advance()
			continue
		}
		if c == '.' && l.peek(1) != '.' && !sawDot && !sawExp {
			sawDot = true
			l.advance()
			continue
		}
		if (c == 'e' || c == 'E') && !sawExp {
			sawExp = true
			l.advance()
			if l.peek(0) == '+' || l.peek(0) == '-' {
				l.advance()
			}
			continue
		}
		break
	}
	text := l.src[start:l.i]
	l.validateNumericUnderscores(text, p)
	clean := replaceAll(text, "_", "")
	if sawDot || sawExp {
		var f float64
		fmt.Sscanf(clean, "%g", &f)
		return token{Kind: tkFloat, Text: text, Val: f, Pos: p}
	}
	var v int64
	fmt.Sscanf(clean, "%d", &v)
	return token{Kind: tkInt, Text: text, Val: v, Pos: p}
}

func (l *lexer) validateNumericUnderscores(text string, p SourcePos) {
	if !stringsContainsByte(text, '_') {
		return
	}
	lower := toLower(text)
	var digits string
	if hasPrefixStr(lower, "0x") {
		digits = "0123456789abcdefABCDEF"
	} else if hasPrefixStr(lower, "0b") {
		digits = "01"
	} else if hasPrefixStr(lower, "0o") {
		digits = "01234567"
	} else {
		digits = "0123456789"
	}
	for i := 0; i < len(text); i++ {
		if text[i] != '_' {
			continue
		}
		before, after := byte(0), byte(0)
		if i > 0 {
			before = text[i-1]
		}
		if i+1 < len(text) {
			after = text[i+1]
		}
		if !stringsContainsByte(digits, before) || !stringsContainsByte(digits, after) {
			panic(parseErr(p, "numeric underscores must occur between digits: %s", text))
		}
	}
}
