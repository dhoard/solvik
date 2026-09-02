// expected C107: algebraic enums cannot mix payload and integer-value cases
package reference_invalid

enum Mixed {
    A = 1
    B(int)
}

func main() -> int {
    return 0
}
