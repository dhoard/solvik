val xs1: List<String> = List("text8", "a1")

xs1.add("x7")

println(xs1.size)

println(xs1.get(0))

func kind2(v: Any): String {
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

println(kind2(true))

println(kind2(true) is String)

func arith3(a: Integer, b: Integer): Integer {
    return a + b * a
}

println(arith3(17, 73))

println(arith3(40, 17))
