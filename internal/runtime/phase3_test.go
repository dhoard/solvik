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

// ===== 3.1 Core Module =====

func TestCoreBool(t *testing.T) {
	sourceText := `package example
def main() -> int {
    b: bool = core.bool(1)
    if b == true {
        return 0
    }
    return 1
}
`
	result := CompileAndExecute("test.sol", sourceText, DefaultOptions())
	if result.Error != nil {
		t.Fatalf("runtime error: %v", result.Error)
	}
	if result.Value.Kind != vm.ValueInt || result.Value.Int() != 0 {
		t.Fatalf("expected 0, got %v", result.Value)
	}
}

func TestCoreTypeOf(t *testing.T) {
	sourceText := `package example
def main() -> int {
    t: string = core.typeOf(42)
    if t == "int" {
        return 0
    }
    return 1
}
`
	result := CompileAndExecute("test.sol", sourceText, DefaultOptions())
	if result.Error != nil {
		t.Fatalf("runtime error: %v", result.Error)
	}
	if result.Value.Kind != vm.ValueInt || result.Value.Int() != 0 {
		t.Fatalf("expected 0, got %v", result.Value)
	}
}

// ===== 3.2 String Module =====

func TestStringLength(t *testing.T) {
	sourceText := `package example
def main() -> int {
    n: int = string.length("hello")
    if n == 5 {
        return 0
    }
    return 1
}
`
	result := CompileAndExecute("test.sol", sourceText, DefaultOptions())
	if result.Error != nil {
		t.Fatalf("runtime error: %v", result.Error)
	}
	if result.Value.Kind != vm.ValueInt || result.Value.Int() != 0 {
		t.Fatalf("expected 0, got %v", result.Value)
	}
}

func TestStringContains(t *testing.T) {
	sourceText := `package example
def main() -> int {
    has: bool = string.contains("hello world", "world")
    if has == true {
        return 0
    }
    return 1
}
`
	result := CompileAndExecute("test.sol", sourceText, DefaultOptions())
	if result.Error != nil {
		t.Fatalf("runtime error: %v", result.Error)
	}
	if result.Value.Kind != vm.ValueInt || result.Value.Int() != 0 {
		t.Fatalf("expected 0, got %v", result.Value)
	}
}

func TestStringStartsWith(t *testing.T) {
	sourceText := `package example
def main() -> int {
    if string.startsWith("hello", "hel") {
        return 0
    }
    return 1
}
`
	result := CompileAndExecute("test.sol", sourceText, DefaultOptions())
	if result.Error != nil {
		t.Fatalf("runtime error: %v", result.Error)
	}
	if result.Value.Kind != vm.ValueInt || result.Value.Int() != 0 {
		t.Fatalf("expected 0, got %v", result.Value)
	}
}

func TestStringEndsWith(t *testing.T) {
	sourceText := `package example
def main() -> int {
    if string.endsWith("hello", "lo") {
        return 0
    }
    return 1
}
`
	result := CompileAndExecute("test.sol", sourceText, DefaultOptions())
	if result.Error != nil {
		t.Fatalf("runtime error: %v", result.Error)
	}
	if result.Value.Kind != vm.ValueInt || result.Value.Int() != 0 {
		t.Fatalf("expected 0, got %v", result.Value)
	}
}

func TestStringIndexOf(t *testing.T) {
	sourceText := `package example
def main() -> int {
    idx: int = string.indexOf("hello", "l")
    if idx == 2 {
        return 0
    }
    return 1
}
`
	result := CompileAndExecute("test.sol", sourceText, DefaultOptions())
	if result.Error != nil {
		t.Fatalf("runtime error: %v", result.Error)
	}
	if result.Value.Kind != vm.ValueInt || result.Value.Int() != 0 {
		t.Fatalf("expected 0, got %v", result.Value)
	}
}

