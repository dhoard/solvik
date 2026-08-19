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

// Regression tests for category-1 correctness fixes: runtime crashes and
// checker/compiler inconsistencies that violated the documented language.
package runtime

import (
	"strings"
	"testing"

	"github.com/dhoard/solvik-language/internal/vm"
)

func runCheck(t *testing.T, source string) Result {
	t.Helper()
	res := CompileAndExecute("test.sol", source, DefaultOptions())
	if res.Diagnostics != nil && len(res.Diagnostics.All()) > 0 {
		for _, d := range res.Diagnostics.All() {
			t.Logf("diagnostic: %s: %s (span %v)", d.Code, d.Message, d.Span)
		}
	}
	if res.Error != nil || (res.Diagnostics != nil && res.Diagnostics.HasErrors()) {
		t.Fatalf("compilation/execution failed: err=%v", res.Error)
	}
	return res
}

// Variadic spread (list...) must contribute elements, not the list itself.
func TestVariadicSpread(t *testing.T) {
	source := `package test
func sum(values: ...int) -> int {
    mut total: int = 0
    for v in values {
        total = total + v
    }
    return total
}
func main() -> int {
    nums: list<int> = [1, 2, 3]
    a: int = sum(nums...)
    b: int = sum(10, nums...)
    c: int = sum(nums..., 4)
    if a == 6 && b == 16 && c == 10 {
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

// For-in over strings must iterate characters, and string indexing must
// return the character at the index.
func TestStringIterationAndIndex(t *testing.T) {
	source := `package test
func main() -> int {
    mut count: int = 0
    mut sawH: bool = false
    mut sawO: bool = false
    for c in "hello" {
        count = count + 1
        if c == 'h' { sawH = true }
        if c == 'o' { sawO = true }
    }
    if count != 5 || !sawH || !sawO {
        return 0
    }
    c0: char = "hello"[1]
    if c0 != 'e' {
        return 0
    }
    return 1
}
`
	res := runCheck(t, source)
	if res.Value.Kind != vm.ValueInt || res.Value.Int() != 1 {
		t.Fatalf("expected 1, got %v", res.Value)
	}
}

// An empty map literal must infer its type from the declaration context.
func TestEmptyMapLiteral(t *testing.T) {
	source := `package test
func main() -> int {
    m: map<string, int> = {}
    m["a"] = 1
    m["b"] = 2
    if m.len() == 2 && m["a"] == 1 && m["b"] == 2 {
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

// Repeated map element assignments must not leak values on the operand
// stack; a long loop must still produce correct results.
func TestMapAssignmentStackBalance(t *testing.T) {
	source := `package test
func main() -> int {
    m: map<string, int> = {}
    mut i: int = 0
    while i < 2000 {
        m["k" .. string(i)] = i
        i = i + 1
    }
    if m.len() != 2000 || m["k1999"] != 1999 {
        return 0
    }
    return 1
}
`
	res := runCheck(t, source)
	if res.Value.Kind != vm.ValueInt || res.Value.Int() != 1 {
		t.Fatalf("expected 1, got %v", res.Value)
	}
}

// try with return and finally (no catch) must count as a returning function.
func TestTryFinallyReturn(t *testing.T) {
	source := `package test
func f() -> int {
    try {
        return 10
    } finally {
        println("cleanup")
    }
}
func main() -> int {
    if f() == 10 {
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

// Float modulo must compute a float remainder, not silently truncate.
func TestFloatModulo(t *testing.T) {
	source := `package test
func main() -> int {
    r: float = 5.5 % 2.0
    if r == 1.5 {
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

// Mixed int/float comparisons must not panic and must compare numerically.
func TestIntFloatComparison(t *testing.T) {
	source := `package test
func main() -> int {
    a: int = 5
    b: float = 5.0
    c: int = 6
    if a == b && a < c && b < 6.5 {
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

// Struct equality must be structural and independent of string rendering.
func TestStructStructuralEquality(t *testing.T) {
	source := `package test
struct Pair {
    pub a: string
    pub b: int
}
func main() -> int {
    p1: Pair = Pair { a: "x, y", b: 1 }
    p2: Pair = Pair { a: "x, y", b: 1 }
    p3: Pair = Pair { a: "x, y", b: 2 }
    if p1 == p2 && p1 != p3 {
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

// List element types must be validated against the declared element type.
func TestListElementTypeCheck(t *testing.T) {
	source := `package test
func main() -> int {
    l: list<int> = ["a"]
    return l.len()
}
`
	res := CompileAndExecute("test.sol", source, DefaultOptions())
	if res.Diagnostics == nil || !res.Diagnostics.HasErrors() {
		t.Fatal("expected a type error for list element mismatch")
	}
	found := false
	for _, d := range res.Diagnostics.All() {
		if d.Code == "C082" {
			found = true
		}
	}
	if !found {
		t.Fatalf("expected C082 diagnostic, got %v", res.Diagnostics.All())
	}
}

// Map literal entries must be validated against the declared types.
func TestMapLiteralTypeCheck(t *testing.T) {
	source := `package test
func main() -> int {
    m: map<string, int> = { "a": "b" }
    return m.len()
}
`
	res := CompileAndExecute("test.sol", source, DefaultOptions())
	if res.Diagnostics == nil || !res.Diagnostics.HasErrors() {
		t.Fatal("expected a type error for map value mismatch")
	}
	found := false
	for _, d := range res.Diagnostics.All() {
		if d.Code == "C037" {
			found = true
		}
	}
	if !found {
		t.Fatalf("expected C037 diagnostic, got %v", res.Diagnostics.All())
	}
}

// stack() with arguments must report a real diagnostic code (not C0XX).
func TestStackArityCode(t *testing.T) {
	source := `package test
func main() -> int {
    s: stack<int> = stack(5)
    return 0
}
`
	res := CompileAndExecute("test.sol", source, DefaultOptions())
	if res.Diagnostics == nil || !res.Diagnostics.HasErrors() {
		t.Fatal("expected a type error for stack(5)")
	}
	found := false
	for _, d := range res.Diagnostics.All() {
		if d.Code == "C075" {
			found = true
		}
		if strings.Contains(d.Code, "XX") {
			t.Fatalf("placeholder code leaked: %s", d.Code)
		}
	}
	if !found {
		t.Fatalf("expected C075 diagnostic, got %v", res.Diagnostics.All())
	}
}

// A missing main function must be reported by the checker, not only at runtime.
func TestMissingMainDetected(t *testing.T) {
	source := `package test
func helper() -> int {
    return 1
}
`
	_, diags, err := Compile("test.sol", source)
	if err == nil || diags == nil || !diags.HasErrors() {
		t.Fatal("expected a compile error for missing main")
	}
	found := false
	for _, d := range diags.All() {
		if d.Code == "C029" {
			found = true
		}
	}
	if !found {
		t.Fatalf("expected C029 diagnostic, got %v", diags.All())
	}
}

// Multi-byte UTF-8 characters are valid char literals (rune-based, like
// string indexing and iteration).
func TestMultiByteCharLiteral(t *testing.T) {
	source := `package test
func main() -> int {
    m: char = 'é'
    if m == 'é' && int(m) == 233 {
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

// Escape sequences decode correctly in strings and chars.
func TestEscapeSequences(t *testing.T) {
	source := `package test
func main() -> int {
    s: string = "\x41\u0042\U0001F600"
    c1: char = '\x41'
    c2: char = '\u0042'
    if s.len() == 3 && c1 == 'A' && c2 == 'B' {
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

// Unknown escape sequences are compile errors (L016).
func TestUnknownEscapeRejected(t *testing.T) {
	source := `package test
func main() -> int {
    s: string = "a\qb"
    return 0
}
`
	res := CompileAndExecute("test.sol", source, DefaultOptions())
	if res.Diagnostics == nil || !res.Diagnostics.HasErrors() {
		t.Fatal("expected a lex error for unknown escape")
	}
	if !hasCode(t, res, "L016") {
		t.Fatalf("expected L016 diagnostic, got %v", res.Diagnostics.All())
	}
}

// Invalid hex digits in \x and invalid \u/\U escapes are compile errors.
func TestInvalidHexEscapeRejected(t *testing.T) {
	// \x with non-hex digits -> L017
	source := "package test\nfunc main() -> int {\n    s: string = \"\\xZZ\"\n    return 0\n}\n"
	res := CompileAndExecute("test.sol", source, DefaultOptions())
	if res.Diagnostics == nil || !res.Diagnostics.HasErrors() {
		t.Fatal("expected a lex error for \\xZZ")
	}
	if !hasCode(t, res, "L017") {
		t.Fatalf("expected L017 diagnostic for \\xZZ, got %v", res.Diagnostics.All())
	}

	// \u/\U with non-hex digits or invalid code points -> L018
	for _, lit := range []string{`"\uZZZZ"`, `"\U0000ZZZZ"`, `"\uD800"` /* surrogate */} {
		src := "package test\nfunc main() -> int {\n    s: string = " + lit + "\n    return 0\n}\n"
		res := CompileAndExecute("test.sol", src, DefaultOptions())
		if res.Diagnostics == nil || !res.Diagnostics.HasErrors() {
			t.Fatalf("expected a lex error for %s", lit)
		}
		if !hasCode(t, res, "L018") {
			t.Fatalf("expected L018 diagnostic for %s, got %v", lit, res.Diagnostics.All())
		}
	}
}

// Numeric widening applies into nullable targets: float? accepts int/byte
// and int? accepts byte.
func TestNullableWidening(t *testing.T) {
	source := `package test
func main() -> int {
    f: float? = 5
    if (f ?? 0.0) != 5.0 { return 0 }
    g: int? = byte(3)
    if (g ?? -1) != 3 { return 0 }
    h: float? = byte(7)
    if (h ?? 0.0) != 7.0 { return 0 }
    return 1
}
`
	res := runCheck(t, source)
	if res.Value.Kind != vm.ValueInt || res.Value.Int() != 1 {
		t.Fatalf("expected 1, got %v", res.Value)
	}
}

// Characters order by Unicode code point; mixed char/numeric comparisons are
// rejected (chars are opaque, like enums — use int(c) explicitly).
func TestCharOrdering(t *testing.T) {
	source := `package test
func main() -> int {
    if !('a' < 'b') { return 0 }
    if !('z' > 'a') { return 0 }
    if 'z' > 'é' { return 0 }   // 122 > 233 is false
    if !('A' <= 'a') { return 0 }
    if 'x' != 'x' { return 0 }
    return 1
}
`
	res := runCheck(t, source)
	if res.Value.Kind != vm.ValueInt || res.Value.Int() != 1 {
		t.Fatalf("expected 1, got %v", res.Value)
	}
}

// Mixed char/numeric ordering requires an explicit int(c) conversion.
func TestCharNumericOrderingRejected(t *testing.T) {
	source := `package test
func main() -> int {
    b: bool = 'a' < 100
    return 0
}
`
	res := CompileAndExecute("test.sol", source, DefaultOptions())
	if res.Diagnostics == nil || !res.Diagnostics.HasErrors() {
		t.Fatal("expected a compile error for char/int comparison")
	}
	if !hasCode(t, res, "C017") {
		t.Fatalf("expected C017 diagnostic, got %v", res.Diagnostics.All())
	}
}
