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

// Package bytecode defines the bytecode instruction set, serialization format,
// and related utilities for the virtual machine.
package bytecode

import (
	"encoding/binary"
	"fmt"
	"io"
	"math"
)

// Magic number for bytecode files.
const Magic = 0x4C414E47 // "LANG" in ASCII

// Current bytecode format version.
const FormatVersion = 2

// Opcode represents a single bytecode instruction.
type Opcode byte

// Bytecode instruction opcodes.
const (
	OpNOP          Opcode = iota // No operation
	OpCONST_BOOL                 // Push boolean constant
	OpCONST_BYTE                 // Push byte constant
	OpCONST_INT                  // Push int constant (64-bit)
	OpCONST_FLOAT                // Push float constant (64-bit)
	OpCONST_CHAR                 // Push char constant
	OpCONST_STRING               // Push string constant
	OpCONST_NULL                 // Push null

	OpLOAD_LOCAL   // Load local variable by index
	OpSTORE_LOCAL  // Store to local variable by index
	OpLOAD_GLOBAL  // Load global variable by index
	OpSTORE_GLOBAL // Store to global variable by index

	OpPOP // Pop and discard top of stack
	OpDUP // Duplicate top of stack

	// Integer arithmetic (64-bit)
	OpADD_INT
	OpSUB_INT
	OpMUL_INT
	OpDIV_INT
	OpREM_INT
	OpNEG_INT

	// Float arithmetic (64-bit)
	OpADD_FLOAT
	OpSUB_FLOAT
	OpMUL_FLOAT
	OpDIV_FLOAT
	OpNEG_FLOAT

	// String
	OpCONCAT_STRING

	// Comparison
	OpEQ_BOOL
	OpEQ_INT
	OpEQ_FLOAT
	OpEQ_CHAR
	OpEQ_STRING
	OpEQ_REF
	OpLT_INT
	OpLE_INT
	OpGT_INT
	OpGE_INT
	OpLT_FLOAT
	OpLE_FLOAT
	OpGT_FLOAT
	OpGE_FLOAT

	// Boolean
	OpNOT_BOOL

	// Bitwise (integer, 64-bit)
	OpBIT_AND_INT
	OpBIT_OR_INT
	OpBIT_XOR_INT
	OpBIT_NOT_INT
	OpSHIFT_LEFT_INT
	OpSHIFT_RIGHT_INT

	// Control flow
	OpJUMP
	OpJUMP_IF_FALSE
	OpJUMP_IF_TRUE
	OpCALL
	OpCALL_NATIVE
	OpRETURN
	OpRETURN_VOID
	OpRETURN_MULTI

	// Collections
	OpNEW_LIST
	OpLIST_GET
	OpLIST_SET
	OpLIST_APPEND
	OpLIST_LENGTH
	OpNEW_MAP
	OpMAP_GET
	OpMAP_SET
	OpMAP_CONTAINS
	OpMAP_LENGTH

	// Map iteration
	OpMAP_KEYS

	// Nullable
	OpCOALESCE
	OpCHECK_NOT_NULL

	// Conversions
	OpCONVERT_BYTE_TO_INT
	OpCONVERT_INT_TO_FLOAT

	// Exception handling
	OpTHROW           // Pop exception value and throw
	OpSETUP_HANDLER   // Setup a handler for a protected region (operands: catchOffset, finallyOffset, stackDepth)
	OpREMOVE_HANDLER  // Remove the current handler
	OpNEW_EXCEPTION   // Pop string, capture trace, push exception value
	OpEXCEPTION_FIELD // Pop exception, push field (operand 0=message, 1=trace)
	OpHALT

	// Sentinel for instruction count
	OpMAX
)

// InstructionInfo describes a single instruction.
type InstructionInfo struct {
	Opcode    Opcode
	Name      string
	Operands  []OperandType
	PopCount  int // number of values popped from stack
	PushCount int // number of values pushed to stack
}

// OperandType describes the type of an instruction operand.
type OperandType int

