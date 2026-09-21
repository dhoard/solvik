val s1: Set<String> = Set("a", "b", "a")

println(s1.size)

val st2: Stack<Integer> = Stack(1, 2, 3)

println(st2.size)

interface I3 {
    func base5(): Integer
    func twice6(): Integer {
        return this.base5() * 2
    }
}

class Imp4 implements I3 {
    func base5(): Integer {
        return 30
    }
}

val ifaceValue7: I3 = Imp4()

println(ifaceValue7.twice6())
