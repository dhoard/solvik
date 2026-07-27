package runtime

import (
	"testing"

	"github.com/dhoard/solvik-language/internal/vm"
)

func TestMultiReturn(t *testing.T) {
	source := `package test
func divide(a: int, b: int) -> int, int {
    return a / b, a % b
}
func main() -> int {
    mut q: int
    mut r: int
    q, r = divide(10, 3)
    if q == 3 && r == 1 {
        return 1
    }
    return 0
}
`
	result := CompileAndExecute("test.sol", source, DefaultOptions())
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
