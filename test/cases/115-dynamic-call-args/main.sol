package dynargs

class Box {

    mutable v: Long

    public static new(v: Long): Self {
        return Self { v: v, }
    }

    public bump(d: Long): Long {
        self.v = self.v + d
        return self.v
    }

    public addBoth(a: Long, b: Long): Long {
        self.v = self.v + a + b
        return self.v
    }
}

class Main {

    public static run(args: String...): Long {
        let o: Object = Box.new(10)
        let r1: Object = o.bump(5)
        System.out().println(r1.toString())
        let r2: Object = o.addBoth(1, 2)
        System.out().println(r2.toString())
        // side-effecting argument must be evaluated before the call
        let mutable x: Long = 0
        o.bump(x + 100)
        let r3: Object = o.bump(0)
        System.out().println(r3.toString())
        return 0
    }
}
