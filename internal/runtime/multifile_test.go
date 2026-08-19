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

// Multi-file scope rules (design decision 4): types declared in any file of a
// package are usable in every other file of that package, and only the entry
// file may define main (libraries defining main are a compile error).
package runtime

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/dhoard/solvik-language/internal/vm"
)

const sharedTypesSource = `package app

enum Priority {
    Low,
    High = 10,
}

struct Point {
    pub mut x: int,
    pub mut y: int,

    pub func describe() -> string {
        return "Point(" .. x .. ", " .. y .. ")"
    }

    pub mut func move(dx: int, dy: int) {
        x = x + dx
        y = y + dy
    }
}

trait Named {
    func name() -> string
}

struct Tag {
    pub label: string,

    pub func name() -> string {
        return label
    }
}
`

// Structs, enums, and traits declared in one file of a package are usable in
// every other file of that package.
func TestSamePackageTypeSharing(t *testing.T) {
	mainSource := `package app
use file:types

func showName(n: Named) -> string {
    return n.name()
}

func main() -> int {
    p: Priority = Priority.High
    if int(p) != 10 { return 1 }
    mut pt: Point = Point { x: 3, y: 4 }
    pt.move(1, 2)
    if pt.x != 4 || pt.y != 6 { return 1 }
    t: Tag = Tag { label: "solvik" }
    if showName(t) != "solvik" { return 1 }
    return 0
}
`
	prog, diags, err := CompileFiles(map[string]string{
		"main.sol":  mainSource,
		"types.sol": sharedTypesSource,
	})
	if err != nil || (diags != nil && diags.HasErrors()) {
		t.Fatalf("same-package compile failed: err=%v diags=%v", err, diags.All())
	}
	val, execErr := Execute(t.Context(), prog, DefaultOptions())
	if execErr != nil {
		t.Fatalf("execution failed: %v", execErr)
	}
	if val.Kind != vm.ValueInt || val.Int() != 0 {
		t.Fatalf("expected 0, got %v", val)
	}
}

// Only the entry file may define main; a library defining main is an error,
// so the program entry point never depends on filename sort order.
func TestLibraryMainRejected(t *testing.T) {
	dir := t.TempDir()
	lib := filepath.Join(dir, "lib.sol")
	app := filepath.Join(dir, "app.sol")
	if err := os.WriteFile(lib, []byte(`package lib
func main() -> int { return 99 }
func useful() -> int { return 42 }
`), 0644); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(app, []byte(`package app
use file:lib
func main() -> int {
    return lib.useful()
}
`), 0644); err != nil {
		t.Fatal(err)
	}

	prog, diags, err := CompileWithUses(app)
	_ = prog
	if err == nil || diags == nil || !diags.HasErrors() {
		t.Fatal("expected a compile error for a library defining main")
	}
	found := false
	for _, d := range diags.All() {
		if d.Code == "C093" {
			found = true
		}
	}
	if !found {
		t.Fatalf("expected C093 diagnostic, got %v", diags.All())
	}
}
