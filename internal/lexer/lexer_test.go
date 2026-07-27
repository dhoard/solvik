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

func TestLexHello(t *testing.T) {
	src := source.NewSourceText("test.sol", `package example
def main() -> int {
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
