interface Shape {
    method sides(): Integer
}
class Sq implements Shape {
    Sq() {
    }

    method sides(): Integer {
        return 4
    }
}
var a: Shape = Sq()
var b: Shape = Sq()
var c: Shape = a
print("if" .. (a === b) .. (a === c))
