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

// Package vm implements the stack-based virtual machine.
package vm

import (
	"context"
	"encoding/binary"
	"fmt"
	"math"
	"regexp"
	"strings"

	"github.com/dhoard/solvik-language/internal/bytecode"
)

// ValueKind represents the type of a VM value.
type ValueKind uint8

const (
	ValueNull ValueKind = iota
	ValueBool
	ValueByte
	ValueInt
	ValueLong
	ValueFloat
	ValueDouble
	ValueChar
	ValueString
	ValueList
	ValueMap
	ValueRegex
)

// Value represents a tagged VM value.
type Value struct {
	Kind ValueKind
	// Data fields - using interface{} for reference types
	boolVal      bool
	byteVal      uint8
	intVal       int32
	longVal      int64
	floatVal     float32
	doubleVal    float64
	charVal      rune
	strVal       string
	listVal      []Value
	mapVal       mapValue
	regexVal     *regexp.Regexp
	regexPattern string
}

// mapValue wraps a map with deterministic iteration order.
// Uses a pointer to the entries slice so that when the mapValue is copied (e.g., via DUP),
// both copies share the same underlying entries slice, and mutations are visible to both.
type mapValue struct {
	entries *[]mapEntry
}

type mapEntry struct {
	key   Value
	value Value
}

// findEntry finds the index of an entry with the given key, or -1.
func (mv *mapValue) findEntry(key Value) int {
	if mv.entries == nil {
		return -1
	}
	for i, e := range *mv.entries {
		if ValueEqual(e.key, key) {
			return i
		}
	}
	return -1
}

// appendEntry adds a new entry to the map.
func (mv *mapValue) appendEntry(key, value Value) {
	if mv.entries == nil {
		entries := make([]mapEntry, 0)
		mv.entries = &entries
	}
	*mv.entries = append(*mv.entries, mapEntry{key: key, value: value})
}

// NewValueBool creates a boolean value.
func NewValueBool(v bool) Value {
	return Value{Kind: ValueBool, boolVal: v}
}

// NewValueByte creates a byte value.
func NewValueByte(v uint8) Value {
	return Value{Kind: ValueByte, byteVal: v}
}

// NewValueInt creates an int value.
func NewValueInt(v int32) Value {
	return Value{Kind: ValueInt, intVal: v}
}

// NewValueLong creates a long value.
func NewValueLong(v int64) Value {
	return Value{Kind: ValueLong, longVal: v}
}

// NewValueFloat creates a float value.
func NewValueFloat(v float32) Value {
	return Value{Kind: ValueFloat, floatVal: v}
}

// NewValueDouble creates a double value.
func NewValueDouble(v float64) Value {
	return Value{Kind: ValueDouble, doubleVal: v}
}

// NewValueChar creates a char value.
func NewValueChar(v rune) Value {
	return Value{Kind: ValueChar, charVal: v}
}

// NewValueString creates a string value.
func NewValueString(v string) Value {
	return Value{Kind: ValueString, strVal: v}
}

// NewValueList creates a list value.
func NewValueList(v []Value) Value {
	return Value{Kind: ValueList, listVal: v}
}

// NewValueMap creates a map value.
func NewValueMap() Value {
	entries := make([]mapEntry, 0)
	return Value{
		Kind: ValueMap,
		mapVal: mapValue{
			entries: &entries,
		},
	}
}

// NewValueRegex creates a regex value.
func NewValueRegex(pattern string, compiled *regexp.Regexp) Value {
	return Value{Kind: ValueRegex, regexPattern: pattern, regexVal: compiled}
}

// IsRegex returns true if the value is a regex.
func (v Value) IsRegex() bool {
	return v.Kind == ValueRegex
}

// RegexMatch returns true if the regex matches the given string.
func (v Value) RegexMatch(s string) bool {
	if v.Kind != ValueRegex || v.regexVal == nil {
		return false
	}
	return v.regexVal.MatchString(s)
}

// NewValueNull creates a null value.
func NewValueNull() Value {
	return Value{Kind: ValueNull}
}

// Bool returns the boolean value. Panics if not a bool.
func (v Value) Bool() bool {
	if v.Kind != ValueBool {
		panic("value is not bool")
	}
	return v.boolVal
}

// Byte returns the byte value.
func (v Value) Byte() uint8 {
	if v.Kind != ValueByte {
		panic("value is not byte")
	}
	return v.byteVal
}

// Int returns the int value, widening from compatible numeric types.
func (v Value) Int() int32 {
	switch v.Kind {
	case ValueInt:
		return v.intVal
	case ValueByte:
		return int32(v.byteVal)
	case ValueChar:
		return int32(v.charVal)
	case ValueLong:
		return int32(v.longVal)
	case ValueFloat:
		return int32(v.floatVal)
	case ValueDouble:
		return int32(v.doubleVal)
	case ValueBool:
		if v.boolVal {
			return 1
		}
		return 0
	default:
		panic(fmt.Sprintf("value is not int (kind=%d)", v.Kind))
	}
}

// Long returns the long value, widening from compatible numeric types.
func (v Value) Long() int64 {
	switch v.Kind {
	case ValueLong:
		return v.longVal
	case ValueInt:
		return int64(v.intVal)
	case ValueByte:
		return int64(v.byteVal)
	case ValueChar:
		return int64(v.charVal)
	default:
		panic("value is not long")
	}
}

