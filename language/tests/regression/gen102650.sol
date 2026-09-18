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
        return 28
    }
}

val ifaceValue7: I3 = Imp4()

println(ifaceValue7.twice6())

func sw8(n: Int): String {
    switch (n) {
        case 5:
            return "a"
        case 6:
            return "b"
        default:
            return "d"
    }
}

println(sw8(5))

println(sw8(6))

println(sw8(42))
