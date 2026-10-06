class C1 {
    var x: Integer = 1
}
func f(): Integer {
    return C1().missing
}
println(f())
