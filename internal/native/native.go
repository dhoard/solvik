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

// Package native provides standard native/host function implementations.
package native

import (
	"crypto/md5"
	cryptorand "crypto/rand"
	"crypto/sha1"
	"crypto/sha256"
	"crypto/sha512"
	"encoding/base64"
	"fmt"
	"math"
	"math/rand/v2"
	"os"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/dhoard/solvik-language/internal/vm"
)

// Capability tokens for gating sensitive native functions.
const (
	CapFilesystem = "filesystem"
	CapProcess    = "process"
	CapEnv        = "env"
	CapNetwork    = "network"
)

// _randomSource is the seeded PRNG instance. Nil means not yet initialized.
var _randomSource *rand.Rand

// RegisterAll registers all standard native functions.
func RegisterAll(registry *vm.NativeRegistry) {
	registerCore(registry)
	registerString(registry)
	registerMath(registry)
	registerEnv(registry)
	registerFile(registry)
	registerProcess(registry)
	registerTime(registry)
	registerRandom(registry)
	registerPath(registry)
	registerBase64(registry)
	registerHash(registry)
	registerSecrets(registry)
	registerStack(registry)
	registerMap(registry)
	registerAliases(registry)
}

// ===== 3.1 Core Module =====

func registerCore(registry *vm.NativeRegistry) {
	registry.Register(&vm.NativeFunction{
		Name: "core.print",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("print expects 1 argument, got %d", len(args))
			}
			fmt.Print(args[0].String())
			return vm.NewValueNull(), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "core.println",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("println expects 1 argument, got %d", len(args))
			}
			fmt.Println(args[0].String())
			return vm.NewValueNull(), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "core.string",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("string expects 1 argument, got %d", len(args))
			}
			return vm.NewValueString(args[0].String()), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "core.int",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("int expects 1 argument, got %d", len(args))
			}
			s := args[0].String()
			v, err := strconv.ParseInt(s, 10, 64)
			if err != nil {
				return vm.NewValueNull(), fmt.Errorf("cannot convert %q to int", s)
			}
			return vm.NewValueInt(v), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "core.float",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("float expects 1 argument, got %d", len(args))
			}
			s := args[0].String()
			v, err := strconv.ParseFloat(s, 64)
			if err != nil {
				return vm.NewValueNull(), fmt.Errorf("cannot convert %q to float", s)
			}
			return vm.NewValueFloat(v), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "core.byte",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("byte expects 1 argument, got %d", len(args))
			}
			v := args[0].Int()
			if v < 0 || v > 255 {
				return vm.NewValueNull(), fmt.Errorf("byte value %d out of range (0-255)", v)
			}
			return vm.NewValueByte(uint8(v)), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "core.bool",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("bool expects 1 argument, got %d", len(args))
			}
			return vm.NewValueBool(args[0].IsTruthy()), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "core.typeOf",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("typeOf expects 1 argument, got %d", len(args))
			}
			return vm.NewValueString(typeName(args[0])), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "core.regex",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("regex expects exactly 1 argument, got %d", len(args))
			}
			if args[0].Kind != vm.ValueString {
				return vm.NewValueNull(), fmt.Errorf("regex expects a string argument, got %s", typeName(args[0]))
			}
			pattern := args[0].String()
			compiled, err := regexp.Compile(pattern)
			if err != nil {
				return vm.NewValueNull(), fmt.Errorf("invalid regular expression %q: %v", pattern, err)
			}
			return vm.NewValueRegex(pattern, compiled), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "core.isType",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 2 {
				return vm.NewValueNull(), fmt.Errorf("isType expects 2 arguments, got %d", len(args))
			}
			if args[1].Kind != vm.ValueString {
				return vm.NewValueNull(), fmt.Errorf("isType expects a string as second argument")
			}
			actual := typeName(args[0])
			expected := args[1].String()
			return vm.NewValueBool(actual == expected), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "core.len",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("len expects 1 argument, got %d", len(args))
			}
			switch args[0].Kind {
			case vm.ValueList:
				return vm.NewValueInt(int64(args[0].ListLen())), nil
			case vm.ValueMap:
				return vm.NewValueInt(int64(args[0].MapLen())), nil
			default:
				return vm.NewValueNull(), fmt.Errorf("len expects a list or map, got %s", args[0].String())
			}
		},
	})
}

