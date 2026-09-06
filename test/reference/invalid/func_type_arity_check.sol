// expected P076: function types require at least a return type
package reference_invalid

func main() -> Int {
    f: Func = func() -> Int { return 1 }
    return 0
}
