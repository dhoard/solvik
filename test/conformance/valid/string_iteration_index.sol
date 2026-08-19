// Strings iterate over characters and support index access.
package conformance

func main() -> int {
    mut count: int = 0
    for c in "hello" {
        count = count + 1
    }
    c0: char = "hello"[1]
    return count
}
