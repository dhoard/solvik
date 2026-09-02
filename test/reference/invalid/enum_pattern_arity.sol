// expected C107: pattern payload count must match the case
package reference_invalid

enum Shape {
    Rect(int, int)
}

func f(s: Shape) -> int {
    switch s {
        case Shape.Rect(a) {
            return 1
        }
    }
    return 0
}