// Double returns the double value, widening float to double.
func (v Value) Double() float64 {
	switch v.Kind {
	case ValueDouble:
		return v.doubleVal
	case ValueFloat:
		return float64(v.floatVal)
	default:
		panic("value is not double")
	}
}

// Float returns the float value.
func (v Value) Float() float32 {
	if v.Kind != ValueFloat {
		panic("value is not float")
	}
	return v.floatVal
}

// Char returns the char value.
func (v Value) Char() rune {
	if v.Kind != ValueChar {
		panic("value is not char")
	}
	return v.charVal
}

// String returns the string value.
func (v Value) String() string {
	switch v.Kind {
	case ValueString:
		return v.strVal
	case ValueBool:
		if v.boolVal {
			return "true"
		}
		return "false"
	case ValueByte:
		return fmt.Sprintf("%d", v.byteVal)
	case ValueInt:
		return fmt.Sprintf("%d", v.intVal)
	case ValueLong:
		return fmt.Sprintf("%d", v.longVal)
	case ValueFloat:
		return fmt.Sprintf("%g", v.floatVal)
	case ValueDouble:
		return fmt.Sprintf("%g", v.doubleVal)
	case ValueChar:
		return string(v.charVal)
	case ValueNull:
		return "null"
	case ValueList:
		if len(v.listVal) == 0 {
			return "[]"
		}
		var b strings.Builder
		b.Grow(len(v.listVal) * 8)
		b.WriteByte('[')
		for i, e := range v.listVal {
			if i > 0 {
				b.WriteString(", ")
			}
			b.WriteString(e.String())
		}
		b.WriteByte(']')
		return b.String()
	case ValueMap:
		entries := v.mapVal.entries
		if entries == nil || len(*entries) == 0 {
			return "{}"
		}
		var b strings.Builder
		b.Grow(len(*entries) * 16)
		b.WriteByte('{')
		for i, e := range *entries {
			if i > 0 {
				b.WriteString(", ")
			}
			b.WriteString(e.key.String())
			b.WriteString(": ")
			b.WriteString(e.value.String())
		}
		b.WriteByte('}')
		return b.String()
	default:
		return "<unknown>"
	}
}

// ListLen returns the length of a list value. Returns 0 if not a list.
func (v Value) ListLen() int {
	if v.Kind != ValueList {
		return 0
	}
	return len(v.listVal)
}

// MapLen returns the number of entries in a map value. Returns 0 if not a map.
func (v Value) MapLen() int {
	if v.Kind != ValueMap {
		return 0
	}
	if v.mapVal.entries == nil {
		return 0
	}
	return len(*v.mapVal.entries)
}

// ListGet returns the element at index i from a list value. Returns null if out of range.
func (v Value) ListGet(i int) Value {
	if v.Kind != ValueList || i < 0 || i >= len(v.listVal) {
		return NewValueNull()
	}
	return v.listVal[i]
}

// IsNull returns true if the value is null.
func (v Value) IsNull() bool {
	return v.Kind == ValueNull
}

// IsTruthy returns true if the value is truthy.
func (v Value) IsTruthy() bool {
	switch v.Kind {
	case ValueBool:
		return v.boolVal
	case ValueNull:
		return false
	default:
		return true
	}
}

// ValueHash returns a hash code for map key operations.
func ValueHash(v Value) uint64 {
	switch v.Kind {
	case ValueBool:
		if v.boolVal {
			return 1
		}
		return 0
	case ValueByte:
		return uint64(v.byteVal)
	case ValueInt:
		return uint64(v.intVal)
	case ValueLong:
		return uint64(v.longVal)
	case ValueChar:
		return uint64(v.charVal)
	case ValueString:
		return stringHash(v.strVal)
	default:
		return 0
	}
}

// ValueEqual checks equality between two values.
func ValueEqual(a, b Value) bool {
	if a.Kind != b.Kind {
		return false
	}
	switch a.Kind {
	case ValueNull:
		return true
	case ValueBool:
		return a.boolVal == b.boolVal
	case ValueByte:
		return a.byteVal == b.byteVal
	case ValueInt:
		return a.intVal == b.intVal
	case ValueLong:
		return a.longVal == b.longVal
	case ValueFloat:
		return a.floatVal == b.floatVal
	case ValueDouble:
		return a.doubleVal == b.doubleVal
	case ValueChar:
		return a.charVal == b.charVal
	case ValueString:
		return a.strVal == b.strVal
	case ValueList:
		if len(a.listVal) != len(b.listVal) {
			return false
		}
		for i := range a.listVal {
			if !ValueEqual(a.listVal[i], b.listVal[i]) {
				return false
			}
		}
		return true
	case ValueMap:
		if a.mapVal.entries == nil || b.mapVal.entries == nil {
			return a.mapVal.entries == b.mapVal.entries
		}
		if len(*a.mapVal.entries) != len(*b.mapVal.entries) {
			return false
		}
		for i := range *a.mapVal.entries {
			if !ValueEqual((*a.mapVal.entries)[i].key, (*b.mapVal.entries)[i].key) ||
				!ValueEqual((*a.mapVal.entries)[i].value, (*b.mapVal.entries)[i].value) {
				return false
			}
		}
		return true
	default:
		return false
	}
}

func stringHash(s string) uint64 {
	var h uint64
	for _, c := range s {
		h = h*31 + uint64(c)
	}
	return h
}

// RuntimeError represents a runtime error with source location.
type RuntimeError struct {
	Code     string
	Message  string
	Function string
	Offset   int
	Line     int
	Column   int
	Stack    []StackFrame
}

