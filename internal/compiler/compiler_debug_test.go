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
	"testing"

	"github.com/dhoard/solvik-language/internal/bytecode"
	"github.com/dhoard/solvik-language/internal/lexer"
	"github.com/dhoard/solvik-language/internal/parser"
	"github.com/dhoard/solvik-language/internal/resolver"
	"github.com/dhoard/solvik-language/internal/source"
)

func TestCompileDebug(t *testing.T) {
	src := source.NewSourceText("test.sol", `package example
def main() -> int {
    print("Hello!\n")
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
	t.Logf("initial nativeMap size: %d", len(comp.nativeMap))
	bcProg, compDiags := comp.Compile(prog)
	t.Logf("after Compile nativeMap size: %d", len(comp.nativeMap))
	for k, v := range comp.nativeMap {
		t.Logf("  native map: %s -> %d", k, v)
	}
	if compDiags.HasErrors() {
		t.Fatal("compile errors:", compDiags.All())
	}

	for i, fn := range bcProg.Functions {
		t.Logf("Function %d: %s", i, fn.Name)
		t.Logf("  Constants: %d", len(fn.Constants))
		for j, c := range fn.Constants {
			t.Logf("    %d: kind=%d str=%q", j, c.Kind, c.Str)
		}
		t.Logf("  Natives registered: %d", len(comp.natives))
		for j, n := range bcProg.Natives {
			t.Logf("    %d: %s.%s (params=%d, return=%v)", j, n.Module, n.Name, n.Params, n.Return)
		}
		t.Logf("  Code bytes:")
		for _, b := range fn.Code {
			t.Logf("    %02x", b)
		}
		// Decode instructions
		offset := 0
		for offset < len(fn.Code) {
			inst, next, err := bytecode.Decode(fn.Code, offset)
			if err != nil {
				t.Fatalf("decode error: %v", err)
			}
			t.Logf("    %04d: %s", inst.Offset, inst.String())
			offset = next
		}
	}
}
