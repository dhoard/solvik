class C1 {
    val x: Integer = 1
}
func f(): Integer {
    return C1().missing
}
println(f())
