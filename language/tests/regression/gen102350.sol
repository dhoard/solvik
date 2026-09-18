val m1: Map<String, Int> = Map("a": 1, "b": 2)

m1.put("c", 3)

println(m1.get("b"))

println(m1.containsKey("c"))

println(m1.size)

func either2(flag: Boolean): Any {
    return if (flag) { 'b' } else { 81 }
}

println(either2(true) is Any)

println(either2(false) is Any)

println(either2(true) != false)

func arith3(a: Float, b: Float): Float {
    return a - b * a
}

println(arith3(70.0f, 30.0f))

println(arith3(5.0f, 70.0f))
