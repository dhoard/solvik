// expected E077: writing to stdin after close() is a catchable I/O error
package process_write_closed_stdin

func main() -> Int {
    p: Process = Process.new(ProcessDef { program: "/bin/sh", args: ["-c", "exit 0"] })
    p.start()
    p.stdin.close()
    try {
        p.stdin.write("late\n")
    } catch (e: Exception) {
        throw e.code .. ":" .. e.message
    }
    return 0
}
