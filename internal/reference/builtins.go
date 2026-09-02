package reference

// Standard library: builtin methods on values plus the core namespaces.
// Mirrors the Python reference's build_builtins and builtin_method.

import (
	"crypto/md5"
	cryptorand "crypto/rand"
	"crypto/sha1"
	"crypto/sha256"
	"crypto/sha512"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"math"
	mathrand "math/rand"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"
)

func makeNative(name string, fn func(args ...any) any) *nativeFn {
	return &nativeFn{name: name, fn: fn}
}

// Keep the language RNG isolated from Go's package-global source.  Recent Go
// releases may auto-seed or ignore math/rand.Seed on the global source, while
// Solvik specifies that random.seed() makes subsequent draws reproducible.
var solvikRandom = mathrand.New(mathrand.NewSource(1))

// builtinMethod resolves intrinsic methods on values.
func builtinMethod(obj any, name string, in *Interpreter) *nativeFn {
	switch obj.(type) {
	case bool, *byteValue, int64, float64, charValue, string, []any, *solvikMap, *stackValue:
		switch name {
		case "string":
			return makeNative(typeNameOf(obj)+".string", func(...any) any { return solvikString(obj) })
		case "equals":
			return makeNative(typeNameOf(obj)+".equals", func(args ...any) any { return builtinEqual(obj, args[0], in) })
		}
	}
	switch obj.(type) {
	case *userFunction, *closureValue, *boundMethod, *bcCallable, *nativeFn:
		switch name {
		case "string":
			return makeNative("function.string", func(...any) any { return solvikString(obj) })
		case "equals":
			return makeNative("function.equals", func(args ...any) any { return in.equal(obj, args[0]) })
		}
	}
	switch obj.(type) {
	case *byteValue, int64, float64:
		if name == "abs" {
			return makeNative(typeNameOf(obj)+".abs", func(...any) any {
				switch n := numericValue(obj).(type) {
				case int64:
					if n < 0 {
						return -n
					}
					return n
				case float64:
					return math.Abs(n)
				}
				return obj
			})
		}
	}
	switch obj.(type) {
	case *byteValue, int64, float64, charValue, string:
		switch name {
		case "compare":
			return makeNative(typeNameOf(obj)+".compare", func(args ...any) any { return builtinCompare(obj, args[0], in) })
		case "hash":
			return makeNative(typeNameOf(obj)+".hash", func(...any) any { return stableHash(obj) })
		}
	}
	if b, ok := obj.(bool); ok && name == "hash" {
		_ = b
		return makeNative("bool.hash", func(...any) any {
			if b {
				return int64(1)
			}
			return int64(0)
		})
	}
	if s, ok := obj.(string); ok && name != "" {
		// note: charValue is distinct from string via type switch order above
		switch name {
		case "len":
			return makeNative("string.len", func(...any) any { return int64(len([]rune(s))) })
		case "isEmpty":
			return makeNative("string.isEmpty", func(...any) any { return len(s) == 0 })
		case "byteLength":
			return makeNative("string.byteLength", func(...any) any { return int64(len(s)) })
		case "charAt":
			return makeNative("string.charAt", func(args ...any) any {
				runes := []rune(s)
				i := int(toIntLike(args[0]))
				if i < 0 || i >= len(runes) {
					panic(runtimeErrCode("E031", "index out of range"))
				}
				return charValue(string(runes[i]))
			})
		case "substring":
			return makeNative("string.substring", func(args ...any) any {
				runes := []rune(s)
				a := int(toIntLike(args[0]))
				b := int(toIntLike(args[1]))
				if a < 0 {
					a = 0
				}
				if b > len(runes) {
					b = len(runes)
				}
				if a > b {
					a = b
				}
				return string(runes[a:b])
			})
		case "contains":
			return makeNative("string.contains", func(args ...any) any { return strings.Contains(s, args[0].(string)) })
		case "startsWith":
			return makeNative("string.startsWith", func(args ...any) any { return strings.HasPrefix(s, args[0].(string)) })
		case "endsWith":
			return makeNative("string.endsWith", func(args ...any) any { return strings.HasSuffix(s, args[0].(string)) })
		case "indexOf":
			return makeNative("string.indexOf", func(args ...any) any { return int64(strings.Index(s, args[0].(string))) })
		case "toUpper":
			return makeNative("string.toUpper", func(...any) any { return strings.ToUpper(s) })
		case "toLower":
			return makeNative("string.toLower", func(...any) any { return strings.ToLower(s) })
		case "trim":
			return makeNative("string.trim", func(...any) any { return strings.TrimSpace(s) })
		case "split":
			return makeNative("string.split", func(args ...any) any {
				parts := strings.Split(s, args[0].(string))
				out := make([]any, len(parts))
				for i, p := range parts {
					out[i] = p
				}
				return out
			})
		case "iterator":
			return makeNative("string.iterator", func(...any) any {
				runes := []rune(s)
				out := make([]any, len(runes))
				for i, r := range runes {
					out[i] = charValue(string(r))
				}
				return out
			})
		}
	}
	if xs, ok := obj.([]any); ok {
		switch name {
		case "len":
			return makeNative("list.len", func(...any) any { return int64(len(xs)) })
		case "isEmpty":
			return makeNative("list.isEmpty", func(...any) any { return len(xs) == 0 })
		case "contains":
			return makeNative("list.contains", func(args ...any) any {
				for _, x := range xs {
					if builtinEqual(x, args[0], in) {
						return true
					}
				}
				return false
			})
		case "iterator":
			return makeNative("list.iterator", func(...any) any { return copyValue(xs) })
		case "map":
			return makeNative("list.map", func(args ...any) any {
				out := make([]any, 0, len(xs))
				for _, x := range xs {
					out = append(out, copyValue(invokeCallable(args[0], []any{x})))
				}
				return out
			})
		case "filter":
			return makeNative("list.filter", func(args ...any) any {
				out := make([]any, 0)
				for _, x := range xs {
					if boolOf(invokeCallable(args[0], []any{x})) {
						out = append(out, copyValue(x))
					}
				}
				return out
			})
		case "reduce":
			return makeNative("list.reduce", func(args ...any) any {
				if len(xs) == 0 {
					panic(runtimeErrCode("E072", "reduce of an empty list"))
				}
				mut := copyValue(xs[0])
				for _, x := range xs[1:] {
					mut = invokeCallable(args[0], []any{mut, x})
				}
				return mut
			})
		case "fold":
			return makeNative("list.fold", func(args ...any) any {
				mut := copyValue(args[0])
				for _, x := range xs {
					mut = invokeCallable(args[1], []any{mut, x})
				}
				return mut
			})
		case "find":
			return makeNative("list.find", func(args ...any) any {
				for _, x := range xs {
					if boolOf(invokeCallable(args[0], []any{x})) {
						return copyValue(x)
					}
				}
				return nil
			})
		case "any":
			return makeNative("list.any", func(args ...any) any {
				for _, x := range xs {
					if boolOf(invokeCallable(args[0], []any{x})) {
						return true
					}
				}
				return false
			})
		case "all":
			return makeNative("list.all", func(args ...any) any {
				for _, x := range xs {
					if !boolOf(invokeCallable(args[0], []any{x})) {
						return false
					}
				}
				return true
			})
		case "first":
			return makeNative("list.first", func(...any) any {
				if len(xs) == 0 {
					return nil
				}
				return copyValue(xs[0])
			})
		case "last":
			return makeNative("list.last", func(...any) any {
				if len(xs) == 0 {
					return nil
				}
				return copyValue(xs[len(xs)-1])
			})
		case "reverse":
			return makeNative("list.reverse", func(...any) any {
				out := make([]any, len(xs))
				for i, x := range xs {
					out[len(xs)-1-i] = copyValue(x)
				}
				return out
			})
		case "sort":
			return makeNative("list.sort", func(args ...any) any {
				out := make([]any, len(xs))
				copy(out, xs)
				compare := args[0]
				sort.SliceStable(out, func(i, j int) bool {
					n := toIntLike(invokeCallable(compare, []any{out[i], out[j]}))
					return n < 0
				})
				for i := range out {
					out[i] = copyValue(out[i])
				}
				return out
			})
		}
	}
	if m, ok := obj.(*solvikMap); ok {
		switch name {
		case "len":
			return makeNative("map.len", func(...any) any { return int64(len(m.entries)) })
		case "isEmpty":
			return makeNative("map.isEmpty", func(...any) any { return len(m.entries) == 0 })
		case "contains":
			return makeNative("map.contains", func(args ...any) any {
				_, has := m.get(args[0])
				return has
			})
		case "iterator":
			return makeNative("map.iterator", func(...any) any {
				return m.keys()
			})
		}
	}
	if st, ok := obj.(*stackValue); ok {
		switch name {
		case "push":
			return makeNative("stack.push", func(args ...any) any {
				st.items = append(st.items, copyValue(args[0]))
				return nil
			})
		case "pop":
			return makeNative("stack.pop", func(...any) any {
				if len(st.items) == 0 {
					panic(runtimeErrCode("E031", "pop from empty stack"))
				}
				last := st.items[len(st.items)-1]
				st.items = st.items[:len(st.items)-1]
				return copyValue(last)
			})
		case "peek":
			return makeNative("stack.peek", func(...any) any {
				if len(st.items) == 0 {
					panic(runtimeErrCode("E031", "peek from empty stack"))
				}
				return copyValue(st.items[len(st.items)-1])
			})
		case "len":
			return makeNative("stack.len", func(...any) any { return int64(len(st.items)) })
		case "isEmpty":
			return makeNative("stack.isEmpty", func(...any) any { return len(st.items) == 0 })
		case "contains":
			return makeNative("stack.contains", func(args ...any) any {
				for _, x := range st.items {
					if builtinEqual(x, args[0], in) {
						return true
					}
				}
				return false
			})
		case "iterator":
			return makeNative("stack.iterator", func(...any) any { return copyValue(st.items) })
		}
	}
	return nil
}

