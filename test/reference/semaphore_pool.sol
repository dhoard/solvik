package semaphore_pool

// Bounded worker pool: at most 3 workers hold the gate at once. The total is
// independent of scheduling because every update is mutex-protected and the
// work count is fixed. Only main prints.

func main() -> Int {
    mut total: Int = 0
    lock: Mutex = Mutex.new()
    gate: Semaphore = Semaphore.new(3)
    worker: Func<Int> = func() -> Int {
        gate.acquire()
        try {
            lock.lock()
            try {
                total = total + 1
            } finally {
                lock.unlock()
            }
        } finally {
            gate.release()
        }
        return 0
    }
    mut i: Int = 0
    mut handles: Stack<Thread> = Stack.new()
    while i < 10 {
        wt: Thread = Thread.new(ThreadDef { body: worker })
        wt.start()
        handles.push(wt)
        i = i + 1
    }
    while handles.len() > 0 {
        h: Thread = handles.pop()
        r: Int = h.join()
        if r != 0 {
            return 1
        }
    }
    // Signaling use: a zero-count semaphore released from a worker is observed
    // by main through the shared counter above; also exercise release ordering
    // deterministically on the main thread.
    gate2: Semaphore = Semaphore.new(0)
    releaser: Func<Int> = func() -> Int {
        gate2.release()
        return 0
    }
    t: Thread = Thread.new(ThreadDef { body: releaser })
    t.start()
    gate2.acquire()
    tr: Int = t.join()
    if tr != 0 {
        return 2
    }
    if total != 10 {
        return 3
    }
    println("total=" .. total)
    return 0
}