const (
	OperandNone      OperandType = iota
	OperandUint8                 // 1-byte unsigned integer
	OperandUint16                // 2-byte unsigned integer
	OperandUint32                // 4-byte unsigned integer
	OperandInt32                 // 4-byte signed integer
	OperandInt64                 // 8-byte signed integer
	OperandFloat32               // 4-byte IEEE 754 float
	OperandFloat64               // 8-byte IEEE 754 float
	OperandString                // string constant (index into constant pool)
	OperandFuncIndex             // function index
)

// Instructions table indexed by opcode.
var Instructions = func() [OpMAX]InstructionInfo {
	var t [OpMAX]InstructionInfo
	t[OpNOP] = InstructionInfo{OpNOP, "NOP", nil, 0, 0}

	t[OpCONST_BOOL] = InstructionInfo{OpCONST_BOOL, "CONST_BOOL", []OperandType{OperandUint8}, 0, 1}
	t[OpCONST_BYTE] = InstructionInfo{OpCONST_BYTE, "CONST_BYTE", []OperandType{OperandUint8}, 0, 1}
	t[OpCONST_INT] = InstructionInfo{OpCONST_INT, "CONST_INT", []OperandType{OperandInt64}, 0, 1}
	t[OpCONST_FLOAT] = InstructionInfo{OpCONST_FLOAT, "CONST_FLOAT", []OperandType{OperandFloat64}, 0, 1}
	t[OpCONST_CHAR] = InstructionInfo{OpCONST_CHAR, "CONST_CHAR", []OperandType{OperandUint32}, 0, 1}
	t[OpCONST_STRING] = InstructionInfo{OpCONST_STRING, "CONST_STRING", []OperandType{OperandString}, 0, 1}
	t[OpCONST_NULL] = InstructionInfo{OpCONST_NULL, "CONST_NULL", nil, 0, 1}

	t[OpLOAD_LOCAL] = InstructionInfo{OpLOAD_LOCAL, "LOAD_LOCAL", []OperandType{OperandUint16}, 0, 1}
	t[OpSTORE_LOCAL] = InstructionInfo{OpSTORE_LOCAL, "STORE_LOCAL", []OperandType{OperandUint16}, 1, 0}
	t[OpLOAD_GLOBAL] = InstructionInfo{OpLOAD_GLOBAL, "LOAD_GLOBAL", []OperandType{OperandUint16}, 0, 1}
	t[OpSTORE_GLOBAL] = InstructionInfo{OpSTORE_GLOBAL, "STORE_GLOBAL", []OperandType{OperandUint16}, 1, 0}

	t[OpPOP] = InstructionInfo{OpPOP, "POP", nil, 1, 0}
	t[OpDUP] = InstructionInfo{OpDUP, "DUP", nil, 1, 2}

	t[OpADD_INT] = InstructionInfo{OpADD_INT, "ADD_INT", nil, 2, 1}
	t[OpSUB_INT] = InstructionInfo{OpSUB_INT, "SUB_INT", nil, 2, 1}
	t[OpMUL_INT] = InstructionInfo{OpMUL_INT, "MUL_INT", nil, 2, 1}
	t[OpDIV_INT] = InstructionInfo{OpDIV_INT, "DIV_INT", nil, 2, 1}
	t[OpREM_INT] = InstructionInfo{OpREM_INT, "REM_INT", nil, 2, 1}
	t[OpNEG_INT] = InstructionInfo{OpNEG_INT, "NEG_INT", nil, 1, 1}

	t[OpADD_FLOAT] = InstructionInfo{OpADD_FLOAT, "ADD_FLOAT", nil, 2, 1}
	t[OpSUB_FLOAT] = InstructionInfo{OpSUB_FLOAT, "SUB_FLOAT", nil, 2, 1}
	t[OpMUL_FLOAT] = InstructionInfo{OpMUL_FLOAT, "MUL_FLOAT", nil, 2, 1}
	t[OpDIV_FLOAT] = InstructionInfo{OpDIV_FLOAT, "DIV_FLOAT", nil, 2, 1}
	t[OpNEG_FLOAT] = InstructionInfo{OpNEG_FLOAT, "NEG_FLOAT", nil, 1, 1}

	t[OpCONCAT_STRING] = InstructionInfo{OpCONCAT_STRING, "CONCAT_STRING", nil, 2, 1}

	t[OpEQ_BOOL] = InstructionInfo{OpEQ_BOOL, "EQ_BOOL", nil, 2, 1}
	t[OpEQ_INT] = InstructionInfo{OpEQ_INT, "EQ_INT", nil, 2, 1}
	t[OpEQ_FLOAT] = InstructionInfo{OpEQ_FLOAT, "EQ_FLOAT", nil, 2, 1}
	t[OpEQ_CHAR] = InstructionInfo{OpEQ_CHAR, "EQ_CHAR", nil, 2, 1}
	t[OpEQ_STRING] = InstructionInfo{OpEQ_STRING, "EQ_STRING", nil, 2, 1}
	t[OpEQ_REF] = InstructionInfo{OpEQ_REF, "EQ_REF", nil, 2, 1}

	t[OpLT_INT] = InstructionInfo{OpLT_INT, "LT_INT", nil, 2, 1}
	t[OpLE_INT] = InstructionInfo{OpLE_INT, "LE_INT", nil, 2, 1}
	t[OpGT_INT] = InstructionInfo{OpGT_INT, "GT_INT", nil, 2, 1}
	t[OpGE_INT] = InstructionInfo{OpGE_INT, "GE_INT", nil, 2, 1}

	t[OpLT_FLOAT] = InstructionInfo{OpLT_FLOAT, "LT_FLOAT", nil, 2, 1}
	t[OpLE_FLOAT] = InstructionInfo{OpLE_FLOAT, "LE_FLOAT", nil, 2, 1}
	t[OpGT_FLOAT] = InstructionInfo{OpGT_FLOAT, "GT_FLOAT", nil, 2, 1}
	t[OpGE_FLOAT] = InstructionInfo{OpGE_FLOAT, "GE_FLOAT", nil, 2, 1}

	t[OpNOT_BOOL] = InstructionInfo{OpNOT_BOOL, "NOT_BOOL", nil, 1, 1}

	t[OpBIT_AND_INT] = InstructionInfo{OpBIT_AND_INT, "BIT_AND_INT", nil, 2, 1}
	t[OpBIT_OR_INT] = InstructionInfo{OpBIT_OR_INT, "BIT_OR_INT", nil, 2, 1}
	t[OpBIT_XOR_INT] = InstructionInfo{OpBIT_XOR_INT, "BIT_XOR_INT", nil, 2, 1}
	t[OpBIT_NOT_INT] = InstructionInfo{OpBIT_NOT_INT, "BIT_NOT_INT", nil, 1, 1}
	t[OpSHIFT_LEFT_INT] = InstructionInfo{OpSHIFT_LEFT_INT, "SHIFT_LEFT_INT", nil, 2, 1}
	t[OpSHIFT_RIGHT_INT] = InstructionInfo{OpSHIFT_RIGHT_INT, "SHIFT_RIGHT_INT", nil, 2, 1}

	t[OpJUMP] = InstructionInfo{OpJUMP, "JUMP", []OperandType{OperandInt32}, 0, 0}
	t[OpJUMP_IF_FALSE] = InstructionInfo{OpJUMP_IF_FALSE, "JUMP_IF_FALSE", []OperandType{OperandInt32}, 1, 0}
	t[OpJUMP_IF_TRUE] = InstructionInfo{OpJUMP_IF_TRUE, "JUMP_IF_TRUE", []OperandType{OperandInt32}, 1, 0}

	t[OpCALL] = InstructionInfo{OpCALL, "CALL", []OperandType{OperandFuncIndex, OperandUint8}, 0, 0} // pops args+1, pushes return
	t[OpCALL_NATIVE] = InstructionInfo{OpCALL_NATIVE, "CALL_NATIVE", []OperandType{OperandUint16, OperandUint8}, 0, 0}

	t[OpRETURN] = InstructionInfo{OpRETURN, "RETURN", []OperandType{}, 1, 0}
	t[OpRETURN_VOID] = InstructionInfo{OpRETURN_VOID, "RETURN_VOID", nil, 0, 0}
	t[OpRETURN_MULTI] = InstructionInfo{OpRETURN_MULTI, "RETURN_MULTI", []OperandType{OperandUint8}, 0, 0} // dynamic: pops N, pushes N

	t[OpNEW_LIST] = InstructionInfo{OpNEW_LIST, "NEW_LIST", []OperandType{OperandUint8}, 0, 1} // arg count
	t[OpLIST_GET] = InstructionInfo{OpLIST_GET, "LIST_GET", nil, 2, 1}
	t[OpLIST_SET] = InstructionInfo{OpLIST_SET, "LIST_SET", nil, 3, 0}
	t[OpLIST_APPEND] = InstructionInfo{OpLIST_APPEND, "LIST_APPEND", nil, 2, 1}
	t[OpLIST_LENGTH] = InstructionInfo{OpLIST_LENGTH, "LIST_LENGTH", nil, 1, 1}

	t[OpNEW_MAP] = InstructionInfo{OpNEW_MAP, "NEW_MAP", nil, 0, 1}
	t[OpMAP_GET] = InstructionInfo{OpMAP_GET, "MAP_GET", nil, 2, 1}
	t[OpMAP_SET] = InstructionInfo{OpMAP_SET, "MAP_SET", nil, 3, 1} // pops 3, pushes modified map back
	t[OpMAP_CONTAINS] = InstructionInfo{OpMAP_CONTAINS, "MAP_CONTAINS", nil, 2, 1}
	t[OpMAP_LENGTH] = InstructionInfo{OpMAP_LENGTH, "MAP_LENGTH", nil, 1, 1}
	t[OpMAP_KEYS] = InstructionInfo{OpMAP_KEYS, "MAP_KEYS", nil, 1, 1}

	t[OpCOALESCE] = InstructionInfo{OpCOALESCE, "COALESCE", nil, 2, 1}
	t[OpCHECK_NOT_NULL] = InstructionInfo{OpCHECK_NOT_NULL, "CHECK_NOT_NULL", nil, 1, 1}

	t[OpCONVERT_BYTE_TO_INT] = InstructionInfo{OpCONVERT_BYTE_TO_INT, "CONVERT_BYTE_TO_INT", nil, 1, 1}
	t[OpCONVERT_INT_TO_FLOAT] = InstructionInfo{OpCONVERT_INT_TO_FLOAT, "CONVERT_INT_TO_FLOAT", nil, 1, 1}

	t[OpTHROW] = InstructionInfo{OpTHROW, "THROW", nil, 1, 0}
	t[OpSETUP_HANDLER] = InstructionInfo{OpSETUP_HANDLER, "SETUP_HANDLER", []OperandType{OperandInt32, OperandInt32, OperandUint16}, 0, 0}
	t[OpREMOVE_HANDLER] = InstructionInfo{OpREMOVE_HANDLER, "REMOVE_HANDLER", nil, 0, 0}
	t[OpNEW_EXCEPTION] = InstructionInfo{OpNEW_EXCEPTION, "NEW_EXCEPTION", nil, 1, 1}
	t[OpEXCEPTION_FIELD] = InstructionInfo{OpEXCEPTION_FIELD, "EXCEPTION_FIELD", []OperandType{OperandUint8}, 1, 1}

	t[OpHALT] = InstructionInfo{OpHALT, "HALT", nil, 0, 0}

	return t
}()