func typeName(v vm.Value) string {
	switch v.Kind {
	case vm.ValueNull:
		return "null"
	case vm.ValueBool:
		return "bool"
	case vm.ValueByte:
		return "byte"
	case vm.ValueInt:
		return "int"
	case vm.ValueFloat:
		return "float"
	case vm.ValueChar:
		return "char"
	case vm.ValueString:
		return "string"
	case vm.ValueList:
		return "list"
	case vm.ValueMap:
		return "map"
	case vm.ValueRegex:
		return "regex"
	case vm.ValueException:
		return "exception"
	case vm.ValueStruct:
		return strings.ToLower(v.StructTypeName())
	case vm.ValueTrait:
		return strings.ToLower(v.StructTypeName())
	case vm.ValueStack:
		return "stack"
	default:
		return "unknown"
	}
}

// ===== 3.2 String Module =====

func registerString(registry *vm.NativeRegistry) {
	registry.Register(&vm.NativeFunction{
		Name: "string.length",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("string.length expects 1 argument, got %d", len(args))
			}
			s := args[0].String()
			return vm.NewValueInt(int64(utf8.RuneCountInString(s))), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "string.byteLength",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("string.byteLength expects 1 argument, got %d", len(args))
			}
			s := args[0].String()
			return vm.NewValueInt(int64(len(s))), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "string.charAt",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 2 {
				return vm.NewValueNull(), fmt.Errorf("string.charAt expects 2 arguments, got %d", len(args))
			}
			s := args[0].String()
			idx := int(args[1].Int())
			runes := []rune(s)
			if idx < 0 || idx >= len(runes) {
				return vm.NewValueNull(), fmt.Errorf("string.charAt: index %d out of range (length %d)", idx, len(runes))
			}
			return vm.NewValueChar(runes[idx]), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "string.substring",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 3 {
				return vm.NewValueNull(), fmt.Errorf("string.substring expects 3 arguments, got %d", len(args))
			}
			s := args[0].String()
			start := int(args[1].Int())
			end := int(args[2].Int())
			runes := []rune(s)
			if start < 0 {
				start = 0
			}
			if end > len(runes) {
				end = len(runes)
			}
			if start > end {
				start = end
			}
			return vm.NewValueString(string(runes[start:end])), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "string.contains",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 2 {
				return vm.NewValueNull(), fmt.Errorf("string.contains expects 2 arguments, got %d", len(args))
			}
			s := args[0].String()
			substr := args[1].String()
			return vm.NewValueBool(strings.Contains(s, substr)), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "string.startsWith",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 2 {
				return vm.NewValueNull(), fmt.Errorf("string.startsWith expects 2 arguments, got %d", len(args))
			}
			s := args[0].String()
			prefix := args[1].String()
			return vm.NewValueBool(strings.HasPrefix(s, prefix)), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "string.endsWith",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 2 {
				return vm.NewValueNull(), fmt.Errorf("string.endsWith expects 2 arguments, got %d", len(args))
			}
			s := args[0].String()
			suffix := args[1].String()
			return vm.NewValueBool(strings.HasSuffix(s, suffix)), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "string.indexOf",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 2 {
				return vm.NewValueNull(), fmt.Errorf("string.indexOf expects 2 arguments, got %d", len(args))
			}
			s := args[0].String()
			substr := args[1].String()
			idx := strings.Index(s, substr)
			return vm.NewValueInt(int64(idx)), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "string.toUpper",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("string.toUpper expects 1 argument, got %d", len(args))
			}
			return vm.NewValueString(strings.ToUpper(args[0].String())), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "string.toLower",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("string.toLower expects 1 argument, got %d", len(args))
			}
			return vm.NewValueString(strings.ToLower(args[0].String())), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "string.trim",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("string.trim expects 1 argument, got %d", len(args))
			}
			return vm.NewValueString(strings.TrimSpace(args[0].String())), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "string.split",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 2 {
				return vm.NewValueNull(), fmt.Errorf("string.split expects 2 arguments, got %d", len(args))
			}
			s := args[0].String()
			delim := args[1].String()
			parts := strings.Split(s, delim)
			values := make([]vm.Value, len(parts))
			for i, p := range parts {
				values[i] = vm.NewValueString(p)
			}
			return vm.NewValueList(values), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "string.join",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 2 {
				return vm.NewValueNull(), fmt.Errorf("string.join expects 2 arguments, got %d", len(args))
			}
			list := args[0]
			delim := args[1].String()
			if list.Kind != vm.ValueList {
				return vm.NewValueNull(), fmt.Errorf("string.join expects a list as first argument")
			}
			parts := make([]string, list.ListLen())
			for i := 0; i < list.ListLen(); i++ {
				parts[i] = list.ListGet(i).String()
			}
			return vm.NewValueString(strings.Join(parts, delim)), nil
		},
	})

}

