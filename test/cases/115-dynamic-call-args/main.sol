package dynargs

struct Box {

    var v: Long

    pub func new(v: Long): Self {
        return Self { v: v, }
    }

    pub func bump(self, d: Long): Long {
        self.v = self.v + d
        return self.v
    }

    pub func addBoth(self, a: Long, b: Long): Long {
        self.v = self.v + a + b
        return self.v
    }
}

struct Main {

    pub func run(args: String...): Integer {
        let o: Object = Box.new(10)
        let r1: Object = o.bump(5)
        System.getOut().println(r1.toString())
        let r2: Object = o.addBoth(1, 2)
        System.getOut().println(r2.toString())
        // side-effecting argument must be evaluated before the call
        var x: Long = 0
        o.bump(x + 100)
        let r3: Object = o.bump(0)
        System.getOut().println(r3.toString())
        return 0
    }
}
