open class A {
    func f(): Integer {
        return 1
    }
}
class B extends A {
    override func f(): Integer {
        return 2
    }
}
println(B().f())
