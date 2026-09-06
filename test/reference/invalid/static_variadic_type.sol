// expected C101: variadic arguments must match the element type
package reference_invalid

func sum(values: ...Int) -> Int {
    mut t: Int = 0
    for v in values {
        t = t + v
    }
    return t
}

func main() -> Int {
    return sum("a", "b")
}
