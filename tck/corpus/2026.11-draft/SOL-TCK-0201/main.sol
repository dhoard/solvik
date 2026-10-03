enum Color {
    RED
    GREEN
}
val c = Color.RED
val n = match c {
    RED => 1
    GREEN => 2
}
print("enum" .. n)
