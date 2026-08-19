// expect: C094
package conformance

func main() -> int {
    y: int = 1
    switch y {
        case null {
            println("m")
        }
        default {
            println("d")
        }
    }
    return 0
}