func mapKeyString(v any) string {
	switch x := v.(type) {
	case string:
		return x
	case charValue:
		return string(x)
	case int64:
		return fmt.Sprintf("%d", x)
	case *byteValue:
		return fmt.Sprintf("%d", x.v)
	case bool:
		if x {
			return "true"
		}
		return "false"
	}
	panic(runtimeErr("map key must be a scalar value"))
}

func boolOf(v any) bool {
	if b, ok := v.(bool); ok {
		return b
	}
	return v != nil
}

func builtinEqual(a, b any, in *Interpreter) bool {
	if ab, ok := a.(*byteValue); ok {
		if bb, ok2 := b.(*byteValue); ok2 {
			return ab.v == bb.v
		}
		if bi, ok2 := b.(int64); ok2 {
			return ab.v == bi
		}
		return false
	}
	if ab, ok := b.(*byteValue); ok {
		if ai, ok2 := a.(int64); ok2 {
			return ab.v == ai
		}
	}
	return in.equal(a, b)
}

func builtinCompare(a, b any, in *Interpreter) int64 {
	switch av := numericValue(a).(type) {
	case int64:
		bv := toIntLike(b)
		if av < bv {
			return -1
		}
		if av > bv {
			return 1
		}
		return 0
	case float64:
		if bf, ok := numericValue(b).(float64); ok {
			if av < bf {
				return -1
			}
			if av > bf {
				return 1
			}
			return 0
		}
		return builtinCompare(float64(av), b, in)
	}
	// strings/chars compare by codepoint
	as, aIsS := a.(string)
	bs, bIsS := b.(string)
	if aIsS && bIsS {
		if as < bs {
			return -1
		}
		if as > bs {
			return 1
		}
		return 0
	}
	if ac, aIsC := a.(charValue); aIsC {
		if bc, bIsC := b.(charValue); bIsC {
			if ac < bc {
				return -1
			}
			if ac > bc {
				return 1
			}
			return 0
		}
	}
	panic(runtimeErr("cannot compare %s and %s", typeNameOf(a), typeNameOf(b)))
}

