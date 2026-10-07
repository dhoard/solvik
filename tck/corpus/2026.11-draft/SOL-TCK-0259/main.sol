class L {
    L() {
    }

    method override equals(other: Any?): Boolean {
        print("L")
        return false
    }

    method override hashCode(): Integer {
        return 1
    }
}
class R {
    R() {
    }

    method override equals(other: Any?): Boolean {
        print("R")
        return false
    }

    method override hashCode(): Integer {
        return 2
    }
}
var l: L = L()
var r: R = R()
var q: Any = l
print(q == r)
