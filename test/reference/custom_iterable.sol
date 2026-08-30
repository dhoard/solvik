package reference_custom_iterable

struct Range3 {
    pub start: int

    pub func iterator() -> list<int> {
        return [start, start + 1, start + 2]
    }
}

func sum<T: Iterable<int>>(values: T) -> int {
    mut total: int = 0
    for value in values {
        total = total + value
    }
    return total
}

func main() -> int {
    r: Range3 = Range3 { start: 4 }
    if sum(r) != 15 {
        return 1
    }
    return 0
}
