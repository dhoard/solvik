// expected C112: statements after return are unreachable
package reference_invalid

func f() -> Int {
    return 1
    println("dead")
}

func main() -> Int {
    return 0
}
