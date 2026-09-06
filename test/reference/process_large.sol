package process_large

// Output far larger than one pipe buffer on stderr while the parent only
// reads stdout: ignoring stderr must not stall the child. join() waits for
// exit only; buffered output stays readable after the join.

func main() -> Int {
    p: Process = Process.start(ProcessDef {
        program: "/bin/sh",
        args: ["-c", "i=0; while [ $i -lt 10000 ]; do echo noiseline 1>&2; i=$((i+1)); done; echo done"],
    })
    p.stdin.close()
    code: Int = p.join() // join before reading any output
    line: String? = p.stdout.readLine()
    if code != 0 || line != "done" {
        return 1
    }
    // Drain stderr fully after the join.
    mut count: Int = 0
    while true {
        l: String? = p.stderr.readLine()
        if l != null {
            count = count + 1
        } else {
            break
        }
    }
    if count != 10000 {
        return 2
    }
    println("code=" .. code .. " lines=" .. count)
    return 0
}
