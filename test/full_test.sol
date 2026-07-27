package example
func sum(values: List<int>) -> long {
    result: long = 0
    for value in values {
        result = result + value
    }
    return result
}
func fibonacci(value: int) -> long {
    if value <= 1 {
        return value
    }
    previous: long = 0
    current: long = 1
    index: int = 2
    while index <= value {
        next: long = previous + current
        previous = current
        current = next
        index = index + 1
    }
    return current
}
func main() -> int {
    values: List<int> = [
        10,
        20,
        30,
        40
    ]
    total: long = sum(values)
    fib: long = fibonacci(20)
    print("Total: " + string(total))
    print("Fibonacci: " + string(fib))
    expected: long = 100
    if total != expected {
        print("Unexpected total")
        return 1
    }
    return 0
}
