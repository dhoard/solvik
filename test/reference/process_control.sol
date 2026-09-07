package process_control

// Launch failure is a catchable error in the caller; terminate() force-kills
// the direct child (128+9 on POSIX, native kill code elsewhere); child argv
// is passed directly with no shell interpolation of our arguments.

func main() -> Int {
    try {
        bad: Process = Process.new(ProcessDef { program: "/nonexistent/solvik-phase14", args: [] })
        bad.start()
        bad.join()
        return 1
    } catch (e: Exception) {
        if e.message != "cannot launch process '/nonexistent/solvik-phase14'" {
            return 2
        }
    }
    p: Process = Process.new(ProcessDef {
        program: "/bin/sh",
        args: ["-c", "sleep 30"],
    })
    p.start()
    p.terminate()
    code: Int = p.join()
    if code != 137 && code != 1 {
        return 3
    }
    p.terminate() // no-op once exit has been observed
    if !p.is_done() {
        return 4
    }
    q: Process = Process.new(ProcessDef {
        program: "/bin/sh",
        args: ["-c", "echo \"$0/$1\"", "alpha", "beta"],
    })
    q.start()
    q.stdin.close()
    line: String? = q.stdout.readLine()
    if line != "alpha/beta" {
        return 5
    }
    if q.join() != 0 {
        return 6
    }
    println("kill=" .. code)
    return 0
}
