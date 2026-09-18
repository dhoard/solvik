// Declaration-only helper included by EqualityInclude.sol.
//
// A user `equals` override makes `==`, an explicit `equals` call, `Set` membership, and `Map` key
// lookup agree. A constant `switch` keeps matching by the same semantic equality.

class Coordinate {
    val x: Int
    val y: Int

    Coordinate(x: Int, y: Int) {
        this.x = x
        this.y = y
    }

    override func equals(other: Any?): Boolean {
        if (other is Coordinate) {
            return this.x == other.x && this.y == other.y
        }
        return false
    }
}

func label(keyword: String): String {
    switch (keyword) {
        case "one":
            return "1"
        case "two":
            return "2"
        default:
            return "?"
    }
}
