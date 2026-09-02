// expected E067: struct literal inference needs a type for T
package runtime_errors

struct Box<T> {
    pub value: T
}

func main() -> int {
    b: any = Box { value: null }
    return 0
}
