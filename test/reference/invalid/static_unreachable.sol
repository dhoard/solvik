// expected C112: statements after return are unreachable
package reference_invalid

func f() -> int {
    return 1
    println("dead")
}

func main() -> int {
    return 0
}
