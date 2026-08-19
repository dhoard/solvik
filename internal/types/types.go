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

// Package types defines the type system for the language.
package types

// Kind classifies a type.
type Kind uint8

const (
	KindInvalid Kind = iota
	KindVoid
	KindBool
	KindByte
	KindInt
	KindFloat
	KindChar
	KindString
	KindList
	KindMap
	KindStack
	KindFunction
	KindModule
	KindException
	KindEnum
	KindStruct
	KindTrait
)

// Type represents a resolved type in the type system.
type Type struct {
	Kind      Kind
	Element   *Type // for List<T>, element type
	KeyType   *Type // for Map<K,V>, key type
	ValueType *Type // for Map<K,V>, value type
	Nullable  bool  // true if the type is nullable (T?)
	Params    []*Type
	Return    *Type
	Variadic  bool // true if the last parameter is variadic (...T)
	// Enum fields (only used when Kind == KindEnum)
	EnumName    string           // e.g. "Color"
	EnumVariant string           // e.g. "Red" when referencing a specific variant; empty for the base enum type
	EnumValues  map[string]int64 // variant name -> integer value (set on base enum type only)
	// Struct fields (only used when Kind == KindStruct)
	StructName    string                       // e.g. "Point"
	StructFields  []StructFieldInfo            // field definitions in declaration order
	StructMethods map[string]*StructMethodInfo // method name -> method info
	// Trait fields (only used when Kind == KindTrait)
	TraitName    string                      // e.g. "Drawable"
	TraitMethods map[string]*TraitMethodInfo // method name -> method info
}

// Predefined type singletons.
var (
	Void      = &Type{Kind: KindVoid}
	Bool      = &Type{Kind: KindBool}
	Byte      = &Type{Kind: KindByte}
	Int       = &Type{Kind: KindInt}
	Float     = &Type{Kind: KindFloat}
	Char      = &Type{Kind: KindChar}
	String    = &Type{Kind: KindString}
	Exception = &Type{Kind: KindException}
)

// Named returns a human-readable name for the type.
func (t *Type) Named() string {
	if t == nil {
		return "<nil>"
	}
	base := t.baseName()
	if t.Nullable {
		return base + "?"
	}
	return base
}

func (t *Type) baseName() string {
	switch t.Kind {
	case KindInvalid:
		return "<invalid>"
	case KindVoid:
		return "void"
	case KindBool:
		return "bool"
	case KindByte:
		return "byte"
	case KindInt:
		return "int"
	case KindFloat:
		return "float"
	case KindChar:
		return "char"
	case KindString:
		return "string"
	case KindException:
		return "exception"
	case KindEnum:
		if t.EnumVariant != "" {
			return t.EnumName + "." + t.EnumVariant
		}
		return t.EnumName
	case KindStruct:
		return t.StructName
	case KindTrait:
		return t.TraitName
	case KindList:
		if t.Element != nil {
			return "list<" + t.Element.Named() + ">"
		}
		return "list"
	case KindStack:
		if t.Element != nil {
			return "stack<" + t.Element.Named() + ">"
		}
		return "stack"
	case KindMap:
		if t.KeyType != nil && t.ValueType != nil {
			return "map<" + t.KeyType.Named() + ", " + t.ValueType.Named() + ">"
		}
		return "map"
	case KindFunction:
		return t.functionName()
	case KindModule:
		return "module"
	case KindAny:
		return "any"
	default:
		return "<unknown>"
	}
}

func (t *Type) functionName() string {
	s := "("
	for i, p := range t.Params {
		if i > 0 {
			s += ", "
		}
		if t.Variadic && i == len(t.Params)-1 {
			s += "..."
		}
		s += p.Named()
	}
	s += ") -> "
	if t.Return != nil {
		s += t.Return.Named()
	} else {
		s += "void"
	}
	return s
}

// Equals checks structural type equality.
func (t *Type) Equals(other *Type) bool {
	if t == nil || other == nil {
		return t == other
	}
	if t == other {
		return true
	}
	if t.Kind != other.Kind || t.Nullable != other.Nullable {
		return false
	}
	switch t.Kind {
	case KindList, KindStack:
		return typesEqual(t.Element, other.Element)
	case KindMap:
		return typesEqual(t.KeyType, other.KeyType) && typesEqual(t.ValueType, other.ValueType)
	case KindFunction:
		if !typesEqual(t.Return, other.Return) || len(t.Params) != len(other.Params) || t.Variadic != other.Variadic {
			return false
		}
		for i := range t.Params {
			if !typesEqual(t.Params[i], other.Params[i]) {
				return false
			}
		}
		return true
	case KindEnum:
		return t.EnumName == other.EnumName && t.EnumVariant == other.EnumVariant
	case KindStruct:
		return t.StructName == other.StructName
	case KindTrait:
		return t.TraitName == other.TraitName
	default:
		return true
	}
}

