// expected C096: generic annotations must carry exactly one type argument
package conformance

struct Box<T> {
    pub value: T
}

func main() -> int {
    b: Box<int, string> = Box { value: 1 }
    return 0
}
