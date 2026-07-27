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

// Package verifier validates bytecode before execution.
package verifier

import (
	"fmt"

	"github.com/dhoard/solvik-language/internal/bytecode"
)

// Error represents a verification error.
type Error struct {
	Message string
	Offset  int
}

func (e *Error) Error() string {
	if e.Offset >= 0 {
		return fmt.Sprintf("at offset %d: %s", e.Offset, e.Message)
	}
	return e.Message
}

// Verify checks a bytecode program for validity.
// Returns nil if the program is safe to execute, or an error describing the first
// verification violation.
func Verify(prog *bytecode.Program) error {
	if prog == nil {
		return &Error{Message: "nil program", Offset: -1}
	}

	if prog.Magic != bytecode.Magic {
		return &Error{Message: fmt.Sprintf("invalid magic: %x", prog.Magic), Offset: -1}
	}

	if prog.Version > bytecode.FormatVersion {
		return &Error{Message: fmt.Sprintf("unsupported version: %d", prog.Version), Offset: -1}
	}

	// Check function count
	if len(prog.Functions) > 65535 {
		return &Error{Message: fmt.Sprintf("too many functions: %d", len(prog.Functions)), Offset: -1}
	}

	// Verify each function
	for fnIdx, fn := range prog.Functions {
		if err := verifyFunction(prog, fnIdx, fn); err != nil {
			return err
		}
	}

	return nil
}

// verifyFunction verifies a single function's bytecode.
func verifyFunction(prog *bytecode.Program, fnIdx int, fn bytecode.Function) error {
	// Check parameter count
	if fn.ParamCount > 255 {
		return &Error{Message: fmt.Sprintf("function %d: too many parameters: %d", fnIdx, fn.ParamCount), Offset: -1}
	}

	if fn.LocalCount > 65535 {
		return &Error{Message: fmt.Sprintf("function %d: too many locals: %d", fnIdx, fn.LocalCount), Offset: -1}
	}

	if fn.MaxStack > 65535 {
		return &Error{Message: fmt.Sprintf("function %d: max stack too large: %d", fnIdx, fn.MaxStack), Offset: -1}
	}

	if len(fn.Code) == 0 {
		return &Error{Message: fmt.Sprintf("function %d: empty code", fnIdx), Offset: -1}
	}

	// Verify code structure
	code := fn.Code
	offset := 0
	instructions := 0
	for offset < len(code) {
		instructions++
		if instructions > 1000000 {
			return &Error{Message: "too many instructions", Offset: offset}
		}

		_, next, err := bytecode.Decode(code, offset)
		if err != nil {
			return &Error{Message: err.Error(), Offset: offset}
		}
		offset = next
	}

	if offset != len(code) {
		return &Error{Message: "code length mismatch", Offset: offset}
	}

	// Validate instructions
	if err := verifyInstructions(prog, fnIdx, fn); err != nil {
		return err
	}

	// Validate constant pool
	if err := verifyConstants(fn); err != nil {
		return err
	}

	// Validate jump targets
	return verifyJumps(prog, fnIdx, fn)
}

// verifyInstructions checks instruction validity.
func verifyInstructions(prog *bytecode.Program, fnIdx int, fn bytecode.Function) error {
	offset := 0
	totalLocals := fn.ParamCount + fn.LocalCount
	constCount := len(fn.Constants)

	for offset < len(fn.Code) {
		inst, next, err := bytecode.Decode(fn.Code, offset)
		if err != nil {
			return err
		}

		switch inst.Opcode {
		case bytecode.OpLOAD_LOCAL, bytecode.OpSTORE_LOCAL:
			idx := int(inst.Operands[0])
			if idx < 0 || idx >= totalLocals {
				return &Error{
					Message: fmt.Sprintf("local index %d out of range (max %d)", idx, totalLocals-1),
					Offset:  offset,
				}
			}

		case bytecode.OpLOAD_GLOBAL, bytecode.OpSTORE_GLOBAL:
			idx := int(inst.Operands[0])
			if idx < 0 || idx >= prog.Globals {
				return &Error{
					Message: fmt.Sprintf("global index %d out of range", idx),
					Offset:  offset,
				}
			}

		case bytecode.OpCONST_STRING:
			idx := int(inst.Operands[0])
			if idx < 0 || idx >= constCount {
				return &Error{
					Message: fmt.Sprintf("constant index %d out of range", idx),
					Offset:  offset,
				}
			}
			if fn.Constants[idx].Kind != bytecode.ConstString {
				return &Error{
					Message: fmt.Sprintf("expected string constant at index %d", idx),
					Offset:  offset,
				}
			}

		case bytecode.OpCALL:
			fnIdx2 := int(inst.Operands[0])
			argCount := int(inst.Operands[1])
			if fnIdx2 < 0 || fnIdx2 >= len(prog.Functions) {
				return &Error{
					Message: fmt.Sprintf("function index %d out of range", fnIdx2),
					Offset:  offset,
				}
			}
			target := prog.Functions[fnIdx2]
			if target.ParamCount != argCount {
				return &Error{
					Message: fmt.Sprintf("function %s expects %d arguments but call has %d", target.Name, target.ParamCount, argCount),
					Offset:  offset,
				}
			}

		case bytecode.OpCALL_NATIVE:
			nativeIdx := int(inst.Operands[0])
			argCount := int(inst.Operands[1])
			if nativeIdx < 0 || nativeIdx >= len(prog.Natives) {
				return &Error{
					Message: fmt.Sprintf("native function index %d out of range", nativeIdx),
					Offset:  offset,
				}
			}
			nd := prog.Natives[nativeIdx]
			if nd.Params >= 0 && nd.Params != argCount {
				return &Error{
					Message: fmt.Sprintf("native %s.%s expects %d arguments but call has %d", nd.Module, nd.Name, nd.Params, argCount),
					Offset:  offset,
				}
			}

		case bytecode.OpRETURN, bytecode.OpRETURN_VOID:
			// Valid return

		case bytecode.OpJUMP, bytecode.OpJUMP_IF_FALSE, bytecode.OpJUMP_IF_TRUE:
			// Jump target validation is done separately
		}

		offset = next
	}

	return nil
}

