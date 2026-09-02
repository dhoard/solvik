// expected C101: variadic arguments must match the element type
package reference_invalid

func sum(values: ...int) -> int {
    mut t: int = 0
    for v in values {
        t = t + v
    }
    return t
}

func main() -> int {
    return sum("a", "b")
}
