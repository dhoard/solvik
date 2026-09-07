// expected E081: starting an already-started process is a lifecycle error
package process_double_start

func main() -> Int {
    p: Process = Process.new(ProcessDef { program: "/bin/true", args: [] })
    p.start()
    p.start()
    return 0
}
