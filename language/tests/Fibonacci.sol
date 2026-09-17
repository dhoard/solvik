// Solvik Fibonacci sample: the Phase 5 executable core (typed functions, recursion, println).
func fib(n: Int): Int {
    if (n < 2) {
        return n
    }
    return fib(n - 1) + fib(n - 2)
}

func main(): Unit {
    println(fib(10))
}
