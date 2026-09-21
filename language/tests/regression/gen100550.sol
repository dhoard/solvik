func kind1(v: Any): String {
    if (v is Integer) {
        return "int"
    }
    if (v is Number) {
        return "number"
    }
    if (v is Any) {
        return "any"
    }
    return "other"
}

println(kind1(0))

println(kind1(0) is String)

func cmp2(a: Double, b: Double): Boolean {
    return a > b
}

println(cmp2(92.0, 98.0))

open class A3 {
    open func v5(): Integer {
        return 22
    }
}

class B4 extends A3 {
    override func v5(): Integer {
        return 19
    }
}

func call6(a: A3): Integer {
    return a.v5()
}

println(call6(A3()))

println(call6(B4()))
