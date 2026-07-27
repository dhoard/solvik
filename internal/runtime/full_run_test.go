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
	"context"
	"os"
	"testing"

	"github.com/dhoard/solvik-language/internal/vm"
)

func TestFullRun(t *testing.T) {
	data, err := os.ReadFile("../../test/full_test.sol")
	if err != nil {
		t.Fatal("cannot read test file:", err)
	}
	sourceText := string(data)

	// First just compile to see the bytecode
	prog, diags, err := Compile("test.sol", sourceText)
	if err != nil || (diags != nil && diags.HasErrors()) {
		t.Fatal("Compile error:", err, diags)
	}

	for i, fn := range prog.Functions {
		t.Logf("=== Function %d: %s ===", i, fn.Name)
		t.Logf("Params=%d, Locals=%d, MaxStack=%d, Code=%d bytes",
			fn.ParamCount, fn.LocalCount, fn.MaxStack, len(fn.Code))
		for j, c := range fn.Constants {
			t.Logf("  Const %d: kind=%d str=%q", j, c.Kind, c.Str)
		}
		for _, b := range fn.Code {
			t.Logf("  %02x", b)
		}
		t.Logf("")
	}

	opts := DefaultOptions()
	val, execErr := Execute(context.Background(), prog, opts)
	if execErr != nil {
		if rerr, ok := execErr.(*vm.RuntimeError); ok {
			t.Logf("Runtime error: %s", vm.FormatStackTrace(rerr))
		}
		t.Fatal("Execute error:", execErr)
	}
	t.Logf("Result: %v", val)
}
