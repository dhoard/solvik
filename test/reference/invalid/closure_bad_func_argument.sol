// expected C101: mismatched function value passed as an argument
package reference_invalid

func apply(f: Func<Int, Int>) -> Int {
    return f(1)
}

func main() -> Int {
    g: Func<Int, String> = func(x: Int) -> String { return "s" }
    return apply(g)
}
