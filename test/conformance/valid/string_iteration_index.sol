// Strings iterate over characters and support index access.
package conformance

func main() -> Int {
    mut count: Int = 0
    for c in "hello" {
        count = count + 1
    }
    c0: Char = "hello"[1]
    return count
}
