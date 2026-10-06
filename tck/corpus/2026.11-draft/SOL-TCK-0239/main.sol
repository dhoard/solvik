class Loud {
    Loud() {
    }

    override func equals(other: Any?): Boolean {
        print("u")
        return true
    }

    override func hashCode(): Integer {
        return 4
    }
}
var p = Loud()
var q = Loud()
print("id" .. (p === q))
