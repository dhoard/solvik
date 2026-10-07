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
var p: Loud? = Loud()
print("one" .. (p == null))
