class L {
    L() {
    }

    override func equals(other: Any?): Boolean {
        print("L")
        return false
    }

    override func hashCode(): Integer {
        return 1
    }
}
class R {
    R() {
    }

    override func equals(other: Any?): Boolean {
        print("R")
        return false
    }

    override func hashCode(): Integer {
        return 2
    }
}
val l = L()
val r = R()
val q: Any = l
print(q == r)