func (e *RuntimeError) Error() string {
	return fmt.Sprintf("runtime %s: %s", e.Code, e.Message)
}

// StackFrame represents a call frame in a stack trace.
type StackFrame struct {
	Function string
	Offset   int
	Line     int
	Column   int
}

// Limits define resource constraints for the VM.
type Limits struct {
	MaxStackSize    int
	MaxCallDepth    int
	MaxInstructions int64
	MaxStringSize   int
	MaxListSize     int
	MaxMapSize      int
}

// DefaultLimits returns sensible default limits.
func DefaultLimits() Limits {
	return Limits{
		MaxStackSize:    65536,
		MaxCallDepth:    1024,
		MaxInstructions: 10000000,
		MaxStringSize:   1 << 24, // 16MB
		MaxListSize:     1 << 24,
		MaxMapSize:      1 << 20,
	}
}

// NativeFunction is a Go function that can be called from the VM.
type NativeFunction struct {
	Name    string
	Handler func(args []Value) (Value, error)
}

// NativeRegistry manages available native functions.
type NativeRegistry struct {
	funcs map[string]*NativeFunction
}

// NewNativeRegistry creates a new native registry.
func NewNativeRegistry() *NativeRegistry {
	return &NativeRegistry{funcs: make(map[string]*NativeFunction)}
}

// Register adds a native function.
func (r *NativeRegistry) Register(fn *NativeFunction) {
	r.funcs[fn.Name] = fn
}

// Lookup finds a native function by name.
func (r *NativeRegistry) Lookup(name string) (*NativeFunction, bool) {
	fn, ok := r.funcs[name]
	return fn, ok
}

// VM represents a virtual machine instance.
type VM struct {
	program     *bytecode.Program
	stack       []Value
	frames      []CallFrame
	globals     []Value
	natives     *NativeRegistry
	nativeCache []*NativeFunction // resolved native functions, indexed by native decl index
	limits      Limits
	ctx         context.Context
	instCount   int64
}

// CallFrame represents an active function call.
type CallFrame struct {
	FunctionID int
	IP         int
	StackBase  int
	LocalBase  int
	LocalCount int
}

// New creates a new VM.
func New(natives *NativeRegistry, limits Limits) *VM {
	return &VM{
		stack:   make([]Value, 0, 1024),
		frames:  make([]CallFrame, 0, 64),
		natives: natives,
		limits:  limits,
	}
}

// Execute runs a compiled program.
func (vm *VM) Execute(ctx context.Context, prog *bytecode.Program) (Value, error) {
	vm.program = prog
	vm.ctx = ctx
	vm.instCount = 0

	// Initialize globals
	vm.globals = make([]Value, prog.Globals)
	for i := range vm.globals {
		vm.globals[i] = NewValueNull()
	}

	// Pre-resolve native function cache (avoids string concat + map lookup per call)
	vm.nativeCache = make([]*NativeFunction, len(prog.Natives))
	for i := range prog.Natives {
		nd := &prog.Natives[i]
		fullName := nd.Module + "." + nd.Name
		nf, ok := vm.natives.Lookup(fullName)
		if !ok {
			nf, ok = vm.natives.Lookup(nd.Name)
		}
		if ok {
			vm.nativeCache[i] = nf
		}
	}

	// Find main function
	mainIdx := -1
	for i, fn := range prog.Functions {
		if fn.Name == "main" {
			mainIdx = i
			break
		}
	}

	if mainIdx < 0 {
		return NewValueNull(), &RuntimeError{
			Code:    "E001",
			Message: "no main function found",
		}
	}

	// Push main frame
	vm.pushFrame(CallFrame{
		FunctionID: mainIdx,
		IP:         0,
		StackBase:  0,
		LocalBase:  0,
		LocalCount: prog.Functions[mainIdx].ParamCount + prog.Functions[mainIdx].LocalCount,
	})

	// Allocate locals
	fn := prog.Functions[mainIdx]
	totalLocals := fn.ParamCount + fn.LocalCount
	for i := 0; i < totalLocals; i++ {
		vm.stack = append(vm.stack, NewValueNull())
	}

	// Run
	result, err := vm.run()
	if err != nil {
		return result, err
	}
	return result, nil
}

