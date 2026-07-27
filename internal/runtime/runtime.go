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

// Package runtime orchestrates the full compilation and execution pipeline.
package runtime

import (
	"bytes"
	"context"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"

	"github.com/dhoard/solvik-language/internal/ast"
	"github.com/dhoard/solvik-language/internal/bytecode"
	"github.com/dhoard/solvik-language/internal/checker"
	"github.com/dhoard/solvik-language/internal/compiler"
	"github.com/dhoard/solvik-language/internal/diagnostic"
	"github.com/dhoard/solvik-language/internal/fetcher"
	"github.com/dhoard/solvik-language/internal/lexer"
	"github.com/dhoard/solvik-language/internal/native"
	"github.com/dhoard/solvik-language/internal/parser"
	"github.com/dhoard/solvik-language/internal/resolver"
	"github.com/dhoard/solvik-language/internal/source"
	"github.com/dhoard/solvik-language/internal/verifier"
	"github.com/dhoard/solvik-language/internal/vm"
)

// Result holds the outcome of compilation or execution.
type Result struct {
	Program     *bytecode.Program
	Diagnostics *diagnostic.Diagnostics
	Value       vm.Value
	Error       error
}

// Options configures compilation and execution.
type Options struct {
	Limits vm.Limits
}

// DefaultOptions returns sensible default options.
func DefaultOptions() Options {
	return Options{
		Limits: vm.DefaultLimits(),
	}
}

// ResolveUsePath resolves a use declaration to an absolute filesystem path.
// srcFile is the path of the file containing the use declaration.
// usePath is the string argument to use: "foo.bar", "~/modules/foo.bar", "/abs/path/foo.bar", "https://..."
// checksum is an optional sha-256 hex string for content verification (required for https).
func ResolveUsePath(srcFile, usePath, checksum string) (string, error) {
	// HTTPS URL — download and cache
	if strings.HasPrefix(usePath, "https://") {
		return fetcher.Fetch(usePath, checksum)
	}

	// Local file path
	modulePath := strings.ReplaceAll(usePath, ".", "/")

	var resolved string
	switch {
	case strings.HasPrefix(modulePath, "~/") || modulePath == "~":
		home, err := os.UserHomeDir()
		if err != nil {
			return "", fmt.Errorf("cannot expand ~: %v", err)
		}
		if modulePath == "~" {
			resolved = home
		} else {
			resolved = filepath.Join(home, modulePath[2:])
		}

	case filepath.IsAbs(modulePath):
		resolved = modulePath

	default:
		// Relative to the declaring file's directory
		resolved = filepath.Join(filepath.Dir(srcFile), modulePath)
	}

	resolved = filepath.Clean(resolved + ".sol")

	// Validate checksum for local files if provided
	if checksum != "" {
		if _, err := fetcher.VerifyFile(resolved, checksum); err != nil {
			return "", err
		}
	}

	return resolved, nil
}

// CompileWithUses compiles a source file and all its use dependencies.
func CompileWithUses(entryFile string) (*bytecode.Program, *diagnostic.Diagnostics, error) {
	seen := make(map[string]bool)
	files := make(map[string]string)
	allDiags := diagnostic.NewDiagnostics()

	var load func(path string) error
	load = func(path string) error {
		if seen[path] {
			return nil
		}
		seen[path] = true

		data, err := os.ReadFile(path)
		if err != nil {
			return fmt.Errorf("cannot read %s: %v", path, err)
		}
		files[path] = string(data)

		src := source.NewSourceText(path, files[path])
		tokens, lexDiags := lexer.New(src).Tokenize()
		if lexDiags.HasErrors() {
			for _, d := range lexDiags.All() {
				allDiags.Add(d)
			}
			return fmt.Errorf("lex error in %s", path)
		}

		prog, parseDiags := parser.New(src, tokens).Parse()
		if parseDiags.HasErrors() {
			for _, d := range parseDiags.All() {
				allDiags.Add(d)
			}
			return fmt.Errorf("parse error in %s", path)
		}

		for _, useDecl := range prog.Uses {
			depPath, err := ResolveUsePath(path, useDecl.Path, useDecl.Checksum)
			if err != nil {
				return err
			}
			if err := load(depPath); err != nil {
				return err
			}
		}
		return nil
	}

	if err := load(entryFile); err != nil {
		return nil, allDiags, err
	}

	return CompileFiles(files)
}

