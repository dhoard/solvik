// expected C104: void is only the return element of a function type
package reference_invalid

func main() -> int {
    f: func<void, int> = func() -> int { return 1 }
    return 0
}
