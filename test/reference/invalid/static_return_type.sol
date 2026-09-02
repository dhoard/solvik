// expected C114: return value must match the declared type
package reference_invalid

func f() -> int {
    return "str"
}

func main() -> int {
    return 0
}
