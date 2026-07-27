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

package runtime

import (
	"context"
	"testing"
)

// BenchmarkFullProgram measures the end-to-end compile-and-execute time
// for a complete small program.
func BenchmarkFullProgram(b *testing.B) {
	opts := DefaultOptions()
	source := `package example
func main() -> int {
    mut count: int = 0
    count = count + 1
    print("Hello from language!\n")
    return 0
}
`
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		result := CompileAndExecute("test.sol", source, opts)
		if result.Error != nil {
			b.Fatal("error:", result.Error)
		}
	}
}

// BenchmarkLargeProgram measures performance on a larger program with
// loops, conditionals, and function calls.
func BenchmarkLargeProgram(b *testing.B) {
	opts := DefaultOptions()
	source := `package example
func factorial(n: int) -> long {
    if n <= 1 {
        return 1
    }
    return n * factorial(n - 1)
}
func sumTo(n: int) -> int {
    total: int = 0
    i: int = 1
    while i <= n {
        total = total + i
        i = i + 1
    }
    return total
}
func main() -> int {
    a: long = factorial(10)
    b: int = sumTo(100)
    return 0
}
`
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		result := CompileAndExecute("test.sol", source, opts)
		if result.Error != nil {
			b.Fatal("error:", result.Error)
		}
	}
}

// BenchmarkStringConcatenation measures string concat performance.
func BenchmarkStringConcatenation(b *testing.B) {
	opts := DefaultOptions()
	source := `package example
func main() -> int {
    s: string = ""
    mut i: int = 0
    while i < 100 {
        s = s + "x"
        i = i + 1
    }
    return 0
}
`
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		result := CompileAndExecute("test.sol", source, opts)
		if result.Error != nil {
			b.Fatal("error:", result.Error)
		}
	}
}

// BenchmarkDeepRecursion measures function call overhead.
func BenchmarkDeepRecursion(b *testing.B) {
	opts := DefaultOptions()
	source := `package example
func recurse(n: int) -> int {
    if n <= 0 {
        return 0
    }
    return 1 + recurse(n - 1)
}
func main() -> int {
    return recurse(50)
}
`
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		result := CompileAndExecute("test.sol", source, opts)
		if result.Error != nil {
			b.Fatal("error:", result.Error)
		}
	}
}

// BenchmarkLoopOnly measures tight loop overhead (10k iterations).
func BenchmarkLoopOnly(b *testing.B) {
	opts := DefaultOptions()
	source := `package example
func main() -> int {
    mut i: int = 0
    while i < 10000 {
        i = i + 1
    }
    return i
}
`
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		result := CompileAndExecute("test.sol", source, opts)
		if result.Error != nil {
			b.Fatal("error:", result.Error)
		}
	}
}

// BenchmarkNoOp measures baseline interpreter overhead.
func BenchmarkNoOp(b *testing.B) {
	opts := DefaultOptions()
	source := `package example
func main() -> int {
    return 0
}
`
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		result := CompileAndExecute("test.sol", source, opts)
		if result.Error != nil {
			b.Fatal("error:", result.Error)
		}
	}
}

// BenchmarkCompileOnly measures compilation time (no execution).
func BenchmarkCompileOnly(b *testing.B) {
	source := `package example
func main() -> int {
    mut count: int = 0
    count = count + 1
    print("Hello from language!\n")
    return 0
}
`
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		_, _, err := Compile("test.sol", source)
		if err != nil {
			b.Fatal("error:", err)
		}
	}
}

// BenchmarkExecuteOnly measures execution time without compilation.
func BenchmarkExecuteOnly(b *testing.B) {
	source := `package example
func main() -> int {
    mut i: int = 0
    while i < 10000 {
        i = i + 1
    }
    return i
}
`
	prog, diags, err := Compile("test.sol", source)
	if err != nil || (diags != nil && diags.HasErrors()) {
		b.Fatalf("compile error: %v %v", err, diags)
	}

	opts := DefaultOptions()

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		val, execErr := Execute(context.Background(), prog, opts)
		if execErr != nil {
			b.Fatal("execute error:", execErr)
		}
		if val.Int() != 10000 {
			b.Fatalf("expected 10000, got %d", val.Int())
		}
	}
}
