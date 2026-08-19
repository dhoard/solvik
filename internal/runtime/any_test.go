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

// any semantics (design decision 2): downcasting an `any` value to a concrete
// type is checked at runtime and raises a catchable type-mismatch exception
// (E066) on mismatch. isType is the guard idiom. Nullable targets accept null.
package runtime

import (
	"testing"

	"github.com/dhoard/solvik-language/internal/vm"
)

// A correct downcast works; a wrong one raises a catchable type mismatch.
func TestAnyDowncastChecked(t *testing.T) {
	source := `package test
func probe() -> int {
    x: any = 42
    n: int = x
    if n != 42 { return 0 }
    y: any = "hello"
    try {
        m: int = y
        return 0
    } catch (e: exception) {
        if e.message == "type mismatch: expected int, got string" {
            return 1
        }
    }
    return 0
}
func main() -> int {
    return probe()
}
`
	res := runCheck(t, source)
	if res.Value.Kind != vm.ValueInt || res.Value.Int() != 1 {
		t.Fatalf("expected 1, got %v", res.Value)
	}
}

// The isType guard idiom works: the check passes at runtime.
func TestAnyIsTypeGuard(t *testing.T) {
	source := `package test
func main() -> int {
    x: any = 7
    if isType(x, "int") {
        n: int = x
        if n != 7 { return 0 }
        return 1
    }
    return 0
}
`
	res := runCheck(t, source)
	if res.Value.Kind != vm.ValueInt || res.Value.Int() != 1 {
		t.Fatalf("expected 1, got %v", res.Value)
	}
}

// Downcasts are checked in function arguments, returns, and list/map/struct
// elements.
func TestAnyDowncastFlows(t *testing.T) {
	source := `package test
func double(v: int) -> int { return v * 2 }
func returnsAny() -> any { return 99 }
struct Pair { pub a: int }
func main() -> int {
    // argument
    w: any = 21
    if double(w) != 42 { return 0 }
    bad: any = "nope"
    try { r: int = double(bad) } catch (e: exception) { if e.message != "type mismatch: expected int, got string" { return 0 } }
    // return
    ret: int = returnsAny()
    if ret != 99 { return 0 }
    // list element
    el: any = 5
    nums: list<int> = [el, 6]
    if nums.len() != 2 { return 0 }
    badEl: any = "s"
    try { badList: list<int> = [badEl] } catch (e: exception) { if e.message != "type mismatch: expected int, got string" { return 0 } }
    // struct field
    f: any = 3
    p: Pair = Pair { a: f }
    if p.a != 3 { return 0 }
    // map value
    kv: any = 9
    m: map<string, int> = { "k": kv }
    if m["k"] != 9 { return 0 }
    return 1
}
`
	res := runCheck(t, source)
	if res.Value.Kind != vm.ValueInt || res.Value.Int() != 1 {
		t.Fatalf("expected 1, got %v", res.Value)
	}
}

// Nullable targets accept null but reject a wrong non-null type.
func TestAnyNullableTarget(t *testing.T) {
	source := `package test
func main() -> int {
    a: any = null
    opt: int? = a
    if opt ?? -1 != -1 { return 0 }
    b: any = "not int"
    try { opt2: int? = b } catch (e: exception) { if e.message != "type mismatch: expected int, got string" { return 0 } }
    return 1
}
`
	res := runCheck(t, source)
	if res.Value.Kind != vm.ValueInt || res.Value.Int() != 1 {
		t.Fatalf("expected 1, got %v", res.Value)
	}
}

// ?? with an `any` operand must keep the result type `any` so the downcast
// performs a runtime check (no silent type confusion).
func TestAnyInNullCoalescing(t *testing.T) {
	// correct value passes the check
	source := `package test
func main() -> int {
    x: any = "hello"
    s: string? = null
    r: string = s ?? x
    if r != "hello" { return 0 }
    x2: any = 42
    s2: string? = "pref"
    r2: string = s2 ?? x2
    if r2 != "pref" { return 0 }
    return 1
}
`
	res := runCheck(t, source)
	if res.Value.Kind != vm.ValueInt || res.Value.Int() != 1 {
		t.Fatalf("expected 1, got %v", res.Value)
	}

	// a mismatched value raises a catchable type mismatch
	bad := `package test
func main() -> int {
    x: any = 42
    s: string? = null
    r: string = s ?? x
    return 0
}
`
	res2 := CompileAndExecute("test.sol", bad, DefaultOptions())
	if res2.Error == nil {
		t.Fatal("expected a runtime type-mismatch error")
	}
}

// A regex value cannot be used with ?? (it is untyped but non-null).
func TestRegexInNullCoalescingRejected(t *testing.T) {
	source := `package test
func main() -> int {
    r: int = regex("^a") ?? 5
    return 0
}
`
	res := CompileAndExecute("test.sol", source, DefaultOptions())
	if res.Diagnostics == nil || !res.Diagnostics.HasErrors() {
		t.Fatal("expected a compile error for regex with ??")
	}
	if !hasCode(t, res, "C028") {
		t.Fatalf("expected C028 diagnostic, got %v", res.Diagnostics.All())
	}
}
