// expected E073: failed string-to-int conversion
package runtime_errors

func main() -> int {
    n: int = int("abc")
    return 0
}
