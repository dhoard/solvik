func cmp1(a: Integer, b: Integer): Boolean {
    return a > b
}

println(cmp1(44, 76))

val re2 = Regex(r#"[a-z]+"#)

println(re2.matches("hello"))

println(re2.matches("123"))

func logic3(a: Boolean, b: Boolean): Boolean {
    return (a && b) || !a
}

println(logic3(true, false))

println(logic3(false, true))
