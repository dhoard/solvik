package reference_stdlib_collections
func main() -> Int {
    xs: List<Int> = [1, 2, 3, 4, 5]
    // map
    doubled: List<Int> = xs.map(func(x: Int) -> Int { return x * 2 })
    if doubled != [2, 4, 6, 8, 10] {
        return 1
    }
    // map to different type
    strs: List<String> = xs.map(func(x: Int) -> String { return "n" .. x })
    if strs[2] != "n3" {
        return 2
    }
    // filter
    evens: List<Int> = xs.filter(func(x: Int) -> Bool { return x % 2 == 0 })
    if evens != [2, 4] {
        return 3
    }
    // fold with accumulator
    sum: Int = xs.fold(0, func(acc: Int, x: Int) -> Int { return acc + x })
    if sum != 15 {
        return 4
    }
    // reduce
    prod: Int = xs.reduce(func(a: Int, b: Int) -> Int { return a * b })
    if prod != 120 {
        return 5
    }
    // find
    firstEven: Int? = xs.find(func(x: Int) -> Bool { return x % 2 == 0 })
    if firstEven != 2 {
        return 6
    }
    missing: Int? = xs.find(func(x: Int) -> Bool { return x > 100 })
    if missing != null {
        return 7
    }
    // any / all
    if !xs.any(func(x: Int) -> Bool { return x == 3 }) {
        return 8
    }
    if xs.any(func(x: Int) -> Bool { return x > 100 }) {
        return 9
    }
    if !xs.all(func(x: Int) -> Bool { return x > 0 }) {
        return 10
    }
    if xs.all(func(x: Int) -> Bool { return x > 2 }) {
        return 11
    }
    // contains (existing)
    if !xs.contains(4) {
        return 12
    }
    // first / last
    f: Int? = xs.first()
    l: Int? = xs.last()
    if f != 1 || l != 5 {
        return 13
    }
    empty: List<Int> = []
    if empty.first() != null || empty.last() != null {
        return 14
    }
    // reverse
    rev: List<Int> = xs.reverse()
    if rev != [5, 4, 3, 2, 1] {
        return 15
    }
    // sort with comparator
    unsorted: List<Int> = [3, 1, 4, 1, 5]
    sorted: List<Int> = unsorted.sort(func(a: Int, b: Int) -> Int { return a - b })
    if sorted != [1, 1, 3, 4, 5] {
        return 16
    }
    desc: List<Int> = unsorted.sort(func(a: Int, b: Int) -> Int { return b - a })
    if desc != [5, 4, 3, 1, 1] {
        return 17
    }
    // strings of lists
    names: List<String> = ["bob", "alice", "carol"]
    upper: List<String> = names.map(func(s: String) -> String { return s.toUpper() })
    if upper[1] != "ALICE" {
        return 18
    }
    longNames: List<String> = names.filter(func(s: String) -> Bool { return s.len() > 3 })
    if longNames.len() != 2 {
        return 19
    }
    return 0
}
