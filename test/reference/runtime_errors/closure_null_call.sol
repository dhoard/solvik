// expected E031: calling a null function value is a null reference
package runtime_errors

func main() -> int {
    mut maybe: func<int, int>? = null
    maybe(1)
    return 0
}
