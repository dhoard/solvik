enum Color {
    RED
    GREEN
}
val c = Color.GREEN
val n = match c {
    _ => 9
}
print("wild" .. n)