// ===== 3.3 Math Module =====

func registerMath(registry *vm.NativeRegistry) {
	registry.Register(&vm.NativeFunction{
		Name: "math.PI",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 0 {
				return vm.NewValueNull(), fmt.Errorf("math.PI expects 0 arguments, got %d", len(args))
			}
			return vm.NewValueFloat(3.141592653589793), nil
		},
	})
	registry.Register(&vm.NativeFunction{
		Name: "math.E",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 0 {
				return vm.NewValueNull(), fmt.Errorf("math.E expects 0 arguments, got %d", len(args))
			}
			return vm.NewValueFloat(2.718281828459045), nil
		},
	})
	registry.Register(&vm.NativeFunction{
		Name: "math.abs",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("math.abs expects 1 argument, got %d", len(args))
			}
			switch args[0].Kind {
			case vm.ValueInt:
				v := args[0].Int()
				if v < 0 {
					return vm.NewValueInt(-v), nil
				}
				return args[0], nil
			case vm.ValueFloat:
				v := args[0].Double()
				if v < 0 {
					return vm.NewValueFloat(-v), nil
				}
				return args[0], nil
			default:
				return vm.NewValueNull(), fmt.Errorf("math.abs expects a numeric argument")
			}
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "math.min",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 2 {
				return vm.NewValueNull(), fmt.Errorf("math.min expects 2 arguments, got %d", len(args))
			}
			a, b := args[0], args[1]
			if a.Kind == vm.ValueInt && b.Kind == vm.ValueInt {
				if a.Int() < b.Int() {
					return a, nil
				}
				return b, nil
			}
			if a.Kind == vm.ValueFloat || b.Kind == vm.ValueFloat {
				if a.Double() < b.Double() {
					return a, nil
				}
				return b, nil
			}
			return vm.NewValueNull(), fmt.Errorf("math.min expects numeric arguments")
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "math.max",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 2 {
				return vm.NewValueNull(), fmt.Errorf("math.max expects 2 arguments, got %d", len(args))
			}
			a, b := args[0], args[1]
			if a.Kind == vm.ValueInt && b.Kind == vm.ValueInt {
				if a.Int() > b.Int() {
					return a, nil
				}
				return b, nil
			}
			if a.Kind == vm.ValueFloat || b.Kind == vm.ValueFloat {
				if a.Double() > b.Double() {
					return a, nil
				}
				return b, nil
			}
			return vm.NewValueNull(), fmt.Errorf("math.max expects numeric arguments")
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "math.floor",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("math.floor expects 1 argument, got %d", len(args))
			}
			switch args[0].Kind {
			case vm.ValueFloat:
				return vm.NewValueFloat(math.Floor(args[0].Double())), nil
			case vm.ValueInt:
				return args[0], nil
			default:
				return vm.NewValueNull(), fmt.Errorf("math.floor expects a numeric argument")
			}
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "math.ceil",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("math.ceil expects 1 argument, got %d", len(args))
			}
			switch args[0].Kind {
			case vm.ValueFloat:
				return vm.NewValueFloat(math.Ceil(args[0].Double())), nil
			case vm.ValueInt:
				return args[0], nil
			default:
				return vm.NewValueNull(), fmt.Errorf("math.ceil expects a numeric argument")
			}
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "math.round",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("math.round expects 1 argument, got %d", len(args))
			}
			switch args[0].Kind {
			case vm.ValueFloat:
				return vm.NewValueFloat(math.Round(args[0].Double())), nil
			case vm.ValueInt:
				return args[0], nil
			default:
				return vm.NewValueNull(), fmt.Errorf("math.round expects a numeric argument")
			}
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "math.sqrt",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("math.sqrt expects 1 argument, got %d", len(args))
			}
			switch args[0].Kind {
			case vm.ValueFloat:
				return vm.NewValueFloat(math.Sqrt(args[0].Double())), nil
			case vm.ValueInt:
				return vm.NewValueFloat(math.Sqrt(float64(args[0].Int()))), nil
			default:
				return vm.NewValueNull(), fmt.Errorf("math.sqrt expects a numeric argument")
			}
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "math.pow",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 2 {
				return vm.NewValueNull(), fmt.Errorf("math.pow expects 2 arguments, got %d", len(args))
			}
			base := args[0].Double()
			exp := args[1].Double()
			return vm.NewValueFloat(math.Pow(base, exp)), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "math.sin",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("math.sin expects 1 argument, got %d", len(args))
			}
			return vm.NewValueFloat(math.Sin(args[0].Double())), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "math.cos",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("math.cos expects 1 argument, got %d", len(args))
			}
			return vm.NewValueFloat(math.Cos(args[0].Double())), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "math.tan",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("math.tan expects 1 argument, got %d", len(args))
			}
			return vm.NewValueFloat(math.Tan(args[0].Double())), nil
		},
	})
}

