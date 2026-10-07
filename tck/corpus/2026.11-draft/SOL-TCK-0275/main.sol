class mutable Both2 {
    Both2() {
    }

    method override mutable equals(other: Any?): Boolean {
        return true
    }

    method override mutable hashCode(): Integer {
        return 5
    }
}
class OnlyEq extends Both2 {
    OnlyEq() {
    }

    method override equals(other: Any?): Boolean {
        return false
    }
}
var s: OnlyEq = OnlyEq()
print(s)

print("EXECUTED-INVALID")