// run executes the current program.
func (vm *VM) run() (Value, error) {
	for {
		// Check context cancellation
		select {
		case <-vm.ctx.Done():
			return NewValueNull(), &RuntimeError{
				Code:    "E002",
				Message: "execution cancelled",
			}
		default:
		}

		// Check instruction limit
		vm.instCount++
		if vm.limits.MaxInstructions > 0 && vm.instCount > vm.limits.MaxInstructions {
			return NewValueNull(), &RuntimeError{
				Code:    "E003",
				Message: "instruction limit exceeded",
			}
		}

		frame := &vm.frames[len(vm.frames)-1]
		fn := vm.program.Functions[frame.FunctionID]

		if frame.IP >= len(fn.Code) {
			return NewValueNull(), &RuntimeError{
				Code:    "E004",
				Message: "execution fell off end of code",
			}
		}

		// Decode next instruction inline (avoids heap-allocated Instruction struct)
		op, operands, nextIP, err := vm.decodeOp(fn.Code, frame.IP)
		if err != nil {
			return NewValueNull(), &RuntimeError{
				Code:    "E005",
				Message: fmt.Sprintf("decode error: %v", err),
			}
		}

		frame.IP = nextIP

		switch op {
		case bytecode.OpNOP:
			// Do nothing

		case bytecode.OpCONST_BOOL:
			vm.push(NewValueBool(operands[0] != 0))

		case bytecode.OpCONST_BYTE:
			vm.push(NewValueByte(uint8(operands[0])))

		case bytecode.OpCONST_INT:
			vm.push(NewValueInt(int32(operands[0])))

		case bytecode.OpCONST_LONG:
			vm.push(NewValueLong(int64(operands[0])))

		case bytecode.OpCONST_FLOAT:
			vm.push(NewValueFloat(math.Float32frombits(uint32(operands[0]))))

		case bytecode.OpCONST_DOUBLE:
			vm.push(NewValueDouble(math.Float64frombits(operands[0])))

		case bytecode.OpCONST_CHAR:
			vm.push(NewValueChar(rune(operands[0])))

		case bytecode.OpCONST_STRING:
			idx := int(operands[0])
			if idx >= 0 && idx < len(fn.Constants) {
				vm.push(NewValueString(fn.Constants[idx].Str))
			} else {
				vm.push(NewValueString(""))
			}

		case bytecode.OpCONST_NULL:
			vm.push(NewValueNull())

		case bytecode.OpLOAD_LOCAL:
			idx := int(operands[0])
			addr := frame.StackBase + idx
			if addr >= 0 && addr < len(vm.stack) {
				vm.push(vm.stack[addr])
			} else {
				return NewValueNull(), vm.errorAt(frame, "E006", fmt.Sprintf("local index %d out of range", idx))
			}

		case bytecode.OpSTORE_LOCAL:
			idx := int(operands[0])
			addr := frame.StackBase + idx
			val := vm.pop()
			if addr >= 0 && addr < len(vm.stack) {
				vm.stack[addr] = val
			} else {
				return NewValueNull(), vm.errorAt(frame, "E007", fmt.Sprintf("local index %d out of range", idx))
			}

		case bytecode.OpLOAD_GLOBAL:
			idx := int(operands[0])
			if idx >= 0 && idx < len(vm.globals) {
				vm.push(vm.globals[idx])
			} else {
				return NewValueNull(), vm.errorAt(frame, "E008", fmt.Sprintf("global index %d out of range", idx))
			}

		case bytecode.OpSTORE_GLOBAL:
			idx := int(operands[0])
			val := vm.pop()
			if idx >= 0 && idx < len(vm.globals) {
				vm.globals[idx] = val
			} else {
				return NewValueNull(), vm.errorAt(frame, "E009", fmt.Sprintf("global index %d out of range", idx))
			}

		case bytecode.OpPOP:
			vm.pop()

		case bytecode.OpDUP:
			top := vm.peek()
			vm.push(top)

		case bytecode.OpADD_INT:
			b := vm.pop()
			a := vm.pop()
			// Handle string concatenation when the compiler emits ADD_INT for strings
			if a.Kind == ValueString || b.Kind == ValueString {
				vm.push(NewValueString(a.String() + b.String()))
			} else {
				vm.push(NewValueInt(a.Int() + b.Int()))
			}

		case bytecode.OpSUB_INT:
			b, a := vm.popInt(), vm.popInt()
			vm.push(NewValueInt(a - b))

		case bytecode.OpMUL_INT:
			b, a := vm.popInt(), vm.popInt()
			vm.push(NewValueInt(a * b))

		case bytecode.OpDIV_INT:
			b, a := vm.popInt(), vm.popInt()
			if b == 0 {
				return NewValueNull(), vm.errorAt(frame, "E010", "integer division by zero")
			}
			vm.push(NewValueInt(a / b))

		case bytecode.OpREM_INT:
			b, a := vm.popInt(), vm.popInt()
			if b == 0 {
				return NewValueNull(), vm.errorAt(frame, "E011", "integer modulo by zero")
			}
			vm.push(NewValueInt(a % b))

		case bytecode.OpNEG_INT:
			v := vm.popInt()
			vm.push(NewValueInt(-v))

		case bytecode.OpADD_LONG:
			b, a := vm.popLong(), vm.popLong()
			vm.push(NewValueLong(a + b))

		case bytecode.OpSUB_LONG:
			b, a := vm.popLong(), vm.popLong()
			vm.push(NewValueLong(a - b))

		case bytecode.OpMUL_LONG:
			b, a := vm.popLong(), vm.popLong()
			vm.push(NewValueLong(a * b))

		case bytecode.OpDIV_LONG:
			b, a := vm.popLong(), vm.popLong()
			if b == 0 {
				return NewValueNull(), vm.errorAt(frame, "E012", "long division by zero")
			}
			vm.push(NewValueLong(a / b))

		case bytecode.OpREM_LONG:
			b, a := vm.popLong(), vm.popLong()
			if b == 0 {
				return NewValueNull(), vm.errorAt(frame, "E013", "long modulo by zero")
			}
			vm.push(NewValueLong(a % b))

		case bytecode.OpNEG_LONG:
			v := vm.popLong()
			vm.push(NewValueLong(-v))

		case bytecode.OpADD_FLOAT:
			b, a := vm.popFloat(), vm.popFloat()
			vm.push(NewValueFloat(a + b))

		case bytecode.OpSUB_FLOAT:
			b, a := vm.popFloat(), vm.popFloat()
			vm.push(NewValueFloat(a - b))

		case bytecode.OpMUL_FLOAT:
			b, a := vm.popFloat(), vm.popFloat()
			vm.push(NewValueFloat(a * b))

		case bytecode.OpDIV_FLOAT:
			b, a := vm.popFloat(), vm.popFloat()
			vm.push(NewValueFloat(a / b))

		case bytecode.OpNEG_FLOAT:
			v := vm.popFloat()
			vm.push(NewValueFloat(-v))

		case bytecode.OpADD_DOUBLE:
			b, a := vm.popDouble(), vm.popDouble()
			vm.push(NewValueDouble(a + b))

		case bytecode.OpSUB_DOUBLE:
			b, a := vm.popDouble(), vm.popDouble()
			vm.push(NewValueDouble(a - b))

		case bytecode.OpMUL_DOUBLE:
			b, a := vm.popDouble(), vm.popDouble()
			vm.push(NewValueDouble(a * b))

		case bytecode.OpDIV_DOUBLE:
			b, a := vm.popDouble(), vm.popDouble()
			vm.push(NewValueDouble(a / b))

		case bytecode.OpNEG_DOUBLE:
			v := vm.popDouble()
			vm.push(NewValueDouble(-v))

		case bytecode.OpCONCAT_STRING:
			b, a := vm.popString(), vm.popString()
			vm.push(NewValueString(a + b))

		case bytecode.OpEQ_BOOL:
			b, a := vm.popBool(), vm.popBool()
			vm.push(NewValueBool(a == b))

		case bytecode.OpEQ_INT:
			b, a := vm.popInt(), vm.popInt()
			vm.push(NewValueBool(a == b))

		case bytecode.OpEQ_LONG:
			b, a := vm.popLong(), vm.popLong()
			vm.push(NewValueBool(a == b))

		case bytecode.OpEQ_FLOAT:
			b, a := vm.popFloat(), vm.popFloat()
			vm.push(NewValueBool(a == b))

		case bytecode.OpEQ_DOUBLE:
			b, a := vm.popDouble(), vm.popDouble()
			vm.push(NewValueBool(a == b))

		case bytecode.OpEQ_CHAR:
			b, a := vm.popChar(), vm.popChar()
			vm.push(NewValueBool(a == b))

		case bytecode.OpEQ_STRING:
			b, a := vm.popString(), vm.popString()
			vm.push(NewValueBool(a == b))

		case bytecode.OpEQ_REF:
			// Reference equality (pointer comparison)
			// Also handles regex matching: if one value is a regex and the other is a string,
			// perform regex matching instead of equality.
			b, a := vm.pop(), vm.pop()
			// Check for regex matching
			if a.Kind == ValueRegex && b.Kind == ValueString {
				vm.push(NewValueBool(a.RegexMatch(b.strVal)))
			} else if b.Kind == ValueRegex && a.Kind == ValueString {
				vm.push(NewValueBool(b.RegexMatch(a.strVal)))
			} else if a.Kind == ValueRegex || b.Kind == ValueRegex {
				// Regex compared to non-string: no match
				vm.push(NewValueBool(false))
			} else {
				// Standard reference equality
				vm.push(NewValueBool(a.String() == b.String() && a.Kind == b.Kind))
			}

		case bytecode.OpLT_INT:
			b, a := vm.popInt(), vm.popInt()
			vm.push(NewValueBool(a < b))

		case bytecode.OpLE_INT:
			b, a := vm.popInt(), vm.popInt()
			vm.push(NewValueBool(a <= b))

		case bytecode.OpGT_INT:
			b, a := vm.popInt(), vm.popInt()
			vm.push(NewValueBool(a > b))

		case bytecode.OpGE_INT:
			b, a := vm.popInt(), vm.popInt()
			vm.push(NewValueBool(a >= b))

		case bytecode.OpLT_LONG:
			b, a := vm.popLong(), vm.popLong()
			vm.push(NewValueBool(a < b))

		case bytecode.OpLE_LONG:
			b, a := vm.popLong(), vm.popLong()
			vm.push(NewValueBool(a <= b))

		case bytecode.OpGT_LONG:
			b, a := vm.popLong(), vm.popLong()
			vm.push(NewValueBool(a > b))

		case bytecode.OpGE_LONG:
			b, a := vm.popLong(), vm.popLong()
			vm.push(NewValueBool(a >= b))

		case bytecode.OpLT_FLOAT:
			b, a := vm.popFloat(), vm.popFloat()
			vm.push(NewValueBool(a < b))

		case bytecode.OpLE_FLOAT:
			b, a := vm.popFloat(), vm.popFloat()
			vm.push(NewValueBool(a <= b))

		case bytecode.OpGT_FLOAT:
			b, a := vm.popFloat(), vm.popFloat()
			vm.push(NewValueBool(a > b))

		case bytecode.OpGE_FLOAT:
			b, a := vm.popFloat(), vm.popFloat()
			vm.push(NewValueBool(a >= b))

		case bytecode.OpLT_DOUBLE:
			b, a := vm.popDouble(), vm.popDouble()
			vm.push(NewValueBool(a < b))

		case bytecode.OpLE_DOUBLE:
			b, a := vm.popDouble(), vm.popDouble()
			vm.push(NewValueBool(a <= b))

		case bytecode.OpGT_DOUBLE:
			b, a := vm.popDouble(), vm.popDouble()
			vm.push(NewValueBool(a > b))

		case bytecode.OpGE_DOUBLE:
			b, a := vm.popDouble(), vm.popDouble()
			vm.push(NewValueBool(a >= b))

		case bytecode.OpNOT_BOOL:
			v := vm.popBool()
			vm.push(NewValueBool(!v))

		case bytecode.OpBIT_AND_INT:
			b, a := vm.popInt(), vm.popInt()
			vm.push(NewValueInt(a & b))

		case bytecode.OpBIT_OR_INT:
			b, a := vm.popInt(), vm.popInt()
			vm.push(NewValueInt(a | b))

		case bytecode.OpBIT_XOR_INT:
			b, a := vm.popInt(), vm.popInt()
			vm.push(NewValueInt(a ^ b))

		case bytecode.OpBIT_NOT_INT:
			v := vm.popInt()
			vm.push(NewValueInt(^v))

		case bytecode.OpSHIFT_LEFT_INT:
			b, a := vm.popInt(), vm.popInt()
			vm.push(NewValueInt(a << uint32(b)))

		case bytecode.OpSHIFT_RIGHT_INT:
			b, a := vm.popInt(), vm.popInt()
			vm.push(NewValueInt(a >> uint32(b)))

		case bytecode.OpBIT_AND_LONG:
			b, a := vm.popLong(), vm.popLong()
			vm.push(NewValueLong(a & b))

		case bytecode.OpBIT_OR_LONG:
			b, a := vm.popLong(), vm.popLong()
			vm.push(NewValueLong(a | b))

		case bytecode.OpBIT_XOR_LONG:
			b, a := vm.popLong(), vm.popLong()
			vm.push(NewValueLong(a ^ b))

		case bytecode.OpBIT_NOT_LONG:
			v := vm.popLong()
			vm.push(NewValueLong(^v))

		case bytecode.OpSHIFT_LEFT_LONG:
			b, a := vm.popLong(), vm.popLong()
			vm.push(NewValueLong(a << uint64(b)))

		case bytecode.OpSHIFT_RIGHT_LONG:
			b, a := vm.popLong(), vm.popLong()
			vm.push(NewValueLong(a >> uint64(b)))

		case bytecode.OpJUMP:
			offset := int32(operands[0])
			target := frame.IP + int(offset)
			frame.IP = target

		case bytecode.OpJUMP_IF_FALSE:
			offset := int32(operands[0])
			cond := vm.popBool()
			if !cond {
				target := frame.IP + int(offset)
				frame.IP = target
			}

		case bytecode.OpJUMP_IF_TRUE:
			offset := int32(operands[0])
			cond := vm.popBool()
			if cond {
				target := frame.IP + int(offset)
				frame.IP = target
			}

		case bytecode.OpCALL:
			fnIdx := int(operands[0])
			argCount := int(operands[1])

			if fnIdx < 0 || fnIdx >= len(vm.program.Functions) {
				return NewValueNull(), vm.errorAt(frame, "E014", fmt.Sprintf("invalid function index: %d", fnIdx))
			}

			targetFn := vm.program.Functions[fnIdx]
			totalLocals := targetFn.ParamCount + targetFn.LocalCount

			// Check call depth
			if len(vm.frames) >= vm.limits.MaxCallDepth {
				return NewValueNull(), vm.errorAt(frame, "E015", "maximum call depth exceeded")
			}

			// Pop arguments (use stack buffer for small arg counts)
			var argsBuf [4]Value
			args := argsBuf[:]
			if argCount > len(argsBuf) {
				args = make([]Value, argCount)
			} else {
				args = args[:argCount]
			}
			for i := argCount - 1; i >= 0; i-- {
				args[i] = vm.pop()
			}

			// Push frame
			newFrame := CallFrame{
				FunctionID: fnIdx,
				IP:         0,
				StackBase:  len(vm.stack),
				LocalBase:  0,
				LocalCount: totalLocals,
			}

			vm.pushFrame(newFrame)

			// Push locals
			for i := 0; i < totalLocals; i++ {
				if i < targetFn.ParamCount && i < len(args) {
					vm.stack = append(vm.stack, args[i])
				} else {
					vm.stack = append(vm.stack, NewValueNull())
				}
			}

		case bytecode.OpCALL_NATIVE:
			nativeIdx := int(operands[0])
			argCount := int(operands[1])

			if nativeIdx < 0 || nativeIdx >= len(vm.program.Natives) {
				return NewValueNull(), vm.errorAt(frame, "E016", fmt.Sprintf("invalid native function index: %d", nativeIdx))
			}

			nd := vm.program.Natives[nativeIdx]

			// Pop arguments (use stack buffer for small arg counts)
			var argsBuf [4]Value
			args := argsBuf[:]
			if argCount > len(argsBuf) {
				args = make([]Value, argCount)
			} else {
				args = args[:argCount]
			}
			for i := argCount - 1; i >= 0; i-- {
				args[i] = vm.pop()
			}

			// Look up and call native (using pre-resolved cache to avoid string concat + map lookup)
			nf := vm.nativeCache[nativeIdx]
			if nf == nil {
				return NewValueNull(), vm.errorAt(frame, "E017", fmt.Sprintf("native function not found: %s.%s", nd.Module, nd.Name))
			}

			result, err := nf.Handler(args)
			if err != nil {
				return NewValueNull(), &RuntimeError{
					Code:     "E018",
					Message:  err.Error(),
					Function: fn.Name,
					Offset:   frame.IP,
				}
			}

			if nd.Return {
				vm.push(result)
			}

		case bytecode.OpRETURN:
			val := vm.pop()
			vm.popFrame()

			if len(vm.frames) == 0 {
				return val, nil
			}

			frame = &vm.frames[len(vm.frames)-1]
			vm.push(val)

		case bytecode.OpRETURN_VOID:
			vm.popFrame()

			if len(vm.frames) == 0 {
				return NewValueNull(), nil
			}

			frame = &vm.frames[len(vm.frames)-1]

		case bytecode.OpNEW_LIST:
			// Create empty list (nil slice is equivalent to empty for len/append)
			vm.push(Value{Kind: ValueList})

		case bytecode.OpLIST_GET:
			idx := vm.popInt()
			list := vm.pop()

			if list.Kind != ValueList {
				return NewValueNull(), vm.errorAt(frame, "E019", "cannot index non-list")
			}

			if idx < 0 || int(idx) >= len(list.listVal) {
				return NewValueNull(), vm.errorAt(frame, "E020", fmt.Sprintf("list index out of range: %d (length %d)", idx, len(list.listVal)))
			}
			vm.push(list.listVal[idx])

		case bytecode.OpLIST_SET:
			val := vm.pop()
			idx := vm.popInt()
			list := vm.pop()

			if list.Kind != ValueList {
				return NewValueNull(), vm.errorAt(frame, "E021", "cannot index non-list")
			}
			if idx < 0 || int(idx) >= len(list.listVal) {
				return NewValueNull(), vm.errorAt(frame, "E022", "list index out of range")
			}
			list.listVal[idx] = val

		case bytecode.OpLIST_APPEND:
			val := vm.pop()
			list := vm.pop()

			if list.Kind != ValueList {
				return NewValueNull(), vm.errorAt(frame, "E023", "cannot append to non-list")
			}
			if len(list.listVal) >= vm.limits.MaxListSize {
				return NewValueNull(), vm.errorAt(frame, "E024", "list size limit exceeded")
			}
			list.listVal = append(list.listVal, val)
			vm.push(list)

		case bytecode.OpLIST_LENGTH:
			list := vm.pop()
			if list.Kind != ValueList {
				return NewValueNull(), vm.errorAt(frame, "E025", "cannot get length of non-list")
			}
			vm.push(NewValueInt(int32(len(list.listVal))))

		case bytecode.OpNEW_MAP:
			vm.push(NewValueMap())

		case bytecode.OpMAP_GET:
			key := vm.pop()
			m := vm.pop()

			if m.Kind != ValueMap {
				return NewValueNull(), vm.errorAt(frame, "E026", "cannot index non-map")
			}

			if idx := m.mapVal.findEntry(key); idx >= 0 {
				if m.mapVal.entries != nil {
					vm.push((*m.mapVal.entries)[idx].value)
				} else {
					vm.push(NewValueNull())
				}
			} else {
				vm.push(NewValueNull()) // missing key
			}

		case bytecode.OpMAP_SET:
			val := vm.pop()
			key := vm.pop()
			m := vm.pop()

			if m.Kind != ValueMap {
				return NewValueNull(), vm.errorAt(frame, "E027", "cannot set on non-map")
			}

			if idx := m.mapVal.findEntry(key); idx >= 0 {
				if m.mapVal.entries != nil {
					(*m.mapVal.entries)[idx].value = val
				}
			} else {
				if m.mapVal.entries != nil && len(*m.mapVal.entries) >= vm.limits.MaxMapSize {
					return NewValueNull(), vm.errorAt(frame, "E028", "map size limit exceeded")
				}
				m.mapVal.appendEntry(key, val)
			}
			// Push the modified map back onto the stack
			vm.push(m)

		case bytecode.OpMAP_CONTAINS:
			key := vm.pop()
			m := vm.pop()

			if m.Kind != ValueMap {
				return NewValueNull(), vm.errorAt(frame, "E029", "cannot check contains on non-map")
			}

			exists := m.mapVal.findEntry(key) >= 0
			vm.push(NewValueBool(exists))

		case bytecode.OpMAP_LENGTH:
			m := vm.pop()
			if m.Kind != ValueMap {
				return NewValueNull(), vm.errorAt(frame, "E030", "cannot get length of non-map")
			}
			if m.mapVal.entries == nil {
				vm.push(NewValueInt(0))
			} else {
				vm.push(NewValueInt(int32(len(*m.mapVal.entries))))
			}

		case bytecode.OpCOALESCE:
			right := vm.pop()
			left := vm.pop()

			if left.IsNull() {
				vm.push(right)
			} else {
				vm.push(left)
			}

		case bytecode.OpCHECK_NOT_NULL:
			v := vm.peek()
			if v.IsNull() {
				return NewValueNull(), vm.errorAt(frame, "E031", "null reference")
			}

		case bytecode.OpCONVERT_BYTE_TO_INT:
			v := vm.popByte()
			vm.push(NewValueInt(int32(v)))

		case bytecode.OpCONVERT_INT_TO_LONG:
			v := vm.popInt()
			vm.push(NewValueLong(int64(v)))

		case bytecode.OpCONVERT_FLOAT_TO_DOUBLE:
			v := vm.popFloat()
			vm.push(NewValueDouble(float64(v)))

		case bytecode.OpHALT:
			return NewValueNull(), nil

		default:
			return NewValueNull(), vm.errorAt(frame, "E032", fmt.Sprintf("unknown opcode: %d", op))
		}
	}
}

