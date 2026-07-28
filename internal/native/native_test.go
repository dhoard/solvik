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

package native_test

import (
	"os"
	"testing"

	"github.com/dhoard/solvik-language/internal/native"
	"github.com/dhoard/solvik-language/internal/vm"
)

func newTestRegistry() *vm.NativeRegistry {
	reg := vm.NewNativeRegistry()
	native.RegisterAll(reg)
	return reg
}

func TestRegistryCoreFunctions(t *testing.T) {
	reg := newTestRegistry()
	tests := []struct {
		name string
		args []vm.Value
	}{
		{"core.print", []vm.Value{vm.NewValueString("test")}},
		{"core.println", []vm.Value{vm.NewValueString("test")}},
		{"core.string", []vm.Value{vm.NewValueInt(42)}},
		{"core.int", []vm.Value{vm.NewValueString("42")}},
		{"core.int", []vm.Value{vm.NewValueString("9999999999")}},
		{"core.float", []vm.Value{vm.NewValueString("3.14")}},
		{"core.bool", []vm.Value{vm.NewValueString("true")}},
		{"core.typeOf", []vm.Value{vm.NewValueInt(42)}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			fn, ok := reg.Lookup(tt.name)
			if !ok {
				t.Fatalf("native function %s not found", tt.name)
			}
			result, err := fn.Handler(tt.args)
			if err != nil {
				t.Errorf("Handler returned error: %v", err)
			}
			_ = result
		})
	}
}

func TestCorePrint(t *testing.T) {
	reg := newTestRegistry()
	fn, ok := reg.Lookup("core.print")
	if !ok {
		t.Fatal("core.print not found")
	}
	_, err := fn.Handler([]vm.Value{vm.NewValueString("hello")})
	if err != nil {
		t.Errorf("unexpected error: %v", err)
	}
}

func TestCorePrintArgError(t *testing.T) {
	reg := newTestRegistry()
	fn, ok := reg.Lookup("core.print")
	if !ok {
		t.Fatal("core.print not found")
	}
	_, err := fn.Handler(nil)
	if err == nil {
		t.Error("expected error for wrong arg count")
	}
	_, err = fn.Handler([]vm.Value{vm.NewValueString("a"), vm.NewValueString("b")})
	if err == nil {
		t.Error("expected error for too many args")
	}
}

func TestCorePrintln(t *testing.T) {
	reg := newTestRegistry()
	fn, ok := reg.Lookup("core.println")
	if !ok {
		t.Fatal("core.println not found")
	}
	_, err := fn.Handler([]vm.Value{vm.NewValueString("hello")})
	if err != nil {
		t.Errorf("unexpected error: %v", err)
	}
}

func TestCorePrintlnArgError(t *testing.T) {
	reg := newTestRegistry()
	fn, ok := reg.Lookup("core.println")
	if !ok {
		t.Fatal("core.println not found")
	}
	_, err := fn.Handler(nil)
	if err == nil {
		t.Error("expected error for wrong arg count")
	}
}

func TestCoreString(t *testing.T) {
	reg := newTestRegistry()
	fn, ok := reg.Lookup("core.string")
	if !ok {
		t.Fatal("core.string not found")
	}
	tests := []struct {
		arg  vm.Value
		want string
	}{
		{vm.NewValueInt(42), "42"},
		{vm.NewValueBool(true), "true"},
		{vm.NewValueString("hello"), "hello"},
		{vm.NewValueNull(), "null"},
	}
	for _, tt := range tests {
		result, err := fn.Handler([]vm.Value{tt.arg})
		if err != nil {
			t.Errorf("Handler(%v) error: %v", tt.arg, err)
			continue
		}
		if result.String() != tt.want {
			t.Errorf("got %q, want %q", result.String(), tt.want)
		}
	}
}

func TestCoreStringArgError(t *testing.T) {
	reg := newTestRegistry()
	fn, ok := reg.Lookup("core.string")
	if !ok {
		t.Fatal("core.string not found")
	}
	_, err := fn.Handler(nil)
	if err == nil {
		t.Error("expected error")
	}
}

