interface Shape {
    func sides(): Integer
}
class Sq implements Shape {
    Sq() {
    }

    func sides(): Integer {
        return 4
    }
}
val a: Shape = Sq()
val b: Shape = Sq()
val c: Shape = a
print("if" .. (a === b) .. (a === c))
