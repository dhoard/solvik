// test/file_temp_test.sol — file.temp and file.tempDir tests
//
// Tests: file.temp, file.tempDir

package test

func main() -> int {
    // === file.temp — creates a temp file and returns its path ===

    f: string = file.temp("solvik-test-")
    if f == "" {
        println("FAIL: file.temp returned empty path")
    }

    // The file should exist
    if file.exists(f) == false {
        println("FAIL: file.temp should create an existing file")
    }

    // Clean up
    file.delete(f)

    // After cleanup the file should not exist
    if file.exists(f) {
        println("FAIL: file.temp should be deletable")
    }

    // === file.tempDir — creates a temp directory and returns its path ===

    d: string = file.tempDir("solvik-test-")
    if d == "" {
        println("FAIL: file.tempDir returned empty path")
    }

    // The directory should exist (file.exists works for directories too)
    if file.exists(d) == false {
        println("FAIL: file.tempDir should create an existing directory")
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

    f1: string = file.temp("solvik-uniq-")
    f2: string = file.temp("solvik-uniq-")
    if f1 == f2 {
        println("FAIL: two file.temp calls should produce unique paths")
    }
    file.delete(f1)
    file.delete(f2)

    // === Multiple temp dirs should have unique paths ===

    d1: string = file.tempDir("solvik-uniq-")
    d2: string = file.tempDir("solvik-uniq-")
    if d1 == d2 {
        println("FAIL: two file.tempDir calls should produce unique paths")
    }
    file.delete(d1)
    file.delete(d2)

    println("file_temp tests passed")
    return 0
}
