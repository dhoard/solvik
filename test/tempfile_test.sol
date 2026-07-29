// test/tempfile_test.sol — built-in tempfile module tests
//
// Tests: tempfile.file, tempfile.dir

package test

func main() -> int {
    // === tempfile.file — creates a temp file and returns its path ===

    f: string = tempfile.file("solvik-test-")
    if f == "" {
        println("FAIL: tempfile.file returned empty path")
    }

    // The file should exist
    if file.exists(f) == false {
        println("FAIL: tempfile.file should create an existing file")
    }

    // Clean up
    file.delete(f)

    // After cleanup the file should not exist
    if file.exists(f) {
        println("FAIL: tempfile.file should be deletable")
    }

    // === tempfile.dir — creates a temp directory and returns its path ===

    d: string = tempfile.dir("solvik-test-")
    if d == "" {
        println("FAIL: tempfile.dir returned empty path")
    }

    // The directory should exist (file.exists works for directories too)
    if file.exists(d) == false {
        println("FAIL: tempfile.dir should create an existing directory")
    }

    // Can create a file inside the temp directory
    inner: string = path.join(d, "test.txt")
    file.write(inner, "hello")
    if file.exists(inner) == false {
        println("FAIL: should be able to write inside temp dir")
    }

    // Clean up inner file and directory
    file.delete(inner)
    file.delete(d)

    // === Multiple temp files should have unique paths ===

    f1: string = tempfile.file("solvik-uniq-")
    f2: string = tempfile.file("solvik-uniq-")
    if f1 == f2 {
        println("FAIL: two tempfile.file calls should produce unique paths")
    }
    file.delete(f1)
    file.delete(f2)

    // === Multiple temp dirs should have unique paths ===

    d1: string = tempfile.dir("solvik-uniq-")
    d2: string = tempfile.dir("solvik-uniq-")
    if d1 == d2 {
        println("FAIL: two tempfile.dir calls should produce unique paths")
    }
    file.delete(d1)
    file.delete(d2)

    println("tempfile tests passed")
    return 0
}