// Compile compiles source code into a bytecode program.
func Compile(name string, sourceText string) (*bytecode.Program, *diagnostic.Diagnostics, error) {
	return CompileFiles(map[string]string{name: sourceText})
}

// CompileFiles compiles multiple source files into a single bytecode program.
// The files map is filename -> source content. Functions are mangled as "module.funcname"
// to avoid conflicts across modules.
func CompileFiles(files map[string]string) (*bytecode.Program, *diagnostic.Diagnostics, error) {
	type fileResult struct {
		src  *source.Source
		prog *ast.Program
	}

	var fileResults []fileResult
	allDiags := diagnostic.NewDiagnostics()

	// Phase 1: Lex and parse all files
	// Sort file names for deterministic processing order
	fileNames := make([]string, 0, len(files))
	for name := range files {
		fileNames = append(fileNames, name)
	}
	sort.Strings(fileNames)
	for _, name := range fileNames {
		sourceText := files[name]
		src := source.NewSourceText(name, sourceText)

		tokens, lexDiags := lexer.New(src).Tokenize()
		if lexDiags.HasErrors() {
			for _, d := range lexDiags.All() {
				allDiags.Add(d)
			}
			continue
		}

		par := parser.New(src, tokens)
		prog, parseDiags := par.Parse()
		if parseDiags.HasErrors() {
			for _, d := range parseDiags.All() {
				allDiags.Add(d)
			}
			continue
		}

		fileResults = append(fileResults, fileResult{src: src, prog: prog})
	}

	if allDiags.HasErrors() {
		return nil, allDiags, fmt.Errorf("parsing failed")
	}

	// Phase 2: Build a combined function map across all modules
	allFuncs := make(map[string]*ast.Function) // mangled name -> function

	for _, fr := range fileResults {
		for _, fn := range fr.prog.Funcs {
			moduleName := fr.prog.Module
			if moduleName == "" {
				moduleName = "main"
			}
			mangledName := moduleName + "." + fn.Name
			allFuncs[mangledName] = fn
		}
	}

	// Pre-resolve function type annotations for all files
	// This is needed so cross-module calls can resolve parameter/return types
	for _, fr := range fileResults {
		res := resolver.New(fr.src)
		res.SetAllFuncs(allFuncs)
		resDiags, err := res.Resolve(fr.prog)
		if err != nil || (resDiags != nil && resDiags.HasErrors()) {
			for _, d := range resDiags.All() {
				allDiags.Add(d)
			}
			continue
		}
	}

	// Now type-check all files (after all are resolved)
	// Skip main function check for individual files - the VM will enforce
	// the main function requirement at execution time.
	for _, fr := range fileResults {
		chk := checker.New(fr.src)
		chk.SetAllFuncs(allFuncs)
		chk.SetSkipMainCheck(true)
		checkDiags, err := chk.Check(fr.prog)
		if err != nil || (checkDiags != nil && checkDiags.HasErrors()) {
			for _, d := range checkDiags.All() {
				allDiags.Add(d)
			}
			continue
		}
	}

	if allDiags.HasErrors() {
		return nil, allDiags, fmt.Errorf("compilation failed")
	}

	// Phase 3: Compile all functions together
	// Build a single combined AST with all functions from all files
	combinedProg := &ast.Program{}
	if len(fileResults) > 0 {
		combinedProg.Module = fileResults[0].prog.Module
		combinedProg.Imports = fileResults[0].prog.Imports
	}
	// Collect all source texts for source map purposes
	// Use the first file's source for span information
	var firstSrc *source.Source
	for _, fr := range fileResults {
		if firstSrc == nil {
			firstSrc = fr.src
		}
		for _, en := range fr.prog.Enums {
			combinedProg.Enums = append(combinedProg.Enums, en)
		}
		for _, fn := range fr.prog.Funcs {
			fn.Module = fr.prog.Module
			combinedProg.Funcs = append(combinedProg.Funcs, fn)
		}
	}

	// Compile the combined program
	if firstSrc == nil {
		return nil, allDiags, fmt.Errorf("no source files")
	}
	comp := compiler.New(firstSrc)
	bcProg, compDiags := comp.Compile(combinedProg)
	if compDiags.HasErrors() {
		for _, d := range compDiags.All() {
			allDiags.Add(d)
		}
		return nil, allDiags, fmt.Errorf("compilation failed")
	}

	// Set module name from the first file
	if len(fileResults) > 0 {
		bcProg.ModuleName = fileResults[0].prog.Module
	}

	// Verify
	if err := verifier.Verify(bcProg); err != nil {
		return nil, allDiags, fmt.Errorf("verification failed: %v", err)
	}

	return bcProg, allDiags, nil
}