// stableHash mirrors the Python _stable_hash (FNV-1a of the string form).
func stableHash(v any) int64 {
	s := solvikString(v)
	const offset = int64(-3750763034362895579)
	const prime = int64(1099511628211)
	h := offset
	for i := 0; i < len(s); i++ {
		h ^= int64(s[i])
		h *= prime
	}
	return h
}

func invokeCallable(fn any, args []any) any {
	if in := theInterpreter; in != nil {
		v, err := in.callValue(fn, args, false, nil, nil)
		if err != nil {
			panic(err)
		}
		return v
	}
	panic(runtimeErr("interpreter unavailable"))
}

// ---- conversions ----

func toInt(v any) any {
	switch x := v.(type) {
	case *enumValue:
		if len(x.payload) > 0 {
			panic(runtimeErrCode("E066", "cannot convert payload enum value %s.%s to int", x.enumName, x.memberName))
		}
		return x.value
	case charValue:
		return int64([]rune(x)[0])
	case *byteValue:
		return x.v
	case string:
		n, err := parseIntStrict(x)
		if err != nil {
			panic(runtimeErrCode("E073", "cannot convert '%s' to int", x))
		}
		return n
	case float64:
		return int64(x)
	case int64:
		return x
	}
	panic(runtimeErrCode("E073", "cannot convert '%s' to int", solvikString(v)))
}

func parseIntStrict(s string) (int64, error) {
	var n int64
	if _, err := fmt.Sscanf(s, "%d", &n); err != nil {
		return 0, err
	}
	return n, nil
}

