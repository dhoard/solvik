module dynargs

class Box {

    public mutable v: Long

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
        o: Object = Box.new(10)
        r1: Object = o.bump(5)
        stdout.println(r1.toString())
        r2: Object = o.addBoth(1, 2)
        stdout.println(r2.toString())
        // side-effecting argument must be evaluated before the call
        mutable x: Long = 0
        o.bump(x + 100)
        r3: Object = o.bump(0)
        stdout.println(r3.toString())
        return 0
    }
}
