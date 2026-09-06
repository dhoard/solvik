// expected E072: reduce of an empty list is a standard-library error
package runtime_errors

func main() -> Int {
    xs: List<Int> = []
    println(xs.reduce(func(a: Int, b: Int) -> Int { return a + b }))
    return 0
}