func typesEqual(a, b *Type) bool {
	if a == nil || b == nil {
		return a == b
	}
	return a.Equals(b)
}

// ListOf creates a List<T> type.
func ListOf(element *Type) *Type {
	return &Type{Kind: KindList, Element: element}
}

// MapOf creates a Map<K,V> type.
func MapOf(key, value *Type) *Type {
	return &Type{Kind: KindMap, KeyType: key, ValueType: value}
}

// StackOf creates a Stack<T> type.
func StackOf(element *Type) *Type {
	return &Type{Kind: KindStack, Element: element}
}

// NullableOf creates a nullable version of a type.
func NullableOf(t *Type) *Type {
	if t == nil {
		return nil
	}
	cp := *t
	cp.Nullable = true
	return &cp
}

// IsNullable returns true if the type is nullable.
func (t *Type) IsNullable() bool {
	return t != nil && t.Nullable
}

// IsReferenceType returns true if the type is a reference/managed type.
func (t *Type) IsReferenceType() bool {
	if t == nil {
		return false
	}
	switch t.Kind {
	case KindString, KindList, KindMap, KindStack, KindException, KindStruct, KindTrait:
		return true
	}
	return false
}

// IsPrimitive returns true if the type is a primitive value type.
func (t *Type) IsPrimitive() bool {
	if t == nil || t.Nullable {
		return false
	}
	switch t.Kind {
	case KindBool, KindByte, KindInt, KindFloat, KindChar:
		return true
	}
	return false
}

// IsInteger returns true if the type is an integer type.
func (t *Type) IsInteger() bool {
	if t == nil {
		return false
	}
	switch t.Kind {
	case KindByte, KindInt:
		return true
	}
	return false
}

// IsNumeric returns true if the type is a numeric type.
func (t *Type) IsNumeric() bool {
	if t == nil {
		return false
	}
	switch t.Kind {
	case KindByte, KindInt, KindFloat:
		return true
	}
	return false
}

// IsBool returns true if the type is bool.
func (t *Type) IsBool() bool {
	return t != nil && t.Kind == KindBool
}

// IsString returns true if the type is string.
func (t *Type) IsString() bool {
	return t != nil && t.Kind == KindString
}

// IsChar returns true if the type is char.
func (t *Type) IsChar() bool {
	return t != nil && t.Kind == KindChar
}

// IsException returns true if the type is exception.
func (t *Type) IsException() bool {
	return t != nil && t.Kind == KindException
}

// IsVoid returns true if the type is void.
func (t *Type) IsVoid() bool {
	return t != nil && t.Kind == KindVoid
}

// IsValidMapKey returns true if the type can be used as a map key.
func (t *Type) IsValidMapKey() bool {
	if t == nil || t.Nullable {
		return false
	}
	switch t.Kind {
	case KindBool, KindByte, KindInt, KindChar, KindString, KindEnum:
		return true
	}
	return false
}

