enum Color {
    RED
    GREEN
}
var c: Color = Color.RED
var v: Number = match c {
    RED => 1
    GREEN => 2L
}
print("join" .. v)
