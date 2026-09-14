package syncprims

struct Worker implements Runnable {

    mu: Mutex
    var hits: Long

    pub func new(mu: Mutex): Self {
        return Self { mu: mu, hits: 0, }
    }

    pub func run(self) {
        self.mu.lock()
        self.hits += 1
        self.mu.unlock()
    }

    pub func count(self): Long {
        return self.hits
    }
}

struct Main {

    pub func run(args: String...): Integer {
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
