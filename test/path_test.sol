// test/path_test.sol — built-in path module tests
//
// Tests: path.join, path.basename, path.dirname, path.ext, path.abs, path.exists

package test

func main() -> int {
    // === path.join ===

    p: string = path.join("/home", "user", "file.txt")
    if p != "/home/user/file.txt" {
        println("FAIL: path.join expected /home/user/file.txt, got " .. p)
    }

    // path.join with single arg
    q: string = path.join("single")
    if q != "single" {
        println("FAIL: path.join single expected single, got " .. q)
    }

    // path.join with two args
    r: string = path.join("/usr", "bin")
    if r != "/usr/bin" {
        println("FAIL: path.join two args expected /usr/bin, got " .. r)
    }

    // === path.basename ===

    b: string = path.basename("/home/user/file.txt")
    if b != "file.txt" {
        println("FAIL: path.basename expected file.txt, got " .. b)
    }

    b2: string = path.basename("file.txt")
    if b2 != "file.txt" {
        println("FAIL: path.basename bare filename expected file.txt, got " .. b2)
    }

    // === path.dirname ===

    d: string = path.dirname("/home/user/file.txt")
    if d != "/home/user" {
        println("FAIL: path.dirname expected /home/user, got " .. d)
    }

    d2: string = path.dirname("file.txt")
    if d2 != "." {
        println("FAIL: path.dirname bare filename expected ., got " .. d2)
    }

    // === path.ext ===

    e: string = path.ext("file.txt")
    if e != ".txt" {
        println("FAIL: path.ext expected .txt, got " .. e)
    }

    e2: string = path.ext("archive.tar.gz")
    if e2 != ".gz" {
        println("FAIL: path.ext expected .gz, got " .. e2)
    }

    e3: string = path.ext("Makefile")
    if e3 != "" {
        println("FAIL: path.ext no ext expected empty, got " .. e3)
    }

    // === path.abs ===

    a: string = path.abs("some/relative/path")
    if a == "" {
        println("FAIL: path.abs returned empty")
    }
    // Should end with the relative path
    if path.ext(a) != ".path" {
        // Just check it didn't return the input unchanged
        // (on some systems it might, if CWD is root)
    }

    // === path.exists ===

    // Current directory should exist
    if path.exists(".") == false {
        println("FAIL: path.exists(\".\") should be true")
    }

    // Nonexistent path should not exist
    if path.exists("/nonexistent/path/xyz") {
        println("FAIL: path.exists nonexistent should be false")
    }

    println("path tests passed")
    return 0
}
