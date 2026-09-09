package aliasing

class Counter {
    mut {
        value: Int
    }

    pub static new(value: Int = 0): Self {
        return Self { value }
    }

    pub increment(): Void {
        value += 1
    }

    pub value(): Int {
        return value
    }
}

class Main {
    pub static run(args: String...): Int {
        // 32.1 reference aliasing: a and b share one object
        original: Counter = Counter::new()
        a: Counter = original
        b: Counter = a
        a.increment()
        if b.value() != 1 { return 1 }
        if a != b { return 2 }

        // 32.2 separate objects are not identity-equal
        p: Counter = Counter::new(value: 1)
        q: Counter = Counter::new(value: 1)
        if p == q { return 3 }

        stdout.println("ok")
        return 0
    }
}
