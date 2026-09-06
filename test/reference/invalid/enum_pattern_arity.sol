// expected C107: pattern payload count must match the case
package reference_invalid

enum Shape {
    Rect(Int, Int)
}

func f(s: Shape) -> Int {
    switch s {
        case Shape.Rect(a) {
            return 1
        }
    }
    return 0
}
