// expected C097: struct fields may not recurse without nullability or indirection
package conformance

struct Node {
    pub value: int
    pub next: Node
}

func main() -> int {
    return 0
}