// ===== 3.4 Environment Module =====

func registerEnv(registry *vm.NativeRegistry) {
	registry.Register(&vm.NativeFunction{
		Name: "env.get",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("env.get expects 1 argument, got %d", len(args))
			}
			key := args[0].String()
			val, ok := os.LookupEnv(key)
			if !ok {
				return vm.NewValueNull(), nil
			}
			return vm.NewValueString(val), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "env.set",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 2 {
				return vm.NewValueNull(), fmt.Errorf("env.set expects 2 arguments, got %d", len(args))
			}
			key := args[0].String()
			value := args[1].String()
			if err := os.Setenv(key, value); err != nil {
				return vm.NewValueNull(), fmt.Errorf("env.set: %v", err)
			}
			return vm.NewValueNull(), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "env.keys",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 0 {
				return vm.NewValueNull(), fmt.Errorf("env.keys expects 0 arguments, got %d", len(args))
			}
			envVars := os.Environ()
			values := make([]vm.Value, len(envVars))
			for i, e := range envVars {
				parts := strings.SplitN(e, "=", 2)
				values[i] = vm.NewValueString(parts[0])
			}
			return vm.NewValueList(values), nil
		},
	})
}

// ===== 3.4 File Module =====

func registerFile(registry *vm.NativeRegistry) {
	registry.Register(&vm.NativeFunction{
		Name: "file.read",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("file.read expects 1 argument, got %d", len(args))
			}
			path := args[0].String()
			data, err := os.ReadFile(path)
			if err != nil {
				return vm.NewValueNull(), fmt.Errorf("file.read: %v", err)
			}
			return vm.NewValueString(string(data)), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "file.write",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 2 {
				return vm.NewValueNull(), fmt.Errorf("file.write expects 2 arguments, got %d", len(args))
			}
			path := args[0].String()
			content := args[1].String()
			if err := os.WriteFile(path, []byte(content), 0644); err != nil {
				return vm.NewValueNull(), fmt.Errorf("file.write: %v", err)
			}
			return vm.NewValueNull(), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "file.append",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 2 {
				return vm.NewValueNull(), fmt.Errorf("file.append expects 2 arguments, got %d", len(args))
			}
			path := args[0].String()
			content := args[1].String()
			f, err := os.OpenFile(path, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0644)
			if err != nil {
				return vm.NewValueNull(), fmt.Errorf("file.append: %v", err)
			}
			defer f.Close()
			if _, err := f.WriteString(content); err != nil {
				return vm.NewValueNull(), fmt.Errorf("file.append: %v", err)
			}
			return vm.NewValueNull(), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "file.delete",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("file.delete expects 1 argument, got %d", len(args))
			}
			path := args[0].String()
			if err := os.Remove(path); err != nil {
				return vm.NewValueNull(), fmt.Errorf("file.delete: %v", err)
			}
			return vm.NewValueNull(), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "file.exists",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("file.exists expects 1 argument, got %d", len(args))
			}
			path := args[0].String()
			_, err := os.Stat(path)
			return vm.NewValueBool(err == nil), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "file.temp",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("file.temp expects 1 argument, got %d", len(args))
			}
			f, err := os.CreateTemp("", args[0].String())
			if err != nil {
				return vm.NewValueNull(), fmt.Errorf("file.temp: %v", err)
			}
			f.Close()
			return vm.NewValueString(f.Name()), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "file.tempDir",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("file.tempDir expects 1 argument, got %d", len(args))
			}
			dir, err := os.MkdirTemp("", args[0].String())
			if err != nil {
				return vm.NewValueNull(), fmt.Errorf("file.tempDir: %v", err)
			}
			return vm.NewValueString(dir), nil
		},
	})
}

