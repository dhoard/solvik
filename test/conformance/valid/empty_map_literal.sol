// An empty map literal infers its type from the declaration context.
package conformance

func main() -> Int {
    m: Map<String, Int> = {}
    m["a"] = 1
    return m.len()
}
