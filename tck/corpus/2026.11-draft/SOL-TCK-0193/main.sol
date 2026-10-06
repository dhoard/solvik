abstract class A {
}
mutable class B extends A {
}
class C extends B {
}
var a: A = C()
var n = match a {
    b: B => 20
    c: C => 10
    _ => 0
}
print(n)

print("EXECUTED-INVALID")
