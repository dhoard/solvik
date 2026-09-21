func either1(flag: Boolean): Any {
    return if (flag) { true } else { 'a' }
}

println(either1(true) is Any)

println(either1(false) is Any)

println(either1(true) != false)

func join2(a: String, n: Integer): String {
    return a .. n .. a
}

println(join2("solvik6", 40))

println("solvik6" .. 'b' .. 40 .. true)
