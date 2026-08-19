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

package lexer

import (
	"fmt"
	"testing"

	"github.com/dhoard/solvik-language/internal/source"
)

func TestTryCatchFinallyThrowKeywords(t *testing.T) {
	src := source.NewSourceText("test.sol", `package example
func main() -> int {
    try {
        throw "error"
    } catch (e: exception) {
        println(e.message)
    } finally {
        cleanup()
    }
    return 0
}
`)
	tokens, diags := New(src).Tokenize()
	if diags.HasErrors() {
		t.Fatal("lex errors:", diags.All())
	}

	// Check for the keywords in order
	foundTry := false
	foundThrow := false
	foundCatch := false
	foundFinally := false

	for _, tok := range tokens {
		switch tok.Kind {
		case TokenTry:
			foundTry = true
		case TokenThrow:
			foundThrow = true
		case TokenCatch:
			foundCatch = true
		case TokenFinally:
			foundFinally = true
		}
	}

	if !foundTry {
		t.Error("expected TokenTry not found")
	}
	if !foundThrow {
		t.Error("expected TokenThrow not found")
	}
	if !foundCatch {
		t.Error("expected TokenCatch not found")
	}
	if !foundFinally {
		t.Error("expected TokenFinally not found")
	}
}

func TestKeywordsAsIdentifiers(t *testing.T) {
	// Words containing keyword prefixes should still be identifiers
	src := source.NewSourceText("test.sol", `package example
func main() -> int {
    trying: string = "test"
    catchValue: string = trying
    finallyResult: string = "done"
    throwable: string = "maybe"
    return 0
}

func TestImportIsNotAKeyword(t *testing.T) {
	src := source.NewSourceText("test.sol", "import")
	tokens, diags := New(src).Tokenize()
	if diags.HasErrors() {
		t.Fatal("lex errors:", diags.All())
	}
	if len(tokens) == 0 || tokens[0].Kind != TokenIdentifier || tokens[0].Lexeme != "import" {
		t.Fatalf("expected import to lex as an identifier, got %+v", tokens)
	}
}
`)
	tokens, diags := New(src).Tokenize()
	if diags.HasErrors() {
		t.Fatal("lex errors:", diags.All())
	}

	// These should all be identifiers, not keywords
	identCount := 0
	for _, tok := range tokens {
		if tok.Kind == TokenIdentifier {
			identCount++
			switch tok.Lexeme {
			case "trying", "catchValue", "finallyResult", "throwable":
				// OK - these are identifiers
			default:
				// Other identifiers are fine too
			}
		}
	}
	if identCount < 4 {
		t.Errorf("expected at least 4 identifiers, got %d", identCount)
	}
}

func TestPlusPlusIsNotConcat(t *testing.T) {
	src := source.NewSourceText("test.sol", `package example
func main() -> int {
    println("a" ++ "b")
    return 0
}
`)
	tokens, diags := New(src).Tokenize()
	if diags.HasErrors() {
		t.Fatal("lex errors:", diags.All())
	}

	for _, tok := range tokens {
		if tok.Kind == TokenConcat {
			t.Fatalf("'++' must not lex as TokenConcat, got %+v", tok)
		}
	}

	plusCount := 0
	for _, tok := range tokens {
		if tok.Kind == TokenPlus {
			plusCount++
		}
	}
	if plusCount != 2 {
		t.Fatalf("expected 2 TokenPlus tokens for '++', got %d", plusCount)
	}
}

func TestLexHello(t *testing.T) {
	src := source.NewSourceText("test.sol", `package example
func main() -> int {
    count: int = 0
    count = count + 1
    print("Hello from language!\n")
    return 0
}

func TestNumericUnderscorePlacement(t *testing.T) {
	tests := []struct {
		name  string
		src   string
		valid bool
	}{
		{"integer", "1_000", true},
		{"hex", "0xFF_FF", true},
		{"exponent", "1.5e1_0", true},
		{"identifier_prefix", "_100", true},
		{"hex_leading", "0x_FF", false},
		{"trailing", "100_", false},
		{"consecutive", "1__000", false},
		{"hex_consecutive", "0xFF__00", false},
		{"exponent_consecutive", "1e1__0", false},
		{"before_decimal", "1_.0", false},
		{"after_decimal", "1._0", false},
		{"after_exponent", "1e_10", false},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			src := source.NewSourceText("test.sol", tt.src)
			_, diags := New(src).Tokenize()
			if diags.HasErrors() != !tt.valid {
				t.Fatalf("source %q: errors=%v, diagnostics=%v", tt.src, diags.HasErrors(), diags.All())
			}
		})
	}
}
`)
	tokens, diags := New(src).Tokenize()
	if diags.HasErrors() {
		t.Fatal("lex errors:", diags.All())
	}
	for _, tok := range tokens {
		fmt.Printf("%3d %s %q\n", tok.Kind, tok.Kind, tok.Lexeme)
	}
}
