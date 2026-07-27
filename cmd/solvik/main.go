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
	"syscall"

	"github.com/dhoard/solvik-language/internal/runtime"
	"github.com/dhoard/solvik-language/internal/vm"
)

const version = "0.1.0"

func main() {
	// Subcommands
	runCmd := flag.NewFlagSet("run", flag.ExitOnError)
	checkCmd := flag.NewFlagSet("check", flag.ExitOnError)
	compileCmd := flag.NewFlagSet("compile", flag.ExitOnError)
	execCmd := flag.NewFlagSet("exec", flag.ExitOnError)
	disasmCmd := flag.NewFlagSet("disassemble", flag.ExitOnError)

	compileOutput := compileCmd.String("o", "output.lbc", "output file path")

	if len(os.Args) < 2 {
		printUsage()
		os.Exit(1)
	}

	// Handle version
	if os.Args[1] == "version" {
		fmt.Printf("solvik version %s\n", version)
		os.Exit(0)
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
			os.Exit(1)
		}
		runSource(ctx, runCmd.Arg(0))

	case "check":
		checkCmd.Parse(os.Args[2:])
		if checkCmd.NArg() != 1 {
			if checkCmd.NArg() == 0 {
				fmt.Fprintf(os.Stderr, "error: expected source file\n")
			} else {
				fmt.Fprintf(os.Stderr, "error: check accepts exactly one source file, got %d\n", checkCmd.NArg())
			}
			os.Exit(1)
		}
		checkSource(checkCmd.Arg(0))

	case "compile":
		compileCmd.Parse(os.Args[2:])
		if compileCmd.NArg() < 1 {
			fmt.Fprintf(os.Stderr, "error: expected source file\n")
			os.Exit(1)
		}
		compileSource(compileCmd.Arg(0), *compileOutput)

	case "exec":
		execCmd.Parse(os.Args[2:])
		if execCmd.NArg() < 1 {
			fmt.Fprintf(os.Stderr, "error: expected bytecode file\n")
			os.Exit(1)
		}
		execBytecode(ctx, execCmd.Arg(0))

	case "disassemble":
		disasmCmd.Parse(os.Args[2:])
		if disasmCmd.NArg() < 1 {
			fmt.Fprintf(os.Stderr, "error: expected bytecode file\n")
			os.Exit(1)
		}
		disassembleBytecode(disasmCmd.Arg(0))

	default:
		fmt.Fprintf(os.Stderr, "unknown command: %s\n", os.Args[1])
		printUsage()
		os.Exit(1)
	}
}

func printUsage() {
	fmt.Fprintf(os.Stderr, `solvik - Solvik toolchain

Usage:
  solvik run <file>          Compile and run a source file
  solvik check <file>        Check source for errors
  solvik compile <file>      Compile source to bytecode
  solvik exec <file>         Execute a bytecode file
  solvik disassemble <file>  Disassemble bytecode
  solvik version             Print version
`)
}

func runSource(ctx context.Context, path string) {
	sourceText, err := readFile(path)
	if err != nil {
		fmt.Fprintf(os.Stderr, "error: %v\n", err)
		os.Exit(1)
	}

	opts := runtime.DefaultOptions()
	result := runtime.CompileAndExecuteCtx(ctx, path, sourceText, opts)

	if result.Diagnostics != nil && len(result.Diagnostics.All()) > 0 {
		fmt.Fprintf(os.Stderr, "%s\n", result.Diagnostics.All())
	}

	if result.Error != nil {
		fmt.Fprintf(os.Stderr, "error: %v\n", result.Error)
		if rerr, ok := result.Error.(*vm.RuntimeError); ok {
			fmt.Fprint(os.Stderr, vm.FormatStackTrace(rerr))
		}
		os.Exit(1)
	}
}

func checkSource(path string) {
	sourceText, err := readFile(path)
	if err != nil {
		fmt.Fprintf(os.Stderr, "error: %v\n", err)
		os.Exit(1)
	}

	_, diags, err := runtime.Compile(path, sourceText)
	if diags != nil && len(diags.All()) > 0 {
		for _, d := range diags.All() {
			fmt.Fprintf(os.Stderr, "error %s: %s\n  --> %s\n", d.Code, d.Message, d.Span)
		}
	}
	if err != nil {
		os.Exit(1)
	}
	fmt.Println("OK")
}

func compileSource(sourcePath, outputPath string) {
	sourceText, err := readFile(sourcePath)
	if err != nil {
		fmt.Fprintf(os.Stderr, "error: %v\n", err)
		os.Exit(1)
	}

	prog, diags, err := runtime.Compile(sourcePath, sourceText)
	if diags != nil && len(diags.All()) > 0 {
		for _, d := range diags.All() {
			fmt.Fprintf(os.Stderr, "error %s: %s\n  --> %s\n", d.Code, d.Message, d.Span)
		}
	}
	if err != nil {
		os.Exit(1)
	}

	data, err := runtime.Serialize(prog)
	if err != nil {
		fmt.Fprintf(os.Stderr, "error: %v\n", err)
		os.Exit(1)
	}

	if err := os.WriteFile(outputPath, data, 0644); err != nil {
		fmt.Fprintf(os.Stderr, "error: %v\n", err)
		os.Exit(1)
	}
	fmt.Printf("Compiled to %s (%d bytes)\n", outputPath, len(data))
}

func execBytecode(ctx context.Context, path string) {
	data, err := readBytes(path)
	if err != nil {
		fmt.Fprintf(os.Stderr, "error: %v\n", err)
		os.Exit(1)
	}

	prog, err := runtime.Deserialize(data)
	if err != nil {
		fmt.Fprintf(os.Stderr, "error: %v\n", err)
		os.Exit(1)
	}

	opts := runtime.DefaultOptions()
	val, execErr := runtime.Execute(ctx, prog, opts)
	if execErr != nil {
		fmt.Fprintf(os.Stderr, "runtime error: %v\n", execErr)
		if rerr, ok := execErr.(*vm.RuntimeError); ok {
			fmt.Fprint(os.Stderr, vm.FormatStackTrace(rerr))
		}
		os.Exit(1)
	}

	// If main returned a non-zero int, use as exit code
	if val.Kind == vm.ValueInt && val.Int() != 0 {
		os.Exit(int(val.Int()))
	}
}

func disassembleBytecode(path string) {
	data, err := readBytes(path)
	if err != nil {
		fmt.Fprintf(os.Stderr, "error: %v\n", err)
		os.Exit(1)
	}

	prog, err := runtime.Deserialize(data)
	if err != nil {
		fmt.Fprintf(os.Stderr, "error: %v\n", err)
		os.Exit(1)
	}

	fmt.Print(runtime.Disassemble(prog))
}

func readFile(path string) (string, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return "", fmt.Errorf("cannot read %s: %v", path, err)
	}
	return string(data), nil
}

func readBytes(path string) ([]byte, error) {
	return os.ReadFile(path)
}
