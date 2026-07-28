// Package api provides a stable public API for embedding solvik as a scripting engine.
//
// Thread safety:
//   - Program is safe for concurrent use (immutable after creation).
//   - Compiler may be reused but is not safe for concurrent access.
//   - VM is single-threaded and must not be accessed concurrently.
//   - NativeRegistry is safe for concurrent registration and lookup.
package api

import (
	"context"
	"fmt"

	"github.com/dhoard/solvik-language/internal/bytecode"
	"github.com/dhoard/solvik-language/internal/diagnostic"
	"github.com/dhoard/solvik-language/internal/native"
	"github.com/dhoard/solvik-language/internal/runtime"
	"github.com/dhoard/solvik-language/internal/vm"
)

// Compiler compiles solvik source code into executable programs.
type Compiler struct {
	options CompilerOptions
}

// CompilerOptions configures the compiler.
type CompilerOptions struct {
	// MaxInstructions limits the number of instructions a program may execute (0 = no limit).
	MaxInstructions int64
	// MaxCallDepth limits the call stack depth (0 = default).
	MaxCallDepth int
}

// DefaultCompilerOptions returns sensible default compiler options.
func DefaultCompilerOptions() CompilerOptions {
	return CompilerOptions{
		MaxInstructions: 10000000,
		MaxCallDepth:    1024,
	}
}

// NewCompiler creates a new compiler with the given options.
func NewCompiler(options CompilerOptions) *Compiler {
	return &Compiler{options: options}
}

// Compile compiles solvik source code into a Program.
func (c *Compiler) Compile(ctx context.Context, name, source string) (*Program, Diagnostics, error) {
	prog, diags, err := runtime.Compile(name, source)
	if err != nil {
		return nil, diagnosticsFromInternal(diags), err
	}
	return &Program{prog: prog, options: c.options}, diagnosticsFromInternal(diags), nil
}

// Program represents a compiled solvik program, safe for concurrent use.
type Program struct {
	prog    *bytecode.Program
	options CompilerOptions
}

// VM executes compiled solvik programs.
type VM struct {
	registry *vm.NativeRegistry
}

// NewVM creates a new virtual machine.
func NewVM() *VM {
	registry := vm.NewNativeRegistry()
	native.RegisterAll(registry)
	return &VM{registry: registry}
}

// Execute runs a compiled program and returns the result value.
// The VM is single-threaded and must not be accessed concurrently.
func (vm_ *VM) Execute(ctx context.Context, prog *Program) (Value, error) {
	limits := vm.DefaultLimits()
	if prog.options.MaxInstructions > 0 {
		limits.MaxInstructions = prog.options.MaxInstructions
	}
	if prog.options.MaxCallDepth > 0 {
		limits.MaxCallDepth = prog.options.MaxCallDepth
	}

	machine := vm.New(vm_.registry, limits)
	val, err := machine.Execute(ctx, prog.prog)
	if err != nil {
		return Value{}, err
	}
	return Value{val: val}, nil
}

// Value represents a runtime value.
type Value struct {
	val vm.Value
}

// IsNull returns true if the value is null.
func (v Value) IsNull() bool {
	return v.val.IsNull()
}

// Int returns the integer value.
func (v Value) Int() int64 {
	return v.val.Int()
}

// String returns the string representation.
func (v Value) String() string {
	return v.val.String()
}

// Diagnostic represents a compiler diagnostic message.
type Diagnostic struct {
	Code     string
	Message  string
	Severity string // "error", "warning", or "note"
	Line     int
	Column   int
}

// Diagnostics is a collection of diagnostics.
type Diagnostics []Diagnostic

func diagnosticsFromInternal(diags *diagnostic.Diagnostics) Diagnostics {
	if diags == nil {
		return nil
	}
	var result Diagnostics
	for _, d := range diags.All() {
		result = append(result, Diagnostic{
			Code:     d.Code,
			Message:  d.Message,
			Severity: d.Severity.String(),
			Line:     d.Span.StartL,
			Column:   d.Span.StartC,
		})
	}
	return result
}

// Ensure imports are used
var _ = fmt.Sprintf
