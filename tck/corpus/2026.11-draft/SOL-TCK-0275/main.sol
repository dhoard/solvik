mutable class Both2 {
    Both2() {
    }

    mutable override func equals(other: Any?): Boolean {
        return true
    }

    mutable override func hashCode(): Integer {
        return 5
    }
}
class OnlyEq extends Both2 {
    OnlyEq() {
    }

    override func equals(other: Any?): Boolean {
        return false
    }
}
var s = OnlyEq()
print(s)

print("EXECUTED-INVALID")
