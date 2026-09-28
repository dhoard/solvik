class Exact {
    Exact() {
    }

    override func equals(other: Any?): Boolean {
        return true
    }

    override func hashCode(): Integer {
        return 7
    }
}
val p = Exact()
print("hash" .. p.hashCode())
