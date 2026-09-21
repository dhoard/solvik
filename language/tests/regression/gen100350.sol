enum E1 {
    A(Integer)
    B
    C(String)
}

func me2(e: E1): String {
    return match e {
        A(n) => "a" .. n
        B => "b"
        C(s) => "c" .. s
    }
}

println(me2(E1.A(2)))

println(me2(E1.B))

println(me2(E1.C("a3")))

func arith3(a: Long, b: Long): Long {
    return a * b + a
}

println(arith3(88L, 14L))

println(arith3(57L, 88L))