// Instruction represents a decoded instruction with operands.
type Instruction struct {
	Opcode   Opcode
	Offset   int      // byte offset in the code stream
	Operands []uint64 // raw operand values
	Span     SourceSpan
}

// String returns a disassembly string for the instruction.
func (inst Instruction) String() string {
	info := Instructions[inst.Opcode]
	s := info.Name
	for i, op := range info.Operands {
		if i > 0 || len(info.Operands) > 0 {
			s += " "
		}
		switch op {
		case OperandUint8, OperandUint16, OperandUint32, OperandFuncIndex:
			s += fmt.Sprintf("%d", inst.Operands[i])
		case OperandInt32:
			s += fmt.Sprintf("%d", int32(inst.Operands[i]))
		case OperandInt64:
			s += fmt.Sprintf("%d", int64(inst.Operands[i]))
		case OperandFloat32, OperandFloat64:
			s += fmt.Sprintf("%g", float64FromBits(inst.Operands[i]))
		case OperandString:
			s += fmt.Sprintf("%q", inst.Operands[i]) // shows index in constant pool
		}
	}
	return s
}

func float64FromBits(v uint64) float64 {
	return math.Float64frombits(v)
}

// SourceSpan maps a bytecode offset to a source location.
type SourceSpan struct {
	StartLine int
	StartCol  int
	EndLine   int
	EndCol    int
}