// decodeOp decodes the opcode and operands at the given offset without heap allocation.
// Returns the opcode, up to 2 operands in a fixed-size array, and the next offset.
func (vm *VM) decodeOp(code []byte, offset int) (bytecode.Opcode, [2]uint64, int, error) {
	if offset >= len(code) {
		return 0, [2]uint64{}, 0, fmt.Errorf("offset %d beyond code length %d", offset, len(code))
	}

	op := bytecode.Opcode(code[offset])
	if int(op) >= len(bytecode.Instructions) {
		return 0, [2]uint64{}, 0, fmt.Errorf("invalid opcode %d at offset %d", op, offset)
	}

	info := bytecode.Instructions[op]
	var operands [2]uint64
	pos := offset + 1

	for i, opType := range info.Operands {
		if i >= 2 {
			break
		}
		if pos >= len(code) {
			return 0, [2]uint64{}, 0, fmt.Errorf("truncated instruction at offset %d", offset)
		}
		switch opType {
		case bytecode.OperandUint8:
			operands[i] = uint64(code[pos])
			pos++
		case bytecode.OperandUint16:
			if pos+2 > len(code) {
				return 0, [2]uint64{}, 0, fmt.Errorf("truncated uint16 at offset %d", pos)
			}
			operands[i] = uint64(binary.BigEndian.Uint16(code[pos:]))
			pos += 2
		case bytecode.OperandUint32, bytecode.OperandFloat32, bytecode.OperandInt32:
			if pos+4 > len(code) {
				return 0, [2]uint64{}, 0, fmt.Errorf("truncated uint32 at offset %d", pos)
			}
			operands[i] = uint64(binary.BigEndian.Uint32(code[pos:]))
			pos += 4
		case bytecode.OperandInt64, bytecode.OperandFloat64:
			if pos+8 > len(code) {
				return 0, [2]uint64{}, 0, fmt.Errorf("truncated int64 at offset %d", pos)
			}
			operands[i] = binary.BigEndian.Uint64(code[pos:])
			pos += 8
		case bytecode.OperandString, bytecode.OperandFuncIndex:
			if pos+4 > len(code) {
				return 0, [2]uint64{}, 0, fmt.Errorf("truncated uint32 at offset %d", pos)
			}
			operands[i] = uint64(binary.BigEndian.Uint32(code[pos:]))
			pos += 4
		}
	}

	return op, operands, pos, nil
}

