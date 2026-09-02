package reference_stdlib_files
func main() -> int {
    // temp dir + file ops
    dir: string = file.tempDir("solviktest")
    f: string = path.join(dir, "a.txt")
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
    sub: string = path.join(dir, "sub")
    file.mkdir(sub)
    if !file.isDir(sub) {
        return 4
    }
    file.write(path.join(sub, "b.txt"), "x")
    entries: list<string> = file.list(dir)
    if entries.len() != 2 {
        return 5
    }
    // rename + remove
    g: string = path.join(dir, "renamed.txt")
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
    // process.capture: run python printing a value
    proc: map<string, any> = process.capture("python3", "-c", "print(42)")
    if proc["status"] != 0 {
        return 10
    }
    if proc["stdout"] != "42\n" {
        return 11
    }
    // program args (empty here)
    if process.args().len() != 0 {
        return 12
    }
    return 0
}
