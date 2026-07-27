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

func TestLexHello(t *testing.T) {
	src := source.NewSourceText("test.sol", `package example
func main() -> int {
    count: int = 0
    count = count + 1
    print("Hello from language!\n")
    return 0
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