// Stack operations

func (vm *VM) push(v Value) {
	if len(vm.stack) >= vm.limits.MaxStackSize {
		// Hard limit to prevent memory exhaustion - just drop values
		// The instruction that caused the push will fail gracefully
		return
	}
	vm.stack = append(vm.stack, v)
}

func (vm *VM) pop() Value {
	if len(vm.stack) == 0 {
		return NewValueNull()
	}
	v := vm.stack[len(vm.stack)-1]
	vm.stack = vm.stack[:len(vm.stack)-1]
	return v
}

func (vm *VM) peek() Value {
	if len(vm.stack) == 0 {
		return NewValueNull()
	}
	return vm.stack[len(vm.stack)-1]
}

func (vm *VM) popBool() bool {
	return vm.pop().Bool()
}

func (vm *VM) popByte() uint8 {
	return vm.pop().Byte()
}

func (vm *VM) popInt() int32 {
	return vm.pop().Int()
}

func (vm *VM) popLong() int64 {
	return vm.pop().Long()
}

func (vm *VM) popFloat() float32 {
	return vm.pop().Float()
}

func (vm *VM) popDouble() float64 {
	return vm.pop().Double()
}

func (vm *VM) popChar() rune {
	return vm.pop().Char()
}

func (vm *VM) popString() string {
	return vm.pop().String()
}

