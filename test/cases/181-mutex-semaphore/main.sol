package syncprims

struct Worker implements Runnable {

    mu: Mutex
    mutable hits: Long

    public func new(mu: Mutex): Self {
        return Self { mu: mu, hits: 0, }
    }

    public func run(self): Void {
        self.mu.lock()
        self.hits += 1
        self.mu.unlock()
    }

    public func count(self): Long {
        return self.hits
    }
}

struct Main {

    public func run(args: String...): Long {
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
        System.getOut().println(w1.count() + w2.count())

        // Semaphore: acquire/release cycle.
        let sem: Semaphore = Semaphore.new(2)
        sem.acquire()
        sem.acquire()
        sem.release()
        sem.release()
        System.getOut().println("sync ok")
        return 0
    }
}
