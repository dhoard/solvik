// expected C110: annotations must name known types
package reference_invalid

func main() -> int {
    x: Wibble = 5
    return 0
}
