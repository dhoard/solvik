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

package verifier_test

import (
	"testing"

	"github.com/dhoard/solvik-language/internal/bytecode"
	"github.com/dhoard/solvik-language/internal/verifier"
)

// Helper to build a minimal valid program.
func validProg() *bytecode.Program {
	return &bytecode.Program{
		Magic:   bytecode.Magic,
		Version: bytecode.FormatVersion,
		Functions: []bytecode.Function{
			{
				Name:       "main",
				ParamCount: 0,
				LocalCount: 0,
				MaxStack:   1,
				Code:       bytecode.Encode(bytecode.OpCONST_INT, 42),
			},
			{
				Name:       "helper",
				ParamCount: 1,
				LocalCount: 0,
				MaxStack:   1,
				Code:       append(bytecode.Encode(bytecode.OpCONST_INT, 0), byte(bytecode.OpRETURN)),
			},
		},
	}
}

func TestVerifyValidProgram(t *testing.T) {
	prog := validProg()
	prog.Functions[0].Code = append(prog.Functions[0].Code, byte(bytecode.OpRETURN))
	if err := verifier.Verify(prog); err != nil {
		t.Errorf("expected no error, got: %v", err)
	}
}

func TestVerifyNilProgram(t *testing.T) {
	err := verifier.Verify(nil)
	if err == nil {
		t.Fatal("expected error for nil program")
	}
}

func TestVerifyInvalidMagic(t *testing.T) {
	prog := validProg()
	prog.Magic = 0xDEADBEEF
	err := verifier.Verify(prog)
	if err == nil {
		t.Fatal("expected error for invalid magic")
	}
}

func TestVerifyUnsupportedVersion(t *testing.T) {
	prog := validProg()
	prog.Version = bytecode.FormatVersion + 1
	err := verifier.Verify(prog)
	if err == nil {
		t.Fatal("expected error for unsupported version")
	}
}

func TestVerifyTooManyFunctions(t *testing.T) {
	prog := validProg()
	prog.Functions = make([]bytecode.Function, 65536)
	err := verifier.Verify(prog)
	if err == nil {
		t.Fatal("expected error for too many functions")
	}
}

func TestVerifyEmptyCode(t *testing.T) {
	prog := validProg()
	prog.Functions[0].Code = []byte{}
	prog.Functions[0].MaxStack = 0
	err := verifier.Verify(prog)
	if err == nil {
		t.Fatal("expected error for empty code")
	}
}

func TestVerifyTooManyParams(t *testing.T) {
	prog := validProg()
	prog.Functions[0].ParamCount = 256
	err := verifier.Verify(prog)
	if err == nil {
		t.Fatal("expected error for too many params")
	}
}

func TestVerifyTooManyLocals(t *testing.T) {
	prog := validProg()
	prog.Functions[0].LocalCount = 65536
	err := verifier.Verify(prog)
	if err == nil {
		t.Fatal("expected error for too many locals")
	}
}

func TestVerifyMaxStackTooLarge(t *testing.T) {
	prog := validProg()
	prog.Functions[0].MaxStack = 65536
	err := verifier.Verify(prog)
	if err == nil {
		t.Fatal("expected error for max stack too large")
	}
}

func TestVerifyInvalidOpcode(t *testing.T) {
	prog := validProg()
	prog.Functions[0].Code = []byte{255}
	err := verifier.Verify(prog)
	if err == nil {
		t.Fatal("expected error for invalid opcode")
	}
}

func TestVerifyTruncatedInstruction(t *testing.T) {
	prog := validProg()
	// OpCONST_INT requires 8 operand bytes after opcode
	prog.Functions[0].Code = []byte{byte(bytecode.OpCONST_INT), 0, 0}
	err := verifier.Verify(prog)
	if err == nil {
		t.Fatal("expected error for truncated instruction")
	}
}

func TestVerifyLocalIndexOutOfRange(t *testing.T) {
	prog := validProg()
	prog.Functions[0].LocalCount = 2
	prog.Functions[0].Code = append(
		bytecode.Encode(bytecode.OpLOAD_LOCAL, 5),
		byte(bytecode.OpRETURN),
	)
	err := verifier.Verify(prog)
	if err == nil {
		t.Fatal("expected error for local index out of range")
	}
}

