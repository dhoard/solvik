// Binary and octal integer literals.
package conformance

func main() -> Int {
    b: Int = 0b101
    o: Int = 0o17
    us: Int = 0b1010_1010
    return b + o + us
}
