package selfassign

// Regression: assignment statements whose target begins with `self` (or any
// non-identifier expression) previously could not be parsed at all, and
// compound assignment to `self.field` mis-ordered the stack (LoadField ran
// after the value was evaluated, popping the value instead of the receiver).

class Counter {

    mutable count: Long

    public static new(): Self {
        return Self { count: 10, }
    }

    public tick(): Long {
        self.count += 5
        return self.count
    }

    public reset(): Void {
        self.count = 1
    }

    public sub(): Long {
        self.count -= 1
        return self.count
    }

    public mul(): Long {
        self.count *= 3
        return self.count
    }

    public read(): Long {
        return self.count
    }

    public bump(v: Long): Long {
        self.count += v
        return self.count
    }
}

class Main {

    public static run(args: String...): Long {
        let c: Counter = Counter.new()
        System.out().println(c.tick()) // 15
        c.reset()
        System.out().println(c.tick()) // 6
        System.out().println(c.sub())  // 5
        System.out().println(c.mul())  // 15
        System.out().println(c.bump(100)) // 115
        return 0
    }
}
