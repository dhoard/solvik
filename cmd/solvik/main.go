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
	"syscall"
	"time"

	"github.com/dhoard/solvik-language/internal/diagnostic"
	"github.com/dhoard/solvik-language/internal/runtime"
	"github.com/dhoard/solvik-language/internal/source"
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

Options:
  --max-instructions N            Maximum instruction count (0 = unbounded)
  --max-call-depth N              Maximum call depth (0 = unbounded)
  --timeout D                     Execution timeout (e.g., 5s, 100ms)
  --verbose                       Show verbose output
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

	// Read source for diagnostics formatting
	data, _ := os.ReadFile(path)
	src := source.NewSourceText(path, string(data))

	bcProg, diags, err := runtime.CompileWithUses(path)
	if diags != nil && len(diags.All()) > 0 {
		for _, d := range diags.All() {
			fmt.Fprint(os.Stderr, diagnostic.FormatDiagnostic(d, src))
		}
	}
	if err != nil {
		fmt.Fprintf(os.Stderr, "error: %v\n", err)
		os.Exit(exitCompileError)
	}

	_, execErr := runtime.Execute(runCtx, bcProg, opts)
	if execErr != nil {
		fmt.Fprintf(os.Stderr, "error: %v\n", execErr)
		if rerr, ok := execErr.(*vm.RuntimeError); ok {
			fmt.Fprint(os.Stderr, vm.FormatStackTrace(rerr))
		}
		os.Exit(exitRuntimeError)
	}
}

func checkSource(path string) {
	// Read source for diagnostics formatting
	data, err := os.ReadFile(path)
	src := source.NewSourceText(path, string(data))

	prog, diags, err := runtime.CompileWithUses(path)
	if diags != nil && len(diags.All()) > 0 {
		for _, d := range diags.All() {
			fmt.Fprint(os.Stderr, diagnostic.FormatDiagnostic(d, src))
		}
	}
	if err != nil {
		os.Exit(exitCompileError)
	}
	fmt.Println("OK")
	_ = prog
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
