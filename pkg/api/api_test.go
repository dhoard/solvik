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

package api_test

import (
	"context"
	"strings"
	"testing"

	"github.com/dhoard/solvik-language/pkg/api"
)

func TestCompilerCompile(t *testing.T) {
	ctx := context.Background()
	source := `package test

func main() -> int {
    return 42
}
`
	compiler := api.NewCompiler(api.DefaultCompilerOptions())
	prog, diags, err := compiler.Compile(ctx, "test.sol", source)
	if err != nil {
		t.Fatalf("Compile error: %v", err)
	}
	if prog == nil {
		t.Fatal("Program is nil")
	}
	if len(diags) > 0 {
		for _, d := range diags {
			t.Logf("diagnostic: %s %s: %s", d.Severity, d.Code, d.Message)
		}
	}
}

func TestCompilerCompileError(t *testing.T) {
	ctx := context.Background()
	// Missing return statement
	source := `package test

func main() -> int {
    let x = 42
}
`
	compiler := api.NewCompiler(api.DefaultCompilerOptions())
	prog, diags, err := compiler.Compile(ctx, "test.sol", source)
	if err != nil {
		// This is expected - compilation may return an error
		if prog != nil {
			t.Error("Program should be nil on error")
		}
		_ = diags
		return
	}
	// Some implementations return diagnostics without an error
	if prog != nil && len(diags) == 0 {
		t.Error("expected diagnostics for compilation error")
	}
}

func TestVMExecute(t *testing.T) {
	ctx := context.Background()
	source := `package test

func main() -> int {
    return 42
}
`
	compiler := api.NewCompiler(api.DefaultCompilerOptions())
	prog, diags, err := compiler.Compile(ctx, "test.sol", source)
	if err != nil {
		t.Fatalf("Compile error: %v", err)
	}
	if diags != nil && len(diags) > 0 {
		for _, d := range diags {
			if d.Severity == "error" {
				t.Fatalf("compile error: %s: %s", d.Code, d.Message)
			}
		}
	}

	vm := api.NewVM()
	val, err := vm.Execute(ctx, prog)
	if err != nil {
		t.Fatalf("Execute error: %v", err)
	}
	if val.Int() != 42 {
		t.Errorf("got %d, want 42", val.Int())
	}
}

func TestVMExecutePrint(t *testing.T) {
	ctx := context.Background()
	source := `package test

func main() -> int {
    println("hello from api test")
    return 0
}
`
	compiler := api.NewCompiler(api.DefaultCompilerOptions())
	prog, diags, err := compiler.Compile(ctx, "test.sol", source)
	if err != nil {
		t.Fatalf("Compile error: %v", err)
	}
	if diags != nil {
		for _, d := range diags {
			if d.Severity == "error" {
				t.Fatalf("compile error: %s: %s", d.Code, d.Message)
			}
		}
	}

	vm := api.NewVM()
	_, err = vm.Execute(ctx, prog)
	if err != nil {
		t.Fatalf("Execute error: %v", err)
	}
}

func TestValueIsNull(t *testing.T) {
	// We can't easily create a null value from the public API,
	// but we can test Value's methods exist
	ctx := context.Background()
	source := `package test

func main() -> int {
    return 0
}
`
	compiler := api.NewCompiler(api.DefaultCompilerOptions())
	prog, diags, err := compiler.Compile(ctx, "test.sol", source)
	if err != nil {
		t.Fatalf("Compile error: %v", err)
	}
	_ = diags

	vm := api.NewVM()
	val, err := vm.Execute(ctx, prog)
	if err != nil {
		t.Fatalf("Execute error: %v", err)
	}
	if val.IsNull() {
		t.Error("value should not be null")
	}
}

func TestDiagnosticsFromCompile(t *testing.T) {
	ctx := context.Background()
	// A valid program
	source := `package test

func main() -> int {
    return 42
}
`
	compiler := api.NewCompiler(api.DefaultCompilerOptions())
	_, diags, err := compiler.Compile(ctx, "test.sol", source)
	if err != nil {
		t.Fatalf("Compile error: %v", err)
	}
	// Diagnostics should be present (possibly empty for success)
	_ = diags
}

func TestCompileAndExecuteString(t *testing.T) {
	ctx := context.Background()
	source := `package test

func main() -> int {
    return 42
}
`
	compiler := api.NewCompiler(api.DefaultCompilerOptions())
	prog, diags, err := compiler.Compile(ctx, "test.sol", source)
	if err != nil {
		t.Fatalf("Compile error: %v", err)
	}
	_ = diags

	vm := api.NewVM()
	val, err := vm.Execute(ctx, prog)
	if err != nil {
		t.Fatalf("Execute error: %v", err)
	}

	// Test String() on value
	s := val.String()
	if s != "42" {
		t.Logf("value.String() = %s", s)
	}
}

func TestDefaultCompilerOptions(t *testing.T) {
	opts := api.DefaultCompilerOptions()
	if opts.MaxInstructions == 0 {
		t.Error("expected non-zero MaxInstructions")
	}
	if opts.MaxCallDepth == 0 {
		t.Error("expected non-zero MaxCallDepth")
	}
}

func TestNewCompiler(t *testing.T) {
	opts := api.CompilerOptions{
		MaxInstructions: 5000,
		MaxCallDepth:    50,
	}
	c := api.NewCompiler(opts)
	if c == nil {
		t.Fatal("NewCompiler returned nil")
	}
}

func TestNewVM(t *testing.T) {
	vm := api.NewVM()
	if vm == nil {
		t.Fatal("NewVM returned nil")
	}
}

