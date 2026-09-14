package selfassign

// Regression: assignment statements whose target begins with `self` (or any
// non-identifier expression) previously could not be parsed at all, and
// compound assignment to `self.field` mis-ordered the stack (LoadField ran
// after the value was evaluated, popping the value instead of the receiver).

struct Counter {

    var count: Long

    pub func new(): Self {
        return Self { count: 10, }
    }

    pub func tick(self): Long {
        self.count += 5
        return self.count
    }

    pub func reset(self) {
        self.count = 1
    }

    pub func sub(self): Long {
        self.count -= 1
        return self.count
    }

    pub func mul(self): Long {
        self.count *= 3
        return self.count
    }

    pub func read(self): Long {
        return self.count
    }

    pub func bump(self, v: Long): Long {
        self.count += v
        return self.count
    }
}

struct Main {

    pub func run(args: String...): Integer {
        let c: Counter = Counter.new()
        System.getOut().println(c.tick()) // 15
        c.reset()
        System.getOut().println(c.tick()) // 6
        System.getOut().println(c.sub())  // 5
        System.getOut().println(c.mul())  // 15
        System.getOut().println(c.bump(100)) // 115
        return 0
    }
}
