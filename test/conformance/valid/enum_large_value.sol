// Enum values are 64-bit constants.
package conformance

enum Big {
    A = 5000000000,
}

func main() -> int {
    return int(Big.A)
}
