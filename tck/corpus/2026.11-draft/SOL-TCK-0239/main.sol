class Loud {
    Loud() {
    }

    method override equals(other: Any?): Boolean {
        print("u")
        return true
    }

    method override hashCode(): Integer {
        return 4
    }
}
var p: Loud = Loud()
var q: Loud = Loud()
print("id" .. (p === q))
