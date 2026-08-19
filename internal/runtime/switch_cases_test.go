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

// Switch case typing (design decision 3): a case must be assignable to the
// switch type, with regex() and null-on-nullable as the documented exceptions.
// A case that can never match is a compile error (C094). Case comparisons are
// type-aware (a float switch matches an int case) and stack-balanced.
package runtime

import (
	"testing"

	"github.com/dhoard/solvik-language/internal/vm"
)

// Valid switch forms: same-type cases, widening cases, regex, nullable+null.
func TestSwitchCaseForms(t *testing.T) {
	source := `package test
func main() -> int {
    code: int = 200
    switch code {
        case 200 { }
        case 404 { }
        default { }
    }
    f: float = 1.0
    switch f {
        case 1 { return 1 }   // int case widens to float and matches 1.0
        default { }
    }
    s: string = "ERROR [1]: x"
    switch s {
        case regex(r"^ERROR") { }
        default { }
    }
    n: string? = null
    switch n {
        case null { }
        case "a" { }
        default { }
    }
    return 0
}
`
	res := runCheck(t, source)
	if res.Value.Kind != vm.ValueInt || res.Value.Int() != 1 {
		t.Fatalf("expected 1 (float case matched), got %v", res.Value)
	}
}

// A case of a type that can never match the switch type is a compile error.
func TestSwitchCaseTypeMismatch(t *testing.T) {
	sources := []string{
		`package test
func main() -> int {
    x: int = 42
    switch x {
        case "abc" { }
        default { }
    }
    return 0
}
`,
		`package test
func main() -> int {
    x: int = 1
    switch x {
        case null { }
        default { }
    }
    return 0
}
`,
		`package test
func main() -> int {
    x: int = 42
    switch x {
        case 1.5 { }
        default { }
    }
    return 0
}
`,
	}
	for _, src := range sources {
		res := CompileAndExecute("test.sol", src, DefaultOptions())
		if res.Diagnostics == nil || !res.Diagnostics.HasErrors() {
			t.Fatal("expected a compile error for an unmatchable switch case")
		}
		if !hasCode(t, res, "C094") {
			t.Fatalf("expected C094 diagnostic, got %v", res.Diagnostics.All())
		}
	}
}

// Switch dispatch is stack-balanced even when a case body does not return and
// control falls through to the default via the end-of-switch jump.
func TestSwitchStackBalance(t *testing.T) {
	source := `package test
func main() -> int {
    mut sum: int = 0
    for i in [1, 2, 3, 4, 5] {
        switch i {
            case 1 { sum = sum + 10 }
            case 2 { sum = sum + 20 }
            case 3 { sum = sum + 30 }
            default { sum = sum + 100 }
        }
    }
    if sum != 260 { return 0 }  // 10+20+30+100+100
    return 1
}
`
	res := runCheck(t, source)
	if res.Value.Kind != vm.ValueInt || res.Value.Int() != 1 {
		t.Fatalf("expected 1, got %v", res.Value)
	}
}
