// expected C100: function signatures must match exactly
package reference_invalid

func double(x: int) -> int {
    return x * 2
}

func main() -> int {
    f: func<int, string> = double
    return 0
}