func toFloat(v any) any {
	switch x := v.(type) {
	case int64:
		return float64(x)
	case float64:
		return x
	case *byteValue:
		return float64(x.v)
	case string:
		var f float64
		if _, err := fmt.Sscanf(x, "%g", &f); err != nil {
			panic(runtimeErrCode("E073", "cannot convert '%s' to float", x))
		}
		return f
	}
	panic(runtimeErrCode("E073", "cannot convert '%s' to float", solvikString(v)))
}

func toByte(v any) any {
	var n int64
	switch x := v.(type) {
	case string:
		var f float64
		if _, err := fmt.Sscanf(x, "%g", &f); err != nil {
			panic(runtimeErrCode("E073", "cannot convert '%s' to byte", x))
		}
		n = int64(f)
	case int64:
		n = x
	case float64:
		n = int64(x)
	case *byteValue:
		n = x.v
	default:
		panic(runtimeErrCode("E073", "cannot convert '%s' to byte", solvikString(v)))
	}
	if n < 0 || n > 255 {
		panic(runtimeErrCode("E073", "byte conversion out of range"))
	}
	return &byteValue{v: n}
}

func toBool(v any) any {
	switch x := v.(type) {
	case bool:
		return x
	case string:
		return strings.ToLower(x) == "true"
	case *byteValue:
		return x.v != 0
	case int64:
		return x != 0
	case float64:
		return x != 0
	case charValue:
		return len([]rune(x)) > 0 && []rune(x)[0] != 0
	case []any:
		return len(x) > 0
	case *solvikMap:
		return len(x.entries) > 0
	case *stackValue:
		return len(x.items) > 0
	case nil:
		return false
	default:
		return true
	}
}

// ---- stdlib helpers ----

func jsonParse(s any) any {
	var out any
	dec := json.NewDecoder(strings.NewReader(s.(string)))
	dec.UseNumber()
	if err := dec.Decode(&out); err != nil {
		panic(runtimeErrCode("E072", "json parse error: %v", err))
	}
	return convertJSON(out)
}

func convertJSON(v any) any {
	switch x := v.(type) {
	case json.Number:
		if i, err := x.Int64(); err == nil {
			return i
		}
		f, _ := x.Float64()
		return f
	case map[string]any:
		out := newSolvikMap()
		for k, val := range x {
			out.set(k, convertJSON(val))
		}
		return out
	case []any:
		out := make([]any, len(x))
		for i, val := range x {
			out[i] = convertJSON(val)
		}
		return out
	}
	return v
}

func jsonCompatible(v any) any {
	switch x := v.(type) {
	case *solvikMap:
		out := map[string]any{}
		for _, entry := range x.entriesInOrder() {
			out[mapKeyString(entry.key)] = jsonCompatible(entry.value)
		}
		return out
	case []any:
		out := make([]any, len(x))
		for i, item := range x {
			out[i] = jsonCompatible(item)
		}
		return out
	case *stackValue:
		out := make([]any, len(x.items))
		for i, item := range x.items {
			out[i] = jsonCompatible(item)
		}
		return out
	default:
		return v
	}
}

func jsonStringify(v any) any {
	out, err := json.Marshal(jsonCompatible(v))
	if err != nil {
		panic(runtimeErrCode("E072", "json stringify error: %v", err))
	}
	return string(out)
}

func httpRequest(method, url string, body any, headers *solvikMap) *solvikMap {
	var reader io.Reader
	if body != nil {
		if s, ok := body.(string); ok {
			reader = strings.NewReader(s)
		}
	}
	req, err := httpNewRequest(method, url, reader)
	if err != nil {
		panic(runtimeErrCode("E072", "http request failed: %v", err))
	}
	if headers != nil {
		for _, entry := range headers.entriesInOrder() {
			key, ok := entry.key.(string)
			if !ok {
				panic(runtimeErr("http headers require string keys"))
			}
			req.Header.Set(key, mapKeyString(entry.value))
		}
	}
	client := httpClient()
	resp, err := client.Do(req)
	if err != nil {
		panic(runtimeErrCode("E072", "http request failed: %v", err))
	}
	defer resp.Body.Close()
	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		panic(runtimeErrCode("E072", "http request failed: %v", err))
	}
	hdr := newSolvikMap()
	for k, vals := range resp.Header {
		if len(vals) > 0 {
			hdr.set(k, vals[0])
		}
	}
	out := newSolvikMap()
	out.set("status", int64(resp.StatusCode))
	out.set("body", string(bodyBytes))
	out.set("headers", hdr)
	return out
}

