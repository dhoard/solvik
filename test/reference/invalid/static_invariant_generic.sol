// expected C118: generic instantiations are invariant
package reference_invalid

struct Box<T> {
    pub value: T
}

func main() -> Int {
    b: Box<Int> = Box { value: 1 }
    c: Box<Int?> = b
    return 0
}
