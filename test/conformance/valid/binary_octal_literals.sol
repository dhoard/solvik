// Binary and octal integer literals.
package conformance

func main() -> int {
    b: int = 0b101
    o: int = 0o17
    us: int = 0b1010_1010
    return b + o + us
}
