func name(n: Integer): String {
    switch (n) {
        case 1:
            return "one"
        case 2, 3:
            return "few"
        default:
            return "many"
    }
}
println(name(1))
println(name(3))
println(name(10))