// Execute runs a compiled bytecode program.
func Execute(ctx context.Context, prog *bytecode.Program, opts Options) (vm.Value, error) {
	registry := vm.NewNativeRegistry()
	native.RegisterAll(registry)

	machine := vm.New(registry, opts.Limits)
	return machine.Execute(ctx, prog)
}

// CompileAndExecute compiles and runs source code.
func CompileAndExecute(name string, sourceText string, opts Options) Result {
	bcProg, diags, err := Compile(name, sourceText)
	if err != nil || diags.HasErrors() {
		return Result{
			Diagnostics: diags,
			Error:       err,
		}
	}

	val, execErr := Execute(context.Background(), bcProg, opts)
	return Result{
		Program:     bcProg,
		Diagnostics: diags,
		Value:       val,
		Error:       execErr,
	}
}

// CompileAndExecuteCtx compiles and runs with a context.
func CompileAndExecuteCtx(ctx context.Context, name string, sourceText string, opts Options) Result {
	bcProg, diags, err := Compile(name, sourceText)
	if err != nil || diags.HasErrors() {
		return Result{
			Diagnostics: diags,
			Error:       err,
		}
	}

	val, execErr := Execute(ctx, bcProg, opts)
	return Result{
		Program:     bcProg,
		Diagnostics: diags,
		Value:       val,
		Error:       execErr,
	}
}

// Serialize serializes a program to bytes.
func Serialize(prog *bytecode.Program) ([]byte, error) {
	var buf bytes.Buffer
	if err := prog.Serialize(&buf); err != nil {
		return nil, err
	}
	return buf.Bytes(), nil
}

// Deserialize deserializes a program from bytes.
func Deserialize(data []byte) (*bytecode.Program, error) {
	return bytecode.Deserialize(bytes.NewReader(data))
}

// Verify verifies a bytecode program.
func Verify(prog *bytecode.Program) error {
	return verifier.Verify(prog)
}

// Disassemble disassembles a program's bytecode.
func Disassemble(prog *bytecode.Program) string {
	var s string
	s += fmt.Sprintf("Module: %s\n", prog.ModuleName)
	s += fmt.Sprintf("Functions: %d\n", len(prog.Functions))
	s += fmt.Sprintf("Globals: %d\n", prog.Globals)
	s += fmt.Sprintf("Natives: %d\n", len(prog.Natives))
	s += "\n"

	for i, fn := range prog.Functions {
		s += fmt.Sprintf("=== Function %d: %s ===\n", i, fn.Name)
		s += fmt.Sprintf("Parameters: %d, Locals: %d, MaxStack: %d\n", fn.ParamCount, fn.LocalCount, fn.MaxStack)
		s += "\n"
		s += bytecode.Disassemble(fn.Code, fn.Constants)
		s += "\n"
	}

	return s
}

// FormatDiagnostics formats compiler diagnostics.
func FormatDiagnostics(diags *diagnostic.Diagnostics, src *source.Source) string {
	return diagnostic.FormatAll(diags.All(), src)
}
