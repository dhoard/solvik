// expected E073: byte conversion out of range
package runtime_errors

func main() -> int {
    b: byte = byte(300)
    return 0
}
