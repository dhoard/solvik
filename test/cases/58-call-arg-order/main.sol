package callargs

struct Counter {

    var n: Long

    pub func new(): Self {
        return Self { n: 0, }
    }

    pub func bump(self): Long {
        self.n += 1
        return self.n
    }

    pub func useBump(self, x: Long): Long {
        return x + bump()
    }

    pub func count(self): Long {
        return self.n
    }
}

struct Main {

    pub func run(args: String...): Integer {
        let c: Counter = Counter.new()
        // The argument is evaluated exactly once, before the call body.
        let r: Long = c.useBump(c.bump())
        System.getOut().println(r)
        System.getOut().println(c.count())
        return 0
    }
}
