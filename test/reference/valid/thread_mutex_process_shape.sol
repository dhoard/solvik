package thread_mutex_process_shape

// Compile coverage of the full Phase 14 type surface: handles carried in
// struct payloads, record literals, stream properties, and every control
// method. Also runs to a fixed result.

struct WorkerInfo {
    handle: Thread
    lock: Mutex
}

func probe(t: Thread) -> Int {
    s: Int? = t.status()
    if t.is_done() {
        return 999
    }
    if s != null {
        return s
    }
    return 0
}

func main() -> Int {
    lock: Mutex = mutex()
    info: WorkerInfo = WorkerInfo {
        handle: Thread.start(ThreadDef { body: func() -> Int {
            lock.lock()
            try {
                // critical section
            } finally {
                lock.unlock()
            }
            return 5
        } }),
        lock: lock,
    }
    r: Int = info.handle.join()
    after: Int = probe(info.handle)
    if r != 5 || after != 999 {
        return 1
    }

    p: Process = Process.start(ProcessDef {
        program: "/bin/sh",
        args: ["-c", "printf 'ping\\n' ; printf 'pong\\n' 1>&2"],
    })
    out: OutStream = p.stdin
    sin: InStream = p.stdout
    errIn: InStream = p.stderr
    out.write("hello\n")
    out.close()
    l1: String? = sin.readLine()
    l2: String? = sin.readLine()
    e1: String? = errIn.readLine()
    e2: String? = errIn.readLine()
    code: Int = p.join()
    st: Int? = p.status()
    done: Bool = p.is_done()
    p.terminate()
    if l1 != "ping" || l2 != null || e1 != "pong" || e2 != null {
        return 2
    }
    if code != 0 || st == null || !done {
        return 3
    }
    n: Int = args().len()
    println("r=" .. r .. " after=" .. after .. " code=" .. code .. " args=" .. n)
    return 0
}
