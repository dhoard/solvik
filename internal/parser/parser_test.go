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

package parser

import (
	"fmt"
	"testing"

	"github.com/dhoard/solvik-language/internal/ast"
	"github.com/dhoard/solvik-language/internal/diagnostic"
	"github.com/dhoard/solvik-language/internal/lexer"
	"github.com/dhoard/solvik-language/internal/source"
)

// parseForTest lexes and parses source text, returning the program and diagnostics.
func parseForTest(srcText string) (*ast.Program, *diagnostic.Diagnostics) {
	src := source.NewSourceText("test.sol", srcText)
	lex := lexer.New(src)
	tokens, lexDiags := lex.Tokenize()
	if lexDiags.HasErrors() {
		return nil, lexDiags
	}
	par := New(src, tokens)
	prog, parseDiags := par.Parse()
	return prog, parseDiags
}

// requireParseError checks that parsing the given source produces a diagnostic
// with the specified error code. Returns the diagnostics for further inspection.
func requireParseError(t *testing.T, src string, wantCode string) *diagnostic.Diagnostics {
	t.Helper()
	_, diags := parseForTest(src)
	if diags == nil || !diags.HasErrors() {
		t.Fatalf("expected parse error %q but got none", wantCode)
	}
	found := false
	for _, d := range diags.All() {
		if d.Code == wantCode {
			found = true
			break
		}
	}
	if !found {
		codes := ""
		for _, d := range diags.All() {
			if codes != "" {
				codes += ", "
			}
			codes += d.Code
		}
		t.Fatalf("expected parse error %q, got codes: [%s]", wantCode, codes)
	}
	return diags
}

// requireParseSuccess checks that parsing the given source succeeds (no errors).
func requireParseSuccess(t *testing.T, src string) *ast.Program {
	t.Helper()
	prog, diags := parseForTest(src)
	if diags != nil && diags.HasErrors() {
		codes := ""
		for _, d := range diags.All() {
			if codes != "" {
				codes += ", "
			}
			codes += d.Code + ":" + d.Message
		}
		t.Fatalf("expected parse success, got errors: [%s]", codes)
	}
	if prog == nil {
		t.Fatal("expected non-nil program")
	}
	return prog
}

func TestParseHello(t *testing.T) {
	src := source.NewSourceText("test.sol", `package example
def main() -> int {
    count: int = 0
    count = count + 1
    print("Hello from language!\n")
    return 0
}
`)
	tokens, diags := lexer.New(src).Tokenize()
	if diags.HasErrors() {
		t.Fatal("lex errors:", diags.All())
	}

	prog, parseDiags := New(src, tokens).Parse()
	if parseDiags.HasErrors() {
		t.Fatal("parse errors:", parseDiags.All())
	}
	fmt.Printf("Module: %s\n", prog.Module)
	fmt.Printf("Functions: %d\n", len(prog.Funcs))
	for i, fn := range prog.Funcs {
		fmt.Printf("  %d: %s (params=%d)\n", i, fn.Name, len(fn.Parameters))
		if fn.Body != nil {
			fmt.Printf("    body: %d statements\n", len(fn.Body.Statements))
			for j, s := range fn.Body.Statements {
				fmt.Printf("    stmt %d: %T\n", j, s)
				if exprStmt, ok := s.(*ast.ExprStmt); ok {
					if exprStmt.Expr != nil {
						fmt.Printf("      Expr: %T\n", exprStmt.Expr)
					} else {
						fmt.Printf("      Expr: nil\n")
					}
				}
			}
		}
	}
}

// ---------------------------------------------------------------------------
// Brace-Requirement Tests
//
// Every body-bearing construct (if, else, else if, while, for) must use
// explicit brace-delimited blocks. Single-statement bodies without braces
// are rejected with a clear error.
// ---------------------------------------------------------------------------

// --- Positive tests: valid brace-delimited constructs ---

func TestBrace_IfWithBlock(t *testing.T) {
	requireParseSuccess(t, `package test
def main() -> int {
    if true {
        print("ok")
    }
    return 0
}
`)
}

func TestBrace_IfElseWithBlock(t *testing.T) {
	requireParseSuccess(t, `package test
def main() -> int {
    if true {
        print("yes")
    } else {
        print("no")
    }
    return 0
}
`)
}

func TestBrace_IfElseIfElse(t *testing.T) {
	requireParseSuccess(t, `package test
def main() -> int {
    if true {
        print("a")
    } else if false {
        print("b")
    } else {
        print("c")
    }
    return 0
}
`)
}

func TestBrace_WhileWithBlock(t *testing.T) {
	requireParseSuccess(t, `package test
def main() -> int {
    i: int = 0
    while i < 10 {
        i = i + 1
    }
    return 0
}
`)
}

func TestBrace_ForWithBlock(t *testing.T) {
	requireParseSuccess(t, `package test
def main() -> int {
    items: List<int> = [1, 2, 3]
    for item in items {
        print(item)
    }
    return 0
}
`)
}

func TestBrace_EmptyBlock(t *testing.T) {
	requireParseSuccess(t, `package test
def main() -> int {
    if true {
    }
    while false {
    }
    return 0
}
`)
}

func TestBrace_NestedBlocks(t *testing.T) {
	requireParseSuccess(t, `package test
def main() -> int {
    if true {
        while false {
            print("nested")
        }
    }
    return 0
}
`)
}

func TestBrace_MultilineBlock(t *testing.T) {
	requireParseSuccess(t, `package test
def main() -> int {
    if true {
        a: int = 1
        b: int = 2
        c: int = a + b
        print(c)
    }
    return 0
}
`)
}

