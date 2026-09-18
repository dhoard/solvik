open class A {
    func f(): Int {
        return 1
    }
}
class B extends A {
    override func f(): Int {
        return 2
    }
}
println(B().f())
