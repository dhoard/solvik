// expected E074: a thread cannot join itself; the error stays inside the body
package thread_self_join

struct Note {
    pub mut text: String
    pub mut handle: Thread?
}

func main() -> Int {
    mut note: Note = Note { text: "", handle: null }
    lock: Mutex = Mutex.new()
    t: Thread = Thread.new(ThreadDef { body: func() -> Int {
        // Wait until main publishes the handle, then join it from itself.
        mut h: Thread? = null
        while h == null {
            lock.lock()
            try {
                h = note.handle
            } finally {
                lock.unlock()
            }
        }
        try {
            h.join()
        } catch (e: Exception) {
            lock.lock()
            try {
                note.text = e.code .. ":" .. e.message
            } finally {
                lock.unlock()
            }
        }
        return 0
    } })
    t.start()
    lock.lock()
    try {
        note.handle = t
    } finally {
        lock.unlock()
    }
    t.join()
    throw note.text
}
