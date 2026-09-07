// expected E075: unlocking from a different thread is a catchable runtime error
package mutex_cross_thread

func main() -> Int {
    m: Mutex = Mutex.new()
    m.lock()
    t: Thread = Thread.new(ThreadDef { body: func() -> Int {
        try {
            m.unlock()
        } catch (e: Exception) {
            // contained in the worker; status becomes 1
        }
        return 0
    } })
    t.start()
    t.join()
    m.unlock()
    // Unlocking an unlocked mutex also raises E075.
    try {
        m.unlock()
    } catch (e: Exception) {
        throw e.code .. ":" .. e.message
    }
    return 0
}
