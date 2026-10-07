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

println(kind1('z'))

println(kind1('z') is String)

class mutable A2 {
    method mutable v4(): Integer {
        return 20
    }
}

class B3 extends A2 {
    method override v4(): Integer {
        return 33
    }
}

func call5(a: A2): Integer {
    return a.v4()
}

println(call5(A2()))

println(call5(B3()))

println(2 == 3)

println(2 != 3)

println("b7" == "b7")

println('Q' == 'Q')
