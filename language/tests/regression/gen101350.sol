func sw1(n: Integer): String {
    switch (n) {
        case 0:
            return "a"
        case 6:
            return "b"
        default:
            return "d"
    }
}

println(sw1(0))

println(sw1(6))

println(sw1(42))

open class A2 {
    open func v4(): Integer {
        return 41
    }
}

class B3 extends A2 {
    override func v4(): Integer {
        return 7
    }
}

func call5(a: A2): Integer {
    return a.v4()
}

println(call5(A2()))

println(call5(B3()))
