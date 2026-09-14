package p

struct Pair<A, B> {

    av: A
    bv: B

    public func of(x: A, y: B): Self {
        return Self { av: x, bv: y, }
    }

    public func both(self, x: B, y: B): A {
        return self.av
    }
}

struct Main {

    public func run(args: String...): Integer {
        let p: Pair<Long, String> = Pair.of(1, "s")
        let r: Long = p.both(2, 3)
        System.getOut().println(r)
        return 0
    }
}
