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

// Null-coalescing operator (??) semantics: A ?? B yields A when A is non-null
// and otherwise evaluates and yields B. Chains select the first non-null
// operand from left to right, evaluation is short-circuiting, and non-null
// falsy values (false, 0, "", empty collections) are not treated as null.
package runtime

import (
	"testing"

	"github.com/dhoard/solvik-language/internal/vm"
)

// Chains of 2, 3, and 4 operands select the first non-null value from left
// to right, including all-null chains that yield null.
func TestCoalesceChains(t *testing.T) {
	source := `package test
func main() -> Int {
    a: Int = 1 ?? 2 ?? 3
    if a != 1 { return 0 }
    b: Int = null ?? 2 ?? 3
    if b != 2 { return 0 }
    c: Int = null ?? null ?? 3
    if c != 3 { return 0 }
    d: Int? = null ?? null ?? null
    if d ?? -1 != -1 { return 0 }
    e: Int = null ?? null ?? null ?? 4
    if e != 4 { return 0 }
    f: Int = 1 ?? 2 ?? 3 ?? 4
    if f != 1 { return 0 }
    return 1
}
`
	res := runCheck(t, source)
	if res.Value.Kind != vm.ValueInt || res.Value.Int() != 1 {
		t.Fatalf("expected 1, got %v", res.Value)
	}
}

// Nullable primitive variables initialize from non-null values.
func TestCoalesceNullablePrimitives(t *testing.T) {
	source := `package test
func main() -> Int {
    a: Int? = 5
    if (a ?? 99) != 5 { return 0 }
    b: Float? = 2.5
    if (b ?? 0.0) != 2.5 { return 0 }
    c: Bool? = true
    if !(c ?? false) { return 0 }
    d: Char? = 'x'
    if (d ?? 'y') != 'x' { return 0 }
    return 1
}
`
	res := runCheck(t, source)
	if res.Value.Kind != vm.ValueInt || res.Value.Int() != 1 {
		t.Fatalf("expected 1, got %v", res.Value)
	}
}

// Evaluation is short-circuiting: the right operand is evaluated only when
// the left operand is null. Division by zero is used as an observable side
// effect (it raises a runtime error when evaluated).
func TestCoalesceShortCircuit(t *testing.T) {
	cases := []struct {
		name   string
		src    string
		expect int64 // 1 = runs, 0 = must fail
	}{
		{
			name: "left non-null skips middle and right",
			src: `package test
func main() -> Int {
    a: String? = "ready"
    r: String = a ?? string(10 / 0)
    return 1
}
`,
			expect: 1,
		},
		{
			name: "middle non-null skips right",
			src: `package test
func main() -> Int {
    r: String = null ?? "ok" ?? string(10 / 0)
    return 1
}
`,
			expect: 1,
		},
		{
			name: "middle evaluated when left is null",
			src: `package test
func main() -> Int {
    r: Int = null ?? (10 / 0) ?? 5
    return 1
}
`,
			expect: 0, // division by zero must be raised
		},
		{
			name: "right evaluated when both are null",
			src: `package test
func main() -> Int {
    r: Int = null ?? null ?? (10 / 0)
    return 1
}
`,
			expect: 0, // division by zero must be raised
		},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			res := CompileAndExecute("test.sol", tc.src, DefaultOptions())
			if tc.expect == 1 {
				if res.Error != nil || (res.Diagnostics != nil && res.Diagnostics.HasErrors()) {
					t.Fatalf("expected success, got error=%v diags=%v", res.Error, res.Diagnostics.All())
				}
			} else {
				if res.Error == nil {
					t.Fatal("expected a runtime error (division by zero)")
				}
			}
		})
	}
}

// Non-null falsy values — false, 0, "", empty collections — are not null and
// are selected by ??. Only the null value triggers the right operand.
func TestCoalesceNonNullFalsyValues(t *testing.T) {
	source := `package test
func main() -> Int {
    z: Int? = null
    a: Int = z ?? 0 ?? 5
    if a != 0 { return 0 }
    b: Bool = false ?? true
    if b { return 0 }
    s: String? = null
    c: String = s ?? "" ?? "d"
    if c.len() != 0 { return 0 }
    l: List<Int> = [] ?? [1, 2]
    if l.len() != 0 { return 0 }
    return 1
}
`
	res := runCheck(t, source)
	if res.Value.Kind != vm.ValueInt || res.Value.Int() != 1 {
		t.Fatalf("expected 1, got %v", res.Value)
	}
}

// Precedence: ?? binds looser than arithmetic, comparisons, and logical
// operators, and tighter than assignment. A ?? B + C parses as A ?? (B + C).
func TestCoalescePrecedence(t *testing.T) {
	source := `package test
func main() -> Int {
    a: Int? = 5
    r1: Int = a ?? 1 + 2
    if r1 != 5 { return 0 }        // a ?? (1 + 2); a non-null -> 5
    b: Int? = null
    r2: Int = b ?? 1 + 2
    if r2 != 3 { return 0 }        // b ?? (1 + 2) -> 3
    r3: Bool = b ?? 1 == 2
    if r3 { return 0 }             // b ?? (1 == 2) -> false
    mut x: Int = 0
    x = b ?? 7
    if x != 7 { return 0 }         // assignment of the whole ??
    return 1
}
`
	res := runCheck(t, source)
	if res.Value.Kind != vm.ValueInt || res.Value.Int() != 1 {
		t.Fatalf("expected 1, got %v", res.Value)
	}
}

// A ?? expression must leave exactly one value on the stack, so it composes
// as an operand in larger expressions (call arguments, returns, indexing).
func TestCoalesceComposition(t *testing.T) {
	source := `package test
func double(x: Int) -> Int { return x * 2 }
func main() -> Int {
    a: Int? = null
    r1: Int = double(a ?? 4)
    if r1 != 8 { return 0 }
    b: Int? = 3
    r2: Int = (b ?? 0) + (a ?? 1)
    if r2 != 4 { return 0 }
    return 1
}
`
	res := runCheck(t, source)
	if res.Value.Kind != vm.ValueInt || res.Value.Int() != 1 {
		t.Fatalf("expected 1, got %v", res.Value)
	}
}

// void, function, and module operands are rejected.
func TestCoalesceRejectsNonValues(t *testing.T) {
	sources := []string{
		`package test
func main() -> Int {
    r: Int = println("x") ?? 1
    return 0
}
`,
		`package test
func main() -> Int {
    r: Int = 1 ?? println("x")
    return 0
}
`,
	}
	for _, src := range sources {
		res := CompileAndExecute("test.sol", src, DefaultOptions())
		if res.Diagnostics == nil || !res.Diagnostics.HasErrors() {
			t.Fatal("expected a compile error for a void ?? operand")
		}
		if !hasCode(t, res, "C028") {
			t.Fatalf("expected C028 diagnostic, got %v", res.Diagnostics.All())
		}
	}
}
