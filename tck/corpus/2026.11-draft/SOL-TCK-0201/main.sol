enum Color {
    RED
    GREEN
}
var c: Color = Color.RED
var n: Integer = match c {
    RED => 1
    GREEN => 2
}
print("enum" .. n)