// Frame operations

func (vm *VM) pushFrame(f CallFrame) {
	if len(vm.frames) >= vm.limits.MaxCallDepth {
		// Limit reached - will be caught by the CALL instruction
		vm.frames = append(vm.frames, f)
	}
	vm.frames = append(vm.frames, f)
}

func (vm *VM) popFrame() {
	if len(vm.frames) == 0 {
		return
	}
	f := vm.frames[len(vm.frames)-1]

	// Pop stack back to before this frame's locals
	if f.StackBase < len(vm.stack) {
		vm.stack = vm.stack[:f.StackBase]
	}
	vm.frames = vm.frames[:len(vm.frames)-1]
}

// errorAt creates a RuntimeError with source location information.
func (vm *VM) errorAt(frame *CallFrame, code, msg string) *RuntimeError {
	fn := vm.program.Functions[frame.FunctionID]

	err := &RuntimeError{
		Code:     code,
		Message:  msg,
		Function: fn.Name,
		Offset:   frame.IP,
	}

	// Build stack trace
	for i := len(vm.frames) - 1; i >= 0; i-- {
		f := vm.frames[i]
		fn2 := vm.program.Functions[f.FunctionID]
		err.Stack = append(err.Stack, StackFrame{
			Function: fn2.Name,
			Offset:   f.IP,
		})
	}

	return err
}

// FormatStackTrace returns a human-readable stack trace.
func FormatStackTrace(err *RuntimeError) string {
	var b strings.Builder
	b.WriteString(fmt.Sprintf("runtime error: %s: %s\n", err.Code, err.Message))
	if err.Function != "" {
		b.WriteString(fmt.Sprintf("  in function %s at offset %d\n", err.Function, err.Offset))
	}
	for _, sf := range err.Stack {
		b.WriteString(fmt.Sprintf("  called from %s at offset %d\n", sf.Function, sf.Offset))
	}
	return b.String()
}

// Standard native functions for initialization
func init() {
	// Register standard natives
	_ = ValueEqual
	_ = ValueHash
}
