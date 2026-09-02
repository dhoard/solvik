// Command solvik runs the Solvik language through the Go bytecode compiler and
// stack VM. Python remains the semantic reference implementation.
package main

import (
	"context"
	"fmt"
	"os"

	"github.com/dhoard/solvik-language/internal/native"
	"github.com/dhoard/solvik-language/internal/reference"
	"github.com/dhoard/solvik-language/internal/runtime"
)

const version = "development"

func main() {
	args := os.Args[1:]
	check := false
	versionOnly := false
	files := []string{}
	for _, a := range args {
		switch a {
		case "--check":
			check = true
		case "--version":
			versionOnly = true
		default:
			files = append(files, a)
		}
	}
	if versionOnly {
		fmt.Printf("solvik version %s\n", version)
		os.Exit(0)
	}
	if len(files) == 0 {
		fmt.Fprintln(os.Stderr, "error: a source file is required")
		os.Exit(1)
	}
	path := files[0]
	programArgs := files[1:]
	native.SetProgramArgs(programArgs)
	reference.SetProgramArgs(programArgs)
	if check {
		if err := reference.CheckProgram(path); err != nil {
			fmt.Fprintln(os.Stderr, err)
			os.Exit(1)
		}
		os.Exit(0)
	}
	value, err := runtime.ExecuteSemanticBytecode(context.Background(), path, programArgs)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
	os.Exit(int(value.Int()))
}
