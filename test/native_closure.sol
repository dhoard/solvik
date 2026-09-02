package native_closure

func makeAdder(amount: int) -> func<int, int> {
    return func(x: int) -> int {
        return x + amount
    }
}

func main() -> int {
    add: func<int, int> = makeAdder(4)
    return add(3) - 7
}
