// expected C113: break/continue require an enclosing loop
package reference_invalid

func main() -> int {
    break
    return 0
}
