// expected C097: struct fields may not recurse without nullability or indirection
package conformance

struct Node {
    pub value: Int
    pub next: Node
}

func main() -> Int {
    return 0
}
