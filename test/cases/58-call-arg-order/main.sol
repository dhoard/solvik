package callargs

class Counter {

    mutable n: Long

    public static new(): Self {
        return Self { n: 0, }
    }

    public bump(): Long {
        self.n += 1
        return self.n
    }

    public useBump(x: Long): Long {
        return x + bump()
    }

    public count(): Long {
        return self.n
    }
}

class Main {

    public static run(args: String...): Long {
        let c: Counter = Counter.new()
        // The argument is evaluated exactly once, before the call body.
        let r: Long = c.useBump(c.bump())
        System.out().println(r)
        System.out().println(c.count())
        return 0
    }
}
