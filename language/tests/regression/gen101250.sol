class Box1<T> {
    var mutable value: T
    Box1(value: T) {
        this.value = value
    }
    func get(): T {
        return this.value
    }
}

var box2: Box1<Boolean> = Box1(false)

println(box2.get())

println(box2 is Any)

func either3(flag: Boolean): Any {
    return if (flag) {
        false
    }
    else {
        24
    }
}

println(either3(true) is Any)

println(either3(false) is Any)

println(either3(true) != false)
