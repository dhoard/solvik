// expected C104: void is only the return element of a function type
package reference_invalid

func main() -> Int {
    f: Func<Void, Int> = func() -> Int { return 1 }
    return 0
}