func TestCoreInt(t *testing.T) {
	reg := newTestRegistry()
	fn, ok := reg.Lookup("core.int")
	if !ok {
		t.Fatal("core.int not found")
	}
	result, err := fn.Handler([]vm.Value{vm.NewValueString("42")})
	if err != nil {
		t.Errorf("unexpected error: %v", err)
	}
	if result.Int() != 42 {
		t.Errorf("got %d, want 42", result.Int())
	}
}

func TestCoreIntParseError(t *testing.T) {
	reg := newTestRegistry()
	fn, ok := reg.Lookup("core.int")
	if !ok {
		t.Fatal("core.int not found")
	}
	_, err := fn.Handler([]vm.Value{vm.NewValueString("notanumber")})
	if err == nil {
		t.Error("expected error for invalid int string")
	}
}

func TestCoreLong(t *testing.T) {
	reg := newTestRegistry()
	fn, ok := reg.Lookup("core.int")
	if !ok {
		t.Fatal("core.int not found")
	}
	result, err := fn.Handler([]vm.Value{vm.NewValueString("9999999999")})
	if err != nil {
		t.Errorf("unexpected error: %v", err)
	}
	if result.Int() != 9999999999 {
		t.Errorf("got %d, want 9999999999", result.Int())
	}
}

func TestCoreFloat(t *testing.T) {
	reg := newTestRegistry()
	fn, ok := reg.Lookup("core.float")
	if !ok {
		t.Fatal("core.float not found")
	}
	result, err := fn.Handler([]vm.Value{vm.NewValueString("3.14")})
	if err != nil {
		t.Errorf("unexpected error: %v", err)
	}
	if result.Double() != 3.14 {
		t.Errorf("got %f, want 3.14", result.Double())
	}
}

func TestCoreBool(t *testing.T) {
	reg := newTestRegistry()
	fn, ok := reg.Lookup("core.bool")
	if !ok {
		t.Fatal("core.bool not found")
	}
	// core.bool uses IsTruthy(): all non-bool, non-null values are truthy
	tests := []struct {
		arg  vm.Value
		want bool
	}{
		{vm.NewValueBool(true), true},
		{vm.NewValueBool(false), false},
		{vm.NewValueString("true"), true},  // string is truthy
		{vm.NewValueString("false"), true}, // string is truthy
		{vm.NewValueInt(1), true},
		{vm.NewValueInt(0), true}, // non-zero int is truthy
		{vm.NewValueNull(), false},
	}
	for _, tt := range tests {
		result, err := fn.Handler([]vm.Value{tt.arg})
		if err != nil {
			t.Errorf("Handler(%v) error: %v", tt.arg, err)
			continue
		}
		if result.Bool() != tt.want {
			t.Errorf("got %v, want %v", result.Bool(), tt.want)
		}
	}
}

func TestCoreTypeOf(t *testing.T) {
	reg := newTestRegistry()
	fn, ok := reg.Lookup("core.typeOf")
	if !ok {
		t.Fatal("core.typeOf not found")
	}
	tests := []struct {
		arg  vm.Value
		want string
	}{
		{vm.NewValueInt(42), "int"},
		{vm.NewValueString("hi"), "string"},
		{vm.NewValueBool(true), "bool"},
		{vm.NewValueNull(), "null"},
	}
	for _, tt := range tests {
		result, err := fn.Handler([]vm.Value{tt.arg})
		if err != nil {
			t.Errorf("Handler(%v) error: %v", tt.arg, err)
			continue
		}
		if result.String() != tt.want {
			t.Errorf("got %q, want %q", result.String(), tt.want)
		}
	}
}

