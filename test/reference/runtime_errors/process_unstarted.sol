// expected E081: reading status of a process before .start() is a lifecycle error
package process_unstarted

func main() -> Int {
    p: Process = Process.new(ProcessDef { program: "/bin/true", args: [] })
    p.status()
    return 0
}