// IsAssignableFrom checks if a value of srcType can be assigned to this type.
// This handles widening conversions and nullable relationships.
func (t *Type) IsAssignableFrom(srcType *Type) bool {
	if t == nil || srcType == nil {
		return false
	}

	// Any (the "any non-null" sentinel) is assignable to and from any type
	if t.Kind == KindAny || srcType.Kind == KindAny {
		return true
	}

	// Null can be assigned to nullable types
	if srcType.Kind == KindInvalid && t.Nullable {
		return true
	}

	// Exact match
	if t.Equals(srcType) {
		return true
	}

	// Trait satisfaction: a struct can be assigned to a trait type
	if t.Kind == KindTrait && srcType.Kind == KindStruct {
		return StructSatisfiesTrait(srcType, t)
	}

	// Trait-to-trait assignment (same trait)
	if t.Kind == KindTrait && srcType.Kind == KindTrait {
		return t.TraitName == srcType.TraitName
	}

	// Nullable assignment: T? can accept T (non-nullable to nullable is fine)
	if t.Nullable && !srcType.Nullable {
		if t.Kind == srcType.Kind {
			switch t.Kind {
			case KindString, KindList, KindMap, KindStack:
				return typesEqual(t.Element, srcType.Element) &&
					typesEqual(t.KeyType, srcType.KeyType) &&
					typesEqual(t.ValueType, srcType.ValueType)
			case KindStruct:
				return t.StructName == srcType.StructName
			case KindTrait:
				return t.TraitName == srcType.TraitName
			case KindInt, KindFloat, KindByte, KindBool, KindChar:
				return true
			}
		}
	}

	// Nullable trait assignment: Drawable? can accept a struct that satisfies Drawable
	if t.Nullable && !srcType.Nullable && t.Kind == KindTrait && srcType.Kind == KindStruct {
		return StructSatisfiesTrait(srcType, t)
	}

	// Numeric widening: byte -> int -> float
	if !t.Nullable && !srcType.Nullable {
		if t.Kind == KindInt && srcType.Kind == KindByte {
			return true
		}
		if t.Kind == KindFloat && (srcType.Kind == KindByte || srcType.Kind == KindInt) {
			return true
		}
	}

	// Numeric widening into a nullable target: float? accepts int/byte, and
	// int? accepts byte.
	if t.Nullable && !srcType.Nullable {
		if t.Kind == KindInt && srcType.Kind == KindByte {
			return true
		}
		if t.Kind == KindFloat && (srcType.Kind == KindByte || srcType.Kind == KindInt) {
			return true
		}
	}

	// String can be assigned to exception (auto-conversion with trace capture)
	if t.Kind == KindException && srcType.Kind == KindString && !srcType.Nullable {
		return true
	}

	// Enum assignments:
	// 1. A specific variant (Color.Red) can be assigned to the base enum type (Color)
	if t.Kind == KindEnum && srcType.Kind == KindEnum {
		if t.EnumName == srcType.EnumName && t.EnumVariant == "" {
			// Assigning Color.Red to Color
			return true
		}
	}
	return false
}

// IsCoercibleNumeric checks if a numeric type can be implicitly coerced.
func IsCoercibleNumeric(to, from *Type) bool {
	if to == nil || from == nil {
		return false
	}
	if to.Nullable || from.Nullable {
		return false
	}
	// byte -> int -> float
	if to.IsInteger() && from.IsInteger() {
		return from.IsNumericWideningTo(to)
	}
	// int -> float
	if to.Kind == KindFloat && from.Kind == KindInt {
		return true
	}
	// byte -> float
	if to.Kind == KindFloat && from.Kind == KindByte {
		return true
	}
	return false
}

// IsNumericWideningTo checks if this type can be widened to target.
func (t *Type) IsNumericWideningTo(target *Type) bool {
	if t == nil || target == nil {
		return false
	}
	rank := func(k Kind) int {
		switch k {
		case KindByte:
			return 0
		case KindInt:
			return 1
		case KindFloat:
			return 2
		default:
			return -1
		}
	}
	return rank(t.Kind) < rank(target.Kind)
}

// CommonNumericType returns the common numeric type for binary operations.
func CommonNumericType(a, b *Type) *Type {
	rank := func(k Kind) int {
		switch k {
		case KindByte:
			return 0
		case KindInt:
			return 1
		case KindFloat:
			return 2
		default:
			return -1
		}
	}
	if rank(a.Kind) >= rank(b.Kind) {
		return a
	}
	return b
}

// SizeInBytes returns the byte size of a primitive type (0 for non-primitives).
func (t *Type) SizeInBytes() int {
	if t == nil {
		return 0
	}
	switch t.Kind {
	case KindBool, KindByte:
		return 1
	case KindInt, KindEnum:
		return 8
	case KindFloat:
		return 8
	case KindChar:
		return 4
	default:
		return 0
	}
}

// String returns the string representation.
func (t *Type) String() string {
	return t.Named()
}

// VerifyType creates an invalid type for error recovery.
var Invalid = &Type{Kind: KindInvalid}

// Any is a sentinel type meaning "any non-null value". Used for generic
// native function return types (e.g., random.choice) where the concrete
// type depends on the argument. Unlike Invalid, Any is not treated as null.
var Any = &Type{Kind: KindAny}

// KindAny is a type kind representing "any non-null value".
// It is compatible with every type for assignment purposes and does not
// trigger null-related diagnostics.
const KindAny Kind = KindTrait + 1

// IsValid returns true if the type is not the invalid sentinel.
func (t *Type) IsValid() bool {
	return t != nil && t.Kind != KindInvalid
}

// IsNull returns true if the type represents null.
func (t *Type) IsNull() bool {
	return t != nil && t.Kind == KindInvalid
}

// IsAny returns true if the type is the "any non-null value" sentinel.
func (t *Type) IsAny() bool {
	return t != nil && t.Kind == KindAny
}

// StructFieldInfo describes a single field in a struct type.
type StructFieldInfo struct {
	Name  string
	Type  *Type
	IsMut bool
	IsPub bool
}

