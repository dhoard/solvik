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

// Package compile handles compiling a solvik source file into a standalone
// self-contained executable binary. The executable embeds all bytecode
// including transitive use dependencies and runs without any external .sol
// files or network access.
package compile

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"

	"github.com/dhoard/solvik-language/internal/diagnostic"
	"github.com/dhoard/solvik-language/internal/runtime"
)

// CompileToExecutable compiles a source file into a standalone executable.
// entryFile is the path to the entry point .sol file.
// outPath is the output executable path.
// arch is the optional target architecture in "os/arch" format (e.g., "linux/amd64").
// When arch is empty, the current platform is used.
func CompileToExecutable(entryFile, outPath, arch string) error {
	// Resolve to absolute paths for consistent module building
	absEntry, err := filepath.Abs(entryFile)
	if err != nil {
		return fmt.Errorf("cannot resolve entry file path: %v", err)
	}

	absOut, err := filepath.Abs(outPath)
	if err != nil {
		return fmt.Errorf("cannot resolve output path: %v", err)
	}

	// Check that output file does not already exist
	if _, err := os.Stat(absOut); err == nil {
		return fmt.Errorf("output file already exists: %s", absOut)
	}

	// Compile the program with all transitive use dependencies
	prog, diags, sources, err := runtime.CompileWithSources(absEntry)

	// Format and print diagnostics against each error's own file
	if diags != nil && len(diags.All()) > 0 {
		for _, d := range diags.All() {
			fmt.Fprint(os.Stderr, diagnostic.FormatDiagnostic(d, sources[d.Span.File]))
		}
	}

	if err != nil {
		return fmt.Errorf("compilation failed")
	}
	if diags != nil && diags.HasErrors() {
		return fmt.Errorf("compilation failed")
	}

	// Serialize the bytecode program to a stable binary format
	progData, err := runtime.Serialize(prog)
	if err != nil {
		return fmt.Errorf("serialization failed: %v", err)
	}

	// Find the project root for building the wrapper inside the module
	projectRoot, err := findProjectRoot()
	if err != nil {
		return err
	}

	// Generate and build the executable wrapper
	return buildExecutable(projectRoot, progData, absOut, arch)
}

// buildExecutable generates a Go wrapper source inside the project tree
// (so it can import internal packages), builds it, then removes the
// generated sources.
func buildExecutable(projectRoot string, progData []byte, outPath, arch string) error {
	// Create a temporary subdirectory inside the project root
	tmpDir, err := os.MkdirTemp(projectRoot, ".compile-*")
	if err != nil {
		return fmt.Errorf("cannot create temp directory: %v", err)
	}
	defer os.RemoveAll(tmpDir)

	// Generate the wrapper main.go with embedded bytecode
	mainSrc, err := generateWrapperSource(progData)
	if err != nil {
		return fmt.Errorf("cannot generate wrapper source: %v", err)
	}

	if err := os.WriteFile(filepath.Join(tmpDir, "main.go"), []byte(mainSrc), 0644); err != nil {
		return fmt.Errorf("cannot write main.go: %v", err)
	}

	// Build the wrapper. Run from the project root, targeting the temp dir
	// as a package path. This works because tmpDir is inside the module,
	// so internal packages are accessible.
	relPkg := "." + string(filepath.Separator) + filepath.Base(tmpDir)

	cmd := exec.Command("go", "build", "-o", outPath, "-ldflags=-s -w", relPkg)
	cmd.Dir = projectRoot
	cmd.Env = os.Environ()

	// Set environment for cross-compilation if arch is specified
	if arch != "" {
		parts := strings.SplitN(arch, "/", 2)
		if len(parts) != 2 {
			return fmt.Errorf("invalid architecture format %q: expected os/arch (e.g., linux/amd64)", arch)
		}
		cmd.Env = append(os.Environ(), "GOOS="+parts[0], "GOARCH="+parts[1])
	}

	output, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("build failed: %v\n%s", err, string(output))
	}

	// Make the output executable
	if err := os.Chmod(outPath, 0755); err != nil {
		return fmt.Errorf("cannot set executable permission: %v", err)
	}

	return nil
}

