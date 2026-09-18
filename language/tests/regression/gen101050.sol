open class A1 {
    open func v3(): Int {
        return 9
    }
}

class B2 extends A1 {
    override func v3(): Int {
        return 7
    }
}

func call4(a: A1): Int {
    return a.v3()
}

println(call4(A1()))

println(call4(B2()))

func either5(flag: Boolean): Any {
    return if (flag) { true } else { "text2" }
}

println(either5(true) is Any)

println(either5(false) is Any)

println(either5(true) != false)
