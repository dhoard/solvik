module aliasing

class Counter {

    mutable value: Long

    public static new(value: Long = 0): Self {
        return Self { value: value, }
    }

    public increment(): Void {
        self.value += 1
    }

    public value(): Long {
        return self.value
    }
}

class Main {

    public static run(args: String...): Long {
        // 32.1 reference aliasing: a and b share one object
        original: Counter = Counter.new()
        a: Counter = original
        b: Counter = a
        a.increment()
        if b.value() != 1 { return 1 }
        if a != b { return 2 }

        // 32.2 separate objects are not identity-equal
        p: Counter = Counter.new(value: 1)
        q: Counter = Counter.new(value: 1)
        if p == q { return 3 }

        stdout.println("ok")
        return 0
    }
}
