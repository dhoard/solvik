class mutable A {
    method f(): Integer {
        return 1
    }
}
class B extends A {
    method override f(): Integer {
        return 2
    }
}
println(B().f())
