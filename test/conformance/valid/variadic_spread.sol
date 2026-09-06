// Variadic spread (list...) contributes elements, not the list itself.
package conformance

func sum(values: ...Int) -> Int {
    mut total: Int = 0
    for v in values {
        total = total + v
    }
    return total
}

func main() -> Int {
    nums: List<Int> = [1, 2, 3]
    return sum(nums...) + sum(10, nums...)
}
