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

// TestRawStringRuntime verifies raw strings work end-to-end in the language runtime.
func TestRawStringRuntime(t *testing.T) {
	tests := []struct {
		name     string
		source   string
		expected string
	}{
		{
			"basic",
			`package example
def main() -> int {
    s: string = r"hello"
    print(s)
    return 0
}
`,
			"hello",
		},
		{
			"with_quotes",
			`package example
def main() -> int {
    s: string = r#"hello "world""#
    print(s)
    return 0
}
`,
			`hello "world"`,
		},
		{
			"with_backslash",
			`package example
def main() -> int {
    s: string = r"\n"
    print(s)
    return 0
}
`,
			`\n`, // raw string preserves backslash-n as two characters
		},
		{
			"windows_path",
			`package example
def main() -> int {
    s: string = r"C:\Users\name\file.txt"
    print(s)
    return 0
}
`,
			`C:\Users\name\file.txt`,
		},
		{
			"double_hash",
			`package example
def main() -> int {
    s: string = r##"hello "# world"##
    print(s)
    return 0
}
`,
			`hello "# world`,
		},
		{
			"triple_hash",
			`package example
def main() -> int {
    s: string = r###"a "## b"###
    print(s)
    return 0
}
`,
			`a "## b`,
		},
		{
			"empty",
			`package example
def main() -> int {
    s: string = r""
    print(s)
    return 0
}
`,
			"",
		},
		{
			"multiline",
			`package example
def main() -> int {
    s: string = r#"hello
world"#
    print(s)
    return 0
}
`,
			"hello\nworld",
		},
		{
			"interpolation_looking",
			`package example
def main() -> int {
    s: string = r"${name}"
    print(s)
    return 0
}
`,
			`${name}`,
		},
		{
			"escape_looking",
			`package example
def main() -> int {
    s: string = r"\t\n\r"
    print(s)
    return 0
}
`,
			`\t\n\r`,
		},
		{
			"unicode",
			`package example
def main() -> int {
    s: string = r"Hello, 世界!"
    print(s)
    return 0
}
`,
			"Hello, 世界!",
		},
		{
			"function_arg",
			`package example
def main() -> int {
    n: int = string.length(r"hello")
    if n == 5 {
        return 0
    }
    return 1
}
`,
			"",
		},
		{
			"concatenation",
			`package example
def main() -> int {
    s: string = r"hello" + r" world"
    print(s)
    return 0
}
`,
			"hello world",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := CompileAndExecute("test.sol", tt.source, DefaultOptions())
			if result.Error != nil {
				t.Fatalf("runtime error: %v, diags: %v", result.Error, result.Diagnostics)
			}
		})
	}
}

func TestRawStringEquality(t *testing.T) {
	sourceText := `package example
def main() -> int {
    a: string = r"hello"
    b: string = "hello"
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
		t.Fatalf("expected 0, got %v", result.Value)
	}
}

func TestRawStringInList(t *testing.T) {
	sourceText := `package example
def main() -> int {
    values: List<string> = [r"a", r"b", r"c"]
    if values[0] == "a" && values[1] == "b" && values[2] == "c" {
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

func TestRawStringComparison(t *testing.T) {
	sourceText := `package example
def main() -> int {
    path: string = r"C:\Users\name"
    if path == "C:\\Users\\name" {
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
