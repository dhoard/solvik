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

// Nullability model (design decision 1): using a possibly-null value as a
// value raises a catchable null-reference exception (E031). The unwrap
// mechanisms — ?? and if x != null narrowing — and equality with null are
// untouched, and non-null nullable values behave like their base type.
package runtime

import (
	"testing"

	"github.com/dhoard/solvik-language/internal/vm"
)

// Every value use of a null raises a catchable "null reference" exception.
func TestNullReferenceIsCatchable(t *testing.T) {
	source := `package test
func probe() -> int {
    s: string? = null
    try {
        n: int = s.len()
        return 0
    } catch (e: exception) {
        if e.message == "null reference" {
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
		t.Fatalf("expected 1 (caught), got %v", res.Value)
	}
}

// Non-null nullable values behave exactly like their base type.
func TestNonNullNullableValuesWork(t *testing.T) {
	source := `package test
func main() -> int {
    s: string? = "hi"
    if s.len() != 2 { return 0 }
    x: int? = 5
    if (x + 1) != 6 { return 0 }   // result is int?; unwrapped by ??
    p: Point? = Point { a: 1 }
    if p.a != 1 { return 0 }       // member access on non-null nullable
    return 1
}
struct Point { pub a: int }
`
	res := runCheck(t, source)
	if res.Value.Kind != vm.ValueInt || res.Value.Int() != 1 {
		t.Fatalf("expected 1, got %v", res.Value)
	}
}

// ?? unwraps null without raising; equality with null is untouched.
func TestNullUnwrapMechanisms(t *testing.T) {
	source := `package test
func main() -> int {
    s: string? = null
    w: string = s ?? "fallback"
    if w != "fallback" { return 0 }
    if !(s == null) { return 0 }
    // narrowing
    z: string? = "narrowed"
    mut len: int = 0
    if z != null {
        len = z.len()
    }
    if len != 8 { return 0 }
    return 1
}
`
	res := runCheck(t, source)
	if res.Value.Kind != vm.ValueInt || res.Value.Int() != 1 {
		t.Fatalf("expected 1, got %v", res.Value)
	}
}

// Indexing, iteration, and concatenation with a null value are catchable.
func TestNullIndexIterateConcat(t *testing.T) {
	source := `package test
func main() -> int {
    s: string? = null
    mut ok: int = 0
    try { c: char = s[0] } catch (e: exception) { ok = ok + 1 }
    try { for c in s { } } catch (e: exception) { ok = ok + 1 }
    try { r: string = "x" .. s } catch (e: exception) { ok = ok + 1 }
    if ok == 3 {
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
