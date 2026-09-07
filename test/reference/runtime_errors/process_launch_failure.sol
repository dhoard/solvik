// expected E076: launching a missing program is a catchable error in the caller
package process_launch_failure

func main() -> Int {
    try {
        p: Process = Process.new(ProcessDef { program: "/nonexistent/solvik-phase14", args: [] })
        p.start()
        p.join()
    } catch (e: Exception) {
        throw e.code .. ":" .. e.message
    }
    return 0
}
