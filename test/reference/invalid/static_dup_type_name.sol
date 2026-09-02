// expected C109: top-level names must be unique within a package
package reference_invalid

struct Widget {
    pub x: int
}

enum Widget {
    A
}

func main() -> int {
    return 0
}
