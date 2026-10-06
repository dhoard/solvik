interface I {
    func get(): Integer
}
class Impl implements I {
    Impl() {
    }

    func get(): Integer {
        return 1
    }
}
class Holder {
    delegate var hashCode: I

    Holder(i: I) {
        this.hashCode = i
    }
}
print(1)

print("EXECUTED-INVALID")
