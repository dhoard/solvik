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

	"github.com/dhoard/solvik-language/internal/vm"
)

func TestRunHello(t *testing.T) {
	result := CompileAndExecute("test.sol", `package example
def main() -> int {
    count: int = 0
    count = count + 1
    print("Hello from language!\n")
    return 0
}
`, DefaultOptions())

	if result.Program != nil {
		t.Log("Bytecode:")
		for i, fn := range result.Program.Functions {
			t.Logf("  Function %d: %s", i, fn.Name)
			for _, b := range fn.Code {
				t.Logf("    %02x", b)
			}
			t.Logf("  Constants:")
			for j, c := range fn.Constants {
				t.Logf("    %d: kind=%d str=%q", j, c.Kind, c.Str)
			}
		}
	}

	if result.Error != nil {
		t.Fatal("runtime error:", result.Error)
	}
	if result.Diagnostics != nil && len(result.Diagnostics.All()) > 0 {
		t.Log("diagnostics:", result.Diagnostics.All())
	}
	if result.Value.Kind != vm.ValueInt || result.Value.Int() != 0 {
		t.Fatalf("expected return 0, got %v", result.Value)
	}
}
