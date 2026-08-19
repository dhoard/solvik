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

// solvik is the command-line tool for the solvik toolchain.
package main

import (
	"context"
	"flag"
	"fmt"
	"os"
	"os/signal"
	"strconv"
	"strings"
	"syscall"
	"time"

	"github.com/dhoard/solvik-language/internal/compile"
	"github.com/dhoard/solvik-language/internal/diagnostic"
	"github.com/dhoard/solvik-language/internal/runtime"
	"github.com/dhoard/solvik-language/internal/vm"
)

// Exit codes
const (
	exitSuccess       = 0
	exitCompileError  = 1
	exitRuntimeError  = 2
	exitInternalError = 3
)

// Version is set at build time via GoReleaser ldflags. Default is "development" for local builds.
var Version = "development"

func main() {
	// Define flags
	maxInsts := flag.Int64("max-instructions", 0, "maximum instruction count (0 = unbounded)")
	maxDepth := flag.Int("max-call-depth", 0, "maximum call depth (0 = unbounded)")
	timeout := flag.String("timeout", "", "execution timeout (e.g., 5s, 100ms)")
	verbose := flag.Bool("verbose", false, "show verbose output")
	checkMode := flag.Bool("check", false, "check source for errors")
	showVersion := flag.Bool("version", false, "print version")
	compileFile := flag.String("compile", "", "compile source into standalone executable")
	outFile := flag.String("out", "", "output executable path (required with --compile)")
	arch := flag.String("arch", "", "target architecture (e.g., linux/amd64, darwin/arm64)")

	flag.Usage = printUsage
	flag.Parse()

	// Handle --version
	if *showVersion {
		fmt.Printf("solvik version %s\n", Version)
		os.Exit(exitSuccess)
	}

	// Set up signal handling
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	go func() {
		<-sigCh
		cancel()
	}()

	// Validate --compile / --out / --arch flags
	if *compileFile != "" {
		if *outFile == "" {
			fmt.Fprintf(os.Stderr, "error: --compile requires --out\n")
			os.Exit(exitInternalError)
		}
		if *checkMode {
			fmt.Fprintf(os.Stderr, "error: --compile is mutually exclusive with --check\n")
			os.Exit(exitInternalError)
		}
		if flag.NArg() > 0 {
			fmt.Fprintf(os.Stderr, "error: --compile is mutually exclusive with a positional file argument\n")
			os.Exit(exitInternalError)
		}
		if *arch != "" {
			if err := validateArch(*arch); err != nil {
				fmt.Fprintf(os.Stderr, "error: invalid --arch %q: %v\n", *arch, err)
				os.Exit(exitInternalError)
			}
		}

		if err := compile.CompileToExecutable(*compileFile, *outFile, *arch); err != nil {
			fmt.Fprintf(os.Stderr, "error: %v\n", err)
			os.Exit(exitCompileError)
		}
		fmt.Printf("Compiled executable written to %s\n", *outFile)
		os.Exit(exitSuccess)
	}

	if *outFile != "" {
		fmt.Fprintf(os.Stderr, "error: --out requires --compile\n")
		os.Exit(exitInternalError)
	}

	if *arch != "" {
		fmt.Fprintf(os.Stderr, "error: --arch requires --compile\n")
		os.Exit(exitInternalError)
	}

	// Handle --check mode
	if *checkMode {
		if flag.NArg() != 1 {
			if flag.NArg() == 0 {
				fmt.Fprintf(os.Stderr, "error: expected source file\n")
			} else {
				fmt.Fprintf(os.Stderr, "error: --check accepts exactly one source file, got %d\n", flag.NArg())
			}
			os.Exit(exitInternalError)
		}
		checkSource(flag.Arg(0))
		return
	}

	// Default: execute file
	if flag.NArg() != 1 {
		printUsage()
		os.Exit(exitInternalError)
	}

	runSource(ctx, flag.Arg(0), *maxInsts, *maxDepth, *timeout, *verbose)
}

