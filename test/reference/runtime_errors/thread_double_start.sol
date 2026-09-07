// expected E081: starting an already-started thread is a lifecycle error
package thread_double_start

func main() -> Int {
    t: Thread = Thread.new(ThreadDef { body: func() -> Int { return 1 } })
    t.start()
    t.start()
    return 0
}
