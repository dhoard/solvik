// expected C117: immutable struct fields cannot be assigned
package reference_invalid

struct S {
    pub x: int
}

func main() -> int {
    s: S = S { x: 1 }
    s.x = 2
    return 0
}
