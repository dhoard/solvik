// expected E067: struct literal inference needs a type for T
package runtime_errors

struct Box<T> {
    pub value: T
}

func main() -> Int {
    b: Any = Box { value: null }
    return 0
}
