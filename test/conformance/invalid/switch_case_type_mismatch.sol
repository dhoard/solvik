// expect: C094
package conformance

func main() -> Int {
    x: Int = 42
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
