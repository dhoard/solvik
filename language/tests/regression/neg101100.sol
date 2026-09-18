class C1 {
    val x: Int = 1
}
func f(): Int {
    return C1().missing
}
println(f())
