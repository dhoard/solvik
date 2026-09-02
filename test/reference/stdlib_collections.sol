package reference_stdlib_collections
func main() -> int {
    xs: list<int> = [1, 2, 3, 4, 5]
    // map
    doubled: list<int> = xs.map(func(x: int) -> int { return x * 2 })
    if doubled != [2, 4, 6, 8, 10] {
        return 1
    }
    // map to different type
    strs: list<string> = xs.map(func(x: int) -> string { return "n" .. x })
    if strs[2] != "n3" {
        return 2
    }
    // filter
    evens: list<int> = xs.filter(func(x: int) -> bool { return x % 2 == 0 })
    if evens != [2, 4] {
        return 3
    }
    // fold with accumulator
    sum: int = xs.fold(0, func(acc: int, x: int) -> int { return acc + x })
    if sum != 15 {
        return 4
    }
    // reduce
    prod: int = xs.reduce(func(a: int, b: int) -> int { return a * b })
    if prod != 120 {
        return 5
    }
    // find
    firstEven: int? = xs.find(func(x: int) -> bool { return x % 2 == 0 })
    if firstEven != 2 {
        return 6
    }
    missing: int? = xs.find(func(x: int) -> bool { return x > 100 })
    if missing != null {
        return 7
    }
    // any / all
    if !xs.any(func(x: int) -> bool { return x == 3 }) {
        return 8
    }
    if xs.any(func(x: int) -> bool { return x > 100 }) {
        return 9
    }
    if !xs.all(func(x: int) -> bool { return x > 0 }) {
        return 10
    }
    if xs.all(func(x: int) -> bool { return x > 2 }) {
        return 11
    }
    // contains (existing)
    if !xs.contains(4) {
        return 12
    }
    // first / last
    f: int? = xs.first()
    l: int? = xs.last()
    if f != 1 || l != 5 {
        return 13
    }
    empty: list<int> = []
    if empty.first() != null || empty.last() != null {
        return 14
    }
    // reverse
    rev: list<int> = xs.reverse()
    if rev != [5, 4, 3, 2, 1] {
        return 15
    }
    // sort with comparator
    unsorted: list<int> = [3, 1, 4, 1, 5]
    sorted: list<int> = unsorted.sort(func(a: int, b: int) -> int { return a - b })
    if sorted != [1, 1, 3, 4, 5] {
        return 16
    }
    desc: list<int> = unsorted.sort(func(a: int, b: int) -> int { return b - a })
    if desc != [5, 4, 3, 1, 1] {
        return 17
    }
    // strings of lists
    names: list<string> = ["bob", "alice", "carol"]
    upper: list<string> = names.map(func(s: string) -> string { return s.toUpper() })
    if upper[1] != "ALICE" {
        return 18
    }
    longNames: list<string> = names.filter(func(s: string) -> bool { return s.len() > 3 })
    if longNames.len() != 2 {
        return 19
    }
    return 0
}
