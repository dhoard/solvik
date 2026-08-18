package conformance

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/dhoard/solvik-language/internal/runtime"
)

func TestLanguageConformanceFixtures(t *testing.T) {
	root := filepath.Join("..", "..", "test", "conformance")

	valid, err := os.ReadDir(filepath.Join(root, "valid"))
	if err != nil {
		t.Fatal(err)
	}
	for _, entry := range valid {
		if entry.IsDir() || !strings.HasSuffix(entry.Name(), ".sol") {
			continue
		}
		name := filepath.Join(root, "valid", entry.Name())
		src, readErr := os.ReadFile(name)
		if readErr != nil {
			t.Fatal(readErr)
		}
		_, diags, compileErr := runtime.Compile(name, string(src))
		if compileErr != nil || (diags != nil && diags.HasErrors()) {
			t.Errorf("valid fixture %s failed: err=%v diags=%v", name, compileErr, diags.All())
		}
	}

	invalid, err := os.ReadDir(filepath.Join(root, "invalid"))
	if err != nil {
		t.Fatal(err)
	}
	for _, entry := range invalid {
		if entry.IsDir() || !strings.HasSuffix(entry.Name(), ".sol") {
			continue
		}
		name := filepath.Join(root, "invalid", entry.Name())
		src, readErr := os.ReadFile(name)
		if readErr != nil {
			t.Fatal(readErr)
		}
		lines := strings.SplitN(string(src), "\n", 2)
		if len(lines) == 0 || !strings.HasPrefix(lines[0], "// expect: ") {
			t.Errorf("invalid fixture %s is missing an expected diagnostic", name)
			continue
		}
		want := strings.TrimSpace(strings.TrimPrefix(lines[0], "// expect: "))
		_, diags, compileErr := runtime.Compile(name, string(src))
		found := false
		if diags != nil {
			for _, d := range diags.All() {
				if d.Code == want {
					found = true
					break
				}
			}
		}
		if !found {
			detail := "<nil>"
			if diags != nil {
				detail = fmt.Sprint(diags.All())
			}
			t.Errorf("invalid fixture %s did not produce %s: err=%v diags=%s", name, want, compileErr, detail)
		}
	}
}
