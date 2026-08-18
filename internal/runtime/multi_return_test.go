package runtime

import (
	"testing"

	"github.com/dhoard/solvik-language/internal/vm"
)

func TestMultiReturn(t *testing.T) {
	source := `package test
struct DivisionResult {
    pub Quotient: int
    pub Remainder: int
}
func divide(a: int, b: int) -> DivisionResult {
    return DivisionResult {
        Quotient: a / b,
        Remainder: a % b,
    }
}
func main() -> int {
    result: DivisionResult = divide(10, 3)
    if result.Quotient == 3 && result.Remainder == 1 {
        return 1
    }
    return 0
}
`
	result := CompileAndExecute("test.sol", source, DefaultOptions())
	if result.Error != nil {
		t.Log("error:", result.Error)
	}
	if result.Diagnostics != nil && len(result.Diagnostics.All()) > 0 {
		for _, d := range result.Diagnostics.All() {
			t.Logf("diagnostic: %s: %s (span %v)", d.Code, d.Message, d.Span)
		}
	}
	if result.Error != nil || (result.Diagnostics != nil && result.Diagnostics.HasErrors()) {
		t.Fatal("compilation failed")
	}
	if result.Value.Kind != vm.ValueInt || result.Value.Int() != 1 {
		t.Fatalf("expected 1, got %v", result.Value)
	}
}
