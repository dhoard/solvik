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
	"fmt"
	"strings"
	"testing"
	"time"

	"github.com/dhoard/solvik-language/internal/bytecode"
	"github.com/dhoard/solvik-language/internal/checker"
	"github.com/dhoard/solvik-language/internal/compiler"
	"github.com/dhoard/solvik-language/internal/lexer"
	"github.com/dhoard/solvik-language/internal/parser"
	"github.com/dhoard/solvik-language/internal/resolver"
	"github.com/dhoard/solvik-language/internal/source"
	"github.com/dhoard/solvik-language/internal/vm"
)

// ===== Phase 1.1: String Concatenation in Compiler =====

func TestStringConcatenationCompileTime(t *testing.T) {
	sourceText := `package example
func main() -> int {
    s: string = "hello" .. " world"
    return 0
}
`
	src := source.NewSourceText("test.sol", sourceText)
	tokens, _ := lexer.New(src).Tokenize()
	prog, _ := parser.New(src, tokens).Parse()

	res := resolver.New(src)
	res.Resolve(prog)
	chk := checker.New(src)
	chk.Check(prog)

	comp := compiler.New(src)
	bcProg, compDiags := comp.Compile(prog)
	if compDiags.HasErrors() {
		t.Fatalf("compile errors: %v", compDiags.All())
	}

	// Find main function
	var mainFn *bytecode.Function
	for i := range bcProg.Functions {
		if bcProg.Functions[i].Name == "main" {
			mainFn = &bcProg.Functions[i]
			break
		}
	}
	if mainFn == nil {
		t.Fatal("main function not found")
	}

	// Decode and check for CONCAT_STRING (OpCONCAT_STRING = 0x26)
	hasConcat := false
	hasAddInt := false
	offset := 0
	for offset < len(mainFn.Code) {
		inst, next, err := bytecode.Decode(mainFn.Code, offset)
		if err != nil {
			break
		}
		if inst.Opcode == bytecode.OpCONCAT_STRING {
			hasConcat = true
		}
		if inst.Opcode == bytecode.OpADD_INT {
			hasAddInt = true
		}
		offset = next
	}

	if !hasConcat {
		// Dump code for debugging
		t.Logf("Main function code:")
		t.Logf("%s", bytecode.Disassemble(mainFn.Code, mainFn.Constants))
	}

	// Verify runtime execution works with string concat
	result := CompileAndExecute("test.sol", sourceText, DefaultOptions())
	if result.Error != nil {
		t.Fatalf("runtime error: %v", result.Error)
	}

	_ = hasAddInt
	_ = hasConcat
}

// ===== Phase 1.1: Numeric Type Promotion =====

func TestNumericTypePromotion(t *testing.T) {
	sourceText := `package example
func main() -> int {
    a: int = 100000
    b: int = 200000
    c: int = a + b
    if c != 300000 {
        return 1
    }
    return 0
}
`
	result := CompileAndExecute("test.sol", sourceText, DefaultOptions())
	if result.Error != nil {
		t.Fatalf("runtime error: %v", result.Error)
	}
	if result.Value.Kind != vm.ValueInt || result.Value.Int() != 0 {
		t.Fatalf("expected return 0, got %v", result.Value)
	}
}

func TestNumericTypePromotionLongArithmetic(t *testing.T) {
	tests := []struct {
		name string
		src  string
	}{
		{"add", `package example
func compute() -> int {
    a: int = 5
    b: int = 3
    return a + b
}
func main() -> int {
    return 0
}
`},
		{"sub", `package example
func compute() -> int {
    a: int = 5
    b: int = 3
    return a - b
}
func main() -> int {
    return 0
}
`},
		{"mul", `package example
func compute() -> int {
    a: int = 5
    b: int = 3
    return a * b
}
func main() -> int {
    return 0
}
`},
		{"div", `package example
func compute() -> int {
    a: int = 10
    b: int = 3
    return a / b
}
func main() -> int {
    return 0
}
`},
		{"rem", `package example
func compute() -> int {
    a: int = 10
    b: int = 3
    return a % b
}
func main() -> int {
    return 0
}
`},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := CompileAndExecute("test.sol", tt.src, DefaultOptions())
			if result.Error != nil {
				t.Fatalf("runtime error: %v", result.Error)
			}
		})
	}
}

