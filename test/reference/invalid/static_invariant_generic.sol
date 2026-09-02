// expected C118: generic instantiations are invariant
package reference_invalid

struct Box<T> {
    pub value: T
}

func main() -> int {
    b: Box<int> = Box { value: 1 }
    c: Box<int?> = b
    return 0
}
