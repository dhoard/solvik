// expected C118: nullable values need narrowing before a non-nullable target
package reference_invalid

func main() -> Int {
    x: Int? = 5
    y: Int = x
    return 0
}
