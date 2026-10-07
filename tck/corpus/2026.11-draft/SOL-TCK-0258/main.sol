class Loud {
    Loud() {
    }

    method override equals(other: Any?): Boolean {
        print("u")
        return false
    }

    method override hashCode(): Integer {
        return 4
    }
}
var p: Loud = Loud()
print("sc" .. (p == p) .. p.equals(p))