// ConstantKind classifies constant pool entries.
type ConstantKind uint8

const (
	ConstNone ConstantKind = iota
	ConstBool
	ConstByte
	ConstInt
	ConstFloat
	ConstChar
	ConstString
)

// Constant represents a constant pool entry.
type Constant struct {
	Kind ConstantKind
	Data uint64 // for integer and float types
	Str  string // for strings
}

// Function represents a function in the bytecode.
type Function struct {
	Name        string
	ParamCount  int
	LocalCount  int
	MaxStack    int
	ReturnCount int // 0=void, 1=single, N=multi
	Code        []byte
	Constants   []Constant
	SourceMap   []SourceSpan // index by instruction offset
}

// Program represents a complete compiled bytecode program.
type Program struct {
	Magic      uint32
	Version    uint32
	ModuleName string
	Functions  []Function
	Globals    int
	Natives    []NativeDecl
}

// NativeDecl declares a native/host function used by the program.
type NativeDecl struct {
	Module string
	Name   string
	Params int
	Return bool // true if returns non-void
}

// Serialize writes the program to a writer in binary format.
func (p *Program) Serialize(w io.Writer) error {
	// Magic
	if err := binary.Write(w, binary.BigEndian, uint32(Magic)); err != nil {
		return err
	}
	// Version
	if err := binary.Write(w, binary.BigEndian, uint32(FormatVersion)); err != nil {
		return err
	}

	// Module name
	if err := writeString(w, p.ModuleName); err != nil {
		return err
	}

	// Global count
	if err := binary.Write(w, binary.BigEndian, uint32(p.Globals)); err != nil {
		return err
	}

	// Native function count
	if err := binary.Write(w, binary.BigEndian, uint32(len(p.Natives))); err != nil {
		return err
	}
	for _, n := range p.Natives {
		if err := writeString(w, n.Module); err != nil {
			return err
		}
		if err := writeString(w, n.Name); err != nil {
			return err
		}
		if err := binary.Write(w, binary.BigEndian, uint16(n.Params)); err != nil {
			return err
		}
		var ret byte
		if n.Return {
			ret = 1
		}
		if err := binary.Write(w, binary.BigEndian, ret); err != nil {
			return err
		}
	}

	// Function count
	if err := binary.Write(w, binary.BigEndian, uint32(len(p.Functions))); err != nil {
		return err
	}
	for _, fn := range p.Functions {
		if err := writeString(w, fn.Name); err != nil {
			return err
		}
		if err := binary.Write(w, binary.BigEndian, uint16(fn.ParamCount)); err != nil {
			return err
		}
		if err := binary.Write(w, binary.BigEndian, uint16(fn.LocalCount)); err != nil {
			return err
		}
		if err := binary.Write(w, binary.BigEndian, uint16(fn.MaxStack)); err != nil {
			return err
		}
		if err := binary.Write(w, binary.BigEndian, uint16(fn.ReturnCount)); err != nil {
			return err
		}

		// Code
		if err := binary.Write(w, binary.BigEndian, uint32(len(fn.Code))); err != nil {
			return err
		}
		if _, err := w.Write(fn.Code); err != nil {
			return err
		}

		// Constants
		if err := binary.Write(w, binary.BigEndian, uint32(len(fn.Constants))); err != nil {
			return err
		}
		for _, c := range fn.Constants {
			if err := binary.Write(w, binary.BigEndian, byte(c.Kind)); err != nil {
				return err
			}
			switch c.Kind {
			case ConstBool:
				if err := binary.Write(w, binary.BigEndian, byte(c.Data)); err != nil {
					return err
				}
			case ConstByte:
				if err := binary.Write(w, binary.BigEndian, byte(c.Data)); err != nil {
					return err
				}
			case ConstInt:
				if err := binary.Write(w, binary.BigEndian, int64(c.Data)); err != nil {
					return err
				}
			case ConstFloat:
				if err := binary.Write(w, binary.BigEndian, c.Data); err != nil {
					return err
				}
			case ConstChar:
				if err := binary.Write(w, binary.BigEndian, uint32(c.Data)); err != nil {
					return err
				}
			case ConstString:
				if err := binary.Write(w, binary.BigEndian, uint32(len(c.Str))); err != nil {
					return err
				}
				if _, err := w.Write([]byte(c.Str)); err != nil {
					return err
				}
			}
		}

		// Source map
		if err := binary.Write(w, binary.BigEndian, uint32(len(fn.SourceMap))); err != nil {
			return err
		}
		for _, sm := range fn.SourceMap {
			if err := binary.Write(w, binary.BigEndian, uint16(sm.StartLine)); err != nil {
				return err
			}
			if err := binary.Write(w, binary.BigEndian, uint16(sm.StartCol)); err != nil {
				return err
			}
			if err := binary.Write(w, binary.BigEndian, uint16(sm.EndLine)); err != nil {
				return err
			}
			if err := binary.Write(w, binary.BigEndian, uint16(sm.EndCol)); err != nil {
				return err
			}
		}
	}

	return nil
}

