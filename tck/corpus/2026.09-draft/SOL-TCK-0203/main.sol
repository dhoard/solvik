enum Color {
    RED
    GREEN
}
val c = Color.RED
val v: Number = match c {
    RED => 1
    GREEN => 2L
}
print("join" .. v)
