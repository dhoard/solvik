package runtime

// Phase 10.5 semantic-bytecode entrypoint.
//
// The semantic frontend is lowered and executed by the semantic bytecode VM
// in internal/reference. This entrypoint adapts its process contract to the
// existing runtime API; it does not install a host callback in the historical
// typed-AST VM.

import (
	"context"
	"fmt"
	"os"

	"github.com/dhoard/solvik-language/internal/reference"
	"github.com/dhoard/solvik-language/internal/vm"
)

// ExecuteSemanticBytecode compiles and executes a semantic bytecode program.
func ExecuteSemanticBytecode(ctx context.Context, sourcePath string, programArgs []string) (vm.Value, error) {
	_ = ctx
	reference.SetProgramArgs(programArgs)
	code, stdout, err := reference.RunProgram(sourcePath, programArgs)
	fmt.Print(stdout)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
	}
	return vm.NewValueInt(int64(code)), nil
}
