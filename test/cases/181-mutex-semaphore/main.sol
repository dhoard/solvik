package syncprims

class Worker implements Runnable {

    mu: Mutex
    mutable hits: Long

    public static new(mu: Mutex): Self {
        return Self { mu: mu, hits: 0, }
    }

    public run(): Void {
        self.mu.lock()
        self.hits += 1
        self.mu.unlock()
    }

    public count(): Long {
        return self.hits
    }
}

class Main {

    public static run(args: String...): Long {
        // Mutex: two workers increment under the lock.
        let mu: Mutex = Mutex.new()
        let w1: Worker = Worker.new(mu)
        let w2: Worker = Worker.new(mu)
        let t1: Thread = Thread.new(w1)
        let t2: Thread = Thread.new(w2)
        t1.start()
        t2.start()
        t1.join()
        t2.join()
        System.out().println(w1.count() + w2.count())

        // Semaphore: acquire/release cycle.
        let sem: Semaphore = Semaphore.new(2)
        sem.acquire()
        sem.acquire()
        sem.release()
        sem.release()
        System.out().println("sync ok")
        return 0
    }
}
