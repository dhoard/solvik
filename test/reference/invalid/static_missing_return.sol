// expected C111: non-void functions must return on every path
package reference_invalid

func f(x: int) -> int {
    if x > 0 {
        return 1
    }
}

func main() -> int {
    return 0
}