// verifyConstants checks constant pool validity.
func verifyConstants(fn bytecode.Function) error {
	for i, c := range fn.Constants {
		switch c.Kind {
		case bytecode.ConstNone:
			return &Error{Message: fmt.Sprintf("constant %d has no kind", i), Offset: -1}
		case bytecode.ConstBool, bytecode.ConstByte, bytecode.ConstInt,
			bytecode.ConstLong, bytecode.ConstFloat, bytecode.ConstDouble,
			bytecode.ConstChar, bytecode.ConstString:
			// Valid
		default:
			return &Error{Message: fmt.Sprintf("constant %d: unknown kind %d", i, c.Kind), Offset: -1}
		}
	}
	return nil
}

// verifyJumps validates jump targets.
func verifyJumps(prog *bytecode.Program, fnIdx int, fn bytecode.Function) error {
	_ = prog
	code := fn.Code

	offset := 0
	for offset < len(code) {
		inst, next, err := bytecode.Decode(code, offset)
		if err != nil {
			return err
		}

		switch inst.Opcode {
		case bytecode.OpJUMP, bytecode.OpJUMP_IF_FALSE, bytecode.OpJUMP_IF_TRUE:
			jumpOffset := int32(inst.Operands[0])
			target := next + int(jumpOffset)
			if target < 0 || target > len(code) {
				return &Error{
					Message: fmt.Sprintf("jump target %d out of range (code size %d)", target, len(code)),
					Offset:  offset,
				}
			}
		}

		offset = next
	}

	return nil
}

// VerifyStackDepth verifies consistent stack depth across control flow.
func VerifyStackDepth(prog *bytecode.Program) error {
	for fnIdx, fn := range prog.Functions {
		if err := verifyFunctionStack(fnIdx, fn); err != nil {
			return err
		}
	}
	return nil
}

// verifyFunctionStack verifies stack depth consistency for a function.
func verifyFunctionStack(fnIdx int, fn bytecode.Function) error {
	code := fn.Code

	// Map each instruction offset to its max stack depth
	stackDepths := make(map[int]int)

	// BFS over control flow
	type state struct {
		offset int
		depth  int
	}

	queue := []state{{offset: 0, depth: 0}}
	for len(queue) > 0 {
		s := queue[0]
		queue = queue[1:]

		// Check if we've been here with a different depth
		if prevDepth, visited := stackDepths[s.offset]; visited {
			if prevDepth != s.depth {
				return &Error{
					Message: fmt.Sprintf("stack depth mismatch at offset %d: %d vs %d", s.offset, prevDepth, s.depth),
					Offset:  s.offset,
				}
			}
			continue
		}
		stackDepths[s.offset] = s.depth

		if s.offset >= len(code) {
			continue
		}

		inst, next, err := bytecode.Decode(code, s.offset)
		if err != nil {
			return err
		}

		info := bytecode.Instructions[inst.Opcode]
		newDepth := s.depth - info.PopCount + info.PushCount
		if newDepth < 0 {
			return &Error{
				Message: fmt.Sprintf("stack underflow at offset %d", s.offset),
				Offset:  s.offset,
			}
		}

		switch inst.Opcode {
		case bytecode.OpJUMP:
			target := next + int(int32(inst.Operands[0]))
			queue = append(queue, state{offset: target, depth: newDepth})
		case bytecode.OpJUMP_IF_FALSE, bytecode.OpJUMP_IF_TRUE:
			target := next + int(int32(inst.Operands[0]))
			queue = append(queue, state{offset: target, depth: newDepth})
			queue = append(queue, state{offset: next, depth: newDepth})
		case bytecode.OpRETURN, bytecode.OpRETURN_VOID:
			// End of path
		default:
			queue = append(queue, state{offset: next, depth: newDepth})
		}
	}

	return nil
}
