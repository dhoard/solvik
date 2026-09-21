interface I1 {
    func base3(): Integer
    func twice4(): Integer {
        return this.base3() * 2
    }
}

class Imp2 implements I1 {
    func base3(): Integer {
        return 39
    }
}

val ifaceValue5: I1 = Imp2()

println(ifaceValue5.twice4())

func cmp6(a: Double, b: Double): Boolean {
    return a < b
}

println(cmp6(44.0, 96.0))

open class A7 {
    open func v9(): Integer {
        return 27
    }
}

class B8 extends A7 {
    override func v9(): Integer {
        return 18
    }
}

func call10(a: A7): Integer {
    return a.v9()
}

println(call10(A7()))

println(call10(B8()))
