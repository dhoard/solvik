abstract class Shape {
}
class Sq extends Shape {
}
class Ci extends Shape {
}
val s: Shape = Ci()
val n = match s {
    q: Sq => 1
    c: Ci => 2
    _ => 0
}
print("both" .. n)
