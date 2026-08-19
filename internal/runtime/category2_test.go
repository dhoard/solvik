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

// Regression tests for category-2 fixes: underspecified language questions
// resolved by Solvik's design philosophy (C#-style ?? chaining, Go-style
// numeric prefixes and duplicate rejection, 64-bit enum values, conversions
// that accept what the runtime accepts).
package runtime

import (
	"testing"

	"github.com/dhoard/solvik-language/internal/vm"
)

// ?? chains right-associatively, like C#.
func TestNullCoalescingChain(t *testing.T) {
	source := `package test
func main() -> int {
    a: string? = null
    b: string? = null
    c: string? = "C"
    r1: string = a ?? b ?? c
    r2: string = a ?? b ?? "fallback"
    if r1 == "C" && r2 == "fallback" {
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

// Binary and octal literals are first-class integer literals.
func TestBinaryOctalLiterals(t *testing.T) {
	source := `package test
func main() -> int {
    b: int = 0b101
    o: int = 0o17
    h: int = 0xFF
    us: int = 0b1010_1010
    if b == 5 && o == 15 && h == 255 && us == 170 {
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

// Enum values are full 64-bit integer constants; no silent truncation.
func TestEnumLargeValue(t *testing.T) {
	source := `package test
enum Big {
    A = 5000000000,
    B = 100,
}
func main() -> int {
    if int(Big.A) == 5000000000 && int(Big.B) == 100 {
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

// int/float conversions accept numeric values (int truncates floats) and
// parseable strings, matching byte() and the runtime natives.
func TestIntFloatConversions(t *testing.T) {
	source := `package test
func main() -> int {
    a: int = int(42)
    b: int = int(3.9)
    c: float = float(42)
    d: int = int("123")
    e: float = float("1.5")
    if a == 42 && b == 3 && c == 42.0 && d == 123 && e == 1.5 {
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

// Duplicate top-level functions are compile errors (Go/Rust-style).
func TestDuplicateFunction(t *testing.T) {
	source := `package test
func f() -> int { return 1 }
func f() -> int { return 2 }
func main() -> int { return 0 }
`
	res := CompileAndExecute("test.sol", source, DefaultOptions())
	if res.Diagnostics == nil || !res.Diagnostics.HasErrors() {
		t.Fatal("expected a compile error for duplicate function")
	}
	if !hasCode(t, res, "C090") {
		t.Fatalf("expected C090 diagnostic, got %v", res.Diagnostics.All())
	}
}

// Duplicate struct fields are compile errors.
func TestDuplicateStructField(t *testing.T) {
	source := `package test
struct S {
    pub x: int
    pub x: string
}
func main() -> int { return 0 }
`
	res := CompileAndExecute("test.sol", source, DefaultOptions())
	if res.Diagnostics == nil || !res.Diagnostics.HasErrors() {
		t.Fatal("expected a compile error for duplicate struct field")
	}
	if !hasCode(t, res, "C091") {
		t.Fatalf("expected C091 diagnostic, got %v", res.Diagnostics.All())
	}
}

// Duplicate parameters are compile errors.
func TestDuplicateParameter(t *testing.T) {
	source := `package test
func f(a: int, a: string) -> int { return a }
func main() -> int { return 0 }
`
	res := CompileAndExecute("test.sol", source, DefaultOptions())
	if res.Diagnostics == nil || !res.Diagnostics.HasErrors() {
		t.Fatal("expected a compile error for duplicate parameter")
	}
	if !hasCode(t, res, "C092") {
		t.Fatalf("expected C092 diagnostic, got %v", res.Diagnostics.All())
	}
}

func hasCode(t *testing.T, res Result, code string) bool {
	t.Helper()
	if res.Diagnostics == nil {
		return false
	}
	for _, d := range res.Diagnostics.All() {
		if d.Code == code {
			return true
		}
	}
	return false
}
