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
	ValueFloat
	ValueChar
	ValueString
	ValueList
	ValueMap
	ValueRegex
	ValueException
)

// Value represents a tagged VM value.
// Uses a hybrid storage approach for zero-allocation common paths:
//   - immData: inline storage for bool, byte, int32, int64, float32, float64, char (rune)
//   - Data: heap types (string, []Value, mapValue, *regexp.Regexp, exceptionValue)
//
// Size: 1 (Kind) + 7 (pad) + 8 (immData) + 16 (any) = 32 bytes
// Previously: 144 bytes (explicit fields). Before that: 24 bytes (pure any, but boxed allocs).
type Value struct {
	Kind    ValueKind
	immData uint64
	Data    any
}

// exceptionValue represents a caught or constructed exception.
type exceptionValue struct {
	message string
	trace   string
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
func (mv mapValue) findEntry(key Value) int {
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
func (mv mapValue) appendEntry(key, value Value) {
	if mv.entries == nil {
		entries := make([]mapEntry, 0)
		mv.entries = &entries
	}
	*mv.entries = append(*mv.entries, mapEntry{key: key, value: value})
}

// NewValueBool creates a boolean value.
func NewValueBool(v bool) Value {
	var d uint64
	if v {
		d = 1
	}
	return Value{Kind: ValueBool, immData: d}
}

// NewValueByte creates a byte value.
func NewValueByte(v uint8) Value {
	return Value{Kind: ValueByte, immData: uint64(v)}
}

// NewValueInt creates an int value (zero-alloc, stored inline in immData).
func NewValueInt(v int64) Value {
	return Value{Kind: ValueInt, immData: uint64(v)}
}

// NewValueFloat creates a float value (zero-alloc, stored inline in immData).
func NewValueFloat(v float64) Value {
	return Value{Kind: ValueFloat, immData: math.Float64bits(v)}
}

// NewValueChar creates a char value.
func NewValueChar(v rune) Value {
	return Value{Kind: ValueChar, immData: uint64(v)}
}

// NewValueString creates a string value.
func NewValueString(v string) Value {
	return Value{Kind: ValueString, Data: v}
}

// NewValueList creates a list value.
func NewValueList(v []Value) Value {
	return Value{Kind: ValueList, Data: v}
}

// NewValueMap creates a map value.
func NewValueMap() Value {
	entries := make([]mapEntry, 0)
	return Value{
		Kind: ValueMap,
		Data: mapValue{
			entries: &entries,
		},
	}
}

// NewValueException creates an exception value.
func NewValueException(message, trace string) Value {
	return Value{Kind: ValueException, Data: exceptionValue{message: message, trace: trace}}
}

// NewValueRegex creates a regex value.
func NewValueRegex(pattern string, compiled *regexp.Regexp) Value {
	return Value{Kind: ValueRegex, Data: compiled}
}

// IsRegex returns true if the value is a regex.
func (v Value) IsRegex() bool {
	return v.Kind == ValueRegex
}

// RegexMatch returns true if the regex matches the given string.
func (v Value) RegexMatch(s string) bool {
	if v.Kind != ValueRegex {
		return false
	}
	r, ok := v.Data.(*regexp.Regexp)
	if !ok || r == nil {
		return false
	}
	return r.MatchString(s)
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
	return v.immData != 0
}

// Byte returns the byte value.
func (v Value) Byte() uint8 {
	if v.Kind != ValueByte {
		panic("value is not byte")
	}
	return uint8(v.immData)
}

// Int returns the int64 value, widening from compatible numeric types.
func (v Value) Int() int64 {
	switch v.Kind {
	case ValueInt:
		return int64(v.immData)
	case ValueByte:
		return int64(uint8(v.immData))
	case ValueChar:
		return int64(rune(v.immData))
	case ValueFloat:
		return int64(math.Float64frombits(v.immData))
	case ValueBool:
		if v.immData != 0 {
			return 1
		}
		return 0
	default:
		panic(fmt.Sprintf("value is not int (kind=%d)", v.Kind))
	}
}

// MustFloat returns the float64 value. Panics if not a float.
func (v Value) Float64() float64 {
	if v.Kind != ValueFloat {
		panic("value is not float")
	}
	return math.Float64frombits(v.immData)
}

// Double returns the float64 value, widening from compatible numeric types.
func (v Value) Double() float64 {
	switch v.Kind {
	case ValueFloat:
		return math.Float64frombits(v.immData)
	default:
		panic("value is not float/double")
	}
}

// Char returns the char value.
func (v Value) Char() rune {
	if v.Kind != ValueChar {
		panic("value is not char")
	}
	return rune(v.immData)
}

// String returns the string value.
func (v Value) String() string {
	switch v.Kind {
	case ValueString:
		return v.Data.(string)
	case ValueBool:
		if v.immData != 0 {
			return "true"
		}
		return "false"
	case ValueByte:
		return fmt.Sprintf("%d", uint8(v.immData))
	case ValueInt:
		return fmt.Sprintf("%d", int64(v.immData))
	case ValueFloat:
		return fmt.Sprintf("%g", math.Float64frombits(v.immData))
	case ValueChar:
		return string(rune(v.immData))
	case ValueNull:
		return "null"
	case ValueList:
		list := v.Data.([]Value)
		if len(list) == 0 {
			return "[]"
		}
		var b strings.Builder
		b.Grow(len(list) * 8)
		b.WriteByte('[')
		for i, e := range list {
			if i > 0 {
				b.WriteString(", ")
			}
			b.WriteString(e.String())
		}
		b.WriteByte(']')
		return b.String()
	case ValueMap:
		mv := v.Data.(mapValue)
		entries := mv.entries
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
	case ValueException:
		return v.Data.(exceptionValue).message
	default:
		return "<unknown>"
	}
}

// ListLen returns the length of a list value. Returns 0 if not a list.
func (v Value) ListLen() int {
	if v.Kind != ValueList {
		return 0
	}
	return len(v.Data.([]Value))
}

// MapLen returns the number of entries in a map value. Returns 0 if not a map.
func (v Value) MapLen() int {
	if v.Kind != ValueMap {
		return 0
	}
	mv := v.Data.(mapValue)
	if mv.entries == nil {
		return 0
	}
	return len(*mv.entries)
}

// MapContains returns true if the map contains the given key.
func (v Value) MapContains(key Value) bool {
	if v.Kind != ValueMap {
		return false
	}
	return v.Data.(mapValue).findEntry(key) >= 0
}

// ListGet returns the element at index i from a list value. Returns null if out of range.
func (v Value) ListGet(i int) Value {
	if v.Kind != ValueList || i < 0 {
		return NewValueNull()
	}
	list := v.Data.([]Value)
	if i >= len(list) {
		return NewValueNull()
	}
	return list[i]
}

// IsNull returns true if the value is null.
func (v Value) IsNull() bool {
	return v.Kind == ValueNull
}

// IsTruthy returns true if the value is truthy.
func (v Value) IsTruthy() bool {
	switch v.Kind {
	case ValueBool:
		return v.immData != 0
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
		if v.immData != 0 {
			return 1
		}
		return 0
	case ValueByte:
		return v.immData
	case ValueInt:
		return v.immData
	case ValueChar:
		return v.immData
	case ValueString:
		return stringHash(v.Data.(string))
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
		return a.immData == b.immData
	case ValueByte:
		return a.immData == b.immData
	case ValueInt:
		return a.immData == b.immData
	case ValueFloat:
		return a.immData == b.immData
	case ValueChar:
		return a.immData == b.immData
	case ValueString:
		return a.Data.(string) == b.Data.(string)
	case ValueList:
		alist := a.Data.([]Value)
		blist := b.Data.([]Value)
		if len(alist) != len(blist) {
			return false
		}
		for i := range alist {
			if !ValueEqual(alist[i], blist[i]) {
				return false
			}
		}
		return true
	case ValueMap:
		amv := a.Data.(mapValue)
		bmv := b.Data.(mapValue)
		if amv.entries == nil || bmv.entries == nil {
			return amv.entries == bmv.entries
		}
		if len(*amv.entries) != len(*bmv.entries) {
			return false
		}
		for i := range *amv.entries {
			if !ValueEqual((*amv.entries)[i].key, (*bmv.entries)[i].key) ||
				!ValueEqual((*amv.entries)[i].value, (*bmv.entries)[i].value) {
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

	// Exception handling
	handlerStack     []handlerEntry // stack of active handlers
	pendingException *Value         // non-nil when an exception is being propagated
}

// PendingAction represents an abrupt completion that must be handled by finally blocks.
type PendingAction int

const (
	ActionNone      PendingAction = iota
	ActionException               // propagating an exception
)

// CallFrame represents an active function call.
type CallFrame struct {
	FunctionID int
	IP         int
	StackBase  int
	LocalBase  int
	LocalCount int
}

// handlerEntry represents an active exception handler on the handler stack.
type handlerEntry struct {
	catchOffset   int // byte offset of catch handler, 0 if none
	finallyOffset int // byte offset of finally handler, 0 if none
	stackDepth    int // stack depth to restore on exception
	frameIndex    int // index of the frame this handler belongs to
	active        bool
	inFinally     bool // true when currently executing this handler's finally block
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
	// Batch-check interval for instruction counts and context cancellation.
	// Checking on every instruction is expensive (select on channel is ~20-40ns
	// atomic op). Instead, check in batches, and also check on call/return
	// boundaries where resumption is natural.
	const limitCheckInterval = 1024

	for {
		// Batch instruction counter check (avoid per-instruction atomic overhead)
		vm.instCount++
		if vm.limits.MaxInstructions > 0 && vm.instCount&(limitCheckInterval-1) == 0 {
			if vm.instCount > vm.limits.MaxInstructions {
				return NewValueNull(), &RuntimeError{
					Code:    "E003",
					Message: "instruction limit exceeded",
				}
			}
			// Batch context check alongside instruction limit
			select {
			case <-vm.ctx.Done():
				return NewValueNull(), &RuntimeError{
					Code:    "E002",
					Message: "execution cancelled",
				}
			default:
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
			vm.push(NewValueInt(int64(operands[0])))

		case bytecode.OpCONST_FLOAT:
			vm.push(NewValueFloat(math.Float64frombits(operands[0])))

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
			b, a := vm.popInt(), vm.popInt()
			vm.push(NewValueInt(a + b))

		case bytecode.OpSUB_INT:
			b, a := vm.popInt(), vm.popInt()
			vm.push(NewValueInt(a - b))

		case bytecode.OpMUL_INT:
			b, a := vm.popInt(), vm.popInt()
			vm.push(NewValueInt(a * b))

		case bytecode.OpDIV_INT:
			b, a := vm.popInt(), vm.popInt()
			if b == 0 {
				result, err := vm.throwRuntimeException(frame, "E010", "integer division by zero")
				if err != nil {
					return result, err
				}
				break
			}
			vm.push(NewValueInt(a / b))

		case bytecode.OpREM_INT:
			b, a := vm.popInt(), vm.popInt()
			if b == 0 {
				result, err := vm.throwRuntimeException(frame, "E011", "integer modulo by zero")
				if err != nil {
					return result, err
				}
				break
			}
			vm.push(NewValueInt(a % b))

		case bytecode.OpNEG_INT:
			v := vm.popInt()
			vm.push(NewValueInt(-v))

		case bytecode.OpADD_FLOAT:
			b, a := vm.popDouble(), vm.popDouble()
			vm.push(NewValueFloat(a + b))

		case bytecode.OpSUB_FLOAT:
			b, a := vm.popDouble(), vm.popDouble()
			vm.push(NewValueFloat(a - b))

		case bytecode.OpMUL_FLOAT:
			b, a := vm.popDouble(), vm.popDouble()
			vm.push(NewValueFloat(a * b))

		case bytecode.OpDIV_FLOAT:
			b, a := vm.popDouble(), vm.popDouble()
			vm.push(NewValueFloat(a / b))

		case bytecode.OpNEG_FLOAT:
			v := vm.popDouble()
			vm.push(NewValueFloat(-v))

		case bytecode.OpCONCAT_STRING:
			b, a := vm.popString(), vm.popString()
			vm.push(NewValueString(a + b))

		case bytecode.OpEQ_BOOL:
			b, a := vm.popBool(), vm.popBool()
			vm.push(NewValueBool(a == b))

		case bytecode.OpEQ_INT:
			b, a := vm.popInt(), vm.popInt()
			vm.push(NewValueBool(a == b))

		case bytecode.OpEQ_FLOAT:
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
				vm.push(NewValueBool(a.RegexMatch(b.Data.(string))))
			} else if b.Kind == ValueRegex && a.Kind == ValueString {
				vm.push(NewValueBool(b.RegexMatch(a.Data.(string))))
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

		case bytecode.OpLT_FLOAT:
			b, a := vm.popDouble(), vm.popDouble()
			vm.push(NewValueBool(a < b))

		case bytecode.OpLE_FLOAT:
			b, a := vm.popDouble(), vm.popDouble()
			vm.push(NewValueBool(a <= b))

		case bytecode.OpGT_FLOAT:
			b, a := vm.popDouble(), vm.popDouble()
			vm.push(NewValueBool(a > b))

		case bytecode.OpGE_FLOAT:
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
			vm.push(NewValueInt(^v)) // 64-bit bitwise NOT

		case bytecode.OpSHIFT_LEFT_INT:
			b, a := vm.popInt(), vm.popInt()
			vm.push(NewValueInt(a << uint64(b)))

		case bytecode.OpSHIFT_RIGHT_INT:
			b, a := vm.popInt(), vm.popInt()
			vm.push(NewValueInt(a >> uint64(b)))

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
			// Check cancellation at call boundaries for responsiveness
			select {
			case <-vm.ctx.Done():
				return NewValueNull(), &RuntimeError{
					Code:    "E002",
					Message: "execution cancelled",
				}
			default:
			}
			fnIdx := int(operands[0])
			argCount := int(operands[1])

			if fnIdx < 0 || fnIdx >= len(vm.program.Functions) {
				return NewValueNull(), vm.errorAt(frame, "E014", fmt.Sprintf("invalid function index: %d", fnIdx))
			}

			targetFn := vm.program.Functions[fnIdx]
			totalLocals := targetFn.ParamCount + targetFn.LocalCount

			// Check call depth
			if vm.limits.MaxCallDepth > 0 && len(vm.frames) >= vm.limits.MaxCallDepth {
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
				result, err := vm.throwRuntimeException(frame, "E016", fmt.Sprintf("invalid native function index: %d", nativeIdx))
				if err != nil {
					return result, err
				}
				break
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
				result, err := vm.throwRuntimeException(frame, "E017", fmt.Sprintf("native function not found: %s.%s", nd.Module, nd.Name))
				if err != nil {
					return result, err
				}
				break
			}

			result, err := nf.Handler(args)
			if err != nil {
				// Convert native function errors to catchable exceptions
				res, e := vm.throwRuntimeException(frame, "E018", err.Error())
				if e != nil {
					return res, e
				}
				break
			}

			if nd.Return {
				vm.push(result)
			}

		case bytecode.OpRETURN:
			// Check cancellation at return boundaries for responsiveness
			select {
			case <-vm.ctx.Done():
				return NewValueNull(), &RuntimeError{
					Code:    "E002",
					Message: "execution cancelled",
				}
			default:
			}
			val := vm.pop()
			vm.popFrame()

			if len(vm.frames) == 0 {
				return val, nil
			}

			frame = &vm.frames[len(vm.frames)-1]
			vm.push(val)

		case bytecode.OpRETURN_VOID:
			// Check cancellation at return boundaries for responsiveness
			select {
			case <-vm.ctx.Done():
				return NewValueNull(), &RuntimeError{
					Code:    "E002",
					Message: "execution cancelled",
				}
			default:
			}
			vm.popFrame()

			if len(vm.frames) == 0 {
				return NewValueNull(), nil
			}

			frame = &vm.frames[len(vm.frames)-1]

		case bytecode.OpRETURN_MULTI:
			// Check cancellation at return boundaries for responsiveness
			select {
			case <-vm.ctx.Done():
				return NewValueNull(), &RuntimeError{
					Code:    "E002",
					Message: "execution cancelled",
				}
			default:
			}
			count := int(operands[0])
			vals := make([]Value, count)
			for i := count - 1; i >= 0; i-- {
				vals[i] = vm.pop()
			}
			vm.popFrame()

			if len(vm.frames) == 0 {
				// Top-level return: use first value for exit
				if count > 0 {
					return vals[0], nil
				}
				return NewValueNull(), nil
			}

			frame = &vm.frames[len(vm.frames)-1]
			for _, v := range vals {
				vm.push(v)
			}

		case bytecode.OpNEW_LIST:
			// Create empty list (nil slice is equivalent to empty for len/append)
			vm.push(NewValueList(nil))

		case bytecode.OpLIST_GET:
			idx := vm.popInt()
			list := vm.pop()

			if list.Kind != ValueList {
				result, err := vm.throwRuntimeException(frame, "E019", "cannot index non-list")
				if err != nil {
					return result, err
				}
				break
			}
			l := list.Data.([]Value)
			if idx < 0 || int(idx) >= len(l) {
				result, err := vm.throwRuntimeException(frame, "E020",
					fmt.Sprintf("list index out of range: %d (length %d)", idx, len(l)))
				if err != nil {
					return result, err
				}
				break
			}
			vm.push(l[idx])

		case bytecode.OpLIST_SET:
			val := vm.pop()
			idx := vm.popInt()
			list := vm.pop()

			if list.Kind != ValueList {
				result, err := vm.throwRuntimeException(frame, "E021", "cannot index non-list")
				if err != nil {
					return result, err
				}
				break
			}
			l := list.Data.([]Value)
			if idx < 0 || int(idx) >= len(l) {
				result, err := vm.throwRuntimeException(frame, "E022",
					fmt.Sprintf("list index out of range: %d (length %d)", idx, len(l)))
				if err != nil {
					return result, err
				}
				break
			}
			l[idx] = val

		case bytecode.OpLIST_APPEND:
			val := vm.pop()
			list := vm.pop()

			if list.Kind != ValueList {
				result, err := vm.throwRuntimeException(frame, "E023", "cannot append to non-list")
				if err != nil {
					return result, err
				}
				break
			}
			l := list.Data.([]Value)
			if len(l) >= vm.limits.MaxListSize {
				result, err := vm.throwRuntimeException(frame, "E024", "list size limit exceeded")
				if err != nil {
					return result, err
				}
				break
			}
			list.Data = append(l, val)
			vm.push(list)

		case bytecode.OpLIST_LENGTH:
			list := vm.pop()
			if list.Kind != ValueList {
				return NewValueNull(), vm.errorAt(frame, "E025", "cannot get length of non-list")
			}
			vm.push(NewValueInt(int64(len(list.Data.([]Value)))))

		case bytecode.OpNEW_MAP:
			vm.push(NewValueMap())

		case bytecode.OpMAP_GET:
			key := vm.pop()
			m := vm.pop()

			if m.Kind != ValueMap {
				result, err := vm.throwRuntimeException(frame, "E026", "cannot index non-map")
				if err != nil {
					return result, err
				}
				break
			}
			mv := m.Data.(mapValue)
			if idx := mv.findEntry(key); idx >= 0 {
				if mv.entries != nil {
					vm.push((*mv.entries)[idx].value)
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
			mv := m.Data.(mapValue)
			if idx := mv.findEntry(key); idx >= 0 {
				if mv.entries != nil {
					(*mv.entries)[idx].value = val
				}
			} else {
				if mv.entries != nil && len(*mv.entries) >= vm.limits.MaxMapSize {
					return NewValueNull(), vm.errorAt(frame, "E028", "map size limit exceeded")
				}
				mv.appendEntry(key, val)
			}
			// Push the modified map back onto the stack
			vm.push(m)

		case bytecode.OpMAP_CONTAINS:
			key := vm.pop()
			m := vm.pop()

			if m.Kind != ValueMap {
				return NewValueNull(), vm.errorAt(frame, "E029", "cannot check contains on non-map")
			}
			mv := m.Data.(mapValue)
			exists := mv.findEntry(key) >= 0
			vm.push(NewValueBool(exists))

		case bytecode.OpMAP_LENGTH:
			m := vm.pop()
			if m.Kind != ValueMap {
				return NewValueNull(), vm.errorAt(frame, "E030", "cannot get length of non-map")
			}
			mv := m.Data.(mapValue)
			if mv.entries == nil {
				vm.push(NewValueInt(0))
			} else {
				vm.push(NewValueInt(int64(len(*mv.entries))))
			}

		case bytecode.OpMAP_KEYS:
			m := vm.pop()
			if m.Kind != ValueMap {
				return NewValueNull(), vm.errorAt(frame, "E039", "cannot get keys of non-map")
			}
			mv := m.Data.(mapValue)
			if mv.entries == nil {
				vm.push(NewValueList(nil))
			} else {
				keys := make([]Value, len(*mv.entries))
				for i, e := range *mv.entries {
					keys[i] = e.key
				}
				vm.push(NewValueList(keys))
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
			vm.push(NewValueInt(int64(v)))

		case bytecode.OpCONVERT_INT_TO_FLOAT:
			v := vm.popInt()
			vm.push(NewValueFloat(float64(v)))

		case bytecode.OpTHROW:
			// Pop the exception value
			excVal := vm.pop()
			var msg string
			if excVal.Kind == ValueException {
				msg = excVal.Data.(exceptionValue).message
			} else {
				msg = excVal.String()
			}

			// Look for a handler in the handler stack
			handled := vm.handleException(excVal, frame)
			if !handled {
				// Uncaught exception - report it
				return NewValueNull(), vm.errorAt(frame, "E040",
					fmt.Sprintf("uncaught exception: %s", msg))
			}

		case bytecode.OpSETUP_HANDLER:
			// The instruction is: op(1) + catchOffset(int32, 4) + finallyOffset(int32, 4) + stackDepth(uint16, 2) = 11 bytes.
			// decodeOp only captures 2 operands. We need to decode all 3 operands ourselves.
			// frame.IP has been advanced past 2 operands (to offset+9). The full instruction is 11 bytes.
			// Let's decode from the instruction start.
			insnStart := frame.IP - 9 // back up past 2 decoded operands
			if insnStart < 0 {
				insnStart = 0
			}
			// Read catch offset (operands[0] has this value)
			catchOffset := int32(operands[0])
			// Read finally offset (operands[1] has this value)
			finallyOffset := int32(operands[1])
			// Read stack depth from bytes 9-10 (past 1 opcode + 4 catch + 4 finally)
			stackDepthPos := insnStart + 9
			if stackDepthPos+1 < len(fn.Code) {
				stackDepth := int(binary.BigEndian.Uint16(fn.Code[stackDepthPos:]))

				// The instruction end is at insnStart + 11
				insnEnd := insnStart + 11

				// Compute absolute offsets (catch/finally offsets are relative to instruction end)
				catchTarget := 0
				if catchOffset != 0 {
					catchTarget = insnEnd + int(catchOffset)
				}
				finallyTarget := 0
				if finallyOffset != 0 {
					finallyTarget = insnEnd + int(finallyOffset)
				}

				// Push handler entry
				vm.handlerStack = append(vm.handlerStack, handlerEntry{
					catchOffset:   catchTarget,
					finallyOffset: finallyTarget,
					stackDepth:    stackDepth,
					frameIndex:    len(vm.frames) - 1,
					active:        true,
				})
			}
			// Advance frame.IP past the full instruction
			frame.IP = insnStart + 11

		case bytecode.OpREMOVE_HANDLER:
			// Pop the most recent handler
			if len(vm.handlerStack) > 0 {
				vm.handlerStack = vm.handlerStack[:len(vm.handlerStack)-1]
			}
			// Check for pending exception that needs re-throwing after finally
			if vm.pendingException != nil {
				excVal := *vm.pendingException
				vm.pendingException = nil
				// Propagate the pending exception
				handled := vm.handleException(excVal, frame)
				if !handled {
					return NewValueNull(), vm.errorAt(frame, "E040",
						fmt.Sprintf("uncaught exception: %s", excVal.Data.(exceptionValue).message))
				}
			}

		case bytecode.OpNEW_EXCEPTION:
			// Pop string message, capture trace, push exception value
			msgVal := vm.pop()
			msg := msgVal.String()
			excVal := vm.buildExceptionValue(msg)
			vm.push(excVal)

		case bytecode.OpEXCEPTION_FIELD:
			// Pop exception value, push the requested field
			fieldID := int(operands[0])
			excVal := vm.pop()
			if excVal.Kind != ValueException {
				return NewValueNull(), vm.errorAt(frame, "E041", fmt.Sprintf("expected exception, got %s", excVal.String()))
			}
			ev := excVal.Data.(exceptionValue)
			switch fieldID {
			case 0: // message
				vm.push(NewValueString(ev.message))
			case 1: // trace
				vm.push(NewValueString(ev.trace))
			}

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

func (vm *VM) popInt() int64 {
	return vm.pop().Int()
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
	if vm.limits.MaxCallDepth > 0 && len(vm.frames) >= vm.limits.MaxCallDepth {
		// Limit reached - will be caught by the CALL instruction
		return
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

// handleException looks for a handler that can catch the given exception.
// Returns true if the exception was handled, false if uncaught.
func (vm *VM) handleException(excVal Value, frame *CallFrame) bool {
	// Search the handler stack from innermost to outermost
	for i := len(vm.handlerStack) - 1; i >= 0; i-- {
		h := vm.handlerStack[i]

		// Check if handler belongs to current or outer frame
		if h.frameIndex > len(vm.frames)-1 {
			continue
		}

		// If the handler belongs to an outer frame, we need to unwind frames
		for len(vm.frames) > h.frameIndex+1 {
			vm.popFrame()
		}

		// Update frame pointer since we may have unwound
		currentFrame := &vm.frames[len(vm.frames)-1]

		// Restore stack to the frame base + locals + saved operand depth.
		// The saved stackDepth is the operand stack depth at the try block entry.
		targetDepth := currentFrame.StackBase + currentFrame.LocalCount + h.stackDepth
		if targetDepth < len(vm.stack) {
			vm.stack = vm.stack[:targetDepth]
		}

		if h.active && h.catchOffset > 0 {
			// Has active catch handler - push exception value and jump to catch
			vm.push(excVal)
			currentFrame.IP = h.catchOffset
			// Deactivate this handler since we're entering catch
			// (so future exceptions from catch don't re-enter it, but finally still runs)
			vm.handlerStack[i].active = false
			// Clear any pending exception since we're handling this one
			vm.pendingException = nil
			return true
		}

		if h.finallyOffset > 0 && !h.inFinally {
			// Has finally - execute finally, then re-throw or continue propagating
			// Store pending exception in VM state
			vm.pendingException = &excVal
			// Mark that we're entering the finally block so re-entry is prevented
			vm.handlerStack[i].inFinally = true
			currentFrame.IP = h.finallyOffset
			// Deactivate the catch part so it won't catch again, but the finally
			// will execute because we jump directly to it, not via this function
			vm.handlerStack[i].active = false
			return true // will re-throw after finally via REMOVE_HANDLER
		}

		// Handler has no catch and no finally - skip it (should not normally occur)
	}

	return false // uncaught
}

// buildExceptionValue creates an exception value with a .sol stack trace
// captured from the current call frame stack.
func (vm *VM) buildExceptionValue(msg string) Value {
	var b strings.Builder
	b.WriteString(fmt.Sprintf("exception: %s\n", msg))

	// Walk frames from most recent to oldest to build the trace
	for i := len(vm.frames) - 1; i >= 0; i-- {
		f := vm.frames[i]
		fn := vm.program.Functions[f.FunctionID]
		b.WriteString(fmt.Sprintf("  at %s", fn.Name))
		// Resolve source location from SourceMap if available
		if f.IP >= 0 && f.IP < len(fn.SourceMap) {
			sm := fn.SourceMap[f.IP]
			b.WriteString(fmt.Sprintf(" (%s:%d:%d)", fn.Name, sm.StartLine, sm.StartCol))
		} else if f.IP >= 0 {
			b.WriteString(fmt.Sprintf(" (offset %d)", f.IP))
		}
		b.WriteString("\n")
	}

	return NewValueException(msg, b.String())
}

// throwRuntimeException converts a runtime error into a catchable exception.
// Returns (result, error) - if caught, returns normally with null; if uncaught, returns the error.
func (vm *VM) throwRuntimeException(frame *CallFrame, code, msg string) (Value, error) {
	// Build an exception value with trace
	excVal := vm.buildExceptionValue(msg)
	// Try to find a handler for this exception
	handled := vm.handleException(excVal, frame)
	if handled {
		return NewValueNull(), nil
	}
	return NewValueNull(), vm.errorAt(frame, code, msg)
}

// rethrowPendingException re-throws a pending exception after finally execution.
func (vm *VM) rethrowPendingException(frame *CallFrame) (Value, error) {
	if vm.pendingException != nil {
		excVal := *vm.pendingException
		vm.pendingException = nil
		// Try to handle the re-thrown exception
		if vm.handleException(excVal, frame) {
			return NewValueNull(), nil // handled, continue
		}
		return NewValueNull(), vm.errorAt(frame, "E040",
			fmt.Sprintf("uncaught exception: %s", excVal.Data.(exceptionValue).message))
	}
	return NewValueNull(), nil
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
	// Show most recent call first
	for i := len(err.Stack) - 1; i >= 0; i-- {
		sf := err.Stack[i]
		b.WriteString(fmt.Sprintf("  at %s", sf.Function))
		if sf.Line > 0 {
			b.WriteString(fmt.Sprintf(" at line %d", sf.Line))
		}
		b.WriteString("\n")
	}
	if err.Function != "" {
		b.WriteString(fmt.Sprintf("  at %s", err.Function))
		if err.Line > 0 {
			b.WriteString(fmt.Sprintf(" at line %d", err.Line))
		}
		b.WriteString("\n")
	}
	return b.String()
}

// Standard native functions for initialization
func init() {
	// Register standard natives
	_ = ValueEqual
	_ = ValueHash
}