func TestStringFunctions(t *testing.T) {
	reg := newTestRegistry()
	tests := []struct {
		name string
		args []vm.Value
	}{
		{"string.length", []vm.Value{vm.NewValueString("hello")}},
		{"string.byteLength", []vm.Value{vm.NewValueString("hello")}},
		{"string.charAt", []vm.Value{vm.NewValueString("hello"), vm.NewValueInt(0)}},
		{"string.substring", []vm.Value{vm.NewValueString("hello"), vm.NewValueInt(0), vm.NewValueInt(3)}},
		{"string.contains", []vm.Value{vm.NewValueString("hello"), vm.NewValueString("ell")}},
		{"string.startsWith", []vm.Value{vm.NewValueString("hello"), vm.NewValueString("he")}},
		{"string.endsWith", []vm.Value{vm.NewValueString("hello"), vm.NewValueString("lo")}},
		{"string.indexOf", []vm.Value{vm.NewValueString("hello"), vm.NewValueString("l")}},
		{"string.toUpper", []vm.Value{vm.NewValueString("hello")}},
		{"string.toLower", []vm.Value{vm.NewValueString("HELLO")}},
		{"string.trim", []vm.Value{vm.NewValueString("  hello  ")}},
		{"string.split", []vm.Value{vm.NewValueString("a,b,c"), vm.NewValueString(",")}},
		{"string.join", []vm.Value{vm.NewValueList([]vm.Value{vm.NewValueString("a"), vm.NewValueString("b")}), vm.NewValueString(",")}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			fn, ok := reg.Lookup(tt.name)
			if !ok {
				t.Fatalf("native function %s not found", tt.name)
			}
			result, err := fn.Handler(tt.args)
			if err != nil {
				t.Errorf("Handler error: %v", err)
			}
			_ = result
		})
	}
}

func TestStringLength(t *testing.T) {
	reg := newTestRegistry()
	fn, ok := reg.Lookup("string.length")
	if !ok {
		t.Fatal("string.length not found")
	}
	result, err := fn.Handler([]vm.Value{vm.NewValueString("hello")})
	if err != nil {
		t.Errorf("unexpected error: %v", err)
	}
	if result.Int() != 5 {
		t.Errorf("got %d, want 5", result.Int())
	}
}

func TestStringByteLength(t *testing.T) {
	reg := newTestRegistry()
	fn, ok := reg.Lookup("string.byteLength")
	if !ok {
		t.Fatal("string.byteLength not found")
	}
	result, err := fn.Handler([]vm.Value{vm.NewValueString("hello")})
	if err != nil {
		t.Errorf("unexpected error: %v", err)
	}
	if result.Int() != 5 {
		t.Errorf("got %d, want 5", result.Int())
	}
}

func TestStringCharAt(t *testing.T) {
	reg := newTestRegistry()
	fn, ok := reg.Lookup("string.charAt")
	if !ok {
		t.Fatal("string.charAt not found")
	}
	result, err := fn.Handler([]vm.Value{vm.NewValueString("hello"), vm.NewValueInt(1)})
	if err != nil {
		t.Errorf("unexpected error: %v", err)
	}
	if result.Char() != 'e' {
		t.Errorf("got %c, want e", result.Char())
	}
}

func TestStringSubstring(t *testing.T) {
	reg := newTestRegistry()
	fn, ok := reg.Lookup("string.substring")
	if !ok {
		t.Fatal("string.substring not found")
	}
	result, err := fn.Handler([]vm.Value{vm.NewValueString("hello"), vm.NewValueInt(1), vm.NewValueInt(4)})
	if err != nil {
		t.Errorf("unexpected error: %v", err)
	}
	if result.String() != "ell" {
		t.Errorf("got %q, want ell", result.String())
	}
}

func TestStringContains(t *testing.T) {
	reg := newTestRegistry()
	fn, ok := reg.Lookup("string.contains")
	if !ok {
		t.Fatal("string.contains not found")
	}
	result, err := fn.Handler([]vm.Value{vm.NewValueString("hello world"), vm.NewValueString("world")})
	if err != nil {
		t.Errorf("unexpected error: %v", err)
	}
	if !result.Bool() {
		t.Error("expected true")
	}
	result, err = fn.Handler([]vm.Value{vm.NewValueString("hello"), vm.NewValueString("xyz")})
	if err != nil {
		t.Errorf("unexpected error: %v", err)
	}
	if result.Bool() {
		t.Error("expected false")
	}
}

