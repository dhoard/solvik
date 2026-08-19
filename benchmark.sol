// benchmark.sol — deterministic, CPU-bound benchmark for the solvik
// interpreters.
//
// The program exercises every major solvik language construct — primitive
// types, arithmetic/bitwise/boolean operators, strings, lists, maps, stacks,
// structs, methods, traits, enums, switch (including regex cases), loops,
// break/continue, exceptions, nullability, `any`, recursion, `use`
// dependencies, and the standard conversion/type functions — in loops large
// enough to produce stable timings.
//
// It intentionally avoids I/O, time, random, environment, and filesystem
// access, so every run performs exactly the same work and returns the same
// result from both the Go and Rust interpreters.
//
// The `use file:lib.format` dependency resolves to ./lib/format.sol relative
// to this file, so run the benchmark from the repository root (benchmark.sh
// does this automatically).

package benchmark

use file:lib.format

// ---------------------------------------------------------------------------
// Types used by the benchmark
// ---------------------------------------------------------------------------

enum Level {
    Low,
    Medium,
    High,
}

trait Named {
    func name() -> string
}

struct Counter {
    pub mut value: int,
    label: string,

    pub func name() -> string {
        return label
    }

    pub mut func increment(n: int) {
        value = value + n
    }
}

// ---------------------------------------------------------------------------
// Primitive types and operators
// ---------------------------------------------------------------------------

func benchPrimitives(iterations: int) -> int {
    mut total: int = 0

    // Numeric literal forms (hex, binary, octal, underscored) and a char
    // literal, exercised once so the parser/compiler covers them.
    hex: int = 0xFF
    bin: int = 0b1010
    oct: int = 0o17
    underscored: int = 1_000
    z: char = 'Z'
    total = total + hex + bin + oct + underscored + int(z)

    mut flag: bool = true
    mut i: int = 0
    while i < iterations {
        b: byte = byte(i % 256)
        total = total + int(b)

        ch: char = "abc".charAt(i % 3)
        total = total + int(ch)

        if flag && i % 2 == 0 {
            total = total + 1
        }
        flag = !flag

        i = i + 1
    }
    return total
}

func benchArithmetic(iterations: int) -> int {
    mut acc: int = 0
    mut i: int = 0
    while i < iterations {
        acc = acc + i * 3
        acc = acc - i / 2
        acc = acc + i % 7
        acc = acc | (i & 15)
        acc = acc ^ (i << 1)
        i = i + 1
    }
    return acc
}

func benchFloat(iterations: int) -> int {
    mut f: float = 1.0
    mut i: int = 0
    while i < iterations {
        f = f * 1.000001
        f = f + math.sqrt(f)
        i = i + 1
    }

    // Keep the float result live without making the exit code depend on
    // floating-point rounding, which could differ between implementations.
    if f < 0.0 {
        return 1
    }
    return 0
}

// ---------------------------------------------------------------------------
// Loops: while, for-in, break, continue
// ---------------------------------------------------------------------------

func benchLoops(iterations: int) -> int {
    values: list<int> = [1, 2, 3, 4, 5]
    mut total: int = 0
    mut i: int = 0
    while i < iterations {
        i = i + 1
        if i % 3 == 0 {
            continue
        }

        mut j: int = 0
        while j < values.len() {
            total = total + values[j]
            j = j + 1
        }

        if total > 1000000000 {
            break
        }
    }
    return total
}

// ---------------------------------------------------------------------------
// Collections: lists, maps, stacks
// ---------------------------------------------------------------------------

func benchCollections(iterations: int) -> int {
    mut s: stack<int> = stack()
    m: map<string, int> = {"a": 1, "b": 2, "c": 3}
    mut total: int = 0
    mut i: int = 0
    while i < iterations {
        s.push(i)
        top: int = s.peek()
        total = total + top
        if s.len() >= 4 {
            popped: int = s.pop()
            total = total + popped
        }

        total = total + m["a"] + m["b"] + m["c"]

        for k, v in m {
            total = total + v + k.len()
        }

        i = i + 1
    }
    return total
}

// ---------------------------------------------------------------------------
// Strings
// ---------------------------------------------------------------------------

func benchStrings(iterations: int) -> int {
    mut s: string = "benchmark"
    mut total: int = 0
    mut i: int = 0
    while i < iterations {
        s = s.substring(0, 5) .. ":" .. string(i)
        total = total + s.len()
        if s.contains(":") {
            total = total + 1
        }
        total = total + s.indexOf(":")
        total = total + s.toUpper().len()
        total = total + format.greetFromLib("x").len()
        i = i + 1
    }
    return total
}