// ===== 3.4 Process Module =====

func registerProcess(registry *vm.NativeRegistry) {
	registry.Register(&vm.NativeFunction{
		Name: "process.run",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) < 1 {
				return vm.NewValueNull(), fmt.Errorf("process.run expects at least 1 argument, got %d", len(args))
			}
			executable := args[0].String()
			var procArgs []string
			for i := 1; i < len(args); i++ {
				procArgs = append(procArgs, args[i].String())
			}
			attr := &os.ProcAttr{
				Files: []*os.File{os.Stdin, os.Stdout, os.Stderr},
			}
			proc, err := os.StartProcess(executable, append([]string{executable}, procArgs...), attr)
			if err != nil {
				return vm.NewValueNull(), fmt.Errorf("process.run: %v", err)
			}
			state, err := proc.Wait()
			if err != nil {
				return vm.NewValueNull(), fmt.Errorf("process.run: %v", err)
			}
			return vm.NewValueInt(int64(state.ExitCode())), nil
		},
	})
}

// ===== 3.4 Time Module =====

func registerTime(registry *vm.NativeRegistry) {
	registry.Register(&vm.NativeFunction{
		Name: "time.now",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 0 {
				return vm.NewValueNull(), fmt.Errorf("time.now expects 0 arguments, got %d", len(args))
			}
			return vm.NewValueInt(time.Now().UnixMilli()), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "time.sleep",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("time.sleep expects 1 argument, got %d", len(args))
			}
			millis := args[0].Int()
			time.Sleep(time.Duration(millis) * time.Millisecond)
			return vm.NewValueNull(), nil
		},
	})
}

// ===== 3.5 Random Module =====

