// expected E072: reduce of an empty list is a standard-library error
package runtime_errors

func main() -> int {
    xs: list<int> = []
    println(xs.reduce(func(a: int, b: int) -> int { return a + b }))
    return 0
}
