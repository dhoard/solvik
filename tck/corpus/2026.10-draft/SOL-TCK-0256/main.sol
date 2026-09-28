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
val p: Loud? = null
print("both" .. (p == null))
