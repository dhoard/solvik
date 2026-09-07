package reference_stdlib_files
func main() -> Int {
    // temp dir + file ops
    dir: String = file.tempDir("solviktest")
    f: String = path.join(dir, "a.txt")
    file.write(f, "line1\n")
    file.append(f, "line2\n")
    if file.read(f) != "line1\nline2\n" {
        return 1
    }
    if !file.isFile(f) {
        return 2
    }
    if file.size(f) != 12 {
        return 3
    }
    // directory ops
    sub: String = path.join(dir, "sub")
    file.mkdir(sub)
    if !file.isDir(sub) {
        return 4
    }
    file.write(path.join(sub, "b.txt"), "x")
    entries: List<String> = file.list(dir)
    if entries.len() != 2 {
        return 5
    }
    // rename + remove
    g: String = path.join(dir, "renamed.txt")
    file.rename(f, g)
    if !file.exists(g) || file.exists(f) {
        return 6
    }
    file.remove(g)
    file.remove(path.join(sub, "b.txt"))
    if file.exists(g) {
        return 7
    }
    // path ops
    if path.basename(g) != "renamed.txt" || path.ext(g) != ".txt" {
        return 8
    }
    if path.join("a", "b") != "a/b" && path.join("a", "b") != "a\\b" {
        return 9
    }
    // Process: run python printing a value; read stdout lines, join for status
    proc: Process = Process.new(ProcessDef { program: "python3", args: ["-c", "print(42)"] })
    proc.start()
    proc.stdin.close()
    mut captured: String = ""
    while true {
        line: String? = proc.stdout.readLine()
        if line != null {
            captured = captured .. line .. "\n"
        } else {
            break
        }
    }
    if proc.join() != 0 {
        return 10
    }
    if captured != "42\n" {
        return 11
    }
    // stderr and nonzero status are preserved
    errp: Process = Process.new(ProcessDef { program: "/bin/sh", args: ["-c", "printf 'bad\\n' 1>&2 ; exit 5"] })
    errp.start()
    errp.stdin.close()
    eline: String? = errp.stderr.readLine()
    if errp.join() != 5 || eline != "bad" {
        return 13
    }
    // program args (empty here)
    if args().len() != 0 {
        return 12
    }
    return 0
}
