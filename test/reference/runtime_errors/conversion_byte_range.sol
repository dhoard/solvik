// expected E073: byte conversion out of range
package runtime_errors

func main() -> Int {
    b: Byte = byte(300)
    return 0
}
