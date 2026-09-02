// expected C101: mismatched function value passed as an argument
package reference_invalid

func apply(f: func<int, int>) -> int {
    return f(1)
}

func main() -> int {
    g: func<int, string> = func(x: int) -> string { return "s" }
    return apply(g)
}
