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
	"sync"

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
// usePath is the path argument (URL or file path).
// checksum is an optional sha-256 hex string for content verification.
// insecure allows http:// URLs and skips TLS verification (ignored for file paths).
func ResolveUsePath(srcFile, usePath, checksum string, insecure bool) (string, error) {
	// URL — download and cache
	if strings.HasPrefix(usePath, "https://") || (insecure && strings.HasPrefix(usePath, "http://")) {
		return fetcher.Fetch(usePath, checksum, insecure)
	}
	if strings.HasPrefix(usePath, "http://") {
		return "", fmt.Errorf("http URLs require insecure flag: %s", usePath)
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
			depPath, err := ResolveUsePath(path, useDecl.Path, useDecl.Checksum, useDecl.Insecure)
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

	return compileFiles(files, entryFile)
}

// Compile compiles source code into a bytecode program.
func Compile(name string, sourceText string) (*bytecode.Program, *diagnostic.Diagnostics, error) {
	return compileFiles(map[string]string{name: sourceText}, name)
}

// CompileFiles compiles multiple source files into a single bytecode program.
// No main-function validation is performed; callers that compile an entry
// program should use Compile or CompileWithUses.
func CompileFiles(files map[string]string) (*bytecode.Program, *diagnostic.Diagnostics, error) {
	return compileFiles(files, "")
}

// compileFiles compiles multiple source files into a single bytecode program.
// The files map is filename -> source content. Functions are mangled as
// "module.funcname" to avoid conflicts across modules. When entryFile is
// non-empty, that file is validated as the program entry point (a well-formed
// main function is required).
func compileFiles(files map[string]string, entryFile string) (*bytecode.Program, *diagnostic.Diagnostics, error) {
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
		// Also add struct methods
		for _, sd := range fr.prog.Structs {
			for _, m := range sd.Methods {
				moduleName := fr.prog.Module
				if moduleName == "" {
					moduleName = "main"
				}
				mangledName := moduleName + "." + m.Name
				allFuncs[mangledName] = m
			}
		}
	}

	// Build same-package type registries so types declared in one file of a
	// package are usable in every other file of that package.
	moduleTypeReg := map[string]*moduleTypes{}
	for _, fr := range fileResults {
		mt := moduleTypeReg[fr.prog.Module]
		if mt == nil {
			mt = &moduleTypes{}
			moduleTypeReg[fr.prog.Module] = mt
		}
		mt.enums = append(mt.enums, fr.prog.Enums...)
		mt.structs = append(mt.structs, fr.prog.Structs...)
		mt.traits = append(mt.traits, fr.prog.Traits...)
	}

	// Pre-resolve function type annotations for all files
	// This is needed so cross-module calls can resolve parameter/return types
	for _, fr := range fileResults {
		res := resolver.New(fr.src)
		res.SetAllFuncs(allFuncs)
		res.SetExternalTypeNames(externalTypeNames(fr.prog, moduleTypeReg))
		resDiags, err := res.Resolve(fr.prog)
		if err != nil || (resDiags != nil && resDiags.HasErrors()) {
			for _, d := range resDiags.All() {
				allDiags.Add(d)
			}
			continue
		}
	}

	// Now type-check all files (after all are resolved). The entry file's main
	// function is validated here; library files loaded via `use` are not
	// required to define main, and only the entry file may define it.
	for _, fr := range fileResults {
		// A library file must not define the program entry point; the entry
		// file is the sole authority, so the entry point never depends on
		// filename sort order.
		if entryFile != "" && fr.src.Name != entryFile {
			for _, fn := range fr.prog.Funcs {
				if fn.Name == "main" {
					allDiags.AddError("C093",
						fmt.Sprintf("library file %s cannot define main; only the entry file may define the program entry point", fr.src.Name),
						fn.Span())
				}
			}
		}

		chk := checker.New(fr.src)
		chk.SetAllFuncs(allFuncs)
		extEnums, extStructs, extTraits := externalTypes(fr.prog, moduleTypeReg)
		chk.SetExternalTypes(extEnums, extStructs, extTraits)
		chk.SetSkipMainCheck(entryFile == "" || fr.src.Name != entryFile)
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
		for _, td := range fr.prog.Traits {
			combinedProg.Traits = append(combinedProg.Traits, td)
		}
		for _, sd := range fr.prog.Structs {
			combinedProg.Structs = append(combinedProg.Structs, sd)
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

// globalRegistry is a lazily-initialized, shared native registry.
// Registering ~50 native functions on every Execute() call is expensive (~19KB allocs).
// The registry is immutable after initialization, so sharing is safe.
var globalRegistry = sync.OnceValue(func() *vm.NativeRegistry {
	r := vm.NewNativeRegistry()
	native.RegisterAll(r)
	return r
})

// moduleTypes collects the type declarations of one package across files.
type moduleTypes struct {
	enums   []*ast.EnumDecl
	structs []*ast.StructDecl
	traits  []*ast.TraitDecl
}

// externalTypes returns the type declarations from other files of the same
// package (i.e., the package's types minus this file's own declarations).
func externalTypes(prog *ast.Program, reg map[string]*moduleTypes) ([]*ast.EnumDecl, []*ast.StructDecl, []*ast.TraitDecl) {
	mt := reg[prog.Module]
	if mt == nil {
		return nil, nil, nil
	}
	hasEnum := func(d *ast.EnumDecl) bool {
		for _, e := range prog.Enums {
			if e == d {
				return true
			}
		}
		return false
	}
	hasStruct := func(d *ast.StructDecl) bool {
		for _, s := range prog.Structs {
			if s == d {
				return true
			}
		}
		return false
	}
	hasTrait := func(d *ast.TraitDecl) bool {
		for _, t := range prog.Traits {
			if t == d {
				return true
			}
		}
		return false
	}
	var enums []*ast.EnumDecl
	var structs []*ast.StructDecl
	var traits []*ast.TraitDecl
	for _, e := range mt.enums {
		if !hasEnum(e) {
			enums = append(enums, e)
		}
	}
	for _, s := range mt.structs {
		if !hasStruct(s) {
			structs = append(structs, s)
		}
	}
	for _, t := range mt.traits {
		if !hasTrait(t) {
			traits = append(traits, t)
		}
	}
	return enums, structs, traits
}

// externalTypeNames returns the names of types declared in other files of the
// same package, for resolver use.
func externalTypeNames(prog *ast.Program, reg map[string]*moduleTypes) []string {
	enums, structs, traits := externalTypes(prog, reg)
	names := make([]string, 0, len(enums)+len(structs)+len(traits))
	for _, e := range enums {
		names = append(names, e.Name)
	}
	for _, s := range structs {
		names = append(names, s.Name)
	}
	for _, t := range traits {
		names = append(names, t.Name)
	}
	return names
}

// Execute runs a compiled bytecode program.
func Execute(ctx context.Context, prog *bytecode.Program, opts Options) (vm.Value, error) {
	registry := globalRegistry()
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
