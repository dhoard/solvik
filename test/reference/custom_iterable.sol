package reference_custom_iterable

struct Range3 {
    pub start: Int

    pub func iterator() -> List<Int> {
        return [start, start + 1, start + 2]
    }
}

func sum<T: Iterable<Int>>(values: T) -> Int {
    mut total: Int = 0
    for value in values {
        total = total + value
    }
    return total
}

func main() -> Int {
    r: Range3 = Range3 { start: 4 }
    if sum(r) != 15 {
        return 1
    }
    return 0
}
