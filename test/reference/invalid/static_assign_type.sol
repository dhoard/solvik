// expected C119: assignment must match the target type
package reference_invalid

func main() -> Int {
    mut n: Int = 5
    n = "str"
    return 0
}
