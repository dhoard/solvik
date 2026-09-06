// expected E067: method type argument count must match the declaration
package runtime_errors

struct Box<T> {
    pub value: T

    pub func wrap<U>(other: U) -> String {
        return "wrapped"
    }
}

func main() -> Int {
    b: Box<Int> = Box { value: 1 }
    println(b.wrap<Int, String>("s"))
    return 0
}