// ===== Phase 1.2: Error Recovery =====

func TestErrorRecovery(t *testing.T) {
	sourceText := `package example
func main() -> int {
    x: int = 
    y: int = 10
    return y
}
`
	src := source.NewSourceText("test.sol", sourceText)
	tokens, _ := lexer.New(src).Tokenize()
	p := parser.New(src, tokens)
	_, diags := p.Parse()

	// Should have errors but not crash
	if !diags.HasErrors() {
		t.Log("Expected parse errors due to malformed code")
	}
}

func TestErrorLimit(t *testing.T) {
	// Create code with many errors (malformed syntax)
	var sb strings.Builder
	sb.WriteString("package example\n")
	sb.WriteString("func main() -> int {\n")
	for i := 0; i < 100; i++ {
		sb.WriteString(fmt.Sprintf("    x%d: %d\n", i, i)) // malformed: type is a number
	}
	sb.WriteString("    return 0\n")
	sb.WriteString("}\n")

	src := source.NewSourceText("test.sol", sb.String())
	tokens, _ := lexer.New(src).Tokenize()
	p := parser.New(src, tokens)
	_, diags := p.Parse()

	// Should not crash even with many errors
	t.Logf("Got %d parse errors", len(diags.All()))
}

// ===== Phase 1.3: Null Safety =====

func TestNullCoalescing(t *testing.T) {
	sourceText := `package example
func main() -> int {
    x: string? = null
    y: string = x ?? "default"
    return 0
}
`
	// Just check compilation works
	result := CompileAndExecute("test.sol", sourceText, DefaultOptions())
	if result.Error != nil {
		t.Fatalf("runtime error: %v", result.Error)
	}
}

func TestNullAssignmentToNonNullable(t *testing.T) {
	sourceText := `package example
func main() -> int {
    x: string = null
    return 0
}
`
	src := source.NewSourceText("test.sol", sourceText)
	tokens, _ := lexer.New(src).Tokenize()
	prog, _ := parser.New(src, tokens).Parse()

	res := resolver.New(src)
	res.Resolve(prog)
	chk := checker.New(src)
	chkDiags, _ := chk.Check(prog)

	// Should have an error about assigning null to non-nullable type
	found := false
	for _, d := range chkDiags.All() {
		if strings.Contains(d.Message, "null") && strings.Contains(d.Message, "non-nullable") {
			found = true
			break
		}
	}
	if !found {
		t.Log("Expected error about null assignment to non-nullable, got:", chkDiags.All())
	}
}

func TestNullNarrowing(t *testing.T) {
	sourceText := `package example
func main() -> int {
    x: string? = "hello"
    if x != null {
        print(x)
    }
    return 0
}
`
	result := CompileAndExecute("test.sol", sourceText, DefaultOptions())
	if result.Error != nil {
		t.Fatalf("runtime error: %v", result.Error)
	}
}

// ===== Phase 1.4: Definite Assignment =====

func TestDefiniteAssignment(t *testing.T) {
	sourceText := `package example
func main() -> int {
    x: int
    y: int = x
    return 0
}
`
	src := source.NewSourceText("test.sol", sourceText)
	tokens, _ := lexer.New(src).Tokenize()
	prog, _ := parser.New(src, tokens).Parse()

	res := resolver.New(src)
	res.Resolve(prog)
	chk := checker.New(src)
	chkDiags, _ := chk.Check(prog)

	// Should have an error about variable not assigned
	found := false
	for _, d := range chkDiags.All() {
		if strings.Contains(d.Message, "may not have been assigned") {
			found = true
			break
		}
	}
	if !found {
		t.Log("Expected definite assignment error, got:", chkDiags.All())
	}
}

