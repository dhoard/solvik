// expected C101: wrong callable argument type
package reference_invalid

func main() -> Int {
    f: Func<Int, Int> = func(x: Int) -> Int { return x }
    return f("hi")
}