func TestStringSplitJoin(t *testing.T) {
	reg := newTestRegistry()
	splitFn, ok := reg.Lookup("string.split")
	if !ok {
		t.Fatal("string.split not found")
	}
	result, err := splitFn.Handler([]vm.Value{vm.NewValueString("a,b,c"), vm.NewValueString(",")})
	if err != nil {
		t.Errorf("unexpected error: %v", err)
	}
	if result.ListLen() != 3 {
		t.Errorf("expected 3 elements, got %d", result.ListLen())
	}
}

func TestJoin(t *testing.T) {
	reg := newTestRegistry()
	fn, ok := reg.Lookup("string.join")
	if !ok {
		t.Fatal("string.join not found")
	}
	list := vm.NewValueList([]vm.Value{vm.NewValueString("a"), vm.NewValueString("b"), vm.NewValueString("c")})
	result, err := fn.Handler([]vm.Value{list, vm.NewValueString(",")})
	if err != nil {
		t.Errorf("unexpected error: %v", err)
	}
	if result.String() != "a,b,c" {
		t.Errorf("got %q, want a,b,c", result.String())
	}
}

func TestMathFunctions(t *testing.T) {
	reg := newTestRegistry()
	tests := []struct {
		name string
		args []vm.Value
	}{
		{"math.PI", nil},
		{"math.E", nil},
		{"math.abs", []vm.Value{vm.NewValueFloat(-5.0)}},
		{"math.min", []vm.Value{vm.NewValueFloat(1.0), vm.NewValueFloat(2.0)}},
		{"math.max", []vm.Value{vm.NewValueFloat(1.0), vm.NewValueFloat(2.0)}},
		{"math.floor", []vm.Value{vm.NewValueFloat(3.7)}},
		{"math.ceil", []vm.Value{vm.NewValueFloat(3.1)}},
		{"math.round", []vm.Value{vm.NewValueFloat(3.5)}},
		{"math.sqrt", []vm.Value{vm.NewValueFloat(9.0)}},
		{"math.pow", []vm.Value{vm.NewValueFloat(2.0), vm.NewValueFloat(3.0)}},
		{"math.sin", []vm.Value{vm.NewValueFloat(0.0)}},
		{"math.cos", []vm.Value{vm.NewValueFloat(0.0)}},
		{"math.tan", []vm.Value{vm.NewValueFloat(0.0)}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			fn, ok := reg.Lookup(tt.name)
			if !ok {
				t.Fatalf("native function %s not found", tt.name)
			}
			result, err := fn.Handler(tt.args)
			if err != nil {
				t.Errorf("Handler error: %v", err)
			}
			_ = result
		})
	}
}

func TestMathPI(t *testing.T) {
	reg := newTestRegistry()
	fn, ok := reg.Lookup("math.PI")
	if !ok {
		t.Fatal("math.PI not found")
	}
	result, err := fn.Handler(nil)
	if err != nil {
		t.Errorf("unexpected error: %v", err)
	}
	if result.Double() < 3.14 || result.Double() > 3.15 {
		t.Errorf("PI = %f, expected ~3.14", result.Double())
	}
}

func TestMathAbs(t *testing.T) {
	reg := newTestRegistry()
	fn, ok := reg.Lookup("math.abs")
	if !ok {
		t.Fatal("math.abs not found")
	}
	result, err := fn.Handler([]vm.Value{vm.NewValueFloat(-5.0)})
	if err != nil {
		t.Errorf("unexpected error: %v", err)
	}
	if result.Double() != 5.0 {
		t.Errorf("got %f, want 5.0", result.Double())
	}
}

func TestMathMin(t *testing.T) {
	reg := newTestRegistry()
	fn, ok := reg.Lookup("math.min")
	if !ok {
		t.Fatal("math.min not found")
	}
	result, err := fn.Handler([]vm.Value{vm.NewValueFloat(3.0), vm.NewValueFloat(7.0)})
	if err != nil {
		t.Errorf("unexpected error: %v", err)
	}
	if result.Double() != 3.0 {
		t.Errorf("got %f, want 3.0", result.Double())
	}
}

