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
	"fmt"
	"testing"
	"time"

	"github.com/dhoard/solvik-language/internal/checker"
	"github.com/dhoard/solvik-language/internal/diagnostic"
	"github.com/dhoard/solvik-language/internal/lexer"
	"github.com/dhoard/solvik-language/internal/parser"
	"github.com/dhoard/solvik-language/internal/resolver"
	"github.com/dhoard/solvik-language/internal/source"
)

func diagStrings(d *diagnostic.Diagnostics) string {
	if d == nil {
		return "nil"
	}
	return fmt.Sprintf("%v", d.All())
}

func TestStepDebug(t *testing.T) {
	sourceText := `package example
def main() -> int {
    values: List<int> = [10, 20, 30, 40]
    print("Total: " + string(100))
    return 0
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

		prog, parseDiags := parser.New(src, tokens).Parse()
		if parseDiags.HasErrors() {
			t.Logf("Parse errors: %v", parseDiags.All())
			done <- true
			return
		}
		t.Logf("Parsed OK")

		resDiags, resErr := resolver.New(src).Resolve(prog)
		if resErr != nil || (resDiags != nil && resDiags.HasErrors()) {
			t.Logf("Resolve error: %v, diags: %v", resErr, diagStrings(resDiags))
			done <- true
			return
		}
		t.Logf("Resolved OK")

		chkDiags, chkErr := checker.New(src).Check(prog)
		if chkErr != nil || (chkDiags != nil && chkDiags.HasErrors()) {
			t.Logf("Check error: %v, diags: %v", chkErr, diagStrings(chkDiags))
			done <- true
			return
		}
		t.Logf("Checked OK")
		done <- true
	}()

	select {
	case <-done:
	case <-time.After(5 * time.Second):
		t.Fatal("TIMEOUT")
	}
}
