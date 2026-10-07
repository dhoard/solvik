class H1 {
    var n: String?
    H1(n: String?) {
        this.n = n
    }
}

func show2(h: H1): String {
    var n: String? = h.n
    if (n == null) {
        return "none"
    }
    return n.toString()
}

println(show2(H1("solvik5")))

println(show2(H1(null)))

println(H1(null).n ?? "fallback")
