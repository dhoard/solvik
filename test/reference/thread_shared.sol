package thread_shared

// Shared-heap fork-join: scalar/aggregate captures, bound methods, nested
// workers, closure lifetime, preserved assignment copies, handle identity,
// repeated joins, completion polling, and nonzero worker returns. Only main
// prints; every asserted value is independent of lock acquisition order.

struct Counter {
    pub mut total: Int
}

struct Bank {
    pub mut balance: Int

    pub mut func deposit(amount: Int) -> Void {
        self.balance = self.balance + amount
    }
}

func main() -> Int {
    mut counter: Counter = Counter { total: 0 }
    mut bank: Bank = Bank { balance: 0 }
    lock: Mutex = Mutex.new()

    worker: Func<Int> = func() -> Int {
        mut i: Int = 0
        while i < 250 {
            lock.lock()
            try {
                counter.total = counter.total + 1
                bank.deposit(2)
            } finally {
                lock.unlock()
            }
            i = i + 1
        }
        return 7
    }

    // A worker that starts and joins a child worker before returning.
    nested: Func<Int> = func() -> Int {
        child: Thread = Thread.new(ThreadDef { body: func() -> Int {
            lock.lock()
            try {
                counter.total = counter.total + 1000
            } finally {
                lock.unlock()
            }
            return 3
        } })
        child.start()
        return child.join()
    }

    a: Thread = Thread.new(ThreadDef { body: worker })
    a.start()
    b: Thread = Thread.new(ThreadDef { body: worker })
    b.start()
    n: Thread = Thread.new(ThreadDef { body: nested })
    n.start()

    ra: Int = a.join()
    rb: Int = b.join()
    rn: Int = n.join()
    if ra != 7 || rb != 7 || rn != 3 {
        return 1
    }
    // Repeated joins return the cached result.
    if a.join() != 7 || b.join() != 7 || n.join() != 3 {
        return 1
    }
    // Completion polling after join.
    sa: Int? = a.status()
    if sa == null || sa != 7 || !a.is_done() || !b.is_done() || !n.is_done() {
        return 1
    }
    // Handle identity: copies of a handle are the same handle.
    acopy: Thread = a
    if acopy.join() != 7 || !(acopy == a) {
        return 1
    }
    // Ordinary assignment still copies values: mutating the copy leaves the
    // shared original untouched.
    cp: Counter = counter
    cp.total = cp.total + 555
    expected: Int = 2 * 250 + 1000
    if counter.total != expected || bank.balance != 2 * 250 * 2 {
        return 1
    }
    println("total=" .. counter.total .. " balance=" .. bank.balance .. " copy=" .. cp.total)
    return 0
}
