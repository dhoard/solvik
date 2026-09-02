// expected C118: nullable values need narrowing before a non-nullable target
package reference_invalid

func main() -> int {
    x: int? = 5
    y: int = x
    return 0
}
