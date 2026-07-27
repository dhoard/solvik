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

package compiler

import (
	"fmt"
	"testing"

	"github.com/dhoard/solvik-language/internal/lexer"
	"github.com/dhoard/solvik-language/internal/parser"
	"github.com/dhoard/solvik-language/internal/resolver"
	"github.com/dhoard/solvik-language/internal/source"
)

func TestCompileHello(t *testing.T) {
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

	prog, parseDiags := parser.New(src, tokens).Parse()
	if parseDiags.HasErrors() {
		t.Fatal("parse errors:", parseDiags.All())
	}

	res := resolver.New(src)
	resDiags, err := res.Resolve(prog)
	if err != nil || resDiags.HasErrors() {
		t.Fatal("resolve errors:", resDiags.All())
	}

	comp := New(src)
	bcProg, compDiags := comp.Compile(prog)
	t.Logf("compile diagnostics: %v", compDiags.All())

	for i, fn := range bcProg.Functions {
		t.Logf("Function %d: %s (params=%d, locals=%d, maxstack=%d)",
			i, fn.Name, fn.ParamCount, fn.LocalCount, fn.MaxStack)
		t.Logf("Code length: %d", len(fn.Code))
		for _, b := range fn.Code {
			t.Logf("  %02x", b)
		}
	}

	if compDiags.HasErrors() {
		t.Fatal("compile errors:", compDiags.All())
	}

	fmt.Printf("Program: %+v\n", bcProg)
}
