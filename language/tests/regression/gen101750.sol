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

println(show2(H1("text0")))

println(show2(H1(null)))

println(H1(null).n ?? "fallback")

func kind3(v: Any): String {
    if (v is Int) {
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

println(kind3(52))

println(kind3(52) is String)

class Box4<T> {
    var value: T
    Box4(value: T) {
        this.value = value
    }
    func get(): T {
        return this.value
    }
}

val box5: Box4<String> = Box4("solvik3")

println(box5.get())

println(box5 is Any)
