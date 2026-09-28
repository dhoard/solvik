open class Both {
    Both() {
    }

    override func equals(other: Any?): Boolean {
        return true
    }

    override func hashCode(): Integer {
        return 5
    }
}
class Plain extends Both {
    Plain() {
    }
}
val s = Plain()
print("inh" .. (s == s) .. s.hashCode())
