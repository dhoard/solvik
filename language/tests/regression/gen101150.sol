class C1 {
    val f2: String
    C1(f2: String) {
        this.f2 = f2
    }
    func get(): String {
        return this.f2
    }
}

println(C1("b5").get())

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

println(kind3('z'))

println(kind3('z') is String)

func num4(flag: Boolean): Number {
    return if (flag) { 43 } else { 11L }
}

println(num4(true) is Number)

println(num4(true) is Any)
