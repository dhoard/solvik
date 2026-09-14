package threads

struct Counter implements Runnable {

    n: Long

    pub func new(n: Long): Self {
        return Self { n: n, }
    }

    pub func run(self) {
        System.getOut().println("worker " .. self.n)
    }
}

struct Main {

    pub func run(args: String...): Integer {
        let t: Thread = Thread.new(Counter.new(3))
        t.start()
        t.join()
        System.getOut().println("joined")
        return 0
    }
}
