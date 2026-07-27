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
	// Subcommands
	runCmd := flag.NewFlagSet("run", flag.ExitOnError)
	runMaxInsts := runCmd.Int64("max-instructions", 10000000, "maximum instruction count")
	runMaxDepth := runCmd.Int("max-call-depth", 1024, "maximum call depth")
	runTimeout := runCmd.String("timeout", "", "execution timeout (e.g., 5s, 100ms)")
	runVerbose := runCmd.Bool("verbose", false, "show verbose output")

	checkCmd := flag.NewFlagSet("check", flag.ExitOnError)

	if len(os.Args) < 2 {
		printUsage()
		os.Exit(exitInternalError)
	}

	// Handle version
	if os.Args[1] == "version" || os.Args[1] == "--version" {
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

	switch os.Args[1] {
	case "run":
		runCmd.Parse(os.Args[2:])
		if runCmd.NArg() != 1 {
			if runCmd.NArg() == 0 {
				fmt.Fprintf(os.Stderr, "error: expected source file\n")
			} else {
				fmt.Fprintf(os.Stderr, "error: run accepts exactly one source file, got %d\n", runCmd.NArg())
			}
			os.Exit(exitInternalError)
		}
		runSource(ctx, runCmd.Arg(0), *runMaxInsts, *runMaxDepth, *runTimeout, *runVerbose)

	case "check":
		checkCmd.Parse(os.Args[2:])
		if checkCmd.NArg() != 1 {
			if checkCmd.NArg() == 0 {
				fmt.Fprintf(os.Stderr, "error: expected source file\n")
			} else {
				fmt.Fprintf(os.Stderr, "error: check accepts exactly one source file, got %d\n", checkCmd.NArg())
			}
			os.Exit(exitInternalError)
		}
		checkSource(checkCmd.Arg(0))

	default:
		fmt.Fprintf(os.Stderr, "unknown command: %s\n", os.Args[1])
		printUsage()
		os.Exit(exitInternalError)
	}
}

func printUsage() {
	fmt.Fprintf(os.Stderr, `solvik - Solvik toolchain

Usage:
  solvik run <file>              Compile and run a source file
  solvik run --max-instructions N <file>
  solvik run --max-call-depth N <file>
  solvik check <file>            Check source for errors
  solvik version                 Print version
`)
}

func runSource(ctx context.Context, path string, maxInsts int64, maxDepth int, timeout string, verbose bool) {
	sourceText, err := readFile(path)
	if err != nil {
		fmt.Fprintf(os.Stderr, "error: %v\n", err)
		os.Exit(exitInternalError)
	}

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

	src := source.NewSourceText(path, sourceText)
	opts := runtime.DefaultOptions()
	opts.Limits.MaxInstructions = maxInsts
	opts.Limits.MaxCallDepth = maxDepth

	if verbose {
		fmt.Fprintf(os.Stderr, "Compiling %s...\n", path)
	}

	result := runtime.CompileAndExecuteCtx(runCtx, path, sourceText, opts)

	if result.Diagnostics != nil && len(result.Diagnostics.All()) > 0 {
		for _, d := range result.Diagnostics.All() {
			fmt.Fprint(os.Stderr, diagnostic.FormatDiagnostic(d, src))
		}
	}

	if result.Error != nil {
		fmt.Fprintf(os.Stderr, "error: %v\n", result.Error)
		if rerr, ok := result.Error.(*vm.RuntimeError); ok {
			fmt.Fprint(os.Stderr, vm.FormatStackTrace(rerr))
		}
		os.Exit(exitRuntimeError)
	}

	// If compilation had errors but we still ran, exit with compile error
	if result.Diagnostics != nil && result.Diagnostics.HasErrors() {
		os.Exit(exitCompileError)
	}
}

func checkSource(path string) {
	sourceText, err := readFile(path)
	if err != nil {
		fmt.Fprintf(os.Stderr, "error: %v\n", err)
		os.Exit(exitInternalError)
	}

	src := source.NewSourceText(path, sourceText)
	_, diags, err := runtime.Compile(path, sourceText)
	if diags != nil && len(diags.All()) > 0 {
		for _, d := range diags.All() {
			fmt.Fprint(os.Stderr, diagnostic.FormatDiagnostic(d, src))
		}
	}
	if err != nil {
		os.Exit(exitCompileError)
	}
	fmt.Println("OK")
}

func readFile(path string) (string, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return "", fmt.Errorf("cannot read %s: %v", path, err)
	}
	return string(data), nil
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

// Ensure strings import is used
var _ = strings.TrimSpace
