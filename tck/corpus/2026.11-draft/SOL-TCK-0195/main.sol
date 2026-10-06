abstract class Shape {
}
class Sq extends Shape {
}
class Ci extends Shape {
}
var s: Shape = Sq()
var n = match s {
    q: Sq => 1
    c: Ci => 2
}
print(n)

print("EXECUTED-INVALID")