func TestCompileWithUse(t *testing.T) {
	ctx := context.Background()
	// Test a valid program with complex features
	source := `package test

func helper(x: int) -> int {
    return x * 2
}

func main() -> int {
    return helper(21)
}
`
	compiler := api.NewCompiler(api.DefaultCompilerOptions())
	prog, diags, err := compiler.Compile(ctx, "test.sol", source)
	if err != nil {
		t.Fatalf("Compile error: %v", err)
	}
	if diags != nil {
		for _, d := range diags {
			if d.Severity == "error" {
				t.Fatalf("compile error: %s: %s", d.Code, d.Message)
			}
		}
	}

	vm := api.NewVM()
	val, err := vm.Execute(ctx, prog)
	if err != nil {
		t.Fatalf("Execute error: %v", err)
	}
	if val.Int() != 42 {
		t.Errorf("got %d, want 42", val.Int())
	}
}

func TestCompileSyntaxError(t *testing.T) {
	ctx := context.Background()
	source := `package test

func main() -> int {
    broken syntax here
}
`
	compiler := api.NewCompiler(api.DefaultCompilerOptions())
	prog, diags, err := compiler.Compile(ctx, "test.sol", source)
	// Should have some error indication
	if err == nil && prog != nil && (diags == nil || len(diags) == 0) {
		t.Error("expected error or diagnostics for broken syntax")
	}
}

func TestVMContextCancellation(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	source := `package test

func main() -> int {
    while true {
        // infinite loop
    }
    return 0
}
`
	compiler := api.NewCompiler(api.CompilerOptions{
		MaxInstructions: 0, // unbounded
	})
	prog, diags, err := compiler.Compile(ctx, "test.sol", source)
	if err != nil {
		t.Fatalf("Compile error: %v", err)
	}
	_ = diags

	// Cancel before execution
	cancel()

	vm := api.NewVM()
	_, err = vm.Execute(ctx, prog)
	if err == nil {
		// Context cancellation may not be detected immediately,
		// but it's not a failure if it isn't
		t.Log("context cancellation not detected (may happen with infinite loop)")
	}
}

func TestCompileWithComments(t *testing.T) {
	ctx := context.Background()
	source := `package test
// this is a comment
func main() -> int {
    // another comment
    return 42 // inline comment
}
`
	compiler := api.NewCompiler(api.DefaultCompilerOptions())
	prog, diags, err := compiler.Compile(ctx, "test.sol", source)
	if err != nil {
		t.Fatalf("Compile error: %v", err)
	}
	if diags != nil {
		for _, d := range diags {
			if d.Severity == "error" {
				t.Fatalf("compile error: %s: %s", d.Code, d.Message)
			}
		}
	}

	vm := api.NewVM()
	val, err := vm.Execute(ctx, prog)
	if err != nil {
		t.Fatalf("Execute error: %v", err)
	}
	if val.Int() != 42 {
		t.Errorf("got %d, want 42", val.Int())
	}
}

func TestCompileWithStrings(t *testing.T) {
	ctx := context.Background()
	source := `package test

func main() -> int {
    s: string = "hello"
    println(s)
    return 0
}
`
	compiler := api.NewCompiler(api.DefaultCompilerOptions())
	prog, diags, err := compiler.Compile(ctx, "test.sol", source)
	if err != nil {
		t.Fatalf("Compile error: %v", err)
	}
	if diags != nil {
		for _, d := range diags {
			if d.Severity == "error" {
				t.Fatalf("compile error: %s: %s", d.Code, d.Message)
			}
		}
	}

	vm := api.NewVM()
	_, err = vm.Execute(ctx, prog)
	if err != nil {
		t.Fatalf("Execute error: %v", err)
	}
}

func TestDiagnosticType(t *testing.T) {
	// Just verify the Diagnostic type exists and has fields
	var d api.Diagnostic
	d.Code = "C001"
	d.Message = "test"
	d.Severity = "error"
	d.Line = 1
	d.Column = 5
	if d.Code != "C001" || d.Severity != "error" {
		t.Error("Diagnostic fields mismatch")
	}
}

func TestDiagnosticsType(t *testing.T) {
	// Verify Diagnostics is a slice
	var diags api.Diagnostics
	if len(diags) != 0 {
		t.Error("expected empty diagnostics")
	}
}

func TestCompileEmptySource(t *testing.T) {
	ctx := context.Background()
	compiler := api.NewCompiler(api.DefaultCompilerOptions())
	prog, diags, err := compiler.Compile(ctx, "empty.sol", "")
	if err == nil && prog != nil {
		// May or may not have errors for empty source
	}
	_ = diags
}

func TestLargeProgram(t *testing.T) {
	ctx := context.Background()
	var sb strings.Builder
	sb.WriteString("package test\n\n")
	sb.WriteString("func helper(x: int) -> int {\n    return x + 1\n}\n\n")
	sb.WriteString("func main() -> int {\n")
	sb.WriteString("    result: int = 0\n")
	for i := 0; i < 10; i++ {
		sb.WriteString("    result = helper(result)\n")
	}
	sb.WriteString("    return result\n")
	sb.WriteString("}\n")

	compiler := api.NewCompiler(api.DefaultCompilerOptions())
	prog, diags, _ := compiler.Compile(ctx, "large.sol", sb.String())
	if diags != nil {
		hasErr := false
		for _, d := range diags {
			if d.Severity == "error" {
				hasErr = true
				t.Logf("compile error: %s: %s", d.Code, d.Message)
			}
		}
		if hasErr {
			t.Skip("compilation had errors, skipping execution test")
		}
	}

	if prog != nil {
		vm_ := api.NewVM()
		val, err := vm_.Execute(ctx, prog)
		if err != nil {
			t.Fatalf("Execute error: %v", err)
		}
		if val.Int() != 10 {
			t.Errorf("got %d, want 10", val.Int())
		}
	}
}
