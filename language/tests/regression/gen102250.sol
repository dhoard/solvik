val xs1: List<Int> = List(96, 6)

xs1.add(99)

println(xs1.size)

println(xs1.get(0))

class Box2<T> {
    var value: T
    Box2(value: T) {
        this.value = value
    }
    func get(): T {
        return this.value
    }
}

val box3: Box2<Boolean> = Box2(true)

println(box3.get())

println(box3 is Any)

val s4: Set<String> = Set("a", "b", "a")

println(s4.size)

val st5: Stack<Int> = Stack(1, 2, 3)

println(st5.size)
