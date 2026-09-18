class Box1<T> {
    var value: T
    Box1(value: T) {
        this.value = value
    }
    func get(): T {
        return this.value
    }
}

val box2: Box1<String> = Box1("b2")

println(box2.get())

println(box2 is Any)

func either3(flag: Boolean): Any {
    return if (flag) { 'a' } else { false }
}

println(either3(true) is Any)

println(either3(false) is Any)

println(either3(true) != false)