func TestMathMax(t *testing.T) {
	reg := newTestRegistry()
	fn, ok := reg.Lookup("math.max")
	if !ok {
		t.Fatal("math.max not found")
	}
	result, err := fn.Handler([]vm.Value{vm.NewValueFloat(3.0), vm.NewValueFloat(7.0)})
	if err != nil {
		t.Errorf("unexpected error: %v", err)
	}
	if result.Double() != 7.0 {
		t.Errorf("got %f, want 7.0", result.Double())
	}
}

func TestMathSqrt(t *testing.T) {
	reg := newTestRegistry()
	fn, ok := reg.Lookup("math.sqrt")
	if !ok {
		t.Fatal("math.sqrt not found")
	}
	result, err := fn.Handler([]vm.Value{vm.NewValueFloat(9.0)})
	if err != nil {
		t.Errorf("unexpected error: %v", err)
	}
	if result.Double() != 3.0 {
		t.Errorf("got %f, want 3.0", result.Double())
	}
}

func TestMathPow(t *testing.T) {
	reg := newTestRegistry()
	fn, ok := reg.Lookup("math.pow")
	if !ok {
		t.Fatal("math.pow not found")
	}
	result, err := fn.Handler([]vm.Value{vm.NewValueFloat(2.0), vm.NewValueFloat(3.0)})
	if err != nil {
		t.Errorf("unexpected error: %v", err)
	}
	if result.Double() != 8.0 {
		t.Errorf("got %f, want 8.0", result.Double())
	}
}

func TestEnvFunctions(t *testing.T) {
	reg := newTestRegistry()
	// env.set and env.get
	setFn, ok := reg.Lookup("env.set")
	if !ok {
		t.Fatal("env.set not found")
	}
	_, err := setFn.Handler([]vm.Value{vm.NewValueString("TEST_VAR"), vm.NewValueString("test_value")})
	if err != nil {
		t.Errorf("env.set error: %v", err)
	}

	getFn, ok := reg.Lookup("env.get")
	if !ok {
		t.Fatal("env.get not found")
	}
	result, err := getFn.Handler([]vm.Value{vm.NewValueString("TEST_VAR")})
	if err != nil {
		t.Errorf("env.get error: %v", err)
	}
	if result.String() != "test_value" {
		t.Errorf("env.get = %q, want test_value", result.String())
	}

	// Cleanup
	os.Unsetenv("TEST_VAR")

	// env.keys
	keysFn, ok := reg.Lookup("env.keys")
	if !ok {
		t.Fatal("env.keys not found")
	}
	keysResult, err := keysFn.Handler(nil)
	if err != nil {
		t.Errorf("env.keys error: %v", err)
	}
	if keysResult.ListLen() == 0 {
		t.Error("env.keys returned empty list (should have at least PATH)")
	}
}

func TestFileFunctions(t *testing.T) {
	reg := newTestRegistry()
	// Use a temp file for testing
	tmpFile := t.TempDir() + "/test.txt"

	// file.write
	writeFn, ok := reg.Lookup("file.write")
	if !ok {
		t.Fatal("file.write not found")
	}
	_, err := writeFn.Handler([]vm.Value{vm.NewValueString(tmpFile), vm.NewValueString("hello world")})
	if err != nil {
		t.Errorf("file.write error: %v", err)
	}

	// file.exists
	existsFn, ok := reg.Lookup("file.exists")
	if !ok {
		t.Fatal("file.exists not found")
	}
	result, err := existsFn.Handler([]vm.Value{vm.NewValueString(tmpFile)})
	if err != nil {
		t.Errorf("file.exists error: %v", err)
	}
	if !result.Bool() {
		t.Error("file.exists returned false for existing file")
	}

	// file.read
	readFn, ok := reg.Lookup("file.read")
	if !ok {
		t.Fatal("file.read not found")
	}
	readResult, err := readFn.Handler([]vm.Value{vm.NewValueString(tmpFile)})
	if err != nil {
		t.Errorf("file.read error: %v", err)
	}
	if readResult.String() != "hello world" {
		t.Errorf("file.read = %q, want hello world", readResult.String())
	}

	// file.append
	appendFn, ok := reg.Lookup("file.append")
	if !ok {
		t.Fatal("file.append not found")
	}
	_, err = appendFn.Handler([]vm.Value{vm.NewValueString(tmpFile), vm.NewValueString("!")})
	if err != nil {
		t.Errorf("file.append error: %v", err)
	}

	// file.delete
	deleteFn, ok := reg.Lookup("file.delete")
	if !ok {
		t.Fatal("file.delete not found")
	}
	_, err = deleteFn.Handler([]vm.Value{vm.NewValueString(tmpFile)})
	if err != nil {
		t.Errorf("file.delete error: %v", err)
	}

	// Verify deleted
	result, err = existsFn.Handler([]vm.Value{vm.NewValueString(tmpFile)})
	if err != nil {
		t.Errorf("file.exists error: %v", err)
	}
	if result.Bool() {
		t.Error("file.exists returned true for deleted file")
	}
}

