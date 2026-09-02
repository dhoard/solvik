// expected C096: a generic trait constraint must be instantiated
package reference_invalid

func count<C: Iterable>(values: C) -> int {
    return values.iterator().len()
}

func main() -> int {
    return 0
}
