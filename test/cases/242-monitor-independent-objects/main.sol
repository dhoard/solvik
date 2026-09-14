package monitorindependent

// Different struct instances have independent monitors. One thread holds
// account A's monitor while the main thread mutates unrelated account B; if
// there were a single global struct lock this program would deadlock and the
// conformance timeout would fail the build.

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

struct Locker implements Runnable {

    target: Counter
    entered: Semaphore
    release: Semaphore

    pub func new(target: Counter, entered: Semaphore, release: Semaphore): Self {
        return Self { target: target, entered: entered, release: release, }
    }

    pub func run(self) {
        atomic(self.target) {
            self.entered.release()
            self.release.acquire()
        }
    }
}

struct Main {

    pub func run(args: String...): Integer {
        let a: Counter = Counter.new()
        let b: Counter = Counter.new()
        let entered: Semaphore = Semaphore.new(0)
        let release: Semaphore = Semaphore.new(0)
        let thread: Thread = Thread.new(Locker.new(a, entered, release))
        thread.start()

        // The worker now holds A's monitor and is waiting on `release`.
        entered.acquire()
        b.increment()
        b.increment()
        b.increment()
        System.getOut().println(b.get())

        release.release()
        thread.join()
        System.getOut().println(a.get())
        System.getOut().println("done")
        return 0
    }
}