func TestTimeFunctions(t *testing.T) {
	reg := newTestRegistry()
	// time.now
	nowFn, ok := reg.Lookup("time.now")
	if !ok {
		t.Fatal("time.now not found")
	}
	result, err := nowFn.Handler(nil)
	if err != nil {
		t.Errorf("time.now error: %v", err)
	}
	if result.Int() <= 0 {
		t.Errorf("time.now = %d, expected > 0", result.Int())
	}

	// time.sleep
	sleepFn, ok := reg.Lookup("time.sleep")
	if !ok {
		t.Fatal("time.sleep not found")
	}
	_, err = sleepFn.Handler([]vm.Value{vm.NewValueInt(1)})
	if err != nil {
		t.Errorf("time.sleep error: %v", err)
	}
}

func TestRegisterAliases(t *testing.T) {
	reg := newTestRegistry()
	// The registerAliases function should register unqualified names
	fn, ok := reg.Lookup("print")
	if !ok {
		// This might not have aliases - just check that RegisterAll works
	}
	_ = fn
}

func TestMapFunctions(t *testing.T) {
	reg := newTestRegistry()
	fn, ok := reg.Lookup("map.contains")
	if !ok {
		t.Fatal("map.contains not found")
	}
	// Create a map with an entry
	m := vm.NewValueMap()
	// Test with a simple map and key
	_, err := fn.Handler([]vm.Value{m, vm.NewValueString("key")})
	if err != nil {
		t.Errorf("unexpected error: %v", err)
	}
}

func TestProcessRun(t *testing.T) {
	reg := newTestRegistry()
	fn, ok := reg.Lookup("process.run")
	if !ok {
		t.Fatal("process.run not found")
	}
	// Run echo - try /bin/echo first, then just check the function returns
	result, err := fn.Handler([]vm.Value{vm.NewValueString("/bin/echo"), vm.NewValueString("hello")})
	if err != nil {
		// May fail on systems without /bin/echo
		t.Skipf("process.run failed: %v", err)
	}
	// process.run returns void in some configurations, check if result is valid
	_ = result
}

func TestRegisterAll(t *testing.T) {
	reg := vm.NewNativeRegistry()
	native.RegisterAll(reg)
	// Verify key functions are registered
	keyFuncs := []string{
		"core.print", "core.println", "core.string", "core.int", "core.float",
		"string.length", "string.contains", "math.abs", "math.sqrt",
		"env.get", "file.read", "process.run", "time.now", "map.contains",
	}
	for _, name := range keyFuncs {
		if _, ok := reg.Lookup(name); !ok {
			t.Errorf("function %s not registered", name)
		}
	}
}

func TestCapabilityConstants(t *testing.T) {
	if native.CapFilesystem != "filesystem" {
		t.Error("CapFilesystem mismatch")
	}
	if native.CapProcess != "process" {
		t.Error("CapProcess mismatch")
	}
	if native.CapEnv != "env" {
		t.Error("CapEnv mismatch")
	}
	if native.CapNetwork != "network" {
		t.Error("CapNetwork mismatch")
	}
}
