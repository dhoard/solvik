package selfassign

// Regression: assignment statements whose target begins with `self` (or any
// non-identifier expression) previously could not be parsed at all, and
// compound assignment to `self.field` mis-ordered the stack (LoadField ran
// after the value was evaluated, popping the value instead of the receiver).

class Counter {
    mut {
        count: Int
    }

    pub static new(): Counter {
        return Self { count: 10 }
    }

    pub tick(): Int {
        self.count += 5
        return self.count
    }

    pub reset(): Void {
        self.count = 1
    }

    pub sub(): Int {
        self.count -= 1
        return self.count
    }

    pub mul(): Int {
        self.count *= 3
        return self.count
    }

    pub read(): Int {
        return count
    }

    pub bump(v: Int): Int {
        count += v
        return count
    }
}

class Main {
    pub static run(args: String...): Int {
        c: Counter = Counter::new()
        stdout.println(c.tick()) // 15
        c.reset()
        stdout.println(c.tick()) // 6
        stdout.println(c.sub())  // 5
        stdout.println(c.mul())  // 15
        stdout.println(c.bump(100)) // 115
        return 0
    }
}
