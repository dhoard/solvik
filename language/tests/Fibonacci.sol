// Solvik Fibonacci sample: the Phase 5 executable core (typed functions, recursion, println).
func fib(n: Integer): Integer {
    if (n < 2) {
        return n
    }
    return fib(n - 1) + fib(n - 2)
}

println(fib(10))
