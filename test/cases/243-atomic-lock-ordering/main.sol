package atomicordering

// Two threads request the same lock pair in opposite source order:
// atomic(a, b) and atomic(b, a). The runtime acquires monitors in a
// deterministic order derived from each object's hidden lock-order id, so the
// requests serialize instead of deadlocking.

struct Counter {

    var value: Long

    pub func new(): Self {
        return Self { value: 0, }
    }

    pub func increment(self) {
        self.value += 1
    }

    pub func get(self): Long {
        return self.value
    }
}

struct Mover implements Runnable {

    first: Counter
    second: Counter
    start: Semaphore
    rounds: Long

    pub func new(first: Counter, second: Counter, start: Semaphore, rounds: Long): Self {
        return Self { first: first, second: second, start: start, rounds: rounds, }
    }

    pub func run(self) {
        self.start.acquire()
        var i: Long = 0
        while i < self.rounds {
            atomic(self.first, self.second) {
                self.first.increment()
                self.second.increment()
            }
            i += 1
        }
    }
}

struct Main {

    pub func run(args: String...): Integer {
        let a: Counter = Counter.new()
        let b: Counter = Counter.new()
        let start: Semaphore = Semaphore.new(0)
        let ab: Thread = Thread.new(Mover.new(a, b, start, 2000))
        let ba: Thread = Thread.new(Mover.new(b, a, start, 2000))
        ab.start()
        ba.start()
        start.release()
        start.release()
        ab.join()
        ba.join()
        System.getOut().println(a.get())
        System.getOut().println(b.get())
        return 0
    }
}
