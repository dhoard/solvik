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

package types_test

import (
	"testing"

	"github.com/dhoard/solvik-language/internal/types"
)

func TestPredefinedTypes(t *testing.T) {
	tests := []struct {
		name string
		typ  *types.Type
		kind types.Kind
	}{
		{"Void", types.Void, types.KindVoid},
		{"Bool", types.Bool, types.KindBool},
		{"Byte", types.Byte, types.KindByte},
		{"Int", types.Int, types.KindInt},
		{"Long", types.Long, types.KindLong},
		{"Float", types.Float, types.KindFloat},
		{"Double", types.Double, types.KindDouble},
		{"Char", types.Char, types.KindChar},
		{"String", types.String, types.KindString},
		{"Exception", types.Exception, types.KindException},
		{"Invalid", types.Invalid, types.KindInvalid},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if tt.typ.Kind != tt.kind {
				t.Errorf("kind = %v, want %v", tt.typ.Kind, tt.kind)
			}
			if tt.typ.Nullable {
				t.Error("predefined type should not be nullable")
			}
		})
	}
}

func TestNamed(t *testing.T) {
	tests := []struct {
		name string
		typ  *types.Type
		want string
	}{
		{"void", types.Void, "void"},
		{"bool", types.Bool, "bool"},
		{"int", types.Int, "int"},
		{"string", types.String, "string"},
		{"exception", types.Exception, "exception"},
		{"nil_type", nil, "<nil>"},
		{"invalid", types.Invalid, "<invalid>"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := tt.typ.Named(); got != tt.want {
				t.Errorf("Named() = %q, want %q", got, tt.want)
			}
		})
	}
}

func TestNamedNullable(t *testing.T) {
	nt := types.NullableOf(types.Int)
	if got := nt.Named(); got != "int?" {
		t.Errorf("Named() = %q, want %q", got, "int?")
	}
	nt2 := types.NullableOf(types.String)
	if got := nt2.Named(); got != "string?" {
		t.Errorf("Named() = %q, want %q", got, "string?")
	}
}

func TestNamedList(t *testing.T) {
	lt := types.ListOf(types.Int)
	if got := lt.Named(); got != "List<int>" {
		t.Errorf("Named() = %q, want %q", got, "List<int>")
	}
	// Nested list
	nlt := types.ListOf(types.ListOf(types.String))
	if got := nlt.Named(); got != "List<List<string>>" {
		t.Errorf("Named() = %q, want %q", got, "List<List<string>>")
	}
}

func TestNamedMap(t *testing.T) {
	mt := types.MapOf(types.String, types.Int)
	if got := mt.Named(); got != "Map<string, int>" {
		t.Errorf("Named() = %q, want %q", got, "Map<string, int>")
	}
}

func TestNamedFunction(t *testing.T) {
	ft := types.FunctionType([]*types.Type{types.Int, types.String}, types.Bool)
	if got := ft.Named(); got != "(int, string) -> bool" {
		t.Errorf("Named() = %q, want %q", got, "(int, string) -> bool")
	}
	// Void return
	ft2 := types.FunctionType([]*types.Type{types.String}, nil)
	if got := ft2.Named(); got != "(string) -> void" {
		t.Errorf("Named() = %q, want %q", got, "(string) -> void")
	}
}

