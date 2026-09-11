package threads

class Counter implements Runnable {

    n: Long

    public static new(n: Long): Self {
        return Self { n: n, }
    }

    public run(): Void {
        System.out().println("worker " .. self.n)
    }
}

class Main {

    public static run(args: String...): Long {
        let t: Thread = Thread.new(Counter.new(3))
        t.start()
        t.join()
        System.out().println("joined")
        return 0
    }
}
