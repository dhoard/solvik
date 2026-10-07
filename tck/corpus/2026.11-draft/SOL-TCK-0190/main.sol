enum Color {
    RED
    GREEN
}
var c: Color = Color.GREEN
var n: Integer = match c {
    RED => 1
    GREEN => 2
}
print("all" .. n)
