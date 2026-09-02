// expected C099: a method type parameter may not shadow the struct's
package reference_invalid

struct Box<T> {
    pub value: T

    pub func into<T>(other: T) -> T {
        return other
    }
}

func main() -> int {
    return 0
}
