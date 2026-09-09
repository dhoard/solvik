module threads

class Counter implements Runnable {

    n: Long

    public static new(n: Long): Self {
        return Self { n: n, }
    }

    public run(): Void {
        stdout.println("worker " .. self.n)
    }
}

class Main {

    public static run(args: String...): Long {
        t: Thread = Thread.new(Counter.new(3))
        t.start()
        t.join()
        stdout.println("joined")
        return 0
    }
}
