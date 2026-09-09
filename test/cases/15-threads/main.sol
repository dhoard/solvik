package threads

class Counter implements Runnable {
    n: Int

    pub static new(n: Int): Self {
        return Self { n }
    }

    pub run(): Void {
        stdout.println("worker " .. n)
    }
}

class Main {
    pub static run(args: String...): Int {
        t: Thread = Thread::new(Counter::new(3))
        t.start()
        t.join()
        stdout.println("joined")
        return 0
    }
}
