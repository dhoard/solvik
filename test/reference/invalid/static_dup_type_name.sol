// expected C109: top-level names must be unique within a package
package reference_invalid

struct Widget {
    pub x: Int
}

enum Widget {
    A
}

func main() -> Int {
    return 0
}
