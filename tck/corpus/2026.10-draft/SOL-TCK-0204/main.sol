enum Color {
    RED
    GREEN
}
val c = Color.RED
val v: Integer = match c {
    RED => 1
    GREEN => 2L
}
print(v)

print("EXECUTED-INVALID")
