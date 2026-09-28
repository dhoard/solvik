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
val p = Loud()
val q = Loud()
print("id" .. (p === q))
