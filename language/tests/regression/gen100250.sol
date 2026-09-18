val xs1: List<Boolean> = List(false, true)

xs1.add(false)

println(xs1.size)

println(xs1.get(0))

class H2 {
    val n: String?
    H2(n: String?) {
        this.n = n
    }
}

func show3(h: H2): String {
    val n = h.n
    if (n == null) {
        return "none"
    }
    return n.toString()
}

println(show3(H2("x1")))

println(show3(H2(null)))

println(H2(null).n ?? "fallback")
