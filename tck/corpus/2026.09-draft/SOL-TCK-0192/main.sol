sealed class A {
}
open class B extends A {
}
class C extends B {
}
val a: A = C()
val n = match a {
    c: C => 10
    b: B => 20
}
print("first" .. n)