func printUsage() {
	fmt.Fprintf(os.Stderr, `solvik - Solvik toolchain

Usage:
  solvik [options] <file>         Compile and run a source file
  solvik --check <file>           Check source for errors (without running)
  solvik --version                Print version
  solvik --compile <file> --out <executable> [--arch os/arch]
                                  Compile a source file into a standalone executable

Options:
  --max-instructions N            Maximum instruction count (0 = unbounded)
  --max-call-depth N              Maximum call depth (0 = unbounded)
  --timeout D                     Execution timeout (e.g., 5s, 100ms)
  --verbose                       Show verbose output
  --compile <file>                Compile source into standalone executable
  --out <executable>              Output executable path (required with --compile)
  --arch os/arch                  Target architecture (e.g., linux/amd64, darwin/arm64)
`)
}

func runSource(ctx context.Context, path string, maxInsts int64, maxDepth int, timeout string, verbose bool) {
	// Apply timeout if specified
	runCtx := ctx
	if timeout != "" {
		d, err := parseDuration(timeout)
		if err != nil {
			fmt.Fprintf(os.Stderr, "error: invalid timeout %q: %v\n", timeout, err)
			os.Exit(exitInternalError)
		}
		var cancel context.CancelFunc
		runCtx, cancel = context.WithTimeout(ctx, d)
		defer cancel()
	}

	opts := runtime.DefaultOptions()
	opts.Limits.MaxInstructions = maxInsts
	opts.Limits.MaxCallDepth = maxDepth

	if verbose {
		fmt.Fprintf(os.Stderr, "Compiling %s...\n", path)
	}

	bcProg, diags, sources, err := runtime.CompileWithSources(path)
	if diags != nil && len(diags.All()) > 0 {
		for _, d := range diags.All() {
			fmt.Fprint(os.Stderr, diagnostic.FormatDiagnostic(d, sources[d.Span.File]))
		}
	}
	if err != nil {
		fmt.Fprintf(os.Stderr, "error: %v\n", err)
		os.Exit(exitCompileError)
	}

	val, execErr := runtime.Execute(runCtx, bcProg, opts)
	if execErr != nil {
		fmt.Fprintf(os.Stderr, "error: %v\n", execErr)
		if rerr, ok := execErr.(*vm.RuntimeError); ok {
			fmt.Fprint(os.Stderr, vm.FormatStackTrace(rerr))
		}
		os.Exit(exitRuntimeError)
	}

	// main()'s return value is the process exit code (0 for success).
	if val.Kind == vm.ValueInt && val.Int() != 0 {
		os.Exit(int(val.Int()))
	}
}

func checkSource(path string) {
	prog, diags, sources, err := runtime.CompileWithSources(path)
	if diags != nil && len(diags.All()) > 0 {
		for _, d := range diags.All() {
			fmt.Fprint(os.Stderr, diagnostic.FormatDiagnostic(d, sources[d.Span.File]))
		}
	}
	if err != nil {
		os.Exit(exitCompileError)
	}
	fmt.Println("OK")
	_ = prog
}

// validateArch validates that the architecture string follows Go's GOOS/GOARCH convention.
func validateArch(arch string) error {
	parts := strings.SplitN(arch, "/", 2)
	if len(parts) != 2 {
		return fmt.Errorf("expected format os/arch (e.g., linux/amd64), got %q", arch)
	}
	if parts[0] == "" || parts[1] == "" {
		return fmt.Errorf("expected format os/arch (e.g., linux/amd64), got %q", arch)
	}
	// Basic validation against Go's known GOOS/GOARCH values
	knownOS := map[string]bool{
		"aix": true, "android": true, "darwin": true, "dragonfly": true,
		"freebsd": true, "hurd": true, "illumos": true, "ios": true,
		"js": true, "linux": true, "nacl": true, "netbsd": true,
		"openbsd": true, "plan9": true, "solaris": true, "wasip1": true,
		"windows": true, "zos": true,
	}
	knownArch := map[string]bool{
		"386": true, "amd64": true, "arm": true, "arm64": true,
		"loong64": true, "mips": true, "mips64": true, "mips64le": true,
		"mipsle": true, "ppc64": true, "ppc64le": true, "riscv64": true,
		"s390x": true, "wasm": true,
	}
	if !knownOS[parts[0]] {
		return fmt.Errorf("unknown OS %q", parts[0])
	}
	if !knownArch[parts[1]] {
		return fmt.Errorf("unknown architecture %q", parts[1])
	}
	return nil
}

// parseDuration parses a duration string like "5s", "100ms", "2m" into time.Duration.
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
