// Variadic spread (list...) contributes elements, not the list itself.
package conformance

func sum(values: ...int) -> int {
    mut total: int = 0
    for v in values {
        total = total + v
    }
    return total
}

func main() -> int {
    nums: list<int> = [1, 2, 3]
    return sum(nums...) + sum(10, nums...)
}