// Deserialize reads a program from a reader.
func Deserialize(r io.Reader) (*Program, error) {
	p := &Program{}

	// Magic
	if err := binary.Read(r, binary.BigEndian, &p.Magic); err != nil {
		return nil, fmt.Errorf("cannot read magic: %w", err)
	}
	if p.Magic != uint32(Magic) {
		return nil, fmt.Errorf("invalid magic: %x", p.Magic)
	}

	// Version
	if err := binary.Read(r, binary.BigEndian, &p.Version); err != nil {
		return nil, fmt.Errorf("cannot read version: %w", err)
	}
	if p.Version > FormatVersion {
		return nil, fmt.Errorf("unsupported version: %d", p.Version)
	}

	// Module name
	var err error
	p.ModuleName, err = readString(r)
	if err != nil {
		return nil, err
	}

	// Globals
	var globalCount uint32
	if err := binary.Read(r, binary.BigEndian, &globalCount); err != nil {
		return nil, err
	}
	p.Globals = int(globalCount)

	// Natives
	var nativeCount uint32
	if err := binary.Read(r, binary.BigEndian, &nativeCount); err != nil {
		return nil, err
	}
	p.Natives = make([]NativeDecl, nativeCount)
	for i := range p.Natives {
		p.Natives[i].Module, err = readString(r)
		if err != nil {
			return nil, err
		}
		p.Natives[i].Name, err = readString(r)
		if err != nil {
			return nil, err
		}
		var params uint16
		if err := binary.Read(r, binary.BigEndian, &params); err != nil {
			return nil, err
		}
		p.Natives[i].Params = int(params)
		var ret byte
		if err := binary.Read(r, binary.BigEndian, &ret); err != nil {
			return nil, err
		}
		p.Natives[i].Return = ret != 0
	}

	// Functions
	var fnCount uint32
	if err := binary.Read(r, binary.BigEndian, &fnCount); err != nil {
		return nil, err
	}
	p.Functions = make([]Function, fnCount)
	for i := range p.Functions {
		fn := &p.Functions[i]

		fn.Name, err = readString(r)
		if err != nil {
			return nil, err
		}

		var pc, lc, ms uint16
		if err := binary.Read(r, binary.BigEndian, &pc); err != nil {
			return nil, err
		}
		fn.ParamCount = int(pc)
		if err := binary.Read(r, binary.BigEndian, &lc); err != nil {
			return nil, err
		}
		fn.LocalCount = int(lc)
		if err := binary.Read(r, binary.BigEndian, &ms); err != nil {
			return nil, err
		}
		fn.MaxStack = int(ms)

		var rc uint16
		if err := binary.Read(r, binary.BigEndian, &rc); err != nil {
			return nil, err
		}
		fn.ReturnCount = int(rc)

		// Code
		var codeLen uint32
		if err := binary.Read(r, binary.BigEndian, &codeLen); err != nil {
			return nil, err
		}
		if codeLen > 1<<24 {
			return nil, fmt.Errorf("code too large: %d", codeLen)
		}
		fn.Code = make([]byte, codeLen)
		if _, err := io.ReadFull(r, fn.Code); err != nil {
			return nil, err
		}

		// Constants
		var constCount uint32
		if err := binary.Read(r, binary.BigEndian, &constCount); err != nil {
			return nil, err
		}
		if constCount > 1<<20 {
			return nil, fmt.Errorf("too many constants: %d", constCount)
		}
		fn.Constants = make([]Constant, constCount)
		for j := range fn.Constants {
			var kind byte
			if err := binary.Read(r, binary.BigEndian, &kind); err != nil {
				return nil, err
			}
			fn.Constants[j].Kind = ConstantKind(kind)

			switch ConstantKind(kind) {
			case ConstBool:
				var v byte
				if err := binary.Read(r, binary.BigEndian, &v); err != nil {
					return nil, err
				}
				fn.Constants[j].Data = uint64(v)
			case ConstByte:
				var v byte
				if err := binary.Read(r, binary.BigEndian, &v); err != nil {
					return nil, err
				}
				fn.Constants[j].Data = uint64(v)
			case ConstInt:
				var v int64
				if err := binary.Read(r, binary.BigEndian, &v); err != nil {
					return nil, err
				}
				fn.Constants[j].Data = uint64(v)
			case ConstFloat:
				var v uint64
				if err := binary.Read(r, binary.BigEndian, &v); err != nil {
					return nil, err
				}
				fn.Constants[j].Data = v
			case ConstChar:
				var v uint32
				if err := binary.Read(r, binary.BigEndian, &v); err != nil {
					return nil, err
				}
				fn.Constants[j].Data = uint64(v)
			case ConstString:
				var strLen uint32
				if err := binary.Read(r, binary.BigEndian, &strLen); err != nil {
					return nil, err
				}
				if strLen > 1<<24 {
					return nil, fmt.Errorf("string too large: %d", strLen)
				}
				buf := make([]byte, strLen)
				if _, err := io.ReadFull(r, buf); err != nil {
					return nil, err
				}
				fn.Constants[j].Str = string(buf)
			default:
				return nil, fmt.Errorf("unknown constant kind: %d", kind)
			}
		}

		// Source map
		var smCount uint32
		if err := binary.Read(r, binary.BigEndian, &smCount); err != nil {
			return nil, err
		}
		if smCount > 1<<20 {
			return nil, fmt.Errorf("source map too large: %d", smCount)
		}
		fn.SourceMap = make([]SourceSpan, smCount)
		for j := range fn.SourceMap {
			if err := binary.Read(r, binary.BigEndian, &fn.SourceMap[j].StartLine); err != nil {
				return nil, err
			}
			if err := binary.Read(r, binary.BigEndian, &fn.SourceMap[j].StartCol); err != nil {
				return nil, err
			}
			if err := binary.Read(r, binary.BigEndian, &fn.SourceMap[j].EndLine); err != nil {
				return nil, err
			}
			if err := binary.Read(r, binary.BigEndian, &fn.SourceMap[j].EndCol); err != nil {
				return nil, err
			}
		}
	}

	return p, nil
}

