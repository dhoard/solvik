// expected C096: a generic struct annotation must be fully instantiated
package conformance

struct Box<T> {
    pub value: T
}

func main() -> int {
    b: Box = Box { value: 1 }
    return 0
}
