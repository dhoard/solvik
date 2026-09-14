package bench.classic.fibonacci

// Naive recursive Fibonacci. No memoization: this is the canonical benchmark
// for recursive call/return machinery, frame setup, and integer arithmetic.
//
// Problem size: fib(25), repeated `repeat_count` times.
// Expected result: 75025 (validated in the harness; see benches/bench.rs).

struct Fib {

    public func fib(n: Long): Long {
        if n < 2 {
            return n
        }
        return Fib.fib(n - 1) + Fib.fib(n - 2)
    }
}

struct Main {

    public func run(args: String...): Integer {
        let repeat_count: Long = 1
        let n: Long = 25
        let mutable total: Long = 0
        let mutable i: Long = 0
        while i < repeat_count {
            total += Fib.fib(n)
            i += 1
        }
        return Integer.from(total % 1000003)
    }
}
