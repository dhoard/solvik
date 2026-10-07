class mutable Both {
    Both() {
    }

    method override equals(other: Any?): Boolean {
        return true
    }

    method override hashCode(): Integer {
        return 5
    }
}
class Plain extends Both {
    Plain() {
    }
}
var s: Plain = Plain()
print("inh" .. (s == s) .. s.hashCode())
