// expected C115: value return in a void function
package reference_invalid

func f() {
    return 5
}

func main() -> int {
    return 0
}
