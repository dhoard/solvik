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
	"fmt"
	"math"
	"os"
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

// RegisterAll registers all standard native functions.
func RegisterAll(registry *vm.NativeRegistry) {
	registerCore(registry)
	registerString(registry)
	registerMath(registry)
	registerEnv(registry)
	registerFile(registry)
	registerProcess(registry)
	registerTime(registry)
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
			v, err := strconv.ParseInt(s, 10, 32)
			if err != nil {
				return vm.NewValueNull(), fmt.Errorf("cannot convert %q to int", s)
			}
			return vm.NewValueInt(int32(v)), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "core.long",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("long expects 1 argument, got %d", len(args))
			}
			s := args[0].String()
			v, err := strconv.ParseInt(s, 10, 64)
			if err != nil {
				return vm.NewValueNull(), fmt.Errorf("cannot convert %q to long", s)
			}
			return vm.NewValueLong(v), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "core.double",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("double expects 1 argument, got %d", len(args))
			}
			s := args[0].String()
			v, err := strconv.ParseFloat(s, 64)
			if err != nil {
				return vm.NewValueNull(), fmt.Errorf("cannot convert %q to double", s)
			}
			return vm.NewValueDouble(v), nil
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
		Name: "core.len",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("len expects 1 argument, got %d", len(args))
			}
			switch args[0].Kind {
			case vm.ValueList:
				return vm.NewValueInt(int32(args[0].ListLen())), nil
			case vm.ValueMap:
				return vm.NewValueInt(int32(args[0].MapLen())), nil
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
	case vm.ValueLong:
		return "long"
	case vm.ValueFloat:
		return "float"
	case vm.ValueDouble:
		return "double"
	case vm.ValueChar:
		return "char"
	case vm.ValueString:
		return "string"
	case vm.ValueList:
		return "List"
	case vm.ValueMap:
		return "Map"
	case vm.ValueRegex:
		return "Regex"
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
			return vm.NewValueInt(int32(utf8.RuneCountInString(s))), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "string.byteLength",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("string.byteLength expects 1 argument, got %d", len(args))
			}
			s := args[0].String()
			return vm.NewValueInt(int32(len(s))), nil
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
			return vm.NewValueInt(int32(idx)), nil
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
			case vm.ValueLong:
				v := args[0].Long()
				if v < 0 {
					return vm.NewValueLong(-v), nil
				}
				return args[0], nil
			case vm.ValueFloat:
				v := args[0].Float()
				if v < 0 {
					return vm.NewValueFloat(-v), nil
				}
				return args[0], nil
			case vm.ValueDouble:
				v := args[0].Double()
				if v < 0 {
					return vm.NewValueDouble(-v), nil
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
			if a.Kind == vm.ValueLong && b.Kind == vm.ValueLong {
				if a.Long() < b.Long() {
					return a, nil
				}
				return b, nil
			}
			if a.Kind == vm.ValueDouble || b.Kind == vm.ValueDouble {
				if a.Double() < b.Double() {
					return a, nil
				}
				return b, nil
			}
			if a.Kind == vm.ValueFloat || b.Kind == vm.ValueFloat {
				if a.Float() < b.Float() {
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
			if a.Kind == vm.ValueLong && b.Kind == vm.ValueLong {
				if a.Long() > b.Long() {
					return a, nil
				}
				return b, nil
			}
			if a.Kind == vm.ValueDouble || b.Kind == vm.ValueDouble {
				if a.Double() > b.Double() {
					return a, nil
				}
				return b, nil
			}
			if a.Kind == vm.ValueFloat || b.Kind == vm.ValueFloat {
				if a.Float() > b.Float() {
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
				return vm.NewValueFloat(float32(math.Floor(float64(args[0].Float())))), nil
			case vm.ValueDouble:
				return vm.NewValueDouble(math.Floor(args[0].Double())), nil
			case vm.ValueInt, vm.ValueLong:
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
				return vm.NewValueFloat(float32(math.Ceil(float64(args[0].Float())))), nil
			case vm.ValueDouble:
				return vm.NewValueDouble(math.Ceil(args[0].Double())), nil
			case vm.ValueInt, vm.ValueLong:
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
				return vm.NewValueFloat(float32(math.Round(float64(args[0].Float())))), nil
			case vm.ValueDouble:
				return vm.NewValueDouble(math.Round(args[0].Double())), nil
			case vm.ValueInt, vm.ValueLong:
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
			v := args[0].Double()
			return vm.NewValueDouble(math.Sqrt(v)), nil
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
			return vm.NewValueDouble(math.Pow(base, exp)), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "math.sin",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("math.sin expects 1 argument, got %d", len(args))
			}
			return vm.NewValueDouble(math.Sin(args[0].Double())), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "math.cos",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("math.cos expects 1 argument, got %d", len(args))
			}
			return vm.NewValueDouble(math.Cos(args[0].Double())), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "math.tan",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("math.tan expects 1 argument, got %d", len(args))
			}
			return vm.NewValueDouble(math.Tan(args[0].Double())), nil
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
			// Use the process package
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
			return vm.NewValueInt(int32(state.ExitCode())), nil
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
			return vm.NewValueLong(time.Now().UnixMilli()), nil
		},
	})

	registry.Register(&vm.NativeFunction{
		Name: "time.sleep",
		Handler: func(args []vm.Value) (vm.Value, error) {
			if len(args) != 1 {
				return vm.NewValueNull(), fmt.Errorf("time.sleep expects 1 argument, got %d", len(args))
			}
			millis := args[0].Long()
			time.Sleep(time.Duration(millis) * time.Millisecond)
			return vm.NewValueNull(), nil
		},
	})
}

// ===== Aliases =====

func registerAliases(registry *vm.NativeRegistry) {
	// Alias short names without module prefix for convenience
	handlers := []struct {
		name    string
		handler func([]vm.Value) (vm.Value, error)
	}{
		{"print", corePrintHandler(registry)},
		{"println", corePrintlnHandler(registry)},
		{"len", coreLenHandler(registry)},
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

// Ensure imports are used
var _ = utf8.ValidString
