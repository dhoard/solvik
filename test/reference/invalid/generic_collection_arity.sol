// expected C096: collections must be instantiated with their exact arity
package conformance

func main() -> Int {
    xs: List<Int, String> = [1, 2]
    m: Map<Int> = { 1: 2 }
    return 0
}