// ---------------------------------------------------------------------------
// Structs, methods, and traits
// ---------------------------------------------------------------------------

func benchStructsAndTraits(iterations: int) -> int {
    mut c: Counter = Counter { value: 0, label: "count" }
    mut total: int = 0
    mut i: int = 0
    while i < iterations {
        c.increment(i)
        total = total + c.value

        n: Named = c
        total = total + n.name().len()

        q: Counter = Counter { value: i, label: "x" }
        if c != q {
            total = total + 1
        }

        i = i + 1
    }
    return total
}

// ---------------------------------------------------------------------------
// Enums, switch, and regex cases
// ---------------------------------------------------------------------------

func levelFor(i: int) -> Level {
    if i % 3 == 0 {
        return Level.Low
    }
    if i % 3 == 1 {
        return Level.Medium
    }
    return Level.High
}

func classifyInt(code: int) -> string {
    switch code {
        case 0 {
            return "zero"
        }
        case 1 {
            return "one"
        }
        case 2 {
            return "two"
        }
        default {
            return "many"
        }
    }
}

func logFor(i: int) -> string {
    if i % 3 == 0 {
        return "ERROR: boom"
    }
    if i % 3 == 1 {
        return "WARN: careful"
    }
    return "INFO: ok"
}

func classifyLog(entry: string) -> string {
    switch entry {
        case regex(r"^ERROR") {
            return "error"
        }
        case regex(r"^WARN") {
            return "warn"
        }
        case "INFO" {
            return "info"
        }
        default {
            return "other"
        }
    }
}

func benchEnumsAndSwitch(iterations: int) -> int {
    mut total: int = 0
    mut i: int = 0
    while i < iterations {
        lvl: Level = levelFor(i)
        switch lvl {
            case Level.Low {
                total = total + 1
            }
            case Level.Medium {
                total = total + 2
            }
            case Level.High {
                total = total + 3
            }
            default {
                total = total + 99
            }
        }

        total = total + classifyInt(i % 4).len()
        total = total + classifyLog(logFor(i)).len()

        i = i + 1
    }
    return total
}

// ---------------------------------------------------------------------------
// Exceptions
// ---------------------------------------------------------------------------

func benchExceptions(iterations: int) -> int {
    mut total: int = 0
    mut i: int = 0
    while i < iterations {
        try {
            if i % 5 == 0 {
                throw "boom"
            }
            total = total + i
        } catch (e: exception) {
            total = total + e.message.len()
        } finally {
            total = total + 1
        }

        i = i + 1
    }
    return total
}

// ---------------------------------------------------------------------------
// Nullability, coalescing, and any
// ---------------------------------------------------------------------------

func benchNullabilityAndAny(iterations: int) -> int {
    mut total: int = 0
    mut i: int = 0
    while i < iterations {
        v: any = i
        if isType(v, "int") {
            n: int = v
            total = total + n
        }
        total = total + typeOf(v).len()

        mut maybe: int? = null
        if i % 2 == 0 {
            maybe = i
        }
        total = total + (maybe ?? 1000)
        switch maybe {
            case null {
                total = total + 1
            }
            default {
                total = total + 2
            }
        }

        i = i + 1
    }
    return total
}

// ---------------------------------------------------------------------------
// Recursion
// ---------------------------------------------------------------------------

func fib(n: int) -> int {
    if n <= 1 {
        return n
    }
    return fib(n - 1) + fib(n - 2)
}

// ---------------------------------------------------------------------------
// Entry point
// ---------------------------------------------------------------------------

func main() -> int {
    if benchPrimitives(60000) != 13553690 {
        return 1
    }
    if benchArithmetic(60000) != 4502015345 {
        return 2
    }
    if benchFloat(60000) != 0 {
        return 3
    }
    if benchLoops(60000) != 600000 {
        return 4
    }
    if benchCollections(30000) != 900419997 {
        return 5
    }
    if benchStrings(30000) != 1687780 {
        return 6
    }
    if benchStructsAndTraits(30000) != 4500000175000 {
        return 7
    }
    if benchEnumsAndSwitch(6000) != 61000 {
        return 8
    }
    if benchExceptions(15000) != 90027000 {
        return 9
    }
    if benchNullabilityAndAny(30000) != 690105000 {
        return 10
    }
    if fib(26) != 121393 {
        return 11
    }

    println("benchmark complete")
    return 0
}
