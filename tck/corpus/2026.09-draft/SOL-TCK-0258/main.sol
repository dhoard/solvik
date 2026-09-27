class Loud {
    Loud() {
    }

    override func equals(other: Any?): Boolean {
        print("u")
        return false
    }

    override func hashCode(): Integer {
        return 4
    }
}
val p = Loud()
print("sc" .. (p == p) .. p.equals(p))
