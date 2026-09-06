// Struct equality is structural.
package conformance

struct Pair {
    pub a: String
    pub b: Int
}

func main() -> Int {
    p1: Pair = Pair { a: "x, y", b: 1 }
    p2: Pair = Pair { a: "x, y", b: 1 }
    p3: Pair = Pair { a: "x, y", b: 2 }
    if p1 == p2 && p1 != p3 {
        return 1
    }
    return 0
}
