// expected E075: recursive locking is a catchable runtime error
package mutex_misuse

func main() -> Int {
    m: Mutex = Mutex.new()
    m.lock()
    try {
        m.lock()
    } catch (e: Exception) {
        m.unlock()
        throw e.code .. ":" .. e.message
    }
    return 0
}
