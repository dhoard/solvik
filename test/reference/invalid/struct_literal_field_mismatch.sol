// expected C098: struct literal field type mismatch after substitution
package conformance

struct Box<T> {
    pub value: T
    pub count: int
}

func main() -> int {
    b: any = Box { value: 1, count: "many" }
    return 0
}