func registerRandom(registry *vm.NativeRegistry) {
	registry.Register(&vm.NativeFunction{
		Name: "random.float",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 0 {
				return vm.NewValueNull(), fmt.Errorf("random.float expects 0 arguments, got %d", len(args))
			}
			if _randomSource == nil {
				_randomSource = rand.New(rand.NewPCG(rand.Uint64(), rand.Uint64()))
			}
			return vm.NewValueFloat(_randomSource.Float64()), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "random.int",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 2 {
				return vm.NewValueNull(), fmt.Errorf("random.int expects 2 arguments, got %d", len(args))
			}
			a := args[0].Int()
			b := args[1].Int()
			if a > b {
				return vm.NewValueNull(), fmt.Errorf("random.int: min (%d) must be <= max (%d)", a, b)
			}
			if _randomSource == nil {
				_randomSource = rand.New(rand.NewPCG(rand.Uint64(), rand.Uint64()))
			}
			return vm.NewValueInt(a + _randomSource.Int64N(b-a+1)), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "random.range",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 2 {
				return vm.NewValueNull(), fmt.Errorf("random.range expects 2 arguments, got %d", len(args))
			}
			start := args[0].Int()
			stop := args[1].Int()
			if stop <= start {
				return vm.NewValueNull(), fmt.Errorf("random.range: stop (%d) must be > start (%d)", stop, start)
			}
			if _randomSource == nil {
				_randomSource = rand.New(rand.NewPCG(rand.Uint64(), rand.Uint64()))
			}
			return vm.NewValueInt(start + _randomSource.Int64N(stop-start)), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "random.uniform",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 2 {
				return vm.NewValueNull(), fmt.Errorf("random.uniform expects 2 arguments, got %d", len(args))
			}
			a := args[0].Double()
			b := args[1].Double()
			if _randomSource == nil {
				_randomSource = rand.New(rand.NewPCG(rand.Uint64(), rand.Uint64()))
			}
			return vm.NewValueFloat(a + (b-a)*_randomSource.Float64()), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "random.choice",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("random.choice expects 1 argument, got %d", len(args))
			}
			list := args[0]
			if list.Kind != vm.ValueList {
				return vm.NewValueNull(), fmt.Errorf("random.choice expects a list")
			}
			n := list.ListLen()
			if n == 0 {
				return vm.NewValueNull(), nil
			}
			if _randomSource == nil {
				_randomSource = rand.New(rand.NewPCG(rand.Uint64(), rand.Uint64()))
			}
			idx := int(_randomSource.Int64N(int64(n)))
			return list.ListGet(idx), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "random.shuffle",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("random.shuffle expects 1 argument, got %d", len(args))
			}
			list := args[0]
			if list.Kind != vm.ValueList {
				return vm.NewValueNull(), fmt.Errorf("random.shuffle expects a list")
			}
			n := list.ListLen()
			result := make([]vm.Value, n)
			for i := 0; i < n; i++ {
				result[i] = list.ListGet(i)
			}
			if _randomSource == nil {
				_randomSource = rand.New(rand.NewPCG(rand.Uint64(), rand.Uint64()))
			}
			for i := n - 1; i > 0; i-- {
				j := int(_randomSource.Int64N(int64(i + 1)))
				result[i], result[j] = result[j], result[i]
			}
			return vm.NewValueList(result), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "random.sample",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 2 {
				return vm.NewValueNull(), fmt.Errorf("random.sample expects 2 arguments, got %d", len(args))
			}
			list := args[0]
			if list.Kind != vm.ValueList {
				return vm.NewValueNull(), fmt.Errorf("random.sample expects a list as first argument")
			}
			k := int(args[1].Int())
			n := list.ListLen()
			if k <= 0 {
				return vm.NewValueList(nil), nil
			}
			if k >= n {
				// Return all elements shuffled
				all := make([]vm.Value, n)
				for i := 0; i < n; i++ {
					all[i] = list.ListGet(i)
				}
				if _randomSource == nil {
					_randomSource = rand.New(rand.NewPCG(rand.Uint64(), rand.Uint64()))
				}
				for i := n - 1; i > 0; i-- {
					j := int(_randomSource.Int64N(int64(i + 1)))
					all[i], all[j] = all[j], all[i]
				}
				return vm.NewValueList(all), nil
			}
			// Reservoir sampling
			if _randomSource == nil {
				_randomSource = rand.New(rand.NewPCG(rand.Uint64(), rand.Uint64()))
			}
			result := make([]vm.Value, k)
			for i := 0; i < k; i++ {
				result[i] = list.ListGet(i)
			}
			for i := k; i < n; i++ {
				j := int(_randomSource.Int64N(int64(i + 1)))
				if j < k {
					result[j] = list.ListGet(i)
				}
			}
			return vm.NewValueList(result), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "random.seed",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("random.seed expects 1 argument, got %d", len(args))
			}
			s := args[0].Int()
			seed := uint64(s)
			_randomSource = rand.New(rand.NewPCG(seed, seed^0xdeadbeefcafebabe))
			return vm.NewValueNull(), nil
		},
	})
}

// ===== 3.6 Path Module =====

