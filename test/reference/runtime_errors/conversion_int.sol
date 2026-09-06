// expected E073: failed string-to-int conversion
package runtime_errors

func main() -> Int {
    n: Int = int("abc")
    return 0
}
