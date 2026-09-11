package p

class Pair<A, B> {

    av: A
    bv: B

    public static of(x: A, y: B): Self {
        return Self { av: x, bv: y, }
    }

    public both(x: B, y: B): A {
        return self.av
    }
}

class Main {

    public static run(args: String...): Long {
        let p: Pair<Long, String> = Pair.of(1, "s")
        let r: Long = p.both(2, 3)
        System.out().println(r)
        return 0
    }
}
