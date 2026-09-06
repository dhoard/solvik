// expect: C028
package conformance

func main() -> Int {
    r: Int = println("x") ?? 1
    return 0
}
