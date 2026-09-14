package threads

struct Counter implements Runnable {

    n: Long

    public func new(n: Long): Self {
        return Self { n: n, }
    }

    public func run(self): Void {
        System.getOut().println("worker " .. self.n)
    }
}

struct Main {

    public func run(args: String...): Integer {
        let t: Thread = Thread.new(Counter.new(3))
        t.start()
        t.join()
        System.getOut().println("joined")
        return 0
    }
}