func TestVerifyConstantIndexOutOfRange(t *testing.T) {
	prog := validProg()
	prog.Functions[0].Code = append(
		bytecode.Encode(bytecode.OpCONST_STRING, 99),
		byte(bytecode.OpRETURN),
	)
	err := verifier.Verify(prog)
	if err == nil {
		t.Fatal("expected error for constant index out of range")
	}
}

func TestVerifyConstantWrongKind(t *testing.T) {
	prog := validProg()
	prog.Functions[0].Constants = []bytecode.Constant{
		{Kind: bytecode.ConstInt, Data: 42},
	}
	prog.Functions[0].Code = append(
		bytecode.Encode(bytecode.OpCONST_STRING, 0),
		byte(bytecode.OpRETURN),
	)
	err := verifier.Verify(prog)
	if err == nil {
		t.Fatal("expected error for constant kind mismatch")
	}
}

func TestVerifyCallFunctionIndexOutOfRange(t *testing.T) {
	prog := validProg()
	prog.Functions[0].Code = append(
		bytecode.Encode(bytecode.OpCALL, 99, 0),
		byte(bytecode.OpRETURN_VOID),
	)
	err := verifier.Verify(prog)
	if err == nil {
		t.Fatal("expected error for function index out of range")
	}
}

func TestVerifyCallArgCountMismatch(t *testing.T) {
	prog := validProg()
	// helper takes 1 param, but call passes 0 args
	prog.Functions[0].Code = append(
		bytecode.Encode(bytecode.OpCALL, 1, 0), // func index 1 (helper), arg count 0
		byte(bytecode.OpRETURN_VOID),
	)
	err := verifier.Verify(prog)
	if err == nil {
		t.Fatal("expected error for arg count mismatch")
	}
}

func TestVerifyNativeCallIndexOutOfRange(t *testing.T) {
	prog := validProg()
	prog.Natives = []bytecode.NativeDecl{
		{Module: "core", Name: "print", Params: 1},
	}
	prog.Functions[0].Code = append(
		bytecode.Encode(bytecode.OpCALL_NATIVE, 99, 0),
		byte(bytecode.OpRETURN_VOID),
	)
	err := verifier.Verify(prog)
	if err == nil {
		t.Fatal("expected error for native function index out of range")
	}
}

func TestVerifyNativeArgCountMismatch(t *testing.T) {
	prog := validProg()
	prog.Natives = []bytecode.NativeDecl{
		{Module: "core", Name: "print", Params: 1},
	}
	prog.Functions[0].Code = append(
		bytecode.Encode(bytecode.OpCALL_NATIVE, 0, 2),
		byte(bytecode.OpRETURN_VOID),
	)
	err := verifier.Verify(prog)
	if err == nil {
		t.Fatal("expected error for native arg count mismatch")
	}
}

func TestVerifyJumpTargetOutOfRange(t *testing.T) {
	prog := validProg()
	prog.Functions[0].Code = bytecode.Encode(bytecode.OpJUMP, 100)
	err := verifier.Verify(prog)
	if err == nil {
		t.Fatal("expected error for jump target out of range")
	}
}

func TestVerifySetupHandlerInvalid(t *testing.T) {
	prog := validProg()
	prog.Functions[0].Code = []byte{
		byte(bytecode.OpSETUP_HANDLER),
		0, 0, 0, 0, // catch offset = 0
		0, 0, 0, 0, // finally offset = 0
		0, 5, // stack depth
		byte(bytecode.OpRETURN_VOID),
	}
	err := verifier.Verify(prog)
	if err == nil {
		t.Fatal("expected error for SETUP_HANDLER with no catch/finally")
	}
}

func TestVerifySetupHandlerCatchTargetOutOfRange(t *testing.T) {
	prog := validProg()
	prog.Functions[0].Code = []byte{
		byte(bytecode.OpSETUP_HANDLER),
		0, 0, 3, 0xE8, // catch offset = 1000
		0, 0, 0, 0, // finally offset = 0
		0, 0, // stack depth
		byte(bytecode.OpRETURN_VOID),
	}
	err := verifier.Verify(prog)
	if err == nil {
		t.Fatal("expected error for catch target out of range")
	}
}

func TestVerifyConstantNoneKind(t *testing.T) {
	prog := validProg()
	prog.Functions[0].Constants = []bytecode.Constant{
		{Kind: bytecode.ConstNone},
	}
	prog.Functions[0].Code = append(
		bytecode.Encode(bytecode.OpCONST_INT, 0),
		byte(bytecode.OpRETURN),
	)
	err := verifier.Verify(prog)
	if err == nil {
		t.Fatal("expected error for ConstNone kind")
	}
}

