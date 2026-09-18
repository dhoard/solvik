func arith1(a: Float, b: Float): Float {
    return a * b + a
}

println(arith1(91.0f, 48.0f))

println(arith1(56.0f, 91.0f))

func num2(flag: Boolean): Number {
    return if (flag) { 34 } else { 0L }
}

println(num2(true) is Number)

println(num2(true) is Any)

class H3 {
    val n: String?
    H3(n: String?) {
        this.n = n
    }
}

func show4(h: H3): String {
    val n = h.n
    if (n == null) {
        return "none"
    }
    return n.toString()
}

println(show4(H3("b2")))

println(show4(H3(null)))

println(H3(null).n ?? "fallback")
