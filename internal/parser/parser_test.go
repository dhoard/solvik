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
	"github.com/dhoard/solvik-language/internal/lexer"
	"github.com/dhoard/solvik-language/internal/source"
)

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
