// try with a returning body and finally (no catch) is a valid returning function.
package conformance

func f() -> int {
    try {
        return 10
    } finally {
        println("cleanup")
    }
}

func main() -> int {
    return f()
}
