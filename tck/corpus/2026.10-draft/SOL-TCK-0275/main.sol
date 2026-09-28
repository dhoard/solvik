open class Both2 {
    Both2() {
    }

    open override func equals(other: Any?): Boolean {
        return true
    }

    open override func hashCode(): Integer {
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
val s = OnlyEq()
print(s)

print("EXECUTED-INVALID")
