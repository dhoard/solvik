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

package runtime

import (
	"testing"
	"time"

	"github.com/dhoard/solvik-language/internal/lexer"
	"github.com/dhoard/solvik-language/internal/parser"
	"github.com/dhoard/solvik-language/internal/source"
)

func TestParseFib(t *testing.T) {
	sourceText := `package example
func fibonacci(value: int) -> long {
    if value <= 1 {
        return value
    }
    mut previous: long = 0
    mut current: long = 1
    index: int = 2
    while index <= value {
        next: long = previous + current
        previous = current
        current = next
        index = index + 1
    }
    return current
}
`
	done := make(chan bool, 1)
	go func() {
		src := source.NewSourceText("test.sol", sourceText)
		tokens, diags := lexer.New(src).Tokenize()
		if diags.HasErrors() {
			t.Logf("Lex errors: %v", diags.All())
			done <- true
			return
		}
		t.Logf("Tokens: %d", len(tokens))

		prog, parseDiags := parser.New(src, tokens).Parse()
		if parseDiags.HasErrors() {
			t.Logf("Parse errors: %v", parseDiags.All())
			for _, d := range parseDiags.All() {
				t.Logf("  %s: %s at %s", d.Code, d.Message, d.Span)
			}
		} else {
			t.Logf("Parsed OK, functions: %d", len(prog.Funcs))
		}
		done <- true
	}()

	select {
	case <-done:
	case <-time.After(5 * time.Second):
		t.Fatal("TIMEOUT")
	}
}