// StructMethodInfo describes a method on a struct type.
type StructMethodInfo struct {
	FuncIndex int   // index in program function list
	Signature *Type // function type signature
	IsPub     bool
	IsMut     bool // true when the method may mutate its receiver
}

// TraitMethodInfo describes a method signature in a trait.
type TraitMethodInfo struct {
	Signature *Type // function type signature (without _self)
	IsPub     bool  // always true for trait methods
	IsMut     bool  // true when implementations may mutate their receiver
}

// StructType creates a struct type with the given name and fields.
func StructType(name string, fields []StructFieldInfo) *Type {
	return &Type{
		Kind:          KindStruct,
		StructName:    name,
		StructFields:  fields,
		StructMethods: make(map[string]*StructMethodInfo),
	}
}

// TraitType creates a trait type with the given name and method signatures.
func TraitType(name string, methods map[string]*TraitMethodInfo) *Type {
	return &Type{
		Kind:         KindTrait,
		TraitName:    name,
		TraitMethods: methods,
	}
}

// StructSatisfiesTrait checks whether a struct type satisfies all methods of a trait.
func StructSatisfiesTrait(structType, traitType *Type) bool {
	if structType == nil || traitType == nil || structType.Kind != KindStruct || traitType.Kind != KindTrait {
		return false
	}
	for methodName, traitMethod := range traitType.TraitMethods {
		structMethod, ok := structType.StructMethods[methodName]
		if !ok {
			return false
		}
		if !structMethod.IsPub {
			return false
		}
		if traitMethod.IsMut != structMethod.IsMut {
			return false
		}
		// Compare signatures (excluding _self)
		traitSig := traitMethod.Signature
		structSig := structMethod.Signature
		if traitSig == nil || structSig == nil {
			return false
		}
		// trait methods have no _self; struct methods include _self as first param
		// Compare return types
		if !typesEqual(traitSig.Return, structSig.Return) {
			return false
		}
		// Compare parameter counts: trait has N params, struct has N+1 (with _self)
		if len(traitSig.Params) != len(structSig.Params)-1 {
			return false
		}
		// Compare each parameter type
		for i, tp := range traitSig.Params {
			if !typesEqual(tp, structSig.Params[i+1]) {
				return false
			}
		}
	}
	return true
}

// FunctionType creates a function type.
func FunctionType(params []*Type, ret *Type) *Type {
	return &Type{
		Kind:   KindFunction,
		Params: params,
		Return: ret,
	}
}

// VariadicFunctionType creates a variadic function type.
// The last parameter in params is the variadic element type.
func VariadicFunctionType(params []*Type, ret *Type) *Type {
	return &Type{
		Kind:     KindFunction,
		Params:   params,
		Return:   ret,
		Variadic: true,
	}
}

// EnumType creates an enum base type with the given name and variant values.
func EnumType(name string, values map[string]int64) *Type {
	return &Type{
		Kind:       KindEnum,
		EnumName:   name,
		EnumValues: values,
	}
}

// EnumVariantType creates a type representing a specific enum variant.
func EnumVariantType(enumType *Type, variantName string) *Type {
	return &Type{
		Kind:        KindEnum,
		EnumName:    enumType.EnumName,
		EnumVariant: variantName,
		EnumValues:  enumType.EnumValues,
	}
}

// EnumVariantValue returns the integer value for a specific enum variant.
func EnumVariantValue(t *Type) (int64, bool) {
	if t == nil || t.Kind != KindEnum || t.EnumVariant == "" || t.EnumValues == nil {
		return 0, false
	}
	v, ok := t.EnumValues[t.EnumVariant]
	return v, ok
}

// TypeOrPanic returns the type or panics with the given message.
// Useful for tests.
func TypeOrPanic(t *Type, msg string) *Type {
	if t == nil || t.Kind == KindInvalid {
		panic(msg)
	}
	return t
}

// EqualsStrict checks type equality including nullability.
func (t *Type) EqualsStrict(other *Type) bool {
	if t == nil || other == nil {
		return t == other
	}
	return t.Equals(other) && t.Nullable == other.Nullable
}

// WithoutNullable returns a copy of the type with nullable set to false.
func (t *Type) WithoutNullable() *Type {
	if t == nil {
		return nil
	}
	cp := *t
	cp.Nullable = false
	return &cp
}

// Ensure type safety: verify Immutable is not needed since Type is a value-safe pointer.

func init() {
	// Validate predefined types
	if Void == nil || Bool == nil || Byte == nil || Int == nil ||
		Float == nil || Char == nil || String == nil {
		panic("types: predefined types not initialized")
	}
	if ListOf(Int) == nil || MapOf(String, Int) == nil {
		panic("types: type constructors not working")
	}
}
