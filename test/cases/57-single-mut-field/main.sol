package smf

struct Counter {

    mutable count: Long

    public static func new(): Self {
        return Self { count: 0, }
    }

    public func tick(self): Long {
        self.count += 1
        return self.count
    }
}

struct Main {

    public static func run(args: String...): Long {
        let c: Counter = Counter.new()
        c.tick()
        System.getOut().println(c.tick())
        return 0
    }
}
