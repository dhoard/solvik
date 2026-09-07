package mutex_pool

// User-built drain-and-exit worker pool over a shared stack worklist (Phase 14
// plan section 3.2). No work is added after workers start, so the sum is
// independent of work assignment and lock acquisition order.

func main() -> Int {
    mut jobs: Stack<Int> = Stack.new()
    jobs.push(1)
    jobs.push(2)
    jobs.push(3)
    mut total: Int = 0
    lock: Mutex = Mutex.new()
    worker: Func<Int> = func() -> Int {
        while true {
            mut job: Int? = null
            lock.lock()
            try {
                if jobs.len() > 0 {
                    job = jobs.pop()
                }
            } finally {
                lock.unlock()
            }
            if job != null {
                result: Int = job * job
                lock.lock()
                try {
                    total = total + result
                } finally {
                    lock.unlock()
                }
            } else {
                return 0
            }
        }
        return 0
    }
    a: Thread = Thread.new(ThreadDef { body: worker })
    a.start()
    b: Thread = Thread.new(ThreadDef { body: worker })
    b.start()
    ac: Int = a.join()
    bc: Int = b.join()
    if ac != 0 || bc != 0 {
        return 1
    }
    println(total) // 14
    return 0
}