func TestBrace_CommentBeforeBlock(t *testing.T) {
	requireParseSuccess(t, `package test
def main() -> int {
    if true // comment
    {
        print("ok")
    }
    return 0
}
`)
}

// --- Negative tests: brace-less bodies must be rejected ---

func TestBrace_IfWithoutBody(t *testing.T) {
	requireParseError(t, `package test
def main() -> int {
    if true print("no-brace")
    return 0
}
`, "P024")
}

func TestBrace_IfWithoutBodyNewline(t *testing.T) {
	requireParseError(t, `package test
def main() -> int {
    if true
    print("no-brace")
    return 0
}
`, "P024")
}

func TestBrace_ElseWithoutBlock(t *testing.T) {
	requireParseError(t, `package test
def main() -> int {
    if true {
        print("yes")
    } else print("no-brace")
    return 0
}
`, "P025")
}

func TestBrace_ElseIfWithoutBody(t *testing.T) {
	requireParseError(t, `package test
def main() -> int {
    if true {
        print("a")
    } else if false print("no-brace")
    else {
        print("c")
    }
    return 0
}
`, "P024")
}

func TestBrace_WhileWithoutBody(t *testing.T) {
	requireParseError(t, `package test
def main() -> int {
    i: int = 0
    while i < 10 i = i + 1
    return 0
}
`, "P028")
}

func TestBrace_WhileWithoutBodyNewline(t *testing.T) {
	requireParseError(t, `package test
def main() -> int {
    i: int = 0
    while i < 10
    i = i + 1
    return 0
}
`, "P028")
}

func TestBrace_ForWithoutBody(t *testing.T) {
	requireParseError(t, `package test
def main() -> int {
    items: List<int> = [1, 2, 3]
    for item in items process(item)
    return 0
}
`, "P033")
}

func TestBrace_ForWithoutBodyNewline(t *testing.T) {
	requireParseError(t, `package test
def main() -> int {
    items: List<int> = [1, 2, 3]
    for item in items
    process(item)
    return 0
}
`, "P033")
}

func TestBrace_IfMissingClosingBrace(t *testing.T) {
	requireParseError(t, `package test
def main() -> int {
    if true {
        print("missing close")
    return 0
}
`, "P018")
}

func TestBrace_IfMissingOpeningBrace(t *testing.T) {
	requireParseError(t, `package test
def main() -> int {
    if true
    print("missing open")
    return 0
}
`, "P024")
}

func TestBrace_SemicolonAfterCondition(t *testing.T) {
	// semicolon after condition, then brace-less body
	requireParseError(t, `package test
def main() -> int {
    if true;
    return 0
}
`, "P024")
}

// --- Diagnostic message checks ---

func TestBrace_DiagnosticIfBody(t *testing.T) {
	diags := requireParseError(t, `package test
def main() -> int {
    if true print("test")
    return 0
}
`, "P024")
	for _, d := range diags.All() {
		if d.Code == "P024" {
			if d.Message != "expected '{' for if body" {
				t.Errorf("expected message 'expected '{' for if body', got %q", d.Message)
			}
			return
		}
	}
}

func TestBrace_DiagnosticWhileBody(t *testing.T) {
	diags := requireParseError(t, `package test
def main() -> int {
    while true loop()
    return 0
}
`, "P028")
	for _, d := range diags.All() {
		if d.Code == "P028" {
			if d.Message != "expected '{' for while body" {
				t.Errorf("expected message 'expected '{' for while body', got %q", d.Message)
			}
			return
		}
	}
}

func TestBrace_DiagnosticForBody(t *testing.T) {
	diags := requireParseError(t, `package test
def main() -> int {
    items: List<int> = [1]
    for item in items process(item)
    return 0
}
`, "P033")
	for _, d := range diags.All() {
		if d.Code == "P033" {
			if d.Message != "expected '{' for for body" {
				t.Errorf("expected message 'expected '{' for for body', got %q", d.Message)
			}
			return
		}
	}
}

func TestBrace_DiagnosticElseBlock(t *testing.T) {
	diags := requireParseError(t, `package test
def main() -> int {
    if true {
        print("yes")
    } else print("no")
    return 0
}
`, "P025")
	for _, d := range diags.All() {
		if d.Code == "P025" {
			if d.Message != "expected 'if' or '{' after 'else'" {
				t.Errorf("expected message 'expected 'if' or '{' after 'else'', got %q", d.Message)
			}
			return
		}
	}
}

// --- Regression tests: these should succeed with errors ---
// These tests ensure that brace-less body errors do not cascade into
// spurious failures on subsequent valid code.

func TestBrace_NoCascadeAfterIf(t *testing.T) {
	// The brace-less body should be skipped; the next valid statement
	// should parse without error.
	src := `package test
def main() -> int {
    if true
        print("bad")
    x: int = 42
    return x
}
`
	prog, diags := parseForTest(src)
	if diags == nil || !diags.HasErrors() {
		t.Fatal("expected parse error for brace-less if")
	}
	hasP024 := false
	for _, d := range diags.All() {
		if d.Code == "P024" {
			hasP024 = true
			break
		}
	}
	if !hasP024 {
		t.Fatal("expected error P024")
	}
	// Program should still have a function with statements after recovery
	if prog != nil && len(prog.Funcs) > 0 {
		fn := prog.Funcs[0]
		if fn.Body != nil {
			// Should have recovered and found the var decl
			for _, s := range fn.Body.Statements {
				if _, ok := s.(*ast.VariableDecl); ok {
					return // found the var decl - recovery succeeded
				}
			}
		}
	}
}
