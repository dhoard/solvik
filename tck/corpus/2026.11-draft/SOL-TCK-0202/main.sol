abstract class Shape {
}
class Sq extends Shape {
}
class Ci extends Shape {
}
val s: Shape = Ci()
val n = match s {
    _ => 5
}
print("under" .. n)