// Helper: write string with length prefix.
func writeString(w io.Writer, s string) error {
	if err := binary.Write(w, binary.BigEndian, uint32(len(s))); err != nil {
		return err
	}
	_, err := w.Write([]byte(s))
	return err
}

// Helper: read string with length prefix.
func readString(r io.Reader) (string, error) {
	var length uint32
	if err := binary.Read(r, binary.BigEndian, &length); err != nil {
		return "", err
	}
	if length > 1<<24 {
		return "", fmt.Errorf("string too long: %d", length)
	}
	buf := make([]byte, length)
	if _, err := io.ReadFull(r, buf); err != nil {
		return "", err
	}
	return string(buf), nil
}

// Encode encodes an instruction into a byte slice.
func Encode(op Opcode, operands ...uint64) []byte {
	info := Instructions[op]
	if len(operands) != len(info.Operands) {
		panic(fmt.Sprintf("bytecode: %s expects %d operands, got %d", info.Name, len(info.Operands), len(operands)))
	}

	size := 1 // opcode
	for _, opType := range info.Operands {
		switch opType {
		case OperandUint8:
			size++
		case OperandUint16:
			size += 2
		case OperandUint32, OperandFloat32, OperandInt32:
			size += 4
		case OperandInt64, OperandFloat64:
			size += 8
		case OperandString, OperandFuncIndex:
			size += 4
		}
	}

	buf := make([]byte, size)
	buf[0] = byte(op)
	offset := 1
	for i, opType := range info.Operands {
		v := operands[i]
		switch opType {
		case OperandUint8:
			buf[offset] = byte(v)
			offset++
		case OperandUint16:
			binary.BigEndian.PutUint16(buf[offset:], uint16(v))
			offset += 2
		case OperandUint32, OperandFloat32:
			binary.BigEndian.PutUint32(buf[offset:], uint32(v))
			offset += 4
		case OperandInt32:
			if int32(v) < 0 {
				binary.BigEndian.PutUint32(buf[offset:], uint32(v))
			} else {
				binary.BigEndian.PutUint32(buf[offset:], uint32(v))
			}
			offset += 4
		case OperandInt64, OperandFloat64:
			binary.BigEndian.PutUint64(buf[offset:], v)
			offset += 8
		case OperandString, OperandFuncIndex:
			binary.BigEndian.PutUint32(buf[offset:], uint32(v))
			offset += 4
		}
	}

	return buf
}

