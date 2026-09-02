// expected E067: method type argument count must match the declaration
package runtime_errors

struct Box<T> {
    pub value: T

    pub func wrap<U>(other: U) -> string {
        return "wrapped"
    }
}

func main() -> int {
    b: Box<int> = Box { value: 1 }
    println(b.wrap<int, string>("s"))
    return 0
}
