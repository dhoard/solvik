// expect: C094
package conformance

func main() -> int {
    x: int = 42
    switch x {
        case "abc" {
            println("m")
        }
        default {
            println("d")
        }
    }
    return 0
}
