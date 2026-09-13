package callargs

struct Counter {

    mutable n: Long

    public static func new(): Self {
        return Self { n: 0, }
    }

    public func bump(self): Long {
        self.n += 1
        return self.n
    }

    public func useBump(self, x: Long): Long {
        return x + bump()
    }

    public func count(self): Long {
        return self.n
    }
}

struct Main {

    public static func run(args: String...): Long {
        let c: Counter = Counter.new()
        // The argument is evaluated exactly once, before the call body.
        let r: Long = c.useBump(c.bump())
        System.getOut().println(r)
        System.getOut().println(c.count())
        return 0
    }
}
