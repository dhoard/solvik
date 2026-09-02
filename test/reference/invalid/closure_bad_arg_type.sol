// expected C101: wrong callable argument type
package reference_invalid

func main() -> int {
    f: func<int, int> = func(x: int) -> int { return x }
    return f("hi")
}
