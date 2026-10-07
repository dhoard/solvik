interface I {
    method get(): Integer
}
class Impl implements I {
    Impl() {
    }

    method get(): Integer {
        return 1
    }
}
class Holder {
    delegate hashCode: I

    Holder(i: I) {
        this.hashCode = i
    }
}
print(1)

print("EXECUTED-INVALID")
