class abstract A {
}
class mutable B extends A {
}
class C extends B {
}
var a: A = C()
var n: Integer = match a {
    c: C => 10
    b: B => 20
    _ => 0
}
print("first" .. n)
