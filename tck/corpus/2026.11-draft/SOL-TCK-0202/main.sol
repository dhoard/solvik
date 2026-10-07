class abstract Shape {
}
class Sq extends Shape {
}
class Ci extends Shape {
}
var s: Shape = Ci()
var n: Integer = match s {
    _ => 5
}
print("under" .. n)