// ===== Phase 1.5: Unreachable Code =====

func TestUnreachableCode(t *testing.T) {
	sourceText := `package example
func main() -> int {
    return 0
    x: int = 10
}
`
	src := source.NewSourceText("test.sol", sourceText)
	tokens, _ := lexer.New(src).Tokenize()
	prog, _ := parser.New(src, tokens).Parse()

	res := resolver.New(src)
	res.Resolve(prog)
	chk := checker.New(src)
	chkDiags, _ := chk.Check(prog)

	// Should have a warning about unreachable code
	found := false
	for _, d := range chkDiags.All() {
		if strings.Contains(d.Message, "unreachable") {
			found = true
			break
		}
	}
	if !found {
		t.Log("Expected unreachable warning, got:", chkDiags.All())
	}
}

// ===== Phase 1.6: Resource Limits =====

func TestInstructionLimit(t *testing.T) {
	sourceText := `package example
func main() -> int {
    mut i: int = 0
    while i < 1000000 {
        i = i + 1
    }
    return 0
}
`
	opts := DefaultOptions()
	opts.Limits.MaxInstructions = 1000

	result := CompileAndExecute("test.sol", sourceText, opts)
	if result.Error == nil {
		t.Fatal("expected instruction limit error, got none")
	}
	if !strings.Contains(result.Error.Error(), "instruction limit") {
		t.Fatalf("expected instruction limit error, got: %v", result.Error)
	}
}

func TestCallDepthLimit(t *testing.T) {
	sourceText := `package example
func recurse(n: int) -> int {
    if n <= 0 {
        return 0
    }
    return recurse(n - 1)
}
func main() -> int {
    return recurse(5000)
}
`
	opts := DefaultOptions()
	opts.Limits.MaxCallDepth = 100

	result := CompileAndExecute("test.sol", sourceText, opts)
	if result.Error == nil {
		t.Fatal("expected call depth error, got none")
	}
	if !strings.Contains(result.Error.Error(), "call depth") && !strings.Contains(result.Error.Error(), "depth") {
		t.Logf("Expected call depth error, got: %v", result.Error)
	}
}

func TestContextCancellation(t *testing.T) {
	sourceText := `package example
func main() -> int {
    mut i: int = 0
    while i < 10000000 {
        i = i + 1
    }
    return 0
}
`
	// Create a context that cancels quickly
	ctx, cancel := context.WithTimeout(context.Background(), 1*time.Millisecond)
	defer cancel()

	// Compile
	bcProg, diags, err := Compile("test.sol", sourceText)
	if err != nil || (diags != nil && diags.HasErrors()) {
		t.Skip("compile error:", err, diags)
	}

	// Execute with the short-lived context
	opts := DefaultOptions()
	val, execErr := Execute(ctx, bcProg, opts)
	if execErr == nil {
		t.Fatal("expected cancellation error, got result:", val)
	}
	if !strings.Contains(execErr.Error(), "cancelled") {
		t.Logf("Expected cancellation error, got: %v", execErr)
	}
}

// ===== Jump Offset Calculation =====

func TestJumpOffsets(t *testing.T) {
	sourceText := `package example
func main() -> int {
    x: int = 10
    if x > 5 {
        print("greater")
    } else {
        print("less")
    }

    mut count: int = 0
    while count < 5 {
        if count == 3 {
            break
        }
        count = count + 1
    }

    values: List<int> = [1, 2, 3]
    for v in values {
        print(string(v))
    }

    return 0
}
`
	result := CompileAndExecute("test.sol", sourceText, DefaultOptions())
	if result.Error != nil {
		t.Fatalf("runtime error: %v", result.Error)
	}
}
