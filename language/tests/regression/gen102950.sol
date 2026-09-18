val s1: Set<String> = Set("a", "b", "a")

println(s1.size)

val st2: Stack<Int> = Stack(1, 2, 3)

println(st2.size)

interface I3 {
    func base5(): Int
    func twice6(): Int {
        return this.base5() * 2
    }
}

class Imp4 implements I3 {
    func base5(): Int {
        return 30
    }
}

val ifaceValue7: I3 = Imp4()

println(ifaceValue7.twice6())
