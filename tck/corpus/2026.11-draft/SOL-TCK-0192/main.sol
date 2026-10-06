abstract class A {
}
mutable class B extends A {
}
class C extends B {
}
var a: A = C()
var n = match a {
    c: C => 10
    b: B => 20
    _ => 0
}
print("first" .. n)
