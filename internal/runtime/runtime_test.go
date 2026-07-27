package runtime

import (
	"testing"

	"github.com/dhoard/solvik-language/internal/vm"
)

func TestTryCatchFinally(t *testing.T) {
	tests := []struct {
		name     string
		source   string
		expected int32
	}{
		{
			name: "basic_try_catch",
			source: `package example
func main() -> int {
    mut result: int = 0
    try {
        result = 1
    } catch (e: exception) {
        result = 2
    }
    return result
}
`,
			expected: 1,
		},
		{
			name: "throw_caught",
			source: `package example
func main() -> int {
    mut result: int = 0
    try {
        throw "error"
        result = 1
    } catch (e: exception) {
        result = 2
    }
    return result
}
`,
			expected: 2,
		},
		{
			name: "try_finally_normal",
			source: `package example
func main() -> int {
    mut result: int = 0
    try {
        result = 1
    } finally {
        result = 2
    }
    return result
}
`,
			expected: 2,
		},
		{
			name: "catch_exception_message",
			source: `package example
func main() -> int {
    mut msgValue: string = ""
    try {
        throw "hello world"
    } catch (e: exception) {
        msgValue = e.message
    }
    if msgValue == "hello world" {
        return 1
    }
    return 0
}
`,
			expected: 1,
		},
		{
			name: "division_by_zero_caught",
			source: `package example
func main() -> int {
    mut result: int = 0
    try {
        x: int = 10 / 0
    } catch (e: exception) {
        result = 1
    }
    return result
}
`,
			expected: 1,
		},
		{
			name: "nested_try",
			source: `package example
func main() -> int {
    mut result: int = 0
    try {
        try {
            throw "inner"
        } catch (e: exception) {
            result = 1
        }
    } finally {
        result = 2
    }
    return result
}
`,
			expected: 2,
		},
		{
			name: "exception_variable",
			source: `package example
func main() -> int {
    e: exception = "test message"
    if e.message == "test message" {
        return 1
    }
    return 0
}
`,
			expected: 1,
		},
		{
			name: "throw_string_variable",
			source: `package example
func main() -> int {
    v: string = "error occurred"
    try {
        throw v
    } catch (e: exception) {
        if e.message == "error occurred" {
            return 1
        }
    }
    return 0
}
`,
			expected: 1,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := CompileAndExecute("test.sol", tt.source, DefaultOptions())
			if result.Error != nil {
				t.Fatal("error:", result.Error)
			}
			if result.Diagnostics != nil && len(result.Diagnostics.All()) > 0 {
				for _, d := range result.Diagnostics.All() {
					t.Logf("diagnostic: %s: %s", d.Code, d.Message)
				}
			}
			if result.Value.Kind != vm.ValueInt || result.Value.Int() != tt.expected {
				t.Fatalf("expected %d, got %v", tt.expected, result.Value)
			}
		})
	}
}

func TestRunHello(t *testing.T) {
	result := CompileAndExecute("test.sol", `package example
func main() -> int {
    mut count: int = 0
    count = count + 1
    print("Hello from language!\n")
    return count
}
`, DefaultOptions())
	if result.Error != nil {
		t.Fatal("error:", result.Error)
	}
	if result.Diagnostics != nil && len(result.Diagnostics.All()) > 0 {
		for _, d := range result.Diagnostics.All() {
			t.Logf("diagnostic: %s: %s", d.Code, d.Message)
		}
	}
	if result.Value.Kind != vm.ValueInt || result.Value.Int() != 1 {
		t.Fatalf("expected 1, got %v", result.Value)
	}
}
