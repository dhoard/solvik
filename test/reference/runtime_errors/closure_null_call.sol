// expected E031: calling a null function value is a null reference
package runtime_errors

func main() -> Int {
    mut maybe: Func<Int, Int>? = null
    maybe(1)
    return 0
}
