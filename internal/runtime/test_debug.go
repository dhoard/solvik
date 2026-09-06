package runtime

import (
	"fmt"
	"testing"
)

func TestDebugMultiReturn(t *testing.T) {
	source := `package test
struct DivisionResult {
    quotient: Int
    remainder: Int
}
func divide(a: Int, b: Int) -> DivisionResult {
    return DivisionResult {
        quotient: a / b,
        remainder: a % b,
    }
}
func main() -> Int {
    result: DivisionResult = divide(10, 3)
    if result.quotient == 3 && result.remainder == 1 {
        return 1
    }
    return 0
}
`
	result := CompileAndExecute("test.sol", source, DefaultOptions())
	fmt.Println("Error:", result.Error)
	if result.Diagnostics != nil {
		for _, d := range result.Diagnostics.All() {
			fmt.Printf("DIAG: %s: %s\n", d.Code, d.Message)
		}
	}
}
