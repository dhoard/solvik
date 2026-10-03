abstract class Shape {
}
class Sq extends Shape {
}
class Ci extends Shape {
}
val s: Shape = Ci()
val n = match s {
    q: Sq => 1
    _ => 0
}
print("abstract" .. n)
