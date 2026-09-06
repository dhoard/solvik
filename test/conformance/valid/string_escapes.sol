// Standard escapes, hex escapes, and unicode escapes decode.
package conformance

func main() -> Int {
    s: String = "\x41\u0042\U0001F600\n\t"
    return s.len()
}