func TestStringToUpper(t *testing.T) {
	sourceText := `package example
def main() -> int {
    s: string = string.toUpper("hello")
    if s == "HELLO" {
        return 0
    }
    return 1
}
`
	result := CompileAndExecute("test.sol", sourceText, DefaultOptions())
	if result.Error != nil {
		t.Fatalf("runtime error: %v", result.Error)
	}
	if result.Value.Kind != vm.ValueInt || result.Value.Int() != 0 {
		t.Fatalf("expected 0, got %v", result.Value)
	}
}

func TestStringTrim(t *testing.T) {
	sourceText := `package example
def main() -> int {
    s: string = string.trim("  hello  ")
    if s == "hello" {
        return 0
    }
    return 1
}
`
	result := CompileAndExecute("test.sol", sourceText, DefaultOptions())
	if result.Error != nil {
		t.Fatalf("runtime error: %v", result.Error)
	}
	if result.Value.Kind != vm.ValueInt || result.Value.Int() != 0 {
		t.Fatalf("expected 0, got %v", result.Value)
	}
}

func TestStringSplit(t *testing.T) {
	sourceText := `package example
def main() -> int {
    parts: List<string> = string.split("a,b,c", ",")
    if parts[0] == "a" && parts[1] == "b" && parts[2] == "c" {
        return 0
    }
    return 1
}
`
	result := CompileAndExecute("test.sol", sourceText, DefaultOptions())
	if result.Error != nil {
		t.Fatalf("runtime error: %v", result.Error)
	}
	if result.Value.Kind != vm.ValueInt || result.Value.Int() != 0 {
		t.Fatalf("expected 0, got %v", result.Value)
	}
}

func TestStringSubstring(t *testing.T) {
	sourceText := `package example
def main() -> int {
    s: string = string.substring("hello", 1, 4)
    if s == "ell" {
        return 0
    }
    return 1
}
`
	result := CompileAndExecute("test.sol", sourceText, DefaultOptions())
	if result.Error != nil {
		t.Fatalf("runtime error: %v", result.Error)
	}
	if result.Value.Kind != vm.ValueInt || result.Value.Int() != 0 {
		t.Fatalf("expected 0, got %v", result.Value)
	}
}

// ===== 3.3 Math Module =====

func TestMathAbs(t *testing.T) {
	sourceText := `package example
def main() -> int {
    n: double = math.abs(-5.0)
    if n == 5.0 {
        return 0
    }
    return 1
}
`
	result := CompileAndExecute("test.sol", sourceText, DefaultOptions())
	if result.Error != nil {
		t.Fatalf("runtime error: %v", result.Error)
	}
	if result.Value.Kind != vm.ValueInt || result.Value.Int() != 0 {
		t.Fatalf("expected 0, got %v", result.Value)
	}
}

func TestMathMinMax(t *testing.T) {
	sourceText := `package example
def main() -> int {
    a: double = math.min(10.0, 20.0)
    b: double = math.max(10.0, 20.0)
    if a == 10.0 && b == 20.0 {
        return 0
    }
    return 1
}
`
	result := CompileAndExecute("test.sol", sourceText, DefaultOptions())
	if result.Error != nil {
		t.Fatalf("runtime error: %v", result.Error)
	}
	if result.Value.Kind != vm.ValueInt || result.Value.Int() != 0 {
		t.Fatalf("expected 0, got %v", result.Value)
	}
}

func TestMathSqrt(t *testing.T) {
	sourceText := `package example
def main() -> int {
    n: double = math.sqrt(9.0)
    if n > 2.9 && n < 3.1 {
        return 0
    }
    return 1
}
`
	result := CompileAndExecute("test.sol", sourceText, DefaultOptions())
	if result.Error != nil {
		t.Fatalf("runtime error: %v", result.Error)
	}
	if result.Value.Kind != vm.ValueInt || result.Value.Int() != 0 {
		t.Fatalf("expected 0, got %v", result.Value)
	}
}

// ===== 3.4 Time Module =====

