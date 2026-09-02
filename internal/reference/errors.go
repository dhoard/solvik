// Package reference is the Go port of the Solvik semantic reference
// (solvik.py). It mirrors the Python implementation statement-for-statement
// where practical: same lexer, AST, canonicalization, parser, static
// validator, tree-walking interpreter, standard library, and loader. The
// Python implementation is the oracle; observable behavior (stdout, exit
// codes, diagnostic codes and messages) must match.
package reference

import "fmt"

// SourcePos identifies a location in a source file.
type SourcePos struct {
	File string
	Line int
	Col  int
}

func (p SourcePos) String() string { return fmt.Sprintf("%s:%d:%d", p.File, p.Line, p.Col) }

// SolvikError is the base error for the reference implementation.
type SolvikError struct{ msg string }

func (e *SolvikError) Error() string { return e.msg }

func newSolvikError(format string, args ...any) *SolvikError {
	return &SolvikError{msg: fmt.Sprintf(format, args...)}
}

// ParseError is a syntax error (no diagnostic code).
type ParseError struct {
	Pos     SourcePos
	Message string
}

func (e *ParseError) Error() string {
	return fmt.Sprintf("%s: parse error: %s", e.Pos, e.Message)
}

func parseErr(pos SourcePos, format string, args ...any) *ParseError {
	return &ParseError{Pos: pos, Message: fmt.Sprintf(format, args...)}
}

// DiagnosticError is a compile-time diagnostic with a stable code.
type DiagnosticError struct {
	Code       string
	Pos        SourcePos
	Message    string
	SpanLength int
	Phase      string
	sourceText string
}

func (e *DiagnosticError) Error() string {
	// Same rendering as the Python reference: code, message, location, source
	// line, and span marker when the source is available.
	path := e.Pos.File
	line := ""
	if src, ok := sourceRegistry[path]; ok {
		if lines := splitLines(src); e.Pos.Line-1 < len(lines) {
			line = lines[e.Pos.Line-1]
		}
	}
	width := len(fmt.Sprint(e.Pos.Line))
	gutter := spaces(width+2) + "|"
	primary := []string{
		fmt.Sprintf("error %s: %s", e.Code, e.Message),
		fmt.Sprintf("  --> %s:%d:%d", displayName(path), e.Pos.Line, e.Pos.Col),
		gutter,
		fmt.Sprintf(" %d | %s", e.Pos.Line, line),
		fmt.Sprintf("%s %s%s", gutter, spaces(e.Pos.Col-1), carets(e.SpanLength)),
		gutter,
	}
	if e.Code == "L016" || e.Code == "L017" {
		endCol := len([]rune(line))
		primary = append(primary,
			"error L005: newline in string literal",
			fmt.Sprintf("  --> %s:%d:%d", displayName(path), e.Pos.Line, endCol),
			gutter,
			fmt.Sprintf(" %d | %s", e.Pos.Line, line),
			fmt.Sprintf("%s %s^", gutter, spaces(endCol-1)),
			gutter,
		)
	}
	out := ""
	for _, part := range primary {
		out += part + "\n"
	}
	if e.Phase == "lex" || e.Phase == "parse" {
		out += fmt.Sprintf("error: %s error in %s", e.Phase, path)
	} else {
		out += "error: compilation failed"
	}
	return out
}

func spaces(n int) string {
	if n < 0 {
		n = 0
	}
	b := make([]byte, n)
	for i := range b {
		b[i] = ' '
	}
	return string(b)
}

func carets(n int) string {
	if n < 1 {
		n = 1
	}
	b := make([]byte, n)
	for i := range b {
		b[i] = '^'
	}
	return string(b)
}

func displayName(path string) string {
	parts := splitString(path, '/')
	if len(parts) >= 2 {
		return parts[len(parts)-2] + "/" + parts[len(parts)-1]
	}
	return path
}

func splitString(s string, sep byte) []string {
	var out []string
	start := 0
	for i := 0; i < len(s); i++ {
		if s[i] == sep {
			out = append(out, s[start:i])
			start = i + 1
		}
	}
	out = append(out, s[start:])
	return out
}

func splitLines(s string) []string {
	var out []string
	start := 0
	for i := 0; i < len(s); i++ {
		if s[i] == '\n' {
			out = append(out, s[start:i])
			start = i + 1
		}
	}
	out = append(out, s[start:])
	return out
}

// sourceRegistry keeps loaded source text for diagnostic rendering.
var sourceRegistry = map[string]string{}

func registerSource(file, text string) {
	if file != "" {
		sourceRegistry[file] = text
	}
}

func diagErr(code string, pos SourcePos, span int, format string, args ...any) *DiagnosticError {
	phase := "compilation"
	if len(code) > 0 && code[0] == 'L' {
		phase = "lex"
	} else if len(code) > 0 && code[0] == 'P' {
		phase = "parse"
	}
	return &DiagnosticError{Code: code, Pos: pos, Message: fmt.Sprintf(format, args...), SpanLength: span, Phase: phase}
}

// RuntimeSignal is a catchable runtime exception carrying a message and code.
type RuntimeSignal struct {
	Message string
	Code    string
}

func (e *RuntimeSignal) Error() string {
	if e.Code != "" {
		return fmt.Sprintf("uncaught exception [%s]: %s", e.Code, e.Message)
	}
	return fmt.Sprintf("uncaught exception: %s", e.Message)
}

func runtimeErr(format string, args ...any) *RuntimeSignal {
	return &RuntimeSignal{Message: fmt.Sprintf(format, args...)}
}

func runtimeErrCode(code, format string, args ...any) *RuntimeSignal {
	return &RuntimeSignal{Message: fmt.Sprintf(format, args...), Code: code}
}

// Control-flow signals (panics in Go, exceptions in Python).
type returnSignal struct{ value any }
type breakSignal struct{}
type continueSignal struct{}
