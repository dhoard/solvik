enum Color {
    Red
    Green
}
func show(c: Color): String {
    return match c {
        Red => "red"
    }
}
println(show(Color.Red))
