// expected P076: function types require at least a return type
package reference_invalid

func main() -> int {
    f: func = func() -> int { return 1 }
    return 0
}
