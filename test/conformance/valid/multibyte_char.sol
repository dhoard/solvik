// Char literals support multi-byte UTF-8 characters.
package conformance

func main() -> int {
    m: char = 'é'
    if m == 'é' && int(m) == 233 {
        return 1
    }
    return 0
}