// Decode decodes a single instruction from a byte slice at the given offset.
func Decode(code []byte, offset int) (Instruction, int, error) {
	if offset >= len(code) {
		return Instruction{}, 0, fmt.Errorf("offset %d beyond code length %d", offset, len(code))
	}

	op := Opcode(code[offset])
	if int(op) >= len(Instructions) {
		return Instruction{}, 0, fmt.Errorf("invalid opcode %d at offset %d", op, offset)
	}

	info := Instructions[op]
	inst := Instruction{
		Opcode:   op,
		Offset:   offset,
		Operands: make([]uint64, len(info.Operands)),
	}

	pos := offset + 1
	for i, opType := range info.Operands {
		if pos >= len(code) {
			return Instruction{}, 0, fmt.Errorf("truncated instruction at offset %d", offset)
		}
		switch opType {
		case OperandUint8:
			inst.Operands[i] = uint64(code[pos])
			pos++
		case OperandUint16:
			if pos+2 > len(code) {
				return Instruction{}, 0, fmt.Errorf("truncated uint16 at offset %d", pos)
			}
			inst.Operands[i] = uint64(binary.BigEndian.Uint16(code[pos:]))
			pos += 2
		case OperandUint32, OperandFloat32:
			if pos+4 > len(code) {
				return Instruction{}, 0, fmt.Errorf("truncated uint32 at offset %d", pos)
			}
			inst.Operands[i] = uint64(binary.BigEndian.Uint32(code[pos:]))
			pos += 4
		case OperandInt32:
			if pos+4 > len(code) {
				return Instruction{}, 0, fmt.Errorf("truncated int32 at offset %d", pos)
			}
			inst.Operands[i] = uint64(binary.BigEndian.Uint32(code[pos:]))
			pos += 4
		case OperandInt64, OperandFloat64:
			if pos+8 > len(code) {
				return Instruction{}, 0, fmt.Errorf("truncated int64 at offset %d", pos)
			}
			inst.Operands[i] = binary.BigEndian.Uint64(code[pos:])
			pos += 8
		case OperandString, OperandFuncIndex:
			if pos+4 > len(code) {
				return Instruction{}, 0, fmt.Errorf("truncated uint32 at offset %d", pos)
			}
			inst.Operands[i] = uint64(binary.BigEndian.Uint32(code[pos:]))
			pos += 4
		}
	}

	return inst, pos, nil
}

// DecodeAll decodes all instructions from code.
func DecodeAll(code []byte) ([]Instruction, error) {
	var insts []Instruction
	offset := 0
	for offset < len(code) {
		inst, next, err := Decode(code, offset)
		if err != nil {
			return insts, err
		}
		insts = append(insts, inst)
		offset = next
	}
	return insts, nil
}

// Disassemble returns a human-readable disassembly of code.
func Disassemble(code []byte, constants []Constant) string {
	insts, err := DecodeAll(code)
	if err != nil {
		return fmt.Sprintf("error: %v", err)
	}
	var s string
	for _, inst := range insts {
		s += fmt.Sprintf("  %04d  %s\n", inst.Offset, inst.String())
	}
	return s
}
