package native_closure

func makeAdder(amount: Int) -> Func<Int, Int> {
    return func(x: Int) -> Int {
        return x + amount
    }
}

func main() -> Int {
    add: Func<Int, Int> = makeAdder(4)
    return add(3) - 7
}
