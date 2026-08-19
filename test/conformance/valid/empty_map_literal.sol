// An empty map literal infers its type from the declaration context.
package conformance

func main() -> int {
    m: map<string, int> = {}
    m["a"] = 1
    return m.len()
}
