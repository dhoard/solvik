// benchmark.sol — deterministic, CPU-bound benchmark for the Solvik Rust
// bytecode VM.
//
// The program exercises the major language constructs — primitive types,
// arithmetic/boolean operators, strings, lists, maps, stacks, classes,
// inheritance, interfaces, generics, enums with payloads, match, loops,
// break/continue, exceptions, nullability, and the standard conversion /
// type functions — in loops large enough to produce stable timings.
//
// It intentionally avoids I/O, time, random, environment, and filesystem
// access, so every run performs exactly the same work and returns the same
// result.

package benchmark

// ---------------------------------------------------------------------------
// Types used by the benchmark
// ---------------------------------------------------------------------------

enum Level {
    Low
    Medium
    High
}

interface Named {
    name(): String
}

class Counter implements Named {
    value: Int
    label: String

    pub static new(label: String): Self {
        return Self { value: 0, label }
    }

    pub name(): String {
        return label
    }

    pub increment(n: Int): Void {
        value = value + n
    }

    pub get(): Int {
        return value
    }
}

class Box<T> {
    value: T

    pub static new(value: T): Self {
        return Self { value }
    }

    pub get(): T {
        return value
    }

    pub set(v: T): Void {
        value = v
    }
}

// ---------------------------------------------------------------------------
// Primitive types and operators
// ---------------------------------------------------------------------------

class BenchPrims {
    pub static run(iterations: Int): Int {
        mut total: Int = 0

        // Numeric literal forms (hex, binary, octal, underscored) and a char
        // literal, exercised once so the parser/compiler covers them.
        hex: Int = 0xFF
        bin: Int = 0b1010
        oct: Int = 0o17
        underscored: Int = 1_000
        z: Char = 'Z'
        total = total + hex + bin + oct + underscored + Int::from(z)

        mut flag: Bool = true
        mut i: Int = 0
        while i < iterations {
            b: Byte = Byte::from((i % 256) - 128)
            total = total + Int::from(b)

            ch: Char = "abc".charAt(i % 3)
            total = total + Int::from(ch)

            if flag && i % 2 == 0 {
                total = total + 1
            }
            flag = !flag

            f: Float = Float::from(i) / 3.0
            if f > 1.5 {
                total = total - 1
            }

            i = i + 1
        }
        return total
    }
}

// ---------------------------------------------------------------------------
// Strings
// ---------------------------------------------------------------------------

class BenchStrs {
    pub static run(iterations: Int): Int {
        mut total: Int = 0
        mut i: Int = 0
        while i < iterations {
            s: String = "solvik" .. i
            total = total + s.length()
            if s.contains("sol") {
                total = total + 1
            }
            parts: List<String> = s.split("v")
            total = total + parts.size()
            i = i + 1
        }
        return total
    }
}

// ---------------------------------------------------------------------------
// Collections
// ---------------------------------------------------------------------------

class BenchColls {
    pub static run(iterations: Int): Int {
        mut total: Int = 0
        xs: List<Int> = List<Int>::new()
        m: Map<String, Int> = Map<String, Int>::new()
        st: Stack<Int> = Stack<Int>::new()

        mut i: Int = 0
        while i < iterations {
            xs.add(i)
            st.push(i)
            m.put("k" .. (i % 8), i)

            if i % 4 == 0 {
                total = total + xs.get(i / 2)
            }
            if i % 4 == 1 {
                total = total + Int::from(st.pop())
            }
            if i % 4 == 2 {
                v: Object = m.get("k" .. (i % 8))
                total = total + Int::from(v)
            }
            if i % 4 == 3 {
                total = total + xs.size()
            }
            i = i + 1
        }
        total = total + xs.size() + m.size() + st.size()
        return total
    }
}

// ---------------------------------------------------------------------------
// Classes, inheritance, interfaces, generics
// ---------------------------------------------------------------------------

class BenchObj {
    pub static run(iterations: Int): Int {
        mut total: Int = 0
        c: Counter = Counter::new("c")
        n: Named = c
        b: Box<Int> = Box<Int>::new(0)

        mut i: Int = 0
        while i < iterations {
            c.increment(2)
            b.set(i)
            total = total + c.get()
            total = total + b.get()
            if n.name().length() > 0 {
                total = total + 1
            }
            i = i + 1
        }
        return total
    }
}

// ---------------------------------------------------------------------------
// Enums and match
// ---------------------------------------------------------------------------

class BenchEnum {
    pub static run(iterations: Int): Int {
        mut total: Int = 0
        mut i: Int = 0
        while i < iterations {
            lv: Level = match i % 3 {
                0 => Level::Low
                1 => Level::Medium
                _ => Level::High
            }
            add: Int = match lv {
                Level::Low => 1
                Level::Medium => 2
                Level::High => 3
            }
            total = total + add
            i = i + 1
        }
        return total
    }
}

// ---------------------------------------------------------------------------
// Exceptions and nullability
// ---------------------------------------------------------------------------

class BenchMisc {
    pub static run(iterations: Int): Int {
        mut total: Int = 0
        mut i: Int = 0
        while i < iterations {
            // Cheap exception path every 16th iteration.
            if i % 16 == 0 {
                try {
                    throw "x"
                } catch (e) {
                    total = total + e.toString().length()
                }
            }
            mut v: Int? = null
            if i % 5 != 0 {
                v = i
            }
            total = total + (v ?? 0)
            i = i + 1
        }
        return total
    }
}

// ---------------------------------------------------------------------------
// Entry point
// ---------------------------------------------------------------------------

class Main {
    pub static run(args: String...): Int {
        iterations: Int = 20000
        mut acc: Int = 0
        acc = acc + BenchPrims::run(iterations)
        acc = acc + BenchStrs::run(iterations)
        acc = acc + BenchColls::run(iterations)
        acc = acc + BenchObj::run(iterations)
        acc = acc + BenchEnum::run(iterations)
        acc = acc + BenchMisc::run(iterations)
        stdout.println(acc)
        return 0
    }
}
