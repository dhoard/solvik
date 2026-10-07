class abstract Shape {
}
class Sq extends Shape {
}
class Ci extends Shape {
}
var s: Shape = Ci()
var n: Integer = match s {
    q: Sq => 1
    _ => 0
}
print("abstract" .. n)