func buildBuiltins() map[string]any {
	core := map[string]any{}
	core["print"] = makeNative("print", func(args ...any) any {
		for _, a := range args {
			theInterpreter.print(solvikString(a))
		}
		return nil
	})
	core["println"] = makeNative("println", func(args ...any) any {
		for i, a := range args {
			if i > 0 {
				theInterpreter.print(" ")
			}
			theInterpreter.print(solvikString(a))
		}
		theInterpreter.print("\n")
		return nil
	})
	core["string"] = makeNative("string", func(args ...any) any { return solvikString(args[0]) })
	core["int"] = makeNative("int", func(args ...any) any { return toInt(args[0]) })
	core["float"] = makeNative("float", func(args ...any) any { return toFloat(args[0]) })
	core["byte"] = makeNative("byte", func(args ...any) any { return toByte(args[0]) })
	core["bool"] = makeNative("bool", func(args ...any) any { return toBool(args[0]) })
	core["typeOf"] = makeNative("typeOf", func(args ...any) any { return typeNameOf(args[0]) })
	core["isType"] = makeNative("isType", func(args ...any) any { return typeNameOf(args[0]) == args[1].(string) })
	core["regex"] = makeNative("regex", func(args ...any) any { return &regexValue{pattern: args[0].(string)} })
	core["stack"] = makeNative("stack", func(...any) any { return &stackValue{} })

	stringNs := &namespace{name: "string", values: map[string]any{}, callFn: func(args ...any) any {
		return solvikString(args[0])
	}}
	stringNs.values["join"] = makeNative("string.join", func(args ...any) any {
		xs := args[0].([]any)
		sep := args[1].(string)
		parts := make([]string, len(xs))
		for i, x := range xs {
			parts[i] = solvikString(x)
		}
		return strings.Join(parts, sep)
	})
	stringNs.values["convert"] = makeNative("string.convert", func(args ...any) any { return solvikString(args[0]) })
	stringNs.values["repeat"] = makeNative("string.repeat", func(args ...any) any { return strings.Repeat(args[0].(string), int(toIntLike(args[1]))) })
	stringNs.values["padStart"] = makeNative("string.padStart", func(args ...any) any {
		s := args[0].(string)
		w := int(toIntLike(args[1]))
		pad := " "
		if len(args) > 2 {
			pad = args[2].(string)
		}
		if len(s) >= w {
			return s
		}
		return strings.Repeat(pad, w-len(s)) + s
	})
	stringNs.values["padEnd"] = makeNative("string.padEnd", func(args ...any) any {
		s := args[0].(string)
		w := int(toIntLike(args[1]))
		pad := " "
		if len(args) > 2 {
			pad = args[2].(string)
		}
		if len(s) >= w {
			return s
		}
		return s + strings.Repeat(pad, w-len(s))
	})

	mathNs := &namespace{name: "math", values: map[string]any{}}
	mathNs.values["abs"] = makeNative("math.abs", func(args ...any) any {
		switch n := numericValue(args[0]).(type) {
		case int64:
			if n < 0 {
				return -n
			}
			return n
		case float64:
			return math.Abs(n)
		}
		return args[0]
	})
	mathNs.values["min"] = makeNative("math.min", func(args ...any) any { return mathMin(args[0], args[1]) })
	mathNs.values["max"] = makeNative("math.max", func(args ...any) any { return mathMax(args[0], args[1]) })
	mathNs.values["floor"] = makeNative("math.floor", func(args ...any) any { return float64(math.Floor(toFloat(args[0]).(float64))) })
	mathNs.values["ceil"] = makeNative("math.ceil", func(args ...any) any { return float64(math.Ceil(toFloat(args[0]).(float64))) })
	mathNs.values["round"] = makeNative("math.round", func(args ...any) any { return float64(math.Floor(toFloat(args[0]).(float64) + 0.5)) })
	mathNs.values["sqrt"] = makeNative("math.sqrt", func(args ...any) any { return math.Sqrt(toFloat(args[0]).(float64)) })
	mathNs.values["pow"] = makeNative("math.pow", func(args ...any) any { return math.Pow(toFloat(args[0]).(float64), toFloat(args[1]).(float64)) })
	mathNs.values["sin"] = makeNative("math.sin", func(args ...any) any { return math.Sin(toFloat(args[0]).(float64)) })
	mathNs.values["cos"] = makeNative("math.cos", func(args ...any) any { return math.Cos(toFloat(args[0]).(float64)) })
	mathNs.values["tan"] = makeNative("math.tan", func(args ...any) any { return math.Tan(toFloat(args[0]).(float64)) })
	mathNs.values["PI"] = math.Pi
	mathNs.values["E"] = math.E

	envNs := &namespace{name: "env", values: map[string]any{}}
	envNs.values["get"] = makeNative("env.get", func(args ...any) any { return os.Getenv(args[0].(string)) })
	envNs.values["set"] = makeNative("env.set", func(args ...any) any { return os.Setenv(args[0].(string), args[1].(string)) })
	envNs.values["keys"] = makeNative("env.keys", func(...any) any {
		keys := []string{}
		for _, kv := range os.Environ() {
			parts := strings.SplitN(kv, "=", 2)
			keys = append(keys, parts[0])
		}
		out := make([]any, len(keys))
		for i, k := range keys {
			out[i] = k
		}
		return out
	})

	fileNs := &namespace{name: "file", values: map[string]any{}}
	fileNs.values["read"] = makeNative("file.read", func(args ...any) any {
		b, err := os.ReadFile(args[0].(string))
		if err != nil {
			panic(runtimeErrCode("E072", "file read failed: %v", err))
		}
		return string(b)
	})
	fileNs.values["write"] = makeNative("file.write", func(args ...any) any {
		return os.WriteFile(args[0].(string), []byte(args[1].(string)), 0o644)
	})
	fileNs.values["append"] = makeNative("file.append", func(args ...any) any {
		f, err := os.OpenFile(args[0].(string), os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0o644)
		if err != nil {
			panic(runtimeErrCode("E072", "file append failed: %v", err))
		}
		defer f.Close()
		_, err = f.WriteString(args[1].(string))
		return err
	})
	fileNs.values["delete"] = makeNative("file.delete", func(args ...any) any { return os.Remove(args[0].(string)) })
	fileNs.values["remove"] = makeNative("file.remove", func(args ...any) any { return os.Remove(args[0].(string)) })
	fileNs.values["exists"] = makeNative("file.exists", func(args ...any) any { _, err := os.Stat(args[0].(string)); return err == nil })
	fileNs.values["temp"] = makeNative("file.temp", func(args ...any) any {
		f, err := os.CreateTemp("", mapKeyString(args[0]))
		if err != nil {
			panic(runtimeErrCode("E072", "temp file failed: %v", err))
		}
		name := f.Name()
		f.Close()
		return name
	})
	fileNs.values["tempDir"] = makeNative("file.tempDir", func(args ...any) any {
		dir, err := os.MkdirTemp("", mapKeyString(args[0]))
		if err != nil {
			panic(runtimeErrCode("E072", "temp dir failed: %v", err))
		}
		return dir
	})
	fileNs.values["list"] = makeNative("file.list", func(args ...any) any {
		entries, err := os.ReadDir(args[0].(string))
		if err != nil {
			panic(runtimeErrCode("E072", "file list failed: %v", err))
		}
		out := make([]any, len(entries))
		for i, e := range entries {
			out[i] = e.Name()
		}
		return out
	})
	fileNs.values["mkdir"] = makeNative("file.mkdir", func(args ...any) any {
		return os.MkdirAll(args[0].(string), 0o755)
	})
	fileNs.values["isFile"] = makeNative("file.isFile", func(args ...any) any {
		info, err := os.Stat(args[0].(string))
		return err == nil && !info.IsDir()
	})
	fileNs.values["isDir"] = makeNative("file.isDir", func(args ...any) any {
		info, err := os.Stat(args[0].(string))
		return err == nil && info.IsDir()
	})
	fileNs.values["size"] = makeNative("file.size", func(args ...any) any {
		info, err := os.Stat(args[0].(string))
		if err != nil {
			panic(runtimeErrCode("E072", "file size failed: %v", err))
		}
		return info.Size()
	})
	fileNs.values["rename"] = makeNative("file.rename", func(args ...any) any {
		return os.Rename(args[0].(string), args[1].(string))
	})

	processNs := &namespace{name: "process", values: map[string]any{}}
	processNs.values["run"] = makeNative("process.run", func(args ...any) any {
		return processRun(args)
	})
	processNs.values["capture"] = makeNative("process.capture", func(args ...any) any {
		return processCapture(args)
	})
	processNs.values["args"] = makeNative("process.args", func(...any) any {
		out := make([]any, len(programArgs))
		for i, a := range programArgs {
			out[i] = a
		}
		return out
	})

	timeNs := &namespace{name: "time", values: map[string]any{}}
	timeNs.values["now"] = makeNative("time.now", func(...any) any { return time.Now().UnixMilli() })
	timeNs.values["sleep"] = makeNative("time.sleep", func(args ...any) any {
		time.Sleep(time.Duration(toIntLike(args[0])) * time.Millisecond)
		return nil
	})
	timeNs.values["iso"] = makeNative("time.iso", func(args ...any) any {
		return time.UnixMilli(toIntLike(args[0])).UTC().Format("2006-01-02T15:04:05Z")
	})
	timeNs.values["parse"] = makeNative("time.parse", func(args ...any) any {
		t, err := time.Parse("2006-01-02T15:04:05Z07:00", args[0].(string))
		if err != nil {
			panic(runtimeErrCode("E072", "time parse failed: %v", err))
		}
		return t.UnixMilli()
	})

	randomNs := &namespace{name: "random", values: map[string]any{}}
	randomNs.values["float"] = makeNative("random.float", func(...any) any { return solvikRandom.Float64() })
	randomNs.values["int"] = makeNative("random.int", func(args ...any) any {
		lo, hi := toIntLike(args[0]), toIntLike(args[1])
		if hi < lo {
			panic(runtimeErr("empty random.int range"))
		}
		return solvikRandom.Int63n(hi-lo+1) + lo
	})
	randomNs.values["range"] = makeNative("random.range", func(args ...any) any {
		var lo, hi int64
		if len(args) == 1 {
			lo, hi = 0, toIntLike(args[0])
		} else {
			lo, hi = toIntLike(args[0]), toIntLike(args[1])
		}
		if hi <= lo {
			panic(runtimeErr("empty random.range"))
		}
		return solvikRandom.Int63n(hi-lo) + lo
	})
	randomNs.values["uniform"] = makeNative("random.uniform", func(args ...any) any {
		return toFloat(args[0]).(float64) + solvikRandom.Float64()*(toFloat(args[1]).(float64)-toFloat(args[0]).(float64))
	})
	randomNs.values["choice"] = makeNative("random.choice", func(args ...any) any {
		xs := args[0].([]any)
		if len(xs) == 0 {
			return nil
		}
		return copyValue(xs[solvikRandom.Intn(len(xs))])
	})
	randomNs.values["shuffle"] = makeNative("random.shuffle", func(args ...any) any {
		xs := copyValue(args[0]).([]any)
		solvikRandom.Shuffle(len(xs), func(i, j int) { xs[i], xs[j] = xs[j], xs[i] })
		return xs
	})
	randomNs.values["sample"] = makeNative("random.sample", func(args ...any) any {
		xs := args[0].([]any)
		k := int(toIntLike(args[1]))
		if k < 0 {
			k = 0
		}
		out := make([]any, 0, k)
		perm := solvikRandom.Perm(len(xs))
		for i := 0; i < k && i < len(xs); i++ {
			out = append(out, copyValue(xs[perm[i]]))
		}
		return out
	})
	randomNs.values["seed"] = makeNative("random.seed", func(args ...any) any {
		solvikRandom.Seed(toIntLike(args[0]))
		return nil
	})

	pathNs := &namespace{name: "path", values: map[string]any{}}
	pathNs.values["join"] = makeNative("path.join", func(args ...any) any {
		parts := make([]string, len(args))
		for i, a := range args {
			parts[i] = mapKeyString(a)
		}
		return filepath.Join(parts...)
	})
	pathNs.values["basename"] = makeNative("path.basename", func(args ...any) any { return filepath.Base(args[0].(string)) })
	pathNs.values["dirname"] = makeNative("path.dirname", func(args ...any) any { return filepath.Dir(args[0].(string)) })
	pathNs.values["ext"] = makeNative("path.ext", func(args ...any) any { return filepath.Ext(args[0].(string)) })
	pathNs.values["abs"] = makeNative("path.abs", func(args ...any) any {
		p, err := filepath.Abs(args[0].(string))
		if err != nil {
			panic(runtimeErrCode("E072", "path abs failed: %v", err))
		}
		return p
	})
	pathNs.values["exists"] = makeNative("path.exists", func(args ...any) any {
		_, err := os.Stat(args[0].(string))
		return err == nil
	})

	base64Ns := &namespace{name: "base64", values: map[string]any{}}
	base64Ns.values["encode"] = makeNative("base64.encode", func(args ...any) any {
		return base64.StdEncoding.EncodeToString([]byte(args[0].(string)))
	})
	base64Ns.values["decode"] = makeNative("base64.decode", func(args ...any) any {
		b, err := base64.StdEncoding.DecodeString(args[0].(string))
		if err != nil {
			panic(runtimeErrCode("E072", "base64 decode failed: %v", err))
		}
		return string(b)
	})

	hashNs := &namespace{name: "hash", values: map[string]any{}}
	hashNs.values["md5"] = makeNative("hash.md5", func(args ...any) any {
		h := md5.Sum([]byte(args[0].(string)))
		return hex.EncodeToString(h[:])
	})
	hashNs.values["sha1"] = makeNative("hash.sha1", func(args ...any) any {
		h := sha1.Sum([]byte(args[0].(string)))
		return hex.EncodeToString(h[:])
	})
	hashNs.values["sha256"] = makeNative("hash.sha256", func(args ...any) any {
		h := sha256.Sum256([]byte(args[0].(string)))
		return hex.EncodeToString(h[:])
	})
	hashNs.values["sha512"] = makeNative("hash.sha512", func(args ...any) any {
		h := sha512.Sum512([]byte(args[0].(string)))
		return hex.EncodeToString(h[:])
	})

	secretsNs := &namespace{name: "secrets", values: map[string]any{}}
	secretsNs.values["token"] = makeNative("secrets.token", func(args ...any) any {
		n := int(toIntLike(args[0]))
		if n < 0 {
			panic(runtimeErr("negative token length"))
		}
		buf := make([]byte, n)
		if _, err := cryptorand.Read(buf); err != nil {
			panic(runtimeErrCode("E072", "secure token generation failed: %v", err))
		}
		return base64.RawURLEncoding.EncodeToString(buf)
	})
	secretsNs.values["hex"] = makeNative("secrets.hex", func(args ...any) any {
		b := make([]byte, int(toIntLike(args[0])))
		if _, err := cryptorand.Read(b); err != nil {
			panic(runtimeErrCode("E072", "secure token generation failed: %v", err))
		}
		return hex.EncodeToString(b)
	})

	jsonNs := &namespace{name: "json", values: map[string]any{}}
	jsonNs.values["parse"] = makeNative("json.parse", func(args ...any) any { return jsonParse(args[0]) })
	jsonNs.values["stringify"] = makeNative("json.stringify", func(args ...any) any { return jsonStringify(args[0]) })

	httpNs := &namespace{name: "http", values: map[string]any{}}
	httpNs.values["get"] = makeNative("http.get", func(args ...any) any { return httpRequest("GET", args[0].(string), nil, nil) })
	httpNs.values["post"] = makeNative("http.post", func(args ...any) any { return httpRequest("POST", args[0].(string), args[1], nil) })
	httpNs.values["request"] = makeNative("http.request", func(args ...any) any {
		headers, _ := args[3].(*solvikMap)
		return httpRequest(args[0].(string), args[1].(string), args[2], headers)
	})

	testNs := &namespace{name: "test", values: map[string]any{}}
	testNs.values["assert"] = makeNative("test.assert", func(args ...any) any {
		cond := args[0]
		msg := ""
		if len(args) > 1 {
			msg = solvikString(args[1])
		}
		if !boolOf(cond) {
			panic(runtimeErrCode("E071", "assertion failed: %s", msg))
		}
		return nil
	})
	testNs.values["assertTrue"] = testNs.values["assert"]
	testNs.values["assertFalse"] = makeNative("test.assertFalse", func(args ...any) any {
		if boolOf(args[0]) {
			panic(runtimeErrCode("E071", "assertion failed: %s", msgOrEmpty(args, 1)))
		}
		return nil
	})
	testNs.values["assertEq"] = makeNative("test.assertEq", func(args ...any) any {
		if !theInterpreter.equal(args[0], args[1]) {
			panic(runtimeErrCode("E071", "assertion failed: expected %s equal to %s %s", solvikString(args[0]), solvikString(args[1]), msgOrEmpty(args, 2)))
		}
		return nil
	})
	testNs.values["assertNe"] = makeNative("test.assertNe", func(args ...any) any {
		if theInterpreter.equal(args[0], args[1]) {
			panic(runtimeErrCode("E071", "assertion failed: expected %s not equal to %s %s", solvikString(args[0]), solvikString(args[1]), msgOrEmpty(args, 2)))
		}
		return nil
	})
	testNs.values["assertNull"] = makeNative("test.assertNull", func(args ...any) any {
		if args[0] != nil {
			panic(runtimeErrCode("E071", "assertion failed: expected null but got %s %s", typeNameOf(args[0]), msgOrEmpty(args, 1)))
		}
		return nil
	})

	core["string"] = stringNs
	core["math"] = mathNs
	core["env"] = envNs
	core["file"] = fileNs
	core["process"] = processNs
	core["time"] = timeNs
	core["random"] = randomNs
	core["path"] = pathNs
	core["base64"] = base64Ns
	core["hash"] = hashNs
	core["secrets"] = secretsNs
	core["json"] = jsonNs
	core["http"] = httpNs
	core["test"] = testNs
	return core
}

func msgOrEmpty(args []any, i int) string {
	if len(args) > i {
		return solvikString(args[i])
	}
	return ""
}

func mathMin(a, b any) any {
	if toFloat(a).(float64) < toFloat(b).(float64) {
		return a
	}
	return b
}

func mathMax(a, b any) any {
	if toFloat(a).(float64) > toFloat(b).(float64) {
		return a
	}
	return b
}
