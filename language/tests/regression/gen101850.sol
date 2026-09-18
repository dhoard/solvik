func kind1(v: Any): String {
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

println(kind1('b'))

println(kind1('b') is String)

val s2: Set<String> = Set("a", "b", "a")

println(s2.size)

val st3: Stack<Int> = Stack(1, 2, 3)

println(st3.size)