func registerPath(registry *vm.NativeRegistry) {
	registry.Register(&vm.NativeFunction{
		Name: "path.join",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) < 1 {
				return vm.NewValueNull(), fmt.Errorf("path.join expects at least 1 argument, got %d", len(args))
			}
			parts := make([]string, len(args))
			for i, arg := range args {
				parts[i] = arg.String()
			}
			return vm.NewValueString(filepath.Join(parts...)), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "path.basename",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("path.basename expects 1 argument, got %d", len(args))
			}
			return vm.NewValueString(filepath.Base(args[0].String())), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "path.dirname",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("path.dirname expects 1 argument, got %d", len(args))
			}
			return vm.NewValueString(filepath.Dir(args[0].String())), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "path.ext",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("path.ext expects 1 argument, got %d", len(args))
			}
			return vm.NewValueString(filepath.Ext(args[0].String())), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "path.abs",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("path.abs expects 1 argument, got %d", len(args))
			}
			abs, err := filepath.Abs(args[0].String())
			if err != nil {
				return vm.NewValueNull(), fmt.Errorf("path.abs: %v", err)
			}
			return vm.NewValueString(abs), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "path.exists",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("path.exists expects 1 argument, got %d", len(args))
			}
			_, err := os.Stat(args[0].String())
			return vm.NewValueBool(err == nil), nil
		},
	})
}

// ===== 3.7 Base64 Module =====

func registerBase64(registry *vm.NativeRegistry) {
	registry.Register(&vm.NativeFunction{
		Name: "base64.encode",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("base64.encode expects 1 argument, got %d", len(args))
			}
			encoded := base64.StdEncoding.EncodeToString([]byte(args[0].String()))
			return vm.NewValueString(encoded), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "base64.decode",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("base64.decode expects 1 argument, got %d", len(args))
			}
			decoded, err := base64.StdEncoding.DecodeString(args[0].String())
			if err != nil {
				return vm.NewValueNull(), fmt.Errorf("base64.decode: %v", err)
			}
			return vm.NewValueString(string(decoded)), nil
		},
	})
}

// ===== 3.8 Hash Module =====

func registerHash(registry *vm.NativeRegistry) {
	registry.Register(&vm.NativeFunction{
		Name: "hash.md5",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("hash.md5 expects 1 argument, got %d", len(args))
			}
			sum := md5.Sum([]byte(args[0].String()))
			return vm.NewValueString(fmt.Sprintf("%x", sum)), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "hash.sha1",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("hash.sha1 expects 1 argument, got %d", len(args))
			}
			sum := sha1.Sum([]byte(args[0].String()))
			return vm.NewValueString(fmt.Sprintf("%x", sum)), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "hash.sha256",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("hash.sha256 expects 1 argument, got %d", len(args))
			}
			sum := sha256.Sum256([]byte(args[0].String()))
			return vm.NewValueString(fmt.Sprintf("%x", sum)), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "hash.sha512",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("hash.sha512 expects 1 argument, got %d", len(args))
			}
			sum := sha512.Sum512([]byte(args[0].String()))
			return vm.NewValueString(fmt.Sprintf("%x", sum)), nil
		},
	})
}

// ===== 3.10 Secrets Module =====

func registerSecrets(registry *vm.NativeRegistry) {
	registry.Register(&vm.NativeFunction{
		Name: "secrets.token",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("secrets.token expects 1 argument, got %d", len(args))
			}
			n := int(args[0].Int())
			if n <= 0 {
				return vm.NewValueNull(), fmt.Errorf("secrets.token: n must be > 0")
			}
			buf := make([]byte, n)
			if _, err := cryptorand.Read(buf); err != nil {
				return vm.NewValueNull(), fmt.Errorf("secrets.token: %v", err)
			}
			return vm.NewValueString(base64.RawURLEncoding.EncodeToString(buf)), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "secrets.hex",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("secrets.hex expects 1 argument, got %d", len(args))
			}
			n := int(args[0].Int())
			if n <= 0 {
				return vm.NewValueNull(), fmt.Errorf("secrets.hex: n must be > 0")
			}
			buf := make([]byte, n)
			if _, err := cryptorand.Read(buf); err != nil {
				return vm.NewValueNull(), fmt.Errorf("secrets.hex: %v", err)
			}
			return vm.NewValueString(fmt.Sprintf("%x", buf)), nil
		},
	})
}

// ===== Stack Module =====