func TestTimeNow(t *testing.T) {
	sourceText := `package example
def main() -> int {
    t: long = time.now()
    if t > 0 {
        return 0
    }
    return 1
}
`
	result := CompileAndExecute("test.sol", sourceText, DefaultOptions())
	if result.Error != nil {
		t.Fatalf("runtime error: %v", result.Error)
	}
	if result.Value.Kind != vm.ValueInt || result.Value.Int() != 0 {
		t.Fatalf("expected 0, got %v", result.Value)
	}
}

// ===== Edge Cases =====

func TestBoolOnList(t *testing.T) {
	sourceText := `package example
def main() -> int {
    b: bool = core.bool([1, 2, 3])
    if b == true {
        return 0
    }
    return 1
}
`
	result := CompileAndExecute("test.sol", sourceText, DefaultOptions())
	if result.Error != nil {
		t.Fatalf("runtime error: %v", result.Error)
	}
	if result.Value.Kind != vm.ValueInt || result.Value.Int() != 0 {
		t.Fatalf("expected 0, got %v", result.Value)
	}
}

func TestBoolOnNull(t *testing.T) {
	sourceText := `package example
def main() -> int {
    x: string? = null
    b: bool = core.bool(x)
    if b == false {
        return 0
    }
    return 1
}
`
	result := CompileAndExecute("test.sol", sourceText, DefaultOptions())
	if result.Error != nil {
		t.Fatalf("runtime error: %v", result.Error)
	}
	if result.Value.Kind != vm.ValueInt || result.Value.Int() != 0 {
		t.Fatalf("expected 0, got %v", result.Value)
	}
}

func TestTypeOfAllTypes(t *testing.T) {
	sourceText := `package example
def main() -> int {
    if core.typeOf(null) != "null" { return 1 }
    if core.typeOf(true) != "bool" { return 1 }
    if core.typeOf(42) != "int" { return 1 }
    if core.typeOf("hello") != "string" { return 1 }
    if core.typeOf([1, 2]) != "List" { return 1 }
    return 0
}
`
	result := CompileAndExecute("test.sol", sourceText, DefaultOptions())
	if result.Error != nil {
		t.Fatalf("runtime error: %v", result.Error)
	}
	if result.Value.Kind != vm.ValueInt || result.Value.Int() != 0 {
		t.Fatalf("expected 0, got %v", result.Value)
	}
}

func TestStringJoin(t *testing.T) {
	sourceText := `package example
def main() -> int {
    parts: List<string> = ["a", "b", "c"]
    s: string = string.join(parts, ",")
    if s == "a,b,c" {
        return 0
    }
    return 1
}
`
	result := CompileAndExecute("test.sol", sourceText, DefaultOptions())
	if result.Error != nil {
		t.Fatalf("runtime error: %v", result.Error)
	}
	if result.Value.Kind != vm.ValueInt || result.Value.Int() != 0 {
		t.Fatalf("expected 0, got %v", result.Value)
	}
}

func TestStringCharAt(t *testing.T) {
	sourceText := `package example
def main() -> int {
    c: char = string.charAt("hello", 1)
    if c == 'e' {
        return 0
    }
    return 1
}
`
	result := CompileAndExecute("test.sol", sourceText, DefaultOptions())
	if result.Error != nil {
		t.Fatalf("runtime error: %v", result.Error)
	}
	if result.Value.Kind != vm.ValueInt || result.Value.Int() != 0 {
		t.Fatalf("expected 0, got %v", result.Value)
	}
}

func TestMathPow(t *testing.T) {
	sourceText := `package example
def main() -> int {
    n: double = math.pow(2.0, 3.0)
    if n > 7.9 && n < 8.1 {
        return 0
    }
    return 1
}
`
	result := CompileAndExecute("test.sol", sourceText, DefaultOptions())
	if result.Error != nil {
		t.Fatalf("runtime error: %v", result.Error)
	}
	if result.Value.Kind != vm.ValueInt || result.Value.Int() != 0 {
		t.Fatalf("expected 0, got %v", result.Value)
	}
}
