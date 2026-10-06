enum Color {
    RED
    GREEN
}
var c = Color.RED
var v: Integer = match c {
    RED => 1
    GREEN => 2L
}
print(v)

print("EXECUTED-INVALID")
