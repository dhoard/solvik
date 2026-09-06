// expected C113: break/continue require an enclosing loop
package reference_invalid

func main() -> Int {
    break
    return 0
}
