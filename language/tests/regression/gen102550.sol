class H1 {
    val n: String?
    H1(n: String?) {
        this.n = n
    }
}

func show2(h: H1): String {
    val n = h.n
    if (n == null) {
        return "none"
    }
    return n.toString()
}

println(show2(H1("solvik0")))

println(show2(H1(null)))

println(H1(null).n ?? "fallback")