func TestEquals(t *testing.T) {
	tests := []struct {
		name string
		a, b *types.Type
		want bool
	}{
		{"same_ptr", types.Int, types.Int, true},
		{"nil_nil", nil, nil, true},
		{"nil_nonil", nil, types.Int, false},
		{"different_kind", types.Int, types.String, false},
		{"nullable_vs_non", types.NullableOf(types.Int), types.Int, false},
		{"list_same", types.ListOf(types.Int), types.ListOf(types.Int), true},
		{"list_diff", types.ListOf(types.Int), types.ListOf(types.String), false},
		{"map_same", types.MapOf(types.String, types.Int), types.MapOf(types.String, types.Int), true},
		{"map_diff_key", types.MapOf(types.String, types.Int), types.MapOf(types.Int, types.Int), false},
		{"map_diff_val", types.MapOf(types.String, types.Int), types.MapOf(types.String, types.String), false},
		{"func_same", types.FunctionType([]*types.Type{types.Int}, types.Void), types.FunctionType([]*types.Type{types.Int}, types.Void), true},
		{"func_diff_param", types.FunctionType([]*types.Type{types.Int}, types.Void), types.FunctionType([]*types.Type{types.String}, types.Void), false},
		{"func_diff_ret", types.FunctionType([]*types.Type{types.Int}, types.Void), types.FunctionType([]*types.Type{types.Int}, types.Int), false},
		{"func_diff_count", types.FunctionType([]*types.Type{types.Int}, types.Void), types.FunctionType([]*types.Type{types.Int, types.Int}, types.Void), false},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := tt.a.Equals(tt.b); got != tt.want {
				t.Errorf("Equals() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestEqualsStrict(t *testing.T) {
	a := types.Int
	b := types.NullableOf(types.Int)
	if a.EqualsStrict(b) {
		t.Error("EqualsStrict should distinguish nullable vs non-nullable")
	}
	if !a.EqualsStrict(a) {
		t.Error("EqualsStrict should be reflexive")
	}
}

func TestListOf(t *testing.T) {
	lt := types.ListOf(types.Int)
	if lt.Kind != types.KindList {
		t.Errorf("expected KindList, got %v", lt.Kind)
	}
	if lt.Element != types.Int {
		t.Error("element type mismatch")
	}
}

func TestMapOf(t *testing.T) {
	mt := types.MapOf(types.String, types.Int)
	if mt.Kind != types.KindMap {
		t.Errorf("expected KindMap, got %v", mt.Kind)
	}
	if mt.KeyType != types.String || mt.ValueType != types.Int {
		t.Error("key/value type mismatch")
	}
}

func TestNullableOf(t *testing.T) {
	nt := types.NullableOf(types.Int)
	if nt == types.Int {
		t.Error("NullableOf should return a new type")
	}
	if !nt.Nullable {
		t.Error("expected nullable")
	}
	if nt.Kind != types.KindInt {
		t.Error("kind should be preserved")
	}
	// Nil input
	if types.NullableOf(nil) != nil {
		t.Error("NullableOf(nil) should return nil")
	}
}

func TestIsNullable(t *testing.T) {
	if types.Int.IsNullable() {
		t.Error("Int should not be nullable")
	}
	if !types.NullableOf(types.Int).IsNullable() {
		t.Error("nullable Int should be nullable")
	}
	if (*types.Type)(nil).IsNullable() {
		t.Error("nil type should not be nullable")
	}
}

func TestIsReferenceType(t *testing.T) {
	tests := []struct {
		name string
		typ  *types.Type
		want bool
	}{
		{"string", types.String, true},
		{"list", types.ListOf(types.Int), true},
		{"map", types.MapOf(types.String, types.Int), true},
		{"exception", types.Exception, true},
		{"int", types.Int, false},
		{"bool", types.Bool, false},
		{"nil", nil, false},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := tt.typ.IsReferenceType(); got != tt.want {
				t.Errorf("IsReferenceType() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestIsPrimitive(t *testing.T) {
	tests := []struct {
		name string
		typ  *types.Type
		want bool
	}{
		{"bool", types.Bool, true},
		{"byte", types.Byte, true},
		{"int", types.Int, true},
		{"long", types.Long, true},
		{"float", types.Float, true},
		{"double", types.Double, true},
		{"char", types.Char, true},
		{"string", types.String, false},
		{"nullable_int", types.NullableOf(types.Int), false},
		{"nil", nil, false},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := tt.typ.IsPrimitive(); got != tt.want {
				t.Errorf("IsPrimitive() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestIsInteger(t *testing.T) {
	if !types.Int.IsInteger() {
		t.Error("Int should be integer")
	}
	if !types.Byte.IsInteger() {
		t.Error("Byte should be integer")
	}
	if !types.Long.IsInteger() {
		t.Error("Long should be integer")
	}
	if types.String.IsInteger() {
		t.Error("String should not be integer")
	}
	if (*types.Type)(nil).IsInteger() {
		t.Error("nil should not be integer")
	}
}

func TestIsNumeric(t *testing.T) {
	for _, typ := range []*types.Type{types.Byte, types.Int, types.Long, types.Float, types.Double} {
		if !typ.IsNumeric() {
			t.Errorf("%s should be numeric", typ.Named())
		}
	}
	if types.String.IsNumeric() {
		t.Error("String should not be numeric")
	}
	if types.Bool.IsNumeric() {
		t.Error("Bool should not be numeric")
	}
}

func TestIsBool(t *testing.T) {
	if !types.Bool.IsBool() {
		t.Error("Bool.IsBool() should be true")
	}
	if types.Int.IsBool() {
		t.Error("Int.IsBool() should be false")
	}
}

func TestIsString(t *testing.T) {
	if !types.String.IsString() {
		t.Error("String.IsString() should be true")
	}
	if types.Int.IsString() {
		t.Error("Int.IsString() should be false")
	}
}

func TestIsException(t *testing.T) {
	if !types.Exception.IsException() {
		t.Error("Exception.IsException() should be true")
	}
	if types.Int.IsException() {
		t.Error("Int.IsException() should be false")
	}
}

func TestIsVoid(t *testing.T) {
	if !types.Void.IsVoid() {
		t.Error("Void.IsVoid() should be true")
	}
	if types.Int.IsVoid() {
		t.Error("Int.IsVoid() should be false")
	}
}

func TestIsValid(t *testing.T) {
	if !types.Int.IsValid() {
		t.Error("Int should be valid")
	}
	if types.Invalid.IsValid() {
		t.Error("Invalid should not be valid")
	}
	if (*types.Type)(nil).IsValid() {
		t.Error("nil should not be valid")
	}
}

func TestIsNull(t *testing.T) {
	if !types.Invalid.IsNull() {
		t.Error("Invalid should be null")
	}
	if types.Int.IsNull() {
		t.Error("Int should not be null")
	}
}

func TestIsValidMapKey(t *testing.T) {
	tests := []struct {
		name string
		typ  *types.Type
		want bool
	}{
		{"bool", types.Bool, true},
		{"byte", types.Byte, true},
		{"int", types.Int, true},
		{"long", types.Long, true},
		{"char", types.Char, true},
		{"string", types.String, true},
		{"float", types.Float, false},
		{"double", types.Double, false},
		{"list", types.ListOf(types.Int), false},
		{"map", types.MapOf(types.String, types.Int), false},
		{"nullable_int", types.NullableOf(types.Int), false},
		{"nil", nil, false},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := tt.typ.IsValidMapKey(); got != tt.want {
				t.Errorf("IsValidMapKey() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestIsAssignableFrom(t *testing.T) {
	tests := []struct {
		name string
		dst  *types.Type
		src  *types.Type
		want bool
	}{
		{"same_type", types.Int, types.Int, true},
		{"nil_to_nullable", types.NullableOf(types.Int), types.Invalid, true},
		{"byte_to_int", types.Int, types.Byte, true},
		{"byte_to_long", types.Long, types.Byte, true},
		{"int_to_long", types.Long, types.Int, true},
		{"float_to_double", types.Double, types.Float, true},
		{"string_to_exception", types.Exception, types.String, true},
		{"int_to_string", types.String, types.Int, false},
		{"string_to_int", types.Int, types.String, false},
		{"bool_to_int", types.Int, types.Bool, false},
		{"nil_types", nil, nil, false},
		{"list_compat", types.ListOf(types.Int), types.ListOf(types.Int), true},
		{"list_incompat", types.ListOf(types.Int), types.ListOf(types.String), false},
		{"nullable_to_nullable", types.NullableOf(types.String), types.NullableOf(types.String), true},
		{"nonnull_to_nullable", types.NullableOf(types.String), types.String, true},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := tt.dst.IsAssignableFrom(tt.src); got != tt.want {
				t.Errorf("IsAssignableFrom() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestIsCoercibleNumeric(t *testing.T) {
	tests := []struct {
		name string
		to   *types.Type
		from *types.Type
		want bool
	}{
		{"byte_to_int", types.Int, types.Byte, true},
		{"int_to_long", types.Long, types.Int, true},
		{"float_to_double", types.Double, types.Float, true},
		{"int_to_byte", types.Byte, types.Int, false},
		{"double_to_float", types.Float, types.Double, false},
		{"string_to_int", types.Int, types.String, false},
		{"nil_nil", nil, nil, false},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := types.IsCoercibleNumeric(tt.to, tt.from); got != tt.want {
				t.Errorf("IsCoercibleNumeric() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestCommonNumericType(t *testing.T) {
	tests := []struct {
		name string
		a, b *types.Type
		want types.Kind
	}{
		{"int_int", types.Int, types.Int, types.KindInt},
		{"int_long", types.Int, types.Long, types.KindLong},
		{"long_int", types.Long, types.Int, types.KindLong},
		{"float_double", types.Float, types.Double, types.KindDouble},
		{"byte_int", types.Byte, types.Int, types.KindInt},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := types.CommonNumericType(tt.a, tt.b)
			if got.Kind != tt.want {
				t.Errorf("CommonNumericType() = %v, want %v", got.Kind, tt.want)
			}
		})
	}
}

func TestSizeInBytes(t *testing.T) {
	tests := []struct {
		name string
		typ  *types.Type
		want int
	}{
		{"bool", types.Bool, 1},
		{"byte", types.Byte, 1},
		{"int", types.Int, 4},
		{"long", types.Long, 8},
		{"float", types.Float, 4},
		{"double", types.Double, 8},
		{"char", types.Char, 4},
		{"string", types.String, 0},
		{"nil", nil, 0},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := tt.typ.SizeInBytes(); got != tt.want {
				t.Errorf("SizeInBytes() = %d, want %d", got, tt.want)
			}
		})
	}
}

func TestFunctionType(t *testing.T) {
	ft := types.FunctionType([]*types.Type{types.Int, types.String}, types.Bool)
	if ft.Kind != types.KindFunction {
		t.Errorf("expected KindFunction, got %v", ft.Kind)
	}
	if len(ft.Params) != 2 || ft.Params[0] != types.Int || ft.Params[1] != types.String {
		t.Error("params mismatch")
	}
	if ft.Return != types.Bool {
		t.Error("return type mismatch")
	}
}

func TestTypeOrPanic(t *testing.T) {
	types.TypeOrPanic(types.Int, "should not panic")
	defer func() {
		if r := recover(); r == nil {
			t.Error("expected panic for invalid type")
		}
	}()
	types.TypeOrPanic(types.Invalid, "panic expected")
}

func TestWithoutNullable(t *testing.T) {
	nt := types.NullableOf(types.Int)
	nn := nt.WithoutNullable()
	if nn.Nullable {
		t.Error("expected non-nullable")
	}
	if nn.Kind != types.KindInt {
		t.Error("kind should be preserved")
	}
	if (*types.Type)(nil).WithoutNullable() != nil {
		t.Error("nil.WithoutNullable() should return nil")
	}
}

func TestString(t *testing.T) {
	if types.Int.String() != "int" {
		t.Errorf("String() = %q, want %q", types.Int.String(), "int")
	}
}

func TestIsNumericWideningTo(t *testing.T) {
	if !types.Byte.IsNumericWideningTo(types.Int) {
		t.Error("byte should widen to int")
	}
	if !types.Int.IsNumericWideningTo(types.Long) {
		t.Error("int should widen to long")
	}
	if types.Int.IsNumericWideningTo(types.Byte) {
		t.Error("int should not widen to byte")
	}
	if (*types.Type)(nil).IsNumericWideningTo(types.Int) {
		t.Error("nil should not widen")
	}
}

func TestInitPanicsOnNil(t *testing.T) {
	// The init function validates predefined types are not nil.
	// This is tested at package init time - if it panics, tests fail.
	// Just verify the types are accessible.
	_ = types.Void
	_ = types.Bool
	_ = types.Int
}
