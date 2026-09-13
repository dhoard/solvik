package threads

struct Counter implements Runnable {

    n: Long

    public static func new(n: Long): Self {
        return Self { n: n, }
    }

    public func run(self): Void {
        System.getOut().println("worker " .. self.n)
    }
}

struct Main {

    public static func run(args: String...): Long {
        let t: Thread = Thread.new(Counter.new(3))
        t.start()
        t.join()
        System.getOut().println("joined")
        return 0
    }
}
