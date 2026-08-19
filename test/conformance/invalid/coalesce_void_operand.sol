// expect: C028
package conformance

func main() -> int {
    r: int = println("x") ?? 1
    return 0
}
