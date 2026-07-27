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
	"os"
	"testing"
	"time"

	"github.com/dhoard/solvik-language/internal/checker"
	"github.com/dhoard/solvik-language/internal/compiler"
	"github.com/dhoard/solvik-language/internal/lexer"
	"github.com/dhoard/solvik-language/internal/parser"
	"github.com/dhoard/solvik-language/internal/resolver"
	"github.com/dhoard/solvik-language/internal/source"
	"github.com/dhoard/solvik-language/internal/verifier"
)

func TestFullCompile(t *testing.T) {
	data, err := os.ReadFile("../../test/full_test.sol")
	if err != nil {
		t.Fatal("cannot read test file:", err)
	}
	sourceText := string(data)
	t.Logf("Source length: %d", len(sourceText))

	done := make(chan bool, 1)
	go func() {
		src := source.NewSourceText("test.sol", sourceText)
		tokens, diags := lexer.New(src).Tokenize()
		if diags.HasErrors() {
			t.Logf("Lex errors: %v", diags.All())
			done <- true
			return
		}
		t.Logf("Lexed: %d tokens", len(tokens))

		prog, parseDiags := parser.New(src, tokens).Parse()
		if parseDiags.HasErrors() {
			t.Logf("Parse errors: %v", parseDiags.All())
			done <- true
			return
		}
		t.Logf("Parsed OK")

		resDiags, resErr := resolver.New(src).Resolve(prog)
		if resErr != nil || (resDiags != nil && resDiags.HasErrors()) {
			t.Logf("Resolve error: %v, diags: %v", resErr, resDiags)
			done <- true
			return
		}
		t.Logf("Resolved OK")

		chkDiags, chkErr := checker.New(src).Check(prog)
		if chkErr != nil || (chkDiags != nil && chkDiags.HasErrors()) {
			t.Logf("Check error: %v, diags: %v", chkErr, chkDiags)
			done <- true
			return
		}
		t.Logf("Checked OK")

		bcProg, compDiags := compiler.New(src).Compile(prog)
		if compDiags.HasErrors() {
			t.Logf("Compile errors: %v", compDiags.All())
			done <- true
			return
		}
		t.Logf("Compiled OK: %d functions", len(bcProg.Functions))
		for i, fn := range bcProg.Functions {
			t.Logf("  Func %d: %s (params=%d, locals=%d, code=%d bytes)",
				i, fn.Name, fn.ParamCount, fn.LocalCount, len(fn.Code))
		}

		if err := verifier.Verify(bcProg); err != nil {
			t.Logf("Verify error: %v", err)
			done <- true
			return
		}
		t.Logf("Verified OK")

		done <- true
	}()

	select {
	case <-done:
	case <-time.After(10 * time.Second):
		t.Fatal("TIMEOUT")
	}
}
