package monitorincrements

// The automatic per-struct monitor serializes concurrent mutation of one
// struct instance. Every method call below reenters the same counter's
// exclusive monitor, so the final value is exact.

struct Counter {

    var value: Long

    pub func new(): Self {
        return Self { value: 0, }
    }

    pub func increment(self) {
        self.incrementBy(1)
    }

    func incrementBy(self, amount: Long) {
        self.value += amount
    }

    pub func get(self): Long {
        return self.value
    }
}

struct Worker implements Runnable {

    counter: Counter
    rounds: Long

    pub func new(counter: Counter, rounds: Long): Self {
        return Self { counter: counter, rounds: rounds, }
    }

    pub func run(self) {
        var i: Long = 0
        while i < self.rounds {
            atomic(self.counter) {
                self.counter.increment()
            }
            i += 1
        }
    }
}

struct Main {

    pub func run(args: String...): Integer {
        let counter: Counter = Counter.new()
        let threads: List<Thread> = List<Thread>.new()
        var i: Long = 0
        while i < 8 {
            let worker: Thread = Thread.new(Worker.new(counter, 500))
            worker.start()
            threads.add(worker)
            i += 1
        }
        for thread in threads {
            thread.join()
        }
        System.getOut().println(counter.get())
        return 0
    }
}
