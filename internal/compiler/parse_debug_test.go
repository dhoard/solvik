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

	"github.com/dhoard/solvik-language/internal/ast"
	"github.com/dhoard/solvik-language/internal/lexer"
	"github.com/dhoard/solvik-language/internal/parser"
	"github.com/dhoard/solvik-language/internal/source"
)

func TestParseDebug(t *testing.T) {
	src := source.NewSourceText("test.sol", `package example
func main() -> int {
    print("Hello!\n")
    return 0
}
`)
	tokens, diags := lexer.New(src).Tokenize()
	if diags.HasErrors() {
		t.Fatal("lex errors:", diags.All())
	}
	t.Logf("Tokens: %d", len(tokens))

	prog, parseDiags := parser.New(src, tokens).Parse()
	if parseDiags.HasErrors() {
		t.Fatal("parse errors:", parseDiags.All())
	}

	t.Logf("Module: %s", prog.Module)
	for i, fn := range prog.Funcs {
		t.Logf("Func %d: %s", i, fn.Name)
		for j, stmt := range fn.Body.Statements {
			t.Logf("  Stmt %d: %T", j, stmt)
			switch s := stmt.(type) {
			case *ast.ExprStmt:
				if s.Expr != nil {
					t.Logf("    Expr: %T", s.Expr)
					if call, ok := s.Expr.(*ast.CallExpr); ok {
						t.Logf("    CallExpr function: %T", call.Function)
						if ident, ok := call.Function.(*ast.Identifier); ok {
							t.Logf("      Identifier: %q", ident.Name)
						}
						t.Logf("    Args: %d", len(call.Args))
					}
				} else {
					t.Logf("    Expr: nil")
				}
			case *ast.ReturnStmt:
				if s.Value != nil {
					t.Logf("    Return value: %T", s.Value)
				}
			}
		}
	}
	_ = fmt.Sprintf
}
