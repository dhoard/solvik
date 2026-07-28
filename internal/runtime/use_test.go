package runtime

import (
	"context"
	"testing"
)

func TestUseSimple(t *testing.T) {
	files := map[string]string{
		"main.sol": `package example

use file:helper

func main() -> int {
    return greetFromHelper("test")
}
`,
		"helper.sol": `package example

func greetFromHelper(name: string) -> int {
    return 42
}
`,
	}

	// Write files temporarily
	// Actually, use CompileFiles directly since we don't need disk I/O
	prog, diags, err := CompileFiles(files)
	if err != nil {
		t.Fatalf("compile error: %v, diags: %v", err, diags)
	}
	if diags != nil && diags.HasErrors() {
		for _, d := range diags.All() {
			t.Logf("diag: %s %s", d.Code, d.Message)
		}
	}

	val, execErr := Execute(context.Background(), prog, DefaultOptions())
	if execErr != nil {
		t.Fatalf("exec error: %v", execErr)
	}
	if val.Int() != 42 {
		t.Fatalf("expected 42, got %v", val)
	}
}
