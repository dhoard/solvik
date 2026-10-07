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

println(show2(H1("text0")))

println(show2(H1(null)))

println(H1(null).n ?? "fallback")

func kind3(v: Any): String {
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

println(kind3(52))

println(kind3(52) is String)

class Box4<T> {
    var mutable value: T
    Box4(value: T) {
        this.value = value
    }
    method get(): T {
        return this.value
    }
}

var box5: Box4<String> = Box4("solvik3")

println(box5.get())

println(box5 is Any)
