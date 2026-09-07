// expected E081: joining a thread before .start() is a lifecycle error
package thread_unstarted

func main() -> Int {
    t: Thread = Thread.new(ThreadDef { body: func() -> Int { return 1 } })
    t.join()
    return 0
}
