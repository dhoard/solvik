package dynargs

struct Box {

    mutable v: Long

    public func new(v: Long): Self {
        return Self { v: v, }
    }

    public func bump(self, d: Long): Long {
        self.v = self.v + d
        return self.v
    }

    public func addBoth(self, a: Long, b: Long): Long {
        self.v = self.v + a + b
        return self.v
    }
}

struct Main {

    public func run(args: String...): Integer {
        let o: Object = Box.new(10)
        let r1: Object = o.bump(5)
        System.getOut().println(r1.toString())
        let r2: Object = o.addBoth(1, 2)
        System.getOut().println(r2.toString())
        // side-effecting argument must be evaluated before the call
        let mutable x: Long = 0
        o.bump(x + 100)
        let r3: Object = o.bump(0)
        System.getOut().println(r3.toString())
        return 0
    }
}
