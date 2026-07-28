package example
func sum(values: List<int>) -> int {
    mut result: int = 0
    for value in values {
        result = result + value
    }
    return result
}
func fibonacci(value: int) -> int {
    if value <= 1 {
        return value
    }
    mut previous: int = 0
    mut current: int = 1
    mut index: int = 2
    while index <= value {
        next: int = previous + current
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
    total: int = sum(values)
    fib: int = fibonacci(20)
    print("Total: " .. total)
    print("Fibonacci: " .. fib)
    expected: int = 100
    if total != expected {
        print("Unexpected total")
        return 1
    }
    return 0
}
