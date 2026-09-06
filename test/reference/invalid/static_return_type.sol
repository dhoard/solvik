// expected C114: return value must match the declared type
package reference_invalid

func f() -> Int {
    return "str"
}

func main() -> Int {
    return 0
}
