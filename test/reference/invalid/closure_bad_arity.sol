// expected C101: wrong callable arity
package reference_invalid

func main() -> Int {
    f: Func<Int, Int> = func(x: Int) -> Int { return x }
    return f(1, 2)
}
