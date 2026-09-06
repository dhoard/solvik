package native_collections

func main() -> Int {
    xs: List<Int> = [1, 2, 3, 4, 5]
    doubled: List<Int> = xs.map(func(x: Int) -> Int { return x * 2 })
    evens: List<Int> = xs.filter(func(x: Int) -> Bool { return x % 2 == 0 })
    sum: Int = xs.fold(0, func(acc: Int, x: Int) -> Int { return acc + x })
    product: Int = xs.reduce(func(a: Int, b: Int) -> Int { return a * b })
    sorted: List<Int> = [3, 1, 2].sort(func(a: Int, b: Int) -> Int { return a - b })
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