func registerStack(registry *vm.NativeRegistry) {
	registry.Register(&vm.NativeFunction{
		Name: "stack.push",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 2 {
				return vm.NewValueNull(), fmt.Errorf("stack.push expects 2 arguments (stack, value), got %d", len(args))
			}
			if args[0].Kind != vm.ValueStack {
				return vm.NewValueNull(), fmt.Errorf("stack.push expects a stack as first argument")
			}
			args[0].StackPush(args[1])
			return vm.NewValueNull(), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "stack.pop",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("stack.pop expects 1 argument, got %d", len(args))
			}
			if args[0].Kind != vm.ValueStack {
				return vm.NewValueNull(), fmt.Errorf("stack.pop expects a stack")
			}
			if args[0].StackLen() == 0 {
				return vm.NewValueNull(), fmt.Errorf("stack.pop: stack is empty")
			}
			return args[0].StackPop(), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "stack.peek",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("stack.peek expects 1 argument, got %d", len(args))
			}
			if args[0].Kind != vm.ValueStack {
				return vm.NewValueNull(), fmt.Errorf("stack.peek expects a stack")
			}
			if args[0].StackLen() == 0 {
				return vm.NewValueNull(), fmt.Errorf("stack.peek: stack is empty")
			}
			return args[0].StackPeek(), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "stack.size",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("stack.size expects 1 argument, got %d", len(args))
			}
			if args[0].Kind != vm.ValueStack {
				return vm.NewValueNull(), fmt.Errorf("stack.size expects a stack")
			}
			return vm.NewValueInt(int64(args[0].StackLen())), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "stack.isEmpty",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("stack.isEmpty expects 1 argument, got %d", len(args))
			}
			if args[0].Kind != vm.ValueStack {
				return vm.NewValueNull(), fmt.Errorf("stack.isEmpty expects a stack")
			}
			return vm.NewValueBool(args[0].StackLen() == 0), nil
		},
	})
}

// ===== Aliases =====

// ===== Map Module =====

func registerMap(registry *vm.NativeRegistry) {
	registry.Register(&vm.NativeFunction{
		Name: "map.contains",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 2 {
				return vm.NewValueNull(), fmt.Errorf("map.contains expects 2 arguments (map, key), got %d", len(args))
			}
			m := args[0]
			if m.Kind != vm.ValueMap {
				return vm.NewValueNull(), fmt.Errorf("map.contains expects a map as first argument")
			}
			key := args[1]
			return vm.NewValueBool(m.MapContains(key)), nil
		},
	})
}

func registerAliases(registry *vm.NativeRegistry) {
	// Alias short names without module prefix for convenience
	handlers := []struct {
		name    string
		handler func([]vm.Value) (vm.Value, error)
	}{
		{"print", corePrintHandler(registry)},
		{"println", corePrintlnHandler(registry)},
		{"len", coreLenHandler(registry)},
		{"typeOf", coreTypeOfHandler(registry)},
		{"isType", coreIsTypeHandler(registry)},
	}
	for _, h := range handlers {
		registry.Register(&vm.NativeFunction{
			Name:    h.name,
			Handler: h.handler,
		})
	}
}

func corePrintHandler(registry *vm.NativeRegistry) func([]vm.Value) (vm.Value, error) {
	fn, _ := registry.Lookup("core.print")
	if fn != nil {
		return fn.Handler
	}
	return nil
}

func corePrintlnHandler(registry *vm.NativeRegistry) func([]vm.Value) (vm.Value, error) {
	fn, _ := registry.Lookup("core.println")
	if fn != nil {
		return fn.Handler
	}
	return nil
}

func coreLenHandler(registry *vm.NativeRegistry) func([]vm.Value) (vm.Value, error) {
	fn, _ := registry.Lookup("core.len")
	if fn != nil {
		return fn.Handler
	}
	return nil
}

func coreTypeOfHandler(registry *vm.NativeRegistry) func([]vm.Value) (vm.Value, error) {
	fn, _ := registry.Lookup("core.typeOf")
	if fn != nil {
		return fn.Handler
	}
	return nil
}

func coreIsTypeHandler(registry *vm.NativeRegistry) func([]vm.Value) (vm.Value, error) {
	fn, _ := registry.Lookup("core.isType")
	if fn != nil {
		return fn.Handler
	}
	return nil
}

// Ensure imports are used
var _ = utf8.ValidString
