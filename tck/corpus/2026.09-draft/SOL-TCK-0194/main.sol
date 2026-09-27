enum Color {
    RED
    GREEN
}
val c = Color.RED
val n = match c {
    RED => 1
    RED => 2
    GREEN => 3
}
print(n)

print("EXECUTED-INVALID")
