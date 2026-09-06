// expected C100: function signatures must match exactly
package reference_invalid

func double(x: Int) -> Int {
    return x * 2
}

func main() -> Int {
    f: Func<Int, String> = double
    return 0
}
