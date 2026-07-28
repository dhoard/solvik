package example
func sum(values: List<int>) -> long {
    mut result: long = 0
    for value in values {
        result = result + value
    }
    return result
}
func fibonacci(value: int) -> long {
    if value <= 1 {
        return value
    }
    mut previous: long = 0
    mut current: long = 1
    mut index: int = 2
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
    print("Total: " .. total)
    print("Fibonacci: " .. fib)
    expected: long = 100
    if total != expected {
        print("Unexpected total")
        return 1
    }
    return 0
}
