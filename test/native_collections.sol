package native_collections

func main() -> int {
    xs: list<int> = [1, 2, 3, 4, 5]
    doubled: list<int> = xs.map(func(x: int) -> int { return x * 2 })
    evens: list<int> = xs.filter(func(x: int) -> bool { return x % 2 == 0 })
    sum: int = xs.fold(0, func(acc: int, x: int) -> int { return acc + x })
    product: int = xs.reduce(func(a: int, b: int) -> int { return a * b })
    sorted: list<int> = [3, 1, 2].sort(func(a: int, b: int) -> int { return a - b })
    if doubled != [2, 4, 6, 8, 10] || evens != [2, 4] || sum != 15 || product != 120 {
        return 1
    }
    if sorted != [1, 2, 3] || xs.first() != 1 || xs.last() != 5 || !xs.contains(4) {
        return 2
    }
    if "hello".toUpper() != "HELLO" || !"hello world".contains("world") {
        return 3
    }
    return 0
}
