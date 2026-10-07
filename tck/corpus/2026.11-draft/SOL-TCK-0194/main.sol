enum Color {
    RED
    GREEN
}
var c: Color = Color.RED
var n: Integer = match c {
    RED => 1
    RED => 2
    GREEN => 3
}
print(n)

print("EXECUTED-INVALID")
