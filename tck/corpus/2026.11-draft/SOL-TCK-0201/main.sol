enum Color {
    RED
    GREEN
}
var c = Color.RED
var n = match c {
    RED => 1
    GREEN => 2
}
print("enum" .. n)
