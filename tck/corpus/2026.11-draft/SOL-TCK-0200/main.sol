abstract class Shape {
}
class Sq extends Shape {
}
class Ci extends Shape {
}
var s: Shape = Ci()
var n = match s {
    q: Sq => 1
    c: Ci => 2
    _ => 0
}
print("both" .. n)
