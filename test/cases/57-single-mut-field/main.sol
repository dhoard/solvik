package smf

struct Counter {

    var count: Long

    pub func new(): Self {
        return Self { count: 0, }
    }

    pub func tick(self): Long {
        self.count += 1
        return self.count
    }
}

struct Main {

    pub func run(args: String...): Integer {
        let c: Counter = Counter.new()
        c.tick()
        System.getOut().println(c.tick())
        return 0
    }
}
