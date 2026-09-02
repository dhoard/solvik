// expected C101: wrong callable arity
package reference_invalid

func main() -> int {
    f: func<int, int> = func(x: int) -> int { return x }
    return f(1, 2)
}
