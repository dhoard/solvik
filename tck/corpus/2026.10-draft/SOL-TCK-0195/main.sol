sealed class Shape {
}
class Sq extends Shape {
}
class Ci extends Shape {
}
val s: Shape = Sq()
val n = match s {
    q: Sq => 1
}
print(n)

print("EXECUTED-INVALID")
