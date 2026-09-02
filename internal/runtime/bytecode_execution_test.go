package runtime

import (
	"testing"

	"github.com/dhoard/solvik-language/internal/bytecode"
)

// The historical benchmark compiler must continue to emit valid ordinary VM
// instructions; semantic source execution is tested through the native
// semantic bytecode runner.
func TestBenchmarkUsesDirectBytecode(t *testing.T) {
	program, diags, err := CompileWithUses("../../benchmark.sol")
	if err != nil || (diags != nil && diags.HasErrors()) {
		t.Fatalf("benchmark compilation failed: %v", err)
	}
	for i, function := range program.Functions {
		instructions, decodeErr := bytecode.DecodeAll(function.Code)
		if decodeErr != nil {
			t.Fatalf("function %d (%s) has invalid bytecode: %v", i, function.Name, decodeErr)
		}
		_ = instructions
	}
}
