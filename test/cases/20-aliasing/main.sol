package aliasing

struct Counter {

    var value: Long

    pub func new(value: Long = 0): Self {
        return Self { value: value, }
    }

    pub func increment(self) {
        self.value += 1
    }

    pub func value(self): Long {
        return self.value
    }
}

struct Main {

    pub func run(args: String...): Integer {
        // 32.1 reference aliasing: a and b share one object
        let original: Counter = Counter.new()
        let a: Counter = original
        let b: Counter = a
        a.increment()
        if b.value() != 1 { return 1 }
        if a != b { return 2 }

        // 32.2 separate objects are not identity-equal
        let p: Counter = Counter.new(value: 1)
        let q: Counter = Counter.new(value: 1)
        if p == q { return 3 }

        System.getOut().println("ok")
        return 0
    }
}
