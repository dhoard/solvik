// Declaration-only helper included by EqualityInclude.sol.
//
// A user `equals` override makes `==`, an explicit `equals` call, `Set` membership, and `Map` key
// lookup agree. A constant `switch` keeps matching by the same semantic equality.

class Coordinate {
    var x: Integer
    var y: Integer

    Coordinate(x: Integer, y: Integer) {
        this.x = x
        this.y = y
    }

    method override equals(other: Any?): Boolean {
        if (other is Coordinate) {
            return this.x == other.x && this.y == other.y
        }
        return false
    }

    method override hashCode(): Integer {
        return 31 * this.x + this.y
    }
}

func label(keyword: String): String {
    switch (keyword) {
        case "one" {
            return "1"
        }
        case "two" {
            return "2"
        }
        default {
            return "?"
        }
    }
}
