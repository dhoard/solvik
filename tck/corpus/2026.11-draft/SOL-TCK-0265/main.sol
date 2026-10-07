class Exact {
    Exact() {
    }

    method override equals(other: Any?): Boolean {
        return true
    }

    method override hashCode(): Integer {
        return 7
    }
}
var p: Exact = Exact()
print("shape" .. (p == p))
