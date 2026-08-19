// Standard escapes, hex escapes, and unicode escapes decode.
package conformance

func main() -> int {
    s: string = "\x41\u0042\U0001F600\n\t"
    return s.len()
}
