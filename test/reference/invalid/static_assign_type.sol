// expected C119: assignment must match the target type
package reference_invalid

func main() -> int {
    mut n: int = 5
    n = "str"
    return 0
}