// generateWrapperSource creates the Go source for the executable wrapper.
// The wrapper deserialises the embedded bytecode and runs the program with
// the same CLI flags as the main solvik tool.
func generateWrapperSource(progData []byte) (string, error) {
	var buf strings.Builder
	buf.WriteString("package main\n\n")
	buf.WriteString("import (\n")
	buf.WriteString("\t\"context\"\n")
	buf.WriteString("\t\"flag\"\n")
	buf.WriteString("\t\"fmt\"\n")
	buf.WriteString("\t\"os\"\n")
	buf.WriteString("\t\"os/signal\"\n")
	buf.WriteString("\t\"strconv\"\n")
	buf.WriteString("\t\"syscall\"\n")
	buf.WriteString("\t\"time\"\n")
	buf.WriteString("\n")
	buf.WriteString("\t\"github.com/dhoard/solvik-language/internal/runtime\"\n")
	buf.WriteString("\t\"github.com/dhoard/solvik-language/internal/vm\"\n")
	buf.WriteString(")\n\n")

	// Embed the bytecode as a byte slice
	buf.WriteString("// programBytes contains the serialized solvik bytecode program.\n")
	buf.WriteString("var programBytes = []byte{\n")
	for i, b := range progData {
		if i > 0 && i%16 == 0 {
			buf.WriteString("\n")
		}
		fmt.Fprintf(&buf, "0x%02x, ", b)
	}
	buf.WriteString("\n}\n\n")

	buf.WriteString(`func main() {
	maxInsts := flag.Int64("max-instructions", 0, "maximum instruction count (0 = unbounded)")
	maxDepth := flag.Int("max-call-depth", 0, "maximum call depth (0 = unbounded)")
	timeout := flag.String("timeout", "", "execution timeout (e.g., 5s, 100ms)")
	verbose := flag.Bool("verbose", false, "show verbose output")
	flag.Parse()

	// Set up signal handling
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	go func() {
		<-sigCh
		cancel()
	}()

	// Apply timeout if specified
	runCtx := ctx
	if *timeout != "" {
		d, err := parseDuration(*timeout)
		if err != nil {
			fmt.Fprintf(os.Stderr, "error: invalid timeout %q: %v\n", *timeout, err)
			os.Exit(3)
		}
		var c context.CancelFunc
		runCtx, c = context.WithTimeout(ctx, d)
		defer c()
	}

	// Deserialize the embedded program
	prog, err := runtime.Deserialize(programBytes)
	if err != nil {
		fmt.Fprintf(os.Stderr, "error: cannot deserialize program: %v\n", err)
		os.Exit(3)
	}

	// Execute the program
	opts := runtime.DefaultOptions()
	opts.Limits.MaxInstructions = *maxInsts
	opts.Limits.MaxCallDepth = *maxDepth

	if *verbose {
		fmt.Fprintf(os.Stderr, "Running compiled program...\n")
	}

	val, execErr := runtime.Execute(runCtx, prog, opts)
	if execErr != nil {
		fmt.Fprintf(os.Stderr, "error: %v\n", execErr)
		if rerr, ok := execErr.(*vm.RuntimeError); ok {
			fmt.Fprint(os.Stderr, vm.FormatStackTrace(rerr))
		}
		os.Exit(2)
	}

	// main()'s return value is the process exit code (0 for success).
	if val.Kind == vm.ValueInt && val.Int() != 0 {
		os.Exit(int(val.Int()))
	}
}

func parseDuration(s string) (time.Duration, error) {
	if len(s) < 2 {
		return 0, fmt.Errorf("invalid duration: %q", s)
	}
	suffix := s[len(s)-2:]
	if suffix == "ms" {
		val, err := strconv.ParseInt(s[:len(s)-2], 10, 64)
		if err != nil {
			return 0, fmt.Errorf("invalid duration: %q", s)
		}
		return time.Duration(val) * time.Millisecond, nil
	}
	suffix = s[len(s)-1:]
	switch suffix {
	case "s":
		val, err := strconv.ParseInt(s[:len(s)-1], 10, 64)
		if err != nil {
			return 0, fmt.Errorf("invalid duration: %q", s)
		}
		return time.Duration(val) * time.Second, nil
	case "m":
		val, err := strconv.ParseInt(s[:len(s)-1], 10, 64)
		if err != nil {
			return 0, fmt.Errorf("invalid duration: %q", s)
		}
		return time.Duration(val) * time.Minute, nil
	default:
		return 0, fmt.Errorf("invalid duration: %q (use s, ms, or m)", s)
	}
}
`)

	return buf.String(), nil
}

// findProjectRoot locates the directory containing go.mod by walking up from
// the current working directory.
func findProjectRoot() (string, error) {
	dir, err := os.Getwd()
	if err != nil {
		return "", err
	}

	for {
		if _, err := os.Stat(filepath.Join(dir, "go.mod")); err == nil {
			return dir, nil
		}
		parent := filepath.Dir(dir)
		if parent == dir {
			return "", fmt.Errorf("go.mod not found")
		}
		dir = parent
	}
}