func TestVerifyConstantUnknownKind(t *testing.T) {
	prog := validProg()
	prog.Functions[0].Constants = []bytecode.Constant{
		{Kind: bytecode.ConstantKind(255), Data: 0},
	}
	prog.Functions[0].Code = append(
		bytecode.Encode(bytecode.OpCONST_INT, 0),
		byte(bytecode.OpRETURN),
	)
	err := verifier.Verify(prog)
	if err == nil {
		t.Fatal("expected error for unknown constant kind")
	}
}

func TestVerifyStackDepth(t *testing.T) {
	prog := validProg()
	prog.Functions[0].Code = append(
		bytecode.Encode(bytecode.OpCONST_INT, 42),
		byte(bytecode.OpRETURN),
	)
	err := verifier.VerifyStackDepth(prog)
	if err != nil {
		t.Errorf("expected no stack depth error, got: %v", err)
	}
}

func TestVerifyStackDepthUnderflow(t *testing.T) {
	prog := validProg()
	prog.Functions[0].Code = []byte{byte(bytecode.OpRETURN)}
	err := verifier.VerifyStackDepth(prog)
	if err == nil {
		t.Fatal("expected stack underflow error")
	}
}

func TestVerifyStackDepthMismatch(t *testing.T) {
	prog := validProg()
	prog.Functions[0].Code = append(
		bytecode.Encode(bytecode.OpCONST_INT, 42),
		byte(bytecode.OpRETURN),
	)
	err := verifier.VerifyStackDepth(prog)
	if err != nil {
		t.Errorf("unexpected error: %v", err)
	}
}

func TestVerifyNoDuplicateMain(t *testing.T) {
	prog := validProg()
	prog.Functions = append(prog.Functions, bytecode.Function{
		Name:       "helper",
		ParamCount: 1,
		LocalCount: 0,
		MaxStack:   1,
		Code:       append(bytecode.Encode(bytecode.OpCONST_INT, 0), byte(bytecode.OpRETURN)),
	})
	if err := verifier.Verify(prog); err != nil {
		t.Errorf("expected no error for multiple functions, got: %v", err)
	}
}

func TestVerifyJumpIfFalse(t *testing.T) {
	prog := validProg()
	// CONST_BOOL true (2 bytes)
	// JUMP_IF_FALSE skip CONST_INT (JUMP_IF_FALSE is 5 bytes, target after CONST_INT's next)
	// CONST_INT 1 (9 bytes)
	// RETURN (1 byte) = total 17 bytes
	// JUMP_IF_FALSE at offset 2, next=7, target past CONST_INT = 7 + 8 = 15 (to RETURN)
	prog.Functions[0].Code = []byte{
		byte(bytecode.OpCONST_BOOL), 1,
		0, 0, 0, 0, 0, // JUMP_IF_FALSE placeholder for Encode
	}
	// Use Encode properly
	prog.Functions[0].Code = nil
	prog.Functions[0].Code = append(prog.Functions[0].Code, bytecode.Encode(bytecode.OpCONST_BOOL, 1)...)
	prog.Functions[0].Code = append(prog.Functions[0].Code, bytecode.Encode(bytecode.OpJUMP_IF_FALSE, 10)...)
	prog.Functions[0].Code = append(prog.Functions[0].Code, bytecode.Encode(bytecode.OpCONST_INT, 1)...)
	prog.Functions[0].Code = append(prog.Functions[0].Code, byte(bytecode.OpRETURN))
	if err := verifier.Verify(prog); err != nil {
		t.Errorf("expected no error for JUMP_IF_FALSE, got: %v", err)
	}
}

func TestVerifyNativeFunction(t *testing.T) {
	prog := validProg()
	prog.Natives = []bytecode.NativeDecl{
		{Module: "core", Name: "println", Params: 1, Return: false},
	}
	prog.Functions[0].Code = append(
		bytecode.Encode(bytecode.OpCONST_STRING, 0),
		bytecode.Encode(bytecode.OpCALL_NATIVE, 0, 1)...,
	)
	prog.Functions[0].Code = append(prog.Functions[0].Code, byte(bytecode.OpRETURN_VOID))
	prog.Functions[0].Constants = []bytecode.Constant{
		{Kind: bytecode.ConstString, Str: "hello"},
	}
	if err := verifier.Verify(prog); err != nil {
		t.Errorf("expected no error for valid native call, got: %v", err)
	}
}
