enum Color {
    RED
    GREEN
}
var c: Color = Color.GREEN
var n: Integer = match c {
    _ => 9
}
print("wild" .. n)
