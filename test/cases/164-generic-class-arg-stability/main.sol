package p

struct Pair<A, B> {

    av: A
    bv: B

    pub func of(x: A, y: B): Self {
        return Self { av: x, bv: y, }
    }

    pub func both(self, x: B, y: B): A {
        return self.av
    }
}

struct Main {

    pub func run(args: String...): Integer {
        let p: Pair<Long, String> = Pair.of(1, "s")
        let r: Long = p.both(2, 3)
        System.getOut().println(r)
        return 0
    }
}
