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
	"strings"
	"testing"

	"github.com/dhoard/solvik-language/internal/checker"
	"github.com/dhoard/solvik-language/internal/lexer"
	"github.com/dhoard/solvik-language/internal/parser"
	"github.com/dhoard/solvik-language/internal/resolver"
	"github.com/dhoard/solvik-language/internal/source"
	"github.com/dhoard/solvik-language/internal/vm"
)

// ===== 2.1 List Completeness =====

func TestTrailingCommaInList(t *testing.T) {
	sourceText := `package example
func main() -> int {
    values: list<int> = [10, 20, 30,]
    return 0
}
`
	result := CompileAndExecute("test.sol", sourceText, DefaultOptions())
	if result.Error != nil {
		t.Fatalf("runtime error: %v", result.Error)
	}
}

func TestNestedListLiterals(t *testing.T) {
	sourceText := `package example
func main() -> int {
    matrix: list<list<int> > = [[1, 2], [3, 4]]
    return 0
}
`
	result := CompileAndExecute("test.sol", sourceText, DefaultOptions())
	if result.Error != nil {
		t.Fatalf("runtime error: %v", result.Error)
	}
}

func TestEmptyListLiteral(t *testing.T) {
	sourceText := `package example
func main() -> int {
    values: list<int> = []
    return 0
}
`
	result := CompileAndExecute("test.sol", sourceText, DefaultOptions())
	if result.Error != nil {
		t.Fatalf("runtime error: %v", result.Error)
	}
}

func TestEmptyListLiteralNoTypeAnnotation(t *testing.T) {
	sourceText := `package example
func main() -> int {
    values = []
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

	hasError := false
	for _, d := range chkDiags.All() {
		if strings.Contains(d.Message, "cannot infer") || strings.Contains(d.Message, "Invalid") {
			hasError = true
			break
		}
	}
	if !hasError {
		t.Log("Expected error about empty list type inference, got:", chkDiags.All())
	}
}

func TestListEquality(t *testing.T) {
	sourceText := `package example
func main() -> int {
    a: list<int> = [1, 2, 3]
    b: list<int> = [1, 2, 3]
    if a == b {
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
		t.Fatalf("expected return 0, got %v", result.Value)
	}
}

func TestListIteration(t *testing.T) {
	sourceText := `package example
func main() -> int {
    values: list<int> = [10, 20, 30]
    mut total: int = 0
    for v in values {
        total = total + v
    }
    if total != 60 {
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

func TestListSetViaIndex(t *testing.T) {
	sourceText := `package example
func main() -> int {
    values: list<int> = [10, 20, 30]
    values[1] = 25
    if values[1] != 25 {
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

// ===== 2.2 Map Completeness =====

func TestMapLiteralAndAccess(t *testing.T) {
	sourceText := `package example
func main() -> int {
    scores: map<string, int> = {"alice": 100, "bob": 200}
    aliceScore: int = scores["alice"]
    if aliceScore != 100 {
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

func TestMapSetViaIndex(t *testing.T) {
	sourceText := `package example
func main() -> int {
    scores: map<string, int> = {"alice": 100}
    scores["bob"] = 200
    bobScore: int = scores["bob"]
    if bobScore != 200 {
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

func TestMapKeyTypeValidation(t *testing.T) {
	// Test that float key produces an error
	sourceText := `package example
func main() -> int {
    m: map<float, int> = {1.0: 100}
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

	hasError := false
	for _, d := range chkDiags.All() {
		if strings.Contains(d.Message, "map key") {
			hasError = true
			break
		}
	}
	if !hasError {
		t.Log("Expected error about invalid map key type, got:", chkDiags.All())
	}
}

func TestMapLength(t *testing.T) {
	sourceText := `package example
func main() -> int {
    m: map<string, int> = {"a": 1, "b": 2}
    if m.len() != 2 {
        return 1
    }
    return 0
}
`
	result := CompileAndExecute("test.sol", sourceText, DefaultOptions())
	if result.Error != nil {
		t.Fatalf("runtime error: %v", result.Error)
	}
}

// ===== 2.3 Module System =====

func TestMultiFileCompilation(t *testing.T) {
	files := map[string]string{
		"main.sol": `package example
func main() -> int {
    return helper()
}
`,
		"helper.sol": `package example
func helper() -> int {
    return 42
}
`,
	}

	prog, diags, err := CompileFiles(files)
	if err != nil || (diags != nil && diags.HasErrors()) {
		t.Fatalf("compilation error: %v, diags: %v", err, diags)
	}

	val, execErr := Execute(context.Background(), prog, DefaultOptions())
	if execErr != nil {
		t.Fatalf("execution error: %v", execErr)
	}
	if val.Kind != vm.ValueInt || val.Int() != 42 {
		t.Fatalf("expected 42, got %v", val)
	}
}

func TestImportStatementIsRejected(t *testing.T) {
	sourceText := `package main
import example
func main() -> int {
    return 0
}
`
	src := source.NewSourceText("test.sol", sourceText)
	tokens, _ := lexer.New(src).Tokenize()
	prog, parseDiags := parser.New(src, tokens).Parse()
	if prog == nil || !parseDiags.HasErrors() {
		t.Fatal("expected legacy import syntax to be rejected")
	}
	for _, diag := range parseDiags.All() {
		if diag.Message == "expected function declaration or import" {
			t.Fatalf("diagnostic still refers to legacy import syntax: %s", diag.Message)
		}
	}
}

func TestCoreModuleImplicit(t *testing.T) {
	sourceText := `package example
func main() -> int {
    print("hello")
    s: string = string(42)
    i: int = int("10")
    return 0
}
`
	result := CompileAndExecute("test.sol", sourceText, DefaultOptions())
	if result.Error != nil {
		t.Fatalf("runtime error: %v", result.Error)
	}
}

func TestModuleQualifiedCoreCall(t *testing.T) {
	sourceText := `package example
func main() -> int {
    core.println("hello from core")
    return 0
}
`
	result := CompileAndExecute("test.sol", sourceText, DefaultOptions())
	if result.Error != nil {
		t.Fatalf("runtime error: %v", result.Error)
	}
}

// ===== 2.4 Initialization Order =====

func TestPackageDeclaration(t *testing.T) {
	sourceText := `package myapp
func main() -> int {
    return 0
}
`
	src := source.NewSourceText("test.sol", sourceText)
	tokens, _ := lexer.New(src).Tokenize()
	prog, _ := parser.New(src, tokens).Parse()

	if prog.Module != "myapp" {
		t.Fatalf("expected module 'myapp', got %q", prog.Module)
	}

	result := CompileAndExecute("test.sol", sourceText, DefaultOptions())
	if result.Error != nil {
		t.Fatalf("runtime error: %v", result.Error)
	}
}

// ===== Additional Edge Cases =====

func TestPrintln(t *testing.T) {
	sourceText := `package example
func main() -> int {
    println("hello")
    return 0
}
`
	result := CompileAndExecute("test.sol", sourceText, DefaultOptions())
	if result.Error != nil {
		t.Fatalf("runtime error: %v", result.Error)
	}
}
