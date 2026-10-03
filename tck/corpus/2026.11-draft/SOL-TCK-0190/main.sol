enum Color {
    RED
    GREEN
}
val c = Color.GREEN
val n = match c {
    RED => 1
    GREEN => 2
}
print("all" .. n)
