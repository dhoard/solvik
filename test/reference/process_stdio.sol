package process_stdio

// External process stdio: direct argv, stdin write + EOF (idempotent close),
// line framing (CRLF strip, blank line, final unterminated line), UTF-8
// decoding, independent stderr, and preserved nonzero exit status.

func main() -> Int {
    p: Process = Process.new(ProcessDef {
        program: "/bin/sh",
        args: ["-c", "cat >/dev/null ; printf 'a\\r\\n\\ncaf\\303\\251 last' ; printf 'oops\\n' 1>&2 ; exit 3"],
    })
    p.start()
    p.stdin.write("ignored\n")
    p.stdin.close()
    p.stdin.close() // idempotent
    mut out: String = ""
    mut first: Bool = true
    while true {
        line: String? = p.stdout.readLine()
        if line != null {
            if !first {
                out = out .. "|"
            }
            out = out .. line
            first = false
        } else {
            break
        }
    }
    mut err: String = ""
    while true {
        line: String? = p.stderr.readLine()
        if line != null {
            err = err .. line
        } else {
            break
        }
    }
    code: Int = p.join()
    if code != 3 {
        return 1
    }
    // Repeated joins return the cached exit code; polling agrees.
    if p.join() != 3 || !p.is_done() {
        return 1
    }
    st: Int? = p.status()
    if st == null || st != 3 {
        return 1
    }
    if out != "a||café last" {
        return 2
    }
    if err != "oops" {
        return 3
    }
    println("out=" .. out .. " err=" .. err .. " code=" .. code)
    return 0
}
